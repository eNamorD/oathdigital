# Negotiation on the Procedure Walker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Negotiation onto the procedure walker as `ActionRef.Negotiation`, with the deal held as a fold over `PendingTree.answered`, and delete the legacy Negotiation path.

**Architecture:** A `Repeat` loop around one co-owned `Decide` (`negotiation.deal`) carries the conversation: each answer (`ProposeTerms`, `AcceptDeal`, `DeclineDeal`) is recorded in `answered`, the deal is `NegotiationDeal.fold(...)`, and the query is a snapshot rebuilt from live state on every command. The engine gains `Decide.coOwners`, an open-decision park API and `accepts(..., by)`. `ChooseMany` becomes a range, and `MajorActionKind` becomes `ActionKind`. Legacy `PendingProcedure.Negotiation`, its commands, events and projection are deleted after a differential parity test.

**Tech Stack:** Scala 2.13, sbt via `./sbtw`, munit, Scala.js frontend (`frontend/`), shared protocol module (`shared/`, compiled inside the root project).

**Spec:** [docs/superpowers/specs/2026-09-19-negotiation-walker-design.md](../specs/2026-09-19-negotiation-walker-design.md)

## Global Constraints

- Persisted text (code, comments, docs, commit messages, PR text) is normal English. Commit trailer: `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.
- Run sbt through `./sbtw`. `shared/` compiles inside the root project, so its tests run through the root `testOnly`; frontend tests run through `frontend/testOnly`.
- `BackendArchitectureSuite` bounds every production Scala file to 800 lines and forbids power names in walker sources and model imports of gameplay. Check `wc -l` on every file a task grows.
- The walker rule W: a `Branch.select`, a `Repeat` guard and a `Decide` query read only answered values plus state no earlier step of the same tree has changed. Anything else is built inside a `BuildOps`.
- Spec, "Rule changes, all deliberate": no first-game gate; unsupported Negotiation rules are ignored and recorded (`ActionKind.Negotiation`, `fallbackKind`); non-participants and the public view see the deal.
- Spec, "Out of scope": Citizenship, Grand Scepter, secret/adviser/site/banner transfers, remote and private Negotiation, promises, every Negotiation power, the `Simultaneous` node, and the first-game gate on other minor actions and Campaign.
- Journals are forward-only: no old-journal compatibility.
- After every task: the task's suites pass and `./sbtw "Test/compile" "frontend/Test/compile"` succeeds. After Tasks 5, 8, 11 and 12 run the full gate `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`.
- macOS: use `perl -pi -e` for in-place regex edits, not `sed -i`.

## Deviations from the spec

Found while reading the code for this plan. Task 12 folds each into the spec.

1. **`DecisionQuery.Negotiate` also carries the deal.** Its fields are `participants`, `terms`, `accepted`, `bounds`, `acceptors`, `heading`. The spec lists only participants, bounds and acceptors. Carrying the current terms and acceptances lets the generic projector render the deal from the query alone, without folding `answered` itself.
2. **`parkedDecide` and `awaitedPlayer` stay** as single-decision conveniences (`openDecisions(...).headOption` and the primary owner). The spec says they are replaced. About 17 test call sites use `parkedDecide`, and a `Simultaneous` node is the only thing that needs them gone. `openDecisions` and `awaitedPlayers` are the new set-returning API.
3. **`Negotiation.eligible` becomes `NegotiationDeal.eligible`.** The new code lives in `gameplay/actions/negotiation/`; the old `gameplay/actions/Negotiation.scala` is deleted in Task 10.
4. **`WalkerWaitingProjection` gains `coOwnerPlayerIds` and `deal`.** `playerId` stays the primary owner.
5. **`DecisionQueryProjection.count` is removed.** `ChooseMany` projects `minimum` and `maximum`, the fields `ChooseAmount` already uses.

## File structure

New:
- `src/main/scala/oathdigital/gameplay/actions/negotiation/NegotiationDeal.scala`: `DealState`, `fold`, `participants`, `eligible`, `snapshot`, `settle`. The deal rules, pure and with no walker types beyond `Answered`.
- `src/main/scala/oathdigital/gameplay/actions/negotiation/NegotiationProcedure.scala`: the tree, `build`, `rebuild`, `startable`, the decision ids.
- `src/main/scala/oathdigital/model/NegotiationTerms.scala`: the terms types moved out of `PendingProcedures.scala`, plus `NegotiationBounds`.
- `src/main/scala/oathdigital/serialization/NegotiationTermsCodec.scala`: the journal encoding of terms, shared by `DecisionAnswerCodec` and (until Task 10) the legacy event codec.
- `frontend/src/main/scala/oathdigital/frontend/NegotiationDealPanel.scala`, `NegotiationControls.scala`.
- Tests: `NegotiationFixture.scala`, `NegotiationDealSuite.scala`, `NegotiationProcedureSuite.scala`, `NegotiationParitySuite.scala`, `CoOwnedDecideSuite.scala`, `NegotiationWindowsSuite.scala`, `ActionKindSuite.scala`, frontend `NegotiationControlsSuite.scala`, `NegotiationDealPanelSuite.scala`.

Modified throughout: `Decisions.scala`, `DecisionQueries.scala`, `ProcedureWalker.scala`, `CoreOperations.scala`, `WalkerProcedureRegistry.scala`, `ProcedureRef.scala`, `RuleFallbackProtocol.scala`, `PowerRuntime.scala`, `PowerWindow.scala`, `GameProcedureProtocol.scala`, `WalkerDecisionProjector.scala`, `ActionProjectionDtos.scala`, `ActionProjectionCodec.scala`, `CommandIntents.scala`, `CommandNestedCodecs.scala`, `GameIntentMapper.scala`, `DecisionAnswerCodec.scala`, `LegalActionProjector.scala`, frontend files.

---

### Task 1: Rename `MajorActionKind` to `ActionKind`

**Files:**
- Modify (mechanical): every file under `src`, `frontend`, `shared` that names `MajorActionKind` (27 files including docs; source and tests are about 20).
- Create: `src/test/scala/oathdigital/model/ActionKindSuite.scala`

**Interfaces:**
- Produces: `oathdigital.model.ActionKind` (sealed trait, `key: String`) with the same members and keys as `MajorActionKind`, and `ActionKind.values`, `ActionKind.fromKey`. `IgnoredRuleDiagnostic.action`, `IgnoredRulesRecorded.action`, `WalkerProcedureRegistry.Entry.fallbackKind: Option[ActionKind]` and `PowerRuntime` signatures use it. Task 5 adds `ActionKind.Negotiation`.

- [ ] **Step 1: Write the wire-safety test**

```scala
package oathdigital.model

class ActionKindSuite extends munit.FunSuite {
  test("keys are the stable wire spellings") {
    assertEquals(ActionKind.values.map(_.key), Vector("travel", "search",
      "campaign", "muster", "trade", "forge", "recover", "challenge", "wake",
      "rest", "when-played", "action-boundary"))
  }

