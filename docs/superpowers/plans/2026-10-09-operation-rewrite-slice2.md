# Operation Rewrite, Slice 2 (Migrations) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The hand-written matchers that find a step in their window's children move onto `OperationRewrite`. The Old Oak and Mounted Library read the Trade's secret gain through a deferral record. Way Station and Mounted Library fold `Late` instead of by priority. Every existing test passes unchanged.

**Architecture:**

- **Refactor only.** Each helper keeps its behavior. It now finds what it changes through `OperationRewrite.ops` (core ops, including what closures build) or `OperationRewrite.nodes` (any node, in the tree only). It takes the fold's `PowerCtx`, which both need, and reads the actor from it.
- **Supply and cost rewrites** move onto `ops`: `SupplyStrip`, `TravelRoute.adjustSupply`, Disciples, Initiation Rite and `EconomySources.intoOccupied`.
- **Replacements that are not core ops** move onto `nodes`: `SuitSupplyWaiver` swaps a payment for a `BuildOps`, and `ExtraWarbands` puts a `Note` after the gain it raises.
- **Decision rewrites** move onto `nodes`: `EconomySources.withOptions`, `SearchSources`, `ConspiracyTargets.narrowed`, Vow of Wandering, Land Warden and Spinning Bee.
- **Deferral, record form.** The Old Oak and Mounted Library record the actor's `Gain.Secrets` as it runs, and their bonus step is a `Deferred.resolve` that reads what was recorded.
- **Fold phase.** Way Station and Mounted Library fold `Late`. Every other priority stays, with its reason in its doc.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. There is no frontend change.

**Spec:** Read `docs/superpowers/specs/2026-10-09-operation-rewrite-design.md` before starting, with the slice 1 plan's rulings already written into it. Task 1 amends it with this plan's rulings, so re-read its "Migrations (slice 2)" section after Task 1.

**Starts from:** `main` once operation rewrite slice 1 has merged. Check this before anything else (Task 1, Step 1).

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`.
  - An unused import or private member fails the build. Each step lists the imports it changes.
  - A refutable pattern in a `val` also fails it, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - No power name appears in walker or `gameplay/operations` sources.
  - `gameplay/powerresolver` imports only `model` and its own package. `gameplay/operations` may import `gameplay.powerresolver`.
- **Line width.** Scala lines this plan writes stay at or under 80 characters, as in the files around them. Re-wrap any line that comes out longer.
- **Indentation in lists.** A code block inside a list item is indented two extra spaces for the list. Drop those two when you copy it.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
  - Run `ExitWorktree` with `keep` before merging into `main`. Do not push.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **No existing test changes** (spec ruling 11). If an existing test fails and the fix would change the test, stop and report: it marks a behavior change, which needs a ruling.
- **Texts.** No decision, button or log line changes wording or order.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 4 server tests and no frontend test. It rewrites no existing test.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## What slice 1 provides

Check each of these in the code before Task 1. If a name or signature differs, stop and report.

- `oathdigital.gameplay.powerresolver.OperationRewrite`:

  ```scala
  type Matcher[A, B] = (ReadyGame, PendingTree) => PartialFunction[A, Vector[B]]
  def ops(ctx: PowerCtx, children: Vector[Operation])(
      matcher: Matcher[CoreOperation, CoreOperation]): Vector[Operation]
  def nodes(ctx: PowerCtx, children: Vector[Operation])(
      matcher: Matcher[Operation, Operation]): Vector[Operation]
  ```

  - Both descend `Sequence`, `Repeat` and `Branch`; `ops` also wraps a `BuildOps`' `build`. Both leave alone a node below that carries its own window, except the windowed leaf being folded.
  - One replacement takes the op's place. Several, or none, go in a `Sequence` there; inside what a `BuildOps` builds they are spliced in.
  - A fold-time match reads `ctx.state` and `PendingTree(ctx.nodePath, ctx.answered)`. A walk-time match reads the closure's arguments.
- `oathdigital.gameplay.powerresolver.Deferred`:

  ```scala
  def held(ready: ReadyGame, key: DeferralKey): Vector[CoreOperation]
  def resolve(key: DeferralKey)(
      f: (ReadyGame, PendingTree, Vector[CoreOperation]) => Vector[Operation])
      : Operation
  ```

  `resolve` selects nothing while nothing is held, and otherwise `f(...) :+ TakeDeferred(key)`.
- In `oathdigital.model`: `DeferralKey(value: String)`, `Defer(key, ops: Vector[CoreOperation])` and `TakeDeferred(key)`. Neither op writes a Game Log line.
- `Transform(fn, phase: FoldPhase = FoldPhase.Normal)` and `enum FoldPhase { case Normal, Late }`, both in `oathdigital.gameplay.powerresolver`.

## Rulings made at plan time

Task 1 writes these into the spec.

1. **`ops` returns core ops only, so two helpers move onto `nodes`.** The spec put `SuitSupplyWaiver` and `ExtraWarbands` under `ops`. `SuitSupplyWaiver` replaces a payment with a `BuildOps`, and `ExtraWarbands` puts a `Note` after the gain. Neither is a core op. Both helpers only rewrite nodes in the tree, so `nodes` fits.
2. **The draw rewrites stay as they are:** `DrawExtension`, `BottomDraw`, Painted Trumpet's draw and Spinning Bee's draw.
   - The spec listed `DrawExtension`; the other three were written after the spec's survey.
   - Each rewrites its own draw leaf's batch as a whole: it drops the batch's `AdvanceVisionsDrawn` and may add it back. It can also refuse the batch with an error, which a `Matcher` cannot return.
   - Each already finds its leaf by its window, never by position.
3. **`PlacementTree.adjust` stays.** Its first call replaces the play's default children whole with a `PlacementBody`, which a node matcher cannot express. It already finds the body by its type.
4. **Decision rewrites written after the survey move too.** `SearchSources`, Land Warden's keep decision and Spinning Bee's source decision have the shape of `EconomySources.withOptions`. They move onto `nodes` with it, so that every decision rewrite reads the same.
5. **A helper that moves takes the fold's `PowerCtx`.** `ops` and `nodes` need it. The actor comes from `ctx.activePlayer`, except in `SupplyStrip`, whose follow-up user strips another player's payment.
6. **A resolve's line goes inside the resolve.** Mounted Library's "Gained {n} more secrets." covers the generic line of the step before it. So the resolve returns the gain and then the line, and the take comes after both.
7. **The priority review.**
   - Mounted Library's priority exists only so its discard comes after the other Trade modifiers' root steps, so it becomes `Late`.
   - Every other priority orders one rewrite after another, and stays: Bottom Draw, Painted Trumpet, `EconomySources.ExemptionPriority`, Royal Stables, Special Envoy, and on the Campaign's losses `SparedForce`, Hospital and Cursed Cauldron. Each doc already gives its reason.
8. **An empty `Sequence` keeps a removed node's place.** When `SupplyStrip` removes a payment, or `ConspiracyTargets` drops a decision left with no option, an empty `Sequence` now stands where the node stood. The child indices after it no longer shift, so a later step's `nodeId` changes. No test found reads a `nodeId` in those windows; if one fails on it, stop and report.

## File map

| File | Change | Task |
|---|---|---|
| `docs/superpowers/specs/2026-10-09-operation-rewrite-design.md` | modify | 1, 5 |
| `src/main/scala/oathdigital/gameplay/operations/SupplyStrip.scala` | modify | 1 |
| `src/test/scala/oathdigital/gameplay/operations/SupplyStripSuite.scala` | create | 1 |
| `src/main/scala/oathdigital/gameplay/walker/FollowUpSupplyWaiver.scala` | modify | 1 |
| `src/main/scala/oathdigital/gameplay/powers/economy/AwaitedReturn.scala` | modify | 1 |
| `src/main/scala/oathdigital/gameplay/powers/search/{Mushrooms,NewsFromAfar}.scala` | modify | 1 (Mushrooms also 3) |
| `src/main/scala/oathdigital/gameplay/powers/travel/{AFastSteed,ForestPaths,SpecialEnvoy,Tents}.scala` | modify | 1 |
| `src/main/scala/oathdigital/gameplay/powers/travel/WayStation.scala` | modify | 1, 5 |
| `src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala` | modify | 2 |
| `src/main/scala/oathdigital/gameplay/powers/travel/RoyalStables.scala` | modify | 2 |
| `src/main/scala/oathdigital/gameplay/powers/search/Disciples.scala` | modify | 2 |
| `src/main/scala/oathdigital/gameplay/powers/economy/{InitiationRite,SuitSupplyWaiver,ExtraWarbands,VowOfBeastkin}.scala` | modify | 2 |
| `src/main/scala/oathdigital/gameplay/powers/economy/VowOfWandering.scala` | modify | 2, 3 |
| `src/main/scala/oathdigital/gameplay/powers/economy/EconomySources.scala` | modify | 2, 3 |
| `src/main/scala/oathdigital/gameplay/powers/economy/{OldSongs,Pressgangs}.scala` | modify | 2, 3 |
| `src/main/scala/oathdigital/gameplay/powers/economy/{GolemLegions,MapLibrary,SkilledMerchants}.scala` | modify | 3 |
| `src/main/scala/oathdigital/gameplay/powers/search/{SearchSources,ErrandBoy,Observatory,LandWarden,SpinningBee}.scala` | modify | 3 |
| `src/main/scala/oathdigital/gameplay/powers/targeting/{ConspiracyTargets,LostTongue}.scala` | modify | 3 |
| `src/main/scala/oathdigital/gameplay/powers/economy/{TheOldOak,MountedLibrary}.scala` | modify | 4 |
| `src/test/scala/oathdigital/gameplay/powers/economy/MountedLibrarySuite.scala` | modify | 4 |
| `src/test/scala/oathdigital/gameplay/powers/travel/WayStationSuite.scala` | modify | 5 |
| `docs/ROADMAP.md` | modify | 5 |

---

### Task 1: The spec's rulings, and `SupplyStrip` on `ops`

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-operation-rewrite-design.md`
- Modify: `src/main/scala/oathdigital/gameplay/operations/SupplyStrip.scala`
- Create: `src/test/scala/oathdigital/gameplay/operations/SupplyStripSuite.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/FollowUpSupplyWaiver.scala`
- Modify: `AwaitedReturn.scala` (economy), `Mushrooms.scala` and `NewsFromAfar.scala` (search), `AFastSteed.scala`, `ForestPaths.scala`, `SpecialEnvoy.scala`, `Tents.scala` and `WayStation.scala` (travel), all under `src/main/scala/oathdigital/gameplay/powers/`

