# Rulebook implementation traceability

Last reviewed: 2026-08-09

This matrix maps the source-cited rules knowledge layer to executable behavior
and tests. It is a project-management aid, not a second rules reference. Read
the cited summary and source page before implementing a row. Update a row only
when behavior or evidence changes; a similarly named type is not implementation
evidence.

## Status and source conventions

| Status | Meaning |
|---|---|
| **Unimplemented** | No executable rule behavior was found. State/type names alone remain unimplemented. |
| **Partial** | Some bounded behavior is executable, but required rule steps or outcomes are absent. |
| **Implemented** | The bounded behavior is executable, but no focused automated rule test was found. |
| **Tested** | Executable behavior has focused automated tests. |
| **Blocked** | Implementation depends on missing component data or a product/source decision. |

`CR` means the normative *Oath Combined Rulebook*. `NF` means an explicit New
Foundations change/orientation statement; CR remains normative. `A#` refers to
the numbered issue in [`ambiguities.md`](ambiguities.md): B# for “Blocking
without component/card corpus” and F# for “Formalization decisions.”
Implementation paths are abbreviated relative to
`src/main/scala/oathdigital/`; suite paths are abbreviated relative to
`src/test/scala/oathdigital/`. “Model only” deliberately does not raise a row
above **Unimplemented**.

## Setup

