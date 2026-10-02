# Test readability sweep

Date: 2026-10-02. Status: designed.

This is the fourth of five test-suite improvement projects from the
2026-09-27 test audit. The first three are merged:
[Table builder](2026-09-27-test-table-builder-design.md),
[prune low-value tests](2026-09-27-prune-low-value-tests-design.md) and
[service and game-log start states](2026-09-28-service-log-start-states-design.md).
Speed is queued after this one.

The standards come from the `scala-testing` skill as updated on 2026-09-29 and
2026-10-01: DAMP over DRY, no inherited facts, no borrowed fixtures, names not
ids, a built start state, and fakes over fixtures over mocks, with a contract
suite for every fake.

## Problem

The start-state work held: `execute()` and `initialReady` remain in 7 files,
and 157 files build their start with `Table.start`. What is left is how the
tests read. A baseline taken on 2026-10-02 over the root project (372 suites,
411 files) found:

- **State patches.** 313 `updateCurrent` or `copy(current = …)` patches in 125
  files. The heaviest are `StateBasedEvaluationSuite` (24),
  `OperationExecutorSuite` (16), `RestSuite` (11) and `CardPlayProcedureSuite`
  (9). Some patch the subject of the test; many patch a fact a `Table` step
  could state.
- **Pattern extracts.** 150 `@unchecked` in 38 files, mostly
  `val Ready(x) = …: @unchecked`. `GameApplicationServiceSuite` holds 39.
- **Numeric ids.** 84 card ids such as `DenizenId("93")` in 57 files.
- **Tuple fixtures.** 64 destructures that discard fields with `_`.
- **Escape hatch.** 14 uses of `Table.update`.
- **Shared fixtures.** `PowerFixture` (82 users), `TargetsFixture` (66),
  `CampaignFixture` (57), `SearchFixture` (43), `PlanDriver` (40) and smaller
  ones were rebased onto `Table` in the first project and kept as domain
  phrases. Nobody has judged their helpers one by one.
  `FirstGameSetupFixture` has 194 users, nearly all for its `catalog`.
- **Large files.** `GameApplicationServiceSuite` (1829 lines),
  `ProcedureWalkerSuite` (1022) and `GameEventWireSuite` (931) are over the
  800-line cap that production code keeps. Tests have no cap.
- **Test doubles.** About 15 hand classes implement production traits
  (`IdentityRepository`, `PollClock`, `CampaignDicePort`, `EventEvolution`,
  `LogStore`, `GameClient` and others). `InMemoryEventStreamRepository` is the
  fake every service test uses, and no suite runs it against the Hsqldb
  repository.

These counts catch the mechanical smells. The DAMP question, whether a reader
can say what a test checks without opening a helper, needs a reading of every
suite. The frontend's 43 suites were not part of the audit and are in scope.

## Goal

A new reader can open any test, root or frontend, and say what it checks
from its body alone. Every fact the outcome depends on is stated there, in the
game's own words. Every fake is held to the real object by a contract suite.
Guards keep the mechanical rules from coming back.

## Rubric

Each finding falls under one rule. A finding may also end as "keep", with a
stated reason.

| # | Rule | Fix |
|---|---|---|
| R1 | **Hidden fact.** The outcome depends on a board fact the test does not state: a helper default, a pawn position, a card at a site. | State it as a `Table` step in the test body. |
| R2 | **Opaque helper.** A new reader cannot say what a helper builds or does without opening it. Shared fixture helpers are judged the same way. | Inline it as steps, or rename it to the domain phrase. A helper that passes the test stays. |
| R3 | **State patch.** `updateCurrent`, `copy(current = …)` or a `turn.copy` that a `Table` step could state. | Use the step. A fact that three or more suites need becomes a new step. Keep the patch, with a comment, when the patched field is the subject or no step reaches the state. |
| R4 | **Pattern extract.** `val Ready(x) = …: @unchecked` and similar. | A testkit accessor that fails with the actual value (for example `accepted(result)`, `parked(result)`). This is mechanics, so DRY applies. |
| R5 | **Numeric id.** `DenizenId("93")`. | `CatalogNames`. Exempt: wire and catalog suites where the id is the subject. A guard enforces it. |
| R6 | **Tuple fixture.** `val (a, _, _, b) = fixture`. | Named fields or separate builders. |
| R7 | **Title mismatch.** The title does not say what the body asserts, or the body asserts unrelated things. | Rename, or split. A scenario test that tells one story stays whole. |
| R8 | **Unexplained expectation.** An expected value whose origin the reader cannot see (`assertEquals(supply, 5)`). | Derive it from stated facts, or add a one-line why. |
| R9 | **Borrowed fixture.** A fixture or helper imported from another suite's file. | Move it to the testkit, or inline it. |
| R10 | **Test double.** A hand class that implements a production trait. | Classify it as fake, spy, stub or mock. The test-double task acts on it. |
| R11 | **Oversized file.** Over 800 lines. | Split by subject. |

