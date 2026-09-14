# Rest on the Walker and Phase Powers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run the whole Rest phase (Begin Rest, Finish Rest, League Treaty) on the procedure walker. Add one generic "use a phase power" action for Wake, Act and Rest, with Silver Tongue as the first REST power. Delete the legacy Rest seam. Prove that nothing but a walker resume can act while a walker is parked.

**Architecture:** `BeginRest` and `FinishRest` become `PhaseTransitionRef`s. Finish Rest's tree runs cleanup (hookable at `PowerWindow.RestReturnFavor`), the Supply refresh, and a new `BeginTurn` primitive. A turn boundary (round end, then Wake evaluation) runs after its `WalkerCompleted`. League Treaty becomes an automatic `ContributingPower` that inserts two decisions before cleanup: a `ChooseOne` over favor banks and a new `Distribute` query. A `PhasePower` is started through one parameterized `ActionRef.UsePower(powerId)`, whose tree is `power.build` followed by `RecordPowerUse`. One usability function feeds the start gate, the legal controls, the projection and the Rest auto-skip.

**Tech Stack:** Scala 2.13 (`-Xlint`), sbt multi-project (root engine, `shared` cross-built protocol, `frontend` Scala.js under jsdom), munit, ujson.

**Spec:** `docs/superpowers/specs/2026-09-13-rest-walker-and-phase-powers-design.md` (approved, `04944c5`). Read it before any task; this plan argues from it.

## Global Constraints

- Full gate green at every commit: `./sbtw "test" "frontend/test" "frontend/fastLinkJS" && python3 scripts/check-architecture.py && git diff --check`.
- Every production Scala file stays at or under 800 lines (`BackendArchitectureSuite`, "all production Scala files stay bounded"). Watch: `frontend/.../ActionDecisionRenderer.scala` 734, `frontend/.../ServerUiSupport.scala` 712, `gameplay/operations/OperationValidator.scala` 688, `gameplay/walker/ProcedureWalker.scala` 674, `serialization/GameEventJsonSupport.scala` 658, `serialization/WalkerOperationCodec.scala` 623, `gameplay/operations/CoreOperations.scala` 596, `gameplay/operations/OperationStateMutation.scala` 573, `application/GameApplicationService.scala` 550, `gameplay/OathRulesWalker.scala` 453, `gameplay/OathRules.scala` 374, `gameplay/walker/WalkerProcedureRegistry.scala` 359. If a file would cross 800, split it in the same task.
- `BackendArchitectureSuite` stays green without being weakened. `gameplay` imports no `application`, `persistence`, `presentation`, `protocol`, `serialization` or `server`. No source under `gameplay/walker` or `gameplay/operations` names a specific power. A power imports nothing from `oathdigital.gameplay.walker`.
- Replay applies recorded operations and events only. No contribution, `PhasePower.usable`, `PhasePower.build`, `DecisionQueries` check or turn-boundary evaluation runs at replay.
- Old-journal compatibility is not a constraint. Deleting `RestStarted`, `RestCompleted` and the League Treaty events and wire types is expected; rewrite fixtures to the new spelling.
- A walker procedure belongs to `turn.activePlayer`. Only a parked `Decide`'s owner may differ (League Treaty's ruler).
- `ReviewedPowerCatalog.AuditedCatalogFingerprint` and the Rest handler inventory fingerprint (`BeginRestProcedure.ExpectedHandlerInventory`, today `Rest.ExpectedHandlerInventory`) are recomputed in the same commit that changes the handler inventory. Take the new value from the failing assertion's message; never weaken the check.
- Work on branch `feat/rest-walker`, cut from `feat/engine-redesign`. Commit per task with the message shown, ending with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- Run a single suite with `./sbtw "testOnly <fully.qualified.Suite>"`; frontend and shared suites with `./sbtw "frontend/testOnly <fully.qualified.Suite>"`.
- zsh: quote globs, and never `echo ====` (use `echo --`).

## Corrections to the spec

The spec is binding. Implementation needs these changes to its letter; each is recorded so a reviewer does not read it as drift.

1. **`Distribute` carries display options, like every other query.** The spec sketches slots by reference. Here a slot is `DistributeSlot(option: DecisionOption, minimum, maximum, suggested: Option[Int])`, and the query is `Distribute(slots, total, heading: Option[String] = None, confirmLabel: Option[String] = None)`. A slot's `ref` is `option.ref`, so the projector presents it exactly as it presents a `ChooseOne` option, and `heading` stays the `Option` that `DecisionQuery.heading` already declares.
2. **`ActionRef.UsePower(power)` is parameterized, so no `all` vector can list it.** `ActionRef.fromKey`, `StartableRef.fromKey` and `ProcedureRef.fromFamilyKey` parse the `use-power:<powerId>` key directly. `WalkerProcedureRegistry.lookup` and `isRegistered` map every `UsePower(_)` to one entry built for that id. The registry coverage test checks `ProcedureRef.all` plus one representative `UsePower`.
3. **`WalkerProcedureRegistry.fallbackKind` returns `Either[OathViolation, Option[MajorActionKind]]`.** Begin Rest records `MajorActionKind.Rest` diagnostics; Finish Rest and `UsePower` declare `None`, and `startWalker` skips `withFallback` for them. A triggered procedure still never reaches this accessor.
4. **`UsePower` trees bypass the injected tree source.** `OathRules`' default parameter list cannot pass its own `phasePowerCatalog` into `declaredWalkerTree`, so `WalkerTreeSource` keeps its shape and every test lambda stays as it is. Instead `OathRulesWalker.buildWalker` and `WalkerDecisionProjector` route an `ActionRef.UsePower` straight to `WalkerProcedureRegistry.build`/`rebuild` with the injected `PhasePowers`; every other procedure still goes through the injected source.
5. **The Rest files live in a new package `gameplay/phases/rest/`**: `BeginRestProcedure`, `FinishRestProcedure`, `TurnBoundary` and the moved `WarExhaustionRandomPort`. `RestCleanup.scala` stays in `gameplay/phases`.
6. **A phase power's sources follow `ReviewedPowerInspector.accessible`.** That rule exposes a site card or site relic only at the player's pawn site, and an adviser or held relic only to its owner, all face up. It does not expose ruled-site access, so neither does this plan.
7. **The application gate already exists.** `GameApplicationService.applyCommand` refuses every non-resume command while `walkerPending` is set, and `OathLifecycle.validateAct` refuses Act commands. The unguarded path is the `OathRules.handle` overloads. The invariant task adds that guard and pins both layers.
8. **The invariant is pinned per layer, not as one matrix over four contexts.** The application gate refuses a command from `walkerPending` alone, so its matrix of every `GameCommand` runs over the two parks commands can reach from a first-game setup: a Recover roll and an off-turn Oathkeeper recipient. League Treaty needs card 237 on a ruled site and Silver Tongue needs card 92 as an adviser. A recorded delta cannot lift a specific card from the middle of a deck, so those two parks are arranged as state and pinned at the rules boundary: every walker start and every legacy `handle` overload is refused over them.
9. **Delivery is split into twelve tasks, not six.** Each task is a reviewable unit. Between Task 4 and Task 7 the Rest auto-skip consults a usability stub that is always false. Task 7 replaces it with the real usability function, and the production phase power catalog stays empty until Task 8 adds Silver Tongue. The stub is honest for the catalog of that moment.
10. **Task 4 keeps a trimmed `Rest.scala`.** It holds only the legacy League Treaty resolve and decline commands until Task 6 deletes the seam. Nothing starts a legacy League Treaty decision after Task 4, so that code is unreachable but still compiles.
11. **The walker League Treaty is a new file, `powers/rest/LeagueTreatyContribution.scala`.** The spec rewrites `LeagueTreatyPower.scala` in place, but the legacy seam still compiles against that object until Task 6. Following the `CatacombsContribution` naming, Task 5 adds the new power beside it, and Task 6 deletes `LeagueTreatyPower.scala`.

## File map

| File | Responsibility | Tasks |
|---|---|---|
| `src/main/scala/oathdigital/model/Decisions.scala` | `FavorBank` ref and option, `Distribute` query, `DistributeAnswer` | 1 |
| `src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala` | `Distribute` well-formedness and acceptance | 1 |
| `src/main/scala/oathdigital/serialization/DecisionAnswerCodec.scala` | Journal spelling of `DistributeAnswer` | 1 |
| `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala` | `distribute` projection, `FavorBank` label, phase-power tree factory | 1, 7 |
| `shared/.../protocol/projection/ActionProjectionDtos.scala`, `ActionProjectionCodec.scala` | Slot projection; `PhasePowerProjection` | 1, 9 |
| `shared/.../protocol/CommandIntents.scala`, `CommandNestedCodecs.scala`, `CommandIntentCodec.scala`, `CommandIntentDecoders.scala` | `DistributeWire`; `usePower` intent; Rest intent deletion | 1, 6, 9 |
| `src/main/scala/oathdigital/application/GameIntentMapper.scala` | Distribute answer mapping; `usePower`; Rest intent deletion | 1, 6, 9 |
| `frontend/.../DistributeDecisionState.scala` (new) | Pure stepper state and walker draft | 2 |
| `frontend/.../DistributePanelRenderer.scala` (new) | Distribute panel with Shift+click | 2 |
| `frontend/.../ServerUiSupport.scala`, `ServerModeUi.scala`, `ActionDecisionRenderer.scala` | Draft wiring; Rest renderer deletion; phase-power buttons | 2, 6, 10 |
| `src/main/scala/oathdigital/model/GameState.scala` | `PowerSourceRef.Card` | 3 |
| `src/main/scala/oathdigital/gameplay/operations/CoreOperations.scala`, `OperationStateMutation.scala`, `OperationError.scala` | `BeginTurn` | 3 |
| `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala` | `begin-turn`; card power source | 3 |
| `src/main/scala/oathdigital/model/ProcedureRef.scala` | `BeginRest`, `FinishRest`, `UsePower` | 4, 7 |
| `src/main/scala/oathdigital/gameplay/phases/rest/` (new) | `BeginRestProcedure`, `FinishRestProcedure`, `TurnBoundary`, `WarExhaustionRandomPort` | 4 |
| `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` | Rest and `UsePower` entries; optional fallback kind; phase powers | 4, 7 |
| `src/main/scala/oathdigital/gameplay/RuleSourceIndex.scala`, `gameplay/powers/PowerSupport.scala` | One access rule for reviewed and phase powers | 7 |
| `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala` | Rest continuation, turn boundary, auto-skip | 4, 7 |
| `src/main/scala/oathdigital/gameplay/OathRules.scala` | Legacy Rest removal, constructor, `handle` guard | 4, 6, 7, 11 |
| `src/main/scala/oathdigital/gameplay/model/GameProcedureProtocol.scala` | `AwaitingRestDecision`, `AwaitingPowerDecision` | 4, 6, 7 |
| `src/main/scala/oathdigital/gameplay/powers/rest/LeagueTreatyContribution.scala` (new) | League Treaty as a `ContributingPower` | 5 |
| `src/main/scala/oathdigital/gameplay/powers/RestPowers.scala` | Reviewed handler inventory for League Treaty and Silver Tongue | 5, 8 |
| `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala` | Registers League Treaty and Silver Tongue | 5, 8 |
| Legacy Rest seam (deleted) | `Rest.scala`, `RestPowerIntegration`, `RestPowerHandler`, events, codecs, commands, projections | 6 |
| `src/main/scala/oathdigital/gameplay/powerresolver/PhasePower.scala` (new) | `PhasePower`, `PhasePowers` | 7 |
| `src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala` (new) | Sources, usability, tree | 7 |
| `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala` (new) | Production phase powers | 7, 8 |
| `src/main/scala/oathdigital/gameplay/powers/rest/SilverTongue.scala` (new) | Silver Tongue | 8 |
| `src/main/scala/oathdigital/application/PhasePowerProjector.scala` (new) | `phasePowers` projection and `usePower` controls | 9 |
| `frontend/.../PhasePowerButtons.scala` (new) | Phase power buttons and the Finish Rest predicate | 10 |
| `src/test/scala/oathdigital/application/PendingWalkerInvariantSuite.scala`, `src/test/scala/oathdigital/gameplay/PendingWalkerRulesSuite.scala` (new) | The invariant, per layer | 11 |
| `docs/superpowers/specs/2026-09-12-walker-ownership-and-phases-design.md` | Superseded statements corrected | 12 |

---

### Task 1: Distribute decision vocabulary through the backend and the wire

**Files:**
- Modify: `src/main/scala/oathdigital/model/Decisions.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala`
- Modify: `src/main/scala/oathdigital/serialization/DecisionAnswerCodec.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandIntents.scala` (`DecisionAnswerWire`)
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandNestedCodecs.scala`
- Modify: `src/main/scala/oathdigital/application/GameIntentMapper.scala:190-202`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala:126`, `ActionProjectionCodec.scala:212-246`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala:90-93,161-170,196-214`
- Modify: `frontend/src/main/scala/oathdigital/frontend/package.scala:83` (alias)
- Test: `src/test/scala/oathdigital/gameplay/walker/DecisionQuerySuite.scala`, `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`, `shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala`, `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`, `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala`, `src/test/scala/oathdigital/server/GameHttpWireSuite.scala`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `DecisionOptionRef.FavorBank(suit: Suit)` with kind `"favor-bank"`, wireId `suit.key`; `DecisionOption.FavorBank(ref: DecisionOptionRef.FavorBank)`.
  - `DistributeSlot(option: DecisionOption, minimum: Int, maximum: Int, suggested: Option[Int]) { def ref: DecisionOptionRef }`.
  - `DecisionQuery.Distribute(slots: Vector[DistributeSlot], total: Int, heading: Option[String] = None, confirmLabel: Option[String] = None)`.
  - `DistributeAmount(option: DecisionOptionRef, amount: Int)`; `DecisionAnswer.DistributeAnswer(amounts: Vector[DistributeAmount])`.
  - Wire: `DecisionAnswerWire.DistributeWire(amounts: Vector[DistributeAmountWire])`, `DistributeAmountWire(optionKind: String, optionId: String, amount: Int)`.
  - Projection: `DecisionSlotProjection(option: DecisionOptionProjection, minimum: Int, maximum: Int, suggested: Option[Int])`; `DecisionQueryProjection` gains trailing `slots: Vector[DecisionSlotProjection] = Vector.empty, total: Option[Int] = None`; form `"distribute"` carries `options = Vector.empty`. Frontend alias `DecisionSlotState`.

- [ ] **Step 1: Write the failing validator tests**

Append to `DecisionQuerySuite` (add `DistributeAmount`, `DistributeSlot`, `Suit` to its imports):

```scala
  private def bank(suit: Suit) = DecisionOptionRef.FavorBank(suit)
  private def slot(suit: Suit, min: Int, max: Int,
      suggested: Option[Int] = None) =
    DistributeSlot(DecisionOption.FavorBank(bank(suit)), min, max, suggested)

  /** The spec's worked example: three source suits of two favor each, and a
    * destination that must keep its own zero.
    */
  private val distribute = DecisionQuery.Distribute(Vector(
    slot(Suit.Arcane, 0, 2, Some(2)), slot(Suit.Discord, 0, 2, Some(2)),
    slot(Suit.Hearth, 0, 2, Some(2)), slot(Suit.Nomad, 0, 6, Some(0))),
    total = 6)

  private def amounts(values: (Suit, Int)*) = DecisionAnswer.DistributeAnswer(
    values.toVector.map { case (suit, n) => DistributeAmount(bank(suit), n) })

  private def violation(detail: String) =
    Left(OathViolation.InvalidEventOrder(s"decision $decisionId $detail"))

  test("a distribution with two or more open slots and a reachable total is well formed") {
    assertEquals(DecisionQueries.wellFormed(decisionId, distribute), Right(()))
  }

  test("a malformed distribution names its own defect") {
    val two = Vector(slot(Suit.Arcane, 0, 2), slot(Suit.Nomad, 0, 2))
    val cases = Vector(
      DecisionQuery.Distribute(Vector(slot(Suit.Arcane, 0, 2)), 1) ->
        "declares fewer than two slots",
      DecisionQuery.Distribute(Vector(slot(Suit.Arcane, 0, 2),
        slot(Suit.Arcane, 0, 2)), 1) -> "declares duplicate options",
      DecisionQuery.Distribute(Vector(slot(Suit.Arcane, -1, 2),
        slot(Suit.Nomad, 0, 2)), 1) ->
        "declares slot favor-bank/arcane with bounds -1..2",
      DecisionQuery.Distribute(Vector(slot(Suit.Arcane, 3, 2),
        slot(Suit.Nomad, 0, 2)), 1) ->
        "declares slot favor-bank/arcane with bounds 3..2",
      DecisionQuery.Distribute(two, 5) -> "declares a total no answer can meet",
      DecisionQuery.Distribute(Vector(slot(Suit.Arcane, 1, 2),
        slot(Suit.Nomad, 1, 2)), 2) ->
        "declares minimums that already make its total, leaving nothing to decide",
      DecisionQuery.Distribute(two, 4) ->
        "declares maximums that already make its total, leaving nothing to decide",
      DecisionQuery.Distribute(Vector(slot(Suit.Arcane, 0, 2, Some(1)),
        slot(Suit.Nomad, 0, 2)), 1) ->
        "suggests amounts for some slots but not all",
      DecisionQuery.Distribute(Vector(slot(Suit.Arcane, 0, 2, Some(2)),
        slot(Suit.Nomad, 0, 2, Some(2))), 1) ->
        "suggests a distribution it would not accept")
    cases.foreach { case (query, detail) =>
      assertEquals(DecisionQueries.wellFormed(decisionId, query),
        violation(detail), detail)
    }
  }

  test("the worked example's answer is accepted") {
    assertEquals(DecisionQueries.accepts(decisionId, distribute, amounts(
      Suit.Arcane -> 0, Suit.Discord -> 1, Suit.Hearth -> 2, Suit.Nomad -> 3)),
      Right(()))
  }

  test("a mismatched distribution answer names its own defect") {
    val full = Vector(Suit.Arcane -> 0, Suit.Discord -> 1, Suit.Hearth -> 2,
      Suit.Nomad -> 3)
    val cases = Vector(
      amounts(full :+ (Suit.Order -> 0): _*) ->
        "does not offer a distributed option",
      amounts(full :+ (Suit.Nomad -> 0): _*) ->
        "distributes to an option more than once",
      amounts(full.init: _*) -> "leaves an option undistributed",
      amounts(Suit.Arcane -> 3, Suit.Discord -> 0, Suit.Hearth -> 0,
        Suit.Nomad -> 3) -> "distributes to favor-bank/arcane outside 0..2",
      amounts(Suit.Arcane -> 0, Suit.Discord -> 0, Suit.Hearth -> 0,
        Suit.Nomad -> 3) -> "distributes an amount other than its total of 6")
    cases.foreach { case (answer, detail) =>
      assertEquals(DecisionQueries.accepts(decisionId, distribute, answer),
        violation(detail), detail)
    }
    assertEquals(DecisionQueries.accepts(decisionId, distribute,
      DecisionAnswer.ChooseOneAnswer(bank(Suit.Nomad))),
      violation("expects a distribution answer"))
    assertEquals(DecisionQueries.accepts(decisionId, chooseOne,
      amounts(full: _*)), violation("expects a single-choice answer"))
  }
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.DecisionQuerySuite"`
Expected: compilation fails on `DecisionOptionRef.FavorBank`, `DistributeSlot` and `DecisionQuery.Distribute`.

- [ ] **Step 3: Add the vocabulary to `Decisions.scala`**

In `object DecisionOptionRef`, after `Deck`:

```scala
  /** A suit's favor bank: the one shared place favor of that suit returns
    * to. A suit is a closed six-case enum, so this can never be absent.
    */
  final case class FavorBank(suit: Suit) extends DecisionOptionRef {
    val kind: String = "favor-bank"
    def wireId: String = suit.key
  }
```

In `fromWire`, before `case _ => None`, add
`case "favor-bank" => Suit.all.find(_.key == wireId).map(FavorBank(_))`. In the doc comments of `DecisionOptionRef.kind` and `fromWire`, change "seven" to "eight".

In `object DecisionOption`, after `Deck`:

```scala
  final case class FavorBank(ref: DecisionOptionRef.FavorBank)
      extends DecisionOption
```

After `DecisionSection`:

```scala
/** One amount-taking slot of a [[DecisionQuery.Distribute]].
  *
  * @param option what the slot distributes to, presented like any other
  *   option.
  * @param minimum fewest the slot may take; never negative.
  * @param maximum most the slot may take; never below `minimum`.
  * @param suggested the amount a client's draft opens at. Either every slot
  *   of a query suggests one or none does, and the suggestions together must
  *   be an accepted answer.
  */
final case class DistributeSlot(option: DecisionOption, minimum: Int,
    maximum: Int, suggested: Option[Int]) {
  def ref: DecisionOptionRef = option.ref
}
```

In `object DecisionQuery`, after `Partition`:

```scala
  /** Spread exactly `total` across the slots, each within its own bounds.
    *
    * @param confirmLabel what the submitting control is called; a
    *   distribution, like a partition, has a confirm step.
    */
  final case class Distribute(slots: Vector[DistributeSlot], total: Int,
      heading: Option[String] = None,
      confirmLabel: Option[String] = None) extends DecisionQuery
```

After `DecisionPlacement`:

```scala
/** One slot's amount in a [[DecisionAnswer.DistributeAnswer]]. */
final case class DistributeAmount(option: DecisionOptionRef, amount: Int)
```

In `object DecisionAnswer`, after `PartitionAnswer`:

```scala
  /** Answer to a [[DecisionQuery.Distribute]]: every declared slot exactly
    * once, each with its amount.
    */
  final case class DistributeAnswer(amounts: Vector[DistributeAmount])
      extends DecisionAnswer
```

- [ ] **Step 4: Validate `Distribute` in `DecisionQueries.scala`**

Add a `wellFormed` case after `Partition`:

```scala
    case DecisionQuery.Distribute(slots, total, _, _) =>
      val refs = slots.map(_.ref)
      val minimums = slots.map(_.minimum.toLong).sum
      val maximums = slots.map(_.maximum.toLong).sum
      val suggested = slots.flatMap(_.suggested)
      for {
        _ <- require(slots.size >= 2, decisionId,
          "declares fewer than two slots")
        _ <- require(refs.distinct.size == refs.size, decisionId,
          "declares duplicate options")
        _ <- slots.find(s => s.minimum < 0 || s.minimum > s.maximum) match {
          case Some(s) => reject(decisionId, s"declares slot ${label(s.ref)} " +
            s"with bounds ${s.minimum}..${s.maximum}")
          case None => Right(())
        }
        _ <- require(minimums <= total && total <= maximums, decisionId,
          "declares a total no answer can meet")
        _ <- require(minimums != total, decisionId, "declares minimums that " +
          "already make its total, leaving nothing to decide")
        _ <- require(maximums != total, decisionId, "declares maximums that " +
          "already make its total, leaving nothing to decide")
        _ <- require(suggested.isEmpty || suggested.size == slots.size,
          decisionId, "suggests amounts for some slots but not all")
        _ <- if (suggested.isEmpty) Right(())
          else acceptsDistribution(decisionId, slots, total,
              slots.zip(suggested).map { case (s, n) => DistributeAmount(s.ref, n) })
            .fold(_ => reject(decisionId,
              "suggests a distribution it would not accept"), Right(_))
      } yield ()
```

Extend the class doc's forced-shape paragraph with one sentence: "A distribution is forced exactly when its minimums or its maximums already sum to its total, so both are rejected."

Add an `accepts` case after `Partition`:

```scala
    case DecisionQuery.Distribute(slots, total, _, _) => answer match {
      case DecisionAnswer.DistributeAnswer(amounts) =>
        acceptsDistribution(decisionId, slots, total, amounts)
      case _ =>
        reject(decisionId, "expects a distribution answer")
    }
```

Add the helpers beside `acceptsPartition` (import `DistributeAmount` and `DistributeSlot`):

