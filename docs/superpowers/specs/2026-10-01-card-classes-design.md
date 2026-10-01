# Card Classes Design

Date: 2026-10-01. Phase: **Phase - Card classes** in `docs/ROADMAP.md`.

## Goal

Move the 393 printed components (255 denizens, 48 relics, 30 edifices, 36
legacies, 24 sites) out of the hand-authored runtime JSON
`docs/catalog/new-foundations-component-catalog.json` and into Scala objects.
Scala becomes the only source of truth for card facts. Three payoffs:

1. **Compile safety.** Suits, restrictions, costs, card ids and the homeland
   suit are typed fields, not strings decoded at startup.
2. **Static card-to-power wiring.** A power refers to its card object
   directly. The `CatalogCards.*` lookups by power id, and most of the roughly
   250 `forCatalog` methods' reason to exist, go away.
3. **Printed properties as traits.** Locked, Site Only and Adviser Only are
   traits a card mixes in. They replace the catalog function that builds
   `LockedCards` (`OperationRestrictions.printedBy`).

## Decisions

### Source of truth

- Scala is authoritative. The JSON moves to
  `docs/catalog/reference/new-foundations-component-catalog.json` with a
  README note: reference only, Scala under `catalog/` and `gameplay/` is
  authoritative, and the file is not kept in sync.
- Deleted: the JSON schema, `scripts/validate-component-catalog.py`, the
  ingestion generator `reference/catalog-ingestion/build_runtime_catalog.py`
  and its reviewed mirrors (`reviewed-runtime-powers.json`,
  `runtime-denizen-definitions.json`), `CatalogLoader`, `CatalogLoaderSuite`
  and `src/test/resources/catalog/executable-subset.json`. The OCR files and
  transcriptions under `reference/catalog-ingestion/` stay as source evidence,
  and its README says the pipeline is retired.

### Types (package `oathdigital.catalog`)

- `PrintedPower(id: PowerId, persistent: Boolean, cost: Cost, text: String)`.
  `cost` is the leading run of `[favor]`, `[secret]`, `[favor-burnt]` and
  `[secret-burnt]` tokens in today's `rulesText` (127 powers have one). It
  uses the existing `model.Cost`. `text` is the rest, with the timing keyword
  (`**ACTION:**`, `**WHEN PLAYED:**`) left in place. There is no separate
  timing field: timing already lives on the behaviour (`PowerTiming`).
  `PrintedPower.rulesText` renders the printed line: the cost symbols, then
  `text`.