**Interfaces:**
- Consumes: `OperationRewrite.ops` (slice 1).
- Produces: `SupplyStrip.apply(ctx: PowerCtx, actor: PlayerId, operations: Vector[Operation]): Vector[Operation]`.

- [ ] **Step 1: Check that slice 1 has merged**

Run: `git log --oneline -15 main && ls src/main/scala/oathdigital/gameplay/powerresolver/`
Expected: a merge of operation rewrite slice 1 on `main`, and both `OperationRewrite.scala` and `Deferred.scala` listed. Then check the names under "What slice 1 provides" in the code. If anything is missing, stop and report.

- [ ] **Step 2: Set up the worktree and record the baseline**

Follow Global Constraints, "Worktree". Then run `./sbtw "test" "frontend/test"` and note both counts.

- [ ] **Step 3: Amend the spec**

In `docs/superpowers/specs/2026-10-09-operation-rewrite-design.md`, replace the whole "### Migrations (slice 2)" section, from its heading up to but not including "### Extension points (not built)", with:

```markdown
### Migrations (slice 2)

Each rewrite helper below moves onto `ops` or `nodes`. Its behavior and its
tests stay the same. A helper that moves takes the fold's `PowerCtx`, which
`ops` and `nodes` need, and reads the actor from it.

- `ops`:
  - Disciples (`LossRewrite` moved in slice 1);
  - `SupplyStrip` and `TravelRoute.adjustSupply`;
  - Initiation Rite and `EconomySources.intoOccupied`.
- `nodes`:
  - `SuitSupplyWaiver` and `ExtraWarbands`, whose replacements are not core
    ops: a payment built when it runs, and a gain followed by its line;
  - the decision rewrites: `EconomySources.withOptions`, `SearchSources`,
    `ConspiracyTargets.narrowed`, Vow of Wandering's Decide-to-Note swap,
    Land Warden's keep decision and Spinning Bee's source decision.
- **Left as they are:**
  - The draw rewrites: `DrawExtension`, `BottomDraw`, Painted Trumpet's
    draw and Spinning Bee's draw. Each rewrites its own draw leaf's batch
    as a whole and may refuse it with an error, which a matcher cannot
    return. Each finds its leaf by its window.
  - `PlacementTree.adjust`. Its first call replaces the default children
    whole with a `PlacementBody`, which a node matcher cannot express, and
    it already finds the body by its type.
- **The Old Oak and Mounted Library** record the actor's `Gain.Secrets` at
  `TradeGain` (`ops`, record form). Their bonus step becomes a `resolve`
  that reads what was gained. The bonus stays a separate step after the
  gain, so the lines stay as they are. Mounted Library's line is part of
  its resolve, before the take, so it still follows and covers its gain.
- **Way Station** keeps its fold-time read of `SpendSupply`. The consent
  design asks every question of a cost before any of it is paid
  ([consent design](2026-10-08-consent-system-design.md)), and a deferral
  would learn the Supply only after the spending closures ran. It moves
  from priority 3 to the `Late` phase. Its doc names the limit: it cannot
  see Supply spent inside a closure.
- **Priority review.** Each remaining priority was checked:
  - A priority that exists only so the power folds after others' appends
    becomes `Late`: Mounted Library, whose discard ends the Trade.
  - A priority that orders one rewrite after another stays, with its
    reason in its doc: Bottom Draw before the draw extensions, Painted
    Trumpet after them, `EconomySources.ExemptionPriority` after the powers
    that add sources, Royal Stables after the terrain, Special Envoy after
    Royal Stables, and on the Campaign's losses `SparedForce`, Hospital and
    Cursed Cauldron, in that order.
- **Shape.** When `SupplyStrip` removes a payment, or `ConspiracyTargets`
  drops a decision left with no option, an empty `Sequence` keeps the
  node's place. The child indices after it no longer shift.
```

