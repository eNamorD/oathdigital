# Catalog Batch 6: Battle Plan Restrictions and Search Sources

**Status:** designed 2026-10-09. Slices 1 and 2 are ready to plan. Slices 3
and 4 wait for [modifier selection on the walker](2026-10-09-modifier-walker-design.md),
and are re-read against that design before they are planned. The per-card
rulings are in [the rulings appendix](2026-10-09-catalog-batch-6-rulings.md).

**Builds on** [Catalog batch 4](2026-10-05-catalog-batch-4-design.md),
[Catalog batch 5](2026-10-08-catalog-batch-5-design.md) and the
[search source design](2026-10-07-search-source-walker-design.md), whose
vocabulary, cost rules, walker shapes and log-line rules apply unchanged.

## Goal and scope

**Phase - Catalog batch - engine-blocked** in the roadmap groups its cards by
the engine change each needs. Two groups turned out cheaper than the roadmap
said, because earlier work already put their decisions on the walker:

- **Battle plans** are chosen inside the walk, in `CampaignPlanChoice`, an
  `OfferHost`. A plan already carries `withheld`, the reason it is shown
  unavailable (Encirclement). What is missing is a way for one power to
  withhold another power's plan.
- **The Search source** is the walk's first board decision (`search.source`).
  A Transform can add piles to it, as Golem Legions adds Muster sources.

| Slice | Count | Cards |
|---|---|---|
| 1. Plan restrictions | 5 | 114 Specialist, 192 Marsh Spirit, 90 Beast Tamer, 41 True Names, 103 Code of Honor |
| 2. Plan rewrites | 2 | 125 Peace Envoy, 153 Wild Mounts |
| 3. Search sources | 5 | 64 Observatory, 11 Errand Boy, R33 Wine of Welcome, 183 Mushrooms, R23 Painted Trumpet |
| 4. Search draws | 2 | 232 Spinning Bee, 130 Land Warden |

### The bar

The product owner lifted batch 5's bar where this batch needs it. The
approved changes outside `gameplay/powers/`:

- **Option restrictions over offers** (slice 1). `WalkerPowerGather` applies
  a window's `OptionRestriction`s to the plans an `OfferHost` gathers, and
  `CampaignPlanOffer.withheld` becomes an `OptionReason`. A plan source
  names its own option (`CampaignPlanSource.ref`), so the walker keys offers
  without importing the Campaign package.
- **Plans used alone** (slice 1, approved at plan time).
  `CampaignPlanOffer.alone` marks a plan its user may use with no other. A
  bandit defender that would apply one applies it and nothing else
  (`CampaignPlanChoice`).
- **A Search source window** (slice 3). `PowerWindow.SearchSourceSelection`
  on the `search.source` decision.
- **Search costs and draws** (slice 3). `SearchRules.cost` and
  `SearchRules.draw` accept another region's discard pile, a draw from the
  bottom, and a draw of the top card of every discard pile, when a power asks
  for one.
- **Modifiers that restrict or warn about other modifiers** (slices 3 and 4)
  come from the modifier walker phase, not from this batch.

### Out of scope

- **Reroll**, **discard destinations** (Bracken, Cracked Horn, Ring of
  Devotion) and every other engine-blocked group stay where they are.
- **Hearts and Minds** shares Peace Envoy's "victorious now", but it is a
  defender's persistent rule with its own end-of-Campaign discard, so it
  waits for a later batch. Slice 2's rewrite is shaped so that it can reuse
  it.

## Slice 1: Plan restrictions

### Option restrictions over offers

`WalkerPowerGather.gather` applies `OptionRestriction`s only when the
windowed node is itself a `Decide`. The plan window's node is a
`CampaignPlanChoice`, an `OfferHost`, and its `Decide` sits in a `Branch`
that `expand` builds, so a restriction at the plan window reaches nothing.

