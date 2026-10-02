# Table Readability

**Status:** designed 2026-10-02. Implements six cleanup tasks from the
playtest of 2026-10-01: keep site powers visible, shrink the player strip,
enlarge glyphs, make secrets and defense dice look different, show the cards
in a discard pile, and show how warbands map to Supply.

## Goal

A player at the table reads the board without leaning in. The strip gives
height back to the map, site powers stay on the map, glyphs are larger and
each has its own shape, and two things the board only hinted at (a discard
pile's contents, the Supply a Rest will return) open on a click.

## Ruling on map text

Text on the map may be unreadable while zoomed out. The player zooms in to
read it. This loosens the Glance Layer Rule (DESIGN.md): the study layer is
no longer hidden below the compact threshold; it shrinks with the map. The
glance layer (region and site names, pawns, tokens, defense, forces, card
names) keeps its counter-scaled size, so the Fit view still scans.

## Measurements at 1440×900

- World pane 627px tall. Map surface 1500px wide, about 1460px tall. Fit is
  height-bound at 0.43; width alone allows 0.57.
- Strip row `minmax(150px, 26%)`, 234px. The card row sets a board's height;
  a board with compact card faces needs about 140px.
- Compact threshold 0.72. No layout change reaches it.

## Layout

### Player strip

- Row becomes `minmax(140px, 16%)`.
- Card faces stay. Advisers and relics render at the compact map face size
  (the board's `.board-cards` font size drops so a card is about 5rem tall).
- Identity, resources, title and Vision pills, and banner lines stay as
  they are. Nothing in the strip scrolls vertically.
- Fit rises to about 0.49.

### Site powers

- `.map-compact .site-footer { display: none }` goes. The footer (powers and
  Forge requirement) renders in compact mode at the map's own size, scaled
  with the map.
- The site box grows from `--site-h: 19.5rem` to 21rem, the height of one
  footer line plus its margin, so the footer never costs the card row.
- `.site-powers` wraps to at most two lines (`-webkit-line-clamp: 2`)
  instead of one ellipsized line. Each power's rules text stays on its
  hover title.
- The compact card-row formula (`--card-w`) subtracts the footer line, the
  way it subtracts the forces line.
- The map's Fit scale is computed from the map's height, so the box change
  moves Fit by about −0.02. Nothing in compact mode may change the map's
  height; the box is still fixed and clips.

## Glyphs

### Shapes

Secrets and defense dice blur because both are hollow outlines in mid-blue
at the same size. Both change shape; the Shape First Rule holds.

- `secret`: a solid book with a page-spine notch. `secret-burnt` keeps the
  solid body and the existing burn stroke.
- `defense-die`: a shield with two cut-out pips. Color stays in the die
  triad (attack red, defense blue, round purple).

Paths in `TokenSprite`:

```
secret        M4 3h13a2 2 0 0 1 2 2v16H7a3 3 0 0 1-3-3zM8 6v12h1.6V6z
defense-die   M12 1.5 20.5 4.8v6.4c0 5.2-3.6 9.4-8.5 11.3-4.9-1.9-8.5-6.1-8.5-11.3V4.8z
              M9 8h2.6v2.6H9zm3.4 4.2H15v2.6h-2.6z
```

Both use `fill-rule: evenodd`.

### Sizes

| Place | Now | New |
|---|---|---|
| `.token-glyph` base | 1.05em | 1.25em |
| Card header suit (`.card-header .token-glyph`) | 1.2em | 1.5em |
| Compact map corner suit | 1.05em | 1.5em |
| Card token row (`.card-tokens`) | 0.8em | 1em |
| Card defense row (`.card-defense`) | 0.8em | 1em |
| Player strip resources (`.resources .token-glyph`) | 1.05em | 1.3em |
| Die chip (`.die-face .token-glyph`) | 0.95em | 1.1em |

The compact face's corner suit is absolutely positioned, so
`.map-compact .site .card-face` gets `padding-top: 1.7em` to keep the name
clear of the larger glyph.

## Discard pile overlay

The pile widget is unchanged. It becomes a button with the title pill's
hover. Click opens the card-list overlay (`CardInspection.openCards`, the
one a log line's card list uses), titled "Cradle discard pile (12)", holding
one card per pile card, top first.

### Projection

`SetupRegionProjection` gains `discardCards: Vector[CardDetailsProjection]`,
top first. `discardCount` and `discardTopCardKind` are derived from it in
the DTO (`def discardCount = discardCards.size`); the codec writes the three
fields as now plus the vector, so an old client still reads the count.

Each entry goes through the one visibility rule:
`identifiesCard(ready, viewer, id, None, CardContainer.RegionalDiscard(region))`.
Identified: `cardDetails(id, None, hidden = false)`, a face. Otherwise
`hiddenCard(kind)`, a back with the kind letter. Today that shows backs, and
a Scryer-peeked card as its face. When the engine keeps knowledge on
discard, faces appear with no frontend change.

`CardList` and `CardFace` are untouched.

## Supply overlay

Click "Supply 5/7" on any player board. It becomes a button with the title
pill's hover and opens the text overlay Oathkeeper titles use
(`CardInspection.openText`), titled "Supply at Rest". Body, one line each:

- "Warbands in bag: 5 (14 printed, 6 on board, 3 at sites)."
- The band table, the current band marked: "0–3 warbands: Supply 4",
  "4–8 warbands: Supply 5", "9 or more warbands: Supply 6".
- "Next Rest: 5 from the band, plus unspent Supply, capped at 7."

Public information, so every board opens it. A citizen board says
"Citizens copy the Chancellor's Supply." and shows no table; citizens are
outside the all-Exile alpha beyond that line.

### Projection

- `PlayerBoardProjection` gains `bankedWarbands: Int`, `printedWarbands: Int`,
  `siteWarbands: Int` and `restSupplyBase: Int`. The first three come from
  one engine function, `PlayerFacts.banked` and its parts;
  `FinishRestProcedure.bankedWarbands` is replaced by it so Rest and the
  overlay read one count. `restSupplyBase` is
  `ExileSupply.baseSupplyFor(banked)`.
- `GameProjection` gains `supplyBands: Vector[SupplyBandProjection]`, each
  `(from: Int, to: Option[Int], supply: Int)`, from
  `ExileSupply.refreshBands`, so the frontend never holds the table.

## Testing

- `PlayerBoardSuite` (jsdom): the strip's card row carries the compact
  font size; "Supply 5/7" is a button; clicking it opens the text overlay
  with the three line groups and the current band marked.
- `SiteBoxLayoutSuite` and `SiteFaceSuite`: a compact map still renders
  `.site-footer`; the box height is 21rem.
- `SuitGlyphSuite`: the secret and defense-die symbols carry the new paths
  and `fill-rule`; the strip's resource glyphs are 1.3em.
- `BoardSurfaceSuite`: the pile is a button; its click opens the card-list
  overlay with one `CardFace` per card, backs for hidden entries.
- A new `GamePresentationProjectorDiscardSuite`, beside the adviser
  redaction suite: a discard pile projects top first; a peeked card
  projects as a face to its peeker and a back to others.
- `SupplyProjectionSuite`: `bankedWarbands` and `restSupplyBase` match
  `FinishRestProcedure`'s Rest result; `supplyBands` matches `ExileSupply`.
- `ProjectionProtocolSuite`: the new fields round-trip, and a payload
  without them decodes to empty defaults.
- `MapViewStateSuite`: name buckets re-checked at the larger corner suit.
- Browser check at 1440×900 and 1024×768 over the scratch DB: strip height,
  Fit scale, footer visible on zoom, both overlays, glyphs on a site card
  and in the strip.

## DESIGN.md

Amend: Glance Layer Rule (study layer scales, not hidden; box 21rem),
Layout (strip row and board heights), Sites (powers two lines, visible in
compact), Cards and Dice (glyph sizes), Player Boards (card size, Supply
button), and the token glyph list.

## Out of scope

- Keeping a known card known when it enters a discard pile (an engine
  knowledge rule).
- Moving the Shared Bank and favor banks off the map (Fit 0.49 to 0.57).
- Citizen Supply.
