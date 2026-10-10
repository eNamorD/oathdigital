# Catalog Batch 6: Battle Plan Restrictions and Search Sources

**Status:** designed 2026-10-09. Slices 1 to 3 are done. Slice 4 is
planned against [modifier selection on the walker](2026-10-09-modifier-walker-design.md);
its plan-time rulings are under "Slice 4". The per-card rulings are in
[the rulings appendix](2026-10-09-catalog-batch-6-rulings.md).

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
- **A gift in a plan's price** (slice 2, approved at plan time).
  `PlanPrice` counts a favor `Give` that the plan's dry run recorded, so
  Peace Envoy's option is priced with its gift.
- **A Search source window** (slice 3). `PowerWindow.SearchSourceSelection`
  on the `search.source` decision.
- **Search costs and draws** (slice 3, settled at plan time).
  `SearchRules.cost` charges 2 Supply for any region's discard pile, and
  `SearchRules.draw` draws from any pile, from the top or the bottom.
  Which piles a Search may pick is the source decision's business.
  `Draw` gains `position`, and `SearchDrawPort.prepare` no longer takes
  the actor's region.
- **A source decision a power removes** (slice 4, approved at plan
  time). With no source answer, a Search draws from the actor's own
  region's discard pile (`SearchProcedure.sourcePicked`). Only Spinning
  Bee removes the decision.
- **A keep window, and kept cards played in order** (slice 4).
  `PowerWindow.SearchKeep` on the `search.cards` decision. The placement
  plays each kept card in the order kept, a later one planned from the
  state the earlier plays left. A Search that holds no drawn card plays
  nothing.
- **Placement limits** (slice 4, approved at plan time).
  `CardPlayRules.placementLimits`: a play a limit refuses is shown
  unavailable with the limit's reason. When the limits leave no play, the
  discard pile stays open, since a card that cannot be played is
  discarded.
- **Two note arguments** (slice 4). `NoteArg.RegionName` ("Cradle") and
  `NoteArg.SuitName` ("Hearth"), for Spinning Bee's line.
- **The keep panel** (slice 4, approved at plan time). A Keep section
  that holds one or two cards reads "Keep one or two; discard the rest."
  and, while it holds two, "They are played in this order."
- **Modifiers that restrict or warn about other modifiers** (slices 3 and
  4) use the modifier walker's `offerReason` (modifier walker design,
  ruling 5).

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
  used it, with the reason "Peace Envoy: The battle is decided.". Nothing is
  playable, so the defender is not asked.
- Transforms at `CampaignAttackRoll`, `CampaignSacrificeSelection` and
  `CampaignDefenseRoll` return no children: nothing is rolled or sacrificed.
- A Transform at `CampaignAfterOutcome` rewrites `RecordCampaignResult` so
  that the user's side wins.
- The losses kill no warbands on either side, through `LossRewrite`. After
  a Conquest it wins, the defender's warbands at the targets all leave
  them and all return to the defender's board, so the victor can place
  warbands as usual (ruled at plan time).

The rewrite reads whether Peace Envoy was applied in this Campaign from the
answered plan picks, so it is a pure function of the walk.

The roll removal and the result rewrite are `VictoriousNow`, a helper that
Hearts and Minds can reuse.

### Wild Mounts

Wild Mounts is a free battle plan for either side. Choosing it has no effect
at the time; it arms a replacement at the end of the Campaign.

The end-of-Campaign discards of plans that say "At end, discard" are
appended at `CampaignActionEligibility` by `PlanDiscard.afterCampaign`.
That helper marks what it appends with a `PlanDiscard.Marked` node naming
the plan's card and user. Wild Mounts' rewrite at the same window, through
the `BattlePlan#rewrites` hook and at a priority after every plan that
appends, strips its user's marked discards of Nomad plans and appends one
step. That step asks the user, when they rule a Beast card: discard one of
them, or "Skip Wild Mounts". Choosing a Beast card discards that card
instead and drops the marked discards; skipping runs them. A bandit user
follows the FAQ: with two or more Nomad discards, the first Beast card the
bandits rule in map order is discarded instead.

"Cards you rule" are the user's advisers and the denizens at sites the user
rules.

## Slice 3: Search sources

Planned against slice 1 of the modifier walker design.

### The source window

`PowerWindow.SearchSourceSelection` hooks the `search.source` decision.
Transforms there add options, mark options `Warned`, or change options to
unavailable with a reason. `SearchSource` stays the pile picked; how it is
drawn is read from the selected powers when the draw is built.

