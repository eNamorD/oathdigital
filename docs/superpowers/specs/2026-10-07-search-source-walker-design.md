# Search Source in the Walker

**Status:** designed and implemented 2026-10-07. It takes the half of the roadmap item
**Start controls and modifier combinations** that says a Search source which
only a modifier makes affordable shows the reduced cost before the player
selects that modifier. It follows the
[Travel destination design](2026-10-07-travel-destination-walker-design.md),
whose rulings and machinery it reuses.

## Goal

A Search's source becomes the walk's first parked decision, answered on the
board after the modifiers are selected, instead of a selection the client
makes on the Search button. Each source then shows what it costs under the
modifiers actually selected, and a source the player cannot use says why.

## Background

The Act panel draws one button per legal source: "Search world deck
(3 Supply)" and "Search Cradle discard (2 Supply)". The button sends
`StartWalker("search", modifiers, Vector(Button("search:world")))`, through
the modifier flow when modifiers are offered. The source is picked before the
modifiers, so the button cannot know which modifiers will follow.

`LegalActionProjector.legalSearch` fills the buttons from
`SearchProcedure.legalSources`, under every selection
`ModifierOffers.selections` returns. A source legal without modifiers shows
its unmodified cost. A source legal only under a modifier shows the cost under
the first selection that allows it. A player with 1 Supply and 2 favor who
holds News from Afar sees "Search world deck (0 Supply)". The modifier panel
then offers News from Afar unselected, and confirming without it fails.

No Search modifier depends on the source. Disciples may be selected whatever
the source, and News from Afar waives the Supply whatever the source. Forced
Labor's toll sits in `SearchCost` and applies to both sources alike.

A regional discard pile is already a board option (`DecisionOptionRef.DiscardPile`,
option kind `discard-pile`), labelled for card play: "Discard to the Cradle
discard". `DecisionOptionRef.Deck(CardDeck.World)` (kind `deck`, id `world`)
already names the world deck, but has no board home: the World pane draws the
world deck pile as a plain display. Clay Rattle offers its four piles as
`Button` options in the Action panel.

The service checks a Search's draw against its random port
(`SearchDrawPort`) when the Search starts, reading the source from the start
selection.

## Rulings

1. **Modifiers are the walk's first decision.** `StartWalker("search")`
   carries no start selection and no modifiers; a start that names a
   selection is refused. The walk asks for its modifiers first, each paid
   when it is picked, then for the source
   ([modifier walker design](2026-10-09-modifier-walker-design.md)).
2. **The source is a board decision.** The walk's first node is a board
   choose-one, `search.source`, headed "Choose a Search source". Its options
   are the world deck (`DecisionOptionRef.Deck(CardDeck.World)`, option kind
   `deck`) and the actor's region's discard pile
   (`DecisionOptionRef.DiscardPile(region)`). Other regions' discards are
   offered only when a picked modifier adds them (catalog batch 6, slice 3:
   Observatory and Errand Boy). The options read "World deck" and "Cradle
   discard pile"; a card play's discard option reads the same, in place of
   "Discard to the Cradle discard".
3. **Every option says what it costs.** Each available source is `Badged`
   with its cost under the selected modifiers, worded as Travel's ruling 3:
   - Supply only: "3 Supply". A Search that spends no Supply reads "0 Supply".
   - With Forced Labor's toll: "3 Supply · 1 favor", or "1 favor" when no
     Supply is spent.

   A selected modifier's own cost, paid onto its card, is not in the badge.
4. **A source the actor cannot use is shown, not hidden.** It is
   `Unavailable`, with a reason:
   - Too little Supply: "Needs 3 Supply; you have 1".
   - Too little favor for a toll: "Needs 1 favor; you have 0".
   - An empty pile: "The world deck is empty", "The Cradle discard pile is
     empty". The empty-pile gate leaves the start and becomes this reason.
   - Otherwise: "Cannot search there now".
5. **No available source, no Search.** One `beginSearch` control, labelled
   "Search" with no cost, shows only when at least one source is available.
   The engine refuses a start with none (`requiresPlayableOption`). The
   control and the parked decision read the same preview.
6. **Undo is the way back.** Undo takes back the start, as it does for
   Travel. Undo takes back each modifier pick too. The client's ordering
   stage, with its "Back" and "Cancel action", is gone (modifier walker
   design).
