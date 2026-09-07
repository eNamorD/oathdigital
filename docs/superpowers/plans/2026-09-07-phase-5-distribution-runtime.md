# Phase 5 Distribution and Runtime Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce versioned Universal ZIP/TGZ and Linux OCI artifacts that run Oath Digital's server and optimized frontend without sbt or Node, using validated configuration and explicit liveness/readiness behavior.

**Architecture:** `ServerConfig` becomes the sole command-line/environment boundary and feeds a lifecycle-oriented `OathServer`. Production frontend files are generated into JVM classpath resources, so Universal and OCI packages share identical immutable assets. `sbt-native-packager` builds both artifact types from one Java server application mapping; smoke tests launch packaged output, wait for readiness, exercise the frontend and database, then verify clean shutdown.

**Tech Stack:** Scala 2.13.16, Scala.js 1.20.1, Java 21 runtime contract, Akka HTTP 10.5.3, HSQLDB 2.7.4, munit 1.0.4, sbt-native-packager 1.11.7, POSIX shell, OCI/Docker.

**Spec:** `docs/superpowers/specs/2026-09-07-phase-5-alpha-readiness-design.md`

## Global Constraints

- Universal ZIP/TGZ supports macOS, Linux, and Windows launchers and requires installed Java 21; it must not require sbt or Node.
- OCI targets `linux/amd64` and `linux/arm64`, includes Java 21, and runs as a non-root user.
- Both artifacts contain the same optimized frontend, server classpath, catalog, launchers, version metadata, configuration reference, and lifecycle behavior.
- `ServerConfig` is the only runtime configuration parser; precedence is command-line options, environment variables, then defaults.
- Packaged launchers select `trusted-alpha`; existing development behavior stays explicit and loopback-only.
- Server validates all configuration before opening the database or binding a socket.
- This plan must not edit gameplay procedures, powers, walker code, domain events, or action-history code.
- Frontend test commands use Codex's bundled Node path when system Node is absent:

```sh
PATH=/Applications/ChatGPT.app/Contents/Resources/cua_node/bin:/usr/bin:/bin:/usr/sbin:/sbin ./sbtw frontend/test
```

---

## File Structure

- Create `src/main/scala/oathdigital/server/ServerConfig.scala`: typed runtime mode, validated values, CLI/environment parsing, usage text.
- Create `src/test/scala/oathdigital/server/ServerConfigSuite.scala`: precedence and validation contract.
- Create `src/main/scala/oathdigital/server/HealthRoutes.scala`: liveness and readiness HTTP routes.
- Create `src/test/scala/oathdigital/server/HealthRoutesSuite.scala`: response/status contract without full process startup.
- Create `src/main/scala/oathdigital/server/ProductionFrontendRoutes.scala`: classpath-backed SPA assets with production cache policy.
- Create `src/test/scala/oathdigital/server/ProductionFrontendRoutesSuite.scala`: optimized resource and fallback routing contract.
- Create `frontend/production-index.html`: production shell referencing stable packaged assets.
- Create `scripts/smoke-packaged-distribution.sh`: process-level Universal artifact smoke test.
- Create `scripts/smoke-packaged-container.sh`: OCI artifact smoke test.
- Create `docs/operations/configuration.md`: runtime configuration reference required inside packages.
- Create `docs/operations/packaged-smoke-test.md`: repeatable clean-environment test instructions.
- Modify `project/plugins.sbt`: add sbt-native-packager.
- Modify `build.sbt`: Java server packaging, optimized frontend resources, Universal and Docker settings, smoke aliases.
- Modify `src/main/scala/oathdigital/server/OathServer.scala`: consume `ServerConfig`, readiness state, and packaged route mode.
- Modify `src/main/scala/oathdigital/server/ServerRoutes.scala`: mount mode-specific frontend and health routes without exposing development routes in packaged mode.
- Modify `src/main/scala/oathdigital/server/DevelopmentRoutes.scala`: delegate `/health` ownership and preserve development asset behavior.
- Modify `src/test/scala/oathdigital/server/ServerRoutesSuite.scala`: prove route separation.
- Modify `README.md`: link packaged build and runtime docs.
- Modify `.gitignore`: ignore local package smoke-test output and databases.

### Task 1: Typed Server Configuration

**Files:**
- Create: `src/main/scala/oathdigital/server/ServerConfig.scala`
- Create: `src/test/scala/oathdigital/server/ServerConfigSuite.scala`
- Modify: `src/main/scala/oathdigital/server/OathServer.scala`

