# Powers Slice 2: Modifiers, Restrictions and Triggers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement sixteen cards (seventeen powers, because the Fortress has two faces): the modifiers Augury, Truthful Harp, Tents, Forest Paths, Cup of Plenty, Rowdy Pub, Dragonskin Drum, Relic Worship, Knights Errant, Wild Cry and Welcoming Party, and the persistent rules Toll Roads, Grasping Vines, Circlet of Command, the Oaken and Rotting Fortress faces and Gossip. Land the engine changes E6 (the card-play split and `PlacementRules`), E7 (a window on Conspiracy's target decision) and E9 (the procedure on `PowerCtx`) that they and slice 4 need, and the generic discard rules the product owner asked for.

**Architecture:** Every power is declared solely as a `ContributingPower` over existing operations. Selected modifiers share one small base, `SelectedModifier`, which decides when a modifier may be selected (its action, access to its card, a payable cost) and lets each power state only what it does inside the walk. Persistent rules are plain `ContributingPower`s that read state. The engine changes are the ones the design names, plus the small ones found necessary at plan time; each is listed with its justification below and in the report.

**Tech Stack:** Scala 2.13, sbt via `./sbtw`, munit.

**Spec:** [Powers design](../specs/2026-09-20-powers-design.md) and [rulings appendix](../specs/2026-09-20-powers-rulings.md) (sections "Slice 2: modifiers", "Slice 2: persistent rules", "Slice 2: card-play triggers", the engine changes E6, E7 and E9, and "Walker shapes for powers"). Builds on the [slice 0 plan](2026-09-20-powers-slice-0-foundations.md) and the slice 1 plans, whose `PowerFixture`, `PaidActionHarness` and `TargetsFixture` it reuses.

## Global Constraints

- `BackendArchitectureSuite` and `scripts/check-architecture.py` apply: production files stay at or under 800 lines; no power name appears in `gameplay/walker` or `gameplay/operations` sources; a power imports nothing from `oathdigital.gameplay.walker`; no direct state write (`copy(advisers =`, `temporaryHands.updated(` and similar) under `gameplay/powers`.
- A power is declared solely by a `ContributingPower` (all sixteen here; none is a `PhasePower`). Engine changes are only those named in "Engine changes" below.
- A decision id is unique per power and lives under a prefix the registry already maps to a continuation, or under the prefix Task 13 adds for a Muster.
- A selected modifier that has a cost pays it at the very start of its action, whenever the player selects the modifier, whether or not the modifier then has an effect: the player owns that choice (product decision). Selecting several modifiers validates all of their payments together (Tasks 6 and 7), so a combination the player cannot pay is refused at selection.
- A card that cannot be discarded (locked, an intact edifice, an active modifier, protected by the Hall of Ministers) is refused by `DiscardRestrictions`, and every path that discards a card in play attaches it (Task 3).
- Commit messages end with `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`. Code, comments, commits and docs are normal prose.
- Run the whole suite with `./sbtw test`, and one suite with `./sbtw "testOnly <fully.qualified.Suite>"`.
- Test fixtures keep the card inventory whole. The first game deals cards to sites, regional discards, advisers and the decks, so a fixture that places a card takes it out of wherever it was (`CardStaging.without`, Task 3). `PowerFixture.atHome` and `asAdviser` remove a card from the world deck only.
- Recorded operations must survive the journal wire and replay. Each power's suite asserts `PaidActionHarness.replayed(...)` equals the state the command reached, and `PaidActionHarness.wireRoundTrips(...)` for a recorded batch that carries a new operation shape.
- Other slices (3 and 4) are planned in parallel. Shared files (`WalkerPowerCatalog.scala`, the design "Slicing" row, the rulings rows) are edited minimally, each addition on its own line, to ease merging.

## How this slice is split

The slice is large. It should be **executed, reviewed and merged as six sub-slices**, one at a time, in this order. Each sub-slice ends green (`./sbtw test` and the architecture check).

| Sub-slice | Tasks | Contents | Engine changes | Needs |
| --- | --- | --- | --- | --- |
| 2a. Card play and discards | 1 to 4 | the played-card hook split, `PlacementRules`, the generic discard rules, `siteDiscardFirst`; folds `AdviserLimit` and Horned Mask's discard destination into them | E6, the generic discard rules | nothing |
| 2b. Modifier kit and card-play triggers | 5 to 7 | `PowerCtx.procedure`; the selection payments; `SelectedModifier`, `CatalogCards`, `SearchFixture`; Wild Cry, Welcoming Party, Gossip | E9, `selectionPayments` | 2a |
| 2c. Travel | 8 | Tents, Forest Paths, Dragonskin Drum, Toll Roads, Grasping Vines | a free Travel is still a candidate; `ContributingPower.ignores` | 2b |
| 2d. Search, Trade, Muster and Recover | 9 | Augury, Truthful Harp, Cup of Plenty, Rowdy Pub, Relic Worship | `SearchRules.draw` takes extra cards | 2b |
| 2e. Target protection | 10 and 11 | a window on Conspiracy's target decision; Circlet of Command, Oaken and Rotting Fortress | E7 | 2b |
| 2f. Knights Errant | 12 and 13 | restrictions see what a Transform inserts and the answers of the command; Knights Errant | restriction traversal, Muster continuation | 2b, 2e |

**Slice 4 (banner faces) depends on 2a only.** Task 4 ends with `PlacementRules.siteDiscardFirst` working end to end, tested with a test-only power, so Mob is later a one-line contribution, and the generic discard rules of Task 3 are what make "an intact edifice is refused as locked" true for it. Sub-slices 2c, 2d and 2e depend on each other not at all, so they can merge in any order after 2b. 2f needs 2e because its tests use the Fortress.

## Engine changes

The design names E6, E7 and E9. Planning found that E9 is needed (it was conditional), and that the changes below are needed too. None is silent: each is in the task that needs it, and each is in the report.

- **E6, the card-play split (Tasks 1, 2 and 4).** `CardPlayed` becomes `CardPlayedFaceup(card, resultingSource)` and a new `CardPlayedFacedown(card, player)`, in the windows `ActionCardPlayedFaceup` (key `action.card-played`, unchanged) and `ActionCardPlayedFacedown` (key `action.card-played-facedown`). `PlacementRules(faceupAdviserLimit, facedownAdviserLimit, siteDiscardFirst)` replaces `PlacementTree.withAdviserLimit` and `withFaceupAdviserLimit`; contributors change it with `PlacementTree.adjust` and compose in any order. `siteDiscardFirst` lets a play to a site first discard one card of the site's list, optionally when there is room and necessarily when full, lifting the "full non-matching site" rejection.
- **Generic discard rules (Task 3, requested by the product owner).** `DiscardRestrictions` refuses every discard of a faceup locked adviser, of an intact edifice and of a card that prints a power selected for the running action, beside the Hall of Ministers rule it already had. A replaced edifice at a Homeland is now discarded (`Discard.RuinedEdifice`), not buried. Horned Mask attaches the restrictions. A coverage suite fails when a new production file builds a discard without attaching them.
- **E9 (Task 5).** `PowerCtx` gains `procedure: Option[ProcedureRef]`. It is needed twice: Welcoming Party reads a card's origin from it (a card that was a facedown adviser is played by the `PlayFacedownAdviser` procedure), and Knights Errant's hook on `CampaignCost` uses it to know it is walked for a Muster. A walk runs on a state with the pending position and the procedure stripped, and `nodePath` is only child indices, so neither could read it otherwise.
- **Selection payments (Task 6, requested by the product owner).** Every selected modifier pays its cost at the very start of its action. `ContributingPower.selectionPayments(ready, actor)` states the payment as operations (free by default), `OathRules.validateModifiers` dry-runs the payments of every selected power together through `OperationPipeline.run`, and an unpayable combination is refused at selection with "the selected modifiers cannot all be paid together". Catacombs, which already paid at the start of Recover, states its secret. The kit (Task 7) prepends each costed modifier's payment to the root of its action's tree.
- **E7 (Task 10).** `PowerWindow.ConspiracyTargetSelection` on Conspiracy's target `Decide`. A decision left with no option by a power is dropped, and Conspiracy then plays and takes nothing.
- **Beyond E6, E7 and E9, found at plan time:**
  1. **Free Travel is still a candidate** (Task 8). `TravelProcedure.candidates` read a destination's cost off the last `SpendSupply` and dropped the destination when there was none. Tents and Forest Paths remove the payment, so a free Travel vanished from the destination list. The fix reads a missing payment as 0.
  2. **`ContributingPower.ignores(ctx, other)`** (Task 8). `shouldIgnore` has no context, so a power could ignore another only for every node it is gathered at. Forest Paths ignores site powers only when the destination holds a beast card, and must be selectable (and pay) whatever the route. `ignores` defaults to `shouldIgnore` and the collector calls it, so no existing power changes.
  3. **A longer Search draw** (Task 9). `SearchRules.draw` gains `extra: Int = 0` and a `DrawSize = 3` constant, so Augury and the Truthful Harp draw more cards under the rule's own limits (stop after a Vision, a short pile) instead of repeating it.
  4. **Restrictions see the whole tree** (Task 12). `restrictionViolations` now folds each windowed composite through the powers as the walk does, and selects each `Branch` with the answers the command carries. Without both, a Restriction hooked inside a subtree a power adds (Knights Errant's nested Campaign) is never checked.
  5. **A Muster can hold a Campaign** (Task 13). `WalkerProcedureRegistry`'s Muster entry knew only the decision id `muster.source`. It now maps a Campaign's decision ids to `AwaitingCampaignDecision` and any id under a new `MusterProcedure.decisionPrefix` (`muster.`) to `AwaitingEconomyDecision`.

## What planning found

These facts are read from the code (or established by compiling and running the plan's code in a throwaway copy). They shape the tasks and are not in the design.

1. **The window key is not fingerprinted.** `CatalogHandlerInventory.fingerprint` covers catalog handler ids and `structuralFingerprint` covers catalog structure. No window key is in either, and `action.card-played` appears in no reviewed data. The faceup window keeps its key anyway. This answers the design's "Whether any structural fingerprint covers window keys (E6)".
2. **`PlacementTree`'s children are flat, and composing two contributors needed a value.** Silver Tongue used `withAdviserLimit(2)`, which rebuilds the whole child vector, so a second contributor (Mob) would have replaced the first. `PlacementTree.adjust(children)(change)` returns one `PlacementBody` carrying the rules, so the second contributor changes the same rules. The tree without a contributor is unchanged (`children.head` is still the placement `Decide`). With a contributor the parked path of the placement gains one level.
3. **Locking was enforced in three unrelated places, and one was wrong.** `DiscardRestrictions` implemented only the Hall of Ministers. `LockedAdviserOnly` was checked by `CardPlay.validateAdviserReplacement` and by Horned Mask's own filter. Intact edifices were not enforced at all: `Discard.RuinedEdifice` says "only a ruined edifice can be discarded" but nothing checked it. A locked card is locked only while faceup (`MinorActionsSuite` pins that a facedown locked adviser can be discarded), and `CardRestrictions.Locked` is never produced for a denizen (the loader yields `LockedAdviserOnly` for `[adviser-only, locked]`).
4. **The Homeland replacement buried an edifice.** `CardPlay` replaced an edifice with `Bury` when it held no tokens (and `Discard.RuinedEdifice` when it did). `Bury` ignores locked by rule, so a locked (intact) edifice could be removed. The product owner's reading is that a Homeland discards. Task 3 makes both cases a `Discard.RuinedEdifice`, which the restriction refuses for an intact edifice.
5. **Which paths discard a card in play.** `CardPlay` (attached by `CardPlayProcedure` and `legalChoices`), Dazzle (attached), Horned Mask (not attached; Task 3 attaches it), Magic Carpet (its own relic, never locked), and `Bury.standard` users (Crystal Vial, Magic Waterskin, Bone Dice, Fae Merchant, Family Heirloom: a bury, which ignores locked). The Search's discard of a drawn card is from the hand, where nothing is locked.
6. **"Active modifiers cannot be discarded" is written nowhere.** It is not in the rules documents, the rulings appendix or the code (a replacement candidate list never excluded a selected modifier). Task 3 enforces it in `DiscardRestrictions`, reading the selected powers from `walkerModifiers`, and the rulings appendix records it (Task 4's docs step). The first walk of an action, before its first park, does not have `walkerModifiers` yet, so a placement offered at that park may include a card the next command refuses; the refusal itself is exact.
7. **A walk runs on a stripped state.** `ProcedureWalker` clears `walkerPending` and `walkerProcedure` from the state its windows read. Restrictions are checked against the unstripped state (`OathRulesWalker.checkRestrictions`), which still holds the pending position and its answers. Task 13's Fortress guard reads them there; Task 5 exists because a window fold cannot read the procedure from the state.
8. **Why Vow of Peace and the Fortress never reached Knights Errant's Campaign.** `WalkerPowerGather.restrictionViolations` walks the declared tree (`node.children`) without the Transforms the walk applies, so a node a Transform inserts is never visited (Knights Errant appends its nested Campaign to the Muster's root that way). It also resolved every `Branch` with an empty `PendingTree`, so a Branch that yields its children only once a decision is answered showed nothing. Task 12 fixes both, and the command that answers a decision checks the tree the answer opens (after the walker accepts the answer, because an invalid answer can build a node that cannot exist).
9. **Campaign asks neither its kind nor its defender when there is one choice.** `kindStep` and `defenderStep` are omitted with fewer than two options, so an `OptionRestriction` on their windows cannot stop a Raid on the only enemy pawn at the site. The Fortress therefore also adds a `Restriction` at `CampaignActionEligibility` that refuses a Campaign whose only legal kind is a Raid on protected players, until the Campaign has answered one of its own decisions (a Conquest that takes the site would otherwise leave the Raid as the only kind mid-Campaign).
10. **A required decision left with no option is malformed.** `DecisionQueries.wellFormed` rejects an empty `ChooseOne`. A `Transform` at the Conspiracy window that drops the decision is therefore the shape, and Conspiracy's effects must accept "no decision was asked" (Task 10). `ChooseMany` with `min = 0` is dropped by the walker itself (a Raid's optional targets).
11. **`offerableWalkerPowers` never checks which windows a power hooks.** It asks `applicable` at the action's modifier-selection window. A modifier that answered "yes" for any window would be selectable for every action. `SelectedModifier` therefore checks the action itself.
12. **A Search's draw is one windowed `BuildOps`.** A `Transform` at `SearchBeforeDraw` receives `Vector(thatBuildOps)`, so a power wraps its `build` and adjusts the `Draw` it returns. Two wrappers add to what the wrapped node drew, so Augury and the Truthful Harp stack in either order.
13. **A card in a temporary hand cannot be revealed.** `Reveal` (a faceup `Flip`) needs a card state and a hand card has none, and the hand is projected to its owner only. The Truthful Harp records `Peek(viewer, card, Hand(actor))` for every other player and card (confirmed by the product owner). It restricts nothing: the kept card is played as usual, including facedown.
14. **`MusterSource.resolve` requires a token-free card.** The Cup of Plenty's node runs after the Trade's payment has placed a secret on the card, so it reads the suit off the answered card instead.
15. **`GainSupply` at a full track records nothing.** A test that expects a Supply gain starts the actor below 7.
16. **A Recover journal does not round-trip the whole wire.** Its first step records `ModifyDicePool` with a window the codec does not keep. This is not caused by a power. The Relic Worship suite round-trips every step after the first.
17. **`TravelProcedureSuite` handed the walker every catalog power.** Its `powers` was `WalkerPowerCatalog.default(catalog)`, selected or not, so a selected modifier that pays a cost applied to every route. A command offers automatic powers plus the ones the player selected; Task 8 makes the suite do the same.
18. **Every catalog denizen has at least one power, and the first game deals some cards to regional discards.** Fixtures pick "plain" cards (no production walker power of their own) and stage them with `CardStaging.without`.
19. **Cost timing (product rule: every selected modifier pays at the very start of its action).** Checked against every power that pays: Catacombs (existing) already paid at the start of Recover, as the first node of its transform. Phase powers pay through the engine (`PayCost` prepended to the power's tree) at the start. Tents and Forest Paths paid inside the Travel cost node (still one atomic command, but not first). Relic Worship paid after the relic was taken (`RecoverAfterRelic`). Only Relic Worship contradicted the rule, and its ruling says so (see open item 1). Tasks 6 and 7 move the payment to the root of the action's tree for every selected modifier.
20. **Combined validation needs a dry run, not a report.** `OperationPipeline.report` checks each operation against the initial state, so two payments that need the same secret both pass it. `OperationPipeline.run` applies the operations one after the other, so the second is rejected (`insufficient-pieces`). Task 6 uses `run` and discards the state.
21. **Relic Worship with Catacombs and one faceup secret was a dead end,** established by running it: both were offered, Catacombs paid the secret at the start, and answering the relic decision was rejected with nothing left to pay. With Task 6 the pair is refused at selection.
22. **Locked as a generic `OperationRestriction` (product owner's design), sized and deferred.** `OperationRestriction.reason(ready, operation)` is called by `OperationValidator` once per top-level operation (composites are not flattened first, so `Bury` and `Discard.Denizen` are distinguishable, and `Bury` can be exempt). Restrictions are a per-call argument of `OperationPipeline.run`, supplied today only by a `BuildOps` node. Walker steps all run through one place, `ProcedureWalker.recordBatch`, and two other callers use the pipeline directly (`MinorActions`, `StateBasedEvaluation`). A generic version is a `Locked` restriction registered on `WalkerPowers` (a new field), merged in `recordBatch`, built by `OathRules` from the catalog and the state, and refusing `Move`, `Flip`, `Swap` and the discards of a faceup locked adviser, an intact edifice and an active modifier. It would retire `DiscardRestrictions`' locked rules, `CardPlay`'s locked-adviser check and Horned Mask's filter, and needs an audit of every walker step that legitimately moves such a card. About 300 lines and one task, with regression risk in existing suites (Negotiation, Campaign). It is not needed by any card in this slice, so the plan keeps Task 3's `DiscardRestrictions` and a coverage suite, and the design is a ROADMAP item (added with this revision).

## File Structure

- Task 1: modify `PowerWindow.scala`, `CoreOperations.scala`, `WalkerOperationCodec.scala`, `PowerRuntime.scala`, `PowerSupport.scala`, `ActionPowers.scala`, `WalkerPowerCatalog.scala` (comment), `CardPlayProcedure.scala`, `WhenPlayedPower.scala`, `Dazzle.scala`, `ConspiracyWhenPlayed.scala`. Test: `CardPlayHooksSuite`.
- Task 2: create `gameplay/actions/PlacementRules.scala`; rewrite `powers/AdviserLimit.scala`; modify `CardPlay.scala`, `CardPlayProcedure.scala`, `SilverTongue.scala`, `HornedMask.scala`. Test: `PlacementFixture`, `PlacementRulesSuite`.
- Task 3: rewrite `operations/DiscardRestrictions.scala`; modify `CardPlay.scala`, `HornedMask.scala`. Test: `CardStaging`, `DiscardRestrictionsSuite`, `DiscardRestrictionsCoverageSuite`.
- Task 4: modify `CardPlay.scala`, `CardPlayProcedure.scala`. Test: `SiteDiscardFirstSuite`.
- Task 5: modify `ContributingPower.scala`, `ProcedureWalker.scala`, `WalkerPowerGather.scala`, `OathRulesWalker.scala`. Test: `EnclosingProcedureSuite`.
- Task 6: modify `powerresolver/ContributingPower.scala`, `OathRulesWalker.scala`, `powers/recover/CatacombsContribution.scala`. Test: `SelectionPaymentsSuite`.
- Task 7: create `powers/SelectedModifier.scala`, `powers/CatalogCards.scala`, `powers/cardplay/{WildCry,WelcomingParty,Gossip,CardPlayTriggers}.scala`; modify `WalkerPowerCatalog.scala`. Test: `SearchFixture`, `SelectedModifierSuite`, `WildCrySuite`, `WelcomingPartySuite`, `GossipSuite`.
- Task 8: create `powers/travel/{TravelPayments,Tents,ForestPaths,DragonskinDrum,TollRoads,GraspingVines,TravelModifiers}.scala`; modify `ContributingPower.scala`, `ContributionCollector.scala`, `TravelProcedure.scala`, `WalkerPowerCatalog.scala`. Test: `TravelFixture`, `ContributionIgnoresSuite`, five suites, one edit to `TravelProcedureSuite`.
- Task 9: create `powers/search/{DrawExtension,Augury,TruthfulHarp}.scala`, `powers/economy/{CupOfPlenty,RowdyPub}.scala`, `powers/recover/RelicWorship.scala`, `powers/ActionModifiers.scala`; modify `Search.scala`, `RecoverPowers.scala`, `WalkerPowerCatalog.scala`. Test: five suites.
- Task 10: modify `PowerWindow.scala`, `ConspiracyWhenPlayed.scala`. Test: `ConspiracyTargetWindowSuite`.
- Task 11: create `powers/targeting/{CircletOfCommand,FortressRules,TargetProtections}.scala`; modify `WalkerPowerCatalog.scala`. Test: `TargetingFixture`, `CircletOfCommandSuite`, `FortressRulesSuite`.
- Task 12: modify `WalkerPowerGather.scala`, `ProcedureWalker.scala`, `OathRulesWalker.scala`. Test: `RestrictionAnswersSuite`.
- Task 13: create `powers/economy/KnightsErrant.scala`; modify `WalkerPowerCatalog.scala`, `MusterProcedure.scala`, `WalkerProcedureRegistry.scala`. Test: `KnightsErrantSuite`.

All paths are under `src/main/scala/oathdigital/gameplay/` (production) or `src/test/scala/oathdigital/gameplay/` (tests) except `PowerWindow.scala` and `CoreOperations.scala` (`.../model/`) and `WalkerOperationCodec.scala` (`.../serialization/`). The step blocks below give full paths.

---

## Sub-slice 2a: Card play and discards (E6)

### Task 1: Split the played-card hook

**Files:**
- Modify: the eleven files listed for Task 1 above.
- Test: `src/test/scala/oathdigital/gameplay/CardPlayHooksSuite.scala`, and the three existing suites that name the hook.

**Interfaces:**
- Produces: `CardPlayedFaceup(card: WorldCardId, resultingSource: RuleSourceRef)` and `CardPlayedFacedown(card: WorldCardId, player: PlayerId)` (both `CoreOperation`s with no children); `PowerWindow.ActionCardPlayedFaceup` (key `"action.card-played"`) and `PowerWindow.ActionCardPlayedFacedown` (key `"action.card-played-facedown"`).
- Consumes: `CardPlay.playedSource`, which returns `None` for a discard and a facedown adviser.

A card played to a site or as a faceup adviser visits the faceup window (today's behaviour, under the old key). A card placed facedown as an adviser, a denizen or a Vision, visits the new facedown window. A discard visits neither. This is a rename plus one new case: every existing `CardPlayed` user is a faceup user.

- [ ] **Step 1: Write the tests**

Create `src/test/scala/oathdigital/gameplay/CardPlayHooksSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.catalog.CardRestrictions
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Transform}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers, WalkerStepRecorded}
import oathdigital.model._

/** The played-card windows: a faceup play and a facedown play each visit their
  * own window, and a discard visits neither.
  *
  * A probe power adds Supply in each window (1 faceup, 2 facedown), so the
  * Supply the actor ends with says which hooks ran.
  */
class CardPlayHooksSuite extends munit.FunSuite {
  private val probeId = PowerId("test.card-play-hooks")
  private val probe: ContributingPower = new ContributingPower {
    def id: PowerId = probeId
    def source: RuleSourceRef = RuleSourceRef.GameRule(probeId.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.ActionCardPlayedFaceup -> Vector(Transform((ctx, ops) =>
        ops :+ GainSupply(ctx.activePlayer, 1))),
      PowerWindow.ActionCardPlayedFacedown -> Vector(Transform((ctx, ops) =>
        ops :+ GainSupply(ctx.activePlayer, 2))))
  }
  private val powers = WalkerPowers(Vector(probe))
  private val startSupply = 3

  private def plainDenizen(ready: ReadyGame): DenizenId =
    ready.game.current.commonCards.worldDeck.collectFirst {
      case id: DenizenId if catalog.denizens.exists(d => d.id.value == id.value &&
        d.restrictions == CardRestrictions.Unrestricted) => id
    }.get

  /** The active player holds `card` in a temporary hand, with Supply 3. */
  private def inHand(card: WorldCardId): (ReadyGame, PlayerId) = {
    val base = initialReady
    val actor = base.game.current.turn.activePlayer
    val current = base.game.current
    (base.updateCurrent(_.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(_ == card)),
      temporaryHands = current.temporaryHands.updated(actor, Vector(card)),
      players = current.players.map(p => if (p.player == actor)
        p.copy(board = p.board.copy(supply = SupplyTrack(startSupply))) else p))),
      actor)
  }

  private def supplyOf(ready: ReadyGame, actor: PlayerId): Int =
    ready.game.current.players.find(_.player == actor).get.board.supply.supply

  /** Plays `card` from the hand with `button`, returning the finished walk. */
  private def play(ready: ReadyGame, actor: PlayerId, card: WorldCardId,
      button: String): WalkerOutcome.Finished = {
    val tree = CardPlayProcedure.build(catalog, ready, actor, card,
      CardPlayProcedure.Origin.TemporaryHand).toOption.get
    val parked = ProcedureWalker.advance(ready, tree, None, powers).toOption.get
      .asInstanceOf[WalkerOutcome.Parked].tree
    ProcedureWalker.resolve(ready, tree, parked, Answered(
      s"cardplay.place.${card.kind}.${card.value}",
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button(button)), actor),
      powers).toOption.get.asInstanceOf[WalkerOutcome.Finished]
  }

  private def attributed(finished: WalkerOutcome.Finished): Boolean =
    finished.events.collect { case step: WalkerStepRecorded => step }
      .exists(_.contributions.contains(probeId))

  test("the faceup window keeps the persisted key of the single window") {
    assertEquals(PowerWindow.ActionCardPlayedFaceup.key, "action.card-played")
    assertEquals(PowerWindow.ActionCardPlayedFacedown.key,
      "action.card-played-facedown")
  }

  test("a card played to a site visits the faceup window only") {
    val card = plainDenizen(initialReady)
    val (ready, actor) = inHand(card)
    val done = play(ready, actor, card, "site")
    assertEquals(supplyOf(done.treeless, actor), startSupply + 1)
    assert(attributed(done))
  }

  test("a card played as a faceup adviser visits the faceup window only") {
    val card = plainDenizen(initialReady)
    val (ready, actor) = inHand(card)
    val done = play(ready, actor, card, "adviser-faceup")
    assertEquals(supplyOf(done.treeless, actor), startSupply + 1)
  }

  test("a denizen played as a facedown adviser visits the facedown window only") {
    val card = plainDenizen(initialReady)
    val (ready, actor) = inHand(card)
    val done = play(ready, actor, card, "adviser-facedown")
    assertEquals(supplyOf(done.treeless, actor), startSupply + 2)
    assert(attributed(done))
  }

  test("a Vision played as a facedown adviser visits the facedown window") {
    val (ready, actor) = inHand(VisionRules.Faith)
    val done = play(ready, actor, VisionRules.Faith, "adviser-facedown")
    assertEquals(supplyOf(done.treeless, actor), startSupply + 2)
  }

  test("a discard visits neither window") {
    val card = plainDenizen(initialReady)
    val (ready, actor) = inHand(card)
    val done = play(ready, actor, card, "discard")
    assertEquals(supplyOf(done.treeless, actor), startSupply)
    assert(!attributed(done))
  }

  test("a facedown adviser played faceup visits the faceup window") {
    val card = plainDenizen(initialReady)
    val base = initialReady
    val actor = base.game.current.turn.activePlayer
    val current = base.game.current
    val ready = base.updateCurrent(_.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(_ == card)),
      players = current.players.map(p => if (p.player == actor)
        p.copy(board = p.board.copy(supply = SupplyTrack(startSupply)),
          advisers = p.advisers :+ DenizenState(card, Orientation.FaceDown,
            Tokens.empty)) else p)))
    val tree = CardPlayProcedure.build(catalog, ready, actor, card,
      CardPlayProcedure.Origin.FacedownAdviser).toOption.get
    val parked = ProcedureWalker.advance(ready, tree, None, powers).toOption.get
      .asInstanceOf[WalkerOutcome.Parked].tree
    val done = ProcedureWalker.resolve(ready, tree, parked, Answered(
      s"cardplay.place.${card.kind}.${card.value}",
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("adviser-faceup")),
      actor), powers).toOption.get.asInstanceOf[WalkerOutcome.Finished]
    assertEquals(supplyOf(done.treeless, actor), startSupply + 1)
  }

  test("the facedown hook names the card and the player who played it") {
    val hook = CardPlayedFacedown(VisionRules.Faith, PlayerId("p1"))
    assertEquals(hook.window, Some(PowerWindow.ActionCardPlayedFacedown))
    assertEquals(hook.children, Vector.empty[Operation])
    assertEquals((hook.card, hook.player), (VisionRules.Faith, PlayerId("p1")))
  }
}
```

In `src/test/scala/oathdigital/gameplay/ProcedureWalkerSuite.scala`, replace:

```scala
    val hook = CardPlayed(adviser.id,
```

with:

```scala
    val hook = CardPlayedFaceup(adviser.id,
```

In `src/test/scala/oathdigital/gameplay/ProcedureWalkerSuite.scala`, replace (every occurrence, 2 in all):

```scala
PowerWindow.ActionCardPlayed)
```

with:

```scala
PowerWindow.ActionCardPlayedFaceup)
```

In `src/test/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedHarness.scala`, replace:

```scala
  def hook(card: DenizenId): CardPlayed =
    CardPlayed(card, RuleSourceRef.Adviser(actor, card))
```

with:

```scala
  def hook(card: DenizenId): CardPlayedFaceup =
    CardPlayedFaceup(card, RuleSourceRef.Adviser(actor, card))
```

In `src/test/scala/oathdigital/gameplay/powers/whenplayed/DazzleSuite.scala`, replace (every occurrence, 4 in all):

```scala
CardPlayed(dazzle, RuleSourceRef.Adviser(actor, dazzle))
```

with:

```scala
CardPlayedFaceup(dazzle, RuleSourceRef.Adviser(actor, dazzle))
```

In `src/test/scala/oathdigital/gameplay/powers/whenplayed/DazzleSuite.scala`, replace:

```scala
CardPlayed(dazzleId, RuleSourceRef.Adviser(actor, dazzleId))
```

with:

```scala
CardPlayedFaceup(dazzleId, RuleSourceRef.Adviser(actor, dazzleId))
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, for example `value ActionCardPlayedFaceup is not a member of object oathdigital.model.PowerWindow`.

- [ ] **Step 3: Implement**

In `src/main/scala/oathdigital/model/PowerWindow.scala`, replace:

```scala
  case object ActionCardPlayed extends OtherWindow { val key = "action.card-played" }
```

with:

```scala
  /** A card played faceup, to a site or as a faceup adviser. The key is the
    * one the single played-card window always had, so reviewed data and
    * fingerprints do not change.
    */
  case object ActionCardPlayedFaceup extends OtherWindow {
    val key = "action.card-played"
  }
  /** A card placed facedown as an adviser, a denizen or a Vision. */
  case object ActionCardPlayedFacedown extends OtherWindow {
    val key = "action.card-played-facedown"
  }
```

In `src/main/scala/oathdigital/model/CoreOperations.scala`, replace:

```scala
/** Semantic played-card window. Powers supply its children; no hook is a delta. */
final case class CardPlayed(card: WorldCardId, resultingSource: RuleSourceRef)
    extends CoreOperation {
  override val window: Option[PowerWindow] = Some(PowerWindow.ActionCardPlayed)
  override val children: Vector[Operation] = Vector.empty
}
```

with:

```scala
/** Semantic window for a card played faceup, to a site or as a faceup
  * adviser. Powers supply its children; no hook is a delta.
  */
final case class CardPlayedFaceup(card: WorldCardId, resultingSource: RuleSourceRef)
    extends CoreOperation {
  override val window: Option[PowerWindow] = Some(PowerWindow.ActionCardPlayedFaceup)
  override val children: Vector[Operation] = Vector.empty
}

/** Semantic window for a card (a denizen or a Vision) placed facedown as an
  * adviser by `player`. A discard emits neither hook. Powers supply its
  * children; no hook is a delta.
  */
final case class CardPlayedFacedown(card: WorldCardId, player: PlayerId)
    extends CoreOperation {
  override val window: Option[PowerWindow] = Some(PowerWindow.ActionCardPlayedFacedown)
  override val children: Vector[Operation] = Vector.empty
}
```

In `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`, replace:

```scala
      case _: CardPlayed => throw UnencodableOperation(InvalidValue(
        "$.payload.ops", "a CardPlayed hook is a tree-control composite, " +
          "never a recorded delta"))
```

with:

```scala
      case _: CardPlayedFaceup | _: CardPlayedFacedown =>
        throw UnencodableOperation(InvalidValue(
          "$.payload.ops", "a CardPlayed hook is a tree-control composite, " +
            "never a recorded delta"))
```

In `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`, replace:

```scala
            val hook = CardPlay.playedSource(ready, actor, card, placement)
              .map(CardPlayed(card, _)).toVector
```

with:

```scala
            val hook: Vector[Operation] = placement match {
              case SearchPlacement.Adviser(Orientation.FaceDown, _) =>
                Vector(CardPlayedFacedown(card, actor))
              case _ => CardPlay.playedSource(ready, actor, card, placement)
                .map(CardPlayedFaceup(card, _)).toVector
            }
```

In `src/main/scala/oathdigital/gameplay/PowerRuntime.scala`, replace:

```scala
    case ActionKind.WhenPlayed => PowerWindow.ActionCardPlayed
```

with:

```scala
    case ActionKind.WhenPlayed => PowerWindow.ActionCardPlayedFaceup
```

In `src/main/scala/oathdigital/gameplay/PowerRuntime.scala`, replace:

```scala
    case PowerWindow.RestStart | PowerWindow.ActionCardPlayed |
        PowerWindow.WakeBoundary | PowerWindow.ActionAfterMajorAction =>
```

with:

```scala
    case PowerWindow.RestStart | PowerWindow.ActionCardPlayedFaceup |
        PowerWindow.ActionCardPlayedFacedown |
        PowerWindow.WakeBoundary | PowerWindow.ActionAfterMajorAction =>
```

In `src/main/scala/oathdigital/gameplay/powers/PowerSupport.scala`, replace:

```scala
      window == PowerWindow.ActionCardPlayed)
```

with:

```scala
      window == PowerWindow.ActionCardPlayedFaceup)
```

In `src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala`, replace (every occurrence, 3 in all):

```scala
PowerWindow.ActionCardPlayed
```

with:

```scala
PowerWindow.ActionCardPlayedFaceup
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, replace:

```scala
  * play runs `ActionCardPlayed` for Conspiracy.
```

with:

```scala
  * play runs `ActionCardPlayedFaceup` for Conspiracy.
```

In `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPower.scala`, replace:

```scala
    case CardPlayed(card, _) => card == cardId
```

with:

```scala
    case CardPlayedFaceup(card, _) => card == cardId
```

In `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPower.scala`, replace:

```scala
Map(PowerWindow.ActionCardPlayed ->
```

with:

```scala
Map(PowerWindow.ActionCardPlayedFaceup ->
```

In `src/main/scala/oathdigital/gameplay/powers/whenplayed/Dazzle.scala`, replace:

```scala
    case CardPlayed(card, _) => card == cardId
```

with:

```scala
    case CardPlayedFaceup(card, _) => card == cardId
```

In `src/main/scala/oathdigital/gameplay/powers/whenplayed/Dazzle.scala`, replace:

```scala
Map(PowerWindow.ActionCardPlayed ->
```

with:

```scala
Map(PowerWindow.ActionCardPlayedFaceup ->
```

In `src/main/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala`, replace:

```scala
    case CardPlayed(card, _) => card == VisionRules.Conspiracy
```

with:

```scala
    case CardPlayedFaceup(card, _) => card == VisionRules.Conspiracy
```

In `src/main/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala`, replace:

```scala
Map(PowerWindow.ActionCardPlayed ->
```

with:

```scala
Map(PowerWindow.ActionCardPlayedFaceup ->
```

- [ ] **Step 4: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.CardPlayHooksSuite oathdigital.gameplay.ProcedureWalkerSuite oathdigital.gameplay.CardPlayProcedureSuite oathdigital.gameplay.SearchProcedureSuite oathdigital.gameplay.powers.whenplayed.DazzleSuite oathdigital.gameplay.powers.whenplayed.ConspiracyWhenPlayedSuite oathdigital.gameplay.BackendArchitectureSuite oathdigital.gameplay.RuleResolutionSuite"`
Expected: PASS (117 tests in these suites and the ones they touch).

- [ ] **Step 5: Run the whole suite and the architecture check**

Run: `./sbtw test` and `python3 scripts/check-architecture.py`
Expected: PASS, and `architecture check passed`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "refactor: split the played-card hook into faceup and facedown windows

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```


### Task 2: `PlacementRules` replaces the adviser limits

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/actions/PlacementRules.scala`
- Modify: `AdviserLimit.scala` (rewritten), `CardPlay.scala`, `CardPlayProcedure.scala`, `SilverTongue.scala`, `HornedMask.scala`
- Test: `PlacementFixture.scala` (shared by Tasks 2 to 4), `PlacementRulesSuite.scala`, an edit to `CardPlayProcedureSuite.scala`

**Interfaces:**
- Produces: `PlacementRules(faceupAdviserLimit: Int = 3, facedownAdviserLimit: Int = 3, siteDiscardFirst: Boolean = false)` with `adviserLimit(orientation)`, `limitAdvisers(limit)`, `limitFaceupAdvisers(limit)`, `withSiteDiscardFirst`, `PlacementRules.default` and `PlacementRules.DefaultAdviserLimit`. `CardPlay.legalChoices(catalog, ready, actor, card, origin, rules = PlacementRules.default)` and `CardPlay.plannedOperations(..., origin, rules = PlacementRules.default)`. `CardPlayProcedure.PlacementTree.adjust(current: Vector[Operation])(change: PlacementRules => PlacementRules): Vector[Operation]` and `CardPlayProcedure.PlacementBody` (with `rules`). `CardPlay.nextRegion(region)` (now public). `SilverTongue.HolderLimit` (2) and `SilverTongue.limitFor(ready, player): Option[Int]`.
- Consumes: Task 1 (none of its names; the two tasks touch `CardPlayProcedure.scala` in different places).

This is a refactor. Nothing new is possible yet: `siteDiscardFirst` exists on the value and is read in Task 4. The point is one definition of each placement rule, and a value two powers can change without replacing each other. Slice 1c left two notes that this folds together. `AdviserLimit.of` repeated Silver Tongue's holder rule and its limit; it now asks `SilverTongue.limitFor` and `PlacementRules.DefaultAdviserLimit`. Horned Mask repeated the three-case region rule of `CardPlay`; it calls `CardPlay.nextRegion`.

The new test file `PlacementFixture` is used again in Tasks 3 and 4. It builds a hand, a pawn site holding chosen cards, and drives a `CardPlayProcedure` tree with `ProcedureWalker`, the way `CardPlayProcedureSuite` does.

- [ ] **Step 1: Write the tests**

Create `src/test/scala/oathdigital/gameplay/PlacementFixture.scala`:

```scala
package oathdigital.gameplay

import oathdigital.catalog.CardRestrictions
import oathdigital.gameplay.actions.PlacementRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Transform}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._

/** Staging and driving shared by the card-play placement suites. The powers
  * built here are test doubles: they change `PlacementRules` and nothing else.
  */
object PlacementFixture {
  /** A power that changes the rules every play is planned under. */
  def rulePower(name: String)(
      change: PlacementRules => PlacementRules): ContributingPower =
    new ContributingPower {
      def id: PowerId = PowerId(name)
      def source: RuleSourceRef = RuleSourceRef.GameRule(name)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
        PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, ops) =>
          ctx.operation match {
            case tree: CardPlayProcedure.PlacementTree =>
              tree.adjust(ops)(change)
            case _ => ops
          })))
    }

  val discardFirst: ContributingPower =
    rulePower("test.discard-first")(_.withSiteDiscardFirst)
  val limitTwo: ContributingPower =
    rulePower("test.limit-two")(_.limitAdvisers(2))

  /** The unrestricted denizens still in the world deck. */
  def plain(ready: ReadyGame): Vector[DenizenId] =
    ready.game.current.commonCards.worldDeck.collect {
      case id: DenizenId if catalog.denizens.exists(d => d.id.value == id.value &&
        d.restrictions == CardRestrictions.Unrestricted) => id
    }

  def actorOf(ready: ReadyGame): PlayerState =
    ready.game.current.players.find(
      _.player == ready.game.current.turn.activePlayer).get

  def denizen(id: DenizenId, tokens: Tokens = Tokens.empty): DenizenState =
    DenizenState(id, Orientation.FaceUp, tokens)

  /** `card` in the actor's hand and the actor's pawn site holding exactly
    * `site`. Every card that leaves a place goes to the matching deck, so the
    * inventory stays whole.
    */
  def staged(card: DenizenId, site: Vector[SiteDenizenState])
      : (ReadyGame, PlayerId, SiteId) = {
    val base = initialReady
    val current = base.game.current
    val actor = actorOf(base)
    val siteId = actor.pawnSite.get
    val placed = site.collect { case d: DenizenState => d.id: CardId } :+ card
    val edifices = site.collect { case e: EdificeState => e.id }
    val before = current.map.sites(siteId).denizens
    (base.updateCurrent(_.copy(
      temporaryHands = current.temporaryHands.updated(actor.player, Vector(card)),
      commonCards = current.commonCards.copy(
        worldDeck = current.commonCards.worldDeck.filterNot(placed.contains) ++
          before.collect { case d: DenizenState => d.id },
        edificeDeck = current.commonCards.edificeDeck.filterNot(edifices.contains) ++
          before.collect { case e: EdificeState if !edifices.contains(e.id) => e.id }),
      map = current.map.copy(sites = current.map.sites.updated(siteId,
        current.map.sites(siteId).copy(denizens = site))))),
      actor.player, siteId)
  }

  /** The actor rules the pawn site, so a Hall of Ministers does not protect it. */
  def ruledByActor(ready: ReadyGame, site: SiteId): ReadyGame =
    ready.updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(site, c.map.sites(site).copy(forces =
        SiteForces.Occupied(ForceKind.Exile(actorOf(ready).lineage), 1))))))

  def decisionId(card: WorldCardId, kind: String): String =
    s"cardplay.$kind.${card.kind}.${card.value}"

  def build(ready: ReadyGame, actor: PlayerId, card: WorldCardId): Operation =
    CardPlayProcedure.build(catalog, ready, actor, card,
      CardPlayProcedure.Origin.TemporaryHand).toOption.get

  def park(ready: ReadyGame, tree: Operation, powers: WalkerPowers)
      : PendingTree = ProcedureWalker.advance(ready, tree, None, powers)
    .toOption.get.asInstanceOf[WalkerOutcome.Parked].tree

  def answer(ready: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, id: String, ref: DecisionOptionRef,
      actor: PlayerId): WalkerOutcome = ProcedureWalker.resolve(ready, tree,
    pending, Answered(id, DecisionAnswer.ChooseOneAnswer(ref), actor), powers)
    .toOption.get

  /** The options of the decision the walk is parked on. */
  def options(ready: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers): Vector[DecisionOptionRef] =
    ProcedureWalker.parkedDecide(ready, tree, pending, powers).get.query
      .asInstanceOf[DecisionQuery.ChooseOne].options.map(_.ref)
}
```

Create `src/test/scala/oathdigital/gameplay/PlacementRulesSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.PlacementRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.AdviserLimit
import oathdigital.gameplay.powers.rest.SilverTongue
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._

/** `PlacementRules`: the adviser limits a card play is planned under, and how
  * two contributors to them compose. The powers here are test doubles.
  */
class PlacementRulesSuite extends munit.FunSuite {
  import PlacementFixture._

  test("the default rules are the printed limit of three and no site discard") {
    assertEquals(PlacementRules.default, PlacementRules(3, 3, false))
    assertEquals(PlacementRules.DefaultAdviserLimit, 3)
  }

  test("a limit only lowers, and a faceup limit leaves the facedown one") {
    assertEquals(PlacementRules.default.limitAdvisers(5), PlacementRules.default)
    assertEquals(PlacementRules.default.limitAdvisers(2),
      PlacementRules(2, 2, false))
    assertEquals(PlacementRules.default.limitFaceupAdvisers(2),
      PlacementRules(2, 3, false))
    assertEquals(PlacementRules.default.adviserLimit(Orientation.FaceUp), 3)
    assertEquals(PlacementRules.default.limitFaceupAdvisers(2)
      .adviserLimit(Orientation.FaceDown), 3)
  }

  test("without a contributor the tree is the play under the default rules") {
    val card = plain(initialReady).head
    val (ready, actor, _) = staged(card, Vector.empty)
    val tree = build(ready, actor, card)
    assertEquals(tree.children.size, 2)
    assert(tree.children.head.isInstanceOf[Decide])
  }

  test("two contributors compose in either order") {
    val card = plain(initialReady).head
    val (ready, actor, _) = staged(card, Vector.empty)
    val tree = build(ready, actor, card)
      .asInstanceOf[CardPlayProcedure.PlacementTree]
    def rulesOf(ops: Vector[Operation]): PlacementRules =
      ops.head.asInstanceOf[CardPlayProcedure.PlacementBody].rules
    val limitFirst = tree.adjust(tree.adjust(tree.children)(_.limitAdvisers(2)))(
      _.withSiteDiscardFirst)
    val discardFirstThenLimit = tree.adjust(tree.adjust(tree.children)(
      _.withSiteDiscardFirst))(_.limitAdvisers(2))
    assertEquals(rulesOf(limitFirst), PlacementRules(2, 2, true))
    assertEquals(rulesOf(discardFirstThenLimit), PlacementRules(2, 2, true))
    assertEquals(limitFirst.size, 1)
  }

  test("a contributed limit reaches the placement: two advisers fill an area " +
      "limited to two") {
    val Vector(card, first, second) = plain(initialReady).take(3)
    val (staged1, actor, _) = staged(card, Vector.empty)
    val held = Vector(first, second)
    val current = staged1.game.current
    val ready = staged1.updateCurrent(_.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(held.contains)),
      players = current.players.map(p => if (p.player == actor)
        p.copy(advisers = held.map(id => DenizenState(id,
          Orientation.FaceDown, Tokens.empty))) else p)))
    val tree = build(ready, actor, card)
    // Under the default limit of three there is room, so no discard is asked.
    val open = WalkerPowers.empty
    val opened = answer(ready, tree, park(ready, tree, open), open,
      decisionId(card, "place"), DecisionOptionRef.Button("adviser-faceup"),
      actor)
    assert(opened.isInstanceOf[WalkerOutcome.Finished])
    // Under a limit of two the area is full and a discard is asked first.
    val limited = WalkerPowers(Vector(limitTwo))
    val asked = answer(ready, tree, park(ready, tree, limited), limited,
      decisionId(card, "place"), DecisionOptionRef.Button("adviser-faceup"),
      actor).asInstanceOf[WalkerOutcome.Parked]
    assertEquals(options(ready, tree, asked.tree, limited).toSet,
      Set[DecisionOptionRef](DecisionOptionRef.Denizen(first),
        DecisionOptionRef.Denizen(second)))
  }

  test("Silver Tongue and the adviser-limit read agree on the limit") {
    val tongue = SilverTongue.forCatalog(catalog).get
    val held = base.updateCurrent(c => c.copy(players = c.players.map(p =>
      if (p.player == actorOf(base).player) p.copy(advisers = p.advisers :+
        DenizenState(tongue.cardId, Orientation.FaceUp, Tokens.empty)) else p)))
    val player = actorOf(held).player
    assertEquals(tongue.limitFor(held, player), Some(SilverTongue.HolderLimit))
    assertEquals(AdviserLimit.of(catalog, held, player), SilverTongue.HolderLimit)
    assertEquals(AdviserLimit.of(catalog, base, player), AdviserLimit.Default)
    assertEquals(tongue.limitFor(base, player), None)
  }

  private def base: ReadyGame = {
    val ready = initialReady
    val tongue = SilverTongue.forCatalog(catalog).get.cardId
    ready.updateCurrent(c => c.copy(commonCards = c.commonCards.copy(
      worldDeck = c.commonCards.worldDeck.filterNot(_ == tongue))))
  }
}
```

In `src/test/scala/oathdigital/gameplay/CardPlayProcedureSuite.scala`, replace (every occurrence, 6 in all):

```scala
CardPlay.Origin.TemporaryHand, 3, 3)
```

with:

```scala
CardPlay.Origin.TemporaryHand)
```

In `src/test/scala/oathdigital/gameplay/CardPlayProcedureSuite.scala`, replace:

```scala
CardPlay.Origin.FacedownAdviser, 3, 3)
```

with:

```scala
CardPlay.Origin.FacedownAdviser)
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, for example `not enough arguments for method legalChoices: (catalog: oathdigital.catalog.ExecutableCatalog, ready: oathdigital.model.ReadyGame, actor: oathdigital.model.PlayerId, card: oathdigital.model.WorldCardId, origin: oathdigital.gameplay.actions.CardPlay.Origin, faceupLimit: Int, facedownLimit: Int): Vector[oathdigital.gameplay.actions.CardPlay.Choice].`.

- [ ] **Step 3: Implement**

Create `src/main/scala/oathdigital/gameplay/actions/PlacementRules.scala`:

```scala
package oathdigital.gameplay.actions

import oathdigital.model.Orientation

/** The rules a card play is planned under. A power changes them through the
  * `SearchPlayAdviser` window, and card play reads nothing else, so the play
  * never names a power.
  *
  *  - `faceupAdviserLimit` and `facedownAdviserLimit` are how many advisers
  *    the player may hold in each orientation. A play that would exceed the
  *    limit must discard an adviser.
  *  - `siteDiscardFirst` lets a play to a site first discard one card of the
  *    site's card list, at any capacity. It is optional with room and required
  *    without, and it lifts the rule that a full site accepts only a card that
  *    matches its homeland edifice.
  *
  * Every change narrows or adds a permission independently of the others, so
  * two powers that change the rules compose in any order.
  */
final case class PlacementRules(
    faceupAdviserLimit: Int = PlacementRules.DefaultAdviserLimit,
    facedownAdviserLimit: Int = PlacementRules.DefaultAdviserLimit,
    siteDiscardFirst: Boolean = false) {

  def adviserLimit(orientation: Orientation): Int = orientation match {
    case Orientation.FaceUp => faceupAdviserLimit
    case Orientation.FaceDown => facedownAdviserLimit
  }

  /** Lowers both limits to at most `limit`. */
  def limitAdvisers(limit: Int): PlacementRules = copy(
    faceupAdviserLimit = math.min(faceupAdviserLimit, limit),
    facedownAdviserLimit = math.min(facedownAdviserLimit, limit))

  /** Lowers the faceup limit to at most `limit`. */
  def limitFaceupAdvisers(limit: Int): PlacementRules =
    copy(faceupAdviserLimit = math.min(faceupAdviserLimit, limit))

  /** Permits a discard before a play to a site. */
  def withSiteDiscardFirst: PlacementRules = copy(siteDiscardFirst = true)
}

object PlacementRules {
  val DefaultAdviserLimit: Int = 3
  val default: PlacementRules = PlacementRules()
}
```

Create `src/main/scala/oathdigital/gameplay/powers/AdviserLimit.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.PlacementRules
import oathdigital.gameplay.powers.rest.SilverTongue
import oathdigital.model._

/** How many advisers a player may hold, for a power that adds an adviser
  * outside card play (Horned Mask).
  *
  * Card play gets its limit from [[PlacementRules]], which Silver Tongue's
  * `SearchPlayAdviser` transform narrows. This reads the same two facts as a
  * read of state, so the limit is defined once: the default is
  * `PlacementRules.DefaultAdviserLimit` and the only power that lowers it is
  * Silver Tongue, through `SilverTongue.limitFor`.
  */
object AdviserLimit {
  val Default: Int = PlacementRules.DefaultAdviserLimit

  def of(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId): Int =
    SilverTongue.forCatalog(catalog).flatMap(_.limitFor(ready, player))
      .getOrElse(Default)
}
```

In `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`, replace:

```scala
      actor: PlayerId, card: WorldCardId, origin: Origin,
      faceupLimit: Int, facedownLimit: Int): Vector[Choice] = {
```

with:

```scala
      actor: PlayerId, card: WorldCardId, origin: Origin,
      rules: PlacementRules = PlacementRules.default): Vector[Choice] = {
```

In `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`, replace:

```scala
    placements.flatMap { placement =>
      val limit = placement match {
        case SearchPlacement.Adviser(Orientation.FaceUp, _) => faceupLimit
        case _ => facedownLimit
      }
      val direct = plannedOperations(catalog, ready, actor, card,
        placement, origin, limit).exists(permitted)
```

with:

```scala
    placements.flatMap { placement =>
      val direct = plannedOperations(catalog, ready, actor, card,
        placement, origin, rules).exists(permitted)
```

In `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`, replace:

```scala
        plannedOperations(catalog, ready, actor, card, selected,
          origin, limit).exists(permitted)
```

with:

```scala
        plannedOperations(catalog, ready, actor, card, selected,
          origin, rules).exists(permitted)
```

In `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`, replace:

```scala
      origin: Origin, adviserLimit: Int = 3)
      : Either[OathViolation, Vector[CoreOperation]] = for {
```

with:

```scala
      origin: Origin, rules: PlacementRules = PlacementRules.default)
      : Either[OathViolation, Vector[CoreOperation]] = for {
```

In `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`, replace:

```scala
    plan <- plan(catalog, ready, player, card, placement, origin, adviserLimit)
```

with:

```scala
    plan <- plan(catalog, ready, player, card, placement, origin, rules)
```

In `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`, replace:

```scala
      origin: Origin, adviserLimit: Int)
      : Either[OathViolation, PlacementPlan] = placement match {
```

with:

```scala
      origin: Origin, rules: PlacementRules)
      : Either[OathViolation, PlacementPlan] = placement match {
```

In `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`, replace:

```scala
        removed <- validateAdviserReplacement(catalog, remaining, replace,
          adviserLimit)
```

with:

```scala
        removed <- validateAdviserReplacement(catalog, remaining, replace,
          rules.adviserLimit(orientation))
```

In `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`, replace:

```scala
        else planVision(catalog, player, origin, id, orientation, replace,
          adviserLimit)
```

with:

```scala
        else planVision(catalog, player, origin, id, orientation, replace,
          rules.adviserLimit(orientation))
```

In `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`, replace:

```scala
  private def nextRegion(region: Region): Region = region match {
```

with:

```scala
  /** The region whose discard pile receives a card discarded at a site of
    * `region`.
    */
  def nextRegion(region: Region): Region = region match {
```

In `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`, replace:

```scala
import oathdigital.gameplay.actions.CardPlay
```

with:

```scala
import oathdigital.gameplay.actions.{CardPlay, PlacementRules}
```

In `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`, replace:

```scala
  /** Generic limit-aware placement seam. A power can replace children using
    * a different limit without placing its identity in the card-play rules.
    */
  final class PlacementTree private[cardplay](val card: WorldCardId,
      childrenAt: (Int, Int) => Vector[Operation])
      extends Operation {
    override val window: Option[PowerWindow] =
      Some(PowerWindow.SearchPlayAdviser)
    override val children: Vector[Operation] = childrenAt(3, 3)
    def withAdviserLimit(limit: Int): Vector[Operation] =
      childrenAt(limit, limit)
    def withFaceupAdviserLimit(limit: Int): Vector[Operation] =
      childrenAt(limit, 3)
  }
```

with:

```scala
  /** The placement subtree planned under one set of [[PlacementRules]]. */
  final class PlacementBody private[cardplay](val rules: PlacementRules,
      childrenAt: PlacementRules => Vector[Operation]) extends Operation {
    override val children: Vector[Operation] = childrenAt(rules)
    private[cardplay] def adjust(change: PlacementRules => PlacementRules)
        : PlacementBody = new PlacementBody(change(rules), childrenAt)
  }

  /** Generic rules-aware placement seam. A power changes the rules its play is
    * planned under without placing its identity in the card-play code.
    *
    * `children` is the play under the default rules. A `Transform` at
    * `SearchPlayAdviser` calls [[adjust]] with the children it was handed and
    * the change it wants. Every contributor's change is applied to the same
    * rules, so contributors compose in any order, and the result is the play
    * planned under all of them.
    */
  final class PlacementTree private[cardplay](val card: WorldCardId,
      childrenAt: PlacementRules => Vector[Operation])
      extends Operation {
    override val window: Option[PowerWindow] =
      Some(PowerWindow.SearchPlayAdviser)
    override val children: Vector[Operation] =
      childrenAt(PlacementRules.default)

    def adjust(current: Vector[Operation])(
        change: PlacementRules => PlacementRules): Vector[Operation] =
      current match {
        case Vector(body: PlacementBody) => Vector(body.adjust(change))
        case _ => Vector(new PlacementBody(change(PlacementRules.default),
          childrenAt))
      }
  }
```

In `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`, replace:

```scala
    else Right(new PlacementTree(card, (faceup, facedown) =>
      childrenFor(catalog, ready, actor, card, origin, faceup, facedown)))
```

with:

```scala
    else Right(new PlacementTree(card, rules =>
      childrenFor(catalog, ready, actor, card, origin, rules)))
```

In `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`, replace:

```scala
      actor: PlayerId, card: WorldCardId, origin: Origin, faceupLimit: Int,
      facedownLimit: Int)
      : Vector[Operation] = {
```

with:

```scala
      actor: PlayerId, card: WorldCardId, origin: Origin,
      rules: PlacementRules)
      : Vector[Operation] = {
```

In `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`, replace:

```scala
      val candidates = CardPlay.legalChoices(catalog, ready, actor, card,
        legacyOrigin, faceupLimit, facedownLimit).map { choice =>
```

with:

```scala
      val candidates = CardPlay.legalChoices(catalog, ready, actor, card,
        legacyOrigin, rules).map { choice =>
```

In `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`, replace:

```scala
            val adviserLimit = placement match {
              case SearchPlacement.Adviser(Orientation.FaceUp, _) => faceupLimit
              case _ => facedownLimit
            }
```

with nothing: delete it.

In `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`, replace:

```scala
                card, _, legacyOrigin, adviserLimit))
```

with:

```scala
                card, _, legacyOrigin, rules))
```

In `src/main/scala/oathdigital/gameplay/powers/rest/SilverTongue.scala`, replace:

```scala
        case tree: CardPlayProcedure.PlacementTree
            if holder(ctx.state).exists(_.player == ctx.activePlayer) =>
          tree.withAdviserLimit(2) :+ limitGuard(ctx.activePlayer)
        case tree: CardPlayProcedure.PlacementTree if tree.card == cardId =>
          tree.withFaceupAdviserLimit(2) :+ limitGuard(ctx.activePlayer)
        case _ => children
      })))