```scala
  private def acceptsDistribution(decisionId: String,
      slots: Vector[DistributeSlot], total: Int,
      amounts: Vector[DistributeAmount]): Either[OathViolation, Unit] = {
    val declared = slots.map(_.ref)
    val named = amounts.map(_.option)
    for {
      _ <- require(named.forall(declared.contains), decisionId,
        "does not offer a distributed option")
      _ <- require(named.distinct.size == named.size, decisionId,
        "distributes to an option more than once")
      _ <- require(declared.forall(named.contains), decisionId,
        "leaves an option undistributed")
      _ <- slots.find(s => amounts.find(_.option == s.ref)
          .exists(a => a.amount < s.minimum || a.amount > s.maximum)) match {
        case Some(s) => reject(decisionId, s"distributes to ${label(s.ref)} " +
          s"outside ${s.minimum}..${s.maximum}")
        case None => Right(())
      }
      _ <- require(amounts.map(_.amount.toLong).sum == total.toLong,
        decisionId, s"distributes an amount other than its total of $total")
    } yield ()
  }

  private def label(ref: DecisionOptionRef): String =
    s"${ref.kind}/${ref.wireId}"
```

- [ ] **Step 5: Run the validator suite**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.DecisionQuerySuite"`
Expected: compilation still fails elsewhere. `-Xlint` reports non-exhaustive matches in `DecisionAnswerCodec`, `WalkerDecisionProjector` and `GameIntentMapper`. Steps 6-9 fix them.

- [ ] **Step 6: Journal spelling in `DecisionAnswerCodec.scala`**

Add `private val DistributeTag = "distribute"`. Add an encode case:

```scala
    case DecisionAnswer.DistributeAnswer(amounts) => ujson.Obj(
      "kind" -> DistributeTag,
      "amounts" -> ujson.Arr.from(amounts.map(entry => ujson.Obj(
        "option" -> encodeRef(entry.option), "amount" -> entry.amount))))
```

Add a decode case before `case other`:

```scala
      case DistributeTag =>
        traverse(value("amounts").arr.toVector.zipWithIndex) {
          case (entry, index) =>
            val entryPath = s"$path.amounts[$index]"
            val raw = entry("amount").num
            for {
              ref <- decodeRef(entry("option"), s"$entryPath.option")
              amount <- Either.cond(raw.isValidInt, raw.toInt,
                InvalidValue(s"$entryPath.amount", s"amount '$raw' is not an integer"))
            } yield DistributeAmount(ref, amount)
        }.map(DecisionAnswer.DistributeAnswer)
```

In `GameEventWireSuite`'s test "both generic walker decision answers round trip on the step and on the park", add a third answer and include it in the `answered` vector and as a `WalkerStepRecorded` step:

```scala
    val distribute = DistributeAnswer(Vector(
      DistributeAmount(DecisionOptionRef.FavorBank(Suit.Arcane), 0),
      DistributeAmount(DecisionOptionRef.FavorBank(Suit.Nomad), 3)))
```

Rename that test to "every generic walker decision answer round trips on the step and on the park". In "every option reference kind round trips through a recorded answer", add `DecisionOptionRef.FavorBank(Suit.Hearth)` to the reference list.

- [ ] **Step 7: Command wire for the answer**

In `CommandIntents.scala`, beside `PartitionWire`:

```scala
  final case class DistributeWire(amounts: Vector[DistributeAmountWire])
      extends DecisionAnswerWire
```

and at top level beside `DecisionPlacementWire`:

```scala
final case class DistributeAmountWire(optionKind: String, optionId: String,
    amount: Int)
```

In `CommandNestedCodecs.encodeDecisionAnswerWire`, add:

```scala
    case DecisionAnswerWire.DistributeWire(amounts) => ujson.Obj(
      "kind" -> "distribute",
      "amounts" -> ujson.Arr.from(amounts.map(row => ujson.Obj(
        "optionKind" -> row.optionKind, "optionId" -> row.optionId,
        "amount" -> row.amount))))
```

In `decodeDecisionAnswerWire`, before `case kind =>`:

```scala
      case "distribute" => for {
        _ <- exact(root, Set("kind", "amounts"), path)
        raw <- field(root, "amounts", path).flatMap(array(_, s"$path.amounts"))
        rows <- traverse(raw.zipWithIndex) { case (v, i) =>
          decodeDistributeAmount(v, s"$path.amounts[$i]") }
        _ <- noDuplicates(rows.map(row => s"${row.optionKind}/${row.optionId}"),
          s"$path.amounts")
      } yield DecisionAnswerWire.DistributeWire(rows)
```

and beside `decodeDecisionPlacement`:

```scala
  private def decodeDistributeAmount(value: ujson.Value, path: String)
      : Either[ProtocolDecodeFailure, DistributeAmountWire] = obj(value, path).flatMap { row => for {
    _ <- exact(row, Set("optionKind", "optionId", "amount"), path)
    optionKind <- string(row, "optionKind", path)
    optionId <- string(row, "optionId", path)
    amount <- field(row, "amount", path).flatMap(integer(_, s"$path.amount"))
  } yield DistributeAmountWire(optionKind, optionId, amount) }
```

In `GameIntentMapper.decisionAnswer` (import `DistributeAnswer` and `DistributeAmount`):

```scala
    case DecisionAnswerWire.DistributeWire(amounts) =>
      traverse(amounts)(row => optionRef(row.optionKind, row.optionId,
        "$.intent.payload.amounts.option").map(
          DistributeAmount(_, row.amount))).map(DistributeAnswer)
```

Add to `CommandProtocolSuite`:

```scala
  test("a distribute payload round-trips, and one naming a slot twice is " +
      "rejected at its exact path") {
    val intent = GameIntent.ResolveWalker("rest.league-treaty.distribution",
      DecisionAnswerWire.DistributeWire(Vector(
        DistributeAmountWire("favor-bank", "arcane", 0),
        DistributeAmountWire("favor-bank", "nomad", 3))))
    val command = ActorlessCommand(0L, intent)
    assertEquals(ActorlessCommandCodec.decode(
      ActorlessCommandCodec.encode(command)), Right(command))
    val row = """{"optionKind":"favor-bank","optionId":"nomad","amount":1}"""
    val json = """{"expectedNextSequence":0,"intent":{"type":"resolveWalker",""" +
      """"decisionId":"d","payload":{"kind":"distribute",""" +
      s""""amounts":[$row,$row]}}}"""
    assertEquals(ActorlessCommandCodec.decode(json).left.toOption.get.path,
      "$.intent.payload.amounts")
  }
```

Use the file's existing names for `ActorlessCommand` and its codec's `encode`. If the suite spells them differently, follow the neighbouring "a partition payload placing one option twice" test.

In `GameHttpWireSuite`, find the test that posts a `resolveWalker` partition payload and add a distribute case beside it. Assert that the mapped command is `GameCommand.ResolveWalker(actor, TreeDecision(id, DistributeAnswer(Vector(DistributeAmount(DecisionOptionRef.FavorBank(Suit.Nomad), 3), ...))))`.

- [ ] **Step 8: Projection DTO and codec**

In `ActionProjectionDtos.scala`, append to `DecisionQueryProjection`:

```scala
    confirmLabel: Option[String] = None,
    slots: Vector[DecisionSlotProjection] = Vector.empty,
    total: Option[Int] = None)
```

Document both fields in the class doc: "`slots` and `total` are a `distribute` form's whole content. That form's `options` is empty, because every option it offers sits on a slot." Add:

```scala
/** One amount-taking slot of a `distribute` query: its option, presented
  * exactly as a choose-one option is, its bounds, and the amount a draft
  * opens at when the query suggests one.
  */
final case class DecisionSlotProjection(option: DecisionOptionProjection,
    minimum: Int, maximum: Int, suggested: Option[Int])
```

In `ActionProjectionCodec.scala`, extract the option-row pair into two private helpers `encodeOptionRow(row: DecisionOptionProjection): ujson.Value` and `decodeOptionRow(raw: ujson.Value, child: String): Result[DecisionOptionProjection]`, with bodies copied from the existing inline lambdas. Use them for `options`. Then extend both functions:

```scala
    "confirmLabel" -> stringOption(value.confirmLabel),
    "slots" -> encoded(value.slots)(slot => ujson.Obj(
      "option" -> encodeOptionRow(slot.option), "minimum" -> slot.minimum,
      "maximum" -> slot.maximum,
      "suggested" -> slot.suggested.fold[ujson.Value](ujson.Null)(ujson.Num(_)))),
    "total" -> value.total.fold[ujson.Value](ujson.Null)(ujson.Num(_)))
```

```scala
    _ <- exact(value, Set("form", "options", "sections", "heading",
      "confirmLabel", "slots", "total"), path)
    // ... existing fields ...
    slotRaws <- array(value, "slots", path)
    slots <- traverse(slotRaws, s"$path.slots") { (raw, child) => for {
      row <- obj(raw, child)
      _ <- exact(row, Set("option", "minimum", "maximum", "suggested"), child)
      option <- field(row, "option", child).flatMap(decodeOptionRow(_, s"$child.option"))
      minimum <- int(row, "minimum", child)
      maximum <- int(row, "maximum", child)
      suggested <- optionalInt(row, "suggested", child)
    } yield DecisionSlotProjection(option, minimum, maximum, suggested) }
    total <- optionalInt(value, "total", path)
  } yield DecisionQueryProjection(form, options, sections, heading,
    confirmLabel, slots, total)
```

If `ProjectionCodecSupport` has no `field(obj, name, path)`, read the key the way `optionalAbsent` does in the same file.

In `frontend/.../package.scala`, after the `DecisionSectionState` alias:

```scala
  type DecisionSlotState = protocol.projection.DecisionSlotProjection
  val DecisionSlotState = protocol.projection.DecisionSlotProjection
```

Add to `ProjectionProtocolSuite`:

```scala
  test("a distribute query round-trips its slots, suggestions and total") {
    def bank(id: String) = DecisionOptionProjection("favor-bank", id, id)
    val distribute = DecisionQueryProjection("distribute", Vector.empty,
      heading = Some("League Treaty"), confirmLabel = Some("Move favor"),
      slots = Vector(DecisionSlotProjection(bank("arcane"), 0, 2, Some(2)),
        DecisionSlotProjection(bank("nomad"), 0, 6, None)),
      total = Some(6))
    val carrying = projection.copy(walkerDecision =
      projection.walkerDecision.map(_.copy(query = Some(distribute))))
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(carrying)),
      Right(carrying))
  }
```

(Test data only: the query mixes a suggestion with an absent one to exercise both codec paths.)

- [ ] **Step 9: Project the query in `WalkerDecisionProjector.scala`**

Replace the `waiting` heading match (lines 90-93) with `.flatMap(_.query.heading)`:

```scala
    ProcedureWalker.parkedDecide(context.ready, parked.tree, parked.pending,
      parked.powers).flatMap(_.query.heading))
```

In `queryProjection`, add after the `Partition` case:

```scala
      case DecisionQuery.Distribute(slots, total, heading, confirmLabel) =>
        described(slots.map(_.option)).map(options =>
          DecisionQueryProjection("distribute", Vector.empty,
            heading = heading, confirmLabel = confirmLabel,
            slots = slots.zip(options).map { case (slot, option) =>
              DecisionSlotProjection(option, slot.minimum, slot.maximum,
                slot.suggested) },
            total = Some(total)))
```

In `optionProjection`, add
`case DecisionOption.FavorBank(bank) => row(presentation.safeLabel(bank.suit.key))`. Update the doc's "A `Deck` is a closed four-case enum" sentence to also cover a favor bank.

Add to `WalkerDecisionProjectorSuite` a test that parks the owner on a one-node `Sequence(Decide("test.distribute", actor, DecisionQuery.Distribute(...)))` substituted through the `TreeSource` seam, as the suite's Roll-park test does. Assert that `project` returns `query.form == "distribute"`, `options == Vector.empty`, each slot's `option.kind == "favor-bank"` and `option.id == suit key`, the declared bounds and suggestions, and `total == Some(6)`.

- [ ] **Step 10: Run the affected suites**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.DecisionQuerySuite oathdigital.serialization.GameEventWireSuite oathdigital.application.WalkerDecisionProjectorSuite oathdigital.server.GameHttpWireSuite" "frontend/testOnly oathdigital.protocol.CommandProtocolSuite oathdigital.protocol.ProjectionProtocolSuite"`
Expected: PASS.

- [ ] **Step 11: Run the full gate and commit**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS" && python3 scripts/check-architecture.py && git diff --check`
Expected: all green.

