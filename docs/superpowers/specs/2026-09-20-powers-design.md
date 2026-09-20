# Powers Batch 1: Engine Changes and Slicing

> Status: design approved 2026-09-20. Per-power rules are in [the rulings appendix](2026-09-20-powers-rulings.md). Extends the [procedure walker design](2026-09-05-procedure-walker-design.md) and follows the [Campaign port](2026-09-19-campaign-walker-design.md). Each slice below gets its own implementation plan.

## Goal and scope

Implement a limited, first batch of powers on the procedure walker, each declared solely as a `ContributingPower` (or, for ACTION, WAKE and REST powers, a `PhasePower`) that returns existing Operations. Where that was not possible, the gap is an engine change listed here.

In scope: 30 denizens, 12 edifice faces, 15 relics and the two banner powers Wandering Flame (move, place a secret) and Mob (card-play discard). The list and every ruling are in the appendix.

Out of scope, recorded so they are not lost:
- **Parked**: the six setup/explore edifices (Great Market, Bandit Market, Great Forge, Broken Forge, Proving Grounds, Empty Grounds). No exploration procedure exists to hook. Marble Fountains, Murky Fountain, Towering and Cracked Rampart, Oaken and Rotting Fortress are in scope.
- **Deferred**: the card-slot redesign of card play (see [ROADMAP.md](../../ROADMAP.md), Phase 3), the player-chosen sign of Mercenaries, defender-side activation of non-plan modifiers, Empire rulers (Toll Roads, Oaken Fortress) and Peace Envoy.
- **Verify only**: Dazzle, Catacombs and League Treaty already exist. Slice 0 checks them against the appendix and reports mismatches. It does not rebuild them. The one exception is Dazzle, which gains ruled edifices (product ruling).

## How the rulings were gathered

The powers were grilled in fifteen rounds, a few at a time. Every ruling below is the product owner's answer to a question, or a fact read from the code. Where an answer corrected an earlier assumption, the appendix records only the final ruling.

## Vocabulary

**Power kinds.** The catalog carries only `id`, `persistent` and `rulesText`. A power's kind is declared in code, by the windows it hooks and its `resolution`:

| Kind | Declared by | Resolution |
| --- | --- | --- |
| Modifier | hooks a start-of-action window; `persistent == false` | `PlayerSelected`, selected in the command's `modifiers` at the start of the major action |
| Persistent rule | hooks any window; `persistent == true` | `Automatic` |
| When Played | hooks the card-played window | fires when the card is played |
| ACTION, WAKE, REST | a `PhasePower` | used as an action in its phase |
| Battle plan | hooks a Campaign plan window through `Offer` | `Automatic`, with its own in-window decision; chosen at the plan step, not at action start |

A battle plan is attacker-only, defender-only, or usable by either side. Its side is declared by the power.

**Cost rules.** Every non-burnt cost is placed onto the card in question unless the power says otherwise. A placed cost can only go onto an empty card (no favor or secrets on it). Battle plans may pay onto an occupied card. A cost paid outside the payer's own turn settles immediately: favor goes straight to the matching suit bank and secrets flip facedown, so nothing rests on the card.

**Permissive resolution.** A power is usable when its cost is payable. An effect with nothing to do is a no-op. Non-required operations do as much as possible at execution time, which is how "if able" is modelled.

## Engine changes

### E1. `PowerAccess`

One helper implements the rulebook's access rule: "cards at your site, cards at sites you rule, and everything in your play area: your relics, banners, advisers and legacies". Faceup advisers and relics only, except where a power says otherwise (battle plans may use facedown advisers). Ruled-site access covers denizens and edifices, intact and ruined. `PhasePowerProcedure.accessible` and every contribution's `applicable` call it instead of each deriving its own test.

Known code deviations it removes: `PhasePowerProcedure.accessible` covers ruled-site site cards and relics but not edifices; the faceup site-relic branch can never match, because relics at sites are always facedown; `CatacombsContribution.applicable` reads only the pawn's site.

### E2. Activation from the catalog

`resolution` is derived from `CatalogPower.persistent` in each power's `forCatalog`: false is `PlayerSelected`, true is `Automatic`. Battle plans are the exception, being `Automatic` with their own decision. The product owner corrects the catalog: Relic Worship becomes `persistent: false`. That edit must be mirrored wherever the runtime catalog is compared or validated (`reference/catalog-ingestion`), or the equality check fails.

