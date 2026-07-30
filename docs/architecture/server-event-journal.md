# Server-authoritative event journal

Status: Batch B foundation.

## Authority boundary

The JVM server is the sole production command authority. Network and UI inputs
are transient requests: a decoder constructs a typed setup command and passes
it to `ServerCommandGateway`. The gateway delegates to
`SetupApplicationService`, which loads and replays the authoritative stream,
validates the command, and appends only the emitted versioned domain-event
envelopes. Clients never append events, submit HRF action strings, or decide
that a command was accepted.

The initial HTTP surface intentionally exposes only `GET /health`. Command
route and JSON decoding are isolated as the next transport layer over
`ServerCommandGateway`; Batch C can expand the setup vocabulary without
changing the journal schema or database adapter.

## Database schema and upgrades

The file-backed HSQLDB journal uses:

- `schema_versions(version PRIMARY KEY, applied_at_epoch_millis)`;
- `event_streams(game_id PRIMARY KEY, next_sequence CHECK >= 0)`; and
- `event_entries(game_id, sequence CHECK >= 0, envelope_json CLOB NOT NULL)`,
  with primary key `(game_id, sequence)` and a foreign key to the stream.

`EventJournalSchema` creates the version ledger if needed, reads the installed
version, and applies every later numbered migration in one transaction.
Initialization is repeatable. Future schema changes add an ordered migration
and increment `TargetVersion`; they do not replace migration history with a
one-shot `schema.create`.

`envelope_json` is stored opaquely and exactly as supplied. The application
wire decoder remains responsible for format versions and semantic validation.

## Transaction and conflict semantics

An append locks the stream row, reads its next sequence, compares
`MustNotExist` or `AtNextSequence(n)`, inserts the complete event batch, and
updates `next_sequence` in one database transaction. A stale writer receives
`SequenceConflict(expected, actual)` and writes nothing. Creation races map to
`StreamAlreadyExists`; missing update streams map to `StreamNotFound`.
Unexpected database failures map to `RepositoryFailure.StorageFailure`.

## HRF concepts

Retained from HRF are durable journal/entry separation, stable journal
identity, ordered entry positions, and server-owned access to persistence.
Oath stores explicit domain-event envelopes rather than serialized actions.
Users, invitations, journal sharing, and access-right tables are deferred until
authentication and multiplayer ownership requirements are defined.

## Run and shutdown

Start the server with a database path and optional catalog path:

```sh
./sbtw 'runMain oathdigital.server.OathServer var/oathdigital docs/catalog/new-foundations-component-catalog.json'
```

The default bind address is `127.0.0.1:8080`; override it with JVM properties
`oathdigital.host` and `oathdigital.port`. HSQLDB creates several files using
the supplied path prefix. Use a durable local directory in production.

Akka Coordinated Shutdown first unbinds HTTP and then closes the application
runtime, issues HSQLDB `SHUTDOWN`, and closes Slick/Hikari resources. SIGTERM
and normal JVM shutdown use that path.
