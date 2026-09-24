# Scala 3 migration — design

Date: 2026-09-24
Status: approved design, not yet planned or implemented

## Goal

Build both sbt projects (the JVM `root` and the Scala.js `frontend`) with the
Scala 3 LTS compiler instead of Scala 2.13.16, with every existing gate still
passing. This is a compiler switch only: source code keeps its Scala 2 style
except where Scala 3 forces a change.

## Decisions

| Topic | Decision |
| --- | --- |
| Scope | Compiler switch only; no syntax modernization |
| Target version | Latest patch of the current Scala 3 LTS line: 3.9.0 (released 2026-09-03); 3.3.8 only as a fallback if a toolchain component fails on 3.9 |
| Strategy | Staged on `main`: 2.13-compatible preparation first, then one switch commit |
| Coverage ratchet | Re-baseline to the measured Scala 3 value, rounded down to one decimal, whether it falls or rises |
| Dependencies | Same versions, `_3` artifacts via `%%`/`%%%`; bump only if a version has no `_3` artifact |

Out of scope, each a candidate for its own later spec: `given`/`using`,
extension methods, top-level definitions replacing the frontend package object,
`enum` ADTs, opaque types, and braceless syntax.

## Starting point

Measured on 2026-09-24:

- 658 Scala files, about 82,500 lines, across `src`, `frontend/src`, and
  `shared`.
- sbt 1.11.2, Java 21, Scala.js 1.20.1, sbt-native-packager 1.11.7,
  sbt-scoverage 2.4.4.
- Dependencies: ujson 4.4.3, Slick 3.5.2 with slick-hikaricp, HSQLDB 2.7.4,
  Akka 2.8.5 (actor-typed, stream), akka-http 10.5.3, logback 1.5.18,
  munit 1.0.4, munit-scalacheck 1.0.0, scalajs-dom 2.8.0.
- No macros, runtime reflection, shapeless, XML literals, existential types,
  or Java serialization.
- Slick is used only through `SimpleDBIO` with plain JDBC; there is no lifted
  embedding (`TableQuery`) and no `sql"..."` interpolation.
- 21 `implicit` occurrences, nearly all `ActorSystem` or `ExecutionContext`
  values in route tests.
- `scalacOptions` in both projects: `-deprecation -feature -unchecked -Xlint`.
  There is no fatal-warnings flag.

Known Scala 3 incompatibilities found by inspection:

- `src/main/scala/oathdigital/persistence/HsqldbDatabaseOwner.scala:159` uses
  `do { ... } while (...)`, which Scala 3 removed.
- `src/main/scala/oathdigital/server/OathServer.scala:41` declares
  `implicit val executionContext = system.executionContext` without a type;
  Scala 3 requires an explicit type on implicit definitions.
- `-Xlint` is a Scala 2 option.

Compiles unchanged on Scala 3 and stays as is: the package object in
`frontend/src/main/scala/oathdigital/frontend/package.scala`, the
`implicit final class ... extends AnyVal` in
`src/main/scala/oathdigital/gameplay/actions/Search.scala`, all `implicit`
parameters, and all sealed-trait ADTs.

## Stage 1: preparation on 2.13.16

Every commit in this stage compiles on 2.13.16 and is also valid Scala 3, so
it lands on `main` independently and never needs reverting.

1. Rewrite the `do/while` loop in `HsqldbDatabaseOwner` as a `while` loop with
   identical retry semantics: the attempt runs at least once, and the loop
   stops on a non-retryable result, on `maxAttempts`, or at the deadline. The
   existing suite covers this method; add a test only for a retry-count edge
   case it does not already exercise.
2. Give `OathServer`'s implicit execution context the explicit type
   `ExecutionContext`.
3. Add `-Xsource:3` to `scalacOptions` in both projects. Scala 2.13 reports
   the resulting diagnostics in the `scala3-migration` category, which is
   an error by default (`-Wconf:cat=scala3-migration:e`), so no extra
   `-Wconf` flag is needed. Fix everything the flag reports, grouped by kind
   of change rather than by file. The flag is added in the same commit as
   the last fix, so `main` is never red.
4. Upgrade `sbt-scalajs` from 1.20.1 to 1.22.0. Scala 3.9's compiler
   contains a Scala.js 1.22.0 backend, so the linker must be at least that
   version.

With the flags on, new feature code on `main` cannot introduce Scala 3
incompatibilities while Stage 2 is pending.

## Stage 2: the switch commit

A single commit:

- `ThisBuild / scalaVersion` becomes the latest Scala 3 LTS patch release.
  `frontend` inherits it.
- In both projects, `scalacOptions` drops `-Xlint` and `-Xsource:3`. It
  keeps `-deprecation -feature -unchecked` and adds
  `-Wunused:imports,privates,locals,implicits,nowarn`, the closest Scala 3
  match for the unused checks that 2.13's `-Xlint` enabled. The remaining
  `-Xlint` checks have no Scala 3 equivalent and are not replaced.
- Dependency coordinates do not change. `project/` does not change, because
  sbt compiles the build with its own Scala 2.12.
- Any error only the Scala 3 compiler reports is fixed here with the smallest
  change that compiles. If those fixes exceed about 30 files, or any of them
  needs a design decision, stop: move them back into Stage 1 as
  2.13-compatible commits and retry the switch.
- `coverageMinimumStmtTotal` is set to the measured Scala 3 statement
  coverage, rounded down to one decimal. The comment above it records the
  2.13 baseline (84.11%), the Scala 3 baseline, and that the change reflects
  different instrumentation rather than lost or gained tests.

Pre-switch checks, each a separate commit on `main` if action is needed:

- Every dependency has a `_3` artifact at its current version. If one does
  not, bump it to the nearest version that does.
- sbt-scoverage 2.4.4 supports the chosen Scala 3 version. If not, upgrade
  it.

## Verification

Every Stage 1 commit passes:

- `test`
- `frontend/test`
- `coverageReport`
- `scripts/check-architecture.py`

The switch commit additionally passes:

- `smokeUniversal`, which exercises packaging and the Scala.js link that
  feeds `resourceGenerators`.
- `scripts/smoke-packaged-distribution.sh` against the staged distribution.
- A manual run: start the server, open the frontend, and play a turn, to
  prove Akka HTTP routing, Slick/HSQLDB persistence, and the linked frontend
  work end to end.
- Opening an HSQLDB database created by a 2.13 build, to prove existing saves
  still load.

## Rollback and coordination

- Stage 2 is one commit; `git revert` undoes it completely.
- `main` is shared with concurrent sessions. Do the switch in a worktree,
  rebase onto the latest `main` immediately before merging, rerun the full
  gate, and land it promptly so no 2.13-only code slips in between.
- After the switch lands, other sessions need a clean build, because stale
  2.13 output in `target/` can confuse incremental compilation.
- The release workflow calls sbt through scripts that do not depend on the
  Scala version, so it needs no change.

## Done when

- Both projects compile with the Scala 3 LTS compiler.
- All gates listed under Verification pass.
- The coverage floor is re-baselined, with its comment updated.
- The release workflow runs without edits.
