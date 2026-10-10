# Catalog Batch 7, Slice 1 (Rerolls) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After every roll in the engine, let the roller reroll dice with the powers that allow it. Implement the two first such powers:

- Jinx (68): its ruler pays one secret to reroll every die of a throw.
- Master at Arms (249): an attacker battle plan that rerolls hollow swords of the attack roll.

**Architecture:**

- **A reroll operation.** `Reroll(pool, indices, faces)` replaces faces of a pool's recorded outcome. A tree declares it without faces. The walker fills them from its dice source, as it fills a `Shuffle`'s order, so the journal records them and replay applies them.
- **Reroll offers are their own contribution.** A power offers a reroll through `OfferReroll` at the new `PowerWindow.Reroll`, the way a battle plan offers itself through `Offer`. The walker hands the offers to a `RerollHost`, which shares `OfferHost.Pass` with the battle-plan host.
- **The roll step.** `RollStep(pool, dice, roller, window)` builds the `Roll` and then a `Repeat` of `RerollChoice` passes, modelled on the battle-plan window's `CampaignPlanChoice`.
  - Each pass asks the roller to pick a reroll or "Keep the roll", then pays for and runs the pick.
  - A pass with nothing usable asks nothing, which ends the `Repeat`.
  - A power picked on this throw is no longer offered.
  - All six roll sites use it.
- **The throw** is the last faces of the pool's outcome, as many as the pool holds dice. Recover's pool accumulates faces over its rolls, so each Recover roll is its own throw, numbered in its decision id.
- **The Game Log.** Each reroll writes its power's line and then restates the throw: "Rolled {faces} for the attack". The reroll decisions write no line of their own.
- **The roll panel.** A parked reroll decision shows its pool's dice, whatever procedure asks it.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these documents before starting:

- `docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md`: "Goal and scope", "The bar", "Slice 1: Rerolls", "Seams used for the first time" and "Testing".
- `docs/superpowers/specs/2026-10-09-catalog-batch-7-rulings.md`: "Rules that apply to the whole batch" and "Slice 1: Rerolls".

Task 1 amends both with the plan-time rulings, so read them again after Task 1.

**Starts from:** `main` at `26dce1e0` or later. Walker game loop slice 1 is merged there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails it.
  - Each file below lists the imports it needs.
  - A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). `ProcedureWalker.scala` is at 657 lines; this plan adds three.
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - No power name appears in walker sources.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them. Re-wrap any line in this plan that comes out longer.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Texts, exactly:**
  - Jinx's lines: "Jinx: Rerolled the attack dice." and "Jinx: Rerolled the defense dice."
  - Master at Arms' line: "Master at Arms: Rerolled 2 hollow swords." ("1 hollow sword." for one).
  - The reroll decision's heading: "Reroll the attack dice, or keep the roll" ("the defense dice" for a defense roll). Its button: "Keep the roll".
  - Master at Arms' amount heading: "Reroll how many hollow swords?", confirmed with "Reroll".
  - Master at Arms is unavailable on a throw with no hollow sword: "No die shows a hollow sword".
  - Jinx is unavailable without the secret: "Needs 1 secret; you have 0".
  - The line after each reroll: "Rolled {faces} for the attack" ("for the defense"; nothing after the faces for any other pool), as a roll line reads today.
  - Master at Arms' plan option label: "Master at Arms: reroll hollow swords".
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 35 server tests and no frontend test.
  - It changes one existing test: `WalkerOperationWireSuite`'s round trip of every operation takes two `Reroll`s.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

The product owner reviews these with the plan. Task 1 writes all of them into the spec.

1. **Reroll offers have their own contribution and host.** A battle plan's `Offer` carries a `CampaignPlanOffer`, which names a Campaign side and source. A reroll is offered on any roll, so it gets `OfferReroll` and `RerollHost` beside them. Both hosts read the same `OfferHost.Pass`. The design's "an `OfferHost` at `PowerWindow.Reroll`" means this host.
2. **The throw.** A throw is the last faces of the pool's outcome, as many as the pool holds dice. The reroll decision's id is `{pool}.reroll.{n}`, where `n` counts the pool's throws so far: `campaign.attack.reroll.1`, `recover.reroll.3`. A Campaign's ids therefore start with `campaign.`, as every Campaign decision does.
3. **Master at Arms asks how many, not which.** Every die showing a hollow sword shows the same face, so choosing which ones changes nothing. It asks "Reroll how many hollow swords?" from one to all of them, and rerolls the first that many. With one hollow sword it rerolls it without asking. On a throw with none it is shown unavailable, "No die shows a hollow sword".
4. **The Game Log after a reroll.** The roll's own line has already been shown, since the roller sees the dice before deciding. Each reroll writes its power's line, then a roll line restating the whole throw as it now stands: "Rolled {faces} for the attack". The reroll decisions write no line. Jinx's payment writes what any power's payment writes.
5. **Jinx needs a faceup card.** It is offered while the roller holds it as a faceup adviser, or it stands faceup at a site the roller rules. A facedown adviser has no power.
6. **What is asked.** The roller is asked only when some offer is usable. An offer that cannot be used, such as Jinx without a secret, is shown unavailable with its reason when the decision is asked for another offer.
7. **Hidden outcome.** A `Reroll` shows new dice, so the legality search stops at it and undo does not take it back, as with a `Roll`.
8. **Plans read rerolled faces.** Every step after the roll step, the Campaign's attack result included, reads the faces as rerolled.

## File map

**The reroll operation (Task 2):**
- `src/main/scala/oathdigital/model/CoreOperations.scala`: `Reroll`.
- `src/main/scala/oathdigital/model/DicePoolState.scala`: `RollOutcome.derive`.
- `src/main/scala/oathdigital/gameplay/operations/RollOperations.scala`: new, `reroll`.
- `src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala`: applies it.
- `src/main/scala/oathdigital/gameplay/walker/WalkerRolls.scala`: uses `RollOutcome.derive`.
- `src/main/scala/oathdigital/gameplay/walker/WalkerRerolls.scala`: new, fills the faces.
- `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`: runs a declared reroll.
- `src/main/scala/oathdigital/gameplay/walker/HiddenOutcomes.scala`: `Reroll` hides.
- `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala` and `WalkerEventCodec.scala`: the `reroll` kind.
- Tests: new `RollOperationsSuite` and `ProcedureWalkerRerollSuite`; extended `WalkerOperationWireSuite`.

**The roll step (Task 3):**
- `src/main/scala/oathdigital/model/PowerWindow.scala`: `Reroll`.
- `src/main/scala/oathdigital/model/RerollOffers.scala`: new, `RerollOffer` and `OfferedReroll`.
- `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`: `OfferReroll` and `RerollHost`.
- `src/main/scala/oathdigital/gameplay/powerresolver/ContributionCollector.scala`: gathers `rerolls`.
- `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`: expands a `RerollHost`.
- `src/main/scala/oathdigital/gameplay/actions/dice/DiceThrow.scala`, `RerollChoice.scala` and `RollStep.scala`: new.
- The six roll sites: `CampaignProcedure.scala`, `RecoverProcedure.scala`, `GamblingHall.scala`, `RelicThief.scala`, `BoneDice.scala` and `MurkyFountain.scala`.
- `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`: the reroll decision's roll panel.
- `src/main/scala/oathdigital/application/gamelog/DetailLines.scala`: the line after a reroll, and the silent decisions.
- Tests: new `RerollChoiceSuite` and `GameLogRerollSuite`, and the tests' own reroll power `FreeReroll`.

**Jinx (Task 4):**
- `src/main/scala/oathdigital/gameplay/powers/dice/Jinx.scala`: new. `JinxCard` moves out of `catalog/holding/HoldingArcaneDenizens.scala`.
- Registration: `WalkerPowerCatalog.scala`, and the import in `NewFoundations.scala`.
- Tests: new `JinxSuite`; one test each added to `GamblingHallSuite`, `RelicThiefSuite`, `BoneDiceSuite` and `MurkyFountainSuite`.

**Master at Arms (Task 5):**
- `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`: `otherContributions`.
- `src/main/scala/oathdigital/gameplay/powers/campaign/MasterAtArms.scala`: new. `MasterAtArmsCard` moves out of `catalog/holding/HoldingOrderDenizens.scala`.
- Registration: `PlanRules.scala`, and the import in `NewFoundations.scala`.
- Tests: new `MasterAtArmsSuite`.

**Roadmap (Task 6):** `docs/ROADMAP.md`.

---

### Task 1: Write the plan-time rulings into the spec

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md`
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-7-rulings.md`

- [ ] **Step 1: Amend the design**

1. Replace the **Status** paragraph with:

```markdown
**Status:** designed 2026-10-09. Slice 1 is planned; its plan-time rulings
are under "Slice 1". The per-card rulings are in
[the rulings appendix](2026-10-09-catalog-batch-7-rulings.md).
```

2. In "The bar", replace the first bullet (from `- **A roll step with rerolls** (slice 1).` to `Fountain.`) with:

```markdown
- **A roll step with rerolls** (slice 1). `RollStep` builds a `Roll`
  followed by a `Repeat` of `RerollChoice` passes. A `RerollChoice` is a
  `RerollHost` at a new `PowerWindow.Reroll`: the reroll counterpart of the
  battle-plan `OfferHost`, fed by the powers' `OfferReroll` contributions
  and reading the same `OfferHost.Pass`. All six roll sites use it: the
  Campaign's attack and defense rolls, Recover, Gambling Hall, Relic Thief,
  Bone Dice and Murky Fountain.
```

3. In "The bar", replace the second bullet (from `- **A reroll operation** (slice 1).` to `as it does an automatic roll's.`) with:

```markdown
- **A reroll operation** (slice 1). `Reroll(pool, indices, faces)`
  replaces the faces at `indices` in the pool's `RollOutcome`, re-deriving
  the skulls and the score. A tree declares it without faces; the walker
  fills them from its dice source, as it fills a `Shuffle`'s order, so the
  journal records them and replay applies them. It is a hidden outcome: the
  legality search stops at it, and undo does not take it back.
```

4. Append to "## Slice 1: Rerolls":

```markdown
### Plan-time rulings

- **The throw.** A throw is the last faces of the pool's outcome, as many
  as the pool holds dice. The reroll decision's id is `{pool}.reroll.{n}`,
  where `n` counts the pool's throws so far (`campaign.attack.reroll.1`,
  `recover.reroll.3`). Its heading reads "Reroll the attack dice, or keep
  the roll" ("the defense dice" for a defense roll), and its button "Keep
  the roll". A parked reroll decision shows its pool's dice.
- **What is asked.** The roller is asked only when some offer is usable.
  An unusable offer, such as Jinx without a secret ("Needs 1 secret; you
  have 0"), is shown unavailable when the decision is asked for another.
- **Master at Arms asks how many.** Every hollow sword is the same face,
  so it asks "Reroll how many hollow swords?", from one to all of them, and
  rerolls the first that many. With one hollow sword it asks nothing. On a
  throw with none it is shown unavailable: "No die shows a hollow sword".
- **Jinx needs a faceup card**: a faceup adviser of the roller, or a card
  faceup at a site the roller rules.
- **The Game Log.** The roll's own line is shown before the roller decides.
  Each reroll writes its power's line, then a roll line restating the whole
  throw: "Rolled {faces} for the attack". The reroll decisions write no
  line.
```

