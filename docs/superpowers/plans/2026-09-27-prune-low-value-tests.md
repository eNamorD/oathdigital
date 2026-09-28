# Prune Low-Value Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove or strengthen every test the survey found low value, so each remaining test checks a behavior through a production entry point and fails when that behavior breaks.

**Architecture:**
- The work is driven by the ledger: 356 findings, each a checkbox with a category, a suggested action, a confidence and evidence.
- Each task handles one of the spec's five steps.
- For every finding, the executor checks the spec rule's proof, acts, and ticks the ledger entry with its outcome.
- Dead production code is deleted together with its tests.
- One catalog-wide test replaces about 60 per-power registration tests.

**Tech Stack:** Scala 3, munit, sbt via `./sbtw` (projects `root` and `frontend`).

**Spec:** `docs/superpowers/specs/2026-09-27-prune-low-value-tests-design.md`. Read it first; its Rules table and Settled cases decide every outcome.

**Ledger:** `docs/superpowers/specs/2026-09-27-prune-low-value-tests-ledger.md`. Line numbers there refer to commit `4fa6c568`. When a line has moved, find the test by its title.

## Global Constraints

**Commands and workflow**
- Tests:
  - one suite: `./sbtw "root/testOnly oathdigital.gameplay.RestSuite"`
  - all root tests: `./sbtw root/test`
  - all frontend tests: `./sbtw frontend/test`
- Keep a full-run log with `./sbtw root/test 2>&1 | tee <scratchpad>/root-<step>.log`. The last line reads `Passed: Total N, Failed 0, ...`.
- CPU time: `python3 <scratchpad>/timing.py '.' <log>` sums munit's per-test times. The script is in the session scratchpad; if it is missing, recreate it from its docstring. It sums the trailing `N.NNNs` of every `  + ` line under an `oathdigital.…:` suite header, with ANSI codes stripped.
- The build uses `-Werror`, so an unused import fails compilation. Remove imports that a deletion leaves unused.
- Never use `preview_start`: it hits the live DB.
- Never stage or commit `docs/ROADMAP.md`.
- HEAD on `main` moves under you, because other sessions work there. Re-check `git log -1` before any amend, reset or rebase, and prefer new commits.
- Work in the worktree on branch `test/prune-low-value`. Before running `./sbtw` there, symlink the main checkout's `.tooling` into the worktree.
- To merge at the end: call `ExitWorktree(keep)`, then merge into local `main` yourself. `origin` lags local `main`, so never pull it.
- End every commit message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

**Writing**
- Persisted text (code, comments, commits, docs, ledger outcomes) is normal prose.
- A test that is renamed or merged keeps the repo's title style: a sentence that states the behavior.
- When a test is touched, rename a title that names retired things ("v2", "v3", "the fixed dev one").
- Focused game-log tests stay, even where a golden log implies them.

**Prune rules (from the spec)**
- **Never delete without the rule's proof:**
  - a duplicate names its surviving test;
  - dead code shows a grep with no caller;
  - a data pin shows a grep proving it is not a wire or journal key.
- **Confidence gate:**
  - A high-confidence finding is acted on after its proof is checked.
  - Re-read a medium- or low-confidence finding first, and keep it if the proof fails.
- **Every finding ends ticked, with an outcome after its evidence:**
  - `**done**`
  - `**strengthened**`
  - `**merged into X**`
  - `**kept: reason**`
- **Never weaken an assertion.** A merge carries the unique assertions of the deleted test into its survivor.
- **Test counts.** Record them in the ledger's Progress table after each task.
- **Out of scope:**
  - replay and wire round-trip lines inside behavior tests;
  - long or setup-heavy tests;
  - the 49 shared-project tests.

---

### Task 0: Worktree and baseline

**Files:**
- Modify: `docs/superpowers/specs/2026-09-27-prune-low-value-tests-ledger.md` (add a Progress table under the intro)

- [ ] **Step 1: Create the worktree.** Use `EnterWorktree` with the branch `test/prune-low-value`. It must fork from local `main`; check that `git merge-base HEAD main` equals `git rev-parse main`.
- [ ] **Step 2: Link the tooling.** Run `ln -s /Users/roman/projects/oathdigital/.tooling .tooling` in the worktree root. `.tooling` is gitignored.
- [ ] **Step 3: Run the baseline.**