**Interfaces:**
- Consumes: `Array[String]`, `Map[String, String]`, and build version string.
- Produces: `ServerMode`, `ServerConfig`, `ServerConfig.parse(arguments, environment, version): Either[Vector[String], ServerConfig]`, and `ServerConfig.usage: String`.

- [ ] **Step 1: Write failing configuration tests**

Create table-driven tests covering defaults, environment values, CLI-over-environment precedence, unknown options, missing values, port range, bind host, absolute normalized paths, runtime modes, and public-base-URL validation. Use this public surface:

```scala
val result = ServerConfig.parse(
  Array("--host", "0.0.0.0", "--port", "9090"),
  Map(
    "OATH_HOST" -> "127.0.0.1",
    "OATH_PORT" -> "8081",
    "OATH_DATABASE_PATH" -> "var/test-db",
    "OATH_CATALOG_PATH" -> "docs/catalog/new-foundations-component-catalog.json",
    "OATH_MODE" -> "trusted-alpha",
    "OATH_PUBLIC_BASE_URL" -> "http://192.168.1.20:9090"
  ),
  "0.1.0-alpha.1"
)
val config = result.toOption.get
assertEquals(config.host, "0.0.0.0")
assertEquals(config.port, 9090)
assertEquals(config.mode, ServerMode.TrustedAlpha)
assertEquals(config.version, "0.1.0-alpha.1")
```

Assert all parse errors are returned together in stable option order. Assert `development` rejects non-loopback hosts. Assert trusted-alpha accepts non-loopback hosts only when `publicBaseUrl` is present and valid. Accept HTTP for trusted LAN alpha and HTTP loopback; document HTTPS as required for Internet exposure.

- [ ] **Step 2: Run configuration tests and verify failure**

Run:

```sh
./sbtw "testOnly oathdigital.server.ServerConfigSuite"
```

Expected: compilation fails because `ServerConfig` and `ServerMode` do not exist.

- [ ] **Step 3: Implement minimal typed parser**

Implement immutable types with exact field names:

```scala
sealed trait ServerMode extends Product with Serializable
object ServerMode {
  case object Development extends ServerMode
  case object TrustedAlpha extends ServerMode
}

final case class ServerConfig(
    host: String,
    port: Int,
    publicBaseUrl: Option[java.net.URI],
    databasePath: java.nio.file.Path,
    catalogPath: java.nio.file.Path,
    mode: ServerMode,
    version: String
)

object ServerConfig {
  def parse(
      arguments: Array[String],
      environment: Map[String, String],
      version: String
  ): Either[Vector[String], ServerConfig]

  val usage: String
}
```

Supported CLI options: `--host`, `--port`, `--public-base-url`, `--database-path`, `--catalog-path`, and `--mode`. Environment names match tests. Defaults remain `127.0.0.1`, `8080`, `var/oathdigital`, catalog path from current server, `development`, and no public base URL. `ServerConfig.usage` is included in every unknown-option or missing-value error; a separate help command is outside this first runtime slice.

Do not read `sys.props` inside `ServerConfig`; the explicit environment parameter makes precedence deterministic. Keep URI/path parsing private and return messages without exceptions.

- [ ] **Step 4: Switch `OathServer.main` to `ServerConfig`**

Replace positional arguments and `sys.props` reads with:

```scala
val version = Option(getClass.getPackage.getImplementationVersion)
  .getOrElse("development")
val config = ServerConfig
  .parse(arguments, sys.env, version)
  .fold(errors => throw new IllegalArgumentException(errors.mkString("; ")), identity)
```

Pass `config.databasePath`, `config.catalogPath`, `config.host`, and `config.port` to existing runtime/binding code. Preserve current coordinated shutdown behavior. Route-mode changes land in Task 3.

- [ ] **Step 5: Run focused and server suites**

Run:

```sh
./sbtw "testOnly oathdigital.server.ServerConfigSuite oathdigital.server.DevelopmentTrustBoundarySuite oathdigital.server.ServerRoutesSuite"
```

Expected: all tests pass.

- [ ] **Step 6: Commit typed configuration**

```sh
git add src/main/scala/oathdigital/server/ServerConfig.scala src/main/scala/oathdigital/server/OathServer.scala src/test/scala/oathdigital/server/ServerConfigSuite.scala
git commit -m "feat(server): add typed runtime configuration"
```

