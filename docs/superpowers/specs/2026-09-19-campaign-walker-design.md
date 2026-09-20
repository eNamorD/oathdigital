# Campaign on the Procedure Walker

> Status: implemented by [the plan](../plans/2026-09-19-campaign-walker.md). See **Implementation notes** at the end for where the build differs from this design. Extends the [procedure walker design](2026-09-05-procedure-walker-design.md), the [declarative decisions design](2026-09-10-declarative-walker-decisions-design.md) and the [ownership design](2026-09-12-walker-ownership-and-phases-design.md), and follows the recipe of the Challenge and Negotiation ports ([Challenge](2026-09-19-challenge-walker-design.md), [Negotiation](2026-09-19-negotiation-walker-design.md)). Rules content stays as in [bounded-campaign.md](../../architecture/bounded-campaign.md) except where **Rule changes** below says otherwise.

## Goal and scope

Move Campaign, Conquest and Raid, onto the walker as one registry entry, `ActionRef.Campaign`, and delete the legacy path in the same slice, only after a differential parity test against the legacy result. Campaign is the last user of the legacy `PendingProcedure`: `Campaign` and `CampaignRaidRelocation` are its two remaining cases. After the slice the `pending` slot is deleted too.

The slice adds four engine capabilities that Campaign needs and other ports will reuse: `ChooseMany` with a minimum of 0, `Distribute` with a total range, automatic (non-parking) `Roll` nodes, and option-level restrictions.

Design rule for the whole tree, unchanged from the Challenge design: **every decision is built from the board as it stands when it is asked**, and no `Branch` selection, `Repeat` guard or decision query may read state that an earlier step of the same tree has changed, other than answered values and recorded roll outcomes. Campaign is unusually well suited to this rule: nothing leaves the board until the results are determined (see **Force stays on the board**).

Out of scope:
- Any new Campaign power. The windows added here are hook points.
- Action-budget accounting. The engine has none.
- The Simultaneous node, and everything the Negotiation spec deferred.

## Rule changes

All deliberate. Any other rule defect found on the way is a separate change.

- **Unsupported handlers are ignored and recorded**, not blocking. The legacy fail-closed classification (`CampaignHandlerClassifications`) is deleted. `StartWalker` records ignored handlers through `IgnoredRulesRecorded` under `fallbackKind = Some(ActionKind.Campaign)`, as the other ported actions do. Bag of Siegeworks, Weeping Banner, Peace Envoy, the Mountain and Plains Campaign effects, and the unsupported Raid and player-defender powers therefore no longer block a Campaign. The handlers that stay executable are the ones the boundary document lists: Vow of Peace, Outriders, Brass Army, the title defender plan, and deterministic bandit Watchdog.
- **The first-game gates are dropped**: exile-only roles, unaltered Foundations and inactive legacies. This is the pending item from the Negotiation spec. The Campaign rules that the boundary document calls unmodelled (Imperial effects, altered Foundations, legacy powers) are not audited here; that audit is deferred.
- **No cancel after start.** The legacy browser holds a local draft with Back and Cancel and changes nothing until Confirm. In the walker, `SpendSupply(2)` is the first step and every later decision parks durably, as in Challenge. The start control is offered only when a legal Conquest or Raid exists and the actor can pay 2 Supply.
- **The Narrow Pass and Vow of Peace rules become powers** (see **Powers**). Behaviour is preserved.

Preserved: costs, target legality, attack and defense arithmetic, plan availability and costs, loss and placement rules, Raid resolution order, the action boundary (bandit refill, Supremacy, the Oathkeeper tie recipient decision), control strings and labels where a control survives.

## Force stays on the board

Legacy already keeps committed force in the attacker's play area throughout the battle: it leaves the board only when it dies or is placed. The walker version makes this the structure of the tree. `campaign.force` is only an answer. No `Move` runs for it. Both dice pools are gathered once, in one `BuildOps`, after that answer, because nothing on the board changes before the terminal steps. Placed survivors move from the board onto the targets at the end. Survivors that are not placed simply stay on the board: there is no "return" step.

