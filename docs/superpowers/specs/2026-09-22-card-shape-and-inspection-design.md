# Card Shape and Inspection

> Status: implemented 2026-09-22. Plan: [2026-09-22-card-shape-and-inspection.md](../plans/2026-09-22-card-shape-and-inspection.md). Verification record: [card-shape-verification.md](../../testing/card-shape-verification.md). The deferred items at the end each still need their own design.

## Goal and scope

Cards stop changing size. Every denizen occupies the same box as every other denizen, face-up or face-down, and the same holds for relics. A card's face carries a permanent summary instead of a name that expands into a tooltip on hover. Full rules move to a full-screen overlay opened by clicking any card.

In scope:

- Fixed, type-uniform card boxes for denizens, edifices, visions and relics, sized in font-relative units.
- A permanent summary on the face of every face-up card, replacing the hover popover.
- A letter treatment for face-down cards, with a marker on the backs this viewer is entitled to know.
- A full-screen inspection overlay, opened by click from any card anywhere in the UI.
- Rendering the catalog's `[token]` markup as inline-SVG glyphs inside that overlay, and the suit colour palette they need.
- Removing `tabindex="0"` from `.decision-option`.

Out of scope, recorded as follow-ups at the end: suit and restriction icons on the card face, restructuring player areas and sites beyond what fixed boxes force, porting the map to the `haunt-roll-fail` canvas approach, and the polling change-check (already recorded in [phase-5-follow-ups.md](../../operations/phase-5-follow-ups.md)).

## How the rulings were gathered

The design was grilled over four rounds. Every decision below is the product owner's answer, a fact read from this repository, or a fact read from `haunt-roll-fail`, which the product owner named as the reference implementation for click behaviour. Two of the owner's opening assumptions were corrected against the code and are recorded as such, because the corrections shaped the design.

## Current state

**The hover reflow has two causes, and the larger one is not size mismatch.** [`styles.css:431`](../../../frontend/styles.css) overrides the popover inside the game table:

```css
.game-table .card-popover { position: static; min-width: 0; max-width: 280px; }
```

In flow, revealing the popover grows the button that contains it and reflows the whole pane. The override exists because `.game-pane` is `overflow: hidden` and `.pane-content` is `overflow: auto`, so an absolutely positioned popover is clipped. The second, smaller cause is genuine size mismatch: `.empty-denizen-slot` and `.facedown-relic` are `2rem` squares, `.site-card` is a pill whose width follows its text, `.card-detail` is a `36px`-min button, and `.peeked-relic` stacks a back and a reveal in one grid cell so the cell resizes when the reveal appears.

**Cards are no longer selection targets.** `BoardTargetRefProjection` has exactly one case, `Site(siteId)` ([`ActionProjectionDtos.scala:12`](../../../shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala)). Earlier work removed card-level targeting. `cardDetailsPopover` has nine call sites and none of them attach a selection handler. In decision panels, selection is by drag, by the `move-option` buttons, or by the `±` steppers — never by clicking the card. The card click is unclaimed everywhere.

**Hidden cards are redacted server-side, completely.** `hiddenCard(kind)` ships `name = "Facedown <kind>"`, `hidden = true`, and nothing else — no id, no suit, no rules ([`GamePresentationProjector.scala:226`](../../../src/main/scala/oathdigital/application/GamePresentationProjector.scala)).

Entitlement is decided by a single rule, `identifiesCard`, which combines face-up status, ownership, pawn presence for site relics, and **recorded per-viewer knowledge** — `knowledge.advisers`, `knowledge.heldRelics` and `knowledge.siteRelics`. That knowledge grows during play: a player who discloses an adviser or relic during a negotiation makes it identifiable to whoever learned it. The set of face-down cards a given viewer may identify is therefore dynamic, includes cards on *other* players' boards, and cannot be enumerated in the client.

**Rules text is already tokenized, and is currently printed raw.** Catalog `powers[].rulesText` contains 17 icon tokens and Markdown emphasis, for example `[secret] [secret-burnt] **ACTION:** Gain [favor] [favor] [favor] [favor] from any favor bank or banks.` The UI prints the brackets and asterisks verbatim.

**Measured text budget**, across 255 denizens and 48 relics, counting each `[token]` as one glyph and stripping emphasis markers:

| | median | p90 | max |
| --- | --- | --- | --- |
| Denizen, raw | 96 | 154 | 229 |
| Denizen, tokens as glyphs | 80 | 134 | 212 |
| Relic, raw | 100 | 151 | 314 |
| Relic, tokens as glyphs | 81 | 128 | 262 |

Three denizens and three relics have more than one power. Names reach 20 characters for denizens and 18 for relics.

