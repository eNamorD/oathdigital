# Catalog Batch 7, Slice 2 (Recover Rerolls) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Silver Charm (R31): each time its user pays Supply for a Recover roll, after the first, they may reroll any earlier Recover dice together with the two new dice.

**Architecture:**

- **A throw keeps its rerolled dice.** `RollOutcome` records the faces rerolled since the pool last rolled (`rerolled`). A throw is the faces its roll added plus those. So when Silver Charm rerolls an earlier die with a roll, a reroll power that follows (Jinx) rerolls that die too. The next roll starts a new throw and clears the record.
- **A window on Recover's roll.** Recover's roll step passes `PowerWindow.RecoverRoll` as its roll's window. A windowed leaf folds `Vector(roll)` through the transforms, so a power can put steps on either side of the roll and still come before the roll step's reroll choice.
- **Silver Charm** is a selected Recover modifier. Its transform at `RecoverRoll` gives `Vector(choice, roll, reroll)`:
  - `choice` is a `Branch` that asks a `ChooseMany` over the earlier Recover faces. On the first payment there are none, so it asks nothing.
  - `reroll` is a `Branch` that reads the answer just given. It writes Silver Charm's line, which covers the roll's own line, and runs a `Reroll` leaf of the chosen dice.
- **The Game Log** needs no new rule. The covering note drops the roll's line. Slice 1's restated line after the `Reroll` names the whole throw, since a throw now includes the rerolled earlier dice. The decision is silent through `narratedDecisions`.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these documents before starting:

- `docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md`: "Goal and scope", "The bar", "Slice 1: Rerolls" (with its plan-time rulings), "Slice 2: Recover rerolls", "Seams used for the first time" and "Testing".
- `docs/superpowers/specs/2026-10-09-catalog-batch-7-rulings.md`: "Rules that apply to the whole batch" and the Silver Charm entry.

Task 1 amends both with this plan's rulings, so read them again after Task 1.

**Starts from:** `main` with catalog batch 7 slice 1 merged. In `docs/ROADMAP.md`, "### Phase - Catalog batch 7 - Campaign dice" must show `- [x] Slice 1`. If it does not, stop: this plan builds on slice 1's `Reroll`, `RollStep`, `RerollChoice`, `DiceThrow` and Jinx.

Slice 1 was written from a plan, and its executor may have adapted names or line positions. Where this plan quotes slice 1 code, read the file as it is on `main` and apply the change to what is there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails it.
  - Each file below lists the imports it needs.
  - A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
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
- **Names, exactly:**
  - Window: `PowerWindow.RecoverRoll`, key `"recover.roll"`.
  - Decision id: `"recover.silver-charm"`, the same on every payment.
  - Option refs: `DecisionOptionRef.Button("die-{index}")`, where `index` is the face's 0-based index in the Recover pool's outcome.
- **Texts, exactly:**
  - Silver Charm's printed text: "Each time you spend Supply to roll [defense-die], you may also reroll any number of previous [defense-die] at the same time."
  - The decision's heading: "Reroll earlier dice with this roll".
  - Its summary: "Reroll 1 die" / "Reroll 2 dice" (`DecisionSummary("Reroll", "die", "dice")`).
  - Each option's label: "Die {n}: {face}", where `n` counts from 1 and `{face}` is "blank", "shield", "two shields" or "doubler". Example: "Die 1: shield".
  - Silver Charm's line: "Silver Charm: Rerolled blank, shield." It names the chosen dice as they showed before the reroll, in die order, worded as a roll line words dice.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 14 server tests and no frontend test.
  - It changes one existing suite: `RecoverProcedureSuite` pins the Recover roll's node id and the tree's windowed nodes, and both move (Task 3, Step 6).
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

The product owner reviews these with the plan. Task 1 writes all of them into the spec.

1. **A new window on Recover's roll.** The design's bar does not list one, so it needs approval. Recover's roll step passes `PowerWindow.RecoverRoll` as its roll's window, as the Campaign's attack roll passes `CampaignAttackRoll`. Silver Charm hooks it. The alternative, the existing `RecoverCost` window, wraps only the payment, and a `Reroll` there would run before the roll and miss the new throw.
2. **When it asks.** After the payment and before the roll: the Supply is spent, then Silver Charm asks, then the two new dice are rolled. The chosen dice are rerolled right after the roll, in the same command. The player never sees the new dice before choosing, which is "at the same time".
3. **The throw holds the rerolled dice.** `RollOutcome` gains `rerolled`, the indices of the faces rerolled since the pool last rolled. A throw is the faces its roll added plus those. This amends slice 1's ruling "A throw is the last faces of the pool's outcome, as many as the pool holds dice". It changes nothing for slice 1's powers, which reroll only faces of the throw.
4. **One decision id.** Silver Charm asks `recover.silver-charm` on every payment. Its reroll reads the answer just given, as Recover's continue-or-stop decision is read.
5. **The options.** One button per earlier die, labelled with its number and face ("Die 1: shield"). Choosing none is allowed, and nothing is rerolled. The options name the faces, so the choose-many panel needs no dice of its own.
6. **The Game Log.** Silver Charm's line takes the place of the roll's line: "Silver Charm: Rerolled blank, shield.", then the throw restated with every die in it, "Rolled two shields, two shields, blank". The dice read as every roll line reads them, so the ruling's "double shield" reads "two shields". The decision writes no line. Choosing no die leaves the roll's line as it was.