```bash
git add -A src/main/scala/oathdigital/model/Decisions.scala src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala src/main/scala/oathdigital/serialization/DecisionAnswerCodec.scala src/main/scala/oathdigital/application shared frontend/src/main/scala/oathdigital/frontend/package.scala src/test
git commit -m "feat(walker): add the Distribute decision and favor-bank options

Distribute spreads an exact total across bounded slots, and is the first
decision shape that carries amounts. It is validated, journalled,
projected and accepted on the command wire.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

### Task 2: Distribute panel with Shift+click fill and drain

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/DistributeDecisionState.scala`
- Create: `frontend/src/main/scala/oathdigital/frontend/DistributePanelRenderer.scala`
- Create: `frontend/src/test/scala/oathdigital/frontend/RecordingServerUiView.scala` (moved out of `PartitionPanelRenderSuite`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala:18-19` (trait members)
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala:99-102,392-393`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala:343-345`
- Modify: `frontend/src/test/scala/oathdigital/frontend/PartitionPanelRenderSuite.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/DistributeDecisionStateSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/DistributePanelRenderSuite.scala`

**Interfaces:**
- Consumes: `DecisionQueryState` with `form == "distribute"`, `slots: Vector[DecisionSlotState]` and `total: Option[Int]` (Task 1); `DecisionAnswerWire.DistributeWire` and `DistributeAmountWire` (Task 1); `WalkerPartitionDraft.itemId`.
- Produces:
  - `DistributeDecisionState(slots: Vector[DistributeSlotBounds], total: Int, amounts: Map[String, Int])` with `amount`, `remaining`, `increment`, `decrement`, `fill`, `drain`, `canConfirm`, and `DistributeDecisionState.opened(slots, total, suggested: Option[Vector[Int]])`.
  - `WalkerDistributeDraft(context, decisionId, query, state)` with `reconcile` and `command: Option[GameIntent.ResolveWalker]`.
  - `ServerUiView.currentWalkerDistribution` getter and setter.
  - `DistributePanelRenderer.render(value, presentation, canControl, panel, ui)`.

- [ ] **Step 1: Write the failing state tests**

Create `DistributeDecisionStateSuite.scala`:

```scala
package oathdigital.frontend

/** The stepper interaction behind a parked distribution, with no idea what
  * it distributes: slots are opaque ids with bounds, and confirmation is
  * exactly `nothing left to place`.
  */
class DistributeDecisionStateSuite extends munit.FunSuite {
  private val slots = Vector(
    DistributeSlotBounds("arcane", 0, 2),
    DistributeSlotBounds("discord", 0, 2),
    DistributeSlotBounds("nomad", 1, 6))
  private val atMinimums = DistributeDecisionState.opened(slots, 5, None)

  test("a draft with no suggestions opens at the minimums") {
    assertEquals(slots.map(s => atMinimums.amount(s.item)), Vector(0, 0, 1))
    assertEquals(atMinimums.remaining, 4)
    assert(!atMinimums.canConfirm)
  }

  test("a draft with suggestions opens at them") {
    val suggested = DistributeDecisionState.opened(slots, 5,
      Some(Vector(2, 2, 1)))
    assertEquals(slots.map(s => suggested.amount(s.item)), Vector(2, 2, 1))
    assert(suggested.canConfirm)
  }

  test("increment stops at the slot maximum and decrement at its minimum") {
    val raised = atMinimums.increment("arcane").increment("arcane")
      .increment("arcane")
    assertEquals(raised.amount("arcane"), 2)
    assertEquals(atMinimums.decrement("nomad").amount("nomad"), 1)
    assertEquals(atMinimums.increment("missing"), atMinimums)
  }

  test("increment stops when nothing remains") {
    val spent = atMinimums.fill("nomad")
    assertEquals(spent.remaining, 0)
    assertEquals(spent.increment("arcane"), spent)
  }

  test("fill is limited by what remains") {
    val partly = atMinimums.fill("nomad")
    assertEquals(partly.amount("nomad"), 5)
    assertEquals(partly.remaining, 0)
  }

  test("fill is limited by the slot maximum") {
    val capped = atMinimums.fill("arcane")
    assertEquals(capped.amount("arcane"), 2)
    assertEquals(capped.remaining, 2)
  }

  test("fill with nothing remaining changes nothing") {
    val spent = atMinimums.fill("nomad")
    assertEquals(spent.fill("discord"), spent)
  }

  test("drain lowers a slot to its minimum") {
    val drained = atMinimums.fill("nomad").drain("nomad")
    assertEquals(drained.amount("nomad"), 1)
    assertEquals(drained.remaining, 4)
  }

  test("confirmation is enabled exactly when nothing remains") {
    assert(!atMinimums.increment("arcane").canConfirm)
    assert(atMinimums.fill("arcane").fill("discord").canConfirm)
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.DistributeDecisionStateSuite"`
Expected: compilation fails, because `DistributeDecisionState` is not defined.

- [ ] **Step 3: Implement `DistributeDecisionState.scala`**

```scala
package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DistributeAmountWire,
  GameIntent => GameCommand}

/** One slot's identity and bounds, as the stepper sees them. */
private[frontend] final case class DistributeSlotBounds(item: String,
    minimum: Int, maximum: Int)

/** The stepper interaction, with no idea what it is distributing.
  *
  * Every move is clamped twice: to the slot's own bounds, and, when raising,
  * to what is left of the total. So no sequence of clicks builds an
  * over-allocated draft, and `canConfirm` is exactly `remaining == 0`.
  */
private[frontend] final case class DistributeDecisionState(
    slots: Vector[DistributeSlotBounds],
    total: Int,
    amounts: Map[String, Int]
) {
  def amount(item: String): Int = amounts.getOrElse(item, 0)

  def remaining: Int = total - slots.map(slot => amount(slot.item)).sum

  def increment(item: String): DistributeDecisionState = raise(item, 1)

  /** Raises a slot by as much as both its maximum and the remainder allow. */
  def fill(item: String): DistributeDecisionState = raise(item, Int.MaxValue)

  def decrement(item: String): DistributeDecisionState = lower(item, 1)

  /** Lowers a slot to its minimum. */
  def drain(item: String): DistributeDecisionState = lower(item, Int.MaxValue)

  def canConfirm: Boolean = remaining == 0

  private def raise(item: String, by: Int): DistributeDecisionState =
    slot(item).fold(this) { bounds =>
      val step = math.min(by, math.min(remaining,
        bounds.maximum - amount(item)))
      if (step <= 0) this else set(item, amount(item) + step)
    }

  private def lower(item: String, by: Int): DistributeDecisionState =
    slot(item).fold(this) { bounds =>
      val step = math.min(by, amount(item) - bounds.minimum)
      if (step <= 0) this else set(item, amount(item) - step)
    }

  private def slot(item: String): Option[DistributeSlotBounds] =
    slots.find(_.item == item)

  private def set(item: String, value: Int): DistributeDecisionState =
    copy(amounts = amounts.updated(item, value))
}

private[frontend] object DistributeDecisionState {
  /** Opens at `suggested` when the query carries one, else at the minimums. */
  def opened(slots: Vector[DistributeSlotBounds], total: Int,
      suggested: Option[Vector[Int]]): DistributeDecisionState =
    DistributeDecisionState(slots, total, slots.map(_.item).zip(
      suggested.filter(_.size == slots.size)
        .getOrElse(slots.map(_.minimum))).toMap)
}

/** A draft answer to a parked walker distribution. Like
  * [[WalkerPartitionDraft]], everything it knows comes from the projected
  * query, so the submitted `DistributeWire` names each projected slot once.
  */
private[frontend] final case class WalkerDistributeDraft(
    context: BoardSelectionContext,
    decisionId: String,
    query: DecisionQueryState,
    state: DistributeDecisionState
) {
  def increment(item: String): WalkerDistributeDraft = copy(state = state.increment(item))
  def decrement(item: String): WalkerDistributeDraft = copy(state = state.decrement(item))
  def fill(item: String): WalkerDistributeDraft = copy(state = state.fill(item))
  def drain(item: String): WalkerDistributeDraft = copy(state = state.drain(item))
  def canConfirm: Boolean = state.canConfirm

  def command: Option[GameCommand.ResolveWalker] =
    Option.when(canConfirm)(GameCommand.ResolveWalker(decisionId,
      DecisionAnswerWire.DistributeWire(query.slots.map(slot =>
        DistributeAmountWire(slot.option.kind, slot.option.id,
          state.amount(WalkerPartitionDraft.itemId(slot.option)))))))
}

private[frontend] object WalkerDistributeDraft {
  /** Adopts whichever parked decision projects a distribute query, and drops
    * the draft when the decision, the query or the board context changes.
    */
  def reconcile(previous: Option[WalkerDistributeDraft],
      context: BoardSelectionContext, decision: Option[WalkerDecisionState])
      : Option[WalkerDistributeDraft] =
    decision.flatMap(parked => parked.query.filter(_.form == "distribute")
        .map(parked.decisionId -> _))
      .map { case (decisionId, query) =>
        previous.filter(draft => draft.context == context &&
            draft.decisionId == decisionId && draft.query == query)
          .getOrElse(WalkerDistributeDraft(context, decisionId, query,
            DistributeDecisionState.opened(query.slots.map(slot =>
              DistributeSlotBounds(WalkerPartitionDraft.itemId(slot.option),
                slot.minimum, slot.maximum)), query.total.getOrElse(0),
              Option.when(query.slots.nonEmpty &&
                query.slots.forall(_.suggested.nonEmpty))(
                query.slots.flatMap(_.suggested)))))
      }
}
```

- [ ] **Step 4: Run the state suite**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.DistributeDecisionStateSuite"`
Expected: PASS.

- [ ] **Step 5: Move the recording view into a shared test file**

Cut `private final class RecordingView` out of `PartitionPanelRenderSuite.scala` and paste it unchanged into `frontend/src/test/scala/oathdigital/frontend/RecordingServerUiView.scala`, as `private[frontend] final class RecordingView(...)` with the same imports it uses. Add to it:

```scala
  var currentWalkerDistribution: Option[WalkerDistributeDraft] = None
```

(Adjust to the partition field's style: the getter and setter pair `currentWalkerPartition` uses.)

Add to `trait ServerUiView`, after `currentWalkerPartition_=`:

```scala
  def currentWalkerDistribution: Option[WalkerDistributeDraft]
  def currentWalkerDistribution_=(value: Option[WalkerDistributeDraft]): Unit
```

In `ServerModeUi.scala`, add `var walkerDistributeDraft: Option[WalkerDistributeDraft] = None` beside `walkerPartitionDraft`. Reconcile it right after the partition reconcile:

```scala
          walkerDistributeDraft = WalkerDistributeDraft.reconcile(
            walkerDistributeDraft,
            BoardSelectionContext(gameId, selectedPlayer,
              displayed.nextSequence), displayed.walkerDecision)
```

Implement the pair beside `currentWalkerPartition`:

```scala
      def currentWalkerDistribution = walkerDistributeDraft
      def currentWalkerDistribution_=(value: Option[WalkerDistributeDraft]) = walkerDistributeDraft = value
```

- [ ] **Step 6: Write the failing render tests**

Create `DistributePanelRenderSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DistributeAmountWire,
  GameIntent => Intent}
import org.scalajs.dom
import scala.scalajs.js

/** The Distribute panel at the DOM: rows, steppers, Shift+click and the
  * confirm button's enablement, all read off the rendered tree.
  */
class DistributePanelRenderSuite extends munit.FunSuite {
  private def bank(id: String, label: String) =
    DecisionOptionState("favor-bank", id, label)
  private val query = DecisionQueryState("distribute", Vector.empty,
    heading = Some("League Treaty"), confirmLabel = Some("Move favor"),
    slots = Vector(DecisionSlotState(bank("arcane", "Arcane"), 0, 2, Some(2)),
      DecisionSlotState(bank("nomad", "Nomad"), 0, 4, Some(0))),
    total = Some(2))
  private val parked = WalkerDecisionState("begin-rest",
    "rest.league-treaty.distribution", "decide", query = Some(query))

  private def projection: GameProjection = GameProjection("game", 9L, "rest",
    Some("red"), Vector.empty, Vector.empty, Vector.empty, Vector.empty,
    ready = true, completed = false, walkerDecision = Some(parked))

  private val presentation = ServerUiSupport.ViewerPresentation(
    showGameplayControls = true, None, None)

  private def opened(): RecordingView = {
    val ui = new RecordingView("game", "red")
    ui.currentWalkerDistribution = WalkerDistributeDraft.reconcile(None,
      BoardSelectionContext("game", "red", 9), Some(parked))
    ui
  }

  private def render(ui: RecordingView, canControl: Boolean = true): dom.Element = {
    val panel = dom.document.createElement("div")
    DistributePanelRenderer.render(projection, presentation, canControl, panel, ui)
    panel
  }

  private def one(root: dom.Element, selector: String): dom.Element = {
    val found = root.querySelectorAll(selector)
    assertEquals(found.length, 1, s"expected exactly one $selector")
    found(0).asInstanceOf[dom.Element]
  }

  private def amount(panel: dom.Element, item: String): String =
    one(panel, s"""[data-option-id="$item"] .distribute-amount""").textContent

  private def click(element: dom.Element, shift: Boolean = false): Unit =
    element.dispatchEvent(new dom.MouseEvent("click", js.Dynamic.literal(
      bubbles = true, shiftKey = shift).asInstanceOf[dom.MouseEventInit]))

  test("the panel renders one row per slot at its suggested amount") {
    val panel = render(opened())
    assertEquals(one(panel, "h2").textContent, "League Treaty")
    assertEquals(amount(panel, "favor-bank:arcane"), "2")
    assertEquals(amount(panel, "favor-bank:nomad"), "0")
    assertEquals(one(panel, """[data-option-id="favor-bank:nomad"] .distribute-maximum""")
      .textContent, "max 4")
    assertEquals(one(panel, """[data-option-id="favor-bank:nomad"] .distribute-increment""")
      .getAttribute("title"), "Shift+click: all")
    assertEquals(one(panel, """[data-option-id="favor-bank:nomad"] .distribute-decrement""")
      .getAttribute("title"), "Shift+click: all")
  }

  test("a plain click steps by one and a Shift+click drains or fills") {
    val ui = opened()
    click(one(render(ui), """[data-option-id="favor-bank:arcane"] .distribute-decrement"""))
    assertEquals(amount(render(ui), "favor-bank:arcane"), "1")
    click(one(render(ui), """[data-option-id="favor-bank:arcane"] .distribute-decrement"""),
      shift = true)
    assertEquals(amount(render(ui), "favor-bank:arcane"), "0")
    click(one(render(ui), """[data-option-id="favor-bank:nomad"] .distribute-increment"""),
      shift = true)
    assertEquals(amount(render(ui), "favor-bank:nomad"), "2")
    assert(ui.rerenders >= 3)
  }

  test("confirm is enabled exactly when nothing remains and submits the amounts") {
    val ui = opened()
    click(one(render(ui), """[data-option-id="favor-bank:arcane"] .distribute-decrement"""))
    val short = render(ui)
    val confirm = one(short, ".distribute-confirm").asInstanceOf[dom.html.Button]
    assert(confirm.disabled)
    click(one(short, """[data-option-id="favor-bank:nomad"] .distribute-increment"""))
    val ready = one(render(ui), ".distribute-confirm").asInstanceOf[dom.html.Button]
    assertEquals(ready.textContent, "Move favor")
    assert(!ready.disabled)
    ready.click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker(
      "rest.league-treaty.distribution", DecisionAnswerWire.DistributeWire(Vector(
        DistributeAmountWire("favor-bank", "arcane", 1),
        DistributeAmountWire("favor-bank", "nomad", 1))))))
  }

  test("a viewer who cannot control sees disabled steppers and confirm") {
    val panel = render(opened(), canControl = false)
    assert(one(panel, """[data-option-id="favor-bank:nomad"] .distribute-increment""")
      .asInstanceOf[dom.html.Button].disabled)
    assert(one(panel, ".distribute-confirm").asInstanceOf[dom.html.Button].disabled)
  }
}
```

`RecordingView.submitted` and `rerenders` already exist on the moved class. If `GameProjection`'s positional shape differs from `PartitionPanelRenderSuite.projectionWith`, copy that function's argument list.

- [ ] **Step 7: Run it to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.DistributePanelRenderSuite"`
Expected: compilation fails, because `DistributePanelRenderer` is not defined.

- [ ] **Step 8: Implement `DistributePanelRenderer.scala`**

```scala
package oathdigital.frontend

import org.scalajs.dom

/** The Distribute panel. It sits in its own file rather than in
  * `ActionDecisionRenderer`, which has no headroom. Like the partition panel,
  * it renders only what the projected query declares: a row per slot, with
  * its label, a stepper and its maximum.
  */
private[frontend] object DistributePanelRenderer {
  import ServerUiSupport.{ViewerPresentation, button, element, text}

  val AllTooltip = "Shift+click: all"

  def render(value: GameProjection, presentation: ViewerPresentation,
      canControl: Boolean, panel: dom.Element, ui: ServerUiView): Unit =
    value.walkerDecision.filter(_ => presentation.showGameplayControls)
        .flatMap(decision => decision.query.filter(_.form == "distribute")
          .map(decision -> _))
        .foreach { case (decision, query) =>
      panel.appendChild(text("h2", "", query.heading.getOrElse("Resolve decision")))
      ui.currentWalkerDistribution.filter(_.decisionId == decision.decisionId)
          .foreach { draft =>
        val rows = element("div", "distribute-slots")
        query.slots.foreach(slot => rows.appendChild(row(slot, draft, canControl, ui)))
        panel.appendChild(rows)
        panel.appendChild(text("p", "distribute-remaining",
          s"Remaining: ${draft.state.remaining}"))
        val confirm = button(query.confirmLabel.getOrElse("Confirm"),
          "distribute-confirm")
        confirm.disabled = !canControl || !draft.canConfirm
        confirm.onclick = _ => draft.command.foreach(ui.submitCommand)
        panel.appendChild(confirm)
      }
    }

  private def row(slot: DecisionSlotState, draft: WalkerDistributeDraft,
      canControl: Boolean, ui: ServerUiView): dom.Element = {
    val item = WalkerPartitionDraft.itemId(slot.option)
    val node = element("div", "distribute-slot")
    node.setAttribute("data-option-id", item)
    node.appendChild(slot.option.card.fold(
      text("span", "distribute-label", slot.option.label))(
      ServerUiSupport.cardDetailsPopover))
    node.appendChild(stepper("−", "distribute-decrement", canControl, ui,
      shift => if (shift) draft.drain(item) else draft.decrement(item)))
    node.appendChild(text("span", "distribute-amount",
      draft.state.amount(item).toString))
    node.appendChild(stepper("+", "distribute-increment", canControl, ui,
      shift => if (shift) draft.fill(item) else draft.increment(item)))
    node.appendChild(text("span", "distribute-maximum", s"max ${slot.maximum}"))
    node
  }

  private def stepper(label: String, className: String, canControl: Boolean,
      ui: ServerUiView, next: Boolean => WalkerDistributeDraft): dom.Element = {
    val control = button(label, className)
    control.setAttribute("title", AllTooltip)
    control.disabled = !canControl
    control.onclick = (event: dom.MouseEvent) => {
      ui.currentWalkerDistribution = Some(next(event.shiftKey))
      ui.rerender()
    }
    control
  }
}
```

In `ActionDecisionRenderer.scala`, after the `renderPartitionPanel` call:

```scala
   DistributePanelRenderer.render(value, presentation, canControl, panel, ui)
```

- [ ] **Step 9: Run the frontend suites**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.DistributeDecisionStateSuite oathdigital.frontend.DistributePanelRenderSuite oathdigital.frontend.PartitionPanelRenderSuite oathdigital.frontend.ServerModeUiSuite"`
Expected: PASS.

- [ ] **Step 10: Run the full gate and commit**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS" && python3 scripts/check-architecture.py && git diff --check`
Expected: all green.

```bash
git add frontend
git commit -m "feat(frontend): render Distribute decisions with Shift+click steppers

A pure DistributeDecisionState clamps every step to its slot's bounds and
the remaining total. Its panel lives in its own renderer file for headroom.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

### Task 3: `BeginTurn` and card power sources

**Files:**
- Modify: `src/main/scala/oathdigital/model/GameState.scala` (`PowerSourceRef`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/CoreOperations.scala:503` (after `SetOathkeeper`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationStateMutation.scala:305-337`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationError.scala:112` (after `PhaseAlreadyEntered`)
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala:48-60,205-225`
- Test: `src/test/scala/oathdigital/gameplay/operations/OperationStateMutationSuite.scala`, `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala:526-607`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `final case class BeginTurn(player: PlayerId, phase: Phase) extends PrimitiveOperation`: sets `turn = TurnState(player, phase, Set.empty)`. It rejects an unseated player (`OperationError.UnknownPlayer`) and a phase outside `{Wake, RoundEnd}` (`OperationError.InvalidTurnPhase(phase)`, code `"invalid-turn-phase"`).
  - `PowerSourceRef.Card(id: CardId)`.
  - Journal kinds: `"begin-turn"` `{playerId, phase}`; `"record-power-use"` with `siteId` for a site source, or `cardKind` and `cardId` for a card source.

- [ ] **Step 1: Write the failing mutation tests**

Append to `OperationStateMutationSuite` (it already imports `oathdigital.model._` and has `ready` and `playerId`):

```scala
  private def begin(ready: ReadyGame, operation: BeginTurn) =
    new OperationExecutor().executeAll(ready, Vector(operation))
      .map(_.game.current.turn)

  test("BeginTurn hands the turn to a seated player and clears used powers") {
    val used = PowerUseRef(PowerTiming.Rest,
      PowerSourceRef.Card(DenizenId("92")), PowerId("denizen.silver-tongue"))
    val resting = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(turn = TurnState(playerId, Phase.Rest,
        Set(used)))))
    assertEquals(begin(resting, BeginTurn(playerId, Phase.Wake)),
      Right(TurnState(playerId, Phase.Wake, Set.empty)))
    assertEquals(begin(resting, BeginTurn(playerId, Phase.RoundEnd)),
      Right(TurnState(playerId, Phase.RoundEnd, Set.empty)))
  }

  test("BeginTurn rejects an unseated player and a phase no turn begins in") {
    assertEquals(begin(ready, BeginTurn(PlayerId("nobody"), Phase.Wake))
      .left.map(_.code), Left("unknown-player"))
    Vector(Phase.Act, Phase.Rest, Phase.WarExhaustion).foreach { phase =>
      assertEquals(begin(ready, BeginTurn(playerId, phase)).left.map(_.code),
        Left("invalid-turn-phase"), phase.key)
    }
  }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.OperationStateMutationSuite"`
Expected: compilation fails, because `BeginTurn` and `PowerSourceRef.Card` are not defined.

- [ ] **Step 3: Implement the primitive and the source**

In `GameState.scala`, inside `object PowerSourceRef`, after `Site`:

```scala
  /** A card whose printed power was used: a denizen, relic, edifice or
    * vision, named globally. Where the card sat when it was used is not part
    * of the use's identity, just as a decision option names a card without
    * its location.
    */
  final case class Card(id: CardId) extends PowerSourceRef
```

In `CoreOperations.scala`, after `SetOathkeeper`:

```scala
/** Hands the turn to `player` in `phase`, clearing every power use recorded
  * against the turn that ended.
  *
  * Finish Rest is the only procedure that declares it. As with
  * [[EnterPhase]], which player follows whom is not stated here: that is the
  * turn order Finish Rest reads. What applying this does reject is a player
  * who is not seated, and a phase no turn begins in. A turn begins in Wake,
  * or in RoundEnd when the last player has rested.
  */
final case class BeginTurn(player: PlayerId, phase: Phase)
    extends PrimitiveOperation
```

In `OperationError.scala`, after `PhaseAlreadyEntered`:

```scala
  /** A turn handed over in a phase no turn begins in. */
  final case class InvalidTurnPhase(phase: Phase) extends OperationError {
    override val code: String = "invalid-turn-phase"
    override val detail: String =
      s"a turn cannot begin in the ${phase.productPrefix} phase"
  }
```

In `OperationStateMutation.scala`, after the `SetOathkeeper` fold case:

```scala
      case (result, BeginTurn(player, phase)) =>
        result.flatMap(beginTurn(_, player, phase))
```

and after `setOathkeeper`:

```scala
  private def beginTurn(ready: ReadyGame, player: PlayerId,
      phase: Phase): Either[OperationError, ReadyGame] =
    if (!ready.game.current.players.exists(_.player == player))
      Left(UnknownPlayer(player))
    else if (phase != Phase.Wake && phase != Phase.RoundEnd)
      Left(InvalidTurnPhase(phase))
    else Right(updateCurrent(ready)(current =>
      current.copy(turn = TurnState(player, phase, Set.empty))))
```

Run `grep -rn "SetOathkeeper" --include='*.scala' src/main`. Every production match that names `SetOathkeeper` must also name `BeginTurn`. Today the only such matches are the two codec arms and the mutation above.

- [ ] **Step 4: Run the mutation suite**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.OperationStateMutationSuite"`
Expected: compilation fails in `WalkerOperationCodec` ("match may not be exhaustive": `BeginTurn` and `PowerSourceRef.Card`).

- [ ] **Step 5: Journal both in `WalkerOperationCodec.scala`**

Import `BeginTurn`. Replace the site-only `RecordPowerUse` encode arm and add `BeginTurn`:

```scala
      case RecordPowerUse(PowerUseRef(timing, source, id)) =>
        ujson.Obj.from(Vector[(String, ujson.Value)](
          "kind" -> "record-power-use",
          "timing" -> encodePowerTiming(timing)) ++ (source match {
          case PowerSourceRef.Site(site) => Vector("siteId" -> ujson.Str(site.value))
          case PowerSourceRef.Card(card) => Vector(
            "cardKind" -> ujson.Str(card.kind), "cardId" -> ujson.Str(card.value))
        }) :+ ("powerId" -> ujson.Str(id.value)))
      case BeginTurn(player, phase) => ujson.Obj("kind" -> "begin-turn",
        "playerId" -> player.value, "phase" -> phase.key)
```

Replace the `"record-power-use"` decode arm and add `"begin-turn"`:

```scala
      case "record-power-use" => for {
        timing <- decodePowerTiming(value("timing").str, s"$path.timing")
        source <- if (value.obj.contains("siteId"))
            Right(PowerSourceRef.Site(SiteId(value("siteId").str)))
          else decodePowerCard(value("cardKind").str, value("cardId").str,
            s"$path.cardKind").map(PowerSourceRef.Card)
      } yield RecordPowerUse(PowerUseRef(timing, source,
        PowerId(value("powerId").str)))
      case "begin-turn" =>
        val key = value("phase").str
        Phase.fromKey(key).toRight(
          InvalidValue(s"$path.phase", s"unknown phase '$key'"))
          .map(BeginTurn(PlayerId(value("playerId").str), _))
```

Add beside `decodePowerTiming`:

```scala
  private def decodePowerCard(kind: String, id: String,
      path: String): Either[WireError, CardId] = kind match {
    case "denizen" => Right(DenizenId(id))
    case "relic" => Right(RelicId(id))
    case "edifice" => Right(EdificeId(id))
    case "vision" => Right(VisionId(id))
    case other => Left(InvalidValue(path, s"unknown power source card '$other'"))
  }
```

Before writing this, check `CardId.kind` for each of `DenizenId`, `RelicId`, `EdificeId` and `VisionId` in `model/Identity.scala`. The strings above must be exactly what `kind` returns. If a kind differs, match the value `kind` returns.

- [ ] **Step 6: Extend the codec round-trip list**

In `GameEventWireSuite`'s "every CoreOperation variant round-trips through the walker codec", add to `operations`:

```scala
      RecordPowerUse(PowerUseRef(PowerTiming.Wake,
        PowerSourceRef.Site(site), PowerId("site.take-wealth"))),
      RecordPowerUse(PowerUseRef(PowerTiming.Rest,
        PowerSourceRef.Card(denizen), PowerId("denizen.silver-tongue"))),
      RecordPowerUse(PowerUseRef(PowerTiming.Act,
        PowerSourceRef.Card(relic), PowerId("relic.test-power"))),
      EnterPhase(Phase.Act),
      BeginTurn(player, Phase.Wake),
      BeginTurn(other, Phase.RoundEnd),
```

Check that the suite imports `oathdigital.model._` and the walker operation names; add `BeginTurn`, `EnterPhase` and `RecordPowerUse` to its operations import if they are listed explicitly.

- [ ] **Step 7: Run both suites**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.OperationStateMutationSuite oathdigital.serialization.GameEventWireSuite"`
Expected: PASS.

- [ ] **Step 8: Run the full gate and commit**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS" && python3 scripts/check-architecture.py && git diff --check`
Expected: all green.

```bash
git add src
git commit -m "feat(walker): add BeginTurn and card power sources

BeginTurn hands the turn over with its used powers cleared, and a
PowerUseRef can now name the card whose power was used.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: Rest on the walker

**Files:**
- Modify: `src/main/scala/oathdigital/model/ProcedureRef.scala` (`BeginRest`, `FinishRest`)
- Create: `src/main/scala/oathdigital/gameplay/phases/rest/WarExhaustionRandomPort.scala`, `BeginRestProcedure.scala`, `FinishRestProcedure.scala`, `TurnBoundary.scala`
- Modify: `src/main/scala/oathdigital/gameplay/phases/Rest.scala` (trim to the legacy League Treaty resolve/decline only)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala:49-75,330-395`
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala:11-12,36-45,168-183,244-246,302`
- Modify: `src/main/scala/oathdigital/gameplay/model/GameProcedureProtocol.scala` (`AwaitingRestDecision`)
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala:16-17,84,486-490`
- Modify: `src/main/scala/oathdigital/application/LegalActionProjector.scala:13,127,158`
- Test: `src/test/scala/oathdigital/gameplay/RestSuite.scala`, `StateBasedEvaluationSuite.scala:8,136-161`, `src/test/scala/oathdigital/application/ForgeWalkerFixture.scala:114-126`, `GameApplicationServiceSuite.scala:575,601-605,1570-1591,1990-1998`, `src/test/scala/oathdigital/gameplay/walker/WalkerProcedureRegistrySuite.scala`

**Interfaces:**
- Consumes: `BeginTurn(player, phase)` (Task 3).
- Produces:
  - `PhaseTransitionRef.BeginRest` (key `"begin-rest"`), `PhaseTransitionRef.FinishRest` (key `"finish-rest"`).
  - `oathdigital.gameplay.phases.rest.WarExhaustionRandomPort` (moved, same members).
  - `BeginRestProcedure.validateBegin(catalog, state: OathState, player): Either[OathViolation, ReadyGame]`, `BeginRestProcedure.build(catalog, ready, player, args): Either[OathViolation, Operation]`.
  - `FinishRestProcedure.gate(catalog, ready, player): Either[OathViolation, ReadyGame]`, `FinishRestProcedure.build(...)` (same shape as `BeginRestProcedure.build`), `FinishRestProcedure.cleanup(catalog, ready, player): Either[OathViolation, Vector[CoreOperation]]`.
  - `TurnBoundary.finishRound(catalog, transition, port): Either[OathViolation, OathTransition]`.
  - `OathContinue.AwaitingRestDecision(playerId: PlayerId, decision: DecisionId)`.
  - `WalkerProcedureRegistry.fallbackKind(procedure: StartableRef): Either[OathViolation, Option[MajorActionKind]]`.
  - `OathRulesWalker` abstract members `turnBoundary(transition)` and `restPowerUsable(ready: ReadyGame, player: PlayerId): Boolean`. Task 7 replaces the stub body of `restPowerUsable`.

- [ ] **Step 1: Rewrite `RestSuite` against the walker (failing)**

Replace the imports with:

```scala
import oathdigital.gameplay.phases.RestCleanupPlan
import oathdigital.gameplay.phases.rest.WarExhaustionRandomPort
import oathdigital.gameplay.walker.WalkerCompleted
import oathdigital.model._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.OathEvent.IgnoredRulesRecorded
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.OathViolation.{UnsupportedRestState,
  UnsupportedRoundEndCatalogInventory, UnsupportedRuleCatalog}
import oathdigital.catalog.CatalogPower
```

Add helpers under `act`:

```scala
  private def rest(state: OathState, player: PlayerId,
      using: OathRules = rules) =
    using.startWalker(state, PhaseTransitionRef.BeginRest, player)

  private def inRest(ready: ReadyGame): ReadyGame = ready.copy(game =
    ready.game.copy(current = ready.game.current.copy(turn =
      ready.game.current.turn.copy(phase = Phase.Rest))))
```

Rewrite each existing test body, keeping its name unless stated:

1. "Rest returns controlled resources reveals secrets refreshes and wakes next": replace the `started`/`completed`/`event` lines with

```scala
    val completed = rest(Ready(prepared), actor.player).toOption.get
    assertEquals(completed.events.collect { case WalkerCompleted(p) => p },
      Vector(PhaseTransitionRef.BeginRest, PhaseTransitionRef.FinishRest))
    val Ready(after) = completed.state: @unchecked
    val rested = after.game.current.players.find(_.player == actor.player).get
    assertEquals(completed.continue,
      OathContinue.AwaitingWakeAction(after.game.current.turn.activePlayer))
```

   Delete the two `event.returned*` assertions; keep every other assertion (the bank `+ 3` and the faceup `+ 2 + 6` already pin them).
2. "Rest rejects a missing bounded warband supply": `rest(Ready(malformed), actor.player)`.
3. "Rest globally cleans every in-play denizen and relic": replace `began`/`finished` with `val finished = rest(Ready(prepared), actor.player).toOption.get`.
4. Rename "replay validates recorded Rest outcome and advances the round" to "replaying a round of walker Rests reproduces the state and advances the round":

```scala
  test("replaying a round of walker Rests reproduces the state and advances the round") {
    var state: OathState = Ready(act)
    var events = Vector.empty[OathEvent]
    val participants = act.game.current.players.map(_.player)
    val start = participants.indexOf(act.setup.firstPlayer)
    val order = participants.drop(start) ++ participants.take(start)
    order.foreach { player =>
      val rested = rest(state, player).toOption.get
      events ++= rested.events
      state = rested.state
      if (player != order.last) {
        val woke = rules.startWalker(state, PhaseTransitionRef.EndWake,
          state.asInstanceOf[Ready].value.game.current.turn.activePlayer).toOption.get
        events ++= woke.events
        state = woke.state
      }
    }
    val Ready(after) = state: @unchecked
    assertEquals(after.game.current.tracks.round, 2)
    assertEquals(after.game.current.turn.activePlayer, after.setup.firstPlayer)
    assertEquals(after.game.current.turn.phase, Phase.Wake)
    assert(events.exists(_.isInstanceOf[OathEvent.RoundEnded]))
    assertEquals(events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(act)))((next, event) => next.flatMap(rules.evolve(_, event))),
      Right(state))
  }
```

5. "unrelated active powers ...": `assert(rest(Ready(supported), supported.game.current.turn.activePlayer).isRight)`.
6. "each relevant Rest handler records fallback diagnostics without blocking": both `rules.handle(..., RestCommand.Begin(actor.player))` become `rest(Ready(...), actor.player)`. The diagnostics event stays `events.head`.
7. "changed inventory ...": `rest(Ready(base), actor, new OathRules(c))`.
8. "altered banner ...": `rest(Ready(ready), actor)`.
9. "last player of round eight ...": `val finished = rest(Ready(unsupported), last, deterministic).toOption.get` replaces `started`/`finished`; the `GameEnded` assertion becomes `rest(finished.state, last, deterministic).left.toOption.get`.
10. Delete "League Treaty gives its off-turn ruler an atomic optional Rest decision" and "League Treaty is omitted without a player ruler or regional favor". Task 5 replaces them.

Append:

```scala
  test("Finish Rest belongs to the active player in the Rest phase") {
    val ready = inRest(act)
    val actor = ready.game.current.turn.activePlayer
    val other = ready.game.current.players.map(_.player).find(_ != actor).get
    assertEquals(rules.startWalker(Ready(ready), PhaseTransitionRef.FinishRest,
      other).left.toOption, Some(OathViolation.WrongPlayer(actor, other)))
    assertEquals(rules.startWalker(Ready(act), PhaseTransitionRef.FinishRest,
      actor).left.toOption,
      Some(OathViolation.WrongPhase(Phase.Rest, Phase.Act)))
    val finished = rules.startWalker(Ready(ready), PhaseTransitionRef.FinishRest,
      actor).toOption.get
    assertEquals(finished.state.asInstanceOf[Ready].value.game.current.turn.phase,
      Phase.Wake)
    val projector = new oathdigital.application.GameProjector(catalog)
    def controls(state: ReadyGame) = projector.project("rest-controls",
      oathdigital.application.LoadedGame(Ready(state), 30L), actor).legalControls
    assertEquals(controls(ready), Vector("finishRest"))
    assert(!controls(act).contains("finishRest"))
  }

  test("Begin Rest records no Rest events and cannot run twice") {
    val actor = act.game.current.turn.activePlayer
    val rested = rest(Ready(act), actor).toOption.get
    assert(!rested.events.exists(_.getClass.getSimpleName.startsWith("Rest")))
    assertEquals(rules.startWalker(Ready(inRest(act)),
      PhaseTransitionRef.BeginRest, actor).left.toOption,
      Some(OathViolation.WrongPhase(Phase.Act, Phase.Rest)))
  }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.RestSuite"`
Expected: compilation fails on `PhaseTransitionRef.BeginRest` and `oathdigital.gameplay.phases.rest`.

- [ ] **Step 3: Add the references and the continuation**

In `ProcedureRef.scala`, `object PhaseTransitionRef`:

```scala
  /** Leaves Act for Rest (rest-walker spec, Rest procedure). */
  case object BeginRest extends PhaseTransitionRef { val key = "begin-rest" }
  /** Cleans up, refreshes Supply and hands the turn over. */
  case object FinishRest extends PhaseTransitionRef { val key = "finish-rest" }

  val all: Vector[PhaseTransitionRef] = Vector(EndWake, BeginRest, FinishRest)
```

In `GameProcedureProtocol.scala`, after `AwaitingRestAction`:

```scala
  /** Any decision parked inside Finish Rest; the owner may be off-turn. */
  final case class AwaitingRestDecision(playerId: PlayerId,
      decision: DecisionId) extends OathContinue
```

Run `grep -rn "AwaitingRestAction" --include='*.scala' src shared frontend`. Every exhaustive match over `OathContinue` that names `AwaitingRestAction` gains an `AwaitingRestDecision` arm with the same shape as its `AwaitingOathkeeperRecipient` arm.

- [ ] **Step 4: Create the Rest procedure package**

`gameplay/phases/rest/WarExhaustionRandomPort.scala`: move the trait and companion out of `Rest.scala` verbatim, with `package oathdigital.gameplay.phases.rest`. Update the imports in `OathRules`, `GameApplicationService` and `GameApplicationServiceSuite` (both inline `oathdigital.gameplay.phases.WarExhaustionRandomPort` references).

`gameplay/phases/rest/BeginRestProcedure.scala`:

```scala
package oathdigital.gameplay.phases.rest

import oathdigital.catalog.{CatalogHandlerInventory, ExecutableCatalog}
import oathdigital.gameplay.operations.{EnterPhase, Operation, Sequence}
import oathdigital.gameplay.{OathLifecycle, OathState, OathViolation, ReadyGame}
import oathdigital.gameplay.OathViolation._
import oathdigital.model._

/** Begin Rest: the Act-phase gate and the phase change, nothing else.
  *
  * Cleanup waits for Finish Rest so REST powers can be used in between. The
  * exile-only and handler-inventory checks stay here because Rest's cleanup
  * and Supply bands are only reviewed for that game.
  */
object BeginRestProcedure {
  private[rest] val ExpectedHandlerInventory =
    "5fc88b0d9622a3f523722c288ea7a78d0ec09b7ce191bdabc7f471139ec85898"

  def validateBegin(catalog: ExecutableCatalog, state: OathState,
      playerId: PlayerId): Either[OathViolation, ReadyGame] =
    OathLifecycle.validateAct(state, playerId).flatMap(ready =>
      validateSupportedState(catalog, ready).map(_ => ready))

  def validateSupportedState(catalog: ExecutableCatalog,
      ready: ReadyGame): Either[OathViolation, Unit] = {
    val game = ready.game
    val actual = CatalogHandlerInventory.structuralFingerprint(catalog)
    val missing = game.current.players.iterator
      .map(player => ForceKind.Exile(player.lineage)).toVector.distinct
      .filterNot(ready.banks.warbandSupply.contains)
    if (game.campaign.lineages.values.exists(_.role != Role.Exile))
      Left(UnsupportedRestState("Rest is limited to the exile-only first game"))
    else if (missing.nonEmpty)
      Left(UnsupportedRestState(
        s"no bounded warband supply for ${missing.mkString(", ")}"))
    else if (actual != ExpectedHandlerInventory)
      Left(UnsupportedRoundEndCatalogInventory(ExpectedHandlerInventory, actual))
    else Right(())
  }

  /** One leaf, so it finishes in the command that starts it; resume never
    * reaches this.
    */
  def build(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
    _ <- Either.cond(args.isEmpty, (), InvalidEventOrder(
      "Begin Rest selects nothing"))
    _ <- validateBegin(catalog, OathState.Ready(ready), player)
  } yield Sequence(Vector(EnterPhase(Phase.Rest)))
}
```

`gameplay/phases/rest/FinishRestProcedure.scala`:

```scala
package oathdigital.gameplay.phases.rest

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathViolation, ReadyGame}
import oathdigital.gameplay.OathViolation._
import oathdigital.gameplay.operations.{AdjustSupply, BeginTurn, BuildOps,
  CoreOperation, FlipSecrets, Location, Move, Operation, Piece,
  PositionedLocation, SecretSide, Sequence}
import oathdigital.gameplay.phases.RestCleanupPlan
import oathdigital.gameplay.powerresolver.PowerWindow
import oathdigital.model._

/** Finish Rest (rest-walker spec, `FinishRest`).
  *
  * {{{
  * Sequence(
  *   BuildOps(cleanup)        // window = RestReturnFavor
  *   BuildOps(supplyRefresh)
  *   BuildOps(beginNextTurn))
  * }}}
  *
  * Every leaf derives its operations at walk time, so a power folded in
  * front of cleanup (League Treaty) changes what cleanup finds. The gate
  * holds for the whole procedure: nothing before `BeginTurn` leaves Rest, so
  * resume rebuilds through the same function.
  */
object FinishRestProcedure {
  private val ExileSupply = SupplyRules(SupplyTrack.Maximum, Vector(
    SupplyRefreshBand(InclusiveIntRange(9, Int.MaxValue), 6),
    SupplyRefreshBand(InclusiveIntRange(4, 8), 5),
    SupplyRefreshBand(InclusiveIntRange(0, 3), 4)))

  def gate(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerId): Either[OathViolation, ReadyGame] = {
    val current = ready.game.current
    if (current.result.nonEmpty) Left(GameEnded)
    else if (current.turn.activePlayer != player)
      Left(WrongPlayer(current.turn.activePlayer, player))
    else if (current.turn.phase != Phase.Rest)
      Left(WrongPhase(Phase.Rest, current.turn.phase))
    else current.pending match {
      case Some(value) => Left(PendingProcedureBlocksAction(value.decision))
      case None => BeginRestProcedure.validateSupportedState(catalog, ready)
        .map(_ => ready)
    }
  }

  def build(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
    _ <- Either.cond(args.isEmpty, (), InvalidEventOrder(
      "Finish Rest selects nothing"))
    _ <- gate(catalog, ready, player)
  } yield Sequence(Vector(
    BuildOps((state, _) => cleanup(catalog, state, player),
      window = Some(PowerWindow.RestReturnFavor)),
    BuildOps((state, _) => supplyRefresh(state, player)),
    BuildOps((state, _) => beginNextTurn(state, player))))

  /** Card favor to its printed suit bank, card secrets to the resting
    * player, and the resting player's facedown stash flipped faceup.
    */
  def cleanup(catalog: ExecutableCatalog, ready: ReadyGame, resting: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] = for {
    plan <- RestCleanupPlan.derive(catalog, ready, resting)
      .left.map(UnsupportedRestState)
    player <- ready.game.current.players.find(_.player == resting)
      .toRight(UnsupportedRestState(s"unknown resting player $resting"))
  } yield {
    val from = (id: CardId) => PositionedLocation(Location.OnCard(id))
    val favor = plan.cards.collect {
      case card if card.suit.nonEmpty && card.favor > 0 =>
        Move(Piece.Favor(card.favor), from(card.id),
          PositionedLocation(Location.FavorBank(card.suit.get)))
    }
    val secrets = plan.cards.collect {
      case card if card.secrets > 0 => Move(Piece.Secrets(card.secrets),
        from(card.id), PositionedLocation(Location.PlayArea(resting)))
    }
    val reveal = Vector(player.board.faceDownSecrets).filter(_ > 0).map(count =>
      FlipSecrets(resting, count, SecretSide.FaceDown, SecretSide.FaceUp))
    favor ++ secrets ++ reveal
  }

  private def supplyRefresh(ready: ReadyGame, resting: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val current = ready.game.current
    for {
      player <- current.players.find(_.player == resting)
        .toRight(UnsupportedRestState(s"unknown resting player $resting"))
      kind = ForceKind.Exile(player.lineage)
      supply <- ready.banks.warbandSupply.get(kind)
        .toRight(UnsupportedRestState(s"no bounded warband supply for $kind"))
      siteWarbands = current.map.sites.valuesIterator.map(_.forces).collect {
        case SiteForces.Occupied(ForceKind.Exile(owner), count)
            if owner == player.lineage => count
      }.sum
      banked = math.max(0, supply - player.board.warbands - siteWarbands)
      refreshed <- ExileSupply.refresh(banked, player.board.supply.supply)
        .toRight(UnsupportedRestState(s"no Supply band for $banked banked warbands"))
    } yield Vector(refreshed.supply - player.board.supply.supply)
      .filter(_ != 0).map(AdjustSupply(resting, _))
  }

  private def beginNextTurn(ready: ReadyGame, resting: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val order = turnOrder(ready)
    val index = order.indexOf(resting)
    if (index < 0) Left(UnsupportedRestState(s"$resting is not in turn order"))
    else if (index == order.size - 1)
      Right(Vector(BeginTurn(order.head, Phase.RoundEnd)))
    else Right(Vector(BeginTurn(order(index + 1), Phase.Wake)))
  }

  private def turnOrder(ready: ReadyGame): Vector[PlayerId] = {
    val participants = ready.game.current.players.map(_.player)
    val index = participants.indexOf(ready.setup.firstPlayer)
    participants.drop(index) ++ participants.take(index)
  }
}
```

Before compiling, check `SupplyRules.refresh`'s return type in `model`. Legacy `Rest.expected` read `.supply` off its result, so it is a `SupplyTrack`; if it is an `Int`, drop `.supply` on `refreshed`.

`gameplay/phases/rest/TurnBoundary.scala`:

```scala
package oathdigital.gameplay.phases.rest

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathContinue, OathTransition, OathViolation,
  StateBasedEvaluation}
import oathdigital.gameplay.OathState.Ready

/** Round end after the last player's Finish Rest, moved unchanged from the
  * deleted `Rest.finishRound`.
  */
object TurnBoundary {
  def finishRound(catalog: ExecutableCatalog, transition: OathTransition,
      randomPort: WarExhaustionRandomPort)
      : Either[OathViolation, OathTransition] =
    StateBasedEvaluation.endRound(transition.state, randomPort.choose)
      .flatMap(_.foldLeft[Either[OathViolation, OathTransition]](
        Right(transition)) {
        case (Right(current), event) =>
          StateBasedEvaluation.evolve(catalog, current.state, event).map { next =>
            val continue = next match {
              case Ready(ready) if ready.game.current.result.nonEmpty =>
                OathContinue.GameFinished(ready.game.current.result.get.winner)
              case _ => current.continue
            }
            current.copy(state = next, events = current.events :+ event,
              continue = continue)
          }
        case (failure @ Left(_), _) => failure
      })
}
```

If `StateBasedEvaluation.evolve` or `endRound` is `private[gameplay]`, it is still visible from `gameplay.phases.rest`.

- [ ] **Step 5: Trim `Rest.scala`**

Delete `RestCommand.Begin` and `RestCommand.Finish`, the trait `WarExhaustionRandomPort` and its companion, `ExpectedHandlerInventory`, `ExileSupply`, `validateBegin`, `validateRest`, `validateSupportedState`, `validateAllExileAndRules`, `expected`, `applyCompletion`, `turnOrder`, `transition` and `finishRound`. What remains:

```scala
sealed trait RestCommand extends Product with Serializable
object RestCommand {
  final case class ResolvePower(playerId: PlayerId, decision: DecisionId,
      allocations: Vector[FavorAllocation], destinationBank: Suit)
      extends RestCommand
  final case class DeclinePower(playerId: PlayerId, decision: DecisionId)
      extends RestCommand
}

/** Legacy League Treaty resolution only, unreachable since Begin Rest moved
  * onto the walker. Task 6 deletes it.
  */
object Rest {
  def handle(catalog: ExecutableCatalog, state: OathState, command: RestCommand)
      : Either[OathViolation, OathTransition] = command match {
    case RestCommand.ResolvePower(playerId, decision, allocations, bank) =>
      RestPowerIntegration.resolveLeagueTreaty(catalog, state, playerId,
        decision, allocations, bank)
    case RestCommand.DeclinePower(playerId, decision) =>
      RestPowerIntegration.decline(catalog, state, playerId, decision)
  }

  def evolve(catalog: ExecutableCatalog, state: OathState, event: RestPowerEvent)
      : Either[OathViolation, OathState] =
    RestPowerIntegration.evolve(catalog, state, event)
}
```

Remove imports the compiler reports unused (`-Xlint`).

- [ ] **Step 6: Registry entries and optional fallback kind**

In `WalkerProcedureRegistry.scala` import `oathdigital.gameplay.phases.rest.{BeginRestProcedure, FinishRestProcedure}` and add after the `EndWake` entry:

```scala
    /** Records the Rest timing's ignored-rule diagnostics, as the legacy
      * `withFallback(MajorActionKind.Rest)` wrapper did.
      */
    PhaseTransitionRef.BeginRest -> Entry(
      fallbackKind = Some(MajorActionKind.Rest),
      rollDecisionId = None,
      modifierWindow = None,
      continuationFor = (_, _, _) => None,
      build = BeginRestProcedure.build,
      rebuild = BeginRestProcedure.build),

    /** Begin Rest already recorded the Rest diagnostics, so this declares no
      * fallback kind. Any decision a power parks inside it -- League Treaty's
      * off-turn ruler -- is a generic Rest decision, so this names no power.
      */
    PhaseTransitionRef.FinishRest -> Entry(
      fallbackKind = None,
      rollDecisionId = None,
      modifierWindow = None,
      continuationFor = (_, awaited, decision) =>
        Some(OathContinue.AwaitingRestDecision(awaited, decision)),
      build = FinishRestProcedure.build,
      rebuild = FinishRestProcedure.build),
```

Replace `fallbackKind` with:

```scala
  def fallbackKind(procedure: StartableRef)
      : Either[OathViolation, Option[MajorActionKind]] =
    lookup(procedure, entries).map(_.fallbackKind)
```

and change its doc's last sentence to: "`None` means the start records no fallback diagnostics."

- [ ] **Step 7: Walker completion in Rest**

In `OathRulesWalker.scala`, add abstract members:

```scala
  protected def turnBoundary(transition: OathTransition)
      : Either[OathViolation, OathTransition]
  /** Whether `player` could use a REST power now. Task 7 gives it a body. */
  protected def restPowerUsable(ready: ReadyGame, player: PlayerId): Boolean
```

In `startWalker`, replace the `fallbackKind(...).flatMap(kind => withFallback(state, activePlayer, kind) { ... })` wrapper with:

```scala
        def run = for {
          _ <- requireActivePlayer(ready, requester)
          _ <- validateModifiers(ready, activePlayer, procedure, modifiers)
          powers = walkerPowers(ready, activePlayer, modifiers)
          tree <- buildWalker(procedure, ready, activePlayer, startArgs,
            starting = true)
          _ <- checkRestrictions(tree, powers, ready, activePlayer)
          outcome <- walkerCall(ProcedureWalker.advance(ready, tree, None,
            powers))
          transition <- walkerTransition(state, procedure, tree,
            outcome, powers, modifiers, startArgs)
        } yield transition
        WalkerProcedureRegistry.fallbackKind(procedure).flatMap {
          case Some(kind) => withFallback(state, activePlayer, kind)(run)
          case None => run
        }
```

Replace the `Finished` arm of `walkerTransition` with:

```scala
    case WalkerOutcome.Finished(treeless, steps) =>
      val turn = treeless.game.current.turn
      val continued =
        if (runsTurnBoundary(procedure))
          Right(OathContinue.AwaitingWakeAction(turn.activePlayer))
        else continuationIn(turn.phase, turn.activePlayer)
      continued.flatMap(continue => GameplayTransition(state,
          steps :+ WalkerCompleted(procedure), continue)(evolve))
        .flatMap(transition =>
          if (runsActionBoundary(procedure)) completeAction(transition)
          else if (runsTurnBoundary(procedure)) turnBoundary(transition)
          else Right(transition))
        .flatMap(transition =>
          if (procedure == PhaseTransitionRef.BeginRest) autoFinishRest(transition)
          else Right(transition))
```

Add beside `runsActionBoundary`:

```scala
  /** Whether the turn boundary (round end, then Wake evaluation) follows a
    * completed procedure. Only Finish Rest hands the turn over; it owns its
    * continuation, so `continuationIn` is never asked about `RoundEnd`.
    */
  private def runsTurnBoundary(procedure: ProcedureRef): Boolean =
    procedure == PhaseTransitionRef.FinishRest

  /** Only after Begin Rest: a player with no usable REST power finishes Rest
    * in the same command. After a REST power is used the player always
    * finishes Rest deliberately, so nothing else calls this.
    */
  private def autoFinishRest(transition: OathTransition)
      : Either[OathViolation, OathTransition] = transition.state match {
    case Ready(ready) if !restPowerUsable(ready,
        ready.game.current.turn.activePlayer) =>
      startWalker(transition.state, PhaseTransitionRef.FinishRest,
        ready.game.current.turn.activePlayer).map(finished =>
        finished.copy(events = transition.events ++ finished.events))
    case _ => Right(transition)
  }
```

In `continuationIn`, add `case Phase.Rest => Right(OathContinue.AwaitingRestAction(actor))` and update its doc: Rest now has a continuation; `RoundEnd` still has none because only Finish Rest reaches it and the turn boundary owns that continuation. Update `startTriggered`'s doc sentence about `continuationIn` to name Act, Wake and Rest.

- [ ] **Step 8: `OathRules` wiring**

- Import `oathdigital.gameplay.phases.{Rest, RestCommand}` and `oathdigital.gameplay.phases.rest.{TurnBoundary, WarExhaustionRandomPort}`.
- Constructor: `protected val warExhaustionRandomPort: WarExhaustionRandomPort = WarExhaustionRandomPort.random`.
- Replace `handle(state, command: RestCommand)` with `def handle(state: OathState, command: RestCommand) = Rest.handle(catalog, state, command)`.
- evolve: `case event: RestPowerEvent => Rest.evolve(catalog, state, event)`. `RestStarted` and `RestCompleted` become `case _: RestStarted | _: RestCompleted => Left(InvalidEventOrder("legacy Rest events no longer replay"))`.
- `enterWake` becomes `private[gameplay] def enterWake` (unchanged body), and add:

```scala
  protected def turnBoundary(transition: OathTransition)
      : Either[OathViolation, OathTransition] = {
    val rounded = transition.state match {
      case Ready(ready) if ready.game.current.turn.phase == Phase.RoundEnd =>
        TurnBoundary.finishRound(catalog, transition, warExhaustionRandomPort)
      case _ => Right(transition)
    }
    rounded.flatMap(next => next.state match {
      case Ready(ready) if ready.game.current.result.nonEmpty => Right(next)
      case _ => enterWake(next)
    })
  }

  protected def restPowerUsable(ready: ReadyGame, player: PlayerId): Boolean =
    false
```

- [ ] **Step 9: Service and legal controls**

`GameApplicationService.applyUnblockedCommand`:

```scala
      case GameCommand.BeginRest(playerId) =>
        rules.startWalker(state, PhaseTransitionRef.BeginRest, playerId)
      case GameCommand.FinishRest(playerId) =>
        rules.startWalker(state, PhaseTransitionRef.FinishRest, playerId)
```

Import `WarExhaustionRandomPort` from `phases.rest`.

`LegalActionProjector`: import `oathdigital.gameplay.phases.rest.{BeginRestProcedure, FinishRestProcedure}` in place of `phases.Rest`. The Act vector uses `BeginRestProcedure.validateBegin(catalog, Ready(context.ready), active.player)`. Replace `case Phase.Rest => Vector("finishRest")` with:

```scala
        case Phase.Rest => Option.when(FinishRestProcedure.gate(catalog,
          context.ready, active.player).isRight)("finishRest").toVector
```

- [ ] **Step 10: Move the other callers off `RestCommand`**

- `StateBasedEvaluationSuite`: drop the `RestCommand` import. Each `Begin`/`Finish` pair becomes `accept(rules.startWalker(state, PhaseTransitionRef.BeginRest, player))` (and `holder`).
- `ForgeWalkerFixture:114-126` and `GameApplicationServiceSuite:601-603,1990-1998`: delete every `GameCommand.FinishRest` submission; `BeginRest` now finishes Rest.
- `GameApplicationServiceSuite` "Rest v5 commands persist reload and project the next player's Wake": rename it "Begin Rest finishes Rest, persists and reloads to the next player's Wake". Delete the `restProjection` lines and the `FinishRest` submission; `finished` is the `BeginRest` result. Keep the load and phase assertions. The `takeRight(2)` formatVersion assertion stays.
- `WalkerProcedureRegistrySuite`: add

```scala
  test("Begin Rest records Rest diagnostics; Finish Rest records none and " +
      "parks as a generic Rest decision") {
    assertEquals(WalkerProcedureRegistry.fallbackKind(
      PhaseTransitionRef.BeginRest), Right(Some(MajorActionKind.Rest)))
    assertEquals(WalkerProcedureRegistry.fallbackKind(
      PhaseTransitionRef.FinishRest), Right(None))
    assertEquals(WalkerProcedureRegistry.continuationFor(
      PhaseTransitionRef.FinishRest, "any", actor, DecisionId("d")),
      Right(Some(OathContinue.AwaitingRestDecision(actor, DecisionId("d")))))
  }
```

   Fix any existing `fallbackKind` assertion to expect `Right(Some(...))`.

- [ ] **Step 11: Run the Rest and walker suites**

Run: `./sbtw "testOnly oathdigital.gameplay.RestSuite oathdigital.gameplay.StateBasedEvaluationSuite oathdigital.gameplay.walker.WalkerProcedureRegistrySuite oathdigital.application.GameApplicationServiceSuite"`
Expected: PASS. If "Rest returns controlled resources" fails on `usedPowers`, check that `BeginTurn` is the last recorded step.

- [ ] **Step 12: Full gate and commit**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS" && python3 scripts/check-architecture.py && git diff --check`
Expected: all green.

```bash
git add src
git commit -m "feat(rest): run Begin Rest and Finish Rest on the walker

Begin Rest enters the Rest phase and, with no usable REST power, finishes
Rest in the same command. Finish Rest cleans up, refreshes Supply and
hands the turn over through BeginTurn; the turn boundary then ends the
round and evaluates Wake.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: League Treaty as a walker power

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/rest/LeagueTreatyContribution.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/gameplay/phases/RestCleanup.scala` (widen `suitOf`)
- Test: `src/test/scala/oathdigital/gameplay/powers/rest/LeagueTreatySuite.scala` (new)

**Interfaces:**
- Consumes: `DecisionOptionRef.FavorBank`, `DecisionOption.FavorBank`, `DistributeSlot`, `DecisionQuery.Distribute`, `DistributeAmount`, `DecisionAnswer.DistributeAnswer` (Task 1); `PhaseTransitionRef.BeginRest`, `FinishRestProcedure`'s `RestReturnFavor` window, `OathContinue.AwaitingRestDecision` (Task 4).
- Produces: `LeagueTreatyContribution.id = PowerId("denizen.league-treaty")`, `LeagueTreatyContribution.forCatalog(catalog): Option[LeagueTreatyContribution]`, `LeagueTreatyContribution.destinationDecisionId(ready, rester, site, card): String` and `distributionDecisionId(...)` (same parameters).

- [ ] **Step 1: Write the failing suite**

`LeagueTreatySuite.scala`:

```scala
package oathdigital.gameplay.powers.rest

import oathdigital.gameplay._
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.model._

class LeagueTreatySuite extends munit.FunSuite {
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))
  private val treatyCard = DenizenId("237")

  private def act: ReadyGame = {
    val Ready(initial) = execute(new FirstGameSetupRules(catalog))._1: @unchecked
    initial.copy(game = initial.game.copy(current = initial.game.current.copy(
      turn = initial.game.current.turn.copy(phase = Phase.Act))))
  }

  private def suitOf(id: DenizenId): Suit = Suit.all.find(suit => catalog
    .denizens.find(_.id.value == id.value).exists(_.suit.value == suit.key)).get

  /** Places League Treaty and `favor` (suit -> amounts per card) on the
    * treaty site's region, with every placed card pulled out of the decks
    * and every other in-play card emptied of favor. The treaty site is ruled
    * by `ruler`'s warband when `ruler` is set, and by bandits otherwise.
    */
  private def arranged(ruler: Option[PlayerId],
      favor: Vector[(Suit, Int)]): (ReadyGame, SiteId) = {
    val base = act
    val current = base.game.current
    val site = current.map.cradle.head
    val region = current.map.inPlay.filter(current.map.regionOf(_) ==
      current.map.regionOf(site))
    val deck = current.commonCards.worldDeck.collect { case id: DenizenId => id }
    val picks = favor.foldLeft(Vector.empty[(DenizenId, Int)]) {
      case (chosen, (suit, amount)) => chosen :+ (deck.find(id =>
        id != treatyCard && !chosen.exists(_._1 == id) && suitOf(id) == suit)
        .get -> amount)
    }
    val lineage = current.players.map(p => p.player -> p.lineage).toMap
    val placed = picks.zipWithIndex.groupBy { case (_, i) =>
      region(i % region.size) }.map { case (id, rows) => id -> rows.map {
        case ((card, amount), _) => DenizenState(card, Orientation.FaceUp,
          Tokens(amount, 0)) } }
    val sites = current.map.sites.map { case (id, state) =>
      val emptied = state.copy(denizens = state.denizens.map {
        case d: DenizenState => d.copy(tokens = d.tokens.copy(favor = 0))
        case e: EdificeState => e.copy(tokens = e.tokens.copy(favor = 0))
      })
      val treaty = Vector(DenizenState(treatyCard, Orientation.FaceUp,
        Tokens.empty)).filter(_ => id == site)
      id -> (if (id == site) emptied.copy(forces = ruler.fold[SiteForces](
          SiteForces.Occupied(ForceKind.Bandit, 1))(owner =>
          SiteForces.Occupied(ForceKind.Exile(lineage(owner)), 1)),
          denizens = treaty ++ placed.getOrElse(id, Vector.empty))
        else emptied.copy(denizens = emptied.denizens ++
          placed.getOrElse(id, Vector.empty)))
    }
    val removed = picks.map(_._1).toSet + treatyCard
    base.copy(game = base.game.copy(current = current.copy(
      map = current.map.copy(sites = sites),
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot {
          case id: DenizenId => removed(id)
          case _ => false
        })))) -> site
  }

  private def rester(ready: ReadyGame) = ready.game.current.turn.activePlayer
  private def offTurn(ready: ReadyGame) =
    ready.game.current.players.map(_.player).find(_ != rester(ready)).get
  private def bank(suit: Suit) = DecisionOptionRef.FavorBank(suit)
  private def banks(state: OathState) = state.asInstanceOf[Ready].value.banks.favor
  private val example = Vector(Suit.Arcane -> 2, Suit.Discord -> 2,
    Suit.Hearth -> 2)

  test("an unruled treaty site or a region without card favor asks nothing") {
    Vector(arranged(None, example), arranged(Some(offTurn(act)), Vector.empty))
      .foreach { case (ready, _) =>
      val rested = rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest,
        rester(ready)).toOption.get
      assert(rested.continue.isInstanceOf[OathContinue.AwaitingWakeAction],
        rested.continue.toString)
    }
  }

  test("the off-turn ruler alone answers the destination, and declining " +
      "leaves cleanup to printed banks") {
    val owner = offTurn(act)
    val (ready, site) = arranged(Some(owner), example)
    val destination = LeagueTreatyContribution.destinationDecisionId(ready,
      rester(ready), site, treatyCard)
    val parked = rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest,
      rester(ready)).toOption.get
    assertEquals(parked.continue,
      OathContinue.AwaitingRestDecision(owner, DecisionId(destination)))
    val decline = DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("decline"))
    assert(rules.resolveWalker(parked.state, rester(ready), destination,
      decline).isLeft)
    val declined = rules.resolveWalker(parked.state, owner, destination,
      decline).toOption.get
    assert(declined.continue.isInstanceOf[OathContinue.AwaitingWakeAction])
    example.foreach { case (suit, amount) =>
      assertEquals(banks(declined.state)(suit), ready.banks.favor(suit) + amount)
    }
  }

  test("a destination that is the only source suit moves nothing and asks " +
      "no distribution") {
    val owner = offTurn(act)
    val (ready, site) = arranged(Some(owner),
      Vector(Suit.Nomad -> 2, Suit.Nomad -> 1))
    val destination = LeagueTreatyContribution.destinationDecisionId(ready,
      rester(ready), site, treatyCard)
    val parked = rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest,
      rester(ready)).toOption.get
    val chosen = rules.resolveWalker(parked.state, owner, destination,
      DecisionAnswer.ChooseOneAnswer(bank(Suit.Nomad))).toOption.get
    assert(chosen.continue.isInstanceOf[OathContinue.AwaitingWakeAction])
    assertEquals(banks(chosen.state)(Suit.Nomad), ready.banks.favor(Suit.Nomad) + 3)
  }

  test("the worked example moves 2 Arcane and 1 Discord favor to Nomad, " +
      "and a replayed park resumes to the same decision") {
    val owner = offTurn(act)
    val (ready, site) = arranged(Some(owner), example)
    val destination = LeagueTreatyContribution.destinationDecisionId(ready,
      rester(ready), site, treatyCard)
    val distribution = LeagueTreatyContribution.distributionDecisionId(ready,
      rester(ready), site, treatyCard)
    val parked = rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest,
      rester(ready)).toOption.get
    val chosen = rules.resolveWalker(parked.state, owner, destination,
      DecisionAnswer.ChooseOneAnswer(bank(Suit.Nomad))).toOption.get
    assertEquals(chosen.continue,
      OathContinue.AwaitingRestDecision(owner, DecisionId(distribution)))

    val replayed = (parked.events ++ chosen.events)
      .foldLeft[Either[OathViolation, OathState]](Right(Ready(ready)))(
        (state, event) => state.flatMap(rules.evolve(_, event)))
    assertEquals(replayed, Right(chosen.state))

    val answer = DecisionAnswer.DistributeAnswer(Vector(
      Suit.Arcane -> 0, Suit.Discord -> 1, Suit.Hearth -> 2, Suit.Nomad -> 3)
      .map { case (suit, n) => DistributeAmount(bank(suit), n) })
    val done = rules.resolveWalker(replayed.toOption.get, owner, distribution,
      answer).toOption.get
    assert(done.continue.isInstanceOf[OathContinue.AwaitingWakeAction])
    Vector(Suit.Arcane -> 0, Suit.Discord -> 1, Suit.Hearth -> 2,
      Suit.Nomad -> 3).foreach { case (suit, delta) =>
      assertEquals(banks(done.state)(suit), ready.banks.favor(suit) + delta,
        suit.toString)
    }
  }
}
```

If `suit.key` does not match the catalog's `suit.value` spelling, compare the way `RestCleanupPlan.suitOf` does. If the first game's cradle has fewer sites with room than the picks need, the helper still works: several picks share a site.

- [ ] **Step 2: Run to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.rest.LeagueTreatySuite"`
Expected: compilation fails on `LeagueTreatyContribution`.

- [ ] **Step 3: Widen the suit lookup**

In `RestCleanup.scala`, change `private def suitOf` to `private[gameplay] def suitOf` and leave its body alone. The steps below call it as `RestCleanupPlan.suitOf(catalog, cardId)`. If it lives on a different object or takes different parameters, call it with its real signature.

- [ ] **Step 4: Implement the power**

`LeagueTreatyContribution.scala`:

```scala
package oathdigital.gameplay.powers.rest

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathViolation, ReadyGame, RuleSourceRef, SiteRule,
  SiteRuler}
import oathdigital.gameplay.operations._
import oathdigital.gameplay.phases.RestCleanupPlan
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** League Treaty (card 237): before Rest cleanup, the ruler of the treaty's
  * site may send the region's card favor to one bank instead of each card's
  * printed bank.
  *
  * The transform inserts two decisions and one move ahead of cleanup. Both
  * decisions come before anything it changes, so the transform sees the same
  * state, and folds to the same vector, while either is parked.
  */
final case class LeagueTreatyContribution private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower {
  import LeagueTreatyContribution._

  def id: PowerId = LeagueTreatyContribution.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.RestReturnFavor -> Vector(Transform((ctx, ops) =>
      treaty(ctx.state).fold(ops)(inserted(ctx.state, ctx.activePlayer, _) ++ ops))))

  /** One region card holding favor, in site order then card id. */
  private final case class Holding(card: CardId, suit: Suit, favor: Int)
  private final case class Treaty(site: SiteId, ruler: PlayerId,
      holdings: Vector[Holding]) {
    def favorOf(suit: Suit): Int = holdings.filter(_.suit == suit).map(_.favor).sum
    def suits: Vector[Suit] = Suit.all.filter(favorOf(_) > 0)
    def total: Int = holdings.map(_.favor).sum
  }

  private def treaty(ready: ReadyGame): Option[Treaty] = {
    val current = ready.game.current
    for {
      site <- current.map.inPlay.find(id => current.map.sites.get(id).exists(
        _.denizens.exists {
          case DenizenState(`cardId`, Orientation.FaceUp, _) => true
          case _ => false
        }))
      ruler <- SiteRule.ruler(current.map.sites(site).forces, current.players)
        .toOption.collect { case SiteRuler.Player(player) => player }
      region <- current.map.regionOf(site)
      holdings = current.map.inPlay.filter(current.map.regionOf(_)
          .contains(region)).flatMap { id =>
        current.map.sites(id).denizens.collect {
          case DenizenState(card, Orientation.FaceUp, tokens) if tokens.favor > 0 =>
            card -> tokens.favor
          case EdificeState(card, _, tokens) if tokens.favor > 0 =>
            card -> tokens.favor
        }.sortBy(_._1.value).flatMap { case (card, favor) =>
          RestCleanupPlan.suitOf(catalog, card).map(Holding(card, _, favor))
        }
      }
      if holdings.nonEmpty
    } yield Treaty(site, ruler, holdings)
  }

  private def inserted(ready: ReadyGame, rester: PlayerId,
      treaty: Treaty): Vector[Operation] = {
    val destination = destinationDecisionId(ready, rester, treaty.site, cardId)
    val distribution = distributionDecisionId(ready, rester, treaty.site, cardId)
    Vector(
      Decide(destination, treaty.ruler, DecisionQuery.ChooseOne(
        Suit.all.map(suit => DecisionOption.FavorBank(
          DecisionOptionRef.FavorBank(suit))) :+
          DecisionOption.Button(DecisionOptionRef.Button(Decline), "Decline"),
        heading = Some("League Treaty: send this region's favor to one bank"))),
      Branch((_, pending) => chosenBank(pending, destination)
        .filter(bank => treaty.suits.exists(_ != bank))
        .map(bank => Decide(distribution, treaty.ruler, query(treaty, bank)))
        .toVector),
      BuildOps((_, pending) => chosenBank(pending, destination) match {
        case Some(bank) if treaty.suits.exists(_ != bank) =>
          amounts(pending, distribution).map(moves(treaty, bank, _))
        case _ => Right(Vector.empty)
      }))
  }

  private def query(treaty: Treaty, bank: Suit): DecisionQuery.Distribute =
    DecisionQuery.Distribute(
      treaty.suits.filter(_ != bank).map { suit =>
        val maximum = treaty.favorOf(suit)
        DistributeSlot(DecisionOption.FavorBank(
          DecisionOptionRef.FavorBank(suit)), 0, maximum, Some(maximum))
      } :+ DistributeSlot(DecisionOption.FavorBank(
        DecisionOptionRef.FavorBank(bank)), treaty.favorOf(bank), treaty.total,
        Some(treaty.favorOf(bank))),
      total = treaty.total,
      heading = Some("League Treaty: how much favor stays with its own bank?"),
      confirmLabel = Some("Send favor"))

  /** For each source suit, what the ruler did not leave behind moves to the
    * destination, taken card by card in holding order.
    */
  private def moves(treaty: Treaty, bank: Suit,
      kept: Map[Suit, Int]): Vector[CoreOperation] =
    treaty.suits.filter(_ != bank).flatMap { suit =>
      val cards = treaty.holdings.filter(_.suit == suit)
      cards.foldLeft((treaty.favorOf(suit) - kept.getOrElse(suit, 0),
          Vector.empty[CoreOperation])) { case ((left, ops), holding) =>
        val taken = math.min(left, holding.favor)
        (left - taken, if (taken == 0) ops else ops :+ Move(Piece.Favor(taken),
          PositionedLocation(Location.OnCard(holding.card)),
          PositionedLocation(Location.FavorBank(bank))))
      }._2
    }
}

object LeagueTreatyContribution {
  val id: PowerId = PowerId("denizen.league-treaty")
  private val Decline = "decline"

  def forCatalog(catalog: ExecutableCatalog): Option[LeagueTreatyContribution] =
    catalog.denizens.find(_.powers.exists(_.id == id))
      .map(d => new LeagueTreatyContribution(DenizenId(d.id.value), catalog))

  private def stem(ready: ReadyGame, rester: PlayerId, site: SiteId,
      card: DenizenId) = s"rest-${ready.game.current.tracks.round}-" +
    s"${rester.value}-${id.value}-${site.value}-${card.value}"
  def destinationDecisionId(ready: ReadyGame, rester: PlayerId, site: SiteId,
      card: DenizenId): String = stem(ready, rester, site, card) + "-destination"
  def distributionDecisionId(ready: ReadyGame, rester: PlayerId, site: SiteId,
      card: DenizenId): String = stem(ready, rester, site, card) + "-distribution"

  private def chosenBank(pending: PendingTree, decision: String): Option[Suit] =
    pending.answered.collectFirst {
      case Answered(`decision`, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.FavorBank(suit)), _) => suit
    }

  private def amounts(pending: PendingTree, decision: String)
      : Either[OathViolation, Map[Suit, Int]] = pending.answered.collectFirst {
    case Answered(`decision`, DecisionAnswer.DistributeAnswer(rows), _) =>
      rows.collect { case DistributeAmount(DecisionOptionRef.FavorBank(suit), n) =>
        suit -> n }.toMap
  }.toRight(OathViolation.InvalidEventOrder(
    s"no League Treaty distribution answer is recorded for $decision"))
}
```

The `Branch` and the trailing `BuildOps` both read the destination answer. When the answer is a bank that is the only source suit, the `Branch` yields nothing and the `BuildOps` yields nothing: that favor reaches the same bank through cleanup. On decline, `chosenBank` is `None`, and both yield nothing.

Check `DistributeSlot`'s bounds against Task 1's `wellFormed`. A destination with its own favor has `minimum = favorOf(bank)`, `maximum = total`. With at least one other source suit, the sum of minimums stays below `total` and the sum of maximums stays above it, so the query is never forced.

- [ ] **Step 5: Register it**

`WalkerPowerCatalog.default`:

```scala
    WalkerPowers(CatacombsContribution.forCatalog(catalog).toVector ++
      TravelSitePowers.forCatalog(catalog) ++
      LeagueTreatyContribution.forCatalog(catalog) :+ TakeWealthLimit)
```

Add a sentence to its doc: League Treaty is inert until Finish Rest walks its `RestReturnFavor` window.

- [ ] **Step 6: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.rest.LeagueTreatySuite oathdigital.gameplay.RestSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS. If the architecture suite's power-name scan fails because an engine file contains "leaguetreaty", rename nothing in the engine. Instead check which file matched: no file under `gameplay/walker` or `gameplay/operations` should name it.

- [ ] **Step 7: Full gate and commit**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS" && python3 scripts/check-architecture.py && git diff --check`
Expected: all green. `GameApplicationService` passes `WalkerPowerCatalog.default(catalog)`, so production Rest now asks the ruler.

```bash
git add src
git commit -m "feat(rest): fold League Treaty into Finish Rest as a walker power

The treaty site's ruler picks a destination bank or declines, then
distributes the region's card favor. Whatever the ruler keeps back is
returned by the usual cleanup.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: Delete the legacy Rest seam

This task deletes code and adds no behavior. Its test is the grep in Step 6, plus the full gate.

**Files:**
- Delete: `src/main/scala/oathdigital/gameplay/phases/Rest.scala`, `src/main/scala/oathdigital/gameplay/powers/rest/RestPowerIntegration.scala`, `RestPowerHandler.scala`, `LeagueTreatyPower.scala`, `frontend/src/main/scala/oathdigital/frontend/RestPowerDecisionRenderer.scala`
- Modify (backend): `gameplay/OathRules.scala`, `gameplay/powers/RestPowers.scala`, `gameplay/model/GameProcedureProtocol.scala`, `gameplay/model/GameEventProtocol.scala`, `gameplay/model/GameViolation.scala`, `gameplay/setup/FirstGameSetup.scala:248-249`, `model/PendingProcedures.scala:42-126,354-369`, `serialization/GameEventWire.scala:38-43`, `serialization/LifecycleEventCodec.scala`, `application/GameCommands.scala:98-101`, `application/Authorization.scala:72-77`, `application/GameIntentMapper.scala`, `application/GameApplicationService.scala:491-495`, `application/PendingProcedureProjector.scala`, `application/ScopedProjectionContext.scala:36-37`, `application/GameProjection.scala:156-157`
- Modify (shared): `protocol/CommandIntents.scala:13,16,90-91`, `CommandIntentCodec.scala:12-16,69`, `CommandIntentDecoders.scala:12-27,170-176`, `projection/GameProjectionDto.scala:40-41`, `projection/GameProjectionCodec.scala:21,95-96,158-159,166,169-206`, `projection/ActionProjectionDtos.scala:283-291`
- Modify (frontend): `ActionDecisionRenderer.scala:563-567`, `ServerUiSupport.scala:319-326`, `package.scala:132-137`
- Test: `BackendArchitectureSuite.scala:387-397`, `GameEventWireSuite.scala`, `server/GameHttpWireSuite.scala`, `shared/.../CommandProtocolSuite.scala:7-10`, `ProjectionProtocolSuite.scala:71-75`, `frontend/.../ProtocolTestCommands.scala`, `HttpGameClientSuite.scala:184-198`, `ServerModeUiSuite.scala:611-630`

**Interfaces:**
- Consumes: Tasks 4 and 5 (nothing reaches the legacy seam any more).
- Produces: no new names. `RestCommand`, `RestPowerEvent`, `RestStarted`, `RestCompleted`, the three League Treaty events, `PendingProcedure.RestPowerDecision`, `OathContinue.AwaitingRestPowerDecision`, `RestOutcomeMismatch`, `GameCommand.ResolveRestPower`/`DeclineRestPower`, the `resolveRestPower`/`declineRestPower` intents, `RestPowerProjection`, `LeagueTreatyProjection` and `GameProjection.restPower`/`restPowerWaiting` stop existing.

- [ ] **Step 1: Delete the gameplay seam**

- `git rm` the four backend files above.
- `OathRules`: delete `handle(state, command: RestCommand)`, the `RestPowerEvent`/`RestStarted`/`RestCompleted` evolve arms and the `phases.{Rest, RestCommand}` import.
- `RestPowers`: drop `LeagueTreatyPower` from `powers` and its import. `LeagueTreatyContribution` covers the id, following the Catacombs precedent in `RecoverPowers`' doc. Add one line to the object doc saying so.
- `GameProcedureProtocol`: delete `AwaitingRestPowerDecision` and every match arm naming it.
- `GameEventProtocol`: delete `RestPowerEvent`, `RestPowerDecisionStarted`, `RestPowerDecisionCompleted`, `RestStarted`, `LeagueTreatyDecisionStarted`, `LeagueTreatyResolved`, `LeagueTreatyDeclined`, `RestCompleted`.
- `GameViolation`: delete `RestOutcomeMismatch`. Keep `UnsupportedRestState` and `UnsupportedRoundEndCatalogInventory`.
- `FirstGameSetup.scala:248-249`: delete the Rest event arms.
- `PendingProcedures.scala`: delete `SiteFavorSource`, `FavorAllocation`, `RestPowerSourceRef`, `RestPowerInvocationRef`, `RestPowerDecisionPayload`, `PendingProcedure.RestPowerDecision`, `RestPowerContinuation`, and every exhaustive-match arm that names them.

- [ ] **Step 2: Delete the journal spelling**

- `GameEventWire.scala`: delete `RestStartedType`, `LeagueTreatyDecisionStartedType`, `LeagueTreatyResolvedType`, `LeagueTreatyDeclinedType`, `RestCompletedType` and their dispatch arms.
- `LifecycleEventCodec.scala`: delete the encode and decode branches and helpers for the same events, including every helper used only by them (the compiler's `-Xlint` unused warnings name them).
- `GameEventWireSuite` and `GameHttpWireSuite`: delete the round-trip cases for those events. Do not add rejection tests: an unknown type already fails through the generic unknown-type path.

- [ ] **Step 3: Delete the command and projection surface**

- `GameCommands`: delete `ResolveRestPower` and `DeclineRestPower`. `Authorization`: delete `resolveRestPower` and `declineRestPower`. `GameIntentMapper`: delete their intent arms and any helper used only by them. `GameApplicationService`: delete their `applyUnblockedCommand` arms.
- `CommandIntents`: delete `ResolveRestPower`, `DeclineRestPower`, `RestFavorSource`, `RestFavorAllocation`. Delete their codec, decoder and `restAllocation` helpers.
- `ScopedProjectionContext.PendingProjection`: delete `restPower` and `restPowerWaiting`. `PendingProcedureProjector`: delete `restPowerProjection`, the `SiteFavorSource` rows and the `"rest-power-decision"`/`"rest-power-waiting"` phase strings. `GameProjection`: delete the two `.copy` fields.
- `GameProjectionDto`: delete `restPower` and `restPowerWaiting`. `GameProjectionCodec`: remove both keys from the key set and delete their encode, decode and helper code. `ActionProjectionDtos`: delete `RestFavorSourceProjection`, `RestPowerPayloadProjection`, `LeagueTreatyProjection`, `RestPowerProjection`.
- Shared tests: remove the two intents from `CommandProtocolSuite:7-10` and the two fields from `ProjectionProtocolSuite:71-75`.

- [ ] **Step 4: Delete the frontend branches**

- `git rm frontend/src/main/scala/oathdigital/frontend/RestPowerDecisionRenderer.scala`.
- `ActionDecisionRenderer`: delete the `value.restPower...foreach` block. `ServerUiSupport`: delete the `restPower`/`restPowerWaiting` branches. `package.scala`: delete `RestFavorSourceState`, `RestPowerState` and `LeagueTreatyState`.
- Tests: delete the two Rest power helpers in `ProtocolTestCommands`, the resolve/decline encoding assertions in `HttpGameClientSuite:184-198` (keep the Begin/Finish Rest encoding), and the "Rest power owner controls the off-turn choice..." test in `ServerModeUiSuite:611-630`.

- [ ] **Step 5: Retire the architecture assertion**

In `BackendArchitectureSuite`'s "Rest registry cannot own procedure orchestration or state mutation", delete the final `assert(Files.exists(... RestPowerIntegration.scala))`. Keep the forbidden-string loop.

- [ ] **Step 6: Verify nothing names the seam**

Run:

```bash
grep -rnE "RestPowerIntegration|RestPowerHandler|RestPowerEvent|RestStarted|RestCompleted|LeagueTreatyPower\b|LeagueTreatyDecision|LeagueTreatyResolved|LeagueTreatyDeclined|RestPowerDecision|RestPowerProjection|LeagueTreatyProjection|ResolveRestPower|DeclineRestPower|resolveRestPower|declineRestPower|SiteFavorSource|FavorAllocation|RestOutcomeMismatch|RestFavor|RestCommand|restPower|rest-power" --include='*.scala' src shared frontend
```

Expected: no output.

- [ ] **Step 7: Full gate and commit**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS" && python3 scripts/check-architecture.py && git diff --check`
Expected: all green. If `ReviewedPowerCatalog.requireAudited` or `BeginRestProcedure.validateSupportedState` now fails on a fingerprint, recompute that constant from the failing message (Global Constraints). If an "unclassified-handler" rejection names `denizen.league-treaty`, compare how `denizen.catacombs` is audited after leaving `RecoverPowers`, and do the same.

```bash
git add -A src shared frontend
git commit -m "refactor(rest): delete the legacy Rest power seam

Rest events, League Treaty events, the Rest power pending procedure,
its intents, commands, projections and renderer are gone. Rest now
exists only as walker procedures.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: The phase power engine

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powerresolver/PhasePower.scala`
- Create: `src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/gameplay/RuleSourceIndex.scala` (add `RuleSourceAccess`), `gameplay/powers/PowerSupport.scala:25-48` (delegate to it)
- Modify: `src/main/scala/oathdigital/model/ProcedureRef.scala`
- Modify: `src/main/scala/oathdigital/gameplay/model/GameProcedureProtocol.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala` (`buildWalker`), `gameplay/OathRules.scala` (constructor, `restPowerUsable`)
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala:42-49,322-327`, `application/GameApplicationService.scala:78-95`
- Test: `src/test/scala/oathdigital/gameplay/PhasePowerSuite.scala` (new), `src/test/scala/oathdigital/model/ProcedureRefSuite.scala` (new), `walker/WalkerProcedureRegistrySuite.scala`, `serialization/GameEventWireSuite.scala`, `gameplay/BackendArchitectureSuite.scala:~254`

**Interfaces:**
- Consumes: `PowerSourceRef.Card`, `BeginTurn` (Task 3); `restPowerUsable` stub, `continuationIn(Phase.Rest)` (Task 4).
- Produces:
  - `trait PhasePower { def id: PowerId; def timing: PowerTiming; def usable(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef): Boolean; def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef): Either[OathViolation, Operation] }`; `final case class PhasePowers(powers: Vector[PhasePower])` with `PhasePowers.empty`.
  - `PhasePowerProcedure.PowerSource(power: PhasePower, card: CardId, ref: DecisionOptionRef)`; `PhasePowerProcedure.usable(catalog, ready, player, powers): Vector[PowerSource]`; `PhasePowerProcedure.check(catalog, ready, requester, power, source): Either[OathViolation, CardId]`; `PhasePowerProcedure.useRef(power, card): PowerUseRef`.
  - `ActionRef.UsePower(power: PowerId)` with key `s"use-power:${power.value}"`; `ActionRef.usePower(key: String): Option[UsePower]`.
  - `OathContinue.AwaitingPowerDecision(playerId: PlayerId, decision: DecisionId)`.
  - `OathRules(..., phasePowerCatalog: PhasePowers = PhasePowers.empty)`, the last constructor parameter.
  - `WalkerProcedureRegistry.build`/`rebuild(..., args, phasePowers: PhasePowers = PhasePowers.empty, registrations = entries)`.
  - `WalkerDecisionProjector(..., rebuildTree, phasePowers: PhasePowers = PhasePowers.empty)`.
  - `PhasePowerCatalog.default(catalog): PhasePowers` (empty until Task 8).

- [ ] **Step 1: Write the failing reference test**

`src/test/scala/oathdigital/model/ProcedureRefSuite.scala`:

```scala
package oathdigital.model

class ProcedureRefSuite extends munit.FunSuite {
  private val use = ActionRef.UsePower(PowerId("denizen.silver-tongue"))

  test("a use-power reference parses from its key under the action family only") {
    assertEquals(use.key, "use-power:denizen.silver-tongue")
    assertEquals(ActionRef.fromKey(use.key), Some(use))
    assertEquals(StartableRef.fromKey(use.key), Some(use))
    assertEquals(ProcedureRef.fromFamilyKey("action", use.key), Some(use))
    assertEquals(ProcedureRef.fromFamilyKey("phase-transition", use.key), None)
    assertEquals(ActionRef.fromKey("use-power:"), None)
    assertEquals(ProcedureRef.fromFamilyKey("phase-transition", "finish-rest"),
      Some(PhaseTransitionRef.FinishRest))
  }
}
```

- [ ] **Step 2: Write the failing engine suite**

`src/test/scala/oathdigital/gameplay/PhasePowerSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.OathEvent.BanditsRefilled
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.operations.{BuildOps, Decide, Operation}
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powerresolver.{PhasePower, PhasePowers}
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

/** Synthetic WAKE, ACTION and REST powers on a faceup adviser, injected
  * through `OathRules`, drive the generic phase power path end to end.
  */
class PhasePowerSuite extends munit.FunSuite {
  private final case class TestPower(id: PowerId, timing: PowerTiming,
      tree: PlayerId => Operation = _ => BuildOps((_, _) => Right(Vector.empty)))
      extends PhasePower {
    def usable(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef) = true
    def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef) =
      Right(tree(player))
  }

  /** The active player holds one faceup adviser with a catalog power, and a
    * site with capacity has no force, so the action boundary visibly refills
    * bandits.
    */
  private val (base, actor, card, powerId) = {
    val Ready(ready) = execute(new FirstGameSetupRules(catalog))._1: @unchecked
    val current = ready.game.current
    val actor = current.turn.activePlayer
    val deck = current.commonCards.worldDeck.collect { case id: DenizenId => id }
    val card = catalog.denizens.collectFirst {
      case d if d.powers.nonEmpty && deck.contains(DenizenId(d.id.value)) =>
        DenizenId(d.id.value)
    }.get
    val empty = current.map.inPlay.find(id =>
      catalog.sites.find(_.id == id).exists(_.capacity > 0)).get
    val arranged = ready.copy(game = ready.game.copy(current = current.copy(
      players = current.players.map(p => if (p.player != actor) p else
        p.copy(advisers = Vector(DenizenState(card, Orientation.FaceUp,
          Tokens.empty)))),
      map = current.map.copy(sites = current.map.sites.updated(empty,
        current.map.sites(empty).copy(forces = SiteForces.Empty))),
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(_ == card)))))
    val powerId = RuleSourceIndex.enumerate(catalog, arranged).collectFirst {
      case IndexedRuleSource(RuleSourceRef.Adviser(`actor`, `card`), ids, _, _)
          if ids.nonEmpty => ids.head
    }.get
    (arranged, actor, card, powerId)
  }

  private def inPhase(phase: Phase) = base.copy(game = base.game.copy(current =
    base.game.current.copy(turn = TurnState(actor, phase, Set.empty))))
  private val source = DecisionOptionRef.Denizen(card)
  private def rules(power: PhasePower) =
    new OathRules(catalog, phasePowerCatalog = PhasePowers(Vector(power)))
  private def use(power: PhasePower, state: OathState, by: PlayerId = actor) =
    rules(power).startWalker(state, ActionRef.UsePower(power.id), by,
      Vector.empty, Vector(source))
  private def ready(state: OathState) = state.asInstanceOf[Ready].value

  test("a WAKE power is usable only in Wake, once per source, and runs the " +
      "action boundary") {
    val power = TestPower(powerId, PowerTiming.Wake)
    val powers = PhasePowers(Vector(power))
    assertEquals(PhasePowerProcedure.usable(catalog, inPhase(Phase.Wake), actor,
      powers), Vector(PhasePowerProcedure.PowerSource(power, card, source)))
    assertEquals(PhasePowerProcedure.usable(catalog, inPhase(Phase.Act), actor,
      powers), Vector.empty)

    val used = use(power, Ready(inPhase(Phase.Wake))).toOption.get
    val ref = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Card(card), powerId)
    assert(used.events.exists(_.isInstanceOf[BanditsRefilled]))
    assertEquals(used.continue, OathContinue.AwaitingWakeAction(actor))
    assert(ready(used.state).game.current.turn.usedPowers.contains(ref))
    assertEquals(use(power, used.state).left.toOption,
      Some(OathViolation.PowerAlreadyUsed(ref)))
    assertEquals(PhasePowerProcedure.usable(catalog, ready(used.state), actor,
      powers), Vector.empty)
  }

  test("an ACTION power returns its player to action selection") {
    val used = use(TestPower(powerId, PowerTiming.Act), Ready(inPhase(Phase.Act)))
      .toOption.get
    assert(used.events.exists(_.isInstanceOf[BanditsRefilled]))
    assertEquals(used.continue, OathContinue.ActActionSelection(actor))
  }

  test("another player, the wrong phase and an inaccessible source are refused") {
    val power = TestPower(powerId, PowerTiming.Act)
    val other = base.game.current.players.map(_.player).find(_ != actor).get
    assertEquals(use(power, Ready(inPhase(Phase.Act)), other).left.toOption,
      Some(OathViolation.WrongPlayer(actor, other)))
    assert(use(power, Ready(inPhase(Phase.Wake))).isLeft)
    assert(rules(power).startWalker(Ready(inPhase(Phase.Act)),
      ActionRef.UsePower(powerId), actor, Vector.empty,
      Vector(DecisionOptionRef.Denizen(DenizenId("no-such-card")))).isLeft)
  }

  test("a usable REST power stops the Rest auto-skip, and using it still " +
      "leaves Finish Rest to the player") {
    val power = TestPower(powerId, PowerTiming.Rest)
    val rested = rules(power).startWalker(Ready(inPhase(Phase.Act)),
      PhaseTransitionRef.BeginRest, actor).toOption.get
    assertEquals(rested.continue, OathContinue.AwaitingRestAction(actor))
    val used = use(power, rested.state).toOption.get
    assertEquals(used.continue, OathContinue.AwaitingRestAction(actor))
    val finished = rules(power).startWalker(used.state,
      PhaseTransitionRef.FinishRest, actor).toOption.get
    val next = ready(finished.state).game.current.turn
    assertNotEquals(next.activePlayer, actor)
    assertEquals(next.usedPowers, Set.empty[PowerUseRef])
  }

  test("a decision inside a power parks as a power decision and replays") {
    val choice = "test-power-choice"
    val power = TestPower(powerId, PowerTiming.Act, player => Decide(choice,
      player, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(DecisionOptionRef.Button("go"), "Go"),
        DecisionOption.Button(DecisionOptionRef.Button("stop"), "Stop")))))
    val parked = use(power, Ready(inPhase(Phase.Act))).toOption.get
    assertEquals(parked.continue,
      OathContinue.AwaitingPowerDecision(actor, DecisionId(choice)))
    val done = rules(power).resolveWalker(parked.state, actor, choice,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("go"))).toOption.get
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
    assertEquals((parked.events ++ done.events)
      .foldLeft[Either[OathViolation, OathState]](Right(Ready(inPhase(Phase.Act))))(
        (state, event) => state.flatMap(rules(power).evolve(_, event))),
      Right(done.state))
  }
}
```

- [ ] **Step 3: Run both to verify they fail**

Run: `./sbtw "testOnly oathdigital.model.ProcedureRefSuite oathdigital.gameplay.PhasePowerSuite"`
Expected: compilation fails on `ActionRef.UsePower` and `PhasePower`.

- [ ] **Step 4: The contract, the reference and the continuation**

`gameplay/powerresolver/PhasePower.scala`:

```scala
package oathdigital.gameplay.powerresolver

