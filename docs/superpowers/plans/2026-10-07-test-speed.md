# Test Speed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the three engine hot spots that make the campaign tests slow,
without changing any game result, and commit a repeatable way to measure and
profile the tests.

**Architecture:** Task 1 adds `scripts/profile-tests.py` (gate timing, JUnit
ranking, Java Flight Recorder frames) and records a baseline. Task 2 caches
one card index per `OathGame` instance in a `lazy val`. Task 3 indexes a
command's powers by window once per `WalkerPowers` instance, so
`ContributionCollector.gather` stops rebuilding every power's contribution map.

**Tech Stack:** Scala 3.9.0, munit, sbt through `./sbtw`, Python 3, JDK 17 `jfr`.

**Spec:** [docs/superpowers/specs/2026-10-07-test-speed-design.md](../specs/2026-10-07-test-speed-design.md)

**Execution rulings:**
- Ruling (Task 1): the script streams `jfr print` as text, not JSON, and
  reports the `WATCHED` frames and the load average. The JSON form of a
  256-frame recording ran to 15 GB. The code block in Task 1 Step 1 is the first draft;
  `scripts/profile-tests.py` is the authority.
- Ruling: commit trailers use `Claude Sonnet 5.5`, the model that executes
  the plan, not the Opus trailer in the commit commands below.

## Global Constraints

- No game result changes. The gate stays green, and no existing test changes
  except the four suites that call `ContributionCollector.gather` directly
  (Task 3).
- No timing guard in the tests, and no counting hook in production code
  (spec decision 6).
- The state invariant still runs on every pipeline run, including runs inside
  a search (spec decision 3).
- Gate: `./sbtw root/test frontend/test`. Compiler flags include `-Werror`
  and `-Wunused:imports`, so remove any import an edit leaves unused.
- `scripts/check-architecture.py` caps production Scala files at 800 lines.
- Work in one worktree: `git worktree add .worktrees/test-speed -b test-speed main`,
  then `ln -s /Users/roman/projects/oathdigital/.tooling .worktrees/test-speed/.tooling`.
  Merge into local main at the end (origin lags local main).
