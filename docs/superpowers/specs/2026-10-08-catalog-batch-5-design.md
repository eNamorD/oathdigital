# Catalog Batch 5: Muster and Trade Sources, Adviser Counting

**Status:** designed 2026-10-08. The per-card rulings are in
[the rulings appendix](2026-10-08-catalog-batch-5-rulings.md).

**Builds on** [Powers batch 1](2026-09-20-powers-design.md),
[Catalog batch 2](2026-09-26-catalog-batch-2-design.md),
[Catalog batch 3](2026-09-29-catalog-batch-3-design.md) and
[Catalog batch 4](2026-10-05-catalog-batch-4-design.md), whose vocabulary,
cost rules, walker shapes and log-line rules apply unchanged.

## Goal and scope

**Phase - Catalog batch - engine-blocked** in the roadmap groups its cards by
the engine change each needs. This batch takes the cheapest groups first: the
Muster and Trade source rule and the adviser limit count. Each needs a local
change to an existing procedure, with no new operation, walker window,
decision query kind or option kind. Reroll was considered and left out: it
needs a new operation, a subset-of-dice query and a protocol change.

| Slice | Count | Cards |
|---|---|---|
| 1. Muster and Trade sources | 5 | 199 Golem Legions, 76 Map Library, 238 Skilled Merchants, 229 Old Songs, 6 Pressgangs |
| 2. Warband placement | 1 | 247 Vow of Wandering |
| 3. Adviser counting | 3 | 148 Marriage, 182 Pied Piper, R29 Lost Tapestry |
| 4. Adviser arrival | 1 | 168 Family Wagon |

Vow of Wandering was listed under the adviser limit, but it has nothing to do
with the limit: it is a Muster gain and a ban on placing warbands on sites.

### The bar

Batch 4's bar holds: no new operation, walker window, decision query kind,
option kind, `NoteArg` kind, protocol change or frontend change. Refactors
inside `gameplay/powers/` are allowed. The product owner approved seven
changes outside `gameplay/powers/`:

- **The Muster and Trade source query and `MusterSource.resolve`** (slice 1).
- **A warband placement restriction**, an `OperationRestriction` that a power
  registers (slice 2).
- **The minor action's refusal** names the reason of an operation a
  restriction skipped, in `MinorActions` (slice 2, approved at plan time).
- **The adviser limit count** in `CardPlayRules`, `CardSlots.adviserSlots`
  and `AdviserLimit` (slice 3), and **the weighted suit count**
  `AdviserSuits` beside `MusterSource` (slice 3, placed at plan time).
- **The board's adviser slots** in `GamePresentationProjector` add one slot
  per adviser that does not count (slice 3, approved at plan time), and draw
  the advisers and one empty slot for a player with no limit (slice 4).
- **An adviser arrival pass** in the walker, with `ContributingPower.onArrival`
  and `AdviserArrivals` in `gameplay/powerresolver` (slice 4).
- **Card play's lifted limit and adviser play notes** in `CardPlayRules` and
  `CardSlots.adviserSlots` (slice 4, approved at plan time).

### Out of scope

- **Vow of Union** joins Sneak Attack in the engine-blocked phase. Its
  holder acts at the start of **any** Campaign, which needs an off-turn
  actor. Its suit lock waits with it, so that the card lands whole.
- **Reroll** (Jinx, Master at Arms, Silver Charm, Unstable Summon) and every
  other engine-blocked group stay where they are.

## Slice 1: Muster and Trade sources

Golem Legions, Map Library, Skilled Merchants, Old Songs and Pressgangs are
selected modifiers (`persistent = false`), like Mounted Library. Each acts
through Transforms on the source decision's window,
`MusterSourceSelection` or `TradeSourceSelection`.

### The base query

`MusterSource.atSite` offers every card at the actor's pawn site, denizens
and edifices, in site order. A card that holds favor or secrets is shown
unavailable with the reason "Already holds favor or secrets"
(`DecisionOption.Unavailable` with `OptionReason.Written`). The walker
accepts only available options (`DecisionQueries.accepts`), so the query is
the gate.

A Muster or Trade whose query has no available option offers no start
control, as an all-dropped start does today. Selecting an exemption
modifier can make a start available, and `ModifierOffers.selections`
already tries each single modifier.

