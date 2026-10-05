# Card Play Through Card Slots

**Status:** designed 2026-10-04. Spec 2 of board decisions. It builds on the
[board decisions spec](2026-10-03-board-decisions-design.md) and replaces
**Phase - Card play UI** and the Powers-related deferred item
**walker-native card play through card slots**.

## Goal

A player plays a card by clicking where it goes. The empty site slot, the
adviser slot, the revealed Vision spot or the discard pile lights up on the
table; a card the play would discard is marked "Replace". One decision
answers where the card goes and what it displaces. Powers that change card
play (Homeland, People's Favor, Crop Rotation, Silver Tongue, Insomnia, Vow
of Obedience, Sacred Ground) shape that one decision, and a power that
refuses an option on the table says why.

## Rulings

- Placement is one choose-one over slot options: site slots at the actor's
  site, the actor's adviser slots, the actor's revealed Vision spot, and the
  discard pile the card would go to. There is no "site or advisers" step.
- A slot pick never submits by itself. Confirm submits it (spec 1).
- Only the first empty slot of each area is lit; empty slots are
  interchangeable.
- An occupied slot whose card the play may or must discard is an option
  marked "Replace". The played card takes that card's place.
- An adviser slot carries two options, faceup and facedown. Selecting the
  slot shows a facing toggle in the Action panel with no default; Confirm
  stays disabled until a facing is set. When only one facing is legal it is
  preselected, and an unavailable facing shows disabled with its reason.
- A faceup Vision goes to the revealed Vision spot, not an adviser slot. The
  spot is marked "Replace" when a revealed Vision is there.
- A faceup Conspiracy takes no slot, so it is not a slot option: it is a
  "Play" button in the Action panel, beside the discard pile on the board.
  A Conspiracy from the hand may still be played facedown to an adviser
  slot.