- [ ] **Step 2: Amend the rulings appendix**

1. In "## Rules that apply to the whole batch", replace the bullet `- **Log lines omit the actor.** A roll's own line shows its faces after any reroll.` (two lines) with:

```markdown
- **Log lines omit the actor.** A roll's own line shows the faces as
  rolled, since the roller sees them before deciding. Each reroll writes its
  power's line, then a roll line restating the throw as it now stands.
```

2. In the Master at Arms entry, replace `after the attack roll its user may reroll any number of the dice showing a hollow sword, once.` with:

```markdown
  after the attack roll its user may reroll any number of the dice showing
  a hollow sword, once. It asks how many, since the dice show the same face,
  and asks nothing when only one shows a hollow sword.
```

- [ ] **Step 3: Check the links and commit**

Run: `python3 scripts/check-markdown-links.py`
Expected: pass.

```bash
git add docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md docs/superpowers/specs/2026-10-09-catalog-batch-7-rulings.md
git commit -m "docs(batch7): record slice 1 plan-time rulings"
```

---

### Task 2: The reroll operation

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala` (after `ModifyRollOutcome`, near line 588)
- Modify: `src/main/scala/oathdigital/model/DicePoolState.scala`
- Create: `src/main/scala/oathdigital/gameplay/operations/RollOperations.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala` (`applyNonMoveLeaves`, near line 200)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerRolls.scala`
- Create: `src/main/scala/oathdigital/gameplay/walker/WalkerRerolls.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` (`runLeaf`, near line 615)
- Modify: `src/main/scala/oathdigital/gameplay/walker/HiddenOutcomes.scala`
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala` (the `TurnOperation` union near line 47, `encodePieceOperation` near line 247, the decoder near line 515)
- Modify: `src/main/scala/oathdigital/serialization/WalkerEventCodec.scala` (`encodeDieFace` near line 121, `decodeDieFace` near line 202)
- Test: `src/test/scala/oathdigital/gameplay/operations/RollOperationsSuite.scala`, `src/test/scala/oathdigital/gameplay/ProcedureWalkerRerollSuite.scala`, `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala`

**Interfaces:**
- Produces:
  - `Reroll(pool: PoolKey, indices: Vector[Int], faces: Option[Vector[DieFace]] = None)`, a `PrimitiveOperation` with `required = true`.
  - `RollOutcome.derive(faces: Vector[DieFace]): (Int, Int)`, the skulls and the score.
  - `RollOperations.reroll(ready: ReadyGame, pool: PoolKey, indices: Vector[Int], faces: Option[Vector[DieFace]]): Either[OperationError, ReadyGame]`.
  - `WalkerRerolls.filled(reroll: Reroll, state: ReadyGame, dice: WalkerDice): Either[OathViolation, Reroll]`.
  - The wire kind `"reroll"`, with `pool`, `indices` and optional `faces`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/operations/RollOperationsSuite.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.model._
import oathdigital.testkit.Table

/** `Reroll`: rerolled faces replace the old ones in place, and the skulls
  * and score are worked out again (catalog batch 7, slice 1). */
class RollOperationsSuite extends munit.FunSuite:
  private val pool = PoolKey("campaign.attack")
  import AttackDieFace.{HollowSword, OneSword, TwoSwordsSkull}

  /** The quiet table with `faces` recorded for the attack pool, which
    * holds as many dice. */
  private def rolled(faces: Vector[DieFace]): ReadyGame =
    val (skulls, score) = RollOutcome.derive(faces)
    Table.start.ready.updateCurrent(current => current.copy(
      rollPools = Map(pool -> DicePoolState(faces.size)),
      rollOutcomes = Map(pool ->
        RollOutcome(pool, faces.size, faces, skulls, score))))

  test("rerolled faces replace the chosen ones and rescore the roll"):
    val before = rolled(Vector(HollowSword, OneSword, HollowSword))
    val after = RollOperations.reroll(before, pool, Vector(0, 2),
      Some(Vector(TwoSwordsSkull, OneSword))).toOption.get
    val outcome = after.game.current.rollOutcomes(pool)
    assertEquals(outcome.faces,
      Vector[DieFace](TwoSwordsSkull, OneSword, OneSword))
    assertEquals(outcome.skulls, 1)
    assertEquals(outcome.score, 4)
    assertEquals(outcome.count, 3)

  test("a reroll keeps skulls another step already changed"):
    // A power lowered the skulls to 0 after the roll; rerolling a sword
    // into a skull adds one to what stands.
    val before = rolled(Vector(TwoSwordsSkull, OneSword))
    val lowered = before.updateCurrent(current => current.copy(
      rollOutcomes = current.rollOutcomes.updated(pool,
        current.rollOutcomes(pool).copy(skulls = 0))))
    val after = RollOperations.reroll(lowered, pool, Vector(1),
      Some(Vector(TwoSwordsSkull))).toOption.get
    assertEquals(after.game.current.rollOutcomes(pool).skulls, 1)

  test("a reroll without faces, of a missing pool, or out of range fails"):
    val before = rolled(Vector(HollowSword, OneSword))
    assert(RollOperations.reroll(before, pool, Vector(0), None).isLeft)
    assert(RollOperations.reroll(before, PoolKey("recover"), Vector(0),
      Some(Vector(OneSword))).isLeft)
    assert(RollOperations.reroll(before, pool, Vector(2),
      Some(Vector(OneSword))).isLeft)
    assert(RollOperations.reroll(before, pool, Vector(0, 0),
      Some(Vector(OneSword, OneSword))).isLeft)
    assert(RollOperations.reroll(before, pool, Vector(0),
      Some(Vector(OneSword, OneSword))).isLeft)
    assert(RollOperations.reroll(before, pool, Vector(0),
      Some(Vector(DefenseDieFace.Blank))).isLeft)
```

Create `src/test/scala/oathdigital/gameplay/ProcedureWalkerRerollSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.walker.{HiddenOutcomes, ProcedureWalker,
  WalkerOutcome, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.testkit.ProcedureWalkerFixture._

/** A declared `Reroll` on the walker: its faces come from the dice source
  * and ride the recorded operation, so replay applies them without rolling
  * (catalog batch 7, slice 1). */
class ProcedureWalkerRerollSuite extends munit.FunSuite:
  import DefenseDieFace.{Blank, TwoShields}

  private val tree: Operation = Sequence(
    ModifyDicePool(recoverPool, 2),
    Roll(recoverPool, DiceSpec(DiceKind.Defense), RollMode.Automatic),
    Reroll(recoverPool, Vector(1)))

  test("a declared reroll takes its faces from the dice source and " +
      "records them"):
    val dice = WalkerDiceFixture.scripted(Vector(Blank, Blank),
      Vector(TwoShields))
    ProcedureWalker.advance(ready, tree, None, noPowers, dice) match
      case Right(WalkerOutcome.Finished(end, events)) =>
        val recorded = events.collect { case step: WalkerStepRecorded =>
          step.ops }.flatten
        assert(recorded.contains(Reroll(recoverPool, Vector(1),
          Some(Vector(TwoShields)))))
        val outcome = end.game.current.rollOutcomes(recoverPool)
        assertEquals(outcome.faces, Vector[DieFace](Blank, TwoShields))
        assertEquals(outcome.score, 2)
      case other => fail(s"expected the tree to finish, got $other")

  test("a reroll is a hidden outcome"):
    assert(HiddenOutcomes.hides(Reroll(recoverPool, Vector(0))))
```

In `WalkerOperationWireSuite`, in the operation list of the test that holds `ModifyRollOutcome(PoolKey("recover"), Some(1), Some(2)),`, add after that line:

```scala
      Reroll(PoolKey("recover"), Vector(0, 2),
        Some(Vector(DefenseDieFace.TwoShields, DefenseDieFace.Blank))),
      Reroll(PoolKey("campaign.attack"), Vector(1)),
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.RollOperationsSuite oathdigital.gameplay.ProcedureWalkerRerollSuite oathdigital.serialization.WalkerOperationWireSuite"`
Expected: compilation fails: `Reroll`, `RollOutcome.derive` and `RollOperations` are not defined.

- [ ] **Step 3: Add the operation and `RollOutcome.derive`**

In `CoreOperations.scala`, after `ModifyRollOutcome`:

```scala
/** Rerolls the dice at `indices` of `pool`'s recorded outcome: the faces
  * at those indices are replaced by `faces`, in order, and the skulls and
  * the score are worked out again (catalog batch 7, slice 1).
  *
  * A tree declares it with no faces. The walker fills them from its dice
  * source before running it, so the journal records them and replay applies
  * them as they ran, as it applies a `Shuffle`'s order. A `BuildOps` batch
  * is run as it is built, so a reroll inside one is never filled. Applying
  * a reroll with no faces is an error.
  */
final case class Reroll(pool: PoolKey, indices: Vector[Int],
    faces: Option[Vector[DieFace]] = None) extends PrimitiveOperation:
  override val required: Boolean = true
```

In `DicePoolState.scala`, after `final case class RollOutcome(...)`:

```scala
object RollOutcome:
  /** Skulls and score of `faces`, all of one family: attack faces score
    * swords and count skulls, defense faces score shields, a Doubler
    * applying across every face. */
  def derive(faces: Vector[DieFace]): (Int, Int) =
    val attack = faces.collect { case face: AttackDieFace => face }
    if attack.nonEmpty then
      (AttackDieFace.skulls(attack), AttackDieFace.score(attack))
    else (0, DefenseDieFace.score(faces.collect {
      case face: DefenseDieFace => face
    }))
```

If `RollOutcome` already has a companion object, add `derive` to it instead.

In `WalkerRolls.scala`, delete the private `derive` method and replace its three calls (`derive(faces)` twice and `derive(faces)._2` once) with `RollOutcome.derive(...)`. Remove `AttackDieFace` and `DefenseDieFace` from the import if nothing else there uses them; `outcomeFor` still does, so they likely stay.

- [ ] **Step 4: Apply it**

Create `src/main/scala/oathdigital/gameplay/operations/RollOperations.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.model._
import oathdigital.model.OperationError.InvalidDescription

/** The `Reroll` write: rerolled faces replace recorded ones in place. */
private[gameplay] object RollOperations:
  /** The skulls of the replaced faces come off what the outcome records and
    * the skulls of the new faces go on, so a power's earlier change to the
    * skulls stands. The score is worked out again from every face, since a
    * Doubler multiplies across the whole pool, as `WalkerRolls.write` does.
    */
  def reroll(ready: ReadyGame, pool: PoolKey, indices: Vector[Int],
      faces: Option[Vector[DieFace]]): Either[OperationError, ReadyGame] =
    def invalid(detail: String) =
      Left(InvalidDescription(s"a reroll of ${pool.value} $detail"))
    ready.game.current.rollOutcomes.get(pool) match
      case None => invalid("has no recorded roll")
      case Some(outcome) => faces match
        case None => invalid("has no faces")
        case Some(rolled) if rolled.size != indices.size =>
          invalid("has a face count unlike its dice")
        case Some(_) if indices.distinct.size != indices.size ||
            indices.exists(index => index < 0 ||
              index >= outcome.faces.size) =>
          invalid("names a die the roll does not have")
        case Some(rolled) if !sameFamily(indices.map(outcome.faces) ++
            rolled) => invalid("mixes attack and defense faces")
        case Some(rolled) =>
          val replaced = indices.zip(rolled).foldLeft(outcome.faces) {
            case (all, (index, face)) => all.updated(index, face) }
          val skulls = outcome.skulls -
            RollOutcome.derive(indices.map(outcome.faces))._1 +
            RollOutcome.derive(rolled)._1
          Right(ready.updateCurrent(current => current.copy(
            rollOutcomes = current.rollOutcomes.updated(pool,
              outcome.copy(faces = replaced, skulls = skulls,
                score = RollOutcome.derive(replaced)._2)))))

  private def sameFamily(faces: Vector[DieFace]): Boolean =
    faces.forall(_.isInstanceOf[AttackDieFace]) ||
      faces.forall(_.isInstanceOf[DefenseDieFace])
```

