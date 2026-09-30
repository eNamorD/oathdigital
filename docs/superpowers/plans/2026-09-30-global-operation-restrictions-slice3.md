# Global Operation Restrictions, Slice 3 (Grand Scepter, Forced Choices and Take) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the Grand Scepter in play wherever an operation runs, let a decision the search leaves with one option answer itself, retire Fae Merchant's own scepter filter, and move the banner by `Take` in Challenge and Conspiracy.

**Architecture:** A printed `GrandScepter` restriction joins `LockedCards` and the Hall of Ministers in `OperationRestrictions.printed`. A choose-one `Decide` can be a forced choice (`autoAnswer`): after the search narrows it, the walker answers one surviving option itself, recording a `ChoicePayload` marked `automatic`, and passes a decision with none left. Replay checks an automatic answer's node id only and adds the answer to the pending answers. Fae Merchant then offers every held relic in a forced choice and lets the restriction and the search hide the scepter. Challenge custody becomes a required `Take`, and Conspiracy's banner transfer an optional `Take`.

**Tech Stack:** Scala 3, sbt through `./sbtw`, munit.

**Spec:** [Global operation restrictions design](../specs/2026-09-30-global-operation-restrictions-design.md): "Rules" (Grand Scepter), "Lazy pruning" (Forced choices), "What retires", "Take for Challenge and Conspiracy", and slice 3 of "Slicing" and "Testing". Read both documents before starting.

## Global Constraints

- Build and test with `./sbtw`. The compiler runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import fails the build.
- No production Scala file may exceed 800 lines (`BackendArchitectureSuite`, "all production Scala files stay bounded"; also `python3 scripts/check-architecture.py`). `ProcedureWalker.scala` has exactly 800 lines at the start, so Task 3 moves the answer checks out before it adds anything.
- `BackendArchitectureSuite` still applies: no power names in walker sources (comments included), and powers do not import `gameplay.walker`.
- Every global restriction refuses as `Impossible`. An optional operation it refuses is skipped; a required one rejects the batch.
- Replay applies recorded operations without re-checking restrictions. The only replay change is Task 2's automatic answer.
- No timing check joins `sbt test`. The `SearchBudget` benchmark stays a program run by hand.
- Never touch the live database under `var/oathdigital`.
- Commits end with the trailer the session's system reminder names.

## Decisions taken at plan time

These answer the questions put to the product owner on 2026-09-30. The spec
already records them (committed with this plan).

- **Forced choices.** Removing Fae Merchant's filter without them would change
  two edge cases: holding the scepter and one drawn relic would park on a
  one-option confirm, and holding only the scepter with an empty relic deck
  would refuse the action. The product owner asked for a look-ahead that keeps
  both. `Decide.autoAnswer` does it for any choose-one: one option left is
  answered by the walker, none left is passed. Only a choose-one may be a
  forced choice.
- **Recording.** The automatic answer is a `ChoicePayload` with
  `automatic = true`, like an automatic roll's `RollPayload`. It is on the
  wire only when true. Replay checks its node id only (there is no park before
  it) and appends the answer to the pending answers, starting them when
  nothing is pending, so a later `WalkerParked` finds the same answers. It
  posts no "Chose" line.
- **Challenge custody** sits directly in the tree, so the walker checks the
  `Take` whole and records its `Move` leaf: the Game Log line, which reads that
  `Move`, needs no change. The custody is a required `Take` (`Take` gains
  `required`, like `Give`), because the payment buys the banner: a refused
  custody fails the path and the search hides that banner.
- **Conspiracy's** banner `Take` runs in its `BuildOps`, so it is recorded as a
  `Take`. Its Game Log line is a note, so it is unchanged. It stays optional,
  like the relic `Give` beside it; whether a refused target is hidden is
  decided with Lost Tongue.
- **`required` stays off the wire**, as it is for `Give` and every discard:
  replay never re-checks restrictions.

## File Structure

- Create `src/main/scala/oathdigital/gameplay/operations/GrandScepter.scala`: the printed restriction.
- Modify `src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala`: `printed` adds one `GrandScepter` per scepter relic.
- Modify `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala`: `ChoicePayload.automatic`.
- Modify `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala`: the `automatic` flag on the wire.
- Modify `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala`: the automatic answer's replay.
- Modify `src/main/scala/oathdigital/application/gamelog/DetailLines.scala`: no "Chose" line for an automatic answer.
- Modify the other `ChoicePayload` patterns (a fourth `_`): `application/gamelog/CampaignLines.scala`, `LogJournal.scala`, `NegotiationLines.scala`, `ActionLines.scala`, and three test suites.
- Modify `src/main/scala/oathdigital/model/CoreOperations.scala`: `Decide.autoAnswer`; `Take.required`.
- Modify `src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala`: `answerable`, the answer checks moved out of the walker.
- Modify `src/main/scala/oathdigital/gameplay/walker/WalkerSearch.scala`: forced choices in `narrow`, `narrowed` and `reach`; new `forced`.
- Modify `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`: answer a forced choice; `nodeId` helper; `answerDecide` slimmed.
- Modify `src/main/scala/oathdigital/gameplay/powers/action/FaeMerchant.scala` (becomes a `case object`), `DiceAndRelicDrawPowers.scala` and `gameplay/powers/PhasePowerCatalog.scala`.
- Modify `src/main/scala/oathdigital/gameplay/actions/challenge/ChallengeProcedure.scala` and `gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala`: `Take`.
- Modify `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala` and `application/gamelog/CampaignLines.scala`: the `Take` patterns gain a sixth `_`.
- Tests: `OperationRestrictionsSuite`, `GameEventWireSuite`, new `gameplay/walker/AutomaticChoiceReplaySuite`, `GameLogPowerLinesSuite`, `WalkerSearchSuite`, new `gameplay/ForcedChoiceSuite`, `FaeMerchantSuite`, `DicePowerDecisionProjectionSuite`, `ChallengeProcedureSuite`, `ConspiracyWhenPlayedSuite`.
- Docs: the spec's status line and `docs/ROADMAP.md`.

---

