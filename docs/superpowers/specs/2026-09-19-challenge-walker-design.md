# Challenge and Place Banner Resource on the Procedure Walker

> Status: implemented by [the plan](../plans/2026-09-19-challenge-walker.md). Extends the [procedure walker design](2026-09-05-procedure-walker-design.md) and the [declarative decisions design](2026-09-10-declarative-walker-decisions-design.md), and follows the recipe of the Economy, Search and Visions ports ([Muster and Trade](2026-09-18-economy-walker-design.md), [Visions and Conspiracy](2026-09-19-visions-conspiracy-walker-design.md)).

## Goal and scope

Move Challenge and Place Banner Resource (the legacy `Challenge` object and `ChallengeCommand`) onto the walker as two registry entries, `ActionRef.Challenge` and `ActionRef.PlaceBannerResource`, and delete the legacy path in the same slice, only after a differential parity test against the legacy result. The player's banner, amount and Wandering Flame site choices become walker `Decide`s. The slice adds two decision shapes to the sealed `DecisionQuery` family, `ChooseMany` and `ChooseAmount`, and their answers.

Design rule for the whole tree: **every decision is built from the board as it stands when it is asked.** A later decision shows the effects of earlier steps, and no `Branch` selection or decision query may read state that an earlier step of the same tree has changed, because the tree is rebuilt from live state on every command. The ordering below follows from this rule.

Out of scope:
- Any Challenge power. No `ChallengePowers` exists. The windows added here are the hook points, and the audited-power diagnostics keep working through the registry's `fallbackKind`.
- Action-budget accounting. The engine has none. Challenge and Place Banner Resource return to action selection as they do today.
- Ribbon rules for banner faces other than Mob and Wandering Flame (see Start gates).

Rule changes, all deliberate. The legacy first-game gates for exile-only roles, unaltered Foundations and inactive legacies do not exist in the walker version, as in Muster and Trade. The legacy handler-catalog fingerprint is replaced by `PowerRuntime.requireAudited`. The order of the player's decisions changes (amount before ribbon), and Wandering Flame no longer asks a tie question whose answer cannot change the result. Costs, gains, other gates, control strings and labels are preserved. Any other rule defect found on the way is a separate change.

## Challenge procedure

The tree is a `Sequence` whose window is `ChallengeActionEligibility`. Decision ids: `challenge.banner`, `challenge.amount`, `challenge.ribbon-site`.

```
Sequence(                                             // ChallengeActionEligibility
  Sequence(window = ChallengeCost, SpendSupply(actor, 1)),
  Decide("challenge.banner", actor,                   // ChallengeBannerSelection
    ChooseOne(Challenge.legalBanners(state, actor))),
  Branch { (state, pending) =>                        // banner read from the answer
    Vector(
      Decide("challenge.amount", actor,               // ChallengeAmountSelection
        ChooseAmount(prior + 1, actorResources)),
      Sequence(window = ChallengeRibbon, <ribbon steps for the banner>),
      Sequence(window = ChallengePlacement, payment, custody)) })
```

As built, the tree has three sibling `Branch`es after the Supply step (banner, amount, effects). The walker re-runs every enclosing `Branch.select` on each resume, so each `Branch` is selected only when reached and reads only answered values or state no earlier step changes. Everything derived from state that an earlier step changes (the ribbon, the payment and the custody source) is built inside a `BuildOps` or a lazily selected `Branch`.

**Supply.** `SpendSupply(actor, 1)` in `ChallengeCost` is the first step, so the walk spends it before the first decision, as legacy `Begin` did. Supply affordability is not a build gate: the transformed `SpendSupply` owns it, so a start with no Supply fails at the first step before anything is persisted, and a power that rewrites the cost does so in `ChallengeCost`. Because the Supply is already spent when every decision is built or rebuilt, no decision reads Supply.