Check `PileOperations.scala` for how it imports `InvalidDescription` and match it.

In `OperationApplication.applyNonMoveLeaves`, after the `ModifyRollOutcome` case:

```scala
      case (result, Reroll(pool, indices, faces)) =>
        result.flatMap(RollOperations.reroll(_, pool, indices, faces))
```

- [ ] **Step 5: Fill and run a declared reroll**

Create `src/main/scala/oathdigital/gameplay/walker/WalkerRerolls.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.model.{AttackDieFace, DiceKind, OathViolation, ReadyGame,
  Reroll}

/** Fills a declared `Reroll`'s faces from the walker's dice source. Split out
  * of [[ProcedureWalker]], which is close to the project's per-file bound,
  * as [[WalkerShuffles]] is. The filled operation is what the walker runs and
  * records, so replay applies the same faces without rolling again. The die
  * kind is read from the faces being replaced. A reroll of no dice, or of a
  * pool with no recorded roll, is handed back unfilled, and applying it
  * fails.
  */
private[walker] object WalkerRerolls:
  def filled(reroll: Reroll, state: ReadyGame, dice: WalkerDice)
      : Either[OathViolation, Reroll] =
    state.game.current.rollOutcomes.get(reroll.pool)
      .flatMap(outcome => reroll.indices.headOption
        .flatMap(outcome.faces.lift)) match
      case None => Right(reroll)
      case Some(face) =>
        val kind = face match
          case _: AttackDieFace => DiceKind.Attack
          case _ => DiceKind.Defense
        dice.roll(kind, reroll.indices.size).flatMap(faces => Either.cond(
          faces.size == reroll.indices.size,
          reroll.copy(faces = Some(faces)),
          OathViolation.InvalidEventOrder(s"dice source gave ${faces.size} " +
            s"faces for a reroll of ${reroll.indices.size}")))
```

In `ProcedureWalker.runLeaf`, after the `case shuffle: Shuffle if shuffle.order.isEmpty =>` case (two lines), add:

```scala
          case reroll: Reroll if reroll.faces.isEmpty =>
            WalkerRerolls.filled(reroll, ctx.state, ctx.dice).flatMap(
              WalkerSteps.record(_, ctx, path, contributions, strict)).map(Done(_))
```

and add `Reroll` to the file's `oathdigital.model` import.

In `HiddenOutcomes.hides`, change the first case to:

```scala
    case _: Roll | _: Reroll | _: Shuffle | _: Draw | _: Peek | _: Reveal =>
      true
```

and add `Reroll` to its import. Update the doc comment's list: "dice, a reroll, a draw, ...".

- [ ] **Step 6: Encode and decode it**

In `WalkerEventCodec.scala`, cut `encodeDieFace` and `decodeDieFace` and paste them into `WalkerOperationCodec.scala`, changing `private def` to `protected final def`. `WalkerEventCodec` extends `WalkerOperationCodec`, so its callers keep working. If they call helpers (`encodeAttackFace`, `decodeDefenseFace`, ...) that `WalkerOperationCodec` cannot see, move those too, the same way.

In `WalkerOperationCodec.scala`:

1. Add `Reroll` to the `TurnOperation` union, after `ModifyRollOutcome`.
2. In `encodePieceOperation` (the helper that owns the `TurnOperation` cases), after the `ModifyRollOutcome` case:

```scala
      case Reroll(pool, indices, faces) =>
        val rerolled = ujson.Obj("kind" -> "reroll", "pool" -> pool.value,
          "indices" -> ujson.Arr.from(indices.map(ujson.Num(_))))
        faces.foreach(rolled =>
          rerolled("faces") = ujson.Arr.from(rolled.map(encodeDieFace)))
        rerolled
```

3. In the decoder, after the `"modify-roll-outcome"` case:

```scala
    case "reroll" => for
      indices <- traverse(value("indices").arr.zipWithIndex.toVector)({
        case (index, at) => decodeSignedInt(index, s"$path.indices[$at]") })
      faces <- value.obj.get("faces") match
        case None => Right(None)
        case Some(rolled) => traverse(rolled.arr.toVector)(face =>
          decodeDieFace(face.str, s"$path.faces")).map(Some(_))
    yield Reroll(PoolKey(value("pool").str), indices, faces)
```

Match the decoder's existing style for reading an optional field: if it uses a helper for one (as `Shuffle`'s `order` does near line 373), use that helper.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.RollOperationsSuite oathdigital.gameplay.ProcedureWalkerRerollSuite oathdigital.serialization.WalkerOperationWireSuite oathdigital.gameplay.ProcedureWalkerRollSuite"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/model/CoreOperations.scala src/main/scala/oathdigital/model/DicePoolState.scala src/main/scala/oathdigital/gameplay/operations/RollOperations.scala src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala src/main/scala/oathdigital/gameplay/walker/WalkerRolls.scala src/main/scala/oathdigital/gameplay/walker/WalkerRerolls.scala src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/main/scala/oathdigital/gameplay/walker/HiddenOutcomes.scala src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala src/main/scala/oathdigital/serialization/WalkerEventCodec.scala src/test/scala/oathdigital/gameplay/operations/RollOperationsSuite.scala src/test/scala/oathdigital/gameplay/ProcedureWalkerRerollSuite.scala src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala
git commit -m "feat(dice): a reroll operation, filled from the dice source"
```

---

### Task 3: The roll step

**Files:**
- Modify: `src/main/scala/oathdigital/model/PowerWindow.scala`
- Create: `src/main/scala/oathdigital/model/RerollOffers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributionCollector.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala` (`applyWindow`, near line 74)
- Create: `src/main/scala/oathdigital/gameplay/actions/dice/DiceThrow.scala`
- Create: `src/main/scala/oathdigital/gameplay/actions/dice/RerollChoice.scala`
- Create: `src/main/scala/oathdigital/gameplay/actions/dice/RollStep.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala` (`battle`, lines 101-109)
- Modify: `src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala` (`recovery`, line 211, and `rollFeedback`, line 80)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/GamblingHall.scala`, `RelicThief.scala`, `BoneDice.scala`, `MurkyFountain.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` (`rollFeedback`, near line 506)
- Modify: `src/main/scala/oathdigital/application/gamelog/DetailLines.scala`
- Test: `src/test/scala/oathdigital/gameplay/actions/dice/FreeReroll.scala` (new), `src/test/scala/oathdigital/gameplay/actions/dice/RerollChoiceSuite.scala`, `src/test/scala/oathdigital/application/gamelog/GameLogRerollSuite.scala`

**Interfaces:**
- Consumes, from Task 2: `Reroll`.
- Produces:
  - `PowerWindow.Reroll`, key `"dice.reroll"`.
  - `RerollOffer(card: DenizenId, payment: Option[PayCost], steps: Vector[Operation], withheld: Option[OptionReason] = None)` and `OfferedReroll(power: PowerId, offer: RerollOffer)`, in `oathdigital.model`.
  - `OfferReroll(offer: PowerCtx => Option[RerollOffer]) extends Contribution` and `trait RerollHost extends Operation { def expand(offers: Vector[OfferedReroll], pass: OfferHost.Pass): Vector[Operation] }`, in `oathdigital.gameplay.powerresolver`.
  - `GatheredContributions.rerolls: Vector[(PowerId, OfferReroll)]`.
  - In `oathdigital.gameplay.actions.dice`:
    - `DiceThrow(pool: PoolKey, die: DiceKind, roller: PlayerId, number: Int, indices: Vector[Int], faces: Vector[DieFace])`, with `DiceThrow.lastIndices(ready, pool): Vector[Int]` and `DiceThrow.count(ready, pool): Int`.
    - `RerollChoice.throwOf(ctx: PowerCtx): Option[DiceThrow]`, `RerollChoice.decisionId(pool: PoolKey, number: Int): String`, `RerollChoice.poolOf(decisionId: String): Option[PoolKey]`, `RerollChoice.isDecision(decisionId: String): Boolean`, `RerollChoice.keep: DecisionOptionRef`.
    - `RollStep(pool: PoolKey, dice: DiceSpec, roller: Option[PlayerId], window: Option[PowerWindow] = None): Operation`.

- [ ] **Step 1: Write the failing tests**

The seam is tested with a power of the tests' own: Wrestlers, held faceup, offers a free reroll of the whole throw. Wrestlers prints no implemented power; the suite checks it with `PlanDriver.planlessCard`.

Create `src/test/scala/oathdigital/gameplay/actions/dice/FreeReroll.scala`:

```scala
package oathdigital.gameplay.actions.dice

import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  OfferReroll}
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** A power of the tests' own: a free reroll of every die of a throw, for a
  * roller who holds Wrestlers faceup. Wrestlers' own power is not
  * implemented, so the card offers nothing else. */
object FreeReroll extends ContributingPower:
  val wrestlers: DenizenId = CatalogNames.denizen("Wrestlers")
  def id: PowerId = PowerId("test.free-reroll")
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.Reroll -> Vector(OfferReroll(ctx =>
      RerollChoice.throwOf(ctx).filter(rolled =>
        ctx.state.game.current.players.find(_.player == rolled.roller)
          .exists(_.advisers.exists {
            case DenizenState(`wrestlers`, Orientation.FaceUp, _) => true
            case _ => false
          })).map(rolled => RerollOffer(wrestlers, None,
        Vector(Reroll(rolled.pool, rolled.indices)))))))
```

Create `src/test/scala/oathdigital/gameplay/actions/dice/RerollChoiceSuite.scala`:

```scala
package oathdigital.gameplay.actions.dice

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.WalkerDiceFixture.scripted
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._

/** The roll step: after a roll its roller picks a reroll a power offers, or
  * keeps the roll (catalog batch 7, slice 1, "The roll step"). The tests'
  * own `FreeReroll` offers it. */
class RerollChoiceSuite extends munit.FunSuite:
  import AttackDieFace.{HollowSword, OneSword}
  import DefenseDieFace.{Blank, TwoShields}
  import FreeReroll.wrestlers

  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(wrestlers)
  private val attackReroll = RerollChoice.decisionId(CampaignIds.attackPool, 1)
  private val defenseReroll =
    RerollChoice.decisionId(CampaignIds.defensePool, 1)

  test("Wrestlers offers nothing of its own"):
    assertEquals(planlessCard("Wrestlers"), wrestlers)

  /** p1 attacks the bandit-ruled origin with 2 warbands, holding
    * Wrestlers faceup. */
  private def attacker: Board =
    withAdviser(board(warbands = 2, supply = 7), wrestlers,
      Orientation.FaceUp)

  test("a roller who may reroll is asked after the roll, and a reroll " +
      "replaces the dice"):
    val b = attacker
    val game = rulesWith(Vector(FreeReroll), scripted(
      Vector(HollowSword, HollowSword), Vector(OneSword, OneSword),
      Vector(Blank, Blank)))
    val asked = commit(game, b, 2).finishPlanWindows
    assert(awaits(asked, b.actor, attackReroll))
    assertEquals(asked.options(b.actor, WalkerPowers(Vector(FreeReroll)))
      .map(_.ref), Vector(ref, RerollChoice.keep))
    val done = asked.pick(b.actor, attackReroll, ref).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](OneSword, OneSword))

  test("a power used on a throw is not offered again, so nothing more is " +
      "asked"):
    val b = attacker
    val game = rulesWith(Vector(FreeReroll), scripted(
      Vector(HollowSword, HollowSword), Vector(OneSword, HollowSword),
      Vector(Blank, Blank)))
    val done = commit(game, b, 2).finishPlanWindows
      .pick(b.actor, attackReroll, ref).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](OneSword, HollowSword))

  test("keeping the roll changes nothing"):
    val b = attacker
    val game = rulesWith(Vector(FreeReroll), scripted(
      Vector(HollowSword, HollowSword), Vector(Blank, Blank)))
    val done = commit(game, b, 2).finishPlanWindows
      .pick(b.actor, attackReroll, RerollChoice.keep).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](HollowSword, HollowSword))

  test("with nothing offered, the roll step asks nothing"):
    val b = board(warbands = 2, supply = 7)
    val game = rulesWith(Vector(FreeReroll), scripted(
      Vector(HollowSword, HollowSword), Vector(Blank, Blank)))
    val done = commit(game, b, 2).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](HollowSword, HollowSword))

  test("a player defender rerolls the defense inside the attacker's walk"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 4, supply = 7))
    val b = withAdviserFor(base, base.rival, wrestlers, Orientation.FaceUp)
    val game = rulesWith(Vector(FreeReroll), scripted(
      Vector.fill(4)(OneSword), Vector(Blank, Blank),
      Vector(TwoShields, TwoShields)))
    val asked = commit(game, b, 4).finishPlanWindows
    assert(awaits(asked, b.rival, defenseReroll))
    val done = asked.pick(b.rival, defenseReroll, ref).finishPlanWindows
    assertEquals(done.result.defenseFaces,
      Vector[DefenseDieFace](TwoShields, TwoShields))

  test("bandits roll for nobody, so no one rerolls their defense"):
    // The attacker holds Wrestlers, but only the defense's roller may
    // reroll the defense, and bandits are nobody. The attack is kept.
    val b = attacker
    val game = rulesWith(Vector(FreeReroll), scripted(
      Vector(OneSword, OneSword), Vector(Blank, Blank)))
    val done = commit(game, b, 2).finishPlanWindows
      .pick(b.actor, attackReroll, RerollChoice.keep).finishPlanWindows
    assertEquals(done.result.defenseFaces,
      Vector[DefenseDieFace](Blank, Blank))

  test("each Recover roll is its own throw, with its own decision"):
    val recover = RerollChoice.decisionId(PoolKey("recover"), 2)
    assertEquals(recover, "recover.reroll.2")
    assertEquals(RerollChoice.poolOf(recover), Some(PoolKey("recover")))
    assertEquals(RerollChoice.poolOf(attackReroll),
      Some(CampaignIds.attackPool))
    assertEquals(RerollChoice.poolOf("campaign.sacrifice"), None)
    assert(RerollChoice.isDecision(attackReroll))
```

Create `src/test/scala/oathdigital/application/gamelog/GameLogRerollSuite.scala`. It appends a Recover roll and its reroll to `LogScripts.recordedEventsBoard`, as `GameLogCampaignSuite` appends plan events. A Recover decision is not narrated by any prefix, so it tests the new silent rule. `formatAppended` gives every appended step the same state after it, so `lastStateAfter` writes the rerolled outcome the reroll line reads:

```scala
package oathdigital.application.gamelog

import oathdigital.gameplay.actions.dice.RerollChoice
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.walker.{ChoicePayload, DeltaMeaning, RollPayload,
  WalkerCompleted, WalkerStepPayload, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.CatalogNames
import LogScripts._

/** The lines of a reroll: the roll as rolled, then the throw restated after
  * each reroll, and nothing for the reroll decision (catalog batch 7,
  * slice 1). */
class GameLogRerollSuite extends munit.FunSuite:
  import DefenseDieFace.{Blank, TwoShields}

  private val pool = RecoverProcedure.recoverPool
  private val decision = RerollChoice.decisionId(pool, 1)
  private val jinx = DecisionOptionRef.Denizen(CatalogNames.denizen("Jinx"))

  /** The state after the appended steps: the Recover pool's two dice show
    * two double shields. */
  private def rerolled(state: OathState): OathState = state match
    case OathState.Ready(ready) => OathState.Ready(ready.updateCurrent(
      current => current.copy(
        rollPools = current.rollPools.updated(pool, DicePoolState(2)),
        rollOutcomes = current.rollOutcomes.updated(pool, RollOutcome(pool,
          2, Vector(TwoShields, TwoShields), 0, 4)))))
    case other => other

  private def rolled(by: PlayerId, answer: DecisionOptionRef)
      : Vector[OathEvent] = Vector(
    WalkerStepRecorded("roll", RollPayload(pool, Vector(Blank, Blank),
      automatic = true), Vector.empty, Vector.empty),
    WalkerStepRecorded("pick", ChoicePayload(decision,
      ChooseOneAnswer(answer), by), Vector.empty, Vector.empty))

  private def rerollStep: OathEvent = WalkerStepRecorded("reroll",
    WalkerStepPayload.DeltaRecorded(DeltaMeaning.OperationApplied("reroll")),
    Vector(Reroll(pool, Vector(0, 1), Some(Vector(TwoShields, TwoShields)))),
    Vector.empty)

  test("a reroll restates the throw, and its decision writes no line"):
    val script = recordedEventsBoard
    val steps = script.history.steps
    val shown = texts(formatAppended(script,
      rolled(script.actor, jinx) :+ rerollStep :+
        WalkerCompleted(ActionRef.Recover),
      lastStateAfter = rerolled).filter(_.sequence >= steps.size))
    val rolls = shown.filter(_.startsWith("Rolled "))
    assertEquals(rolls.size, 2, shown)
    assertNotEquals(rolls(0), rolls(1))
    assert(!shown.exists(_.toLowerCase.contains("chose")), shown)

  test("keeping the roll writes no line"):
    val script = recordedEventsBoard
    val steps = script.history.steps
    val shown = texts(formatAppended(script,
      rolled(script.actor, RerollChoice.keep) :+
        WalkerCompleted(ActionRef.Recover)).filter(_.sequence >= steps.size))
    assertEquals(shown.count(_.startsWith("Rolled ")), 1, shown)
    assert(!shown.exists(_.contains("Keep the roll")), shown)
```

Check `WalkerStepRecorded`'s argument order and `DeltaMeaning.OperationApplied` against `GameLogCampaignSuite`, which builds both.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.actions.dice.* oathdigital.application.gamelog.GameLogRerollSuite"`
Expected: compilation fails: `RerollChoice`, `OfferReroll`, `RerollOffer` and `PowerWindow.Reroll` are not defined.

- [ ] **Step 3: The window, the offer and the host**

In `PowerWindow.scala`, after `PayOrAsk`:

```scala
  /** After a roll, its roller may reroll dice with the powers that allow it
    * (catalog batch 7, slice 1). Every roll site's `RollStep` asks here. */
  case object Reroll extends OtherWindow { val key = "dice.reroll" }
```

Create `src/main/scala/oathdigital/model/RerollOffers.scala`:

```scala
package oathdigital.model

/** One reroll a power offers on a throw: the card that offers it, what using
  * it pays, and what it does (catalog batch 7, slice 1).
  *
  * `payment` is the power's cost, built by the power, which knows its card's
  * suit; it is run before `steps`. `steps` reroll dice, with any decision
  * and note the power needs; a `Reroll` among them must be a leaf, never
  * inside a `BuildOps`, so the walker fills its faces. `withheld` is why the
  * reroll stands but cannot be used now: it is shown unavailable with that
  * reason.
  *
  * The offer is rebuilt at every fold, so its steps must not depend on what
  * using it changes.
  */
final case class RerollOffer(card: DenizenId, payment: Option[PayCost],
    steps: Vector[Operation], withheld: Option[OptionReason] = None)

/** A reroll offer with the power that made it. */
final case class OfferedReroll(power: PowerId, offer: RerollOffer)
```

In `ContributingPower.scala`, after `final case class Offer(...)`:

```scala
/** Offers one reroll to the node its window hooks, when that node is a
  * [[RerollHost]] (a roll step's reroll choice). Like [[Offer]], it is asked
  * again at every fold, so it must be a pure function of state. Whether the
  * roller can pay is not its business: the host dry-runs the payment.
  */
final case class OfferReroll(
    offer: PowerCtx => Option[RerollOffer]
) extends Contribution
```

and after `object OfferHost { ... }`:

```scala
/** A windowed node that turns the rerolls the powers offer into the
  * children it walks: the reroll counterpart of [[OfferHost]], with the same
  * contract for `expand`.
  */
trait RerollHost extends Operation:
  def expand(offers: Vector[OfferedReroll], pass: OfferHost.Pass)
      : Vector[Operation]
```

Add `OfferedReroll` and `RerollOffer` to the file's model imports if it lists them by name.

In `ContributionCollector.scala`:

- `GatheredContributions` gains `rerolls: Vector[(PowerId, OfferReroll)] = Vector.empty` after `offers`.
- `gather` collects them: a builder `val rerolls = Vector.newBuilder[(PowerId, OfferReroll)]`, the case `case reroll: OfferReroll => rerolls += id -> reroll`, and `rerolls = rerolls.result()` in the result.

`Contribution` is sealed: compile, and add an `OfferReroll` case to any other match the compiler reports as not exhaustive.

In `WalkerPowerGather.applyWindow`, after the `case host: OfferHost =>` case:

```scala
          case host: RerollHost =>
            val offered = gathered.rerolls.flatMap { case (powerId, reroll) =>
              reroll.offer(ctxFor(byId(powerId))).map(OfferedReroll(powerId, _))
            }
            (folded ++ host.expand(offered, OfferHost.Pass(state, answered,
              resuming, WalkerSimulation.applies(_, state, powers))),
              gathered.order)
```

Add `RerollHost` to the `powerresolver` import and `OfferedReroll` to the `model` import. The window's option restrictions are not run over reroll offers: no power restricts a reroll.

- [ ] **Step 4: The throw, the choice and the roll step**

