# Server-authoritative event journal

Status: implemented through schema v3 and mixed v2-v4 game streams.

## Authority boundary

The JVM server is the sole production command authority. Network and UI inputs
are transient requests. `GameApplicationService` loads and replays the
authoritative stream, validates one typed command through `OathRules`, and
appends only emitted versioned domain-event envelopes. Clients never append
events or decide that a command was accepted.

The loaded stream position must equal the client's `expectedNextSequence`
before domain validation begins. A mismatch returns
`StaleClientPosition(clientExpected, serverActual)`, even when the command
would still be legal in the newer state. The client must reload before issuing
a new command. This protects the asynchronous browser contract independently
of the repository's second optimistic check, which protects the later
load-to-append race.

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

HSQLDB can briefly retain a file-lock heartbeat after a coordinated
`SHUTDOWN`, causing an immediate reopen to report
`LockHeldExternallyException` even though the prior owner has closed. Database
open retries at most once, after a 50 ms backoff and within a 25-second retry
deadline, only when the exception chain contains that exact HSQLDB lock class
and `checkHeartbeat` diagnostic. Each failed Hikari datasource is closed before
retry. Unsafe paths, schema incompatibility, corruption, and all other open
failures are returned immediately without retry.

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

Schema version 3 adds a nullable, exact 32-byte CSRF-token digest to sessions.
New sessions are rejected unless that digest is supplied. Migration cannot
safely manufacture a synchronizer token, so every pre-v3 session is revoked
and retains a null digest. V1 and v2 ledgers upgrade through the same contiguous
migration path; initialization and close/reopen remain idempotent.

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

## Authentication and membership boundary

`AuthenticatedPrincipal` is the provider-neutral identity presented to server
application code by a pluggable `Authenticator`. It contains only Oath's
internal `UserId`; raw OIDC claims, development headers, and caller-selected
player IDs do not cross this boundary.

`MembershipAuthorizationService` resolves a principal and game through
`IdentityRepository` into one of three access contexts. Owners may bootstrap
but have public-only projection scope and cannot submit player commands.
Players receive private projection scope for their single membership-derived
`PlayerId`; `AuthorizedPlayer` constructs commands with that actor. Explicit
spectators receive public-only projections and cannot bootstrap or command.
Non-members, global administrators, and public spectators are denied. Storage
failure and nonmembership remain distinct internal outcomes.

The optional `DevelopmentIdentityShim` is a separately named test/manual
authenticator for the `X-Oath-Dev-User` header. Configuration returns no shim
unless explicitly enabled, and enabling it revalidates that the already
validated bind host is `127.0.0.1`, `localhost`, or `::1`. It is not an OIDC or
production session path. Existing `/api/dev` routes are not changed in this
slice.

The separately mountable authenticated game transport is:

- `GET /api/authenticated/first-games/{gameId}`; and
- `POST /api/authenticated/first-games/{gameId}/commands`; and
- `POST /api/authenticated/first-games/{gameId}/bootstrap`.

`OathServer` mounts this transport only when both
`oathdigital.sessionCookieName` and `oathdigital.publicOrigin` are explicitly
configured. Without both, only the existing development transport is mounted;
partial configuration fails startup. The server remains loopback-only in this
slice even when authenticated routes are mounted.

Every operation authenticates before membership lookup. The HTTP authenticator
accepts only a 43-128 character base64url-shaped raw session cookie, hashes it
with SHA-256 at the edge, and sends only the fixed digest to
`IdentityRepository`. Resolution runs on `oathdigital.blocking-dispatcher`, not
Akka's request dispatcher. Missing, malformed, unknown, expired, and revoked
sessions share one stable `401` response. Raw session and CSRF tokens never
enter repositories, logs, projections, or errors.

