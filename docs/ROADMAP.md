# Oath Digital Roadmap

This is the project-level source of truth for planned work. The Main Thread
maintains priorities and status. Implementation work runs in separate Codex
tasks, and an item moves to Done only after its changes and verification have
been reviewed.

## Now

- [ ] **N1 — Component catalog refactor**
  - Owner: Oath Digital - Catalog Loader task
  - Refine the catalog's runtime projections and source-derived data.
  - Acceptance: catalog validation and the complete Scala test suite pass.

- [ ] **N2 — HRF UI reuse feasibility spike**
  - Establish a Scala.js browser module.
  - Identify the smallest worthwhile set of HRF UI/runtime sources to reuse.
  - Render the bounded setup state with image-independent fallbacks.
  - Make legal setup choices interactive and show event/replay results.
  - Document every reused, adapted, and rejected HRF source area.

- [ ] **N3 — Event-store application service**
  - Load an authoritative event stream and reconstruct setup state.
  - Validate a transient command and append emitted events using an expected
    stream index.
  - Return typed conflicts and validation failures.
  - Keep storage abstract and deterministic; do not choose a database yet.

- [ ] **N4 — Rulebook implementation traceability**
  - Turn the existing source-cited rules ingestion into an implementation
    matrix.
  - Track each rule area as unimplemented, partial, implemented, tested, or
    blocked.
  - Preserve rulebook/page citations and unresolved New Foundations questions.

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
