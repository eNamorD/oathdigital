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

The initial format has no migration machinery. A future format change must
define an explicit version and migration/dual-reader policy before writers emit
it. Event payload evolution should prefer compatible additive fields when
possible, but readers remain strict about required authoritative data.

The catalog reference is pinned in every envelope. For `setup.started`, it is
also present in the payload because it is domain data; the codec requires the
two references to agree.

## Open concerns

The current stream identity is a stable `gameId`, and sequence positions are
zero-based within that game stream. Database partition keys, tenant identity,
branching/fork identity, archival boundaries, and cross-game transactions are
deliberately unresolved. Those decisions may refine storage partitioning but
must not introduce another authoritative history.