7. **Game Log.** The "Search" start line waits for the first step that
   records anything (a toll, a modifier's payment, the Supply or the draw),
   as Travel's does. The source pick posts no "Chose" line, since "Drew …
   from the world deck and kept …" names the source.
8. **Clay Rattle's piles are on the board.** Its `power.clay-rattle.pile`
   decision offers the world deck and the three `DiscardPile` refs instead of
   buttons, so the board-home rule draws them on the board. Empty piles stay
   pickable, as the card allows any pile. They carry no badge: the secrets
   are paid when the power is used, before the pick. Its note is unchanged.

## Architecture

### The tree

```
Sequence(
  Decide(search.source, board choose-one over the two sources),
  Branch { after the answer:
    Sequence(cost, draw, query, placement)  // window = SearchActionEligibility
  })
```

- `cost` keeps `SearchCost` and `draw` keeps `SearchBeforeDraw` on their own
  nodes, now inside the `Branch`'s selected vector. A selected modifier's
  payment, prepended at `SearchActionEligibility`, is paid after the pick, so
  nothing is recorded before the first decision.
- `build` keeps the start-only gates (an acting player, no temporary hand)
  and drops the source gate. `build` and `rebuild` read the source from the
  `search.source` answer and the region from the pawn's site. No start facts
  are needed: the pawn does not move during a Search.
- `SearchProcedure.startable` previews the fresh tree for the legal-action
  projection, under each modifier selection through
  `startableWithModifiers`, as Travel's does.
- The service's draw-port check moves from the start to the `search.source`
  answer, which is where the draw now happens.

### Options

- The generic part of `TravelOptions` moves to a shared walker module that
  takes the decision id and a fallback reason: the badge read from the
  recorded `SpendSupply`, `Give` and non-card `PayCost`, the shortfall read
  from a second preview with the actor's Supply and favor lifted, and the
  previews themselves.
- `TravelOptions` keeps its decision id and "Cannot travel there now", and
  calls the shared module. A Search pricing calls it with `search.source`
  and "Cannot search there now", and maps a `SearchSourceUnavailable`
  rejection to ruling 4's empty-pile wording.
- The Search registry entry declares `optionPricing` and
  `requiresPlayableOption`.

### Retired

`SearchProcedure.legalSources` and `sourceOf`'s start-arg parsing,
`LegalActionProjector.legalSearch`, `LegalSearchSourceProjection`, the
projection's `legalSearchSources` field and its codec, and the per-source
buttons in `ActionDecisionRenderer`.

### Frontend

- A `beginSearch` control sends `StartWalker("search")` through the modifier
  flow.
- The World pane marks the world deck pile as the `deck:world` option, and
  `BoardHomes` gives the world deck a home. A badge renders inside a pile as
  it does on a site box, and a reason is the pile's hover text.

### Persistence

No compatibility code. Journals that hold a Search started with a source
selection no longer replay; development games containing a Search are dropped,
per the roadmap's standing rule on event formats before the first public
release.

## Testing

- **Engine.** A Search parks on `search.source` and completes on its answer.
  A start with a selection is refused, and a start with no available source is
  refused.
- **Pricing.** Badges for plain Supply, News from Afar ("0 Supply"),
  Disciples and Forced Labor's toll. Reasons for a Supply shortfall and an
  empty pile. A toll shortfall cannot show at a parked Search, since the toll
  falls on both piles and a start neither can pay is refused; the shared
  wording is Travel's, whose suites pass unchanged after the extraction.
- **Clay Rattle.** Its answers are pile refs, and an empty pile can be picked.
- **Game Log.** The start line's placement, and no "Chose" line for the
  source pick.
- **Service.** Undo takes back the start. The source answer draws, so it is
  an undo barrier. The draw-port check refuses a tampered draw at the answer.
- **Frontend.** The launch sends no selection. Both piles render as board
  options with badges and reasons. The world deck has a board home.
- Existing suites that start a Search with a source selection move to start,
  then answer; most go through `SearchFixture`.

## Out of scope

- **The two-of-three modifier gap.** A start that needs two modifiers but not
  a third is still not found by `ModifierOffers.selections`. The roadmap item
  is trimmed to this.
