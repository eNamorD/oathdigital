# Phase 5 Trusted Seat Access Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a trusted-alpha host create one persisted all-Exile game and distribute one stable opaque URL per player, with later canonical game loads restoring that seat from a path-scoped cookie.

**Architecture:** Add a seat-code capability beside the existing account/session identity model; raw 128-bit codes cross the boundary only during creation and exchange, while HSQLDB stores SHA-256 digests mapped directly to game and player. A trusted-alpha gateway binds actorless commands to the cookie-resolved player and reuses the existing application service/projector. Server-owned `/s/{code}` and `/games/{gameId}` routes keep credentials out of API URLs, while a separate trusted frontend client removes development identity controls.

**Tech Stack:** Scala 2.13.16, Scala.js 1.20.1, Akka HTTP 10.5.3, HSQLDB 2.7.4, munit 1.0.4, Java `SecureRandom`, SHA-256, URL-safe Base64.

**Spec:** `docs/superpowers/specs/2026-09-07-phase-5-alpha-readiness-design.md`

## Global Constraints

- Work only in isolated branch `feat/phase-5-trusted-seat-access`; do not edit gameplay procedure, power, walker, event, or action-history code.
- Preserve existing development and authenticated routes through additive seams.
- Trusted players only: no accounts, passwords, OIDC, invitations, claims, expiration, revocation, rotation, spectator links, or impersonation-prevention layer.
- Each raw seat code contains exactly 128 random bits and is unpadded URL-safe Base64; only its SHA-256 digest is persisted.
- Raw seat codes may appear only in the successful creation response and generated `/s/{seatCode}` links. Never log them or include them in errors, canonical pages, API URLs, or database rows.
- Seat cookie is `HttpOnly`, `SameSite=Lax`, scoped to `/games/{gameId}`, persists for 365 days, and is `Secure` exactly when public base URL uses HTTPS. Server-side seat code has no expiry; reopening the original link refreshes the cookie.
- Client commands remain actorless; server derives `PlayerId` from the resolved seat. Existing expected-sequence conflict behavior remains authoritative.
- `/api/dev/*` and development identity shim stay absent from trusted-alpha mode.
- Use `./sbtw`, not system `sbt`; use TDD for every production behavior.

---

### Task 1: Persistent seat capability model

**Files:**
- Modify: `src/main/scala/oathdigital/application/IdentityRepository.scala`
- Modify: `src/main/scala/oathdigital/persistence/EventJournalSchema.scala`
- Modify: `src/main/scala/oathdigital/persistence/HsqldbIdentityRepository.scala`
- Modify: `src/main/scala/oathdigital/persistence/HsqldbDatabaseOwner.scala`
- Test: `src/test/scala/oathdigital/persistence/HsqldbIdentityRepositorySuite.scala`
- Create: `src/test/scala/oathdigital/server/SeatCodeSuite.scala`
- Create: `src/main/scala/oathdigital/server/SeatCode.scala`

**Interfaces:**
- Produces: `SeatCode.generate(random: SecureRandom): SeatCode`, `SeatCode.parse(raw: String): Either[String, SeatCode]`, `SeatCode.digest: SeatCodeDigest`.
- Produces: `SeatCodeDigest.fromBytes(Vector[Byte])`, `TrustedSeat(gameId: String, playerId: String)`, `IdentityRepository.createTrustedSeats(gameId, seats, nowMillis)`, and `IdentityRepository.resolveTrustedSeat(digest)`.
- Persistence rule: migration 4 creates `trusted_seats(token_digest BINARY(32) PRIMARY KEY, game_id VARCHAR(255), player_id VARCHAR(128), created_at_millis BIGINT, UNIQUE(game_id, player_id), FOREIGN KEY(game_id) REFERENCES game_resources ON DELETE CASCADE)`; no raw-token column.

- [ ] **Step 1: Write failing value-object tests**

  Test that generated values decode to exactly 16 bytes, use only `[A-Za-z0-9_-]`, contain no padding, distinct deterministic random input produces distinct codes, malformed or non-22-character strings fail generically, and identical raw values produce identical 32-byte digests.

