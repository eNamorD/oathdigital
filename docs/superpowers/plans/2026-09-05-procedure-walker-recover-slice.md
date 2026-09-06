# Vertical Slice: Recover on the Procedure Walker

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the first action (Recover) on the new procedure-walker engine end to end — Operation tree, generic walker, PendingTree state, J1 recorded-ops events, generic command surface — while every other action keeps working on the legacy evolve path.

**Architecture:** Introduce a single `Operation` ADT (`children: Vector[Operation]`) that subsumes today's `CoreOperation`/`PrimitiveOperation` (composites may nest; primitives are leaves). A pure `ProcedureWalker` derives an action tree per command, applies power contributions later (not in this slice), executes delta leaves through the existing executor, parks at `Decide`/`Roll`, and records one semantic node event carrying the applied ops. Replay branches: legacy events still go through `OathRules.evolve`; walker events go through recorded-ops application.

**Tech Stack:** Scala 2.13, sbt multi-project (root engine + frontend Scala.js), munit. Full gate: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`.

**Spec:** `docs/superpowers/specs/2026-09-05-procedure-walker-design.md` (approved). This plan implements the "vertical slice first" migration step with Recover as the slice; all non-slice actions keep their legacy paths until batch migration.

## Global Constraints

- Do not break any other action. Full gate green at every commit (root + frontend).
- Power contributions, ignore, and restrictions are NOT wired in this slice (Phase B of the spec); powers keep their existing seams until then. The walker accepts a no-op power collector now.
- Engine never calls random ports; dice faces ride commands.
- Replay must never re-run the walker for walker events — apply recorded ops only (J1). Drift checks are dev/test-only, not in the production replay path.
- No new serialization format for legacy events. Walker events get a new `OathEvent` family + codec case.
- `usedPowers` tracking stays state (spec decision 11 of the old program, restated in the spec).
- Commit per task with the exact message shown. Work on branch `feat/procedure-walker`.
- Per-task gate: root suite `./sbtw "test"` green. Frontend gate (`frontend/test`, `frontend/fastLinkJS`) runs at the Task 8 checkpoint only — the Scala.js frontend source set excludes `gameplay/operations`, so per-task frontend runs add cost without signal.
- All new leaf/composite `Operation` cases (walker leaves in Task 2, `Repeat`) are declared inside `src/main/scala/oathdigital/gameplay/operations/CoreOperations.scala`: `CoreOperation`/`PrimitiveOperation` are sealed and Scala 2.13 requires sealed subclasses in the same source file. (Task 1 review finding.)

---

### Task 1: Operation ADT core + tree math

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/operations/Operation.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/CoreOperations.scala` (retarget hierarchy)

**Interfaces:**
- Consumes: existing `PrimitiveOperation` case classes in `CoreOperations.scala` (their bodies unchanged).
- Produces:
  - `sealed trait Operation { def window: Option[PowerWindow]; def children: Vector[Operation] }`
  - `object Operation { def flatten(operation: Operation): Vector[Operation] }` — depth-first leaf sequence.
  - `sealed trait PrimitiveOperation extends Operation` with `final children = Vector(this)`.
  - `sealed trait CoreOperation extends Operation` — composites; existing cases override `children` with the same vectors they previously exposed as `primitives` (now widened to `Vector[Operation]`).
  - Remove the `primitives` member from `CoreOperation`; migrate every reader to `Operation.flatten`.

Note: `CoreOperation` today lives in `CoreOperations.scala` with `def primitives`. This task re-roots it under `Operation` and rewires the ~12 composite case classes (`Draw`, `Discard.*`, `Exchange`, `Gain.*`, `Give`, `PayCost`, `Kill`, `Play`, `Replace`, `Reveal`, `Sacrifice`, `Swap`, `Take`, `Burn.*`) so their `children` equal their old `primitives` vectors. Primitive cases (`Move`, `Peek`, `Flip`, `FlipSecrets`, `AdjustSupply`, `Bury`) extend `PrimitiveOperation` unchanged.

- [ ] **Step 1: Write the failing test** — new `OperationTreeSuite.scala` (package `oathdigital.gameplay.operations`):

