# Follow-up Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A power can queue an action to run after the current one, as its own full walker run that spends no Supply. Knights Errant moves onto the queue, and Hunting Party and Second Wind are built on it.

**Architecture:**

- **Queue.** `CurrentGameState.followUps` holds the queue, and `walkerFollowUp` holds the entry the current run was started from. A primitive op `QueueFollowUp` appends to the queue. Two walker events, `FollowUpStarted` and `FollowUpSkipped`, take the head.
- **Start and skip.** `startWalker` with a non-empty queue accepts only the head's action from the head's player. It evolves `FollowUpStarted` first and then runs the normal start on that state, so the build skips the Act-phase check and the cost waiver sees the entry. `skipFollowUp` emits `FollowUpSkipped`. The application service refuses every other command with `OathViolation.FollowUpPending`.
- **Waiver.** `WalkerPowers.selected` always appends a built-in automatic power, `FollowUpSupplyWaiver`. It applies only at the cost window of the action `walkerFollowUp` names, and strips that player's `SpendSupply` through a shared `SupplyStrip`. Every other unconditional Supply strip in the powers moves onto `SupplyStrip`, and the economy's by-suit `SupplyWaiver` becomes `SuitSupplyWaiver`.
- **Projection.** `followUp: Option[FollowUpProjection]` is added to the projection. The head player gets only `beginFollowUp` and `skipFollowUp`, and the phase reads `follow-up-pending`. The frontend draws a follow-up panel with a start button (disabled, with a hover reason, when the action cannot start) and a Skip button.
- **Game Log.** `FollowUpStarted` joins the queued run's first segment, and the start tag appends the source: "Campaign · Knights Errant". `FollowUpSkipped` writes "Knights Errant: Campaign skipped." into the latest section.
- **Cards.**
  - Knights Errant queues a Campaign unconditionally.
  - Hunting Party queues one after a Search of the world deck. It warns on the discard pile through a new choosable `DecisionOption.Warned`.
  - Second Wind queues a Travel and then a Campaign after an attacker win.

**Tech Stack:** Scala 3 on the JVM (server, munit) and Scala.js 3 (frontend, scalajs-dom, munit on jsdom), built through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-10-08-follow-up-actions-design.md`. Read the whole spec before starting.

## Plan decisions beyond the spec

The spec left these to the plan, or the code forced them. Each task repeats the one it uses.

1. **The waiver is always offered and gated by state.** It is not appended only when `walkerFollowUp` is set. `WalkerPowers.selected` has no state, and most of its callers have none to give. `FollowUpSupplyWaiver.applicable` reads `ctx.state.game.current.walkerFollowUp`, so a command with no entry folds nothing.
2. **One unconditional strip, and two consistently named waivers.** The economy's `private[economy] object SupplyWaiver` (Cup of Plenty, Animal Playmates, Birdsong) cannot absorb the follow-up waiver. It decides at run time from the suit of the answered source card, and it needs `PowerAnswers` from `gameplay.powers`, which the walker does not import. What does repeat is the unconditional strip: `TravelPayments.withoutSupply` (six travel powers), Awaited Return's filter, News from Afar's and Knights Errant's. Task 3 therefore:
   - creates `gameplay/operations/SupplyStrip.scala`, which the walker and the powers may both import, and moves every unconditional strip onto it (Knights Errant's goes in Task 8);
   - renames the economy object to `SuitSupplyWaiver` (its method `effects` stays), so the two waivers read `SuitSupplyWaiver` and `FollowUpSupplyWaiver`.

   The travel strips now also filter a `BuildOps`'s built payments. No `BuildOps` in a travel cost node builds the traveller's payment today (Dragonskin Drum's gain, Tyrant's kill, the Travel's error node), so behaviour is unchanged.
3. **Recover has no cost window.** It pays inside its `Repeat` body with no window, so `FollowUpSupplyWaiver.costWindow` returns `Option[PowerWindow]`, and Recover, like every non-major procedure, maps to `None`. Nothing queues a Recover.
4. **The Campaign reason uses the restriction's own text.** Vow of Peace's restriction already returns `CampaignUnavailable("Vow of Peace prevents its ruler from campaigning")`, and the hover reason shows that text. The spec's "Forbidden by Vow of Peace" was an example. A new wording would be a second copy of the same rule.
5. **The projection's `queuedAfter` is `Vector[String]`, not a count.** It holds the action labels of the entries behind the head ("Campaign"), so the panel can print "Then: Campaign". A count cannot name the action.
6. **Superseded (2026-10-08): Second Wind is an attacker-only battle plan**, not a selected modifier; see the spec's Cards section. It still reads the outcome at the root, through `BattlePlan.later`. The original decision follows. **Second Wind reads the outcome at the Campaign's root.** It does not use `CampaignAfterOutcome`. Like Herald (`powers/campaign/Herald.scala:47-53`), it appends a `Branch` to the `CampaignActionEligibility` children. The `Branch` reads `lastCampaignResult` after the whole outcome, Conquest or Raid included. It checks `attacker == actor && attackerWins`: a selected modifier is only ever offered on the holder's own Campaign.
7. **Hunting Party's warning hook.** The Search source `Decide` has no window, and no wrapper keeps an option choosable while giving it a reason. The smallest hook has three parts:
   - `ContributingPower.warning(decide, option): Option[OptionReason]`, default `None`.
   - A choosable `DecisionOption.Warned(option, reason)`.
   - `OptionWarnings.marked`, which the walker decision projector applies to every choose-one it shows.

   The warning is projection-only: it never reaches the walk, legality or the journal.
8. **Superseded (2026-10-08): dropped with Second Wind's move to a battle plan; Campaign keeps starting directly.** **Campaign starts through the modifier flow.** Second Wind is the first selected Campaign modifier. `ModifierFlowDraft.walkerActions` gains `"campaign"`, so the Campaign button and the follow-up button both offer it.
9. **No journal version bump.** Readers accept only an exact `FormatVersion`, so a bump would stop every stored game, not only the ones holding a nested Knights Errant Campaign. `docs/ROADMAP.md` ("backward compatibility, migrations, and version bumps are not required") makes it optional before release, and the user dropped it (2026-10-08). The spec's ruling 7 records the same.

## Global Constraints

- **Build rules.** Every project builds with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import, private member or local fails the build: delete whatever the compiler names.
- **File size.** Every Scala file stays at or under 800 lines (`scripts/check-architecture.py`). `serialization/WalkerOperationCodec.scala` is at 734. Put new codec code in the new `serialization/FollowUpWire.scala`, never in that file.
- **Ids and wording, exactly:**
  - Op wire kind `queue-follow-up`. Event types are `walker.follow-up-started` and `walker.follow-up-skipped`.
  - The engine power id is `engine.follow-up-supply`.
  - The violation is `OathViolation.FollowUpPending(followUp: FollowUp)`. Its rejection text is `Start or skip the queued {action key} first`.
  - Legal controls are `beginFollowUp` and `skipFollowUp`. The pending phase label is `follow-up-pending`. The intent and wire tag are `skipFollowUp`.
  - Start button label: `{Action label} for no Supply · {source label}`, for example `Campaign for no Supply · Knights Errant`. Skip button label: `Skip`. Line under the panel: `Then: Campaign`. Status for a waiting viewer: `{player} may campaign (Knights Errant)`.
  - Reasons:
    - Campaign with nothing to attack: `No site to conquer and no enemy pawn to raid`.
    - Campaign refused by a restriction: the restriction's `CampaignUnavailable` detail.
    - Travel: `No site to travel to`.
    - Anything else: `Cannot start now`.
  - Log tag: `Campaign · Knights Errant`. Skip line: `Knights Errant: Campaign skipped.`
  - Hunting Party's discard warning is `Hunting Party needs the world deck`. The option state on the wire is `warning`.
  - Card power ids are `denizen.hunting-party` (122) and `denizen.second-wind` (16).
- **Rulings** (spec, "Rulings"):
  1. Opt in after the origin ends.
  2. Nothing runs in between.
  3. The engine waives Supply only.
  4. An unstartable entry stays until it is skipped.
  5. The queue survives the end of the Act phase, and a queued start skips the Act-phase check.
  6. There is no journal compatibility.
  7. Second Wind's entries are independent, and their order is fixed.
  8. New entries go to the back.
- **Never touch the live database `var/oathdigital`.** Never run `preview_start`.
- **Concurrent sessions.** Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree on branch `feat/follow-up-actions`.
  - `EnterWorktree` branches from `origin`, which lags local `main`, so fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
  - Copy `.superpowers/` from the main checkout if a ledger is in use.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Verification commands:**
  - One server suite: `./sbtw "testOnly oathdigital.gameplay.FollowUpRulesSuite"`.
  - One frontend suite: `./sbtw "frontend/testOnly oathdigital.frontend.FollowUpControlsSuite"`.
  - One shared suite: `./sbtw "sharedJVM/testOnly oathdigital.protocol.CommandProtocolSuite"`. If sbt names the shared project differently, run `./sbtw projects` and use the name it prints.
  - Whole server or frontend: `./sbtw test` or `./sbtw frontend/test`.
  - Full gate (Task 11), in order:
    1. `./sbtw "test" "frontend/test"`
    2. `./sbtw frontend/fullOptJS`
    3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
    4. `git diff --check`

---

### Task 1: Queue state, op and events

This task adds the data, the replay and the wire spelling. Nothing emits the op or the events yet.

**Files:**
- Modify: `src/main/scala/oathdigital/model/GameState.scala` (beside `PowerSourceRef`, L87-109; `CurrentGameState`, L136-184)
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala` (beside `RecordCampaignResult`, L523-527)
- Modify: `src/main/scala/oathdigital/gameplay/operations/TurnStateOperations.scala` (beside `recordCampaignResult`, L58-59)
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala` (`applyNonMoveLeaves`, L178-212)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala` (`applyRecordedReady`)
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala:87-94` (`evolve` dispatch)
- Create: `src/main/scala/oathdigital/serialization/FollowUpWire.scala`
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala` (`TurnOperation` union L46-48, `encodeTurnOperation` L212-246, `decodeTurnOperation` L459-499)
- Modify: `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala` (discriminator, encoder, decoder; move the note-source and procedure spellings out)
- Modify: `src/main/scala/oathdigital/serialization/GameEventWire.scala:38-41` (type constants)
- Create: `src/test/scala/oathdigital/gameplay/walker/FollowUpReplaySuite.scala`
- Modify: `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala` (operations list L233-304)
- Modify: `src/test/scala/oathdigital/serialization/WalkerDecisionWireSuite.scala`

**Interfaces:**
- Produces:
  - `final case class FollowUp(player: PlayerId, action: ActionRef, source: PowerSourceRef)` in `oathdigital.model`.
  - `CurrentGameState.followUps: Vector[FollowUp] = Vector.empty` and `CurrentGameState.walkerFollowUp: Option[FollowUp] = None`.
  - `final case class QueueFollowUp(followUp: FollowUp) extends PrimitiveOperation`.
  - `final case class FollowUpStarted(followUp: FollowUp) extends WalkerEvent` and `final case class FollowUpSkipped(followUp: FollowUp) extends WalkerEvent`, both in `oathdigital.gameplay.walker`.
  - `GameEventWire.FollowUpStartedType = "walker.follow-up-started"` and `GameEventWire.FollowUpSkippedType = "walker.follow-up-skipped"`.
  - Replay refusals, exactly:
    - `follow-up start does not match the queue head`
    - `follow-up skip does not match the queue head`
    - `a follow-up started while another run is open`

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/walker/FollowUpReplaySuite.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.walker.DeltaMeaning.OperationApplied
import oathdigital.gameplay.walker.WalkerStepPayload.DeltaRecorded
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** The queue's replay (follow-up actions design, "State, ops and events"):
  * queueing appends, a start takes the head and holds it until the run
  * completes, and a skip takes the head alone. */
class FollowUpReplaySuite extends munit.FunSuite:
  private val knights =
    PowerSourceRef.Card(CatalogNames.denizen("Knights Errant"))
  private val campaign = FollowUp(p1, ActionRef.Campaign, knights)
  private val travel = FollowUp(p1, ActionRef.Travel, knights)

  private def current(state: OathState): CurrentGameState = state match
    case OathState.Ready(ready) => ready.game.current
    case other => fail(s"not ready: $other")

  private def queued(entries: FollowUp*): OathState =
    Table.start.update(_.updateCurrent(_.copy(
      followUps = entries.toVector))).state

  test("queueing appends to the back of the queue"):
    val step = WalkerStepRecorded("0", DeltaRecorded(OperationApplied("queue")),
      Vector(QueueFollowUp(travel)), Vector.empty)
    assertEquals(ProcedureWalker.applyRecorded(queued(campaign), step)
      .map(current(_).followUps), Right(Vector(campaign, travel)))

  test("a start takes the head and holds it until the run completes"):
    val started = ProcedureWalker.applyRecorded(queued(campaign, travel),
      FollowUpStarted(campaign))
    assertEquals(started.map(current(_).followUps), Right(Vector(travel)))
    assertEquals(started.map(current(_).walkerFollowUp), Right(Some(campaign)))
    val completed = started.flatMap(ProcedureWalker.applyRecorded(_,
      WalkerCompleted(ActionRef.Campaign)))
    assertEquals(completed.map(current(_).walkerFollowUp), Right(None))
    assertEquals(completed.map(current(_).followUps), Right(Vector(travel)))

  test("a skip takes the head and nothing else"):
    val skipped = ProcedureWalker.applyRecorded(queued(travel, campaign),
      FollowUpSkipped(travel))
    assertEquals(skipped.map(current(_).followUps), Right(Vector(campaign)))
    assertEquals(skipped.map(current(_).walkerFollowUp), Right(None))

  test("a start or a skip that names another entry than the head is refused"):
    assertEquals(ProcedureWalker.applyRecorded(queued(campaign, travel),
      FollowUpStarted(travel)), Left(OathViolation.InvalidEventOrder(
      "follow-up start does not match the queue head")))
    assertEquals(ProcedureWalker.applyRecorded(queued(campaign, travel),
      FollowUpSkipped(travel)), Left(OathViolation.InvalidEventOrder(
      "follow-up skip does not match the queue head")))

  test("a start while a run is open is refused"):
    val open = Table.start.parkedWalker(ActionRef.Muster)
      .update(_.updateCurrent(_.copy(followUps = Vector(campaign)))).state
    assertEquals(ProcedureWalker.applyRecorded(open, FollowUpStarted(campaign)),
      Left(OathViolation.InvalidEventOrder(
        "a follow-up started while another run is open")))
```

`Table.parkedWalker(action, path = "0", ...)` is at `testkit/Table.scala:326`. Read its signature. If it needs more arguments, pass the defaults its other callers pass.

In `WalkerOperationWireSuite`, append this entry to the `operations` vector of the test "every CoreOperation variant round-trips through the walker codec" (L233-304):

```scala
      QueueFollowUp(FollowUp(PlayerId("p1"), ActionRef.Campaign,
        PowerSourceRef.Card(DenizenId("120")))),