```

with:

```scala
        case tree: CardPlayProcedure.PlacementTree
            if limitFor(ctx.state, ctx.activePlayer).nonEmpty =>
          tree.adjust(children)(_.limitAdvisers(HolderLimit)) :+
            limitGuard(ctx.activePlayer)
        case tree: CardPlayProcedure.PlacementTree if tree.card == cardId =>
          tree.adjust(children)(_.limitFaceupAdvisers(HolderLimit)) :+
            limitGuard(ctx.activePlayer)
        case _ => children
      })))

  /** The adviser limit Silver Tongue sets on `player`: its holder, and only
    * while it is faceup.
    */
  def limitFor(ready: ReadyGame, player: PlayerId): Option[Int] =
    holder(ready).filter(_.player == player).map(_ => HolderLimit)
```

In `src/main/scala/oathdigital/gameplay/powers/rest/SilverTongue.scala`, replace:

```scala
    val holds = holder(state).exists(_.player == actor)
    if (!holds || count <= 2) Right(Vector.empty)
```

with:

```scala
    if (limitFor(state, actor).forall(count <= _)) Right(Vector.empty)
```

In `src/main/scala/oathdigital/gameplay/powers/rest/SilverTongue.scala`, replace:

```scala
object SilverTongue {
  val id: PowerId = PowerId("denizen.silver-tongue")
```

with:

```scala
object SilverTongue {
  val id: PowerId = PowerId("denizen.silver-tongue")
  /** How many advisers the holder may have, in either orientation. */
  val HolderLimit: Int = 2
```

In `src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala`, replace:

```scala
import oathdigital.gameplay.PowerAccess
```

with:

```scala
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.CardPlay
```

In `src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala`, replace:

```scala
      .flatMap(ready.game.current.map.regionOf).map(next)
```

with:

```scala
      .flatMap(ready.game.current.map.regionOf).map(CardPlay.nextRegion)
```

In `src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala`, replace:

```scala

  /** The region whose discard pile receives a discard made at a site of
    * `region`. It repeats `CardPlay`'s rule, which is private there and is
    * rewritten by slice 2, so the two may be folded together then.
    */
  private def next(region: Region): Region = region match {
    case Region.Cradle => Region.Provinces
    case Region.Provinces => Region.Hinterland
    case Region.Hinterland => Region.Cradle
  }
```

with nothing: delete it.

- [ ] **Step 4: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.PlacementRulesSuite oathdigital.gameplay.CardPlayProcedureSuite oathdigital.gameplay.CardPlayHooksSuite oathdigital.gameplay.powers.rest.SilverTongueSuite oathdigital.gameplay.powers.AdviserLimitSuite oathdigital.gameplay.powers.wake.HornedMaskSuite oathdigital.gameplay.SearchProcedureSuite oathdigital.gameplay.PendingWalkerRulesSuite oathdigital.application.PendingWalkerInvariantSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS (92 tests in these suites and the ones they touch).

- [ ] **Step 5: Run the whole suite and the architecture check**

Run: `./sbtw test` and `python3 scripts/check-architecture.py`
Expected: PASS, and `architecture check passed`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "refactor: replace the adviser limits with PlacementRules

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```


### Task 3: One rule for which cards can be discarded

**Files:**
- Rewrite: `src/main/scala/oathdigital/gameplay/operations/DiscardRestrictions.scala`
- Modify: `CardPlay.scala`, `powers/wake/HornedMask.scala`
- Test: `powers/CardStaging.scala` (shared by later tasks), `DiscardRestrictionsSuite.scala`, `DiscardRestrictionsCoverageSuite.scala`

**Interfaces:**
- Produces: `DiscardRestrictions(catalog, actor)` (same constructor) refuses, as `OperationReasonKind.Impossible` reasons with codes `locked`, `active-modifier` and `discard-immune` (the Hall of Ministers, unchanged): a `Discard.Denizen` of a faceup `LockedAdviserOnly` card from a site or a play area, a `Discard.RuinedEdifice` of an intact edifice, and a `Discard.Denizen` or `Discard.Relic` of a card that prints a power in `walkerModifiers`. Test support `CardStaging.without(ready, id)`.
- Consumes: Task 2's `PlacementFixture`.

The product owner asked for locked cards to be handled generically. Where locking is enforced today, and what this task decides, is in "What planning found" 3 to 6. The layer is `DiscardRestrictions`, not `OperationValidator`: the validator has no catalog, and a card's locked restriction is a catalog fact. `DiscardRestrictions` is also the object `CardPlay.legalChoices` uses to decide what to offer, so a card that cannot be discarded is never offered. The intact-edifice rule needs only state, but sits here beside the others so one class answers "may this be discarded?".

- **`Bury` is not a discard.** It ignores locked by rule and never reaches this class (Crystal Vial buries an intact edifice on purpose).
- **Only a card in play is locked.** A locked card drawn by a Search and discarded from the temporary hand is not, and a facedown locked adviser can be discarded (existing rule).
- **An edifice is discarded, never buried, when a play replaces it.** The `CardPlay` edit.
- **Active modifiers.** A card that prints a power selected for the running action cannot be discarded (product owner; not in the docs, so the rulings record it in the docs step). It is read from `walkerModifiers`, which the walker records at the action's first park.
- **Coverage.** `DiscardRestrictionsCoverageSuite` reads the production sources and fails when a file builds `Discard.Denizen`, `Discard.Vision` or `Discard.RuinedEdifice` without attaching `DiscardRestrictions`. Horned Mask was the one path that did not; it does now. A new discarding power fails the suite until it attaches them.

- [ ] **Step 1: Write the tests**

Create `src/test/scala/oathdigital/gameplay/DiscardRestrictionsCoverageSuite.scala`:

```scala
package oathdigital.gameplay

import java.nio.file.{Files, Paths}

import scala.jdk.CollectionConverters._

/** Every production file that builds a discard of a card in play attaches
  * `DiscardRestrictions` to it, so a rule about which cards can be discarded
  * (locked, active modifier, Hall of Ministers) cannot be bypassed by a new
  * power that forgets it.
  *
  * A file that builds one of these operations must be listed here with the
  * file that attaches the restrictions, or carry the restrictions itself. A new
  * discarding file fails this suite until it is added on purpose.
  */
class DiscardRestrictionsCoverageSuite extends munit.FunSuite {
  private val root = Paths.get("src/main/scala/oathdigital")
  private val discards =
    """Discard\.(Denizen|Vision|RuinedEdifice)\(""".r

  /** file suffix -> the file that attaches the restrictions to its operations. */
  private val delegated = Map(
    "gameplay/actions/CardPlay.scala" ->
      "gameplay/actions/cardplay/CardPlayProcedure.scala")

  private def read(relative: String): String =
    Files.readString(root.resolve(relative))

  private def sources: Vector[String] = Files.walk(root).iterator.asScala
    .filter(_.toString.endsWith(".scala")).map(p => root.relativize(p).toString)
    .toVector

  test("every file that discards a card in play attaches DiscardRestrictions") {
    val building = sources.filter(path => !path.startsWith("model/") &&
      !path.startsWith("serialization/") && discards.findFirstIn(read(path)).nonEmpty)
    assert(building.nonEmpty)
    val unguarded = building.filter { path =>
      val attached = delegated.getOrElse(path, path)
      !read(attached).contains("DiscardRestrictions")
    }
    assertEquals(unguarded, Vector.empty[String])
  }

  test("CardPlay's callers that build a play attach them") {
    assert(read("gameplay/actions/CardPlay.scala")
      .contains("new DiscardRestrictions"))
    assert(read("gameplay/actions/cardplay/CardPlayProcedure.scala")
      .contains("new DiscardRestrictions"))
  }
}
```

Create `src/test/scala/oathdigital/gameplay/DiscardRestrictionsSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.catalog.CardRestrictions
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.operations.DiscardRestrictions
import oathdigital.gameplay.powers.CardStaging
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

/** The discard restrictions: a locked denizen, an intact edifice and a
  * modifier selected for the running action cannot be discarded, whichever
  * path discards them, and a card that can be discarded is unaffected.
  */
class DiscardRestrictionsSuite extends munit.FunSuite {
  import PlacementFixture._

  private val restrictions = new DiscardRestrictions(catalog,
    actorOf(initialReady).player)
  private val lockedCard = DenizenId(catalog.denizens.find(
    _.restrictions == CardRestrictions.LockedAdviserOnly).get.id.value)
  private val hall = EdificeId("E16")
  private val wildCry = DenizenId("189")
  private val actor = actorOf(initialReady).player

  private def discard(card: DenizenId, from: Location): Discard.Denizen =
    Discard.Denizen(card, PositionedLocation(from), Region.Provinces,
      catalog.suitOf(card).get, 0, 0, actor)

  private def refused(ready: ReadyGame, operation: CoreOperation): Boolean =
    restrictions.reason(ready, operation).nonEmpty

  private def holding(ready: ReadyGame, card: DenizenId,
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    CardStaging.without(ready, card).updateCurrent(c => c.copy(players =
      c.players.map(p => if (p.player == actor) p.copy(advisers = p.advisers :+
        DenizenState(card, orientation, Tokens.empty)) else p)))

  private def selecting(ready: ReadyGame, powers: PowerId*): ReadyGame =
    ready.updateCurrent(_.copy(walkerModifiers = powers.toVector))

  test("a locked adviser cannot be discarded from play, and reports why") {
    val ready = holding(initialReady, lockedCard)
    val reason = restrictions.reason(ready,
      discard(lockedCard, Location.PlayArea(actor))).get
    assertEquals(reason.code, "locked")
    assertEquals(reason.kind, OperationReasonKind.Impossible)
  }

  test("a locked adviser is locked only while it is faceup") {
    val facedown = holding(initialReady, lockedCard, Orientation.FaceDown)
    assert(!refused(facedown, discard(lockedCard, Location.PlayArea(actor))))
  }

  test("a locked card drawn by a Search can still be discarded from the hand") {
    assert(!refused(initialReady, discard(lockedCard, Location.Hand(actor))))
  }

  test("an ordinary adviser can be discarded") {
    val card = plain(initialReady).head
    assert(!refused(holding(initialReady, card),
      discard(card, Location.PlayArea(actor))))
  }

  private def edificeAt(side: EdificeSide): (ReadyGame, SiteId) = {
    val card = plain(initialReady).head
    val (ready, _, site) = staged(card,
      Vector(EdificeState(hall, side, Tokens.empty)))
    (ruledByActor(ready, site), site)
  }

  private def edificeDiscard(site: SiteId) = Discard.RuinedEdifice(hall,
    PositionedLocation(Location.Site(site)), catalog.suitOf(hall).get, 0, 0,
    actor)

  test("an intact edifice is locked, and a ruined one is not") {
    val (intact, site) = edificeAt(EdificeSide.Intact)
    assertEquals(restrictions.reason(intact, edificeDiscard(site)).map(_.code),
      Some("locked"))
    val (ruined, ruinedSite) = edificeAt(EdificeSide.Ruined)
    assert(!refused(ruined, edificeDiscard(ruinedSite)))
  }

  test("a modifier selected for the running action cannot be discarded") {
    val ready = holding(initialReady, wildCry)
    val operation = discard(wildCry, Location.PlayArea(actor))
    assert(!refused(ready, operation))
    val active = selecting(ready, PowerId("denizen.wild-cry"))
    assertEquals(restrictions.reason(active, operation).map(_.code),
      Some("active-modifier"))
    // A different modifier selected leaves the card free.
    assert(!refused(selecting(ready, PowerId("denizen.tents")), operation))
  }

  test("a relic modifier selected for the running action cannot be discarded") {
    val relic = Discard.Relic(RelicId("R20"),
      PositionedLocation(Location.PlayArea(actor)), 0, actor)
    assert(!refused(initialReady, relic))
    assert(refused(selecting(initialReady,
      PowerId("relic.dragonskin-drum")), relic))
  }

  // ---- The card-play path ----

  private def siteChoice(ready: ReadyGame, card: DenizenId): CardPlay.Choice =
    CardPlay.legalChoices(catalog, ready, actor, card,
      CardPlay.Origin.TemporaryHand)
      .find(_.placement.isInstanceOf[SearchPlacement.Site]).get

  /** A full site whose Homeland is the Hall, so the played card may replace one
    * of its cards, and the actor rules it.
    */
  private def fullHomeland(side: EdificeSide)
      : (ReadyGame, DenizenId, Vector[DenizenId]) = {
    val hallSuit = catalog.suitOf(hall).get
    val cards = plain(initialReady)
    val card = cards.find(catalog.suitOf(_).contains(hallSuit)).get
    val (probe, _, site) = staged(card, Vector.empty)
    val capacity = catalog.sites.find(_.id == site).get.capacity
    val fillers = cards.filter(_ != card).take(capacity - 1)
    val (ready, _, _) = staged(card, fillers.map(denizen(_)) :+
      EdificeState(hall, side, Tokens.empty))
    (ruledByActor(ready, site), card, fillers)
  }

  test("a replacement can never be an intact edifice") {
    val (ready, card, fillers) = fullHomeland(EdificeSide.Intact)
    assertEquals(siteChoice(ready, card).replacements.toSet,
      fillers.toSet[CardId])
  }

  test("a ruined edifice can be the replacement, and is discarded, not buried") {
    val (ready, card, fillers) = fullHomeland(EdificeSide.Ruined)
    val choice = siteChoice(ready, card)
    assertEquals(choice.replacements.toSet, fillers.toSet[CardId] + hall)
    val planned = CardPlay.plannedOperations(catalog, ready, actor, card,
      SearchPlacement.Site(Some(hall)), CardPlay.Origin.TemporaryHand)
      .toOption.get
    assert(planned.exists(_.isInstanceOf[Discard.RuinedEdifice]))
    assert(!planned.exists(_.isInstanceOf[Bury]))
  }

  test("a locked adviser is not offered as the replacement of a faceup adviser") {
    val card = plain(initialReady).head
    val extras = plain(initialReady).filter(_ != card).take(2)
    val (staged1, _, _) = staged(card, Vector.empty)
    val current = staged1.game.current
    val full = staged1.updateCurrent(_.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(id =>
          extras.contains(id) || id == lockedCard)),
      players = current.players.map(p => if (p.player == actor)
        p.copy(advisers = (extras :+ lockedCard).map(id => DenizenState(id,
          Orientation.FaceDown, Tokens.empty))) else p)))
    val faceup = CardPlay.legalChoices(catalog, full, actor, card,
      CardPlay.Origin.TemporaryHand).find(_.placement ==
      SearchPlacement.Adviser(Orientation.FaceUp, None)).get
    assertEquals(faceup.replacements.toSet, extras.toSet[CardId])
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/CardStaging.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.model._

/** Takes a card out of wherever the first game put it, so a fixture can place
  * it somewhere else without leaving a duplicate. `PowerFixture` removes a card
  * from the world deck or the relic deck only, but the first game also deals
  * cards to sites, regional discards and advisers.
  */
object CardStaging {
  def without(ready: ReadyGame, id: CardId): ReadyGame = ready.updateCurrent {
    current =>
      val cards = current.commonCards
      current.copy(
        commonCards = cards.copy(
          worldDeck = cards.worldDeck.filterNot(_ == id),
          relicDeck = cards.relicDeck.filterNot(_ == id),
          edificeDeck = cards.edificeDeck.filterNot(_ == id),
          regionalDiscards = cards.regionalDiscards.view
            .mapValues(_.filterNot(_ == id)).toMap),
        setAsideRelics = current.setAsideRelics.filterNot(_ == id),
        temporaryHands = current.temporaryHands.view
          .mapValues(_.filterNot(_ == id)).toMap,
        players = current.players.map(player => player.copy(
          advisers = player.advisers.filterNot(_.id == id),
          relics = player.relics.filterNot(_.id == id))),
        map = current.map.copy(sites = current.map.sites.view.mapValues(site =>
          site.copy(denizens = site.denizens.filterNot(_.id == id),
            relics = site.relics.filterNot(_.id == id))).toMap))
  }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "Test/compile"`
Expected: the new tests FAIL.

- [ ] **Step 3: Implement**

Create `src/main/scala/oathdigital/gameplay/operations/DiscardRestrictions.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.catalog.{CardRestrictions, ExecutableCatalog}
import oathdigital.model._

/** Catalog-backed restrictions on discarding a card, for the acting player.
  * Every path that discards a card in play attaches one (see
  * `DiscardRestrictionsCoverageSuite`), so a card that cannot be discarded is
  * never offered as a choice and is refused if it is named anyway.
  *
  * A card cannot be discarded when:
  *
  *  - it is a locked denizen (`LockedAdviserOnly`) that is faceup: a locked
  *    card is locked only while it is faceup, and a facedown adviser can be
  *    discarded;
  *  - it is an intact edifice, which is locked (`Discard.RuinedEdifice` is the
  *    discard of an edifice, and only a ruined one can be discarded);
  *  - it prints a power that is selected for the action being run. A modifier
  *    a player selected at the start of an action stays in play until the
  *    action ends;
  *  - it is protected by the Hall of Ministers: an enemy of the ruler acts as if
  *    the denizens and relics at the ruler's sites were locked.
  *
  * `Bury` is not a discard and ignores locked, so it never reaches this class.
  * The selected modifiers are read from `walkerModifiers`, which the walker
  * records when an action first parks. An action's first walk, before its first
  * park, does not see them, so a choice offered at that first park may include
  * a card the next command refuses (a placement whose only replacement is an
  * active modifier); the refusal itself is exact.
  */
final class DiscardRestrictions(catalog: ExecutableCatalog,
    actor: PlayerId) extends OperationRestriction {
  private val hallPower = PowerId("edifice.e16.intact")

  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] =
    lockedReason(ready, operation).orElse(hallReason(ready, operation))

  private def refuse(code: String, detail: String): Option[OperationReason] =
    Some(OperationReason(code, detail, OperationReasonKind.Impossible))

  private def lockedReason(ready: ReadyGame, operation: CoreOperation)
      : Option[OperationReason] = operation match {
    case value: Discard.Denizen if inPlay(value.from.location) =>
      if (faceup(ready, value.card, value.from.location) &&
          catalog.denizens.find(_.id.value == value.card.value)
          .exists(d => d.restrictions == CardRestrictions.LockedAdviserOnly ||
            d.restrictions == CardRestrictions.Locked))
        refuse("locked", s"${value.card.value} is locked and cannot be discarded")
      else active(ready, value.card)
    case value: Discard.RuinedEdifice =>
      if (intact(ready, value.card, value.from.location))
        refuse("locked",
          s"${value.card.value} is an intact edifice and cannot be discarded")
      else None
    case value: Discard.Relic if inPlay(value.from.location) =>
      active(ready, value.card)
    case _ => None
  }

  /** Only a card in play is locked. A card drawn by a Search and discarded from
    * the temporary hand is not.
    */
  private def inPlay(from: Location): Boolean = from match {
    case _: Location.Site | _: Location.PlayArea => true
    case _ => false
  }

  /** A denizen at a site is always faceup. An adviser is as it is held. */
  private def faceup(ready: ReadyGame, card: DenizenId, from: Location)
      : Boolean = from match {
    case Location.PlayArea(owner) => ready.game.current.players
      .find(_.player == owner).exists(_.advisers.exists {
        case held: DenizenState =>
          held.id == card && held.orientation == Orientation.FaceUp
        case _ => false
      })
    case _ => true
  }

  /** `card` prints a power selected for the running action. */
  private def active(ready: ReadyGame, card: CardId): Option[OperationReason] =
    if (ready.game.current.walkerModifiers.exists(power =>
        printedBy(power).contains(card)))
      refuse("active-modifier", s"${card.value} is a modifier selected for " +
        "this action and cannot be discarded")
    else None

  private def printedBy(power: PowerId): Option[CardId] =
    catalog.denizens.find(_.powers.exists(_.id == power))
      .map(card => DenizenId(card.id.value): CardId)
      .orElse(catalog.relics.find(_.powers.exists(_.id == power))
        .map(card => RelicId(card.id.value): CardId))

  private def intact(ready: ReadyGame, card: EdificeId,
      from: Location): Boolean = from match {
    case Location.Site(site) => ready.game.current.map.sites.get(site)
      .exists(_.denizens.exists {
        case edifice: EdificeState =>
          edifice.id == card && edifice.side == EdificeSide.Intact
        case _ => false
      })
    case _ => false
  }

  private def hallReason(ready: ReadyGame, operation: CoreOperation)
      : Option[OperationReason] = {
    val source = operation match {
      case value: Discard.Denizen => Some(value.from.location)
      case value: Discard.Vision => Some(value.from.location)
      case value: Discard.RuinedEdifice => Some(value.from.location)
      case value: Discard.Relic => Some(value.from.location)
      case _ => None
    }
    source.collect { case Location.Site(site) => site }.flatMap { site =>
      val current = ready.game.current
      val actorSide = current.players.find(_.player == actor).flatMap { player =>
        ready.game.campaign.lineages.get(player.lineage).map { lineage =>
          if (lineage.role.isImperial) SiteRuler.Empire
          else SiteRuler.Player(actor)
        }
      }
      val targetRuler = current.map.sites.get(site).flatMap(state =>
        SiteRule.ruler(state.forces, current.players).toOption)
      for {
        sourceSide <- actorSide
        ruler <- targetRuler
        if SiteRule.enemies(sourceSide, ruler)
        if current.map.sites.exists { case (_, state) =>
          SiteRule.ruler(state.forces, current.players).toOption.contains(ruler) &&
            state.denizens.exists {
              case edifice: EdificeState if edifice.side == EdificeSide.Intact =>
                catalog.edifices.find(_.id.value == edifice.id.value)
                  .exists(_.intact.powers.exists(_.id == hallPower))
              case _ => false
            }
        }
      } yield OperationReason("discard-immune",
        s"cards at site ${site.value} cannot be discarded by ${actor.value}",
        OperationReasonKind.Impossible)
    }
  }
}
```

In `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`, replace:

```scala
      case Some(value: EdificeState) if !value.tokens.isEmpty =>
        suitOf(catalog, value.id).map { replacedSuit =>
          PlacementPlan(kept, favor, Vector.empty, Vector.empty,
            tokenEdifice = Some((value.id, replacedSuit,
              value.tokens.favor, value.tokens.secrets)))
        }
      case Some(value: EdificeState) =>
        Right(PlacementPlan(kept, favor, Vector.empty,
          Vector(value.id -> site)))
```

with:

```scala
      case Some(value: EdificeState) =>
        suitOf(catalog, value.id).map { replacedSuit =>
          PlacementPlan(kept, favor, Vector.empty, Vector.empty,
            tokenEdifice = Some((value.id, replacedSuit,
              value.tokens.favor, value.tokens.secrets)))
        }
```

In `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`, replace:

```scala
    val edificeOps = plan.discardedEdifices.map { case (id, from) =>
      Bury(BuryableCard.Edifice(id), from, required = true)
    } ++ plan.tokenEdifice.toVector.flatMap {
```

with:

```scala
    val edificeOps = plan.tokenEdifice.toVector.flatMap {
```

In `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`, replace:

```scala
      discardedEdifices: Vector[(EdificeId, PositionedLocation)],
```

with:

```scala
      // Always empty: a replaced edifice is `tokenEdifice`, a discard.
      discardedEdifices: Vector[(EdificeId, PositionedLocation)],
```

In `src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala`, replace:

```scala
    BuildOps((live, pending) => take(live, player, pending)))))
```

with:

```scala
    BuildOps((live, pending) => take(live, player, pending),
      restrictions = (_, _) => Vector(
        new DiscardRestrictions(catalog, player))))))
```

In `src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala`, replace:

```scala
import oathdigital.gameplay.actions.CardPlay
```

with:

```scala
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.operations.DiscardRestrictions
```

- [ ] **Step 4: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.DiscardRestrictionsSuite oathdigital.gameplay.DiscardRestrictionsCoverageSuite oathdigital.gameplay.CardPlayProcedureSuite oathdigital.gameplay.PlacementRulesSuite oathdigital.gameplay.powers.wake.HornedMaskSuite oathdigital.gameplay.powers.whenplayed.DazzleSuite oathdigital.gameplay.SearchProcedureSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS (86 tests in these suites and the ones they touch).

- [ ] **Step 5: Run the whole suite and the architecture check**

Run: `./sbtw test` and `python3 scripts/check-architecture.py`
Expected: PASS, and `architecture check passed`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: refuse every discard of a locked card or an active modifier

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```


### Task 4: A play to a site may discard first

**Files:**
- Modify: `CardPlay.scala`, `CardPlayProcedure.scala`
- Test: `SiteDiscardFirstSuite.scala`

**Interfaces:**
- Produces: `CardPlay.Choice(placement, replacements, replacementOptional: Boolean = false)`; `CardPlayProcedure.noReplacement: DecisionOption` (`Button("replace:none")`, label "Discard nothing"), offered first in the replacement decision when the discard is optional.
- Consumes: Task 2's `PlacementRules.siteDiscardFirst`, `PlacementTree.adjust`, and `PlacementFixture`; Task 3's `DiscardRestrictions`.

Ruling for the (slice 4) Mob power: a play to a site may first discard one card of the site's card list, at any capacity. With room the discard is optional, so the replacement decision gains a "discard nothing" option. Without room it is required, and the rule that a full site accepts only a card matching its homeland edifice no longer applies. The discard is the standard one: the card goes facedown to the next region's discard, favor returns to its suit bank and secrets return to the player facedown. What may be discarded is decided by Task 3: a locked card, an intact edifice and an active modifier are not offered; a ruined edifice is. No real power sets `siteDiscardFirst` in this slice, so the suite uses a test power.

- [ ] **Step 1: Write the tests**

Create `src/test/scala/oathdigital/gameplay/SiteDiscardFirstSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.{CardPlay, PlacementRules}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._

/** `PlacementRules.siteDiscardFirst`: a play to a site may first discard one
  * card of the site's card list, at any capacity. No real power sets it yet
  * (the People's Favor banner face does), so a test double does.
  */
class SiteDiscardFirstSuite extends munit.FunSuite {
  import PlacementFixture._

