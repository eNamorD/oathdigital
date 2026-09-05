# Engine Redesign Phase 2 (Redo): Cost & Supply Vocabulary — No Cap Flags

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Roll back the `bestEffort` flag experiment and land Phase 2 as a vocabulary + semantics phase with zero per-op cap flags: (1) as-much-as-possible = plan-time clamp via a tiny supply helper; (2) new `Cost`/`PayCost` vocabulary (zero-cost allowed, `Cost.free` default) replaces the PowerOperations cost machinery and absorbs Economy payments; (3) new `AdjustSupply` op standardizes all supply spending. Replay machinery stays — it stops driving op design.

**Architecture:** No executor cap modes. `Cost(favor, secret, favorBurnt, secretBurnt)` is a plain data struct; `PayCost(player, placedAt, cost)` is a composite `CoreOperation` whose primitives are the same Moves today's cost planner emits (placed portions player→`placedAt` = `Location.OnCard`, burnt portions → `Location.SharedBank`). `Costs.plan` is the typed pre-flight (affordability + placed-destination validation) that emits the single root; `PowerOperationPlanner.payment` is deleted, `placement` stays. `AdjustSupply(player, amount)` is a new `PrimitiveOperation` mutated inside `OperationStateMutation.applyNonMovePrimitives`, replacing seven per-action `SupplyTrack(x - n)` update callbacks. Plan-time clamps use `SupplyResource.favor`.

**Tech Stack:** Scala 2.13, sbt multi-project, munit. Full test command: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`.

**Spec:** Locked with the user (plan-mode Q&A):
1. No flag: killed `bestEffort` on `Gain`; no `requireExact`. Supply-capped effects clamp at plan time in the author (`math.min` precedent `CardPlay.scala:167`); deterministic under replay because recorded amount = actual.
2. Replay kept as-is; vocabulary design no longer replay-bound.
3. `Cost` fields all ≥ 0, **zero total allowed** (`Cost.free = Cost(0,0,0,0)` default) — discount/increase modifiers adjust via `cost.copy(...)`.
4. `PayCost(player, placedAt: Location, cost)` — non-burnt portions → `placedAt`; burnt portions leave play. `placedAt` validated only when `favor + secret > 0`. Zero-cost PayCost = inert no-op.
5. PayCost scope = powers + Economy (Muster/Trade migrate; `Mustered`/`Traded` events unchanged).
6. `AdjustSupply(player, amount)`: negative = spend (insufficient → new `OperationError.InsufficientSupply`); positive = gain capped at `SupplyTrack.Maximum` (7). Rest refresh stays a set-to-value.
7. Work on `feat/engine-redesign`, base `66f1124` (Phase 1 HEAD). Phase-1 doc: `docs/superpowers/plans/2026-09-04-engine-redesign.md` (Decision 3 / Roadmap get updated in Task 6).

## Global Constraints

- Branch `feat/engine-redesign`, base commit `66f1124`. Commit each task separately with the messages given.
- TDD per task: failing test → watch fail → implement → watch pass → commit.
- No behavior change to any action end-state: each migration must leave existing suites green (byte-identical outcomes).
- Full gate = `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` exit 0; `git diff --check` clean.
- Grep gate at end: zero `bestEffort|requireExact|ResourceCost|CostDisposition` under `src/`.
- No TODO block currently exists in `CoreOperations.scala` (verified); the new ops land as vocabulary additions without touching any TODO list.

---

### Task 0: Reset the flag experiment

**Files:** none committed (history already clean at `66f1124`).

- [ ] **Step 1: Reset the working tree to the Phase-1 head**

Run: `git reset --hard 66f1124`
Expected: branch clean at `66f1124` (`git status` shows only untracked `docs/superpowers/`).

- [ ] **Step 2: Remove the leftover experiment suite if present**

Run: `rm -f src/test/scala/oathdigital/gameplay/GainCapSemanticsSuite.scala`
Expected: file gone (it never existed in history).

- [ ] **Step 3: Compile check**

Run: `./sbtw "Test/compile"`
Expected: success.

### Task 1: Plan-time clamp helper

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/SupplyResource.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala:167`
- Test: `src/test/scala/oathdigital/gameplay/SupplyResourceSuite.scala`

**Interfaces:**
- Consumes: nothing new.
- Produces: `object SupplyResource { def favor(available: Int, requested: Int): Int }` — clamped, never negative, never exceeds requested.

- [ ] **Step 1: Write the failing test**