```

In `WalkerDecisionWireSuite`, after the test that round-trips `WalkerParked` and `WalkerCompleted` (L109-121), add:

```scala
  test("a follow-up's start and skip round trip with their entry"):
    val entry = FollowUp(PlayerId("p1"), ActionRef.Travel,
      PowerSourceRef.Card(DenizenId("16")))
    val encoded = encodedStreamThatRoundTrips("follow-ups", Vector[OathEvent](
      FollowUpStarted(entry), FollowUpSkipped(entry)))
    assert(encoded.contains("\"walker.follow-up-started\""), encoded)
    assert(encoded.contains("\"walker.follow-up-skipped\""), encoded)
```

`encodedStreamThatRoundTrips` is the suite's own helper. If its name differs, use the helper the neighbouring `WalkerParked` test uses.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.FollowUpReplaySuite oathdigital.serialization.WalkerOperationWireSuite oathdigital.serialization.WalkerDecisionWireSuite"`
Expected: compilation fails, because `FollowUp`, `QueueFollowUp`, `FollowUpStarted` and `FollowUpSkipped` are not found.

- [ ] **Step 3: Add the model**

In `GameState.scala`, after `PowerSourceRef`'s companion, add:

```scala
/** An action owed to `player` once the current one ends (follow-up actions
  * design): a full action of its own, started or skipped by its player
  * before anything else happens, spending no Supply. `source` is the power
  * that queued it, which the Game Log credits. `player` is the one place an
  * off-turn actor (Sneak Attack) will differ from the active player. */
final case class FollowUp(player: PlayerId, action: ActionRef,
    source: PowerSourceRef)
```

In `CurrentGameState`, after `lastCampaignResult`, add:

```scala
    lastCampaignResult: Option[CampaignResult] = None,
    // Actions owed once the current one ends, oldest first (follow-up
    // actions design). Not walker scratch: it outlives runs and phases, and
    // only `FollowUpStarted` and `FollowUpSkipped` take from it.
    followUps: Vector[FollowUp] = Vector.empty,
    // The entry the current run was started from. Walker scratch: replay
    // sets it from `FollowUpStarted` and `WalkerCompleted` clears it, so the
    // cost waiver and the Act-phase exemption hold on every resume.
    walkerFollowUp: Option[FollowUp] = None
```

In `CoreOperations.scala`, after `RecordCampaignResult`, add:

```scala
/** Appends `followUp` to the back of the queue (follow-up actions design).
  * A source power emits it inside the origin action's tree. */
final case class QueueFollowUp(followUp: FollowUp) extends PrimitiveOperation
```

In `TurnStateOperations.scala`, after `recordCampaignResult`, add:

```scala
  def queueFollowUp(ready: ReadyGame, followUp: FollowUp): ReadyGame =
    ready.updateCurrent(current =>
      current.copy(followUps = current.followUps :+ followUp))
```

In `OperationApplication.applyNonMoveLeaves`, next to the `RecordCampaignResult` case, add:

```scala
        case (result, QueueFollowUp(followUp)) =>
          result.map(TurnStateOperations.queueFollowUp(_, followUp))
```

- [ ] **Step 4: Add the events and their replay**

In `WalkerEvents.scala`, after `WalkerCompleted`, add the two events. Add `FollowUp` to the file's `oathdigital.model` import.

```scala
/** The first event of a run started from the queue's head: replay takes the
  * head and holds it as `walkerFollowUp` until `WalkerCompleted`. An event,
  * not a `WalkerParked` field, because a run can finish in its starting
  * command without parking, and the cost waiver and the Game Log tag still
  * need the entry (follow-up actions design). */
final case class FollowUpStarted(followUp: FollowUp) extends WalkerEvent

/** The head's player declined it: replay takes the head, and the Game Log
  * writes the Skip line. */
final case class FollowUpSkipped(followUp: FollowUp) extends WalkerEvent
```

In `WalkerReplay.applyRecordedReady`, before `case _: PowerNoted`, add:

```scala
      case FollowUpStarted(followUp) =>
        val current = ready.game.current
        for
          _ <- Either.cond(current.followUps.headOption.contains(followUp), (),
            OathViolation.InvalidEventOrder(
              "follow-up start does not match the queue head"))
          _ <- Either.cond(current.walkerProcedure.isEmpty &&
            current.walkerFollowUp.isEmpty, (), OathViolation.InvalidEventOrder(
            "a follow-up started while another run is open"))
        yield ready.updateCurrent(_.copy(followUps = current.followUps.tail,
          walkerFollowUp = Some(followUp)))

      case FollowUpSkipped(followUp) =>
        val current = ready.game.current
        Either.cond(current.followUps.headOption.contains(followUp),
          ready.updateCurrent(_.copy(followUps = current.followUps.tail)),
          OathViolation.InvalidEventOrder(
            "follow-up skip does not match the queue head"))
```

In the `WalkerCompleted` case's `copy`, add `walkerFollowUp = None` beside `walkerStartFacts = Vector.empty`.

`Table.parkedWalker` may set only `walkerPending`, not `walkerProcedure`. If so, also check `current.walkerPending.isEmpty` in the open-run guard.

In `OathRules.evolve` (L87-94), before the `case other: WalkerEvent` fallback, add:

```scala
      case event: FollowUpStarted => ProcedureWalker.applyRecorded(state, event)
      case event: FollowUpSkipped => ProcedureWalker.applyRecorded(state, event)
```

Also extend the `oathdigital.gameplay.walker` import at `OathRules.scala:15`.

- [ ] **Step 5: Add the wire spelling**

Create `src/main/scala/oathdigital/serialization/FollowUpWire.scala`:
- Declare the trait with the same self-type `WalkerOperationCodec` declares.
- Move `encodeNoteSource` and `decodeNoteSource` out of `WalkerEventCodec.scala` (L117-124 and L157-165) into it, renamed `encodePowerSource` and `decodePowerSource`, and update their two callers in `WalkerEventCodec`.
- Move `encodeProcedure` and `decodeProcedure` (`WalkerEventCodec.scala` L243-251) in unchanged, as `protected final`.

```scala
package oathdigital.serialization

import oathdigital.model._

/** Wire spellings shared by the walker's operations and events: a power's
  * source, a procedure reference, and a queued follow-up entry, which holds
  * both (follow-up actions design). */
private[serialization] trait FollowUpWire:
  this: GameEventJsonSupport =>
  import WireError._

  protected final def encodePowerSource(source: PowerSourceRef): ujson.Value =
    source match
      case PowerSourceRef.Site(site) =>
        ujson.Obj("kind" -> "site", "siteId" -> site.value)
      case PowerSourceRef.Card(card) =>
        ujson.Obj("kind" -> "card", "card" -> encodeCardId(card))
      case PowerSourceRef.Banner(banner) =>
        ujson.Obj("kind" -> "banner", "bannerKey" -> banner.key)

  protected final def decodePowerSource(value: ujson.Value,
      path: String): Either[WireError, PowerSourceRef] =
    value("kind").str match
      case "site" => Right(PowerSourceRef.Site(SiteId(value("siteId").str)))
      case "card" => decodeCardId(value("card"), s"$path.card")
        .map(PowerSourceRef.Card.apply)
      case "banner" => decodeBanner(value("bannerKey").str,
        s"$path.bannerKey").map(PowerSourceRef.Banner.apply)
      case other => Left(InvalidValue(s"$path.kind",
        s"unknown note source '$other'"))

  /** Wire spelling of a procedure reference (Task 4): a family tag plus its
    * key, so a decoder can reject a reference read back under the wrong
    * family (an End Wake key spelled `"action"`) or an unknown family,
    * rather than resolving on the key alone.
    */
  protected final def encodeProcedure(procedure: ProcedureRef): ujson.Value =
    ujson.Obj("family" -> procedure.family, "key" -> procedure.key)

  protected final def decodeProcedure(value: ujson.Value,
      path: String): Either[WireError, ProcedureRef] =
    val family = value("family").str
    val key = value("key").str
    ProcedureRef.fromFamilyKey(family, key).toRight(InvalidValue(path,
      s"unknown walker procedure '$family/$key'"))

  protected final def encodeFollowUp(followUp: FollowUp): ujson.Value =
    ujson.Obj(
      "playerId" -> followUp.player.value,
      "action" -> encodeProcedure(followUp.action),
      "source" -> encodePowerSource(followUp.source))

  protected final def decodeFollowUp(value: ujson.Value,
      path: String): Either[WireError, FollowUp] = for
    procedure <- decodeProcedure(value("action"), s"$path.action")
    action <- procedure match
      case action: ActionRef => Right(action)
      case other => Left(InvalidValue(s"$path.action",
        s"a follow-up is an action, not ${other.key}"))
    source <- decodePowerSource(value("source"), s"$path.source")
  yield FollowUp(PlayerId(value("playerId").str), action, source)
```

Make `WalkerOperationCodec` extend `FollowUpWire`. Copy the existing `import WireError._` line, or the qualifier the moved code used, so `InvalidValue` resolves.

In `WalkerOperationCodec`:
- Add `| QueueFollowUp` to the `TurnOperation` union (L46-48).
- In `encodeTurnOperation`, add:

```scala
      case QueueFollowUp(followUp) => ujson.Obj(
        "kind" -> "queue-follow-up",
        "followUp" -> encodeFollowUp(followUp))
```

- In `decodeTurnOperation`, add:

```scala
    case "queue-follow-up" =>
      decodeFollowUp(value("followUp"), s"$path.followUp")
        .map(QueueFollowUp(_))
```

If the encode-side dispatch to `encodeTurnOperation` is a type test on the union, the compiler names the line to extend.

In `GameEventWire.scala`, after `PowerNotedType`, add:

```scala
  val FollowUpStartedType = "walker.follow-up-started"
  val FollowUpSkippedType = "walker.follow-up-skipped"
```

In `WalkerEventCodec`, import the two events, then add:
- to `walkerDiscriminator`: `case _: FollowUpStarted => FollowUpStartedType` and `case _: FollowUpSkipped => FollowUpSkippedType`;
- to `walkerEncoder`: `case FollowUpStarted(followUp) => ujson.Obj("followUp" -> encodeFollowUp(followUp))` and the same for `FollowUpSkipped`;
- to `walkerDecode`:

```scala
      case FollowUpStartedType =>
        decodeFollowUp(payload("followUp"), s"$path.followUp")
          .map(FollowUpStarted(_))
      case FollowUpSkippedType =>
        decodeFollowUp(payload("followUp"), s"$path.followUp")
          .map(FollowUpSkipped(_))
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.FollowUpReplaySuite oathdigital.serialization.*"`
Expected: PASS.

- [ ] **Step 7: Run the whole server suite**

Run: `./sbtw test`
Expected: PASS. If a suite compares `CurrentGameState` field lists, add the two new fields there.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/model/GameState.scala src/main/scala/oathdigital/model/CoreOperations.scala src/main/scala/oathdigital/gameplay/operations/TurnStateOperations.scala src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala src/main/scala/oathdigital/gameplay/OathRules.scala src/main/scala/oathdigital/serialization/FollowUpWire.scala src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala src/main/scala/oathdigital/serialization/WalkerEventCodec.scala src/main/scala/oathdigital/serialization/GameEventWire.scala src/test/scala/oathdigital/gameplay/walker/FollowUpReplaySuite.scala src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala src/test/scala/oathdigital/serialization/WalkerDecisionWireSuite.scala
git commit -m "feat(walker): add the follow-up queue, its op and its events"
```

---

### Task 2: Starting and skipping in the rules

**Files:**
- Modify: `src/main/scala/oathdigital/model/GameViolation.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala` (`startWalker` L61-91; new `startFollowUp`, `skipFollowUp`)
- Modify: `src/main/scala/oathdigital/gameplay/OathLifecycle.scala:23-38` (`validateAct`)
- Create: `src/test/scala/oathdigital/gameplay/FollowUpRulesSuite.scala`

**Interfaces:**
- Consumes: Task 1's `FollowUp`, `FollowUpStarted`, `FollowUpSkipped` and `CurrentGameState.followUps` and `walkerFollowUp`.
- Produces:
  - `OathViolation.FollowUpPending(followUp: FollowUp)`.
  - `OathRulesWalker.skipFollowUp(state: OathState, requester: PlayerId): Either[OathViolation, OathTransition]`, reached as `rules.skipFollowUp` on `OathRules`.
  - `startWalker`, when the queue is not empty, accepts only `head.action` from `head.player`, and its transition's first event is `FollowUpStarted(head)`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/FollowUpRulesSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{FollowUpSkipped, FollowUpStarted,
  WalkerCompleted}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{CatalogNames, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** Starting and skipping a queued action (follow-up actions design, "Start
  * and skip"). Entries are placed on the table directly: which power queues
  * them is each card's own suite. */
class FollowUpRulesSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = WalkerDiceFixture.blanks)
  private val ancientCity = CatalogNames.site("Ancient City")
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  private val knights =
    PowerSourceRef.Card(CatalogNames.denizen("Knights Errant"))

  private def owed(action: ActionRef): FollowUp = FollowUp(p1, action, knights)

  /** p1 at Ancient City, which two bandits rule, so a Conquest is legal; p2 at
    * Broken Peaks and p3 at Buried Giant. `queue` is owed to p1. */
  private def table(queue: FollowUp*): Table = Table.start
    .pawn(p1, at = ancientCity).pawn(p2, at = brokenPeaks)
    .pawn(p3, at = "Buried Giant")
    .bandits(ancientCity, 2)
    .update(_.updateCurrent(_.copy(followUps = queue.toVector)))

  private def current(state: OathState): CurrentGameState =
    Outcomes.ready(state).game.current

  /** Folds `events` from `from` through the replay path. */
  private def replayed(from: OathState, events: Vector[OathEvent])
      : OathState =
    accepted(events.foldLeft[Either[OathViolation, OathState]](Right(from))(
      (state, event) => state.flatMap(rules.evolve(_, event))))

  test("the queued start takes the head and opens with FollowUpStarted"):
    val start = table(owed(ActionRef.Campaign)).state
    val started = accepted(rules.startWalker(start, ActionRef.Campaign, p1))
    assertEquals(started.events.head,
      FollowUpStarted(owed(ActionRef.Campaign)): OathEvent)
    assertEquals(current(started.state).followUps, Vector.empty)
    assertEquals(current(started.state).walkerFollowUp,
      Some(owed(ActionRef.Campaign)))
    assertEquals(replayed(start, started.events), started.state)

  test("another action than the head's is refused"):
    assertEquals(rules.startWalker(table(owed(ActionRef.Campaign)).state,
      ActionRef.Travel, p1),
      Left(OathViolation.FollowUpPending(owed(ActionRef.Campaign))))

  test("another player than the head's is refused"):
    assertEquals(rules.startWalker(table(owed(ActionRef.Campaign)).state,
      ActionRef.Campaign, p2), Left(OathViolation.WrongPlayer(p1, p2)))

  test("a start that fails leaves the whole command refused"):
    // No bandits and no enemy pawn at Ancient City: nothing to campaign
    // against. A rules call is pure, so a refusal records nothing.
    val board = table(owed(ActionRef.Campaign)).bandits(ancientCity, 0).state
    assert(rules.startWalker(board, ActionRef.Campaign, p1).isLeft)

  test("Skip takes the head, and the next entry is owed"):
    val start = table(owed(ActionRef.Travel), owed(ActionRef.Campaign)).state
    val skipped = accepted(rules.skipFollowUp(start, p1))
    assertEquals(skipped.events,
      Vector[OathEvent](FollowUpSkipped(owed(ActionRef.Travel))))
    assertEquals(current(skipped.state).followUps,
      Vector(owed(ActionRef.Campaign)))
    assertEquals(replayed(start, skipped.events), skipped.state)

  test("Skip is refused with nothing queued, and from another player"):
    assertEquals(rules.skipFollowUp(table().state, p1),
      Left(OathViolation.InvalidEventOrder("no follow-up action is queued")))
    assertEquals(rules.skipFollowUp(
      table(owed(ActionRef.Campaign)).state, p2),
      Left(OathViolation.WrongPlayer(p1, p2)))

  test("a queued run's completion leaves the next entry owed"):
    val start = table(owed(ActionRef.Travel), owed(ActionRef.Campaign)).state
    val started = accepted(rules.startWalker(start, ActionRef.Travel, p1))
    val done = accepted(rules.resolveWalker(started.state, p1,
      TravelProcedure.destinationDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Site(brokenPeaks))))
    assert(done.events.contains(WalkerCompleted(ActionRef.Travel)),
      done.events)
    assertEquals(current(done.state).walkerFollowUp, None)
    assertEquals(current(done.state).followUps,
      Vector(owed(ActionRef.Campaign)))
    assertEquals(replayed(start, started.events ++ done.events), done.state)

  test("a queued action starts outside the Act phase"):
    val resting = table(owed(ActionRef.Campaign)).turn(p1, Phase.Rest).state
    assert(rules.startWalker(resting, ActionRef.Campaign, p1).isRight)

  test("without a queue, a Campaign still needs the Act phase"):
    assertEquals(rules.startWalker(table().turn(p1, Phase.Rest).state,
      ActionRef.Campaign, p1),
      Left(OathViolation.WrongPhase(Phase.Act, Phase.Rest)))
```