In the "## Testing" section, replace `- **Slice 2:** the full suite, unchanged.` with:

```markdown
- **Slice 2:** the full suite, unchanged, plus `SupplyStripSuite` (a
  payment in the tree, in a `Branch` and in a `BuildOps`; another player's
  and a nested window's left alone) and the fold phase of Way Station and
  Mounted Library.
```

Run: `python3 scripts/check-markdown-links.py`
Expected: pass for this file.

- [ ] **Step 4: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/operations/SupplyStripSuite.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Unwrap.as

/** `SupplyStrip` finds the actor's Supply payments wherever the cost window
  * holds them (operation rewrite design, slice 2). Pure: no walker. */
class SupplyStripSuite extends munit.FunSuite:
  private val actor = Table.p1
  private val ready = Table.start.ready
  private val pending = PendingTree(Vector("0"), Vector.empty)
  private val window = PowerWindow.TravelCost
  private val pay: SpendSupply = SpendSupply(actor, 2)
  private val othersPay: SpendSupply = SpendSupply(Table.p2, 2)

  /** The context of folding the cost window, a windowed `Sequence`. */
  private val folding: PowerCtx = PowerCtx(ready, actor,
    RuleSourceRef.GameRule("test"), window, Vector("0"),
    Sequence(Vector.empty, Some(window)))

  test("the actor's payment is removed in the tree, in what a Branch " +
      "selects and in what a BuildOps builds"):
    val stripped = SupplyStrip(folding, actor, Vector(pay,
      Branch((_, _) => Vector(pay)),
      BuildOps((_, _) => Right(Vector(pay, othersPay)))))
    assertEquals(stripped.head, Sequence(Vector.empty))
    assertEquals(as[Branch](stripped(1)).select(ready, pending),
      Vector[Operation](Sequence(Vector.empty)))
    assertEquals(as[BuildOps](stripped(2)).build(ready, pending),
      Right(Vector[CoreOperation](othersPay)))

  test("another player's payment and a node with its own window are left " +
      "alone"):
    val nested = Sequence(Vector(pay),
      Some(PowerWindow.TravelActionEligibility))
    val stripped = SupplyStrip(folding, actor, Vector(othersPay, nested))
    assertEquals(stripped.head, othersPay)
    assert(stripped(1) eq nested)
```

- [ ] **Step 5: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.SupplyStripSuite"`
Expected: compile failure: `SupplyStrip.apply` takes two arguments, not three.

- [ ] **Step 6: Move `SupplyStrip` onto `ops`**

Replace the whole of `src/main/scala/oathdigital/gameplay/operations/SupplyStrip.scala` with:

```scala
package oathdigital.gameplay.operations

import oathdigital.gameplay.powerresolver.{OperationRewrite, PowerCtx}
import oathdigital.model.{Operation, PlayerId, SpendSupply}

/** Removes `actor`'s Supply payments from a cost window's children, wherever
  * the window holds them (operation rewrite design): listed (a Campaign's, a
  * Travel's), selected by a `Branch`, or built when a node runs (a Search's
  * `BuildOps`, or a waiver's). Everything else stays: a Travel's cost window
  * also holds its pawn move. `SpendSupply` cannot be zero, so a waived
  * payment is removed, not set to nothing; in the tree an empty `Sequence`
  * keeps its place.
  */
object SupplyStrip:
  def apply(ctx: PowerCtx, actor: PlayerId, operations: Vector[Operation])
      : Vector[Operation] =
    OperationRewrite.ops(ctx, operations)((_, _) => {
      case SpendSupply(player, _, _) if player == actor => Vector.empty
    })
```

- [ ] **Step 7: Pass the context at every call**

Run:

```bash
sed -i '' 's/SupplyStrip(ctx.activePlayer, operations)/SupplyStrip(ctx, ctx.activePlayer, operations)/' src/main/scala/oathdigital/gameplay/powers/economy/AwaitedReturn.scala src/main/scala/oathdigital/gameplay/powers/search/Mushrooms.scala src/main/scala/oathdigital/gameplay/powers/search/NewsFromAfar.scala src/main/scala/oathdigital/gameplay/powers/travel/AFastSteed.scala src/main/scala/oathdigital/gameplay/powers/travel/ForestPaths.scala src/main/scala/oathdigital/gameplay/powers/travel/SpecialEnvoy.scala src/main/scala/oathdigital/gameplay/powers/travel/Tents.scala
```

Then `git grep -n "SupplyStrip(" -- src/main` must show three arguments at every call except in `FollowUpSupplyWaiver.scala` and `WayStation.scala`, which the next two edits change.

In `src/main/scala/oathdigital/gameplay/walker/FollowUpSupplyWaiver.scala`, replace

```scala
      (ctx, operations) => ctx.state.game.current.walkerFollowUp
        .fold(operations)(entry => SupplyStrip(entry.player, operations)))))
```

with

```scala
      (ctx, operations) => ctx.state.game.current.walkerFollowUp
        .fold(operations)(entry =>
          SupplyStrip(ctx, entry.player, operations)))))
```

In `src/main/scala/oathdigital/gameplay/powers/travel/WayStation.scala`, replace `        rewrite(ctx.state, traveller, ruler, operations)))))` with `        rewrite(ctx, traveller, ruler, operations)))))`, and replace the whole `rewrite` method with:

```scala
  private def rewrite(ctx: PowerCtx, traveller: PlayerId, ruler: SiteRuler,
      operations: Vector[Operation]): Vector[Operation] =
    val payment = operations.collect {
      case spend @ SpendSupply(player, _, _) if player == traveller => spend }
    val supply = payment.map(_.amount).sum
    val board = ctx.state.game.current.players.find(_.player == traveller)
      .map(_.board)
    val stripped = SupplyStrip(ctx, traveller, operations)
    if supply == 0 then operations
    else if ruler == SiteRuler.Player(traveller) then stripped
    else if !board.exists(_.favor >= WayStation.favorOwed(operations, traveller) +
        WayStation.Favor) then operations
    else
      val options = Vector(
        Option.when(board.exists(_.supply.supply >= supply))(
          DecisionOption.Button(WayStation.supplyOption,
            s"Spend $supply Supply")),
        Some(DecisionOption.Button(WayStation.favorOption, ruler match
          case SiteRuler.Player(owner) => s"Give 1 favor to ${owner.value}"
          case _ => "Burn 1 favor"))).flatten
      Vector[Operation](
        Decide(WayStation.decisionId, traveller, DecisionQuery.ChooseOne(
          options, heading = Some("Way Station: spend no Supply?")),
          presentation = DecisionPresentation.Panel)) ++
        TravelRoute.beforeMove(stripped, Vector(
          Branch((_, pending) =>
            PowerAnswers.one(pending, WayStation.decisionId) match
              case Some(WayStation.favorOption) => Vector(
                RulerToll.payment(traveller, ruler),
                Note(id, RulerToll.note(cardId, traveller, ruler)))
              case Some(_) => payment
              case None => Vector.empty)))
```

