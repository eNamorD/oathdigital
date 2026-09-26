---
name: Oath Digital
description: A dark table for the painted world of Oath, built for players who know the board.
colors:
  brass: "#d2a85e"
  brass-bright: "#f1c781"
  brass-label: "#d5bd8f"
  brass-line: "#7c694b"
  cream: "#f5ecd7"
  cream-bright: "#fff2d6"
  cream-focus: "#fff0c9"
  parchment-muted: "#d9cdb5"
  parchment-dim: "#c9bfa9"
  ink-dim: "#a99f8c"
  ink-separator: "#6f6455"
  table: "#0f0e0c"
  base: "#17140f"
  pane: "#211f1b"
  pane-header: "#26231d"
  panel: "#211d17"
  site: "#2b251d"
  raised: "#302a20"
  pressed: "#3b3123"
  line: "#675a43"
  line-mid: "#88775d"
  line-dim: "#40382c"
  line-pane: "#443c30"
  region-cradle: "#213a24"
  region-cradle-line: "#3d5f40"
  region-provinces: "#1d3441"
  region-provinces-line: "#3a5b6d"
  region-hinterland: "#46261a"
  region-hinterland-line: "#7a4a35"
  selection: "#8ecbff"
  selection-fill: "#243442"
  facedown: "#2a2333"
  facedown-letter: "#cbb7dd"
  pile-back: "#493751"
  unimplemented: "#a15c4a"
  error-line: "#e27f79"
  error-text: "#ffd0cd"
  replay: "#9ed59e"
  limiter: "#ff8c7f"
  dev-panel: "#211d25"
  dev-line: "#927da4"
  token-favor: "#e4ba5a"
  token-favor-burnt: "#a28a5a"
  token-secret: "#66a8d8"
  token-secret-burnt: "#60849c"
  token-symbol: "#d8cdb4"
  suit-arcane: "#6e377d"
  suit-beast: "#8c371e"
  suit-discord: "#ce2029"
  suit-hearth: "#e06a1e"
  suit-nomad: "#4b9678"
  suit-order: "#14417d"
  die-attack: "#c0392b"
  die-defense: "#3d6fb4"
  die-round: "#9b59b6"
  player-purple: "#d9b8ff"
  player-blue: "#8ecbff"
  player-red: "#ffaaa5"
  player-yellow: "#ffe08a"
  player-white: "#ffffff"
  player-black: "#111111"
  player-black-pill: "#e8dcc2"
  player-pink: "#ffa6d8"
  player-brown: "#d0a075"
  force-bandit: "#d7ed72"
typography:
  display:
    fontFamily: "Georgia, serif"
    fontSize: "2.5rem"
    fontWeight: 400
    lineHeight: 1.2
  headline:
    fontFamily: "Georgia, serif"
    fontSize: "1.08rem"
    fontWeight: 800
    lineHeight: 1.2
  title:
    fontFamily: "Inter, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.8rem"
    fontWeight: 750
    letterSpacing: "0.06em"
  body:
    fontFamily: "Inter, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.9rem"
    fontWeight: 400
    lineHeight: 1.45
  label:
    fontFamily: "Inter, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.78rem"
    fontWeight: 800
    letterSpacing: "0.14em"
  mono:
    fontFamily: "ui-monospace, monospace"
    fontSize: "0.85rem"
    fontWeight: 400
rounded:
  xs: "4px"
  sm: "6px"
  md: "9px"
  lg: "12px"
  card: "0.35em"
  die: "0.25em"
  pill: "999px"
spacing:
  gutter: "4px"
  xs: "6px"
  sm: "8px"
  md: "12px"
  lg: "18px"
