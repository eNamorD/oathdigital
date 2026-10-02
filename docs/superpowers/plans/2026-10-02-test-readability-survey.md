# Test Readability Survey Implementation Plan (Plan 1 of 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Survey every root, shared and frontend test file against the readability rubric and commit an approved ledger of findings, each with a planned outcome.

**Architecture:** A script partitions the test files into twelve areas plus one list of shared helper files. Read-only Sonnet agents survey one area each and write ledger rows in a fixed format to a gitignored scratch directory. One area runs first to calibrate the prompt. The shared-fixtures verdicts are taken before the other areas run, so area agents cite them. The main agent merges, spot-checks and commits the ledger, then stops for the user's review. Plan 2 (the fixes) is written from the approved ledger.

**Tech Stack:** Python 3 and POSIX sh for the partition and metrics scripts, `git grep`, the Agent tool (general-purpose, model `sonnet`), Markdown.

**Spec:** `docs/superpowers/specs/2026-10-02-test-readability-design.md`

## Global Constraints

- No test or production code changes in this plan. The only committed file is the ledger, `docs/superpowers/specs/2026-10-02-test-readability-ledger.md`.
- Subagents run with `model: "sonnet"` and must not exceed the main agent's model or effort.
- Survey agents are read-only on the repository. Each writes only its own rows file under `.superpowers/readability/rows/` (gitignored).
- Persisted text (ledger, commit messages) is normal prose. Commits end with the current Co-Authored-By trailer.
- Run in a worktree off local `main` (executing-plans step 1). Symlink the main checkout's `.tooling` into the worktree. `.superpowers/` is gitignored: create `.superpowers/readability/` inside the worktree.
- Never stage `docs/ROADMAP.md`. Re-check `HEAD` before any amend, reset or rebase; other sessions move `main`.
- Rubric rules R1–R11, the shared-fixture rule and the exclusions are as written in the spec's **Rubric** section. The agent prompt below copies them verbatim.

## Areas

The partition script assigns files by the first rule that matches. Counts at 2026-10-02 (466 files):

| Area | Files | Rule |
|---|---|---|
| 00-shared-fixtures | 40 | Every root test file not named `*Suite.scala` (fixtures, drivers, harnesses, `testkit` builders), plus frontend non-suite test files |
| 01-service | 34 | `application/` (not `gamelog/`), `engine/` |
| 02-game-log-and-testkit | 17 | `application/gamelog/`, `testkit/` suites |
| 03-engine | 56 | `gameplay/walker/`, `gameplay/operations/`, top-level `gameplay/` suites whose names start with an engine prefix (see script) |
| 04-powers-action | 59 | `gameplay/powers/action/` |
| 05-powers-play-and-economy | 38 | `gameplay/powers/{whenplayed,cardplay,economy}/` |
| 06-powers-phases-and-travel | 34 | every other `gameplay/powers/` suite except campaign and targeting |
| 07-campaign-targeting | 51 | `gameplay/powers/{campaign,targeting}/`, `gameplay/Campaign*`, `gameplay/Catacombs*` |
| 08-model-catalog-serialization | 25 | `model/`, `catalog/`, `serialization/`, and `shared/src/test` |
| 09-setup-server-persistence | 29 | `server/`, `persistence/`, `gameplay/setup/` |
| 10-procedures | 36 | every other `gameplay/` suite |
| 11-frontend-a | 24 | the first half of the frontend suites, sorted by path |
| 12-frontend-b | 23 | the second half |

This is twelve areas, not the spec's ten. The power suites (131 files) were too many for two agents, so they are split by subdirectory into three areas. `shared/src/test` (6 files) rides with area 08; the spec scoped root and frontend, and these are the reference codec suites beside the serialization area.

## Ledger format

`docs/superpowers/specs/2026-10-02-test-readability-ledger.md`:

```markdown
# Test readability ledger

Date: 2026-10-02. Spec: [test readability sweep](2026-10-02-test-readability-design.md).
Status: surveyed, awaiting review.

## Baseline

<output of metrics.sh, one bullet per line>

## Summary

| Area | R1 | R2 | R3 | R4 | R5 | R6 | R7 | R8 | R9 | R10 | R11 | Keep | Total |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| ... |

## Calibration

<what the calibration run misjudged, and how the prompt changed>

## Spot check

<rows checked, rows corrected, areas re-checked>

## Shared fixtures

| ID | File | Helper | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 01-service

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

(one section per area, 01 to 12)
```