import oathdigital.gameplay.{OathViolation, ReadyGame}
import oathdigital.gameplay.operations.Operation
import oathdigital.model.{DecisionOptionRef, PlayerId, PowerId, PowerTiming}

/** A WAKE, ACTION or REST power a player uses as an action.
  *
  * The engine finds its sources, checks access and once-per-turn use, and
  * records the use after `build`'s tree. `build` must be a pure function of
  * state: the tree is rebuilt on every resume.
  */
trait PhasePower {
  def id: PowerId
  def timing: PowerTiming
  /** Power-specific preconditions beyond access and once-per-turn. */
  def usable(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef): Boolean
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation]
}

final case class PhasePowers(powers: Vector[PhasePower]) {
  def find(id: PowerId): Option[PhasePower] = powers.find(_.id == id)
}
object PhasePowers {
  val empty: PhasePowers = PhasePowers(Vector.empty)
}
```

`ProcedureRef.scala`, in `object ActionRef`:

```scala
  /** Uses one phase power. Parameterized, so `all` cannot list it; the key
    * parses directly.
    */
  final case class UsePower(power: PowerId) extends ActionRef {
    val key: String = s"${UsePower.Prefix}${power.value}"
  }
  object UsePower { private[model] val Prefix = "use-power:" }

  def usePower(key: String): Option[UsePower] =
    Option.when(key.startsWith(UsePower.Prefix))(key.stripPrefix(UsePower.Prefix))
      .flatMap(PowerId.fromValue).map(UsePower(_))

  def fromKey(key: String): Option[ActionRef] =
    all.find(_.key == key).orElse(usePower(key))