If the Travel to Broken Peaks parks on a further decision before it completes, answer that decision the way `TravelProcedureSuite` does for the same board. Do not change the destination.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.FollowUpRulesSuite"`
Expected: compilation fails, because `FollowUpPending` and `skipFollowUp` are not found.

- [ ] **Step 3: Add the violation**

In `GameViolation.scala`, inside `object OathViolation`, add:

```scala
  /** A queued action waits for its player to start or skip it, and nothing
    * else may happen first (follow-up actions design, ruling 3). */
  final case class FollowUpPending(followUp: FollowUp) extends OathViolation
```

- [ ] **Step 4: Start from the queue, and skip**

In `OathRulesWalker.scala`:
- Import `FollowUpSkipped` and `FollowUpStarted` from `oathdigital.gameplay.walker`.
- Split `startWalker` into a dispatch and a `startNow` that holds today's body.
- Replace the `case Ready(ready) =>` arm of `startWalker` with the dispatch, and move its old body, unchanged, into `startNow`.
- Add `startFollowUp` and `skipFollowUp`.

```scala
      case Ready(ready) => ready.game.current.followUps.headOption match
        case Some(head) => startFollowUp(state, head, procedure, requester,
          modifiers, startArgs)
        case None => startNow(state, ready, procedure, requester, modifiers,
          startArgs)
```

```scala
  /** The normal start: what `startWalker` did before the follow-up queue. */
  private def startNow(state: OathState, ready: ReadyGame,
      procedure: StartableRef, requester: PlayerId, modifiers: Vector[PowerId],
      startArgs: Vector[DecisionOptionRef])
      : Either[OathViolation, OathTransition] =
    val activePlayer = ready.game.current.turn.activePlayer
    def run =
      for
        _ <- requireActivePlayer(ready, requester)
        _ <- validateModifiers(ready, activePlayer, procedure, modifiers)
        powers = walkerPowers(ready, activePlayer, modifiers)
        tree <- buildWalker(procedure, ready, activePlayer, startArgs,
          starting = true)
        _ <- checkRestrictions(tree, powers, ready, activePlayer)
        outcome <- walkerCall(ProcedureWalker.advance(ready, tree, None,
          powers, walkerDice))
        _ <- requirePlayableOption(procedure, ready, tree, outcome, powers)
        transition <- walkerTransition(state, procedure, tree,
          outcome, powers, modifiers, startArgs,
          WalkerProcedureRegistry.startFacts(procedure, catalog, ready,
            activePlayer))
      yield transition
    WalkerProcedureRegistry.fallbackKind(procedure).flatMap:
      case Some(kind) => withFallback(state, activePlayer, kind)(run)
      case None => run

  /** Starts the queue's head (follow-up actions design, "Start and skip").
    * The requester is compared with the entry's player here and nowhere
    * else, so an off-turn actor (Sneak Attack) changes only this comparison.
    * `FollowUpStarted` is evolved before the normal start runs on the state
    * it leaves, so the build skips the Act-phase check (`OathLifecycle`) and
    * the cost waiver sees the entry. A start that fails refuses the whole
    * command, so the queue is unchanged.
    */
  private def startFollowUp(state: OathState, head: FollowUp,
      procedure: StartableRef, requester: PlayerId, modifiers: Vector[PowerId],
      startArgs: Vector[DecisionOptionRef])
      : Either[OathViolation, OathTransition] =
    val started = FollowUpStarted(head)
    for
      _ <- Either.cond(requester == head.player, (),
        WrongPlayer(head.player, requester))
      _ <- Either.cond(procedure == head.action, (), FollowUpPending(head))
      afterStart <- evolve(state, started)
      ready <- afterStart match
        case Ready(live) => Right(live)
        case _ => Left(GameNotStarted)
      transition <- startNow(afterStart, ready, procedure, requester,
        modifiers, startArgs)
    yield transition.copy(events = started +: transition.events)

  /** Declines the queue's head; the next entry is owed next, or the normal
    * controls return. Accepted whether or not the head could start. */
  def skipFollowUp(state: OathState, requester: PlayerId)
      : Either[OathViolation, OathTransition] = state match
    case Ready(ready) if ready.game.current.result.nonEmpty => Left(GameEnded)
    case Ready(ready) if ready.game.current.walkerPending.nonEmpty ||
        ready.game.current.walkerProcedure.nonEmpty =>
      Left(InvalidEventOrder("a walker procedure is pending"))
    case Ready(ready) => ready.game.current.followUps.headOption match
      case None => Left(InvalidEventOrder("no follow-up action is queued"))
      case Some(head) if head.player != requester =>
        Left(WrongPlayer(head.player, requester))
      case Some(head) =>
        GameplayTransition(state, Vector(FollowUpSkipped(head)))(evolve)
    case _ => Left(GameNotStarted)
```

`startNow`'s body is the old `run` block moved as is. Keep the `startWalker` doc comment above `startWalker`, and add one sentence to it: "With a queue, only the head may start (`startFollowUp`)."

If `OathRules` exposes the walker's methods through an explicit forwarder instead of the mixin, add a `skipFollowUp` forwarder there too.

- [ ] **Step 5: Exempt a queued run from the Act-phase check**

In `OathLifecycle.validateAct`, replace the phase line:

```scala
      // A queued action may run after the Act phase has ended (follow-up
      // actions design, ruling 6); its start has set `walkerFollowUp`.
      else if current.turn.phase != Phase.Act &&
          current.walkerFollowUp.isEmpty then
        Left(WrongPhase(Phase.Act, current.turn.phase))
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.FollowUpRulesSuite"`
Expected: PASS.

- [ ] **Step 7: Audit the Campaign and Travel paths for Act-phase assumptions**

Run: `grep -rn "Phase.Act\|turn.phase" src/main/scala/oathdigital/gameplay/actions/campaign src/main/scala/oathdigital/gameplay/actions/travel src/main/scala/oathdigital/gameplay/powers/campaign src/main/scala/oathdigital/gameplay/powers/travel`
Expected: no output. The exploration found none outside `OathLifecycle.validateAct`. If any line prints, read it. A phase read that would refuse or change a queued Campaign or Travel in Rest gets the same `walkerFollowUp` exemption, plus a test in this suite.

- [ ] **Step 8: Run the whole server suite and commit**

Run: `./sbtw test`
Expected: PASS.

```bash
git add src/main/scala/oathdigital/model/GameViolation.scala src/main/scala/oathdigital/gameplay/OathRulesWalker.scala src/main/scala/oathdigital/gameplay/OathLifecycle.scala src/test/scala/oathdigital/gameplay/FollowUpRulesSuite.scala
git commit -m "feat(rules): start a queued action, or skip it"
```

Also stage `OathRules.scala` if it gained a forwarder.

---

### Task 3: The Supply waiver

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/operations/SupplyStrip.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/search/NewsFromAfar.scala:29-38`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/AwaitedReturn.scala` (the `TradeCost` filter)
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TravelPayments.scala` (delete `withoutSupply`), and its callers `WayStation.scala` (2), `AFastSteed.scala`, `ForestPaths.scala`, `Tents.scala`, `SpecialEnvoy.scala`
- Rename: `src/main/scala/oathdigital/gameplay/powers/economy/SupplyWaiver.scala` to `SuitSupplyWaiver.scala`, and its callers `CupOfPlenty.scala`, `AnimalPlaymates.scala`, `Birdsong.scala`
- Create: `src/main/scala/oathdigital/gameplay/walker/FollowUpSupplyWaiver.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowers.scala` (`selected`)
- Create: `src/test/scala/oathdigital/gameplay/FollowUpSupplyWaiverSuite.scala`

**Interfaces:**
- Consumes: Task 2's queued start, and `CurrentGameState.walkerFollowUp`.
- Produces:
  - `SupplyStrip(actor: PlayerId, operations: Vector[Operation]): Vector[Operation]`.
  - `FollowUpSupplyWaiver.id = PowerId("engine.follow-up-supply")`.
  - `FollowUpSupplyWaiver.costWindow(action: ActionRef): Option[PowerWindow]`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/FollowUpSupplyWaiverSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.travel.SpecialEnvoy
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerStepRecorded
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** A queued action spends no Supply (follow-up actions design, ruling 4),
  * on its start and on every resume, and a queued action may run in Rest. */
class FollowUpSupplyWaiverSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = WalkerDiceFixture.blanks)
  private val ancientCity = CatalogNames.site("Ancient City")
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  private val knights =
    PowerSourceRef.Card(CatalogNames.denizen("Knights Errant"))

  private def owed(action: ActionRef): FollowUp = FollowUp(p1, action, knights)

  /** p1 at Ancient City, which two bandits rule, with `supply` Supply; p2 at
    * Broken Peaks and p3 at Buried Giant. `queue` is owed to p1. */
  private def table(supply: Int, queue: FollowUp*): Table = Table.start
    .pawn(p1, at = ancientCity).pawn(p2, at = brokenPeaks)
    .pawn(p3, at = "Buried Giant")
    .bandits(ancientCity, 2).supply(p1, supply)
    .update(_.updateCurrent(_.copy(followUps = queue.toVector)))

  private def spent(events: Vector[OathEvent]): Vector[SpendSupply] =
    events.collect { case step: WalkerStepRecorded => step.ops }.flatten
      .collect { case spend: SpendSupply => spend }

  test("a queued Campaign spends no Supply, even with none to spend"):
    val started = accepted(rules.startWalker(
      table(supply = 0, owed(ActionRef.Campaign)).state,
      ActionRef.Campaign, p1))
    assertEquals(spent(started.events), Vector.empty)
    assertEquals(Look(Outcomes.ready(started.state)).supply(p1), 0)

  test("the same Campaign started normally spends its Supply"):
    val started = accepted(rules.startWalker(table(supply = 2).state,
      ActionRef.Campaign, p1))
    assertEquals(Look(Outcomes.ready(started.state)).supply(p1), 0)
    assert(rules.startWalker(table(supply = 0).state, ActionRef.Campaign, p1)
      .isLeft)

  test("the waiver holds on a resume: a queued Travel pays after its " +
      "destination is chosen"):
    val started = accepted(rules.startWalker(
      table(supply = 0, owed(ActionRef.Travel)).state, ActionRef.Travel, p1))
    val done = accepted(rules.resolveWalker(started.state, p1,
      TravelProcedure.destinationDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Site(brokenPeaks))))
    assertEquals(spent(done.events), Vector.empty)
    assertEquals(Look(Outcomes.ready(done.state)).pawn(p1), Some(brokenPeaks))

  test("a queued Travel with Special Envoy enters Rest, and the queued " +
      "Campaign still starts there"):
    val board = table(supply = 0, owed(ActionRef.Travel),
      owed(ActionRef.Campaign)).adviser(p1, "Special Envoy").state
    val started = accepted(rules.startWalker(board, ActionRef.Travel, p1,
      Vector(SpecialEnvoy.id)))
    val travelled = accepted(rules.resolveWalker(started.state, p1,
      TravelProcedure.destinationDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Site(brokenPeaks))))
    assertEquals(Look(Outcomes.ready(travelled.state)).phase, Phase.Rest)
    assertEquals(Outcomes.ready(travelled.state).game.current.followUps,
      Vector(owed(ActionRef.Campaign)))
    // p2 stands at Broken Peaks, so a Raid is legal there.
    assert(rules.startWalker(travelled.state, ActionRef.Campaign, p1).isRight)
```

`Look.pawn` returns `player(p).pawnSite`, an `Option[SiteId]` if the field is optional. Match the assertion to its type.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.FollowUpSupplyWaiverSuite"`
Expected: FAIL. The first test's start is refused for insufficient Supply, and the Travel tests spend Supply.

- [ ] **Step 3: Extract the strip**

Create `src/main/scala/oathdigital/gameplay/operations/SupplyStrip.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.model.{BuildOps, Operation, PlayerId, SpendSupply}

/** Removes `actor`'s Supply payments from a cost window's children, whether
  * they are listed (a Campaign's, a Travel's) or built when the node runs (a
  * Search's `BuildOps`, re-wrapped to filter what it builds). Everything else
  * stays: a Travel's cost window also holds its pawn move.
  */
object SupplyStrip:
  def apply(actor: PlayerId, operations: Vector[Operation]): Vector[Operation] =
    operations.flatMap {
      case SpendSupply(player, _, _) if player == actor => Vector.empty
      case payment: BuildOps => Vector(payment.copy(build = (ready, pending) =>
        payment.build(ready, pending).map(_.filterNot {
          case SpendSupply(player, _, _) => player == actor
          case _ => false
        })))
      case other => Vector(other)
    }
```

In `NewsFromAfar.scala`, replace the `SearchCost` Transform body (L29-38) with:

```scala
  PowerWindow.SearchCost -> Vector(Transform((ctx, operations) =>
    SupplyStrip(ctx.activePlayer, operations))))
```

Import `oathdigital.gameplay.operations.SupplyStrip`, and delete any import the compiler then names unused.