- [ ] **Step 2: Run RED gate**

  Run `./sbtw "testOnly oathdigital.server.SeatCodeSuite"`; expected failure: missing `SeatCode` and `SeatCodeDigest` APIs.

- [ ] **Step 3: Implement minimal code and digest types**

  Use `SecureRandom.nextBytes` with a 16-byte array, `Base64.getUrlEncoder.withoutPadding`, strict 22-character decoding, and `MessageDigest.getInstance("SHA-256")`. Avoid `toString` implementations that expose raw codes.

- [ ] **Step 4: Run GREEN gate**

  Run `./sbtw "testOnly oathdigital.server.SeatCodeSuite"`; expected: all tests pass.

- [ ] **Step 5: Write failing migration/repository tests**

  Extend persistence tests to require schema version 4, contiguous v1/v2/v3 upgrades, atomic insertion of all seats with the game resource, duplicate digest and duplicate `(gameId, playerId)` rollback, digest lookup, close/reopen persistence, cascade behavior, and a metadata assertion that `trusted_seats` contains no raw token/code column.

- [ ] **Step 6: Run persistence RED gate**

  Run `./sbtw "testOnly oathdigital.persistence.HsqldbIdentityRepositorySuite"`; expected failure from schema version/API absence.

- [ ] **Step 7: Implement migration and repository methods**

  Add typed failures `DuplicateTrustedSeat`, `TrustedSeatNotFound`, and `InvalidTrustedSeat`; validate non-empty unique player IDs and non-empty seat vector before entering one `runExpected(...).transactionally` operation. Insert `game_resources` and every digest row on one connection. Do not synthesize users or memberships.

- [ ] **Step 8: Run GREEN and regression gates**

  Run `./sbtw "testOnly oathdigital.persistence.HsqldbIdentityRepositorySuite oathdigital.server.SeatCodeSuite"`; expected: all pass.

- [ ] **Step 9: Commit**

  Commit as `feat(server): persist trusted seat capabilities`.

### Task 2: Transactional trusted game provisioning

