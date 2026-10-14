# Catalog Batch 8, Slice 6 (Operation Triggers) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Secret Testament (R38) and Brass Army's restriction (R25). Also correct Brass Army's battle plan to the gradient plan it is.

**Architecture:**

- **Triggers on executed operations.** A new `ExecutedTrigger` power, with `onExecuted(batch): Option[Operation]`, is asked after each walker batch about the operations the batch executed. It may start a follow-up, which is a tree, not only operations, so Brass Army can ask its holder.
  - The walker walks the follow-up right after the batch, at addresses under the batch's own node: trigger `i` at `path :+ i`, then a `TakeDeferred` at `path :+ n`.
  - A batch that starts a follow-up also holds its executed operations with the existing `Defer`, in the same journaled step. A command that resumes inside a follow-up reads them back and asks the triggers again. That needs no new saved-game field.
  - A follow-up's own batches start no follow-up, and neither does a burn or cost that slice 5 runs whole.
- **Brass Army's restriction** (`BrassArmyMoves`) is the first `ExecutedTrigger`. When a batch moves the pawn of the player holding the relic faceup, it asks that player right away: bury the relic, or flip a faceup secret facedown. With no faceup secret, the relic is buried and nothing is asked.
- **Brass Army's battle plan** becomes a plan for either side, ±4 attack dice. The component catalog's "+4" is a typo.
- **Secret Testament** is a persistent Challenge modifier: a `Transform` at `ChallengeRibbon`. When an enemy challenges for a banner the relic's holder holds, the holder takes every favor or secret on the banner, in place of the ribbon's returns. It touches no Campaign and no card that takes a banner.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these documents before starting:

- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`, sections:
  - "Goal and scope";
  - "The bar" (the slice 6 bullet);
  - "Slice 6: Operation triggers";
  - "Seams used for the first time";
  - "Testing".
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: "Rules that apply to the whole batch" and the slice 6 entries.

Task 1 amends both with this plan's rulings. Read them again after Task 1.

**Starts from:** `main` with slice 5 (burns taken) merged, at `c2009622` or later. Slice 5 changed `WalkerSteps.recordBatch` (the `BurnTakes` swap) and `ProcedureWalker.walkComposite` (a claimed burn or cost runs whole). This plan's code below is written against those versions.

Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it.
  - Each new file below lists the imports it needs.
  - When code moves out of a file, remove any import that file no longer uses.
  - A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). `ProcedureWalker.scala` is at 673 before this plan and ends near 720. Check with `wc -l`.
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - No power name appears in walker sources.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
- **Tree addresses.** No procedure tree gains, loses or moves a node. A follow-up lives at addresses under the batch's own node, which a leaf never had before. Every segment stays a non-negative integer, so `WalkerParked`'s replay check still holds.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them. Re-wrap any line in this plan that comes out longer.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` and `node_modules` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Names, exactly:**
  - In `oathdigital.gameplay.powerresolver`, file `ExecutedTrigger.scala`:
    - `ExecutedBatch(actor: PlayerId, executed: Vector[CoreOperation], now: ReadyGame)`;
    - `trait ExecutedTrigger extends ContributingPower` with `def onExecuted(batch: ExecutedBatch): Option[Operation]`.
  - In `oathdigital.gameplay.walker`, file `ExecutedTriggers.scala`: the `private[walker]` object `ExecutedTriggers`, with `key`, `of`, `batch`, `holds` and `held`.
  - `WalkCtx.following: Boolean` (default `false`).
  - In `oathdigital.gameplay.powers.travel`, file `BrassArmyMoves.scala`: the case object `BrassArmyMoves`, with `decisionId = "power.brass-army.moved"`, `bury = DecisionOptionRef.Button("brass-army.bury")` and `flip = DecisionOptionRef.Button("brass-army.flip")`.
  - In `oathdigital.gameplay.powers.banner`, file `SecretTestament.scala`: `SecretTestamentCard`, moved out of `HoldingRelics`, and the case object `SecretTestament`.
  - Power ids are unchanged: `relic.brass-army.campaign`, `relic.brass-army.restriction` and `relic.secret-testament`.
  - Note keys:
    - `BrassArmyMoves`: `buried` (`"buried"`), `buriedBy` (`"buried-by"`), `flipped` (`"flipped"`) and `flippedBy` (`"flipped-by"`);
    - `SecretTestament`: `tookFavor` (`"took-favor"`) and `tookSecrets` (`"took-secrets"`).
- **Texts, exactly:**
  - Brass Army's question: heading "Brass Army: your pawn moved", buttons "Bury Brass Army" and "Flip a secret facedown".
  - Brass Army's lines on the holder's turn: "Buried after a pawn move." and "Flipped a secret facedown."
  - Off the holder's turn: "{Red} buried it after a pawn move." and "{Red} flipped a secret facedown."
  - Secret Testament: "{Red} took 2 favor from the {People's Favor}." and "{Red} took 2 secrets from the {Darkest Secret}." "secret" is singular for a count of 1, and "favor" never changes.
  - Brass Army's printed Campaign text: "±4 [attack-die]". Its plan labels: "Brass Army: add 4 attack dice" and "Brass Army: remove 4 attack dice".
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 13 server tests and no frontend test.
  - It changes one existing expectation: `PowerKindsCatalogSuite`'s persistent rules gain the two new ids.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

These are written into the design and the rulings appendix in Task 1. Rulings 1, 2 and 7 were agreed with the product owner on 2026-10-13; rulings 3 to 6 were confirmed then.

1. **Secret Testament is a persistent Challenge modifier.** It works only when an enemy challenges for a banner its holder holds. A Raid, a Conspiracy or Wizard's Conclave that takes the banner does not trigger it.
2. **Secret Testament takes the tokens that would leave the banner.**
   - The People's Favor: the holder takes every favor on it, in place of their return to the banks.
   - The Darkest Secret: the holder takes every secret on it, in place of the retained half and the secrets placed on sites.
   - The challenger then pays onto the empty banner and takes it.
3. **Secret Testament's line** names the holder: "{Red} took 2 favor from the {People's Favor}." It is never the holder's turn, because a player cannot challenge for a banner they hold. An empty banner writes no line.
4. **Brass Army asks right after the move,** before the rest of the action. In a Travel the question comes before after-travel powers such as Hospitality, and in a Raid before the Raid's remaining steps.
5. **What counts as a move.** Any batch that moves the holder's pawn: Travel, a Raid's relocation, Keep's placement, Whistle, Brass Horse, Magic Carpet, and later Palanquin. A placement that leaves the pawn where it stands moves nothing, so it asks nothing.
6. **The holder** holds the relic faceup. A facedown Brass Army asks nothing. A Raid that takes the relic before the defender's pawn moves leaves nothing to ask.
7. **Brass Army's battle plan is a gradient plan,** ±4 attack dice, for either side. The component catalog's "+4" is a typo.
   - An attacker places the secret onto the relic and adds four dice to the attack pool, as before.
   - A defender places the secret onto the relic and removes four dice from the attack pool, which never goes below zero.
8. **Lines.** "Buried after a pawn move." and "Flipped a secret facedown." on the holder's turn. Off it, "{Red} buried it after a pawn move." and "{Red} flipped a secret facedown." The bury's line covers the generic "Buried {card}" detail line.

## File map

- Create `src/main/scala/oathdigital/gameplay/powerresolver/ExecutedTrigger.scala`.
- Create `src/main/scala/oathdigital/gameplay/walker/ExecutedTriggers.scala`. Modify:
  - `WalkState.scala`: `WalkCtx.following`;
  - `WalkerSteps.scala`: `recordBatch` holds a triggering batch;
  - `ProcedureWalker.scala`: walks the follow-ups, fresh and on resume.
- Modify `src/main/scala/oathdigital/gameplay/powers/campaign/BrassArmy.scala`: ±4, both sides.
- Modify `docs/catalog/reference/new-foundations-component-catalog.json`: Brass Army's rules text.
- Create `src/main/scala/oathdigital/gameplay/powers/travel/BrassArmyMoves.scala`.
- Create `src/main/scala/oathdigital/gameplay/powers/banner/SecretTestament.scala`. Modify:
  - `src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala`: `SecretTestamentCard` leaves;
  - `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`: imports it.
- Modify `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`: both powers.
- Tests:
  - create `src/test/scala/oathdigital/gameplay/walker/ExecutedTriggersSuite.scala`;
  - create `src/test/scala/oathdigital/gameplay/powers/travel/BrassArmyMovesSuite.scala`;
  - create `src/test/scala/oathdigital/gameplay/powers/banner/SecretTestamentSuite.scala`;
  - modify `src/test/scala/oathdigital/gameplay/CampaignPlansSuite.scala` and `PowerKindsCatalogSuite.scala`.
- Docs: the design, the rulings appendix and `docs/ROADMAP.md`.

---

### Task 1: Write the plan-time rulings into the design and the rulings appendix

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`

- [ ] **Step 1: Replace the slice 6 bullet in "The bar".** Replace the whole bullet that starts "- **Triggers on executed operations** (slice 6)." with:

```markdown
- **Triggers on executed operations** (slice 6, detailed at plan time). An
  `ExecutedTrigger` power's `onExecuted` is asked after each walker batch,
  with the operations the batch executed. A follow-up it starts is a tree,
  walked right after the batch under the batch's own node, so Brass Army
  can ask its holder. The batch holds its operations with `Defer`, in its
  own step, so a command that resumes inside a follow-up asks again with
  the same operations. A follow-up's own batches start no follow-up.
  Secret Testament needs no trigger: it is a Challenge modifier.
```

- [ ] **Step 2: Replace the "Slice 6: Operation triggers" section's two bullets** with:

```markdown
- **Secret Testament** is a persistent Challenge modifier at
  `ChallengeRibbon`. When an enemy challenges for a banner its holder
  holds, the holder takes every favor or secret on the banner, in place of
  the ribbon's returns. A Campaign or a card that takes a banner walks no
  ribbon, so it changes nothing there.
- **Brass Army**'s restriction half is the first `ExecutedTrigger`. It
  watches its holder's pawn moves, on or off turn, and asks the holder to
  bury the relic or flip a faceup secret facedown. Its Campaign half
  already exists, and becomes a plan for either side, ±4 attack dice: the
  component catalog's "+4" is a typo.
```

- [ ] **Step 3: Replace the slice 6 entries in the rulings appendix.** In `2026-10-12-catalog-batch-8-rulings.md`, replace everything from "- **R38 Secret Testament.**" up to, not including, "## Slice 7" with:

```markdown
- **R38 Secret Testament.** "When an enemy takes a banner from you, take
  all [favor] or [secret] on the banner."
  - A persistent Challenge modifier (ruled 2026-10-13). It works only when
    an enemy challenges for a banner its holder holds. A Raid, a
    Conspiracy or Wizard's Conclave that takes the banner does not
    trigger it.
  - The holder takes the tokens that would leave the banner (ruled
    2026-10-13): every favor on the People's Favor, in place of their
    return to the banks, and every secret on the Darkest Secret, in place
    of the retained half and the secrets placed on sites. The challenger
    then pays onto the empty banner and takes it.
  - The holder holds the relic faceup.
  - The line names the holder, whose turn it never is: "{Red} took 2
    favor from the {People's Favor}." or "{Red} took 2 secrets from the
    {Darkest Secret}." An empty banner writes no line.
- **R25 Brass Army** (restriction half). "Whenever your pawn moves (travel
  or place), bury this or flip a secret you have facedown."
  - Every move of the holder's pawn counts, on or off turn: Travel, a
    Raid's relocation, Keep's placement, Whistle, Brass Horse, Magic Carpet
    and Palanquin. A placement that leaves the pawn where it stands moves
    nothing, so it asks nothing.
  - The holder chooses: bury the relic, or flip one faceup secret facedown.
    With no faceup secret, the bury is forced and nothing is asked.
  - The question comes right after the move, before the rest of the
    action. In a Travel it comes before after-travel powers such as
    Hospitality.
  - The holder holds the relic faceup. A Raid that takes the relic before
    the defender's pawn moves leaves nothing to ask.
  - The question: "Brass Army: your pawn moved", with "Bury Brass Army" and
    "Flip a secret facedown".
  - Lines: "Buried after a pawn move." and "Flipped a secret facedown." on
    the holder's turn; "{Red} buried it after a pawn move." and "{Red}
    flipped a secret facedown." off it.
- **R25 Brass Army** (Campaign half, corrected 2026-10-13). "[secret] ±4
  [attack-die]". The component catalog prints "+4", a typo: it is a
  gradient battle plan for either side.
  - An attacker places the secret onto the relic and adds four attack
    dice, which are not force.
  - A defender places the secret onto the relic and removes four attack
    dice. The attack pool never goes below zero.
```

- [ ] **Step 4: Check links and commit.**

Run: `python3 scripts/check-markdown-links.py`
Expected: PASS.

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md
git commit -m "docs(batch8): slice 6 rulings: Secret Testament is a Challenge modifier, Brass Army is ±4"
```

---

### Task 2: Triggers on executed operations

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powerresolver/ExecutedTrigger.scala`
- Create: `src/main/scala/oathdigital/gameplay/walker/ExecutedTriggers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkState.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSteps.scala` (`recordBatch`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`
- Test: `src/test/scala/oathdigital/gameplay/walker/ExecutedTriggersSuite.scala`

**Interfaces:**
- Produces:
  - `ExecutedBatch(actor: PlayerId, executed: Vector[CoreOperation], now: ReadyGame)`;
  - `trait ExecutedTrigger extends ContributingPower { def onExecuted(batch: ExecutedBatch): Option[Operation] }`.
  - Task 4 relies on both, and on the walker asking the trigger again with the same `executed` when a command resumes inside its follow-up.

