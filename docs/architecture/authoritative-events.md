# Authoritative domain events

Status: accepted, reviewed August 2026.

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

## Pre-release event-format policy

Until the first public release, saved-game event compatibility is not a product
requirement. Event payloads, discriminators, and the current format version may
change in place when that produces a simpler coherent model. A breaking change
must update the writer, reader, replay tests, fixtures, and development data
together; it does not require a new event version, migration, or backward-
compatible reader. Local pre-release saves may be discarded.

The first public release establishes the compatibility baseline. From that
point, changes to published event history require an explicit version and
migration or rejection policy.

Even before release, durable events use an explicit envelope and never encode
Scala class names or reflection metadata. Readers reject unknown event types,
malformed identities, catalog disagreement, and non-contiguous sequence
positions rather than guessing or defaulting. Checked-in fixtures protect
current replay behavior, not immutable historical bytes.

The historical v1 bounded pawn-placement proof retains a checked-in fixture.
Complete exile-only first-game setup uses a
separate v2 envelope/vocabulary (`setup.first-game-started`,
`setup.first-game-pawn-placed`, `setup.starting-adviser-chosen`, and
`setup.first-game-completed`). `SetupEventWire` remains the v1 reader/writer;
`GameEventWire` handles the mixed v2-v6 game stream. The current dual-codec
shape avoids silently defaulting fields when reading the bounded v1 fixture.
No v1-to-v2 migration exists because a v1 stream did not record
the denizen, relic, adviser, color, first-player, or supporting-world outcomes
needed to construct the v2 aggregate.

V2 writers accept an absolute non-negative sequence for each envelope, so a
command's event batch can begin at the repository's current nonzero stream
position. Batch helpers require contiguous absolute positions relative to the
batch's declared or first position. Both version and sequence fields are
decoded as exact integers; fractional, negative, non-finite, overflowing, and
non-JSON-safe values are rejected rather than truncated.

Wake and Travel extend the same contiguous game stream with format v3. Setup
discriminators remain v2-only; `gameplay.take-wealth`,
`gameplay.wake-ended`, and `gameplay.traveled` are v3-only. A reader validates
one pinned game ID, catalog reference, and absolute safe sequence across the
mixed stream.
No event is silently reinterpreted under another format. Pre-release work may
instead update the current codec and fixtures together under the policy above.

`gameplay.take-wealth` records the actor, the pawn site derived when the
command was accepted, and the chosen loose resource. `gameplay.wake-ended`
records the actor and advances Wake to Act. Both replay deterministically;
neither replay nor command handling makes a random or hidden choice.

Search adds `gameplay.search-started` and `gameplay.search-completed` in v4.
The start event records the server-prepared draw required for deterministic
replay; completion records the player's ordered decision. These privileged
events are not exposed through ordinary player projections.

Rest adds `gameplay.rest-started` and `gameplay.rest-completed` in v5. The
completion event records returned favor by suit, returned secrets, refreshed
Supply, and the resulting player/round position. Replay derives those facts
again from prior state and rejects disagreement before entering the next Wake.

Economy adds `gameplay.mustered` and `gameplay.traded` in v6. Each event records
the typed denizen-or-edifice target and resolved cost/yield. Replay recalculates
access, suit matching, resource movement, and component limits before accepting
the recorded outcome.

The catalog reference is pinned in every envelope. For `setup.started`, it is
also present in the payload because it is domain data; the codec requires the
two references to agree.

## Open concerns

The current stream identity is a stable `gameId`, and sequence positions are
zero-based within that game stream. Database partition keys, tenant identity,
branching/fork identity, archival boundaries, and cross-game transactions are
deliberately unresolved. Those decisions may refine storage partitioning but
must not introduce another authoritative history.