## File map

**The throw (Task 2):**
- `src/main/scala/oathdigital/model/DicePoolState.scala`: `RollOutcome.rerolled`.
- `src/main/scala/oathdigital/gameplay/operations/RollOperations.scala`: a reroll records its dice.
- `src/main/scala/oathdigital/gameplay/walker/WalkerRolls.scala`: doc comment only.
- `src/main/scala/oathdigital/gameplay/actions/dice/DiceThrow.scala`: `lastIndices` adds the rerolled dice.
- Tests: `RollOperationsSuite` and `WalkerRollsSuite` gain one test each; new `DiceThrowSuite`.

**Silver Charm (Task 3):**
- `src/main/scala/oathdigital/model/PowerWindow.scala`: `RecoverRoll`.
- `src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala`: the roll step's window.
- `src/main/scala/oathdigital/gameplay/powers/recover/SilverCharm.scala`: new. `SilverCharmCard` moves out of `catalog/holding/HoldingRelics.scala`.
- Registration: `ActionModifiers.scala`, and the import in `NewFoundations.scala`.
- Tests: new `SilverCharmSuite` and `GameLogSilverCharmSuite`; a new script in `LogScripts`; `RecoverProcedureSuite` updated.

**Roadmap (Task 4):** `docs/ROADMAP.md`, and the design's status.

---

### Task 1: Write the plan-time rulings into the spec

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md`
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-7-rulings.md`

- [ ] **Step 1: Amend the design**

1. Replace the **Status** paragraph with:

```markdown
**Status:** designed 2026-10-09. Slice 1 is done and slice 2 is planned;
each slice's plan-time rulings are under it. The per-card rulings are in
[the rulings appendix](2026-10-09-catalog-batch-7-rulings.md).
```

2. In "The bar", after the bullet that begins `- **A reroll operation** (slice 1).`, insert:

```markdown
- **A Recover roll window and a throw's rerolled dice** (slice 2).
  Recover's roll step passes `PowerWindow.RecoverRoll` as its roll's
  window, so a power may put steps on either side of the roll, before the
  reroll choice. `RollOutcome` records the faces rerolled since the pool
  last rolled, and a throw is the faces its roll added plus those.
```

3. In "## Slice 1: Rerolls", under "### Plan-time rulings", in the bullet `- **The throw.**`, replace the sentence `A throw is the last faces of the pool's outcome, as many as the pool holds dice.` with:

```markdown
A throw is the faces the pool's last roll added, as many as the pool holds
dice, and every face rerolled since that roll (slice 2).
```

Re-wrap the bullet to 78 characters.

4. Append to "## Slice 2: Recover rerolls", after its last paragraph:

```markdown
### Plan-time rulings

- **Where it asks.** Recover's roll step passes `PowerWindow.RecoverRoll`
  as its roll's window, and Silver Charm hooks it. The Supply is spent,
  then Silver Charm asks, then the two new dice are rolled, and the chosen
  dice are rerolled right after them in the same command. The player never
  sees the new dice before choosing.
- **The throw holds the rerolled dice.** `RollOutcome.rerolled` holds the
  indices of the faces rerolled since the pool last rolled. A throw is the
  faces its roll added plus those, so Jinx after Silver Charm rerolls them
  all, and the next roll starts a new throw.
- **One decision.** Silver Charm asks `recover.silver-charm` on every
  payment, "Reroll earlier dice with this roll", with one option per
  earlier die, labelled "Die 1: shield". Choosing none rerolls nothing.
  Its reroll reads the answer just given.
- **The Game Log.** Silver Charm's line takes the place of the roll's
  line, then the throw is restated with every die in it: "Silver Charm:
  Rerolled blank, shield." and "Rolled two shields, two shields, blank".
  The decision writes no line. Choosing no die leaves the roll's line as
  it was.
```

- [ ] **Step 2: Amend the rulings appendix**

In the Silver Charm entry (`- **R31 Silver Charm.**`), replace the last sentence, from `Line:` to `faces of the chosen dice before the reroll.`, with:

```markdown
  It is asked after the payment and before the roll. Line: "Silver Charm:
  Rerolled shield, blank, two shields.", naming the faces of the chosen
  dice before the reroll as a roll line names dice. It takes the place of
  the roll's own line, and the throw restated after it shows every die.
```

Keep the entry's indentation.

- [ ] **Step 3: Check the links and commit**

Run: `python3 scripts/check-markdown-links.py`
Expected: pass.

```bash
git add docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md docs/superpowers/specs/2026-10-09-catalog-batch-7-rulings.md
git commit -m "docs(batch7): record slice 2 plan-time rulings"
```

---

### Task 2: A throw keeps its rerolled dice