**Files:**
- Create: `src/main/scala/oathdigital/server/TrustedGameProvisioning.scala`
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala`
- Create: `src/main/scala/oathdigital/application/TrustedGameStore.scala`
- Create: `src/main/scala/oathdigital/persistence/HsqldbTrustedGameStore.scala`
- Modify: `src/main/scala/oathdigital/persistence/HsqldbDatabaseOwner.scala`
- Create: `shared/src/main/scala/oathdigital/protocol/TrustedGameProtocol.scala`
- Create: `shared/src/main/scala/oathdigital/protocol/TrustedGameProtocolCodec.scala`
- Modify: `src/main/scala/oathdigital/server/ServerRuntime.scala`
- Test: `src/test/scala/oathdigital/server/TrustedGameProvisioningSuite.scala`
- Test: `shared/src/test/scala/oathdigital/protocol/TrustedGameProtocolSuite.scala`

**Interfaces:**
- Consumes: Task 1 `SeatCode`, `SeatCodeDigest`, `TrustedSeat`, and `IdentityRepository.createTrustedSeats`.
- Produces: `TrustedGameCreateRequest(gameId, participants, firstPlayerId)`, `TrustedSeatLink(playerId, url)`, `TrustedGameCreateResponse(gameId, seats)` and strict codecs.
- Produces: `GameApplicationService.prepareBootstrap(gameId, request): Either[GameApplicationError, PreparedGameBootstrap]`, where the prepared value contains validated serialized event records and projected state but performs no write.
- Produces: `TrustedGameStore.create(gameId, seats, preparedRecords, nowMillis)` implemented by `HsqldbTrustedGameStore` as one database transaction inserting `game_resources`, `trusted_seats`, `event_streams`, and ordered `event_entries`.
- Produces: `TrustedGameProvisioning.create(request, publicBaseUrl): Either[TrustedGameFailure, TrustedGameCreateResponse]`.

- [ ] **Step 1: Write failing strict-codec tests**

  Require exact fields, valid identifiers, unique participant player IDs, supported lineage/color values through existing bootstrap validation, actorless content, stable JSON order, and rejection of unknown fields.

- [ ] **Step 2: Run protocol RED gate**

  Run `./sbtw "sharedJVM/testOnly oathdigital.protocol.TrustedGameProtocolSuite"`; expected failure: protocol types absent.

- [ ] **Step 3: Implement minimal shared protocol and codecs**

  Represent participant data with existing `BootstrapParticipantRequest`; response contains `gameId` and ordered `{playerId,url}` values only.

- [ ] **Step 4: Run protocol GREEN gate**

  Run same command; expected: all pass.

- [ ] **Step 5: Write failing provisioning tests**

  Use deterministic code generation. Assert one code per requested player, URLs equal `{origin}/s/{code}`, database contains only digests, duplicate game/collision returns typed generic failure, retries code generation for an in-memory duplicate up to 8 attempts, and no game, seat, stream, or event rows remain on any terminal failure. Inject a failing event-row insertion and assert the whole transaction rolls back. Assert raw codes do not appear in failure `toString` values.

- [ ] **Step 6: Run provisioning RED gate**

  Run `./sbtw "testOnly oathdigital.server.TrustedGameProvisioningSuite"`; expected failure: provisioning API absent.

- [ ] **Step 7: Implement minimal provisioning service**

  Validate and map request with `FirstGameBootstrapMapper`/plan factory, then use the new side-effect-free application preparation seam to derive the initial event records. Generate all codes in memory and reject duplicate digests. Commit game resource, seats, event stream, and entries through `HsqldbTrustedGameStore` in one JDBC transaction; never use compensation and never expose links before commit succeeds. Keep ordinary `GameApplicationService.handle` behavior unchanged by sharing its existing event derivation logic.

- [ ] **Step 8: Run GREEN and focused regression gates**

  Run `./sbtw "testOnly oathdigital.server.TrustedGameProvisioningSuite oathdigital.persistence.HsqldbIdentityRepositorySuite oathdigital.application.GameApplicationServiceSuite"`; expected: all pass.

- [ ] **Step 9: Commit**

  Commit as `feat(server): provision trusted alpha games`.

### Task 3: Seat exchange, cookie authentication, and trusted APIs

**Files:**
- Create: `src/main/scala/oathdigital/server/TrustedSeatRoutes.scala`
- Create: `src/main/scala/oathdigital/server/TrustedGameGateway.scala`
- Modify: `src/main/scala/oathdigital/server/ProductionFrontendRoutes.scala`
- Modify: `src/main/scala/oathdigital/server/ServerRoutes.scala`
- Modify: `src/main/scala/oathdigital/server/ServerRuntime.scala`
- Test: `src/test/scala/oathdigital/server/TrustedSeatRoutesSuite.scala`
- Test: `src/test/scala/oathdigital/server/ServerRoutesSuite.scala`

**Interfaces:**
- Consumes: Task 1 digest resolution; Task 2 provisioning; existing `GameApplicationService`, `GameProjector`, `GameIntentMapper`, and HTTP codecs.
- Produces: `POST /games`, `GET /s/{seatCode}`, `GET /games/{gameId}`, `GET /games/{gameId}/api`, `POST /games/{gameId}/api/commands`, and `POST /games/{gameId}/api/preview`.
- Produces cookie name `oath_seat`, path `/games/{gameId}`, `HttpOnly`, `SameSite=Lax`, `Max-Age=31536000`, `Secure` iff HTTPS, with no Domain attribute.

- [ ] **Step 1: Write failing gateway tests**

  Assert load projects only resolved player's private state; submit and preview bind the resolved player; client actor fields remain rejected by existing codecs; wrong-game seat returns generic forbidden/not-found behavior; stale sequence remains HTTP 409 without retry.

- [ ] **Step 2: Run gateway RED gate**

  Run `./sbtw "testOnly oathdigital.server.TrustedSeatRoutesSuite"`; expected failure: trusted gateway/routes absent.

- [ ] **Step 3: Implement gateway**

  Keep `TrustedSeat` as sole principal. Reuse existing mapping, service, projection, preview-target, and public-error conventions without routing through users or memberships.

- [ ] **Step 4: Write route tests before route implementation**

  Cover creation response links, 303 exchange redirect, exact cookie attributes for HTTP/HTTPS origins, malformed/unknown link generic page with no game detail, canonical page without/with wrong cookie recovery page, canonical SPA shell with valid cookie, cookie-backed API reload, no query parameters, no raw code in redirected location/body/loggable failure values, and coexistence of same-name cookies scoped to two game paths.

- [ ] **Step 5: Implement routes and mode mounting**

  Parse cookie value through `SeatCode.parse`, resolve digest, and require both game IDs to match. Run blocking DB/application calls on `blockingExecutionContext`. Protect mutation requests by rejecting cross-origin `Origin` values against `ServerConfig.publicBaseUrl`; allow missing Origin for same-origin non-browser clients. Mount these routes only in `TrustedAlpha`; serve SPA assets and `/games/{gameId}` shell through `ProductionFrontendRoutes`.

- [ ] **Step 6: Run GREEN gates**

  Run `./sbtw "testOnly oathdigital.server.TrustedSeatRoutesSuite oathdigital.server.ServerRoutesSuite oathdigital.server.AuthenticatedGameRoutesSuite"`; expected: all pass.

- [ ] **Step 7: Commit**

  Commit as `feat(server): exchange trusted seat links for scoped cookies`.

### Task 4: Trusted frontend flow

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameClient.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/Main.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/DevelopmentRenderer.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/HttpGameClientSuite.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/ServerModeUiSuite.scala`