## Procedure

Rulebook order: choose the type and targets; gather dice pools; use battle plans (attacker, then defender); roll attack dice; the attacker sacrifices; roll defense dice; declare the victor; kill the defeated warbands; resolve the victory.

Decision ids: `campaign.kind`, `campaign.defender`, `campaign.targets`, `campaign.force`, `campaign.attacker-plan`, `campaign.defender-plan`, `campaign.sacrifice`, `campaign.placement`, `campaign.relocation`. Pools: `campaign.attack` and `campaign.defense`.

```
Sequence(                                              // CampaignActionEligibility
  Sequence(window = CampaignCost, SpendSupply(actor, 2)),
  Decide("campaign.kind", actor, ChooseOne(legal kinds)),       // omitted when one kind is legal
  Branch(kind):
    Conquest: origin = the actor's pawn site
    Raid:     Decide("campaign.defender", actor,
                ChooseOne(co-located enemy pawns))              // omitted when exactly one
  Decide("campaign.targets", actor,                              // CampaignTargetSelection
    ChooseMany(0, n, optional targets))                          // omitted when none exist
  Decide("campaign.force", actor,                                // CampaignForceSelection
    ChooseAmount(0, board warbands))
  BuildOps(gather pools),                                        // CampaignGatherPools
  Repeat(!attackerFinished) { Decide("campaign.attacker-plan", actor,
    ChooseOne(unused plan sources + Finish)) },                  // CampaignAttackerBattlePlans
  Repeat(!defenderFinished) { Decide("campaign.defender-plan", defender,
    ChooseOne(unused plan sources + Finish)) },                  // CampaignDefenderBattlePlans
  Roll(campaign.attack, Attack, Automatic),                      // CampaignAttackRoll
  BuildOps(attack result),                                       // CampaignAttackResult
  Decide("campaign.sacrifice", actor,                            // CampaignSacrificeSelection
    ChooseAmount(0, force - skulls)),                            // omitted when nothing survives the skulls
  Roll(campaign.defense, Defense, Automatic),                    // CampaignDefenseRoll
  Branch(attack > defense):                                      // declare the victor
    BuildOps(losses)                                             // CampaignLosses
    victory:  Conquest -> Decide("campaign.placement", ...) + placement ops   // CampaignPlacement
              Raid     -> transfers, returns, discards, burn, then
                          Decide("campaign.relocation", ...) + pawn move      // CampaignRaidTransfer, CampaignRaidRelocation
    defeat:   nothing further)
```

The exact node layout, `Branch` boundaries and `Repeat` guards are for the plan. The order above is fixed.

**Supply.** `SpendSupply(actor, 2)` is the first step, as in Challenge. Affordability is not a build gate: the transformed `SpendSupply` owns it, so a start with too little Supply fails at the first step before anything is persisted, and a power that rewrites the cost does so in `CampaignCost`.

**Kind and defender.** `campaign.kind` offers Conquest when the actor's pawn site has a legal Conquest ruler, and Raid when a co-located enemy pawn exists. Both filters reuse the legacy start rules. The projector offers the start control when `WalkerSimulation.starts` accepts the same first walk a start performs.

**Targets.** In the rules every piece is a target, but only the mandatory piece is fixed: the Conquest pawn site, or the Raid enemy pawn. Those are `origin` and `defender` in code and never decision options. `campaign.targets` answers only the optional additions:
- Conquest: other sites ruled by the same bandit defender, in map order.
- Raid: that defender's faceup relics and held banners, in stable relic and banner order. Raid never targets the pawn's site.

The full target set is the mandatory piece plus the answer, canonicalised (map order for Conquest, pawn-first then stable order for Raid). Legality lives in how the query is built, never in a second check, following the contract in `Decide`'s documentation. The Pass and Vow of Peace rules reach the query through powers (see **Powers**).