- Discarding the played card instead is the discard pile of the next region
  (the region after the pawn's), lit as a board option.
- Site discard permissions stack: Homeland, People's Favor and Crop Rotation
  each allow one discard. The slot pick spends one; when it was a Replace
  slot and permissions remain, a follow-up choose-many offers more site
  cards.
- Reasons: an option refused by a power or a printed protection shows its
  reason. An option excluded by a core game rule is not lit and has no
  reason (a Vision at a site slot, a full site with no discard permission).
- An adviser limit lowered by Silver Tongue or Insomnia draws fewer adviser
  slots; the missing slot is explanation enough and carries no reason.
- Partition decisions (Search's keep and discard) keep their drag and drop.
- Old in-progress journals are not migrated. Decision ids and button keys
  change freely.

## Protocol

### Option refs

`DecisionOptionRef` gains four variants and widens one. Each joins
`DecisionOption.forRef`, the wire codecs (`DecisionAnswerCodec`,
`WalkerEventCodec`), `ChoiceWords` and the projector.

| Ref | Meaning | Wire kind |
| --- | --- | --- |
| `SiteSlot(site, index)` | Slot `index` of `site`'s card row, `index` below the site's capacity. Occupied means Replace. | `site-slot` |
| `AdviserSlot(owner, index)` | Widened: `index` below the printed adviser capacity (3), empty or occupied. Existing users (Hunger, Dream Thief, Ivory Eye, setup) keep naming occupied slots. | `adviser-slot` |
| `AdviserPlay(owner, index, faceup)` | Play the card to adviser slot `index` with that facing. Occupied means Replace. | `adviser-play` |
| `RevealedVision(owner)` | Play the Vision faceup as `owner`'s revealed Vision. Occupied means Replace. Not used for a Conspiracy. | `revealed-vision` |
| `DiscardPile(region)` | Discard the played card to `region`'s discard. | `discard-pile` |

`AdviserPlay` exists so that a choose-one answer, one ref, carries the facing.
Two options share one board home; see Frontend.

### Replace mark

`DecisionOption` gains a `Replacing(option, card: CardId)` wrapper beside
`Priced`, `Badged` and `Unavailable`. The projection carries it as
`replaces: Option[CardRef]`; the client draws the badge and names the card in
the summary.

### Projection

- Each player board carries `adviserLimit`, the effective limit (3, or 2
  under Silver Tongue or Insomnia).
- Site rows already carry `denizenCapacity`; empty slots get their index.
- Player boards carry the revealed Vision spot, empty or occupied.
- `MinorAdviserProjection.placements` is removed; the client uses only
  whether the player has facedown advisers.

## Engine

### Card play rules

`PlacementRules` becomes `CardPlayRules`, still contributed at window
`SearchPlayAdviser` through `PlacementTree.adjust`:

- `adviserLimit: Int`, the one limit on all of the player's advisers,
  whatever their facing, set by the advisers they hold; and
  `playedCardLimit: Int`, the limit the played card sets once it is faceup.
  The rulebook checks a play against the new limit ("When you want to play a
  card that changes your adviser limit, check whether to discard advisers
  based on your new adviser limit"), so a faceup play counts against the
  lower of the two and a facedown play against `adviserLimit` alone. The
  implemented limit cards only lower, so contributions compose in any order.
- `siteDiscards: Vector[DiscardPermission]`, with
  `DiscardPermission(source, note)`, each allowing one discard of any site
  card. An edifice is a denizen for placement, so Crop Rotation's "a denizen"
  covers one; an intact edifice is still Locked. Homeland (a rule, keyed off
  the played card's suit matching the site's Homeland suit) adds one;
  People's Favor (Mob) adds one; Crop Rotation adds one.

Silver Tongue and Insomnia stop appending a guard and lower the limit
instead. They write no reason and no line, as today.

### Slot builder

`CardSlots` replaces `CardPlay.legalChoices` and `CardPlay.plannedOperations`.
From the card, its origin, the actor's state and `CardPlayRules` it builds:

1. The placement options, each with its plan:
   - Site slots: the first empty one, if the site has room and the card may
     go to a site; each occupied slot whose card some permission covers,
     marked Replace (required when the site is full).
   - Adviser slots, for each legal facing: the first empty one below the
     limit; when none is empty, each occupied slot, marked Replace. No slot
     at or past the limit is offered. A play from a facedown adviser offers that
     card's own slot, faceup, and no other adviser slot.
   - The revealed Vision spot, for a faceup Vision other than Conspiracy.
   - `Button("play")`, labelled "Play", for a faceup Conspiracy.
   - The discard pile.
2. The facing rules from today's `legalChoices` and `planVision`: a Vision
   never goes to a site and goes faceup only to the revealed Vision spot; a
   site-only denizen goes to advisers facedown only; an adviser-only denizen
   never goes to a site; a play from a facedown adviser is faceup only and
   replaces no adviser.
3. Reasons for options a printed protection refuses. The global operation
   restrictions (`LockedCards`, which covers `Locked` denizens and intact
   edifices, and `ActiveModifier`) mark an option `Unavailable` with written
   text instead of dropping it:
   - Locked card or intact edifice: "Locked: cannot be discarded".
   - Active modifier: "In use as a modifier this action".

   Options refused by a core rule are left out.

The Search discards of the cards not kept use the same builder's discard
plan.

### Placement procedure

`CardPlayProcedure` keeps its shape:

1. `Decide("cardplay.place.<kind>.<id>")`, the choose-one of slot options,
   heading "Play <card>".
2. When the answer was a Replace site slot and site discard permissions
   remain, `Decide("cardplay.discard.<kind>.<id>")`: a choose-many over the
   other site cards, from 0 up to the remaining count, with the replaced card
   locked. Heading "Discard more cards from <site>".
3. `BuildOps` from the chosen plan, then the `CardPlayedFacedown` or
   `CardPlayedFaceup` hook node.

Discards are matched to permissions automatically, in order: every
permission covers any site card, so the follow-up allows as many more cards
as permissions remain. The log credits each discard to the power or rule
that allowed it, with the existing notes.

Vow of Obedience and Sacred Ground are unchanged: they forbid the
`CardPlayedFaceup` operation, so the look-ahead marks every option leading
there (`AdviserPlay` faceup, `RevealedVision`) `Unavailable` with the power's
note. Gossip, Welcoming Party and the other hooks are unchanged.

### Entry points

- **Search**: cost, draw, the partition when more than one card was drawn,
  then the placement procedure.
- **Play facedown adviser**: starts with the chosen card as today.
- **Oracle**: unchanged, through the placement procedure.
- **Conspiracy**: a faceup Conspiracy is the "Play" button and takes no
  slot; its target decision is unchanged.

`LegalActionProjector.adviserPlacements` and the old `cardplay.replace.*`
decision go away. `ActionLines` and `DetailLines` narrate the new decision
ids.

## Frontend

### Board homes

| Ref | Home |
| --- | --- |
| `site-slot` | the empty slot element at that index, or the site card there |
| `adviser-slot`, `adviser-play` | the adviser card or empty placeholder at that index |
| `revealed-vision` | the revealed Vision spot |
| `discard-pile` | that region's "Discard" pile |

Empty site slots gain their index. Player boards draw empty adviser
placeholders up to the player's `adviserLimit`, so a player under Silver
Tongue or Insomnia has two adviser slots. The revealed Vision spot is drawn
empty when there is none.

### Several options on one home

When more than one option shares a board home, clicking the home selects the
home. The Action panel then shows a toggle of those options' labels
("Faceup", "Facedown"), with no default unless only one is available.
Confirm stays disabled until one is picked. This is generic; `AdviserPlay` is
its first user.

### Replace

A Replace option shows the usual available border plus a "Replace" badge in
its corner. The inspect "i" stays on the card. The summary line reads
"Discard Tavern Songs; play Wayward Spirit here".

### Action panel

The panel shows the card being played (`temporaryHandPreview`, with its
inspect button), the summary line, the facing toggle when an adviser slot is
selected, any Decide buttons (the faceup Conspiracy's "Play"), Clear and
Confirm.

### Playing a facedown adviser

The "Play facedown adviser" button lights the player's facedown advisers on
the board before the walker starts. Clicking one selects it; the panel shows
"Play <card>" and "Cancel action". With exactly one facedown adviser it is
preselected. This is a local board choice, not a parked decision, so it
keeps its Cancel; the Undo item in the roadmap tracks moving it into the
walker. `FacedownAdviserRenderer`'s panel picker is removed.

## Plans

Two plans, each landing green.

1. **Engine.** The refs, `Replacing`, `CardPlayRules`, `CardSlots`, the
   placement procedure, the follow-up discard, stacked permissions, the
   reasons, Silver Tongue and Insomnia as limits, the projection fields and
   the log lines. The client shows slot options as labelled panel rows
   ("Empty slot at Tavern", "Replace Tavern Songs at Tavern", "Adviser slot
   3, facedown", "Discard to the Cradle discard"), which become the board's
   summary text in plan 2.
2. **Board.** The homes above, empty adviser placeholders and the revealed
   Vision spot, several options on one home and the facing toggle, the
   Replace badge, and the board pick for playing a facedown adviser.
   Mark **Phase - Card play UI** and the deferred item done.

## Testing

- Engine suites: `CardSlots` per case (empty and full site, Homeland,
  People's Favor, Crop Rotation (an edifice included), stacked permissions, full advisers, Silver Tongue and Insomnia limits, Vision
  and revealed Vision, site-only denizens, facedown-adviser origin,
  Conspiracy's "Play", Locked and active-modifier reasons); the follow-up
  discard; Vow
  and Sacred Ground reasons on `AdviserPlay` and `RevealedVision`; the codecs
  round-trip every new ref; the log narrates plays and credited discards.
  Existing card play, Search, Oracle and power suites move to the new refs.
- Frontend suites: the board-home rule for each new ref; several options on
  one home and the facing toggle; Confirm disabled until a facing is set;
  the Replace badge and summary; empty adviser placeholders up to the
  limit; the facedown
  adviser board pick and Cancel.
- Browser check on a scratch database server on port 8081, never the live
  database: a Search played to a site with Homeland, to advisers faceup and
  facedown, to a full adviser area, and discarded; a facedown adviser played;
  a Vision revealed over another; screenshots.
