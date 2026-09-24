# Scala 3 Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the JVM `root` project and the Scala.js `frontend` project with the Scala 3.9 LTS compiler instead of Scala 2.13.16, with every existing gate still passing.

**Architecture:** Staged migration. Tasks 1–3 are Scala 2.13-compatible preparation commits that can land on `main` one at a time. Task 4 is the single switch commit that changes `scalaVersion`, the compiler flags, and the coverage floor. Source keeps its Scala 2 style except where Scala 3 forces a change.

**Tech Stack:** sbt 1.11.2 (via `./sbtw`), Scala 2.13.16 → 3.9.0, Scala.js 1.20.1 → 1.22.0, sbt-scoverage 2.4.4, munit 1.0.4, Akka 2.8.5, akka-http 10.5.3, Slick 3.5.2.

**Spec:** `docs/superpowers/specs/2026-09-24-scala-3-migration-design.md`

## Global Constraints

- Scope is a compiler switch only. Do not convert `implicit` to `given`/`using`, `implicit class` to `extension`, the frontend package object to top-level definitions, sealed traits to `enum`, or braces to indentation syntax.
- Target version: Scala `3.9.0`, the latest patch of the current LTS line (released 2026-09-03). If a newer 3.9.x patch exists when Task 4 starts, use it. Fall back to `3.3.8` only if a toolchain component in Task 4 fails on 3.9 and has no fixed release; record the reason in the commit message.
- Dependency versions stay as they are. Every dependency has a `_3` artifact at its current version (checked on Maven Central on 2026-09-24).
- Scala 3.9 requires JDK 17 or newer and Scala.js 1.22.0. `./sbtw` defaults to `.tooling/jdk-17.0.19+10`; release verification uses Java 21.
- Every commit passes the gate in the "Gate" section below.
- Commit messages are plain English Conventional Commits, ending with the `Co-Authored-By` line from the session instructions.
- `main` is shared with concurrent sessions and HEAD moves. Run `git status` and `git log --oneline -3` before every commit, and never amend, reset, or rebase commits you did not just create.

## Gate

Run from the repository root. All four must pass before any commit.

```bash
./sbtw test frontend/test
```
Expected: `[success]` with no failed tests.

```bash
./sbtw clean coverage test coverageReport
```
Expected: `[success]`; the log contains `Statement coverage.: NN.NN%` at or above `coverageMinimumStmtTotal`.

```bash
python3 scripts/check-architecture.py
```
Expected: exit code 0, no error lines.

```bash
./sbtw verifyReleaseVersion
```
Expected: `[success]`.

## Execution setup

Work in a git worktree on branch `scala-3-migration` (superpowers:using-git-worktrees). Tasks 1–3 may be merged to `main` individually as soon as each passes the gate. Task 4 merges last, after a rebase onto the latest `main`.

## File map

| File | Change | Task |
| --- | --- | --- |
| `src/test/scala/oathdigital/persistence/HsqldbDatabaseOwnerSuite.scala` | Add characterization test for `retryTransientLock` | 1 |
| `src/main/scala/oathdigital/persistence/HsqldbDatabaseOwner.scala` | Replace `do/while` with a tail-recursive loop | 1 |
| `src/main/scala/oathdigital/server/OathServer.scala` | Explicit `ExecutionContext` type | 2 |
| `build.sbt` | `-Xsource:3` (Task 2); version, flags, coverage floor (Task 4) | 2, 4 |
| Files reported by the compiler | Minimal migration fixes | 2, 4 |
| `project/plugins.sbt` | `sbt-scalajs` 1.20.1 → 1.22.0 | 3 |

---

### Task 1: Remove the `do/while` loop from `retryTransientLock`

Scala 3 removed `do { ... } while (...)`. The method's current behavior must survive exactly, including one subtle rule: after a backoff sleep, the deadline is checked again before the next attempt.

**Files:**
- Modify: `src/main/scala/oathdigital/persistence/HsqldbDatabaseOwner.scala` (imports at lines 1–16; method at lines 149–171)
- Test: `src/test/scala/oathdigital/persistence/HsqldbDatabaseOwnerSuite.scala` (append a test after the one at line 119)