The `favorOwed` line is the file's own, already over 80 columns; leave it as it is. `WayStation.scala` already imports `PowerCtx`.

- [ ] **Step 8: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.SupplyStripSuite oathdigital.gameplay.FollowUpSupplyWaiverSuite oathdigital.gameplay.FollowUpRulesSuite oathdigital.gameplay.powers.travel.* oathdigital.gameplay.powers.search.* oathdigital.gameplay.powers.economy.*"`
Expected: pass. `SupplyStripSuite` has 2 tests.

Then run `./sbtw test`.
Expected: pass, with 2 more server tests than the baseline.

- [ ] **Step 9: Commit**

```bash
git add docs/superpowers/specs/2026-10-09-operation-rewrite-design.md src/main/scala/oathdigital/gameplay/operations/SupplyStrip.scala src/test/scala/oathdigital/gameplay/operations/SupplyStripSuite.scala src/main/scala/oathdigital/gameplay/walker/FollowUpSupplyWaiver.scala src/main/scala/oathdigital/gameplay/powers/economy/AwaitedReturn.scala src/main/scala/oathdigital/gameplay/powers/search/Mushrooms.scala src/main/scala/oathdigital/gameplay/powers/search/NewsFromAfar.scala src/main/scala/oathdigital/gameplay/powers/travel/AFastSteed.scala src/main/scala/oathdigital/gameplay/powers/travel/ForestPaths.scala src/main/scala/oathdigital/gameplay/powers/travel/SpecialEnvoy.scala src/main/scala/oathdigital/gameplay/powers/travel/Tents.scala src/main/scala/oathdigital/gameplay/powers/travel/WayStation.scala
git commit -m "refactor(powers): SupplyStrip finds a payment wherever its window holds it"
```

---

### Task 2: The cost and gain rewrites

Refactor only: no test is added or changed. On paper, each rewrite now also reaches a `Sequence`, `Repeat` or `Branch` in its window. None of these windows holds a matching op there before the rewrite folds, so no test should notice.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala`, `RoyalStables.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/search/Disciples.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/InitiationRite.scala`, `EconomySources.scala`, `OldSongs.scala`, `Pressgangs.scala`, `SuitSupplyWaiver.scala`, `ExtraWarbands.scala`, `VowOfBeastkin.scala`, `VowOfWandering.scala`

**Interfaces:**
- Consumes: `OperationRewrite.ops` and `.nodes` (slice 1).
- Produces:
  - `TravelRoute.adjustSupply(ctx: PowerCtx, operations: Vector[Operation])(rewrite: Int => Int): Vector[Operation]`
  - `EconomySources.intoOccupied(ctx: PowerCtx, operations: Vector[Operation], decisionId: String): Vector[Operation]`
  - `ExtraWarbands.added(ctx: PowerCtx, power: PowerId, card: DenizenId, extra: Int, operations: Vector[Operation]): Vector[Operation]`

- [ ] **Step 1: `TravelRoute.adjustSupply` on `ops`**

In `src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala`, replace the `adjustSupply` method with the following. The file imports `powerresolver._` already.

```scala
  /** `operations` with `rewrite` applied to the amount of each of the
    * traveller's Supply payments, wherever the cost window holds them. */
  def adjustSupply(ctx: PowerCtx, operations: Vector[Operation])
      (rewrite: Int => Int): Vector[Operation] =
    OperationRewrite.ops(ctx, operations)((_, _) => {
      case payment @ SpendSupply(player, amount, _)
          if player == ctx.activePlayer =>
        Vector(payment.copy(amount = rewrite(amount)))
    })
```

Then run:

```bash
sed -i '' 's/TravelRoute.adjustSupply(operations, ctx.activePlayer)/TravelRoute.adjustSupply(ctx, operations)/' src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala src/main/scala/oathdigital/gameplay/powers/travel/RoyalStables.scala
```

Run: `git grep -n "adjustSupply(" -- src/main`
Expected: the definition and four calls, each `adjustSupply(ctx, operations)`.

- [ ] **Step 2: Disciples on `ops`**

In `src/main/scala/oathdigital/gameplay/powers/search/Disciples.scala`:

Replace the `powerresolver` import with `import oathdigital.gameplay.powerresolver.{Contribution, OperationRewrite, Transform}` and re-wrap it:

```scala
import oathdigital.gameplay.powerresolver.{Contribution, OperationRewrite,
  Transform}
```

In the doc, replace

```
  * The Search's cost node is rewritten so that, when it runs, a Supply
  * payment above 2 is lowered to 2 while the player holds the Darkest Secret.
```

with

```
  * The Search's cost is rewritten so that a Supply payment above 2, wherever
  * the cost holds it, is lowered to 2 while the player holds the Darkest
  * Secret.
```

Replace `effects` and delete `capped`:

```scala
  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchCost -> Vector(Transform((ctx, operations) =>
      OperationRewrite.ops(ctx, operations)((ready, _) => {
        case pay @ SpendSupply(player, amount, _)
            if player == ctx.activePlayer && amount > Disciples.Supply &&
              BannerRules.holder(ready.game.current, Banner.DarkestSecret)
                .contains(player) =>
          Vector(pay.copy(amount = Disciples.Supply))
      }))))
```

- [ ] **Step 3: Initiation Rite on `ops`**

In `src/main/scala/oathdigital/gameplay/powers/economy/InitiationRite.scala`, replace the `powerresolver` import with:

```scala
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OperationRewrite, PowerCtx, Transform}
```

and replace `contributions` with:

```scala
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterCost -> Vector(Transform((ctx, operations) =>
      OperationRewrite.ops(ctx, operations)((_, _) => {
        case pay: PayCost if pay.player == ctx.activePlayer &&
            pay.cost.favor > 0 =>
          Vector(pay.copy(cost = pay.cost.copy(favor = 0,
            secret = pay.cost.secret + pay.cost.favor)))
      }))))
```

- [ ] **Step 4: `EconomySources.intoOccupied` on `ops`**

In `src/main/scala/oathdigital/gameplay/powers/economy/EconomySources.scala`, add this import after the `actions.economy` one:

```scala
import oathdigital.gameplay.powerresolver.{OperationRewrite, PowerCtx}
```

Replace `intoOccupied` and its doc with:

```scala
  /** `operations` with the actor's payment onto the answered source marked
    * `intoOccupied`, which `PayCostRules` would refuse onto a card that holds
    * tokens. The source decision is the gate: an occupied card is answerable
    * only when an exemption made it available, so the mark changes nothing
    * for a token-free card. Every other payment keeps the check.
    */
  def intoOccupied(ctx: PowerCtx, operations: Vector[Operation],
      decisionId: String): Vector[Operation] =
    PowerAnswers.last(ctx.answered, decisionId).flatMap(idOf) match
      case None => operations
      case Some(card) => OperationRewrite.ops(ctx, operations)((_, _) => {
        case pay: PayCost if pay.player == ctx.activePlayer &&
            pay.placedAt == Location.OnCard(card) =>
          Vector(pay.copy(intoOccupied = true))
      })
