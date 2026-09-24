> **OUTDATED — engine redesign superseded.** The forward architecture is the
> procedure-walker design (`docs/superpowers/specs/2026-09-05-procedure-walker-design.md`):
> actions become `Operation` trees, a generic walker executes them, powers are
> contributors (`Transform`/`Restriction`), replay applies recorded ops only.
> This file is a historical record of the pre-walker design/code. Read the new
> spec before planning new work.

# Engine Redesign Phase 3: Validator / Executor / Pipeline Split

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Three-way separation of concerns in the operation engine: `OperationValidator` (pure checker with whole-batch and per-op surfaces, aggregated reporting), `OperationExecutor` (pure mutation — runs primitives only, no policy/invariant), and `OperationPipeline` (single public orchestrator that **owns the validator**, assembled per run from contextual data). Behavior stays byte-identical for every existing action.

**Architecture:** All pre-execution structural/sufficiency checks move out of `OperationStateMutation` into `OperationValidator`. The pipeline is the only entry point that may execute a batch: empty-batch guard → `validator.validateBatch(initial, whole Vector)` (global/order constraints, aggregated, before anything executes) → staged fold of `validator.validateOne(staged, op)` (trajectory rules) + raw `OperationExecutor.execute` → module `update` → `OperationStateInvariant.validate`. Callers never construct executor/validator values: they pass per-run `allowlist: OperationPolicy` (unchanged `exact`/`Permissive`/contextual carriers) and `restrictions: Vector[OperationRestriction]` (empty in Phase 3; derived per query in Phase 5, matching Decision 1's never-register powers). `OperationTransaction` is deleted.

**Tech Stack:** Scala 2.13, sbt multi-project, munit. Full test command: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`.

**Spec / Decisions (locked with the user):**
1. Pipeline owns the validator; per-run context params (`allowlist`, `restrictions`).
2. Two validator surfaces: `validateBatch` (global/order, aggregated, pre-execution) + per-op staged `validateOne` (trajectory) — preserves today's semantics exactly.
3. Shape partition = FULL: every check in `OperationStateMutation.applyOperation`'s pre-steps moves to the validator with verbatim `OperationError.code`/`detail` strings as `OperationReason`.
4. Allowlist partition = `OperationPolicy` unchanged. Restrictions partition = empty registry + `OperationRestriction` trait + per-run vector (Phase 5 fills).
5. Post-state invariant runs in the pipeline, not the executor.
6. Aggregation only in the validator API; pipeline rejection stays first-fail atomic (no rejection-UX change).
7. Replay, drift-check, recorded events unchanged. Branch `feat/engine-redesign`, base `61218c1`. Gate exit 0; `git diff --check` clean.

## Core API

```scala
final case class OperationReason(code: String, detail: String)

trait OperationRestriction {
  def reason(ready: ReadyGame, operation: CoreOperation): Option[OperationReason]
}

final class OperationValidator(allowlist: OperationPolicy,
    restrictions: Vector[OperationRestriction]) {
  def validateBatch(ready: ReadyGame,
      operations: Vector[CoreOperation]): Vector[OperationReason]
  def validateOne(ready: ReadyGame, operation: CoreOperation): Vector[OperationReason]
}

object OperationPipeline {
  def run(ready: ReadyGame, operations: Vector[CoreOperation],
      allowlist: OperationPolicy,
      restrictions: Vector[OperationRestriction] = Vector.empty)(
      update: ReadyGame => Either[OathViolation, ReadyGame]
  ): Either[OathViolation, ReadyGame]
  def report(ready: ReadyGame, operations: Vector[CoreOperation],
      allowlist: OperationPolicy,
      restrictions: Vector[OperationRestriction] = Vector.empty)
      : Vector[OperationReason]
}

final class OperationExecutor {
  def execute(ready: ReadyGame, operation: CoreOperation): Either[OperationError, ReadyGame]
  def executeAll(ready: ReadyGame, operations: Vector[CoreOperation]): Either[OperationError, ReadyGame]
}
```

`run` ordering (mirrors old `OperationTransaction.evolve`): `operations.isEmpty` → `EmptyOperationBatch`; `validateBatch` pre-flight (first reason rejected); `cardIds` snapshot; fold: `validateOne(staged, op).headOption` → reject else raw execute; after all ops: `describe(update(staged))`; final `OperationStateInvariant.validate(updated, expected)`. Rejection = `OathViolation.CoreOperationRejected(reason.code, reason.detail)`.

## Task 1: Extract the validator shape partition

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/operations/OperationValidator.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationStateMutation.scala`
- Test: `src/test/scala/oathdigital/gameplay/OperationValidatorSuite.scala` (new)

Move from `OperationStateMutation` into `OperationValidator.validateOne` (aggregated per operation): position/stack-convention checks (`validatePrimitivePositions`/`validatePositions`/source-destination conventions), card-source checks (duplicate same-card moves, per-location source presence, stack source order, destination legality incl. stateful materialization/orientation), counted-source sufficiency (`validateFavorSources`, `validateWarbandSources`), secret-planner feasibility, pawn/banner move preconditions, non-move guards (FlipSecrets availability, Flip/Peek legality, `AdjustSupply` bounds). Each produces `OperationReason` whose `code`/`detail` equal the old `OperationError`'s verbatim. Mutation keeps only the mutation sequence (internal guards remain as unreachable defensive `Left`s, documented).

`validateBatch` Phase-3 content: whole-batch static aggregation over the initial state (duplicate card across ops, conflicting same-location deltas, empty batch) + the restriction hook (empty until Phase 5).

TDD: `OperationValidatorSuite` asserts the same typed rejections the executor produced before (short favor bank `insufficient-pieces`, `invalid-stack-position`, `conflicting-deltas`, `unsupported-orientation`, `unknown-warband-supply`) now as `Vector[OperationReason]`, plus a multi-reason batch case. Gate: gameplay suites green after each slice.

Commit: `refactor(operations): extract OperationValidator shape partition`.

## Task 2: Slim executor + OperationPipeline + delete OperationTransaction

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationExecutor.scala`
- Create: `src/main/scala/oathdigital/gameplay/operations/OperationPipeline.scala`
- Delete: `OperationTransaction` (locate: it lives inside `OperationExecutor.scala` today — the object moves to the pipeline file or is deleted; sites migrate)

Executor constructor becomes parameterless and holds only raw mutation (`execute` = describe-guarded `OperationStateAdapter.applyOperation`; `executeAll` raw fold; empty → `EmptyOperationBatch`). `OperationPolicy`/`Permissive`/`exact`/`all` stay in the executor file.

Compile-driven site migration — every `OperationTransaction.evolve(ready, ops, executor)(update)` and every `new OperationExecutor(<policy>)` becomes `OperationPipeline.run(ready, ops, <allowlist-policy>)(update)` (executor construction disappears; contextual policy objects become allowlists). Raw mutation-only consumers (shadow evolution etc.) use `new OperationExecutor()`.

Commit: `refactor(operations): OperationPipeline orchestrates validator and raw executor`.

## Task 3: Test migration

- `OperationExecutorSuite`: validation-behavior tests → `OperationPipeline.run`/`OperationValidator`; pure-mutation tests → `new OperationExecutor`; preserve every assertion (EmptyOperationBatch, typed rejections, staged ordering, invariant corruption).
- `OperationValidatorSuite` aggregate cases.
- PowerOperationsSuite / SupplyAdjustSuite / action suites: mechanical swap; assertions unchanged.

Commit: `test(operations): validator/pipeline split suites`.

## Task 4: Docs + spec
Program doc (Decision 3/4 rewrite, Roadmap Phase-3 bullet), `docs/ROADMAP.md` Phase-3 entry with commit hashes, `docs/architecture/core-operations-migration.md` three-way split paragraph. This file is the phase record.

Commit: `docs: phase 3 validator/executor/pipeline split`.

## Task 5: Verification
Full gate exit 0; `git diff --check`; grep zero `new OperationExecutor(` with a policy argument in `src/main`; grep zero `OperationTransaction`; live smoke (restart server, 168-event replay `200 ready:true`, fresh bootstrap + placePawn append).

## Success criteria
Executor = mutation only. Validator owns every pre-execution check (batch + per-op surfaces, aggregated). Pipeline is the sole orchestrator and owns validator construction from per-run allowlist/restrictions. Every end-state byte-identical; rejection texts unchanged; gates + smoke green.

## Risks
- Task 1 extraction is delicate (large mutation object). Mitigation: keep defensive guards, verbatim code/detail strings, gate every slice on the full gameplay suite.
- Aggregation is API-only (decision 6); staged trajectory semantics live in the fold (decision 7) — no contextual drift.

## Addendum (post-execution)
- Shipped commits: `9fe5bba` (shape extraction into `OperationShape.validate`/`validateBatch`/`first` + `OperationReason`) and `d9c410c` (slim executor, `OperationPipeline.run`/`report`, `OperationTransaction` deleted, 19 production + test call sites migrated).
- **Executed nuance:** `OperationPipeline.run` rejects from the *staged per-op* `validateOne` inside the fold, not from a whole-batch pre-flight — Campaign's conquest batch is trajectory-dependent (an in-batch `Kill` replenishes a bank a later `ReturnToBoard` move draws from), so an initial-state batch pre-flight over-rejected. `validateBatch`/`report` keep the whole-batch aggregated surface (global/order restriction hooks arrive Phase 5); rejection ordering and code/detail strings are byte-identical to the retired path.
- **Post-review completion of the extraction:** the mutation object is now genuinely validation-free — `OperationStateMutation.applyOperation` is pure mutation (only mutation-time guards remain); the `OperationShape` gate and the original validation chain were removed from it. `OperationValidator.validateOne` reports **allowlist reasons first** (mirroring the retired executor's policy-before-shape precedence, so a both-fail operation rejects `restricted-operation`). Raw `OperationExecutor.execute` is mutation only; the describe guard covers constructor failures only. Parity is machine-checked: `OperationValidatorSuite` asserts pipeline rejection == first shape reason == raw-executor outcome over a corpus, plus allowlist-precedence, registry-inert, and `report` aggregation cases.
- Shipped commits: `9fe5bba` (shape extraction), `d9c410c` (pipeline + migration), `f8fedfa` (review fixes: pure mutation, precedence, parity tests).