**Space budget.** `.map-content` is a fixed `1500px` canvas scaled by `transform`, laid out as three regions across, so a site row is roughly 460px. Site capacity is at most three denizens. Player board columns are `min-width: 225px`. The panel verification record measured working map scales of 0.4524, 0.5655 and 0.71.

Taken together: a summary fits a small card, full rules do not. The design keeps the face for the summary and moves rules to the overlay.

## What was taken from haunt-roll-fail

Four things, each verified in its source:

**Cards are sized in `ex`, per type, not per context.** `arcs/styles.scala:78-82` defines `card` at `14.88ex × 20.78ex` (1 : 1.396), `courtCard` at `20.00ex × 27.93ex` — the identical ratio at 1.34× scale — and `leaderCard` at `14.88ex × 25.50ex`, sharing `card`'s width so the two pack into the same column. Responsiveness comes from the layout engine binary-searching a font size per pane; every `ex`-sized card and token rescales together with ratios intact.

**Nothing changes footprint when its state changes.** `card0` and `cardX` carry `border-width: 0.3vmin` with `border-color: transparent` at rest; `selected` only swaps in a colour and adds an `outline`, which is out of flow. Selection never reflows anything.

**Click is the only gesture.** No hover, no long-press, no modifier key, no right-click, no double-click. `updateHighlight` is a no-op in Arcs. Inspect-versus-select is disambiguated by which action population produced the card, not by how it was clicked: `OnClickInfo` actions zoom, `Choice` actions act, and the two render into different regions of the action pane (`grey.scala:729-746`). Court cards on the canvas always zoom and can never be taken by clicking the board.

**The overlay is a full-window pane at `z-index: 300`**, its content scaled with `max-width: 100%; max-height: 100%`, dismissed by a click anywhere on it, and cleared whenever any real action is taken.

What was **not** taken: Arcs renders each card as a single flat image, so it never faces our text-fitting problem; and its selected-card second-click-to-zoom dance exists only because cards are its selection control, which ours are not.

## Shapes and sizing

Three boxes, all expressed in `ex` so they inherit scale from their container's font size:

| Box | Ratio | Applies to |
| --- | --- | --- |
| Denizen | 1 : 1.4 | Denizens, edifices, visions |
| Relic | 1 : 1 | Relics |

The inspection overlay reuses the ratio of whichever type it is showing, at a larger scale — the pattern `haunt-roll-fail` uses for `courtCard`, which is `card`'s exact ratio at 1.34×. There is no third ratio.

Edifices share the denizen box because they occupy denizen slots; a differently shaped edifice would break the slot grid the moment one is built. Visions are denizen-shaped rather than given a fourth ratio, because they occupy one slot on the player board and a distinct ratio would be inventory carried for a single case.

Sizes are declared once as custom properties and never overridden per pane:

```css
:root { --card-w: 13ex; --card-h: calc(var(--card-w) * 1.4); }
```

Per-context sizing comes from font size, which the panes already set — `.game-table .player-board` at `.82rem`, `.pane-actions .pane-content` at `.9rem`. The map's font size must be **pinned explicitly** on `.map-content` rather than inherited, because the map is additionally scaled by `transform` and an inherited change would compound with it invisibly.

"Same size" means same size among peers in one container: the three denizens at a site, the advisers on one board. It does not mean the same rendered size across panes, which would force either an illegible map or an overflowing player board.

The starting value of `13ex` must satisfy, and is verified against, two constraints: three denizen boxes plus gaps fit a 460px site row at the map's pinned font size, and two denizen boxes plus gaps fit a 225px player board column at `.82rem`. Player board card rows wrap, and `.pane-content` already scrolls.

## The card face

A face-up card shows its summary permanently. There is no hover behaviour on a face-up card.

Fields, in priority order, dropped from the bottom as the box shrinks:

1. **Name** — most prominent, the primary scan target.
2. **Live tokens** — the `favor` and `secrets` actually sitting on this card. These outrank suit because they are the only fields that change during play, and a board that looks stale is worse than one with an unlabelled suit.
3. **Suit** — as a subtitle beneath the name, denizens and edifices only.
4. **Relic value and defense** — relics only.
5. **Restriction** — one of `site-only`, `adviser-only`, `locked`.
6. **Unimplemented marker.**

Field names are removed. `Suit: order` becomes `order`; `Favor: 2` becomes the number beside a favor mark. Rules text does not appear on the face at any size.

This is a deliberate trade. Today's hover shows a card's full rules without a click; after this change, planning a turn requires opening cards one at a time. The trade buys a board that never moves under the pointer, and the overlay is one click away from any card.

## Face-down cards

A face-down card occupies the identical box as its face-up counterpart and shows a single centred letter: **D** for denizen or edifice, **V** for vision, **R** for relic.