**Interfaces:**
- Consumes: `HsqldbDatabaseOwner.retryTransientLock[A](attempt: () => Either[OpenAttemptFailure, A], maxAttempts: Int, deadlineNanos: Long, nanoTime: () => Long, sleep: Long => Unit, retryable: Throwable => Boolean): Either[OpenAttemptFailure, A]`, `HsqldbDatabaseOwner.ConnectionFailure(error: Throwable)`
- Produces: the same signature, unchanged.

The existing test "reopen retry is limited to the exact lock-heartbeat failure" already covers: exhausting `maxAttempts`, a non-connection failure stopping at once, and an already-expired deadline. The new test covers the three paths it does not: success after a retry, a connection failure that is not retryable, and a deadline that expires during the backoff sleep.

- [ ] **Step 1: Write the characterization test**

Append inside `class HsqldbDatabaseOwnerSuite`, after the closing brace of the test that starts at line 119:

```scala
  test("reopen retry returns the first success and rechecks the deadline after backoff") {
    val failure = HsqldbDatabaseOwner.ConnectionFailure(
      new RuntimeException("transient"))

    var attempts = 0
    var sleeps = 0
    val recovered = HsqldbDatabaseOwner.retryTransientLock[Int](
      () => {
        attempts += 1
        if (attempts == 1) Left(failure) else Right(7)
      },
      maxAttempts = 3,
      deadlineNanos = 100L,
      nanoTime = () => 0L,
      sleep = _ => sleeps += 1,
      retryable = _ => true
    )
    assertEquals(recovered, Right(7))
    assertEquals(attempts, 2)
    assertEquals(sleeps, 1)

    attempts = 0
    sleeps = 0
    val notRetryable = HsqldbDatabaseOwner.retryTransientLock[Int](
      () => {
        attempts += 1
        Left(failure)
      },
      maxAttempts = 3,
      deadlineNanos = 100L,
      nanoTime = () => 0L,
      sleep = _ => sleeps += 1,
      retryable = _ => false
    )
    assertEquals(notRetryable, Left(failure))
    assertEquals(attempts, 1)
    assertEquals(sleeps, 0)

    attempts = 0
    sleeps = 0
    val clock = Iterator(0L, 200L)
    val expiredDuringBackoff = HsqldbDatabaseOwner.retryTransientLock[Int](
      () => {
        attempts += 1
        Left(failure)
      },
      maxAttempts = 3,
      deadlineNanos = 100L,
      nanoTime = () => clock.next(),
      sleep = _ => sleeps += 1,
      retryable = _ => true
    )
    assertEquals(expiredDuringBackoff, Left(failure))
    assertEquals(attempts, 1)
    assertEquals(sleeps, 1)
    assert(!clock.hasNext)
  }
```

- [ ] **Step 2: Run the test against the current code**

Run: `./sbtw "testOnly oathdigital.persistence.HsqldbDatabaseOwnerSuite"`
Expected: PASS, all tests. This is a characterization test: it must pass *before* the refactor, proving it describes today's behavior. If it fails, the test is wrong — fix the test, not the code.

- [ ] **Step 3: Replace the loop**

In `HsqldbDatabaseOwner.scala`, add to the `scala.*` import group (keep alphabetical order):

```scala
import scala.annotation.tailrec
```

Replace the body of `retryTransientLock` (from `var attempts = 0` through the final `result`) so the method reads:

```scala
  private[persistence] def retryTransientLock[A](
      attempt: () => Either[OpenAttemptFailure, A],
      maxAttempts: Int,
      deadlineNanos: Long,
      nanoTime: () => Long,
      sleep: Long => Unit,
      retryable: Throwable => Boolean
  ): Either[OpenAttemptFailure, A] = {
    @tailrec
    def loop(attempts: Int): Either[OpenAttemptFailure, A] = {
      val result = attempt()
      result match {
        case Left(ConnectionFailure(error))
            if attempts < maxAttempts && nanoTime() < deadlineNanos &&
              retryable(error) =>
          sleep(ReopenBackoffMillis)
          if (nanoTime() < deadlineNanos) loop(attempts + 1) else result
        case _ => result
      }
    }
    loop(1)
  }
```