### Wideners and exemptions

Transforms on the source window fold in priority order:

- **Wideners, at priority 0,** add options.
  - Golem Legions adds itself.
  - Map Library, selectable only while the actor's pawn is at Map Library's
    site, adds every card at the other sites in the pawn's region.
  - Skilled Merchants, an adviser, does the same from any site.

  An added card that holds tokens arrives unavailable with the same reason.
  An option that is already offered is not added again, so duplicates merge
  by ref.
- **Exemptions, at priority 1,** turn unavailable options available.
  - Pressgangs (Muster only) turns every token-bearing option available.
  - Old Songs (Trade only) turns an option available when its card holds
    exactly one secret and no favor.

Because exemptions fold after wideners, Pressgangs lets Golem Legions muster
on itself while it holds favor. A player who selects both can muster on
Golem twice in one turn.

Vow of Beastkin's `OptionRestriction` at `MusterSourceSelection` still
narrows the result. Restrictions apply after Transforms.

### The cost

`PayCostRules` refuses a `PayCost` onto an occupied card unless the
operation is marked `intoOccupied`. An exemption power therefore also
transforms the cost window (`MusterCost`, `TradeCost`): when the chosen
source holds tokens, it marks the Muster or Trade `PayCost` `intoOccupied`.
Every other `PayCost` keeps the enforcement. `PayCostRules`' doc comment is
updated, because `MusterSource` no longer checks the fact itself.

### `resolve` becomes a lookup

`MusterSource.resolve` finds the answered card wherever it stands:
- at any site in play, as a `RuleSourceRef.SiteCard` or
  `RuleSourceRef.Edifice` origin, or
- among the actor's advisers, with an adviser origin.

It returns the card's catalog suit. It no longer checks the pawn site or the
tokens. A side effect is that batch 4's note no longer applies: a decision
inside the Economy `Branch` failed on resume because `resolve` refused the
source once the cost had put tokens on it. That restriction may now be
dropped, but this batch moves no existing decision.

### Golem Legions counts itself

"It counts itself as a matching adviser" is a reminder. `matching` counts
the actor's faceup advisers of the source suit, and a selectable Golem is a
faceup Arcane adviser. Golem adds nothing to the count. A test pins the
count at its own value plus the other Arcane advisers.

### Implementation notes from slice 1

- **The cost mark reads the answer.** An exemption marks the actor's
  `PayCost` onto the answered source `intoOccupied` whenever it is selected,
  not only when the source holds tokens. The source decision is the gate, so
  the mark changes nothing for a token-free card, and the fold depends on the
  answers alone.
- **No slice 1 power writes a line.** Each is named on its action's start
  line, and the Muster and Trade lines name the source wherever it stands.

## Slice 2: Vow of Wandering

- **The Muster gain** adds two warbands to the Muster's own gain at
  `MusterGain`, as Vow of Beastkin's does. It is best effort from the
  supply. Its line reads what the gain added beyond its amount before the
  Vow, at most two. A Muster under both Vows writes each Vow's own part.
- **The placement restriction** is an `OperationRestriction` the card
  registers through `ContributingPower.operationRestrictions`, as Lost
  Tongue does. While the card is a faceup adviser, the restriction refuses
  any `Move` that puts its holder's warbands on a site: from the board, from
  the bank or from another site. It reads the leaves of a composite, so a
  `Replace` is refused too. A move from a site to the board stays legal.
  - Warbands are the holder's when they leave the holder's board, or when
    they are of the holder's kind. The Chancellor and the Citizens share
    Imperial warbands, so Imperial ones count only while the holder is the
    active player.
  - The refusal is `Impossible`: an optional placement it refuses is
    skipped, and no walk fails because of it. The search hides an option
    only when its walk fails, so it hides nothing here. Each power that
    places warbands reads the Vow itself:
    - Garrison gains its warbands, does not ask for sites, and writes the
      Vow's line.
    - Key to the City kills and gains, and writes the Vow's line instead of
      its placement line.
    - Bandit Prince does not ask, and writes the Vow's line. The walker
      never asks an optional question whose every option is unavailable, so
      the line takes the place of the hover reasons.
    - Ballot Box can replace none of the warbands, so it removes them all,
      after the Vow's line.
    - Hospital saves nothing: the holder's dead stay killed.
    - Messenger and Warning Signals cap each site at what it holds, so the
      holder can only bring warbands back to the board.
  - The Vow's line, under the power it stopped: "Vow of Wandering forbids
    placing warbands on sites."
  - The minor action refuses a move to the site with the reason of the
    operation the restriction skipped: "Vow of Wandering forbids placing
    warbands on sites". The client still offers the move.