Move the other unconditional strips onto it (plan decision 2):
- `AwaitedReturn.scala`: replace the `operations.filterNot { case SpendSupply … }` in its `TradeCost` Transform with `SupplyStrip(ctx.activePlayer, operations)`, keeping the `++ Vector(sacrifice(…), Note(…))` after it.
- `TravelPayments.scala`: delete `withoutSupply` and its doc. In each caller, replace `TravelPayments.withoutSupply(operations, X)` with `SupplyStrip(X, operations)`. The argument order flips: the actor comes first.

Rename the economy waiver: `git mv src/main/scala/oathdigital/gameplay/powers/economy/SupplyWaiver.scala src/main/scala/oathdigital/gameplay/powers/economy/SuitSupplyWaiver.scala`, rename the object to `SuitSupplyWaiver`, and change `SupplyWaiver.effects(` to `SuitSupplyWaiver.effects(` in `CupOfPlenty.scala`, `AnimalPlaymates.scala` and `Birdsong.scala`. Check that nothing else names it: `grep -rn "SupplyWaiver\b\|withoutSupply" src docs`.

Run: `./sbtw "testOnly oathdigital.gameplay.powers.*"`
Expected: PASS. The strips behave as before.

- [ ] **Step 4: Add the waiver**

Create `src/main/scala/oathdigital/gameplay/walker/FollowUpSupplyWaiver.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.SupplyStrip
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  PowerCtx, Transform}
import oathdigital.model.{ActionRef, PowerId, PowerWindow, RuleSourceRef}

/** The engine's waiver for a queued action (follow-up actions design,
  * ruling 4): at the cost window of the action `walkerFollowUp` names, the
  * entry's player spends no Supply. The source power takes no part.
  *
  * `WalkerPowers.selected` offers it to every command, so every walker path
  * (start, every resume, the payment dry run, a start control's dry run, a
  * preview) folds it the same way. It applies only while the run was started
  * from the queue, which `applicable` reads from state; a command with no
  * entry folds nothing.
  */
object FollowUpSupplyWaiver extends ContributingPower:
  val id: PowerId = PowerId("engine.follow-up-supply")
  val source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  /** Where each action pays its Supply. Recover pays inside its repeat with
    * no window, and every other procedure spends none. */
  def costWindow(action: ActionRef): Option[PowerWindow] = action match
    case ActionRef.Campaign => Some(PowerWindow.CampaignCost)
    case ActionRef.Travel => Some(PowerWindow.TravelCost)
    case ActionRef.Search => Some(PowerWindow.SearchCost)
    case ActionRef.Muster => Some(PowerWindow.MusterCost)
    case ActionRef.Trade => Some(PowerWindow.TradeCost)
    case ActionRef.Forge => Some(PowerWindow.ForgeCost)
    case ActionRef.Challenge => Some(PowerWindow.ChallengeCost)
    case _ => None

  private val windows: Vector[PowerWindow] = Vector(PowerWindow.CampaignCost,
    PowerWindow.TravelCost, PowerWindow.SearchCost, PowerWindow.MusterCost,
    PowerWindow.TradeCost, PowerWindow.ForgeCost, PowerWindow.ChallengeCost)

  lazy val contributions: Map[PowerWindow, Vector[Contribution]] =
    windows.map(window => window -> Vector[Contribution](Transform(
      (ctx, operations) => ctx.state.game.current.walkerFollowUp
        .fold(operations)(entry => SupplyStrip(entry.player, operations)))))
      .toMap

  override def applicable(ctx: PowerCtx): Boolean =
    ctx.state.game.current.walkerFollowUp.exists(entry =>
      costWindow(entry.action).contains(ctx.window))
```

If `ContributingPower` (via `NotingPower`) declares another abstract member, the compiler names it. Give it the value an automatic rule power with no notes gives. `HomelandLine` and the reviewed handlers in `powers/` are examples.

In `WalkerPowers.selected`, append the waiver and extend the doc:

```scala
  /** ... (keep the existing text) ... The engine's follow-up waiver is
    * always offered; it applies only to a run started from the queue. */
  def selected(catalog: WalkerPowers, modifiers: Vector[PowerId]): WalkerPowers =
    catalog.copy(powers = catalog.powers.filter(power =>
      power.resolution == PowerResolution.Automatic ||
        modifiers.contains(power.id)) :+ FollowUpSupplyWaiver,
      modifiers = modifiers)
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.FollowUpSupplyWaiverSuite oathdigital.gameplay.powers.search.*"`
Expected: PASS, News from Afar's suite included.

- [ ] **Step 6: Run the whole server suite**

Run: `./sbtw test`
Expected: PASS. A suite that asserts the exact `powers` of a `WalkerPowers.selected` result now sees the waiver last. Update that expectation, and keep any assertion about which catalog powers were chosen.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/operations/SupplyStrip.scala src/main/scala/oathdigital/gameplay/powers/search/NewsFromAfar.scala src/main/scala/oathdigital/gameplay/powers/economy/AwaitedReturn.scala src/main/scala/oathdigital/gameplay/powers/economy/SupplyWaiver.scala src/main/scala/oathdigital/gameplay/powers/economy/SuitSupplyWaiver.scala src/main/scala/oathdigital/gameplay/powers/economy/CupOfPlenty.scala src/main/scala/oathdigital/gameplay/powers/economy/AnimalPlaymates.scala src/main/scala/oathdigital/gameplay/powers/economy/Birdsong.scala src/main/scala/oathdigital/gameplay/powers/travel/TravelPayments.scala src/main/scala/oathdigital/gameplay/powers/travel/WayStation.scala src/main/scala/oathdigital/gameplay/powers/travel/AFastSteed.scala src/main/scala/oathdigital/gameplay/powers/travel/ForestPaths.scala src/main/scala/oathdigital/gameplay/powers/travel/Tents.scala src/main/scala/oathdigital/gameplay/powers/travel/SpecialEnvoy.scala src/main/scala/oathdigital/gameplay/walker/FollowUpSupplyWaiver.scala src/main/scala/oathdigital/gameplay/walker/WalkerPowers.scala src/test/scala/oathdigital/gameplay/FollowUpSupplyWaiverSuite.scala
git commit -m "feat(walker): a queued action spends no Supply"
```

Also stage any suite Step 6 updated.

---

### Task 4: The Skip command and the service gate

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandIntents.scala:9-11`
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandIntentCodec.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandIntentDecoders.scala`
- Modify: `src/main/scala/oathdigital/application/GameCommands.scala` (`GameCommand`, `issuer`)
- Modify: `src/main/scala/oathdigital/application/Authorization.scala:54-58`
- Modify: `src/main/scala/oathdigital/application/GameIntentMapper.scala:16-18`
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala` (`applyCommand` L429-441, `applyUnblockedCommand` L443-522)
- Modify: `server/src/main/scala/oathdigital/server/GameRoutes.scala:28-39` (`CommandRejectionMessage.text`). Find the path with `grep -rn "object CommandRejectionMessage" server`.
- Modify: `src/test/scala/oathdigital/testkit/SituationDriver.scala` (`Rules.transition`)
- Modify: `shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala:9`
- Create: `src/test/scala/oathdigital/application/FollowUpServiceSuite.scala`

**Interfaces:**
- Consumes: Task 2's `skipFollowUp` and `FollowUpPending`.
- Produces:
  - `GameIntent.SkipFollowUp` (wire tag `skipFollowUp`).
  - `GameCommand.SkipFollowUp(playerId: PlayerId)`.
  - `Authorization`'s `skipFollowUp: GameCommand`.

- [ ] **Step 1: Write the failing tests**

In `CommandProtocolSuite`, add `GameIntent.SkipFollowUp` to the round-trip list at L9, beside `FinishRest`.

Create `src/test/scala/oathdigital/application/FollowUpServiceSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.walker.FollowUpSkipped
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

/** While an action is queued, the service accepts only its start and its
  * skip, from its player (follow-up actions design, ruling 3). */
class FollowUpServiceSuite extends munit.FunSuite:
  private val ancientCity = CatalogNames.site("Ancient City")
  private val knights =
    PowerSourceRef.Card(CatalogNames.denizen("Knights Errant"))
  private val campaign = FollowUp(p1, ActionRef.Campaign, knights)

  /** A fresh service at p1's Act with a Campaign owed to p1: two bandits rule
    * Ancient City, so it can start. */
  private def queued(using munit.Location): GameApplicationService =
    Table.start.pawn(p1, at = ancientCity).pawn(p2, at = "Broken Peaks")
      .pawn(p3, at = "Buried Giant").bandits(ancientCity, 2)
      .update(_.updateCurrent(_.copy(followUps = Vector(campaign))))
      .service()._1

  private def refused(command: GameCommand)(using munit.Location)
      : Option[OathViolation] =
    queued.handle("game", 0L, command) match
      case Left(CommandRejected(violation)) => Some(violation)
      case _ => None

  test("other actions, minor actions and other players are refused"):
    val pending = Some(OathViolation.FollowUpPending(campaign))
    assertEquals(refused(GameCommand.StartWalker(ActionRef.Travel,
      StartPayload(p1))), pending)
    assertEquals(refused(GameCommand.PeekSiteRelics(p1)), pending)
    assertEquals(refused(GameCommand.BeginRest(p1)), pending)
    assertEquals(refused(GameCommand.StartWalker(ActionRef.Campaign,
      StartPayload(p2))), pending)
    assertEquals(refused(GameCommand.SkipFollowUp(p2)), pending)

  test("the head's start and its skip are accepted"):
    assert(queued.handle("game", 0L, GameCommand.StartWalker(
      ActionRef.Campaign, StartPayload(p1))).isRight)
    val skipped = queued.handle("game", 0L, GameCommand.SkipFollowUp(p1))
    assertEquals(skipped.map(_.events.collect {
      case skip: FollowUpSkipped => skip }),
      Right(Vector(FollowUpSkipped(campaign))))
```

Check two things before you run it:
- `CommandRejected` must be the `GameApplicationError` case `handleAgainst` wraps a violation in (`GameApplicationService.scala:425`). If it is nested in a companion, import it from there.
- `service.handle(gameId, nextSequence, command)` must be the signature the `Journaled` driver calls (`SituationDriver.scala`, `service.handle(gameId, from.nextSequence, command)`).

A stream that does not exist yet starts at sequence 0 from the table's genesis.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.FollowUpServiceSuite"`
Expected: compilation fails, because `GameCommand.SkipFollowUp` is not found.

- [ ] **Step 3: Thread the intent and the command**

Follow `FinishRest` through every layer. Each edit sits beside its `FinishRest` line:
- `CommandIntents.scala`: `case object SkipFollowUp extends GameIntent`
- `CommandIntentCodec.scala`: `case SkipFollowUp => tagged("skipFollowUp")`
- `CommandIntentDecoders.scala`: `case "skipFollowUp" => empty(value, path, SkipFollowUp)`
- `GameCommands.scala`:
  - Add the command:

    ```scala
      /** Declines the queued action at the head of the queue (follow-up
        * actions design). */
      final case class SkipFollowUp(playerId: PlayerId) extends GameCommand
    ```

  - Add `case SkipFollowUp(player) => Some(player)` to `issuer`.
- `Authorization.scala`: `def skipFollowUp: GameCommand = GameCommand.SkipFollowUp(access.playerId)`
- `GameIntentMapper.scala`: `case Intent.SkipFollowUp => Right(actor.skipFollowUp)`
- `GameApplicationService.applyUnblockedCommand`: `case GameCommand.SkipFollowUp(playerId) => rules.skipFollowUp(state, playerId)`
- `SituationDriver.Rules.transition`: `case GameCommand.SkipFollowUp(player) => rules.skipFollowUp(state, player)`

- [ ] **Step 4: Add the gate**

In `GameApplicationService.applyCommand`, between the walker arm and `case _`, add:

```scala
      // A queued action waits for its player: only its start and its skip
      // are legal (follow-up actions design, ruling 3). Undo and Redo never
      // reach here, so a player can still take the origin back.
      case OathState.Ready(ready) if ready.game.current.result.isEmpty &&
          ready.game.current.followUps.headOption.exists(head =>
            !answersFollowUp(head, command)) =>
        Left(OathViolation.FollowUpPending(ready.game.current.followUps.head))
```

Below `isWalkerResume`, add:

```scala
  /** Whether `command` starts or skips `head`, as its player. */
  private def answersFollowUp(head: FollowUp, command: GameCommand): Boolean =
    command match
      case GameCommand.WithModifiers(inner, _) => answersFollowUp(head, inner)
      case GameCommand.StartWalker(procedure, start) =>
        procedure == head.action && start.actor == head.player
      case GameCommand.SkipFollowUp(player) => player == head.player
      case _ => false
```

In `CommandRejectionMessage.text`, add a case before the fallback:

```scala
    case OathViolation.FollowUpPending(followUp) =>
      s"Start or skip the queued ${followUp.action.key} first"
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.application.FollowUpServiceSuite" "sharedJVM/testOnly oathdigital.protocol.CommandProtocolSuite"`
Expected: PASS.

- [ ] **Step 6: Run the whole build and commit**

Run: `./sbtw test frontend/test`
Expected: PASS. A frontend match over `GameIntent` that the compiler reports as non-exhaustive gets the new case, mirroring `FinishRest`.

```bash
git add shared/src/main/scala/oathdigital/protocol/CommandIntents.scala shared/src/main/scala/oathdigital/protocol/CommandIntentCodec.scala shared/src/main/scala/oathdigital/protocol/CommandIntentDecoders.scala src/main/scala/oathdigital/application/GameCommands.scala src/main/scala/oathdigital/application/Authorization.scala src/main/scala/oathdigital/application/GameIntentMapper.scala src/main/scala/oathdigital/application/GameApplicationService.scala src/test/scala/oathdigital/testkit/SituationDriver.scala shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala src/test/scala/oathdigital/application/FollowUpServiceSuite.scala
git add server/src/main/scala/oathdigital/server/GameRoutes.scala
git commit -m "feat(application): skip a queued action, and refuse anything else first"
```

Use the actual path of `CommandRejectionMessage` in the second `git add`.

---

