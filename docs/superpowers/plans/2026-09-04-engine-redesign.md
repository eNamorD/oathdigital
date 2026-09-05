# Engine Redesign Implementation Plan (Program)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the Oath Digital engine around a single validator + derived power registry so powers are authorable in <20 lines per power with the engine untouched, ending with an MVP power set (2 banners, 30 denizens, ~15 relics) implemented on the new framework.

**Architecture:** One `OperationValidator` (3 rule partitions: generic op-shape, power restriction predicates, per-action policy allowlist) runs on the final operation batch before the `OperationExecutor` applies it; powers are thin objects registered in the static `PowerRegistry` keyed by `PowerWindow`; active-power set is derived per query from board state (no registration events); recorded events stay semantic, and power-path events additionally record their final op batch for replay (no re-derivation of powers at replay). Dice pools stay derived (never state); battle-plan effects stay typed data; prompting is modeled as split powers (phase A opens a decision window without state change, phase B resolves it).

**Tech Stack:** Scala 2.13, sbt multi-project (root engine + `frontend` Scala.js), munit. Full test command: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`.

**Spec:** This document's "Decisions" section is the spec for Phase 1. Later phases each receive their own plan document at phase start (see Roadmap), because their code-level APIs are not yet settled.

## Decisions (locked through design review — spec for this program)

1. **No dynamic registration.** Powers are never "registered when a card is played face-up". Active powers = pure derivation each query from state (`RuleSourceIndex.enumerate` + `ReviewedPowerInspector.accessible` orientation/pawn/owner filters stay).
2. **Two op layers kept.** Powers may interact with `CoreOperation` roots or `PrimitiveOperation` leaves; the executor only ever applies primitives. No collapse. Action-level composite `Operation`s (e.g. Travel; later battle plans) may wrap several `CoreOperation`s so restrictions and validation see the action root rather than only its moves; the executor still applies primitives only. *(Phase 4: wording documented; the hierarchy lands in its own later phase.)*
3. **Executor = authority; validator pre-flights.** Final ops batch validated before execution (`OperationValidator` inside `OperationPipeline`); invalid events never recorded. Resource sufficiency = validator concern in the shape partition (atomic batch all-or-nothing; the raw executor only applies validated batches). Supply-capped gains ("gain as much as the bank holds") clamp at plan time in the effect author (`LimitedResource.clamp`) — there is no per-op best-effort/requireExact executor mode.
4. **Validator = one class, 3 partitions, no per-action validator classes** (scalability guarantee for 100s of powers). Errors: structured reason out of restrictions; all violations reported, not first-fail. Implemented as `OperationValidator` (allowlist `OperationPolicy` partition, shape partition, empty restriction registry) owned per run by `OperationPipeline` (Phase 3).
5. **Restrictions are predicates, not a registry of state.** `(readyGame, operation) => Option[reason]`-style, evaluated fresh; activity + accessibility (site ruler / pawn / face-up) folded into the predicate by reading state. No separate active/accessible pre-pass. Duplicate enforcement (typed blockers like `MinorActionPowerSupport`, `NegotiationPowerSupport`) removed power-by-power as each power lands (Phase 5).
6. **Powers are thin data + lambdas where possible.** Restriction predicate + handler lambdas; typed `PowerContext`/decision-owner stays (it cannot be dropped); `PowerFacts` bundles dropped where facts are re-derivable from `readyGame` + pending record.
7. **Prompts = split powers.** Phase A handler emits a decision-started event + parks pending node, applies NO state change; player sees a read-only preview projection of consequences; phase B handler (on the window opened by A) validates the recorded answer against `readyGame` + pending record and emits ops. Decline = recorded decline event; nothing to roll back.
8. **Effects ≠ ops.** Procedural modifiers (dice-count deltas, skull-ignores, cost deltas, battle-plan effects) are typed facts consumed at the roll/result boundary; ops are state deltas only. Dice are pre-rolled at the command boundary via ports; faces live in events. Dice pools are derived (`force + Σ effects`), never stored. One roll per side, after all pool-affecting plans are final.
9. **Recorded-ops replay (power paths only).** Semantic events keep carrying the game outcome; power-path events additionally record the final validated primitive batch. Replay applies recorded ops; powers/validator do NOT re-run at replay for those paths. Drift-check (`handle == evolve` canonical re-derivation) moves to dev/test assertions for power paths.
10. **Receipts deleted.** `OperationReceipt`/`OperationExecution` have no production consumer; executor returns `ReadyGame` directly (Phase 1).
11. **`RuntimeRuleRegistry`/`RuleResolution` travel path killed** (Phase 4): it is cost-only, has zero `CoreOperation` coupling; terrain migrates onto power windows (`TravelCost`). `TurnState.usedPowers` take-wealth tracking stays outside the framework.
12. **Battle plans migrate onto Power windows** (`CampaignAttackerBattlePlans`/`CampaignDefenderBattlePlans`); `CampaignPlanRegistry`/`CampaignPlanHandlers`/`validateRecorded` overrides deleted; each plan ≤20 lines; effects remain typed data (Phase 7).
13. **MVP power set:** banners Darkest Secret – Wandering Flame + People's Favor – Mob; 30 denizens (5 per suit); ~15 relics. Empire/Citizens-interacting powers out of scope.
14. **Success bar:** adding a power = <20 lines in one file, engine untouched.

## Roadmap (later phases get their own plan doc at phase start)

- **Phase 1 — Receipts removal** (THIS PLAN, fully specified): delete `OperationReceipt`/`OperationExecution`; executor/transaction return `ReadyGame`. Delete-only, compile-driven, full suite stays green.
- **Phase 2 — Cost & supply vocabulary:** `Cost(favor, secret, favorBurnt, secretBurnt)` + `PayCost(player, placedAt, cost)` (zero-cost `Cost.free` allowed) replace the disposition cost machinery across powers and Economy; `AdjustSupply` standardizes supply spending in executor-backed ops (Travel/Search/Economy); procedural supply writes (Challenge/Forge/Recover/Campaign/Rest) stay module-authoritative; plan-time clamps via `LimitedResource`.
- **Phase 3 — Validator/executor/pipeline split:** `OperationValidator` owns all pre-execution shape checks (batch `validateAll` + per-op staged surfaces, aggregated reasons); `OperationExecutor` is raw mutation only; `OperationPipeline` is the sole orchestrator, assembling the validator per run from the action's `OperationPolicy` allowlist + per-query restrictions (empty until Phase 5); `OperationTransaction` deleted.
- **Phase 4 — Terrain migration:** Travel cost via `TravelCost` window handlers; delete `RuntimeRuleRegistry`/`RuleResolution`.
- **Phase 5 — Restrictions framework:** generic restriction predicates replace typed blockers, power-by-power, only for powers actually landing in the MVP set; presence audits stay as safety net for everything else.
- **Phase 6 — Recorded-ops replay:** power-path events record final primitives; drift-check moves to tests.
- **Phase 7 — Battle plans as powers:** plans onto Power windows ≤20 lines; delete plan registry boilerplate; dice pools derived.
- **Phase 8 — MVP power authoring:** 2 banners + 30 denizens + ~15 relics as thin `Power` objects; split-power prompts where needed.

---

# Phase 1: Receipts Removal

**Goal:** Delete `OperationReceipt` and `OperationExecution`, returning `ReadyGame` straight from the executor/transaction, without any behavior change.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationExecutor.scala`
- Modify (call sites — drop `.ready` / `.map(_.ready)` / deref): `actions/CardPlay.scala`, `actions/MinorActions.scala`, `actions/Economy.scala`, `actions/Visions.scala`, `actions/Negotiation.scala`, `actions/Challenge.scala`, `actions/Campaign.scala`, `actions/Search.scala`, `actions/Recover.scala`, `actions/Forge.scala`, `actions/Travel.scala`, `phases/Rest.scala`, `powers/rest/LeagueTreatyPower.scala`
- Test: `src/test/scala/oathdigital/gameplay/OperationExecutorSuite.scala`, `src/test/scala/oathdigital/gameplay/PowerOperationsSuite.scala`