**Force.** `ChooseAmount(0, boardWarbands)`. A zero-force Campaign is legal (the Empty Attack Pool rule), so the decision is always asked, even when the board holds no warbands, and the confirmation states what it commits: the Supply cost and the resulting attack dice before plans.

**Plans.** Legacy's ordered multi-plan selection maps to a `Repeat` around a `ChooseOne` of the unused accessible plan sources plus a Finish option. Each source can be selected once. Selecting a plan pays its cost or applies its reveal immediately, through the plan handler's typed cost and effect vectors, and adds its dice as `ModifyDicePool`. The attacker window runs first. The defender window follows and is owned by the defender when the defender is a player (12a below); a bandit defender applies its deterministic plans, cost-free and choice-free, automatically. A window with no available plan is omitted.

Plan handlers stay in `CampaignPlanRegistry`: it supplies the options and the effects for the decisions. Converting the handlers into walker power contributions is a later slice.

**Attack roll and result.** `Roll(campaign.attack, Attack, Automatic)` needs the walker to derive attack outcomes, which it does not today (see **Engine changes**). The pool count is the physical force plus the added attack dice. A pool of zero dice rolls nothing and records an empty outcome. In `CampaignAttackResult`, one `BuildOps` writes the capped result with `ModifyRollOutcome`:
- a skull removes one force warband, and its two swords count only when that loss can be paid;
- skulls beyond the physical force contribute no swords;
- Outriders ignores all skulls;
- Brass Army's four dice do not raise the physical force or any later loss, sacrifice, survival or placement limit.

Later steps read `rollOutcomes`. A power can transform `CampaignAttackResult`. `ModifyRollOutcome` keeps its name: `RollOutcome` is already the state type, and every other operation is verb-first.

**Sacrifice.** `ChooseAmount(0, force - skulls)`, where skulls are the effective skulls from the result. It is omitted when nothing survives the skulls. The heading states the attack faces, the attack total after plans and skull losses, and the surviving force.

**Defense roll.** `Roll(campaign.defense, Defense, Automatic)`. The defense pool is the targets' printed defense plus resolved defender plan effects plus the force at every target (bandits and players). For Raid, the pawn contributes two dice, each targeted relic adds its printed defense, each targeted banner adds three dice, and the defender's board warbands are their force. The title defender plan adds one die (Oathkeeper) or two (Usurper). Pools are gathered before the plans and the plans adjust them, so both counts are fixed when the rolls happen.

**Victor.** Attack must strictly exceed defense, where attack is the result after sacrifice.

**Losses (step 8).** Nothing is removed from the board before this step. One `BuildOps` in `CampaignLosses` removes the attacker's skull and sacrifice losses and the defender's losses. Legacy's losing-force policy registry, its dormant `Remove`, `Preserve`, `Relocate`, `Replace` and `Return` vocabulary and the policy id recorded in the event are deleted: losses are plain operations in named windows that powers can transform, and recorded operations are the replay authority.
- Attacker defeat: half the attacker's surviving force, rounded down, is killed. The rest stays on the board.
- Attacker victory over bandits: every warband at every target is removed.
- Attacker victory over a player: half the aggregate targeted force, rounded down, is killed and the survivors stay on that player's board.

**Placement (Conquest victory).** `campaign.placement` is a `Distribute` with one slot per target, in canonical order, every slot required (zeros are explicit), `minTotal = 0` and `maxTotal = surviving force`. Survivors placed on targets move from the board. The rest stay. The decision is omitted when nothing survives.