- Never stage `docs/ROADMAP.md`.
- Commit trailer: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Measurements: each task appends its "after" numbers to the
  [Measurements](#measurements) section of this plan, in its own commit. The
  previous row is that task's "before".

---

### Task 1: Profiling script, guide and baseline

**Files:**
- Create: `scripts/profile-tests.py`
- Create: `docs/testing/profiling.md`
- Modify: `docs/superpowers/plans/2026-10-07-test-speed.md` (Measurements, "Baseline" row)

**Interfaces:**
- Produces: `scripts/profile-tests.py --gate` and
  `scripts/profile-tests.py <root test filter>...`, writing
  `target/profile/gate.txt` and `target/profile/profile.txt`. Tasks 2 and 3
  run both to record their numbers.

- [ ] **Step 1: Write the script**

`scripts/profile-tests.py`:

```python
#!/usr/bin/env python3
"""Measure and profile the root tests.

  scripts/profile-tests.py --gate
      Runs the gate (root/test frontend/test), reports its wall-clock and
      CPU time, and ranks the root suites and tests by time.

  scripts/profile-tests.py 'oathdigital.gameplay.powers.campaign.*'
      Runs the matching root suites under Java Flight Recorder, ranks them,
      and lists the hottest frames.

Everything is written under target/profile/. See docs/testing/profiling.md.
"""

import argparse
import collections
import json
import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "target/profile"
REPORTS = ROOT / "target/test-reports"
STACK_DEPTH = 256


def java_home() -> Path:
    configured = os.environ.get("JAVA_HOME")
    if configured:
        return Path(configured)
    return ROOT / ".tooling/jdk-17.0.19+10/Contents/Home"


def run_sbt(args, log_path):
    """Runs ./sbtw with args and copies its output to log_path.

    Returns the exit code, the wall-clock seconds and the CPU seconds of sbt
    and everything it started."""
    before = os.times()
    started = time.monotonic()
    with open(log_path, "w", encoding="utf-8") as log:
        process = subprocess.Popen(
            [str(ROOT / "sbtw"), *args], cwd=ROOT,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        for line in process.stdout:
            sys.stdout.write(line)
            log.write(line)
        code = process.wait()
    wall = time.monotonic() - started
    after = os.times()
    cpu = (after.children_user - before.children_user
           + after.children_system - before.children_system)
    return code, wall, cpu


def rank(since, top):
    """Ranks the root JUnit reports written after `since`."""
    suites, tests = [], []
    for path in REPORTS.glob("TEST-*.xml"):
        if path.stat().st_mtime < since:
            continue
        report = ET.parse(path).getroot()
        suite = report.get("name")
        suites.append((float(report.get("time") or 0),
                       int(report.get("tests") or 0), suite))
        for case in report.iter("testcase"):
            tests.append((float(case.get("time") or 0),
                          suite.rsplit(".", 1)[-1], case.get("name")))
    suites.sort(reverse=True)
    tests.sort(reverse=True)
    lines = [
        f"Root suites: {len(suites)}, tests: {sum(n for _, n, _ in suites)}, "
        f"summed suite time: {sum(t for t, _, _ in suites):.1f} s",
        "",
        f"Slowest {top} suites:",
    ]
    lines += [f"{t:8.2f} s {n:4d} tests  {name}" for t, n, name in suites[:top]]
    lines += ["", f"Slowest {top} tests:"]
    lines += [f"{t:8.3f} s  {suite} :: {name}" for t, suite, name in tests[:top]]
    return lines


def frame_name(frame):
    method = frame["method"]
    return method["type"]["name"].replace("/", ".") + "." + method["name"]


def frames(recording, top):
    """Lists the hottest frames of the samples that ran project code.

    Self: the frame on top of the stack, whatever its package.
    Inclusive: every oathdigital frame anywhere on the stack, once per sample."""
    samples = OUT / "samples.json"
    with open(samples, "w", encoding="utf-8") as out:
        subprocess.run(
            [str(java_home() / "bin/jfr"), "print", "--json",
             "--events", "jdk.ExecutionSample",
             "--stack-depth", str(STACK_DEPTH), str(recording)],
            stdout=out, check=True)
    with open(samples, encoding="utf-8") as source:
        events = json.load(source)["recording"]["events"]
    leaf = collections.Counter()
    inclusive = collections.Counter()
    counted = 0
    for event in events:
        stack = event["values"].get("stackTrace") or {}
        names = [frame_name(frame) for frame in stack.get("frames", [])]
        if not any(name.startswith("oathdigital.") for name in names):
            continue
        counted += 1
        leaf[names[0]] += 1
        for name in set(names):
            if name.startswith("oathdigital."):
                inclusive[name] += 1
    if counted == 0:
        return ["No samples ran oathdigital code."]
    lines = [f"Samples that ran oathdigital code: {counted}", "",
             f"Top {top} self frames:"]
    lines += [f"{count * 100 / counted:5.1f}%  {name}"
              for name, count in leaf.most_common(top)]
    lines += ["", f"Top {top * 2} inclusive oathdigital frames:"]
    lines += [f"{count * 100 / counted:5.1f}%  {name}"
              for name, count in inclusive.most_common(top * 2)]
    return lines


def main():
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("filters", nargs="*",
                        help="root test filters, as testOnly takes them")
    parser.add_argument("--gate", action="store_true",
                        help="time the gate instead of profiling")
    parser.add_argument("--top", type=int, default=25,
                        help="how many suites, tests and frames to list")
    options = parser.parse_args()
    if options.gate == bool(options.filters):
        parser.error("give either --gate or test filters")

    OUT.mkdir(parents=True, exist_ok=True)
    since = time.time()
    if options.gate:
        code, wall, cpu = run_sbt(["root/test", "frontend/test"], OUT / "gate.log")
        lines = [f"Gate: {wall:.0f} s wall clock, {cpu:.0f} s CPU, exit {code}", ""]
        lines += rank(since, options.top)
        report = OUT / "gate.txt"
    else:
        recording = OUT / "tests.jfr"
        recording.unlink(missing_ok=True)
        code, wall, _ = run_sbt([
            f"-J-XX:FlightRecorderOptions=stackdepth={STACK_DEPTH}",
            f"-J-XX:StartFlightRecording=filename={recording},"
            "settings=profile,dumponexit=true",
            "root/testOnly " + " ".join(options.filters),
        ], OUT / "profile.log")
        lines = [f"Profile of {' '.join(options.filters)}: "
                 f"{wall:.0f} s wall clock, exit {code}", ""]
        lines += rank(since, options.top) + [""] + frames(recording, options.top)
        report = OUT / "profile.txt"

    report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    print(f"\nWritten to {report.relative_to(ROOT)}")
    return code


if __name__ == "__main__":
    sys.exit(main())
```

Run: `chmod +x scripts/profile-tests.py`

- [ ] **Step 2: Check the argument guard**

Run: `scripts/profile-tests.py; echo exit=$?`
Expected: usage text with "give either --gate or test filters", `exit=2`.

- [ ] **Step 3: Profile the campaign suites**

Run: `scripts/profile-tests.py 'oathdigital.gameplay.powers.campaign.*'`
Expected: exit 0; `target/profile/profile.txt` lists about 299 tests,
`ProcedureWalker` frames high in the inclusive list, and
`oathdigital.model.CardIndex$.from` near 26% inclusive. If the frame names
use another separator or the inclusive list is empty, fix `frame_name` and
rerun. Do not proceed with an empty frames section.

- [ ] **Step 4: Time the gate**

Run `./sbtw root/test frontend/test` once to warm the incremental compile,
then: `scripts/profile-tests.py --gate`
Expected: exit 0; `target/profile/gate.txt` reports root suites with 3050
tests and a summed suite time near 216 s. Gate wall clock near 46 s.

- [ ] **Step 5: Run SearchBudget**

Run: `./sbtw "Test/runMain oathdigital.gameplay.SearchBudget"`
Expected: a line with "Campaign start" and "reading its park" timings.

- [ ] **Step 6: Write the guide**

`docs/testing/profiling.md`:

````markdown
# Profiling the tests

`scripts/profile-tests.py` measures the test suite and finds where its time
goes. It writes everything under `target/profile/`.

## Time the gate

```bash
./sbtw root/test frontend/test
scripts/profile-tests.py --gate
```

The first command warms the incremental compile, so the measured run times
the tests and not a rebuild. The report, `target/profile/gate.txt`, gives:

- the gate's wall-clock and CPU time;
- the summed root suite time, which counts every test thread, so it is
  larger than the wall-clock time;
- the slowest root suites and tests, from the JUnit reports in
  `target/test-reports/` that the run wrote.

Frontend suites are not ranked: their JUnit reports carry no useful times,
and they run in about 11 s.

## Profile some suites

```bash
scripts/profile-tests.py 'oathdigital.gameplay.powers.campaign.*'
```

The arguments are root `testOnly` filters. The script runs them under Java
Flight Recorder (`target/profile/tests.jfr`), then writes
`target/profile/profile.txt` with the same ranking plus two frame lists. Both
count only samples that ran project code:

- **Self frames** are the frames on top of the stack, in any package. A JDK
  frame here, such as `java.lang.ClassValue.getFromBackup`, names a cost that
  project code pays through a library call.
- **Inclusive frames** are the `oathdigital` frames anywhere on the stack. A
  frame at 26% was on the stack in a quarter of the samples, so removing its
  work saves up to a quarter of the time.

To see who calls a hot frame, open `tests.jfr` in JDK Mission Control, or
read the raw stacks in `target/profile/samples.json`.

Profile a small, stable set of suites. The campaign power suites are the
usual choice: they dominate the slowest-suite list and spend most of their
time in `ProcedureWalker`.

## The search budget

`SearchBudget` times the walker's search on a Campaign start against a 50 ms
budget. Run it alongside a profile when a change touches the walker:

```bash
./sbtw "Test/runMain oathdigital.gameplay.SearchBudget"
```

## Reading numbers

Times vary by a few percent between runs, and by more under load. Compare
runs made on the same machine with nothing else running, and treat a change
below about 5% as noise.
````

Run: `python3 scripts/check-markdown-links.py`
Expected: no errors.

- [ ] **Step 7: Record the baseline**

Fill the "Baseline" row and the "Baseline" details of the
[Measurements](#measurements) section from `target/profile/gate.txt`,
`target/profile/profile.txt` and the SearchBudget output. The details list
the ten slowest suites and the top ten inclusive frames.

- [ ] **Step 8: Commit**

```bash
git add scripts/profile-tests.py docs/testing/profiling.md docs/superpowers/plans/2026-10-07-test-speed.md
git commit -m "test: add a profiling script for the test suite, with a baseline

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: One card index per game state

**Files:**
- Modify: `src/main/scala/oathdigital/model/GameState.scala` (`OathGame`, at the end of the file)
- Modify: `src/main/scala/oathdigital/model/CardIndex.scala` (class doc, `object CardIndex`)
- Modify (callers, found by grep in Step 3):
  `gameplay/operations/OperationStateWrites.scala`,
  `gameplay/operations/OperationExecutor.scala` (`OperationStateInvariant.cardIds`),
  `gameplay/operations/CardMovementOperations.scala`,
  `gameplay/operations/PowerOperations.scala`,
  `gameplay/operations/OperationStateAdapter.scala` (two sites),
  `application/PhasePowerProjector.scala`,
  `application/WalkerDecisionProjector.scala` (three sites),
  `application/GamePresentationProjector.scala` (two sites),
  `application/gamelog/ActionLines.scala`,
  `application/gamelog/DetailLines.scala`,
  all under `src/main/scala/oathdigital/`.
- Modify: this plan's Measurements section.

**Interfaces:**
- Produces: `OathGame.cardIndex: Either[Vector[CardIndexProblem], CardIndex]`.
  `CardIndex.from(game, expectedCards)` keeps its signature and its exact
  output.

No new test (spec decision 2). The existing suites cover it:
`CardIndexSuite` (duplicates, missing cards, locations),
`OperationPipelineSuite` and `OperationExecutorSuite` (invariant failures),
and the golden game logs.

- [ ] **Step 1: Cache the index on `OathGame`**

In `GameState.scala`, replace the `OathGame` declaration with:

```scala
final case class OathGame(
    campaign: CampaignState,
    current: CurrentGameState
):
  /** Where every card of this state is, with no expected cards (see
    * `CardIndex.from`). Built at most once per instance.
    *
    * The cache cannot go stale: `OathGame` is immutable and every change makes
    * a new instance through `copy`. Case-class equality, hashing and `copy`
    * read only the constructor fields, so the cache changes none of them, and
    * nothing serializes it.
    */
  lazy val cardIndex: Either[Vector[CardIndexProblem], CardIndex] =
    CardIndex.scan(this, Set.empty)
```

- [ ] **Step 2: Make `CardIndex.from` read the cache**

In `CardIndex.scala`:

1. Replace the line ` * It is intentionally not part of `OathGame` and must not be serialized.`
   in the `CardIndex` class doc with:

   ```scala
    * It is derived, never stored: `OathGame.cardIndex` caches it once per
    * state, outside the case-class fields, and it must not be serialized.
   ```

2. Rename the existing `def from(` to `private[model] def scan(` and drop its
   default argument, so the signature reads
   `private[model] def scan(game: OathGame, expectedCards: Set[CardId]): Either[Vector[CardIndexProblem], CardIndex] =`.
   Its body stays as it is, except that the local `missingProblems` value
   goes, and the problems line becomes
   `val problems = duplicateProblems ++ missingProblems(expectedCards, grouped.keySet)`.

3. Add above `scan`:

   ```scala
  /** Indexes `game` and reports every card of `expectedCards` it lacks. It
    * reads `game.cardIndex`, so a state is scanned once however often it is
    * indexed.
    */
  def from(
      game: OathGame,
      expectedCards: Set[CardId] = Set.empty
  ): Either[Vector[CardIndexProblem], CardIndex] =
    game.cardIndex match
      case Right(index) =>
        val missing = missingProblems(expectedCards, index.ids)
        if missing.isEmpty then Right(index) else Left(missing)
      // The duplicates alone do not say which expected cards are missing, so
      // this failure path scans again to report both.
      case Left(_) if expectedCards.nonEmpty => scan(game, expectedCards)
      case duplicates => duplicates

  private def missingProblems(
      expectedCards: Set[CardId],
      present: Set[CardId]
  ): Vector[CardIndexProblem] =
    (expectedCards -- present).toVector
      .sortBy(id => (id.kind, id.value))
      .map(CardIndexProblem.MissingCard.apply)
   ```

Run: `./sbtw "root/testOnly oathdigital.model.CardIndexSuite oathdigital.gameplay.OperationPipelineSuite oathdigital.gameplay.OperationExecutorSuite"`
Expected: PASS. (If a suite name is in another package, find it with
`grep -rl "class CardIndexSuite\|class OperationPipelineSuite\|class OperationExecutorSuite" src/test`.)

- [ ] **Step 3: Point the callers at the cache**

Every production caller that indexes a game with no expected cards reads the
field. List them:

Run: `grep -rn "CardIndex.from(" src/main/scala | grep -v "model/CardIndex.scala\|model/DomainValidation.scala"`
Expected: the 14 sites listed under **Files**, each `CardIndex.from(<expr>.game)`.

Rewrite each `CardIndex.from(X.game)` to `X.game.cardIndex`:

```bash
grep -rl "CardIndex.from(" src/main/scala \
  | grep -v "model/CardIndex.scala\|model/DomainValidation.scala" \
  | xargs sed -i '' -E 's/CardIndex\.from\(([A-Za-z_.]+\.game)\)/\1.cardIndex/g'
```

Run the grep again. Expected: no output. `DomainValidation.validate` keeps
`CardIndex.from(game, expectedCards)`, which now reads the cache.

Remove any `CardIndex` import the rewrite left unused (the compile fails
under `-Werror` otherwise). For example `OperationStateInvariant.cardIds`
becomes:

```scala
  def cardIds(ready: ReadyGame): Either[OperationError, Set[CardId]] =
    ready.game.cardIndex.left.map(InvalidCardIndex.apply).map(_.ids)
```

- [ ] **Step 4: Gate**

Run: `./sbtw root/test frontend/test`
Expected: PASS, root 3050 and frontend 640 tests (or the current main
totals), no test file changed: `git diff --stat -- src/test` prints nothing.

Run: `python3 scripts/check-architecture.py`
Expected: no errors.

- [ ] **Step 5: Measure**

Run `scripts/profile-tests.py 'oathdigital.gameplay.powers.campaign.*'`,
`scripts/profile-tests.py --gate` and
`./sbtw "Test/runMain oathdigital.gameplay.SearchBudget"`. Append the "After
Task 2" row and details to the Measurements section. Expected:
`oathdigital.model.CardIndex$.scan` well below the baseline's
`CardIndex$.from` share. If `scan` is still above 10% inclusive, find its
callers in `target/profile/samples.json` and record them in the details.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala docs/superpowers/plans/2026-10-07-test-speed.md
git commit -m "perf(model): index each game state's cards once

OathGame caches its card index in a lazy val, and CardIndex.from reads it.
A pipeline run used to scan the same output state three times.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Window index for a command's powers

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powerresolver/PowerWindowIndex.scala`
- Create: `src/test/scala/oathdigital/gameplay/PowerWindowIndexSuite.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributionCollector.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala` (doc on `contributions`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala` (three `gather` calls)
- Modify (tests that call `gather` directly):
  `src/test/scala/oathdigital/gameplay/ContributionCollectorSuite.scala` (four calls),
  `src/test/scala/oathdigital/gameplay/ContributionIgnoresSuite.scala`,
  `src/test/scala/oathdigital/gameplay/TakeWealthPowerSuite.scala`,
  `src/test/scala/oathdigital/gameplay/TravelSitePowersSuite.scala`
- Modify: this plan's Measurements section.

**Interfaces:**
- Produces:
  - `final case class HookedPower(power: ContributingPower, contributions: Vector[Contribution])`
  - `PowerWindowIndex.of(powers: Vector[ContributingPower]): PowerWindowIndex`
  - `PowerWindowIndex#at(window: PowerWindow): Vector[HookedPower]`
  - `PowerWindowIndex#hasRestrictions: Boolean`
  - `WalkerPowers#windowIndex: PowerWindowIndex` (lazy val)
  - `ContributionCollector.gather(window: PowerWindow, index: PowerWindowIndex, ctxFor: ContributingPower => PowerCtx): GatheredContributions`

- [ ] **Step 1: Write the failing test**

`src/test/scala/oathdigital/gameplay/PowerWindowIndexSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.powerresolver._
import oathdigital.model.{PowerId, PowerWindow, RuleSourceRef}

/** The window index `gather` reads instead of asking every power for its
  * contributions on every call (test speed design, decision 5).
  */
class PowerWindowIndexSuite extends munit.FunSuite:

  /** Stub of ContributingPower: hooks exactly the windows in `hooks`, with
    * the contributions given for each.
    */
  private def powerHooking(name: String,
      hooks: (PowerWindow, Vector[Contribution])*): ContributingPower =
    new ContributingPower:
      def id: PowerId = PowerId(name)
      def source: RuleSourceRef = RuleSourceRef.GameRule(name)
      def contributions: Map[PowerWindow, Vector[Contribution]] = hooks.toMap

  private def hookedAt(index: PowerWindowIndex, window: PowerWindow)
      : Vector[(PowerId, Vector[Contribution])] =
    index.at(window).map(hooked => hooked.power.id -> hooked.contributions)

  test("each window lists the powers that hook it, in input order, with that window's contributions"):
    val recoverTransform = Transform((_, ops) => ops)
    val rollRestriction = Restriction((_, _) => None)
    val recoverOnlyTransform = Transform((_, ops) => ops)
    val hooksBoth = powerHooking("power.both",
      PowerWindow.RecoverEligibility -> Vector(recoverTransform),
      PowerWindow.RecoverBeforeFirstRoll -> Vector(rollRestriction))
    val hooksRecover = powerHooking("power.recover",
      PowerWindow.RecoverEligibility -> Vector(recoverOnlyTransform))

    // Input order puts "power.recover" first, against id order, so the index
    // must keep input order rather than sort.
    val index = PowerWindowIndex.of(Vector(hooksRecover, hooksBoth))

    assertEquals(hookedAt(index, PowerWindow.RecoverEligibility), Vector(
      PowerId("power.recover") -> Vector(recoverOnlyTransform),
      PowerId("power.both") -> Vector(recoverTransform)))
    assertEquals(hookedAt(index, PowerWindow.RecoverBeforeFirstRoll),
      Vector(PowerId("power.both") -> Vector(rollRestriction)))
    assertEquals(hookedAt(index, PowerWindow.TravelCost), Vector.empty)
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "root/testOnly oathdigital.gameplay.PowerWindowIndexSuite"`
Expected: compile error, "Not found: type PowerWindowIndex".

- [ ] **Step 3: Write the index**

`src/main/scala/oathdigital/gameplay/powerresolver/PowerWindowIndex.scala`:

```scala
package oathdigital.gameplay.powerresolver

import oathdigital.model.PowerWindow

/** A power that hooks a window, with its contributions at that window. */
final case class HookedPower(power: ContributingPower,
    contributions: Vector[Contribution])

/** A vector of powers indexed by the windows they hook (test speed design,
  * decision 4). Each window lists the powers whose `contributions` declare
  * it, in the vector's order, with their contributions there.
  *
  * Building it calls `contributions` once per power, and `gather` reads it
  * instead of calling `contributions` again. That relies on the contract on
  * `ContributingPower.contributions`.
  */
final class PowerWindowIndex private (
    byWindow: Map[PowerWindow, Vector[HookedPower]]):

  /** The powers that hook `window`, in input order. */
  def at(window: PowerWindow): Vector[HookedPower] =
    byWindow.getOrElse(window, Vector.empty)

  /** Whether any power declares a tree-level `Restriction` at any window. */
  lazy val hasRestrictions: Boolean = byWindow.valuesIterator.exists(
    _.exists(_.contributions.exists(_.isInstanceOf[Restriction])))

object PowerWindowIndex:
  def of(powers: Vector[ContributingPower]): PowerWindowIndex =
    val hooks = for
      power <- powers
      (window, contributions) <- power.contributions.toVector
    yield window -> HookedPower(power, contributions)
    new PowerWindowIndex(hooks.groupMap(_._1)(_._2))
```

- [ ] **Step 4: Run the test**

Run: `./sbtw "root/testOnly oathdigital.gameplay.PowerWindowIndexSuite"`
Expected: PASS, 1 test.

- [ ] **Step 5: State the contract on `contributions`**

In `ContributingPower.scala`, replace
`  def contributions: Map[PowerWindow, Vector[Contribution]]` with:

```scala
  /** The windows this power hooks and what it contributes at each. It must
    * be pure and give the same result on every call for a given instance:
    * `PowerWindowIndex` calls it once per command and reuses the result.
    */
  def contributions: Map[PowerWindow, Vector[Contribution]]
```

- [ ] **Step 6: Make `gather` read the index**

In `ContributionCollector.scala`:

1. In the object doc, replace `reads only the `PowerCtx` values `ctxFor` produces and the powers
  * it is handed.` with `reads only the `PowerCtx` values `ctxFor` produces and the index it
  * is handed.` (keep the line wrapping under 80 columns).

2. Replace the whole `gather` method with:

```scala
  def gather(
      window: PowerWindow,
      index: PowerWindowIndex,
      ctxFor: ContributingPower => PowerCtx
  ): GatheredContributions =
    // Step 1: discovery -- the powers whose contributions declare this
    // window, with those contributions, from the index.
    val discovered = index.at(window)

    // Step 2: applicability -- keep those applicable in their own context.
    val applicable = discovered.filter(hooked =>
      hooked.power.applicable(ctxFor(hooked.power)))

    // Step 3: named ignore, one pass, no transitivity. Every applicable
    // power's votes are collected against the full applicable set before
    // any power is dropped, so a dropped power's votes still count.
    val ignored: Set[PowerId] = applicable.flatMap { hooked =>
      applicable.filter(other =>
        hooked.power.ignores(ctxFor(hooked.power), other.power))
        .map(_.power.id)
    }.toSet
    val survivors = applicable.filterNot(hooked =>
      ignored.contains(hooked.power.id))

    // Step 4: deterministic order.
    val ordered = survivors.sortBy(hooked =>
      ContributingPower.sortKey(hooked.power))

    // Step 5: split each survivor's contributions at this window into
    // transforms, restrictions and option restrictions, tagged with the owning power id, and
    // record each survivor's id once, in order.
    val transforms = Vector.newBuilder[(PowerId, Transform)]
    val restrictions = Vector.newBuilder[(PowerId, Restriction)]
    val optionRestrictions = Vector.newBuilder[(PowerId, OptionRestriction)]
    val offers = Vector.newBuilder[(PowerId, Offer)]

    ordered.foreach { hooked =>
      val id = hooked.power.id
      hooked.contributions.foreach:
        case transform: Transform => transforms += id -> transform
        case restriction: Restriction => restrictions += id -> restriction
        case option: OptionRestriction => optionRestrictions += id -> option
        case offer: Offer => offers += id -> offer
    }

    GatheredContributions(
      transforms = transforms.result(),
      restrictions = restrictions.result(),
      order = ordered.map(_.power.id),
      optionRestrictions = optionRestrictions.result(),
      offers = offers.result()
    )
```

- [ ] **Step 7: Build the index on `WalkerPowers`**

In `WalkerPowers.scala`, change the import
`import oathdigital.gameplay.powerresolver.{ContributingPower, Restriction}`
to `import oathdigital.gameplay.powerresolver.{ContributingPower, PowerWindowIndex}`,
and replace the `hasRestrictions` lazy val with:

```scala
  /** These powers indexed by the windows they hook, built once per instance
    * (see [[PowerWindowIndex]]). */
  lazy val windowIndex: PowerWindowIndex = PowerWindowIndex.of(powers)

  /** Whether any power can reject an action through a tree-level
    * `Restriction`. The search's tree check has nothing to find without one.
    */
  lazy val hasRestrictions: Boolean = windowIndex.hasRestrictions
```

- [ ] **Step 8: Pass the index at the three walker call sites**

In `WalkerPowerGather.scala`, replace `powers.powers` with
`powers.windowIndex` in each of the three `ContributionCollector.gather(`
calls (in `applyWindow`, and in the `rejected` and `emptied` values). Leave
the other `powers.powers` uses (the `byId` maps) unchanged.

Run: `grep -n "ContributionCollector.gather" src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`
Expected: three calls, each passing `powers.windowIndex` (one call wraps
onto the next line, so check that line too).

- [ ] **Step 9: Update the direct test callers**

Each test that calls `gather` builds an index from its power vector:

- `ContributionCollectorSuite.scala`: in each of the four calls, wrap the
  power vector: `ContributionCollector.gather(window, PowerWindowIndex.of(Vector(declaresElsewhere)), ctxFor(_, window))`,
  and the same for `Vector(inapplicable)`, `Vector(bravoA, alphaZ)` and
  `Vector(power)`. The file already imports `powerresolver._`.
- `ContributionIgnoresSuite.scala`: `ContributionCollector.gather(window, PowerWindowIndex.of(powers), ctxAt(operation)).order`,
  and add `PowerWindowIndex` to the `powerresolver.{...}` import.
- `TakeWealthPowerSuite.scala`: `ContributionCollector.gather(window, PowerWindowIndex.of(powers), ctx(state, window, node, _))`.
- `TravelSitePowersSuite.scala`: `ContributionCollector.gather(window, PowerWindowIndex.of(candidates), ctx(ready, window, node, _))`.

No assertion changes.

- [ ] **Step 10: Gate**

Run: `./sbtw root/test frontend/test`
Expected: PASS, one more root test than after Task 2.
`git diff --stat main -- src/test` lists only the four suites above and the
new `PowerWindowIndexSuite.scala`.

Run: `python3 scripts/check-architecture.py`
Expected: no errors.

- [ ] **Step 11: Measure**

Run `scripts/profile-tests.py 'oathdigital.gameplay.powers.campaign.*'`,
`scripts/profile-tests.py --gate` and
`./sbtw "Test/runMain oathdigital.gameplay.SearchBudget"`. Append the "After
Task 3" row and details to the Measurements section. Expected:
`java.lang.ClassValue.getFromBackup` gone from the top self frames, and
`ContributionCollector$.gather` below its baseline share.

- [ ] **Step 12: Commit**

```bash
git add src/main/scala src/test/scala docs/superpowers/plans/2026-10-07-test-speed.md
git commit -m "perf(walker): index a command's powers by window once

WalkerPowers builds a PowerWindowIndex in a lazy val, and gather reads each
window's powers and contributions from it instead of rebuilding every
power's contribution map on every call.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Measurements

Gate and summed suite time from `scripts/profile-tests.py --gate`. Frame
shares from `scripts/profile-tests.py 'oathdigital.gameplay.powers.campaign.*'`
(inclusive, except `ClassValue`, which is self). SearchBudget is Campaign
start / reading its park.

| Stage | Gate wall | Gate CPU | Summed root suites | Campaign profile wall | `CardIndex` | `gather` | `ClassValue` on stack | SearchBudget |
|---|---|---|---|---|---|---|---|---|
| Spec baseline (37c8563d, by hand) | 46 s | 280 s | 216 s | – | 26% | 33% | 4% | 26 ms / 0 ms |
| Baseline (load 11.8, busy machine) | 58 s | 340 s | 308 s | 61 s | 30% | 34% | 2% | 33 ms / 0 ms |
| After Task 2 (load 7.6 gate, 12.3 profile) | 45 s | 288 s | 294 s | 15 s (warm) | 16.8% | 39.2% | 1.8% | 24 ms / 0 ms |
| After Task 3 | | | | | | | | |

### Baseline

The machine was busy (load average 11.8), so the gate times are inflated
against the spec baseline (46 s, 263 s, 216 s on 3050 tests; this run has
3088). The frame shares and SearchBudget are the comparable numbers.

Ten slowest suites (gate, under load): MercenariesSuite 12.9 s, ShieldWallSuite 10.1 s,
WrestlersSuite 9.8 s, DisgracedCaptainSuite 9.6 s, StickyFireSuite 7.9 s,
FieldPromotionSuite 7.6 s, GreatCrusadeSuite 7.4 s, TrustedSeatRoutesSuite 7.2 s,
CrackingGroundSuite 7.0 s, MilitaryParadeSuite 6.3 s.

Campaign profile (1913 samples that ran project code), share of samples with the
frame on the stack: `CardIndex$` 30.3%, `OperationStateInvariant` 30.9%,
`DomainValidation$.validate` 13.4%, `ContributionCollector$.gather` 34.2%,
`WalkerPowerGather$.breaches` 47.2%, `ClassValue` 2.1%. Top self frames:
`BoxesRunTime.equals2` 6.0%, `HashMapBuilder.update` 5.6%,
`Vector.filterImpl` 3.5%.

### After Task 2

Load differed from the baseline run, so compare the shares, not the times. The
campaign profile's summed suite time went from 130.9 s to 119.5 s. Its first run
(the baseline) included a cold compile in the new worktree, so its wall clock is
not comparable.

Share of samples with the frame on the stack: `CardIndex$` 30.3% to 16.8%,
`OperationStateInvariant` 30.9% to 19.6%, `DomainValidation$.validate` 13.4% to
4.5%. What remains of `CardIndex$` is one scan per new state (13.7% under
`OathGame.cardIndex`, which an operation cannot avoid because every run makes a new
state) and 3.0% in `DomainValidation.validate`'s set difference against the expected
cards.

### After Task 3

Ten slowest suites:

Top ten inclusive frames:
