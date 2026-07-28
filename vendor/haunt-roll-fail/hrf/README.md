# Haunt Roll Fail shared-engine reference

This directory contains selected shared framework files copied from
`haunt-roll-fail/haunt-roll-fail` for use while designing Oath Digital.

Upstream: https://github.com/haunt-roll-fail/haunt-roll-fail

The selected files cover the architectural areas most relevant to an
asynchronous board game:

- `base.scala`: actions, continuations, validation, and rule execution.
- `journal.scala`: append-only journals and replay journal implementations.
- `timeline.scala`: action timelines, reconstruction, undo traversal, and
  cached states.
- `serialize.scala`: textual action serialization and parsing.
- `runner.scala`: client-side orchestration, optimistic writes, polling, and
  replay-driven state reconstruction.
- `tracker.scala` and `new-*-tracker.scala`: identity/location tracking
  approaches for board pieces and cards.

These files are preserved as reference/vendor source rather than wired into a
build. They depend on other parts of the upstream Scala/Scala.js framework and
are not expected to compile independently. Oath Digital can either port the
relevant concepts into its eventual stack or deliberately adopt more of the
upstream framework later.

The upstream MIT license is included as `LICENSE` and must remain with copied
or substantially reused portions.
