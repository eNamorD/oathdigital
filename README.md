# Oath Digital

This project begins with a small, pure-Scala board-game engine based on the
architectural lessons from HRF:

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

The durable core-domain decisions and milestone acceptance criteria are
recorded in [`docs/architecture/core-domain-model.md`](docs/architecture/core-domain-model.md).
The bounded setup command/event/replay slice is documented in
[`docs/architecture/game-setup.md`](docs/architecture/game-setup.md).
The production decision that domain events, rather than commands, are
authoritative is recorded in
[`docs/architecture/authoritative-events.md`](docs/architecture/authoritative-events.md).

The rules engine in `oathdigital.engine` has no UI or asset dependency.
`oathdigital.catalog` loads selected, source-verified catalog projections into
typed definitions and rejects incompatible or unresolved executable data.
`oathdigital.presentation` is a small boundary between rules/application code
and a future terminal, web, or native renderer:

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

## Build

Use the project-local wrapper:

```sh
./sbtw clean compile test Test/compile
```

Run the complete unit-test suite with:

```sh
./sbtw test
```

To compile both production and test sources without running tests:

```sh
./sbtw Test/compile
```

Interactive UI:
```sh
./sbtw frontend/fastLinkJS
./sbtw 'runMain oathdigital.server.OathServer var/oathdigital docs/catalog/new-foundations-component-catalog.json'
```

And then open `http://localhost:8080/?mode=server`. Keep the server terminal open while testing. curl http://localhost:8080/health. Stop it cleanly with Ctrl-C; do not delete var/oathdigital if you want existing games preserved.