| Rule area | Status | Normative source / NF change | Implementation and test evidence | Dependencies / unresolved ambiguity |
|---|---|---|---|---|
| First-game common setup, map population, decks, banks, tracks and player wealth | **Tested (bounded)** | [CR pp. 6-7](rules-reference.md#very-first-game-only); changed/randomized by [NF p. 8](new-foundations-delta.md#campaign-structure) | `setup/FirstGameSetup.scala` constructs the exile-only introductory game from a recorded server-derived plan. Setup, wire, application, persistence, server, and frontend suites cover replay and presentation. | Legacies, Imperial roles, altered Foundations, and later-game restoration remain excluded. |
| Later-game restoration from campaign/Atlas | **Unimplemented** | [CR pp. 8-9](rules-reference.md#later-games); Atlas emphasis [NF p. 12](new-foundations-delta.md#map-and-pieces) | Atlas storage/removal helpers are tested in `model/WorldModelSuite.scala`; no setup command applies the restoration procedure. | Foundation faces B2; Chronicle-produced stored state must exist first. |
| Six foundation setup effects and world-deck construction | **Blocked** | [CR pp. 10-12](rules-reference.md#later-games); mutable foundations [NF p. 9](new-foundations-delta.md#campaign-structure) | Foundation face/source state and reviewed catalog records exist; no typed handler executes a foundation. | Executable Foundation handlers B2; catalog text must not be mistaken for behavior. |
| Ordered pawn placement on an eight-site 2/3/3 map | **Tested** | [CR pp. 7, 13](rules-reference.md#setup) | `setup/Setup.scala` and `FirstGameSetupRules` enforce compatible catalog, unique participants/lineages/sites, eight known sites, participant order, in-play destinations, completion and replay. | Imperial placement restrictions remain absent. |
| Starting adviser choice and pawn placement | **Tested (bounded)** | [CR pp. 7, 13](rules-reference.md#setup) | The first-game flow records each Exile's private adviser choice, enforces ownership/order, and then places pawns. Player-scoped projections expose only the active player's choices. | Imperial placement and reveal rules remain unimplemented; hidden sharing policy F4. |
| Event encoding, replay and application orchestration | **Tested** | Engineering support, not a rulebook statement | `GameEventWire`, `GameApplicationService`, replay, and optimistic repository contracts support mixed v2-v4 setup/gameplay streams. Tests cover malformed history, replay failures, conflicts, reload, and private projections. | Future events must preserve the same compatibility and redaction boundaries. |
| Durable event repository/database adapter | **Tested** | Engineering support, not a rulebook statement | `HsqldbEventStreamRepository` stores atomic ordered batches, performs schema migrations, rejects conflicts, and reconstructs streams after close/reopen. | Operational backup and production deployment remain outside game rules. |

## Turn and phase flow

| Rule area | Status | Normative source / NF change | Implementation and test evidence | Dependencies / unresolved ambiguity |
|---|---|---|---|---|
| Round order and Wake → Act → Rest transitions | **Partial** | [CR p. 17](rules-reference.md#round-and-turn-flow) | `gameplay/phases/Wake.scala` implements only the first active Exile's explicit Wake → Act transition. Later turns, player/round advancement and Rest remain absent. | Action transaction boundary F1; later-turn ordering F2. |
| Wake victory checks, Wake powers, Take Wealth | **Tested (bounded)** | [CR p. 17](rules-reference.md#wake); power identity/timing CR pp. 28, 31; Take Wealth site context [NF p. 11](new-foundations-delta.md#map-and-pieces) | `gameplay/phases/Wake.scala` transfers one loose favor/secret and records stable timing/source/power references. Enemy/resource/per-site-use legality and private controls share the typed query pipeline in `gameplay/RuleResolution.scala`. Mandatory title/Vision states return typed unsupported violations. Tests: `gameplay/WakeSuite.scala`, mixed v2/v3 wire and application suites. | Generic powers, River movement and victory resolution remain deferred. Optional unsupported Wake powers do not block End Wake. |
| Act action loop and Supply spending | **Tested (bounded)** | [CR p. 17](rules-reference.md#act) | First-turn Travel and Search spend typed Supply and return to Act action selection. Search persists a pending choice across requests; the general action loop, Rest, and nested-action fixed point remain absent. | Board/action costs B3; nested action boundary F1. |
| Rest powers, token return, secret reveal, Supply refresh | **Partial** | [CR p. 18](rules-reference.md#rest) | `SupplyRules.refresh` implements the board-table refresh calculation and is tested in `model/SupplySuite.scala`. No Rest transition performs resource return, secret flip, powers, or refresh. | Exact board projection B3; power data B1. |
| After-action fixed-point checks: bandit refill and title transfer | **Unimplemented** | [CR pp. 15-16](rules-reference.md#core-state-and-terminology) | Relevant force/title state exists; no action boundary or state-based rule evaluator exists. | Explicit transaction/fixed-point policy F1. |

## Major and minor actions

| Rule area | Status | Normative source / NF change | Implementation and test evidence | Dependencies / unresolved ambiguity |
|---|---|---|---|---|
| Search | **Tested (bounded)** | [CR p. 20](rules-reference.md#search---2-to-4-supply) and simultaneous ordering [CR p. 42](rules-reference.md#rules-precedence-and-timing) | [bounded-search.md](../architecture/bounded-search.md) documents the exile-only, unaltered-Foundation procedure. `gameplay/actions/Search.scala` implements costs, ordered draw/early Vision stop, v4 durable pending/completion events, ordered next-region discards, typed site/adviser restrictions, favor gain, owner-only projection, HTTP intents, replay and Scala.js controls. `SearchSuite`, wire/application/server/frontend suites cover the slice. | Individual component powers, altered Foundations, Imperials, later turns and generic after-action checks remain deferred. |
| Travel | **Tested (bounded)** | [CR p. 21](rules-reference.md#travel---1-to-4-supply-before-modifiers); site powers CR p. 31 and NF p. 11; consent timing CR p. 43 | `gameplay/actions/Travel.scala` implements first-turn exile-only normal Travel with the region matrix and Supply debit. Coast/Island/Mountain/Pass activate through the exact-ID typed registry documented in [rule-resolution.md](../architecture/rule-resolution.md). Command, deterministic replay, private legal-destination projection, and destination-selection UI share resolution. Tests cover registry precedence, engine, v2/v3 wire, application reload, actor derivation/trust boundaries, projection and frontend behavior. | River, consent workflow, generic card/relic/edifice/legacy/Foundation modifiers, later turns and Rest remain deferred. Unsupported relevant handlers and modifier-bearing states fail typed rather than being ignored. |
| Campaign: Conquest/Raid targeting and battle resolution | **Blocked** | [CR pp. 22-23](rules-reference.md#campaign---2-supply); split/ordering/discard changes [NF p. 13](new-foundations-delta.md#actions) | `PendingProcedure.Campaign` and force/relic orientation state exist; no campaign type, target, dice, plan, loss, conquest or raid transition exists. | Battle-plan/card data B1/B5; target legality F9; RNG/event policy F6; action boundary F1. |
| Muster | **Unimplemented** | [CR p. 24](rules-reference.md#muster---1-supply); variable yield [NF p. 13](new-foundations-delta.md#actions) | Denizen tokens, adviser orientation and warband counts are modeled; no action behavior exists. | Card access/power rules B1. |
| Trade | **Blocked** | [CR p. 24](rules-reference.md#trade---1-supply); secret cost changed [NF p. 13](new-foundations-delta.md#actions) | Resource state and tested Supply arithmetic exist; no trade behavior exists. | Exact player-board yield table B3. |
| Forge | **Blocked** | [CR p. 25](rules-reference.md#forge---1-supply); new action [NF p. 14](new-foundations-delta.md#actions) | Site catalog fields include forge requirements and are decoded/tested in `catalog/CatalogLoaderSuite.scala`; no ruling, payment, denizen-token or relic-draw behavior exists. | Verified site/component data B5; action boundary F1. |
| Recover | **Blocked** | [CR p. 25](rules-reference.md#recover---1-supply); redesigned [NF p. 14](new-foundations-delta.md#actions) | `PendingProcedure.Recover`, relic orientation and catalog difficulty fields exist; no roll/payment/peek/take behavior exists. | Site/component data B5; RNG/event policy F6; hidden-information policy F4. |
| Challenge and banner resource actions | **Blocked** | [CR pp. 26, 32](rules-reference.md#challenge---1-supply); new action/banner changes [NF pp. 12, 15](new-foundations-delta.md#banners) | Banner face/holder/resource state is modeled and face identity is tested in `model/WorldModelSuite.scala`; no challenge or ribbon behavior exists. | Exact banner faces/ribbons B4. |
| Core minor actions: adviser play/discard, powers, relic peek, warband move, negotiation, self-exile, Imperial transfer | **Blocked** | [CR p. 27 and pp. 32, 34-35](rules-reference.md#minor-actions); negotiation/exile changes [NF p. 15](new-foundations-delta.md#actions) | `PendingProcedure.Negotiation` and required state containers exist; no minor-action transitions exist. | Card powers B1; hidden information F4; component limits F5; Empire behavior. |
| Power framework, precedence and timing | **Partial** | [CR pp. 28-30, 42](rules-reference.md#rules-precedence-and-timing) | `RuleResolution.scala` provides typed sources, explicit handler registration, deterministic ordering, blocks, cost changes, decisions, and unsupported outcomes. Travel uses it for Coast/Island/Mountain/Pass; Take Wealth shares typed legality queries. | Most component handlers, simultaneous ordering F2, and first-game citizenship policy F8 remain. |

## Campaign, victory, and Empire

| Rule area | Status | Normative source / NF change | Implementation and test evidence | Dependencies / unresolved ambiguity |
|---|---|---|---|---|
| Oathkeeper qualification, transfer, Usurper limiter and Wake victory | **Unimplemented** | [CR pp. 16, 19](rules-reference.md#oathkeeper-and-usurper); Protection excludes banners [NF p. 16](new-foundations-delta.md#empire-and-other-changes) | Goals/title/tracks are modeled and holder references validated in `model/DomainValidation.scala`; no qualification or victory behavior exists. | Context-specific ties F7; action boundary F1. |
| Vision reveal/qualification/victory and Conspiracy | **Blocked** | [CR pp. 16, 30](rules-reference.md#visions); Conspiracy changed [NF p. 16](new-foundations-delta.md#empire-and-other-changes) | Vision states/catalog goal shape exist and catalog decoding is tested; no reveal, three-Vision gate, goal evaluation or Conspiracy action exists. | Exact Vision/component data B5; hidden information F4. |
| End die, rounds 5-8, War Exhaustion and no-Empire fallback | **Unimplemented** | [CR p. 19](rules-reference.md#empire-endings) | Round/result state exists; no end-of-round evaluator or die roll exists. | RNG/event policy F6; tie rules F7. |
| Chancellor/Citizen roles, Successor and Empire actions | **Unimplemented** | [CR pp. 19, 34-35](rules-reference.md#empire-endings); simplified/changed exile rules [NF pp. 15-16](new-foundations-delta.md#empire-and-other-changes) | Role/Imperial force/Grand-Scepter-capable card state is modeled; multiple Chancellors are rejected by domain validation. No citizenship, Successor, Scepter or Imperial rule behavior exists. | Card/Scepter powers B1/B5; first-game policy F8. |

## Chronicle and durable campaign

| Rule area | Status | Normative source / NF change | Implementation and test evidence | Dependencies / unresolved ambiguity |
|---|---|---|---|---|
| Campaign-state representation and structural invariants: Atlas, lineages, roles, foundations, Reliquary, reserves, oath, era | **Tested** | Durable state inventory [CR pp. 36-41](chronicle.md#durable-state-to-preserve); persistent changes [NF pp. 7, 9, 12](new-foundations-delta.md#campaign-structure) | Aggregate/card-location invariants are tested in `model/DomainValidationSuite.scala`, `model/CardIndexSuite.scala`, `model/PlayerSetupStateSuite.scala`, and Atlas behavior in `model/WorldModelSuite.scala`. This proves in-memory representation/invariants, not Chronicle rules or persistence. `CardIndex` is explicitly derived and not serialized. | Chronicle transitions must establish and preserve these invariants. |
| Full campaign serialization and migration | **Partial** | Persistence requirements [CR pp. 36-41](chronicle.md#durable-state-to-preserve) | Versioned authoritative streams, schema migrations, reload, and durable adapters are tested for the current game. No Chronicle/post-game event vocabulary or cross-version campaign migration exists. `CardIndex` remains derived. | Define Chronicle events, archival boundaries, and campaign migration policy. |
| Sun task | **Blocked** | [CR p. 36](chronicle.md#1-the-sun) | `ChronicleTask.Sun` and a pending Chronicle shape exist only. | Legacy goals/powers B5; role-sensitive ties F7; absent actor policy F10. |
| Throne task | **Blocked** | [CR p. 37](chronicle.md#2-the-throne) | State can represent roles, sites, relics, Reliquary, scores and oath goal; no task behavior exists. | Edifice/legacy data B5; era ties F7; component limits F5. |
| World task | **Unimplemented** | [CR p. 38](chronicle.md#3-the-world) | World/discard, adviser and Dispossessed containers exist and are indexed; no redistribution/choice behavior exists. | Hidden choice policy F4. |
| Beacon task | **Blocked** | [CR p. 39](chronicle.md#4-the-beacon) | Suited reserves, starting advisers and foundation alteration sources exist; no task behavior exists. | Exact legacy/foundation data B2/B5; hidden choice F4. |
| Stars task and storage | **Partial** | [CR pp. 40-41](chronicle.md#5-the-stars) | Tested Atlas helpers can add/remove ordered sites and preserve an encountered Empire divider (`model/WorldModelSuite.scala`). No winner-site archive/consolidation, cleanup, narrative or full storage transition exists. | Exact site/card retention rules B5; component limits F5. |
| Era scoring and reset | **Unimplemented** | [CR pp. 5, 37](chronicle.md#persistence-model); era is new [NF p. 7](new-foundations-delta.md#campaign-structure) | Positive target/non-negative score state exists in `model/Game.scala`; no scoring, transfer, endpoint or reset behavior exists. | Era tie/product policy F7 and joining-player policy from CR p. 5. |

## Hidden information, consent, and determinism

| Rule area | Status | Normative source / NF change | Implementation and test evidence | Dependencies / unresolved ambiguity |
|---|---|---|---|---|
| Public/private visibility, owner peek/share, facedown advisers/relics and secret choices | **Partial** | [CR p. 42](rules-reference.md#rules-precedence-and-timing); facedown relics [NF p. 14](new-foundations-delta.md#actions) | Setup adviser choices and pending Search cards use authenticated player-scoped projections; other players and public scopes receive no identities or controls, and route errors are generic. `CardIndex` and privileged durable events are not public APIs. General reveal/share/audit and relic peeks remain absent. | Broader product visibility/reveal policy F4. |
| “Enemies cannot” consent enforcement | **Blocked** | [CR p. 43](rules-reference.md#rules-precedence-and-timing); explicit change [NF p. 16](new-foundations-delta.md#empire-and-other-changes) | No consent prompt, waiver or timing behavior exists. | Deterministic timeout/auto-consent policy F3; power corpus B1. |
| Randomness, dice, shuffles and random winner | **Partial** | [CR p. 19](rules-reference.md#empire-endings), [p. 20](rules-reference.md#search---2-to-4-supply), [pp. 22-23](rules-reference.md#campaign---2-supply), [p. 25](rules-reference.md#recover---1-supply), and [pp. 30, 42](rules-reference.md#rules-precedence-and-timing) | Search uses an injectable server-owned prepared-draw port and records the exact outcome in v4; replay never redraws and rejects source disagreement. Dice, shuffles outside setup, and random winners remain absent. | General seed/outcome policy F6; unlimited dice/limited pieces F5. |
| Simultaneous ordering and decision audit | **Partial** | [CR p. 42](rules-reference.md#rules-precedence-and-timing) | Search records a stable decision ID and the turn player's exact non-kept discard order. Other simultaneous decisions remain unimplemented. | General choice authority/logging policy F2. |

## Cross-cutting New Foundations changes

The NF booklet is change guidance, not a separate authority. Most changes map to
rows above. The remaining cross-cutting items are:

| NF-specific area | Status | Source | Evidence / gap | Dependency |
|---|---|---|---|---|
| Physical bandits rule empty sites and refill after actions | **Unimplemented** | [NF p. 11](new-foundations-delta.md#map-and-pieces); normative CR p. 15 | `ForceKind.Bandit` exists; no refill/rule transition. | Action boundary F1. |
| Hinterward/Cradleward directions and ordered Atlas storage mechanics | **Tested** | [NF pp. 12, 16](new-foundations-delta.md#empire-and-other-changes); normative CR pp. 9, 45 | `MapState.hinterward/cradleward` and ordered Atlas helpers are tested in `model/WorldModelSuite.scala`. This row covers direction/order mechanics only, not setup or Chronicle geography transitions. | None for the bounded ordering behavior. |
| Cradlemost/Hintermost vocabulary and full Atlas geography use | **Unimplemented** | [NF pp. 12, 16](new-foundations-delta.md#empire-and-other-changes); normative CR pp. 9, 45 | No explicit Cradlemost/Hintermost selector or setup/Chronicle rule consumes the full vocabulary. | Define tie/selection behavior where multiple sites share the relevant region. |
| Edifices are denizens; ruined edifices do not block Trade/Muster | **Partial** | [NF p. 16](new-foundations-delta.md#empire-and-other-changes); normative CR pp. 24, 31 | `EdificeState` occupies the site-denizen type and card index; Trade/Muster do not exist, so the exception is unenforced. | Component data B5. |
| Bury semantics | **Unimplemented** | [NF p. 16](new-foundations-delta.md#empire-and-other-changes); normative CR glossary/fine print | Decks and card locations exist; no bury operation exists. | Card identity/location plus action transaction behavior F1. |
| All-or-nothing migration/component replacement | **Not runtime gameplay** | [NF pp. 1-6](new-foundations-delta.md#expansion-boundary-and-physical-migration) | Catalog ruleset/version identity is checked by `catalog/CatalogLoader.scala` and setup/wire tests. Physical box migration is documentation/operator scope. | External conversion guidance and complete component corpus were not ingested. |

## Upcoming source-cited work

1. **Rest plus phase/round advancement.** Use CR pp. 17-18. The tested Supply
   projection gives this slice a strong starting point; add resource return,
   secret reveal, refresh, phase order and active-player/round changes. Resolve
   F1/F2 so later actions share the same transaction semantics.
2. **Muster and Trade.** Use CR p. 24 and NF p. 13. These establish access,
   empty-card costs, adviser matching, resource movement, limited warbands and
   the NF yield/cost changes. Trade remains blocked until B3 is authoritative.
3. **Oathkeeper/Usurper state-based victory.** Use CR pp. 16-17, 19 and NF
   p. 16. Implement only after the action boundary is stable; it supplies the
   first end-to-end game result without Campaign complexity. Resolve F7.
4. **Chronicle Stars storage before the full Chronicle.** Use CR pp. 40-41.
   Build on tested Atlas ordering to persist a completed fixture, then add
   World/Beacon, Sun, and Throne once component/legacy data is available.

Campaign/raid should follow these slices rather than lead them: it simultaneously
depends on powers, target legality, hidden cards, dice, ordering, losses,
after-action checks and victory.

## Maintenance checklist

- Recheck `docs/rules/rules-reference.md`, `chronicle.md`,
  `new-foundations-delta.md`, and `ambiguities.md` when source ingestion changes.
- Require an executable transition/handler before moving **Unimplemented** to
  **Partial**; require focused automated evidence before moving to **Tested**.
- Record the narrow behavior proved by a test. Do not promote an entire row
  because a state constructor, decoder, or similarly named type is tested.
- Preserve both CR and NF citations where NF explicitly changed the behavior.
- Add unresolved source conflicts to `ambiguities.md`; link them here rather
  than silently choosing an interpretation.
