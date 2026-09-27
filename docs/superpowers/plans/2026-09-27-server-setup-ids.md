# Server-Side Setup IDs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The server derives each lineage from its color and generates the game ID, so creation requests carry neither.

**Architecture:** `BootstrapParticipantRequest` drops `lineageId`, and `FirstGameBootstrapMapper` derives `LineageId(s"${color.key}-lineage")` for both creation paths. `TrustedGameCreateRequest` drops `gameId`. `TrustedGameProvisioning` draws one from an injectable generator and draws again when the store reports a duplicate. The host page stops sending both fields.

**Tech Stack:** Scala 3, ujson, munit, Scala.js frontend.

**Spec:** The bounded design approved in chat on 2026-09-27, restated here. The ROADMAP's "Setup deferred items" record the two items.

## Global Constraints

- Derived lineage: `s"${color.key}-lineage"`, the value the host page sends today.
- Generated game ID: `"game-"` plus 12 characters from `abcdefghijklmnopqrstuvwxyz234567`, drawn from `SecureRandom`.
- At most 8 game ID attempts. When all 8 collide, the result is `TrustedGameFailure.DuplicateGame`, which stays HTTP 409.
- The codecs stay exact: a request that still carries `lineageId` or `gameId` is refused.
- The authenticated bootstrap route keeps its path game ID. Only its participants lose `lineageId`.

---

### Task 1: The server derives the lineage from the color

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandProtocol.scala` (`BootstrapParticipantRequest(playerId, color)`)
- Modify: `shared/src/main/scala/oathdigital/protocol/BootstrapProtocolCodec.scala`, `TrustedGameProtocolCodec.scala` (participant fields `playerId`, `color`)
- Modify: `src/main/scala/oathdigital/application/FirstGameBootstrapMapper.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/TrustedHostUi.scala` (drop `lineageId(color)`)
- Test: `TrustedGameProtocolSuite`, `ProjectionProtocolSuite`, `GameHttpWireSuite`, `ServerRoutesSuite`, `AuthenticatedGameBootstrapRoutesSuite`, `TrustedGameProvisioningSuite`, `TrustedSeatRoutesSuite`, `ServerRuntimeSuite`, `ServerModeUiSuite`, new `FirstGameBootstrapMapperSuite`

**Interfaces:**
- Produces: `BootstrapParticipantRequest(playerId: String, color: PlayerColor)` and `FirstGameBootstrapMapper.lineageOf(color: PlayerColor): LineageId`.

- [ ] **Step 1: Write the failing tests**

`src/test/scala/oathdigital/application/FirstGameBootstrapMapperSuite.scala`:

```scala
package oathdigital.application

import oathdigital.model.{LineageId, PlayerColor, PlayerId}
import oathdigital.protocol.{BootstrapParticipantRequest, FirstGameBootstrapRequest}

class FirstGameBootstrapMapperSuite extends munit.FunSuite:
  test("each participant's lineage is derived from its color"):
    val config = FirstGameBootstrapMapper.map(FirstGameBootstrapRequest(0L, Vector(
      BootstrapParticipantRequest("p1", PlayerColor.Red),
      BootstrapParticipantRequest("p2", PlayerColor.Brown)), "p1"))
    assertEquals(config.participants.map(p => p.playerId -> p.lineageId), Vector(
      PlayerId("p1") -> LineageId("red-lineage"),
      PlayerId("p2") -> LineageId("brown-lineage")))
```

In `TrustedGameProtocolSuite`, the fixture JSON loses `"lineageId"`, and the exact-field test also refuses a participant carrying `"lineageId"`. The invalid-participant case built from `lineageId = ""` is removed. In the `FirstGameBootstrapCodec` coverage (`GameHttpWireSuite`), a participant carrying `lineageId` is refused.

- [ ] **Step 2: Run the tests to verify they fail to compile**

Run: `./sbtw "testOnly *FirstGameBootstrapMapperSuite"`. Expected: compile failure, because the two-argument constructor does not exist.

- [ ] **Step 3: Implement**

Drop the field and codec entries, then derive the lineage in the mapper:

```scala
object FirstGameBootstrapMapper:
  /** The lineage a color plays: every color has exactly one. */
  def lineageOf(color: PlayerColor): LineageId = LineageId(s"${color.key}-lineage")

  def map(request: FirstGameBootstrapRequest): FirstGameBootstrapConfig =
    FirstGameBootstrapConfig(
      request.participants.map(participant => FirstGameParticipant(
        PlayerId(participant.playerId),
        lineageOf(participant.color),
        participant.color
      )),
      PlayerId(request.firstPlayer)
    )