### Task 1: The Grand Scepter restriction

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/operations/GrandScepter.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala`
- Test: `src/test/scala/oathdigital/gameplay/OperationRestrictionsSuite.scala`

**Interfaces:**
- Produces: `final case class GrandScepter(relic: RelicId) extends OperationRestriction`, refusing with code `"grand-scepter"`. `OperationRestrictions.forCatalog(catalog).printed` holds one per `RelicRole.GrandScepter` relic.

- [ ] **Step 1: Write the failing tests**

In `OperationRestrictionsSuite.scala`, change the import to
`import oathdigital.gameplay.operations.{GrandScepter, LockedCards, OperationRestrictions}`,
add `private val scepter = RelicId("grand-scepter")` beside `drum`, and add
before the test "the catalog prints a lock for every lock-icon card and only
those":

```scala
  test("the Grand Scepter refuses a discard, a return to the relic deck and " +
      "a Bury, and allows a Take and a Give"):
    val ready = Table.start.relic(p1, scepter).ready
    val mine = PositionedLocation(Location.PlayArea(p1))
    val removals = Vector[CoreOperation](
      Discard.Relic(scepter, mine, 0, p1),
      Move(Piece.Card(scepter), mine, PositionedLocation(
        Location.Deck(CardDeck.Relic), StackPosition.Bottom)),
      Bury(BuryableCard.Relic(scepter), mine))
    assertEquals(removals.map(refusal(ready, _)),
      Vector.fill(3)(Some("grand-scepter")))
    val passes = Vector[CoreOperation](
      Take(Piece.Card(scepter), p2, Location.PlayArea(p1),
        Location.PlayArea(p2)),
      Give(Piece.Card(scepter), p1, Location.PlayArea(p1),
        Location.PlayArea(p2)))
    assertEquals(passes.map(refusal(ready, _)), Vector.fill(2)(None))

  test("the catalog prints one Grand Scepter restriction, for its scepter"):
    assertEquals(set.printed.collect { case value: GrandScepter => value.relic },
      Vector(scepter))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationRestrictionsSuite"`
Expected: compile failure, `Not found: GrandScepter`.

- [ ] **Step 3: Write the restriction**

Create `GrandScepter.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.model._

/** The Grand Scepter cannot be removed from play (global operation
  * restrictions design, "Rules"). A move that takes it from a site or a play
  * area to anywhere else is refused: a discard to the set-aside relics or a
  * return to the relic deck. A `Bury` of it is refused too. Passing it between
  * players, by a `Take` or a `Give`, keeps it in play and is allowed. The
  * scepter is never facedown, so a refusal reveals nothing.
  */
final case class GrandScepter(relic: RelicId) extends OperationRestriction:
  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] =
    Option.when(Operation.flatten(operation).exists(removes))(OperationReason(
      "grand-scepter", s"${relic.value} cannot be removed from play",
      OperationReasonKind.Impossible))

  private def removes(step: Operation): Boolean = step match
    case Move(Piece.Card(moved), from, to, _) if moved == relic =>
      OperationRestrictions.inPlay(from.location) &&
        !OperationRestrictions.inPlay(to.location)
    case Bury(BuryableCard.Relic(buried), _, _) => buried == relic
    case _ => false
```

In `OperationRestrictions.scala`:
- change the catalog import to `import oathdigital.catalog.{CardRestrictions, ExecutableCatalog, RelicRole}`;
- in the class doc, change "`printed` are the restrictions a catalog's cards print: [[LockedCards]], holding every lock-icon card, and the [[HallOfMinisters]]." to "`printed` are the restrictions a catalog's cards print: [[LockedCards]], holding every lock-icon card, the [[HallOfMinisters]], and one [[GrandScepter]] per scepter relic.";
- replace the last line of `printedBy`, `Vector(LockedCards(locked), HallOfMinisters(catalog))`, with:

```scala
    val scepters = catalog.relics.filter(_.role == RelicRole.GrandScepter)
      .map(relic => GrandScepter(RelicId(relic.id.value)))
    Vector(LockedCards(locked), HallOfMinisters(catalog)) ++ scepters
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationRestrictionsSuite oathdigital.gameplay.powers.action.FaeMerchantSuite"`
Expected: PASS. Fae Merchant still filters the scepter itself, so its suite is unchanged.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/operations/GrandScepter.scala src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala src/test/scala/oathdigital/gameplay/OperationRestrictionsSuite.scala
git commit -m "feat(restrictions): keep the Grand Scepter in play"
```

---

### Task 2: Automatic answer steps

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala:45-55`
- Modify: `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala:75-96`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala:79-88`
- Modify: `src/main/scala/oathdigital/application/gamelog/DetailLines.scala:39-40`
- Modify (pattern arity only): `application/gamelog/CampaignLines.scala:26,37,45`, `LogJournal.scala:85`, `NegotiationLines.scala:33`, `ActionLines.scala:192,199,220`; tests `gameplay/OathRulesWalkerPowerSuite.scala:100`, `gameplay/oathkeeper/OathkeeperProcedureSuite.scala:80`, `application/GameApplicationServiceSuite.scala:1048`
- Create: `src/test/scala/oathdigital/gameplay/walker/AutomaticChoiceReplaySuite.scala`
- Test: `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`, `src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala`

**Interfaces:**
- Produces: `ChoicePayload(decisionId: String, answer: DecisionAnswer, by: PlayerId, automatic: Boolean = false)`. Wire key `"automatic": true`, written only when true. Replay of an automatic answer: node id checked, answer appended to `walkerPending.answered`, or `walkerPending = Some(PendingTree(nodeId segments, Vector(answer)))` when nothing is pending.

- [ ] **Step 1: Write the failing tests**

Create `AutomaticChoiceReplaySuite.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.model._
import oathdigital.model.TestGameFixtures._

/** Replay of an answer the walker gave to a forced choice (global operation
  * restrictions design, "Forced choices"). No park comes before it, so only
  * its node id is checked, and the answer joins the pending answers that a
  * later park carries.
  */