```scala
package oathdigital.gameplay

class SupplyResourceSuite extends munit.FunSuite {
  test("favor clamp takes the full requested amount when available") {
    assertEquals(SupplyResource.favor(available = 5, requested = 3), 3)
  }
  test("favor clamp takes the available remainder when short") {
    assertEquals(SupplyResource.favor(available = 2, requested = 5), 2)
  }
  test("favor clamp yields zero when nothing is available") {
    assertEquals(SupplyResource.favor(available = 0, requested = 5), 0)
  }
  test("favor clamp yields zero when nothing is requested") {
    assertEquals(SupplyResource.favor(available = 5, requested = 0), 0)
  }
  test("favor clamp guards negative inputs") {
    assertEquals(SupplyResource.favor(available = -1, requested = 5), 0)
    assertEquals(SupplyResource.favor(available = 5, requested = -1), 0)
  }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.SupplyResourceSuite"`
Expected: FAIL — `SupplyResource` does not exist.

- [ ] **Step 3: Implement**

```scala
package oathdigital.gameplay

/** Plan-time clamps for supply-capped effects. "As much as possible" is
  * resolved here, before an exact operation is built, so the executor never
  * needs a best-effort mode (engine redesign decision 3).
  */
object SupplyResource {
  def favor(available: Int, requested: Int): Int =
    math.min(math.max(0, available), math.max(0, requested))
}
```

- [ ] **Step 4: Wire CardPlay to the helper**

`src/main/scala/oathdigital/gameplay/actions/CardPlay.scala:167`: replace
`val gain = math.min(1, ready.banks.favor.getOrElse(suit, 0))`
with
`val gain = SupplyResource.favor(ready.banks.favor.getOrElse(suit, 0), 1)`
(The 0/1 outcome is identical; `SupplyResource` is in `oathdigital.gameplay`, same package as `CardPlay`, so no import is needed.)

- [ ] **Step 5: Run to verify it passes**

