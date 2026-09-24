# Operation Family Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put every core-operation shape guard beside the mutation it defends, once, so the live pipeline and replay cross one legality path.

**Architecture:** `gameplay/operations` is split into five family files (card movement, card face, resources, board control, turn state), one shared-writers file, and one dispatcher file `OperationApplication` whose `mutate` runs the aggregated guard as its precondition. `OperationPipeline` and `OperationExecutor` keep their contracts; `WalkerReplay` is untouched and gains the guards through the executor. `OperationValidator.scala` and `OperationStateMutation.scala` are dissolved.

**Tech Stack:** Scala 2.13.16, sbt via `./sbtw`, munit. Architecture gates: `BackendArchitectureSuite` and `python3 scripts/check-architecture.py`.

**Spec:** `docs/superpowers/specs/2026-09-24-operation-family-consolidation-design.md`. Replay decision: `docs/superpowers/specs/2026-09-24-replay-posture-decision.md`.

## Global Constraints

- Production Scala files stay at or below 800 lines (`BackendArchitectureSuite` "all production Scala files stay bounded"). Target under 500 for every file this plan creates.
- Files under `gameplay/walker` and `gameplay/operations` must not contain any power's name as a lowercase substring (`BackendArchitectureSuite` "a walker power imports no engine, and the engine never learns its name"). Do not write power names in comments.
- No rejection code or detail string, executed/skipped result, wire shape, or walker event changes. Every existing `OperationPipeline.run` call keeps its arguments.
- `OperationPolicy` stays. Only `OperationPolicy.all` is removed.
- Commit messages: Conventional Commits, `refactor(operations): ...` unless noted. End every commit message with the trailer line `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Verbatim moves: when a step says "move `def x` unchanged", cut the whole member (its doc comment, signature and body) from the source file and paste it into the target, changing only the visibility modifier stated. Locate members by their signature, not by line number; line numbers in this plan are as of commit `8ba502d` and drift as tasks run.
- Run tests with `./sbtw "testOnly <fully.qualified.Suite>"`. The full gate is `./sbtw test`, `./sbtw frontend/test`, `python3 scripts/check-architecture.py`.

---

## File structure

Created:

- `src/main/scala/oathdigital/gameplay/operations/OperationStateWrites.scala` — shared state writers, `CardTransfer`, `sequence`.
- `src/main/scala/oathdigital/gameplay/operations/TurnStateOperations.scala` — supply, tracks, dice, phase/turn/title: guards and mutations.
- `src/main/scala/oathdigital/gameplay/operations/CardFaceOperations.scala` — `Flip`/`Reveal`/`Peek`: guards and mutations.
- `src/main/scala/oathdigital/gameplay/operations/BoardControlOperations.scala` — pawn and banner moves: guards and mutations.
- `src/main/scala/oathdigital/gameplay/operations/ResourceOperations.scala` — favor/secrets/warbands, `FlipSecrets`, discard descriptions: guards and mutations.
- `src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala` — `validate` and `mutate` dispatchers, `RunningBoards`, reason classification.
- `src/test/scala/oathdigital/gameplay/walker/WalkerReplayGuardSuite.scala` — the replay behaviour change.

Renamed:

- `OperationCardMutation.scala` → `CardMovementOperations.scala` (gains the card guards).
- `src/test/.../gameplay/OperationValidatorSuite.scala` → `OperationApplicationSuite.scala`.
- `src/test/.../gameplay/operations/OperationStateMutationSuite.scala` → `OperationMutationSuite.scala`.

Deleted:

- `OperationValidator.scala`, `OperationStateMutation.scala`.
- `ClearDicePool` (model, two codec arms, one wire fixture line).

Modified:

- `OperationExecutor.scala` (drop `OperationPolicy.all`; `execute` calls `OperationApplication.mutate`; doc).
- `OperationPipeline.scala` (drop `report` and the validator; call `resolve` with allowlist and restrictions; doc).
- `OperationResolution.scala` (takes allowlist and restrictions; owns reason concatenation).
- `OperationStateAdapter.scala` (drop `applyOperation`).
- `OperationResolutionSuite.scala`, `GameEventWireSuite.scala`, `TravelProcedure.scala` (comment), `docs/ROADMAP.md`.

---

### Task 1: Delete the production-dead batch validation

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationExecutor.scala` (`OperationPolicy.all`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationPipeline.scala` (`report`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationValidator.scala` (`OperationValidator.validateBatch`, `OperationShape.validateBatch`, `OperationShape.first`)
- Test: `src/test/scala/oathdigital/gameplay/OperationValidatorSuite.scala`

**Interfaces:**
- Consumes: nothing new.
- Produces: `OperationShape.validate(ready, operation): Vector[OperationReason]` remains the only public reason entry until Task 9 renames it.

- [ ] **Step 1: Remove the two tests that only reach the deleted members**

In `OperationValidatorSuite.scala` delete the whole `test("validateBatch reports a cross-operation same-card move")` block and the whole `test("report aggregates whole-batch reasons with allowlist precedence")` block.

- [ ] **Step 2: Remove the three `OperationShape.first` assertions**

In the same suite delete these lines exactly (each is a two-line `assertEquals`):

```scala
    assertEquals(OperationShape.first(ready, operation).map(_.code),
      Some("insufficient-pieces"))
```

```scala
    assertEquals(OperationShape.first(missing,
      Gain.Warbands(playerId, redForce, 1)).map(_.code),
      Some("unknown-warband-supply"))
```

```scala
    assertEquals(OperationShape.first(ready, SpendSupply(playerId, 8))
      .map(_.code), Some("insufficient-supply"))
```

- [ ] **Step 3: Run the suite to confirm it still compiles and passes**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationValidatorSuite"`
Expected: PASS, 14 tests.

- [ ] **Step 4: Delete `OperationPolicy.all`**

In `OperationExecutor.scala` delete the member from its doc comment `/** Composes several policies first-fail. ...` through the closing `}` of `def all(...)`.

- [ ] **Step 5: Delete `OperationPipeline.report`**

In `OperationPipeline.scala` delete from `/** Aggregated whole-batch report against the initial state: ...` through the closing of `def report(...)`. Remove the sentence in the `OperationPipeline` object doc that begins `so aggregated whole-batch validation is exposed through [[report]] and` so the paragraph reads:

```scala
  * Whole-batch rejection must stay staged: an operation later in a batch can
  * be satisfiable only after earlier operations ran (for example a Campaign
  * losing-force `ReturnToBoard` that moves warbands out of a bank which
  * in-batch `Kill`s replenish). Validating every operation against the initial
  * state would reject those trajectory batches the retired executor accepted,
  * so the authoritative rejection is the staged `validateOne` per operation —
  * the same first-fail, atomic behavior the executor performed.
```

- [ ] **Step 6: Delete `validateBatch` and `first`**

In `OperationValidator.scala`:

1. Delete `OperationValidator.validateBatch` (the first method of the class, from `def validateBatch(` through its closing `operations.flatMap(restrictionReasons(ready, _))`).
2. Delete `OperationShape.validateBatch` from its doc `/** All shape violations for a whole batch against the INITIAL state, plus` through the closing `}` after `perOperation ++ crossOperation.map(...)`.
3. Delete `OperationShape.first` from `/** First violation as an [[OperationError]]-compatible rejection, if any. */` through `violations(ready, operation).headOption`.
4. In the class doc, replace the first paragraph with:

```scala
/** Aggregated validator owned by [[OperationPipeline]] for one run:
  * `validateOne` checks every operation against the staged state during the
  * fold, and `validateResolvedOne` re-checks a shrunk operation without the
  * allowlist.
  *
  * Both return every violation as an [[OperationReason]] (never first-fail),
  * so callers can inspect all of them. Pipeline rejection stays first-fail:
  * it takes the head reason.
  */
```

- [ ] **Step 7: Compile and run the operation suites**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationValidatorSuite oathdigital.gameplay.OperationPipelineSuite oathdigital.gameplay.OperationExecutorSuite oathdigital.gameplay.OperationResolutionSuite oathdigital.gameplay.PowerOperationsSuite"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add -A src/main/scala/oathdigital/gameplay/operations src/test/scala/oathdigital/gameplay/OperationValidatorSuite.scala
git commit -m "refactor(operations): delete the batch validation nothing in production reaches

OperationPipeline.report, OperationShape.validateBatch, OperationShape.first and
the cross-operation conflict detector had one caller between them, a test.
OperationPolicy.all had none. The staged per-operation check is the
authoritative rejection, as the pipeline doc already said.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: Delete `ClearDicePool`

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala`
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`
- Test: `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`

**Interfaces:**
- Produces: nothing. `ClearDicePool` has no emitter in `src/main` (verified by grep before this plan was written); no journal written by this codebase carries one.

- [ ] **Step 1: Remove the fixture entry**

In `GameEventWireSuite.scala` delete the line `      ClearDicePool(PoolKey("recover")),` from the operations fixture list (it sits between `ModifyRollOutcome(PoolKey("recover"), Some(1), Some(2)),` and `RecordCampaignResult(...)`).

- [ ] **Step 2: Remove the model case**

In `CoreOperations.scala` delete:

```scala
/** Removes `pool` from the rollPools state map. */
final case class ClearDicePool(pool: PoolKey) extends PrimitiveOperation
```

- [ ] **Step 3: Remove the two codec arms**

In `WalkerOperationCodec.scala` delete the encode arm:

```scala
      case ClearDicePool(pool) => ujson.Obj("kind" -> "clear-dice-pool",
        "pool" -> pool.value)
```

and the decode arm:

```scala
      case "clear-dice-pool" => Right(ClearDicePool(PoolKey(value("pool").str)))
```

- [ ] **Step 4: Compile everything and run the wire suite**

Run: `./sbtw "testOnly oathdigital.serialization.GameEventWireSuite"`
Expected: PASS. The encode match is exhaustiveness-checked, so a leftover reference fails compilation loudly.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/model/CoreOperations.scala src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala src/test/scala/oathdigital/serialization/GameEventWireSuite.scala
git commit -m "refactor(operations): delete ClearDicePool, an operation nothing applied

It was encodable and recordable but no mutation arm existed and no production
code emitted it. WalkerCompleted already clears every pool wholesale.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: Shared state writers and one `CardTransfer`

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/operations/OperationStateWrites.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationStateMutation.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationCardMutation.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationValidator.scala`

**Interfaces:**
- Produces: `OperationStateWrites.{CardTransfer, cardTransfers, sequence, updateCommonCards, updatePlayer, updateSite, updateCardTokens, updateCardState, updateAtlasCardState, semanticLocation}` — all package-visible, signatures unchanged from their `OperationStateMutation` originals.

- [ ] **Step 1: Create the file with the moved members**

Write `OperationStateWrites.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.model._

/** State writers shared by every operation family, and the one collector of
  * card transfers that both a guard and a mutation read. Nothing here decides
  * legality.
  */
private[operations] object OperationStateWrites {
  import OperationError._
  import OperationStateAdapter.{playerState, siteState}

  /** A card `Move` or `Bury`, seen the same way by the guard that checks it
    * and the mutation that performs it.
    */
  final case class CardTransfer(
      piece: Piece.Card,
      from: PositionedLocation,
      to: PositionedLocation,
      resultingOrientation: Option[Orientation]
  )

  def cardTransfers(leaves: Vector[Operation]): Vector[CardTransfer] =
    leaves.collect {
      case Move(piece: Piece.Card, from, to, orientation) =>
        CardTransfer(piece, from, to, orientation)
      case bury: Bury => CardTransfer(
        Piece.Card(bury.card.id),
        bury.from,
        bury.to,
        resultingOrientation = None
      )
    }

  def sequence[A](
      values: Vector[Either[OperationError, A]]
  ): Either[OperationError, Vector[A]] =
    values.foldLeft[Either[OperationError, Vector[A]]](Right(Vector.empty)) {
      case (result, value) => for {
        accumulated <- result
        next <- value
      } yield accumulated :+ next
    }

  // updateCommonCards, updatePlayer, updateSite, updateCardTokens,
  // updateCardState, updateAtlasCardState and semanticLocation: moved from
  // OperationStateMutation unchanged, with `private[operations]` and
  // `private` modifiers removed so they are public inside this object.
}
```

Then cut the following members out of `OperationStateMutation.scala` and paste them in place of that trailing comment, in this order, dropping their `private[operations]`/`private` modifiers: `updateCommonCards`, `updatePlayer`, `updateSite`, `updateCardTokens`, `updateCardState`, `updateAtlasCardState`, `semanticLocation`. Also cut `CardTransfer`, `cardTransfers` and `sequence` out of `OperationStateMutation.scala` (the file's copies; the versions above replace them).

- [ ] **Step 2: Re-point `OperationStateMutation` at the shared file**

At the top of `object OperationStateMutation`, after `import OperationStateAdapter._`, add:

```scala
  import OperationStateWrites._
```

- [ ] **Step 3: Re-point `OperationCardMutation`**

Replace its import block

```scala
  import OperationStateMutation.{
    CardTransfer,
    cardTransfers,
    semanticLocation,
    sequence,
    updateCommonCards,
    updatePlayer,
    updateSite
  }
```

with

```scala
  import OperationStateWrites.{
    CardTransfer,
    cardTransfers,
    semanticLocation,
    sequence,
    updateCommonCards,
    updatePlayer,
    updateSite
  }
```

- [ ] **Step 4: Collapse `Transfer` into `CardTransfer` in the guard**

In `OperationValidator.scala`, inside `object OperationShape`:

1. Delete the `private final case class Transfer(...)` definition and the `private def transfers(leaves)` method.
2. Add `import OperationStateWrites.{CardTransfer, cardTransfers}` under `import OperationStateAdapter._`.
3. Replace every remaining `Transfer` type reference with `CardTransfer`: in `ResolvedTransfer(transfer: Transfer, located: LocatedCard)`, in `cardDestinationViolation(located: LocatedCard, transfer: Transfer)`, and in `statefulMaterializationViolation(located: LocatedCard, transfer: Transfer)`.
4. In `cardViolations`, replace `val all = transfers(leaves)` with `val all = cardTransfers(leaves)`.

- [ ] **Step 5: Compile and run the operation suites**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationValidatorSuite oathdigital.gameplay.OperationPipelineSuite oathdigital.gameplay.OperationExecutorSuite oathdigital.gameplay.operations.*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/operations
git commit -m "refactor(operations): share the state writers and one CardTransfer

The guard's Transfer and the mutation's CardTransfer were the same collect
under two names. One copy now lives beside the shared writers every family
will need.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: `TurnStateOperations`

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/operations/TurnStateOperations.scala`
- Modify: `OperationValidator.scala` (`nonMoveViolations` arms; delete `adjustSupplyViolation`)
- Modify: `OperationStateMutation.scala` (`applyNonMoveLeaves` arms; delete the moved mutations)

**Interfaces:**
- Produces:
  - `TurnStateOperations.supplyViolation(ready: ReadyGame, player: PlayerId, amount: Int, supply: Map[PlayerId, Int]): (Vector[OperationError], Map[PlayerId, Int])`
  - `TurnStateOperations.visionsDrawnViolation(ready: ReadyGame): Vector[OperationError]`
  - `TurnStateOperations.adjustSupply(ready, player, amount): Either[OperationError, ReadyGame]`
  - `TurnStateOperations.advanceVisionsDrawn(ready): Either[OperationError, ReadyGame]`
  - `TurnStateOperations.adjustDicePool(ready, pool, delta)`, `modifyRollOutcome(ready, pool, skulls, score): ReadyGame`, `recordPowerUse(ready, power): ReadyGame`, `enterPhase(ready, phase)`, `setOathkeeper(ready, holder)`, `recordCampaignResult(ready, fact: CampaignResult): ReadyGame`, `beginTurn(ready, player, phase)` — same signatures as their originals.

- [ ] **Step 1: Create the file**

```scala
package oathdigital.gameplay.operations

import oathdigital.model._

/** Supply, the Visions Drawn track, dice pools and roll outcomes, and the
  * turn, phase and title writes.
  *
  * Only supply and the Visions Drawn track carry a shape guard here. The
  * other operations are guarded at mutation time alone: whether a phase may
  * be entered or a title may change is a fact about the turn they run in,
  * and a shape guard would read the same state the mutation reads.
  */
private[operations] object TurnStateOperations {
  import OperationError._
  import OperationStateAdapter.playerState
  import OperationStateWrites.updatePlayer

  // ------------------------------------------------------------------
  // Guards
  // ------------------------------------------------------------------

  /** Threads a running per-player supply through one operation's leaves so a
    * batch of spends within one operation is checked cumulatively.
    */
  def supplyViolation(
      ready: ReadyGame,
      player: PlayerId,
      amount: Int,
      supply: Map[PlayerId, Int]
  ): (Vector[OperationError], Map[PlayerId, Int]) =
    playerState(ready, player) match {
      case Left(error) => (Vector(error), supply)
      case Right(_) =>
        val current = supply.getOrElse(player, 0)
        if (amount < 0) {
          val required = -amount
          if (current >= required)
            (Vector.empty, supply.updated(player, current - required))
          else (Vector(InsufficientSupply(required, current)), supply)
        } else (Vector.empty, supply.updated(player,
          math.min(SupplyTrack.Maximum, current + amount)))
    }

  def visionsDrawnViolation(ready: ReadyGame): Vector[OperationError] =
    Option.when(ready.game.current.tracks.visionsDrawn == Int.MaxValue)(
      VisionsDrawnOverflow: OperationError).toVector

  // ------------------------------------------------------------------
  // Mutations
  // ------------------------------------------------------------------

  def advanceVisionsDrawn(ready: ReadyGame): Either[OperationError, ReadyGame] = {
    val current = ready.game.current
    if (current.tracks.visionsDrawn == Int.MaxValue)
      Left(VisionsDrawnOverflow)
    else Right(ready.copy(game = ready.game.copy(current = current.copy(
      tracks = current.tracks.copy(
        visionsDrawn = current.tracks.visionsDrawn + 1)))))
  }

  def recordCampaignResult(ready: ReadyGame, fact: CampaignResult): ReadyGame =
    ready.updateCurrent(_.copy(lastCampaignResult = Some(fact)))

  // adjustSupply, adjustDicePool (with its own doc comment, the one that
  // begins "Adds `delta` dice to a named pool's count"), modifyRollOutcome
  // (with the doc that begins "An upsert"), recordPowerUse, enterPhase,
  // setOathkeeper and beginTurn: moved from OperationStateMutation unchanged,
  // `private` dropped.
}
```

Then cut `adjustSupply`, `adjustDicePool`, `modifyRollOutcome`, `recordPowerUse`, `enterPhase`, `setOathkeeper` and `beginTurn` out of `OperationStateMutation.scala` and paste them in place of the trailing comment, dropping `private`. Note that in the source the doc comment beginning `/** Adds \`delta\` dice ...` sits directly above the `/** An upsert ...` doc of `modifyRollOutcome`; it belongs to `adjustDicePool`, so move it with `adjustDicePool`.

- [ ] **Step 2: Re-point the guard fold**

In `OperationValidator.scala`, `nonMoveViolations`, replace the three arms

```scala
      case ((result, state), SpendSupply(player, amount, _)) =>
        val (violations, updated) =
          adjustSupplyViolation(ready, player, -amount, state)
        (result ++ violations, updated)
      case ((result, state), GainSupply(player, amount)) =>
        val (violations, updated) =
          adjustSupplyViolation(ready, player, amount, state)
        (result ++ violations, updated)
      case ((result, state), AdvanceVisionsDrawn) =>
        (result ++ Option.when(ready.game.current.tracks.visionsDrawn ==
          Int.MaxValue)(OperationError.VisionsDrawnOverflow), state)
```

with

```scala
      case ((result, state), SpendSupply(player, amount, _)) =>
        val (violations, supply) = TurnStateOperations.supplyViolation(
          ready, player, -amount, state.supply)
        (result ++ violations, state.copy(supply = supply))
      case ((result, state), GainSupply(player, amount)) =>
        val (violations, supply) = TurnStateOperations.supplyViolation(
          ready, player, amount, state.supply)
        (result ++ violations, state.copy(supply = supply))
      case ((result, state), AdvanceVisionsDrawn) =>
        (result ++ TurnStateOperations.visionsDrawnViolation(ready), state)
```

and delete `private def adjustSupplyViolation(...)` from `OperationShape`.

- [ ] **Step 3: Re-point the mutation fold**

In `OperationStateMutation.scala`, `applyNonMoveLeaves`, replace the arms from `case (result, SpendSupply(player, amount, _)) =>` through `case (result, BeginTurn(player, phase)) =>` with

```scala
      case (result, SpendSupply(player, amount, _)) =>
        result.flatMap(TurnStateOperations.adjustSupply(_, player, -amount))
      case (result, GainSupply(player, amount)) =>
        result.flatMap(TurnStateOperations.adjustSupply(_, player, amount))
      case (result, AdvanceVisionsDrawn) =>
        result.flatMap(TurnStateOperations.advanceVisionsDrawn)
      case (result, ModifyDicePool(pool, delta, _)) =>
        result.flatMap(TurnStateOperations.adjustDicePool(_, pool, delta))
      case (result, ModifyRollOutcome(pool, skulls, score)) =>
        result.map(TurnStateOperations.modifyRollOutcome(_, pool, skulls, score))
      case (result, RecordPowerUse(power)) =>
        result.map(TurnStateOperations.recordPowerUse(_, power))
      case (result, EnterPhase(phase)) =>
        result.flatMap(TurnStateOperations.enterPhase(_, phase))
      case (result, SetOathkeeper(holder)) =>
        result.flatMap(TurnStateOperations.setOathkeeper(_, holder))
      case (result, RecordCampaignResult(fact)) =>
        result.map(TurnStateOperations.recordCampaignResult(_, fact))
      case (result, BeginTurn(player, phase)) =>
        result.flatMap(TurnStateOperations.beginTurn(_, player, phase))
```

- [ ] **Step 4: Compile and run the suites that exercise these operations**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationValidatorSuite oathdigital.gameplay.OperationExecutorSuite oathdigital.gameplay.SupplyAdjustSuite oathdigital.gameplay.OperationPipelineSuite oathdigital.gameplay.operations.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/operations
git commit -m "refactor(operations): move turn-state guards beside their mutations

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: `CardFaceOperations`

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/operations/CardFaceOperations.scala`
- Modify: `OperationValidator.scala` (`nonMoveViolations` arms; delete `flipViolation`, `peekViolation`)
- Modify: `OperationStateMutation.scala` (`applyNonMoveLeaves` arms; delete `flipCard`, `peek`, `appendDistinct`, `recordSiteRelicKnowledge`)

**Interfaces:**
- Produces: `CardFaceOperations.flipViolation(ready, id, at, orientation): Vector[OperationError]`, `peekViolation(ready, viewer, id, at): Vector[OperationError]`, `flipCard(ready, id, at, orientation): Either[OperationError, ReadyGame]`, `peek(ready, viewer, id, at): Either[OperationError, ReadyGame]` — signatures unchanged.

- [ ] **Step 1: Create the file**

```scala
package oathdigital.gameplay.operations

import oathdigital.model._

/** `Flip`, `Reveal` and `Peek`: what a card shows and who knows it. */
private[operations] object CardFaceOperations {
  import OperationError._
  import OperationStateAdapter.{card, isDiscardLook, playerState}
  import OperationStateWrites.updateCardState

  // flipViolation and peekViolation: moved from OperationShape unchanged,
  // `private` dropped.

  // flipCard, peek, appendDistinct and recordSiteRelicKnowledge: moved from
  // OperationStateMutation unchanged; `private` dropped from flipCard and
  // peek, kept on the other two.
}
```

Move the six members as the comments say. `flipViolation` refers to `OperationStateAdapter.isDiscardLook` by its qualified name; that still compiles, or shorten it to `isDiscardLook`.

- [ ] **Step 2: Re-point the folds**

In `OperationValidator.scala`, `nonMoveViolations`:

```scala
      case ((result, state), Flip(id, at, orientation)) =>
        (result ++ CardFaceOperations.flipViolation(ready, id, at, orientation),
          state)
```

```scala
      case ((result, state), Peek(viewer, id, at)) =>
        (result ++ CardFaceOperations.peekViolation(ready, viewer, id, at), state)
```

In `OperationStateMutation.scala`, `applyNonMoveLeaves`:

```scala
      case (result, Flip(id, at, orientation)) =>
        result.flatMap(CardFaceOperations.flipCard(_, id, at, orientation))
```

```scala
      case (result, Peek(viewer, id, at)) =>
        result.flatMap(CardFaceOperations.peek(_, viewer, id, at))
```

- [ ] **Step 3: Compile and test**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationValidatorSuite oathdigital.gameplay.OperationExecutorSuite oathdigital.gameplay.RevealDiscardSuite oathdigital.gameplay.operations.*"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/operations
git commit -m "refactor(operations): move card-face guards beside their mutations

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: `BoardControlOperations`

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/operations/BoardControlOperations.scala`
- Modify: `OperationValidator.scala` (`violations` call; delete section 4)
- Modify: `OperationStateMutation.scala` (`mutate` call; delete pawn/banner members and `requireFinite`)

**Interfaces:**
- Produces: `BoardControlOperations.pawnAndBannerViolations(ready, leaves): Vector[OperationError]` and `applyPawnAndBannerMoves(ready, leaves): Either[OperationError, ReadyGame]` — signatures unchanged.

- [ ] **Step 1: Create the file**

```scala
package oathdigital.gameplay.operations

import oathdigital.model._

/** Pawn and banner moves. The guard threads the pawns' sites and the
  * banners' holders through one operation's leaves so two moves of one piece
  * in one operation are checked in sequence.
  */
private[operations] object BoardControlOperations {
  import OperationError._
  import OperationStateAdapter.{bannerHolder, playerState, quantity, siteState}
  import OperationStateWrites.updatePlayer

  // MovedPieces, pawnAndBannerViolations, pawnMoveViolation and
  // bannerMoveViolation: moved from OperationShape unchanged; `private`
  // dropped from pawnAndBannerViolations only.

  // applyPawnAndBannerMoves, movePawn, moveBanner, setBannerHolder and
  // requireFinite: moved from OperationStateMutation unchanged; `private`
  // dropped from applyPawnAndBannerMoves only. requireFinite is deleted in
  // Task 10.
}
```

- [ ] **Step 2: Re-point the dispatchers**

In `OperationShape.violations` replace `accumulated ++= pawnAndBannerViolations(ready, leaves)` with `accumulated ++= BoardControlOperations.pawnAndBannerViolations(ready, leaves)` and delete the whole `// 4. Pawn and banner move preconditions` section.

In `OperationStateMutation.mutate` replace `pieces <- applyPawnAndBannerMoves(resources, leaves)` with `pieces <- BoardControlOperations.applyPawnAndBannerMoves(resources, leaves)`.

- [ ] **Step 3: Compile and test**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationValidatorSuite oathdigital.gameplay.OperationExecutorSuite oathdigital.gameplay.operations.*"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/operations
git commit -m "refactor(operations): move pawn and banner guards beside their mutations

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: `ResourceOperations`

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/operations/ResourceOperations.scala`
- Modify: `OperationValidator.scala` (`violations` calls and the `FlipSecrets` arm; delete sections 3 and the resource-description guard)
- Modify: `OperationStateMutation.scala` (`mutate` call and the `FlipSecrets` arm; delete the counted members)

**Interfaces:**
- Produces:
  - `ResourceOperations.resourceDescriptionViolations(ready, operation)`, `planSecrets(ready, moves)`, `countedSourceViolations(ready, favorMoves, warbandMoves, secretReasons)`, `countedDestinationViolations(ready, leaves)` — signatures unchanged.
  - `ResourceOperations.flipSecretsViolation(ready: ReadyGame, player: PlayerId, amount: Int, from: SecretSide, to: SecretSide, faceUp: Map[PlayerId, Int], faceDown: Map[PlayerId, Int]): (Vector[OperationError], Map[PlayerId, Int], Map[PlayerId, Int])`
  - `ResourceOperations.applyCountedMoves(ready, leaves)`, `flipPlayerSecrets(ready, player, amount, from, to)` — signatures unchanged.

- [ ] **Step 1: Create the file**

```scala
package oathdigital.gameplay.operations

import oathdigital.model._

/** Favor, secrets and warbands: counted moves, secret flips, and the
  * resource descriptions a discard carries. The mutation debits every source
  * before crediting any destination, so a batch that moves the same resource
  * twice within one operation never sees an intermediate credit.
  */
private[operations] object ResourceOperations {
  import OperationError._
  import OperationStateAdapter.{playerState, quantity, siteState}
  import OperationStateWrites.{updateCardTokens, updatePlayer, updateSite}

  // ------------------------------------------------------------------
  // Guards
  // ------------------------------------------------------------------

  // resourceDescriptionViolations, planSecrets, countedSourceViolations,
  // countedDestinationViolations, favorSourceViolations,
  // warbandSourceViolations and finiteSufficiency: moved from OperationShape
  // unchanged; `private` dropped from the first four only.

  /** Threads the running faceup and facedown counts through one operation's
    * leaves; `RunningBoards.initial` in the dispatcher seeds them with the
    * planned secret moves so a flip after a move sees the moved secrets.
    */
  def flipSecretsViolation(
      ready: ReadyGame,
      player: PlayerId,
      amount: Int,
      from: SecretSide,
      to: SecretSide,
      faceUp: Map[PlayerId, Int],
      faceDown: Map[PlayerId, Int]
  ): (Vector[OperationError], Map[PlayerId, Int], Map[PlayerId, Int]) =
    playerState(ready, player) match {
      case Left(error) => (Vector(error), faceUp, faceDown)
      case Right(_) =>
        val available = from match {
          case SecretSide.FaceUp => faceUp.getOrElse(player, 0)
          case SecretSide.FaceDown => faceDown.getOrElse(player, 0)
        }
        if (available < amount)
          (Vector(InsufficientPieces(Piece.Secrets(amount),
            Location.PlayArea(player), available)), faceUp, faceDown)
        else {
          val faceUpDelta = (from, to) match {
            case (SecretSide.FaceUp, SecretSide.FaceDown) => -amount
            case (SecretSide.FaceDown, SecretSide.FaceUp) => amount
            case _ => 0
          }
          (Vector.empty,
            faceUp.updated(player, faceUp.getOrElse(player, 0) + faceUpDelta),
            faceDown.updated(player,
              faceDown.getOrElse(player, 0) - faceUpDelta))
        }
    }

  // ------------------------------------------------------------------
  // Mutations
  // ------------------------------------------------------------------

  // applyCountedMoves, adjustFavor, adjustSecrets, adjustWarbands and
  // flipPlayerSecrets: moved from OperationStateMutation unchanged; `private`
  // dropped from applyCountedMoves and flipPlayerSecrets only.
}
```

Move the members as the comments say, and delete the old `private def flipSecretsViolation(...)` (the `RunningBoards`-typed version) from `OperationShape`.

- [ ] **Step 2: Re-point the dispatchers**

In `OperationShape.violations`:

```scala
    val (secretReasons, plannedSecrets) =
      ResourceOperations.planSecrets(ready, secretMoves)
```

```scala
    accumulated ++= ResourceOperations.resourceDescriptionViolations(
      ready, operation)
```

```scala
    accumulated ++= ResourceOperations.countedSourceViolations(
      ready, favorMoves, warbandMoves, secretReasons)
    accumulated ++= ResourceOperations.countedDestinationViolations(
      ready, leaves)
```

In `OperationShape.nonMoveViolations`:

```scala
      case ((result, state), FlipSecrets(player, amount, from, to)) =>
        val (violations, faceUp, faceDown) =
          ResourceOperations.flipSecretsViolation(ready, player, amount,
            from, to, state.faceUp, state.faceDown)
        (result ++ violations, state.copy(faceUp = faceUp, faceDown = faceDown))
```

In `OperationStateMutation.mutate`: `resources <- ResourceOperations.applyCountedMoves(ready, leaves)`. In `applyNonMoveLeaves`:

```scala
      case (result, FlipSecrets(player, amount, from, to)) =>
        result.flatMap(ResourceOperations.flipPlayerSecrets(
          _, player, amount, from, to))
```

- [ ] **Step 3: Compile and test**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationValidatorSuite oathdigital.gameplay.OperationExecutorSuite oathdigital.gameplay.OperationPipelineSuite oathdigital.gameplay.OperationResolutionSuite oathdigital.gameplay.PowerOperationsSuite oathdigital.gameplay.PayCostSuite oathdigital.gameplay.PayCostSettlementSuite oathdigital.gameplay.operations.*"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/operations
git commit -m "refactor(operations): move resource guards beside their mutations

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: `CardMovementOperations`

**Files:**
- Rename: `OperationCardMutation.scala` → `src/main/scala/oathdigital/gameplay/operations/CardMovementOperations.scala`
- Modify: `OperationValidator.scala` (`violations` calls; delete sections 1 and 2)
- Modify: `OperationStateMutation.scala` (`mutate` call)

**Interfaces:**
- Produces: `CardMovementOperations.positionViolations(leaves: Vector[Operation]): Vector[OperationError]`, `cardViolations(ready, leaves): Vector[OperationError]`, `applyCardMoves(ready, leaves): Either[OperationError, ReadyGame]` — signatures unchanged from their originals.

- [ ] **Step 1: Rename the file and object**

```bash
git mv src/main/scala/oathdigital/gameplay/operations/OperationCardMutation.scala src/main/scala/oathdigital/gameplay/operations/CardMovementOperations.scala
```

In the renamed file change `private[operations] object OperationCardMutation {` to `private[operations] object CardMovementOperations {`, replace the doc `/** Internal card-storage mutation boundary used by [[OperationStateMutation]]. */` with

```scala
/** Card movement: `Move` of a card, `Bury`, and the composites that flatten
  * to them. The guard checks stack-position conventions, that each card is
  * where the operation says it is (and in the stack position it names), and
  * that its destination can hold it; the mutation removes every card before
  * inserting any.
  */
```

and change `import OperationStateAdapter.playerState` to `import OperationStateAdapter.{card, playerState}`.

- [ ] **Step 2: Move the card guards in**

Cut from `OperationShape` the whole of section `// 1. Primitive position / stack-convention checks` (`positionViolations` both overloads, `sourcePositionViolation`, `destinationPositionViolation`, `isStack`) and section `// 2. Card-source and destination checks` (`ResolvedTransfer`, `cardViolations`, `cardSourceOrderViolations`, `cardDestinationViolation`, `statefulMaterializationViolation`, `cardDeck`, `stackCards`). Paste them into `CardMovementOperations` above `applyCardMoves`, dropping `private` from `positionViolations(leaves: Vector[Operation])` and `cardViolations` only.

- [ ] **Step 3: Re-point the dispatchers**

In `OperationShape.violations`:

```scala
    accumulated ++= CardMovementOperations.positionViolations(leaves)
    accumulated ++= CardMovementOperations.cardViolations(ready, leaves)
```

In `OperationStateMutation.mutate`: `cards <- CardMovementOperations.applyCardMoves(pieces, leaves)`.

- [ ] **Step 4: Compile and test, and check the line count**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationValidatorSuite oathdigital.gameplay.OperationExecutorSuite oathdigital.gameplay.OperationPipelineSuite oathdigital.gameplay.OperationVocabularySuite oathdigital.gameplay.operations.*"`
Expected: PASS.

Run: `wc -l src/main/scala/oathdigital/gameplay/operations/CardMovementOperations.scala`
Expected: under 500.

- [ ] **Step 5: Commit**

```bash
git add -A src/main/scala/oathdigital/gameplay/operations
git commit -m "refactor(operations): move card-movement guards beside their mutations

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 9: `OperationApplication` — one guard, both entries

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala`
- Delete: `OperationValidator.scala`, `OperationStateMutation.scala`
- Modify: `OperationResolution.scala`, `OperationPipeline.scala`, `OperationExecutor.scala`, `OperationStateAdapter.scala`
- Test: create `src/test/scala/oathdigital/gameplay/walker/WalkerReplayGuardSuite.scala`
- Test: rename `OperationValidatorSuite.scala` → `OperationApplicationSuite.scala` (rewritten below)
- Test: modify `OperationResolutionSuite.scala`
- Test: rename `gameplay/operations/OperationStateMutationSuite.scala` → `OperationMutationSuite.scala`

**Interfaces:**
- Consumes: every family object from Tasks 4–8.
- Produces:
  - `OperationApplication.validate(ready: ReadyGame, operation: CoreOperation): Vector[OperationReason]` (`private[gameplay]` object; this is the resolver's interface and the test surface for reasons).
  - `OperationApplication.mutate(ready, operation): Either[OperationError, ReadyGame]` (`private[operations]`).
  - `OperationResolution.reasons(ready, requested, allowlist: OperationPolicy, restrictions: Vector[OperationRestriction]): Vector[OperationReason]` (`private[gameplay]`).
  - `OperationResolution.resolve(ready, requested, allowlist: OperationPolicy, restrictions: Vector[OperationRestriction], requireAll: Boolean = false): Either[OathViolation, Result]`.

- [ ] **Step 1: Write the failing replay test**

Create `src/test/scala/oathdigital/gameplay/walker/WalkerReplayGuardSuite.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.walker.WalkerStepPayload.DeltaRecorded
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

/** Replay applies recorded operations through the executor. The executor
  * runs the shape guard as the mutation's precondition, so a recorded
  * operation the live pipeline would have refused is refused on replay too,
  * instead of applying with the guard skipped.
  */
class WalkerReplayGuardSuite extends munit.FunSuite {
  private val held = ready.updateCurrent(current => current.copy(
    commonCards = current.commonCards.copy(
      worldDeck = current.commonCards.worldDeck.filterNot(_ == worldDenizen)),
    temporaryHands = Map(playerId -> Vector(worldDenizen))))

  private def discard(position: StackPosition) = Move(
    Piece.Card(worldDenizen),
    PositionedLocation(Location.Hand(playerId)),
    PositionedLocation(Location.RegionalDiscard(Region.Cradle), position))

  private def step(operation: CoreOperation) = WalkerStepRecorded("0",
    DeltaRecorded(DeltaMeaning.OperationApplied("discard")), Vector(operation),
    Vector.empty)

  test("a recorded discard with a stack position replays") {
    val replayed = ProcedureWalker.applyRecorded(OathState.Ready(held),
      step(discard(StackPosition.Top)))
    val OathState.Ready(after) = replayed.toOption.get: @unchecked
    assertEquals(after.game.current.commonCards.discard(Region.Cradle),
      Vector(worldDenizen))
    assertEquals(after.game.current.temporaryHands(playerId), Vector.empty)
  }

  test("a recorded discard without a stack position is rejected on replay " +
      "instead of losing the card") {
    val replayed = ProcedureWalker.applyRecorded(OathState.Ready(held),
      step(discard(StackPosition.Unspecified)))
    assertEquals(replayed, Left(OathViolation.CoreOperationRejected(
      "invalid-stack-position",
      "stack destination must specify top or bottom")))
  }
}
```

- [ ] **Step 2: Run it to see the second test fail**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.WalkerReplayGuardSuite"`
Expected: first test PASS; second test FAIL — replay returns `Right(...)` because `insertStack` treats `Unspecified` as a no-op and the card is silently dropped.

- [ ] **Step 3: Create `OperationApplication.scala`**

```scala
package oathdigital.gameplay.operations

import oathdigital.model._

/** The two dispatchers over the operation families.
  *
  * [[validate]] aggregates every family's shape guard against `ready`, in a
  * fixed order, so the resolver can inspect all of an operation's reasons
  * before anything runs. [[mutate]] runs the same guard as its precondition
  * and then the family mutations in their fixed order. The live pipeline and
  * replay both reach the mutation through [[OperationExecutor]], so a guard
  * written once beside its mutation is a guard on both paths.
  *
  * The mutation runs families in the order counted resources, pawn and
  * banner, cards, then everything else; the guard checks in the order the
  * old shape layer used, which is what keeps every rejection code and detail
  * the same as before the families were split.
  */
private[gameplay] object OperationApplication {
  import OperationError._

  /** All shape violations for one operation against `ready`, aggregated. */
  def validate(
      ready: ReadyGame,
      operation: CoreOperation
  ): Vector[OperationReason] =
    violations(ready, operation).map(reason(_, operation))

  /** Guard first, then mutate: the first shape violation rejects before any
    * family runs, so no family mutation ever sees an operation the shape
    * guard refuses.
    */
  private[operations] def mutate(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, ReadyGame] =
    violations(ready, operation).headOption.toLeft(())
      .flatMap(_ => applyFamilies(ready, operation))

  private def violations(
      ready: ReadyGame,
      operation: CoreOperation
  ): Vector[OperationError] = {
    val leaves = Operation.flatten(operation)
    val favorMoves = leaves.collect {
      case move @ Move(_: Piece.Favor, _, _, _) => move
    }
    val secretMoves = leaves.collect {
      case move @ Move(_: Piece.Secrets, _, _, _) => move
    }
    val warbandMoves = leaves.collect {
      case move @ Move(_: Piece.Warbands, _, _, _) => move
    }
    val (secretReasons, plannedSecrets) =
      ResourceOperations.planSecrets(ready, secretMoves)

    val accumulated = Vector.newBuilder[OperationError]
    accumulated ++= CardMovementOperations.positionViolations(leaves)
    accumulated ++= CardMovementOperations.cardViolations(ready, leaves)
    accumulated ++= ResourceOperations.resourceDescriptionViolations(
      ready, operation)
    accumulated ++= PayCostRules.violations(ready, operation)
    accumulated ++= ResourceOperations.countedSourceViolations(
      ready, favorMoves, warbandMoves, secretReasons)
    accumulated ++= ResourceOperations.countedDestinationViolations(
      ready, leaves)
    accumulated ++= BoardControlOperations.pawnAndBannerViolations(
      ready, leaves)
    accumulated ++= nonMoveViolations(ready, leaves, plannedSecrets)
    accumulated.result()
  }

  private def applyFamilies(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, ReadyGame] = {
    val leaves = Operation.flatten(operation)
    for {
      resources <- ResourceOperations.applyCountedMoves(ready, leaves)
      pieces <- BoardControlOperations.applyPawnAndBannerMoves(
        resources, leaves)
      cards <- CardMovementOperations.applyCardMoves(pieces, leaves)
      finished <- applyNonMoveLeaves(cards, leaves)
    } yield finished
  }

  private def reason(error: OperationError,
      operation: CoreOperation): OperationReason = {
    val impossible = error match {
      case _: InsufficientSupply => true
      case InsufficientPieces(piece, _, _) => operation match {
        case _: Discard | _: PayCost => false
        case _ => piece.isInstanceOf[Piece.Counted]
      }
      case _ => false
    }
    OperationReason(error.code, error.detail,
      if (impossible) OperationReasonKind.Impossible
      else OperationReasonKind.Invalid)
  }

  // RunningBoards (the case class and its companion with `initial` and
  // `addDelta`): moved from OperationShape unchanged.

  private def nonMoveViolations(
      ready: ReadyGame,
      leaves: Vector[Operation],
      plannedSecrets: Option[Vector[(Move,
        OperationSecretPlanner.SecretSplit)]]
  ): Vector[OperationError] = {
    val initial = RunningBoards.initial(ready, plannedSecrets)
    val (reasons, _) = leaves.foldLeft[(Vector[OperationError],
      RunningBoards)]((Vector.empty, initial)) {
      case ((result, state), Flip(id, at, orientation)) =>
        (result ++ CardFaceOperations.flipViolation(ready, id, at, orientation),
          state)
      case ((result, state), FlipSecrets(player, amount, from, to)) =>
        val (violations, faceUp, faceDown) =
          ResourceOperations.flipSecretsViolation(ready, player, amount,
            from, to, state.faceUp, state.faceDown)
        (result ++ violations, state.copy(faceUp = faceUp, faceDown = faceDown))
      case ((result, state), Peek(viewer, id, at)) =>
        (result ++ CardFaceOperations.peekViolation(ready, viewer, id, at), state)
      case ((result, state), SpendSupply(player, amount, _)) =>
        val (violations, supply) = TurnStateOperations.supplyViolation(
          ready, player, -amount, state.supply)
        (result ++ violations, state.copy(supply = supply))
      case ((result, state), GainSupply(player, amount)) =>
        val (violations, supply) = TurnStateOperations.supplyViolation(
          ready, player, amount, state.supply)
        (result ++ violations, state.copy(supply = supply))
      case ((result, state), AdvanceVisionsDrawn) =>
        (result ++ TurnStateOperations.visionsDrawnViolation(ready), state)
      case ((result, state), _) => (result, state)
    }
    reasons
  }

  private def applyNonMoveLeaves(
      ready: ReadyGame,
      leaves: Vector[Operation]
  ): Either[OperationError, ReadyGame] =
    leaves.foldLeft[Either[OperationError, ReadyGame]](Right(ready)) {
      case (result, Flip(id, at, orientation)) =>
        result.flatMap(CardFaceOperations.flipCard(_, id, at, orientation))
      case (result, FlipSecrets(player, amount, from, to)) =>
        result.flatMap(ResourceOperations.flipPlayerSecrets(
          _, player, amount, from, to))
      case (result, Peek(viewer, id, at)) =>
        result.flatMap(CardFaceOperations.peek(_, viewer, id, at))
      case (result, SpendSupply(player, amount, _)) =>
        result.flatMap(TurnStateOperations.adjustSupply(_, player, -amount))
      case (result, GainSupply(player, amount)) =>
        result.flatMap(TurnStateOperations.adjustSupply(_, player, amount))
      case (result, AdvanceVisionsDrawn) =>
        result.flatMap(TurnStateOperations.advanceVisionsDrawn)
      case (result, ModifyDicePool(pool, delta, _)) =>
        result.flatMap(TurnStateOperations.adjustDicePool(_, pool, delta))
      case (result, ModifyRollOutcome(pool, skulls, score)) =>
        result.map(TurnStateOperations.modifyRollOutcome(_, pool, skulls, score))
      case (result, RecordPowerUse(power)) =>
        result.map(TurnStateOperations.recordPowerUse(_, power))
      case (result, EnterPhase(phase)) =>
        result.flatMap(TurnStateOperations.enterPhase(_, phase))
      case (result, SetOathkeeper(holder)) =>
        result.flatMap(TurnStateOperations.setOathkeeper(_, holder))
      case (result, RecordCampaignResult(fact)) =>
        result.map(TurnStateOperations.recordCampaignResult(_, fact))
      case (result, BeginTurn(player, phase)) =>
        result.flatMap(TurnStateOperations.beginTurn(_, player, phase))
      case (result, _) => result
    }
}
```

Cut `private final case class RunningBoards(...)` and `private object RunningBoards { ... }` out of `OperationShape` and paste them in place of the comment.

- [ ] **Step 4: Rewrite `OperationResolution`**

Replace the `resolve` signature and its first line, and the `optional` signature and its one validator use:

```scala
  /** Every reason for `requested`: allowlist first, then shape, then the
    * contextual restrictions. Allowlist reasons come first because the
    * retired executor ran the per-action policy before any shape or mutation
    * check, so a both-fail operation rejects with `RestrictedOperation`.
    */
  private[gameplay] def reasons(ready: ReadyGame, requested: CoreOperation,
      allowlist: OperationPolicy, restrictions: Vector[OperationRestriction])
      : Vector[OperationReason] =
    allowlistReasons(ready, requested, allowlist) ++
      resolvedReasons(ready, requested, restrictions)

  /** Revalidation after a permitted operation shrinks must not re-run an
    * exact allowlist against a different amount.
    */
  private def resolvedReasons(ready: ReadyGame, operation: CoreOperation,
      restrictions: Vector[OperationRestriction]): Vector[OperationReason] =
    OperationApplication.validate(ready, operation) ++
      restrictions.flatMap(_.reason(ready, operation))

  private def allowlistReasons(ready: ReadyGame, operation: CoreOperation,
      allowlist: OperationPolicy): Vector[OperationReason] =
    allowlist.validate(ready, operation) match {
      case Left(error) => Vector(OperationReason(error.code, error.detail))
      case Right(_) => Vector.empty
    }

  def resolve(ready: ReadyGame, requested: CoreOperation,
      allowlist: OperationPolicy, restrictions: Vector[OperationRestriction],
      requireAll: Boolean = false): Either[OathViolation, Result] = {
    val requestedReasons = reasons(ready, requested, allowlist, restrictions)
    val invalid = requestedReasons.find(_.kind == OperationReasonKind.Invalid)
    invalid match {
      case Some(reason) => Left(rejection(reason))
      case None if requested.required || requireAll =>
        requestedReasons.headOption match {
          case Some(reason) => Left(rejection(reason))
          case None => Right(Execute(requested))
        }
      case None => optional(ready, requested, restrictions, requestedReasons)
    }
  }

  private def optional(ready: ReadyGame, requested: CoreOperation,
      restrictions: Vector[OperationRestriction],
      reasons: Vector[OperationReason]): Either[OathViolation, Result] = {
```

Inside `optional`, replace `val candidateReasons = validator.validateResolvedOne(ready, candidate)` with `val candidateReasons = resolvedReasons(ready, candidate, restrictions)`. The rest of the file is unchanged.

- [ ] **Step 5: Rewire `OperationPipeline`**

In `run`, delete `val validator = new OperationValidator(allowlist, restrictions)` and change the resolve call to:

```scala
              OperationResolution.resolve(current.state, prepared, allowlist,
                restrictions, requireAll)
```

Replace the object doc's first paragraph with:

```scala
/** Sole public orchestrator of an operation batch. It folds each operation
  * through settlement, staged resolution ([[OperationResolution]], which
  * concatenates the caller's `allowlist` policy, the shape guard and the
  * restrictions vector) and the [[OperationExecutor]], applies the owning
  * procedure's direct `update`, and runs the post-state invariant.
```

- [ ] **Step 6: Rewire `OperationExecutor`**

Change `execute` to:

```scala
  def execute(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, ReadyGame] =
    OperationError
      .describe(OperationApplication.mutate(ready, operation))
      .flatMap(identity)
```

Replace the class doc with:

```scala
/** Applies one operation (or a raw fold of several) through
  * [[OperationApplication.mutate]], which runs the shape guard before any
  * family mutation. [[OperationPipeline]] owns best-effort resolution and the
  * post-state invariant; replay reaches the same guard by calling this class
  * directly. The describe guard converts constructor failures thrown by a
  * mutation into typed [[OperationError]] rejections.
  */
```

Also fix the `OperationPolicy.exact` doc line `Structural checks belong to [[OperationShape]].` to `Structural checks belong to [[OperationApplication]].`

- [ ] **Step 7: Delete the forwarder and the two old files**

In `OperationStateAdapter.scala` delete:

```scala
  private[operations] def applyOperation(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, ReadyGame] =
    OperationStateMutation.applyOperation(ready, operation)
```

Then:

```bash
git rm src/main/scala/oathdigital/gameplay/operations/OperationValidator.scala src/main/scala/oathdigital/gameplay/operations/OperationStateMutation.scala
```

Before deleting, confirm each holds nothing but what this task has already re-homed: `OperationValidator.scala` should contain only the `OperationValidator` class, `OperationShape.validate`, `reason`, `violations`, `RunningBoards` and `nonMoveViolations`; `OperationStateMutation.scala` only `applyOperation`, `mutate` and `applyNonMoveLeaves`. Anything else still there belongs to an earlier task and must be moved first.

- [ ] **Step 8: Rewrite the validator suite as `OperationApplicationSuite`**

```bash
git mv src/test/scala/oathdigital/gameplay/OperationValidatorSuite.scala src/test/scala/oathdigital/gameplay/OperationApplicationSuite.scala
```

Replace its contents with:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

/** The shape guard through the two interfaces that cross it: the resolver's
  * `OperationApplication.validate`, which reports every reason, and
  * `OperationPipeline.run`, whose rejection is the first of them.
  */
class OperationApplicationSuite extends munit.FunSuite {
  private val blueId = PlayerId("player-blue")
  private val blueLineage = LineageId("blue")
  private val redForce = ForceKind.Exile(lineageId)
  private val extraDenizen = DenizenId("D5")

  private val bluePlayer = PlayerState(
    blueId,
    blueLineage,
    Some(sites(1)),
    PlayerBoardState(2, 1, 0, 2, SupplyTrack.full),
    Vector.empty,
    Vector.empty,
    None
  )

  private val ready = {
    val current = game.current.copy(
      players = game.current.players :+ bluePlayer,
      banners = game.current.banners.copy(
        peoplesFavor = game.current.banners.peoplesFavor.copy(
          holder = Some(playerId)))
    )
    val campaign = game.campaign.copy(lineages = game.campaign.lineages.updated(
      blueLineage,
      LineageState(blueLineage, Some(blueId), Role.Exile,
        Vector.empty, Vector.empty)
    ))
    ReadyGames.of(game.copy(campaign = campaign, current = current))
  }

  private val codes = (values: Vector[OperationReason]) =>
    values.map(_.code)

  private def rejection(state: ReadyGame, operation: CoreOperation,
      allowlist: OperationPolicy = OperationPolicy.Permissive): String =
    OperationPipeline.run(state, Vector(operation), allowlist)(Right(_)) match {
      case Left(OathViolation.CoreOperationRejected(code, _)) => code
      case other => fail(s"expected a CoreOperationRejected, got $other")
    }

  test("short favor bank rejects the drawn amount as insufficient-pieces") {
    val operation = Gain.Favor(playerId, Suit.Order, 7)
    val reasons = OperationApplication.validate(ready, operation)

    assertEquals(reasons.map(_.code), Vector("insufficient-pieces"))
    assertEquals(reasons.head.detail, "favor bank contains 5 of requested favor")
    assertEquals(reasons.head.kind, OperationReasonKind.Impossible)
  }

  test("wrong requested stack source position is an invalid-stack-position") {
    val source = ready.updateCurrent(_.copy(commonCards = ready.game.current.commonCards.copy(
        worldDeck = Vector(worldDenizen, extraDenizen))))
    val operation = Move(
      Piece.Card(extraDenizen),
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Top),
      PositionedLocation(Location.Hand(playerId))
    )

    val reasons = OperationApplication.validate(source, operation)
    assertEquals(reasons.map(_.code), Vector("invalid-stack-position"))
    assertEquals(reasons.head.detail,
      "card does not match requested stack position")
    assertEquals(reasons.head.kind, OperationReasonKind.Invalid)
    assertEquals(rejection(source, operation), "invalid-stack-position")
  }

  test("one operation moving the same card twice is a conflicting-deltas") {
    val operation = Draw(playerId, Vector(worldDenizen, worldDenizen),
      Location.Deck(CardDeck.World), Location.Hand(playerId))

    val reasons = OperationApplication.validate(ready, operation)
    assertEquals(reasons.headOption.map(_.code), Some("conflicting-deltas"))
    assertEquals(reasons.head.detail,
      "one operation moves the same card more than once")
  }

  test("orienting an edifice into a site is an unsupported-orientation") {
    val edifice = EdificeId("E2")
    val source = ready.updateCurrent(_.copy(commonCards = ready.game.current.commonCards.copy(
        edificeDeck = Vector(edifice))))
    val operation = Move(
      Piece.Card(edifice),
      PositionedLocation(Location.Deck(CardDeck.Edifice), StackPosition.Top),
      PositionedLocation(Location.Site(sites.head)),
      Some(Orientation.FaceUp)
    )

    assertEquals(codes(OperationApplication.validate(source, operation)),
      Vector("unsupported-orientation"))
    assertEquals(rejection(source, operation), "unsupported-orientation")
  }

  test("warband moves from an undefined bounded supply are rejected") {
    val missing = ready.copy(banks = ready.banks.copy(
      warbandSupply = ready.banks.warbandSupply - redForce))

    assertEquals(codes(OperationApplication.validate(missing,
      Gain.Warbands(playerId, redForce, 1))),
      Vector("unknown-warband-supply"))
  }

  test("counted move into an incompatible destination is invalid") {
    val operation = Move(Piece.Favor(7),
      PositionedLocation(Location.FavorBank(Suit.Order)),
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Top))
    val reasons = OperationApplication.validate(ready, operation)
    assert(reasons.exists(_.kind == OperationReasonKind.Impossible))
    assert(reasons.exists(_.kind == OperationReasonKind.Invalid))
  }

  test("a supply spend beyond the track is an insufficient-supply") {
    assertEquals(codes(OperationApplication.validate(ready,
      SpendSupply(playerId, 8))), Vector("insufficient-supply"))
    assertEquals(rejection(ready, SpendSupply(playerId, 8)),
      "insufficient-supply")
  }

  test("an already held banner cannot be claimed from the shared bank") {
    val claim = Move(Piece.Banner(Banner.PeoplesFavor),
      PositionedLocation(Location.SharedBank),
      PositionedLocation(Location.PlayArea(blueId)))

    assertEquals(codes(OperationApplication.validate(ready, claim)),
      Vector("insufficient-pieces"))
  }

  test("moving a pawn that is not at the source site is a missing-piece") {
    val operation = Move(Piece.Pawn(playerId),
      PositionedLocation(Location.Site(sites(2))),
      PositionedLocation(Location.Site(sites(3))))

    assertEquals(codes(OperationApplication.validate(ready, operation)),
      Vector("missing-piece"))
    assertEquals(rejection(ready, operation), "missing-piece")
  }

  test("a pawn with no prior site may move from the player area to a site") {
    val unplaced = ready.updateCurrent(_.copy(players = ready.game.current.players.map {
      player => if (player.player == playerId) player.copy(pawnSite = None) else player
    }))
    val operation = Move(Piece.Pawn(playerId),
      PositionedLocation(Location.PlayArea(playerId)),
      PositionedLocation(Location.Site(sites.head)))

    assertEquals(codes(OperationApplication.validate(unplaced, operation)),
      Vector.empty)
  }

  test("a pawn already on a site cannot move from the player area again") {
    val operation = Move(Piece.Pawn(playerId),
      PositionedLocation(Location.PlayArea(playerId)),
      PositionedLocation(Location.Site(sites.head)))

    assertEquals(codes(OperationApplication.validate(ready, operation)),
      Vector("missing-piece"))
  }

  test("pipeline rejection matches the first shape reason byte-for-byte") {
    val corpus = Vector[CoreOperation](
      Gain.Favor(playerId, Suit.Order, 7),          // insufficient-pieces
      Gain.Favor(playerId, Suit.Order, 1),          // valid
      SpendSupply(playerId, 8),                   // insufficient-supply
      Move(Piece.Pawn(playerId),
        PositionedLocation(Location.Site(sites(2))),
        PositionedLocation(Location.Site(sites(3)))) // missing-piece
    )
    val raw = new OperationExecutor
    corpus.foreach { operation =>
      val reasons = OperationApplication.validate(ready, operation)
      val pipeline = OperationPipeline.run(ready, Vector(operation),
        OperationPolicy.Permissive)(Right(_))
      (reasons.headOption, pipeline) match {
        case (None, Right(after)) =>
          assertEquals(after.state.game,
            raw.execute(ready, operation).toOption.get.game)
        case (Some(reason), Right(after))
            if reason.kind == OperationReasonKind.Impossible &&
              !operation.required =>
          assert(after.executed.nonEmpty || after.skipped.nonEmpty)
        case (Some(reason), Left(violation)) =>
          val code = violation match {
            case OathViolation.CoreOperationRejected(code, _) => code
            case other => fail(s"unexpected violation $other")
          }
          assertEquals(code, reason.code)
        case (Some(reason), Right(_)) =>
          fail(s"expected rejection for ${reason.code}")
        case (None, Left(violation)) =>
          fail(s"unexpected rejection $violation")
      }
    }
  }

  test("the raw executor rejects what the shape guard rejects") {
    val raw = new OperationExecutor
    val operation = Move(Piece.Pawn(playerId),
      PositionedLocation(Location.Site(sites(2))),
      PositionedLocation(Location.Site(sites(3))))
    assertEquals(raw.execute(ready, operation).left.map(_.code),
      Left("missing-piece"))
  }

  test("allowlist precedes shape so a both-fail operation reports restricted") {
    val operation = Gain.Favor(playerId, Suit.Order, 7) // shape-insufficient
    val allowlist = OperationPolicy.exact(Vector.empty,
      "test batch not permitted")
    val reasons = OperationResolution.reasons(ready, operation, allowlist,
      Vector.empty)
    assertEquals(reasons.head.code, "restricted-operation")
    assertEquals(rejection(ready, operation, allowlist), "restricted-operation")
  }

  test("restriction registry is checked for each operation") {
    val blocking = new OperationRestriction {
      override def reason(ready: ReadyGame, operation: CoreOperation) =
        Some(OperationReason("power-blocked", "test predicate",
          OperationReasonKind.Impossible))
    }
    val operation = Gain.Favor(playerId, Suit.Order, 1)
    assertEquals(OperationResolution.reasons(ready, operation,
      OperationPolicy.Permissive, Vector(blocking)).map(_.code),
      Vector("power-blocked"))
  }
}
```

- [ ] **Step 9: Re-point `OperationResolutionSuite`**

Apply these edits in `src/test/scala/oathdigital/gameplay/OperationResolutionSuite.scala`:

1. Delete the two-line field
   ```scala
     private val validator = new OperationValidator(OperationPolicy.Permissive,
       Vector.empty)
   ```
2. Every call of the form `OperationResolution.resolve(<state>, <operation>, validator)` becomes `OperationResolution.resolve(<state>, <operation>, OperationPolicy.Permissive, Vector.empty)`. Mechanically:
   ```bash
   sed -i '' 's/, validator)/, OperationPolicy.Permissive, Vector.empty)/g' src/test/scala/oathdigital/gameplay/OperationResolutionSuite.scala
   ```
3. In the test that builds `mixedValidator`: replace
   ```scala
       val mixedValidator = new OperationValidator(OperationPolicy.Permissive,
         Vector(invalidRestriction))
       val mixedReasons = mixedValidator.validateOne(ready, impossibleAndInvalid)
   ```
   with
   ```scala
       val mixed = Vector[OperationRestriction](invalidRestriction)
       val mixedReasons = OperationResolution.reasons(ready, impossibleAndInvalid,
         OperationPolicy.Permissive, mixed)
   ```
   and `OperationResolution.resolve(ready, impossibleAndInvalid, mixedValidator)` with `OperationResolution.resolve(ready, impossibleAndInvalid, OperationPolicy.Permissive, mixed)`.
4. In the discard-restriction test: replace
   ```scala
       val reasons = new OperationValidator(OperationPolicy.Permissive,
         Vector(restriction)).validateOne(changed, discard)
   ```
   with
   ```scala
       val reasons = OperationResolution.reasons(changed, discard,
         OperationPolicy.Permissive, Vector(restriction))
   ```
   replace `new OperationValidator(OperationPolicy.Permissive, Vector(restriction)))` (inside the `resolve` call) with `OperationPolicy.Permissive, Vector(restriction))`, and replace
   ```scala
         new OperationValidator(OperationPolicy.Permissive,
           Vector(rulerRestriction))),
   ```
   with
   ```scala
         OperationPolicy.Permissive, Vector(rulerRestriction)),
   ```
5. In the last test: replace `validator.validateOne(ready, stale)` with `OperationResolution.reasons(ready, stale, OperationPolicy.Permissive, Vector.empty)` and `validator.validateOne(ready, understated)` with `OperationResolution.reasons(ready, understated, OperationPolicy.Permissive, Vector.empty)`.

Then `grep -n 'validator\|OperationValidator' src/test/scala/oathdigital/gameplay/OperationResolutionSuite.scala` must print nothing.

- [ ] **Step 10: Rename `OperationStateMutationSuite`**

```bash
git mv src/test/scala/oathdigital/gameplay/operations/OperationStateMutationSuite.scala src/test/scala/oathdigital/gameplay/operations/OperationMutationSuite.scala
sed -i '' 's/class OperationStateMutationSuite/class OperationMutationSuite/' src/test/scala/oathdigital/gameplay/operations/OperationMutationSuite.scala
```

- [ ] **Step 11: Compile, run the replay test green, run the operation and walker suites**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.WalkerReplayGuardSuite"`
Expected: PASS, 2 tests.