```scala
class OperationTreeSuite extends munit.FunSuite {
  test("primitives are leaves") {
    val move = Move(Piece.Pawn(PlayerId("p")),
      PositionedLocation(Location.Site(SiteId("a"))),
      PositionedLocation(Location.Site(SiteId("b"))))
    assertEquals(Operation.flatten(move), Vector(move))
  }
  test("composites flatten depth-first") {
    val pay = PayCost(PlayerId("p"), Location.PlayArea(PlayerId("p")),
      Cost(secret = 1))
    assertEquals(Operation.flatten(pay).size, 2) // favor? none; secret move only
  }
}
```

Verify compile fails: `Operation` undefined.

- [ ] **Step 2: Run to confirm fail**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.OperationTreeSuite"` — FAIL (missing `Operation`).

- [ ] **Step 3: Create `Operation.scala`**

```scala
package oathdigital.gameplay.operations

import oathdigital.gameplay.powerresolver.PowerWindow

sealed trait Operation {
  def window: Option[PowerWindow] = None
  def children: Vector[Operation]
}

object Operation {
  def flatten(operation: Operation): Vector[Operation] =
    operation.children.flatMap(child =>
      if (child.children.isEmpty) Vector(child) else flatten(child))
}
```

- [ ] **Step 4: Retarget hierarchy in `CoreOperations.scala`**

Change `sealed trait CoreOperation extends Product with Serializable` to extend `Operation`; replace `def primitives: Vector[PrimitiveOperation]` with `def children: Vector[Operation]` and update every composite override to `override val children: Vector[Operation] = ...` using its former primitives expression. `sealed trait PrimitiveOperation extends CoreOperation` becomes `extends Operation` with `final override val children: Vector[Operation] = Vector(this)`. Fix imports as needed.

- [ ] **Step 5: Fix every reader of `primitives`**

Run `./sbtw "Test/compile"` and change call sites to `Operation.flatten(...)` where they needed leaves. Expected readers: `OperationStateMutation`, `OperationShape`, tests. Compile clean.

- [ ] **Step 6: Test green + full gate**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.OperationTreeSuite"` then full gate. Both green.

- [ ] **Step 7: Commit**

```bash
git add -A src/main src/test
git commit -m "refactor(operations): unify Operation ADT with children accessor"
```

### Task 2: Walker leaves (Decide/Roll/pool ops) + PendingTree state

**Files:**
- Modify: `src/main/scala/oathdigital/model/GameState.scala` (`CurrentGameState`, add pool state)
- Create: `src/main/scala/oathdigital/model/PendingTree.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/CoreOperations.scala` — new leaf cases go HERE (sealed `PrimitiveOperation` cannot be extended from another file on Scala 2.13; plan constraint). Do NOT create a separate walker operations file.

**Interfaces:**
- Consumes: Task 1 `Operation`; existing `OathState`/`ReadyGame`; `PlayerId`, `SiteId`, `PowerId`, `PowerWindow`.
- Produces:
  - `sealed trait WalkerLeaf extends PrimitiveOperation`? — no: new leaf cases in the existing ADT:
    - `final case class ModifyDicePool(pool: PoolKey, delta: Int) extends PrimitiveOperation`
    - `final case class Roll(pool: PoolKey, dice: DiceSpec) extends PrimitiveOperation` with `window` override
    - `final case class ModifyRollOutcome(pool: PoolKey, skulls: Option[Int], score: Option[Int]) extends PrimitiveOperation`
    - `final case class ClearDicePool(pool: PoolKey) extends PrimitiveOperation`
    - `final case class Decide(payload: DecisionPayload, owner: OwnerQuery, decisionId: String) extends PrimitiveOperation`
  - Composite: `final case class Repeat(guard: (ReadyGame, PendingTree) => Boolean, body: Operation) extends CoreOperation { val children = Vector(body) }`.
  - `final case class PoolKey(value: String)` (e.g. `"recover"`, `"campaign.attack"`).
  - `final case class DiceSpec(die: DiceKind)` where `sealed trait DiceKind { case object Defense, Attack }` — new file `src/main/scala/oathdigital/gameplay/DiceSpec.scala`.
  - `sealed trait DecisionPayload extends Product with Serializable` (open per D2; empty now).
  - `sealed trait OwnerQuery { def owner(ctx: WalkerCtx): Option[PlayerId] }`.
  - `PendingTree(at: Vector[String], answered: Vector[String], actor: PlayerId, action: Operation, ctx: WalkerCtx)` with `WalkerCtx` minimal case class holding `ready: ReadyGame` + node-local scratch `Map[String, Any]`.
  - State: `CurrentGameState.rollPools: Map[PoolKey, DicePoolState]` and `rollOutcomes: Map[PoolKey, RollOutcome]` replaced `pending` additions.

