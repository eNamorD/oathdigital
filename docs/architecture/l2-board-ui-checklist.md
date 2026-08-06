# L2 board UI validation

The World remains ordered left-to-right as Cradle, Provinces, Hinterland at
desktop widths. Below 760 px, regions stack in that same order while sites use
two columns; below 440 px, sites use one column.

## Manual deployment checklist

Test the server-mode board with a populated, persisted game and with browser
images disabled or no visual assets deployed. At each viewport, verify that
there is no horizontal page scroll and no clipped site content:

- 1280 x 800: three ordered region columns; site names, pawns, four property
  values, denizen chips, and facedown relic count are all visible.
- 760 x 900: stacked ordered regions with two site cards per row; development
  controls wrap vertically and remain keyboard/touch usable.
- 440 x 900: stacked ordered regions with one site card per row; long site and
  denizen labels wrap inside their cards.
- 320 x 568: no horizontal overflow; site names and pawn markers remain more
  prominent than property metadata.

For both the active and an inactive player URL, confirm focus indicators,
logical tab order, readable accessible names, and 44 x 44 CSS-pixel practical
touch targets for enabled commands. Confirm the inactive view says who it is
waiting for and exposes no pawn, adviser, or Wake controls. In Wake, confirm
only server-projected Take Wealth controls appear. Confirm relics show only a
facedown count, reconnect/polling still advances the board, and missing or
failed site/card assets leave the deterministic labelled fallback visible.