class AutomaticChoiceReplaySuite extends munit.FunSuite:
  private val answer = Answered("test.ask",
    DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("keep")), playerId)

  private def step(nodeId: String, automatic: Boolean) =
    WalkerStepRecorded(nodeId, ChoicePayload(answer.decisionId, answer.answer,
      answer.by, automatic), Vector.empty, Vector.empty)

  private def pending(state: OathState): Option[PendingTree] = state match
    case OathState.Ready(value) => value.game.current.walkerPending
    case other => fail(s"expected a ready game, got $other")

  test("an automatic answer with no park before it starts the pending answers"):
    val replayed = ProcedureWalker.applyRecorded(OathState.Ready(ready),
      step("0.1", automatic = true))
    assertEquals(replayed.map(pending), Right(Some(PendingTree(
      at = Vector("0", "1"), answered = Vector(answer)))))

  test("a player's answer still needs the park it answers"):
    assert(ProcedureWalker.applyRecorded(OathState.Ready(ready),
      step("0.1", automatic = false)).isLeft)

  test("an automatic answer after a park joins its answers and keeps its " +
      "position"):
    val earlier = Answered("test.first",
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("a")), playerId)
    val parked = ready.updateCurrent(_.copy(walkerPending = Some(PendingTree(
      at = Vector("0"), answered = Vector(earlier)))))
    val replayed = ProcedureWalker.applyRecorded(OathState.Ready(parked),
      step("2", automatic = true))
    assertEquals(replayed.map(pending), Right(Some(PendingTree(
      at = Vector("0"), answered = Vector(earlier, answer)))))

  test("a later park carrying the automatic answer replays"):
    val park = WalkerParked(ActionRef.Challenge, Vector("1"), Vector(answer),
      Vector.empty, Vector.empty)
    val replayed = ProcedureWalker.applyRecorded(OathState.Ready(ready),
      step("0", automatic = true))
      .flatMap(ProcedureWalker.applyRecorded(_, park))
    assertEquals(replayed.map(pending), Right(Some(PendingTree(
      at = Vector("1"), answered = Vector(answer)))))
```

In `GameEventWireSuite.scala`, after the test "a choose-amount answer round trips through the journal", add:

```scala
  test("an automatic answer round trips through the journal, and a player's " +
      "answer writes no automatic flag"):
    val player = PlayerId("red")
    val answer = ChooseOneAnswer(DecisionOptionRef.Button("keep"))
    val events = Vector[OathEvent](
      WalkerStepRecorded("1", ChoicePayload("test.ask", answer, player,
        automatic = true), Vector.empty, Vector.empty),
      WalkerStepRecorded("2", ChoicePayload("test.ask", answer, player),
        Vector.empty, Vector.empty))
    val encoded = GameEventWire.encodeStream("automatic", catalogRef,
      events.zipWithIndex.map { case (event, index) => RecordedEvent(index, event) })
      .toOption.get
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event),
      events)
    val records = ujson.read(encoded).arr
    assert(records(0).toString.contains("\"automatic\":true"), records(0).toString)
    assert(!records(1).toString.contains("automatic"), records(1).toString)
```

In `GameLogPowerLinesSuite.scala`, after the test "pressing Done on an Inspect writes no Chose line", add:

```scala
  test("an answer the walker gave to a forced choice writes no Chose line"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    def answered(automatic: Boolean) = inserted(steps, take(steps),
      WalkerStepRecorded("inspect", ChoicePayload("power.scryer.inspect",
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("other")),
        script.actor, automatic), Vector.empty, Vector.empty))
    def chose(lines: Vector[String]) = lines.count(_.startsWith("Chose "))
    val before = chose(lines(steps))
    assertEquals(chose(lines(answered(automatic = false))), before + 1)
    assertEquals(chose(lines(answered(automatic = true))), before)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.AutomaticChoiceReplaySuite"`
Expected: compile failure: `ChoicePayload` takes three arguments.

- [ ] **Step 3: Add the flag, its wire form, its replay and its log rule**

In `WalkerEvents.scala`, replace the `ChoicePayload` doc and declaration with:

```scala
/** Records a resolved parked decision (Task 5 ruling 5.3), or, when
  * `automatic`, the answer the walker gave a forced choice left with one
  * option (global operation restrictions design, "Forced choices"). The
  * step's `ops` stay empty: appending the answer to `pending.answered` is a
  * state write (the walker rebuilds `answered` from these events at replay),
  * not an operation batch.
  *
  * @param by who answered: the decision's owner for an automatic answer.
  */
final case class ChoicePayload(decisionId: String, answer: DecisionAnswer,
    by: PlayerId, automatic: Boolean = false)
    extends WalkerStepPayload
```

In `WalkerEventCodec.scala`, replace the `ChoicePayload` encode case with:

```scala
      case ChoicePayload(decisionId, answer, by, automatic) =>
        val encoded = ujson.Obj(
          "kind" -> "choice", "decisionId" -> decisionId,
          "payload" -> DecisionAnswerCodec.encode(answer),
          "byPlayerId" -> by.value)
        if automatic then encoded("automatic") = ujson.True
        encoded
```

and the `"choice"` decode case with:

```scala
      case "choice" => DecisionAnswerCodec.decode(value("payload"), s"$path.payload")
        .map(ChoicePayload(value("decisionId").str, _,
          PlayerId(value("byPlayerId").str),
          value.obj.get("automatic").exists(_.bool)))
```

In `WalkerReplay.scala`, replace the `ChoicePayload` case with:

```scala
      // An automatic answer has no park before it: only its node id is
      // checked, and with nothing pending it starts the pending answers. A
      // later park carries the same answers, and a completion clears them.
      case step @ WalkerStepRecorded(_,
          ChoicePayload(decisionId, payload, by, automatic), ops, _) =>
        for
          pending <- if automatic then validateStep(step).map(_ =>
              ready.game.current.walkerPending.getOrElse(PendingTree(
                at = step.nodeId.split('.').toVector, answered = Vector.empty)))
            else validateParkedStep(step)
          _ <- Either.cond(ops.isEmpty, (), OathViolation.InvalidEventOrder(
            "recorded ChoicePayload must not contain operations"))
          answered = pending.copy(answered = pending.answered :+
            Answered(decisionId, payload, by))
        yield ready.copy(game = ready.game.copy(current =
          ready.game.current.copy(walkerPending = Some(answered))))
```

In `DetailLines.scala`, change the `decision` case head to:

```scala
    case WalkerStepRecorded(_, ChoicePayload(id, answer, by, automatic), _, _)
        if !automatic && !DetailLines.narrated(id) && !narrated(id) =>
```

- [ ] **Step 4: Fix the remaining patterns**

Every other `ChoicePayload(a, b, c)` pattern now has the wrong arity. Add a
fourth `_` to each, changing nothing else:
`CampaignLines.scala` lines 26, 37 and 45, `LogJournal.scala` line 85,
`NegotiationLines.scala` line 33, `ActionLines.scala` lines 192, 199 and 220,
and the tests `OathRulesWalkerPowerSuite.scala` line 100,
`OathkeeperProcedureSuite.scala` line 80 and
`GameApplicationServiceSuite.scala` line 1048. Constructor calls with three
arguments keep compiling through the default.

Run: `./sbtw Test/compile`
Expected: success. If the compiler names another three-argument pattern, add `_` there too.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.AutomaticChoiceReplaySuite oathdigital.serialization.GameEventWireSuite oathdigital.application.gamelog.GameLogPowerLinesSuite"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A src
git commit -m "feat(walker): record, replay and log an automatic answer"
```

---

### Task 3: Forced choices in the walker and the search

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala:589-600` (`Decide`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSearch.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`
- Create: `src/test/scala/oathdigital/gameplay/ForcedChoiceSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/walker/WalkerSearchSuite.scala`

**Interfaces:**
- Consumes: `ChoicePayload(..., automatic)` from Task 2.
- Produces: `Decide(decisionId, owner, query, window = None, coOwners = Vector.empty, autoAnswer: Boolean = false)`, with `autoAnswer` allowed only on a `ChooseOne`. `WalkerSearch.forced(decide: Decide): Option[DecisionAnswer]`. `DecisionQueries.answerable(decide: Decide, answer: Answered): Either[OathViolation, Unit]`.

- [ ] **Step 1: Write the failing tests**

In `WalkerSearchSuite.scala`, add after `private val abc`:

```scala
  private def forcedAsk(query: DecisionQuery) =
    decide(query).copy(autoAnswer = true)
```

and these tests after "an optional choose-many with no survivor is not asked":

```scala
  test("a forced choice with no survivor is not asked, and a search passes it"):
    val asked = forcedAsk(DecisionQuery.ChooseOne(Vector(button("a"))))
    assertEquals(WalkerSearch.narrow(asked, only(_ => false)), Right(None))
    assertEquals(WalkerSearch.reach(asked, only(_ => false)),
      Right(WalkerSearch.Reach.Skipped))

  test("only a forced choice left with one option is answered by the walker"):
    assertEquals(WalkerSearch.forced(forcedAsk(DecisionQuery.ChooseOne(
      Vector(button("b"))))), Some(ChooseOneAnswer(ref("b"))))
    assertEquals(WalkerSearch.forced(forcedAsk(DecisionQuery.ChooseOne(
      Vector(button("a"), button("b"))))), None)
    assertEquals(WalkerSearch.forced(decide(DecisionQuery.ChooseOne(
      Vector(button("b"))))), None)
```

Create `ForcedChoiceSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ChoicePayload, ProcedureWalker,
  WalkerOutcome, WalkerParked, WalkerSimulation, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** A forced choice (`Decide.autoAnswer`) is asked only when the search
  * leaves a real choice: the walker answers the one option left itself and
  * passes a decision with none left (global operation restrictions design,
  * "Forced choices"). Locked refuses to discard the faceup Sealing Ward,
  * which is what prunes an option here.
  */
class ForcedChoiceSuite extends munit.FunSuite:
  private val powers = WalkerPowerCatalog.default(catalog)
  private val lockedCard = CatalogNames.denizen("Sealing Ward")
  private val ready = Table.start.adviser(p1, lockedCard).ready
  private val ask = "test.ask"
  private val next = "test.next"
  private def button(key: String) =
    DecisionOption.Button(DecisionOptionRef.Button(key), key)
  private def ref(key: String): DecisionOptionRef = DecisionOptionRef.Button(key)
  private def chose(key: String): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(ref(key))

  private val discardLocked: Operation = Discard.Denizen(lockedCard,
    PositionedLocation(Location.PlayArea(p1)), Region.Provinces,
    catalog.suitOf(lockedCard).get, 0, 0, p1, required = true)

  /** A forced choice of `keys`, then the discard when "discard" was chosen. */
  private def forced(keys: String*): Operation = Sequence(Vector[Operation](
    Decide(ask, p1, DecisionQuery.ChooseOne(keys.toVector.map(button)),
      autoAnswer = true),
    Branch((_, pending) => pending.answered.collectFirst {
      case Answered(`ask`, answer, _) => answer
    }.filter(_ == chose("discard")).toVector.map(_ => discardLocked))))

  private def choices(events: Vector[OathEvent]): Vector[ChoicePayload] =
    events.collect { case WalkerStepRecorded(_, choice: ChoicePayload, _, _) =>
      choice }

  private def finished(tree: Operation): Vector[OathEvent] =
    ProcedureWalker.advance(ready, tree, None, powers) match
      case Right(WalkerOutcome.Finished(_, events)) => events
      case other => fail(s"expected a finished walk, got $other")

  test("one option left is answered by the walker, without a park"):
    assertEquals(choices(finished(forced("discard", "keep"))),
      Vector(ChoicePayload(ask, chose("keep"), p1, automatic = true)))

  test("a single declared option is answered by the walker"):
    assertEquals(choices(finished(forced("keep"))),
      Vector(ChoicePayload(ask, chose("keep"), p1, automatic = true)))

  test("with no option left the decision is passed and the action still runs"):
    val tree = forced("discard")
    assert(WalkerSimulation.starts(tree, ready, powers))
    assertEquals(choices(finished(tree)), Vector.empty)

  test("several options left park as usual"):
    ProcedureWalker.advance(ready, forced("keep", "other"), None, powers) match
      case Right(WalkerOutcome.Parked(pending, _)) =>
        assertEquals(pending.at, Vector("0"))
      case other => fail(s"expected a park, got $other")

  test("a search passes a later forced choice with nothing left, so the " +
      "earlier option stays offered"):
    val tree = Sequence(Vector[Operation](
      Decide(next, p1, DecisionQuery.ChooseOne(Vector(button("onward"),
        button("stop")))),
      Branch((_, pending) =>
        if pending.answered.exists(_.answer == chose("onward"))
        then Vector(forced("discard")) else Vector.empty)))
    val Right(WalkerOutcome.Parked(pending, _)) =
      ProcedureWalker.advance(ready, tree, None, powers): @unchecked
    val offered = ProcedureWalker.openDecisions(ready, tree, pending, powers)
      .head.query match
      case DecisionQuery.ChooseOne(options, _) => options.map(_.ref)
      case other => fail(s"expected a choose-one, got $other")
    assertEquals(offered, Vector(ref("onward"), ref("stop")))

  test("an answer given before a later park is kept for its resume and " +
      "replays"):
    val tree = Sequence(Vector[Operation](forced("discard", "keep"),
      Decide(next, p1, DecisionQuery.ChooseOne(Vector(button("a"),
        button("b"))))))
    val Right(WalkerOutcome.Parked(pending, events)) =
      ProcedureWalker.advance(ready, tree, None, powers): @unchecked
    assertEquals(pending.answered, Vector(Answered(ask, chose("keep"), p1)))
    val park = WalkerParked(ActionRef.Challenge, pending.at, pending.answered,
      Vector.empty, Vector.empty)
    val replayed = (events.collect { case event: WalkerEvent => event } :+ park)
      .foldLeft[Either[OathViolation, OathState]](Right(OathState.Ready(ready)))(
        (state, event) => state.flatMap(ProcedureWalker.applyRecorded(_, event)))
    assert(replayed.isRight, replayed.toString)

  test("only a choose-one can be a forced choice"):
    intercept[IllegalArgumentException](Decide(ask, p1,
      DecisionQuery.ChooseMany(0, 1, Vector(button("a")), None),
      autoAnswer = true))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.ForcedChoiceSuite oathdigital.gameplay.walker.WalkerSearchSuite"`
Expected: compile failure: `autoAnswer` is not a member of `Decide`.

- [ ] **Step 3: Add the flag to `Decide`**

In `CoreOperations.scala`, add to the end of the `Decide` doc, after the `window` paragraph:

```scala
  *
  * `autoAnswer` makes a choose-one a forced choice (global operation
  * restrictions design, "Forced choices"): it is asked only when the search
  * leaves more than one option. The walker answers the one option left
  * itself, and passes the decision when none is left.
```

and change the declaration to:

```scala
final case class Decide(decisionId: String, owner: PlayerId,
    query: DecisionQuery,
    override val window: Option[PowerWindow] = None,
    coOwners: Vector[PlayerId] = Vector.empty,
    autoAnswer: Boolean = false)
    extends PrimitiveOperation:
  require(!autoAnswer || query.isInstanceOf[DecisionQuery.ChooseOne],
    "only a choose-one can be a forced choice")
```

- [ ] **Step 4: Teach the search forced choices**

In `WalkerSearch.scala`:

In `narrow`, change the choose-one case's `required = true` to
`required = !decide.autoAnswer`, and extend its doc's last sentence: "`Right(None)` is an optional one with nothing left, which is not asked; a forced choice with nothing left is optional."

Replace `reach` with:

```scala
  /** What a search does at `decide`: it passes an optional decision with
    * nothing to ask, stops at the first answer that survives, and fails when
    * none does. A forced choice with no surviving answer is passed. A kind
    * whose answers are not tried (`Partition`, `Distribute`) counts as
    * answerable, and the answer-time check stays.
    */
  def reach(decide: Decide,
      verdict: DecisionAnswer => Either[OathViolation, Unit])
      : Either[OathViolation, Reach] =
    narrowed(decide, _ => true) match
      case None => Right(Reach.Skipped)
      case Some(_) => answers(decide) match
        case None => Right(Reach.Answerable)
        case Some(all) => survivor(all, verdict, None) match
          case Left(_) if decide.autoAnswer => Right(Reach.Skipped)
          case found => found.map(_ => Reach.Answerable)
```

In `narrowed`, replace the choose-one case with:

```scala
      case one: DecisionQuery.ChooseOne =>
        val options = one.options.filter(o => permitted(o.ref))
        if decide.autoAnswer && options.isEmpty then None
        else Some(decide.copy(query = one.copy(options = options)))
```

and change its doc's second sentence to "`None` is an optional choose-many or a forced choice with nothing left, which is not asked."

Add after `narrowed`:

```scala
  /** The answer the walker gives a forced choice left with one option, else
    * `None`. */
  def forced(decide: Decide): Option[DecisionAnswer] = decide.query match
    case DecisionQuery.ChooseOne(Vector(only), _) if decide.autoAnswer =>
      Some(DecisionAnswer.ChooseOneAnswer(only.ref))
    case _ => None
```

- [ ] **Step 5: Move the answer checks into `DecisionQueries`**

In `DecisionQueries.scala`, add `Answered` and `Decide` to the model import, and add after `wellFormed`:

```scala
  /** Whether `answer` may answer `decide`: its submitter is one of the
    * node's owners, the query is answerable at all, and the query accepts the
    * answer. The checks read no game state, so the walker learns nothing here
    * about which action parked: a legality fact that used to live in a
    * per-node `validate` closure now lives in the declared option set, which
    * is also what the projector offers.
    */
  def answerable(decide: Decide, answer: Answered): Either[OathViolation, Unit] =
    for
      _ <- Either.cond(decide.owners.contains(answer.by), (),
        OathViolation.WrongPlayer(decide.owner, answer.by))
      _ <- wellFormed(decide.decisionId, decide.query)
      _ <- accepts(decide.decisionId, decide.query, answer.answer, answer.by)
    yield ()
```

- [ ] **Step 6: Answer a forced choice in the walker**

In `ProcedureWalker.scala`:

Add after `leafLabel`:

```scala
  /** A step's node id: its path, or its leaf label at the root. */
  private def nodeId(path: Vector[String], leaf: Operation): String =
    if path.isEmpty then leafLabel(leaf) else path.mkString(".")
```

Replace the whole of `answerDecide`, its doc included, with:

```scala
  /** Checks an answer against its Decide ([[DecisionQueries.answerable]]) and
    * records it: `answer` is appended to `answered`, and ONE
    * [[WalkerStepRecorded]] carrying a [[ChoicePayload]] (ops empty -- the
    * answer is a state write into `pending.answered`) is appended. An
    * `automatic` answer is one the walker gave a forced choice.
    */
  private def answerDecide(decide: Decide, ctx: WalkCtx,
      path: Vector[String], answer: Answered, contributions: Vector[PowerId],
      automatic: Boolean = false): Either[OathViolation, WalkCtx] =
    DecisionQueries.answerable(decide, answer).map(_ => ctx.copy(
      answered = ctx.answered :+ answer,
      previous = Some((ctx.state, ctx.state)),
      events = ctx.events :+ WalkerStepRecorded(
        nodeId = nodeId(path, decide),
        payload = ChoicePayload(answer.decisionId, answer.answer, answer.by,
          automatic),
        ops = Vector.empty, contributions = contributions)))
```

In `recordRoll`, delete the two lines

```scala
      val nodeId =
        if path.isEmpty then leafLabel(roll) else path.mkString(".")
```

and change `nodeId = nodeId, payload = RollPayload(...)` to `nodeId = nodeId(path, roll), payload = RollPayload(...)`.

In `narrowAt`, change `if !ctx.powers.probing then Right(Some(decide))` to:

```scala
    if !ctx.powers.probing then
      Right(if decide.autoAnswer then WalkerSearch.narrowed(decide, _ => true)
        else Some(decide))
```

In `runLeaf`, replace the `case Some(narrowed) =>` branch with:

```scala
          case Some(narrowed) =>
            // Reached fresh, so this is the only time it is asked this pass.
            val hidden = if cursor.nonEmpty then Vector.empty
              else WalkerPowerGather.lookAheadNotes(ctx.root, decide, narrowed,
                ctx.state, ctx.activePlayer, ctx.powers, ctx.answered,
                ctx.procedure)
            val noted = ctx.copy(events = ctx.events ++ hidden)
            WalkerSearch.forced(narrowed).filter(_ => cursor.isEmpty) match
              case Some(answer) => answerDecide(narrowed, noted, path,
                Answered(narrowed.decisionId, answer, narrowed.owner),
                contributions, automatic = true).map(Done(_))
              case None => runNarrowed(narrowed, noted, path, cursor, resume,
                contributions, strict)
```

and add to `runLeaf`'s doc, after "…is passed without asking.": "A forced choice left with one option is answered here, without a park."

- [ ] **Step 7: Check the line bound**

Run: `wc -l src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`
Expected: at most 800 (about 791).

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.ForcedChoiceSuite oathdigital.gameplay.walker.WalkerSearchSuite oathdigital.gameplay.LazyPruningSuite oathdigital.gameplay.ProcedureWalkerSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add -A src
git commit -m "feat(walker): answer a forced choice the search leaves with one option"
```

---

### Task 4: Fae Merchant asks a forced choice

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/FaeMerchant.scala` (whole file)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/DiceAndRelicDrawPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala:20`
- Test: `src/test/scala/oathdigital/gameplay/powers/action/FaeMerchantSuite.scala`, `src/test/scala/oathdigital/application/DicePowerDecisionProjectionSuite.scala`

**Interfaces:**
- Consumes: `Decide(..., autoAnswer = true)` (Task 3), `ChoicePayload(..., automatic)` (Task 2), `GrandScepter` (Task 1), `BuildOps(build, required = true)` (slice 2).
- Produces: `case object FaeMerchant extends PaidAction` with `decisionId` and `returned` unchanged; `FaeMerchant.forCatalog` is gone. `DiceAndRelicDrawPowers.powers: Vector[PhasePower]` replaces `DiceAndRelicDrawPowers.forCatalog`.

- [ ] **Step 1: Write the tests**

In `FaeMerchantSuite.scala`, change the walker import to
`import oathdigital.gameplay.walker.{ChoicePayload, ParkedDecisionAssertions, WalkerStepRecorded}`,
and change `val merchant = FaeMerchant.forCatalog(catalog)` to `val merchant = FaeMerchant`.

Replace the test "with only the Grand Scepter held, the drawn relic is the one relic eligible and goes straight back" with:

```scala
  test("with only the Grand Scepter held, the drawn relic is the one relic " +
      "left and goes straight back"):
    val ready0 = staged(Vector(scepter))
    val top = ready0.game.current.commonCards.relicDeck.head
    val rules0 = rules()
    val done = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    val end = ready(done.state)
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    assertEquals(relicIds(end), Vector(scepter))
    assertEquals(end.game.current.commonCards.relicDeck.last, top)
    assert(done.events.exists {
      case WalkerStepRecorded(_, ChoicePayload(FaeMerchant.decisionId, _, _,
          true), _, _) => true
      case _ => false
    }, done.events.toString)
    assertEquals(replayed(rules0, ready0, done.events), end)
    assert(wireRoundTrips(done.events))

  test("with only the Grand Scepter held and an empty relic deck, it puts " +
      "nothing back"):
    val ready0 = staged(Vector(scepter))
    val current = ready0.game.current
    val emptied = ready0.updateCurrent(_.copy(commonCards =
      current.commonCards.copy(relicDeck = Vector.empty)))
      .updateCampaign(c => c.copy(reliquary = c.reliquary ++
        current.commonCards.relicDeck))
    assert(usableIds(emptied).contains(FaeMerchant.id))
    val rules0 = rules()
    val done = use(rules0, emptied, FaeMerchant.id, source).toOption.get
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    val end = ready(done.state)
    assertEquals(relicIds(end), Vector(scepter))
    assertEquals(end.game.current.commonCards.relicDeck, Vector.empty[RelicId])
    assertEquals(tokensOn(end, fae), Tokens(0, 1))
    assertEquals(replayed(rules0, emptied, done.events), end)
```

In `DicePowerDecisionProjectionSuite.scala`, after the test "Fae Merchant names both eligible relics to its owner, including the facedown one just taken", add:

```scala
  test("Fae Merchant does not offer the Grand Scepter"):
    val fae = DenizenId("180")
    val held = RelicId("R08")
    val scepter = RelicId("grand-scepter")
    val ready0 = act(withBoard(withRelic(withRelic(atHome(base, fae), scepter),
      held))(_.copy(faceUpSecrets = 2)))
    val top = ready0.game.current.commonCards.relicDeck.head
    val parked = ready(use(rules(), ready0, FaeMerchant.id,
      DecisionOptionRef.Denizen(fae)).toOption.get.state)
    assertEquals(owner(parked).get.query.get.offeredOptions.map(_.id),
      Vector(held.value, top.value))
```

- [ ] **Step 2: Run the tests to verify the new assertion fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.FaeMerchantSuite oathdigital.application.DicePowerDecisionProjectionSuite"`
Expected: compile failure on `FaeMerchant` used as a value (`merchant = FaeMerchant` needs the object). The behaviour tests are regression guards: with the old filter they already pass, and they must still pass once the filter is gone. The automatic-answer assertion is the one that fails on the old code.

- [ ] **Step 3: Rewrite Fae Merchant**

Replace `FaeMerchant.scala` with:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts, RelicDraws}
import oathdigital.model._