### Task 5: Projection

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala` (`starts`, L80-91)
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala` (`build`, `startable`)
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/GameProjectionDto.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/GameProjectionCodec.scala` (`Fields` L12-20, encode L29-79, decode L81-145)
- Modify: `src/main/scala/oathdigital/application/ScopedProjectionContext.scala` (`LegalProjection`)
- Modify: `src/main/scala/oathdigital/application/LegalActionProjector.scala`
- Modify: `src/main/scala/oathdigital/application/PendingProjector.scala`
- Modify: `src/main/scala/oathdigital/application/GameProjection.scala` (L69-72, L93-94, L107)
- Modify: `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`
- Create: `src/test/scala/oathdigital/application/FollowUpProjectionSuite.scala`

**Interfaces:**
- Consumes: Tasks 1-3.
- Produces:
  - Wire DTO in `oathdigital.protocol.projection`:

    ```scala
    final case class FollowUpProjection(playerId: String, action: String,
        actionLabel: String, source: String, startable: Boolean,
        reason: Option[String] = None,
        queuedAfter: Vector[String] = Vector.empty)
    ```

  - `GameProjection.followUp: Option[FollowUpProjection] = None`, trailing, and spelled `followUp` on the wire.
  - `WalkerSimulation.restriction(tree, state, powers): Option[OathViolation]` and `WalkerSimulation.refusal(tree, state, powers): Option[OathViolation]`.
  - `CampaignProcedure.NothingToAttack: String` and `CampaignProcedure.refusal(catalog, state, actor, powers): Option[OathViolation]`.
  - `LegalActionProjector.followUpReason(action: ActionRef, campaignRefusal: Option[OathViolation]): String`, `private[application]`.

- [ ] **Step 1: Write the failing tests**

In `ProjectionProtocolSuite`, add a round trip of a projection whose `followUp` is `Some(FollowUpProjection("p1", "campaign", "Campaign", "Knights Errant", startable = false, reason = Some("No site to conquer and no enemy pawn to raid"), queuedAfter = Vector("Campaign")))`. Also add one with `followUp = None` that encodes no `followUp` key. Copy the suite's `undo` round-trip tests.

Create `src/test/scala/oathdigital/application/FollowUpProjectionSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.{FollowUpProjection, GameProjection}
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

/** What a queued action shows each viewer (follow-up actions design,
  * "Projection and UI"). */
class FollowUpProjectionSuite extends munit.FunSuite:
  private val ancientCity = CatalogNames.site("Ancient City")
  private val knightsCard = CatalogNames.denizen("Knights Errant")
  private val knights = PowerSourceRef.Card(knightsCard)

  private def owed(action: ActionRef): FollowUp = FollowUp(p1, action, knights)

  /** p1 at Ancient City, which two bandits rule, holding Knights Errant
    * faceup; p2 at Broken Peaks and p3 at Buried Giant. `queue` is owed to p1.
    */
  private def table(queue: FollowUp*): Table = Table.start
    .pawn(p1, at = ancientCity).pawn(p2, at = "Broken Peaks")
    .pawn(p3, at = "Buried Giant").adviser(p1, knightsCard)
    .bandits(ancientCity, 2)
    .update(_.updateCurrent(_.copy(followUps = queue.toVector)))

  private def project(board: Table, viewer: PlayerId): GameProjection =
    new GameProjector(catalog).project("follow-up",
      LoadedGame(Ready(board.ready), 3), viewer)

  test("the head's player gets only the follow-up controls"):
    val shown = project(table(owed(ActionRef.Campaign)), p1)
    assertEquals(shown.legalControls, Vector("beginFollowUp", "skipFollowUp"))
    assertEquals(shown.followUp, Some(FollowUpProjection(p1.value,
      "campaign", "Campaign", "Knights Errant", startable = true)))
    assertEquals(shown.phase, "follow-up-pending")
    assertEquals(shown.minorActions, None)
    assert(!shown.actionSelectionOpen)

  test("another player sees the entry and no controls"):
    val shown = project(table(owed(ActionRef.Campaign)), p2)
    assertEquals(shown.legalControls, Vector.empty)
    assertEquals(shown.followUp.map(_.playerId), Some(p1.value))

  test("the entries behind the head are named"):
    val shown = project(table(owed(ActionRef.Travel), owed(ActionRef.Campaign)),
      p1)
    assertEquals(shown.followUp.map(_.queuedAfter), Some(Vector("Campaign")))

  test("a Campaign with nothing to attack is shown unstartable, with why"):
    val shown = project(table(owed(ActionRef.Campaign)).bandits(ancientCity, 0),
      p1)
    assertEquals(shown.followUp.map(f => (f.startable, f.reason)),
      Some((false, Some("No site to conquer and no enemy pawn to raid"))))
    assertEquals(shown.legalControls, Vector("beginFollowUp", "skipFollowUp"))

  test("a restriction's reason comes before the missing target"):
    val vowed = table(owed(ActionRef.Campaign)).bandits(ancientCity, 0)
      .adviser(p1, "Vow of Peace")
    assertEquals(project(vowed, p1).followUp.flatMap(_.reason),
      Some("Vow of Peace prevents its ruler from campaigning"))

  test("each action names its own reason"):
    val campaign = (refusal: OathViolation) => LegalActionProjector
      .followUpReason(ActionRef.Campaign, Some(refusal))
    assertEquals(campaign(OathViolation.CampaignUnavailable(
      "Vow of Peace prevents its ruler from campaigning")),
      "Vow of Peace prevents its ruler from campaigning")
    assertEquals(campaign(OathViolation.GameNotStarted), "Cannot start now")
    assertEquals(LegalActionProjector.followUpReason(ActionRef.Travel, None),
      "No site to travel to")
    assertEquals(LegalActionProjector.followUpReason(ActionRef.Search, None),
      "Cannot start now")

  test("in Rest, Finish Rest waits for the queue"):
    val resting = table(owed(ActionRef.Campaign)).turn(p1, Phase.Rest)
    assertEquals(project(resting, p1).legalControls,
      Vector("beginFollowUp", "skipFollowUp"))
    assert(project(table().turn(p1, Phase.Rest), p1).legalControls
      .contains("finishRest"))

  test("a finished game shows no entry"):
    val over = table(owed(ActionRef.Campaign))
      .update(_.updateCurrent(_.copy(result = Some(GameResult(p2)))))
    assertEquals(project(over, p1).followUp, None)
```

`GameProjection`'s phase field may be named `phase`, and `ActionDecisionRenderer` reads `value.phase`. If the DTO calls it something else, use that name. `followUpReason` is on the `LegalActionProjector` companion: declare it there.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.FollowUpProjectionSuite"`
Expected: compilation fails, because `FollowUpProjection` and `GameProjection.followUp` are not found.

- [ ] **Step 3: Give the start dry run its reason**

In `WalkerSimulation.scala`, replace `starts` with:

```scala
  /** The first restriction that rejects `tree` now, if any. */
  def restriction(tree: Operation, state: ReadyGame, powers: WalkerPowers)
      : Option[OathViolation] =
    guarded(Right(ProcedureWalker.restrictionViolations(tree, powers, state,
      state.game.current.turn.activePlayer).headOption)).toOption.flatten

  /** Why `tree` would not start now, or `None` when it would: a restriction,
    * then the first walk up to its first park. */
  def refusal(tree: Operation, state: ReadyGame, powers: WalkerPowers)
      : Option[OathViolation] =
    guarded {
      restriction(tree, state, powers).toLeft(())
        .flatMap(_ => ProcedureWalker.advance(state, tree, None, powers,
          WalkerDice.placeholder))
    }.left.toOption

  /** (keep the existing doc) */
  def starts(tree: Operation, state: ReadyGame, powers: WalkerPowers): Boolean =
    refusal(tree, state, powers).isEmpty
```

In `CampaignProcedure.scala`, name the gate's text and add `refusal`:

```scala
  /** Why a Campaign cannot start with no site to conquer and no pawn to
    * raid. */
  val NothingToAttack: String = "Campaign needs a ruled pawn site to " +
    "Conquest or a co-located enemy pawn to Raid"
```

Replace the literal in `build` with `OathViolation.CampaignUnavailable(NothingToAttack)`. After `startable`, add:

```scala
  /** Why a Campaign could not start now, or `None` when it could (follow-up
    * actions design, "Projection and UI"). A restriction answers first, so
    * Vow of Peace is the reason even when nothing could be attacked; then
    * the gates; then the first walk. */
  def refusal(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): Option[OathViolation] =
    WalkerSimulation.restriction(tree(catalog, state, actor), state, powers)
      .orElse(build(catalog, state, actor, Vector.empty)
        .fold(Some(_), WalkerSimulation.refusal(_, state, powers)))
```

`tree` stays private; `refusal` is in the same object.

- [ ] **Step 4: Add the wire DTO and codec**

In `GameProjectionDto.scala`, add `FollowUpProjection` (see Interfaces) with a doc comment that names the spec. Add a trailing `followUp: Option[FollowUpProjection] = None` to `GameProjection`, after `logRevision`.

In `GameProjectionCodec.scala`:
- Add `"followUp"` to `Fields`.
- Encode it as `undo` is encoded: `"followUp" -> option(value.followUp)(encodeFollowUp)`.
- Decode it with `optionalAbsent(value, "followUp", path)(decodeFollowUp)`.
- Add `encodeFollowUp` and `decodeFollowUp` beside the step-offer pair, in the same style:
  - Fields `playerId`, `action`, `actionLabel`, `source` and `startable`.
  - `reason`, absent when `None`.
  - `queuedAfter` as a string array.

If the codec checks each object's keys with `exact`, give the follow-up object its own key set.

- [ ] **Step 5: Project the entry and its controls**

In `ScopedProjectionContext.scala`, add a field to `LegalProjection`:

```scala
private[application] final case class LegalProjection(
    controls: Vector[String],
    minorActions: Option[MinorActionsProjection],
    followUp: Option[FollowUpProjection] = None
)
```

In `LegalActionProjector.scala`:
- Import `oathdigital.gameplay.walker.{FollowUpStarted, ProcedureWalker}` and `CampaignProcedure` (if not imported yet).
- Change `project`:

```scala
  def project(context: ScopedProjectionContext,
      projectedPhasePowers: Vector[PhasePowerProjection]): LegalProjection =
    val minor = Option.when(context.viewerIsActive &&
      context.current.turn.phase == Phase.Act &&
      context.current.walkerPending.isEmpty &&
      context.current.followUps.isEmpty)(minorActionsProjection(context))
    val followUp = followUpProjection(context)
    LegalProjection(controls(context, minor, projectedPhasePowers, followUp),
      minor, followUp)
```

- In `controls`, add the parameter `followUp: Option[FollowUpProjection]`, and add an arm after the `walkerPending` arm:

```scala
    else if followUp.nonEmpty then
      if followUp.exists(_.playerId == context.viewer.map(_.value)
          .getOrElse("")) then Vector("beginFollowUp", "skipFollowUp")
      else Vector.empty
```

- Add the projection:

```scala
  /** The queue's head as every viewer sees it (follow-up actions design,
    * "Projection and UI"), while no walker is pending and the game is not
    * over. Whether it can start is asked of the state its start would
    * leave, so the waiver and the Act-phase exemption apply as they would.
    */
  private def followUpProjection(context: ScopedProjectionContext)
      : Option[FollowUpProjection] =
    val current = context.current
    for
      head <- current.followUps.headOption
      if current.walkerPending.isEmpty && current.result.isEmpty
      started <- ProcedureWalker.applyRecorded(Ready(context.ready),
        FollowUpStarted(head)).toOption.collect { case Ready(ready) => ready }
      queued = context.copy(ready = started)
      startable = followUpStartable(queued, head.action)
    yield FollowUpProjection(head.player.value, head.action.key,
      actionLabel(head.action), sourceLabel(context.ready, head.source),
      startable,
      Option.when(!startable)(LegalActionProjector.followUpReason(
        head.action, Option.when(head.action == ActionRef.Campaign)(
          CampaignProcedure.refusal(catalog, started, head.player,
            WalkerPowers.selected(walkerPowerCatalog, Vector.empty))).flatten)),
      current.followUps.tail.map(entry => actionLabel(entry.action)))

  /** Whether the queued `action` could start, by the same dry run as its
    * own start control. */
  private def followUpStartable(context: ScopedProjectionContext,
      action: ActionRef): Boolean = action match
    case ActionRef.Campaign => campaignStartable(context)
    case ActionRef.Travel => travelStartable(context)
    case ActionRef.Search => searchStartable(context)
    case ActionRef.Muster => musterStartable(context)
    case ActionRef.Challenge => challengeStartable(context)
    case ActionRef.Recover => recoverEligible(context, context.active)
    case _ => false

  /** "Campaign", "Travel": an action as a button and the Game Log name it. */
  private def actionLabel(action: ActionRef): String = action.key.capitalize

  private def sourceLabel(ready: ReadyGame, source: PowerSourceRef): String =
    source match
      case PowerSourceRef.Card(card) => presentation.cardLabel(ready, card)
      case PowerSourceRef.Site(site) => presentation.siteLabel(site)
      case PowerSourceRef.Banner(banner) => presentation.bannerLabel(banner)
```

The `presentation` accessors:
- `GamePresentationProjector.cardLabel(ready, id)` is at L333.
- For sites, use whatever `WalkerDecisionProjector` calls for a site label (`presentation.siteLabel`).
- For banners, use the label `LogWords.source` uses for a banner. If no banner label accessor exists, use `banner.key`.

Match each accessor's real signature.

Add the companion:

```scala
object LegalActionProjector:
  /** Why a queued action cannot start (follow-up actions design): each
    * action declares its own. A Campaign shows its refusal's text, or names
    * the missing target. */
  private[application] def followUpReason(action: ActionRef,
      campaignRefusal: Option[OathViolation]): String = action match
    case ActionRef.Campaign => campaignRefusal match
      case Some(OathViolation.CampaignUnavailable(detail))
          if detail == CampaignProcedure.NothingToAttack =>
        "No site to conquer and no enemy pawn to raid"
      case Some(OathViolation.CampaignUnavailable(detail)) => detail
      case _ => "Cannot start now"
    case ActionRef.Travel => "No site to travel to"
    case _ => "Cannot start now"
```

If `LegalActionProjector` already has a companion, add the method to it.

In `PendingProjector.phase`, before the `turn.phase` match, add:

```scala
    // A queued action waits for its player, in whatever phase it was queued.
    else if context.current.followUps.nonEmpty then "follow-up-pending"
```

In `GameProjection.scala`:
- At L93-94, `actionSelectionOpen` gains `&& current.followUps.isEmpty`.
- At L107, set `followUp = legal.followUp`, beside `minorActions = legal.minorActions`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.application.FollowUpProjectionSuite oathdigital.application.*Projection*" "sharedJVM/testOnly oathdigital.protocol.ProjectionProtocolSuite"`
Expected: PASS.

- [ ] **Step 7: Run the whole build and commit**

Run: `./sbtw test frontend/test`
Expected: PASS.

```bash
git add src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala shared/src/main/scala/oathdigital/protocol/projection/GameProjectionDto.scala shared/src/main/scala/oathdigital/protocol/projection/GameProjectionCodec.scala src/main/scala/oathdigital/application/ScopedProjectionContext.scala src/main/scala/oathdigital/application/LegalActionProjector.scala src/main/scala/oathdigital/application/PendingProjector.scala src/main/scala/oathdigital/application/GameProjection.scala shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala src/test/scala/oathdigital/application/FollowUpProjectionSuite.scala
git commit -m "feat(projection): show a queued action, whether it can start, and why"
```

---

