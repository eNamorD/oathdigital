# Card Shape and Inspection — Browser Verification

Covers what jsdom cannot assert: real hover/focus behaviour, real font
metrics for the `ex`-based card box, and the visual judgement calls (glyph
readability, suit distinguishability, palette) no automated check can make.

**Commit verified:** `43db537` (Tasks 1-6), plus the glyph revisions from
the Task 7 Step 5 product-owner review, committed together with this record.

**Server:**

```bash
OATH_DATABASE_PATH=/tmp/card-shape-verification ./sbtw "run --port 8123"
```

Opened at `http://127.0.0.1:8123/?mode=server`, a fresh game created through
the normal start page.

## No-reflow invariant

Checked at four viewports, for a face-up denizen, an unknown face-down card,
and an empty denizen slot, by comparing `getBoundingClientRect()` before and
after `.focus()` and a real pointer hover. All four states, at all four
viewports, produced a `(0, 0)` width/height delta:

| Viewport | Face-up delta | Face-down delta | Empty-slot delta |
| --- | --- | --- | --- |
| 1440×900 | (0, 0) | (0, 0) | (0, 0) |
| 1024×768 | (0, 0) | (0, 0) | (0, 0) |
| 768×1024 | (0, 0) | (0, 0) | (0, 0) |
| 390×844 | (0, 0) | (0, 0) | (0, 0) |

Also confirmed by eye: hovering a face-up card (`Broken Forge`, an edifice
at the Steppe site) shows the `outline` emphasis ring with no visible shift
in the card or the row around it.

**Gap, recorded honestly:** the fourth card state — a knowable face-down
relic (the hover-summary swap) — did not occur naturally in a fresh game;
reaching it needs a negotiation disclosure mid-game, which this pass did not
play out. Its CSS mechanism (`.card-hover-summary { position: absolute;
inset: 0 }` inside the same fixed `.card-face` box) is the same
no-reflow technique already confirmed above, and it has 7 dedicated jsdom
tests in `CardFaceSuite` and `CardInspectionOverlaySuite`, but it was not
independently observed in a real browser this pass.

## Click-to-inspect, in a real browser

Clicking `Broken Forge` opened the full-screen overlay: the card at overlay
scale, `Suit`/`Restrictions`/`Side` field names restored, the multi-line
rules text rendered with token glyphs and bold emphasis. The Close button
returned focus and dismissed the overlay cleanly.

## Map degradation at 0.6 scale

Zoomed the map to 36% (`.map-content` gained `.map-compact`); a face-up
denizen's `.card-suit` computed to `display: none` while `.card-name`
stayed visible. Zoomed back to 138% (`.map-compact` removed); `.card-suit`
computed back to `display: block`. Toggling is a pure class flip with no
re-render, so a degraded face-up card cannot pick up the face-down letter
treatment — confirmed by inspecting the class list directly, not just by eye.

## `--card-w: 13ex` against both space constraints

Measured in-browser (not reasoned about from the stylesheet):

- **Site denizen row:** 427px wide; three `.card-face` boxes measured 94px
  each, plus two 4px gaps = 290px used, 137px to spare.
- **Player-board adviser column:** 473px at desktop width, 200px at the
  narrowest tested viewport (375px). Two boxes plus one gap = 192px used —
  8px to spare at the narrowest width. Comfortable at desktop, tight but
  holding at mobile.

No reduction to `--card-w` was needed.

## Warm-suit distinguishability (Discord / Hearth / Beast)

Their colours are about one degree apart in hue (confirmed: `--suit-discord
#c84623`, `--suit-hearth #d24b23`, `--suit-beast #8c371e`), so shape is what
has to carry the identity. At the smallest rendered size (14px, the
card-face suit-subtitle size):

- `suit-hearth` — a house/roof shape, unambiguous at any size checked.
- `suit-discord` — a rounded face with two upward points; distinguishable
  from both neighbors, though it shares a general "creature head" read with
  `suit-beast`.
- `suit-beast` — a downward chevron/fang shape; the faintest of the three
  at 14px, reads as a simple caret rather than clearly "beast" until
  compared side by side with the other two.

All three remain tellable apart at every size checked. `suit-beast` is the
weakest of the three and worth a second pass in a future slice, but it was
not blocking for the product owner in this review.

## Palette

Checked live on `Hall of Mockery` (Discord denizen, multi-clause rules
text): four `[favor]` glyphs rendered gold, one `[favor-burnt]` glyph
rendered as a distinguishably darker, duller cracked-ring version of the
same gold, and the `Discord` suit subtitle rendered in `--suit-discord`.
Bold emphasis (`cannot`) and the `SETUP (END) / WHEN EXPLORED:` prefix
rendered correctly alongside the glyphs.

## Glyph review (Task 7 Step 5 — blocking)

All seventeen glyphs were rendered at card-face size and overlay size on
the game's own dark background (`#1b1811`) and shown to the product owner.
Two rounds of revision:

1. **`suit-nomad`** — the original had a smiling mouth and dot eyes. Revised
   to a neutral straight-line mouth and U-shaped (cup, opening upward) eyes.
   The first revision attempt had the eye arcs sweeping the wrong direction
   (rendered `^`-shaped instead of `u`-shaped); the arc sweep flags were
   flipped and the product owner confirmed the corrected shape.
2. **Dice colours** — `attack-die`, `defense-die` and `round-die` shared one
   neutral colour (`#d8cdb4`) with `skull`/`shield`/`sword`/`hollow-sword`.
   Split into three dedicated custom properties: `--die-attack: #c0392b`
   (red), `--die-defense: #3d6fb4` (blue), `--die-round: #9b59b6` (purple).
   `skull`/`shield`/`sword`/`hollow-sword` keep the shared neutral colour —
   not requested for change.

Approved by the product owner after the second round. No further glyph
changes were requested.

**One deviation, stated rather than hidden:** the design spec's Global
Constraints palette table (10 custom properties, "sampled from the product
owner's reference images") did not anticipate distinguishing the three
dice by colour. The three new `--die-*` properties are not in that table;
they were added directly from this review because the shared neutral
colour gave no way to tell the dice apart from each other. `skull`,
`shield`, `sword` and `hollow-sword` were left on the shared neutral colour
since nobody asked for those to change.

## Outcome

No defects found in the no-reflow invariant, the map degradation, or the
palette. The glyph review found real problems (a backwards eye direction,
indistinguishable dice) and they were fixed and re-confirmed, not glossed
over. The one open item is `suit-beast`'s weak legibility at the smallest
size and the untested knowable-relic hover state in a live browser — both
recorded above rather than silently passed.