**Interfaces:**
- Consumes: Task 3 canonical path and seat-scoped APIs.
- Produces: `TrustedHttpGameClient` using `/games/{encodedGameId}/api...` with no player query and credentials supplied only by browser cookies.
- Produces: path parser recognizing `/games/{gameId}`; development root query flow remains unchanged.

- [ ] **Step 1: Write failing client URL tests**

  Assert trusted load/submit/preview URLs contain encoded game ID, never contain player ID or seat code, and use existing actorless bodies. Assert 409 reload behavior remains unchanged.

- [ ] **Step 2: Run client RED gate**

  Run `./sbtw "frontend/testOnly oathdigital.frontend.HttpGameClientSuite"`; expected failure: trusted client absent.

- [ ] **Step 3: Implement trusted client**

  Share response parsing with `HttpGameClient`; do not add `localStorage`, session storage, Authorization headers, or credential parameters.

- [ ] **Step 4: Write failing UI/path tests**

  Assert canonical path starts trusted mode with its decoded game ID, hides new-game/player switching/raw-event controls, and never calls bootstrap from a player page. In packaged trusted-alpha mode root renders a host creation form for game ID, participant seat definitions, and first player; successful creation renders copyable ordered seat links. Development mode retains its existing root query flow.

- [ ] **Step 5: Implement minimal mode split**

  Parameterize `ServerModeUi` with a client and fixed-seat mode. In trusted player mode obtain displayed player ID from the returned private projection, keep it fixed, and render recovery messages for 401/403. Add a small host creation component using the Task 2 codec and `POST /games`; it displays links without storing them. Keep development renderer and raw history development-only.

- [ ] **Step 6: Run GREEN and Scala.js gates**

  Run `./sbtw "frontend/testOnly oathdigital.frontend.HttpGameClientSuite oathdigital.frontend.ServerModeUiSuite" frontend/fullLinkJS`; expected: all pass.

- [ ] **Step 7: Commit**

  Commit as `feat(frontend): restore trusted seats from canonical game URLs`.

### Task 5: Restart proof and packaged seat-flow smoke