Because `PendingTree` is stored in `CurrentGameState`, keep `PendingProcedure` alongside for legacy actions this slice (dual pending: `pending: Option[PendingProcedure]`, `walkerPending: Option[PendingTree]`). Do not delete legacy `pending`.

- [ ] **Step 1: Failing test** in `WalkerStateSuite` (package `oathdigital.gameplay`): constructing a `ReadyGame` with a `PendingTree` + a pool slot compiles and reads back; `Operation.flatten(Decide(...))` returns itself; `Repeat(guard = _ => false, body = ...)` flattens to `body`'s leaves (guard false → body still flattened by the accessor, unrolling is walker's job).
- [ ] **Step 2: fail → Step 3: implement** new model types + leaves (leaf `children = Vector(this)`).
- [ ] **Step 4: pass → Step 5: commit** `feat(walker): Decide/Roll/pool leaves and PendingTree state`.

### Task 3: ProcedureWalker core (auto-walk, park at Decide/Roll)

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`
- Create: `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala`
- Modify: `src/main/scala/oathdigital/gameplay/model/GameEventProtocol.scala` (add walker event family)

**Interfaces:**
- Consumes: Task 1 `Operation.flatten`; Task 2 leaves + state; existing `OperationPipeline`, `OperationExecutor`, `OperationPolicy`, `OathLifecycle.validateAct`.
- Produces:
  - `sealed trait WalkerOutcome` — `Parked(tree: PendingTree, events: Vector[OathEvent])`, `Finished(treeless: ReadyGame, events: Vector[OathEvent])`.
  - `object ProcedureWalker {
       def advance(state: ReadyGame, tree: PendingTree): Either[OathViolation, WalkerOutcome]
     }`
  - `sealed trait WalkerEvent extends OathEvent` — concrete per node later; this task defines the container: `final case class WalkerStepRecorded(actor: PlayerId, nodeId: String, payload: WalkerStepPayload, ops: Vector[CoreOperation]) extends OathEvent` where `sealed trait WalkerStepPayload` (open).
- Behavior: `advance` walks from `tree.at`:
  1. `Decide` node → return `Parked` with no new events.
  2. `Roll` node → return `Parked` (engine never rolls; app layer supplies faces via a later command).
  3. `Repeat(guard, body)` composite → evaluate `guard(state, tree)`; if true, walk `body` (recording its events), then re-check guard; if false, continue past the Repeat. If `body` parks (Decide/Roll), the Repeat's position + iteration count live in `tree.at`/`tree.ctx` so the resumed walk re-enters the loop at the same body point. An iteration's events are recorded once per pass (replay never re-guards).
  4. Other leaves → validate + execute in order via `OperationPipeline` with `OperationPolicy.Permissive`; append one `WalkerStepRecorded` per delta leaf.
  5. Composites recurse into children.
  6. Tree exhausted → `Finished` (clear pools in state).
  The walker does not consult powers yet (no-op collector).
  For this slice, treat each delta leaf as a "node" carrying `window=None` and `nodeId` derived from its `toString` index path; records ops applied for that leaf.

- [ ] **Step 1: Failing test** (`ProcedureWalkerSuite`): a `Sequence(Delta(Move...), Delta(AdjustSupply...))` tree walks to completion, returns `Finished`, state has pawn moved and supply spent, events count = 2, each event records the ops that produced the delta.
- [ ] **Step 2: fail → Step 3: implement** `ProcedureWalker`.
- [ ] **Step 4: pass → Step 5: commit** `feat(walker): auto-walk delta leaves with recorded-ops events`.

### Task 4: Roll node flow — faces ride command, count from state

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerEvents.scala` (Roll payload)
- Test: `ProcedureWalkerSuite`