Create `src/main/scala/oathdigital/gameplay/actions/dice/DiceThrow.scala`:

```scala
package oathdigital.gameplay.actions.dice

import oathdigital.model._

/** The dice of one roll that a reroll may change, and who rolled them
  * (catalog batch 7, slice 1). `indices` address the pool's recorded
  * outcome, which keeps every face the pool has rolled; `number` counts the
  * pool's throws, this one included.
  */
final case class DiceThrow(pool: PoolKey, die: DiceKind, roller: PlayerId,
    number: Int, indices: Vector[Int], faces: Vector[DieFace])

object DiceThrow:
  /** The dice `pool` holds, which is how many faces each throw adds. */
  def count(ready: ReadyGame, pool: PoolKey): Int =
    ready.game.current.rollPools.get(pool).fold(0)(_.count)

  /** The last throw of `pool`: its last faces, as many as the pool holds
    * dice. Empty when the pool has not rolled. Recover's pool accumulates
    * faces over its rolls, so each Recover roll is its own throw.
    */
  def lastIndices(ready: ReadyGame, pool: PoolKey): Vector[Int] =
    val faces = ready.game.current.rollOutcomes.get(pool).fold(0)(_.faces.size)
    val thrown = count(ready, pool)
    if thrown == 0 || faces < thrown then Vector.empty
    else (faces - thrown until faces).toVector
```

Create `src/main/scala/oathdigital/gameplay/actions/dice/RerollChoice.scala`:

```scala
package oathdigital.gameplay.actions.dice

import oathdigital.gameplay.powerresolver.{OfferHost, PowerCtx, RerollHost}
import oathdigital.model._

/** One pass of a roll step's reroll choice: the node the powers'
  * `OfferReroll`s are folded into (catalog batch 7, slice 1). A `Repeat` of
  * passes follows the roll in [[RollStep]].
  *
  * What a pass walks, modelled on the battle-plan window's
  * `CampaignPlanChoice`:
  *
  *  - The roller is asked to pick a reroll or "Keep the roll". The rerolls
  *    listed are those not yet used on this throw: each the roller can pay
  *    for (a dry run of its payment says so) priced, the rest shown
  *    unavailable with why. Nothing usable means nothing is asked, the pass
  *    does nothing, and the `Repeat` ends.
  *  - The picked reroll pays and runs its steps.
  *
  * A walk resuming inside a pass keeps its two slots whatever is offered,
  * because the reroll it is running has been picked and is no longer
  * listed.
  */
final class RerollChoice(val pool: PoolKey, val die: DiceKind,
    val roller: PlayerId) extends RerollHost:
  override val window: Option[PowerWindow] = Some(PowerWindow.Reroll)
  override val children: Vector[Operation] = Vector.empty

  /** The throw this choice is about, once the pool has rolled. */
  def throwAt(ready: ReadyGame): Option[DiceThrow] =
    val indices = DiceThrow.lastIndices(ready, pool)
    ready.game.current.rollOutcomes.get(pool).filter(_ => indices.nonEmpty)
      .map(outcome => DiceThrow(pool, die, roller,
        outcome.faces.size / DiceThrow.count(ready, pool), indices,
        indices.map(outcome.faces)))

  def expand(offers: Vector[OfferedReroll], pass: OfferHost.Pass)
      : Vector[Operation] =
    throwAt(pass.state).fold(Vector.empty[Operation]) { rolled =>
      val decisionId = RerollChoice.decisionId(pool, rolled.number)
      val pending = PendingTree(Vector.empty, pass.answered)
      val used = RerollChoice.used(pending, decisionId)
      val listed = offers.filterNot(offered =>
        used.contains(RerollChoice.refOf(offered))).map(option(_, pass))
      val playable = listed.exists(_.available)
      if !playable && !pass.resuming then Vector.empty
      else Vector[Operation](
        Branch((_, _) => if !playable then Vector.empty
          else Vector(decision(decisionId, listed))),
        Branch((_, tree) => RerollChoice.picked(tree, decisionId) match {
          case None => Vector.empty
          case Some(ref) => offers.find(RerollChoice.refOf(_) == ref) match
            case Some(offered) => Vector(RerollChoice.application(offered))
            case None => Vector(BuildOps((_, _) => Left(
              OathViolation.InvalidEventOrder(
                "a picked reroll is no longer offered"))))
        }))
    }

  private def option(offered: OfferedReroll, pass: OfferHost.Pass)
      : DecisionOption =
    val shown = DecisionOption.Denizen(
      DecisionOptionRef.Denizen(offered.offer.card))
    offered.offer.withheld match
      case Some(reason) => DecisionOption.Unavailable(shown, reason)
      case None => pass.applies(RerollChoice.application(offered)) match
        case Left(violation) => DecisionOption.Unavailable(shown,
          OptionReason.Written(RerollChoice.shortfall(pass.state, roller,
            offered.offer, violation)))
        case Right(_) => offered.offer.payment.map(_.cost)
          .filterNot(_ == Cost.free) match
          case None => shown
          case Some(cost) => DecisionOption.Priced(shown,
            OptionPrice(favor = cost.favor, secrets = cost.secret,
              favorBurnt = cost.favorBurnt, secretsBurnt = cost.secretBurnt))

  private def decision(id: String, listed: Vector[DecisionOption]): Decide =
    Decide(id, roller, DecisionQuery.ChooseOne(listed :+
      DecisionOption.Button(RerollChoice.keep, RerollChoice.keepLabel),
      heading = Some(RerollChoice.heading(die))))

object RerollChoice:
  /** "Keep the roll": ends the choice. */
  val keep: DecisionOptionRef = DecisionOptionRef.Button("keep-roll")
  val keepLabel: String = "Keep the roll"
  private val Infix = ".reroll."

  /** `{pool}.reroll.{n}`, for the `n`th throw of `pool`. A Campaign pool's
    * key starts with `campaign.`, so its reroll decisions are Campaign
    * decisions. */
  def decisionId(pool: PoolKey, number: Int): String =
    s"${pool.value}$Infix$number"

  /** The pool a reroll decision is about; `None` for any other decision. */
  def poolOf(decisionId: String): Option[PoolKey] =
    decisionId.lastIndexOf(Infix) match
      case -1 => None
      case at => Option.when(decisionId.drop(at + Infix.length).toIntOption
        .exists(_ > 0))(PoolKey(decisionId.take(at)))

  def isDecision(decisionId: String): Boolean = poolOf(decisionId).nonEmpty

  /** "Reroll the attack dice, or keep the roll". */
  def heading(die: DiceKind): String = s"Reroll the ${word(die)} dice, " +
    "or keep the roll"

  /** "attack" or "defense". */
  def word(die: DiceKind): String = die match
    case DiceKind.Attack => "attack"
    case DiceKind.Defense => "defense"

  /** The throw a reroll offer is asked about: the hooked node's, when it
    * is a reroll choice. */
  def throwOf(ctx: PowerCtx): Option[DiceThrow] = ctx.operation match
    case choice: RerollChoice => choice.throwAt(ctx.state)
    case _ => None

  private[dice] def refOf(offered: OfferedReroll): DecisionOptionRef =
    DecisionOptionRef.Denizen(offered.offer.card)

  /** The rerolls picked on this throw so far, without "Keep the roll". */
  private[dice] def used(pending: PendingTree, decisionId: String)
      : Vector[DecisionOptionRef] = pending.answered.collect:
    case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _)
        if ref != keep => ref

  /** Whether the roller kept the roll. */
  private[dice] def kept(pending: PendingTree, decisionId: String): Boolean =
    pending.answered.reverse.collectFirst {
      case Answered(`decisionId`, answer, _) => answer
    }.contains(DecisionAnswer.ChooseOneAnswer(keep))

  /** The reroll picked last, unless the roller kept the roll. */
  private[dice] def picked(pending: PendingTree, decisionId: String)
      : Option[DecisionOptionRef] = pending.answered.reverse.collectFirst {
    case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref
  }.filter(_ != keep)

  /** The payment runs as a batch, not as the composite's own moves, so the
    * pipeline settles it at once when its payer is not the active player,
    * as a battle plan's does. */
  private[dice] def application(offered: OfferedReroll): Operation =
    Sequence(offered.offer.payment.toVector.map(pay =>
      BuildOps((_, _) => Right(Vector[CoreOperation](pay)))) ++
      offered.offer.steps)

  /** Why a reroll's dry run could not pay for it, as player text. The
    * pipeline may report only a code, so the shortfall is read off the
    * roller's board against the payment when it does. */
  private[dice] def shortfall(ready: ReadyGame, roller: PlayerId,
      offer: RerollOffer, violation: OathViolation): String =
    val cost = offer.payment.map(_.cost).getOrElse(Cost.free)
    violation match
      case OathViolation.InsufficientSecrets(required, available) =>
        needs(required, secrets(required), available)
      case OathViolation.InsufficientFavor(required, available) =>
        needs(required, "favor", available)
      case _ => ready.game.current.players.find(_.player == roller)
        .map(_.board).flatMap(board =>
          if board.faceUpSecrets < cost.secret then Some(needs(cost.secret,
            secrets(cost.secret), board.faceUpSecrets))
          else if board.favor < cost.favor then
            Some(needs(cost.favor, "favor", board.favor))
          else None)
        .getOrElse("Cannot be paid for now")

  private def secrets(count: Int): String =
    if count == 1 then "secret" else "secrets"

  private def needs(required: Int, what: String, available: Int): String =
    s"Needs $required $what; you have $available"
```

`Cost`'s fields are `favor`, `secret`, `favorBurnt` and `secretBurnt`, as `CampaignPlanApplication.pay` builds it.

Create `src/main/scala/oathdigital/gameplay/actions/dice/RollStep.scala`:

```scala
package oathdigital.gameplay.actions.dice

import oathdigital.model._

/** The one way a tree rolls dice (catalog batch 7, slice 1): an automatic
  * `Roll`, then the roller's reroll choice, asked again after each reroll
  * until the roller keeps the roll or nothing usable is left. With no
  * roller (bandits defending), it is the bare `Roll`.
  *
  * `window` is the roll's own, which a power hooks to change the pool
  * before it rolls (Mounted Patrol).
  */
object RollStep:
  def apply(pool: PoolKey, dice: DiceSpec, roller: Option[PlayerId],
      window: Option[PowerWindow] = None): Operation =
    val roll = Roll(pool, dice, RollMode.Automatic, window)
    roller.fold[Operation](roll) { player =>
      val choice = new RerollChoice(pool, dice.die, player)
      Sequence(Vector(roll, Repeat((ready, pending) =>
        choice.throwAt(ready).forall(rolled => !RerollChoice.kept(pending,
          RerollChoice.decisionId(pool, rolled.number))), choice)))
    }
```

If `Sequence` has no single-`Vector` constructor, use the form the files around it use (`Sequence(Vector[Operation](...))`).

- [ ] **Step 5: Use it at the six roll sites**

In `CampaignProcedure.battle`, replace the attack roll:

```scala
    RollStep(CampaignIds.attackPool, DiceSpec(DiceKind.Attack), Some(actor),
      Some(PowerWindow.CampaignAttackRoll)),
```

and the defense roll, whose roller is a player defender and nobody for bandits:

```scala
    Branch((ready, pending) => Vector(RollStep(CampaignIds.defensePool,
      DiceSpec(DiceKind.Defense), CampaignSetup.setup(ready, actor, pending)
        .map(_.defender).collect { case CampaignDefender.Player(player) =>
          player }, Some(PowerWindow.CampaignDefenseRoll)))),
```

Import `oathdigital.gameplay.actions.dice.RollStep`.

In `RecoverProcedure.recovery`, replace `Roll(recoverPool, DiceSpec(DiceKind.Defense), RollMode.Automatic),` with:

```scala
      RollStep(recoverPool, DiceSpec(DiceKind.Defense), Some(actor)),
```

In `RecoverProcedure.rollFeedback`, add the reroll decisions to the ones that show the pool:

```scala
    Option.when(decisionId == rollDecisionId ||
        decisionId == relicDecisionId || decisionId == choiceDecisionId ||
        RerollChoice.poolOf(decisionId).contains(recoverPool))(
```

Import `oathdigital.gameplay.actions.dice.{RerollChoice, RollStep}`.

The `stopped` guard reads the walk's last answer. A reroll answer is never "stop", and the continue-or-stop decision is always answered after the roll step, so the guard is unchanged.

In each action power, replace its `Roll(pool, DiceSpec(kind), RollMode.Automatic)` with `RollStep(pool, DiceSpec(kind), Some(user))`:

- `GamblingHall.build`: `RollStep(pool, DiceSpec(DiceKind.Defense), Some(player)),`
- `RelicThief.roll`: `RollStep(pool, DiceSpec(DiceKind.Defense), Some(actor))`
- `BoneDice.build`: `RollStep(pool, DiceSpec(DiceKind.Attack), Some(player)),`
- `MurkyFountain.build`: `RollStep(pool, DiceSpec(DiceKind.Defense), Some(player)),`

Each imports `oathdigital.gameplay.actions.dice.RollStep`. Their covering roll note still follows the roll step, so it restates the faces as rerolled.

- [ ] **Step 6: The roll panel and the Game Log**

In `WalkerProcedureRegistry.rollFeedback`, fall back to the reroll decision's pool:

```scala
    registrations.get(procedure).flatMap(_.rollFeedback(catalog, ready, actor,
      decisionId)).orElse(RerollChoice.poolOf(decisionId)
        .map(WalkerRollFeedback(_)))
```

Import `oathdigital.gameplay.actions.dice.RerollChoice`. Update the method's doc comment: "or, for a reroll decision any procedure asks, its pool".

In `DetailLines.scala`:

1. `narrated` also holds the reroll decisions:

```scala
  def narrated(decisionId: String): Boolean =
    NarratedIds(decisionId) || ModifierPicks.isDecision(decisionId) ||
      RerollChoice.isDecision(decisionId) ||
      NarratedPrefixes.exists(decisionId.startsWith)
```

and the doc comment above `NarratedPrefixes` gains ", and the reroll decisions, which the reroll's own lines tell".

2. `roll` also restates a throw after a reroll:

```scala
  private def roll(journal: LogJournal, at: Int): Vector[Posted] =
    journal.event(at) match
      case WalkerStepRecorded(_, RollPayload(pool, faces, _), _, _) =>
        rolled(pool, faces)
      case _: WalkerStepRecorded => journal.ops(at).collectFirst {
        case OpStep(Reroll(pool, _, Some(_)), _, after) =>
          rolled(pool, DiceThrow.lastIndices(after, pool).flatMap(
            after.game.current.rollOutcomes(pool).faces.lift))
      }.getOrElse(Vector.empty)
      case _ => Vector.empty

  /** "Rolled {faces}", then the pool's words. */
  private def rolled(pool: PoolKey, faces: Vector[DieFace]): Vector[Posted] =
    LogWords.dice(faces).toVector.map(dice => Posted.line(LogKind.Roll,
      Vector(LogSpan.Text("Rolled "), dice) ++ DetailLines.forPool(pool)))
```

Import `oathdigital.gameplay.actions.dice.{DiceThrow, RerollChoice}`. `OpStep` is the type `journal.ops` returns; it is already used in this file.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.actions.dice.* oathdigital.application.gamelog.* oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.powers.action.* oathdigital.gameplay.RecoverProcedureSuite"`
Expected: PASS. No existing suite changes: with no reroll power registered, every roll step is its bare roll followed by a pass that does nothing.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/model/PowerWindow.scala src/main/scala/oathdigital/model/RerollOffers.scala src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala src/main/scala/oathdigital/gameplay/powerresolver/ContributionCollector.scala src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala src/main/scala/oathdigital/gameplay/actions/dice src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala src/main/scala/oathdigital/gameplay/powers/action/GamblingHall.scala src/main/scala/oathdigital/gameplay/powers/action/RelicThief.scala src/main/scala/oathdigital/gameplay/powers/action/BoneDice.scala src/main/scala/oathdigital/gameplay/powers/action/MurkyFountain.scala src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala src/main/scala/oathdigital/application/gamelog/DetailLines.scala src/test/scala/oathdigital/gameplay/actions/dice src/test/scala/oathdigital/application/gamelog/GameLogRerollSuite.scala
git commit -m "feat(dice): a reroll choice after every roll"
```

Stage any other file the compiler made you touch in Step 3 by its path.

---

### Task 4: Jinx

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/dice/Jinx.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala` (remove `JinxCard`, lines 76-82)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `JinxCard`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/dice/JinxSuite.scala`, plus `GamblingHallSuite`, `RelicThiefSuite`, `BoneDiceSuite` and `MurkyFountainSuite`

**Interfaces:**
- Consumes, from Task 3: `OfferReroll`, `RerollOffer`, `RerollChoice.throwOf`, `RerollChoice.word`, `RerollChoice.decisionId`, `DiceThrow`.
- Produces: `JinxCard`, and `Jinx.id`, `Jinx.rerolledAttack`, `Jinx.rerolledDefense`, `Jinx.forCatalog(catalog)`, in `oathdigital.gameplay.powers.dice`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/dice/JinxSuite.scala`:

```scala
package oathdigital.gameplay.powers.dice

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.WalkerDiceFixture.scripted
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.dice.RerollChoice
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Unwrap.accepted

/** Jinx (card 68): after its ruler rolls, they may pay a secret to reroll
  * every die of that throw, once (catalog batch 7, slice 1). The Campaign
  * and Recover rolls are tested here; each ACTION power's roll is tested in
  * that power's suite. */
class JinxSuite extends munit.FunSuite:
  import AttackDieFace.{HollowSword, OneSword}
  import DefenseDieFace.{Blank, TwoShields}

  private val jinx = CatalogNames.denizen("Jinx")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(jinx)
  private val attackReroll = RerollChoice.decisionId(CampaignIds.attackPool, 1)
  private val defenseReroll =
    RerollChoice.decisionId(CampaignIds.defensePool, 1)

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(Jinx.id, Vector(Jinx.rerolledAttack, Jinx.rerolledDefense),
      run.events)

  private def rerolled(dice: String): NoteText.Said =
    NoteText.Said(s"rerolled-$dice", s"Rerolled the $dice dice.",
      covers = false)

  /** p1 attacks the bandit-ruled origin with 2 warbands, holding Jinx
    * faceup and `secrets` faceup secrets. */
  private def attacker(secrets: Int = 1): Board =
    withSecrets(withAdviser(board(warbands = 2, supply = 7), jinx,
      Orientation.FaceUp), faceUp = secrets)

  test("the attacker pays a secret to reroll every attack die"):
    val b = attacker()
    val game = rules(scripted(Vector(HollowSword, HollowSword),
      Vector(OneSword, OneSword), Vector(Blank, Blank)))
    val asked = commit(game, b, 2).finishPlanWindows
    assert(awaits(asked, b.actor, attackReroll))
    val done = asked.pick(b.actor, attackReroll, ref).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](OneSword, OneSword))
    assertEquals(Look(ready(done.state)).faceUpSecrets(b.actor), 0)
    assertEquals(lines(done), Vector(rerolled("attack")))

  test("Jinx rerolls a throw only once"):
    val b = attacker(secrets = 2)
    val game = rules(scripted(Vector(HollowSword, HollowSword),
      Vector(HollowSword, OneSword), Vector(Blank, Blank)))
    val done = commit(game, b, 2).finishPlanWindows
      .pick(b.actor, attackReroll, ref).finishPlanWindows
    // Nothing more is asked: the second secret stays.
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](HollowSword, OneSword))
    assertEquals(Look(ready(done.state)).faceUpSecrets(b.actor), 1)

  test("without a secret, Jinx is not offered and nothing is asked"):
    val b = attacker(secrets = 0)
    val game = rules(scripted(Vector(HollowSword, HollowSword),
      Vector(Blank, Blank)))
    val done = commit(game, b, 2).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](HollowSword, HollowSword))

  test("a facedown Jinx has no power"):
    val b = withSecrets(withAdviser(board(warbands = 2, supply = 7), jinx,
      Orientation.FaceDown), faceUp = 1)
    val game = rules(scripted(Vector(HollowSword, HollowSword),
      Vector(Blank, Blank)))
    val done = commit(game, b, 2).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](HollowSword, HollowSword))

  test("a defender rerolls the defense dice, paying outside their turn"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 4, supply = 7))
    val b = on(withAdviserFor(base, base.rival, jinx, Orientation.FaceUp))(
      _.secrets(base.rival, faceUp = 1))
    val game = rules(scripted(Vector.fill(4)(OneSword), Vector(Blank, Blank),
      Vector(TwoShields, TwoShields)))
    val asked = commit(game, b, 4).finishPlanWindows
    assert(awaits(asked, b.rival, defenseReroll))
    val done = asked.pick(b.rival, defenseReroll, ref).finishPlanWindows
    assertEquals(done.result.defenseFaces,
      Vector[DefenseDieFace](TwoShields, TwoShields))
    assertEquals(Look(ready(done.state)).faceUpSecrets(b.rival), 0)
    assertEquals(lines(done), Vector(rerolled("defense")))

  test("Jinx at a site its ruler rules rerolls their roll"):
    // p1 rules Broken Peaks with two warbands, and Jinx stands there.
    val base = board(warbands = 2, supply = 7)
    val peaks = CatalogNames.site("Broken Peaks")
    val b = withSecrets(withSiteCard(actorRules(base, peaks), peaks, jinx),
      faceUp = 1)
    val game = rules(scripted(Vector(HollowSword, HollowSword),
      Vector(OneSword, OneSword), Vector(Blank, Blank)))
    val asked = commit(game, b, 2).finishPlanWindows
    assert(awaits(asked, b.actor, attackReroll))

  test("the actor rerolls a Recover roll, and each roll is its own throw"):
    // Fair Isle's difficulty is 3. The first roll and its reroll fail, the
    // second roll is rerolled into a success.
    val site = CatalogNames.site("Fair Isle")
    val relic = CatalogNames.relic("Dowsing Sticks")
    val start = Table.start.turn(Table.p1, Phase.Act).pawn(Table.p1, at = site)
      .relicAt(relic, at = site).adviser(Table.p1, jinx)
      .secrets(Table.p1, faceUp = 2).ready
    val game = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
      walkerDice = scripted(Vector(Blank, Blank), Vector(Blank, Blank),
        Vector(Blank, Blank), Vector(TwoShields, TwoShields)))
    val first = RerollChoice.decisionId(RecoverProcedure.recoverPool, 1)
    val second = RerollChoice.decisionId(RecoverProcedure.recoverPool, 2)
    val started = accepted(game.startWalker(Ready(start), ActionRef.Recover,
      Table.p1))
    val rerolledOnce = accepted(game.resolveWalker(started.state, Table.p1,
      first, DecisionAnswer.ChooseOneAnswer(ref)))
    val continued = accepted(game.resolveWalker(rerolledOnce.state, Table.p1,
      RecoverProcedure.choiceDecisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("continue"))))
    val rerolledTwice = accepted(game.resolveWalker(continued.state,
      Table.p1, second, DecisionAnswer.ChooseOneAnswer(ref)))
    val outcome = ready(rerolledTwice.state).game.current.rollOutcomes(
      RecoverProcedure.recoverPool)
    assertEquals(outcome.score, 4)
    assertEquals(Look(ready(rerolledTwice.state)).faceUpSecrets(Table.p1), 0)

  test("a replayed game reaches the same rerolled faces"):
    val b = attacker()
    val game = rules(scripted(Vector(HollowSword, HollowSword),
      Vector(OneSword, OneSword), Vector(Blank, Blank)))
    val done = commit(game, b, 2).finishPlanWindows
      .pick(b.actor, attackReroll, ref).finishPlanWindows
    // Replay rolls nothing: the faces ride the recorded reroll, and these
    // rules have no dice source, so a replay that rolled would fail.
    val silent = rules()
    val replay = done.events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(b.ready)))((state, event) =>
      state.flatMap(silent.evolve(_, event)))
    assertEquals(ready(accepted(replay)).game.current.lastCampaignResult,
      ready(done.state).game.current.lastCampaignResult)
```

