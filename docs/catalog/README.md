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

The schema remains `1.0.0` while the unreleased product format is in flux.
`catalogVersion` identifies the specific data set independently of that format
version.

Run:

```sh
python3 scripts/validate-component-catalog.py
```

The validator checks the JSON schema, exact component counts, globally unique
component identities, handler-key uniqueness and syntax, edifice pairing, the
single Grand Scepter role, and the absence of ingestion-only fields.