- **ID** is `<area number>-<three-digit sequence>`, for example `04-017`. Shared fixtures use `00-…`.
- **File** is the path relative to the test source root (`src/test/scala/oathdigital/`, `frontend/src/test/scala/oathdigital/frontend/` or `shared/src/test/scala/`), with a line number: `gameplay/RestSuite.scala:88`.
- **Test** is the test's title, abbreviated after 60 characters with `…`, or `suite` for a finding about the whole file or many of its tests.
- **Rule** is `R1`…`R11`, or `Keep` for a candidate the agent judged fine and wants on record (a phase-subject patch, a helper that passes).
- **Problem** is one line.
- **Planned outcome** is concrete: the step to state, the accessor to use, the new name, the split. "Improve readability" is not an outcome.
- **Outcome** and **Commit** stay empty in Plan 1. Plan 2 fills them.

## Agent prompt

Every survey agent gets this prompt, with `<AREA>`, `<FILE LIST>` and `<FIXTURE VERDICTS>` filled in. Task 2 may tighten the **Examples** section after calibration; later tasks use the tightened version.

````text
You are surveying Scala test files for readability. You are read-only on the
repository: do not edit, create or delete any file except your rows file,
.superpowers/readability/rows/<AREA>.md. Do not run sbt.

Repository: <worktree path>. Area: <AREA>.

Files to survey (read every one completely, and read each helper a test calls
far enough to know what it builds):
<FILE LIST>

The standard: a new reader can open any test and say what it checks from its
body alone. Every fact the outcome depends on is stated there, in the game's
own words. DRY applies to mechanics (a builder, a fake, an assertion helper),
never to the story of the test. Read the testkit first:
src/test/scala/oathdigital/testkit/Table.scala (the builder; its doc comment
lists the quiet start: p1's Act, pawns at Ancient City, Broken Peaks and
Buried Giant, nothing at the sites, 1 favor, 1 faceup secret, 3 warbands,
7 Supply each), Look.scala (reads) and CatalogNames.scala (names to ids).

Rubric. Each finding falls under one rule:

| # | Rule | Fix |
|---|---|---|
| R1 | Hidden fact. The outcome depends on a board fact the test does not state: a helper default, a pawn position, a card at a site. | State it as a Table step in the test body. |
| R2 | Opaque helper. A new reader cannot say what a helper builds or does without opening it. | Inline it as steps, or rename it to the domain phrase. A helper that passes the test stays. |
| R3 | State patch. updateCurrent, copy(current = ...) or a turn.copy that a Table step could state. | Use the step. A fact that three or more suites need becomes a new step. Keep the patch, with a comment, when the patched field is the subject or no step reaches the state. |
| R4 | Pattern extract. val Ready(x) = ...: @unchecked and similar. | A testkit accessor that fails with the actual value (for example accepted(result), parked(result)). |
| R5 | Numeric id. DenizenId("93"). | CatalogNames. Exempt: wire and catalog suites where the id is the subject. |
| R6 | Tuple fixture. val (a, _, _, b) = fixture. | Named fields or separate builders. |
| R7 | Title mismatch. The title does not say what the body asserts, or the body asserts unrelated things. | Rename, or split. A scenario test that tells one story stays whole. |
| R8 | Unexplained expectation. An expected value whose origin the reader cannot see (assertEquals(supply, 5)). | Derive it from stated facts, or add a one-line why. |
| R9 | Borrowed fixture. A fixture or helper imported from another suite's file. | Move it to the testkit, or inline it. |
| R10 | Test double. A hand class that implements a production trait. | Classify it as fake, spy, stub or mock, and say which production trait. |
| R11 | Oversized file. Over 800 lines. | Propose a split by subject, naming the new suites and which tests go where. |

Not in the rubric, do not report: tests that look useless (pruning is done),
slowness, golden-log contents, production code.