- **Campaign conquest** asks "place up to N survivors" with a
  `ChooseAmount`, which cannot carry a hover reason. A Transform on
  `CampaignPlacement` replaces the question with a line, so the survivors
  stay on the board: "Kept {n} surviving warbands on the board." The
  restriction still guards the placement.

## Slice 3: Adviser counting

### The weighted suit count

`AdviserSuits.count(catalog, ready, player, suit)` counts a player's faceup
advisers of a suit, with a faceup Marriage counting as two.
`AdviserSuits.weight(card)` gives the weight. Both live in
`gameplay/actions/economy` beside `MusterSource`, with `MarriageCard`: Muster
and Trade read the count, and `gameplay/actions` never imports
`gameplay/powers`. The registered Marriage power adds nothing; it makes the
card count as implemented.

These callers count and move onto the weight:
- `MusterSource.matching`, so Muster and Trade: warbands, favor and secrets
- Town Meeting, Fabled Feast and Wizards Conclave, through
  `RuledCards.count`
- Great Crusade and Pledge of Defense, through `GreatCrusade.nomads`
- Nature Worship
- Military Parade, which lists a Marriage twice for `FavorBySuit`

These list targets or check for one, and stay on cards: Curfew, Resettle,
Twin Brother, Ballot Box, Dissent, Kindred Warriors, Lost Tongue, Diplomat,
Disgraced Captain, Battle Axes, Rival Khan, Cracked Sage, Cup of Plenty, Vow
of Beastkin, Rotting Fortress, Honor Guard and Second Chance. Tribute Spoils
feeds `FavorBySuit` site cards only, and Marriage is adviser-only.

### The count toward the limit

Limits are checked against the advisers as they would be after the card is
played. `CardPlayRules`:
- keeps `adviserLimit`, a limit whatever is held
- gains per-card limits, `limitWhileFaceUp(card, limit)`, which hold while
  their card is a faceup adviser after the play. Silver Tongue's, Insomnia's
  and Assassin's `HolderAdviserLimit` moves onto it, and `playedCardLimit`
  goes.
- gains the adviser faces that do not count, `ignoreLimit(face)`
- answers `fits(advisers)` for the advisers after a play

`CardSlots.adviserSlots` offers a new slot when the area after it fits, and
only that. Otherwise it offers each Replace whose result fits. A Replace of
an adviser that does not count is shown unavailable with the reason "Does
not count toward the adviser limit", because replacing it frees no room.
With no Replace that fits, it offers nothing.

The faces that do not count:
- Pied Piper, faceup. A facedown Pied Piper has no power and counts.
- The holder's facedown Visions, while the holder has Lost Tapestry faceup.

Each power marks its faces with a Transform at `SearchPlayAdviser`.
`AdviserLimit.ignored` and `AdviserLimit.towardLimit` read the same facts
for Horned Mask and the board. Marriage counts once, as any adviser does.

Horned Mask is full when the count toward the limit reaches the limit. Its
discard question shows an adviser that does not count unavailable, with the
same reason. The board projection adds one slot per adviser that does not
count, so the empty slots drawn match the free room.

### Pied Piper

Pied Piper's ACTION (cost one secret, placed on the card) asks which other
player takes it, any player at any site. It moves Pied Piper, with its
secret, from the actor's advisers into that player's, faceup, with a
required `Give`. It then takes up to two favor from that player with a
`Take`. The receiver may be at or over the limit, since Pied Piper does not
count. `RestrictionIcons` allows the move, because Pied Piper is
adviser-only. Its lines:
- "Moved to {Blue}'s advisers and took {2 favor} from them."
- "Moved to {Blue}'s advisers. {Blue} had no favor to take."

## Slice 4: The adviser arrival pass and Family Wagon