**Raid resolution (Raid victory).** In the printed order: targeted faceup relics and banners transfer; People's Favor resources return one at a time to the least-filled, leftmost-on-tie favor bank; the exact number of Darkest Secret resources burned is recorded; ordinary facedown advisers append in board order to the Raid site's next-region discard; the Conspiracy returns to the box; facedown relics enter the Chronicle reliquary; half the defender's favor, rounded down, burns; half the defender's board force, rounded down, is killed. The attacker then receives `campaign.relocation`, a `ChooseOne` over the canonical legal destinations, and the defender's pawn moves. This relocation is not Travel. `BannerRules.raidFavorReturn` is reused. Other viewers receive no hidden discarded identities.

**Registry entry.** `rollDecisionId = None` (Campaign never parks on a roll). `continuationFor` maps every `campaign.*` decision id to the generic walker decision continuation. `fallbackKind` is `ActionKind.Campaign`. `modifierWindow` is `CampaignModifierSelection`, and ordered modifiers keep their current click-order transport.

## Engine changes

Each shape change needs, with a round-trip test: its `DecisionQueries` validation and its exhaustive match; the walker's answer acceptance; the projector's option and answer projection; the protocol DTO and codec; the journal answer codec; and the generic frontend control.

1. **`ChooseMany` with a minimum of 0.** `wellFormed` still requires `min <= max <= options.size` and at least one option. `min = 0` is allowed. A decision that would take every option is still forced and may not park, so `min = 0` is what lets an optional pick coexist with that rule.
2. **`Distribute` total range.** `total` is renamed `maxTotal`, and `minTotal` is added. The answer's sum must satisfy `minTotal <= sum <= maxTotal`. Every existing user sets both to the old `total`, which preserves behaviour exactly, including `rest.distribution`. The query is well-formed only when slot minimums and maximums can reach the range. The generic panel shows the remaining amount and enables Confirm when the sum is in range.
3. **Automatic `Roll` mode.** `Roll(pool, dice, mode)`, with `Parked` (the default, so Recover and its `AwaitingRecoverRoll` and roll button are unchanged) and `Automatic`. The walker takes a dice source per command:
   - When it reaches an `Automatic` `Roll`, it asks the source for faces for the dice kind and the pool count, records a `RollPayload` step and keeps walking. It does not park.
   - Replay still applies the recorded faces, so it stays deterministic. The engine never rolls: the source is the application service's dice port, passed through `rules.startWalker` and `resolveWalker`.
   - `WalkerSimulation` gets a fixed placeholder source. Simulations stop at the first decision, and Campaign's first decision comes before any roll.
   - The application service's roll port is generalised from its hard-wired two defense dice to a kind and a count. The `RollPayload` codec must carry attack faces.
4. **Attack outcome derivation.** `ProcedureWalker` derives outcomes only from defense faces today. For `DiceKind.Attack` the base outcome is skulls = `TwoSwordsSkull` faces and score = `AttackDieFace.score`. `CampaignAttackResult` then edits it.
5. **`OptionRestriction` contribution.** `OptionRestriction(fn: (PowerCtx, DecisionOptionRef) => Option[OathViolation])`, gathered at a `Decide` window and applied once in the window fold, before the query is parked, so projection, answer validation and simulation all see the filtered set.
   - A `min 0` decision left with no options is dropped.
   - A required decision left with no options rejects the start with the restriction's own violation.
   - The violation is also available to say why an option is missing.
   - It follows the gather protocol's deterministic ordering `(priority, source.stableKey, powerId)`.

## Powers

- **Vow of Peace** is a root `Restriction` at `CampaignActionEligibility`, the shape Narrow Pass takes for Travel. It returns the typed `CampaignUnavailable` and blocks the whole Campaign for the ruler. The start control is decided by the `WalkerSimulation` dry run, so the offer and the command agree exactly.
- **Narrow Pass** gains an `OptionRestriction` at `CampaignTargetSelection`, in `NarrowPassSitePower` beside its Travel restriction. Rule text: "If your pawn is outside this region, you cannot travel to other sites in this region or target other sites in this region in campaigns, unless you have the consent of the Pass's ruler." Recorded details:
  - the check is per candidate site and never reads the other targets, so selecting the Pass changes nothing for other sites (the legacy `passAllowsTarget` already behaved this way);
  - the Pass site itself stays targetable, because the rule says "other sites";
  - it affects only Conquest targets, because Raid targets a co-located pawn and its relics and banners, never a site;
  - "consent of the Pass's ruler" is approximated as legacy did: the actor rules the Pass. Real consent is deferred: a consent system in general is its own mechanism, separate from Negotiation.