The change: in the `OfferHost` case, the window's `OptionRestriction`s run
over the gathered offers, keyed by `CampaignPlans.refOf(offer.source)`. An
offer one forbids becomes withheld, with the restriction's note as its
reason (`OptionReason.Noted`). An offer a restriction forbids without a note
is dropped, as a forbidden `Decide` option is. `CampaignPlanOffer.withheld`
becomes `Option[OptionReason]`; Encirclement's reason becomes
`OptionReason.Written`.

Everything after that already works:

- The plan decision shows a withheld plan unavailable, with its reason on
  hover.
- A pass with nothing playable asks nothing, and the `Repeat` ends.
- A bandit defender applies no withheld plan.

The `PowerCtx` of a restriction carries the answered decisions, so a
restriction can read the plans already chosen in the window
(`CampaignAnswers.picks`).

### The powers

Each is an `OptionRestriction` at `CampaignAttackerBattlePlans`,
`CampaignDefenderBattlePlans` or both:

- **Specialist** is the attacker's battle plan. Once it is chosen, every
  defender plan is withheld.
- **Marsh Spirit** is a persistent site rule. While a Campaign's targets
  include its site, every attacker plan is withheld, the title's plan
  included.
- **Beast Tamer** is a persistent adviser rule. Its holder's enemy cannot
  choose a Beast or Nomad plan.
- **True Names** is a persistent adviser rule. Its holder's enemy cannot
  choose a plan whose suit matches one of the holder's faceup advisers.
- **Code of Honor** is a battle plan for either side, adding or removing two
  attack dice. It is withheld once its user has chosen another plan, and
  once chosen, it withholds the user's other plans.

A plan's suit is its source card's suit. A relic and the title have no
suit.

### Plan-time rulings

- **Reasons read as the Game Log words them.** A withheld plan's reason is
  its power's note: the card's name, a colon, then the sentence, as
  "Specialist: The defender cannot use battle plans." No slice 1 power
  writes a Game Log line.
- **An offer its own rule withheld keeps that reason.** A restriction does
  not replace Encirclement's force rule.
- **No plan is asked about.** A restriction's waiver is not consulted at the
  plan windows. None of this slice's restrictions is waivable.
- **Bandits and Code of Honor.** A bandit defender that would apply Code of
  Honor applies it and no other plan (FAQ). A Code of Honor another power
  withholds is not applied, and the bandits apply their other plans as
  usual.
- **Where the restrictions live.** `BattlePlan.restrictions` lets a plan's
  power forbid options at a window (Specialist, Code of Honor). Code of
  Honor's pair of restrictions is `SolePlan`, which Peace Envoy reuses in
  slice 2. Beast Tamer and True Names share `EnemyPlans`: the faceup holder
  when they are the enemy of the window's side, and a plan's suit.

## Slice 2: Plan rewrites

### Peace Envoy

Peace Envoy is a battle plan for either side. Its cost is the printed favor,
and its effect gives the enemy one favor per defense die in the pool. The
gift is part of the plan's application, so the plan window's dry run prices
it, and a user who cannot give the full amount sees the plan unavailable
with the shortfall as its reason (`PlanPrice.shortfall`). It is also
withheld unless the enemy's pawn is at the user's pawn site, and a bandit
defender has no pawn.

Once applied:

- Its "no other plans" restriction is Code of Honor's, shared through one
  helper.
- An `OptionRestriction` withholds every defender plan when the attacker
  used it, with the reason "Peace Envoy decided the battle". Nothing is
  playable, so the defender is not asked.
- Transforms at `CampaignAttackRoll`, `CampaignSacrificeSelection` and
  `CampaignDefenseRoll` return no children: nothing is rolled or sacrificed.
- A Transform at `CampaignAfterOutcome` rewrites `RecordCampaignResult` so
  that the user's side wins.
- The losses kill no warbands on either side, through `LossRewrite`.

The rewrite reads whether Peace Envoy was applied in this Campaign from the
answered plan picks, so it is a pure function of the walk.

### Wild Mounts

Wild Mounts is a free battle plan for either side. Choosing it has no effect
at the time; it arms a replacement at the end of the Campaign.