- [ ] **Step 1: Write the failing tests.** Create `ExecutedTriggersSuite.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{Contribution, ExecutedBatch,
  ExecutedTrigger}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.Outcomes.{finished, parked, recorded}
import oathdigital.testkit.ProcedureWalkerFixture._
import oathdigital.testkit.WalkerStubs.continueOption

class ExecutedTriggersSuite extends munit.FunSuite:
  /** Whether `executed` moves the fixture actor's pawn. */
  private def movesThePawn(executed: Vector[CoreOperation]): Boolean =
    executed.exists {
      case Move(Piece.Pawn(player), _, _, _) => player == actor
      case _ => false
    }

  /** A test trigger: after a batch that moves the actor's pawn, asks the
    * fixture's continue-or-stop question, then spends a Supply. */
  private object AsksAfterThePawnMoves extends ExecutedTrigger:
    val id: PowerId = PowerId("test.asks-after-the-pawn-moves")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    def onExecuted(batch: ExecutedBatch): Option[Operation] =
      Option.when(movesThePawn(batch.executed))(Sequence(decide,
        BuildOps((_, _) => Right(Vector[CoreOperation](spendOneSupply)))))

  /** The move back from S3 to where the fixture's pawn starts. */
  private val movePawnBack: Move = Move(Piece.Pawn(actor), movePawnToS3.to,
    movePawnToS3.from)

  /** A test trigger: after a batch that moves the actor's pawn, moves it
    * back, asking nothing. */
  private object MovesThePawnBack extends ExecutedTrigger:
    val id: PowerId = PowerId("test.moves-the-pawn-back")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    def onExecuted(batch: ExecutedBatch): Option[Operation] =
      Option.when(movesThePawn(batch.executed))(
        BuildOps((_, _) => Right(Vector[CoreOperation](movePawnBack))))

  private val asking = WalkerPowers(Vector(AsksAfterThePawnMoves))
  /** The pawn moves to S3, then a Supply is spent. */
  private val movedThenSpent: Operation = Sequence(movePawnToS3,
    spendOneSupply)
  private val heldMove: CoreOperation =
    Defer(ExecutedTriggers.key, Vector(movePawnToS3))

  test("a batch that moves the pawn holds its operations in its own step " +
      "and parks on the follow-up's question, under the batch's node"):
    val park = parked(ProcedureWalker.advance(ready, movedThenSpent, None,
      asking))
    assertEquals(park.tree.at, Vector("0", "0", "0"))
    assertEquals(park.events.map(recorded(_).ops),
      Vector(Vector[CoreOperation](movePawnToS3, heldMove)))
    assertEquals(park.events.map(recorded(_).nodeId), Vector("0"))

  test("an answer resumes inside the follow-up, which runs before the " +
      "rest of the walk and then takes the held batch"):
    val park = parked(ProcedureWalker.advance(ready, movedThenSpent, None,
      asking))
    val atPark = applyEvents(ready, park.events)
    assert(ExecutedTriggers.holds(atPark))
    val done = finished(ProcedureWalker.resolve(atPark, movedThenSpent,
      park.tree, Answered(decide.decisionId, ChooseOneAnswer(continueOption),
        actor), asking))
    assertEquals(done.events.map(recorded(_).ops), Vector(
      Vector.empty[CoreOperation], Vector[CoreOperation](spendOneSupply),
      Vector[CoreOperation](TakeDeferred(ExecutedTriggers.key)),
      Vector[CoreOperation](spendOneSupply)))
    assertEquals(done.events.map(recorded(_).nodeId),
      Vector("0.0.0", "0.0.1", "0.1", "1"))
    assertEquals(done.events.map(recorded(_).contributions).take(2),
      Vector.fill(2)(Vector(AsksAfterThePawnMoves.id)))
    assertEquals(supplyOf(done.treeless), supplyOf(ready) - 2)

  test("a follow-up's own batches, and a batch no trigger watches, start " +
      "no follow-up"):
    val done = finished(ProcedureWalker.advance(ready, movedThenSpent, None,
      WalkerPowers(Vector(MovesThePawnBack))))
    assertEquals(done.events.map(recorded(_).ops), Vector(
      Vector[CoreOperation](movePawnToS3, heldMove),
      Vector[CoreOperation](movePawnBack),
      Vector[CoreOperation](TakeDeferred(ExecutedTriggers.key)),
      Vector[CoreOperation](spendOneSupply)))
    assertEquals(pawnSiteOf(done.treeless), pawnSiteOf(ready))
```

- [ ] **Step 2: Run the tests to verify they fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.ExecutedTriggersSuite"`
Expected: FAIL to compile: `ExecutedTrigger`, `ExecutedBatch` and `ExecutedTriggers` are not found.

- [ ] **Step 3: Add the trigger API.** Create `ExecutedTrigger.scala`:

```scala
package oathdigital.gameplay.powerresolver

import oathdigital.model.{CoreOperation, Operation, PlayerId, ReadyGame}

/** What a trigger on executed operations reads (catalog batch 8, slice 6):
  * the active player, the operations one walker batch executed, and the
  * state the trigger is asked in. */
final case class ExecutedBatch(actor: PlayerId,
    executed: Vector[CoreOperation], now: ReadyGame)

/** A power that starts a follow-up after a walker batch executes
  * operations it watches (catalog batch 8, slice 6), beside the arrival
  * rule `ContributingPower.onArrival`.
  *
  * The walker walks the follow-up right after the batch, before the walk
  * goes on, and asks each trigger when it reaches it: after the follow-ups
  * of the triggers before it ran, in the order contributions chain. A
  * follow-up's own batches start no follow-up.
  *
  * A command that resumes inside the follow-up asks again, with the same
  * executed operations and the state it parked on, and must get the same
  * tree up to the park. So a follow-up runs nothing before its last
  * question.
  */
trait ExecutedTrigger extends ContributingPower:
  def onExecuted(batch: ExecutedBatch): Option[Operation]
```

- [ ] **Step 4: Add the walk's flag.** In `WalkState.scala`, add this last field to `WalkCtx`, after `visited`:

```scala
    /** Set while the walk runs a trigger's follow-up, whose batches start
      * no follow-up of their own (`ExecutedTriggers`). */
    following: Boolean = false
```

- [ ] **Step 5: Add the walker's side.** Create `ExecutedTriggers.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy,
  OperationRun}
import oathdigital.gameplay.powerresolver.{ContributingPower, Deferred,
  ExecutedBatch, ExecutedTrigger}
import oathdigital.model.{Defer, DeferralKey, OathViolation, ReadyGame}

/** The walker's side of triggers on executed operations (catalog batch 8,
  * slice 6). A batch that starts a follow-up holds its executed operations
  * under [[key]], in its own journaled step, so a command that resumes
  * inside a follow-up asks the triggers again with the same operations.
  * `ProcedureWalker` walks the follow-ups and then takes what is held. It
  * names no card.
  */