Run: `./sbtw "testOnly oathdigital.gameplay.OperationApplicationSuite oathdigital.gameplay.OperationResolutionSuite oathdigital.gameplay.OperationPipelineSuite oathdigital.gameplay.OperationExecutorSuite oathdigital.gameplay.OperationVocabularySuite oathdigital.gameplay.PowerOperationsSuite oathdigital.gameplay.SupplyAdjustSuite oathdigital.gameplay.operations.* oathdigital.gameplay.ProcedureWalkerSuite oathdigital.gameplay.WalkerReplayDriftSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS. `BackendArchitectureSuite` in particular confirms no new file names a power.

- [ ] **Step 12: Run the whole backend suite**

Run: `./sbtw test`
Expected: PASS. If a suite outside `operations` fails on a rejection code, the guard order in `OperationApplication.violations` or a family's moved body differs from the original; diff against `git show 8ba502d:src/main/scala/oathdigital/gameplay/operations/OperationValidator.scala`.

- [ ] **Step 13: Commit**

```bash
git add -A src/main/scala/oathdigital/gameplay/operations src/test/scala/oathdigital/gameplay
git commit -m "refactor(operations): one guard set for the pipeline and replay

OperationApplication owns the two dispatchers. mutate runs the aggregated
shape guard as its precondition, so OperationExecutor -- and through it
WalkerReplay -- rejects what OperationPipeline rejects. OperationValidator
and OperationStateMutation are dissolved into the family files; the 45-line
validator class folds into OperationResolution, which now takes the
allowlist and restrictions directly.