Run: `./sbtw "testOnly oathdigital.gameplay.SupplyResourceSuite"`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/SupplyResource.scala src/main/scala/oathdigital/gameplay/actions/CardPlay.scala src/test/scala/oathdigital/gameplay/SupplyResourceSuite.scala
git commit -m "feat(rules): plan-time supply clamp helper for favor grants"
```

### Task 2: Cost vocabulary + PayCost (zero-cost allowed)

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/operations/CoreOperations.scala` (add `Cost` + `PayCost` near `Give`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/PowerOperations.scala` (delete `ResourceKind`/`CostDisposition`/`ResourceCost`/`CostDescription`/`Payment` + `PayCosts`; add `Costs`; `PowerOperationPlanner.payment` deleted, `placement` kept)
- Test: `src/test/scala/oathdigital/gameplay/PowerOperationsSuite.scala` (rewrite cost tests), plus PayCost behavior cases

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `Cost(favor, secret, favorBurnt, secretBurnt)` + `Cost.free`; `PayCost(player: PlayerId, placedAt: Location, cost: Cost): CoreOperation` (empty primitives when cost is free); `Costs.plan(ready, actor, placedAt: Location, cost: Cost): Either[OathViolation, PayCost]`; `Costs.affordable(...) = plan(...).isRight`.

- [ ] **Step 1: Add `Cost` and `PayCost` to CoreOperations.scala**

Insert immediately after the `Give` case class block (ends line ~302 in the `66f1124` file):

```scala
/** A typed payment: `favor`/`secret` are placed at a destination card,
  * `favorBurnt`/`secretBurnt` leave play to the shared bank. All fields are
  * non-negative; an all-zero cost is legal and represents a free payment.
  */
final case class Cost(favor: Int, secret: Int, favorBurnt: Int,
    secretBurnt: Int) {
  require(favor >= 0 && secret >= 0 && favorBurnt >= 0 && secretBurnt >= 0,
    "costs must be non-negative")
}

object Cost {
  val free: Cost = Cost(0, 0, 0, 0)
}

/** Pays a typed cost. The placed portions (`favor`/`secret`) move from the
  * player's play area to `placedAt`; the burnt portions leave play to the
  * shared bank. An all-zero cost is an inert no-op.
  */
final case class PayCost(player: PlayerId, placedAt: Location, cost: Cost)
    extends CoreOperation {
  override val primitives: Vector[PrimitiveOperation] =
    favorMove(cost.favor, placedAt) ++
      secretMove(cost.secret, placedAt) ++
      favorMove(cost.favorBurnt, Location.SharedBank) ++
      secretMove(cost.secretBurnt, Location.SharedBank)

  private def favorMove(amount: Int, to: Location): Vector[PrimitiveOperation] =
    if (amount == 0) Vector.empty
    else Vector(Move(Piece.Favor(amount),
      PositionedLocation(Location.PlayArea(player)), PositionedLocation(to)))

  private def secretMove(amount: Int, to: Location): Vector[PrimitiveOperation] =
    if (amount == 0) Vector.empty
    else Vector(Move(Piece.Secrets(amount),
      PositionedLocation(Location.PlayArea(player)), PositionedLocation(to)))
}
```

These primitives are byte-identical to the old `PowerOperationPlanner.payment` output (placed portions land on the destination card; burnt portions go to the shared bank; secret orientation split is handled by `OperationSecretPlanner`).

- [ ] **Step 2: Compile**

Run: `./sbtw "Test/compile"`
Expected: success (new types unused yet).

- [ ] **Step 3: Rewrite PowerOperations.scala cost surface**

Delete from `PowerOperations.scala`: the `ResourceKind` object, `CostDisposition` object, `ResourceCost`, `CostDescription`, and `Payment` case classes (the block from `sealed trait ResourceKind` through `final case class Payment(...)`). Replace the whole `object PayCosts { ... }` with:

```scala
object Costs {
  def affordable(ready: ReadyGame, actor: PlayerId, placedAt: Location,
      cost: Cost): Boolean = plan(ready, actor, placedAt, cost).isRight

  /** Pre-flight affordability + placement validation owned by the caller's
    * power or action path. Rejects unaffordable costs early with a typed
    * OathViolation; the executor remains authoritative for atomic batch
    * sufficiency. A free cost (Cost.free) is always affordable and needs no
    * destination check.
    */
  def plan(ready: ReadyGame, actor: PlayerId, placedAt: Location,
      cost: Cost): Either[OathViolation, PayCost] =
    if (cost == Cost.free) Right(PayCost(actor, placedAt, cost))
    else
      for {
        player <- ready.game.current.players.find(_.player == actor)
          .toRight(WrongPlayer(ready.game.current.turn.activePlayer, actor))
        favor = cost.favor + cost.favorBurnt
        secrets = cost.secret + cost.secretBurnt
        _ <- Either.cond(player.board.favor >= favor, (),
          InsufficientFavor(favor, player.board.favor))
        _ <- Either.cond(player.board.faceUpSecrets >= secrets, (),
          InsufficientSecrets(secrets, player.board.faceUpSecrets))
        _ <- validatePlaced(ready, placedAt, cost)
      } yield PayCost(actor, placedAt, cost)

  private def validatePlaced(ready: ReadyGame, placedAt: Location,
      cost: Cost): Either[OathViolation, Unit] =
    if (cost.favor + cost.secret == 0) Right(())
    else
      placedAt match {
        case Location.OnCard(id) if statefulCard(ready, id) => Right(())
        case _ => Left(InvalidEventOrder(
          "placed cost portions require an existing token-bearing card"))
      }

  private def statefulCard(ready: ReadyGame, id: CardId): Boolean =
    CardIndex.from(ready.game).toOption.exists { index =>
      index.get(id).toOption.flatMap(_.state).exists {
        case _: DenizenState | _: EdificeState | _: RelicState => true
        case _ => false
      }
    }
}
```

(Verify the exact `CardIndex`/`LocatedCard` accessor names against `OperationStateAdapter.card` before committing to `statefulCard` — `LocatedCard.state: Option[CardState]` is the shape used there. `WrongPlayer`, `InvalidEventOrder`, `InsufficientFavor`, `InsufficientSecrets` are `OathViolation` members; the file header already imports `oathdigital.gameplay.OathViolation._` — confirm it still does after the edit.)

Delete `PowerOperationPlanner.payment`; keep `PowerOperationPlanner.placement`.

- [ ] **Step 4: Compile inventory**

Run: `./sbtw "Test/compile"`
Expected: FAIL only at consumers of the deleted API: `RecoverPowers.scala` (PayCosts/Planner.payment), `PowerOperationsSuite.scala` (PayCosts/ResourceCost/CostDescription/PowerOperationPlanner.payment). Fix consumers in Tasks 4-5. (Earlier grep: PayCosts/Planner.payment consumers = RecoverPowers + PowerOperationsSuite only.)

- [ ] **Step 5: Rewrite the PayCost behavior tests**

In `PowerOperationsSuite.scala`, replace every cost-construction test that uses `ResourceCost`/`CostDisposition`/`PayCosts` with `Cost`/`Costs` equivalents, preserving each behavioral assertion (empty/unaffordable/wrong-source cases translate field-for-field; the fixture `operationReady` sets `favor = 3, faceUpSecrets = 3`). Keep `DrawTopRelic`/`PlaceRelicAtSite` tests unchanged. Append:

```scala
  test("PayCost executes placed and burnt portions atomically") {
    val (ready, actor, siteId, denizenId) = operationReady
    val placedAt = Location.OnCard(denizenId)
    val cost = Cost(favor = 1, secret = 1, favorBurnt = 1, secretBurnt = 1)
    val payCost = Costs.plan(ready, actor.player, placedAt, cost).toOption.get
    val executor = new OperationExecutor(OperationPolicy.exact(
      Vector(payCost), "test payment operation is not permitted"))
    val after = OperationTransaction.evolve(
      ready, Vector(payCost), executor)(Right(_)).toOption.get
    val player = after.game.current.players.find(_.player == actor.player).get
    val card = after.game.current.map.sites(siteId).denizens.head
    assertEquals(player.board.favor, actor.board.favor - 1)
    assertEquals(player.board.faceUpSecrets, actor.board.faceUpSecrets - 2)
    assertEquals(card.tokens, Tokens(favor = 1, secrets = 1))
  }

  test("a free PayCost is an inert no-op") {
    val (ready, actor, _, _) = operationReady
    val payCost = Costs.plan(ready, actor.player, Location.OnCard(
      DenizenId("irrelevant")), Cost.free).toOption.get
    assertEquals(payCost.primitives, Vector.empty)
    val executor = new OperationExecutor(OperationPolicy.exact(
      Vector(payCost), "test payment operation is not permitted"))
    val after = OperationTransaction.evolve(
      ready, Vector(payCost), executor)(Right(_)).toOption.get
    assertEquals(after.game, ready.game)
    assertEquals(after.banks, ready.banks)
  }
```

(Burnt secrets come from faceUp only, so `faceUpSecrets` drops by 2 — verify against the existing 1-secret-burn assertion leaving `faceUpSecrets == 2`.)

- [ ] **Step 6: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.PowerOperationsSuite"`
Expected: PASS (rewritten tests + the two new ones). Fix any assertion drift found — do not weaken assertions.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/operations/CoreOperations.scala src/main/scala/oathdigital/gameplay/operations/PowerOperations.scala src/test/scala/oathdigital/gameplay/PowerOperationsSuite.scala
git commit -m "feat(operations): Cost/PayCost replace the disposition cost machinery"
```

(Expected red at compile: `RecoverPowers.scala` still references deleted API — fixed in Task 5; the Task-2 commit may not fully compile until then, consistent with the staged-refactor convention used in Phase 1.)

### Task 3: AdjustSupply op + executor + all spend sites

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/operations/CoreOperations.scala` (new `AdjustSupply`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationError.scala` (new `InsufficientSupply`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationStateMutation.scala` (new mutation branch)
- Modify (spend sites): `actions/Travel.scala`, `actions/Economy.scala`, `actions/Challenge.scala`, `actions/Forge.scala`, `actions/Search.scala`, `actions/Recover.scala`, `actions/Campaign.scala`
- Test: `src/test/scala/oathdigital/gameplay/SupplyAdjustSuite.scala` (new)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `AdjustSupply(player: PlayerId, amount: Int) extends PrimitiveOperation`; `OperationError.InsufficientSupply(required: Int, available: Int)` (code `insufficient-supply`); supply writes happen only through this op except Rest's set-to-value.

- [ ] **Step 1: Add the op**

In `CoreOperations.scala`, add after the `FlipSecrets` primitive:

```scala
/** Changes a player's spendable Supply. Negative amounts spend (an
  * insufficient track is an OperationError.InsufficientSupply), positive
  * amounts gain up to SupplyTrack.Maximum. Rest's refresh-to-value stays a
  * non-delta write in the Rest phase.
  */
final case class AdjustSupply(player: PlayerId, amount: Int)
    extends PrimitiveOperation {
  require(amount != 0, "supply adjustment must be non-zero")
}
```

- [ ] **Step 2: Add the error**

In `OperationError.scala`, add after `InsufficientPieces` (line ~39):

```scala
  final case class InsufficientSupply(required: Int, available: Int)
      extends OperationError {
    override val code: String = "insufficient-supply"
    override val detail: String =
      s"a supply spend of $required exceeds the $available available"
  }
```

- [ ] **Step 3: Write the failing executor tests**

Create `src/test/scala/oathdigital/gameplay/SupplyAdjustSuite.scala`, reusing the `OperationExecutorSuite` fixture pattern:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.gameplay.setup.{FirstGameFoundationProfile,
  FirstGameSupportState, PlayerColor}
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

class SupplyAdjustSuite extends munit.FunSuite {
  private val blueId = PlayerId("player-blue")
  private val blueLineage = LineageId("blue")
  private val redForce = ForceKind.Exile(lineageId)
  private val blueForce = ForceKind.Exile(blueLineage)

  private val bluePlayer = PlayerState(
    blueId, blueLineage, Some(sites(1)),
    PlayerBoardState(2, 1, 0, 2, SupplyTrack.full),
    Vector.empty, Vector(RelicState(RelicId("R3"), Orientation.FaceUp,
      Tokens.empty)), None)

  private val ready = {
    val current = game.current.copy(
      players = game.current.players :+ bluePlayer,
      banners = game.current.banners.copy(
        peoplesFavor = game.current.banners.peoplesFavor.copy(
          holder = Some(playerId))))
    val campaign = game.campaign.copy(lineages = game.campaign.lineages.updated(
      blueLineage,
      LineageState(blueLineage, Some(blueId), Role.Exile,
        Vector.empty, Vector.empty)))
    ReadyGame(
      game.copy(campaign = campaign, current = current),
      Map(playerId -> PlayerColor("red"), blueId -> PlayerColor("blue")),
      FirstGameSupportState(FirstGameFoundationProfile.FixedUnaltered, playerId),
      MaterialBankState(
        Suit.all.map(_ -> 5).toMap,
        Map(redForce -> 14, blueForce -> 14, ForceKind.Bandit -> 24)))
  }

  private val executor = new OperationExecutor(OperationPolicy.Permissive)

  private def supply(state: ReadyGame, player: PlayerId): Int =
    state.game.current.players.find(_.player == player).get.board.supply.supply

  private def withSupply(player: PlayerId, value: Int): ReadyGame = {
    val fixed = ready.game.current.players.map(existing =>
      if (existing.player != player) existing
      else existing.copy(board = existing.board.copy(
        supply = SupplyTrack(value))))
    ready.copy(game = ready.game.copy(current = fixed))
  }

  test("an exact spend reduces supply") {
    val actor = ready.game.current.players.find(_.player == playerId).get
    val result = executor.execute(ready,
      AdjustSupply(playerId, -2)).toOption.get
    assertEquals(supply(result, playerId), supply(ready, playerId) - 2)
    assert(actor.board.supply.supply > 2)
  }

  test("an unaffordable spend is rejected") {
    val source = withSupply(playerId, 1)
    val result = executor.execute(source, AdjustSupply(playerId, -2))
    assert(result.left.toOption.get
      .isInstanceOf[OperationError.InsufficientSupply])
  }

  test("a positive adjustment caps at the track maximum") {
    val source = withSupply(playerId, 6)
    val result = executor.execute(source,
      AdjustSupply(playerId, 5)).toOption.get
    assertEquals(supply(result, playerId), SupplyTrack.Maximum)
  }

  test("staged adjustments apply in order") {
    val first = executor.execute(ready,
      AdjustSupply(playerId, -2)).toOption.get
    val second = executor.execute(first,
      AdjustSupply(playerId, -2)).toOption.get
    assertEquals(supply(second, playerId), supply(ready, playerId) - 4)
  }
}
```

(Verify the fixture actor's starting supply is comfortably above 4; if the first player starts at `SupplyTrack.full`, all four tests hold as written.)

- [ ] **Step 4: Run to verify failure**

Run: `./sbtw "testOnly oathdigital.gameplay.SupplyAdjustSuite"`
Expected: FAIL — `AdjustSupply`/`InsufficientSupply` do not exist.

- [ ] **Step 5: Implement the executor branch**

In `OperationStateMutation.scala`, add a case in `applyNonMovePrimitives` (the fold handling Flip/FlipSecrets/Peek):

```scala
      case (result, AdjustSupply(player, amount)) =>
        result.flatMap(adjustSupply(_, player, amount))
```

and add the helper next to `flipPlayerSecrets`:

```scala
  private def adjustSupply(ready: ReadyGame, player: PlayerId,
      amount: Int): Either[OperationError, ReadyGame] =
    playerState(ready, player).flatMap { state =>
      val current = state.board.supply.supply
      if (amount < 0) {
        val required = -amount
        Either.cond(current >= required, (), InsufficientSupply(
          required, current)).flatMap { _ =>
          updatePlayer(ready, player)(value => value.copy(
            board = value.board.copy(supply = SupplyTrack(current - required))))
        }
      } else
        updatePlayer(ready, player)(value => value.copy(
          board = value.board.copy(supply = SupplyTrack(math.min(
            SupplyTrack.Maximum, current + amount)))))
    }
```

- [ ] **Step 6: Run to verify pass**

Run: `./sbtw "testOnly oathdigital.gameplay.SupplyAdjustSuite"`
Expected: PASS (4 tests).

- [ ] **Step 7: Migrate the seven spend sites**

For each file, delete the `updateCurrent(...supply = SupplyTrack(x - n))` callback inside the `evolve` update lambda and instead append `AdjustSupply(player, -n)` to that action's `operations` vector (n = the exact amount the callback previously subtracted). Exact policy vectors already contain the action's semantic roots — `AdjustSupply` is one of them, and each action constructs it from the same recorded fields (e.g. `event.supplySpent`, `SupplyCost`, constants) that previously fed the callback, so replay reconstruction stays canonical:

1. `actions/Travel.scala` (~65-82): the evolve lambda previously set `SupplyTrack(candidate.board.supply.supply - expected)`. Add `AdjustSupply(event.playerId, -expected)` to the `Vector(CoreMove(...))` and delete the supply copy in the lambda.
2. `actions/Economy.scala` `evolveOperations` (~246-263): drop the `supplySpent` callback write; the caller appends `AdjustSupply(player, -supplySpent)` to `operations` (Task 4 details the Muster/Trade vectors).
3. `actions/Challenge.scala` (~197): supply −1 write → `AdjustSupply(playerId, -1)` in the batch.
4. `actions/Forge.scala` (~121): supply −1 → `AdjustSupply(actor, -1)`.
5. `actions/Search.scala` (~108): supply −`cost` → `AdjustSupply(event.playerId, -cost)`.
6. `actions/Recover.scala` (~232): supply −1 → `AdjustSupply(playerId, -1)`.
7. `actions/Campaign.scala` (~300): supply −`SupplyCost` → `AdjustSupply(playerId, -SupplyCost)`.
8. `phases/Rest.scala`: UNCHANGED (set-to-`event.refreshedSupply`).

Each action's update lambda now only writes its non-supply module state; `AdjustSupply` carries the supply delta through the op batch (visible to policy/exact replay and future recorded-ops).

- [ ] **Step 8: Run the action suites**

Run: `./sbtw "testOnly oathdigital.gameplay.*"`
Expected: existing suites stay green — outcomes byte-identical because the op performs the same deduction earlier in the batch and the update lambda no longer touches supply.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/operations/CoreOperations.scala src/main/scala/oathdigital/gameplay/operations/OperationError.scala src/main/scala/oathdigital/gameplay/operations/OperationStateMutation.scala src/main/scala/oathdigital/gameplay/actions/Travel.scala src/main/scala/oathdigital/gameplay/actions/Economy.scala src/main/scala/oathdigital/gameplay/actions/Challenge.scala src/main/scala/oathdigital/gameplay/actions/Forge.scala src/main/scala/oathdigital/gameplay/actions/Search.scala src/main/scala/oathdigital/gameplay/actions/Recover.scala src/main/scala/oathdigital/gameplay/actions/Campaign.scala src/test/scala/oathdigital/gameplay/SupplyAdjustSuite.scala
git commit -m "feat(operations): AdjustSupply standardizes supply spending"
```

### Task 4: Economy onto PayCost/AdjustSupply

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/Economy.scala`
- Test: existing Economy/action suites (behavior assertions unchanged)

**Interfaces:**
- Consumes: Task 2 `PayCost`/`Cost`/`Costs`, Task 3 `AdjustSupply`.
- Produces: `Mustered`/`Traded` resolution emits `PayCost` + `AdjustSupply` ops; end-state identical.

- [ ] **Step 1: Rewrite the Economy operation builders**

In `Economy.scala`, replace `musterOperations`/`tradeOperations` (lines ~218-244) to emit `PayCost` and `AdjustSupply`. Preserve the old totals exactly:
- Muster: placed favor 1 → `Cost(favor = 1)`.
- Trade favor-mode: placed secret 1 → `Cost(secret = 1)`.
- Trade secret-mode: placed favor 1 + burnt favor 1 (2 favor total) → `Cost(favor = 1, favorBurnt = 1)`.

Sketch (adjust to the event's real field names — `Mustered`/`Traded` already carry the values the old code read; `supplySpent` is whatever amount the old callback subtracted):

```scala
  private def musterOperations(event: Mustered): Vector[CoreOperation] =
    Vector(PayCost(event.playerId, Location.OnCard(event.target.id),
      Cost(favor = 1))) ++
      Option.when(event.warbandsGained > 0)(Gain.Warbands(
        event.playerId, event.forceKind, event.warbandsGained)).toVector ++
      Vector(AdjustSupply(event.playerId, -event.supplySpent))

  private def tradeOperations(event: Traded): Vector[CoreOperation] =
    event.resource match {
      case TradeResource.Favor =>
        Vector(PayCost(event.playerId, Location.OnCard(event.target.id),
          Cost(secret = 1))) ++
          Option.when(event.gained > 0)(Gain.Favor(
            event.playerId, event.suit, event.gained)).toVector ++
          Vector(AdjustSupply(event.playerId, -event.supplySpent))
      case TradeResource.Secret =>
        Vector(PayCost(event.playerId, Location.OnCard(event.target.id),
          Cost(favor = 1, favorBurnt = 1))) ++
          Option.when(event.gained > 0)(Gain.Secrets(
            event.playerId, event.gained)).toVector ++
          Vector(AdjustSupply(event.playerId, -event.supplySpent))
    }
```

(Check the `Mustered` event's actual field names — earlier code called `Gain.Warbands(event.playerId, ForceKind.Exile(lineage), event.warbandsGained)` with a `lineage` parameter, so the force kind may be derived from a `lineage` argument rather than stored on the event; reproduce the old derivation. `evolveOperations`'s `supplySpent` parameter is the same value to feed `AdjustSupply`.)

- [ ] **Step 2: Remove the callback supply deduction**

Delete the supply update from `evolveOperations`'s update lambda (~lines 254-262). Keep all other writes the lambda performs exactly; if nothing else remains, the lambda stays only for non-supply module state (verify what else it writes — it previously also cleared nothing else in that snippet, so it may become `Right(...)` passthrough — preserve the exact final `ReadyGame`).

- [ ] **Step 3: Run the Economy suites**

Run: `./sbtw "test"`
Expected: green, end-state unchanged.

- [ ] **Step 4: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/Economy.scala
git commit -m "refactor(economy): Muster/Trade pay through PayCost and AdjustSupply"
```

### Task 5: Catacombs/RecoverPowers + codec onto new vocabulary

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/RecoverPowers.scala`
- Modify: the `OathEvent.CatacombsResolved` event + its serializer (find via grep `CatacombsResolved`)
- Modify: replay/round-trip fixtures referencing `CatacombsResolved(...)` and `.payment`

**Interfaces:**
- Consumes: Task 2 `Cost`/`Costs`/`PayCost`.
- Produces: `CatacombsResolved(actor, decision, powerId, source, cost: Cost, placement: RelicPlacement)` — `Payment` gone.

- [ ] **Step 1: Retype the event payload**

Find `OathEvent.CatacombsResolved` (grep under `src/main`) and replace its `paid: Payment` field with `cost: Cost` (keep field order: player, decision, power id, source, cost, placement). Update every constructor/reference.

- [ ] **Step 2: Update RecoverPowers.scala**

- `catacombsCost` becomes `Cost(secret = 1)`.
- `PayCosts.affordable(...)` → `Costs.affordable(ready, actor, Location.OnCard(sourceCardId), catacombsCost)` — the Catacombs source is a `RuleSourceRef.SiteCard(siteId, denizenId)`; resolve `sourceCardId` from the ref.
- `PayCosts.plan(...)` → `Costs.plan(...)`; the event carries the planned `cost`.
- `prepareCatacombs`/`canonicalCatacombs` re-derive the expected cost and compare (keep the existing canonical pattern, comparing `Cost` values).
- Execution lambda: replace `PowerOperationPlanner.payment(event.payment) :+ placement(...)` with `Right(Vector(PayCost(event.playerId, Location.OnCard(sourceCardId), event.cost)) :+ PowerOperationPlanner.placement(event.placement))`.

- [ ] **Step 3: Update the serializer + fixtures**

Grep `payment` near `CatacombsResolved` in serialization/codec files and replay fixtures; encode/decode `cost` (four ints) instead of the old payment; adjust fixture constructors to the new payload shape.

- [ ] **Step 4: Compile + grep proof**

Run: `./sbtw "Test/compile"` — Expected: success.
Run: `grep -rn "ResourceCost\|CostDisposition\|Planner.payment\|PayCosts" src/main` — Expected: no matches.

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.PowerOperationsSuite"` plus any Catacombs/Recover/serialization suites (grep `CatacombsResolved` under `src/test` to enumerate)
Expected: green.

- [ ] **Step 6: Commit**

```bash
git add <RecoverPowers.scala> <event file> <serializer file(s)> <fixture files>
git commit -m "refactor(powers): Catacombs pays through PayCost"
```

### Task 6: Docs + verification

**Files:**
- Modify: `docs/architecture/core-operations-migration.md`
- Modify: `docs/superpowers/plans/2026-09-04-engine-redesign.md` (Decision 3 + Roadmap Phase 2)
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Architecture doc**

In `docs/architecture/core-operations-migration.md`, add (or replace any rolled-back best-effort paragraph with):

```markdown
Supply-capped "as much as possible" effects are resolved at plan time with
`SupplyResource.favor` — the executor has no best-effort mode. Payments are
typed with `Cost(favor, secret, favorBurnt, secretBurnt)` and applied by the
single `PayCost(player, placedAt, cost)` root (zero-cost `Cost.free` is an
inert no-op); `Costs.plan` is the pre-flight affordability/placement
validator. Supply spending is an `AdjustSupply(player, amount)` operation —
the only supply write outside Rest's refresh-to-value — enforced exactly
against the track.
```

- [ ] **Step 2: Program spec + roadmap**

In `docs/superpowers/plans/2026-09-04-engine-redesign.md`: Decision 3's exception clause becomes "supply-capped gains clamp at plan time in the effect author (`SupplyResource.favor`); no per-op executor mode"; the Roadmap Phase 2 bullet becomes "Op vocabulary: `Cost`/`PayCost` (zero-cost allowed) replaces the disposition cost machinery across powers and Economy; `AdjustSupply` standardizes supply spending; plan-time clamps via `SupplyResource`". In `docs/ROADMAP.md`, rewrite the Phase 2 entry with the shipped commit hashes.

- [ ] **Step 3: Full verification**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: exit code 0.
Run: `git diff --check` — Expected: clean.
Run: `grep -rn "bestEffort\|requireExact\|ResourceCost\|CostDisposition" src/` — Expected: no matches.

- [ ] **Step 4: Live smoke**

Restart the dev server on head (kill the running job, then background `./sbtw "runMain oathdigital.server.OathServer var/oathdigital docs/catalog/new-foundations-component-catalog.json"`), poll `/health`, then:
`curl -s "http://127.0.0.1:8080/api/dev/first-games/manual-1788482797070-427915?playerId=red-exile"` → `200` and `"ready":true`.
Then bootstrap a fresh game and append one benign command (`placePawn`) → `200`, sequence advances.

- [ ] **Step 5: Commit**

```bash
git add docs/architecture/core-operations-migration.md docs/superpowers/plans/2026-09-04-engine-redesign.md docs/ROADMAP.md
git commit -m "docs: phase 2 = cost/supply vocabulary, no cap flags"
```

## Self-review notes
- Spec coverage: decision 1 → Task 1; decisions 3-4 (Cost/PayCost, zero-cost, Cost.free) → Task 2; decisions 6-7 (scope, AdjustSupply + 7 sites) → Tasks 3-4; Catacombs + codec → Task 5; docs/verify → Task 6. Decision 2 (replay kept) is a constraint, not a task.
- Type consistency: `Costs.plan` returns `PayCost`; `PayCost.cost` is `Cost`; `CatacombsResolved.cost: Cost`; `AdjustSupply` amount sign convention (−spend/+gain) consistent across the seven sites and the suite.
- Open verification points (resolve at execution, do not skip): exact `Mustered`/`Traded` field names; `LocatedCard.state` accessor shape for `statefulCard`; fixture starting supply values in the two new suites; names of Catacombs codec/fixture files (grep-driven).

## Addendum (post-execution, approved deviations + review fixes)

- **AdjustSupply scope narrowed (approved mid-execution):** Task 3 Step 7's Challenge / Forge / Recover / Campaign migrations were reverted. Those four flows spend Supply in procedural *start-event* module writes that never pass the executor, and their crafted states do not satisfy the executor's card-inventory invariant (e.g. the Campaign brass fixture legitimately duplicates a relic id between deck and player relics). Forcing `AdjustSupply` through them silently widened invariant gating. Final scope: `AdjustSupply` runs only where an op batch already exists — Travel, Search, Economy (executor-backed); Challenge / Forge / Recover / Campaign / Rest supply writes stay module-authoritative (Rest refresh was already excluded by decision).
- **Rename (code review):** the plan-time clamp helper is `LimitedResource.clamp` — not `SupplyResource.favor`. "Supply" is the Supply-track game concept; the helper bounds any limited pool (e.g. a suit's favor bank). Grep gate uses `LimitedResource`.
- **Cost zero-defaults:** `Cost(favor = 0, secret = 0, favorBurnt = 0, secretBurnt = 0)` defaults added for named-argument ergonomics alongside `Cost.free`; copy-based modifier adjustments unaffected.
- **Shipped commits:** `99fedae` clamp helper · `950a70a` Cost/PayCost · `300e002` Catacombs · `8bd5391` AdjustSupply (executor-backed sites) · `f8a8bb5` Economy · `1947860` docs · `9f10da8` LimitedResource rename.
- **Review fixes (post-merge prep):** Search scaladoc now says Supply is spent by the AdjustSupply op in the batch; `RecoverPowers` locals renamed `payCost`/`expectedCost` (they bind `PayCost`, not `Payment`); ROADMAP Phase-2 commit list includes the docs and rename commits.
