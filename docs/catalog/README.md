# Component catalog

The component catalog is Scala. Card types live in
`src/main/scala/oathdigital/catalog/Cards.scala`. Cards that are not yet
implemented live in `src/main/scala/oathdigital/catalog/holding/`; an
implemented card sits beside its power. The full list of components and the
production `ExecutableCatalog` are in
`src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`. That code is
authoritative for inventory, printed identities, text, restrictions, and
handler keys.

`reference/new-foundations-component-catalog.json` is the last JSON catalog the
game loaded. It is reference data only: the game does not read it, and it is
not kept in sync with the Scala. The catalog holds only:

- 255 final denizens (retained base cards plus final New Foundations cards)
- 48 relics, including the Grand Scepter
- 30 edifices, each with intact and ruined faces
- 36 legacies
- 24 sites

Setup cards, player boards, foundations, visions, and banners are rules-owned
engine concepts and are not catalog entries.

Games and event envelopes carry no catalog reference. The build is the pin.

Components use their lower-right printed identifiers wherever one exists:
denizens use their numeric ID, relics use `R01` through `R47`, edifices use
`E01` through `E30`, and legacies use `L01` through `L36`. The Grand Scepter
has no lower-right identifier; the game identifies it by `TheGrandScepterCard.id`.
Site IDs are stable name-based IDs because sites have no printed component ID.
Power IDs remain name-based, so changing a component's identity does not change
the engine's behavior bindings.

Relics record both printed corner statistics: `value` is the upper-left relic
value and `defense` is the upper-right defense.

A power is a `PrintedPower`: a stable ID, a persistence flag, the run of favor
and secret symbols its text opens with, and the rest of the printed text.
Gameplay never reads that text; `scripts/check-architecture.py` forbids
`rulesText` and `.text` under `gameplay/`. Only the presentation layer reads
`PrintedPower.rulesText` (cost symbols, then text).

Rules text is Markdown. Printed resource symbols use `[favor]`,
`[favor-burnt]`, `[secret]`, and `[secret-burnt]`. Other printed gameplay
symbols use the same bracket convention, including `[attack-die]`,
`[defense-die]`, `[round-die]`, `[sword]`, `[hollow-sword]`, `[shield]`,
`[skull]`, and `[suit-arcane]` through `[suit-order]`. Bold and italic
printing is preserved with standard Markdown.

Restrictions are marker traits a card mixes in: `Locked`, `SiteOnly`, and
`AdviserOnly`. An unrestricted denizen mixes in none. The only restricted forms
are site-only, adviser-only, and locked adviser-only; a locked card is always
adviser-only, so a locked denizen mixes in both `Locked` and `AdviserOnly`, and
a denizen is never `Locked` without `AdviserOnly`, and never `SiteOnly` together with either. Edifice restrictions are
face-specific: every intact face is `Locked` and every ruined face is
unrestricted. Edifices have no restriction of their own.

## Adding or changing a card

Add or edit the card object, keeping each component's powers in printed order.
When splitting independently timed clauses, retain a stable base and assign
stable suffixed power IDs, and set `persistent` for each clause from its printed
black braid. Changing or splitting a power ID means updating the registry and
any handler that binds to it.

List a new card in `NewFoundations`. `CardCatalogSuite`
(`src/test/scala/oathdigital/gameplay/cards/CardCatalogSuite.scala`) pins the
component counts, so a forgotten card fails a test. It also checks the printed
ID ranges, globally unique card identities, power-ID and site-handler
uniqueness, non-blank text, the symbol vocabulary, non-negative printed numbers,
the Forge requirements of three-slot sites, and that every registered card power
is printed in the catalog.