```

In `OldSongs.scala`, replace

```scala
      EconomySources.intoOccupied(operations, ctx.activePlayer,
        TradeProcedure.decisionId, ctx.answered))))
```

with

```scala
      EconomySources.intoOccupied(ctx, operations,
        TradeProcedure.decisionId))))
```

In `Pressgangs.scala`, replace

```scala
      EconomySources.intoOccupied(operations, ctx.activePlayer,
        MusterProcedure.decisionId, ctx.answered))))
```

with

```scala
      EconomySources.intoOccupied(ctx, operations,
        MusterProcedure.decisionId))))
```

- [ ] **Step 5: `SuitSupplyWaiver` on `nodes`**

In `src/main/scala/oathdigital/gameplay/powers/economy/SuitSupplyWaiver.scala`, replace the `powerresolver` import with:

```scala
import oathdigital.gameplay.powerresolver.{Contribution, OperationRewrite,
  Transform}
```

At the end of the object's doc, before `  */`, add:

```
  *
  * The payment is replaced through `OperationRewrite.nodes`, because its
  * replacement is a node, not a core op. A later `ops` rewrite, such as
  * `SupplyStrip`, still finds the payment inside it.
```

Replace `effects` with:

```scala
  def effects(window: PowerWindow, decisionId: String,
      catalog: ExecutableCatalog)(free: (ReadyGame, PlayerId, Suit) => Boolean)
      : Map[PowerWindow, Vector[Contribution]] = Map(
    window -> Vector(Transform((ctx, operations) =>
      OperationRewrite.nodes(ctx, operations)((_, _) => {
        case pay @ SpendSupply(player, _, _) if player == ctx.activePlayer =>
          Vector(unlessFree(pay, decisionId, catalog, free))
      }))))
```

- [ ] **Step 6: `ExtraWarbands` on `nodes`**

In `src/main/scala/oathdigital/gameplay/powers/economy/ExtraWarbands.scala`, add this import before the `powers.NoteSupport` one:

```scala
import oathdigital.gameplay.powerresolver.{OperationRewrite, PowerCtx}
```

Replace `added` and its doc with:

```scala
  /** `operations` with `extra` more warbands on the actor's gain, wherever
    * the window holds it, and the line of `power`, from `card`, after it.
    * Unchanged when they hold no such gain. A Muster's gain window holds one
    * gain. */
  def added(ctx: PowerCtx, power: PowerId, card: DenizenId, extra: Int,
      operations: Vector[Operation]): Vector[Operation] =
    OperationRewrite.nodes(ctx, operations)((_, _) => {
      case gain @ Gain.Warbands(player, _, _) if player == ctx.activePlayer =>
        Vector(gain.copy(amount = gain.amount + extra),
          Note(power, line(_, card, player, gain.amount, extra)))
    })
```

In `VowOfBeastkin.scala`, replace

```scala
      ExtraWarbands.added(id, cardId, VowOfBeastkin.Warbands,
        ctx.activePlayer, operations))))
```

with

```scala
      ExtraWarbands.added(ctx, id, cardId, VowOfBeastkin.Warbands,
        operations))))
```

In `VowOfWandering.scala`, in `VowOfWanderingMuster`, replace

```scala
      ExtraWarbands.added(id, cardId, Warbands, ctx.activePlayer,
        operations))))
```

with

```scala
      ExtraWarbands.added(ctx, id, cardId, Warbands, operations))))
```

- [ ] **Step 7: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.* oathdigital.gameplay.powers.search.* oathdigital.gameplay.powers.economy.* oathdigital.gameplay.MusterProcedureSuite oathdigital.gameplay.EconomyWalkerSuite"`
Expected: pass.

Then run `./sbtw test`.
Expected: pass, with the same count as after Task 1.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala src/main/scala/oathdigital/gameplay/powers/travel/RoyalStables.scala src/main/scala/oathdigital/gameplay/powers/search/Disciples.scala src/main/scala/oathdigital/gameplay/powers/economy/InitiationRite.scala src/main/scala/oathdigital/gameplay/powers/economy/EconomySources.scala src/main/scala/oathdigital/gameplay/powers/economy/OldSongs.scala src/main/scala/oathdigital/gameplay/powers/economy/Pressgangs.scala src/main/scala/oathdigital/gameplay/powers/economy/SuitSupplyWaiver.scala src/main/scala/oathdigital/gameplay/powers/economy/ExtraWarbands.scala src/main/scala/oathdigital/gameplay/powers/economy/VowOfBeastkin.scala src/main/scala/oathdigital/gameplay/powers/economy/VowOfWandering.scala
git commit -m "refactor(powers): the cost and gain rewrites find their ops through OperationRewrite"
```

---

### Task 3: The decision rewrites on `nodes`

Refactor only: no test is added or changed. Each decision these rewrites change sits directly in its window's children, mostly as the window's own leaf, so the result is the same. Only `ConspiracyTargets` leaves an empty `Sequence` where it drops a decision (ruling 8).

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/EconomySources.scala`, `GolemLegions.scala`, `MapLibrary.scala`, `SkilledMerchants.scala`, `OldSongs.scala`, `Pressgangs.scala`, `VowOfWandering.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/search/SearchSources.scala`, `ErrandBoy.scala`, `Observatory.scala`, `Mushrooms.scala`, `LandWarden.scala`, `SpinningBee.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/targeting/ConspiracyTargets.scala`, `LostTongue.scala`

**Interfaces:**
- Consumes: `OperationRewrite.nodes` (slice 1).
- Produces:
  - `EconomySources.widened(ctx: PowerCtx, operations, cards)` and `EconomySources.exempted(ctx: PowerCtx, operations, admits)`
  - `SearchSources.withPiles(ctx: PowerCtx, operations, regions)`, `.warned(ctx, operations, warns, reason)`, `.withheld(ctx, operations, withholds, reason)`
  - `ConspiracyTargets.narrowed(ctx: PowerCtx, power, operations, protects, note)`

- [ ] **Step 1: `EconomySources.withOptions` on `nodes`**

In `EconomySources.scala` (the import was added in Task 2):

Replace the first line of `widened`, `  def widened(operations: Vector[Operation],`, and the line after it, `      cards: Vector[SiteDenizenState]): Vector[Operation] =`, and `    withOptions(operations) { options =>` with:

```scala
  def widened(ctx: PowerCtx, operations: Vector[Operation],
      cards: Vector[SiteDenizenState]): Vector[Operation] =
    withOptions(ctx, operations) { options =>
```

Replace the head of `exempted`,