- **Plan handlers** (Outriders, Brass Army, the title plan, Watchdog) stay in `CampaignPlanRegistry`, as above.
- Restrictions remain the central "cannot" channel for power-derived limits. Where a decision would offer a choice a power forbids, the design is changed so it does not (option restrictions), rather than adding a second check.

## Windows

`CampaignWindow`, carrying `MajorActionType.Campaign`. Existing: `CampaignActionEligibility`, `CampaignModifierSelection`, `CampaignBeforeTargets`, `CampaignAttackerBattlePlans`, `CampaignDefenderBattlePlans`, `CampaignAfterOutcome`. Added, all as audited vocabulary that no Campaign power uses yet: `CampaignCost`, `CampaignKindSelection`, `CampaignTargetSelection`, `CampaignForceSelection`, `CampaignGatherPools`, `CampaignAttackRoll`, `CampaignAttackResult`, `CampaignSacrificeSelection`, `CampaignDefenseRoll`, `CampaignLosses`, `CampaignPlacement`, `CampaignRaidTransfer`, `CampaignRaidRelocation`. A test-only transform reaches two or three of them, as in Challenge.

## Visibility and presentation

- The defender's plan choices are visible only to the defender during that window. The active attacker and every other viewer see a waiting state. Chosen plan sources appear only through the applied effects that the terminal step records.
- The public and other-player projections never receive hidden discarded identities or relocation controls.
- Dice are public. The panels are the generic walker panels: `ChooseOne`, `ChooseMany`, `ChooseAmount` and `Distribute`. Headings carry the summary the legacy draft showed: committed force, remaining warbands, attack dice before plans, cost, allocated and remaining totals. The increment and decrement widgets are lost.
- **Dice display.** With automatic rolls no decision parks on a roll, so the result is shown twice. The `campaign.sacrifice` heading states the attack faces, the total after plans and skull losses, and the surviving force. After the action, a durable, public entry in the action-history feed is built from the recorded roll steps: both dice sets, the totals, the victor and the placements. The plan must first check whether the feed can render a walker `RollPayload`. If it cannot, that is a plan task.
- The legacy Campaign frontend, `CampaignPlacementState` and the legacy projection are deleted.

## Cutover and deletion

Order: (1) engine changes and their codecs and frontend controls; (2) windows; (3) the procedure, powers and registry entry, Conquest then Raid; (4) projection, the action-history entry and generic controls; (5) a differential parity test against the legacy result; (6) delete the legacy path; (7) delete the legacy `pending` slot; (8) docs.

Deleted in step 6:
- `Campaign.handle` and `Campaign.evolve`, `CampaignCommand`, `CampaignRules` start and plan validation, `CampaignHandlerClassifications`, `CampaignResolution` (the policy registry and its dormant vocabulary), and the `OathRules` dispatch and evolve cases.
- `GameCommand.BeginCampaignConquest`, `BeginCampaignRaid`, `ChooseCampaignPlan`, `FinishCampaignPlans`, `ChooseCampaignSacrifice`, `PlaceCampaignForce` and `RelocateCampaignRaidPawn`; their `Authorization` helpers, `GameApplicationService` dispatch and dice-port lines; and the `Intent` cases with decoders, codec cases and mapper entries.
- The events `CampaignStarted`, `CampaignPlanChosen`, `CampaignPlansFinished`, `CampaignSacrificed`, `CampaignConquered`, `CampaignRaided` and `CampaignRaidPawnRelocated`, with their wire and journal codecs (`CampaignEventCodec`).
- `PendingProcedure.Campaign` and `PendingProcedure.CampaignRaidRelocation`, the `PendingProcedureProjector` Campaign projections, `CampaignProjectionCodec` and its DTOs, and the `LegalActionProjector` Campaign board-target selections and controls.
- The frontend Campaign panel, `CampaignPlacementState` and their `ServerUiSupport` intents.
- Any violation left without a user. `CampaignUnavailable` stays for Vow of Peace.