### Task 6: Game Log

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala` (`formatLive` L82-91, `eventLines` L124-161)
- Modify: `src/main/scala/oathdigital/application/gamelog/StartLines.scala` (`start` L21-32, `name` L73-87)
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala:288-298`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`
- Create: `src/test/scala/oathdigital/application/gamelog/GameLogFollowUpSuite.scala`

**Interfaces:**
- Consumes: Task 1's events and Task 2's queued start.
- Produces:
  - Tag `{Action} · {source}`.
  - Skip line `{source}: {Action} skipped.`, posted in the latest section.
  - A step whose only op is `QueueFollowUp` posts no "Used {card}" line.

- [ ] **Step 1: Write the failing tests**

In `LogScripts.scala`, after `raid`, add a script. It is `raid`'s table plus a Campaign owed to p1 by Knights Errant, held faceup, so that every viewer knows the card. It also copies `raid`'s answers.

```scala
  /** The `raid` table with a Campaign owed to p1 by Knights Errant, which p1
    * holds faceup, and the Raid run as that queued Campaign. */
  def queuedRaid(using munit.Location): Script =
    val knights = CatalogNames.denizen("Knights Errant")
    val (service, act) = atTable("queued-raid", tableWithOnePawnPerSite
      .pawn(p2, "Ancient City")
      .adviser(p2, "Birdsong", facedown = true)
      .adviser(p1, knights)
      .update(_.updateCurrent(_.copy(followUps = Vector(FollowUp(p1,
        ActionRef.Campaign, PowerSourceRef.Card(knights))))))
      .banditsRefilled, Dice.raid)
    raidAnswers(act).after(GameCommand.StartWalker(ActionRef.Campaign,
      StartPayload(p1)))
    Script("queued-raid", service, p1)
```

Extract `raid`'s `act.withAnswers { ... }` partial function into `private def raidAnswers(act: Situation): Situation`, and use it in both scripts, so the two answer sets cannot drift.

Create `src/test/scala/oathdigital/application/gamelog/GameLogFollowUpSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.gameplay.walker.FollowUpSkipped
import oathdigital.model._
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Table.p1
import LogScripts._

/** A queued action's Game Log (follow-up actions design, "Game Log and roll
  * feedback"). */
class GameLogFollowUpSuite extends munit.FunSuite:
  private val knights =
    PowerSourceRef.Card(CatalogNames.denizen("Knights Errant"))

  test("a queued Campaign is its own section, tagged with its source and " +
      "no Supply"):
    val entries = format(queuedRaid, None)
    val tag = entryStarting(entries, "Campaign")
    assertEquals(tag.kind, LogKind.Section)
    assertEquals(text(tag), "Campaign · Knights Errant")
    assertEquals(tag.section.map(_.action), Some(ActionRef.Campaign.key))

  test("a queued Campaign writes the battle lines a normal one writes"):
    val queued = texts(format(queuedRaid, None))
    val normal = texts(format(raid, None))
    assertEquals(queued.dropWhile(!_.startsWith("Campaign")).tail,
      normal.dropWhile(!_.startsWith("Campaign")).tail)

  test("Skip writes its line in the latest section"):
    val script = queuedRaid
    val entries = formatAppended(script, Vector(FollowUpSkipped(
      FollowUp(p1, ActionRef.Campaign, knights))))
    val skip = entries.last
    assertEquals(text(skip), "Knights Errant: Campaign skipped.")
    assertEquals(skip.section, entryStarting(entries, "Campaign").section)
```

`LogSection`'s fields are `(sequence, key)` (`LogSections.scala:18-20`). If the second field is not called `action`, use its real name in the first test.

The second test compares everything after the tag. If `raid` and `queuedRaid` differ before the tag only in round headlines, the comparison holds. If the tables' different advisers change a line, compare the two lines that follow the tag instead.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogFollowUpSuite"`
Expected: FAIL. The tag reads `Campaign` alone, and the Skip posts nothing.

- [ ] **Step 3: Place both events**

In `GameLogFormatter.eventLines`:
- Add `_: FollowUpStarted` to the walker list (`case _: WalkerStepRecorded | _: WalkerParked | _: WalkerCompleted | _: PowerNoted | _: FollowUpStarted =>`). The run then begins at the queued start, and its procedure is still read from the segment's closing event.
- Before `case _: WalkerEvent`, add:

```scala
      // Outside any run; `formatLive` files it in the latest section.
      case FollowUpSkipped(followUp) =>
        (Vector(Posted.line(LogKind.Action,
          words.source(followUp.source, journal.readyBefore(at).toVector,
            viewer) ++ Vector(Text(s": ${starts.name(followUp.action)
              .getOrElse(followUp.action.key)} skipped.")))), run)
```

In `formatLive`, file a Skip line in the latest section posted so far:

```scala
        val (lines, next) = eventLines(journal, run, at, viewer)
        val opened = if at == 0 then opening(journal) ++ lines else lines
        val filed = journal.event(at) match
          // A Skip follows the previous run's completion, so it belongs to
          // the latest section in time order (follow-up actions design).
          case _: FollowUpSkipped => opened.map(_.copy(section =
            entries.reverseIterator.flatMap(_.section).nextOption()))
          case _ => opened
        val posted = quietWake(entries.lastOption, filed)
```

Import `FollowUpStarted` and `FollowUpSkipped` from `oathdigital.gameplay.walker`.

- [ ] **Step 4: Credit the source in the tag**

In `StartLines`, widen `name` from `private` to `private[gamelog]`. In `start`, put the source between the name and the cost span:

```scala
    Posted.line(LogKind.Section, Text(named) +:
      (queuedBy(journal, run, at, viewer) ++ cost(journal, run, at))) +: ...
```

Add:

```scala
  /** " · Knights Errant" when the run was started from the queue: its
    * `FollowUpStarted` is in the run's first segment. */
  private def queuedBy(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[LogSpan] =
    (run.first to journal.segmentEnd(at)).iterator.map(journal.event)
      .collectFirst { case FollowUpStarted(followUp) => followUp }
      .toVector.flatMap(followUp => Text(" · ") +: words.source(
        followUp.source, journal.readyBefore(run.first).toVector, viewer))
```

Use the `words` instance and the viewer parameter `start` already has. Match `LogSpan` and `Text` to the types `start` already builds with.

- [ ] **Step 5: A queued entry is not an effect**

In `ActionLines.scala`, add `case QueueFollowUp(_) => true` beside `case RecordPowerUse(_) => true` (L288-298). A step that only queues then posts no "Used {card}" line: the modifiers line and the queued run's own section already say it.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: PASS, every Game Log suite included, `GameLogPowerLinesSuite`'s prefix stability among them.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala src/main/scala/oathdigital/application/gamelog/StartLines.scala src/main/scala/oathdigital/application/gamelog/ActionLines.scala src/test/scala/oathdigital/application/gamelog/LogScripts.scala src/test/scala/oathdigital/application/gamelog/GameLogFollowUpSuite.scala
git commit -m "feat(gamelog): credit a queued action's source and log a Skip"
```

---

### Task 7: The follow-up panel

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/FollowUpControls.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala` (`status` L13-48, `actionsPanel`)
- Create: `frontend/src/test/scala/oathdigital/frontend/FollowUpControlsSuite.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/ViewerPresentationSuite.scala`

**Interfaces:**
- Consumes: Task 4's `GameIntent.SkipFollowUp` and Task 5's `GameProjection.followUp` and controls.
- Produces: `FollowUpControls.render(value: GameProjection, canControl: Boolean, panel: dom.Element, submit: GameCommand => Unit): Unit`.

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/test/scala/oathdigital/frontend/FollowUpControlsSuite.scala`. It builds the projection with `ServerModeFixtures.twoSeatProjection`, whose active player is `red-exile`.

```scala
package oathdigital.frontend

import oathdigital.protocol.GameIntent
import oathdigital.protocol.projection.FollowUpProjection
import org.scalajs.dom

/** The panel a queued action draws in place of the action buttons
  * (follow-up actions design, "Projection and UI"). */
class FollowUpControlsSuite extends munit.FunSuite:
  private val owed = FollowUpProjection("red-exile", "campaign", "Campaign",
    "Knights Errant", startable = true)

  private def rendered(followUp: FollowUpProjection,
      controls: Vector[String] = Vector("beginFollowUp", "skipFollowUp"),
      canControl: Boolean = true)
      : (dom.Element, scala.collection.mutable.Buffer[GameIntent]) =
    val sent = scala.collection.mutable.Buffer.empty[GameIntent]
    val panel = dom.document.createElement("section")
    FollowUpControls.render(ServerModeFixtures.twoSeatProjection(
      controls.toSet, phase = "follow-up-pending")
      .copy(followUp = Some(followUp)), canControl, panel, sent += _)
    (panel, sent)

  private def buttons(panel: dom.Element): Vector[dom.html.Button] =
    panel.querySelectorAll("button").toVector
      .map(_.asInstanceOf[dom.html.Button])

  test("the start button names the action and its source, and Skip skips"):
    val (panel, sent) = rendered(owed)
    assertEquals(buttons(panel).map(_.textContent),
      Vector("Campaign for no Supply · Knights Errant", "Skip"))
    buttons(panel).foreach(_.click())
    assertEquals(sent.toVector, Vector[GameIntent](
      GameIntent.StartWalker("campaign", Vector.empty),
      GameIntent.SkipFollowUp))

  test("an unstartable entry is disabled and says why on hover, and Skip " +
      "still works"):
    val reason = "No site to conquer and no enemy pawn to raid"
    val (panel, _) = rendered(owed.copy(startable = false,
      reason = Some(reason)))
    val start = buttons(panel).head
    assert(start.disabled)
    assertEquals(start.getAttribute("title"), reason)
    assert(panel.textContent.contains(reason))
    assert(!buttons(panel).last.disabled)

  test("the entries behind the head are named"):
    val (panel, _) = rendered(owed.copy(queuedAfter = Vector("Campaign")))
    assert(panel.textContent.contains("Then: Campaign"))

  test("another player sees no buttons"):
    val (panel, _) = rendered(owed, controls = Vector.empty)
    assertEquals(buttons(panel), Vector.empty)
```

`GameIntent.StartWalker` has three fields (`ModifierFlowDraft.action` matches `StartWalker(action, _, _)`). If the third has no default, pass `Vector.empty` for it too, here and in `FollowUpControls`.

In `ViewerPresentationSuite`, after "Wake and the Act phase leave the active player's status empty", add:

```scala
  test("while an action is queued, a waiting player reads who may take it"):
    val value = twoSeatProjection(Set.empty, phase = "follow-up-pending")
      .copy(followUp = Some(FollowUpProjection("red-exile", "campaign",
        "Campaign", "Knights Errant", startable = true)))
    val waiting = ActionDecisionRenderer.status(value,
      ServerUiSupport.viewerPresentation(value, "blue-exile"))
    assert(waiting.textContent.endsWith(" may campaign (Knights Errant)"),
      waiting.textContent)
    val owner = ActionDecisionRenderer.status(value,
      ServerUiSupport.viewerPresentation(value, "red-exile"))
    assertEquals(owner.textContent, "")
```

Import `oathdigital.protocol.projection.FollowUpProjection` in that suite.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.FollowUpControlsSuite oathdigital.frontend.ViewerPresentationSuite"`
Expected: compilation fails, because `FollowUpControls` is not found.

- [ ] **Step 3: Draw the panel**

Create `frontend/src/main/scala/oathdigital/frontend/FollowUpControls.scala`:

```scala
package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand}
import org.scalajs.dom
import ServerUiSupport._

/** The action owed to the viewer once the previous one ended (follow-up
  * actions design, "Projection and UI"): a start button, disabled with why
  * on hover when the action cannot start, and Skip. Drawn in place of the
  * action buttons; only the entry's player is offered the controls. The
  * start goes through the modifier flow, so modifiers are offered as for a
  * normal start.
  */
private[frontend] object FollowUpControls:
  def render(value: GameProjection, canControl: Boolean, panel: dom.Element,
      submit: GameCommand => Unit): Unit =
    value.followUp.filter(_ => value.legalControls.contains("beginFollowUp"))
      .foreach { followUp =>
        val start = button(s"${followUp.actionLabel} for no Supply · " +
          followUp.source, "act-action follow-up-action")
        start.disabled = !canControl || !followUp.startable
        followUp.reason.foreach(reason => start.setAttribute("title", reason))
        start.onclick = _ =>
          submit(GameCommand.StartWalker(followUp.action, Vector.empty))
        panel.appendChild(start)
        followUp.reason.foreach(reason =>
          panel.appendChild(text("p", "walker-choice-reason", reason)))
        val skip = button("Skip", "act-action follow-up-skip")
        skip.disabled = !canControl ||
          !value.legalControls.contains("skipFollowUp")
        skip.onclick = _ => submit(GameCommand.SkipFollowUp)
        panel.appendChild(skip)
        if followUp.queuedAfter.nonEmpty then
          panel.appendChild(text("p", "informational",
            s"Then: ${followUp.queuedAfter.mkString(", ")}"))
      }
```

In `ActionDecisionRenderer.actionsPanel`, after the `value.phase == "wake"` block, add:

```scala
   if value.phase == "follow-up-pending" && presentation.showGameplayControls
   then FollowUpControls.render(value, canControl, panel, submitCommand)
```

In `status`, add a first arm to the `presentation.procedureStatus` match's `None` branch, before `presentation.waitingForPlayerId match`. A waiting viewer reads who may act and why; the entry's own player reads nothing, because the panel says it:

```scala
     case None if value.followUp.nonEmpty =>
       value.followUp.filterNot(f => presentation.playerId.contains(f.playerId))
         .foreach { followUp =>
           node.appendChild(playerReference(value, followUp.playerId))
           node.appendChild(dom.document.createTextNode(
             s" may ${followUp.action} (${followUp.source})"))
         }
```

Match `presentation.playerId`'s real type (`Option[String]` or a player id wrapper). The `phase == "rest"` and `phase == "wake"` branches no longer fire while an entry waits, because the phase reads `follow-up-pending`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.FollowUpControlsSuite oathdigital.frontend.ViewerPresentationSuite"`
Expected: PASS.

- [ ] **Step 5: Run the frontend suite and commit**

Run: `./sbtw frontend/test`
Expected: PASS.

```bash
git add frontend/src/main/scala/oathdigital/frontend/FollowUpControls.scala frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala frontend/src/test/scala/oathdigital/frontend/FollowUpControlsSuite.scala frontend/src/test/scala/oathdigital/frontend/ViewerPresentationSuite.scala
git commit -m "feat(frontend): draw the queued action's start and Skip"
```

---

### Task 8: Knights Errant on the queue

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/KnightsErrant.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala:67`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala:221-232`
- Modify: `src/test/scala/oathdigital/gameplay/walker/WalkerProcedureRegistrySuite.scala:176-191`
- Rewrite: `src/test/scala/oathdigital/gameplay/powers/economy/KnightsErrantSuite.scala`

**Interfaces:**
- Consumes: Tasks 1-5.
- Produces: `KnightsErrant` as a plain `object`-built power with no `catalog` parameter to `forCatalog`. Grep for every `KnightsErrant.forCatalog(` caller and update it.

- [ ] **Step 1: Rewrite the suite to the new behaviour**

Rewrite `KnightsErrantSuite`. Keep the file's `staged`, `ready`, `musterFrom` and `afterMuster` helpers. Delete `answer`, `parkedOn`, `query`, `optionRefs`, `toForce`, `campaign`, `decline`, `campaigning`, `power` and `said`. Replace the twelve tests with:

```scala
  private val owed = FollowUp(p1, ActionRef.Campaign,
    PowerSourceRef.Card(knights))

  private def followUps(transition: OathTransition): Vector[FollowUp] =
    ready(transition).game.current.followUps

  test("the Muster queues a Campaign owed to its player"):
    val mustered = musterFrom(staged(supply = 1).ready, modifiers)
    assertEquals(ready(mustered).game.current.walkerPending, None)
    assertEquals(followUps(mustered), Vector(owed))
    assertEquals(Look(ready(mustered)).boardWarbands(p1), afterMuster)

  test("without the selection the Muster queues nothing"):
    assertEquals(followUps(musterFrom(staged(supply = 1).ready, Vector.empty)),
      Vector.empty)

  test("the queue is unconditional: nothing to campaign against still " +
      "queues"):
    val mustered = musterFrom(staged(supply = 1, campaignLegal = false).ready,
      modifiers)
    assertEquals(followUps(mustered), Vector(owed))

  test("the queued Campaign sees the warbands the Muster gained and spends " +
      "no Supply"):
    val mustered = musterFrom(staged(supply = 0).ready, modifiers)
    val started = accepted(rules.startWalker(mustered.state,
      ActionRef.Campaign, p1))
    assertEquals(Look(ready(started)).supply(p1), 0)
    assertEquals(ready(started).game.current.walkerProcedure,
      Some(ActionRef.Campaign))
    assertEquals(Look(ready(started)).boardWarbands(p1), afterMuster)

  test("it cannot be selected for a Campaign, or for any other action"):
    val state = staged(supply = 1).ready
    val offered = (action: ActionRef) =>
      accepted(rules.offerableWalkerPowers(state, p1, action)).map(_.id)
    assert(offered(ActionRef.Muster).contains(KnightsErrant.id))
    assert(!offered(ActionRef.Campaign).contains(KnightsErrant.id))
    assert(!offered(ActionRef.Trade).contains(KnightsErrant.id))

  test("Vow of Peace refuses the queued Campaign, and Skip clears it"):
    val vowed = staged(supply = 1).adviser(p1, "Vow of Peace").ready
    val mustered = musterFrom(vowed, modifiers)
    assertEquals(rules.startWalker(mustered.state, ActionRef.Campaign, p1)
      .left.toOption, Some(OathViolation.CampaignUnavailable(
        "Vow of Peace prevents its ruler from campaigning")))
    assertEquals(followUps(accepted(rules.skipFollowUp(mustered.state, p1))),
      Vector.empty)

  test("a Fortress that protects every player a Raid could target refuses " +
      "the queued Campaign"):
    val board = staged(supply = 1, campaignLegal = false)
      .pawn(p2, at = ancientCity)
    val fortified = musterFrom(board.edifice("Rotting Fortress",
      EdificeSide.Ruined, at = ancientCity).ready, modifiers)
    assert(rules.startWalker(fortified.state, ActionRef.Campaign, p1).isLeft)
    val open = musterFrom(board.ready, modifiers)
    assert(rules.startWalker(open.state, ActionRef.Campaign, p1).isRight)
```

The projection's view of the Vow of Peace case (a disabled button with the reason) is `FollowUpProjectionSuite`'s, from Task 5. Delete imports the compiler then names unused.

In `WalkerProcedureRegistrySuite`, rename the test "Muster and Campaign declare the same roll for a Campaign question" (L176-191) to "only Campaign declares a Campaign question's roll". Assert `Some(WalkerRollFeedback(CampaignIds.defensePool))` for `ActionRef.Campaign` and `None` for `ActionRef.Muster`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.KnightsErrantSuite oathdigital.gameplay.walker.WalkerProcedureRegistrySuite"`
Expected: FAIL. The Muster parks on `muster.knights-errant.campaign` instead of queueing, and Muster still declares Campaign's roll.

- [ ] **Step 3: Migrate the power**

Replace `KnightsErrant.scala` below the card object with:

```scala
/** Knights Errant (card 120), a free selected Muster modifier: after the
  * Muster's gain, a Campaign is owed to its player, spending no Supply
  * (follow-up actions design, "Cards"). The queued Campaign is a full action
  * of its own: its player starts or skips it once the Muster ends, may
  * select modifiers for it, and sees it disabled with why when it cannot
  * start. The queue is unconditional, so a restriction such as Vow of Peace
  * shows on the queued Campaign's button, not here.
  */
final case class KnightsErrant private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = KnightsErrantCard.id
  def id: PowerId = KnightsErrant.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Muster)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterActionEligibility -> Vector(Transform((ctx, children) =>
      children :+ QueueFollowUp(FollowUp(ctx.activePlayer, ActionRef.Campaign,
        PowerSourceRef.Card(cardId))))))

object KnightsErrant:
  val id: PowerId = KnightsErrantCard.power.id

  def forCatalog(catalog: ExecutableCatalog): KnightsErrant =
    new KnightsErrant(catalog)
```

`SelectedModifier` requires `catalog`, so the private constructor keeps it. The spec's "catalog constructor parameter" goes away only in the sense that nothing reads it any more. If `KnightsErrant.forCatalog` is the factory every other selected modifier uses, keep it. Removed: the decision and its options, the nested Campaign, the `CampaignCost` contribution, `appliesAt` and the `campaigns` note key. Keep the card object's lines as they are. Delete imports the compiler names unused.

In `WalkerProcedureRegistry.scala`, delete the Muster entry's comment and `rollFeedback = CampaignProcedure.rollFeedback` (L230-232). Its `requiresPlayableOption = true` then closes the entry.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.KnightsErrantSuite oathdigital.gameplay.walker.WalkerProcedureRegistrySuite oathdigital.gameplay.powers.*"`
Expected: PASS.

- [ ] **Step 5: Run the whole server suite and commit**

Run: `./sbtw test`
Expected: PASS. Look for two things:
- A Game Log or golden suite that asserted the retired Knights' line must lose that line.
- A suite that names `KnightsErrant.decisionId` or `KnightsErrant.campaigns` must lose that reference.

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/KnightsErrant.scala src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala src/test/scala/oathdigital/gameplay/powers/economy/KnightsErrantSuite.scala src/test/scala/oathdigital/gameplay/walker/WalkerProcedureRegistrySuite.scala
git commit -m "feat(powers): Knights Errant queues its Campaign"
```

Also stage `WalkerPowerCatalog.scala` and any suite Step 5 touched.

---

### Task 9: Option warnings and Hunting Party

**Files:**
- Modify: `src/main/scala/oathdigital/model/Decisions.scala` (beside `Unavailable`, L268)
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala` (`ContributingPower`)
- Create: `src/main/scala/oathdigital/gameplay/walker/OptionWarnings.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala` (`playable` L189-197, `optionProjection` L340-344)
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala:138-151` (`withholds`)
- Create: `src/main/scala/oathdigital/gameplay/powers/search/HuntingParty.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala:46-51` (delete `HuntingPartyCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala:107` (and its import)
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/search/HuntingPartySuite.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/WalkerChoicePanelRenderSuite.scala`

**Interfaces:**
- Consumes: Tasks 1-5.
- Produces:
  - `DecisionOption.Warned(option: DecisionOption, reason: OptionReason)`, choosable.
  - `ContributingPower.warning(decide: Decide, option: DecisionOptionRef): Option[OptionReason]`, default `None`.
  - `OptionWarnings.marked(powers: WalkerPowers, decide: Decide, options: Vector[DecisionOption]): Vector[DecisionOption]`.
  - The wire option state `warning`.
  - `HuntingParty.id = PowerId("denizen.hunting-party")` and `HuntingParty.discardWarning = "Hunting Party needs the world deck"`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/search/HuntingPartySuite.scala`. It drives the whole Search through a `Situation`, so the default answers settle the card choice and the placement after the source:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.application.{GameCommand, GameProjector, LoadedGame,
  StartPayload}
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.DecisionQueryProjection
import oathdigital.testkit.{CatalogNames, Situation, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** Hunting Party (card 122): after a Search of the world deck, a Campaign is
  * owed, spending no Supply; a discard pile warns and stays choosable
  * (follow-up actions design, ruling 10). */
class HuntingPartySuite extends munit.FunSuite:
  private val hunting = CatalogNames.denizen("Hunting Party")
  private val modifiers = Vector(HuntingParty.id)
  private val owed = FollowUp(p1, ActionRef.Campaign,
    PowerSourceRef.Card(hunting))
  private val region = Region.Cradle // Ancient City's region
  /** Plain denizens for the world deck and the Cradle discard. */
  private val deckTop = Vector("Wrestlers", "Errand Boy", "Mercenaries")
    .map(CatalogNames.denizen(_))
  private val discardPile = Vector("Code of Honor", "Messenger", "Shield Wall")
    .map(CatalogNames.denizen(_))

  /** p1 in Act at Ancient City holding Hunting Party faceup, with 5 Supply,
    * the world deck topped by three plain cards and the Cradle discard
    * holding three more. */
  private def board: Table = Table.start
    .worldDeckTop(deckTop*).discarded(region, discardPile*)
    .pawn(p1, "Ancient City").supply(p1, 5).adviser(p1, hunting)

  /** The queue after a whole Search of `pile` with `selected`, every later
    * decision answered by default. */
  private def queuedAfter(pile: SearchSource, selected: Vector[PowerId])
      : Vector[FollowUp] =
    board.situation(Situation.productionRules(catalog)).withAnswers {
      case park if park.decisionId == SearchProcedure.sourceDecisionId =>
        ChooseOneAnswer(SearchSource.ref(pile))
    }.after(GameCommand.StartWalker(ActionRef.Search,
      StartPayload(p1, selected))).ready.game.current.followUps

  test("a Search of the world deck queues a Campaign"):
    assertEquals(queuedAfter(SearchSource.WorldDeck, modifiers), Vector(owed))

  test("a Search of a discard pile queues nothing"):
    assertEquals(queuedAfter(SearchSource.RegionalDiscard(region), modifiers),
      Vector.empty)

  test("without the selection nothing is queued"):
    assertEquals(queuedAfter(SearchSource.WorldDeck, Vector.empty),
      Vector.empty)

  test("while it is selected the discard pile warns and stays choosable"):
    val rules = Situation.productionRules(catalog)
    val parked = board.situation(rules).parkedAfter(GameCommand.StartWalker(
      ActionRef.Search, StartPayload(p1, modifiers)))
    val shown = new GameProjector(catalog).project("hunting",
      LoadedGame(parked.state, 1), p1)
    val options = shown.walkerDecision.flatMap(_.query).collect {
      case one: DecisionQueryProjection.ChooseOne => one.options
    }.getOrElse(Vector.empty)
    assertEquals(options.find(_.kind == "discard-pile").map(o =>
      (o.state, o.reason)),
      Some(("warning", Some(HuntingParty.discardWarning))))
    assertEquals(options.find(_.kind == "deck").map(_.state),
      Some("available"))
```

Two notes:
- The rules adapter refuses `WithModifiers` but accepts `StartWalker` with modifiers in its payload, which is what this suite sends.
- If `LoadedGame`'s constructor takes more than `(state, nextSequence)`, copy the call `ChallengeProjectionSuite` makes.

Delete imports the compiler names unused (`Ready`, `accepted`).

In `WalkerChoicePanelRenderSuite`, after "an unavailable option is a disabled control that says why" (L163), add a test: an option with `state = "warning"` and a reason renders an enabled control whose `title` is the reason, with the reason printed under it in `.walker-choice-reason`. Copy the neighbouring test's setup.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.HuntingPartySuite" "frontend/testOnly oathdigital.frontend.WalkerChoicePanelRenderSuite"`
Expected: compilation fails, because `HuntingParty` is not found, and the frontend test fails on a disabled or reasonless control.

- [ ] **Step 3: Add the choosable warning**

In `Decisions.scala`, after `Unavailable`, add:

```scala
  /** An option shown with a reason to think twice, still choosable: a
    * selected power that has no effect on it says so (Hunting Party on a
    * discard pile). `ref` is the wrapped option's, so the warning never
    * affects legality. */
  final case class Warned(option: DecisionOption, reason: OptionReason)
      extends DecisionOption:
    def ref: DecisionOptionRef = option.ref
    override def available: Boolean = option.available
```

`DecisionOption` is sealed. Fix every match the compiler reports as non-exhaustive by unwrapping `Warned` exactly where that match unwraps `Badged`.

In `ContributingPower`, add:

```scala
  /** Why `option` of `decide` deserves a second look while this power is
    * offered, for the projector to show beside it. The option stays
    * choosable, and the walk never reads this. */
  def warning(decide: Decide, option: DecisionOptionRef): Option[OptionReason] =
    None
```

Import `Decide` and `OptionReason` from `oathdigital.model`.

Create `src/main/scala/oathdigital/gameplay/walker/OptionWarnings.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.model.{Decide, DecisionOption}

/** The warnings the offered powers give a choose-one's options (follow-up
  * actions design, ruling 10). The first power's warning wins. An option
  * that cannot be chosen keeps only its own reason. */
object OptionWarnings:
  def marked(powers: WalkerPowers, decide: Decide,
      options: Vector[DecisionOption]): Vector[DecisionOption] =
    options.map(option =>
      if !option.available then option
      else powers.powers.iterator.flatMap(_.warning(decide, option.ref))
        .nextOption().fold(option)(DecisionOption.Warned(option, _)))
```

In `WalkerDecisionProjector.playable`, mark the choose-one whichever branch produced it:

```scala
  private def playable(procedure: ProcedureRef, ready: ReadyGame,
      tree: Operation, pending: PendingTree, powers: WalkerPowers,
      decide: Decide): (DecisionQuery, Map[DecisionOptionRef, Vector[String]]) =
    val (query, details) = WalkerProcedureRegistry.optionPricing(procedure)
      match
        case Some(price) => (decide.query match
          case one: DecisionQuery.ChooseOne =>
            one.copy(options = price(ready, tree, pending, powers, decide))
          case other => other, Map.empty)
        case None => accepting(procedure, ready, tree, pending, powers, decide)
    (query match
      case one: DecisionQuery.ChooseOne =>
        one.copy(options = OptionWarnings.marked(powers, decide, one.options))
      case other => other, details)
```

In `optionProjection`, after the `Unavailable` case, add:

```scala
      case DecisionOption.Warned(inner, reason) => optionProjection(ready,
        viewer, index, inner, details).map(row =>
          if row.state == "unavailable" then row
          else row.copy(state = "warning",
            reason = Some(reasonText(ready, viewer, reason))))
```

If `DecisionOptionProjection.state`'s doc lists its values, add `"warning"` there. If its codec whitelists states, add it to the whitelist too.

In `WalkerPanelSupport.withholds`, add the warning branch and extend the doc:

```scala
  /** An option shown with why it may not be chosen: its control is disabled,
    * names the reason on hover, and the reason is printed under `after`'s
    * last child so a touch screen can read it too. An option with a warning
    * stays enabled and shows its reason the same way. Any other option is
    * left as it is.
    */
  private[frontend] def withholds(control: dom.html.Button,
      option: DecisionOptionState, after: dom.Element): Unit =
    if option.state == "unavailable" || option.state == "warning" then
      control.disabled = option.state == "unavailable"
      option.reason.foreach { reason =>
        control.setAttribute("title", reason)
        after.appendChild(text("p", "walker-choice-reason", reason))
      }
```