/** Fae Merchant (card 180), ACTION: place 1 secret on this card, draw a relic
  * and take it facedown, then put exactly one relic you hold, except the
  * Grand Scepter, on the bottom of the relic deck. The relic just taken is
  * eligible.
  *
  * Three siblings run in order: the draw, a live `Branch` that holds only the
  * decision and reads the relics after the draw, and the bury. The decision
  * offers every relic held and is a forced choice (`Decide.autoAnswer`). The
  * Grand Scepter's restriction refuses its bury, and the bury is a required
  * batch, so the search hides the scepter. With one relic left it goes back
  * without asking; with none left (only the scepter held and the relic deck
  * empty) nothing goes back. The bury returns any secrets on the relic to
  * their holder. Its `returned` line names the relic chosen, so the choice
  * posts no "Chose" line.
  */
case object FaeMerchant extends PaidAction("denizen.fae-merchant",
    Cost(secret = 1)):
  val decisionId: String = "fae-merchant.relic"
  val returned: NoteKey = NoteKey("returned", Vector(NotePart.Arg(0),
    NotePart.Text(" put "), NotePart.Arg(1),
    NotePart.Text(" on the bottom of the relic deck.")))

  override def noteKeys: Vector[NoteKey] = Vector(RelicDraws.drew, returned)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((state, _) => Right(RelicDraws.takeTop(state, player))),
    Note(this.id, RelicDraws.drawNote(source, player)),
    Branch((state, _) => held(state, player) match {
      case Vector() => Vector.empty
      case relics => Vector(Decide(decisionId, player,
        DecisionQuery.ChooseOne(relics.map(id =>
          DecisionOption.Relic(DecisionOptionRef.Relic(id))),
          heading = Some("Fae Merchant: put a relic on the bottom of the " +
            "relic deck")),
        autoAnswer = true))
    }),
    BuildOps((state, pending) => putBack(state, player, pending),
      required = true),
    Note(this.id, returnNote(_, player, source), covers = true))))

  /** The relic the bury took from the player, in place of its Buried line. */
  private def returnNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    relic <- NoteSupport.relicsLost(step, player).headOption
  yield returned(card, NoteArg.Player(player), NoteArg.Card(relic))

  /** The relics the player holds, in play-area order. */
  private def held(state: ReadyGame, player: PlayerId): Vector[RelicId] =
    PlayerFacts.player(state, player).toOption.toVector.flatMap(_.relics)
      .map(_.id)

  /** Buries the chosen relic. No answer means the forced choice had no relic
    * left to offer, so nothing goes back. */
  private def putBack(state: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    val chosen = pending.answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.Relic(id)), _) => id
    }
    PlayerFacts.player(state, player).map(holder => chosen.toVector.flatMap {
      id =>
        val secrets = holder.relics.find(_.id == id).fold(0)(_.tokens.secrets)
        Bury.standard(BuryableCard.Relic(id),
          PositionedLocation(Location.PlayArea(player)), None, 0, secrets,
          player)
    })