Shared fixture verdicts (do not re-judge these helpers; cite the ID when a
test's finding comes from one):
<FIXTURE VERDICTS>

Examples:
- R1: a Rest test asserts Supply 5 and relies on the quiet table's 3
  warbands without saying so. Planned outcome: ".warbands(p1, 3) in the
  body, and a comment deriving 5 from the band table".
- R3 keep: RestSuite sets turn.phase = Phase.Rest because the phase is the
  subject. Rule Keep, problem "phase is the subject".
- R4: val Accepted(events) = service.handle(...): @unchecked. Planned
  outcome: "accepted(service.handle(...))".
- R2 pass: withAdviser(b, card, FaceUp) reads as a domain phrase. Do not
  report it.
- Keep, not R3: CardIndexSuite builds a card in two containers with a
  game.copy chain because the test makes an illegal state on purpose. Rule
  Keep, problem "the illegal state is the subject". Never write R3 on a row
  whose planned outcome is Keep.
- R4 (repeated mechanics, DRY applies), not R2: a suite repeats
  events.zipWithIndex.map { ... RecordedEvent(...) } and
  encodeStream(...).toOption.get in 17 places. One R4 row with Test "suite"
  and the planned outcome "roundTripStream(label, events) helper that fails
  with the WireError". R2 is only for a helper whose name or body a reader
  cannot follow.
- R6: val (state, events) = execute() reads a tuple by position. Planned
  outcome: "execute() returns SetupResult(state, events)".
- R7 pass: the title "faces accumulate shields then apply every doubler"
  over assertEquals(score(OneShield, TwoShields, Doubler, Doubler), 12). The
  title and the named faces give the 12 ((1 + 2) shields, doubled twice), so
  it is neither R7 nor R8. Report R7 only when a body assertion falls outside
  its title: "usePower names exactly one power and one source" over a test
  that decodes a usePower carrying an extra field.
- R8 pass: ExileSupply.refresh(9, 1) under the titles "refreshes to six" and
  "caps at seven". The titles give the numbers; the argument order is not a
  rubric finding.
- R1 pass: assertEquals(DomainValidation.validate(game.copy(...)), Vector.empty)
  needs the rest of the sample game to be valid. That is the premise of every
  validate test, not a board fact. Report R1 for a card, a pawn or a count
  the outcome reads (a Rest test relying on 3 warbands), never for "the rest
  is valid".

How to report:
- One row per finding. When the same pattern repeats in many tests of one
  file, write one row with Test "suite" and the count in Problem
  ("12 tests extract Ready with @unchecked").
- Write only real findings. A file with none gets no rows.
- Every Planned outcome is concrete.
- Every count in Problem (tests, call sites, lines) comes from grep -c or a
  line count, not an estimate.
- Write rows to .superpowers/readability/rows/<AREA>.md as a Markdown table
  with exactly these columns:
  | ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
  IDs are <area number>-001, -002, ... in file order. Leave Outcome and
  Commit empty. File paths are relative to the test source root, with a
  line number.
- Your final message: the number of files read, rows per rule, and any file
  you could not judge, with why. Nothing else.
````

For **00-shared-fixtures**, replace the second paragraph's file list with that area's list, the column **Test** with **Helper** (the helper's name), and add: "Judge every public helper in these files under R1, R2, R6, R9 and R10. For each helper that stays, add a Keep row naming it, so area agents can cite it. Count each helper's callers with `git grep -l -w <name> -- src/test frontend/src/test` and put the count in Problem."

---

### Task 1: Partition, metrics and ledger skeleton

**Files:**
- Create (gitignored): `.superpowers/readability/partition.py`, `.superpowers/readability/metrics.sh`, `.superpowers/readability/areas/*.txt`
- Create: `docs/superpowers/specs/2026-10-02-test-readability-ledger.md`

**Interfaces:**
- Produces: `.superpowers/readability/areas/<area>.txt` (one path per line, repository-relative) for the 13 lists above; `metrics.sh` output used by the ledger's Baseline and later by Plan 2's Result.

- [ ] **Step 1: Write the partition script**

Write `.superpowers/readability/partition.py`:

```python
"""Partition every test file into the readability survey's areas.

Run from the repository root. Writes .superpowers/readability/areas/<area>.txt
and prints a count per area. Every test file lands in exactly one list."""
import os, re, subprocess

def files(spec):
    out = subprocess.run(["git", "ls-files", spec], capture_output=True,
                         text=True, check=True).stdout.split()
    return sorted(f for f in out if f.endswith(".scala"))

root = files("src/test/scala")
front = files("frontend/src/test")
shared = files("shared/src/test")

G = "src/test/scala/oathdigital/"
ENGINE = re.compile(r"^(Operation|Walker|Contribution|Restriction|Rule|Power|"
                    r"Pending|Lazy|Repeat|Enclosing|Offer|CoOwned|"
                    r"GlobalRestrictions|OptionRestriction|DecisionQueries|"
                    r"PayCost|LimitedResource|SelectionPayments|CoreOperations|"
                    r"GameplayTransition|OathRules|ProcedureWalker|"
                    r"AutomaticRoll|PassWhenEmpty|BackendArchitecture)")

def is_helper(path):
    """A file that defines no suite: a fixture, driver, harness or testkit
    builder. These go to the shared-fixtures agent."""
    if path.endswith("Suite.scala"):
        return False
    return True

def area(path):
    name = os.path.basename(path)
    if path in shared:
        return "08-model-catalog-serialization"
    if path.startswith("frontend/"):
        return None  # split below
    rel = path[len(G):]
    if is_helper(path):
        return "00-shared-fixtures"
    if rel.startswith("application/gamelog/") or rel.startswith("testkit/"):
        return "02-game-log-and-testkit"
    if rel.startswith(("application/", "engine/")):
        return "01-service"
    if rel.startswith(("model/", "catalog/", "serialization/")):
        return "08-model-catalog-serialization"
    if rel.startswith(("server/", "persistence/", "gameplay/setup/")):
        return "09-setup-server-persistence"
    if (rel.startswith(("gameplay/powers/campaign/", "gameplay/powers/targeting/"))
            or re.match(r"gameplay/(Campaign|Catacombs)", rel)):
        return "07-campaign-targeting"
    if rel.startswith("gameplay/powers/action/"):
        return "04-powers-action"
    if rel.startswith(("gameplay/powers/whenplayed/", "gameplay/powers/cardplay/",
                       "gameplay/powers/economy/")):
        return "05-powers-play-and-economy"
    if rel.startswith("gameplay/powers/"):
        return "06-powers-phases-and-travel"
    if rel.startswith(("gameplay/walker/", "gameplay/operations/")):
        return "03-engine"
    if rel.startswith("gameplay/") and "/" not in rel[len("gameplay/"):]:
        return "03-engine" if ENGINE.match(name) else "10-procedures"
    return "10-procedures"

lists = {}
for f in root + shared:
    lists.setdefault(area(f), []).append(f)
suites = [f for f in front if f.endswith("Suite.scala")]
helpers = [f for f in front if not f.endswith("Suite.scala")]
half = (len(suites) + 1) // 2
lists["11-frontend-a"] = suites[:half]
lists["12-frontend-b"] = suites[half:]
lists.setdefault("00-shared-fixtures", []).extend(helpers)

os.makedirs(".superpowers/readability/areas", exist_ok=True)
total = 0
for name in sorted(lists):
    with open(f".superpowers/readability/areas/{name}.txt", "w") as out:
        out.write("\n".join(lists[name]) + "\n")
    total += len(lists[name])
    print(f"{len(lists[name]):4d} {name}")
expected = len(root) + len(front) + len(shared)
assert total == expected, (total, expected)
print(f"{total:4d} total (every test file exactly once)")
```

- [ ] **Step 2: Run it**

Run: `python3 .superpowers/readability/partition.py`
Expected: thirteen lines and a total line ending "(every test file exactly once)". The counts may differ from the Areas table if tests were added since 2026-10-02; the assertion must hold. If an area holds more than 65 files, split it by subdirectory the way the power areas are split, and note it in the ledger's Calibration section.

- [ ] **Step 3: Write the metrics script**

Write `.superpowers/readability/metrics.sh`:

```sh
#!/bin/sh
# Readability metrics for the test sweep. Run from the repository root.
# Prints one "name: count" line per metric, for the ledger's baseline and Result.
T='src/test/scala'
F='frontend/src/test'
sum() { awk -F: '{s+=$NF} END{print s+0}'; }
echo "suites (root): $(git grep -l 'extends munit' -- $T | wc -l | tr -d ' ')"
echo "suites (frontend): $(git grep -l 'extends munit' -- $F | wc -l | tr -d ' ')"
echo "replayed setup files (execute()/initialReady): $(git grep -l -E 'execute\(\)|initialReady' -- $T | wc -l | tr -d ' ')"
echo "Table.start files: $(git grep -l 'Table.start' -- $T | wc -l | tr -d ' ')"
echo "state patches (updateCurrent/copy(current =): $(git grep -c -E 'updateCurrent|copy\(current *=' -- $T | sum)"
echo "@unchecked: $(git grep -c '@unchecked' -- $T $F | sum)"
echo "numeric card ids: $(git grep -c -E '(Denizen|Relic|Edifice|Vision|Card|Site)Id\("[0-9]+"\)' -- $T $F | sum)"
echo "tuple destructures with _: $(git grep -c -E 'val \((_|[a-z][A-Za-z0-9]*), .*_\) =' -- $T $F | sum)"
echo "Table.update escapes: $(git grep -c -E '\.update\(' -- $T/oathdigital | sum)"
echo "test files over 800 lines: $(git ls-files "$T/*.scala" "$F/*.scala" | xargs wc -l | awk '$2 != "total" && $1 > 800' | wc -l | tr -d ' ')"
echo "largest test file: $(git ls-files "$T/*.scala" "$F/*.scala" | xargs wc -l | awk '$2 != "total"' | sort -rn | head -1 | awk '{print $1, $2}')"
```

- [ ] **Step 4: Run it**

Run: `sh .superpowers/readability/metrics.sh`
Expected (2026-10-02): suites 372 root and 43 frontend, 313 state patches, 158 `@unchecked`, 84 numeric ids, 69 tuples, 14 escapes, 4 files over 800 lines. Later counts may differ.

- [ ] **Step 5: Write the ledger skeleton**

Create the ledger from the **Ledger format** above: the Baseline section filled with the metrics output, and empty Summary, Calibration, Spot check and area sections.

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/specs/2026-10-02-test-readability-ledger.md
git commit -m "docs: start the test readability ledger with its baseline"
```

### Task 2: Calibrate, and judge the shared fixtures

**Files:**
- Create (gitignored): `.superpowers/readability/rows/08-model-catalog-serialization.md`, `.superpowers/readability/rows/00-shared-fixtures.md`
- Modify: `docs/superpowers/plans/2026-10-02-test-readability-survey.md` (the Agent prompt's Examples, if calibration tightens them)
- Modify: `docs/superpowers/specs/2026-10-02-test-readability-ledger.md` (Calibration section)

**Interfaces:**
- Consumes: `areas/08-model-catalog-serialization.txt`, `areas/00-shared-fixtures.txt`.
- Produces: the shared-fixture verdict rows (`00-…` IDs) that Task 3 pastes into `<FIXTURE VERDICTS>`; the final Agent prompt.

- [ ] **Step 1: Dispatch both agents in one message**

Two Agent calls in parallel, `subagent_type: "general-purpose"`, `model: "sonnet"`:
- Area `08-model-catalog-serialization` with the Agent prompt, its file list, and `<FIXTURE VERDICTS>` set to "None yet; judge helpers you meet under R2."
- Area `00-shared-fixtures` with the shared-fixtures variant of the prompt.

- [ ] **Step 2: Check the calibration rows**

For every row in `rows/08-model-catalog-serialization.md`, open the cited line and decide whether the rule and planned outcome are right. Also open three files in the area that got no rows and check that they deserve none. Record in the ledger's Calibration section how many rows were right, wrong (and why) or missing.

- [ ] **Step 3: Tighten the prompt**

For each kind of misjudgment, add one example to the Agent prompt's **Examples** in this plan. A misjudgment is a wrong rule, a vague outcome, a missed finding, or a report of an excluded thing. If more than a quarter of the rows were wrong, rerun area 08 with the new prompt and check again.

- [ ] **Step 4: Check the shared-fixture verdicts**

Spot-check five helper rows in `rows/00-shared-fixtures.md` against the code: the caller counts with `git grep -l -w`, and whether the R1 or R2 verdict holds. Correct any wrong row in the rows file.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-10-02-test-readability-ledger.md docs/superpowers/plans/2026-10-02-test-readability-survey.md
git commit -m "docs: calibrate the readability survey and judge the shared fixtures"
```

### Task 3: Survey the remaining areas

**Files:**
- Create (gitignored): `.superpowers/readability/rows/<area>.md` for areas 01–07 and 09–12

**Interfaces:**
- Consumes: the tightened Agent prompt, the area lists, and the shared-fixture rows.
- Produces: eleven rows files.

- [ ] **Step 1: Build the fixture verdicts**

Paste the ID, Helper, Rule and Planned outcome columns of `rows/00-shared-fixtures.md` into `<FIXTURE VERDICTS>`.

- [ ] **Step 2: Dispatch eleven agents in one message**

One Agent call per area (01–07, 09–12), all in the same message so they run in parallel, `subagent_type: "general-purpose"`, `model: "sonnet"`, `run_in_background: true`, each with the tightened prompt, its file list and the fixture verdicts.

- [ ] **Step 3: Collect**

Wait for all eleven completion notifications. For each, compare the agent's file count with its area list's line count. Re-dispatch any agent that read fewer files or names a file it could not judge, giving it only the missing files.

### Task 4: Merge, spot-check and commit the ledger

**Files:**
- Modify: `docs/superpowers/specs/2026-10-02-test-readability-ledger.md`

- [ ] **Step 1: Merge the rows**

Append each rows file under its area heading in the ledger, in area order. Remove duplicate rows: the same file, line and rule. A test finding that only restates a shared-fixture verdict keeps its row, citing the `00-…` ID in Planned outcome, so Plan 2 can find every caller.

- [ ] **Step 2: Spot-check about 10%**

Pick about 10% of the rows, at least three per area, mixing rules. For each, open the cited line and confirm the rule and the outcome. Correct wrong rows in place. If more than one in five rows of an area is wrong in the same way, re-dispatch that area with an example covering the error, and replace its rows. Record the counts in the Spot check section.

- [ ] **Step 3: Fill the Summary**

Count rows per area and rule into the Summary table, with a Total row.

- [ ] **Step 4: Check the ledger's rows**

Write `.superpowers/readability/check_ledger.py`:

```python
"""Check the ledger's finding rows: eight cells each, unique IDs."""
import re

text = open("docs/superpowers/specs/2026-10-02-test-readability-ledger.md").read()
rows = [l for l in text.splitlines() if re.match(r"\| \d{2}-\d{3} \|", l)]
pipes = lambda line: len(re.findall(r"(?<!\\)\|", line))
bad = [l for l in rows if pipes(l) != 9]
ids = [l.split("|")[1].strip() for l in rows]
print(f"{len(rows)} rows, {len(bad)} malformed, {len(ids) - len(set(ids))} duplicate IDs")
for line in bad[:5]:
    print(line)
```

Run: `python3 .superpowers/readability/check_ledger.py`
Expected: `<n> rows, 0 malformed, 0 duplicate IDs`. A pipe inside a cell must be escaped as `\|`.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-10-02-test-readability-ledger.md
git commit -m "docs: record the test readability survey"
```

### Task 5: The user's ledger review

- [ ] **Step 1: Merge to local main**

Finish the branch with superpowers:finishing-a-development-branch. The branch holds only the ledger and the plan, so a local merge is usual.

- [ ] **Step 2: Present the ledger**

Give the user the Summary table, the Calibration and Spot check notes, and the shared-fixture verdicts that would dissolve a fixture. Ask them to strike or change rows.

- [ ] **Step 3: Apply the review**

Edit struck rows to Outcome `struck by the user`, apply changed outcomes, commit, and merge as in Step 1. Plan 1 ends here. Plan 2 is written from this ledger with superpowers:writing-plans.