private[walker] object ExecutedTriggers:
  val key: DeferralKey = DeferralKey("walker.executed-triggers")

  /** The triggers among `powers`, in the order contributions chain. */
  def of(powers: WalkerPowers): Vector[ExecutedTrigger] =
    powers.powers.collect { case trigger: ExecutedTrigger => trigger }
      .sortBy(ContributingPower.sortKey)

  /** What a trigger reads now: the held operations and the walk's state. */
  def batch(ctx: WalkCtx): ExecutedBatch =
    ExecutedBatch(ctx.activePlayer, Deferred.held(ctx.state, key), ctx.state)

  /** Whether a batch's operations are held for its follow-ups. */
  def holds(ready: ReadyGame): Boolean = Deferred.held(ready, key).nonEmpty

  /** `run`, with its executed operations held when some trigger starts a
    * follow-up on them. Nothing is held for a follow-up's own batches or
    * for an empty batch. */
  def held(ctx: WalkCtx, run: OperationRun)
      : Either[OathViolation, OperationRun] =
    val batch = ExecutedBatch(ctx.activePlayer, run.executed, run.state)
    if ctx.following || run.executed.isEmpty ||
        !of(ctx.powers).exists(_.onExecuted(batch).nonEmpty) then Right(run)
    else OperationPipeline.run(run.state, Vector(Defer(key, run.executed)),
        OperationPolicy.Permissive, Vector.empty, requireAll = true)(
        Right(_)).map(deferred => run.copy(state = deferred.state,
        executed = run.executed ++ deferred.executed))
```

- [ ] **Step 6: Hold a triggering batch.** In `WalkerSteps.recordBatch`, wrap the step in `ExecutedTriggers.held`. Its step records the held operations too, while the slice 5 lines still read only what the batch itself executed:

```scala
    ran.flatMap(run => ExecutedTriggers.held(ctx, run).flatMap { updated =>
      val nodeId = if path.isEmpty then label else path.mkString(".")
      val step = Option.when(updated.executed.nonEmpty)(WalkerStepRecorded(
        nodeId = nodeId,
        payload = WalkerStepPayload.DeltaRecorded(
          DeltaMeaning.of(updated.executed, label)),
        ops = updated.executed,
        contributions = contributions))
      val events = ctx.events ++ step ++
        swapped.lines(run.executed, ctx.activePlayer)
      AdviserArrivalPass.after(ctx.state, ctx.copy(state = updated.state,
        events = events, previous = Some((ctx.state, updated.state))),
        nodeId)
    })
```

Extend `recordBatch`'s doc comment, after the `BurnTakes` sentence: "A batch whose operations start a trigger's follow-up also holds them, in the same step ([[ExecutedTriggers]]), and the walker walks the follow-ups after it."

- [ ] **Step 7: Walk the follow-ups.** In `ProcedureWalker.scala`:

1. Import `TakeDeferred` in the `oathdigital.model` import.
2. In `runNarrowed`, the `case Some(remaining)` branch rejects a cursor that runs past a leaf. Let a held batch's follow-ups take it instead:

```scala
      case Some(remaining) =>
        if remaining.nonEmpty then
          // The leaf ran in an earlier command, and its follow-ups parked.
          if ExecutedTriggers.holds(ctx.state) then
            walkTriggered(ctx, path, cursor, resume, ctx.state)
          else contractViolation(
            s"resume path $remaining overruns leaf ${WalkerSteps.leafLabel(leaf)}")
        else resume match
```

3. In the same method, the `case None` branch is one `leaf match`. Wrap it so that each step it returns goes through `triggered`:

```scala
      case None =>
        (leaf match
          // ...the existing cases, unchanged...
        ).flatMap(triggered(path, resume))
```

4. In `walkComposite`, the slice 5 case runs a claimed burn or cost whole. It must start no follow-up, because a resume could not reach one there. Run it with `following` set, and restore the flag:

```scala
      case core @ (_: Burn | _: PayCost) if cursor.isEmpty &&
          BurnTakes.takes(ctx.state, ctx.powers.powers, core) =>
        // It starts no trigger's follow-up: a resume addresses a
        // composite's children, not follow-ups.
        WalkerSteps.runBuildOps(BuildOps((_, _) =>
          Right(Vector[CoreOperation](core)),
          required = core.required || hooks.strict),
          ctx.copy(following = true), path, hooks.inherited).map:
            case Done(next) => Done(next.copy(following = ctx.following))
            case other => other
```

5. Add these two methods after `runNarrowed`:

```scala
  /** After a batch, the follow-ups its triggers started, when it held its
    * operations for them. */
  private def triggered(path: Vector[String], resume: Resume)(step: Step)
      : Either[OathViolation, Step] = step match
    case Done(ctx) if !ctx.following && ExecutedTriggers.holds(ctx.state) =>
      walkTriggered(ctx, path, None, resume,
        ctx.previous.fold(ctx.state)(_._1))
    case other => Right(other)

  /** Walks the follow-ups a batch's triggers started (catalog batch 8,
    * slice 6) under the batch's node `path`: trigger `i`'s at `path :+ i`,
    * then the take of the held batch at `path :+ n`. Each trigger is asked
    * when the walk reaches it, with the held operations and the state the
    * follow-ups before it left. A resumed walk enters at the trigger the
    * cursor's head names. `before` is the state before the batch, so a note
    * after it reads the batch and its follow-ups whole.
    */
  private def walkTriggered(ctx: WalkCtx, path: Vector[String],
      cursor: Option[Vector[String]], resume: Resume, before: ReadyGame)
      : Either[OathViolation, Step] =
    val triggers = ExecutedTriggers.of(ctx.powers)
    def from(index: Int, at: WalkCtx, rest: Option[Vector[String]])
        : Either[OathViolation, Step] =
      if index == triggers.size then
        WalkerSteps.record(TakeDeferred(ExecutedTriggers.key), at,
          path :+ index.toString, Vector.empty, strict = true).map(done =>
          Done(done.copy(following = false,
            previous = Some((before, done.state)))))
      else
        val trigger = triggers(index)
        (trigger.onExecuted(ExecutedTriggers.batch(at)), rest) match
          case (None, None) => from(index + 1, at, None)
          case (None, Some(_)) => contractViolation(
            s"resume path enters follow-up $index, which did not start")
          case (Some(tree), _) => walk(tree, at, path :+ index.toString,
              rest, resume, WalkerHooks(Vector(trigger.id), Set.empty))
            .flatMap:
              case Done(next) => from(index + 1, next, None)
              case stop => Right(stop)
    cursor.flatMap(_.headOption) match
      case None => from(0, ctx.copy(following = true), None)
      case Some(segment) =>
        segment.toIntOption.filter(i => i >= 0 && i < triggers.size) match
          case Some(index) =>
            from(index, ctx.copy(following = true), cursor.map(_.tail))
          case None => contractViolation(
            s"invalid follow-up segment '$segment' at ${path.mkString(".")}")
```

- [ ] **Step 8: Run the tests to verify they pass.**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.ExecutedTriggersSuite oathdigital.gameplay.DeferralSuite oathdigital.gameplay.walker.BurnTakesSuite"`
Expected: PASS.

Run: `./sbtw test`
Expected: PASS. No power is a trigger yet, so nothing else changes.

- [ ] **Step 9: Commit.**

```bash
git add src/main/scala/oathdigital/gameplay/powerresolver/ExecutedTrigger.scala src/main/scala/oathdigital/gameplay/walker/ExecutedTriggers.scala src/main/scala/oathdigital/gameplay/walker/WalkState.scala src/main/scala/oathdigital/gameplay/walker/WalkerSteps.scala src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/test/scala/oathdigital/gameplay/walker/ExecutedTriggersSuite.scala
git commit -m "feat(batch8): a trigger may start a follow-up after a walker batch"
```

---

### Task 3: Brass Army's battle plan is ±4

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/BrassArmy.scala`
- Modify: `docs/catalog/reference/new-foundations-component-catalog.json`
- Test: `src/test/scala/oathdigital/gameplay/CampaignPlansSuite.scala`

**Interfaces:**
- Consumes: `PlanDice.effect(side, dice)` and `PlanDice.label(card, side, dice)` (existing, `private[campaign]`).

- [ ] **Step 1: Write the failing test.** In `CampaignPlansSuite`, after the test "Brass Army needs the relic faceup", add:

```scala
  test("a defender's Brass Army costs a secret placed onto it and removes " +
      "four attack dice"):
    val b = board(warbands = 5, supply = 7)
    val held = on(b)(_.relic(b.rival, brassCard))
    val offer = present(brass.plan(defending(held)),
      "the defender's Brass Army offer")
    assertEquals(offer.label, "Brass Army: remove 4 attack dice")
    assertEquals(offer.costs,
      Vector[CampaignPlanCost](CampaignPlanCost.Secret(1)))
    assertEquals(offer.effects, Vector[CampaignPlanEffect](
      CampaignPlanEffect.RemoveAttackDice(4)))
    assertEquals(brass.sides, Set[CampaignPlanSide](
      CampaignPlanSide.Attacker, CampaignPlanSide.Defender))
```

- [ ] **Step 2: Run it to verify it fails.**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignPlansSuite"`
Expected: FAIL: the defender's offer is `None`.

- [ ] **Step 3: Make it a plan for either side.** In `BrassArmy.scala`:

1. Change the campaign power's text to `"±4 [attack-die]"`.
2. Replace the doc comment of `BrassArmy`:

```scala
/** Brass Army (relic R25), a battle plan for either side: "[secret] ±4
  * attack dice." The component catalog prints "+4", a typo (catalog batch 8
  * rulings, slice 6).
  *
  * The relic must be faceup in its user's play area. The secret is placed
  * onto the relic, which may already hold resources: a battle plan pays into
  * an occupied card. An attacker adds four dice to the attack pool but not
  * to the force, so no loss, sacrifice or placement limit changes. A
  * defender removes four from it, and the pool never goes below zero. The
  * relic's other power, the pawn-move restriction, is `BrassArmyMoves`.
  */
```

3. Replace `sides` and `plan`:

```scala
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.relic(relicId).map(source => CampaignPlanOffer(source,
      PlanDice.label("Brass Army", context.side, BrassArmy.Dice),
      Vector(CampaignPlanCost.Secret(1)),
      Vector(PlanDice.effect(context.side, BrassArmy.Dice))))
```

- [ ] **Step 4: Correct the reference catalog.** In `new-foundations-component-catalog.json`, change the `relic.brass-army.campaign` power's `"rulesText"` from `"[secret] +4 [attack-die]"` to `"[secret] ±4 [attack-die]"`.

- [ ] **Step 5: Run the tests to verify they pass.**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignPlansSuite oathdigital.gameplay.cards.CardCatalogSuite"`
Expected: PASS. The attacker's test keeps its offer: its label is still "Brass Army: add 4 attack dice".

Run: `./sbtw test`
Expected: PASS. If a Campaign suite whose defender holds Brass Army now sees an extra plan offered, report it rather than changing the test.

- [ ] **Step 6: Commit.**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/BrassArmy.scala docs/catalog/reference/new-foundations-component-catalog.json src/test/scala/oathdigital/gameplay/CampaignPlansSuite.scala
git commit -m "fix(batch8): Brass Army's battle plan is ±4 for either side"
```

---

### Task 4: Brass Army's restriction

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/travel/BrassArmyMoves.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/travel/BrassArmyMovesSuite.scala`

**Interfaces:**
- Consumes: `ExecutedTrigger`, `ExecutedBatch` (Task 2); `BrassArmyCard.restriction` (existing).
- Produces: `BrassArmyMoves` with `decisionId`, `bury`, `flip` and its four note keys.

- [ ] **Step 1: Write the failing tests.** Create `BrassArmyMovesSuite.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.CampaignFixture.{on, raidBoard, rules}
import oathdigital.gameplay.TravelPlay
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver.{awaits, commit,
  winningDice}
import oathdigital.gameplay.powers.targeting.TargetingFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class BrassArmyMovesSuite extends munit.FunSuite:
  private val brass = CatalogNames.relic("Brass Army")
  private val game = ActPowerHarness.rulesWithPrintedRestrictions()
  private val destination: SiteId = Table.quietPawnSite(p2)

  /** The lines Brass Army's restriction journaled in `events`. */
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(BrassArmyMoves.id, BrassArmyMoves.noteKeys, events)

  /** p1, in Act, holding Brass Army (facedown when `facedown`) with
    * `faceUp` faceup and 2 facedown secrets. */
  private def holdingBrassArmy(faceUp: Int, facedown: Boolean = false)
      : ReadyGame = Table.start.turn(p1, Phase.Act)
    .relic(p1, brass, facedown = facedown)
    .secrets(p1, faceUp = faceUp, faceDown = 2).ready

  /** p1's Travel to p2's home site, with every command's events. */
  private def travelled(board: ReadyGame): OathTransition =
    accepted(TravelPlay.travelled(game, Ready(board), p1, destination))

  /** p1's answer to Brass Army's question after `travel`, with every
    * command's events. */
  private def answered(travel: OathTransition, answer: DecisionOptionRef)
      : OathTransition =
    val done = accepted(game.resolveWalker(travel.state, p1,
      BrassArmyMoves.decisionId, DecisionAnswer.ChooseOneAnswer(answer)))
    done.copy(events = travel.events ++ done.events)

  /** Every operation the steps in `events` recorded. */
  private def recordedOps(events: Vector[OathEvent]): Vector[CoreOperation] =
    events.collect { case step: WalkerStepRecorded => step.ops }.flatten

  test("a Travel asks the holder at once, and flipping turns one secret " +
      "facedown, with its line"):
    val board = holdingBrassArmy(faceUp = 2)
    val travel = travelled(board)
    assertEquals(TargetingFixture.parkedOn(travel),
      Some(BrassArmyMoves.decisionId))
    assertEquals(TargetingFixture.optionsAt(travel, ActionRef.Travel),
      Vector[DecisionOptionRef](BrassArmyMoves.bury, BrassArmyMoves.flip))
    assertEquals(Look(TargetingFixture.ready(travel)).pawn(p1), destination)
    val done = answered(travel, BrassArmyMoves.flip)
    val end = Outcomes.ready(done.state)
    assertEquals(Look(end).faceUpSecrets(p1), 1)
    assertEquals(Look(end).faceDownSecrets(p1), 3)
    assert(Look(end).relics(p1).contains(brass))
    assertEquals(said(done.events), Vector(NoteText.Said("flipped",
      "Flipped a secret facedown.", covers = false)))
    assertEquals(ActPowerHarness.replayed(game, board, done.events), end)
    assertWireRoundTrips(done.events)

  test("burying puts the relic back, with a line that covers the bury"):
    val done = answered(travelled(holdingBrassArmy(faceUp = 2)),
      BrassArmyMoves.bury)
    val end = Outcomes.ready(done.state)
    assert(!Look(end).relics(p1).contains(brass))
    assertEquals(Look(end).faceUpSecrets(p1), 2)
    assert(recordedOps(done.events).contains(Bury(BuryableCard.Relic(brass),
      PositionedLocation(Location.PlayArea(p1)))))
    assertEquals(said(done.events), Vector(NoteText.Said("buried",
      "Buried after a pawn move.", covers = true)))

  test("with no faceup secret the relic is buried and nothing is asked"):
    val travel = travelled(holdingBrassArmy(faceUp = 0))
    assertNotEquals(TargetingFixture.parkedOn(travel),
      Some(BrassArmyMoves.decisionId))
    val end = Outcomes.ready(travel.state)
    assert(!Look(end).relics(p1).contains(brass))
    assertEquals(Look(end).faceDownSecrets(p1), 2)
    assertEquals(said(travel.events), Vector(NoteText.Said("buried",
      "Buried after a pawn move.", covers = true)))

  test("a facedown Brass Army asks nothing"):
    val travel = travelled(holdingBrassArmy(faceUp = 2, facedown = true))
    assertNotEquals(TargetingFixture.parkedOn(travel),
      Some(BrassArmyMoves.decisionId))
    assert(Look(Outcomes.ready(travel.state)).relics(p1).contains(brass))
    assertEquals(said(travel.events), Vector.empty)

  test("a defender whose pawn a Raid relocates is asked off turn, and the " +
      "line names them"):
    val raid = raidBoard()
    val b = on(raid.board)(_.relic(raid.board.rival, brass)
      .secrets(raid.board.rival, faceUp = 1, faceDown = 0))
    val run = commit(rules(winningDice), b, 4, targets = Vector(
      DecisionOptionRef.Relic(raid.relic)), raid = true).finishPlanWindows
    assertEquals(run.winner, Some(true))
    val relocated = run.pick(b.actor, CampaignIds.relocation,
      run.offered(b.actor).head).finishPlanWindows
    assert(awaits(relocated, b.rival, BrassArmyMoves.decisionId))
    val done = relocated.pick(b.rival, BrassArmyMoves.decisionId,
      BrassArmyMoves.flip).finishPlanWindows
    assertEquals(Look(done.state).faceUpSecrets(b.rival), 0)
    assertEquals(Look(done.state).faceDownSecrets(b.rival), 1)
    assertEquals(said(done.events), Vector(NoteText.Said("flipped-by",
      s"${b.rival.value} flipped a secret facedown.", covers = false)))
```

