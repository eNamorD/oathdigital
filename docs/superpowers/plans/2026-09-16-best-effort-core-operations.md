# Best-Effort Core Operations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make core operations best-effort by default, with explicit required operations, partial counted effects, and journals containing only effects that executed.

**Architecture:** `CoreOperation.required` states command-time policy. A typed validator and separate resolver find each operation's maximal legal effect against staged state. One breaking `OperationPipeline.run` returns final state, canonical executed operations, and skipped reasons; replay applies those recorded effects without resolving them again.

**Tech Stack:** Scala 2.13, sbt, munit, ujson.

**Spec:** `docs/superpowers/specs/2026-09-16-best-effort-core-operations-design.md` (approved). Finish this plan before `docs/superpowers/plans/2026-09-16-search-walker.md`.

## Global Constraints

- Best-effort applies in the single `OperationPipeline.run`, not in a parallel API or `OperationAttempt` wrapper. Complete all caller changes in this prerequisite.
- An optional operation shrinks to its maximal legal counted effect or skips only for typed rule-impossibility. Required operations perform exactly the requested effect or reject. Structural errors, allowlist rejection, executor errors, and invariant failures always reject.
- `PayCost` is always required and atomic. `Draw` and `Exchange` default to required; `Replace` defaults to optional and shrinks linked counts together. `SpendSupply` defaults to required but can be optional per instance.
- Keep stable `OperationError` codes/details where possible. Do not turn missing card IDs or wrong card sources into best-effort skips.
- Only canonical executed operations go into walker events; `required` and skipped reasons never go on the wire. Replay does not run restrictions or partial-count resolution.
- Existing reducers that apply recorded event outcomes must reject a mismatch between their event's claimed effect and `OperationRun.executed`; they must not silently accept a reduced event fact. Use effect-field comparison that ignores command-time `required`.
- `BuildOps` may return an empty vector; direct `OperationPipeline.run` with empty input rejects. A nonempty all-skipped batch succeeds and creates no walker delta step.
- No old-journal compatibility is required in this pre-release repository. Preserve the 800-line production Scala source cap and run `python3 scripts/check-architecture.py`.
- Per task: red/green focused tests, `./sbtw "test"`, architecture check, and `git diff --check`; commit each independently reviewed task. No frontend changes are planned.

## File map

- `src/main/scala/oathdigital/gameplay/operations/CoreOperations.scala`: required defaults, per-instance overrides, positive `GainSupply` and `SpendSupply`; no attempt wrapper.
- `src/main/scala/oathdigital/gameplay/operations/OperationValidator.scala`: typed `OperationReason` classification, staged contextual restrictions, stable shape errors.
- `src/main/scala/oathdigital/gameplay/operations/OperationResolution.scala` (new): pure maximal-count resolution for optional executable operations, with linked-operation invariants.
- `src/main/scala/oathdigital/gameplay/operations/OperationPipeline.scala`: one staged fold returning `OperationRun`; post-update invariant and atomic rejection.
- `src/main/scala/oathdigital/gameplay/operations/OperationStateMutation.scala`, `OperationStateAdapter.scala`, `OperationExecutor.scala`: Supply split and raw application of canonical operations; no best-effort logic in mutation/replay.
- `src/main/scala/oathdigital/gameplay/operations/DiscardRestrictions.scala` (new): reusable catalog-backed discard-immunity restriction, with no Dazzle ID.
- `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`, `WalkerReplay.scala`: pass restrictions at command time and record only canonical executed operations; replay remains raw.
- `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`: distinct Supply wire kinds and effect-only operation encoding.
- Existing action/procedure and power callers: adapt all `OperationPipeline.run` results and all `AdjustSupply` construction/pattern matches in one prerequisite. Start with `rg -n 'OperationPipeline\.run\(|AdjustSupply' src/main/scala src/test/scala`; the current tree has 13 production pipeline call sites and Travel cost transforms depend on the signed amount. Supply references also occur in `actions/{Search,Economy}.scala`, `actions/{travel,recover,forge}/*Procedure.scala`, `phases/rest/FinishRestProcedure.scala`, `powers/travel/TravelSitePowers.scala`, and `gameplay/walker/ProcedureWalker.scala`.

---

### Task 1: Operation declarations and Supply split

