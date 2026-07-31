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

## Development first-game setup API

The exile-only v2 setup API is intentionally separate from the v1 bounded setup
service. V1 envelopes remain format version 1; v2 streams require format
version 2 and are rejected rather than reinterpreted or migrated when read by
the wrong service.

All endpoints are development-only:

- `GET /health`
- `POST /api/dev/first-games/{gameId}/commands?playerId={playerId}`
- `GET /api/dev/first-games/{gameId}?playerId={playerId}`

`playerId` is a development selector for projection redaction, not
authentication or authorization.

The POST body is:

```json
{
  "expectedNextSequence": 2,
  "command": {
    "type": "chooseAdviser",
    "playerId": "p2",
    "adviserId": "denizen:example"
  }
}
```

Command discriminators are `begin`, `placePawn`, and `chooseAdviser`. A begin
command contains a `plan` using the explicit v2 plan fields: `catalog`,
`participants`, `firstPlayer`, `orderedSites`, `denizenOrder`,
`worldDeckOrder`, `relicOrder`, and `homelandEdifices`. No Scala class names or
reflection are part of the protocol. Missing/wrong fields report JSON paths,
and expected positions must be non-negative JSON-safe integers.

Successful POST and GET responses share the player-scoped projection:

```json
{
  "gameId": "game-1",
  "nextSequence": 3,
  "phase": "awaiting-pawn",
  "activeParticipantId": "p3",
  "players": [
    {
      "playerId": "p2",
      "displayName": "P2",
      "role": "exile",
      "colorToken": "blue"
    }
  ],
  "world": [
    {
      "regionId": "cradle",
      "sites": [{"siteId": "site:a", "label": "A"}]
    }
  ],
  "pawnLocations": [{"playerId": "p2", "siteId": "site:a"}],
  "legalControls": [],
  "ready": false,
  "completed": false,
  "privateAdviserChoices": []
}
```

The projection never contains the authoritative event stream, relic shuffle
order, world-deck order, or another player's adviser alternatives. The private
adviser list is populated only when the selected player is the active adviser
chooser. This is privacy shaping for development, not a security boundary.

Malformed JSON is `400`, missing streams are `404`, stale client/repository
position conflicts and duplicate creation are `409`, and domain command
rejection is `422`. Corrupt stored streams and storage failures are `500`.

For same-origin local development, build the frontend and start the server:

```sh
./sbtw frontend/fastLinkJS
./sbtw 'runMain oathdigital.server.OathServer var/oathdigital docs/catalog/new-foundations-component-catalog.json'
```

Open `http://127.0.0.1:8080/`. The server serves `frontend/index.html`, styles,
and the generated Scala.js files from `frontend/`, so no development CORS
permission is required.
