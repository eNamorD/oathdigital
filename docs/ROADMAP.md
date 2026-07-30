# Oath Digital Roadmap

This is the project-level source of truth for planned work. The Main Thread
maintains priorities and status. Implementation work runs in separate Codex
tasks, and an item moves to Done only after its changes and verification have
been reviewed.

## Now

No implementation task is active. The next task should be selected from
**Next** after reviewing the completed parallel milestone.

## Next

- [ ] **X1 — Integrate and maintain the frontend architecture**
  - Apply the outcome of N2 without changing the authoritative-event decision.

- [ ] **X2 — Durable event journal**
  - Select and implement database-backed event storage, optimistic concurrency,
    stream creation/loading, and migrations.
  - Depends on N3.

- [ ] **X3 — Client synchronization and reconnect**
  - Add command submission, event catch-up, stale-command recovery, and
    reconnect behavior.
  - Depends on X1 and X2.

- [ ] **X4 — Complete Oath setup**
  - Expand the bounded pawn-placement slice into the full source-cited setup
    procedure.
  - Depends on N1 and N4.

- [ ] **X5 — Replay and concurrency hardening**
  - Add compatibility, malformed-input, invalid-command, concurrent-append,
    and reconstruction tests around the application and storage layers.
  - Begins with N3 and continues through X2.

## Later

- [ ] **L1 — First post-setup gameplay vertical slice**
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

The combined milestone passes 71 JVM tests, 5 Scala.js tests, the Scala.js
linker, and runtime-catalog validation.

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
