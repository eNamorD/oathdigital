# Prune low-value tests

Date: 2026-09-27. Status: implemented (see Result).

This is the second of five test-suite improvement projects from the
2026-09-27 test audit. The first, the [Table builder](2026-09-27-test-table-builder-design.md),
is merged. Still queued after this one: service and log start states, a
readability sweep, and speed.

## Problem

The suite has about 2785 tests: 2331 in the root project, 455 in the frontend
(the 49 shared-project tests were not surveyed; they are the reference codec
suites the others lean on). A test earns its place when a maintainer could
break a behavior that matters and only this test would say so. A survey of
every root and frontend test (the [ledger](2026-09-27-prune-low-value-tests-ledger.md))
recorded 356 findings that fail that bar (some cover a group of tests), in
recurring shapes:

- **Registration checks.** About 60 tests assert that a power "is
  registered". Most sit at the top of a power suite whose every other test
  already fails if the power is missing; others restate the power's
  constructor constants, or count that a card's plan appears once in a
  hand-written list.
- **Weak assertions.** About 110 tests would pass with the behavior in their
  title removed: a bare `.isLeft` that any rejection satisfies, `.isRight`,
  `.size`, `nonEmpty`, `isInstanceOf`. A few test the wrong thing entirely
  (a facedown Grasping Vines adviser, which never kills in either
  orientation; a Proving Grounds region with nothing else to discard).
- **Tautologies.** Tests that restate a constant or a catalog value, read a
  case class's fields back, check the test helper instead of production code,
  or compare a function with itself.
- **Duplicates.** The same outcome checked twice through the same entry
  point: in neighbouring suites, a generic gate re-tested in every power suite,
  the frontend re-testing the shared codecs, and service tests repeating one
  replay-equality scenario.
- **Dead code.** Production code whose only callers are its own tests:
  `OperationShadow`; `PowerOperationPlanner` with its relic operations;
  `RuleRegistry`; the `presentation` package (`VisualResolver`, `ViewModel`);
  five `AtlasState` methods; `IdentityRepository.createTrustedSeats`;
  `CatalogSelection`; the Chancellor supply bands that exist only in a test;
  and the frontend `ModifierFlowDraft.submission` `case other` branch.
- **Obsolete guards.** Tests pinning the absence of names from finished
  migrations, or gates that no longer exist.

## Goal

Every remaining test checks a behavior through a production entry point, and
would fail if that behavior broke. Nothing is deleted without a stated proof,
and every finding ends with a recorded outcome.

## Rules

Each finding falls under one rule. The rule says what happens and what must be
shown first.

| Rule | Action | Proof before acting |
|---|---|---|
| Tautology | delete | no production code runs in the test, or only code whose result the test itself supplied |
| Duplicate or covered | delete, or merge the unique assertions into the survivor | name the surviving test; it checks the same outcome through the same entry point (or a scenario that runs it) |
| Registration check | delete; add one catalog-wide test that every power id is registered exactly once | the suite's behavior tests run through the default catalog |
| Data pin | delete, unless the value is persisted or on the wire | a grep shows the value is not a journal or wire key |
| Dead code | delete the production code and its tests | a grep of `src/main`, `shared/src` and `frontend/src/main` finds no caller |
| Obsolete guard | delete | the guarded symbol or gate is gone from production |
| Source-text guard | keep the ones protecting a boundary the compiler cannot enforce; delete obsolete and duplicate ones | named per guard in the ledger |
| Weak | strengthen to the exact value or violation the title names | the new assertion states the outcome (see Verification) |

Settled cases:

- **Data pins.** A pin stays only if the value is persisted or sent on the
  wire, since a change there breaks saved games. The catalog corpus-count test
  in `CatalogLoaderSuite` stays as a deliberate drift alarm.
- **Game log.** Focused game-log tests stay even where a golden log implies
  them: they name one behavior each, and a regenerated golden is easy to
  approve without reading.
- **Source-text guards kept** (in `BackendArchitectureSuite`): powers do not
  import the walker and the engine names no power; protocol decoders use no raw
  `.str`; application layer imports; model and gameplay layer imports; the
  800-line cap; the executor-bypass sentinel. The persistence schema guards
  (no raw bearer token or seat code stored) also stay.
- **Trusted seats.** Four server and application suites seed seats through the
  dead `createTrustedSeats`; they are reseeded through the production writer
  `HsqldbTrustedGameStore`. Coverage of `resolveTrustedSeat` stays.
- **Folds.** `ForgeRulesSuite` folds into `ForgeProcedureSuite`,
  `RestWalkerSuite` into `RestSuite`, and the first nine behavior tests of
  `BackendArchitectureSuite` move to the suites of the code they test. The
  frontend `ProtocolTestCommands` fixture is retired in favour of `GameIntent`.
- **Mislabelled tests** are fixed to test what their title says (for example
  the Rotting Fortress "facedown Beast" case, whose adviser is faceup).
- **Stale titles** that name retired things ("v2", "v3", "the fixed dev one")
  are renamed when their test is touched.

Out of scope: the replay and wire round-trip lines embedded in behavior tests
(the speed project), and tests that are merely long or setup-heavy (the
readability project).

## Confidence gate