  test("fromKey inverts key for every member") {
    ActionKind.values.foreach(kind =>
      assertEquals(ActionKind.fromKey(kind.key), Some(kind)))
    assertEquals(ActionKind.fromKey("major"), None)
  }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./sbtw "testOnly oathdigital.model.ActionKindSuite"`
Expected: FAIL to compile, `not found: value ActionKind`.

- [ ] **Step 3: Rename**

```bash
git grep -l "MajorActionKind" -- src frontend shared | xargs perl -pi -e 's/\bMajorActionKind\b/ActionKind/g'
git grep -n "MajorActionKind" -- src frontend shared
```
Expected: the second command prints nothing. Frozen plans and specs under `docs/superpowers` keep the old name. `MajorActionType`, `associatedMajorAction` and `MajorActionPreviewProtocol` are different identifiers and are untouched by the `\b` match.

- [ ] **Step 4: Compile and run the affected suites**

Run: `./sbtw "Test/compile" "frontend/Test/compile" "testOnly oathdigital.model.ActionKindSuite oathdigital.serialization.GameEventWireSuite oathdigital.gameplay.walker.WalkerProcedureRegistrySuite oathdigital.gameplay.MinorActionsSuite oathdigital.gameplay.OathRulesWalkerPowerSuite"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A src frontend shared
git commit -m "refactor: rename MajorActionKind to ActionKind

The type already labels Wake, Rest, WhenPlayed and ActionBoundary, and
Negotiation, a minor action, is about to join it. Keys are unchanged, so the
wire and the journal are unaffected.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: Widen `ChooseMany` to a range

**Files:**
- Modify: `src/main/scala/oathdigital/model/Decisions.scala:254`, `src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala:74,193`, `src/main/scala/oathdigital/gameplay/actions/challenge/ChallengeRibbon.scala:64`, `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala:209`, `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala:138`, `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala:214-262`, `frontend/src/main/scala/oathdigital/frontend/WalkerSelectionDraft.scala:23`, `frontend/src/main/scala/oathdigital/frontend/WalkerSelectionPanels.scala:29`
- Modify tests: `src/test/scala/oathdigital/gameplay/walker/DecisionQuerySuite.scala:415-445`, `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala:149`, frontend `WalkerSelectionDraftSuite`, shared `ProjectionProtocolSuite` if they read `count`.

**Interfaces:**
- Produces: `DecisionQuery.ChooseMany(min: Int, max: Int, options: Vector[DecisionOption], heading: Option[String] = None)`. `wellFormed` requires `1 <= min <= max <= options.size` and rejects `min == max == options.size`. `accepts` requires a distinct offered subset whose size is in `[min, max]`. `DecisionQueryProjection.count` is removed, and a ChooseMany projects `minimum` and `maximum`.

- [ ] **Step 1: Update the tests first (they fail to compile until the model changes)**

In `DecisionQuerySuite.scala` replace the `many` helper and the two ChooseMany tests:

```scala
  private def many(min: Int, options: Vector[DecisionOption] = sites,
      max: Option[Int] = None) =
    DecisionQuery.ChooseMany(min, max.getOrElse(min), options, Some("Choose sites"))

  test("a choose-many query needs 1 <= min <= max <= options and is not forced") {
    assertEquals(wellFormed(many(1)), Right(()))
    assertEquals(wellFormed(many(2)), Right(()))
    assertEquals(wellFormed(many(1, max = Some(3))), Right(()))
    assertEquals(wellFormed(many(2, max = Some(3))), Right(()))
    assertEquals(wellFormed(many(0)),
      invalid("decision recover.choice declares no selection to make"))
    assertEquals(wellFormed(many(3, max = Some(2))), invalid(
      "decision recover.choice declares a selection range 3..2"))
    assertEquals(wellFormed(many(1, max = Some(4))), invalid("decision " +
      "recover.choice declares a maximum above its option count"))
    assertEquals(wellFormed(many(3)), invalid("decision recover.choice " +
      "declares a selection that already takes every option, leaving " +
      "nothing to decide"))
    assertEquals(wellFormed(many(1, sites :+ sites.head)),
      invalid("decision recover.choice declares duplicate options"))
  }

  test("a choose-many answer names distinct offered options within its range") {
    val q = many(2)
    assertEquals(accepts(q, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a"), siteRef("c")))), Right(()))
    assertEquals(accepts(q, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a")))), invalid(
      "decision recover.choice selects 1 options instead of 2"))
    assertEquals(accepts(q, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a"), siteRef("a")))), invalid(
      "decision recover.choice selects an option more than once"))
    assertEquals(accepts(q, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a"), siteRef("z")))), invalid(
      "decision recover.choice does not offer a selected option"))
    assertEquals(accepts(q, DecisionAnswer.ChooseOneAnswer(siteRef("a"))),
      invalid("decision recover.choice expects a multiple-choice answer"))
    val range = many(1, max = Some(3))
    assertEquals(accepts(range, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a")))), Right(()))
    assertEquals(accepts(range, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a"), siteRef("b"), siteRef("c")))), Right(()))
    assertEquals(accepts(range, DecisionAnswer.ChooseManyAnswer(Vector.empty)),
      invalid("decision recover.choice selects 0 options outside 1..3"))
  }
```

In `WalkerDecisionProjectorSuite.scala` line 149 change `DecisionQuery.ChooseMany(<n>, ` to `DecisionQuery.ChooseMany(<n>, <n>, ` (the count becomes both bounds). Read the line first: `sed -n 145,155p src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala`.

- [ ] **Step 2: Model**

In `Decisions.scala` replace the `ChooseMany` case:

```scala
  /** Pick between `min` and `max` distinct options, inclusive. Well-formed
    * only when `1 <= min <= max <= options.size`, and not the forced case
    * `min == max == options.size`, which takes every option and asks nothing.
    * An exact count is `min == max`.
    */
  final case class ChooseMany(min: Int, max: Int,
      options: Vector[DecisionOption], heading: Option[String] = None)
      extends DecisionQuery
```
Also update the doc of `ChooseManyAnswer` ("exactly the query's `count` of them" becomes "between the query's `min` and `max` of them").

- [ ] **Step 3: Validator**

In `DecisionQueries.scala`, `wellFormed`:

```scala
    case DecisionQuery.ChooseMany(min, max, options, _) =>
      val refs = options.map(_.ref)
      for {
        _ <- require(refs.distinct.size == refs.size, decisionId,
          "declares duplicate options")
        _ <- require(min >= 1, decisionId, "declares no selection to make")
        _ <- require(min <= max, decisionId,
          s"declares a selection range $min..$max")
        _ <- require(max <= refs.size, decisionId,
          "declares a maximum above its option count")
        _ <- require(!(min == max && max == refs.size), decisionId,
          "declares a selection that already takes every option, leaving " +
            "nothing to decide")
      } yield ()
```

`accepts`:

```scala
    case DecisionQuery.ChooseMany(min, max, options, _) => answer match {
      case DecisionAnswer.ChooseManyAnswer(selected) =>
        for {
          _ <- require(selected.forall(options.map(_.ref).contains),
            decisionId, "does not offer a selected option")
          _ <- require(selected.distinct.size == selected.size, decisionId,
            "selects an option more than once")
          _ <- require(selected.size >= min && selected.size <= max,
            decisionId,
            if (min == max) s"selects ${selected.size} options instead of $min"
            else s"selects ${selected.size} options outside $min..$max")
        } yield ()
      case _ =>
        reject(decisionId, "expects a multiple-choice answer")
    }
```

- [ ] **Step 4: Call sites and projection**

`ChallengeRibbon.scala:64`: `DecisionQuery.ChooseMany(remaining, ...` becomes `DecisionQuery.ChooseMany(remaining, remaining, ...`. Read the surrounding lines first (`sed -n 60,70p`).

`WalkerDecisionProjector.scala` `queryProjection`:

```scala
      case DecisionQuery.ChooseMany(min, max, options, heading) =>
        described(options).map(DecisionQueryProjection("choose-many", _,
          heading = heading, minimum = Some(min), maximum = Some(max)))
```

`ActionProjectionDtos.scala` `DecisionQueryProjection`: delete the `count: Option[Int] = None,` line. `ActionProjectionCodec.scala`: delete `"count" -> intOption(value.count),` from `encodeDecisionQuery`, remove `"count"` from the `exact(...)` set and delete the `count <- optionalInt(value, "count", path)` line, and drop `count` from the `DecisionQueryProjection(form, options, sections, heading, confirmLabel, slots, total, count, minimum, maximum)` construction (now `..., slots, total, minimum, maximum)`).

- [ ] **Step 5: Frontend**

`WalkerSelectionDraft.scala`: replace `private def count` and its two users:

```scala
  private def minimum: Int = query.minimum.getOrElse(0)
  private def maximum: Int = query.maximum.getOrElse(0)

  /** Adds an unselected option while fewer than `maximum` are selected, and
    * removes a selected one; adding past the maximum changes nothing.
    */
  def toggle(item: String): WalkerChooseManyDraft =
    if (selected.contains(item)) copy(selected = selected.filterNot(_ == item))
    else if (selected.size < maximum &&
      query.options.exists(WalkerPartitionDraft.itemId(_) == item))
      copy(selected = selected :+ item)
    else this

  def canConfirm: Boolean = selected.size >= minimum && selected.size <= maximum
```
`WalkerSelectionPanels.scala:29`: replace `s"Choose ${query.count.getOrElse(0)}."` with

```scala
      if (query.minimum == query.maximum) s"Choose ${query.minimum.getOrElse(0)}."
      else s"Choose ${query.minimum.getOrElse(0)} to ${query.maximum.getOrElse(0)}."
```
Migrate the test sites that read or build a ChooseMany `count`:
- `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala:127`: `count = Some(2)` becomes `minimum = Some(2), maximum = Some(2)` (and the test title's "counts" stays accurate).
- `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala:149-155`: `DecisionQuery.ChooseMany(2,` becomes `DecisionQuery.ChooseMany(2, 2,`, and `assertEquals(query.count, Some(2))` becomes `assertEquals((query.minimum, query.maximum), (Some(2), Some(2)))`.
- `frontend/src/test/scala/oathdigital/frontend/WalkerSelectionDraftSuite.scala:8` and `WalkerSelectionPanelsSuite.scala:9`: the `DecisionQueryState("choose-many", ..., count = Some(n))` fixtures become `minimum = Some(n), maximum = Some(n)`.
Then confirm nothing else uses it: `git grep -n "count = Some" -- frontend/src shared/src src | grep -v "pool ="` must show no ChooseMany fixture.

- [ ] **Step 6: Run**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.DecisionQuerySuite oathdigital.application.WalkerDecisionProjectorSuite oathdigital.gameplay.ChallengeProcedureSuite oathdigital.protocol.ProjectionProtocolSuite" "frontend/testOnly oathdigital.frontend.WalkerSelectionDraftSuite oathdigital.frontend.WalkerSelectionPanelsSuite"`
Expected: PASS. Also add one frontend assertion in `WalkerSelectionDraftSuite` that a `minimum = 1, maximum = 3` query confirms with one or three selections and not with zero.

- [ ] **Step 7: Commit**

```bash
git add -A src shared frontend
git commit -m "feat(walker): widen ChooseMany to a min..max range

An exact count is min == max, so Challenge's tie question is unchanged.
Negotiation's negotiator choice needs one or more.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: Co-owned decisions and the open-decision park API

This is the spike the spec puts first. It proves a `Repeat` around a `Branch` around a co-owned `Decide` parks, resumes on answers from different owners across passes, exits on its guard, and rejects an outsider. If a test here fails for a reason that is not a typo, **stop**: fix the walker if the fix is local and comes with tests, and otherwise bring the failure back for a decision (spec, Verification).

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala:563` (`Decide`), `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala:217-233,584-610`, `src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala:183`, `src/main/scala/oathdigital/model/PendingTree.scala` (doc only)
- Modify tests: `src/test/scala/oathdigital/gameplay/walker/DecisionQuerySuite.scala:57`, `src/test/scala/oathdigital/gameplay/DecisionQueriesSuite.scala:19,44`
- Create: `src/test/scala/oathdigital/gameplay/CoOwnedDecideSuite.scala`

**Interfaces:**
- Produces:
  - `Decide(decisionId: String, owner: PlayerId, query: DecisionQuery, window: Option[PowerWindow] = None, coOwners: Vector[PlayerId] = Vector.empty)` with `def owners: Vector[PlayerId]` (owner first, distinct).
  - `ProcedureWalker.openDecisions(state: ReadyGame, action: Operation, pending: PendingTree, powers: WalkerPowers): Vector[Decide]`.
  - `ProcedureWalker.awaitedPlayers(state: ReadyGame, action: Operation, pending: PendingTree, powers: WalkerPowers): Set[PlayerId]`.
  - `DecisionQueries.accepts(decisionId: String, query: DecisionQuery, answer: DecisionAnswer, by: PlayerId): Either[OathViolation, Unit]`.
  - `parkedDecide` and `awaitedPlayer` keep their signatures (deviation 2).

- [ ] **Step 1: Write the failing spike suite**

```scala
package oathdigital.gameplay

import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.TestGameFixtures._

/** A `Repeat` around a `Branch` around a decision two players may answer: the
  * shape Negotiation's deal loop takes, exercised with a synthetic tree.
  */
class CoOwnedDecideSuite extends munit.FunSuite {
  private val owner = playerId
  private val guest = PlayerId("player-blue")
  private val outsider = PlayerId("player-green")
  private val noPowers = WalkerPowers.empty
  private val go = DecisionOptionRef.Button("go")
  private val stop = DecisionOptionRef.Button("stop")
  private val ready: ReadyGame = ReadyGames.of(game)

  private val shared = Decide("shared.deal", owner,
    DecisionQuery.ChooseOne(Vector(DecisionOption.Button(go, "Go"),
      DecisionOption.Button(stop, "Stop"))), coOwners = Vector(guest))

  /** Re-parks the shared decision until someone answers stop. The guard and
    * the branch read only the answered list, as the walk rule requires.
    */
  private val loop: Operation = Sequence(Repeat(
    (_, pending) => !pending.answered.lastOption.map(_.answer)
      .contains(ChooseOneAnswer(stop)),
    Branch((_, _) => Vector(shared))))

  private def answer(by: PlayerId, ref: DecisionOptionRef.Button) =
    Answered(shared.decisionId, ChooseOneAnswer(ref), by)

  private def parkedAt(outcome: Either[OathViolation, WalkerOutcome]): PendingTree =
    outcome match {
      case Right(WalkerOutcome.Parked(pending, _)) => pending
      case other => fail(s"expected a park, got $other")
    }

  test("owners lists the owner first and drops a repeated owner") {
    assertEquals(shared.owners, Vector(owner, guest))
    assertEquals(shared.copy(coOwners = Vector(owner, guest)).owners,
      Vector(owner, guest))
  }

  test("the park names every owner and one open decision") {
    val pending = parkedAt(ProcedureWalker.advance(ready, loop, None, noPowers))
    assertEquals(pending.at, Vector("0", "0", "0"))
    assertEquals(ProcedureWalker.openDecisions(ready, loop, pending, noPowers),
      Vector(shared))
    assertEquals(ProcedureWalker.awaitedPlayers(ready, loop, pending, noPowers),
      Set(owner, guest))
    assertEquals(ProcedureWalker.awaitedPlayer(ready, loop, pending, noPowers),
      Some(owner))
    assertEquals(ProcedureWalker.parkedDecide(ready, loop, pending, noPowers),
      Some(shared))
  }

  test("a plain decision is awaited by its owner alone") {
    val plain = Sequence(shared.copy(coOwners = Vector.empty))
    val pending = parkedAt(ProcedureWalker.advance(ready, plain, None, noPowers))
    assertEquals(ProcedureWalker.awaitedPlayers(ready, plain, pending, noPowers),
      Set(owner))
  }

  test("owners answer in any order, the loop re-parks, and a stop finishes it") {
    val first = parkedAt(ProcedureWalker.advance(ready, loop, None, noPowers))
    val second = parkedAt(ProcedureWalker.resolve(ready, loop, first,
      answer(guest, go), noPowers))
    val third = parkedAt(ProcedureWalker.resolve(ready, loop, second,
      answer(owner, go), noPowers))
    assertEquals(third.at, Vector("0", "0", "0"))
    assertEquals(third.answered.map(_.by), Vector(guest, owner))
    ProcedureWalker.resolve(ready, loop, third, answer(guest, stop), noPowers) match {
      case Right(WalkerOutcome.Finished(_, events)) =>
        assertEquals(events.size, 1)
      case other => fail(s"expected the loop to finish, got $other")
    }
  }

  test("a player who is neither owner nor co-owner is rejected") {
    val pending = parkedAt(ProcedureWalker.advance(ready, loop, None, noPowers))
    assertEquals(ProcedureWalker.resolve(ready, loop, pending,
      answer(outsider, go), noPowers), Left(OathViolation.WrongPlayer(owner, outsider)))
  }

  test("an answer for a decision that is not open is rejected") {
    val pending = parkedAt(ProcedureWalker.advance(ready, loop, None, noPowers))
    val stale = Answered("other.decision", ChooseOneAnswer(go), guest)
    assert(ProcedureWalker.resolve(ready, loop, pending, stale, noPowers).isLeft)
  }
}
```

- [ ] **Step 2: Run to confirm it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.CoOwnedDecideSuite"`
Expected: FAIL to compile (`coOwners`, `owners`, `openDecisions`, `awaitedPlayers` do not exist).

- [ ] **Step 3: `Decide`**

In `CoreOperations.scala` replace the `Decide` case class:

```scala
final case class Decide(decisionId: String, owner: PlayerId,
    query: DecisionQuery,
    override val window: Option[PowerWindow] = None,
    coOwners: Vector[PlayerId] = Vector.empty)
    extends PrimitiveOperation {
  /** Everyone who may answer: `owner` first, then the co-owners, each once.
    * `owner` stays the primary owner, the addressee of continuations.
    */
  def owners: Vector[PlayerId] = (owner +: coOwners).distinct
}
```
and extend its doc comment: "`coOwners` are further players who may answer the same decision. One answer from any owner resolves it, so a `Repeat` around it re-asks it after each answer."

- [ ] **Step 4: Walker API**

In `ProcedureWalker.scala` replace `parkedDecide` and `awaitedPlayer` (lines 217-233) with:

```scala
  /** Every decision open at the park: one for a plain or co-owned `Decide`,
    * none for a `Roll` or a position that does not resolve. A future
    * `Simultaneous` node would return one per unanswered child, and nothing
    * else here would change.
    */
  def openDecisions(state: ReadyGame, action: Operation,
      pending: PendingTree, powers: WalkerPowers): Vector[Decide] =
    WalkerPowerGather.leafAt(state, action, pending, powers).collect {
      case decide: Decide => decide
    }.toVector

  /** The single-decision view of [[openDecisions]]. */
  def parkedDecide(state: ReadyGame, action: Operation,
      pending: PendingTree, powers: WalkerPowers): Option[Decide] =
    openDecisions(state, action, pending, powers).headOption

  /** Who a parked position waits on, as one player: the primary owner of a
    * parked `Decide`, or the active player for a parked `Roll`. Never stored:
    * a power that changes an owner changes this answer on the next command.
    */
  def awaitedPlayer(state: ReadyGame, action: Operation, pending: PendingTree,
      powers: WalkerPowers): Option[PlayerId] =
    parkedDecide(state, action, pending, powers).map(_.owner).orElse(
      parkedRoll(state, action, pending, powers).map(_ =>
        state.game.current.turn.activePlayer))

  /** Everyone who may answer the parked position: the owners and co-owners of
    * its open decisions, or the active player for a parked `Roll`.
    */
  def awaitedPlayers(state: ReadyGame, action: Operation,
      pending: PendingTree, powers: WalkerPowers): Set[PlayerId] = {
    val open = openDecisions(state, action, pending, powers)
    if (open.nonEmpty) open.flatMap(_.owners).toSet
    else parkedRoll(state, action, pending, powers)
      .map(_ => Set(state.game.current.turn.activePlayer)).getOrElse(Set.empty)
  }
```
In `answerDecide` (line ~599) replace the owner check and the `accepts` call:

```scala
      _ <- Either.cond(decide.owners.contains(answer.by), (),
        OathViolation.WrongPlayer(decide.owner, answer.by))
      _ <- DecisionQueries.wellFormed(decide.decisionId, decide.query)
      _ <- DecisionQueries.accepts(decide.decisionId, decide.query,
        answer.answer, answer.by)
```
Update its doc: "the answer's submitter must be the node's owner or a co-owner". In `PendingTree.scala` change the `Answered` doc of `by` to "the authorized requester: the parked `Decide`'s owner or one of its co-owners".

- [ ] **Step 5: `accepts` takes the answerer**

In `DecisionQueries.scala` add `PlayerId` to the model import and change the signature:

```scala
  def accepts(decisionId: String, query: DecisionQuery,
      answer: DecisionAnswer, by: PlayerId): Either[OathViolation, Unit] =
    query match {
```
No branch uses `by` yet (Task 4 adds the first). Add to the doc: "`by` is the player who answered. Only the negotiation shape reads it, because only there does legality depend on who is answering."

Fix the test call sites:
- `DecisionQuerySuite.scala:57-58`: `DecisionQueries.accepts(decisionId, query, answer, anyone)` with `private val anyone = PlayerId("player-red")` (add `PlayerId` to the import), and the direct calls at lines 385, 404, 407, 410 gain `, anyone`.
- `DecisionQueriesSuite.scala:19,44`: add `, PlayerId("player-red")` (add the import if missing).

- [ ] **Step 6: Run**

Run: `./sbtw "testOnly oathdigital.gameplay.CoOwnedDecideSuite oathdigital.gameplay.walker.DecisionQuerySuite oathdigital.gameplay.DecisionQueriesSuite oathdigital.gameplay.ProcedureWalkerSuite oathdigital.gameplay.ChallengeProcedureSuite oathdigital.gameplay.RecoverProcedureSuite"`
Expected: PASS. Then `wc -l src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` stays under 800.

- [ ] **Step 7: Commit**

```bash
git add -A src
git commit -m "feat(walker): co-owned decisions and an open-decision park API

Decide gains coOwners, and answerDecide accepts an owner or a co-owner. The
park exposes openDecisions and awaitedPlayers, which a Simultaneous node can
extend without reshaping them. accepts now receives the answerer.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 4: The negotiation decision vocabulary and its codecs

**Files:**
- Create: `src/main/scala/oathdigital/model/NegotiationTerms.scala`, `src/main/scala/oathdigital/serialization/NegotiationTermsCodec.scala`, `src/test/scala/oathdigital/application/GameIntentMapperNegotiationSuite.scala`
- Modify: `src/main/scala/oathdigital/model/PendingProcedures.scala:231-262` (move the terms types out), `src/main/scala/oathdigital/model/Decisions.scala`, `src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala`, `src/main/scala/oathdigital/serialization/DecisionAnswerCodec.scala`, `src/main/scala/oathdigital/serialization/GameEventJsonSupport.scala:14-58`, `shared/src/main/scala/oathdigital/protocol/CommandIntents.scala:120-141`, `shared/src/main/scala/oathdigital/protocol/CommandNestedCodecs.scala:64-121`, `src/main/scala/oathdigital/application/GameIntentMapper.scala:139-155`
- Modify tests: `src/test/scala/oathdigital/gameplay/walker/DecisionQuerySuite.scala`, `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`, `shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala:47-49`

**Interfaces:**
- Consumes: `DecisionQueries.accepts(..., by)` from Task 3.
- Produces:
  - `NegotiationBounds(recipients: Vector[PlayerId], maxFavor: Int, relics: Vector[RelicId], disclosures: Vector[NegotiationDisclosureRef])`.
  - `DecisionQuery.Negotiate(participants: Vector[PlayerId], terms: Map[PlayerId, NegotiationTerms], accepted: Set[PlayerId], bounds: Map[PlayerId, NegotiationBounds], acceptors: Set[PlayerId], heading: Option[String] = None)`.
  - `DecisionAnswer.ProposeTerms(terms: NegotiationTerms)`, `DecisionAnswer.AcceptDeal`, `DecisionAnswer.DeclineDeal` (case objects for the last two).
  - `DecisionAnswerWire.ProposeTermsWire(terms: protocol.NegotiationTerms)`, `DecisionAnswerWire.AcceptDealWire`, `DecisionAnswerWire.DeclineDealWire`; journal tags `propose-terms`, `accept-deal`, `decline-deal`; wire kinds the same.
  - `private[serialization] object NegotiationTermsCodec { def encode(terms): ujson.Value; def decode(value, path): Either[WireError, NegotiationTerms] }`.

- [ ] **Step 1: Write the failing tests**

Append to `DecisionQuerySuite.scala` (add `NegotiationBounds, NegotiationDisclosure, NegotiationDisclosureRef, NegotiationTerms, NegotiationTransfer, PlayerId` to its model import):

```scala
  // Negotiate: legality depends on who answers.

  private val red = PlayerId("red")
  private val blue = PlayerId("blue")
  private val green = PlayerId("green")
  private val heldRelic = RelicId("relic-1")
  private val adviser = NegotiationDisclosureRef.Adviser(red, DenizenId("d1"))
  private val redBounds = NegotiationBounds(Vector(blue), 5, Vector(heldRelic),
    Vector(adviser))
  private val blueBounds = NegotiationBounds(Vector(red), 2, Vector.empty,
    Vector.empty)

  private def deal(accepted: Set[PlayerId] = Set.empty,
      acceptors: Set[PlayerId] = Set.empty) = DecisionQuery.Negotiate(
    Vector(red, blue), Map(red -> NegotiationTerms(), blue -> NegotiationTerms()),
    accepted, Map(red -> redBounds, blue -> blueBounds), acceptors,
    Some("Negotiation"))

  private def acceptsBy(by: PlayerId, answer: DecisionAnswer,
      query: DecisionQuery = deal(acceptors = Set(red, blue))) =
    DecisionQueries.accepts(decisionId, query, answer, by)

  test("a negotiate query needs two distinct participants and consistent maps") {
    assertEquals(wellFormed(deal()), Right(()))
    assertEquals(wellFormed(deal().copy(participants = Vector(red))),
      invalid("decision recover.choice declares fewer than two distinct participants"))
    assertEquals(wellFormed(deal().copy(participants = Vector(red, red))),
      invalid("decision recover.choice declares fewer than two distinct participants"))
    assertEquals(wellFormed(deal().copy(bounds = Map(red -> redBounds))),
      invalid("decision recover.choice declares bounds for players outside the deal"))
    assertEquals(wellFormed(deal().copy(terms = Map(red -> NegotiationTerms()))),
      invalid("decision recover.choice declares terms for players outside the deal"))
    assertEquals(wellFormed(deal(accepted = Set(green))),
      invalid("decision recover.choice declares an acceptance from outside the deal"))
  }

  test("proposed terms must sit inside the author's bounds") {
    val fine = NegotiationTerms(
      Vector(NegotiationTransfer(blue, 3, Vector(heldRelic))),
      Vector(NegotiationDisclosure(blue, adviser)))
    assertEquals(acceptsBy(red, DecisionAnswer.ProposeTerms(fine)), Right(()))
    assertEquals(acceptsBy(red, DecisionAnswer.ProposeTerms(NegotiationTerms())),
      Right(()))
    assertEquals(acceptsBy(red, DecisionAnswer.ProposeTerms(NegotiationTerms(
      Vector(NegotiationTransfer(blue, 6, Vector.empty))))),
      invalid("decision recover.choice offers more than 5 favor"))
    assertEquals(acceptsBy(blue, DecisionAnswer.ProposeTerms(NegotiationTerms(
      Vector(NegotiationTransfer(red, 1, Vector(heldRelic)))))),
      invalid("decision recover.choice offers a relic its author does not hold"))
    assertEquals(acceptsBy(red, DecisionAnswer.ProposeTerms(NegotiationTerms(
      Vector(NegotiationTransfer(green, 1, Vector.empty))))),
      invalid("decision recover.choice offers terms to a player outside the deal"))
    assertEquals(acceptsBy(red, DecisionAnswer.ProposeTerms(NegotiationTerms(
      Vector.empty, Vector(NegotiationDisclosure(blue,
        NegotiationDisclosureRef.HeldRelic(red, heldRelic)))))),
      invalid("decision recover.choice promises a disclosure its author cannot make"))
    assertEquals(acceptsBy(green, DecisionAnswer.ProposeTerms(fine)),
      invalid("decision recover.choice is not open to this player"))
  }

  test("only an acceptor accepts and any participant declines") {
    assertEquals(acceptsBy(red, DecisionAnswer.AcceptDeal), Right(()))
    assertEquals(acceptsBy(red, DecisionAnswer.AcceptDeal, deal()),
      invalid("decision recover.choice does not let this player accept now"))
    assertEquals(acceptsBy(green, DecisionAnswer.AcceptDeal),
      invalid("decision recover.choice does not let this player accept now"))
    assertEquals(acceptsBy(blue, DecisionAnswer.DeclineDeal, deal()), Right(()))
    assertEquals(acceptsBy(green, DecisionAnswer.DeclineDeal, deal()),
      invalid("decision recover.choice is not open to this player"))
  }

  test("negotiation answers and queries only meet each other") {
    assertEquals(acceptsBy(red, DecisionAnswer.ChooseOneAnswer(continueRef)),
      invalid("decision recover.choice expects a negotiation answer"))
    assertEquals(acceptsBy(red, DecisionAnswer.AcceptDeal, chooseOne),
      invalid("decision recover.choice expects a single-choice answer"))
  }
```
(`acceptsBy(red, answer, chooseOne)` passes the query as the third argument.)

Append to `GameEventWireSuite.scala` (`ChoicePayload` and `WalkerStepRecorded` are already imported):

```scala
  test("walker choice steps round trip the negotiation answers") {
    val red = PlayerId("red"); val blue = PlayerId("blue")
    val terms = NegotiationTerms(
      Vector(NegotiationTransfer(blue, 2, Vector(RelicId("r1")))),
      Vector(
        NegotiationDisclosure(blue,
          NegotiationDisclosureRef.Adviser(red, VisionId("v1"))),
        NegotiationDisclosure(blue,
          NegotiationDisclosureRef.HeldRelic(red, RelicId("r2"))),
        NegotiationDisclosure(blue,
          NegotiationDisclosureRef.SiteRelic(SiteId("s1"), RelicId("r3")))))
    Vector[DecisionAnswer](DecisionAnswer.ProposeTerms(terms),
      DecisionAnswer.AcceptDeal, DecisionAnswer.DeclineDeal).zipWithIndex
      .foreach { case (answer, index) =>
        val event = WalkerStepRecorded("0.0.0",
          ChoicePayload("negotiation.deal", answer, red), Vector.empty,
          Vector.empty)
        val encoded = GameEventWire.encodeEvent("walker", catalog.ref, index,
          event).toOption.get
        assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
      }
  }
```

In `CommandProtocolSuite.scala` add to `examples` (after the `challenge.amount` line, mind the comma on the previous line):

```scala
    ResolveWalker("negotiation.deal", DecisionAnswerWire.ProposeTermsWire(
      NegotiationTerms(Vector(NegotiationTransfer("blue", 2, Vector("r1"))),
        Vector(NegotiationDisclosure("blue",
          NegotiationInformation.HeldRelic("red", "r2")))))),
    ResolveWalker("negotiation.deal", DecisionAnswerWire.AcceptDealWire),
    ResolveWalker("negotiation.deal", DecisionAnswerWire.DeclineDealWire)
```

Create `GameIntentMapperNegotiationSuite.scala`:

```scala
package oathdigital.application

import oathdigital.model._
import oathdigital.protocol.{GameIntent => Intent, DecisionAnswerWire => Wire}

class GameIntentMapperNegotiationSuite extends munit.FunSuite {
  private val red = PlayerId("red")

  private def bound(answer: Wire) =
    GameIntentMapper.bind(red, Intent.ResolveWalker("negotiation.deal", answer))

  test("a proposed terms wire answer maps to the engine's terms") {
    val wire = Wire.ProposeTermsWire(oathdigital.protocol.NegotiationTerms(
      Vector(oathdigital.protocol.NegotiationTransfer("blue", 2, Vector("r1"))),
      Vector(oathdigital.protocol.NegotiationDisclosure("blue",
        oathdigital.protocol.NegotiationInformation.HeldRelic("red", "r2")))))
    assertEquals(bound(wire), Right(GameCommand.ResolveWalker(red,
      TreeDecision("negotiation.deal", DecisionAnswer.ProposeTerms(
        NegotiationTerms(Vector(NegotiationTransfer(PlayerId("blue"), 2,
          Vector(RelicId("r1")))), Vector(NegotiationDisclosure(PlayerId("blue"),
          NegotiationDisclosureRef.HeldRelic(red, RelicId("r2")))))))))
  }

  test("accept and decline map to their engine answers") {
    assertEquals(bound(Wire.AcceptDealWire), Right(GameCommand.ResolveWalker(red,
      TreeDecision("negotiation.deal", DecisionAnswer.AcceptDeal))))
    assertEquals(bound(Wire.DeclineDealWire), Right(GameCommand.ResolveWalker(red,
      TreeDecision("negotiation.deal", DecisionAnswer.DeclineDeal))))
  }
}
```

- [ ] **Step 2: Run to confirm they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.DecisionQuerySuite oathdigital.serialization.GameEventWireSuite oathdigital.protocol.CommandProtocolSuite oathdigital.application.GameIntentMapperNegotiationSuite"`
Expected: FAIL to compile (`NegotiationBounds`, `Negotiate`, `ProposeTerms`, `ProposeTermsWire` do not exist).

- [ ] **Step 3: Move the terms types and add the bounds**

```bash
python3 - <<'PY'
p = 'src/main/scala/oathdigital/model/PendingProcedures.scala'
s = open(p).read()
i = s.index('final case class NegotiationTransfer(')
moved = s[i:]
open(p, 'w').write(s[:i].rstrip() + "\n")
open('src/main/scala/oathdigital/model/NegotiationTerms.scala', 'w').write(
    "package oathdigital.model\n\n" + moved)
PY
cat >> src/main/scala/oathdigital/model/NegotiationTerms.scala <<'SCALA'

/** What one author may still put into a deal, computed from live state each
  * time the deal is asked: the other participants they may offer to, the
  * favor they hold, the relics they hold, and the information they may
  * currently promise to disclose (their facedown advisers and held relics,
  * and site relics they know that are still at their site).
  */
final case class NegotiationBounds(
    recipients: Vector[PlayerId],
    maxFavor: Int,
    relics: Vector[RelicId],
    disclosures: Vector[NegotiationDisclosureRef]
) {
  require(maxFavor >= 0, "negotiation favor bound must be non-negative")
}
SCALA
tail -5 src/main/scala/oathdigital/model/PendingProcedures.scala
```
`PendingProcedures.scala` must still end with the closing brace of `object PendingProcedure`.

- [ ] **Step 4: Query and answers**

In `Decisions.scala`, inside `object DecisionQuery` after `Distribute`:

```scala
  /** A negotiation deal, as a snapshot the action rebuilds from live state and
    * the recorded answers on every command: who is in it, everyone's current
    * terms, who has accepted, what each author may still offer, and who may
    * accept now. Legality depends on WHO answers (each author has their own
    * bounds), which is why `DecisionQueries.accepts` receives the answerer.
    * Any participant may answer it: the deal is a `Decide` with co-owners.
    */
  final case class Negotiate(participants: Vector[PlayerId],
      terms: Map[PlayerId, NegotiationTerms], accepted: Set[PlayerId],
      bounds: Map[PlayerId, NegotiationBounds], acceptors: Set[PlayerId],
      heading: Option[String] = None) extends DecisionQuery
```
and inside `object DecisionAnswer` after `DistributeAnswer`:

```scala
  /** Answer to a [[DecisionQuery.Negotiate]]: this author's complete terms,
    * replacing their earlier ones and clearing every acceptance.
    */
  final case class ProposeTerms(terms: NegotiationTerms) extends DecisionAnswer

  /** Answer to a [[DecisionQuery.Negotiate]]: accepts the current deal. */
  case object AcceptDeal extends DecisionAnswer

  /** Answer to a [[DecisionQuery.Negotiate]]: declines, which ends it. */
  case object DeclineDeal extends DecisionAnswer
```

- [ ] **Step 5: Validator**

In `DecisionQueries.scala`, add `NegotiationBounds, NegotiationTerms, PlayerId` to the model import. In `wellFormed` add:

```scala
    case DecisionQuery.Negotiate(participants, terms, accepted, bounds,
        acceptors, _) =>
      val members = participants.toSet
      for {
        _ <- require(participants.size >= 2 &&
          participants.distinct.size == participants.size, decisionId,
          "declares fewer than two distinct participants")
        _ <- require(bounds.keySet == members, decisionId,
          "declares bounds for players outside the deal")
        _ <- require(terms.keySet == members, decisionId,
          "declares terms for players outside the deal")
        _ <- require(accepted.subsetOf(members) && acceptors.subsetOf(members),
          decisionId, "declares an acceptance from outside the deal")
      } yield ()
```
and in `accepts` (before the closing brace of the `query match`):

```scala
    case DecisionQuery.Negotiate(participants, _, _, bounds, acceptors, _) =>
      answer match {
        case DecisionAnswer.ProposeTerms(terms) =>
          acceptsTerms(decisionId, participants, bounds, by, terms)
        case DecisionAnswer.AcceptDeal =>
          require(acceptors.contains(by), decisionId,
            "does not let this player accept now")
        case DecisionAnswer.DeclineDeal =>
          require(participants.contains(by), decisionId,
            "is not open to this player")
        case _ =>
          reject(decisionId, "expects a negotiation answer")
      }
```
and a helper next to `acceptsPartition`:

```scala
  private def acceptsTerms(decisionId: String, participants: Vector[PlayerId],
      bounds: Map[PlayerId, NegotiationBounds], by: PlayerId,
      terms: NegotiationTerms): Either[OathViolation, Unit] =
    bounds.get(by).filter(_ => participants.contains(by)) match {
      case None => reject(decisionId, "is not open to this player")
      case Some(own) =>
        val recipients = terms.transfers.map(_.recipient) ++
          terms.disclosures.map(_.recipient)
        val relics = terms.transfers.flatMap(_.relics)
        for {
          _ <- require(recipients.forall(own.recipients.contains), decisionId,
            "offers terms to a player outside the deal")
          _ <- require(terms.transfers.map(_.favor.toLong).sum <=
            own.maxFavor.toLong, decisionId,
            s"offers more than ${own.maxFavor} favor")
          _ <- require(relics.distinct.size == relics.size &&
            relics.forall(own.relics.contains), decisionId,
            "offers a relic its author does not hold")
          _ <- require(terms.disclosures.map(_.information)
            .forall(own.disclosures.contains), decisionId,
            "promises a disclosure its author cannot make")
        } yield ()
    }
```
Check `wc -l src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala` stays under 800.

- [ ] **Step 6: Journal codec**

Create `src/main/scala/oathdigital/serialization/NegotiationTermsCodec.scala`, the legacy encoding lifted out of `GameEventJsonSupport` so the answer codec can reach it:

```scala
package oathdigital.serialization

import scala.util.control.NonFatal
import oathdigital.model._
import oathdigital.serialization.WireError.InvalidValue

/** Journal wire form of negotiation terms, shared by the walker's answer
  * codec and (until it is deleted) the legacy Negotiation event codec, so the
  * two spell terms identically.
  */
private[serialization] object NegotiationTermsCodec {
  def encode(terms: NegotiationTerms): ujson.Value = ujson.Obj(
    "transfers" -> ujson.Arr.from(terms.transfers.map(transfer => ujson.Obj(
      "recipientPlayerId" -> transfer.recipient.value,
      "favor" -> transfer.favor,
      "relicIds" -> ujson.Arr.from(transfer.relics.map(r => ujson.Str(r.value)))))),
    "disclosures" -> ujson.Arr.from(terms.disclosures.map { disclosure =>
      val information = disclosure.information match {
        case NegotiationDisclosureRef.Adviser(owner, card) => ujson.Obj(
          "kind" -> "adviser", "ownerPlayerId" -> owner.value,
          "card" -> encodeWorldCard(card))
        case NegotiationDisclosureRef.HeldRelic(owner, relic) => ujson.Obj(
          "kind" -> "held-relic", "ownerPlayerId" -> owner.value,
          "relicId" -> relic.value)
        case NegotiationDisclosureRef.SiteRelic(site, relic) => ujson.Obj(
          "kind" -> "site-relic", "siteId" -> site.value,
          "relicId" -> relic.value)
      }
      ujson.Obj("recipientPlayerId" -> disclosure.recipient.value,
        "information" -> information)
    }))

  def decode(value: ujson.Value, path: String)
      : Either[WireError, NegotiationTerms] = try {
    for {
      transfers <- traverse(value("transfers").arr.toVector) { row =>
        val raw = row("favor").num
        Either.cond(raw.isValidInt, raw.toInt, InvalidValue(
          s"$path.transfers.favor", s"favor '$raw' is not an integer")).map(
          favor => NegotiationTransfer(PlayerId(row("recipientPlayerId").str),
            favor, row("relicIds").arr.toVector.map(v => RelicId(v.str))))
      }
      disclosures <- traverse(value("disclosures").arr.toVector) { row =>
        val info = row("information")
        val decoded: Either[WireError, NegotiationDisclosureRef] =
          info("kind").str match {
            case "adviser" => decodeWorldCard(info("card"),
              s"$path.disclosures.card").map(card =>
              NegotiationDisclosureRef.Adviser(
                PlayerId(info("ownerPlayerId").str), card))
            case "held-relic" => Right(NegotiationDisclosureRef.HeldRelic(
              PlayerId(info("ownerPlayerId").str), RelicId(info("relicId").str)))
            case "site-relic" => Right(NegotiationDisclosureRef.SiteRelic(
              SiteId(info("siteId").str), RelicId(info("relicId").str)))
            case other => Left(InvalidValue(s"$path.disclosures.kind",
              s"unknown disclosure kind '$other'"))
          }
        decoded.map(NegotiationDisclosure(
          PlayerId(row("recipientPlayerId").str), _))
      }
    } yield NegotiationTerms(transfers, disclosures)
  } catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid Negotiation terms"))) }

  private def encodeWorldCard(id: WorldCardId): ujson.Value = id match {
    case value: DenizenId => ujson.Obj("kind" -> "denizen", "id" -> value.value)
    case value: VisionId => ujson.Obj("kind" -> "vision", "id" -> value.value)
  }

  private def decodeWorldCard(value: ujson.Value, path: String)
      : Either[WireError, WorldCardId] = value("kind").str match {
    case "denizen" => Right(DenizenId(value("id").str))
    case "vision" => Right(VisionId(value("id").str))
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown world card kind '$other'"))
  }

  private def traverse[A, B](values: Vector[A])(
      f: A => Either[WireError, B]): Either[WireError, Vector[B]] =
    values.foldLeft[Either[WireError, Vector[B]]](Right(Vector.empty)) {
      case (Right(acc), value) => f(value).map(acc :+ _)
      case (failure @ Left(_), _) => failure
    }
}
```
In `GameEventJsonSupport.scala` replace both bodies (lines 14-58) so the legacy events keep identical bytes and nothing is duplicated:

```scala
  protected final def encodeNegotiationTerms(terms: NegotiationTerms): ujson.Value =
    NegotiationTermsCodec.encode(terms)

  protected final def decodeNegotiationTerms(value: ujson.Value,
      path: String): Either[WireError, NegotiationTerms] =
    NegotiationTermsCodec.decode(value, path)
```
In `DecisionAnswerCodec.scala` add the tags and cases:

```scala
  private val ProposeTermsTag = "propose-terms"
  private val AcceptDealTag = "accept-deal"
  private val DeclineDealTag = "decline-deal"
```
encode:
```scala
    case DecisionAnswer.ProposeTerms(terms) => ujson.Obj(
      "kind" -> ProposeTermsTag, "terms" -> NegotiationTermsCodec.encode(terms))
    case DecisionAnswer.AcceptDeal => ujson.Obj("kind" -> AcceptDealTag)
    case DecisionAnswer.DeclineDeal => ujson.Obj("kind" -> DeclineDealTag)
```
decode (before `case other`):
```scala
      case ProposeTermsTag => NegotiationTermsCodec.decode(value("terms"),
        s"$path.terms").map(DecisionAnswer.ProposeTerms)
      case AcceptDealTag => Right(DecisionAnswer.AcceptDeal)
      case DeclineDealTag => Right(DecisionAnswer.DeclineDeal)
```

- [ ] **Step 7: Client wire and mapper**

In `CommandIntents.scala`, inside `object DecisionAnswerWire`:

```scala
  /** Answers a negotiation decision with the sender's complete terms. */
  final case class ProposeTermsWire(terms: NegotiationTerms)
      extends DecisionAnswerWire

  /** Accepts the current deal. */
  case object AcceptDealWire extends DecisionAnswerWire

  /** Declines, which ends the negotiation. */
  case object DeclineDealWire extends DecisionAnswerWire
```
In `CommandNestedCodecs.scala` `encodeDecisionAnswerWire` add:

```scala
    case DecisionAnswerWire.ProposeTermsWire(terms) =>
      ujson.Obj("kind" -> "propose-terms", "terms" -> encodeNegotiation(terms))
    case DecisionAnswerWire.AcceptDealWire => ujson.Obj("kind" -> "accept-deal")
    case DecisionAnswerWire.DeclineDealWire => ujson.Obj("kind" -> "decline-deal")
```
and `decodeDecisionAnswerWire` (before the `case kind =>` line):

```scala
      case "propose-terms" => for {
        _ <- exact(root, Set("kind", "terms"), path)
        raw <- field(root, "terms", path)
        terms <- decodeNegotiation(raw, s"$path.terms")
      } yield DecisionAnswerWire.ProposeTermsWire(terms)
      case "accept-deal" => exact(root, Set("kind"), path)
        .map(_ => DecisionAnswerWire.AcceptDealWire)
      case "decline-deal" => exact(root, Set("kind"), path)
        .map(_ => DecisionAnswerWire.DeclineDealWire)
```
In `GameIntentMapper.scala` `decisionAnswer` add (the `negotiation` helper already exists):

```scala
    case DecisionAnswerWire.ProposeTermsWire(terms) =>
      negotiation(terms).map(DecisionAnswer.ProposeTerms)
    case DecisionAnswerWire.AcceptDealWire => Right(DecisionAnswer.AcceptDeal)
    case DecisionAnswerWire.DeclineDealWire => Right(DecisionAnswer.DeclineDeal)
```

- [ ] **Step 8: Fix exhaustiveness elsewhere and run**

`DecisionQuery` and `DecisionAnswer` are sealed, so the compiler now flags every match that does not cover the new cases. Run `./sbtw "Test/compile" "frontend/Test/compile" 2>&1 | grep -i "may not be exhaustive"` and add the missing case wherever a warning names `Negotiate`, `ProposeTerms`, `AcceptDeal` or `DeclineDeal`. The one expected site is `WalkerDecisionProjector.queryProjection`, which Task 6 completes; until then add a placeholder `case _: DecisionQuery.Negotiate => None` there so this task compiles, with a one-line comment `// Task 6 projects the deal.`. Remove that comment when Task 6 replaces the case.

Run: `./sbtw "testOnly oathdigital.gameplay.walker.DecisionQuerySuite oathdigital.serialization.GameEventWireSuite oathdigital.protocol.CommandProtocolSuite oathdigital.application.GameIntentMapperNegotiationSuite oathdigital.gameplay.NegotiationSuite"`
Expected: PASS (the legacy `NegotiationSuite` proves the moved terms types and the delegated legacy codec still work).

- [ ] **Step 9: Commit**

```bash
git add -A src shared
git commit -m "feat(walker): negotiation decision query, answers and codecs

DecisionQuery.Negotiate carries a deal snapshot and per-author bounds, and
accepts now judges a proposal against the answerer's own bounds. The answers
round trip through the journal and the client wire. The terms types move to
their own model file, and the terms journal encoding to its own codec.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: The Negotiation procedure

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/actions/negotiation/NegotiationDeal.scala`, `src/main/scala/oathdigital/gameplay/actions/negotiation/NegotiationProcedure.scala`
- Modify: `src/main/scala/oathdigital/model/ProcedureRef.scala` (`ActionRef.Negotiation`), `src/main/scala/oathdigital/model/RuleFallbackProtocol.scala` (`ActionKind.Negotiation`), `src/main/scala/oathdigital/gameplay/PowerRuntime.scala:49-62`, `src/main/scala/oathdigital/model/PowerWindow.scala` (two windows), `src/main/scala/oathdigital/model/GameProcedureProtocol.scala` (`AwaitingNegotiation`), `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` (entry)
- Create tests: `src/test/scala/oathdigital/gameplay/NegotiationFixture.scala`, `src/test/scala/oathdigital/gameplay/NegotiationDealSuite.scala`, `src/test/scala/oathdigital/gameplay/NegotiationProcedureSuite.scala`, `src/test/scala/oathdigital/model/NegotiationWindowsSuite.scala`
- Modify tests: `src/test/scala/oathdigital/model/ActionKindSuite.scala`

**Interfaces:**
- Consumes: Task 3 (`Decide.coOwners`), Task 4 (`DecisionQuery.Negotiate`, `ProposeTerms`, `AcceptDeal`, `DeclineDeal`, `NegotiationBounds`), Task 2 (`ChooseMany(min, max, ...)`).
- Produces:
  - `NegotiationDeal.negotiatorsDecisionId = "negotiation.negotiators"`, `dealDecisionId = "negotiation.deal"`, `decisionIds: Set[String]`.
  - `final case class DealState(participants: Vector[PlayerId], terms: Map[PlayerId, NegotiationTerms], accepted: Set[PlayerId], declined: Boolean)` with `hasSubstance`, `unanimous`, `closed`, `agreed`.
  - `NegotiationDeal.eligible(state: ReadyGame, actor: PlayerId): Vector[PlayerId]`, `participants(state, actor, pending: PendingTree): Vector[PlayerId]`, `fold(participants: Vector[PlayerId], answered: Vector[Answered]): DealState`, `deal(state, actor, pending): DealState`, `snapshot(state: ReadyGame, deal: DealState): DecisionQuery.Negotiate`, `settle(state: ReadyGame, deal: DealState): Either[OathViolation, Vector[CoreOperation]]`.
  - `NegotiationProcedure.build/rebuild(catalog, state, actor, args): Either[OathViolation, Operation]`, `startable(catalog, state, actor, powers): Boolean`, `decisionIds`.
  - `ActionRef.Negotiation` (key `"negotiation"`), `ActionKind.Negotiation` (key `"negotiation"`), `PowerWindow.NegotiationEligibility` (`"negotiation.eligibility"`), `PowerWindow.NegotiationSettlement` (`"negotiation.settlement"`), `OathContinue.AwaitingNegotiation(playerId: PlayerId, decision: DecisionId)`.

- [ ] **Step 1: The shared test fixture**

Create `src/test/scala/oathdigital/gameplay/NegotiationFixture.scala` (the board is the legacy `NegotiationSuite.ready()` lifted so several suites share it):

```scala
package oathdigital.gameplay

import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** The board the Negotiation suites share: every player at one site holding 5
  * favor and one facedown relic each, the first player (the actor) in the Act
  * phase knowing one relic that lies at that site.
  */
object NegotiationFixture {
  final case class Board(ready: ReadyGame, players: Vector[PlayerState],
      site: SiteId, actorRelic: RelicId, otherRelic: RelicId,
      siteRelic: RelicId) {
    def actor: PlayerId = players.head.player
    def second: PlayerId = players(1).player
    def third: PlayerId = players(2).player
  }

  private val setup = new FirstGameSetupRules(catalog)

  def board(): Board = {
    val Ready(base) = execute(setup)._1: @unchecked
    val siteId = base.game.current.map.inPlay.find(id =>
      base.game.current.map.sites(id).relics.nonEmpty).get
    val siteRelic = base.game.current.map.sites(siteId).relics.head.id
    val relics = base.game.current.map.sites.iterator
      .filterNot(_._1 == siteId).flatMap(_._2.relics).map(_.id).take(3).toVector
    val players = base.game.current.players.zipWithIndex.map { case (p, index) =>
      p.copy(pawnSite = Some(siteId), board = p.board.copy(favor = 5),
        relics = Vector(RelicState(relics(index), Orientation.FaceDown,
          if (index == 0) Tokens(0, 1) else Tokens.empty)))
    }
    val current = base.game.current.copy(players = players,
      map = base.game.current.map.copy(sites = base.game.current.map.sites.map {
        case (`siteId`, value) => siteId -> value
        case (id, value) => id -> value.copy(relics = Vector.empty)
      }), turn = base.game.current.turn.copy(activePlayer = players.head.player,
        phase = Phase.Act), pending = None)
    val ready = base.copy(game = base.game.copy(current = current), knowledge =
      base.knowledge.copy(siteRelics = Map(players.head.player ->
        Map(siteId -> Vector(siteRelic)))))
    Board(ready, players, siteId, relics.head, relics(1), siteRelic)
  }

  private def relocate(board: Board, who: Set[PlayerId]): Board = {
    val elsewhere = board.ready.game.current.map.inPlay.find(_ != board.site).get
    board.copy(ready = board.ready.updateCurrent(current => current.copy(
      players = current.players.map(p =>
        if (who(p.player)) p.copy(pawnSite = Some(elsewhere)) else p))))
  }

  /** The third player moves away, leaving the actor exactly one candidate. */
  def withThirdElsewhere(board: Board): Board = relocate(board, Set(board.third))

  /** Every other player moves away, leaving the actor no candidate. */
  def isolated(board: Board): Board =
    relocate(board, Set(board.second, board.third))

  def player(ready: ReadyGame, id: PlayerId): PlayerState =
    ready.game.current.players.find(_.player == id).get
}
```

- [ ] **Step 2: Write the failing deal-rule tests**

Create `src/test/scala/oathdigital/gameplay/NegotiationDealSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.negotiation.{DealState, NegotiationDeal}
import oathdigital.gameplay.NegotiationFixture.Board
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseManyAnswer, DeclineDeal, ProposeTerms}
import oathdigital.model.OathViolation.InsufficientFavor

/** The deal as a pure function of the answers recorded so far. */
class NegotiationDealSuite extends munit.FunSuite {
  private val b: Board = NegotiationFixture.board()
  private val (a, p, q) = (b.actor, b.second, b.third)
  private val dealId = NegotiationDeal.dealDecisionId

  private def said(by: PlayerId, answer: DecisionAnswer) =
    Answered(dealId, answer, by)
  private val gift = NegotiationTerms(Vector(NegotiationTransfer(p, 2, Vector.empty)))
  private def fold(answers: Answered*) =
    NegotiationDeal.fold(Vector(a, p), answers.toVector)

  test("a fresh deal is empty, open and not agreed") {
    val deal = fold()
    assertEquals(deal.terms, Map(a -> NegotiationTerms(), p -> NegotiationTerms()))
    assert(!deal.hasSubstance && !deal.closed && !deal.agreed)
  }

  test("a proposal replaces its author's whole terms and clears every acceptance") {
    val deal = fold(said(a, ProposeTerms(gift)), said(p, AcceptDeal),
      said(p, ProposeTerms(NegotiationTerms())))
    assertEquals(deal.terms(a), gift)
    assertEquals(deal.terms(p), NegotiationTerms())
    assertEquals(deal.accepted, Set.empty[PlayerId])
    assert(deal.hasSubstance)
  }

  test("the deal is agreed only when every participant accepted terms with substance") {
    val once = fold(said(a, ProposeTerms(gift)), said(p, AcceptDeal))
    assert(!once.closed && !once.agreed)
    val all = fold(said(a, ProposeTerms(gift)), said(p, AcceptDeal),
      said(a, AcceptDeal))
    assert(all.closed && all.agreed)
  }

  test("a decline closes the deal without agreeing it") {
    val deal = fold(said(a, ProposeTerms(gift)), said(a, AcceptDeal),
      said(p, DeclineDeal))
    assert(deal.closed && deal.declined && !deal.agreed)
  }

  test("answers to other decisions are ignored") {
    val stray = Answered(NegotiationDeal.negotiatorsDecisionId,
      ChooseManyAnswer(Vector(DecisionOptionRef.Player(p))), a)
    assertEquals(fold(stray), fold())
  }

  test("eligible lists the other players at the actor's site, in table order") {
    assertEquals(NegotiationDeal.eligible(b.ready, a), Vector(p, q))
    assertEquals(NegotiationDeal.eligible(
      NegotiationFixture.withThirdElsewhere(b).ready, a), Vector(p))
    assertEquals(NegotiationDeal.eligible(
      NegotiationFixture.isolated(b).ready, a), Vector.empty[PlayerId])
  }

  test("participants come from the negotiator answer, else the one candidate") {
    val chosen = PendingTree(Vector("1"), Vector(Answered(
      NegotiationDeal.negotiatorsDecisionId,
      ChooseManyAnswer(Vector(DecisionOptionRef.Player(q))), a)))
    assertEquals(NegotiationDeal.participants(b.ready, a, chosen), Vector(a, q))
    val forced = NegotiationFixture.withThirdElsewhere(b)
    assertEquals(NegotiationDeal.participants(forced.ready, a,
      PendingTree(Vector.empty, Vector.empty)), Vector(a, p))
  }

  test("the snapshot carries each author's own bounds and who may accept") {
    val deal = fold(said(a, ProposeTerms(gift)), said(a, AcceptDeal))
    val query = NegotiationDeal.snapshot(b.ready, deal)
    assertEquals(query.participants, Vector(a, p))
    assertEquals(query.terms(a), gift)
    assertEquals(query.accepted, Set(a))
    assertEquals(query.acceptors, Set(p))
    val own = query.bounds(a)
    assertEquals(own.recipients, Vector(p))
    assertEquals(own.maxFavor, 5)
    assertEquals(own.relics, Vector(b.actorRelic))
    // The fixture leaves every player their setup advisers, so assert what the
    // relics contribute rather than the whole list.
    assert(own.disclosures.contains(
      NegotiationDisclosureRef.HeldRelic(a, b.actorRelic)))
    assert(own.disclosures.contains(
      NegotiationDisclosureRef.SiteRelic(b.site, b.siteRelic)))
    assert(query.bounds(p).disclosures.contains(
      NegotiationDisclosureRef.HeldRelic(p, b.otherRelic)))
    assert(!query.bounds(p).disclosures.exists {
      case NegotiationDisclosureRef.SiteRelic(_, _) => true
      case _ => false
    })
  }

  test("an empty deal has no acceptors") {
    assertEquals(NegotiationDeal.snapshot(b.ready, fold()).acceptors,
      Set.empty[PlayerId])
  }

  test("settlement records disclosures before transfers, in participant order") {
    val terms = NegotiationTerms(
      Vector(NegotiationTransfer(p, 3, Vector(b.actorRelic))),
      Vector(NegotiationDisclosure(p,
        NegotiationDisclosureRef.HeldRelic(a, b.actorRelic))))
    val ops = NegotiationDeal.settle(b.ready,
      fold(said(a, ProposeTerms(terms)))).toOption.get
    assertEquals(ops, Vector[CoreOperation](
      Peek(p, b.actorRelic, Location.PlayArea(a)),
      Give(Piece.Favor(3), a, Location.PlayArea(a), Location.PlayArea(p)),
      Give(Piece.Card(b.actorRelic), a, Location.PlayArea(a),
        Location.PlayArea(p))))
  }

  test("settlement is refused when an author can no longer afford their terms") {
    val terms = NegotiationTerms(Vector(NegotiationTransfer(p, 3, Vector.empty)))
    val broke = b.ready.updateCurrent(current => current.copy(players =
      current.players.map(pl => if (pl.player == a)
        pl.copy(board = pl.board.copy(favor = 1)) else pl)))
    assertEquals(NegotiationDeal.settle(broke,
      fold(said(a, ProposeTerms(terms)))), Left(InsufficientFavor(3, 1)))
    val gone = terms.copy(transfers = Vector(NegotiationTransfer(p, 0,
      Vector(b.otherRelic))))
    assert(NegotiationDeal.settle(b.ready,
      fold(said(a, ProposeTerms(gone)))).isLeft)
  }
}
```

- [ ] **Step 3: Write the failing procedure tests**

Create `src/test/scala/oathdigital/gameplay/NegotiationProcedureSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.NegotiationFixture.{Board, player}
import oathdigital.gameplay.actions.negotiation.{NegotiationDeal, NegotiationProcedure}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseManyAnswer, DeclineDeal, ProposeTerms}
import oathdigital.model.OathEvent.IgnoredRulesRecorded
import oathdigital.model.OathState.Ready

/** Negotiation through the rules, as a client drives it: start, choose the
  * negotiators, then answer the deal in any order until it closes.
  */
class NegotiationProcedureSuite extends munit.FunSuite {
  private val rules = new OathRules(catalog)
  private val negotiators = NegotiationDeal.negotiatorsDecisionId
  private val dealId = NegotiationDeal.dealDecisionId

  private def start(b: Board) =
    rules.startWalker(Ready(b.ready), ActionRef.Negotiation, b.actor)

  private def choose(state: OathState, actor: PlayerId, who: PlayerId*) =
    rules.resolveWalker(state, actor, negotiators, ChooseManyAnswer(
      who.toVector.map(DecisionOptionRef.Player(_))))

  private def say(state: OathState, by: PlayerId, answer: DecisionAnswer) =
    rules.resolveWalker(state, by, dealId, answer)

  private def ready(state: OathState): ReadyGame = state match {
    case Ready(value) => value
    case other => fail(s"expected a ready game, got $other")
  }

  private def gift(to: PlayerId, favor: Int, relics: Vector[RelicId] = Vector.empty) =
    ProposeTerms(NegotiationTerms(Vector(NegotiationTransfer(to, favor, relics))))

  /** Starts and chooses `who`, returning the state parked at the deal. */
  private def atDeal(b: Board, who: PlayerId*): OathTransition = {
    val started = start(b).getOrElse(fail("Negotiation must start"))
    choose(started.state, b.actor, who: _*).getOrElse(
      fail("the negotiators must be accepted"))
  }

  test("starting parks on the negotiator choice, offered to the actor") {
    val b = NegotiationFixture.board()
    val started = start(b).getOrElse(fail("Negotiation must start"))
    assertEquals(started.continue, OathContinue.AwaitingNegotiation(b.actor,
      DecisionId(negotiators)))
    assertEquals(ready(started.state).game.current.pending, None)
  }

  test("a lone candidate skips the negotiator choice") {
    val b = NegotiationFixture.withThirdElsewhere(NegotiationFixture.board())
    val started = start(b).getOrElse(fail("Negotiation must start"))
    assertEquals(started.continue, OathContinue.AwaitingNegotiation(b.actor,
      DecisionId(dealId)))
  }

  test("with no candidate the start is rejected and offered nowhere") {
    val b = NegotiationFixture.isolated(NegotiationFixture.board())
    assert(start(b).left.toOption.exists(
      _.isInstanceOf[OathViolation.NegotiationUnavailable]))
    assert(!NegotiationProcedure.startable(catalog, b.ready, b.actor,
      WalkerPowers.empty))
    val open = NegotiationFixture.board()
    assert(NegotiationProcedure.startable(catalog, open.ready, open.actor,
      WalkerPowers.empty))
  }

  test("no first-game gate: an Imperial player or an altered Foundation still negotiates") {
    val b = NegotiationFixture.board()
    val lineage = b.ready.game.campaign.lineages(player(b.ready, b.second).lineage)
    val campaign = b.ready.game.campaign
    val citizen = b.ready.copy(game = b.ready.game.copy(campaign =
      campaign.copy(lineages = campaign.lineages.updated(lineage.id,
        lineage.copy(role = Role.Citizen)))))
    assert(start(b.copy(ready = citizen)).isRight)
    val altered = b.ready.copy(game = b.ready.game.copy(campaign =
      campaign.copy(foundations = campaign.foundations.map { case (k, f) =>
        k -> f.copy(face = FoundationFace.Altered) })))
    assert(start(b.copy(ready = altered)).isRight)
  }

  test("a bilateral favor and relic transfer settles atomically on the last accept") {
    val b = NegotiationFixture.board()
    val deal = atDeal(b, b.second)
    assertEquals(deal.continue, OathContinue.AwaitingNegotiation(b.actor,
      DecisionId(dealId)))
    val proposed = say(deal.state, b.actor, gift(b.second, 3, Vector(b.actorRelic)))
      .getOrElse(fail("terms must be accepted"))
    val theirs = say(proposed.state, b.second, AcceptDeal)
      .getOrElse(fail("the counterparty may accept first"))
    val done = say(theirs.state, b.actor, AcceptDeal)
      .getOrElse(fail("the last accept must settle"))
    val after = ready(done.state)
    assertEquals(player(after, b.actor).board.favor, 2)
    assertEquals(player(after, b.second).board.favor, 8)
    assertEquals(after.game.current.players.map(_.board.favor).sum,
      b.ready.game.current.players.map(_.board.favor).sum)
    assertEquals(player(after, b.second).relics.find(_.id == b.actorRelic)
      .get.tokens, Tokens(0, 1))
    assertEquals(after.game.current.walkerPending, None)
    assertEquals(after.game.current.pending, None)
    assertEquals(done.continue, OathContinue.ActActionSelection(b.actor))
  }

  test("three players answer in any order and a changed term resets consent") {
    val b = NegotiationFixture.board()
    val deal = atDeal(b, b.second, b.third)
    val one = say(deal.state, b.actor, gift(b.second, 1)).toOption.get
    val two = say(one.state, b.second, AcceptDeal).toOption.get
    val three = say(two.state, b.third, gift(b.actor, 2)).toOption.get
    val open = ready(three.state).game.current.walkerPending.get
    val folded = NegotiationDeal.fold(Vector(b.actor, b.second, b.third),
      open.answered)
    assertEquals(folded.accepted, Set.empty[PlayerId])
    val accepted = Vector(b.third, b.second, b.actor).foldLeft(three.state) {
      (state, by) => say(state, by, AcceptDeal).getOrElse(fail(s"$by accepts"))
        .state
    }
    assertEquals(player(ready(accepted), b.actor).board.favor, 5 - 1 + 2)
    assertEquals(ready(accepted).game.current.walkerPending, None)
  }

  test("a decline ends the action with nothing moved") {
    val b = NegotiationFixture.board()
    val deal = atDeal(b, b.second)
    val proposed = say(deal.state, b.actor, gift(b.second, 3)).toOption.get
    val declined = say(proposed.state, b.second, DeclineDeal)
      .getOrElse(fail("any participant may decline"))
    val after = ready(declined.state)
    assertEquals(after.game.current.players, b.ready.game.current.players)
    assertEquals(after.game.current.walkerPending, None)
    assertEquals(declined.continue, OathContinue.ActActionSelection(b.actor))
  }

  test("a player outside the chosen negotiators cannot answer") {
    val b = NegotiationFixture.board()
    val deal = atDeal(b, b.second)
    assertEquals(say(deal.state, b.third, gift(b.actor, 1)).left.toOption,
      Some(OathViolation.WrongPlayer(b.actor, b.third)))
  }

  test("terms beyond the author's means, and an empty deal's accept, are rejected") {
    val b = NegotiationFixture.board()
    val deal = atDeal(b, b.second)
    assertEquals(say(deal.state, b.actor, gift(b.second, 6)).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "decision negotiation.deal offers more than 5 favor")))
    assertEquals(say(deal.state, b.second, AcceptDeal).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "decision negotiation.deal does not let this player accept now")))
  }

  test("a settlement that cannot be met rejects the last accept and leaves the deal open") {
    val b = NegotiationFixture.board()
    val deal = atDeal(b, b.second)
    val proposed = say(deal.state, b.actor, gift(b.second, 3)).toOption.get
    val theirs = say(proposed.state, b.second, AcceptDeal).toOption.get
    val broke = Ready(ready(theirs.state).updateCurrent(current =>
      current.copy(players = current.players.map(p => if (p.player == b.actor)
        p.copy(board = p.board.copy(favor = 1)) else p))))
    assertEquals(say(broke, b.actor, AcceptDeal).left.toOption,
      Some(OathViolation.InsufficientFavor(3, 1)))
    assert(say(theirs.state, b.actor, AcceptDeal).isRight)
  }

  test("an agreed disclosure grants durable knowledge to its recipient only") {
    val b = NegotiationFixture.board()
    val adviser = player(b.ready, b.actor).advisers.head.id
      .asInstanceOf[WorldCardId]
    val deal = atDeal(b, b.second, b.third)
    val terms = ProposeTerms(NegotiationTerms(disclosures = Vector(
      NegotiationDisclosure(b.second,
        NegotiationDisclosureRef.Adviser(b.actor, adviser)),
      NegotiationDisclosure(b.second,
        NegotiationDisclosureRef.SiteRelic(b.site, b.siteRelic)))))
    val proposed = say(deal.state, b.actor, terms).getOrElse(fail("terms"))
    val closed = Vector(b.actor, b.second, b.third).foldLeft(proposed.state) {
      (state, by) => say(state, by, AcceptDeal).getOrElse(fail(s"$by accepts"))
        .state
    }
    val after = ready(closed)
    assert(after.knowledge.advisers(b.second).contains(adviser))
    assert(after.knowledge.siteRelics(b.second)(b.site).contains(b.siteRelic))
    assert(!after.knowledge.advisers.getOrElse(b.third, Vector.empty)
      .contains(adviser))
  }

  test("closing a deal runs the action boundary, whether declined or agreed") {
    val b = NegotiationFixture.board()
    val emptySite = b.ready.game.current.map.inPlay.find(_ != b.site).get
    val boundary = b.copy(ready = b.ready.updateCurrent(current => current.copy(
      map = current.map.copy(sites = current.map.sites.updated(emptySite,
        current.map.sites(emptySite).copy(forces = SiteForces.Empty))))))
    val deal = atDeal(boundary, b.second)
    val declined = say(deal.state, b.second, DeclineDeal)
      .getOrElse(fail("any participant may decline"))
    assert(declined.events.exists(_.isInstanceOf[OathEvent.BanditsRefilled]),
      "a decline must run the action boundary and its bandit refill")
    val proposed = say(deal.state, b.actor, gift(b.second, 1))
      .getOrElse(fail("terms"))
    val agreed = Vector(b.second, b.actor).foldLeft(proposed.state) {
      (state, by) => say(state, by, AcceptDeal).getOrElse(fail(s"$by accepts"))
        .state
    }
    assertEquals(ready(agreed).game.current.walkerPending, None)
  }

  test("unsupported Negotiation rules are recorded as ignored, not blocking") {
    val b = NegotiationFixture.board()
    val definition = catalog.denizens.find(
      _.handlers.contains("denizen.council-arbiter")).get
    val powered = b.ready.updateCurrent(current => current.copy(players =
      current.players.map(p => if (p.player == b.actor) p.copy(advisers =
        Vector(DenizenState(DenizenId(definition.id.value), Orientation.FaceUp,
          Tokens.empty))) else p)))
    val started = start(b.copy(ready = powered)).getOrElse(
      fail("an unsupported rule must not block"))
    val recorded = started.events.head.asInstanceOf[IgnoredRulesRecorded]
    assertEquals(recorded.action, ActionKind.Negotiation)
    assertEquals(recorded.diagnostics.head.handlerId, "denizen.council-arbiter")
    assert(choose(started.state, b.actor, b.second).isRight)
  }

  test("a completed Negotiation replays exactly from its journal") {
    val (setupState, setupEvents) = execute(new FirstGameSetupRules(catalog))
    val Ready(original) = setupState: @unchecked
    val actor = original.game.current.turn.activePlayer
    val other = original.game.current.players.find(_.player != actor).get.player
    val destination = player(original, other).pawnSite.get
    val act = rules.startWalker(setupState, PhaseTransitionRef.EndWake, actor)
      .toOption.get
    val traveled = rules.startWalker(act.state, ActionRef.Travel, actor,
      Vector.empty, Vector(DecisionOptionRef.Site(destination))).toOption.get
    val adviser = player(ready(traveled.state), actor).advisers.head.id
      .asInstanceOf[WorldCardId]
    val started = rules.startWalker(traveled.state, ActionRef.Negotiation, actor)
      .getOrElse(fail("Negotiation must start"))
    val chosen =
      if (started.continue == OathContinue.AwaitingNegotiation(actor,
          DecisionId(negotiators)))
        choose(started.state, actor, other).getOrElse(fail("negotiators"))
      else started
    val terms = ProposeTerms(NegotiationTerms(disclosures = Vector(
      NegotiationDisclosure(other, NegotiationDisclosureRef.Adviser(actor,
        adviser)))))
    val proposed = say(chosen.state, actor, terms).getOrElse(fail("terms"))
    val a = say(proposed.state, actor, AcceptDeal).getOrElse(fail("actor"))
    val done = say(a.state, other, AcceptDeal).getOrElse(fail("other"))
    val events = setupEvents ++ act.events ++ traveled.events ++ started.events ++
      chosen.events.filterNot(started.events.contains) ++ proposed.events ++
      a.events ++ done.events
    val replayed = new EventReplayEngine(rules).replay(events.zipWithIndex.map {
      case (event, index) => RecordedEvent(index.toLong, event)
    }).toOption.get
    assertEquals(replayed, done.state)
  }
}
```
(The last test filters `chosen.events` because when the negotiator choice is skipped `chosen` is `started`. If the fixture's first-game table always offers two candidates after Travel, simplify the branch to the `choose` call.)

Create `src/test/scala/oathdigital/model/NegotiationWindowsSuite.scala`:

```scala
package oathdigital.model

class NegotiationWindowsSuite extends munit.FunSuite {
  test("Negotiation windows have stable keys and no major action") {
    Vector(
      PowerWindow.NegotiationEligibility -> "negotiation.eligibility",
      PowerWindow.NegotiationSettlement -> "negotiation.settlement",
      PowerWindow.NegotiationOffer -> "negotiation.offer").foreach {
      case (window, key) =>
        assertEquals(window.key, key)
        assertEquals(window.associatedMajorAction, None)
    }
  }
}
```
Update `ActionKindSuite`'s expected keys to end `..., "when-played", "action-boundary", "negotiation")`.

- [ ] **Step 4: Run to confirm they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.NegotiationDealSuite oathdigital.gameplay.NegotiationProcedureSuite oathdigital.model.NegotiationWindowsSuite oathdigital.model.ActionKindSuite"`
Expected: FAIL to compile (`NegotiationDeal`, `ActionRef.Negotiation`, `ActionKind.Negotiation`, `AwaitingNegotiation` and the windows do not exist).

- [ ] **Step 5: References, kinds, windows, continuation**

`ProcedureRef.scala`: add `case object Negotiation extends ActionRef { val key = "negotiation" }` inside `object ActionRef` and append `Negotiation` to `ActionRef.all`. `RuleFallbackProtocol.scala`: add `case object Negotiation extends ActionKind { val key = "negotiation" }` and append it to `ActionKind.values`. `PowerRuntime.scala` `window`: add `case ActionKind.Negotiation => PowerWindow.NegotiationOffer`. `PowerWindow.scala`, next to `NegotiationOffer`:

```scala
  case object NegotiationEligibility extends OtherWindow {
    val key = "negotiation.eligibility"
  }
  case object NegotiationSettlement extends OtherWindow {
    val key = "negotiation.settlement"
  }
```
`GameProcedureProtocol.scala`, next to `AwaitingBannerDecision`:

```scala
  /** Either decision of a Negotiation: the negotiator choice, or the open
    * deal, which any participant may answer. `playerId` is the primary owner,
    * the actor.
    */
  final case class AwaitingNegotiation(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
```

- [ ] **Step 6: The deal rules**

Create `src/main/scala/oathdigital/gameplay/actions/negotiation/NegotiationDeal.scala`:

```scala
package oathdigital.gameplay.actions.negotiation

import oathdigital.model._
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseManyAnswer, DeclineDeal, ProposeTerms}

/** One deal, derived from the answers recorded so far. */
final case class DealState(participants: Vector[PlayerId],
    terms: Map[PlayerId, NegotiationTerms], accepted: Set[PlayerId],
    declined: Boolean) {
  def hasSubstance: Boolean = terms.values.exists(t =>
    t.transfers.exists(x => x.favor > 0 || x.relics.nonEmpty) ||
      t.disclosures.nonEmpty)
  def unanimous: Boolean =
    participants.nonEmpty && accepted == participants.toSet
  def closed: Boolean = declined || unanimous
  def agreed: Boolean = !declined && unanimous && hasSubstance
}

/** The deal rules, pure: who may negotiate, what the answers so far amount
  * to, what each author may still offer, and what settling does. Nothing here
  * is stored: the walker's `PendingTree.answered` is the only record, and
  * everything else is recomputed from it and from live state on every
  * command, which is safe because nothing that changes a bound can run while
  * a deal is open.
  */
object NegotiationDeal {
  val negotiatorsDecisionId: String = "negotiation.negotiators"
  val dealDecisionId: String = "negotiation.deal"
  val decisionIds: Set[String] = Set(negotiatorsDecisionId, dealDecisionId)

  /** The players the actor may deal with: the others whose pawn is at the
    * actor's pawn site. The one place that rule lives, asked when the tree is
    * built and (through the tree) when a later power widens it.
    */
  def eligible(state: ReadyGame, actor: PlayerId): Vector[PlayerId] = {
    val players = state.game.current.players
    players.find(_.player == actor).flatMap(_.pawnSite).toVector.flatMap(site =>
      players.collect {
        case other if other.player != actor && other.pawnSite.contains(site) =>
          other.player
      })
  }

  /** Actor first, then the chosen players in table order. With no recorded
    * negotiator answer the one eligible candidate is the negotiator: the
    * tree omits the choice when there is only one.
    */
  def participants(state: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[PlayerId] = {
    val picked = pending.answered.reverse.collectFirst {
      case Answered(`negotiatorsDecisionId`, ChooseManyAnswer(selected), _) =>
        selected.collect { case DecisionOptionRef.Player(id) => id }
    }.getOrElse(eligible(state, actor)).toSet
    actor +: state.game.current.players.map(_.player).filter(picked)
  }

  def fold(participants: Vector[PlayerId],
      answered: Vector[Answered]): DealState =
    answered.filter(_.decisionId == dealDecisionId).foldLeft(DealState(
      participants, participants.map(_ -> NegotiationTerms()).toMap,
      Set.empty, declined = false)) {
      case (deal, Answered(_, ProposeTerms(terms), by)) =>
        deal.copy(terms = deal.terms.updated(by, terms), accepted = Set.empty)
      case (deal, Answered(_, AcceptDeal, by)) =>
        deal.copy(accepted = deal.accepted + by)
      case (deal, Answered(_, DeclineDeal, _)) => deal.copy(declined = true)
      case (deal, _) => deal
    }

  def deal(state: ReadyGame, actor: PlayerId, pending: PendingTree): DealState =
    fold(participants(state, actor, pending), pending.answered)

  /** The question the deal asks now: everyone's terms, who accepted, what each
    * author may still offer, and who may accept (anyone who has not, once the
    * deal has substance).
    */
  def snapshot(state: ReadyGame, deal: DealState): DecisionQuery.Negotiate =
    DecisionQuery.Negotiate(deal.participants, deal.terms, deal.accepted,
      deal.participants.map(author => author ->
        bounds(state, author, deal.participants)).toMap,
      if (deal.hasSubstance && !deal.declined)
        deal.participants.filterNot(deal.accepted).toSet
      else Set.empty[PlayerId],
      heading = Some("Negotiation"))

  private def bounds(state: ReadyGame, author: PlayerId,
      participants: Vector[PlayerId]): NegotiationBounds = {
    val player = state.game.current.players.find(_.player == author).get
    NegotiationBounds(participants.filter(_ != author), player.board.favor,
      player.relics.map(_.id), disclosable(state, player))
  }

  /** What `author` may promise to reveal: their facedown advisers and held
    * relics, and site relics they know that are still at that site.
    */
  private def disclosable(state: ReadyGame,
      player: PlayerState): Vector[NegotiationDisclosureRef] = {
    val advisers = player.advisers.collect {
      case DenizenState(id, Orientation.FaceDown, _) =>
        NegotiationDisclosureRef.Adviser(player.player, id): NegotiationDisclosureRef
      case VisionState(id, Orientation.FaceDown) =>
        NegotiationDisclosureRef.Adviser(player.player, id): NegotiationDisclosureRef
    }
    val held = player.relics.collect {
      case relic if relic.orientation == Orientation.FaceDown =>
        NegotiationDisclosureRef.HeldRelic(player.player, relic.id): NegotiationDisclosureRef
    }
    val known = state.knowledge.siteRelics.getOrElse(player.player, Map.empty)
      .toVector.sortBy(_._1.value).flatMap { case (site, ids) =>
        state.game.current.map.sites.get(site).toVector.flatMap(
          _.relics.filter(relic => ids.contains(relic.id)).map(relic =>
            NegotiationDisclosureRef.SiteRelic(site, relic.id): NegotiationDisclosureRef))
      }
    advisers ++ held ++ known
  }

  /** What an agreed deal does: every disclosure records knowledge first, while
    * the cards are still where they were disclosed, then every transfer moves.
    * Refused (nothing recorded, the deal stays open) when an author can no
    * longer afford their terms.
    */
  def settle(state: ReadyGame,
      deal: DealState): Either[OathViolation, Vector[CoreOperation]] =
    deal.participants.foldLeft[Either[OathViolation, Unit]](Right(())) {
      (result, author) => result.flatMap(_ => affordable(state, author,
        deal.terms(author)))
    }.map(_ => knowledge(deal) ++ transfers(deal))

  private def affordable(state: ReadyGame, author: PlayerId,
      terms: NegotiationTerms): Either[OathViolation, Unit] = {
    val player = state.game.current.players.find(_.player == author).get
    val favor = terms.transfers.map(_.favor).sum
    for {
      _ <- Either.cond(favor <= player.board.favor, (),
        OathViolation.InsufficientFavor(favor, player.board.favor))
      _ <- Either.cond(terms.transfers.flatMap(_.relics).forall(id =>
        player.relics.exists(_.id == id)), (), OathViolation.NegotiationUnavailable(
        "an offered relic is no longer held by its author"))
    } yield ()
  }

  private def knowledge(deal: DealState): Vector[CoreOperation] =
    deal.participants.flatMap(deal.terms(_).disclosures).map {
      case NegotiationDisclosure(recipient,
          NegotiationDisclosureRef.Adviser(owner, card)) =>
        Peek(recipient, card, Location.PlayArea(owner)): CoreOperation
      case NegotiationDisclosure(recipient,
          NegotiationDisclosureRef.HeldRelic(owner, relic)) =>
        Peek(recipient, relic, Location.PlayArea(owner)): CoreOperation
      case NegotiationDisclosure(recipient,
          NegotiationDisclosureRef.SiteRelic(site, relic)) =>
        Peek(recipient, relic, Location.Site(site)): CoreOperation
    }

  private def transfers(deal: DealState): Vector[CoreOperation] =
    deal.participants.flatMap { author =>
      deal.terms(author).transfers.flatMap { transfer =>
        val favor = Option.when(transfer.favor > 0)(Give(Piece.Favor(
          transfer.favor), author, Location.PlayArea(author),
          Location.PlayArea(transfer.recipient)): CoreOperation).toVector
        val relics = transfer.relics.map(relic => Give(Piece.Card(relic),
          author, Location.PlayArea(author),
          Location.PlayArea(transfer.recipient)): CoreOperation)
        favor ++ relics
      }
    }
}
```

- [ ] **Step 7: The procedure**

Create `src/main/scala/oathdigital/gameplay/actions/negotiation/NegotiationProcedure.scala`:

```scala
package oathdigital.gameplay.actions.negotiation

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathLifecycle, PowerRuntime}
import oathdigital.gameplay.walker.{WalkerPowers, WalkerSimulation}
import oathdigital.model._

/** Negotiation on the walker: choose who to negotiate with, then loop one
  * deal decision that every participant may answer until the deal closes,
  * then settle it if it was agreed. A 0-Supply minor action taking no start
  * selection.
  *
  * {{{
  * Sequence(
  *   Decide(negotiators)                  -- omitted when there is one candidate
  *   Repeat(!closed) { Branch -> Decide(deal, co-owned) }
  *   Branch(agreed -> BuildOps(settle)))
  * }}}
  *
  * Nothing about the deal is stored: the guard, the query and the settlement
  * all read `PendingTree.answered` (see [[NegotiationDeal]]).
  */
object NegotiationProcedure {
  val decisionIds: Set[String] = NegotiationDeal.decisionIds

  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
    _ <- noStartArgs(args)
    _ <- OathLifecycle.validateAct(OathState.Ready(state), actor)
    _ <- PowerRuntime.requireAudited(catalog)
    _ <- Either.cond(NegotiationDeal.eligible(state, actor).nonEmpty, (),
      OathViolation.NegotiationUnavailable(
        "no other player has a pawn at your site"))
  } yield tree(state, actor)

  /** Whether Negotiation could start now: the gates pass and the first walk
    * (up to the first decision) is accepted.
    */
  def startable(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): Boolean =
    build(catalog, state, actor, Vector.empty)
      .exists(WalkerSimulation.starts(_, state, powers))

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    noStartArgs(args).map(_ => tree(state, actor))

  private def noStartArgs(args: Vector[DecisionOptionRef])
      : Either[OathViolation, Unit] = Either.cond(args.isEmpty, (),
    OathViolation.InvalidEventOrder(
      s"walker procedure ${ActionRef.Negotiation.key} takes no start " +
        s"selection, got ${args.map(_.kind).mkString(", ")}"))

  private def tree(state: ReadyGame, actor: PlayerId): Operation = {
    val candidates = NegotiationDeal.eligible(state, actor)
    val choose: Vector[Operation] =
      if (candidates.size < 2) Vector.empty
      else Vector(Decide(NegotiationDeal.negotiatorsDecisionId, actor,
        DecisionQuery.ChooseMany(1, candidates.size, candidates.map(id =>
          DecisionOption.Player(DecisionOptionRef.Player(id))),
          heading = Some("Choose who to negotiate with")),
        window = Some(PowerWindow.NegotiationEligibility)))
    Sequence(choose ++ Vector[Operation](
      Repeat((ready, pending) =>
        !NegotiationDeal.deal(ready, actor, pending).closed,
        Branch((ready, pending) => {
          val deal = NegotiationDeal.deal(ready, actor, pending)
          Vector(Decide(NegotiationDeal.dealDecisionId, actor,
            NegotiationDeal.snapshot(ready, deal),
            coOwners = deal.participants.filter(_ != actor)))
        })),
      Branch((ready, pending) => {
        val deal = NegotiationDeal.deal(ready, actor, pending)
        if (deal.agreed) Vector[Operation](BuildOps(
          (state, _) => NegotiationDeal.settle(state, deal),
          window = Some(PowerWindow.NegotiationSettlement)))
        else Vector.empty
      })))
  }
}
```
The settle `BuildOps` closes over `deal` folded before the walk reaches it. That is safe: the answers are fixed and `settle` reads live state when built.

- [ ] **Step 8: Register**

In `WalkerProcedureRegistry.scala` add the import `import oathdigital.gameplay.actions.negotiation.NegotiationProcedure` and this entry after `PlaceBannerResource`:

```scala
    /** Negotiation is a minor action: no modifier window (none of its powers
      * is player-selected), and an `ActionKind` of its own so a start records
      * the Negotiation rules it ignores.
      */
    ActionRef.Negotiation -> Entry(
      fallbackKind = Some(ActionKind.Negotiation),
      rollDecisionId = None,
      modifierWindow = None,
      continuationFor = (decisionId, actor, decision) =>
        Option.when(NegotiationProcedure.decisionIds.contains(decisionId))(
          OathContinue.AwaitingNegotiation(actor, decision)),
      build = NegotiationProcedure.build,
      rebuild = NegotiationProcedure.rebuild),
```

- [ ] **Step 9: Run**

Run: `./sbtw "testOnly oathdigital.gameplay.NegotiationDealSuite oathdigital.gameplay.NegotiationProcedureSuite oathdigital.model.NegotiationWindowsSuite oathdigital.model.ActionKindSuite oathdigital.gameplay.walker.WalkerProcedureRegistrySuite oathdigital.gameplay.NegotiationSuite oathdigital.serialization.GameEventWireSuite"`
Expected: PASS. If `WalkerProcedureRegistrySuite` or a wire suite enumerates every `ProcedureRef`/`ActionRef` with a fixed count, update the count. The legacy `NegotiationSuite` must still pass, since both paths coexist until Task 10.

If a `NegotiationProcedureSuite` test fails on the deal loop (the guard, resume paths or `Repeat` re-parks) and not on a typo, that is the Task 3 spike failing in a real tree: stop and follow the spike rule.

- [ ] **Step 10: Full gate and commit**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS.

```bash
git add -A src
git commit -m "feat(negotiation): port Negotiation onto the walker

The deal is a Repeat around one co-owned Decide, and its state is a fold over
the recorded answers. The negotiator choice is a ChooseMany range fed by the
eligible players and is skipped when there is one. Settlement disclosures
first, then transfers, as one recorded step that is refused when an author can
no longer afford their terms. Unsupported Negotiation rules are recorded as
ignored through ActionKind.Negotiation. The legacy path still runs beside it.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 6: Projecting the deal

The walker decision projection learns about co-owners and the `Negotiate` query. The legacy `negotiation` field and its projector stay until Task 10, so both paths project during the parity work.

**Files:**
- Create: `src/main/scala/oathdigital/application/NegotiationDealProjector.scala`, `src/test/scala/oathdigital/application/NegotiationDealProjectionSuite.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala` (new DTOs, `DecisionQueryProjection.deal`, `WalkerWaitingProjection`), `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala`, `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`, `src/main/scala/oathdigital/application/LegalActionProjector.scala:167`
- Modify tests: `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala` (round trips), `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala:457,491` only if the `WalkerWaitingProjection` constructor calls need the new defaults (they should not)

**Interfaces:**
- Consumes: Task 4 (`DecisionQuery.Negotiate`), Task 3 (`ProcedureWalker.awaitedPlayers`, `openDecisions`), Task 5 (`NegotiationProcedure.startable`).
- Produces:
  - `NegotiationEditingProjection(editableFavor: Int, editableRelics: Vector[CardDetailsProjection], editableAdvisers: Vector[CardDetailsProjection], editableSiteRelics: Vector[NegotiationSiteRelicProjection], canAccept: Boolean)`.
  - `NegotiationDealProjection(participantPlayerIds: Vector[String], acceptedPlayerIds: Vector[String], transfers: Vector[NegotiationTransferProjection], disclosures: Vector[NegotiationDisclosureProjection], editing: Option[NegotiationEditingProjection] = None)`.
  - `DecisionQueryProjection(..., deal: Option[NegotiationDealProjection] = None)`, form `"negotiate"`.
  - `WalkerWaitingProjection(playerId: String, heading: Option[String] = None, coOwnerPlayerIds: Vector[String] = Vector.empty, deal: Option[NegotiationDealProjection] = None)`.
  - `NegotiationDealProjector.project(ready: ReadyGame, viewer: Option[PlayerId], query: DecisionQuery.Negotiate): NegotiationDealProjection`.
  - The `beginNegotiation` legal control is offered by `NegotiationProcedure.startable`.

- [ ] **Step 1: Write the failing projection tests**

Create `src/test/scala/oathdigital/application/NegotiationDealProjectionSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.NegotiationFixture
import oathdigital.gameplay.NegotiationFixture.Board
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.negotiation.NegotiationDeal
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseManyAnswer, ProposeTerms}
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.{GameProjection, NegotiationDealProjection}

/** What each viewer of a parked deal is shown. */
class NegotiationDealProjectionSuite extends munit.FunSuite {
  private val rules = new OathRules(catalog)
  private val projector = new GameProjector(catalog)
  private val dealId = NegotiationDeal.dealDecisionId

  private def parkedDeal(b: Board, terms: Option[NegotiationTerms] = None,
      who: Vector[PlayerId] = Vector.empty): OathState = {
    val started = rules.startWalker(Ready(b.ready), ActionRef.Negotiation,
      b.actor).getOrElse(fail("Negotiation must start"))
    val chosen = rules.resolveWalker(started.state, b.actor,
      NegotiationDeal.negotiatorsDecisionId, ChooseManyAnswer(
        (if (who.isEmpty) Vector(b.second, b.third) else who)
          .map(DecisionOptionRef.Player(_)))).getOrElse(fail("negotiators"))
    terms.fold(chosen.state)(value => rules.resolveWalker(chosen.state, b.actor,
      dealId, ProposeTerms(value)).getOrElse(fail("terms")).state)
  }

  private def view(state: OathState, viewer: PlayerId): GameProjection =
    projector.project("negotiation", LoadedGame(state, 30), viewer)

  private def deal(projection: GameProjection): NegotiationDealProjection =
    projection.walkerDecision.flatMap(_.query).flatMap(_.deal)
      .orElse(projection.walkerWaiting.flatMap(_.deal))
      .getOrElse(fail("the deal must be projected"))

  test("the actor and every co-owner get the full decision with editing inputs") {
    val b = NegotiationFixture.board()
    val state = parkedDeal(b)
    Vector(b.actor, b.second, b.third).foreach { viewer =>
      val projection = view(state, viewer)
      assertEquals(projection.walkerWaiting, None, viewer.value)
      val decision = projection.walkerDecision.getOrElse(fail("owner decision"))
      assertEquals(decision.decisionId, dealId)
      assertEquals(decision.query.map(_.form), Some("negotiate"))
      val editing = deal(projection).editing.getOrElse(fail("editing"))
      assertEquals(editing.editableFavor, 5)
      assert(!editing.canAccept)
      assert(projection.legalControls.contains("resolveWalkerDecision"))
    }
  }

  test("a player outside the deal and the public view see it read-only") {
    val b = NegotiationFixture.board()
    val state = parkedDeal(b, who = Vector(b.second))
    val outsider = view(state, b.third)
    assertEquals(outsider.walkerDecision, None)
    val waiting = outsider.walkerWaiting.getOrElse(fail("waiting"))
    assertEquals(waiting.playerId, b.actor.value)
    assertEquals(waiting.coOwnerPlayerIds, Vector(b.second.value))
    assertEquals(deal(outsider).participantPlayerIds,
      Vector(b.actor.value, b.second.value))
    assertEquals(deal(outsider).editing, None)
    assert(!outsider.legalControls.contains("resolveWalkerDecision"))
    val public = projector.projectPublic("negotiation", LoadedGame(state, 30))
    assertEquals(deal(public).editing, None)
    assertEquals(public.walkerDecision, None)
  }

  test("terms show amounts to everyone but hide identities from everyone but their author") {
    val b = NegotiationFixture.board()
    val terms = NegotiationTerms(
      Vector(NegotiationTransfer(b.second, 3, Vector(b.actorRelic))),
      Vector(NegotiationDisclosure(b.second,
        NegotiationDisclosureRef.HeldRelic(b.actor, b.actorRelic))))
    val state = parkedDeal(b, Some(terms), Vector(b.second))
    val author = deal(view(state, b.actor))
    assertEquals(author.transfers.head.favor, 3)
    assertEquals(author.transfers.head.relicCount, 1)
    assertEquals(author.transfers.head.relics.map(_.cardId), Vector(b.actorRelic.value))
    assertEquals(author.disclosures.head.card.map(_.cardId), Some(b.actorRelic.value))
    val others = Vector(view(state, b.second), view(state, b.third),
      projector.projectPublic("negotiation", LoadedGame(state, 30)))
    others.foreach { projection =>
      val seen = deal(projection)
      assertEquals(seen.transfers.head.favor, 3)
      assertEquals(seen.transfers.head.relicCount, 1)
      assertEquals(seen.transfers.head.relics, Vector.empty)
      assertEquals(seen.disclosures.head.kind, "held-relic")
      assertEquals(seen.disclosures.head.recipientPlayerId, b.second.value)
      assertEquals(seen.disclosures.head.card, None)
    }
  }

  test("acceptances and the right to accept are projected") {
    val b = NegotiationFixture.board()
    val proposed = parkedDeal(b, Some(NegotiationTerms(
      Vector(NegotiationTransfer(b.second, 1, Vector.empty)))), Vector(b.second))
    assert(deal(view(proposed, b.second)).editing.exists(_.canAccept))
    val accepted = rules.resolveWalker(proposed, b.second, dealId, AcceptDeal)
      .getOrElse(fail("accept")).state
    val seen = deal(view(accepted, b.third))
    assertEquals(seen.acceptedPlayerIds, Vector(b.second.value))
    assert(!deal(view(accepted, b.second)).editing.exists(_.canAccept))
    assert(deal(view(accepted, b.actor)).editing.exists(_.canAccept))
  }

  test("the start control is offered while a candidate exists and not once parked") {
    val b = NegotiationFixture.board()
    assert(view(Ready(b.ready), b.actor).legalControls.contains("beginNegotiation"))
    assert(!view(Ready(NegotiationFixture.isolated(b).ready), b.actor)
      .legalControls.contains("beginNegotiation"))
    assert(!view(parkedDeal(b), b.actor).legalControls.contains("beginNegotiation"))
  }
}
```
(`LoadedGame(state, nextSequence)` and `GameProjector.project(gameId, loaded, viewer)` / `projectPublic(gameId, loaded)` are used the same way in the deleted Challenge parity suite. `GameProjection.legalControls` is the field the frontend reads as `value.legalControls`.)

Add to `ProjectionProtocolSuite.scala` a round-trip test:

```scala
  test("a negotiate query and a waiting deal round trip with and without editing") {
    val card = CardDetailsProjection("r1", "relic", "Relic One", orientation = Some("face-down"))
    val editing = NegotiationEditingProjection(5, Vector(card), Vector.empty,
      Vector(NegotiationSiteRelicProjection("s1", card)), canAccept = true)
    val deal = NegotiationDealProjection(Vector("red", "blue"), Vector("blue"),
      Vector(NegotiationTransferProjection("red", "blue", 3, 1, Vector(card))),
      Vector(NegotiationDisclosureProjection("red", "blue", "held-relic", None)),
      Some(editing))
    val query = DecisionQueryProjection("negotiate", Vector.empty,
      heading = Some("Negotiation"), deal = Some(deal))
    val waiting = WalkerWaitingProjection("red", Some("Negotiation"),
      Vector("blue"), Some(deal.copy(editing = None)))
    val carrying = projection.copy(
      walkerDecision = projection.walkerDecision.map(_.copy(query = Some(query))),
      walkerWaiting = Some(waiting))
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(carrying)),
      Right(carrying))
  }
```
(`projection` is the suite's existing sample; the neighbouring test at line 118-121 uses `projection.walkerDecision.map(_.copy(query = ...))` the same way.)

- [ ] **Step 2: Run to confirm they fail**

Run: `./sbtw "testOnly oathdigital.application.NegotiationDealProjectionSuite oathdigital.protocol.ProjectionProtocolSuite"`
Expected: FAIL to compile (`NegotiationDealProjection`, `deal`, `coOwnerPlayerIds` do not exist).

- [ ] **Step 3: DTOs and codecs**

In `ActionProjectionDtos.scala` add next to the other Negotiation DTOs:

```scala
/** What the viewer may put into the deal: present only for a participant. */
final case class NegotiationEditingProjection(editableFavor: Int,
    editableRelics: Vector[CardDetailsProjection],
    editableAdvisers: Vector[CardDetailsProjection],
    editableSiteRelics: Vector[NegotiationSiteRelicProjection],
    canAccept: Boolean)

/** A parked negotiation as one viewer sees it. Everyone sees the participants,
  * who has accepted, favor amounts, relic counts and faceup relic details, and
  * each disclosure's kind and recipient. Only an author sees the identity of
  * their own facedown relics and of information they promise. `editing` is
  * present only for a participant.
  */
final case class NegotiationDealProjection(participantPlayerIds: Vector[String],
    acceptedPlayerIds: Vector[String],
    transfers: Vector[NegotiationTransferProjection],
    disclosures: Vector[NegotiationDisclosureProjection],
    editing: Option[NegotiationEditingProjection] = None)
```
Add `deal: Option[NegotiationDealProjection] = None` as the last field of `DecisionQueryProjection` (form `"negotiate"` carries it), and change `WalkerWaitingProjection` to:

```scala
final case class WalkerWaitingProjection(playerId: String,
    heading: Option[String] = None,
    coOwnerPlayerIds: Vector[String] = Vector.empty,
    deal: Option[NegotiationDealProjection] = None)
```
In `ActionProjectionCodec.scala`, extract the transfer, disclosure and site-relic row codecs of `encodeNegotiation`/`decodeNegotiation` into private helpers and add the deal codecs:

```scala
  private def encodeTransfer(row: NegotiationTransferProjection): ujson.Value = ujson.Obj(
    "authorPlayerId" -> row.authorPlayerId, "recipientPlayerId" -> row.recipientPlayerId,
    "favor" -> row.favor, "relicCount" -> row.relicCount,
    "relics" -> encoded(row.relics)(encodeCard))
  private def encodeDisclosure(row: NegotiationDisclosureProjection): ujson.Value = ujson.Obj(
    "authorPlayerId" -> row.authorPlayerId, "recipientPlayerId" -> row.recipientPlayerId,
    "kind" -> row.kind, "card" -> option(row.card)(encodeCard))
  private def encodeSiteRelic(row: NegotiationSiteRelicProjection): ujson.Value = ujson.Obj(
    "siteId" -> row.siteId, "card" -> encodeCard(row.card))