**Files:** Modify `src/main/scala/oathdigital/gameplay/operations/CoreOperations.scala`, `OperationValidator.scala`, `OperationStateMutation.scala`, `OperationStateAdapter.scala`, `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`, `src/main/scala/oathdigital/gameplay/actions/Search.scala`, `Economy.scala`, `src/main/scala/oathdigital/gameplay/actions/travel/TravelProcedure.scala`, `recover/RecoverProcedure.scala`, `forge/ForgeProcedure.scala`, `src/main/scala/oathdigital/gameplay/phases/rest/FinishRestProcedure.scala`, `src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala`, `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`. Test `src/test/scala/oathdigital/gameplay/CoreOperationsSuite.scala`, `OperationValidatorSuite.scala`, `SupplyAdjustSuite.scala`, `TravelProcedureSuite.scala`, `TravelSitePowersSuite.scala`, `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`; update remaining `AdjustSupply` test references found by the search above.

**Interfaces:** `CoreOperation.required: Boolean = false`; `GainSupply(player: PlayerId, amount: Int)` (optional); `SpendSupply(player: PlayerId, amount: Int, required: Boolean = true)` (positive amount); `PayCost.required = true`; `Draw.required = true`; `Exchange.required = true`; `Replace.required = false`. Add `required` constructor parameters where a procedure must override the default, including `Discard.Denizen` and chosen placement operations. `required` is not encoded. At the end of this task the pipeline is still strict; behavior changes in Task 3.

- [ ] **Step 1: Write failing tests.** `SpendSupply(actor, 2)` rejects insufficient Supply under the current strict pipeline; `GainSupply(actor, 2)` caps at maximum; `SpendSupply(actor, 1, required = false).required` is false; a `Discard.Denizen` can be constructed with `required = true`; every Supply operation wire round-trips without a `required` key. Add Travel tests proving Mountain increases a positive spend, Island reduces it, and Coast sets it to one. Preserve existing exact event round-trip tests.

```scala
assertEquals(SpendSupply(actor, 1).required, true)
assertEquals(SpendSupply(actor, 1, required = false).required, false)
assertEquals(GainSupply(actor, 1).required, false)
```

- [ ] **Step 2: Run** `./sbtw "testOnly *CoreOperationsSuite *OperationValidatorSuite *TravelProcedureSuite *TravelSitePowersSuite *GameEventWireSuite"`; expect missing-type/field failures.
- [ ] **Step 3: Implement** the declarations and strict mutation/validation equivalents. Replace negative `AdjustSupply(player, -n)` with `SpendSupply(player, n)` and positive values with `GainSupply`. Change Travel transforms from signed adjustments to positive spend math: Mountain `n + 1`, Island `n + 2`, Coast `1`; preserve their window, route, and precedence behavior. Update `ProcedureWalker.deltaMeaning` to match `SpendSupply`. Use new `gain-supply` and `spend-supply` wire kinds and keep `required` absent from JSON. Update all constructor and extractor arities consistently; do not retain an `AdjustSupply` alias.

```scala
case Vector(SpendSupply(player, amount, _)) =>
  SupplySpent(player, amount)
```

- [ ] **Step 4: Run** focused tests, full backend tests, architecture check, and `git diff --check`. Confirm `rg -n 'AdjustSupply' src/main/scala src/test/scala` has no matches.
- [ ] **Step 5: Commit** `refactor(operations): split supply gain and spend with required defaults`.

### Task 2: Typed impossibility and maximal-count resolver

**Files:** Modify `src/main/scala/oathdigital/gameplay/operations/OperationValidator.scala`, `OperationError.scala` if a typed error is needed. Create `src/main/scala/oathdigital/gameplay/operations/OperationResolution.scala` and `DiscardRestrictions.scala`. Test `src/test/scala/oathdigital/gameplay/OperationValidatorSuite.scala`, new `src/test/scala/oathdigital/gameplay/OperationResolutionSuite.scala`, `src/test/scala/oathdigital/gameplay/OperationExecutorSuite.scala`.

**Interfaces:** `OperationReason(code: String, detail: String, kind: OperationReasonKind)` with `Impossible` and `Invalid`; retain existing `code`/`detail`. `OperationResolution.resolve(ready, requested, validator): Either[OathViolation, OperationResolution.Result]`, where `Result` is `Execute(actual: CoreOperation)` or `Skip(reasons: Vector[OperationReason])`. `DiscardRestrictions(catalog)` implements `OperationRestriction` without naming a power. Resolution is pure; it never mutates state or runs the raw executor.