  private val powers = WalkerPowers(Vector(discardFirst))
  private val noReplacement = CardPlayProcedure.noReplacement.ref
  private def choices(ready: ReadyGame, actor: PlayerId, card: DenizenId,
      rules: PlacementRules): Option[CardPlay.Choice] =
    CardPlay.legalChoices(catalog, ready, actor, card,
      CardPlay.Origin.TemporaryHand, rules)
      .find(_.placement.isInstanceOf[SearchPlacement.Site])

  /** Chooses "play at site" and returns the walk parked on the discard. */
  private def toDiscardDecision(ready: ReadyGame, actor: PlayerId,
      card: DenizenId): (Operation, PendingTree) = {
    val tree = build(ready, actor, card)
    val placed = answer(ready, tree, park(ready, tree, powers), powers,
      decisionId(card, "place"), DecisionOptionRef.Button("site"), actor)
      .asInstanceOf[WalkerOutcome.Parked]
    (tree, placed.tree)
  }

  test("a site with room offers a discard only under the permission") {
    val Vector(card, kept, other) = plain(initialReady).take(3)
    val (ready, actor, siteId) = staged(card, Vector(denizen(kept), denizen(other)))
    val capacity = catalog.sites.find(_.id == siteId).get.capacity
    assert(capacity > 2, s"the pawn site must have room, capacity $capacity")
    val plainChoice = choices(ready, actor, card, PlacementRules.default).get
    assertEquals(plainChoice.replacements, Vector.empty)
    assert(!plainChoice.replacementOptional)
    val allowed = choices(ready, actor, card,
      PlacementRules.default.withSiteDiscardFirst).get
    assertEquals(allowed.replacements.toSet, Set[CardId](kept, other))
    assert(allowed.replacementOptional)
  }

  test("declining the optional discard plays the card and discards nothing") {
    val Vector(card, kept) = plain(initialReady).take(2)
    val (ready, actor, siteId) = staged(card, Vector(denizen(kept)))
    val (tree, asked) = toDiscardDecision(ready, actor, card)
    assertEquals(options(ready, tree, asked, powers),
      Vector(noReplacement, DecisionOptionRef.Denizen(kept)))
    val done = answer(ready, tree, asked, powers, decisionId(card, "replace"),
      noReplacement, actor).asInstanceOf[WalkerOutcome.Finished].treeless
    assertEquals(done.game.current.map.sites(siteId).denizens.map(_.id),
      Vector[CardId](kept, card))
    assertEquals(done.game.current.temporaryHands(actor), Vector.empty)
  }

  test("choosing a site card discards it with the standard returns") {
    val card = plain(initialReady).head
    val kept = plain(initialReady).find(id =>
      catalog.suitOf(id) != catalog.suitOf(card)).get
    val suit = catalog.suitOf(kept).get
    val (ready, actor, siteId) = staged(card, Vector(denizen(kept, Tokens(1, 1))))
    val (tree, asked) = toDiscardDecision(ready, actor, card)
    val done = answer(ready, tree, asked, powers, decisionId(card, "replace"),
      DecisionOptionRef.Denizen(kept), actor)
      .asInstanceOf[WalkerOutcome.Finished].treeless
    val region = CardPlay.nextRegion(done.game.current.map.regionOf(siteId).get)
    assertEquals(done.game.current.map.sites(siteId).denizens.map(_.id),
      Vector[CardId](card))
    assertEquals(done.game.current.commonCards.discard(region).last, kept)
    // The favor on the discarded card returns to its suit bank, its secret to
    // the player facedown.
    assertEquals(done.banks.favor.getOrElse(suit, 0),
      ready.banks.favor.getOrElse(suit, 0) + 1)
    assertEquals(actorOf(done).board.faceDownSecrets,
      actorOf(ready).board.faceDownSecrets + 1)
  }

  test("a full site with no matching homeland accepts the play only with a " +
      "discard, and only under the permission") {
    val cards = plain(initialReady)
    val card = cards.head
    val (_, _, probeSite) = staged(card, Vector.empty)
    val capacity = catalog.sites.find(_.id == probeSite).get.capacity
    val fillers = cards.tail.filter(id =>
      catalog.suitOf(id) != catalog.suitOf(card)).take(capacity)
    assertEquals(fillers.size, capacity)
    val (ready, actor, siteId) = staged(card, fillers.map(denizen(_)))
    assertEquals(choices(ready, actor, card, PlacementRules.default), None)
    val site = choices(ready, actor, card,
      PlacementRules.default.withSiteDiscardFirst).get
    assertEquals(site.replacements.toSet, fillers.toSet[CardId])
    assert(!site.replacementOptional)
    val (tree, asked) = toDiscardDecision(ready, actor, card)
    assert(!options(ready, tree, asked, powers).contains(noReplacement))
    val done = answer(ready, tree, asked, powers, decisionId(card, "replace"),
      DecisionOptionRef.Denizen(fillers.head), actor)
      .asInstanceOf[WalkerOutcome.Finished].treeless
    assertEquals(done.game.current.map.sites(siteId).denizens.size, capacity)
    assert(done.game.current.map.sites(siteId).denizens.exists(_.id == card))
  }

  test("an intact edifice is never offered for the discard: it is locked") {
    val Vector(card, kept) = plain(initialReady).take(2)
    val hall = EdificeId("E16")
    val (built, actor, siteId) = staged(card, Vector(denizen(kept),
      EdificeState(hall, EdificeSide.Intact, Tokens.empty)))
    val ready = ruledByActor(built, siteId)
    val site = choices(ready, actor, card,
      PlacementRules.default.withSiteDiscardFirst).get
    assertEquals(site.replacements, Vector[CardId](kept))
  }

  test("a ruined edifice may be discarded, and it goes back to the edifice deck") {
    val Vector(card, kept) = plain(initialReady).take(2)
    val hall = EdificeId("E16")
    val ruined = DecisionOptionRef.Button("replace:edifice:E16")
    val (built, actor, siteId) = staged(card, Vector(denizen(kept),
      EdificeState(hall, EdificeSide.Ruined, Tokens.empty)))
    val ready = ruledByActor(built, siteId)
    val (tree, asked) = toDiscardDecision(ready, actor, card)
    assertEquals(options(ready, tree, asked, powers).toSet,
      Set[DecisionOptionRef](noReplacement, DecisionOptionRef.Denizen(kept),
        ruined))
    val done = answer(ready, tree, asked, powers, decisionId(card, "replace"),
      ruined, actor).asInstanceOf[WalkerOutcome.Finished].treeless
    assertEquals(done.game.current.map.sites(siteId).denizens.map(_.id),
      Vector[CardId](kept, card))
    assertEquals(done.game.current.commonCards.edificeDeck.last, hall)
  }

  test("the permission composes with an adviser limit through the walker") {
    val Vector(card, kept, first, second) = plain(initialReady).take(4)
    val (built, actor, _) = staged(card, Vector(denizen(kept)))
    val held = Vector(first, second)
    val current = built.game.current
    val ready = built.updateCurrent(_.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(held.contains)),
      players = current.players.map(p => if (p.player == actor)
        p.copy(advisers = held.map(id => DenizenState(id,
          Orientation.FaceDown, Tokens.empty))) else p)))
    val both = WalkerPowers(Vector(discardFirst, limitTwo))
    val tree = build(ready, actor, card)
    val parked = park(ready, tree, both)
    // A limit of two with two advisers held: a faceup adviser needs a discard.
    val faceup = answer(ready, tree, parked, both, decisionId(card, "place"),
      DecisionOptionRef.Button("adviser-faceup"), actor)
      .asInstanceOf[WalkerOutcome.Parked]
    assertEquals(options(ready, tree, faceup.tree, both).toSet,
      Set[DecisionOptionRef](DecisionOptionRef.Denizen(first),
        DecisionOptionRef.Denizen(second)))
    // And the site still offers the optional discard.
    val site = answer(ready, tree, parked, both, decisionId(card, "place"),
      DecisionOptionRef.Button("site"), actor).asInstanceOf[WalkerOutcome.Parked]
    assertEquals(options(ready, tree, site.tree, both).head, noReplacement)
  }

  test("without the permission a site with room asks no discard") {
    val Vector(card, kept) = plain(initialReady).take(2)
    val (ready, actor, siteId) = staged(card, Vector(denizen(kept)))
    val none = WalkerPowers.empty
    val tree = build(ready, actor, card)
    val done = answer(ready, tree, park(ready, tree, none), none,
      decisionId(card, "place"), DecisionOptionRef.Button("site"), actor)
      .asInstanceOf[WalkerOutcome.Finished].treeless
    assertEquals(done.game.current.map.sites(siteId).denizens.map(_.id),
      Vector[CardId](kept, card))
  }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, for example `value noReplacement is not a member of object oathdigital.gameplay.actions.cardplay.CardPlayProcedure`.

- [ ] **Step 3: Implement**

In `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`, replace:

```scala
  final case class Choice(placement: SearchPlacement,
      replacements: Vector[CardId])
```

with:

```scala
  /** One legal placement. `replacements` are the cards the play may discard
    * first. They are required when the placement is otherwise impossible, and
    * `replacementOptional` says the play is also legal with no discard (a
    * site with room, under `PlacementRules.siteDiscardFirst`).
    */
  final case class Choice(placement: SearchPlacement,
      replacements: Vector[CardId], replacementOptional: Boolean = false)
```

In `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`, replace:

```scala
      val replacements = if (direct) Vector.empty else candidateIds.filter { id =>
```

with:

```scala
      // A play to a site may be preceded by a discard even where it has room.
      val optional = direct && rules.siteDiscardFirst &&
        placement.isInstanceOf[SearchPlacement.Site]
      val replacements = if (direct && !optional) Vector.empty
      else candidateIds.filter { id =>
```

In `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`, replace:

```scala
      Option.when(direct || replacements.nonEmpty)(Choice(placement, replacements))
```

with:

```scala
      Option.when(direct || replacements.nonEmpty)(Choice(placement,
        replacements, replacementOptional = optional && replacements.nonEmpty))
```

In `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`, replace:

```scala
  private def validateSiteReplacement(catalog: ExecutableCatalog, siteId: SiteId,
      site: SiteState, suit: Suit, replace: Option[CardId])
      : Either[OathViolation, Option[SiteDenizenState]] = {
    val capacity = catalog.sites.find(_.id == siteId).map(_.capacity).getOrElse(0)
    val full = site.denizens.size >= capacity
    if (!full && replace.isEmpty) Right(None)
```

with:

```scala
  private def validateSiteReplacement(catalog: ExecutableCatalog, siteId: SiteId,
      site: SiteState, suit: Suit, replace: Option[CardId],
      rules: PlacementRules)
      : Either[OathViolation, Option[SiteDenizenState]] = {
    val capacity = catalog.sites.find(_.id == siteId).map(_.capacity).getOrElse(0)
    val full = site.denizens.size >= capacity
    if (rules.siteDiscardFirst) replace match {
      // Any site, at any capacity: the discard is optional with room and
      // required without, and it may name any card of the site's card list.
      // `DiscardRestrictions` decide what may actually be discarded: a locked
      // card, an intact edifice and an active modifier may not.
      case None if full => Left(InvalidSearchPlacement(
        "a full site requires a site-card discard"))
      case None => Right(None)
      case Some(id) => site.denizens.find(_.id == id).toRight(
        InvalidSearchPlacement("replacement card is not at the site"))
        .map(Some(_))
    }
    else if (!full && replace.isEmpty) Right(None)
```

In `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`, replace:

```scala
        replacement <- validateSiteReplacement(catalog, siteId, site,
          definition.suit, replace)
```

with:

```scala
        replacement <- validateSiteReplacement(catalog, siteId, site,
          definition.suit, replace, rules)
```

In `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`, replace:

```scala
  private val adviserFaceDown = DecisionOptionRef.Button("adviser-facedown")
```

with:

```scala
  private val adviserFaceDown = DecisionOptionRef.Button("adviser-facedown")

  /** The option that plays to a site without discarding a site card first,
    * offered only when `PlacementRules.siteDiscardFirst` makes the discard
    * optional.
    */
  val noReplacement: DecisionOption = DecisionOption.Button(
    DecisionOptionRef.Button("replace:none"), "Discard nothing")
```

In `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`, replace:

```scala
        (ref, choice.placement,
          choice.replacements.map(id => replacementOption(id) -> id))
      }
      val options = candidates.map { case (ref, _, _) =>
```

with:

```scala
        (ref, choice.placement,
          choice.replacements.map(id => replacementOption(id) -> id),
          choice.replacementOptional)
      }
      val options = candidates.map { case (ref, _, _, _) =>
```

In `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`, replace:

```scala
          case (_, placement, replacements) =>
```

with:

```scala
          case (_, placement, replacements, optional) =>
```

In `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`, replace:

```scala
              Decide(replacementId, actor, DecisionQuery.ChooseOne(
                replacements.map(_._1),
                heading = Some("Choose a card to discard"))))
```

with:

```scala
              Decide(replacementId, actor, DecisionQuery.ChooseOne(
                (if (optional) Vector(noReplacement) else Vector.empty) ++
                  replacements.map(_._1),
                heading = Some("Choose a card to discard"))))
```

In `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`, replace:

```scala
              }.flatMap(value => replacements.find(_._1.ref == value)
                .map(_._2)).toRight(OathViolation.InvalidSearchPlacement(
                  "replacement was not selected")).map { id => placement match {
                    case _: SearchPlacement.Site => SearchPlacement.Site(Some(id))
                    case value: SearchPlacement.Adviser =>
                      value.copy(replace = Some(id))
                    case SearchPlacement.Discard => SearchPlacement.Discard
                  }}
```

with:

```scala
              }.toRight(OathViolation.InvalidSearchPlacement(
                "replacement was not selected")).flatMap { value =>
                if (optional && value == noReplacement.ref) Right(placement)
                else replacements.find(_._1.ref == value).map(_._2)
                  .toRight(OathViolation.InvalidSearchPlacement(
                    "replacement was not selected")).map { id => placement match {
                      case _: SearchPlacement.Site =>
                        SearchPlacement.Site(Some(id))
                      case value: SearchPlacement.Adviser =>
                        value.copy(replace = Some(id))
                      case SearchPlacement.Discard => SearchPlacement.Discard
                    }}
              }
```

- [ ] **Step 4: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.SiteDiscardFirstSuite oathdigital.gameplay.PlacementRulesSuite oathdigital.gameplay.CardPlayProcedureSuite oathdigital.gameplay.CardPlayHooksSuite oathdigital.gameplay.SearchProcedureSuite oathdigital.gameplay.powers.rest.SilverTongueSuite oathdigital.gameplay.PendingWalkerRulesSuite oathdigital.application.PendingWalkerInvariantSuite"`
Expected: PASS (55 tests in these suites and the ones they touch).

- [ ] **Step 5: Run the whole suite and the architecture check**

Run: `./sbtw test` and `python3 scripts/check-architecture.py`
Expected: PASS, and `architecture check passed`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: let a play to a site discard a site card first

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```


- [ ] **Step: Record sub-slice 2a**

Follow "Recording a sub-slice" below for 2a, then run `./sbtw test`, `python3 scripts/check-architecture.py` and `python3 scripts/check-markdown-links.py`, and commit with `docs: record slice 2a`.

---

## Sub-slice 2b: Modifier kit and card-play triggers

### Task 5: The procedure on `PowerCtx` (E9)

**Files:**
- Modify: `powerresolver/ContributingPower.scala`, `walker/ProcedureWalker.scala`, `walker/WalkerPowerGather.scala`, `OathRulesWalker.scala`
- Test: `EnclosingProcedureSuite.scala`

**Interfaces:**
- Produces: `PowerCtx.procedure: Option[ProcedureRef] = None` (a trailing parameter, so no existing construction changes). It is the procedure of the parked position a command resumes, the procedure a modifier is offered for at selection, and `None` for the command that starts a procedure (which has not recorded one yet).
- Consumes: nothing new.

`WalkCtx` captures the procedure from the state before the walker strips it, and `WalkerPowerGather.applyWindow` passes it to the `PowerCtx` it builds. The places that re-fold a window to resolve a parked path read it from the (unstripped) state they are given. A window that is folded in the first command of a procedure sees `None`; Welcoming Party and Knights Errant hook windows that only a resumed command walks (a card is placed after a decision, a Campaign is chosen by an answer).

- [ ] **Step 1: Write the tests**

Create `src/test/scala/oathdigital/gameplay/EnclosingProcedureSuite.scala`:

```scala
package oathdigital.gameplay

import scala.collection.mutable.ArrayBuffer

import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, PowerCtx}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** `PowerCtx.procedure`: the procedure a window is walked for. */
class EnclosingProcedureSuite extends munit.FunSuite {
  private val seen = ArrayBuffer.empty[(PowerWindow, Option[ProcedureRef])]

  /** A power that records what it is asked at three Muster windows. */
  private val probe: ContributingPower = new ContributingPower {
    def id: PowerId = PowerId("test.enclosing-procedure")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    override def resolution: PowerResolution = PowerResolution.PlayerSelected
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.MusterSourceSelection -> Vector.empty,
      PowerWindow.MusterCost -> Vector.empty)
    override def applicable(ctx: PowerCtx): Boolean = {
      seen += ctx.window -> ctx.procedure
      true
    }
  }

  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowers(Vector(probe)))

  private def staged: ReadyGame = EconomyFixture.act()
  private def actor: PlayerId = staged.game.current.turn.activePlayer

  test("a modifier is selected for the procedure that will run it") {
    seen.clear()
    rules.offerableWalkerPowers(staged, actor, ActionRef.Muster)
    assertEquals(seen.toVector, Vector[(PowerWindow, Option[ProcedureRef])](
      PowerWindow.MusterModifierSelection -> Some(ActionRef.Muster)))
  }

  test("the command that starts a procedure has not recorded it yet, and a " +
      "resume walks its windows for it") {
    seen.clear()
    val started = rules.startWalker(Ready(staged), ActionRef.Muster, actor,
      Vector(probe.id)).toOption.get
    assert(seen.contains(PowerWindow.MusterSourceSelection -> None), seen.toString)
    seen.clear()
    rules.resolveWalker(started.state, actor, MusterProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(
        EconomyFixture.plainId))).toOption.get
    assert(seen.contains(PowerWindow.MusterCost -> Some(ActionRef.Muster)),
      seen.toString)
  }

  test("a context built without one names none") {
    val ctx = PowerCtx(staged, actor, probe.source, PowerWindow.MusterCost,
      Vector.empty, Sequence(Vector.empty))
    assertEquals(ctx.procedure, None)
  }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, for example `value procedure is not a member of oathdigital.gameplay.powerresolver.PowerCtx`.

- [ ] **Step 3: Implement**

In `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`, replace:

```scala
import oathdigital.model.{DecisionOptionRef, OathViolation, Operation, PlayerId, PowerId, PowerResolution, PowerWindow, ReadyGame, RuleSourceRef}
```

with:

```scala
import oathdigital.model.{DecisionOptionRef, OathViolation, Operation, PlayerId, PowerId, PowerResolution, PowerWindow, ProcedureRef, ReadyGame, RuleSourceRef}
```

In `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`, replace:

```scala
    /** Exact windowed operation a contribution is being collected for. */
    operation: Operation
)
```

with:

```scala
    /** Exact windowed operation a contribution is being collected for. */
    operation: Operation,
    /** The procedure the window is walked for, when it is known: the
      * procedure of the parked position a command resumes, or the one a
      * modifier is being selected for. `None` for the command that starts a
      * procedure, which has not recorded one yet.
      */
    procedure: Option[ProcedureRef] = None
)
```

In `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`, replace:

```scala
      dice: WalkerDice
  )
```

with:

```scala
      dice: WalkerDice,
      /** The procedure of the parked position being resumed. */
      procedure: Option[oathdigital.model.ProcedureRef]
  )
```

In `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`, replace:

```scala
    walk(action, WalkCtx(base, Vector.empty, activePlayer, answered, powers, dice),
```

with:

```scala
    walk(action, WalkCtx(base, Vector.empty, activePlayer, answered, powers, dice,
      state.game.current.walkerProcedure),
```

In `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`, replace (every occurrence, 2 in all):

```scala
      state.game.current.turn.activePlayer, pending.answered, powers, dice),
```

with:

```scala
      state.game.current.turn.activePlayer, pending.answered, powers, dice,
      state.game.current.walkerProcedure),
```

In `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`, replace:

```scala
    val (folded, order) = WalkerPowerGather.applyWindow(window, operation, ctx.state,
      ctx.activePlayer, ctx.powers, path, children)
```

with:

```scala
    val (folded, order) = WalkerPowerGather.applyWindow(window, operation, ctx.state,
      ctx.activePlayer, ctx.powers, path, children, ctx.procedure)
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`, replace:

```scala
      activePlayer: PlayerId, powers: WalkerPowers, path: Vector[String],
      ops: Vector[Operation]): (Vector[Operation], Vector[PowerId]) =
```

with:

```scala
      activePlayer: PlayerId, powers: WalkerPowers, path: Vector[String],
      ops: Vector[Operation], procedure: Option[oathdigital.model.ProcedureRef])
      : (Vector[Operation], Vector[PowerId]) =
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`, replace:

```scala
          PowerCtx(state, activePlayer, power.source, w, path, operation)
```

with:

```scala
          PowerCtx(state, activePlayer, power.source, w, path, operation,
            procedure)
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`, replace:

```scala
      power => PowerCtx(state, activePlayer, power.source, window, path, operation)
```

with:

```scala
      power => PowerCtx(state, activePlayer, power.source, window, path,
        operation, state.game.current.walkerProcedure)
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`, replace:

```scala
        val (folded, _) = applyWindow(branch.window, branch, state,
          activePlayer, powers, path, selected)
```

with:

```scala
        val (folded, _) = applyWindow(branch.window, branch, state,
          activePlayer, powers, path, selected, state.game.current.walkerProcedure)
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`, replace:

```scala
            val (folded, _) = applyWindow(Some(w), leaf, state,
              activePlayer, powers, path, Vector(leaf))
```

with:

```scala
            val (folded, _) = applyWindow(Some(w), leaf, state,
              activePlayer, powers, path, Vector(leaf),
              state.game.current.walkerProcedure)
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`, replace:

```scala
        val (folded, _) = applyWindow(composite.window, composite, state,
          activePlayer, powers, path, composite.children)
```

with:

```scala
        val (folded, _) = applyWindow(composite.window, composite, state,
          activePlayer, powers, path, composite.children,
          state.game.current.walkerProcedure)
```

In `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala`, replace:

```scala
        power.applicable(PowerCtx(ready, actor, power.source, window,
          Vector.empty, Sequence(Vector.empty, Some(window)))))
```

with:

```scala
        power.applicable(PowerCtx(ready, actor, power.source, window,
          Vector.empty, Sequence(Vector.empty, Some(window)),
          Some(procedure))))
```

- [ ] **Step 4: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.EnclosingProcedureSuite oathdigital.gameplay.ProcedureWalkerSuite oathdigital.gameplay.ContributingPowerSuite oathdigital.gameplay.ContributionCollectorSuite oathdigital.gameplay.OathRulesWalkerPowerSuite oathdigital.gameplay.OptionRestrictionSuite oathdigital.gameplay.CatacombsContributionSuite oathdigital.gameplay.TravelSitePowersSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS (121 tests in these suites and the ones they touch).

- [ ] **Step 5: Run the whole suite and the architecture check**

Run: `./sbtw test` and `python3 scripts/check-architecture.py`
Expected: PASS, and `architecture check passed`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: name the enclosing procedure on PowerCtx

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```


### Task 6: Selected modifiers pay at the start, and their costs are validated together

**Files:**
- Modify: `powerresolver/ContributingPower.scala`, `OathRulesWalker.scala`, `powers/recover/CatacombsContribution.scala`
- Test: `SelectionPaymentsSuite.scala`

**Interfaces:**
- Produces: `ContributingPower.selectionPayments(ready: ReadyGame, actor: PlayerId): Vector[CoreOperation]` (free by default); `OathRules.validateModifiers` refuses, with `InvalidEventOrder("the selected modifiers cannot all be paid together: ...")`, a selection whose payments cannot all be made in order.
- Consumes: `OperationPipeline.run`, `OperationPolicy.Permissive`, `Costs.onCard`.

The product owner's rule is that a modifier's payment is made at the very start of its action, so the validator can catch an unpayable combination up front. This task is the engine half: a power states its payment as operations, and the command that starts an action dry-runs every selected power's payments together against the state before it (`OperationPipeline.run` applies them one after the other and discards the result; `report` would not do, fact 20). Catacombs, the one existing modifier with a cost, already paid at the start of Recover as the first node of its transform, and now states the same payment. Task 7 makes the kit state and place the payment for every other modifier.

The refusal happens in `validateModifiers`, which `startWalker` calls before anything is walked or recorded, so nothing is spent and no journal entry exists. The pre-start preview (`GameApplicationService.preview`) still offers each modifier on its own, because the client picks the set; the combined check is the start command's.

- [ ] **Step 1: Write the tests**

Create `src/test/scala/oathdigital/gameplay/SelectionPaymentsSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Selecting modifiers refuses a combination the player cannot pay, at
  * selection, because every selected modifier pays at the start of its action.
  */
class SelectionPaymentsSuite extends munit.FunSuite {
  private val actor = EconomyFixture.act().game.current.turn.activePlayer

  /** A selectable Muster modifier that burns `secrets` secrets when selected. */
  private def burning(name: String, secrets: Int): ContributingPower =
    new ContributingPower {
      def id: PowerId = PowerId(name)
      def source: RuleSourceRef = RuleSourceRef.GameRule(name)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
      override def resolution: PowerResolution = PowerResolution.PlayerSelected
      override def selectionPayments(ready: ReadyGame, player: PlayerId) =
        Vector(PayCost(player, Location.SharedBank,
          Cost(secretBurnt = secrets)))
    }
  private val a = burning("test.burns-a", 1)
  private val b = burning("test.burns-b", 1)
  private val free: ContributingPower = new ContributingPower {
    def id: PowerId = PowerId("test.free")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def resolution: PowerResolution = PowerResolution.PlayerSelected
  }

  private def muster(secrets: Int, selected: ContributingPower*)
      : Either[OathViolation, OathTransition] = {
    val rules = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowers(Vector(a, b, free)))
    rules.startWalker(Ready(EconomyFixture.act(secrets = secrets)),
      ActionRef.Muster, actor, selected.map(_.id).toVector)
  }

  test("a selection whose payments can all be made is accepted") {
    assert(muster(2, a, b).isRight)
    assert(muster(1, a).isRight)
    assert(muster(1, free).isRight)
  }

  test("two payments that need the only secret are refused at selection") {
    val refused = muster(1, a, b)
    assert(refused.left.toOption.exists(_ match {
      case OathViolation.InvalidEventOrder(detail) =>
        detail.contains("cannot all be paid together")
      case _ => false
    }), refused.toString)
  }

  test("each payment alone is affordable, so the refusal is about the pair") {
    assert(muster(1, a).isRight)
    assert(muster(1, b).isRight)
  }

  test("a free power adds nothing to the payments") {
    assert(muster(1, a, free).isRight)
  }

  test("Catacombs states its secret as a selection payment") {
    val setup = new FirstGameSetupRules(catalog)
    val fixture = CatacombsContributionSuite.reliclessSite(setup, secrets = 1)
    val power = oathdigital.gameplay.powers.recover.CatacombsContribution
      .forCatalog(catalog).get
    assertEquals(power.selectionPayments(fixture.ready, fixture.actor).size, 1)
    val rules = new OathRules(catalog, walkerPowerCatalog = WalkerPowers(
      Vector(power)))
    assert(rules.startWalker(Ready(fixture.ready), ActionRef.Recover,
      fixture.actor, Vector(power.id)).isRight)
  }

  test("Catacombs with no faceup secret is refused at selection, not mid-action") {
    val setup = new FirstGameSetupRules(catalog)
    val fixture = CatacombsContributionSuite.reliclessSite(setup, secrets = 0)
    val power = oathdigital.gameplay.powers.recover.CatacombsContribution
      .forCatalog(catalog).get
    val rules = new OathRules(catalog, walkerPowerCatalog = WalkerPowers(
      Vector(power)))
    val refused = rules.startWalker(Ready(fixture.ready), ActionRef.Recover,
      fixture.actor, Vector(power.id))
    assert(refused.left.toOption.exists(_.toString.contains(
      "cannot all be paid together")), refused.toString)
  }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, for example `value selectionPayments is not a member of oathdigital.gameplay.powers.recover.CatacombsContribution`.

- [ ] **Step 3: Implement**

In `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`, replace:

```scala
import oathdigital.model.{DecisionOptionRef,
```

with:

```scala
import oathdigital.model.{CoreOperation, DecisionOptionRef,
```

In `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`, replace:

```scala
  def applicable(ctx: PowerCtx): Boolean = true
```

with:

```scala
  def applicable(ctx: PowerCtx): Boolean = true
  /** What selecting this power pays, as the operations its action runs first.
    * A command that selects several powers dry-runs all of their payments
    * together (`OathRules.validateModifiers`), so a combination the player
    * cannot pay is refused at selection. Free by default.
    */
  def selectionPayments(ready: ReadyGame, actor: PlayerId)
      : Vector[CoreOperation] = Vector.empty
```

In `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala`, replace:

```scala
import oathdigital.gameplay.powerresolver.{ContributingPower, PowerCtx, PhasePowers}
```

with:

```scala
import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.gameplay.powerresolver.{ContributingPower, PowerCtx, PhasePowers}
```

In `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala`, replace:

```scala
          case (left, _) => left
        }
    }
```

with:

```scala
          case (left, _) => left
        }.flatMap(_ => requirePayable(ready, actor, modifiers))
    }

  /** Every selected power pays at the start of the action, so the payments
    * are dry-run together against the state before the command: one that the
    * player cannot make once the others are made (two costs that need the
    * only secret) refuses the selection, before anything is recorded.
    */
  private def requirePayable(ready: ReadyGame, actor: PlayerId,
      modifiers: Vector[PowerId]): Either[OathViolation, Unit] = {
    val payments = walkerPowerCatalog.powers
      .filter(power => modifiers.contains(power.id))
      .flatMap(_.selectionPayments(ready, actor))
    if (payments.isEmpty) Right(())
    else OperationPipeline.run(ready, payments, OperationPolicy.Permissive)(
      Right(_)).left.map(violation => InvalidEventOrder(
      "the selected modifiers cannot all be paid together: " +
        violation)).map(_ => ())
  }
```

In `src/main/scala/oathdigital/gameplay/powers/recover/CatacombsContribution.scala`, replace:

```scala
  def contributions: Map[PowerWindow, Vector[Contribution]] =
```

with:

```scala
  override def selectionPayments(ready: ReadyGame, actor: PlayerId)
      : Vector[CoreOperation] =
    Vector(Costs.onCard(actor, cardId, Cost(secret = 1), catalog))
  def contributions: Map[PowerWindow, Vector[Contribution]] =
```

- [ ] **Step 4: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.SelectionPaymentsSuite oathdigital.gameplay.CatacombsContributionSuite oathdigital.gameplay.OathRulesWalkerPowerSuite oathdigital.gameplay.RecoverEligibilitySuite oathdigital.gameplay.ContributingPowerSuite oathdigital.application.PendingWalkerInvariantSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS (70 tests in these suites and the ones they touch).

- [ ] **Step 5: Run the whole suite and the architecture check**