Confirm that the panel drawing the Search source decision calls `withholds` for each option. Search the renderers for `search.source`, or for the deck and discard-pile option kinds. If it does not, call `withholds` there the way `renderChooseOnePanel` does.

- [ ] **Step 4: Add Hunting Party**

Create `src/main/scala/oathdigital/gameplay/powers/search/HuntingParty.scala`:
- Move `HuntingPartyCard` out of `HoldingOrderDenizens.scala` (L46-51) into it, unchanged.
- Point `NewFoundations.scala`'s import at the new package.

```scala
package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{PowerAnswers, SelectedModifier}
import oathdigital.model._

object HuntingPartyCard extends Denizen(DenizenId("122"), "Hunting Party",
    Suit.Order):
  // (moved unchanged from HoldingOrderDenizens.scala)

/** Hunting Party (card 122), a free selected Search modifier: after a Search
  * of the world deck, a Campaign is owed to its player, spending no Supply
  * (follow-up actions design, ruling 10). A discard pile is still
  * choosable while it is selected, with a warning that it queues nothing.
  */
final case class HuntingParty private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = HuntingPartyCard.id
  def id: PowerId = HuntingParty.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchActionEligibility -> Vector(Transform((ctx, children) =>
      children :+ HuntingParty.queued(ctx.activePlayer, cardId))))

  override def warning(decide: Decide, option: DecisionOptionRef)
      : Option[OptionReason] = option match
    case _: DecisionOptionRef.DiscardPile
        if decide.decisionId == SearchProcedure.sourceDecisionId =>
      Some(OptionReason.Written(HuntingParty.discardWarning))
    case _ => None

object HuntingParty:
  val id: PowerId = HuntingPartyCard.power.id
  val discardWarning: String = "Hunting Party needs the world deck"

  /** The Campaign, when the answered source is the world deck. */
  private def queued(actor: PlayerId, card: DenizenId): Operation =
    Branch((_, pending) =>
      if PowerAnswers.one(pending, SearchProcedure.sourceDecisionId)
          .flatMap(SearchSource.fromRef).contains(SearchSource.WorldDeck)
      then Vector(QueueFollowUp(FollowUp(actor, ActionRef.Campaign,
        PowerSourceRef.Card(card))))
      else Vector.empty)

  def forCatalog(catalog: ExecutableCatalog): HuntingParty =
    new HuntingParty(catalog)
```

Check two things:
- `PowerAnswers.one`'s return type is at `powers/PowerAnswers.scala:10-12`. If it returns an answer rather than a ref, compose with `SearchProcedure.sourceOf(answer).toOption` instead.
- The `SearchActionEligibility` children run after the source is answered (`SearchProcedure.scala:151-152`), so the Branch always reads an answer.

Register it in `WalkerPowerCatalog.scala` beside the other Search modifiers. Use the vector or `ActionModifiers` list News from Afar is in, `HuntingParty.forCatalog(catalog)`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.* oathdigital.application.WalkerDecisionProjectorSuite" "frontend/testOnly oathdigital.frontend.WalkerChoicePanelRenderSuite"`
Expected: PASS.

- [ ] **Step 6: Run the whole build and commit**

Run: `./sbtw test frontend/test`
Expected: PASS. Some catalog suites count implemented cards or list the holding cards (`PowerKindsCatalogSuite`, an implementation-status or holding suite). Each gains Hunting Party as implemented, or loses it from holding.

```bash
git add src/main/scala/oathdigital/model/Decisions.scala src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala src/main/scala/oathdigital/gameplay/walker/OptionWarnings.scala src/main/scala/oathdigital/application/WalkerDecisionProjector.scala frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala src/main/scala/oathdigital/gameplay/powers/search/HuntingParty.scala src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/test/scala/oathdigital/gameplay/powers/search/HuntingPartySuite.scala frontend/src/test/scala/oathdigital/frontend/WalkerChoicePanelRenderSuite.scala
git commit -m "feat(powers): Hunting Party queues a Campaign after a world-deck Search"
```

Also stage every file the compiler's exhaustiveness fixes and Step 6 touched.

---

### Task 10: Second Wind

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/SecondWind.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala:9-14` (delete `SecondWindCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala:148` (and its import)
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ModifierFlowDraft.scala:26-27` (`walkerActions`)
- Modify: `frontend/src/test/scala/oathdigital/frontend/CampaignControlsSuite.scala:23-25`
- Modify: `src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala:166` (`commit` gains `modifiers`)
- Modify: `src/test/scala/oathdigital/gameplay/powers/SearchFixture.scala:40` and `CallForHelpSuite.scala:61`, if they name the moved card object
- Create: `src/test/scala/oathdigital/gameplay/powers/campaign/SecondWindSuite.scala`

**Interfaces:**
- Consumes: Tasks 1-5.
- Produces: `SecondWind.id = PowerId("denizen.second-wind")`.

- [ ] **Step 1: Write the failing tests**

Give `PlanDriver.commit` (`src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala:166`) a trailing `modifiers: Vector[PowerId] = Vector.empty` parameter, and pass it to its `startWalker` call. Existing callers are unchanged.

Create `src/test/scala/oathdigital/gameplay/powers/campaign/SecondWindSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}
import oathdigital.testkit.Unwrap.accepted

/** Second Wind (card 16): after its holder's Campaign is won, a Travel and
  * then a Campaign are owed, each spending no Supply (follow-up actions
  * design, rulings 9 and 11). */
class SecondWindSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Second Wind")
  private val modifiers = Vector(SecondWind.id)

  /** p1 can conquer the origin, which bandits rule, with 5 warbands and 7
    * Supply, holding Second Wind faceup with `payments` secrets and favor to
    * pay its cost that many times. The rival stands at the origin too, so a
    * Raid is legal there once p1 rules it. */
  private def holding(payments: Int = 1): Board =
    val base = withEnemyAtOrigin(board(warbands = 5, supply = 7))
    on(withAdviserFor(base, base.actor, card, Orientation.FaceUp))(
      _.secrets(base.actor, faceUp = payments).favor(base.actor, payments))

  private def owed(b: Board, action: ActionRef): FollowUp =
    FollowUp(b.actor, action, PowerSourceRef.Card(card))

  private def followUps(run: Run): Vector[FollowUp] =
    ready(run.state).game.current.followUps

  test("a won Campaign queues a Travel, then a Campaign"):
    val b = holding()
    val won = commit(rules(winningDice), b, 4, modifiers = modifiers)
      .finishPlanWindows
    assertEquals(won.winner, Some(true))
    assertEquals(followUps(won),
      Vector(owed(b, ActionRef.Travel), owed(b, ActionRef.Campaign)))

  test("a lost Campaign queues nothing"):
    val lost = commit(rules(losingDice), holding(), 0, modifiers = modifiers)
      .finishPlanWindows
    assertEquals(lost.winner, Some(false))
    assertEquals(followUps(lost), Vector.empty)

  test("the cost is paid at the start, win or lose"):
    val b = holding()
    val started = accepted(rules(losingDice).startWalker(
      oathdigital.model.OathState.Ready(b.ready), ActionRef.Campaign,
      b.actor, modifiers))
    assertEquals(Look(ready(started.state)).faceUpSecrets(b.actor), 0)
    assertEquals(Look(ready(started.state)).favor(b.actor), 0)

  test("skipping the Travel still offers the Campaign"):
    val b = holding()
    val game = rules(winningDice)
    val won = commit(game, b, 4, modifiers = modifiers).finishPlanWindows
    val skipped = accepted(game.skipFollowUp(won.state, b.actor))
    assertEquals(ready(skipped.state).game.current.followUps,
      Vector(owed(b, ActionRef.Campaign)))

  test("the queued Campaign may select it again, and its entries go to the " +
      "back"):
    val b = holding(payments = 2)
    val game = rules(winningDice)
    val won = commit(game, b, 4, modifiers = modifiers).finishPlanWindows
    val skipped = accepted(game.skipFollowUp(won.state, b.actor))
    // p1 now rules the origin, so the queued Campaign raids the rival there.
    val again = commit(game, b.copy(ready = ready(skipped.state)), 4,
      raid = true, modifiers = modifiers).finishPlanWindows
    assertEquals(again.winner, Some(true))
    assertEquals(followUps(again),
      Vector(owed(b, ActionRef.Travel), owed(b, ActionRef.Campaign)))
```

`ready(state)` is the helper `HeraldSuite` uses through the same two imports.

If the raid against the rival does not win with `winningDice` and a force of 4, raise the force to the actor's remaining warbands. Never change the dice: the board's doc names who wins.

In `CampaignControlsSuite`, replace the test "Campaign does not start through the modifier flow" with:

```scala
  test("Campaign starts through the modifier flow"):
    assertEquals(ModifierFlowDraft.action(
      GameIntent.StartWalker("campaign", Vector.empty)),
      Some("campaign" -> Map.empty[String, String]))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.SecondWindSuite" "frontend/testOnly oathdigital.frontend.CampaignControlsSuite"`
Expected: compilation fails, because `SecondWind` is not found, and the frontend test fails because `action` returns `None`.

- [ ] **Step 3: Add Second Wind**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/SecondWind.scala`:
- Move `SecondWindCard` out of `HoldingDiscordDenizens.scala` (L9-14) into it, unchanged.
- Point `NewFoundations.scala`'s import, and any test that names the card object, at the new package.

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object SecondWindCard extends Denizen(DenizenId("16"), "Second Wind",
    Suit.Discord):
  // (moved unchanged from HoldingDiscordDenizens.scala)

/** Second Wind (card 16), a selected Campaign modifier paid at the start (1
  * secret, 1 favor burnt): once its holder's Campaign is won, a Travel and
  * then a Campaign are owed, each spending no Supply (follow-up actions
  * design, rulings 9 and 11). The two entries are independent: skipping the
  * Travel still offers the Campaign. It may be selected again on the queued
  * Campaign, so a chain ends only when its cost cannot be paid.
  *
  * It reads the outcome at the end of the Campaign's root, after the
  * Conquest or Raid, as Herald does. A selected modifier is offered only on
  * its holder's own Campaign, so "victorious" is a win as attacker.
  */
final case class SecondWind private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = SecondWindCard.id
  def id: PowerId = SecondWind.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Campaign)
  override def cost: Cost = SecondWindCard.power.cost

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignActionEligibility -> Vector(Transform(
      (ctx, children) => children :+ SecondWind.queued(ctx.activePlayer,
        cardId))))

object SecondWind:
  val id: PowerId = SecondWindCard.power.id

  /** The Travel and the Campaign, when `actor` won as attacker. */
  private def queued(actor: PlayerId, card: DenizenId): Operation =
    Branch((ready, _) =>
      if ready.game.current.lastCampaignResult.exists(result =>
          result.attacker == actor && result.attackerWins)
      then Vector(ActionRef.Travel, ActionRef.Campaign).map(action =>
        QueueFollowUp(FollowUp(actor, action, PowerSourceRef.Card(card))))
      else Vector.empty)

  def forCatalog(catalog: ExecutableCatalog): SecondWind =
    new SecondWind(catalog)
```

Register it in `WalkerPowerCatalog.scala` beside Herald or the other Campaign powers, as `SecondWind.forCatalog(catalog)`.

In `ModifierFlowDraft.scala`, add `"campaign"` to `walkerActions`. The server's preview is generic over registered actions (`GameApplicationService.walkerAction`), and Campaign's entry declares `CampaignModifierSelection`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*" "frontend/testOnly oathdigital.frontend.CampaignControlsSuite oathdigital.frontend.ModifierFlowSuite"`
Expected: PASS.

- [ ] **Step 5: Run the whole build and commit**

Run: `./sbtw test frontend/test`
Expected: PASS. Catalog suites gain Second Wind as implemented, as in Task 9.

```bash
git add src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala src/main/scala/oathdigital/gameplay/powers/campaign/SecondWind.scala src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala frontend/src/main/scala/oathdigital/frontend/ModifierFlowDraft.scala frontend/src/test/scala/oathdigital/frontend/CampaignControlsSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/SecondWindSuite.scala
git commit -m "feat(powers): Second Wind queues a Travel and a Campaign after a win"
```

Also stage any suite that named the moved card object.

---

### Task 11: Docs and the full gate

**Files:**
- Modify: `docs/ROADMAP.md` (the phase at L83-100; the Sneak Attack item at L108-112; the survey's nested-Campaign line at L137)
- Modify: `docs/superpowers/specs/2026-10-05-catalog-batch-4-rulings.md` ("Moved out of the batch" table)
- Create: `docs/architecture/follow-up-actions.md`

- [ ] **Step 1: Update the roadmap and the rulings**

In `docs/ROADMAP.md`:
- Delete the "Phase - Follow-up actions" section, apart from Search Party.
- Keep Search Party's paragraph as its own item, "Search Party: a card played after its WHEN PLAYED, as if searched …", under the next catalog phase. It has its own spec still to write.
- Replace the Sneak Attack item's text with: "Sneak Attack needs an off-turn actor: a Campaign whose acting player is not the active player, including selecting modifiers off turn. It queues through follow-ups via `FollowUp.player` (follow-up actions design, "Sneak Attack hooks"). Campaign powers read `ctx.activePlayer` as the attacker today (`PlanContext`, `BattlePlan`, `VowOfPeaceContribution`)."
- Remove Second Wind from the survey's "A nested Campaign or Travel" line.
- Add the cleanup item "Play-test follow-ups in a browser: Knights Errant, Hunting Party and Second Wind, Skip, and a queued Travel with Special Envoy."

In the batch-4 rulings' "Moved out of the batch" table:
- Mark Hunting Party (122) implemented by the follow-up actions design.
- Point Search Party (240) at its own future spec.

- [ ] **Step 2: Write the architecture note**

Create `docs/architecture/follow-up-actions.md`, in the style of its neighbours (a short overview, then one section per part). Cover these parts, each in a few sentences, naming the files:
- the queue and its replay (`FollowUp`, `QueueFollowUp`, `FollowUpStarted`, `FollowUpSkipped`, `walkerFollowUp`);
- the start and skip path, and the service gate;
- the waiver (`FollowUpSupplyWaiver`, `SupplyStrip`, and `SuitSupplyWaiver` beside them);
- the projection and the panel;
- the Game Log;
- option warnings;
- the three cards;
- the Sneak Attack hook.

Link the spec and this plan. Record the plan decisions listed at the top of this plan.

- [ ] **Step 3: Run the full gate**

Run, in order:
1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
4. `git diff --check`

Expected: every step passes.

- [ ] **Step 4: Commit the docs**

```bash
git add docs/ROADMAP.md docs/superpowers/specs/2026-10-05-catalog-batch-4-rulings.md docs/architecture/follow-up-actions.md
git commit -m "docs: record follow-up actions"
```

The journal version is not bumped (plan decision 9).