- [ ] **Step 1: Write failing tests.** Zero/short favor bank gives typed `Impossible` for a valid counted source; missing card and wrong stack position give `Invalid`; allowlist denial gives `Invalid`; two simultaneous reasons with any `Invalid` reject. Optional `Gain.Favor(7)` with five in bank resolves to `Gain.Favor(5)`; zero resolves to `Skip`. `GainSupply` at a nearly full track records only the actual increase; at maximum it skips. Optional `SpendSupply(3, required = false)` with one Supply resolves to one; required spend rejects. `Replace(3,3)` with only one available replacement resolves to `(1,1)`, never `(3,1)`. Test optional counted `Move`, `Take`, `Give`, `Burn`, `Kill`, `Sacrifice`, `FlipSecrets`, and dice-pool adjustment at their zero/short boundaries. `PayCost` never reduces; `Draw` and `Exchange` do not split. A stale `Discard.Denizen` favor/secrets description is invalid, not a partial discard: those fields describe resources on the card, not an amount the effect asks to take. Hall of Ministers (`edifice.e16.intact`) blocks an enemy's site-card discard while a separate legal denizen discard remains possible; use its catalog handler rather than hardcoding Dazzle.

```scala
val resolved = OperationResolution.resolve(shortBank,
  Gain.Favor(actor, Suit.Order, 7), validator)
assertEquals(resolved, Right(OperationResolution.Execute(
  Gain.Favor(actor, Suit.Order, 5))))
```

- [ ] **Step 2: Run** `./sbtw "testOnly *OperationValidatorSuite *OperationResolutionSuite *OperationExecutorSuite"`; expect missing resolver/classification failures.
- [ ] **Step 3: Implement** `OperationReasonKind` and wire `restrictions` into both `validateOne` and diagnostic `validateBatch`. Classify insufficient *counted* source as `Impossible` while a card missing from its named source stays `Invalid`; classify discard immunity as `Impossible`. Keep allowlist and structural precedence stable. Resolve optional counts from staged state into one maximal operation, preserving destinations, order, and linked invariants. For a zero feasible count, return `Skip`, not an invalid zero-amount operation. Revalidate every reduced operation. Required operations pass unchanged through validation and reject any reason. Use semantic root normalization, not independent mutation of a composite's flattened children. `DiscardRestrictions` uses the catalog's `edifice.e16.intact` handler, current site ruler, and actor-enemy relation to report immunity for site-card discards; never key off Dazzle. Keep `OperationShape.validateBatch` diagnostic against initial state only.

```scala
if (requested.required) validateExact(requested)
else maximalLegal(requested).flatMap {
  case None => Right(Skip(impossibilityReasons))
  case Some(actual) => validateExact(actual).map(_ => Execute(actual))
}
```

- [ ] **Step 4: Run** focused tests, full backend tests, architecture check, and `git diff --check`.
- [ ] **Step 5: Commit** `feat(operations): classify impossibility and resolve partial effects`.

### Task 3: One breaking staged pipeline and caller cutover

**Files:** Modify `src/main/scala/oathdigital/gameplay/operations/OperationPipeline.scala`; update production callers in `src/main/scala/oathdigital/gameplay/actions/{Search,CardPlay,MinorActions,Visions,Campaign,Challenge,Economy,Negotiation}.scala`, `src/main/scala/oathdigital/gameplay/StateBasedEvaluation.scala`, `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`, and the exact call sites found by `rg`. Test new `src/test/scala/oathdigital/gameplay/OperationPipelineSuite.scala` plus existing action, operation, and walker suites. Update test helper `src/test/scala/oathdigital/gameplay/WalkerRecordedOpsReducer.scala`.

**Interfaces:** `OperationRun(state: ReadyGame, executed: Vector[CoreOperation], skipped: Vector[SkippedOperation])`; `SkippedOperation(requested: CoreOperation, reasons: Vector[OperationReason])`. `OperationPipeline.run(...) (update)` returns `Either[OathViolation, OperationRun]` and is the only pipeline path. It validates/resolves/executes each operation against staged state and runs `update` and post-state invariant once. The result's `executed` operations carry actual counts, with `required` reset to each concrete type's default for recording.

- [ ] **Step 1: Write failing tests.** A batch with optional short gain followed by a required spend uses the post-gain state. A batch with an optional immune discard then a legal discard succeeds and returns only the latter in `executed`. A nonempty all-skipped batch succeeds with empty `executed`; an empty input rejects. Any `Invalid`, required impossibility, raw executor error, update failure, or post-state failure rejects the entire batch. A legacy event claiming more favor than the bank can provide remains a typed mismatch rather than replaying a reduced outcome. Encode/decode equality holds for a recorded optional `SpendSupply` that executed at a reduced amount after canonicalization.