Why this is equivalent: the old loop counted the attempt before running it (`attempts` is 1 on the first run), and after a sleep its `while` condition re-evaluated `attempts < maxAttempts` (already known true from the guard) and `nanoTime() < deadlineNanos`. The new code makes the same calls to `nanoTime` in the same order.

- [ ] **Step 4: Run the suite again**

Run: `./sbtw "testOnly oathdigital.persistence.HsqldbDatabaseOwnerSuite"`
Expected: PASS, all tests.

- [ ] **Step 5: Run the gate** (see "Gate"). Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/persistence/HsqldbDatabaseOwner.scala src/test/scala/oathdigital/persistence/HsqldbDatabaseOwnerSuite.scala
git commit -m "refactor(persistence): drop do/while from the reopen retry loop

Scala 3 removed do/while. A tail-recursive loop keeps the same attempt
count, backoff, and post-sleep deadline check; a new test pins the
paths the existing one did not cover."
```

---

### Task 2: Turn on `-Xsource:3` and fix what it reports

With `-Xsource:3`, Scala 2.13 reports Scala 3 incompatibilities in the `scala3-migration` category, and these are compile errors by default. Once the flag is on, new code on `main` cannot reintroduce them.

**Files:**
- Modify: `src/main/scala/oathdigital/server/OathServer.scala:4` and `:41`
- Modify: `build.sbt` (both `scalacOptions` blocks: the `root` block and the `frontend` block)
- Modify: whatever files the compiler reports in Step 3

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `scalacOptions` in both projects equal to `Seq("-deprecation", "-feature", "-unchecked", "-Xlint", "-Xsource:3")`. Task 4 replaces these.

- [ ] **Step 1: Fix the known incompatibility in `OathServer`**

Line 4 becomes:

```scala
import scala.concurrent.{Await, ExecutionContext, Future}
```

Line 41 becomes:

```scala
    implicit val executionContext: ExecutionContext = system.executionContext
```

- [ ] **Step 2: Add the flag to both projects**

In `build.sbt`, both `scalacOptions ++= Seq(...)` blocks become:

```scala
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlint",
      "-Xsource:3"
    ),
```

- [ ] **Step 3: Compile everything and list the errors**

Run: `./sbtw Test/compile frontend/Test/compile 2>&1 | tee var/scala3-migration/xsource3.log | grep -E '\[error\]' | head -80`
Expected: either `[success]`, or a list of errors. Count affected files with:
`grep -oE '\[error\] /[^:]+\.scala' var/scala3-migration/xsource3.log | sort -u | wc -l`

- [ ] **Step 4: Fix each error using this table**

| Compiler message (contains) | Fix |
| --- | --- |
| `Implicit definition must have explicit type` | Write the inferred type the message names, e.g. `implicit val x: ExecutionContext = ...`; add the import it needs. |
| `Auto-application to \`()\` is deprecated` or `...must be called with ()` | Add `()` at the call site: `iterator.next` → `iterator.next()`. |
| `Unicode escapes in raw interpolations` | Move the escape out of the `raw"..."` string or use `s"..."`. |
| `Lines starting with an operator` / infix continuation | Put the operator at the end of the previous line. |
| `eta-expansion` of a zero-argument method (`f _`) | Replace `f _` with `() => f()`. |
| `access modifiers for \`apply\`/\`copy\`` on a case class with a non-public constructor | **Stop.** This changes a public API shape; report the file and message to your human partner before changing anything. |
| Anything else | If the fix is a local, type-preserving edit of one or two lines, make it and note it in the commit message. Otherwise **stop** and report the message. |

Keep every fix valid on both Scala 2.13 and Scala 3 — do not use Scala 3-only syntax.

- [ ] **Step 5: Recompile**

Run: `./sbtw Test/compile frontend/Test/compile`
Expected: `[success]`. Repeat Step 4 until it is.

- [ ] **Step 6: Run the gate.** Expected: all pass.

- [ ] **Step 7: Commit**

If Step 4 touched more than about 15 files, split into one commit per row of the table, each passing the gate, with the `build.sbt` flag change in the last commit. Otherwise one commit:

```bash
git add build.sbt src/main/scala/oathdigital/server/OathServer.scala
git add -u src frontend/src shared
git commit -m "build: compile with -Xsource:3 to block Scala 3 incompatibilities

Adds the flag to both projects and fixes what it reports, starting with
the untyped implicit execution context in OathServer. Every fix is valid
on both 2.13 and Scala 3."
```

---

### Task 3: Upgrade Scala.js to 1.22.0

Scala 3.9's compiler contains its own Scala.js backend fixed at 1.22.0, and the linker must be at least that version. Doing this on 2.13 first keeps the switch commit small.

**Files:**
- Modify: `project/plugins.sbt:1`

**Interfaces:**
- Consumes: nothing.
- Produces: `sbt-scalajs` 1.22.0 on the build classpath.

- [ ] **Step 1: Bump the plugin**

`project/plugins.sbt` line 1 becomes:

```scala
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
```

- [ ] **Step 2: Link and test the frontend**

Run: `./sbtw frontend/test frontend/fullLinkJS`
Expected: `[success]`; all frontend tests pass under jsdom.

- [ ] **Step 3: Check the packaged frontend**

Run: `./sbtw smokeUniversal && sh scripts/smoke-packaged-distribution.sh target/universal/stage 18090`
Expected: `[success]` from sbt, then the smoke script exits 0.

- [ ] **Step 4: Run the gate.** Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add project/plugins.sbt
git commit -m "build: upgrade Scala.js to 1.22.0

Scala 3.9 LTS ships a Scala.js 1.22.0 backend, so the linker has to be
at least that version before the compiler switch."
```

---

### Task 4: Switch to Scala 3.9

**Files:**
- Modify: `build.sbt` (line 1 `scalaVersion`; both `scalacOptions` blocks; `coverageMinimumStmtTotal` and its comment)
- Modify: whatever files the Scala 3 compiler reports in Step 4

**Interfaces:**
- Consumes: Tasks 1–3 merged (no `do/while`, `-Xsource:3` clean, Scala.js 1.22.0).
- Produces: the finished migration.

- [ ] **Step 1: Record the 2.13 baselines**

On the current commit (still 2.13), run:

```bash
./sbtw clean coverage test coverageReport 2>&1 | grep 'Statement coverage'
```

Write the percentage down as `COVERAGE_213`.

Then create a database with the 2.13 build for the compatibility check in Step 9:

```bash
./sbtw Universal/stage
rm -rf var/scala3-migration && mkdir -p var/scala3-migration
OATH_DATABASE_PATH=var/scala3-migration/database OATH_PORT=18091 OATH_OPEN_BROWSER=false target/universal/stage/bin/oathdigital
```

Open `http://localhost:18091`, create a game, and take at least one action. Write down the game's identifier. Stop the server with Ctrl-C and confirm the log ends with `Oath Digital database closed`.

- [ ] **Step 2: Check for a newer 3.9 patch**

Open `https://www.scala-lang.org/download/all.html`. Use the highest `3.9.x` release. Call it `SCALA3` below (3.9.0 on 2026-09-24).

- [ ] **Step 3: Change version and flags**

`build.sbt` line 1 becomes (substituting `SCALA3`):

```scala
ThisBuild / scalaVersion := "3.9.0"
```

Both `scalacOptions ++= Seq(...)` blocks become:

```scala
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Wunused:imports,privates,locals,implicits,nowarn"
    ),
```

If the compiler rejects any `-Wunused` choice, run `./sbtw 'set scalacOptions += "-Wunused:help"' compile` to list valid choices, drop the rejected one, and note it in the commit message.

- [ ] **Step 4: Compile everything and count affected files**

Run: `./sbtw clean Test/compile frontend/Test/compile 2>&1 | tee var/scala3-migration/scala3.log | grep -E '\[error\]' | head -80`
Then: `grep -oE '\[error\] /[^:]+\.scala' var/scala3-migration/scala3.log | sort -u | wc -l`

**Stop condition:** if more than 30 files have errors, or any error needs a design decision (a changed public signature, a different implicit being selected, a behavior change), stop. Revert Step 3 with `git checkout -- build.sbt`, move the fixes that are valid on 2.13 into new Stage 1 commits following Task 2's rules, and report back before retrying this task.

