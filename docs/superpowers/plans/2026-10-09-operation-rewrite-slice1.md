# Operation Rewrite, Slice 1 (The Utility and Wild Mounts) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A transform finds the operations its window holds by what they are, even inside closures, and a power can hold an operation and resolve it later in the same action. Wild Mounts moves onto both, so `PlanDiscard.Marked` goes.

**Architecture:**

- **Fold phase.** `Transform` gains `phase: FoldPhase` (`Normal` by default, or `Late`). `ContributionCollector.gather` folds every `Late` transform after every `Normal` one. Priority orders transforms within a phase. A battle plan's `rewrites` always fold `Late`.
- **`OperationRewrite`** is a helper a transform calls on its children. It is not a walker change.
  - `ops` maps core ops to core ops, including the ops a closure builds.
  - `nodes` maps any node to any nodes, in the tree only.
  - Both descend `Sequence`, `Repeat` and `Branch`; `ops` also enters `BuildOps`. Both stop at a nested node that has its own window.
- **`LossRewrite` calls `ops`.** `ops` needs the fold's `PowerCtx`, so `BattlePlan.wrapping` now receives it (`BattlePlan.Wrap`).
- **Deferral:**
  - New state field: `CurrentGameState.walkerDeferred`.
  - Two new primitive ops: `Defer` holds ops under a `DeferralKey`, and `TakeDeferred` drops them.
  - New helper: `Deferred.resolve`, a `Branch` that resolves what a key holds and takes it in its last step.
  - The walker clears the field when an action ends.
- **Wild Mounts:**
  - Its root rewrite holds every discard of a Nomad card chosen as a plan on its side, however the plan built that discard.
  - It appends a `Deferred.resolve` that asks, swaps, or runs the held discards.
  - `Priority = 10` and `PlanDiscard.Marked` are deleted.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. There is no frontend change.

**Spec:** Read `docs/superpowers/specs/2026-10-09-operation-rewrite-design.md` before starting. Task 1 amends it with the rulings made at plan time, so re-read it after Task 1.

**Starts from:** `main` at `c3d48bfc` or later (modifier walker slice 4 merged).

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`.
  - An unused import or private member fails the build. Each step lists the imports it needs.
  - A refutable pattern in a `val` also fails it, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). `WalkerOperationCodec.scala` is at 749 before Task 4.
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - No power name appears in walker sources.
  - `gameplay/powerresolver` imports only `model` and its own package.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them. Re-wrap any line in this plan that comes out longer.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
  - Run `ExitWorktree` with `keep` before merging into `main`. Do not push.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Refactor tasks change no test.** Tasks 3 and 5 rewrite power code under existing suites. If an existing test fails there and the fix would change the test, stop and report: it marks a behavior change, which needs a ruling (spec ruling 11).
- **Texts.** Wild Mounts' decision, button and lines stay exactly as they are. A held discard that runs writes the plan's own "Discarded after the Campaign." line (`PlanDiscard.discarded`), under the plan's power.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 21 server tests and no frontend test. It rewrites no existing test.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

Task 1 writes these into the spec.

1. **`BattlePlan.wrapping` receives the fold's `PowerCtx`.** `OperationRewrite.ops` needs the fold's state, answers and folded node. Every `LossRewrite` user calls it from a `wrapping`. So the wrapping function becomes `BattlePlan.Wrap = (PowerCtx, PlanUse, Vector[Operation]) => Vector[Operation]`, and every override takes the extra argument.
2. **Held ops are stored canonically.** `Defer` stores ops as the journal records them (`OperationRun.canonical`), so a game reloaded from its events holds the same ops as the live game. As a result, a held `Discard.Denizen` is not `required`, and `OperationRun.canonical` canonicalises a `Defer`'s ops as well.
3. **A held discard's line goes under the plan's power.** The resolve writes "Discarded after the Campaign." under the card's printed power, which every plan that discards at the end declares (`PlanDiscard.discarded`).
   - Wild Mounts' own lines stay its two swap lines.
   - This replaces the spec's "Wild Mounts' `noteKeys` gain `PlanDiscard.discarded`".
   - The reason: `WildMountsSuite`'s Skip test asserts that Wild Mounts writes no line, and a line under Wild Mounts' power would break it.
4. **The gather order follows the fold phases.**
   - Each power's place in `GatheredContributions.order` is set first by the earliest phase of its transforms at the window, then by `sortKey`.
   - The transforms are then sorted stably by phase.
   - When no `Late` transform is present, both orders are exactly as before.
5. **The node being folded.** It is `ctx.operation`.
   - When that node is a leaf, a leaf below it with the same window counts as the folded node too, because the walker does not fold that window again on the branch (`WalkerHooks.gathered`).
   - Every other node below that carries a window is left alone.
6. **Windows on rewritten nodes.** `Branch` and `Repeat` carry no window, so the spec's "a wrapped `Branch` and `Repeat` keep their `window`" says nothing. A wrapped `BuildOps` keeps its `window` and `required`. A `Sequence` keeps its `window`.
7. **Several replacements, or none.** Either goes in a `Sequence` in the op's place, as `LossRewrite` does now. This holds both in the tree and in what a `Branch` selects. The ops a `BuildOps` builds are a flat batch, so there the replacements are spliced in.
8. **`LossRewrite` leaves slice 2's list.** It moves in this slice.

## File map

| File | Change | Task |
|---|---|---|
| `docs/superpowers/specs/2026-10-09-operation-rewrite-design.md` | modify | 1, 6 |
| `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala` | modify | 1 |
| `src/main/scala/oathdigital/gameplay/powerresolver/ContributionCollector.scala` | modify | 1 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala` | modify | 1, 3, 5 |
| `src/test/scala/oathdigital/gameplay/ContributionCollectorSuite.scala` | modify | 1 |
| `src/test/scala/oathdigital/gameplay/powers/campaign/WildMountsSuite.scala` | modify | 1, 5 |
| `src/main/scala/oathdigital/gameplay/powerresolver/OperationRewrite.scala` | create | 2 |
| `src/test/scala/oathdigital/gameplay/powerresolver/OperationRewriteSuite.scala` | create | 2 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/LossRewrite.scala` | modify | 3 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/{Hospital,CursedCauldron,SparedForce,BillowingFog,TravelingDoctor,VictoriousNow,PeaceEnvoy,StickyFire,MountedPatrol,ShieldWall}.scala` | modify | 3 |
| `src/main/scala/oathdigital/model/CoreOperations.scala` | modify | 4 |
| `src/main/scala/oathdigital/model/GameState.scala` | modify | 4 |
| `src/main/scala/oathdigital/gameplay/operations/TurnStateOperations.scala` | modify | 4 |
| `src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala` | modify | 4 |
| `src/main/scala/oathdigital/gameplay/operations/OperationPipeline.scala` | modify | 4 |
| `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala` | modify | 4 |
| `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` | modify | 4 |
| `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala` | modify | 4 |
| `src/main/scala/oathdigital/gameplay/powerresolver/Deferred.scala` | create | 4 |
| `src/test/scala/oathdigital/gameplay/DeferralSuite.scala` | create | 4 |
| `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala` | modify | 4 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/WildMounts.scala` | modify | 5 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/PlanDiscard.scala` | modify | 5 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/PlanContext.scala` | modify | 5 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/WarningSignals.scala` | modify (doc) | 5 |
| `docs/ROADMAP.md` | modify | 6 |

---

### Task 1: The fold phase, and the spec's plan-time rulings

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-operation-rewrite-design.md`
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributionCollector.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`
- Test: `src/test/scala/oathdigital/gameplay/ContributionCollectorSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/WildMountsSuite.scala`

**Interfaces:**
- Produces: `enum FoldPhase { case Normal, Late }` in `oathdigital.gameplay.powerresolver`. `Transform(fn, phase: FoldPhase = FoldPhase.Normal)`. A battle plan's `rewrites` entries fold `Late`.

- [ ] **Step 1: Amend the spec**

In `docs/superpowers/specs/2026-10-09-operation-rewrite-design.md`, make these replacements. Re-wrap each paragraph at the width of the text around it.

1. Under "### `OperationRewrite`", replace the bullet

   ```
   - A wrapped `BuildOps` keeps its `window` and `required`. A wrapped
     `Branch` and `Repeat` keep their `window`.
   ```

   with

   ```
   - A wrapped `BuildOps` keeps its `window` and `required`, and a
     `Sequence` keeps its `window`. `Branch` and `Repeat` carry no window.
   - The node being folded is `ctx.operation`. When it is a leaf, a leaf
     below it with the same window counts as it too, since the walker does
     not fold that window again on the branch (`WalkerHooks.gathered`).
   - Several replacements, or none, go in a `Sequence` in the op's place,
     in the tree and in what a `Branch` selects. What a `BuildOps` builds
     is a flat batch, so there they are spliced in.
   ```