```scala
val run = OperationPipeline.run(ready,
  Vector(SpendSupply(enemy, 3, required = false)),
  OperationPolicy.Permissive)(Right(_)).toOption.get
assertEquals(run.executed, Vector(SpendSupply(enemy, 1)))
```

- [ ] **Step 2: Run** `./sbtw "testOnly *OperationPipelineSuite *OperationExecutorSuite *ProcedureWalkerSuite *GameEventWireSuite"`; expect signature/behavior failures.
- [ ] **Step 3: Implement** one staged fold using Task 2's resolver and raw `OperationExecutor`. Canonicalize executed operations after successful execution, before returning them; do not alter requested operations before allowlist validation. Return skipped reasons to the caller only. Adapt every `OperationPipeline.run` call site to read `.state` and, where a journal/event is produced, `.executed`; update expected-result tests and helper reducers. Legacy event reducers must compare canonical requested effects with `executed` before accepting a recorded outcome; rejecting a mismatch preserves their existing replay-integrity checks. Keep any action-specific “accepted choice must happen” requirement explicit on the operation rather than restoring global strictness. Do not add `runStrict`, `runAttempts`, or `BuildAttempts`.

```scala
OperationPipeline.run(ready, operations, policy)(Right(_))
  .map(_.state)
```

- [ ] **Step 4: Run** focused suites, `./sbtw "test"`, architecture check, and `git diff --check`. Search all production call sites again and review each for requiredness and event intent.
- [ ] **Step 5: Commit** `feat(operations): run staged best-effort batches globally`.

### Task 4: Walker restriction delivery and recording proof

**Files:** Modify `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`, `src/main/scala/oathdigital/gameplay/operations/CoreOperations.scala`. Test `src/test/scala/oathdigital/gameplay/ProcedureWalkerSuite.scala`, `WalkerReplayDriftSuite.scala`, `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`.

**Interfaces:** Extend `BuildOps` with `restrictions: (ReadyGame, PendingTree) => Vector[OperationRestriction] = (_, _) => Vector.empty`; the walker evaluates it at walk time and passes the result to `OperationPipeline.run`. `recordBatch` uses `OperationRun.executed` for `WalkerStepRecorded.ops` and `deltaMeaning`; when `executed` is empty, it writes no delta step. `WalkerReplay` remains a raw `OperationExecutor.executeAll` of recorded ops and receives no restrictions.

- [ ] **Step 1: Write failing tests.** A synthetic `BuildOps` with an immune and a legal discard records only the legal discard; replay from the same initial state reaches the same result without a catalog restriction. A `BuildOps` with only immune discards records no delta step and the walker still finishes. A required blocked discard rejects and records nothing. A short optional counted effect records its reduced count, and event encode/decode equality holds. An ordinary `BuildOps` with no restriction argument behaves as before.

```scala
assertEquals(recorded.flatMap(_.ops), Vector(legalDiscard))
assertEquals(ProcedureWalker.applyRecorded(initial, recorded.head),
  Right(expectedState))
```

- [ ] **Step 2: Run** `./sbtw "testOnly *ProcedureWalkerSuite *WalkerReplayDriftSuite *GameEventWireSuite"`; expect recording/restriction failures.
- [ ] **Step 3: Implement** contextual restriction delivery at the existing `BuildOps`/walker-to-pipeline seam without a new wrapper node or power-specific pipeline branch. Preserve the no-op behavior of an empty `BuildOps` result. Record canonical executed operations only; do not append an empty `DeltaRecorded` event. Confirm `deltaMeaning` reads actual executed operations. Leave `WalkerReplay` free of restriction and resolver calls.

```scala
OperationPipeline.run(ctx.state, ops, OperationPolicy.Permissive,
  build.restrictions(ctx.state, tree))(Right(_)).map { result =>
  if (result.executed.isEmpty) ctx.copy(state = result.state)
  else recordExecuted(result, ctx)
}
```

- [ ] **Step 4: Run** focused suites, `./sbtw "test"`, architecture check, and `git diff --check`. Confirm Search's plan can now use plain `BuildOps` plus a reusable discard restriction without any `OperationAttempt` type.
- [ ] **Step 5: Commit** `feat(walker): record only executed best-effort operations`.