components:
  pane-heading:
    textColor: "{colors.brass-label}"
    backgroundColor: "{colors.pane-header}"
    typography: "{typography.title}"
    padding: "4px 10px"
    height: "39px"
  button-control:
    backgroundColor: "{colors.raised}"
    textColor: "#e8d9bb"
    rounded: "{rounded.xs}"
    padding: "4px 9px"
    height: "30px"
  button-action:
    backgroundColor: "#282117"
    textColor: "{colors.player-white}"
    rounded: "7px"
    padding: "8px 12px"
    height: "44px"
  button-board-target:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.cream-bright}"
    rounded: "{rounded.sm}"
    height: "38px"
  button-board-target-hover:
    backgroundColor: "{colors.pressed}"
    textColor: "{colors.cream-bright}"
  option-pressed:
    backgroundColor: "{colors.pressed}"
    textColor: "{colors.cream-bright}"
  chip-site-card:
    textColor: "{colors.cream}"
    rounded: "{rounded.pill}"
    padding: "2px 6px"
  pill-player-title:
    textColor: "{colors.token-favor}"
    rounded: "{rounded.pill}"
    padding: "0 0.45em"
  card-face:
    backgroundColor: "{colors.base}"
    textColor: "{colors.cream}"
    rounded: "{rounded.card}"
    padding: "0.4em"
    width: "13ex"
  die-face:
    backgroundColor: "{colors.base}"
    textColor: "{colors.cream}"
    rounded: "{rounded.die}"
    padding: "0 0.25em"
    height: "1.5em"
  site:
    backgroundColor: "{colors.site}"
    textColor: "{colors.cream}"
    rounded: "{rounded.md}"
    padding: "12px"
    height: "19.5rem"
  input-field:
    backgroundColor: "{colors.base}"
    textColor: "{colors.player-white}"
    rounded: "{rounded.sm}"
    padding: "8px"
    height: "44px"
---

# Design System: Oath Digital

## Overview

**Creative North Star: "The Painted World"**

The source is the physical board: an illustrated map where every region is a
place with its own light, the Cradle green, the Provinces teal-blue, the
Hinterland orange-red, with chalk-brush titles, thick white card frames, gold
favor coins, blue secret books, and bright player colors laid on top. It is
colorful and playful, and at the same time evocative of place and history.
Oath Digital's job is to put that world on a screen for players who already
know it, so every piece should be recognized before it is read.

The implementation today is that world seen on a dark table. Surfaces are warm
near-blacks and browns, text is cream, and the accent is brass. The color of
the board lives in the pieces: suit and token colors were sampled from the
owner's reference images and pulled apart by eye so the three warm suits stay
distinct; the three map regions are painted in the board's own light, Cradle
green, Provinces teal-blue, Hinterland orange-red, deep enough for cream text;
region and site names are set in Georgia like the board's titles. The panes
around the map are not painted. A later pass may move them toward the board's
cream panels; this document records the incumbent, and names the board as the
direction.

The system is dense and instrumental. Four fixed panes with 4px gutters, no
page chrome, uppercase pane labels, and no motion anywhere: state is shown by
border color, an inset ring, or an outline, and the stylesheet reserves
borders at rest so hover and focus never reflow the board. Components are
physical and legible: bordered chips, pills, face-shaped dice, cards with the
real card ratio. They are read at a glance on the board and studied on the
overlay. The system rejects generic dark SaaS (grey dark mode, blue-purple
gradients, glass), fantasy ornament (parchment textures, scrollwork,
blackletter, fake-depth drop shadows) and cartoon game-UI chrome (gloss,
bevels, glowing borders beyond the target and focus rings).

**Key Characteristics:**
- The board's pieces are the color; the table is warm dark.
- Suits are told apart by glyph shape first, color second.
- Four fixed panes, no page chrome, brand in the details.
- Flat, layered by tone; rings for target and focus, shadows only on true
  overlays.
- No animation. Nothing moves on hover, focus or selection.
- Cards keep the physical 1:1.4 ratio; relics are square.

## Colors

A warm dark table with cream ink and a brass accent, carrying the saturated
suit, token, die and player colors of the physical board.

### Primary
- **Brass** (`brass`): the one accent. Eyebrows, `h2` section titles, the
  current round segment, the modifier ordinal disc, board-target borders, the
  pile card frame, and the Add Player control. Used for emphasis and
  legality, never as a fill for large areas.
- **Bright Brass** (`brass-bright`): brass raised for small readouts: the
  Oathkeeper status, target-detail badges, the debug label, the restart
  button border.
- **Brass Label** (`brass-label`): the muted brass of pane headings, where the
  accent must be present but quiet.
- **Brass Line** (`brass-line`): the brass-tinted border on map controls, die
  faces, the card overlay body and the close button.

### Secondary
- **Selection Blue** (`selection`): the one cool accent, reserved for "this
  is the thing you picked": the selected board target's border and inset
  ring, on a **Selection Fill** (`selection-fill`) background. It is the same
  hue as the blue player color; selection and blue player never share a
  surface, so it does not collide in practice.

### Tertiary: the board's own colors
These come from the physical components. They are semantic and must not be
reused for UI meaning.