```bash
./sbtw root/test 2>&1 | tee <scratchpad>/root-baseline.log | tail -1
./sbtw frontend/test 2>&1 | tee <scratchpad>/frontend-baseline.log | tail -1
python3 <scratchpad>/timing.py '.' <scratchpad>/root-baseline.log <scratchpad>/frontend-baseline.log
```

Expected: both runs end `Failed 0`. The root total is about 2331 and the frontend total about 459.

- [ ] **Step 4: Add the Progress table.** Put it in the ledger, after the intro paragraph:

```markdown
## Progress

| After | Root tests | Frontend tests | Root CPU | Frontend CPU |
|---|---|---|---|---|
| Baseline | 2331 | 459 | 93.3s | … |
```

Fill in the measured numbers; they replace the example values above.

- [ ] **Step 5: Commit.**

```bash
git add docs/superpowers/specs/2026-09-27-prune-low-value-tests-ledger.md
git commit -m "docs(specs): record the prune baseline

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 1: Dead production code and its tests

Each unit below is one commit. For each unit:

1. Run the caller grep over `src/main shared/src frontend/src/main`. It must show only the unit's own definition, or comments.
2. Delete the code and its tests.
3. Compile and run the affected suites.
4. Tick the ledger entries.

**Units:**

1. **OperationShadow**
   - Grep: `grep -rn "OperationShadow" src/main shared/src frontend/src/main`
   - Delete `src/main/scala/oathdigital/gameplay/operations/OperationShadow.scala`.
   - Delete the test `OperationExecutorSuite` "shadow comparison distinguishes parity rejection and mismatch".
2. **PowerOperationPlanner**
   - Grep: `grep -rn "PowerOperationPlanner\|PlaceRelicAtSite\|DrawTopRelic\|RelicPlacement" src/main shared/src frontend/src/main`
   - Delete `RelicPlacement`, `PowerOperationPlanner`, `DrawTopRelic` and `PlaceRelicAtSite` from `src/main/scala/oathdigital/gameplay/operations/PowerOperations.scala`. Keep everything else in that file (`Costs` and so on).
   - Reword the comment at `CatacombsContribution.scala:31`, which cites `PowerOperations.PlaceRelicAtSite:93`: say what `relicSlots` checks without citing the deleted code.
   - Delete the tests `PowerOperationsSuite` "DrawTopRelic and PlaceRelicAtSite preserve top-card and facedown rules" and "power operation plans apply in order and fail without a partial result".
3. **RuleRegistry**
   - Grep: `grep -rn "RuleRegistry\|TypedRuleHandler" src/main shared/src frontend/src/main`
   - Delete `TypedRuleHandler` and `RuleRegistry` from `src/main/scala/oathdigital/gameplay/RuleResolution.scala`. If nothing else remains in the file, delete the file.
   - Delete the tests `RuleResolutionSuite` "registry lookup is explicit and unknown relevant handlers are safe" and "resolution ordering is priority source identity then handler ID".
4. **The presentation package**
   - Grep: `grep -rn "oathdigital.presentation\|VisualResolver\|PresentationExample\|ViewId\|BoardView" src/main shared/src frontend/src/main`
   - Delete `src/main/scala/oathdigital/presentation/`.
   - Delete `src/test/scala/oathdigital/presentation/VisualResolverSuite.scala`.
   - Delete the test `OpaqueIdSuite` "ViewId keeps its validation and prints raw".
5. **AtlasState's unused methods**
   - Grep each method: `grep -rn "addRecent\|removeRecent\|removeForgotten\|mostRecent\|mostForgotten" src/main shared/src frontend/src/main`
   - Delete the methods with no caller outside `AtlasState`.
   - Delete their five `WorldModelSuite` tests; the ledger lists them in the model/persistence section.
6. **IdentityRepository.createTrustedSeats**
   - Grep: `grep -rn "createTrustedSeats" src/main shared/src frontend/src/main`
   - Delete it from `IdentityRepository` and its HSQLDB implementation.
   - Delete the tests `HsqldbIdentityRepositorySuite` "trusted seat creation rejects…" (both of them).
   - Reseed the seats in its other tests, and in `server/TrustedSeatRoutesSuite`, `server/SessionSecuritySuite`, `server/TrustedGameProvisioningSuite` and `application/MembershipAuthorizationServiceSuite`. Use the production writer `HsqldbTrustedGameStore.create`: read `TrustedGameProvisioning` for how production calls it.
   - Keep every test of `resolveTrustedSeat`.
   - Run all six suites.
7. **CatalogSelection**
   - Grep: `grep -rn "CatalogSelection" src/main shared/src frontend/src/main`
   - Remove the type and the parameter `ServerRuntime` passes. The loader already ignores it, as `CatalogModel.scala` says.
   - Delete the test `CatalogLoaderSuite` "legacy selection flags do not produce partial catalogs".
8. **Chancellor supply bands**
   - Production has no Chancellor table: `grep -rn "Chancellor" src/main/scala/oathdigital/gameplay/phases` shows no supply bands.
   - Delete the test `SupplySuite` "the Chancellor uses the distinct Imperial refresh bands" and its test-local table.
   - In the same commit, point the two Exile tests of that suite at `FinishRestProcedure.ExileSupply` instead of their hand copy. This is the ledger's WEAK finding in the same file.
9. **The frontend ModifierFlowDraft.submission `case other` branch**
   - Proof: both callers, `ModifierFlow.scala` around lines 102 and 115, pass only `StartWalker`.
   - Change the parameter type so the compiler proves it: take a `StartWalker`, or match exhaustively.
   - Delete the test `ModifierSelectionStateSuite` "submission leaves non-walker commands on the legacy ordered-modifiers channel untouched".
   - Run `./sbtw frontend/test`.

- [ ] **Step 1:** Units 1–3 (gameplay), one commit each. Commit message style: `refactor(operations): delete the unused OperationShadow`.
- [ ] **Step 2:** Units 4–5 (model), one commit each.
- [ ] **Step 3:** Unit 6 (trusted seats). Run `./sbtw "root/testOnly oathdigital.server.* oathdigital.persistence.* oathdigital.application.MembershipAuthorizationServiceSuite"`. Expected: PASS.
- [ ] **Step 4:** Units 7–9, one commit each.
- [ ] **Step 5:** Run `./sbtw root/test` and `./sbtw frontend/test`. Expected: `Failed 0`. Add a Progress row "Task 1" and commit the ledger.
- [ ] **Step 6: Review checkpoint.** Dispatch a review subagent, never on a higher model or effort than yours. Prompt: check each Task 1 commit against the spec's dead-code rule. Re-run each grep and confirm no caller remains, no production behavior was removed, and the reseeded trusted-seat tests still exercise `resolveTrustedSeat`. Apply its fixes before Task 2.

---

### Task 2: Registration checks

**Files:**
- Create: `src/test/scala/oathdigital/gameplay/powers/PowerCatalogUniquenessSuite.scala`
- Modify: the power suites listed in the ledger (areas "Powers: action, banner, targeting, title" and "Powers: the other folders"), `PowerImplementationStatusSuite.scala`, `gameplay/title/ChaosCultSuite.scala`

- [ ] **Step 1: Write the uniqueness test.**

```scala
package oathdigital.gameplay.powers

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.PowerId