### Task 2: Liveness, Readiness, and Lifecycle Diagnostics

**Files:**
- Create: `src/main/scala/oathdigital/server/HealthRoutes.scala`
- Create: `src/test/scala/oathdigital/server/HealthRoutesSuite.scala`
- Modify: `src/main/scala/oathdigital/server/OathServer.scala`
- Modify: `src/main/scala/oathdigital/server/DevelopmentRoutes.scala`

**Interfaces:**
- Consumes: readiness state controlled by `OathServer`.
- Produces: `ServerReadiness`, `HealthRoutes.route(readiness): Route`, `GET /health/live`, and `GET /health/ready`.

- [ ] **Step 1: Write failing route tests**

Test `ServerReadiness.starting`, `markReady()`, and `markStopping()` through a bound route:

```scala
val readiness = ServerReadiness.starting
val binding = bind(HealthRoutes.route(readiness))
assertEquals(get(binding, "/health/live").statusCode(), 200)
assertEquals(get(binding, "/health/ready").statusCode(), 503)
readiness.markReady()
assertEquals(get(binding, "/health/ready").statusCode(), 200)
readiness.markStopping()
assertEquals(get(binding, "/health/ready").statusCode(), 503)
```

Assert bodies are fixed JSON containing only `status` and `version`, with `Cache-Control: no-store`.

- [ ] **Step 2: Run health tests and verify failure**

```sh
./sbtw "testOnly oathdigital.server.HealthRoutesSuite"
```

Expected: compilation fails because health types do not exist.

- [ ] **Step 3: Implement health routes**

Use one `AtomicReference`-backed state:

```scala
sealed trait ReadinessState
object ReadinessState {
  case object Starting extends ReadinessState
  case object Ready extends ReadinessState
  case object Stopping extends ReadinessState
}

final class ServerReadiness private (
    val version: String,
    private val state: java.util.concurrent.atomic.AtomicReference[ReadinessState]
) {
  def current: ReadinessState = state.get()
  def markReady(): Unit = state.set(ReadinessState.Ready)
  def markStopping(): Unit = state.set(ReadinessState.Stopping)
}
```

Keep response construction inside `HealthRoutes`; do not expose database paths, hostnames, stack traces, or catalog details.

- [ ] **Step 4: Wire lifecycle transitions and logs**

Create readiness before opening runtime. Mount health routes during route assembly. Call `markReady()` only after `ServerRuntime.open` succeeds and HTTP bind completes. Call `markStopping()` at start of coordinated shutdown. Log version, mode, host, port, public base URL presence, and normalized database/catalog paths at startup; never log secret values introduced by later plans.

Remove `/health` from `DevelopmentRoutes`. Retain a temporary compatibility alias `GET /health` in `HealthRoutes` returning the same response as `/health/ready`; mark it for removal after alpha clients migrate.

- [ ] **Step 5: Run focused lifecycle tests**

```sh
./sbtw "testOnly oathdigital.server.HealthRoutesSuite oathdigital.server.ServerRoutesSuite"
```

Expected: all tests pass, including old `/health` compatibility check.

- [ ] **Step 6: Commit health lifecycle**

```sh
git add src/main/scala/oathdigital/server/HealthRoutes.scala src/main/scala/oathdigital/server/OathServer.scala src/main/scala/oathdigital/server/DevelopmentRoutes.scala src/test/scala/oathdigital/server/HealthRoutesSuite.scala
git commit -m "feat(server): add liveness and readiness lifecycle"
```

### Task 3: Production Frontend Resources and Route Separation

**Files:**
- Create: `frontend/production-index.html`
- Create: `src/main/scala/oathdigital/server/ProductionFrontendRoutes.scala`
- Create: `src/test/scala/oathdigital/server/ProductionFrontendRoutesSuite.scala`
- Modify: `src/main/scala/oathdigital/server/ServerRoutes.scala`
- Modify: `src/test/scala/oathdigital/server/ServerRoutesSuite.scala`
- Modify: `build.sbt`

**Interfaces:**
- Consumes: `ServerConfig.mode`, generated classpath resources under `oathdigital/frontend/`, existing `DevelopmentRoutes`.
- Produces: `ProductionFrontendRoutes.route: Route` and mode-separated `ServerRoutes.route(runtime, blockingExecutionContext, config, readiness, nowMillis)`.

- [ ] **Step 1: Write failing production-route tests**