- `PrintsPowers`, the trait of every card kind and edifice face with
  powers: `powers`, plus the derived `handlers` (power ids as strings) and
  `rulesText` (the powers' lines joined by a blank line).
- `Denizen(id: DenizenId, name: String, suit: Suit)`, `Relic(id: RelicId,
  name, value, defense)`, `Legacy(id: LegacyId, name)`, and
  `Site(id: SiteId, name, defense, capacity, relicSlots, recoverDifficulty,
  startingResources, forgeRequirements, homeland: Option[Suit],
  handlers: Vector[String])`. Site handlers stay strings until slice 4:
  as `PowerId`s, the tests' `handlers.contains("…")` lookups would still
  compile and silently match nothing. Each card kind exposes `powers:
  Vector[PrintedPower]`.
- `Edifice(id: EdificeId, suit: Suit)` with two nested face objects, `intact`
  and `ruined`, each carrying a name and its powers.
- Marker traits `Locked`, `SiteOnly` and `AdviserOnly`. A card mixes in what
  it prints: `with Locked with AdviserOnly` replaces `LockedAdviserOnly`. An
  edifice's intact face mixes in `Locked`. The `CardRestrictions` enum is
  removed and its consumers (`CardPlay`, `SetupProcedure`,
  `GamePresentationProjector.restrictionName`, `OperationRestrictions`)
  match on the traits.
- There is no Grand Scepter trait. Only one Grand Scepter exists, so its
  `GrandScepter` operation restriction comes from the Grand Scepter's own
  declaration. `RelicRole` is removed. Its consumers
  (`FirstGameChronicleGenerator`, `ImplementedCardCatalog`) compare against
  the Grand Scepter card.
- Printed ids do not change (`"9"`, `"R01"`, `"E01"`, `"L01"`,
  `"site:deep-woods"`), and neither do power ids. Events store only these
  strings, so the wire format is unchanged apart from the `CatalogRef`
  removal below.
- `ExecutableCatalog` stays a value with the five vectors and its indexes.
  It loses `schemaVersion` and `ref`. Tests that build variant catalogs with
  `copy(...)` keep working.

### Card shape

Every card, implemented or held, has the same shape:

```scala
object StorytellerCard extends Denizen(DenizenId("…"), "Storyteller", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.storyteller"), persistent = false,
    cost = Cost(favor = 1), text = "**ACTION:** …")
  val powers = Vector(power)

case object Storyteller extends PaidAction(StorytellerCard.power)
```

- The object is named after the card with a `Card` suffix. An edifice is
  named after its intact face (`HallOfDebateCard`).
- Printed powers are members of the card. A card with several powers (Vow of
  Union, Giant Python, Vow of Wandering, Obsidian Cage, Brass Army, The Grand
  Scepter) names each one. Edifice powers are `intact.power` and
  `ruined.power`.
- References run one way: behaviour to card. A card never refers to its
  behaviour, so object initialisation cannot cycle.
- `PaidAction` takes the `PrintedPower` and reads its `id` and `cost`, which
  removes the hand-copied cost. Other powers keep their own base classes and
  take the card or printed power the same way.
- This is the permanent shape. Once every card is implemented, the printed
  data still stays on the card, separate from behaviour. The UI reads text
  without constructing behaviours.

### Placement

- **Final state:** each card object sits in its power's file under
  `gameplay/powers/…`.
- **Unimplemented cards** sit in holding files under `catalog/holding/`,
  split by kind and suit (for example `HoldingArcaneDenizens.scala`), each
  under 800 lines. Implementing a card moves its object out of the holding
  file and into the new power file.
- **Registry:** `gameplay/cards/NewFoundations.scala` lists every card
  explicitly, in one id-sorted `Vector` per kind, and builds the
  `ExecutableCatalog`. It lives in `gameplay` because it lists the cards
  beside the powers there, and `catalog` must only depend on `model`.
- `FirstGameSetupFixture.catalog` and `ServerRuntime` both use
  `NewFoundations.catalog`.
- An explicit list means a new card is added in two places. Two
  `CardCatalogSuite` checks guard that: the counts, and every registered
  power's card being in the registry.
- `catalog/CatalogModel.scala` is no longer compiled into the Scala.js
  frontend. Its `unmanagedSources` entry in `build.sbt` is removed. Nothing in
  the frontend uses it, and the frontend gets card data only from
  `CardDetailsProjection`.

### Rules text in the UI

- `GamePresentationProjector` shows `PrintedPower.rulesText` for each
  power: the cost tokens in printed order (`[favor]`, then `[secret]`, then
  `[favor-burnt]`, then `[secret-burnt]`, each repeated by count), a space,
  then `text`. All 127 printed costs already use that order.
- The `CardDetailsProjection` DTO is unchanged.
- Slice 1 checks once, in the throwaway generator, that this rebuilds every
  power's original `rulesText` exactly. A power whose printed token order
  differs gets its `text` adjusted by hand, and the plan lists it.

### Removed with the JSON

- `--catalog-path` and `OATH_CATALOG_PATH`. An unknown flag fails startup.
  Updated with them: `ServerConfig`, `OathServer`, `ServerRuntime`, the
  `build.sbt` package mapping, the launcher extra-defines,
  `verifyPackageMappings`, `scripts/smoke-packaged-distribution.sh`,
  `scripts/verify-alpha-release.sh`, `.claude/launch.json`, the README and
  `docs/operations/configuration.md`.
- `CatalogRef`: removed from `OathGame`, the event envelope
  (`GameEventWire`, `GameEventJsonSupport`), the codecs' unused
  `envelopeCatalog` parameters, `WireError.CatalogMismatch` and the stub
  `validateEventCatalog`. The format may change in place before the first
  public release (`docs/ROADMAP.md` standing rule). The game's code version is
  the real pin.
- The audit fingerprint: `ReviewedPowerCatalog.AuditedCatalogFingerprint`,
  `requireAudited` and its nine call sites (Forge, Search, Economy, Campaign,
  Challenge, Place Banner Resource, Negotiation, `GeneratedFirstGamePlanFactory`
  and `ReviewedPowerCatalog.resolver`), `CatalogHandlerInventory.fingerprint`
  and `structuralFingerprint`, the pin in `CatalogHandlerInventorySuite`, and
  `OathViolation.UnsupportedRuleCatalog` if nothing else uses it. Once the
  catalog is code, drift is a compile error or a test failure. The rest of the
  reviewed-catalog machinery stays under its existing cleanup item.

### Architecture rule

`scripts/check-architecture.py` today forbids the string `rulesText` under
`gameplay/`. That rule stays, and a second joins it: no file under
`gameplay/` reads `.text`. Declaring text in a card object
(`text = "…"`) is allowed. The intent is unchanged: gameplay never
interprets card text. A third rule makes the existing dependency direction
checked: `catalog/` imports none of `application`, `gameplay`,
`persistence`, `serialization` or `server`.

### Tests

- `CardCatalogSuite` ports every check of the Python validator that the types
  do not already make impossible:
  - counts 255/48/30/36/24;
  - the id sets (denizens 1-258 minus 94, 110 and 174; relics R01-R47 plus
    `grand-scepter`; E01-E30; L01-L36; sites with the `site:` prefix);
  - unique card ids and unique power and handler ids;
  - non-blank `text`, `[symbol]` tokens only from the 17-symbol vocabulary,
    and no OCR glyphs (`©`, `®`, `�`);
  - Forge requirements present if and only if capacity is 3;
  - non-negative relic value and defense and site numbers;
  - every power registered in `WalkerPowerCatalog.default` or
    `PhasePowerCatalog.default` belongs to a card in the registry.
- Made impossible by the types: unknown suits, invalid restriction
  combinations, edifice face restrictions, and more than one Grand Scepter.
- `CatalogNames` keeps working over the new catalog. New tests may reference
  card objects directly.
- `ExecutableCatalogSuite` keeps its index tests.

## Slices

Each slice gets its own plan and leaves `sbt test`, the frontend tests and
both gate scripts green.

1. **Data to Scala.** A throwaway script in the scratchpad (not committed)
   generates all 393 components into the holding files and the registry. The
   generated code is reviewed. The loader, `--catalog-path`, packaging entries,
   validator, schema, ingestion generator, frontend source entry,
   `CatalogRef` and the audit fingerprint go. The JSON moves to the reference
   folder. The restriction traits replace `CardRestrictions`, and
   `GrandScepter` replaces `RelicRole`. `CardCatalogSuite` lands. The
   architecture rule changes. Powers still find their cards through
   `CatalogCards` and the power index.
2. **Denizens beside their powers.** Implemented denizens move from holding
   files into their power files. `CatalogCards.denizen` is replaced by static
   references, and `PaidAction` takes the `PrintedPower`. (Slice 1 already
   has `OperationRestrictions.printedBy` read the `Locked` trait.)
3. **Relics and edifices.** The same move. The Grand Scepter's restriction
   comes from its declaration. `CatalogCards.relic` and `edifice` go.
4. **Sites and legacies.** Site handlers become `Vector[PowerId]`, with every
   test lookup moved off string `contains`. Site powers refer to site objects
   (`RiverSitePower`, `TravelSitePowers`, `siteWithHandler`).
   `FirstGameChronicleGenerator` reads `homeland` instead of parsing handler
   strings. `RuleSourceIndex` reads legacies statically.

## Docs and ROADMAP

- Update the docs that call the JSON authoritative:
  `docs/catalog/README.md`, `docs/architecture/core-domain-model.md`,
  `authoritative-events.md`, `codebase-structure.md`, `gameplay-modules.md`,
  `rule-resolution.md`, `server-event-journal.md`,
  `docs/operations/configuration.md`, `docs/rules/implementation-traceability.md`,
  `docs/rules/ambiguities.md`, `README.md` and `PRODUCT.md`. Dated plans and
  specs under `docs/superpowers/` are historical and stay as they are.
- `docs/ROADMAP.md`: the Card classes phase gets its slices. Two cleanup
  items are added:
  - **Shrink cached catalog fields.** About 242 powers hold a
    `catalog: ExecutableCatalog` field. Static card references make many of
    them unnecessary. Remove them as powers are touched.
  - **Migrate `CatalogNames` to card objects.** About 76 test files look
    cards up by name. Move them to direct object references.

## Out of scope

- Retiring `ReviewedPowerInspector`, `PowerRuntime` and `PowerRegistry`
  (existing cleanup item).
- Terrain traits for sites (coast, plains and so on). Terrain is not a
  printed restriction.
- Implementing any new card power.
