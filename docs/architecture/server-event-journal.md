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

Schema version 2 adds provider-neutral operational identity state:

- `users` and `external_identities`, with external identity uniqueness on
  `(provider, subject)`;
- `game_resources`, which may be created before an authoritative event stream;
- `game_memberships`, uniquely keyed by `(game_id, user_id)`, with roles
  `owner`, `player`, and `spectator`, plus a unique occupied player seat per
  game; and
- `sessions`, keyed by an exact 32-byte cryptographic token digest and storing
  creation, last-seen, idle-expiry, absolute-expiry, and revocation times.

Memberships reference `game_resources`, not `event_streams`. This deliberately
allows an owner and player seats to be provisioned before bootstrap creates the
domain stream. Authenticated production gateways must require this resource
and membership before invoking bootstrap. Existing development or historical
streams are not reinterpreted as identity records. Raw bearer tokens are not
accepted by the storage-neutral repository API and are never stored.
Typed identity-operation failures abort their enclosing database transaction;
in particular, game-resource and initial-owner creation cannot partially
commit.

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
identity, ordered entry positions, internal user identity, explicit relational
resource access, and server-owned persistence. Oath uses typed membership roles
instead of HRF's stringly `full/read/append` rights and stores explicit domain
event envelopes rather than serialized actions. HRF's raw reusable secrets,
URL credentials, and client-supplied identity are not copied. OIDC transport,
cookie issuance, invitations, and notifications remain later X6 work.

## Run and shutdown

Start the server with a database path and optional catalog path:

```sh
./sbtw 'runMain oathdigital.server.OathServer var/oathdigital docs/catalog/new-foundations-component-catalog.json'
```

The default bind address is `127.0.0.1:8080`; override it with JVM properties
`oathdigital.host` and `oathdigital.port`. HSQLDB creates several files using
the supplied path prefix. Use a durable local directory in production.

Akka Coordinated Shutdown first unbinds HTTP and then closes the application
runtime. `HsqldbDatabaseOwner` owns one Hikari datasource and Slick database,
initializes the shared schema ledger once, supplies non-owning event-stream and
identity adapters, issues exactly one HSQLDB `SHUTDOWN`, and then closes its
resources. Adapters cannot independently close or disrupt the database. SIGTERM
and normal JVM shutdown use that path. Bind failure explicitly closes the
runtime and terminates the actor system.

Focused tests and standalone tools may use the explicitly owning
`OwnedHsqldbEventStreamRepository` or `OwnedHsqldbIdentityRepository` handles;
their `close` delegates to their visible owner. Production code opens only
`HsqldbDatabaseOwner`, preventing two silent owners for one database path.

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
- `POST /api/dev/first-games/{gameId}/bootstrap?playerId={playerId}`
- `POST /api/dev/first-games/{gameId}/commands?playerId={playerId}`
- `GET /api/dev/first-games/{gameId}?playerId={playerId}`

`playerId` is a development selector for projection redaction, not
authentication or authorization. It must match the actor in `placePawn` and
`chooseAdviser` requests. Both route identifiers are limited to 128 characters
and the conservative character set `A-Z`, `a-z`, `0-9`, `.`, `_`, `:`, and
`-`.

The development bootstrap route avoids copying the complete executable catalog
and hidden plan into the browser. Its small body is:

```json
{
  "expectedNextSequence": 0,
  "participants": [
    {"playerId": "p1", "lineageId": "l1", "color": "red"},
    {"playerId": "p2", "lineageId": "l2", "color": "blue"},
    {"playerId": "p3", "lineageId": "l3", "color": "yellow"}
  ],
  "firstPlayer": "p2"
}
```

`DevelopmentFirstGamePlanFactory` deterministically selects eight catalog
sites; ten printed denizen IDs per suit; the valid starting-hand, regional,
and Vision packet order; every ordinary relic ordered by its catalog numeric
`value` and printed ID; and a matching ruined edifice for each selected
Homeland. This is reproducible local fixture construction, not production
randomness. The derived plan is submitted through the same v2 `Begin`
application command, so it is fully recorded in the authoritative first event.
Neither the plan nor its hidden orders are returned by the route.

The POST body is:

```json
{
  "expectedNextSequence": 2,
  "command": {
    "type": "chooseAdviser",
    "playerId": "p2",
    "adviserId": "9"
  }
}
```

The generic command route accepts only `placePawn` and `chooseAdviser`.
Transport-level `begin` is rejected: bootstrap is the only HTTP creation path,
and the full plan never comes from the browser. `Begin` remains an internal
application command used by the server-derived bootstrap. No Scala class names
or reflection are part of the protocol. Missing/wrong fields report JSON
paths, and expected positions must be non-negative JSON-safe integers.

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
rejection is `422`. Corrupt stored streams and storage failures are `500` with
a stable generic response; internal exception and storage detail is logged but
never returned to the client.

For same-origin local development, build the frontend and start the server:

```sh
./sbtw frontend/fastLinkJS
./sbtw 'runMain oathdigital.server.OathServer var/oathdigital docs/catalog/new-foundations-component-catalog.json'
```

Open `http://127.0.0.1:8080/`. The server serves `frontend/index.html`, styles,
and the generated Scala.js files from `frontend/`, so no development CORS
permission is required.

Because these routes are unauthenticated, `OathServer` refuses to install them
on anything except `127.0.0.1`, `localhost`, or `::1`; wildcard and non-loopback
host overrides fail startup. Real authentication, authorization, game
membership checks, and a production transport must be implemented before any
part of `/api/dev` can be promoted beyond loopback development.