2. Replace

   ```
   `LossRewrite.apply` becomes a thin call to `ops`, and its private `rewrite`
   is deleted.
   ```

   with

   ```
   `LossRewrite.apply` becomes a thin call to `ops`, and its private `rewrite`
   is deleted. `ops` needs the fold's context, and every `LossRewrite` user
   calls it from a `wrapping`, so `BattlePlan.wrapping` is handed the
   `PowerCtx` too (`BattlePlan.Wrap`).
   ```

3. At the end of the "**State.**" paragraph under "### Deferral" (after `owns it.`), add:

   ```
   `Defer` stores ops as the journal records them (`OperationRun.canonical`),
   so a game reloaded from its events holds the same ops: a held
   `Discard.Denizen` is not `required`.
   ```

4. Under "### Fold phase", after `step lists them in that fold order.`, add:

   ```
   Each power's place in that order is set by the earliest phase of its
   transforms at the window, then by its sort key.
   ```

5. Under "### Wild Mounts", replace

   ```
     Each held discard that runs writes "Discarded after the Campaign." with
     the plan's card as its source. Wild Mounts' `noteKeys` gain
     `PlanDiscard.discarded`.
   ```

   with

   ```
     Each held discard that runs writes "Discarded after the Campaign." with
     the plan's card as its source, under the plan's own power: the card's
     printed power, which declares `PlanDiscard.discarded`. Wild Mounts
     writes only its swap lines.
   ```

6. Under "### Migrations (slice 2)", replace `  - `LossRewrite` (its 5 users are untouched) and Disciples;` with `  - Disciples (`LossRewrite` moved in slice 1);`.

Run: `python3 scripts/check-markdown-links.py`
Expected: pass for this file.

- [ ] **Step 2: Write the failing collector test**

In `src/test/scala/oathdigital/gameplay/ContributionCollectorSuite.scala`, add this test at the end of the class. `powerresolver._` is already imported.

```scala
  test("a Late transform folds after a Normal one of higher priority, and " +
      "priority orders within a phase"):
    val powers = Vector(
      fixturePower("power.late-high", "a", Set(window),
        Vector(Transform((_, ops) => ops, FoldPhase.Late)),
        priorityValue = 5),
      fixturePower("power.late-low", "b", Set(window),
        Vector(Transform((_, ops) => ops, FoldPhase.Late))),
      fixturePower("power.normal-high", "c", Set(window),
        Vector(Transform((_, ops) => ops)), priorityValue = 9))

    val gathered = ContributionCollector.gather(
      window, PowerWindowIndex.of(powers), ctxFor(_, window))

    val folded = Vector("power.normal-high", "power.late-low",
      "power.late-high").map(PowerId(_))
    assertEquals(gathered.transforms.map(_._1), folded)
    assertEquals(gathered.order, folded)
```

- [ ] **Step 3: Write the failing Wild Mounts phase test**

In `src/test/scala/oathdigital/gameplay/powers/campaign/WildMountsSuite.scala`, add these imports:

```scala
import oathdigital.gameplay.powerresolver.{FoldPhase, Transform}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
```

and add this test at the end of the class:

```scala
  test("its rewrite folds Late, after every plan's own steps at the root"):
    val phases = WildMounts.forCatalog(catalog).contributions(
      PowerWindow.CampaignActionEligibility).collect {
        case transform: Transform => transform.phase }
    assertEquals(phases, Vector(FoldPhase.Late))
```

- [ ] **Step 4: Run both tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.ContributionCollectorSuite oathdigital.gameplay.powers.campaign.WildMountsSuite"`
Expected: compile failure, `Not found: FoldPhase`.

- [ ] **Step 5: Add `FoldPhase` and `Transform.phase`**

In `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`, replace the `Transform` case class and its doc with:

```scala
/** Rewrites the hooked node's children. Covers must-effects (insert ops),
  * cost changes (modify the pay ops), and reordering (roll order).
  *
  * `fn` MUST be a pure function of the state it is handed: the walker derives
  * the tree and re-folds every window on every command (S1), so a fold that
  * differed between two commands would make a recorded park position address
  * a different node on resume. This is the same invariant `Branch.select`
  * already carries.
  *
  * `phase` says when it folds among the window's transforms.
  */
final case class Transform(
    fn: (PowerCtx, Vector[Operation]) => Vector[Operation],
    phase: FoldPhase = FoldPhase.Normal
) extends Contribution

/** When a transform folds at its window (operation rewrite design, ruling
  * 9). Every `Late` transform folds after every `Normal` one, and power
  * priority orders the transforms within a phase. A transform that must see
  * what the window's other transforms added folds `Late`.
  */
enum FoldPhase:
  case Normal, Late
```

- [ ] **Step 6: Order the gather by phase**

In `src/main/scala/oathdigital/gameplay/powerresolver/ContributionCollector.scala`:

Replace

```scala
    // Step 4: deterministic order.
    val ordered = survivors.sortBy(hooked =>
      ContributingPower.sortKey(hooked.power))
```

with

```scala
    // Step 4: deterministic order: by the earliest fold phase of each
    // power's transforms here, then by sort key. Without a `Late`
    // transform this is the sort key alone.
    val ordered = survivors.sortBy(hooked =>
      (earliestPhase(hooked), ContributingPower.sortKey(hooked.power)))
```

Replace `      transforms = transforms.result(),` with:

```scala
      // Stable, so priority still orders the transforms within a phase.
      transforms = transforms.result().sortBy(_._2.phase.ordinal),
```

Add this member at the end of `object ContributionCollector`:

```scala
  /** The earliest phase of `hooked`'s transforms at the window, as its
    * ordinal; `Normal` when it has none. */
  private def earliestPhase(hooked: HookedPower): Int =
    hooked.contributions.collect { case transform: Transform =>
      transform.phase.ordinal }.minOption
      .getOrElse(FoldPhase.Normal.ordinal)
```

- [ ] **Step 7: Fold a plan's rewrites `Late`**

In `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`:

Add `FoldPhase` to the `powerresolver` import:

```scala
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  FoldPhase, Offer, OptionRestriction, Transform}
```

Replace the doc bullet

```
  *  - `rewrites` change a window's children whether or not the plan was
  *    chosen. Such a rewrite keeps the children's shape the same on every
  *    fold and learns inside a `Branch`, at walk time, whether the plan was
  *    used, which also sees a bandit's use (Wild Mounts).
```

with

```
  *  - `rewrites` change a window's children whether or not the plan was
  *    chosen. They fold `Late`, after every other transform at the window,
  *    so they see what the other plans added there. Such a rewrite keeps
  *    the children's shape the same on every fold and learns at walk time
  *    whether the plan was used, which also sees a bandit's use (Wild
  *    Mounts).
```

In `contributions`, replace `        rewrites.get(window).toVector ++` with:

```scala
        rewrites.get(window).toVector.map(_.copy(phase = FoldPhase.Late)) ++
```

- [ ] **Step 8: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.ContributionCollectorSuite oathdigital.gameplay.ContributionIgnoresSuite oathdigital.gameplay.powers.campaign.*"`
Expected: pass. Wild Mounts still has `Priority = 10`, and it folds last either way.

- [ ] **Step 9: Commit**

```bash
git add docs/superpowers/specs/2026-10-09-operation-rewrite-design.md src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala src/main/scala/oathdigital/gameplay/powerresolver/ContributionCollector.scala src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala src/test/scala/oathdigital/gameplay/ContributionCollectorSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/WildMountsSuite.scala
git commit -m "feat(powers): a transform folds Normal or Late, and plan rewrites fold Late"
```

---

### Task 2: `OperationRewrite`

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powerresolver/OperationRewrite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powerresolver/OperationRewriteSuite.scala`

**Interfaces:**
- Consumes: `PowerCtx` (`state`, `nodePath`, `answered`, `operation`).
- Produces:

  ```scala
  object OperationRewrite:
    type Matcher[A, B] =
      (ReadyGame, PendingTree) => PartialFunction[A, Vector[B]]
    def ops(ctx: PowerCtx, children: Vector[Operation])(
        matcher: Matcher[CoreOperation, CoreOperation]): Vector[Operation]
    def nodes(ctx: PowerCtx, children: Vector[Operation])(
        matcher: Matcher[Operation, Operation]): Vector[Operation]
  ```

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powerresolver/OperationRewriteSuite.scala`:

```scala
package oathdigital.gameplay.powerresolver

import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Unwrap.as

/** A composite that is not the walker's own and carries no window, as a
  * custom composite inside its window's subtree does. */
private final case class WindowlessComposite(
    override val children: Vector[Operation]) extends Operation

/** `OperationRewrite`: what it descends, what it leaves alone, and when it
  * matches (operation rewrite design). Pure: no walker and no catalog. */
class OperationRewriteSuite extends munit.FunSuite:
  private val actor = Table.p1
  private val foldState = Table.start.ready
  private val walkState = Table.start.supply(actor, 1).ready
  private val walkAnswers = PendingTree(Vector("0", "2"), Vector.empty)
  private val window = PowerWindow.RecoverEligibility
  private val otherWindow = PowerWindow.RecoverBeforeFirstRoll
  private val gainOne: GainSupply = GainSupply(actor, 1)

  /** The context of folding `operation` at `window`, with `foldState`. */
  private def foldingCtx(operation: Operation): PowerCtx =
    PowerCtx(foldState, actor, RuleSourceRef.GameRule("test"), window,
      Vector("0"), operation)

  /** The context of folding a windowed `Sequence`. */
  private val foldingSequence: PowerCtx =
    foldingCtx(Sequence(Vector.empty, Some(window)))

  /** A supply gain becomes a gain of 2 when matched with the fold's state,
    * and of 3 when matched with the walk's. */
  private val gainByMoment
      : OperationRewrite.Matcher[CoreOperation, CoreOperation] =
    (ready, _) => {
      case GainSupply(player, _) =>
        Vector(GainSupply(player, if ready eq walkState then 3 else 2))
    }

  /** The node kinds of `operation` and its static children, in order. */
  private def kinds(operation: Operation): Vector[String] = operation match
    case sequence: Sequence => "Sequence" +: sequence.children.flatMap(kinds)
    case repeat: Repeat => "Repeat" +: kinds(repeat.body)
    case other => Vector(other.getClass.getSimpleName)

  test("a core op among the children is matched at fold time, with the " +
      "fold's state"):
    val rewritten = OperationRewrite.ops(foldingSequence,
      Vector(gainOne, SpendSupply(actor, 1)))(gainByMoment)
    assertEquals(rewritten, Vector[Operation](GainSupply(actor, 2),
      SpendSupply(actor, 1)))

  test("several replacements for one op go in a Sequence in its place, " +
      "and none leave an empty one"):
    val rewritten = OperationRewrite.ops(foldingSequence,
      Vector(gainOne, SpendSupply(actor, 1)))((_, _) => {
        case gain: GainSupply => Vector(gain, gain)
        case _: SpendSupply => Vector.empty
      })
    assertEquals(rewritten, Vector[Operation](
      Sequence(Vector(gainOne, gainOne)), Sequence(Vector.empty)))

  test("a Sequence's children and a Repeat's body are rewritten, and the " +
      "Repeat keeps its guard"):
    val guard: (ReadyGame, PendingTree) => Boolean = (_, _) => false
    val rewritten = OperationRewrite.ops(foldingSequence, Vector(
      Sequence(Vector(gainOne)), Repeat(guard, gainOne)))(gainByMoment)
    assertEquals(rewritten.head, Sequence(Vector(GainSupply(actor, 2))))
    val repeat = as[Repeat](rewritten(1))
    assert(repeat.guard eq guard)
    assertEquals(repeat.body, GainSupply(actor, 2))

  test("a BuildOps' ops are matched when the walk builds them, with the " +
      "walk's state, and its closure does not run at fold time"):
    var builds = 0
    val build = BuildOps((_, _) => { builds += 1; Right(Vector(gainOne)) })
    val rewritten = as[BuildOps](OperationRewrite.ops(foldingSequence,
      Vector(build))(gainByMoment).head)
    assertEquals(builds, 0)
    assertEquals(rewritten.build(walkState, walkAnswers),
      Right(Vector[CoreOperation](GainSupply(actor, 3))))
    assertEquals(builds, 1)

  test("a windowed leaf handed to its own transforms is entered, and keeps " +
      "its window and required"):
    val leaf = BuildOps((_, _) => Right(Vector(gainOne)), Some(window),
      required = true)
    val rewritten = as[BuildOps](OperationRewrite.ops(foldingCtx(leaf),
      Vector(leaf))(gainByMoment).head)
    assertEquals(rewritten.window, Some(window))
    assert(rewritten.required)
    assertEquals(rewritten.build(walkState, walkAnswers),
      Right(Vector[CoreOperation](GainSupply(actor, 3))))

  test("a Branch's selection is rewritten when the walk selects it, with " +
      "the walk's state"):
    var selections = 0
    val branch = Branch((_, _) => { selections += 1; Vector(gainOne) })
    val rewritten = as[Branch](OperationRewrite.ops(foldingSequence,
      Vector(branch))(gainByMoment).head)
    assertEquals(selections, 0)
    assertEquals(rewritten.select(walkState, walkAnswers),
      Vector[Operation](GainSupply(actor, 3)))

  test("two folds give the same shape"):
    val children = Vector[Operation](Branch((_, _) => Vector(gainOne)),
      BuildOps((_, _) => Right(Vector(gainOne))), Sequence(Vector(gainOne)),
      gainOne)
    def fold = OperationRewrite.ops(foldingSequence, children)(gainByMoment)
    assertEquals(fold.flatMap(kinds), fold.flatMap(kinds))
    assertEquals(fold.flatMap(kinds), children.flatMap(kinds))

  test("a node with its own window and a custom composite are left alone"):
    val nested = Sequence(Vector(gainOne), Some(otherWindow))
    val custom = WindowlessComposite(Vector(gainOne))
    val rewritten = OperationRewrite.ops(foldingSequence,
      Vector(nested, custom))(gainByMoment)
    assert(rewritten(0) eq nested)
    assert(rewritten(1) eq custom)

  test("a leaf with the fold's window is left alone below a composite, and " +
      "entered below the leaf being folded"):
    val ask = Decide("test.ask", actor, DecisionQuery.ChooseOne(Vector.empty),
      window = Some(window))
    val dropAsks: OperationRewrite.Matcher[CoreOperation, CoreOperation] =
      (_, _) => { case _: Decide => Vector.empty }
    assertEquals(OperationRewrite.ops(foldingSequence, Vector(ask))(dropAsks),
      Vector[Operation](ask))
    assertEquals(OperationRewrite.ops(foldingCtx(ask), Vector(ask))(dropAsks),
      Vector[Operation](Sequence(Vector.empty)))

  test("a semantic composite is matched whole, never entered"):
    val discard = Discard.Denizen(DenizenId("153"),
      PositionedLocation(Location.PlayArea(actor)), Region.Cradle,
      Suit.Nomad, favor = 0, secrets = 0, actingPlayer = actor)
    val dropMoves: OperationRewrite.Matcher[CoreOperation, CoreOperation] =
      (_, _) => { case _: Move => Vector.empty }
    assert(OperationRewrite.ops(foldingSequence, Vector(discard))(dropMoves)
      .head eq discard)
    assertEquals(OperationRewrite.ops(foldingSequence, Vector(discard))(
      (_, _) => { case _: Discard.Denizen => Vector(gainOne) }),
      Vector[Operation](gainOne))

  test("nodes replaces a BuildOps whole without entering it, and leaves one " +
      "it does not match as it is"):
    var builds = 0
    val build = BuildOps((_, _) => { builds += 1; Right(Vector(gainOne)) })
    val note = Note(PowerId("test"), _ => None)
    assertEquals(OperationRewrite.nodes(foldingSequence, Vector(build))(
      (_, _) => { case _: BuildOps => Vector(note) }),
      Vector[Operation](note))
    assert(OperationRewrite.nodes(foldingSequence, Vector(build))(
      (_, _) => { case _: WindowlessComposite => Vector.empty }).head eq build)
    assertEquals(builds, 0)

  test("nodes matches a custom composite in the tree and in what a Branch " +
      "selects"):
    val custom = WindowlessComposite(Vector(gainOne))
    val unwrap: OperationRewrite.Matcher[Operation, Operation] =
      (_, _) => { case found: WindowlessComposite => found.children }
    assertEquals(OperationRewrite.nodes(foldingSequence, Vector(custom))(
      unwrap), Vector[Operation](gainOne))
    val branch = as[Branch](OperationRewrite.nodes(foldingSequence,
      Vector(Branch((_, _) => Vector(custom))))(unwrap).head)
    assertEquals(branch.select(walkState, walkAnswers),
      Vector[Operation](gainOne))
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powerresolver.OperationRewriteSuite"`
Expected: compile failure, `Not found: OperationRewrite`.

- [ ] **Step 3: Write `OperationRewrite`**

Create `src/main/scala/oathdigital/gameplay/powerresolver/OperationRewrite.scala`:

```scala
package oathdigital.gameplay.powerresolver

import oathdigital.model.{Branch, BuildOps, CoreOperation, Operation,
  PendingTree, PrimitiveOperation, ReadyGame, Repeat, Sequence}

/** Finds the operations a window's children hold by what they are, and
  * replaces them (operation rewrite design). A transform calls it on its
  * children; the walker knows nothing of it.
  *
  * It descends through `Sequence`, `Repeat`, `Branch` and `BuildOps`, and
  * leaves alone any node below that carries a window of its own: that
  * subtree belongs to its window's powers. The node being folded is entered
  * even when windowed, since a windowed leaf is handed to its own transforms
  * as its only child, and the walker does not fold that window again below
  * it.
  *
  * An operation in the tree is matched when the window folds, with the
  * fold's state and answers. One a `Branch` selects or a `BuildOps` builds
  * is matched when the walk reaches it, with that moment's state and
  * answers, so the tree keeps its shape on every fold. What a match returns
  * is not matched again. One replacement takes the operation's place;
  * several, or none, go in a `Sequence` there. The ops a `BuildOps` builds
  * are a flat batch, so there the replacements are spliced in.
  */
object OperationRewrite:
  /** What to replace, given the state and answers of the moment it is
    * matched. An operation it is not defined at stays as it is. */
  type Matcher[A, B] =
    (ReadyGame, PendingTree) => PartialFunction[A, Vector[B]]

  /** `children` with each core operation `matcher` matches replaced,
    * including those a closure builds. A core composite such as
    * `Discard.Denizen` is matched whole; only the walker's own composites
    * are descended. Any other node stays as it is. */
  def ops(ctx: PowerCtx, children: Vector[Operation])(
      matcher: Matcher[CoreOperation, CoreOperation]): Vector[Operation] =
    children.map(Ops(Scope(ctx), matcher).at(ctx.state, folding(ctx)))

  /** `children` with each node `matcher` matches replaced, in the tree only.
    * A `BuildOps` or a `Branch` it matches is replaced whole, and a
    * `BuildOps` it does not match is not entered. `Sequence` and `Repeat`
    * are descended, never matched. */
  def nodes(ctx: PowerCtx, children: Vector[Operation])(
      matcher: Matcher[Operation, Operation]): Vector[Operation] =
    children.map(Nodes(Scope(ctx), matcher).at(ctx.state, folding(ctx)))

  /** The answers as the fold sees them. */
  private def folding(ctx: PowerCtx): PendingTree =
    PendingTree(ctx.nodePath, ctx.answered)

  /** One replacement in the operation's place; several, or none, in a
    * `Sequence` there. */
  private def placed(replacements: Vector[Operation]): Operation =
    replacements match
      case Vector(one) => one
      case several => Sequence(several)

  /** Which nodes below the fold belong to it. */
  private final case class Scope(ctx: PowerCtx):
    /** Whether `node` carries a window of its own, which its own powers
      * fold. */
    def nested(node: Operation): Boolean =
      node.window.nonEmpty && !folded(node)

    /** Whether `node` is the windowed leaf being folded, or a leaf below it
      * with its window (`WalkerHooks.gathered`). */
    private def folded(node: Operation): Boolean = ctx.operation match
      case leaf: PrimitiveOperation =>
        node.isInstanceOf[PrimitiveOperation] && node.window == leaf.window
      case _ => false

  private final case class Ops(scope: Scope,
      matcher: Matcher[CoreOperation, CoreOperation]):
    def at(ready: ReadyGame, pending: PendingTree)(node: Operation)
        : Operation = node match
      case _ if scope.nested(node) => node
      case build: BuildOps => build.copy(build = (now, answers) =>
        build.build(now, answers).map(_.flatMap(operation =>
          matcher(now, answers).applyOrElse(operation,
            (same: CoreOperation) => Vector(same)))))
      case branch: Branch => Branch((now, answers) =>
        branch.select(now, answers).map(at(now, answers)))
      case sequence: Sequence =>
        sequence.copy(children = sequence.children.map(at(ready, pending)))
      case repeat: Repeat =>
        repeat.copy(body = at(ready, pending)(repeat.body))
      case core: CoreOperation =>
        matcher(ready, pending).lift(core).fold[Operation](core)(placed)
      case other => other

  private final case class Nodes(scope: Scope,
      matcher: Matcher[Operation, Operation]):
    def at(ready: ReadyGame, pending: PendingTree)(node: Operation)
        : Operation = node match
      case _ if scope.nested(node) => node
      case sequence: Sequence =>
        sequence.copy(children = sequence.children.map(at(ready, pending)))
      case repeat: Repeat =>
        repeat.copy(body = at(ready, pending)(repeat.body))
      case branch: Branch =>
        matcher(ready, pending).lift(branch).fold[Operation](
          Branch((now, answers) =>
            branch.select(now, answers).map(at(now, answers))))(placed)
      case other =>
        matcher(ready, pending).lift(other).fold[Operation](other)(placed)
```

- [ ] **Step 4: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powerresolver.OperationRewriteSuite"`
Expected: 12 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powerresolver/OperationRewrite.scala src/test/scala/oathdigital/gameplay/powerresolver/OperationRewriteSuite.scala
git commit -m "feat(powers): OperationRewrite finds a window's ops by what they are"
```

---

### Task 3: `LossRewrite` calls `ops`

This is a refactor: no test changes, and the campaign suites must pass unchanged. Two behaviors change on paper:

- `ops` enters `Repeat` bodies.
- `ops` stops at nested windows, where the old private `rewrite` entered them.

No Campaign window that `LossRewrite` touches holds a kill inside either, so no test should notice. If a test does fail, stop and report (Global Constraints).

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/LossRewrite.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`
- Modify: `Hospital.scala`, `CursedCauldron.scala`, `SparedForce.scala`, `BillowingFog.scala`, `TravelingDoctor.scala`, `VictoriousNow.scala`, `PeaceEnvoy.scala`, `StickyFire.scala`, `MountedPatrol.scala` and `ShieldWall.scala`, all in `src/main/scala/oathdigital/gameplay/powers/campaign/`

**Interfaces:**
- Consumes: `OperationRewrite.ops` (Task 2).
- Produces:
  - `BattlePlan.Wrap = (PowerCtx, PlanUse, Vector[Operation]) => Vector[Operation]`, and `BattlePlan.wrapping: Map[PowerWindow, BattlePlan.Wrap]`.
  - `LossRewrite.apply(ctx: PowerCtx, children: Vector[Operation])(f)`.
  - `LossRewrite.counted(pool, kind, owner, returns)(ctx: PowerCtx, children: Vector[Operation])`.

- [ ] **Step 1: Record the baseline**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.actions.campaign.*"`
Expected: pass. Note the test count.

- [ ] **Step 2: Hand `wrapping` the fold's context**

In `BattlePlan.scala`:

Add `PowerCtx` to the `powerresolver` import:

```scala
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  FoldPhase, Offer, OptionRestriction, PowerCtx, Transform}
```

Replace the `wrapping` member and its doc with:

```scala
  /** What a used plan does to a later window's children, as the window is
    * folded, given the fold's context: it may add before them as well as
    * after. It reads the state and answers the fold is made with, which are
    * the same on every resume of the window, so it must not read what the
    * window's own children change.
    */
  def wrapping: Map[PowerWindow, BattlePlan.Wrap] = Map.empty
```

In `contributions`, replace `          BattlePlan.outcomeKnown(window)).fold(children)(wrap(_, children))))` with:

```scala
          BattlePlan.outcomeKnown(window)).fold(children)(
            wrap(ctx, _, children))))
```

In `object BattlePlan`, add before `windowOf`:

```scala
  /** A plan's `wrapping` of a window: the fold's context, the plan's use and
    * the window's children. */
  type Wrap = (PowerCtx, PlanUse, Vector[Operation]) => Vector[Operation]
```

- [ ] **Step 3: Move `LossRewrite` onto `ops`**

Replace the whole of `LossRewrite.scala` with:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.powerresolver.{OperationRewrite, PowerCtx}
import oathdigital.model._

/** The core operations a Campaign window's children produce, rewritten one
  * at a time (R3, from Hospital's private tally) through
  * `OperationRewrite.ops`, which reaches inside the closures that build
  * them and leaves alone any node with a window of its own.
  *
  * Billowing Fog, Traveling Doctor and Peace Envoy rewrite the losses with
  * it, and `VictoriousNow` the recorded result. `counted` is the shared loss
  * counter: Hospital counts its user's dead with it, and Cursed Cauldron its
  * enemy's.
  */