### E3. Phase-power costs and sources

- `PhasePower` gains `def cost: Cost = Cost.free`. The engine prepends the `PayCost` onto the power's source (`OnCard` for a card, `OnBanner` for a banner) and derives the cost half of `usable` from "cost payable, including the empty-card rule". The existing `usable` remains as an extra gate and defaults to true.
- `usedPowers` no longer tracks Act-timed powers. Wake and Rest keep the once-per-turn gate. Costed Act powers are limited by the empty-card rule instead, and free ones are unlimited.
- `PowerSourceRef.Banner` is added and `PhasePowerProcedure.PowerSource`, `useRef` and `sources` are widened from `CardId` to a card-or-banner source. This changes persisted `PowerUseRef`. Journals are forward-only, so there is no migration.
- A helper, `payOnSource`, gives contributions the same placement rule (Catacombs, Tents, Forest Paths, Relic Worship).

### E4. `PayCost`

- **Empty-card rule.** The validator rejects a placed portion whose `OnCard` target already holds favor or secrets. Banner destinations are excluded, because banners have no costs. Muster and Trade already require an empty denizen; their check folds into the same predicate.
- **`intoOccupied: Boolean = false`.** Battle plans set it, which skips the empty-card check.
- **`matchingBank: Option[Suit] = None`.** A fact about the card, supplied by the caller, because the pipeline has no catalog (as with `Discard.Denizen`'s `suit`).
- **Off-turn settlement.** `OperationPipeline` reads the active player from state. When the payer is not the active player, it replaces the favor moves with direct moves to `matchingBank` and the secret moves with `FlipSecrets(FaceUp, FaceDown)`. One expansion function is used by validation, execution and replay, so a recorded `PayCost` re-expands identically. A placed favor with no `matchingBank` (a relic source) is rejected as impossible. No power in this batch does that.

### E5. Operation vocabulary and helpers

- `Give` gains `override val required: Boolean = false`. Toll Roads passes `true`. Sticky Fire's "must give if able" stays non-required.
- `BuryableCard.Vision` (deck: world). Edifices already have `BuryableCard.Edifice`.
- Giving favor to bandits equals burning it, and needs no new operation: a `Give` whose `to` is `Location.SharedBank` does it. Sticky Fire uses that. Toll Roads' bandit case uses a required `PayCost` with a burnt favor.
- A standard-returns bury helper: `Bury` alone does not return resources. The helper returns favor to the suit bank and secrets to the acting player, facedown, as `Discard.Denizen` does.

### E6. Card play

- `CardPlayed` becomes `CardPlayedFaceup(card, resultingSource)` (site and faceup-adviser placements, today's behaviour) and a new `CardPlayedFacedown(card, player)` emitted for a facedown adviser placement. A Discard placement emits nothing. Windows are `ActionCardPlayedFaceup` and `ActionCardPlayedFacedown`. The persisted key string `action.card-played` is kept for the faceup window, so reviewed data and fingerprints do not change.
- A `PlacementRules` value replaces `PlacementTree.withAdviserLimit` and `withFaceupAdviserLimit`. It carries the faceup and facedown adviser limits (used by Silver Tongue) and `siteDiscardFirst` (used by Mob). Its meaning: a play to a site may first discard one card of the site's card list, at any capacity. `CardPlay.legalChoices` and its validation read it. It lifts the "full non-matching site cannot accept a denizen" rejection and offers the discard when the site has room.
- The wider redesign (playing to card slots) is deferred.

### E7. Conspiracy target window

`ConspiracyWhenPlayed`'s target `Decide` gets a window, `ConspiracyTargetSelection`, so Circlet of Command can restrict it.

### E8. Battle-plan offers

Campaign plans move from the handler-id registry in `CampaignPlans.plan` into contributions:

- A new contribution kind, `Offer`, adds an option to the shared "choose a plan, or finish" decision. The option carries its source, label, side (attacker, defender or either), a typed cost preview and its effects. The engine filters already-chosen sources and hides options the owner cannot afford. The shared decision and its `Repeat` are kept, so the projector, tests and frontend panels are unchanged in shape.
- The cost preview is data on the option and is shown by the projector. It comes from dry-running the same application fold the command runs, so a Gleaming Armor surcharge appears in it. A player can take as many plans as they can afford, because options are rebuilt after each pick.
- `CampaignPlanCost` gains a burnt variant and a sacrifice variant. `CampaignPlanEffect` gains an "after the outcome" kind.
- Plan application is a small operation class carrying the owner, side and source, in window `CampaignPlanApplication`. Powers such as Gleaming Armor match on it, as Silver Tongue matches `PlacementTree`.
- Plans are usable only by the source's ruler: the holder for advisers and relics, the site's ruler for site cards and edifices. Current code also offers origin-site cards to a non-ruler and requires Brass Army's card to be empty. Both are removed. A defender plan may now carry a cost.
- Facedown advisers remain usable as plans and are revealed when used (NF p. 13).
- `CampaignResult.victorious` is renamed `attackerWins`. It touches the model, `CampaignResultProjectionCodec`, the shared DTO, the frontend result panel, the Campaign suites and docs. The wire key changes with it.

### E9. Enclosing action on `PowerCtx`

Knights Errant nests a Campaign inside Muster and needs a hook on `CampaignCost` that applies only to that nested Campaign. `PowerCtx` does not expose answered decisions. If `nodePath` does not identify the enclosing action, `PowerCtx` gains the enclosing action's `ProcedureRef`. This is checked at plan time.

## Slicing

Approach: foundations first, then vertical slices by mechanism. Alternatives rejected: one plan for everything (too large to review), and slices that each carry their own engine work (E1, E3 and E4 are shared by nearly every power and would be reworked).

| Slice | Contents | Engine changes |
| --- | --- | --- |
| 0. Foundations | verify Dazzle, Catacombs, League Treaty | E1 to E5 |
| 1. When Played, ACTION, WAKE | A Small Favor, Faithful Friend, Garrison, Family Heirloom; Wayside Inn, Elders, Alchemist, Wolves, Fae Merchant, Sleight of Hand, Gambling Hall, Murky Fountain, Whistle, Brass Horse, Dowsing Sticks, Ivory Eye, Crystal Vial, Bone Dice, Magic Carpet, Magic Waterskin; Marble Fountains, Horned Mask | none beyond slice 0 |
| 2. Modifiers, restrictions, triggers | Augury, Truthful Harp, Tents, Forest Paths, Cup of Plenty, Rowdy Pub, Dragonskin Drum, Relic Worship, Knights Errant; Toll Roads, Grasping Vines, Circlet, Oaken and Rotting Fortress; Wild Cry, Welcoming Party, Gossip | E6 (`CardPlayed` split), E7, E9 |
| 3. Battle plans | Mercenaries, Wrestlers, Warning Signals, Towering and Cracked Rampart, Fearsome Shield, Battle Honors, Sticky Fire; Gleaming Armor | E8 |
| 4. Banner faces | Wandering Flame (move, place a secret), Mob | E3's banner source, E6's `PlacementRules` |

Slices 2, 3 and 4 are independent once slice 0 lands. Slice 1 needs only slice 0. The order above is the recommended one.

## Testing

- Every power gets a walker-driven suite that starts the action (`StartWalker` or `UsePower`), answers its decisions with `ResolveWalker`, and asserts the resulting state and the recorded steps.
- Each suite covers the ruling's edge cases: unaffordable cost, occupied card, empty bank, empty deck, no eligible target, off-turn payment for defender plans.
- Replay tests cover every new operation shape (`CardPlayedFacedown`, the expanded `PayCost`, `Give.required`).
- `BackendArchitectureSuite` still applies: files stay under 800 lines, no power names in walker sources, and powers do not import `gameplay.walker`.

## Verify at plan time

These are unverified assumptions. Each plan checks its own:
- `Location.Site` accepts secrets on the site's tokens (Wandering Flame).
- The shared bank's secret supply is unbounded in the validator.
- How the journal surfaces a `Peek` to its viewer (Ivory Eye).
- `PlaceBannerResource` is not limited by `usedPowers`.
- Whether any structural fingerprint covers window keys (E6).
- Where Muster and Trade enforce the empty-denizen rule today.
- Whether `Discard.Relic`'s `SetAsideRelics` is the discarded relic pile the rules mean, and how `ensureEmptyTokens` treats a relic still holding secrets.
- The format of `PowerCtx.nodePath` (E9).
- Fae Merchant's taken relic is assumed facedown, in line with Dowsing Sticks and Family Heirloom. This was not stated explicitly.