Check `Table.relicAt` and the `RecoverProcedure` names (`recoverPool`, `choiceDecisionId`) are public; `RecoverProcedureSuite` uses `relicAt`. If Recover parks on a modifier decision first, answer it with its start button before `first`.

In each ACTION power's suite, add one test after its first test. Each stages its own table with Jinx faceup and one more faceup secret than the power pays, uses `rulesWithPrintedRestrictions` (which carries the walker powers), and scripts a first roll and a reroll:

`GamblingHallSuite`:

```scala
  test("Jinx rerolls the four dice before the bank is chosen"):
    val ready0 = stagedTable().adviser(actor, CatalogNames.denizen("Jinx"))
      .secrets(actor, faceUp = 1).ready
    val rules0 = rulesWithPrintedRestrictions(
      oathdigital.gameplay.WalkerDiceFixture.scripted(
        Vector.fill(4)(DefenseDieFace.Blank),
        Vector.fill(4)(DefenseDieFace.OneShield)))
    val reroll = oathdigital.gameplay.actions.dice.RerollChoice
      .decisionId(GamblingHall.pool, 1)
    val parked = accepted(use(rules0, ready0, GamblingHall.id, source))
    walkerParked.assertParked(parked.state,
      ActionRef.UsePower(GamblingHall.id), reroll, actor)
    val rerolled = accepted(answer(rules0, parked.state, reroll,
      DecisionOptionRef.Denizen(CatalogNames.denizen("Jinx"))))
    walkerParked.assertParked(rerolled.state,
      ActionRef.UsePower(GamblingHall.id), GamblingHall.decisionId, actor)
    assertEquals(ready(rerolled.state).game.current
      .rollOutcomes(GamblingHall.pool).score, 4)
```

Move the two fully qualified names into the suite's imports.

`RelicThiefSuite`, `BoneDiceSuite` and `MurkyFountainSuite` get the same test shape, named "Jinx rerolls the {power}'s roll":

- `RelicThiefSuite`: from `staged.adviser(p1, jinx).secrets(p1, faceUp = 2).ready`, choose Whistle through `RelicThief.decisionId` with `rulesWithPrintedRestrictions(scripted(<Whistle's defense dice, all two shields>, <the same count of blanks>))`. Read Whistle's defense from `catalog.relic(whistle).get.defense` for the count. The walk then parks on `RerollChoice.decisionId(RelicThief.pool, 1)`; answer Jinx; the relic is taken, because the rerolled dice show no shield (`Look(ready(...)).player(p1).relics` holds Whistle).
- `BoneDiceSuite`: from `staged(supply = 2)` plus Jinx and `.secrets(actor, faceUp = 2)`, scripted two hollow swords then two single swords. After Jinx, Supply rises by 2.
- `MurkyFountainSuite`: from `staged(supply = 1)` plus Jinx and `.secrets(actor, faceUp = 2)`, scripted the pool's blanks then the pool's single shields. After Jinx, Supply rises by the pool's count. Read the pool from `MurkyFountain.Dice`.

In each, check the power's `pool` value is visible to the suite; make it `val` (not `private`) if it is not.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.dice.JinxSuite"`
Expected: compilation fails: `Jinx` is not defined.

- [ ] **Step 3: Write Jinx**

Create `src/main/scala/oathdigital/gameplay/powers/dice/Jinx.scala`:

```scala
package oathdigital.gameplay.powers.dice

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.actions.dice.{DiceThrow, RerollChoice}
import oathdigital.gameplay.operations.Costs
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  OfferReroll, PowerCtx}
import oathdigital.model._

object JinxCard extends Denizen(DenizenId("68"), "Jinx", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.jinx"),
    persistent = true, cost = Cost(secret = 1),
    text = "If you rule Jinx, after you roll [attack-die] or " +
      "[defense-die] for any reason, you may use this power to " +
      "reroll all those dice once. _(This is not a battle plan!)_")
  val powers: Vector[PrintedPower] = Vector(power)

/** Jinx (card 68): "If you rule Jinx, after you roll [attack-die] or
  * [defense-die] for any reason, you may use this power to reroll all those
  * dice once." It costs one secret, paid onto the card when used.
  *
  * It offers itself at every roll step whose roller rules it: a faceup
  * adviser of the roller, or a card faceup at a site the roller rules. A
  * facedown adviser has no power. The roll step stops offering it once it
  * is used on a throw, which is the "once".
  *
  * It writes "Rerolled the attack dice." ("the defense dice"), before the
  * reroll's own roll line.
  */
final case class Jinx private (catalog: ExecutableCatalog)
    extends ContributingPower:
  def id: PowerId = Jinx.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] =
    Vector(Jinx.rerolledAttack, Jinx.rerolledDefense)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.Reroll -> Vector(OfferReroll(offer)))

  private def offer(ctx: PowerCtx): Option[RerollOffer] =
    RerollChoice.throwOf(ctx).filter(rolled =>
      Jinx.rules(ctx.state, rolled.roller)).map(rolled => RerollOffer(
      JinxCard.id,
      Some(Costs.onCard(rolled.roller, JinxCard.id, JinxCard.power.cost,
        catalog, intoOccupied = true)),
      Vector(Note(id, _ => Some(Jinx.line(rolled)(
        PowerSourceRef.Card(JinxCard.id)))),
        Reroll(rolled.pool, rolled.indices))))

object Jinx:
  val id: PowerId = JinxCard.power.id
  /** "Rerolled the attack dice." */
  val rerolledAttack: NoteKey = NoteKey("rerolled-attack",
    Vector(NotePart.Text("Rerolled the attack dice.")))
  /** "Rerolled the defense dice." */
  val rerolledDefense: NoteKey = NoteKey("rerolled-defense",
    Vector(NotePart.Text("Rerolled the defense dice.")))

  private def line(rolled: DiceThrow): NoteKey = rolled.die match
    case DiceKind.Attack => rerolledAttack
    case DiceKind.Defense => rerolledDefense

  /** Whether `player` rules Jinx: a faceup adviser of theirs, or faceup at a
    * site they rule. */
  def rules(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player).exists(_.advisers
      .exists {
        case DenizenState(JinxCard.id, Orientation.FaceUp, _) => true
        case _ => false
      }) || SiteRulers.rulerOfCard(ready, JinxCard.id)
      .contains(SiteRuler.Player(player))

  def forCatalog(catalog: ExecutableCatalog): Jinx = new Jinx(catalog)
```

`JinxCard.id` in a pattern needs a stable identifier; if the compiler refuses it, bind `private val card = JinxCard.id` in `object Jinx` and match `` `card` ``. `RerollChoice.word` is not needed here, so do not import it.

Remove `JinxCard` from `HoldingArcaneDenizens.scala`. In `NewFoundations.scala`, add `import oathdigital.gameplay.powers.dice.JinxCard`; its list entry `JinxCard,` stays.