```

`StartableRef.fromKey`: `all.find(_.key == key).orElse(ActionRef.usePower(key))`. `ProcedureRef.fromFamilyKey`: `all.find(ref => ref.family == family && ref.key == key).orElse(ActionRef.usePower(key).filter(_.family == family))`. Update both docs to say a use-power key parses rather than being listed. Before relying on it, check that `PowerId.fromValue("")` is `None`. If it is not, add `.filter(_.nonEmpty)` before `.flatMap`.

`GameProcedureProtocol.scala`, after `AwaitingRestDecision`:

```scala
  /** Any decision parked inside a used phase power. */
  final case class AwaitingPowerDecision(playerId: PlayerId,
      decision: DecisionId) extends OathContinue
```

Give it an arm wherever Task 4 gave `AwaitingRestDecision` one.

- [ ] **Step 5: One access rule for reviewed and phase powers**

In `RuleSourceIndex.scala`, add:

```scala
/** Which rule sources a player can use: a site, site card or site relic at
  * their pawn site, an intact edifice there, their own faceup advisers and
  * relics, banners, Foundations and their lineage's active legacies.
  */
private[gameplay] object RuleSourceAccess {
  def accessible(ref: RuleSourceRef, face: RuleSourceFace, ready: ReadyGame,
      actor: PlayerId, facedownAdviser: Boolean): Boolean = {
    val player = ready.game.current.players.find(_.player == actor)
    val pawn = player.flatMap(_.pawnSite)
    ref match {
      case RuleSourceRef.Site(id) => pawn.contains(id)
      case RuleSourceRef.SiteCard(id, _) =>
        pawn.contains(id) && face == RuleSourceFace.FaceUp
      case RuleSourceRef.SiteRelic(id, _) =>
        pawn.contains(id) && face == RuleSourceFace.FaceUp
      case RuleSourceRef.Edifice(id, _) =>
        pawn.contains(id) && face == RuleSourceFace.Intact
      case RuleSourceRef.Adviser(owner, _) => owner == actor &&
        (face == RuleSourceFace.FaceUp ||
          (facedownAdviser && face == RuleSourceFace.FaceDown))
      case RuleSourceRef.Relic(owner, _) =>
        owner == actor && face == RuleSourceFace.FaceUp
      case RuleSourceRef.Banner(_) | RuleSourceRef.Foundation(_) => true
      case RuleSourceRef.Legacy(lineage, _) =>
        player.exists(_.lineage == lineage) && face == RuleSourceFace.Active
      case _ => false
    }
  }
}
```

In `PowerSupport.scala`, replace `accessible`'s body with `RuleSourceAccess.accessible(ref, source.face, facts.ready, facts.actor, window == PowerWindow.ActionCardPlayed)`.

- [ ] **Step 6: Sources, usability and the tree**

`gameplay/phases/PhasePowerProcedure.scala`:

```scala
package oathdigital.gameplay.phases

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathViolation, ReadyGame, RuleSourceAccess,
  RuleSourceIndex, RuleSourceRef}