  def encodeDeal(value: NegotiationDealProjection): ujson.Value = ujson.Obj(
    "participantPlayerIds" -> encoded(value.participantPlayerIds)(ujson.Str(_)),
    "acceptedPlayerIds" -> encoded(value.acceptedPlayerIds)(ujson.Str(_)),
    "transfers" -> encoded(value.transfers)(encodeTransfer),
    "disclosures" -> encoded(value.disclosures)(encodeDisclosure),
    "editing" -> option(value.editing)(editing => ujson.Obj(
      "editableFavor" -> editing.editableFavor,
      "editableRelics" -> encoded(editing.editableRelics)(encodeCard),
      "editableAdvisers" -> encoded(editing.editableAdvisers)(encodeCard),
      "editableSiteRelics" -> encoded(editing.editableSiteRelics)(encodeSiteRelic),
      "canAccept" -> editing.canAccept)))

  def decodeDeal(raw: ujson.Value, path: String): Result[NegotiationDealProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("participantPlayerIds", "acceptedPlayerIds", "transfers",
      "disclosures", "editing"), path)
    participants <- strings(value, "participantPlayerIds", path)
    accepted <- strings(value, "acceptedPlayerIds", path)
    transferRaws <- array(value, "transfers", path)
    transfers <- traverse(transferRaws, s"$path.transfers")(decodeTransfer)
    disclosureRaws <- array(value, "disclosures", path)
    disclosures <- traverse(disclosureRaws, s"$path.disclosures")(decodeDisclosure)
    editing <- optionalAbsent(value, "editing", path) { (rawEditing, child) => for {
      row <- obj(rawEditing, child)
      _ <- exact(row, Set("editableFavor", "editableRelics", "editableAdvisers",
        "editableSiteRelics", "canAccept"), child)
      favor <- int(row, "editableFavor", child)
      relicRaws <- array(row, "editableRelics", child)
      relics <- traverse(relicRaws, s"$child.editableRelics")(decodeCard)
      adviserRaws <- array(row, "editableAdvisers", child)
      advisers <- traverse(adviserRaws, s"$child.editableAdvisers")(decodeCard)
      siteRaws <- array(row, "editableSiteRelics", child)
      sites <- traverse(siteRaws, s"$child.editableSiteRelics")(decodeSiteRelic)
      canAccept <- bool(row, "canAccept", child)
    } yield NegotiationEditingProjection(favor, relics, advisers, sites, canAccept) }
  } yield NegotiationDealProjection(participants, accepted, transfers, disclosures, editing)
