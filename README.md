# Oath Digital

Oath Digital is a Scala/Scala.js implementation of Oath: New Foundations built
around a deterministic, server-authoritative game engine:

- accepted commands are transient requests and resulting domain events are the
  authoritative durable source of truth;
- rules transitions are deterministic;
- continuations describe the next player or forced engine step;
- event journals use expected-index appends for asynchronous concurrency; and
- current state can be reconstructed by replaying the event stream.

The copied HRF sources under `vendor/haunt-roll-fail/hrf` are retained as
MIT-licensed reference material. They are not part of this build because they
depend on HRF's complete Scala.js framework.

## Architecture

- [Core domain model](docs/architecture/core-domain-model.md)
- [Authoritative domain events](docs/architecture/authoritative-events.md)
- [First-game setup](docs/architecture/game-setup.md)
- [Server event journal and security boundary](docs/architecture/server-event-journal.md)
- [Gameplay module structure](docs/architecture/gameplay-modules.md)
- [Typed rule resolution](docs/architecture/rule-resolution.md)
- [Bounded Search and hidden decisions](docs/architecture/bounded-search.md)

The rules engine in `oathdigital.engine` has no UI or asset dependency.
`oathdigital.catalog` loads selected, source-verified catalog projections into
typed definitions and rejects incompatible or unresolved executable data.
`oathdigital.presentation` is the image-independent boundary between
rules/application code and renderers:

- `BoardView` contains immutable `SiteView`, `CardView`, `PieceView`, and
  `ActionView` values.
- Every presented entity has a stable `ViewId`, an `AccessibleLabel`, an
  optional non-owning `ImageRef`, and an image-independent `FallbackVisual`.
- A platform image loader reports `ImageLoadResult`; `VisualResolver` produces
  either an image instruction or a text/symbol placeholder. Missing, failed,
  stale, and mismatched image results all choose the same deterministic
  fallback.
- Image references are theme/application concerns. The models do not bundle or
  require HRF art, copyrighted Oath assets, a graphics toolkit, or Scala.js.

Renderers should always expose `AccessibleLabel`, including when displaying an
image. They should render `Placeholder.text` and may additionally render its
short `symbol`. `PresentationExample` is a compile-checked integration sketch.

The server UI presents one player-scoped `pendingCardDecision` protocol for
starting advisers and Search. Search arrangement is local until final
confirmation; the server remains authoritative for the kept card, discard
order, placement, and any required replacement. Ordinary projections redact
other players' hidden cards. A separate raw authoritative event log is
available only on the loopback development transport and may reveal hidden
outcomes.

## Build

Use the project-local wrapper. A normal verification run does not require
`clean`:

```sh
./sbtw compile test
./sbtw frontend/test frontend/fastLinkJS
```

Run the complete unit-test suite with:

```sh
./sbtw test
```

To compile both production and test sources without running tests:

```sh
./sbtw Test/compile
```

To run the interactive server UI:

```sh
./sbtw frontend/fastLinkJS
./sbtw 'runMain oathdigital.server.OathServer var/oathdigital docs/catalog/new-foundations-component-catalog.json'
```

Open `http://localhost:8080/?mode=server`. Keep the server terminal open while
testing; `curl http://localhost:8080/health` checks its health. Stop it with
Ctrl-C. Keep `var/oathdigital*` to preserve local games.