```scala
  def exempted(operations: Vector[Operation],
      admits: DecisionOptionRef => Boolean): Vector[Operation] =
    withOptions(operations)(_.map {
```

with

```scala
  def exempted(ctx: PowerCtx, operations: Vector[Operation],
      admits: DecisionOptionRef => Boolean): Vector[Operation] =
    withOptions(ctx, operations)(_.map {
```

Replace `withOptions` and its doc with:

```scala
  /** `operations` with `change` applied to the options of each choose-one
    * decision. */
  private def withOptions(ctx: PowerCtx, operations: Vector[Operation])(
      change: Vector[DecisionOption] => Vector[DecisionOption])
      : Vector[Operation] =
    OperationRewrite.nodes(ctx, operations)((_, _) => {
      case decide: Decide => Vector(decide.query match {
        case one: DecisionQuery.ChooseOne =>
          decide.copy(query = one.copy(options = change(one.options)))
        case _ => decide
      })
    })
```

Update the callers:

- `GolemLegions.scala`: replace `      EconomySources.widened(operations, held(ctx.state, ctx.activePlayer)))))` with

  ```scala
        EconomySources.widened(ctx, operations,
          held(ctx.state, ctx.activePlayer)))))
  ```

- `MapLibrary.scala` and `SkilledMerchants.scala`: replace `      EconomySources.widened(operations,` with `      EconomySources.widened(ctx, operations,`.
- `OldSongs.scala`: replace `      EconomySources.exempted(operations, ref =>` with `      EconomySources.exempted(ctx, operations, ref =>`.
- `Pressgangs.scala`: replace

  ```scala
      PowerWindow.MusterSourceSelection -> Vector(Transform((_, operations) =>
        EconomySources.exempted(operations, _ => true))),
  ```

  with

  ```scala
      PowerWindow.MusterSourceSelection -> Vector(Transform((ctx, operations) =>
        EconomySources.exempted(ctx, operations, _ => true))),
  ```

- [ ] **Step 2: `SearchSources` on `nodes`**

In `src/main/scala/oathdigital/gameplay/powers/search/SearchSources.scala`:

Add after the `SearchProcedure` import:

```scala
import oathdigital.gameplay.powerresolver.{OperationRewrite, PowerCtx}
```

Give each public method a leading `ctx: PowerCtx` and pass it on:

```scala
  def withPiles(ctx: PowerCtx, operations: Vector[Operation],
      regions: Vector[Region]): Vector[Operation] =
    changed(ctx, operations) { options =>
      val offered = options.map(_.ref).toSet
      options ++ regions.map(region =>
        SearchSource.option(SearchSource.RegionalDiscard(region)))
        .filterNot(option => offered(option.ref))
    }
```

```scala
  def warned(ctx: PowerCtx, operations: Vector[Operation],
      warns: DecisionOptionRef => Boolean, reason: String)
      : Vector[Operation] = changed(ctx, operations)(_.map(option =>
    if option.available && warns(option.ref) then
      DecisionOption.Warned(option, OptionReason.Written(reason))
    else option))
```

```scala
  def withheld(ctx: PowerCtx, operations: Vector[Operation],
      withholds: DecisionOptionRef => Boolean, reason: String)
      : Vector[Operation] = changed(ctx, operations)(_.map(option =>
    if option.available && withholds(option.ref) then
      DecisionOption.Unavailable(option, OptionReason.Written(reason))
    else option))
```

Replace `changed` with:

```scala
  private def changed(ctx: PowerCtx, operations: Vector[Operation])(
      change: Vector[DecisionOption] => Vector[DecisionOption])
      : Vector[Operation] =
    OperationRewrite.nodes(ctx, operations)((_, _) => {
      case decide: Decide
          if decide.decisionId == SearchProcedure.sourceDecisionId =>
        Vector(decide.query match {
          case one: DecisionQuery.ChooseOne =>
            decide.copy(query = one.copy(options = change(one.options)))
          case _ => decide
        })
    })
```

Keep each method's doc as it is.

Update the callers:

- `Observatory.scala`: replace

  ```scala
      PowerWindow.SearchSourceSelection -> Vector(Transform((_, operations) =>
        SearchSources.withPiles(operations, Region.all))))
  ```

  with

  ```scala
      PowerWindow.SearchSourceSelection -> Vector(Transform((ctx, operations) =>
        SearchSources.withPiles(ctx, operations, Region.all))))
  ```

- `Mushrooms.scala`: replace

  ```scala
      PowerWindow.SearchSourceSelection -> Vector(Transform((_, operations) =>
        SearchSources.withheld(operations,
  ```

  with

  ```scala
      PowerWindow.SearchSourceSelection -> Vector(Transform((ctx, operations) =>
        SearchSources.withheld(ctx, operations,
  ```

- `ErrandBoy.scala`: replace its `powerresolver` import with `import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}`. Replace `        .fold(_ => operations, offered(operations, _)))))` with `        .fold(_ => operations, offered(ctx, operations, _)))))`. Replace `offered` with:

  ```scala
    private def offered(ctx: PowerCtx, operations: Vector[Operation],
        own: Region): Vector[Operation] =
      val idle: Set[DecisionOptionRef] = Set[SearchSource](
        SearchSource.WorldDeck, SearchSource.RegionalDiscard(own))
        .map(SearchSource.ref)
      SearchSources.warned(ctx, SearchSources.withPiles(ctx, operations,
        Region.all.filterNot(_ == own)), idle, ErrandBoy.idle)
  ```

- [ ] **Step 3: Land Warden and Spinning Bee on `nodes`**

In `LandWarden.scala`, replace the `powerresolver` import with:

```scala
import oathdigital.gameplay.powerresolver.{Contribution, OperationRewrite,
  Transform}
```

and replace the `SearchKeep` entry of `effects` with:

```scala
    PowerWindow.SearchKeep -> Vector(Transform((ctx, operations) =>
      OperationRewrite.nodes(ctx, operations)((_, _) => {
        case decide: Decide
            if decide.decisionId == SearchProcedure.cardDecisionId =>
          Vector(LandWarden.widened(decide))
      }))),
```

In `SpinningBee.scala`, replace the `powerresolver` import with:

```scala
import oathdigital.gameplay.powerresolver.{Contribution, ModifierPicks,
  OfferReason, OperationRewrite, OptionRestriction, PowerCtx, Transform}
```

and replace the `SearchSourceSelection` entry of `effects` with:

```scala
    PowerWindow.SearchSourceSelection -> Vector(Transform((ctx, operations) =>
      OperationRewrite.nodes(ctx, operations)((_, _) => {
        case decide: Decide
            if decide.decisionId == SearchProcedure.sourceDecisionId =>
          Vector(SpinningBee.declaration(ctx.activePlayer))
      }))),
```

Leave its `SearchBeforeDraw` entry as it is (ruling 2).

- [ ] **Step 4: Vow of Wandering's placement on `nodes`**

In `VowOfWandering.scala`, replace the `powerresolver` import with:

```scala
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OperationRewrite, PowerCtx, Transform}
```

