# New Foundations component catalog

`new-foundations-component-catalog.json` is the versioned, machine-readable
component inventory. Its schema is
`new-foundations-component-catalog.schema.json`.

Printed cards are singleton definitions unless source evidence proves multiple
physical copies. The catalog records `physicalCopyCount: 1` and print evidence,
but intentionally contains no runtime `CardInstanceId`. Printed IDs are typed
objects such as `{ "type": "edifice-id", "value": "E11" }`. A null
`printedComponentId` is not an inferred value: it is paired with an explicit
review item where the icon or small print was not safely readable. OCR was used
only to assist locating text; names in this pre1 catalog received a visual
sheet-level review. Icon-bearing fields remain unresolved instead of guessed.

Authority order is explicit. The June 2026 Combined Rulebook is normative for
general rules. March component sheets and the Welcome booklet remain evidence
for printed components and migration summaries. Suspected wording differences
are cataloged in `conflicts`; consumers must not silently merge them.

The two denizen sources are intentionally distinct:

- `base-pre1/unchanged-denizens.pdf`: 162 retained base fronts.
- `new-foundations-rev1/denizens.pdf`: 93 supplied fronts (replacements and
  additions).

Neither source, and especially not the March folder alone, is claimed to be a
complete 195-denizen corpus.

Banners are two physical families with two discrete active face definitions
each. Their family records retain a flippable active-face runtime state without
inventing card instances. Supply is numeric state from 7 through 0 plus
warband-bank refresh tables; marker coordinates are not persisted. The Atlas is
one front-to-back ordered sequence, with Recent at the front and Forgotten at
the back.

Validate with:

```sh
python3 scripts/validate-component-catalog.py
```

The validator checks schema/catalog version agreement, unique definition and
physical-copy identities, source references, edifice face pairings, and the
required corpus/authority safeguards.