Adjust to the fixtures as they are:

- The Raid test assumes the rival's own defender plan window offers Brass Army now (Task 3), and that `finishPlanWindows` passes it. If it does not, decline that offer explicitly with the driver's own call.
- If `ActPowerHarness.replayed` needs the state the Travel started from, as `ModifierPlay.started` sees it, replay from that instead.

- [ ] **Step 2: Run them to verify they fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.BrassArmyMovesSuite"`
Expected: FAIL to compile: `BrassArmyMoves` is not found.

- [ ] **Step 3: Write the trigger.** Create `BrassArmyMoves.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powerresolver.{Contribution, ExecutedBatch,
  ExecutedTrigger}
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.gameplay.powers.campaign.BrassArmyCard
import oathdigital.model._

/** Brass Army (relic R25)'s restriction: "Whenever your pawn moves (travel
  * or place), bury this or flip a secret you have facedown." (catalog batch
  * 8 rulings, slice 6).
  *
  * A trigger on executed operations. After any walker batch that moves the
  * pawn of the player holding the relic faceup, on or off their turn, it
  * asks them right away, before the rest of the action: bury the relic, or
  * flip one faceup secret facedown. With no faceup secret the relic is
  * buried and nothing is asked. A placement that leaves the pawn where it
  * stands moves nothing, so it asks nothing.
  *
  * Its lines name the holder when it is not their turn: "{Red} buried it
  * after a pawn move." or "{Red} flipped a secret facedown.". The bury's
  * line covers the bury's own detail line.
  */
case object BrassArmyMoves extends ExecutedTrigger:
  val cardId: RelicId = BrassArmyCard.id
  val id: PowerId = BrassArmyCard.restriction.id
  val decisionId: String = "power.brass-army.moved"
  val bury: DecisionOptionRef.Button =
    DecisionOptionRef.Button("brass-army.bury")
  val flip: DecisionOptionRef.Button =
    DecisionOptionRef.Button("brass-army.flip")

  /** "Buried after a pawn move." */
  val buried: NoteKey = NoteKey("buried",
    Vector(NotePart.Text("Buried after a pawn move.")))
  /** "{Red} buried it after a pawn move." */
  val buriedBy: NoteKey = NoteKey("buried-by", Vector(NotePart.Arg(0),
    NotePart.Text(" buried it after a pawn move.")))
  /** "Flipped a secret facedown." */
  val flipped: NoteKey = NoteKey("flipped",
    Vector(NotePart.Text("Flipped a secret facedown.")))
  /** "{Red} flipped a secret facedown." */
  val flippedBy: NoteKey = NoteKey("flipped-by", Vector(NotePart.Arg(0),
    NotePart.Text(" flipped a secret facedown.")))

  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
  override def noteKeys: Vector[NoteKey] =
    Vector(buried, buriedBy, flipped, flippedBy)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def onExecuted(batch: ExecutedBatch): Option[Operation] =
    holderOf(batch.now).filter(moved(batch.executed, _)).map { holder =>
      val faceUp = batch.now.game.current.players
        .find(_.player == holder).fold(0)(_.board.faceUpSecrets)
      if faceUp == 0 then Sequence(buryIt(batch.actor, holder))
      else Sequence(question(holder), Branch((_, pending) =>
        if PowerAnswers.one(pending, decisionId).contains(flip) then
          flipOne(batch.actor, holder)
        else buryIt(batch.actor, holder)))
    }

  private def question(holder: PlayerId): Decide = Decide(decisionId,
    holder, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(bury, "Bury Brass Army"),
      DecisionOption.Button(flip, "Flip a secret facedown")),
      heading = Some("Brass Army: your pawn moved")))

  private def buryIt(actor: PlayerId, holder: PlayerId): Vector[Operation] =
    Vector(
      BuildOps((_, _) => Right(Vector[CoreOperation](Bury(
        BuryableCard.Relic(cardId),
        PositionedLocation(Location.PlayArea(holder)))))),
      Note(id, _ => Some(line(buried, buriedBy, actor, holder)),
        covers = true))

  private def flipOne(actor: PlayerId, holder: PlayerId)
      : Vector[Operation] = Vector(
    BuildOps((_, _) => Right(Vector[CoreOperation](FlipSecrets(holder, 1,
      SecretSide.FaceUp, SecretSide.FaceDown)))),
    Note(id, _ => Some(line(flipped, flippedBy, actor, holder))))

  /** `own` on the holder's turn, else `named`, naming them. */
  private def line(own: NoteKey, named: NoteKey, actor: PlayerId,
      holder: PlayerId): PowerNote =
    val card = PowerSourceRef.Card(cardId)
    if holder == actor then own(card) else named(card, NoteArg.Player(holder))

  /** The player holding the relic faceup, if any. */
  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.relics.exists {
      case RelicState(`cardId`, Orientation.FaceUp, _) => true
      case _ => false
    }).map(_.player)

  private def moved(executed: Vector[CoreOperation], holder: PlayerId)
      : Boolean = executed.exists {
    case Move(Piece.Pawn(player), _, _, _) => player == holder
    case _ => false
  }
```