```

Update every fixture that builds a participant, and the host UI call `BootstrapParticipantRequest(id, row.color)`. `TrustedGameProvisioningSuite`'s invalid case "duplicate lineage" is removed: a duplicate lineage is now only possible through a duplicate color, which the case above it covers. `ServerModeUiSuite` asserts on the participants' colors in place of their lineage IDs.

- [ ] **Step 4: Run the affected suites and the frontend tests**

Run: `./sbtw "test" "frontend/test"`. Expected: PASS.

- [ ] **Step 5: Commit**

`feat(setup): the server derives each lineage from its color`

### Task 2: The server generates the game ID

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/TrustedGameProtocol.scala` (`TrustedGameCreateRequest(participants)`)
- Modify: `shared/src/main/scala/oathdigital/protocol/TrustedGameProtocolCodec.scala` (root field `participants` only)
- Modify: `src/main/scala/oathdigital/server/TrustedGameProvisioning.scala`
- Modify: `src/main/scala/oathdigital/server/ServerRuntime.scala` (`open(..., gameIds: () => String = TrustedGameProvisioning.generateGameId)`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/TrustedHostUi.scala` (no `gameId` state, no 409 branch)
- Modify: `docs/ROADMAP.md`
- Test: `TrustedGameProtocolSuite`, `TrustedGameProvisioningSuite`, `TrustedSeatRoutesSuite`, `ServerRuntimeSuite`, `ServerModeUiSuite`

**Interfaces:**
- Consumes: Task 1's `BootstrapParticipantRequest(playerId, color)`.
- Produces: `TrustedGameProvisioning(service, planFactory, store, generateCode, nowMillis, generateGameId)` and `TrustedGameProvisioning.generateGameId(): String`.

- [ ] **Step 1: Write the failing tests**

In `TrustedGameProvisioningSuite`, `provision` takes a game ID generator, and three tests are new:

```scala
  test("the game ID comes from the server's generator"):
    withDatabase { (owner, connection) =>
      val generated = codes.iterator
      val response = provision(owner, () => generated.next(), () => "server-game")
        .create(request, "https://games.test")
      assertEquals(response.map(_.gameId), Right("server-game"))
      assertEquals(rows(connection, "server-game").head, 1)
    }

  test("a taken game ID is drawn again"):
    withDatabase { (owner, connection) =>
      val first = codes.iterator
      assert(provision(owner, () => first.next(), () => "taken").create(request,
        "https://games.test").isRight)
      val ids = Iterator("taken", "fresh")
      val second = codes.reverseIterator
      val response = provision(owner, () => second.next(), () => ids.next())
        .create(request, "https://games.test")
      assertEquals(response.map(_.gameId), Right("fresh"))
    }

  test("eight taken game IDs refuse the creation as a duplicate"):
    withDatabase { (owner, connection) =>
      val first = codes.iterator
      assert(provision(owner, () => first.next(), () => "taken").create(request,
        "https://games.test").isRight)
      var draws = 0
      val second = codes.reverseIterator
      val response = provision(owner, () => second.next(),
        () => { draws += 1; "taken" }).create(request, "https://games.test")
      assertEquals(response, Left(TrustedGameFailure.DuplicateGame))
      assertEquals(draws, 8)
    }

  test("generated game IDs follow the identifier rule"):
    val ids = Vector.fill(50)(TrustedGameProvisioning.generateGameId())
    assert(ids.forall(_.matches("game-[a-z2-7]{12}")), ids)
    assertEquals(ids.distinct.size, ids.size)
```

`TrustedGameProtocolSuite` refuses a root carrying `"gameId"`.

- [ ] **Step 2: Run to verify failure**

Run: `./sbtw "testOnly *TrustedGameProvisioningSuite"`. Expected: compile failure.

- [ ] **Step 3: Implement**

`TrustedGameProvisioning.create` draws the ID after validation. It prepares the bootstrap, generates the seats once, and stores. On `TrustedGameStoreFailure.DuplicateGame` it draws a new ID and prepares again, keeping the seats, up to `MaxGameIdAttempts = 8`:

```scala
  private def place(participants: Vector[BootstrapParticipantRequest],
      plan: FirstGamePlan, seats: Option[Vector[(String, SeatCode)]], attempt: Int)
      : Either[TrustedGameFailure, (String, Vector[(String, SeatCode)])] =
    val gameId = generateGameId()
    for
      prepared <- service.prepareBootstrap(gameId, plan.chronicle,
        plan.resolvedConfig).left.map:
        case _: GameApplicationError.CommandRejected => InvalidRequest
        case _ => StorageFailure
      codes <- seats.fold(generateSeats(participants))(Right(_))
      placed <- store.create(gameId, codes.map { case (player, code) =>
          code.digest -> player }, prepared.records, nowMillis()) match
        case Left(TrustedGameStoreFailure.DuplicateGame) if attempt < MaxGameIdAttempts =>
          place(participants, plan, Some(codes), attempt + 1)
        case Left(failure) => Left(storeFailure(failure))
        case Right(()) => Right(gameId -> codes)
    yield placed
```

`generateGameId` is `"game-" + 12 draws from the base32 alphabet` on the shared `SecureRandom`. `ServerRuntime.open` passes its `gameIds` parameter through. `TrustedSeatRoutesSuite` feeds the IDs its tests name, like `alpha:one`, through a queue that `create` fills before each POST, so the path-encoding tests keep their IDs.

The host UI drops `var gameId` and the 409 branch. `ServerModeUiSuite` asserts that the posted body has no `gameId`. The old retry test becomes "a refused creation keeps the form editable": it asserts that the server's message is shown and that Create stays enabled. `freshGameId` stays, because `ServerModeUi` still uses it.

ROADMAP: both "Setup deferred items" become `[x]`, each with one line naming this slice.

- [ ] **Step 4: Run the full gates**

Run: `./sbtw "test" "frontend/test"`, `python3 scripts/check-architecture.py`, and the markdown link check. Expected: PASS.

- [ ] **Step 5: Commit**

`feat(server): the server generates the trusted game ID`
