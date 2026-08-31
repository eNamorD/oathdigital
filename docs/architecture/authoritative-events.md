# Authoritative domain events

Status: accepted, reviewed August 2026.

Commands are transient requests. Accepted gameplay emits domain events, and
those events are the single durable game history. Deterministic event evolution
reconstructs state. `EventReplayEngine` is generic replay machinery, not an
alternative authority. Snapshots, if introduced, are discardable caches rebuilt
from the stream.

## Current event boundary

`OathEvent` and its supporting event facts live in `gameplay/model`.
Gameplay modules own validation and evolution. The application service loads,
decodes, replays, handles one command, encodes emitted events, and requests one
atomic expected-position append.

`GameEventWire` owns one pre-release envelope format (`formatVersion = 1`)
for setup and gameplay. It delegates explicit payload cases to cohesive codecs:

- `LifecycleEventCodec` for setup, Wake, Rest, and lifecycle facts;
- `ActionEventCodec` for ordinary actions and pending decisions;
- `CampaignEventCodec` for Campaign procedures; and
- `EndingEventCodec` for title, Vision, round, and victory facts.

Readers reject unknown discriminators, malformed values, catalog disagreement,
unsafe/non-contiguous sequences, and stream identity changes. No Scala class
name, reflection metadata, command, projection, or transport request is stored.

The setup-start payload repeats the pinned catalog reference because it is
domain data; the envelope and payload must agree. It also records the complete
setup plan and selected Oathkeeper goal so replay never consults mutable
defaults or randomness.

## Facts and randomness

Events record accepted facts needed for deterministic replay:

- Search start records the application-prepared draw; completion records the
  exact ordered player decision.
- Recover and Campaign record prepared physical die faces and resolved costs,
  targets, plans, losses, and outcomes.
- Catacombs records one procedure-scoped `CatacombsResolved` outcome containing
  its exact power, source, payment, and relic placement. Replay revalidates the
  Recover window and all power facts, then leaves a typed prepared-Recover
  marker until the matching `RecoverRolled` event. Generic payment and relic
  placement operations are internal composition values, not independently
  injectable `OathEvent` cases.
- Forge records the prepared relic transfer and exact assignments.
- Challenge, banners, minor actions, Negotiation, Visions, and endings record
  their authoritative choices and terminal facts.
- War Exhaustion records the canonical random-fallback candidate order and
  selected winner when deterministic title/Vision rules do not decide it.

Replay never rerolls or redraws. It derives deterministic facts again and
rejects tampering. Privileged events may contain hidden information and are not
ordinary player projection data.

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