`Sequence(buryIt(...))` uses the case class's own `Vector` constructor, and `Sequence(question(holder), Branch(...))` its varargs builder.

- [ ] **Step 4: Register it.** In `WalkerPowerCatalog.default`, add `Vector(BrassArmyMoves)` after `Vector(VowOfSilence, VowOfDivision)`, and add `BrassArmyMoves` to the `oathdigital.gameplay.powers.travel` import. Add to its doc comment: "Brass Army's restriction is inert until a batch moves its holder's pawn."

In `PowerKindsCatalogSuite`, add `"relic.brass-army.restriction"` at the end of `persistentRules`.

- [ ] **Step 5: Run the tests to verify they pass.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.BrassArmyMovesSuite oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.TravelProcedureSuite"`
Expected: PASS.

Run: `./sbtw test`
Expected: PASS. A suite whose board gives a moving player a faceup Brass Army now sees the question. Report any such failure rather than weakening it.

- [ ] **Step 6: Commit.**

```bash
git add src/main/scala/oathdigital/gameplay/powers/travel/BrassArmyMoves.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala src/test/scala/oathdigital/gameplay/powers/travel/BrassArmyMovesSuite.scala
git commit -m "feat(batch8): Brass Army asks its holder after every pawn move"
```

---

### Task 5: Secret Testament

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/banner/SecretTestament.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/banner/SecretTestamentSuite.scala`

**Interfaces:**
- Consumes: `ChallengeProcedure.bannerDecisionId`, `BannerRules.holder` and `BannerRules.resources` (existing); `PowerWindow.ChallengeRibbon` (existing).

- [ ] **Step 1: Write the failing tests.** Create `SecretTestamentSuite.scala`:

```scala
package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.CampaignFixture.{on, raidBoard, rules}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver.{commit, winningDice}
import oathdigital.gameplay.powers.targeting.TargetingFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class SecretTestamentSuite extends munit.FunSuite:
  private val testament = CatalogNames.relic("Secret Testament")
  private val game = ActPowerHarness.rulesWithPrintedRestrictions()

  /** The lines Secret Testament journaled in `events`. */
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(SecretTestament.id, SecretTestament.noteKeys, events)

  /** p1 at Ancient City (6 favor, 6 faceup and 4 facedown secrets) can
    * challenge p2 there, who holds the People's Favor with 2 favor, the
    * Darkest Secret with 3 secrets, and Secret Testament (facedown when
    * `facedown`). */
  private def challengingTestamentHolder(facedown: Boolean = false)
      : ReadyGame = TargetingFixture.challengeTable
    .darkestSecret(Some(p2), secrets = 3)
    .relic(p2, testament, facedown = facedown).ready

  /** p1's Challenge for `banner`, paying `amount`, with every command's
    * events. */
  private def challenged(board: ReadyGame, banner: Banner, amount: Int)
      : OathTransition =
    val started = accepted(game.startWalker(Ready(board), ActionRef.Challenge,
      p1))
    val chosen = accepted(game.resolveWalker(started.state, p1,
      "challenge.banner", DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Banner(banner))))
    val paid = accepted(game.resolveWalker(chosen.state, p1,
      "challenge.amount", DecisionAnswer.ChooseAmountAnswer(amount)))
    paid.copy(events = started.events ++ chosen.events ++ paid.events)

  test("an enemy who challenges for the People's Favor pays onto an empty " +
      "banner, and the holder takes its favor"):
    val board = challengingTestamentHolder()
    val done = challenged(board, Banner.PeoplesFavor, 3)
    val end = Outcomes.ready(done.state)
    val banner = end.game.current.banners.peoplesFavor
    assertEquals((banner.holder, banner.favor), (Some(p1), 3))
    assertEquals(Look(end).favor(p2), Look(board).favor(p2) + 2)
    assertEquals(end.banks.favor, board.banks.favor)
    assertEquals(said(done.events), Vector(NoteText.Said("took-favor",
      s"${p2.value} took 2 favor from the People's Favor.",
      covers = false)))
    assertEquals(ActPowerHarness.replayed(game, board, done.events), end)
    assertWireRoundTrips(done.events)

  test("for the Darkest Secret the holder takes every secret, and none " +
      "go to sites"):
    val board = challengingTestamentHolder()
    val done = challenged(board, Banner.DarkestSecret, 4)
    val end = Outcomes.ready(done.state)
    val banner = end.game.current.banners.darkestSecret
    assertEquals((banner.holder, banner.secrets), (Some(p1), 4))
    assertEquals(Look(end).faceUpSecrets(p2),
      Look(board).faceUpSecrets(p2) + 3)
    assertEquals(end.game.current.map.sites, board.game.current.map.sites)
    assertEquals(said(done.events), Vector(NoteText.Said("took-secrets",
      s"${p2.value} took 3 secrets from the Darkest Secret.",
      covers = false)))

  test("a facedown Secret Testament leaves the ribbon as it is"):
    val board = challengingTestamentHolder(facedown = true)
    val end = Outcomes.ready(challenged(board, Banner.PeoplesFavor, 3).state)
    assertEquals(end.banks.favor, BannerRules.addFavor(board.banks.favor,
      BannerRules.raidFavorReturn(board.banks.favor, 2)))
    assertEquals(Look(end).favor(p2), Look(board).favor(p2))

  test("a Raid that takes the banner from its holder is not changed"):
    val raid = raidBoard()
    def raided(holding: Boolean): ReadyGame =
      val b = if holding then on(raid.board)(_.relic(raid.board.rival,
        testament)) else raid.board
      val run = commit(rules(winningDice), b, 4, targets = Vector(
        DecisionOptionRef.Banner(Banner.PeoplesFavor)), raid = true)
        .finishPlanWindows
      assertEquals(run.winner, Some(true))
      val done = run.pick(b.actor, CampaignIds.relocation,
        run.offered(b.actor).head).finishPlanWindows
      assertEquals(said(done.events), Vector.empty)
      Outcomes.ready(done.state)
    val (plain, held) = (raided(holding = false), raided(holding = true))
    assertEquals(Look(held).favor(raid.board.rival),
      Look(plain).favor(raid.board.rival))
    assertEquals(held.banks.favor, plain.banks.favor)
