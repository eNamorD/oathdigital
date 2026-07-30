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
  - Include the following minor manual-testing and presentation batch:
    - Add a clearly labeled debug **Restart** control that resets the current
      test session to its designated initial state, normally the start of the
      game. Until server persistence is connected, it resets the browser-memory
      event stream and reconstructs the initial state. A future persisted
      implementation must start a new debug session rather than rewrite
      authoritative history.
    - Title the board **The World** and display its regions as columns ordered
      left-to-right: **Cradle**, **Provinces**, **Hinterland**. Preserve the
      required 2/3/3 site counts.
    - Display the Imperial role simply as **Chancellor**, never
      “Chancellor / Purple.” Display other players as color plus role, such as
      **Blue Exile** or **Red Citizen**.
    - Color written player references consistently with their player color,
      including Chancellor references in purple. Keep the complete textual
      player name and sufficient contrast so meaning never depends on color
      alone.
    - Add focused tests for restart behavior, authoritative-history handling,
      region order, board title, player naming, and player-color CSS classes.

- [ ] **X2 — Durable event journal**
  - Use HRF's current local-server storage stack: file-backed HSQLDB 2.7.4,
    Slick 3.5.2 with HikariCP, and Akka HTTP.
  - Adapt HRF's users/journals/entries/access-rights pattern, but store Oath's
    explicit versioned domain-event envelopes rather than serialized HRF
    actions.
  - Enforce optimistic concurrency with a composite game/sequence primary key
    and append each emitted event batch in one database transaction.
  - Implement stream creation/loading, schema creation and repeatable schema
    upgrades while retaining the storage-neutral repository interface so the
    database can be replaced later.
  - Depends on N3.

- [ ] **X3 — Client synchronization and reconnect**
  - Add command submission, event catch-up, stale-command recovery, and
    reconnect behavior.
  - Depends on X1 and X2.

- [ ] **X4 — Exile-only first-game setup**
  - “Complete” means complete for this bounded milestone: construct a valid,
    replayable game state and stop with the first Exile ready to begin their
    first turn. Wake and gameplay actions are separate slices.
  - Pin the runtime catalog and ruleset; record the configured player order,
    colors and lineages; initialize every player as an Exile with the required
    starting board resources, warbands and Supply.
  - Construct **The World** as the 2/3/3 Cradle, Provinces and Hinterland map;
    record the exact selected sites and populate the required starting site
    pieces, resources and facedown relic slots.
  - Construct the first-game denizen pool, regional discards, player starting
    card choices and world deck. Adviser selection is part of setup; Legacy
    selection is omitted.
  - Record the shuffled relic order, relics assigned to sites and the remaining
    relic deck without replaying randomness.
  - Every player is an Exile. Imperial players, Citizenship, and the Chancellor
    are outside this slice.
  - Skip the Legacy system and all Legacy-driven setup changes.
  - Represent the required Foundations as one fixed, unaltered first-game
    profile. Do not build a general Foundation interpreter, alteration system
    or campaign progression.
  - Record every randomized or selected setup outcome as authoritative events
    so replay performs no randomness.
  - Present the completed setup through the browser UI with permanent
    image-independent fallbacks.
  - Exclude later-game restoration, Chronicle behavior, Imperial setup,
    Legacies, Foundation mutation, and all Wake/Act/Rest behavior.
  - Acceptance requires command-produced and serialized-event-replayed game
    states to be exactly equal, with malformed or incomplete setup rejected by
    typed errors.
  - The runtime catalog and rulebook traceability prerequisites are complete.

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
