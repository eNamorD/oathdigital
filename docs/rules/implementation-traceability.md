# Rulebook implementation traceability

Last reviewed: 2026-08-09

## Core minor actions

- Combined Rulebook p. 27: Act-phase, 0-Supply facedown adviser play/discard, relic peek/reveal, and warband movement use the typed `MinorActions` boundary. Only board-to-site movement requires rule; site-to-board movement must leave the last actor warband.
- Combined Rulebook pp. 20 and 28: adviser play enforces faceup Search placement restrictions, Homeland replacement, next-region discard, and normal site-play favor. Unsupported printed `When Played` powers block by audited handler identity.
- New Foundations p. 14: held relics remain facedown until revealed. Site peeks are durable owner-private knowledge while each exact relic remains at that site.

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

## Phase 3 playable-shell audit

This milestone is infrastructure and reviewed fallback, not implementation of
the listed component powers. All rows use the pinned full catalog vocabulary;
unknown vocabulary remains an error.

| Boundary | Implemented behavior retained | Reviewed pre-alpha fallback |
|---|---|---|
| Travel | Base costs and automatic Coast/Island/Mountain/Pass topology | Other accessible audited handlers cannot become selectable or block |
| Search | Base draw, private decision, placement, persistence | Start modifiers are classified; reached When Played triggers record diagnostics |
| Campaign | Base Conquest/Raid and Outriders, Brass Army, Watchdog, title plans | Other audited mandatory/plan rules are ignored without collapsing later battle-plan windows |
| Muster / Trade | Base New Foundations yields, costs, target legality, explicit confirm | Optional audited Economy modifiers are omitted from options |
| Forge | Base assignments, transfer, pending state and replay | Audited vocabulary has no executable optional modifier; shell still revalidates final order |
| Recover | Base roll, pending choice and replay | Reviewed mandatory modifiers record durable diagnostics |
| Wake / Rest / action boundary | Existing phase, title, Vision, refill and ending progression | Reached banner, Foundation and Rest triggers record durable diagnostics |
| When Played | Existing placement restrictions and favor movement | Reviewed triggers record diagnostics instead of blocking play |

`MajorActionPowerShell.scala`, application preview/command mapping, lifecycle
event codec, server routes, and `frontend/ModifierSelectionState.scala` are the
implementation evidence. Focused gameplay, wire, application replay,
authenticated/development route, and Scala.js suites cover classification,
ordering, stale revalidation, redaction, draft clearing, and replay tampering.
`GameApplicationServiceSuite` additionally completes an all-Exile powered game
through round-eight victory and reopens/replays after each Rest.

## Setup