```
with `decodeTransfer`, `decodeDisclosure` and `decodeSiteRelic` lifted from the closures in the legacy `decodeNegotiation` (each `(raw: ujson.Value, child: String) => Result[...]`, keeping the same `exact` field sets). Then rewrite the legacy `encodeNegotiation`/`decodeNegotiation` to call the same helpers so nothing is duplicated (they are deleted in Task 10).

`encodeDecisionQuery` gains `"deal" -> option(value.deal)(encodeDeal)`; `decodeDecisionQuery` adds `"deal"` to its `exact` set and `deal <- optionalAbsent(value, "deal", path)(decodeDeal)` and passes it as the last constructor argument. `encodeWalkerWaiting`/`decodeWalkerWaiting`:

```scala
  def encodeWalkerWaiting(value: WalkerWaitingProjection): ujson.Value = ujson.Obj(
    "playerId" -> value.playerId, "heading" -> stringOption(value.heading),
    "coOwnerPlayerIds" -> encoded(value.coOwnerPlayerIds)(ujson.Str(_)),
    "deal" -> option(value.deal)(encodeDeal))
  def decodeWalkerWaiting(raw: ujson.Value, path: String): Result[WalkerWaitingProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("playerId", "heading", "coOwnerPlayerIds", "deal"), path)
    playerId <- string(value, "playerId", path)
    heading <- optionalString(value, "heading", path)
    coOwners <- strings(value, "coOwnerPlayerIds", path)
    deal <- optionalAbsent(value, "deal", path)(decodeDeal)
  } yield WalkerWaitingProjection(playerId, heading, coOwners, deal)