- **Favor** (`token-favor`) and **Burnt Favor** (`token-favor-burnt`): the
  gold coin and its cracked, dulled twin. Favor also colors the player title
  pill.
- **Secret** (`token-secret`) and **Burnt Secret** (`token-secret-burnt`):
  the blue book and its torn twin. Secret also marks the knowable pip on a
  face-down card.
- **Suits**: Arcane (`suit-arcane`) violet, Beast (`suit-beast`) brick,
  Discord (`suit-discord`) pure red, Hearth (`suit-hearth`) orange, Nomad
  (`suit-nomad`) green, Order (`suit-order`) navy. Discord and Order also fill
  the attack and defense plan-side badges, and the both-sides badge is a
  left-to-right gradient of the two.
- **Dice**: Attack (`die-attack`) red, Defense (`die-defense`) blue, Round
  (`die-round`) purple. Die-face symbols (skull, shield, sword, hollow sword)
  are drawn in **Token Symbol** (`token-symbol`).
- **Players**: purple, blue, red, yellow, white, black, pink, brown
  (`player-*`), plus bandit forces (`force-bandit`). White is pure white and
  underlined so it stays distinct from cream text. Black is ink on a
  **Black Pill** (`player-black-pill`) so it stays readable on the dark table.

### Neutral
- **Table** (`table`): the gutter behind the four panes; the darkest surface.
- **Base** (`base`): the page background and the face of every card, die,
  input and restart button. The floor that pieces sit on.
- **Pane** (`pane`) and **Pane Header** (`pane-header`): the four panes and
  their header strips, one step up from the table.
- **Panel** (`panel`): status boxes and board-target controls.
- **Site** (`site`): a site on the world map, the most raised board surface.
- **Raised** (`raised`) and **Pressed** (`pressed`): small controls, and the
  fill an option takes when hovered or pressed.
- **Region tints**: Cradle (`region-cradle`) deep green, Provinces
  (`region-provinces`) deep teal-blue, Hinterland (`region-hinterland`) deep
  orange-red, in board order, each framed by a line cut from its own hue
  (`region-*-line`). These are the one place a UI surface carries the board's
  color; site boxes on them stay in the neutral ramp so cards read.
- **Lines**: `line` for sites, cards, zones and dividers; `line-mid` for
  decision options, empty slots and inputs; `line-dim` for regions, player
  boards and action groups; `line-pane` for pane headers.
- **Cream** (`cream`): body text. **Bright Cream** (`cream-bright`): emphasized
  text and the round-tracker numerals. **Cream Focus** (`cream-focus`): the
  focus and card-hover outline.
- **Parchment Muted** (`parchment-muted`), **Parchment Dim** (`parchment-dim`),
  **Ink Dim** (`ink-dim`), **Ink Separator** (`ink-separator`): descending
  text emphasis, from site details, to the lede and event log, to helpers and
  restrictions, to the drawn "·" between identity fields.
- **States**: `facedown` and `facedown-letter` for a card back; `pile-back`
  for a stacked pile; `unimplemented` for a card the engine does not run yet;
  `error-line` and `error-text`; `replay` green; `limiter` coral; `dev-panel`
  and `dev-line` for the developer aside, deliberately off-palette purple so
  it never reads as game.

### Named Rules
**The Pieces Carry the Color Rule.** Saturated color belongs to suits,
tokens, dice and players. UI surfaces stay in the warm neutral ramp; the only
UI accents are brass and selection blue. The three map regions are the one
exception, and they carry the board's colors, not new ones.

**The Shape First Rule.** Every suit, token and die has its own glyph. Color
only keeps them from blurring; it never carries meaning alone.

**The One Cool Rule.** Selection blue is the only cool UI color, and it means
"selected". Do not use it for links, information or decoration.

## Typography

**Display Font:** Georgia (with serif fallback)
**Body Font:** Inter (with ui-sans-serif, system-ui, sans-serif fallback)
**Label/Mono Font:** ui-monospace for the event log

**Character:** A serif for names of places and things on the board, a plain
sans for the instrument around them. Georgia stands in for the board's
hand-brushed titles; Inter never tries to be decorative.

### Hierarchy
- **Display** (400, 2.5rem, Georgia): the page `h1` on the host page.
  `clamp(1.8rem, 9vw, 2.5rem)` under 760px.