In `WalkerPowerCatalog.default`, add `Vector(Jinx.forCatalog(catalog)) ++` after `Vector(Marriage) ++`, with `import oathdigital.gameplay.powers.dice.Jinx`. Add a sentence to the object's doc comment: "Jinx is inert until a roll step asks for rerolls."

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.dice.JinxSuite oathdigital.gameplay.powers.action.* oathdigital.gameplay.actions.dice.* oathdigital.catalog.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/dice/Jinx.scala src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/test/scala/oathdigital/gameplay/powers/dice/JinxSuite.scala src/test/scala/oathdigital/gameplay/powers/action/GamblingHallSuite.scala src/test/scala/oathdigital/gameplay/powers/action/RelicThiefSuite.scala src/test/scala/oathdigital/gameplay/powers/action/BoneDiceSuite.scala src/test/scala/oathdigital/gameplay/powers/action/MurkyFountainSuite.scala
git commit -m "feat(batch7): Jinx rerolls any roll its ruler makes"
```

Stage any power file whose `pool` you made public.

---

### Task 5: Master at Arms

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/MasterAtArms.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala` (remove `MasterAtArmsCard`, lines 49-54)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `MasterAtArmsCard`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/MasterAtArmsSuite.scala`

**Interfaces:**
- Consumes, from Tasks 3 and 4: `OfferReroll`, `RerollOffer`, `RerollChoice.throwOf`, `RerollChoice.decisionId`, `DiceThrow`, `Jinx`.
- Produces:
  - `BattlePlan.otherContributions: Map[PowerWindow, Vector[Contribution]]`, empty by default.
  - `MasterAtArmsCard`, and `MasterAtArms.id`, `MasterAtArms.decisionId` (`"campaign.master-at-arms"`), `MasterAtArms.rerolled`, `MasterAtArms.noHollowSword`, `MasterAtArms.heading`, in `oathdigital.gameplay.powers.campaign`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/MasterAtArmsSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.WalkerDiceFixture.scripted
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.dice.RerollChoice
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseAmountAnswer
import oathdigital.testkit.CatalogNames

/** Master at Arms (card 249): a free attacker battle plan. Once chosen,
  * after the attack roll its user may reroll any number of the dice showing
  * hollow swords, once (catalog batch 7, slice 1). */
class MasterAtArmsSuite extends munit.FunSuite:
  import AttackDieFace.{HollowSword, OneSword, TwoSwordsSkull}
  import DefenseDieFace.Blank

  private val card = CatalogNames.denizen("Master at Arms")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val jinx = CatalogNames.denizen("Jinx")
  private val jinxRef: DecisionOptionRef = DecisionOptionRef.Denizen(jinx)
  private val reroll = RerollChoice.decisionId(CampaignIds.attackPool, 1)

  private def rerolled(text: String): NoteText.Said =
    NoteText.Said("rerolled", s"Rerolled $text.", covers = false)

  /** p1 attacks the bandit-ruled origin with 3 warbands, holding Master at
    * Arms faceup. */
  private def attacker: Board =
    withAdviser(board(warbands = 3, supply = 7), card, Orientation.FaceUp)

  /** The attacker chooses Master at Arms and finishes the plan windows. */
  private def chosen(b: Board, dice: Vector[DieFace]*): Run =
    commit(rules(scripted(dice*)), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows

  test("its user rerolls as many hollow swords as they choose"):
    val b = attacker
    val asked = chosen(b, Vector(HollowSword, OneSword, HollowSword),
      Vector(TwoSwordsSkull), Vector(Blank, Blank))
    assert(awaits(asked, b.actor, reroll))
    val amount = asked.pick(b.actor, reroll, ref)
    assert(awaits(amount, b.actor, MasterAtArms.decisionId))
    val done = amount.answer(b.actor, MasterAtArms.decisionId,
      ChooseAmountAnswer(1)).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](TwoSwordsSkull, OneSword, HollowSword))
    assertEquals(said(MasterAtArms.forCatalog(catalog), done.events),
      Vector(rerolled("1 hollow sword")))

  test("with one hollow sword it rerolls it without asking"):
    val b = attacker
    val done = chosen(b, Vector(HollowSword, OneSword, OneSword),
      Vector(OneSword), Vector(Blank, Blank))
      .pick(b.actor, reroll, ref).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](OneSword, OneSword, OneSword))

  test("with no hollow sword nothing is asked"):
    val b = attacker
    val done = chosen(b, Vector(OneSword, OneSword, OneSword),
      Vector(Blank, Blank))
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](OneSword, OneSword, OneSword))

  test("unchosen, it offers no reroll"):
    val b = attacker
    val done = commit(rules(scripted(Vector.fill(3)(HollowSword),
      Vector(Blank, Blank))), b, 3).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector.fill[AttackDieFace](3)(HollowSword))

  test("Jinx and Master at Arms both reroll one throw, Jinx first"):
    val b = withSecrets(withAdviser(attacker, jinx, Orientation.FaceUp),
      faceUp = 1)
    val done = chosen(b, Vector.fill(3)(OneSword),
      Vector(HollowSword, OneSword, OneSword), Vector(OneSword),
      Vector(Blank, Blank))
      .pick(b.actor, reroll, jinxRef)
      .pick(b.actor, reroll, ref).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector.fill[AttackDieFace](3)(OneSword))

  test("Jinx and Master at Arms both reroll one throw, Master at Arms " +
      "first"):
    val b = withSecrets(withAdviser(attacker, jinx, Orientation.FaceUp),
      faceUp = 1)
    val withMaster = chosen(b, Vector(HollowSword, OneSword, OneSword),
      Vector(HollowSword), Vector(OneSword, OneSword, OneSword),
      Vector(Blank, Blank)).pick(b.actor, reroll, ref)
    // Master at Arms is used: only Jinx and "Keep the roll" are left.
    assertEquals(withMaster.offered(b.actor), Vector(jinxRef,
      RerollChoice.keep))
    val done = withMaster.pick(b.actor, reroll, jinxRef).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector.fill[AttackDieFace](3)(OneSword))

  test("without a hollow sword it is shown unavailable beside Jinx"):
    val b = withSecrets(withAdviser(attacker, jinx, Orientation.FaceUp),
      faceUp = 1)
    val asked = chosen(b, Vector.fill(3)(OneSword), Vector(Blank, Blank))
    assertEquals(asked.withheld(b.actor, ref),
      Some(OptionReason.Written(MasterAtArms.noHollowSword)))

  test("Jinx without a secret is shown unavailable beside it"):
    val b = withSecrets(withAdviser(attacker, jinx, Orientation.FaceUp),
      faceUp = 0)
    val asked = chosen(b, Vector(HollowSword, OneSword, OneSword),
      Vector(Blank, Blank))
    assertEquals(asked.withheld(b.actor, jinxRef),
      Some(OptionReason.Written("Needs 1 secret; you have 0")))
```

The suite does not name `Jinx`'s object; it only needs Jinx registered, which Task 4 did.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.MasterAtArmsSuite"`
Expected: compilation fails: `MasterAtArms` is not defined.

- [ ] **Step 3: Let a battle plan contribute elsewhere**

In `BattlePlan.scala`, add after `rewrites`:

```scala
  /** What the plan's power contributes at windows outside the Campaign's
    * own vocabulary, chosen or not: Master at Arms offers a reroll at
    * `PowerWindow.Reroll` and reads there whether it was chosen. */
  def otherContributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
```

In the trait's doc list, add a bullet after the `rewrites` one:

```
  *  - `otherContributions` are what the plan's power contributes at a
  *    window no other member covers, such as a reroll offer.
```

In `contributions`, add `otherContributions.keySet` to the union of window keys, and `otherContributions.getOrElse(window, Vector.empty)` to each window's vector, after the restrictions.

- [ ] **Step 4: Write Master at Arms**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/MasterAtArms.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignProcedure}
import oathdigital.gameplay.actions.dice.{DiceThrow, RerollChoice}
import oathdigital.gameplay.powerresolver.{Contribution, OfferReroll,
  PowerCtx}
import oathdigital.model._

object MasterAtArmsCard extends Denizen(DenizenId("249"), "Master at Arms", Suit.Order):
  val power = PrintedPower(PowerId("denizen.master-at-arms"),
    persistent = false, cost = Cost.free,
    text = "You may reroll any number of dice that show hollow swords " +
      "[hollow-sword] once.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Master at Arms (card 249), an attacker's battle plan: "You may reroll any
  * number of dice that show hollow swords once."
  *
  * It is free and does nothing when chosen. Once chosen, it offers a reroll
  * at the attack roll's roll step. Every hollow sword is the same face, so
  * it asks how many to reroll, from one to all of them, and rerolls the
  * first that many; with one it asks nothing. On a throw with no hollow
  * sword it is shown unavailable. The roll step stops offering it once it
  * is used, which is the "once".
  *
  * It writes "Rerolled {n} hollow swords.".
  */
final case class MasterAtArms private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = MasterAtArmsCard.id
  def id: PowerId = MasterAtArms.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] = Vector(MasterAtArms.rerolled)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      MasterAtArms.label, Vector.empty, Vector.empty))

  override def otherContributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.Reroll -> Vector(OfferReroll(offer)))

  private def offer(ctx: PowerCtx): Option[RerollOffer] =
    RerollChoice.throwOf(ctx).filter(rolled =>
      rolled.pool == CampaignIds.attackPool &&
        rolled.roller == ctx.activePlayer &&
        PlanUse.chosen(ctx.state, PendingTree(ctx.nodePath, ctx.answered),
          ctx.activePlayer, cardRef, sides, afterOutcome = false).nonEmpty)
      .map(rolled => MasterAtArms.hollow(rolled) match
        case Vector() => RerollOffer(cardId, None, Vector.empty,
          Some(OptionReason.Written(MasterAtArms.noHollowSword)))
        case hollow => RerollOffer(cardId, None, steps(rolled, hollow)))

  private def note(amount: Int): Note = Note(id, _ =>
    Some(MasterAtArms.rerolled(PowerSourceRef.Card(cardId),
      NoteArg.Number(amount))))

  private def steps(rolled: DiceThrow, hollow: Vector[Int])
      : Vector[Operation] =
    if hollow.size == 1 then
      Vector(note(1), Reroll(rolled.pool, hollow))
    else Vector(
      Decide(MasterAtArms.decisionId, rolled.roller,
        DecisionQuery.ChooseAmount(1, hollow.size,
          Some(MasterAtArms.heading), "Reroll")),
      Branch((_, pending) => MasterAtArms.amount(pending).toVector
        .flatMap(amount => Vector(note(amount),
          Reroll(rolled.pool, hollow.take(amount))))))

object MasterAtArms:
  val id: PowerId = MasterAtArmsCard.power.id
  val decisionId: String = CampaignProcedure.decisionPrefix +
    "master-at-arms"
  val label: String = "Master at Arms: reroll hollow swords"
  val heading: String = "Reroll how many hollow swords?"
  val noHollowSword: String = "No die shows a hollow sword"
  /** "Rerolled {n} hollow swords." */
  val rerolled: NoteKey = NoteKey("rerolled", Vector(
    NotePart.Text("Rerolled "), NotePart.Arg(0),
    NotePart.Plural(0, " hollow sword.", " hollow swords.")))

  /** The indices of the throw's dice showing a hollow sword, in order. */
  def hollow(rolled: DiceThrow): Vector[Int] = rolled.indices.zip(rolled.faces)
    .collect { case (index, AttackDieFace.HollowSword) => index }

  /** How many hollow swords the user chose to reroll. */
  private def amount(pending: PendingTree): Option[Int] =
    pending.answered.reverse.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseAmountAnswer(n), _) => n
    }

  def forCatalog(catalog: ExecutableCatalog): MasterAtArms =
    new MasterAtArms(catalog)
```

`catalog` is unused in the class. If the build reports it, drop the parameter, make `MasterAtArms` a `case object` extending `BattlePlan`, and register it as `Vector(MasterAtArms)`, as `ShieldWall` is. In that case the suite's `said(MasterAtArms.forCatalog(...), ...)` becomes `said(MasterAtArms, ...)`.

Remove `MasterAtArmsCard` from `HoldingOrderDenizens.scala`. In `NewFoundations.scala`, add `MasterAtArmsCard` to the `oathdigital.gameplay.powers.campaign` import list.

In `PlanRules.forCatalog`, add `Vector(MasterAtArms.forCatalog(catalog)) ++` before `Vector(MountedPatrol.forCatalog(catalog)) ++`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.powers.dice.* oathdigital.catalog.*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala src/main/scala/oathdigital/gameplay/powers/campaign/MasterAtArms.scala src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala src/test/scala/oathdigital/gameplay/powers/campaign/MasterAtArmsSuite.scala
git commit -m "feat(batch7): Master at Arms rerolls hollow swords"
```

---

### Task 6: Gates and roadmap

**Files:**
- Modify: `docs/ROADMAP.md`
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md` (Status)

- [ ] **Step 1: Run the gates**

Run, in order:

1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass. The server count is the baseline plus 35; the frontend count is unchanged. If a count differs, find out why before going on.

- [ ] **Step 2: Mark the slice done**

In `docs/ROADMAP.md`, under "### Phase - Catalog batch 7 - Campaign dice", change `- [ ] Slice 1: rerolls (Jinx, Master at Arms).` to `- [x] Slice 1: rerolls (Jinx, Master at Arms).`

In the design's **Status** paragraph, replace `Slice 1 is planned; its plan-time rulings are under "Slice 1".` with `Slice 1 is done; its plan-time rulings are under "Slice 1".`

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md
git commit -m "docs(batch7): slice 1 done"
```