/** Every production power is registered once per catalog, so a card's power
  * cannot fire twice. The per-power suites prove presence by running each
  * power through the default catalogs; this suite is the one place that
  * catches a doubled entry.
  */
class PowerCatalogUniquenessSuite extends munit.FunSuite:
  private def repeated(ids: Vector[PowerId]): Vector[PowerId] =
    ids.groupBy(identity).collect {
      case (id, copies) if copies.size > 1 => id
    }.toVector

  test("no power id is registered twice in the walker catalog"):
    assertEquals(repeated(WalkerPowerCatalog.default(catalog).powers.map(_.id)),
      Vector.empty)

  test("no power id is registered twice in the phase catalog"):
    assertEquals(repeated(PhasePowerCatalog.default(catalog).powers.map(_.id)),
      Vector.empty)
```

- [ ] **Step 2: Run it.** `./sbtw "root/testOnly oathdigital.gameplay.powers.PowerCatalogUniquenessSuite"`. Expected: PASS.
  - If it fails, a real duplicate exists. Read why before going on: a card with two legitimate contributions under one id is a finding for the user, not something to hide.
- [ ] **Step 3: Mutation check.**
  - Append ` :+ TakeWealthLimit` a second time at the end of `WalkerPowerCatalog.default`.
  - Run step 2's command. Expected: FAIL, naming TakeWealthLimit's id.
  - Revert with `git checkout src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`.
- [ ] **Step 4: Delete the registration tests the ledger lists.** They fall into four groups:
  - the 32 "X is a registered phase power" tests;
  - the persistent-rule "registered…, so it is automatic" tests (Circlet, Vault, Fortress, Mob, Gossip, Grasping Vines, Toll Roads, Book Binders, Horned Mask, Marble Fountains, Hunger, Sacred Ground, Secret Police, Vow of Obedience, Chaos Cult);
  - the "registered selected X modifier" tests (Wild Cry, Welcoming Party, Animal Playmates, Birdsong, Cup of Plenty, Knights Errant, Rowdy Pub, Relic Worship, Augury, Truthful Harp, Dragonskin Drum, Forest Paths, Royal Stables, Tents);
  - the campaign "registered once" tests (Fire Talkers, Mercenaries, Horse Archers, Hospital, Longbows, Black Sword, Nature Worship, Cracked Sage, Storm Caller, Rampart, Gleaming Armor) and Bag of Siegeworks' retired-stub test.

  The proof for each deletion: the suite's behavior tests run through `PhasePowerCatalog.default` or `WalkerPowerCatalog.default` (or `SearchFixture.rules` / `TargetsFixture`, which use them). Check this per suite.
  - If a suite's behavior tests use a private catalog, such as `WalkerPowers(Vector(power))`, keep its registration test and tick it `kept: only proof of catalog wiring`. The harness suites for Faithful Friend, Garrison, A Small Favor, Twin Brother and Shifting Fog are kept for this reason.
  - Remove the helper objects the deletions leave unused, such as `WalkerPowerCatalogHas` in GossipSuite.
- [ ] **Step 5: Keep the facts only registration tests pinned.**
  - Animal Playmates, Birdsong, Dragonskin Drum and Royal Stables are free, and only their registration test checked `Cost.free`. Before deleting it, add to the suite's main behavior test an assertion that the actor's favor and secrets are unchanged after use.
  - Augury is offered only for Search, and only its registration test checked `actions`. Add an assertion that `offerableWalkerPowers` for another action does not contain Augury.
- [ ] **Step 6: Collapse PowerImplementationStatusSuite.** It has 8 per-list tests: the "Vision-play cards…" test and the "catalog batch 2 …" tests. Delete them. Keep the tests of the `implemented` predicate's branches.
- [ ] **Step 7: Run and commit.** Run `./sbtw root/test`. Expected: `Failed 0`. Tick the entries, add a Progress row "Task 2", then commit with the message `test(powers): replace per-power registration checks with one uniqueness test`.
- [ ] **Step 8: Review checkpoint.** Dispatch a review subagent on the same model or lower. Prompt: for each deleted registration test, confirm that the suite's remaining behavior tests use a default catalog, and that step 5's facts are still asserted.

---

### Task 3: Tautologies, data pins, obsolete guards, source-text trims

This task covers the ledger entries in categories TAUTOLOGY, TYPE-PREVENTED, OBSOLETE (those not handled in Task 1), SOURCE-TEXT and OTHER that describe a data pin. Work area by area, in ledger order, with one commit per area.

- [ ] **Step 1: Tautologies.**
  - Proof: the test runs no production code, or only code whose result the test itself supplied. Examples: reading case-class fields back; comparing `boxClass(kind)` with itself; a stub `Authenticator` that returns its input.
  - Delete the test.
  - A tautological single line inside a useful test (an "immutable input unchanged" check) is removed, and the rest of the test stays.
- [ ] **Step 2: Data pins.**
  - For each pinned string or number, check whether it is persisted or on the wire:

```bash
grep -rn '"<the value>"' src/main/scala/oathdigital/serialization src/main/scala/oathdigital/persistence shared/src/main
```

  - No hit: delete the pin.
  - A hit: keep it and tick it `kept: wire key (<file>)`.
  - Always keep the `CatalogLoaderSuite` corpus-count test ("the production catalog contains only the final runtime corpus") as the drift alarm.
  - The `PowerWindow` key suites (`CampaignWindowsSuite`, `ChallengeWindowsSuite`, `NegotiationWindowsSuite`) go only if the grep finds no serializer for `PowerWindow.key`. If they go, delete the files.
- [ ] **Step 3: Obsolete guards.**
  - `BackendArchitectureSuite`:
    - Delete "legacy central power shell cannot return", "generic power operations are not independently replayable events" and "retired setup and browser-memory symbols do not return". Confirm first with a grep that the guarded names are gone.
    - Delete "application projection collaborators stay bounded and layer-independent" and "frontend production sources stay bounded and renderers remain isolated". Their checks are inside the kept import guard and the 800-line guard. Keep the `ScopedProjectionContext` existence check only if nothing else asserts it.
  - `DiscardRestrictionsCoverageSuite`: delete the second test (it is contained in the first), and delete the stale `delegated` map entry for CardPlay.
  - `EnumShapeSuite`: delete it, moving any wire key it pins that is not pinned elsewhere into `ActionValuesSuite`.
  - Delete the remaining OBSOLETE entries (EndWake's ported gates, Campaign's "no first-game gate", Recover's relic-gate leftovers, HttpGameClientSuite's decode halves for fields the frontend no longer reads, and so on). The proof is a grep showing the gate or field is unused.
- [ ] **Step 4: TYPE-PREVENTED.** Delete them.
  - Exception: the `DecisionQuerySuite` negative-count cases, which the ledger marks keep.
  - Exception: `OpaqueIdSuite` "an id prints as its raw string", which log text depends on. Keep it, ticked `kept: log text relies on raw printing`.
- [ ] **Step 5: Run both suites and commit per area.**
  - Run `./sbtw root/test` and `./sbtw frontend/test`. Expected: `Failed 0`.
  - Commit messages look like `test(model): delete tautological and obsolete tests`.
  - Add a Progress row "Task 3".
- [ ] **Step 6: Review checkpoint.** Dispatch a review subagent on the same model or lower. Prompt: sample every deletion in this task. Confirm the stated proof: no production code runs, the grep shows no wire key, or the guarded symbol is gone.

---

### Task 4: Duplicates, merges, folds and leads

This task covers the entries in categories DUPLICATE and COVERED-PLUMBING, plus the folds and the "Leads to check in step 4" sections. Work area by area, with one commit per area.

- [ ] **Step 1: Check each duplicate.**
  - Open the named survivor. It must assert the same outcome through the same entry point, or through a scenario that runs that entry point.
  - If the duplicate has an assertion the survivor lacks, move that assertion into the survivor.
  - Then delete the duplicate and tick it `merged into <Suite>:"<title>"` or `done (survivor <Suite>:"<title>")`.
  - If the survivor does not cover the outcome, tick it `kept: <why>`.
- [ ] **Step 2: Folds.**
  - `ForgeRulesSuite` into `ForgeProcedureSuite`:
    - Move "the actor must be able to fund the printed cost…" and the unaudited-handler test into the P1 section.
    - Delete the start-gate test, which is covered by ForgeProcedureSuite's exact-violation gates.
    - Delete the file.
  - `RestWalkerSuite` into `RestSuite`:
    - Move the phase-and-owner gate test and the round-end test.
    - Delete the rest as duplicates of RestSuite and WakeAutoEndSuite.
    - Delete the file.
  - `BackendArchitectureSuite`'s first nine tests are behavior tests of `GameplayTransition`, `RuleSourceIndex` and `PowerResolver`.
    - Move each one to that type's suite. Create `RuleSourceIndexSuite` if none exists.
    - What remains in `BackendArchitectureSuite` is source-text guards only.
  - Frontend `ProtocolTestCommands`:
    - Replace each use with the `GameIntent` it builds; the builder drops the actor.
    - Delete the assertions that "no actor reaches the payload" where the intent has no actor field. They are TYPE-PREVENTED.
    - Delete the file.
  - `HttpGameClientSuite`: delete the shared-codec round trips the ledger lists. Keep the tests the ledger names as worth keeping:
    - trusted and dev URL shapes;
    - actorless body keys;
    - the JSON-safe `nextSequence` bound;
    - malformed-input decode failures;
    - the log client.
- [ ] **Step 3: Leads.** For each lead, grep the named suites.
  - Confirmed duplicate: add it as a finding under that area, proved as in step 1, then act.
  - Otherwise: tick it `no duplicate found` with a one-line reason.
- [ ] **Step 4: Run both suites and commit per area.**
  - Run `./sbtw root/test` and `./sbtw frontend/test`. Expected: `Failed 0`.
  - Commit messages look like `test(campaign): merge duplicated Campaign tests`.
  - Add a Progress row "Task 4".
- [ ] **Step 5: Review checkpoint.** Dispatch a review subagent on the same model or lower. Prompt: for every deleted duplicate in this task, open the survivor and confirm it asserts the same outcome. List any deletion whose survivor is weaker.

---

### Task 5: Weak to strong

This task covers the WEAK entries and the mislabelled tests. Work area by area, with one commit per area.

- [ ] **Step 1: Strengthen each weak test to the outcome its title names.**

  The usual shape replaces a bare `.isLeft` with the exact violation:

```scala
// before
assert(start(board).isLeft)
// after
assertEquals(start(board), Left(OathViolation.CoreOperationRejected(
  "insufficient-supply", "…the detail the code produces…")))