**Banner decision.** Its options are the banners that pass every start check: the actor does not already hold the banner, is co-located with the holder when the banner is enemy-held, and has strictly more relevant resources than the banner (faceup secrets for Darkest Secret, favor for People's Favor). `Challenge.legalBanners(state, actor)` is one predicate, used to build this query and by the projector to decide whether to offer the start control. If it returns no banner, `StartWalker` rejects with a typed violation before anything is persisted. The option's cost text is projected from state (`Currently N resources`, and `Unclaimed` for a banner with no holder), not authored on the option, and a start control is offered when `WalkerSimulation.starts` accepts the same first walk a start performs. The Economy preview gate is not used: answering a banner option runs no operations, so a preview would read no cost from it, and legality belongs in the query build. `requiresPlayableOption` stays off.

**Amount decision.** `ChooseAmount(priorResources + 1, actorResources)`, where `priorResources` is the banner's current total and `actorResources` is the actor's relevant resources. It is asked before any move runs, so both bounds come from pre-drain state. The heading states the banner's current total. The answer is stored in `PendingTree.answered` and read by the placement step.

**Ribbon, People's Favor.** One `BuildOps` in `ChallengeRibbon` that returns every favor on the banner to the least-favor banks, using `BannerRules.raidFavorReturn` on the bank state at build time. The holder, if any, receives nothing.

**Ribbon, Wandering Flame.**
1. A `BuildOps` moves the holder's retained half (`⌈P/2⌉` secrets, `P` the prior total) from the banner to the holder's play area. An unclaimed banner retains nothing. After this step every secret still on the banner is a secret to place, so the count still to place is always the banner's secrets, readable from live state.
2. A `Repeat` with guard `banner.secrets > 0`. Each pass reads the tied least-stocked sites with `BannerRules.leastSites`.
   - If the banner's secrets are at least the number of tied sites, place one secret on each tied site and run the guard again.
   - Otherwise park `Decide("challenge.ribbon-site", actor, ChooseMany(secrets, tied sites))`. The answer places one secret on each chosen site. The banner is then empty and the `Repeat` ends.

The player is offered zero or one site decision, and it shows the board after all earlier placements. Outcomes match the legacy one-secret-at-a-time rule: with tied sites `k` and secrets `s >= k`, every legacy pick raised its site out of the tie, so each tied site ended with one secret whichever order the player chose. With `s < k`, choosing `s` distinct tied sites at once equals choosing them one by one.

**Payment and custody.** In `ChallengePlacement`, a `Move` of the answered amount from the actor's play area onto the banner, then the banner's custody `Move` from the prior holder's play area, or from the shared bank when unclaimed, to the actor. The holder does not change before this step, so the custody source is read from live state.

Registry entry: `rollDecisionId = None`; `continuationFor` maps the three decision ids to the generic walker decision continuation; `fallbackKind` is `MajorActionKind.Challenge`; `modifierWindow` is `ChallengeModifierSelection`.

## Place Banner Resource procedure

Decision ids: `place-banner-resource.banner`, `place-banner-resource.amount`.

```
Sequence(                                            // PlaceBannerResourceEligibility
  Decide("place-banner-resource.banner", actor,      // PlaceBannerResourceBannerSelection
    ChooseOne(banners the actor holds with relevant resources > 0)),
  Branch { (state, pending) =>
    Vector(
      Decide("place-banner-resource.amount", actor,  // PlaceBannerResourceAmountSelection
        ChooseAmount(1, actorResources)),
      Sequence(window = PlaceBannerResourcePlacement,
        Move(resource, actor play area -> banner))) })
```

There is no Supply cost and no ribbon. The action gate is the act-phase gate. Registry entry: `modifierWindow = None` (as Take Wealth: no rulebook clause backs a modifier window for a free placement) and `fallbackKind = None`. Banner filtering is at query build, as in Challenge.

## Start gates

Start gates stay in `build` because they are facts about state, not costs: the act-phase gate, `PowerRuntime.requireAudited`, and the active-face gate. Supply is a cost, owned by `SpendSupply`, not a gate. The face gate is kept: the ribbon logic in this design is correct only for People's Favor `Mob` and Darkest Secret `WanderingFlame`, so any other active face rejects the start with `UnsupportedBannerState`. Dropped: the altered-Foundation, non-Exile and active-legacy gates and the handler-fingerprint gate. The "banner already held", co-location and strictly-more-resources checks are option filters of the banner decision, not separate gates.

## New decision shapes

- `DecisionQuery.ChooseMany(count, options, heading)`: pick exactly `count` distinct options. Answer `DecisionAnswer.ChooseManyAnswer(selected: Vector[DecisionOptionRef])`, accepted only when the selection has exactly `count` distinct references, each from the query. Well-formed only if `1 <= count < options.size`: a count that takes every option is a forced answer, which no decision shape may park on. Challenge parks it only when secrets are fewer than the tied sites.
- `DecisionQuery.ChooseAmount(min, max, heading, confirmLabel)`: pick an integer. Answer `DecisionAnswer.ChooseAmountAnswer(amount: Int)`, accepted exactly when `min <= amount <= max`. Well-formed only if `min <= max`, and `heading` and `confirmLabel` are required, as on `Distribute`. No `suggested` field. A single-value range stays a decision the player confirms.

The `DecisionAnswer` family is sealed, so each shape needs, with a round-trip test: `DecisionQueries` validation and its exhaustive match; the walker's answer acceptance; the projector's option and answer projection; the protocol DTO and codec; the journal answer codec (`DecisionAnswerCodec`); and a generic frontend control in `WalkerPanelSupport` (a multi-select with a confirm control, and a dropdown with a confirm control that opens on `min`). A dropdown carries no per-option annotation, so context goes in the authored heading. The frontend builds the answer from the control's value, and neither control names a procedure.

## Windows

Challenge (`ChallengeWindow`, carrying `MajorActionType.Challenge`): `ChallengeBannerSelection`, `ChallengeAmountSelection`, `ChallengeCost`, `ChallengeRibbon`, `ChallengePlacement`. The ribbon site decision lives inside `ChallengeRibbon`, not a sixth window. `ChallengeActionEligibility` and `ChallengeModifierSelection` already exist.

Place Banner Resource (`OtherWindow`, no major-action association): `PlaceBannerResourceEligibility`, `PlaceBannerResourceBannerSelection`, `PlaceBannerResourceAmountSelection`, `PlaceBannerResourcePlacement`.

Each window is audited vocabulary that no Challenge power uses yet. The slice adds them as hook points and tests only that a test-only transform reaches the banner and amount selections.

## Start flow

One start control per action, as for Recover and Forge: `challenge` and `place-banner-resource` send `StartWalker` with no arguments, the walk parks at the banner decision, and the generic walker-decision UI renders it. The legacy board-target selection "Choose a banner to Challenge" and the `placeBannerResource` control are replaced. A start control is offered only if the same predicate the banner query uses returns at least one banner and the actor can afford the Supply. The plan decides how the projector reads affordability without duplicating `SpendSupply`'s rule (a dry run of the start walk is the candidate). Control strings and option labels are preserved where a control survives.

## Cutover and deletion

Order, as with Economy: (1) add the two decision shapes with their codecs and frontend controls; (2) add the windows; (3) add the two procedures and registry entries; (4) add a differential parity test against the legacy result; (5) delete the legacy path.

Deleted in step 5:
- `Challenge.handle`, `Challenge.evolve`, `ChallengeCommand`, `ChallengeRules.validate` and `legal`, and `ChallengeRules.validateBase`.
- The `OathRules` Challenge dispatch and evolve cases, and the matching `FirstGameSetup` case.
- `GameCommand.BeginChallenge`, `ChooseChallengeSecretSite`, `CompleteChallenge` and `PlaceBannerResource`, their `Authorization` helpers, their `GameApplicationService` dispatch and action-kind lines, and the `Intent` cases with decoders, codec cases and mapper entries.
- The events `BannerChallengeStarted`, `BannerRibbonChoiceMade`, `BannerChallengeCompleted` and `BannerResourcePlaced` with their wire and journal codecs.
- `PendingProcedure.Challenge`, the `PendingProcedureProjector` challenge projection and `ChallengeProjection` with its DTO and codec, and the `LegalActionProjector` entries (`beginChallenge`, `chooseChallengeSecretSite`, `completeChallenge`, `placeBannerResource` and the challenge board-target selection).
- The frontend Challenge panel and its `ServerUiSupport` intents.
- Any violation left without a user (candidates: `ChallengeUnavailable`, `ChallengeOutcomeMismatch`, `ChallengeDecisionMismatch`, `InsufficientFavor`, `InsufficientSecrets`, if nothing else reads them).

Kept: `OathContinue.AwaitingBannerDecision` (reused as the walker continuation for both procedures), `BannerRules`, moved to its own file as `VisionRules` was (Campaign and Conspiracy use it), `UnsupportedBannerState` for the face gate, and `PowerRuntime`'s Challenge fallback kind. Pre-release history compatibility is not required by the approved walker design.

After the slice, three `PendingProcedure` cases remain: `Campaign`, `CampaignRaidRelocation` and `Negotiation`. The walker roadmap and the remaining-cases list are updated in the same slice.

## Verification

Before deletion, compare legacy and walker results for the same legal scenarios, on complete authoritative state, `CardIndex`, public projection and player-scoped projection. Parity is possible only in exile-only, fixed-unaltered-Foundation, no-legacy states, because legacy rejects every other state. Scenarios:
- People's Favor: unclaimed, and enemy-held.
- Darkest Secret: unclaimed, and enemy-held with a retained half.
- Wandering Flame with a unique least site, with tied sites and enough secrets, and with tied sites and fewer secrets than tied sites (choose a subset).
- Each illegal state (no Supply, which fails the start at `ChallengeCost`; actor already holds the banner, enemy-held without co-location, not strictly more resources), which must offer no control and reject a forced start with no state change.
- Place Banner Resource for each banner.

Legacy asked a site question the walker no longer asks, so parity answers the legacy question with any legal site and compares final state.

Also test: each new decision shape through every codec, the journal round trip and replay through the recorded-operations path, the amount and ribbon decisions reading pre-drain and mid-ribbon state respectively (a resumed `ChooseMany` after earlier placements shows current totals), the option set and cost text of the banner decision, a wrong or stale answer for each shape, the test-only transform reaching the two selection windows, and the architecture rules (`BackendArchitectureSuite`: no lowercase power name in walker sources, files under 800 lines, powers do not import `gameplay.walker`). Existing Challenge projection, protocol and frontend tests are migrated to the walker forms, not deleted.

## Left for the plan

- The mechanism that authors cost text on a build-time `ChooseOne` option (whether `DecisionOption` gains a field, or the existing `details` annotation is set at build).
- The exact typed violation for a start with no legal banner.
- How the projector decides a start control is affordable (see Start flow).
- Whether `ChooseMany` validation and projection share a helper with `Partition` or stand alone.
- File split points, so no source file passes 800 lines.
