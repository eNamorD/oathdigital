# Test readability fixes: the fix procedure

Every fix task in
[the fixes plan](2026-10-02-test-readability-fixes.md) follows this
procedure. A task names its ledger rows and its files; this file says how to
fix them, prove nothing weakened, and record the outcome.

Spec: [test readability sweep](../specs/2026-10-02-test-readability-design.md).
Ledger: [test readability ledger](../specs/2026-10-02-test-readability-ledger.md).

## 1. Read before you edit

1. Read the spec's Rubric table (rules R1 to R11) and its "Every fix task"
   list.
2. Read the ledger's Review section (the user's five decisions), then every
   ledger row your task names. Each row has a planned outcome: that is your
   instruction for the row. Rule labels stay as filed; work from the planned
   outcome, not the label.
3. Read each file your rows name, and every helper those tests call.

Ledger row format:

```text
| ID | File:line | Test (or "suite") | Rule | Problem | Planned outcome | Outcome | Commit |
```

Shared-fixture rows (IDs `00-…`) name a helper instead of a test.

## 2. The rules for the rewrite

- **Frozen assertions.** Rewrite only the arrange and act lines. An assertion
  may get stronger (an exact value in place of `nonEmpty`, an accessor that
  fails with the actual value in place of `.get`), never weaker, and never
  dropped. A test split into two keeps every assertion in one of the halves.
- **State the facts.** A fact the outcome depends on appears in the test
  body as a `Table` step, in the game's words: card and site names through
  `CatalogNames`, players as `Table.p1`, `p2`, `p3`.
- **Use the testkit accessors**, never `.get`, `.toOption.get`,
  `.left.toOption.get`, `asInstanceOf` or a `val Ready(x) = …: @unchecked`
  extract:
  - `oathdigital.testkit.Unwrap` (all three test trees): `accepted(either)`,
    `rejected(either)`, `present(option, "what was expected")`,
    `as[T](value)`.
  - `oathdigital.testkit.Outcomes` (root): `ready(state)`, `parked(outcome)`,
    `finished(outcome)`, `assertWireRoundTrips(events)`.
  - `Look` reads (root): `boardWarbands`, `cardsAt`, `discardAfter`,
    `forceKind`, and the existing reads.
  Where a row names an accessor the Foundation settled under another name
  (`rightOf`, `built`, `started`, `opened`, `decoded`, `right`, … mean
  `accepted`; `leftOf`, `assertLeft`, `refusal`, `refused`, `failureOf` mean
  `rejected`; `loaded` means `present`; `chooseOne`, `chooseMany`,
  `chooseAmount`, `updated` mean `as[…]`), use the settled name and say so in
  the Outcome cell.
- **Delete local copies.** A suite-local `private def ready`, `accepted`,
  `parked` or `finished` that an accessor replaces is deleted.