Run: `./sbtw test` and `python3 scripts/check-architecture.py`
Expected: PASS, and `architecture check passed`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: validate the payments of every selected modifier together

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```


### Task 7: `SelectedModifier`, `CatalogCards` and the card-play triggers

**Files:**
- Create: `powers/SelectedModifier.scala`, `powers/CatalogCards.scala`, `powers/cardplay/{WildCry,WelcomingParty,Gossip,CardPlayTriggers}.scala`
- Modify: `powers/WalkerPowerCatalog.scala`
- Test: `powers/SearchFixture.scala`, `powers/SelectedModifierSuite.scala`, `powers/cardplay/{WildCry,WelcomingParty,Gossip}Suite.scala`

**Interfaces:**
- Produces: `trait SelectedModifier extends ContributingPower` with `catalog`, `cardId: CardId`, `actions: Set[MajorActionType]`, `cost: Cost = Cost.free`, `effects: Map[PowerWindow, Vector[Contribution]]` (a power states this, and the kit's `contributions` adds the payment), `appliesAt(ctx): Boolean = true`, `selectable(ready, actor): Boolean`, `selectionPayments` and a protected `payment(actor): CoreOperation`; `SelectedModifier.selectionAction(window)` and `SelectedModifier.eligibility(action)`. `CatalogCards.denizen/relic/edifice(catalog, power): Option[...]`. `WildCry`, `WelcomingParty`, `Gossip` (each with `id`, `forCatalog(catalog)`), `CardPlayTriggers.forCatalog(catalog)`. Test support `SearchFixture` (`rules`, `staged(top, supply)`, `denizensOf(suit)`, `start`, `keep`, `place`, `replace`, `play`, `playFacedown`, `after`).
- Consumes: Task 1's `CardPlayedFaceup`/`CardPlayedFacedown` and windows; Task 3's `CardStaging` and active-modifier rule; Task 5's `PowerCtx.procedure`; Task 6's `selectionPayments`; `PowerAccess.locate`, `Costs.affordable`, `Costs.onCard`, `CatalogResolution.of`, `PlayerFacts.forceKind`.

`SelectedModifier` is the kit the other selected modifiers of this slice stand on (see fact 11). A power states what it does inside the walk as `effects`, by window, and the kit adds its payment: a modifier with a `cost` gets a `Transform` at its action's eligibility window (the root of the action's tree) that prepends the payment, so the cost is paid at the very start (Task 6's rule), and the same payment is its `selectionPayments`. Its `applicable` answers three questions by window: at a `*ModifierSelection` window "may the player select this now?" (its action, `PowerAccess.locate`, `Costs.affordable`); at the eligibility window a selected modifier always applies (the payment does not depend on the effect); elsewhere `appliesAt`, which must read only the node the power is hooked on, because the walker folds every window again on each resume.

- **Wild Cry** gains 1 Supply and 2 warbands when a beast denizen is played faceup. It excludes its own card, and is silent on a facedown play and a discard. It cannot be discarded while selected (Task 3), which its suite shows through a full adviser area.
- **Welcoming Party** ("If you play a denizen face up when first drawn, gain favor from the Hearth bank", product owner's text) gains 1 favor from the Hearth bank when a denizen is played faceup straight from the draw: the card comes from a Search's temporary hand (its origin) and goes to a site or becomes a faceup adviser. A card placed facedown does not trigger it, and neither does a card that was already a facedown adviser and is played faceup later by the Play-Facedown-Adviser action. The hook does not carry the origin, so the power reads `ctx.procedure` (Task 5): a card played by a Search came straight from its draw. A Vision is not a denizen and a card does not trigger on its own play.
- **Gossip** gives its holder 1 favor from the Discord bank when another player places an adviser facedown, and reads `CardPlayedFacedown`.

- [ ] **Step 1: Write the tests**

Create `src/test/scala/oathdigital/gameplay/powers/SearchFixture.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Drives a real Search through the rules, with the production walker powers,
  * for the suites of the modifiers and triggers that act on it. It builds on
  * `PowerFixture`; the first game deals only some cards, so a test names the
  * cards it needs and the fixture arranges the deck.
  */
object SearchFixture {
  import PowerFixture._

  val rules: OathRules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))

  def after(transition: OathTransition): ReadyGame =
    transition.state.asInstanceOf[Ready].value

  /** The pawn site with nothing at it (its cards go to the bottom of the world
    * deck, its edifice to the edifice deck), the world deck topped by `top`,
    * the actor in the Act phase with `supply` Supply.
    */
  def staged(top: Vector[WorldCardId], supply: Int = 5): ReadyGame = {
    val siteId = home(base)
    val ready = base.updateCurrent { current =>
      val site = current.map.sites(siteId)
      val cards = site.denizens.collect { case d: DenizenState => d.id }
      val edifices = site.denizens.collect { case e: EdificeState => e.id }
      current.copy(
        commonCards = current.commonCards.copy(
          worldDeck = top ++ (current.commonCards.worldDeck.filterNot(top.contains) ++ cards),
          edificeDeck = current.commonCards.edificeDeck ++ edifices),
        map = current.map.copy(sites = current.map.sites.updated(siteId,
          site.copy(denizens = Vector.empty))))
    }
    inPhase(withBoard(ready)(_.copy(supply = SupplyTrack(supply))), Phase.Act)
  }

  /** The plain denizens of `suit` (unrestricted, with no production walker
    * power of their own) that no player and no site holds: in the world deck,
    * or not dealt at all. `staged` puts a card that is not dealt on top of the
    * deck.
    */
  def denizensOf(suit: Suit): Vector[DenizenId] = {
    val index = CardIndex.from(base.game).toOption.get
    val powered = WalkerPowerCatalog.default(catalog).powers.map(_.id).toSet
    catalog.denizens.filter(d => d.suit == suit &&
      !d.powers.exists(power => powered(power.id)) &&
      d.restrictions == oathdigital.catalog.CardRestrictions.Unrestricted)
      .map(d => DenizenId(d.id.value)).filter(id =>
        index.get(id).forall(_.location.container ==
          CardContainer.Deck(CardDeck.World)))
  }

  /** Starts a world Search with `modifiers`. */
  def start(ready: ReadyGame, modifiers: Vector[PowerId] = Vector.empty)
      : Either[OathViolation, OathTransition] =
    rules.startWalker(Ready(ready), ActionRef.Search, actor, modifiers,
      Vector(DecisionOptionRef.Button("search:world")))

  private def refOf(card: WorldCardId): DecisionOptionRef = card match {
    case id: DenizenId => DecisionOptionRef.Denizen(id)
    case id: VisionId => DecisionOptionRef.Vision(id)
  }

  /** Keeps `kept` from the drawn hand and discards the others. */
  def keep(from: OathTransition, kept: WorldCardId)
      : Either[OathViolation, OathTransition] = {
    val drawn = after(from).game.current.temporaryHands(actor)
    if (drawn.size == 1) Right(from.copy(events = Vector.empty))
    else rules.resolveWalker(from.state, actor, SearchProcedure.cardDecisionId,
      DecisionAnswer.PartitionAnswer(DecisionPlacement(refOf(kept),
        SearchProcedure.keepKey) +: drawn.filterNot(_ == kept).map(card =>
        DecisionPlacement(refOf(card), SearchProcedure.discardKey))))
  }

  /** Places the card with the placement button. */
  def place(from: OathTransition, card: WorldCardId, button: String)
      : Either[OathViolation, OathTransition] =
    rules.resolveWalker(from.state, actor,
      s"cardplay.place.${card.kind}.${card.value}",
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button(button)))

  /** A whole Search: start, keep `kept`, place it with `button`. The events are
    * those of the whole Search, so a replay can start from `ready`.
    */
  def play(ready: ReadyGame, modifiers: Vector[PowerId], kept: WorldCardId,
      button: String): OathTransition = (for {
    started <- start(ready, modifiers)
    chosen <- keep(started, kept)
    placed <- place(chosen, kept, button)
  } yield placed.copy(events = started.events ++ chosen.events ++
    placed.events)).fold(error => throw new AssertionError(error.toString),
    identity)

  /** The Play-Facedown-Adviser action: the actor plays `card`, which they hold
    * as a facedown adviser, with `button`. The events are those of the whole
    * action.
    */
  def playFacedown(ready: ReadyGame, modifiers: Vector[PowerId],
      card: DenizenId, button: String): OathTransition = (for {
    started <- rules.startWalker(Ready(ready), ActionRef.PlayFacedownAdviser,
      actor, modifiers, Vector(DecisionOptionRef.Denizen(card)))
    placed <- place(started, card, button)
  } yield placed.copy(events = started.events ++ placed.events)).fold(
    error => throw new AssertionError(error.toString), identity)

  /** Answers the replacement decision of a play with `chosen`. */
  def replace(from: OathTransition, card: WorldCardId, chosen: CardId)
      : Either[OathViolation, OathTransition] =
    rules.resolveWalker(from.state, actor,
      s"cardplay.replace.${card.kind}.${card.value}",
      DecisionAnswer.ChooseOneAnswer(chosen match {
        case id: DenizenId => DecisionOptionRef.Denizen(id)
        case id: VisionId => DecisionOptionRef.Vision(id)
        case other => DecisionOptionRef.Button(
          s"replace:${other.kind}:${other.value}")
      }))
}
```

Create `src/test/scala/oathdigital/gameplay/powers/SelectedModifierSuite.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** The kit every selected modifier stands on: it is offered only for its own
  * action, only when the player may use its card and can pay for it, and it
  * applies inside the walk by asking only about the node it is hooked on.
  */
class SelectedModifierSuite extends munit.FunSuite {
  import PowerFixture._

  private val card = DenizenId("29")

  /** A test double on a catalog modifier id, so its resolution is the catalog's. */
  private final case class Probe(cardId: CardId, override val cost: Cost,
      actions: Set[MajorActionType], idValue: String = "denizen.tents")
      extends SelectedModifier {
    def catalog = oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
    def id: PowerId = PowerId(idValue)
    def effects: Map[PowerWindow, Vector[Contribution]] = Map.empty
  }

  private def ctx(ready: ReadyGame, power: SelectedModifier,
      window: PowerWindow): PowerCtx = PowerCtx(ready, actor, power.source,
    window, Vector.empty, Sequence(Vector.empty, Some(window)))

  private def selectable(ready: ReadyGame, power: SelectedModifier,
      window: PowerWindow = PowerWindow.TravelModifierSelection): Boolean =
    power.applicable(ctx(ready, power, window))

  private val travel: Set[MajorActionType] = Set(MajorActionType.Travel)
  private def free = Probe(card, Cost.free, travel)
  private def atHomeWith(cost: Int = 1) = withBoard(atHome(base, card))(
    _.copy(favor = cost))

  test("its resolution is read from the catalog") {
    assertEquals(free.resolution, PowerResolution.PlayerSelected)
    assertEquals(Probe(card, Cost.free, travel, "denizen.toll-roads").resolution,
      PowerResolution.Automatic)
  }

  test("it is offered for its own action and no other") {
    val ready = atHomeWith()
    assert(selectable(ready, free))
    assert(!selectable(ready, free, PowerWindow.SearchModifierSelection))
    assert(!selectable(ready, free, PowerWindow.MusterModifierSelection))
  }

  test("it is not offered when the player may not use its card") {
    assert(!selectable(base, free))
    val faceDown = withBoard(asAdviser(base, card, Orientation.FaceDown))(
      _.copy(favor = 1))
    assert(!selectable(faceDown, free))
    assert(selectable(withBoard(asAdviser(base, card))(_.copy(favor = 1)), free))
  }

  test("a cost must be payable: enough favor, and an empty card") {
    val priced = Probe(card, Cost(favor = 1), travel)
    assert(selectable(atHomeWith(1), priced))
    assert(!selectable(atHomeWith(0), priced))
    val occupied = atHomeWith(1).updateCurrent(c => c.copy(map = c.map.copy(
      sites = c.map.sites.updated(home(base), c.map.sites(home(base)).copy(
        denizens = c.map.sites(home(base)).denizens.map {
          case d: DenizenState if d.id == card => d.copy(tokens = Tokens(1, 0))
          case other => other
        })))))
    assert(!selectable(occupied, priced))
  }

  test("inside the walk it asks only about its own node") {
    val priced = Probe(card, Cost(favor = 1), travel)
    // No card and no favor, yet the fold at a walk window still applies it,
    // because a walk window must not depend on what the action spends.
    assert(priced.applicable(ctx(base, priced, PowerWindow.TravelCost)))
  }

  test("a cost is paid at the root of the action's tree, and only for a costed " +
      "modifier") {
    val priced = Probe(card, Cost(favor = 1), travel)
    val windows = priced.contributions.keySet
    assertEquals(windows, Set[PowerWindow](PowerWindow.TravelActionEligibility))
    assertEquals(free.contributions, Map.empty[PowerWindow, Vector[Contribution]])
  }

  test("at the action's eligibility window a selected modifier always applies") {
    val priced = Probe(card, Cost(favor = 1), travel)
    assert(priced.applicable(ctx(base, priced,
      PowerWindow.TravelActionEligibility)))
  }

  test("the payment is what selecting it pays, for the combined check") {
    val priced = Probe(card, Cost(favor = 1), travel)
    val ready = atHomeWith(1)
    assertEquals(priced.selectionPayments(ready, actor).size, 1)
    assertEquals(free.selectionPayments(ready, actor), Vector.empty)
  }

  test("a payment and an effect at the same window keep the payment first") {
    val both = new SelectedModifier {
      def catalog = oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
      def cardId: CardId = card
      def id: PowerId = PowerId("denizen.tents")
      def actions: Set[MajorActionType] = travel
      override def cost: Cost = Cost(favor = 1)
      def effects: Map[PowerWindow, Vector[Contribution]] = Map(
        PowerWindow.TravelActionEligibility ->
          Vector(oathdigital.gameplay.powerresolver.Transform((_, ops) => ops)))
    }
    assertEquals(both.contributions(PowerWindow.TravelActionEligibility).size, 2)
  }