The end-of-Campaign discards of plans that say "At end, discard" are
appended at `CampaignActionEligibility` by `PlanDiscard.afterCampaign`.
That helper starts marking what it appends: a node naming the plan's card
and user. Wild Mounts' Transform at the same window, ordered after the
plans that append, finds its user's marked discards of Nomad plans. When
there is at least one, and the user rules a Beast card, it puts a decision
before them: discard each Beast card the user rules, or "Discard the battle
plans". Choosing a Beast card discards that card instead and drops the
marked discards.

"Cards you rule" are the user's advisers and the denizens at sites the user
rules.

## Slice 3: Search sources

Re-read against the modifier walker design before planning.

### The source window

`PowerWindow.SearchSourceSelection` hooks the `search.source` decision.
Transforms there add options, mark options `Warned`, or change options to
unavailable with a reason. `SearchSource` stays the pile picked; how it is
drawn is read from the selected powers when the draw is built.

### Costs and draws

`SearchRules.cost` refuses another region's discard pile today. It takes
the selected powers' permissions instead: a discard pile costs 2 Supply
wherever it is, once a power offers it. `SearchRules.draw` takes a draw
shape:

- **Top**, the printed draw: three cards, plus any draw extension.
- **Bottom**, Wine of Welcome on the world deck and Mushrooms on a discard
  pile, drawn bottom up. The world deck's Vision stop still applies.
- **Each pile**, Painted Trumpet: the top card of every discard pile. Draw
  extensions do not apply.

The plan checks whether `Draw` can take cards from the bottom of a pile.
If not, that is one more change, inside this slice.

### The powers

- **Observatory** adds every region's discard pile.
- **Errand Boy** adds the other regions' discard piles, and marks the
  actor's own pile and the world deck `Warned`.
- **Mushrooms** makes the world deck unavailable, waives the Supply, and
  draws one card from the bottom, plus any draw extension.
- **Wine of Welcome** draws the world deck from the bottom.
- **Painted Trumpet**: a discard pile picked draws the top card of each pile.

The modifier walker phase supplies two modifier rules: Mushrooms and
Painted Trumpet exclude each other, and Wine of Welcome is warned while
Mushrooms is selected.

## Slice 4: Search draws

Re-read against the modifier walker design before planning.

- **Spinning Bee** excludes every other Search modifier. It skips the source
  decision and draws from the actor's region's discard pile. A decision,
  "Declare a suit", offers the six suits as `FavorBank` refs. Cards are drawn
  from the top until one matches, the match included, and the normal
  keep-one choice runs over them. When none match, every drawn card is
  discarded and nothing is kept.
- **Land Warden** lets the Keep section hold one or two cards. The kept
  cards are played in the order kept. When the first did not go to a site,
  the second may only go to a site, and when no site has room, it is
  discarded.

Skipping the source decision and widening the Keep section need a hook in
`SearchProcedure`. The modifier walker design decides where it sits.

## Seams used for the first time

Each gets a test through its first power before the power relies on it.
If a seam needs an engine change beyond those approved, stop and ask the
product owner.

- An `OptionRestriction` over an `OfferHost`'s offers, with and without a
  note (Specialist).
- A restriction reading the plans already chosen in the window (Code of
  Honor).
- A plan cost the dry run prices from the pool (Peace Envoy).
- A Transform that removes a `Roll` and rewrites `RecordCampaignResult`
  (Peace Envoy).
- A marked end-of-Campaign discard replaced by a decision (Wild Mounts).

## Testing

- Each card gets a suite through the walker, from a board stated in Table
  steps, with its Game Log lines.
- Slice 1 tests each restriction on both sides where it applies, with bandit
  defenders, and True Names reading an adviser turned faceup during plan
  selection.
- Slice 2 tests Peace Envoy affordable and short, as attacker and defender,
  and Wild Mounts with zero, one and two Nomad discards.
- The verification gate is the roadmap's standing rule.