```

  - Find the violation by running the test once with `assertEquals(result, Left(null))`. The failure prints the actual value. Keep it only if it is the reason the title names.
  - If the actual rejection is for a different reason, the test was passing by accident. Fix the setup so the named reason is the one that fires.
  - For `.isRight`, `.size` or `nonEmpty`, assert the resulting state or the exact collection.
  - For "not offered" tests, assert that `offerableWalkerPowers(...)` does not contain the power id, as CupOfPlentySuite does.
- [ ] **Step 2: Fix the mislabelled and setup-blind tests.**
  - `FortressRulesSuite` "a faceup beast adviser lifts…": its "facedown" value is a faceup Hearth adviser. Build the facedown Beast case the title describes.
  - `GraspingVinesSuite` and `TollRoadsSuite` "a facedown … is not active": place the facedown card at the ruled site with `denizenAt`, not as an adviser.
  - `ProvingGroundsRulesSuite` "Empty Grounds discards every other denizen in its region": stage other denizens in the region first.
  - `FearsomeShieldSuite`, the two tests: assert that `run.offered(b.actor)` does not contain the plan ref.
  - `KnightsErrantSuite` "it cannot be selected for a Campaign…": drop the final `startWalker(...).isLeft` line, which fails for want of Supply, or give the board enough Supply and assert the refusal reason.
  - `SelectedModifierSuite`:
    - eligibility test: give `Probe` `appliesAt = false`;
    - ordering test: assert the order;
    - payment test: assert the exact `PayCost`.
  - `CampaignPlanWindowSuite` "Finish ends the window and a chosen source is not offered again": use two plans and check the rebuilt options.
  - `TravelProcedureSuite` "role and Foundation changes do not gate Travel": also change a Foundation, or rename the test to what it checks.
- [ ] **Step 3: Mutation checks.** For each test in step 2, and for each WEAK entry whose evidence says it "would pass with the behavior removed":
  - Break the production rule temporarily. For example, make Grasping Vines' orientation check always true.
  - Run the suite. Expected: the strengthened test FAILS.
  - Restore the rule with `git checkout <file>`.
  - Tick the entry `strengthened (mutation: <what was broken>)`.
- [ ] **Step 4: Run both suites and commit per area.**
  - Run `./sbtw root/test` and `./sbtw frontend/test`. Expected: `Failed 0`.
  - Commit messages look like `test(travel): assert why Travel modifiers are refused`.
  - Add a Progress row "Task 5".
- [ ] **Step 5: Review checkpoint.** Dispatch a review subagent on the same model or lower. Prompt: for a sample of strengthened tests from each area, confirm that the new assertion names the title's outcome and that the mutation note matches a real break. List any test still passing with its behavior removed.

---

### Task 6: Result and finish

**Files:**
- Modify: `docs/superpowers/specs/2026-09-27-prune-low-value-tests-design.md` (status line and a Result section)

- [ ] **Step 1: Confirm every ledger checkbox is ticked.** Run `grep -c "^- \[ \]" docs/superpowers/specs/2026-09-27-prune-low-value-tests-ledger.md`. Expected: 0.
- [ ] **Step 2: Measure the final state.** Run both suites to logs and run `timing.py` on them. Add a Progress row "Final".
- [ ] **Step 3: Update the spec.** Set the status to `implemented (see Result)`. Add a Result section with:
  - test counts before and after;
  - CPU time before and after;
  - counts of tests deleted, merged, strengthened and kept;
  - what was kept and why, in one paragraph;
  - dead code removed.
- [ ] **Step 4: Commit.** Use the message `docs(specs): record the prune result`.
- [ ] **Step 5: Final review.** Dispatch a review subagent on the same model or lower over the whole branch diff against `main`, then apply its fixes.
- [ ] **Step 6: Finish.** Use the finishing-a-development-branch skill.
