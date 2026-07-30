# Server-authoritative event journal

Status: Batch B foundation.

## Authority boundary

The JVM server is the sole production command authority. Network and UI inputs
are transient requests: a decoder constructs a typed setup command and passes
it, together with the event position on which the client based the request, to
`ServerCommandGateway`. The gateway delegates to
`SetupApplicationService`, which loads and replays the authoritative stream,
validates the command, and appends only the emitted versioned domain-event
envelopes. Clients never append events, submit HRF action strings, or decide
that a command was accepted.

The loaded stream position must equal the client's `expectedNextSequence`
before domain validation begins. A mismatch returns
`StaleClientPosition(clientExpected, serverActual)`, even when the command
would still be legal in the newer state. The client must reload before issuing
a new command. This protects the asynchronous browser contract independently
of the repository's second optimistic check, which protects the later
load-to-append race.

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

Startup rejects a ledger newer than the binary and rejects missing, duplicate,
zero, or otherwise non-contiguous versions. After migration the ledger must be
exactly `1..TargetVersion`. Failed initialization closes Slick and Hikari
before returning. Database paths containing HSQLDB's `;` URL property
delimiter or control delimiters are rejected before a JDBC URL is constructed.

`envelope_json` is stored opaquely and exactly as supplied. The application
wire decoder remains responsible for format versions and semantic validation.

## Transaction and conflict semantics

An append locks the stream row, reads its next sequence, compares
`MustNotExist` or `AtNextSequence(n)`, inserts the complete event batch, and
updates `next_sequence` in one database transaction. A stale writer receives
`SequenceConflict(expected, actual)` and writes nothing. Creation races map to
`StreamAlreadyExists`; missing update streams map to `StreamNotFound`.
Unexpected database failures map to `RepositoryFailure.StorageFailure`.

The synchronous repository does not impose an application-side timeout on a
running database action. In particular, an append never returns a local timeout
that callers might mistake for proof that no commit occurred. JDBC/Hikari
connection acquisition still has a bounded startup timeout. Callers should
retry stale-position responses only after reload; generic infrastructure
failures are not an idempotency protocol and must not be blindly replayed.

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
and normal JVM shutdown use that path. Bind failure explicitly closes the
runtime and terminates the actor system.

All synchronous journal/application calls made by future command routes must
run on `oathdigital.blocking-dispatcher`, a dedicated fixed thread pool defined
in `application.conf`; they must not run on Akka's default dispatcher. The
current health route performs no database work. Coordinated database shutdown
also uses the blocking dispatcher. Logback supplies the SLF4J backend so
startup, bind, and shutdown messages are not silently discarded.