A face-down card the viewer is entitled to identify carries a corner pip marking it as knowable. Entitlement is neither a fixed category nor the client's to compute: the server settles it in `identifiesCard`, and the pip follows from whether the projection carried real `CardDetails` or a `hiddenCard` placeholder.

Because recorded knowledge grows during play, a pip can appear on a card that previously had none — a negotiation disclosing another player's face-down adviser is the ordinary case, and the card is on someone else's board. The renderer must treat knowability as projection state that changes between polls, not as a property fixed when the card was dealt.

On devices that support hover, hovering a knowable face-down card reveals its summary inside the same box, with no size change. This is a pure CSS rule under `@media (hover: hover)` and holds no client state.

On touch devices there is no reveal step: a tap opens the overlay, which shows the full card and is therefore strictly more informative than the summary it skips. This asymmetry is deliberate. `PanelContent.replace` destroys and rebuilds the panel DOM on every projection update, restoring only scroll position and focus, so a persistent "revealed" flag would have to become reconciled client state alongside the decision drafts. That cost is not worth a saved tap, and the pip still tells a touch user which backs are worth tapping.

This is the one place the design leaves `haunt-roll-fail` behind, which has no hover anywhere. The divergence is justified because our overlay is composed text rather than a pre-rendered image, making a cheap peek genuinely useful on a pointer device.

## The inspection overlay

Clicking any card anywhere — map, player board, decision panel, shared bank — opens a full-screen overlay showing that card in full.

**Contents.** The card at the overlay-card size, its full rules text with every `[token]` rendered as a glyph and Markdown emphasis applied, all its metadata with field names restored, and the unimplemented marker where it applies. Multi-power cards render each power as a separate block; six cards in the catalog need this. A card the viewer is not entitled to identify renders as "face-down" rather than as a card.

**Dismissal.** A click anywhere on the overlay, the `Escape` key, or a visible Close button. Focus returns to the card that opened it. The repository already implements exactly this pattern for the development panel in `GameTableShell` — Escape handler, Close button, focus return to the opening control — and this overlay follows it.

**Staleness.** The overlay ignores projection changes while open. Its subject is what a card does, which does not change when the card moves. Closing it on every poll tick would yank the card out from under a reader mid-sentence, which is the same class of interruption this whole design exists to remove.

**Placement.** The overlay renders at document level, above `.game-pane`. This is what makes `overflow: hidden` on the pane stop being a constraint, and it is why the popover's `position: static` override can be deleted outright rather than repositioned.

## The no-reflow invariant

**Every state a card can enter occupies identical space at rest.** Borders that appear on selection, focus or hover are reserved as transparent at rest; emphasis that cannot be reserved uses `outline`, which is out of flow. This is the rule `haunt-roll-fail` follows and the one whose absence produced the original complaint.

The popover's `position: static` override is deleted. The `.peeked-relic` grid overlay is replaced by the shared face-down box, so its cell can no longer resize.

## Decision panels

Cards in decision panels are clickable to inspect, exactly as elsewhere. Nothing in the repository selects by clicking a card, so there is no contention.

Two consequences:

**Drag.** `partitionOption` builds `<article class="decision-option" tabindex="0" draggable="true">` with the card inside it, so press-and-drag must drag while press-and-release must open the overlay. A movement threshold disambiguates them. `MapViewport` already suppresses click after drag at capture phase, and the map's card clicks inherit that suppression; the decision panel needs its own equivalent.

**Focus.** `tabindex="0"` is removed from `.decision-option`. It is a focus stop with no keyboard action — the article has no `keydown` handler, and the keyboard path for moving an option is the `move-option` buttons inside it, as the comment at that site already states. Today it produces a duplicate tab stop and a doubled screen-reader announcement; once the inner card button opens a modal, it puts a dead focus stop directly in front of a modal trigger. No test depends on it.

## Map degradation

`MapViewport` already reports scale to a callback on every change. Below a scale of **0.6**, map cards drop to name only; at and above it they show the full summary.

The threshold follows the measured scales: fit-to-screen lands at 0.45 to 0.57, and reading is comfortable at 0.71. Below 0.6 the token and suit lines are noise, while a name is still legible and is what a player scans for. A degraded face-up card must never adopt the face-down letter treatment.

## Icon glyphs

This slice renders `[token]` markup as glyphs **in the overlay only**, as **inline SVG** — one `<symbol>` sprite with seventeen paths, referenced by `<use>`. Glyphs are sized in `em` so they scale with the surrounding text, and coloured from custom properties.

SVG rather than Unicode, for three reasons. Colour emoji ignore CSS `color` entirely, so any emoji-presentation glyph defeats the palette below. Non-emoji Unicode can be coloured but has no character resembling a cracked coin or a torn book, which is exactly what the two burnt tokens need. And a mixed approach costs more than an all-SVG one, because it requires both pipelines to be built and then kept in step on sizing, baseline alignment and accessible naming.