  test("the catalog cards helper finds a power's card, or nothing") {
    assertEquals(CatalogCards.denizen(catalog, PowerId("denizen.tents")),
      Some(card))
    assertEquals(CatalogCards.relic(catalog, PowerId("relic.dragonskin-drum")),
      Some(RelicId("R20")))
    assertEquals(CatalogCards.edifice(catalog, PowerId("edifice.e28.ruined")),
      Some(EdificeId("E28")))
    assertEquals(CatalogCards.denizen(catalog, PowerId("denizen.nobody")), None)
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/cardplay/GossipSuite.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.{PowerFixture, SearchFixture, TargetsFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class GossipSuite extends munit.FunSuite {
  import PowerFixture._
  import SearchFixture._

  private val gossip = DenizenId("99")
  private val plain = denizensOf(Suit.Arcane).take(3)
  private val holder = TargetsFixture.others(base).head

  /** `holder` holds a faceup Gossip, and the actor Searches. */
  private def held(top: Vector[WorldCardId],
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    TargetsFixture.giveAdviser(SearchFixture.staged(top), holder, gossip,
      orientation)

  private def favor(ready: ReadyGame, id: PlayerId): Int =
    player(ready, id).board.favor
  private def discordBank(ready: ReadyGame): Int =
    ready.banks.favor.getOrElse(Suit.Discord, 0)

  test("Gossip is a registered persistent rule, so it is automatic") {
    val power = Gossip.forCatalog(catalog).get
    assertEquals(power.cardId, gossip)
    assertEquals(power.resolution, PowerResolution.Automatic)
    assert(WalkerPowerCatalogHas.gossip)
  }

  test("another player's facedown play gains the holder 1 favor from the " +
      "Discord bank") {
    val ready = held(plain)
    val done = play(ready, Vector.empty, plain.head, "adviser-facedown")
    val after = SearchFixture.after(done)
    assertEquals(favor(after, holder), favor(ready, holder) + 1)
    assertEquals(discordBank(after), discordBank(ready) - 1)
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events), after)
  }

  test("a facedown Vision counts too") {
    val ready = held(Vector(VisionRules.Faith))
    val after = SearchFixture.after(play(ready, Vector.empty,
      VisionRules.Faith, "adviser-facedown"))
    assertEquals(favor(after, holder), favor(ready, holder) + 1)
  }

  test("a faceup play and a discard gain nothing") {
    val ready = held(plain)
    Vector("site", "adviser-faceup", "discard").foreach { button =>
      val after = SearchFixture.after(play(ready, Vector.empty, plain.head,
        button))
      assertEquals(favor(after, holder), favor(ready, holder), button)
    }
  }

  test("the holder's own facedown play gains nothing") {
    val ready = asAdviser(SearchFixture.staged(plain), gossip)
    val after = SearchFixture.after(play(ready, Vector.empty, plain.head,
      "adviser-facedown"))
    assertEquals(favor(after, actor), favor(ready, actor))
    assertEquals(discordBank(after), discordBank(ready))
  }

  test("a facedown Gossip is not active") {
    val ready = held(plain, Orientation.FaceDown)
    val after = SearchFixture.after(play(ready, Vector.empty, plain.head,
      "adviser-facedown"))
    assertEquals(favor(after, holder), favor(ready, holder))
  }

  test("an empty Discord bank gives nothing") {
    val ready = held(plain).copy(banks = base.banks.copy(favor =
      base.banks.favor.updated(Suit.Discord, 0)))
    val after = SearchFixture.after(play(ready, Vector.empty, plain.head,
      "adviser-facedown"))
    assertEquals(favor(after, holder), favor(ready, holder))
  }

  private object WalkerPowerCatalogHas {
    def gossip: Boolean = oathdigital.gameplay.powers.WalkerPowerCatalog
      .default(catalog).powers.exists(_.id == Gossip.id)
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/cardplay/WelcomingPartySuite.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class WelcomingPartySuite extends munit.FunSuite {
  import PowerFixture._
  import SearchFixture._

  private val party = DenizenId("50")
  private val modifiers = Vector(WelcomingParty.id)
  private val plain = denizensOf(Suit.Arcane).take(3)

  private def withParty(top: Vector[WorldCardId]): ReadyGame =
    atHome(SearchFixture.staged(top), party)

  private def favor(ready: ReadyGame): Int = player(ready).board.favor
  private def hearthBank(ready: ReadyGame): Int =
    ready.banks.favor.getOrElse(Suit.Hearth, 0)

  test("Welcoming Party is a registered selected Search modifier") {
    val power = WelcomingParty.forCatalog(catalog).get
    assertEquals(power.cardId, party)
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
    assert(rules.offerableWalkerPowers(withParty(plain), actor, ActionRef.Search)
      .toOption.get.map(_.id).contains(WelcomingParty.id))
  }

  test("a denizen played faceup straight from the draw, to a site, gains 1 " +
      "favor from the Hearth bank") {
    val ready = withParty(plain)
    val done = play(ready, modifiers, plain.head, "site")
    val after = SearchFixture.after(done)
    // The play itself also gains 1 favor from the card's own suit bank (Arcane),
    // so Hearth is the only bank the power touches.
    assertEquals(hearthBank(after), hearthBank(ready) - 1)
    assertEquals(favor(after), favor(ready) + 2)
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events), after)
  }

  test("a card played as a faceup adviser gains it too, of any suit") {
    val ready = withParty(plain)
    val after = SearchFixture.after(play(ready, modifiers, plain.head,
      "adviser-faceup"))
    assertEquals(hearthBank(after), hearthBank(ready) - 1)
    assertEquals(favor(after), favor(ready) + 1)
  }

  test("a card drawn by a Search and placed facedown is not played faceup, so " +
      "it gains nothing") {
    val ready = withParty(plain)
    val done = play(ready, modifiers, plain.head, "adviser-facedown")
    val after = SearchFixture.after(done)
    assertEquals(hearthBank(after), hearthBank(ready))
    assertEquals(favor(after), favor(ready))
  }

  test("a card that was already a facedown adviser is not first drawn, so it " +
      "gains nothing, played faceup or to a site") {
    val card = plain.head
    val ready = asAdviser(withParty(plain.drop(1)), card, Orientation.FaceDown)
    Vector("adviser-faceup", "site").foreach { button =>
      val after = SearchFixture.after(playFacedown(ready, modifiers, card, button))
      assertEquals(hearthBank(after), hearthBank(ready), button)
    }
  }

  test("a discard and an unselected power gain nothing") {
    val ready = withParty(plain)
    val discarded = SearchFixture.after(play(ready, modifiers, plain.head,
      "discard"))
    assertEquals(hearthBank(discarded), hearthBank(ready))
    val unselected = SearchFixture.after(play(ready, Vector.empty, plain.head,
      "adviser-faceup"))
    assertEquals(hearthBank(unselected), hearthBank(ready))
  }

  test("a Vision is not a denizen, and a card does not trigger on its own play") {
    val power = WelcomingParty.forCatalog(catalog).get
    val vision = CardPlayedFaceup(VisionRules.Faith,
      RuleSourceRef.Adviser(actor, VisionRules.Faith))
    val ctx = PowerCtx(atHome(base, party), actor, power.source,
      PowerWindow.ActionCardPlayedFaceup, Vector.empty, vision,
      Some(ActionRef.Search))
    assert(!power.applicable(ctx))
    assert(!power.applicable(ctx.copy(operation = CardPlayedFaceup(party,
      RuleSourceRef.SiteCard(home(base), party)))))
    val played = CardPlayedFaceup(plain.head,
      RuleSourceRef.SiteCard(home(base), plain.head))
    assert(power.applicable(ctx.copy(operation = played)))
    assert(!power.applicable(ctx.copy(operation = played,
      procedure = Some(ActionRef.PlayFacedownAdviser))))
    assert(!power.applicable(ctx.copy(operation = played, procedure = None)))
    assert(!power.applicable(ctx.copy(operation =
      CardPlayedFacedown(plain.head, actor),
      window = PowerWindow.ActionCardPlayedFacedown)))
  }

  test("an empty Hearth bank gives nothing") {
    val ready = withParty(plain).copy(banks = base.banks.copy(favor =
      base.banks.favor.updated(Suit.Hearth, 0)))
    val after = SearchFixture.after(play(ready, modifiers, plain.head,
      "adviser-faceup"))
    assertEquals(favor(after), favor(ready))
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/cardplay/WildCrySuite.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{CardStaging, PlayerFacts, PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers, WalkerStepRecorded}
import oathdigital.model._

class WildCrySuite extends munit.FunSuite {
  import PowerFixture._
  import SearchFixture._

  private val wildCry = DenizenId("189")
  private val modifiers = Vector(WildCry.id)
  private val beast = denizensOf(Suit.Beast)
  private val others = denizensOf(Suit.Hearth).take(2)

  /** Wild Cry at the pawn site, the deck topped by `kept` and two fillers. */
  private def withCry(kept: DenizenId): ReadyGame =
    atHome(SearchFixture.staged(Vector(kept) ++ others), wildCry)

  private val supplyAfterCost = 3
  private def warbands(ready: ReadyGame): Int = player(ready).board.warbands

  test("Wild Cry is a registered selected Search modifier") {
    val power = WildCry.forCatalog(catalog).get
    assertEquals(power.cardId, wildCry)
    assertEquals(power.actions, Set[MajorActionType](MajorActionType.Search))
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
  }

  test("it is offered for a Search when the card is usable, and for no other action") {
    val ready = withCry(beast.head)
    def offered(action: ActionRef) = rules.offerableWalkerPowers(ready, actor,
      action).toOption.get.map(_.id)
    assert(offered(ActionRef.Search).contains(WildCry.id))
    assert(offered(ActionRef.PlayFacedownAdviser).contains(WildCry.id))
    assert(!offered(ActionRef.Travel).contains(WildCry.id))
    assert(!offered(ActionRef.Muster).contains(WildCry.id))
  }

  test("it is not offered when the card is facedown or out of reach") {
    val facedown = asAdviser(SearchFixture.staged(Vector(beast.head) ++ others),
      wildCry, Orientation.FaceDown)
    assert(start(facedown, modifiers).isLeft)
    val elsewhere = SearchFixture.staged(Vector(beast.head) ++ others)
    assert(start(elsewhere, modifiers).isLeft)
  }

  test("a beast denizen played to a site gains 1 Supply and 2 warbands") {
    val ready = withCry(beast.head)
    val done = play(ready, modifiers, beast.head, "site")
    val after = SearchFixture.after(done)
    assertEquals(player(after).board.supply.supply, supplyAfterCost + 1)
    assertEquals(warbands(after), warbands(ready) + 2)
    assert(done.events.collect { case step: WalkerStepRecorded => step }
      .exists(_.contributions.contains(WildCry.id)))
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events),
      after)
  }

  test("a beast denizen played as a faceup adviser gains the same") {
    val ready = withCry(beast.head)
    val after = SearchFixture.after(play(ready, modifiers, beast.head,
      "adviser-faceup"))
    assertEquals(player(after).board.supply.supply, supplyAfterCost + 1)
    assertEquals(warbands(after), warbands(ready) + 2)
  }

  test("a facedown play, a discard, another suit and no selection gain nothing") {
    val ready = withCry(beast.head)
    Vector("adviser-facedown", "discard").foreach { button =>
      val after = SearchFixture.after(play(ready, modifiers, beast.head, button))
      assertEquals(player(after).board.supply.supply, supplyAfterCost, button)
      assertEquals(warbands(after), warbands(ready), button)
    }
    val hearth = others.head
    val other = SearchFixture.after(play(atHome(SearchFixture.staged(
      Vector(hearth, beast.head, others(1))), wildCry), modifiers, hearth, "site"))
    assertEquals(player(other).board.supply.supply, supplyAfterCost)
    val unselected = SearchFixture.after(play(ready, Vector.empty, beast.head,
      "site"))
    assertEquals(player(unselected).board.supply.supply, supplyAfterCost)
    assertEquals(warbands(unselected), warbands(ready))
  }

  test("a card does not trigger on its own play") {
    val hook = CardPlayedFaceup(wildCry, RuleSourceRef.Adviser(actor, wildCry))
    val power = WildCry.forCatalog(catalog).get
    val ctx = PowerCtx(atHome(base, wildCry), actor, power.source,
      PowerWindow.ActionCardPlayedFaceup, Vector.empty, hook)
    assert(!power.applicable(ctx))
    val beastHook = CardPlayedFaceup(beast.head,
      RuleSourceRef.Adviser(actor, beast.head))
    assert(power.applicable(ctx.copy(operation = beastHook)))
    val facedown = CardPlayedFacedown(beast.head, actor)
    assert(!power.applicable(ctx.copy(operation = facedown,
      window = PowerWindow.ActionCardPlayedFacedown)))
  }

  test("an empty warband bank gives what it holds") {
    val kind = PlayerFacts.forceKind(base, actor).toOption.get
    val ready = leaveInBank(withCry(beast.head), kind, 1)
    val after = SearchFixture.after(play(ready, modifiers, beast.head, "site"))
    assertEquals(warbands(after), warbands(ready) + 1)
    assertEquals(player(after).board.supply.supply, supplyAfterCost + 1)
  }

  test("a hook walked with the power alone applies it once") {
    val ready = withBoard(atHome(base, wildCry))(
      _.copy(supply = SupplyTrack(supplyAfterCost)))
    val hook = CardPlayedFaceup(beast.head,
      RuleSourceRef.Adviser(actor, beast.head))
    val outcome = ProcedureWalker.advance(ready, hook, None,
      WalkerPowers(Vector(WildCry.forCatalog(catalog).get))).toOption.get
    val steps = outcome.asInstanceOf[WalkerOutcome.Finished].events
      .collect { case step: WalkerStepRecorded => step.ops }.flatten
    assertEquals(steps.count(_.isInstanceOf[GainSupply]), 1)
  }

  test("Wild Cry cannot be discarded while it is selected: it is not offered " +
      "as the replacement of a faceup adviser") {
    val extra = denizensOf(Suit.Arcane).head
    val kept = beast.head
    // The actor starts with one facedown adviser. With one more and Wild Cry,
    // the area of three is full and a faceup play asks for a replacement.
    val holding = asAdviser(CardStaging.without(asAdviser(withCry(kept), extra,
      Orientation.FaceDown), wildCry), wildCry)
    val before = player(holding).advisers.map(_.id).toSet
    assertEquals(before.size, 3)
    val started = start(holding, modifiers).toOption.get
    val chosen = keep(started, kept).toOption.get
    val asked = place(chosen, kept, "adviser-faceup").toOption.get
    assert(replace(asked, kept, wildCry).isLeft, "Wild Cry was discardable")
    val done = replace(asked, kept, extra).toOption.get
    val after = player(SearchFixture.after(done)).advisers.map(_.id).toSet
    assert(after.contains(wildCry) && after.contains(kept))
    assert(!after.contains(extra))
  }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, for example `not found: type SelectedModifier`.

- [ ] **Step 3: Implement**

Create `src/main/scala/oathdigital/gameplay/powers/CatalogCards.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** Which card prints a power, read from the catalog. `None` for a catalog
  * without the card (a test stub), so a power whose card is absent is omitted
  * rather than failing construction.
  */
object CatalogCards {
  def denizen(catalog: ExecutableCatalog, power: PowerId): Option[DenizenId] =
    catalog.denizens.find(_.powers.exists(_.id == power))
      .map(card => DenizenId(card.id.value))

  def relic(catalog: ExecutableCatalog, power: PowerId): Option[RelicId] =
    catalog.relics.find(_.powers.exists(_.id == power))
      .map(card => RelicId(card.id.value))

  def edifice(catalog: ExecutableCatalog, power: PowerId): Option[EdificeId] =
    catalog.edifices.find(card => card.intact.powers.exists(_.id == power) ||
      card.ruined.powers.exists(_.id == power))
      .map(card => EdificeId(card.id.value))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.operations.Costs
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, PowerCtx, Transform}
import oathdigital.model._

/** A power the player selects in a command's `modifiers` at the start of a
  * major action, and that then applies to that action for free unless it names
  * a cost.
  *
  * A modifier that names a `cost` pays it at the very start of its action,
  * whatever the modifier then does: the kit prepends the payment to the root of
  * the action's tree (the action's eligibility window). The player owns the
  * choice to select it, so the payment is made whether or not the modifier then
  * has an effect. The same payment is `selectionPayments`, so a command that
  * selects several modifiers dry-runs all of their payments together and refuses
  * a combination the player cannot pay, before anything happens.
  *
  * `applicable` answers three questions, told apart by the window:
  *
  *  - At a `*ModifierSelection` window it asks "may the player select this
  *    now?": the power belongs to that action, its card is one the player may
  *    use (`PowerAccess`), and its `cost` is payable on its own, including the
  *    empty-card rule.
  *  - At the action's eligibility window (the root, where the payment goes) a
  *    selected modifier always applies.
  *  - At every other window it asks `appliesAt`, which reads only the node the
  *    power is hooked on. It must not read state the action itself changes,
  *    because the walker folds every window again on each resume and a fold
  *    that differs from the first one moves the parked position.
  *
  * The selection window is checked by action, so a Travel modifier is never
  * offered for a Search, whichever window a later power hooks.
  *
  * A power states what it does inside the walk as `effects`, by window, and the
  * kit adds the payment to them. Its resolution is read from the catalog
  * (`persistent: false` is selected).
  */
trait SelectedModifier extends ContributingPower {
  def catalog: ExecutableCatalog
  /** The card the power is printed on. */
  def cardId: CardId
  /** The major actions at whose start the power may be selected. */
  def actions: Set[MajorActionType]
  /** What selecting the power costs, placed onto its card. */
  def cost: Cost = Cost.free
  /** What the power does inside the walk, by window. */
  def effects: Map[PowerWindow, Vector[Contribution]]

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  final override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  final override lazy val contributions: Map[PowerWindow, Vector[Contribution]] = {
    val payments: Map[PowerWindow, Vector[Contribution]] =
      if (cost == Cost.free) Map.empty
      else actions.map(action => SelectedModifier.eligibility(action) ->
        Vector[Contribution](Transform((ctx, operations) =>
          payment(ctx.activePlayer) +: operations))).toMap
    (payments.keySet ++ effects.keySet).map(window => window ->
      (payments.getOrElse(window, Vector.empty) ++
        effects.getOrElse(window, Vector.empty))).toMap
  }

  /** Whether the power applies at the node it is hooked on. */
  def appliesAt(ctx: PowerCtx): Boolean = true

  final override def applicable(ctx: PowerCtx): Boolean =
    SelectedModifier.selectionAction(ctx.window) match {
      case Some(action) => actions(action) &&
        selectable(ctx.state, ctx.activePlayer)
      case None => SelectedModifier.isEligibility(ctx.window) ||
        appliesAt(ctx)
    }

  /** The player may use the card, and can pay for it. */
  def selectable(ready: ReadyGame, actor: PlayerId): Boolean =
    PowerAccess.locate(ready, actor, cardId).isDefined &&
      Costs.affordable(ready, actor, Location.OnCard(cardId), cost)

  final override def selectionPayments(ready: ReadyGame, actor: PlayerId)
      : Vector[CoreOperation] =
    if (cost == Cost.free) Vector.empty else Vector(payment(actor))

  /** The payment, required, placed onto the card as `PayCost` places it. */
  protected final def payment(actor: PlayerId): CoreOperation =
    Costs.onCard(actor, cardId, cost, catalog)
}

object SelectedModifier {
  private val selection: Map[PowerWindow, MajorActionType] = Map(
    PowerWindow.SearchModifierSelection -> MajorActionType.Search,
    PowerWindow.TravelModifierSelection -> MajorActionType.Travel,
    PowerWindow.CampaignModifierSelection -> MajorActionType.Campaign,
    PowerWindow.MusterModifierSelection -> MajorActionType.Muster,
    PowerWindow.TradeModifierSelection -> MajorActionType.Trade,
    PowerWindow.ForgeModifierSelection -> MajorActionType.Forge,
    PowerWindow.RecoverModifierSelection -> MajorActionType.Recover,
    PowerWindow.ChallengeModifierSelection -> MajorActionType.Challenge)

  private val eligibilityWindows: Map[MajorActionType, PowerWindow] = Map(
    MajorActionType.Search -> PowerWindow.SearchActionEligibility,
    MajorActionType.Travel -> PowerWindow.TravelActionEligibility,
    MajorActionType.Campaign -> PowerWindow.CampaignActionEligibility,
    MajorActionType.Muster -> PowerWindow.MusterActionEligibility,
    MajorActionType.Trade -> PowerWindow.TradeActionEligibility,
    MajorActionType.Forge -> PowerWindow.ForgeActionEligibility,
    MajorActionType.Recover -> PowerWindow.RecoverActionEligibility,
    MajorActionType.Challenge -> PowerWindow.ChallengeActionEligibility)

  /** The action a modifier-selection window belongs to, or `None` for any
    * other window.
    */
  def selectionAction(window: PowerWindow): Option[MajorActionType] =
    selection.get(window)

  /** The window at the root of `action`'s tree, where a payment goes. */
  def eligibility(action: MajorActionType): PowerWindow =
    eligibilityWindows(action)

  def isEligibility(window: PowerWindow): Boolean =
    eligibilityWindows.valuesIterator.contains(window)
}
```

Create `src/main/scala/oathdigital/gameplay/powers/cardplay/CardPlayTriggers.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The powers that trigger when a card is played, registered together. A power
  * whose card is absent from `catalog` is omitted.
  */
object CardPlayTriggers {
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    WildCry.forCatalog(catalog).toVector ++
      WelcomingParty.forCatalog(catalog).toVector ++
      Gossip.forCatalog(catalog).toVector
}
```

Create `src/main/scala/oathdigital/gameplay/powers/cardplay/Gossip.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution}
import oathdigital.model._

/** Gossip (card 99), a persistent rule of a faceup adviser: when any other
  * player places an adviser facedown, a denizen or a Vision, the holder gains 1
  * favor from the Discord bank. The holder's own facedown plays do not count.
  *
  * The rule is automatic, so it needs no selection. A facedown copy is not
  * active, and the card is adviser-only, so the holder is found among the
  * players' faceup advisers.
  */
final case class Gossip private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower {
  def id: PowerId = Gossip.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFacedown -> Vector(Transform((ctx, children) =>
      holderOf(ctx).fold(children)(holder => children :+
        Gain.Favor(holder, Suit.Discord, Gossip.Favor)))))

  override def applicable(ctx: PowerCtx): Boolean = holderOf(ctx).nonEmpty

  /** The holder, when the hooked play is another player's facedown play. */
  private def holderOf(ctx: PowerCtx): Option[PlayerId] = ctx.operation match {
    case CardPlayedFacedown(_, player) => ctx.state.game.current.players
      .find(_.advisers.exists {
        case DenizenState(card, Orientation.FaceUp, _) => card == cardId
        case _ => false
      }).map(_.player).filter(_ != player)
    case _ => None
  }
}

object Gossip {
  val id: PowerId = PowerId("denizen.gossip")
  val Favor: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[Gossip] =
    CatalogCards.denizen(catalog, id).map(new Gossip(_, catalog))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/cardplay/WelcomingParty.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** Welcoming Party (card 50), a selected Search modifier: if you play a
  * denizen faceup when it is first drawn, gain 1 favor from the Hearth bank.
  *
  * "When first drawn" is the card's origin: it comes straight from the draw,
  * the temporary hand of a Search. It is played faceup when it goes to a site or
  * becomes a faceup adviser. A card placed facedown does not trigger it, and
  * neither does a card that was already a facedown adviser and is played faceup
  * later by the Play-Facedown-Adviser action. A Vision is not a denizen, and a
  * card does not trigger on its own play.
  *
  * The played-card hook does not carry the origin, so the power reads it from
  * the procedure the window is walked for (`PowerCtx.procedure`): a card played
  * by a Search came straight from its draw. The favor is best-effort, so an
  * empty Hearth bank gives nothing.
  */
final case class WelcomingParty private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier {
  def id: PowerId = WelcomingParty.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup -> Vector(Transform((ctx, children) =>
      children :+ Gain.Favor(ctx.activePlayer, Suit.Hearth,
        WelcomingParty.Favor))))

  override def appliesAt(ctx: PowerCtx): Boolean = ctx.operation match {
    case CardPlayedFaceup(card: DenizenId, _) => card != cardId &&
      ctx.procedure.contains(ActionRef.Search)
    case _ => false
  }
}

object WelcomingParty {
  val id: PowerId = PowerId("denizen.welcoming-party")
  val Favor: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[WelcomingParty] =
    CatalogCards.denizen(catalog, id).map(new WelcomingParty(_, catalog))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/cardplay/WildCry.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, PlayerFacts, SelectedModifier}
import oathdigital.model._

/** Wild Cry (card 189), a selected Search modifier: when you play a beast
  * denizen faceup, to a site or as a faceup adviser, gain 1 Supply and 2
  * warbands. A facedown play does not trigger it, and a card does not trigger
  * on its own play.
  *
  * The trigger reads only the played card, so the fold is the same on every
  * resume. Gaining warbands is best-effort: an empty warband bank gives what
  * it holds.
  */
final case class WildCry private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier {
  def id: PowerId = WildCry.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup -> Vector(Transform((ctx, children) =>
      children ++ effects(ctx.activePlayer))))

  override def appliesAt(ctx: PowerCtx): Boolean = ctx.operation match {
    case CardPlayedFaceup(card: DenizenId, _) =>
      card != cardId && catalog.suitOf(card).contains(Suit.Beast)
    case _ => false
  }

  private def effects(actor: PlayerId): Vector[Operation] = Vector(
    GainSupply(actor, WildCry.Supply),
    BuildOps((ready, _) => PlayerFacts.forceKind(ready, actor).map(kind =>
      Vector(Gain.Warbands(actor, kind, WildCry.Warbands)))))
}

object WildCry {
  val id: PowerId = PowerId("denizen.wild-cry")
  val Supply: Int = 1
  val Warbands: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[WildCry] =
    CatalogCards.denizen(catalog, id).map(new WildCry(_, catalog))
}
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, replace:

```scala
import oathdigital.gameplay.powers.campaign.VowOfPeaceContribution
```

with:

```scala
import oathdigital.gameplay.powers.campaign.VowOfPeaceContribution
import oathdigital.gameplay.powers.cardplay.CardPlayTriggers
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, replace:

```scala
      Dazzle.forCatalog(catalog) :+ TakeWealthLimit :+ ConspiracyWhenPlayed)
```

with:

```scala
      CardPlayTriggers.forCatalog(catalog) ++
      Dazzle.forCatalog(catalog) :+ TakeWealthLimit :+ ConspiracyWhenPlayed)
```

- [ ] **Step 4: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.SelectedModifierSuite oathdigital.gameplay.powers.cardplay.WildCrySuite oathdigital.gameplay.powers.cardplay.WelcomingPartySuite oathdigital.gameplay.powers.cardplay.GossipSuite oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS (67 tests in these suites and the ones they touch).

- [ ] **Step 5: Run the whole suite and the architecture check**

Run: `./sbtw test` and `python3 scripts/check-architecture.py`
Expected: PASS, and `architecture check passed`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: add the modifier kit, Wild Cry, Welcoming Party and Gossip

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```


- [ ] **Step: Record sub-slice 2b**

Follow "Recording a sub-slice" below for 2b and commit with `docs: record slice 2b`.

---

## Sub-slice 2c: Travel

### Task 8: Tents, Forest Paths, Dragonskin Drum, Toll Roads and Grasping Vines

**Files:**
- Create: `powers/travel/{TravelPayments,Tents,ForestPaths,DragonskinDrum,TollRoads,GraspingVines,TravelModifiers}.scala`
- Modify: `powerresolver/ContributingPower.scala`, `powerresolver/ContributionCollector.scala`, `actions/travel/TravelProcedure.scala`, `powers/WalkerPowerCatalog.scala`
- Test: `powers/travel/{TravelFixture,TentsSuite,ForestPathsSuite,DragonskinDrumSuite,TollRoadsSuite,GraspingVinesSuite}.scala`, `ContributionIgnoresSuite.scala`, an edit to `TravelProcedureSuite.scala`

**Interfaces:**
- Produces: `Tents`, `ForestPaths`, `DragonskinDrum` (selected Travel modifiers), `TollRoads`, `GraspingVines` (persistent rules), `TravelModifiers.forCatalog(catalog)`, `ContributingPower.ignores(ctx: PowerCtx, other: ContributingPower): Boolean` (defaults to `shouldIgnore(other)`). Test support `TravelFixture` (`board`, `passRuled`, `ruledBy`, `denizenAt`, `adviser`, `edificeAt`, `candidates`, `travel`, `after`, `supplyOf`, `adviserTokens`, and named sites).
- Consumes: Task 7's `SelectedModifier`, `CatalogCards`; Task 3's `CardStaging`; `TravelRoute.pawnMove` (private to the `travel` package, so the new powers live there).

All five hook `PowerWindow.TravelCost`, the `Sequence(SpendSupply, Move)` cost node the terrain powers already shape.

- **Tents** (cost 1 favor placed): the kit places the favor at the start of every Travel it is selected for. When the destination is in the region of the pawn's site it also removes the Supply payment.
- **Forest Paths** (cost 1 favor placed): the kit places the favor at the start of every Travel it is selected for. When the destination holds a beast denizen or edifice, intact or ruined, it also removes the payment and ignores every site-sourced power (through `ignores`, decided per route). It hooks `TravelActionEligibility` with a transform that changes nothing, because the collector lets a power ignore only what is gathered beside it there, and Narrow Pass hooks that window.
- **Dragonskin Drum** (free): appends a `Gain.Warbands` after the pawn's move, so a rejected Travel gains nothing.
- **Toll Roads** (persistent): an enemy of the ruler of the Toll Roads site who travels to a site that ruler rules gives 1 favor to the ruler (a required `Give`) or burns it when bandits rule (a required `PayCost` with a burnt favor). It is placed before the move, so a traveller who cannot pay is rejected and the destination is not offered. Empire is not supported.
- **Grasping Vines** (persistent): an enemy leaving a site ruled by the Vines' ruler kills one warband of their own, a plain non-required `Kill` before the move.

The two engine edits are `ignores` and the fix from "Beyond E6, E7 and E9" item 1. `TravelProcedureSuite` gets one test edit (fact 17). Every cost-on-selection modifier here follows the permissive, pay-at-the-start rule of the Global Constraints. Tents and Forest Paths together need two favor, and one favor is refused at selection (a test in `TentsSuite`).

- [ ] **Step 1: Write the tests**

Create `src/test/scala/oathdigital/gameplay/ContributionIgnoresSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.{Contribution, ContributionCollector, ContributingPower, PowerCtx, Transform}
import oathdigital.gameplay.setup.FirstGameSetupFixture.initialReady
import oathdigital.model._

/** The named ignore is decided with the context the powers are gathered in, so
  * a power can ignore another only for the node at hand. `shouldIgnore` (with no
  * context) still works: `ignores` defaults to it.
  */
class ContributionIgnoresSuite extends munit.FunSuite {
  private val window = PowerWindow.TravelCost
  private val actor = initialReady.game.current.turn.activePlayer

  private def power(name: String,
      ignoring: (PowerCtx, ContributingPower) => Boolean = (_, _) => false)
      : ContributingPower = new ContributingPower {
    def id: PowerId = PowerId(name)
    def source: RuleSourceRef = RuleSourceRef.GameRule(name)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(window -> Vector(Transform((_, ops) => ops)))
    override def ignores(ctx: PowerCtx, candidate: ContributingPower): Boolean =
      ignoring(ctx, candidate)
  }

  private def ctxAt(operation: Operation)(candidate: ContributingPower)
      : PowerCtx = PowerCtx(initialReady, actor, candidate.source, window,
    Vector.empty, operation)

  private def survivors(powers: Vector[ContributingPower], operation: Operation)
      : Vector[PowerId] = ContributionCollector.gather(window, powers,
    ctxAt(operation)).order

  private val plain = power("test.plain")
  private val marker = SpendSupply(actor, 1)
  private val other = SpendSupply(actor, 2)

  test("a context-free shouldIgnore still ignores, through the default") {
    val quiet = new ContributingPower {
      def id: PowerId = PowerId("test.quiet")
      def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
      def contributions: Map[PowerWindow, Vector[Contribution]] =
        Map(window -> Vector.empty)
      override def shouldIgnore(candidate: ContributingPower): Boolean =
        candidate.id == plain.id
    }
    assertEquals(survivors(Vector(plain, quiet), marker), Vector(quiet.id))
  }

  test("a power can ignore another for one node and not for another") {
    val picky = power("test.picky", ignoring = (ctx, candidate) =>
      candidate.id == plain.id && ctx.operation == marker)
    assertEquals(survivors(Vector(plain, picky), marker), Vector(picky.id))
    assertEquals(survivors(Vector(plain, picky), other).toSet,
      Set(plain.id, picky.id))
  }

  test("one pass: a power that is itself ignored still has its ignore counted") {
    val first = power("test.first", ignoring = (_, candidate) =>
      candidate.id.value == "test.second")
    val second = power("test.second", ignoring = (_, candidate) =>
      candidate.id.value == "test.third")
    val third = power("test.third")
    assertEquals(survivors(Vector(first, second, third), marker),
      Vector(first.id))
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/travel/DragonskinDrumSuite.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{PlayerFacts, PowerFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class DragonskinDrumSuite extends munit.FunSuite {
  import PowerFixture._
  import TravelFixture._

  private val drum = RelicId("R20")
  private val modifiers = Vector(DragonskinDrum.id)
  private def held = withRelic(board(), drum)

  test("the Drum is a registered selected Travel modifier") {
    val power = DragonskinDrum.forCatalog(catalog).get
    assertEquals(power.cardId, drum)
    assertEquals(power.cost, Cost.free)
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
  }

  test("after Travel the player gains one warband, and pays the printed Supply") {
    val ready = held
    val done = travel(ready, coast, modifiers).toOption.get
    val result = after(done)
    assertEquals(player(result).pawnSite, Some(coast))
    assertEquals(player(result).board.warbands, player(ready).board.warbands + 1)
    assertEquals(supplyOf(result), 7 - 1)
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events), result)
    assert(PaidActionHarness.wireRoundTrips(done.events))
  }

  test("without the selection, or with an empty bank, it gains what it can") {
    val plain = after(travel(held, coast).toOption.get)
    assertEquals(player(plain).board.warbands, player(held).board.warbands)
    val kind = PlayerFacts.forceKind(held, actor).toOption.get
    val empty = leaveInBank(held, kind, 0)
    val result = after(travel(empty, coast, modifiers).toOption.get)
    assertEquals(player(result).board.warbands, player(empty).board.warbands)
  }

  test("a Travel that is rejected gains nothing") {
    val ready = withBoard(held)(_.copy(supply = SupplyTrack(0)))
    assert(travel(ready, coast, modifiers).isLeft)
  }

  test("a facedown Drum is not usable") {
    assert(travel(withRelic(board(), drum, Orientation.FaceDown), coast,
      modifiers).isLeft)
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/travel/ForestPathsSuite.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.catalog.CardRestrictions
import oathdigital.gameplay.powers.PowerFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathViolation.TravelPassBlocked

class ForestPathsSuite extends munit.FunSuite {
  import PowerFixture._
  import TravelFixture._

  private val paths = DenizenId("43")
  private val modifiers = Vector(ForestPaths.id)
  private val beast: DenizenId = catalog.denizens.filter(d =>
    d.suit == Suit.Beast && d.restrictions == CardRestrictions.Unrestricted &&
      d.id.value != paths.value).map(d => DenizenId(d.id.value)).head
  private def held = withBoard(adviser(board(), paths))(_.copy(favor = 1))

  test("Forest Paths is a registered selected Travel modifier that costs 1 favor") {
    val power = ForestPaths.forCatalog(catalog).get
    assertEquals(power.cardId, paths)
    assertEquals(power.cost, Cost(favor = 1))
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
  }

  test("a destination with a beast card costs no Supply and ignores the " +
      "terrain of sites") {
    val ready = denizenAt(passRuled(held), beast, mountain)
    // The printed 2 and the Mountain's 1.
    assertEquals(candidates(ready).get(mountain), Some(3))
    assertEquals(candidates(ready, modifiers).get(mountain), Some(0))
    val done = travel(ready, mountain, modifiers).toOption.get
    val result = after(done)
    assertEquals(player(result).pawnSite, Some(mountain))
    assertEquals(supplyOf(result), 7)
    assertEquals(player(result).board.favor, 0)
    assertEquals(adviserTokens(result, paths), Tokens(1, 0))
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events), result)
    assert(PaidActionHarness.wireRoundTrips(done.events))
  }

  test("it ignores Narrow Pass too: a pass the actor does not rule blocks nothing") {
    val ready = denizenAt(held, beast, plains(1))
    val blocked = travel(ready, plains(1))
    assertEquals(blocked.left.toOption.get, TravelPassBlocked(pass, plains(1)))
    assertEquals(candidates(ready).get(plains(1)), None)
    assertEquals(candidates(ready, modifiers).get(plains(1)), Some(0))
    val result = after(travel(ready, plains(1), modifiers).toOption.get)
    assertEquals(player(result).pawnSite, Some(plains(1)))
    assertEquals(supplyOf(result), 7)
  }

  test("a ruined beast edifice counts as a beast card") {
    val edifice = catalog.edifices.find(_.suit == Suit.Beast).get
    val ready = edificeAt(passRuled(held), EdificeId(edifice.id.value),
      EdificeSide.Ruined, mountain)
    assertEquals(candidates(ready, modifiers).get(mountain), Some(0))
  }

  test("without a beast card at the destination the favor is still paid, but " +
      "no Supply is saved and no site power is ignored") {
    val ready = passRuled(held)
    assertEquals(candidates(ready, modifiers), candidates(ready))
    val result = after(travel(ready, mountain, modifiers).toOption.get)
    // The printed 2 and the Mountain's 1, both still charged.
    assertEquals(supplyOf(result), 7 - 3)
    assertEquals(player(result).board.favor, 0)
    assertEquals(adviserTokens(result, paths), Tokens(1, 0))
  }

  test("Narrow Pass still blocks a route with no beast card at the destination") {
    val ready = held
    assertEquals(travel(ready, plains(1), modifiers).left.toOption.get,
      TravelPassBlocked(pass, plains(1)))
  }

  test("it cannot be selected without a favor to place") {
    val broke = withBoard(denizenAt(passRuled(held), beast, mountain))(
      _.copy(favor = 0))
    assert(travel(broke, mountain, modifiers).isLeft)
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/travel/GraspingVinesSuite.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{PowerFixture, TargetsFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class GraspingVinesSuite extends munit.FunSuite {
  import PowerFixture._
  import TravelFixture._

  private val vines = DenizenId("178")
  private val rival = TargetsFixture.others(base).head
  private def warbands(ready: ReadyGame): Int = player(ready).board.warbands

  /** The Vines stand at the actor's own site, which `ruler` rules. */
  private def vinesAtHome(ruler: Option[PlayerId]): ReadyGame = {
    val ready = denizenAt(board(), vines, plains.head)
    ruler.fold(ready)(ruledBy(ready, plains.head, _))
  }

  test("Grasping Vines is a registered persistent rule, so it is automatic") {
    val power = GraspingVines.forCatalog(catalog).get
    assertEquals(power.cardId, vines)
    assertEquals(power.resolution, PowerResolution.Automatic)
  }

  test("an enemy traveling from a site the ruler rules kills a warband of " +
      "their own") {
    val ready = vinesAtHome(Some(rival))
    val done = travel(ready, coast).toOption.get
    val result = after(done)
    assertEquals(player(result).pawnSite, Some(coast))
    assertEquals(warbands(result), warbands(ready) - 1)
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events), result)
    assert(PaidActionHarness.wireRoundTrips(done.events))
  }

  test("bandits rule the site: the traveller is still an enemy") {
    val ready = vinesAtHome(None)
    assertEquals(warbands(after(travel(ready, coast).toOption.get)),
      warbands(ready) - 1)
  }

  test("the ruler is exempt") {
    val ready = vinesAtHome(Some(actor))
    assertEquals(warbands(after(travel(ready, coast).toOption.get)),
      warbands(ready))
  }

  test("only the site the traveller leaves counts, not the destination") {
    val ready = ruledBy(denizenAt(board(), vines, plains(1)), plains(1), rival)
    assertEquals(warbands(after(travel(ready, coast).toOption.get)),
      warbands(ready))
    // Travelling out of the Vines' site does kill.
    val leaving = ruledBy(denizenAt(board(source = plains(1)), vines,
      plains(1)), plains(1), rival)
    val out = after(travel(leaving, coast).toOption.get)
    assertEquals(warbands(out), warbands(leaving) - 1)
  }

  test("the kill is not required: a traveller with no warband is not stopped") {
    val ready = withBoard(vinesAtHome(Some(rival)))(_.copy(warbands = 0))
    val result = after(travel(ready, coast).toOption.get)
    assertEquals(player(result).pawnSite, Some(coast))
    assertEquals(warbands(result), 0)
  }

  test("a facedown Grasping Vines is not active") {
    val ready = ruledBy(adviser(board(), vines, Orientation.FaceDown),
      plains.head, rival)
    assertEquals(warbands(after(travel(ready, coast).toOption.get)),
      warbands(ready))
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/travel/TentsSuite.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.PowerFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class TentsSuite extends munit.FunSuite {
  import PowerFixture._
  import TravelFixture._

  private val tents = DenizenId("29")
  private val modifiers = Vector(Tents.id)
  /** The actor holds Tents as a faceup adviser and 1 favor, at the first plains. */
  private def held = withBoard(adviser(board(), tents))(_.copy(favor = 1))

  test("Tents is a registered selected Travel modifier that costs 1 favor") {
    val power = Tents.forCatalog(catalog).get
    assertEquals(power.cardId, tents)
    assertEquals(power.actions, Set[MajorActionType](MajorActionType.Travel))
    assertEquals(power.cost, Cost(favor = 1))
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
  }

  test("a destination in the region of the pawn's site costs no Supply, and is " +
      "still offered") {
    assertEquals(candidates(held).get(coast), Some(1))
    assertEquals(candidates(held, modifiers).get(coast), Some(0))
  }

  test("Travel in the region places the favor on the card and spends no Supply") {
    val ready = held
    val done = travel(ready, coast, modifiers).toOption.get
    val result = after(done)
    assertEquals(player(result).pawnSite, Some(coast))
    assertEquals(supplyOf(result), 7)
    assertEquals(player(result).board.favor, 0)
    assertEquals(adviserTokens(result, tents), Tokens(1, 0))
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events), result)
    assert(PaidActionHarness.wireRoundTrips(done.events))
  }

  test("the same power at the pawn's site takes its favor onto the site card") {
    val ready = withBoard(denizenAt(board(), tents, plains.head))(_.copy(favor = 1))
    val result = after(travel(ready, coast, modifiers).toOption.get)
    assertEquals(supplyOf(result), 7)
    assertEquals(PaidActionHarness.tokensOn(result, tents), Tokens(1, 0))
  }

  test("a route into another region is not changed, but the favor is still " +
      "paid: the player owns the choice to select it") {
    val ready = passRuled(held)
    val province = plains(1)
    assertEquals(candidates(ready, modifiers).get(province),
      candidates(ready).get(province))
    val result = after(travel(ready, province, modifiers).toOption.get)
    assertEquals(supplyOf(result), 7 - 2)
    assertEquals(player(result).board.favor, 0)
    assertEquals(adviserTokens(result, tents), Tokens(1, 0))
  }

  test("it cannot be selected without a favor to place, or onto an occupied card") {
    val broke = withBoard(held)(_.copy(favor = 0))
    assert(travel(broke, coast, modifiers).isLeft)
    val occupied = updateActor(held)(p => p.copy(advisers = p.advisers.map {
      case card: DenizenState if card.id == tents => card.copy(tokens = Tokens(1, 0))
      case other => other
    }))
    assert(travel(occupied, coast, modifiers).isLeft)
  }

  test("it is a Travel modifier only") {
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(held,
      actor, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Travel).contains(Tents.id))
    assert(!offered(ActionRef.Search).contains(Tents.id))
    assert(!offered(ActionRef.Muster).contains(Tents.id))
  }

  test("it is not offered when the card is facedown") {
    val facedown = withBoard(adviser(board(), tents, Orientation.FaceDown))(
      _.copy(favor = 1))
    assert(travel(facedown, coast, modifiers).isLeft)
  }

  test("Tents and Forest Paths together need two favor: one favor is refused at " +
      "selection, before anything is paid") {
    val paths = DenizenId("43")
    val both = Vector(Tents.id, ForestPaths.id)
    def ready(favor: Int) = withBoard(adviser(held, paths))(_.copy(favor = favor))
    val refused = travel(ready(1), coast, both)
    assert(refused.left.toOption.exists(_.toString.contains(
      "cannot all be paid together")), refused.toString)
    val done = after(travel(ready(2), coast, both).toOption.get)
    assertEquals(player(done).board.favor, 0)
    assertEquals(adviserTokens(done, tents), Tokens(1, 0))
    assertEquals(adviserTokens(done, paths), Tokens(1, 0))
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/travel/TollRoadsSuite.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{PowerFixture, TargetsFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class TollRoadsSuite extends munit.FunSuite {
  import PowerFixture._
  import TravelFixture._

  private val toll = DenizenId("118")
  private val rival = TargetsFixture.others(base).head

  /** Toll Roads stands at `plains(1)`, and `rival` rules it and the coast. The
    * actor, at the first plains, holds 1 favor and travels to the coast.
    */
  private def rivalRules: ReadyGame = {
    val ready = withBoard(denizenAt(board(), toll, plains(1)))(_.copy(favor = 1))
    ruledBy(ruledBy(ready, plains(1), rival), coast, rival)
  }

  test("Toll Roads is a registered persistent rule, so it is automatic") {
    val power = TollRoads.forCatalog(catalog).get
    assertEquals(power.cardId, toll)
    assertEquals(power.resolution, PowerResolution.Automatic)
  }

  test("an enemy pays the ruler 1 favor to travel to a site the ruler rules") {
    val ready = rivalRules
    val done = travel(ready, coast).toOption.get
    val result = after(done)
    assertEquals(player(result).pawnSite, Some(coast))
    assertEquals(player(result).board.favor, 0)
    assertEquals(player(result, rival).board.favor,
      player(ready, rival).board.favor + 1)
    assertEquals(supplyOf(result), 7 - 1)
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events), result)
    assert(PaidActionHarness.wireRoundTrips(done.events))
  }

  test("the rule covers every site the ruler holds, Toll Roads' own included") {
    val result = after(travel(passRuled(rivalRules), plains(1)).toOption.get)
    assertEquals(player(result).board.favor, 0)
    assertEquals(player(result, rival).board.favor,
      player(rivalRules, rival).board.favor + 1)
  }

  test("a traveller who cannot pay does not get the destination") {
    val broke = withBoard(rivalRules)(_.copy(favor = 0))
    assertEquals(candidates(broke).get(coast), None)
    assert(travel(broke, coast).isLeft)
    // A site the rival does not rule is still offered.
    assert(candidates(broke).contains(plains(2)))
  }

  test("a destination the ruler does not rule costs nothing extra") {
    val result = after(travel(rivalRules, plains(2)).toOption.get)
    assertEquals(player(result).board.favor, 1)
  }

  test("the ruler itself travels for free") {
    val ready = withBoard(denizenAt(board(), toll, plains(1)))(_.copy(favor = 1))
    val mine = ruledBy(ruledBy(ready, plains(1), actor), coast, actor)
    val result = after(travel(mine, coast).toOption.get)
    assertEquals(player(result).board.favor, 1)
  }

  test("when bandits rule, the favor is burnt") {
    val ready = withBoard(denizenAt(board(), toll, plains(1)))(_.copy(favor = 1))
    val result = after(travel(ready, coast).toOption.get)
    assertEquals(player(result).board.favor, 0)
    assertEquals(player(result, rival).board.favor,
      player(ready, rival).board.favor)
    assertEquals(candidates(withBoard(ready)(_.copy(favor = 0))).get(coast), None)
  }

  test("a facedown Toll Roads is not active") {
    val ready = withBoard(adviser(board(), toll, Orientation.FaceDown))(
      _.copy(favor = 0))
    assert(travel(ready, coast).isRight)
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/travel/TravelFixture.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.{CardStaging, PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** A Travel board for the modifiers and rules of Travel: the map of
  * `TravelProcedureSuite`, with named sites, every site ruled by bandits, and
  * the active player at the first plains.
  *
  * Regions: the cradle holds `plains(0)` and `coast`, the provinces hold
  * `plains(1)`, `mountain` and `pass`, the hinterland holds `plains(2)`,
  * `island` and `plains(3)`. The printed base is 1 within the cradle, 2 from the
  * cradle to the provinces and within them, 4 from the cradle to the
  * hinterland.
  */
object TravelFixture {
  import PowerFixture._

  val rules: OathRules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))

  private def site(power: String): SiteId = catalog.sites.find(
    _.handlers.exists(_.endsWith(s".$power"))).get.id
  val plains: Vector[SiteId] = catalog.sites.filter(
    _.handlers.exists(_.endsWith(".plains"))).map(_.id)
  val coast: SiteId = site("coast")
  val island: SiteId = site("island")
  val mountain: SiteId = site("mountain")
  val pass: SiteId = site("pass")

  /** The Act phase on the board. `source` is the active player's site; the
    * other players stand on the sites after it.
    */
  def board(source: SiteId = plains.head, supply: Int = 7): ReadyGame = {
    val chosen = Vector(source, coast, plains(1), mountain, pass, plains(2),
      island, plains(3))
    val ids = (chosen.distinct ++ catalog.sites.map(_.id)
      .filterNot(chosen.contains)).take(8)
    val states = ids.map { id =>
      val definition = catalog.sites.find(_.id == id).get
      id -> SiteState(
        if (definition.capacity == 0) SiteForces.Empty
        else SiteForces.Occupied(ForceKind.Bandit, definition.capacity),
        Vector.empty, Vector.empty, definition.startingResources)
    }.toMap
    val players = base.game.current.players.zipWithIndex.map {
      case (player, index) => player.copy(
        pawnSite = Some(if (player.player == actor) source else ids(index + 1)),
        board = if (player.player == actor)
          player.board.copy(supply = SupplyTrack(supply)) else player.board)
    }
    inPhase(base.updateCurrent(_.copy(players = players,
      map = MapState(ids.take(2), ids.slice(2, 5), ids.slice(5, 8), states))),
      Phase.Act)
  }

  /** The pass ruled by the actor, so a cross-region route past it is legal. */
  def passRuled(ready: ReadyGame): ReadyGame =
    ruledBy(ready, pass, actor)

  def ruledBy(ready: ReadyGame, site: SiteId, ruler: PlayerId): ReadyGame =
    ready.updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(site, c.map.sites(site).copy(forces =
        SiteForces.Occupied(ForceKind.Exile(player(ready, ruler).lineage), 1))))))

  /** A denizen or an edifice face put at `site`; it leaves the world or
    * edifice deck if it was there, so the inventory stays whole.
    */
  def denizenAt(ready: ReadyGame, id: DenizenId, at: SiteId): ReadyGame =
    atSite(CardStaging.without(ready, id), id, at)

  /** `id` as an adviser of the actor, taken from wherever it was dealt. */
  def adviser(ready: ReadyGame, id: DenizenId,
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    asAdviser(CardStaging.without(ready, id), id, orientation)

  def edificeAt(ready: ReadyGame, id: EdificeId, side: EdificeSide,
      at: SiteId): ReadyGame = CardStaging.without(ready, id).updateCurrent(c =>
    c.copy(
    map = c.map.copy(sites = c.map.sites.updated(at, c.map.sites(at).copy(
      denizens = c.map.sites(at).denizens :+
        EdificeState(id, side, Tokens.empty))))))

  def powers(modifiers: Vector[PowerId]): WalkerPowers =
    WalkerPowers.selected(WalkerPowerCatalog.default(catalog), modifiers)

  /** The destinations Travel offers with `modifiers` selected, and their Supply. */
  def candidates(ready: ReadyGame, modifiers: Vector[PowerId] = Vector.empty)
      : Map[SiteId, Int] = TravelProcedure.candidates(catalog, ready, actor,
    powers(modifiers)).toMap

  def travel(ready: ReadyGame, destination: SiteId,
      modifiers: Vector[PowerId] = Vector.empty)
      : Either[OathViolation, OathTransition] =
    rules.startWalker(Ready(ready), ActionRef.Travel, actor, modifiers,
      Vector(DecisionOptionRef.Site(destination)))

  def after(transition: OathTransition): ReadyGame =
    transition.state.asInstanceOf[Ready].value

  def supplyOf(ready: ReadyGame, id: PlayerId = actor): Int =
    player(ready, id).board.supply.supply

  /** The favor on an adviser card of the actor. */
  def adviserTokens(ready: ReadyGame, id: CardId): Tokens =
    player(ready).advisers.collectFirst {
      case card: DenizenState if card.id == id => card.tokens
    }.get
}
```

In `src/test/scala/oathdigital/gameplay/TravelProcedureSuite.scala`, replace:

```scala
  private val powers: WalkerPowers = WalkerPowerCatalog.default(catalog)
```

with:

```scala
  private val powers: WalkerPowers = WalkerPowers.selected(
    WalkerPowerCatalog.default(catalog), Vector.empty)
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, for example `not found: value DragonskinDrum`.

- [ ] **Step 3: Implement**

Create `src/main/scala/oathdigital/gameplay/powers/travel/DragonskinDrum.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, PlayerFacts, SelectedModifier}
import oathdigital.model._

/** Dragonskin Drum (relic R20), a selected Travel modifier: after traveling,
  * gain one warband. The gain follows the pawn's move in the same cost node, so
  * a Travel that is rejected gains nothing. The gain is best-effort, so an
  * empty warband bank gives nothing.
  */
final case class DragonskinDrum private (cardId: RelicId,
    catalog: ExecutableCatalog) extends SelectedModifier {
  def id: PowerId = DragonskinDrum.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Travel)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      operations :+ gain(ctx.activePlayer))))

  override def appliesAt(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).nonEmpty

  private def gain(actor: PlayerId): Operation = BuildOps((ready, _) =>
    PlayerFacts.forceKind(ready, actor).map(kind =>
      Vector(Gain.Warbands(actor, kind, DragonskinDrum.Warbands))))
}

object DragonskinDrum {
  val id: PowerId = PowerId("relic.dragonskin-drum")
  val Warbands: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[DragonskinDrum] =
    CatalogCards.relic(catalog, id).map(new DragonskinDrum(_, catalog))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/travel/ForestPaths.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** Forest Paths (card 43), a selected Travel modifier. Cost: 1 favor placed on
  * the card. If the destination holds a beast denizen or edifice, Travel costs
  * no Supply and the powers of sites are ignored for that Travel.
  *
  * The player owns the choice to select it. Selecting Forest Paths places the
  * favor at the start of every Travel (the kit does). Only when the destination
  * holds a beast card does it remove the Supply payment and ignore the site
  * powers.
  *
  * Ignoring a power is a named ignore, decided per Travel (`ignores` reads the
  * route from the context): while the destination qualifies, every power whose
  * source is a site is dropped from the fold. The terrain adds and the Narrow
  * Pass restriction both hook a Travel window, so Forest Paths hooks both
  * windows too (the second with a transform that changes nothing), because the
  * collector only lets a power ignore what is gathered beside it.
  */
final case class ForestPaths private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier {
  def id: PowerId = ForestPaths.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Travel)
  override def cost: Cost = Cost(favor = ForestPaths.Favor)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      if (beastAtDestination(ctx))
        TravelPayments.withoutSupply(operations, ctx.activePlayer)
      else operations)),
    PowerWindow.TravelActionEligibility ->
      Vector(Transform((_, operations) => operations)))

  override def appliesAt(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).nonEmpty

  private def beastAtDestination(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).exists(route =>
      TravelPayments.holdsSuit(ctx.state, route.destination, Suit.Beast,
        catalog.suitOf(_)))

  override def ignores(ctx: PowerCtx, other: ContributingPower): Boolean =
    beastAtDestination(ctx) && (other.source match {
      case _: RuleSourceRef.Site => true
      case _ => false
    })
}

object ForestPaths {
  val id: PowerId = PowerId("denizen.forest-paths")
  val Favor: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[ForestPaths] =
    CatalogCards.denizen(catalog, id).map(new ForestPaths(_, catalog))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/travel/GraspingVines.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution}
import oathdigital.model._

/** Grasping Vines (card 178), a persistent rule of a faceup site card: an enemy
  * traveling from a site ruled by the Vines' ruler kills one warband on their
  * own board if able. The Vines' ruler is the ruler of the site it stands at,
  * and that ruler is exempt.
  *
  * The kill is unconditional and not required: it is a plain `Kill` placed
  * before the pawn's move, which does nothing for a board with no warband, so a
  * traveller with none is not stopped.
  */
final case class GraspingVines private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower {
  def id: PowerId = GraspingVines.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      kill(ctx).fold(operations)(_ +: operations))))

  override def applicable(ctx: PowerCtx): Boolean = kill(ctx).nonEmpty

  private def kill(ctx: PowerCtx): Option[CoreOperation] = for {
    route <- TravelRoute.pawnMove(ctx.operation)
    ruler <- TravelRulers.rulerOfCard(ctx.state, cardId)
    if TravelRulers.isEnemy(ruler, route.player)
    if TravelRulers.rulerOf(ctx.state, route.source).contains(ruler)
    warband <- TravelPayments.ownWarband(ctx.state, route.player,
      GraspingVines.Warbands)
  } yield Kill(warband, PositionedLocation(Location.PlayArea(route.player)))
}

object GraspingVines {
  val id: PowerId = PowerId("denizen.grasping-vines")
  val Warbands: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[GraspingVines] =
    CatalogCards.denizen(catalog, id).map(new GraspingVines(_, catalog))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/travel/Tents.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** Tents (card 29), a selected Travel modifier. Cost: 1 favor placed on the
  * card. If the destination is in the region of the pawn's current site, Travel
  * costs no Supply.
  *
  * The player owns the choice to select it. Selecting Tents places the favor at
  * the start of every Travel (the kit does), whether or not the destination is
  * in the pawn's region, and the Supply payment (terrain adds included) is
  * removed only when it is.
  */
final case class Tents private (cardId: DenizenId, catalog: ExecutableCatalog)
    extends SelectedModifier {
  def id: PowerId = Tents.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Travel)
  override def cost: Cost = Cost(favor = Tents.Favor)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      if (sameRegion(ctx))
        TravelPayments.withoutSupply(operations, ctx.activePlayer)
      else operations)))

  override def appliesAt(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).nonEmpty

  private def sameRegion(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).exists { route =>
      val source = TravelPayments.region(ctx.state, route.source)
      source.nonEmpty &&
        source == TravelPayments.region(ctx.state, route.destination)
    }
}

object Tents {
  val id: PowerId = PowerId("denizen.tents")
  val Favor: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[Tents] =
    CatalogCards.denizen(catalog, id).map(new Tents(_, catalog))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/travel/TollRoads.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution}
import oathdigital.model._

/** Toll Roads (card 118), a persistent rule of a faceup site card: enemies
  * cannot travel to a site ruled by Toll Roads' ruler unless they give 1 favor
  * to that ruler, or burn it when bandits rule. Toll Roads' ruler is the ruler
  * of the site it stands at, and the rule covers every site that ruler holds,
  * Toll Roads' own included. Empire rulers are not supported.
  *
  * The payment is a required operation placed before the pawn's move, so a
  * traveller who cannot pay is rejected, and Travel's destination list does not
  * offer that destination. The ruler is exempt.
  */
final case class TollRoads private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower {
  def id: PowerId = TollRoads.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      toll(ctx).fold(operations)(_ +: operations))))

  override def applicable(ctx: PowerCtx): Boolean = toll(ctx).nonEmpty

  /** The payment this Travel owes, if it owes one. */
  private def toll(ctx: PowerCtx): Option[CoreOperation] = for {
    route <- TravelRoute.pawnMove(ctx.operation)
    ruler <- TravelRulers.rulerOfCard(ctx.state, cardId)
    if TravelRulers.isEnemy(ruler, route.player)
    if TravelRulers.rulerOf(ctx.state, route.destination).contains(ruler)
  } yield ruler match {
    case SiteRuler.Player(owner) => Give(Piece.Favor(TollRoads.Favor),
      route.player, Location.PlayArea(route.player),
      Location.PlayArea(owner), required = true)
    case _ => PayCost(route.player, Location.SharedBank,
      Cost(favorBurnt = TollRoads.Favor))
  }
}

object TollRoads {
  val id: PowerId = PowerId("denizen.toll-roads")
  val Favor: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[TollRoads] =
    CatalogCards.denizen(catalog, id).map(new TollRoads(_, catalog))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/travel/TravelModifiers.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The Travel modifiers and rules that are not terrain, registered together. A
  * power whose card is absent from `catalog` is omitted. Terrain is
  * [[TravelSitePowers]].
  */
object TravelModifiers {
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Tents.forCatalog(catalog).toVector ++
      ForestPaths.forCatalog(catalog).toVector ++
      DragonskinDrum.forCatalog(catalog).toVector ++
      TollRoads.forCatalog(catalog).toVector ++
      GraspingVines.forCatalog(catalog).toVector
}
```

Create `src/main/scala/oathdigital/gameplay/powers/travel/TravelPayments.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** Reads and rewrites the cost node of a Travel that several powers share. */
private[travel] object TravelPayments {

  /** The cost node without the traveller's Supply payment. `SpendSupply` cannot
    * be zero, so a free Travel has no payment at all.
    */
  def withoutSupply(operations: Vector[Operation], traveller: PlayerId)
      : Vector[Operation] = operations.filter {
    case SpendSupply(player, _, _) => player != traveller
    case _ => true
  }

  def region(ready: ReadyGame, site: SiteId): Option[Region] =
    ready.game.current.map.regionOf(site)

  /** Whether `site` holds a faceup denizen or an edifice, intact or ruined,
    * of `suit`.
    */
  def holdsSuit(ready: ReadyGame, site: SiteId, suit: Suit,
      suitOf: CardId => Option[Suit]): Boolean =
    ready.game.current.map.sites.get(site).exists(_.denizens.exists {
      case card: DenizenState => suitOf(card.id).contains(suit)
      case card: EdificeState => suitOf(card.id).contains(suit)
    })

  /** The warband of the traveller's own kind, if it has one. */
  def ownWarband(ready: ReadyGame, traveller: PlayerId, amount: Int)
      : Option[Piece.Warbands] = PlayerFacts.forceKind(ready, traveller)
    .toOption.map(kind => Piece.Warbands(kind, amount))
}

/** Who rules the site a rule card stands at, for Toll Roads and Grasping Vines. */
private[travel] object TravelRulers {

  /** The site holding `card` faceup. */
  def siteOf(ready: ReadyGame, card: DenizenId): Option[SiteId] =
    ready.game.current.map.sites.collectFirst {
      case (id, site) if site.denizens.exists {
        case DenizenState(`card`, Orientation.FaceUp, _) => true
        case _ => false
      } => id
    }

  def rulerOf(ready: ReadyGame, site: SiteId): Option[SiteRuler] =
    ready.game.current.map.sites.get(site).flatMap(state =>
      SiteRule.ruler(state.forces, ready.game.current.players).toOption)

  /** The ruler of the site holding `card`, when that is a player or bandits.
    * An Empire ruler is not supported.
    */
  def rulerOfCard(ready: ReadyGame, card: DenizenId): Option[SiteRuler] =
    siteOf(ready, card).flatMap(rulerOf(ready, _)).filter {
      case SiteRuler.Player(_) | SiteRuler.Bandits => true
      case _ => false
    }

  /** Whether `traveller` is an enemy of `ruler`: every player but the ruler
    * itself, and every player when bandits rule.
    */
  def isEnemy(ruler: SiteRuler, traveller: PlayerId): Boolean = ruler match {
    case SiteRuler.Player(owner) => owner != traveller
    case SiteRuler.Bandits => true
    case _ => false
  }
}
```

In `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`, replace:

```scala
  def shouldIgnore(other: ContributingPower): Boolean = false
```

with:

```scala
  def shouldIgnore(other: ContributingPower): Boolean = false
  /** As `shouldIgnore`, decided with the context this power is gathered in,
    * so it can ignore `other` for the node at hand and not for another.
    * The collector calls this one.
    */
  def ignores(ctx: PowerCtx, other: ContributingPower): Boolean =
    shouldIgnore(other)
```

In `src/main/scala/oathdigital/gameplay/powerresolver/ContributionCollector.scala`, replace:

```scala
      applicable.filter(power.shouldIgnore).map(_.id)
```

with:

```scala
      applicable.filter(other => power.ignores(ctxFor(power), other))
        .map(_.id)
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, replace:

```scala
import oathdigital.gameplay.powers.travel.TravelSitePowers
```

with:

```scala
import oathdigital.gameplay.powers.travel.{TravelModifiers, TravelSitePowers}
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, replace:

```scala
      TravelSitePowers.forCatalog(catalog) ++
```

with:

```scala
      TravelSitePowers.forCatalog(catalog) ++
      TravelModifiers.forCatalog(catalog) ++
```

In `src/main/scala/oathdigital/gameplay/actions/travel/TravelProcedure.scala`, replace:

```scala
          .flatMap(WalkerSimulation.run(_, state, powers))
          .toOption.flatMap(supplySpent(_, activePlayer)).map(destination -> _)
```

with:

```scala
          .flatMap(WalkerSimulation.run(_, state, powers))
          .toOption.map(operations =>
            destination -> supplySpent(operations, activePlayer))
```

In `src/main/scala/oathdigital/gameplay/actions/travel/TravelProcedure.scala`, replace:

```scala
  /** The Supply a completed Travel actually spent, read off the operations the
    * walk recorded rather than recomputed: after the fold, the pay node is
    * whatever the surviving transforms left it as.
    */
  private def supplySpent(operations: Vector[CoreOperation], actor: PlayerId)
      : Option[Int] = operations.collect {
    case SpendSupply(player, amount, _) if player == actor => amount
  }.lastOption
```

with:

```scala
  /** The Supply a completed Travel actually spent, read off the operations the
    * walk recorded rather than recomputed: after the fold, the pay node is
    * whatever the surviving transforms left it as. A Travel a power made free
    * has no payment left, and spent none.
    */
  private def supplySpent(operations: Vector[CoreOperation], actor: PlayerId)
      : Int = operations.collect {
    case SpendSupply(player, amount, _) if player == actor => amount
  }.lastOption.getOrElse(0)
```

- [ ] **Step 4: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.ContributionIgnoresSuite oathdigital.gameplay.ContributionCollectorSuite oathdigital.gameplay.powers.travel.TentsSuite oathdigital.gameplay.powers.travel.ForestPathsSuite oathdigital.gameplay.powers.travel.DragonskinDrumSuite oathdigital.gameplay.powers.travel.TollRoadsSuite oathdigital.gameplay.powers.travel.GraspingVinesSuite oathdigital.gameplay.TravelProcedureSuite oathdigital.gameplay.TravelSitePowersSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS (96 tests in these suites and the ones they touch).

- [ ] **Step 5: Run the whole suite and the architecture check**

Run: `./sbtw test` and `python3 scripts/check-architecture.py`
Expected: PASS, and `architecture check passed`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: implement Tents, Forest Paths, Dragonskin Drum, Toll Roads and Grasping Vines

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```


- [ ] **Step: Record sub-slice 2c**

Follow "Recording a sub-slice" below for 2c and commit with `docs: record slice 2c`.

---

## Sub-slice 2d: Search, Trade, Muster and Recover

### Task 9: Augury, Truthful Harp, Cup of Plenty, Rowdy Pub and Relic Worship

**Files:**
- Create: `powers/search/{DrawExtension,Augury,TruthfulHarp}.scala`, `powers/economy/{CupOfPlenty,RowdyPub}.scala`, `powers/recover/RelicWorship.scala`, `powers/ActionModifiers.scala`
- Modify: `actions/Search.scala`, `powers/RecoverPowers.scala`, `powers/WalkerPowerCatalog.scala`
- Test: `powers/search/{Augury,TruthfulHarp}Suite.scala`, `powers/economy/{CupOfPlenty,RowdyPub}Suite.scala`, `powers/recover/RelicWorshipSuite.scala`

**Interfaces:**
- Produces: `Augury`, `TruthfulHarp`, `CupOfPlenty`, `RowdyPub`, `RelicWorship` (each with `id` and `forCatalog(catalog)`), `ActionModifiers.forCatalog(catalog)`, `SearchRules.DrawSize` and `SearchRules.draw(ready, source, origin, extra = 0)`.
- Consumes: Task 7's `SelectedModifier`, `CatalogCards`, `SearchFixture`; Task 3's `CardStaging`; `EconomyFixture`, `CatacombsContributionSuite.relicSite` (existing).

- **Augury** and the **Truthful Harp** wrap the Search draw at `SearchBeforeDraw`. `DrawExtension.extend(operations, more)` wraps the draw `BuildOps` and asks `SearchRules.draw` for the whole longer draw, so a Vision still stops it and a regional pile is drawn from its top. Two wrappers add up, so Augury and the Harp stack. The Harp appends one more node after the draw that records a `Peek` of every card in the hand for every other player (fact 13). The reveal changes nothing about the play: the kept card can still be played faceup, to a site or facedown as an adviser, and the suite plays it facedown.
- **Cup of Plenty** replaces the Trade's `SpendSupply` with a node that reads the answered source card and pays unless its suit matches a faceup adviser (fact 14). With no faceup adviser the trade is free (confirmed).
- **Rowdy Pub** appends a node to the Muster's gain that adds one warband when the answered source is Rowdy Pub.
- **Relic Worship** pays its secret at the start of the Recover (the kit, Task 7, like every modifier) and appends only the gain of 2 Supply at `RecoverAfterRelic`, where the ruling puts it. A Recover that ends without a relic has still paid the secret. **This departs from its ruling** (open item 1). Catacombs and Relic Worship with one faceup secret are refused at selection. The reviewed classification `RecoverPowers.RelicWorship` (an automatic, unimplemented Recover rule) recorded an ignored-rule diagnostic on every Recover with the card in reach, so it becomes a selected, implemented handler.

- [ ] **Step 1: Write the tests**

Create `src/test/scala/oathdigital/gameplay/powers/economy/CupOfPlentySuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.EconomyFixture
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.{PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

class CupOfPlentySuite extends munit.FunSuite {
  import EconomyFixture._
  import SearchFixture.rules

  private val cup = RelicId("R07")
  private val modifiers = Vector(CupOfPlenty.id)
  /** A suit that is not the traded card's, for an adviser that does not match. */
  private val otherSuit = catalog.denizens.find(d => d.suit != plain.suit).get
  private val otherId = DenizenId(otherSuit.id.value)

  private def held(advisers: Vector[AdviserState]): ReadyGame =
    PowerFixture.withRelic(act(advisers = advisers), cup)

  /** Trades for favor with the plain card at the site, returning the result. */
  private def trade(ready: ReadyGame, selected: Vector[PowerId])
      : (OathTransition, ReadyGame) = {
    val started = rules.startWalker(Ready(ready), ActionRef.Trade,
      PowerFixture.actor, selected, Vector(DecisionOptionRef.Button("favor")))
      .toOption.get
    val done = rules.resolveWalker(started.state, PowerFixture.actor,
      TradeProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(plainId)))
      .toOption.get
    (started.copy(events = started.events ++ done.events),
      done.state.asInstanceOf[Ready].value)
  }

  private def supply(ready: ReadyGame): Int = player(ready).board.supply.supply

  test("the Cup is a registered selected Trade modifier") {
    val power = CupOfPlenty.forCatalog(catalog).get
    assertEquals(power.cardId, cup)
    assertEquals(power.actions, Set[MajorActionType](MajorActionType.Trade))
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
  }

  test("trading costs the printed Supply without the Cup") {
    assertEquals(supply(trade(held(Vector.empty), Vector.empty)._2), 6)
  }

  test("a player with no faceup adviser trades for no Supply") {
    val ready = held(Vector.empty)
    val (transition, result) = trade(ready, modifiers)
    assertEquals(supply(result), 7)
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))
  }

  test("a faceup adviser of another suit does not stop the free trade") {
    val adviser = DenizenState(otherId, Orientation.FaceUp, Tokens.empty)
    assertEquals(supply(trade(held(Vector(adviser)), modifiers)._2), 7)
  }

  test("a faceup adviser of the traded card's suit makes the trade cost Supply") {
    assertEquals(supply(trade(held(Vector(matchingAdviser)), modifiers)._2), 6)
  }

  test("a facedown adviser of the traded card's suit does not count") {
    val facedown = DenizenState(matchingId, Orientation.FaceDown, Tokens.empty)
    assertEquals(supply(trade(held(Vector(facedown)), modifiers)._2), 7)
  }

  test("it is a Trade modifier, and only while the relic is faceup in play") {
    val offered = (ready: ReadyGame, action: ActionRef) =>
      rules.offerableWalkerPowers(ready, PowerFixture.actor, action)
        .toOption.get.map(_.id)
    assert(offered(held(Vector.empty), ActionRef.Trade).contains(CupOfPlenty.id))
    assert(!offered(held(Vector.empty), ActionRef.Muster).contains(CupOfPlenty.id))
    val facedown = PowerFixture.withRelic(act(), cup, Orientation.FaceDown)
    assert(!offered(facedown, ActionRef.Trade).contains(CupOfPlenty.id))
  }

  private def player(ready: ReadyGame): PlayerState =
    EconomyFixture.player(ready)
}
```

Create `src/test/scala/oathdigital/gameplay/powers/economy/RowdyPubSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.EconomyFixture
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powers.{CardStaging, MusterPowers, PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

class RowdyPubSuite extends munit.FunSuite {
  import EconomyFixture._
  import SearchFixture.rules

  private val pub = DenizenId("144")
  private val modifiers = Vector(RowdyPub.id)

  /** The actor's site holds Rowdy Pub and the plain card, both token-free. */
  private def pubAtSite(advisers: Vector[AdviserState] = Vector.empty)
      : ReadyGame = {
    val ready = CardStaging.without(act(advisers = advisers), pub)
    val siteId = PowerFixture.home(ready)
    ready.updateCurrent(c => c.copy(map = c.map.copy(sites = c.map.sites.updated(
      siteId, c.map.sites(siteId).copy(denizens = Vector(
        DenizenState(plainId, Orientation.FaceUp, Tokens.empty),
        DenizenState(pub, Orientation.FaceUp, Tokens.empty)))))))
  }

  /** Musters from `card` and returns the result and the whole journal. */
  private def muster(ready: ReadyGame, selected: Vector[PowerId],
      card: DenizenId): (OathTransition, ReadyGame) = {
    val started = rules.startWalker(Ready(ready), ActionRef.Muster,
      PowerFixture.actor, selected).toOption.get
    val done = rules.resolveWalker(started.state, PowerFixture.actor,
      MusterProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(card)))
      .toOption.get
    (started.copy(events = started.events ++ done.events),
      done.state.asInstanceOf[Ready].value)
  }

  private def warbands(ready: ReadyGame): Int =
    EconomyFixture.player(ready).board.warbands

  test("Rowdy Pub is a registered selected Muster modifier") {
    val power = RowdyPub.forCatalog(catalog).get
    assertEquals(power.cardId, pub)
    assertEquals(power.actions, Set[MajorActionType](MajorActionType.Muster))
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
  }

  test("mustering from Rowdy Pub gains one more warband") {
    val ready = pubAtSite()
    val (transition, result) = muster(ready, modifiers, pub)
    assertEquals(warbands(result), warbands(ready) + 2)
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))
  }

