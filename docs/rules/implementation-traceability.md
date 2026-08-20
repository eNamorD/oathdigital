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
| Event encoding, replay and application orchestration | **Tested** | Engineering support, not a rulebook statement | `GameEventWire`, `GameApplicationService`, replay, and optimistic repository contracts support the current mixed v2-v6 setup/gameplay stream. Tests cover malformed history, replay failures, conflicts, reload, and private projections. | Before public release, formats may change in place, but replay correctness and redaction remain required. |
| Durable event repository/database adapter | **Tested** | Engineering support, not a rulebook statement | `HsqldbEventStreamRepository` stores atomic ordered batches, performs schema migrations, rejects conflicts, and reconstructs streams after close/reopen. | Operational backup and production deployment remain outside game rules. |

## Turn and phase flow

| Rule area | Status | Normative source / NF change | Implementation and test evidence | Dependencies / unresolved ambiguity |
|---|---|---|---|---|
| Round order and Wake → Act → Rest transitions | **Tested (bounded)** | [CR p. 17](rules-reference.md#round-and-turn-flow) | `gameplay/phases/Wake.scala` and `Rest.scala` implement explicit phase transitions, setup-first-player rotation, round advancement, per-turn cleanup, and entry into the next Wake. `RestSuite` and application/reload tests validate the transition. | Round-eight ending resolution, Imperials, and nested action fixed points remain deferred. |
| Wake victory checks, Wake powers, Take Wealth | **Tested (bounded)** | [CR p. 17](rules-reference.md#wake); power identity/timing CR pp. 28, 31; Take Wealth site context [NF p. 11](new-foundations-delta.md#map-and-pieces) | `gameplay/phases/Wake.scala` transfers one loose favor/secret and records stable timing/source/power references. Enemy/resource/per-site-use legality and private controls share the typed query pipeline in `gameplay/RuleResolution.scala`. `StateBasedEvaluation` handles the bounded Oathkeeper/Usurper Wake path, while a merely revealed Vision does not block play. Tests cover Wake, state-based evaluation, wire, and application behavior. | Generic powers, River movement, Vision victory, and non-Supremacy victory remain deferred. Optional unsupported Wake powers do not block End Wake. |
| Act action loop and Supply spending | **Tested (bounded)** | [CR p. 17](rules-reference.md#act) | Travel and Search spend typed Supply and return to action selection; the player may explicitly enter Rest. Common Act lifecycle checks are action-neutral, while each action owns its supported-state validation. | Additional actions and nested action fixed points remain deferred (F1). |
| Reusable public board-target selection | **Tested** | Interaction support for Travel [CR p. 21](rules-reference.md#travel---1-to-4-supply-before-modifiers), Campaign [CR pp. 22-23](rules-reference.md#campaign---2-supply), and Economy [CR p. 24](rules-reference.md#muster---1-supply) | `GameProjection` and `GameHttpWire` project typed site, site-card, adviser, and relic refs with server-authorized candidates, prompts, cardinality, required targets, details, and optional force-formation facts. Scala.js action-first selection implements ordinary single choice, persistent multi-choice/confirm, and context-invalidated Campaign target, formation, and per-site placement drafts. Setup, Travel, Muster, both Trade modes, Campaign target selection/force/placement, strict decode, redaction, keyboard/touch behavior, and future adviser/relic hooks are tested. | Player-defender and Raid target families remain deferred. |
| Rest powers, token return, secret reveal, Supply refresh | **Tested (bounded)** | [CR p. 18](rules-reference.md#rest) | `gameplay/phases/Rest.scala` handles the exile-only, unaltered first-game profile: controlled denizen/relic resources return, facedown secrets reveal, the tested Exile Supply table refreshes, and v5 events validate replay facts. Application, wire, projection, HTTP and frontend suites cover persistence and reload. | Optional component Rest powers, Imperials/Citizens, and round-eight ending resolution remain deferred (B1/B3). |
| After-action fixed-point checks: bandit refill and title transfer | **Tested (bounded)** | [CR pp. 15-16](rules-reference.md#core-state-and-terminology) | `OathRules.completeAction` records replay-validated `BanditsRefilled` facts before the existing fixed-profile Supremacy evaluation. Campaign tests cover zero placement, refill ordering, and tamper rejection; existing StateBasedEvaluation regressions cover title behavior. | Nested/action-granting powers still need a general fixed-point policy F1. |

## Major and minor actions

| Rule area | Status | Normative source / NF change | Implementation and test evidence | Dependencies / unresolved ambiguity |
|---|---|---|---|---|
| Search | **Tested (bounded)** | [CR p. 20](rules-reference.md#search---2-to-4-supply) and simultaneous ordering [CR p. 42](rules-reference.md#rules-precedence-and-timing) | [bounded-search.md](../architecture/bounded-search.md) documents the exile-only, unaltered-Foundation procedure. `gameplay/actions/Search.scala` implements costs, ordered draw/early Vision stop, v4 durable pending/completion events, ordered next-region discards, typed site/adviser restrictions, favor gain, owner-only projection, HTTP intents, replay and Scala.js controls. `SearchSuite`, wire/application/server/frontend suites cover the slice. | Individual component powers, altered Foundations, Imperials, later turns and generic after-action checks remain deferred. |
| Travel | **Tested (bounded)** | [CR p. 21](rules-reference.md#travel---1-to-4-supply-before-modifiers); site powers CR p. 31 and NF p. 11; consent timing CR p. 43 | `gameplay/actions/Travel.scala` implements first-turn exile-only normal Travel with the region matrix and Supply debit. Coast/Island/Mountain/Pass activate through the exact-ID typed registry documented in [rule-resolution.md](../architecture/rule-resolution.md). Command, deterministic replay, private legal-destination projection, and destination-selection UI share resolution. Tests cover registry precedence, engine, v2/v3 wire, application reload, actor derivation/trust boundaries, projection and frontend behavior. | River, consent workflow, generic card/relic/edifice/legacy/Foundation modifiers, later turns and Rest remain deferred. Unsupported relevant handlers and modifier-bearing states fail typed rather than being ignored. |
| Campaign: Conquest/Raid targeting and battle resolution | **Tested (multi-site bandit/player Conquest; generic attacker/defender plan stages)** | [CR pp. 22-23, 29](rules-reference.md#campaign---2-supply); Pass targeting [CR pp. 21, 31]; facedown plan reveal and split/ordering/discard changes [NF p. 13](new-foundations-delta.md#actions) | Registered handlers author generic side-scoped options, costs, and effects; Campaign orchestration and projection contain no individual plan mechanics. Outriders and Brass Army cover ordered attacker plans, the title +1/+2 covers an owner-authorized player-defender decision, and Watchdog covers deterministic cost-free bandit use. Attack randomness occurs only after both player plan windows. Replay re-resolves handler facts and losing-force policies before applying them. Multi-site targeting, force, losses, placement, refill, title transfer, redaction, and empty attack pools remain covered in `architecture/bounded-campaign.md`. | Additional attacker/defender/bandit plan handlers, Raid, Empire defenders, and printed alternate loss powers remain. |
| Muster | **Tested (bounded)** | [CR p. 24](rules-reference.md#muster---1-supply); variable yield [NF p. 13](new-foundations-delta.md#actions) | `gameplay/actions/Economy.scala` validates an empty denizen at the pawn site, spends 1 Supply/favor, gains 1 + matching faceup advisers subject to the finite 14-warband supply, and records replay-validated v6 events. Commands, scoped projections, HTTP intents, persistence and Scala.js controls share this path; `EconomySuite` covers base/NF yield, limits, rejection, tampering and redaction. | Relevant unimplemented Economy powers reject through typed rule resolution; nested/action-granting power boundaries remain F1. |
| Trade | **Tested (bounded)** | [CR p. 24](rules-reference.md#trade---1-supply); secret cost changed [NF p. 13](new-foundations-delta.md#actions) | The shared Economy module implements favor yield `1 + matches` capped by the suit bank and secret yield equal to matches, including zero; NF secret trading places one favor and burns one. Authoritative v6 events validate source/suit/cost/yield/resource movement on replay and persist/reload through the existing journal. | Relevant unimplemented Economy powers reject explicitly; shared-secret physical inventory is not modeled and remains under F5. |
| Forge | **Blocked** | [CR p. 25](rules-reference.md#forge---1-supply); new action [NF p. 14](new-foundations-delta.md#actions) | Site catalog fields include forge requirements and are decoded/tested in `catalog/CatalogLoaderSuite.scala`; no ruling, payment, denizen-token or relic-draw behavior exists. | Verified site/component data B5; action boundary F1. |
| Recover | **Tested (bounded)** | [CR p. 25](rules-reference.md#recover---1-supply); redesigned [NF p. 14](new-foundations-delta.md#actions) | `gameplay/actions/Recover.scala` implements the exile-only, unaltered-Foundation procedure: each Supply payment records two typed defense-die faces, accumulated shields are multiplied by every doubler, unsuccessful rolls may add dice or stop, and success creates an owner-only inline choice of exactly one facedown site relic. V7 events replay exact costs/results without rerolling; application randomness is injectable; authenticated intents, projections, persistence, and Scala.js controls share the authoritative validator. `RecoverSuite` and codec coverage exercise boundaries, tampering, redaction, and transfer. | Recover-related denizen, relic, edifice, legacy, and Foundation modifiers remain deferred and reject explicitly when active. Nested/action-granting power boundaries remain F1. |
| Challenge and banner resource actions | **Blocked** | [CR pp. 26, 32](rules-reference.md#challenge---1-supply); new action/banner changes [NF pp. 12, 15](new-foundations-delta.md#banners) | Banner face/holder/resource state is modeled and face identity is tested in `model/WorldModelSuite.scala`; no challenge or ribbon behavior exists. | Exact banner faces/ribbons B4. |
| Core minor actions: adviser play/discard, powers, relic peek, warband move, negotiation, self-exile, Imperial transfer | **Blocked** | [CR p. 27 and pp. 32, 34-35](rules-reference.md#minor-actions); negotiation/exile changes [NF p. 15](new-foundations-delta.md#actions) | `PendingProcedure.Negotiation` and required state containers exist; no minor-action transitions exist. | Card powers B1; hidden information F4; component limits F5; Empire behavior. |
| Power framework, precedence and timing | **Partial** | [CR pp. 28-30, 42](rules-reference.md#rules-precedence-and-timing) | `RuleResolution.scala` provides typed sources, explicit handler registration, deterministic ordering, blocks, cost changes, decisions, and unsupported outcomes. Travel uses it for Coast/Island/Mountain/Pass; Take Wealth shares typed legality queries. | Most component handlers, simultaneous ordering F2, and first-game citizenship policy F8 remain. |

## Campaign, victory, and Empire

| Rule area | Status | Normative source / NF change | Implementation and test evidence | Dependencies / unresolved ambiguity |
|---|---|---|---|---|
| Oathkeeper qualification, transfer, Usurper limiter and Wake victory | **Tested (bounded)** | [CR pp. 16-17, 19](rules-reference.md#oathkeeper-and-usurper); Protection excludes banners [NF p. 16](new-foundations-delta.md#empire-and-other-changes) | `gameplay/StateBasedEvaluation.scala` is the single fixed-profile path for Supremacy checks after completed actions and Usurper checks on entry to Wake. It records replay-validated title/flip/victory events; a displaced holder facing tied leaders receives a durable owner-scoped recipient decision. `RestCompleted` records the limiter and releases it on entry to round four. Tests cover unique transfer, holder retention, no invented initial tie winner, authorized tied-recipient resolution/replay, limiter release, and Usurper victory. | Protection, banners, altered Foundations, Imperials, Visions, round-eight endings, Chronicle, and non-Supremacy tie contexts remain excluded. |
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
| Randomness, dice, shuffles and random winner | **Partial** | [CR p. 19](rules-reference.md#empire-endings), [p. 20](rules-reference.md#search---2-to-4-supply), [pp. 22-23](rules-reference.md#campaign---2-supply), [p. 25](rules-reference.md#recover---1-supply), and [pp. 30, 42](rules-reference.md#rules-precedence-and-timing) | Search records server-prepared draws; Recover and bounded Campaign record injected physical die faces. Replay validates recorded counts, face semantics, scores, losses, and outcomes without rerolling. | Shuffles outside setup and random winners remain; general seed policy F6 remains deferred. |
| Simultaneous ordering and decision audit | **Partial** | [CR p. 42](rules-reference.md#rules-precedence-and-timing) | Search records a stable decision ID and the turn player's exact non-kept discard order. Other simultaneous decisions remain unimplemented. | General choice authority/logging policy F2. |

## Cross-cutting New Foundations changes

The NF booklet is change guidance, not a separate authority. Most changes map to
rows above. The remaining cross-cutting items are:

| NF-specific area | Status | Source | Evidence / gap | Dependency |
|---|---|---|---|---|
| Physical bandits rule empty sites and refill after actions | **Tested (bounded)** | [NF p. 11](new-foundations-delta.md#map-and-pieces); normative CR p. 15 | Occupied Bandit forces derive `SiteRuler.Bandits`; `SiteForces.Empty` is unruled. Completed actions now record deterministic capacity refills before Supremacy evaluation, with Campaign zero-placement and replay-tampering coverage. | Nested/action-granting powers still require fixed-point formalization F1. |
| Hinterward/Cradleward directions and ordered Atlas storage mechanics | **Tested** | [NF pp. 12, 16](new-foundations-delta.md#empire-and-other-changes); normative CR pp. 9, 45 | `MapState.hinterward/cradleward` and ordered Atlas helpers are tested in `model/WorldModelSuite.scala`. This row covers direction/order mechanics only, not setup or Chronicle geography transitions. | None for the bounded ordering behavior. |
| Cradlemost/Hintermost vocabulary and full Atlas geography use | **Unimplemented** | [NF pp. 12, 16](new-foundations-delta.md#empire-and-other-changes); normative CR pp. 9, 45 | No explicit Cradlemost/Hintermost selector or setup/Chronicle rule consumes the full vocabulary. | Define tie/selection behavior where multiple sites share the relevant region. |
| Edifices are denizens; ruined edifices do not block Trade/Muster | **Tested (bounded)** | [NF p. 16](new-foundations-delta.md#empire-and-other-changes); normative CR pp. 24, 31 | `EconomyTargetRef` preserves denizen/edifice identity through commands, both HTTP boundaries, scoped projections, labeled Scala.js controls, v6 persistence and replay. Ruined edifices use base Economy behavior; the intact Hallowed Spring handler is selected from its actual face and rejects explicitly as unsupported. | Executing intact edifice Economy modifiers remains B5. |
| Bury semantics | **Unimplemented** | [NF p. 16](new-foundations-delta.md#empire-and-other-changes); normative CR glossary/fine print | Decks and card locations exist; no bury operation exists. | Card identity/location plus action transaction behavior F1. |
| All-or-nothing migration/component replacement | **Not runtime gameplay** | [NF pp. 1-6](new-foundations-delta.md#expansion-boundary-and-physical-migration) | Catalog ruleset/version identity is checked by `catalog/CatalogLoader.scala` and setup/wire tests. Physical box migration is documentation/operator scope. | External conversion guidance and complete component corpus were not ingested. |

## Upcoming source-cited work

1. **Chronicle Stars storage before the full Chronicle.** Use CR pp. 40-41.
   Build on tested Atlas ordering to persist a completed fixture, then add
   World/Beacon, Sun, and Throne once component/legacy data is available.

Campaign expansion should proceed handler-first from the tested multi-site
bandit/player Conquest baseline: implement defender choices and a small
modifier/cost set next. Raid continues to wait for hidden-card disposal,
banners, and relocation.

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
