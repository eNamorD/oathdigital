# Phase 5: All-Exile Alpha Readiness

> Status: approved design. This specification starts the project-roadmap Phase 5 work in parallel with Phase 3. It does not depend on Phase 4 action history.

## Goal

Ship a versioned, self-hosted alpha that a trusted group can run without sbt or Node. The same release must provide a Java-based Universal archive and a Linux OCI image, serve the optimized frontend with the server, preserve games across restarts, and let each player reopen their assigned seat through one stable link.

This phase assumes players know and trust each other. Seat links reduce mistakes and provide convenient routing; they are not a production authentication system.

## Scope

The first Phase 5 slice includes:

- Universal ZIP and TGZ distributions for macOS, Linux, and Windows hosts with Java 21 installed;
- a Linux OCI image for `amd64` and `arm64`, including its Java runtime;
- the optimized Scala.js frontend, server libraries, component catalog, database migrations, launchers, version metadata, and operator documentation in both artifacts;
- validated server configuration for bind address, port, public base URL, database path, and catalog path;
- trusted-alpha game creation and one persistent opaque seat link per player;
- cookie-backed seat restoration after the first seat-link visit;
- health, readiness, startup diagnostics, actionable logs, graceful shutdown, backup/restore guidance, browser support, and alpha upgrade/reset policy; and
- automated packaged-build smoke tests plus documented LAN and reverse-proxy/TLS checks.

Deferred work:

- accounts, passwords, OIDC, invitations, claims, expiring sessions, revocation, and remote administration;
- spectator links;
- native Windows, macOS, or Linux installers and bundled Java runtimes outside the OCI image;
- action-history work from roadmap Phase 4; and
- production-grade authorization against malicious players.

## Distribution Architecture

Use `sbt-native-packager` as the shared packaging boundary. One production mapping contains the server classpath, optimized frontend, catalog, documentation, configuration examples, and launch scripts. Universal ZIP/TGZ and OCI packaging consume that mapping so their runtime contents cannot drift.

Universal launchers require Java 21 but never sbt or Node. The OCI image contains a Java 21 runtime and runs as a non-root user. Release automation builds both Linux architectures and publishes immutable version tags. GitHub releases are marked as prereleases and contain archives, checksums, release notes, and smoke-test results.

Native installers and `jpackage` remain deferred until alpha users show demand. This avoids an early operating-system build, signing, and notarization matrix.

## Server Configuration

`ServerConfig` is the single typed configuration model. Configuration precedence is:

1. command-line options;
2. environment variables; and
3. documented defaults.

It owns:

- bind address;
- port;
- public base URL;
- persistent database path;
- component catalog path;
- runtime mode (`development` or `trusted-alpha`); and
- build version.

The server parses and validates the complete configuration before it opens the database or binds a socket. Invalid values produce one actionable startup error and a non-zero exit. Packaged launchers always select `trusted-alpha`. Development mode remains explicit and loopback-only.

The public base URL is the browser-visible origin used when creating seat links. It can differ from the bind address when a reverse proxy terminates TLS. It is optional for loopback use; for non-loopback trusted-alpha use, the host must configure it so displayed links are correct. The value must be an absolute HTTP or HTTPS origin without credentials, query, or fragment.

## Trusted-Alpha Seat Access

### Seat creation

Game creation is one transactional service operation. It creates the game, player seats, and one opaque seat code per player or creates nothing. Each code contains at least 128 bits from a cryptographically secure random generator and uses a URL-safe representation.

The persistent identity store maps a digest of each code to `(gameId, playerId)`. Raw codes appear only in the game-creation result and generated seat links. Normal logs, errors, and database rows must not contain raw codes.

The host receives one link per seat:

```text
{publicBaseUrl}/s/{seatCode}
```

The host distributes these links manually. Possession grants control of the corresponding seat. No claim, account, password, expiration, revocation, or rotation flow exists in this alpha slice.

### First visit and reload

On `GET /s/{seatCode}`, the server:

1. validates and hashes the code;
2. resolves its game and player;
3. sets an `HttpOnly`, `SameSite=Lax` seat cookie scoped to `/games/{gameId}`;
4. redirects to `/games/{gameId}`; and
5. never embeds the code in the resulting page or API URL.

The game page and its API live under `/games/{gameId}` so path-scoped cookies support several games in one browser. Later visits to the canonical game URL restore the seat automatically. The original seat link can initialize another browser or device.

The server derives command identity from the resolved seat. Actor fields supplied by clients remain rejected. Existing expected-sequence checks remain the concurrency boundary.

### Route separation

Packaged trusted-alpha mode exposes health routes, game creation, seat entry, canonical game pages, and seat-scoped game APIs. It does not expose `/api/dev/*` or the development identity shim.

