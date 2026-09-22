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

## Iconography pass (commit `086c085`)

A later slice moved the suit onto the card header as a glyph and gave the
site box its own corners. Re-verified in the same way, on a fresh game at
`1440x900`, `1024x768`, `768x1024` and `390x844`.

**No-reflow invariant holds.** Face-up card, face-down card, empty slot and
the whole site box each produced a `(0, 0)` width/height delta across hover
and `.focus()`, at every viewport above. The site box is inside the
fixed-width map surface, so its own width (234px) does not track the
viewport; the map's transform scale does.

**Card header.** A face-up `Fallen Spire` draws its arcane glyph left of
the name in both the small face and the overlay, coloured
`--suit-arcane`; the suit word is gone from both. The overlay printed
`Suit` and `Side` and no `Restrictions` row, which is the unrestricted
case landing correctly.

**Site corners.** `Mines` drew three favor glyphs' worth as a gold glyph
and the count `3` left of its name, `Broken Peaks` two secrets, and both
drew their defense as dice on the right. An undefended site (`Steppe`)
shows `0` rather than an empty corner, so a missing die never has to mean
two things. Footers paired the power text on the left with `Recover 5` or
a glyph-priced `Forge` on the right; a site with neither requirement omits
the corner.

**One row per site, and one defect found by measuring it.** Every site's
denizens, empty denizen slots and relics shared a single line at all four
viewports. The first measurement showed two distinct top edges per row:
the wrapper `span.site-card-target` around a placed denizen carried a
border, padding and a right margin that the empty slot beside it did not,
so a carded slot sat three pixels low. Nothing read that wrapper, so it
was removed (commit `086c085`) and the row now measures one top edge and
even gaps.

**Map degradation.** At 52% the face keeps its header -- glyph and name
both computed `display` visible -- while tokens, stats and restriction
stay hidden, which is the intended trade at map scale.

**Slots, after the product owner's answer** (commit `ef3df0f`): both slot
kinds draw empty, so a row is the site's capacity rather than its current
contents. Re-checked in a fresh game — `Solitary Pillar` drew one empty
denizen slot beside two face-down relics, `Golden Valley` one card and two
empty denizen slots, all on one top edge. Capacity is not always three:
the catalog has sites with one and two slots as well, and those rows are
correspondingly shorter.

**Heading and overlay, third revision:** the letter badge beside each site
name is gone — it repeated the name's first character — and the name is
centred by a three-cell grid, so the tokens on one side and the defense
dice on the other cannot pull it off centre. Measured in a fresh game:
four sites, each with equal left and right gaps around the name. The
overlay now leads its details with the card name above the `Suit` row, and
turns a card the viewer is allowed to read face-up before drawing it; a
card the viewer cannot identify still shows its back, since there is
nothing to turn. Removing the badge left the frontend's whole visual
chain unused, so `VisualDomRenderer` and `VisualRenderPlan` went with it;
`oathdigital.presentation` itself is untouched and still has its own
tests.

**Players pane and partitions, fourth revision.** A player board is now one
identity line (name, role and resources, with favor and secrets as glyphs)
and one unlabelled card row holding advisers, relics and any revealed
vision. Measured with five cards cloned into a board: at `1440x900` the
board is 134px in a 191px pane, and at `1024x768` 134px in 145px of usable
pane — no scroll at either, where two labelled rows could not have fitted
at any legible card size. The players row grew from 23% to 26% of the
table to buy that margin, which comes out of the map.

A partition zone now lays its options out like a hand rather than stacking
them. Measured by building a zone of four options in the docked actions
pane at `1024x768`: zone 356px, option 95px, three across with the fourth
wrapping. Below roughly 300px of zone width it drops to two, which is the
honest limit rather than a failure.

**Suit colours, second revision** (commit `ef3df0f`): the first attempt
brightened Beast, which the product owner rejected. Beast returns to the
sampled `#8c371e`; Discord moves to a fire-engine `#ce2029` and Hearth to
an orange `#e06a1e`. The three now differ in hue by roughly twenty degrees
each rather than one.

## Outcome

No defects found in the no-reflow invariant, the map degradation, or the
palette. The glyph review found real problems (a backwards eye direction,
indistinguishable dice) and they were fixed and re-confirmed, not glossed
over. The one open item is `suit-beast`'s weak legibility at the smallest
size and the untested knowable-relic hover state in a live browser — both
recorded above rather than silently passed.
