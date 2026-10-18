# Authoritative domain events

Status: accepted, reviewed August 2026.

Commands are transient requests. Accepted gameplay emits domain events, and
those events are the single durable game history. The application's
`JournalFold` reconstructs state by folding every event through
`OathRules.evolve`. `EventReplayEngine` (`engine`) is generic replay machinery
used by tests, not an alternative authority. Snapshots, if introduced, are
discardable caches rebuilt from the stream.

## Current event boundary

`OathEvent` and its supporting event facts live in `model`
(`GameEventProtocol.scala`).
Gameplay modules own validation and evolution. The application service loads,
decodes, replays, handles one command, encodes emitted events, and requests one
atomic expected-position append.

`GameEventWire` owns one pre-release envelope format (`formatVersion = 1`)
for setup and gameplay. It delegates explicit payload cases to cohesive codecs:

- `LifecycleEventCodec` for `GameStarted` and `IgnoredRulesRecorded`;
- `EndingEventCodec` for bandit refill, round, War Exhaustion, Usurper, and
  Vision victory facts;
- `WalkerEventCodec` for walker steps, parks, completions, queued procedures,
  action boundaries, and log notes;
- `UndoEventCodec` for `CommandAccepted`, `StepTakenBack`, and `StepRestored`;
- `ConsentEventCodec` for consent requests, answers, and preferences.

Readers reject unknown discriminators, malformed values,
unsafe/non-contiguous sequences, and stream identity changes. No Scala class
name, reflection metadata, command, projection, or transport request is stored.

The setup-start payload records the complete setup plan and selected
Oathkeeper goal so replay never consults mutable defaults or randomness.
Envelopes carry no catalog reference; stored events that still contain a
`catalog` field load, and the field is ignored.

## Facts and randomness

Events record accepted facts needed for deterministic replay:

- Every action, Wake, Rest, Setup and every triggered procedure runs on the
  procedure walker. Each answer is a walker `ChoicePayload` step and every
  other step is recorded core operations, including Campaign's
  `RecordCampaignResult`. A roll is a `RollPayload` step carrying its faces;
  one the walker rolled itself is marked `automatic`. No action has events of
  its own.
- Catacombs records its relic placement and its payment as the operations of
  the one walker step its contribution produces, attributed to the power that
  produced it. Those payment and placement operations are internal composition
  values, not independently injectable `OathEvent` cases.
- Negotiation records each answer (proposed terms, accept, decline) as a
  `ChoicePayload` step and its settlement as that step's core operations.
- State-based facts record themselves as their own events: bandit refill,
  Usurper flip and victory, Vision victory, round end, and War Exhaustion.
- War Exhaustion records the canonical random-fallback candidate order and
  selected winner when deterministic title/Vision rules do not decide it.
- Every command batch but Begin's opens with `CommandAccepted`, naming the
  player who issued it. Undo appends `StepTakenBack`, which voids the
  latest live batch's range, and Redo appends `StepRestored`, which restores
  the batch most recently voided while no command batch has followed; nothing
  is deleted. The application's `JournalFold` replays every event and rewinds
  or restores on a marker (undo design,
  `docs/superpowers/specs/2026-10-06-undo-design.md`).

Replay never rerolls, redraws or walks a procedure tree. It applies each walker
step's recorded operations, answers and roll faces as they are. It recomputes
only the state-based facts above (`StateBasedEvaluation.evolve`) and rejects a
recorded fact that differs from the recomputed one. Privileged events may
contain hidden information and are not ordinary player projection data.

Reviewed pre-alpha fallback decisions are also authoritative facts.
`IgnoredRulesRecorded` stores stable source identity, handler, action, timing,
and the fixed fallback reason; replay re-discovers the source and rejects a
missing, newly implemented, differently classified, or otherwise tampered
diagnostic. Raw diagnostics are available only through loopback development
surfaces and the privileged event stream. Authenticated and ordinary public or
player projections omit them so hidden component identity is not exposed.

## Application and adapters

`application.GameEventCodec` is the representation-free port.
`serialization.GameEventCodecAdapter` implements it with
`serialization.GameEventWire`. `EventStreamRepository` stores opaque
serialized records and has no gameplay legality. HSQLDB and JSON remain outward
adapters.

Appending compares the expected next sequence and writes the complete emitted
batch atomically. A conflict writes nothing and requires reload. See
[event-store application service](event-store-application-service.md) and
[server event journal](server-event-journal.md).

## Pre-release compatibility policy

Before the first public release, the current envelope may change in place when
the writer, reader, replay tests, fixtures, and disposable development data are
updated together. There is no compatibility reader for retired experimental
event formats. The first public release establishes the migration/rejection
baseline.

The current stream key is `gameId`, with zero-based absolute sequence
positions. Tenant, archival, branching, and cross-game transaction policy
remain open, but must not introduce another authoritative history.