Add a classpath test fixture at `src/test/resources/oathdigital/frontend/index.html` and `main.js`. Test:

```scala
val route = ProductionFrontendRoutes.route
assertEquals(get(route, "/").statusCode(), 200)
assert(get(route, "/").body().contains("/assets/main.js"))
assertEquals(get(route, "/assets/main.js").statusCode(), 200)
assertEquals(get(route, "/missing.js").statusCode(), 404)
```

Assert index responses use `no-cache`; fingerprint-ready asset responses use `public, max-age=31536000, immutable`. Add `ServerRoutesSuite` cases proving trusted-alpha mode returns 404 for `/api/dev/...`, while development mode retains current routes.

- [ ] **Step 2: Run production-route tests and verify failure**

```sh
./sbtw "testOnly oathdigital.server.ProductionFrontendRoutesSuite oathdigital.server.ServerRoutesSuite"
```

Expected: compilation fails because production routes and new route signature do not exist.

- [ ] **Step 3: Add production shell and classpath route**

Create `frontend/production-index.html` with no development query flags:

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Oath Digital</title>
    <link rel="stylesheet" href="/assets/styles.css">
  </head>
  <body>
    <main id="app"><p>Loading Oath Digital…</p></main>
    <script src="/assets/main.js"></script>
  </body>
</html>
```

Serve only known resources from `oathdigital/frontend`; never translate arbitrary URL text into a filesystem path. Unknown extensions return 404. SPA HTML fallback applies only to routes explicitly owned by frontend, initially `/`.

- [ ] **Step 4: Generate optimized frontend resources**

In `build.sbt`, add a root resource generator that depends on `frontend / Compile / fullLinkJS`, copies its `main.js` plus `frontend/styles.css` and `frontend/production-index.html`, and returns exact managed resource files:

```scala
Compile / resourceGenerators += Def.task {
  val report = (frontend / Compile / fullLinkJS).value
  val linkerOutput = (frontend / Compile / fullLinkJS /
    scalaJSLinkerOutputDirectory).value
  val output = (Compile / resourceManaged).value / "oathdigital" / "frontend"
  val linked = linkerOutput /
    report.data.publicModules.find(_.moduleID == "main").get.jsFileName
  val files = Seq(
    linked -> (output / "main.js"),
    baseDirectory.value / "frontend" / "styles.css" -> (output / "styles.css"),
    baseDirectory.value / "frontend" / "production-index.html" -> (output / "index.html")
  )
  IO.copy(files)
  files.map(_._2)
}.taskValue
```

Use the typed Scala.js linker report and output-directory key shown above; do not glob the whole `target` tree or hard-code the Scala version directory.

- [ ] **Step 5: Mount routes by mode**

Change `ServerRoutes.route` to accept `ServerConfig` and `ServerReadiness`. Always mount `HealthRoutes`. In development mode, mount existing development routes and optional authenticated routes. In trusted-alpha mode, mount `ProductionFrontendRoutes` and no development routes. Trusted seat/game routes arrive in the next implementation plan.

- [ ] **Step 6: Verify optimized resources and separation**

```sh
./sbtw "testOnly oathdigital.server.ProductionFrontendRoutesSuite oathdigital.server.ServerRoutesSuite" "Compile/package"
jar tf target/scala-2.13/oathdigital-engine_2.13-*.jar | grep 'oathdigital/frontend/main.js'
```

Expected: tests pass and packaged JAR contains index, stylesheet, and optimized `main.js`.

- [ ] **Step 7: Commit production resources**

```sh
git add build.sbt frontend/production-index.html src/main/scala/oathdigital/server/ProductionFrontendRoutes.scala src/main/scala/oathdigital/server/ServerRoutes.scala src/test/scala/oathdigital/server/ProductionFrontendRoutesSuite.scala src/test/scala/oathdigital/server/ServerRoutesSuite.scala
git commit -m "feat(server): serve optimized packaged frontend"
```

### Task 4: Universal and OCI Packaging

**Files:**
- Modify: `project/plugins.sbt`
- Modify: `build.sbt`
- Create: `docs/operations/configuration.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: Task 1 `ServerConfig`, Task 3 classpath frontend resources.
- Produces: `Universal/packageBin`, `Universal/packageZipTarball`, `Docker/stage`, `Docker/publishLocal`, versioned launchers, and image metadata.

- [ ] **Step 1: Add packaging assertions before plugin configuration**

Create an sbt task `verifyPackageMappings` that fails unless mappings contain:

```text
bin/oathdigital
bin/oathdigital.bat
lib/<server-and-dependency-jars>
share/oathdigital/new-foundations-component-catalog.json
share/oathdigital/configuration.md
```

Make `Universal/packageBin`, `Universal/packageZipTarball`, and `Docker/stage` depend on it. Run `./sbtw verifyPackageMappings`; expected result: task is unknown.

- [ ] **Step 2: Enable Java server packaging**

Add to `project/plugins.sbt`:

```scala
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.7")
```

Enable `JavaServerAppPackaging` and `DockerPlugin` on root. Set:

```scala
Compile / mainClass := Some("oathdigital.server.OathServer")
Universal / packageName := "oathdigital"
Docker / packageName := "oathdigital"
Docker / dockerExposedPorts := Seq(8080)
Docker / daemonUser := "oathdigital"
Docker / daemonUserUid := Some("10001")
Docker / dockerBaseImage := "eclipse-temurin:21-jre"
```

Add catalog and configuration documentation to `Universal / mappings`. Set packaged Java options only for stable JVM settings; runtime host, port, paths, mode, and public URL remain CLI/environment configuration.

- [ ] **Step 3: Add package configuration documentation**

Document every Task 1 CLI option and environment variable with defaults and archive/container examples. Include:

```sh
OATH_MODE=trusted-alpha \
OATH_HOST=0.0.0.0 \
OATH_PORT=8080 \
OATH_PUBLIC_BASE_URL=http://192.168.1.20:8080 \
OATH_DATABASE_PATH=/var/lib/oathdigital/database \
OATH_CATALOG_PATH=/opt/oathdigital/catalog/new-foundations-component-catalog.json \
bin/oathdigital
```

State Java 21 requirement for archives and bundled Java 21 for OCI. State that Internet exposure requires HTTPS at a reverse proxy; detailed acceptance remains plan 3.

- [ ] **Step 4: Run mapping and staging gates**

```sh
./sbtw verifyPackageMappings Universal/stage Docker/stage
```

Expected: all tasks pass. Inspect generated start scripts and Dockerfile; trusted-alpha mode must be explicit and container must not run as root.

- [ ] **Step 5: Build both Universal archives**

```sh
./sbtw Universal/packageBin Universal/packageZipTarball
```

Expected: versioned `.zip` and `.tgz` under `target/universal/`, each containing identical mapped files.

- [ ] **Step 6: Commit packaging**

```sh
git add project/plugins.sbt build.sbt docs/operations/configuration.md README.md
git commit -m "build: add Universal and OCI distributions"
```

### Task 5: Packaged Artifact Smoke Tests

**Files:**
- Create: `scripts/smoke-packaged-distribution.sh`
- Create: `scripts/smoke-packaged-container.sh`
- Create: `docs/operations/packaged-smoke-test.md`
- Modify: `.gitignore`
- Modify: `build.sbt`

**Interfaces:**
- Consumes: Task 4 staged Universal distribution and locally built OCI image.
- Produces: `smokeUniversal` and `smokeContainer` sbt tasks/aliases with deterministic exit status.

- [ ] **Step 1: Write Universal smoke script with failing precondition**

Script contract:

```sh
scripts/smoke-packaged-distribution.sh target/universal/stage 18080
```

It must create a temporary directory with `mktemp -d`, copy only staged package contents, set trusted-alpha environment values, launch `bin/oathdigital`, poll `/health/ready` for at most 30 seconds, assert `/` contains `/assets/main.js`, assert asset HTTP 200, stop with `TERM`, wait at most 15 seconds, assert exit success, and verify HSQLDB files exist. Trap cleanup must terminate child process and remove only the exact temporary directory.

Run before implementation with a nonexistent stage directory; expected: exit non-zero with `packaged stage directory not found`.

- [ ] **Step 2: Implement Universal smoke script**

Use `curl --fail --silent --show-error`; never use fixed sleeps as readiness proof. Capture logs inside temporary directory and print them only after failure. Do not invoke sbt, Node, or source-tree frontend paths after staged contents are copied.

- [ ] **Step 3: Run Universal smoke test**

```sh
./sbtw Universal/stage
scripts/smoke-packaged-distribution.sh target/universal/stage 18080
```

Expected: readiness, index, asset, persistence, and shutdown checks pass.

- [ ] **Step 4: Write and implement OCI smoke script**

Contract:

```sh
scripts/smoke-packaged-container.sh oathdigital:smoke 18081
```

It creates one named volume and one container with unique explicit names, publishes only `127.0.0.1:18081:8080`, supplies trusted-alpha environment values, polls readiness, checks index and asset, restarts same container/volume, rechecks readiness, sends `docker stop --time 15`, then removes only those exact test resources in a trap. Reject missing Docker before creating anything.

- [ ] **Step 5: Add sbt smoke entry points and docs**

Add command aliases or input tasks:

```scala
addCommandAlias("smokeUniversal", ";Universal/stage;verifyPackageMappings")
addCommandAlias("buildAlphaArtifacts", ";test;frontend/test;Universal/packageBin;Universal/packageZipTarball")
```

Document required follow-up shell invocation explicitly; do not hide Docker dependency inside an ordinary unit-test task. Add `.package-smoke/` and local smoke databases to `.gitignore` if scripts create any repo-local fallback.

- [ ] **Step 6: Run package-focused verification**

```sh
./sbtw verifyPackageMappings Universal/stage Universal/packageBin Universal/packageZipTarball Docker/stage
scripts/smoke-packaged-distribution.sh target/universal/stage 18080
git diff --check
```

If Docker is available, also run:

```sh
./sbtw Docker/publishLocal
scripts/smoke-packaged-container.sh oathdigital:0.1.0-SNAPSHOT 18081
```

Expected: every available gate passes; unavailable Docker is reported as an environment limitation, not silently skipped.

- [ ] **Step 7: Commit smoke tests**

```sh
git add scripts/smoke-packaged-distribution.sh scripts/smoke-packaged-container.sh docs/operations/packaged-smoke-test.md .gitignore build.sbt
git commit -m "test(packaging): smoke-test alpha artifacts"
```

### Task 6: Full Foundation Verification and Roadmap Record

**Files:**
- Modify: `docs/ROADMAP.md`
- Modify: `docs/superpowers/plans/2026-09-07-phase-5-distribution-runtime.md`

**Interfaces:**
- Consumes: all prior tasks.
- Produces: reviewed Phase 5 distribution/runtime foundation and explicit next-plan boundary.

- [ ] **Step 1: Run full automated verification**

```sh
./sbtw test
PATH=/Applications/ChatGPT.app/Contents/Resources/cua_node/bin:/usr/bin:/bin:/usr/sbin:/sbin ./sbtw frontend/test frontend/fullOptJS
python3 scripts/check-architecture.py
python3 scripts/check-markdown-links.py
python3 scripts/validate-component-catalog.py
python3 reference/catalog-ingestion/build_runtime_catalog.py
git diff --check
```

Expected: 0 failures. Record exact test counts and any pre-existing warnings in this plan's execution notes.

- [ ] **Step 2: Repeat packaged smoke gates from clean outputs**

Delete only generated `target/universal` and Docker smoke resources through their build/script cleanup mechanisms, rebuild, then run Task 5 smoke commands. Never use broad recursive deletion against repository or home paths.

- [ ] **Step 3: Update roadmap narrowly**

Under project Phase 5, record distribution/runtime foundation as complete only if Universal and available OCI smoke tests pass. Leave trusted seat access, multi-machine acceptance, backup/restore, and release publication unchecked. Link this plan and design spec.

- [ ] **Step 4: Mark plan execution evidence**

Append an `## Execution evidence` section containing commit IDs, test counts, artifact names, smoke commands, host architecture, and any unrun environment-dependent gate. Do not claim OCI multi-architecture completion from a single-architecture local build.

- [ ] **Step 5: Commit verification record**

```sh
git add docs/ROADMAP.md docs/superpowers/plans/2026-09-07-phase-5-distribution-runtime.md
git commit -m "docs: record Phase 5 distribution foundation"
```

## Follow-up Plans

After this plan passes review, write and execute these separate plans against the same approved design:

1. `phase-5-trusted-seat-access`: persistent code digests, transactional game creation, cookie exchange, canonical game routes, frontend bootstrap, restart tests.
2. `phase-5-release-operations`: backup/restore and upgrade policy, GitHub prerelease automation, multi-architecture OCI publication, LAN multi-browser acceptance, reverse-proxy/TLS exercise, host/player quick-start.

Each plan must merge/rebase the latest Phase 3 work before integration and rerun the complete verification gate. Neither may modify gameplay procedures, power handlers, walker logic, or Phase 4 action history.