GET/poll requires a session but is CSRF-exempt. Every authenticated POST
requires exactly the configured `Origin` and one `X-CSRF-Token` header. The
header is hashed and compared to the stored digest with a constant-time digest
comparison before request-body or domain handling; failure is a stable `403`
and performs no append. GET has no player selector. POST accepts
`expectedNextSequence` plus an actor-free
`intent`: `placePawn` contains only `siteId`, and `chooseAdviser` contains only
`adviserId`. Unknown fields, including `playerId`, are rejected. Membership
constructs the domain actor, and mutations are attempted once without retry.
Authenticated bootstrap requires the owner membership and a pre-provisioned
game resource. Its participant list must exactly match every provisioned
`player` membership; owner and spectator memberships are never setup seats.
The request selects only participant order, lineage, color, and first player.
The server derives every hidden site, deck, denizen, and relic ordering from the
executable catalog and creates the event stream once. The current membership
model deliberately does not let the owner also occupy a player seat. The
existing `/api/dev` API and bootstrap creation remain unchanged.

This slice deliberately has no login, logout, session issuance, cookie-setting,
OIDC redirect/callback, or refresh endpoint. Tests provision digests directly.
Cookie flags and lifecycle belong to the later issuance boundary.

## Run and shutdown

Start the server with a database path and optional catalog path:

```sh
./sbtw 'runMain oathdigital.server.OathServer var/oathdigital docs/catalog/new-foundations-component-catalog.json'
```

The default bind address is `127.0.0.1:8080`; override it with JVM properties
`oathdigital.host` and `oathdigital.port`. HSQLDB creates several files using
the supplied path prefix. Use a durable local directory in production.

For seeded-session testing, configure both
`oathdigital.sessionCookieName=oath_session` and an exact origin such as
`oathdigital.publicOrigin=http://127.0.0.1:8080`. This does not issue a session
or relax the loopback binding gate.

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

Synchronous journal/application calls run on `oathdigital.blocking-dispatcher`,
a dedicated fixed thread pool defined in `application.conf`; they do not run on
Akka's default dispatcher. Coordinated database shutdown also uses the blocking
dispatcher. Logback supplies the SLF4J backend so
startup, bind, and shutdown messages are not silently discarded.

## Loopback development API

The game API uses mixed v2-v4 streams and remains separate from the historical
v1 pawn-placement service. Versions are rejected rather than reinterpreted by
the wrong codec.

All endpoints are development-only:

- `GET /health`
- `POST /api/dev/first-games/{gameId}/bootstrap?playerId={playerId}`
- `POST /api/dev/first-games/{gameId}/commands?playerId={playerId}`
- `GET /api/dev/first-games/{gameId}?playerId={playerId}`

`playerId` is a development selector for projection redaction and command
actor, not authentication or authorization. Both route identifiers are limited
to 128 characters and the conservative character set `A-Z`, `a-z`, `0-9`,
`.`, `_`, `:`, and `-`.

The development bootstrap request supplies public participant order, lineage,
color, and first player. It never supplies hidden setup order.

`DevelopmentFirstGamePlanFactory` deterministically selects eight catalog
sites; ten printed denizen IDs per suit; the valid starting-hand, regional,
and Vision packet order; every ordinary relic ordered by its catalog numeric
`value` and printed ID; and a matching ruined edifice for each selected
Homeland. This is reproducible local fixture construction, not production
randomness. The derived plan is submitted through the same v2 `Begin`
application command, so it is fully recorded in the authoritative first event.
Neither the plan nor its hidden orders are returned by the route.

The command route accepts the implemented setup, Wake, Travel, and Search
intents. Transport-level `begin` and server-prepared Search draws are rejected:
bootstrap is the only creation path, and hidden outcomes never come from the
browser. No Scala class names or reflection are part of the protocol.
Missing/wrong fields report JSON paths, and expected positions must be
non-negative JSON-safe integers.

Successful POST and GET responses share the player-scoped projection. It never
contains the authoritative event stream, relic shuffle
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
membership checks, and a production transport are available only through the
separately mounted authenticated routes; `/api/dev` remains loopback-only.