```

Check that `NoteSupport`, `PlayerFacts` and `RelicDraws` are still all used (they are: `relicsLost`, `player`, `takeTop`), so no import warning appears.

Replace `DiceAndRelicDrawPowers.scala`'s object with (and drop the `ExecutableCatalog` import):

```scala
object DiceAndRelicDrawPowers:
  val powers: Vector[PhasePower] =
    Vector[PhasePower](GamblingHall, BoneDice, MurkyFountain, DowsingSticks,
      FaeMerchant)
```

In `PhasePowerCatalog.scala`, change `DiceAndRelicDrawPowers.forCatalog(catalog) ++` to `DiceAndRelicDrawPowers.powers ++`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.FaeMerchantSuite oathdigital.application.DicePowerDecisionProjectionSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS, including the unchanged tests "the Grand Scepter is never offered, and cannot be chosen", "with no other relic the drawn one is the only candidate and no decision is asked" and "an empty relic deck still puts one held relic on the bottom".

Run: `grep -rn "FaeMerchant.forCatalog\|DiceAndRelicDrawPowers.forCatalog" src`
Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "refactor(powers): hide the Grand Scepter from Fae Merchant through the search"
```

---

### Task 5: Challenge and Conspiracy take the banner

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala:434-444` (`Take`)
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala:151`
- Modify: `src/main/scala/oathdigital/application/gamelog/CampaignLines.scala:128,130`
- Modify: `src/main/scala/oathdigital/gameplay/actions/challenge/ChallengeProcedure.scala:131-137`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala:108-110`
- Test: `src/test/scala/oathdigital/gameplay/ChallengeProcedureSuite.scala`, `src/test/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayedSuite.scala`

**Interfaces:**
- Produces: `Take(piece, player, from, to, sourcePosition = StackPosition.Unspecified, required: Boolean = false)`. `required` is not on the wire.

- [ ] **Step 1: Write the failing tests**

In `ChallengeProcedureSuite.scala`, add after "a power's transforms reach the banner and amount selections" (`ProcedureWalkerSuite` is in the same package, so its test power needs no import):

```scala
  test("a restriction on Take sees Challenge's custody and hides the banner"):
    val board = challenger.secrets(p1, faceUp = 0, faceDown = 4)
      .peoplesFavor(None, favor = 2).ready
    val tree = ChallengeProcedure.build(catalog, board, p1, Vector.empty)
      .getOrElse(fail("the tree must build"))
    val noBannerTaken = new OperationRestriction:
      override def reason(ready: ReadyGame,
          operation: CoreOperation): Option[OperationReason] = operation match
        case Take(Piece.Banner(_), _, _, _, _, _) => Some(OperationReason(
          "test.no-banner", "no banner may be taken",
          OperationReasonKind.Impossible))
        case _ => None
    val powers = WalkerPowers(Vector(
      ProcedureWalkerSuite.TestOperationRestrictionPower(
        PowerId("test.no-banner"), noBannerTaken)))
    assert(ProcedureWalker.advance(board, tree, None, powers).isLeft)
