# Extraction and verification coverage

## Inputs

| Source | Pages | Text extraction | Visual render |
|---|---:|---|---|
| Combined Rulebook (CR) | 48 | All pages, native PDF text | All pages rendered and reviewed in contact sheets |
| Welcome booklet (NF) | 16 | All pages, native PDF text | All pages rendered and reviewed in contact sheet |

Both PDFs are tagged Adobe InDesign exports. Every page produced non-empty native text. OCR was not used.

## Page coverage map

### Combined Rulebook

- pp. 1-4: contents/components
- p. 5: game concept, lineages, eras
- pp. 6-7: first-game setup
- pp. 8-13: later setup and foundations
- pp. 14-16: core concepts and victory objects
- pp. 17-19: turn/round flow and end conditions
- pp. 20-27: major/minor actions
- pp. 28-30: powers, modifiers, cards, edge clarifications
- pp. 31-35: sites, banners, legacies, Empire
- pp. 36-41: Chronicle
- pp. 42-43: interpretation, timing, information, consent
- pp. 44-46: glossary and edge cases
- pp. 47-48: credits/index

### Welcome booklet

- pp. 1-6: expansion boundary and component migration
- pp. 7-9: eras, first game, Chronicle/foundations overview
- pp. 10-12: sites, bandits, Atlas, banners
- pp. 13-15: action and citizenship/exile changes
- p. 16: edifices, goals, Reliquary, bury, map terms, consent, Empire

## Verification results

- Page counts from PDF metadata: 48 + 16 = 64.
- Extracted text yield: approximately 19,016 words (CR) and 4,807 words (NF).
- No blank-text pages.
- Visual review found no missing pages, rotated pages, clipping, or unreadable source layouts.
- Citation numbering matches the printed page labels on the supplied PDFs.
- JSON was syntax-validated and YAML was parse-validated after creation.

## Limitations

- Decorative typography causes noisy character spacing/capitalization in raw extraction; summaries normalize it.
- Game icons can extract as arbitrary glyphs or disappear. This layer describes their meaning but is not an icon-level transcription.
- Component images contain data (card IDs, board tracks, icons) not fully represented in prose. Exact implementation requires separate component/card ingestion.
- This ingestion covers only the two supplied PDFs. It does not include errata, FAQ, official card database content, or the original base rulebook.