```

Adjust to the fixtures as they are:

- If `Table.darkestSecret` or `.relic` takes other parameter names, use them.
- If the site states' equality is too broad for the Darkest Secret test (a state field the Challenge itself writes), compare each site's secrets instead.
- If `Outcomes.ready(done.state)` from a `Run` needs `Look(done.state)` as the Keep suite does, follow the Keep suite.
- If `p2`'s faceup Darkest Secret secrets count needs a separate board count, read it with `Look`.

- [ ] **Step 2: Run them to verify they fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.banner.SecretTestamentSuite"`
Expected: FAIL to compile: `SecretTestament` is not found.

- [ ] **Step 3: Move the card.** Cut `SecretTestamentCard` out of `HoldingRelics.scala`. Remove any import that file no longer uses. `NewFoundations.scala` imports the holding package with a wildcard, so add `import oathdigital.gameplay.powers.banner.SecretTestamentCard` beside its other power-card imports.

- [ ] **Step 4: Write the modifier.** Create `SecretTestament.scala`:

```scala
package oathdigital.gameplay.powers.banner

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.actions.challenge.ChallengeProcedure
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.model._

object SecretTestamentCard extends Relic(RelicId("R38"), "Secret Testament", value = 82, defense = 2):
  val power = PrintedPower(PowerId("relic.secret-testament"),
    persistent = true, cost = Cost.free,
    text = "When an enemy takes a banner from you, take all [favor] or " +
      "[secret] on the banner.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Secret Testament (relic R38), a persistent Challenge modifier: "When an
  * enemy takes a banner from you, take all [favor] or [secret] on the
  * banner." (catalog batch 8 rulings, slice 6).
  *
  * When an enemy challenges for a banner its faceup holder holds, it
  * replaces the Challenge's ribbon: the holder takes every favor or secret
  * on the banner, in place of the favor's return to the banks, or the
  * Darkest Secret's retained half and the secrets placed on sites. The
  * challenger then pays onto the empty banner and takes it. A Campaign or a
  * card that takes a banner walks no ribbon, so it changes nothing there.
  *
  * Its line names the holder, whose turn it never is: "{Red} took 2 favor
  * from the {People's Favor}." An empty banner writes none.
  */
case object SecretTestament extends ContributingPower:
  val cardId: RelicId = SecretTestamentCard.id
  val id: PowerId = SecretTestamentCard.power.id

  /** "{Red} took 2 favor from the {People's Favor}." */
  val tookFavor: NoteKey = NoteKey("took-favor", Vector(NotePart.Arg(0),
    NotePart.Text(" took "), NotePart.Arg(1),
    NotePart.Text(" favor from the "), NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} took 2 secrets from the {Darkest Secret}." */
  val tookSecrets: NoteKey = NoteKey("took-secrets", Vector(
    NotePart.Arg(0), NotePart.Text(" took "), NotePart.Arg(1),
    NotePart.Text(" "), NotePart.Plural(1, "secret", "secrets"),
    NotePart.Text(" from the "), NotePart.Arg(2), NotePart.Text(".")))

  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] = Vector(tookFavor, tookSecrets)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ChallengeRibbon -> Vector(Transform((ctx, children) =>
      challenged(ctx).fold(children)((banner, holder) => Vector(
        BuildOps((ready, _) => Right(taken(ready, banner, holder))),
        Note(id, line(banner, holder)))))))

  /** The banner an enemy of its holder challenges for, and that holder. */
  private def challenged(ctx: PowerCtx): Option[(Banner, PlayerId)] = for
    banner <- ctx.answered.collectFirst {
      case Answered(ChallengeProcedure.bannerDecisionId,
          DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Banner(chosen)),
          _) => chosen }
    holder <- BannerRules.holder(ctx.state.game.current, banner)
    if holder != ctx.activePlayer && holderOf(ctx.state).contains(holder)
  yield (banner, holder)

  /** Every favor or secret on `banner`, moved to `holder`. */
  private def taken(ready: ReadyGame, banner: Banner, holder: PlayerId)
      : Vector[CoreOperation] =
    val amount = BannerRules.resources(ready.game.current, banner)
    Option.when(amount > 0)(Move(piece(banner, amount),
      PositionedLocation(Location.OnBanner(banner)),
      PositionedLocation(Location.PlayArea(holder))): CoreOperation).toVector

  private def piece(banner: Banner, amount: Int): Piece = banner match
    case Banner.PeoplesFavor => Piece.Favor(amount)
    case Banner.DarkestSecret => Piece.Secrets(amount)

  /** What the holder took, read from the banner before the take. */
  private def line(banner: Banner, holder: PlayerId)(states: NoteStates)
      : Option[PowerNote] = for
    step <- states.previous
    amount = BannerRules.resources(step._1.game.current, banner)
    if amount > 0
  yield
    val key = banner match
      case Banner.PeoplesFavor => tookFavor
      case Banner.DarkestSecret => tookSecrets
    key(PowerSourceRef.Card(cardId), NoteArg.Player(holder),
      NoteArg.Number(amount), NoteArg.Banner(banner))

  /** The player holding the relic faceup, if any. */
  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.relics.exists {
      case RelicState(`cardId`, Orientation.FaceUp, _) => true
      case _ => false
    }).map(_.player)
```

- [ ] **Step 5: Register it.** In `WalkerPowerCatalog.default`, add `Vector(SecretTestament)` after `Vector(BrassArmyMoves)`, and `SecretTestament` to the `oathdigital.gameplay.powers.banner` import. Add to its doc comment: "Secret Testament is inert until a Challenge walks `ChallengeRibbon`."

In `PowerKindsCatalogSuite`, add `"relic.secret-testament"` at the end of `persistentRules`.

- [ ] **Step 6: Run the tests to verify they pass.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.banner.SecretTestamentSuite oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.ChallengeProcedureSuite oathdigital.gameplay.cards.CardCatalogSuite"`
Expected: PASS.

Run: `./sbtw test`
Expected: PASS.

- [ ] **Step 7: Commit.**

```bash
git add src/main/scala/oathdigital/gameplay/powers/banner/SecretTestament.scala src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala src/test/scala/oathdigital/gameplay/powers/banner/SecretTestamentSuite.scala
git commit -m "feat(batch8): Secret Testament takes a challenged banner's tokens"
```

---

### Task 6: Gates and docs

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` (Status line)
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Run the gates.**

Run, in order:

1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass.

- The server count is your baseline plus 13.
- The frontend count is unchanged.

- [ ] **Step 2: Check the client.**

Run: `grep -rn 'brass-army\|secret-testament\|Brass Army\|Secret Testament' frontend/src/main`
Expected: nothing. The client shows Brass Army's question through the generic decision rendering, and both cards' lines through the generic note rendering. Report anything else you find instead of changing it.

- [ ] **Step 3: Update the docs.**
  - In the design, change the Status line to "Slices 1 to 6 are done."
  - In `docs/ROADMAP.md`:
    - check the slice 6 box: `- [x] **6. Operation triggers:** Secret Testament, Brass Army.`;
    - set the Now line to "**Phase - Catalog batch 8** is under way: slices 1 to 6 are done, and slice 7 is next."

Run: `python3 scripts/check-markdown-links.py`
Expected: PASS.

- [ ] **Step 4: Commit.**

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/ROADMAP.md
git commit -m "docs(batch8): slice 6 done"
```