  test("the extra warband is on top of the matching-adviser bonus") {
    val ready = pubAtSite()
    val economy = MusterPowers.powers.map(_.id).toSet
    val hearthAdviser = catalog.denizens.find(d => d.suit == Suit.Hearth &&
      d.id.value != pub.value && !d.powers.exists(p => economy(p.id))).get
    val adviser = DenizenState(DenizenId(hearthAdviser.id.value),
      Orientation.FaceUp, Tokens.empty)
    val withAdviser = pubAtSite(Vector(adviser))
    val (_, result) = muster(withAdviser, modifiers, pub)
    assertEquals(warbands(result), warbands(ready) + 3)
  }

  test("mustering from another card, or without the selection, gains no extra") {
    val ready = pubAtSite()
    assertEquals(warbands(muster(ready, modifiers, plainId)._2),
      warbands(ready) + 1)
    assertEquals(warbands(muster(ready, Vector.empty, pub)._2),
      warbands(ready) + 1)
  }

  test("it is a Muster modifier, offered when the card is in reach") {
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(pubAtSite(),
      PowerFixture.actor, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Muster).contains(RowdyPub.id))
    assert(!offered(ActionRef.Trade).contains(RowdyPub.id))
    assert(!rules.offerableWalkerPowers(act(), PowerFixture.actor,
      ActionRef.Muster).toOption.get.map(_.id).contains(RowdyPub.id))
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/recover/RelicWorshipSuite.scala`:

```scala
package oathdigital.gameplay.powers.recover

import oathdigital.gameplay.CatacombsContributionSuite
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powers.{CardStaging, PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.model._
import oathdigital.model.OathState.Ready

class RelicWorshipSuite extends munit.FunSuite {
  import SearchFixture.rules

  private val worship = DenizenId("173")
  private val modifiers = Vector(RelicWorship.id)
  private val setup = new FirstGameSetupRules(catalog)

  /** A Recover site with a facedown relic, the actor holding Relic Worship as
    * a faceup adviser, with `secrets` faceup secrets and 4 Supply.
    */
  private def staged(secrets: Int = 2)
      : (ReadyGame, CatacombsContributionSuite.Fixture) = {
    val withRelic = CatacombsContributionSuite.relicSite(setup)
    val ready = PowerFixture.withBoard(PowerFixture.asAdviser(CardStaging
      .without(withRelic.ready, worship), worship))(board => board.copy(
      faceUpSecrets = secrets, supply = SupplyTrack(4)))
    (ready, withRelic.copy(ready = ready))
  }

  /** A whole Recover that succeeds and takes the site's relic. */
  private def recover(ready: ReadyGame, selected: Vector[PowerId],
      site: CatacombsContributionSuite.Fixture): (Vector[OathEvent], ReadyGame) = {
    val actor = PowerFixture.actor
    val started = rules.startWalker(Ready(ready), ActionRef.Recover, actor,
      selected).toOption.get
    val rolled = rules.rollWalkerPrepared(started.state, actor,
      RecoverProcedure.recoverPool)(count => Right(
        Vector.fill(count)(DefenseDieFace.TwoShields))).toOption.get
    val relic = RecoverProcedure.actorFacedownRelics(
      rolled.state.asInstanceOf[Ready].value, actor).head.id
    val done = rules.resolveWalker(rolled.state, actor,
      RecoverProcedure.relicDecisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Relic(relic))).toOption.get
    (started.events ++ rolled.events ++ done.events,
      done.state.asInstanceOf[Ready].value)
  }

  private def me(ready: ReadyGame): PlayerState = PowerFixture.player(ready)

  test("Relic Worship is a registered selected Recover modifier that costs a secret") {
    val power = RelicWorship.forCatalog(catalog).get
    assertEquals(power.cardId, worship)
    assertEquals(power.actions, Set[MajorActionType](MajorActionType.Recover))
    assertEquals(power.cost, Cost(secret = 1))
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
  }

  test("after recovering a relic the player pays a secret onto the card and " +
      "gains 2 Supply") {
    val (ready, site) = staged()
    val (events, result) = recover(ready, modifiers, site)
    assertEquals(me(result).relics.size, me(ready).relics.size + 1)
    assertEquals(me(result).board.faceUpSecrets, 1)
    assertEquals(me(result).advisers.collectFirst {
      case card: DenizenState if card.id == worship => card.tokens
    }, Some(Tokens(0, 1)))
    // 4 Supply, less 1 for the roll, plus 2.
    assertEquals(me(result).board.supply.supply, 4 - 1 + 2)
    assertEquals(PaidActionHarness.replayed(rules, ready, events), result)
    // The dice-pool change every Recover starts with does not round-trip the
    // wire (its window is not kept), so it is left out of the check.
    assert(PaidActionHarness.wireRoundTrips(events.filterNot {
      case step: oathdigital.gameplay.walker.WalkerStepRecorded =>
        step.ops.exists(_.isInstanceOf[ModifyDicePool])
      case _ => false
    }))
  }

  test("without the selection nothing is paid and nothing is gained") {
    val (ready, site) = staged()
    val (_, result) = recover(ready, Vector.empty, site)
    assertEquals(me(result).board.faceUpSecrets, 2)
    assertEquals(me(result).board.supply.supply, 4 - 1)
  }

  test("a Recover that ends without a relic has still paid the secret, and " +
      "gains nothing") {
    val (ready, _) = staged()
    val actor = PowerFixture.actor
    val started = rules.startWalker(Ready(ready), ActionRef.Recover, actor,
      modifiers).toOption.get
    // The secret is paid at the start, before any roll.
    val paid = started.state.asInstanceOf[Ready].value
    assertEquals(me(paid).board.faceUpSecrets, 1)
    assertEquals(me(paid).advisers.collectFirst {
      case card: DenizenState if card.id == worship => card.tokens
    }, Some(Tokens(0, 1)))
    val failed = rules.rollWalkerPrepared(started.state, actor,
      RecoverProcedure.recoverPool)(count => Right(
        Vector.fill(count)(DefenseDieFace.Blank))).toOption.get
    val stopped = rules.resolveWalker(failed.state, actor,
      RecoverProcedure.choiceDecisionId, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Button("stop"))).toOption.get
    val result = stopped.state.asInstanceOf[Ready].value
    assertEquals(me(result).board.faceUpSecrets, 1)
    assertEquals(me(result).board.supply.supply, 4 - 1)
  }

  test("Catacombs and Relic Worship with one faceup secret are refused at " +
      "selection, and accepted with two") {
    val catacombs = PowerId("denizen.catacombs")
    def attempt(secrets: Int) = {
      val fixture = CatacombsContributionSuite.reliclessSite(setup, secrets)
      val ready = PowerFixture.asAdviser(CardStaging.without(fixture.ready,
        worship), worship)
      rules.startWalker(Ready(ready), ActionRef.Recover, PowerFixture.actor,
        Vector(catacombs, RelicWorship.id))
    }
    val refused = attempt(1)
    assert(refused.left.toOption.exists(_.toString.contains(
      "cannot all be paid together")), refused.toString)
    assert(attempt(2).isRight)
  }

  test("it cannot be selected without a faceup secret, or onto an occupied card") {
    val (broke, _) = staged(secrets = 0)
    assert(rules.startWalker(Ready(broke), ActionRef.Recover, PowerFixture.actor,
      modifiers).isLeft)
    val (ready, _) = staged()
    val occupied = PowerFixture.updateActor(ready)(p => p.copy(advisers =
      p.advisers.map {
        case card: DenizenState if card.id == worship =>
          card.copy(tokens = Tokens(0, 1))
        case other => other
      }))
    assert(rules.startWalker(Ready(occupied), ActionRef.Recover,
      PowerFixture.actor, modifiers).isLeft)
  }