```

- [ ] **Step 4: The deal projector**

Create `src/main/scala/oathdigital/application/NegotiationDealProjector.scala`:

```scala
package oathdigital.application

import oathdigital.model._
import oathdigital.protocol.projection._

/** Projects a [[DecisionQuery.Negotiate]] for one viewer.
  *
  * Everyone sees what a non-author participant sees: who is in the deal, who
  * has accepted, favor amounts, relic counts and the details of faceup relics,
  * and the kind and recipient of each disclosure. Only an author sees the
  * identity of a facedown relic they offer or of information they promise.
  * Only a participant gets the editing inputs, and never another player's.
  */
private[application] final class NegotiationDealProjector(
    presentation: GamePresentationProjector) {

  def project(ready: ReadyGame, viewer: Option[PlayerId],
      query: DecisionQuery.Negotiate): NegotiationDealProjection = {
    val current = ready.game.current
    val transfers = query.participants.flatMap { author =>
      val owner = current.players.find(_.player == author).get
      query.terms(author).transfers.map { transfer =>
        NegotiationTransferProjection(author.value, transfer.recipient.value,
          transfer.favor, transfer.relics.size,
          transfer.relics.flatMap(id => owner.relics.find(_.id == id)).collect {
            case relic if viewer.contains(author) ||
                relic.orientation == Orientation.FaceUp =>
              presentation.cardDetails(relic.id, Some(relic.orientation),
                hidden = false)
          })
      }
    }
    val disclosures = query.participants.flatMap { author =>
      query.terms(author).disclosures.map { disclosure =>
        val visible = viewer.contains(author)
        def detail(id: CardId) = Option.when(visible)(presentation.cardDetails(
          id, Some(Orientation.FaceDown), hidden = false))
        val (kind, card) = disclosure.information match {
          case NegotiationDisclosureRef.Adviser(_, id) => "adviser" -> detail(id)
          case NegotiationDisclosureRef.HeldRelic(_, id) => "held-relic" -> detail(id)
          case NegotiationDisclosureRef.SiteRelic(_, id) => "site-relic" -> detail(id)
        }
        NegotiationDisclosureProjection(author.value,
          disclosure.recipient.value, kind, card)
      }
    }
    NegotiationDealProjection(query.participants.map(_.value),
      query.participants.filter(query.accepted).map(_.value), transfers,
      disclosures, viewer.filter(query.participants.contains)
        .map(editing(ready, query, _)))
  }

  private def editing(ready: ReadyGame, query: DecisionQuery.Negotiate,
      viewer: PlayerId): NegotiationEditingProjection = {
    val current = ready.game.current
    val player = current.players.find(_.player == viewer).get
    val own = query.bounds(viewer)
    NegotiationEditingProjection(own.maxFavor,
      own.relics.flatMap(id => player.relics.find(_.id == id)).map(relic =>
        presentation.cardDetails(relic.id, Some(relic.orientation),
          hidden = false)),
      own.disclosures.collect {
        case NegotiationDisclosureRef.Adviser(_, card) =>
          presentation.cardDetails(card, Some(Orientation.FaceDown),
            hidden = false)
      },
      own.disclosures.collect {
        case NegotiationDisclosureRef.SiteRelic(site, relic) => site -> relic
      }.flatMap { case (site, relic) =>
        current.map.sites.get(site).flatMap(_.relics.find(_.id == relic)).map(
          state => NegotiationSiteRelicProjection(site.value,
            presentation.cardDetails(relic, Some(state.orientation),
              hidden = false)))
      },
      query.acceptors.contains(viewer))
  }
}
```

- [ ] **Step 5: The walker projector**

In `WalkerDecisionProjector.scala`:
- Add the imports `NegotiationEditingProjection` is not needed; add `private val deals = new NegotiationDealProjector(presentation)` after the constructor parameters.
- `Parked` (companion, bottom of file) gains `owners: Set[PlayerId]`.
- `parkedPosition` computes it:

```scala
      awaited <- ProcedureWalker.awaitedPlayer(context.ready, tree, pending,
        powers)
      owners = ProcedureWalker.awaitedPlayers(context.ready, tree, pending,
        powers)
    } yield Parked(procedure, tree, pending, powers, awaited, owners)
```
- `project` gates on the owners and passes the viewer:

```scala
  def project(context: ScopedProjectionContext)
      : Option[WalkerDecisionProjection] = for {
    parked <- parkedPosition(context)
    if context.viewer.exists(parked.owners.contains)
    projection <- this.parked(parked.procedure, parked.tree, context.ready,
      parked.pending, parked.powers, parked.awaited, context.viewer)
  } yield projection
```
- `waiting` names every awaited player and carries the deal:

```scala
  def waiting(context: ScopedProjectionContext)
      : Option[WalkerWaitingProjection] = for {
    parked <- parkedPosition(context)
    if !context.viewer.exists(parked.owners.contains)
    _ <- this.parked(parked.procedure, parked.tree, context.ready,
      parked.pending, parked.powers, parked.awaited, context.viewer,
      previewed = false)
  } yield {
    val decide = ProcedureWalker.parkedDecide(context.ready, parked.tree,
      parked.pending, parked.powers)
    WalkerWaitingProjection(parked.awaited.value, decide.flatMap(_.query.heading),
      parked.owners.filter(_ != parked.awaited).toVector.map(_.value).sorted,
      decide.map(_.query).collect {
        case negotiate: DecisionQuery.Negotiate =>
          deals.project(context.ready, context.viewer, negotiate)
      })
  }
```
- `parked(...)` gains `viewer: Option[PlayerId]` after `awaited` and passes it to `queryProjection` in place of `Some(awaited)`:

```scala
  private def parked(procedure: ProcedureRef, tree: Operation,
      ready: ReadyGame, pending: PendingTree, powers: WalkerPowers,
      awaited: PlayerId, viewer: Option[PlayerId], previewed: Boolean = true)
```
and `queryProjection(ready, viewer, query, details)`. For every existing decision the viewer is the sole owner, so this changes nothing for them.
- In `queryProjection` replace the Task 4 placeholder:

```scala
      case negotiate: DecisionQuery.Negotiate =>
        Some(DecisionQueryProjection("negotiate", Vector.empty,
          heading = negotiate.heading,
          deal = Some(deals.project(ready, viewer, negotiate))))
```
Check `wc -l` stays under 800.

- [ ] **Step 6: The start control**

In `LegalActionProjector.scala` add next to `challengeStartable`:

```scala
  private def negotiationStartable(context: ScopedProjectionContext): Boolean =
    NegotiationProcedure.startable(catalog, context.ready,
      context.active.player, WalkerPowers.selected(walkerPowerCatalog, Vector.empty))
