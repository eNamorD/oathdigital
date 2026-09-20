# Campaign on the Procedure Walker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Campaign, Conquest and Raid, onto the procedure walker as `ActionRef.Campaign`, show its dice and result to every player, and delete the legacy Campaign path and then the legacy `pending` slot.

**Architecture:** One tree in rulebook order: cost, kind, targets, force, gather pools, attacker then defender battle plans, an automatic attack roll, a recorded attack result, sacrifice, an automatic defense roll, a recorded defense result, then losses and resolution in named windows. Every battle fact is a recorded roll outcome or an answer, and nothing leaves the board until the results are known, so every `Branch` and `Repeat` obeys walker rule W. The engine gains `ChooseMany` with a minimum of 0, a `Distribute` total range, automatic `Roll` nodes fed by a dice source, attack outcomes, an executed `ModifyRollOutcome`, and an `OptionRestriction` contribution. Vow of Peace becomes a root `Restriction` and Narrow Pass an `OptionRestriction`. A public `CampaignResult` fact, written by a new `RecordCampaignResult` operation, shows the dice and the victor.

**Tech Stack:** Scala 2.13, sbt via `./sbtw`, munit, Scala.js frontend (`frontend/`), shared protocol module (`shared/`, compiled inside the root project).

**Spec:** [docs/superpowers/specs/2026-09-19-campaign-walker-design.md](../specs/2026-09-19-campaign-walker-design.md)

## Global Constraints

- Persisted text (code, comments, docs, commit messages, PR text) is normal English. Commit trailer: `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.
- Run sbt through `./sbtw`. `shared/` compiles inside the root project, so its tests run through the root `testOnly`; frontend tests run through `frontend/testOnly`.
- `BackendArchitectureSuite` bounds every production Scala file to 800 lines, forbids power names in walker sources, forbids the model importing gameplay, and forbids powers importing `gameplay.walker`. Check `wc -l` on every file a task grows. `ProcedureWalker.scala` is at 705 lines: Task 3 moves the roll code out before Task 4 adds to it.
- Walker rule W: a `Branch.select`, a `Repeat` guard and a `Decide` query read only answered values, recorded roll outcomes, and state that no earlier step of the same tree has changed. A `Repeat` guard runs only at pass boundaries, so it may read live state; a `Branch.select` is re-evaluated on resume and may not. Anything else is built inside a `BuildOps`.
- Spec, "Rule changes, all deliberate": unsupported Campaign handlers are ignored and recorded (`ActionKind.Campaign`, `fallbackKind`); the first-game gates (exile-only, unaltered Foundation, inactive legacies) are dropped; there is no cancel after the start.
- Spec, "Out of scope": any new Campaign power, action-budget accounting, the Simultaneous node, and everything the Negotiation spec deferred.
- Spec, "Deferred": all rolls should eventually become automatic (Recover stays `Parked`), real consent for the Pass, the first-game rule audit, converting plan handlers into power contributions.
- Journals are forward-only: no old-journal compatibility. Deleted legacy events are not replayed.
- After every task: the task's suites pass and `./sbtw "Test/compile" "frontend/Test/compile"` succeeds. After Tasks 4, 12, 13, 17 and 18 and at Task 19 run the full gate `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`.
- macOS: use `perl -pi -e` for in-place regex edits, not `sed -i`. Quote globs in shell (`--include='*.scala'`), the shell is zsh.

## Deviations from the spec

Found while reading the code for this plan. Task 19 folds each into the spec.

1. **There is no action-history feed.** Spec 20a assumed one. Projections are built from state alone, rolls are cleared when the walker completes, and the only event view is a development-only raw dump. You chose a durable result fact. Tasks 7 and 13 add `CampaignResult`, a `RecordCampaignResult` operation, `CurrentGameState.lastCampaignResult` and a public `GameProjection.lastCampaign`; Task 14 adds its panel.
2. **A single-target placement is `ChooseAmount`, not `Distribute`.** `wellFormed` rejects a distribution with fewer than two slots. `campaign.placement` is a `ChooseAmount(0, survivors)` for one target and a `Distribute` for two or more.
3. **`ModifyRollOutcome` is defined but never executed.** The executor's catch-all `case (result, _) => result` silently ignores it. Task 3 executes it as an upsert (it creates the outcome when the pool has none).
4. **An automatic `Roll` on an empty pool is skipped and records nothing.** Replay requires the pool to exist, and a zero-force Campaign with no plans never creates one. Downstream steps read a missing outcome as zero faces, zero skulls and zero score.
5. **Victory reads recorded outcomes only.** The defender's board force cannot be read after the losses run, so a `CampaignDefenseResult` window writes `ModifyRollOutcome(defense, score = dice score + defender force)` right after the defense roll. The spec's window list gains `CampaignDefenseResult`.
6. **The plan loop finishes by itself when no unused plan is left.** The explicit Finish option is offered while at least one plan can still be chosen. Legacy always required a Finish.
7. **The plan registry stops depending on `PendingProcedure.Campaign`.** The new registry (`actions/campaign/CampaignPlans.scala`) takes a small `CampaignSetup` and omits `validateRecorded`, `resolve` and `validate` (replay applies recorded operations). The legacy registry stays until Task 17, which deletes it together with the dormant `TransformAttackResult`, `ReplaceLosingForcePolicy` and `Suspend` effects (no handler produces them; the legacy event codec still names them until then).
8. **The Raid discard rule drops "revealed by a defender plan".** No registered defender plan carries `RevealSource`, so only facedown advisers are discarded.
9. **`WalkerDecisionProjector.rollOutcome` is Recover-specific** and would attach a Recover difficulty to every decision of any procedure. Task 13 gates it on `ActionRef.Recover`.
10. **`Distribute`'s projected `total` field becomes `minTotal` and `maxTotal`** in the DTO and the wire (a protocol change, deployed together).
11. **`OathContinue.AwaitingCampaignDecision(playerId, decision)` replaces the four legacy Campaign continuations.**
12. **The Vow of Peace second sentence is not modelled.** "Attackers cannot sacrifice warbands to increase their attack against you" applies to a faceup Vow of Peace held by a defender. Legacy never modelled it either. It falls under the ignore-and-record rule.
13. **The outcome branch reads the durable result.** After the losses change the board, the only nodes re-selected are those on the path to a parked placement or relocation decision. They read `lastCampaignResult`, written before the losses and never changed after, instead of deriving the kind and the defender from live state (Task 11).
14. **"Ignore and record" records what the reviewed power catalog lists at the Campaign windows** (today Bag of Siegeworks). The other handlers the legacy classifier named are neither blocked nor recorded, as for every other ported action.
15. **Correction to 14, found in Task 9: nothing is recorded today.** The resolver reports a diagnostic only for an unimplemented *automatic* handler at the window being resolved. Bag of Siegeworks is *player-selected* and hooks the attacker battle-plan window, so `PowerRuntime.ignored` for `ActionKind.Campaign` returns nothing for it. The `fallbackKind` wiring stays (it records whatever the catalog lists later), and the start test asserts only that an unimplemented held power does not block. The spec's "ignored and recorded" is therefore "ignored" for now.

## File structure

New:
- `src/main/scala/oathdigital/gameplay/walker/WalkerRolls.scala`: roll outcome derivation, `recordRoll` and `writeRollOutcome`, moved out of `ProcedureWalker`.
- `src/main/scala/oathdigital/gameplay/walker/WalkerDice.scala`: the dice source.
- `src/main/scala/oathdigital/model/CampaignTypes.scala`: `CampaignKind`, `CampaignDefender`, `CampaignRaidTarget`, the plan vocabulary and `CampaignResult`, moved out of `PendingProcedures.scala`.
- `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala`: what the answers say (kind, defender, targets, force) and who may be chosen.
- `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala`: the tree, `build`, `rebuild`, `startable`, the decision ids.
- `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlans.scala`: the plan registry, trimmed, and the plan steps.
- `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignBattle.scala`: pools, results, victor, losses.
- `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignConquest.scala`, `CampaignRaid.scala`: the two resolutions.
- `src/main/scala/oathdigital/gameplay/powers/campaign/CampaignPowers.scala`: Vow of Peace and the Narrow Pass campaign contribution (the existing `powers/CampaignPowers.scala` reviewed catalog stays).
- `src/main/scala/oathdigital/application/CampaignResultProjector.scala`.
- `frontend/src/main/scala/oathdigital/frontend/CampaignResultPanel.scala`.
- Tests: `CampaignFixture.scala`, `CampaignSetupSuite.scala`, `CampaignProcedureSuite.scala`, `CampaignBattleSuite.scala`, `CampaignRaidSuite.scala`, `CampaignPowersSuite.scala`, `CampaignParitySuite.scala`, `AutomaticRollSuite.scala`, `OptionRestrictionSuite.scala`, `CampaignWindowsSuite.scala`, `CampaignResultSuite.scala`, `CampaignResultProjectionSuite.scala`, frontend `CampaignResultPanelSuite.scala`.

Modified throughout: `Decisions.scala`, `DecisionQueries.scala`, `CoreOperations.scala`, `OperationStateMutation.scala`, `ProcedureWalker.scala`, `WalkerPowerGather.scala`, `WalkerReplay.scala`, `WalkerSimulation.scala`, `WalkerProcedureRegistry.scala`, `ContributingPower.scala`, `ContributionCollector.scala`, `OathRules.scala`, `OathRulesWalker.scala`, `PowerWindow.scala`, `ProcedureRef.scala`, `GameProcedureProtocol.scala`, `GameState.scala`, `WalkerEventCodec.scala`, `WalkerOperationCodec.scala`, `GameRandomPorts.scala`, `GameApplicationService.scala`, `WalkerDecisionProjector.scala`, `LegalActionProjector.scala`, `GameProjection.scala`, `ActionProjectionDtos.scala`, `ActionProjectionCodec.scala`, `GameProjectionDto.scala`, `GameProjectionCodec.scala`, `WalkerPowerCatalog.scala`, `TravelSitePowers.scala`, frontend files.

---

### Task 1: `ChooseMany` may take zero options

**Files:**
- Modify: `src/main/scala/oathdigital/model/Decisions.scala:254-260`, `src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala:74-88`
- Modify tests: `src/test/scala/oathdigital/gameplay/walker/DecisionQuerySuite.scala:418-464`, `frontend/src/test/scala/oathdigital/frontend/WalkerSelectionDraftSuite.scala`

**Interfaces:**
- Produces: `DecisionQuery.ChooseMany(min, max, options, heading)` is well-formed for `0 <= min <= max <= options.size` with `max >= 1`, and is still rejected when it takes every option (`min == max == options.size`). `accepts` already checks `min <= selected.size <= max`, so an empty selection is a valid answer when `min == 0`. The wire, journal and projection codecs already carry an empty selection, and the frontend draft already confirms at its minimum, so nothing outside `wellFormed` changes.

- [ ] **Step 1: Update the tests first**

In `DecisionQuerySuite.scala` replace the well-formed test with:

```scala
  test("a choose-many query needs 0 <= min <= max <= options, one pickable option, and is not forced") {
    assertEquals(wellFormed(many(1)), Right(()))
    assertEquals(wellFormed(many(2)), Right(()))
    assertEquals(wellFormed(many(1, max = Some(3))), Right(()))
    assertEquals(wellFormed(many(2, max = Some(3))), Right(()))
    assertEquals(wellFormed(many(0, max = Some(1))), Right(()))
    assertEquals(wellFormed(many(0, max = Some(3))), Right(()))
    assertEquals(wellFormed(many(0)),
      invalid("decision recover.choice declares no selection to make"))
    assertEquals(wellFormed(many(-1, max = Some(2))), invalid(
      "decision recover.choice declares a selection range -1..2"))
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
```

and add after the existing answer test:

```scala
  test("an optional choose-many accepts the empty selection and any subset") {
    val optional = many(0, max = Some(3))
    assertEquals(accepts(optional, DecisionAnswer.ChooseManyAnswer(Vector.empty)),
      Right(()))
    assertEquals(accepts(optional, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("b")))), Right(()))
    assertEquals(accepts(optional, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a"), siteRef("b"), siteRef("c")))), Right(()))
    assertEquals(accepts(many(0, max = Some(2)), DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a"), siteRef("b"), siteRef("c")))), invalid(
      "decision recover.choice selects 3 options outside 0..2"))
  }
```

In `WalkerSelectionDraftSuite.scala` add:

```scala
  test("an optional choose-many confirms an empty selection and submits it") {
    val optional = many.copy(minimum = Some(0), maximum = Some(3))
    val Some(draft: WalkerChooseManyDraft) = WalkerSelectionDraft.reconcile(None,
      context, parked("campaign.targets", optional)): @unchecked
    assert(draft.canConfirm)
    assertEquals(draft.command, Some(Intent.ResolveWalker("campaign.targets",
      DecisionAnswerWire.ChooseManyWire(Vector.empty))))
    assertEquals(draft.toggle("site:b").command, Some(Intent.ResolveWalker(
      "campaign.targets", DecisionAnswerWire.ChooseManyWire(
        Vector(DecisionOptionWire("site", "b"))))))
  }
```

- [ ] **Step 2: Run to confirm the first fails**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.DecisionQuerySuite" "frontend/testOnly oathdigital.frontend.WalkerSelectionDraftSuite"`
Expected: FAIL in `DecisionQuerySuite` (`many(0, max = Some(1))` is rejected as "declares no selection to make"). The frontend test already passes.

- [ ] **Step 3: Relax `wellFormed`**

In `DecisionQueries.scala` replace the `ChooseMany` case's checks:

```scala
    case DecisionQuery.ChooseMany(min, max, options, _) =>
      val refs = options.map(_.ref)
      for {
        _ <- require(refs.distinct.size == refs.size, decisionId,
          "declares duplicate options")
        _ <- require(max >= 1, decisionId, "declares no selection to make")
        _ <- require(min >= 0 && min <= max, decisionId,
          s"declares a selection range $min..$max")
        _ <- require(max <= refs.size, decisionId,
          "declares a maximum above its option count")
        _ <- require(!(min == max && max == refs.size), decisionId,
          "declares a selection that already takes every option, leaving " +
            "nothing to decide")
      } yield ()
```

In `Decisions.scala` replace the `ChooseMany` doc:

```scala
  /** Pick between `min` and `max` distinct options, inclusive. Well-formed
    * only when `0 <= min <= max <= options.size`, `max >= 1`, and not the
    * forced case `min == max == options.size`, which takes every option and
    * asks nothing. `min == 0` makes the pick optional: the empty selection is
    * an answer. An exact count is `min == max`.
    */
```

- [ ] **Step 4: Run and commit**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.DecisionQuerySuite oathdigital.application.WalkerDecisionProjectorSuite" "frontend/testOnly oathdigital.frontend.WalkerSelectionDraftSuite oathdigital.frontend.WalkerSelectionPanelsSuite"`
Expected: PASS.

```bash
git add -A src frontend
git commit -m "feat(walker): let ChooseMany take zero options

An optional pick needs the empty selection as an answer. wellFormed now
requires 0 <= min <= max <= options and one pickable option; a query that
takes every option is still rejected as forced. The codecs and the frontend
draft already carried an empty selection.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: `Distribute` takes a total range

**Files:**
- Modify: `src/main/scala/oathdigital/model/Decisions.scala:267-287`, `src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala:152-192,248-253,270-292`, `src/main/scala/oathdigital/gameplay/powers/rest/LeagueTreatyContribution.scala:82`, `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala:239-249`, `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala:145`, `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala:212-267`, `frontend/src/main/scala/oathdigital/frontend/DistributeDecisionState.scala`, `frontend/src/main/scala/oathdigital/frontend/DistributePanelRenderer.scala:24-32`
- Modify tests: `src/test/scala/oathdigital/gameplay/walker/DecisionQuerySuite.scala:328-331`, `src/test/scala/oathdigital/gameplay/walker/WalkerPreviewSuite.scala:70`, `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala:128-142`, `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala:153-162`, `frontend/src/test/scala/oathdigital/frontend/DistributePanelRenderSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/DistributeDecisionStateSuite.scala:12,21`

**Interfaces:**
- Produces: `DecisionQuery.Distribute(slots, minTotal, maxTotal, heading, confirmLabel)` and `DecisionQuery.Distribute.exactly(slots, total, heading, confirmLabel)`, which sets both totals. The answer's sum must satisfy `minTotal <= sum <= maxTotal`. `DecisionQueryProjection` carries `minTotal: Option[Int]` and `maxTotal: Option[Int]` in place of `total`. `DistributeDecisionState(slots, minTotal, maxTotal, amounts)` with `allocated`, `remaining = maxTotal - allocated` and `canConfirm = minTotal <= allocated <= maxTotal`, and `DistributeDecisionState.opened(slots, minTotal, maxTotal, suggested)`.

A range is forced when only one answer exists: every slot at its minimum already reaches `maxTotal`, or every slot at its maximum reaches only `minTotal`. So `wellFormed` requires `0 <= minTotal <= maxTotal`, reachability (`minimums <= maxTotal` and `minTotal <= maximums`), `minimums != maxTotal`, `maximums != minTotal`, two variable slots, and a suggestion that `accepts` would take. With `minTotal == maxTotal` this is exactly today's rule, which is why League Treaty and every existing test keep their meaning.

- [ ] **Step 1: Update the tests first**

In `DecisionQuerySuite.scala` change the `dist` helper (line 328) and add a `ranged` helper beside it:

```scala
  private def dist(slots: Vector[DistributeSlot], total: Int,
      heading: Option[String] = Some("League Treaty"),
      confirm: String = "Move favor") =
    DecisionQuery.Distribute.exactly(slots, total, heading, confirm)

  private def ranged(slots: Vector[DistributeSlot], min: Int, max: Int) =
    DecisionQuery.Distribute(slots, min, max, Some("Place force"), "Place")
```

Add these tests after the test named `"the worked example's answer is accepted"`:

```scala
  test("a ranged distribution is well formed when its range is reachable and open") {
    val two = Vector(slot(Suit.Arcane, 0, 3), slot(Suit.Nomad, 0, 3))
    assertEquals(DecisionQueries.wellFormed(decisionId, ranged(two, 0, 3)),
      Right(()))
    assertEquals(DecisionQueries.wellFormed(decisionId, ranged(two, 1, 4)),
      Right(()))
  }

  test("a malformed range names its own defect") {
    val two = Vector(slot(Suit.Arcane, 0, 3), slot(Suit.Nomad, 0, 3))
    val cases = Vector(
      ranged(two, 4, 3) -> "declares a total no answer can meet",
      ranged(two, -1, 3) -> "declares a total no answer can meet",
      ranged(two, 7, 8) -> "declares a total no answer can meet",
      ranged(Vector(slot(Suit.Arcane, 2, 3), slot(Suit.Nomad, 1, 3)), 0, 3) ->
        "declares minimums that already make its total, leaving nothing to decide",
      ranged(two, 6, 9) ->
        "declares maximums that already make its total, leaving nothing to decide")
    cases.foreach { case (query, detail) =>
      assertEquals(DecisionQueries.wellFormed(decisionId, query),
        violation(detail), detail)
    }
  }

  test("a ranged distribution accepts any sum inside its range and no other") {
    val query = ranged(Vector(slot(Suit.Arcane, 0, 3), slot(Suit.Nomad, 0, 3)),
      0, 3)
    Vector(0 -> 0, 1 -> 0, 2 -> 1, 3 -> 0).foreach { case (a, n) =>
      assertEquals(DecisionQueries.accepts(decisionId, query,
        amounts(Suit.Arcane -> a, Suit.Nomad -> n), anyone), Right(()))
    }
    assertEquals(DecisionQueries.accepts(decisionId, query,
      amounts(Suit.Arcane -> 2, Suit.Nomad -> 2), anyone),
      violation("distributes an amount outside 0..3"))
    val floor = ranged(Vector(slot(Suit.Arcane, 0, 3), slot(Suit.Nomad, 0, 3)),
      2, 4)
    assertEquals(DecisionQueries.accepts(decisionId, floor,
      amounts(Suit.Arcane -> 1, Suit.Nomad -> 0), anyone),
      violation("distributes an amount outside 2..4"))
  }
```

In `WalkerPreviewSuite.scala` (line 70) and `WalkerDecisionProjectorSuite.scala` (line 130) keep the existing arguments and change the constructor name:

```bash
perl -pi -e 's/DecisionQuery\.Distribute\(/DecisionQuery.Distribute.exactly(/' \
  src/test/scala/oathdigital/gameplay/walker/WalkerPreviewSuite.scala \
  src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala
```

In `WalkerDecisionProjectorSuite.scala` replace `assertEquals(query.total, Some(2))` (line 141) with `assertEquals(query.minTotal -> query.maxTotal, Some(2) -> Some(2))` and add after that test:

```scala
  test("a ranged distribution projects both totals") {
    val (context, actor) = parked(ActionRef.Recover)
    val tree = Sequence(Decide("test.distribute", actor, DecisionQuery.Distribute(
      Vector(DistributeSlot(DecisionOptionRef.FavorBank(Suit.Arcane), 0, 3, None),
        DistributeSlot(DecisionOptionRef.FavorBank(Suit.Nomad), 0, 3, None)),
      minTotal = 0, maxTotal = 3, heading = Some("Place force"),
      confirmLabel = "Place")))
    val query = projectorFor(tree).project(context).flatMap(_.query)
      .getOrElse(fail("a parked ranged distribution must project"))
    assertEquals(query.minTotal -> query.maxTotal, Some(0) -> Some(3))
  }
```

In `ProjectionProtocolSuite.scala` change `total = Some(6))` (line 159) to `minTotal = Some(6), maxTotal = Some(6))` and add after that test:

```scala
  test("a ranged distribute query round-trips both totals") {
    def bank(id: String) = DecisionOptionProjection("favor-bank", id, id)
    val distribute = DecisionQueryProjection("distribute", Vector.empty,
      heading = Some("Place force"), confirmLabel = Some("Place"),
      slots = Vector(DecisionSlotProjection(bank("arcane"), 0, 3, None),
        DecisionSlotProjection(bank("nomad"), 0, 3, None)),
      minTotal = Some(0), maxTotal = Some(3))
    val carrying = projection.copy(walkerDecision =
      projection.walkerDecision.map(_.copy(query = Some(distribute))))
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(carrying)),
      Right(carrying))
  }
```

In `DistributePanelRenderSuite.scala` change `total = Some(2))` (line 18) to `minTotal = Some(2), maxTotal = Some(2))` and add before the closing brace:

```scala
  private val rangedQuery = query.copy(slots = Vector(
    DecisionSlotState(bank("arcane", "Arcane"), 0, 3, None),
    DecisionSlotState(bank("nomad", "Nomad"), 0, 3, None)),
    minTotal = Some(1), maxTotal = Some(3))
  private val rangedParked = parked.copy(query = Some(rangedQuery))

  test("a range shows its minimum and confirms anywhere inside it") {
    val ui = new RecordingView("game", "red")
    ui.currentWalkerDistribution = WalkerDistributeDraft.reconcile(None,
      BoardSelectionContext("game", "red", 9), Some(rangedParked))
    val ranged = projection.copy(walkerDecision = Some(rangedParked))
    def draw(): dom.Element = {
      val panel = dom.document.createElement("div")
      DistributePanelRenderer.render(ranged, presentation, true, panel, ui)
      panel
    }
    assertEquals(one(draw(), ".distribute-minimum").textContent,
      "At least 1 must be placed")
    assert(one(draw(), ".distribute-confirm").asInstanceOf[dom.html.Button].disabled)
    click(one(draw(), """[data-option-id="favor-bank:arcane"] .distribute-increment"""))
    assert(!one(draw(), ".distribute-confirm").asInstanceOf[dom.html.Button].disabled)
    assertEquals(one(draw(), ".distribute-remaining").textContent, "Remaining: 2")
  }
```

In `DistributeDecisionStateSuite.scala` change line 12 to `DistributeDecisionState.opened(slots, 5, 5, None)` and line 21 to `DistributeDecisionState.opened(slots, 5, 5,` (keep its `Some(Vector(2, 2, 1)))` continuation), then add before the closing brace:

```scala
  test("a range confirms at any allocation between its minimum and maximum") {
    val two = Vector(DistributeSlotBounds("a", 0, 3), DistributeSlotBounds("b", 0, 3))
    val open = DistributeDecisionState.opened(two, 1, 3, None)
    assert(!open.canConfirm)
    val one = open.increment("a")
    assert(one.canConfirm)
    assertEquals(one.remaining, 2)
    val full = one.fill("b")
    assertEquals(full.allocated, 3)
    assert(full.canConfirm)
    assertEquals(full.increment("a"), full)
  }
```

- [ ] **Step 2: Run to confirm it fails to compile**

Run: `./sbtw "Test/compile"`
Expected: FAIL, `Distribute.exactly` and `minTotal` are not defined.

- [ ] **Step 3: Change the model**

In `Decisions.scala` replace the `Distribute` case class and its doc comment (lines 267-287) with:

```scala
  /** Spread an amount across the slots, each within its own bounds, so that
    * the amounts sum to between `minTotal` and `maxTotal` inclusive. An exact
    * distribution sets both to one value ([[Distribute.exactly]]).
    *
    * Both labels are required. `heading` keeps the `Option` type that
    * [[DecisionQuery.heading]] declares, and `DecisionQueries.wellFormed`
    * rejects `None` or blank. A distribution has a confirm step, so it
    * always names its confirm control.
    */
  final case class Distribute(slots: Vector[DistributeSlot], minTotal: Int,
      maxTotal: Int, heading: Option[String], confirmLabel: String)
      extends DecisionQuery
  object Distribute {
    /** A distribution whose amounts must sum to exactly `total`. */
    def exactly(slots: Vector[DistributeSlot], total: Int,
        heading: Option[String], confirmLabel: String): Distribute =
      Distribute(slots, total, total, heading, confirmLabel)
  }
```

- [ ] **Step 4: Change the validator**

In `DecisionQueries.scala` replace the whole `DecisionQuery.Distribute` case of `wellFormed` with:

```scala
    case DecisionQuery.Distribute(slots, minTotal, maxTotal, heading,
        confirmLabel) =>
      val refs = slots.map(_.ref)
      val minimums = slots.map(_.minimum.toLong).sum
      val maximums = slots.map(_.maximum.toLong).sum
      val variableSlots = slots.count(s => s.minimum < s.maximum)
      val suggested = slots.flatMap(_.suggested)
      for {
        _ <- require(heading.exists(_.trim.nonEmpty), decisionId,
          "declares no heading")
        _ <- require(confirmLabel.trim.nonEmpty, decisionId,
          "declares a blank confirm label")
        _ <- require(slots.size >= 2, decisionId,
          "declares fewer than two slots")
        _ <- require(refs.distinct.size == refs.size, decisionId,
          "declares duplicate options")
        _ <- refs.find(DecisionOption.forRef(_).isEmpty) match {
          case Some(ref) => reject(decisionId,
            s"declares slot ${label(ref)}, which has no presentable option")
          case None => Right(())
        }
        _ <- slots.find(s => s.minimum < 0 || s.minimum > s.maximum) match {
          case Some(s) => reject(decisionId, s"declares slot ${label(s.ref)} " +
            s"with bounds ${s.minimum}..${s.maximum}")
          case None => Right(())
        }
        _ <- require(minTotal >= 0 && minTotal <= maxTotal &&
          minimums <= maxTotal && minTotal <= maximums, decisionId,
          "declares a total no answer can meet")
        _ <- require(minimums != maxTotal, decisionId, "declares minimums " +
          "that already make its total, leaving nothing to decide")
        _ <- require(maximums != minTotal, decisionId, "declares maximums " +
          "that already make its total, leaving nothing to decide")
        _ <- require(variableSlots >= 2, decisionId,
          "declares fewer than two variable slots, leaving nothing to decide")
        _ <- require(suggested.isEmpty || suggested.size == slots.size,
          decisionId, "suggests amounts for some slots but not all")
        _ <- if (suggested.isEmpty) Right(())
          else acceptsDistribution(decisionId, slots, minTotal, maxTotal,
              slots.zip(suggested).map { case (s, n) => DistributeAmount(s.ref, n) })
            .fold(_ => reject(decisionId,
              "suggests a distribution it would not accept"), Right(_))
      } yield ()
```

Replace the `Distribute` case of `accepts` and the whole `acceptsDistribution` method with:

```scala
    case DecisionQuery.Distribute(slots, minTotal, maxTotal, _, _) => answer match {
      case DecisionAnswer.DistributeAnswer(amounts) =>
        acceptsDistribution(decisionId, slots, minTotal, maxTotal, amounts)
      case _ =>
        reject(decisionId, "expects a distribution answer")
    }
```

```scala
  private def acceptsDistribution(decisionId: String,
      slots: Vector[DistributeSlot], minTotal: Int, maxTotal: Int,
      amounts: Vector[DistributeAmount]): Either[OathViolation, Unit] = {
    val declared = slots.map(_.ref)
    val named = amounts.map(_.ref)
    val sum = amounts.map(_.amount.toLong).sum
    for {
      _ <- require(named.forall(declared.contains), decisionId,
        "does not offer a distributed option")
      _ <- require(named.distinct.size == named.size, decisionId,
        "distributes to an option more than once")
      _ <- require(declared.forall(named.contains), decisionId,
        "leaves an option undistributed")
      _ <- slots.find(s => amounts.find(_.ref == s.ref)
          .exists(a => a.amount < s.minimum || a.amount > s.maximum)) match {
        case Some(s) => reject(decisionId, s"distributes to ${label(s.ref)} " +
          s"outside ${s.minimum}..${s.maximum}")
        case None => Right(())
      }
      _ <- require(sum >= minTotal.toLong && sum <= maxTotal.toLong,
        decisionId,
        if (minTotal == maxTotal)
          s"distributes an amount other than its total of $maxTotal"
        else s"distributes an amount outside $minTotal..$maxTotal")
    } yield ()
  }
```

- [ ] **Step 5: Change the callers**

`LeagueTreatyContribution.scala:82`: change `DecisionQuery.Distribute(` to `DecisionQuery.Distribute.exactly(`. Its named arguments `total`, `heading` and `confirmLabel` stay valid.

`WalkerDecisionProjector.scala:239-249`: replace the `Distribute` case with:

```scala
      case DecisionQuery.Distribute(slots, minTotal, maxTotal, heading,
          confirmLabel) =>
        described(slots.flatMap(slot => DecisionOption.forRef(slot.ref)))
          .filter(_.size == slots.size).map(options =>
            DecisionQueryProjection("distribute", Vector.empty,
              heading = heading, confirmLabel = Some(confirmLabel),
              slots = slots.zip(options).map { case (slot, option) =>
                DecisionSlotProjection(option, slot.minimum, slot.maximum,
                  slot.suggested) },
              minTotal = Some(minTotal), maxTotal = Some(maxTotal)))
```

`ActionProjectionDtos.scala`: in `DecisionQueryProjection` replace `total: Option[Int] = None,` with `minTotal: Option[Int] = None,` and `maxTotal: Option[Int] = None,`. In the doc comments that name `total` (lines 114 and 212 of the codec) say "totals".

`ActionProjectionCodec.scala`: in `encodeDecisionQuery` replace `"total" -> intOption(value.total),` with

```scala
    "minTotal" -> intOption(value.minTotal),
    "maxTotal" -> intOption(value.maxTotal),
```
in `decodeDecisionQuery` replace `"slots", "total", "minimum"` in the `exact` key set with `"slots", "minTotal", "maxTotal", "minimum"`, replace `total <- optionalInt(value, "total", path)` with

```scala
    minTotal <- optionalInt(value, "minTotal", path)
    maxTotal <- optionalInt(value, "maxTotal", path)
```
and end the constructor call with `confirmLabel, slots, minTotal, maxTotal, minimum, maximum, deal)`.

`DistributeDecisionState.scala`: replace the class head through `remaining` and `canConfirm`:

```scala
private[frontend] final case class DistributeDecisionState(
    slots: Vector[DistributeSlotBounds],
    minTotal: Int,
    maxTotal: Int,
    amounts: Map[String, Int]
) {
  def amount(item: String): Int = amounts.getOrElse(item, 0)

  def allocated: Int = slots.map(slot => amount(slot.item)).sum

  def remaining: Int = maxTotal - allocated
```
and `def canConfirm: Boolean = allocated >= minTotal && allocated <= maxTotal`. Replace `opened` with:

```scala
  def opened(slots: Vector[DistributeSlotBounds], minTotal: Int, maxTotal: Int,
      suggested: Option[Vector[Int]]): DistributeDecisionState =
    DistributeDecisionState(slots, minTotal, maxTotal, slots.map(_.item).zip(
      suggested.filter(_.size == slots.size)
        .getOrElse(slots.map(_.minimum))).toMap)
```
In `WalkerDistributeDraft.reconcile` replace `query.total.getOrElse(0)` with `query.minTotal.getOrElse(0), query.maxTotal.getOrElse(0)`. Change the class doc "`canConfirm` is exactly `remaining == 0`" to "`canConfirm` is exactly `minTotal <= allocated <= maxTotal`".

`DistributePanelRenderer.scala`: after the `distribute-remaining` paragraph (line 27-28) add:

```scala
        query.minTotal.filter(min => query.maxTotal.exists(min < _)).foreach(min =>
          panel.appendChild(text("p", "distribute-minimum",
            s"At least $min must be placed")))
```

- [ ] **Step 6: Run and commit**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.DecisionQuerySuite oathdigital.gameplay.walker.WalkerPreviewSuite oathdigital.application.WalkerDecisionProjectorSuite oathdigital.protocol.ProjectionProtocolSuite oathdigital.gameplay.RestWalkerSuite" "frontend/testOnly oathdigital.frontend.DistributeDecisionStateSuite oathdigital.frontend.DistributePanelRenderSuite oathdigital.frontend.ServerModeUiSuite"`
Expected: PASS. `RestWalkerSuite` proves League Treaty (an exact distribution) is unchanged.

```bash
git add -A src shared frontend
git commit -m "feat(walker): give Distribute a total range

Distribute takes minTotal and maxTotal; Distribute.exactly keeps the old
meaning for League Treaty. wellFormed keeps the forced-shape rules for a
range, the answer's sum must land inside it, and the projection, wire and
frontend stepper carry both totals.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```


---

### Task 3: Attack roll outcomes and an executed `ModifyRollOutcome`

The walker derives a roll outcome only from defense faces, and `ModifyRollOutcome` is defined but silently ignored by the executor (its catch-all `case (result, _) => result`). Campaign needs both. The roll code also moves out of `ProcedureWalker.scala` (705 lines, cap 800) into its own file so Task 4 has room.

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/walker/WalkerRolls.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` (imports; `parkedRoll` at 205-208; `poolCount` at 265; `recordRoll` at 644-680; `writeRollOutcome` at 682-705; the class doc at 135-150), `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala:52-70`, `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala:78-99`, `src/main/scala/oathdigital/gameplay/operations/OperationStateMutation.scala:311`
- Test: create `src/test/scala/oathdigital/gameplay/walker/WalkerRollsSuite.scala`, `src/test/scala/oathdigital/gameplay/operations/ModifyRollOutcomeSuite.scala`; modify `src/test/scala/oathdigital/gameplay/ProcedureWalkerSuite.scala:646-655`, `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`, `src/test/scala/oathdigital/gameplay/WalkerRecordedOpsReducer.scala:37-56`

**Interfaces:**
- Produces: `WalkerRolls.poolCount(state, pool): Int`; `WalkerRolls.outcomeFor(roll: Roll, state: ReadyGame, faces: Vector[DieFace]): Either[OathViolation, RollOutcome]` (live walk); `WalkerRolls.outcomeForRecorded(state, pool, faces): Either[OathViolation, RollOutcome]` (replay); `WalkerRolls.write(ready, outcome): ReadyGame` (accumulating merge). An `Attack` roll derives `skulls = AttackDieFace.skulls(faces)` and `score = AttackDieFace.score(faces)`; a `Defense` roll derives `skulls = 0` and `score = DefenseDieFace.score(faces)`. `ModifyRollOutcome(pool, skulls, score)` writes the given fields into `rollOutcomes(pool)`, creating an empty outcome (`count = 0`, no faces) when the pool has none.
- Consumes: nothing from earlier tasks.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/walker/WalkerRollsSuite.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.model._

/** Roll outcome mechanics: what a face vector scores, what a recorded roll may
  * claim, and how repeated rolls of one pool accumulate.
  */
class WalkerRollsSuite extends munit.FunSuite {
  private val attackPool = PoolKey("campaign.attack")
  private val defensePool = PoolKey("campaign.defense")
  private val attackRoll = Roll(attackPool, DiceSpec(DiceKind.Attack))
  private val defenseRoll = Roll(defensePool, DiceSpec(DiceKind.Defense))

  private def withPools(pools: (PoolKey, Int)*): ReadyGame =
    TestGameFixtures.ready.updateCurrent(current => current.copy(rollPools =
      pools.map { case (pool, count) => pool -> DicePoolState(count) }.toMap))

  private val attackFaces: Vector[DieFace] = Vector(AttackDieFace.TwoSwordsSkull,
    AttackDieFace.OneSword, AttackDieFace.HollowSword, AttackDieFace.HollowSword)

  test("an attack roll counts skulls and scores swords") {
    val state = withPools(attackPool -> 4)
    assertEquals(WalkerRolls.outcomeFor(attackRoll, state, attackFaces),
      Right(RollOutcome(attackPool, 4, attackFaces, skulls = 1, score = 4)))
  }

  test("a defense roll scores shields and has no skulls") {
    val faces: Vector[DieFace] = Vector(DefenseDieFace.OneShield,
      DefenseDieFace.Doubler)
    val state = withPools(defensePool -> 2)
    assertEquals(WalkerRolls.outcomeFor(defenseRoll, state, faces),
      Right(RollOutcome(defensePool, 2, faces, skulls = 0,
        score = DefenseDieFace.score(Vector(DefenseDieFace.OneShield,
          DefenseDieFace.Doubler)))))
  }

  test("a roll must match its pool count and its die kind") {
    val state = withPools(attackPool -> 4, defensePool -> 2)
    assertEquals(WalkerRolls.outcomeFor(attackRoll, state, attackFaces.init),
      Left(OathViolation.InvalidEventOrder(
        s"rolled 3 dice for pool $attackPool but pool count is 4")))
    assertEquals(WalkerRolls.outcomeFor(attackRoll, state,
      Vector.fill(4)(DefenseDieFace.Blank)),
      Left(OathViolation.InvalidEventOrder(
        s"attack roll for pool $attackPool received a non-attack die face")))
    assertEquals(WalkerRolls.outcomeFor(defenseRoll, state,
      Vector(AttackDieFace.OneSword, AttackDieFace.OneSword)),
      Left(OathViolation.InvalidEventOrder(
        s"defense roll for pool $defensePool received a non-defense die face")))
  }

  test("a recorded roll needs its pool, its count and one family of faces") {
    val state = withPools(attackPool -> 4)
    assertEquals(WalkerRolls.outcomeForRecorded(state, attackPool, attackFaces),
      Right(RollOutcome(attackPool, 4, attackFaces, skulls = 1, score = 4)))
    assertEquals(WalkerRolls.outcomeForRecorded(state, defensePool,
      Vector(DefenseDieFace.Blank)),
      Left(OathViolation.InvalidEventOrder(
        s"recorded roll references missing pool ${defensePool.value}")))
    assertEquals(WalkerRolls.outcomeForRecorded(state, attackPool,
      attackFaces.init), Left(OathViolation.InvalidEventOrder(
        "recorded roll has 3 faces but pool count is 4")))
    assertEquals(WalkerRolls.outcomeForRecorded(state, attackPool,
      Vector[DieFace](AttackDieFace.OneSword, AttackDieFace.OneSword,
        DefenseDieFace.Blank, DefenseDieFace.Blank)),
      Left(OathViolation.InvalidEventOrder(
        "recorded roll mixes attack and defense faces")))
  }

  test("two attack rolls of one pool accumulate faces, skulls and a re-scored total") {
    val first: Vector[DieFace] = Vector(AttackDieFace.HollowSword,
      AttackDieFace.TwoSwordsSkull)
    val second: Vector[DieFace] = Vector(AttackDieFace.HollowSword,
      AttackDieFace.OneSword)
    val one = WalkerRolls.write(withPools(attackPool -> 2), RollOutcome(
      attackPool, 2, first, skulls = 1, score = 2))
    val two = WalkerRolls.write(one, RollOutcome(attackPool, 2, second,
      skulls = 0, score = 1))
    assertEquals(two.game.current.rollOutcomes(attackPool), RollOutcome(
      attackPool, 4, first ++ second, skulls = 1,
      score = AttackDieFace.score((first ++ second).collect {
        case face: AttackDieFace => face })))
  }
}
```

Create `src/test/scala/oathdigital/gameplay/operations/ModifyRollOutcomeSuite.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.model._

class ModifyRollOutcomeSuite extends munit.FunSuite {
  private val pool = PoolKey("campaign.attack")
  private val faces: Vector[DieFace] = Vector(AttackDieFace.TwoSwordsSkull,
    AttackDieFace.OneSword)
  private val rolled = TestGameFixtures.ready.updateCurrent(current =>
    current.copy(rollOutcomes = Map(pool -> RollOutcome(pool, 2, faces,
      skulls = 1, score = 3))))

  private def run(ready: ReadyGame, operation: CoreOperation) =
    new OperationExecutor().executeAll(ready, Vector(operation))
      .toOption.get.game.current.rollOutcomes

  test("modifying an outcome rewrites only the fields it names") {
    assertEquals(run(rolled, ModifyRollOutcome(pool, Some(0), None))(pool),
      RollOutcome(pool, 2, faces, skulls = 0, score = 3))
    assertEquals(run(rolled, ModifyRollOutcome(pool, None, Some(1)))(pool),
      RollOutcome(pool, 2, faces, skulls = 1, score = 1))
    assertEquals(run(rolled, ModifyRollOutcome(pool, Some(0), Some(0)))(pool),
      RollOutcome(pool, 2, faces, skulls = 0, score = 0))
  }

  test("modifying a pool that never rolled creates an empty outcome with the fields") {
    val outcomes = run(TestGameFixtures.ready,
      ModifyRollOutcome(pool, Some(0), Some(5)))
    assertEquals(outcomes(pool),
      RollOutcome(pool, 0, Vector.empty, skulls = 0, score = 5))
  }

  test("modifying one pool leaves every other pool alone") {
    val other = PoolKey("campaign.defense")
    val both = rolled.updateCurrent(current => current.copy(rollOutcomes =
      current.rollOutcomes.updated(other, RollOutcome(other, 1,
        Vector(DefenseDieFace.OneShield), 0, 1))))
    val outcomes = run(both, ModifyRollOutcome(pool, None, Some(9)))
    assertEquals(outcomes(other).score, 1)
    assertEquals(outcomes(pool).score, 9)
  }
}
```

In `ProcedureWalkerSuite.scala` replace the test `"roll() rejects an Attack-kind roll in this defense-only slice"` (lines 646-655) with:

```scala
  test("roll() accepts an Attack roll and derives skulls and score from its faces") {
    val attackPool = PoolKey("campaign.attack")
    val tree: Operation = Sequence(ModifyDicePool(attackPool, 4),
      Roll(attackPool, DiceSpec(DiceKind.Attack)))
    val faces: Vector[DieFace] = Vector(AttackDieFace.TwoSwordsSkull,
      AttackDieFace.OneSword, AttackDieFace.HollowSword, AttackDieFace.HollowSword)
    val (pending, parkedState) = parkAtRoll(tree)
    ProcedureWalker.roll(parkedState, tree, pending, faces, noPowers) match {
      case Right(WalkerOutcome.Finished(finalState, events)) =>
        assertEquals(finalState.game.current.rollOutcomes(attackPool),
          RollOutcome(attackPool, 4, faces, skulls = 1, score = 4))
        assertEquals(events.head.asInstanceOf[WalkerStepRecorded].payload,
          RollPayload(attackPool, faces))
      case other => fail(s"expected the attack roll to finish the tree, got $other")
    }
  }

  test("roll() rejects a defense face mixed into an attack roll") {
    val attackPool = PoolKey("campaign.attack")
    val tree: Operation = Sequence(ModifyDicePool(attackPool, 2),
      Roll(attackPool, DiceSpec(DiceKind.Attack)))
    val (pending, parkedState) = parkAtRoll(tree)
    expectRollViolation(parkedState, tree, pending,
      Vector[DieFace](AttackDieFace.HollowSword, DefenseDieFace.Blank),
      "non-attack")
  }
```

In `GameEventWireSuite.scala` add `RollPayload` to the `oathdigital.gameplay.walker.{...}` import and add after the test that ends at line 660 (the walker-ops round trip):

```scala
  test("a walker roll payload round-trips attack and defense faces") {
    val events = Vector[OathEvent](
      WalkerStepRecorded("1", RollPayload(PoolKey("campaign.attack"),
        Vector(AttackDieFace.HollowSword, AttackDieFace.OneSword,
          AttackDieFace.TwoSwordsSkull)), Vector.empty, Vector.empty),
      WalkerStepRecorded("2", RollPayload(PoolKey("campaign.defense"),
        Vector(DefenseDieFace.Doubler, DefenseDieFace.Blank)),
        Vector.empty, Vector.empty))
    val encoded = GameEventWire.encodeStream("walker-rolls", catalogRef,
      events.zipWithIndex.map { case (event, index) =>
        RecordedEvent(index.toLong, event) }).toOption.get
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event),
      events)
  }
```
(`catalogRef` is the suite's existing value; the neighbouring `"walker-ops"` test uses it the same way.)

In `WalkerRecordedOpsReducer.scala` replace the `RollPayload` arm (lines 37-56) so the helper scores attack faces too, keeping its "independent of production" purpose:

```scala
        case RollPayload(pool, faces) =>
          def derive(all: Vector[DieFace]): (Int, Int) = {
            val attack = all.collect { case face: AttackDieFace => face }
            if (attack.nonEmpty)
              (AttackDieFace.skulls(attack), AttackDieFace.score(attack))
            else (0, DefenseDieFace.score(all.collect {
              case face: DefenseDieFace => face }))
          }
          val (skulls, score) = derive(faces)
          val outcome = RollOutcome(pool, faces.size, faces, skulls, score)
          val accumulated =
            current.game.current.rollOutcomes.get(pool).fold(outcome) {
              previous =>
                val accumulatedFaces = previous.faces ++ outcome.faces
                RollOutcome(pool, previous.count + outcome.count,
                  accumulatedFaces, previous.skulls + outcome.skulls,
                  derive(accumulatedFaces)._2)
            }
          current.updateCurrent(_.copy(rollOutcomes =
              current.game.current.rollOutcomes.updated(pool, accumulated)))
```

- [ ] **Step 2: Run to confirm they fail**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, `WalkerRolls` is not defined.

- [ ] **Step 3: Create `WalkerRolls.scala`**

```scala
package oathdigital.gameplay.walker

import oathdigital.model.{AttackDieFace, DefenseDieFace, DiceKind, DieFace,
  OathViolation, PoolKey, ReadyGame, Roll, RollOutcome}

/** Roll outcome mechanics, split out of [[ProcedureWalker]] (which is close to
  * the project's per-file bound) and shared by the live walk and by
  * [[WalkerReplay]], so a rolled outcome and a replayed one are derived and
  * accumulated by the same code rather than by two implementations agreeing.
  */
private[walker] object WalkerRolls {

  def poolCount(state: ReadyGame, pool: PoolKey): Int =
    state.game.current.rollPools.get(pool).fold(0)(_.count)

  /** Skulls and score of `faces`, all of one family: attack faces score
    * swords and count skulls, defense faces score shields (a Doubler applies
    * across every accumulated roll).
    */
  private def derive(faces: Vector[DieFace]): (Int, Int) = {
    val attack = faces.collect { case face: AttackDieFace => face }
    if (attack.nonEmpty)
      (AttackDieFace.skulls(attack), AttackDieFace.score(attack))
    else (0, DefenseDieFace.score(faces.collect {
      case face: DefenseDieFace => face
    }))
  }

  private def invalid(detail: String) =
    OathViolation.InvalidEventOrder(detail)

  /** The outcome of rolling `faces` at `roll`: the face count must equal the
    * pool's count and every face must belong to the roll's die kind.
    */
  def outcomeFor(roll: Roll, state: ReadyGame, faces: Vector[DieFace])
      : Either[OathViolation, RollOutcome] = {
    val pool = roll.pool
    val count = poolCount(state, pool)
    for {
      _ <- Either.cond(faces.size == count, (), invalid(
        s"rolled ${faces.size} dice for pool $pool but pool count is $count"))
      _ <- roll.dice.die match {
        case DiceKind.Defense => Either.cond(
          faces.forall(_.isInstanceOf[DefenseDieFace]), (), invalid(
            s"defense roll for pool $pool received a non-defense die face"))
        case DiceKind.Attack => Either.cond(
          faces.forall(_.isInstanceOf[AttackDieFace]), (), invalid(
            s"attack roll for pool $pool received a non-attack die face"))
      }
    } yield {
      val (skulls, score) = derive(faces)
      RollOutcome(pool, count, faces, skulls, score)
    }
  }

  /** The outcome a recorded roll claims. Replay has no tree, so it cannot know
    * the die kind: it checks the pool and its count, and that the faces are
    * one family.
    */
  def outcomeForRecorded(state: ReadyGame, pool: PoolKey,
      faces: Vector[DieFace]): Either[OathViolation, RollOutcome] = for {
    count <- state.game.current.rollPools.get(pool).map(_.count).toRight(
      invalid(s"recorded roll references missing pool ${pool.value}"))
    _ <- Either.cond(faces.size == count, (), invalid(
      s"recorded roll has ${faces.size} faces but pool count is $count"))
    _ <- Either.cond(faces.forall(_.isInstanceOf[DefenseDieFace]) ||
      faces.forall(_.isInstanceOf[AttackDieFace]), (), invalid(
      "recorded roll mixes attack and defense faces"))
  } yield {
    val (skulls, score) = derive(faces)
    RollOutcome(pool, count, faces, skulls, score)
  }

  /** Merges `outcome` into the pool's accumulated entry: repeated rolls of one
    * pool accumulate faces, count and skulls, and the score is re-derived from
    * every accumulated face because each Doubler multiplies shields from every
    * roll, not only its own.
    */
  def write(ready: ReadyGame, outcome: RollOutcome): ReadyGame = {
    val current = ready.game.current
    val accumulated = current.rollOutcomes.get(outcome.pool).fold(outcome) {
      previous =>
        val faces = previous.faces ++ outcome.faces
        RollOutcome(outcome.pool, previous.count + outcome.count, faces,
          previous.skulls + outcome.skulls, derive(faces)._2)
    }
    ready.copy(game = ready.game.copy(current = current.copy(rollOutcomes =
      current.rollOutcomes.updated(outcome.pool, accumulated))))
  }
}
```

- [ ] **Step 4: Route the walker and replay through it**

In `ProcedureWalker.scala`:
- Delete `private def poolCount` (line 265) and change `parkedRoll` (205-208) to `case roll: Roll => (roll.pool, WalkerRolls.poolCount(state, roll.pool))`.
- Replace `recordRoll` (644-680) with:

```scala
  /** Records one roll step: the outcome is derived and validated by
    * [[WalkerRolls.outcomeFor]], merged into `ctx.state`, and the step carries
    * a [[RollPayload]] with no ops (the outcome is a state write).
    */
  private def recordRoll(roll: Roll, ctx: WalkCtx, path: Vector[String],
      faces: Vector[DieFace], contributions: Vector[PowerId])
      : Either[OathViolation, WalkCtx] =
    WalkerRolls.outcomeFor(roll, ctx.state, faces).map { outcome =>
      val nodeId =
        if (path.isEmpty) leafLabel(roll) else path.mkString(".")
      ctx.copy(
        state = WalkerRolls.write(ctx.state, outcome),
        events = ctx.events :+ WalkerStepRecorded(
          nodeId = nodeId, payload = RollPayload(roll.pool, faces),
          ops = Vector.empty, contributions = contributions))
    }
```
- Delete `writeRollOutcome` (682-705) with its two doc comments.
- In the `roll` method's doc (lines 135-150) replace "Only `DiceKind.Defense` rolls are legal this slice: ..." with "A face count differing from the pool count, or a face outside the roll's die kind, rejects with `OathViolation.InvalidEventOrder`."
- Remove `DefenseDieFace`, `DiceKind` and `RollOutcome` from the `oathdigital.model.{...}` import if the compiler reports them unused.

In `WalkerReplay.scala` replace the whole `RollPayload` arm (lines 52-70) with:

```scala
      case step @ WalkerStepRecorded(_, RollPayload(pool, faces), ops, _) =>
        for {
          _ <- validateParkedStep(step)
          _ <- Either.cond(ops.isEmpty, (), OathViolation.InvalidEventOrder(
            "recorded RollPayload must not contain operations"))
          outcome <- WalkerRolls.outcomeForRecorded(ready, pool, faces)
        } yield WalkerRolls.write(ready, outcome)
```
and drop `DefenseDieFace` and `RollOutcome` from its import.

In `WalkerEventCodec.scala` replace the `RollPayload` encode arm and the `"roll"` decode arm:

```scala
      case RollPayload(pool, faces) => ujson.Obj(
        "kind" -> "roll", "pool" -> pool.value,
        "faces" -> ujson.Arr.from(faces.map {
          case face: DefenseDieFace => ujson.Str(encodeDefenseFace(face))
          case face: AttackDieFace => ujson.Str(encodeAttackFace(face))
          case other => throw new IllegalArgumentException(
            s"unsupported walker die face $other")
        }))
```
```scala
      case "roll" => traverse(value("faces").arr.toVector)(face =>
        decodeDieFace(face.str, s"$path.faces"))
        .map(faces => RollPayload(PoolKey(value("pool").str), faces))
```
and add beside the other private decoders:

```scala
  private def decodeDieFace(value: String, path: String)
      : Either[WireError, DieFace] = value match {
    case "hollow-sword" | "one-sword" | "two-swords-skull" =>
      decodeAttackFace(value, path)
    case _ => decodeDefenseFace(value, path)
  }
```
(`AttackDieFace`, `DieFace` and `PoolKey` come from `oathdigital.model._`; add an import if the file names them individually.)

In `OperationStateMutation.scala` add a case to `applyNonMoveLeaves` next to `ModifyDicePool` (line 311):

```scala
      case (result, ModifyRollOutcome(pool, skulls, score)) =>
        result.map(modifyRollOutcome(_, pool, skulls, score))
```
and the method beside `adjustDicePool`:

```scala
  /** An upsert: a pool that never rolled starts from an empty outcome, so a
    * step that has no roll (a pool of zero dice is never rolled) can still
    * record a result. Fields left `None` are unchanged.
    */
  private def modifyRollOutcome(ready: ReadyGame, pool: PoolKey,
      skulls: Option[Int], score: Option[Int]): ReadyGame = {
    val outcomes = ready.game.current.rollOutcomes
    val base = outcomes.getOrElse(pool, RollOutcome(pool, 0, Vector.empty, 0, 0))
    ready.updateCurrent(current => current.copy(rollOutcomes = outcomes.updated(
      pool, base.copy(skulls = skulls.getOrElse(base.skulls),
        score = score.getOrElse(base.score)))))
  }
```

- [ ] **Step 5: Run and commit**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.WalkerRollsSuite oathdigital.gameplay.operations.ModifyRollOutcomeSuite oathdigital.gameplay.ProcedureWalkerSuite oathdigital.serialization.GameEventWireSuite oathdigital.gameplay.RecoverProcedureSuite oathdigital.gameplay.WalkerReplayDriftSuite oathdigital.gameplay.OathRulesWalkerPowerSuite" && wc -l src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`
Expected: PASS, and `ProcedureWalker.scala` is about 630 lines.

```bash
git add -A src
git commit -m "feat(walker): derive attack roll outcomes and execute ModifyRollOutcome

The walker derived an outcome only from defense faces, and ModifyRollOutcome
was silently ignored by the executor. Roll outcome mechanics move to
WalkerRolls, shared by the live walk and by replay; an attack roll scores
swords and counts skulls; the RollPayload codec carries attack faces; and
ModifyRollOutcome now upserts the named fields.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 4: Automatic `Roll` nodes and a dice source

A `Roll` currently parks and waits for a `RollWalker` command carrying faces. Campaign's rolls carry no decision, so a `Roll` can opt in to `RollMode.Automatic`: the walker asks a per-command dice source for faces, records a `RollPayload` step and keeps walking. Recover stays `Parked`. Spec: "all rolls should eventually become automatic" is deferred and recorded in Task 19.

**Files:**
- Modify: `src/main/scala/oathdigital/model/DiceSpec.scala` (add `RollMode`), `src/main/scala/oathdigital/model/CoreOperations.scala:532` (`Roll`), `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala:51` (`RollPayload`), `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` (`WalkCtx`, `advance`, `roll`, `resolve`, `runLeaf`, `parkedRoll`, `recordRoll`, class doc), `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala` (the `RollPayload` arm), `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala:54,75,88,128`, `src/main/scala/oathdigital/gameplay/OathRules.scala:33-41`, `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala:22-40,71,115,235,263`, `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala` (the `RollPayload` arms), `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala:154,403` (the `Roll` arms), `src/main/scala/oathdigital/application/GameRandomPorts.scala`, `src/main/scala/oathdigital/application/GameApplicationService.scala:84-88`
- Create: `src/main/scala/oathdigital/gameplay/walker/WalkerDice.scala`
- Test: create `src/test/scala/oathdigital/gameplay/AutomaticRollSuite.scala`, `src/test/scala/oathdigital/application/WalkerDiceAdapterSuite.scala`; modify `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`, `src/test/scala/oathdigital/gameplay/WalkerRecordedOpsReducer.scala`

**Interfaces:**
- Produces: `RollMode.{Parked, Automatic}`; `Roll(pool, dice, mode = RollMode.Parked, window = None)`; `RollPayload(pool, faces, automatic: Boolean = false)`; `trait WalkerDice { def roll(kind: DiceKind, count: Int): Either[OathViolation, Vector[DieFace]] }` with `WalkerDice.unavailable` (fails with a typed violation) and `WalkerDice.placeholder` (fixed faces, for simulations); `ProcedureWalker.advance/resolve/roll(..., powers, dice: WalkerDice = WalkerDice.unavailable)`; `OathRules(..., walkerDice: WalkerDice = WalkerDice.unavailable)`; `CampaignDicePort.walkerDice(port): WalkerDice`.
- Rules: an `Automatic` roll on a pool of zero dice does nothing and records nothing. An `Automatic` roll's `RollPayload` has `automatic = true`, and replay accepts it without a durable park at its node (a parked roll's payload still requires one). Encoding writes `"automatic": true` only when set, so every existing journal decodes unchanged. `parkedRoll` reports only `Parked` rolls.
- Why the `dice` default is `unavailable` and not required: the walker entry points state `powers` explicitly so a suite cannot walk unpowered by accident. A dice source defaults to one that *fails loudly*, so a test that reaches an automatic roll without supplying dice gets a typed violation rather than a silent roll, and the many existing call sites need no change.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/AutomaticRollSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.walker.{ProcedureWalker, RollPayload, WalkerDice,
  WalkerOutcome, WalkerPowers, WalkerSimulation, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

/** Automatic rolls: a `Roll` that takes its faces from the walker's dice
  * source and keeps walking, and the rules around it.
  */
class AutomaticRollSuite extends munit.FunSuite {
  private val noPowers = WalkerPowers.empty
  private val pool = PoolKey("campaign.attack")
  private val faces: Vector[DieFace] = Vector(AttackDieFace.TwoSwordsSkull,
    AttackDieFace.OneSword, AttackDieFace.HollowSword)
  private val fixed: WalkerDice = (kind, count) => kind match {
    case DiceKind.Attack => Right(faces.take(count))
    case DiceKind.Defense => Right(Vector.fill(count)(DefenseDieFace.OneShield))
  }
  private val automatic = Roll(pool, DiceSpec(DiceKind.Attack), RollMode.Automatic)
  private val afterPool = PoolKey("after")

  private def tree(steps: Operation*): Operation = Sequence(steps.toVector)

  test("an automatic roll rolls from the source and walks on without parking") {
    val walk = tree(ModifyDicePool(pool, 3), automatic,
      ModifyDicePool(afterPool, 1))
    ProcedureWalker.advance(ready, walk, None, noPowers, fixed) match {
      case Right(WalkerOutcome.Finished(done, events)) =>
        assertEquals(done.game.current.rollOutcomes(pool),
          RollOutcome(pool, 3, faces, skulls = 1, score = 3))
        val payloads = events.collect { case step: WalkerStepRecorded => step.payload }
        assertEquals(payloads.count(_.isInstanceOf[RollPayload]), 1)
        assert(payloads.contains(RollPayload(pool, faces, automatic = true)))
        assertEquals(events.size, 3)
      case other => fail(s"an automatic roll must not park, got $other")
    }
  }

  test("a pool of zero dice is skipped and records nothing") {
    ProcedureWalker.advance(ready, tree(automatic), None, noPowers, fixed) match {
      case Right(WalkerOutcome.Finished(done, events)) =>
        assertEquals(events, Vector.empty[OathEvent])
        assertEquals(done.game.current.rollOutcomes, Map.empty[PoolKey, RollOutcome])
      case other => fail(s"expected the empty roll to finish, got $other")
    }
  }

  test("without a dice source an automatic roll fails with a typed violation") {
    val walk = tree(ModifyDicePool(pool, 3), automatic)
    assertEquals(ProcedureWalker.advance(ready, walk, None, noPowers),
      Left(OathViolation.InvalidEventOrder(
        "walker has no dice source for an automatic roll")))
  }

  test("a source that returns the wrong number of faces is rejected") {
    val short: WalkerDice = (_, _) => Right(faces.take(1))
    val walk = tree(ModifyDicePool(pool, 3), automatic)
    assertEquals(ProcedureWalker.advance(ready, walk, None, noPowers, short),
      Left(OathViolation.InvalidEventOrder(
        s"rolled 1 dice for pool $pool but pool count is 3")))
  }

  test("a roll left in its default mode still parks") {
    val walk = tree(ModifyDicePool(pool, 2), Roll(pool, DiceSpec(DiceKind.Defense)))
    ProcedureWalker.advance(ready, walk, None, noPowers, fixed) match {
      case Right(WalkerOutcome.Parked(pending, _)) =>
        assertEquals(pending.at, Vector("1"))
      case other => fail(s"a default roll must park, got $other")
    }
  }

  test("parkedRoll reports a parked roll and never an automatic one") {
    val parkedTree = tree(ModifyDicePool(pool, 2), Roll(pool, DiceSpec(DiceKind.Defense)))
    val autoTree = tree(ModifyDicePool(pool, 2), automatic)
    val state = ready.updateCurrent(_.copy(rollPools = Map(pool -> DicePoolState(2))))
    val at = PendingTree(Vector("1"), Vector.empty)
    assertEquals(ProcedureWalker.parkedRoll(state, parkedTree, at, noPowers),
      Some((pool, 2)))
    assertEquals(ProcedureWalker.parkedRoll(state, autoTree, at, noPowers), None)
  }

  test("an automatic roll replays from its recorded payload and rejects a tampered one") {
    val walk = tree(ModifyDicePool(pool, 3), automatic)
    val Right(WalkerOutcome.Finished(done, events)) =
      ProcedureWalker.advance(ready, walk, None, noPowers, fixed): @unchecked
    val recorded = events.collect { case step: WalkerStepRecorded => step }
    def replay(steps: Vector[WalkerStepRecorded]) =
      steps.foldLeft[Either[OathViolation, OathState]](
        Right(OathState.Ready(ready))) {
        case (Right(state), step) => ProcedureWalker.applyRecorded(state, step)
        case (failure, _) => failure
      }
    val Right(OathState.Ready(replayed)) = replay(recorded): @unchecked
    assertEquals(replayed.game.current.rollOutcomes,
      done.game.current.rollOutcomes)
    val tampered = recorded.init :+ recorded.last.copy(payload =
      RollPayload(pool, faces.take(2), automatic = true))
    assertEquals(replay(tampered), Left(OathViolation.InvalidEventOrder(
      "recorded roll has 2 faces but pool count is 3")))
  }

  test("a simulated tree rolls placeholder faces instead of failing") {
    val walk = tree(ModifyDicePool(pool, 2), automatic)
    assert(WalkerSimulation.run(walk, ready, noPowers).isRight)
  }
}
```

Create `src/test/scala/oathdigital/application/WalkerDiceAdapterSuite.scala`:

```scala
package oathdigital.application

import oathdigital.model._

class WalkerDiceAdapterSuite extends munit.FunSuite {
  private val port = new CampaignDicePort {
    def rollAttack(count: Int) = Vector.fill(count)(AttackDieFace.OneSword)
    def rollDefense(count: Int) = Vector.fill(count)(DefenseDieFace.TwoShields)
  }

  test("the adapter routes by die kind and passes the count through") {
    val dice = CampaignDicePort.walkerDice(port)
    assertEquals(dice.roll(DiceKind.Attack, 2),
      Right(Vector(AttackDieFace.OneSword, AttackDieFace.OneSword)))
    assertEquals(dice.roll(DiceKind.Defense, 3),
      Right(Vector.fill(3)(DefenseDieFace.TwoShields)))
    assertEquals(dice.roll(DiceKind.Attack, 0), Right(Vector.empty))
  }
}
```

In `GameEventWireSuite.scala` extend the roll payload test added in Task 3: add a third event `WalkerStepRecorded("3", RollPayload(PoolKey("campaign.attack"), Vector(AttackDieFace.OneSword), automatic = true), Vector.empty, Vector.empty)` to `events`, and after the round-trip assertion add:

```scala
    val steps = ujson.read(encoded).arr.map(_("payload")("payload")).toVector
    assert(!steps(0).obj.contains("automatic"))
    assert(!steps(1).obj.contains("automatic"))
    assert(steps(2)("automatic").bool)
```

In `WalkerRecordedOpsReducer.scala` change the arm head from `case RollPayload(pool, faces) =>` to `case RollPayload(pool, faces, _) =>`.

- [ ] **Step 2: Run to confirm they fail**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, `RollMode`, `WalkerDice` and `RollPayload.automatic` are not defined.

- [ ] **Step 3: Model and payload**

In `DiceSpec.scala` append:

```scala
/** How a `Roll` node gets its faces. */
sealed trait RollMode extends Product with Serializable
object RollMode {
  /** The walker parks and the faces ride a later `RollWalker` command. */
  case object Parked extends RollMode
  /** The walker asks its dice source and keeps walking in the same command. */
  case object Automatic extends RollMode
}
```

In `CoreOperations.scala` replace `Roll`:

```scala
/** Rolls `dice` drawn from `pool`; the pool count comes from state. `Parked`
  * (the default) parks the walker until the faces ride the next command;
  * `Automatic` takes them from the walker's dice source and keeps walking.
  * A `window` lets a power hook the roll.
  */
final case class Roll(pool: PoolKey, dice: DiceSpec,
    mode: RollMode = RollMode.Parked,
    override val window: Option[PowerWindow] = None)
    extends PrimitiveOperation
```

In `WalkerEvents.scala` replace `RollPayload`:

```scala
final case class RollPayload(pool: PoolKey, faces: Vector[DieFace],
    automatic: Boolean = false) extends WalkerStepPayload
```
and add to its doc: "`automatic` is true when the walker rolled the faces itself from its dice source; replay then needs no durable park at the node."

In `WalkerOperationCodec.scala` change the encode arm `case Roll(pool, dice) =>` to `case Roll(pool, dice, _, _) =>` (a `Roll` is never a recorded operation, so its mode is not part of the wire; a decoded `Roll` is `Parked`).

- [ ] **Step 4: The dice source**

Create `WalkerDice.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.model.{AttackDieFace, DefenseDieFace, DiceKind, DieFace,
  OathViolation}

/** Where an automatic `Roll` gets its faces: asked once per roll, with the die
  * kind and the pool's count. The engine never rolls; production supplies the
  * application service's dice port. Replay never asks: it applies the faces
  * recorded in the `RollPayload`.
  */
trait WalkerDice {
  def roll(kind: DiceKind, count: Int): Either[OathViolation, Vector[DieFace]]
}

object WalkerDice {
  /** The default: fails loudly, so a walk that reaches an automatic roll
    * without a source is a typed violation and never a silent roll.
    */
  val unavailable: WalkerDice = (_, _) => Left(OathViolation.InvalidEventOrder(
    "walker has no dice source for an automatic roll"))

  /** Fixed faces, for simulations: a simulation reports what a tree would do,
    * and no rule reads a placeholder face because a start only walks to its
    * first decision.
    */
  val placeholder: WalkerDice = (kind, count) => Right(Vector.fill(count)(
    kind match {
      case DiceKind.Attack => AttackDieFace.HollowSword: DieFace
      case DiceKind.Defense => DefenseDieFace.Blank: DieFace
    }))
}
```

- [ ] **Step 5: The walker**

In `ProcedureWalker.scala`:
- Add `dice: WalkerDice` as the last field of `WalkCtx`, and give `advance`, `roll` and `resolve` a last parameter `dice: WalkerDice = WalkerDice.unavailable`, passed into each `WalkCtx(...)` they build. Add `RollMode` to the `oathdigital.model.{...}` import.
- In `parkedRoll` change the match to `case roll: Roll if roll.mode == RollMode.Parked => (roll.pool, WalkerRolls.poolCount(state, roll.pool))`.
- In `runLeaf`'s `case None =>` branch put the automatic arm before the park arm:

```scala
          case roll: Roll if roll.mode == RollMode.Automatic =>
            runAutomaticRoll(roll, ctx, path, contributions).map(Done(_))
          case _: Decide | _: Roll => Right(Park(path, ctx))
```
- Change `recordRoll` to take `automatic: Boolean` (the `RollResume` arm passes `false`) and build `RollPayload(roll.pool, faces, automatic)`, then add beside it:

```scala
  /** Rolls an `Automatic` node: the faces come from the dice source and the
    * step is recorded like a resumed roll, marked `automatic`. A pool of zero
    * dice is skipped and records nothing: replay needs the pool to exist, and
    * a Campaign with no force and no plans never creates one.
    */
  private def runAutomaticRoll(roll: Roll, ctx: WalkCtx, path: Vector[String],
      contributions: Vector[PowerId]): Either[OathViolation, WalkCtx] = {
    val count = WalkerRolls.poolCount(ctx.state, roll.pool)
    if (count == 0) Right(ctx)
    else ctx.dice.roll(roll.dice.die, count).flatMap(faces =>
      recordRoll(roll, ctx, path, faces, contributions, automatic = true))
  }
```
- In the class doc, change "`Roll` nodes park in `advance` (their faces ride a later command)" to add "(unless the node is `Automatic`, which rolls from the dice source)".

In `WalkerReplay.scala` replace the `RollPayload` arm's head and first check:

```scala
      case step @ WalkerStepRecorded(_, RollPayload(pool, faces, automatic), ops, _) =>
        for {
          _ <- if (automatic) validateStep(step)
            else validateParkedStep(step).map(_ => ())
          _ <- Either.cond(ops.isEmpty, (), OathViolation.InvalidEventOrder(
            "recorded RollPayload must not contain operations"))
          outcome <- WalkerRolls.outcomeForRecorded(ready, pool, faces)
        } yield WalkerRolls.write(ready, outcome)
```

In `WalkerSimulation.scala` pass `dice = WalkerDice.placeholder` to the four walker calls (lines 54, 75, 88 `advance`, and 128 `resolve`).

In `OathRules.scala` add the constructor parameter after `phasePowerCatalog`:

```scala
    protected val phasePowerCatalog: PhasePowers = PhasePowers.empty,
    protected val walkerDice: WalkerDice = WalkerDice.unavailable)
```
(import `oathdigital.gameplay.walker.WalkerDice`). In `OathRulesWalker.scala` declare `protected def walkerDice: WalkerDice` beside the other abstract members and pass it as the last argument of the four calls: `ProcedureWalker.advance(ready, tree, None, powers, walkerDice)` (lines 71 and 115), `ProcedureWalker.resolve(ready, tree, pending, Answered(...), powers, walkerDice)` (235), `ProcedureWalker.roll(ready, tree, pending, faces, powers, walkerDice)` (263).

- [ ] **Step 6: The codec and the application**

In `WalkerEventCodec.scala` replace the roll encode and decode arms:

```scala
      case RollPayload(pool, faces, automatic) =>
        val encoded = ujson.Obj(
          "kind" -> "roll", "pool" -> pool.value,
          "faces" -> ujson.Arr.from(faces.map {
            case face: DefenseDieFace => ujson.Str(encodeDefenseFace(face))
            case face: AttackDieFace => ujson.Str(encodeAttackFace(face))
            case other => throw new IllegalArgumentException(
              s"unsupported walker die face $other")
          }))
        if (automatic) encoded("automatic") = true
        encoded
```
```scala
      case "roll" => for {
        faces <- traverse(value("faces").arr.toVector)(face =>
          decodeDieFace(face.str, s"$path.faces"))
        automatic <- value.obj.get("automatic") match {
          case None => Right(false)
          case Some(ujson.Bool(flag)) => Right(flag)
          case Some(_) => Left(InvalidValue(s"$path.automatic",
            "expected a boolean"))
        }
      } yield RollPayload(PoolKey(value("pool").str), faces, automatic)
```

In `GameRandomPorts.scala` add to `object CampaignDicePort`:

```scala
  /** Feeds the walker's automatic rolls from a Campaign dice port, by die kind. */
  def walkerDice(port: CampaignDicePort): WalkerDice =
    (kind, count) => Right(kind match {
      case DiceKind.Attack => port.rollAttack(count)
      case DiceKind.Defense => port.rollDefense(count)
    })
```
(import `oathdigital.gameplay.walker.WalkerDice`; `oathdigital.model._` is already imported.)

In `GameApplicationService.scala` extend the `new OathRules(...)` call (line 84) with `walkerDice = CampaignDicePort.walkerDice(campaignDicePort)`.

- [ ] **Step 7: Run and commit**

Run: `./sbtw "testOnly oathdigital.gameplay.AutomaticRollSuite oathdigital.application.WalkerDiceAdapterSuite oathdigital.gameplay.ProcedureWalkerSuite oathdigital.gameplay.walker.WalkerRollsSuite oathdigital.serialization.GameEventWireSuite oathdigital.gameplay.RecoverProcedureSuite oathdigital.gameplay.WalkerReplayDriftSuite oathdigital.application.GameApplicationServiceSuite oathdigital.gameplay.BackendArchitectureSuite" && wc -l src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`
Expected: PASS, `ProcedureWalker.scala` under 700 lines. Then the full gate: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`.

```bash
git add -A src
git commit -m "feat(walker): automatic Roll nodes fed by a dice source

A Roll may opt in to RollMode.Automatic: the walker asks a per-command dice
source for faces, records a RollPayload marked automatic and keeps walking.
Replay applies the recorded faces and needs no durable park at the node;
a pool of zero dice is skipped. Parked stays the default, so Recover is
unchanged, and the source defaults to one that fails loudly.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: The `OptionRestriction` contribution

A `Restriction` rejects a whole action, and a `Transform` rewrites operations; neither is the channel for "this option may not be offered". `OptionRestriction` is: a power hooked at a `Decide`'s window forbids options, the walker removes them once, in the window fold, before the query is parked, so projection, answer validation and simulation all see the filtered set. Narrow Pass's Campaign rule (Task 8) is its first user.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala:22-50`, `src/main/scala/oathdigital/gameplay/powerresolver/ContributionCollector.scala`, `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala:31-49,63-100`
- Test: create `src/test/scala/oathdigital/gameplay/OptionRestrictionSuite.scala`

**Interfaces:**
- Produces: `OptionRestriction(fn: (PowerCtx, DecisionOptionRef) => Option[OathViolation]) extends Contribution`. `GatheredContributions` gains `optionRestrictions: Vector[(PowerId, OptionRestriction)] = Vector.empty`. Semantics, applied only when the hooked node is a `Decide`:
  - `ChooseOne`: forbidden options are removed.
  - `ChooseMany`: forbidden options are removed and `min` and `max` are clamped to the remaining count. When `min == 0` and nothing remains, the whole `Decide` is dropped from the folded vector (the decision is not asked).
  - Any other query shape is untouched.
  - At command entry `ProcedureWalker.restrictionViolations` also reports the restriction's own violation for a *required* decision (a `ChooseOne`, or a `ChooseMany` with `min >= 1`) whose every option is forbidden, so a start that cannot be answered is rejected with a typed reason before anything is persisted.
- Consumes: nothing from earlier tasks.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/OptionRestrictionSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction, PowerCtx}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers,
  WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

object OptionRestrictionSuite {
  /** A power whose only contribution is one option restriction at one window. */
  final case class TestOptionRestrictionPower(id: PowerId, hook: PowerWindow,
      fn: (PowerCtx, DecisionOptionRef) => Option[OathViolation])
      extends ContributingPower {
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(hook -> Vector(OptionRestriction(fn)))
  }
}

class OptionRestrictionSuite extends munit.FunSuite {
  import OptionRestrictionSuite.TestOptionRestrictionPower

  private val actor: PlayerId = playerId
  private val window = PowerWindow.ChallengeAmountSelection
  private val blocked = OathViolation.InvalidEventOrder("blocked by test power")
  private def site(id: String) = DecisionOptionRef.Site(SiteId(id))
  private def option(id: String) = DecisionOption.Site(site(id))
  private val all = Vector("a", "b", "c").map(option)
  private val after = ModifyDicePool(PoolKey("after"), 1)

  private def forbid(power: String, sites: String*) = TestOptionRestrictionPower(
    PowerId(power), window, (_, ref) => Option.when(sites.exists(site(_) == ref))(blocked))

  private val optionalPick = Sequence(Vector[Operation](
    Decide("pick", actor, DecisionQuery.ChooseMany(0, 3, all, Some("Pick")),
      window = Some(window)), after))
  private def requiredPick(query: DecisionQuery): Operation = Sequence(
    Vector[Operation](Decide("pick", actor, query, window = Some(window))))

  test("without a restriction the parked query is what the tree declared") {
    val Right(WalkerOutcome.Parked(pending, _)) = ProcedureWalker.advance(ready,
      optionalPick, None, WalkerPowers.empty): @unchecked
    assertEquals(ProcedureWalker.openDecisions(ready, optionalPick, pending,
      WalkerPowers.empty).map(_.query),
      Vector(DecisionQuery.ChooseMany(0, 3, all, Some("Pick"))))
  }

  test("a forbidden option is absent from the parked query and from every answer") {
    val powers = WalkerPowers(Vector(forbid("test.no-b", "b")))
    val Right(WalkerOutcome.Parked(pending, _)) = ProcedureWalker.advance(ready,
      optionalPick, None, powers): @unchecked
    assertEquals(ProcedureWalker.openDecisions(ready, optionalPick, pending,
      powers).map(_.query), Vector(DecisionQuery.ChooseMany(0, 2,
      Vector(option("a"), option("c")), Some("Pick"))))
    assertEquals(ProcedureWalker.resolve(ready, optionalPick, pending, Answered(
      "pick", DecisionAnswer.ChooseManyAnswer(Vector(site("b"))), actor), powers),
      Left(OathViolation.InvalidEventOrder(
        "decision pick does not offer a selected option")))
    assert(ProcedureWalker.resolve(ready, optionalPick, pending, Answered("pick",
      DecisionAnswer.ChooseManyAnswer(Vector(site("a"))), actor), powers).isRight)
  }

  test("restrictions from two powers compose") {
    val powers = WalkerPowers(Vector(forbid("test.no-a", "a"),
      forbid("test.no-b", "b")))
    val Right(WalkerOutcome.Parked(pending, _)) = ProcedureWalker.advance(ready,
      optionalPick, None, powers): @unchecked
    assertEquals(ProcedureWalker.openDecisions(ready, optionalPick, pending,
      powers).map(_.query), Vector(DecisionQuery.ChooseMany(0, 1,
      Vector(option("c")), Some("Pick"))))
  }

  test("an optional decision with nothing left to offer is not asked") {
    val powers = WalkerPowers(Vector(forbid("test.no-all", "a", "b", "c")))
    ProcedureWalker.advance(ready, optionalPick, None, powers) match {
      case Right(WalkerOutcome.Finished(_, events)) =>
        assertEquals(events.collect { case step: WalkerStepRecorded => step.ops },
          Vector(Vector[CoreOperation](after)))
      case other => fail(s"the emptied decision must be dropped, got $other")
    }
  }

  test("a restriction at another window changes nothing") {
    val elsewhere = TestOptionRestrictionPower(PowerId("test.elsewhere"),
      PowerWindow.ChallengeBannerSelection, (_, _) => Some(blocked))
    val Right(WalkerOutcome.Parked(pending, _)) = ProcedureWalker.advance(ready,
      optionalPick, None, WalkerPowers(Vector(elsewhere))): @unchecked
    assertEquals(ProcedureWalker.openDecisions(ready, optionalPick, pending,
      WalkerPowers(Vector(elsewhere))).map(_.query),
      Vector(DecisionQuery.ChooseMany(0, 3, all, Some("Pick"))))
  }

  test("a required decision whose every option is forbidden reports the restriction's violation at command entry") {
    val one = requiredPick(DecisionQuery.ChooseOne(Vector(option("a"), option("b"))))
    val many = requiredPick(DecisionQuery.ChooseMany(1, 2, all.take(2)))
    val all2 = WalkerPowers(Vector(forbid("test.no-ab", "a", "b", "c")))
    assertEquals(ProcedureWalker.restrictionViolations(one, all2, ready, actor),
      Vector(blocked))
    assertEquals(ProcedureWalker.restrictionViolations(many, all2, ready, actor),
      Vector(blocked))
    val some = WalkerPowers(Vector(forbid("test.no-a", "a")))
    assertEquals(ProcedureWalker.restrictionViolations(one, some, ready, actor),
      Vector.empty[OathViolation])
    assertEquals(ProcedureWalker.restrictionViolations(optionalPick, all2, ready,
      actor), Vector.empty[OathViolation])
  }
}
```

- [ ] **Step 2: Run to confirm it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.OptionRestrictionSuite"`
Expected: FAIL to compile, `OptionRestriction` is not defined.

- [ ] **Step 3: The contribution and the collector**

In `ContributingPower.scala` add the model import `DecisionOptionRef`, extend the `Contribution` doc ("The three ways a power may speak at a hooked node") and add after `Restriction`:

```scala
/** Forbids one option of the `Decide` the window hooks. Called once per offered
  * option in the window fold, before the query is parked, so a forbidden
  * option is absent from what the projector offers, from what `accepts`
  * validates and from what a simulation answers. Covers cannot-effects that
  * name a choice rather than the whole action.
  */
final case class OptionRestriction(
    fn: (PowerCtx, DecisionOptionRef) => Option[OathViolation]
) extends Contribution
```

In `ContributionCollector.scala` add the field and the arm:

```scala
final case class GatheredContributions(
    transforms: Vector[(PowerId, Transform)],
    restrictions: Vector[(PowerId, Restriction)],
    order: Vector[PowerId],
    optionRestrictions: Vector[(PowerId, OptionRestriction)] = Vector.empty
)
```
```scala
    val optionRestrictions = Vector.newBuilder[(PowerId, OptionRestriction)]

    ordered.foreach { power =>
      power.contributions(window).foreach {
        case transform: Transform => transforms += power.id -> transform
        case restriction: Restriction => restrictions += power.id -> restriction
        case option: OptionRestriction => optionRestrictions += power.id -> option
      }
    }

    GatheredContributions(
      transforms = transforms.result(),
      restrictions = restrictions.result(),
      order = ordered.map(_.id),
      optionRestrictions = optionRestrictions.result()
    )
```
Update the object doc's "transforms and restrictions" to "transforms, restrictions and option restrictions".

- [ ] **Step 4: Filter in the window fold and check at command entry**

In `WalkerPowerGather.scala` add `Decide`, `DecisionOptionRef` and `DecisionQuery` to the model import and `OptionRestriction` to the `powerresolver` import. In `applyWindow` replace the last two lines of the `case Some(w)` block:

```scala
        val restricted = operation match {
          case _: Decide => restrictOptions(folded, gathered.optionRestrictions,
            ctxFor, byId)
          case _ => folded
        }
        (restricted, gathered.order)
```
and add these methods beside it:

```scala
  private def permits(restrictions: Vector[(PowerId, OptionRestriction)],
      ctxFor: ContributingPower => PowerCtx,
      byId: Map[PowerId, ContributingPower]): DecisionOptionRef => Boolean =
    ref => restrictions.forall { case (id, restriction) =>
      restriction.fn(ctxFor(byId(id)), ref).isEmpty
    }

  /** Removes the forbidden options from every `Decide` in `ops`; see
    * [[OptionRestriction]] for what happens when nothing is left.
    */
  private def restrictOptions(ops: Vector[Operation],
      restrictions: Vector[(PowerId, OptionRestriction)],
      ctxFor: ContributingPower => PowerCtx,
      byId: Map[PowerId, ContributingPower]): Vector[Operation] =
    if (restrictions.isEmpty) ops
    else {
      val permitted = permits(restrictions, ctxFor, byId)
      ops.flatMap {
        case decide: Decide => decide.query match {
          case one: DecisionQuery.ChooseOne => Vector(decide.copy(query =
            one.copy(options = one.options.filter(o => permitted(o.ref)))))
          case many: DecisionQuery.ChooseMany =>
            val options = many.options.filter(o => permitted(o.ref))
            if (many.min == 0 && options.isEmpty) Vector.empty
            else Vector(decide.copy(query = many.copy(
              min = math.min(many.min, options.size),
              max = math.min(many.max, options.size), options = options)))
          case _ => Vector(decide)
        }
        case other => Vector(other)
      }
    }

  /** A required decision with every option forbidden cannot be answered: the
    * violation is the first restriction's, for the first option.
    */
  private def emptiedDecision(decide: Decide,
      restrictions: Vector[(PowerId, OptionRestriction)],
      ctx: ContributingPower => PowerCtx,
      byId: Map[PowerId, ContributingPower]): Vector[OathViolation] = {
    val required: Option[Vector[DecisionOptionRef]] = decide.query match {
      case one: DecisionQuery.ChooseOne => Some(one.options.map(_.ref))
      case many: DecisionQuery.ChooseMany if many.min >= 1 =>
        Some(many.options.map(_.ref))
      case _ => None
    }
    required.filter(_.nonEmpty).toVector.flatMap { refs =>
      val verdicts = refs.map(ref => restrictions.flatMap {
        case (id, restriction) => restriction.fn(ctx(byId(id)), ref)
      }.headOption)
      if (verdicts.forall(_.nonEmpty)) verdicts.head.toVector else Vector.empty
    }
  }
```
In `restrictionViolations`, bind the existing result and append the emptied-decision violations: change `windowsIn(tree, Vector.empty).flatMap { case (window, path, operation) => ... }` to `val rejected = windowsIn(tree, Vector.empty).flatMap { ... }` (same body), then:

```scala
    val emptied = windowsIn(tree, Vector.empty).flatMap {
      case (window, path, decide: Decide) =>
        val ctx = ctxFor(window, path, decide)
        emptiedDecision(decide, ContributionCollector.gather(window,
          powers.powers, ctx).optionRestrictions, ctx, byId)
      case _ => Vector.empty
    }
    rejected ++ emptied
```
Update the object doc to say the traversal also reports an unanswerable required decision.

- [ ] **Step 5: Run and commit**

Run: `./sbtw "testOnly oathdigital.gameplay.OptionRestrictionSuite oathdigital.gameplay.ContributionCollectorSuite oathdigital.gameplay.ContributingPowerSuite oathdigital.gameplay.ProcedureWalkerSuite oathdigital.gameplay.OathRulesWalkerPowerSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS.

```bash
git add -A src
git commit -m "feat(walker): add the OptionRestriction contribution

A power hooked at a Decide's window may forbid options. The walker removes
them in the window fold, so the projector, answer validation and simulation
all see the filtered set; an optional decision left empty is not asked, and
a required decision left empty is rejected at command entry with the
restriction's own violation.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 6: Campaign plan vocabulary at the top level, and the Campaign windows

The battle-plan vocabulary (`CampaignPlanSource`, `Side`, `Cost`, `Effect`, `Resolution`) is nested inside `object PendingProcedure`, which Task 18 deletes, and the new walker code needs it. It moves to a top-level file now, mechanically, so nothing later has to move it while legacy code still uses it. The new Campaign windows are added as audited vocabulary.

**Files:**
- Create: `src/main/scala/oathdigital/model/CampaignTypes.scala`
- Modify: `src/main/scala/oathdigital/model/PendingProcedures.scala:108-172` (delete the nested plan types), `src/main/scala/oathdigital/model/PowerWindow.scala` (add the windows), and every file naming `PendingProcedure.CampaignPlan*` (about 140 references in `src` production and tests, listed by the `git grep` in Step 2)
- Test: create `src/test/scala/oathdigital/model/CampaignWindowsSuite.scala`

**Interfaces:**
- Produces: top-level `oathdigital.model.CampaignPlanSide`, `CampaignPlanCost`, `CampaignPlanEffect`, `CampaignPlanSource` and `CampaignPlanResolution`, unchanged in shape. Windows, all `CampaignWindow`s (associated major action `Campaign`): `CampaignCost` (`campaign.cost`), `CampaignKindSelection` (`campaign.kind-selection`), `CampaignDefenderSelection` (`campaign.defender-selection`), `CampaignTargetSelection` (`campaign.target-selection`), `CampaignForceSelection` (`campaign.force-selection`), `CampaignGatherPools` (`campaign.gather-pools`), `CampaignAttackRoll` (`campaign.attack-roll`), `CampaignAttackResult` (`campaign.attack-result`), `CampaignSacrificeSelection` (`campaign.sacrifice-selection`), `CampaignDefenseRoll` (`campaign.defense-roll`), `CampaignDefenseResult` (`campaign.defense-result`), `CampaignLosses` (`campaign.losses`), `CampaignPlacement` (`campaign.placement`), `CampaignRaidTransfer` (`campaign.raid-transfer`), `CampaignRaidRelocation` (`campaign.raid-relocation`). The existing `CampaignActionEligibility`, `CampaignModifierSelection`, `CampaignBeforeTargets`, `CampaignAttackerBattlePlans`, `CampaignDefenderBattlePlans` and `CampaignAfterOutcome` are unchanged.
- The dormant plan effects `TransformAttackResult`, `ReplaceLosingForcePolicy` and `Suspend` move with the rest and are deleted with the legacy path in Task 17 (the legacy event codec still names them).

- [ ] **Step 1: Write the failing windows test**

Create `src/test/scala/oathdigital/model/CampaignWindowsSuite.scala`:

```scala
package oathdigital.model

class CampaignWindowsSuite extends munit.FunSuite {
  test("the Campaign windows have stable keys and belong to the Campaign action") {
    Vector(
      PowerWindow.CampaignCost -> "campaign.cost",
      PowerWindow.CampaignKindSelection -> "campaign.kind-selection",
      PowerWindow.CampaignDefenderSelection -> "campaign.defender-selection",
      PowerWindow.CampaignTargetSelection -> "campaign.target-selection",
      PowerWindow.CampaignForceSelection -> "campaign.force-selection",
      PowerWindow.CampaignGatherPools -> "campaign.gather-pools",
      PowerWindow.CampaignAttackRoll -> "campaign.attack-roll",
      PowerWindow.CampaignAttackResult -> "campaign.attack-result",
      PowerWindow.CampaignSacrificeSelection -> "campaign.sacrifice-selection",
      PowerWindow.CampaignDefenseRoll -> "campaign.defense-roll",
      PowerWindow.CampaignDefenseResult -> "campaign.defense-result",
      PowerWindow.CampaignLosses -> "campaign.losses",
      PowerWindow.CampaignPlacement -> "campaign.placement",
      PowerWindow.CampaignRaidTransfer -> "campaign.raid-transfer",
      PowerWindow.CampaignRaidRelocation -> "campaign.raid-relocation",
      PowerWindow.CampaignActionEligibility -> "campaign.action-eligibility",
      PowerWindow.CampaignModifierSelection -> "campaign.modifier-selection",
      PowerWindow.CampaignBeforeTargets -> "campaign.before-targets",
      PowerWindow.CampaignAttackerBattlePlans -> "campaign.attacker-battle-plans",
      PowerWindow.CampaignDefenderBattlePlans -> "campaign.defender-battle-plans",
      PowerWindow.CampaignAfterOutcome -> "campaign.after-outcome").foreach {
      case (window, key) =>
        assertEquals(window.key, key)
        assertEquals(window.associatedMajorAction, Some(MajorActionType.Campaign))
    }
  }

  test("every Campaign window key is distinct") {
    val keys = Vector(PowerWindow.CampaignCost, PowerWindow.CampaignKindSelection,
      PowerWindow.CampaignDefenderSelection, PowerWindow.CampaignTargetSelection,
      PowerWindow.CampaignForceSelection, PowerWindow.CampaignGatherPools,
      PowerWindow.CampaignAttackRoll, PowerWindow.CampaignAttackResult,
      PowerWindow.CampaignSacrificeSelection, PowerWindow.CampaignDefenseRoll,
      PowerWindow.CampaignDefenseResult, PowerWindow.CampaignLosses,
      PowerWindow.CampaignPlacement, PowerWindow.CampaignRaidTransfer,
      PowerWindow.CampaignRaidRelocation).map(_.key)
    assertEquals(keys.distinct.size, keys.size)
  }
}
```

- [ ] **Step 2: Run to confirm it fails**

Run: `./sbtw "testOnly oathdigital.model.CampaignWindowsSuite"`
Expected: FAIL to compile, `PowerWindow.CampaignCost` is not defined.

- [ ] **Step 3: Add the windows**

In `PowerWindow.scala` add after `CampaignAfterOutcome`:

```scala
  case object CampaignCost extends CampaignWindow { val key = "campaign.cost" }
  case object CampaignKindSelection extends CampaignWindow {
    val key = "campaign.kind-selection"
  }
  case object CampaignDefenderSelection extends CampaignWindow {
    val key = "campaign.defender-selection"
  }
  case object CampaignTargetSelection extends CampaignWindow {
    val key = "campaign.target-selection"
  }
  case object CampaignForceSelection extends CampaignWindow {
    val key = "campaign.force-selection"
  }
  case object CampaignGatherPools extends CampaignWindow {
    val key = "campaign.gather-pools"
  }
  case object CampaignAttackRoll extends CampaignWindow {
    val key = "campaign.attack-roll"
  }
  case object CampaignAttackResult extends CampaignWindow {
    val key = "campaign.attack-result"
  }
  case object CampaignSacrificeSelection extends CampaignWindow {
    val key = "campaign.sacrifice-selection"
  }
  case object CampaignDefenseRoll extends CampaignWindow {
    val key = "campaign.defense-roll"
  }
  case object CampaignDefenseResult extends CampaignWindow {
    val key = "campaign.defense-result"
  }
  case object CampaignLosses extends CampaignWindow { val key = "campaign.losses" }
  case object CampaignPlacement extends CampaignWindow {
    val key = "campaign.placement"
  }
  case object CampaignRaidTransfer extends CampaignWindow {
    val key = "campaign.raid-transfer"
  }
  case object CampaignRaidRelocation extends CampaignWindow {
    val key = "campaign.raid-relocation"
  }
```

- [ ] **Step 4: Move the plan vocabulary**

Create `src/main/scala/oathdigital/model/CampaignTypes.scala` holding exactly the nested definitions of `PendingProcedure` lines 109-172, now top-level:

```scala
package oathdigital.model

/** Which side of a Campaign a battle plan belongs to. */
sealed trait CampaignPlanSide extends Product with Serializable
object CampaignPlanSide {
  case object Attacker extends CampaignPlanSide
  case object Defender extends CampaignPlanSide
}

sealed trait CampaignPlanCost extends Product with Serializable
object CampaignPlanCost {
  final case class Favor(count: Int) extends CampaignPlanCost {
    require(count > 0, "Campaign favor cost must be positive")
  }
  final case class Secret(count: Int) extends CampaignPlanCost {
    require(count > 0, "Campaign secret cost must be positive")
  }
}

sealed trait CampaignPlanEffect extends Product with Serializable
object CampaignPlanEffect {
  final case class AddAttackDice(count: Int) extends CampaignPlanEffect {
    require(count > 0, "added Campaign attack dice must be positive")
  }
  final case class AddDefenseDice(count: Int) extends CampaignPlanEffect {
    require(count > 0, "added Campaign defense dice must be positive")
  }
  case object IgnoreAttackSkulls extends CampaignPlanEffect
  case object RevealSource extends CampaignPlanEffect
  /** Typed extension points: their payload remains owned by a registered
    * handler rather than interpreted as a general card scripting language.
    * No handler produces them; they are deleted with the legacy Campaign.
    */
  final case class TransformAttackResult(handlerId: String)
      extends CampaignPlanEffect
  final case class ReplaceLosingForcePolicy(policyId: String)
      extends CampaignPlanEffect
  final case class Suspend(decisionKind: String) extends CampaignPlanEffect
}

sealed trait CampaignPlanSource extends Product with Serializable {
  def stableKey: String
}
object CampaignPlanSource {
  final case class Adviser(playerId: PlayerId, id: DenizenId)
      extends CampaignPlanSource {
    def stableKey: String = s"adviser:${playerId.value}:denizen:${id.value}"
  }
  final case class SiteCard(siteId: SiteId, id: DenizenId)
      extends CampaignPlanSource {
    def stableKey: String = s"site-card:${siteId.value}:denizen:${id.value}"
  }
  final case class Relic(playerId: PlayerId, id: RelicId)
      extends CampaignPlanSource {
    def stableKey: String = s"relic:${playerId.value}:${id.value}"
  }
  final case class Title(playerId: PlayerId) extends CampaignPlanSource {
    def stableKey: String = s"title:${playerId.value}"
  }
}

final case class CampaignPlanResolution(
    source: CampaignPlanSource,
    handlerId: String,
    side: CampaignPlanSide,
    costs: Vector[CampaignPlanCost],
    effects: Vector[CampaignPlanEffect]
)
```

Delete lines 109-172 of `PendingProcedures.scala` (from `sealed trait CampaignPlanSide` through the closing brace of `CampaignPlanResolution`), leaving `object PendingProcedure { final case class Campaign(...) ... }` with its two cases. Then rename every qualified use:

```bash
git grep -l "PendingProcedure\.CampaignPlan" -- src shared frontend | xargs perl -pi -e 's/\bPendingProcedure\.CampaignPlan(Side|Cost|Effect|Source|Resolution)\b/CampaignPlan$1/g'
git grep -n "PendingProcedure\.CampaignPlan" -- src shared frontend
```
Expected: the second command prints nothing. `shared` and `frontend` name only `oathdigital.protocol.CampaignPlanSource`, a different type, and are untouched. `CampaignPlans.scala` imports `PendingProcedure._` and names the plan types unqualified: they now resolve to the top-level ones through its existing `oathdigital.model._` import.

- [ ] **Step 5: Run and commit**

Run: `./sbtw "Test/compile" "frontend/Test/compile" "testOnly oathdigital.model.CampaignWindowsSuite oathdigital.gameplay.CampaignSuite oathdigital.serialization.GameEventWireSuite oathdigital.application.GameApplicationServiceSuite oathdigital.application.PendingWalkerInvariantSuite"`
Expected: PASS.

```bash
git add -A src shared frontend
git commit -m "refactor(campaign): hoist the plan vocabulary and add the Campaign windows

The battle-plan types nested in PendingProcedure move to a top-level file
so the walker code does not depend on the legacy pending type. The Campaign
windows are added as audited hook points; no power uses them yet.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 7: The durable Campaign result fact

Players need to see the dice and the victor of a Campaign, including a Conquest that ends in the same command as its defense roll. There is no action-history feed, and roll outcomes are cleared when the walker completes, so the result is a public fact in state, written by an operation the tree records (and therefore replayed from the journal). This task adds the fact and its operation; Task 11 records it and Tasks 13 and 14 show it.

**Files:**
- Modify: `src/main/scala/oathdigital/model/CampaignTypes.scala` (add `CampaignResult`), `src/main/scala/oathdigital/model/CoreOperations.scala` (add `RecordCampaignResult` near `SetOathkeeper`, line 510), `src/main/scala/oathdigital/model/GameState.scala:130-160` (add the state field), `src/main/scala/oathdigital/gameplay/operations/OperationStateMutation.scala:311-321`, `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala:40,231` (extend the trait and add both arms)
- Create: `src/main/scala/oathdigital/serialization/CampaignResultCodec.scala`
- Test: create `src/test/scala/oathdigital/gameplay/operations/RecordCampaignResultSuite.scala`, `src/test/scala/oathdigital/serialization/CampaignResultCodecSuite.scala`; modify `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala` (the operation list near line 640)

**Interfaces:**
- Produces: `CampaignResult(attacker, kind, defender, targetSites, raidTargets, force, attackFaces: Vector[AttackDieFace], attackScore, skullLosses, sacrificed, defenseFaces: Vector[DefenseDieFace], defenseScore, victorious)` with `attackTotal = attackScore + sacrificed`; `RecordCampaignResult(result)`; `CurrentGameState.lastCampaignResult: Option[CampaignResult] = None`, overwritten by the next Campaign and never cleared by `WalkerCompleted`. `attackScore` is the score after the skull cap and Outriders and before the sacrifice; `defenseScore` is the dice score plus the defender's board force. Every field is public information: dice are public, and Raid targets are faceup relics, banners and a pawn.
- Codec: `encodeCampaignResult` and `decodeCampaignResult` in `CampaignResultCodec`, used by the `"record-campaign-result"` operation arm.
- Consumes: nothing from earlier tasks.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/operations/RecordCampaignResultSuite.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.model._

class RecordCampaignResultSuite extends munit.FunSuite {
  private val conquest = CampaignResult(PlayerId("red"), CampaignKind.Conquest,
    CampaignDefender.Bandits, Vector(SiteId("site:a")), Vector.empty, force = 3,
    attackFaces = Vector(AttackDieFace.HollowSword, AttackDieFace.TwoSwordsSkull),
    attackScore = 2, skullLosses = 1, sacrificed = 1,
    defenseFaces = Vector(DefenseDieFace.OneShield), defenseScore = 4,
    victorious = false)

  private def record(ready: ReadyGame, result: CampaignResult) =
    new OperationExecutor().executeAll(ready, Vector(RecordCampaignResult(result)))
      .toOption.get.game.current.lastCampaignResult

  test("recording a result writes it into state") {
    assertEquals(TestGameFixtures.ready.game.current.lastCampaignResult, None)
    assertEquals(record(TestGameFixtures.ready, conquest), Some(conquest))
  }

  test("the next Campaign's result replaces the last one") {
    val later = conquest.copy(victorious = true, sacrificed = 0)
    val once = TestGameFixtures.ready.updateCurrent(
      _.copy(lastCampaignResult = Some(conquest)))
    assertEquals(record(once, later), Some(later))
  }

  test("the total an attacker brought is the score plus the sacrifice") {
    assertEquals(conquest.attackTotal, 3)
  }
}
```

Create `src/test/scala/oathdigital/serialization/CampaignResultCodecSuite.scala`:

```scala
package oathdigital.serialization

import oathdigital.engine.RecordedEvent
import oathdigital.gameplay.walker.{WalkerStepPayload, DeltaMeaning, WalkerStepRecorded}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class CampaignResultCodecSuite extends munit.FunSuite {
  private val conquest = CampaignResult(PlayerId("red"), CampaignKind.Conquest,
    CampaignDefender.Bandits, Vector(SiteId("site:a"), SiteId("site:b")),
    Vector.empty, force = 3,
    attackFaces = Vector(AttackDieFace.HollowSword, AttackDieFace.TwoSwordsSkull),
    attackScore = 2, skullLosses = 1, sacrificed = 1,
    defenseFaces = Vector(DefenseDieFace.OneShield, DefenseDieFace.Doubler),
    defenseScore = 4, victorious = false)
  private val raid = conquest.copy(kind = CampaignKind.Raid,
    defender = CampaignDefender.Player(PlayerId("blue")),
    targetSites = Vector.empty,
    raidTargets = Vector(CampaignRaidTarget.Pawn(PlayerId("blue")),
      CampaignRaidTarget.Relic(PlayerId("blue"), RelicId("r1")),
      CampaignRaidTarget.Banner(PlayerId("blue"), Banner.PeoplesFavor)),
    victorious = true)
  private val empty = conquest.copy(attackFaces = Vector.empty,
    defenseFaces = Vector.empty, attackScore = 0, skullLosses = 0,
    sacrificed = 0, force = 0)

  test("a recorded Campaign result round trips through the wire for both kinds") {
    val events = Vector(conquest, raid, empty).map(result =>
      WalkerStepRecorded("0", WalkerStepPayload.DeltaRecorded(
        DeltaMeaning.OperationApplied("campaign")),
        Vector(RecordCampaignResult(result)), Vector.empty): OathEvent)
    val encoded = GameEventWire.encodeStream("campaign-result", catalog.ref,
      events.zipWithIndex.map { case (event, index) =>
        RecordedEvent(index.toLong, event) }).toOption.get
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event),
      events)
    assertEquals(ujson.read(encoded).arr.map(
      _("payload")("ops")(0)("kind").str).toVector.distinct,
      Vector("record-campaign-result"))
  }

  test("a malformed recorded result is a typed decode failure, not an exception") {
    val encoded = GameEventWire.encodeEvent("campaign-result", catalog.ref, 0,
      WalkerStepRecorded("0", WalkerStepPayload.DeltaRecorded(
        DeltaMeaning.OperationApplied("campaign")),
        Vector(RecordCampaignResult(conquest)), Vector.empty)).toOption.get
    val json = ujson.read(encoded)
    json("payload")("ops")(0)("result")("attackFaces") = ujson.Arr("not-a-face")
    assert(GameEventWire.decode(json.toString).isLeft)
  }
}
```

In `GameEventWireSuite.scala` add to the `operations` list (before `SetOathkeeper(Some(...))`):

```scala
      RecordCampaignResult(CampaignResult(player, CampaignKind.Conquest,
        CampaignDefender.Bandits, Vector(site), Vector.empty, force = 2,
        attackFaces = Vector(AttackDieFace.OneSword), attackScore = 1,
        skullLosses = 0, sacrificed = 0,
        defenseFaces = Vector(DefenseDieFace.Blank), defenseScore = 2,
        victorious = false)),
```
(`player` and `site` are that test's own values.)

- [ ] **Step 2: Run to confirm it fails**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, `CampaignResult` and `RecordCampaignResult` are not defined.

- [ ] **Step 3: Model, operation and state**

Append to `CampaignTypes.scala`:

```scala
/** The public, durable record of one Campaign's battle: written by
  * `RecordCampaignResult` when the outcome is known, projected to every viewer,
  * and replaced by the next Campaign. Everything in it is public: dice are
  * public, and a Raid's targets are a pawn, faceup relics and banners.
  *
  * `attackScore` is the attack after the skull cap and any Outriders, before
  * the sacrifice; `defenseScore` is the defense dice score plus the defender's
  * board force. The attacker prevails when `attackTotal > defenseScore`.
  */
final case class CampaignResult(
    attacker: PlayerId,
    kind: CampaignKind,
    defender: CampaignDefender,
    targetSites: Vector[SiteId],
    raidTargets: Vector[CampaignRaidTarget],
    force: Int,
    attackFaces: Vector[AttackDieFace],
    attackScore: Int,
    skullLosses: Int,
    sacrificed: Int,
    defenseFaces: Vector[DefenseDieFace],
    defenseScore: Int,
    victorious: Boolean
) {
  def attackTotal: Int = attackScore + sacrificed
}
```

In `CoreOperations.scala` add beside `SetOathkeeper`:

```scala
/** Records the result of the Campaign just fought as the public
  * `lastCampaignResult`, replacing the previous one.
  */
final case class RecordCampaignResult(result: CampaignResult)
    extends PrimitiveOperation
```

In `GameState.scala` add to `CurrentGameState` after `walkerStartArgs`:

```scala
    walkerStartArgs: Vector[DecisionOptionRef] = Vector.empty,
    // The public result of the last Campaign fought. Not walker scratch:
    // `WalkerCompleted` leaves it, and the next Campaign replaces it.
    lastCampaignResult: Option[CampaignResult] = None
```

In `OperationStateMutation.applyNonMoveLeaves` add before the catch-all:

```scala
      case (result, RecordCampaignResult(fact)) =>
        result.map(_.updateCurrent(_.copy(lastCampaignResult = Some(fact))))
```

- [ ] **Step 4: The codec**

Create `CampaignResultCodec.scala`:

```scala
package oathdigital.serialization

import oathdigital.model._
import scala.util.control.NonFatal

/** Wire spelling of [[CampaignResult]], for the `record-campaign-result`
  * operation. Split out of `WalkerOperationCodec` for headroom.
  */
private[serialization] trait CampaignResultCodec {
    this: GameEventJsonSupport =>
  import WireError._

  protected final def encodeCampaignResult(result: CampaignResult): ujson.Value =
    ujson.Obj(
      "attackerPlayerId" -> result.attacker.value,
      "campaignKind" -> result.kind.key,
      "defender" -> (result.defender match {
        case CampaignDefender.Bandits => ujson.Obj("kind" -> "bandits")
        case CampaignDefender.Player(id) =>
          ujson.Obj("kind" -> "player", "playerId" -> id.value)
      }),
      "targetSiteIds" -> ujson.Arr.from(result.targetSites.map(site =>
        ujson.Str(site.value))),
      "raidTargets" -> ujson.Arr.from(result.raidTargets.map(
        encodeCampaignRaidTarget)),
      "force" -> result.force,
      "attackFaces" -> ujson.Arr.from(result.attackFaces.map(face =>
        ujson.Str(encodeAttackFace(face)))),
      "attackScore" -> result.attackScore,
      "skullLosses" -> result.skullLosses,
      "sacrificed" -> result.sacrificed,
      "defenseFaces" -> ujson.Arr.from(result.defenseFaces.map(face =>
        ujson.Str(encodeDefenseFace(face)))),
      "defenseScore" -> result.defenseScore,
      "victorious" -> result.victorious)

  protected final def decodeCampaignResult(value: ujson.Value, path: String)
      : Either[WireError, CampaignResult] = try {
    for {
      kind <- decodeCampaignKind(value("campaignKind"), s"$path.campaignKind")
      defender <- value("defender")("kind").str match {
        case "bandits" => Right(CampaignDefender.Bandits)
        case "player" => Right(CampaignDefender.Player(
          PlayerId(value("defender")("playerId").str)))
        case other => Left(InvalidValue(s"$path.defender.kind",
          s"unknown Campaign defender '$other'"))
      }
      raidTargets <- traverse(value("raidTargets").arr.toVector.zipWithIndex) {
        case (entry, index) =>
          decodeCampaignRaidTarget(entry, s"$path.raidTargets[$index]")
      }
      attackFaces <- traverse(value("attackFaces").arr.toVector.zipWithIndex) {
        case (entry, index) =>
          decodeAttackFace(entry.str, s"$path.attackFaces[$index]")
      }
      defenseFaces <- traverse(value("defenseFaces").arr.toVector.zipWithIndex) {
        case (entry, index) =>
          decodeDefenseFace(entry.str, s"$path.defenseFaces[$index]")
      }
    } yield CampaignResult(PlayerId(value("attackerPlayerId").str), kind,
      defender, value("targetSiteIds").arr.toVector.map(v => SiteId(v.str)),
      raidTargets, value("force").num.toInt, attackFaces,
      value("attackScore").num.toInt, value("skullLosses").num.toInt,
      value("sacrificed").num.toInt, defenseFaces,
      value("defenseScore").num.toInt, value("victorious").bool)
  } catch {
    case NonFatal(error) => Left(InvalidValue(path,
      Option(error.getMessage).getOrElse("invalid Campaign result")))
  }
}
```
(`traverse` is the helper `WalkerEventCodec` already uses; it lives in `GameEventJsonSupport`. If it is declared with a different arity, mirror the call in `DecisionAnswerCodec`.)

In `WalkerOperationCodec.scala` change the head to `private[serialization] trait WalkerOperationCodec extends CampaignResultCodec {` and add the arms:

```scala
      case RecordCampaignResult(result) => ujson.Obj(
        "kind" -> "record-campaign-result",
        "result" -> encodeCampaignResult(result))
```
```scala
      case "record-campaign-result" =>
        decodeCampaignResult(value("result"), s"$path.result")
          .map(RecordCampaignResult(_))
```

- [ ] **Step 5: Run and commit**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.RecordCampaignResultSuite oathdigital.serialization.CampaignResultCodecSuite oathdigital.serialization.GameEventWireSuite oathdigital.gameplay.operations.OperationStateMutationSuite oathdigital.gameplay.BackendArchitectureSuite" && wc -l src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`
Expected: PASS, the codec under 700 lines.

```bash
git add -A src
git commit -m "feat(campaign): add the durable Campaign result fact

RecordCampaignResult writes a public CampaignResult (both dice sets, the
totals, the victor and the targets) into state, replayed from the journal
and replaced by the next Campaign. Roll outcomes are cleared when a walker
completes and there is no action-history feed, so this is what lets every
player see the battle.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 8: Vow of Peace and Narrow Pass as Campaign powers

Both rules exist today as private legacy checks (`vowOfPeace` in `CampaignRules`, `passAllowsTarget`). They become walker powers, so the Campaign tree in Tasks 9 to 12 asks no question about either: Vow of Peace is a root `Restriction` at `CampaignActionEligibility`, and Narrow Pass gains an `OptionRestriction` at `CampaignTargetSelection` beside its Travel restriction. The legacy checks stay until Task 17 (the parity suite compares against them).

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/VowOfPeaceContribution.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala:64-100` (`NarrowPassSitePower`), `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala:24-29`
- Test: create `src/test/scala/oathdigital/gameplay/CampaignPowersSuite.scala`

**Interfaces:**
- Produces: `VowOfPeaceContribution.forCatalog(catalog): Option[VowOfPeaceContribution]`, a `ContributingPower` with id `denizen.vow-of-peace` whose `Restriction` at `PowerWindow.CampaignActionEligibility` returns `OathViolation.CampaignUnavailable("Vow of Peace prevents its ruler from campaigning")` when the acting player holds the card faceup as an adviser. `NarrowPassSitePower` additionally hooks `PowerWindow.CampaignTargetSelection` with an `OptionRestriction` that forbids a `DecisionOptionRef.Site(target)` when the acting player's pawn is outside the Pass's region, `target` is another site in that region, and the actor does not rule the Pass. Both are in `WalkerPowerCatalog.default(catalog)`.
- Consumes: `OptionRestriction` (Task 5), the windows (Task 6).
- Rule text and recorded details are in the spec's **Powers** section: the check is per candidate site and never reads the other targets; the Pass itself stays targetable; only site options are filtered, so a Raid's relic and banner options are never touched; "consent" is approximated as "the actor rules the Pass".

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/CampaignPowersSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.powers.campaign.VowOfPeaceContribution
import oathdigital.gameplay.powers.travel.{NarrowPassSitePower, TravelSitePowers}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathState.Ready

class CampaignPowersSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)
  private val Ready(initial) = execute(setup)._1: @unchecked
  private val actor: PlayerId = initial.game.current.turn.activePlayer
  private val other: PlayerId =
    initial.game.current.players.map(_.player).find(_ != actor).get

  // ---- Vow of Peace ------------------------------------------------------
  private val vowCard = catalog.denizens.find(_.handlers.contains(
    "denizen.vow-of-peace")).get
  private val eligibility =
    Sequence(Vector.empty, Some(PowerWindow.CampaignActionEligibility))
  private val vowPowers =
    WalkerPowers(VowOfPeaceContribution.forCatalog(catalog).toVector)

  private def holding(orientation: Orientation, holder: PlayerId): ReadyGame =
    initial.updateCurrent(current => current.copy(players = current.players.map(
      player => if (player.player == holder) player.copy(advisers = Vector(
        DenizenState(DenizenId(vowCard.id.value), orientation, Tokens.empty)))
      else player)))

  private def vowViolations(ready: ReadyGame) =
    ProcedureWalker.restrictionViolations(eligibility, vowPowers, ready, actor)

  test("a faceup Vow of Peace blocks its holder's Campaign at eligibility") {
    assertEquals(vowViolations(holding(Orientation.FaceUp, actor)), Vector(
      OathViolation.CampaignUnavailable(
        "Vow of Peace prevents its ruler from campaigning")))
  }

  test("a facedown Vow of Peace, or another player's, blocks nothing") {
    assertEquals(vowViolations(holding(Orientation.FaceDown, actor)),
      Vector.empty[OathViolation])
    assertEquals(vowViolations(holding(Orientation.FaceUp, other)),
      Vector.empty[OathViolation])
    assertEquals(vowViolations(initial), Vector.empty[OathViolation])
  }

  // ---- Narrow Pass -------------------------------------------------------
  private val pass = catalog.sites.find(_.handlers.contains(
    "site.narrow-pass.pass")).get.id
  private val others = catalog.sites.map(_.id).filterNot(_ == pass)
  /** Cradle: o0, o1. Provinces: the Pass, o2, o3. Hinterland: o4, o5, o6. */
  private val ordered = Vector(others.head, others(1), pass, others(2),
    others(3), others(4), others(5), others(6))
  private val passMap = {
    val states = ordered.map { id =>
      val definition = catalog.sites.find(_.id == id).get
      id -> SiteState(
        if (definition.capacity == 0) SiteForces.Empty
        else SiteForces.Occupied(ForceKind.Bandit, definition.capacity),
        Vector.empty, Vector.empty, definition.startingResources)
    }.toMap
    MapState(ordered.take(2), ordered.slice(2, 5), ordered.slice(5, 8), states)
  }
  private def withPawnAt(site: SiteId, map: MapState = passMap): ReadyGame =
    initial.updateCurrent(current => current.copy(map = map,
      players = current.players.map(player =>
        if (player.player == actor) player.copy(pawnSite = Some(site))
        else player)))

  private val passPower = TravelSitePowers.forCatalog(catalog).collectFirst {
    case power: NarrowPassSitePower => power }.get
  private def siteOption(id: SiteId) =
    DecisionOption.Site(DecisionOptionRef.Site(id))
  private val candidates = Vector(pass, ordered(3), ordered(4), ordered(1),
    ordered(5))
  private def targets(options: Vector[DecisionOption]) = Sequence(Vector[Operation](
    Decide("campaign.targets", actor, DecisionQuery.ChooseMany(0, options.size,
      options, Some("Choose targets")),
      window = Some(PowerWindow.CampaignTargetSelection))))

  private def offered(ready: ReadyGame,
      options: Vector[DecisionOption] = candidates.map(siteOption))
      : Vector[DecisionOption] = {
    val tree = targets(options)
    val powers = WalkerPowers(Vector(passPower))
    val Right(WalkerOutcome.Parked(pending, _)) =
      ProcedureWalker.advance(ready, tree, None, powers): @unchecked
    ProcedureWalker.openDecisions(ready, tree, pending, powers).head.query match {
      case many: DecisionQuery.ChooseMany => many.options
      case other => fail(s"expected a choose-many, got $other")
    }
  }

  test("Pass keeps its own site targetable and removes the other sites in its region") {
    assertEquals(offered(withPawnAt(ordered.head)).map(_.ref),
      Vector(pass, ordered(1), ordered(5)).map(DecisionOptionRef.Site(_)))
  }

  test("a pawn already inside the Pass's region is not blocked") {
    assertEquals(offered(withPawnAt(ordered(3))).map(_.ref),
      candidates.map(DecisionOptionRef.Site(_)))
  }

  test("the ruler of the Pass may target every site in its region") {
    val lineage = initial.game.current.players.find(_.player == actor).get.lineage
    val ruled = passMap.copy(sites = passMap.sites.updated(pass,
      passMap.sites(pass).copy(forces = SiteForces.Occupied(
        ForceKind.Exile(lineage), 1))))
    assertEquals(offered(withPawnAt(ordered.head, ruled)).map(_.ref),
      candidates.map(DecisionOptionRef.Site(_)))
  }

  test("options that are not sites are never filtered") {
    val relics = Vector("r1", "r2").map(id =>
      DecisionOption.Relic(DecisionOptionRef.Relic(RelicId(id))))
    assertEquals(offered(withPawnAt(ordered.head), relics), relics)
  }

  test("a Pass that is not in play forbids nothing") {
    val without = passMap.copy(sites = passMap.sites - pass,
      provinces = passMap.provinces.filterNot(_ == pass))
    assertEquals(offered(withPawnAt(ordered.head, without),
      Vector(ordered(3), ordered(4)).map(siteOption)).size, 2)
  }
}
```

- [ ] **Step 2: Run to confirm it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignPowersSuite"`
Expected: FAIL to compile, `VowOfPeaceContribution` is not defined.

- [ ] **Step 3: Vow of Peace**

Create `VowOfPeaceContribution.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Vow of Peace: "You cannot campaign." A faceup copy held as an adviser blocks
  * its holder's whole Campaign, a `Restriction` at the action's root, the same
  * shape as Narrow Pass blocking a Travel. A facedown copy is not active, and
  * the card is adviser-only, so no other location is considered. The second
  * printed sentence, that attackers cannot sacrifice against a holder, is not
  * modelled (the legacy Campaign never modelled it either).
  */
final case class VowOfPeaceContribution private (cardId: DenizenId)
    extends ContributingPower {
  def id: PowerId = VowOfPeaceContribution.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.CampaignActionEligibility ->
      Vector(Restriction((ctx, _) => blocked(ctx))))

  private def blocked(ctx: PowerCtx): Option[OathViolation] =
    Option.when(ctx.state.game.current.players
      .find(_.player == ctx.activePlayer).exists(_.advisers.exists {
        case card: DenizenState =>
          card.id == cardId && card.orientation == Orientation.FaceUp
        case _ => false
      }))(OathViolation.CampaignUnavailable(
        "Vow of Peace prevents its ruler from campaigning"))
}

object VowOfPeaceContribution {
  val id: PowerId = PowerId("denizen.vow-of-peace")

  /** `None` when the catalog has no such card, for example a test stub. */
  def forCatalog(catalog: ExecutableCatalog): Option[VowOfPeaceContribution] =
    catalog.denizens.find(_.handlers.contains(id.value))
      .map(card => new VowOfPeaceContribution(DenizenId(card.id.value)))
}
```

In `WalkerPowerCatalog.scala` import it and add `VowOfPeaceContribution.forCatalog(catalog).toVector ++` to the `WalkerPowers(...)` list (before `TravelSitePowers.forCatalog(catalog)`); extend the object doc with "Vow of Peace's restriction is inert until Campaign walks `CampaignActionEligibility`."

- [ ] **Step 4: Narrow Pass**

In `TravelSitePowers.scala` change `NarrowPassSitePower` (add `DecisionOptionRef` and `OptionRestriction` to the imports):

```scala
final case class NarrowPassSitePower(id: PowerId, site: SiteId,
    coastSites: Set[SiteId], coastOrIslandSites: Set[SiteId])
    extends ContributingPower {
  def source: RuleSourceRef = RuleSourceRef.Site(site)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelActionEligibility ->
      Vector(Restriction((ctx, _) => blocked(ctx))),
    PowerWindow.CampaignTargetSelection ->
      Vector(OptionRestriction(campaignBlocked)))

  override def applicable(ctx: PowerCtx): Boolean = ctx.window match {
    // Per candidate, in `campaignBlocked`: nothing about the tree decides it.
    case PowerWindow.CampaignTargetSelection => true
    case _ => TravelRoute.pawnMove(ctx.operation).exists { route =>
      val map = ctx.state.game.current.map
      (for {
        sourceRegion <- map.regionOf(route.source)
        destinationRegion <- map.regionOf(route.destination)
        passRegion <- map.regionOf(site)
      } yield sourceRegion != destinationRegion && destinationRegion == passRegion &&
        route.destination != site).getOrElse(false)
    }
  }
```
keeping the existing `blocked` method unchanged, and add:

```scala
  /** Campaign: "If your pawn is outside this region, you cannot ... target
    * other sites in this region in campaigns, unless you have the consent of
    * the Pass's ruler." Judged per candidate site, never against the other
    * targets. Consent is approximated as ruling the Pass, as it was before
    * this was a power. Only site options are considered: a Raid targets a
    * pawn, relics and banners, never a site.
    */
  private def campaignBlocked(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = ref match {
    case DecisionOptionRef.Site(target) =>
      val current = ctx.state.game.current
      val map = current.map
      val outside = for {
        pawn <- current.players.find(_.player == ctx.activePlayer)
          .flatMap(_.pawnSite)
        pawnRegion <- map.regionOf(pawn)
        targetRegion <- map.regionOf(target)
        passRegion <- map.regionOf(site)
      } yield pawnRegion != targetRegion && targetRegion == passRegion &&
        target != site
      val ruledByActor = map.sites.get(site).exists(pass =>
        SiteRule.ruler(pass.forces, current.players) match {
          case Right(SiteRuler.Player(player)) => player == ctx.activePlayer
          case _ => false
        })
      Option.when(outside.contains(true) && !ruledByActor)(
        OathViolation.CampaignUnavailable(
          s"a Pass prevents targeting '${target.value}' from the pawn site"))
    case _ => None
  }
```

- [ ] **Step 5: Run and commit**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignPowersSuite oathdigital.gameplay.TravelSitePowersSuite oathdigital.gameplay.TravelProcedureSuite oathdigital.gameplay.CampaignSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS. `TravelSitePowersSuite` and `TravelProcedureSuite` prove the Travel restriction is unchanged.

```bash
git add -A src
git commit -m "feat(campaign): Vow of Peace and Narrow Pass as walker powers

Vow of Peace is a root Restriction at CampaignActionEligibility and Narrow
Pass gains an OptionRestriction at CampaignTargetSelection beside its Travel
restriction. Nothing walks those windows yet; the legacy checks stay until
the legacy Campaign is deleted.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 9: Campaign on the walker, part 1: registration, kind, targets, force and the dice pools

The first four steps of the tree (cost, kind and defender, targets, force) and the gathering of both dice pools, registered as `ActionRef.Campaign`. Nothing offers the start control yet (Task 13), and Tasks 10 to 12 extend the tree, so a client cannot reach the intermediate tree.

**Design decisions this task fixes, all following walker rule W:**
- The tree is `Sequence(window = CampaignActionEligibility)` of: `Sequence(CampaignCost, SpendSupply(actor, 2))`; `Sequence(CampaignBeforeTargets, kind, defender, targets)`; `campaign.force`; `Sequence(CampaignGatherPools, BuildOps(gather))`; and, in later tasks, plans, rolls, results and resolution.
- Each early decision sits in a `Branch` whose selection reads live state that nothing before it changes (pawn site, rulers, enemy pawns, the defender's relics) plus earlier answers. Earlier siblings are never re-selected when a later decision resumes (the walker re-enters only the nodes on the path to the parked leaf), so this is safe until the losses run. Everything after the losses reads the durable `CampaignResult` and the answers instead (Task 11).
- `campaign.kind` is omitted when one kind is legal, and `campaign.defender` when one enemy pawn is present. When omitted, later steps derive the value from the same live state, which is still unchanged then.
- `campaign.targets` answers only the optional additions (spec, "Targets"). The full target set is the mandatory piece plus the answer, in canonical order.
- Both pools are gathered once, after the force answer: the attack pool holds the committed force, the defense pool the targets' printed defense. Nothing on the board changes before the terminal steps, so the counts are stable.

**Files:**
- Modify: `src/main/scala/oathdigital/model/ProcedureRef.scala:52-67` (add `ActionRef.Campaign`), `src/main/scala/oathdigital/model/GameProcedureProtocol.scala:38-50` (add `AwaitingCampaignDecision`), `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` (the entry and its import), `src/test/scala/oathdigital/model/ActionValuesSuite.scala:18-20`
- Create: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala`, `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignBattle.scala`, `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala`
- Test: create `src/test/scala/oathdigital/gameplay/CampaignFixture.scala`, `src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala`, `src/test/scala/oathdigital/gameplay/CampaignSetupSuite.scala`

**Interfaces:**
- Produces: `ActionRef.Campaign` (key `"campaign"`, last in `ActionRef.all`); `OathContinue.AwaitingCampaignDecision(playerId, decision)`; `CampaignIds` (decision ids `campaign.kind`, `.defender`, `.targets`, `.force`, `.attacker-plan`, `.defender-plan`, `.sacrifice`, `.placement`, `.relocation`; pools `campaign.attack` and `campaign.defense`; `SupplyCost = 2`; `finish`); `CampaignSetup(actor, kind, origin, defender, targetSites, raidTargets, force)` and `object CampaignSetup` (`originOf`, `defenderAt`, `conquestDefender`, `raidDefenders`, `legalKinds`, `kindOptions`, `defenderOptions`, `targetOptions`, `kindOf`, `defenderOf`, `setup`); `CampaignAnswers` (readers over `PendingTree`); `CampaignBattle.printedDefense` and `gatherPools`; `CampaignProcedure.build/rebuild/startable/decisionIds`.
- Rules: the gates are the Act phase, `PowerRuntime.requireAudited` and at least one legal kind. The first-game gates (unaltered Foundation, exile-only roles) do not exist. Conquest is legal when the actor's pawn site is ruled by Bandits or by another player and is not the actor's; Raid is legal when another player's pawn is at the actor's site. Supply is a cost owned by `SpendSupply`, not a gate. Vow of Peace and Narrow Pass reach the tree only through the powers of Task 8.

- [ ] **Step 1: Write the fixture and the failing tests**

Create `src/test/scala/oathdigital/gameplay/CampaignFixture.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerDice, WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** The boards the Campaign suites share. The actor stands at `origin`, ruled
  * by two Bandits, in the Act phase with Supply and warbands; `extras` further
  * sites are also Bandit-ruled and every other site is empty and unruled; no
  * site holds a denizen. The other player stands elsewhere.
  */
object CampaignFixture {
  final case class Board(ready: ReadyGame, actor: PlayerId, other: PlayerId,
      origin: SiteId) {
    def player(id: PlayerId): PlayerState =
      ready.game.current.players.find(_.player == id).get
    def extras: Vector[SiteId] = ready.game.current.map.inPlay.filter(site =>
      site != origin && ready.game.current.map.sites(site).forces ==
        SiteForces.Occupied(ForceKind.Bandit, 2))
  }

  private val setup = new FirstGameSetupRules(catalog)

  def board(extras: Int = 0, warbands: Int = 5, supply: Int = 7): Board = {
    val Ready(base) = execute(setup)._1: @unchecked
    val current = base.game.current
    val inPlay = current.map.inPlay
    val origin = inPlay.find(id => catalog.sites.find(_.id == id).exists(
      _.handlers.forall(h => !h.endsWith(".mountain") && !h.endsWith(".plains")))).get
    val ruled = (origin +: inPlay.filter(_ != origin).take(extras)).toSet
    val elsewhere = inPlay.find(!ruled(_)).getOrElse(inPlay.find(_ != origin).get)
    val activeId = current.turn.activePlayer
    val otherId = current.players.map(_.player).find(_ != activeId).get
    val players = current.players.map { player =>
      if (player.player == activeId) player.copy(pawnSite = Some(origin),
        board = player.board.copy(warbands = warbands,
          supply = SupplyTrack(supply)))
      else player.copy(pawnSite = Some(elsewhere))
    }
    val sites = current.map.sites.map { case (id, site) =>
      id -> site.copy(denizens = Vector.empty, forces =
        if (ruled(id)) SiteForces.Occupied(ForceKind.Bandit, 2)
        else SiteForces.Empty)
    }
    val ready = base.updateCurrent(_.copy(players = players, pending = None,
      map = current.map.copy(sites = sites),
      turn = current.turn.copy(phase = Phase.Act)))
    Board(ready, activeId, otherId, origin)
  }

  /** The other player joins the actor at `origin`, so a Raid is legal. */
  def withEnemyAtOrigin(b: Board): Board = b.copy(ready = b.ready.updateCurrent(
    current => current.copy(players = current.players.map(p =>
      if (p.player == b.other) p.copy(pawnSite = Some(b.origin)) else p))))

  def rules(dice: WalkerDice = WalkerDice.unavailable,
      powers: Boolean = false): OathRules = new OathRules(catalog,
    walkerPowerCatalog =
      if (powers) WalkerPowerCatalog.default(catalog) else WalkerPowers.empty,
    walkerDice = dice)

  /** Dice that return exactly these faces, and fail loudly on a wrong count. */
  def dice(attack: Vector[AttackDieFace] = Vector.empty,
      defense: Vector[DefenseDieFace] = Vector.empty): WalkerDice =
    (kind, count) => kind match {
      case DiceKind.Attack => Either.cond(attack.size == count, attack,
        OathViolation.InvalidEventOrder(
          s"test dice: ${attack.size} attack faces for a pool of $count"))
      case DiceKind.Defense => Either.cond(defense.size == count, defense,
        OathViolation.InvalidEventOrder(
          s"test dice: ${defense.size} defense faces for a pool of $count"))
    }
}
```

Create `src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala` (Tasks 10 to 12 add tests to it):

```scala
package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture.{Board, board, rules, withEnemyAtOrigin}
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignProcedure}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerCompleted,
  WalkerPowers, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer._
import oathdigital.model.OathEvent.IgnoredRulesRecorded
import oathdigital.model.OathState.Ready

/** Campaign through the rules, as a client drives it. */
class CampaignProcedureSuite extends munit.FunSuite {
  private val r = rules()

  private def start(b: Board) =
    r.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor)

  private def answer(state: OathState, actor: PlayerId, id: String,
      answer: DecisionAnswer) = r.resolveWalker(state, actor, id, answer)

  private def ready(state: OathState): ReadyGame = state match {
    case Ready(value) => value
    case other => fail(s"expected a ready game, got $other")
  }

  private def button(key: String) =
    ChooseOneAnswer(DecisionOptionRef.Button(key))

  private def ops(events: Vector[OathEvent]): Vector[CoreOperation] =
    events.collect { case step: WalkerStepRecorded => step.ops }.flatten

  private def parkedDecision(b: Board, transition: OathTransition): Decide = {
    val pending = ready(transition.state).game.current.walkerPending.get
    val tree = CampaignProcedure.rebuild(catalog, ready(transition.state),
      b.actor, Vector.empty).toOption.get
    ProcedureWalker.openDecisions(ready(transition.state), tree, pending,
      WalkerPowers.empty).head
  }

  private def supply(state: OathState, id: PlayerId): Int =
    ready(state).game.current.players.find(_.player == id).get
      .board.supply.supply

  test("with one legal kind, no extra target and force to commit, the start parks on the force") {
    val b = board()
    val started = start(b).getOrElse(fail("Campaign must start"))
    assertEquals(started.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.force)))
    assertEquals(ready(started.state).game.current.pending, None)
    assertEquals(parkedDecision(b, started).query, DecisionQuery.ChooseAmount(0, 5,
      Some("Commit warbands to the Campaign: 0 to 5, each adds one attack die"),
      "Commit force"))
    assertEquals(supply(started.state, b.actor), 5)
  }

  test("extra same-ruler sites are offered as optional targets, in map order") {
    val b = board(extras = 2)
    val started = start(b).getOrElse(fail("Campaign must start"))
    assertEquals(started.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.targets)))
    assertEquals(parkedDecision(b, started).query, DecisionQuery.ChooseMany(0, 2,
      b.extras.map(site => DecisionOption.Site(DecisionOptionRef.Site(site))),
      Some("Also target these sites ruled by the same defender")))
    val chosen = answer(started.state, b.actor, CampaignIds.targets,
      ChooseManyAnswer(Vector(DecisionOptionRef.Site(b.extras.last)))).toOption.get
    assertEquals(chosen.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.force)))
  }

  test("the empty selection is a valid targets answer") {
    val b = board(extras = 1)
    val started = start(b).toOption.get
    assert(answer(started.state, b.actor, CampaignIds.targets,
      ChooseManyAnswer(Vector.empty)).isRight)
  }

  test("a pawn shared with an enemy offers both kinds; a lone enemy is the Raid defender") {
    val shared = withEnemyAtOrigin(board())
    val b = shared.copy(ready = shared.ready.updateCurrent(current =>
      current.copy(players = current.players.map(p =>
        if (p.player == shared.other) p.copy(relics = Vector(RelicState(
          RelicId("r-raid"), Orientation.FaceUp, Tokens.empty))) else p))))
    val started = start(b).toOption.get
    assertEquals(started.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.kind)))
    assertEquals(parkedDecision(b, started).query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(DecisionOptionRef.Button("conquest"), "Conquest"),
      DecisionOption.Button(DecisionOptionRef.Button("raid"), "Raid")),
      Some("Choose a Campaign")))
    val raid = answer(started.state, b.actor, CampaignIds.kind, button("raid"))
      .getOrElse(fail("the kind must be accepted"))
    // One enemy pawn: the defender is implied, so the Raid targets come next.
    assertEquals(raid.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.targets)))
  }

  test("with neither a ruled pawn site nor an enemy pawn, no Campaign starts or is offered") {
    val b = board()
    val unruled = b.ready.updateCurrent(current => current.copy(map =
      current.map.copy(sites = current.map.sites.updated(b.origin,
        current.map.sites(b.origin).copy(forces = SiteForces.Empty)))))
    val stuck = b.copy(ready = unruled)
    assert(start(stuck).left.toOption.exists(
      _.isInstanceOf[OathViolation.CampaignUnavailable]))
    assert(!CampaignProcedure.startable(catalog, stuck.ready, stuck.actor,
      WalkerPowers.empty))
    assert(CampaignProcedure.startable(catalog, b.ready, b.actor, WalkerPowers.empty))
  }

  test("a Campaign the actor cannot pay for is not offered and does not start") {
    val b = board(supply = 1)
    assert(start(b).isLeft)
    assert(!CampaignProcedure.startable(catalog, b.ready, b.actor,
      WalkerPowers.empty))
  }

  test("committing force gathers the attack pool and the printed defense pool") {
    val b = board()
    val started = start(b).toOption.get
    val done = answer(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(3)).getOrElse(fail("the force must be accepted"))
    val printed = catalog.sites.find(_.id == b.origin).get.defense
    val gathered = ops(done.events).collect { case pool: ModifyDicePool => pool }
    assertEquals(gathered, Vector(ModifyDicePool(CampaignIds.attackPool, 3)) ++
      Option.when(printed > 0)(ModifyDicePool(CampaignIds.defensePool, printed)))
    assert(done.events.exists(_.isInstanceOf[WalkerCompleted]))
  }

  test("zero force is legal and gathers no attack pool") {
    val b = board(warbands = 0)
    val started = start(b).toOption.get
    assertEquals(parkedDecision(b, started).query, DecisionQuery.ChooseAmount(0, 0,
      Some("Commit warbands to the Campaign: 0 to 0, each adds one attack die"),
      "Commit force"))
    val done = answer(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(0)).toOption.get
    assert(!ops(done.events).exists {
      case ModifyDicePool(pool, _, _) => pool == CampaignIds.attackPool
      case _ => false
    })
  }

  test("more force than the board holds is rejected") {
    val b = board(warbands = 2)
    val started = start(b).toOption.get
    assert(answer(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(3)).isLeft)
  }

  test("no first-game gate: an altered Foundation or a Citizen still campaigns") {
    val b = board()
    val campaign = b.ready.game.campaign
    val lineage = campaign.lineages(b.player(b.actor).lineage)
    val citizen = b.ready.copy(game = b.ready.game.copy(campaign =
      campaign.copy(lineages = campaign.lineages.updated(lineage.id,
        lineage.copy(role = Role.Citizen)))))
    assert(start(b.copy(ready = citizen)).isRight)
    val altered = b.ready.copy(game = b.ready.game.copy(campaign =
      campaign.copy(foundations = campaign.foundations.map { case (k, f) =>
        k -> f.copy(face = FoundationFace.Altered) })))
    assert(start(b.copy(ready = altered)).isRight)
  }

  test("a held Campaign power the engine does not run neither blocks the start nor goes unrecorded") {
    val b = board()
    val bag = catalog.relics.find(_.handlers.contains("relic.bag-of-siegeworks")).get
    val holding = b.ready.updateCurrent(current => current.copy(players =
      current.players.map(p => if (p.player == b.actor) p.copy(relics = Vector(
        RelicState(RelicId(bag.id.value), Orientation.FaceUp, Tokens.empty)))
      else p)))
    val started = start(b.copy(ready = holding)).getOrElse(
      fail("an unsupported handler must not block"))
    assert(started.events.exists {
      case IgnoredRulesRecorded(_, ActionKind.Campaign, diagnostics) =>
        diagnostics.exists(_.powerId == "relic.bag-of-siegeworks")
      case _ => false
    })
  }

  test("a faceup Vow of Peace stops the start, through the walker power catalog") {
    val b = board()
    val vow = catalog.denizens.find(_.handlers.contains("denizen.vow-of-peace")).get
    val holding = b.ready.updateCurrent(current => current.copy(players =
      current.players.map(p => if (p.player == b.actor) p.copy(advisers = Vector(
        DenizenState(DenizenId(vow.id.value), Orientation.FaceUp, Tokens.empty)))
      else p)))
    val withPowers = rules(powers = true)
    assertEquals(withPowers.startWalker(Ready(holding), ActionRef.Campaign, b.actor),
      Left(OathViolation.CampaignUnavailable(
        "Vow of Peace prevents its ruler from campaigning")))
  }
}
```

Create `src/test/scala/oathdigital/gameplay/CampaignSetupSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture.{board, withEnemyAtOrigin}
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignSetup}
import oathdigital.model._
import oathdigital.model.DecisionAnswer._

class CampaignSetupSuite extends munit.FunSuite {
  private def pending(answers: (String, DecisionAnswer)*) = PendingTree(
    Vector("0"), answers.toVector.map { case (id, a) =>
      Answered(id, a, PlayerId("actor")) })

  test("legal kinds follow the pawn site's ruler and the enemy pawns") {
    val b = board()
    assertEquals(CampaignSetup.legalKinds(b.ready, b.actor),
      Vector(CampaignKind.Conquest))
    assertEquals(CampaignSetup.legalKinds(withEnemyAtOrigin(b).ready, b.actor),
      Vector(CampaignKind.Conquest, CampaignKind.Raid))
  }

  test("a player never conquers their own site") {
    val b = board()
    val lineage = b.player(b.actor).lineage
    val own = b.ready.updateCurrent(current => current.copy(map =
      current.map.copy(sites = current.map.sites.updated(b.origin,
        current.map.sites(b.origin).copy(forces =
          SiteForces.Occupied(ForceKind.Exile(lineage), 1))))))
    assertEquals(CampaignSetup.conquestDefender(own, b.actor), None)
  }

  test("the setup is the mandatory site plus the answered extras, in map order, with the force") {
    val b = board(extras = 2)
    val last = DecisionOptionRef.Site(b.extras.last)
    val first = DecisionOptionRef.Site(b.extras.head)
    val setup = CampaignSetup.setup(b.ready, b.actor, pending(
      CampaignIds.targets -> ChooseManyAnswer(Vector(last, first)),
      CampaignIds.force -> ChooseAmountAnswer(4))).get
    assertEquals(setup.kind, CampaignKind.Conquest)
    assertEquals(setup.defender, CampaignDefender.Bandits)
    assertEquals(setup.targetSites, b.origin +: b.extras)
    assertEquals(setup.force, 4)
  }

  test("a Raid setup is the pawn first, then the chosen relic and banner in canonical order") {
    val b = withEnemyAtOrigin(board())
    val relic = RelicId("r-raid")
    val armed = b.ready.updateCurrent(current => current.copy(
      players = current.players.map(p => if (p.player == b.other) p.copy(
        relics = Vector(RelicState(relic, Orientation.FaceUp, Tokens.empty)))
      else p),
      banners = current.banners.copy(peoplesFavor =
        current.banners.peoplesFavor.copy(holder = Some(b.other)))))
    val setup = CampaignSetup.setup(armed, b.actor, pending(
      CampaignIds.kind -> ChooseOneAnswer(DecisionOptionRef.Button("raid")),
      CampaignIds.targets -> ChooseManyAnswer(Vector(
        DecisionOptionRef.Banner(Banner.PeoplesFavor),
        DecisionOptionRef.Relic(relic))),
      CampaignIds.force -> ChooseAmountAnswer(2))).get
    assertEquals(setup.raidTargets, Vector[CampaignRaidTarget](
      CampaignRaidTarget.Pawn(b.other), CampaignRaidTarget.Relic(b.other, relic),
      CampaignRaidTarget.Banner(b.other, Banner.PeoplesFavor)))
    assertEquals(setup.defender, CampaignDefender.Player(b.other))
  }

  test("Raid target options are the defender's faceup relics and held banners only") {
    val b = withEnemyAtOrigin(board())
    val up = RelicState(RelicId("r-up"), Orientation.FaceUp, Tokens.empty)
    val down = RelicState(RelicId("r-down"), Orientation.FaceDown, Tokens.empty)
    val armed = b.ready.updateCurrent(current => current.copy(
      players = current.players.map(p => if (p.player == b.other)
        p.copy(relics = Vector(up, down)) else p),
      banners = current.banners.copy(darkestSecret =
        current.banners.darkestSecret.copy(holder = Some(b.other)))))
    assertEquals(CampaignSetup.targetOptions(armed, b.actor, CampaignKind.Raid,
      CampaignDefender.Player(b.other)), Vector(
      DecisionOption.Relic(DecisionOptionRef.Relic(up.id)),
      DecisionOption.Banner(DecisionOptionRef.Banner(Banner.DarkestSecret))))
  }

  test("there is no setup before the force is answered") {
    val b = board()
    assertEquals(CampaignSetup.setup(b.ready, b.actor, pending()), None)
  }
}
```

In `ActionValuesSuite.scala` line 20 append `, "campaign"` after `"negotiation"`.

- [ ] **Step 2: Run to confirm they fail**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, `ActionRef.Campaign` and `oathdigital.gameplay.actions.campaign` do not exist.

- [ ] **Step 3: Registration vocabulary**

In `ProcedureRef.scala` add `case object Campaign extends ActionRef { val key = "campaign" }` after `Negotiation`, and append `Campaign` to `ActionRef.all`. In `GameProcedureProtocol.scala` add after `AwaitingNegotiation`:

```scala
  /** Any decision of a Campaign: the attacker's choices, and the defender's
    * battle plans, which are owned by the defender. `playerId` is the
    * decision's owner.
    */
  final case class AwaitingCampaignDecision(playerId: PlayerId,
      decision: DecisionId) extends OathContinue
```
In `WalkerProcedureRegistry.scala` import `oathdigital.gameplay.actions.campaign.CampaignProcedure` and add the entry after `ActionRef.Negotiation`:

```scala
    /** Campaign. Its first step spends the Supply, so a start runs an operation
      * before its first decision and cannot use the playable-option gate. No
      * roll parks: both dice rolls are automatic, so `rollDecisionId` is
      * `None`.
      */
    ActionRef.Campaign -> Entry(
      fallbackKind = Some(ActionKind.Campaign),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.CampaignModifierSelection),
      continuationFor = (decisionId, actor, decision) =>
        Option.when(CampaignProcedure.decisionIds.contains(decisionId))(
          OathContinue.AwaitingCampaignDecision(actor, decision)),
      build = CampaignProcedure.build,
      rebuild = CampaignProcedure.rebuild),
```

- [ ] **Step 4: `CampaignSetup.scala`**

```scala
package oathdigital.gameplay.actions.campaign

import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

/** Decision ids and pool keys of the Campaign procedure. */
object CampaignIds {
  val kind = "campaign.kind"
  val defender = "campaign.defender"
  val targets = "campaign.targets"
  val force = "campaign.force"
  val attackerPlan = "campaign.attacker-plan"
  val defenderPlan = "campaign.defender-plan"
  val sacrifice = "campaign.sacrifice"
  val placement = "campaign.placement"
  val relocation = "campaign.relocation"
  val all: Set[String] = Set(kind, defender, targets, force, attackerPlan,
    defenderPlan, sacrifice, placement, relocation)

  val attackPool: PoolKey = PoolKey("campaign.attack")
  val defensePool: PoolKey = PoolKey("campaign.defense")
  val SupplyCost = 2

  /** The option that ends a plan window. */
  val finish: DecisionOptionRef.Button = DecisionOptionRef.Button("finish")
}

/** What a Campaign's early answers say. `targetSites` is the mandatory origin
  * plus the answered additions in map order (Conquest); `raidTargets` is the
  * pawn plus the answered relics and banners in canonical order (Raid).
  */
final case class CampaignSetup(actor: PlayerId, kind: CampaignKind,
    origin: SiteId, defender: CampaignDefender, targetSites: Vector[SiteId],
    raidTargets: Vector[CampaignRaidTarget], force: Int)

object CampaignSetup {
  private def playerOf(ready: ReadyGame, id: PlayerId): Option[PlayerState] =
    ready.game.current.players.find(_.player == id)

  /** The actor's pawn site: the mandatory Conquest site, and where a Raid's
    * defender must stand.
    */
  def originOf(ready: ReadyGame, actor: PlayerId): Option[SiteId] =
    playerOf(ready, actor).flatMap(_.pawnSite)

  /** Who rules `site` as a Campaign defender. `None` when the site is
    * unruled, Imperial or its rule is corrupt.
    */
  def defenderAt(ready: ReadyGame, site: SiteId): Option[CampaignDefender] =
    ready.game.current.map.sites.get(site).flatMap(state =>
      SiteRule.ruler(state.forces, ready.game.current.players).toOption)
      .flatMap {
        case SiteRuler.Bandits => Some(CampaignDefender.Bandits)
        case SiteRuler.Player(player) => Some(CampaignDefender.Player(player))
        case _ => None
      }

  def conquestDefender(ready: ReadyGame, actor: PlayerId)
      : Option[CampaignDefender] =
    originOf(ready, actor).flatMap(defenderAt(ready, _))
      .filterNot(_ == CampaignDefender.Player(actor))

  def raidDefenders(ready: ReadyGame, actor: PlayerId): Vector[PlayerId] =
    originOf(ready, actor).toVector.flatMap(site =>
      ready.game.current.players.filter(p => p.player != actor &&
        p.pawnSite.contains(site)).map(_.player))

  def legalKinds(ready: ReadyGame, actor: PlayerId): Vector[CampaignKind] =
    Vector(
      Option.when(conquestDefender(ready, actor).nonEmpty)(
        CampaignKind.Conquest: CampaignKind),
      Option.when(raidDefenders(ready, actor).nonEmpty)(
        CampaignKind.Raid: CampaignKind)).flatten

  def kindOptions(ready: ReadyGame, actor: PlayerId): Vector[DecisionOption] =
    legalKinds(ready, actor).map {
      case CampaignKind.Conquest => DecisionOption.Button(
        DecisionOptionRef.Button("conquest"), "Conquest")
      case CampaignKind.Raid => DecisionOption.Button(
        DecisionOptionRef.Button("raid"), "Raid")
    }

  def defenderOptions(ready: ReadyGame, actor: PlayerId): Vector[DecisionOption] =
    raidDefenders(ready, actor).map(id =>
      DecisionOption.Player(DecisionOptionRef.Player(id)))

  /** The optional additions: Conquest sites ruled by the same defender, in map
    * order; a Raid's faceup relics and held banners of the defender.
    */
  def targetOptions(ready: ReadyGame, actor: PlayerId, kind: CampaignKind,
      defender: CampaignDefender): Vector[DecisionOption] = {
    val current = ready.game.current
    (kind, defender) match {
      case (CampaignKind.Conquest, _) =>
        val origin = originOf(ready, actor)
        current.map.inPlay.filter(site => !origin.contains(site) &&
          defenderAt(ready, site).contains(defender)).map(site =>
          DecisionOption.Site(DecisionOptionRef.Site(site)))
      case (CampaignKind.Raid, CampaignDefender.Player(id)) =>
        playerOf(ready, id).toVector.flatMap { held =>
          held.relics.filter(_.orientation == Orientation.FaceUp).map(relic =>
            DecisionOption.Relic(DecisionOptionRef.Relic(relic.id)): DecisionOption) ++
            Banner.all.filter(banner => BannerRules.holder(current, banner)
              .contains(id)).map(banner => DecisionOption.Banner(
                DecisionOptionRef.Banner(banner)): DecisionOption)
        }
      case _ => Vector.empty
    }
  }

  /** The chosen kind: the answer, or the only legal kind when the decision was
    * omitted.
    */
  def kindOf(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Option[CampaignKind] =
    CampaignAnswers.kind(pending).orElse(legalKinds(ready, actor) match {
      case Vector(only) => Some(only)
      case _ => None
    })

  def defenderOf(ready: ReadyGame, actor: PlayerId, pending: PendingTree,
      kind: CampaignKind): Option[CampaignDefender] = kind match {
    case CampaignKind.Conquest => conquestDefender(ready, actor)
    case CampaignKind.Raid => CampaignAnswers.raidDefender(pending)
      .orElse(raidDefenders(ready, actor) match {
        case Vector(only) => Some(only)
        case _ => None
      }).map(CampaignDefender.Player(_))
  }

  /** The complete setup, once the force is answered. */
  def setup(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Option[CampaignSetup] = for {
    kind <- kindOf(ready, actor, pending)
    origin <- originOf(ready, actor)
    defender <- defenderOf(ready, actor, pending, kind)
    force <- CampaignAnswers.force(pending)
  } yield {
    val picked = CampaignAnswers.targets(pending)
    kind match {
      case CampaignKind.Conquest =>
        val extras = picked.collect { case DecisionOptionRef.Site(id) => id }.toSet
        CampaignSetup(actor, kind, origin, defender, origin +:
          ready.game.current.map.inPlay.filter(site =>
            site != origin && extras(site)), Vector.empty, force)
      case CampaignKind.Raid =>
        val id = defender match {
          case CampaignDefender.Player(player) => player
          case CampaignDefender.Bandits => actor
        }
        val relics = picked.collect {
          case DecisionOptionRef.Relic(relic) =>
            CampaignRaidTarget.Relic(id, relic): CampaignRaidTarget }
        val banners = picked.collect {
          case DecisionOptionRef.Banner(banner) =>
            CampaignRaidTarget.Banner(id, banner): CampaignRaidTarget }
        CampaignSetup(actor, kind, origin, defender, Vector.empty,
          CampaignRaidTarget.canonical(
            Vector[CampaignRaidTarget](CampaignRaidTarget.Pawn(id)) ++
              relics ++ banners), force)
    }
  }
}

/** Readers over the answers recorded so far. The latest answer to a decision
  * wins, which is what a `Repeat` that re-asks one decision id relies on.
  */
object CampaignAnswers {
  private def latest(pending: PendingTree, id: String): Option[DecisionAnswer] =
    pending.answered.reverse.collectFirst { case Answered(`id`, answer, _) => answer }

  def kind(pending: PendingTree): Option[CampaignKind] =
    latest(pending, CampaignIds.kind).collect {
      case DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button(key)) => key
    }.flatMap {
      case "conquest" => Some(CampaignKind.Conquest)
      case "raid" => Some(CampaignKind.Raid)
      case _ => None
    }

  def raidDefender(pending: PendingTree): Option[PlayerId] =
    latest(pending, CampaignIds.defender).collect {
      case DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Player(id)) => id
    }

  def targets(pending: PendingTree): Vector[DecisionOptionRef] =
    latest(pending, CampaignIds.targets).toVector.flatMap {
      case DecisionAnswer.ChooseManyAnswer(selected) => selected
      case _ => Vector.empty
    }

  def force(pending: PendingTree): Option[Int] =
    latest(pending, CampaignIds.force).collect {
      case DecisionAnswer.ChooseAmountAnswer(amount) => amount
    }

  def sacrificed(pending: PendingTree): Int =
    latest(pending, CampaignIds.sacrifice).collect {
      case DecisionAnswer.ChooseAmountAnswer(amount) => amount
    }.getOrElse(0)

  /** Every source picked in `decisionId` so far, in order, without Finish. */
  def picks(pending: PendingTree, decisionId: String): Vector[DecisionOptionRef] =
    pending.answered.collect {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _)
          if ref != CampaignIds.finish => ref
    }

  def finished(pending: PendingTree, decisionId: String): Boolean =
    latest(pending, decisionId).contains(
      DecisionAnswer.ChooseOneAnswer(CampaignIds.finish))

  /** The site and count of each placement: one target answers with an amount,
    * several with a distribution.
    */
  def placements(pending: PendingTree, targets: Vector[SiteId])
      : Vector[(SiteId, Int)] = latest(pending, CampaignIds.placement) match {
    case Some(DecisionAnswer.ChooseAmountAnswer(count)) =>
      targets.headOption.toVector.map(_ -> count)
    case Some(DecisionAnswer.DistributeAnswer(amounts)) => amounts.collect {
      case DistributeAmount(DecisionOptionRef.Site(site), count) => site -> count
    }
    case _ => Vector.empty
  }

  def relocation(pending: PendingTree): Option[SiteId] =
    latest(pending, CampaignIds.relocation).collect {
      case DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Site(site)) => site
    }
}
```

- [ ] **Step 5: `CampaignBattle.scala` (pools) and `CampaignProcedure.scala`**

Create `CampaignBattle.scala` (Tasks 11 and 12 add to it):

```scala
package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** The battle arithmetic and its operations. */
object CampaignBattle {
  /** The printed defense dice of the targets: a Conquest's sites, or a Raid's
    * pawn (2), each targeted relic's printed defense and each banner (3).
    */
  def printedDefense(catalog: ExecutableCatalog, setup: CampaignSetup): Int =
    setup.kind match {
      case CampaignKind.Conquest => setup.targetSites
        .flatMap(site => catalog.sites.find(_.id == site)).map(_.defense).sum
      case CampaignKind.Raid => setup.raidTargets.map {
        case _: CampaignRaidTarget.Pawn => 2
        case CampaignRaidTarget.Relic(_, relic) => catalog.relics
          .find(_.id.value == relic.value).map(_.defense).getOrElse(0)
        case _: CampaignRaidTarget.Banner => 3
      }.sum
    }

  /** Both pools, gathered once the force is known. A pool of zero is not
    * created: an empty pool is never rolled.
    */
  def gatherPools(catalog: ExecutableCatalog, setup: CampaignSetup)
      : Vector[CoreOperation] = {
    val printed = printedDefense(catalog, setup)
    Vector[Option[CoreOperation]](
      Option.when(setup.force > 0)(
        ModifyDicePool(CampaignIds.attackPool, setup.force)),
      Option.when(printed > 0)(
        ModifyDicePool(CampaignIds.defensePool, printed))).flatten
  }
}
```

Create `CampaignProcedure.scala`:

```scala
package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathLifecycle, PowerRuntime}
import oathdigital.gameplay.walker.{WalkerPowers, WalkerSimulation}
import oathdigital.model._

/** Campaign on the walker, in the rulebook's order: choose the kind and the
  * targets, commit force and gather both dice pools, use battle plans, roll
  * the attack, sacrifice, roll the defense, declare the victor, kill the
  * defeated warbands, resolve the victory.
  *
  * Each early decision is built when reached from live state that nothing
  * before it has changed, plus the answers. After the losses run, every later
  * step reads the durable `CampaignResult` and the answers instead, because
  * the losses change the board.
  */
object CampaignProcedure {
  val decisionIds: Set[String] = CampaignIds.all

  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
    _ <- noStartArgs(args)
    _ <- OathLifecycle.validateAct(OathState.Ready(state), actor)
    _ <- PowerRuntime.requireAudited(catalog)
    _ <- Either.cond(CampaignSetup.legalKinds(state, actor).nonEmpty, (),
      OathViolation.CampaignUnavailable("Campaign needs a ruled pawn site to " +
        "Conquest or a co-located enemy pawn to Raid"))
  } yield tree(catalog, state, actor)

  /** Whether Campaign could start now: the gates pass and the first walk (the
    * Supply cost, up to the first decision) is accepted, restrictions
    * included.
    */
  def startable(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): Boolean =
    build(catalog, state, actor, Vector.empty)
      .exists(WalkerSimulation.starts(_, state, powers))

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    noStartArgs(args).map(_ => tree(catalog, state, actor))

  private def noStartArgs(args: Vector[DecisionOptionRef])
      : Either[OathViolation, Unit] = Either.cond(args.isEmpty, (),
    OathViolation.InvalidEventOrder(
      s"walker procedure ${ActionRef.Campaign.key} takes no start selection, " +
        s"got ${args.map(_.kind).mkString(", ")}"))

  private def tree(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Operation = Sequence(Vector[Operation](
    Sequence(Vector[Operation](SpendSupply(actor, CampaignIds.SupplyCost)),
      Some(PowerWindow.CampaignCost)),
    Sequence(Vector[Operation](kindStep(actor), defenderStep(actor),
      targetsStep(actor)), Some(PowerWindow.CampaignBeforeTargets)),
    forceStep(state, actor),
    Sequence(Vector[Operation](BuildOps((ready, pending) =>
      CampaignSetup.setup(ready, actor, pending).toRight(
        OathViolation.InvalidEventOrder(
          "Campaign gathered its dice pools without a complete setup"))
        .map(CampaignBattle.gatherPools(catalog, _)))),
      Some(PowerWindow.CampaignGatherPools))),
    Some(PowerWindow.CampaignActionEligibility))

  /** Omitted when exactly one kind is legal. */
  private def kindStep(actor: PlayerId): Operation = Branch((ready, _) => {
    val kinds = CampaignSetup.kindOptions(ready, actor)
    if (kinds.size < 2) Vector.empty
    else Vector(Decide(CampaignIds.kind, actor, DecisionQuery.ChooseOne(kinds,
      heading = Some("Choose a Campaign")),
      window = Some(PowerWindow.CampaignKindSelection)))
  })

  /** A Raid only, and only when several enemy pawns stand here. */
  private def defenderStep(actor: PlayerId): Operation = Branch((ready, pending) =>
    if (!CampaignSetup.kindOf(ready, actor, pending)
        .contains(CampaignKind.Raid)) Vector.empty
    else {
      val defenders = CampaignSetup.defenderOptions(ready, actor)
      if (defenders.size < 2) Vector.empty
      else Vector(Decide(CampaignIds.defender, actor, DecisionQuery.ChooseOne(
        defenders, heading = Some("Choose whom to Raid")),
        window = Some(PowerWindow.CampaignDefenderSelection)))
    })

  /** The optional additions to the mandatory target. */
  private def targetsStep(actor: PlayerId): Operation = Branch((ready, pending) =>
    (for {
      kind <- CampaignSetup.kindOf(ready, actor, pending)
      defender <- CampaignSetup.defenderOf(ready, actor, pending, kind)
      options = CampaignSetup.targetOptions(ready, actor, kind, defender)
      if options.nonEmpty
    } yield Vector[Operation](Decide(CampaignIds.targets, actor,
      DecisionQuery.ChooseMany(0, options.size, options, heading = Some(kind match {
        case CampaignKind.Conquest =>
          "Also target these sites ruled by the same defender"
        case CampaignKind.Raid =>
          "Also target the defender's faceup relics and banners"
      })), window = Some(PowerWindow.CampaignTargetSelection))))
      .getOrElse(Vector.empty))

  /** Always asked, even for zero: a Campaign with no force is legal, and the
    * confirmation states what it commits.
    */
  private def forceStep(state: ReadyGame, actor: PlayerId): Operation = {
    val warbands = state.game.current.players.find(_.player == actor)
      .fold(0)(_.board.warbands)
    Decide(CampaignIds.force, actor, DecisionQuery.ChooseAmount(0, warbands,
      Some(s"Commit warbands to the Campaign: 0 to $warbands, each adds one " +
        "attack die"), "Commit force"),
      window = Some(PowerWindow.CampaignForceSelection))
  }
}
```

- [ ] **Step 6: Run and commit**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignProcedureSuite oathdigital.gameplay.CampaignSetupSuite oathdigital.model.ActionValuesSuite oathdigital.model.ProcedureRefSuite oathdigital.gameplay.walker.WalkerProcedureRegistrySuite oathdigital.gameplay.CampaignPowersSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS. If `WalkerProcedureRegistrySuite` or another suite that enumerates `ActionRef.all` names the list of actions, extend it with `campaign`. Then `./sbtw "testOnly oathdigital.application.GameApplicationServiceSuite oathdigital.server.*"`: the legacy Campaign commands still work, and nothing offers the walker start yet.

```bash
git add -A src
git commit -m "feat(campaign): start Campaign on the walker up to the dice pools

ActionRef.Campaign is registered with the cost, the kind, the defender, the
optional targets and the force decisions, and gathers both dice pools once
the force is known. Nothing offers the start yet, and the rest of the tree
follows in the next tasks. The first-game gates do not exist here.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 10: Campaign on the walker, part 2: battle plans

The attacker's plan window, the defender's (owned by the defender when the defender is a player), and the automatic bandit plans. Plans are registry handlers that supply options and effects (spec: the registry stays the option and effect source; converting handlers to power contributions is deferred). The legacy `CampaignPlanRegistry` in `gameplay/actions/CampaignPlans.scala` keeps serving the legacy Campaign until Task 17, so this task writes a trimmed twin in the new package and does not touch the legacy file.

**Design decisions, all following walker rule W:**
- A plan window is `Sequence(window = CampaignAttackerBattlePlans | CampaignDefenderBattlePlans)` around a `Repeat`. The `Repeat` guard `!finished && plansAvailable` runs only at pass boundaries, so it may read live state: it stops when Finish was answered or no unused plan is left (Deviation 6: the window then finishes by itself).
- The body is `Sequence(Branch(decision), Branch(apply))`. The decision's options come from the live state at the moment it parks (earlier payments included) and are re-derived identically on resume. The apply step reads the latest answer, and its `BuildOps` finds the chosen option by reference in the *unfiltered* options and returns its cost payment, its reveal and its dice as one recorded batch. Finish applies nothing.
- Plan options are `Denizen` and `Relic` references (the projector names them and shows their rules text) plus a `Button("title")` and the `Button("finish")`. The card is the source; a source may be chosen once.
- The "ignore all skulls" effect is read later from the answered references (`ignoresSkulls`), because a plan's own option changes after it is paid.
- A bandit defender applies its cost-free, faceup, choice-free plans automatically in one `BuildOps`. A defender plan with a cost is never offered (none is registered).

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlans.scala`, `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanSteps.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala` (`CampaignAnswers.lastPick`), `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala` (the tree)
- Test: create `src/test/scala/oathdigital/gameplay/CampaignPlansSuite.scala`; modify `src/test/scala/oathdigital/gameplay/CampaignFixture.scala` and `src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala`

**Interfaces:**
- Produces: `CampaignPlanOption(ref, source, handlerId, side, costs, effects, order, label)` with `queryOption: DecisionOption`; `CampaignPlans.options(catalog, ready, setup, side, owner)`, `.available(catalog, ready, setup, side, owner, picked)`, `.banditPlans(catalog, ready, setup)`, `.apply(option, owner): Vector[CoreOperation]`, `.ignoresSkulls(catalog, picks): Boolean`; `CampaignAnswers.lastPick(pending, decisionId)`; `CampaignPlanSteps.attacker(catalog, actor)` and `.defender(catalog, actor)`.
- Consumes: `CampaignSetup`, `CampaignAnswers`, `CampaignIds` (Task 9), the top-level plan types (Task 6).
- Handlers ported from the legacy registry, with their exact rules: **Outriders** (`denizen.outriders`, attacker; from an adviser or a site card at a site the attacker rules or the origin; effects `IgnoreAttackSkulls`, preceded by `RevealSource` when the source is facedown). **Brass Army** (`relic.brass-army.campaign`, attacker; a faceup relic with no tokens while the owner has a faceup secret; cost `Secret(1)`, effect `AddAttackDice(4)`, the dice do not raise the physical force). **The title** (`title.oathkeeper-defense`, defender player holding the title: `AddDefenseDice(1)` Oathkeeper, `2` Usurper). **Watchdog** (`denizen.watchdog`, defender; effect `AddDefenseDice(1)` when a target site is in the Cradle).

- [ ] **Step 1: Extend the fixture and write the failing tests**

Add to `CampaignFixture.scala`:

```scala
  private def replacePlayer(b: Board, id: PlayerId)(f: PlayerState => PlayerState)
      : Board = b.copy(ready = b.ready.updateCurrent(current => current.copy(
    players = current.players.map(p => if (p.player == id) f(p) else p))))

  def withAdviser(b: Board, card: String, orientation: Orientation): Board =
    replacePlayer(b, b.actor)(p => p.copy(advisers = p.advisers :+
      DenizenState(DenizenId(card), orientation, Tokens.empty)))

  def withRelic(b: Board, relic: String): Board = replacePlayer(b, b.actor)(p =>
    p.copy(relics = p.relics :+ RelicState(RelicId(relic), Orientation.FaceUp,
      Tokens.empty)))

  def withSecrets(b: Board, faceUp: Int): Board = replacePlayer(b, b.actor)(p =>
    p.copy(board = p.board.copy(faceUpSecrets = faceUp)))

  /** The origin becomes ruled by the other player, who holds the title. */
  def againstPlayer(b: Board): Board = {
    val lineage = b.player(b.other).lineage
    b.copy(ready = b.ready.updateCurrent(current => current.copy(
      map = current.map.copy(sites = current.map.sites.updated(b.origin,
        current.map.sites(b.origin).copy(forces =
          SiteForces.Occupied(ForceKind.Exile(lineage), 2)))),
      title = current.title.copy(holder = Some(b.other),
        side = TitleSide.Oathkeeper))))
  }

  def withSiteCard(b: Board, site: SiteId, card: String): Board =
    b.copy(ready = b.ready.updateCurrent(current => current.copy(map =
      current.map.copy(sites = current.map.sites.updated(site,
        current.map.sites(site).copy(denizens = Vector(DenizenState(
          DenizenId(card), Orientation.FaceUp, Tokens.empty))))))))

  def cardWith(handler: String): String =
    catalog.denizens.find(_.handlers.contains(handler)).get.id.value
  def relicWith(handler: String): String =
    catalog.relics.find(_.handlers.contains(handler)).get.id.value
```

Create `src/test/scala/oathdigital/gameplay/CampaignPlansSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans, CampaignSetup}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class CampaignPlansSuite extends munit.FunSuite {
  private def setupOf(b: Board, force: Int = 2): CampaignSetup = CampaignSetup(
    b.actor, CampaignKind.Conquest, b.origin,
    CampaignSetup.conquestDefender(b.ready, b.actor).get, Vector(b.origin),
    Vector.empty, force)

  private val outriders = cardWith("denizen.outriders")
  private val brass = relicWith("relic.brass-army.campaign")
  private def attacker(b: Board) = CampaignPlans.options(catalog, b.ready,
    setupOf(b), CampaignPlanSide.Attacker, b.actor)

  test("Outriders from a faceup adviser ignores skulls, from a facedown one it reveals first") {
    val up = attacker(withAdviser(board(), outriders, Orientation.FaceUp)).head
    assertEquals(up.effects, Vector[CampaignPlanEffect](
      CampaignPlanEffect.IgnoreAttackSkulls))
    assertEquals(up.ref, DecisionOptionRef.Denizen(DenizenId(outriders)))
    val down = attacker(withAdviser(board(), outriders, Orientation.FaceDown)).head
    assertEquals(down.effects, Vector[CampaignPlanEffect](
      CampaignPlanEffect.RevealSource, CampaignPlanEffect.IgnoreAttackSkulls))
  }

  test("Brass Army needs a faceup relic without tokens and a faceup secret") {
    val ready = withSecrets(withRelic(board(), brass), 1)
    val option = attacker(ready).head
    assertEquals(option.costs, Vector[CampaignPlanCost](CampaignPlanCost.Secret(1)))
    assertEquals(option.effects, Vector[CampaignPlanEffect](
      CampaignPlanEffect.AddAttackDice(4)))
    assertEquals(attacker(withSecrets(withRelic(board(), brass), 0)), Vector.empty)
    assertEquals(attacker(board()), Vector.empty)
  }

  test("options are ordered adviser, relic, site card, then by stable key") {
    val both = withSecrets(withRelic(withAdviser(board(), outriders,
      Orientation.FaceUp), brass), 1)
    assertEquals(attacker(both).map(_.handlerId),
      Vector("denizen.outriders", "relic.brass-army.campaign"))
  }

  test("a defender plan with a cost is never offered") {
    val b = againstPlayer(board())
    val options = CampaignPlans.options(catalog, b.ready, setupOf(b).copy(
      defender = CampaignDefender.Player(b.other)), CampaignPlanSide.Defender,
      b.other)
    assert(options.forall(_.costs.isEmpty))
  }

  test("the title adds one defense die to an Oathkeeper and two to a Usurper") {
    val b = againstPlayer(board())
    def titled(side: TitleSide) = CampaignPlans.options(catalog,
      b.ready.updateCurrent(c => c.copy(title = c.title.copy(side = side))),
      setupOf(b).copy(defender = CampaignDefender.Player(b.other)),
      CampaignPlanSide.Defender, b.other).head
    assertEquals(titled(TitleSide.Oathkeeper).effects,
      Vector[CampaignPlanEffect](CampaignPlanEffect.AddDefenseDice(1)))
    assertEquals(titled(TitleSide.Usurper).effects,
      Vector[CampaignPlanEffect](CampaignPlanEffect.AddDefenseDice(2)))
    assertEquals(titled(TitleSide.Oathkeeper).ref, DecisionOptionRef.Button("title"))
  }

  test("only Outriders in the answers ignores skulls") {
    assertEquals(CampaignPlans.ignoresSkulls(catalog, Vector(
      DecisionOptionRef.Denizen(DenizenId(outriders)))), true)
    assertEquals(CampaignPlans.ignoresSkulls(catalog, Vector(
      DecisionOptionRef.Relic(RelicId(brass)))), false)
    assertEquals(CampaignPlans.ignoresSkulls(catalog, Vector.empty), false)
  }

  test("applying a plan pays its cost onto the card, reveals a facedown source and adds dice") {
    val b = withSecrets(withRelic(board(), brass), 1)
    val option = attacker(b).head
    assertEquals(CampaignPlans.apply(option, b.actor), Vector[CoreOperation](
      Move(Piece.Secrets(1), PositionedLocation(Location.PlayArea(b.actor)),
        PositionedLocation(Location.OnCard(RelicId(brass)))),
      ModifyDicePool(CampaignIds.attackPool, 4)))
    val down = withAdviser(board(), outriders, Orientation.FaceDown)
    assertEquals(CampaignPlans.apply(attacker(down).head, down.actor),
      Vector[CoreOperation](Move(Piece.Card(DenizenId(outriders)),
        PositionedLocation(Location.PlayArea(down.actor)),
        PositionedLocation(Location.PlayArea(down.actor)),
        resultingOrientation = Some(Orientation.FaceUp))))
  }
}
```

Append these tests to `CampaignProcedureSuite.scala` (inside the class; add `import oathdigital.gameplay.CampaignFixture._` names `withAdviser, withRelic, withSecrets, againstPlayer, withSiteCard, cardWith, relicWith` to the fixture import and `oathdigital.gameplay.actions.campaign.CampaignPlans` if needed):

```scala
  private val outriders = cardWith("denizen.outriders")
  private val brass = relicWith("relic.brass-army.campaign")
  private def planPick(ref: DecisionOptionRef) = ChooseOneAnswer(ref)
  private val finish = ChooseOneAnswer(CampaignIds.finish)

  private def atPlans(b: Board, force: Int = 2): OathTransition = {
    val started = start(b).toOption.get
    answer(started.state, b.actor, CampaignIds.force, ChooseAmountAnswer(force))
      .getOrElse(fail("the force must be accepted"))
  }

  test("an attacker plan is offered after the force, with Finish, and a pick applies its effects") {
    val b = withSecrets(withRelic(board(), brass), 2)
    val plans = atPlans(b)
    assertEquals(plans.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.attackerPlan)))
    assertEquals(parkedDecision(b, plans).query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Relic(DecisionOptionRef.Relic(RelicId(brass))),
      DecisionOption.Button(CampaignIds.finish, "Finish battle plans")),
      Some("Choose a battle plan, or finish")))
    val picked = answer(plans.state, b.actor, CampaignIds.attackerPlan,
      planPick(DecisionOptionRef.Relic(RelicId(brass)))).toOption.get
    assert(ops(picked.events).contains(ModifyDicePool(CampaignIds.attackPool, 4)))
    assert(ops(picked.events).contains(Move(Piece.Secrets(1),
      PositionedLocation(Location.PlayArea(b.actor)),
      PositionedLocation(Location.OnCard(RelicId(brass))))))
    // Nothing else can be chosen, so the window finishes by itself.
    assert(picked.events.exists(_.isInstanceOf[WalkerCompleted]))
  }

  test("two plans are chosen one at a time, each source once, and Finish ends the window") {
    val b = withSecrets(withRelic(withAdviser(board(), outriders,
      Orientation.FaceUp), brass), 1)
    val plans = atPlans(b)
    val first = answer(plans.state, b.actor, CampaignIds.attackerPlan,
      planPick(DecisionOptionRef.Denizen(DenizenId(outriders)))).toOption.get
    assertEquals(first.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.attackerPlan)))
    assertEquals(parkedDecision(b, first).query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Relic(DecisionOptionRef.Relic(RelicId(brass))),
      DecisionOption.Button(CampaignIds.finish, "Finish battle plans")),
      Some("Choose a battle plan, or finish")))
    val done = answer(first.state, b.actor, CampaignIds.attackerPlan, finish).toOption.get
    assertEquals(ops(done.events).collect { case pool: ModifyDicePool => pool }
      .filter(_.pool == CampaignIds.attackPool), Vector.empty)
  }

  test("a plan already chosen is rejected when chosen again") {
    val b = withSecrets(withRelic(withAdviser(board(), outriders,
      Orientation.FaceUp), brass), 1)
    val first = answer(atPlans(b).state, b.actor, CampaignIds.attackerPlan,
      planPick(DecisionOptionRef.Denizen(DenizenId(outriders)))).toOption.get
    assert(answer(first.state, b.actor, CampaignIds.attackerPlan,
      planPick(DecisionOptionRef.Denizen(DenizenId(outriders)))).isLeft)
  }

  test("a facedown Outriders is revealed when chosen") {
    val b = withAdviser(board(), outriders, Orientation.FaceDown)
    val done = answer(atPlans(b).state, b.actor, CampaignIds.attackerPlan,
      planPick(DecisionOptionRef.Denizen(DenizenId(outriders)))).toOption.get
    assert(ops(done.events).contains(Move(Piece.Card(DenizenId(outriders)),
      PositionedLocation(Location.PlayArea(b.actor)),
      PositionedLocation(Location.PlayArea(b.actor)),
      resultingOrientation = Some(Orientation.FaceUp))))
  }

  test("with no plan available the attacker window is skipped") {
    val plans = atPlans(board())
    assert(plans.events.exists(_.isInstanceOf[WalkerCompleted]))
  }

  test("a player defender owns the defender window and the attacker cannot answer it") {
    val b = againstPlayer(board())
    val plans = atPlans(b)
    assertEquals(plans.continue, OathContinue.AwaitingCampaignDecision(b.other,
      DecisionId(CampaignIds.defenderPlan)))
    assertEquals(parkedDecision(b, plans).query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(DecisionOptionRef.Button("title"),
        "Oathkeeper title: add 1 defense die"),
      DecisionOption.Button(CampaignIds.finish, "Finish battle plans")),
      Some("Defender: choose a battle plan, or finish")))
    assert(answer(plans.state, b.actor, CampaignIds.defenderPlan, finish).isLeft)
    val picked = answer(plans.state, b.other, CampaignIds.defenderPlan,
      planPick(DecisionOptionRef.Button("title"))).toOption.get
    assert(ops(picked.events).contains(ModifyDicePool(CampaignIds.defensePool, 1)))
  }

  test("a bandit defender applies its cost-free plans by itself") {
    val watchdog = cardWith("denizen.watchdog")
    val base = board()
    assume(base.ready.game.current.map.regionOf(base.origin).contains(Region.Cradle),
      "the fixture's origin must be in the Cradle for Watchdog")
    val b = withSiteCard(base, base.origin, watchdog)
    val done = atPlans(b)
    assert(ops(done.events).contains(ModifyDicePool(CampaignIds.defensePool, 1)))
    assert(done.events.exists(_.isInstanceOf[WalkerCompleted]))
  }
```
(The Watchdog test assumes the fixture's origin is a Cradle site. If it is not, the `assume` skips it; then choose the origin explicitly in `CampaignFixture.board` by preferring a Cradle site, and remove the `assume`.)

- [ ] **Step 2: Run to confirm they fail**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, `CampaignPlans` is not defined.

- [ ] **Step 3: `CampaignPlans.scala`**

```scala
package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** One battle plan a player could choose now. `ref` is what a decision option
  * names: a card, or the title's button. */
final case class CampaignPlanOption(ref: DecisionOptionRef,
    source: CampaignPlanSource, handlerId: String, side: CampaignPlanSide,
    costs: Vector[CampaignPlanCost], effects: Vector[CampaignPlanEffect],
    order: Int, label: String) {
  /** The option the plan's decision offers. A card is named by the projector;
    * the title has no card, so its button carries the authored label. */
  def queryOption: DecisionOption = source match {
    case CampaignPlanSource.Title(_) =>
      DecisionOption.Button(DecisionOptionRef.Button("title"), label)
    case _ => DecisionOption.forRef(ref).getOrElse(
      throw new IllegalStateException(s"no option for plan ref $ref"))
  }
}

/** The registered Campaign battle plans: what a source offers, what choosing
  * it costs and what it changes. Handlers own printed availability, costs and
  * effects; Campaign orchestrates the windows.
  */
object CampaignPlans {
  val Outriders = "denizen.outriders"
  val BrassArmy = "relic.brass-army.campaign"
  val Title = "title.oathkeeper-defense"
  val Watchdog = "denizen.watchdog"

  private final case class Found(source: CampaignPlanSource,
      handlers: Vector[String], facedown: Boolean, order: Int)

  private def denizenHandlers(catalog: ExecutableCatalog, id: DenizenId) =
    catalog.denizens.find(_.id.value == id.value).toVector.flatMap(_.handlers)
  private def relicHandlers(catalog: ExecutableCatalog, id: RelicId) =
    catalog.relics.find(_.id.value == id.value).toVector.flatMap(_.handlers)

  /** Every card `owner` could use as a plan source: their advisers, their
    * faceup relics, and the denizens at the origin and at sites they rule.
    */
  private def found(catalog: ExecutableCatalog, ready: ReadyGame,
      owner: PlayerId, origin: SiteId): Vector[Found] = {
    val current = ready.game.current
    val player = current.players.find(_.player == owner)
    val advisers = player.toVector.flatMap(_.advisers.collect {
      case card: DenizenState => Found(CampaignPlanSource.Adviser(owner, card.id),
        denizenHandlers(catalog, card.id),
        card.orientation == Orientation.FaceDown, 100)
    })
    val relics = player.toVector.flatMap(_.relics
      .filter(_.orientation == Orientation.FaceUp).map(relic => Found(
        CampaignPlanSource.Relic(owner, relic.id),
        relicHandlers(catalog, relic.id), false, 200)))
    val ruled = current.map.inPlay.filter(site => SiteRule.ruledBy(
      current.map.sites(site).forces, current.players, owner).getOrElse(false))
    val siteCards = (origin +: ruled).distinct.flatMap(site =>
      current.map.sites.get(site).toVector.flatMap(_.denizens.collect {
        case card: DenizenState => Found(CampaignPlanSource.SiteCard(site, card.id),
          denizenHandlers(catalog, card.id),
          card.orientation == Orientation.FaceDown, 300)
      }))
    advisers ++ relics ++ siteCards
  }

  private def refOf(source: CampaignPlanSource): DecisionOptionRef = source match {
    case CampaignPlanSource.Adviser(_, id) => DecisionOptionRef.Denizen(id)
    case CampaignPlanSource.SiteCard(_, id) => DecisionOptionRef.Denizen(id)
    case CampaignPlanSource.Relic(_, id) => DecisionOptionRef.Relic(id)
    case CampaignPlanSource.Title(_) => DecisionOptionRef.Button("title")
  }

  private def inCradle(ready: ReadyGame, setup: CampaignSetup): Boolean =
    setup.targetSites.exists(site =>
      ready.game.current.map.regionOf(site).contains(Region.Cradle))

  private def plan(ready: ReadyGame, setup: CampaignSetup, side: CampaignPlanSide,
      owner: PlayerId, from: Found, handler: String): Option[CampaignPlanOption] = {
    def option(label: String, costs: Vector[CampaignPlanCost],
        effects: Vector[CampaignPlanEffect]) = CampaignPlanOption(
      refOf(from.source), from.source, handler, side, costs, effects,
      from.order, label)
    handler match {
      case Outriders if side == CampaignPlanSide.Attacker && owner == setup.actor =>
        Some(option("Outriders: ignore all attack skulls", Vector.empty,
          (if (from.facedown) Vector[CampaignPlanEffect](
            CampaignPlanEffect.RevealSource) else Vector.empty) :+
            CampaignPlanEffect.IgnoreAttackSkulls))
      case BrassArmy if side == CampaignPlanSide.Attacker && owner == setup.actor =>
        from.source match {
          case CampaignPlanSource.Relic(player, relicId)
              if ready.game.current.players.find(_.player == player).exists(p =>
                p.board.faceUpSecrets >= 1 && p.relics.exists(r =>
                  r.id == relicId && r.orientation == Orientation.FaceUp &&
                    r.tokens.isEmpty)) =>
            Some(option("Brass Army: add 4 attack dice",
              Vector(CampaignPlanCost.Secret(1)),
              Vector(CampaignPlanEffect.AddAttackDice(4))))
          case _ => None
        }
      case Watchdog if side == CampaignPlanSide.Defender && inCradle(ready, setup) =>
        Some(option("Watchdog: add 1 defense die", Vector.empty,
          Vector(CampaignPlanEffect.AddDefenseDice(1))))
      case _ => None
    }
  }

  private def title(ready: ReadyGame, setup: CampaignSetup, owner: PlayerId)
      : Option[CampaignPlanOption] = {
    val held = ready.game.current.title
    Option.when(held.holder.contains(owner) &&
        setup.defender == CampaignDefender.Player(owner)) {
      val dice = held.side match {
        case TitleSide.Oathkeeper => 1
        case TitleSide.Usurper => 2
      }
      CampaignPlanOption(DecisionOptionRef.Button("title"),
        CampaignPlanSource.Title(owner), Title, CampaignPlanSide.Defender,
        Vector.empty, Vector(CampaignPlanEffect.AddDefenseDice(dice)), 0,
        s"${held.side} title: add $dice defense ${if (dice == 1) "die" else "dice"}")
    }
  }

  /** The plans `owner` could choose on `side`, in stable order. A defender plan
    * with a cost is never offered: none is registered, and a defender has no
    * cost-paying step.
    */
  def options(catalog: ExecutableCatalog, ready: ReadyGame, setup: CampaignSetup,
      side: CampaignPlanSide, owner: PlayerId): Vector[CampaignPlanOption] = {
    val printed = found(catalog, ready, owner, setup.origin).flatMap(source =>
      source.handlers.flatMap(handler =>
        plan(ready, setup, side, owner, source, handler)))
    val all = (if (side == CampaignPlanSide.Defender)
      title(ready, setup, owner).toVector else Vector.empty) ++ printed
    all.filter(o => o.side == CampaignPlanSide.Attacker || o.costs.isEmpty)
      .sortBy(o => (o.order, o.source.stableKey, o.handlerId))
  }

  /** The plans still unchosen: a source may be chosen once. */
  def available(catalog: ExecutableCatalog, ready: ReadyGame, setup: CampaignSetup,
      side: CampaignPlanSide, owner: PlayerId, picked: Vector[DecisionOptionRef])
      : Vector[CampaignPlanOption] =
    options(catalog, ready, setup, side, owner).filterNot(o => picked.contains(o.ref))

  /** A bandit defender uses every cost-free plan of a faceup bandit-ruled site
    * card, without choosing.
    */
  def banditPlans(catalog: ExecutableCatalog, ready: ReadyGame,
      setup: CampaignSetup): Vector[CampaignPlanOption] =
    if (setup.defender != CampaignDefender.Bandits) Vector.empty
    else {
      val current = ready.game.current
      current.map.inPlay.filter(site => CampaignSetup.defenderAt(ready, site)
        .contains(CampaignDefender.Bandits)).flatMap(site =>
        current.map.sites(site).denizens.collect {
          case card: DenizenState if card.orientation == Orientation.FaceUp =>
            Found(CampaignPlanSource.SiteCard(site, card.id),
              denizenHandlers(catalog, card.id), false, 300)
        }).flatMap(source => source.handlers.flatMap(handler => plan(ready,
        setup, CampaignPlanSide.Defender, setup.actor, source, handler)))
        .filter(_.costs.isEmpty)
        .sortBy(o => (o.order, o.source.stableKey, o.handlerId))
    }

  /** Whether an answered pick was Outriders. Read from the answers because the
    * plan's own option changes once it is paid.
    */
  def ignoresSkulls(catalog: ExecutableCatalog,
      picks: Vector[DecisionOptionRef]): Boolean = picks.exists {
    case DecisionOptionRef.Denizen(id) =>
      denizenHandlers(catalog, id).contains(Outriders)
    case _ => false
  }

  /** The operations that pay for and apply a plan: the cost moves onto the
    * source card, a facedown source is revealed, and dice join their pool.
    */
  def apply(option: CampaignPlanOption, owner: PlayerId): Vector[CoreOperation] = {
    val card: Option[CardId] = option.source match {
      case CampaignPlanSource.Adviser(_, id) => Some(id)
      case CampaignPlanSource.SiteCard(_, id) => Some(id)
      case CampaignPlanSource.Relic(_, id) => Some(id)
      case CampaignPlanSource.Title(_) => None
    }
    val payments: Vector[CoreOperation] = card.toVector.flatMap(target =>
      option.costs.map {
        case CampaignPlanCost.Favor(count) => Move(Piece.Favor(count),
          PositionedLocation(Location.PlayArea(owner)),
          PositionedLocation(Location.OnCard(target)))
        case CampaignPlanCost.Secret(count) => Move(Piece.Secrets(count),
          PositionedLocation(Location.PlayArea(owner)),
          PositionedLocation(Location.OnCard(target)))
      })
    val reveal: Vector[CoreOperation] =
      if (!option.effects.contains(CampaignPlanEffect.RevealSource)) Vector.empty
      else option.source match {
        case CampaignPlanSource.Adviser(player, id) => Vector(Move(Piece.Card(id),
          PositionedLocation(Location.PlayArea(player)),
          PositionedLocation(Location.PlayArea(player)),
          resultingOrientation = Some(Orientation.FaceUp)))
        case CampaignPlanSource.SiteCard(site, id) =>
          Vector(Reveal(id, Location.Site(site)))
        case _ => Vector.empty
      }
    val dice: Vector[CoreOperation] = option.effects.collect {
      case CampaignPlanEffect.AddAttackDice(count) =>
        ModifyDicePool(CampaignIds.attackPool, count): CoreOperation
      case CampaignPlanEffect.AddDefenseDice(count) =>
        ModifyDicePool(CampaignIds.defensePool, count): CoreOperation
    }
    payments ++ reveal ++ dice
  }
}
```

- [ ] **Step 4: The plan steps and the tree**

In `CampaignSetup.scala`, add to `CampaignAnswers`:

```scala
  /** The latest source picked in `decisionId`, or `None` when the latest answer
    * was Finish or there is none.
    */
  def lastPick(pending: PendingTree, decisionId: String): Option[DecisionOptionRef] =
    latest(pending, decisionId).collect {
      case DecisionAnswer.ChooseOneAnswer(ref) if ref != CampaignIds.finish => ref
    }
```

Create `CampaignPlanSteps.scala`:

```scala
package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** The two battle-plan windows: the attacker's, then the defender's. Each is a
  * `Repeat` of a decision and its application, so each plan is paid and applied
  * the moment it is chosen and the next options see the result.
  */
private[campaign] object CampaignPlanSteps {
  private def decisionId(side: CampaignPlanSide): String = side match {
    case CampaignPlanSide.Attacker => CampaignIds.attackerPlan
    case CampaignPlanSide.Defender => CampaignIds.defenderPlan
  }

  private def ownerOf(setup: CampaignSetup, side: CampaignPlanSide): Option[PlayerId] =
    side match {
      case CampaignPlanSide.Attacker => Some(setup.actor)
      case CampaignPlanSide.Defender => setup.defender match {
        case CampaignDefender.Player(player) => Some(player)
        case CampaignDefender.Bandits => None
      }
    }

  def attacker(catalog: ExecutableCatalog, actor: PlayerId): Operation =
    Sequence(Vector[Operation](loop(catalog, actor, CampaignPlanSide.Attacker)),
      Some(PowerWindow.CampaignAttackerBattlePlans))

  /** A player defender chooses plans; a bandit defender applies its own. */
  def defender(catalog: ExecutableCatalog, actor: PlayerId): Operation =
    Sequence(Vector[Operation](Branch((ready, pending) =>
      CampaignSetup.setup(ready, actor, pending) match {
        case Some(setup) if setup.defender == CampaignDefender.Bandits =>
          Vector(BuildOps((state, tree) => CampaignSetup.setup(state, actor, tree)
            .toRight(OathViolation.InvalidEventOrder(
              "Campaign reached the defender's plans without a setup"))
            .map(found => CampaignPlans.banditPlans(catalog, state, found)
              .flatMap(CampaignPlans.apply(_, actor)))))
        case Some(_) => Vector(loop(catalog, actor, CampaignPlanSide.Defender))
        case None => Vector.empty
      })), Some(PowerWindow.CampaignDefenderBattlePlans))

  private def loop(catalog: ExecutableCatalog, actor: PlayerId,
      side: CampaignPlanSide): Operation = {
    val id = decisionId(side)
    Repeat((ready, pending) => !CampaignAnswers.finished(pending, id) &&
        remaining(catalog, ready, actor, pending, side).nonEmpty,
      Sequence(Vector[Operation](
        Branch((ready, pending) => decision(catalog, ready, actor, pending, side)),
        Branch((ready, pending) => application(catalog, actor, pending, side)))))
  }

  private def remaining(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, pending: PendingTree, side: CampaignPlanSide)
      : Vector[CampaignPlanOption] = (for {
    setup <- CampaignSetup.setup(ready, actor, pending)
    owner <- ownerOf(setup, side)
  } yield CampaignPlans.available(catalog, ready, setup, side, owner,
    CampaignAnswers.picks(pending, decisionId(side)))).getOrElse(Vector.empty)

  private def decision(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, pending: PendingTree, side: CampaignPlanSide)
      : Vector[Operation] = (for {
    setup <- CampaignSetup.setup(ready, actor, pending)
    owner <- ownerOf(setup, side)
    options = remaining(catalog, ready, actor, pending, side)
    if options.nonEmpty
  } yield Vector[Operation](Decide(decisionId(side), owner,
    DecisionQuery.ChooseOne(options.map(_.queryOption) :+
      DecisionOption.Button(CampaignIds.finish, "Finish battle plans"),
      heading = Some(side match {
        case CampaignPlanSide.Attacker => "Choose a battle plan, or finish"
        case CampaignPlanSide.Defender =>
          "Defender: choose a battle plan, or finish"
      }))))).getOrElse(Vector.empty)

  /** Applies the latest pick. The chosen plan is found in the unfiltered
    * options, which still offer it: nothing changed since it was chosen.
    */
  private def application(catalog: ExecutableCatalog, actor: PlayerId,
      pending: PendingTree, side: CampaignPlanSide): Vector[Operation] =
    CampaignAnswers.lastPick(pending, decisionId(side)).toVector.map(pick =>
      BuildOps((ready, tree) => (for {
        setup <- CampaignSetup.setup(ready, actor, tree)
        owner <- ownerOf(setup, side)
        option <- CampaignPlans.options(catalog, ready, setup, side, owner)
          .find(_.ref == pick)
      } yield CampaignPlans.apply(option, owner)).toRight(
        OathViolation.InvalidEventOrder(
          "a chosen Campaign battle plan is no longer available"))))
}
```
(`BuildOps(...)` in a `Vector.map` yields `Vector[BuildOps]`, which is a `Vector[Operation]`.)

In `CampaignProcedure.tree` add the two windows after the pools step (before the closing `Some(...ActionEligibility)`):

```scala
    Sequence(Vector[Operation](BuildOps(...gather...)), Some(PowerWindow.CampaignGatherPools)),
    CampaignPlanSteps.attacker(catalog, actor),
    CampaignPlanSteps.defender(catalog, actor)),
    Some(PowerWindow.CampaignActionEligibility))
```
(keep the existing gather `Sequence` exactly and add a comma and the two steps.)

- [ ] **Step 5: Run and commit**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignPlansSuite oathdigital.gameplay.CampaignProcedureSuite oathdigital.gameplay.CampaignSetupSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS.

```bash
git add -A src
git commit -m "feat(campaign): battle plans on the walker

The attacker's plan window, the defender's (owned by the defender when a
player), and the automatic cost-free bandit plans. Each plan is paid and
applied the moment it is chosen, a source may be chosen once, and the window
finishes by itself when nothing is left to choose. Outriders' effect is read
from the answers because a paid plan's own option changes.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 11: Campaign on the walker, part 3: the battle and a Conquest victory

The attack roll, the recorded attack result, the sacrifice, the defense roll, the recorded defense result, the durable result, and the outcome: attacker and defender losses, and the Conquest placement. Raid's resolution is Task 12; this task leaves that arm empty.

**Design decisions, all following walker rule W:**
- Both rolls are `Roll(..., RollMode.Automatic)` (Task 4). Neither parks. The attack roll happens in the command that answers the last plan window; the defense roll in the command that answers the sacrifice.
- `CampaignAttackResult` writes the capped attack with `ModifyRollOutcome`: a skull removes one force warband, its two swords count only when that loss can be paid, skulls beyond the force add nothing, Outriders ignores all skulls. `CampaignDefenseResult` writes `defense score = dice score + the defender's force` the same way, so **victory reads recorded outcomes only**: the defender's force cannot be read after the losses (Deviation 5).
- `RecordCampaignResult` (window `CampaignAfterOutcome`) writes the public `CampaignResult`, including `victorious = attackTotal > defenseScore`.
- The outcome `Branch` reads only `lastCampaignResult`. It is the first node re-selected after the losses have changed the board, and the record is written before it and never changes, so the selection is stable across resume (this is why the fact is a state fact). It selects `losses` and, on victory, the Conquest placement.
- Nothing leaves the board before `CampaignLosses`. Attacker deaths are `skulls + sacrificed`, plus half the survivors rounded down on defeat; the rest of the committed force simply stays on the board. On a victory every warband at every target is killed, and a player defender's survivors (half the total, rounded up) return from the supply to their board before the attacker places.
- Placement: `ChooseAmount(0, survivors)` for one target and `Distribute(minTotal = 0, maxTotal = survivors)` for several (Deviation 2). It is omitted when nothing survives.

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignOutcome.scala`, `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignConquest.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignBattle.scala`, `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala`
- Test: create `src/test/scala/oathdigital/gameplay/CampaignBattleSuite.scala`; modify `src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala` and `src/test/scala/oathdigital/gameplay/CampaignFixture.scala`

**Interfaces:**
- Produces: `CampaignBattle.attackResult(faces, force, ignoreSkulls): (score, skulls)`, `.attackResultOps`, `.defenderForce`, `.defenseResultOps`, `.sacrificeMax`, `.sacrificeHeading`, `.result`, `.losses`; `CampaignConquest.steps(actor, result)`; `CampaignOutcome.steps(ready, catalog, actor, result)`.
- Consumes: Tasks 3 to 5 (attack outcomes, automatic rolls, `RecordCampaignResult`), Tasks 9 and 10.

- [ ] **Step 1: Fixture and failing tests**

In `CampaignFixture.board`, keep the bank consistent when sites are cleared or filled (otherwise the boundary's Bandit refill cannot find warbands): replace the final `val ready = ...` with a version that also adjusts the supply by the Bandit count the map lost or gained:

```scala
    def bandits(forces: SiteForces): Int = forces match {
      case SiteForces.Occupied(ForceKind.Bandit, count) => count
      case _ => 0
    }
    val delta = current.map.sites.values.map(s => bandits(s.forces)).sum -
      sites.values.map(s => bandits(s.forces)).sum
    val ready = base.updateCurrent(_.copy(players = players, pending = None,
      map = current.map.copy(sites = sites),
      turn = current.turn.copy(phase = Phase.Act))).copy(banks =
      base.banks.copy(warbandSupply = base.banks.warbandSupply.updated(
        ForceKind.Bandit, base.banks.warbandSupply.getOrElse(ForceKind.Bandit, 0) + delta)))
```

Create `src/test/scala/oathdigital/gameplay/CampaignBattleSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.campaign.CampaignBattle
import oathdigital.model._

class CampaignBattleSuite extends munit.FunSuite {
  import AttackDieFace._

  test("a skull costs a warband and its swords only when the warband can be paid") {
    assertEquals(CampaignBattle.attackResult(Vector(OneSword, OneSword), 2,
      ignoreSkulls = false), (2, 0))
    assertEquals(CampaignBattle.attackResult(Vector(TwoSwordsSkull, OneSword), 2,
      ignoreSkulls = false), (3, 1))
    // Two skulls but one warband: only one skull can be paid, so only its
    // swords count; the other adds nothing.
    assertEquals(CampaignBattle.attackResult(Vector(TwoSwordsSkull,
      TwoSwordsSkull), 1, ignoreSkulls = false), (2, 1))
    assertEquals(CampaignBattle.attackResult(Vector(TwoSwordsSkull,
      TwoSwordsSkull), 0, ignoreSkulls = false), (0, 0))
  }

  test("Outriders ignores every skull and keeps every sword") {
    assertEquals(CampaignBattle.attackResult(Vector(TwoSwordsSkull,
      TwoSwordsSkull), 1, ignoreSkulls = true), (4, 0))
  }

  test("hollow swords score one per pair") {
    assertEquals(CampaignBattle.attackResult(Vector(HollowSword, HollowSword,
      HollowSword), 3, ignoreSkulls = false), (1, 0))
  }

  test("the sacrifice heading names the faces, the total and the survivors") {
    assertEquals(CampaignBattle.sacrificeHeading(Vector(TwoSwordsSkull, OneSword),
      score = 3, skulls = 1, max = 1),
      "Attack roll: two swords and a skull, one sword. Attack 3 with 1 skull " +
        "loss. Sacrifice up to 1 warband for one attack each.")
    assertEquals(CampaignBattle.sacrificeHeading(Vector(OneSword, OneSword),
      score = 2, skulls = 0, max = 2),
      "Attack roll: one sword, one sword. Attack 2 with 0 skull losses. " +
        "Sacrifice up to 2 warbands for one attack each.")
  }
}
```

Append to `CampaignProcedureSuite.scala`:

```scala
  // ---- battle -----------------------------------------------------------
  private def printed(b: Board): Int =
    catalog.sites.find(_.id == b.origin).get.defense
  private def blanks(b: Board) = Vector.fill(printed(b))(DefenseDieFace.Blank)
  private def sword(count: Int) = Vector.fill(count)(AttackDieFace.OneSword)

  private def committed(game: OathRules, b: Board, force: Int): OathTransition = {
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor)
      .getOrElse(fail("Campaign must start"))
    game.resolveWalker(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(force)).getOrElse(fail("the force must be accepted"))
  }

  private def result(state: OathState): CampaignResult =
    ready(state).game.current.lastCampaignResult.get
  private def site(state: OathState, id: SiteId): SiteForces =
    ready(state).game.current.map.sites(id).forces
  private def boardWarbands(state: OathState, id: PlayerId): Int =
    ready(state).game.current.players.find(_.player == id).get.board.warbands
  private def isAutomaticRoll(pool: PoolKey)(event: OathEvent): Boolean =
    event match {
      case step: WalkerStepRecorded => step.payload match {
        case RollPayload(`pool`, _, true) => true
        case _ => false
      }
      case _ => false
    }

  test("a Conquest victory rolls both dice by itself, records the result and places the survivors") {
    val b = board()
    val game = rules(CampaignFixture.dice(sword(4), blanks(b)))
    val start = committed(game, b, 4)
    assert(start.events.exists(isAutomaticRoll(CampaignIds.attackPool)))
    assertEquals(start.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.sacrifice)))
    val sacrificed = game.resolveWalker(start.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(0)).getOrElse(fail("the sacrifice must be accepted"))
    assertEquals(sacrificed.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.placement)))
    assertEquals(result(sacrificed.state), CampaignResult(b.actor,
      CampaignKind.Conquest, CampaignDefender.Bandits, Vector(b.origin),
      Vector.empty, force = 4, attackFaces = sword(4), attackScore = 4,
      skullLosses = 0, sacrificed = 0, defenseFaces = blanks(b), defenseScore = 2,
      victorious = true))
    // The board is untouched until the losses: the bandits are gone, the
    // committed force is still on the board, and nothing is placed yet.
    assertEquals(boardWarbands(sacrificed.state, b.actor), 5)
    val placed = game.resolveWalker(sacrificed.state, b.actor, CampaignIds.placement,
      ChooseAmountAnswer(3)).getOrElse(fail("the placement must be accepted"))
    val lineage = b.player(b.actor).lineage
    assertEquals(site(placed.state, b.origin),
      SiteForces.Occupied(ForceKind.Exile(lineage), 3))
    assertEquals(boardWarbands(placed.state, b.actor), 2)
    assertEquals(placed.continue, OathContinue.ActActionSelection(b.actor))
    assertEquals(ready(placed.state).game.current.walkerPending, None)
    assertEquals(ready(placed.state).game.current.rollPools, Map.empty[PoolKey, DicePoolState])
  }

  test("the recorded events replay to the same state as the live walk") {
    val b = board()
    val game = rules(CampaignFixture.dice(sword(4), blanks(b)))
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor)
      .toOption.get
    val forced = game.resolveWalker(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(4)).toOption.get
    val sacrificed = game.resolveWalker(forced.state, b.actor,
      CampaignIds.sacrifice, ChooseAmountAnswer(0)).toOption.get
    val placed = game.resolveWalker(sacrificed.state, b.actor,
      CampaignIds.placement, ChooseAmountAnswer(3)).toOption.get
    val events = started.events ++ forced.events ++ sacrificed.events ++
      placed.events
    val replayed = events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(b.ready))) {
      case (Right(state), event) => game.evolve(state, event)
      case (failure, _) => failure
    }
    assertEquals(replayed, Right(placed.state))
  }

  test("a defeat kills the skull and sacrifice losses and half the survivors, and the bandits stay") {
    val b = board()
    val game = rules(CampaignFixture.dice(sword(2), blanks(b)))
    val start = committed(game, b, 2)
    val done = game.resolveWalker(start.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(0)).toOption.get
    assertEquals(result(done.state).victorious, false)
    assertEquals(boardWarbands(done.state, b.actor), 4)
    assertEquals(site(done.state, b.origin), SiteForces.Occupied(ForceKind.Bandit, 2))
    assertEquals(done.continue, OathContinue.ActActionSelection(b.actor))
  }

  test("a sacrifice adds one attack per warband and can turn a defeat into a victory") {
    val b = board()
    val game = rules(CampaignFixture.dice(sword(2), blanks(b)))
    val start = committed(game, b, 2)
    val won = game.resolveWalker(start.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(1)).toOption.get
    assertEquals(result(won.state).sacrificed, 1)
    assertEquals(result(won.state).victorious, true)
    assertEquals(won.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.placement)))
  }

  test("the sacrifice decision is bounded by the force the skulls left, and shows the roll") {
    val b = board()
    val faces = Vector(AttackDieFace.TwoSwordsSkull, AttackDieFace.OneSword)
    val game = rules(CampaignFixture.dice(faces, blanks(b)))
    val start = committed(game, b, 2)
    assertEquals(parkedDecision(b, start).query, DecisionQuery.ChooseAmount(0, 1,
      Some(CampaignBattle.sacrificeHeading(faces, 3, 1, 1)), "Sacrifice"))
  }

  test("zero force asks no sacrifice, and the attacker loses with nothing to kill") {
    val b = board(warbands = 0)
    val game = rules(CampaignFixture.dice(Vector.empty, blanks(b)))
    val done = committed(game, b, 0)
    assertEquals(done.continue, OathContinue.ActActionSelection(b.actor))
    assertEquals(result(done.state).force, 0)
    assertEquals(result(done.state).victorious, false)
    assertEquals(boardWarbands(done.state, b.actor), 0)
  }

  test("a player defender keeps half the killed force, returned to its board") {
    val b = againstPlayer(board())
    val game = rules(CampaignFixture.dice(sword(4), blanks(b)))
    val before = boardWarbands(Ready(b.ready), b.other)
    val start = committed(game, b, 4)
    // The defender may choose plans first; finish that window.
    val afterPlans = game.resolveWalker(start.state, b.other,
      CampaignIds.defenderPlan, ChooseOneAnswer(CampaignIds.finish)).toOption.get
    val sacrificed = game.resolveWalker(afterPlans.state, b.actor,
      CampaignIds.sacrifice, ChooseAmountAnswer(0)).toOption.get
    assertEquals(result(sacrificed.state).defender, CampaignDefender.Player(b.other))
    assertEquals(boardWarbands(sacrificed.state, b.other), before + 1)
  }

  test("several targets are placed with one distribution, and the rest stay on the board") {
    val b = board(extras = 1)
    val game = rules(CampaignFixture.dice(sword(4),
      Vector.fill(printed(b) + catalog.sites.find(_.id == b.extras.head).get.defense)(
        DefenseDieFace.Blank)))
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor).toOption.get
    val targeted = game.resolveWalker(started.state, b.actor, CampaignIds.targets,
      ChooseManyAnswer(Vector(DecisionOptionRef.Site(b.extras.head)))).toOption.get
    val forced = game.resolveWalker(targeted.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(4)).toOption.get
    val sacrificed = game.resolveWalker(forced.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(0)).toOption.get
    assertEquals(parkedDecision(b, sacrificed).query, DecisionQuery.Distribute(
      Vector(b.origin, b.extras.head).map(site => DistributeSlot(
        DecisionOptionRef.Site(site), 0, 4, None)), 0, 4,
      Some("Place up to 4 surviving warbands across the conquered sites; the " +
        "rest stay on your board"), "Place warbands"))
    val placed = game.resolveWalker(sacrificed.state, b.actor, CampaignIds.placement,
      DistributeAnswer(Vector(
        DistributeAmount(DecisionOptionRef.Site(b.origin), 2),
        DistributeAmount(DecisionOptionRef.Site(b.extras.head), 1)))).toOption.get
    val lineage = b.player(b.actor).lineage
    assertEquals(site(placed.state, b.origin),
      SiteForces.Occupied(ForceKind.Exile(lineage), 2))
    assertEquals(site(placed.state, b.extras.head),
      SiteForces.Occupied(ForceKind.Exile(lineage), 1))
    assertEquals(boardWarbands(placed.state, b.actor), 2)
    // More than the survivors is rejected.
    assert(game.resolveWalker(sacrificed.state, b.actor, CampaignIds.placement,
      DistributeAnswer(Vector(
        DistributeAmount(DecisionOptionRef.Site(b.origin), 3),
        DistributeAmount(DecisionOptionRef.Site(b.extras.head), 2)))).isLeft)
  }
```
(Add `import oathdigital.gameplay.walker.RollPayload` and `oathdigital.gameplay.actions.campaign.CampaignBattle` to the suite's imports. The single-target placement question is covered by the first test; the sacrifice test with force `2` and one sword each gives a total attack of 2.)

- [ ] **Step 2: Run to confirm they fail**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, `CampaignBattle.attackResult` is not defined.

- [ ] **Step 3: `CampaignBattle` additions**

Add to `CampaignBattle.scala` (imports: `oathdigital.gameplay.actions.BannerRules` is not needed here):

```scala
  /** The attack after the skull cap: a skull removes one force warband and its
    * two swords count only when that loss can be paid; skulls beyond the force
    * add nothing; Outriders ignores every skull. Returns (score, skulls lost).
    */
  def attackResult(faces: Vector[AttackDieFace], force: Int,
      ignoreSkulls: Boolean): (Int, Int) = {
    val rolled = AttackDieFace.skulls(faces)
    if (ignoreSkulls) AttackDieFace.score(faces) -> 0
    else {
      val payable = math.min(rolled, force)
      (AttackDieFace.score(faces) - (rolled - payable) * 2) -> payable
    }
  }

  private def attackFacesOf(ready: ReadyGame): Vector[AttackDieFace] =
    ready.game.current.rollOutcomes.get(CampaignIds.attackPool).toVector
      .flatMap(_.faces.collect { case face: AttackDieFace => face })

  /** Writes the capped attack over the rolled one. A pool that never rolled has
    * no outcome, and a missing outcome already reads as zero.
    */
  def attackResultOps(catalog: ExecutableCatalog, ready: ReadyGame,
      setup: CampaignSetup, pending: PendingTree): Vector[CoreOperation] =
    ready.game.current.rollOutcomes.get(CampaignIds.attackPool).toVector.map { _ =>
      val ignore = CampaignPlans.ignoresSkulls(catalog,
        CampaignAnswers.picks(pending, CampaignIds.attackerPlan))
      val (score, skulls) = attackResult(attackFacesOf(ready), setup.force, ignore)
      ModifyRollOutcome(CampaignIds.attackPool, Some(skulls), Some(score))
    }

  /** The force a defender adds to its dice: the warbands at every target, or a
    * Raid defender's board.
    */
  def defenderForce(ready: ReadyGame, setup: CampaignSetup): Int = {
    val current = ready.game.current
    setup.kind match {
      case CampaignKind.Conquest => setup.targetSites.flatMap(current.map.sites.get)
        .map(_.forces match {
          case SiteForces.Occupied(_, count) => count
          case SiteForces.Empty => 0
        }).sum
      case CampaignKind.Raid => setup.defender match {
        case CampaignDefender.Player(player) => current.players
          .find(_.player == player).fold(0)(_.board.warbands)
        case CampaignDefender.Bandits => 0
      }
    }
  }

  /** The defense is the dice score plus the defender's force. It is written
    * here, before any warband dies, so the victor never reads the board.
    */
  def defenseResultOps(ready: ReadyGame, setup: CampaignSetup)
      : Vector[CoreOperation] = {
    val dice = ready.game.current.rollOutcomes.get(CampaignIds.defensePool)
      .fold(0)(_.score)
    Vector(ModifyRollOutcome(CampaignIds.defensePool, None,
      Some(dice + defenderForce(ready, setup))))
  }

  /** How many force warbands the attacker may still sacrifice. */
  def sacrificeMax(ready: ReadyGame, setup: CampaignSetup): Int =
    setup.force - ready.game.current.rollOutcomes.get(CampaignIds.attackPool)
      .fold(0)(_.skulls)

  private def faceName(face: AttackDieFace): String = face match {
    case AttackDieFace.HollowSword => "hollow sword"
    case AttackDieFace.OneSword => "one sword"
    case AttackDieFace.TwoSwordsSkull => "two swords and a skull"
  }

  def sacrificeHeading(faces: Vector[AttackDieFace], score: Int, skulls: Int,
      max: Int): String =
    s"Attack roll: ${if (faces.isEmpty) "no dice" else faces.map(faceName).mkString(", ")}. " +
      s"Attack $score with $skulls skull loss${if (skulls == 1) "" else "es"}. " +
      s"Sacrifice up to $max warband${if (max == 1) "" else "s"} for one attack each."

  /** The durable record of this battle, built from the recorded outcomes and
    * the answers, before any warband dies.
    */
  def result(ready: ReadyGame, setup: CampaignSetup, pending: PendingTree)
      : CampaignResult = {
    val outcomes = ready.game.current.rollOutcomes
    val attack = outcomes.get(CampaignIds.attackPool)
    val defense = outcomes.get(CampaignIds.defensePool)
    val sacrificed = CampaignAnswers.sacrificed(pending)
    val attackScore = attack.fold(0)(_.score)
    val defenseScore = defense.fold(0)(_.score)
    CampaignResult(setup.actor, setup.kind, setup.defender, setup.targetSites,
      setup.raidTargets, setup.force, attackFacesOf(ready), attackScore,
      attack.fold(0)(_.skulls), sacrificed,
      defense.toVector.flatMap(_.faces.collect { case face: DefenseDieFace => face }),
      defenseScore, attackScore + sacrificed > defenseScore)
  }

  /** Step 8: kill the defeated warbands. Attacker deaths are the skull and
    * sacrifice losses, plus half the survivors on a defeat; on a victory every
    * warband at every target dies, a player defender's survivors return from
    * the supply to their board, and a Raid defender loses half its board.
    */
  def losses(ready: ReadyGame, result: CampaignResult)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val current = ready.game.current
    current.players.find(_.player == result.attacker).toRight(
      OathViolation.InvalidEventOrder("the Campaign's attacker is not in the game"))
      .map { attacker =>
        val survivors = result.force - result.skullLosses - result.sacrificed
        val deaths = result.skullLosses + result.sacrificed +
          (if (result.victorious) 0 else survivors / 2)
        val own: Vector[CoreOperation] = Option.when(deaths > 0)(Kill(
          Piece.Warbands(ForceKind.Exile(attacker.lineage), deaths),
          PositionedLocation(Location.PlayArea(result.attacker)))).toVector
        own ++ (if (!result.victorious) Vector.empty
          else result.kind match {
            case CampaignKind.Conquest => conquestLosses(ready, result)
            case CampaignKind.Raid => raidBoardLosses(ready, result)
          })
      }
  }

  private def conquestLosses(ready: ReadyGame, result: CampaignResult)
      : Vector[CoreOperation] = {
    val sites = result.targetSites.flatMap(site =>
      ready.game.current.map.sites.get(site).map(state => site -> state.forces))
    val kills: Vector[CoreOperation] = sites.collect {
      case (site, SiteForces.Occupied(force, count)) if count > 0 => Kill(
        Piece.Warbands(force, count), PositionedLocation(Location.Site(site)))
    }
    val returned: Vector[CoreOperation] = result.defender match {
      case CampaignDefender.Player(player) =>
        val total = sites.collect { case (_, SiteForces.Occupied(_, n)) => n }.sum
        val back = total - total / 2
        sites.collectFirst { case (_, SiteForces.Occupied(force, _)) => force }
          .filter(_ => back > 0).toVector.map(force => Move(
            Piece.Warbands(force, back),
            PositionedLocation(Location.WarbandBank(force)),
            PositionedLocation(Location.PlayArea(player))))
      case CampaignDefender.Bandits => Vector.empty
    }
    kills ++ returned
  }

  private def raidBoardLosses(ready: ReadyGame, result: CampaignResult)
      : Vector[CoreOperation] = result.defender match {
    case CampaignDefender.Player(player) =>
      ready.game.current.players.find(_.player == player).toVector.flatMap {
        defender =>
          val killed = defender.board.warbands / 2
          Option.when(killed > 0)(Kill(Piece.Warbands(
            ForceKind.Exile(defender.lineage), killed),
            PositionedLocation(Location.PlayArea(player)))).toVector
      }
    case CampaignDefender.Bandits => Vector.empty
  }
```

- [ ] **Step 4: The Conquest placement and the outcome**

Create `CampaignConquest.scala`:

```scala
package oathdigital.gameplay.actions.campaign

import oathdigital.model._

/** Step 9 for a Conquest victory: place surviving warbands on the conquered
  * targets. Unplaced survivors simply stay on the board.
  */
private[campaign] object CampaignConquest {
  def steps(actor: PlayerId, result: CampaignResult): Vector[Operation] = {
    val survivors = result.force - result.skullLosses - result.sacrificed
    if (survivors <= 0) Vector.empty
    else Vector(Sequence(Vector[Operation](
      Decide(CampaignIds.placement, actor, query(result, survivors)),
      BuildOps((ready, pending) => placements(ready, actor, result, pending))),
      Some(PowerWindow.CampaignPlacement)))
  }

  private def query(result: CampaignResult, survivors: Int): DecisionQuery =
    result.targetSites match {
      case Vector(_) => DecisionQuery.ChooseAmount(0, survivors,
        Some(s"Place up to $survivors surviving warband${if (survivors == 1) "" else "s"} " +
          "on the conquered site; the rest stay on your board"), "Place warbands")
      case sites => DecisionQuery.Distribute(sites.map(site => DistributeSlot(
        DecisionOptionRef.Site(site), 0, survivors, None)), 0, survivors,
        Some(s"Place up to $survivors surviving warbands across the conquered " +
          "sites; the rest stay on your board"), "Place warbands")
    }

  private def placements(ready: ReadyGame, actor: PlayerId, result: CampaignResult,
      pending: PendingTree): Either[OathViolation, Vector[CoreOperation]] =
    ready.game.current.players.find(_.player == actor).toRight(
      OathViolation.InvalidEventOrder("the Campaign's attacker is not in the game"))
      .map(attacker => CampaignAnswers.placements(pending, result.targetSites)
        .filter(_._2 > 0).map { case (site, count) => Move(
          Piece.Warbands(ForceKind.Exile(attacker.lineage), count),
          PositionedLocation(Location.PlayArea(actor)),
          PositionedLocation(Location.Site(site))): CoreOperation })
}
```

Create `CampaignOutcome.scala`:

```scala
package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** What follows the victor. Read from the durable [[CampaignResult]] only:
  * this is selected again after the losses have changed the board.
  */
private[campaign] object CampaignOutcome {
  def steps(ready: ReadyGame, catalog: ExecutableCatalog, actor: PlayerId,
      result: CampaignResult): Vector[Operation] = {
    val losses: Operation = Sequence(Vector[Operation](BuildOps((ready, _) =>
      CampaignBattle.losses(ready, result))), Some(PowerWindow.CampaignLosses))
    val resolution: Vector[Operation] =
      if (!result.victorious) Vector.empty
      else result.kind match {
        case CampaignKind.Conquest => CampaignConquest.steps(actor, result)
        case CampaignKind.Raid => Vector.empty
      }
    losses +: resolution
  }
}
```

In `CampaignProcedure.scala` add the helper and the steps. Replace the tree's last lines so it reads (keep everything above unchanged):

```scala
    CampaignPlanSteps.attacker(catalog, actor),
    CampaignPlanSteps.defender(catalog, actor),
    Roll(CampaignIds.attackPool, DiceSpec(DiceKind.Attack), RollMode.Automatic,
      Some(PowerWindow.CampaignAttackRoll)),
    Sequence(Vector[Operation](BuildOps((ready, pending) =>
      withSetup(ready, actor, pending)(setup =>
        CampaignBattle.attackResultOps(catalog, ready, setup, pending)))),
      Some(PowerWindow.CampaignAttackResult)),
    sacrificeStep(actor),
    Roll(CampaignIds.defensePool, DiceSpec(DiceKind.Defense), RollMode.Automatic,
      Some(PowerWindow.CampaignDefenseRoll)),
    Sequence(Vector[Operation](BuildOps((ready, pending) =>
      withSetup(ready, actor, pending)(setup =>
        CampaignBattle.defenseResultOps(ready, setup)))),
      Some(PowerWindow.CampaignDefenseResult)),
    Sequence(Vector[Operation](BuildOps((ready, pending) =>
      withSetup(ready, actor, pending)(setup => Vector(RecordCampaignResult(
        CampaignBattle.result(ready, setup, pending)))))),
      Some(PowerWindow.CampaignAfterOutcome)),
    outcomeStep(catalog, actor)),
    Some(PowerWindow.CampaignActionEligibility))
```
and add:

```scala
  private def withSetup(ready: ReadyGame, actor: PlayerId, pending: PendingTree)(
      f: CampaignSetup => Vector[CoreOperation])
      : Either[OathViolation, Vector[CoreOperation]] =
    CampaignSetup.setup(ready, actor, pending).map(f).toRight(
      OathViolation.InvalidEventOrder(
        "Campaign reached a battle step without a complete setup"))

  /** Omitted when no force survives the skulls. */
  private def sacrificeStep(actor: PlayerId): Operation =
    Branch((ready, pending) => CampaignSetup.setup(ready, actor, pending)
      .filter(setup => CampaignBattle.sacrificeMax(ready, setup) > 0).map { setup =>
        val max = CampaignBattle.sacrificeMax(ready, setup)
        val attack = ready.game.current.rollOutcomes.get(CampaignIds.attackPool)
        Vector[Operation](Decide(CampaignIds.sacrifice, actor,
          DecisionQuery.ChooseAmount(0, max, Some(CampaignBattle.sacrificeHeading(
            attack.toVector.flatMap(_.faces.collect {
              case face: AttackDieFace => face }), attack.fold(0)(_.score),
            attack.fold(0)(_.skulls), max)), "Sacrifice"),
          window = Some(PowerWindow.CampaignSacrificeSelection)))
      }.getOrElse(Vector.empty))

  /** Read from the durable result, never from the board: this is selected
    * again after the losses have changed it.
    */
  private def outcomeStep(catalog: ExecutableCatalog, actor: PlayerId): Operation =
    Branch((ready, _) => ready.game.current.lastCampaignResult match {
      case Some(result) => CampaignOutcome.steps(ready, catalog, actor, result)
      case None => Vector(BuildOps((_, _) => Left(OathViolation.InvalidEventOrder(
        "Campaign reached its outcome without a recorded result"))))
    })
```
Also change the gather step in `tree` to use `withSetup`:

```scala
    Sequence(Vector[Operation](BuildOps((ready, pending) =>
      withSetup(ready, actor, pending)(CampaignBattle.gatherPools(catalog, _)))),
      Some(PowerWindow.CampaignGatherPools)),
```

- [ ] **Step 5: Run and commit**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignBattleSuite oathdigital.gameplay.CampaignProcedureSuite oathdigital.gameplay.CampaignPlansSuite oathdigital.gameplay.CampaignSetupSuite oathdigital.gameplay.BackendArchitectureSuite" && wc -l src/main/scala/oathdigital/gameplay/actions/campaign/*.scala`
Expected: PASS, every new file well under 800 lines. Then the full gate: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`.

```bash
git add -A src
git commit -m "feat(campaign): the battle and a Conquest victory on the walker

Both rolls are automatic and record their outcomes, the capped attack and the
defense (dice plus the defender's force) are written as roll outcomes so the
victor reads only recorded facts, and the result is a durable public fact.
Nothing leaves the board before the losses; a Conquest victory then places
the survivors with a single amount or a distribution.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 12: Campaign on the walker, part 4: Raid

A Raid victory resolves in the rulebook's printed order and then relocates the defender's pawn. The tree, the kind and defender decisions, the targets, the plans, the rolls and the losses (which already kill half the defender's board warbands) are Tasks 9 to 11; this task adds the transfer step and the relocation, and replaces the empty Raid arm of `CampaignOutcome`.

**Design decisions, all following walker rule W:**
- The Raid tail is `Sequence(CampaignRaidTransfer, BuildOps(transfer))` then `Sequence(CampaignRaidRelocation, Decide(relocation), BuildOps(move))`. The `Decide` is selected again after the transfer has run, so it reads only what the transfer does not change: the in-play sites and the defender's pawn site.
- The transfer is one recorded batch computed from live state when it runs, in the printed order: targeted faceup relics and banners transfer; People's Favor returns one unit at a time to the least-filled, leftmost-on-tie bank (`BannerRules.raidFavorReturn`); the exact number of Darkest Secret resources is burned; ordinary facedown advisers go to the next region's discard, facedown; the Conspiracy returns to the box; facedown relics are set aside for the Chronicle; half the defender's favor, rounded down, burns. (Half the defender's board warbands already died in `CampaignLosses`.)
- Deviation 8: a facedown adviser is discarded only if it is facedown; no registered defender plan reveals.
- The relocation is not Travel: it is one `Move` of the defender's pawn.

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignRaid.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignOutcome.scala` (the Raid arm)
- Test: create `src/test/scala/oathdigital/gameplay/CampaignRaidSuite.scala`; modify `src/test/scala/oathdigital/gameplay/CampaignFixture.scala`

**Interfaces:**
- Produces: `CampaignRaid.steps(actor, result): Vector[Operation]`, `CampaignRaid.transfer(ready, result): Either[OathViolation, Vector[CoreOperation]]`, `CampaignRaid.relocationSites(ready, defender): Vector[SiteId]`.
- Consumes: `BannerRules`, `VisionRules.Conspiracy` (existing), Tasks 9 to 11.

- [ ] **Step 1: Fixture and failing tests**

Add to `CampaignFixture.scala` (import `oathdigital.gameplay.actions.VisionRules`):

```scala
  /** A Raid board: the other player stands at the origin holding a faceup relic,
    * a facedown relic, three facedown advisers (one a Conspiracy), both banners
    * and 5 favor; the actor has 4 warbands.
    */
  def raidBoard(defenderWarbands: Int = 3): (Board, RelicId) = {
    val b = withEnemyAtOrigin(board(warbands = 4))
    val relic = RelicId(catalog.relics.head.id.value)
    val ready = b.ready.updateCurrent(current => current.copy(
      players = current.players.map(p =>
        if (p.player == b.other) p.copy(
          board = p.board.copy(warbands = defenderWarbands, favor = 5),
          advisers = Vector(
            DenizenState(DenizenId("raid-facedown-denizen"), Orientation.FaceDown,
              Tokens.empty),
            VisionState(VisionId("raid-facedown-vision"), Orientation.FaceDown),
            VisionState(VisionRules.Conspiracy, Orientation.FaceDown)),
          relics = Vector(
            RelicState(relic, Orientation.FaceUp, Tokens.empty),
            RelicState(RelicId("raid-facedown-relic"), Orientation.FaceDown,
              Tokens.empty)))
        else p),
      commonCards = current.commonCards.copy(
        worldDeck = current.commonCards.worldDeck.filterNot(_ == VisionRules.Conspiracy),
        relicDeck = current.commonCards.relicDeck.filterNot(_ == relic)),
      map = current.map.copy(sites = current.map.sites.map { case (id, site) =>
        id -> site.copy(relics = site.relics.filterNot(_.id == relic)) }),
      banners = current.banners.copy(
        peoplesFavor = current.banners.peoplesFavor.copy(
          holder = Some(b.other), favor = 3),
        darkestSecret = current.banners.darkestSecret.copy(
          holder = Some(b.other), secrets = 2))))
    (b.copy(ready = ready), relic)
  }
```

Create `src/test/scala/oathdigital/gameplay/CampaignRaidSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer._
import oathdigital.model.OathState.Ready

class CampaignRaidSuite extends munit.FunSuite {
  private def ready(state: OathState): ReadyGame = state match {
    case Ready(value) => value
    case other => fail(s"expected a ready game, got $other")
  }
  private def player(state: OathState, id: PlayerId) =
    ready(state).game.current.players.find(_.player == id).get
  private def favorInBanks(state: OathState): Int =
    ready(state).banks.favor.values.sum
  private val raid = ChooseOneAnswer(DecisionOptionRef.Button("raid"))

  private def walk(b: Board, relic: RelicId, attack: Int, sacrifice: Int = 0) = {
    val printed = 2 + catalog.relics.find(_.id.value == relic.value).get.defense + 3
    val sword = Vector.fill(attack)(AttackDieFace.OneSword)
    val game = rules(dice(sword, Vector.fill(printed)(DefenseDieFace.Blank)))
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor).toOption.get
    val kind = game.resolveWalker(started.state, b.actor, CampaignIds.kind, raid).toOption.get
    val targeted = game.resolveWalker(kind.state, b.actor, CampaignIds.targets,
      ChooseManyAnswer(Vector(DecisionOptionRef.Relic(relic),
        DecisionOptionRef.Banner(Banner.PeoplesFavor)))).toOption.get
    val forced = game.resolveWalker(targeted.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(attack)).toOption.get
    val sacrificed = game.resolveWalker(forced.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(sacrifice)).toOption.get
    (game, started, kind, targeted, forced, sacrificed)
  }

  test("the Raid's defense is the pawn, the targeted relic and banner, plus the board force") {
    val (b, relic) = raidBoard()
    val (_, _, _, _, _, sacrificed) = walk(b, relic, attack = 4)
    val result = ready(sacrificed.state).game.current.lastCampaignResult.get
    assertEquals(result.kind, CampaignKind.Raid)
    assertEquals(result.raidTargets, Vector[CampaignRaidTarget](
      CampaignRaidTarget.Pawn(b.other), CampaignRaidTarget.Relic(b.other, relic),
      CampaignRaidTarget.Banner(b.other, Banner.PeoplesFavor)))
    assertEquals(result.defenseScore, 3)
    assertEquals(result.victorious, true)
  }

  test("a Raid victory transfers in the printed order and then asks where the pawn goes") {
    val (b, relic) = raidBoard()
    val (game, _, _, _, _, sacrificed) = walk(b, relic, attack = 4)
    assertEquals(sacrificed.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.relocation)))
    val after = ready(sacrificed.state)
    // The transfer has happened; the pawn has not moved yet.
    assert(player(sacrificed.state, b.actor).relics.exists(_.id == relic))
    assertEquals(BannerRules.holder(after.game.current, Banner.PeoplesFavor),
      Some(b.actor))
    assertEquals(after.game.current.banners.peoplesFavor.favor, 0)
    assertEquals(favorInBanks(sacrificed.state) - favorInBanks(Ready(b.ready)), 3)
    assertEquals(player(sacrificed.state, b.other).advisers, Vector.empty)
    assertEquals(player(sacrificed.state, b.other).relics, Vector.empty)
    assertEquals(after.game.current.setAsideRelics,
      Vector(RelicId("raid-facedown-relic")))
    assertEquals(player(sacrificed.state, b.other).board.favor, 3)
    assertEquals(player(sacrificed.state, b.other).board.warbands, 2)
    assertEquals(player(sacrificed.state, b.other).pawnSite, Some(b.origin))
    val destination = after.game.current.map.inPlay.find(_ != b.origin).get
    val options = after.game.current.map.inPlay.filterNot(_ == b.origin)
      .map(site => DecisionOption.Site(DecisionOptionRef.Site(site)))
    val pending = after.game.current.walkerPending.get
    val tree = oathdigital.gameplay.actions.campaign.CampaignProcedure.rebuild(
      catalog, after, b.actor, Vector.empty).toOption.get
    assertEquals(oathdigital.gameplay.walker.ProcedureWalker.openDecisions(after,
      tree, pending, oathdigital.gameplay.walker.WalkerPowers.empty).head.query,
      DecisionQuery.ChooseOne(options, Some("Move the defeated pawn to another site")))
    val done = game.resolveWalker(sacrificed.state, b.actor, CampaignIds.relocation,
      ChooseOneAnswer(DecisionOptionRef.Site(destination))).toOption.get
    assertEquals(player(done.state, b.other).pawnSite, Some(destination))
    assertEquals(done.continue, OathContinue.ActActionSelection(b.actor))
    assertEquals(ready(done.state).game.current.walkerPending, None)
  }

  test("the pawn cannot be relocated to its own site") {
    val (b, relic) = raidBoard()
    val (game, _, _, _, _, sacrificed) = walk(b, relic, attack = 4)
    assert(game.resolveWalker(sacrificed.state, b.actor, CampaignIds.relocation,
      ChooseOneAnswer(DecisionOptionRef.Site(b.origin))).isLeft)
  }

  test("a Raid defeat transfers nothing, moves no pawn and kills half the survivors") {
    val (b, relic) = raidBoard(defenderWarbands = 9)
    val (_, _, _, _, _, done) = walk(b, relic, attack = 4)
    assertEquals(done.continue, OathContinue.ActActionSelection(b.actor))
    assertEquals(player(done.state, b.actor).relics.exists(_.id == relic), false)
    assertEquals(player(done.state, b.other).pawnSite, Some(b.origin))
    assertEquals(player(done.state, b.other).board.warbands, 9)
    assertEquals(player(done.state, b.actor).board.warbands, 2)
  }

  test("several enemy pawns at the site ask which to Raid") {
    val (b, relic) = raidBoard()
    val third = b.ready.game.current.players.map(_.player)
      .find(id => id != b.actor && id != b.other)
    assume(third.nonEmpty, "the fixture needs a third player")
    val crowded = b.copy(ready = b.ready.updateCurrent(current => current.copy(
      players = current.players.map(p =>
        if (third.contains(p.player)) p.copy(pawnSite = Some(b.origin)) else p))))
    val game = rules()
    val started = game.startWalker(Ready(crowded.ready), ActionRef.Campaign,
      b.actor).toOption.get
    val kind = game.resolveWalker(started.state, b.actor, CampaignIds.kind, raid)
      .toOption.get
    assertEquals(kind.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.defender)))
    assert(game.resolveWalker(kind.state, b.actor, CampaignIds.defender,
      ChooseOneAnswer(DecisionOptionRef.Player(b.other))).isRight)
  }

  test("the Raid's recorded events replay to the same state as the live walk") {
    val (b, relic) = raidBoard()
    val (game, started, kind, targeted, forced, sacrificed) =
      walk(b, relic, attack = 4)
    val destination = ready(sacrificed.state).game.current.map.inPlay
      .find(_ != b.origin).get
    val done = game.resolveWalker(sacrificed.state, b.actor, CampaignIds.relocation,
      ChooseOneAnswer(DecisionOptionRef.Site(destination))).toOption.get
    val events = Vector(started, kind, targeted, forced, sacrificed, done)
      .flatMap(_.events)
    val replayed = events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(b.ready))) {
      case (Right(state), event) => game.evolve(state, event)
      case (failure, _) => failure
    }
    assertEquals(replayed, Right(done.state))
  }
}
```

- [ ] **Step 2: Run to confirm they fail**

Run: `./sbtw "Test/compile" "testOnly oathdigital.gameplay.CampaignRaidSuite"`
Expected: the suite compiles; the tests fail because a Raid victory stops after the losses (the Raid arm is empty).

- [ ] **Step 3: `CampaignRaid.scala` and the outcome**

Create `CampaignRaid.scala`:

```scala
package oathdigital.gameplay.actions.campaign

import oathdigital.gameplay.actions.{BannerRules, VisionRules}
import oathdigital.model._

/** Step 9 for a Raid victory: the printed transfer, then the pawn's
  * relocation. Both read the durable [[CampaignResult]].
  */
private[campaign] object CampaignRaid {
  def steps(ready: ReadyGame, actor: PlayerId, result: CampaignResult)
      : Vector[Operation] = result.defender match {
    case CampaignDefender.Player(defender) => Vector(
      Sequence(Vector[Operation](BuildOps((state, _) => transfer(state, result))),
        Some(PowerWindow.CampaignRaidTransfer)),
      Sequence(Vector[Operation](
        Decide(CampaignIds.relocation, actor, DecisionQuery.ChooseOne(
          relocationSites(ready, defender).map(site =>
            DecisionOption.Site(DecisionOptionRef.Site(site))),
          heading = Some("Move the defeated pawn to another site"))),
        BuildOps((state, pending) => relocate(state, defender, pending))),
        Some(PowerWindow.CampaignRaidRelocation)))
    case CampaignDefender.Bandits => Vector.empty
  }

  /** Every in-play site except the one the defender's pawn stands on. The
    * pawn does not move before the relocation, so this reads the same when
    * the decision is selected again after the transfer.
    */
  def relocationSites(ready: ReadyGame, defender: PlayerId): Vector[SiteId] = {
    val origin = ready.game.current.players.find(_.player == defender)
      .flatMap(_.pawnSite)
    ready.game.current.map.inPlay.filterNot(site => origin.contains(site))
  }

  private def next(region: Region): Region = region match {
    case Region.Cradle => Region.Provinces
    case Region.Provinces => Region.Hinterland
    case Region.Hinterland => Region.Cradle
  }

  /** The printed Raid resolution as one recorded batch, from live state. */
  def transfer(ready: ReadyGame, result: CampaignResult)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val current = ready.game.current
    for {
      defenderId <- result.defender match {
        case CampaignDefender.Player(player) => Right(player)
        case CampaignDefender.Bandits => Left(OathViolation.InvalidEventOrder(
          "a Raid requires a player defender"))
      }
      defender <- current.players.find(_.player == defenderId).toRight(
        OathViolation.InvalidEventOrder("the Raid's defender is not in the game"))
      origin <- CampaignSetup.originOf(ready, result.attacker).toRight(
        OathViolation.PawnSiteMissing(result.attacker))
      region <- current.map.regionOf(origin).toRight(
        OathViolation.InvalidEventOrder("the Raid's origin has no region"))
    } yield {
      val attacker = result.attacker
      val relics = result.raidTargets.collect {
        case CampaignRaidTarget.Relic(_, id) => id }
      val banners = result.raidTargets.collect {
        case CampaignRaidTarget.Banner(_, banner) => banner }
      val relicTakes: Vector[CoreOperation] = relics.map(id => Take(
        Piece.Card(id), attacker, Location.PlayArea(defenderId),
        Location.PlayArea(attacker)))
      val bannerOps: Vector[CoreOperation] = banners.flatMap { banner =>
        val leaving: Vector[CoreOperation] = banner match {
          case Banner.PeoplesFavor =>
            val returned = BannerRules.raidFavorReturn(ready.banks.favor,
              BannerRules.resources(current, banner)).groupBy(identity)
              .view.mapValues(_.size).toMap
            Suit.all.flatMap(suit => returned.get(suit).filter(_ > 0).map(count =>
              Move(Piece.Favor(count),
                PositionedLocation(Location.OnBanner(banner)),
                PositionedLocation(Location.FavorBank(suit))): CoreOperation))
          case Banner.DarkestSecret =>
            val secrets = BannerRules.resources(current, banner)
            Option.when(secrets > 0)(Burn.secrets(secrets,
              PositionedLocation(Location.OnBanner(banner)))).toVector
        }
        leaving :+ Take(Piece.Banner(banner), attacker,
          Location.PlayArea(defenderId), Location.PlayArea(attacker))
      }
      val facedown: Vector[WorldCardId] = defender.advisers.collect {
        case card: DenizenState if card.orientation == Orientation.FaceDown =>
          card.id: WorldCardId
        case vision: VisionState if vision.orientation == Orientation.FaceDown =>
          vision.id: WorldCardId
      }
      val discards: Vector[CoreOperation] = facedown
        .filterNot(_ == VisionRules.Conspiracy).map(id => Move(Piece.Card(id),
          PositionedLocation(Location.PlayArea(defenderId)),
          PositionedLocation(Location.RegionalDiscard(next(region)),
            StackPosition.Top), resultingOrientation = Some(Orientation.FaceDown)))
      val boxed: Vector[CoreOperation] = facedown
        .filter(_ == VisionRules.Conspiracy).map(id => Move(Piece.Card(id),
          PositionedLocation(Location.PlayArea(defenderId)),
          PositionedLocation(Location.SharedBank)))
      val setAside: Vector[CoreOperation] = defender.relics
        .filter(_.orientation == Orientation.FaceDown).map(relic => Move(
          Piece.Card(relic.id), PositionedLocation(Location.PlayArea(defenderId)),
          PositionedLocation(Location.SetAsideRelics)))
      val burn: Vector[CoreOperation] = Option.when(defender.board.favor / 2 > 0)(
        Burn.favor(defender.board.favor / 2,
          PositionedLocation(Location.PlayArea(defenderId)))).toVector
      relicTakes ++ bannerOps ++ discards ++ setAside ++ burn ++ boxed
    }
  }

  private def relocate(ready: ReadyGame, defender: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = for {
    origin <- ready.game.current.players.find(_.player == defender)
      .flatMap(_.pawnSite).toRight(OathViolation.PawnSiteMissing(defender))
    destination <- CampaignAnswers.relocation(pending).toRight(
      OathViolation.InvalidEventOrder("the Raid's relocation was not answered"))
  } yield Vector[CoreOperation](Move(Piece.Pawn(defender),
    PositionedLocation(Location.Site(origin)),
    PositionedLocation(Location.Site(destination))))
}
```
(`Suit.all` is the ordered suit list `BannerRules.leastFavorBanks` already uses.)

In `CampaignOutcome.steps` replace the Raid arm `case CampaignKind.Raid => Vector.empty` with `case CampaignKind.Raid => CampaignRaid.steps(ready, actor, result)`.

- [ ] **Step 4: Run and commit**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignRaidSuite oathdigital.gameplay.CampaignProcedureSuite oathdigital.gameplay.CampaignBattleSuite oathdigital.gameplay.BackendArchitectureSuite" && wc -l src/main/scala/oathdigital/gameplay/actions/campaign/*.scala`
Expected: PASS. Then the full gate `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`.

```bash
git add -A src
git commit -m "feat(campaign): Raid on the walker

A Raid victory transfers targeted relics and banners in the printed order,
returns People's Favor to the least-filled banks, burns the Darkest Secret's
resources, discards facedown advisers to the next region, boxes the
Conspiracy, sets aside facedown relics and burns half the defender's favor,
then asks the attacker where the defeated pawn goes.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 13: Projecting the Campaign start and the result

Every viewer sees the last Campaign's result; the start control is offered exactly when the dry run of the start walk accepts; a defender's plan decision is shown to the defender and as a waiting notice to everyone else (the generic walker projection already does this once the decision is parked); and the Recover-only roll feedback stops leaking onto other procedures' decisions.

**Files:**
- Create: `src/main/scala/oathdigital/application/CampaignResultProjector.scala`, `shared/src/main/scala/oathdigital/protocol/projection/CampaignResultProjectionCodec.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala` (add the DTO), `shared/src/main/scala/oathdigital/protocol/projection/GameProjectionDto.scala:1-40` (add `lastCampaign`), `shared/src/main/scala/oathdigital/protocol/projection/GameProjectionCodec.scala` (the `Fields` set, the encoder, the decoder and its constructor call), `src/main/scala/oathdigital/application/GameProjection.scala:100-160` (assign it), `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala:141-175,310-330` (gate the roll feedback), `src/main/scala/oathdigital/application/LegalActionProjector.scala:75-90,150-170` (the start control)
- Test: create `src/test/scala/oathdigital/application/CampaignResultProjectionSuite.scala`; modify `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`, `src/test/scala/oathdigital/application/WalkerDecisionProjectionSuite.scala`

**Interfaces:**
- Produces: `CampaignResultProjection(attackerPlayerId, kind: String, defenderPlayerId: Option[String], targetSiteIds: Vector[String], raidTargets: Vector[String], force: Int, attackDice: Vector[String], attackScore: Int, skullLosses: Int, sacrificed: Int, defenseDice: Vector[String], defenseScore: Int, victorious: Boolean)` (`defenderPlayerId = None` is the bandits; `raidTargets` are stable keys such as `pawn:blue`, `relic:blue:r1`, `banner:blue:peoples-favor`; dice spellings are the wire's `hollow-sword`, `one-sword`, `two-swords-skull`, `blank`, `one-shield`, `two-shields`, `doubler`); `GameProjection.lastCampaign: Option[CampaignResultProjection] = None` as its last field; the control string `"beginCampaign"`.
- Public by construction: dice and totals are public, and a Raid's targets are a pawn, faceup relics and banners. The result is identical for every viewer including the public view.
- Consumes: `CampaignResult` (Task 7), `CampaignProcedure.startable` (Task 9).

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/application/CampaignResultProjectionSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.CampaignFixture
import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer._
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.{CampaignResultProjection, GameProjection}

class CampaignResultProjectionSuite extends munit.FunSuite {
  private val projector = new GameProjector(catalog)
  private def view(state: OathState, viewer: PlayerId): GameProjection =
    projector.project("campaign", LoadedGame(state, 40), viewer)

  private def printed(b: Board) = catalog.sites.find(_.id == b.origin).get.defense

  /** A finished Conquest victory: force 4 of swords against 2 bandits. */
  private def fought(b: Board): OathState = {
    val game = rules(dice(Vector.fill(4)(AttackDieFace.OneSword),
      Vector.fill(printed(b))(DefenseDieFace.Blank)))
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor).toOption.get
    val forced = game.resolveWalker(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(4)).toOption.get
    val sacrificed = game.resolveWalker(forced.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(1)).toOption.get
    game.resolveWalker(sacrificed.state, b.actor, CampaignIds.placement,
      ChooseAmountAnswer(0)).toOption.get.state
  }

  test("no Campaign has been fought, so there is no result to show") {
    val b = board()
    assertEquals(view(Ready(b.ready), b.actor).lastCampaign, None)
  }

  test("every viewer, including the public, sees the same result") {
    val b = board()
    val state = fought(b)
    val expected = CampaignResultProjection(b.actor.value, "conquest", None,
      Vector(b.origin.value), Vector.empty, force = 4,
      attackDice = Vector.fill(4)("one-sword"), attackScore = 4, skullLosses = 0,
      sacrificed = 1, defenseDice = Vector.fill(printed(b))("blank"),
      defenseScore = 2, victorious = true)
    Vector(b.actor, b.other).foreach(viewer =>
      assertEquals(view(state, viewer).lastCampaign, Some(expected), viewer.value))
    assertEquals(projector.projectPublic("campaign", LoadedGame(state, 40))
      .lastCampaign, Some(expected))
  }

  test("a Raid result names its targets and its defender") {
    val (b, relic) = raidBoard()
    val printedRaid = 2 + catalog.relics.find(_.id.value == relic.value).get.defense
    val game = rules(dice(Vector.fill(4)(AttackDieFace.OneSword),
      Vector.fill(printedRaid)(DefenseDieFace.Blank)))
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor).toOption.get
    val kind = game.resolveWalker(started.state, b.actor, CampaignIds.kind,
      ChooseOneAnswer(DecisionOptionRef.Button("raid"))).toOption.get
    val targeted = game.resolveWalker(kind.state, b.actor, CampaignIds.targets,
      ChooseManyAnswer(Vector(DecisionOptionRef.Relic(relic)))).toOption.get
    val forced = game.resolveWalker(targeted.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(4)).toOption.get
    val fight = game.resolveWalker(forced.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(0)).toOption.get
    val shown = view(fight.state, b.other).lastCampaign.get
    assertEquals(shown.kind, "raid")
    assertEquals(shown.defenderPlayerId, Some(b.other.value))
    assertEquals(shown.raidTargets, Vector(s"pawn:${b.other.value}",
      s"relic:${b.other.value}:${relic.value}"))
    assertEquals(shown.targetSiteIds, Vector.empty[String])
  }

  test("the start control is offered to the active player exactly when a Campaign could start") {
    val b = board()
    assert(view(Ready(b.ready), b.actor).legalControls.contains("beginCampaign"))
    assert(!view(Ready(b.ready), b.other).legalControls.contains("beginCampaign"))
    assert(!view(Ready(board(supply = 1).ready), b.actor).legalControls
      .contains("beginCampaign"))
    val started = rules().startWalker(Ready(b.ready), ActionRef.Campaign, b.actor)
      .toOption.get
    assert(!view(started.state, b.actor).legalControls.contains("beginCampaign"))
  }

  test("a player defender's plan decision is the defender's; everyone else waits on them") {
    val b = againstPlayer(board())
    val game = rules(dice(Vector.fill(2)(AttackDieFace.OneSword),
      Vector.fill(printed(b))(DefenseDieFace.Blank)))
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor).toOption.get
    val plans = game.resolveWalker(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(2)).toOption.get
    val defender = view(plans.state, b.other)
    assertEquals(defender.walkerDecision.map(_.decisionId), Some(CampaignIds.defenderPlan))
    assertEquals(defender.walkerWaiting, None)
    val attacker = view(plans.state, b.actor)
    assertEquals(attacker.walkerDecision, None)
    assertEquals(attacker.walkerWaiting.map(_.playerId), Some(b.other.value))
    assertEquals(projector.projectPublic("campaign", LoadedGame(plans.state, 40))
      .walkerDecision, None)
  }
}
```

In `ProjectionProtocolSuite.scala` add a test that round trips a projection carrying a result, for both a Conquest and a Raid shape:

```scala
  test("a Campaign result round-trips for a Conquest and a Raid") {
    val conquest = CampaignResultProjection("red", "conquest", None,
      Vector("site:a", "site:b"), Vector.empty, 3, Vector("hollow-sword",
        "two-swords-skull"), 2, 1, 1, Vector("one-shield", "doubler"), 4, false)
    val raid = CampaignResultProjection("red", "raid", Some("blue"), Vector.empty,
      Vector("pawn:blue", "relic:blue:r1", "banner:blue:peoples-favor"), 2,
      Vector.empty, 0, 0, 0, Vector.empty, 5, true)
    Vector(conquest, raid).foreach { result =>
      val carrying = projection.copy(lastCampaign = Some(result))
      assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(carrying)),
        Right(carrying))
    }
  }
```

In `WalkerDecisionProjectionSuite.scala` add a test that a Campaign decision carries no Recover roll feedback:

```scala
  test("only a Recover decision carries roll feedback") {
    val b = oathdigital.gameplay.CampaignFixture.board()
    val game = oathdigital.gameplay.CampaignFixture.rules()
    val started = game.startWalker(OathState.Ready(b.ready), ActionRef.Campaign,
      b.actor).toOption.get
    val projection = new GameProjector(catalog).project("campaign",
      LoadedGame(started.state, 12), b.actor)
    assertEquals(projection.walkerDecision.map(_.rollOutcome), Some(None))
  }
```

- [ ] **Step 2: Run to confirm they fail**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, `CampaignResultProjection` and `lastCampaign` are not defined.

- [ ] **Step 3: The DTO and its codec**

Append to `ActionProjectionDtos.scala`:

```scala
/** The public record of the last Campaign fought (see `CampaignResult`).
  * `defenderPlayerId` is `None` for bandits. Dice use the wire spellings; a
  * Raid's targets are stable keys.
  */
final case class CampaignResultProjection(
    attackerPlayerId: String,
    kind: String,
    defenderPlayerId: Option[String],
    targetSiteIds: Vector[String],
    raidTargets: Vector[String],
    force: Int,
    attackDice: Vector[String],
    attackScore: Int,
    skullLosses: Int,
    sacrificed: Int,
    defenseDice: Vector[String],
    defenseScore: Int,
    victorious: Boolean)
```

Create `CampaignResultProjectionCodec.scala`:

```scala
package oathdigital.protocol.projection

import ProjectionCodecSupport._

private[projection] object CampaignResultProjectionCodec {
  private val Fields = Set("attackerPlayerId", "kind", "defenderPlayerId",
    "targetSiteIds", "raidTargets", "force", "attackDice", "attackScore",
    "skullLosses", "sacrificed", "defenseDice", "defenseScore", "victorious")

  def encode(value: CampaignResultProjection): ujson.Value = ujson.Obj(
    "attackerPlayerId" -> value.attackerPlayerId, "kind" -> value.kind,
    "defenderPlayerId" -> stringOption(value.defenderPlayerId),
    "targetSiteIds" -> encoded(value.targetSiteIds)(ujson.Str(_)),
    "raidTargets" -> encoded(value.raidTargets)(ujson.Str(_)),
    "force" -> value.force,
    "attackDice" -> encoded(value.attackDice)(ujson.Str(_)),
    "attackScore" -> value.attackScore, "skullLosses" -> value.skullLosses,
    "sacrificed" -> value.sacrificed,
    "defenseDice" -> encoded(value.defenseDice)(ujson.Str(_)),
    "defenseScore" -> value.defenseScore, "victorious" -> value.victorious)

  def decode(raw: ujson.Value, path: String): Result[CampaignResultProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Fields, path)
    attacker <- string(value, "attackerPlayerId", path)
    kind <- string(value, "kind", path)
    defender <- optionalString(value, "defenderPlayerId", path)
    sites <- strings(value, "targetSiteIds", path)
    raid <- strings(value, "raidTargets", path)
    force <- int(value, "force", path)
    attackDice <- strings(value, "attackDice", path)
    attackScore <- int(value, "attackScore", path)
    skulls <- int(value, "skullLosses", path)
    sacrificed <- int(value, "sacrificed", path)
    defenseDice <- strings(value, "defenseDice", path)
    defenseScore <- int(value, "defenseScore", path)
    victorious <- bool(value, "victorious", path)
  } yield CampaignResultProjection(attacker, kind, defender, sites, raid, force,
    attackDice, attackScore, skulls, sacrificed, defenseDice, defenseScore,
    victorious)
}
```
In `GameProjectionDto.scala` add `,lastCampaign: Option[CampaignResultProjection] = None` after `phasePowers`. In `GameProjectionCodec.scala` add `"lastCampaign"` to `Fields`; add `"lastCampaign" -> option(value.lastCampaign)(CampaignResultProjectionCodec.encode)` after the `phasePowers` line (mind the comma); in the decoder add `lastCampaign <- optionalAbsent(value, "lastCampaign", path)(CampaignResultProjectionCodec.decode)` after `phasePowers <- ...` and pass `walkerDecision, walkerWaiting, phasePowers, lastCampaign)` to the constructor.

- [ ] **Step 4: The projector and the assignment**

Create `CampaignResultProjector.scala`:

```scala
package oathdigital.application

import oathdigital.model._
import oathdigital.protocol.projection.CampaignResultProjection

/** The last Campaign's result. Everything in it is public, so it takes no
  * viewer.
  */
private[application] object CampaignResultProjector {
  def project(ready: ReadyGame): Option[CampaignResultProjection] =
    ready.game.current.lastCampaignResult.map { result =>
      CampaignResultProjection(result.attacker.value, result.kind.key,
        result.defender match {
          case CampaignDefender.Player(player) => Some(player.value)
          case CampaignDefender.Bandits => None
        }, result.targetSites.map(_.value), result.raidTargets.map(_.stableKey),
        result.force, result.attackFaces.map(attackFace), result.attackScore,
        result.skullLosses, result.sacrificed,
        result.defenseFaces.map(defenseFace), result.defenseScore,
        result.victorious)
    }

  // The wire spellings, duplicated because the application layer may not
  // import the serialization layer (see `defenseFaceName` in
  // `WalkerDecisionProjector`).
  private def attackFace(face: AttackDieFace): String = face match {
    case AttackDieFace.HollowSword => "hollow-sword"
    case AttackDieFace.OneSword => "one-sword"
    case AttackDieFace.TwoSwordsSkull => "two-swords-skull"
  }

  private def defenseFace(face: DefenseDieFace): String = face match {
    case DefenseDieFace.Blank => "blank"
    case DefenseDieFace.OneShield => "one-shield"
    case DefenseDieFace.TwoShields => "two-shields"
    case DefenseDieFace.Doubler => "doubler"
  }
}
```
In `GameProjection.scala` (`readyProjection`) extend the final `.copy(...)`:

```scala
      .copy(walkerDecision = pending.walkerDecision,
        walkerWaiting = pending.walkerWaiting,
        phasePowers = projectedPhasePowers,
        lastCampaign = CampaignResultProjector.project(context.ready))
```

In `WalkerDecisionProjector.parked` gate the roll feedback on Recover: replace both `rollOutcome = rollOutcome(ready, awaited)` arguments with `rollOutcome = Option.when(procedure == ActionRef.Recover)(rollOutcome(ready, awaited)).flatten`, and add to the `rollOutcome` doc: "Only Recover has this feedback; the caller gates on the procedure."

In `LegalActionProjector.scala` add, beside `negotiationStartable` (import `oathdigital.gameplay.actions.campaign.CampaignProcedure`):

```scala
  private def campaignStartable(context: ScopedProjectionContext): Boolean =
    CampaignProcedure.startable(catalog, context.ready, context.active.player,
      WalkerPowers.selected(walkerPowerCatalog, Vector.empty))
```
and in the Act-phase control list after `"beginChallenge"`: `Option.when(campaignStartable(context))("beginCampaign"),`. The legacy `chooseCampaign*` controls and board-target selections stay until Task 16.

- [ ] **Step 5: Run and commit**

Run: `./sbtw "testOnly oathdigital.application.CampaignResultProjectionSuite oathdigital.application.WalkerDecisionProjectionSuite oathdigital.application.WalkerDecisionProjectorSuite oathdigital.protocol.ProjectionProtocolSuite oathdigital.application.GameApplicationServiceSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS. Both the legacy board-target Campaign start and the new control are offered until Task 16.

```bash
git add -A src shared
git commit -m "feat(campaign): project the Campaign start and its result

The last Campaign's result is public and projected to every viewer, the
start control is offered exactly when the dry run of the start walk accepts,
and the Recover-only roll feedback no longer rides other procedures'
decisions.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 14: The frontend start control and the result panel

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/CampaignControls.scala`, `frontend/src/main/scala/oathdigital/frontend/CampaignResultPanel.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala:262,337` (two call lines), `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala:307-315` (the `campaign` kind is a major action), `frontend/src/main/scala/oathdigital/frontend/package.scala` (alias)
- Test: create `frontend/src/test/scala/oathdigital/frontend/CampaignControlsSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/CampaignResultPanelSuite.scala`

**Interfaces:**
- Produces: `CampaignControls.render(value, canControl, groups, submit)`, drawing "Campaign (2 Supply)" when `legalControls` contains `beginCampaign` and submitting `StartWalker("campaign", Vector.empty)`; `CampaignResultPanel.render(value, panel)`, drawing the last result to every viewer. The plan, sacrifice, placement and relocation decisions use the existing generic walker panels (`ChooseOne`, `ChooseAmount`, `Distribute`), so nothing else is drawn: the increment and decrement widgets of the legacy force and placement drafts are gone (spec, "Visibility and presentation").
- The Campaign start does not use the modifier workflow (no walker-selected Campaign power exists), like Negotiation.

- [ ] **Step 1: Write the failing tests**

Create `CampaignControlsSuite.scala` mirroring `NegotiationControlsSuite`:

```scala
package oathdigital.frontend

import oathdigital.protocol.GameIntent
import org.scalajs.dom

/** The Campaign start control at the DOM. */
class CampaignControlsSuite extends munit.FunSuite {
  private def projection(controls: Vector[String]): GameProjection =
    GameProjection("game", 9L, "act-action-selection", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, controls, ready = true,
      completed = false, actionSelectionOpen = true)

  private def render(controls: Vector[String], canControl: Boolean = true)
      : (Vector[dom.html.Button], Vector[GameIntent]) = {
    var submitted = Vector.empty[GameIntent]
    val groups = new ServerUiSupport.ActionSections
    CampaignControls.render(projection(controls), canControl, groups,
      command => submitted :+= command)
    val panel = dom.document.createElement("div")
    groups.appendTo(panel)
    val buttons = panel.querySelectorAll(".act-action").toVector
      .map(_.asInstanceOf[dom.html.Button])
    buttons.foreach(_.click())
    (buttons, submitted)
  }

  test("the control renders only when offered, as a major action, and starts Campaign") {
    val (buttons, submitted) = render(Vector("beginCampaign"))
    assertEquals(buttons.map(_.textContent), Vector("Campaign (2 Supply)"))
    assertEquals(submitted, Vector[GameIntent](
      GameIntent.StartWalker("campaign", Vector.empty)))
    assertEquals(render(Vector.empty)._1, Vector.empty)
    assert(render(Vector("beginCampaign"), canControl = false)._1.forall(_.disabled))
    assertEquals(ServerUiSupport.actionCategory("campaign"), "major")
  }

  test("Campaign does not start through the modifier workflow") {
    assertEquals(ModifierWorkflow.action(
      GameIntent.StartWalker("campaign", Vector.empty)), None)
  }
}
```

Create `CampaignResultPanelSuite.scala`:

```scala
package oathdigital.frontend

import org.scalajs.dom

class CampaignResultPanelSuite extends munit.FunSuite {
  private def projection(result: Option[CampaignResultState]): GameProjection =
    GameProjection("game", 9L, "act-action-selection", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false, lastCampaign = result)

  private def draw(result: Option[CampaignResultState]): dom.Element = {
    val panel = dom.document.createElement("div")
    CampaignResultPanel.render(projection(result), panel)
    panel
  }

  private val conquest = CampaignResultState("red", "conquest", None,
    Vector("site:a"), Vector.empty, 4, Vector("one-sword", "two-swords-skull"),
    3, 1, 1, Vector("one-shield", "doubler"), 4, victorious = true)

  test("nothing is drawn before a Campaign has been fought") {
    assertEquals(draw(None).children.length, 0)
  }

  test("the result names the fight, both dice sets, both totals and the victor") {
    val panel = draw(Some(conquest))
    val text = panel.textContent
    assert(text.contains("Last Campaign"), text)
    assert(text.contains("Conquest"), text)
    assert(text.contains("Bandits"), text)
    assert(text.contains("one sword, two swords and a skull"), text)
    assert(text.contains("Attack 3 + 1 sacrificed = 4"), text)
    assert(text.contains("one shield, doubler"), text)
    assert(text.contains("Defense 4"), text)
    assert(text.contains("Victory"), text)
  }

  test("a defeat and a Raid against a player read as such") {
    val raid = conquest.copy(kind = "raid", defenderPlayerId = Some("blue"),
      targetSiteIds = Vector.empty,
      raidTargets = Vector("pawn:blue", "relic:blue:r1"), victorious = false)
    val text = draw(Some(raid)).textContent
    assert(text.contains("Raid"), text)
    assert(text.contains("blue"), text)
    assert(text.contains("Defeat"), text)
  }
}
```

- [ ] **Step 2: Run to confirm they fail**

Run: `./sbtw "frontend/Test/compile"`
Expected: FAIL to compile, `CampaignControls`, `CampaignResultPanel` and `CampaignResultState` are not defined.

- [ ] **Step 3: Implement**

In `package.scala` add the alias beside the other projection aliases:

```scala
  type CampaignResultState = protocol.projection.CampaignResultProjection
  val CampaignResultState = protocol.projection.CampaignResultProjection
```

Create `CampaignControls.scala`:

```scala
package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand}
import ServerUiSupport._

/** The Act-phase start control for Campaign. The engine offers it only while
  * its dry run starts, so this layer decides nothing. The kind, the targets,
  * the force and every later choice are parked decisions.
  */
private[frontend] object CampaignControls {
  def render(value: GameProjection, canControl: Boolean, groups: ActionSections,
      submit: GameCommand => Unit): Unit =
    if (value.legalControls.contains("beginCampaign")) {
      val node = button("Campaign (2 Supply)", "act-action campaign-action")
      node.disabled = !canControl
      node.onclick = _ => submit(GameCommand.StartWalker("campaign", Vector.empty))
      groups.appendKind("campaign", node)
    }
}
```

Create `CampaignResultPanel.scala`:

```scala
package oathdigital.frontend

import org.scalajs.dom

/** The last Campaign's result, drawn for every viewer: it is public, and it is
  * the only place the dice of a Campaign that ended in one command are shown.
  */
private[frontend] object CampaignResultPanel {
  import ServerUiSupport.{element, playerDisplayName, siteLabel, text}

  private val names = Map("hollow-sword" -> "hollow sword",
    "one-sword" -> "one sword", "two-swords-skull" -> "two swords and a skull",
    "blank" -> "blank", "one-shield" -> "one shield",
    "two-shields" -> "two shields", "doubler" -> "doubler")

  private def dice(faces: Vector[String]): String =
    if (faces.isEmpty) "no dice" else faces.map(f => names.getOrElse(f, f)).mkString(", ")

  def render(value: GameProjection, panel: dom.Element): Unit =
    value.lastCampaign.foreach { result =>
      val box = element("section", "campaign-result")
      val kind = if (result.kind == "raid") "Raid" else "Conquest"
      val against = result.defenderPlayerId.fold("Bandits")(id =>
        playerDisplayName(value, id))
      val targets = (result.targetSiteIds.map(siteLabel(value, _)) ++
        result.raidTargets).mkString(", ")
      box.appendChild(text("h2", "", "Last Campaign"))
      box.appendChild(text("p", "campaign-result-summary",
        s"$kind by ${playerDisplayName(value, result.attackerPlayerId)} against " +
          s"$against${if (targets.isEmpty) "" else s" ($targets)"} with ${result.force} " +
          s"committed warband${if (result.force == 1) "" else "s"}"))
      box.appendChild(text("p", "campaign-result-attack",
        s"Attack dice: ${dice(result.attackDice)}. Attack ${result.attackScore} + " +
          s"${result.sacrificed} sacrificed = ${result.attackScore + result.sacrificed}, " +
          s"${result.skullLosses} skull loss${if (result.skullLosses == 1) "" else "es"}."))
      box.appendChild(text("p", "campaign-result-defense",
        s"Defense dice: ${dice(result.defenseDice)}. Defense ${result.defenseScore}."))
      box.appendChild(text("p", "campaign-result-outcome",
        if (result.victorious) "Victory" else "Defeat"))
      panel.appendChild(box)
    }
}
```
(`playerDisplayName` and `siteLabel` are the helpers `ServerUiSupport` already exposes; if either is `private`, widen it to `private[frontend]`.)

In `ActionDecisionRenderer.scala` add `CampaignControls.render(value, canControl, groups, submitCommand)` after the `NegotiationControls.render` line (262) and `CampaignResultPanel.render(value, panel)` after `WalkerPanelSupport.renderWaitingNotice(value, panel)` (line 338). In `ServerUiSupport.actionCategory` add `"campaign"` to the `"major"` alternatives. `ServerUiSupport.actionLabel` needs no change.

- [ ] **Step 4: Run and commit**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.CampaignControlsSuite oathdigital.frontend.CampaignResultPanelSuite oathdigital.frontend.ServerModeUiSuite oathdigital.frontend.NegotiationControlsSuite" "frontend/fastLinkJS"`
Expected: PASS and the link succeeds.

```bash
git add -A frontend
git commit -m "feat(campaign): the frontend start control and result panel

Campaign starts from a control the engine offers, and the last Campaign's
dice, totals and victor are drawn for every viewer. Plans, sacrifice,
placement and relocation use the existing generic decision panels.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 15: Prove the walker Campaign matches legacy

Before any deletion, run the same legal Campaigns through the legacy commands and through the walker with the same dice, and compare the complete authoritative state. This is the oracle Tasks 16 to 18 delete, so it is written now and its result is recorded. The two paths differ by design in what is *not* state: the legacy `pending`, the walker's scratch fields, the dice pools and outcomes, and the new public `lastCampaignResult`; those are normalized away. Everything else (warbands, sites, banners, favor, secrets, cards and their orientation, discards, banks, knowledge, the turn) must be equal. A mismatch is a bug in the new code or an undocumented rule difference: investigate it, do not weaken the comparison.

Parity is possible only where both accept: the unaltered exile-only state, injected dice, and no relevant unsupported power. The deliberate differences (no first-game gate, ignored powers, no cancel) are tested in Tasks 9 to 12 as new walker behaviour. Projections are not compared: the legacy Campaign projection is deleted by design and the new result projection has its own suite (Task 13).

**Files:**
- Test: create `src/test/scala/oathdigital/gameplay/CampaignParitySuite.scala`

**Interfaces:**
- Consumes: the walker Campaign (Tasks 9 to 12), `CampaignFixture` (Tasks 9 to 12), and the legacy `CampaignCommand` path, which still exists.
- Produces: nothing new.

- [ ] **Step 1: Write the suite**

```scala
package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.CampaignCommand
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer._
import oathdigital.model.OathState.Ready

/** The walker Campaign against the legacy Campaign, on the same boards with
  * the same dice. */
class CampaignParitySuite extends munit.FunSuite {
  private val old = new OathRules(catalog)
  private val decision = DecisionId("parity")

  private def ok[A](result: Either[OathViolation, A], step: String): A =
    result.fold(violation => fail(s"$step: $violation"), identity)

  private def legacy(state: ReadyGame, commands: CampaignCommand*): OathState =
    commands.foldLeft[OathState](Ready(state)) { (current, command) =>
      ok(old.handle(current, command), command.toString).state }

  private def walker(game: OathRules, b: Board,
      answers: (PlayerId, String, DecisionAnswer)*): OathState = {
    val started = ok(game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor),
      "start")
    answers.foldLeft(started.state) { case (state, (by, id, answer)) =>
      ok(game.resolveWalker(state, by, id, answer), id).state }
  }

  /** Everything but the machinery that differs by design. */
  private def normalized(state: OathState): ReadyGame = state match {
    case Ready(ready) => ready.updateCurrent(_.copy(pending = None,
      walkerPending = None, walkerProcedure = None, walkerModifiers = Vector.empty,
      walkerStartArgs = Vector.empty, rollPools = Map.empty,
      rollOutcomes = Map.empty, lastCampaignResult = None))
    case other => fail(s"expected a ready game, got $other")
  }

  private def same(legacyState: OathState, walkerState: OathState): Unit =
    assertEquals(normalized(walkerState), normalized(legacyState))

  private def printed(b: Board): Int = catalog.sites.find(_.id == b.origin).get.defense
  private def blanks(n: Int) = Vector.fill(n)(DefenseDieFace.Blank)
  private def swords(n: Int) = Vector.fill(n)(AttackDieFace.OneSword)
  private def site(id: SiteId) = DecisionOptionRef.Site(id)
  private val zero = ChooseAmountAnswer(0)
  private def amount(n: Int) = ChooseAmountAnswer(n)

  test("a Conquest victory over bandits places the survivors") {
    val b = board()
    val game = rules(dice(swords(4), blanks(printed(b))))
    same(legacy(b.ready,
      CampaignCommand.Start(b.actor, decision, b.origin, 4),
      CampaignCommand.FinishPlans(b.actor, decision, swords(4)),
      CampaignCommand.Sacrifice(b.actor, decision, 0, blanks(printed(b))),
      CampaignCommand.Place(b.actor, decision, Vector(
        CampaignForceAllocation(b.origin, 3)))),
      walker(game, b, (b.actor, CampaignIds.force, amount(4)),
        (b.actor, CampaignIds.sacrifice, zero),
        (b.actor, CampaignIds.placement, amount(3))))
  }

  test("a Conquest defeat kills the skull and sacrifice losses and half the survivors") {
    val b = board()
    val game = rules(dice(swords(2), blanks(printed(b))))
    same(legacy(b.ready,
      CampaignCommand.Start(b.actor, decision, b.origin, 2),
      CampaignCommand.FinishPlans(b.actor, decision, swords(2)),
      CampaignCommand.Sacrifice(b.actor, decision, 0, blanks(printed(b)))),
      walker(game, b, (b.actor, CampaignIds.force, amount(2)),
        (b.actor, CampaignIds.sacrifice, zero)))
  }

  test("a sacrifice that turns a defeat into a victory") {
    val b = board()
    val game = rules(dice(swords(2), blanks(printed(b))))
    same(legacy(b.ready,
      CampaignCommand.Start(b.actor, decision, b.origin, 2),
      CampaignCommand.FinishPlans(b.actor, decision, swords(2)),
      CampaignCommand.Sacrifice(b.actor, decision, 1, blanks(printed(b))),
      CampaignCommand.Place(b.actor, decision, Vector(
        CampaignForceAllocation(b.origin, 1)))),
      walker(game, b, (b.actor, CampaignIds.force, amount(2)),
        (b.actor, CampaignIds.sacrifice, amount(1)),
        (b.actor, CampaignIds.placement, amount(1))))
  }

  test("two targets are placed with one allocation") {
    val b = board(extras = 1)
    val second = b.extras.head
    val defense = blanks(printed(b) + catalog.sites.find(_.id == second).get.defense)
    val game = rules(dice(swords(4), defense))
    same(legacy(b.ready,
      CampaignCommand.Start(b.actor, decision, Vector(b.origin, second), 4),
      CampaignCommand.FinishPlans(b.actor, decision, swords(4)),
      CampaignCommand.Sacrifice(b.actor, decision, 0, defense),
      CampaignCommand.Place(b.actor, decision, Vector(
        CampaignForceAllocation(b.origin, 2),
        CampaignForceAllocation(second, 1)))),
      walker(game, b,
        (b.actor, CampaignIds.targets, ChooseManyAnswer(Vector(site(second)))),
        (b.actor, CampaignIds.force, amount(4)),
        (b.actor, CampaignIds.sacrifice, zero),
        (b.actor, CampaignIds.placement, DistributeAnswer(Vector(
          DistributeAmount(site(b.origin), 2), DistributeAmount(site(second), 1))))))
  }

  test("Brass Army adds four attack dice for a secret") {
    val brass = relicWith("relic.brass-army.campaign")
    val b = withSecrets(withRelic(board(), brass), 2)
    val faces = swords(5)
    val game = rules(dice(faces, blanks(printed(b))))
    same(legacy(b.ready,
      CampaignCommand.Start(b.actor, decision, b.origin, 1),
      CampaignCommand.ChoosePlan(b.actor, decision,
        CampaignPlanSource.Relic(b.actor, RelicId(brass))),
      CampaignCommand.FinishPlans(b.actor, decision, faces),
      CampaignCommand.Sacrifice(b.actor, decision, 0, blanks(printed(b))),
      CampaignCommand.Place(b.actor, decision, Vector(
        CampaignForceAllocation(b.origin, 1)))),
      walker(game, b, (b.actor, CampaignIds.force, amount(1)),
        (b.actor, CampaignIds.attackerPlan,
          ChooseOneAnswer(DecisionOptionRef.Relic(RelicId(brass)))),
        (b.actor, CampaignIds.sacrifice, zero),
        (b.actor, CampaignIds.placement, amount(1))))
  }

  test("Outriders ignores every skull, from a facedown adviser it reveals first") {
    val outriders = cardWith("denizen.outriders")
    Vector(Orientation.FaceUp, Orientation.FaceDown).foreach { orientation =>
      val b = withAdviser(board(), outriders, orientation)
      val faces = Vector[AttackDieFace](AttackDieFace.TwoSwordsSkull,
        AttackDieFace.TwoSwordsSkull)
      val game = rules(dice(faces, blanks(printed(b))))
      same(legacy(b.ready,
        CampaignCommand.Start(b.actor, decision, b.origin, 2),
        CampaignCommand.ChoosePlan(b.actor, decision,
          CampaignPlanSource.Adviser(b.actor, DenizenId(outriders))),
        CampaignCommand.FinishPlans(b.actor, decision, faces),
        CampaignCommand.Sacrifice(b.actor, decision, 0, blanks(printed(b))),
        CampaignCommand.Place(b.actor, decision, Vector(
          CampaignForceAllocation(b.origin, 2)))),
        walker(game, b, (b.actor, CampaignIds.force, amount(2)),
          (b.actor, CampaignIds.attackerPlan,
            ChooseOneAnswer(DecisionOptionRef.Denizen(DenizenId(outriders)))),
          (b.actor, CampaignIds.sacrifice, zero),
          (b.actor, CampaignIds.placement, amount(2))))
    }
  }

  test("a player defender with the title adds a die and keeps half the killed force") {
    val b = againstPlayer(board())
    val defense = blanks(printed(b) + 1)
    val game = rules(dice(swords(4), defense))
    same(legacy(b.ready,
      CampaignCommand.Start(b.actor, decision, b.origin, 4),
      CampaignCommand.FinishPlans(b.actor, decision, Vector.empty),
      CampaignCommand.ChoosePlan(b.other, decision,
        CampaignPlanSource.Title(b.other)),
      CampaignCommand.FinishPlans(b.other, decision, swords(4)),
      CampaignCommand.Sacrifice(b.actor, decision, 0, defense),
      CampaignCommand.Place(b.actor, decision, Vector(
        CampaignForceAllocation(b.origin, 2)))),
      walker(game, b, (b.actor, CampaignIds.force, amount(4)),
        (b.other, CampaignIds.defenderPlan,
          ChooseOneAnswer(DecisionOptionRef.Button("title"))),
        (b.actor, CampaignIds.sacrifice, zero),
        (b.actor, CampaignIds.placement, amount(2))))
  }

  test("a Campaign with no force fights and loses nothing") {
    val b = board(warbands = 0)
    val game = rules(dice(Vector.empty, blanks(printed(b))))
    same(legacy(b.ready,
      CampaignCommand.Start(b.actor, decision, b.origin, 0),
      CampaignCommand.FinishPlans(b.actor, decision, Vector.empty),
      CampaignCommand.Sacrifice(b.actor, decision, 0, blanks(printed(b)))),
      walker(game, b, (b.actor, CampaignIds.force, amount(0))))
  }

  private def raidCase(defenderWarbands: Int, attack: Int, sacrifice: Int,
      relocate: Boolean): Unit = {
    val (b, relic) = raidBoard(defenderWarbands)
    val targets = Vector[CampaignRaidTarget](CampaignRaidTarget.Pawn(b.other),
      CampaignRaidTarget.Relic(b.other, relic),
      CampaignRaidTarget.Banner(b.other, Banner.PeoplesFavor))
    val defense = blanks(2 + catalog.relics.find(_.id.value == relic.value).get
      .defense + 3)
    val game = rules(dice(swords(attack), defense))
    val destination = b.ready.game.current.map.inPlay.find(_ != b.origin).get
    val legacyCommands = Vector[CampaignCommand](
      CampaignCommand.StartRaid(b.actor, decision, targets, attack),
      CampaignCommand.FinishPlans(b.actor, decision, Vector.empty),
      CampaignCommand.FinishPlans(b.other, decision, swords(attack)),
      CampaignCommand.Sacrifice(b.actor, decision, sacrifice, defense)) ++
      Option.when(relocate)(CampaignCommand.RelocateRaidPawn(b.actor, decision,
        destination))
    val walkerAnswers = Vector[(PlayerId, String, DecisionAnswer)](
      (b.actor, CampaignIds.kind, ChooseOneAnswer(DecisionOptionRef.Button("raid"))),
      (b.actor, CampaignIds.targets, ChooseManyAnswer(Vector(
        DecisionOptionRef.Relic(relic), DecisionOptionRef.Banner(Banner.PeoplesFavor)))),
      (b.actor, CampaignIds.force, amount(attack)),
      (b.actor, CampaignIds.sacrifice, amount(sacrifice))) ++
      Option.when(relocate)((b.actor, CampaignIds.relocation,
        ChooseOneAnswer(site(destination))))
    same(legacy(b.ready, legacyCommands: _*), walker(game, b, walkerAnswers: _*))
  }

  test("a Raid victory transfers and relocates") {
    raidCase(defenderWarbands = 3, attack = 4, sacrifice = 0, relocate = true)
  }

  test("a Raid defeat changes only the attacker's warbands") {
    raidCase(defenderWarbands = 9, attack = 4, sacrifice = 0, relocate = false)
  }

  test("an illegal start is refused by both paths and changes nothing") {
    val noSupply = board(supply = 1)
    assert(old.handle(Ready(noSupply.ready), CampaignCommand.Start(noSupply.actor,
      decision, noSupply.origin, 0)).isLeft)
    assert(rules().startWalker(Ready(noSupply.ready), ActionRef.Campaign,
      noSupply.actor).isLeft)
    val tooMany = board(warbands = 2)
    assert(old.handle(Ready(tooMany.ready), CampaignCommand.Start(tooMany.actor,
      decision, tooMany.origin, 3)).isLeft)
    val started = rules().startWalker(Ready(tooMany.ready), ActionRef.Campaign,
      tooMany.actor).toOption.get
    assert(rules().resolveWalker(started.state, tooMany.actor, CampaignIds.force,
      amount(3)).isLeft)
  }
}
```

- [ ] **Step 2: Run**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignParitySuite"`
Expected: PASS. If a scenario differs, print both normalized states, find the first differing field, and fix the new code (Tasks 9 to 12) or, if the difference is a deliberate rule change in the spec, document it in the spec and change the scenario so it isolates it. Do not delete or loosen a scenario.

- [ ] **Step 3: Commit**

```bash
git add -A src
git commit -m "test(campaign): prove legacy and walker Campaign agree

Conquest and Raid, victory and defeat, sacrifice, Brass Army, Outriders,
the title, several targets and zero force run through the legacy commands
and the walker with the same dice and produce the same authoritative state.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 16: Retire the legacy Campaign client surface

The parity suite proved the walker path, so the ways a client reached the legacy one go, top layer first so every commit builds: (1) the server stops offering the legacy start, (2) the frontend drops its legacy panels, force draft and helpers, (3) the seven commands, intents and mapper arms go. The legacy projection is Task 17.1 and the legacy rules and events are Task 17.2.

**Files (by step):**
- 16.1: `src/main/scala/oathdigital/application/LegalActionProjector.scala:5,124-145,196-275`, `src/test/scala/oathdigital/application/CampaignResultProjectionSuite.scala`
- 16.2: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala:156-206,341-452`, `ServerUiSupport.scala:14-21,276-279,296-325,387-476,521-536`, `ServerModeUi.scala` (`campaignPlacementState`, `boardFormationState`), `BoardTargetSelectionState.scala`, `ModifierWorkflow.scala:39-44`, `package.scala:49-50,74-105`, delete `CampaignPlacementState.scala`; tests `frontend/src/test/scala/oathdigital/frontend/BoardTargetSelectionStateSuite.scala`, `HttpGameClientSuite.scala`, `ProtocolTestCommands.scala`, `RecordingServerUiView.scala`, `ServerModeUiSuite.scala`
- 16.3: `src/main/scala/oathdigital/application/GameCommands.scala:35-55`, `Authorization.scala:79-98`, `GameApplicationService.scala:6,400-430,455-462`, `GameIntentMapper.scala:28-34,85-96`, `shared/src/main/scala/oathdigital/protocol/CommandIntents.scala:18-30,66-79`, `CommandIntentCodec.scala:17-23,43-53`, `CommandIntentDecoders.scala:32-59,100-112`; tests `shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala:9-22`, `src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala`, `PendingWalkerInvariantSuite.scala:35-45`, `ForgeWalkerFixture.scala:81-98`, `src/test/scala/oathdigital/gameplay/PendingWalkerRulesSuite.scala:58-60`

**Interfaces:**
- Produces: nothing new. After 16.3 a client can only start Campaign with `StartWalker("campaign")` and answer it with `ResolveWalker`.
- `BoardTargetFormationProjection`, the `formation` field of `BoardTargetActionProjection` and the `PlayerPawn`, `PlayerRelic` and `PlayerBanner` board-target refs lose their only producer. They stay in the shared protocol in this slice (removing them is a wire cleanup with no behaviour); Task 19 records them as follow-up work.

#### 16.1 The server stops offering the legacy start

- [ ] **Step 1: Write the failing test**

Add to `CampaignResultProjectionSuite`:

```scala
  test("Campaign is offered as a start control, not as a board-target selection") {
    val b = board(extras = 1)
    val projection = view(Ready(b.ready), b.actor)
    assert(projection.legalControls.contains("beginCampaign"))
    assert(!projection.boardTargetActions.exists(_.kind.startsWith("campaign")))
    assert(!projection.legalControls.exists(_.endsWith("CampaignPlans")))
  }
```

- [ ] **Step 2: Run to confirm it fails**

Run: `./sbtw "testOnly oathdigital.application.CampaignResultProjectionSuite"`
Expected: FAIL, the projection still carries `campaign-conquest` and `campaign-raid` board-target actions.

- [ ] **Step 3: Remove the selections and the legacy controls**

In `LegalActionProjector.boardTargetActions` delete the `val campaign = ...` and `val raid = ...` bindings and both `selection("campaign-conquest", ...)` and `selection("campaign-raid", ...)` arguments, leaving:

```scala
    Vector(selection("travel", "Choose a Travel destination", travel)).flatten
```
(and delete the now-unused `ready` and `player` locals). In `controls`, delete the five legacy `PendingProcedure.Campaign` and `CampaignRaidRelocation` cases (lines 132-142) so the `current.pending match` keeps only `case _ if !context.viewerIsActive => Vector.empty`, `case Some(_) => Vector.empty` and `case None => ...`. Remove the `CampaignRules` import (line 5). Task 18 removes the `pending` match itself.

- [ ] **Step 4: Run and commit**

Run: `./sbtw "testOnly oathdigital.application.CampaignResultProjectionSuite oathdigital.application.GameApplicationServiceSuite"`
Expected: PASS except any `GameApplicationServiceSuite` test that asserted a legacy `boardTargetActions` campaign entry or a legacy Campaign control; those assert removed behaviour, so delete them (16.3 removes the commands they drive).

```bash
git add -A src
git commit -m "refactor(campaign): stop offering the legacy board-target start

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

#### 16.2 The frontend drops the legacy panels and helpers

- [ ] **Step 1: Delete, then let the compiler list what is left**

In `ActionDecisionRenderer.scala` delete: the whole `else if (currentBoardFormation.nonEmpty) { ... }` branch (the "Form Campaign force" panel, lines 156-206, ending before `} else if (selection.nonEmpty) {`); the `value.campaign.filter(...).foreach { campaign => ... }` block (lines 341-436); and the `value.campaignRaidRelocation.filter(...)...` block (438-452).

In `ServerUiSupport.scala` delete `campaignPlanButtonLabel`, `campaignSelectedPlansLabel`, `campaignFormationSummary`, `campaignForceChoiceLabel`, `campaignForceAdjustmentLabel`, `commandForFormation`, `raidRelocationCommands`, `protocolRaidTarget` and `protocolCampaignPlan`; delete the two `campaign-conquest` and `campaign-raid` arms of `commandForSelection` (leaving `place-pawn` and `travel`); remove `"campaign-conquest"` and `"campaign-raid"` from `actionLabel`, `actionCategory` and `actionFamily`; in `viewerPresentation` (lines 276-279) replace `val controllingPlayer = value.campaign.filter(!_.plansFinished).flatMap(_.decisionOwnerPlayerId).orElse(value.activeParticipantId)` with `val controllingPlayer = value.activeParticipantId`; delete the `currentBoardFormation` and `currentCampaignPlacement` members of `ServerUiView` (lines 14-17).

In `ServerModeUi.scala` delete `campaignPlacementState`, `boardFormationState` and every reset of them and their two accessor pairs (`currentBoardFormation`, `currentCampaignPlacement`). Delete `CampaignPlacementState.scala`. In `BoardTargetSelectionState.scala` delete `BoardTargetFormationState`, `BoardSelectionResult.Form` and the two branches that produce it (lines 69-72 and 106-109), so a selection always confirms directly. In `ModifierWorkflow.scala` delete the two `campaign-*` entries of `targetedActions`. In `package.scala` delete the `CampaignState` type and object, `CampaignRaidRelocation`, `CampaignPlacementTarget`, `CampaignPlanChoice` and `BoardTargetFormation` aliases.

- [ ] **Step 2: Migrate the tests**

In the five frontend suites delete every test that exercises the deleted helpers, the force formation, the placement state, the legacy Campaign panel or the legacy Campaign JSON (for example `HttpGameClientSuite`'s `campaign-17` projection decode test, the formation tests in `BoardTargetSelectionStateSuite`, the placement tests in `ServerModeUiSuite`), and remove the `currentBoardFormation` and `currentCampaignPlacement` members from `RecordingServerUiView`. Any assertion that survives (a `travel` selection confirming directly, for example) stays.

- [ ] **Step 3: Compile and run**

Run: `./sbtw "frontend/Test/compile" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS. Fix whatever the compiler still names by deleting it if it exists only for the legacy Campaign, or by keeping it when another feature uses it: `git grep -n "Campaign\|Formation" -- frontend/src` must name only `CampaignControls`, `CampaignResultPanel`, `CampaignResultState` and their suites.

```bash
git add -A frontend
git commit -m "refactor(campaign): drop the legacy frontend panels and force draft

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

#### 16.3 The commands, intents and mapper go

- [ ] **Step 1: Migrate the fixture that used them**

`ForgeWalkerFixture.scala` (lines 81-98) conquers the Forge site through the legacy commands. Replace that block with the walker (import `oathdigital.gameplay.actions.campaign.CampaignIds`, `oathdigital.model.DecisionAnswer._`):

```scala
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.StartWalker(ActionRef.Campaign, StartPayload(actor)))
      .fold(error => fail(s"Campaign fixture rejected: $error"), identity)
    // Other bandit-ruled sites are optional targets: take none.
    if (accepted.continue == OathContinue.AwaitingCampaignDecision(actor,
        DecisionId(CampaignIds.targets)))
      accepted = service.handle(gameId, accepted.nextSequence,
        GameCommand.ResolveWalker(actor, TreeDecision(CampaignIds.targets,
          ChooseManyAnswer(Vector.empty)))).toOption.get
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(CampaignIds.force,
        ChooseAmountAnswer(3)))).toOption.get
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(CampaignIds.sacrifice,
        ChooseAmountAnswer(2)))).toOption.get
    accepted.continue match {
      case OathContinue.AwaitingCampaignDecision(_, decision)
          if decision.value == CampaignIds.placement =>
        accepted = service.handle(gameId, accepted.nextSequence,
          GameCommand.ResolveWalker(actor, TreeDecision(CampaignIds.placement,
            ChooseAmountAnswer(1)))).toOption.get
      case _ => ()
    }
```
The service is built with `campaignDicePort = blankCampaignDice`, which the walker's dice adapter uses (Task 4).

- [ ] **Step 2: Delete the commands and their layers**

Delete the seven `GameCommand` cases `BeginCampaignConquest` (with its companion), `BeginCampaignRaid`, `ChooseCampaignPlan`, `FinishCampaignPlans`, `ChooseCampaignSacrifice`, `PlaceCampaignForce` and `RelocateCampaignRaidPawn`; the seven `Authorization` helpers; the `GameApplicationService` dispatch arms (lines 400-430), the two `majorAction` Campaign arms (so it becomes `case _ => None`) and the `Campaign`, `CampaignCommand` and `CampaignRules` imports; the seven `GameIntentMapper` arms and its `raid` and `plan` helpers; the seven `Intent` cases and the `CampaignForceAllocation`, `CampaignRaidTarget` and `CampaignPlanSource` protocol types in `CommandIntents.scala`; and their arms in `CommandIntentCodec` (`allocation`, `raid`, `plan` too) and `CommandIntentDecoders` (`campaignRaid`, `campaignPlan`, the allocation decoder).

- [ ] **Step 3: Migrate the tests**

`CommandProtocolSuite`: delete the seven Campaign examples (lines 15-22) and the `"retired facedown adviser wire intents"` test stays untouched. Add a decoder test that each of the seven retired tags is now an unknown intent type:

```scala
  test("the retired Campaign intents are rejected as unknown types") {
    Vector("beginCampaignConquest", "beginCampaignRaid", "chooseCampaignPlan",
      "finishCampaignPlans", "chooseCampaignSacrifice", "placeCampaignForce",
      "relocateCampaignRaidPawn").foreach { tag =>
      val json = s"""{"expectedNextSequence":0,"intent":{"type":"$tag"}}"""
      val failure = ActorlessCommandCodec.decode(json).left.toOption.get
      assertEquals(failure.path, "$.intent.type", tag)
      assert(failure.message.contains("unknown intent type"), tag)
    }
  }
```
Also add `StartWalker("campaign", Vector.empty)` to the `examples` vector so the round trip covers it. `PendingWalkerInvariantSuite`: delete the seven Campaign constructors from `everyCommand` (lines 35-44). `PendingWalkerRulesSuite`: delete the `"campaign" -> rules.handle(state, CampaignCommand.Start(...))` pair (58-60) and its import. `GameApplicationServiceSuite`: delete the tests that drive the legacy Campaign commands (the block from `safeCampaignSite` at line 1017 through the last legacy Campaign test, about lines 1017-1160, and `"invalid Campaign plan requests consume no attack randomness"`), and any other test naming `BeginCampaign*`, `ChooseCampaign*`, `FinishCampaignPlans`, `PlaceCampaignForce` or `RelocateCampaignRaidPawn`. The walker equivalents are in `CampaignProcedureSuite`, `CampaignRaidSuite` and `CampaignResultProjectionSuite`. Add one service-level test that proves the whole Campaign persists and replays through the application service (append, reload, replay), by reusing the Forge fixture, which now drives a full walker Campaign (start, targets, force, sacrifice, placement) and then a Search:

```scala
  test("a walker Campaign persists every command and replays to the same state") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository,
      campaignDicePort = blankCampaignDice)
    val (accepted, _, _) =
      ForgeWalkerFixture.forgeReadyGame(service, "walker-campaign")
    val reloaded = new GameApplicationService(catalog, repository,
      campaignDicePort = blankCampaignDice).load("walker-campaign")
      .toOption.flatten.get
    assertEquals(reloaded.state, accepted.state)
  }
```

- [ ] **Step 4: Compile and run**

Run: `./sbtw "Test/compile" "frontend/Test/compile"` then `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS. `git grep -n "BeginCampaign\|ChooseCampaignPlan\|FinishCampaignPlans\|ChooseCampaignSacrifice\|PlaceCampaignForce\|RelocateCampaignRaidPawn" -- src shared frontend` prints only the retired-tag test above and, in `src/main`, the legacy rules that Task 17.2 deletes.

```bash
git add -A src shared frontend
git commit -m "refactor(campaign): retire the seven legacy Campaign commands

Campaign now starts with StartWalker and is answered with ResolveWalker.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 17: Retire the legacy Campaign projection, rules, events and vocabulary

**Files (by step):**
- 17.1: `src/main/scala/oathdigital/application/PendingProcedureProjector.scala:4,14-160`, `ScopedProjectionContext.scala:24-33` (`PendingProjection`), `GameProjection.scala:140,152`, `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala:250-270`, `GameProjectionDto.scala:23-24`, `GameProjectionCodec.scala` (`Fields`, the `campaign` and `campaignRaidRelocation` lines, `decodeRelocation`), delete `CampaignProjectionCodec.scala`; tests `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`
- 17.2: delete `src/main/scala/oathdigital/gameplay/actions/Campaign.scala`, `CampaignRules.scala`, `CampaignPlans.scala`, `CampaignResolution.scala`, `CampaignHandlerClassifications.scala`, `src/main/scala/oathdigital/serialization/CampaignEventCodec.scala`; edit `OathRules.scala:5-6,33-35,67-90,117-130`, `GameEventProtocol.scala:40-130`, `GameEventWire.scala:21,36-42,275-296`, `GameEventJsonSupport.scala`, `FirstGameSetup.scala:200-201`, `PendingProcedures.scala`, `GameProcedureProtocol.scala:43-50`, `GameViolation.scala`, `RuleSources.scala:100-135`, `CampaignTypes.scala`, `GameApplicationService.scala` (imports); tests listed in step 17.2.3

**Interfaces:**
- Consumes: Tasks 9 to 16.
- Kept, and now used only by the walker path: `CampaignDicePort`, `BannerRules`, `SiteRule`, `OathViolation.CampaignUnavailable` and `InsufficientSupply`, `PowerRuntime`'s Campaign timings, the reviewed `CampaignPowers` catalog, `CampaignKind`, `CampaignRaidTarget`, `CampaignDefender`, the plan vocabulary, and in `GameEventJsonSupport` the die-face codecs (`encodeAttackFace`, `decodeAttackFace`, `encodeDefenseFace`, `decodeDefenseFace`), `decodeCampaignKind`, `encodeCampaignRaidTarget` and `decodeCampaignRaidTarget`, which `CampaignResultCodec` uses.
- Deleted with the legacy path: `Campaign`, `CampaignCommand`, `CampaignRules`, the legacy `CampaignPlanRegistry` and `CampaignPlanHandler`s, `CampaignLosingForceResolver`/`Registry` and their dormant vocabulary (`CampaignLosingForceEffect`), `CampaignHandlerClassifications`, the events `CampaignStarted`, `CampaignPlanChosen`, `CampaignPlansFinished`, `CampaignSacrificed`, `CampaignConquered`, `CampaignRaided` and `CampaignRaidPawnRelocated` with their codecs and wire constants, `PendingProcedure.Campaign` and `CampaignRaidRelocation`, `CampaignForceAllocation`, `CampaignRaidBoardLoss`, the dormant plan effects `TransformAttackResult`, `ReplaceLosingForcePolicy` and `Suspend`, `OathContinue.AwaitingCampaignSacrifice`, `AwaitingCampaignPlan`, `AwaitingCampaignPlacement` and `AwaitingCampaignRaidRelocation`, the violations `CampaignDecisionMismatch`, `CampaignPlanUnavailable`, `CampaignOutcomeMismatch` and `UnsupportedCampaignState` (only those nothing else names; `git grep` first), the `RuleQueryContext.Campaign` block and `CampaignTimingWindow` if nothing else names them, and `OathRules`'s `campaignLosingForceRegistry` parameter.

#### 17.1 The legacy projection

- [ ] **Step 1: Delete the projection layer**

In `PendingProcedureProjector.scala` delete `campaignProjection`, `sourceIdentity`, the plan-label helpers, `costs`, `effects`, `optionProjection`, `campaignRaidRelocation`, the two attack and defense face-name helpers if unused, and the legacy `pending match` arms of `phase` (keep the `walkerPending` branch and the phase match), and remove the `campaign` and `relocation` parameters and results. In `PendingProjection` delete `campaign` and `campaignRaidRelocation`. In `GameProjection.readyProjection` delete `campaign = pending.campaign` and `campaignRaidRelocation = pending.campaignRaidRelocation`. In the shared DTOs delete `CampaignProjection`, `CampaignPlacementTargetProjection`, `CampaignRaidRelocationProjection` and `CampaignPlanChoiceProjection`; in `GameProjection` (the DTO) delete `campaign` and `campaignRaidRelocation`; in `GameProjectionCodec` delete both from `Fields`, from the encoder and the decoder and the constructor call, and delete `decodeRelocation`; delete `CampaignProjectionCodec.scala`.

- [ ] **Step 2: Migrate the tests, compile, run and commit**

In `ProjectionProtocolSuite` delete the tests that build or round trip a `CampaignProjection` or a `CampaignRaidRelocationProjection`. The result and decision round trips added in Tasks 2 and 13 stay.

Run: `./sbtw "Test/compile" "frontend/Test/compile" "testOnly oathdigital.protocol.ProjectionProtocolSuite oathdigital.application.*"`
Expected: PASS. `git grep -n "CampaignProjection\|CampaignRaidRelocationProjection\|CampaignPlanChoiceProjection\|campaignRaidRelocation" -- src shared frontend` prints nothing.

```bash
git add -A src shared frontend
git commit -m "refactor(campaign): retire the legacy Campaign projection

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

#### 17.2 The legacy rules, events and vocabulary

- [ ] **Step 1: Port the three tests worth keeping before `CampaignSuite` goes**

Add to `CampaignProcedureSuite` (facedown site card revealed in place):

```scala
  test("a facedown site Outriders is revealed in place when chosen") {
    val outriders = cardWith("denizen.outriders")
    val base = board()
    val facedown = base.copy(ready = base.ready.updateCurrent(current => current.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(_ == DenizenId(outriders))),
      map = current.map.copy(sites = current.map.sites.updated(base.origin,
        current.map.sites(base.origin).copy(denizens = Vector(DenizenState(
          DenizenId(outriders), Orientation.FaceDown, Tokens.empty))))))))
    val plans = atPlans(facedown)
    val done = answer(plans.state, facedown.actor, CampaignIds.attackerPlan,
      planPick(DecisionOptionRef.Denizen(DenizenId(outriders)))).toOption.get
    assert(ops(done.events).contains(Reveal(DenizenId(outriders),
      Location.Site(facedown.origin))))
    assertEquals(ready(done.state).game.current.map.sites(facedown.origin)
      .denizens.head.asInstanceOf[DenizenState].orientation, Orientation.FaceUp)
  }
```

Add the refill test (the boundary refills a cleared site with its printed Bandit force before the title check, and refuses a tampered refill on replay):

```scala
  test("a victory with nothing placed refills the bandits at the action boundary") {
    val b = board()
    val game = rules(CampaignFixture.dice(sword(3), blanks(b)))
    val start = committed(game, b, 3)
    val sacrificed = game.resolveWalker(start.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(0)).toOption.get
    val done = game.resolveWalker(sacrificed.state, b.actor, CampaignIds.placement,
      ChooseAmountAnswer(0)).toOption.get
    assert(done.events.exists(_.isInstanceOf[OathEvent.BanditsRefilled]))
    val capacity = catalog.sites.find(_.id == b.origin).get.capacity
    assertEquals(ready(done.state).game.current.map.sites(b.origin).forces,
      SiteForces.Occupied(ForceKind.Bandit, capacity))
    val refill = done.events.collectFirst {
      case event: OathEvent.BanditsRefilled => event }.get
    assert(game.evolve(sacrificed.state, refill.copy(sites =
      Vector(b.origin -> 99))).isLeft)
  }
```

Move the policy test `"state-based operation policy permits only bandit-bank refills"` (`CampaignSuite.scala:966-980`) into `StateBasedEvaluationSuite` unchanged except that its board comes from `CampaignFixture.board()` (`val b = board(); val ready = b.ready; val site = b.origin`); it tests `StateBasedOperationPolicy`, not Campaign.

Every other `CampaignSuite` test is deleted with the legacy path, because its behaviour is already covered by a walker suite or belonged to deleted vocabulary. The mapping is: Raid targets, defense and canonical order to `CampaignSetupSuite` and `CampaignRaidSuite`; attack faces to `CampaignBattleSuite`; Conquest origin and multi-site to `CampaignSetupSuite` and `CampaignProcedureSuite`; the Pass to `CampaignPowersSuite`; empty force and projection bounds to `CampaignProcedureSuite`; staged Conquest, defeat, sacrifice and both player-defender cases to `CampaignProcedureSuite`, `CampaignBattleSuite` and `CampaignParitySuite`; Brass Army, Outriders and the plan order to `CampaignPlansSuite` and `CampaignProcedureSuite`; Vow of Peace and the ignored Bag of Siegeworks to `CampaignPowersSuite` and `CampaignProcedureSuite`; bandit Watchdog to `CampaignProcedureSuite`; replay to `"the recorded events replay..."`. Deleted with no counterpart: the typed losing-force policy tests (`:1061`, `:1094`, `:1122`), the plan-extension test (`:270`), formation and projection bounds (`:135`, `:404`), stale or tampered recorded plan sources (`:669`), and the audited classification and unknown-handler tests (`:833`, `:854`, `:901`, `:917`, `:931`): those pinned the legacy classifier, event validation and policy seam that no longer exist.

- [ ] **Step 2: Run the ported tests before deleting anything**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignProcedureSuite oathdigital.gameplay.StateBasedEvaluationSuite"`
Expected: PASS.

- [ ] **Step 3: Delete, layer by layer, compiling between layers**

Order (each layer compiles before the next): (1) delete `CampaignSuite.scala` and the other tests that name the deleted symbols (the `CampaignTimingWindow` test at `RuleResolutionSuite.scala:8-20`, `BackendArchitectureSuite.scala:7,164-171` and its `CampaignRules` import, the legacy pending test block in `OathkeeperProcedureSuite.scala:139-150`, the seven event tests in `GameEventWireSuite` naming `CampaignStarted` and its siblings, `CampaignLosingForceResolver` and `CampaignRules` imports there); (2) delete `Campaign.scala`, `CampaignRules.scala`, `CampaignPlans.scala`, `CampaignResolution.scala`, `CampaignHandlerClassifications.scala` and the `OathRules` `handle(CampaignCommand)`, the seven `evolve` arms, the `campaignLosingForceRegistry` parameter and its imports; (3) delete the seven events from `GameEventProtocol`, `CampaignEventCodec.scala`, the seven wire type constants and the `CampaignEventCodec` mixin, `campaignDiscriminator`, `campaignEncoder` and `campaignDecode` uses in `GameEventWire`, the event names in `FirstGameSetup.scala:200-201`, and in `GameEventJsonSupport` the losing-force effect, plan-source, plan-side, plan-cost and plan-effect codecs (lines 253-345 and 419-483), keeping the die-face, `decodeCampaignKind` and raid-target helpers; (4) in `PendingProcedures.scala` delete `PendingProcedure.Campaign` and `CampaignRaidRelocation`, `CampaignForceAllocation`, `CampaignLosingForceEffect` and `CampaignRaidBoardLoss`; in `CampaignTypes.scala` delete the three dormant plan effects; in `GameProcedureProtocol.scala` delete the four legacy Campaign continuations; in `GameViolation.scala` and `RuleSources.scala` delete each violation and each rule-source type that `git grep` shows is no longer named anywhere, and in `server` the route messages for the deleted violations.

- [ ] **Step 4: Verify and commit**

Run: `./sbtw "Test/compile" "frontend/Test/compile" "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS. Then:

```bash
git grep -n "PendingProcedure\.Campaign\|CampaignRaidRelocation\|CampaignRules\|CampaignCommand\|CampaignLosing\|CampaignHandlerClassifications\|CampaignStarted\|CampaignPlanChosen\|CampaignPlansFinished\|CampaignSacrificed\|CampaignConquered\|CampaignRaided\|CampaignRaidPawnRelocated" -- src shared frontend
```
Expected: prints nothing (docs under `docs/` are frozen history and are updated in Task 19).

```bash
git add -A src shared frontend
git commit -m "refactor(campaign): delete the legacy Campaign rules, events and vocabulary

The walker owns Campaign. The legacy procedure, its plan registry and losing-force
policy seam, its seven events and codecs, its pending cases and continuations,
and the unused violations are removed.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 18: Delete the legacy `pending` slot

After Campaign, `PendingProcedure` has no cases and `CurrentGameState.pending` is always `None`. The dual-pending guard exists only to protect that slot. This task is its own commit so it can be reviewed, or split out of the slice, on its own.

**Files:**
- Modify: `src/main/scala/oathdigital/model/GameState.scala:141-147`, `PendingProcedures.scala` (delete the file after moving what remains), `src/main/scala/oathdigital/model/GameViolation.scala:34`, `src/main/scala/oathdigital/gameplay/OathLifecycle.scala:35-42`, `StateBasedEvaluation.scala:65-70,146-152`, `OathRulesWalker.scala:100-108,305-312`, `phases/PhasePowerProcedure.scala:70-75,94-97`, `phases/rest/FinishRestProcedure.scala:32-40`, `walker/ProcedureWalker.scala` (the dual-pending comments), `src/main/scala/oathdigital/application/GameProjection.scala:128-132`, `LegalActionProjector.scala:104-112,127-146`, `GameApplicationService.scala` (the `unblocked` guard's legacy half), `PendingProcedureProjector.scala`, and every test constructing `CurrentGameState` with `pending = ...`

**Interfaces:**
- Produces: `CurrentGameState` without `pending`; no `PendingProcedure`, no `OathViolation.PendingProcedureBlocksAction`. What remains in `PendingProcedures.scala` (`SiteDenizenTarget`, `CampaignKind`, `CampaignRaidTarget`, `CampaignDefender`) moves to `CampaignTypes.scala` (or its own file when the name is not a Campaign type) and the file is deleted.
- Rule: a guard of the form `current.pending.isEmpty` (or `pending match { case Some(...) ... }`) is deleted, and the walker's own guard (`walkerPending`/`walkerProcedure`) stays.

- [ ] **Step 1: Take stock**

Run: `git grep -n "\.pending\b\|pending = \|pending: \|PendingProcedure\|PendingProcedureBlocksAction" -- src shared frontend | grep -v "walkerPending\|PendingTree\|pending: PendingTree"`
Expected: production hits in `GameState.scala`, `OathLifecycle.scala`, `StateBasedEvaluation.scala`, `OathRulesWalker.scala`, `PhasePowerProcedure.scala`, `FinishRestProcedure.scala`, `GameProjection.scala`, `LegalActionProjector.scala`, `GameApplicationService.scala`, `GameViolation.scala`, plus about 45 test constructions of `pending = None`.

- [ ] **Step 2: Remove the field and every guard, compiling as you go**

Delete the field and its comment from `CurrentGameState` (`GameState.scala:144`; the "dual pending" comment above `walkerPending` is reworded to say the walker slot is the only pending state). Then, file by file:
- `OathLifecycle.scala:39-41`: delete `else current.pending match { case Some(value) => Left(PendingProcedureBlocksAction(value.decision)) ... }`, keeping the `walkerPending` refusal that follows it.
- `StateBasedEvaluation.scala:67-68` and `:149-151`: delete the two `pending.nonEmpty` refusals.
- `OathRulesWalker.scala:105` (the `startTriggered` guard: drop `|| ready.game.current.pending.nonEmpty`) and `:310-311` (delete the `"legacy pending procedure blocks walker resume"` check).
- `PhasePowerProcedure.scala:73,96` and `FinishRestProcedure.scala:36-37`: drop the `current.pending.isEmpty` conjunct and the `pending match` refusal.
- `GameProjection.scala:130` and `LegalActionProjector.scala:106,109,131-146`: drop `current.pending.isEmpty` from the conditions and delete the `current.pending match` in `controls`, leaving its `case _ if !context.viewerIsActive`, `case None` branches as plain `if` and `else`.
- `GameApplicationService.applyCommand`: the block that refuses non-resume commands while a walker is pending stays (it is the walker guard); delete the legacy half if it names `pending`.
- `GameViolation.scala`: delete `PendingProcedureBlocksAction` and its server route message if nothing else names it.
- `PendingProcedures.scala`: delete `sealed trait PendingProcedure`; move `SiteDenizenTarget`, `CampaignKind`, `CampaignRaidTarget` and `CampaignDefender` into `CampaignTypes.scala` (`SiteDenizenTarget` into `Cards.scala` if it is not a Campaign type: `git grep -n SiteDenizenTarget`); delete the file. In `ProcedureWalker.strip`, keep clearing `walkerPending` and `walkerProcedure` and delete the comment about the dual-pending guard.
- Rename or keep `PendingProcedureProjector`: it now projects only the card decision, the walker decision and the phase; rename it to `PendingProjector` only if the rename touches fewer than five call sites, otherwise leave the name and update its class doc.
- Tests: delete every `pending = None`, `pending = Some(...)` and `.pending` mention (`git grep -n "pending = None\|current.pending" -- src/test`), and rewrite or delete the assertions that a legacy pending blocks an action: the legacy half of `PendingWalkerInvariantSuite` and `PendingWalkerRulesSuite` (the walker-pending half stays), and any test asserting `PendingProcedureBlocksAction`.

- [ ] **Step 3: Verify and commit**

Run: `./sbtw "Test/compile" "frontend/Test/compile" "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS. `git grep -n "PendingProcedure\b\|\.pending\b\|pending = " -- src shared frontend | grep -v "walkerPending\|PendingTree"` prints nothing.

If this step needs changes in more than about 25 files or breaks a suite you cannot explain, stop and split it out of the slice: the Campaign deletion in Task 17 stands on its own.

```bash
git add -A src shared frontend
git commit -m "refactor: delete the legacy pending slot

Campaign was the last procedure on PendingProcedure, so the type, the
CurrentGameState field, the dual-pending guards and the violation that
reported them are gone. The walker's pending position is the only one.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 19: Documentation and the final gate

**Files:**
- Rewrite: `docs/architecture/bounded-campaign.md`
- Modify: `docs/ROADMAP.md`, `docs/architecture/core-operations-migration.md`, `docs/architecture/gameplay-modules.md`, `docs/architecture/authoritative-events.md`, `docs/architecture/rule-resolution.md`, `docs/architecture/implementation-traceability.md`, `docs/superpowers/specs/2026-09-05-procedure-walker-design.md`, `docs/superpowers/specs/2026-09-12-walker-ownership-and-phases-design.md`, `docs/superpowers/specs/2026-09-19-campaign-walker-design.md`, `docs/superpowers/plans/2026-09-19-campaign-walker.md` (status line only)

- [ ] **Step 1: Rewrite `bounded-campaign.md`** in the shape of `all-exile-negotiation.md`: a dated note at the top ("ported to the procedure walker ... design: `docs/superpowers/specs/2026-09-19-campaign-walker-design.md`"), then the procedure as built: the tree in rulebook order, the decision ids, the two pools, the automatic rolls, the recorded attack and defense results, the durable `CampaignResult`, losses and resolution, Conquest placement and Raid resolution, the powers (Vow of Peace as a root restriction, Narrow Pass as an option restriction), the ignore-and-record rule, visibility (defender plans private to the defender, the result public), the journal, and the deferred list. Keep the rules content of the old document that still holds (costs, targets, arithmetic, Raid order).

- [ ] **Step 2: Fold the deviations into the spec.** In `2026-09-19-campaign-walker-design.md` change the status line to "implemented by [the plan](../plans/2026-09-19-campaign-walker.md)" and add an "Implementation notes" section listing the twelve deviations of the plan's "Deviations from the spec" section, so the spec matches what was built. Also record: what "ignore and record" actually records is what the reviewed power catalog lists at the Campaign windows (today Bag of Siegeworks); the other handlers the legacy classifier named are neither blocked nor recorded, as for every other ported action; and the deferred follow-ups: the unused board-target `formation`, `PlayerPawn`, `PlayerRelic` and `PlayerBanner` protocol types, an action-history feed (the durable result is the interim), all rolls automatic (Recover), real Pass consent, the first-game rule audit, converting the plan handlers into power contributions, the second sentence of Vow of Peace.

- [ ] **Step 3: Update the walker roadmap and the status lists.** In `ROADMAP.md`, the walker spec's "Migration status" and remaining-cases list, and `core-operations-migration.md`: Campaign runs on the walker; no legacy `PendingProcedure` remains; the `pending` slot is deleted. In `authoritative-events.md` and `implementation-traceability.md` replace the seven Campaign events by the walker's recorded roll and delta steps and `RecordCampaignResult`. In `gameplay-modules.md` list the new `actions/campaign/` package, in `rule-resolution.md` the new windows, `OptionRestriction` and the ignore-and-record rule. In the walker-ownership spec, add the automatic `Roll` mode and the defender-owned decision to its status. Add the walker spec's decision "an `Automatic` `Roll` never parks" under its Roll section.

- [ ] **Step 4: Confirm the frozen documents.** The plan asked for no reference to `RuntimeRuleRegistry` in `docs`; check the analogous claims here: `git grep -n "CampaignRules\|PendingProcedure" -- docs ':!docs/superpowers' ':!docs/architecture/bounded-campaign.md'` prints only historical notes that already carry a "superseded" banner.

- [ ] **Step 5: The final gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: PASS. Then `git status --short` shows a clean tree and `git log --oneline main..HEAD | head -30` lists the Campaign commits in order.

```bash
git add -A docs
git commit -m "docs: record Campaign as ported to the walker

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

