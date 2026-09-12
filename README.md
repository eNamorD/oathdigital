# Oath Digital

Oath Digital is a Scala/Scala.js implementation of Oath: New Foundations. The
JVM is authoritative: clients send actorless intents, deterministic gameplay
emits domain events, and state is rebuilt by replaying the event stream.

The copied HRF sources under `vendor/haunt-roll-fail/hrf` are MIT-licensed
reference material and are not part of the build.

## Architecture

Start with [codebase structure](docs/architecture/codebase-structure.md). The
main durable decisions are:

- [gameplay modules](docs/architecture/gameplay-modules.md)
- [core operations migration](docs/architecture/core-operations-migration.md)
- [typed rule resolution](docs/architecture/rule-resolution.md)
- [authoritative events](docs/architecture/authoritative-events.md)
- [application/event-store boundary](docs/architecture/event-store-application-service.md)
- [server journal and trust boundary](docs/architecture/server-event-journal.md)
- [first-game setup](docs/architecture/game-setup.md)
- [core domain model](docs/architecture/core-domain-model.md)

Rule-specific designs remain in `docs/architecture/bounded-*.md` and the
all-Exile decision notes. Rule coverage and current implementation evidence are
tracked in [implementation traceability](docs/rules/implementation-traceability.md).

## Build and verification

Use the project-local wrapper; a normal verification run does not require
`clean`:

```sh
npm ci
./sbtw compile
./sbtw test
./sbtw frontend/test
./sbtw frontend/fullOptJS
```

Scala.js tests need Node on `PATH`, and the frontend suites run in jsdom so a
renderer test can drive the DOM the panels build. `npm ci` installs that one
dependency from the pinned `package.json`/`package-lock.json`; run it once
after cloning and again whenever the lockfile changes. Nothing from
`node_modules` ships: the production bundle is linked by sbt and served from
the JVM. In Codex desktop, the bundled runtime can
be selected explicitly:

```sh
PATH=/Applications/ChatGPT.app/Contents/Resources/cua_node/bin:/usr/bin:/bin:/usr/sbin:/sbin \
  ./sbtw frontend/test frontend/fullOptJS
```

Run deterministic repository checks with:

```sh
python3 scripts/check-architecture.py
python3 scripts/check-markdown-links.py
python3 scripts/validate-component-catalog.py
python3 reference/catalog-ingestion/build_runtime_catalog.py
git diff --check
```

The catalog generator without `--output` is a non-writing equality check.

## Local server UI

Build the frontend and run the loopback server:

```sh
./sbtw frontend/fastLinkJS
./sbtw 'runMain oathdigital.server.OathServer --database-path var/oathdigital --catalog-path docs/catalog/new-foundations-component-catalog.json'
```

Open `http://127.0.0.1:8080/?mode=server`. Check
`http://127.0.0.1:8080/health`, stop with Ctrl-C, and retain
`var/oathdigital*` to preserve local games. The development transport includes
player-view controls and a privileged raw event log; it is loopback-only and is
not an authentication boundary.

## Packaged server

Build versioned Universal ZIP and TGZ distributions with:

```sh
./sbtw Universal/packageBin Universal/packageZipTarball
```

Archives are written under `target/universal/`, require Java 21, and need
neither sbt nor Node at runtime. Build the local non-root Java 21 OCI image
with `./sbtw Docker/publishLocal`. Packaged launchers default to
`trusted-alpha` mode and their bundled catalog while preserving command-line,
environment, then default precedence. See
[runtime configuration](docs/operations/configuration.md) for every option and
archive/container examples.
