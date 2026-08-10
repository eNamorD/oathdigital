# Authoritative domain events

Status: accepted, July 2026.

## Decision

Production Oath Digital commands are transient requests. A command is validated
against current state and may produce one or more domain events, but the command
itself is not durable game history. The resulting domain events are the single
authoritative stream. Replaying those events through deterministic evolution
reconstructs game state.

Events are authoritative because they record what the rules accepted and what
actually happened, including rule-generated consequences such as setup
completion. Persisting commands instead would require historical command
handling to reproduce those consequences forever and could create ambiguity
when rules or command orchestration evolve.

The HRF-inspired `Action`, `Journal`, and `ReplayEngine` API remains for
compatibility and existing tests. It is not a second production source of
truth, and new production slices must use domain events and event evolution.

Snapshots may be introduced later only as rebuildable replay caches. A snapshot
must be discardable and recoverable from the authoritative event stream; it
must not replace or fork that history.

## Wire compatibility

Durable events use an explicit versioned envelope with stable field names and
event discriminators. Scala class names and reflection are not part of the
format. Readers reject unsupported format versions, unknown event types,
malformed identities, catalog disagreement, and non-contiguous sequence
positions rather than guessing or defaulting.

The v1 bounded pawn-placement stream remains byte-for-byte stable and retains
its checked-in golden fixture. Complete exile-only first-game setup uses a
separate v2 envelope/vocabulary (`setup.first-game-started`,
`setup.first-game-pawn-placed`, `setup.starting-adviser-chosen`, and
`setup.first-game-completed`). `SetupEventWire` remains the v1 reader/writer;
`GameEventWire` is the v2 reader/writer. This explicit dual-codec policy
avoids silently defaulting new authoritative fields when reading v1 history.
No automatic v1-to-v2 migration is claimed because a v1 stream did not record
the denizen, relic, adviser, color, first-player, or supporting-world outcomes
needed to construct the v2 aggregate.

V2 writers accept an absolute non-negative sequence for each envelope, so a
command's event batch can begin at the repository's current nonzero stream
position. Batch helpers require contiguous absolute positions relative to the
batch's declared or first position. Both version and sequence fields are
decoded as exact integers; fractional, negative, non-finite, overflowing, and
non-JSON-safe values are rejected rather than truncated.

First-turn gameplay extends the same contiguous game stream with format v3.
Setup discriminators remain v2-only; `gameplay.take-wealth` and
`gameplay.wake-ended` are v3-only. A reader validates one pinned game ID,
catalog reference, and absolute safe sequence across the mixed v2/v3 stream.
Existing setup-only streams and their bytes are unchanged. Older readers may
reject v3 explicitly; no event is silently reinterpreted under another format.

`gameplay.take-wealth` records the actor, the pawn site derived when the
command was accepted, and the chosen loose resource. `gameplay.wake-ended`
records the actor and advances Wake to Act. Both replay deterministically;
neither replay nor command handling makes a random or hidden choice.

The catalog reference is pinned in every envelope. For `setup.started`, it is
also present in the payload because it is domain data; the codec requires the
two references to agree.

## Open concerns

The current stream identity is a stable `gameId`, and sequence positions are
zero-based within that game stream. Database partition keys, tenant identity,
branching/fork identity, archival boundaries, and cross-game transactions are
deliberately unresolved. Those decisions may refine storage partitioning but
must not introduce another authoritative history.