```
import `oathdigital.gameplay.actions.negotiation.NegotiationProcedure`, and replace the `beginNegotiation` entry (line ~167):

```scala
          Option.when(negotiationStartable(context))("beginNegotiation")
```
The legacy board-target selection `"negotiation"` in `boardTargetActions` stays until Task 9.

- [ ] **Step 7: Run**

Run: `./sbtw "testOnly oathdigital.application.NegotiationDealProjectionSuite oathdigital.protocol.ProjectionProtocolSuite oathdigital.application.WalkerDecisionProjectorSuite oathdigital.application.WalkerDecisionProjectionSuite oathdigital.application.WalkerDecisionQueryPowerSuite oathdigital.gameplay.NegotiationSuite oathdigital.application.GameApplicationServiceSuite"`
Expected: PASS. The legacy suites must still pass: the legacy `negotiation` field is untouched, and the `beginNegotiation` control still appears wherever a candidate exists (`legalParticipants` and `eligible` are the same rule).

- [ ] **Step 8: Commit**

```bash
git add -A src shared
git commit -m "feat(negotiation): project the deal to every viewer

The walker decision projection names every owner of a co-owned decision and
carries a Negotiate variant. Owners get the deal with their editing inputs,
everyone else the read-only deal at a non-author participant's redaction
level. The start control comes from the procedure's own dry run.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 7: The frontend start control and deal panel

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/NegotiationControls.scala`, `frontend/src/main/scala/oathdigital/frontend/NegotiationDealPanel.scala`, `frontend/src/test/scala/oathdigital/frontend/NegotiationControlsSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/NegotiationDealPanelSuite.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/package.scala` (two aliases), `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala` (two call sites)

**Interfaces:**
- Consumes: Task 6 (`NegotiationDealProjection`, `NegotiationEditingProjection`, `DecisionQueryProjection.deal`, `WalkerWaitingProjection.deal`), Task 4 (`DecisionAnswerWire.ProposeTermsWire`, `AcceptDealWire`, `DeclineDealWire`).
- Produces:
  - `NegotiationControls.render(value: GameProjection, canControl: Boolean, groups: ActionSections, submit: GameCommand => Unit): Unit`, offering `beginNegotiation` as `StartWalker("negotiation", Vector.empty)`.
  - `NegotiationDealPanel.render(value: GameProjection, presentation: ViewerPresentation, canControl: Boolean, panel: dom.Element, ui: ServerUiView): Unit`.
  - Aliases `NegotiationDealState`, `NegotiationEditingState`.

The legacy `NegotiationState` panel in `ActionDecisionRenderer` stays until Task 9. Both panels never show at once: a deal is either a legacy `pending` (`value.negotiation`) or a parked walker (`walkerDecision`/`walkerWaiting`).

- [ ] **Step 1: Write the failing controls test**

```scala
package oathdigital.frontend

import oathdigital.protocol.GameIntent
import org.scalajs.dom

/** The Negotiation start control at the DOM. */
class NegotiationControlsSuite extends munit.FunSuite {
  private def projection(controls: Vector[String]): GameProjection =
    GameProjection("game", 9L, "act-action-selection", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, controls, ready = true,
      completed = false, actionSelectionOpen = true)

  private def render(controls: Vector[String], canControl: Boolean = true)
      : (Vector[dom.html.Button], Vector[GameIntent]) = {
    var submitted = Vector.empty[GameIntent]
    val groups = new ServerUiSupport.ActionSections
    NegotiationControls.render(projection(controls), canControl, groups,
      command => submitted :+= command)
    val panel = dom.document.createElement("div")
    groups.appendTo(panel)
    val buttons = panel.querySelectorAll(".act-action").toVector
      .map(_.asInstanceOf[dom.html.Button])
    buttons.foreach(_.click())
    (buttons, submitted)
  }

  test("the control renders only when offered and starts Negotiation") {
    val (buttons, submitted) = render(Vector("beginNegotiation"))
    assertEquals(buttons.map(_.textContent), Vector("Negotiate (0 Supply)"))
    assertEquals(submitted, Vector[GameIntent](
      GameIntent.StartWalker("negotiation", Vector.empty)))
    assertEquals(render(Vector.empty)._1, Vector.empty)
    assert(render(Vector("beginNegotiation"), canControl = false)._1
      .forall(_.disabled))
  }

  test("Negotiation does not start through the modifier workflow") {
    assertEquals(ModifierWorkflow.action(
      GameIntent.StartWalker("negotiation", Vector.empty)), None)
  }
}
```

- [ ] **Step 2: Write the failing panel test**

```scala
package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, GameIntent => Intent,
  NegotiationTerms, NegotiationTransfer}
import org.scalajs.dom

/** The deal panel for a parked negotiation, as an owner and as a spectator. */
class NegotiationDealPanelSuite extends munit.FunSuite {
  private val presentation = ServerUiSupport.ViewerPresentation(
    showGameplayControls = true, None, None)
  // Faceup, so it is offered as a transfer only and not also as a disclosure.
  private val relic = CardDetails("r1", "relic", "Relic One",
    orientation = Some("face-up"))

  private def deal(canAccept: Boolean = true, editing: Boolean = true) =
    NegotiationDealState(Vector("red", "blue"), Vector("blue"),
      Vector(NegotiationTransferState("blue", "red", 2, 0, Vector.empty)),
      Vector(NegotiationDisclosureState("blue", "red", "held-relic", None)),
      Option.when(editing)(NegotiationEditingState(5, Vector(relic),
        Vector.empty, Vector.empty, canAccept)))

  private def owner(value: NegotiationDealState): GameProjection =
    GameProjection("game", 9L, "act-action-selection", Some("red"), Vector.empty,
      Vector.empty, Vector.empty, Vector("resolveWalkerDecision"), ready = true,
      completed = false, walkerDecision = Some(WalkerDecisionState("negotiation",
        "negotiation.deal", "decide", query = Some(DecisionQueryState("negotiate",
          Vector.empty, heading = Some("Negotiation"), deal = Some(value))))))

  private def spectator(value: NegotiationDealState): GameProjection =
    GameProjection("game", 9L, "act-action-selection", Some("red"), Vector.empty,
      Vector.empty, Vector.empty, Vector.empty, ready = true, completed = false,
      walkerWaiting = Some(WalkerWaitingState("red", Some("Negotiation"),
        Vector("blue"), Some(value))))

  private def render(projection: GameProjection, ui: RecordingView,
      canControl: Boolean = true, shows: Boolean = true): dom.Element = {
    val panel = dom.document.createElement("div")
    NegotiationDealPanel.render(projection, presentation.copy(
      showGameplayControls = shows), canControl, panel, ui)
    panel
  }

  private def one(root: dom.Element, selector: String): dom.Element = {
    val found = root.querySelectorAll(selector)
    assertEquals(found.length, 1, s"expected exactly one $selector")
    found(0).asInstanceOf[dom.Element]
  }

  test("an owner sees the deal and can save, accept and decline") {
    val ui = new RecordingView("game", "red")
    val panel = render(owner(deal()), ui)
    assertEquals(one(panel, "h2").textContent, "Negotiation")
    assertEquals(one(panel, ".negotiation-status").textContent,
      "red: reviewing · blue: accepted")
    assertEquals(one(panel, ".negotiation-transfer").textContent,
      "blue gives red: 2 favor, 0 relic(s)")
    assertEquals(one(panel, ".negotiation-disclosure").textContent,
      "blue promises red a held-relic disclosure")
    val favor = one(panel, "input[type=number]").asInstanceOf[dom.html.Input]
    favor.value = "3"
    one(panel, "input[type=checkbox]").asInstanceOf[dom.html.Input].checked = true
    one(panel, ".negotiation-save").asInstanceOf[dom.html.Button].click()
    one(panel, ".negotiation-accept").asInstanceOf[dom.html.Button].click()
    one(panel, ".negotiation-decline").asInstanceOf[dom.html.Button].click()
    assertEquals(ui.submitted, Vector[Intent](
      Intent.ResolveWalker("negotiation.deal", DecisionAnswerWire.ProposeTermsWire(
        NegotiationTerms(Vector(NegotiationTransfer("blue", 3, Vector("r1"))),
          Vector.empty))),
      Intent.ResolveWalker("negotiation.deal", DecisionAnswerWire.AcceptDealWire),
      Intent.ResolveWalker("negotiation.deal", DecisionAnswerWire.DeclineDealWire)))
  }

  test("accept is disabled until the engine says this player may accept") {
    val ui = new RecordingView("game", "red")
    val panel = render(owner(deal(canAccept = false)), ui)
    assert(one(panel, ".negotiation-accept").asInstanceOf[dom.html.Button].disabled)
    assert(!one(panel, ".negotiation-decline").asInstanceOf[dom.html.Button].disabled)
    val blocked = render(owner(deal()), ui, canControl = false)
    assert(one(blocked, ".negotiation-save").asInstanceOf[dom.html.Button].disabled)
  }

  test("a spectator sees the deal read-only, with no inputs or buttons") {
    val ui = new RecordingView("game", "green")
    val panel = render(spectator(deal(editing = false)), ui, shows = false)
    assertEquals(one(panel, ".negotiation-status").textContent,
      "red: reviewing · blue: accepted")
    assertEquals(panel.querySelectorAll("input").length, 0)
    assertEquals(panel.querySelectorAll("button").length, 0)
  }

  test("nothing renders when no negotiation is parked") {
    val ui = new RecordingView("game", "red")
    val empty = GameProjection("game", 9L, "act-action-selection", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false)
    assertEquals(render(empty, ui).childNodes.length, 0)
  }
}
```
(`protocol.NegotiationTerms/NegotiationTransfer` are the wire types in `oathdigital.protocol`; `NegotiationTransferState`/`NegotiationDisclosureState` are the projection aliases already in `package.scala`.)

- [ ] **Step 3: Run to confirm they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.NegotiationControlsSuite oathdigital.frontend.NegotiationDealPanelSuite"`
Expected: FAIL to compile (`NegotiationControls`, `NegotiationDealPanel`, `NegotiationDealState` do not exist).

- [ ] **Step 4: Aliases**

In `package.scala`, after `NegotiationState`:

```scala
  type NegotiationEditingState = protocol.projection.NegotiationEditingProjection
  val NegotiationEditingState = protocol.projection.NegotiationEditingProjection
  type NegotiationDealState = protocol.projection.NegotiationDealProjection
  val NegotiationDealState = protocol.projection.NegotiationDealProjection
```

- [ ] **Step 5: The start control**

Create `NegotiationControls.scala`:

```scala
package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand}
import ServerUiSupport._

/** The Act-phase start control for Negotiation. The engine offers it only
  * while its dry run starts, so this layer decides nothing: it draws what
  * `legalControls` names. The negotiators are chosen at the parked decision.
  */
private[frontend] object NegotiationControls {
  def render(value: GameProjection, canControl: Boolean, groups: ActionSections,
      submit: GameCommand => Unit): Unit =
    if (value.legalControls.contains("beginNegotiation")) {
      val node = button("Negotiate (0 Supply)", "act-action negotiation-action")
      node.disabled = !canControl
      node.onclick = _ => submit(GameCommand.StartWalker("negotiation", Vector.empty))
      groups.appendKind("negotiation", node)
    }
}
```
In `ActionDecisionRenderer.scala`, after `BannerControls.render(value, canControl, groups, submitCommand)` add `NegotiationControls.render(value, canControl, groups, submitCommand)`. Until Task 9 the legacy board-target "negotiation" group also renders; the control here starts the walker path.

- [ ] **Step 6: The deal panel**

Create `NegotiationDealPanel.scala`. It renders the summary for everyone, and the editor only for an owner. The editor is the legacy inline editor, with its helpers moved here and its three commands turned into `ResolveWalker` answers:

```scala
package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, GameIntent => GameCommand}
import org.scalajs.dom

/** The deal panel for a parked negotiation. Every viewer sees who is in the
  * deal, who has accepted, and the terms as their redaction level allows; only
  * an owner gets the editor, which answers the deal decision through
  * `ResolveWalker` (propose terms, accept, decline).
  */
private[frontend] object NegotiationDealPanel {
  import ServerUiSupport.{ViewerPresentation, button, protocolNegotiationTerms, text}

  private final case class DisclosureOffer(kind: String, card: CardDetails,
      siteId: Option[String])

  def render(value: GameProjection, presentation: ViewerPresentation,
      canControl: Boolean, panel: dom.Element, ui: ServerUiView): Unit =
    value.walkerDecision.flatMap(decision => decision.query
        .filter(_.form == "negotiate").flatMap(_.deal)
        .map(decision.decisionId -> _)) match {
      case Some((decisionId, deal)) =>
        summary(deal, panel)
        deal.editing.filter(_ => presentation.showGameplayControls).foreach(
          editor(decisionId, deal, _, canControl, panel, ui))
      case None => value.walkerWaiting.flatMap(_.deal).foreach(summary(_, panel))
    }

  private def summary(deal: NegotiationDealState, panel: dom.Element): Unit = {
    panel.appendChild(text("h2", "", "Negotiation"))
    panel.appendChild(text("p", "negotiation-status",
      deal.participantPlayerIds.map(id => s"$id: ${if (deal.acceptedPlayerIds
        .contains(id)) "accepted" else "reviewing"}").mkString(" · ")))
    deal.transfers.foreach(t => panel.appendChild(text("p", "negotiation-transfer",
      s"${t.authorPlayerId} gives ${t.recipientPlayerId}: ${t.favor} favor, " +
        s"${t.relicCount} relic(s)")))
    deal.disclosures.foreach(d => panel.appendChild(text("p",
      "negotiation-disclosure",
      s"${d.authorPlayerId} promises ${d.recipientPlayerId} a ${d.kind} disclosure")))
  }

  private def offers(editing: NegotiationEditingState): Vector[DisclosureOffer] =
    editing.editableAdvisers.map(DisclosureOffer("adviser", _, None)) ++
      editing.editableRelics.filter(_.orientation.contains("face-down"))
        .map(DisclosureOffer("held-relic", _, None)) ++
      editing.editableSiteRelics.map(entry =>
        DisclosureOffer("site-relic", entry.card, Some(entry.siteId)))

  private def input(kind: String): dom.html.Input = {
    val node = dom.document.createElement("input").asInstanceOf[dom.html.Input]
    node.`type` = kind
    node
  }

  private def editor(decisionId: String, deal: NegotiationDealState,
      editing: NegotiationEditingState, canControl: Boolean,
      panel: dom.Element, ui: ServerUiView): Unit = {
    val me = ui.currentPlayerId
    val favors = scala.collection.mutable.ArrayBuffer.empty[(String, dom.html.Input)]
    val relics = scala.collection.mutable.ArrayBuffer.empty[
      (String, String, dom.html.Input)]
    val disclosures = scala.collection.mutable.ArrayBuffer.empty[
      (String, DisclosureOffer, dom.html.Input)]
    deal.participantPlayerIds.filterNot(_ == me).foreach { recipient =>
      panel.appendChild(text("h3", "", s"Your terms for $recipient"))
      val favor = input("number")
      favor.min = "0"; favor.max = editing.editableFavor.toString
      favor.value = deal.transfers.find(t => t.authorPlayerId == me &&
        t.recipientPlayerId == recipient).map(_.favor).getOrElse(0).toString
      favor.setAttribute("aria-label", s"Favor offered to $recipient")
      panel.appendChild(favor); favors += recipient -> favor
      editing.editableRelics.foreach { relic =>
        val check = input("checkbox")
        check.setAttribute("aria-label", s"Offer ${relic.name} to $recipient")
        check.checked = deal.transfers.exists(t => t.authorPlayerId == me &&
          t.recipientPlayerId == recipient && t.relics.exists(_.cardId == relic.cardId))
        // A relic goes to one recipient at most.
        check.onchange = _ => if (check.checked) relics.foreach {
          case (other, otherRelic, otherCheck)
              if other != recipient && otherRelic == relic.cardId =>
            otherCheck.checked = false
          case _ => ()
        }
        panel.appendChild(check)
        panel.appendChild(text("span", "", s" ${relic.name} "))
        relics += ((recipient, relic.cardId, check))
      }
      offers(editing).foreach { offer =>
        val check = input("checkbox")
        check.setAttribute("aria-label",
          s"Promise ${offer.kind} disclosure of ${offer.card.name} to $recipient")
        check.checked = deal.disclosures.exists(d => d.authorPlayerId == me &&
          d.recipientPlayerId == recipient && d.kind == offer.kind &&
          d.card.exists(_.cardId == offer.card.cardId))
        panel.appendChild(check)
        panel.appendChild(text("span", "", s" Show ${offer.card.name} "))
        disclosures += ((recipient, offer, check))
      }
    }
    val save = button("Save Deal Changes", "negotiation-save")
    save.disabled = !canControl
    save.onclick = _ => {
      val terms = NegotiationTermsInput(favors.map { case (recipient, favor) =>
        NegotiationTransferInput(recipient, favor.value.toIntOption.getOrElse(0),
          relics.collect { case (`recipient`, relic, check) if check.checked =>
            relic }.toVector)
      }.toVector, disclosures.collect { case (recipient, offer, check)
          if check.checked => NegotiationDisclosureInput(recipient, offer.kind,
        Option.when(offer.kind != "site-relic")(me), offer.siteId,
        Option.when(offer.kind == "adviser")(offer.card.cardKind),
        offer.card.cardId) }.toVector)
      ui.submitCommand(GameCommand.ResolveWalker(decisionId,
        DecisionAnswerWire.ProposeTermsWire(protocolNegotiationTerms(terms))))
    }
    panel.appendChild(save)
    val accept = button("Accept Current Deal", "negotiation-accept")
    accept.disabled = !canControl || !editing.canAccept
    accept.onclick = _ => ui.submitCommand(GameCommand.ResolveWalker(
      decisionId, DecisionAnswerWire.AcceptDealWire))
    panel.appendChild(accept)
    val decline = button("End/Decline", "negotiation-decline")
    decline.disabled = !canControl
    decline.onclick = _ => ui.submitCommand(GameCommand.ResolveWalker(
      decisionId, DecisionAnswerWire.DeclineDealWire))
    panel.appendChild(decline)
  }
}
```
In `ActionDecisionRenderer.scala`, after `DistributePanelRenderer.render(value, presentation, canControl, panel, ui)` add:

```scala
   NegotiationDealPanel.render(value, presentation, canControl, panel, ui)
```

- [ ] **Step 7: Run**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.NegotiationControlsSuite oathdigital.frontend.NegotiationDealPanelSuite oathdigital.frontend.ServerModeUiSuite oathdigital.frontend.WalkerChoicePanelRenderSuite" "frontend/fastLinkJS"`
Expected: PASS. `ServerModeUiSuite` still exercises the legacy panel, which is unchanged.

- [ ] **Step 8: Commit**

```bash
git add -A frontend
git commit -m "feat(negotiation): frontend start control and deal panel

The start control comes from legalControls like the other Act controls. The
deal panel shows the deal to every viewer and gives owners the editor, which
answers the parked deal decision through ResolveWalker. The legacy panel is
still in place.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 8: Prove the walker Negotiation matches legacy

The differential parity test the spec requires before any deletion. It runs the same scripted deals through the legacy commands and the walker on the same fixture, and compares what they leave behind and, for participants, what each is shown mid-deal. Blocked-deal cases are out (that behaviour changed) and non-participant visibility is out (it is new and has its own test in Task 6). This suite is deleted with the legacy rules in Task 11.

**Files:**
- Create: `src/test/scala/oathdigital/gameplay/NegotiationParitySuite.scala`

**Interfaces:**
- Consumes: legacy `NegotiationCommand.{Begin, ReplaceTerms, Accept, Decline}` through `OathRules.handle` (still present), the walker through `OathRules.startWalker`/`resolveWalker`, `NegotiationFixture` (Task 5), `GameProjector.project`.
- Produces: nothing later tasks use.

- [ ] **Step 1: Write the suite**

```scala
package oathdigital.gameplay

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.NegotiationFixture.Board
import oathdigital.gameplay.actions.NegotiationCommand
import oathdigital.gameplay.actions.negotiation.NegotiationDeal
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseManyAnswer, DeclineDeal, ProposeTerms}
import oathdigital.model.OathState.Ready

/** The legacy Negotiation and the walker Negotiation, run on the same board
  * and compared on the state they leave and on what each participant is shown
  * while the deal is open. Parity is possible only in the states legacy
  * accepts, so every fixture is the first-game one.
  */
class NegotiationParitySuite extends munit.FunSuite {
  private val rules = new OathRules(catalog)
  private val projector = new GameProjector(catalog)

  private sealed trait Move { def by: PlayerId }
  private final case class Propose(by: PlayerId, terms: NegotiationTerms)
      extends Move
  private final case class Accept(by: PlayerId) extends Move
  private final case class Decline(by: PlayerId) extends Move

  private def ready(state: OathState): ReadyGame = state match {
    case Ready(value) => value
    case other => fail(s"expected a ready game, got $other")
  }

  private val legacyId = DecisionId("parity")

  private def legacyCommand(move: Move): NegotiationCommand = move match {
    case Propose(by, terms) => NegotiationCommand.ReplaceTerms(by, legacyId, terms)
    case Accept(by) => NegotiationCommand.Accept(by, legacyId)
    case Decline(by) => NegotiationCommand.Decline(by, legacyId)
  }

  private def legacy(b: Board, who: Vector[PlayerId],
      script: Vector[Move]): OathTransition = {
    val begun = rules.handle(Ready(b.ready),
      NegotiationCommand.Begin(b.actor, legacyId, who))
      .getOrElse(fail("legacy Begin must succeed"))
    script.foldLeft(begun) { (current, move) =>
      rules.handle(current.state, legacyCommand(move))
        .getOrElse(fail(s"legacy $move must succeed"))
    }
  }

  private def walkerOpen(b: Board, who: Vector[PlayerId]): OathTransition = {
    val started = rules.startWalker(Ready(b.ready), ActionRef.Negotiation,
      b.actor).getOrElse(fail("walker start must succeed"))
    if (NegotiationDeal.eligible(b.ready, b.actor).size < 2) started
    else rules.resolveWalker(started.state, b.actor,
      NegotiationDeal.negotiatorsDecisionId, ChooseManyAnswer(
        who.map(DecisionOptionRef.Player(_))))
      .getOrElse(fail("walker negotiators must be accepted"))
  }

  private def walker(b: Board, who: Vector[PlayerId],
      script: Vector[Move]): OathTransition =
    script.foldLeft(walkerOpen(b, who)) { (current, move) =>
      val answer: DecisionAnswer = move match {
        case Propose(_, terms) => ProposeTerms(terms)
        case Accept(_) => AcceptDeal
        case Decline(_) => DeclineDeal
      }
      rules.resolveWalker(current.state, move.by,
        NegotiationDeal.dealDecisionId, answer)
        .getOrElse(fail(s"walker $move must be accepted"))
    }

  private def assertSameOutcome(b: Board, who: Vector[PlayerId],
      script: Vector[Move]): Unit = {
    val expected = legacy(b, who, script)
    val actual = walker(b, who, script)
    val (x, y) = (ready(expected.state), ready(actual.state))
    assertEquals(y.game.current.players, x.game.current.players)
    assertEquals(y.game.current.map, x.game.current.map)
    assertEquals(y.game.current.banners, x.game.current.banners)
    assertEquals(y.game.current.turn, x.game.current.turn)
    assertEquals(y.knowledge, x.knowledge)
    assertEquals(y.banks, x.banks)
    assertEquals(y.game.current.pending, None)
    assertEquals(y.game.current.walkerPending, None)
    assertEquals(actual.continue, expected.continue)
    (b.players.map(_.player)).foreach(viewer => assertEquals(
      projector.project("parity", LoadedGame(actual.state, 30), viewer),
      projector.project("parity", LoadedGame(expected.state, 30), viewer)))
    assertEquals(projector.projectPublic("parity", LoadedGame(actual.state, 30)),
      projector.projectPublic("parity", LoadedGame(expected.state, 30)))
  }