**Files:**
- Modify: `src/main/scala/oathdigital/model/DicePoolState.scala` (`RollOutcome`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/RollOperations.scala` (`reroll`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerRolls.scala` (`write`'s doc comment)
- Modify: `src/main/scala/oathdigital/gameplay/actions/dice/DiceThrow.scala` (`lastIndices`)
- Test: `src/test/scala/oathdigital/gameplay/operations/RollOperationsSuite.scala`, `src/test/scala/oathdigital/gameplay/walker/WalkerRollsSuite.scala`, `src/test/scala/oathdigital/gameplay/actions/dice/DiceThrowSuite.scala` (new)

**Interfaces:**
- Consumes, from slice 1: `RollOutcome.derive`, `RollOperations.reroll`, `DiceThrow.lastIndices(ready, pool)` and `DiceThrow.count(ready, pool)`.
- Produces: `RollOutcome.rerolled: Vector[Int]`, the last field, defaulting to `Vector.empty`. `DiceThrow.lastIndices` keeps its signature and now includes those indices.

- [ ] **Step 1: Write the failing tests**

In `RollOperationsSuite`, after its first test:

```scala
  test("a reroll records which dice it rerolled"):
    val before = rolled(Vector(HollowSword, OneSword, HollowSword))
    val once = RollOperations.reroll(before, pool, Vector(2),
      Some(Vector(OneSword))).toOption.get
    val twice = RollOperations.reroll(once, pool, Vector(0, 2),
      Some(Vector(OneSword, OneSword))).toOption.get
    assertEquals(twice.game.current.rollOutcomes(pool).rerolled,
      Vector(0, 2))
```

In `WalkerRollsSuite`, after the test "two attack rolls of one pool accumulate faces, skulls and a re-scored total":

```scala
  test("a new roll of a pool forgets the dice rerolled before it"):
    val faces: Vector[DieFace] = Vector(AttackDieFace.HollowSword,
      AttackDieFace.OneSword)
    val one = WalkerRolls.write(withPools(attackPool -> 2), RollOutcome(
      attackPool, 2, faces, skulls = 0, score = 1))
    val rerolled = one.updateCurrent(current => current.copy(rollOutcomes =
      current.rollOutcomes.updated(attackPool,
        current.rollOutcomes(attackPool).copy(rerolled = Vector(0)))))
    val two = WalkerRolls.write(rerolled, RollOutcome(attackPool, 2, faces,
      skulls = 0, score = 1))
    assertEquals(two.game.current.rollOutcomes(attackPool).rerolled,
      Vector.empty[Int])
```

Create `src/test/scala/oathdigital/gameplay/actions/dice/DiceThrowSuite.scala`:

```scala
package oathdigital.gameplay.actions.dice

import oathdigital.model._
import oathdigital.testkit.Table

/** A throw: the faces its roll added, and every earlier face rerolled since
  * (catalog batch 7, slice 2). */
class DiceThrowSuite extends munit.FunSuite:
  import DefenseDieFace.{Blank, OneShield, TwoShields}

  private val pool = PoolKey("recover")

  /** The quiet table after two Recover rolls of two dice that show `faces`,
    * with `rerolled` recorded on the outcome. */
  private def afterTwoRecoverRolls(faces: Vector[DieFace],
      rerolled: Vector[Int] = Vector.empty): ReadyGame =
    val (skulls, score) = RollOutcome.derive(faces)
    Table.start.ready.updateCurrent(current => current.copy(
      rollPools = Map(pool -> DicePoolState(2)),
      rollOutcomes = Map(pool -> RollOutcome(pool, faces.size, faces,
        skulls, score, rerolled))))

  test("a throw is the faces the pool's last roll added"):
    val ready = afterTwoRecoverRolls(Vector(OneShield, Blank, Blank,
      TwoShields))
    assertEquals(DiceThrow.lastIndices(ready, pool), Vector(2, 3))

  test("an earlier face rerolled since the last roll joins its throw"):
    val ready = afterTwoRecoverRolls(Vector(OneShield, Blank, Blank,
      TwoShields), rerolled = Vector(0, 3))
    assertEquals(DiceThrow.lastIndices(ready, pool), Vector(0, 2, 3))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.RollOperationsSuite oathdigital.gameplay.walker.WalkerRollsSuite oathdigital.gameplay.actions.dice.DiceThrowSuite"`
Expected: compilation fails: `RollOutcome` has no member or parameter `rerolled`.

- [ ] **Step 3: Record the rerolled dice**

In `DicePoolState.scala`, give `RollOutcome` a last field and say what it holds. Append to its doc comment:

```scala
  *
  * `rerolled` holds the indices of the faces rerolled since the pool last
  * rolled, in index order. A roll starts a new throw and leaves it empty; a
  * `Reroll` adds its dice. A throw is the faces its roll added plus these
  * (catalog batch 7, slice 2: Silver Charm rerolls earlier dice with a
  * roll).
```

and the field, after `score: Int`:

```scala
    score: Int,
    rerolled: Vector[Int] = Vector.empty
```

In `RollOperations.reroll`, where the rerolled outcome is copied, add the dice to the record:

```scala
              outcome.copy(faces = replaced, skulls = skulls,
                score = RollOutcome.derive(replaced)._2,
                rerolled = (outcome.rerolled ++ indices).distinct.sorted)))))
```

In `WalkerRolls.write`, the code already builds a fresh `RollOutcome` for each roll, so `rerolled` comes out empty. Add a sentence to its doc comment: "A new roll starts a new throw, so the dice rerolled before it are forgotten."

In `DiceThrow.scala`, replace `lastIndices` and its doc comment:

```scala
  /** The last throw of `pool`: the faces its last roll added, as many as
    * the pool holds dice, and every earlier face rerolled since, in index
    * order (Silver Charm rerolls earlier dice with a roll). Empty when the
    * pool has not rolled. Recover's pool accumulates faces over its rolls,
    * so each Recover roll is its own throw.
    */
  def lastIndices(ready: ReadyGame, pool: PoolKey): Vector[Int] =
    val thrown = count(ready, pool)
    ready.game.current.rollOutcomes.get(pool)
      .filter(outcome => thrown > 0 && outcome.faces.size >= thrown)
      .fold(Vector.empty[Int]) { outcome =>
        val size = outcome.faces.size
        (outcome.rerolled ++ (size - thrown until size)).distinct.sorted
      }
```

Update the `DiceThrow` case class's doc comment the same way: `indices` are the throw's faces, which may include earlier faces rerolled with it.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.* oathdigital.gameplay.walker.* oathdigital.gameplay.actions.dice.* oathdigital.gameplay.powers.dice.*"`
Expected: PASS. Slice 1's suites are unchanged: their rerolls touch only faces of the throw.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/model/DicePoolState.scala src/main/scala/oathdigital/gameplay/operations/RollOperations.scala src/main/scala/oathdigital/gameplay/walker/WalkerRolls.scala src/main/scala/oathdigital/gameplay/actions/dice/DiceThrow.scala src/test/scala/oathdigital/gameplay/operations/RollOperationsSuite.scala src/test/scala/oathdigital/gameplay/walker/WalkerRollsSuite.scala src/test/scala/oathdigital/gameplay/actions/dice/DiceThrowSuite.scala
git commit -m "feat(dice): a throw keeps the earlier dice rerolled with it"
```

---

### Task 3: Silver Charm

**Files:**
- Modify: `src/main/scala/oathdigital/model/PowerWindow.scala` (after `RecoverCost`)
- Modify: `src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala` (the tree's doc comment, and `recovery`'s `RollStep`)
- Create: `src/main/scala/oathdigital/gameplay/powers/recover/SilverCharm.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala` (remove `SilverCharmCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (the `powers.recover` import)
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/recover/SilverCharmSuite.scala` (new), `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`, `src/test/scala/oathdigital/application/gamelog/GameLogSilverCharmSuite.scala` (new), `src/test/scala/oathdigital/gameplay/RecoverProcedureSuite.scala`

**Interfaces:**
- Consumes:
  - From Task 2: `RollOutcome.rerolled`.
  - From slice 1: `Reroll(pool, indices)`, `RollStep(pool, dice, roller, window)`, `RerollChoice.decisionId(pool, number)`, `RerollChoice.keep`, and Jinx in `WalkerPowerCatalog.default`.
- Produces:
  - `PowerWindow.RecoverRoll`.
  - In `oathdigital.gameplay.powers.recover`: `SilverCharmCard`, and `SilverCharm.id`, `SilverCharm.decisionId`, `SilverCharm.die(index: Int): DecisionOptionRef.Button`, `SilverCharm.rerolled: NoteKey` and `SilverCharm.forCatalog(catalog)`.
  - `LogScripts.recoverSilverCharm: Script`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/recover/SilverCharmSuite.scala`:

```scala
package oathdigital.gameplay.powers.recover

import oathdigital.gameplay.{ModifierPlay, OathRules}
import oathdigital.gameplay.WalkerDiceFixture.scripted
import oathdigital.gameplay.actions.dice.RerollChoice
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{PowerNoted, WalkerDice,
  WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseManyAnswer, ChooseOneAnswer}
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, Outcomes, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

/** Silver Charm (relic R31): each time its user pays Supply for a Recover
  * roll, after the first, they may reroll any earlier Recover dice with the
  * two new ones (catalog batch 7, slice 2). */
class SilverCharmSuite extends munit.FunSuite:
  import DefenseDieFace.{Blank, OneShield, TwoShields}

  private val pool = RecoverProcedure.recoverPool
  private val continueRecovering: DecisionAnswer =
    ChooseOneAnswer(DecisionOptionRef.Button("continue"))

  private def rulesRolling(dice: WalkerDice): OathRules = new OathRules(
    catalog, walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = dice)

  /** p1's Act at Fair Isle, whose Recover difficulty is 3, with Dowsing
    * Sticks facedown there, holding Silver Charm and 4 Supply. */
  private def silverCharmAtFairIsle: Table = Table.start
    .pawn(p1, "Fair Isle").relicAt("Dowsing Sticks", "Fair Isle")
    .relic(p1, "Silver Charm").supply(p1, 4)

  /** p1's Recover from `table` up to its first park, with Silver Charm
    * picked unless `picked` is false. */
  private def recoverStarted(rules: OathRules, table: Table,
      picked: Boolean = true): OathTransition =
    accepted(ModifierPlay.started(rules, Ready(table.ready),
      ActionRef.Recover, p1, PowerWindow.RecoverModifierSelection,
      if picked then Vector(SilverCharm.id) else Vector.empty))

  private def resolved(rules: OathRules, from: OathTransition,
      decisionId: String, answer: DecisionAnswer): OathTransition =
    accepted(rules.resolveWalker(from.state, p1, decisionId, answer))

  /** Silver Charm's answer that rerolls the earlier dice at `indices`. */
  private def rerollingDice(indices: Int*): DecisionAnswer =
    ChooseManyAnswer(indices.toVector.map(SilverCharm.die))

  private def recoverOutcome(at: OathTransition): RollOutcome =
    Outcomes.ready(at.state).game.current.rollOutcomes(pool)

  private def parkedDecisionId(at: OathTransition): String =
    EconomyActions.parkedDecision(at.state).decisionId

  /** What each of Silver Charm's notes names, and whether it covers. */
  private def silverCharmNotes(events: Vector[OathEvent])
      : Vector[(Vector[NoteArg], Boolean)] = events.collect {
    case PowerNoted(SilverCharm.id, note, covers) => (note.args, covers)
  }

  test("its text reads [defense-die], where the card misprints " +
      "[attack-die]"):
    assert(SilverCharmCard.power.text.contains("[defense-die]"))
    assert(!SilverCharmCard.power.text.contains("[attack-die]"))

  test("the first payment has no earlier dice, so nothing is asked"):
    val rules = rulesRolling(scripted(Vector(Blank, Blank)))
    val started = recoverStarted(rules, silverCharmAtFairIsle)
    assertEquals(parkedDecisionId(started),
      RecoverProcedure.choiceDecisionId)

  test("a later payment asks which earlier dice to reroll, one option per " +
      "die"):
    val rules = rulesRolling(scripted(Vector(OneShield, Blank)))
    val started = recoverStarted(rules, silverCharmAtFairIsle)
    val asked = resolved(rules, started, RecoverProcedure.choiceDecisionId,
      continueRecovering)
    val decide = EconomyActions.parkedDecision(asked.state)
    assertEquals(decide.decisionId, SilverCharm.decisionId)
    assertEquals(decide.query, DecisionQuery.ChooseMany(0, 2, Vector(
      DecisionOption.Button(SilverCharm.die(0), "Die 1: shield"),
      DecisionOption.Button(SilverCharm.die(1), "Die 2: blank")),
      heading = Some("Reroll earlier dice with this roll"),
      summary = Some(DecisionSummary("Reroll", "die", "dice"))))
    // Both payments are made, and the second roll waits for the answer.
    assertEquals(Look(Outcomes.ready(asked.state)).player(p1).board.supply
      .supply, 4 - 2)
    assertEquals(recoverOutcome(asked).faces.size, 2)

  test("the chosen earlier dice are rerolled with the two new dice, and " +
      "its line names them as they showed"):
    val ready = silverCharmAtFairIsle.ready
    val rules = rulesRolling(scripted(Vector(OneShield, Blank),
      Vector(Blank, Blank), Vector(TwoShields)))
    val started = recoverStarted(rules, silverCharmAtFairIsle)
    val continued = resolved(rules, started,
      RecoverProcedure.choiceDecisionId, continueRecovering)
    val rerolled = resolved(rules, continued, SilverCharm.decisionId,
      rerollingDice(1))
    val outcome = recoverOutcome(rerolled)
    assertEquals(outcome.faces,
      Vector[DieFace](OneShield, TwoShields, Blank, Blank))
    assertEquals(outcome.score, 3)
    // The throw holds the rerolled die beside the two new ones.
    assertEquals(outcome.rerolled, Vector(1))
    assertEquals(parkedDecisionId(rerolled),
      RecoverProcedure.relicDecisionId)
    val events = started.events ++ continued.events ++ rerolled.events
    assertEquals(silverCharmNotes(events),
      Vector((Vector[NoteArg](NoteArg.Dice(Vector(Blank))), true)))
    assertEquals(ActPowerHarness.replayed(rules, ready, events),
      Outcomes.ready(rerolled.state))
    // The dice-pool change every Recover starts with does not round-trip
    // the wire (its window is not kept), so it is left out of the check.
    assertWireRoundTrips(events.filterNot {
      case step: WalkerStepRecorded =>
        step.ops.exists(_.isInstanceOf[ModifyDicePool])
      case _ => false
    })

  test("choosing no die rerolls nothing and writes no line"):
    val rules = rulesRolling(scripted(Vector(OneShield, Blank),
      Vector(Blank, Blank)))
    val started = recoverStarted(rules, silverCharmAtFairIsle)
    val continued = resolved(rules, started,
      RecoverProcedure.choiceDecisionId, continueRecovering)
    val kept = resolved(rules, continued, SilverCharm.decisionId,
      rerollingDice())
    val outcome = recoverOutcome(kept)
    assertEquals(outcome.faces,
      Vector[DieFace](OneShield, Blank, Blank, Blank))
    assertEquals(outcome.rerolled, Vector.empty[Int])
    assertEquals(silverCharmNotes(continued.events ++ kept.events),
      Vector.empty)
    assertEquals(parkedDecisionId(kept), RecoverProcedure.choiceDecisionId)

  test("a die may be rerolled again on a later payment"):
    // Die 1 is rerolled on the second payment and again on the third.
    val rules = rulesRolling(scripted(Vector(Blank, Blank),
      Vector(Blank, Blank), Vector(Blank),
      Vector(OneShield, Blank), Vector(TwoShields)))
    val started = recoverStarted(rules, silverCharmAtFairIsle)
    val second = resolved(rules, resolved(rules, started,
      RecoverProcedure.choiceDecisionId, continueRecovering),
      SilverCharm.decisionId, rerollingDice(0))
    assertEquals(parkedDecisionId(second), RecoverProcedure.choiceDecisionId)
    val third = resolved(rules, resolved(rules, second,
      RecoverProcedure.choiceDecisionId, continueRecovering),
      SilverCharm.decisionId, rerollingDice(0))
    val outcome = recoverOutcome(third)
    assertEquals(outcome.faces, Vector[DieFace](TwoShields, Blank, Blank,
      Blank, OneShield, Blank))
    assertEquals(outcome.score, 3)
    assertEquals(parkedDecisionId(third), RecoverProcedure.relicDecisionId)

  test("without the pick, a later payment rolls at once"):
    val rules = rulesRolling(scripted(Vector(Blank, Blank),
      Vector(TwoShields, TwoShields)))
    val started = recoverStarted(rules, silverCharmAtFairIsle,
      picked = false)
    val continued = resolved(rules, started,
      RecoverProcedure.choiceDecisionId, continueRecovering)
    assertEquals(parkedDecisionId(continued),
      RecoverProcedure.relicDecisionId)

  test("Jinx after Silver Charm rerolls every die of the throw, the " +
      "earlier one too"):
    // Jinx is kept on the first throw. On the second, Silver Charm rerolls
    // die 1 with the two new dice, and Jinx then rerolls those three,
    // leaving die 2 as it was.
    val jinx = CatalogNames.denizen("Jinx")
    val table = silverCharmAtFairIsle.adviser(p1, jinx)
      .secrets(p1, faceUp = 1)
    val rules = rulesRolling(scripted(Vector(Blank, Blank),
      Vector(Blank, Blank), Vector(Blank),
      Vector(OneShield, OneShield, OneShield)))
    val first = RerollChoice.decisionId(pool, 1)
    val second = RerollChoice.decisionId(pool, 2)
    val started = recoverStarted(rules, table)
    assertEquals(parkedDecisionId(started), first)
    val kept = resolved(rules, started, first,
      ChooseOneAnswer(RerollChoice.keep))
    val continued = resolved(rules, kept, RecoverProcedure.choiceDecisionId,
      continueRecovering)
    val charmed = resolved(rules, continued, SilverCharm.decisionId,
      rerollingDice(0))
    assertEquals(parkedDecisionId(charmed), second)
    val jinxed = resolved(rules, charmed, second,
      ChooseOneAnswer(DecisionOptionRef.Denizen(jinx)))
    assertEquals(recoverOutcome(jinxed).faces,
      Vector[DieFace](OneShield, Blank, OneShield, OneShield))
    assertEquals(parkedDecisionId(jinxed), RecoverProcedure.relicDecisionId)
```

`EconomyActions.parkedDecision` reads the parked `Decide` with the default walker powers; `Look(...).player(p1).board.supply.supply` is how `RelicWorshipSuite` reads Supply.

In `LogScripts.scala`, add after `recoverWorship`:

```scala
  /** p1 recovers at Broken Peaks, difficulty 4, holding Silver Charm. The
    * first roll shows a blank and a shield. On the second payment Silver
    * Charm rerolls the blank, which comes up two shields, beside two new
    * dice that show two shields and a blank: 5 in all. Silver Charm is the
    * only modifier, so its pick starts the Recover by itself; the relic
    * choice is answered by default. */
  def recoverSilverCharm(using munit.Location): Script =
    val (service, act) = atTable("recover-silver-charm",
      ParkedServiceFixture.recoverTable.relic(p1, "Silver Charm")
        .banditsRefilled,
      new Dice.ScriptedDefense(Vector(
        Vector(DefenseDieFace.Blank, DefenseDieFace.OneShield),
        Vector(DefenseDieFace.TwoShields, DefenseDieFace.Blank),
        Vector(DefenseDieFace.TwoShields))))
    val continuingThenRerollingTheFirstDie: Answers = {
      case park if park.decisionId == RecoverProcedure.choiceDecisionId =>
        ChooseOneAnswer(DecisionOptionRef.Button("continue"))
      case park if park.decisionId == SilverCharm.decisionId =>
        ChooseManyAnswer(Vector(SilverCharm.die(0)))
    }
    act.withAnswers(ModifierPlay.answers(
        PowerWindow.RecoverModifierSelection, Vector(SilverCharm.id))
        .orElse(continuingThenRerollingTheFirstDie))
      .after(GameCommand.StartWalker(ActionRef.Recover, StartPayload(p1)))
    Script("recover-silver-charm", service, p1)
```

Change the file's imports: `import oathdigital.gameplay.powers.recover.{RelicWorship, SilverCharm}`, and add `ChooseManyAnswer` to the `oathdigital.model.DecisionAnswer` import.

Create `src/test/scala/oathdigital/application/gamelog/GameLogSilverCharmSuite.scala`:

```scala
package oathdigital.application.gamelog

import LogScripts._

/** Silver Charm's lines: its own line in place of the roll's, then the
  * throw restated with the rerolled earlier die, and nothing for the
  * decision (catalog batch 7, slice 2). */
class GameLogSilverCharmSuite extends munit.FunSuite:
  private val restated = "Rolled two shields, two shields, blank"

  test("Silver Charm's line replaces the roll's, and the throw restated " +
      "names the rerolled earlier die"):
    val entries = format(recoverSilverCharm, None)
    val shown = texts(entries)
    // The first roll's line stands; the second roll's is covered.
    assertEquals(entries.filter(_.kind == LogKind.Roll).map(text),
      Vector("Rolled blank, shield", restated), shown)
    val line = shown.indexOf("Silver Charm: Rerolled blank.")
    assert(line >= 0, shown)
    assert(line < shown.indexOf(restated), shown)

  test("the choice of dice writes no line of its own"):
    val shown = texts(format(recoverSilverCharm, None))
    assert(!shown.exists(_.contains("Die ")), shown)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.recover.SilverCharmSuite oathdigital.application.gamelog.GameLogSilverCharmSuite"`
Expected: compilation fails: `SilverCharm` is not defined.

- [ ] **Step 3: The window on Recover's roll**

In `PowerWindow.scala`, after `RecoverCost`:

```scala
  /** Each Recover roll, before its reroll choice (catalog batch 7, slice
    * 2). The roll step passes it as its roll's window, so a power may put
    * steps on either side of the roll: Silver Charm asks before it and
    * rerolls after it. */
  case object RecoverRoll extends RecoverWindow:
    val key = "recover.roll"
```

In `RecoverProcedure.recovery`, give the roll step its window:

```scala
      RollStep(recoverPool, DiceSpec(DiceKind.Defense), Some(actor),
        Some(PowerWindow.RecoverRoll)),
```

In the tree diagram of `RecoverProcedure`'s doc comment, mark the roll step `// window = RecoverRoll`. Under "Descriptions of PowerWindows", add:

```scala
  * `RecoverRoll` - Each roll, before its reroll choice
```

- [ ] **Step 4: Write Silver Charm**

Create `src/main/scala/oathdigital/gameplay/powers/recover/SilverCharm.scala`:

```scala
package oathdigital.gameplay.powers.recover

import oathdigital.catalog.{ExecutableCatalog, PrintedPower, Relic}
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseManyAnswer

object SilverCharmCard extends Relic(RelicId("R31"), "Silver Charm", value = 37, defense = 1):
  val power = PrintedPower(PowerId("relic.silver-charm"),
    persistent = false, cost = Cost.free,
    text = "Each time you spend Supply to roll [defense-die], you may " +
      "also reroll any number of previous [defense-die] at the same " +
      "time.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Silver Charm (relic R31), a selected Recover modifier: "Each time you
  * spend Supply to roll [defense-die], you may also reroll any number of
  * previous [defense-die] at the same time." The card misprints
  * `[attack-die]` (product owner, 2026-10-09).
  *
  * It hooks each Recover roll at `RecoverRoll`. On a payment after the
  * first, before the roll, it asks which earlier dice to reroll, one option
  * per die; the first payment has none, so nothing is asked. The chosen
  * dice are rerolled right after the two new dice are rolled, so the throw
  * holds them all and a reroll power that follows (Jinx) rerolls every one.
  * There is no once-limit: a die may be rerolled on every payment.
  *
  * Its line names the chosen dice as they showed, "Rerolled blank,
  * shield.", and covers the roll's own line; the throw restated after the
  * reroll shows every die. Choosing no die changes nothing and writes
  * nothing.
  */
final case class SilverCharm private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: RelicId = SilverCharmCard.id
  def id: PowerId = SilverCharm.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Recover)
  override def noteKeys: Vector[NoteKey] = Vector(SilverCharm.rerolled)
  override def narratedDecisions: Set[String] = Set(SilverCharm.decisionId)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.RecoverRoll -> Vector(Transform((ctx, operations) =>
      choice(ctx.activePlayer) +: operations :+ reroll)))

  /** Which earlier dice to reroll, on a payment after the first. */
  private def choice(actor: PlayerId): Operation = Branch((ready, _) =>
    val earlier = SilverCharm.earlier(ready)
    if earlier.isEmpty then Vector.empty
    else Vector(Decide(SilverCharm.decisionId, actor,
      DecisionQuery.ChooseMany(0, earlier.size, earlier.map((index, face) =>
        DecisionOption.Button(SilverCharm.die(index),
          SilverCharm.label(index, face))),
        heading = Some(SilverCharm.heading),
        summary = Some(SilverCharm.summary)))))

  /** The chosen dice, rerolled after the roll, and the line naming them.
    * The `Reroll` is a leaf, never inside a `BuildOps`, so the walker
    * fills its faces. */
  private def reroll: Operation = Branch((_, pending) =>
    val chosen = SilverCharm.chosen(pending)
    if chosen.isEmpty then Vector.empty
    else Vector(Note(id, states => named(states.now, chosen), covers = true),
      Reroll(RecoverProcedure.recoverPool, chosen)))

  /** "Rerolled {the chosen dice}.", read before the reroll changes them. */
  private def named(ready: ReadyGame, chosen: Vector[Int])
      : Option[PowerNote] =
    ready.game.current.rollOutcomes.get(RecoverProcedure.recoverPool)
      .map(outcome => SilverCharm.rerolled(PowerSourceRef.Card(cardId),
        NoteArg.Dice(chosen.flatMap(outcome.faces.lift))))

object SilverCharm:
  val id: PowerId = SilverCharmCard.power.id
  /** Asked on every payment after the first. Each answer is read back by
    * the walk that gives it, as Recover's continue-or-stop decision is. */
  val decisionId: String = "recover.silver-charm"
  val heading: String = "Reroll earlier dice with this roll"
  /** "Reroll 2 dice". */
  val summary: DecisionSummary = DecisionSummary("Reroll", "die", "dice")
  /** "Rerolled {dice}." */
  val rerolled: NoteKey = NoteKey("rerolled", Vector(
    NotePart.Text("Rerolled "), NotePart.Arg(0), NotePart.Text(".")))
  private val DiePrefix = "die-"

  /** The option naming the earlier die at `index` of the Recover pool. */
  def die(index: Int): DecisionOptionRef.Button =
    DecisionOptionRef.Button(s"$DiePrefix$index")

  /** "Die 1: shield". */
  def label(index: Int, face: DefenseDieFace): String =
    s"Die ${index + 1}: ${word(face)}"

  /** A face as a roll line words it. */
  private def word(face: DefenseDieFace): String = face match
    case DefenseDieFace.Blank => "blank"
    case DefenseDieFace.OneShield => "shield"
    case DefenseDieFace.TwoShields => "two shields"
    case DefenseDieFace.Doubler => "doubler"

  /** The Recover pool's faces so far, with their indices: the dice a
    * payment may reroll with its roll. */
  private def earlier(ready: ReadyGame): Vector[(Int, DefenseDieFace)] =
    ready.game.current.rollOutcomes.get(RecoverProcedure.recoverPool)
      .fold(Vector.empty[(Int, DefenseDieFace)])(_.faces.zipWithIndex
        .collect { case (face: DefenseDieFace, index) => index -> face })

  /** The dice the answer just given chose, in index order; none when the
    * last answer is not this decision's. */
  private def chosen(pending: PendingTree): Vector[Int] =
    pending.answered.lastOption.collect {
      case Answered(`decisionId`, ChooseManyAnswer(selected), _) =>
        selected.collect {
          case DecisionOptionRef.Button(key) if key.startsWith(DiePrefix) =>
            key.drop(DiePrefix.length).toIntOption
        }.flatten.distinct.sorted
    }.getOrElse(Vector.empty)

  def forCatalog(catalog: ExecutableCatalog): SilverCharm =
    new SilverCharm(catalog)
```

The card object's header line matches the other card headers, which run past 80 characters; leave it as it is.

The reroll reads the walk's last answer. Nothing is answered between Silver Charm's decision and its reroll, since the roll between them is automatic. On a payment where the decision was not asked, the last answer is another decision's, so nothing is rerolled.

Remove `SilverCharmCard` from `HoldingRelics.scala`. In `NewFoundations.scala`, change the import to `import oathdigital.gameplay.powers.recover.{CatacombsCard, RelicWorshipCard, SilverCharmCard}`, re-wrapped to 80 characters; its list entry `SilverCharmCard,` stays.

In `ActionModifiers.forCatalog`, add `++ Vector(SilverCharm.forCatalog(catalog))` after `Vector(RelicWorship.forCatalog(catalog))`, and change the import to `import oathdigital.gameplay.powers.recover.{RelicWorship, SilverCharm}`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.recover.* oathdigital.application.gamelog.* oathdigital.gameplay.powers.dice.* oathdigital.catalog.* oathdigital.gameplay.cards.*"`
Expected: PASS.

- [ ] **Step 6: Move the Recover roll in `RecoverProcedureSuite`**

Run: `./sbtw "testOnly oathdigital.gameplay.RecoverProcedureSuite"`
Expected: the tree-shape assertions fail, for these reasons only:

- The roll's recorded node id gains one more `.0`. A windowed leaf runs one level down, even when no power changes it. For example, if slice 1 left it at `"1.0.1.0"`, it is now `"1.0.1.0.0"`.
- The set of windowed nodes gains `Some(PowerWindow.RecoverRoll)`, and its expected size rises by one.

Update those expected values and the comments that explain them: the roll step's roll now carries `RecoverRoll`, so it records one level deeper. If any other assertion fails, stop and find out why before going on.

Run the full server suite: `./sbtw test`. Any other suite that pins the Recover roll's node id moves it one level down the same way. Change nothing else.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/model/PowerWindow.scala src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala src/main/scala/oathdigital/gameplay/powers/recover/SilverCharm.scala src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala src/test/scala/oathdigital/gameplay/powers/recover/SilverCharmSuite.scala src/test/scala/oathdigital/application/gamelog/LogScripts.scala src/test/scala/oathdigital/application/gamelog/GameLogSilverCharmSuite.scala src/test/scala/oathdigital/gameplay/RecoverProcedureSuite.scala
git commit -m "feat(batch7): Silver Charm rerolls earlier Recover dice"
```

Stage any other suite Step 6 changed by its path.

---

### Task 4: Gates and roadmap

**Files:**
- Modify: `docs/ROADMAP.md`
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md` (Status)

- [ ] **Step 1: Run the gates**

Run, in order:

1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass. The server count is the baseline plus 14; the frontend count is unchanged. If a count differs, find out why before going on.

- [ ] **Step 2: Mark the slice done**

In `docs/ROADMAP.md`, under "### Phase - Catalog batch 7 - Campaign dice", change `- [ ] Slice 2: Recover rerolls (Silver Charm).` to `- [x] Slice 2: Recover rerolls (Silver Charm).`

In the design's **Status** paragraph, replace `Slice 1 is done and slice 2 is planned;` with `Slices 1 and 2 are done;`.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md
git commit -m "docs(batch7): slice 2 done"
```