**Interfaces:**
- Consumes: Task 2 `Roll`/`ModifyDicePool`/`RollOutcome`; existing die types (`DefenseDieFace`, `AttackDieFace`).
- Produces:
  - Walker `Roll` parking response carries `PoolKey`, required `count`.
  - `object ProcedureWalker { def roll(state, tree, faces): Either[OathViolation, WalkerOutcome] }` — validates `faces.size == count`, writes `RollOutcome`, appends `WalkerStepRecorded(..., RollPayload(pool, faces), ops = Vector.empty)`.
  - App-layer pre-roll: an application method `prepareRoll(pool, count)` calls the port; wiring to command surface comes in Task 6.
- [ ] **Step 1: failing test**: tree `Sequence(ModifyDicePool(recover, +2), Roll(recover, Defense))`; walker parks at Roll with count 2; `roll(state, tree, faces=2)` writes outcome with score from `DefenseDieFace.score`; wrong count rejected.
- [ ] **Step 2-5: TDD implement + commit** `feat(walker): Roll parks, faces validated, outcome recorded`.

### Task 5: Recover tree declaration (no powers yet)

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala`
- Test: `RecoverProcedureSuite`

**Interfaces:**
- Consumes: `RecoverRules` (keep module: `difficulty`, `score`, `validate`, `validatePotential`); `Operation.flatten`; `Decide`/`Roll`/`Repeat`/deltas; `SupplyTrack`; `PendingTree`; relic deck draw via port at command boundary (draws ride commands — not in this task; use existing `relicDrawPort` pattern in app layer Task 6).
- Produces: `object RecoverProcedure { def build(ctx: WalkerCtx): Either[OathViolation, Operation] }` returning the declared tree.

```
Recover =
  Repeat(
    guard = not (cumulativeScore >= difficulty) && not stopped,
    body = Sequence(
      Roll(pool = "recover", dice = DefenseDieSpec),     // pay 1 supply: 2 defense dice
      Delta(AdjustSupply(actor, -1)),                     // supply debit per roll
      Decide(payload = RecoverDecision.continueOrStop,
             owner = Active, decisionId = "recover.choice")
    )
  )
  then, on success, Decide(payload = RecoverDecision.takeRelic, ...)  // relic choice + facedown move
```

Loop semantics reproduced from the legacy `Recover.handle`/`Recover.evolve` flow:
cumulative `RecoverRules.score` over all recorded rolls must reach
`RecoverRules.difficulty`; each roll costs 1 supply (recorded in the event's
ops via `AdjustSupply`); a failed roll parks the `continueOrStop` decision;
`stop` ends the loop without success; `takeRelic` (success only) moves the
chosen facedown site relic to the actor's play area. Legacy `Recover.handle`
and `Recover.evolve` stay in place; the walker path runs in parallel for this
action behind an application flag (Task 6). Replay never re-guards the loop:
recorded ops per iteration are applied; the guard runs only at command time
during `advance`.

- [ ] **Step 1: failing test**: Recover success: roll once ≥ difficulty → decision `takeRelic`; choose relic → tree ends, relic moved facedown to play area, 1 supply spent, state otherwise unchanged; events recorded carry ops.
- [ ] **Step 2-5: TDD implement; commit** `feat(recover): declared Recover procedure tree on walker`.

### Task 6: Command surface + application wiring (Recover slice)

**Files:**
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala`
- Modify: `src/main/scala/oathdigital/application/GameCommands.scala`
- Modify: `src/main/scala/oathdigital/application/GameEventCodec.scala` + serialization codecs for walker events
- Test: application suite slice