private[campaign] object LossRewrite:
  def apply(ctx: PowerCtx, children: Vector[Operation])(
      f: CoreOperation => Vector[CoreOperation]): Vector[Operation] =
    OperationRewrite.ops(ctx, children)((_, _) => { case operation =>
      f(operation) })

  /** `children`, with each kill of `owner`'s warbands of force `kind` counted
    * in `pool`, a pool that is never rolled or shown and that the walker
    * clears with every pool when the Campaign ends. Bandits are no player,
    * so only their kills count. When `returns` is set, a warband returned
    * from the supply to the owner's board did not stay dead, so it comes
    * back out of the count; only the losses return warbands.
    *
    * It counts the amount each operation asks for, which the Campaign's
    * kills always execute in full because they read the board they kill
    * from. */
  def counted(pool: PoolKey, kind: ForceKind, owner: Option[PlayerId],
      returns: Boolean)(ctx: PowerCtx, children: Vector[Operation])
      : Vector[Operation] =
    apply(ctx, children) {
      case kill @ Kill(Piece.Warbands(`kind`, count), _) =>
        Vector(kill, ModifyDicePool(pool, count))
      case sacrifice @ Sacrifice(player, Piece.Warbands(`kind`, count), _)
          if owner.contains(player) =>
        Vector(sacrifice, ModifyDicePool(pool, count))
      case move @ Move(Piece.Warbands(`kind`, count),
          PositionedLocation(Location.WarbandBank(_), _),
          PositionedLocation(Location.PlayArea(player), _), None)
          if returns && owner.contains(player) =>
        Vector(move, ModifyDicePool(pool, -count))
      case other => Vector(other)
    }
