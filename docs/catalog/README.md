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
metadata. Rendered component records contain stable identities and ordered
`powers` entries with stable ID, persistence, and exact rules text. Sites retain
handler-only records because they have no rendered power text. Source paths,
provenance, ingestion status,
confidence, unresolved-task lists, and corpus claims live in
`reference/catalog-ingestion` instead.

Components use their lower-right printed identifiers wherever one exists:
denizens use their numeric ID, relics use `R01` through `R47`, edifices use
`E01` through `E30`, and legacies use `L01` through `L36`. The Grand Scepter
has no lower-right identifier and uses the explicit `grand-scepter` fallback.
Site IDs remain stable name-based IDs because sites have no printed component
ID. Power IDs remain name-based, so changing catalog identity does not
change the engine's behavior bindings.

Relics record both printed corner statistics: `value` is the upper-left relic
value and `defense` is the upper-right defense.

Rules text is Markdown. Printed resource symbols use `[favor]`,
`[favor-burnt]`, `[secret]`, and `[secret-burnt]`. Other printed gameplay
symbols use the same bracket convention, including `[attack-die]`,
`[defense-die]`, `[round-die]`, `[sword]`, `[hollow-sword]`, `[shield]`,
`[skull]`, and `[suit-arcane]` through `[suit-order]`. Bold and italic
printing is preserved with standard Markdown.

Every denizen has a required `restrictions` field. JSON `null` is the canonical
explicit representation of an unrestricted denizen; an empty array is not
accepted. The only restricted forms are `["site-only"]`,
`["adviser-only"]`, and `["adviser-only", "locked"]`. A locked card is always
adviser-only, so `locked` is invalid by itself or in any other combination.
Edifices also preserve an explicit `restrictions: null` field, but restricted
values are invalid for them.

Schema `1.2.0` replaces each rendered component's handler array and combined
rules text with ordered `{id, persistent, rulesText}` power entries. Catalog
`2026.08.29-pre4` is intentionally incompatible with the earlier prerelease
format; no migration is provided because no production data uses it.

The initial segmentation and persistence values are provisional. They were
derived only from the checked-in transcription and Scala classifications, with
no reference-PDF review: every existing record remains one power so its stable
ID and exact combined text are preserved. Only `denizen.vow-of-peace`,
`denizen.relic-worship`, `edifice.e13.ruined`, `edifice.e17.intact`, and
`edifice.e17.ruined` are marked persistent from existing explicit runtime
classifications. All other flags conservatively default to false until manual
component review splits clauses and corrects braid status.

## Manual power review

Edit only `docs/catalog/new-foundations-component-catalog.json` during manual
review, and preserve each component's power order. When splitting independently
timed clauses, retain a stable base and assign stable suffixed IDs; set
`persistent` for each clause from its printed black braid. Changing or splitting
an ID requires later registry and handler-fingerprint reconciliation.

The reference-ingestion mirror and generator may temporarily differ from the
reviewed runtime catalog. They will be reconciled after review rather than used
to overwrite in-progress catalog edits.

Run:

```sh
python3 scripts/validate-component-catalog.py
```

The reviewed runtime denizen records are mirrored in
`reference/catalog-ingestion/runtime-denizen-definitions.json`. Running the
reference generator with no arguments is a non-writing equality check:

```sh
python3 reference/catalog-ingestion/build_runtime_catalog.py
```

To inspect regenerated output, pass `--output` with a temporary path. This
deliberately avoids overwriting the runtime catalog before equality has been
established.

The validator checks the JSON schema, exact component counts, globally unique
component identities, power-ID uniqueness and syntax, ordered non-empty power
arrays, persistence booleans, exact printed ID
ranges, denizen restriction combinations, relic values, symbol vocabulary,
edifice pairing, the single Grand Scepter role, and the absence of
ingestion-only fields.
