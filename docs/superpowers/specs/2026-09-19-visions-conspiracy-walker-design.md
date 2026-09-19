# Visions and Conspiracy on the Procedure Walker

> Status: implemented by [the plan](../plans/2026-09-19-visions-conspiracy-walker.md). Extends the [procedure walker design](2026-09-05-procedure-walker-design.md) and follows the recipe of the [Muster and Trade design](2026-09-18-economy-walker-design.md): move the behaviour onto the walker and delete the legacy path in the same slice.

## Goal and scope

Retire the legacy `Visions` object (`VisionCommand.Reveal`, `VisionCommand.PlayConspiracy`) and `PendingProcedure.Conspiracy`. Both behaviours become ordinary facedown-adviser plays on the existing `ActionRef.PlayFacedownAdviser` and `ActionRef.Search` card-play tree (`CardPlayProcedure`):

- **Reveal Vision** is a facedown Vision played faceup into the revealed-Vision slot. The replaced Vision goes to the next region's regional discard. No Supply is spent.
- **Conspiracy** is a `WHEN PLAYED` power contribution at `PowerWindow.ActionCardPlayed`, in the same way `Dazzle` is. It works identically whether the card is kept faceup from a Search (temporary hand) or played from a facedown adviser.

Neither behaviour adds a procedure, a registry entry or a walker change. What the slice does add is one generic operation capability (`Move` of a Vision to `SharedBank`), two `DecisionOptionRef` kinds, and one power.

Out of scope: the restrictions the legacy audit enforced (see Deferred), and every other action still on its legacy path.

## Behaviour

**Reveal.** The `Origin.FacedownAdviser` case of `CardPlay.planVision` already builds this play: a `Play` into the revealed slot, with the current revealed Vision discarded to the next region. It is unreachable today because `CardPlay.validateOrigin` calls `MinorActionPowerSupport.validateVisionPlay`, which rejects every non-discard facedown Vision play unconditionally. Removing that call enables it. The legacy limit to the four true Visions disappears with it: any facedown Vision can be played faceup, and Conspiracy is special-cased below.

**Conspiracy.** Playing Conspiracy faceup, from either origin, places nothing: no `Play` operation, no revealed-slot replacement, and no replacement decision. The card stays where it is until the power removes it. The power then does the following, in order:

1. If any target is legal, a `Decide` offers them as a `ChooseOne`. A target is legal when it is a relic slot or a banner held by another player whose pawn is at the actor's site. With no legal target there is no `Decide`, and the power only removes the card. The player cannot decline a legal target.
2. A `BuildOps` reads the answer and re-checks it against live state. An answer that is no longer legal rejects.
3. The effects are the legacy ones:
   - A relic target: `Give` the relic to the actor.
   - Peoples Favor: drain its favor to the banks in the order `BannerRules.raidFavorReturn` computes, then move the banner to the actor.
   - Darkest Secret: burn every secret on it, then move the banner to the actor. Nothing is placed on sites and nothing returns to the previous holder.
4. The card leaves the game: `Move(Piece.Card(conspiracy), from, PositionedLocation(Location.SharedBank))`, where `from` is `Hand(actor)` when the card is in the actor's temporary hand and `PlayArea(actor)` otherwise. Steps 3 and 4 run in one batch, and the removal runs even with no target.

## The Conspiracy power

A new `ContributingPower` in `powers/whenplayed/`, registered in `WalkerPowerCatalog.default`. `VisionDefinition` carries no powers, so it is found the way `TakeWealthLimit` is: always present, keyed on the constant `VisionRules.Conspiracy` and not on a catalog lookup. `applicable` matches `CardPlayed(VisionRules.Conspiracy, _)`. Its `Transform` at `ActionCardPlayed` appends the `Decide`, the `BuildOps` and nothing else. It is automatic, not player-selected. The walker already allows a transform to insert a `Decide`, and `SilverTongue` and `LeagueTreatyContribution` do; an empty `BuildOps` batch is a no-op in the walker.

The decision id is `cardplay.conspiracy.target`. The registry's `continuationFor` for both `PlayFacedownAdviser` and `Search` matches the `cardplay.` prefix, so the prompt continuation follows with no registry edit.

## Card play changes

- `planVision` gets one Conspiracy case for both origins. `PlacementPlan.startConspiracy` and `BeginConspiracy` are deleted.
- `CardPlayProcedure` stops suppressing the `CardPlayed` hook for Conspiracy (`card != VisionRules.Conspiracy`). `resultingSource` is whatever `CardPlay.playedSource` already returns for a faceup Vision, the `Adviser` ref.
- `CardPlay.legalChoices` must offer Conspiracy's faceup placement with no replacement candidates. Its current candidate list for a faceup Vision is the revealed Vision, and `planVision`'s new case must not require or accept one.
- `validateOrigin` loses the `validateAdviserPlay` and `validateVisionPlay` calls. The `FirstGameRulesData.visions` membership check stays.

## Boxing: `Move` to `SharedBank` and the card inventory

`OperationPipeline.run` records the card ids before the batch, and `OperationStateInvariant.validate` requires the same set afterwards, otherwise `CardInventoryChanged`. That is why the legacy removal was an executor bypass after the pipeline. The extension has four parts:

1. `OperationCardMutation.insertCard` gets a `Location.SharedBank` case: check the card holds no tokens, as its sibling cases do, then drop the card. `removeCard` already handles `Hand` and `Advisers`.
2. `OperationValidator.cardDestinationViolation` accepts `SharedBank` for a `VisionId` only. No other card kind can leave the game yet.
3. `OperationPipeline.run` subtracts every card in an executed `Move(Card, _, SharedBank)` from the expected set passed to `validate`. `DomainValidation` and `CardIndex` take that same expected set, so they follow. A card can only leave the game through a declared, recorded operation, and any other disappearance still trips the invariant.
4. Replay applies recorded operations through the same pipeline. `Location.SharedBank` already has a wire spelling (`shared-bank`, used by `Burn`), so the walker operation codec needs only a test for a card `Move` to it.

## Vocabulary

- `DecisionOptionRef.RelicSlot(owner: PlayerId, slot: Int)`: opaque, so it discloses nothing about the facedown relic.
- `DecisionOptionRef.Banner(banner: Banner)`: the enum only. The holder is read from live state when the answer resolves, and the answer must be a banner an enemy at the actor's site holds.

Each gets a `DecisionOption` case, a wire spelling and a `fromWire` case (the `DecisionAnswerCodec` `kind`/`wireId` pair), a `WalkerDecisionProjector.optionProjection` case, and a line in `GameEventWireSuite`'s option-reference round trip. Labels follow the legacy ones (`<owner> facedown relic`, `<owner> <banner>`). The decision renders in the generic choose-one decision panel (`WalkerPanelSupport`); there is no board-target selection for it.

## Cutover and deletion

Order: (1) add the `Move` extension, the two option refs and the power, with tests; (2) switch `planVision`, `validateOrigin` and the `CardPlayed` hook; (3) delete the legacy path.

Deleted in step 3: `Visions.scala` and `VisionCommand`; `MinorActionPowerSupport.scala` and its tests; the `OathRules` Vision dispatch and the `Conspiracy*` and `VisionRevealed` evolve cases; `PendingProcedure.Conspiracy`, `BeginConspiracy` and `OathContinue.AwaitingConspiracyDecision`; the conspiracy special case in `OathRulesWalker`, `LegalActionProjector`, `GameApplicationService` and `OperationStateMutation`; `GameCommand.RevealVision` and `PlayConspiracy`, their `Authorization` helpers and `GameIntentMapper` entries; the `revealVision` and `playConspiracy` intents with decoders and codecs; `ConspiracyTargetRef` and the wire `ConspiracyTarget`; the `ConspiracyStarted`, `ConspiracyCompleted` and `VisionRevealed` events with their codecs and protocol entries; the `play-conspiracy` board-target projection and the frontend `reveal-vision` and `play-conspiracy` cases in `ServerUiSupport`; the reveal and conspiracy entries in `MinorActionsProjection` and the DTOs nothing else reads; and any violations left without a user (`ConspiracyUnavailable`, `ConspiracyOutcomeMismatch`, `ConspiracyDecisionMismatch`, `UnsupportedVisionRule`, `UnsupportedMinorActionRule`, `UnsupportedMinorActionCatalogInventory`, `VisionUnavailable` and the like). Kept: `VisionRules.goals` and `VisionVictoryEligibility`, which victory evaluation uses. Pre-release history compatibility is not required by the approved walker design.

## Deferred

- The audit `MinorActionPowerSupport.validateFaceupVision` enforced fail-closed rejections for `denizen.vow-of-obedience`, `denizen.secret-police`, `denizen.book-binders` and the E08 edifice faces. They should become `Restriction` powers. Until they do, those cards are silently ignored on Vision play; today they reject it.
- Altered Foundations are not checked at all, by decision.
- The minor-action handler-inventory fingerprint is deleted. It was a transitional tripwire on catalog drift, superseded by the reviewed fallback registry (`recordCardPlayFallback`, `IgnoredRulesRecorded`). The separate Negotiation and Rest fingerprints stay.

## Verification

- `Move` of a Vision to `SharedBank` boxes it and passes the invariant; a non-Vision to `SharedBank` is rejected; a card that disappears without a boxing operation still trips `CardInventoryChanged`.
- Conspiracy from a Search (temporary hand) and from a facedown adviser: relic target; Peoples Favor target with its return order; Darkest Secret target with the burn; no legal target (only the removal runs); a stale answer rejecting; and no replacement decision offered.
- Reveal from a facedown adviser: the old revealed Vision goes to the next region's discard, no Supply is spent, and the action boundary runs (as the legacy path's did).
- Journal round trip and replay through the recorded-operations path for both, including the new option refs through every codec.
- Existing `VisionsSuite`, `CardPlayProcedureSuite`, projection, protocol and frontend tests are migrated to the walker forms; the tests for deleted behaviour (the audit, the fingerprint, the direct command) are deleted with it.

## Left for the plan

- The exact `PowerWindow` and `PowerId` for the Conspiracy power.
- Confirming nothing dereferences `CardPlayed.resultingSource` in a way the `Adviser` ref breaks when the card is still in the temporary hand.
- The projector's label source for `<owner>` in the two new option kinds.
- Whether the frontend's facedown-adviser draft (`FacedownAdviserDraft`) and the legal facedown-adviser projection already offer facedown Visions, or filter them out.
- Whether `DomainValidation` or any other post-state check besides the shared expected-card set needs an allowance for a boxed card.
- Updating the walker design's status line and "What remains" list (Visions ported; `PendingProcedure.Conspiracy` gone).
