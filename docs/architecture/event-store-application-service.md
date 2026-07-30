# Event-store application service boundary

The setup application service is the transaction coordinator between transient
commands, deterministic setup rules, versioned event serialization, and durable
storage. It loads the complete authoritative event stream for a game, decodes
and replays it, handles exactly one command, encodes every emitted domain event,
and requests one atomic append.

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

For a relational database, a stream row plus event rows keyed by
`(game_id, sequence)` is one possible implementation. Creation would insert the
stream and its initial events in one transaction. Later writes would lock or
conditionally update the stream's next-sequence value and insert all event rows
before commit. A document or log database may use native conditional writes,
provided the same observable contract holds.

Commands are never passed to or retained by this boundary. Snapshots, if added,
are rebuildable caches and do not change the event stream contract.
