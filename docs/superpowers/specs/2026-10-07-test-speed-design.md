# Test speed: engine hot paths

Date: 2026-10-07. Status: approved design.

This is the fifth and last of the test-suite improvement projects from the
2026-09-27 test audit. The first four are merged:
[Table builder](2026-09-27-test-table-builder-design.md),
[prune low-value tests](2026-09-27-prune-low-value-tests-design.md),
[service and game-log start states](2026-09-28-service-log-start-states-design.md)
and the [readability sweep](2026-10-02-test-readability-design.md).

## Problem

A baseline taken on 2026-10-07 (main at 37c8563d) found that the tests are
slow because the engine is slow, not because the tests set up too much.

- **Gate.** `./sbtw root/test frontend/test` takes 46 s of wall-clock time
  and 263 s of CPU. Root suite times sum to 216 s across test threads;
  frontend suites are all fast.
- **Where the time goes.** The 489 root tests that take 0.1 s or more account
  for 187 s, 87% of the summed time. The campaign power suites
  (`MercenariesSuite`, `ScoringFoldOrderSuite`, `CursedCauldronSuite`,
  `StickyFireSuite` and their neighbors) dominate the list.
- **Real work, not contention.** Run alone in a warm JVM, a campaign test
  still takes 100 to 950 ms.
- **Profile.** A Java Flight Recorder profile of the 299 campaign power tests
  puts 83% of the samples inside `ProcedureWalker`. Test setup barely shows.
  Three hot spots stand out:
  1. **Card index rebuilds: 26%.** `CardIndex.from` walks every card container
     of the game. `OperationPipeline.run` calls it at least three times: once
     on the input state for the expected card set, then twice on the same
     output state, in `OperationStateInvariant.cardIds` and again inside
     `DomainValidation.validate`. Executor operations call it again on every
     staged state, and the search runs the pipeline for every hypothetical
     branch.
  2. **Restriction gathering: 38%.** `WalkerPowerGather.breaches`,
     `attributed` and `applyWindowNoted` call `ContributionCollector.gather`
     many times per walk. Each gather filters the command's whole power
     vector with `_.contributions.contains(window)`, then calls
     `contributions(window)` again on each survivor.
  3. **`ClassValue` lookups: about 4% self time.** Most `contributions`
     implementations are `def`s that build a fresh `Map` of `Vector(...)`
     literals on every call. A varargs `Vector(...)` looks up a `ClassTag`
     through `java.lang.ClassValue`, so hot spot 2 pays this cost too.
- **Existing benchmark.** `SearchBudget` (hand-run, 50 ms budget, from the
  [global operation restrictions design](2026-09-30-global-operation-restrictions-design.md))
  reports Campaign start at 26 ms and reading its park at 0 ms.

The live server runs the same engine, so these fixes speed up play as well
as tests.

## Goal

Remove the three hot spots without changing any game result, and leave behind
a repeatable way to measure and profile the tests.

## Scope

In scope: the three hot spots above, and a profiling script.

Out of scope, and follow-ups if the next profile still shows them:

- reusing one `gather` result across the two or three calls per window inside
  a single `attributed` pass;
- memoizing `gather` across calls, keyed on its full context;
- skipping the state invariant inside search simulations;
- test-side changes such as starting campaign tests closer to their decision;
- build changes such as keeping `frontend fullLinkJS` out of `root/test`.

## Decisions

1. **Done means the three hot spots are fixed, with numbers before and
   after.** There is no percentage target. Whatever the next profile shows is
   follow-up work, not scope creep.