```

- [ ] **Step 4: Update every `wrapping`**

In each file below, replace the two-line signature

```scala
  override def wrapping
      : Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]] = Map(
```

with `  override def wrapping: Map[PowerWindow, BattlePlan.Wrap] = Map(`. Then make that file's other edits.

- **`StickyFire.scala`:** replace `    PowerWindow.CampaignLosses -> ((use, losses) => (for` with `    PowerWindow.CampaignLosses -> ((_, use, losses) => (for`.
- **`ShieldWall.scala`:** the same replacement.
- **`MountedPatrol.scala`:** replace `    PowerWindow.CampaignAttackRoll -> ((_, children) => Vector(` with `    PowerWindow.CampaignAttackRoll -> ((_, _, children) => Vector(`.
- **`BillowingFog.scala` and `TravelingDoctor.scala`:** replace

  ```scala
      PowerWindow.CampaignLosses -> ((use, losses) =>
        SparedForce.spare(id, cardId, use, losses)))
  ```

  with

  ```scala
      PowerWindow.CampaignLosses -> ((ctx, use, losses) =>
        SparedForce.spare(ctx, id, cardId, use, losses)))
  ```

- **`SparedForce.scala`:** `PowerCtx` is already imported.
  - Change the head of `spare` to:

    ```scala
      def spare(ctx: PowerCtx, power: PowerId, card: DenizenId, use: PlanUse,
          losses: Vector[Operation]): Vector[Operation] = (for
    ```

  - Replace `    LossRewrite(losses)(kept(use.side, user, kind, result, atTargets)) :+ line` with:

    ```scala
        LossRewrite(ctx, losses)(kept(use.side, user, kind, result,
          atTargets)) :+ line
    ```

- **`Hospital.scala`:** add `import oathdigital.gameplay.powerresolver.PowerCtx`. Replace the `counted` method with:

  ```scala
    private def counted(returns: Boolean)(ctx: PowerCtx, use: PlanUse,
        children: Vector[Operation]): Vector[Operation] = (for
      user <- use.user
      kind <- PlayerFacts.forceKind(use.ready, user).toOption
    yield LossRewrite.counted(Hospital.Saved, kind, Some(user), returns)(ctx,
      children)).getOrElse(children)
  ```

  The map entries `counted(returns = false)` and `counted(returns = true)` stay as they are.
- **`CursedCauldron.scala`:** add `import oathdigital.gameplay.powerresolver.PowerCtx`.
  - Replace the two entries in `wrapping` with:

    ```scala
        PowerWindow.CampaignPlanApplication -> ((ctx, use, children) =>
          counted(ctx, use, children, returns = false)),
        PowerWindow.CampaignLosses -> ((ctx, use, losses) => (for
          user <- use.user
          if use.won.contains(true)
        yield counted(ctx, use, losses, returns = true) ++ Vector(
          gain(use, user),
          Note(id, NoteSupport.gainNote(CursedCauldron.gained, cardRef, user,
            NoteUnit.Warband, NoteSupport.warbands)))).getOrElse(losses)))
    ```

  - Replace the private `counted` with:

    ```scala
      private def counted(ctx: PowerCtx, use: PlanUse,
          children: Vector[Operation], returns: Boolean): Vector[Operation] =
        (for
          foe <- enemy(use)
          kind <- forceOf(use.ready, foe)
        yield LossRewrite.counted(CursedCauldron.Died, kind, playerOf(foe),
          returns)(ctx, children)).getOrElse(children)
    ```

- **`VictoriousNow.scala`:** replace the import line with:

  ```scala
  import oathdigital.gameplay.powerresolver.PowerCtx
  import oathdigital.model._
  ```

  Replace everything from `  def wrapping(line: PlanUse => Note)` to the end of the object with:

  ```scala
    def wrapping(line: PlanUse => Note): Map[PowerWindow, BattlePlan.Wrap] =
      Map(
        PowerWindow.CampaignAttackRoll -> unrolled,
        PowerWindow.CampaignSacrificeSelection -> unrolled,
        PowerWindow.CampaignDefenseRoll -> unrolled,
        PowerWindow.CampaignAfterOutcome -> ((ctx, use, children) =>
          recorded(ctx, use.side, children) :+ line(use)))

    private val unrolled: BattlePlan.Wrap = (_, _, _) => Vector.empty

    /** `children`, with the recorded result won by `winner`'s side. */
    private def recorded(ctx: PowerCtx, winner: CampaignPlanSide,
        children: Vector[Operation]): Vector[Operation] =
      LossRewrite(ctx, children) {
        case RecordCampaignResult(result) => Vector(RecordCampaignResult(
          result.copy(attackerWins = winner == CampaignPlanSide.Attacker)))
        case other => Vector(other)
      }
  ```

- **`PeaceEnvoy.scala`:** `PowerCtx` is already imported.
  - Replace the whole `wrapping` member with:

    ```scala
      override def wrapping: Map[PowerWindow, BattlePlan.Wrap] =
        VictoriousNow.wrapping(victory).updated(PowerWindow.CampaignLosses,
          (ctx: PowerCtx, use: PlanUse, losses: Vector[Operation]) =>
            spared(ctx, use, losses))
    ```

  - Change `spared`'s head to `  private def spared(ctx: PowerCtx, use: PlanUse, losses: Vector[Operation])`, re-wrapped as the file does, and its `    LossRewrite(losses) {` to `    LossRewrite(ctx, losses) {`.

Run: `grep -rn "(PlanUse, Vector\[Operation\])" src/main/scala`
Expected: no match.

- [ ] **Step 5: Run the campaign suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.actions.campaign.*"`
Expected: pass, with the same count as Step 1. If a test fails, stop and report the failure. Do not change the test.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/LossRewrite.scala src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala src/main/scala/oathdigital/gameplay/powers/campaign/Hospital.scala src/main/scala/oathdigital/gameplay/powers/campaign/CursedCauldron.scala src/main/scala/oathdigital/gameplay/powers/campaign/SparedForce.scala src/main/scala/oathdigital/gameplay/powers/campaign/BillowingFog.scala src/main/scala/oathdigital/gameplay/powers/campaign/TravelingDoctor.scala src/main/scala/oathdigital/gameplay/powers/campaign/VictoriousNow.scala src/main/scala/oathdigital/gameplay/powers/campaign/PeaceEnvoy.scala src/main/scala/oathdigital/gameplay/powers/campaign/StickyFire.scala src/main/scala/oathdigital/gameplay/powers/campaign/MountedPatrol.scala src/main/scala/oathdigital/gameplay/powers/campaign/ShieldWall.scala
git commit -m "refactor(powers): LossRewrite is a call to OperationRewrite.ops"
```

---

### Task 4: Deferral

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala`
- Modify: `src/main/scala/oathdigital/model/GameState.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/TurnStateOperations.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationPipeline.scala`
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala`
- Create: `src/main/scala/oathdigital/gameplay/powerresolver/Deferred.scala`
- Test: `src/test/scala/oathdigital/gameplay/DeferralSuite.scala`
- Test: `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala`

**Interfaces:**
- Produces:
  - In `model`: `final case class DeferralKey(value: String)`, `final case class Defer(key: DeferralKey, ops: Vector[CoreOperation]) extends PrimitiveOperation` and `final case class TakeDeferred(key: DeferralKey) extends PrimitiveOperation`.
  - `CurrentGameState.walkerDeferred: Map[DeferralKey, Vector[CoreOperation]]`.
  - In `powerresolver`: `Deferred.held(ready: ReadyGame, key: DeferralKey): Vector[CoreOperation]` and `Deferred.resolve(key: DeferralKey)(f: (ReadyGame, PendingTree, Vector[CoreOperation]) => Vector[Operation]): Operation`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/DeferralSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.gameplay.powerresolver.Deferred
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerCompleted}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.Outcomes
import oathdigital.testkit.Outcomes.{finished, parked, recorded}
import oathdigital.testkit.ProcedureWalkerFixture._
import oathdigital.testkit.Unwrap.accepted
import oathdigital.testkit.WalkerStubs.continueOption

/** Deferral (operation rewrite design): `Defer` and `TakeDeferred` through
  * the pipeline, the walker dropping what is held when the action ends, and
  * a resolve that parks on its question. */
class DeferralSuite extends munit.FunSuite:
  private val key = DeferralKey("test.held")

  /** `state` after running `ops` through the pipeline as one batch. */
  private def afterBatch(state: ReadyGame, ops: CoreOperation*): ReadyGame =
    accepted(OperationPipeline.run(state, ops.toVector,
      OperationPolicy.Permissive, Vector.empty)(Right(_))).state

  test("Defer holds ops under its key without running them, and " +
      "TakeDeferred drops them"):
    val held = afterBatch(ready, Defer(key, Vector(spendOneSupply)),
      Defer(key, Vector(movePawnToS3)))
    assertEquals(Deferred.held(held, key),
      Vector[CoreOperation](spendOneSupply, movePawnToS3))
    assertEquals(supplyOf(held), supplyOf(ready))
    assertEquals(pawnSiteOf(held), pawnSiteOf(ready))
    val taken = afterBatch(held, TakeDeferred(key))
    assertEquals(taken.game.current.walkerDeferred, Map.empty)

  test("a held op is kept as the journal records it"):
    val held = afterBatch(ready,
      Defer(key, Vector(SpendSupply(actor, 1, required = false))))
    assertEquals(Deferred.held(held, key),
      Vector[CoreOperation](SpendSupply(actor, 1)))

  test("the walker drops what is held when the action ends, live and on " +
      "replay"):
    val done = finished(ProcedureWalker.advance(ready,
      Defer(key, Vector(spendOneSupply)), None, noPowers))
    assertEquals(recorded(done.events.head).ops,
      Vector[CoreOperation](Defer(key, Vector(spendOneSupply))))
    assertEquals(done.treeless.game.current.walkerDeferred, Map.empty)
    val held = afterBatch(ready, Defer(key, Vector(spendOneSupply)))
    val completed = accepted(ProcedureWalker.applyRecorded(
      OathState.Ready(held), WalkerCompleted(ActionRef.Recover)))
    assertEquals(Outcomes.ready(completed).game.current.walkerDeferred,
      Map.empty)

  test("a resolve that parks on its question still finds the ops held on " +
      "resume, and takes them last"):
    val tree: Operation = Sequence(Defer(key, Vector(spendOneSupply)),
      Deferred.resolve(key)((_, _, held) =>
        Vector(decide, BuildOps((_, _) => Right(held)))))
    val park = parked(ProcedureWalker.advance(ready, tree, None, noPowers))
    assertEquals(park.tree.at, Vector("1", "0"))
    val atPark = applyEvents(ready, park.events)
    assertEquals(Deferred.held(atPark, key),
      Vector[CoreOperation](spendOneSupply))
    val done = finished(ProcedureWalker.resolve(atPark, tree, park.tree,
      Answered(decide.decisionId, ChooseOneAnswer(continueOption), actor),
      noPowers))
    assertEquals(done.events.map(recorded(_).ops), Vector(
      Vector.empty[CoreOperation], Vector[CoreOperation](spendOneSupply),
      Vector[CoreOperation](TakeDeferred(key))))
    assertEquals(supplyOf(done.treeless), supplyOf(ready) - 1)

  test("a resolve selects nothing while nothing is held"):
    val tree: Operation = Sequence(
      Deferred.resolve(key)((_, _, _) => Vector(decide)), spendOneSupply)
    val done = finished(ProcedureWalker.advance(ready, tree, None, noPowers))
    assertEquals(done.events.map(recorded(_).ops),
      Vector(Vector[CoreOperation](spendOneSupply)))
```

In `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala`, add at the end of the class:

```scala
  test("a deferral and its take round trip, with the held ops"):
    val key = DeferralKey("test.held")
    val held = Vector[CoreOperation](ModifyDicePool(PoolKey("test.pool"), 2),
      Gain.Secrets(Table.p1, 1))
    Vector[CoreOperation](Defer(key, held), TakeDeferred(key)).foreach { op =>
      val event = recordedOperation("held", op)
      val encoded = accepted(GameEventWire.encodeEvent("walker", 0, event))
      assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    }
    val deferral = accepted(GameEventWire.encodeEvent("walker", 0,
      recordedOperation("held", Defer(key, held))))
    assertEquals(ujson.read(deferral)("payload")("ops")(0)("kind").str,
      "defer")
```

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.DeferralSuite oathdigital.serialization.WalkerOperationWireSuite"`
Expected: compile failure, `Not found: DeferralKey`.

- [ ] **Step 3: Add the key, the ops and the state field**

In `src/main/scala/oathdigital/model/CoreOperations.scala`, insert after the `ModifyRollOutcome` case class:

```scala
/** The name [[Defer]] holds ops under, until a later step of the same
  * action takes them (operation rewrite design, "Deferral"). Named after
  * the power that owns it. */
final case class DeferralKey(value: String)

/** Holds `ops` under `key` in `CurrentGameState.walkerDeferred`, after any
  * held there already, without running them. A later step of the same
  * action runs them, replaces them or drops them, and takes them with
  * [[TakeDeferred]]; the walker drops whatever is left when the action
  * ends. They are held as the journal records them, so a game reloaded from
  * its events holds the same ops. It writes no Game Log line.
  */
final case class Defer(key: DeferralKey, ops: Vector[CoreOperation])
    extends PrimitiveOperation

/** Drops what [[Defer]] holds under `key`. Taking a key that holds nothing
  * changes nothing. It writes no Game Log line. */
final case class TakeDeferred(key: DeferralKey) extends PrimitiveOperation
```

In `src/main/scala/oathdigital/model/GameState.scala`, replace the last field of `CurrentGameState`:

```scala
    walkerFollowUp: Option[FollowUp] = None
)
```

with

```scala
    walkerFollowUp: Option[FollowUp] = None,
    // Ops a step of the current action holds for a later step of it
    // (operation rewrite design, "Deferral"). Walker scratch: replay
    // rebuilds it from the recorded `Defer`s, and the walker and
    // `WalkerCompleted` clear it with the dice pools. Never projected.
    walkerDeferred: Map[DeferralKey, Vector[CoreOperation]] = Map.empty
)
```

- [ ] **Step 4: Apply them**

In `src/main/scala/oathdigital/gameplay/operations/TurnStateOperations.scala`, add after `recordPowerUse`:

```scala
  /** Appends `ops` under `key`, as the journal records them, so a game
    * reloaded from its events holds the same ops as the live one. */
  def defer(ready: ReadyGame, key: DeferralKey,
      ops: Vector[CoreOperation]): ReadyGame =
    val held = ready.game.current.walkerDeferred
    ready.updateCurrent(current => current.copy(walkerDeferred = held.updated(
      key, held.getOrElse(key, Vector.empty) ++
        ops.map(OperationRun.canonical))))

  /** Drops what `key` holds; a key that holds nothing changes nothing. */
  def takeDeferred(ready: ReadyGame, key: DeferralKey): ReadyGame =
    ready.updateCurrent(current =>
      current.copy(walkerDeferred = current.walkerDeferred - key))
```

In `src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala`, in `applyNonMoveLeaves`, insert before `      case (result, EnterPhase(phase)) =>`:

```scala
      case (result, Defer(key, ops)) =>
        result.map(TurnStateOperations.defer(_, key, ops))
      case (result, TakeDeferred(key)) =>
        result.map(TurnStateOperations.takeDeferred(_, key))
```

In `src/main/scala/oathdigital/gameplay/operations/OperationPipeline.scala`, in `OperationRun.canonical`, insert before `    case other => other`:

```scala
    case value: Defer => value.copy(ops = value.ops.map(canonical))
```

- [ ] **Step 5: Spell them on the wire**

In `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`:

Replace the `TurnOperation` type with:

```scala
  private type TurnOperation = AdvanceVisionsDrawn.type | BeginTurn | EnterPhase |
    SetOathkeeper | RecordPowerUse | RecordCampaignResult | ModifyDicePool | Roll |
    ModifyRollOutcome | QueueFollowUp | Defer | TakeDeferred
```

In `encodeTurnOperation`, add after the `ModifyRollOutcome` arm:

```scala
      // A held op is spelled as a recorded one: it is held canonically.
      case Defer(key, ops) => ujson.Obj("kind" -> "defer",
        "key" -> key.value, "ops" -> ujson.Arr.from(ops.map(encodeOperation)))
      case TakeDeferred(key) => ujson.Obj("kind" -> "take-deferred",
        "key" -> key.value)
```

In `decodeTurnOperation`, add after the `"modify-roll-outcome"` case:

```scala
    case "defer" =>
      traverse(value("ops").arr.zipWithIndex.toVector)({
        case (operation, index) =>
          decodeOperation(operation, s"$path.ops[$index]") })
        .map(Defer(DeferralKey(value("key").str), _))
    case "take-deferred" =>
      Right(TakeDeferred(DeferralKey(value("key").str)))
```

Run: `wc -l src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`
Expected: 800 or fewer.

- [ ] **Step 6: Clear the field when an action ends**

In `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`, in `finish`, replace

```scala
      ctx.state.game.current.copy(walkerPending = None,
        rollPools = Map.empty, walkerProcedure = None)))
```

with

```scala
      ctx.state.game.current.copy(walkerPending = None,
        rollPools = Map.empty, walkerDeferred = Map.empty,
        walkerProcedure = None)))
```

In `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala`, in the `WalkerCompleted` case, replace

```scala
            rollPools = Map.empty,
            rollOutcomes = Map.empty)))
```

with

```scala
            rollPools = Map.empty,
            walkerDeferred = Map.empty,
            rollOutcomes = Map.empty)))
```

- [ ] **Step 7: Write `Deferred`**

Create `src/main/scala/oathdigital/gameplay/powerresolver/Deferred.scala`:

```scala
package oathdigital.gameplay.powerresolver

import oathdigital.model.{Branch, CoreOperation, DeferralKey, Operation,
  PendingTree, ReadyGame, TakeDeferred}

/** Reads and resolves the ops a power holds with `Defer` (operation rewrite
  * design, "Deferral"). A power holds an op by matching it with
  * `OperationRewrite.ops` and returning `Defer(key, Vector(op))`, so that it
  * does not run. It records one by returning `op` and then `Defer(key,
  * Vector(op))`. A later step resolves what is held, in the same action.
  */
object Deferred:
  /** The ops held under `key`, oldest first. */
  def held(ready: ReadyGame, key: DeferralKey): Vector[CoreOperation] =
    ready.game.current.walkerDeferred.getOrElse(key, Vector.empty)

  /** A step that resolves what `key` holds: nothing while nothing is held,
    * else what `f` returns for the held ops, then the take. `f` returns its
    * questions first, then what runs. The take comes last because a `Branch`
    * selects again when a parked command resumes, and must find the ops
    * still held; so nothing after `f`'s last question may park.
    */
  def resolve(key: DeferralKey)(
      f: (ReadyGame, PendingTree, Vector[CoreOperation]) => Vector[Operation])
      : Operation =
    Branch((ready, pending) => {
      val ops = held(ready, key)
      if ops.isEmpty then Vector.empty
      else f(ready, pending, ops) :+ TakeDeferred(key)
    })
```

- [ ] **Step 8: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.DeferralSuite oathdigital.serialization.* oathdigital.gameplay.Walker* oathdigital.gameplay.ProcedureWalker* oathdigital.gameplay.walker.*"`
Expected: pass. `DeferralSuite` has 5 tests, and `WalkerOperationWireSuite` has one more than before.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/model/CoreOperations.scala src/main/scala/oathdigital/model/GameState.scala src/main/scala/oathdigital/gameplay/operations/TurnStateOperations.scala src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala src/main/scala/oathdigital/gameplay/operations/OperationPipeline.scala src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala src/main/scala/oathdigital/gameplay/powerresolver/Deferred.scala src/test/scala/oathdigital/gameplay/DeferralSuite.scala src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala
git commit -m "feat(walker): a power holds ops with Defer and resolves them later in the action"
```

---

### Task 5: Wild Mounts holds the discards it replaces

Every existing `WildMountsSuite` test must pass unchanged. One test is new: a Nomad plan whose discard is a bare `PlanDiscard.denizen` is still replaced.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/WildMounts.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanDiscard.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanContext.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/WarningSignals.scala` (doc)
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/WildMountsSuite.scala`

**Interfaces:**
- Consumes:
  - `OperationRewrite.ops` and `OperationRewrite.Matcher` (Task 2).
  - `Defer`, `DeferralKey`, `Deferred.resolve` (Task 4).
  - Plan rewrites fold `Late` (Task 1).
- Produces:
  - `PlanUse.plans(ready: ReadyGame, pending: PendingTree, side: CampaignPlanSide): Vector[DecisionOptionRef]`.
  - `PlanDiscard.discardedNote(power: PowerId, card: DenizenId): Note`.
  - `WildMounts.deferred: DeferralKey`.

- [ ] **Step 1: Write the failing test**

In `WildMountsSuite.scala`, add these imports:

```scala
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.powers.WalkerPowerCatalog
```

Add this member after `attackerChoosesMountsThen`, and make `attackerChoosesMountsThen` delegate to it:

```scala
  /** As `attackerChoosesMountsThen`, under `game`'s rules. */
  private def attackerChoosesMountsUnder(game: OathRules, b: Board,
      plans: DenizenId*): Run =
    plans.foldLeft(commit(game, b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref))((run, plan) =>
      run.pick(b.actor, CampaignIds.attackerPlan,
        DecisionOptionRef.Denizen(plan))).finishPlanWindows
```

```scala
  private def attackerChoosesMountsThen(b: Board, plans: DenizenId*): Run =
    attackerChoosesMountsUnder(rules(winningDice), b, plans*)
```

Add, after the class, at the end of the file:

```scala
/** Horse Archers' card as a free attacker plan whose discard at the end of
  * the Campaign is a bare `PlanDiscard.denizen`, with no helper and no
  * line, as a plan that forgets `afterCampaign` would build it. */
private final case class BareDiscardNomadPlan(
    catalog: oathdigital.catalog.ExecutableCatalog) extends BattlePlan:
  val cardId: DenizenId = HorseArchersCard.id
  def id: PowerId = PowerId("test.bare-discard-nomad-plan")
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Bare discard", Vector.empty, Vector.empty))
  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      use.user.toVector.map(PlanDiscard.denizen(catalog, _, cardId))))
```

Add this test at the end of the class:

```scala
  test("a Nomad plan whose discard is a bare PlanDiscard.denizen is " +
      "replaced too"):
    val powers = WalkerPowerCatalog.default(catalog).powers
      .filterNot(_.id == HorseArchersCard.power.id) :+
      BareDiscardNomadPlan(catalog)
    val b = mountsBeastAndPlansHeldByActor(horseArchers)
    val done = attackerChoosesMountsUnder(rulesWith(powers, winningDice), b,
      horseArchers).pick(b.actor, WildMounts.decisionId,
      DecisionOptionRef.Denizen(smallFriends))
    assert(Look(done.state).advisers(b.actor).contains(horseArchers))
    assert(Look(done.state).inRegionalDiscard(smallFriends))
```

`awaits` and `finishPlanWindows` rebuild the parked decision with the default walker powers. The bare plan and the real Horse Archers each append one `Branch` at the root, so the parked position is the same under both. If `finishPlanWindows` stops early in this test, report it rather than changing the helper.

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.WildMountsSuite"`
Expected: the new test fails. No question is parked, so `pick` fails, or Horse Archers is in the discard. The other tests pass.

- [ ] **Step 3: Name the plans used on a side**

In `PlanContext.scala`, add to `object PlanUse`, after `chosen`:

```scala
  /** The plans used on `side` in this Campaign: those a player picked, and
    * for the defender those bandits applied, which the application recorded
    * (`CampaignPlans.appliedMarker`). */
  def plans(ready: ReadyGame, pending: PendingTree, side: CampaignPlanSide)
      : Vector[DecisionOptionRef] =
    val applied =
      if side != CampaignPlanSide.Defender then Vector.empty
      else ready.game.current.rollPools.keys.toVector
        .flatMap(CampaignPlans.markedRef)
    CampaignAnswers.picks(pending, CampaignIds.planDecision(side)) ++ applied
```

- [ ] **Step 4: Drop the mark from `PlanDiscard`**

In `PlanDiscard.scala`:

Replace the last paragraph of the object's doc

```
  * `afterCampaign` is the discard of a plan that says "At end, discard" (Horse
  * Archers, Storm Caller), with its line, marked so that a power can replace
  * it (Wild Mounts).
```

with

```
  * `afterCampaign` is the discard of a plan that says "At end, discard" (Horse
  * Archers, Storm Caller), with its line. A power that replaces the discard
  * of a plan (Wild Mounts) finds the discard itself, however it was built.
```

Delete `final case class Marked` and its doc. Replace `afterCampaign` and its doc with:

```scala
  /** The standard discard of `card` once the Campaign has resolved, whoever
    * won, and `power`'s line. A bandit defender has no user, so the attacker
    * is the acting player of its discard. */
  def afterCampaign(catalog: ExecutableCatalog, power: PowerId, use: PlanUse,
      card: DenizenId): Vector[Operation] = Vector(
    denizen(catalog, use.user.getOrElse(use.actor), card),
    discardedNote(power, card))

  /** `power`'s "Discarded after the Campaign." for `card`, written only when
    * the step before it took the card to a discard pile. */
  def discardedNote(power: PowerId, card: DenizenId): Note =
    Note(power, states => Option.when(states.previous.exists {
      case (before, after) =>
        !inDiscard(before, card) && inDiscard(after, card)
    })(discarded(PowerSourceRef.Card(card))))
```

- [ ] **Step 5: Rewrite Wild Mounts**

In `WildMounts.scala`:

Replace the `powerresolver` import with:

```scala
import oathdigital.gameplay.powerresolver.{Deferred, OperationRewrite,
  Transform}
```

Replace the class doc's second paragraph and its list, from `  * It is free, and choosing it does nothing at the time.` through `  * every fold, used or not.`, with:

```
  * It is free, and choosing it does nothing at the time. Its rewrite at the
  * Campaign's root folds `Late`, after every plan's own steps there. Once
  * the plan was used, it holds each discard of a Nomad card chosen as a
  * battle plan on its side (`OperationRewrite.ops`, `Deferred`), however
  * the plan built that discard. It does not read the discard's acting
  * player, which is the attacker for a bandit defender's plan. It appends
  * one step that resolves the held discards:
  *
  *  - it asks a player user who rules a Beast card, their faceup advisers
  *    and the faceup denizens at sites they rule, to discard one of them or
  *    to "Skip Wild Mounts". A Beast card chosen is discarded instead of the
  *    plans; skipping runs the held discards;
  *  - for a bandit user, it follows the FAQ: with two or more held
  *    discards, the first Beast card the bandits rule in map order is
  *    discarded instead;
  *  - otherwise it runs the held discards.
  *
  * Whether the plan was used is read at walk time, so a bandit's use,
  * recorded during the walk, counts. A held discard that runs writes the
  * plan's own "Discarded after the Campaign.", under the plan's power.
```

Delete `  override def priority: Int = WildMounts.Priority`.

Replace everything from `  override def rewrites` through the end of `ask` with:

```scala
  override def rewrites: Map[PowerWindow, Transform] = Map(
    PowerWindow.CampaignActionEligibility -> Transform((ctx, children) =>
      OperationRewrite.ops(ctx, children)(swappable(ctx.activePlayer)) :+
        Deferred.resolve(WildMounts.deferred)(swap(ctx.activePlayer))))

  /** The plan, when it was used in this Campaign. */
  private def used(ready: ReadyGame, pending: PendingTree, actor: PlayerId)
      : Option[PlanUse] =
    PlanUse.chosen(ready, pending, actor, cardRef, sides, afterOutcome = true)

  /** Holds the discard of a Nomad card chosen as a battle plan on the side
    * the plan was used on. */
  private def swappable(actor: PlayerId)
      : OperationRewrite.Matcher[CoreOperation, CoreOperation] =
    (ready, pending) => {
      case discard: Discard.Denizen if discard.suit == Suit.Nomad &&
          used(ready, pending, actor).exists(use => PlanUse.plans(ready,
            pending, use.side).contains(
              DecisionOptionRef.Denizen(discard.card))) =>
        Vector(Defer(WildMounts.deferred, Vector(discard)))
    }

  /** What resolves the held discards: a question, a swap, or the
    * discards. */
  private def swap(actor: PlayerId)(ready: ReadyGame, pending: PendingTree,
      held: Vector[CoreOperation]): Vector[Operation] =
    used(ready, pending, actor).fold(discarded(held)) { use =>
      use.user match
        case Some(user) => ask(use, user, discarded(held),
          beasts(ready, SiteRuler.Player(user)))
        case None if held.size >= WildMounts.BanditDiscards =>
          beasts(ready, SiteRuler.Bandits).headOption
            .fold(discarded(held))(card => instead(use, use.actor, card))
        case None => discarded(held)
    }

  /** The held discards, each run as the plan built it, then the plan's own
    * line under the plan's power. */
  private def discarded(held: Vector[CoreOperation]): Vector[Operation] =
    held.flatMap(operation =>
      BuildOps((_, _) => Right(Vector(operation))) +: (operation match {
        case discard: Discard.Denizen => planPower(discard.card).toVector
          .map(PlanDiscard.discardedNote(_, discard.card))
        case _ => Vector.empty
      }))

  /** The power printed on `card`, which is the plan's. */
  private def planPower(card: DenizenId): Option[PowerId] =
    catalog.denizen(card).flatMap(_.powers.headOption).map(_.id)

  /** The faceup Beast denizens `ruler` rules, in map order. */
  private def beasts(ready: ReadyGame, ruler: SiteRuler): Vector[DenizenId] =
    RuledCards.of(catalog, ready, ruler, Suit.Beast).collect {
      case card: DenizenId => card }

  private def ask(use: PlanUse, user: PlayerId, discards: Vector[Operation],
      ruled: Vector[DenizenId]): Vector[Operation] =
    if ruled.isEmpty then discards
    else Vector(
      Decide(WildMounts.decisionId, user, DecisionQuery.ChooseOne(
        ruled.flatMap(card =>
          DecisionOption.forRef(DecisionOptionRef.Denizen(card))) :+
          DecisionOption.Button(WildMounts.skip, "Skip Wild Mounts"),
        heading = Some("Wild Mounts: discard a Beast card you rule " +
          "instead of your Nomad battle plans"))),
      Branch((_, pending) =>
        PowerAnswers.one(pending, WildMounts.decisionId) match {
          case Some(DecisionOptionRef.Denizen(card)) if ruled.contains(card) =>
            instead(use, user, card)
          case _ => discards
        }))
```

`instead` stays as it is.

In `object WildMounts`, replace the `Priority` value and its doc with:

```scala
  /** Where the held discards wait for the step that resolves them. */
  val deferred: DeferralKey = DeferralKey("wild-mounts")
```

- [ ] **Step 6: Update Warning Signals' doc**

In `WarningSignals.scala`, replace

```
  * when the Campaign has resolved, whether or not the defender won, by
  * `PlanDiscard.afterCampaign`, so Wild Mounts can replace it.
```

with

```
  * when the Campaign has resolved, whether or not the defender won, by
  * `PlanDiscard.afterCampaign`.
```

Run: `grep -rn "Marked\|WildMounts.Priority" src/main/scala src/test/scala`
Expected: no match.

- [ ] **Step 7: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.actions.campaign.*"`
Expected: pass. `WildMountsSuite` has 11 tests: 9 from before this plan, Task 1's phase test, and Step 1's test. If an existing test fails, stop and report (Global Constraints).

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/WildMounts.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanDiscard.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanContext.scala src/main/scala/oathdigital/gameplay/powers/campaign/WarningSignals.scala src/test/scala/oathdigital/gameplay/powers/campaign/WildMountsSuite.scala
git commit -m "refactor(powers): Wild Mounts holds the Nomad plan discards it replaces"
```

---

### Task 6: Record the slice and run the gates

**Files:**
- Modify: `docs/ROADMAP.md` ("### Phase - Operation rewrite")
- Modify: `docs/superpowers/specs/2026-10-09-operation-rewrite-design.md` (Status)

- [ ] **Step 1: Run the gates**

Run, in order:
1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass. The server count is the baseline plus 21, and the frontend count is unchanged.

The link check may fail on another session's file, `docs/superpowers/plans/2026-10-09-modifier-walker-slice4.md`, which links `2026-10-09-catalog-batch-6-design.md` without `../specs/`. That file is not this plan's to fix: report the failure and go on, as long as it is the only one.

- [ ] **Step 2: Update the roadmap and the spec**

In `docs/ROADMAP.md`, under "### Phase - Operation rewrite", delete the bullet:

```
- [ ] Slice 1: `OperationRewrite`, deferral, the fold phase, and Wild
  Mounts. Starts after modifier walker slice 4.
```

In the spec's **Status** paragraph, replace `Slice 1 starts once the [modifier walker](2026-10-09-modifier-walker-design.md) has merged.` (it spans two lines) with `Slice 1 is done.`, and re-wrap the paragraph.

Run: `python3 scripts/check-markdown-links.py`
Expected: pass, apart from the other session's file named in Step 1.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md docs/superpowers/specs/2026-10-09-operation-rewrite-design.md
git commit -m "docs: record operation rewrite slice 1"
```