**Shared fixtures.** Each helper in the shared fixtures is judged on its own
under R1 and R2. A helper that reads as a domain phrase and hides no board
fact (`withAdviser(b, card, FaceUp)`, `againstPlayer`) stays. A helper that
hides facts is inlined as `Table` steps at its call sites, and a fixture that
empties is deleted.

**Not in the rubric:** pruning, speed, and golden-log contents.

## Ledger

`docs/superpowers/specs/2026-10-02-test-readability-ledger.md` holds one row
per finding: file, test (or "suite" for a suite-wide finding), rule, the
problem in one line, the planned outcome, and later the recorded outcome and
commit. A summary table at the top counts findings per rule and per area.

## Survey

**Partition.** Ten areas of about 40 suites each, cut along package lines:

1. service and application;
2. game log;
3. walker and procedures;
4. powers that use the shared fixtures;
5. the other power suites;
6. campaign and targeting;
7. model, catalog and serialization;
8. setup, server and persistence;
9. frontend, first half;
10. frontend, second half.

The plan fixes the exact file lists.

**Shared fixtures.** One more agent judges every helper in the shared
fixtures (`PowerFixture`, `TargetsFixture`, `CampaignFixture`,
`SearchFixture`, `PlanDriver`, `MovementFixture`, `BannerFixture`,
`WhenPlayedHarness`, `TestGameFixtures` and the smaller ones). Area agents
cite its verdicts and do not re-judge those helpers.

**Agents.** One read-only general-purpose agent per area, run in parallel
through the Agent tool, on Sonnet (not above the main agent's model). Each
reads its suites and the helpers they call, and returns ledger rows in a
fixed format.

**Calibration.** One agent surveys one small area first. Its rows are
checked against the rubric, and the rubric's examples are tightened where it
misjudged. Only then do the other areas run.

**Merge.** The rows are deduplicated and about 10% are spot-checked against
the code. A wrong row is corrected; if the error looks systematic, its area
is re-checked. The ledger is committed with its summary.

**Gate.** The user reviews the ledger before any fix task starts, and may
strike or change rows.

## Fix phase

The project has two plans. Plan 1 covers the survey and ends at the ledger
review. Plan 2 is written from the approved ledger, so its tasks match real
findings.

Plan 2's tasks, in order:

1. **Foundation.** The testkit accessors that replace pattern extracts (R4),
   and any new `Table` steps the ledger shows three or more suites need
   (R3). Each has a test in `TableSuite` or a testkit suite. No migration.
2. **Area tasks.** One per area, ordered by finding count. Each fixes every
   ledger row in its area, including splitting its own oversized files
   (`GameApplicationServiceSuite` in the service task). A shared-fixture
   helper that is inlined moves in the area task holding most of its
   callers, together with all its other callers, so no fixture is left half
   dissolved.
3. **Test doubles.** A contract suite for each fake, starting with an
   abstract `EventStreamRepositoryContract` run against
   `InMemoryEventStreamRepository` and the Hsqldb repository (this closes the
   ROADMAP item). Mocks that hide an interaction are replaced by the fake. A
   spy over a fake stays.
4. **Guards and Result.** The 800-line cap extends to `src/test` and
   `frontend/src/test`. A guard forbids numeric card ids in tests outside the
   exempt wire and catalog suites. Every ledger row gets its outcome, and the
   Result is written here.

**Every fix task:**

- runs in a short-lived worktree off local `main` and merges back to local
  `main` when green, since other sessions keep moving `main`;
- rewrites only the arrange and act lines; an assertion may only get
  stronger, never weaker or dropped;
- breaks three to five of the production rules its rewritten tests guard,
  one mutation at a time, confirms the right test fails, restores the rule
  from a backup, and records the result in the ledger;
- passes a review subagent that checks the frozen-assertion rule on the diff
  and judges each touched suite against the rubric;
- ends with `./sbtw root/test` and `./sbtw frontend/test` green, and the test
  count unchanged except for recorded splits or merges.

## Success criteria

- Every ledger row has an outcome: fixed, kept with a reason, or struck by
  the user.
- No numeric card id outside the exempt wire and catalog suites; a guard
  enforces it.
- No test file over 800 lines; a guard enforces it.
- No `@unchecked` pattern extract in tests.
- Every remaining state patch is the test's subject, or carries a comment
  saying why no step reaches the state.
- Every fake has a contract suite that it and the real implementation pass.
  A divergence the contract exposes is fixed in the fake (a production
  change, since the in-memory fakes live in `src/main`) and recorded in the
  ledger.
- Every fix task's mutations are caught, with counts recorded.
- **Brittleness re-check.** Making `OathRules.wakeOptionOpen` always true
  fails no more than the 21 tests it failed after the start-state project.
- The gate is green after every task, and the test count changes only
  through recorded splits or merges.

## Out of scope

- Speed, the next project.
- Pruning, beyond a merge the rubric justifies.
- Golden-log contents.
- Production changes, except fixing a fake's divergence or a seam a
  contract suite needs.
- State patches whose subject is the phase or turn.