  private def gift(to: PlayerId, favor: Int, relics: Vector[RelicId] = Vector.empty) =
    NegotiationTerms(Vector(NegotiationTransfer(to, favor, relics)))

  test("a bilateral favor and relic transfer") {
    val b = NegotiationFixture.board()
    assertSameOutcome(b, Vector(b.second), Vector(
      Propose(b.actor, gift(b.second, 3, Vector(b.actorRelic))),
      Accept(b.second), Accept(b.actor)))
  }

  test("three players, a replaced term clearing consent, any acceptance order") {
    val b = NegotiationFixture.board()
    assertSameOutcome(b, Vector(b.second, b.third), Vector(
      Propose(b.actor, gift(b.second, 1)), Accept(b.second),
      Propose(b.third, gift(b.actor, 2)), Accept(b.third), Accept(b.second),
      Accept(b.actor)))
  }

  test("a disclosure deal grants selective durable knowledge") {
    val b = NegotiationFixture.board()
    val adviser = b.players.head.advisers.head.id.asInstanceOf[WorldCardId]
    val terms = NegotiationTerms(disclosures = Vector(
      NegotiationDisclosure(b.second,
        NegotiationDisclosureRef.Adviser(b.actor, adviser)),
      NegotiationDisclosure(b.second,
        NegotiationDisclosureRef.SiteRelic(b.site, b.siteRelic))))
    assertSameOutcome(b, Vector(b.second, b.third), Vector(
      Propose(b.actor, terms), Accept(b.actor), Accept(b.second),
      Accept(b.third)))
  }

  test("information for assets: a held-relic disclosure for favor") {
    val b = NegotiationFixture.board()
    val theirs = NegotiationTerms(disclosures = Vector(NegotiationDisclosure(
      b.actor, NegotiationDisclosureRef.HeldRelic(b.second, b.otherRelic))))
    assertSameOutcome(b, Vector(b.second), Vector(
      Propose(b.actor, gift(b.second, 2)), Propose(b.second, theirs),
      Accept(b.actor), Accept(b.second)))
  }

  test("a decline applies nothing and runs the action boundary") {
    val b = NegotiationFixture.board()
    assertSameOutcome(b, Vector(b.second), Vector(
      Propose(b.actor, gift(b.second, 3)), Decline(b.second)))
  }

  test("what each participant is shown mid-deal matches") {
    val b = NegotiationFixture.board()
    val terms = NegotiationTerms(
      Vector(NegotiationTransfer(b.second, 3, Vector(b.actorRelic))),
      Vector(NegotiationDisclosure(b.second,
        NegotiationDisclosureRef.HeldRelic(b.actor, b.actorRelic))))
    val who = Vector(b.second, b.third)
    val script = Vector[Move](Propose(b.actor, terms), Accept(b.second))
    val old = legacy(b, who, script).state
    val now = walker(b, who, script).state
    Vector(b.actor, b.second, b.third).foreach { viewer =>
      val before = projector.project("parity", LoadedGame(old, 30), viewer)
        .negotiation.getOrElse(fail("legacy projects the deal to participants"))
      val after = projector.project("parity", LoadedGame(now, 30), viewer)
        .walkerDecision.flatMap(_.query).flatMap(_.deal)
        .getOrElse(fail("the walker projects the deal to owners"))
      assertEquals(after.participantPlayerIds, before.participantPlayerIds)
      assertEquals(after.acceptedPlayerIds, before.acceptedPlayerIds)
      assertEquals(after.transfers, before.transfers)
      assertEquals(after.disclosures, before.disclosures)
      val editing = after.editing.getOrElse(fail("participants get editing"))
      assertEquals((editing.editableFavor, editing.editableRelics,
        editing.editableAdvisers, editing.editableSiteRelics),
        (before.editableFavor, before.editableRelics, before.editableAdvisers,
          before.editableSiteRelics))
    }
  }

  test("both paths refuse an over-budget proposal, an empty accept and an outsider") {
    val b = NegotiationFixture.board()
    val who = Vector(b.second)
    val begun = rules.handle(Ready(b.ready),
      NegotiationCommand.Begin(b.actor, legacyId, who)).toOption.get.state
    assert(rules.handle(begun, legacyCommand(Propose(b.actor, gift(b.second, 6)))).isLeft)
    assert(rules.handle(begun, legacyCommand(Accept(b.second))).isLeft)
    assert(rules.handle(begun, legacyCommand(Propose(b.third, gift(b.actor, 1)))).isLeft)
    val parked = walkerOpen(b, who).state
    def say(by: PlayerId, answer: DecisionAnswer) = rules.resolveWalker(parked,
      by, NegotiationDeal.dealDecisionId, answer)
    assert(say(b.actor, ProposeTerms(gift(b.second, 6))).isLeft)
    assert(say(b.second, AcceptDeal).isLeft)
    assert(say(b.third, ProposeTerms(gift(b.actor, 1))).isLeft)
  }
}
```

- [ ] **Step 2: Run**

Run: `./sbtw "testOnly oathdigital.gameplay.NegotiationParitySuite"`
Expected: PASS. A failure here means the walker and legacy disagree, so decide which one is wrong before touching either. Two things to check first if the outcome comparison fails: the order in which transfers execute (legacy iterates `terms.toVector`, the walker iterates participants in table order, and the results agree unless one author's transfer depends on another's), and the boundary events (legacy `completeAction` versus the walker's `runsActionBoundary`).

- [ ] **Step 3: Full gate and commit**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS.

```bash
git add src/test/scala/oathdigital/gameplay/NegotiationParitySuite.scala
git commit -m "test(negotiation): prove legacy and walker Negotiation agree

Scripted deals through both paths on the same board: transfers, replaced terms
clearing consent, disclosure knowledge, decline and the action boundary, plus
what each participant is shown mid-deal and what both refuse.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 9: Retire the legacy client surface

The parity suite proved the walker path, so the ways a client reached the legacy one go, top layer first so every commit builds: (1) the server stops offering the legacy start, (2) the frontend drops its legacy panel and start, (3) the commands, intents and mapper go. The legacy projection is Task 10 and the legacy rules are Task 11.

**Files (by step):**
- 9.1: `src/main/scala/oathdigital/application/LegalActionProjector.scala:257-275`, `src/test/scala/oathdigital/application/NegotiationDealProjectionSuite.scala`
- 9.2: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala`, `ServerUiSupport.scala`, `package.scala`; tests `frontend/src/test/scala/oathdigital/frontend/ServerModeUiSuite.scala`, `HttpGameClientSuite.scala`, `ProtocolTestCommands.scala`, `NegotiationDealPanelSuite.scala`
- 9.3: `src/main/scala/oathdigital/application/GameCommands.scala:35-42`, `Authorization.scala:79-87`, `GameApplicationService.scala:12,401-409`, `GameIntentMapper.scala:28-31`, `shared/src/main/scala/oathdigital/protocol/CommandIntents.scala:18-22`, `CommandIntentCodec.scala:17-20,59`, `CommandIntentDecoders.scala:32-43`; tests `shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala`, `src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala`, `PendingWalkerInvariantSuite.scala`, `src/test/scala/oathdigital/server/AuthenticatedGameRoutesSuite.scala`

**Interfaces:**
- Consumes: Tasks 4-7.
- Produces: nothing new. After 9.3 a client can only start Negotiation with `StartWalker("negotiation")` and answer it with `ResolveWalker`.

#### 9.1 The server stops offering the legacy start

- [ ] **Step 1: Write the failing test**

Add to `NegotiationDealProjectionSuite`:

```scala
  test("Negotiation is offered as a start control, not as a board-target selection") {
    val b = NegotiationFixture.board()
    val projection = view(Ready(b.ready), b.actor)
    assert(projection.legalControls.contains("beginNegotiation"))
    assert(!projection.boardTargetActions.exists(_.kind == "negotiation"))
  }
```

- [ ] **Step 2: Run to confirm it fails**

Run: `./sbtw "testOnly oathdigital.application.NegotiationDealProjectionSuite"`
Expected: FAIL, the projection still carries a `negotiation` board-target action.

- [ ] **Step 3: Remove the selection**

In `LegalActionProjector.boardTargetActions` delete the four-line `val negotiators = ...` binding and the trailing `selection("negotiation", "Choose one or more co-located negotiators", negotiators, minimum = 1, maximum = negotiators.size)` argument, closing the `Vector(...)` after the `campaign-raid` selection:

```scala
      selection("campaign-raid", "Choose Raid targets", raid,
        Option.when(raid.nonEmpty)(BoardTargetFormationProjection(
          oathdigital.gameplay.actions.Campaign.MinimumForce, player.board.warbands,
          player.board.warbands, oathdigital.gameplay.actions.Campaign.SupplyCost)),
        Option.when(raid.nonEmpty)(1).getOrElse(0), raid.size,
        raid.headOption.map(_.target).toVector)).flatten
```
The `Player` target kind in `BoardTargetRefProjection` stays: Campaign or other selections may use it, and removing it is not this slice's job. `git grep -n "BoardTargetRefProjection.Player\b" -- src/main` shows the users.

- [ ] **Step 4: Run and commit**

Run: `./sbtw "testOnly oathdigital.application.NegotiationDealProjectionSuite oathdigital.application.GameApplicationServiceSuite oathdigital.gameplay.NegotiationSuite"`
Expected: PASS.

```bash
git add -A src
git commit -m "refactor(negotiation): stop offering the legacy board-target start

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

#### 9.2 The frontend drops the legacy panel and start

- [ ] **Step 1: Move the legacy editor tests onto the new panel (failing until step 3 only if they exercise removed helpers; they exercise the new panel, so they pass now)**

Add to `NegotiationDealPanelSuite` (they replace the two `ServerModeUiSuite` tests of the deleted helpers, and add the JSON decode coverage below):

```scala
  private def threeWay(editing: NegotiationEditingState,
      transfers: Vector[NegotiationTransferState] = Vector.empty,
      disclosures: Vector[NegotiationDisclosureState] = Vector.empty) =
    NegotiationDealState(Vector("red", "blue", "yellow"), Vector.empty,
      transfers, disclosures, Some(editing))

  private def box(panel: dom.Element, label: String): dom.html.Input =
    one(panel, s"""input[aria-label="$label"]""").asInstanceOf[dom.html.Input]

  test("the editor restores only the viewer's own relic and disclosure selections") {
    val adviser = CardDetails("D1", "denizen", "Hidden Adviser")
    val deal = threeWay(NegotiationEditingState(3, Vector(relic),
      Vector(adviser), Vector.empty, canAccept = false),
      Vector(NegotiationTransferState("red", "blue", 0, 1, Vector(relic))),
      Vector(NegotiationDisclosureState("red", "yellow", "adviser", Some(adviser))))
    val panel = render(owner(deal), new RecordingView("game", "red"))
    assert(box(panel, "Offer Relic One to blue").checked)
    assert(!box(panel, "Offer Relic One to yellow").checked)
    assert(box(panel,
      "Promise adviser disclosure of Hidden Adviser to yellow").checked)
    assert(!box(panel,
      "Promise adviser disclosure of Hidden Adviser to blue").checked)
  }

  test("offering a relic to one recipient clears it for another") {
    val deal = threeWay(NegotiationEditingState(3, Vector(relic), Vector.empty,
      Vector.empty, canAccept = false))
    val panel = render(owner(deal), new RecordingView("game", "red"))
    val blue = box(panel, "Offer Relic One to blue")
    val yellow = box(panel, "Offer Relic One to yellow")
    blue.checked = true
    yellow.checked = true
    yellow.dispatchEvent(new dom.Event("change"))
    assert(!blue.checked)
    assert(yellow.checked)
  }

  test("disclosures are offered only for information the author can inspect") {
    val faceUp = CardDetails("R1", "relic", "Public Relic",
      orientation = Some("face-up"))
    val faceDown = CardDetails("R2", "relic", "Secret Relic",
      orientation = Some("face-down"))
    val adviser = CardDetails("D1", "denizen", "Hidden Adviser")
    val siteRelic = CardDetails("R3", "relic", "Bone Dice")
    val editing = NegotiationEditingState(3, Vector(faceUp, faceDown),
      Vector(adviser), Vector(NegotiationSiteRelicState("site:broken-peaks",
        siteRelic)), canAccept = false)
    val deal = NegotiationDealState(Vector("red", "blue"), Vector.empty,
      Vector.empty, Vector.empty, Some(editing))
    val ui = new RecordingView("game", "red")
    val panel = render(owner(deal), ui)
    val labels = panel.querySelectorAll("input[type=checkbox]").toVector
      .map(_.getAttribute("aria-label")).filter(_.startsWith("Promise"))
    assertEquals(labels, Vector(
      "Promise adviser disclosure of Hidden Adviser to blue",
      "Promise held-relic disclosure of Secret Relic to blue",
      "Promise site-relic disclosure of Bone Dice to blue"))
    box(panel, "Promise site-relic disclosure of Bone Dice to blue")
      .checked = true
    one(panel, ".negotiation-save").asInstanceOf[dom.html.Button].click()
    assertEquals(ui.submitted, Vector[Intent](Intent.ResolveWalker(
      "negotiation.deal", DecisionAnswerWire.ProposeTermsWire(NegotiationTerms(
        Vector(NegotiationTransfer("blue", 0, Vector.empty)),
        Vector(oathdigital.protocol.NegotiationDisclosure("blue",
          oathdigital.protocol.NegotiationInformation.SiteRelic(
            "site:broken-peaks", "R3"))))))))
  }
```
(`NegotiationTransfer("blue", 0, Vector.empty)` is there because the editor sends a row per recipient, as legacy did.)

In `HttpGameClientSuite` replace the legacy test `"Negotiation projection decodes redacted ledger and encodes authored terms"` with:

```scala
  test("a projected deal decodes for a spectator and a proposal encodes") {
    val card = """{"cardId":"R1","cardKind":"relic","name":"Old Crown","suit":null,"restrictions":null,"rulesText":null,"orientation":"face-down","side":null,"favor":0,"secrets":0,"relicValue":2,"defense":1,"hidden":false}"""
    val deal = s"""{"participantPlayerIds":["red-exile","blue-exile"],"acceptedPlayerIds":["blue-exile"],"transfers":[{"authorPlayerId":"red-exile","recipientPlayerId":"blue-exile","favor":2,"relicCount":1,"relics":[$card]}],"disclosures":[{"authorPlayerId":"blue-exile","recipientPlayerId":"red-exile","kind":"adviser","card":null}],"editing":null}"""
    val waiting = s"""{"playerId":"red-exile","heading":"Negotiation","coOwnerPlayerIds":["blue-exile"],"deal":$deal}"""
    val json = projectionJson(sequence = 51, phase = "act-action-selection",
      ready = true, completed = false, choices = false).replace(
      "\"pendingCardDecision\":null",
      s"\"pendingCardDecision\":null,\"walkerWaiting\":$waiting")
    val decoded = GameJson.decodeProjection(json).toOption.get.walkerWaiting.get
    assertEquals(decoded.coOwnerPlayerIds, Vector("blue-exile"))
    val seen = decoded.deal.get
    assertEquals(seen.acceptedPlayerIds, Vector("blue-exile"))
    assertEquals(seen.disclosures.head.card, None)
    assertEquals(seen.editing, None)
    val terms = NegotiationTermsInput(Vector(NegotiationTransferInput(
      "blue-exile", 2, Vector("R1"))), Vector.empty)
    val encoded = GameJson.encodeCommand(51, GameIntent.ResolveWalker(
      "negotiation.deal", DecisionAnswerWire.ProposeTermsWire(
        ServerUiSupport.protocolNegotiationTerms(terms))))
    assert(encoded.contains("propose-terms"))
    assert(encoded.contains("\"relicIds\":[\"R1\"]"))
  }
```

- [ ] **Step 2: Run the new tests (they pass against the new panel)**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.NegotiationDealPanelSuite oathdigital.frontend.HttpGameClientSuite"`
Expected: the new tests PASS. The legacy test in `HttpGameClientSuite` is gone in the same edit.

- [ ] **Step 3: Delete the legacy frontend code and its tests**

- `ActionDecisionRenderer.scala`: delete the whole `value.negotiation match { case Some(deal) if showNegotiationControls(value, presentation) => ... case _ => () }` block (about 80 lines, from `value.negotiation match {` to its closing brace before `value.campaign.filter(...)`), and any import that only it used.
- `ServerUiSupport.scala`: delete `negotiationRelicChecked`, `negotiationDisclosureChecked`, `negotiationRelicCompetes`, `NegotiationDisclosureOffer`, `negotiationDisclosureOffers`, `showNegotiationControls`; the two `viewerPresentation` branches that read `value.negotiation` and `value.negotiationWaiting` (the `Negotiation in progress.` and `Waiting for the negotiation to finish.` ones); and the `case ("negotiation", players) if players.nonEmpty && ...` arm of `commandForSelection`. Keep `protocolNegotiationTerms`.
- `package.scala`: delete `type NegotiationState` and `val NegotiationState`.
- `ServerModeUiSuite.scala`: delete the two tests `"Negotiation editor restores only authored relic and disclosure selections"` and `"Negotiation editor offers disclosures only for inspectable information"`, delete the `val negotiation = BoardTargetAction("negotiation", ...)` binding and its `assertEquals(... GameCommand.BeginNegotiation ...)` (inside the Campaign selection test, leaving that test's other assertions), and delete the test `"Negotiation participants control the procedure regardless of active turn"`. A co-owner's controls now come from `walkerDecision`, which the existing off-turn owner test in the same file covers.
- `ProtocolTestCommands.scala`: delete `BeginNegotiation` and `ReplaceNegotiationTerms` (and any `AcceptNegotiation`/`DeclineNegotiation` helper).

- [ ] **Step 4: Run and commit**

Run: `./sbtw "frontend/test" "frontend/fastLinkJS"`
Expected: PASS.

```bash
git add -A frontend
git commit -m "refactor(negotiation): drop the legacy frontend panel and start

The deal panel, the start control and the ResolveWalker answers replace the
legacy editor, the board-target start and the four Negotiation commands.
The legacy editor's tests move onto the new panel.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

#### 9.3 The commands, intents and mapper go

- [ ] **Step 1: Migrate the tests that use the four commands (they fail to compile once the commands go, so migrate first and see the old ones pass)**

`shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala`: delete the five `examples` entries (`BeginNegotiation`, `ReplaceNegotiationTerms`, `AcceptNegotiation`, `DeclineNegotiation`) and replace the duplicate-participants assertion (the `beginNegotiation` JSON near line 111) with a duplicate-recipient check on the new answer:

```scala
    val duplicateRecipient = """{"expectedNextSequence":0,"intent":{"type":"resolveWalker","decisionId":"negotiation.deal","payload":{"kind":"propose-terms","terms":{"transfers":[{"recipientPlayerId":"p2","favor":1,"relicIds":[]},{"recipientPlayerId":"p2","favor":2,"relicIds":[]}],"disclosures":[]}}}}"""
    assert(ActorlessCommandCodec.decode(duplicateRecipient).isLeft)
```
(Use the same `decode` call and `duplicate` variable style the neighbouring assertion uses; the point is that terms with two rows for one recipient still fail to decode.)

`src/test/scala/oathdigital/application/PendingWalkerInvariantSuite.scala`: delete the four `GameCommand.*Negotiation` entries from `everyCommand`, and the `decision` binding if nothing else uses it.

`src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala`: replace the test `"HSQL reopen preserves completed Negotiation disclosure knowledge"` with the two walker tests below (add `oathdigital.gameplay.actions.negotiation.NegotiationDeal` and `oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseManyAnswer, ProposeTerms}` to the imports):

```scala
  private def negotiationOpened(service: GameApplicationService, gameId: String)
      : (LoadedGame, PlayerId, PlayerId, WorldCardId) = {
    val setup = execute(service, gameId)
    val Ready(ready) = setup.state: @unchecked
    val actor = ready.game.current.turn.activePlayer
    val other = ready.game.current.players.find(_.player != actor).get
    val act = service.handle(gameId, setup.nextSequence,
      GameCommand.EndWake(actor)).toOption.get
    val traveled = service.handle(gameId, act.nextSequence,
      GameCommand.StartWalker(ActionRef.Travel, StartPayload(actor,
        Vector.empty, Vector(DecisionOptionRef.Site(other.pawnSite.get)))))
      .toOption.get
    val adviser = ready.game.current.players.find(_.player == actor).get
      .advisers.head.id.asInstanceOf[WorldCardId]
    val started = service.handle(gameId, traveled.nextSequence,
      GameCommand.StartWalker(ActionRef.Negotiation, StartPayload(actor)))
      .fold(error => fail(s"Negotiation must start: $error"), identity)
    val Ready(afterTravel) = traveled.state: @unchecked
    val opened =
      if (NegotiationDeal.eligible(afterTravel, actor).size < 2) started
      else service.handle(gameId, started.nextSequence,
        GameCommand.ResolveWalker(actor, TreeDecision(
          NegotiationDeal.negotiatorsDecisionId, ChooseManyAnswer(
            Vector(DecisionOptionRef.Player(other.player))))))
        .fold(error => fail(s"negotiators must be accepted: $error"), identity)
    (opened, actor, other.player, adviser)
  }

  private def say(service: GameApplicationService, gameId: String,
      from: LoadedGame, by: PlayerId, answer: DecisionAnswer) =
    service.handle(gameId, from.nextSequence, GameCommand.ResolveWalker(by,
      TreeDecision(NegotiationDeal.dealDecisionId, answer)))

  test("HSQL reopen preserves a parked Negotiation deal") {
    val path = Files.createTempDirectory("oathdigital-negotiation-parked-")
      .resolve("journal")
    val gameId = "game-hsql-negotiation-parked"
    val first = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    val (parked, actor, other, adviser) = try {
      val service = new GameApplicationService(catalog, first)
      val (opened, actor, other, adviser) = negotiationOpened(service, gameId)
      val terms = NegotiationTerms(disclosures = Vector(NegotiationDisclosure(
        other, NegotiationDisclosureRef.Adviser(actor, adviser))))
      (say(service, gameId, opened, actor, ProposeTerms(terms))
        .fold(error => fail(s"terms must persist: $error"), identity),
        actor, other, adviser)
    } finally first.close()
    val reopened = OwnedHsqldbEventStreamRepository.open(path).fold(
      error => fail(s"failed to reopen Negotiation repository: $error"), identity)
    try {
      val loaded = new GameApplicationService(catalog, reopened)
        .load(gameId).toOption.flatten.get
      assertEquals(loaded.state, parked.state)
      val seen = new GameProjector(catalog).project(gameId, loaded, other)
        .walkerDecision.flatMap(_.query).flatMap(_.deal).get
      assertEquals(seen.disclosures.map(d => (d.authorPlayerId, d.card)),
        Vector((actor.value, None)))
    } finally reopened.close()
  }

  test("HSQL reopen preserves completed Negotiation disclosure knowledge") {
    val path = Files.createTempDirectory("oathdigital-negotiation-reopen-")
      .resolve("journal")
    val gameId = "game-hsql-negotiation"
    val first = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    val (completed, actor, other, adviser) = try {
      val service = new GameApplicationService(catalog, first)
      val (opened, actor, other, adviser) = negotiationOpened(service, gameId)
      val terms = NegotiationTerms(disclosures = Vector(NegotiationDisclosure(
        other, NegotiationDisclosureRef.Adviser(actor, adviser))))
      val changed = say(service, gameId, opened, actor, ProposeTerms(terms))
        .fold(error => fail(s"failed to persist Negotiation terms: $error"), identity)
      assertEquals(say(service, gameId, opened, actor, AcceptDeal).left.toOption,
        Some(GameApplicationError.StaleClientPosition(
          opened.nextSequence, changed.nextSequence)))
      val actorAccepted = say(service, gameId, changed, actor, AcceptDeal)
        .toOption.get
      val completed = say(service, gameId, actorAccepted, other, AcceptDeal)
        .toOption.get
      (completed, actor, other, adviser)
    } finally first.close()
    val reopened = OwnedHsqldbEventStreamRepository.open(path).fold(
      error => fail(s"failed to reopen Negotiation repository: $error"), identity)
    try {
      val loaded = new GameApplicationService(catalog, reopened)
        .load(gameId).toOption.flatten.get
      assertEquals(loaded.state, completed.state)
      val projector = new GameProjector(catalog)
      assertEquals(projector.project(gameId, loaded, actor).walkerDecision, None)
      val recipientBoard = projector.project(gameId, loaded, other).playerBoards
        .find(_.playerId == actor.value).get
      assert(recipientBoard.advisers.exists(card =>
        card.cardId == adviser.value && !card.hidden))
      val publicBoard = projector.projectPublic(gameId, loaded).playerBoards
        .find(_.playerId == actor.value).get
      assert(publicBoard.advisers.forall(card =>
        card.cardId != adviser.value || card.hidden))
      assertEquals(projector.projectPublic(gameId, loaded).walkerWaiting, None)
    } finally reopened.close()
  }
