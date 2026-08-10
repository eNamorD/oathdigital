# Oath Digital Roadmap

This is the project-level source of truth for planned work. The Main Thread
maintains priorities and status. Implementation work runs in separate Codex
tasks, and an item moves to Done only after its changes and verification have
been reviewed.

## Now

- [ ] **R2 — Extract gameplay modules and clean up runtime naming**
  - Follow [the gameplay module architecture](architecture/gameplay-modules.md):
    extract Wake, Travel, and Search, reduce the aggregate to routing, and
    delete `FirstTurnWake.scala` without changing behavior.
  - In a separate mechanical pass, rename runtime `FirstGame*` types that no
    longer describe setup while preserving v1-v4 event bytes, HTTP behavior,
    replay compatibility, and golden fixtures.
  - Complete the full integration gate before implementing additional rules.

## Next

- [ ] **L6c — Rest and turn advancement**
  - Implement Rest powers, resource return, secret reveal, Supply refresh,
    per-turn cleanup, player/round advancement, and the next player's Wake.

## Later

- [ ] **L6d — Bounded Economy action slice**
  - Implement Muster and Trade together in `actions/Economy.scala`, sharing
    denizen access and suit/adviser evaluation while retaining distinct typed
    commands and outcomes. Split them only if implemented complexity warrants
    it.
- [ ] **X6 — Complete production authentication and deployment**
  - [x] Provider-neutral users, OIDC identity links, memberships, digest-only
    sessions, shared HSQL lifecycle, membership authorization, authenticated
    projections/commands/bootstrap, and session-cookie/CSRF validation.
  - [ ] Add OIDC Authorization Code + PKCE, session issuance/rotation/logout,
    secure cookie-setting responses, and frontend login/session-expiry UX.
  - [ ] Add membership-management UX, rate limiting, audit logging, and the
    final non-loopback deployment gate. Until then, keep development routes
    loopback-only.
- [ ] **L3 — Licensed game assets with permanent visual fallbacks**
- [ ] **L4 — Saved-game browser and replay navigation**
- [ ] **L5 — Asynchronous accounts, invitations, and notifications**
- [ ] **L6 — Incremental implementation of remaining phases and rules**
- [ ] **L7 — Incremental synchronization transport**
  - Replace complete-snapshot polling with conditional responses, projection
    deltas, long polling, SSE, or another push transport when scale or latency
    justifies the added server lifecycle complexity.

## Done

- [x] Clone, inspect, and compile HRF and its Scala.js dependencies.
- [x] Establish the Oath Digital Scala build and baseline tests.
- [x] Define the core domain model and architecture boundaries.
- [x] Create and validate the initial component catalog and loader.
- [x] Add image-independent presentation and fallback models.
- [x] Implement the bounded setup command/event/replay slice.
- [x] Choose authoritative domain events and document the decision.
- [x] Add explicit, versioned setup-event serialization and compatibility tests.
- [x] Refactor the runtime catalog into five complete component families while
  retaining ingestion evidence and manual-review data separately.
- [x] Complete the HRF UI reuse spike and deliver an interactive Scala.js setup
  page whose displayed state is derived from authoritative-event replay.
- [x] Add the storage-neutral setup application service, in-memory repository,
  optimistic-concurrency contract, and typed failure handling.
- [x] Add source-cited rulebook implementation traceability without treating
  placeholder domain types as implemented rules.
- [x] Integrate the frontend with a server-authoritative bootstrap and command
  API while retaining an explicit browser-memory debug mode.
- [x] Add the file-backed HSQLDB event journal, schema upgrades, atomic event
  batches, optimistic concurrency, and close/reopen reconstruction.
- [x] Complete and present the exile-only first-game setup through Ready, with
  The World, player naming/color treatment, restart controls, and permanent
  image-independent fallbacks.
- [x] Integrate the reviewed printed-ID catalog and typed denizen placement
  restrictions as catalog `2026.08.03-pre3` / schema `1.1.0`.
- [x] Complete the batch integration gate, including independent diff review,
  catalog validation, clean JVM/Scala.js builds, database restart, persisted
  browser reload, and distinct-stream restart testing.
- [x] Complete X3 client synchronization and reconnect: persisted game/player
  URLs, explicit recovery from transport failures, stale-response generation
  guards, refresh-only conflict handling, and visibility-aware player-scoped
  snapshot polling keyed by authoritative `nextSequence`.
- [x] Complete X5 replay and concurrency hardening across v1/v2 compatibility,
  malformed and misidentified streams, exact replay-failure indexes, sequence
  integrity, concurrent creation/appends, atomic rollback, restart durability,
  and stale HTTP command handling.
- [x] Establish the deferred X6 security foundation through schema-v3
  digest-only sessions, membership-derived authorization, authenticated game
  routes, exact-origin CSRF protection, and deterministic HSQL close/reopen.
- [x] Complete L1 first-turn Wake through separate Take Wealth and End Wake
  commands, mixed v2/v3 authoritative replay, server and authenticated command
  boundaries, player-scoped UI controls, and the Act action-selection boundary.
- [x] Complete L2 board and fallback polish: detailed responsive site cards,
  HRF-style inactive-player waiting, valid-only Wake controls, semantic site
  interactions, and deterministic accessible visual fallbacks with no artwork.
- [x] Complete L6a bounded first-turn Travel through authoritative commands and
  events, replay, server-derived actor context, player-scoped projection, and
  destination-selection UI, including base region costs and mandatory Coast,
  Island, Mountain, and Pass behavior. Manually verified in the interactive UI.
- [x] Complete R1 typed runtime rule resolution with broad stable source
  identities, explicit catalog-handler registration, deterministic outcome
  ordering, safe unsupported-rule handling, a minimal decision boundary, and
  shared Travel and Take Wealth legality queries. Event wire formats and golden
  replay fixtures remain unchanged.
- [x] Complete L6b bounded Search with server-prepared deterministic draws,
  v4 authoritative pending/completion events, replay validation, regional and
  world sources, ordered discards, typed placement restrictions, owner-only
  pending-card projection, reconnect-safe UI controls, and unchanged v1-v3
  compatibility.

The combined milestone passes 188 JVM tests, 49 Scala.js tests, the Scala.js
linker, runtime-catalog validation, and a persisted three-player browser smoke
test through Take Wealth, End Wake, Act selection, responsive site rendering,
reload reconstruction, and disconnect/reconnect recovery.

## Coordination rules

- The Main Thread owns this file and prioritization.
- Each implementation item gets a separate Codex task with explicit file
  ownership and acceptance criteria.
- Parallel tasks must avoid editing the same files or packages.
- Tasks run focused checks while parallel work is active. They must not run
  `clean` against shared build outputs.
- After parallel tasks finish, the Main Thread reviews the combined diff and
  runs the complete build, catalog validation, and test suite once.
- Completion reports must include changed files, verification commands, test
  counts, unresolved risks, and the resulting commit.