**Interfaces:**
- Consumes: Task 4/5; existing ports `defenseDicePort`, `relicDrawPort`; `OathRules`.
- Produces (three generic commands wired for Recover only in this slice):
  - `GameCommand.StartWalker(action: ActionRef, start: StartPayload)` — begins Recover: validate act, derive tree, walk, park or finish.
  - `GameCommand.ResolveWalker(treeDecision: TreeDecision)` — answer parked Decide.
  - `GameCommand.RollWalker(pool: PoolKey, faces: Vector[DiceFace])` — app pre-rolls count via port.
  - `ActionRef` = `case object Recover` initially.
- App flow: on `Roll` park, app calls `defenseDicePort.rollTwo()`-style count derived from state pool; faces ride `RollWalker`. On relic take, `relicDrawPort` semantics preserved by having the Draw happen inside the delta node builder from a relic id that rode the command (relic draw in this engine is deterministic given the port call at app boundary — keep the existing app-layer prepare pattern).
- Encode walker events in `GameEventCodec`; decode on replay and route: `OathRules.evolve` gains `case event: WalkerStepRecorded => ProcedureWalker.applyRecorded(state, event)` = validate node id matches derived tree position then apply recorded ops. Replay never calls `advance`.
- Add drift assertion in test only: walker-derived tree ops == recorded ops.

- [ ] **Step 1: failing test** in `GameApplicationService` style: HTTP-less direct service call — start Recover via `StartWalker(Recover, ...)`, roll, take relic; assert stream appends walker events and state matches legacy-path expectations on identical inputs. Replay the stream through `reconstruct` and assert state equality.
- [ ] **Step 2-5: TDD implement; commit** `feat(service): walker command surface for Recover with recorded-ops replay`.

### Task 7: Projection + frontend minimal surface (Recover slice)

**Files:**
- Modify: `src/main/scala/oathdigital/application/GamePresentationProjector.scala` / legal projector (parked decision → legal controls)
- Modify frontend: `ActionDecisionRenderer`/`ServerModeUi` (render parked walker decision for Recover)
- Test: frontend suite slice

**Interfaces:**
- Consumes: Task 6 outcomes (parked decision options + preview).
- Produces: frontend sends `StartWalker/ResolveWalker/RollWalker` for Recover; renders walker park prompts (roll indicator, continue/stop/take-relic) via existing decision DTOs where possible, else a small walker-specific projection.

Because this slice must validate the walker against real UI interaction only if cheap, accept either: (a) full UI wiring for Recover, or (b) UI still uses legacy Recover commands and the walker slice is exercised at service level with a frontend suite asserting DTO shape only. Prefer (b) if (a) balloons; note the choice in the commit.

- [ ] **Step 1: failing test** (frontend or projector): parked Recover roll/decision projects legal controls.
- [ ] **Step 2-5: implement; commit** `feat(ui): walker decision projection for Recover slice`.

### Task 8: Verification checkpoint (slice done)

- [ ] **Step 1: Full gate**: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` exit 0.
- [ ] **Step 2: `git diff --check`** clean.
- [ ] **Step 3: Replay drift test** asserts recorded ops == derived ops for the slice corpus (dev/test only).
- [ ] **Step 4: Grep proof legacy actions untouched**: other actions' suites all pass; no `PrimitiveOperation`-reader regressions (compile).
- [ ] **Step 5: Update docs**: mark Recover migration status in spec or a slice addendum; commit `docs: record Recover walker slice`.

## Out of scope (later slices/plans)

- Power contributions (Transform/Restriction/ignore), power-authored payloads, power windows on nodes, power collector in walker.
- Migrating other actions (Search/Economy/Campaign/Forge/Challenge/Negotiation/Rest/Wake/CardPlay/Visions).
- Deleting legacy `PendingProcedure`/evolve paths (kept until batch migration finishes).
- Player-chosen ordering of simultaneous effects; whole-action restructure hooks; PowerWindow renames.

## Acceptance Criteria

- Recover works identically on legacy and walker paths (same inputs → same resulting state), walker verified via replay-equality tests.
- Walker events carry recorded ops; replay applies them without re-running the walker; other actions unchanged and green.
- New Operation ADT compiles with `Operation.flatten`; no `CoreOperation.primitives` remains.
- Engine never rolls internally.