```
(`LoadedGame` here is the service's `load` result, and `handle` returns a value with `state` and `nextSequence`. If `handle`'s result type is not `LoadedGame`, type `from` as whatever `handle` returns; the suite's other tests show the exact name. Leave the `assert(!view.negotiationWaiting)` near line 664 alone: the field goes in Task 10, which deletes that assertion.)

`src/test/scala/oathdigital/server/AuthenticatedGameRoutesSuite.scala`: rewrite the Negotiation route test's command block. Each command's next expected sequence comes from the previous response, since a walker command appends several events:

```scala
    try {
      def sequenceOf(response: java.net.http.HttpResponse[String]): Long =
        ujson.read(response.body())("nextSequence").num.toLong
      var sequence = allEvents.size.toLong
      def send(user: UserId, intent: ujson.Obj) = post(client, base + "/commands",
        user.value, ujson.write(ujson.Obj("expectedNextSequence" ->
          ujson.Num(sequence.toDouble), "intent" -> intent)))
      def answer(decision: String, payload: ujson.Obj) = ujson.Obj(
        "type" -> "resolveWalker", "decisionId" -> decision, "payload" -> payload)
      val begin = send(actorUser, ujson.Obj("type" -> "startWalker",
        "action" -> "negotiation", "modifiers" -> ujson.Arr(),
        "startArgs" -> ujson.Arr()))
      assertEquals(begin.statusCode(), 200, begin.body()); sequence = sequenceOf(begin)
      if (ujson.read(begin.body())("walkerDecision")("decisionId").str ==
          "negotiation.negotiators") {
        val chosen = send(actorUser, answer("negotiation.negotiators",
          ujson.Obj("kind" -> "choose-many", "options" -> ujson.Arr(ujson.Obj(
            "optionKind" -> "player", "optionId" -> other.player.value)))))
        assertEquals(chosen.statusCode(), 200, chosen.body()); sequence = sequenceOf(chosen)
      }
      val outsiderAttempt = send(outsider, answer("negotiation.deal",
        ujson.Obj("kind" -> "decline-deal")))
      assertEquals(outsiderAttempt.statusCode(), 403)
      val replace = send(otherUser, answer("negotiation.deal", ujson.Obj(
        "kind" -> "propose-terms", "terms" -> ujson.Obj("transfers" ->
          ujson.Arr(ujson.Obj("recipientPlayerId" -> actor.value, "favor" -> 1,
            "relicIds" -> ujson.Arr())), "disclosures" -> ujson.Arr()))))
      assertEquals(replace.statusCode(), 200, replace.body()); sequence = sequenceOf(replace)
      val accept = send(otherUser, answer("negotiation.deal",
        ujson.Obj("kind" -> "accept-deal")))
      assertEquals(accept.statusCode(), 200, accept.body()); sequence = sequenceOf(accept)
      val decline = send(otherUser, answer("negotiation.deal",
        ujson.Obj("kind" -> "decline-deal")))
      assertEquals(decline.statusCode(), 200, decline.body())
      assert(ujson.read(decline.body())("walkerDecision").isNull)
    } finally {
```
(Keep the existing `finally` block. If the projection JSON omits `walkerDecision` rather than writing `null` when absent, assert with `ujson.read(...).obj.get("walkerDecision").forall(_.isNull)`.)

- [ ] **Step 2: Run the migrated tests against the old code**

Run: `./sbtw "testOnly oathdigital.application.GameApplicationServiceSuite oathdigital.server.AuthenticatedGameRoutesSuite oathdigital.protocol.CommandProtocolSuite oathdigital.application.PendingWalkerInvariantSuite"`
Expected: PASS. `PendingWalkerInvariantSuite`'s "the sample holds every GameCommand constructor" test fails until the commands are gone, which is the next step's proof.

- [ ] **Step 3: Delete the commands, intents and mapper entries**

- `GameCommands.scala`: delete `BeginNegotiation`, `ReplaceNegotiationTerms`, `AcceptNegotiation` and `DeclineNegotiation`.
- `Authorization.scala`: delete `beginNegotiation`, `replaceNegotiationTerms`, `acceptNegotiation` and `declineNegotiation`.
- `GameApplicationService.scala`: delete the four `case GameCommand.*Negotiation` arms and `import oathdigital.gameplay.actions.NegotiationCommand`.
- `GameIntentMapper.scala`: delete the four `Intent.*Negotiation` arms (the private `negotiation` and `disclosure` helpers stay, `decisionAnswer` uses them).
- `CommandIntents.scala`: delete the four intents. Keep the `NegotiationTerms`, `NegotiationTransfer`, `NegotiationDisclosure` and `NegotiationInformation` wire types.
- `CommandIntentCodec.scala`: delete the four `tagged(...)` cases and the private `negotiation` helper. `CommandIntentDecoders.scala`: delete the four `case "beginNegotiation"`, `"replaceNegotiationTerms"`, `"acceptNegotiation"` and `"declineNegotiation"` decoders. `CommandNestedCodecs.encodeNegotiation`/`decodeNegotiation` stay (the propose-terms answer uses them).

- [ ] **Step 4: Run and commit**

Run: `./sbtw "Test/compile" "frontend/Test/compile" "testOnly oathdigital.application.GameApplicationServiceSuite oathdigital.server.AuthenticatedGameRoutesSuite oathdigital.protocol.CommandProtocolSuite oathdigital.application.PendingWalkerInvariantSuite oathdigital.server.GameHttpWireSuite"`
Expected: PASS. `NegotiationSuite` and the parity suite compile and pass, since the rules layer is untouched.

```bash
git add -A src shared frontend
git commit -m "refactor(negotiation): retire the four legacy Negotiation commands

BeginNegotiation, ReplaceNegotiationTerms, AcceptNegotiation and
DeclineNegotiation, their intents, codecs and mapper entries are gone. A client
starts Negotiation with StartWalker and answers it with ResolveWalker. The HSQL
and route tests now drive the walker path and add a reopen of a parked deal.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 10: Retire the legacy Negotiation projection

**Files:**
- Modify: `src/main/scala/oathdigital/application/LegalActionProjector.scala:127-132`, `PendingProcedureProjector.scala:22-28,129-178`, `ScopedProjectionContext.scala:30-31`, `GameProjection.scala:155-156`, `shared/src/main/scala/oathdigital/protocol/projection/GameProjectionDto.scala:31-32`, `GameProjectionCodec.scala:20,71-72,120-121,147`, `ActionProjectionCodec.scala` (`encodeNegotiation`/`decodeNegotiation`), `ActionProjectionDtos.scala` (`NegotiationProjection`)
- Modify tests: `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala:56-61`, `src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala:664`, `src/test/scala/oathdigital/gameplay/NegotiationSuite.scala` (the projection tests), `src/test/scala/oathdigital/gameplay/NegotiationParitySuite.scala` (the mid-deal visibility test)

**Interfaces:**
- Consumes: Task 6's walker projection (which now carries every case the legacy `negotiation` and `negotiationWaiting` fields did).
- Produces: `GameProjection` has no `negotiation` or `negotiationWaiting`.

- [ ] **Step 1: Remove the tests that read the legacy fields**

- `NegotiationParitySuite`: delete the test `"what each participant is shown mid-deal matches"` (visibility parity was proven in Task 8, and after this task there is no legacy field to compare). The outcome tests compare whole projections and keep working.
- `NegotiationSuite`: delete the tests that call `projector.project(...)`/`projectPublic(...)` and read `.negotiation` or `.negotiationWaiting`: the disclosure test's projection assertions (keep its state assertions if you want it kept as a legacy rules test, since the whole suite goes in Task 11) and `"projection gives every participant only participant Negotiation controls"`. Task 6's `NegotiationDealProjectionSuite` covers what they asserted.
- `GameApplicationServiceSuite.scala:664`: delete `assert(!view.negotiationWaiting)`.
- `ProjectionProtocolSuite.scala:56-61`: delete the `negotiation = Some(NegotiationProjection(...))` and `negotiationWaiting = true` lines from the sample projection. The Task 6 round-trip test covers the DTO rows.

- [ ] **Step 2: Run to see what still compiles**

Run: `./sbtw "Test/compile"`
Expected: PASS (the fields still exist).

- [ ] **Step 3: Remove the legacy projection**

- `LegalActionProjector.scala`: delete the two `case Some(...: PendingProcedure.Negotiation)` arms (the participant controls `replaceNegotiationTerms`/`declineNegotiation`/`acceptNegotiation` and the empty non-participant arm).
- `PendingProcedureProjector.scala`: delete `negotiationProjection` and the `negotiationWaiting` boolean argument in `project`, so `PendingProjection` is built without them.
- `ScopedProjectionContext.scala`: delete `negotiation` and `negotiationWaiting` from `PendingProjection`. `GameProjection.scala`: delete `negotiation = pending.negotiation,` and `negotiationWaiting = ...,`.
- Shared: delete `negotiation` and `negotiationWaiting` from `GameProjection` (`GameProjectionDto.scala`); in `GameProjectionCodec.scala` delete their keys from the allowed set, from `encode`, from `decode` and from the constructor call; in `ActionProjectionCodec.scala` delete `encodeNegotiation` and `decodeNegotiation` (the per-row helpers extracted in Task 6 stay, the deal codec uses them); in `ActionProjectionDtos.scala` delete `NegotiationProjection` and keep `NegotiationTransferProjection`, `NegotiationDisclosureProjection`, `NegotiationSiteRelicProjection`.

- [ ] **Step 4: Run and commit**

Run: `./sbtw "Test/compile" "frontend/Test/compile" "testOnly oathdigital.protocol.ProjectionProtocolSuite oathdigital.application.NegotiationDealProjectionSuite oathdigital.application.GameApplicationServiceSuite oathdigital.gameplay.NegotiationParitySuite" "frontend/test"`
Expected: PASS.

```bash
git add -A src shared frontend
git commit -m "refactor(negotiation): retire the legacy negotiation projection

The walker decision projection carries every case the negotiation and
negotiationWaiting fields did, and shows the deal to non-participants too.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 11: Retire the legacy rules, events, pending state and rule block

**Files:**
- Delete: `src/main/scala/oathdigital/gameplay/actions/Negotiation.scala` (holds `NegotiationCommand`, `Negotiation` and `NegotiationPowerSupport`), `src/main/scala/oathdigital/server/NegotiationHttpCodec.scala`, `src/test/scala/oathdigital/gameplay/NegotiationSuite.scala`, `src/test/scala/oathdigital/gameplay/NegotiationParitySuite.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala:8,68-77,130-134`, `src/main/scala/oathdigital/model/PendingProcedures.scala` (the `Negotiation` case), `src/main/scala/oathdigital/model/GameEventProtocol.scala:40-52`, `src/main/scala/oathdigital/serialization/ActionEventCodec.scala`, `GameEventWire.scala:36-40`, `GameEventJsonSupport.scala` (the two terms delegates), `src/main/scala/oathdigital/gameplay/setup/FirstGameSetup.scala:200-201`, `src/main/scala/oathdigital/gameplay/RuleResolution.scala:44-53`, `src/main/scala/oathdigital/model/RuleSources.scala:107-109`, `src/main/scala/oathdigital/model/GameViolation.scala:73-78`, `src/main/scala/oathdigital/server/GameRoutes.scala:32-38`
- Modify tests: `src/test/scala/oathdigital/gameplay/PendingWalkerRulesSuite.scala:5,59`, `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala` (the v11 Negotiation events test and any stream test naming the events), `src/test/scala/oathdigital/gameplay/RuleResolutionSuite.scala:42`, `src/test/scala/oathdigital/server/CommandRejectionMessageSuite.scala`, `src/test/scala/oathdigital/gameplay/BackendArchitectureSuite.scala` only if it names a deleted file

**Interfaces:**
- Consumes: everything above.
- Produces: no `PendingProcedure.Negotiation`, no Negotiation events, no fail-closed Negotiation rule block. The remaining `PendingProcedure` cases are `Campaign` and `CampaignRaidRelocation`.

- [ ] **Step 1: Delete the legacy suites and the rule-stub tests**

```bash
git rm src/test/scala/oathdigital/gameplay/NegotiationSuite.scala src/test/scala/oathdigital/gameplay/NegotiationParitySuite.scala
```
`NegotiationSuite` held the legacy rules tests; their walker equivalents are `NegotiationDealSuite`, `NegotiationProcedureSuite` and `NegotiationDealProjectionSuite`. The parity suite has served its purpose (Task 8). Before deleting, skim `NegotiationSuite` once more for a behaviour with no walker test (`git show HEAD:src/test/scala/oathdigital/gameplay/NegotiationSuite.scala`); the blocked-handler test is deliberately gone, since that behaviour changed.

In `PendingWalkerRulesSuite.scala` delete `NegotiationCommand` from the import and the `"negotiation" -> rules.handle(state, NegotiationCommand.Decline(...))` entry. In `GameEventWireSuite.scala` delete the test `"v11 Negotiation events preserve authored terms and disclosures"` and any other case that constructs `NegotiationStarted`, `NegotiationTermsReplaced`, `NegotiationAccepted`, `NegotiationDeclined` or `NegotiationCompleted` (`git grep -n "Negotiation" -- src/test/scala/oathdigital/serialization`).

- [ ] **Step 2: Delete the events and the rules**

- `OathRules.scala`: delete the import of `Negotiation`/`NegotiationCommand`, the whole `def handle(state, command: NegotiationCommand)`, and the five `case event: Negotiation*` arms in `evolve`.
- `Negotiation.scala`: `git rm`. This removes `NegotiationCommand`, the legacy `Negotiation` object and `NegotiationPowerSupport` with its `ExpectedInventory` fingerprint.
- `GameEventProtocol.scala`: delete the five `Negotiation*` event case classes. `ActionEventCodec.scala`: delete the five discriminator, encoder and decoder cases (leaving `SiteRelicsPeeked`, `OwnedRelicRevealed`, `WarbandsMoved`). `GameEventWire.scala`: delete the five `Negotiation*Type` constants. `FirstGameSetup.scala:200-201`: delete the five names from the event match.
- `GameEventJsonSupport.scala`: delete `encodeNegotiationTerms` and `decodeNegotiationTerms` (nothing calls them now). `NegotiationTermsCodec` stays: `DecisionAnswerCodec` uses it.
- `PendingProcedures.scala`: delete the `Negotiation` case class from `object PendingProcedure`. The terms types already live in `NegotiationTerms.scala` (Task 4).
- `NegotiationHttpCodec.scala`: `git rm` (nothing calls it).

- [ ] **Step 3: Delete the rule block and its violations**

- `RuleResolution.scala`: delete the `RuntimeRuleRegistry` object and its doc comment (the "Travel-only stub retained for Negotiation's explicit blocking boundary"). Keep `TypedRuleHandler` and `RuleRegistry`: Campaign uses them.
- `RuleSources.scala`: delete the `RuleQueryContext.Negotiation` case (lines ~107-109). Keep `RuleQueryContext.Campaign` and the other types.
- `GameViolation.scala`: delete `NegotiationDecisionMismatch`, `NegotiationOutcomeMismatch`, `UnsupportedNegotiationRule` and `UnsupportedNegotiationCatalogInventory`. Keep `NegotiationUnavailable` (the start gate and the `NegotiationDeal.settle` refusal use it) and `InsufficientFavor`.
- `GameRoutes.scala`: delete the messages for the four deleted violations, keeping `NegotiationUnavailable`'s. `CommandRejectionMessageSuite.scala` and `RuleResolutionSuite.scala:42`: fix any reference to a deleted name (the suite text refers to the stub in a comment; drop or reword that comment).

- [ ] **Step 4: Compile, then find what is left**

Run: `./sbtw "Test/compile" "frontend/Test/compile"`
Fix each error by deleting the reference to a deleted symbol. Then:

```bash
git grep -n "PendingProcedure.Negotiation\|NegotiationCommand\|NegotiationPowerSupport\|NegotiationStarted\|NegotiationCompleted\|NegotiationTermsReplaced\|NegotiationAccepted\|NegotiationDeclined\|RuntimeRuleRegistry\|UnsupportedNegotiation\|NegotiationDecisionMismatch\|NegotiationOutcomeMismatch\|negotiationWaiting" -- src shared frontend
```
Expected: no output.

- [ ] **Step 5: Full gate and commit**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS. `BackendArchitectureSuite` checks the file-size bound and the layering rules; if it fails on a file that grew, split that file.

```bash
git add -A src shared frontend
git commit -m "refactor(negotiation): delete the legacy Negotiation rules, events and rule block

PendingProcedure.Negotiation, the four commands' rules, the five Negotiation
events with their codecs, the fail-closed handler block with its catalog
fingerprint and RuntimeRuleRegistry stub, and the violations only they used.
Only Campaign's two PendingProcedure cases remain.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 12: Documentation and the final gate

**Files:**
- Modify: `docs/architecture/all-exile-negotiation.md`, `docs/ROADMAP.md`, `docs/architecture/authoritative-events.md`, `docs/architecture/core-operations-migration.md`, `docs/architecture/gameplay-modules.md`, `docs/architecture/rule-resolution.md`, `docs/rules/implementation-traceability.md`, `docs/rules/new-foundations-delta.md` (only if it states the all-Exile limit), `docs/superpowers/specs/2026-09-05-procedure-walker-design.md`, `docs/superpowers/specs/2026-09-12-walker-ownership-and-phases-design.md`, `docs/superpowers/specs/2026-09-19-negotiation-walker-design.md`

- [ ] **Step 1: Rewrite the architecture doc**

`docs/architecture/all-exile-negotiation.md` keeps its filename so inbound links hold. Retitle it "Negotiation", drop the "all-Exile first game" scope and the "`PendingProcedure.Negotiation` is the authoritative deal" paragraph, and describe the walker form: the `Negotiation` procedure, the co-owned deal `Decide`, the fold over `answered`, settlement (disclosures before transfers, refused when an author can no longer afford their terms), what each viewer sees, and that unsupported `When Negotiating` handlers are ignored and recorded (`ActionKind.Negotiation`, `IgnoredRulesRecorded`) rather than blocking. Keep the "Citizenship ... remain deferred" paragraph. Remove the sentence about the catalog fingerprint.

- [ ] **Step 2: Update the other docs**

Run `git grep -n -i "negotiat\|RuntimeRuleRegistry\|MajorActionKind" -- docs ':!docs/superpowers/plans' ':!docs/superpowers/specs'` and bring each hit up to date:
- `docs/ROADMAP.md`: mark Negotiation ported to the walker; list the deletions.
- `docs/architecture/authoritative-events.md`, `gameplay-modules.md`, `rule-resolution.md`, `core-operations-migration.md`: drop the five Negotiation events, `Negotiation.scala`, the `RuntimeRuleRegistry` stub note and the "Negotiation's blocking boundary" wording; `MajorActionKind` becomes `ActionKind` where a current doc names it.
- `docs/rules/implementation-traceability.md`: the minor-actions row now points at the walker procedure files (`gameplay/actions/negotiation/`), and the relic-peek and warband-move minor actions keep their own row.
- `docs/superpowers/specs/2026-09-05-procedure-walker-design.md`: in "What remains", only `Campaign` and `CampaignRaidRelocation` remain, and Negotiation is done.
- `docs/superpowers/specs/2026-09-12-walker-ownership-and-phases-design.md`: in Open items, mark "Concurrent multi-owner decisions" closed by co-owned decisions (this slice) and note that a simultaneous step is designed for but not built.
- `docs/superpowers/specs/2026-09-19-negotiation-walker-design.md`: fold the plan's five deviations into the spec (the `Negotiate` query carries the deal, `parkedDecide` and `awaitedPlayer` remain, `NegotiationDeal.eligible`, the waiting projection's new fields, `DecisionQueryProjection.count` removed) and change its status line to "implemented by the plan".

- [ ] **Step 3: Verify the docs**

Run: `git grep -n "PendingProcedure.Negotiation\|NegotiationPowerSupport\|RuntimeRuleRegistry" -- docs ':!docs/superpowers/plans' ':!docs/superpowers/specs'`
Expected: no output.

- [ ] **Step 4: Final gate and commit**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS.

```bash
git add -A docs
git commit -m "docs: record Negotiation as ported to the walker

Rewrites the Negotiation architecture note for the walker form and updates the
roadmap, the event and module inventories, the walker spec's remaining cases
and the ownership design's multi-owner item.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Self-review

**Spec coverage.**
- Rename to `ActionKind`: Task 1.
- `ChooseMany` range: Task 2.
- `Decide.coOwners`, `openDecisions`, `awaitedPlayers`, `accepts(by)`, the spike, and the recorded `Simultaneous` semantics (in the spec, deferred): Task 3.
- `Negotiate` query, three answers, journal and wire codecs, mapper: Task 4.
- Procedure (tree, forced candidate, start gate without a first-game gate, fold, snapshot, settlement, action boundary, fallback recording, continuation, windows, registry): Task 5.
- Projection to owners and to everyone else, waiting projection with co-owners: Task 6.
- Frontend start control and panel: Task 7.
- Parity before deletion: Task 8.
- Deletions and their order: Tasks 9-11.
- Docs and spec fold-in: Task 12.
- Verification bullets of the spec: spike (Task 3), engine (3), deal semantics (5), non-Exile and altered Foundation (5), diagnostics (5), visibility per viewer class (6), parity (8), protocol and frontend (2, 4, 6, 7, 9), gate (5, 8, 11, 12).

**Placeholder scan.** No task says "TBD" or "add appropriate handling". The places that name a symbol the executor must locate rather than a line (the `ActionDecisionRenderer` legacy block, `ServerModeUiSuite` deletions, the `GameApplicationServiceSuite` `handle` result type, the `ProjectionProtocolSuite` sample) say what to look for and what the result must be.

**Type consistency.** `NegotiationDeal.{negotiatorsDecisionId, dealDecisionId, decisionIds, eligible, participants, fold, deal, snapshot, settle}` and `DealState` are defined in Task 5 and used unchanged by Tasks 6, 8 and 9. `DecisionQuery.Negotiate(participants, terms, accepted, bounds, acceptors, heading)` is defined in Task 4 and consumed by Tasks 5 and 6. `WalkerWaitingProjection(playerId, heading, coOwnerPlayerIds, deal)` and `NegotiationDealProjection`/`NegotiationEditingProjection` are defined in Task 6 and used by Tasks 7 and 9. `ActionKind.Negotiation` and `OathContinue.AwaitingNegotiation(playerId, decision)` are defined in Task 5. `ActionKind` replaces `MajorActionKind` everywhere from Task 1 on.

**Known risks the executor should watch.**
1. Task 3's spike: `Branch` inside `Repeat` with a co-owned `Decide`.
2. Task 5's replay test depends on the first-game fixture's player count after Travel.
3. Task 6 assumes `walkerControls` derives `resolveWalkerDecision` from `walkerDecisions.project(context)` for every owner.
4. Task 8's parity suite compares the order of transfers between legacy (map iteration) and the walker (participant order); a difference there is a legacy quirk to record, not a walker bug.