### The arrival pass

No hook runs when an adviser arrives by a route other than card play:
Horned Mask, `Swap` (Enchantress, Twin Brother, Dream Thief) and Pied
Piper's move add advisers silently. The pass sits where every walker leaf
runs its operations (`WalkerSteps.recordBatch`), so it sees every route,
card play included. After a leaf's operations apply, it compares each
player's advisers before and after (`AdviserArrivals.between`). An adviser
that is new, or that turned faceup, is an arrival.

Powers register arrival rules through `ContributingPower.onArrival`, which
returns an `ArrivalFollowUp`: operations and a line. The pass asks every
power, in the order contributions chain. Each follow-up runs as one
required batch, recorded as its own step at the leaf's node and credited to
its power, and its line is journaled right after it. A follow-up with no
operations records no step and journals only its line. Replay applies the
recorded operations, and the Game Log reads the step and the line. The pass
names no card. It runs once per leaf, and the follow-up operations it adds
do not start another pass.

A line after the leaf reads the leaf and its follow-ups together. So when
Pied Piper moves into a Family Wagon holder's advisers, Family Wagon's line
comes before Pied Piper's.

### Family Wagon

While a player holds Family Wagon faceup, any arrival in that player's
advisers buries every adviser that is not a faceup Nomad. Family Wagon's
own arrival counts. The buried cards include:
- facedown advisers, Visions too, since a suitless card is not a Nomad
  adviser
- locked advisers such as Marriage, because Locked guards against a
  discard and a bury is not a discard

Advisers are buried in held order with `Bury.standard`: favor goes to the
card's suit bank, and secrets to the active player, facedown. The line
names the buried cards each viewer may identify: "Buried {cards}." on the
holder's turn, "{Blue} buried {cards}." otherwise. It covers the generic
"Buried" lines.

A card Family Wagon buries on arrival was never gained, so its WHEN PLAYED
does not run: `WhenPlayedPower` skips a card that went to the world deck
before its hook. Powers that trigger on a play (Saddle Makers, Wild Cry,
Gossip, Welcoming Party) still fire, because the card was played.

Family Wagon makes its holder's limit unbounded. `AdviserLimit.of` gives
its holder no limit before the lowering powers apply. In card play, Family
Wagon's Transform at `SearchPlayAdviser` lifts the base limit while Family
Wagon is a faceup adviser after the play (`CardPlayRules.liftWhileFaceUp`),
and cards' own limits still apply. The board draws the holder's advisers and
one empty slot.

A card play that would add a non-Nomad or facedown adviser stays on offer.
Card play shows it `Warned` with the reason "Family Wagon will bury it",
from the note Family Wagon adds to `CardPlayRules`. A play that replaces
Family Wagon, and the play of Family Wagon itself, carry no note.

## Seams used for the first time

Each gets a test through its first power before the power relies on it.
If a seam needs an engine change beyond the four approved, stop and ask
the product owner.

- A source query whose options are all unavailable offers no start
  control, and an exemption modifier makes one available (Pressgangs).
- A `PayCost` onto an adviser (Golem Legions) and onto an occupied card
  marked `intoOccupied` by a Transform (Pressgangs, Old Songs).
- A Muster or Trade resumed after its cost, whose source now holds tokens.
- A `Move` of a denizen from one player's advisers to another's (Pied
  Piper).
- An `OperationRestriction` registered by a power over warband moves (Vow of
  Wandering).
- A `Bury` of an adviser, faceup and facedown (Family Wagon).

## Testing

- Each card gets a suite through the walker, from a board stated in Table
  steps, with its Game Log lines.
- Slice 1 tests the composition above: Golem with Pressgangs, Map Library
  with Skilled Merchants on the same card, Old Songs refusing a card with one
  secret and one favor, and Vow of Beastkin narrowing a widened query.
- Slice 2 tests every placement path in the list, refused for the holder and
  allowed for other players.
- Slice 3 tests each counting caller with Marriage, and Replace at the limit
  with Pied Piper held.
- Slice 4 tests each arrival route: card play, Family Wagon's own arrival,
  turning faceup, Horned Mask, a swap and Pied Piper's move.
- The verification gate is the roadmap's standing rule.