  test("a Recover with the card in reach records no ignored-rule diagnostic") {
    val (ready, site) = staged()
    val (events, _) = recover(ready, Vector.empty, site)
    assert(!events.exists(_.isInstanceOf[OathEvent.IgnoredRulesRecorded]))
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/search/AugurySuite.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.{CardStaging, PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class AugurySuite extends munit.FunSuite {
  import PowerFixture._
  import SearchFixture._

  private val augury = DenizenId("56")
  private val modifiers = Vector(Augury.id)
  private val plain: Vector[DenizenId] = Suit.all.flatMap(denizensOf)

  private def withAugury(top: Vector[WorldCardId]): ReadyGame =
    atHome(CardStaging.without(SearchFixture.staged(top), augury), augury)

  private def hand(transition: OathTransition): Vector[WorldCardId] =
    SearchFixture.after(transition).game.current.temporaryHands(actor)

  test("Augury is a registered selected Search modifier") {
    val power = Augury.forCatalog(catalog).get
    assertEquals(power.cardId, augury)
    assertEquals(power.actions, Set[MajorActionType](MajorActionType.Search))
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
  }

  test("a world Search draws one card more than the printed three") {
    val top = plain.take(6)
    val plainSearch = start(withAugury(top)).toOption.get
    assertEquals(hand(plainSearch), top.take(3))
    val ready = withAugury(top)
    val started = start(ready, modifiers).toOption.get
    assertEquals(hand(started), top.take(4))
    // The cost is the printed one: Augury adds a card, not a price.
    assertEquals(player(SearchFixture.after(started)).board.supply.supply, 5 - 2)
    assertEquals(PaidActionHarness.replayed(rules, ready, started.events),
      SearchFixture.after(started))
    assert(PaidActionHarness.wireRoundTrips(started.events))
  }

  test("the player keeps any one of the drawn cards") {
    val top = plain.take(6)
    val kept = top(3)
    val done = play(withAugury(top), modifiers, kept, "discard")
    assertEquals(SearchFixture.after(done).game.current.temporaryHands(actor),
      Vector.empty)
  }

  test("the draw still stops after a Vision, wherever the Vision falls") {
    val early = plain.take(2) ++ Vector(VisionRules.Faith) ++ plain.drop(2).take(3)
    val stopped = start(withAugury(early), modifiers).toOption.get
    assertEquals(hand(stopped), early.take(3))
    // A Vision is the fourth card, past the printed three: it is drawn, and the
    // draw counts it.
    val late = plain.take(3) ++ Vector(VisionRules.Faith) ++ plain.drop(3).take(2)
    val ready = withAugury(late)
    val reached = start(ready, modifiers).toOption.get
    assertEquals(hand(reached), late.take(4))
    assertEquals(SearchFixture.after(reached).game.current.tracks.visionsDrawn,
      ready.game.current.tracks.visionsDrawn + 1)
    assertEquals(PaidActionHarness.replayed(rules, ready, reached.events),
      SearchFixture.after(reached))
  }

  test("a Search from a regional discard draws one card more from its top") {
    val region = player(base).pawnSite.flatMap(base.game.current.map.regionOf).get
    val pile = plain.take(5)
    val staged = pile.foldLeft(withAugury(Vector.empty))(CardStaging.without(_, _))
    val ready = staged.updateCurrent(c => c.copy(commonCards =
      c.commonCards.copy(regionalDiscards = c.commonCards.regionalDiscards
        .updated(region, pile))))
    val started = rules.startWalker(OathState.Ready(ready), ActionRef.Search,
      actor, modifiers, Vector(DecisionOptionRef.Button(
        s"search:regional-discard:${region.key}"))).toOption.get
    // The pile is drawn from its end, and Augury takes a fourth card.
    assertEquals(hand(started), pile.reverse.take(4))
  }

  test("without the selection the draw is the printed three") {
    val top = plain.take(6)
    assertEquals(hand(start(withAugury(top)).toOption.get), top.take(3))
  }

  test("it is not offered when the card is out of reach") {
    assert(start(SearchFixture.staged(plain.take(6)), modifiers).isLeft)
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/search/TruthfulHarpSuite.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.powers.{CardStaging, PowerFixture, SearchFixture, TargetsFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class TruthfulHarpSuite extends munit.FunSuite {
  import PowerFixture._
  import SearchFixture._

  private val harp = RelicId("R04")
  private val plain: Vector[DenizenId] = Suit.all.flatMap(denizensOf)
  private val onlyHarp = Vector(TruthfulHarp.id)
  private val both = Vector(TruthfulHarp.id, Augury.id)

  private def withHarp(top: Vector[WorldCardId]): ReadyGame =
    withRelic(SearchFixture.staged(top), harp)

  private def hand(transition: OathTransition): Vector[WorldCardId] =
    SearchFixture.after(transition).game.current.temporaryHands(actor)

  private def known(transition: OathTransition, viewer: PlayerId)
      : Vector[WorldCardId] = SearchFixture.after(transition).knowledge
    .advisers.getOrElse(viewer, Vector.empty)

  test("the Harp is a registered selected Search modifier") {
    val power = TruthfulHarp.forCatalog(catalog).get
    assertEquals(power.cardId, harp)
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
  }

  test("a Search draws two more cards, and every card drawn is revealed") {
    val top = plain.take(7)
    val ready = withHarp(top)
    val started = start(ready, onlyHarp).toOption.get
    assertEquals(hand(started), top.take(5))
    TargetsFixture.others(ready).foreach(viewer =>
      assertEquals(known(started, viewer).toSet, top.take(5).toSet[WorldCardId]))
    assertEquals(PaidActionHarness.replayed(rules, ready, started.events),
      SearchFixture.after(started))
    assert(PaidActionHarness.wireRoundTrips(started.events))
  }

  test("the reveal does not change the play: the kept card can still be played " +
      "facedown as an adviser, as usual") {
    val top = plain.take(7)
    val ready = withHarp(top)
    val done = play(ready, onlyHarp, top(4), "adviser-facedown")
    val advisers = player(SearchFixture.after(done)).advisers
    assert(advisers.exists {
      case card: DenizenState => card.id == top(4) &&
        card.orientation == Orientation.FaceDown
      case _ => false
    })
    // The other players saw the card while it was revealed, and remember it.
    // That is what a reveal at a table leaves behind, and it needs no rule.
    val other = TargetsFixture.others(ready).head
    assert(known(done, other).contains(top(4)))
  }

  test("the Harp and Augury stack, and the reveal covers all six cards") {
    val top = plain.take(8)
    val augury = DenizenId("56")
    val ready = atHome(CardStaging.without(withHarp(top), augury), augury)
    val started = start(ready, both).toOption.get
    assertEquals(hand(started), top.take(6))
    TargetsFixture.others(ready).foreach(viewer =>
      assertEquals(known(started, viewer).toSet, top.take(6).toSet[WorldCardId]))
  }

  test("nothing is revealed without the Harp") {
    val top = plain.take(7)
    val started = start(withHarp(top)).toOption.get
    assertEquals(hand(started), top.take(3))
    TargetsFixture.others(withHarp(top)).foreach(viewer =>
      assertEquals(known(started, viewer), Vector.empty[WorldCardId]))
  }

  test("a facedown Harp cannot be selected") {
    val facedown = withRelic(SearchFixture.staged(plain.take(7)), harp,
      Orientation.FaceDown)
    assert(start(facedown, onlyHarp).isLeft)
  }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, for example `not found: value CupOfPlenty`.

- [ ] **Step 3: Implement**

Create `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.gameplay.powers.economy.{CupOfPlenty, RowdyPub}
import oathdigital.gameplay.powers.recover.RelicWorship
import oathdigital.gameplay.powers.search.{Augury, TruthfulHarp}

/** The Search, Trade, Muster and Recover modifiers, registered together. A
  * power whose card is absent from `catalog` is omitted.
  */
object ActionModifiers {
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Augury.forCatalog(catalog).toVector ++
      TruthfulHarp.forCatalog(catalog).toVector ++
      CupOfPlenty.forCatalog(catalog).toVector ++
      RowdyPub.forCatalog(catalog).toVector ++
      RelicWorship.forCatalog(catalog).toVector
}
```

Create `src/main/scala/oathdigital/gameplay/powers/economy/CupOfPlenty.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.economy.{MusterSource, TradeProcedure}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{CatalogCards, PowerAnswers, SelectedModifier}
import oathdigital.model._

/** The Cup of Plenty (relic R07), a selected Trade modifier: trading with a
  * card whose suit differs from every faceup adviser the player holds costs no
  * Supply. A facedown adviser does not count, and a player with no faceup
  * adviser trades free.
  *
  * The card traded with is the answer to the Trade's source decision, which the
  * cost node cannot see, so the Supply payment is replaced by a node that reads
  * the answer when it runs and pays only when the suits match.
  */
final case class CupOfPlenty private (cardId: RelicId,
    catalog: ExecutableCatalog) extends SelectedModifier {
  def id: PowerId = CupOfPlenty.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeCost -> Vector(Transform((ctx, operations) =>
      operations.map {
        case pay @ SpendSupply(player, _, _) if player == ctx.activePlayer =>
          unlessFree(ctx.activePlayer, pay)
        case other => other
      })))

  private def unlessFree(actor: PlayerId, pay: SpendSupply): Operation =
    BuildOps((ready, pending) => Right(
      if (differs(ready, actor, pending)) Vector.empty
      else Vector[CoreOperation](pay)))

  /** Whether the card traded with matches none of the faceup advisers. The suit
    * is read off the answered card, not resolved as a source: this node runs
    * after the payment that placed a secret on the card, which a source must
    * not yet hold.
    */
  private def differs(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Boolean = (for {
    ref <- PowerAnswers.one(pending, TradeProcedure.decisionId)
    suit <- ref match {
      case DecisionOptionRef.Denizen(id) => catalog.suitOf(id)
      case DecisionOptionRef.Edifice(id) => catalog.suitOf(id)
      case _ => None
    }
  } yield MusterSource.matching(catalog, ready, actor, suit) == 0)
    .getOrElse(false)
}

object CupOfPlenty {
  val id: PowerId = PowerId("relic.cup-of-plenty")

  def forCatalog(catalog: ExecutableCatalog): Option[CupOfPlenty] =
    CatalogCards.relic(catalog, id).map(new CupOfPlenty(_, catalog))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/economy/RowdyPub.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{CatalogCards, PlayerFacts, PowerAnswers, SelectedModifier}
import oathdigital.model._

/** Rowdy Pub (card 144), a selected Muster modifier: mustering from Rowdy Pub
  * gains one more warband, on top of the bonus for matching advisers.
  *
  * The card mustered from is the answer to the Muster's source decision, read
  * when the gain node runs, so the extra warband is added only when Rowdy Pub is
  * the source. The gain is best-effort like the base gain.
  */
final case class RowdyPub private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier {
  def id: PowerId = RowdyPub.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Muster)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterGain -> Vector(Transform((ctx, operations) =>
      operations :+ bonus(ctx.activePlayer))))

  private def bonus(actor: PlayerId): Operation = BuildOps((ready, pending) =>
    if (PowerAnswers.one(pending, MusterProcedure.decisionId)
        .contains(DecisionOptionRef.Denizen(cardId)))
      PlayerFacts.forceKind(ready, actor).map(kind =>
        Vector[CoreOperation](Gain.Warbands(actor, kind, RowdyPub.Warbands)))
    else Right(Vector.empty))
}

object RowdyPub {
  val id: PowerId = PowerId("denizen.rowdy-pub")
  val Warbands: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[RowdyPub] =
    CatalogCards.denizen(catalog, id).map(new RowdyPub(_, catalog))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/recover/RelicWorship.scala`:

```scala
package oathdigital.gameplay.powers.recover

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** Relic Worship (card 173), a selected Recover modifier. Cost: 1 secret placed
  * on the card. After recovering a relic, gain 2 Supply.
  *
  * The secret is paid at the start of the Recover, like every modifier's cost
  * (the kit does it), and the gain follows the move that takes the relic, at
  * `RecoverAfterRelic`. A Recover that ends without a relic has still paid the
  * secret and gains nothing: the player owns the choice to select it. Selecting
  * needs a faceup secret and an empty card, and every selected modifier's payment
  * is checked together at selection, so Catacombs and Relic Worship with one
  * faceup secret are refused at the start instead of stranding the Recover.
  */
final case class RelicWorship private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier {
  def id: PowerId = RelicWorship.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Recover)
  override def cost: Cost = Cost(secret = RelicWorship.Secrets)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.RecoverAfterRelic -> Vector(Transform((ctx, operations) =>
      operations :+ GainSupply(ctx.activePlayer, RelicWorship.Supply))))
}

object RelicWorship {
  val id: PowerId = PowerId("denizen.relic-worship")
  val Secrets: Int = 1
  val Supply: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[RelicWorship] =
    CatalogCards.denizen(catalog, id).map(new RelicWorship(_, catalog))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/search/Augury.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** Augury (card 56), a selected Search modifier: a Search from the world deck
  * or a regional discard draws one more card. The draw still stops after a
  * Vision.
  */
final case class Augury private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier {
  def id: PowerId = Augury.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchBeforeDraw -> Vector(Transform((_, operations) =>
      DrawExtension.extend(operations, Augury.More))))
}

object Augury {
  val id: PowerId = PowerId("denizen.augury")
  val More: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[Augury] =
    CatalogCards.denizen(catalog, id).map(new Augury(_, catalog))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/search/DrawExtension.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.actions.SearchRules
import oathdigital.model._

/** How a Search modifier makes the draw take more cards.
  *
  * A Search's draw is one windowed `BuildOps` (`SearchBeforeDraw`) that
  * returns a `Draw`. The extension wraps that node so its result draws `more`
  * cards beyond what it already draws, by asking [[SearchRules.draw]] for the
  * whole longer draw again. Asking the rule rather than counting cards here
  * keeps the draw's own limits: a world draw still stops after a Vision, a
  * pile that is short gives what it has, and a regional pile is drawn from its
  * top.
  *
  * The extension adds to what the wrapped node drew, so two extensions compose
  * in either order. It replaces the count of Visions drawn along with the cards,
  * because a longer draw may reach a Vision the printed one did not.
  */
private[search] object DrawExtension {

  def extend(operations: Vector[Operation], more: Int): Vector[Operation] =
    operations.map {
      case node: BuildOps if node.window.contains(PowerWindow.SearchBeforeDraw) =>
        node.copy(build = (ready, pending) =>
          node.build(ready, pending).flatMap(extended(ready, more)))
      case other => other
    }

  private def extended(ready: ReadyGame, more: Int)(
      operations: Vector[CoreOperation])
      : Either[OathViolation, Vector[CoreOperation]] =
    operations.collectFirst { case draw: Draw => draw } match {
      case None => Right(operations)
      case Some(draw) => for {
        source <- sourceOf(draw.source)
        origin <- ready.game.current.players.find(_.player == draw.player)
          .flatMap(_.pawnSite).flatMap(ready.game.current.map.regionOf)
          .toRight(OathViolation.PawnSiteMissing(draw.player))
        cards <- SearchRules.draw(ready, source, origin,
          extra = draw.cards.size + more - SearchRules.DrawSize)
      } yield Vector[CoreOperation](draw.copy(cards = cards)) ++
        Option.when(source == SearchSource.WorldDeck &&
          cards.exists(_.isInstanceOf[VisionId]))(AdvanceVisionsDrawn)
    }

  private def sourceOf(location: Location)
      : Either[OathViolation, SearchSource] = location match {
    case Location.Deck(CardDeck.World) => Right(SearchSource.WorldDeck)
    case Location.RegionalDiscard(region) =>
      Right(SearchSource.RegionalDiscard(region))
    case other => Left(OathViolation.InvalidEventOrder(
      s"a Search draws from a world deck or a regional discard, not $other"))
  }
}
```

Create `src/main/scala/oathdigital/gameplay/powers/search/TruthfulHarp.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** The Truthful Harp (relic R04), a selected Search modifier: a Search draws 2
  * more cards, and every card drawn is revealed while it is in the player's
  * hand. The Harp and Augury stack.
  *
  * A card in a temporary hand is private to its owner and has no orientation,
  * so it cannot be turned faceup. It is revealed by recording that every other
  * player has seen it (`Peek`), which is what a public reveal amounts to in the
  * recorded state: the identity of a card that later becomes a facedown
  * adviser is then known to all. The reveal is a node of its own after the
  * draw, so it covers every card drawn whichever other extension ran.
  */
final case class TruthfulHarp private (cardId: RelicId,
    catalog: ExecutableCatalog) extends SelectedModifier {
  def id: PowerId = TruthfulHarp.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchBeforeDraw -> Vector(Transform((ctx, operations) =>
      DrawExtension.extend(operations, TruthfulHarp.More) :+
        reveal(ctx.activePlayer))))

  private def reveal(actor: PlayerId): Operation = BuildOps((ready, _) => Right(
    for {
      viewer <- ready.game.current.players.map(_.player).filter(_ != actor)
      card <- ready.game.current.temporaryHands.getOrElse(actor, Vector.empty)
    } yield Peek(viewer, card, Location.Hand(actor))))
}

object TruthfulHarp {
  val id: PowerId = PowerId("relic.truthful-harp")
  val More: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[TruthfulHarp] =
    CatalogCards.relic(catalog, id).map(new TruthfulHarp(_, catalog))
}
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, replace:

```scala
      CardPlayTriggers.forCatalog(catalog) ++
```

with:

```scala
      ActionModifiers.forCatalog(catalog) ++
      CardPlayTriggers.forCatalog(catalog) ++
```

In `src/main/scala/oathdigital/gameplay/actions/Search.scala`, replace:

```scala
  /** World decks use head-as-top; discard piles use last-as-top. */
  def draw(ready: ReadyGame, source: SearchSource,
      origin: Region): Either[OathViolation, Vector[WorldCardId]] =
    cost(ready, source, origin).map { _ => source match {
      case SearchSource.WorldDeck =>
        ready.game.current.commonCards.worldDeck.take(3)
          .takeThrough(_.isInstanceOf[VisionId])
      case SearchSource.RegionalDiscard(region) =>
        ready.game.current.commonCards.discard(region).reverse.take(3)
    }}
```

with:

```scala
  /** How many cards a Search draws before any power changes it. */
  val DrawSize: Int = 3

  /** World decks use head-as-top; discard piles use last-as-top. `extra` is the
    * number of cards a power adds to the printed draw.
    */
  def draw(ready: ReadyGame, source: SearchSource, origin: Region,
      extra: Int = 0): Either[OathViolation, Vector[WorldCardId]] =
    cost(ready, source, origin).map { _ => source match {
      case SearchSource.WorldDeck =>
        ready.game.current.commonCards.worldDeck.take(DrawSize + extra)
          .takeThrough(_.isInstanceOf[VisionId])
      case SearchSource.RegionalDiscard(region) =>
        ready.game.current.commonCards.discard(region).reverse
          .take(DrawSize + extra)
    }}
```

In `src/main/scala/oathdigital/gameplay/powers/RecoverPowers.scala`, replace:

```scala
  object RelicWorship extends ReviewedPower("denizen.relic-worship", modifier,
    Vector(ReviewedHandler.automatic(PowerWindow.RecoverBeforeFirstRoll)))
```

with:

```scala
  object RelicWorship extends ReviewedPower("denizen.relic-worship", modifier,
    Vector(ReviewedHandler.selected(PowerWindow.RecoverBeforeFirstRoll,
      implemented = true)))
```

- [ ] **Step 4: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.AugurySuite oathdigital.gameplay.powers.search.TruthfulHarpSuite oathdigital.gameplay.powers.economy.CupOfPlentySuite oathdigital.gameplay.powers.economy.RowdyPubSuite oathdigital.gameplay.powers.recover.RelicWorshipSuite oathdigital.gameplay.SearchProcedureSuite oathdigital.gameplay.SearchSuite oathdigital.gameplay.RuleResolutionSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS (74 tests in these suites and the ones they touch).

- [ ] **Step 5: Run the whole suite and the architecture check**

Run: `./sbtw test` and `python3 scripts/check-architecture.py`
Expected: PASS, and `architecture check passed`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: implement Augury, Truthful Harp, Cup of Plenty, Rowdy Pub and Relic Worship

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```


- [ ] **Step: Record sub-slice 2d**

Follow "Recording a sub-slice" below for 2d and commit with `docs: record slice 2d`.

---

## Sub-slice 2e: Target protection

### Task 10: A window on Conspiracy's target decision (E7)

**Files:**
- Modify: `model/PowerWindow.scala`, `powers/whenplayed/ConspiracyWhenPlayed.scala`
- Test: `powers/whenplayed/ConspiracyTargetWindowSuite.scala`

**Interfaces:**
- Produces: `PowerWindow.ConspiracyTargetSelection` (key `"conspiracy.target-selection"`), on the `Decide` with id `ConspiracyWhenPlayed.decisionId`.
- Consumes: nothing new.

Conspiracy's effects insisted on an answer whenever a legal target existed. A power at the new window may now remove every target, and the decision is then not asked (fact 10), so the effects treat "no decision was asked" as "take nothing". The card still plays and leaves the game.

- [ ] **Step 1: Write the tests**

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyTargetWindowSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Transform}
import oathdigital.gameplay.powers.PowerFixture
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._

/** The window on Conspiracy's target decision: a power can remove targets, and
  * when it removes every one the decision is not asked and nothing is taken.
  */
class ConspiracyTargetWindowSuite extends munit.FunSuite {
  import PowerFixture._

  private val conspiracy = VisionRules.Conspiracy
  private val enemy: PlayerId = base.game.current.players.map(_.player)
    .find(_ != actor).get
  private val relics = Vector(RelicId("R10"), RelicId("R11"))

  /** The actor holds Conspiracy in a temporary hand. The enemy stands on the
    * actor's site holding `relics`, faceup.
    */
  private def staged: ReadyGame = {
    val current = base.game.current
    val site = player(base).pawnSite
    base.updateCurrent(_.copy(
      players = current.players.map(p =>
        if (p.player == enemy) p.copy(pawnSite = site,
          relics = relics.map(RelicState(_, Orientation.FaceUp, Tokens.empty)))
        else p),
      banners = current.banners.copy(
        peoplesFavor = current.banners.peoplesFavor.copy(holder = None),
        darkestSecret = current.banners.darkestSecret.copy(holder = None)),
      commonCards = current.commonCards.copy(
        worldDeck = current.commonCards.worldDeck.filterNot(_ == conspiracy),
        relicDeck = current.commonCards.relicDeck.filterNot(relics.contains)),
      map = current.map.copy(sites = current.map.sites.map { case (id, s) =>
        id -> s.copy(relics = s.relics.filterNot(r => relics.contains(r.id))) }),
      temporaryHands = current.temporaryHands.updated(actor,
        Vector(conspiracy))))
  }

  private def removing(keep: DecisionOptionRef => Boolean): ContributingPower =
    new ContributingPower {
      def id: PowerId = PowerId("test.conspiracy-window")
      def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
        PowerWindow.ConspiracyTargetSelection -> Vector(Transform((_, ops) =>
          ops.flatMap {
            case decide: Decide => decide.query match {
              case one: DecisionQuery.ChooseOne =>
                val options = one.options.filter(o => keep(o.ref))
                if (options.isEmpty) Vector.empty
                else Vector(decide.copy(query = one.copy(options = options)))
              case _ => Vector(decide)
            }
            case other => Vector(other)
          })))
    }

  private def hook: CardPlayedFaceup =
    CardPlayedFaceup(conspiracy, RuleSourceRef.Adviser(actor, conspiracy))

  private def walk(power: Option[ContributingPower])
      : (WalkerPowers, WalkerOutcome) = {
    val powers = WalkerPowers(ConspiracyWhenPlayed +: power.toVector)
    (powers, ProcedureWalker.advance(staged, hook, None, powers).toOption.get)
  }

  test("the target decision carries the Conspiracy target window") {
    assertEquals(PowerWindow.ConspiracyTargetSelection.key,
      "conspiracy.target-selection")
    val (powers, parked) = walk(None)
    val decide = ProcedureWalker.parkedDecide(staged, hook,
      parked.asInstanceOf[WalkerOutcome.Parked].tree, powers).get
    assertEquals(decide.window, Some(PowerWindow.ConspiracyTargetSelection))
    assertEquals(decide.query.asInstanceOf[DecisionQuery.ChooseOne].options
      .map(_.ref), Vector[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(enemy, 0),
      DecisionOptionRef.RelicSlot(enemy, 1)))
  }

  test("a power at the window removes the targets it forbids") {
    val only = DecisionOptionRef.RelicSlot(enemy, 1)
    val (powers, parked) = walk(Some(removing(_ == only)))
    val decide = ProcedureWalker.parkedDecide(staged, hook,
      parked.asInstanceOf[WalkerOutcome.Parked].tree, powers).get
    assertEquals(decide.query.asInstanceOf[DecisionQuery.ChooseOne].options
      .map(_.ref), Vector[DecisionOptionRef](only))
  }

  test("when every target is removed nothing is asked and nothing is taken, " +
      "and the card still leaves the game") {
    val (_, outcome) = walk(Some(removing(_ => false)))
    val done = outcome.asInstanceOf[WalkerOutcome.Finished].treeless
    assertEquals(player(done, enemy).relics.map(_.id), relics)
    assertEquals(player(done).relics, player(staged).relics)
    assertEquals(done.game.current.temporaryHands(actor), Vector.empty)
    assert(!CardIndex.from(done.game).toOption.get.ids.contains(conspiracy))
  }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, for example `value ConspiracyTargetSelection is not a member of object oathdigital.model.PowerWindow`.

- [ ] **Step 3: Implement**

In `src/main/scala/oathdigital/model/PowerWindow.scala`, replace:

```scala
  case object PlaceBannerResourceEligibility extends OtherWindow {
```

with:

```scala
  /** The target decision of a played Conspiracy: a power may remove targets
    * from it, and a decision left with none is not asked.
    */
  case object ConspiracyTargetSelection extends OtherWindow {
    val key = "conspiracy.target-selection"
  }
  case object PlaceBannerResourceEligibility extends OtherWindow {
```

In `src/main/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala`, replace:

```scala
    else Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(options,
      heading = Some("Conspiracy: choose an enemy asset to take"))))
```

with:

```scala
    else Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(options,
      heading = Some("Conspiracy: choose an enemy asset to take")),
      window = Some(PowerWindow.ConspiracyTargetSelection)))
```

In `src/main/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala`, replace:

```scala
      case None if legal.isEmpty => Right(Vector.empty)
      case None => Left(OathViolation.ConspiracyUnavailable(
        "a legal target must be chosen"))
```

with:

```scala
      // No decision was asked: no target was legal, or a power at the
      // target window removed every one. The walker never skips a decision
      // that is asked, so nothing is taken.
      case None => Right(Vector.empty)
```

- [ ] **Step 4: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.ConspiracyTargetWindowSuite oathdigital.gameplay.powers.whenplayed.ConspiracyWhenPlayedSuite oathdigital.gameplay.SearchProcedureSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS (46 tests in these suites and the ones they touch).

- [ ] **Step 5: Run the whole suite and the architecture check**

Run: `./sbtw test` and `python3 scripts/check-architecture.py`
Expected: PASS, and `architecture check passed`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: add a window on the Conspiracy target decision

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```


### Task 11: Circlet of Command, the Oaken Fortress and the Rotting Fortress

**Files:**
- Create: `powers/targeting/{CircletOfCommand,FortressRules,TargetProtections}.scala`
- Modify: `powers/WalkerPowerCatalog.scala`
- Test: `powers/targeting/{TargetingFixture,CircletOfCommandSuite,FortressRulesSuite}.scala`

**Interfaces:**
- Produces: `CircletOfCommand`, `OakenFortress`, `RottingFortress` (each with `id` and `forCatalog(catalog)`), `TargetProtections.forCatalog(catalog)`. Test support `TargetingFixture` (`rules`, `fortressAt`, `ruledBy`, `unruled`, `pawnAt`, `holds`, `adviserOf`, `optionsAt`, `start`, `ready`, `playerOf`).
- Consumes: Task 10's `ConspiracyTargetSelection`; Task 3's `CardStaging`; Task 7's `CatalogCards`; `CampaignSetup.raidDefenders`/`legalKinds`, `CampaignIds`, `BannerRules.holder`, `OptionRestriction`, `Restriction`.

All three are persistent rules.

- **Circlet of Command** (faceup): players other than the holder cannot target the holder's banners or the holder's relics other than the Circlet. It restricts a Raid's optional targets (`CampaignTargetSelection`, an `OptionRestriction`), a Challenge's banner choice (`ChallengeBannerSelection`, an `OptionRestriction`) and Conspiracy's target (a `Transform` at `ConspiracyTargetSelection` that drops the decision when empty). The Circlet itself and the Raid's pawn target stay targetable.
- **Oaken Fortress** (intact E28): its ruler, while at the fortress site and ruling it, cannot be targeted by a Challenge or a Raid. **Rotting Fortress** (ruined E28): players at the site, the ruler included, cannot be targeted unless the targeting player holds a faceup beast adviser. Empire is ignored. A Conquest is unaffected. Both take the protected player out of a Raid's defender decision and a Challenge's banner choice, remove Raid from the kind decision when every co-located enemy is protected, and refuse a Campaign whose only legal kind is such a Raid (fact 9). The refusal applies until the Campaign has answered one of its own decisions, read from the pending position (fact 7), so it holds for a Campaign a power runs inside another action too (Task 13).

- [ ] **Step 1: Write the tests**

Create `src/test/scala/oathdigital/gameplay/powers/targeting/CircletOfCommandSuite.scala`:

```scala
package oathdigital.gameplay.powers.targeting

import oathdigital.gameplay.{CampaignFixture, ChallengeFixture}
import oathdigital.gameplay.CampaignFixture.raidBoard
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{CardStaging, PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer

class CircletOfCommandSuite extends munit.FunSuite {
  import TargetingFixture._

  private val raid = ChooseOneAnswer(DecisionOptionRef.Button("raid"))

  test("the Circlet is a registered persistent rule, so it is automatic") {
    val power = CircletOfCommand.forCatalog(catalog).get
    assertEquals(power.cardId, circlet)
    assertEquals(power.resolution, PowerResolution.Automatic)
  }

  // ---- Raid ----

  /** The Raid board with the Circlet faceup on the defender, beside the relic and
    * the banners the board already gives them. Returns the Raid's target options.
    */
  private def raidTargets(circletSide: Option[Orientation],
      attacker: Boolean = false): Vector[DecisionOptionRef] = {
    val (b, _) = raidBoard()
    val held = circletSide.fold(b.ready)(side =>
      holds(b.ready, if (attacker) b.actor else b.other, circlet, side))
    val started = start(held, ActionRef.Campaign, b.actor).toOption.get
    val kind = rules.resolveWalker(started.state, b.actor, CampaignIds.kind, raid)
      .toOption.get
    optionsAt(kind, ActionRef.Campaign)
  }

  test("a Raid may not target the holder's other relics or banners, but may " +
      "target the Circlet") {
    val (b, relic) = raidBoard()
    assertEquals(raidTargets(None).toSet, Set[DecisionOptionRef](
      DecisionOptionRef.Relic(relic), DecisionOptionRef.Banner(Banner.PeoplesFavor),
      DecisionOptionRef.Banner(Banner.DarkestSecret)))
    assertEquals(raidTargets(Some(Orientation.FaceUp)),
      Vector[DecisionOptionRef](DecisionOptionRef.Relic(circlet)))
  }

  test("a facedown Circlet protects nothing") {
    val (_, relic) = raidBoard()
    assert(raidTargets(Some(Orientation.FaceDown))
      .contains(DecisionOptionRef.Relic(relic)))
  }

  test("the holder's own Raid is not restricted by their Circlet") {
    // The defender holds no Circlet; the attacker does. The defender's things
    // stay targetable: the Circlet protects only its holder.
    val (_, relic) = raidBoard()
    assert(raidTargets(Some(Orientation.FaceUp), attacker = true)
      .contains(DecisionOptionRef.Relic(relic)))
  }

  // ---- Challenge ----

  private def challengeBanners(circletSide: Option[Orientation])
      : Vector[DecisionOptionRef] = {
    val (base, _) = ChallengeFixture.ready(resources = 2)
    val actor = ChallengeFixture.active(base)
    val withHolder = ChallengeFixture.enemyHolds(base, Banner.PeoplesFavor, 2)
    val enemy = ChallengeFixture.enemy(withHolder).player
    val held = circletSide.fold(withHolder)(holds(withHolder, enemy, circlet, _))
    optionsAt(start(held, ActionRef.Challenge, actor).toOption.get,
      ActionRef.Challenge)
  }

  test("a Challenge may not name a banner its holder's Circlet protects") {
    val banner = (b: Banner) => DecisionOptionRef.Banner(b): DecisionOptionRef
    assertEquals(challengeBanners(None).toSet, Set(banner(Banner.PeoplesFavor),
      banner(Banner.DarkestSecret)))
    assertEquals(challengeBanners(Some(Orientation.FaceUp)),
      Vector(banner(Banner.DarkestSecret)))
    assertEquals(challengeBanners(Some(Orientation.FaceDown)).toSet,
      Set(banner(Banner.PeoplesFavor), banner(Banner.DarkestSecret)))
  }

  // ---- Conspiracy ----

  private val conspiracy = VisionRules.Conspiracy
  private val other = RelicId("R10")

  /** The actor plays Conspiracy at a site the enemy shares; the enemy holds the
    * Circlet, one other relic and the People's Favor banner.
    */
  private def conspiracyTargets(circletSide: Orientation)
      : (ReadyGame, PlayerId, Vector[DecisionOptionRef]) = {
    val base = PowerFixture.base
    val actor = PowerFixture.actor
    val enemy = base.game.current.players.map(_.player).find(_ != actor).get
    val current = base.game.current
    val site = PowerFixture.player(base).pawnSite
    val staged = CardStaging.without(CardStaging.without(base, conspiracy), other)
      .updateCurrent(c => c.copy(
        players = c.players.map(p => if (p.player == enemy)
          p.copy(pawnSite = site) else p),
        banners = c.banners.copy(
          peoplesFavor = c.banners.peoplesFavor.copy(holder = Some(enemy)),
          darkestSecret = c.banners.darkestSecret.copy(holder = None)),
        temporaryHands = c.temporaryHands.updated(actor, Vector(conspiracy))))
    val ready = holds(holds(staged, enemy, other), enemy, circlet, circletSide)
    val hook = CardPlayedFaceup(conspiracy, RuleSourceRef.Adviser(actor, conspiracy))
    val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
      Vector.empty)
    val parked = ProcedureWalker.advance(ready, hook, None, powers).toOption.get
    val options = parked match {
      case WalkerOutcome.Parked(pending, _) =>
        ProcedureWalker.parkedDecide(ready, hook, pending, powers).get.query
          .asInstanceOf[DecisionQuery.ChooseOne].options.map(_.ref)
      case _ => Vector.empty
    }
    (ready, enemy, options)
  }

  test("Conspiracy may take the Circlet, but not the holder's other relic or banner") {
    val (_, enemy, options) = conspiracyTargets(Orientation.FaceUp)
    val slots = PowerFixture.player(conspiracyTargets(Orientation.FaceUp)._1, enemy)
      .relics.map(_.id)
    assertEquals(slots, Vector(other, circlet))
    assertEquals(options, Vector[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(enemy, 1)))
  }

  test("a facedown Circlet leaves every target open") {
    val (_, enemy, options) = conspiracyTargets(Orientation.FaceDown)
    assertEquals(options.toSet, Set[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(enemy, 0), DecisionOptionRef.RelicSlot(enemy, 1),
      DecisionOptionRef.Banner(Banner.PeoplesFavor)))
  }

  test("the Circlet's rule ignores an option that is not a banner or a relic") {
    val power = CircletOfCommand.forCatalog(catalog).get
    val ready = holds(PowerFixture.base, PowerFixture.actor, circlet)
    val ctx = PowerCtx(ready, PowerFixture.actor, power.source,
      PowerWindow.CampaignTargetSelection, Vector.empty,
      Decide("x", PowerFixture.actor, DecisionQuery.ChooseOne(Vector.empty)))
    val restriction = power.contributions(PowerWindow.CampaignTargetSelection)
      .head.asInstanceOf[oathdigital.gameplay.powerresolver.OptionRestriction]
    assertEquals(restriction.fn(ctx, DecisionOptionRef.Site(SiteId("s"))), None)
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/targeting/FortressRulesSuite.scala`:

```scala
package oathdigital.gameplay.powers.targeting

import oathdigital.gameplay.{CampaignFixture, ChallengeFixture}
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignProcedure}
import oathdigital.gameplay.powerresolver.{PowerCtx, Restriction}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer

class FortressRulesSuite extends munit.FunSuite {
  import CampaignFixture._
  import TargetingFixture._

  private val powers = WalkerPowers.selected(
    WalkerPowerCatalog.default(catalog), Vector.empty)
  private val conquest = DecisionOptionRef.Button("conquest")
  private val raid = DecisionOptionRef.Button("raid")

  private def player(b: Board, id: PlayerId): PlayerState = playerOf(b.ready, id)
  private def third(b: Board): PlayerId = b.ready.game.current.players
    .map(_.player).find(id => id != b.actor && id != b.other).get

  /** The Campaign board with the enemy at the actor's origin, the Fortress on
    * `side` there, and the enemy holding the rule of the origin when `ruled`.
    */
  private def fortified(side: EdificeSide, ruled: Boolean): Board = {
    val b = withEnemyAtOrigin(board(warbands = 4))
    val staged = fortressAt(b.ready, side, b.origin)
    b.copy(ready = if (ruled) ruledBy(staged, b.origin, b.other)
      else unruled(staged, b.origin))
  }

  private def startOf(b: Board) = start(b.ready, ActionRef.Campaign, b.actor)

  test("the Fortress faces are registered persistent rules, so they are automatic") {
    assertEquals(OakenFortress.forCatalog(catalog).get.resolution,
      PowerResolution.Automatic)
    assertEquals(RottingFortress.forCatalog(catalog).get.resolution,
      PowerResolution.Automatic)
    assertEquals(OakenFortress.forCatalog(catalog).get.id,
      PowerId("edifice.e28.intact"))
  }

  // ---- Oaken Fortress ----

  test("the Oaken Fortress removes its ruler from a Raid: only the Conquest is left") {
    val b = fortified(EdificeSide.Intact, ruled = true)
    val started = startOf(b).toOption.get
    assertEquals(optionsAt(started, ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest))
  }

  test("without the Fortress the same board offers both") {
    val b = withEnemyAtOrigin(board(warbands = 4))
    val ruled = b.copy(ready = ruledBy(b.ready, b.origin, b.other))
    assertEquals(optionsAt(startOf(ruled).toOption.get, ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest, raid))
  }

  test("the Oaken Fortress protects its ruler only while the ruler is at the " +
      "site: a ruler elsewhere may be raided") {
    val b = withEnemyAtOrigin(board(extras = 1, warbands = 4))
    val elsewhere = b.extras.head
    val staged = ruledBy(fortressAt(b.ready, EdificeSide.Intact, elsewhere),
      elsewhere, b.other)
    assertEquals(optionsAt(start(ruledBy(staged, b.origin, b.other),
      ActionRef.Campaign, b.actor).toOption.get, ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest, raid))
  }

  test("a second enemy at the site keeps the Raid, and the ruler is not " +
      "offered as its defender") {
    val b = fortified(EdificeSide.Intact, ruled = true)
    val crowded = pawnAt(b.ready, third(b), b.origin)
    val started = start(crowded, ActionRef.Campaign, b.actor).toOption.get
    assertEquals(optionsAt(started, ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest, raid))
    val kind = TargetingFixture.rules.resolveWalker(started.state, b.actor, CampaignIds.kind,
      ChooseOneAnswer(raid)).toOption.get
    assertEquals(optionsAt(kind, ActionRef.Campaign),
      Vector[DecisionOptionRef](DecisionOptionRef.Player(third(b))))
  }

  // ---- Rotting Fortress ----

  test("a Raid whose only defenders stand at a Rotting Fortress cannot start") {
    val b = fortified(EdificeSide.Ruined, ruled = false)
    assertEquals(CampaignProcedure.startable(catalog, b.ready, b.actor, powers),
      false)
    assert(startOf(b).left.toOption.exists(
      _.isInstanceOf[OathViolation.CampaignUnavailable]))
  }

  test("a faceup beast adviser lifts the Rotting Fortress's protection") {
    val b = fortified(EdificeSide.Ruined, ruled = false)
    val armed = b.copy(ready = adviserOf(b.ready, b.actor, Suit.Beast))
    assert(CampaignProcedure.startable(catalog, armed.ready, armed.actor, powers))
    assert(startOf(armed).isRight)
    val facedown = b.copy(ready = adviserOf(b.ready, b.actor, Suit.Hearth))
    assert(startOf(facedown).isLeft)
  }

  test("the Rotting Fortress protects every player at the site, so a second " +
      "enemy does not open the Raid") {
    val b = fortified(EdificeSide.Ruined, ruled = false)
    val crowded = b.copy(ready = pawnAt(b.ready, third(b), b.origin))
    assert(startOf(crowded).isLeft)
  }

  test("the Rotting Fortress leaves a Conquest of the same site alone") {
    val b = fortified(EdificeSide.Ruined, ruled = true)
    assertEquals(optionsAt(startOf(b).toOption.get, ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest))
  }

  test("a Campaign already under way is never refused by the start rule") {
    val b = fortified(EdificeSide.Ruined, ruled = false)
    val power = RottingFortress.forCatalog(catalog).get
    val restriction = power.contributions(PowerWindow.CampaignActionEligibility)
      .head.asInstanceOf[Restriction]
    def ctx(state: ReadyGame) = PowerCtx(state, b.actor, power.source,
      PowerWindow.CampaignActionEligibility, Vector.empty,
      Sequence(Vector.empty))
    assert(restriction.fn(ctx(b.ready), Sequence(Vector.empty)).nonEmpty)
    // The Campaign has answered its force decision: it is under way.
    val underway = b.ready.updateCurrent(_.copy(walkerPending =
      Some(PendingTree(Vector("2"), Vector(Answered(CampaignIds.force,
        DecisionAnswer.ChooseAmountAnswer(0), b.actor))))))
    assertEquals(restriction.fn(ctx(underway), Sequence(Vector.empty)), None)
  }

  test("a Fortress that protects nobody in the Raid does not disturb it") {
    val b = withEnemyAtOrigin(board(extras = 1, warbands = 4))
    // The Rotting Fortress stands where no Raid is being made.
    val ready = fortressAt(b.ready, EdificeSide.Ruined, b.extras.head)
    val started = start(ready, ActionRef.Campaign, b.actor).toOption.get
    assertEquals(optionsAt(started, ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest, raid))
  }

  // ---- Challenge ----

  private def challengeBanners(side: EdificeSide, ruled: Boolean)
      : Vector[DecisionOptionRef] = {
    val (base, _) = ChallengeFixture.ready(resources = 2)
    val actor = ChallengeFixture.active(base)
    val held = ChallengeFixture.enemyHolds(base, Banner.PeoplesFavor, 2)
    val enemy = ChallengeFixture.enemy(held).player
    val site = playerOf(held, actor).pawnSite.get
    val staged = fortressAt(held, side, site)
    val ready = if (ruled) ruledBy(staged, site, enemy) else staged
    optionsAt(start(ready, ActionRef.Challenge, actor).toOption.get,
      ActionRef.Challenge)
  }

  test("a Challenge may not name a banner a protected player holds") {
    val banner = (b: Banner) => DecisionOptionRef.Banner(b): DecisionOptionRef
    // Oaken: the holder rules the site. Rotting: the holder is at the site.
    assertEquals(challengeBanners(EdificeSide.Intact, ruled = true),
      Vector(banner(Banner.DarkestSecret)))
    assertEquals(challengeBanners(EdificeSide.Ruined, ruled = false),
      Vector(banner(Banner.DarkestSecret)))
    // An Oaken Fortress the holder does not rule protects nobody.
    assertEquals(challengeBanners(EdificeSide.Intact, ruled = false).toSet,
      Set(banner(Banner.PeoplesFavor), banner(Banner.DarkestSecret)))
  }
}
```

Create `src/test/scala/oathdigital/gameplay/powers/targeting/TargetingFixture.scala`:

```scala
package oathdigital.gameplay.powers.targeting

import oathdigital.gameplay.{CampaignFixture, OathRules}
import oathdigital.gameplay.powers.{CardStaging, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers, WalkerProcedureRegistry}
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Shared staging and reading for the suites of the rules that keep a player
  * from being targeted.
  */
object TargetingFixture {
  val rules: OathRules = CampaignFixture.rules(powers = true)
  val fortress: EdificeId = EdificeId("E28")
  val circlet: RelicId = RelicId("R15")

  def ready(transition: OathTransition): ReadyGame =
    transition.state.asInstanceOf[Ready].value

  def playerOf(state: ReadyGame, id: PlayerId): PlayerState =
    state.game.current.players.find(_.player == id).get

  /** The Fortress on `side` at `site`, taken from wherever it was. */
  def fortressAt(state: ReadyGame, side: EdificeSide, site: SiteId): ReadyGame =
    CardStaging.without(state, fortress).updateCurrent(c => c.copy(map =
      c.map.copy(sites = c.map.sites.updated(site, c.map.sites(site).copy(
        denizens = c.map.sites(site).denizens :+
          EdificeState(fortress, side, Tokens.empty))))))

  /** `site` ruled by `ruler`. */
  def ruledBy(state: ReadyGame, site: SiteId, ruler: PlayerId): ReadyGame =
    state.updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(site, c.map.sites(site).copy(forces =
        SiteForces.Occupied(ForceKind.Exile(playerOf(state, ruler).lineage),
          2))))))

  def unruled(state: ReadyGame, site: SiteId): ReadyGame =
    state.updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(site, c.map.sites(site).copy(forces =
        SiteForces.Empty)))))

  def pawnAt(state: ReadyGame, player: PlayerId, site: SiteId): ReadyGame =
    state.updateCurrent(c => c.copy(players = c.players.map(p =>
      if (p.player == player) p.copy(pawnSite = Some(site)) else p)))

  /** `player` holds the relic, faceup or facedown. */
  def holds(state: ReadyGame, player: PlayerId, relic: RelicId,
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    CardStaging.without(state, relic).updateCurrent(c => c.copy(players =
      c.players.map(p => if (p.player == player) p.copy(relics = p.relics :+
        RelicState(relic, orientation, Tokens.empty)) else p)))

  /** `player` holds a faceup adviser of `suit`. */
  def adviserOf(state: ReadyGame, player: PlayerId, suit: Suit): ReadyGame = {
    val card = DenizenId(catalog.denizens.find(_.suit == suit).get.id.value)
    CardStaging.without(state, card).updateCurrent(c => c.copy(players =
      c.players.map(p => if (p.player == player) p.copy(advisers = p.advisers :+
        DenizenState(card, Orientation.FaceUp, Tokens.empty)) else p)))
  }

  /** The options of the decision a parked walk of `procedure` is waiting on. */
  def optionsAt(transition: OathTransition, procedure: ProcedureRef)
      : Vector[DecisionOptionRef] = {
    val state = ready(transition)
    val current = state.game.current
    val tree = WalkerProcedureRegistry.rebuild(procedure, catalog, state,
      current.turn.activePlayer, current.walkerStartArgs).toOption.get
    val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
      current.walkerModifiers)
    ProcedureWalker.parkedDecide(state, tree, current.walkerPending.get, powers)
      .get.query match {
      case one: DecisionQuery.ChooseOne => one.options.map(_.ref)
      case many: DecisionQuery.ChooseMany => many.options.map(_.ref)
      case other => throw new AssertionError(s"unexpected query $other")
    }
  }

  def start(state: ReadyGame, procedure: ActionRef, actor: PlayerId)
      : Either[OathViolation, OathTransition] =
    rules.startWalker(Ready(state), procedure, actor)
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, for example `not found: value CircletOfCommand`.

- [ ] **Step 3: Implement**

Create `src/main/scala/oathdigital/gameplay/powers/targeting/CircletOfCommand.scala`:

```scala
package oathdigital.gameplay.powers.targeting

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, OptionRestriction, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution}
import oathdigital.model._

/** The Circlet of Command (relic R15), a persistent rule of a faceup relic:
  * players other than the holder cannot target the holder's banners, or the
  * holder's relics other than the Circlet itself. A facedown Circlet does
  * nothing.
  *
  * It restricts the three decisions that name a banner or a relic of another
  * player:
  *
  *  - a Raid's optional targets (`CampaignTargetSelection`), which lists the
  *    defender's faceup relics and banners;
  *  - a Challenge's banner choice (`ChallengeBannerSelection`);
  *  - a played Conspiracy's target (`ConspiracyTargetSelection`). A decision
  *    left with no option is dropped there, so a Conspiracy with no target
  *    left plays and takes nothing.
  *
  * A Raid's mandatory target, the defender's pawn, is not a banner or a relic
  * and stays a target.
  */
final case class CircletOfCommand private (cardId: RelicId,
    catalog: ExecutableCatalog) extends ContributingPower {
  def id: PowerId = CircletOfCommand.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignTargetSelection -> Vector(OptionRestriction(guard)),
    PowerWindow.ChallengeBannerSelection -> Vector(OptionRestriction(guard)),
    PowerWindow.ConspiracyTargetSelection -> Vector(Transform(dropShielded)))

  private def guard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = Option.when(shields(ctx, ref))(
    OathViolation.InvalidEventOrder(
      "the Circlet of Command protects its holder's banners and relics"))

  private def dropShielded(ctx: PowerCtx, operations: Vector[Operation])
      : Vector[Operation] = operations.flatMap {
    case decide: Decide => decide.query match {
      case one: DecisionQuery.ChooseOne =>
        val options = one.options.filterNot(option => shields(ctx, option.ref))
        if (options.isEmpty) Vector.empty
        else Vector(decide.copy(query = one.copy(options = options)))
      case _ => Vector(decide)
    }
    case other => Vector(other)
  }

  /** Whether `ref` names a banner or a relic of the holder that the acting
    * player may not target.
    */
  private def shields(ctx: PowerCtx, ref: DecisionOptionRef): Boolean = {
    val current = ctx.state.game.current
    val holder = current.players.find(_.relics.exists {
      case RelicState(`cardId`, Orientation.FaceUp, _) => true
      case _ => false
    }).map(_.player)
    def held(owner: PlayerId): Boolean = holder.contains(owner) &&
      owner != ctx.activePlayer
    ref match {
      case DecisionOptionRef.Banner(banner) =>
        BannerRules.holder(current, banner).exists(held)
      case DecisionOptionRef.Relic(relic) => relic != cardId &&
        current.players.find(_.relics.exists(_.id == relic)).exists(p =>
          held(p.player))
      case DecisionOptionRef.RelicSlot(owner, slot) => held(owner) &&
        !current.players.find(_.player == owner).flatMap(_.relics.lift(slot))
          .exists(_.id == cardId)
      case _ => false
    }
  }
}

object CircletOfCommand {
  val id: PowerId = PowerId("relic.circlet-of-command")

  def forCatalog(catalog: ExecutableCatalog): Option[CircletOfCommand] =
    CatalogCards.relic(catalog, id).map(new CircletOfCommand(_, catalog))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/targeting/FortressRules.scala`:

```scala
package oathdigital.gameplay.powers.targeting

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignSetup}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, OptionRestriction, PowerCtx, Restriction}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution}
import oathdigital.model._

/** A rule of the Fortress edifice (E28) that keeps a player from being the
  * target of a Challenge or a Raid. A Conquest is not affected. Each face is a
  * persistent rule of the edifice wherever it stands, so it applies to every
  * player, and it needs no selection.
  *
  * A protected player is taken out of the decisions that name them:
  *
  *  - a Raid's defender decision (`CampaignDefenderSelection`), which is asked
  *    when several enemy pawns stand at the attacker's site;
  *  - a Challenge's banner choice (`ChallengeBannerSelection`), which loses the
  *    banner a protected player holds.
  *
  * Campaign asks neither of those when it needs no choice, so two more hooks
  * cover a single defender. The kind decision loses its Raid when every enemy
  * pawn at the site is protected, and a Campaign whose only legal kind is such a
  * Raid is refused when it starts. That refusal applies only until the Campaign
  * has answered one of its own decisions: once a Campaign is under way it must
  * be able to finish, whatever the board has since become. (A Conquest that
  * takes the site leaves the Raid as the only legal kind, for one.) The answers
  * are read from the pending position the restriction is checked against, so it
  * holds for a Campaign that a power runs inside another action as well.
  */
sealed abstract class FortressRule extends ContributingPower {
  def catalog: ExecutableCatalog
  protected def fortress: EdificeId
  protected def side: EdificeSide

  /** Whether `defender` may not be targeted by `attacker`. */
  protected def shields(ready: ReadyGame, attacker: PlayerId,
      defender: PlayerId): Boolean

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  final override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  final def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignKindSelection -> Vector(OptionRestriction(kindGuard)),
    PowerWindow.CampaignDefenderSelection ->
      Vector(OptionRestriction(defenderGuard)),
    PowerWindow.CampaignActionEligibility ->
      Vector(Restriction((ctx, _) => startGuard(ctx))),
    PowerWindow.ChallengeBannerSelection ->
      Vector(OptionRestriction(bannerGuard)))

  private def blocked(detail: String): OathViolation =
    OathViolation.CampaignUnavailable(detail)

  /** The sites where this face of the Fortress stands. */
  protected final def sites(ready: ReadyGame): Vector[SiteId] =
    ready.game.current.map.sites.collect {
      case (id, site) if site.denizens.exists {
        case card: EdificeState => card.id == fortress && card.side == side
        case _ => false
      } => id
    }.toVector

  protected final def pawnSite(ready: ReadyGame, player: PlayerId)
      : Option[SiteId] = ready.game.current.players.find(_.player == player)
    .flatMap(_.pawnSite)

  private def raidBlocked(ready: ReadyGame, attacker: PlayerId): Boolean = {
    val defenders = CampaignSetup.raidDefenders(ready, attacker)
    defenders.nonEmpty && defenders.forall(shields(ready, attacker, _))
  }

  private def kindGuard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = ref match {
    case DecisionOptionRef.Button("raid")
        if raidBlocked(ctx.state, ctx.activePlayer) =>
      Some(blocked("a Fortress protects every player a Raid could target"))
    case _ => None
  }

  private def defenderGuard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = ref match {
    case DecisionOptionRef.Player(defender)
        if shields(ctx.state, ctx.activePlayer, defender) =>
      Some(blocked(s"a Fortress protects ${defender.value} from a Raid"))
    case _ => None
  }

  private def startGuard(ctx: PowerCtx): Option[OathViolation] =
    Option.when(!underway(ctx.state) &&
      CampaignSetup.legalKinds(ctx.state, ctx.activePlayer) ==
        Vector(CampaignKind.Raid) && raidBlocked(ctx.state, ctx.activePlayer))(
      blocked("a Fortress protects every player a Raid could target"))

  /** The Campaign has answered one of its own decisions. */
  private def underway(ready: ReadyGame): Boolean =
    ready.game.current.walkerPending.exists(_.answered.exists(answered =>
      CampaignIds.all.contains(answered.decisionId)))

  private def bannerGuard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = ref match {
    case DecisionOptionRef.Banner(banner)
        if BannerRules.holder(ctx.state.game.current, banner)
          .exists(shields(ctx.state, ctx.activePlayer, _)) =>
      Some(OathViolation.InvalidEventOrder(
        s"a Fortress protects the holder of ${banner.key} from a Challenge"))
    case _ => None
  }
}

/** The Oaken Fortress (E28, intact): while its ruler is at this site, they
  * cannot be targeted by a Challenge or a Raid. Empire rulers are not
  * supported.
  */
final case class OakenFortress private (fortress: EdificeId,
    catalog: ExecutableCatalog) extends FortressRule {
  def id: PowerId = OakenFortress.id
  protected def side: EdificeSide = EdificeSide.Intact

  protected def shields(ready: ReadyGame, attacker: PlayerId,
      defender: PlayerId): Boolean = defender != attacker &&
    sites(ready).exists(site => pawnSite(ready, defender).contains(site) &&
      ready.game.current.map.sites.get(site).flatMap(state =>
        SiteRule.ruler(state.forces, ready.game.current.players).toOption)
        .contains(SiteRuler.Player(defender)))
}

object OakenFortress {
  val id: PowerId = PowerId("edifice.e28.intact")

  def forCatalog(catalog: ExecutableCatalog): Option[OakenFortress] =
    CatalogCards.edifice(catalog, id).map(new OakenFortress(_, catalog))
}

/** The Rotting Fortress (E28, ruined): players at this site cannot be targeted
  * by a Challenge or a Raid, unless the targeting player has a faceup beast
  * adviser.
  */
final case class RottingFortress private (fortress: EdificeId,
    catalog: ExecutableCatalog) extends FortressRule {
  def id: PowerId = RottingFortress.id
  protected def side: EdificeSide = EdificeSide.Ruined

  protected def shields(ready: ReadyGame, attacker: PlayerId,
      defender: PlayerId): Boolean = defender != attacker &&
    sites(ready).exists(site => pawnSite(ready, defender).contains(site)) &&
    !holdsBeastAdviser(ready, attacker)

  private def holdsBeastAdviser(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player).exists(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) =>
        catalog.suitOf(card).contains(Suit.Beast)
      case _ => false
    })
}

object RottingFortress {
  val id: PowerId = PowerId("edifice.e28.ruined")

  def forCatalog(catalog: ExecutableCatalog): Option[RottingFortress] =
    CatalogCards.edifice(catalog, id).map(new RottingFortress(_, catalog))
}
```

Create `src/main/scala/oathdigital/gameplay/powers/targeting/TargetProtections.scala`:

```scala
package oathdigital.gameplay.powers.targeting

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The persistent rules that keep a player from being targeted by a Raid, a
  * Challenge or a Conspiracy, registered together. A power whose card is absent
  * from `catalog` is omitted.
  */
object TargetProtections {
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    CircletOfCommand.forCatalog(catalog).toVector ++
      OakenFortress.forCatalog(catalog).toVector ++
      RottingFortress.forCatalog(catalog).toVector
}
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, replace:

```scala
import oathdigital.gameplay.powers.rest.{LeagueTreatyContribution, SilverTongue}
```

with:

```scala
import oathdigital.gameplay.powers.rest.{LeagueTreatyContribution, SilverTongue}
import oathdigital.gameplay.powers.targeting.TargetProtections
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, replace:

```scala
      CardPlayTriggers.forCatalog(catalog) ++
```

with:

```scala
      TargetProtections.forCatalog(catalog) ++
      CardPlayTriggers.forCatalog(catalog) ++
```

- [ ] **Step 4: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.targeting.CircletOfCommandSuite oathdigital.gameplay.powers.targeting.FortressRulesSuite oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.CampaignProcedureSuite oathdigital.gameplay.CampaignRaidSuite oathdigital.gameplay.CampaignSetupSuite oathdigital.gameplay.ChallengeProcedureSuite oathdigital.gameplay.OptionRestrictionSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS (112 tests in these suites and the ones they touch).

- [ ] **Step 5: Run the whole suite and the architecture check**

Run: `./sbtw test` and `python3 scripts/check-architecture.py`
Expected: PASS, and `architecture check passed`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: implement Circlet of Command and the Fortress rules

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```


- [ ] **Step: Record sub-slice 2e**

Follow "Recording a sub-slice" below for 2e and commit with `docs: record slice 2e`.

---

## Sub-slice 2f: Knights Errant

### Task 12: Restrictions see what a Transform inserts and the answers of the command

**Files:**
- Modify: `walker/WalkerPowerGather.scala`, `walker/ProcedureWalker.scala`, `OathRulesWalker.scala`
- Test: `RestrictionAnswersSuite.scala`

**Interfaces:**
- Produces: `ProcedureWalker.restrictionViolations(tree, powers, state, activePlayer, answered: Vector[Answered] = Vector.empty)`. The traversal folds each windowed composite and `Branch` through the powers as the walk does, and selects each `Branch` with `answered`. `OathRulesWalker` passes the pending position's answers on a resumed command, and the command that answers a decision also checks against the answers plus the one it records, after the walker has accepted it.
- Consumes: nothing new.

This answers the product owner's question, "does the Restriction not catch the Campaign procedure?". It does not, for two reasons that are in `WalkerPowerGather.restrictionViolations` (fact 8): it walks the declared tree, and Knights Errant's nested Campaign is inserted by a `Transform`, so it is never visited; and every `Branch` was resolved with an empty `PendingTree`, so a Branch that yields its children only once a decision is answered showed nothing. Whole-tree restrictions (Vow of Peace) and the Fortress refusal therefore now apply to any subtree a power adds, and Task 13 relies on it. The suite pins both blind spots with test powers; `KnightsErrantSuite` shows Vow of Peace and the Fortress refusing the nested Campaign.

- [ ] **Step 1: Write the tests**

Create `src/test/scala/oathdigital/gameplay/RestrictionAnswersSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, PowerCtx, Restriction, Transform}
import oathdigital.gameplay.setup.FirstGameSetupFixture.initialReady
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers}
import oathdigital.model._