The one replay behaviour change is pinned by WalkerReplayGuardSuite: a
recorded card move into a regional discard with no stack position used to
replay to a state missing the card; it now fails replay with the same
invalid-stack-position the live pipeline produces.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 10: Delete the mutation-side re-checks the guard now makes unreachable

**Files:**
- Modify: `BoardControlOperations.scala`, `CardFaceOperations.scala`, `ResourceOperations.scala`, `TurnStateOperations.scala`

**Interfaces:**
- Consumes: `OperationApplication.mutate` runs `violations` before any family mutation (Task 9).
- Produces: nothing new. Every deleted check is one the aggregated guard already performs on the same state.

Each site below is an explicit `Either.cond` or `if` re-checking a fact the shape guard established. Total-match fallbacks (`case _ => Left(...)`) are not re-checks and stay. `requireFinite` loses its last caller here and is deleted.

- [ ] **Step 1: `BoardControlOperations.movePawn`**

Replace

```scala
      case (Location.PlayArea(source), Location.Site(destination))
          if source == player =>
        playerState(ready, player).flatMap { state =>
          Either.cond(state.pawnSite.isEmpty, (),
            MissingPiece(Piece.Pawn(player), from)).flatMap { _ =>
            siteState(ready, destination).flatMap(_ =>
              updatePlayer(ready, player)(_.copy(pawnSite = Some(destination))))
          }
        }
      case (Location.Site(source), Location.Site(destination)) =>
        playerState(ready, player).flatMap { state =>
          Either.cond(state.pawnSite.contains(source), (),
            MissingPiece(Piece.Pawn(player), from)).flatMap { _ =>
            siteState(ready, destination).flatMap(_ =>
              updatePlayer(ready, player)(_.copy(pawnSite = Some(destination))))
          }
        }
```