- **Headline** (800, 1.08rem, Georgia): site names on the map, centered in
  the site heading. Region titles on the map are Georgia 1.6rem, 800, mixed
  case, Bright Cream: the brightest text on the map, like the board's brushed
  lettering. The card overlay name is Georgia 1.5em, 800, Bright Cream. On
  the zoomed-out map both are set against the scale (see The Glance Layer
  Rule): a site name holds 12px on screen, a region title 12.5px.
- **Title** (750, 0.8rem, uppercase, 0.06em, Inter): pane headings in Brass
  Label. Under 620px: 0.72rem, 0.025em.
- **Body** (400, 0.9rem in the action pane, 0.86rem/1.45 for site details,
  0.82rem on player boards, Inter): the reading size follows the pane. Bold
  runs at 800 for counts, forces, requirements and player references.
- **Label** (800, 0.78rem, uppercase, 0.14em, Inter): the eyebrow in Brass;
  region labels on the host page use 0.1em tracking in Ink Dim.
- **Mono** (400, 0.85rem, ui-monospace): the raw event log only.

### Named Rules
**The Font-Size Handle Rule.** Cards are sized in `ex`, never in px per
context. To make cards smaller on a player board or larger on the overlay,
change the container's font size and let the card follow.

**The Serif Names Things Rule.** Georgia is for names of sites, regions and
cards and the page title. It is never used for controls, labels or body text.

**The Eleven-Pixel Floor.** Functional text is never under 11px on screen. In
the panes this is a floor on the text, not the box: a card name is
`max(0.95em, 0.7rem)` and a restriction `max(0.72em, 0.7rem)`, so a card
scaled down by its pane's font size keeps its box in `ex` and its text
readable. The smallest steps in use are `.84em` for a partition option and
`.85em` for its order note, both chosen to clear the floor at the pane's
0.9rem.