import oathdigital.gameplay.OathViolation._
import oathdigital.gameplay.operations.{Operation, RecordPowerUse, Sequence}
import oathdigital.gameplay.powerresolver.{PhasePower, PhasePowers}
import oathdigital.model._

/** Use Power (rest-walker spec, Phase powers).
  *
  * {{{
  * Sequence(power.build(...), RecordPowerUse(timing, Card(source), id))
  * }}}
  *
  * `usable` is the single usability function: the start gate, legal
  * controls, the `phasePowers` projection and the Rest auto-skip all ask it.
  */
object PhasePowerProcedure {
  final case class PowerSource(power: PhasePower, card: CardId,
      ref: DecisionOptionRef)

  def timingOf(phase: Phase): Option[PowerTiming] = phase match {
    case Phase.Wake => Some(PowerTiming.Wake)
    case Phase.Act => Some(PowerTiming.Act)
    case Phase.Rest => Some(PowerTiming.Rest)
    case _ => None
  }

  def useRef(power: PhasePower, card: CardId): PowerUseRef =
    PowerUseRef(power.timing, PowerSourceRef.Card(card), power.id)

  /** Cards `player` can access that print `power`, in index order. */
  def sources(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      power: PhasePower): Vector[(CardId, DecisionOptionRef)] =
    RuleSourceIndex.enumerate(catalog, ready).filter(source =>
      source.powerIds.contains(power.id) && RuleSourceAccess.accessible(
        source.source, source.face, ready, player, facedownAdviser = false))
      .flatMap(source => cardRef(source.source))

  def check(catalog: ExecutableCatalog, ready: ReadyGame, requester: PlayerId,
      power: PhasePower, source: DecisionOptionRef)
      : Either[OathViolation, CardId] = {
    val current = ready.game.current
    val active = current.turn.activePlayer
    val phase = current.turn.phase
    for {
      _ <- Either.cond(current.result.isEmpty, (), GameEnded)
      _ <- Either.cond(requester == active, (), WrongPlayer(active, requester))
      _ <- Either.cond(current.walkerPending.isEmpty &&
        current.walkerProcedure.isEmpty && current.pending.isEmpty, (),
        InvalidEventOrder("a procedure is already pending"))
      _ <- Either.cond(timingOf(phase).contains(power.timing), (),
        InvalidEventOrder(s"${power.id.value} is a ${power.timing} power " +
          s"and cannot be used in the ${phase.productPrefix} phase"))
      card <- sources(catalog, ready, active, power).collectFirst {
        case (card, `source`) => card
      }.toRight(InvalidEventOrder(s"${source.kind}/${source.wireId} is not " +
        s"an accessible source of ${power.id.value}"))
      _ <- Either.cond(!current.turn.usedPowers.contains(useRef(power, card)),
        (), PowerAlreadyUsed(useRef(power, card)))
      _ <- Either.cond(power.usable(ready, active, source), (),
        InvalidEventOrder(s"${power.id.value} has nothing to do from " +
          s"${source.kind}/${source.wireId}"))
    } yield card
  }

  def usable(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      powers: PhasePowers): Vector[PowerSource] =
    powers.powers.flatMap(power => sources(catalog, ready, player, power)
      .collect { case (card, ref)
          if check(catalog, ready, player, power, ref).isRight =>
        PowerSource(power, card, ref) })