Kept: `BannerRules`, the plan registry as the option and effect source, `SiteRule`, and the `Campaign` fallback kind. Journals are forward-only: old journals are not replayed.

Deleted in step 7, as its own commit: `PendingProcedure`, `CurrentGameState.pending`, the dual-pending guard, the legacy parts of `PendingProcedureProjector`, and the state codec entry. The slot has about 75 main and 45 test references, in `OathLifecycle`, `StateBasedEvaluation`, `OathRules`, `FinishRestProcedure`, `PhasePowerProcedure`, `ProcedureWalker`, the projectors and `GameApplicationService`. If this step grows beyond a single reviewable change, it is split out of the slice.

## Verification

Before deletion, compare legacy and walker results for the same legal scenarios on complete authoritative state, `CardIndex`, public projection and player-scoped projection. Parity is possible only where both accept: exile-only, unaltered Foundation, no relevant unsupported powers, injected dice. Scenarios:
- Conquest against bandits with and without extra targets.
- Conquest against a player defender.
- Each plan handler: Outriders, Brass Army, the title plan, Watchdog.
- Sacrifice 0 and N, victory and defeat, and zero force.
- Raid with pawn only, with relics and banners, and with each banner's resource return.
- The illegal-start cases, which must offer no control and reject a forced start with no state change.

The deliberate differences (dropped gates, ignored powers, no cancel) are tested separately as new walker behaviour. After parity, the remaining `CampaignSuite` cases migrate to walker fixtures, not deleted.

Also test:
- each new engine shape through every codec, the journal round trip and replay through the recorded-operations path;
- automatic rolls: the recorded `RollPayload`, a replay that never calls the dice source, and the placeholder source in simulations;
- attack outcome derivation for each face, and the capped result for skulls beyond the force, Outriders and Brass Army;
- `Distribute` ranges, including the unchanged exact behaviour of existing users;
- `OptionRestriction`: filtering, the empty optional decision dropped, the empty required decision rejected, and the Pass rule on each of its cases;
- the Vow of Peace restriction and the start control it hides;
- stale and wrong answers for each new decision;
- a test-only transform reaching the new windows;
- the architecture rules (`BackendArchitectureSuite`: no lowercase power name in walker sources, files under 800 lines, powers do not import `gameplay.walker`).

## Deferred, and recorded

- **All rolls should eventually become automatic.** Recover stays `Parked` in this slice because removing its roll button and `RollWalker` changes a shipped interaction and a wire command. That is its own slice.
- Real consent for the Pass, and a consent system in general, separate from Negotiation.
- The first-game rule audit behind the dropped gates.
- Converting the plan handlers into walker power contributions.
- Further optional attacker, defender and deterministic bandit plan families; non-deterministic sacrifice and loss choices where several legal assignments matter; and the additional Raid, victory, defeat and `At End` handlers. The timing windows exist structurally, and no behaviour is inferred for them.
- The `Simultaneous` node, and everything the Negotiation spec deferred.

## Left for the plan

- Exact node layout, `Branch` boundaries and `Repeat` guards, and whether the plan windows loop through one shared helper.
- The dice source's exact type and how `rules.startWalker` and `resolveWalker` carry it.
- How the action-history feed renders a walker roll payload, and whether it needs a new entry type.
- How the projector reads start affordability without duplicating `SpendSupply`'s rule (a dry run of the start walk is the candidate).
- Whether the frontend panels need a "remaining" display for `Distribute` ranges beyond what the generic panel shows.
- File split points, so no production file passes 800 lines.