**The Glance Layer Rule.** The map is a fixed 1500px surface scaled by the
viewport, so its text shrinks with it. Below a scale of 0.72, where the map
card name (0.95em of 16px) falls under the floor, `.map-content` carries
`map-compact` and `--map-scale`, and the glance layer is set against the
scale instead: `font-size: clamp(rest, calc(<screen px> / var(--map-scale)),
cap)` holds region and site names, pawns, tokens, defense, forces and card
names near one screen size while the board keeps shrinking under them, the
way a map's place names hold still while the map zooms. The study layer
(site powers and requirement, a card's defense, restriction and badge) is
hidden; a zoom or the overlay carries it. Nothing in the compact mode may
change the map's height, because the Fit scale is computed from it: the site
box is fixed and clips, and the region title has a fixed 2.4rem line. The
two glance sizes are named once on `.map-compact` (`--glance-name`,
`--glance-text`), and the compact site's card row is sized from what they
leave: `--card-w` is the least of the row's own ceiling, a third of the
width, and the box's height less the name line, the pawn row and, on a site
holding forces, the forces line, divided by the card ratio. Above a scale of
about 0.35 the ceiling wins and nothing changes; below it the cards, which
show initials there anyway, give up height so the forces line is never the
thing the box clips.

## Layout

The play surface is one fixed viewport, no page scroll: `html, body` are
`overflow: hidden`, and `#app` fills `100dvh`. Inside it, `.game-table` is a
CSS grid with 4px gaps on a Table background, four panes:

- Wide (default): columns `minmax(0, 2.4fr) minmax(300px, 1fr)`; rows
  `minmax(150px, 26%) minmax(88px, 16%) minmax(0, 1fr)`; areas Players across
  the top, World left spanning two rows, Log then Actions stacked right.
- Narrow or squarer than 4:3, or under 1050px: columns `1.65fr / minmax(260px,
  1fr)`, Actions above Log on the right.
- Portrait-ish (under 4:5 or 620px): World spans the full width in the middle
  row; Actions and Log share the bottom row side by side. Pane padding drops
  to 8px and headings shrink.
- Under 500px tall in landscape: rows compress to `24% / 1fr / 20%`.

Each pane is a `section` with a 39px header strip and a scrolling
`.pane-content` (12px padding, thin scrollbar in Brass Line on Pane). The
World pane is the exception: zero padding, a Base field, a hidden
scrollbar, and a `.map-content` surface fixed at 1500px wide with 20px padding
that is transform-scaled by the viewport. The map's font size is pinned to
1rem so zoom and font scaling never compound. Fit is the default and is
height-bound at the supported sizes (0.36 at 1024×768, 0.43 at 1440×900);
below 0.72 the map goes compact and the glance layer is counter-scaled (The
Glance Layer Rule). In compact mode the site re-grids: the name takes the
full width on the first row, the token corner, the pawns and the defense
corner share the second, and the details fill the rest; a site holding forces
narrows its cards to 7.3rem so the forces line fits the fixed box.

On the map, three regions sit in a 3-column grid with 12px gaps; each region
lists its sites in one column with 9px gaps. Every site is the same height
(`--site-h: 19.5rem`) whatever it holds, so a pawn arriving or a preview
opening never shifts the board. The card row inside a site is three cards
wide: `--card-w: min(8.6rem, (100% - 2 gaps) / 3)`.

The Players pane is a horizontal strip of player boards, each `flex: 1 0
300px`, divided by 1px Line-mid right borders. A board is a two-column grid:
identity, resources and the title or Vision row on the left (8.5–10rem), the
card row on the right, spanning the rows. The card row sets the board's
height and scrolls sideways inside the board when it holds more than fits;
the strip scrolls sideways past four boards at 1024px. Nothing in the strip
scrolls vertically. The Actions pane stacks
decision zones in one column with 10px gaps. Spacing runs 4, 6, 8, 12, 18px;
`min-height: 44px` is the touch floor for every action button.

The trusted-host page (game creation) is the one page-scrolling surface: a
1280px container with 28px/20px/48px padding, forms in a 40rem grid.

## Elevation & Depth

Flat, layered by tone. Depth is read from the surface ramp (Table, Base,
Pane, Panel, Site, Raised, Pressed) and 1px borders, like paper laid on a
table. There are no resting shadows on any board piece. Rings carry state: a
brass inset ring for a legal board target, a soft brass halo on hover, a
2px selection-blue inset ring when picked, a Cream Focus outline (2px, offset
-3px) for keyboard focus inside the table, and a 3px white outline on the
host page.

### Shadow Vocabulary
- **Target halo** (`box-shadow: 0 0 0 3px rgba(210, 168, 94, 0.35)`): a
  hovered or focused legal board target. It is a ring, not a lift.
- **Overlay lift** (`box-shadow: 0 12px 45px #000b`): the developer panel
  only, which floats above the table as a fixed aside.
- **Overlay scrim** (`background: #0b0a08e8`): the card inspection overlay
  darkens the table instead of casting a shadow.

### Named Rules
**The Flat Board Rule.** Board pieces never cast shadows. If something must
read as above the table, it is a fixed overlay with a scrim or a lift, and
there are two of those.

**The No-Reflow Rule.** Every hover, focus and selection treatment fits in
space reserved at rest: transparent borders, inset rings, negative-offset
outlines. Nothing grows.

## Shapes

Gently rounded, with the radius following the size of the piece. Large
surfaces round more: status and panel boxes at 12px, sites, regions, zones and
player boards at 9px, options and cards at 8px, controls at 6px or 7px, the
tightest controls at 4px. Cards use `0.35em` and dice `0.25em` so their
corners scale with their font-size handle. Countable things are pills: site
card counts, target-detail badges, the player title, the vision slot, the
black player and black forces, and the plan-side badge at `1em`. The
modifier ordinal is a full circle, as is the color swatch and the knowable
pip.

Borders are 1px solid in the Line ramp. Dashed means "not real yet": a blank
die face, an empty card slot, the debug toolbar, the active player board's
outline. A 2px border marks a piece that must read as an object at small
size: the pile card, the Add Player control, the color swatch. A 3px
`currentColor` border is the victory banner, the one loud frame in the
system.

Cards keep the physical proportions: denizens and edifices 1:1.4, relics 1:1.
Nothing on the board is clipped except by its own `overflow: hidden` box.

## Components

### Buttons
Solid, bordered, no gloss. Every action button is at least 44px tall; compact
controls are 30px.
- **Shape:** 4px on map controls and the dev toggle; 6px on board-target
  controls; 7px on load-game, adviser and restart.
- **Control** (map zoom, Fit, dev tools, overlay close): Raised fill, `#e8d9bb`
  text, Brass Line border, `4px 9px` padding, 0.78rem. Hover: Pressed fill,
  Bright Cream border, the Action face's own answer. The two player pills
  (title, Vision) take the Raised fill on hover.
- **Action** (load game, adviser, and every act, decision, confirm and
  modifier button in the Actions pane): `#282117` fill, white text, `#bca780`
  border, `8px 12px` padding. Hover: Pressed fill, Bright Cream border. The
  small movers and steppers beside an option (card pickers, Put on top,
  distribute `−`/`+`, modifier Earlier/Later) wear the Control face at their
  own sizes. Decision buttons keep their 5px margins. The partition confirm
  sits in a footer strip pinned to the pane's bottom edge (Pane fill,
  Line-pane top border, 12px padding), full width, so the answer stays in
  view while the zones scroll.