```

In `ConspiracyWhenPlayedSuite.scala`, in the test "Conspiracy taking the Darkest Secret burns every secret and takes the banner", add before `assertEquals(replayed(f, done), after)`:

```scala
    assert(recorded(done).contains(Take(Piece.Banner(Banner.DarkestSecret),
      f.actor, Location.PlayArea(f.enemy), Location.PlayArea(f.actor))),
      recorded(done).toString)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.ChallengeProcedureSuite oathdigital.gameplay.powers.whenplayed.ConspiracyWhenPlayedSuite"`
Expected: compile failure: the `Take` pattern has six arguments. (After Step 3's model change alone, both new tests fail on behaviour: Challenge parks, and Conspiracy records a `Move`.)

- [ ] **Step 3: Give `Take` a `required` flag**

In `CoreOperations.scala`, change `Take` to:

```scala
/** Moves a piece into the prompted player's custody. A required `Take`
  * rejects its batch, or fails the search's path, when a restriction refuses
  * it.
  */
final case class Take(piece: Piece, player: PlayerId,
    from: Location, to: Location,
    sourcePosition: StackPosition = StackPosition.Unspecified,
    override val required: Boolean = false)
    extends CoreOperation:
```

(the body is unchanged). Add a sixth `_` to the `Take` patterns:
`WalkerOperationCodec.scala` line 151 becomes
`case Take(piece, player, from, to, sourcePosition, _) => ujson.Obj(`, and
`CampaignLines.scala` lines 128 and 130 become
`OpStep(Take(Piece.Card(id), _, _, _, _, _), before, after)` and
`OpStep(Take(Piece.Banner(banner), _, _, _, _, _), _, _)`.

Run: `./sbtw Test/compile`
Expected: success. If the compiler names another five-argument `Take` pattern, add `_` there too.

- [ ] **Step 4: Take the banner in Challenge and Conspiracy**

In `ChallengeProcedure.scala`, replace `custody` with:

```scala
  /** The banner moves to the challenger by a required `Take`: the payment
    * buys it, so a refused custody fails the path and the search hides that
    * banner. The walker checks the `Take` whole and records its `Move`, which
    * the Game Log reads.
    */
  private def custody(ready: ReadyGame, actor: PlayerId,
      banner: Banner): Operation =
    Take(Piece.Banner(banner), actor,
      BannerRules.holder(ready.game.current, banner)
        .fold[Location](Location.SharedBank)(Location.PlayArea(_)),
      Location.PlayArea(actor), required = true)
```

In `ConspiracyWhenPlayed.scala`, replace the local `banner` function in `take` with:

```scala
    def banner(held: Banner, owner: PlayerId): CoreOperation = Take(
      Piece.Banner(held), actor, Location.PlayArea(owner),
      Location.PlayArea(actor))
```

and add to the class doc, after the "take effects" bullet list: "The banner moves by a `Take`, so a restriction on taking sees it; a relic moves by a `Give`. Both are optional."

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.ChallengeProcedureSuite oathdigital.gameplay.powers.whenplayed.ConspiracyWhenPlayedSuite oathdigital.application.gamelog.GameLogActionLineSuite oathdigital.application.gamelog.GameLogRareLinesSuite oathdigital.serialization.GameEventWireSuite"`
Expected: PASS. `GameLogActionLineSuite`'s "Challenge takes the banner from the bank; Place Banner Resource names the amount" and Conspiracy's "line names the banner it seized" show the Game Log lines are unchanged.

- [ ] **Step 6: Commit**

```bash
git add -A src
git commit -m "feat(actions): take the banner by Take in Challenge and Conspiracy"
```

---

### Task 6: Close the phase

**Files:**
- Modify: `docs/superpowers/specs/2026-09-30-global-operation-restrictions-design.md:3-4`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Run the full suite and the architecture check**

Run: `./sbtw test`
Expected: every test passes (2149 before this slice, plus the new ones).

Run: `python3 scripts/check-architecture.py`
Expected: `architecture check passed`.

- [ ] **Step 2: Mark the spec done**

Replace the spec's status paragraph

```markdown
**Status:** designed 2026-09-30. It is the prerequisite of
[Catalog batch 3](2026-09-29-catalog-batch-3-design.md), which starts after it.
```

with

```markdown
**Status:** designed and implemented 2026-09-30, in three slices. It is the
prerequisite of [Catalog batch 3](2026-09-29-catalog-batch-3-design.md), which
starts next.
```

- [ ] **Step 3: Update the roadmap**

In `docs/ROADMAP.md`:
- In "## Now", replace "**Phase - Global operation restrictions** is next, then **Phase - Catalog batch 3**, which depends on it, then **Phase - Card classes**." with "**Phase - Catalog batch 3** is next, then **Phase - Card classes**." and rewrap the paragraph.
- Delete the whole "### Phase - Global operation restrictions" section, from its heading through the "Grand Scepter and Take" item.
- In "### Phase - Catalog batch 3", delete the line "Starts after **Phase - Global operation restrictions**."

Run: `grep -n "Global operation restrictions" docs/ROADMAP.md`
Expected: only the Card classes paragraph's mention of the catalog function that phase built.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-09-30-global-operation-restrictions-design.md docs/ROADMAP.md
git commit -m "docs: close the global operation restrictions phase"
```
