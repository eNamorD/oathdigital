# Replay Posture: Recorded Operations and Mutation Guards Only

> Status: decision recorded 2026-09-24 from an architecture review. This refines
> decision 5 of the [procedure walker design](2026-09-05-procedure-walker-design.md);
> it does not replace it. No implementation is authorized by this document alone.

## Decision

Replay applies recorded operations through the operation executor and nothing
else. The guards the executor runs are the only legality replay performs. The
whole-state post-invariant that `OperationPipeline` runs after a live command
(card inventory conservation, domain validation, Vision orientation, warband
conservation) does **not** run on replay, and no "loaded but invalid" state is
introduced. A journal either replays to a state or fails to load.

The one change this review does make is to where the guards live: each
operation family holds its shape guard beside its mutation, and the mutation
runs the guard as its precondition. Replay therefore reaches the shape checks
that previously lived only in `OperationShape`, because the executor is the
path it already takes. That is a consequence of removing a duplicated copy, not
a new replay-time validator.

## Why

Two reasons, both about the alpha rather than about correctness in the
abstract.

The check has never fired. No journal produced by this codebase has failed the
post-invariant, so adding it to the load path would be adding a failure mode
with no observed case behind it. Decision 5 already places whole-state drift
checks in dev and test suites; that is where they stay.

A replay rejection is total. Every replay failure today surfaces as a generic
500 for every request from every player, and the data policy offers no repair
path beyond restoring a backup or a reversible reset. Running the invariant on
load either bricks a game that currently opens, or requires a quarantined
"read-only, refuse commands" load outcome carried through `LoadedGame`, the
projection, and the routes. Neither cost is justified until a real corruption
exists to point at.

## Considered and rejected

- **Invariant on replay, fatal.** Rejected: converts silently wrong state into
  a permanently unopenable game with no recorded recovery policy.
- **Invariant on replay, non-fatal ("quarantine").** Rejected for now: a new
  load outcome, a projection flag, a new error code and a mapped
  `ReplayFailure`, all to guard against something never observed. Revisit when
  a real journal fails the invariant; the design was fully worked out and the
  option remains open.
- **Full pipeline on replay with reduction suppressed.** Rejected: reduction is
  the pipeline's spine, and suppressing it would be a second code path inside
  the thing being unified.

## Consequences

- Architecture reviews should not re-propose the post-invariant on replay
  without a concrete failing journal.
- A recorded operation that fails a shape guard now fails replay, fatally,
  like any other rejected event fact, with the first shape violation as its
  reason. This is the posture
  `docs/architecture/core-operations-migration.md` already asks for and is the
  only replay behaviour change the guard consolidation introduces.
- `WalkerReplayDriftSuite` remains the home of re-derived-versus-recorded
  drift checking. It does not gain rejection cases from this decision.