- **Board target control**: full width of the site, Panel fill, Bright Cream
  800 text, brass border. Hover and focus: Pressed fill and a 3px Bright
  Cream outline offset 2px. Pressed state prefixes "✓ ".
- **Pressed option** (`aria-pressed="true"` on many-options and card choices):
  Pressed fill, Bright Cream border and text, weight 800, "✓ " prefix.
- **Focus:** 2px Cream Focus outline, -3px offset, inside the table; 3px white
  outline, 3px offset, on the host page.
- **Disabled:** opacity 0.5, `cursor: not-allowed` or default.
- **Partition surface:** a keep-one partition (Search, the starting adviser)
  reads "Keep one; discard the rest." with no minimum lines; the kept card
  has no mover, because Keep on another card swaps them; each discard card
  that is not last carries one Control-face "Put on top" button, and the
  last carries a "Top" pill of the same height (Line-mid border, Ink Dim
  text, 999px radius). The order note reads "The last one lands on top of
  the pile." Forge's payment partition keeps its minimum lines and movers
  and has no order controls.

### Chips and Pills
- **Site card count**: pill, 1px Line border, Cream 800 text, `2px 6px`
  padding, 1.65rem minimum width.
- **Target detail badge**: pill, `#a98b58` border, Base fill, Bright Brass
  0.72rem 800 text, absolutely placed top-right of a site.
- **Player title**: pill in Favor color, Favor border, 0.78rem 600, no fill,
  clickable.
- **Vision slot**: pill, `currentColor` border, 0.85em, no fill.
- **Plan side**: 1em pill, `0 0.6em` padding, 0.75em; Discord fill for attack,
  Order fill for defense, a Discord-to-Order gradient for both; white text.
- **Modifier ordinal**: 1.8rem brass disc, Base text, 800.
- **Unimplemented badge**: 0.25em radius, Unimplemented fill, Cream 0.62em
  uppercase, bottom-left of the card.

### Cards
The signature component. A card is a bordered box in Base with the physical
ratio, sized in `ex` from `--card-w: 13ex`.
- **Corner:** 0.35em.
- **Border:** 1px, transparent at rest so states never reflow; Line for a
  denizen, `#7a6a4c` for a relic, dashed Unimplemented for a card the engine
  does not run, dashed Line-mid with transparent fill for an empty slot.
- **Face:** header row of suit glyph (1.2em) and name (750, 0.95em) with
  defense dice pushed right; a name that does not fit beside the glyph drops
  under it whole rather than breaking mid-word; token row at 0.8em;
  restriction in Ink Dim capitalized at the bottom; footer sinks to the
  bottom.