- **Helper names describe what they build or do.** A doc comment does not
  excuse a vague name (the user's review, decision 3). Long names are fine.
- **Placeholders.** A planned outcome such as `CatalogNames.site("<printed
  name>")` is filled with the real printed name, read from the catalog.
- **State patches.** A remaining `updateCurrent`, `copy(current = …)` or
  `Table.update` either patches the test's subject or carries a one-line
  comment saying why no `Table` step reaches the state.
- **Shared fixtures.** When your task owns a fixture helper that is inlined
  or renamed, change every caller in the repository in the same task, in
  any area, so no fixture is left half dissolved. A fixture file that
  empties is deleted.
- **R10 rows** (test doubles) belong to the test-double tasks. Leave them,
  and leave their Outcome cells empty.
- **Splits (R11).** Split by subject into the suites the row names. Each new
  suite gets a doc comment saying what it covers. Record the split in the
  Outcome cell: the new file names and test counts.
- **Out of scope:** speed, golden-log contents, pruning (a merge only when a
  row says so), production code (except where a row or a contract suite
  needs a seam), state patches whose subject is the phase or turn.

## 3. Set up the worktree

The controller creates one worktree per task off local `main` (other
sessions move `main`, and the remote lags it):

```bash
cd /Users/roman/projects/oathdigital
git worktree add .worktrees/readability-<task> -b readability-<task> main
ln -s /Users/roman/projects/oathdigital/.tooling .worktrees/readability-<task>/.tooling
```

`<task>` is the task's short name from the plan, for example `04-2`.

Before any edit, record the base and the test counts:

```bash
git rev-parse --short HEAD
./sbtw root/test 2>&1 | tee /tmp/readability-<task>-root-before.log | grep -E 'Passed: Total|Failed: Total|\[error\]' | tail -3
./sbtw frontend/test 2>&1 | tee /tmp/readability-<task>-frontend-before.log | grep -E 'Passed: Total|Failed: Total|\[error\]' | tail -3
```

Root-only tasks may skip the frontend count before, but the gate (step 6)
runs both.

## 4. Fix the rows

Work file by file. After each file, run its suite:

```bash
./sbtw "root/testOnly oathdigital.gameplay.powers.action.ArmedMobSuite"
```

(frontend suites: `./sbtw "frontend/testOnly oathdigital.frontend.<Suite>"`).
A shared-fixture change runs every caller's suite, or the whole root test.

Commit the fixes when every row in the task is done and the gate passes
(step 6). One commit, or one per file group if the task is large:

```bash
git add <the changed test files>
git commit -m "test: <what the task made readable>" -m "Ledger rows <first>-<last>." -m "Co-Authored-By: <your model's trailer>"
```

Never stage `docs/ROADMAP.md`.

## 5. Mutations

Pick 3 to 5 production rules that the rewritten tests guard, spread over
the task's files. For each, one at a time:

1. Copy the production file to `/tmp/readability-mutant.bak`.
2. Break one rule with one small edit (flip a comparison, drop a cost, skip
   a step, return the wrong player).
3. Run the suite that should catch it with `testOnly`; confirm that a
   rewritten test fails, and that it fails for the broken rule.
4. Restore: `cp /tmp/readability-mutant.bak <file>`, then confirm
   `git status --short src/main frontend/src/main shared/src/main` prints
   nothing.

A mutation no test catches is a finding: report it (status
DONE_WITH_CONCERNS) with the rule and the tests you expected to fail. Do
not weaken anything to make it pass, and do not add a test to catch it
unless a row says so.

## 6. The gate

```bash
./sbtw root/test 2>&1 | tee /tmp/readability-<task>-root-after.log | grep -E 'Passed: Total|Failed: Total|\[error\]' | tail -3
./sbtw frontend/test 2>&1 | tee /tmp/readability-<task>-frontend-after.log | grep -E 'Passed: Total|Failed: Total|\[error\]' | tail -3
python3 /Users/roman/projects/oathdigital/.superpowers/readability/frozen.py <base> HEAD
```

Both test runs green. The test totals equal the "before" totals, except for
splits or merges recorded in the Outcome cells. `frozen.py` prints `clean`,
or every line it lists is an assertion you strengthened (list each in your
report beside its replacement).

## 7. Record the outcomes

Fill the Outcome and Commit cells of every row the task names, in the
ledger, in a second commit after the fix commit:

- Outcome: `fixed: <what changed, one line>`, or `kept: <why>` for a Keep
  row or a row whose planned outcome the code proved wrong (say what you
  found), or `R10: test-double task` left empty for R10 rows.
- Commit: the short SHA of the fix commit.

Append one line per mutation to the ledger's `## Mutations` table:

```text
| <task> | <file:line>: <the change> | <the test that failed> | caught |
```

Then commit the ledger:

```bash
git add docs/superpowers/specs/2026-10-02-test-readability-ledger.md
git commit -m "docs: record readability outcomes for <task>" -m "Co-Authored-By: <your model's trailer>"
```

## 8. Report

Write the report file the controller named: rows fixed, kept (with
reasons), the before and after test totals, each `frozen.py` line with its
replacement, the mutations, and any concern.

## 9. Merge (controller)

After the task review passes, the controller brings the branch up to date
with local `main`, reruns the gate in the worktree, and fast-forwards `main`:

```bash
git -C .worktrees/readability-<task> merge main
```

Rerun step 6's two test commands in the worktree, then from the main
checkout:

```bash
git merge --ff-only readability-<task>
git worktree remove .worktrees/readability-<task>
git branch -d readability-<task>
```

If `--ff-only` fails because `main` moved again, repeat from the merge.
