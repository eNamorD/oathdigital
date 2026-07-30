# HRF UI reuse feasibility spike

## Outcome

The useful boundary is a small Scala.js project that compiles Oath Digital's
portable catalog model, identity/resource values, event engine, and setup rules
unchanged. A thin browser adapter renders those types directly. HRF remains a
design/source reference; its action-authoritative runner is not in the runtime.

The page uses an in-memory demo catalog assembled from code so the spike neither
loads nor changes catalog data. It starts the bounded setup, shows ordered
participants and the 2/3/3 eight-site layout, makes every in-play site a legal
choice for the active participant, applies `SetupRules`, records emitted
`SetupEvent`s, and accepts/displays only state reconstructed by
`EventReplayEngine`. A replay failure or disagreement with the rule
transition is an explicit session error and the proposed event batch is not
accepted.

## Dependency inventory

The audit covered both `vendor/haunt-roll-fail/hrf` and the full checkout at
`/Users/roman/projects/haunt-roll-fail/haunt-roll-fail`.

| HRF file or area | Decision | Reason |
| --- | --- | --- |
| `build.sbt`, `project/project.sbt` | Adapted | Reused Scala 2.13.16, sbt 1.11.2, Scala.js 1.19.0, main-module initialization, and the Scala.js DOM dependency shape. Oath uses released `scalajs-dom` 2.8.0 rather than HRF's local snapshot. |
| `html.scala`: `AttachmentPoint`, `ElementAttachmentPoint` | Extracted/adapted as `HrfDomAdapter.scala` | The stable mount/clear/replace lifecycle is valuable and has no game semantics. The extracted adapter uses standard DOM nodes instead of HRF `Elem`, `Resources`, and image loading. |
| `elem.scala`, `styles.scala` | Rejected for the spike | The DSL is capable but requires `colmat`, reflection-based style naming, logger helpers, custom match/option syntax, and a large CSS vocabulary. Native DOM plus one CSS file is much smaller. |
| `html.scala` materializer and image resources | Rejected | Coupled to `elem.scala`, `loader.scala`, generated styles, image wrappers, and asset keys. The spike explicitly needs image-independent fallbacks. |
| `web.scala` | Rejected | Mostly global browser utilities, URL/local-storage helpers, downloading, and timing behavior not needed for bounded setup. |
| `ui.scala`, pane/tracker files | Rejected | Depend on HRF `Meta`, `Game`, faction/action types, element DSL, resources, and mutable pane layout. They do not form a game-neutral package boundary. |
| `runner.scala` | Concept only; runtime rejected | Its replay-driven redraw and append-only stream are useful precedents, but the implementation owns HRF actions, timelines, bots, milestones, polling, and server protocols. Adopting it would make actions authoritative. Oath instead uses its existing `SetupRules`, `SetupEvent`, and `EventReplayEngine`. |
| `base.scala`, `journal.scala`, `timeline.scala`, `serialize.scala` | Rejected from browser runtime | Vendored for architectural reference and deeply coupled through HRF collections, actions, continuations, serializers, and timelines. Oath already has the event-authoritative equivalents required by this slice. |
| `loader.scala`, `sprites.scala`, `canvas.scala`, `voice.scala` | Rejected | Asset/canvas/audio infrastructure is outside this image-independent proof and brings unrelated coupling. |
| HRF source files reused unchanged | None | No UI/runtime file is independently compilable at a cost lower than the adapter. The vendored reference and MIT license remain unchanged. |

The exact recommended HRF source subset for a later extraction is the
game-neutral portion of `html.scala` (`HtmlBlock`, `AttachmentPoint`,
`CommentsAttachmentPoint`, and `ElementAttachmentPoint`) after replacing
`Elem`/`Resources` parameters with a neutral render callback. `elem.scala` may
be reconsidered only if multiple game screens demonstrate that a typed view DSL
outweighs its dependency surface.

## Build boundary and run instructions

`frontend` is an independent Scala.js subproject. Its build explicitly includes
only five portable authoritative sources from the root project:

- `model/Identity.scala`
- `model/Resources.scala`
- `catalog/CatalogModel.scala`
- `engine/Engine.scala`
- `setup/Setup.scala`

This explicit list prevents JVM-only catalog loading and unrelated domain code
from leaking into the browser boundary.

From the repository root:

```text
./sbtw frontend/fastLinkJS
python3 -m http.server 8000 --directory frontend
```

Then open `http://localhost:8000/`. Run focused tests with:

```text
./sbtw frontend/test
```

Scala.js tests require Node.js on `PATH`. The verified environment used Node
from the Codex workspace runtime:

```text
env PATH=/Users/roman/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:/usr/bin:/bin:/usr/sbin:/sbin \
  ./sbtw frontend/test frontend/fastLinkJS
```

Outside Codex, install a supported Node.js release and confirm `node --version`
works before running the same sbt tasks. Linking the browser page does not
replace this prerequisite: the Scala.js test framework launches Node.

## Known integration risks

- The source-file allowlist is deliberately narrow. If portable shared code
  grows, a cross-project/shared-source layout will be easier to maintain.
- The demo catalog proves the UI/rules boundary, not catalog transport. A later
  application service should supply the executable catalog without moving file
  IO into Scala.js.
- Browser persistence, multiplayer transport, optimistic concurrency, and
  production asset resolution are intentionally outside this spike.