Each glyph carries an accessible name; a glyph alone is not readable by assistive technology.

### Shapes

Shapes follow the physical components. `[favor]` is a coin, `[favor-burnt]` the same coin cracked. `[secret]` is a closed book, `[secret-burnt]` the same book torn. The suit tokens reuse the favor-bank emblems: a horned face for Discord, a star-marked triangle for Arcane, a pipped square for Order, a house for Hearth, a fox for Beast, a calm face for Nomad.

### Palette

Sampled from the reference images supplied by the product owner, not chosen by eye:

| Token | Colour | |
| --- | --- | --- |
| `[favor]` | `#e4ba5a` | gold |
| `[favor-burnt]` | `#a28a5a` | the same gold, darkened and desaturated |
| `[secret]` | `#66a8d8` | blue |
| `[secret-burnt]` | `#60849c` | the same blue, darkened and desaturated |

Suit colours, taken from the **emblem inside the small suit circle** — not from the favor-bank ring, which is gold for both Arcane and Hearth and teal-or-blue for both Order and Beast, and so does not identify a suit at all:

| Suit | Colour | |
| --- | --- | --- |
| Arcane | `#6e377d` | purple |
| Beast | `#8c371e` | brown |
| Discord | `#c84623` | red |
| Hearth | `#d24b23` | orange |
| Nomad | `#4b9678` | green |
| Order | `#14417d` | blue |

These are new to the codebase — `styles.css` defines no suit colours today. They are declared once as custom properties and used both by the suit glyphs and by the suit subtitle on the card face.

Sampled from two independent reference images that agree to within a few units, so the relationships are reliable even if the absolute values are not canonical. If published component art carries exact values, prefer those.

**One hazard to design around.** Discord `#c84623` and Hearth `#d24b23` are about one degree apart in hue, and Beast `#8c371e` is the same hue family at lower lightness. Both source images agree on this, so it is a property of the art rather than a sampling artefact: three of the six suits are warm red-orange and only lightness separates one of them.

Colour therefore cannot be what identifies a suit. The emblem shapes must carry it — horned face, fox, house — and must stay distinguishable at text size and at the smallest card size. Verify the Discord/Hearth pair and the Discord/Beast pair explicitly during implementation. Where a suit is named in text rather than shown as a glyph, the name is the identifier and colour is decoration only.

Frequency, which should guide how much care each glyph gets: `[favor]` is 32.2% of all 512 token occurrences in the catalog and `[secret]` 26.2%. With their burnt variants, those four are 67.8% of everything rendered. The six suit tokens are 15.7%, and the remaining seven share 16.5%.

### Markup

Emphasis markers `**bold**` and `_italic_` are rendered rather than printed. `CatalogModel.rulesText` joins multiple powers with a blank line before the projection sees them, so the renderer splits on that boundary to produce per-power blocks.

Icons on the card *face* remain deferred; this section covers the overlay only.

## Testing

The frontend suite runs under jsdom, which has no `ResizeObserver`; `TestBrowser` stubs it. New tests:

- Face-up and face-down cards of the same type produce identical box dimensions.
- A face-down card the viewer cannot identify exposes no name, suit or rules in the DOM.
- The knowable-back pip appears only where the projection carried real card details, and appears on a previously unmarked card on another player's board when a later projection carries details for it.
- Clicking a card opens the overlay; Escape, the Close button and a click on the overlay each dismiss it; focus returns to the originating card.
- A projection update while the overlay is open leaves it open and unchanged.
- Token markup renders as SVG glyphs with accessible names, each carrying its palette colour, and multi-power cards render one block per power.
- `[suit-discord]`, `[suit-hearth]` and `[suit-beast]` render as mutually distinguishable glyphs at text size and at the smallest card size, given that their colours are near-identical.
- `.decision-option` is not focusable, and the `move-option` buttons remain the keyboard path.
- Below 0.6 map scale the face shows the name only; at 0.6 and above it shows the summary.

Browser verification follows the pattern of the existing panel UI record: an isolated development server and its own database, checked at 1440×900, 1024×768, 768×1024 and 390×844, confirming that no card changes footprint on hover, focus or selection at any of them.

## Follow-ups

- **Suit and restriction icons on the card face.** Deferred until the boxes are settled and the face's real budget is measured.
- **Player area and site restructuring.** This slice restructures only what fixed boxes force. A fuller rework of how player areas and sites are laid out is its own design.
- **Map implementation.** The product owner is inclined to adopt `haunt-roll-fail`'s canvas map approach. Deferred, and unaffected by this design beyond the pinned font size.
- **Polling change-check.** Recorded separately in [phase-5-follow-ups.md](../../operations/phase-5-follow-ups.md).