and replace the `contributions` of `case object VowOfWandering` with:

```scala
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignPlacement -> Vector(Transform((ctx, operations) =>
      OperationRewrite.nodes(ctx, operations)((_, _) => {
        case decide: Decide if decide.decisionId == CampaignIds.placement =>
          Vector(Note(id, _ => Some(kept(PowerSourceRef.Card(cardId),
            NoteArg.Number(survivors(decide.query))))))
      }))))
```

- [ ] **Step 5: `ConspiracyTargets.narrowed` on `nodes`**

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/targeting/ConspiracyTargets.scala` with:

```scala
package oathdigital.gameplay.powers.targeting

import oathdigital.gameplay.powerresolver.{OperationRewrite, PowerCtx}
import oathdigital.model._

/** Narrows a played Conspiracy's target decision for a rule that protects
  * some of its targets (Lost Tongue).
  *
  * An `OptionRestriction` does not fit there: a decision left with no option
  * must be dropped, so that the Conspiracy plays and takes nothing. The rule
  * narrows the decision with a transform instead, and an empty `Sequence`
  * keeps a dropped decision's place. No hide hook runs for a transform, so
  * the rule's line is a `Note` put first whenever an option was dropped.
  */
private[targeting] object ConspiracyTargets:
  /** `operations` without the options `protects` names, after `power`'s
    * `note` when any was dropped. */
  def narrowed(ctx: PowerCtx, power: PowerId, operations: Vector[Operation],
      protects: DecisionOptionRef => Boolean, note: => Option[PowerNote])
      : Vector[Operation] =
    val kept = OperationRewrite.nodes(ctx, operations)((_, _) => {
      case decide: Decide => decide.query match {
        case one: DecisionQuery.ChooseOne =>
          val options = one.options.filterNot(option => protects(option.ref))
          if options.isEmpty then Vector.empty
          else Vector(decide.copy(query = one.copy(options = options)))
        case _ => Vector(decide)
      }
    })
    if kept == operations then operations
    else note.map(said => Note(power, _ => Some(said))).toVector ++ kept
```

In `LostTongue.scala`, replace `      ConspiracyTargets.narrowed(id, operations, bannerShielded(ctx, _), None))))` with:

```scala
      ConspiracyTargets.narrowed(ctx, id, operations,
        bannerShielded(ctx, _), None))))
```

- [ ] **Step 6: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.* oathdigital.gameplay.powers.search.* oathdigital.gameplay.powers.targeting.* oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.MusterProcedureSuite oathdigital.gameplay.EconomyWalkerSuite"`
Expected: pass.

Then run `./sbtw test`.
Expected: pass, with the same count as after Task 1.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/EconomySources.scala src/main/scala/oathdigital/gameplay/powers/economy/GolemLegions.scala src/main/scala/oathdigital/gameplay/powers/economy/MapLibrary.scala src/main/scala/oathdigital/gameplay/powers/economy/SkilledMerchants.scala src/main/scala/oathdigital/gameplay/powers/economy/OldSongs.scala src/main/scala/oathdigital/gameplay/powers/economy/Pressgangs.scala src/main/scala/oathdigital/gameplay/powers/economy/VowOfWandering.scala src/main/scala/oathdigital/gameplay/powers/search/SearchSources.scala src/main/scala/oathdigital/gameplay/powers/search/ErrandBoy.scala src/main/scala/oathdigital/gameplay/powers/search/Observatory.scala src/main/scala/oathdigital/gameplay/powers/search/Mushrooms.scala src/main/scala/oathdigital/gameplay/powers/search/LandWarden.scala src/main/scala/oathdigital/gameplay/powers/search/SpinningBee.scala src/main/scala/oathdigital/gameplay/powers/targeting/ConspiracyTargets.scala src/main/scala/oathdigital/gameplay/powers/targeting/LostTongue.scala
git commit -m "refactor(powers): the decision rewrites find their decision through OperationRewrite"
```

---

### Task 4: The Old Oak and Mounted Library read the gain through a record

`TheOldOakSuite` and `MountedLibrarySuite` must pass unchanged. They check the secrets, the lines, the replay and the wire round trip, so they cover the record, its codec and its take. One test is new: Mounted Library's two rewrites fold `Late`.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/TheOldOak.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/MountedLibrary.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/economy/MountedLibrarySuite.scala`

**Interfaces:**
- Consumes: `OperationRewrite.ops`, `Deferred.resolve`, `Defer`, `DeferralKey`, `FoldPhase` (slice 1).
- Produces: `TheOldOak.deferred: DeferralKey` and `MountedLibrary.deferred: DeferralKey`. `MountedLibrary.Priority` is deleted.

- [ ] **Step 1: Write the failing phase test**

In `MountedLibrarySuite.scala`, add this import:

```scala
import oathdigital.gameplay.powerresolver.{FoldPhase, Transform}
```

and add this test at the end of the class:

```scala
  test("both its rewrites fold Late, after every other Trade modifier"):
    val phases = Vector(PowerWindow.TradeGain,
      PowerWindow.TradeActionEligibility).flatMap(window =>
      power.contributions(window).collect {
        case transform: Transform => transform.phase })
    assertEquals(phases, Vector(FoldPhase.Late, FoldPhase.Late))
    assertEquals(power.priority, 0)
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.MountedLibrarySuite"`
Expected: the new test fails with `Normal` where `Late` was expected.

- [ ] **Step 3: The Old Oak records the gain**

In `TheOldOak.scala`, replace the `powerresolver` import with:

```scala
import oathdigital.gameplay.powerresolver.{Contribution, Deferred,
  OperationRewrite, Transform}
```

In the class doc, replace

```
  * faceup beast adviser, and a Trade for favor holds none. The power adds a
  * node after the gain that gains one more secret when the gain node holds a
  * secret gain for the player and the source decision was answered with The
  * Old Oak. The node is added whatever the Trade, so the window's node count
  * never depends on the answer. The generic gain line tells the secret.
```

with

```
  * faceup beast adviser, and a Trade for favor holds none. The power records
  * the player's secret gain as it runs (operation rewrite design, "record"),
  * and adds a step after the gain that gains one more secret when a gain was
  * recorded and the source decision was answered with The Old Oak. The step
  * is added whatever the Trade, so the window's node count never depends on
  * the answer. The generic gain line tells the secret.
```

Replace `effects` and `bonus` with:

```scala
  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeGain -> Vector(Transform((ctx, operations) =>
      OperationRewrite.ops(ctx, operations)((_, _) => {
        case gain @ Gain.Secrets(player, _) if player == ctx.activePlayer =>
          Vector(gain, Defer(TheOldOak.deferred, Vector(gain)))
      }) :+ bonus(ctx.activePlayer))))

  /** One more secret, when the Trade's gain was recorded and its source was
    * The Old Oak. */
  private def bonus(actor: PlayerId): Operation =
    Deferred.resolve(TheOldOak.deferred)((_, pending, _) =>
      if PowerAnswers.one(pending, TradeProcedure.decisionId)
          .contains(DecisionOptionRef.Denizen(cardId)) then
        Vector(Gain.Secrets(actor, TheOldOak.Secrets))
      else Vector.empty)
```