- **Compact (zoomed-out map only):** the box and ratio are unchanged; the
  suit glyph moves to the top-left corner and the name sits centered in the
  box. The face inherits the counter-scaled size from the site, so the name
  holds 11px on screen. A name whose longest word does not fit one line at
  the current scale shows its initials instead, at 1.5em 800: the first
  character of each word as written ("Rotting Fortress" is RF, "Master of
  Disguise" is MoD), or a lone word's first two ("Quartermaster" is Qu).
  Both forms are in the DOM; the card carries `name-fits-N` for each of the
  buckets 6, 8, 10 and 12 its longest word clears, the map carries the one
  `map-fit-N` its scale allows (`GameTableShell.nameFit`), and the
  stylesheet picks. The whole name stays in the button's label and title.
  Defense, restriction and the unimplemented badge are hidden; tokens stay.
- **Face-down:** Facedown fill, centered Facedown Letter at 1.9em 800; a
  knowable card adds a Secret pip top-right and reveals a summary on hover or
  focus (pointer devices only, CSS only, no client state).
- **Hover / Focus:** 2px Cream Focus outline, -3px offset. No lift, no move.
- **Overlay:** the same card at `font-size: 1.9em` beside a details column
  (Georgia name, `dt`/`dd` property grid), inside a Brass Line box on
  `#1b1811` over the scrim.

### Dice
A rolled die is a face-shaped chip: 1.9em minimum width, 1.5em tall, 1px
Brass Line border, 0.25em radius, Base fill, symbols in Token Symbol at 0.9em
700. A blank face is a dashed `#5d5144` chip with nothing on it. Faces sit in
an inline wrap with 0.25em gaps; a walk's roll shows the faces, then one line
of totals.

### Sites
A site is the board's unit: Site fill, Line border, 9px radius, 12px padding,
fixed 19.5rem height, `overflow: hidden`. The heading is a three-column grid
so the name is centered on the box regardless of token counts; tokens sit
left, defense right. Below the compact threshold the name owns the first row
and the two corners flank the pawns on the second (Layout). Powers are one
ellipsized line with hover titles, hidden in compact mode. A legal
target gets a brass border and inset ring; hover brightens the border to
Bright Cream with the target halo; selected turns Selection blue on Selection
Fill.

### Panes
Four `section`s with a header strip (Pane Header fill, Line-pane bottom
border, 39px, Brass Label uppercase title) and a scrolling content area. The
Log pane's content carries a 12px diagonal stripe (`#211f1b` / `#24211c`) as
a texture while empty; the placeholder is Ink Dim 0.82rem, as is every
pane's empty or loading line, and a waiting or seat notice in the Actions
pane. On the map the empty line is set against the scale
(`0.82rem / --map-scale`) and centered, so it reads at one size whatever
Fit came to. The pane header is each pane's visible title; the `h2`s the
renderers write inside a pane ("The World", "Available actions", a decision's
heading) are kept for the document outline and taken off the screen with
the clip pattern, never `display: none`.

Text selection is brass on Base (`::selection`), so the one browser surface
a player touches in every pane carries the palette.

### Inputs / Fields
- **Style:** Base fill, white text, Line-mid border, 6px radius, 8px padding,
  44px minimum height, `font: inherit`. Host-page inputs use browser defaults
  with `font: inherit` and 0.5rem padding.
- **Focus:** browser default on the host page; the table's focus outline
  inside the table.
- **Error:** `.error` sets Error Line border and Error Text.
- **Number inputs** in negotiation are 5em wide; distribute steppers are
  `−` / `+` buttons of 2.2em around a tabular-nums count.

### Navigation
There is no navigation bar. The Players pane header carries the Dev tools
toggle; the World pane header carries the zoom controls (−, percentage
label, +, Fit). The developer panel is a fixed aside, top-right, 540px wide,
in the off-palette purple with the overlay lift.

### Player Boards
One board per player in the strip: 225px minimum, `6px 12px` padding,
0.82rem, a Line-mid right border. The active board gets a dashed `#c8b48b`
outline and a `#29251d` fill. The identity line joins name, role and title
with drawn "·" separators in Ink Separator. Cards on a board render at 0.86em
in a sideways-scrolling row beside the identity column (Layout).

## Do's and Don'ts

### Do:
- **Do** keep saturated color on the pieces: suits, tokens, dice, players,
  plan sides. Keep UI surfaces in the warm neutral ramp with brass as the
  accent; the three painted map regions are the only colored surfaces.
- **Do** give every new token or suit its own glyph in `TokenSprite` before
  giving it a color.
- **Do** size cards through the container's font size (`ex`-based
  `--card-w`), never with a per-context pixel width.
- **Do** reserve space for every state at rest: transparent borders, inset
  rings, negative-offset outlines.
- **Do** keep every site the same fixed height and every action button at
  least 44px tall.
- **Do** use Georgia for names of sites, regions, cards and the page title,
  and Inter for everything else.
- **Do** use a pill for a count or a state of a player, and a dashed border
  for something not yet real.

### Don't:
- **Don't** add `transition` or `animation`. State changes by color, ring and
  outline only.
- **Don't** put a resting `box-shadow` on a board piece; only the developer
  panel lifts and only the card overlay scrims.
- **Don't** introduce grey or blue-tinted neutrals, blue-purple gradients,
  glassmorphism or translucent panels.
- **Don't** add parchment textures, scrollwork, blackletter, bevels, gloss
  or glowing borders beyond the target halo and focus outline.
- **Don't** use selection blue for anything except "selected".
- **Don't** reuse a suit, token, die or player color for UI meaning.
- **Don't** let a hover, focus or preview change the size of a card, site
  or player board.
