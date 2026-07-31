# Runtime component catalog

`new-foundations-component-catalog.json` is the complete component data loaded
by the game engine. It deliberately contains only:

- 255 final denizens (retained base cards plus final New Foundations cards)
- 48 relics, including the Grand Scepter
- 30 edifices, each with intact and ruined faces
- 36 legacies
- 24 sites

Setup cards, player boards, foundations, visions, and banners are rules-owned
engine concepts and are not catalog entries.

The runtime format keeps only `schemaVersion` and `catalogVersion` as top-level
metadata. Component records contain stable identities, stable handler keys,
and fields needed by gameplay. Source paths, provenance, ingestion status,
confidence, unresolved-task lists, and corpus claims live in
`reference/catalog-ingestion` instead.

Components use their lower-right printed identifiers wherever one exists:
denizens use their numeric ID, relics use `R01` through `R47`, edifices use
`E01` through `E30`, and legacies use `L01` through `L36`. The Grand Scepter
has no lower-right identifier and uses the explicit `grand-scepter` fallback.
Site IDs remain stable name-based IDs because sites have no printed component
ID. Handler keys remain name-based, so changing catalog identity does not
change the engine's behavior bindings.

Relics record both printed corner statistics: `value` is the upper-left relic
value and `defense` is the upper-right defense.

Rules text is Markdown. Printed resource symbols use `[favor]`,
`[favor-burnt]`, `[secret]`, and `[secret-burnt]`. Other printed gameplay
symbols use the same bracket convention, including `[attack-die]`,
`[defense-die]`, `[round-die]`, `[sword]`, `[hollow-sword]`, `[shield]`,
`[skull]`, and `[suit-arcane]` through `[suit-order]`. Bold and italic
printing is preserved with standard Markdown.

The schema remains `1.0.0` while the unreleased product format is in flux.
`catalogVersion` identifies the specific data set independently of that format
version.

Run:

```sh
python3 scripts/validate-component-catalog.py
```

The validator checks the JSON schema, exact component counts, globally unique
component identities, handler-key uniqueness and syntax, exact printed ID
ranges, relic values, symbol vocabulary, edifice pairing, the single Grand
Scepter role, and the absence of ingestion-only fields.