In `object TheOldOak`, add after `val Secrets: Int = 1`:

```scala
  /** The Trade's secret gain, recorded as it runs. */
  val deferred: DeferralKey = DeferralKey("the-old-oak")
```

- [ ] **Step 4: Mounted Library records the gain and folds `Late`**

In `MountedLibrary.scala`, replace the `powerresolver` import with:

```scala
import oathdigital.gameplay.powerresolver.{Contribution, Deferred,
  FoldPhase, OperationRewrite, Transform}
```

In the class doc, replace

```
  * The doubling is a second secret gain after the Trade's own, added for
  * every Trade and run only when Mounted Library was the source, as The Old
  * Oak's is. Its line covers that second gain's generic line only, so the
  * Trade's own "Gained" line stays and this one follows it.
  *
  * The discard is appended to the Trade's root and folds at priority 1, so
  * it comes after every other modifier's root question. It is the standard
```

with

```
  * The doubling records the Trade's own secret gain as it runs, and adds a
  * step after it for every Trade that gains as much again, run only when
  * Mounted Library was the source, as The Old Oak's is. Its line covers
  * that second gain's generic line only, so the Trade's own "Gained" line
  * stays and this one follows it.
  *
  * Both rewrites fold `Late`. The discard is appended to the Trade's root,
  * so it comes after every other modifier's root question. It is the standard
```

Delete `  override def priority: Int = Priority`.

Replace `effects`, `secretsGained` and `double` with:

```scala
  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeGain -> Vector(Transform((ctx, operations) =>
      OperationRewrite.ops(ctx, operations)((_, _) => {
        case gain @ Gain.Secrets(player, _) if player == ctx.activePlayer =>
          Vector(gain, Defer(deferred, Vector(gain)))
      }) :+ double(ctx.activePlayer), FoldPhase.Late)),
    PowerWindow.TradeActionEligibility -> Vector(Transform((ctx, children) =>
      children ++ Vector(discard(ctx.activePlayer),
        Note(id, discardedNote, covers = true)), FoldPhase.Late)))

  /** As many secrets again as the Trade gained, then its line, which covers
    * the gain's: run only when Mounted Library was the source. */
  private def double(actor: PlayerId): Operation =
    Deferred.resolve(deferred)((_, pending, held) => {
      val secrets = held.collect { case Gain.Secrets(_, amount) => amount }.sum
      if secrets > 0 && tradedWith(pending) then Vector(
        Gain.Secrets(actor, secrets),
        Note(id, doubledNote(_, actor), covers = true))
      else Vector.empty
    })
```

In `object MountedLibrary`, replace

```scala
  /** Folds after the Trade modifiers at priority 0, so its discard ends the
    * Trade. */
  val Priority: Int = 1
```

with

```scala
  /** The Trade's secret gain, recorded as it runs. */
  val deferred: DeferralKey = DeferralKey("mounted-library")
```

Run: `git grep -n "MountedLibrary.Priority" -- src`
Expected: no match.

- [ ] **Step 5: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.* oathdigital.gameplay.OperationRestrictionsSuite oathdigital.serialization.*"`
Expected: pass. `MountedLibrarySuite` has one more test than before.

Then run `./sbtw test`.
Expected: pass, with 3 more server tests than the baseline.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/TheOldOak.scala src/main/scala/oathdigital/gameplay/powers/economy/MountedLibrary.scala src/test/scala/oathdigital/gameplay/powers/economy/MountedLibrarySuite.scala
git commit -m "refactor(powers): The Old Oak and Mounted Library read the Trade's gain from a record"
```

---

### Task 5: Way Station folds `Late`, and the records

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/WayStation.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/travel/WayStationSuite.scala`
- Modify: `docs/superpowers/specs/2026-10-09-operation-rewrite-design.md` (Status)
- Modify: `docs/ROADMAP.md` ("### Phase - Operation rewrite")

**Interfaces:**
- Consumes: `FoldPhase` (slice 1).

- [ ] **Step 1: Write the failing phase test**

In `WayStationSuite.scala`, add this import:

```scala
import oathdigital.gameplay.powerresolver.{FoldPhase, Transform}
```

and add this test at the end of the class:

```scala
  test("it folds Late, after every other Travel power, at no priority"):
    val power = WayStation.forCatalog(catalog)
    assertEquals(power.contributions(PowerWindow.TravelCost).collect {
      case transform: Transform => transform.phase }, Vector(FoldPhase.Late))
    assertEquals(power.priority, 0)
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.WayStationSuite"`
Expected: the new test fails with `Normal` where `Late` was expected.

- [ ] **Step 3: Fold Way Station `Late`**

In `WayStation.scala`:

Replace the `powerresolver` import with:

```scala
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  FoldPhase, PowerCtx, Transform}
```

In the class doc, replace

```
  * It rewrites the Travel's cost node after every other Travel power (priority
  * 3), so it sees the Supply they leave. Its ruler's payment is removed. A
```

with

```
  * It rewrites the Travel's cost node `Late`, after every other Travel power,
  * so it sees the Supply they leave. It reads the payments the node lists
  * when it folds, since every question of a cost is asked before any of it
  * is paid, so it cannot see Supply spent inside a closure. Its ruler's
  * payment is removed. A
```

Re-wrap that paragraph so that `A non-ruler with favor, ...` continues it.

Delete `  override def priority: Int = 3`.

Replace `contributions` with:

```scala
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      arriving(ctx).fold(operations)((traveller, ruler) =>
        rewrite(ctx, traveller, ruler, operations)), FoldPhase.Late)))
```

- [ ] **Step 4: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.*"`
Expected: pass. `WayStationSuite` has one more test than before.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/travel/WayStation.scala src/test/scala/oathdigital/gameplay/powers/travel/WayStationSuite.scala
git commit -m "refactor(powers): Way Station folds Late instead of at priority 3"
```

- [ ] **Step 6: Run the gates**

Run, in order:
1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass. The server count is the baseline plus 4, and the frontend count is unchanged.

If the link check fails only on a file another session owns, report it and go on.

- [ ] **Step 7: Update the roadmap and the spec**

In `docs/ROADMAP.md`, delete the whole "### Phase - Operation rewrite" section: its heading, its paragraph and its remaining bullet, up to the next `### ` heading. Both slices are done.

Run: `git grep -n "Operation rewrite\|operation-rewrite" -- docs/ROADMAP.md`
Expected: no match. If another section links the spec, keep that link.

In the spec, replace the whole **Status** paragraph with:

```markdown
**Status:** designed 2026-10-09. Both slices are done.
```

Run: `python3 scripts/check-markdown-links.py`
Expected: pass, apart from any file reported in Step 6.

- [ ] **Step 8: Commit**

```bash
git add docs/ROADMAP.md docs/superpowers/specs/2026-10-09-operation-rewrite-design.md
git commit -m "docs: record operation rewrite slice 2"
```