  def build(id: PowerId, powers: PhasePowers)(catalog: ExecutableCatalog,
      ready: ReadyGame, player: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for {
    power <- find(id, powers)
    source <- single(id, args)
    card <- check(catalog, ready, player, power, source)
    tree <- power.build(ready, player, source)
  } yield Sequence(Vector(tree, RecordPowerUse(useRef(power, card))))

  /** Resume skips the gate: the walker is pending and the use is not yet
    * recorded, so only the tree is rebuilt.
    */
  def rebuild(id: PowerId, powers: PhasePowers)(catalog: ExecutableCatalog,
      ready: ReadyGame, player: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for {
    power <- find(id, powers)
    source <- single(id, args)
    card <- cardRef(source)
    tree <- power.build(ready, player, source)
  } yield Sequence(Vector(tree, RecordPowerUse(useRef(power, card))))

  private def find(id: PowerId, powers: PhasePowers) = powers.find(id)
    .toRight(InvalidEventOrder(s"no phase power is registered for ${id.value}"))

  private def single(id: PowerId, args: Vector[DecisionOptionRef]) = args match {
    case Vector(source) => Right(source)
    case other => Left(InvalidEventOrder(s"using ${id.value} names exactly " +
      s"one source, got ${other.size}"))
  }

  private def cardRef(source: RuleSourceRef): Option[(CardId, DecisionOptionRef)] =
    source match {
      case RuleSourceRef.SiteCard(_, id: DenizenId) =>
        Some(id -> DecisionOptionRef.Denizen(id))
      case RuleSourceRef.Adviser(_, id: DenizenId) =>
        Some(id -> DecisionOptionRef.Denizen(id))
      case RuleSourceRef.Relic(_, id) => Some(id -> DecisionOptionRef.Relic(id))
      case RuleSourceRef.SiteRelic(_, id) => Some(id -> DecisionOptionRef.Relic(id))
      case _ => None
    }

  private def cardRef(source: DecisionOptionRef): Either[OathViolation, CardId] =
    source match {
      case DecisionOptionRef.Denizen(id) => Right(id)
      case DecisionOptionRef.Relic(id) => Right(id)
      case other => Left(InvalidEventOrder(
        s"${other.kind}/${other.wireId} is not a power source card"))
    }
}
```

If the compiler rejects the two `cardRef` overloads under `-Xlint`, rename the second one `sourceCard`.

`gameplay/powers/PhasePowerCatalog.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePowers

/** The production phase powers, beside [[WalkerPowerCatalog]]. A power whose
  * card is absent from `catalog` is omitted.
  */
object PhasePowerCatalog {
  def default(catalog: ExecutableCatalog): PhasePowers = PhasePowers(Vector.empty)
}
```

- [ ] **Step 7: Registry, rules and projector routing**

`WalkerProcedureRegistry`:

```scala
  /** Every `UsePower` shares one entry shape, built for its id. A parked
    * decision inside the power is a generic power decision, so the registry
    * names no power.
    */
  private def usePowerEntry(id: PowerId, powers: PhasePowers): Entry = Entry(
    fallbackKind = None,
    rollDecisionId = None,
    modifierWindow = None,
    continuationFor = (_, awaited, decision) =>
      Some(OathContinue.AwaitingPowerDecision(awaited, decision)),
    build = PhasePowerProcedure.build(id, powers),
    rebuild = PhasePowerProcedure.rebuild(id, powers))

  private def lookup(procedure: ProcedureRef,
      registrations: Map[ProcedureRef, Entry],
      powers: PhasePowers = PhasePowers.empty): Either[OathViolation, Entry] =
    procedure match {
      case ActionRef.UsePower(id) => Right(usePowerEntry(id, powers))
      case _ => registrations.get(procedure).toRight(OathViolation
        .InvalidEventOrder(s"no walker procedure registered for ${procedure.key}"))
    }
```

`build` and `rebuild` gain `phasePowers: PhasePowers = PhasePowers.empty` between `args` and `registrations`, and pass it to `lookup`. `isRegistered` becomes `procedure.isInstanceOf[ActionRef.UsePower] || entries.contains(procedure)`.

`OathRulesWalker`: add `protected def phasePowerCatalog: PhasePowers`, and replace `buildWalker`'s body with:

```scala
    procedure match {
      // Corrections 4: the injected tree source cannot carry the injected
      // phase powers, so a power use goes to the registry directly.
      case _: ActionRef.UsePower if starting => WalkerProcedureRegistry.build(
        procedure, catalog, ready, actor, startArgs, phasePowerCatalog)
      case _: ActionRef.UsePower => WalkerProcedureRegistry.rebuild(
        procedure, catalog, ready, actor, startArgs, phasePowerCatalog)
      case _ => walkerTree(catalog, procedure, ready, actor, startArgs, starting)
    }
```

`OathRules`: add `protected val phasePowerCatalog: PhasePowers = PhasePowers.empty` as the last constructor parameter. Replace the `restPowerUsable` stub body with `PhasePowerProcedure.usable(catalog, ready, player, phasePowerCatalog).nonEmpty`.

`WalkerDecisionProjector`: add `phasePowers: PhasePowers = PhasePowers.empty` as the last constructor parameter. The auxiliary constructor passes `WalkerDecisionProjector.declaredTree, PhasePowerCatalog.default(catalog)`. Every call of `rebuildTree(catalog, procedure, ready, actor, args)` goes through a private `tree(procedure, ready, actor, args)`:

```scala
  private def tree(procedure: ProcedureRef, ready: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]) = procedure match {
    case _: ActionRef.UsePower => WalkerProcedureRegistry.rebuild(procedure,
      catalog, ready, actor, args, phasePowers)
    case _ => rebuildTree(catalog, procedure, ready, actor, args)
  }
```

`GameApplicationService` constructor: pass `phasePowerCatalog = PhasePowerCatalog.default(catalog)` to `new OathRules`.

- [ ] **Step 8: Registry, codec and architecture tests**

`WalkerProcedureRegistrySuite`:

```scala
  test("every use-power reference is registered and parks as a power decision") {
    val use = ActionRef.UsePower(PowerId("denizen.anything"))
    assert(WalkerProcedureRegistry.isRegistered(use))
    assertEquals(WalkerProcedureRegistry.fallbackKind(use), Right(None))
    assertEquals(WalkerProcedureRegistry.continuationFor(use, "any", actor,
      DecisionId("d")),
      Right(Some(OathContinue.AwaitingPowerDecision(actor, DecisionId("d")))))
  }
```

`GameEventWireSuite`: next to `WalkerCompleted(TriggeredProcedureRef.Oathkeeper)` (line ~184), add `WalkerCompleted(ActionRef.UsePower(PowerId("denizen.silver-tongue")))` to the same round-trip list.

`BackendArchitectureSuite`: `val declaresPower = "(?:extends|with)\\s+(?:ContributingPower|PhasePower)\\b".r`, and add "or `PhasePower`" to the comment above it. If the engine-name scan then flags `PhasePowerProcedure`, the regex's `declaration` picks the declared name; an engine file matches only when it names a specific power, and no file under `gameplay/walker` or `gameplay/operations` should.

- [ ] **Step 9: Run the suites**

Run: `./sbtw "testOnly oathdigital.model.ProcedureRefSuite oathdigital.gameplay.PhasePowerSuite oathdigital.gameplay.walker.WalkerProcedureRegistrySuite oathdigital.serialization.GameEventWireSuite oathdigital.gameplay.BackendArchitectureSuite oathdigital.gameplay.RestSuite"`
Expected: PASS.

- [ ] **Step 10: Full gate and commit**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS" && python3 scripts/check-architecture.py && git diff --check`
Expected: all green.

```bash
git add src
git commit -m "feat(walker): use WAKE, ACTION and REST powers through one procedure

UsePower(powerId) gates on one usability function, walks the power's
tree, records the use and runs the action boundary. Begin Rest's
auto-skip now asks the same function.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 8: Silver Tongue

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/rest/SilverTongue.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`, `WalkerPowerCatalog.scala`, `RestPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/rest/SilverTongueSuite.scala` (new), `src/test/scala/oathdigital/gameplay/RestSuite.scala` ("each relevant Rest handler records fallback diagnostics without blocking")

**Interfaces:**
- Consumes: `PhasePower`, `PhasePowers`, `PhasePowerProcedure`, `ActionRef.UsePower`, `OathContinue.AwaitingPowerDecision` (Task 7); `DecisionOptionRef.FavorBank` (Task 1); `RestCleanupPlan.suitOf` (Task 5).
- Produces: `SilverTongue.id = PowerId("denizen.silver-tongue")`, `SilverTongue.forCatalog(catalog): Option[SilverTongue]`, `SilverTongue.choiceDecisionId(ready, player): String`.

- [ ] **Step 1: Write the failing suite**

`SilverTongueSuite.scala`:

```scala
package oathdigital.gameplay.powers.rest

import oathdigital.gameplay._
import oathdigital.gameplay.OathEvent.BanditsRefilled
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class SilverTongueSuite extends munit.FunSuite {
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private val tongue = DenizenId("92")

  private def suitOf(id: DenizenId): Suit = Suit.all.find(suit => catalog
    .denizens.find(_.id.value == id.value).exists(_.suit.value == suit.key)).get

  /** The Rest phase, with Silver Tongue as the active player's only adviser.
    * The pawn site shows one faceup denizen per suit in `siteSuits`, every
    * bank in `stocked` holds 3 favor and every other bank is empty. A site
    * with capacity is emptied so the action boundary visibly refills it.
    */
  private def arranged(siteSuits: Vector[Suit], stocked: Set[Suit]) = {
    val Ready(ready) = execute(new FirstGameSetupRules(catalog))._1: @unchecked
    val current = ready.game.current
    val actor = current.turn.activePlayer
    val pawn = current.players.find(_.player == actor).get.pawnSite.get
    val deck = current.commonCards.worldDeck.collect { case id: DenizenId => id }
    val cards = siteSuits.map(suit =>
      deck.find(id => id != tongue && suitOf(id) == suit).get)
    val empty = current.map.inPlay.find(id => id != pawn &&
      catalog.sites.find(_.id == id).exists(_.capacity > 0)).get
    val sites = current.map.sites
      .updated(pawn, current.map.sites(pawn).copy(denizens = cards.map(
        DenizenState(_, Orientation.FaceUp, Tokens.empty))))
      .updated(empty, current.map.sites(empty).copy(forces = SiteForces.Empty))
    val removed = cards.toSet + tongue
    val state = ready.copy(
      banks = ready.banks.copy(favor = Suit.all.map(suit =>
        suit -> (if (stocked(suit)) 3 else 0)).toMap),
      game = ready.game.copy(current = current.copy(
        turn = TurnState(actor, Phase.Rest, Set.empty),
        map = current.map.copy(sites = sites),
        players = current.players.map(p => if (p.player != actor) p else
          p.copy(advisers = Vector(DenizenState(tongue, Orientation.FaceUp,
            Tokens.empty)))),
        commonCards = current.commonCards.copy(worldDeck =
          current.commonCards.worldDeck.filterNot {
            case id: DenizenId => removed(id)
            case _ => false
          }))))
    (state, actor)
  }

  private val use = ActionRef.UsePower(SilverTongue.id)
  private val source = DecisionOptionRef.Denizen(tongue)
  private def favor(state: OathState) = state.asInstanceOf[Ready].value.banks.favor

  test("one matching bank with favor gives one favor without a decision, " +
      "once per turn, and runs the action boundary") {
    val (ready, actor) = arranged(Vector(Suit.Arcane, Suit.Nomad), Set(Suit.Arcane))
    val used = rules.startWalker(Ready(ready), use, actor, Vector.empty,
      Vector(source)).toOption.get
    assertEquals(used.continue, OathContinue.AwaitingRestAction(actor))
    assertEquals(favor(used.state)(Suit.Arcane), 2)
    assert(used.events.exists(_.isInstanceOf[BanditsRefilled]))
    val ref = PowerUseRef(PowerTiming.Rest, PowerSourceRef.Card(tongue),
      SilverTongue.id)
    assertEquals(rules.startWalker(used.state, use, actor, Vector.empty,
      Vector(source)).left.toOption, Some(OathViolation.PowerAlreadyUsed(ref)))
  }

  test("several matching banks with favor ask the player to choose one of them") {
    val (ready, actor) = arranged(Vector(Suit.Arcane, Suit.Nomad),
      Set(Suit.Arcane, Suit.Nomad, Suit.Order))
    val choice = SilverTongue.choiceDecisionId(ready, actor)
    val parked = rules.startWalker(Ready(ready), use, actor, Vector.empty,
      Vector(source)).toOption.get
    assertEquals(parked.continue,
      OathContinue.AwaitingPowerDecision(actor, DecisionId(choice)))
    assert(rules.resolveWalker(parked.state, actor, choice,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.FavorBank(Suit.Order)))
      .isLeft)
    val taken = rules.resolveWalker(parked.state, actor, choice,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.FavorBank(Suit.Nomad)))
      .toOption.get
    assertEquals(favor(taken.state)(Suit.Nomad), 2)
    assertEquals(favor(taken.state)(Suit.Arcane), 3)
    assertEquals(taken.continue, OathContinue.AwaitingRestAction(actor))
  }

  test("without matching favor Silver Tongue is not usable and Rest skips ahead") {
    val (ready, actor) = arranged(Vector(Suit.Arcane), Set(Suit.Nomad))
    assertEquals(PhasePowerProcedure.usable(catalog, ready, actor,
      PhasePowerCatalog.default(catalog)), Vector.empty)
    val act = ready.copy(game = ready.game.copy(current = ready.game.current
      .copy(turn = TurnState(actor, Phase.Act, Set.empty))))
    val rested = rules.startWalker(Ready(act), PhaseTransitionRef.BeginRest,
      actor).toOption.get
    assert(rested.continue.isInstanceOf[OathContinue.AwaitingWakeAction],
      rested.continue.toString)
  }
}
```

In `RestSuite`'s "each relevant Rest handler records fallback diagnostics without blocking", remove `"denizen.silver-tongue"` from `relevant`. Silver Tongue's diagnostic now belongs to Search.

- [ ] **Step 2: Run to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.rest.SilverTongueSuite"`
Expected: compilation fails on `SilverTongue`.

- [ ] **Step 3: Implement Silver Tongue**

`SilverTongue.scala`:

```scala
package oathdigital.gameplay.powers.rest

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathViolation, ReadyGame, RuleSourceRef}
import oathdigital.gameplay.operations._
import oathdigital.gameplay.phases.RestCleanupPlan
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Silver Tongue (card 92): "You can only have two advisers. REST: Take a
  * favor from a favor bank matching a card at your site."
  *
  * The REST power is a [[PhasePower]]. The adviser limit is a registered
  * [[Restriction]] at `SearchPlayFacedownAdviser`, inert until Search walks
  * that window. It reads the holder's current advisers; Search's migration
  * decides whether it must also read the cards the tree is about to add.
  */
final case class SilverTongue private (catalog: ExecutableCatalog)
    extends PhasePower with ContributingPower {
  import SilverTongue._

  def id: PowerId = SilverTongue.id
  def timing: PowerTiming = PowerTiming.Rest
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = stocked(ready, player).nonEmpty

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = {
    val choice = choiceDecisionId(ready, player)
    Right(Branch((state, _) => stocked(state, player) match {
      case Vector(only) => Vector(take(player, _ => Right(only)))
      case several => Vector(
        Decide(choice, player, DecisionQuery.ChooseOne(several.map(suit =>
          DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))),
          heading = Some("Silver Tongue: take a favor from a bank"))),
        take(player, pending => pending.answered.collectFirst {
          case Answered(`choice`, DecisionAnswer.ChooseOneAnswer(
            DecisionOptionRef.FavorBank(suit)), _) => suit
        }.toRight(OathViolation.InvalidEventOrder(
          s"no Silver Tongue bank is recorded for $choice"))))
    }))
  }

  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.SearchPlayFacedownAdviser -> Vector(Restriction((ctx, _) =>
      ctx.state.game.current.players.find(_.advisers.exists {
        case DenizenState(card, Orientation.FaceUp, _) => card == Card
        case _ => false
      }).filter(_.advisers.size > 2).map(holder => OathViolation.InvalidEventOrder(
        s"${holder.player.value} holds Silver Tongue and can have only two advisers")))))

  /** Suits of faceup denizens and edifices at the player's pawn site whose
    * bank holds favor, in suit order.
    */
  private def stocked(ready: ReadyGame, player: PlayerId): Vector[Suit] = {
    val current = ready.game.current
    val cards = current.players.find(_.player == player).flatMap(_.pawnSite)
      .flatMap(current.map.sites.get).toVector.flatMap(_.denizens.collect {
        case DenizenState(card, Orientation.FaceUp, _) => card: CardId
        case EdificeState(card, _, _) => card: CardId
      })
    val suits = cards.flatMap(RestCleanupPlan.suitOf(catalog, _)).toSet
    Suit.all.filter(suit => suits(suit) && ready.banks.favor.getOrElse(suit, 0) > 0)
  }

  private def take(player: PlayerId,
      suit: PendingTree => Either[OathViolation, Suit]): Operation =
    BuildOps((_, pending) => suit(pending).map(bank => Vector(Move(
      Piece.Favor(1), PositionedLocation(Location.FavorBank(bank)),
      PositionedLocation(Location.PlayArea(player))))))
}

object SilverTongue {
  val id: PowerId = PowerId("denizen.silver-tongue")
  private val Card = DenizenId("92")

  def forCatalog(catalog: ExecutableCatalog): Option[SilverTongue] =
    Option.when(catalog.denizens.exists(_.powers.exists(_.id == id)))(
      new SilverTongue(catalog))

  def choiceDecisionId(ready: ReadyGame, player: PlayerId): String =
    s"silver-tongue-${ready.game.current.tracks.round}-${player.value}"
}
```

`Card` duplicates the catalog id. Replace it with the id `forCatalog` finds (a second constructor field, `cardId: DenizenId`, as `CatacombsContribution` does), so the restriction and the catalog cannot disagree.

- [ ] **Step 4: Register it and move the legacy diagnostic**

- `PhasePowerCatalog.default`: `PhasePowers(SilverTongue.forCatalog(catalog).toVector)`.
- `WalkerPowerCatalog.default`: add `++ SilverTongue.forCatalog(catalog)` before `:+ TakeWealthLimit`, and a doc line saying the restriction is inert until Search walks `SearchPlayFacedownAdviser`.
- `RestPowers.SilverTongue`: `extends ReviewedPower("denizen.silver-tongue", None, Vector(ReviewedHandler.automatic(PowerWindow.SearchModifierSelection)))`.

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.rest.SilverTongueSuite oathdigital.gameplay.RestSuite oathdigital.gameplay.PhasePowerSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS. If a reviewed-catalog or Rest inventory fingerprint assertion fails, recompute that constant from the failure message (Global Constraints) and rerun.

- [ ] **Step 6: Full gate and commit**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS" && python3 scripts/check-architecture.py && git diff --check`
Expected: all green.

```bash
git add src
git commit -m "feat(rest): add Silver Tongue as the first REST power

Silver Tongue takes one favor from a bank matching a card at the pawn
site, asking only when several banks qualify. Its two-adviser limit is
registered for Search's migration, and its legacy diagnostic moves to
Search.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 9: Phase power projection, intent and command

**Files:**
- Create: `src/main/scala/oathdigital/application/PhasePowerProjector.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala`, `ActionProjectionCodec.scala:212-236`, `GameProjectionDto.scala:41-43`, `GameProjectionCodec.scala:20-22,87-98,142-167`
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandIntents.scala`, `CommandIntentCodec.scala:45-52`, `CommandIntentDecoders.scala:117-129`
- Modify: `src/main/scala/oathdigital/application/GameCommands.scala`, `Authorization.scala`, `GameIntentMapper.scala:55-60`, `GameApplicationService.scala:355-357`, `WalkerDecisionProjector.scala:189`, `LegalActionProjector.scala`, `GameProjection.scala`
- Test: `shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala`, `ProjectionProtocolSuite.scala`, `src/test/scala/oathdigital/application/PhasePowerProjectorSuite.scala` (new), `src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala`

**Interfaces:**
- Consumes: `PhasePowerProcedure.usable`, `PhasePowerCatalog.default`, `ActionRef.UsePower` (Task 7); `SilverTongue` (Task 8).
- Produces:
  - Shared: `PhasePowerProjection(powerId: String, source: DecisionOptionProjection, name: String, rulesText: String)`; `GameProjection.phasePowers: Vector[PhasePowerProjection] = Vector.empty` (last field, JSON key `"phasePowers"`); `GameIntent.UsePower(powerId: String, source: WalkerStartArgWire)` (JSON `{"type":"usePower","powerId":..,"source":{"optionKind":..,"optionId":..}}`).
  - Backend: `GameCommand.UsePower(playerId: PlayerId, power: PowerId, source: DecisionOptionRef)`; `AuthorizedPlayer.usePower(power, source)`; `PhasePowerProjector.project(context): Vector[PhasePowerProjection]`; the legal control `s"usePower:${powerId}:${source.id}"`.

- [ ] **Step 1: Write the failing protocol tests**

`CommandProtocolSuite`: add `UsePower("denizen.silver-tongue", WalkerStartArgWire("denizen", "92"))` to the intent round-trip list, and a test that the decoder rejects a `usePower` object with an extra key:

```scala
  test("usePower names exactly one power and one source") {
    val json = """{"type":"usePower","powerId":"denizen.silver-tongue",""" +
      """"source":{"optionKind":"denizen","optionId":"92"},"extra":1}"""
    assert(CommandIntentCodec.decode(ujson.read(json), "$").isLeft)
  }
```

`ProjectionProtocolSuite`: in the full projection fixture, set `phasePowers = Vector(PhasePowerProjection("denizen.silver-tongue", DecisionOptionProjection("denizen", "92", "Silver Tongue"), "Silver Tongue", "Take a favor."))` and keep the existing round-trip assertion.

Check the spelling `encodeStartArgWire` writes before copying the JSON above. If its keys are not `optionKind`/`optionId`, use its keys.

- [ ] **Step 2: Write the failing projector test**

`src/test/scala/oathdigital/application/PhasePowerProjectorSuite.scala`. Reuse `SilverTongueSuite`'s arrangement by copying its `arranged` helper into a shared test object `src/test/scala/oathdigital/gameplay/powers/rest/SilverTongueFixture.scala`. Make `SilverTongueSuite` call it in the same commit.

```scala
package oathdigital.application

import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.powers.rest.{SilverTongue, SilverTongueFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class PhasePowerProjectorSuite extends munit.FunSuite {
  private val projector = new GameProjector(catalog)

  test("a usable REST power is projected and legal for its active player only") {
    val (ready, actor) = SilverTongueFixture.arranged(Vector(Suit.Arcane),
      Set(Suit.Arcane))
    val own = projector.project("phase-powers", LoadedGame(Ready(ready), 30L), actor)
    assertEquals(own.phasePowers.map(p => p.powerId -> p.source.id),
      Vector(SilverTongue.id.value -> "92"))
    assert(own.phasePowers.head.rulesText.nonEmpty)
    assert(own.legalControls.contains("usePower:denizen.silver-tongue:92"))
    assert(own.legalControls.contains("finishRest"))
    val other = ready.game.current.players.map(_.player).find(_ != actor).get
    val theirs = projector.project("phase-powers", LoadedGame(Ready(ready), 30L),
      other)
    assertEquals(theirs.phasePowers, Vector.empty)
    assert(!theirs.legalControls.exists(_.startsWith("usePower:")))
  }

  test("an unusable power is neither projected nor legal") {
    val (ready, actor) = SilverTongueFixture.arranged(Vector(Suit.Arcane),
      Set(Suit.Nomad))
    val own = projector.project("phase-powers", LoadedGame(Ready(ready), 30L), actor)
    assertEquals(own.phasePowers, Vector.empty)
    assert(!own.legalControls.exists(_.startsWith("usePower:")))
  }
}
```

The intent-to-command mapping is pinned end to end by Task 11, which submits `GameCommand.UsePower` through the service, and by the `CommandProtocolSuite` round trip above.

- [ ] **Step 3: Run to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.protocol.CommandProtocolSuite oathdigital.protocol.ProjectionProtocolSuite" "testOnly oathdigital.application.PhasePowerProjectorSuite"`
Expected: compilation fails on `UsePower` and `PhasePowerProjection`.

- [ ] **Step 4: Shared protocol**

- `ActionProjectionDtos`:

```scala
/** A phase power the viewer can use now: its id, the card it is used from,
  * and the card's printed power name and rules text.
  */
final case class PhasePowerProjection(powerId: String,
    source: DecisionOptionProjection, name: String, rulesText: String)
```

- `ActionProjectionCodec`: extract the option-row object in `encodeDecisionQuery`/`decodeDecisionQuery` into `encodeDecisionOption(row)` and `decodeDecisionOption(raw, path)` (skip if Task 1 already did), then add `encodePhasePower`/`decodePhasePower` over `powerId`, `source`, `name`, `rulesText` with `exact` key checking.
- `GameProjectionDto`: append `,phasePowers: Vector[PhasePowerProjection] = Vector.empty`.
- `GameProjectionCodec`: add `"phasePowers"` to the key set; encode with `encoded(value.phasePowers)(ActionProjectionCodec.encodePhasePower)`; decode with `default(value, "phasePowers", path, Vector.empty[ujson.Value])(array)` and `traverse`, as `favorBanks` does; pass it last to the constructor.
- `CommandIntents`: `final case class UsePower(powerId: String, source: WalkerStartArgWire) extends GameIntent`.
- `CommandIntentCodec.encode`: `case UsePower(power, source) => tagged("usePower", "powerId" -> power, "source" -> CommandNestedCodecs.encodeStartArgWire(source))`.
- `CommandIntentDecoders`:

```scala
    case "usePower" => for {
      _ <- exact(value, Set("type", "powerId", "source"), path)
      power <- string(value, "powerId", path)
      source <- field(value, "source", path).flatMap(raw =>
        CommandNestedCodecs.decodeStartArgsWire(ujson.Arr(raw), s"$path.source"))
        .flatMap {
          case Vector(one) => Right(one)
          case _ => Left(ProtocolDecodeFailure(s"$path.source",
            "expected one power source"))
        }
    } yield UsePower(power, source)
```

If `ProtocolDecodeFailure` takes other parameters, build it the way the neighboring decoders do.

- [ ] **Step 5: Backend command path**

- `GameCommands`: `final case class UsePower(playerId: PlayerId, power: PowerId, source: DecisionOptionRef) extends GameCommand`.
- `Authorization.AuthorizedPlayer`: `def usePower(power: PowerId, source: DecisionOptionRef): GameCommand = GameCommand.UsePower(access.playerId, power, source)`.
- `GameIntentMapper`:

```scala
      case Intent.UsePower(value, source) => for {
        power <- PowerId.fromValue(value).toRight(GameIntentMappingFailure(
          "$.intent.powerId", s"invalid power id '$value'"))
        ref <- optionRef(source.optionKind, source.optionId, "$.intent.source")
      } yield actor.usePower(power, ref)
```

- `GameApplicationService.applyUnblockedCommand`:

```scala
      case GameCommand.UsePower(playerId, power, source) =>
        rules.startWalker(state, ActionRef.UsePower(power), playerId,
          Vector.empty, Vector(source))
```