## Implementation notes

What was built differs from the design above in the following ways. The design text is kept as approved.

1. **There is no action-history feed.** The design assumed one. Projections are built from state alone, rolls are cleared when the walker completes, and the only event view is a development-only raw dump. A durable public result fact replaces the feed: `CampaignResult`, written by a `RecordCampaignResult` operation into `CurrentGameState.lastCampaignResult`, projected to every viewer as `GameProjection.lastCampaign` and drawn by a result panel.
2. **A single-target placement is `ChooseAmount`, not `Distribute`,** because a distribution needs at least two slots. Several targets use `Distribute` with `minTotal = 0` and `maxTotal = survivors`.
3. **`ModifyRollOutcome` was defined but never executed.** It is now an upsert: it creates the outcome when the pool has none.
4. **An automatic `Roll` on an empty pool is skipped and records nothing.** Downstream steps read a missing outcome as zero faces, zero skulls and zero score.
5. **Victory reads recorded outcomes only.** The defender's board force cannot be read after the losses, so a `CampaignDefenseResult` window writes the defense score (dice score plus the defender's force) right after the defense roll. The window list gains `CampaignDefenseResult`.
6. **The plan loop finishes by itself when no unused plan is left.** The Finish option is offered while at least one plan can still be chosen.
7. **The plan registry stops depending on the legacy pending type.** The registry in `actions/campaign/CampaignPlans.scala` takes a small `CampaignSetup` and omits the replay validation the legacy registry carried (replay applies recorded operations). The dormant `TransformAttackResult`, `ReplaceLosingForcePolicy` and `Suspend` plan effects were deleted with the legacy path.
8. **The Raid discard rule drops "revealed by a defender plan":** no registered defender plan reveals, so only facedown advisers are discarded.
9. **The Recover roll feedback is gated on Recover.** `WalkerDecisionProjector.rollOutcome` is Recover-specific and would otherwise attach a Recover difficulty to every decision.
10. **`Distribute`'s projected `total` is `minTotal` and `maxTotal`** in the DTO and on the wire.
11. **`OathContinue.AwaitingCampaignDecision(playerId, decision)` replaces the four legacy Campaign continuations.**
12. **The second sentence of Vow of Peace is not modelled.** Attackers cannot sacrifice against a faceup holder. The legacy Campaign never modelled it either.
13. **The outcome branch reads the durable result.** After the losses change the board, the only nodes re-selected are those on the path to a parked placement or relocation decision. They read `lastCampaignResult`, written before the losses and never changed after.
14. **"Ignore and record" records what the reviewed power catalog lists at the Campaign windows, and today that is nothing.** The resolver reports a diagnostic only for an unimplemented automatic handler at the window being resolved. Bag of Siegeworks is player-selected and hooks the attacker battle-plan window, so it is ignored but not recorded. The other handlers the legacy classifier named are neither blocked nor recorded, as for every other ported action. The `fallbackKind` wiring stays, so anything the catalog lists later is recorded without further work.
15. **The `Distribute` panel and the sacrifice heading carry the summary the legacy draft showed**, and the increment and decrement widgets of the legacy force and placement drafts are gone.

## Deferred follow-ups

- The board-target `formation` field and the `PlayerPawn`, `PlayerRelic` and `PlayerBanner` board-target refs lost their only producer. They remain in the shared protocol as unused wire types.
- An action-history feed. The durable result is the interim.
- All rolls automatic (Recover still parks on its roll).
- Real consent for the Pass, and a consent system in general.
- The first-game rule audit behind the dropped gates.
- Converting the plan handlers into power contributions.
- The second sentence of Vow of Peace.