**Files:**
- Modify: `scripts/smoke-packaged-distribution.sh`
- Modify: `scripts/smoke-packaged-container.sh`
- Modify: `docs/operations/packaged-smoke-test.md`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/superpowers/plans/2026-09-08-phase-5-trusted-seat-access.md`
- Test: `src/test/scala/oathdigital/server/TrustedSeatRoutesSuite.scala`

**Interfaces:**
- Consumes: Tasks 1–4 complete trusted seat flow.
- Produces: repeatable process tests proving creation, each seat exchange, cookie reload, restart with same database, and continued seat resolution without sbt or Node.

- [ ] **Step 1: Add failing restart route test**

  Create game, retain raw link and cookie, close runtime, reopen same database, then prove both original link and cookie still load correct private seat. Assert another seat's cookie cannot control it.

- [ ] **Step 2: Run route RED gate**

  Run `./sbtw "testOnly oathdigital.server.TrustedSeatRoutesSuite"`; expected failure until persistence/runtime wiring is correct.

- [ ] **Step 3: Extend Universal and container smoke scripts**

  POST one deterministic three-player creation request, extract returned links without logging codes, exchange each through separate cookie jars, load all canonical URLs, submit one representative command from correct seat, restart against same database/volume, reload every seat, and preserve existing bounded-shutdown assertions. On failure, redact `/s/<value>` as `/s/[REDACTED]`.

- [ ] **Step 4: Run focused, full, and package gates**

  Run:

  ```bash
  ./sbtw test frontend/fullLinkJS verifyPackageMappings Universal/stage Universal/packageBin Universal/packageZipTarball Docker/stage
  scripts/smoke-packaged-distribution.sh target/universal/stage 18080
  ./sbtw Docker/publishLocal
  scripts/smoke-packaged-container.sh oathdigital:0.1.0-SNAPSHOT 18081
  git diff --check
  ```

  Expected: 0 failures; both process smoke tests prove seat restoration after restart. If Docker daemon is unavailable, record this gate as unrun, never passed.

- [ ] **Step 5: Record evidence and roadmap status**

  Mark only Phase 5 Item 2 complete. Append exact test counts, artifact names, host architecture, commands, and Docker status under `## Execution evidence`. Leave Items 3–5 unchecked.

- [ ] **Step 6: Commit**

  Commit as `test(packaging): prove trusted seat restoration`.

## Execution evidence

### 2026-09-09 — Task 5 restart and packaged seat flow

- Host: `arm64`; Docker CLI: `29.7.2` (`a7dcaa6`). Docker daemon socket
  `/Users/roman/.docker/run/docker.sock` was absent.
- Restart route test was added before any runtime production change. The first
  authorized focused run failed 1 of 9 tests because the draft asserted a
  private decision before any command created that decision. A second run moved
  the same assertion before restart and failed there, confirming the test
  expectation was invalid rather than persistence behavior. After narrowing
  the test to persisted seat identity, link exchange, cookie reload, cross-seat
  command denial, unchanged sequence, and correct-seat command acceptance,
  `./sbtw "testOnly oathdigital.server.TrustedSeatRoutesSuite"` passed 9 of 9.
  Existing runtime persistence needed no production patch.
- `./sbtw test frontend/fullLinkJS verifyPackageMappings Universal/stage Universal/packageBin Universal/packageZipTarball Docker/stage`
  exited 0. JVM tests: 561 passed, 0 failed, 0 errors. Optimized frontend
  linking, package mappings, Universal staging, both Universal archives, and
  Docker staging succeeded. Existing Scaladoc warnings remained unchanged;
  Docker staging warned that it could not inspect the unavailable daemon.
- Universal artifacts:
  `target/universal/oathdigital-0.1.0-SNAPSHOT.tgz` and
  `target/universal/oathdigital-0.1.0-SNAPSHOT.zip`. Docker staging artifact:
  `target/docker/stage/Dockerfile`.
- `scripts/smoke-packaged-distribution.sh target/universal/stage 18080`
  exited 0 after proving readiness, packaged frontend, three separate seat
  exchanges and cookie jars, correct private projections, one accepted command,
  retained-cookie reloads, original-link exchange after restart, database-close
  evidence for both runs, and bounded shutdown.
- `./sbtw Docker/publishLocal` exited 1 because the Docker daemon socket was
  unavailable. Therefore image `oathdigital:0.1.0-SNAPSHOT` was not built and
  `scripts/smoke-packaged-container.sh oathdigital:0.1.0-SNAPSHOT 18081` was
  unrun, not passed.
- `sh -n scripts/smoke-packaged-distribution.sh` and
  `sh -n scripts/smoke-packaged-container.sh` exited 0. `git diff --check`
  exited 0 before evidence was recorded and is rerun as the final gate.
