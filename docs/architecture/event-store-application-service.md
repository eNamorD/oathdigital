> **PARTIALLY SUPERSEDED — replay/command/action-model parts change under the
> procedure-walker redesign**: replay applies recorded ops only (no evolve
> re-derive); the command surface collapses to Start/Resolve/RollSubmitted;
> `PendingProcedure` becomes a `PendingTree` pointer; dice pools/roll outcomes
> become state; `usedPowers` tracking stays. Storage/journal/domain-foundation
> content here remains valid.

# Event-store application service boundary

`GameApplicationService` coordinates transient commands, deterministic rules,
versioned event serialization, and durable storage. It loads the authoritative
stream, decodes and replays it, handles exactly one command, encodes every
emitted domain event, and requests one atomic append.

`EventStreamRepository` is deliberately storage-neutral. Records are serialized
wire values, so an adapter does not depend on Scala event classes. A database
adapter must:

- identify one stream by `gameId`;
- return a stream identity matching the lookup key; the application service
  also verifies that every decoded event envelope has that identity;
- preserve record order exactly;
- atomically append the entire supplied batch;
- compare `MustNotExist` or `AtNextSequence(n)` in the same transaction as the
  append;
- distinguish an existing stream, a missing stream, and a stale next sequence;
- map infrastructure faults to `RepositoryFailure.StorageFailure`.

`HsqldbEventStreamRepository` implements this contract with a stream row and
event rows keyed by `(game_id, sequence)`. Other storage adapters may use native
conditional writes if they preserve the same observable semantics.

Commands are never passed to or retained by this boundary. Snapshots, if added,
are rebuildable caches and do not change the event stream contract.