Development mode retains existing development routes but may bind only to explicit loopback addresses. Trusted-alpha routes may bind to non-loopback addresses.

## Client Flow

The server owns reloadable deep routes and serves the SPA shell for canonical game pages. The frontend receives only the canonical game ID and seat-scoped API location; it does not store seat codes or authoritative game state in `localStorage`.

After reconnect or reload, the client loads the current projection from the server and resumes from the authoritative event journal. A sequence conflict triggers a projection reload rather than speculative retry. Polling behavior may remain unchanged in this slice; new synchronization transport is outside scope.

This follows useful Haunt Roll Fail patterns: opaque per-player links, server-owned deep links, journal reconstruction, and optimistic sequence checks. It does not copy bearer credentials in API paths, secrets in local storage, broad CORS, or multi-step client-side game creation.

## Errors and Lifecycle

- An unknown or malformed seat code returns a generic invalid-seat-link page without game details.
- A canonical game page without its seat cookie instructs the player to open the assigned seat link.
- A cookie for a missing game uses the same recovery page.
- Sequence conflicts return the existing typed conflict response; the frontend reloads current state.
- Configuration, catalog, migration, or database failures stop startup before network readiness and identify the operator action required.
- Expected client errors do not log stack traces. Unexpected failures include correlation context but no seat codes or private game data.
- Shutdown stops accepting new requests, allows a bounded drain, closes the database, and exits.

`GET /health/live` reports only process liveness. `GET /health/ready` succeeds only after configuration, catalog, database migration, and route initialization complete. Health responses expose no sensitive configuration.

## Data and Upgrade Policy

Database initialization and migrations are automatic, contiguous, and idempotent. A database created by a newer unsupported version causes startup failure rather than downgrade or reinterpretation.

Each alpha release documents whether its data format is compatible with the prior release. Until a stable compatibility promise exists, breaking upgrades may require reset. Before any upgrade, operators copy the stopped database directory. Restore means stopping the server and replacing the complete database directory with a matching backup. Documentation must state that copying a live HSQLDB directory is unsupported.

## Operator Documentation

The release includes:

- a host quick-start for archive and OCI installs;
- a player quick-start centered on opening and bookmarking the assigned seat link;
- configuration reference and examples for loopback, LAN, and reverse-proxy use;
- firewall guidance;
- HTTPS and trusted-proxy requirements for Internet exposure;
- backup, restore, reset, and upgrade instructions;
- supported browser expectations; and
- a prominent warning that seat links grant full control of their seats and are intended only for trusted alpha groups.

## Verification

### Automated tests

- Unit tests cover `ServerConfig` precedence and validation, seat-code generation and digest lookup, collision handling, cookie attributes, redirects, invalid links, and log redaction.
- Route tests create a game, open every seat link, reload each canonical URL, and prove commands use the resolved seat rather than client actor data.
- Persistence tests restart the server and prove existing seat links and cookies still resolve.
- Existing sequence-conflict, replay, projection-redaction, database-migration, JVM, and Scala.js suites remain green.
- Packaging tests inspect both artifacts for the optimized frontend, catalog, migrations, launchers, version metadata, and required documentation.
- Clean-environment smoke tests launch Universal and OCI artifacts without sbt or Node, wait for readiness, create and reload a game, then verify graceful shutdown.
- OCI CI builds and smoke-tests `linux/amd64` and `linux/arm64` images.

### Manual acceptance

Two or more isolated browser profiles on separate LAN machines must create or join a game, complete representative multiplayer turns, reconnect, reload persisted state, and resume their assigned seats. A reverse-proxy/TLS exercise must verify generated public links and forwarded requests. Results are recorded for each alpha build.

The current development baseline is 509 passing JVM tests and a successful Scala.js `fastLinkJS`. Frontend tests require Node on the development host; release smoke tests explicitly prove packaged runtime does not.

## Implementation Order and Parallel-Safety Boundary

1. Packaging foundation and optimized frontend mapping.
2. `ServerConfig`, diagnostics, health, and graceful lifecycle.
3. Persistent seat-code model and transactional game creation.
4. Seat exchange, scoped cookie, canonical routes, and frontend bootstrap.
5. Archive and OCI smoke tests.
6. Operator/player documentation and LAN/TLS acceptance.

Phase 5 work must stay on `feat/phase-5-alpha-readiness` in its isolated worktree. It must not edit gameplay procedure, power, walker, event, or action-history code. Shared persistence and server files may evolve only through additive seams that preserve existing development and authenticated routes. Before integration, rebase or merge the latest Phase 3 branch, rerun all verification, and review overlap explicitly.