If a toolchain component (sbt-scoverage, Scala.js, a dependency) fails on 3.9 with no fixed release, set `scalaVersion` to `3.3.8` instead and continue; record why in the commit message.

- [ ] **Step 5: Fix the remaining errors**

Use the table from Task 2 Step 4, plus these Scala 3-only cases:

| Compiler message (contains) | Fix |
| --- | --- |
| `is not a member of` after a wildcard import of an implicit | Import the implicit by name. |
| `ambiguous given instances` / `ambiguous implicit arguments` | Pass the intended instance explicitly at the call site. |
| `method ... must be called with () argument` | Add `()` at the call site. |
| `Found: ... Required: ...` where 2.13 inferred a wider type | Add the type annotation 2.13 inferred to the `val`/`def`. |

Fixes here may use Scala 3-only syntax only when no cross-compatible form exists. Rerun Step 4's compile until `[success]`.

- [ ] **Step 6: Run all tests**

Run: `./sbtw test frontend/test`
Expected: `[success]`, no failed tests. A test that passed on 2.13 and fails now is a behavior change: diagnose it with superpowers:systematic-debugging before changing any code.

- [ ] **Step 7: Re-baseline the coverage floor**

Run: `./sbtw clean coverage test coverageReport 2>&1 | grep 'Statement coverage'`
Call the value `COVERAGE_3`. Set the floor to `COVERAGE_3` rounded down to one decimal (e.g. 83.47 → 83.4; 85.92 → 85.9), whether it went down or up. Replace the comment and setting in `build.sbt` with (filling in both numbers):

```scala
    // Ratchet: pinned at the baseline measured when scoverage was adopted
    // (stmt 84.11% on Scala 2.13). Re-baselined on the Scala 3 switch to the
    // measured stmt COVERAGE_3%, from COVERAGE_213% on 2.13 the commit
    // before: Scala 3 instruments statements differently, so the change is
    // in measurement, not tests. Raise it as coverage improves; the goal is
    // 100% with justified $COVERAGE-OFF$ exemptions. Enforced by
    // `coverageReport`.
    coverageMinimumStmtTotal := 83.4,
```

Rerun `./sbtw clean coverage test coverageReport`. Expected: `[success]`.

- [ ] **Step 8: Package and smoke test under Java 21**

```bash
JAVA_HOME="$PWD/.tooling/jdk-21.0.12.1+1/Contents/Home" ./sbtw clean smokeUniversal
sh scripts/smoke-packaged-distribution.sh target/universal/stage 18090
```
Expected: `[success]`, then the smoke script exits 0.

- [ ] **Step 9: Manual end-to-end run with the 2.13 database**

```bash
OATH_DATABASE_PATH=var/scala3-migration/database OATH_PORT=18091 OATH_OPEN_BROWSER=false target/universal/stage/bin/oathdigital
```

Open `http://localhost:18091`. Expected: the game created in Step 1 loads with its action applied; you can take one more action; the browser console shows no errors. Stop with Ctrl-C; expected log ends with `Oath Digital database closed`.

- [ ] **Step 10: Rest of the gate**

Run `python3 scripts/check-architecture.py` and `./sbtw verifyReleaseVersion`. Expected: both pass.

- [ ] **Step 11: Rebase and re-verify**

```bash
git rebase main
```

If the rebase brought in new commits, rerun Steps 4–10 (compile, tests, coverage, smoke). New feature code already compiled under `-Xsource:3`, so only Scala 3-only errors can appear.

- [ ] **Step 12: Commit**

```bash
git add build.sbt
git add -u src frontend/src shared
git commit -m "build: switch to Scala 3.9 LTS

Both projects now compile with Scala 3.9.0. -Xlint and -Xsource:3 give
way to the closest Scala 3 warnings (-Wunused). The coverage floor moves
from 84.0 to the Scala 3 measurement because the compiler instruments
statements differently. Verified with the full test suite, coverage,
packaged smoke test under Java 21, and a 2.13-created database."
```

- [ ] **Step 13: Tell other sessions**

After merging to `main`, tell your human partner that other checkouts and worktrees need `./sbtw clean` once, because stale 2.13 output in `target/` can confuse incremental compilation.