The survey gave each finding a confidence. High-confidence findings are acted
on. Medium- and low-confidence findings are re-read first; if the proof still
does not hold, the test stays and the ledger says why. The survey's "leads"
(behaviors suspected to be tested again in another area) are checked in step 4
and become findings only once confirmed.

## Execution

Work happens in a worktree on branch `test/prune-low-value`, forked from local
`main`. Each step is a checkpoint, lowest risk first:

1. **Dead code.** Production code and its tests, one commit per unit.
2. **Registration checks.** Delete them; add the catalog-wide uniqueness test.
3. **Tautologies, data pins, obsolete guards, source-text trims.**
4. **Duplicates and merges**, area by area, including the folds and the
   confirmed leads.
5. **Weak to strong**, area by area.

## Verification

- After each step the root and frontend suites pass, and the test count is
  recorded in the ledger.
- **Mutation checks.** A strengthened test must fail when its behavior breaks.
  For each test the survey showed would pass with its rule removed (the
  facedown Grasping Vines and Toll Roads tests, Proving Grounds, Fearsome
  Shield, `SelectedModifier` eligibility, and the others the ledger marks that
  way), the production rule is broken temporarily, the test is run and seen to
  fail, and the rule is restored. For other strengthened tests, an exact
  expected value is enough.
- **Review.** After each step a review subagent (never a higher model or
  effort than the main agent) checks that step's ledger entries against the
  diff, above all that each deleted duplicate's survivor really covers the same
  outcome.

## Done when

Every ledger entry is ticked with an outcome, both suites are green, and a
Result section here records the test count and summed CPU time before and
after.

## Result

Implemented on branch `test/prune-low-value`, 2026-09-28. Every ledger entry
carries an outcome; the ledger's Progress table has the counts after each
step.

| | Before | After |
|---|---|---|
| Root tests | 2331 | 2101 |
| Frontend tests (with the 49 shared) | 508 | 464 |
| Root CPU, summed per test | 131.9s | 88.4s |
| Frontend CPU | 3.1s | 1.7s |

The CPU sums vary by tens of percent with machine load (the Table builder's
end state measured 93.3s for its 2329 tests), so the fall in root CPU is
mostly noise; the test count is the measure. Wall time is unchanged.

**Outcomes.** Of 360 findings and 56 leads: 215 deleted or trimmed with
their proof, 82 strengthened, 13 merged into a survivor, 47 kept with a
reason, and 7 leads confirmed as duplicates and removed; the other leads
found no duplicate, were already handled, or were out of scope.

**Dead production code removed** (about 500 lines): `OperationShadow`; the
relic placement planners (`PowerOperationPlanner`, `DrawTopRelic`,
`PlaceRelicAtSite`, `RelicPlacement`) and `RecoverOutcomeMismatch`;
`RuleRegistry`, `TypedRuleHandler` and the rule query types; the
`presentation` package; `AtlasState`'s methods and `AtlasRemoval`;
`IdentityRepository.createTrustedSeats` with its validation and failure
cases; `CatalogSelection` and the empty catalog shells. The frontend's
modifier submission now takes a `StartWalker`, which removes a branch no
caller reached.

**Structure.** `ForgeRulesSuite`, `RestWalkerSuite`, `ContributingPowerSuite`,
`EnumShapeSuite`, `GameStartedSuite`, the three `PowerWindow` key suites,
`VisualResolverSuite` and the frontend `ProtocolTestCommands` fixture are
gone; `BackendArchitectureSuite` is source-text guards only, its behavior
tests moved to the suites of what they test. About 75 per-power
"registered" tests became one `PowerCatalogUniquenessSuite`, and the
per-power copies of the generic phase-power gates went. Wire keys that were
pinned only by deleted tests now sit in `EnumWireKeySuite`.

**Tests that passed by accident.** The Grasping Vines and Toll Roads facedown
tests used advisers, which never act; Empty Grounds had nothing else in its
region to discard; Fearsome Shield's checks read the wrong actor's tree; the
Rotting Fortress "facedown" adviser was a faceup Hearth card; the locked
adviser's faceup half was refused because the card was not a facedown
adviser; `SelectedModifier`'s eligibility probe applied everywhere. Each now
fails when its rule is broken; thirteen mutations were run to show it.

**Review fixes.** The Task 4 review found that deleting the per-power
facedown-relic copies left no test of that gate through the power path, so
`PhasePowerSuite` has one generic test for it (mutation checked); it also
restored two projection codec cases to the shared fixture, the log gateway's
other-game refusal, an executor-level banner check and two Rest assertions
the survivors lacked.

**Kept on purpose.** The catalog corpus-count alarm and the handler
vocabulary fingerprint (deliberate tripwires); every pin of a value that is
journaled or sent to the frontend; focused game-log tests; tests at a
different entry point from their look-alike; five harness suites' catalog
presence tests and the four Rivers' registration test, their only proof of
catalog wiring.

**Raised, not changed here.** The server still projects
`legalTravelDestinations` and `actionFamilies`, which the frontend never
reads. The card inspection overlay leaves focus on its hidden close button
when its opener has been rebuilt away. `GameLogPropertiesSuite` checks
script coverage against a hand-kept list of procedures, which has no
forced-wake script.

