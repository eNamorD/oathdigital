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
./sbtw compile
./sbtw test
./sbtw frontend/test
./sbtw frontend/fullOptJS
```

Scala.js tests need Node on `PATH`. In Codex desktop, the bundled runtime can
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
./sbtw 'runMain oathdigital.server.OathServer var/oathdigital docs/catalog/new-foundations-component-catalog.json'
```

Open `http://127.0.0.1:8080/?mode=server`. Check
`http://127.0.0.1:8080/health`, stop with Ctrl-C, and retain
`var/oathdigital*` to preserve local games. The development transport includes
player-view controls and a privileged raw event log; it is loopback-only and is
not an authentication boundary.
