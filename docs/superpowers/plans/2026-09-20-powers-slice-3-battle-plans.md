# Powers Slice 3: Battle Plans Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the nine battle-plan powers of the first powers batch (Mercenaries, Wrestlers, Warning Signals, Towering Rampart, Cracked Rampart, Fearsome Shield, Battle Honors, Sticky Fire and Gleaming Armor) on the engine change E8, and port the four battle plans that were registry handlers (Outriders, Brass Army, Watchdog and the title's defense) onto the same footing.

**Architecture:** A Campaign plan window becomes a node, `CampaignPlanChoice`, that hosts the `Offer` contributions of the powers in play. Each plan is a `BattlePlan` power: an `Offer` that says where its card must stand and what the plan costs and does, and, when it acts later in the Campaign, a hook at a later window. The engine asks the decision, prices each option from a dry run of the plan, and applies the chosen plan as a `CampaignPlanApplication`, a windowed operation other powers can add to (Gleaming Armor). Every power is declared solely as contributions over existing operations, and nothing beyond the engine changes listed below was needed.

**Tech Stack:** Scala 2.13, sbt via `./sbtw`, munit.

**Spec:** [Powers design](../specs/2026-09-20-powers-design.md) (E8 "Battle-plan offers", the cost rules and the slicing table) and [rulings appendix](../specs/2026-09-20-powers-rulings.md) (the "Slice 3: Campaign battle plans" section and "Rules that apply to every power"). Builds on the [slice 0 plan](2026-09-20-powers-slice-0-foundations.md), which supplied E4's `PayCost` (the empty-card rule, `intoOccupied`, `matchingBank` and off-turn settlement), and the [Campaign walker plan](2026-09-19-campaign-walker.md), whose Campaign this changes.

## Global Constraints

- `BackendArchitectureSuite` and `scripts/check-architecture.py` apply: production files stay at or under 800 lines; no power name appears in `gameplay/walker` or `gameplay/operations` sources; a power imports nothing from `oathdigital.gameplay.walker`; no direct state write (`copy(advisers =`, `map.copy(sites =` and similar) under `gameplay/powers`. `scripts/check-markdown-links.py` gates the docs.
- Every power is declared solely as a `ContributingPower` (through the `BattlePlan` kit, or a plain contribution for Gleaming Armor) over existing operations. Engine changes are only those named in "Engine changes" below.
- A power is registered through one object of powers per sub-slice and one line in `WalkerPowerCatalog` (`BattlePlans`, `SimplePlans`, `PlanRules`), as slices 1 and 2 did. A power whose card is absent from the catalog is omitted.
- A battle plan is automatic whatever its catalog `persistent` flag says: it is chosen at the plan step, not selected at the start of the action. Gleaming Armor is a persistent rule and takes its resolution from the flag.
- Every decision a Campaign asks, engine or power, has an id under the prefix `campaign.`, so it continues as a Campaign decision (Task 5).
- Recorded operations must survive the journal wire and replay. A suite that pays a plan asserts `PaidActionHarness.replayed(...)` equals the state the command reached, and `PaidActionHarness.wireRoundTrips(...)` for a recorded batch that carries a new operation shape.
- Commit messages end with `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`. Code, comments, commits and docs are normal prose.
- Run the whole suite with `./sbtw test`, the frontend suite with `./sbtw frontend/test`, and one suite with `./sbtw "testOnly <fully.qualified.Suite>"`.
- Test fixtures keep the card inventory whole. `CampaignFixture` places a card by first taking it out of every zone it could be in (`scrub`), and its new helpers (`withAdviserFor`, `withRelicFor`, `withEdifice`) do the same.
- Other slices (2 and 4) are planned in parallel. Shared files (`WalkerPowerCatalog.scala`, the design's status and "Slicing" lines, the rulings tables) are edited minimally, each addition on its own line, to ease merging.

## How this slice is split

The slice is large, and its parts have different risk. It should be **executed, reviewed and merged as four sub-slices**, one at a time, in this order. Each sub-slice ends green (`./sbtw test`, `./sbtw frontend/test` for 3a, and the architecture check) and is mergeable on its own.

| Sub-slice | Tasks | Contents | Engine changes | Needs |
| --- | --- | --- | --- | --- |
| 3a. The rename | 1 | `CampaignResult.victorious` becomes `attackerWins` across the model, both codecs, the shared DTO, the result panel, the suites and the docs | the rename | nothing |
| 3b. The plan window | 2 and 3 | the `Offer` seam in the walker; the plan window as a host of offers; `CampaignPlanApplication`; the burnt and sacrifice costs; the option's price; ruler-only sources; the four ported plans (title, Outriders, Brass Army, Watchdog) | E8, `PowerCtx.answered`, a `Repeat` rule, replay settlement of `PayCost` | 3a for nothing but the name |
| 3c. Simple plans | 4 | Mercenaries, Wrestlers, Fearsome Shield, Towering and Cracked Rampart, Battle Honors | none | 3b |
| 3d. Plans that reach further | 5 to 7 | Sticky Fire, Warning Signals, Gleaming Armor | the kit's `wrapping` hook, the `campaign.` decision prefix | 3b |

Why this split, and not one plan or one task per power:

- **3a is a pure rename with a wire consequence.** It touches the frontend and the shared codec, which no other part of the slice does, and it changes the journal key of a recorded result. It should be reviewed and merged alone, and it can be dropped or reordered without touching E8.
- **3b is the only sub-slice that changes the engine.** Everything in it is exercised by the four plans that already exist and by test-only plans, so a reviewer can judge the engine without a card's rules in the way. Splitting it in two tasks separates the walker's generic seam (Task 2, tested with hosts of its own) from the Campaign's use of it (Task 3).
- **3c adds no engine code.** Six powers, each a few lines over the kit, with suites that drive a whole Campaign.
- **3d holds the three powers that need something beyond an offer**: a question in the losses, a decision of its own inside a plan, and an added cost on the enemy's plans. They are independent of one another, so they are three tasks, and they can merge in any order after 3b (Task 7's tests use no other 3d code).

Sub-slices 3c and 3d depend on each other not at all. Slice 4 does not depend on this slice.

## Engine changes

The design names E8. Planning found that the changes below are needed too. None is silent: each is in the task that needs it, and each is in the report.

- **E8, battle-plan offers (Tasks 2 and 3).** A new contribution kind, `Offer`, offers a plan to a windowed node that implements `OfferHost`. The Campaign's plan window, `CampaignPlanChoice`, is that node: it keeps the shared choose-a-plan-or-finish decision and its `Repeat`, asks the decision with the plans still unchosen that the user can pay for, and applies the pick as a `CampaignPlanApplication`, a windowed operation (window `CampaignPlanApplication`) carrying the side, the user and the source, so a power can match on it as Silver Tongue matches `PlacementTree`. `CampaignPlanCost` gains `FavorBurnt`, `SecretBurnt` and `SacrificeWarband`; `CampaignPlanEffect` gains `RemoveAttackDice` and `Run`. Plans are usable only by the ruler of their source, the origin-site offer to a non-ruler and Brass Army's empty-relic requirement are gone, and a defender's plan may carry a cost.
- **The option's price (Task 3).** The option carries what choosing it costs as `DecisionOption.Priced(option, OptionPrice)`, and the projector words the price as the option's details. The price is read from the dry run of the plan's application, so a Gleaming Armor surcharge appears in it. The design proposed a "cost preview on the option, shown by the projector"; this is that, built on the option itself because the preview gate a procedure opts into (`requiresPlayableOption`) cannot be used by Campaign, whose first step spends Supply before any decision.
- **What a used plan does later is a kit hook, not an effect kind (Task 3, and `wrapping` in Task 5).** E8 proposed an "after the outcome" kind of `CampaignPlanEffect`. A plan is offered at its window and is not re-derived at the end of the Campaign, so nothing would hold the effect until then. The `BattlePlan` kit therefore installs `later` (operations appended to a later window, run only when the plan was chosen) and `wrapping` (the same, but able to add before the window's own children too), which read the picks from `PowerCtx.answered` and the outcome from the recorded result. Outriders, Mercenaries, Battle Honors and Warning Signals use `later`, Sticky Fire uses `wrapping`. A plan a bandit defender applies is not an answer, so the application records it as a pool marker, which the hooks read as they read a pick.
- **`PowerCtx.answered` (Task 2).** A contribution can read the decisions answered so far in the running action. Nothing exposed them, and a plan needs to know which plans were chosen.
- **A `Repeat` pass that records nothing and asks nothing ends the loop (Task 2).** A guard reads only state and answers, so such a pass cannot change what it reads, and the loop would repeat for ever. The plan window relies on it to end when nothing is left to offer.
- **Replay settles a recorded `PayCost` (Task 2).** E4 specified that off-turn settlement is one function used by validation, execution and replay. The pipeline did it, but the walker's replay applied a recorded `PayCost` with a raw executor, so a defender's payment, settled when it ran, rested on the card in the state the command returned (a live command's state is the replay of its own events). No earlier power paid off turn. Task 2 makes replay use the same settlement.
- **The campaign decision prefix (Task 5).** The registry recognised a Campaign's decision by an exact set of ids. It now recognises the prefix `campaign.`, as a Muster's decisions are recognised by `muster.`, so a question a power asks inside a Campaign (Sticky Fire, Warning Signals) continues as a Campaign decision.

## What planning found

These facts are read from the code, or established by compiling and running the plan's code in a throwaway copy. They shape the tasks and are not in the design.

1. **The walker walks a composite `PayCost` as its moves.** A `PayCost` in a tree is a composite, so the walker records its `Move` children and the pipeline never sees a `PayCost` to settle. A payment that must settle off turn therefore runs as a batch, `BuildOps` returning `Vector(PayCost(...))`, which the pipeline settles and the journal records as the requested `PayCost`. `CampaignPlanApplication` and Gleaming Armor pay that way.
2. **A plan that asks a question inside its application resumes against changed state.** The tree is derived again on every command. A plan that parks on a decision after paying (Wrestlers' choice of site, Warning Signals' distribution) is resumed against the state its earlier slots changed, and the pick it is resuming is no longer offered. The application therefore has a fixed shape of `Branch` slots whose selection depends only on what the plan itself does not change, the window's node keeps its two slots when the walk is `resuming` inside it whatever is offered, and an offer must not depend on the resources a plan spends, the orientation of its card or the warbands it moves. The walker tells a host it is resuming, and `CampaignPlanWindowSuite` and `WarningSignalsSuite` resume inside a plan.
3. **The dry run parks.** A plan whose application asks a question parks after its payment. The dry run accepts a park, because a decision is not a cost, and reports what ran before it. A cost that has to be checked before the question (a sacrifice with no warband to give) is therefore a `BuildOps` that refuses, ahead of the decision.
4. **`ModifyDicePool` with a negative delta is best-effort.** It takes what the pool holds, up to the count, and skips an empty or missing pool. `RemoveAttackDice` needs nothing more, and Mercenaries' "minimum 0" is free.
5. **`FlipSecrets` with no faceup secret is skipped, not refused.** Gleaming Armor's added cost for the title's plan (a faceup secret turned facedown) checks the secret first and refuses with `InsufficientSecrets`; otherwise a title plan would stay free for a defender with none.
6. **Fold time is before the window's children run.** A `Transform` on the losses window is folded when the window is entered, before the losses kill anything, and again with the same state on a resume, because Sticky Fire's question comes first. Sticky Fire reads what a Conquest would return to the defender from that state.
7. **The four ported plans were registry handlers, and the Campaign suites assumed them.** `CampaignFixture.rules` handed the walker no powers, and the defender's window in several Raid suites existed only because of the title's plan. The fixture now defaults to the production walker power catalog. `raidBoard` gave the defender the first relic in the catalog, which is Sticky Fire; it now takes a relic that prints no plan. The one test that put a facedown denizen at a site is replaced: a site card is always faceup.
8. **A plan's dice are not the force.** Brass Army and Mercenaries change the attack pool, not the committed force, so no loss, sacrifice or placement limit changes.
9. **`Reveal` of a facedown adviser is a `Move` in place with a resulting orientation.** That is what the old code did and what `CampaignPlanApplication` still does, in a `Branch` slot that selects nothing when the adviser is already faceup.
10. **The reviewed catalog is untouched.** `CampaignPowers` still lists Outriders, Brass Army, Watchdog and Bag of Siegeworks with handlers at the plan windows. Nothing resolves those windows for a Campaign, so they are inert, and a test pins the Outriders entry.
11. **Power names in the engine.** The architecture suite scans `gameplay/walker` and `gameplay/operations` for the name of every class that extends `ContributingPower` directly. The kit `BattlePlan` and the plans that extend it are found under `powers`, and the words the engine uses (`OfferHost`, `Offer`) are not their names.
12. **A Campaign parked at a plan window before this slice cannot resume.** The window's tree has a different shape, so a stored parked position no longer addresses a node. Journals are forward-only, as the design already says.
13. **A bandit's plan is not an answer.** A player's plan is a recorded answer that a later window reads, but a bandit defender applies its cost-free plans without asking, so nothing durable says it used one. The application records it in `rollPools` under `campaign.plan-applied.<kind>.<id>`. Nothing rolls that pool, the Campaign clears it with the others, the journal records it as a `ModifyDicePool`, and the marker is only written by the application of a plan that passed the dry run, so a plan a power made unpayable leaves no marker.

## File Structure

All paths are under `src/main/scala/oathdigital/` (production) or `src/test/scala/oathdigital/` (tests) unless written in full. The step blocks below give full paths.

- **Task 1:** modify `model/CampaignTypes.scala`, `serialization/CampaignResultCodec.scala`, `gameplay/actions/campaign/CampaignBattle.scala` and `CampaignOutcome.scala`, `application/CampaignResultProjector.scala`, `shared/src/main/.../ActionProjectionDtos.scala` and `CampaignResultProjectionCodec.scala`, `frontend/src/main/.../CampaignResultPanel.scala`; the eight suites that name the field.
- **Task 2:** modify `gameplay/powerresolver/ContributingPower.scala` and `ContributionCollector.scala`, `gameplay/walker/WalkerPowerGather.scala`, `ProcedureWalker.scala`, `WalkerSimulation.scala` and `WalkerReplay.scala`, `model/CampaignTypes.scala` (the offer types). Test: `gameplay/OfferHostSuite`, `RepeatPassSuite`, `WalkerReplaySettlementSuite`.
- **Task 3:** modify `model/CampaignTypes.scala`, `model/PowerWindow.scala`, `model/Decisions.scala`, `application/WalkerDecisionProjector.scala`, `gameplay/actions/campaign/CampaignSetup.scala`, `CampaignBattle.scala`, `CampaignProcedure.scala`, `gameplay/powers/WalkerPowerCatalog.scala`; rewrite `gameplay/actions/campaign/CampaignPlans.scala` and `CampaignPlanSteps.scala`; create `gameplay/actions/campaign/CampaignPlanChoice.scala`, `CampaignPlanApplication.scala` and `PlanPrice.scala`, `application/PriceDetails.scala`, `gameplay/powers/campaign/BattlePlan.scala`, `PlanContext.scala`, `TitleDefensePlan.scala`, `Outriders.scala`, `BrassArmy.scala`, `Watchdog.scala` and `BattlePlans.scala`. Test: `gameplay/CampaignPlanWindowSuite`, `application/PricedOptionProjectionSuite`; rewrite `gameplay/CampaignPlansSuite`; modify `CampaignFixture` and `CampaignProcedureSuite`.
- **Task 4:** create `gameplay/powers/campaign/PlanDiscard.scala`, `Mercenaries.scala`, `Wrestlers.scala`, `FearsomeShield.scala`, `ToweringRampart.scala`, `CrackedRampart.scala`, `BattleHonors.scala` and `SimplePlans.scala`; modify `gameplay/powers/WalkerPowerCatalog.scala`. Test: `gameplay/powers/campaign/PlanDriver`, `MercenariesSuite`, `WrestlersSuite`, `FearsomeShieldSuite`, `RampartSuite`, `BattleHonorsSuite`; modify `CampaignFixture`.
- **Task 5:** modify `gameplay/powers/campaign/BattlePlan.scala`, `gameplay/actions/campaign/CampaignProcedure.scala`, `gameplay/walker/WalkerProcedureRegistry.scala`, `gameplay/powers/WalkerPowerCatalog.scala`; create `gameplay/powers/campaign/StickyFire.scala` and `PlanRules.scala`. Test: `gameplay/powers/campaign/StickyFireSuite`; modify `CampaignFixture`.
- **Task 6:** create `gameplay/powers/campaign/WarningSignals.scala`; modify `PlanRules.scala`. Test: `gameplay/powers/campaign/WarningSignalsSuite`; modify `PlanDriver`.
- **Task 7:** create `gameplay/powers/campaign/GleamingArmor.scala`; modify `PlanRules.scala`. Test: `gameplay/powers/campaign/GleamingArmorSuite`.

---

## Sub-slice 3a: The rename

### Task 1: `CampaignResult.attackerWins`

**Files:**
- Modify: the model, both journal and projection codecs, the shared DTO, the frontend result panel, the projector and the Campaign steps that read the field (listed in "File Structure").
- Test: the eight suites that construct or read a `CampaignResult`, and the frontend `CampaignResultPanelSuite`.

**Interfaces:**
- Produces: `CampaignResult.attackerWins: Boolean`, true when the attacker prevailed and false when the defender did; the journal key and the projection key `attackerWins`.

The field is called `victorious` and is true when the attacker prevailed, which reads wrongly once a battle plan belongs to the defender: a plan needs to ask "did my user win", and the answer is `attackerWins` for the attacker and `!attackerWins` for the defender. The rename is mechanical, and the wire key changes with it (the design's E8). A journal recorded before it holds a Campaign result the codec no longer reads, which the design accepts (journals are forward-only).

- [ ] **Step 1: Write the tests**

The suites are renamed first, so that they fail to compile until the field is.

In `frontend/src/test/scala/oathdigital/frontend/CampaignResultPanelSuite.scala`, replace:

```scala

  private val conquest = CampaignResultState("red", "conquest", None,
    Vector("site:a"), Vector.empty, 4, Vector("one-sword", "two-swords-skull"),
    3, 1, 1, Vector("one-shield", "doubler"), 4, victorious = true)

  test("nothing is drawn before a Campaign has been fought") {
    assertEquals(draw(None).children.length, 0)
```

with:

```scala

  private val conquest = CampaignResultState("red", "conquest", None,
    Vector("site:a"), Vector.empty, 4, Vector("one-sword", "two-swords-skull"),
    3, 1, 1, Vector("one-shield", "doubler"), 4, attackerWins = true)

  test("nothing is drawn before a Campaign has been fought") {
    assertEquals(draw(None).children.length, 0)
```

In `frontend/src/test/scala/oathdigital/frontend/CampaignResultPanelSuite.scala`, replace:

```scala
  test("a defeat and a Raid against a player read as such") {
    val raid = conquest.copy(kind = "raid", defenderPlayerId = Some("blue"),
      targetSiteIds = Vector.empty,
      raidTargets = Vector("pawn:blue", "relic:blue:r1"), victorious = false)
    val text = draw(Some(raid)).textContent
    assert(text.contains("Raid"), text)
    assert(text.contains("blue"), text)
```

with:

```scala
  test("a defeat and a Raid against a player read as such") {
    val raid = conquest.copy(kind = "raid", defenderPlayerId = Some("blue"),
      targetSiteIds = Vector.empty,
      raidTargets = Vector("pawn:blue", "relic:blue:r1"), attackerWins = false)
    val text = draw(Some(raid)).textContent
    assert(text.contains("Raid"), text)
    assert(text.contains("blue"), text)
```

In `src/test/scala/oathdigital/application/CampaignResultProjectionSuite.scala`, replace:

```scala
      Vector(b.origin.value), Vector.empty, force = 4,
      attackDice = Vector.fill(4)("one-sword"), attackScore = 4, skullLosses = 0,
      sacrificed = 1, defenseDice = Vector.fill(printed(b))("blank"),
      defenseScore = 2, victorious = true)
    Vector(b.actor, b.other).foreach(viewer =>
      assertEquals(view(state, viewer).lastCampaign, Some(expected), viewer.value))
    assertEquals(projector.projectPublic("campaign", LoadedGame(state, 40))
```

with:

```scala
      Vector(b.origin.value), Vector.empty, force = 4,
      attackDice = Vector.fill(4)("one-sword"), attackScore = 4, skullLosses = 0,
      sacrificed = 1, defenseDice = Vector.fill(printed(b))("blank"),
      defenseScore = 2, attackerWins = true)
    Vector(b.actor, b.other).foreach(viewer =>
      assertEquals(view(state, viewer).lastCampaign, Some(expected), viewer.value))
    assertEquals(projector.projectPublic("campaign", LoadedGame(state, 40))
```

In `src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala`, replace:

```scala
    assertEquals(reloaded.state, accepted.state)
    assertEquals(reloaded.nextSequence, accepted.nextSequence)
    val Ready(after) = reloaded.state: @unchecked
    assert(after.game.current.lastCampaignResult.exists(_.victorious))
  }

  test("create advance and reload replay the complete persisted v2 stream") {
```

with:

```scala
    assertEquals(reloaded.state, accepted.state)
    assertEquals(reloaded.nextSequence, accepted.nextSequence)
    val Ready(after) = reloaded.state: @unchecked
    assert(after.game.current.lastCampaignResult.exists(_.attackerWins))
  }

  test("create advance and reload replay the complete persisted v2 stream") {
```

In `src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala`, replace:

```scala
      CampaignKind.Conquest, CampaignDefender.Bandits, Vector(b.origin),
      Vector.empty, force = 4, attackFaces = sword(4), attackScore = 4,
      skullLosses = 0, sacrificed = 0, defenseFaces = blanks(b), defenseScore = 2,
      victorious = true))
    // The board is untouched until the losses: the bandits are gone, the
    // committed force is still on the board, and nothing is placed yet.
    assertEquals(boardWarbands(sacrificed.state, b.actor), 5)
```

with:

```scala
      CampaignKind.Conquest, CampaignDefender.Bandits, Vector(b.origin),
      Vector.empty, force = 4, attackFaces = sword(4), attackScore = 4,
      skullLosses = 0, sacrificed = 0, defenseFaces = blanks(b), defenseScore = 2,
      attackerWins = true))
    // The board is untouched until the losses: the bandits are gone, the
    // committed force is still on the board, and nothing is placed yet.
    assertEquals(boardWarbands(sacrificed.state, b.actor), 5)
```

In `src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala`, replace:

```scala
    val start = committed(game, b, 2)
    val done = game.resolveWalker(start.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(0)).toOption.get
    assertEquals(result(done.state).victorious, false)
    assertEquals(boardWarbands(done.state, b.actor), 4)
    assertEquals(site(done.state, b.origin), SiteForces.Occupied(ForceKind.Bandit, 2))
    assertEquals(done.continue, OathContinue.ActActionSelection(b.actor))
```

with:

```scala
    val start = committed(game, b, 2)
    val done = game.resolveWalker(start.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(0)).toOption.get
    assertEquals(result(done.state).attackerWins, false)
    assertEquals(boardWarbands(done.state, b.actor), 4)
    assertEquals(site(done.state, b.origin), SiteForces.Occupied(ForceKind.Bandit, 2))
    assertEquals(done.continue, OathContinue.ActActionSelection(b.actor))
```

In `src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala`, replace:

```scala
    val won = game.resolveWalker(start.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(1)).toOption.get
    assertEquals(result(won.state).sacrificed, 1)
    assertEquals(result(won.state).victorious, true)
    assertEquals(won.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.placement)))
  }
```

with:

```scala
    val won = game.resolveWalker(start.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(1)).toOption.get
    assertEquals(result(won.state).sacrificed, 1)
    assertEquals(result(won.state).attackerWins, true)
    assertEquals(won.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.placement)))
  }
```

In `src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala`, replace:

```scala
    val done = committed(game, b, 0)
    assertEquals(done.continue, OathContinue.ActActionSelection(b.actor))
    assertEquals(result(done.state).force, 0)
    assertEquals(result(done.state).victorious, false)
    assertEquals(boardWarbands(done.state, b.actor), 0)
  }

```

with:

```scala
    val done = committed(game, b, 0)
    assertEquals(done.continue, OathContinue.ActActionSelection(b.actor))
    assertEquals(result(done.state).force, 0)
    assertEquals(result(done.state).attackerWins, false)
    assertEquals(boardWarbands(done.state, b.actor), 0)
  }

```

In `src/test/scala/oathdigital/gameplay/CampaignRaidSuite.scala`, replace:

```scala
      CampaignRaidTarget.Pawn(b.other), CampaignRaidTarget.Relic(b.other, relic),
      CampaignRaidTarget.Banner(b.other, Banner.PeoplesFavor)))
    assertEquals(result.defenseScore, 3)
    assertEquals(result.victorious, true)
  }

  test("a Raid victory transfers in the printed order and then asks where the pawn goes") {
```

with:

```scala
      CampaignRaidTarget.Pawn(b.other), CampaignRaidTarget.Relic(b.other, relic),
      CampaignRaidTarget.Banner(b.other, Banner.PeoplesFavor)))
    assertEquals(result.defenseScore, 3)
    assertEquals(result.attackerWins, true)
  }

  test("a Raid victory transfers in the printed order and then asks where the pawn goes") {
```

In `src/test/scala/oathdigital/gameplay/operations/RecordCampaignResultSuite.scala`, replace:

```scala
    attackFaces = Vector(AttackDieFace.HollowSword, AttackDieFace.TwoSwordsSkull),
    attackScore = 2, skullLosses = 1, sacrificed = 1,
    defenseFaces = Vector(DefenseDieFace.OneShield), defenseScore = 4,
    victorious = false)

  private def record(ready: ReadyGame, result: CampaignResult) =
    new OperationExecutor().executeAll(ready, Vector(RecordCampaignResult(result)))
```

with:

```scala
    attackFaces = Vector(AttackDieFace.HollowSword, AttackDieFace.TwoSwordsSkull),
    attackScore = 2, skullLosses = 1, sacrificed = 1,
    defenseFaces = Vector(DefenseDieFace.OneShield), defenseScore = 4,
    attackerWins = false)

  private def record(ready: ReadyGame, result: CampaignResult) =
    new OperationExecutor().executeAll(ready, Vector(RecordCampaignResult(result)))
```

In `src/test/scala/oathdigital/gameplay/operations/RecordCampaignResultSuite.scala`, replace:

```scala
  }

  test("the next Campaign's result replaces the last one") {
    val later = conquest.copy(victorious = true, sacrificed = 0)
    val once = TestGameFixtures.ready.updateCurrent(
      _.copy(lastCampaignResult = Some(conquest)))
    assertEquals(record(once, later), Some(later))
```

with:

```scala
  }

  test("the next Campaign's result replaces the last one") {
    val later = conquest.copy(attackerWins = true, sacrificed = 0)
    val once = TestGameFixtures.ready.updateCurrent(
      _.copy(lastCampaignResult = Some(conquest)))
    assertEquals(record(once, later), Some(later))
```

In `src/test/scala/oathdigital/serialization/CampaignResultCodecSuite.scala`, replace:

```scala
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
```

with:

```scala
    attackFaces = Vector(AttackDieFace.HollowSword, AttackDieFace.TwoSwordsSkull),
    attackScore = 2, skullLosses = 1, sacrificed = 1,
    defenseFaces = Vector(DefenseDieFace.OneShield, DefenseDieFace.Doubler),
    defenseScore = 4, attackerWins = false)
  private val raid = conquest.copy(kind = CampaignKind.Raid,
    defender = CampaignDefender.Player(PlayerId("blue")),
    targetSites = Vector.empty,
    raidTargets = Vector(CampaignRaidTarget.Pawn(PlayerId("blue")),
      CampaignRaidTarget.Relic(PlayerId("blue"), RelicId("r1")),
      CampaignRaidTarget.Banner(PlayerId("blue"), Banner.PeoplesFavor)),
    attackerWins = true)
  private val empty = conquest.copy(attackFaces = Vector.empty,
    defenseFaces = Vector.empty, attackScore = 0, skullLosses = 0,
    sacrificed = 0, force = 0)
```

In `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`, replace:

```scala
        attackFaces = Vector(AttackDieFace.OneSword), attackScore = 1,
        skullLosses = 0, sacrificed = 0,
        defenseFaces = Vector(DefenseDieFace.Blank), defenseScore = 2,
        victorious = false)),
      SetOathkeeper(Some(PlayerId("red"))),
      SetOathkeeper(None))

```

with:

```scala
        attackFaces = Vector(AttackDieFace.OneSword), attackScore = 1,
        skullLosses = 0, sacrificed = 0,
        defenseFaces = Vector(DefenseDieFace.Blank), defenseScore = 2,
        attackerWins = false)),
      SetOathkeeper(Some(PlayerId("red"))),
      SetOathkeeper(None))

```


- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, for example `unknown parameter name: attackerWins`.

- [ ] **Step 3: Implement**

In `frontend/src/main/scala/oathdigital/frontend/CampaignResultPanel.scala`, replace:

```scala
      box.appendChild(text("p", "campaign-result-defense",
        s"Defense dice: ${dice(result.defenseDice)}. Defense ${result.defenseScore}."))
      box.appendChild(text("p", "campaign-result-outcome",
        if (result.victorious) "Victory" else "Defeat"))
      panel.appendChild(box)
    }
}
```

with:

```scala
      box.appendChild(text("p", "campaign-result-defense",
        s"Defense dice: ${dice(result.defenseDice)}. Defense ${result.defenseScore}."))
      box.appendChild(text("p", "campaign-result-outcome",
        if (result.attackerWins) "Victory" else "Defeat"))
      panel.appendChild(box)
    }
}
```

In `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala`, replace:

```scala
    sacrificed: Int,
    defenseDice: Vector[String],
    defenseScore: Int,
    victorious: Boolean)
```

with:

```scala
    sacrificed: Int,
    defenseDice: Vector[String],
    defenseScore: Int,
    attackerWins: Boolean)
```

In `shared/src/main/scala/oathdigital/protocol/projection/CampaignResultProjectionCodec.scala`, replace:

```scala
private[projection] object CampaignResultProjectionCodec {
  private val Fields = Set("attackerPlayerId", "kind", "defenderPlayerId",
    "targetSiteIds", "raidTargets", "force", "attackDice", "attackScore",
    "skullLosses", "sacrificed", "defenseDice", "defenseScore", "victorious")

  def encode(value: CampaignResultProjection): ujson.Value = ujson.Obj(
    "attackerPlayerId" -> value.attackerPlayerId, "kind" -> value.kind,
```

with:

```scala
private[projection] object CampaignResultProjectionCodec {
  private val Fields = Set("attackerPlayerId", "kind", "defenderPlayerId",
    "targetSiteIds", "raidTargets", "force", "attackDice", "attackScore",
    "skullLosses", "sacrificed", "defenseDice", "defenseScore", "attackerWins")

  def encode(value: CampaignResultProjection): ujson.Value = ujson.Obj(
    "attackerPlayerId" -> value.attackerPlayerId, "kind" -> value.kind,
```

In `shared/src/main/scala/oathdigital/protocol/projection/CampaignResultProjectionCodec.scala`, replace:

```scala
    "attackScore" -> value.attackScore, "skullLosses" -> value.skullLosses,
    "sacrificed" -> value.sacrificed,
    "defenseDice" -> encoded(value.defenseDice)(ujson.Str(_)),
    "defenseScore" -> value.defenseScore, "victorious" -> value.victorious)

  def decode(raw: ujson.Value, path: String): Result[CampaignResultProjection] = for {
    value <- obj(raw, path)
```

with:

```scala
    "attackScore" -> value.attackScore, "skullLosses" -> value.skullLosses,
    "sacrificed" -> value.sacrificed,
    "defenseDice" -> encoded(value.defenseDice)(ujson.Str(_)),
    "defenseScore" -> value.defenseScore, "attackerWins" -> value.attackerWins)

  def decode(raw: ujson.Value, path: String): Result[CampaignResultProjection] = for {
    value <- obj(raw, path)
```

In `shared/src/main/scala/oathdigital/protocol/projection/CampaignResultProjectionCodec.scala`, replace:

```scala
    sacrificed <- int(value, "sacrificed", path)
    defenseDice <- strings(value, "defenseDice", path)
    defenseScore <- int(value, "defenseScore", path)
    victorious <- bool(value, "victorious", path)
  } yield CampaignResultProjection(attacker, kind, defender, sites, raid, force,
    attackDice, attackScore, skulls, sacrificed, defenseDice, defenseScore,
    victorious)
}
```

with:

```scala
    sacrificed <- int(value, "sacrificed", path)
    defenseDice <- strings(value, "defenseDice", path)
    defenseScore <- int(value, "defenseScore", path)
    attackerWins <- bool(value, "attackerWins", path)
  } yield CampaignResultProjection(attacker, kind, defender, sites, raid, force,
    attackDice, attackScore, skulls, sacrificed, defenseDice, defenseScore,
    attackerWins)
}
```

In `src/main/scala/oathdigital/application/CampaignResultProjector.scala`, replace:

```scala
        result.force, result.attackFaces.map(attackFace), result.attackScore,
        result.skullLosses, result.sacrificed,
        result.defenseFaces.map(defenseFace), result.defenseScore,
        result.victorious)
    }

  // The wire spellings, duplicated because the application layer may not
```

with:

```scala
        result.force, result.attackFaces.map(attackFace), result.attackScore,
        result.skullLosses, result.sacrificed,
        result.defenseFaces.map(defenseFace), result.defenseScore,
        result.attackerWins)
    }

  // The wire spellings, duplicated because the application layer may not
```

In `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignBattle.scala`, replace:

```scala
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
```

with:

```scala
      .map { attacker =>
        val survivors = result.force - result.skullLosses - result.sacrificed
        val deaths = result.skullLosses + result.sacrificed +
          (if (result.attackerWins) 0 else survivors / 2)
        val own: Vector[CoreOperation] = Option.when(deaths > 0)(Kill(
          Piece.Warbands(ForceKind.Exile(attacker.lineage), deaths),
          PositionedLocation(Location.PlayArea(result.attacker)))).toVector
        own ++ (if (!result.attackerWins) Vector.empty
          else result.kind match {
            case CampaignKind.Conquest => conquestLosses(ready, result)
            case CampaignKind.Raid => raidBoardLosses(ready, result)
```

In `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignOutcome.scala`, replace:

```scala
    val losses: Operation = Sequence(Vector[Operation](BuildOps((ready, _) =>
      CampaignBattle.losses(ready, result))), Some(PowerWindow.CampaignLosses))
    val resolution: Vector[Operation] =
      if (!result.victorious) Vector.empty
      else result.kind match {
        case CampaignKind.Conquest => CampaignConquest.steps(actor, result)
        case CampaignKind.Raid => CampaignRaid.steps(ready, actor, result)
```

with:

```scala
    val losses: Operation = Sequence(Vector[Operation](BuildOps((ready, _) =>
      CampaignBattle.losses(ready, result))), Some(PowerWindow.CampaignLosses))
    val resolution: Vector[Operation] =
      if (!result.attackerWins) Vector.empty
      else result.kind match {
        case CampaignKind.Conquest => CampaignConquest.steps(actor, result)
        case CampaignKind.Raid => CampaignRaid.steps(ready, actor, result)
```

In `src/main/scala/oathdigital/model/CampaignTypes.scala`, replace:

```scala
  *
  * `attackScore` is the attack after the skull cap and any Outriders, before
  * the sacrifice; `defenseScore` is the defense dice score plus the defender's
  * board force. The attacker prevails when `attackTotal > defenseScore`.
  */
final case class CampaignResult(
    attacker: PlayerId,
```

with:

```scala
  *
  * `attackScore` is the attack after the skull cap and any Outriders, before
  * the sacrifice; `defenseScore` is the defense dice score plus the defender's
  * board force. The attacker prevails when `attackTotal > defenseScore`, and
  * `attackerWins` records that fact: it is true for an attacker victory and
  * false for a defender victory, whichever side a battle plan's user is on.
  */
final case class CampaignResult(
    attacker: PlayerId,
```

In `src/main/scala/oathdigital/model/CampaignTypes.scala`, replace:

```scala
    sacrificed: Int,
    defenseFaces: Vector[DefenseDieFace],
    defenseScore: Int,
    victorious: Boolean
) {
  def attackTotal: Int = attackScore + sacrificed
}
```

with:

```scala
    sacrificed: Int,
    defenseFaces: Vector[DefenseDieFace],
    defenseScore: Int,
    attackerWins: Boolean
) {
  def attackTotal: Int = attackScore + sacrificed
}
```

In `src/main/scala/oathdigital/serialization/CampaignResultCodec.scala`, replace:

```scala
      "defenseFaces" -> ujson.Arr.from(result.defenseFaces.map(face =>
        ujson.Str(encodeDefenseFace(face)))),
      "defenseScore" -> result.defenseScore,
      "victorious" -> result.victorious)

  protected final def decodeCampaignResult(value: ujson.Value, path: String)
      : Either[WireError, CampaignResult] = try {
```

with:

```scala
      "defenseFaces" -> ujson.Arr.from(result.defenseFaces.map(face =>
        ujson.Str(encodeDefenseFace(face)))),
      "defenseScore" -> result.defenseScore,
      "attackerWins" -> result.attackerWins)

  protected final def decodeCampaignResult(value: ujson.Value, path: String)
      : Either[WireError, CampaignResult] = try {
```

In `src/main/scala/oathdigital/serialization/CampaignResultCodec.scala`, replace:

```scala
      raidTargets, value("force").num.toInt, attackFaces,
      value("attackScore").num.toInt, value("skullLosses").num.toInt,
      value("sacrificed").num.toInt, defenseFaces,
      value("defenseScore").num.toInt, value("victorious").bool)
  } catch {
    case NonFatal(error) => Left(InvalidValue(path,
      Option(error.getMessage).getOrElse("invalid Campaign result")))
```

with:

```scala
      raidTargets, value("force").num.toInt, attackFaces,
      value("attackScore").num.toInt, value("skullLosses").num.toInt,
      value("sacrificed").num.toInt, defenseFaces,
      value("defenseScore").num.toInt, value("attackerWins").bool)
  } catch {
    case NonFatal(error) => Left(InvalidValue(path,
      Option(error.getMessage).getOrElse("invalid Campaign result")))
```


- [ ] **Step 4: Run the whole suites**

Run: `./sbtw test` and `./sbtw frontend/test`
Expected: PASS (1383 tests in the backend suite, and the frontend suite).

- [ ] **Step 5: Run the architecture check**

Run: `python3 scripts/check-architecture.py`
Expected: `architecture check passed`.

- [ ] **Step 6: Record sub-slice 3a**

The docs say what the design and the campaign architecture note said before, now true. Each addition sits on its own line, to ease merging with slice 4.

In `docs/architecture/bounded-campaign.md`, replace:

```markdown
  defense.
- **Result.** `RecordCampaignResult` writes the public `CampaignResult`. It is the
  only thing the steps after the losses read, because the losses change the board.

## Losses and resolution

```

with:

```markdown
  defense.
- **Result.** `RecordCampaignResult` writes the public `CampaignResult`. It is the
  only thing the steps after the losses read, because the losses change the board.
  `attackerWins` is true when the attacker prevailed and false when the defender
  did, whichever side a battle plan's user is on.

## Losses and resolution

```

In `docs/architecture/bounded-campaign.md`, replace:

```markdown

Each answer is a `WalkerStepRecorded` carrying a `ChoicePayload`. Each automatic roll
is a `RollPayload` with `automatic = true`, which replay applies without asking the
dice source. Every other step is a recorded operation batch, including
`RecordCampaignResult`. The seven legacy Campaign events no longer exist, and
journals are forward-only.

```

with:

```markdown

Each answer is a `WalkerStepRecorded` carrying a `ChoicePayload`. Each automatic roll
is a `RollPayload` with `automatic = true`, which replay applies without asking the
dice source. The recorded result carries the key `attackerWins`, so a journal recorded
before that name cannot be read. Every other step is a recorded operation batch, including
`RecordCampaignResult`. The seven legacy Campaign events no longer exist, and
journals are forward-only.

```

In `docs/superpowers/specs/2026-09-20-powers-design.md`, replace:

```markdown
# Powers Batch 1: Engine Changes and Slicing

> Status: design approved 2026-09-20. Slice 0 (E1 to E5), slice 1a, slice 1b, slice 1c, slice 1d and slice 2 (sub-slices 2a to 2f) are implemented; see the [Slice 0 plan](../plans/2026-09-20-powers-slice-0-foundations.md), the [slice 1a plan](../plans/2026-09-20-powers-slice-1a-when-played-and-simple-actions.md), the [slice 1b plan](../plans/2026-09-20-powers-slice-1b-dice-and-relic-draws.md) the [slice 1c plan](../plans/2026-09-20-powers-slice-1c-targets-and-information.md) the [slice 1d plan](../plans/2026-09-20-powers-slice-1d-movement.md) and the [slice 2 plan](../plans/2026-09-20-powers-slice-2-modifiers-restrictions-triggers.md) (all six sub-slices). Per-power rules are in [the rulings appendix](2026-09-20-powers-rulings.md). Extends the [procedure walker design](2026-09-05-procedure-walker-design.md) and follows the [Campaign port](2026-09-19-campaign-walker-design.md). Each slice below gets its own implementation plan, and slice 1 is split into four.

## Goal and scope

```

with:

```markdown
# Powers Batch 1: Engine Changes and Slicing

> Status: design approved 2026-09-20. Slice 0 (E1 to E5), slice 1a, slice 1b, slice 1c, slice 1d and slice 2 (sub-slices 2a to 2f) are implemented; see the [Slice 0 plan](../plans/2026-09-20-powers-slice-0-foundations.md), the [slice 1a plan](../plans/2026-09-20-powers-slice-1a-when-played-and-simple-actions.md), the [slice 1b plan](../plans/2026-09-20-powers-slice-1b-dice-and-relic-draws.md) the [slice 1c plan](../plans/2026-09-20-powers-slice-1c-targets-and-information.md) the [slice 1d plan](../plans/2026-09-20-powers-slice-1d-movement.md) and the [slice 2 plan](../plans/2026-09-20-powers-slice-2-modifiers-restrictions-triggers.md) (all six sub-slices). Per-power rules are in [the rulings appendix](2026-09-20-powers-rulings.md). Extends the [procedure walker design](2026-09-05-procedure-walker-design.md) and follows the [Campaign port](2026-09-19-campaign-walker-design.md). Each slice below gets its own implementation plan, and slice 1 is split into four.

> Slice 3 (battle plans) is planned in four sub-slices: see its [plan](../plans/2026-09-20-powers-slice-3-battle-plans.md). Implemented so far: 3a.

## Goal and scope

```

In `docs/superpowers/specs/2026-09-20-powers-design.md`, replace:

```markdown
- Plan application is a small operation class carrying the owner, side and source, in window `CampaignPlanApplication`. Powers such as Gleaming Armor match on it, as Silver Tongue matches `PlacementTree`.
- Plans are usable only by the source's ruler: the holder for advisers and relics, the site's ruler for site cards and edifices. Current code also offers origin-site cards to a non-ruler and requires Brass Army's card to be empty. Both are removed. A defender plan may now carry a cost.
- Facedown advisers remain usable as plans and are revealed when used (NF p. 13).
- `CampaignResult.victorious` is renamed `attackerWins`. It touches the model, `CampaignResultProjectionCodec`, the shared DTO, the frontend result panel, the Campaign suites and docs. The wire key changes with it.

### E9. Enclosing action on `PowerCtx`

```

with:

```markdown
- Plan application is a small operation class carrying the owner, side and source, in window `CampaignPlanApplication`. Powers such as Gleaming Armor match on it, as Silver Tongue matches `PlacementTree`.
- Plans are usable only by the source's ruler: the holder for advisers and relics, the site's ruler for site cards and edifices. Current code also offers origin-site cards to a non-ruler and requires Brass Army's card to be empty. Both are removed. A defender plan may now carry a cost.
- Facedown advisers remain usable as plans and are revealed when used (NF p. 13).
- `CampaignResult.victorious` is renamed `attackerWins`. It touches the model, `CampaignResultProjectionCodec`, the shared DTO, the frontend result panel, the Campaign suites and docs. The wire key changes with it. Implemented in slice 3a.

### E9. Enclosing action on `PowerCtx`

```

In `docs/superpowers/specs/2026-09-20-powers-design.md`, replace:

```markdown
| 4. Banner faces | Wandering Flame (move, place a secret), Mob | E3's banner source, E6's `PlacementRules` |

Slices 2, 3 and 4 are independent once slice 0 lands. Slices 1a to 1d need only slice 0. They are planned one at a time, so each plan can use what the previous one learned. Slice 1a is planned: see its [plan](../plans/2026-09-20-powers-slice-1a-when-played-and-simple-actions.md). Slice 1b is planned: see its [plan](../plans/2026-09-20-powers-slice-1b-dice-and-relic-draws.md). Slice 1c is planned: see its [plan](../plans/2026-09-20-powers-slice-1c-targets-and-information.md). Slice 1d is planned: see its [plan](../plans/2026-09-20-powers-slice-1d-movement.md). Slice 2 is planned in six sub-slices: see its [plan](../plans/2026-09-20-powers-slice-2-modifiers-restrictions-triggers.md). The order above is the recommended one.

## Walker shapes for powers

```

with:

```markdown
| 4. Banner faces | Wandering Flame (move, place a secret), Mob | E3's banner source, E6's `PlacementRules` |

Slices 2, 3 and 4 are independent once slice 0 lands. Slices 1a to 1d need only slice 0. They are planned one at a time, so each plan can use what the previous one learned. Slice 1a is planned: see its [plan](../plans/2026-09-20-powers-slice-1a-when-played-and-simple-actions.md). Slice 1b is planned: see its [plan](../plans/2026-09-20-powers-slice-1b-dice-and-relic-draws.md). Slice 1c is planned: see its [plan](../plans/2026-09-20-powers-slice-1c-targets-and-information.md). Slice 1d is planned: see its [plan](../plans/2026-09-20-powers-slice-1d-movement.md). Slice 2 is planned in six sub-slices: see its [plan](../plans/2026-09-20-powers-slice-2-modifiers-restrictions-triggers.md). The order above is the recommended one.

Slice 3 is planned in four sub-slices: 3a the rename of `CampaignResult.victorious`, 3b the plan window (E8) with the four plans already in the code, 3c the plans that change the dice or pay after the Campaign, and 3d Warning Signals, Sticky Fire and Gleaming Armor. See its [plan](../plans/2026-09-20-powers-slice-3-battle-plans.md).

## Walker shapes for powers

```

In `docs/superpowers/specs/2026-09-20-powers-rulings.md`, replace:

```markdown

Off-turn settlement for a defender's plan payment: favor moves directly to the matching suit bank, and a secret becomes a `FlipSecrets(FaceUp, FaceDown)`. Nothing rests on the card.

## Slice 4: banner faces

| Power | Ruling |
```

with:

```markdown

Off-turn settlement for a defender's plan payment: favor moves directly to the matching suit bank, and a secret becomes a `FlipSecrets(FaceUp, FaceDown)`. Nothing rests on the card.

### Slice 3 implementation notes

- **3a:** `CampaignResult.victorious` is now `attackerWins` in the model, the journal codec, the shared DTO and its codec, the result panel and the suites. It is true when the attacker prevailed and false when the defender did. The wire key changes with it, and journals are forward-only, so a game whose journal holds a recorded Campaign result cannot be read after this change.

## Slice 4: banner faces

| Power | Ruling |
```


Run: `python3 scripts/check-markdown-links.py`
Expected: `Markdown link check passed`.

- [ ] **Step 7: Commit**

```bash
git add src shared frontend
git commit -m "refactor: rename CampaignResult.victorious to attackerWins

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
git add docs
git commit -m "docs: record slice 3a

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Sub-slice 3b: The plan window (E8)

### Task 2: The `Offer` seam in the walker

**Files:**
- Modify: the files listed for Task 2 in "File Structure".
- Test: `src/test/scala/oathdigital/gameplay/OfferHostSuite.scala`, `RepeatPassSuite.scala`, `WalkerReplaySettlementSuite.scala`.

**Interfaces:**
- Produces: `PowerCtx.answered: Vector[Answered]` (default empty); `Offer(plan: PowerCtx => Option[CampaignPlanOffer]) extends Contribution`; `trait OfferHost extends Operation { def expand(offers: Vector[OfferedPlan], pass: OfferHost.Pass): Vector[Operation] }` with `OfferHost.Pass(state: ReadyGame, answered: Vector[Answered], resuming: Boolean, applies: Operation => Either[OathViolation, Vector[CoreOperation]])`; `GatheredContributions.offers`; `WalkerSimulation.applies(tree, state, powers)`; `CampaignPlanOffer(source, label, costs, effects)` and `OfferedPlan(power, offer)`.
- Consumes: `PowerWindow`, `WalkerPowers`, the existing collector and window fold.

This task adds the generic half of E8 and touches no Campaign code. A windowed node that implements `OfferHost` is handed, when its window is folded, the plans of every `Offer` contribution hooked there, in the deterministic power order, after the window's transforms have run. It is told whether the walk is resuming inside it, and it can dry-run any operation through the same windows and powers the walk uses. The Campaign's plan window (Task 3) is the first host. Three smaller engine changes ride with the seam because the window needs them: the decisions answered so far are readable from a contribution's context, a `Repeat` pass that records nothing and asks nothing ends the loop, and replay settles a recorded `PayCost` the way the pipeline ran it.

- [ ] **Step 1: Write the tests**

`OfferHostSuite` uses hosts and offering powers of its own. `RepeatPassSuite` pins the loop rule. `WalkerReplaySettlementSuite` replays a recorded off-turn `PayCost`, and fails without the change to `WalkerReplay`, where the payment would rest on the card.

Create `src/test/scala/oathdigital/gameplay/OfferHostSuite.scala`:

```scala
package oathdigital.gameplay

import scala.collection.mutable

import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, Offer, OfferHost, PowerCtx, Transform}
import oathdigital.gameplay.setup.FirstGameSetupFixture.initialReady
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._

/** The `Offer` contribution and the node that hosts it: what the walker hands a
  * host, in which order, and how a host is told the walk is resuming inside it.
  */
class OfferHostSuite extends munit.FunSuite {
  private val ready = initialReady
  private val actor = ready.game.current.turn.activePlayer
  private val window = PowerWindow.CampaignAttackerBattlePlans

  private def offer(name: String): CampaignPlanOffer = CampaignPlanOffer(
    CampaignPlanSource.Adviser(actor, DenizenId(name)), name, Vector.empty,
    Vector.empty)

  /** A power that offers what `plan` says, whatever the window's context. */
  private final case class Offering(name: String,
      plan: PowerCtx => Option[CampaignPlanOffer]) extends ContributingPower {
    def id: PowerId = PowerId(s"test.$name")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(window -> Vector(Offer(plan)))
  }

  /** A host that records each fold and expands to a `GainSupply` per offer. */
  private final class Host(gain: Int = 1) extends OfferHost {
    val folds: mutable.Buffer[(Vector[OfferedPlan], Boolean, Vector[Answered])] =
      mutable.Buffer.empty
    override val window: Option[PowerWindow] = Some(OfferHostSuite.this.window)
    override val children: Vector[Operation] = Vector.empty
    def expand(offers: Vector[OfferedPlan], pass: OfferHost.Pass)
        : Vector[Operation] = {
      folds += ((offers, pass.resuming, pass.answered))
      offers.map(_ => GainSupply(actor, gain))
    }
  }

  private def low(state: ReadyGame): ReadyGame = state.updateCurrent(current =>
    current.copy(players = current.players.map(p =>
      if (p.player == actor) p.copy(board = p.board.copy(supply = SupplyTrack(1)))
      else p)))

  test("a host is handed the offers of the powers hooked at its window, in power order") {
    val host = new Host
    val powers = WalkerPowers(Vector(Offering("b", _ => Some(offer("b"))),
      Offering("a", _ => Some(offer("a"))), Offering("none", _ => None)))
    val outcome = ProcedureWalker.advance(low(ready), host, None, powers)
    assert(outcome.isRight)
    assertEquals(host.folds.toVector.map(_._1.map(_.power.value)), Vector(
      Vector("test.a", "test.b")))
    assertEquals(host.folds.head._2, false)
    assertEquals(host.folds.head._3, Vector.empty[Answered])
  }

  test("a host with nothing offered expands to nothing, and a power's other window is not gathered") {
    val host = new Host
    val elsewhere = new ContributingPower {
      def id: PowerId = PowerId("test.elsewhere")
      def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
        PowerWindow.CampaignDefenderBattlePlans -> Vector(Offer(_ =>
          Some(offer("elsewhere")))))
    }
    ProcedureWalker.advance(low(ready), host, None, WalkerPowers(Vector(elsewhere)))
    assertEquals(host.folds.head._1, Vector.empty[OfferedPlan])
  }

  test("a transform at the host's window runs before the expansion") {
    val host = new Host(gain = 2)
    val first = new ContributingPower {
      def id: PowerId = PowerId("test.first")
      def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map(window ->
        Vector(Transform((_, children) => children :+ GainSupply(actor, 1)),
          Offer(_ => Some(offer("x")))))
    }
    val Right(WalkerOutcome.Finished(after, events)) = ProcedureWalker.advance(
      low(ready), host, None, WalkerPowers(Vector(first))): @unchecked
    assertEquals(after.game.current.players.find(_.player == actor).get
      .board.supply.supply, 4)
    assertEquals(events.size, 2)
  }

  test("a host is told the walk is resuming inside it, and sees the answers recorded so far") {
    val question = Decide("test.question", actor, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Button(DecisionOptionRef.Button("yes"), "Yes"))))
    val hosted = new OfferHost {
      val seen: mutable.Buffer[(Boolean, Vector[Answered])] = mutable.Buffer.empty
      override val window: Option[PowerWindow] = Some(OfferHostSuite.this.window)
      override val children: Vector[Operation] = Vector.empty
      def expand(offers: Vector[OfferedPlan], pass: OfferHost.Pass)
          : Vector[Operation] = {
        seen += ((pass.resuming, pass.answered))
        Vector(question)
      }
    }
    val tree = Sequence(Vector[Operation](hosted))
    val state = low(ready)
    val Right(WalkerOutcome.Parked(pending, _)) = ProcedureWalker.advance(state,
      tree, None, WalkerPowers.empty): @unchecked
    assertEquals(hosted.seen.toVector, Vector((false, Vector.empty[Answered])))
    val answer = Answered("test.question", DecisionAnswer.ChooseOneAnswer(
      DecisionOptionRef.Button("yes")), actor)
    assert(ProcedureWalker.resolve(state, tree, pending, answer,
      WalkerPowers.empty).isRight)
    // The resume folds the host again, inside it.
    assertEquals(hosted.seen.toVector.last, (true, Vector.empty[Answered]))
    // The parked decision is found through the same fold.
    assertEquals(ProcedureWalker.parkedDecide(state, tree, pending,
      WalkerPowers.empty).map(_.decisionId), Some("test.question"))
  }

  test("an offer reads the decisions answered before its window in its context") {
    val question = Decide("test.question", actor, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Button(DecisionOptionRef.Button("yes"), "Yes"))))
    val host = new Host
    val reading = Offering("reader", ctx => Option.when(ctx.answered.exists(
      _.decisionId == "test.question"))(offer("answered")))
    val tree = Sequence(Vector[Operation](question, host))
    val powers = WalkerPowers(Vector(reading))
    val state = low(ready)
    val Right(WalkerOutcome.Parked(pending, _)) = ProcedureWalker.advance(state,
      tree, None, powers): @unchecked
    assertEquals(host.folds.size, 0)
    ProcedureWalker.resolve(state, tree, pending, Answered("test.question",
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("yes")), actor),
      powers)
    assertEquals(host.folds.toVector.map(_._1.map(_.offer.label)), Vector(
      Vector("answered")))
  }

  test("a host can ask whether an operation would run, and what it would record") {
    val results: mutable.Buffer[Either[OathViolation, Vector[CoreOperation]]] =
      mutable.Buffer.empty
    val host = new OfferHost {
      override val window: Option[PowerWindow] = Some(OfferHostSuite.this.window)
      override val children: Vector[Operation] = Vector.empty
      def expand(offers: Vector[OfferedPlan], pass: OfferHost.Pass)
          : Vector[Operation] = {
        results += pass.applies(GainSupply(actor, 1))
        results += pass.applies(SpendSupply(actor, 6))
        results += pass.applies(Sequence(Vector[Operation](GainSupply(actor, 1),
          Decide("test.question", actor, DecisionQuery.ChooseOne(Vector(
            DecisionOption.Button(DecisionOptionRef.Button("yes"), "Yes")))))))
        Vector.empty
      }
    }
    val state = low(ready)
    ProcedureWalker.advance(state, host, None, WalkerPowers.empty)
    assertEquals(results(0), Right(Vector[CoreOperation](GainSupply(actor, 1))))
    assert(results(1).isLeft)
    assertEquals(results(2), Right(Vector[CoreOperation](GainSupply(actor, 1))))
    // Asking changed nothing.
    assertEquals(state.game.current.players.find(_.player == actor).get
      .board.supply.supply, 1)
  }
}
```

Create `src/test/scala/oathdigital/gameplay/RepeatPassSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.setup.FirstGameSetupFixture.initialReady
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._

/** When a `Repeat` stops. Its guard reads only state and answers, so a pass that
  * records nothing and asks nothing cannot change what the guard reads, and the
  * loop ends instead of repeating for ever.
  */
class RepeatPassSuite extends munit.FunSuite {
  private val actor = initialReady.game.current.turn.activePlayer
  private val low: ReadyGame = initialReady.updateCurrent(current =>
    current.copy(players = current.players.map(p =>
      if (p.player == actor) p.copy(board = p.board.copy(supply = SupplyTrack(1)))
      else p)))

  private def supply(state: ReadyGame): Int = state.game.current.players
    .find(_.player == actor).get.board.supply.supply

  private def run(tree: Operation) = ProcedureWalker.advance(low, tree, None,
    WalkerPowers.empty)

  test("a pass that records nothing ends the loop, and the walk goes on") {
    val Right(WalkerOutcome.Finished(after, events)) = run(Sequence(Vector[Operation](
      Repeat((_, _) => true, Sequence(Vector.empty)),
      GainSupply(actor, 1)))): @unchecked
    assertEquals(supply(after), 2)
    assertEquals(events.size, 1)
  }

  test("a pass that records something repeats while its guard holds") {
    val Right(WalkerOutcome.Finished(after, events)) = run(Repeat(
      (state, _) => supply(state) < 4, GainSupply(actor, 1))): @unchecked
    assertEquals(supply(after), 4)
    assertEquals(events.size, 3)
  }

  test("a pass that parks on a decision is not an empty pass") {
    val question = Decide("test.question", actor, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Button(DecisionOptionRef.Button("yes"), "Yes"))))
    val outcome = run(Repeat((_, _) => true, question))
    assert(outcome.exists(_.isInstanceOf[WalkerOutcome.Parked]))
  }
}
```

Create `src/test/scala/oathdigital/gameplay/WalkerReplaySettlementSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{DeltaMeaning, ProcedureWalker, WalkerStepPayload, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Replay applies a recorded `PayCost` the way the pipeline ran it: a payer who
  * is not the active player settles at once, so the recorded (requested)
  * operation expands to the same moves it did when it first ran. Without this a
  * live command, whose state is the replay of its own events, would rest a
  * defender's payment on the card it was settled off.
  */
class WalkerReplaySettlementSuite extends munit.FunSuite {
  private val base = initialReady
  private val current = base.game.current
  private val active = current.turn.activePlayer
  private val payer = current.players.map(_.player).find(_ != active).get
  private val siteId = current.map.inPlay.head
  private val denizen = current.commonCards.worldDeck.collectFirst {
    case id: DenizenId => id }.get
  private val suit = catalog.suitOf(denizen).get

  private val arranged: ReadyGame = base.updateCurrent(c => c.copy(
    commonCards = c.commonCards.copy(worldDeck =
      c.commonCards.worldDeck.filterNot(_ == denizen)),
    players = c.players.map(p => p.copy(board = p.board.copy(favor = 3,
      faceUpSecrets = 3, faceDownSecrets = 0))),
    map = c.map.copy(sites = c.map.sites.updated(siteId,
      c.map.sites(siteId).copy(denizens = Vector(DenizenState(denizen,
        Orientation.FaceUp, Tokens.empty)))))))

  private def replay(pay: PayCost): ReadyGame = ProcedureWalker.applyRecorded(
    Ready(arranged), WalkerStepRecorded("0",
      WalkerStepPayload.DeltaRecorded(DeltaMeaning.OperationApplied("PayCost")),
      Vector(pay),
      Vector.empty)) match {
    case Right(Ready(state)) => state
    case other => fail(s"the recorded payment must replay, got $other")
  }

  private def tokens(state: ReadyGame): Tokens =
    state.game.current.map.sites(siteId).denizens.collectFirst {
      case held: DenizenState if held.id == denizen => held.tokens }.get

  private def board(state: ReadyGame, who: PlayerId) =
    state.game.current.players.find(_.player == who).get.board

  test("an off-turn payment replays settled: favor to the matching bank, a secret facedown") {
    val done = replay(PayCost(payer, Location.OnCard(denizen),
      Cost(favor = 1, secret = 1), intoOccupied = true, matchingBank = Some(suit)))
    assertEquals(tokens(done), Tokens.empty)
    val after = board(done, payer)
    assertEquals((after.favor, after.faceUpSecrets, after.faceDownSecrets),
      (2, 2, 1))
    assertEquals(done.banks.favor.getOrElse(suit, 0),
      arranged.banks.favor.getOrElse(suit, 0) + 1)
  }

  test("the active player's payment replays resting on the card") {
    val done = replay(PayCost(active, Location.OnCard(denizen),
      Cost(favor = 1, secret = 1), matchingBank = Some(suit)))
    assertEquals(tokens(done), Tokens(1, 1))
    assertEquals(board(done, active).faceDownSecrets, 0)
  }

  test("a recorded batch of other operations replays as before") {
    val done = replay(PayCost(active, Location.OnCard(denizen),
      Cost(favorBurnt = 1)))
    assertEquals(board(done, active).favor, 2)
  }
}
```


- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.OfferHostSuite oathdigital.gameplay.RepeatPassSuite oathdigital.gameplay.WalkerReplaySettlementSuite"`
Expected: FAIL to compile, for example `not found: type OfferHost`.

- [ ] **Step 3: Implement**

In `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`, replace:

```scala
package oathdigital.gameplay.powerresolver

import oathdigital.model.{CoreOperation, DecisionOptionRef, OathViolation, Operation, PlayerId, PowerId, PowerResolution, PowerWindow, ProcedureRef, ReadyGame, RuleSourceRef}

/** Everything a contribution may read at the node it hooks. Carries no
  * mutable state and no catalog -- a power looks up whatever else it needs
```

with:

```scala
package oathdigital.gameplay.powerresolver

import oathdigital.model.{Answered, CampaignPlanOffer, CoreOperation, DecisionOptionRef, OathViolation, OfferedPlan, Operation, PlayerId, PowerId, PowerResolution, PowerWindow, ProcedureRef, ReadyGame, RuleSourceRef}

/** Everything a contribution may read at the node it hooks. Carries no
  * mutable state and no catalog -- a power looks up whatever else it needs
```

In `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`, replace:

```scala
      * modifier is being selected for. `None` for the command that starts a
      * procedure, which has not recorded one yet.
      */
    procedure: Option[ProcedureRef] = None
)

/** The three ways a power may speak at a hooked node (spec decision 9). A
```

with:

```scala
      * modifier is being selected for. `None` for the command that starts a
      * procedure, which has not recorded one yet.
      */
    procedure: Option[ProcedureRef] = None,
    /** The decisions answered so far in the running action, oldest first. A
      * contribution that must know what the player chose earlier (which battle
      * plans were used) reads them here. Empty when the window is gathered
      * outside a walk.
      */
    answered: Vector[Answered] = Vector.empty
)

/** The three ways a power may speak at a hooked node (spec decision 9). A
```

In `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`, replace:

```scala
final case class OptionRestriction(
    fn: (PowerCtx, DecisionOptionRef) => Option[OathViolation]
) extends Contribution

/** One object per power (spec decision 8). No engine code lives in a power --
  * only the windows it hooks and the contributions it offers there.
```

with:

```scala
final case class OptionRestriction(
    fn: (PowerCtx, DecisionOptionRef) => Option[OathViolation]
) extends Contribution

/** Offers one option to the node its window hooks, when that node is an
  * [[OfferHost]] (a Campaign's battle-plan window). `plan` says whether the
  * offer stands now and what it is; it reads the context like any contribution
  * and is asked again at every fold, so it must be a pure function of state.
  * Whether the user can pay is not its business: the host dry-runs the plan.
  */
final case class Offer(
    plan: PowerCtx => Option[CampaignPlanOffer]
) extends Contribution

/** A windowed node that turns what the powers offer into the children it walks.
  * The walker gathers the window's [[Offer]]s, asks each for its plan, and hands
  * the plans to `expand` in the deterministic power order.
  *
  * `expand` runs at every fold of the node, so it must be a pure function of its
  * arguments. A walk that resumes inside the node (`Pass.resuming`) is handed
  * the same offers again against a later state, and must give the node the same
  * shape as when it parked, even if nothing is left to offer.
  */
trait OfferHost extends Operation {
  def expand(offers: Vector[OfferedPlan], pass: OfferHost.Pass): Vector[Operation]
}

object OfferHost {
  /** What the walker tells a host about this fold.
    *
    * `applies` dry-runs an operation through the same windows the walk uses.
    * It reports the operations the walk would record, or why it could not run.
    * An operation that parks on a decision is accepted when everything before
    * the decision ran, and reports what ran.
    */
  final case class Pass(state: ReadyGame, answered: Vector[Answered],
      resuming: Boolean,
      applies: Operation => Either[OathViolation, Vector[CoreOperation]])
}

/** One object per power (spec decision 8). No engine code lives in a power --
  * only the windows it hooks and the contributions it offers there.
```

In `src/main/scala/oathdigital/gameplay/powerresolver/ContributionCollector.scala`, replace:

```scala
import oathdigital.model.PowerId
import oathdigital.model.PowerWindow

/** The result of one gather at a hooked node: transforms, restrictions and
  * option restrictions declared by the surviving powers, tagged with the power that declared
  * each, plus the deterministic order those powers were resolved in.
  */
final case class GatheredContributions(
    transforms: Vector[(PowerId, Transform)],
    restrictions: Vector[(PowerId, Restriction)],
    order: Vector[PowerId],
    optionRestrictions: Vector[(PowerId, OptionRestriction)] = Vector.empty
)

/** Turns "which powers hook this window" into "which transforms, restrictions
```

with:

```scala
import oathdigital.model.PowerId
import oathdigital.model.PowerWindow

/** The result of one gather at a hooked node: transforms, restrictions, option
  * restrictions and offers declared by the surviving powers, tagged with the
  * power that declared each, plus the deterministic order those powers were
  * resolved in.
  */
final case class GatheredContributions(
    transforms: Vector[(PowerId, Transform)],
    restrictions: Vector[(PowerId, Restriction)],
    order: Vector[PowerId],
    optionRestrictions: Vector[(PowerId, OptionRestriction)] = Vector.empty,
    offers: Vector[(PowerId, Offer)] = Vector.empty
)

/** Turns "which powers hook this window" into "which transforms, restrictions
```

In `src/main/scala/oathdigital/gameplay/powerresolver/ContributionCollector.scala`, replace:

```scala
    val transforms = Vector.newBuilder[(PowerId, Transform)]
    val restrictions = Vector.newBuilder[(PowerId, Restriction)]
    val optionRestrictions = Vector.newBuilder[(PowerId, OptionRestriction)]

    ordered.foreach { power =>
      power.contributions(window).foreach {
        case transform: Transform => transforms += power.id -> transform
        case restriction: Restriction => restrictions += power.id -> restriction
        case option: OptionRestriction => optionRestrictions += power.id -> option
      }
    }

```

with:

```scala
    val transforms = Vector.newBuilder[(PowerId, Transform)]
    val restrictions = Vector.newBuilder[(PowerId, Restriction)]
    val optionRestrictions = Vector.newBuilder[(PowerId, OptionRestriction)]
    val offers = Vector.newBuilder[(PowerId, Offer)]

    ordered.foreach { power =>
      power.contributions(window).foreach {
        case transform: Transform => transforms += power.id -> transform
        case restriction: Restriction => restrictions += power.id -> restriction
        case option: OptionRestriction => optionRestrictions += power.id -> option
        case offer: Offer => offers += power.id -> offer
      }
    }

```

In `src/main/scala/oathdigital/gameplay/powerresolver/ContributionCollector.scala`, replace:

```scala
      transforms = transforms.result(),
      restrictions = restrictions.result(),
      order = ordered.map(_.id),
      optionRestrictions = optionRestrictions.result()
    )
  }
}
```

with:

```scala
      transforms = transforms.result(),
      restrictions = restrictions.result(),
      order = ordered.map(_.id),
      optionRestrictions = optionRestrictions.result(),
      offers = offers.result()
    )
  }
}
```

In `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`, replace:

```scala
      cursor: Option[Vector[String]], resume: Resume,
      hooks: WalkerHooks): Either[OathViolation, Step] = {
    val (folded, order) = WalkerPowerGather.applyWindow(window, operation, ctx.state,
      ctx.activePlayer, ctx.powers, path, children, ctx.procedure)
    walkChildren(folded, ctx, path, cursor, resume, hooks.withOrder(order))
  }

```

with:

```scala
      cursor: Option[Vector[String]], resume: Resume,
      hooks: WalkerHooks): Either[OathViolation, Step] = {
    val (folded, order) = WalkerPowerGather.applyWindow(window, operation, ctx.state,
      ctx.activePlayer, ctx.powers, path, children, ctx.procedure, ctx.answered,
      cursor.isDefined)
    walkChildren(folded, ctx, path, cursor, resume, hooks.withOrder(order))
  }

```

In `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`, replace:

```scala
      walkFolded(repeat.window, repeat, repeat.children, current, path, at, resume,
        hooks).flatMap {
          case park: Park => Right(park)
          case Done(next) => passes(next)
        }

```

with:

```scala
      walkFolded(repeat.window, repeat, repeat.children, current, path, at, resume,
        hooks).flatMap {
          case park: Park => Right(park)
          // A pass that recorded nothing and asked nothing cannot change what
          // the guard reads, so it would only repeat itself for ever.
          case Done(next) if next.events.size == current.events.size &&
              next.answered.size == current.answered.size => Right(Done(next))
          case Done(next) => passes(next)
        }

```

In `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`, replace:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{ContributingPower, ContributionCollector, OptionRestriction, PowerCtx}
import oathdigital.model.{Answered, Branch, Decide, DecisionOptionRef, DecisionQuery, OathViolation, Operation, PendingTree, PlayerId, PowerId, PowerWindow, PrimitiveOperation, ReadyGame}

/** Task 3's power-gather/fold mechanics for [[ProcedureWalker]], split into
  * their own file to keep `ProcedureWalker.scala` under the project's
```

with:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{ContributingPower, ContributionCollector, OfferHost, OptionRestriction, PowerCtx}
import oathdigital.model.{Answered, Branch, Decide, DecisionOptionRef, DecisionQuery, OathViolation, OfferedPlan, Operation, PendingTree, PlayerId, PowerId, PowerWindow, PrimitiveOperation, ReadyGame}

/** Task 3's power-gather/fold mechanics for [[ProcedureWalker]], split into
  * their own file to keep `ProcedureWalker.scala` under the project's
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`, replace:

```scala
    * the gather's contribution order, to be recorded verbatim on whichever
    * `WalkerStepRecorded` this node's execution produces (Task 3 wiring
    * rules 1-3).
    */
  def applyWindow(window: Option[PowerWindow], operation: Operation,
      state: ReadyGame,
      activePlayer: PlayerId, powers: WalkerPowers, path: Vector[String],
      ops: Vector[Operation], procedure: Option[oathdigital.model.ProcedureRef])
      : (Vector[Operation], Vector[PowerId]) =
    window match {
      case None => (ops, Vector.empty)
```

with:

```scala
    * the gather's contribution order, to be recorded verbatim on whichever
    * `WalkerStepRecorded` this node's execution produces (Task 3 wiring
    * rules 1-3).
    *
    * `answered` are the decisions answered so far, which a contribution reads
    * from its `PowerCtx`. `resuming` is true when the walk is resuming inside
    * this node: an [[OfferHost]] then keeps its shape whatever it is offered.
    * When the node is an [[OfferHost]], the offers the window gathered are
    * turned into its children after the transforms have run.
    */
  def applyWindow(window: Option[PowerWindow], operation: Operation,
      state: ReadyGame,
      activePlayer: PlayerId, powers: WalkerPowers, path: Vector[String],
      ops: Vector[Operation], procedure: Option[oathdigital.model.ProcedureRef],
      answered: Vector[Answered] = Vector.empty, resuming: Boolean = false)
      : (Vector[Operation], Vector[PowerId]) =
    window match {
      case None => (ops, Vector.empty)
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`, replace:

```scala
          powers.powers.map(power => power.id -> power).toMap
        def ctxFor(power: ContributingPower): PowerCtx =
          PowerCtx(state, activePlayer, power.source, w, path, operation,
            procedure)
        val gathered = ContributionCollector.gather(w, powers.powers, ctxFor)
        val folded = gathered.transforms.foldLeft(ops) {
          case (acc, (powerId, transform)) =>
```

with:

```scala
          powers.powers.map(power => power.id -> power).toMap
        def ctxFor(power: ContributingPower): PowerCtx =
          PowerCtx(state, activePlayer, power.source, w, path, operation,
            procedure, answered)
        val gathered = ContributionCollector.gather(w, powers.powers, ctxFor)
        val folded = gathered.transforms.foldLeft(ops) {
          case (acc, (powerId, transform)) =>
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`, replace:

```scala
        val restricted = operation match {
          case _: Decide => restrictOptions(folded, gathered.optionRestrictions,
            ctxFor, byId)
          case _ => folded
        }
        (restricted, gathered.order)
```

with:

```scala
        val restricted = operation match {
          case _: Decide => restrictOptions(folded, gathered.optionRestrictions,
            ctxFor, byId)
          case host: OfferHost =>
            val offered = gathered.offers.flatMap { case (powerId, offer) =>
              offer.plan(ctxFor(byId(powerId))).map(OfferedPlan(powerId, _))
            }
            folded ++ host.expand(offered, OfferHost.Pass(state, answered,
              resuming, WalkerSimulation.applies(_, state, powers)))
          case _ => folded
        }
        (restricted, gathered.order)
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`, replace:

```scala
        case branch: Branch => descend(applyWindow(branch.window, branch,
          state, activePlayer, powers, path, branch.select(state,
            PendingTree(at = path, answered = answered)),
          state.game.current.walkerProcedure)._1)
        case _ => descend(applyWindow(node.window, node, state,
          activePlayer, powers, path, node.children,
          state.game.current.walkerProcedure)._1)
      }
      own ++ nested
    }
```

with:

```scala
        case branch: Branch => descend(applyWindow(branch.window, branch,
          state, activePlayer, powers, path, branch.select(state,
            PendingTree(at = path, answered = answered)),
          state.game.current.walkerProcedure, answered)._1)
        case _ => descend(applyWindow(node.window, node, state,
          activePlayer, powers, path, node.children,
          state.game.current.walkerProcedure, answered)._1)
      }
      own ++ nested
    }
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`, replace:

```scala
      case branch: Branch =>
        val selected = branch.select(state, pending.copy(at = path))
        val (folded, _) = applyWindow(branch.window, branch, state,
          activePlayer, powers, path, selected, state.game.current.walkerProcedure)
        (folded, gathered)
      case leaf: PrimitiveOperation =>
        leaf.window match {
          case Some(w) if !gathered.contains(w) =>
            val (folded, _) = applyWindow(Some(w), leaf, state,
              activePlayer, powers, path, Vector(leaf),
              state.game.current.walkerProcedure)
            (folded, gathered + w)
          case _ => (leaf.children, gathered)
        }
      case composite =>
        val (folded, _) = applyWindow(composite.window, composite, state,
          activePlayer, powers, path, composite.children,
          state.game.current.walkerProcedure)
        (folded, gathered)
    }
  }
```

with:

```scala
      case branch: Branch =>
        val selected = branch.select(state, pending.copy(at = path))
        val (folded, _) = applyWindow(branch.window, branch, state,
          activePlayer, powers, path, selected, state.game.current.walkerProcedure,
          pending.answered, resuming = true)
        (folded, gathered)
      case leaf: PrimitiveOperation =>
        leaf.window match {
          case Some(w) if !gathered.contains(w) =>
            val (folded, _) = applyWindow(Some(w), leaf, state,
              activePlayer, powers, path, Vector(leaf),
              state.game.current.walkerProcedure, pending.answered,
              resuming = true)
            (folded, gathered + w)
          case _ => (leaf.children, gathered)
        }
      case composite =>
        val (folded, _) = applyWindow(composite.window, composite, state,
          activePlayer, powers, path, composite.children,
          state.game.current.walkerProcedure, pending.answered, resuming = true)
        (folded, gathered)
    }
  }
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala`, replace:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.OperationExecutor
import oathdigital.model.{Answered, OathState, OathViolation, PendingTree, ReadyGame, WalkerEvent}

/** Replay half of the walker: applies durable walker facts to state without
  * ever deriving or walking an action tree (batch-1 Task 5).
```

with:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.{OperationExecutor, PayCostSettlement}
import oathdigital.model.{Answered, CoreOperation, OathState, OathViolation, PendingTree, ReadyGame, WalkerEvent}

/** Replay half of the walker: applies durable walker facts to state without
  * ever deriving or walking an action tree (batch-1 Task 5).
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala`, replace:

```scala
      .map(OathState.Ready)
    case _ => Left(OathViolation.GameNotStarted)
  }

  private def applyRecordedReady(ready: ReadyGame,
      event: WalkerEvent): Either[OathViolation, ReadyGame] = {
```

with:

```scala
      .map(OathState.Ready)
    case _ => Left(OathViolation.GameNotStarted)
  }

  /** Applies the recorded operations the way the pipeline ran them: a `PayCost`
    * whose payer is not the active player settles at once, so the recorded
    * (requested) operation expands to the same moves it did when it ran.
    */
  private def executeRecorded(ready: ReadyGame, ops: Vector[CoreOperation])
      : Either[OathViolation, ReadyGame] =
    ops.foldLeft[Either[OathViolation, ReadyGame]](Right(ready)) {
      (result, operation) => result.flatMap(state =>
        PayCostSettlement.prepare(state, operation).flatMap(prepared =>
          new OperationExecutor().execute(state, prepared).left.map(_.toViolation)))
    }

  private def applyRecordedReady(ready: ReadyGame,
      event: WalkerEvent): Either[OathViolation, ReadyGame] = {
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala`, replace:

```scala
          _ <- validateStep(step)
          _ <- Either.cond(ops.nonEmpty, (), OathViolation.InvalidEventOrder(
            "recorded delta step must contain operations"))
          updated <- new OperationExecutor().executeAll(ready, ops)
            .left.map(_.toViolation)
        } yield updated

      case WalkerParked(procedure, at, answered, modifiers, startArgs) => for {
```

with:

```scala
          _ <- validateStep(step)
          _ <- Either.cond(ops.nonEmpty, (), OathViolation.InvalidEventOrder(
            "recorded delta step must contain operations"))
          updated <- executeRecorded(ready, ops)
        } yield updated

      case WalkerParked(procedure, at, answered, modifiers, startArgs) => for {
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala`, replace:

```scala
              "only a tree that runs to the end has an outcome to simulate"))
        }
    }

  /** Whether a freshly built tree could start now: the same restriction check
    * and first walk a start performs, with nothing persisted. A tree that
```

with:

```scala
              "only a tree that runs to the end has an outcome to simulate"))
        }
    }

  /** Whether `tree` could run now, as an [[oathdigital.gameplay.powerresolver.OfferHost]]
    * asks it of a plan, and the operations it would record: the same windows and
    * powers fold it and the same pipeline validates it. A tree that parks on a
    * decision is accepted when everything before the decision ran, because a
    * decision is not a cost. The state is not changed.
    */
  def applies(tree: Operation, state: ReadyGame,
      powers: WalkerPowers): Either[OathViolation, Vector[CoreOperation]] =
    guarded(ProcedureWalker.advance(state, tree, None, powers,
      WalkerDice.placeholder).map {
        case WalkerOutcome.Finished(_, events) => recordedOperations(events)
        case WalkerOutcome.Parked(_, events) => recordedOperations(events)
      })

  /** Whether a freshly built tree could start now: the same restriction check
    * and first walk a start performs, with nothing persisted. A tree that
```

In `src/main/scala/oathdigital/model/CampaignTypes.scala`, replace:

```scala
    effects: Vector[CampaignPlanEffect]
)

/** The public, durable record of one Campaign's battle: written by
  * `RecordCampaignResult` when the outcome is known, projected to every viewer,
  * and replaced by the next Campaign. Everything in it is public: dice are
```

with:

```scala
    effects: Vector[CampaignPlanEffect]
)

/** One battle plan a power offers now: where it comes from, what it costs and
  * what it does. `label` is the words of the option when the source has no card
  * to name (the title). An offer says only that the plan is usable, never
  * whether its user can pay: the engine dry-runs the plan to learn that.
  */
final case class CampaignPlanOffer(source: CampaignPlanSource, label: String,
    costs: Vector[CampaignPlanCost], effects: Vector[CampaignPlanEffect])

/** An offer with the power that made it. */
final case class OfferedPlan(power: PowerId, offer: CampaignPlanOffer)

/** The public, durable record of one Campaign's battle: written by
  * `RecordCampaignResult` when the outcome is known, projected to every viewer,
  * and replaced by the next Campaign. Everything in it is public: dice are
```


- [ ] **Step 4: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.OfferHostSuite oathdigital.gameplay.RepeatPassSuite oathdigital.gameplay.WalkerReplaySettlementSuite oathdigital.gameplay.ProcedureWalkerSuite oathdigital.gameplay.PayCostSettlementSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS.

- [ ] **Step 5: Run the whole suite and the architecture check**

Run: `./sbtw test` and `python3 scripts/check-architecture.py`
Expected: PASS (1395 tests), and `architecture check passed`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: let powers offer options to a window host

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 3: The plan window, the kit and the four ported plans

**Files:**
- Modify and create: the files listed for Task 3 in "File Structure".
- Test: `src/test/scala/oathdigital/gameplay/CampaignPlanWindowSuite.scala`, `CampaignPlansSuite.scala` (rewritten), `application/PricedOptionProjectionSuite.scala`; `CampaignFixture` and `CampaignProcedureSuite` (modified).

**Interfaces:**
- Consumes: Task 2's `Offer`, `OfferHost`, `PowerCtx.answered`, `WalkerSimulation.applies`, `CampaignPlanOffer`, `OfferedPlan`; E4's `PayCost(player, placedAt, cost, intoOccupied, matchingBank)` and `Costs.onCard`.
- Produces: `CampaignPlanCost.{Favor, Secret, FavorBurnt, SecretBurnt, SacrificeWarband}`; `CampaignPlanEffect.{AddAttackDice, RemoveAttackDice, AddDefenseDice, Run(operations)}`; `CampaignPlanSource.SiteEdifice(siteId, id)`; `PowerWindow.CampaignPlanApplication`; `CampaignIds.planDecision(side)` and `CampaignIds.planSacrifice`; `CampaignPlans.{userOf, cardOf, refOf, optionOf, sorted}`; `class CampaignPlanChoice(catalog, actor, side) extends OfferHost`; `class CampaignPlanApplication(catalog, setup, side, offered)` with `source`, `user`, `side`, `setup`; `DecisionOption.Priced(option, price)` and `OptionPrice`; `trait BattlePlan extends ContributingPower { cardRef; sides; plan(context); later }`; `PlanContext(ready, setup, side)` with `user`, `rules`, `denizen`, `relic`, `edifice`, `pawnAt`, `targets`, `targetsIn`; `PlanUse(side, actor, result, ready)` with `user` and `won`; `CampaignFixture.rulesWith`, `withAdviserFor`, `actorRules`, `replacePlayer`.

This task moves Campaign's plans onto the seam.

**The plan window.** `CampaignPlanChoice` is the `OfferHost` at `CampaignAttackerBattlePlans` and `CampaignDefenderBattlePlans`. A player's window is `Repeat(!finished, CampaignPlanChoice)`. A pass sorts the offers (title, advisers, relics, cards at sites), drops the sources already chosen, dry-runs the application of each remaining one to learn whether the user can pay and what it costs, and asks the decision with the plans that could be paid for, priced, then Finish. A pass with nothing to offer does nothing, which ends the loop through Task 2's rule. A bandit defender has no decision: the node expands to the application of every cost-free plan it is offered and can pay for (a power's added cost can make a plan unpayable for bandits, and a dry run says so). When the walk is resuming inside a pass, the pass keeps its two slots (the decision, and the application of the last pick) whatever is offered, because the pick it is resuming is no longer listed.

**The application.** `CampaignPlanApplication` has a fixed set of `Branch` slots: reveal a facedown adviser, pay, sacrifice a warband, then one child per effect. Payment is a batch, `Costs.onCard(...)` with `intoOccupied`, so the pipeline settles it when the payer is not the active player (favor to the card's suit bank, a secret turned facedown). A warband sacrifice asks which force pays when several could, and refuses before any effect when none can. A bandit defender pays nothing, and records that it applied the plan as a pool marker (`CampaignPlans.appliedMarker`), because a bandit's plan is never an answer and a later window must be able to tell it was used.

**The kit.** `BattlePlan` turns a plan's three statements into contributions: an `Offer` at each side's window, and a `Transform` at a later window for `later`. `PlanContext` answers where a card stands for a ruler (an adviser of the user, a faceup relic of the user, a card at a site the user rules), and `PlanUse` tells a later window which side used the plan and whether its user won. A plan was used when a player chose it (an answer) or a bandit defender applied it (the marker). The four plans that were registry handlers become `TitleDefensePlan`, `Outriders`, `BrassArmy` and `Watchdog`, registered through `BattlePlans`. Outriders no longer marks an effect that the engine reads by handler id: it writes the attack's roll outcome again, without the skull cap, in the attack result window, once chosen.

- [ ] **Step 1: Write the tests**

`CampaignPlanWindowSuite` drives the window with plans of its own: costs, off-turn payment, options rebuilt after each pick, a sacrifice that resumes inside the plan, a power that adds to a cost, a later hook, a bandit defender. `CampaignPlansSuite` is rewritten to ask the four ported plans what they offer. The existing suites change where they must: the fixture defaults to the production powers, the options are priced, a payment is recorded as a `PayCost`, and the facedown site card test becomes a ruler-only test.

In `src/test/scala/oathdigital/gameplay/CampaignFixture.scala`, replace:

```scala
    current => current.copy(players = current.players.map(p =>
      if (p.player == b.other) p.copy(pawnSite = Some(b.origin)) else p))))

  def rules(dice: WalkerDice = WalkerDice.unavailable,
      powers: Boolean = false): OathRules = new OathRules(catalog,
    walkerPowerCatalog =
      if (powers) WalkerPowerCatalog.default(catalog) else WalkerPowers.empty,
    walkerDice = dice)

  /** Dice for tests that only walk through the battle: hollow swords and
    * blanks, of whatever count the pool holds.
```

with:

```scala
    current => current.copy(players = current.players.map(p =>
      if (p.player == b.other) p.copy(pawnSite = Some(b.origin)) else p))))

  /** Battle plans are powers, so a Campaign runs with the walker power catalog
    * unless a suite asks for none.
    */
  def rules(dice: WalkerDice = WalkerDice.unavailable,
      powers: Boolean = true): OathRules = new OathRules(catalog,
    walkerPowerCatalog =
      if (powers) WalkerPowerCatalog.default(catalog) else WalkerPowers.empty,
    walkerDice = dice)

  /** Rules with exactly these walker powers, for a suite that tests the plan
    * window with plans of its own.
    */
  def rulesWith(powers: Vector[oathdigital.gameplay.powerresolver.ContributingPower],
      dice: WalkerDice = anyDice): OathRules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowers(powers), walkerDice = dice)

  /** Dice for tests that only walk through the battle: hollow swords and
    * blanks, of whatever count the pool holds.
```

In `src/test/scala/oathdigital/gameplay/CampaignFixture.scala`, replace:

```scala
            case _ => true },
          relics = site.relics.filterNot(_.id.value == card)) })))

  private def replacePlayer(b: Board, id: PlayerId)(f: PlayerState => PlayerState)
      : Board = b.copy(ready = b.ready.updateCurrent(current => current.copy(
    players = current.players.map(p => if (p.player == id) f(p) else p))))

  def withAdviser(b: Board, card: String, orientation: Orientation): Board =
    replacePlayer(b.copy(ready = scrub(b.ready, card)), b.actor)(p => p.copy(advisers = p.advisers :+
      DenizenState(DenizenId(card), orientation, Tokens.empty)))

  def withRelic(b: Board, relic: String): Board =
    replacePlayer(b.copy(ready = scrub(b.ready, relic)), b.actor)(p =>
```

with:

```scala
            case _ => true },
          relics = site.relics.filterNot(_.id.value == card)) })))

  def replacePlayer(b: Board, id: PlayerId)(f: PlayerState => PlayerState)
      : Board = b.copy(ready = b.ready.updateCurrent(current => current.copy(
    players = current.players.map(p => if (p.player == id) f(p) else p))))

  def withAdviserFor(b: Board, player: PlayerId, card: String,
      orientation: Orientation, tokens: Tokens = Tokens.empty): Board =
    replacePlayer(b.copy(ready = scrub(b.ready, card)), player)(p => p.copy(
      advisers = p.advisers :+ DenizenState(DenizenId(card), orientation, tokens)))

  def withAdviser(b: Board, card: String, orientation: Orientation): Board =
    withAdviserFor(b, b.actor, card, orientation)

  def withRelic(b: Board, relic: String): Board =
    replacePlayer(b.copy(ready = scrub(b.ready, relic)), b.actor)(p =>
```

In `src/test/scala/oathdigital/gameplay/CampaignFixture.scala`, replace:

```scala
      title = current.title.copy(holder = Some(b.other),
        side = TitleSide.Oathkeeper))))
  }

  def withSiteCard(b: Board, site: SiteId, card: String): Board =
    b.copy(ready = scrub(b.ready, card).updateCurrent(current => current.copy(map =
```

with:

```scala
      title = current.title.copy(holder = Some(b.other),
        side = TitleSide.Oathkeeper))))
  }

  /** The actor rules `site`, holding it with two warbands of their own. */
  def actorRules(b: Board, site: SiteId): Board = b.copy(ready =
    b.ready.updateCurrent(current => current.copy(map = current.map.copy(
      sites = current.map.sites.updated(site, current.map.sites(site).copy(
        forces = SiteForces.Occupied(ForceKind.Exile(
          b.player(b.actor).lineage), 2)))))))

  def withSiteCard(b: Board, site: SiteId, card: String): Board =
    b.copy(ready = scrub(b.ready, card).updateCurrent(current => current.copy(map =
```

Create `src/test/scala/oathdigital/gameplay/CampaignPlansSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignPlans, CampaignSetup}
import oathdigital.gameplay.powers.campaign.{BattlePlans, BrassArmy, Outriders, PlanContext, TitleDefensePlan, Watchdog}
import oathdigital.gameplay.powers.campaign.BattlePlan
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** What the ported plans offer, asked directly: where a plan's card must stand,
  * what it costs and what it does. Whether the user can pay, and how a plan is
  * chosen and applied, are the plan window's business (`CampaignProcedureSuite`
  * and `CampaignPlanWindowSuite`).
  */
class CampaignPlansSuite extends munit.FunSuite {
  private def setupOf(b: Board, force: Int = 2): CampaignSetup = CampaignSetup(
    b.actor, CampaignKind.Conquest, b.origin,
    CampaignSetup.conquestDefender(b.ready, b.actor).get, Vector(b.origin),
    Vector.empty, force)

  private def context(b: Board, side: CampaignPlanSide): PlanContext =
    PlanContext(b.ready, setupOf(b), side)

  private def defending(b: Board): PlanContext = PlanContext(b.ready,
    setupOf(b).copy(defender = CampaignDefender.Player(b.other)),
    CampaignPlanSide.Defender)

  private val outriders = Outriders.forCatalog(catalog).get
  private val brass = BrassArmy.forCatalog(catalog).get
  private val watchdog = Watchdog.forCatalog(catalog).get
  private val outridersCard = cardWith("denizen.outriders")
  private val brassCard = relicWith("relic.brass-army.campaign")
  private val watchdogCard = cardWith("denizen.watchdog")

  test("Outriders is an attacker's plan from any adviser of the attacker, in either orientation") {
    val up = withAdviser(board(), outridersCard, Orientation.FaceUp)
    val down = withAdviser(board(), outridersCard, Orientation.FaceDown)
    Vector(up, down).foreach { b =>
      val offer = outriders.plan(context(b, CampaignPlanSide.Attacker)).get
      assertEquals(offer.source, CampaignPlanSource.Adviser(b.actor,
        DenizenId(outridersCard)))
      assertEquals((offer.costs, offer.effects), (Vector.empty, Vector.empty))
      assertEquals(CampaignPlans.refOf(offer.source),
        DecisionOptionRef.Denizen(DenizenId(outridersCard)))
    }
    assertEquals(outriders.sides, Set[CampaignPlanSide](CampaignPlanSide.Attacker))
  }

  test("a plan is used only by the ruler of its source: a card at the origin does not count unless the attacker rules it") {
    val b = withSiteCard(board(), board().origin, outridersCard)
    assertEquals(outriders.plan(context(b, CampaignPlanSide.Attacker)), None)
    // The attacker rules a second site, and the card stands there.
    val two = board(extras = 1)
    val ruled = two.extras.head
    val staged = withSiteCard(actorRules(two, ruled), ruled, outridersCard)
    assertEquals(outriders.plan(context(staged, CampaignPlanSide.Attacker))
      .map(_.source), Some(CampaignPlanSource.SiteCard(ruled,
        DenizenId(outridersCard))))
  }

  test("a card held by another player is not offered") {
    val b = board()
    val elsewhere = b.copy(ready = withAdviser(b, outridersCard,
      Orientation.FaceUp).ready.updateCurrent(current => current.copy(
      players = current.players.map(p => if (p.player == b.actor)
        p.copy(advisers = Vector.empty) else p.copy(advisers = Vector(
        DenizenState(DenizenId(outridersCard), Orientation.FaceUp,
          Tokens.empty)))))))
    assertEquals(outriders.plan(context(elsewhere, CampaignPlanSide.Attacker)),
      None)
  }

  test("Brass Army costs a secret placed onto a faceup relic, occupied or not, and adds four attack dice") {
    val offer = brass.plan(context(withSecrets(withRelic(board(), brassCard), 1),
      CampaignPlanSide.Attacker)).get
    assertEquals(offer.costs, Vector[CampaignPlanCost](CampaignPlanCost.Secret(1)))
    assertEquals(offer.effects, Vector[CampaignPlanEffect](
      CampaignPlanEffect.AddAttackDice(4)))
    // Whether the attacker can pay is not the offer's business.
    assert(brass.plan(context(withSecrets(withRelic(board(), brassCard), 0),
      CampaignPlanSide.Attacker)).nonEmpty)
    val occupied = withRelic(board(), brassCard)
    val holding = occupied.copy(ready = occupied.ready.updateCurrent(current =>
      current.copy(players = current.players.map(p => p.copy(relics =
        p.relics.map(r => r.copy(tokens = Tokens(1, 1))))))))
    assert(brass.plan(context(holding, CampaignPlanSide.Attacker)).nonEmpty)
    assertEquals(brass.plan(context(board(), CampaignPlanSide.Attacker)), None)
  }

  test("Brass Army needs the relic faceup") {
    val b = withRelic(board(), brassCard)
    val down = b.copy(ready = b.ready.updateCurrent(current => current.copy(
      players = current.players.map(p => p.copy(relics = p.relics.map(r =>
        r.copy(orientation = Orientation.FaceDown)))))))
    assertEquals(brass.plan(context(down, CampaignPlanSide.Attacker)), None)
  }

  test("Watchdog adds a defense die when a target is in the Cradle, for a ruler of its card") {
    val base = board()
    assert(base.ready.game.current.map.regionOf(base.origin).contains(Region.Cradle),
      "the fixture's origin must be in the Cradle")
    val bandits = withSiteCard(base, base.origin, watchdogCard)
    val offer = watchdog.plan(context(bandits, CampaignPlanSide.Defender)).get
    assertEquals(offer.source, CampaignPlanSource.SiteCard(base.origin,
      DenizenId(watchdogCard)))
    assertEquals(offer.effects, Vector[CampaignPlanEffect](
      CampaignPlanEffect.AddDefenseDice(1)))
    // A Raid targets no site.
    val raid = PlanContext(bandits.ready, setupOf(bandits).copy(
      kind = CampaignKind.Raid, targetSites = Vector.empty),
      CampaignPlanSide.Defender)
    assertEquals(watchdog.plan(raid), None)
  }

  test("a player defender uses Watchdog from an adviser, and a card at a site the attacker's enemy does not rule is not theirs") {
    val b = againstPlayer(board())
    val held = b.copy(ready = withAdviser(b, watchdogCard, Orientation.FaceUp)
      .ready.updateCurrent(current => current.copy(players = current.players.map(
        p => if (p.player == b.other) p.copy(advisers = Vector(DenizenState(
          DenizenId(watchdogCard), Orientation.FaceUp, Tokens.empty)))
        else p.copy(advisers = Vector.empty)))))
    assertEquals(watchdog.plan(defending(held)).map(_.source),
      Some(CampaignPlanSource.Adviser(b.other, DenizenId(watchdogCard))))
    val siteCard = withSiteCard(againstPlayer(board()), b.origin, watchdogCard)
    assertEquals(watchdog.plan(defending(siteCard)).map(_.source),
      Some(CampaignPlanSource.SiteCard(b.origin, DenizenId(watchdogCard))))
    // The card stands at a site the attacker's pawn is at but the defender does not rule.
    val unruled = withSiteCard(board(), b.origin, watchdogCard)
    assertEquals(watchdog.plan(defending(unruled)), None)
  }

  test("the title adds one defense die to an Oathkeeper and two to a Usurper, and only for a defender who holds it") {
    val b = againstPlayer(board())
    def titled(side: TitleSide): Option[CampaignPlanOffer] = {
      val state = b.copy(ready = b.ready.updateCurrent(c => c.copy(title =
        c.title.copy(side = side))))
      TitleDefensePlan.plan.plan(defending(state))
    }
    assertEquals(titled(TitleSide.Oathkeeper).get.effects,
      Vector[CampaignPlanEffect](CampaignPlanEffect.AddDefenseDice(1)))
    assertEquals(titled(TitleSide.Usurper).get.effects,
      Vector[CampaignPlanEffect](CampaignPlanEffect.AddDefenseDice(2)))
    assertEquals(titled(TitleSide.Oathkeeper).get.source,
      CampaignPlanSource.Title(b.other))
    assertEquals(CampaignPlans.refOf(titled(TitleSide.Oathkeeper).get.source),
      DecisionOptionRef.Button("title"))
    assertEquals(titled(TitleSide.Oathkeeper).get.label,
      "Oathkeeper title: add 1 defense die")
    assertEquals(TitleDefensePlan.plan.plan(context(b, CampaignPlanSide.Attacker)),
      None)
    assertEquals(TitleDefensePlan.plan.plan(context(board(), CampaignPlanSide.Defender)),
      None)
  }

  test("plans are listed with the title first, then advisers, relics, and cards at sites, then by key") {
    val adviser = OfferedPlan(PowerId("test.a"), CampaignPlanOffer(
      CampaignPlanSource.Adviser(PlayerId("p"), DenizenId("d")), "", Vector.empty,
      Vector.empty))
    val relic = OfferedPlan(PowerId("test.b"), CampaignPlanOffer(
      CampaignPlanSource.Relic(PlayerId("p"), RelicId("r")), "", Vector.empty,
      Vector.empty))
    val site = OfferedPlan(PowerId("test.c"), CampaignPlanOffer(
      CampaignPlanSource.SiteCard(SiteId("s"), DenizenId("d")), "", Vector.empty,
      Vector.empty))
    val edifice = OfferedPlan(PowerId("test.d"), CampaignPlanOffer(
      CampaignPlanSource.SiteEdifice(SiteId("s"), EdificeId("e")), "",
      Vector.empty, Vector.empty))
    val title = OfferedPlan(PowerId("test.e"), CampaignPlanOffer(
      CampaignPlanSource.Title(PlayerId("p")), "t", Vector.empty, Vector.empty))
    assertEquals(CampaignPlans.sorted(Vector(site, edifice, relic, adviser, title))
      .map(_.power.value), Vector("test.e", "test.a", "test.b", "test.c", "test.d"))
  }

  test("a card names its option and the title names it by button") {
    val card = OfferedPlan(PowerId("test.a"), CampaignPlanOffer(
      CampaignPlanSource.SiteEdifice(SiteId("s"), EdificeId("e")), "", Vector.empty,
      Vector.empty))
    assertEquals(CampaignPlans.optionOf(card),
      DecisionOption.Edifice(DecisionOptionRef.Edifice(EdificeId("e"))))
    val title = OfferedPlan(PowerId("test.t"), CampaignPlanOffer(
      CampaignPlanSource.Title(PlayerId("p")), "the words", Vector.empty,
      Vector.empty))
    assertEquals(CampaignPlans.optionOf(title), DecisionOption.Button(
      DecisionOptionRef.Button("title"), "the words"))
  }

  test("the four ported plans register through one object, and are automatic whatever the catalog flag") {
    val plans = BattlePlans.forCatalog(catalog)
    assertEquals(plans.size, 4)
    plans.foreach(plan => assertEquals(plan.resolution, PowerResolution.Automatic))
    assert(plans.forall(_.isInstanceOf[BattlePlan]))
    assertEquals(plans.map(_.id.value).toSet, Set("title.oathkeeper-defense",
      "denizen.outriders", "relic.brass-army.campaign", "denizen.watchdog"))
  }
}
```

In `src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala`, replace:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture.{Board, againstPlayer, board, cardWith, relicWith, rules, withAdviser, withEnemyAtOrigin, withRelic, withSecrets, withSiteCard}
import oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignIds, CampaignProcedure}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, RollPayload,
  WalkerPowers, WalkerStepRecorded}
```

with:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture.{Board, actorRules, againstPlayer, board, cardWith, relicWith, rules, withAdviser, withEnemyAtOrigin, withRelic, withSecrets, withSiteCard}
import oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignIds, CampaignProcedure}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, RollPayload,
  WalkerPowers, WalkerStepRecorded}
```

In `src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala`, replace:

```scala
    val tree = CampaignProcedure.rebuild(catalog, ready(transition.state),
      b.actor, Vector.empty).toOption.get
    ProcedureWalker.openDecisions(ready(transition.state), tree, pending,
      WalkerPowers.empty).head
  }

  private def supply(state: OathState, id: PlayerId): Int =
```

with:

```scala
    val tree = CampaignProcedure.rebuild(catalog, ready(transition.state),
      b.actor, Vector.empty).toOption.get
    ProcedureWalker.openDecisions(ready(transition.state), tree, pending,
      WalkerPowerCatalog.default(catalog)).head
  }

  private def supply(state: OathState, id: PlayerId): Int =
```

In `src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala`, replace:

```scala
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
    // Nothing else can be chosen, so the window finishes by itself and the
    // walk goes on to the sacrifice.
    assertEquals(picked.continue, OathContinue.AwaitingCampaignDecision(b.actor,
```

with:

```scala
    assertEquals(plans.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.attackerPlan)))
    assertEquals(parkedDecision(b, plans).query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Priced(
        DecisionOption.Relic(DecisionOptionRef.Relic(RelicId(brass))),
        OptionPrice(secrets = 1)),
      DecisionOption.Button(CampaignIds.finish, "Finish battle plans")),
      Some("Choose a battle plan, or finish")))
    val picked = answer(plans.state, b.actor, CampaignIds.attackerPlan,
      planPick(DecisionOptionRef.Relic(RelicId(brass)))).toOption.get
    assert(ops(picked.events).contains(ModifyDicePool(CampaignIds.attackPool, 4)))
    assert(ops(picked.events).contains(PayCost(b.actor,
      Location.OnCard(RelicId(brass)), Cost(secret = 1), intoOccupied = true)))
    // Nothing else can be chosen, so the window finishes by itself and the
    // walk goes on to the sacrifice.
    assertEquals(picked.continue, OathContinue.AwaitingCampaignDecision(b.actor,
```

In `src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala`, replace:

```scala
    assertEquals(first.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.attackerPlan)))
    assertEquals(parkedDecision(b, first).query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Relic(DecisionOptionRef.Relic(RelicId(brass))),
      DecisionOption.Button(CampaignIds.finish, "Finish battle plans")),
      Some("Choose a battle plan, or finish")))
    val done = answer(first.state, b.actor, CampaignIds.attackerPlan, finish).toOption.get
```

with:

```scala
    assertEquals(first.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.attackerPlan)))
    assertEquals(parkedDecision(b, first).query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Priced(
        DecisionOption.Relic(DecisionOptionRef.Relic(RelicId(brass))),
        OptionPrice(secrets = 1)),
      DecisionOption.Button(CampaignIds.finish, "Finish battle plans")),
      Some("Choose a battle plan, or finish")))
    val done = answer(first.state, b.actor, CampaignIds.attackerPlan, finish).toOption.get
```

In `src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala`, replace:

```scala
        DistributeAmount(DecisionOptionRef.Site(b.extras.head), 2)))).isLeft)
  }

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

  test("a victory with nothing placed refills the bandits at the action boundary") {
```

with:

```scala
        DistributeAmount(DecisionOptionRef.Site(b.extras.head), 2)))).isLeft)
  }

  test("a site card is a plan only for the site's ruler, and is never revealed") {
    val outriders = cardWith("denizen.outriders")
    val two = board(extras = 1)
    val ruled = two.extras.head
    val b = withSiteCard(actorRules(two, ruled), ruled, outriders)
    val plans = atPlans(b)
    assertEquals(plans.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.attackerPlan)))
    val done = answer(plans.state, b.actor, CampaignIds.attackerPlan,
      planPick(DecisionOptionRef.Denizen(DenizenId(outriders)))).toOption.get
    assert(!ops(done.events).exists(_.isInstanceOf[Reveal]))
    // The same card at the origin, which the bandits rule, is not the attacker's.
    val atOrigin = withSiteCard(board(), board().origin, outriders)
    assertEquals(atPlans(atOrigin).continue, OathContinue
      .AwaitingCampaignDecision(atOrigin.actor, DecisionId(CampaignIds.sacrifice)))
  }

  test("a victory with nothing placed refills the bandits at the action boundary") {
```

Create `src/test/scala/oathdigital/application/PricedOptionProjectionSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model.OathState.Ready
import oathdigital.model._

/** An option that states its price projects as the option it wraps, with the
  * price worded as details.
  */
class PricedOptionProjectionSuite extends munit.FunSuite {
  private val Ready(base) = execute(new FirstGameSetupRules(catalog))._1: @unchecked
  private val actor = base.game.current.turn.activePlayer
  private val site = base.game.current.map.inPlay.head

  private def projected(options: Vector[DecisionOption]) = {
    val ready: ReadyGame = base.updateCurrent(_.copy(
      turn = base.game.current.turn.copy(phase = Phase.Act),
      walkerProcedure = Some(ActionRef.Recover),
      walkerPending = Some(PendingTree(Vector("0"), Vector.empty))))
    val tree = Sequence(Decide("test.priced", actor,
      DecisionQuery.ChooseOne(options)))
    new WalkerDecisionProjector(catalog, new GamePresentationProjector(catalog),
      WalkerPowers.empty, (_, _, _, _, _) => Right(tree))
      .project(ScopedProjectionContext(ready, Some(actor)))
      .flatMap(_.query).getOrElse(fail("the decision must project")).options
  }

  test("a priced option is the wrapped option with its price as details") {
    val plain = DecisionOption.Site(DecisionOptionRef.Site(site))
    val options = projected(Vector(plain, DecisionOption.Priced(plain,
      OptionPrice(favor = 1, secrets = 2, favorBurnt = 1, secretsBurnt = 1,
        warbands = 1))))
    assertEquals(options.map(o => (o.kind, o.id)), Vector.fill(2)(
      ("site", site.value)))
    assertEquals(options.head.details, Vector.empty[String])
    assertEquals(options(1).label, options.head.label)
    assertEquals(options(1).details, Vector("Cost: 1 favor", "Cost: 2 secrets",
      "Cost: 1 favor burnt", "Cost: 1 secret burnt", "Cost: sacrifice 1 warband"))
  }

  test("a price words only what it costs, and pluralises") {
    assertEquals(PriceDetails.of(OptionPrice()), Vector.empty[String])
    assertEquals(PriceDetails.of(OptionPrice(secretsBurnt = 2, warbands = 3)),
      Vector("Cost: 2 secrets burnt", "Cost: sacrifice 3 warbands"))
  }

  test("a price never affects which answer names the option") {
    val plain = DecisionOption.Button(DecisionOptionRef.Button("x"), "X")
    assertEquals(DecisionOption.Priced(plain, OptionPrice(favor = 1)).ref,
      plain.ref)
    assert(OptionPrice().isFree)
    assert(!OptionPrice(warbands = 1).isFree)
  }
}
```

Create `src/test/scala/oathdigital/gameplay/CampaignPlanWindowSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlanApplication, CampaignProcedure}
import oathdigital.gameplay.operations.Costs
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, Transform}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.powers.campaign.{BattlePlan, PlanContext, PlanUse}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerDice, WalkerPowers, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer._
import oathdigital.model.OathState.Ready

/** The plan window itself, with plans of the suite's own: what a plan may cost,
  * who pays and when, which plans are offered after each pick, and how a plan
  * that asks a question of its own is resumed. The ported plans are covered by
  * `CampaignPlansSuite` and `CampaignProcedureSuite`.
  */
class CampaignPlanWindowSuite extends munit.FunSuite {
  private val orderCard = cardWith("denizen.outriders")
  private val hearthCard = cardWith("denizen.watchdog")

  /** A plan that costs and does what the test says, from a denizen of its user. */
  private final case class Plan(name: String, card: String,
      planSides: Set[CampaignPlanSide],
      costs: Vector[CampaignPlanCost] = Vector.empty,
      effects: Vector[CampaignPlanEffect] = Vector.empty,
      afterwards: Map[PowerWindow, PlanUse => Vector[Operation]] = Map.empty)
      extends BattlePlan {
    def id: PowerId = PowerId(s"test.plan.$name")
    def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))
    def sides: Set[CampaignPlanSide] = planSides
    def plan(context: PlanContext): Option[CampaignPlanOffer] =
      context.denizen(DenizenId(card)).map(CampaignPlanOffer(_, name, costs,
        effects))
    override def later: Map[PowerWindow, PlanUse => Vector[Operation]] =
      afterwards
  }

  /** Every attack die a sword and every defense die blank, whatever the pool. */
  private val swords: WalkerDice = (kind, count) => Right(kind match {
    case DiceKind.Attack => Vector.fill(count)(AttackDieFace.OneSword: DieFace)
    case DiceKind.Defense => Vector.fill(count)(DefenseDieFace.Blank: DieFace)
  })

  private val attacker = Set[CampaignPlanSide](CampaignPlanSide.Attacker)
  private val defender = Set[CampaignPlanSide](CampaignPlanSide.Defender)

  private def ready(state: OathState): ReadyGame = state match {
    case Ready(value) => value
    case other => fail(s"expected a ready game, got $other")
  }

  private def ops(events: Vector[OathEvent]): Vector[CoreOperation] =
    events.collect { case step: WalkerStepRecorded => step.ops }.flatten

  private def player(state: OathState, id: PlayerId): PlayerState =
    ready(state).game.current.players.find(_.player == id).get

  private def adviserTokens(state: OathState, id: PlayerId, card: String)
      : Tokens = player(state, id).advisers.collectFirst {
    case held: DenizenState if held.id.value == card => held.tokens
  }.get

  private def with_(b: Board, id: PlayerId)(f: PlayerBoardState => PlayerBoardState)
      : Board = replacePlayer(b, id)(p => p.copy(board = f(p.board)))

  private def committed(g: OathRules, b: Board, force: Int = 2)
      : (OathTransition, OathTransition) = {
    val started = g.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor)
      .getOrElse(fail("Campaign must start"))
    started -> g.resolveWalker(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(force)).fold(e => fail(s"the force must be accepted: $e"), identity)
  }

  private def pick(g: OathRules, from: OathTransition, who: PlayerId, id: String,
      ref: DecisionOptionRef): OathTransition = g.resolveWalker(from.state, who,
    id, ChooseOneAnswer(ref)).fold(e => fail(s"the plan must be accepted: $e"), identity)

  private def parked(plans: Vector[ContributingPower], b: Board,
      from: OathTransition): Decide = {
    val current = ready(from.state)
    val tree = CampaignProcedure.rebuild(catalog, current, b.actor,
      Vector.empty).toOption.get
    ProcedureWalker.openDecisions(current, tree,
      current.game.current.walkerPending.get, WalkerPowers(plans)).head
  }

  private def optionsOf(decide: Decide): Vector[DecisionOption] =
    decide.query match {
      case DecisionQuery.ChooseOne(options, _) => options
      case other => fail(s"expected a choose-one, got $other")
    }

  private def awaits(who: PlayerId, id: String) =
    OathContinue.AwaitingCampaignDecision(who, DecisionId(id))

  private def denizen(card: String): DecisionOptionRef =
    DecisionOptionRef.Denizen(DenizenId(card))
  private val finishOption =
    DecisionOption.Button(CampaignIds.finish, "Finish battle plans")

  // ---- costs --------------------------------------------------------------

  test("a favor cost is placed onto a card that already holds resources, and the option states the price") {
    val base = board()
    val b = with_(withAdviserFor(base, base.actor, orderCard,
      Orientation.FaceUp, Tokens(1, 0)), base.actor)(_.copy(favor = 2))
    val plan = Plan("pay", orderCard, attacker, Vector(CampaignPlanCost.Favor(1)),
      Vector(CampaignPlanEffect.AddAttackDice(3)))
    val g = rulesWith(Vector(plan))
    val (started, forced) = committed(g, b)
    assertEquals(forced.continue, awaits(b.actor, CampaignIds.attackerPlan))
    assertEquals(optionsOf(parked(Vector(plan), b, forced)), Vector(
      DecisionOption.Priced(DecisionOption.Denizen(
        DecisionOptionRef.Denizen(DenizenId(orderCard))), OptionPrice(favor = 1)),
      finishOption))
    val picked = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      denizen(orderCard))
    assertEquals(adviserTokens(picked.state, b.actor, orderCard), Tokens(2, 0))
    assertEquals(player(picked.state, b.actor).board.favor, 1)
    assert(ops(picked.events).contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assert(ops(picked.events).contains(PayCost(b.actor,
      Location.OnCard(DenizenId(orderCard)), Cost(favor = 1), intoOccupied = true,
      matchingBank = catalog.suitOf(DenizenId(orderCard)))))
    // The recorded steps replay to the same state, and survive the journal wire.
    val events = started.events ++ forced.events ++ picked.events
    assertEquals(PaidActionHarness.replayed(g, b.ready, events),
      ready(picked.state))
    assert(PaidActionHarness.wireRoundTrips(events))
  }

  test("burnt costs leave play to the shared bank, and the option states them apart") {
    val base = board()
    val b = with_(withAdviserFor(base, base.actor, orderCard,
      Orientation.FaceUp), base.actor)(_.copy(favor = 1, faceUpSecrets = 1))
    val plan = Plan("burn", orderCard, attacker, Vector(
      CampaignPlanCost.FavorBurnt(1), CampaignPlanCost.SecretBurnt(1)),
      Vector(CampaignPlanEffect.AddAttackDice(1)))
    val g = rulesWith(Vector(plan))
    val (_, forced) = committed(g, b)
    assertEquals(optionsOf(parked(Vector(plan), b, forced)).head,
      DecisionOption.Priced(DecisionOption.Denizen(
        DecisionOptionRef.Denizen(DenizenId(orderCard))),
        OptionPrice(favorBurnt = 1, secretsBurnt = 1)))
    val picked = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      denizen(orderCard))
    val after = player(picked.state, b.actor)
    assertEquals((after.board.favor, after.board.faceUpSecrets), (0, 0))
    assertEquals(adviserTokens(picked.state, b.actor, orderCard), Tokens.empty)
    assert(ops(picked.events).contains(PayCost(b.actor,
      Location.OnCard(DenizenId(orderCard)), Cost(favorBurnt = 1, secretBurnt = 1),
      intoOccupied = true, matchingBank = catalog.suitOf(DenizenId(orderCard)))))
  }

  test("a plan the user cannot pay is not offered, and a window with nothing to offer is skipped") {
    val base = board()
    val b = withAdviserFor(base, base.actor, orderCard, Orientation.FaceUp)
    val plan = Plan("poor", orderCard, attacker, Vector(CampaignPlanCost.Favor(1)),
      Vector(CampaignPlanEffect.AddAttackDice(3)))
    val (_, forced) = committed(rulesWith(Vector(plan)), with_(b, b.actor)(
      _.copy(favor = 0)))
    assertEquals(forced.continue, awaits(b.actor, CampaignIds.sacrifice))
  }

  test("the options are rebuilt after each pick: a second plan the payment made unaffordable is gone") {
    val base = board()
    val b = with_(withAdviserFor(withAdviserFor(base, base.actor, orderCard,
      Orientation.FaceUp), base.actor, hearthCard, Orientation.FaceUp),
      base.actor)(_.copy(faceUpSecrets = 1))
    val first = Plan("first", orderCard, attacker,
      Vector(CampaignPlanCost.Secret(1)), Vector(CampaignPlanEffect.AddAttackDice(1)))
    val second = Plan("second", hearthCard, attacker,
      Vector(CampaignPlanCost.Secret(1)), Vector(CampaignPlanEffect.AddAttackDice(1)))
    val plans = Vector[ContributingPower](first, second)
    val g = rulesWith(plans)
    val (_, forced) = committed(g, b)
    assertEquals(optionsOf(parked(plans, b, forced)).size, 3)
    val picked = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      denizen(orderCard))
    assertEquals(picked.continue, awaits(b.actor, CampaignIds.sacrifice))
  }

  test("a defender's plan is paid at once: favor goes to the card's suit bank and a secret turns facedown") {
    val base = againstPlayer(board())
    val b = with_(withAdviserFor(base, base.other, orderCard, Orientation.FaceUp),
      base.other)(_.copy(favor = 2, faceUpSecrets = 1, faceDownSecrets = 0))
    val plan = Plan("guard", orderCard, defender, Vector(
      CampaignPlanCost.Favor(1), CampaignPlanCost.Secret(1)),
      Vector(CampaignPlanEffect.AddDefenseDice(1)))
    val g = rulesWith(Vector(plan))
    val (started, forced) = committed(g, b)
    assertEquals(forced.continue, awaits(b.other, CampaignIds.defenderPlan))
    assertEquals(optionsOf(parked(Vector(plan), b, forced)).head,
      DecisionOption.Priced(DecisionOption.Denizen(
        DecisionOptionRef.Denizen(DenizenId(orderCard))),
        OptionPrice(favor = 1, secrets = 1)))
    val suit = catalog.suitOf(DenizenId(orderCard)).get
    val bank = ready(forced.state).banks.favor.getOrElse(suit, 0)
    val picked = pick(g, forced, b.other, CampaignIds.defenderPlan,
      denizen(orderCard))
    assertEquals(adviserTokens(picked.state, b.other, orderCard), Tokens.empty)
    assertEquals(ready(picked.state).banks.favor.getOrElse(suit, 0), bank + 1)
    val after = player(picked.state, b.other).board
    assertEquals((after.favor, after.faceUpSecrets, after.faceDownSecrets),
      (1, 0, 1))
    assert(ops(picked.events).contains(ModifyDicePool(CampaignIds.defensePool, 1)))
    // The recorded payment is the requested one, and replay settles it again.
    assertEquals(PaidActionHarness.replayed(g, b.ready,
      started.events ++ forced.events ++ picked.events), ready(picked.state))
  }

  test("a defender plan with a cost the defender cannot pay is not offered") {
    val base = againstPlayer(board())
    val b = withAdviserFor(base, base.other, orderCard, Orientation.FaceUp)
    val plan = Plan("guard", orderCard, defender,
      Vector(CampaignPlanCost.SecretBurnt(2)),
      Vector(CampaignPlanEffect.AddDefenseDice(1)))
    val (_, forced) = committed(rulesWith(Vector(plan)), b)
    assertEquals(forced.continue, awaits(b.actor, CampaignIds.sacrifice))
  }

  // ---- a warband sacrifice ------------------------------------------------

  private def defenderHolding(b: Board, warbands: Int): Board = {
    val against = withEnemyAtOrigin(againstPlayer(b))
    with_(withAdviserFor(against, against.other, orderCard, Orientation.FaceUp),
      against.other)(_.copy(warbands = warbands))
  }

  private val sacrificing = Plan("sacrifice", orderCard, defender,
    Vector(CampaignPlanCost.SacrificeWarband),
    Vector(CampaignPlanEffect.AddDefenseDice(1)))

  test("a Raid defender sacrifices a warband from the board") {
    val b = defenderHolding(board(warbands = 4), 3)
    val g = rulesWith(Vector(sacrificing))
    val started = g.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor).toOption.get
    val kind = g.resolveWalker(started.state, b.actor, CampaignIds.kind,
      ChooseOneAnswer(DecisionOptionRef.Button("raid"))).toOption.get
    val forced = g.resolveWalker(kind.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(2)).toOption.get
    assertEquals(forced.continue, awaits(b.other, CampaignIds.defenderPlan))
    assertEquals(optionsOf(parked(Vector(sacrificing), b, forced)).head,
      DecisionOption.Priced(DecisionOption.Denizen(
        DecisionOptionRef.Denizen(DenizenId(orderCard))), OptionPrice(warbands = 1)))
    val picked = pick(g, forced, b.other, CampaignIds.defenderPlan,
      denizen(orderCard))
    assertEquals(player(picked.state, b.other).board.warbands, 2)
    assert(ops(picked.events).exists(_.isInstanceOf[Sacrifice]))
  }

  test("a Raid defender with no warband on the board cannot pay the sacrifice, so the plan is not offered") {
    val b = defenderHolding(board(warbands = 4), 0)
    val g = rulesWith(Vector(sacrificing))
    val started = g.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor).toOption.get
    val kind = g.resolveWalker(started.state, b.actor, CampaignIds.kind,
      ChooseOneAnswer(DecisionOptionRef.Button("raid"))).toOption.get
    val forced = g.resolveWalker(kind.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(2)).toOption.get
    assertEquals(forced.continue, awaits(b.actor, CampaignIds.sacrifice))
  }

  test("a Conquest defender sacrifices from the one target site it rules") {
    val b = withAdviserFor(againstPlayer(board()), againstPlayer(board()).other,
      orderCard, Orientation.FaceUp)
    val g = rulesWith(Vector(sacrificing))
    val (_, forced) = committed(g, b)
    assertEquals(forced.continue, awaits(b.other, CampaignIds.defenderPlan))
    val picked = pick(g, forced, b.other, CampaignIds.defenderPlan,
      denizen(orderCard))
    assertEquals(ready(picked.state).game.current.map.sites(b.origin).forces,
      SiteForces.Occupied(ForceKind.Exile(b.player(b.other).lineage), 1))
  }

  test("with several target sites the defender chooses which pays, and the walk resumes inside the plan") {
    val two = againstPlayer(board(extras = 1))
    val extra = two.extras.head
    val lineage = two.player(two.other).lineage
    val staged = withAdviserFor(two.copy(ready = two.ready.updateCurrent(
      current => current.copy(map = current.map.copy(sites =
        current.map.sites.updated(extra, current.map.sites(extra).copy(forces =
          SiteForces.Occupied(ForceKind.Exile(lineage), 2))))))), two.other,
      orderCard, Orientation.FaceUp)
    val g = rulesWith(Vector(sacrificing))
    val started = g.startWalker(Ready(staged.ready), ActionRef.Campaign,
      staged.actor).toOption.get
    val targeted = g.resolveWalker(started.state, staged.actor, CampaignIds.targets,
      ChooseManyAnswer(Vector(DecisionOptionRef.Site(extra)))).toOption.get
    val forced = g.resolveWalker(targeted.state, staged.actor, CampaignIds.force,
      ChooseAmountAnswer(2)).toOption.get
    val picked = pick(g, forced, staged.other, CampaignIds.defenderPlan,
      denizen(orderCard))
    // The plan asks which force pays before it kills anything.
    assertEquals(picked.continue, awaits(staged.other, CampaignIds.planSacrifice))
    assertEquals(ready(picked.state).game.current.map.sites(extra).forces,
      SiteForces.Occupied(ForceKind.Exile(lineage), 2))
    val paid = g.resolveWalker(picked.state, staged.other,
      CampaignIds.planSacrifice, ChooseOneAnswer(DecisionOptionRef.Site(extra)))
      .getOrElse(fail("the site must be accepted"))
    assertEquals(ready(paid.state).game.current.map.sites(extra).forces,
      SiteForces.Occupied(ForceKind.Exile(lineage), 1))
    assertEquals(ready(paid.state).game.current.map.sites(staged.origin).forces,
      SiteForces.Occupied(ForceKind.Exile(lineage), 2))
    assert(ops(paid.events).contains(ModifyDicePool(CampaignIds.defensePool, 1)))
    // Nothing else is offered, so the window ends and the attacker sacrifices.
    assertEquals(paid.continue, awaits(staged.actor, CampaignIds.sacrifice))
  }

  // ---- effects ------------------------------------------------------------

  test("a defender plan that removes attack dice takes what the pool holds, and none from an empty pool") {
    val base = againstPlayer(board())
    val b = withAdviserFor(base, base.other, orderCard, Orientation.FaceUp)
    val plan = Plan("remove", orderCard, defender, effects =
      Vector(CampaignPlanEffect.RemoveAttackDice(3)))
    val g = rulesWith(Vector(plan))
    val (_, forced) = committed(g, b, force = 2)
    val picked = pick(g, forced, b.other, CampaignIds.defenderPlan,
      denizen(orderCard))
    assert(ops(picked.events).contains(ModifyDicePool(CampaignIds.attackPool, -2)))
    val (_, none) = committed(g, b, force = 0)
    val nothing = pick(g, none, b.other, CampaignIds.defenderPlan,
      denizen(orderCard))
    assert(!ops(nothing.events).exists(_.isInstanceOf[ModifyDicePool]))
  }

  test("a plan's own operations run after its payment") {
    val b = withAdviserFor(board(supply = 4), board().actor, orderCard,
      Orientation.FaceUp)
    val plan = Plan("run", orderCard, attacker, effects =
      Vector(CampaignPlanEffect.Run(Vector(GainSupply(b.actor, 1)))))
    val g = rulesWith(Vector(plan))
    val (_, forced) = committed(g, b)
    val picked = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      denizen(orderCard))
    assertEquals(player(picked.state, b.actor).board.supply.supply, 3)
  }

  // ---- other powers change what a plan costs ------------------------------

  /** Every attacker plan costs one more secret, placed on its card. */
  private final case class Surcharge() extends ContributingPower {
    def id: PowerId = PowerId("test.surcharge")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.CampaignPlanApplication -> Vector[Contribution](
        Transform((ctx, children) => ctx.operation match {
          case application: CampaignPlanApplication
              if application.side == CampaignPlanSide.Attacker =>
            application.source match {
              case CampaignPlanSource.Adviser(user, card) => children :+
                BuildOps((_, _) => Right(Vector[CoreOperation](Costs.onCard(user,
                  card, Cost(secret = 1), catalog, intoOccupied = true))))
              case _ => children
            }
          case _ => children
        })))
  }

  test("a power that adds to a plan's cost changes what is offered, and the option's price") {
    val base = board()
    val b = withAdviserFor(base, base.actor, orderCard, Orientation.FaceUp)
    val plan = Plan("free", orderCard, attacker, effects =
      Vector(CampaignPlanEffect.AddAttackDice(1)))
    val plain = Vector[ContributingPower](plan)
    val taxed = Vector[ContributingPower](plan, Surcharge())
    // Without the surcharge the free plan is offered, free.
    val (_, freely) = committed(rulesWith(plain), with_(b, b.actor)(
      _.copy(faceUpSecrets = 0)))
    assertEquals(optionsOf(parked(plain, b, freely)).head, DecisionOption.Denizen(
      DecisionOptionRef.Denizen(DenizenId(orderCard))))
    // With it, and no secret to pay, it is not offered at all.
    val (_, broke) = committed(rulesWith(taxed), with_(b, b.actor)(
      _.copy(faceUpSecrets = 0)))
    assertEquals(broke.continue, awaits(b.actor, CampaignIds.sacrifice))
    // With a secret it is offered, priced, and choosing it pays the secret.
    val rich = with_(b, b.actor)(_.copy(faceUpSecrets = 1))
    val g = rulesWith(taxed)
    val (_, forced) = committed(g, rich)
    assertEquals(optionsOf(parked(taxed, rich, forced)).head,
      DecisionOption.Priced(DecisionOption.Denizen(
        DecisionOptionRef.Denizen(DenizenId(orderCard))), OptionPrice(secrets = 1)))
    val picked = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      denizen(orderCard))
    assertEquals(adviserTokens(picked.state, b.actor, orderCard), Tokens(0, 1))
    assertEquals(player(picked.state, b.actor).board.faceUpSecrets, 0)
  }

  // ---- later windows ------------------------------------------------------

  test("what a used plan adds at a later window runs only when the plan was chosen") {
    val base = board(supply = 4)
    val b = withAdviserFor(base, base.actor, orderCard, Orientation.FaceUp)
    val plan = Plan("later", orderCard, attacker, effects =
      Vector(CampaignPlanEffect.AddAttackDice(1)), afterwards = Map(
        PowerWindow.CampaignActionEligibility -> (use =>
          Vector[Operation](GainSupply(use.actor, 1)))))
    val plans = Vector[ContributingPower](plan)
    def finished(chooses: Boolean): Int = {
      val g = rulesWith(plans, swords)
      val (_, forced) = committed(g, b, force = 4)
      val next =
        if (chooses) pick(g, forced, b.actor, CampaignIds.attackerPlan,
          denizen(orderCard))
        else pick(g, forced, b.actor, CampaignIds.attackerPlan,
          CampaignIds.finish)
      val sacrificed = g.resolveWalker(next.state, b.actor, CampaignIds.sacrifice,
        ChooseAmountAnswer(0)).getOrElse(fail("no sacrifice"))
      val placed = g.resolveWalker(sacrificed.state, b.actor,
        CampaignIds.placement, ChooseAmountAnswer(0)).getOrElse(fail("no placement"))
      player(placed.state, b.actor).board.supply.supply
    }
    assertEquals(finished(chooses = true), 3)
    assertEquals(finished(chooses = false), 2)
  }

  // ---- a bandit defender --------------------------------------------------

  test("a bandit defender applies every cost-free plan of a site it rules, and none that costs") {
    val two = board(extras = 1)
    val staged = withSiteCard(withSiteCard(two, two.origin, orderCard),
      two.extras.head, hearthCard)
    val free = Plan("free", orderCard, defender, effects =
      Vector(CampaignPlanEffect.AddDefenseDice(1)))
    val costly = Plan("costly", hearthCard, defender,
      Vector(CampaignPlanCost.Favor(1)),
      Vector(CampaignPlanEffect.AddDefenseDice(5)))
    val g = rulesWith(Vector(free, costly))
    val funded = with_(staged, staged.actor)(_.copy(favor = 3))
    val started = g.startWalker(Ready(funded.ready), ActionRef.Campaign,
      funded.actor).toOption.get
    val targeted = g.resolveWalker(started.state, funded.actor,
      CampaignIds.targets, ChooseManyAnswer(Vector.empty)).toOption.get
    val forced = g.resolveWalker(targeted.state, funded.actor, CampaignIds.force,
      ChooseAmountAnswer(2)).toOption.get
    val pools = ops(forced.events).collect {
      case pool @ ModifyDicePool(CampaignIds.defensePool, _, _) => pool }
    assertEquals(pools.filter(_.delta == 1).size, 1)
    assert(!pools.exists(_.delta == 5))
    assert(!ops(forced.events).exists(_.isInstanceOf[PayCost]))
    assertEquals(forced.continue, awaits(staged.actor, CampaignIds.sacrifice))
  }

  test("Finish ends the window and a chosen source is not offered again") {
    val base = board()
    val b = withAdviserFor(base, base.actor, orderCard, Orientation.FaceUp)
    val plan = Plan("once", orderCard, attacker, effects =
      Vector(CampaignPlanEffect.AddAttackDice(1)))
    val g = rulesWith(Vector(plan))
    val (_, forced) = committed(g, b)
    val finished = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      CampaignIds.finish)
    assertEquals(finished.continue, awaits(b.actor, CampaignIds.sacrifice))
    assert(!ops(finished.events).exists(_.isInstanceOf[ModifyDicePool]))
    val taken = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      denizen(orderCard))
    assert(g.resolveWalker(taken.state, b.actor, CampaignIds.attackerPlan,
      ChooseOneAnswer(denizen(orderCard))).isLeft)
  }
}
```


- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignPlanWindowSuite oathdigital.gameplay.CampaignPlansSuite oathdigital.gameplay.CampaignProcedureSuite oathdigital.application.PricedOptionProjectionSuite"`
Expected: FAIL to compile, for example `not found: type BattlePlan`.

- [ ] **Step 3: Implement**

In `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`, replace:

```scala
      Some(DecisionOptionProjection(ref.kind, ref.wireId, label, card,
        details ++ extra))
    option match {
      case DecisionOption.Button(_, label) => row(label)
      case DecisionOption.Player(player) =>
        if (ready.game.current.players.exists(_.player == player.id))
```

with:

```scala
      Some(DecisionOptionProjection(ref.kind, ref.wireId, label, card,
        details ++ extra))
    option match {
      case DecisionOption.Priced(inner, price) => optionProjection(ready,
        viewer, index, inner, details ++ PriceDetails.of(price))
      case DecisionOption.Button(_, label) => row(label)
      case DecisionOption.Player(player) =>
        if (ready.game.current.players.exists(_.player == player.id))
```

In `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignBattle.scala`, replace:

```scala
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

```

with:

```scala
    }
  }

  /** The faces the attack pool rolled, before any cap. */
  def attackFacesOf(ready: ReadyGame): Vector[AttackDieFace] =
    ready.game.current.rollOutcomes.get(CampaignIds.attackPool).toVector
      .flatMap(_.faces.collect { case face: AttackDieFace => face })

  /** Writes the capped attack over the rolled one. A pool that never rolled has
    * no outcome, and a missing outcome already reads as zero. A plan that
    * ignores the skulls writes its own result afterwards, in the attack result
    * window.
    */
  def attackResultOps(ready: ReadyGame, setup: CampaignSetup)
      : Vector[CoreOperation] =
    ready.game.current.rollOutcomes.get(CampaignIds.attackPool).toVector.map { _ =>
      val (score, skulls) = attackResult(attackFacesOf(ready), setup.force,
        ignoreSkulls = false)
      ModifyRollOutcome(CampaignIds.attackPool, Some(skulls), Some(score))
    }

```

Create `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanSteps.scala`:

```scala
package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** The two battle-plan windows: the attacker's, then the defender's. A player's
  * window is a `Repeat` of [[CampaignPlanChoice]] passes, each asking one plan
  * and applying it, so each plan is paid and applied the moment it is chosen and
  * the next options see the result. The `Repeat` ends on Finish, or when a pass
  * has nothing left to offer.
  */
private[campaign] object CampaignPlanSteps {
  def attacker(catalog: ExecutableCatalog, actor: PlayerId): Operation =
    loop(catalog, actor, CampaignPlanSide.Attacker)

  /** A player defender chooses plans; a bandit defender applies its own. */
  def defender(catalog: ExecutableCatalog, actor: PlayerId): Operation =
    Branch((ready, pending) => CampaignSetup.setup(ready, actor, pending)
      .map(_.defender) match {
        case Some(CampaignDefender.Bandits) => Vector(
          new CampaignPlanChoice(catalog, actor, CampaignPlanSide.Defender))
        case Some(_) => Vector(loop(catalog, actor, CampaignPlanSide.Defender))
        case None => Vector.empty
      })

  private def loop(catalog: ExecutableCatalog, actor: PlayerId,
      side: CampaignPlanSide): Operation = Repeat(
    (_, pending) => !CampaignAnswers.finished(pending,
      CampaignIds.planDecision(side)),
    new CampaignPlanChoice(catalog, actor, side))
}
```

Create `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlans.scala`:

```scala
package oathdigital.gameplay.actions.campaign

import oathdigital.model._

/** The battle-plan vocabulary the plan window and the powers that offer plans
  * share: who uses a plan, how a plan's source is named in a decision, and the
  * order the plans are listed in. The plans themselves are `Offer`
  * contributions of powers, folded by [[CampaignPlanChoice]].
  */
object CampaignPlans {
  /** The player who uses a plan on `side`: the attacker, or a player defender.
    * `None` for a bandit defender, which uses plans without choosing them.
    */
  def userOf(setup: CampaignSetup, side: CampaignPlanSide): Option[PlayerId] =
    side match {
      case CampaignPlanSide.Attacker => Some(setup.actor)
      case CampaignPlanSide.Defender => setup.defender match {
        case CampaignDefender.Player(player) => Some(player)
        case CampaignDefender.Bandits => None
      }
    }

  /** The card a plan's source is printed on. The title has none. */
  def cardOf(source: CampaignPlanSource): Option[CardId] = source match {
    case CampaignPlanSource.Adviser(_, id) => Some(id)
    case CampaignPlanSource.SiteCard(_, id) => Some(id)
    case CampaignPlanSource.SiteEdifice(_, id) => Some(id)
    case CampaignPlanSource.Relic(_, id) => Some(id)
    case CampaignPlanSource.Title(_) => None
  }

  /** What a decision option names a source by: the card, or the title's button. */
  def refOf(source: CampaignPlanSource): DecisionOptionRef = source match {
    case CampaignPlanSource.Adviser(_, id) => DecisionOptionRef.Denizen(id)
    case CampaignPlanSource.SiteCard(_, id) => DecisionOptionRef.Denizen(id)
    case CampaignPlanSource.SiteEdifice(_, id) => DecisionOptionRef.Edifice(id)
    case CampaignPlanSource.Relic(_, id) => DecisionOptionRef.Relic(id)
    case CampaignPlanSource.Title(_) => DecisionOptionRef.Button("title")
  }

  /** The pool a bandit defender's application of the plan named by `ref` is
    * recorded in. A plan a player chooses is an answer, which a later window
    * reads; a bandit chooses nothing, so its plan is recorded here instead. The
    * pool is never rolled, and it is cleared with the others when the Campaign
    * ends.
    */
  def appliedMarker(ref: DecisionOptionRef): PoolKey =
    PoolKey(s"campaign.plan-applied.${ref.kind}.${ref.wireId}")

  /** A card is named by the projector; the title has no card, so its button
    * carries the label its offer authored.
    */
  def optionOf(offered: OfferedPlan): DecisionOption = offered.offer.source match {
    case CampaignPlanSource.Title(_) => DecisionOption.Button(
      DecisionOptionRef.Button("title"), offered.offer.label)
    case source => DecisionOption.forRef(refOf(source)).getOrElse(
      throw new IllegalStateException(s"no option for plan source $source"))
  }

  /** The title first, then advisers, relics, and cards at sites. */
  private def order(source: CampaignPlanSource): Int = source match {
    case _: CampaignPlanSource.Title => 0
    case _: CampaignPlanSource.Adviser => 100
    case _: CampaignPlanSource.Relic => 200
    case _: CampaignPlanSource.SiteCard => 300
    case _: CampaignPlanSource.SiteEdifice => 300
  }

  /** The offers in a stable order: by kind of source, then source, then power. */
  def sorted(offers: Vector[OfferedPlan]): Vector[OfferedPlan] =
    offers.sortBy(o => (order(o.offer.source), o.offer.source.stableKey,
      o.power.value))
}
```

In `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala`, replace:

```scala
      Some(PowerWindow.CampaignAttackRoll)),
    Sequence(Vector[Operation](BuildOps((ready, pending) =>
      withSetup(ready, actor, pending)(setup =>
        CampaignBattle.attackResultOps(catalog, ready, setup, pending)))),
      Some(PowerWindow.CampaignAttackResult)),
    sacrificeStep(actor),
    Roll(CampaignIds.defensePool, DiceSpec(DiceKind.Defense), RollMode.Automatic,
```

with:

```scala
      Some(PowerWindow.CampaignAttackRoll)),
    Sequence(Vector[Operation](BuildOps((ready, pending) =>
      withSetup(ready, actor, pending)(setup =>
        CampaignBattle.attackResultOps(ready, setup)))),
      Some(PowerWindow.CampaignAttackResult)),
    sacrificeStep(actor),
    Roll(CampaignIds.defensePool, DiceSpec(DiceKind.Defense), RollMode.Automatic,
```

In `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignSetup.scala`, replace:

```scala
  val sacrifice = "campaign.sacrifice"
  val placement = "campaign.placement"
  val relocation = "campaign.relocation"
  val all: Set[String] = Set(kind, defender, targets, force, attackerPlan,
    defenderPlan, sacrifice, placement, relocation)

  val attackPool: PoolKey = PoolKey("campaign.attack")
  val defensePool: PoolKey = PoolKey("campaign.defense")
```

with:

```scala
  val sacrifice = "campaign.sacrifice"
  val placement = "campaign.placement"
  val relocation = "campaign.relocation"
  /** Which of a defender's forces pays a plan's warband sacrifice. */
  val planSacrifice = "campaign.plan-sacrifice"
  val all: Set[String] = Set(kind, defender, targets, force, attackerPlan,
    defenderPlan, sacrifice, placement, relocation, planSacrifice)

  /** The decision a side's plan window asks. */
  def planDecision(side: CampaignPlanSide): String = side match {
    case CampaignPlanSide.Attacker => attackerPlan
    case CampaignPlanSide.Defender => defenderPlan
  }

  val attackPool: PoolKey = PoolKey("campaign.attack")
  val defensePool: PoolKey = PoolKey("campaign.defense")
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, replace:

```scala
package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.campaign.VowOfPeaceContribution
import oathdigital.gameplay.powers.economy.KnightsErrant
import oathdigital.gameplay.powers.cardplay.CardPlayTriggers
import oathdigital.gameplay.powers.recover.CatacombsContribution
```

with:

```scala
package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.campaign.{BattlePlans, VowOfPeaceContribution}
import oathdigital.gameplay.powers.economy.KnightsErrant
import oathdigital.gameplay.powers.cardplay.CardPlayTriggers
import oathdigital.gameplay.powers.recover.CatacombsContribution
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, replace:

```scala
  * play runs `ActionCardPlayedFaceup` for Conspiracy.
  * Vow of Peace's restriction is inert until Campaign walks
  * `CampaignActionEligibility`.
  */
object WalkerPowerCatalog {
  def default(catalog: ExecutableCatalog): WalkerPowers =
```

with:

```scala
  * play runs `ActionCardPlayedFaceup` for Conspiracy.
  * Vow of Peace's restriction is inert until Campaign walks
  * `CampaignActionEligibility`.
  * The battle plans are inert until a Campaign folds its plan windows: each
  * offers itself there, and the title's defense, which no card prints, is always
  * present.
  */
object WalkerPowerCatalog {
  def default(catalog: ExecutableCatalog): WalkerPowers =
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, replace:

```scala
      ActionModifiers.forCatalog(catalog) ++
      TargetProtections.forCatalog(catalog) ++
      KnightsErrant.forCatalog(catalog).toVector ++
      CardPlayTriggers.forCatalog(catalog) ++
      Dazzle.forCatalog(catalog) :+ TakeWealthLimit :+ ConspiracyWhenPlayed)
}
```

with:

```scala
      ActionModifiers.forCatalog(catalog) ++
      TargetProtections.forCatalog(catalog) ++
      KnightsErrant.forCatalog(catalog).toVector ++
      BattlePlans.forCatalog(catalog) ++
      CardPlayTriggers.forCatalog(catalog) ++
      Dazzle.forCatalog(catalog) :+ TakeWealthLimit :+ ConspiracyWhenPlayed)
}
```

In `src/main/scala/oathdigital/model/CampaignTypes.scala`, replace:

```scala
  case object Defender extends CampaignPlanSide
}

sealed trait CampaignPlanCost extends Product with Serializable
object CampaignPlanCost {
  final case class Favor(count: Int) extends CampaignPlanCost {
```

with:

```scala
  case object Defender extends CampaignPlanSide
}

/** What using a battle plan costs its user. `Favor` and `Secret` are placed onto
  * the plan's source card, which may already hold resources; `FavorBurnt` and
  * `SecretBurnt` leave play to the shared bank. `SacrificeWarband` kills one
  * warband of the user's own force, and only a defender may pay it: the board
  * in a Raid, a warband at a target site the defender rules in a Conquest.
  */
sealed trait CampaignPlanCost extends Product with Serializable
object CampaignPlanCost {
  final case class Favor(count: Int) extends CampaignPlanCost {
```

In `src/main/scala/oathdigital/model/CampaignTypes.scala`, replace:

```scala
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
}

sealed trait CampaignPlanSource extends Product with Serializable {
```

with:

```scala
  final case class Secret(count: Int) extends CampaignPlanCost {
    require(count > 0, "Campaign secret cost must be positive")
  }
  final case class FavorBurnt(count: Int) extends CampaignPlanCost {
    require(count > 0, "Campaign burnt favor cost must be positive")
  }
  final case class SecretBurnt(count: Int) extends CampaignPlanCost {
    require(count > 0, "Campaign burnt secret cost must be positive")
  }
  case object SacrificeWarband extends CampaignPlanCost
}

/** What a chosen battle plan does once it is paid, in order. `RemoveAttackDice`
  * takes what the attack pool holds, up to the count, so a pool of two loses
  * two and an empty pool loses none. `Run` is the escape hatch for a plan whose
  * effect is not a dice change: it runs the operations as they are.
  */
sealed trait CampaignPlanEffect extends Product with Serializable
object CampaignPlanEffect {
  final case class AddAttackDice(count: Int) extends CampaignPlanEffect {
    require(count > 0, "added Campaign attack dice must be positive")
  }
  final case class RemoveAttackDice(count: Int) extends CampaignPlanEffect {
    require(count > 0, "removed Campaign attack dice must be positive")
  }
  final case class AddDefenseDice(count: Int) extends CampaignPlanEffect {
    require(count > 0, "added Campaign defense dice must be positive")
  }
  final case class Run(operations: Vector[Operation]) extends CampaignPlanEffect
}

sealed trait CampaignPlanSource extends Product with Serializable {
```

In `src/main/scala/oathdigital/model/CampaignTypes.scala`, replace:

```scala
      extends CampaignPlanSource {
    def stableKey: String = s"site-card:${siteId.value}:denizen:${id.value}"
  }
  final case class Relic(playerId: PlayerId, id: RelicId)
      extends CampaignPlanSource {
    def stableKey: String = s"relic:${playerId.value}:${id.value}"
```

with:

```scala
      extends CampaignPlanSource {
    def stableKey: String = s"site-card:${siteId.value}:denizen:${id.value}"
  }
  final case class SiteEdifice(siteId: SiteId, id: EdificeId)
      extends CampaignPlanSource {
    def stableKey: String = s"site-edifice:${siteId.value}:edifice:${id.value}"
  }
  final case class Relic(playerId: PlayerId, id: RelicId)
      extends CampaignPlanSource {
    def stableKey: String = s"relic:${playerId.value}:${id.value}"
```

In `src/main/scala/oathdigital/model/CampaignTypes.scala`, replace:

```scala
  }
}

final case class CampaignPlanResolution(
    source: CampaignPlanSource,
    handlerId: String,
    side: CampaignPlanSide,
    costs: Vector[CampaignPlanCost],
    effects: Vector[CampaignPlanEffect]
)

/** One battle plan a power offers now: where it comes from, what it costs and
  * what it does. `label` is the words of the option when the source has no card
  * to name (the title). An offer says only that the plan is usable, never
  * whether its user can pay: the engine dry-runs the plan to learn that.
  */
final case class CampaignPlanOffer(source: CampaignPlanSource, label: String,
    costs: Vector[CampaignPlanCost], effects: Vector[CampaignPlanEffect])
```

with:

```scala
  }
}

/** One battle plan a power offers now: where it comes from, what it costs and
  * what it does. `label` is the words of the option when the source has no card
  * to name (the title). An offer says only that the plan is usable, never
  * whether its user can pay: the engine dry-runs the plan to learn that.
  *
  * The costs and effects must not depend on anything using the plan changes
  * (the resources it spends, the orientation of its card, the warbands it
  * moves), because the plan is rebuilt from a fresh offer whenever a walk
  * resumes inside it.
  */
final case class CampaignPlanOffer(source: CampaignPlanSource, label: String,
    costs: Vector[CampaignPlanCost], effects: Vector[CampaignPlanEffect])
```

In `src/main/scala/oathdigital/model/Decisions.scala`, replace:

```scala
  final case class FavorBank(ref: DecisionOptionRef.FavorBank)
      extends DecisionOption

  /** The option presenting `ref`, for every kind whose name the projector
    * resolves itself. A button has no such name: its label is authored, so
    * a reference alone cannot present one.
```

with:

```scala
  final case class FavorBank(ref: DecisionOptionRef.FavorBank)
      extends DecisionOption

  /** An option that states what choosing it costs, which the projector words.
    * The choice is the wrapped option's: `ref` is its reference, so an answer
    * names the same thing whether or not the option carries a price, and the
    * price never affects legality.
    */
  final case class Priced(option: DecisionOption, price: OptionPrice)
      extends DecisionOption {
    def ref: DecisionOptionRef = option.ref
  }

  /** The option presenting `ref`, for every kind whose name the projector
    * resolves itself. A button has no such name: its label is authored, so
    * a reference alone cannot present one.
```

In `src/main/scala/oathdigital/model/Decisions.scala`, replace:

```scala
    case value: DecisionOptionRef.Deck => Some(Deck(value))
    case value: DecisionOptionRef.FavorBank => Some(FavorBank(value))
  }
}

/** One named bucket a [[DecisionQuery.Partition]] spreads its options across.
```

with:

```scala
    case value: DecisionOptionRef.Deck => Some(Deck(value))
    case value: DecisionOptionRef.FavorBank => Some(FavorBank(value))
  }
}

/** What choosing an option costs the chooser, as a dry run of the choice found
  * it: favor and secrets paid (placed, or burnt to the shared bank) and
  * warbands sacrificed. Everything is a count of what leaves the chooser.
  */
final case class OptionPrice(favor: Int = 0, secrets: Int = 0,
    favorBurnt: Int = 0, secretsBurnt: Int = 0, warbands: Int = 0) {
  require(favor >= 0 && secrets >= 0 && favorBurnt >= 0 && secretsBurnt >= 0 &&
    warbands >= 0, "an option price is never negative")
  def isFree: Boolean = this == OptionPrice()
}

/** One named bucket a [[DecisionQuery.Partition]] spreads its options across.
```

In `src/main/scala/oathdigital/model/PowerWindow.scala`, replace:

```scala
  }
  case object CampaignDefenderBattlePlans extends CampaignWindow {
    val key = "campaign.defender-battle-plans"
  }
  case object CampaignAfterOutcome extends CampaignWindow {
    val key = "campaign.after-outcome"
```

with:

```scala
  }
  case object CampaignDefenderBattlePlans extends CampaignWindow {
    val key = "campaign.defender-battle-plans"
  }
  /** One battle plan being paid for and applied. A power may add to what a
    * chosen plan costs or does, matching on the plan's owner, side and source.
    */
  case object CampaignPlanApplication extends CampaignWindow {
    val key = "campaign.plan-application"
  }
  case object CampaignAfterOutcome extends CampaignWindow {
    val key = "campaign.after-outcome"
```

Create `src/main/scala/oathdigital/application/PriceDetails.scala`:

```scala
package oathdigital.application

import oathdigital.model.OptionPrice

/** The cost of an option, worded for display: what a player weighs when they
  * choose a battle plan. Every part is a line of its own, in a fixed order, and
  * a part that costs nothing is not mentioned.
  */
private[application] object PriceDetails {
  def of(price: OptionPrice): Vector[String] = Vector(
    line(price.favor, "favor", "favor", ""),
    line(price.secrets, "secret", "secrets", ""),
    line(price.favorBurnt, "favor", "favor", " burnt"),
    line(price.secretsBurnt, "secret", "secrets", " burnt"),
    if (price.warbands == 0) ""
    else s"Cost: sacrifice ${price.warbands} warband" +
      (if (price.warbands == 1) "" else "s")).filter(_.nonEmpty)

  private def line(count: Int, one: String, many: String, suffix: String)
      : String =
    if (count == 0) ""
    else s"Cost: $count ${if (count == 1) one else many}$suffix"
}
```

Create `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanApplication.scala`:

```scala
package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.operations.Costs
import oathdigital.model._

/** One chosen battle plan being paid for and applied. It carries the side, the
  * user and the source, in the window `CampaignPlanApplication`, so a power can
  * change what a plan costs or does by matching on it (as Silver Tongue matches
  * `PlacementTree`), and a dry run of it answers whether the user can pay.
  *
  * The children have a fixed shape, a slot each for revealing the source,
  * paying, sacrificing, and then one per effect, and every slot that depends on
  * state is a `Branch`. A walk that parks on a decision inside a plan resumes
  * against the state the earlier slots changed, so a slot must still select the
  * same children then, and a slot that has nothing to do selects none.
  *
  *  - A facedown adviser is revealed when it is used.
  *  - Costs are paid onto the source card, which may already hold resources. A
  *    plan paid outside its owner's turn settles at once: favor goes to the
  *    card's suit bank and secrets flip facedown (see `PayCost`). A bandit
  *    defender pays nothing, and records that it applied the plan.
  *  - A warband sacrifice asks which force pays when several could, then kills
  *    one warband. It comes before every effect, so a plan that cannot pay is
  *    refused before it does anything.
  */
final class CampaignPlanApplication(catalog: ExecutableCatalog,
    val setup: CampaignSetup, val side: CampaignPlanSide,
    val offered: OfferedPlan) extends Operation {
  def source: CampaignPlanSource = offered.offer.source
  /** The player who uses the plan; `None` for a bandit defender. */
  def user: Option[PlayerId] = CampaignPlans.userOf(setup, side)

  override val window: Option[PowerWindow] =
    Some(PowerWindow.CampaignPlanApplication)

  override val children: Vector[Operation] = Vector[Operation](
    Branch((ready, _) => reveal(ready)),
    Branch((_, _) => pay),
    Branch((ready, _) => sacrifice(ready))) ++ offered.offer.effects.map(effect) :+
    Branch((_, _) => marker)

  /** A bandit defender chooses nothing, so a later window learns it applied the
    * plan from this record.
    */
  private def marker: Vector[Operation] =
    if (user.nonEmpty) Vector.empty
    else Vector(ModifyDicePool(CampaignPlans.appliedMarker(
      CampaignPlans.refOf(source)), 1))

  private def reveal(ready: ReadyGame): Vector[Operation] = source match {
    case CampaignPlanSource.Adviser(player, id) if ready.game.current.players
        .find(_.player == player).exists(_.advisers.exists {
          case held: DenizenState =>
            held.id == id && held.orientation == Orientation.FaceDown
          case _ => false }) =>
      Vector(Move(Piece.Card(id), PositionedLocation(Location.PlayArea(player)),
        PositionedLocation(Location.PlayArea(player)),
        resultingOrientation = Some(Orientation.FaceUp)))
    case _ => Vector.empty
  }

  private def pay: Vector[Operation] = {
    val costs = offered.offer.costs
    def total(pick: PartialFunction[CampaignPlanCost, Int]): Int =
      costs.collect(pick).sum
    val cost = Cost(
      favor = total { case CampaignPlanCost.Favor(count) => count },
      secret = total { case CampaignPlanCost.Secret(count) => count },
      favorBurnt = total { case CampaignPlanCost.FavorBurnt(count) => count },
      secretBurnt = total { case CampaignPlanCost.SecretBurnt(count) => count })
    if (cost == Cost.free) Vector.empty
    else user match {
      case None => Vector.empty
      case Some(player) => CampaignPlans.cardOf(source) match {
        case Some(card) => Vector(payment(Costs.onCard(player, card, cost,
          catalog, intoOccupied = true)))
        case None if cost.favor + cost.secret == 0 =>
          Vector(payment(PayCost(player, Location.PlayArea(player), cost)))
        case None => Vector(refuse(
          "the title has no card to place a plan's cost on"))
      }
    }
  }

  /** The payment runs as a batch, not as the composite's own moves, so that
    * the pipeline settles it at once when its payer is not the active player.
    */
  private def payment(pay: PayCost): Operation =
    BuildOps((_, _) => Right(Vector[CoreOperation](pay)))

  private def refuse(detail: String): Operation =
    BuildOps((_, _) => Left(OathViolation.InvalidEventOrder(detail)))

  /** The forces a warband can be sacrificed from: a Raid defender's board, or
    * each target site the defender rules and holds a warband on.
    */
  private def sacrificeFrom(ready: ReadyGame, player: PlayerId)
      : Vector[Location] = setup.kind match {
    case CampaignKind.Raid => ready.game.current.players
      .find(_.player == player).filter(_.board.warbands > 0)
      .map(_ => Location.PlayArea(player): Location).toVector
    case CampaignKind.Conquest => setup.targetSites.filter(site =>
      CampaignSetup.defenderAt(ready, site).contains(
        CampaignDefender.Player(player)) &&
        ready.game.current.map.sites.get(site).exists(_.forces match {
          case SiteForces.Occupied(_, count) => count > 0
          case SiteForces.Empty => false
        })).map(site => Location.Site(site): Location)
  }

  private def sacrifice(ready: ReadyGame): Vector[Operation] =
    if (!offered.offer.costs.contains(CampaignPlanCost.SacrificeWarband))
      Vector.empty
    else user match {
      case Some(player) if side == CampaignPlanSide.Defender =>
        sacrificeFrom(ready, player) match {
          case Vector() => Vector(refuse(
            "the plan needs a warband in the defender's force to sacrifice"))
          case Vector(only) => Vector(kill(ready, player, only))
          case several => Vector(
            Decide(CampaignIds.planSacrifice, player, DecisionQuery.ChooseOne(
              several.collect { case Location.Site(site) =>
                DecisionOption.Site(DecisionOptionRef.Site(site)): DecisionOption },
              heading = Some("Choose the site to sacrifice a warband from"))),
            BuildOps((state, pending) => PlanAnswers.site(pending)
              .toRight(OathViolation.InvalidEventOrder(
                "no site is chosen for the plan's sacrifice"))
              .flatMap(site => killOps(state, player, Location.Site(site)))))
        }
      case _ => Vector(refuse(
        "only a player defender can pay a warband sacrifice"))
    }

  private def kill(ready: ReadyGame, player: PlayerId, from: Location)
      : Operation = BuildOps((state, _) => killOps(state, player, from))

  private def killOps(ready: ReadyGame, player: PlayerId, from: Location)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val kind = from match {
      case Location.Site(site) => ready.game.current.map.sites.get(site)
        .map(_.forces).collect { case SiteForces.Occupied(force, _) => force }
      case _ => ready.game.current.players.find(_.player == player)
        .map(p => ForceKind.Exile(p.lineage))
    }
    kind.toRight(OathViolation.InvalidEventOrder(
      "the plan's sacrifice has no warband to kill")).map(force =>
      Vector[CoreOperation](Sacrifice(player, Piece.Warbands(force, 1),
        PositionedLocation(from))))
  }

  private def effect(effect: CampaignPlanEffect): Operation = effect match {
    case CampaignPlanEffect.AddAttackDice(count) =>
      ModifyDicePool(CampaignIds.attackPool, count)
    case CampaignPlanEffect.RemoveAttackDice(count) =>
      ModifyDicePool(CampaignIds.attackPool, -count)
    case CampaignPlanEffect.AddDefenseDice(count) =>
      ModifyDicePool(CampaignIds.defensePool, count)
    case CampaignPlanEffect.Run(operations) => Sequence(operations)
  }
}

/** Reads of the choices a plan's own decisions recorded. */
private[campaign] object PlanAnswers {
  def site(pending: PendingTree): Option[SiteId] = pending.answered.reverse
    .collectFirst {
      case Answered(CampaignIds.planSacrifice,
          DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Site(site)), _) => site
    }
}
```

Create `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanChoice.scala`:

```scala
package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.OfferHost
import oathdigital.model._

/** One side's battle-plan window: the node the powers' `Offer`s are folded into.
  * A pass of it, inside a `Repeat`, asks the user to choose one plan and then
  * pays for and applies it, so the next pass offers what is still usable after
  * the payment.
  *
  * What a pass walks:
  *
  *  - A player asks a decision listing the plans still unchosen that the user
  *    can pay for (a dry run of each plan says so, with every power's changes
  *    to its cost, and prices the option from what it recorded), then "Finish".
  *    Nothing usable means nothing is asked, the pass does nothing, and the
  *    `Repeat` ends.
  *  - The chosen plan is applied as a [[CampaignPlanApplication]].
  *  - A bandit defender applies every cost-free plan it is offered and can
  *    pay for, without asking.
  *
  * A walk resuming inside a pass keeps the two slots of the pass whatever is
  * offered, because the plan it is paying for has been chosen and is no longer
  * listed.
  */
final class CampaignPlanChoice(catalog: ExecutableCatalog, val actor: PlayerId,
    val side: CampaignPlanSide) extends OfferHost {
  override val window: Option[PowerWindow] = Some(side match {
    case CampaignPlanSide.Attacker => PowerWindow.CampaignAttackerBattlePlans
    case CampaignPlanSide.Defender => PowerWindow.CampaignDefenderBattlePlans
  })
  override val children: Vector[Operation] = Vector.empty

  def expand(offers: Vector[OfferedPlan], pass: OfferHost.Pass)
      : Vector[Operation] = {
    val pending = PendingTree(Vector.empty, pass.answered)
    CampaignSetup.setup(pass.state, actor, pending).fold(
      Vector.empty[Operation])(setup => CampaignPlans.userOf(setup, side) match {
      case None => banditPlans(setup, CampaignPlans.sorted(offers), pass)
      case Some(user) => userPass(setup, user, CampaignPlans.sorted(offers),
        pending, pass)
    })
  }

  private def applicationOf(setup: CampaignSetup, offered: OfferedPlan)
      : CampaignPlanApplication =
    new CampaignPlanApplication(catalog, setup, side, offered)

  /** Bandits pay nothing, so a plan that costs is never applied, and neither is
    * one a power makes unpayable for them (a surcharge in secrets, which bandits
    * do not hold).
    */
  private def banditPlans(setup: CampaignSetup, offers: Vector[OfferedPlan],
      pass: OfferHost.Pass): Vector[Operation] = offers
    .filter(_.offer.costs.isEmpty).map(applicationOf(setup, _))
    .filter(application => pass.applies(application).isRight)

  private def userPass(setup: CampaignSetup, user: PlayerId,
      offers: Vector[OfferedPlan], pending: PendingTree, pass: OfferHost.Pass)
      : Vector[Operation] = {
    val decisionId = CampaignIds.planDecision(side)
    val chosen = CampaignAnswers.picks(pending, decisionId)
    val listed = offers.filterNot(offered =>
      chosen.contains(CampaignPlans.refOf(offered.offer.source)))
      .flatMap(offered => pass.applies(applicationOf(setup, offered)).toOption
        .map(operations => PlanPrice.priced(CampaignPlans.optionOf(offered),
          offered.offer, operations)))
    if (listed.isEmpty && !pass.resuming) Vector.empty
    else Vector[Operation](
      Branch((_, _) => if (listed.isEmpty) Vector.empty
        else Vector(decision(decisionId, user, listed))),
      Branch((_, tree) => CampaignAnswers.lastPick(tree, decisionId) match {
        case None => Vector.empty
        case Some(ref) => offers.find(offered =>
          CampaignPlans.refOf(offered.offer.source) == ref) match {
          case Some(offered) => Vector(applicationOf(setup, offered))
          case None => Vector(BuildOps((_, _) => Left(
            OathViolation.InvalidEventOrder(
              "a chosen Campaign battle plan is no longer available"))))
        }
      }))
  }

  private def decision(id: String, user: PlayerId,
      listed: Vector[DecisionOption]): Decide = Decide(id, user,
    DecisionQuery.ChooseOne(listed :+
      DecisionOption.Button(CampaignIds.finish, "Finish battle plans"),
    heading = Some(side match {
      case CampaignPlanSide.Attacker => "Choose a battle plan, or finish"
      case CampaignPlanSide.Defender =>
        "Defender: choose a battle plan, or finish"
    })))
}
```

Create `src/main/scala/oathdigital/gameplay/actions/campaign/PlanPrice.scala`:

```scala
package oathdigital.gameplay.actions.campaign

import oathdigital.model._

/** What a battle plan costs its user, for the option that offers it. The payments
  * are read from the operations a dry run of the plan's application recorded,
  * not from the offer, so a power that adds to the cost (a surcharge on the
  * enemy's plans) shows in it: a payment is a `PayCost` whether it is placed or
  * burnt, and a secret paid by turning it facedown is a `FlipSecrets`.
  *
  * A warband sacrifice is read from the offer. The dry run stops at the decision
  * that asks which force pays, before the warband is killed, so it may not have
  * recorded the kill yet.
  */
private[campaign] object PlanPrice {
  def of(offer: CampaignPlanOffer, operations: Vector[CoreOperation])
      : OptionPrice = {
    val paid = operations.foldLeft(OptionPrice()) {
      case (price, PayCost(_, _, cost, _, _, _)) => price.copy(
        favor = price.favor + cost.favor,
        secrets = price.secrets + cost.secret,
        favorBurnt = price.favorBurnt + cost.favorBurnt,
        secretsBurnt = price.secretsBurnt + cost.secretBurnt)
      case (price, FlipSecrets(_, amount, SecretSide.FaceUp, SecretSide.FaceDown)) =>
        price.copy(secrets = price.secrets + amount)
      case (price, _) => price
    }
    if (offer.costs.contains(CampaignPlanCost.SacrificeWarband))
      paid.copy(warbands = 1)
    else paid
  }

  /** The option, carrying its price when it has one. */
  def priced(option: DecisionOption, offer: CampaignPlanOffer,
      operations: Vector[CoreOperation]): DecisionOption = {
    val price = of(offer, operations)
    if (price.isFree) option else DecisionOption.Priced(option, price)
  }
}
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Offer, Transform}
import oathdigital.model._

/** A power that offers a Campaign battle plan. It is chosen at the plan step,
  * by the ruler of its source, so it is automatic: it needs no selection at the
  * start of the action, whatever the catalog's `persistent` flag says.
  *
  * A plan states three things:
  *
  *  - `plan` says whether the plan is usable now and what it costs and does. It
  *    reads only where its card stands and the Campaign's setup; whether the
  *    user can pay is left to the plan window, which dry-runs the plan. The
  *    offer is hooked at the window of every side in `sides`, and `plan` reads
  *    the side from its context.
  *  - `later` is what a used plan does when the Campaign reaches a later window
  *    (Outriders ignores the skulls when the attack is scored). It runs only when
  *    the plan was chosen, and its operations are appended to the window's
  *    children, so a plan that must run last is registered at the end of the
  *    Campaign, which is `CampaignActionEligibility`, the root.
  *  - `cardRef` is how the plan's source is named in a decision, which is how a
  *    later window learns the plan was chosen.
  */
trait BattlePlan extends ContributingPower {
  /** How the plan's source is named in the plan decision. */
  def cardRef: DecisionOptionRef
  /** The sides of the Campaign that may use the plan. */
  def sides: Set[CampaignPlanSide]
  /** The plan, when it is usable now for `context`'s side. */
  def plan(context: PlanContext): Option[CampaignPlanOffer]
  /** What a used plan adds at a later window. */
  def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map.empty

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  final override def resolution: PowerResolution = PowerResolution.Automatic

  final override lazy val contributions: Map[PowerWindow, Vector[Contribution]] = {
    val offers: Map[PowerWindow, Vector[Contribution]] = sides.toVector.map {
      side => BattlePlan.windowOf(side) -> Vector[Contribution](Offer(ctx =>
        PlanContext.of(ctx).filter(_.side == side).flatMap(plan)))
    }.toMap
    val afterwards: Map[PowerWindow, Vector[Contribution]] = later.map {
      case (window, build) => window -> Vector[Contribution](Transform(
        (ctx, children) => children :+ Branch((ready, pending) =>
          PlanUse.chosen(ready, pending, ctx.activePlayer, cardRef, sides,
            BattlePlan.outcomeKnown(window)).fold(Vector.empty[Operation])(build))))
    }
    offers ++ afterwards
  }
}

object BattlePlan {
  def windowOf(side: CampaignPlanSide): PowerWindow = side match {
    case CampaignPlanSide.Attacker => PowerWindow.CampaignAttackerBattlePlans
    case CampaignPlanSide.Defender => PowerWindow.CampaignDefenderBattlePlans
  }

  /** The windows walked after `RecordCampaignResult`, where the recorded result
    * is this Campaign's. The root is the last of them.
    */
  private val afterOutcome: Set[PowerWindow] = Set(PowerWindow.CampaignLosses,
    PowerWindow.CampaignPlacement, PowerWindow.CampaignRaidTransfer,
    PowerWindow.CampaignRaidRelocation, PowerWindow.CampaignActionEligibility)

  def outcomeKnown(window: PowerWindow): Boolean = afterOutcome(window)
}
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlans.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The Campaign battle plans the first batch's engine ported, registered
  * together: the title's defense, Outriders, Brass Army and Watchdog. A plan
  * whose card is absent from `catalog` is omitted, and the title's defense,
  * which no card prints, is always present.
  */
object BattlePlans {
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector[ContributingPower](TitleDefensePlan.plan) ++
      Outriders.forCatalog(catalog).toVector ++
      BrassArmy.forCatalog(catalog).toVector ++
      Watchdog.forCatalog(catalog).toVector
}
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/BrassArmy.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Brass Army (relic R25), an attacker's battle plan: "[secret] +4 attack dice."
  *
  * The relic must be faceup in the attacker's play area. The secret is placed
  * onto the relic, which may already hold resources: a battle plan pays into an
  * occupied card. The dice join the attack pool but not the force, so no loss,
  * sacrifice or placement limit changes. The relic's other power, the pawn-move
  * restriction, is not part of this plan.
  */
final case class BrassArmy private (relicId: RelicId) extends BattlePlan {
  def id: PowerId = BrassArmy.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Relic(relicId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.relic(relicId).map(source => CampaignPlanOffer(source,
      "Brass Army: add 4 attack dice", Vector(CampaignPlanCost.Secret(1)),
      Vector(CampaignPlanEffect.AddAttackDice(BrassArmy.Dice))))
}

object BrassArmy {
  val id: PowerId = PowerId("relic.brass-army.campaign")
  val Dice: Int = 4

  def forCatalog(catalog: ExecutableCatalog): Option[BrassArmy] =
    CatalogCards.relic(catalog, id).map(new BrassArmy(_))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/Outriders.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignIds}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Outriders (card 104), an attacker's battle plan: "Ignore all skulls you roll."
  *
  * Choosing it costs nothing and changes no dice. Once the attack is scored, the
  * plan writes the roll outcome again without the skull cap, so no skull removes
  * a warband and every skull face keeps its two swords. A facedown Outriders is
  * revealed when it is chosen, as any plan's source is.
  */
final case class Outriders private (cardId: DenizenId) extends BattlePlan {
  def id: PowerId = Outriders.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Outriders: ignore all attack skulls", Vector.empty, Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (_ => Vector(BuildOps((ready, _) =>
      Right(ready.game.current.rollOutcomes.get(CampaignIds.attackPool).toVector
        .map(_ => ModifyRollOutcome(CampaignIds.attackPool, Some(0),
          Some(AttackDieFace.score(CampaignBattle.attackFacesOf(ready))))))))))
}

object Outriders {
  val id: PowerId = PowerId("denizen.outriders")

  def forCatalog(catalog: ExecutableCatalog): Option[Outriders] =
    CatalogCards.denizen(catalog, id).map(new Outriders(_))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/PlanContext.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.actions.campaign.{CampaignAnswers, CampaignIds, CampaignPlans, CampaignSetup}
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

/** What a battle plan reads when it is asked whether it is usable now: the
  * state, the Campaign's setup and the side the window is for.
  *
  * A plan is used only by the ruler of its source, so a plan asks this for where
  * a card stands: an adviser of the plan's user, a relic held faceup by the
  * user, or a card at a site the user rules. A bandit defender is the ruler of
  * the sites Bandits rule and holds nothing else.
  */
final case class PlanContext(ready: ReadyGame, setup: CampaignSetup,
    side: CampaignPlanSide) {
  /** The player who would use the plan; `None` for a bandit defender. */
  def user: Option[PlayerId] = CampaignPlans.userOf(setup, side)

  private def ruler: CampaignDefender = user.fold[CampaignDefender](
    CampaignDefender.Bandits)(CampaignDefender.Player(_))

  /** Whether the plan's user rules `site`. */
  def rules(site: SiteId): Boolean =
    CampaignSetup.defenderAt(ready, site).contains(ruler)

  private def held: Option[PlayerState] = user.flatMap(player =>
    ready.game.current.players.find(_.player == player))

  private def sitesRuled: Vector[(SiteId, SiteState)] =
    ready.game.current.map.inPlay.filter(rules).flatMap(site =>
      ready.game.current.map.sites.get(site).map(site -> _))

  /** A denizen the user holds as an adviser, in either orientation, or one that
    * is faceup at a site the user rules.
    */
  def denizen(id: DenizenId): Option[CampaignPlanSource] =
    held.flatMap(player => player.advisers.collectFirst {
      case card: DenizenState if card.id == id =>
        CampaignPlanSource.Adviser(player.player, id): CampaignPlanSource
    }).orElse(sitesRuled.collectFirst {
      case (site, state) if state.denizens.exists {
        case card: DenizenState =>
          card.id == id && card.orientation == Orientation.FaceUp
        case _ => false
      } => CampaignPlanSource.SiteCard(site, id): CampaignPlanSource
    })

  /** A relic the user holds faceup. A relic at a site is facedown, and a plan
    * cannot use it.
    */
  def relic(id: RelicId): Option[CampaignPlanSource] = held.flatMap(player =>
    player.relics.collectFirst {
      case card if card.id == id && card.orientation == Orientation.FaceUp =>
        CampaignPlanSource.Relic(player.player, id): CampaignPlanSource
    })

  /** An edifice on the given face at a site the user rules. */
  def edifice(id: EdificeId, face: EdificeSide)
      : Option[CampaignPlanSource.SiteEdifice] = sitesRuled.collectFirst {
    case (site, state) if state.denizens.exists {
      case card: EdificeState => card.id == id && card.side == face
      case _ => false
    } => CampaignPlanSource.SiteEdifice(site, id)
  }

  /** Whether the plan's user has their pawn at `site`. */
  def pawnAt(site: SiteId): Boolean = user.exists(player =>
    ready.game.current.players.find(_.player == player)
      .exists(_.pawnSite.contains(site)))

  /** Whether `site` is a target of the Campaign. */
  def targets(site: SiteId): Boolean = setup.targetSites.contains(site)

  /** Whether any target of a Conquest is in `region`. A Raid targets no site. */
  def targetsIn(region: Region): Boolean = setup.targetSites.exists(site =>
    ready.game.current.map.regionOf(site).contains(region))
}

object PlanContext {
  /** The context of the plan window `ctx` is gathered for; `None` at any other
    * window, or before the Campaign's force is known.
    */
  def of(ctx: PowerCtx): Option[PlanContext] = (ctx.window match {
    case PowerWindow.CampaignAttackerBattlePlans =>
      Some(CampaignPlanSide.Attacker)
    case PowerWindow.CampaignDefenderBattlePlans =>
      Some(CampaignPlanSide.Defender)
    case _ => None
  }).flatMap(side => CampaignSetup.setup(ctx.state, ctx.activePlayer,
    PendingTree(ctx.nodePath, ctx.answered)).map(PlanContext(ctx.state, _, side)))
}

/** A plan that was used, as a later window sees it. `result` is the Campaign's
  * recorded result once the outcome is known (the windows from the losses on),
  * and is what a step after the losses must read, because the losses change the
  * board.
  */
final case class PlanUse(side: CampaignPlanSide, actor: PlayerId,
    result: Option[CampaignResult], ready: ReadyGame) {
  /** The player who used the plan; `None` for a bandit defender. */
  def user: Option[PlayerId] = side match {
    case CampaignPlanSide.Attacker => Some(actor)
    case CampaignPlanSide.Defender => result.map(_.defender).collect {
      case CampaignDefender.Player(player) => player
    }
  }

  /** Whether the plan's user won. `None` before the outcome is known. */
  def won: Option[Boolean] = result.map(_.attackerWins == (
    side == CampaignPlanSide.Attacker))
}

object PlanUse {
  /** The plan named by `ref`, if it was used on one of `sides` in this
    * Campaign: chosen by a player, or applied by a bandit defender, which the
    * application recorded (`CampaignPlans.appliedMarker`). `afterOutcome` says
    * whether `lastCampaignResult` is this Campaign's already.
    */
  def chosen(ready: ReadyGame, pending: PendingTree, actor: PlayerId,
      ref: DecisionOptionRef, sides: Set[CampaignPlanSide],
      afterOutcome: Boolean): Option[PlanUse] = {
    val picked = sides.toVector.find(side => CampaignAnswers.picks(pending,
      CampaignIds.planDecision(side)).contains(ref))
    val banditApplied = Option.when(sides(CampaignPlanSide.Defender) &&
      ready.game.current.rollPools.contains(CampaignPlans.appliedMarker(ref)))(
      CampaignPlanSide.Defender)
    picked.orElse(banditApplied).map(side => PlanUse(side, actor,
      Option.when(afterOutcome)(ready.game.current.lastCampaignResult)
        .flatten.filter(_.attacker == actor), ready))
  }
}
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/TitleDefensePlan.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.model._

/** The title's defense, a defender's battle plan that no card prints: the holder
  * of the Oathkeeper title, when defending, adds one defense die, and a Usurper
  * adds two. Only a player defender who holds the title has it, and it has no
  * card, so its option is a button and an added cost cannot be placed on it.
  *
  * Like the rulebook's own rules it is always present, and it hooks the plan
  * window until the defender uses it.
  */
final case class TitleDefensePlan private () extends BattlePlan {
  def id: PowerId = TitleDefensePlan.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Button("title")
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] = {
    val title = context.ready.game.current.title
    context.user.filter(user => title.holder.contains(user) &&
      context.setup.defender == CampaignDefender.Player(user)).map { user =>
      val dice = title.side match {
        case TitleSide.Oathkeeper => 1
        case TitleSide.Usurper => 2
      }
      CampaignPlanOffer(CampaignPlanSource.Title(user),
        s"${title.side} title: add $dice defense ${if (dice == 1) "die" else "dice"}",
        Vector.empty, Vector(CampaignPlanEffect.AddDefenseDice(dice)))
    }
  }
}

object TitleDefensePlan {
  val id: PowerId = PowerId("title.oathkeeper-defense")
  val plan: TitleDefensePlan = new TitleDefensePlan()
}
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/Watchdog.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Watchdog (card 234), a defender's battle plan: "+1 defense die if any target
  * is in the Cradle."
  *
  * It is used by the ruler of its card, from an adviser or a ruled site. A
  * bandit defender uses it from a site Bandits rule, without choosing. A Raid
  * targets no site, so it never applies to one.
  */
final case class Watchdog private (cardId: DenizenId) extends BattlePlan {
  def id: PowerId = Watchdog.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    if (!context.targetsIn(Region.Cradle)) None
    else context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Watchdog: add 1 defense die", Vector.empty,
      Vector(CampaignPlanEffect.AddDefenseDice(1))))
}

object Watchdog {
  val id: PowerId = PowerId("denizen.watchdog")

  def forCatalog(catalog: ExecutableCatalog): Option[Watchdog] =
    CatalogCards.denizen(catalog, id).map(new Watchdog(_))
}
```


- [ ] **Step 4: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignPlanWindowSuite oathdigital.gameplay.CampaignPlansSuite oathdigital.gameplay.CampaignProcedureSuite oathdigital.gameplay.CampaignRaidSuite oathdigital.gameplay.CampaignPowersSuite oathdigital.application.PricedOptionProjectionSuite oathdigital.application.CampaignResultProjectionSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS.

- [ ] **Step 5: Run the whole suite and the architecture check**

Run: `./sbtw test` and `python3 scripts/check-architecture.py`
Expected: PASS (1418 tests), and `architecture check passed`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: make Campaign battle plans powers

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

- [ ] **Step 7: Record sub-slice 3b**

The campaign architecture note described the plan handlers as a registry and listed their conversion as deferred. The design records what was built against E8, and the rulings appendix records the behaviour.

In `docs/architecture/bounded-campaign.md`, replace:

```markdown
  committed force. The defense pool holds the targets' printed defense: for a
  Raid, two for the pawn, each targeted relic's printed defense and three per
  banner. A pool of zero dice is not created and is never rolled.
- **Battle plans.** Each window is a `Repeat` of a choice and its application, so
  each plan is paid and applied the moment it is chosen and the next options see
  the result. Options are the unused plan sources plus an explicit Finish. A
  source may be chosen once. The window finishes by itself when no plan is left.
  A player defender owns the defender window. A bandit defender applies its
  cost-free, choice-free plans automatically. Plan handlers stay in a registry
  (`CampaignPlans`): Outriders (ignore all skulls), Brass Army (secret for four
  attack dice), the title (one defense die for an Oathkeeper, two for a Usurper)
  and Watchdog (one defense die at a Cradle target).
- **Attack.** The roll is automatic. A skull removes one force warband and its two
  swords count only when that loss can be paid. Skulls beyond the force add
  nothing, and Outriders ignores every skull. Hollow swords score one per pair.
  The capped result is written over the rolled outcome. Brass Army's dice do not
  raise the physical force or any loss, sacrifice or placement limit.
- **Sacrifice.** `ChooseAmount(0, force - skulls)` warbands for one attack each.
```

with:

```markdown
  committed force. The defense pool holds the targets' printed defense: for a
  Raid, two for the pawn, each targeted relic's printed defense and three per
  banner. A pool of zero dice is not created and is never rolled.
- **Battle plans.** Each side's window is a `Repeat` of `CampaignPlanChoice` passes. The node is an
  `OfferHost`: its window gathers the `Offer` contributions of the powers in play, and each pass asks
  the user to choose one plan they can pay for, or Finish, and applies it as a
  `CampaignPlanApplication` (window `CampaignPlanApplication`). Each plan is therefore paid and
  applied the moment it is chosen, and the next options see the result. A plan is usable only by the
  ruler of its source: the holder of an adviser or of a faceup relic, or the ruler of the site a card
  or edifice stands at. Whether the user can pay is found by dry-running the plan's application
  through the same windows, so a power that adds to a cost changes what is offered, and each option
  carries the price the dry run found. A source may be chosen once. A pass with nothing to offer does
  nothing, and the loop ends. A player defender owns the defender window. A bandit defender applies
  every cost-free plan of a site Bandits rule that no power makes unpayable, without choosing, and
  records each in a pool marker so a later window can read it. A facedown adviser is revealed when
  it is used. The plans are `BattlePlan` powers registered through `BattlePlans`: `TitleDefensePlan`
  (one defense die for an Oathkeeper, two for a Usurper), `Outriders` (ignore all skulls),
  `BrassArmy` (a secret for four attack dice) and `Watchdog` (one defense die at a Cradle target).
- **Attack.** The roll is automatic. A skull removes one force warband and its two
  swords count only when that loss can be paid. Skulls beyond the force add
  nothing. Hollow swords score one per pair. Outriders, once chosen, scores the attack
  again without the cap.
  The capped result is written over the rolled outcome. Brass Army's dice do not
  raise the physical force or any loss, sacrifice or placement limit.
- **Sacrifice.** `ChooseAmount(0, force - skulls)` warbands for one attack each.
```

In `docs/architecture/bounded-campaign.md`, replace:

```markdown
  answer check and simulation all see the filtered set. An optional decision left
  empty is dropped. A required decision left empty rejects the start with the
  restriction's own violation.
- Plan handlers (Outriders, Brass Army, the title, Watchdog) are registry handlers,
  not power contributions. Converting them is deferred.

## Unsupported Campaign rules

```

with:

```markdown
  answer check and simulation all see the filtered set. An optional decision left
  empty is dropped. A required decision left empty rejects the start with the
  restriction's own violation.
- Battle plans are `BattlePlan` powers (see Battle plans above): an `Offer` at a plan window
  and, for what a used plan does later, a hook at a later window that reads the picks from
  `PowerCtx.answered`.

## Unsupported Campaign rules

```

In `docs/architecture/bounded-campaign.md`, replace:

```markdown
is a `RollPayload` with `automatic = true`, which replay applies without asking the
dice source. The recorded result carries the key `attackerWins`, so a journal recorded
before that name cannot be read. Every other step is a recorded operation batch, including
`RecordCampaignResult`. The seven legacy Campaign events no longer exist, and
journals are forward-only.

## Rule changes from the legacy Campaign

```

with:

```markdown
is a `RollPayload` with `automatic = true`, which replay applies without asking the
dice source. The recorded result carries the key `attackerWins`, so a journal recorded
before that name cannot be read. Every other step is a recorded operation batch, including
`RecordCampaignResult`. A plan's payment is recorded as the requested `PayCost`, and replay
settles it again when its payer is not the active player. The seven legacy Campaign events
no longer exist, and journals are forward-only.

## Rule changes from the legacy Campaign

```

In `docs/architecture/bounded-campaign.md`, replace:

```markdown
- All rolls become automatic (Recover still parks on its roll).
- Real consent for the Pass, and a consent system in general.
- The first-game rule audit behind the dropped gates.
- Converting the plan handlers into power contributions.
- Further optional attacker, defender and deterministic bandit plan families,
  non-deterministic loss choices, and the additional Raid, victory, defeat and
  `At End` handlers. The timing windows exist and no behavior is inferred for them.
```

with:

```markdown
- All rolls become automatic (Recover still parks on its roll).
- Real consent for the Pass, and a consent system in general.
- The first-game rule audit behind the dropped gates.
- Further optional attacker, defender and deterministic bandit plan families,
  non-deterministic loss choices, and the additional Raid, victory, defeat and
  `At End` handlers. The timing windows exist and no behavior is inferred for them.
```

In `docs/superpowers/specs/2026-09-20-powers-design.md`, replace:

```markdown

> Status: design approved 2026-09-20. Slice 0 (E1 to E5), slice 1a, slice 1b, slice 1c, slice 1d and slice 2 (sub-slices 2a to 2f) are implemented; see the [Slice 0 plan](../plans/2026-09-20-powers-slice-0-foundations.md), the [slice 1a plan](../plans/2026-09-20-powers-slice-1a-when-played-and-simple-actions.md), the [slice 1b plan](../plans/2026-09-20-powers-slice-1b-dice-and-relic-draws.md) the [slice 1c plan](../plans/2026-09-20-powers-slice-1c-targets-and-information.md) the [slice 1d plan](../plans/2026-09-20-powers-slice-1d-movement.md) and the [slice 2 plan](../plans/2026-09-20-powers-slice-2-modifiers-restrictions-triggers.md) (all six sub-slices). Per-power rules are in [the rulings appendix](2026-09-20-powers-rulings.md). Extends the [procedure walker design](2026-09-05-procedure-walker-design.md) and follows the [Campaign port](2026-09-19-campaign-walker-design.md). Each slice below gets its own implementation plan, and slice 1 is split into four.

> Slice 3 (battle plans) is planned in four sub-slices: see its [plan](../plans/2026-09-20-powers-slice-3-battle-plans.md). Implemented so far: 3a.

## Goal and scope

```

with:

```markdown

> Status: design approved 2026-09-20. Slice 0 (E1 to E5), slice 1a, slice 1b, slice 1c, slice 1d and slice 2 (sub-slices 2a to 2f) are implemented; see the [Slice 0 plan](../plans/2026-09-20-powers-slice-0-foundations.md), the [slice 1a plan](../plans/2026-09-20-powers-slice-1a-when-played-and-simple-actions.md), the [slice 1b plan](../plans/2026-09-20-powers-slice-1b-dice-and-relic-draws.md) the [slice 1c plan](../plans/2026-09-20-powers-slice-1c-targets-and-information.md) the [slice 1d plan](../plans/2026-09-20-powers-slice-1d-movement.md) and the [slice 2 plan](../plans/2026-09-20-powers-slice-2-modifiers-restrictions-triggers.md) (all six sub-slices). Per-power rules are in [the rulings appendix](2026-09-20-powers-rulings.md). Extends the [procedure walker design](2026-09-05-procedure-walker-design.md) and follows the [Campaign port](2026-09-19-campaign-walker-design.md). Each slice below gets its own implementation plan, and slice 1 is split into four.

> Slice 3 (battle plans) is planned in four sub-slices: see its [plan](../plans/2026-09-20-powers-slice-3-battle-plans.md). Implemented so far: 3a and 3b.

## Goal and scope

```

In `docs/superpowers/specs/2026-09-20-powers-design.md`, replace:

```markdown
- Facedown advisers remain usable as plans and are revealed when used (NF p. 13).
- `CampaignResult.victorious` is renamed `attackerWins`. It touches the model, `CampaignResultProjectionCodec`, the shared DTO, the frontend result panel, the Campaign suites and docs. The wire key changes with it. Implemented in slice 3a.

### E9. Enclosing action on `PowerCtx`

Knights Errant nests a Campaign inside Muster and needs a hook on `CampaignCost` that applies only to that nested Campaign. `PowerCtx` does not expose answered decisions. `nodePath` does not identify the enclosing action, so `PowerCtx` gains the enclosing action's `ProcedureRef`. It was needed for Welcoming Party's origin and Knights Errant.
```

with:

```markdown
- Facedown advisers remain usable as plans and are revealed when used (NF p. 13).
- `CampaignResult.victorious` is renamed `attackerWins`. It touches the model, `CampaignResultProjectionCodec`, the shared DTO, the frontend result panel, the Campaign suites and docs. The wire key changes with it. Implemented in slice 3a.

**What slice 3b built.** The plan window is a node, `CampaignPlanChoice`, that implements `OfferHost`. The walker gathers the `Offer` contributions hooked at its window and hands the plans to it. It asks the decision, prices each option from a dry run of the plan's application (every power's changes to the cost included), and applies the chosen plan as a `CampaignPlanApplication`. The four plans that were registry handlers (Outriders, Brass Army, the title, Watchdog) are now powers. It differs from the E8 text above in these ways, each found at plan time:

- The price is a `DecisionOption.Priced(option, OptionPrice)`, which the projector words as the option's details. Campaign cannot use the preview gate (`requiresPlayableOption`), because its first step spends Supply before any decision.
- What a used plan does later is not a `CampaignPlanEffect` variant. The `BattlePlan` kit installs it as a hook at the later window (`later`), which reads the picks from `PowerCtx.answered` and the outcome from the recorded result, because a plan is not re-derived at the end of the Campaign. `CampaignPlanEffect` gains `RemoveAttackDice` and `Run`, and loses `IgnoreAttackSkulls` and `RevealSource`: the engine reveals a facedown adviser, and Outriders scores the attack again without the cap.
- Three engine changes E8 did not list: `PowerCtx.answered`; a `Repeat` pass that records nothing and asks nothing ends the loop; and replay settles a recorded `PayCost` whose payer is not the active player, which E4 specified but the walker's replay did not do.

### E9. Enclosing action on `PowerCtx`

Knights Errant nests a Campaign inside Muster and needs a hook on `CampaignCost` that applies only to that nested Campaign. `PowerCtx` does not expose answered decisions. `nodePath` does not identify the enclosing action, so `PowerCtx` gains the enclosing action's `ProcedureRef`. It was needed for Welcoming Party's origin and Knights Errant.
```

In `docs/superpowers/specs/2026-09-20-powers-rulings.md`, replace:

```markdown
### Slice 3 implementation notes

- **3a:** `CampaignResult.victorious` is now `attackerWins` in the model, the journal codec, the shared DTO and its codec, the result panel and the suites. It is true when the attacker prevailed and false when the defender did. The wire key changes with it, and journals are forward-only, so a game whose journal holds a recorded Campaign result cannot be read after this change.

## Slice 4: banner faces

```

with:

```markdown
### Slice 3 implementation notes

- **3a:** `CampaignResult.victorious` is now `attackerWins` in the model, the journal codec, the shared DTO and its codec, the result panel and the suites. It is true when the attacker prevailed and false when the defender did. The wire key changes with it, and journals are forward-only, so a game whose journal holds a recorded Campaign result cannot be read after this change.
- **3b:** every plan is a `BattlePlan` power declared as an `Offer` (where its card must stand, what it costs and does) and, when it acts later, a hook at a later window. The user must be the ruler of the source: the origin-site offer to a non-ruler is gone, and Brass Army no longer needs an empty relic. A defender's plan may carry a cost, paid at once. Costs are `Favor` and `Secret` (placed onto the card, which may be occupied), `FavorBurnt`, `SecretBurnt` and `SacrificeWarband` (a defender only: the board in a Raid, or a target site the defender rules in a Conquest, asking which when several). A plan that cannot be paid, with every power's added cost, is not offered, and a window with nothing to offer is skipped. The option states its price. A source is chosen once. A bandit defender applies every cost-free plan at a site Bandits rule that no power makes unpayable, pays nothing, and records what it applied in a pool marker (`campaign.plan-applied.<kind>.<id>`) that a later hook reads, because a bandit's plan is not an answer. A facedown adviser is revealed when it is chosen, and a card at a site is always faceup. Outriders scores the attack again without the skull cap, and Brass Army adds four dice to the pool but not to the force.

## Slice 4: banner faces

```


Run: `python3 scripts/check-markdown-links.py`
Expected: `Markdown link check passed`.

```bash
git add docs
git commit -m "docs: record slice 3b

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Sub-slice 3c: Simple plans

### Task 4: Mercenaries, Wrestlers, Fearsome Shield, the Ramparts and Battle Honors

**Files:**
- Create and modify: the files listed for Task 4 in "File Structure".
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/{PlanDriver, MercenariesSuite, WrestlersSuite, FearsomeShieldSuite, RampartSuite, BattleHonorsSuite}.scala`; `CampaignFixture` (modified).

**Interfaces:**
- Consumes: Task 3's `BattlePlan`, `PlanContext`, `PlanUse`, `CampaignPlanOffer`, the costs and effects.
- Produces: `object SimplePlans { def forCatalog(catalog): Vector[ContributingPower] }`; `PlanDiscard.denizen(catalog, user, card): Operation`; `Mercenaries`, `Wrestlers`, `FearsomeShield`, `ToweringRampart`, `CrackedRampart`, `BattleHonors`; test-side `PlanDriver.{winning, losing, commit, Run}` and `CampaignFixture.{withRelicFor, withEdifice}`.

Each power is a few lines over the kit.

| Card | Sides | Offer | Later |
| --- | --- | --- | --- |
| 12 Mercenaries | either | a card of the user's; cost `Favor(1)`; `AddAttackDice(3)` for an attacker, `RemoveAttackDice(3)` for a defender | when its user is defeated, discard the card by the standard denizen discard (`PlanDiscard`: the region after the site's region for a card on a site, after the region of the holder's pawn for an adviser) |
| 1 Wrestlers | defender | a card of the user's; cost `SacrificeWarband`; `AddDefenseDice(1)` | none |
| R27 Fearsome Shield | defender | a faceup relic of the user's; cost `SecretBurnt(2)`; `AddDefenseDice(2)` | none |
| E20 Towering Rampart | defender | the intact edifice at a site the user rules, when the user's pawn is there or the site is targeted; `AddDefenseDice(2)` | none |
| E20 Cracked Rampart | defender | the ruined edifice at a site the user rules, when the site is targeted; `AddDefenseDice(1)` | none |
| 2 Battle Honors | either | a card of the user's; free; no effect | when its user won, `Gain.Favor(user, Order, 2)`; for a bandit defender, the same favor moved from the Order bank to the shared bank |

`PlanDiscard` finds the card where it stands (an adviser of the user, or a site) and discards it as a standard denizen discard would: facedown to the pile of the region after the card's own region (`CardPlay.nextRegion`, the one rule for the region after), its favor to its suit's bank and its secrets to the user facedown. A card at a site is in the site's region. An adviser has no region of its own, so it takes the region of its holder's pawn. It attaches `DiscardRestrictions`, as every path that discards a card in play does.

The suites drive a whole Campaign through the rules with the production powers. `PlanDriver` chooses who wins by the dice it hands the walker (every attack die a sword, or half a sword) and the force the suite commits.

- [ ] **Step 1: Write the tests**

In `src/test/scala/oathdigital/gameplay/CampaignFixture.scala`, replace:

```scala
    p.copy(relics = p.relics :+ RelicState(RelicId(relic), Orientation.FaceUp,
      Tokens.empty)))

  def withSecrets(b: Board, faceUp: Int): Board = replacePlayer(b, b.actor)(p =>
    p.copy(board = p.board.copy(faceUpSecrets = faceUp)))

```

with:

```scala
    p.copy(relics = p.relics :+ RelicState(RelicId(relic), Orientation.FaceUp,
      Tokens.empty)))

  /** `player` holds a faceup relic. */
  def withRelicFor(b: Board, player: PlayerId, relic: String): Board =
    replacePlayer(b.copy(ready = scrub(b.ready, relic)), player)(p =>
      p.copy(relics = p.relics :+ RelicState(RelicId(relic), Orientation.FaceUp,
        Tokens.empty)))

  /** An edifice stands at `site`, on the given face. */
  def withEdifice(b: Board, site: SiteId, edifice: String, side: EdificeSide)
      : Board = b.copy(ready = b.ready.updateCurrent(current => current.copy(
    commonCards = current.commonCards.copy(edificeDeck =
      current.commonCards.edificeDeck.filterNot(_.value == edifice)),
    map = current.map.copy(sites = current.map.sites.updated(site,
      current.map.sites(site).copy(denizens = current.map.sites(site).denizens
        :+ EdificeState(EdificeId(edifice), side, Tokens.empty)))))))

  def withSecrets(b: Board, faceUp: Int): Board = replacePlayer(b, b.actor)(p =>
    p.copy(board = p.board.copy(faceUpSecrets = faceUp)))

```

Create `src/test/scala/oathdigital/gameplay/powers/campaign/BattleHonorsSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Battle Honors: a free plan for either side that gains two favor from the Order
  * bank if its user wins, as much as the bank holds.
  */
class BattleHonorsSuite extends munit.FunSuite {
  private val card = cardWith("denizen.battle-honors")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))

  private def orderBank(state: OathState): Int =
    ready(state).banks.favor.getOrElse(Suit.Order, 0)

  private def favor(state: OathState, who: PlayerId): Int =
    player(state, who).board.favor

  private def attacking: Board = withAdviser(board(), card, Orientation.FaceUp)

  private def defending: Board = {
    val base = againstPlayer(board())
    withAdviserFor(base, base.other, card, Orientation.FaceUp)
  }

  test("an attacker that wins gains two favor from the Order bank") {
    val b = attacking
    val bank = orderBank(asState(b))
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(true))
    assertEquals(favor(done.state, b.actor), favor(asState(b), b.actor) + 2)
    assertEquals(orderBank(done.state), bank - 2)
  }

  test("an attacker that loses gains nothing") {
    val b = attacking
    val done = commit(rules(losing), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(favor(done.state, b.actor), favor(asState(b), b.actor))
  }

  test("choosing it costs nothing and changes no dice") {
    val b = attacking
    val run = commit(rules(winning), b, 4)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(!picked.since(run).exists(op => op.isInstanceOf[PayCost] ||
      op.isInstanceOf[ModifyDicePool]))
  }

  test("a defender that wins gains two favor, and one that loses gains nothing") {
    val b = defending
    val won = commit(rules(losing), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    assertEquals(ready(won.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assertEquals(favor(won.state, b.other), favor(asState(b), b.other) + 2)
    val lost = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    assertEquals(favor(lost.state, b.other), favor(asState(b), b.other))
  }

  test("an Order bank with one favor gives one") {
    val b0 = attacking
    val b = b0.copy(ready = b0.ready.copy(banks = b0.ready.banks.copy(
      favor = b0.ready.banks.favor.updated(Suit.Order, 1))))
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(favor(done.state, b.actor), favor(asState(b), b.actor) + 1)
    assertEquals(orderBank(done.state), 0)
  }

  private def banditHolds: Board = {
    val two = board(extras = 1)
    withSiteCard(two, two.extras.head, card)
  }

  test("a bandit defender that wins gains two favor, settled into the shared bank, without choosing") {
    val b = banditHolds
    val bank = orderBank(asState(b))
    val run = commit(rules(losing), b, 2)
    // It applied the free plan by itself, so nothing was asked of the attacker.
    assertEquals(run.continue, awaits(b.actor, CampaignIds.sacrifice))
    val done = run.finish
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assertEquals(orderBank(done.state), bank - 2)
    assertEquals(favor(done.state, b.actor), favor(asState(b), b.actor))
  }

  test("a bandit defender that loses gains nothing") {
    val b = banditHolds
    val bank = orderBank(asState(b))
    val done = commit(rules(winning), b, 4).finish
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(true))
    assertEquals(orderBank(done.state), bank)
  }

  test("the gain is best-effort for bandits too") {
    val b0 = banditHolds
    val b = b0.copy(ready = b0.ready.copy(banks = b0.ready.banks.copy(
      favor = b0.ready.banks.favor.updated(Suit.Order, 1))))
    val done = commit(rules(losing), b, 2).finish
    assertEquals(orderBank(done.state), 0)
  }

  private def asState(b: Board): OathState = OathState.Ready(b.ready)
}
```

Create `src/test/scala/oathdigital/gameplay/powers/campaign/FearsomeShieldSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Fearsome Shield: a defender burns two faceup secrets for two more defense
  * dice, and nothing is placed on the relic.
  */
class FearsomeShieldSuite extends munit.FunSuite {
  private val relic = relicWith("relic.fearsome-shield")
  private val ref: DecisionOptionRef = DecisionOptionRef.Relic(RelicId(relic))

  private def defending(secrets: Int): Board = {
    val base = againstPlayer(board())
    replacePlayer(withRelicFor(base, base.other, relic), base.other)(p =>
      p.copy(board = p.board.copy(faceUpSecrets = secrets, faceDownSecrets = 0)))
  }

  private def secretsOf(state: OathState, who: PlayerId): (Int, Int) = {
    val held = player(state, who).board
    held.faceUpSecrets -> held.faceDownSecrets
  }

  test("a defender burns two secrets and adds two defense dice") {
    val b = defending(3)
    val run = commit(rules(losing), b, 4)
    assertEquals(run.continue, awaits(b.other, CampaignIds.defenderPlan))
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref)
    assertEquals(picked.since(run).size, 2)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.defensePool, 2)))
    assert(picked.ops.contains(PayCost(b.other, Location.OnCard(RelicId(relic)),
      Cost(secretBurnt = 2), intoOccupied = true)))
    assertEquals(secretsOf(picked.state, b.other), (1, 0))
    // Nothing rests on the relic.
    assertEquals(player(picked.state, b.other).relics.map(_.tokens),
      Vector(Tokens.empty))
  }

  test("with fewer than two faceup secrets the defender cannot pay, and the plan is not offered") {
    val b = defending(1)
    val run = commit(rules(losing), b, 4)
    // Only the title's plan is left to choose.
    assertEquals(run.continue, awaits(b.other, CampaignIds.defenderPlan))
    val done = run.finish
    assertEquals(secretsOf(done.state, b.other), (1, 0))
    assert(!done.ops.exists(_.isInstanceOf[PayCost]))
  }

  test("the relic must be faceup, and it is not the attacker's plan") {
    val base = againstPlayer(board())
    val down = replacePlayer(withRelicFor(base, base.other, relic), base.other)(
      p => p.copy(relics = p.relics.map(_.copy(orientation = Orientation.FaceDown)),
        board = p.board.copy(faceUpSecrets = 3)))
    assertEquals(commit(rules(losing), down, 4).ops.count(_.isInstanceOf[PayCost]), 0)
    val attacker = replacePlayer(withRelic(board(), relic), board().actor)(p =>
      p.copy(board = p.board.copy(faceUpSecrets = 3)))
    assertEquals(commit(rules(losing), attacker, 2).continue,
      awaits(attacker.actor, CampaignIds.sacrifice))
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/campaign/MercenariesSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Mercenaries: a favor placed for three more attack dice, or three fewer for the
  * attacker when it defends, and gone when its user is defeated.
  */
class MercenariesSuite extends munit.FunSuite {
  private val card = cardWith("denizen.mercenaries")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)

  private def funded(b: Board, who: PlayerId, favor: Int): Board =
    replacePlayer(b, who)(p => p.copy(board = p.board.copy(favor = favor)))

  private def discarded(state: OathState): Boolean = ready(state).game.current
    .commonCards.regionalDiscards.values.exists(_.contains(id))

  private def attackerBoard: Board = {
    val base = board()
    funded(withAdviser(base, card, Orientation.FaceUp), base.actor, 2)
  }

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    player(state, who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  test("an attacker pays a favor onto the card and adds three attack dice") {
    val b = attackerBoard
    val run = commit(rules(winning), b, 0)
    assertEquals(run.continue, awaits(b.actor, CampaignIds.attackerPlan))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assertEquals(adviserTokens(picked.state, b.actor), Some(Tokens(1, 0)))
    assertEquals(player(picked.state, b.actor).board.favor, 1)
  }

  test("an attacker that wins keeps Mercenaries and its favor") {
    val b = attackerBoard
    val done = commit(rules(winning), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(true))
    assertEquals(adviserTokens(done.state, b.actor), Some(Tokens(1, 0)))
    assert(!discarded(done.state))
  }

  test("an attacker that is defeated discards Mercenaries, and its favor returns to the bank") {
    val b = attackerBoard
    val before = b.ready.banks.favor.getOrElse(Suit.Discord, 0)
    val done = commit(rules(losing), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assertEquals(adviserTokens(done.state, b.actor), None)
    assert(discarded(done.state))
    assertEquals(ready(done.state).banks.favor.getOrElse(Suit.Discord, 0),
      before + 1)
    assertEquals(player(done.state, b.actor).board.favor, 1)
  }

  private def pile(state: OathState, region: Region): Vector[WorldCardId] =
    ready(state).game.current.commonCards.regionalDiscards.getOrElse(region,
      Vector.empty)

  test("an adviser is discarded to the region after the region of its holder's pawn") {
    val b = attackerBoard
    val own = b.ready.game.current.map.regionOf(b.origin).get
    val done = commit(rules(losing), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assert(pile(done.state, CardPlay.nextRegion(own)).contains(id))
  }

  test("a card at a site the attacker rules is discarded from the site, to the region after the site's own") {
    val base = board()
    val current = base.ready.game.current
    val pawnRegion = current.map.regionOf(base.origin).get
    val ruled = current.map.inPlay.find(site =>
      current.map.regionOf(site).exists(_ != pawnRegion)).get
    val siteRegion = current.map.regionOf(ruled).get
    val b = funded(withSiteCard(actorRules(base, ruled), ruled, card), base.actor, 2)
    val done = commit(rules(losing), b, 0).pick(b.actor, CampaignIds.attackerPlan,
      ref).finish
    assert(discarded(done.state))
    assertEquals(ready(done.state).game.current.map.sites(ruled).denizens,
      Vector.empty)
    assert(pile(done.state, CardPlay.nextRegion(siteRegion)).contains(id))
    assert(!pile(done.state, CardPlay.nextRegion(pawnRegion)).contains(id))
  }

  test("an attacker with no favor to place is not offered Mercenaries") {
    val b = funded(attackerBoard, attackerBoard.actor, 0)
    assertEquals(commit(rules(winning), b, 2).continue,
      awaits(b.actor, CampaignIds.sacrifice))
  }

  private def defending(warbands: Int = 8): Board = {
    val base = againstPlayer(board(warbands = warbands))
    funded(withAdviserFor(base, base.other, card, Orientation.FaceUp),
      base.other, 2)
  }

  test("a defender pays at once and takes away the dice the attacker would roll, down to none") {
    val b = defending(warbands = 2)
    val run = commit(rules(winning), b, 2)
    assertEquals(run.continue, awaits(b.other, CampaignIds.defenderPlan))
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref)
    // The pool held two dice, so two are taken and not three.
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))
    assertEquals(adviserTokens(picked.state, b.other), Some(Tokens.empty))
    assertEquals(player(picked.state, b.other).board.favor, 1)
    assertEquals(ready(picked.state).banks.favor.getOrElse(Suit.Discord, 0),
      ready(run.state).banks.favor.getOrElse(Suit.Discord, 0) + 1)
  }

  test("a defender that wins keeps Mercenaries") {
    val b = defending(warbands = 2)
    val done = commit(rules(winning), b, 2)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assert(adviserTokens(done.state, b.other).nonEmpty)
    assert(!discarded(done.state))
  }

  test("a defender that is defeated discards Mercenaries") {
    val b = defending()
    val done = commit(rules(winning), b, 8)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(true))
    assertEquals(adviserTokens(done.state, b.other), None)
    assert(discarded(done.state))
  }

  test("a bandit defender never uses Mercenaries, which costs a favor") {
    val two = board(extras = 1)
    val b = funded(withSiteCard(two, two.extras.head, card), two.actor, 5)
    val run = commit(rules(winning), b, 2)
    assertEquals(run.continue, awaits(b.actor, CampaignIds.sacrifice))
    assert(!run.ops.exists(_.isInstanceOf[PayCost]))
  }

  test("the card is found in the catalog and registered once") {
    assertEquals(SimplePlans.forCatalog(catalog).count(_.id == Mercenaries.id), 1)
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignProcedure}
import oathdigital.gameplay.CampaignFixture.Board
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerDice, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer._
import oathdigital.model.OathState.Ready

/** Drives a Campaign through the rules with the production battle plans, for the
  * suites that test one plan through a whole Campaign. Every attack die is a sword
  * (`winning`) or half a sword (`losing`) and every defense die is blank, whatever
  * the pool holds, so a suite chooses who wins by the force it commits: a Conquest
  * against two bandit or player warbands is won by four swords and lost by four
  * hollow ones.
  */
object PlanDriver {
  private def dice(attack: AttackDieFace): WalkerDice = (kind, count) =>
    Right(kind match {
      case DiceKind.Attack => Vector.fill(count)(attack: DieFace)
      case DiceKind.Defense => Vector.fill(count)(DefenseDieFace.Blank: DieFace)
    })

  val winning: WalkerDice = dice(AttackDieFace.OneSword)
  val losing: WalkerDice = dice(AttackDieFace.HollowSword)

  def ready(state: OathState): ReadyGame = state match {
    case Ready(value) => value
    case other => throw new IllegalStateException(s"not a ready game: $other")
  }

  def player(state: OathState, id: PlayerId): PlayerState =
    ready(state).game.current.players.find(_.player == id).get

  def awaits(who: PlayerId, id: String): OathContinue =
    OathContinue.AwaitingCampaignDecision(who, DecisionId(id))

  /** A Campaign in progress: the transition it reached and every event so far. */
  final case class Run(game: OathRules, transition: OathTransition,
      events: Vector[OathEvent]) {
    def state: OathState = transition.state
    def continue: OathContinue = transition.continue

    /** The operations recorded so far, in order. */
    def ops: Vector[CoreOperation] = events.collect {
      case step: WalkerStepRecorded => step.ops }.flatten

    /** The operations recorded since `earlier`. */
    def since(earlier: Run): Vector[CoreOperation] = ops.drop(earlier.ops.size)

    def answer(who: PlayerId, id: String, answer: DecisionAnswer): Run =
      game.resolveWalker(state, who, id, answer).fold(
        error => throw new IllegalStateException(s"$id was refused: $error"),
        next => Run(game, next, events ++ next.events))

    def pick(who: PlayerId, id: String, ref: DecisionOptionRef): Run =
      answer(who, id, ChooseOneAnswer(ref))

    /** The options the parked decision offers, as `actor`'s Campaign builds it. */
    def offered(actor: PlayerId): Vector[DecisionOptionRef] = {
      val current = ready(state)
      val tree = CampaignProcedure.rebuild(catalog, current, actor, Vector.empty)
        .toOption.get
      ProcedureWalker.openDecisions(current, tree,
        current.game.current.walkerPending.get,
        WalkerPowerCatalog.default(catalog)).headOption.map(_.query).collect {
        case DecisionQuery.ChooseOne(options, _) => options.map(_.ref)
      }.getOrElse(Vector.empty)
    }

    def refused(who: PlayerId, id: String, answer: DecisionAnswer)
        : Option[OathViolation] =
      game.resolveWalker(state, who, id, answer).left.toOption

    /** Finishes every plan window, sacrifices nothing and places nothing, until
      * the Campaign ends or asks something else.
      */
    def finish: Run = continue match {
      case OathContinue.AwaitingCampaignDecision(who, DecisionId(id)) => id match {
        case CampaignIds.attackerPlan | CampaignIds.defenderPlan =>
          answer(who, id, ChooseOneAnswer(CampaignIds.finish)).finish
        case CampaignIds.sacrifice | CampaignIds.placement =>
          answer(who, id, ChooseAmountAnswer(0)).finish
        case _ => this
      }
      case _ => this
    }
  }

  /** Starts a Campaign, chooses a Raid when asked and `raid` is set, answers the
    * optional targets (when asked) and the force.
    */
  def commit(game: OathRules, b: Board, force: Int,
      targets: Vector[DecisionOptionRef] = Vector.empty,
      raid: Boolean = false): Run = {
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor)
      .fold(error => throw new IllegalStateException(s"no start: $error"),
        identity)
    val run = Run(game, started, started.events)
    val kind =
      if (run.continue == awaits(b.actor, CampaignIds.kind)) run.pick(b.actor,
        CampaignIds.kind, DecisionOptionRef.Button(if (raid) "raid" else "conquest"))
      else run
    val asked =
      if (kind.continue == awaits(b.actor, CampaignIds.targets))
        kind.answer(b.actor, CampaignIds.targets, ChooseManyAnswer(targets))
      else kind
    asked.answer(b.actor, CampaignIds.force, ChooseAmountAnswer(force))
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/campaign/RampartSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** The Rampart edifice: two more defense dice for its ruler when their pawn is
  * at its site or the site is targeted (intact), one more when it is targeted
  * (ruined). It is a plan of the site's ruler, and a bandit ruler applies it
  * without choosing.
  */
class RampartSuite extends munit.FunSuite {
  private val edifice = "E20"
  private val defensePool = CampaignIds.defensePool

  /** The other player rules the origin, which the Campaign targets. */
  private def targeted(face: EdificeSide): Board = {
    val base = againstPlayer(board())
    withEdifice(base, base.origin, edifice, face)
  }

  /** The other player also rules a second site, which is not targeted. */
  private def untargeted(face: EdificeSide, pawnThere: Boolean): Board = {
    val two = againstPlayer(board(extras = 1))
    val extra = two.extras.head
    val lineage = two.player(two.other).lineage
    val ruled = two.copy(ready = two.ready.updateCurrent(current =>
      current.copy(map = current.map.copy(sites = current.map.sites.updated(
        extra, current.map.sites(extra).copy(forces = SiteForces.Occupied(
          ForceKind.Exile(lineage), 2)))))))
    val staged = withEdifice(ruled, extra, edifice, face)
    if (!pawnThere) staged
    else staged.copy(ready = staged.ready.updateCurrent(current => current.copy(
      players = current.players.map(p =>
        if (p.player == staged.other) p.copy(pawnSite = Some(extra)) else p))))
  }

  private val edificeRef: DecisionOptionRef =
    DecisionOptionRef.Edifice(EdificeId(edifice))

  test("Towering Rampart at a targeted site adds two defense dice") {
    val b = targeted(EdificeSide.Intact)
    val run = commit(rules(losing), b, 4)
    assertEquals(run.continue, awaits(b.other, CampaignIds.defenderPlan))
    val picked = run.pick(b.other, CampaignIds.defenderPlan, edificeRef)
    assertEquals(picked.since(run), Vector[CoreOperation](
      ModifyDicePool(defensePool, 2)))
  }

  test("Towering Rampart at a site that is neither targeted nor the ruler's pawn's is not offered") {
    val b = untargeted(EdificeSide.Intact, pawnThere = false)
    val run = commit(rules(losing), b, 4)
    // Only the title's plan is left to choose.
    assertEquals(run.offered(b.actor), Vector[DecisionOptionRef](
      DecisionOptionRef.Button("title"), CampaignIds.finish))
  }

  test("Towering Rampart is offered where the ruler's pawn stands, though the site is not targeted") {
    val b = untargeted(EdificeSide.Intact, pawnThere = true)
    val run = commit(rules(losing), b, 4)
    val picked = run.pick(b.other, CampaignIds.defenderPlan, edificeRef)
    assertEquals(picked.since(run), Vector[CoreOperation](
      ModifyDicePool(defensePool, 2)))
  }

  test("Cracked Rampart adds one defense die at a targeted site") {
    val b = targeted(EdificeSide.Ruined)
    val run = commit(rules(losing), b, 4)
    val picked = run.pick(b.other, CampaignIds.defenderPlan, edificeRef)
    assertEquals(picked.since(run), Vector[CoreOperation](
      ModifyDicePool(defensePool, 1)))
  }

  test("Cracked Rampart is not offered where the site is not targeted, whatever the pawn does") {
    val b = untargeted(EdificeSide.Ruined, pawnThere = true)
    assertEquals(commit(rules(losing), b, 4).offered(b.actor),
      Vector[DecisionOptionRef](DecisionOptionRef.Button("title"),
        CampaignIds.finish))
  }

  test("the intact face is the Towering plan and the ruined face the Cracked plan, never both") {
    val b = targeted(EdificeSide.Intact)
    val picked = commit(rules(losing), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, edificeRef)
    assertEquals(picked.offered(b.actor), Vector[DecisionOptionRef](
      DecisionOptionRef.Button("title"), CampaignIds.finish))
  }

  test("a bandit ruler applies it without choosing, at a targeted site") {
    val base = board()
    val b = withEdifice(base, base.origin, edifice, EdificeSide.Intact)
    def defensePoolChanges(run: Run): Int = run.ops.count {
      case ModifyDicePool(`defensePool`, _, _) => true
      case _ => false
    }
    val before = commit(rules(losing), board(), 2)
    val run = commit(rules(losing), b, 2)
    assertEquals(run.continue, awaits(b.actor, CampaignIds.sacrifice))
    assertEquals(defensePoolChanges(run), defensePoolChanges(before) + 1)
  }

  test("the two faces are two powers of one card, registered once each") {
    assertEquals(ToweringRampart.id.value, "edifice.e20.intact")
    assertEquals(CrackedRampart.id.value, "edifice.e20.ruined")
    val ids = SimplePlans.forCatalog(oathdigital.gameplay.setup
      .FirstGameSetupFixture.catalog).map(_.id)
    assertEquals(ids.count(id => id == ToweringRampart.id ||
      id == CrackedRampart.id), 2)
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/campaign/WrestlersSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Wrestlers: a defender sacrifices a warband from its force for one more
  * defense die, and the force the defense is scored with is one lower.
  */
class WrestlersSuite extends munit.FunSuite {
  private val card = cardWith("denizen.wrestlers")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))

  private def conquest: Board = {
    val base = againstPlayer(board())
    withAdviserFor(base, base.other, card, Orientation.FaceUp)
  }

  private def raid(defenderWarbands: Int): Board = {
    val base = withEnemyAtOrigin(board(warbands = 4))
    replacePlayer(withAdviserFor(base, base.other, card, Orientation.FaceUp),
      base.other)(p => p.copy(board = p.board.copy(warbands = defenderWarbands)))
  }

  private def siteForces(state: OathState, b: Board): SiteForces =
    ready(state).game.current.map.sites(b.origin).forces

  test("a Conquest defender sacrifices a warband at the target site for a defense die") {
    val b = conquest
    val run = commit(rules(losing), b, 4)
    assertEquals(run.continue, awaits(b.other, CampaignIds.defenderPlan))
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref)
    assertEquals(siteForces(picked.state, b),
      SiteForces.Occupied(ForceKind.Exile(b.player(b.other).lineage), 1))
    assertEquals(picked.since(run).map(_.getClass.getSimpleName),
      Vector("Sacrifice", "ModifyDicePool"))
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.defensePool, 1)))
  }

  test("the sacrificed warband lowers the recorded force by one") {
    val b = conquest
    val without = commit(rules(losing), b, 4).finish
    val with_ = commit(rules(losing), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    val score = (run: Run) => ready(run.state).game.current.lastCampaignResult
      .get.defenseScore
    assertEquals(score(without), 2)
    assertEquals(score(with_), 1)
  }

  test("a Raid defender sacrifices from the board") {
    val b = raid(3)
    val run = commit(rules(losing), b, 2, raid = true)
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref)
    assertEquals(player(picked.state, b.other).board.warbands, 2)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.defensePool, 1)))
  }

  test("a defender with no warband in its force cannot pay, so the plan is not offered") {
    val b = raid(0)
    assertEquals(commit(rules(losing), b, 2, raid = true).continue,
      awaits(b.actor, CampaignIds.sacrifice))
  }

  test("it is a defender's plan only") {
    val base = board()
    val b = withAdviser(base, card, Orientation.FaceUp)
    assertEquals(commit(rules(losing), b, 2).continue,
      awaits(b.actor, CampaignIds.sacrifice))
  }

  test("a bandit defender never uses it, since it costs a warband") {
    val two = board(extras = 1)
    val b = withSiteCard(two, two.extras.head, card)
    val run = commit(rules(losing), b, 2)
    assertEquals(run.continue, awaits(b.actor, CampaignIds.sacrifice))
    assert(!run.ops.exists(_.isInstanceOf[Sacrifice]))
  }
}
```


- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.MercenariesSuite oathdigital.gameplay.powers.campaign.WrestlersSuite oathdigital.gameplay.powers.campaign.FearsomeShieldSuite oathdigital.gameplay.powers.campaign.RampartSuite oathdigital.gameplay.powers.campaign.BattleHonorsSuite"`
Expected: FAIL to compile, for example `not found: value SimplePlans`.

- [ ] **Step 3: Implement**

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, replace:

```scala
package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.campaign.{BattlePlans, VowOfPeaceContribution}
import oathdigital.gameplay.powers.economy.KnightsErrant
import oathdigital.gameplay.powers.cardplay.CardPlayTriggers
import oathdigital.gameplay.powers.recover.CatacombsContribution
```

with:

```scala
package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.campaign.{BattlePlans, SimplePlans, VowOfPeaceContribution}
import oathdigital.gameplay.powers.economy.KnightsErrant
import oathdigital.gameplay.powers.cardplay.CardPlayTriggers
import oathdigital.gameplay.powers.recover.CatacombsContribution
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, replace:

```scala
      TargetProtections.forCatalog(catalog) ++
      KnightsErrant.forCatalog(catalog).toVector ++
      BattlePlans.forCatalog(catalog) ++
      CardPlayTriggers.forCatalog(catalog) ++
      Dazzle.forCatalog(catalog) :+ TakeWealthLimit :+ ConspiracyWhenPlayed)
}
```

with:

```scala
      TargetProtections.forCatalog(catalog) ++
      KnightsErrant.forCatalog(catalog).toVector ++
      BattlePlans.forCatalog(catalog) ++
      SimplePlans.forCatalog(catalog) ++
      CardPlayTriggers.forCatalog(catalog) ++
      Dazzle.forCatalog(catalog) :+ TakeWealthLimit :+ ConspiracyWhenPlayed)
}
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/BattleHonors.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Battle Honors (card 2), a battle plan for either side: "If you're victorious,
  * gain 2 favor from the Order bank."
  *
  * Choosing it costs nothing and changes no dice. Once the Campaign has resolved,
  * its user gains two favor from the Order bank if they won: the attacker when the
  * attacker won, the defender when the defender did. The gain is best-effort, so an
  * Order bank with less favor gives what it holds. A bandit defender that wins
  * gains it too, settled into the shared bank, because bandits hold no board of
  * their own. It applies the plan by itself, since the plan is free.
  */
final case class BattleHonors private (cardId: DenizenId) extends BattlePlan {
  def id: PowerId = BattleHonors.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Battle Honors: gain 2 favor if victorious", Vector.empty, Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      if (!use.won.contains(true)) Vector.empty
      else Vector(use.user.fold[Operation](toBandits)(
        Gain.Favor(_, Suit.Order, BattleHonors.Favor)))))

  /** The favor a bandit defender gains: from the Order bank to the shared bank. */
  private def toBandits: Operation = Move(Piece.Favor(BattleHonors.Favor),
    PositionedLocation(Location.FavorBank(Suit.Order)),
    PositionedLocation(Location.SharedBank))
}

object BattleHonors {
  val id: PowerId = PowerId("denizen.battle-honors")
  val Favor: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[BattleHonors] =
    CatalogCards.denizen(catalog, id).map(new BattleHonors(_))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/CrackedRampart.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Cracked Rampart (edifice E20, ruined), a defender's battle plan: "+1 defense
  * die if this site is targeted."
  *
  * It is used by the ruler of the site the ruined edifice stands at, and only
  * when a Conquest targets that site. A Raid never targets a site.
  */
final case class CrackedRampart private (edificeId: EdificeId)
    extends BattlePlan {
  def id: PowerId = CrackedRampart.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Edifice(edificeId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.edifice(edificeId, EdificeSide.Ruined)
      .filter(source => context.targets(source.siteId))
      .map(source => CampaignPlanOffer(source,
        "Cracked Rampart: add 1 defense die", Vector.empty,
        Vector(CampaignPlanEffect.AddDefenseDice(1))))
}

object CrackedRampart {
  val id: PowerId = PowerId("edifice.e20.ruined")

  def forCatalog(catalog: ExecutableCatalog): Option[CrackedRampart] =
    CatalogCards.edifice(catalog, id).map(new CrackedRampart(_))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/FearsomeShield.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Fearsome Shield (relic R27), a defender's battle plan: "[secret-burnt]
  * [secret-burnt] +2 defense dice."
  *
  * The relic must be faceup in the defender's play area. Two faceup secrets are
  * burnt to the shared bank, and nothing is placed on the relic. A defender with
  * fewer than two faceup secrets cannot pay, and the plan is not offered.
  */
final case class FearsomeShield private (relicId: RelicId) extends BattlePlan {
  def id: PowerId = FearsomeShield.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Relic(relicId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.relic(relicId).map(source => CampaignPlanOffer(source,
      "Fearsome Shield: burn 2 secrets for 2 defense dice",
      Vector(CampaignPlanCost.SecretBurnt(2)),
      Vector(CampaignPlanEffect.AddDefenseDice(2))))
}

object FearsomeShield {
  val id: PowerId = PowerId("relic.fearsome-shield")

  def forCatalog(catalog: ExecutableCatalog): Option[FearsomeShield] =
    CatalogCards.relic(catalog, id).map(new FearsomeShield(_))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/Mercenaries.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Mercenaries (card 12), a battle plan for either side: "[favor] +-3 attack
  * dice. If you are defeated while using this power, discard Mercenaries."
  *
  * It costs a favor, placed onto the card. An attacker adds three dice to the
  * attack pool. A defender removes three from it, and the pool never goes below
  * zero. The sign is fixed by the side, so a player cannot choose the other one
  * (a deferred rule). When the user is defeated, the card is discarded by the
  * standard denizen discard once the Campaign has resolved, so its favor returns
  * to the bank.
  */
final case class Mercenaries private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan {
  def id: PowerId = Mercenaries.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map { source =>
      val (label, effect) = context.side match {
        case CampaignPlanSide.Attacker => "Mercenaries: add 3 attack dice" ->
          CampaignPlanEffect.AddAttackDice(Mercenaries.Dice)
        case CampaignPlanSide.Defender => "Mercenaries: remove 3 attack dice" ->
          CampaignPlanEffect.RemoveAttackDice(Mercenaries.Dice)
      }
      CampaignPlanOffer(source, label, Vector(CampaignPlanCost.Favor(1)),
        Vector(effect))
    }

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      if (!use.won.contains(false)) Vector.empty
      else use.user.toVector.map(PlanDiscard.denizen(catalog, _, cardId))))
}

object Mercenaries {
  val id: PowerId = PowerId("denizen.mercenaries")
  val Dice: Int = 3

  def forCatalog(catalog: ExecutableCatalog): Option[Mercenaries] =
    CatalogCards.denizen(catalog, id).map(new Mercenaries(_, catalog))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/PlanDiscard.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.operations.DiscardRestrictions
import oathdigital.model._

/** The standard discard of a denizen a battle plan used, once the plan is spent:
  * the card goes facedown to the discard pile of the region after the card's own
  * region, its favor returns to its suit's bank, and its secrets return to its
  * user facedown. The card is found where it stands now. A card at a site is in
  * the site's region. An adviser has no region of its own, so it takes the
  * region of its holder's pawn, as a card played from a hand does
  * (`CardPlay.nextRegion` is the one rule for the region after). Nothing happens
  * when the card is no longer in either place.
  *
  * Like every discard of a card in play, it attaches `DiscardRestrictions`.
  */
object PlanDiscard {
  def denizen(catalog: ExecutableCatalog, user: PlayerId, card: DenizenId)
      : Operation = BuildOps((ready, _) => operations(catalog, ready, user, card),
    restrictions = (_, _) => Vector(new DiscardRestrictions(catalog, user)))

  private def operations(catalog: ExecutableCatalog, ready: ReadyGame,
      user: PlayerId, card: DenizenId)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val current = ready.game.current
    val player = current.players.find(_.player == user)
    val pawnRegion = player.flatMap(_.pawnSite).flatMap(current.map.regionOf)
    val asAdviser = player.toVector.flatMap(_.advisers.collect {
      case held: DenizenState if held.id == card =>
        (PositionedLocation(Location.PlayArea(user)), held.tokens, pawnRegion)
    })
    val atSite = current.map.inPlay.flatMap(site => current.map.sites(site)
      .denizens.collect {
        case held: DenizenState if held.id == card =>
          (PositionedLocation(Location.Site(site)), held.tokens,
            current.map.regionOf(site))
      })
    (asAdviser ++ atSite).headOption match {
      case None => Right(Vector.empty)
      case Some((from, tokens, region)) => for {
        own <- region.toRight(OathViolation.PawnSiteMissing(user))
        suit <- catalog.suitOf(card).toRight(OathViolation.UnknownWorldCard(card))
      } yield Vector[CoreOperation](Discard.Denizen(card, from,
        CardPlay.nextRegion(own), suit, tokens.favor, tokens.secrets, user,
        required = true))
    }
  }
}
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans that change the dice, or pay after the Campaign, and need
  * nothing beyond the plan window: Mercenaries, Wrestlers, Fearsome Shield, the
  * two faces of the Rampart and Battle Honors, registered together. A plan whose
  * card is absent from `catalog` is omitted.
  */
object SimplePlans {
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Mercenaries.forCatalog(catalog).toVector ++
      Wrestlers.forCatalog(catalog).toVector ++
      FearsomeShield.forCatalog(catalog).toVector ++
      ToweringRampart.forCatalog(catalog).toVector ++
      CrackedRampart.forCatalog(catalog).toVector ++
      BattleHonors.forCatalog(catalog).toVector
}
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/ToweringRampart.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Towering Rampart (edifice E20, intact), a defender's battle plan: "+2 defense
  * dice if your pawn is at this site or this site is targeted."
  *
  * It is used by the ruler of the site the edifice stands at. A Raid targets no
  * site, so a Raid gets it only when the ruler's pawn is here. A bandit ruler
  * has no pawn, so it applies when the site is targeted, without choosing.
  */
final case class ToweringRampart private (edificeId: EdificeId)
    extends BattlePlan {
  def id: PowerId = ToweringRampart.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Edifice(edificeId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.edifice(edificeId, EdificeSide.Intact).filter(source =>
      context.pawnAt(source.siteId) || context.targets(source.siteId))
      .map(source => CampaignPlanOffer(source,
        "Towering Rampart: add 2 defense dice", Vector.empty,
        Vector(CampaignPlanEffect.AddDefenseDice(2))))
}

object ToweringRampart {
  val id: PowerId = PowerId("edifice.e20.intact")

  def forCatalog(catalog: ExecutableCatalog): Option[ToweringRampart] =
    CatalogCards.edifice(catalog, id).map(new ToweringRampart(_))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/Wrestlers.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Wrestlers (card 1), a defender's battle plan: "+1 defense die if you sacrifice
  * one warband in your force."
  *
  * The cost is a warband sacrifice: the defender's board in a Raid, or a warband
  * at a target site the defender rules in a Conquest (they choose which site when
  * several qualify). The sacrificed warband is gone before the defense is scored,
  * so it lowers the recorded force by one. A defender with no warband in their
  * force cannot pay, and the plan is not offered.
  */
final case class Wrestlers private (cardId: DenizenId) extends BattlePlan {
  def id: PowerId = Wrestlers.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Wrestlers: sacrifice a warband for 1 defense die",
      Vector(CampaignPlanCost.SacrificeWarband),
      Vector(CampaignPlanEffect.AddDefenseDice(1))))
}

object Wrestlers {
  val id: PowerId = PowerId("denizen.wrestlers")

  def forCatalog(catalog: ExecutableCatalog): Option[Wrestlers] =
    CatalogCards.denizen(catalog, id).map(new Wrestlers(_))
}
```


- [ ] **Step 4: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.MercenariesSuite oathdigital.gameplay.powers.campaign.WrestlersSuite oathdigital.gameplay.powers.campaign.FearsomeShieldSuite oathdigital.gameplay.powers.campaign.RampartSuite oathdigital.gameplay.powers.campaign.BattleHonorsSuite oathdigital.gameplay.DiscardRestrictionsCoverageSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS.

- [ ] **Step 5: Run the whole suite and the architecture check**

Run: `./sbtw test` and `python3 scripts/check-architecture.py`
Expected: PASS (1454 tests), and `architecture check passed`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: implement Mercenaries, Wrestlers, Fearsome Shield, the Ramparts and Battle Honors

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

- [ ] **Step 7: Record sub-slice 3c**

In `docs/superpowers/specs/2026-09-20-powers-design.md`, replace:

```markdown

> Status: design approved 2026-09-20. Slice 0 (E1 to E5), slice 1a, slice 1b, slice 1c, slice 1d and slice 2 (sub-slices 2a to 2f) are implemented; see the [Slice 0 plan](../plans/2026-09-20-powers-slice-0-foundations.md), the [slice 1a plan](../plans/2026-09-20-powers-slice-1a-when-played-and-simple-actions.md), the [slice 1b plan](../plans/2026-09-20-powers-slice-1b-dice-and-relic-draws.md) the [slice 1c plan](../plans/2026-09-20-powers-slice-1c-targets-and-information.md) the [slice 1d plan](../plans/2026-09-20-powers-slice-1d-movement.md) and the [slice 2 plan](../plans/2026-09-20-powers-slice-2-modifiers-restrictions-triggers.md) (all six sub-slices). Per-power rules are in [the rulings appendix](2026-09-20-powers-rulings.md). Extends the [procedure walker design](2026-09-05-procedure-walker-design.md) and follows the [Campaign port](2026-09-19-campaign-walker-design.md). Each slice below gets its own implementation plan, and slice 1 is split into four.

> Slice 3 (battle plans) is planned in four sub-slices: see its [plan](../plans/2026-09-20-powers-slice-3-battle-plans.md). Implemented so far: 3a and 3b.

## Goal and scope

```

with:

```markdown

> Status: design approved 2026-09-20. Slice 0 (E1 to E5), slice 1a, slice 1b, slice 1c, slice 1d and slice 2 (sub-slices 2a to 2f) are implemented; see the [Slice 0 plan](../plans/2026-09-20-powers-slice-0-foundations.md), the [slice 1a plan](../plans/2026-09-20-powers-slice-1a-when-played-and-simple-actions.md), the [slice 1b plan](../plans/2026-09-20-powers-slice-1b-dice-and-relic-draws.md) the [slice 1c plan](../plans/2026-09-20-powers-slice-1c-targets-and-information.md) the [slice 1d plan](../plans/2026-09-20-powers-slice-1d-movement.md) and the [slice 2 plan](../plans/2026-09-20-powers-slice-2-modifiers-restrictions-triggers.md) (all six sub-slices). Per-power rules are in [the rulings appendix](2026-09-20-powers-rulings.md). Extends the [procedure walker design](2026-09-05-procedure-walker-design.md) and follows the [Campaign port](2026-09-19-campaign-walker-design.md). Each slice below gets its own implementation plan, and slice 1 is split into four.

> Slice 3 (battle plans) is planned in four sub-slices: see its [plan](../plans/2026-09-20-powers-slice-3-battle-plans.md). Implemented so far: 3a to 3c.

## Goal and scope

```

In `docs/superpowers/specs/2026-09-20-powers-rulings.md`, replace:

```markdown

| Card | Side | Ruling |
| --- | --- | --- |
| 12 Mercenaries | either | Cost 1 favor placed. An attacker adds 3 attack dice. A defender removes 3 from the attacker's pool, minimum 0. If its user is defeated (`!attackerWins` for the attacker, `attackerWins` for the defender), it is discarded through the standard denizen discard. Player-chosen sign is deferred. |
| 1 Wrestlers | defender | Cost: sacrifice one warband from the defender's force, which is their board in a Raid or a warband at a target site they rule in a Conquest (chosen if several). It lowers the recorded force by 1. Effect: +1 defense die. |
| R27 Fearsome Shield | defender | Cost 2 secrets burnt. +2 defense dice. |
| E20 Towering Rampart | defender | +2 defense dice if the ruler's pawn is at this site or this site is a Conquest target. |
| E20 Cracked Rampart | defender | +1 defense die if this site is a Conquest target. A Raid never targets a site. |
| 25 Warning Signals | defender | A `Distribute.exactly` over the defender's board and every site they rule, total conserved, each ruled site's minimum 1. It applies when chosen, before the defender's force is recorded. Player defenders only. After the Campaign fully resolves it is discarded, unconditionally. |
| 2 Battle Honors | either | Chosen at the plan step. After the result, if its user won (the attacker when `attackerWins`, else the defender), gain 2 favor from the Order bank with `Gain.Favor`. |
| R01 Sticky Fire | either | If its user wins, a second prompt at `CampaignLosses`, owned by the winner, asks whether to kill all warbands in the enemy's force. Attacker wins a Conquest: the defender's half-return is cancelled. Attacker wins a Raid: every warband on the defender's board dies, not half. Defender wins: every warband on the attacker's board dies, committed or not. Then the winner gives the loser 1 favor if able, a non-required `Give`. Against bandits it burns the favor, as a `Give` to `Location.SharedBank`. |

Persistent modifier, not a plan:
```

with:

```markdown

| Card | Side | Ruling |
| --- | --- | --- |
| 12 Mercenaries | either | Cost 1 favor placed. An attacker adds 3 attack dice. A defender removes 3 from the attacker's pool, minimum 0. If its user is defeated (`!attackerWins` for the attacker, `attackerWins` for the defender), it is discarded through the standard denizen discard. Player-chosen sign is deferred. The discard follows the card: a card on a site goes to the region after the site's own region, an adviser to the region after the region of its holder's pawn (`CardPlay.nextRegion`). Implemented (slice 3c). |
| 1 Wrestlers | defender | Cost: sacrifice one warband from the defender's force, which is their board in a Raid or a warband at a target site they rule in a Conquest (chosen if several). It lowers the recorded force by 1. Effect: +1 defense die. Implemented (slice 3c). |
| R27 Fearsome Shield | defender | Cost 2 secrets burnt. +2 defense dice. Implemented (slice 3c). |
| E20 Towering Rampart | defender | +2 defense dice if the ruler's pawn is at this site or this site is a Conquest target. Implemented (slice 3c). |
| E20 Cracked Rampart | defender | +1 defense die if this site is a Conquest target. A Raid never targets a site. Implemented (slice 3c). |
| 25 Warning Signals | defender | A `Distribute.exactly` over the defender's board and every site they rule, total conserved, each ruled site's minimum 1. It applies when chosen, before the defender's force is recorded. Player defenders only. After the Campaign fully resolves it is discarded, unconditionally. |
| 2 Battle Honors | either | Chosen at the plan step. After the result, if its user won (the attacker when `attackerWins`, else the defender), gain 2 favor from the Order bank with `Gain.Favor`. A bandit defender that wins gains them too, settled into the shared bank as a move from the Order bank to `Location.SharedBank`, and applies the plan by itself because it is free. Implemented (slice 3c). |
| R01 Sticky Fire | either | If its user wins, a second prompt at `CampaignLosses`, owned by the winner, asks whether to kill all warbands in the enemy's force. Attacker wins a Conquest: the defender's half-return is cancelled. Attacker wins a Raid: every warband on the defender's board dies, not half. Defender wins: every warband on the attacker's board dies, committed or not. Then the winner gives the loser 1 favor if able, a non-required `Give`. Against bandits it burns the favor, as a `Give` to `Location.SharedBank`. |

Persistent modifier, not a plan:
```

In `docs/superpowers/specs/2026-09-20-powers-rulings.md`, replace:

```markdown

- **3a:** `CampaignResult.victorious` is now `attackerWins` in the model, the journal codec, the shared DTO and its codec, the result panel and the suites. It is true when the attacker prevailed and false when the defender did. The wire key changes with it, and journals are forward-only, so a game whose journal holds a recorded Campaign result cannot be read after this change.
- **3b:** every plan is a `BattlePlan` power declared as an `Offer` (where its card must stand, what it costs and does) and, when it acts later, a hook at a later window. The user must be the ruler of the source: the origin-site offer to a non-ruler is gone, and Brass Army no longer needs an empty relic. A defender's plan may carry a cost, paid at once. Costs are `Favor` and `Secret` (placed onto the card, which may be occupied), `FavorBurnt`, `SecretBurnt` and `SacrificeWarband` (a defender only: the board in a Raid, or a target site the defender rules in a Conquest, asking which when several). A plan that cannot be paid, with every power's added cost, is not offered, and a window with nothing to offer is skipped. The option states its price. A source is chosen once. A bandit defender applies every cost-free plan at a site Bandits rule that no power makes unpayable, pays nothing, and records what it applied in a pool marker (`campaign.plan-applied.<kind>.<id>`) that a later hook reads, because a bandit's plan is not an answer. A facedown adviser is revealed when it is chosen, and a card at a site is always faceup. Outriders scores the attack again without the skull cap, and Brass Army adds four dice to the pool but not to the force.

## Slice 4: banner faces

```

with:

```markdown

- **3a:** `CampaignResult.victorious` is now `attackerWins` in the model, the journal codec, the shared DTO and its codec, the result panel and the suites. It is true when the attacker prevailed and false when the defender did. The wire key changes with it, and journals are forward-only, so a game whose journal holds a recorded Campaign result cannot be read after this change.
- **3b:** every plan is a `BattlePlan` power declared as an `Offer` (where its card must stand, what it costs and does) and, when it acts later, a hook at a later window. The user must be the ruler of the source: the origin-site offer to a non-ruler is gone, and Brass Army no longer needs an empty relic. A defender's plan may carry a cost, paid at once. Costs are `Favor` and `Secret` (placed onto the card, which may be occupied), `FavorBurnt`, `SecretBurnt` and `SacrificeWarband` (a defender only: the board in a Raid, or a target site the defender rules in a Conquest, asking which when several). A plan that cannot be paid, with every power's added cost, is not offered, and a window with nothing to offer is skipped. The option states its price. A source is chosen once. A bandit defender applies every cost-free plan at a site Bandits rule that no power makes unpayable, pays nothing, and records what it applied in a pool marker (`campaign.plan-applied.<kind>.<id>`) that a later hook reads, because a bandit's plan is not an answer. A facedown adviser is revealed when it is chosen, and a card at a site is always faceup. Outriders scores the attack again without the skull cap, and Brass Army adds four dice to the pool but not to the force.
- **3c:** the six plans are `BattlePlan` powers registered through `SimplePlans`. Mercenaries costs a favor placed onto the card, adds three attack dice for an attacker and removes three for a defender (never below an empty pool), and when its user is defeated is discarded by the standard denizen discard once the Campaign has resolved: facedown to the discard pile of the region after the card's own region (the site's region for a card on a site, the region of its holder's pawn for an adviser), its favor returned to the Discord bank. The sign is fixed by the side. Wrestlers is a defender's plan whose cost is the sacrifice, so a defender with no warband in its force is not offered it. Fearsome Shield burns two faceup secrets and places nothing on the relic. The Rampart plans are used by the ruler of the site the edifice stands at, from either face: the intact face needs the ruler's pawn at the site or the site targeted, the ruined face needs the site targeted, and a bandit ruler applies it without choosing. Battle Honors is free, and its user gains the favor after the Campaign has resolved, best-effort. A bandit defender that wins gains it too, into the shared bank, having applied the plan by itself.

## Slice 4: banner faces

```


Run: `python3 scripts/check-markdown-links.py`
Expected: `Markdown link check passed`.

```bash
git add docs
git commit -m "docs: record slice 3c

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Sub-slice 3d: Plans that reach further

### Task 5: Sticky Fire

**Files:**
- Modify: `BattlePlan.scala`, `CampaignProcedure.scala`, `WalkerProcedureRegistry.scala`, `WalkerPowerCatalog.scala`.
- Create: `StickyFire.scala`, `PlanRules.scala`.
- Test: `StickyFireSuite.scala`; `CampaignFixture` (modified).

**Interfaces:**
- Consumes: Task 3's kit and `PlanUse`; `PowerAnswers.one`, `PlayerFacts.forceKind`.
- Produces: `BattlePlan.wrapping: Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]]`; `CampaignProcedure.decisionPrefix` and `isDecision`; `StickyFire`; `object PlanRules { def forCatalog(catalog): Vector[ContributingPower] }`.

Sticky Fire (R01) says: if you are victorious you may kill all the warbands in your enemy's force, and if you do you must give them a favor if able. The plan itself costs nothing and changes nothing when it is chosen. When its user wins, the losses step asks its user, before anything dies, whether to burn the enemy's force. A yes then does, after the ordinary losses have run:

- an attacker's Conquest: the warbands the victory would have returned to a player defender's board are killed again (what stands at the targets before the losses, less half), so the defender keeps none;
- an attacker's Raid: every warband left on the defender's board dies, not half;
- a defender's victory: every warband on the attacker's board dies, committed to the Campaign or not.

The winner then gives the loser one favor if able (a `Give`, which takes only what its giver holds), and against bandits gives it to the shared bank, which burns it.

The question has to come before the losses and needs the state as it stands before them, so it uses the kit's new `wrapping` hook: a `Transform` folded when the losses window is entered, which sees the same state on a resume, because the question is the first thing in the window and nothing has run. The question's id is under the Campaign's prefix, so the registry recognises it as a Campaign decision: the registry now recognises the prefix and no longer an exact set. `CampaignFixture.raidBoard` gave the defender the first relic in the catalog, which is Sticky Fire, so it now takes one that prints no plan.

- [ ] **Step 1: Write the tests**

In `src/test/scala/oathdigital/gameplay/CampaignFixture.scala`, replace:

```scala
    */
  def raidBoard(defenderWarbands: Int = 3): (Board, RelicId) = {
    val b = withEnemyAtOrigin(board(warbands = 4))
    val relic = RelicId(catalog.relics.head.id.value)
    val ready = b.ready.updateCurrent(current => current.copy(
      players = current.players.map(p =>
        if (p.player == b.other) p.copy(
```

with:

```scala
    */
  def raidBoard(defenderWarbands: Int = 3): (Board, RelicId) = {
    val b = withEnemyAtOrigin(board(warbands = 4))
    // A relic that prints no battle plan, so the defender is offered none.
    val plans = Set("relic.sticky-fire", "relic.fearsome-shield",
      "relic.brass-army.campaign", "relic.bag-of-siegeworks")
    val relic = RelicId(catalog.relics.find(_.handlers.forall(!plans(_))).get
      .id.value)
    val ready = b.ready.updateCurrent(current => current.copy(
      players = current.players.map(p =>
        if (p.player == b.other) p.copy(
```

Create `src/test/scala/oathdigital/gameplay/powers/campaign/StickyFireSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Sticky Fire: when its user wins, they may kill the whole of the enemy's force,
  * and if they do they give the loser a favor. The question is asked in the
  * losses, by the winner, before anything dies.
  */
class StickyFireSuite extends munit.FunSuite {
  private val relic = relicWith("relic.sticky-fire")
  private val ref: DecisionOptionRef = DecisionOptionRef.Relic(RelicId(relic))
  private val decision = StickyFire.decisionId

  private def favored(b: Board, who: PlayerId, favor: Int): Board =
    replacePlayer(b, who)(p => p.copy(board = p.board.copy(favor = favor)))

  private def warbands(state: OathState, who: PlayerId): Int =
    player(state, who).board.warbands

  private def favorOf(state: OathState, who: PlayerId): Int =
    player(state, who).board.favor

  // ---- the attacker wins a Conquest against a player ----------------------

  private def conquest: Board = {
    val base = againstPlayer(board())
    favored(withRelic(base, relic), base.actor, 2)
  }

  private def bank(state: OathState): Int =
    ready(state).banks.favor.values.sum

  test("the winner is asked before anything dies, and the defender keeps no returned warband if the attacker says yes") {
    val b = conquest
    val g = rules(winning)
    val before = warbands(OathState.Ready(b.ready), b.other)
    val asked = commit(g, b, 4).pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.other, CampaignIds.defenderPlan, CampaignIds.finish)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
    assertEquals(asked.continue, awaits(b.actor, decision))
    // The question is asked first: nobody has died yet.
    assertEquals(ready(asked.state).game.current.map.sites(b.origin).forces,
      SiteForces.Occupied(ForceKind.Exile(b.player(b.other).lineage), 2))
    val done = asked.pick(b.actor, decision, StickyFire.yes).finish
    assertEquals(warbands(done.state, b.other), before)
    // The winner gave the loser a favor.
    assertEquals(favorOf(done.state, b.actor), 1)
    assertEquals(favorOf(done.state, b.other),
      favorOf(OathState.Ready(b.ready), b.other) + 1)
  }

  test("if the attacker says no, the defender keeps the returned half and nothing is given") {
    val b = conquest
    val before = warbands(OathState.Ready(b.ready), b.other)
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.other, CampaignIds.defenderPlan, CampaignIds.finish)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
      .pick(b.actor, decision, StickyFire.no).finish
    assertEquals(warbands(done.state, b.other), before + 1)
    assertEquals(favorOf(done.state, b.actor), 2)
  }

  test("nothing is asked when its user loses, or when it was not chosen") {
    val b = conquest
    val lost = commit(rules(losing), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.other, CampaignIds.defenderPlan, CampaignIds.finish)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
    assertEquals(ready(lost.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assertNotEquals(lost.continue, awaits(b.actor, decision))
    assertNotEquals(lost.continue, awaits(b.other, decision))
    val unchosen = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, CampaignIds.finish)
      .pick(b.other, CampaignIds.defenderPlan, CampaignIds.finish)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
    assertNotEquals(unchosen.continue, awaits(b.actor, decision))
  }

  test("the favor is given only if the winner has one to give") {
    val b = favored(conquest, conquest.actor, 0)
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.other, CampaignIds.defenderPlan, CampaignIds.finish)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
      .pick(b.actor, decision, StickyFire.yes).finish
    assertEquals(favorOf(done.state, b.other),
      favorOf(OathState.Ready(b.ready), b.other))
  }

  // ---- the attacker wins a Raid -------------------------------------------

  test("in a Raid every warband on the defender's board dies, not half") {
    val base = withEnemyAtOrigin(board(warbands = 4))
    val b = favored(withRelic(replacePlayer(base, base.other)(p => p.copy(
      board = p.board.copy(warbands = 3))), relic), base.actor, 1)
    val g = rules(winning)
    val done = commit(g, b, 4, raid = true)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
      .pick(b.actor, decision, StickyFire.yes)
    assertEquals(warbands(done.state, b.other), 0)
    assertEquals(favorOf(done.state, b.actor), 0)
    // The Raid then burns half of the defender's favor, the given one included.
    val given = favorOf(OathState.Ready(b.ready), b.other) + 1
    assertEquals(favorOf(done.state, b.other), given - given / 2)
  }

  test("the same Raid without Sticky Fire kills half the board") {
    val base = withEnemyAtOrigin(board(warbands = 4))
    val b = replacePlayer(base, base.other)(p => p.copy(
      board = p.board.copy(warbands = 3)))
    val done = commit(rules(winning), b, 4, raid = true)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
    assertEquals(warbands(done.state, b.other), 2)
  }

  // ---- the defender wins --------------------------------------------------

  private def defending: Board = {
    val base = againstPlayer(board())
    favored(withRelicFor(base, base.other, relic), base.other, 2)
  }

  test("a defender that wins is asked, and every warband on the attacker's board dies, committed or not") {
    val b = defending
    val g = rules(losing)
    val asked = commit(g, b, 4).pick(b.other, CampaignIds.defenderPlan, ref)
      .pick(b.other, CampaignIds.defenderPlan, CampaignIds.finish)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
    assertEquals(ready(asked.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assertEquals(asked.continue, awaits(b.other, decision))
    val done = asked.pick(b.other, decision, StickyFire.yes).finish
    assertEquals(warbands(done.state, b.actor), 0)
    assertEquals(favorOf(done.state, b.other), 1)
    assertEquals(favorOf(done.state, b.actor),
      favorOf(OathState.Ready(b.ready), b.actor) + 1)
  }

  // ---- against bandits ----------------------------------------------------

  test("against bandits the favor is burnt instead of given") {
    val base = board()
    val b = favored(withRelic(base, relic), base.actor, 2)
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
      .pick(b.actor, decision, StickyFire.yes).finish
    assertEquals(favorOf(done.state, b.actor), 1)
    // The favor left every player's board without reaching a suit bank.
    assertEquals(bank(done.state), bank(OathState.Ready(b.ready)))
  }

  test("a player who does not hold the relic is never asked") {
    val base = againstPlayer(board())
    val done = commit(rules(winning), favored(base, base.actor, 2), 4)
      .pick(base.other, CampaignIds.defenderPlan, CampaignIds.finish).finish
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(true))
    assertEquals(done.ops.count(_.isInstanceOf[Give]), 0)
  }
}
```


- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.StickyFireSuite"`
Expected: FAIL to compile, for example `not found: value StickyFire`.

- [ ] **Step 3: Implement**

In `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala`, replace:

```scala
  */
object CampaignProcedure {
  val decisionIds: Set[String] = CampaignIds.all

  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
```

with:

```scala
  */
object CampaignProcedure {
  val decisionIds: Set[String] = CampaignIds.all

  /** Every decision a Campaign asks starts with this, whether the engine or a
    * power asks it, so a parked one is always a Campaign decision.
    */
  val decisionPrefix: String = "campaign."

  def isDecision(decisionId: String): Boolean =
    decisionId.startsWith(decisionPrefix)

  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, replace:

```scala
package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.campaign.{BattlePlans, SimplePlans, VowOfPeaceContribution}
import oathdigital.gameplay.powers.economy.KnightsErrant
import oathdigital.gameplay.powers.cardplay.CardPlayTriggers
import oathdigital.gameplay.powers.recover.CatacombsContribution
```

with:

```scala
package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.campaign.{BattlePlans, PlanRules, SimplePlans, VowOfPeaceContribution}
import oathdigital.gameplay.powers.economy.KnightsErrant
import oathdigital.gameplay.powers.cardplay.CardPlayTriggers
import oathdigital.gameplay.powers.recover.CatacombsContribution
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, replace:

```scala
      TargetProtections.forCatalog(catalog) ++
      KnightsErrant.forCatalog(catalog).toVector ++
      BattlePlans.forCatalog(catalog) ++
      SimplePlans.forCatalog(catalog) ++
      CardPlayTriggers.forCatalog(catalog) ++
      Dazzle.forCatalog(catalog) :+ TakeWealthLimit :+ ConspiracyWhenPlayed)
```

with:

```scala
      TargetProtections.forCatalog(catalog) ++
      KnightsErrant.forCatalog(catalog).toVector ++
      BattlePlans.forCatalog(catalog) ++
      PlanRules.forCatalog(catalog) ++
      SimplePlans.forCatalog(catalog) ++
      CardPlayTriggers.forCatalog(catalog) ++
      Dazzle.forCatalog(catalog) :+ TakeWealthLimit :+ ConspiracyWhenPlayed)
```

In `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`, replace:

```scala
  *    (Outriders ignores the skulls when the attack is scored). It runs only when
  *    the plan was chosen, and its operations are appended to the window's
  *    children, so a plan that must run last is registered at the end of the
  *    Campaign, which is `CampaignActionEligibility`, the root.
  *  - `cardRef` is how the plan's source is named in a decision, which is how a
  *    later window learns the plan was chosen.
  */
```

with:

```scala
  *    (Outriders ignores the skulls when the attack is scored). It runs only when
  *    the plan was chosen, and its operations are appended to the window's
  *    children, so a plan that must run last is registered at the end of the
  *    Campaign, which is `CampaignActionEligibility`, the root. `wrapping` is
  *    the same for a plan that must also add before the window's own children
  *    (Sticky Fire asks its question before the losses run).
  *  - `cardRef` is how the plan's source is named in a decision, which is how a
  *    later window learns the plan was chosen.
  */
```

In `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`, replace:

```scala
  def plan(context: PlanContext): Option[CampaignPlanOffer]
  /** What a used plan adds at a later window. */
  def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map.empty

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  final override def resolution: PowerResolution = PowerResolution.Automatic
```

with:

```scala
  def plan(context: PlanContext): Option[CampaignPlanOffer]
  /** What a used plan adds at a later window. */
  def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map.empty
  /** What a used plan does to a later window's children, as the window is
    * folded: it may add before them as well as after. It reads the state and
    * answers the fold is made with, which are the same on every resume of the
    * window, so it must not read what the window's own children change.
    */
  def wrapping: Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]] =
    Map.empty

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  final override def resolution: PowerResolution = PowerResolution.Automatic
```

In `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`, replace:

```scala
          PlanUse.chosen(ready, pending, ctx.activePlayer, cardRef, sides,
            BattlePlan.outcomeKnown(window)).fold(Vector.empty[Operation])(build))))
    }
    offers ++ afterwards
  }
}

```

with:

```scala
          PlanUse.chosen(ready, pending, ctx.activePlayer, cardRef, sides,
            BattlePlan.outcomeKnown(window)).fold(Vector.empty[Operation])(build))))
    }
    val around: Map[PowerWindow, Vector[Contribution]] = wrapping.map {
      case (window, wrap) => window -> Vector[Contribution](Transform(
        (ctx, children) => PlanUse.chosen(ctx.state, PendingTree(ctx.nodePath,
          ctx.answered), ctx.activePlayer, cardRef, sides,
          BattlePlan.outcomeKnown(window)).fold(children)(wrap(_, children))))
    }
    (offers.keySet ++ afterwards.keySet ++ around.keySet).map(window =>
      window -> (offers.getOrElse(window, Vector.empty) ++
        afterwards.getOrElse(window, Vector.empty) ++
        around.getOrElse(window, Vector.empty))).toMap
  }
}

```

In `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`, replace:

```scala
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.MusterModifierSelection),
      continuationFor = (decisionId, actor, decision) =>
        if (CampaignProcedure.decisionIds.contains(decisionId))
          Some(OathContinue.AwaitingCampaignDecision(actor, decision))
        else Option.when(decisionId.startsWith(MusterProcedure.decisionPrefix))(
          OathContinue.AwaitingEconomyDecision(actor, decision)),
```

with:

```scala
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.MusterModifierSelection),
      continuationFor = (decisionId, actor, decision) =>
        if (CampaignProcedure.isDecision(decisionId))
          Some(OathContinue.AwaitingCampaignDecision(actor, decision))
        else Option.when(decisionId.startsWith(MusterProcedure.decisionPrefix))(
          OathContinue.AwaitingEconomyDecision(actor, decision)),
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`, replace:

```scala
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.CampaignModifierSelection),
      continuationFor = (decisionId, actor, decision) =>
        Option.when(CampaignProcedure.decisionIds.contains(decisionId))(
          OathContinue.AwaitingCampaignDecision(actor, decision)),
      build = CampaignProcedure.build,
      rebuild = CampaignProcedure.rebuild),
```

with:

```scala
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.CampaignModifierSelection),
      continuationFor = (decisionId, actor, decision) =>
        Option.when(CampaignProcedure.isDecision(decisionId))(
          OathContinue.AwaitingCampaignDecision(actor, decision)),
      build = CampaignProcedure.build,
      rebuild = CampaignProcedure.rebuild),
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans that reach beyond the plan window, registered together:
  * Sticky Fire, which asks a question in the losses. A power whose card is absent
  * from `catalog` is omitted.
  */
object PlanRules {
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    StickyFire.forCatalog(catalog).toVector
}
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/StickyFire.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.CampaignProcedure
import oathdigital.gameplay.powers.{CatalogCards, PlayerFacts, PowerAnswers}
import oathdigital.model._

/** Sticky Fire (relic R01), a battle plan for either side: "If you're victorious,
  * you may kill all the warbands in your enemy's force. If you do, you must give
  * them [favor] if able."
  *
  * Choosing it costs nothing. When its user wins, the losses step asks them,
  * before anything dies, whether to burn the enemy's force. If they do:
  *
  *  - after a Conquest won by the attacker, the defender keeps none of the
  *    warbands the victory would have returned to their board;
  *  - after a Raid won by the attacker, every warband left on the defender's
  *    board dies, not half;
  *  - after a defense won by the defender, every warband on the attacker's board
  *    dies, committed to the Campaign or not.
  *
  * Then the winner gives the loser a favor if they can. A `Give` takes only what
  * its giver holds, and against bandits the favor is given to the shared bank,
  * which burns it.
  */
final case class StickyFire private (relicId: RelicId) extends BattlePlan {
  def id: PowerId = StickyFire.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Relic(relicId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.relic(relicId).map(source => CampaignPlanOffer(source,
      "Sticky Fire: kill the enemy's force if victorious", Vector.empty,
      Vector.empty))

  override def wrapping
      : Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]] = Map(
    PowerWindow.CampaignLosses -> ((use, losses) => (for {
      user <- use.user
      result <- use.result
      if use.won.contains(true)
    } yield ask(user) +: (losses :+ burn(use, user, result))).getOrElse(losses)))

  private def ask(user: PlayerId): Operation = Decide(StickyFire.decisionId, user,
    DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(StickyFire.yes, "Kill the warbands in the enemy's force"),
      DecisionOption.Button(StickyFire.no, "Do not")),
      heading = Some("Sticky Fire: kill all the warbands in your enemy's force?")))

  /** The killing and the favor, when the user said yes. */
  private def burn(use: PlanUse, user: PlayerId, result: CampaignResult)
      : Operation = {
    val returned = returnedToDefender(use.ready, use.side, result)
    BuildOps((ready, pending) =>
      if (!PowerAnswers.one(pending, StickyFire.decisionId).contains(StickyFire.yes))
        Right(Vector.empty)
      else for {
        kills <- kills(ready, use.side, result, returned)
        gift = gives(use.side, user, result)
      } yield kills ++ gift)
  }

  /** The warbands a victorious attacker's Conquest would give back to a player
    * defender: what stands at the targets now, before the losses kill it, less
    * half. Read when the losses window is folded, which is before they run.
    */
  private def returnedToDefender(ready: ReadyGame, side: CampaignPlanSide,
      result: CampaignResult): Int =
    if (side != CampaignPlanSide.Attacker || result.kind != CampaignKind.Conquest)
      0
    else result.defender match {
      case CampaignDefender.Player(_) =>
        val total = result.targetSites.flatMap(ready.game.current.map.sites.get)
          .map(_.forces).collect {
            case SiteForces.Occupied(_, count) => count }.sum
        total - total / 2
      case CampaignDefender.Bandits => 0
    }

  private def kills(ready: ReadyGame, side: CampaignPlanSide,
      result: CampaignResult, returned: Int)
      : Either[OathViolation, Vector[CoreOperation]] = {
    def killed(player: PlayerId, amount: Int)
        : Either[OathViolation, Vector[CoreOperation]] =
      if (amount <= 0) Right(Vector.empty)
      else PlayerFacts.forceKind(ready, player).map(kind => Vector(Kill(
        Piece.Warbands(kind, amount),
        PositionedLocation(Location.PlayArea(player)))))
    def board(player: PlayerId): Int = ready.game.current.players
      .find(_.player == player).fold(0)(_.board.warbands)
    (side, result.defender) match {
      case (CampaignPlanSide.Defender, _) =>
        killed(result.attacker, board(result.attacker))
      case (CampaignPlanSide.Attacker, CampaignDefender.Player(defender)) =>
        result.kind match {
          case CampaignKind.Conquest => killed(defender, returned)
          case CampaignKind.Raid => killed(defender, board(defender))
        }
      case (CampaignPlanSide.Attacker, CampaignDefender.Bandits) =>
        Right(Vector.empty)
    }
  }

  private def gives(side: CampaignPlanSide, user: PlayerId,
      result: CampaignResult): Vector[CoreOperation] = {
    val to: Location = (side, result.defender) match {
      case (CampaignPlanSide.Defender, _) => Location.PlayArea(result.attacker)
      case (_, CampaignDefender.Player(defender)) => Location.PlayArea(defender)
      case (_, CampaignDefender.Bandits) => Location.SharedBank
    }
    Vector(Give(Piece.Favor(1), user, Location.PlayArea(user), to))
  }
}

object StickyFire {
  val id: PowerId = PowerId("relic.sticky-fire")
  /** Under the Campaign's prefix, so a parked question is a Campaign decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix + "sticky-fire"
  val yes: DecisionOptionRef.Button = DecisionOptionRef.Button("kill")
  val no: DecisionOptionRef.Button = DecisionOptionRef.Button("spare")

  def forCatalog(catalog: ExecutableCatalog): Option[StickyFire] =
    CatalogCards.relic(catalog, id).map(new StickyFire(_))
}
```


- [ ] **Step 4: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.StickyFireSuite oathdigital.gameplay.CampaignRaidSuite oathdigital.gameplay.CampaignProcedureSuite oathdigital.gameplay.walker.WalkerProcedureRegistrySuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS.

- [ ] **Step 5: Run the whole suite and the architecture check**

Run: `./sbtw test` and `python3 scripts/check-architecture.py`
Expected: PASS (1463 tests), and `architecture check passed`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: implement Sticky Fire

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 6: Warning Signals

**Files:**
- Create: `WarningSignals.scala`.
- Modify: `PlanRules.scala`.
- Test: `WarningSignalsSuite.scala`; `PlanDriver` (modified).

**Interfaces:**
- Consumes: Task 4's `PlanDiscard`; Task 3's `CampaignPlanEffect.Run`; `PowerAnswers.distribution`.
- Produces: `WarningSignals`; test-side `PlanDriver.Run.query` and `options`.

Warning Signals (card 25) is a defender's plan for a player defender: "Move any warbands to and from your board and any sites you rule (except the last warband from a site). At end, discard Warning Signals." It costs nothing. Its effect is a `Run` of one `Branch`: when the defender rules a site and holds a warband anywhere, it asks one exact distribution over the defender's board and every site they rule, targeted or not, with each site's minimum one, and then moves the warbands (a site that shrinks sends its extras to the board first, so the board always holds what the sites that grow are given). The distribution is asked inside the plan's application, so it is resumed like any question a plan asks. Its slots keep to what the plan does not change: the board and the ruled sites as they stand when the plan is chosen. The card is discarded when the Campaign has resolved, whether or not it changed anything, through `later` at the root and `PlanDiscard`.

- [ ] **Step 1: Write the tests**

In `src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala`, replace:

```scala
    def pick(who: PlayerId, id: String, ref: DecisionOptionRef): Run =
      answer(who, id, ChooseOneAnswer(ref))

    /** The options the parked decision offers, as `actor`'s Campaign builds it. */
    def offered(actor: PlayerId): Vector[DecisionOptionRef] = {
      val current = ready(state)
      val tree = CampaignProcedure.rebuild(catalog, current, actor, Vector.empty)
        .toOption.get
      ProcedureWalker.openDecisions(current, tree,
        current.game.current.walkerPending.get,
        WalkerPowerCatalog.default(catalog)).headOption.map(_.query).collect {
        case DecisionQuery.ChooseOne(options, _) => options.map(_.ref)
      }.getOrElse(Vector.empty)
    }

    def refused(who: PlayerId, id: String, answer: DecisionAnswer)
        : Option[OathViolation] =
```

with:

```scala
    def pick(who: PlayerId, id: String, ref: DecisionOptionRef): Run =
      answer(who, id, ChooseOneAnswer(ref))

    /** The parked decision's query, as `actor`'s Campaign builds it. */
    def query(actor: PlayerId): DecisionQuery = {
      val current = ready(state)
      val tree = CampaignProcedure.rebuild(catalog, current, actor, Vector.empty)
        .toOption.get
      ProcedureWalker.openDecisions(current, tree,
        current.game.current.walkerPending.get,
        WalkerPowerCatalog.default(catalog)).head.query
    }

    /** The options the parked choice offers. */
    def options(actor: PlayerId): Vector[DecisionOption] = query(actor) match {
      case DecisionQuery.ChooseOne(options, _) => options
      case _ => Vector.empty
    }

    def offered(actor: PlayerId): Vector[DecisionOptionRef] =
      options(actor).map(_.ref)

    def refused(who: PlayerId, id: String, answer: DecisionAnswer)
        : Option[OathViolation] =
```

Create `src/test/scala/oathdigital/gameplay/powers/campaign/WarningSignalsSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.DecisionAnswer._

/** Warning Signals: a defender rearranges their warbands over their board and the
  * sites they rule, before their force is scored, and the card is discarded when
  * the Campaign has resolved.
  */
class WarningSignalsSuite extends munit.FunSuite {
  private val card = cardWith("denizen.warning-signals")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val decision = WarningSignals.decisionId

  /** The other player rules the origin and a second site, two warbands each,
    * holds three on their board, and holds Warning Signals. The Campaign targets
    * the origin only.
    */
  private def defending: Board = {
    val two = againstPlayer(board(extras = 1))
    val extra = two.extras.head
    val lineage = two.player(two.other).lineage
    val ruled = two.copy(ready = two.ready.updateCurrent(current =>
      current.copy(map = current.map.copy(sites = current.map.sites.updated(
        extra, current.map.sites(extra).copy(forces = SiteForces.Occupied(
          ForceKind.Exile(lineage), 2)))))))
    replacePlayer(withAdviserFor(ruled, ruled.other, card, Orientation.FaceUp),
      ruled.other)(p => p.copy(board = p.board.copy(warbands = 3)))
  }

  /** The second site the other player rules, which the Campaign does not target. */
  private def secondSite(b: Board): SiteId =b.ready.game.current.map.inPlay.find(
    site => site != b.origin && b.ready.game.current.map.sites(site).forces ==
      exile(b, 2)).get

  private def forcesAt(state: OathState, site: SiteId): SiteForces =
    ready(state).game.current.map.sites(site).forces

  private def exile(b: Board, count: Int): SiteForces =
    SiteForces.Occupied(ForceKind.Exile(b.player(b.other).lineage), count)

  private def arrangement(b: Board, board: Int, origin: Int, extra: Int) =
    DistributeAnswer(Vector(
      DistributeAmount(DecisionOptionRef.Player(b.other), board),
      DistributeAmount(DecisionOptionRef.Site(b.origin), origin),
      DistributeAmount(DecisionOptionRef.Site(secondSite(b)), extra)))

  private def chosen(b: Board): Run = commit(rules(losing), b, 4).pick(b.other,
    CampaignIds.defenderPlan, ref)

  test("choosing it asks the defender to arrange their board and every ruled site") {
    val b = defending
    val run = chosen(b)
    assertEquals(run.continue, awaits(b.other, decision))
    // Nothing has moved yet, and the card has not been discarded.
    assertEquals(forcesAt(run.state, b.origin), exile(b, 2))
    assertEquals(player(run.state, b.other).board.warbands, 3)
  }

  test("the answer moves warbands between the board and the sites, keeping the total") {
    val b = defending
    val run = chosen(b)
    val moved = run.answer(b.other, decision, arrangement(b, board = 0, origin = 4,
      extra = 3))
    assertEquals(forcesAt(moved.state, b.origin), exile(b, 4))
    assertEquals(forcesAt(moved.state, secondSite(b)), exile(b, 3))
    assertEquals(player(moved.state, b.other).board.warbands, 0)
    // The window then offers what is left, so the defender finishes it.
    assertEquals(moved.continue, awaits(b.other, CampaignIds.defenderPlan))
  }

  test("warbands may also go from the sites to the board") {
    val b = defending
    val moved = chosen(b).answer(b.other, decision, arrangement(b, board = 5,
      origin = 1, extra = 1))
    assertEquals(forcesAt(moved.state, b.origin), exile(b, 1))
    assertEquals(forcesAt(moved.state, secondSite(b)), exile(b, 1))
    assertEquals(player(moved.state, b.other).board.warbands, 5)
  }

  test("the defense is scored with the force the rearrangement left at the target") {
    val b = defending
    val done = chosen(b).answer(b.other, decision, arrangement(b, board = 0,
      origin = 4, extra = 3)).finish
    assertEquals(ready(done.state).game.current.lastCampaignResult.get
      .defenseScore, 4)
  }

  test("a site must keep a warband, and the total must be kept") {
    val b = defending
    val run = chosen(b)
    assert(run.refused(b.other, decision, arrangement(b, board = 3, origin = 0,
      extra = 4)).nonEmpty)
    assert(run.refused(b.other, decision, arrangement(b, board = 2, origin = 2,
      extra = 2)).nonEmpty)
    assert(run.refused(b.other, decision, arrangement(b, board = 4, origin = 3,
      extra = 3)).nonEmpty)
    assert(run.refused(b.other, decision, arrangement(b, board = 3, origin = 2,
      extra = 2)).isEmpty)
  }

  test("the query names the board and each ruled site, with what each holds now") {
    val b = defending
    val slots = chosen(b).query(b.actor) match {
      case DecisionQuery.Distribute(slots, min, max, _, _) =>
        assertEquals((min, max), (7, 7))
        slots
      case other => fail(s"expected a distribution, got $other")
    }
    assertEquals(slots.map(s => (s.ref, s.minimum, s.maximum, s.suggested)),
      Vector(
        (DecisionOptionRef.Player(b.other), 0, 7, Some(3)),
        (DecisionOptionRef.Site(b.origin), 1, 6, Some(2)),
        (DecisionOptionRef.Site(secondSite(b)), 1, 6, Some(2))))
  }

  test("it is discarded when the Campaign has resolved, won or lost") {
    val b = defending
    def discarded(state: OathState) = ready(state).game.current.commonCards
      .regionalDiscards.values.exists(_.contains(id))
    val lost = chosen(b).answer(b.other, decision, arrangement(b, 3, 2, 2)).finish
    assertEquals(ready(lost.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assert(discarded(lost.state))
    assertEquals(player(lost.state, b.other).advisers.collectFirst {
      case held: DenizenState if held.id == id => held }, None)
    val won = commit(rules(winning), b, 4).pick(b.other, CampaignIds.defenderPlan,
      ref).answer(b.other, decision, arrangement(b, 3, 2, 2)).finish
    assertEquals(ready(won.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(true))
    assert(discarded(won.state))
  }

  test("a defender that rules no site has nowhere to move to, so nothing is asked, and the card is still discarded") {
    val base = withEnemyAtOrigin(board(warbands = 4))
    val b = replacePlayer(withAdviserFor(base, base.other, card, Orientation.FaceUp),
      base.other)(p => p.copy(board = p.board.copy(warbands = 3)))
    val run = commit(rules(losing), b, 2, raid = true).pick(b.other,
      CampaignIds.defenderPlan, ref)
    assertEquals(run.continue, awaits(b.actor, CampaignIds.sacrifice))
    val done = run.finish
    assert(ready(done.state).game.current.commonCards.regionalDiscards.values
      .exists(_.contains(id)))
  }

  test("a bandit defender never uses it, and the attacker's own copy is no defender's plan") {
    val two = board(extras = 1)
    val bandit = withSiteCard(two, two.extras.head, card)
    assertEquals(commit(rules(losing), bandit, 2).continue,
      awaits(bandit.actor, CampaignIds.sacrifice))
    val attacker = withAdviser(board(), card, Orientation.FaceUp)
    assertEquals(commit(rules(losing), attacker, 2).continue,
      awaits(attacker.actor, CampaignIds.sacrifice))
  }

  test("the recorded moves replay to the same state, and survive the journal wire") {
    val b = defending
    val g = rules(losing)
    val run = commit(g, b, 4).pick(b.other, CampaignIds.defenderPlan, ref)
      .answer(b.other, decision, arrangement(b, board = 0, origin = 4, extra = 3))
    assertEquals(PaidActionHarness.replayed(g, b.ready, run.events),
      ready(run.state))
    assert(PaidActionHarness.wireRoundTrips(run.events))
  }
}
```


- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.WarningSignalsSuite"`
Expected: FAIL to compile, for example `not found: value WarningSignals`.

- [ ] **Step 3: Implement**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans that reach beyond the plan window, registered together:
  * Sticky Fire, which asks a question in the losses, and Warning Signals, which
  * asks a decision of its own and is discarded at the end. A power whose card is
  * absent from `catalog` is omitted.
  */
object PlanRules {
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    StickyFire.forCatalog(catalog).toVector ++
      WarningSignals.forCatalog(catalog).toVector
}
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/WarningSignals.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.{CampaignProcedure, CampaignSetup}
import oathdigital.gameplay.powers.{CatalogCards, PlayerFacts, PowerAnswers}
import oathdigital.model._

/** Warning Signals (card 25), a defender's battle plan: "Move any warbands to and
  * from your board and any sites you rule (except the last warband from a site).
  * At end, discard Warning Signals."
  *
  * Only a player defender uses it, from an adviser or a site the defender rules.
  * It costs nothing. When it is chosen the defender arranges their warbands
  * again, before their force is scored: one distribution over their board and
  * every site they rule, whether or not it is targeted, that keeps the total and
  * leaves each site at least one warband. Nothing is asked when there is nowhere
  * to move to. The card is discarded when the Campaign has resolved, whether or
  * not the defender won.
  */
final case class WarningSignals private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan {
  def id: PowerId = WarningSignals.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.user.flatMap(user => context.denizen(cardId).map(source =>
      CampaignPlanOffer(source, "Warning Signals: rearrange your warbands",
        Vector.empty, Vector(CampaignPlanEffect.Run(Vector(rearrange(user)))))))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      use.user.toVector.map(PlanDiscard.denizen(catalog, _, cardId))))

  /** The board and each ruled site, with the warbands each holds now. */
  private def holdings(ready: ReadyGame, user: PlayerId)
      : (Int, Vector[(SiteId, Int)]) = {
    val current = ready.game.current
    val board = current.players.find(_.player == user).fold(0)(_.board.warbands)
    val sites = current.map.inPlay.filter(site => CampaignSetup
      .defenderAt(ready, site).contains(CampaignDefender.Player(user)))
      .flatMap(site => current.map.sites(site).forces match {
        case SiteForces.Occupied(_, count) if count > 0 => Some(site -> count)
        case _ => None
      })
    (board, sites)
  }

  private def rearrange(user: PlayerId): Operation = Branch((ready, _) => {
    val (board, sites) = holdings(ready, user)
    val total = board + sites.map(_._2).sum
    if (sites.isEmpty || total == 0) Vector.empty
    else {
      val room = total - (sites.size - 1)
      val slots = DistributeSlot(DecisionOptionRef.Player(user), 0, total,
        Some(board)) +: sites.map { case (site, count) => DistributeSlot(
          DecisionOptionRef.Site(site), 1, room, Some(count)) }
      Vector(
        Decide(WarningSignals.decisionId, user, DecisionQuery.Distribute.exactly(
          slots, total, Some("Warning Signals: arrange your warbands. Your " +
            "board holds the ones no site keeps, and each site keeps at least one"),
          "Move warbands")),
        BuildOps((state, pending) => PowerAnswers.distribution(pending,
          WarningSignals.decisionId).toRight(PowerAnswers.missing(
          WarningSignals.decisionId)).flatMap(rows => moves(state, user, rows))))
    }
  })

  /** The moves from what each site holds now to what the answer gives it:
    * every site that shrinks sends its extras to the board first, so the board
    * always holds what the sites that grow are given.
    */
  private def moves(ready: ReadyGame, user: PlayerId,
      rows: Vector[DistributeAmount])
      : Either[OathViolation, Vector[CoreOperation]] =
    PlayerFacts.forceKind(ready, user).map { kind =>
      val (_, sites) = holdings(ready, user)
      val changes = sites.flatMap { case (site, now) => rows.collectFirst {
        case DistributeAmount(DecisionOptionRef.Site(`site`), wanted) =>
          (site, wanted - now)
      }}
      def move(site: SiteId, count: Int, out: Boolean): CoreOperation =
        Move(Piece.Warbands(kind, count),
          PositionedLocation(if (out) Location.Site(site)
            else Location.PlayArea(user)),
          PositionedLocation(if (out) Location.PlayArea(user)
            else Location.Site(site)))
      changes.collect { case (site, delta) if delta < 0 =>
        move(site, -delta, out = true) } ++
        changes.collect { case (site, delta) if delta > 0 =>
          move(site, delta, out = false) }
    }
}

object WarningSignals {
  val id: PowerId = PowerId("denizen.warning-signals")
  /** Under the Campaign's prefix, so a parked question is a Campaign decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix + "warning-signals"

  def forCatalog(catalog: ExecutableCatalog): Option[WarningSignals] =
    CatalogCards.denizen(catalog, id).map(new WarningSignals(_, catalog))
}
```


- [ ] **Step 4: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.WarningSignalsSuite oathdigital.gameplay.powers.campaign.StickyFireSuite oathdigital.gameplay.BackendArchitectureSuite oathdigital.gameplay.DiscardRestrictionsCoverageSuite"`
Expected: PASS.

- [ ] **Step 5: Run the whole suite and the architecture check**

Run: `./sbtw test` and `python3 scripts/check-architecture.py`
Expected: PASS (1473 tests), and `architecture check passed`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: implement Warning Signals

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

### Task 7: Gleaming Armor

**Files:**
- Create: `GleamingArmor.scala`.
- Modify: `PlanRules.scala`.
- Test: `GleamingArmorSuite.scala`.

**Interfaces:**
- Consumes: `CampaignPlanApplication` (`side`, `user`, `source`, `setup`), `CampaignPlans.cardOf`, `Costs.onCard`.
- Produces: `GleamingArmor`.

Gleaming Armor (card 66) is a persistent rule of a faceup adviser: "Your enemy's battle plans have an added cost of [secret]." It is a `Transform` at `CampaignPlanApplication` that matches on the operation, as Silver Tongue matches `PlacementTree`. While its holder is in the Campaign, as the attacker or as a player defender, a plan applied for the opposing side is paid one more secret first: placed onto the plan's source card, like any cost, and for the title's plan (which has no card) one faceup secret turned facedown. The added cost is part of the application, so the dry run sees it: a plan the enemy cannot afford with it is not offered, and the option's price includes it. Bandits are enemies too, so a bandit defender's plans are taxed. Bandits hold no secrets and cannot pay, so its application is refused (the dry run sees it) and a bandit applies no plan while a holder attacks.

- [ ] **Step 1: Write the tests**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/GleamingArmorSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Gleaming Armor: while its faceup holder is in a Campaign, every plan the enemy
  * chooses costs one more secret, placed onto its card, or turned facedown for the
  * title's plan. A plan the enemy cannot afford with it is not offered, and the
  * option states the price with it.
  */
class GleamingArmorSuite extends munit.FunSuite {
  private val armor = cardWith("denizen.gleaming-armor")
  private val watchdog = cardWith("denizen.watchdog")
  private val honors = cardWith("denizen.battle-honors")
  private val brass = relicWith("relic.brass-army.campaign")
  private val titleRef: DecisionOptionRef = DecisionOptionRef.Button("title")

  private def secrets(b: Board, who: PlayerId, faceUp: Int): Board =
    replacePlayer(b, who)(p => p.copy(board = p.board.copy(faceUpSecrets = faceUp,
      faceDownSecrets = 0)))

  private def held(state: OathState, who: PlayerId): (Int, Int) = {
    val board = player(state, who).board
    board.faceUpSecrets -> board.faceDownSecrets
  }

  private def price(option: DecisionOption): OptionPrice = option match {
    case DecisionOption.Priced(_, price) => price
    case _ => OptionPrice()
  }

  // ---- the attacker holds it: the defender's plans cost more ---------------

  /** The attacker holds Gleaming Armor. The defender holds the title, and a
    * Watchdog, which costs nothing of itself.
    */
  private def attackerHolds(defenderSecrets: Int): Board = {
    val base = againstPlayer(board())
    secrets(withAdviserFor(withAdviser(base, armor, Orientation.FaceUp),
      base.other, watchdog, Orientation.FaceUp), base.other, defenderSecrets)
  }

  test("a defender's plan costs one more secret, which turns facedown at once because it is paid off turn") {
    val b = attackerHolds(1)
    val run = commit(rules(losing), b, 4)
    val options = run.options(b.actor)
    val watchdogOption = options.find(_.ref == DecisionOptionRef.Denizen(
      DenizenId(watchdog))).get
    assertEquals(price(watchdogOption), OptionPrice(secrets = 1))
    val picked = run.pick(b.other, CampaignIds.defenderPlan,
      DecisionOptionRef.Denizen(DenizenId(watchdog)))
    assertEquals(held(picked.state, b.other), (0, 1))
    assertEquals(player(picked.state, b.other).advisers.collectFirst {
      case card: DenizenState if card.id.value == watchdog => card.tokens },
      Some(Tokens.empty))
  }

  test("the title's plan costs a faceup secret turned facedown, since it has no card") {
    val b = attackerHolds(1)
    val run = commit(rules(losing), b, 4)
    val title = run.options(b.actor).find(_.ref == titleRef).get
    assertEquals(price(title), OptionPrice(secrets = 1))
    val picked = run.pick(b.other, CampaignIds.defenderPlan, titleRef)
    assertEquals(held(picked.state, b.other), (0, 1))
  }

  test("a plan the defender cannot afford with the added cost is not offered") {
    val b = attackerHolds(0)
    // Neither the title's plan nor the Watchdog can be paid for.
    assertEquals(commit(rules(losing), b, 4).continue,
      awaits(b.actor, CampaignIds.sacrifice))
  }

  test("without Gleaming Armor the same plans are free") {
    val base = againstPlayer(board())
    val b = secrets(withAdviserFor(base, base.other, watchdog, Orientation.FaceUp),
      base.other, 0)
    val run = commit(rules(losing), b, 4)
    assertEquals(run.options(b.actor).map(price), Vector.fill(3)(OptionPrice()))
  }

  test("a facedown Gleaming Armor is not active") {
    val base = againstPlayer(board())
    val b = secrets(withAdviserFor(withAdviser(base, armor, Orientation.FaceDown),
      base.other, watchdog, Orientation.FaceUp), base.other, 0)
    assertEquals(commit(rules(losing), b, 4).options(b.actor).map(price),
      Vector.fill(3)(OptionPrice()))
  }

  test("bandits are enemies too: a bandit defender is taxed, cannot pay, and applies no plan") {
    val two = board(extras = 1)
    val free = withSiteCard(withSiteCard(two, two.origin, watchdog),
      two.extras.head, honors)
    val taxed = withAdviser(free, armor, Orientation.FaceUp)
    // Every pool change but the attacker's: the printed defense, Watchdog's die
    // and the record of each plan the bandit applied.
    def defenseChanges(run: Run): Int = run.ops.count {
      case ModifyDicePool(pool, _, _) => pool != CampaignIds.attackPool
      case _ => false
    }
    def orderBank(state: OathState): Int =
      ready(state).banks.favor.getOrElse(Suit.Order, 0)
    // Without the holder the bandit applies both plans by itself: Watchdog's die
    // and a record of each, on top of the printed defense.
    val plain = commit(rules(losing), free, 2)
    val armored = commit(rules(losing), taxed, 2)
    assertEquals(plain.continue, awaits(free.actor, CampaignIds.sacrifice))
    assertEquals(armored.continue, awaits(taxed.actor, CampaignIds.sacrifice))
    assertEquals(defenseChanges(plain) - defenseChanges(armored), 3)
    // It offers nothing to the attacker either, and nothing is paid or flipped.
    assertEquals(armored.ops.count(op => op.isInstanceOf[PayCost] ||
      op.isInstanceOf[FlipSecrets]), 0)
    // Battle Honors would have paid the winning bandit, and now does not.
    val won = plain.finish
    val lost = armored.finish
    assertEquals(ready(won.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assertEquals(orderBank(won.state), orderBank(OathState.Ready(free.ready)) - 2)
    assertEquals(orderBank(lost.state), orderBank(OathState.Ready(free.ready)))
  }

  // ---- the defender holds it: the attacker's plans cost more ---------------

  private def defenderHolds(attackerSecrets: Int): Board = {
    val base = againstPlayer(board())
    secrets(withRelic(withAdviserFor(base, base.other, armor, Orientation.FaceUp),
      brass), base.actor, attackerSecrets)
  }

  test("an attacker's plan costs one more secret, placed onto its card") {
    val b = defenderHolds(2)
    val run = commit(rules(winning), b, 2)
    val option = run.options(b.actor).find(_.ref == DecisionOptionRef.Relic(
      RelicId(brass))).get
    assertEquals(price(option), OptionPrice(secrets = 2))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan,
      DecisionOptionRef.Relic(RelicId(brass)))
    assertEquals(player(picked.state, b.actor).relics.map(_.tokens),
      Vector(Tokens(0, 2)))
    assertEquals(held(picked.state, b.actor), (0, 0))
  }

  test("an attacker with only the secret Brass Army needs cannot pay the added cost, so it is not offered") {
    val b = defenderHolds(1)
    assertEquals(commit(rules(winning), b, 2).continue,
      awaits(b.other, CampaignIds.defenderPlan))
  }

  test("the holder's own plans are not taxed") {
    val base = againstPlayer(board())
    val b = secrets(withAdviserFor(withAdviserFor(base, base.other, armor,
      Orientation.FaceUp), base.other, watchdog, Orientation.FaceUp), base.other, 0)
    val run = commit(rules(losing), b, 4)
    assertEquals(run.options(b.actor).map(price), Vector.fill(3)(OptionPrice()))
  }

  test("the card is registered once and is automatic, as the catalog marks it persistent") {
    val plans = oathdigital.gameplay.powers.WalkerPowerCatalog.default(catalog)
      .powers.filter(_.id == GleamingArmor.id)
    assertEquals(plans.size, 1)
    assertEquals(plans.head.resolution, PowerResolution.Automatic)
  }
}
```


- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.GleamingArmorSuite"`
Expected: FAIL to compile, for example `not found: value GleamingArmor`.

- [ ] **Step 3: Implement**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans that reach beyond the plan window, and the rule that taxes
  * them, registered together: Sticky Fire (a question in the losses), Warning
  * Signals (a decision of its own and a discard at the end) and Gleaming Armor (an
  * added cost on the enemy's plans). A power whose card is absent from `catalog`
  * is omitted.
  */
object PlanRules {
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    StickyFire.forCatalog(catalog).toVector ++
      WarningSignals.forCatalog(catalog).toVector ++
      GleamingArmor.forCatalog(catalog).toVector
}
```

Create `src/main/scala/oathdigital/gameplay/powers/campaign/GleamingArmor.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.{CampaignPlanApplication, CampaignPlans}
import oathdigital.gameplay.operations.Costs
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution}
import oathdigital.model._

/** Gleaming Armor (card 66), a persistent rule of a faceup adviser: "Your enemy's
  * battle plans have an added cost of [secret]."
  *
  * While its holder is in a Campaign, as the attacker or as a player defender,
  * every plan the opposing side chooses costs one more secret, placed onto the
  * plan's source card like any plan's cost. The title has no card, so the added
  * cost of the title's plan is turning one of its user's faceup secrets
  * facedown. The added cost is part of the plan's application, so a plan the user
  * cannot afford with it is not offered, and the option's price includes it.
  * Bandits are enemies too, and the cost applies to a bandit defender's plans.
  * Bandits hold no secrets and cannot pay it, so while a holder attacks a bandit
  * defender applies no plan at all.
  *
  * The rule is automatic, so it needs no selection. A facedown copy is not
  * active, and the card is adviser-only, so the holder is found among the
  * players' faceup advisers.
  */
final case class GleamingArmor private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower {
  def id: PowerId = GleamingArmor.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignPlanApplication -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case application: CampaignPlanApplication =>
          surcharge(ctx, application).fold(children)(_ +: children)
        case _ => children
      })))

  private def holder(ctx: PowerCtx): Option[PlayerId] =
    ctx.state.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player)

  /** The added cost, when the plan is the enemy's of a holder in this Campaign. */
  private def surcharge(ctx: PowerCtx, application: CampaignPlanApplication)
      : Option[Operation] = for {
    holding <- holder(ctx)
    if enemy(application, holding)
  } yield application.user.fold[Operation](unpayable)(user =>
    BuildOps((ready, _) => CampaignPlans.cardOf(application.source) match {
      case Some(card) => Right(Vector[CoreOperation](Costs.onCard(user, card,
        Cost(secret = 1), catalog, intoOccupied = true)))
      case None =>
        // Turning a secret facedown does nothing without one, so the cost of the
        // title's plan is checked here rather than left to a best-effort flip.
        val faceUp = ready.game.current.players.find(_.player == user)
          .fold(0)(_.board.faceUpSecrets)
        if (faceUp >= 1) Right(Vector[CoreOperation](FlipSecrets(user, 1,
          SecretSide.FaceUp, SecretSide.FaceDown)))
        else Left(OathViolation.InsufficientSecrets(1, faceUp))
    }))

  private def enemy(application: CampaignPlanApplication, holding: PlayerId)
      : Boolean = application.side match {
    case CampaignPlanSide.Defender => application.setup.actor == holding
    case CampaignPlanSide.Attacker =>
      application.setup.defender == CampaignDefender.Player(holding)
  }

  /** Bandits hold no secrets, so a bandit's plan cannot pay the added cost. */
  private def unpayable: Operation = BuildOps((_, _) =>
    Left(OathViolation.InsufficientSecrets(1, 0)))
}

object GleamingArmor {
  val id: PowerId = PowerId("denizen.gleaming-armor")

  def forCatalog(catalog: ExecutableCatalog): Option[GleamingArmor] =
    CatalogCards.denizen(catalog, id).map(new GleamingArmor(_, catalog))
}
```


- [ ] **Step 4: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.GleamingArmorSuite oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS.

- [ ] **Step 5: Run the whole suite and the architecture check**

Run: `./sbtw test` and `python3 scripts/check-architecture.py`
Expected: PASS (1483 tests), and `architecture check passed`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: implement Gleaming Armor

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

- [ ] **Step 7: Record sub-slice 3d**

The rulings rows are marked implemented, the notes for 3d are added, and the design's status and slicing table say slice 3 is complete. Two deferred items go on the roadmap.

In `docs/ROADMAP.md`, replace:

```markdown
  operation reveals a card in a temporary hand and the hand is projected to its
  owner only, so no view shows the reveal to the other players yet.

- [ ] **Deferred: offer a nested Campaign only when it would be accepted.**
  Knights Errant runs a Campaign inside a Muster and offers it whenever a
  Campaign is legal. A restriction on the whole Campaign (Vow of Peace, the
```

with:

```markdown
  operation reveals a card in a temporary hand and the hand is projected to its
  owner only, so no view shows the reveal to the other players yet.

- [ ] **Deferred: a board slot for distributions and Sticky Fire without a choice.**
  Warning Signals names the defender's board by a player option in its distribution, which
  the panel shows as a player name, and Sticky Fire asks its question even when a yes
  changes nothing (against bandits it only costs the favor). A board option, and skipping a
  question whose answers are the same, need a small change to the option vocabulary.

- [ ] **Deferred: offer a nested Campaign only when it would be accepted.**
  Knights Errant runs a Campaign inside a Muster and offers it whenever a
  Campaign is legal. A restriction on the whole Campaign (Vow of Peace, the
```

In `docs/superpowers/specs/2026-09-20-powers-design.md`, replace:

```markdown

> Status: design approved 2026-09-20. Slice 0 (E1 to E5), slice 1a, slice 1b, slice 1c, slice 1d and slice 2 (sub-slices 2a to 2f) are implemented; see the [Slice 0 plan](../plans/2026-09-20-powers-slice-0-foundations.md), the [slice 1a plan](../plans/2026-09-20-powers-slice-1a-when-played-and-simple-actions.md), the [slice 1b plan](../plans/2026-09-20-powers-slice-1b-dice-and-relic-draws.md) the [slice 1c plan](../plans/2026-09-20-powers-slice-1c-targets-and-information.md) the [slice 1d plan](../plans/2026-09-20-powers-slice-1d-movement.md) and the [slice 2 plan](../plans/2026-09-20-powers-slice-2-modifiers-restrictions-triggers.md) (all six sub-slices). Per-power rules are in [the rulings appendix](2026-09-20-powers-rulings.md). Extends the [procedure walker design](2026-09-05-procedure-walker-design.md) and follows the [Campaign port](2026-09-19-campaign-walker-design.md). Each slice below gets its own implementation plan, and slice 1 is split into four.

> Slice 3 (battle plans) is planned in four sub-slices: see its [plan](../plans/2026-09-20-powers-slice-3-battle-plans.md). Implemented so far: 3a to 3c.

## Goal and scope

```

with:

```markdown

> Status: design approved 2026-09-20. Slice 0 (E1 to E5), slice 1a, slice 1b, slice 1c, slice 1d and slice 2 (sub-slices 2a to 2f) are implemented; see the [Slice 0 plan](../plans/2026-09-20-powers-slice-0-foundations.md), the [slice 1a plan](../plans/2026-09-20-powers-slice-1a-when-played-and-simple-actions.md), the [slice 1b plan](../plans/2026-09-20-powers-slice-1b-dice-and-relic-draws.md) the [slice 1c plan](../plans/2026-09-20-powers-slice-1c-targets-and-information.md) the [slice 1d plan](../plans/2026-09-20-powers-slice-1d-movement.md) and the [slice 2 plan](../plans/2026-09-20-powers-slice-2-modifiers-restrictions-triggers.md) (all six sub-slices). Per-power rules are in [the rulings appendix](2026-09-20-powers-rulings.md). Extends the [procedure walker design](2026-09-05-procedure-walker-design.md) and follows the [Campaign port](2026-09-19-campaign-walker-design.md). Each slice below gets its own implementation plan, and slice 1 is split into four.

> Slice 3 (battle plans) is planned in four sub-slices: see its [plan](../plans/2026-09-20-powers-slice-3-battle-plans.md). Implemented so far: 3a to 3d, so slice 3 is complete.

## Goal and scope

```

In `docs/superpowers/specs/2026-09-20-powers-design.md`, replace:

```markdown
| 1c. Targets and information (implemented) | Alchemist, Wolves, Sleight of Hand, Crystal Vial, Ivory Eye; Horned Mask | none |
| 1d. Movement (implemented) | Whistle, Brass Horse, Magic Carpet | none beyond accepting `Reveal` at a regional discard |
| 2. Modifiers, restrictions, triggers | Augury, Truthful Harp, Tents, Forest Paths, Cup of Plenty, Rowdy Pub, Dragonskin Drum, Relic Worship, Knights Errant; Toll Roads, Grasping Vines, Circlet, Oaken and Rotting Fortress; Wild Cry, Welcoming Party, Gossip | E6 (`CardPlayed` split), E7, E9 |
| 3. Battle plans | Mercenaries, Wrestlers, Warning Signals, Towering and Cracked Rampart, Fearsome Shield, Battle Honors, Sticky Fire; Gleaming Armor | E8 |
| 4. Banner faces | Wandering Flame (move, place a secret), Mob | E3's banner source, E6's `PlacementRules` |

Slices 2, 3 and 4 are independent once slice 0 lands. Slices 1a to 1d need only slice 0. They are planned one at a time, so each plan can use what the previous one learned. Slice 1a is planned: see its [plan](../plans/2026-09-20-powers-slice-1a-when-played-and-simple-actions.md). Slice 1b is planned: see its [plan](../plans/2026-09-20-powers-slice-1b-dice-and-relic-draws.md). Slice 1c is planned: see its [plan](../plans/2026-09-20-powers-slice-1c-targets-and-information.md). Slice 1d is planned: see its [plan](../plans/2026-09-20-powers-slice-1d-movement.md). Slice 2 is planned in six sub-slices: see its [plan](../plans/2026-09-20-powers-slice-2-modifiers-restrictions-triggers.md). The order above is the recommended one.
```

with:

```markdown
| 1c. Targets and information (implemented) | Alchemist, Wolves, Sleight of Hand, Crystal Vial, Ivory Eye; Horned Mask | none |
| 1d. Movement (implemented) | Whistle, Brass Horse, Magic Carpet | none beyond accepting `Reveal` at a regional discard |
| 2. Modifiers, restrictions, triggers | Augury, Truthful Harp, Tents, Forest Paths, Cup of Plenty, Rowdy Pub, Dragonskin Drum, Relic Worship, Knights Errant; Toll Roads, Grasping Vines, Circlet, Oaken and Rotting Fortress; Wild Cry, Welcoming Party, Gossip | E6 (`CardPlayed` split), E7, E9 |
| 3. Battle plans (implemented) | Mercenaries, Wrestlers, Warning Signals, Towering and Cracked Rampart, Fearsome Shield, Battle Honors, Sticky Fire; Gleaming Armor | E8 |
| 4. Banner faces | Wandering Flame (move, place a secret), Mob | E3's banner source, E6's `PlacementRules` |

Slices 2, 3 and 4 are independent once slice 0 lands. Slices 1a to 1d need only slice 0. They are planned one at a time, so each plan can use what the previous one learned. Slice 1a is planned: see its [plan](../plans/2026-09-20-powers-slice-1a-when-played-and-simple-actions.md). Slice 1b is planned: see its [plan](../plans/2026-09-20-powers-slice-1b-dice-and-relic-draws.md). Slice 1c is planned: see its [plan](../plans/2026-09-20-powers-slice-1c-targets-and-information.md). Slice 1d is planned: see its [plan](../plans/2026-09-20-powers-slice-1d-movement.md). Slice 2 is planned in six sub-slices: see its [plan](../plans/2026-09-20-powers-slice-2-modifiers-restrictions-triggers.md). The order above is the recommended one.
```

In `docs/superpowers/specs/2026-09-20-powers-rulings.md`, replace:

```markdown
| R27 Fearsome Shield | defender | Cost 2 secrets burnt. +2 defense dice. Implemented (slice 3c). |
| E20 Towering Rampart | defender | +2 defense dice if the ruler's pawn is at this site or this site is a Conquest target. Implemented (slice 3c). |
| E20 Cracked Rampart | defender | +1 defense die if this site is a Conquest target. A Raid never targets a site. Implemented (slice 3c). |
| 25 Warning Signals | defender | A `Distribute.exactly` over the defender's board and every site they rule, total conserved, each ruled site's minimum 1. It applies when chosen, before the defender's force is recorded. Player defenders only. After the Campaign fully resolves it is discarded, unconditionally. |
| 2 Battle Honors | either | Chosen at the plan step. After the result, if its user won (the attacker when `attackerWins`, else the defender), gain 2 favor from the Order bank with `Gain.Favor`. A bandit defender that wins gains them too, settled into the shared bank as a move from the Order bank to `Location.SharedBank`, and applies the plan by itself because it is free. Implemented (slice 3c). |
| R01 Sticky Fire | either | If its user wins, a second prompt at `CampaignLosses`, owned by the winner, asks whether to kill all warbands in the enemy's force. Attacker wins a Conquest: the defender's half-return is cancelled. Attacker wins a Raid: every warband on the defender's board dies, not half. Defender wins: every warband on the attacker's board dies, committed or not. Then the winner gives the loser 1 favor if able, a non-required `Give`. Against bandits it burns the favor, as a `Give` to `Location.SharedBank`. |

Persistent modifier, not a plan:

| Card | Ruling |
| --- | --- |
| 66 Gleaming Armor | While its holder, faceup as an adviser, is a Campaign participant, every plan chosen by the opposing side costs 1 more secret, placed on the plan's source card. Unaffordable plans are not offered and the preview includes it. The Oathkeeper title plan has no card, so its added cost is flipping a faceup secret facedown. |

Off-turn settlement for a defender's plan payment: favor moves directly to the matching suit bank, and a secret becomes a `FlipSecrets(FaceUp, FaceDown)`. Nothing rests on the card.

```

with:

```markdown
| R27 Fearsome Shield | defender | Cost 2 secrets burnt. +2 defense dice. Implemented (slice 3c). |
| E20 Towering Rampart | defender | +2 defense dice if the ruler's pawn is at this site or this site is a Conquest target. Implemented (slice 3c). |
| E20 Cracked Rampart | defender | +1 defense die if this site is a Conquest target. A Raid never targets a site. Implemented (slice 3c). |
| 25 Warning Signals | defender | A `Distribute.exactly` over the defender's board and every site they rule, total conserved, each ruled site's minimum 1. It applies when chosen, before the defender's force is recorded. Player defenders only. After the Campaign fully resolves it is discarded, unconditionally. Implemented (slice 3d). |
| 2 Battle Honors | either | Chosen at the plan step. After the result, if its user won (the attacker when `attackerWins`, else the defender), gain 2 favor from the Order bank with `Gain.Favor`. A bandit defender that wins gains them too, settled into the shared bank as a move from the Order bank to `Location.SharedBank`, and applies the plan by itself because it is free. Implemented (slice 3c). |
| R01 Sticky Fire | either | If its user wins, a second prompt at `CampaignLosses`, owned by the winner, asks whether to kill all warbands in the enemy's force. Attacker wins a Conquest: the defender's half-return is cancelled. Attacker wins a Raid: every warband on the defender's board dies, not half. Defender wins: every warband on the attacker's board dies, committed or not. Then the winner gives the loser 1 favor if able, a non-required `Give`. Against bandits it burns the favor, as a `Give` to `Location.SharedBank`. Implemented (slice 3d). |

Persistent modifier, not a plan:

| Card | Ruling |
| --- | --- |
| 66 Gleaming Armor | While its holder, faceup as an adviser, is a Campaign participant, every plan chosen by the opposing side costs 1 more secret, placed on the plan's source card. Unaffordable plans are not offered and the preview includes it. The Oathkeeper title plan has no card, so its added cost is flipping a faceup secret facedown. Bandits are enemies too, so the added cost applies to a bandit defender's plans, which bandits cannot pay (they hold no secrets): a bandit applies no plan while a holder is attacking. Implemented (slice 3d). |

Off-turn settlement for a defender's plan payment: favor moves directly to the matching suit bank, and a secret becomes a `FlipSecrets(FaceUp, FaceDown)`. Nothing rests on the card.

```

In `docs/superpowers/specs/2026-09-20-powers-rulings.md`, replace:

```markdown
- **3a:** `CampaignResult.victorious` is now `attackerWins` in the model, the journal codec, the shared DTO and its codec, the result panel and the suites. It is true when the attacker prevailed and false when the defender did. The wire key changes with it, and journals are forward-only, so a game whose journal holds a recorded Campaign result cannot be read after this change.
- **3b:** every plan is a `BattlePlan` power declared as an `Offer` (where its card must stand, what it costs and does) and, when it acts later, a hook at a later window. The user must be the ruler of the source: the origin-site offer to a non-ruler is gone, and Brass Army no longer needs an empty relic. A defender's plan may carry a cost, paid at once. Costs are `Favor` and `Secret` (placed onto the card, which may be occupied), `FavorBurnt`, `SecretBurnt` and `SacrificeWarband` (a defender only: the board in a Raid, or a target site the defender rules in a Conquest, asking which when several). A plan that cannot be paid, with every power's added cost, is not offered, and a window with nothing to offer is skipped. The option states its price. A source is chosen once. A bandit defender applies every cost-free plan at a site Bandits rule that no power makes unpayable, pays nothing, and records what it applied in a pool marker (`campaign.plan-applied.<kind>.<id>`) that a later hook reads, because a bandit's plan is not an answer. A facedown adviser is revealed when it is chosen, and a card at a site is always faceup. Outriders scores the attack again without the skull cap, and Brass Army adds four dice to the pool but not to the force.
- **3c:** the six plans are `BattlePlan` powers registered through `SimplePlans`. Mercenaries costs a favor placed onto the card, adds three attack dice for an attacker and removes three for a defender (never below an empty pool), and when its user is defeated is discarded by the standard denizen discard once the Campaign has resolved: facedown to the discard pile of the region after the card's own region (the site's region for a card on a site, the region of its holder's pawn for an adviser), its favor returned to the Discord bank. The sign is fixed by the side. Wrestlers is a defender's plan whose cost is the sacrifice, so a defender with no warband in its force is not offered it. Fearsome Shield burns two faceup secrets and places nothing on the relic. The Rampart plans are used by the ruler of the site the edifice stands at, from either face: the intact face needs the ruler's pawn at the site or the site targeted, the ruined face needs the site targeted, and a bandit ruler applies it without choosing. Battle Honors is free, and its user gains the favor after the Campaign has resolved, best-effort. A bandit defender that wins gains it too, into the shared bank, having applied the plan by itself.

## Slice 4: banner faces

```

with:

```markdown
- **3a:** `CampaignResult.victorious` is now `attackerWins` in the model, the journal codec, the shared DTO and its codec, the result panel and the suites. It is true when the attacker prevailed and false when the defender did. The wire key changes with it, and journals are forward-only, so a game whose journal holds a recorded Campaign result cannot be read after this change.
- **3b:** every plan is a `BattlePlan` power declared as an `Offer` (where its card must stand, what it costs and does) and, when it acts later, a hook at a later window. The user must be the ruler of the source: the origin-site offer to a non-ruler is gone, and Brass Army no longer needs an empty relic. A defender's plan may carry a cost, paid at once. Costs are `Favor` and `Secret` (placed onto the card, which may be occupied), `FavorBurnt`, `SecretBurnt` and `SacrificeWarband` (a defender only: the board in a Raid, or a target site the defender rules in a Conquest, asking which when several). A plan that cannot be paid, with every power's added cost, is not offered, and a window with nothing to offer is skipped. The option states its price. A source is chosen once. A bandit defender applies every cost-free plan at a site Bandits rule that no power makes unpayable, pays nothing, and records what it applied in a pool marker (`campaign.plan-applied.<kind>.<id>`) that a later hook reads, because a bandit's plan is not an answer. A facedown adviser is revealed when it is chosen, and a card at a site is always faceup. Outriders scores the attack again without the skull cap, and Brass Army adds four dice to the pool but not to the force.
- **3c:** the six plans are `BattlePlan` powers registered through `SimplePlans`. Mercenaries costs a favor placed onto the card, adds three attack dice for an attacker and removes three for a defender (never below an empty pool), and when its user is defeated is discarded by the standard denizen discard once the Campaign has resolved: facedown to the discard pile of the region after the card's own region (the site's region for a card on a site, the region of its holder's pawn for an adviser), its favor returned to the Discord bank. The sign is fixed by the side. Wrestlers is a defender's plan whose cost is the sacrifice, so a defender with no warband in its force is not offered it. Fearsome Shield burns two faceup secrets and places nothing on the relic. The Rampart plans are used by the ruler of the site the edifice stands at, from either face: the intact face needs the ruler's pawn at the site or the site targeted, the ruined face needs the site targeted, and a bandit ruler applies it without choosing. Battle Honors is free, and its user gains the favor after the Campaign has resolved, best-effort. A bandit defender that wins gains it too, into the shared bank, having applied the plan by itself.
- **3d:** the three powers are registered through `PlanRules`. A question a plan asks is under the Campaign's decision prefix (`campaign.`), so a parked one is always a Campaign decision. Warning Signals is a defender's plan for a player defender only, from an adviser or a ruled site. Its one distribution covers the defender's board (named by a player option) and every site they rule, targeted or not, keeps the total, and leaves each site one warband at least. It asks nothing when the defender rules no site, and the card is discarded after the Campaign whether or not it was used to any effect. Sticky Fire is a plan for either side. When its user wins, the losses step asks them before anything dies; a yes cancels the warbands a Conquest would return to a player defender, kills what is left on a Raid defender's board, or kills the attacker's whole board when the defender won, and then the winner gives the loser a favor if able (burnt against bandits). Gleaming Armor is an automatic rule of a faceup adviser: while its holder is in a Campaign, each plan the opposing side chooses is applied with one more secret placed onto its card (a secret turned facedown for the title), so the plan is not offered when it cannot be paid and its price includes the secret. Bandits are taxed too and cannot pay, so a bandit defender applies no plan while a holder attacks.

## Slice 4: banner faces

```


Run: `python3 scripts/check-markdown-links.py`
Expected: `Markdown link check passed`.

```bash
git add docs
git commit -m "docs: record slice 3d

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Open questions for the product owner

Each has a recommended default. The plan builds the default, and each is a small change if the answer differs.

1. **Renaming the journal key breaks games that already recorded a Campaign result.** `CampaignResultCodec` writes `victorious` today, and journals are forward-only, so after 3a an event stream holding a recorded result no longer decodes. Example: a trusted-alpha game saved after its first Campaign fails to load. Recommended: accept, as the design's E8 says the wire key changes with the field, and no production games exist. A two-line fallback in the codec (read `victorious` when `attackerWins` is absent) would remove the risk if there are saved games worth keeping.
2. **Sticky Fire's question is asked even when a yes changes nothing.** Against bandits a yes only burns the winner's favor, and a defender or attacker with an empty enemy force kills nothing. Recommended: always ask. The card says "you may", the player owns the choice, and a rule that skips the question when it looks pointless has to define "pointless". The alternative is to skip it when there is nothing to kill and no favor to give.
3. **Sticky Fire gives the favor only when its user says yes, and only if able.** The card text is "If you do, you must give them favor if able", so a "no" gives nothing, and a winner with no favor gives nothing. The rulings appendix reads "then the winner gives", which could be read as unconditional. Recommended: the card text, as built.
4. **Sticky Fire after a Conquest the attacker wins.** The defender's warbands at the targets die as usual, and the half that would return to the defender's board is killed after it returns. Recommended: as built. A bandit defender has no board, so a yes against bandits only burns the favor.
5. **Answered: where a discarded Mercenaries goes.** The product owner: "Discards are based on the region of the card. If on a site, it goes to the next site over. If it's an adviser, it's based on the pawn." Built as the existing rule, `CardPlay.nextRegion`, applied to the card's own region: a card on a site goes to the pile of the region after the site's region, an adviser to the pile of the region after the region of its holder's pawn. **Flagged:** "the next site over" is read as the next region, which is what `nextRegion` returns and what every other discard does. No existing helper builds a discard for a site card outside the pawn's region (`Dazzle` and site replacement only discard cards in the pawn's region, where the two rules agree), so `PlanDiscard` is the one place that applies the site's region.
6. **When the after-Campaign effects run.** Mercenaries' discard, Battle Honors' gain and Warning Signals' discard run once the Campaign has fully resolved (after a Conquest's placement, and after a Raid's transfer and the pawn's relocation), not right after the result. The rulings say "after the result" for Battle Honors and "after the Campaign fully resolves" for Warning Signals. Recommended: the end for all three. Example where it matters: an attacker who wins a Raid gains Battle Honors' favor after the Raid has burnt half of the defender's favor, which cannot affect the winner but could affect a defender's Sticky Fire favor.
7. **Ruler-only removes plans a Raid defender used to have.** A Raid's defender is the enemy pawn at the attacker's site, so a Rampart at that site helps only if the defender rules it, and a site card at an unruled origin helps nobody (it used to help the attacker). Recommended: ruler-only as specced. Example: the defender is Raided at a site a third party rules; the defender can use their own advisers and relics and no site's cards.
8. **Warning Signals' reach.** The distribution covers the defender's board and every site they rule, targeted or not, and only sites they rule (warbands cannot be moved to a site they do not rule), each keeping at least one. It may move warbands between two sites directly. It asks nothing when the defender rules no site. Recommended: as built. A stricter reading is that only targeted sites matter to the battle, but the card says any site you rule.
9. **Warning Signals is discarded even when it did nothing,** for example when the defender ruled no site so nothing was asked, or when the plan was chosen and the Campaign then ended in a defender victory. The ruling says "unconditionally". Recommended: as ruled.
10. **Answered: a bandit defender and the plans it applies.** Bandits apply every cost-free plan of a card at a site they rule, if no power makes it unpayable. That includes the Ramparts (for a targeted site, since bandits have no pawn) and Battle Honors, which pays a winning bandit: the product owner ruled that the two favor from the Order bank settle into the shared bank. It excludes Mercenaries, Wrestlers and Fearsome Shield, which cost something. Built as ruled, with a pool marker so a later window can tell a bandit applied a plan (planning fact 13).
11. **Answered: Gleaming Armor taxes bandits too.** The product owner: bandits are enemies. A bandit defender's plans carry the added secret, bandits hold none, so a bandit applies no plan while a holder attacks. Built as ruled, with a test that a bandit offers and applies nothing, and that Battle Honors then pays it nothing.
12. **Gleaming Armor and the title.** The title's plan has no card, so its added cost is one faceup secret turned facedown, as ruled. A defender who holds the title and has no faceup secret does not get the title's plan against a holder. Recommended: as ruled.
13. **What the player is shown.** An unaffordable plan is hidden, not shown disabled, and each shown option carries its price as text under the card ("Cost: 1 favor", "Cost: sacrifice 1 warband"). The design says "unaffordable plans are not offered and the preview shows the total cost". Recommended: as built. A disabled option with the reason would need a new option state in the wire vocabulary.
14. **The sacrificed warband of Wrestlers.** It is killed at once (it goes to the warband bank), so it lowers the force the defense is scored with, and it is not part of what a Conquest returns to the defender's board when the attacker wins. In a Conquest the defender chooses which target site pays when several qualify, and a site ruled but not targeted cannot pay. Recommended: as ruled.
15. **Facedown advisers are revealed when a plan is chosen, and stay faceup.** This includes advisers whose plan changes nothing visible (Outriders, Battle Honors), as the design says (NF p. 13). Recommended: as built.
16. **Deferred, unchanged:** Mercenaries' player-chosen sign; Peace Envoy and other powers that restrict plans; Bag of Siegeworks; Empire defenders. The reviewed catalog's entries for the four ported plans stay in `CampaignPowers` (inert, because nothing resolves a Campaign's plan windows through them). Recommended: leave them, and remove them in the slice that retires the reviewed catalog.

## Risks to check while executing

- **The `Repeat` rule reaches every `Repeat`.** A pass that records nothing and asks nothing now ends the loop. Every existing `Repeat` records or parks in each pass, so nothing changes, and `RepeatPassSuite` pins the rule. If a suite that used to hang now finishes early, look for a guard that depends on something the pass never changes.
- **`CampaignFixture.rules` now defaults to the production powers.** Other suites use it (`TargetingFixture`, `KnightsErrantSuite`, the Fortress and Circlet suites). None fails today, because a plan is offered only where a card is held. A suite that stages a Campaign for a defender who holds a title gets the title's plan, as it did before the change.
- **The resume shape of a plan.** A walk that parks inside a plan (a sacrifice, Warning Signals) resumes against changed state. If a new plan asks a question in its application, keep the offer independent of what the plan spends or moves (planning fact 2), and add a suite that answers the question, as `CampaignPlanWindowSuite` and `WarningSignalsSuite` do.
- **The dry run accepts a park.** A plan whose application parks on a decision is accepted if everything before the decision ran, so a failure in an effect after the decision is found only when the player answers. Today only Warning Signals has one, and its effect cannot fail.
- **Root hooks run in power order.** `later` at `CampaignActionEligibility` appends to the Campaign's root, so several used plans append in the deterministic power order (priority, source key, id). Nothing depends on the order today (a discard, a gain, a discard).
- **A stored Campaign parked at a plan window cannot resume** (planning fact 12). Journals are forward-only, so this needs no migration, but do not run the slice against a live game parked at a plan.
- **The frontend shows a price through the option's existing details line** (`WalkerPanelSupport`). No frontend code changes in 3b to 3d, and no frontend test covers a priced option; the projector suite does.
- **File sizes.** `ProcedureWalker.scala` grows by a few lines (still under 700) and `WalkerPowerGather.scala` stays under 300. No production file nears the 800-line bound.
- **A bandit's plan marker is a dice-pool entry.** It is a `ModifyDicePool` in the journal under `campaign.plan-applied.<kind>.<id>`. A suite that counts a Campaign's `ModifyDicePool` operations sees it (`RampartSuite` and `GleamingArmorSuite` count the defense pool, or every pool but the attacker's, for that reason).
- **Knights Errant's nested Campaign.** It builds the Campaign tree at walk time, so its plan windows are folded like any other, and the registry now recognises every `campaign.` decision inside a Muster. No suite runs a plan inside a Knights Errant Campaign.

## Self-review

- **Spec coverage.** Mercenaries, Wrestlers, Fearsome Shield, Towering and Cracked Rampart, Battle Honors (Task 4); Warning Signals (Task 6); Sticky Fire (Task 5); Gleaming Armor (Task 7). E8: the offer contribution (Tasks 2 and 3), `CampaignPlanApplication` window and operation (Task 3), burnt and sacrifice cost variants (Task 3), the after-outcome behaviour (Task 3's `later`, with the deviation explained under "Engine changes"), ruler-only sources and the removal of the non-ruler origin-site offer and Brass Army's empty-card requirement (Task 3), facedown advisers revealed when used (Task 3), a defender's plan with a cost (Task 3), the rename (Task 1). Two questions the design left open are answered: how a plan's cost preview reaches the projector, and whether off-turn settlement reaches a payment made inside a walk ("Engine changes" and planning fact 1).
- **Placeholders.** None. Every code step is a complete file or an exact replacement, and every one was applied in a throwaway copy of `main` in the order below.
- **Validation.** Every file and replacement in Tasks 1 to 7 was applied, in this order, to a fresh copy of `main` by a script that reads this document, compiled and run: Task 1's and each task's tests failed to compile before its implementation, each task's files equal the state it was developed to, and the whole suite and the architecture check passed after each task. The results are in the report.
- **Types.** `CampaignPlanOffer` and `OfferedPlan` (Task 2) are used by `OfferHost` and the kit (Task 3). `CampaignPlanApplication.{side, user, source, setup}` (Task 3) are read by Gleaming Armor (Task 7). `BattlePlan.later` (Task 3) is used by Outriders, Mercenaries, Battle Honors and Warning Signals, and `wrapping` (Task 5) by Sticky Fire. `PlanUse.{user, won, result, ready}` (Task 3) is read by every later hook. `PlanDiscard.denizen` (Task 4) is used by Warning Signals (Task 6). `CampaignPlans.appliedMarker` (Task 3) is written by `CampaignPlanApplication` and read by `PlanUse.chosen`. `PlanDriver` (Task 4) is extended by Task 6 (`query`, `options`) and used by Tasks 5 to 7. `CampaignFixture.{withAdviserFor, actorRules, replacePlayer, rulesWith}` (Task 3) and `{withRelicFor, withEdifice}` (Task 4) are used by every Campaign power suite.