with

```scala
      // Whether the pawn is where `from` says is the guard's
      // (pawnMoveViolation); by the time this runs it is.
      case (Location.PlayArea(source), Location.Site(destination))
          if source == player =>
        siteState(ready, destination).flatMap(_ =>
          updatePlayer(ready, player)(_.copy(pawnSite = Some(destination))))
      case (Location.Site(_), Location.Site(destination)) =>
        siteState(ready, destination).flatMap(_ =>
          updatePlayer(ready, player)(_.copy(pawnSite = Some(destination))))
```

`updatePlayer` still rejects an unknown player, so no `UnknownPlayer` path is lost.

- [ ] **Step 2: `BoardControlOperations.moveBanner`**

Replace

```scala
      case (Location.PlayArea(source), Location.PlayArea(destination)) =>
        for {
          _ <- playerState(ready, destination)
          _ <- Either.cond(bannerHolder(ready, banner).contains(source), (),
            MissingPiece(Piece.Banner(banner), from))
        } yield setBannerHolder(ready, banner, destination)
      case (Location.SharedBank, Location.PlayArea(destination)) =>
        for {
          _ <- playerState(ready, destination)
          available <- quantity(ready, Piece.Banner(banner), from)
          _ <- requireFinite(Piece.Banner(banner), from, available, 1)
        } yield setBannerHolder(ready, banner, destination)
```