**Interfaces:**
- Before: `OperationExecutor.execute(ready, op): Either[OperationError, OperationExecution]`; `executeAll(...): Either[OperationError, OperationExecution]`; `OperationTransaction.evolve(...)(update): Either[OathViolation, OperationExecution]`; `OperationExecution(ready: ReadyGame, receipts: Vector[OperationReceipt])`; `OperationReceipt(operation: CoreOperation, primitives: Vector[PrimitiveOperation])`.
- After: `execute(...): Either[OperationError, ReadyGame]`; `executeAll(...): Either[OperationError, ReadyGame]`; `evolve(...)(update): Either[OathViolation, ReadyGame]`. `OperationReceipt`, `OperationExecution` no longer exist.

## Task 1: Retype the executor core

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationExecutor.scala:55-119`

**Interfaces:**
- Consumes: nothing new.
- Produces: `execute: Either[OperationError, ReadyGame]`, `executeAll: Either[OperationError, ReadyGame]`, `evolve: Either[OathViolation, ReadyGame]`.

- [ ] **Step 1: Delete the receipt/execution case classes**

In `OperationExecutor.scala`, delete lines 55-63 exactly (the `OperationReceipt` case class and the `OperationExecution` case class, including their `private[gameplay]` constructors and doc-free bodies).

- [ ] **Step 2: Retype `execute` to return the evolved state**

Replace lines 65-81 with:

```scala
final class OperationExecutor(policy: OperationPolicy) {
  def execute(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, ReadyGame] =
    for {
      expected <- OperationStateInvariant.cardIds(ready)
      _ <- OperationStateInvariant.validate(ready, expected)
      _ <- policy.validate(ready, operation)
      evolved <- OperationError
        .describe(OperationStateAdapter.applyOperation(ready, operation))
        .flatMap(identity)
      _ <- OperationStateInvariant.validate(evolved, expected)
    } yield evolved
```

- [ ] **Step 3: Retype `executeAll` — drop the receipts fold**

Replace lines 83-97 with:

```scala
  def executeAll(
      ready: ReadyGame,
      operations: Vector[CoreOperation]
  ): Either[OperationError, ReadyGame] =
    if (operations.isEmpty) Left(OperationError.EmptyOperationBatch)
    else
      operations.foldLeft[Either[OperationError, ReadyGame]](Right(ready)) {
        (result, operation) => result.flatMap(staged => execute(staged, operation))
      }
```

- [ ] **Step 4: Retype `OperationTransaction.evolve`**

Replace lines 100-119 with:

```scala
object OperationTransaction {
  def evolve(
      ready: ReadyGame,
      operations: Vector[CoreOperation],
      executor: OperationExecutor
  )(
      update: ReadyGame => Either[OathViolation, ReadyGame]
  ): Either[OathViolation, ReadyGame] =
    for {
      expected <- OperationStateInvariant.cardIds(ready)
        .left.map(_.toViolation)
      executed <- executor.executeAll(ready, operations)
        .left.map(_.toViolation)
      updated <- OperationError.describe(update(executed))
        .left.map(_.toViolation)
        .flatMap(identity)
      _ <- OperationStateInvariant.validate(updated, expected)
        .left.map(_.toViolation)
    } yield updated
}
```

- [ ] **Step 5: Compile to enumerate every call site that must change**

Run: `./sbtw "Test/compile"`
Expected: FAIL — compile errors at each production consumer of `OperationExecution`/`.ready` and at each test using `.ready` on the old result type. Keep this error list; Tasks 2-4 fix each file.

- [ ] **Step 6: Commit the breaking change deliberately**

```bash
git add src/main/scala/oathdigital/gameplay/operations/OperationExecutor.scala
git commit -m "refactor(operations): executor returns ReadyGame, drop receipts"
```

## Task 2: Fix the `.map(_.ready)` production call sites

**Files:**
- Modify (each drops the trailing `.map(_.ready)` — the value is already `Either[OathViolation, ReadyGame]` now):
  - `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala:340`
  - `src/main/scala/oathdigital/gameplay/actions/MinorActions.scala:229-230`
  - `src/main/scala/oathdigital/gameplay/actions/Economy.scala:254-262`
  - `src/main/scala/oathdigital/gameplay/actions/Visions.scala:252-253`
  - `src/main/scala/oathdigital/gameplay/actions/Negotiation.scala:269-271`
  - `src/main/scala/oathdigital/gameplay/actions/Challenge.scala:314`
  - `src/main/scala/oathdigital/gameplay/actions/Campaign.scala:538`
  - `src/main/scala/oathdigital/gameplay/phases/Rest.scala:242-243`

**Interfaces:**
- Consumes: Task 1 `OperationTransaction.evolve: Either[OathViolation, ReadyGame]`.
- Produces: no change in public action signatures — these helpers already returned `Either[OathViolation, ReadyGame]` after the old `.map(_.ready)`.

- [ ] **Step 1: Remove each trailing `.map(_.ready)`**

For each site above, delete the `.map(_.ready)` suffix. The enclosing expression then has type `Either[OathViolation, ReadyGame]`, which is what each enclosing helper already declared as its return type. Example (CardPlay.scala:339-340):

```scala
    val evolved = if (operations.isEmpty) update(ready)
    else OperationTransaction.evolve(
      ready, operations, executor)(update)
```

and (Rest.scala:242-243):

```scala
    if (operations.isEmpty) update(ready)
    else OperationTransaction.evolve(ready, operations, executor)(update)
```

- [ ] **Step 2: Compile**

Run: `./sbtw "Test/compile"`
Expected: no compile errors from these 8 sites (remaining errors are Task 3/4 files).

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/CardPlay.scala src/main/scala/oathdigital/gameplay/actions/MinorActions.scala src/main/scala/oathdigital/gameplay/actions/Economy.scala src/main/scala/oathdigital/gameplay/actions/Visions.scala src/main/scala/oathdigital/gameplay/actions/Negotiation.scala src/main/scala/oathdigital/gameplay/actions/Challenge.scala src/main/scala/oathdigital/gameplay/actions/Campaign.scala src/main/scala/oathdigital/gameplay/phases/Rest.scala
git commit -m "refactor(actions): drop .map(_.ready) after executor retype"
```

## Task 3: Fix the for-comprehension / explicit-deref call sites

**Files:**
- Modify:
  - `src/main/scala/oathdigital/gameplay/actions/Visions.scala:152-154` (`execution <- ...; } yield Ready(execution.ready)`)
  - `src/main/scala/oathdigital/gameplay/actions/Challenge.scala:257-259` (`evolved <- ...; } yield Ready(evolved.ready)`)
  - `src/main/scala/oathdigital/gameplay/actions/Campaign.scala:442-445` (`.map(execution => Ready(execution.ready))`)
  - `src/main/scala/oathdigital/gameplay/actions/Travel.scala:65-82` (`.map(execution => Ready(execution.ready))`)
  - `src/main/scala/oathdigital/gameplay/actions/Search.scala:99-117` (`execution <- ...; } yield Ready(execution.ready)`)
  - `src/main/scala/oathdigital/gameplay/actions/Recover.scala:70-76` (`execution <- ...; } yield Ready(execution.ready)`)
  - `src/main/scala/oathdigital/gameplay/actions/Recover.scala:255-262` (`.map(execution => Ready(execution.ready))`)
  - `src/main/scala/oathdigital/gameplay/actions/Forge.scala:136-139` (`execution <- ...; } yield Ready(execution.ready)`)
  - `src/main/scala/oathdigital/gameplay/powers/rest/LeagueTreatyPower.scala:237-240` (`execution <- ...; } yield execution.ready`)

**Interfaces:**
- Consumes: Task 1 `OperationTransaction.evolve: Either[OathViolation, ReadyGame]`.
- Produces: unchanged — these code paths produce `Ready(...)` (`OathState`) or `ReadyGame` exactly as before.

- [ ] **Step 1: Rewrite each site**

Pattern A — for-comprehension binding `execution <- OperationTransaction.evolve(...)` then `yield Ready(execution.ready)` / `yield execution.ready`: rename the binding to `evolved` and deref the field off, i.e. `yield Ready(evolved)` (Search.scala, Recover.scala:70-76, Forge.scala, Visions.scala:152-154, Challenge.scala:257-259) or `yield evolved` (LeagueTreatyPower.scala). Example (Forge.scala):

```scala
          evolved <- OperationTransaction.evolve(
            ready, operations, executor)(evolved =>
              Right(updateCurrent(evolved)(_.copy(pending = None))))
        } yield Ready(evolved)
```

Pattern B — chained `.map(execution => Ready(execution.ready))`: becomes `.map(evolved => Ready(evolved))`. Examples (Travel.scala, Campaign.scala:442-445, Recover.scala:255-262):

```scala
          }.map(evolved => Ready(evolved))
```

- [ ] **Step 2: Compile**

Run: `./sbtw "Test/compile"`
Expected: no compile errors from production code; only the two test files (Task 4) still error.

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/Visions.scala src/main/scala/oathdigital/gameplay/actions/Challenge.scala src/main/scala/oathdigital/gameplay/actions/Campaign.scala src/main/scala/oathdigital/gameplay/actions/Travel.scala src/main/scala/oathdigital/gameplay/actions/Search.scala src/main/scala/oathdigital/gameplay/actions/Recover.scala src/main/scala/oathdigital/gameplay/actions/Forge.scala src/main/scala/oathdigital/gameplay/powers/rest/LeagueTreatyPower.scala
git commit -m "refactor(actions): executor result is ReadyGame, drop field derefs"
```

## Task 4: Fix the test suites

**Files:**
- Modify: `src/test/scala/oathdigital/gameplay/OperationExecutorSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerOperationsSuite.scala`

**Interfaces:**
- Consumes: Task 1 result types (`execute`/`executeAll`/`evolve` all return `ReadyGame`).
- Produces: suite green; receipts assertions gone.

- [ ] **Step 1: OperationExecutorSuite — drop `.ready` on executor results**

Mechanical edit: every `.toOption.get.ready` where the call is `executor.execute(...)` / `executor.executeAll(...)` becomes `.toOption.get`. Affected lines: 66-69 (the `authoritative` val — `.toOption.get.ready` → `.toOption.get`), 101 (`executeAll(...).toOption.get` — already no `.ready`; but `result.ready` at 102, 105 → `result`), 130, 149, 170, 179, 190, 226, 247, 263, 295, 302, 316, 327, 348, 363, 377, 383, 395, 412, 417, 430, 453, 470.

So `val result = executor.execute(source, draw).toOption.get.ready` becomes `val result = executor.execute(source, draw).toOption.get`, and `result.ready.game...` references become `result.game...`.

- [ ] **Step 2: OperationExecutorSuite — delete the receipts assertions**

In the test named `"ordered batches see staged state and retain root receipts"`, the test still exercises staged-state application — keep it, but delete the two receipt assertions (lines 106-107) and rename it to `"ordered batches apply staged state in order"`:

```scala
  test("ordered batches apply staged state in order") {
    val gain = Gain.Favor(playerId, Suit.Order, 2)
    val place = Move(
      Piece.Favor(3),
      PositionedLocation(Location.PlayArea(playerId)),
      PositionedLocation(Location.Site(sites.head))
    )
    val result = executor.executeAll(ready, Vector(gain, place)).toOption.get
    val actor = result.game.current.players.find(_.player == playerId).get

    assertEquals(actor.board.favor, 0)
    assertEquals(result.game.current.map.sites(sites.head).tokens.favor, 3)
  }
```

- [ ] **Step 3: OperationExecutorSuite — evolve call sites already fine**

Lines 486-530 use `OperationTransaction.evolve(...)` and inspect `Either` shape (`assertEquals(failed, Left(...))`, `.left.toOption`): these need no change since the `Left` side and the `Either` wrapper are unchanged. Verify by compiling.

- [ ] **Step 4: PowerOperationsSuite — drop `.ready` on evolve results**

Lines 46-47, 98-99, 121-122: `OperationTransaction.evolve(...)(Right(_)).toOption.get.ready` → `.toOption.get`. Line 104's `.isLeft` assertion is unchanged.

```scala
    val after = OperationTransaction.evolve(
      ready, operations, executor)(Right(_)).toOption.get
```

- [ ] **Step 5: Full test suite + bundle**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: exit code 0, all green.

- [ ] **Step 6: Grep proof that receipts are gone**

Run: `grep -rn "OperationReceipt\|OperationExecution\|\.receipts" src/main src/test`
Expected: no matches (empty output).

- [ ] **Step 7: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/OperationExecutorSuite.scala src/test/scala/oathdigital/gameplay/PowerOperationsSuite.scala
git commit -m "test(operations): drop receipts assertions after executor retype"
```

## Task 5: Verification checkpoint (Phase 1 done)

**Files:** none (verification only).

- [ ] **Step 1: Full verification**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: exit code 0.

Run: `git diff --check`
Expected: no whitespace errors.

- [ ] **Step 2: Confirm receipts dead everywhere**

Run: `grep -rn "OperationReceipt\|OperationExecution\|OperationReceipt\|\.receipts" src/main src/test`
Expected: no matches.

- [ ] **Step 3: Confirm behavior untouched — restart dev server and smoke-check a live game**

Run (background): `./sbtw "runMain oathdigital.server.OathServer var/oathdigital docs/catalog/new-foundations-component-catalog.json"`
Then GET `http://127.0.0.1:8080/health` → `ok`. Post one benign command to the manual test game (`manual-1788482797070-427915`, nextSequence per its current stream) and confirm a 200 and the expected event append — e.g. replaying the negotiation state must still offer the same `legalControls` as before the refactor.

- [ ] **Step 4: Phase-1 complete note**

Add a short entry to `docs/ROADMAP.md` (or the plan's Roadmap section above, ticking Phase 1 done) recording which Phase-1 commit hash shipped it.
