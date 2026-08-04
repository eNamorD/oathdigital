# Oath Digital Roadmap

This is the project-level source of truth for planned work. The Main Thread
maintains priorities and status. Implementation work runs in separate Codex
tasks, and an item moves to Done only after its changes and verification have
been reviewed.

## Now

- [ ] **X3 — Client synchronization and reconnect**
  - [x] Preserve persisted game/player URL state and provide a user-controlled
    reconnect path that fetches the authoritative player-scoped snapshot and
    sequence after transient transport failures.
  - [x] Keep stale-command handling refresh-only, prevent mutation retries, and
    discard callbacks from superseded game/player/reconnect generations.
  - [ ] Add automatic asynchronous catch-up through a bounded polling or push
    design, including cursor/delta semantics, backoff, and recovery tests.
  - Preserve server authority, optimistic concurrency, private
    player projections, and explicit browser-memory debug mode.

- [ ] **X5 — Replay and concurrency hardening**
  - Continue compatibility, malformed-input, invalid-command,
    concurrent-append, and reconstruction coverage as gameplay slices are
    added.

- [ ] **X6 — Production identity and authorization boundary**
  - Replace the development-only caller-selected player identity with
    authenticated game membership before permitting a non-loopback deployment.
  - Keep the current unauthenticated API bound to loopback and labeled for
    development/manual testing only.

## Next

- [ ] **L1 — First post-setup gameplay vertical slice**
  - Define the smallest Wake-to-action command/event/replay milestone after
    setup, with UI controls and server-authoritative persistence.

## Later

- [ ] **L2 — Board layout and asset-loading pipeline**
- [ ] **L3 — Licensed game assets with permanent visual fallbacks**
- [ ] **L4 — Saved-game browser and replay navigation**
- [ ] **L5 — Asynchronous accounts, invitations, and notifications**
- [ ] **L6 — Incremental implementation of remaining phases and rules**

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

The combined milestone passes 115 JVM tests, 25 Scala.js tests, the Scala.js
linker, runtime-catalog validation, and a persisted three-player browser smoke
test through **Ready to begin first turn** and reload reconstruction.

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