/** The restriction traversal selects a `Branch` with the answers recorded so
  * far, so a Restriction hooked inside a subtree that only exists once a
  * decision is answered is checked once it does.
  */
class RestrictionAnswersSuite extends munit.FunSuite {
  private val actor = initialReady.game.current.turn.activePlayer
  private val nested = PowerWindow.CampaignActionEligibility
  private val ask = "test.ask"
  private val yes = DecisionOptionRef.Button("yes")
  private val no = DecisionOptionRef.Button("no")
  private val violation = OathViolation.CampaignUnavailable("the test forbids it")

  private val forbidding: ContributingPower = new ContributingPower {
    def id: PowerId = PowerId("test.forbidding")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(nested -> Vector(Restriction((_: PowerCtx, _) => Some(violation))))
  }
  private val powers = WalkerPowers(Vector(forbidding))

  /** A decision, then a subtree that exists only when it was answered "yes". */
  private val tree: Operation = Sequence(Vector[Operation](
    Decide(ask, actor, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(yes, "Yes"), DecisionOption.Button(no, "No")))),
    Branch((_, pending) => if (pending.answered.exists {
      case Answered(`ask`, DecisionAnswer.ChooseOneAnswer(`yes`), _) => true
      case _ => false
    }) Vector(Sequence(Vector.empty, Some(nested))) else Vector.empty)))

  private def answered(ref: DecisionOptionRef): Vector[Answered] =
    Vector(Answered(ask, DecisionAnswer.ChooseOneAnswer(ref), actor))

  private def violations(answers: Vector[Answered]): Vector[OathViolation] =
    ProcedureWalker.restrictionViolations(tree, powers, initialReady, actor,
      answers)

  test("a Restriction inside a subtree the answers have not opened is not checked") {
    assertEquals(violations(Vector.empty), Vector.empty[OathViolation])
    assertEquals(violations(answered(no)), Vector.empty[OathViolation])
  }

  test("once the answer opens the subtree its Restriction is checked") {
    assertEquals(violations(answered(yes)), Vector[OathViolation](violation))
  }

  test("the answers default to none, so every existing caller is unchanged") {
    assertEquals(ProcedureWalker.restrictionViolations(tree, powers,
      initialReady, actor), Vector.empty[OathViolation])
  }

  // ---- What a Transform inserts ----

  private val inserting: ContributingPower = new ContributingPower {
    def id: PowerId = PowerId("test.inserting")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.MusterActionEligibility -> Vector(Transform((_, children) =>
        children :+ Sequence(Vector.empty, Some(nested)))))
  }
  private val root: Operation =
    Sequence(Vector.empty, Some(PowerWindow.MusterActionEligibility))

  test("a subtree a Transform inserts is checked too, not only the declared tree") {
    val without = WalkerPowers(Vector(forbidding))
    assertEquals(ProcedureWalker.restrictionViolations(root, without,
      initialReady, actor), Vector.empty[OathViolation])
    val both = WalkerPowers(Vector(forbidding, inserting))
    assertEquals(ProcedureWalker.restrictionViolations(root, both,
      initialReady, actor), Vector[OathViolation](violation))
  }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, for example `too many arguments (found 5, expected 4) for method restrictionViolations: (tree: oathdigital.model.Operation, powers: oathdigital.gameplay.walker.WalkerPowers, state: oathdigital.model.ReadyGame, activePlayer: oathdigital.model.PlayerId): Vector[oathdigital.model.OathViolation]`.

- [ ] **Step 3: Implement**

In `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`, replace:

```scala
import oathdigital.model.{Branch, Decide,
```

with:

```scala
import oathdigital.model.{Answered, Branch, Decide,
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`, replace:

```scala
  def restrictionViolations(tree: Operation, powers: WalkerPowers,
      state: ReadyGame, activePlayer: PlayerId): Vector[OathViolation] = {
```

with:

```scala
  def restrictionViolations(tree: Operation, powers: WalkerPowers,
      state: ReadyGame, activePlayer: PlayerId,
      answered: Vector[Answered] = Vector.empty): Vector[OathViolation] = {
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`, replace:

```scala
        case branch: Branch => descend(branch.select(state,
          PendingTree(at = path, answered = Vector.empty)))
```

with:

```scala
        case branch: Branch => descend(branch.select(state,
          PendingTree(at = path, answered = answered)))
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`, replace:

```scala
        case branch: Branch => descend(branch.select(state,
          PendingTree(at = path, answered = answered)))
        case _ => descend(node.children)
```

with:

```scala
        case branch: Branch => descend(applyWindow(branch.window, branch,
          state, activePlayer, powers, path, branch.select(state,
            PendingTree(at = path, answered = answered)),
          state.game.current.walkerProcedure)._1)
        case _ => descend(applyWindow(node.window, node, state,
          activePlayer, powers, path, node.children,
          state.game.current.walkerProcedure)._1)
```

In `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`, replace:

```scala
  def restrictionViolations(tree: Operation, powers: WalkerPowers,
      state: ReadyGame, activePlayer: PlayerId): Vector[OathViolation] =
    WalkerPowerGather.restrictionViolations(tree, powers, state, activePlayer)
```

with:

```scala
  def restrictionViolations(tree: Operation, powers: WalkerPowers,
      state: ReadyGame, activePlayer: PlayerId,
      answered: Vector[oathdigital.model.Answered] = Vector.empty)
      : Vector[OathViolation] = WalkerPowerGather.restrictionViolations(tree,
    powers, state, activePlayer, answered)
```

In `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala`, replace:

```scala
  private def checkRestrictions(tree: Operation, powers: WalkerPowers,
      ready: ReadyGame, actor: PlayerId): Either[OathViolation, Unit] =
    ProcedureWalker.restrictionViolations(tree, powers, ready, actor)
      .headOption.toLeft(())
```

with:

```scala
  private def checkRestrictions(tree: Operation, powers: WalkerPowers,
      ready: ReadyGame, actor: PlayerId,
      answered: Vector[Answered] = Vector.empty): Either[OathViolation, Unit] =
    ProcedureWalker.restrictionViolations(tree, powers, ready, actor, answered)
      .headOption.toLeft(())
```

In `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala`, replace:

```scala
      powers = walkerPowers(ready, activePlayer, modifiers)
      _ <- checkRestrictions(tree, powers, ready, activePlayer)
    } yield (ready, procedure, tree, pending, powers, modifiers, startArgs)
```

with:

```scala
      powers = walkerPowers(ready, activePlayer, modifiers)
      _ <- checkRestrictions(tree, powers, ready, activePlayer,
        pending.answered)
    } yield (ready, procedure, tree, pending, powers, modifiers, startArgs)
```

In `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala`, replace:

```scala
      case (ready, procedure, tree, pending, powers, modifiers, startArgs) =>
        walkerCall(ProcedureWalker.resolve(ready, tree, pending,
          Answered(decisionId, answer, by = requester),
          powers, walkerDice)).flatMap(walkerTransition(state, procedure, tree, _,
            powers, modifiers, startArgs))
```

with:

```scala
      case (ready, procedure, tree, pending, powers, modifiers, startArgs) =>
        val recorded = Answered(decisionId, answer, by = requester)
        walkerCall(ProcedureWalker.resolve(ready, tree, pending, recorded,
          powers, walkerDice)).flatMap(outcome => checkRestrictions(tree,
          powers, ready, ready.game.current.turn.activePlayer,
          pending.answered :+ recorded).map(_ => outcome))
          .flatMap(walkerTransition(state, procedure, tree, _, powers,
            modifiers, startArgs))
```

- [ ] **Step 4: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.RestrictionAnswersSuite oathdigital.gameplay.OathRulesWalkerPowerSuite oathdigital.gameplay.ProcedureWalkerSuite oathdigital.gameplay.OptionRestrictionSuite oathdigital.gameplay.CampaignProcedureSuite oathdigital.gameplay.ChallengeProcedureSuite oathdigital.gameplay.SearchProcedureSuite oathdigital.gameplay.MusterProcedureSuite oathdigital.gameplay.PendingWalkerRulesSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS (161 tests in these suites and the ones they touch).

- [ ] **Step 5: Run the whole suite and the architecture check**

Run: `./sbtw test` and `python3 scripts/check-architecture.py`
Expected: PASS, and `architecture check passed`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: check restrictions against the answers a command carries

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```


### Task 13: Knights Errant

**Files:**
- Create: `powers/economy/KnightsErrant.scala`
- Modify: `powers/WalkerPowerCatalog.scala`, `actions/economy/MusterProcedure.scala`, `walker/WalkerProcedureRegistry.scala`
- Test: `powers/economy/KnightsErrantSuite.scala`

**Interfaces:**
- Produces: `KnightsErrant` (`id`, `forCatalog`, `decisionId = "muster.knights-errant.campaign"`, `campaignOption`, `declineOption`), `MusterProcedure.decisionPrefix = "muster."`.
- Consumes: Task 5's `PowerCtx.procedure`; Task 7's `SelectedModifier`; Task 11's `TargetingFixture` and Fortress; Task 12's restriction traversal; `CampaignProcedure.rebuild`, `CampaignSetup.legalKinds`, `PowerAnswers.one`.

After mustering the player may campaign, spending no Supply. Two nodes are appended to the Muster's tree at `MusterActionEligibility`: a live decision, asked only when a Campaign is legal, and a node that once the answer is "campaign" builds `CampaignProcedure.rebuild(...)` from live state when it is walked, so the Campaign sees the warbands the Muster gained and is rebuilt on every resume as a Campaign is. A transform at `CampaignCost` removes the Supply payment when `ctx.procedure` is Muster. The Muster registry entry now recognises the Campaign's decision ids and the `muster.` prefix, so the client is told the right continuation. The suite runs a whole nested Campaign to its end and replays its journal.

With Task 12, a restriction on the whole Campaign applies to the nested one: answering "campaign" is rejected with `CampaignUnavailable` when Vow of Peace forbids it, or when a Fortress protects every player a Raid could target, and declining is still allowed. The offer is still made (the power cannot ask the walker whether the answer would be accepted), and the rejection happens on the answer. That is accepted, and recorded as a deferred item in `docs/ROADMAP.md`.

- [ ] **Step 1: Write the tests**

Create `src/test/scala/oathdigital/gameplay/powers/economy/KnightsErrantSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.{CampaignFixture, EconomyFixture, OathRules}
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powers.{CardStaging, PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.powers.targeting.TargetingFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers, WalkerProcedureRegistry}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseManyAnswer, ChooseAmountAnswer, ChooseOneAnswer}
import oathdigital.model.OathState.Ready

class KnightsErrantSuite extends munit.FunSuite {
  import EconomyFixture.plainId

  private val knights = DenizenId("120")
  private val modifiers = Vector(KnightsErrant.id)
  private val actor = PowerFixture.actor
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = CampaignFixture.anyDice)

  /** The actor holds Knights Errant and stands at a site with a token-free card
    * to muster from. The site is ruled by bandits, so a Conquest is legal,
    * unless `campaignLegal` is false. The actor has `supply` Supply and 3
    * warbands.
    */
  private def staged(supply: Int = 1, campaignLegal: Boolean = true)
      : ReadyGame = {
    val ready = PowerFixture.asAdviser(CardStaging.without(
      EconomyFixture.act(supply = supply, favor = 4, boardWarbands = 3), knights),
      knights)
    val site = PowerFixture.home(ready)
    ready.updateCurrent(c => c.copy(map = c.map.copy(sites = c.map.sites.updated(
      site, c.map.sites(site).copy(forces =
        if (campaignLegal) SiteForces.Occupied(ForceKind.Bandit, 2)
        else SiteForces.Empty)))))
  }

  private def ready(transition: OathTransition): ReadyGame =
    transition.state.asInstanceOf[Ready].value

  private def me(state: ReadyGame): PlayerState = PowerFixture.player(state)

  /** Starts a Muster with `selected` and answers its source. */
  private def musterFrom(state: ReadyGame, selected: Vector[PowerId])
      : OathTransition = {
    val started = rules.startWalker(Ready(state), ActionRef.Muster, actor,
      selected).toOption.get
    val done = rules.resolveWalker(started.state, actor,
      MusterProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(plainId))).toOption.get
    done.copy(events = started.events ++ done.events)
  }

  private def answer(from: OathTransition, id: String, given: DecisionAnswer)
      : OathTransition = {
    val next = rules.resolveWalker(from.state, actor, id, given).toOption.get
    next.copy(events = from.events ++ next.events)
  }

  private def parkedOn(transition: OathTransition): String =
    ready(transition).game.current.walkerPending
      .fold("")(_ => transition.continue match {
        case OathContinue.AwaitingEconomyDecision(_, id) => id.value
        case OathContinue.AwaitingCampaignDecision(_, id) => id.value
        case other => other.toString
      })

  private def query(transition: OathTransition): DecisionQuery = {
    val state = ready(transition)
    val current = state.game.current
    val tree = WalkerProcedureRegistry.rebuild(ActionRef.Muster, catalog, state,
      actor, current.walkerStartArgs).toOption.get
    val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
      current.walkerModifiers)
    ProcedureWalker.parkedDecide(state, tree, current.walkerPending.get, powers)
      .get.query
  }

  /** Answers the optional-targets decision with none, if it is asked. */
  private def toForce(transition: OathTransition): OathTransition =
    if (parkedOn(transition) == CampaignIds.targets)
      answer(transition, CampaignIds.targets, ChooseManyAnswer(Vector.empty))
    else transition

  /** The warbands the actor has once the Muster has gained: the matching-adviser
    * bonus depends on the cards drawn, so it is read from a Muster without the
    * power.
    */
  private def afterMuster: Int =
    me(ready(musterFrom(staged(), Vector.empty))).board.warbands

  private val campaign = ChooseOneAnswer(KnightsErrant.campaignOption)
  private val decline = ChooseOneAnswer(KnightsErrant.declineOption)

  test("Knights Errant is a registered selected Muster modifier") {
    val power = KnightsErrant.forCatalog(catalog).get
    assertEquals(power.cardId, knights)
    assertEquals(power.actions, Set[MajorActionType](MajorActionType.Muster))
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
  }

  test("after the gain it asks whether to campaign, as a Muster decision") {
    val asked = musterFrom(staged(), modifiers)
    assertEquals(parkedOn(asked), KnightsErrant.decisionId)
    assertEquals(asked.continue, OathContinue.AwaitingEconomyDecision(actor,
      DecisionId(KnightsErrant.decisionId)))
    assertEquals(query(asked).asInstanceOf[DecisionQuery.ChooseOne].options
      .map(_.ref), Vector[DecisionOptionRef](KnightsErrant.campaignOption,
      KnightsErrant.declineOption))
    // The Muster's own gain has already happened.
    assertEquals(me(ready(asked)).board.warbands, afterMuster)
  }

  test("declining ends the Muster with no Campaign") {
    val done = answer(musterFrom(staged(), modifiers),
      KnightsErrant.decisionId, decline)
    assertEquals(ready(done).game.current.walkerProcedure, None)
    assertEquals(ready(done).game.current.lastCampaignResult, None)
    assertEquals(me(ready(done)).board.supply.supply, 0)
  }

  test("nothing is asked when no Campaign is legal") {
    val done = musterFrom(staged(campaignLegal = false), modifiers)
    assertEquals(ready(done).game.current.walkerPending, None)
    assertEquals(me(ready(done)).board.warbands, afterMuster)
  }

  test("without the selection the Muster ends as before") {
    val done = musterFrom(staged(), Vector.empty)
    assertEquals(ready(done).game.current.walkerPending, None)
  }

  test("the Campaign sees the warbands the Muster gained and costs no Supply") {
    val start = staged(supply = 1)
    val forced = toForce(answer(musterFrom(start, modifiers),
      KnightsErrant.decisionId, campaign))
    assertEquals(parkedOn(forced), CampaignIds.force)
    assertEquals(forced.continue, OathContinue.AwaitingCampaignDecision(actor,
      DecisionId(CampaignIds.force)))
    assertEquals(query(forced).asInstanceOf[DecisionQuery.ChooseAmount].max,
      afterMuster)
    // 1 Supply less the Muster's 1: the Campaign's 2 was not spent.
    assertEquals(me(ready(forced)).board.supply.supply, 0)
  }

  test("the same Campaign started on its own is refused for want of Supply") {
    val alone = staged(supply = 0)
    assert(rules.startWalker(Ready(alone), ActionRef.Campaign, actor).isLeft)
  }

  test("a Campaign run this way finishes, and the Muster ends after it") {
    val forced = toForce(answer(musterFrom(staged(), modifiers),
      KnightsErrant.decisionId, campaign))
    val done = answer(forced, CampaignIds.force, ChooseAmountAnswer(0))
    val result = ready(done)
    assertEquals(result.game.current.walkerProcedure, None)
    assert(result.game.current.lastCampaignResult.nonEmpty)
    assertEquals(PaidActionHarness.replayed(rules, staged(), done.events),
      result)
  }

  test("it cannot be selected for a Campaign, or for any other action") {
    val state = staged()
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(state,
      actor, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Muster).contains(KnightsErrant.id))
    assert(!offered(ActionRef.Campaign).contains(KnightsErrant.id))
    assert(!offered(ActionRef.Trade).contains(KnightsErrant.id))
    assert(rules.startWalker(Ready(state), ActionRef.Campaign, actor,
      modifiers).isLeft)
  }

  // ---- Restrictions on the whole Campaign apply to the nested one ----

  private def campaigning(from: OathTransition)
      : Either[OathViolation, OathTransition] =
    rules.resolveWalker(from.state, actor, KnightsErrant.decisionId, campaign)

  test("Vow of Peace forbids the nested Campaign, and declining is still allowed") {
    val vow = DenizenId(catalog.denizens.find(_.powers.exists(
      _.id.value == "denizen.vow-of-peace")).get.id.value)
    val ready = PowerFixture.asAdviser(CardStaging.without(staged(), vow), vow)
    val asked = musterFrom(ready, modifiers)
    assertEquals(parkedOn(asked), KnightsErrant.decisionId)
    assert(campaigning(asked).left.toOption.exists(
      _.isInstanceOf[OathViolation.CampaignUnavailable]))
    assert(rules.resolveWalker(asked.state, actor, KnightsErrant.decisionId,
      decline).isRight)
  }

  test("a Fortress that protects every player a Raid could target forbids the " +
      "nested Campaign") {
    // Nobody rules the site, so a Conquest is not legal. An enemy pawn stands
    // there, so a Raid is, and the Rotting Fortress protects that enemy.
    val base = staged(campaignLegal = false)
    val other = base.game.current.players.map(_.player).find(_ != actor).get
    val site = PowerFixture.home(base)
    val fortified = TargetingFixture.fortressAt(
      TargetingFixture.pawnAt(base, other, site), EdificeSide.Ruined, site)
    val asked = musterFrom(fortified, modifiers)
    assertEquals(parkedOn(asked), KnightsErrant.decisionId)
    assert(campaigning(asked).left.toOption.exists(
      _.isInstanceOf[OathViolation.CampaignUnavailable]))
    // The same board without the Fortress lets the Raid start.
    val open = musterFrom(TargetingFixture.pawnAt(base, other, site), modifiers)
    assert(campaigning(open).isRight)
  }

  test("a nested Campaign that is allowed is not stopped by a restriction " +
      "once it is under way") {
    val forced = toForce(answer(musterFrom(staged(), modifiers),
      KnightsErrant.decisionId, campaign))
    // Every later command of the Campaign is checked against its answers too,
    // and none of them is refused.
    val done = answer(forced, CampaignIds.force, ChooseAmountAnswer(0))
    assertEquals(ready(done).game.current.walkerProcedure, None)
  }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "Test/compile"`
Expected: FAIL to compile, for example `not found: value KnightsErrant`.

- [ ] **Step 3: Implement**

Create `src/main/scala/oathdigital/gameplay/powers/economy/KnightsErrant.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.{CampaignProcedure, CampaignSetup}
import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, PowerAnswers, SelectedModifier}
import oathdigital.model._

/** Knights Errant (card 120), a selected Muster modifier: after mustering, you
  * may campaign, spending no Supply.
  *
  * The Campaign runs inside the Muster. Two nodes are appended to the Muster's
  * tree, after the gain:
  *
  *  - a live decision, asked only when a Campaign is legal (a ruled site to
  *    Conquest, or an enemy pawn to Raid), whether to campaign;
  *  - a node that, once the answer is "campaign", builds the Campaign's own tree
  *    from live state when it is walked. The Campaign therefore sees the
  *    warbands the Muster just gained, and it is rebuilt on every resume like
  *    any Campaign is.
  *
  * The Campaign's Supply payment is removed at `CampaignCost`, and only when the
  * window is walked for a Muster (`PowerCtx.procedure`). The power cannot be
  * selected for any other action, so this is the Campaign it runs itself.
  *
  * Limitation: a Restriction hooked on the whole Campaign (Vow of Peace) is
  * checked once, when a command starts, against the tree that exists then, and
  * the nested Campaign is not part of it. The decisions of the nested Campaign
  * are restricted as usual.
  */
final case class KnightsErrant private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier {
  def id: PowerId = KnightsErrant.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Muster)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterActionEligibility -> Vector(Transform((ctx, children) =>
      children ++ Vector(offer(ctx.activePlayer), campaign(ctx.activePlayer)))),
    PowerWindow.CampaignCost -> Vector(Transform((ctx, operations) =>
      operations.filterNot {
        case SpendSupply(player, _, _) => player == ctx.activePlayer
        case _ => false
      })))

  override def appliesAt(ctx: PowerCtx): Boolean = ctx.window match {
    case PowerWindow.CampaignCost =>
      ctx.procedure.contains(ActionRef.Muster)
    case _ => true
  }

  private def offer(actor: PlayerId): Operation = Branch((ready, _) =>
    if (CampaignSetup.legalKinds(ready, actor).isEmpty) Vector.empty
    else Vector(Decide(KnightsErrant.decisionId, actor, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Button(KnightsErrant.campaignOption, "Campaign"),
        DecisionOption.Button(KnightsErrant.declineOption, "Do not campaign")),
      heading = Some("Knights Errant: campaign for no Supply?")))))

  private def campaign(actor: PlayerId): Operation = Branch((ready, pending) =>
    if (!PowerAnswers.one(pending, KnightsErrant.decisionId)
        .contains(KnightsErrant.campaignOption)) Vector.empty
    else CampaignProcedure.rebuild(catalog, ready, actor, Vector.empty)
      .fold(error => Vector[Operation](BuildOps((_, _) => Left(error))),
        tree => Vector(tree)))
}

object KnightsErrant {
  val id: PowerId = PowerId("denizen.knights-errant")
  /** Under the `muster.` prefix, which the Muster's continuation recognises. */
  val decisionId: String = "muster.knights-errant.campaign"
  val campaignOption: DecisionOptionRef.Button =
    DecisionOptionRef.Button("campaign")
  val declineOption: DecisionOptionRef.Button =
    DecisionOptionRef.Button("decline")

  def forCatalog(catalog: ExecutableCatalog): Option[KnightsErrant] =
    CatalogCards.denizen(catalog, id).map(new KnightsErrant(_, catalog))
}
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, replace:

```scala
import oathdigital.gameplay.powers.campaign.VowOfPeaceContribution
```

with:

```scala
import oathdigital.gameplay.powers.campaign.VowOfPeaceContribution
import oathdigital.gameplay.powers.economy.KnightsErrant
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`, replace:

```scala
      CardPlayTriggers.forCatalog(catalog) ++
```

with:

```scala
      KnightsErrant.forCatalog(catalog).toVector ++
      CardPlayTriggers.forCatalog(catalog) ++
```

In `src/main/scala/oathdigital/gameplay/actions/economy/MusterProcedure.scala`, replace:

```scala
  val decisionId: String = "muster.source"
```

with:

```scala
  val decisionId: String = "muster.source"

  /** Every decision a Muster or a power inside it asks starts with this. */
  val decisionPrefix: String = "muster."
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`, replace:

```scala
      continuationFor = (decisionId, actor, decision) =>
        Option.when(decisionId == MusterProcedure.decisionId)(
          OathContinue.AwaitingEconomyDecision(actor, decision)),
      build = (catalog, state, activePlayer, args) => noStartArgs(ActionRef.Muster,
```

with:

```scala
      continuationFor = (decisionId, actor, decision) =>
        if (CampaignProcedure.decisionIds.contains(decisionId))
          Some(OathContinue.AwaitingCampaignDecision(actor, decision))
        else Option.when(decisionId.startsWith(MusterProcedure.decisionPrefix))(
          OathContinue.AwaitingEconomyDecision(actor, decision)),
      build = (catalog, state, activePlayer, args) => noStartArgs(ActionRef.Muster,
```

- [ ] **Step 4: Run the task's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.KnightsErrantSuite oathdigital.gameplay.powers.targeting.FortressRulesSuite oathdigital.gameplay.MusterProcedureSuite oathdigital.gameplay.EconomyWalkerSuite oathdigital.gameplay.CampaignProcedureSuite oathdigital.gameplay.CampaignPowersSuite oathdigital.gameplay.walker.WalkerProcedureRegistrySuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS (120 tests in these suites and the ones they touch).

- [ ] **Step 5: Run the whole suite and the architecture check**

Run: `./sbtw test` and `python3 scripts/check-architecture.py`
Expected: PASS, and `architecture check passed`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: implement Knights Errant

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```


- [ ] **Step: Record sub-slice 2f**

Follow "Recording a sub-slice" below for 2f and commit with `docs: record slice 2f`.

---

## Recording a sub-slice

Each sub-slice ends with a docs commit, so a merged sub-slice leaves the design and rulings true. Edit the files named below, each addition on its own line, to ease merging with slices 3 and 4.

- `docs/superpowers/specs/2026-09-20-powers-design.md`: in the status line add the sub-slice ("slice 2a" and so on) to the implemented list with a link to this plan. In the "Slicing" section add "Slice 2 is planned in six sub-slices: see its [plan](../plans/2026-09-20-powers-slice-2-modifiers-restrictions-triggers.md)." once, in 2a. In "Verify at plan time" replace: the window-key fingerprint item (2a) with "No: fingerprints cover catalog handler ids and structure only"; the `PowerCtx.nodePath` item (2b) with "`nodePath` is a vector of child indices and names no action, and a walk's state has no procedure; `PowerCtx.procedure` names it (E9)". In the E9 paragraph (2b) replace "If `nodePath` does not identify..." with the outcome: needed, for Welcoming Party's origin and Knights Errant.
- `docs/superpowers/specs/2026-09-20-powers-rulings.md`: in "Slice 2: modifiers" replace the intro "All are selected at the start of the major action, and once selected they apply for free." with "All are selected at the start of the major action. A modifier's cost is paid at the very start of the action, whether or not the modifier then has an effect, and the costs of all selected modifiers must be payable together (refused at selection otherwise). Once selected they apply for free." and replace Relic Worship's row with: "173 Relic Worship | Non-persistent after the catalog fix. `applicable` requires a secret and an empty card. Its cost, 1 secret placed, is paid at the start of the Recover with the other selected modifiers' costs, and is refused at selection if they cannot all be paid together (Catacombs with one faceup secret). After the relic is taken (`RecoverAfterRelic`), gain 2 Supply. A Recover that ends without a relic has still paid the secret." Then add `Implemented (slice 2x)` beside each card's row in "Slice 2: modifiers", "Slice 2: persistent rules" and "Slice 2: card-play triggers", and add a "Slice 2 implementation notes" list with these entries for the sub-slice:
  - **2a:** `PlacementRules` replaces the adviser limits and composes; the tree with no contributor is unchanged, and with one the placement path gains a level. **Generic discard rules (product decisions):** a faceup locked adviser, an intact edifice and a card that prints a power selected for the running action cannot be discarded, by any path, and `DiscardRestrictions` is where that is enforced. A Homeland replacement discards an edifice and no longer buries it. Add to "Rules that apply to every power", exactly: "**Active modifiers.** Active modifiers cannot be discarded. A modifier cannot be discarded during the major action it is modifying." Mob's row: an intact edifice is refused by the generic rule. `AdviserLimit.of` and Horned Mask no longer repeat Silver Tongue's or card play's rules.
  - **2b:** every selected modifier's cost is paid at the start of its action and all are validated together at selection (`ContributingPower.selectionPayments`); Catacombs states its secret. `SelectedModifier` checks a modifier's action, access and cost at selection. Welcoming Party's row reads "If you play a denizen face up when first drawn, gain 1 favor from the Hearth bank with `Gain.Favor`. A card does not trigger on its own play." (replacing "that is not a facedown adviser"): a denizen played faceup straight from the Search's draw, to a site or as a faceup adviser; a facedown placement and a card that was already a facedown adviser do not trigger it. Wild Cry cannot be discarded while selected. `PowerCtx.procedure` exists (E9).
  - **2c:** a selected modifier's cost is paid at the start of every Travel, whatever the route (permissive, product decision); the Supply saving and Forest Paths' ignore apply only when the condition holds. A free Travel is still a destination candidate, with cost 0. Toll Roads and Grasping Vines find their ruler as the ruler of the site the card stands at and ignore a facedown copy.
  - **2d:** the Truthful Harp reveals by recording a `Peek` for every other player and restricts nothing; the hand itself stays private in projections, and the other players remember a revealed card played facedown. Augury and the Harp stack. Relic Worship pays its secret at the start of the Recover and gains its 2 Supply after the relic is taken; Catacombs plus Relic Worship with one faceup secret is refused at selection. The Cup of Plenty is free for a player with no faceup adviser. The reviewed entry for Relic Worship is now a selected, implemented handler.
  - **2e:** Conspiracy's target decision has a window and is dropped when a power removes every option. The Fortress start refusal applies until the Campaign has answered one of its decisions. Circlet's protection covers Raid targets, Challenge banners and Conspiracy targets and never the Circlet itself.
  - **2f:** restrictions are checked against the tree a power adds and the answers a command carries, so Vow of Peace and the Fortress apply to Knights Errant's nested Campaign, which is refused when the player answers "campaign". The Muster registry entry recognises the Campaign's decision ids and the `muster.` prefix.
- `docs/ROADMAP.md` (2d only): add a deferred item beside the card-slots item: "A public view of a revealed temporary hand (Truthful Harp reveals by `Peek` today)". The deferred item for Knights Errant's offer was added when this plan was revised.

## Open items

Everything else the product owner answered is built into the tasks. What remains:

1. **Relic Worship's ruling says the cost is paid after the relic is taken.** The plan follows the product owner's newer rule (every modifier pays at the start) and moves it, so the ruling's row is rewritten in the 2d docs step. The exact ruling text it contradicts, from the rulings appendix: "Non-persistent after the catalog fix. `applicable` requires a secret and an empty card. After the relic is taken (`RecoverAfterRelic`), pay 1 secret placed with a required `PayCost` and gain 2 Supply. Limitation: another selected modifier spending your only secret first makes the payment fail late." The card text does not contradict it: "[secret] After recovering a relic, gain 2 Supply." (the `[secret]` is the cost, `After recovering a relic` scopes the gain). One consequence to confirm: a Recover that fails or is stopped has still spent the secret. Example: the player selects Relic Worship, rolls blanks and stops, and has lost a secret for nothing. Recommended default: accept (the player owns the choice, as for Tents).
2. **The combined check happens at the start command, not in the pre-start preview.** The client offers each modifier on its own, so a player can build a selection the start command refuses ("the selected modifiers cannot all be paid together"). Example: with one favor the client offers both Tents and Forest Paths, and starting the Travel with both is refused. Recommended default: accept (the refusal is immediate and nothing is spent); the preview could later disable the second choice.
3. **Locked as a generic `OperationRestriction`** is deferred (fact 22, ROADMAP). Recommended: defer, as the three rules are enforced generically by Task 3 and no card in the slice needs more.

## Risks to check while executing

- **Payments move to the root of the tree.** A costed modifier's payment is the first child of the action's root, before the action's own first step. A suite that asserts the first recorded step of Travel or Recover (`Catacombs` asserts node ids `"0"`) sees the payment first. The kit's payment is a `Transform` at the eligibility window, so it shifts every parked path by one when a costed modifier is selected; nothing asserts that today.
- **`PlacementBody` changes the parked path.** With a contributor to the placement rules, the placement `Branch` is one level deeper. Nothing asserts the old path, but a suite that does (a Silver Tongue projection or pending-walker test) will show it. Compare before relaxing anything.
- **Test fixtures and the card inventory.** A `CardIndex` duplicate (`invalid-card-index ... failed 1 structural checks`) means a fixture placed a card that was still in a regional discard, a site or an adviser area. Stage it with `CardStaging.without`.
- **A selected modifier changes an existing suite that hands the walker every catalog power.** `TravelProcedureSuite` did (Task 8). If another suite fails with a modifier's payment, do the same: `WalkerPowers.selected(WalkerPowerCatalog.default(catalog), Vector.empty)`.
- **Task 12 changes what every command checks.** The traversal now folds windows and selects Branches with answers, so a Branch whose `select` throws on an unvalidated answer breaks it (`PlaceBannerResource` did: an amount of 0 builds a `FlipSecrets` of 0). The answering command therefore checks after the walker accepted the answer. If another suite fails with an `IllegalArgumentException` from a Branch, keep that order.
- **Discard rules and the first walk.** `walkerModifiers` is recorded at an action's first park, so the placement options offered at that park do not exclude an active modifier while the next command's replacement list does (fact 6). A test that asserts the options of the first park will not see the rule.
- **Locked is faceup only.** `MinorActionsSuite` pins that a facedown locked adviser can be discarded. Do not widen the rule.
- **Fortress start guard reads the pending position.** It is unit-tested with a hand-built `PowerCtx` and end to end through Knights Errant. If a later change moves the restriction check inside the walk, the guard would stop firing.
- **Recover journal and the wire codec.** Its first step does not round-trip (fact 16). Do not "fix" the Relic Worship suite by asserting the whole journal.
- **`Peek` on a temporary hand records knowledge for good.** It is correct for a revealed card. A test that compares full `ReadyGame` values before and after a Harp Search sees `knowledge.advisers` change.
- **`GainSupply` at a full track records no operation.** A suite that expects a Supply step starts the actor below 7.
- **Shared registration vectors.** `WalkerPowerCatalog.default` gains one line per sub-slice (`CardPlayTriggers`, `TravelModifiers`, `ActionModifiers`, `TargetProtections`, `KnightsErrant`). The lines are inserted before the `CardPlayTriggers` line, so the sub-slices apply in any order (Task 13 needs Task 11's fixture, so 2f follows 2e).
- **Reviewed entries.** Only Relic Worship's reviewed classification changes. Rowdy Pub, Knights Errant and Cup of Plenty stay listed as unimplemented selected handlers, which records nothing. If `RuleResolutionSuite` is extended to demand `implemented = true` for a walker power, flip them.
- **Two windows on one power.** Forest Paths hooks `TravelActionEligibility` only so that its ignore reaches Narrow Pass. If a future power hooks a Travel window that Forest Paths does not, it will not be ignored: add the window with the same no-op transform.

## Self-review

- **Spec coverage.** Augury, Truthful Harp, Cup of Plenty, Rowdy Pub, Relic Worship (Task 9); Tents, Forest Paths, Dragonskin Drum (Task 8); Knights Errant (Task 13); Wild Cry, Welcoming Party (Task 7); Toll Roads, Grasping Vines (Task 8); Circlet, Oaken and Rotting Fortress (Task 11); Gossip (Task 7); E6 (Tasks 1, 2 and 4), E7 (Task 10), E9 (Task 5). The design's verify-at-plan-time items for E6 (fingerprint) and E9 (`nodePath`) are answered in fact 1 and in Task 5. The product owner's answers: Welcoming Party in Task 7, permissive and pay-at-the-start costs in the Global Constraints and Tasks 6, 7, 8 and 9, the Harp in Task 9, restrictions in Task 12, discards and active modifiers in Task 3, the Fortress in Task 11, Cup of Plenty in Task 9.
- **Placeholders.** None: every code step is a complete file or an exact replacement, compiled and run in a throwaway copy before it was written here.
- **Validation.** Every file and replacement in Tasks 1 to 13 was applied, in this order, to a fresh copy of `main`, compiled and run: each task's tests failed to compile (or failed) before its implementation and passed after it, and the whole suite and the architecture check passed at the end (1383 tests, against 1195 on `main`). The same tasks were also applied in a second order (2a, 2b, 2e, 2f, 2d, 2c) with the same result, so the sub-slices are independent as the split table says.
- **Types.** `PlacementRules`, `PlacementTree.adjust`, `PlacementBody`, `CardPlay.Choice.replacementOptional` and `CardPlayProcedure.noReplacement` (Tasks 2 and 4) are used unchanged by `PlacementFixture`, `SiteDiscardFirstSuite` and `SilverTongue`. `DiscardRestrictions` and `CardStaging` (Task 3) are used by Tasks 4, 7, 8, 9, 11 and 13. `PowerCtx.procedure` (Task 5) is used by Tasks 7 and 13. `ContributingPower.selectionPayments` (Task 6) is overridden by Catacombs and by `SelectedModifier` (Task 7). `SelectedModifier.effects`, `CatalogCards` (Task 7) are the base of Tasks 8, 9 and 13. `SearchFixture` (Task 7) is used by Task 9. `ContributingPower.ignores` (Task 8) is used by Forest Paths. The restriction traversal (Task 12) is what Task 13's suite relies on.