   Run `grep -n "case GameCommand.EndWake" src/main/scala/oathdigital/application/*.scala`. Every other exhaustive `GameCommand` match (the actor lookup, `majorAction`, audit naming) gains a `UsePower` arm shaped like its `EndWake` arm.

- [ ] **Step 6: Projection**

- `WalkerDecisionProjector.optionProjection`: widen to `private[application]`.
- `PhasePowerProjector.scala`:

```scala
package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.powers.PhasePowerCatalog
import oathdigital.model._
import oathdigital.protocol.projection.PhasePowerProjection

/** The viewer's usable phase powers, and their `usePower` legal controls,
  * from the one usability function the start gate also asks.
  */
private[application] final class PhasePowerProjector(catalog: ExecutableCatalog,
    walkerDecisions: WalkerDecisionProjector,
    powers: PhasePowers) {
  def this(catalog: ExecutableCatalog, walkerDecisions: WalkerDecisionProjector) =
    this(catalog, walkerDecisions, PhasePowerCatalog.default(catalog))

  def project(context: ScopedProjectionContext): Vector[PhasePowerProjection] =
    if (!context.viewerIsActive) Vector.empty
    else {
      val index = CardIndex.from(context.ready.game).toOption
      PhasePowerProcedure.usable(catalog, context.ready, context.active.player,
        powers).flatMap { usable =>
        val printed = catalog.denizens.find(_.id.value == usable.card.value)
          .flatMap(d => d.powers.find(_.id == usable.power.id).map(d -> _))
        for {
          (card, power) <- printed
          source <- walkerDecisions.optionProjection(context.ready,
            context.viewer, index, option(usable.ref))
        } yield PhasePowerProjection(usable.power.id.value, source,
          card.name, power.rulesText)
      }
    }

  def controls(context: ScopedProjectionContext): Vector[String] =
    project(context).map(p => s"usePower:${p.powerId}:${p.source.id}")

  private def option(ref: DecisionOptionRef): DecisionOption = ref match {
    case denizen: DecisionOptionRef.Denizen => DecisionOption.Denizen(denizen)
    case relic: DecisionOptionRef.Relic => DecisionOption.Relic(relic)
    case other => DecisionOption.Button(DecisionOptionRef.Button(other.wireId),
      other.wireId)
  }
}
```

   Check the catalog denizen's display-name field (it may be `title` rather than `name`) and use it. A relic source reads `catalog.relics` the same way; add that lookup when the first relic phase power exists, not now.
- `LegalActionProjector`: take a `PhasePowerProjector` constructor argument, built in `GameProjector` from the same `WalkerDecisionProjector`. In `controls`, the `Phase.Act` vector, the `Phase.Wake` vector and the `Phase.Rest` vector each gain `++ phasePowers.controls(context)`: Act before its `.flatten`-ed list's end, Wake before `"endWake"`, Rest before `"finishRest"`.
- `GameProjection`: build `PhasePowerProjector` next to `LegalActionProjector`, and add `phasePowers = phasePowers.project(context)` to the final `.copy`.

- [ ] **Step 7: Run the suites**

Run: `./sbtw "frontend/testOnly oathdigital.protocol.CommandProtocolSuite oathdigital.protocol.ProjectionProtocolSuite" "testOnly oathdigital.application.PhasePowerProjectorSuite oathdigital.application.GameApplicationServiceSuite oathdigital.gameplay.powers.rest.SilverTongueSuite"`
Expected: PASS.

- [ ] **Step 8: Full gate and commit**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS" && python3 scripts/check-architecture.py && git diff --check`
Expected: all green.

```bash
git add src shared
git commit -m "feat(protocol): project usable phase powers and accept usePower

The projection lists the viewer's usable phase powers with their source
card, legal controls name each as usePower:<power>:<source>, and the
usePower intent starts the UsePower walker procedure.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 10: Phase power buttons and Finish Rest in the frontend

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/PhasePowerButtons.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala:95-106,329-335,568-577`, `frontend/src/main/scala/oathdigital/frontend/package.scala` (alias)
- Test: `frontend/src/test/scala/oathdigital/frontend/ServerModeUiSuite.scala`

**Interfaces:**
- Consumes: `GameProjection.phasePowers`, `PhasePowerProjection`, `GameIntent.UsePower`, `WalkerStartArgWire` (Task 9).
- Produces: `PhasePowerButtons.actions(value: GameProjection): Vector[(PhasePowerState, GameCommand)]`, `PhasePowerButtons.showsFinishRest(value): Boolean`, `PhasePowerButtons.render(value, canControl, panel: dom.Element, submit: GameCommand => Unit): Unit`; buttons carry class `phase-power` and `data-power-id`.

- [ ] **Step 1: Write the failing UI tests**

In `ServerModeUiSuite`, add `phasePowers: Vector[PhasePowerState] = Vector.empty` as the last parameter of the `projection` helper at line 931 and pass it to `GameProjection(...)`. The suite tests UI decisions through pure helpers (as `ServerUiSupport.takeWealthActions` is tested), so add:

```scala
  test("a legal phase power becomes one usePower command") {
    val power = PhasePowerState("denizen.silver-tongue",
      DecisionOptionState("denizen", "92", "Silver Tongue"),
      "Silver Tongue", "Take a favor.")
    val legal = projection(Set("usePower:denizen.silver-tongue:92", "finishRest"),
      phase = "rest", phasePowers = Vector(power))
    assertEquals(PhasePowerButtons.actions(legal), Vector(power ->
      GameCommand.UsePower("denizen.silver-tongue",
        WalkerStartArgWire("denizen", "92"))))
    assertEquals(PhasePowerButtons.actions(projection(Set("finishRest"),
      phase = "rest", phasePowers = Vector(power))), Vector.empty)
  }

  test("Finish Rest is offered only when finishRest is legal") {
    assert(PhasePowerButtons.showsFinishRest(projection(Set("finishRest"),
      phase = "rest")))
    assert(!PhasePowerButtons.showsFinishRest(projection(Set.empty,
      phase = "rest")))
    assert(!PhasePowerButtons.showsFinishRest(projection(Set("finishRest"),
      phase = "wake")))
  }
```

Import `oathdigital.protocol.WalkerStartArgWire` if the suite does not already.

- [ ] **Step 2: Run to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ServerModeUiSuite"`
Expected: compilation fails on `PhasePowerState`.

- [ ] **Step 3: Implement the buttons**

`package.scala`: `type PhasePowerState = oathdigital.protocol.projection.PhasePowerProjection` and `val PhasePowerState = oathdigital.protocol.projection.PhasePowerProjection`, placed beside the other projection aliases.

`PhasePowerButtons.scala`:

```scala
package oathdigital.frontend

import oathdigital.protocol.WalkerStartArgWire
import org.scalajs.dom

/** One button per legal phase power, in Act, Wake and Rest alike. */
object PhasePowerButtons {
  /** Each projected power whose `usePower` control is legal, with the
    * command its button submits.
    */
  def actions(value: GameProjection): Vector[(PhasePowerState, GameCommand)] =
    value.phasePowers.filter(power => value.legalControls.contains(
      s"usePower:${power.powerId}:${power.source.id}")).map(power =>
      power -> GameCommand.UsePower(power.powerId,
        WalkerStartArgWire(power.source.kind, power.source.id)))

  def showsFinishRest(value: GameProjection): Boolean =
    value.phase == "rest" && value.legalControls.contains("finishRest")

  def render(value: GameProjection, canControl: Boolean, panel: dom.Element,
      submit: GameCommand => Unit): Unit =
    actions(value).foreach { case (power, command) =>
      val control = dom.document.createElement("button")
        .asInstanceOf[dom.html.Button]
      control.className = "phase-power"
      control.textContent = power.name
      control.title = power.rulesText
      control.setAttribute("data-power-id", power.powerId)
      control.disabled = !canControl
      control.onclick = _ => submit(command)
      panel.appendChild(control)
    }
}
```

Match the import of `GameProjection` and `GameCommand` to how `DistributePanelRenderer` (Task 2) imports them.

In `ActionDecisionRenderer`:
- Wake panel: call `PhasePowerButtons.render(value, canControl, panel, submitCommand)` before the "End Wake" button.
- Act panel: call it before the `beginRest` button.
- Rest block:

```scala
   if (value.phase == "rest" && presentation.showGameplayControls) {
     PhasePowerButtons.render(value, canControl, panel, submitCommand)
     if (PhasePowerButtons.showsFinishRest(value)) {
       panel.appendChild(text("p", "informational",
         "Finish Rest to return card resources, reveal secrets, refresh " +
           "Supply, and wake the next player."))
       val finish = button("Finish Rest", "rest-action finish-rest")
       finish.disabled = !canControl
       finish.onclick = _ => submitCommand(GameCommand.FinishRest)
       panel.appendChild(finish)
     }
   }
```

   Check the file stays at or under 800 lines. It shrank in Task 6.

- [ ] **Step 4: Run the UI suite**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ServerModeUiSuite"`
Expected: PASS.

- [ ] **Step 5: Full gate and commit**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS" && python3 scripts/check-architecture.py && git diff --check`
Expected: all green.

```bash
git add frontend
git commit -m "feat(frontend): render phase power buttons and legal-only Finish Rest

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 11: The pending-walker invariant

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/OathRules.scala` (every `handle` overload)
- Create: `src/test/scala/oathdigital/application/PendingWalkerInvariantSuite.scala`, `src/test/scala/oathdigital/application/OathkeeperTieFixture.scala`
- Create: `src/test/scala/oathdigital/gameplay/PendingWalkerRulesSuite.scala`, `src/test/scala/oathdigital/gameplay/powers/rest/LeagueTreatyFixture.scala`
- Modify: `src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala:1339-1406` (use the fixture), `src/test/scala/oathdigital/gameplay/powers/rest/LeagueTreatySuite.scala` (use the fixture)

**Interfaces:**
- Consumes: every earlier task. `GameCommand.UsePower` (Task 9), `SilverTongueFixture.arranged` (Task 9), `LeagueTreatySuite.arranged` (Task 5).
- Produces: `OathRules.handle` refuses with `InvalidEventOrder("a walker procedure is already pending")` whenever `walkerPending` or `walkerProcedure` is set. Test fixtures `OathkeeperTieFixture.parked(service, repository, gameId): (GameAccepted, PlayerId, PlayerId)` returning `(parked, active, holder)` and `LeagueTreatyFixture.arranged(ruler: Option[PlayerId], favor: Vector[(Suit, Int)]): (ReadyGame, SiteId)`.

- [ ] **Step 1: Move the two park arrangements into fixtures**

- `LeagueTreatyFixture`: move `LeagueTreatySuite`'s `act`, `suitOf`, `treatyCard` and `arranged` into `object LeagueTreatyFixture` unchanged, and have the suite import them.
- `OathkeeperTieFixture`: move the body of `GameApplicationServiceSuite`'s "an off-turn Oathkeeper tie parks through the application service and survives reload" from `val setup = execute(service, gameId)` through the `parked` value and its `WalkerParked`/`AwaitingOathkeeperRecipient` assertions into `def parked(service: GameApplicationService, repository: InMemoryEventStreamRepository, gameId: String): (GameAccepted, PlayerId, PlayerId)`. It returns `(parked, active, holder)`. The fixture needs `execute`: copy the suite's private `execute` helper (lines 1157-1185) into the fixture as `def setUp` with the same body and defaults. The existing test calls the fixture and keeps every assertion after the park, including `leaderB`, which it recomputes from the parked state as the fixture did.
- Run `./sbtw "testOnly oathdigital.application.GameApplicationServiceSuite oathdigital.gameplay.powers.rest.LeagueTreatySuite"`. Expected: PASS, with no behavior change.

- [ ] **Step 2: Write the failing rules-level suite**

`PendingWalkerRulesSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.actions.{CampaignCommand, ChallengeCommand,
  EconomyCommand, MinorActionCommand, NegotiationCommand, SearchCommand,
  VisionCommand}
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.powers.rest.{LeagueTreatyFixture, SilverTongue,
  SilverTongueFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

/** The rules half of the pending-walker invariant: over a parked walker,
  * neither a walker start nor any legacy `handle` overload runs. The
  * application half is `PendingWalkerInvariantSuite`.
  */
class PendingWalkerRulesSuite extends munit.FunSuite {
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private val pending = Left(OathViolation.InvalidEventOrder(
    "a walker procedure is already pending"))

  private def leagueTreatyPark: OathState = {
    val (ready, _) = LeagueTreatyFixture.arranged(Some(
      LeagueTreatyFixture.act.game.current.players.map(_.player)
        .find(_ != LeagueTreatyFixture.act.game.current.turn.activePlayer).get),
      Vector(Suit.Arcane -> 2, Suit.Discord -> 2))
    val parked = rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest,
      ready.game.current.turn.activePlayer).toOption.get
    assert(parked.continue.isInstanceOf[OathContinue.AwaitingRestDecision])
    parked.state
  }

  private def silverTonguePark: OathState = {
    val (ready, actor) = SilverTongueFixture.arranged(
      Vector(Suit.Arcane, Suit.Nomad), Set(Suit.Arcane, Suit.Nomad))
    val parked = rules.startWalker(Ready(ready), ActionRef.UsePower(SilverTongue.id),
      actor, Vector.empty, Vector(DecisionOptionRef.Denizen(DenizenId("92"))))
      .toOption.get
    assert(parked.continue.isInstanceOf[OathContinue.AwaitingPowerDecision])
    parked.state
  }

  Vector("off-turn League Treaty" -> (() => leagueTreatyPark),
    "Silver Tongue choice" -> (() => silverTonguePark)).foreach {
    case (name, park) =>
      test(s"nothing but a resume runs over a parked $name") {
        val state = park()
        val ready = state.asInstanceOf[Ready].value
        val actor = ready.game.current.turn.activePlayer
        val site = ready.game.current.map.inPlay.head
        (StartableRef.all :+ ActionRef.UsePower(SilverTongue.id)).foreach { ref =>
          assertEquals(rules.startWalker(state, ref, actor).left.toOption,
            pending.left.toOption, ref.key)
        }
        Vector(
          rules.handle(state, EconomyCommand.Muster(actor,
            EconomyTargetRef.Denizen(DenizenId("d1")))),
          rules.handle(state, SearchCommand.Start(actor, DecisionId("s1"),
            SearchSource.WorldDeck, Vector.empty)),
          rules.handle(state, ChallengeCommand.Begin(actor, DecisionId("c1"),
            Banner.all.head)),
          rules.handle(state, MinorActionCommand.PeekSiteRelics(actor)),
          rules.handle(state, VisionCommand.Reveal(actor, VisionId("v1"))),
          rules.handle(state, NegotiationCommand.Decline(actor, DecisionId("n1"))),
          rules.handle(state, CampaignCommand.Start(actor, DecisionId("cp1"),
            Vector(site), 1))
        ).zipWithIndex.foreach { case (result, index) =>
          assertEquals(result, pending, s"legacy handle #$index")
        }
      }
  }
}
```

If `LeagueTreatyFixture.act` is a `def` that rebuilds setup, bind it to a local `val` first. The off-turn owner must come from the same state the arrangement starts from.

- [ ] **Step 3: Run to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.PendingWalkerRulesSuite"`
Expected: FAIL. The legacy handles reject with their own messages (for example `validateAct`'s "a walker procedure is pending; legacy actions are blocked", or a phase error), not the invariant's. The `startWalker` assertions already pass.

- [ ] **Step 4: Guard every `handle` overload**

In `OathRules.scala`, add:

```scala
  /** Walker-ownership invariant: while a walker procedure is parked, only
    * its resume commands run. `GameApplicationService.applyCommand` refuses
    * other commands first; this keeps the rules boundary honest for every
    * other caller.
    */
  private def unlessWalkerPending(state: OathState)(
      handled: => Either[OathViolation, OathTransition])
      : Either[OathViolation, OathTransition] = state match {
    case Ready(ready) if ready.game.current.walkerPending.nonEmpty ||
        ready.game.current.walkerProcedure.nonEmpty =>
      Left(InvalidEventOrder("a walker procedure is already pending"))
    case _ => handled
  }
```

Wrap the body of each `handle` overload (`EconomyCommand`, `SearchCommand`, `ChallengeCommand`, `MinorActionCommand`, `VisionCommand`, `NegotiationCommand`, `CampaignCommand`): `def handle(state: OathState, command: X): Either[OathViolation, OathTransition] = unlessWalkerPending(state) { <existing body> }`. Setup's `handle` (if `OathRules` exposes one) is not wrapped: no walker exists before the game is ready.

- [ ] **Step 5: Run the rules suite**

Run: `./sbtw "testOnly oathdigital.gameplay.PendingWalkerRulesSuite"`
Expected: PASS.

- [ ] **Step 6: Write the service-level matrix**

`PendingWalkerInvariantSuite.scala`:

```scala
package oathdigital.application

import java.nio.file.{Files, Paths}
import oathdigital.gameplay.OathContinue
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.protocol.{GameIntent => Intent, _}

/** The application half of the pending-walker invariant. Over each parked
  * walker, every `GameCommand` constructor except `ResolveWalker` and
  * `RollWalker` is refused and appends nothing, whoever submits it.
  */
class PendingWalkerInvariantSuite extends munit.FunSuite {

  /** Every transport intent, copied verbatim from `CommandProtocolSuite`'s
    * `examples` (shared tests are not on this project's classpath).
    */
  private val intents: Vector[GameIntent] = Vector(
    // Paste CommandProtocolSuite.examples here, unchanged.
  )

  /** One instance of every `GameCommand` constructor, bound to `actor`. */
  private def everyCommand(actor: PlayerId): Vector[GameCommand] =
    intents.map(intent => GameIntentMapper.bind(actor, intent)
      .fold(failure => fail(s"$intent did not bind: $failure"), identity)) ++
      Vector(
        GameCommand.WithModifiers(GameCommand.EndWake(actor), Vector.empty),
        GameCommand.Begin(plan),
        GameCommand.ChooseAdviser(actor, DenizenId("d1")),
        GameCommand.CompleteSearch(actor, DecisionId("s1"), DenizenId("d1"),
          Vector.empty, SearchPlacement.Site(None)))

  private def resumes(command: GameCommand) = command match {
    case _: GameCommand.ResolveWalker | _: GameCommand.RollWalker => true
    case _ => false
  }

  test("the sample holds every GameCommand constructor") {
    val source = Files.readString(Paths.get(
      "src/main/scala/oathdigital/application/GameCommands.scala"))
    val body = source.substring(source.indexOf("object GameCommand {"),
      source.indexOf("\n}\n", source.indexOf("object GameCommand {")))
    val declared = "final case class (\\w+)".r.findAllMatchIn(body)
      .map(_.group(1)).toSet
    assertEquals(everyCommand(PlayerId("p1")).map(_.productPrefix).toSet, declared)
  }

  private def assertOnlyResumes(service: GameApplicationService,
      repository: InMemoryEventStreamRepository, gameId: String,
      parked: GameAccepted, players: Vector[PlayerId]): Unit = {
    val before = repository.load(gameId).toOption.flatten.get.records
    for {
      player <- players
      command <- everyCommand(player) if !resumes(command)
    } assert(service.handle(gameId, parked.nextSequence, command).isLeft,
      s"$command by $player must be refused over a parked walker")
    assertEquals(repository.load(gameId).toOption.flatten.get.records, before,
      "a refused command must append nothing")
  }

  test("over a parked Recover roll in Act, only resumes are accepted") {
    val recoverSite = catalog.sites.find(site =>
      site.recoverDifficulty.exists(d => d > 0 && d <= 4) &&
        site.relicSlots > 0 &&
        !site.handlers.exists(_.contains(".homeland-"))).get.id
    val recoverPlan = plan.copy(orderedSites = recoverSite +:
      plan.orderedSites.filterNot(_ == recoverSite))
    val actor = recoverPlan.firstPlayer
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val setup = OathkeeperTieFixture.setUp(service, "invariant-recover",
      recoverPlan.orderedSites, recoverPlan)
    val act = service.handle("invariant-recover", setup.nextSequence,
      GameCommand.EndWake(actor)).toOption.get
    val parked = service.handle("invariant-recover", act.nextSequence,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor))).toOption.get
    assertEquals(parked.continue, OathContinue.AwaitingRecoverRoll(actor,
      DecisionId(RecoverProcedure.rollDecisionId)))
    assertOnlyResumes(service, repository, "invariant-recover", parked,
      recoverPlan.participants.map(_.playerId))
  }

  test("over an off-turn Oathkeeper recipient decision, only resumes are accepted") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val (parked, _, _) = OathkeeperTieFixture.parked(service, repository,
      "invariant-oathkeeper")
    assertOnlyResumes(service, repository, "invariant-oathkeeper", parked,
      plan.participants.map(_.playerId))
  }
}
```

Fill `intents` by pasting `CommandProtocolSuite.examples` as it stands after Tasks 6 and 9, which is every intent including `UsePower` and `ResolveWalker`. It is the one list in this plan that is copied rather than written out: the source of truth is that vector, and the coverage test above fails if the copy misses a constructor.

If `recoverPlan.participants.map(_.playerId)` or `plan.participants` does not exist under those names, use the same player list `setUp` iterates.

- [ ] **Step 7: Run the matrix**

Run: `./sbtw "testOnly oathdigital.application.PendingWalkerInvariantSuite"`
Expected: PASS. The application gate already refuses these commands (Correction 7), so this suite pins it rather than driving new code. If a command is accepted, the gate has a hole: fix it in `GameApplicationService.applyCommand`, never in the test.

- [ ] **Step 8: Full gate and commit**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS" && python3 scripts/check-architecture.py && git diff --check`
Expected: all green.

```bash
git add src
git commit -m "feat(walker): refuse every non-resume command over a parked walker

The rules boundary now refuses legacy handles while a walker is parked,
matching the application gate. Both layers are pinned: every GameCommand
over a parked Recover roll and an off-turn Oathkeeper decision, and every
walker start and legacy handle over League Treaty and Silver Tongue parks.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 12: Close out the superseded spec statements

**Files:**
- Modify: `docs/superpowers/specs/2026-09-12-walker-ownership-and-phases-design.md:66-72,244-247,272-273,553-555,564-565`
- Modify: `docs/superpowers/specs/2026-09-13-rest-walker-and-phase-powers-design.md:3`

**Interfaces:**
- Consumes: the shipped behavior of Tasks 1-11.
- Produces: no code.

- [ ] **Step 1: Correct the 2026-09-12 spec**

Make each edit in place, keeping surrounding text:

1. Scope paragraph (lines 66-72): delete "the Rest completion continuation;" from the out-of-scope list, and append the sentence: "The Rest completion continuation was settled by `2026-09-13-rest-walker-and-phase-powers-design.md`."
2. Lines 244-247: replace the paragraph starting "`continuationIn` keeps its typed rejection" with:

   > `continuationIn` gives Act `ActActionSelection`, Wake `AwaitingWakeAction` and Rest `AwaitingRestAction`, and keeps its typed rejection for every other phase. Rest gained its continuation with Begin Rest (`2026-09-13-rest-walker-and-phase-powers-design.md`). Finish Rest never consults it: its turn boundary owns the continuation.

3. In "Completion and the action boundary", change the Continuation bullet's last clause to "Act gives `ActActionSelection`, Wake gives `AwaitingWakeAction`, Rest gives `AwaitingRestAction`, any other phase is a typed rejection." Replace the paragraph "Legacy Rest commands (`BeginRest`, `FinishRest`, the power hook commands) are not actions and do not run it." with:

   > Begin Rest and Finish Rest are phase transitions and do not run it. Finish Rest runs the turn boundary instead. A REST power used through `UsePower` is an action and does run it (`2026-09-13-rest-walker-and-phase-powers-design.md`).

4. Open item "Answer kinds" (lines 553-555): append "Amounts now have `DecisionQuery.Distribute` (`2026-09-13-rest-walker-and-phase-powers-design.md`); ordering and per-site allocations remain open."
5. Open item "Rest completion continuation" (lines 564-565): replace its text with "Settled: Begin Rest completes to `AwaitingRestAction`, and Finish Rest's turn boundary to `AwaitingWakeAction` or `GameFinished` (`2026-09-13-rest-walker-and-phase-powers-design.md`)."
6. Open item "Unanswered off-turn decisions": replace "as legacy Rest hooks and Oathkeeper recipients already do" with "as League Treaty and Oathkeeper recipients do".

- [ ] **Step 2: Mark the Rest spec implemented**

In `2026-09-13-rest-walker-and-phase-powers-design.md`, change line 3 to `> Status: implemented on feat/rest-walker; see docs/superpowers/plans/2026-09-13-rest-walker-and-phase-powers.md.`

- [ ] **Step 3: Check and commit**

Run: `grep -n "Nothing in this design can complete in Rest\|Legacy Rest commands" docs/superpowers/specs/2026-09-12-walker-ownership-and-phases-design.md`
Expected: no output.

Run: `git diff --check`
Expected: no output.

```bash
git add docs/superpowers/specs
git commit -m "docs(spec): record what the Rest walker settled

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```