with

```scala
      // Who holds the banner is the guard's (bannerMoveViolation).
      case (Location.PlayArea(_), Location.PlayArea(destination)) =>
        playerState(ready, destination).map(_ =>
          setBannerHolder(ready, banner, destination))
      case (Location.SharedBank, Location.PlayArea(destination)) =>
        playerState(ready, destination).map(_ =>
          setBannerHolder(ready, banner, destination))
```

Delete `private def requireFinite(...)` from the file, and drop `bannerHolder` and `quantity` from the `OperationStateAdapter` import if nothing else in the file uses them (the guard's `bannerMoveViolation` still uses `bannerHolder` through `pawnAndBannerViolations`; keep whatever the compiler needs).

- [ ] **Step 3: `ResourceOperations.flipPlayerSecrets`**

Replace

```scala
    playerState(ready, player).flatMap { state =>
      val available = from match {
        case SecretSide.FaceUp => state.board.faceUpSecrets
        case SecretSide.FaceDown => state.board.faceDownSecrets
      }
      Either.cond(available >= amount, (), InsufficientPieces(
        Piece.Secrets(amount), Location.PlayArea(player), available)).flatMap { _ =>
        updatePlayer(ready, player) { value =>
          val faceUpDelta = (from, to) match {
            case (SecretSide.FaceUp, SecretSide.FaceDown) => -amount
            case (SecretSide.FaceDown, SecretSide.FaceUp) => amount
            case _ => 0
          }
          value.copy(board = value.board.copy(
            faceUpSecrets = value.board.faceUpSecrets + faceUpDelta,
            faceDownSecrets = value.board.faceDownSecrets - faceUpDelta))
        }
      }
    }
```

with

```scala
    // Sufficiency is the guard's (flipSecretsViolation).
    updatePlayer(ready, player) { value =>
      val faceUpDelta = (from, to) match {
        case (SecretSide.FaceUp, SecretSide.FaceDown) => -amount
        case (SecretSide.FaceDown, SecretSide.FaceUp) => amount
        case _ => 0
      }
      value.copy(board = value.board.copy(
        faceUpSecrets = value.board.faceUpSecrets + faceUpDelta,
        faceDownSecrets = value.board.faceDownSecrets - faceUpDelta))
    }
```

- [ ] **Step 4: `ResourceOperations.adjustWarbands`**

Replace the `PlayArea` arm

```scala
    case Location.PlayArea(player) => updatePlayer(ready, player) { state =>
      state.copy(board = state.board.copy(warbands = state.board.warbands + delta))
    }.flatMap { updated =>
      quantity(updated, Piece.Warbands(kind, 1), Location.PlayArea(player))
        .map(_ => updated)
    }
```

with

```scala
    // Kind compatibility and sufficiency are the guard's
    // (warbandSourceViolations, countedDestinationViolations).
    case Location.PlayArea(player) => updatePlayer(ready, player) { state =>
      state.copy(board = state.board.copy(warbands = state.board.warbands + delta))
    }
```

and in the `Site` arm replace

```scala
          val next = current + delta
          Either.cond(next >= 0, (), InsufficientPieces(
            Piece.Warbands(kind, math.max(1, -delta)), at, current)).flatMap { _ =>
            updateSite(ready, site)(_.copy(forces =
              if (next == 0) SiteForces.Empty
              else SiteForces.Occupied(kind, next)))
          }
```

with

```scala
          val next = current + delta
          updateSite(ready, site)(_.copy(forces =
            if (next == 0) SiteForces.Empty
            else SiteForces.Occupied(kind, next)))
```

The `ConflictingDeltas("site cannot contain multiple force kinds")` arm above it stays: the guard reads a foreign-kind site as holding zero and only rejects a debit, so a credit onto a site held by another kind is caught here and nowhere else.

- [ ] **Step 5: `CardFaceOperations.peek`**

Replace

```scala
    _ <- playerState(ready, viewer)
    _ <- Either.cond(id.isInstanceOf[WorldCardId] || id.isInstanceOf[RelicId],
      (), IncompatibleLocation(Piece.Card(id), at))
    located <- card(ready, id, at)
```

with

```scala
    // The card kind is the guard's (peekViolation).
    _ <- playerState(ready, viewer)
    located <- card(ready, id, at)
```

- [ ] **Step 6: `TurnStateOperations.adjustSupply`**

Replace

```scala
    playerState(ready, player).flatMap { state =>
      val current = state.board.supply.supply
      if (amount < 0) {
        val required = -amount
        Either.cond(current >= required, (), InsufficientSupply(
          required, current)).flatMap { _ =>
          updatePlayer(ready, player)(value => value.copy(
            board = value.board.copy(supply = SupplyTrack(current - required))))
        }
      } else
        updatePlayer(ready, player)(value => value.copy(
          board = value.board.copy(supply = SupplyTrack(math.min(
            SupplyTrack.Maximum, current + amount)))))
    }
```

with

```scala
    // Sufficiency is the guard's (supplyViolation).
    playerState(ready, player).flatMap { state =>
      val current = state.board.supply.supply
      val next = if (amount < 0) current + amount
        else math.min(SupplyTrack.Maximum, current + amount)
      updatePlayer(ready, player)(value => value.copy(
        board = value.board.copy(supply = SupplyTrack(next))))
    }
```

- [ ] **Step 7: `TurnStateOperations.advanceVisionsDrawn`**

Replace the body with

```scala
  def advanceVisionsDrawn(ready: ReadyGame): Either[OperationError, ReadyGame] = {
    // Overflow is the guard's (visionsDrawnViolation).
    val current = ready.game.current
    Right(ready.copy(game = ready.game.copy(current = current.copy(
      tracks = current.tracks.copy(
        visionsDrawn = current.tracks.visionsDrawn + 1)))))
  }
```

- [ ] **Step 8: Compile and run every suite that exercises these operations**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationApplicationSuite oathdigital.gameplay.OperationExecutorSuite oathdigital.gameplay.OperationPipelineSuite oathdigital.gameplay.OperationResolutionSuite oathdigital.gameplay.SupplyAdjustSuite oathdigital.gameplay.operations.* oathdigital.gameplay.walker.WalkerReplayGuardSuite oathdigital.gameplay.ProcedureWalkerSuite"`
Expected: PASS. `OperationExecutorSuite` "Visions Drawn advances once and rejects integer overflow", "later failure returns no partial execution result", "a held banner cannot be claimed from the shared bank" and `OperationMutationSuite` "a pawn already on a site cannot move from the player area again" are the ones that would catch a deleted check the guard does not cover.

- [ ] **Step 9: Run the whole backend suite**

Run: `./sbtw test`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/operations
git commit -m "refactor(operations): drop the mutation re-checks the guard makes unreachable

Seven explicit sufficiency and placement checks duplicated a shape guard that
now runs before any family mutation. requireFinite loses its last caller.
Mutation-only guards (site force-kind conflicts, empty-token and revealed-slot
checks, phase, title and turn writes) stay.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 11: Docs, the deferred item, and the full gate

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/travel/TravelProcedure.scala` (doc comment)
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Fix the stale comment**

In `TravelProcedure.scala` change `the transformed \`SpendSupply\` and \`OperationValidator\`, and because the` to `the transformed \`SpendSupply\` and the operation guard, and because the`.

- [ ] **Step 2: Record the deferred seam merge**

In `docs/ROADMAP.md`, after the `### Powers-related deferred items` list, add:

```markdown
### Engine deferred items

- [ ] **Deferred: one seam for "who may veto an operation".** `OperationPolicy`
  (an exact-shape allowlist, used by `StateBasedOperationPolicy` and
  `MinorActionOperationPolicy` on the legacy-event paths) and
  `OperationRestriction` (contextual reasons from powers) answer the same
  question through two interfaces. Migrating the two policies onto
  `OperationRestriction` would leave one seam, but it reorders precedence on
  the legacy-event path (allowlist reasons come before shape reasons today)
  and needs its own preservation argument. Recorded during the
  [operation family consolidation](superpowers/specs/2026-09-24-operation-family-consolidation-design.md).
```

- [ ] **Step 3: Confirm nothing references the dissolved names**

Run: `grep -rn 'OperationShape\|OperationStateMutation\b\|OperationCardMutation\|OperationValidator\b' src frontend/src shared/src scripts --include='*.scala' --include='*.py'`
Expected: no output. (Historical specs and plans under `docs/` keep their references; they are records.)

- [ ] **Step 4: Run the full gate**

Run: `./sbtw test`
Expected: PASS.

Run: `./sbtw frontend/test`
Expected: PASS (nothing in `frontend/` changed; this is the repository gate).

Run: `python3 scripts/check-architecture.py`
Expected: `architecture check passed: N production Scala files`.

Run: `wc -l src/main/scala/oathdigital/gameplay/operations/*.scala`
Expected: every file under 500 lines.

Optional, because no CI workflow runs it but `build.sbt` pins
`coverageMinimumStmtTotal := 84.0` with `coverageFailOnMinimum := true`:

Run: `./sbtw coverage test coverageReport`
Expected: statement coverage at or above 84.0%. This slice deletes covered
dead code and adds tests, so it should not move the ratio down; if it does,
the deleted re-checks in Task 10 are the place to look for a test that no
longer reaches anything.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/travel/TravelProcedure.scala docs/ROADMAP.md
git commit -m "docs: record the deferred OperationPolicy seam merge and fix a stale comment

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```
