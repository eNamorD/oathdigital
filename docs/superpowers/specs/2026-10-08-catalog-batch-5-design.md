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
inside `gameplay/powers/` are allowed. The product owner approved four
changes outside `gameplay/powers/`:

- **The Muster and Trade source query and `MusterSource.resolve`** (slice 1).
- **A warband placement restriction**, an `OperationRestriction` that a power
  registers (slice 2).
- **The adviser limit count** in `CardSlots.adviserSlots` and
  `AdviserLimit.of` (slice 3).
- **An adviser arrival pass** in the walker (slice 4).

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
  supply. Its line reads what the gain added beyond the base.
- **The placement restriction** is an `OperationRestriction` the card
  registers through `ContributingPower.operationRestrictions`, as Lost
  Tongue does. While the card is a faceup adviser, the restriction refuses
  any `Move` of its holder's warbands whose destination is a site, from the
  board or from another site. A move from a site to the board stays legal.
  It covers every path found: the minor action, `WarbandArrangement`
  (Messenger, Warning Signals), Garrison, Key to the City, Hospital, Bandit
  Prince and Ballot Box. An option it refuses is hidden by the search, and
  the minor action already runs restrictions.
- **Campaign conquest** asks "place up to N survivors" with a
  `ChooseAmount`, which cannot carry a hover reason. A Transform on
  `CampaignPlacement` removes the question, so the survivors stay on the
  board. Its line says Vow of Wandering kept them there. The restriction
  still guards the placement.

## Slice 3: Adviser counting

### The weighted suit count

`AdviserSuits.count(catalog, ready, player, suit)` counts a player's faceup
advisers of a suit, with Marriage counting as two Hearth advisers. Every
caller that counts moves onto it:
- `MusterSource.matching`, so Muster, Trade, Cup of Plenty, Vow of Beastkin
  and The Old Oak follow
- the callers of `RuledCards.of` that count rather than list
- `FavorBySuit` as Military Parade feeds it
- Nature Worship

A caller that lists targets or only asks whether a player has one adviser
of a suit stays on cards, because a second Marriage changes nothing there.
The plan sorts each `RuledCards.of` caller into one kind or the other and
records the sort.

### The count toward the limit

`AdviserLimit.towardLimit(catalog, ready, player)` counts the advisers that
count toward the limit. It skips:
- Pied Piper, which ignores the limit
- facedown Visions, while the player holds Lost Tapestry

Marriage counts once, as any adviser does. Family Wagon makes the limit
unbounded. `AdviserLimit.of` returns the unbounded limit for its holder
before the lowering powers apply.

`CardSlots.adviserSlots` compares the count toward the limit, not
`held.size`, against the limit. At the limit, Replace offers each held
adviser. An adviser that does not count is shown unavailable with the reason
"Does not count toward the adviser limit", because replacing it frees no
room. Horned Mask's check reads the same count.

### Pied Piper

Pied Piper's ACTION (cost one secret) moves Pied Piper from the actor's
advisers into another player's advisers, faceup. It then takes two favor
from that player, best effort. The receiver may be at or over the limit,
since Pied Piper does not count. `RestrictionIcons` allows the move, because
Pied Piper is adviser-only. The move is a plain `Move` between play areas.
No power has moved a denizen adviser one way between players before.

## Slice 4: The adviser arrival pass and Family Wagon

### The arrival pass

No hook runs when an adviser arrives by a route other than card play:
Horned Mask, `Swap` (Enchantress, Twin Brother, Dream Thief) and Pied
Piper's move add advisers silently. After a leaf's operations apply, the
walker compares each player's advisers before and after. An adviser that
is new, or that turned faceup, is an arrival. For each arrival, the pass
asks the registered arrival rules for follow-up operations. It runs them as
journaled operations in the same step, so replay and the Game Log see them.

Powers register arrival rules through a new member of `ContributingPower`.
The pass names no card. It runs once per leaf, and the follow-up operations
it adds do not start another pass.

### Family Wagon

While a player holds Family Wagon faceup, any arrival in that player's
advisers buries every adviser that is not a faceup Nomad. Family Wagon's
own arrival counts. The buried cards include:
- facedown advisers, Visions too, since a suitless card is not a Nomad
  adviser
- locked advisers such as Marriage, because Locked guards against a
  discard and a bury is not a discard

Advisers are buried in held order with `Bury`. Family Wagon's line names the
buried cards each viewer may identify.

A card play that would add a non-Nomad or facedown adviser stays on offer.
Its option carries a note that the card will be buried. Card play's slots
give Family Wagon's holder no limit (slice 3).

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
