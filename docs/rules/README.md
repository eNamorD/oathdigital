# Oath: New Foundations rules knowledge layer

This directory is an implementation-neutral, source-cited summary of the supplied 2026 rules. It does **not** encode executable game logic.

## Source authority

1. `Oath Combined Rulebook.pdf` ("CR") is the complete, current rules reference for *Oath: New Foundations*.
2. `Oath Welcome to New Foundations Booklet.pdf` ("NF") is migration and orientation guidance. It explains differences from base *Oath*, but repeatedly directs readers to CR for complete rules.
3. Card text and the official card database may add or override rules. CR says powers can break general rules and directs readers to `cards.buriedgiant.com` for detailed card data (CR pp. 28, 42). Card content was not part of this ingestion.

All citations use the 1-based PDF page number, which matches the printed page number on these files. Example: `[CR p. 17]`.

## Files

- `rules-reference.md` - terminology, setup, turn flow, actions, victory, Empire, and fine print.
- `new-foundations-delta.md` - explicit changes/additions relative to base *Oath*.
- `chronicle.md` - persistent campaign/era model and the ordered Chronicle procedure.
- `glossary.yaml` - machine-readable terms with citations.
- `rule-index.json` - machine-readable topic/source index and precedence metadata.
- `ambiguities.md` - implementation questions and source limitations.
- `coverage.md` - page-level extraction and verification coverage.

## Interpretation conventions

- **must** means mandatory; **may** means optional; **cannot** means prohibited.
- A rule on a component/card can override the general rule.
- Resolve instructions in order. If effects occur simultaneously, the player taking the turn chooses their order; in the Chronicle Phase, the current Chancellor chooses (CR p. 42).
- Italic text is reminder/clarification and is superseded by the non-italic rule it references (CR p. 42).
- This layer paraphrases rules. Consult the cited PDF page and applicable components whenever exact wording or iconography matters.