2. **One card index per game state.** `OathGame` gains
   `lazy val cardIndex: Either[Vector[CardIndexProblem], CardIndex]`, the
   result of indexing the game with no expected cards. `OathGame` is an
   immutable case class and every change makes a new instance through `copy`,
   so the cached index can never describe a different state. Case-class
   equality and hashing ignore members declared in the class body, so the
   field changes no equality, no map key and no codec. The doc on the field
   states this immutability contract.

   Every production caller that indexes a game without expected cards reads
   the field instead of calling `CardIndex.from`. That is the operation
   adapters and writes, `PowerOperations`, `CardMovementOperations`, the
   invariant, the projectors and the game-log lines.

   `CardIndex.from(game, expectedCards)` keeps its exact output. The
   expected-card argument only adds "missing card" problems, so it is
   computed from the cached index's ids rather than by indexing again.
   `DomainValidation.validate` therefore reads the cached index as well. The
   effect is that one pipeline run indexes its input state at most once (and
   usually not at all, because the previous run already indexed it) and its
   output state once.

   No new test covers the field. It has nothing to get wrong that the
   existing suites would not catch, and a staleness test would pass by
   construction.

3. **The invariant still runs on every pipeline run, including runs inside a
   search.** Every `Left` from `OperationPipeline.run` already counts as a
   rejection in the search, so skipping the invariant there could change
   which options are legal whenever the executor has a bug. With decision 2
   the invariant is much cheaper. Skipping it is a follow-up decision, only
   if the profile still shows it.

4. **A window index on `WalkerPowers`.** A new value in `powerresolver`
   indexes a vector of powers by window. For each `PowerWindow` it lists the
   powers whose `contributions` hook that window, in the vector's order,
   paired with their contributions at that window. It calls `contributions`
   once per power when it is built.

   `WalkerPowers` builds it once per instance as a `lazy val`, the same way
   it already caches `hasRestrictions` and `quiet`. `hasRestrictions` derives
   from the index.

   `ContributionCollector.gather` takes the window's entries from the index
   instead of a power vector and the `contributions` calls. Steps 2 to 5
   (applicability, ignores, order and splitting) are unchanged. Tests that
   call `gather` directly build an index from their power vector.

   This removes both the per-call `Map` rebuild and its `ClassValue` lookups
   for every power, including powers added later, without editing the 42
   `contributions` implementations.

   The doc on `ContributingPower.contributions` states the contract the index
   relies on: `contributions` is pure and the same on every call for a given
   instance. All 42 implementations already meet it; none holds mutable
   state.

5. **One unit test for the window index.** It asserts, for a small set of
   stub powers: each window lists exactly the powers that hook it; the order
   is the input order; a power that hooks several windows appears under each
   with that window's contributions; and a window no power hooks lists
   nothing. The index is new logic that can be wrong in those ways.

6. **No timing guard.** A test with a time budget is flaky under parallel
   load and breaks the determinism rule. A counting hook in production code
   would be a test back door. The profiling script is the guard.

7. **A committed profiling script.** `scripts/profile-tests.py <test filter>`
   runs `./sbtw` with Java Flight Recorder on the filtered root tests. It then
   prints the slowest suites and tests from the JUnit XML reports written by
   that run, plus the top self and inclusive `oathdigital` frames from the
   recording. Output goes under `target/profile/`. A short guide,
   `docs/testing/profiling.md`, explains how to run it and how to read it.
   `SearchBudget` stays as it is; its numbers are reported alongside.

## Behavior proof

- The gate (`./sbtw root/test frontend/test`) stays green, and no existing
  test changes except where a test calls `gather` directly and now builds an
  index. The golden game logs and the stream suite's full replay games are
  part of the gate, so their journals and logs stay identical.
- The window index gets the unit test from decision 5.

## Measurement

Each task reports before and after:

- gate wall-clock time and CPU time;
- summed root suite time and the ten slowest suites;
- the top frames from the profiling script on
  `oathdigital.gameplay.powers.campaign.*`;
- `SearchBudget`'s two timings.

## Process

A spec (this document), then a light plan with three tasks:

1. The profiling script, its guide and a fresh baseline.
2. The card index (decision 2).
3. The window index (decisions 4 and 5).

Each task is profiled before and after, reviewed by a subagent and gated. The
work happens in a worktree and is merged into local main.