### Costs and draws

`SearchRules.cost` charges 2 Supply for a discard pile wherever it lies.
The source decision offers another region's pile only when a picked
modifier adds it, and the walker accepts only an answer the decision
offers. `SearchRules.draw` takes a draw shape:

- **Top**, the printed draw: three cards, plus any draw extension.
- **Bottom**, Wine of Welcome on the world deck and Mushrooms on a discard
  pile, drawn bottom up. The world deck's Vision stop still applies.
- **Each pile**, Painted Trumpet: the top card of every discard pile. Draw
  extensions do not apply.

`Draw` takes cards from the top or the bottom (`Draw.position`). The move
guard already checks a bottom take. Painted Trumpet's draw is one `Draw`
per pile.

### The powers

- **Observatory** adds every region's discard pile.
- **Errand Boy** adds the other regions' discard piles, and marks the
  actor's own pile and the world deck `Warned`.
- **Mushrooms** makes the world deck unavailable, waives the Supply, and
  draws one card from the bottom, plus any draw extension.
- **Wine of Welcome** draws the world deck from the bottom.
- **Painted Trumpet**: a discard pile picked draws the top card of each pile.

Each card states its own modifier rules through `offerReason`. Mushrooms
and Painted Trumpet exclude each other, and Wine of Welcome and Mushrooms
warn each other.

### Plan-time rulings

- **The draw order is the powers' priority.** Wine of Welcome and
  Mushrooms fold at -1 and redraw from the bottom. The draw extensions
  fold at 0 and keep the end the draw takes from, so Augury adds cards
  from the bottom too. Painted Trumpet folds at 1 and replaces the draw,
  so nothing modifies it.
- **Warnings.** Wine of Welcome and Painted Trumpet warn the sources they
  leave alone through the `warning` hook, as Hunting Party does. Errand
  Boy's warning depends on the actor's region, which that hook cannot
  see, so Errand Boy marks it in its own Transform.
- **The Search line** names a bottom draw ("from the bottom of the Cradle
  discard") and Painted Trumpet's draw ("from each discard pile"). No
  slice 3 power writes a line of its own.

## Slice 4: Search draws

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

### Plan-time rulings

- **The skipped source decision.** Spinning Bee's Transform at
  `SearchSourceSelection` replaces `search.source` with "Declare a suit"
  (`search.suit`), a choose-one of the six favor banks in suit order. With
  no source answer, the Search draws from the actor's own region's
  discard pile. A Spinning Bee Search therefore has no source answer, and
  its draw is not checked against the draw port.
- **The exclusion.** Once Spinning Bee is picked, every other Search
  modifier is unavailable through its `OptionRestriction`, with the note
  "Spinning Bee: You cannot use other Search modifiers.". Spinning Bee
  itself is unavailable once another modifier is picked ("Cannot be used
  with other Search modifiers"), and while the actor's own pile is empty
  ("The Cradle discard pile is empty").
- **No match.** The whole pile is drawn, then every drawn card is
  discarded in draw order, where a Search discards the cards it does not
  keep. A Vision has no suit and never matches.
- **The Game Log.** Spinning Bee's line, "Spinning Bee: Drawing from
  the Cradle discard until Hearth card is drawn", comes before the Search
  line. With no match, the Search line ends "and kept none".
- **Land Warden's seam is card play.** `CardPlayRules` is already where a
  power changes where a card may go, and only `CardSlots` sees every slot,
  which "no site has room" needs. Land Warden sets a `PlacementLimit` on
  the second card's play through `SearchPlayAdviser`. Its keep decision's
  heading reads "Choose one or two cards to keep".
- **The order kept** is the order of the Keep section in the answer.

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
- A Transform at the Search source window that adds and marks options
  (Observatory, Errand Boy, Mushrooms).
- A draw from the bottom of a pile (Mushrooms, Wine of Welcome).
- A Transform that replaces the source decision (Spinning Bee).
- A placement limit, and a keep decision that holds two cards (Land
  Warden).

## Testing

- Each card gets a suite through the walker, from a board stated in Table
  steps, with its Game Log lines.
- Slice 1 tests each restriction on both sides where it applies, with bandit
  defenders, and True Names reading an adviser turned faceup during plan
  selection.
- Slice 2 tests Peace Envoy affordable and short, as attacker and defender,
  and Wild Mounts with zero, one and two Nomad discards.
- The verification gate is the roadmap's standing rule.