| Rule area | Status | Normative source / NF change | Implementation and test evidence | Dependencies / unresolved ambiguity |
|---|---|---|---|---|
| First-game common setup, map population, decks, banks, tracks and player wealth | **Tested (bounded)** | [CR pp. 6-7](rules-reference.md#very-first-game-only); changed/randomized by [NF p. 8](new-foundations-delta.md#campaign-structure) | `gameplay/setup/FirstGameSetup.scala` constructs the exile-only introductory game from a recorded server-derived plan. Setup, wire, application, persistence, server, and frontend suites cover replay and presentation. | Legacies, Imperial roles, altered Foundations, and later-game restoration remain excluded. |
| Later-game restoration from campaign/Atlas | **Unimplemented** | [CR pp. 8-9](rules-reference.md#later-games); Atlas emphasis [NF p. 12](new-foundations-delta.md#map-and-pieces) | Atlas storage/removal helpers are tested in `model/WorldModelSuite.scala`; no setup command applies the restoration procedure. | Foundation faces B2; Chronicle-produced stored state must exist first. |
| Six foundation setup effects and world-deck construction | **Blocked** | [CR pp. 10-12](rules-reference.md#later-games); mutable foundations [NF p. 9](new-foundations-delta.md#campaign-structure) | Foundation face/source state and reviewed catalog records exist; no typed handler executes a foundation. | Executable Foundation handlers B2; catalog text must not be mistaken for behavior. |
| Ordered pawn placement on an eight-site 2/3/3 map | **Tested** | [CR pp. 7, 13](rules-reference.md#setup) | `gameplay/setup/FirstGameSetup.scala` enforces compatible catalog, unique participants/lineages/sites, eight known sites, participant order, in-play destinations, completion and replay. | Imperial placement restrictions remain absent. |
| Starting adviser choice and pawn placement | **Tested (bounded)** | [CR pp. 7, 13](rules-reference.md#setup) | The first-game flow records each Exile's private adviser choice, enforces ownership/order, and then places pawns. Player-scoped projections expose only the active player's choices. | Imperial placement and reveal rules remain unimplemented; hidden sharing policy F4. |
| Event encoding, replay and application orchestration | **Tested** | Engineering support, not a rulebook statement | `GameEventWire`, `GameApplicationService`, replay, and optimistic repository contracts support the single current setup/gameplay event stream. Tests cover malformed history, replay failures, conflicts, reload, and private projections. | Before public release, formats may change in place, but replay correctness and redaction remain required. |
| Durable event repository/database adapter | **Tested** | Engineering support, not a rulebook statement | `HsqldbEventStreamRepository` stores atomic ordered batches, performs schema migrations, rejects conflicts, and reconstructs streams after close/reopen. | Operational backup and production deployment remain outside game rules. |

## Turn and phase flow

| Rule area | Status | Normative source / NF change | Implementation and test evidence | Dependencies / unresolved ambiguity |
|---|---|---|---|---|
| Round order and Wake → Act → Rest transitions | **Tested (bounded)** | [CR p. 17](rules-reference.md#round-and-turn-flow) | `gameplay/phases/Wake.scala` and `gameplay/phases/Rest.scala` implement explicit phase transitions, setup-first-player rotation, round advancement, per-turn cleanup, and entry into the next Wake. `RestSuite` and application/reload tests validate the transition. | Imperials and nested action fixed points remain deferred. |
| Wake victory checks, Wake powers, Take Wealth | **Tested (bounded)** | [CR p. 17](rules-reference.md#wake); power identity/timing CR pp. 28, 31; Take Wealth site context [NF p. 11](new-foundations-delta.md#map-and-pieces) | `gameplay/phases/Wake.scala` transfers one loose favor/secret and records stable timing/source/power references. Enemy/resource/per-site-use legality and private controls share the typed query pipeline in `gameplay/RuleResolution.scala`. `gameplay/StateBasedEvaluation.scala` handles the bounded Oathkeeper, Usurper, and Vision paths. Tests cover Wake, state-based evaluation, wire, and application behavior. | Generic powers, River movement, Imperials, and altered Foundations remain deferred. Optional unsupported Wake powers do not block End Wake. |
| Act action loop and Supply spending | **Tested (bounded)** | [CR p. 17](rules-reference.md#act) | Implemented major/minor actions spend typed Supply where required and return to action selection; the player may explicitly enter Rest. Common lifecycle checks are action-neutral, while each action owns its supported-state validation. | Component-granted actions and nested action fixed points remain deferred (F1). |
| Reusable public board-target selection | **Tested** | Interaction support for Travel [CR p. 21](rules-reference.md#travel---1-to-4-supply-before-modifiers), Campaign [CR pp. 22-23](rules-reference.md#campaign---2-supply), and Economy [CR p. 24](rules-reference.md#muster---1-supply) | Shared projection DTOs and `application/LegalActionProjector.scala` expose typed site, site-card, player, adviser, relic, pawn, and banner candidates with prompts, cardinality, mandatory targets, details, and force-formation facts. Scala.js interaction-state classes implement single/multi-choice, Campaign formation/placement, and context invalidation. Setup, Travel, Economy, Conquest, Raid, Challenge, strict decode, redaction, keyboard, and touch behavior are tested. | New target families must extend the shared typed reference rather than add action-specific DOM legality. |
| Rest powers, token return, secret reveal, Supply refresh | **Tested (bounded)** | [CR p. 18](rules-reference.md#rest) | `gameplay/phases/Rest.scala` handles the exile-only, unaltered first-game profile: controlled denizen/relic resources return, facedown secrets reveal, the tested Exile Supply table refreshes, and current events validate replay facts. Application, wire, projection, HTTP and frontend suites cover persistence and reload. | Optional component Rest powers and Imperials/Citizens remain deferred (B1/B3). |
| After-action fixed-point checks: bandit refill and title transfer | **Tested (bounded)** | [CR pp. 15-16](rules-reference.md#core-state-and-terminology) | `OathRules.completeAction` records replay-validated `BanditsRefilled` facts before the existing fixed-profile Supremacy evaluation. Campaign tests cover zero placement, refill ordering, and tamper rejection; existing StateBasedEvaluation regressions cover title behavior. | Nested/action-granting powers still need a general fixed-point policy F1. |

## Major and minor actions

| Rule area | Status | Normative source / NF change | Implementation and test evidence | Dependencies / unresolved ambiguity |
|---|---|---|---|---|
| Search | **Tested (bounded)** | [CR p. 20](rules-reference.md#search---2-to-4-supply) and simultaneous ordering [CR p. 42](rules-reference.md#rules-precedence-and-timing) | [bounded-search.md](../architecture/bounded-search.md) documents the exile-only, unaltered-Foundation procedure. `gameplay/actions/Search.scala` implements costs, ordered draw/early Vision stop, durable pending/completion events, ordered next-region discards, typed site/adviser restrictions, favor gain, owner-only projection, HTTP intents, replay and Scala.js controls. `SearchSuite`, wire/application/server/frontend suites cover the slice. | Individual component powers, altered Foundations, Imperials, later turns and generic after-action checks remain deferred. |
| Travel | **Tested (bounded)** | [CR p. 21](rules-reference.md#travel---1-to-4-supply-before-modifiers); site powers CR p. 31 and NF p. 11; consent timing CR p. 43 | `gameplay/actions/Travel.scala` implements first-turn exile-only normal Travel with the region matrix and Supply debit. Coast/Island/Mountain/Pass activate through the exact-ID typed registry documented in [rule-resolution.md](../architecture/rule-resolution.md). Command, deterministic replay, private legal-destination projection, and destination-selection UI share resolution. Tests cover registry precedence, engine, current event wire, application reload, actor derivation/trust boundaries, projection and frontend behavior. | River, consent workflow, generic card/relic/edifice/legacy/Foundation modifiers, later turns and Rest remain deferred. Unsupported relevant handlers and modifier-bearing states fail typed rather than being ignored. |
| Campaign: Conquest/Raid targeting and battle resolution | **Tested (base multi-site Conquest and Raid; generic attacker/defender plan stages)** | [CR pp. 22-23, 29](rules-reference.md#campaign---2-supply); Pass targeting [CR pp. 21, 31]; Raid split, facedown disposal, and pre-defense sacrifice [NF p. 13](new-foundations-delta.md#actions); banner defense/ribbons [NF p. 12](new-foundations-delta.md#banners) | Registered handlers author generic side-scoped options, costs, and effects; orchestration and projection contain no individual plan mechanics. Raid uses canonical pawn-first typed targets, printed relic/banner defense, defender board force, registered losses, and a kind-aware co-location origin for attacker losses. Its durable success record distinguishes People's Favor bank returns from the exact Darkest Secret burn, appends ordinary facedown advisers in order to the next-region discard, returns the Conspiracy to the box, sends facedown relics to the Chronicle reliquary, burns defender favor, and authorizes owner-scoped non-Travel pawn relocation. Replay re-resolves target legality, pools, outcomes, policies, terminal facts, and relocation without rerolling; redaction hides identities and controls from other viewers. See `architecture/bounded-campaign.md`. | Additional attacker/defender/bandit, Raid, victory, defeat, and `At End` handlers remain explicitly deferred; their terminal windows are structural only. Empire defenders and printed alternate loss powers remain deferred. |
| Muster | **Tested (bounded)** | [CR p. 24](rules-reference.md#muster---1-supply); variable yield [NF p. 13](new-foundations-delta.md#actions) | `gameplay/actions/Economy.scala` validates an empty denizen at the pawn site, spends 1 Supply/favor, gains 1 + matching faceup advisers subject to the finite 14-warband supply, and records replay-validated events. Commands, scoped projections, HTTP intents, persistence and Scala.js controls share this path; `EconomySuite` covers base/NF yield, limits, rejection, tampering and redaction. | Relevant unimplemented Economy powers reject through typed rule resolution; nested/action-granting power boundaries remain F1. |
| Trade | **Tested (bounded)** | [CR p. 24](rules-reference.md#trade---1-supply); secret cost changed [NF p. 13](new-foundations-delta.md#actions) | The shared Economy module implements favor yield `1 + matches` capped by the suit bank and secret yield equal to matches, including zero; NF secret trading places one favor and burns one. Authoritative events validate source/suit/cost/yield/resource movement on replay and persist/reload through the existing journal. | Relevant unimplemented Economy powers reject explicitly; shared-secret physical inventory is not modeled and remains under F5. |
| Forge | **Tested (bounded)** | [CR p. 25](rules-reference.md#forge---1-supply); new action [NF p. 14](new-foundations-delta.md#actions) | `gameplay/actions/Forge.scala` implements the printed base action: authoritative rule and pawn-site checks, exact empty-denizen targets, printed resource assignments in any order, 1 Supply, and an application-prepared relic-deck-top transfer facedown to the actor. Typed pending state, durable events, replay validation, owner-only projection, application persistence/reload, HTTP route intents, and accessible Scala.js controls are covered by `ForgeSuite` and boundary suites. See `architecture/bounded-forge.md`. | The current handler vocabulary is explicitly audited as Forge-irrelevant and pinned by fingerprint; changed vocabulary blocks active component handlers until re-audited. Component-specific Forge behavior remains deferred; no `rulesText` interpretation. |
| Recover | **Tested (bounded)** | [CR p. 25](rules-reference.md#recover---1-supply); redesigned [NF p. 14](new-foundations-delta.md#actions) | `gameplay/actions/Recover.scala` implements the exile-only, unaltered-Foundation procedure: each Supply payment records two typed defense-die faces, accumulated shields are multiplied by every doubler, unsuccessful rolls may add dice or stop, and success creates an owner-only inline choice of exactly one facedown site relic. Recorded events replay exact costs/results without rerolling; application randomness is injectable; authenticated intents, projections, persistence, and Scala.js controls share the authoritative validator. `RecoverSuite` and codec coverage exercise boundaries, tampering, redaction, and transfer. | Recover-related denizen, relic, edifice, legacy, and Foundation modifiers remain deferred and reject explicitly when active. Nested/action-granting power boundaries remain F1. |
| Challenge and banner resource actions | **Tested (fixed first-game Mob and Wandering Flame faces)** | [CR pp. 26, 32](rules-reference.md#challenge---1-supply); new action/banner changes [NF pp. 12, 15](new-foundations-delta.md#banners) | Public typed banner state is authoritative. Challenge spends 1 Supply, projects only strict legal comparisons (faceup secrets only), enforces enemy co-location, deterministically returns Mob favor one at a time with leftmost ties, durably resolves owner-scoped Wandering Flame site ties, and atomically transfers the banner with a finite greater replacement. The two printed placement powers are typed 0-Supply minor actions. Campaign Raid and Challenge share banner distribution helpers and replay validates all recorded facts. See `architecture/bounded-banners.md`. | Altered banner faces, mutable Foundation selection, component Challenge powers, and other roles remain explicit typed blockers. |
| Core minor actions: adviser play/discard, relic peek, warband move, all-Exile negotiation | **Bounded implementation** | [CR p. 27 and pp. 32, 34-35](rules-reference.md#minor-actions); negotiation changes [NF p. 15](new-foundations-delta.md#actions) | `gameplay/actions/MinorActions.scala`, `gameplay/actions/Negotiation.scala`, current event records, owner-scoped knowledge and participant projections. Negotiation persists authored favor/relic/disclosure terms, resets consent on revision, and applies unanimous deals atomically. | Action/When Played powers B1; Citizenship/Grand Scepter, self-exile, Imperial transfer, remote and component-modified Negotiation remain deferred. |
| Power framework, precedence and timing | **Partial** | [CR pp. 28-30, 42](rules-reference.md#rules-precedence-and-timing) | `gameplay/RuleSourceIndex.scala` enumerates factual sources, `catalog/CatalogHandlerInventory.scala` fingerprints vocabulary, and `gameplay/RuleResolution.scala` provides explicit registration, deterministic ordering, typed outcomes, and unsupported failures. Owning action modules add narrower exact-ID registries where needed. | Most component handlers, simultaneous ordering F2, and first-game citizenship policy F8 remain. |

## Campaign, victory, and Empire

| Rule area | Status | Normative source / NF change | Implementation and test evidence | Dependencies / unresolved ambiguity |
|---|---|---|---|---|
| Oathkeeper qualification, transfer, Usurper limiter, Wake victory, and fixed all-Exile round endings | **Tested (bounded)** | [CR pp. 16-17, 19](rules-reference.md#oathkeeper-and-usurper); Protection excludes banners [NF p. 16](new-foundations-delta.md#empire-and-other-changes) | `gameplay/StateBasedEvaluation.scala` is the single fixed-profile path for all four Oathkeeper goals, Wake victory, round advancement, and round-eight War Exhaustion. It records replay-validated title, flip, round-end, and victory events; `RoundEnded` releases the limiter after round three. Round-eight Visionary eligibility retains the general three-Visions-drawn threshold and printed Vision priority. Rest-power relevance uses an exact handler set behind a complete catalog-family fingerprint, never rules-text inference. | Imperials, altered Foundations and banners, executable printed Rest powers, and Chronicle remain excluded. |
| Vision reveal/qualification/victory and Conspiracy | **Tested (bounded all-Exile)** | [CR pp. 16-17, 20, 27, 30](rules-reference.md#visions); Conspiracy changed [NF p. 16](new-foundations-delta.md#empire-and-other-changes) | [bounded-visions-and-conspiracy.md](../architecture/bounded-visions-and-conspiracy.md) documents the four fixed goal mappings, three-drawn gate, Wake ordering, zero-Supply reveal, Search integration, opaque relic targeting, banner ribbons, deterministic events, replay, scoped projection, transport, and Scala.js controls. | Altered Foundations, Empire Vision rules, alternate banner faces, and printed Vision/reveal modifiers remain deferred to registered handlers. |
| Rounds 5-8, War Exhaustion and no-Empire fallback | **Tested (bounded all-Exile)** | [CR p. 19](rules-reference.md#empire-endings) | `gameplay/phases/Rest.scala` and `gameplay/StateBasedEvaluation.scala` implement rounds 1-8, limiter release, printed Vision priority, Usurper/Oathkeeper precedence, and application-port-owned random fallback. `RestSuite` and `StateBasedEvaluationSuite` cover replay and eligibility. | Empire Stable Regime/Chancellor endings and altered Foundations remain deferred. |
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
| Era scoring and reset | **Unimplemented** | [CR pp. 5, 37](chronicle.md#persistence-model); era is new [NF p. 7](new-foundations-delta.md#campaign-structure) | Positive target/non-negative score state exists in `model/GameState.scala`; no scoring, transfer, endpoint or reset behavior exists. | Era tie/product policy F7 and joining-player policy from CR p. 5. |

## Hidden information, consent, and determinism

| Rule area | Status | Normative source / NF change | Implementation and test evidence | Dependencies / unresolved ambiguity |
|---|---|---|---|---|
| Public/private visibility, owner peek/share, facedown advisers/relics and secret choices | **Partial** | [CR p. 42](rules-reference.md#rules-precedence-and-timing); facedown relics [NF p. 14](new-foundations-delta.md#actions) | `GameProjector` chooses one scope; `GamePresentationProjector` and `PendingProcedureProjector` redact setup/Search decisions, adviser hands, relic peeks, Negotiation disclosures, and pending controls. Authenticated routes derive player scope; public/other-player projections omit identities. `CardIndex` and privileged events are not public APIs. | Broader reveal/share/audit policy F4 remains. |
| “Enemies cannot” consent enforcement | **Blocked** | [CR p. 43](rules-reference.md#rules-precedence-and-timing); explicit change [NF p. 16](new-foundations-delta.md#empire-and-other-changes) | No consent prompt, waiver or timing behavior exists. | Deterministic timeout/auto-consent policy F3; power corpus B1. |
| Randomness, dice, shuffles and random winner | **Partial** | [CR p. 19](rules-reference.md#empire-endings), [p. 20](rules-reference.md#search---2-to-4-supply), [pp. 22-23](rules-reference.md#campaign---2-supply), [p. 25](rules-reference.md#recover---1-supply), and [pp. 30, 42](rules-reference.md#rules-precedence-and-timing) | Application-owned ports prepare Search draws, Recover/Campaign dice, and the all-Exile War Exhaustion fallback. Events record canonical facts and replay validates them without rerolling. | General shuffle/seed policy outside implemented procedures remains deferred under F6. |
| Simultaneous ordering and decision audit | **Partial** | [CR p. 42](rules-reference.md#rules-precedence-and-timing) | Search records a stable decision ID and the turn player's exact non-kept discard order. Other simultaneous decisions remain unimplemented. | General choice authority/logging policy F2. |

## Cross-cutting New Foundations changes

The NF booklet is change guidance, not a separate authority. Most changes map to
rows above. The remaining cross-cutting items are:

| NF-specific area | Status | Source | Evidence / gap | Dependency |
|---|---|---|---|---|
| Physical bandits rule empty sites and refill after actions | **Tested (bounded)** | [NF p. 11](new-foundations-delta.md#map-and-pieces); normative CR p. 15 | Occupied Bandit forces derive `SiteRuler.Bandits`; `SiteForces.Empty` is unruled. Completed actions now record deterministic capacity refills before Supremacy evaluation, with Campaign zero-placement and replay-tampering coverage. | Nested/action-granting powers still require fixed-point formalization F1. |
| Hinterward/Cradleward directions and ordered Atlas storage mechanics | **Tested** | [NF pp. 12, 16](new-foundations-delta.md#empire-and-other-changes); normative CR pp. 9, 45 | `MapState.hinterward/cradleward` and ordered Atlas helpers are tested in `model/WorldModelSuite.scala`. This row covers direction/order mechanics only, not setup or Chronicle geography transitions. | None for the bounded ordering behavior. |
| Cradlemost/Hintermost vocabulary and full Atlas geography use | **Unimplemented** | [NF pp. 12, 16](new-foundations-delta.md#empire-and-other-changes); normative CR pp. 9, 45 | No explicit Cradlemost/Hintermost selector or setup/Chronicle rule consumes the full vocabulary. | Define tie/selection behavior where multiple sites share the relevant region. |
| Edifices are denizens; ruined edifices do not block Trade/Muster | **Tested (bounded)** | [NF p. 16](new-foundations-delta.md#empire-and-other-changes); normative CR pp. 24, 31 | `EconomyTargetRef` preserves denizen/edifice identity through commands, both HTTP boundaries, scoped projections, labeled Scala.js controls, current persistence and replay. Ruined edifices use base Economy behavior; the intact Hallowed Spring handler is selected from its actual face and rejects explicitly as unsupported. | Executing intact edifice Economy modifiers remains B5. |
| Bury semantics | **Unimplemented** | [NF p. 16](new-foundations-delta.md#empire-and-other-changes); normative CR glossary/fine print | Decks and card locations exist; no bury operation exists. | Card identity/location plus action transaction behavior F1. |
| All-or-nothing migration/component replacement | **Not runtime gameplay** | [NF pp. 1-6](new-foundations-delta.md#expansion-boundary-and-physical-migration) | Catalog ruleset/version identity is checked by `catalog/CatalogLoader.scala` and setup/wire tests. Physical box migration is documentation/operator scope. | External conversion guidance and complete component corpus were not ingested. |

## Remaining source-cited work

Empire, Citizenship, remaining component handlers, Chronicle storage, campaign
continuity, and Era scoring remain deferred after the bounded all-Exile rules
coverage documented above.

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
