# Modifier Selection on the Walker, Slice 2 (Travel) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Travel asks for its modifiers inside the walk, as its first decision, and pays each one when it is picked.

**Architecture:**

- **Slice 1 built the machinery; this slice moves Travel onto it.** No walker file changes. The node (`ModifierChoice`), the picks vocabulary (`ModifierPicks`), the powers that follow the answers (`WalkerPowers.withAnswers`), the start refusal (`choosesModifiers`) and the Game Log's pick reading are all on `main`.
- **Travel's tree starts with `ModifierChoice.loop`.** What follows it, the destination decision and the route, becomes `afterModifiers`. "Start Travel" previews it, and the destination pricing (`TravelOptions.priced`) words why it cannot start.
- **`afterModifiers` is public.** The Act panel's `startable` previews it, and so do the Travel suites' cost previews. Previewing the whole tree would park on the modifier decision whenever the actor holds a Travel modifier.
- **Paying moves.** Travel joins `SelectedModifier.paidAtPick`, so a Travel modifier no longer pays at `TravelActionEligibility`.
- **Tests that start a Travel with modifiers pick them instead.** `TravelPlay.travelled` goes through `ModifierPlay.started`, as `SearchPlay.searched` does. A test that expected a refusal for a modifier its player cannot pick now expects the Travel to start on its destination: the decision is not asked when nothing can be picked.
- **The client starts Travel directly,** without the modifier flow.
- **The Game Log needs no code change.** Slice 1 already anchors the Travel tag on `ModifierPicks.settled`, and the `travel.` prefix already marks `travel.modifiers` as narrated. This slice adds a test that proves it.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-10-09-modifier-walker-design.md`. Read it whole, including its "Plan-time rulings". The slice 1 plan, `docs/superpowers/plans/2026-10-09-modifier-walker-slice1.md`, shows how Search moved. This plan copies that move for Travel. The files to copy from are:

- `SearchProcedure` (`gameplay/actions/search/`).
- `SearchPlay` and `SearchModifierChoiceSuite` (`src/test/scala/oathdigital/gameplay/`).

**Starts from:** `main` at `8b2f8ac0` or later. That includes the slice 1 merge (`5b74fec0`) and catalog batch 6 slice 2, which touches none of the files below except `docs/ROADMAP.md`.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build.
  - Several edits below remove the last use of `Refusals` from a suite, or of a private helper. Delete the import or helper when the compiler says it is unused.
  - A refutable pattern in a `val` also fails the build.
- **File size.** Production and test Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - No power name appears in walker sources, in any case, not even in a comment.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them. Re-wrap any line in this plan that comes out longer.
- **Never touch the live database `var/oathdigital`.** A browser check runs a scratch server on port 8081 through Bash, never `preview_start`.
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
  - Copy `.superpowers/` into the worktree if a ledger exists; it is gitignored.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **No compatibility for parked saves** (spec ruling 10). A journal parked mid-Travel before this change fails to resume.
- **Texts, exactly:**
  - Travel's start button: "Start Travel".
  - Start's reason at 0 Supply from the Cradle's plains on `TravelFixture.board`: "Needs 1 Supply; you have 0".
  - A second modifier left unpaid for: "Needs 1 favor; you have 0".
  - The start refusal: "travel chooses its modifiers inside the walk".
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end. This plan adds 7 server tests. The frontend loses 1: two Travel preview tests become one.
- **The verification gate** (roadmap standing rule), run in Task 3:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
  4. `git diff --check main...HEAD`

---

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `src/main/scala/oathdigital/gameplay/actions/travel/TravelProcedure.scala` | Modify | The tree starts with the modifier decision; `afterModifiers`; `startable` previews what follows the decision |
| `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` | Modify | Travel `choosesModifiers` |
| `src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala` | Modify | Travel joins `paidAtPick` |
| `src/main/scala/oathdigital/gameplay/powers/travel/Tents.scala`, `ForestPaths.scala` | Modify | Doc comments: the favor is placed at the pick |
| `src/test/scala/oathdigital/gameplay/TravelPlay.scala` | Modify | Picks modifiers through the decision |
| `src/test/scala/oathdigital/gameplay/powers/travel/TravelFixture.scala` | Modify | `candidates` previews `afterModifiers`; `parkedDecision`, `startParksOn`, `modifierDecisionId` |
| `src/test/scala/oathdigital/gameplay/powers/travel/TravelModifierChoiceSuite.scala` | Create | The Travel modifier decision |
| Travel and follow-up suites | Modify | Listed in Task 1 |
| `src/test/scala/oathdigital/application/UndoServiceSuite.scala` | Modify | Undo of a Travel pick, then of the start |
| `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`, `GameLogStartLineSuite.scala` | Modify | A Travel that picks Tents |
| `frontend/src/main/scala/oathdigital/frontend/ModifierFlowDraft.scala` | Modify | Travel starts directly |
| Frontend suites | Modify | Listed in Task 3 |
| `docs/ROADMAP.md`, `CONTEXT.md`, `docs/superpowers/specs/2026-10-07-travel-destination-walker-design.md` | Modify | Slice 2 checked; flow note; ruling 1 note |

---

### Task 1: Travel chooses its modifiers in the walk

This task turns the switch and fixes every suite the switch breaks, so that its commit is green.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/travel/TravelProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` (the `ActionRef.Travel` entry)
- Modify: `src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/Tents.scala`, `ForestPaths.scala`
- Modify: `src/test/scala/oathdigital/gameplay/TravelPlay.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/travel/TravelFixture.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/travel/TravelModifierChoiceSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/TravelProcedureSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/SelectedModifierSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/travel/TentsSuite.scala`, `ForestPathsSuite.scala`, `RoyalStablesSuite.scala`, `DragonskinDrumSuite.scala`, `NarrowPassConsentSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/FollowUpSupplyWaiverSuite.scala`
- Modify: `src/test/scala/oathdigital/application/ModifierStartControlsSuite.scala`
- Modify: `src/test/scala/oathdigital/application/TravelDestinationRowsSuite.scala`

**Interfaces:**
- Consumes, from slice 1:
  - `ModifierChoice.loop(actor, window, rest, startLabel, explain)`.
  - `ModifierPicks.start`, `ModifierPicks.decisionId(window)`.
  - `ModifierPlay.started(rules, state, procedure, actor, window, modifiers, powers)` and `ModifierPlay.pick(power)`.
  - `ParkedNode.of(state, catalog, powers)`.
- Produces:
  - `TravelProcedure.afterModifiers(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId): Either[OathViolation, Operation]`.
  - `TravelFixture.modifierDecisionId: String`.
  - `TravelFixture.parkedDecision(state: OathState): Decide`.
  - `TravelFixture.startParksOn(ready: ReadyGame): String`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/travel/TravelModifierChoiceSuite.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.PowerFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Outcomes}
import oathdigital.testkit.Unwrap.{accepted, rejected}

/** Travel asks for its modifiers inside the walk (modifier walker design,
  * slice 2). */
class TravelModifierChoiceSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val tents = CatalogNames.denizen("Tents")

  /** The actor at the Cradle's plains with `supply` Supply and `favor`
    * favor, Tents a faceup adviser. */
  private def withTents(supply: Int, favor: Int): ReadyGame =
    on(board(supply = supply))(_.adviser(actor, tents).favor(actor, favor))

  private def started(ready: ReadyGame): OathTransition =
    accepted(rules.startWalker(Ready(ready), ActionRef.Travel, actor))

  private def options(state: OathState)(using munit.Location)
      : Vector[DecisionOption] = parkedDecision(state).query match
    case DecisionQuery.ChooseOne(listed, _) => listed
    case other => fail(s"expected a choose-one, got $other")

  private val startTravel: DecisionOption =
    DecisionOption.Button(ModifierPicks.start, "Start Travel")

  test("a Travel starts on the modifier decision when a modifier can be " +
      "picked"):
    val begun = started(withTents(supply = 7, favor = 1))
    assertEquals(parkedDecision(begun.state).decisionId, modifierDecisionId)
    assertEquals(options(begun.state), Vector(startTravel,
      DecisionOption.Priced(
        DecisionOption.Denizen(DecisionOptionRef.Denizen(tents)),
        OptionPrice(favor = 1))))

  test("with no modifier to pick, a Travel starts on its destination"):
    assertEquals(startParksOn(board()), TravelProcedure.destinationDecisionId)

  test("Start says why the Travel cannot go on, until a pick makes it free"):
    val begun = started(withTents(supply = 0, favor = 1))
    assertEquals(options(begun.state).head, DecisionOption.Unavailable(
      startTravel, OptionReason.Written("Needs 1 Supply; you have 0")))
    val picked = accepted(rules.resolveWalker(begun.state, actor,
      modifierDecisionId, ModifierPlay.pick(Tents.id)))
    val after = Outcomes.ready(picked.state)
    // The favor is paid at the pick, onto the card.
    assertEquals(Look(after).favor(actor), 0)
    assertEquals(Look(after).tokensOn(tents), Tokens(1, 0))
    assertEquals(parkedDecision(picked.state).decisionId,
      TravelProcedure.destinationDecisionId)

  test("a start may no longer carry modifiers for a Travel"):
    assertEquals(rejected(rules.startWalker(Ready(withTents(7, 1)),
      ActionRef.Travel, actor, Vector(Tents.id))),
      OathViolation.InvalidEventOrder(
        "travel chooses its modifiers inside the walk"))

  test("a picked modifier pays once, at the pick"):
    val done = Outcomes.ready(accepted(travel(
      withTents(supply = 7, favor = 2), coast, Vector(Tents.id))).state)
    assertEquals(Look(done).favor(actor), 1)
    assertEquals(Look(done).tokensOn(tents), Tokens(1, 0))
```

Add these members to `TravelFixture` (`src/test/scala/oathdigital/gameplay/powers/travel/TravelFixture.scala`). Its new imports are:

- `oathdigital.gameplay.powerresolver.ModifierPicks`.
- `ParkedNode`, added to the existing `oathdigital.testkit.{...}` import.
- `oathdigital.testkit.Unwrap.accepted`.

```scala
  /** Travel's modifier decision, `travel.modifiers`. */
  val modifierDecisionId: String =
    ModifierPicks.decisionId(PowerWindow.TravelModifierSelection)

  /** The decision a walk at `state` is parked on, rebuilt as the projector
    * rebuilds it. */
  def parkedDecision(state: OathState)(using munit.Location): Decide =
    ParkedNode.of(state, catalog, WalkerPowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide
      case other =>
        munit.Assertions.fail(s"expected a parked decision, got $other")

  /** The id of the decision a Travel `actor` starts at `ready` parks on
    * first. */
  def startParksOn(ready: ReadyGame)(using munit.Location): String =
    parkedDecision(accepted(rules.startWalker(Ready(ready), ActionRef.Travel,
      actor)).state).decisionId
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.TravelModifierChoiceSuite"`

Expected:
- "with no modifier to pick" passes.
- The four others fail. The start parks on `travel.destination`, and a start with modifiers is accepted.

- [ ] **Step 3: Move Travel onto the modifier decision**

In `TravelProcedure.scala`, change the walker import to:

```scala
import oathdigital.gameplay.walker.{ModifierChoice, WalkerPowers,
  WalkerSimulation}
```

Replace the object's doc comment with:

```scala
/** Travel on the walker (travel destination design; modifier walker design).
  *
  * {{{
  * Sequence(
  *   Repeat(ModifierChoice),                // the modifiers, one per pass,
  *                                          // then "Start Travel"
  *   Sequence(
  *     Decide(travel.destination),          // a board choose-one over sites
  *     Branch { after the answer:
  *       Sequence(                          // window = TravelActionEligibility
  *         Sequence(                        // window = TravelCost
  *           SpendSupply(actor, printedBase),
  *           Move(Pawn, source -> destination))) }))
  * }}}
  *
  * **The modifiers come first.** Each pick pays at once, so the destination
  * decision shows what each site costs under them.
  *
  * **The destination is a decision.** A power that cares which site was
  * picked acts inside the walk and may ask its own question (Way Station).
  * The start carries no selection.
  *
  * **The windows sit inside the `Branch`.** Every Travel power finds the
  * route by flattening its windowed node to the `Move`, and a `Branch`
  * flattens to nothing, so a window above it would see no route.
  *
  * **The source is a start fact.** A resume after the pawn moved could not
  * read it from the pawn, so a fresh build reads the pawn and the start
  * stores it; a resume reads it back from `walkerStartFacts`.
  *
  * **What does not gate a start.** Supply, terrain, role and Foundation faces
  * are not build gates: "Start Travel" previews every destination and says
  * why none can be reached, and with no modifier to pick, the registry's
  * playable-option gate refuses a start none of them can complete.
  */
```

Add the label beside `heading`:

```scala
  private val startLabel = "Start Travel"
```

Rename the private `tree` to `journey`; its body does not change. Then add a new `tree` and the public `afterModifiers`:

```scala
  /** The destination decision and the Travel to it: what a fresh start runs
    * after its modifier decision. The Act panel's [[startable]] previews it,
    * and so do the Travel suites. */
  def afterModifiers(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Either[OathViolation, Operation] = for
    _ <- OathLifecycle.validateAct(OathState.Ready(state), actor)
    source <- actorSite(state, actor).toRight(
      OathViolation.PawnSiteMissing(actor))
  yield journey(catalog, state, actor, source)

  private def tree(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, source: SiteId): Operation =
    val rest = journey(catalog, state, actor, source)
    Sequence(Vector(ModifierChoice.loop(actor,
      PowerWindow.TravelModifierSelection, rest, startLabel,
      Some(TravelOptions.priced)), rest))

  /** The destination pick and the route to it. */
  private def journey(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, source: SiteId): Operation =
    // the old `tree` body, unchanged: `pick`, `route`, then
    // Sequence(Vector(pick, route))
```

`build` and `rebuild` keep calling `tree(catalog, state, activePlayer, source)`. Replace `startable` with:

```scala
  /** Whether a Travel could start now under `powers`: some destination
    * survives the preview of what follows the modifier decision. The Act
    * panel asks it of each selection `ModifierOffers.selections` tries. */
  def startable(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): Boolean =
    afterModifiers(catalog, state, actor)
      .flatMap(WalkerSimulation.preview(_, state, powers))
      .exists(_.exists(_.outcome.isRight))
```

In `WalkerProcedureRegistry.scala`, give the `ActionRef.Travel` entry `choosesModifiers = true`, after `requiresPlayableOption = true`. Replace its doc comment with:

```scala
    /** Travel asks for its modifiers, then parks on its destination (travel
      * destination design; modifier walker design). Nothing runs before
      * those decisions, so the start is previewed through the
      * playable-option gate. Its source is a start fact: a resume after the
      * pawn moved reads it back rather than from the pawn.
      */
```

In `SelectedModifier.scala`:

```scala
  val paidAtPick: Set[MajorActionType] =
    Set(MajorActionType.Search, MajorActionType.Travel)
```

In the trait's doc comment, change "A Search modifier is paid when it is picked" to "A Search or Travel modifier is paid when it is picked".

In `Tents.scala`, replace the doc comment's second paragraph with:

```scala
  * The player owns the choice to pick it. Picking Tents places the favor at
  * once, in the modifier decision, whether or not the destination is in the
  * pawn's region, and the Supply payment (terrain adds included) is removed
  * only when it is.
```

In `ForestPaths.scala`, replace "Selecting Forest Paths places the favor at the start of every Travel (the kit does)." with "Picking Forest Paths places the favor at once, in the modifier decision.".

- [ ] **Step 4: Run the new suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.TravelModifierChoiceSuite"`

Expected: PASS, 5 tests.

- [ ] **Step 5: Move the shared helpers onto the decision**

`src/test/scala/oathdigital/gameplay/TravelPlay.scala`:

```scala
  /** `actor` starts a Travel, picks `modifiers` in its modifier decision,
    * and picks `destination`. The events are every command's. */
  def travelled(rules: OathRules, state: OathState, actor: PlayerId,
      destination: SiteId, modifiers: Vector[PowerId] = Vector.empty)
      : Either[OathViolation, OathTransition] = for
    started <- ModifierPlay.started(rules, state, ActionRef.Travel, actor,
      PowerWindow.TravelModifierSelection, modifiers)
    picked <- rules.resolveWalker(started.state, actor,
      TravelProcedure.destinationDecisionId, this.destination(destination))
  yield picked.copy(events = started.events ++ picked.events)
```

In `TravelFixture.candidates`, preview what follows the decision:

```scala
    TravelProcedure.afterModifiers(catalog, ready, actor)
      .flatMap(WalkerSimulation.preview(_, ready, powers(modifiers)))
```

In `TravelProcedureSuite.previewedCosts`, make the same change: `TravelProcedure.afterModifiers(catalog, ready, actor)` in place of `TravelProcedure.build(catalog, ready, actor, Vector.empty)`.

- [ ] **Step 6: Fix the tree-shape tests in `TravelProcedureSuite`**

In "the destination offers every in-play site but the pawn's":

```scala
    // The modifier decision comes first; the destination opens what follows.
    val offered = tree.children(1).children.head match
```

In "a resume reads the source from the start fact, not the pawn":

```scala
    val pending = PendingTree(Vector("1", "1"), Vector(Answered(
      TravelProcedure.destinationDecisionId, TravelPlay.destination(coast),
      actor)))
    val selected = rebuilt.children(1).children(1) match
```

- [ ] **Step 7: A modifier nobody can pick no longer refuses the start**

The modifier decision is not asked when nothing can be picked, so the Travel starts on its destination. Rewrite each refusal test.

`TentsSuite`, "it cannot be selected without a favor to place, or onto an occupied card":

```scala
  test("without a favor to place, or onto an occupied card, it is not " +
      "offered: the Travel starts on its destination"):
    val broke = on(held)(_.favor(actor, 0))
    assertEquals(startParksOn(broke), TravelProcedure.destinationDecisionId)
    val occupied = on(held)(_.tokens(tents, favor = 1))
    assertEquals(startParksOn(occupied),
      TravelProcedure.destinationDecisionId)
```

`TentsSuite`, "Tents and Forest Paths together need two favor: one favor is refused at selection, before anything is paid":

```scala
  test("Tents and Forest Paths together need two favor: with one, picking " +
      "Tents leaves Forest Paths unpaid for"):
    val paths = CatalogNames.denizen("Forest Paths")
    val both = Vector(Tents.id, ForestPaths.id)
    def ready(favor: Int) = on(held)(_.adviser(actor, paths)
      .favor(actor, favor))
    val begun = accepted(rules.startWalker(Ready(ready(1)), ActionRef.Travel,
      actor))
    val picked = accepted(rules.resolveWalker(begun.state, actor,
      modifierDecisionId, ModifierPlay.pick(Tents.id)))
    val listed = parkedDecision(picked.state).query match
      case DecisionQuery.ChooseOne(options, _) => options
      case other => fail(s"expected a choose-one, got $other")
    assertEquals(listed.find(_.ref == DecisionOptionRef.Denizen(paths)),
      Some(DecisionOption.Unavailable(
        DecisionOption.Denizen(DecisionOptionRef.Denizen(paths)),
        OptionReason.Written("Needs 1 favor; you have 0"))))
    val done = Outcomes.ready(accepted(travel(ready(2), coast, both)).state)
    assertEquals(Look(done).favor(actor), 0)
    assertEquals(Look(done).tokensOn(tents), Tokens(1, 0))
    assertEquals(Look(done).tokensOn(paths), Tokens(1, 0))
```

`TentsSuite` needs four new imports:

- `oathdigital.gameplay.ModifierPlay`.
- `oathdigital.gameplay.actions.travel.TravelProcedure`.
- `oathdigital.model.OathState.Ready`.
- `oathdigital.testkit.Unwrap.accepted`, which is already imported.

Drop `Refusals` from the import if nothing else in the suite uses it.

`ForestPathsSuite`, "it cannot be selected without a favor to place":

```scala
  test("without a favor to place it is not offered: the Travel starts on " +
      "its destination"):
    val broke = on(held)(_.warbandsAt(pass, actor, 1)
      .denizen(beast, at = mountain).favor(actor, 0))
    assertEquals(startParksOn(broke), TravelProcedure.destinationDecisionId)
```

`RoyalStablesSuite`, "it may not be used at a site the actor neither stands at nor rules":

```scala
  test("it is not offered at a site the actor neither stands at nor rules: " +
      "the Travel starts on its destination"):
    val ready = on(board())(_.denizen(stables, at = hinterlandPlains)
      .warbandsAt(pass, actor, 1))
    assertEquals(startParksOn(ready), TravelProcedure.destinationDecisionId)
```

`DragonskinDrumSuite`, "a facedown Drum is not usable":

```scala
  test("a facedown Drum is not offered: the Travel starts on its " +
      "destination"):
    assertEquals(startParksOn(withRelic(board(), drum,
      Orientation.FaceDown)), TravelProcedure.destinationDecisionId)
```

Each of these suites imports `oathdigital.gameplay.actions.travel.TravelProcedure` if it does not already, and drops `Refusals` if that was its last use.

- [ ] **Step 8: Start the other Travel suites through the decision**

`NarrowPassConsentSuite.dunesOffered` starts with `modifiers` under its own powers:

```scala
    val started = accepted(ModifierPlay.started(
      new OathRules(catalog, walkerPowerCatalog = powers), Ready(ready),
      ActionRef.Travel, actor, PowerWindow.TravelModifierSelection,
      modifiers, powers))
```

Import `oathdigital.gameplay.ModifierPlay`. The `PlacementPicks.parkedOptions` line stays.

`FollowUpSupplyWaiverSuite`, "a queued Travel with Special Envoy enters Rest, and the queued Campaign still starts there":

```scala
    val started = accepted(ModifierPlay.started(rules, board,
      ActionRef.Travel, p1, PowerWindow.TravelModifierSelection,
      Vector(SpecialEnvoy.id)))
```

The suite is in `oathdigital.gameplay`, so `ModifierPlay` needs no import.

`TravelDestinationRowsSuite.rows`:

```scala
    val started = accepted(ModifierPlay.started(rules, Ready(ready),
      ActionRef.Travel, actor, PowerWindow.TravelModifierSelection,
      modifiers))
```

Import `oathdigital.gameplay.ModifierPlay`.

`ModifierStartControlsSuite`: replace the first Travel test with:

```scala
  test("Travel is offered at 0 Supply when Tents would make it free; the " +
      "start asks for the modifier, and picking it reaches the destination"):
    val ready = broke(favor = 1, tents = true)
    assert(controls(ready, actor).contains("beginTravel"))
    val started = accepted(rules.startWalker(OathState.Ready(ready),
      ActionRef.Travel, actor))
    val modifiers = parked(started.state)
    assertEquals(modifiers.decisionId, travelModifiers)
    assertEquals(modifiers.query match
      case DecisionQuery.ChooseOne(options, _) => options.headOption
      case _ => None,
      Some(DecisionOption.Unavailable(
        DecisionOption.Button(ModifierPicks.start, "Start Travel"),
        OptionReason.Written("Needs 1 Supply; you have 0"))))
    val picked = accepted(rules.resolveWalker(started.state, actor,
      travelModifiers, ModifierPlay.pick(Tents.id)))
    assertEquals(parked(picked.state).decisionId,
      TravelProcedure.destinationDecisionId)
    // Without Tents nothing can be picked, and the start is refused.
    assertEquals(refusal(broke(favor = 1, tents = false), actor,
      ActionRef.Travel, Vector.empty), noSupplyFor(1))

  private val travelModifiers =
    ModifierPicks.decisionId(PowerWindow.TravelModifierSelection)
```

Import `oathdigital.gameplay.actions.travel.TravelProcedure`. The suite's `parked` helper, from slice 1, serves both actions. If `assertStarts` is now unused, delete it.

`SelectedModifierSuite`: replace "a cost is paid at the root of the action's tree, and only for a costed modifier" with:

```scala
  test("a cost not paid at the pick is paid at the root of the action's " +
      "tree, and only for a costed modifier"):
    val muster: Set[MajorActionType] = Set(MajorActionType.Muster)
    val priced = Probe(card, Cost(favor = 1), muster)
    assertEquals(priced.contributions.keySet,
      Set[PowerWindow](PowerWindow.MusterActionEligibility))
    assertEquals(Probe(card, Cost.free, muster).contributions,
      Map.empty[PowerWindow, Vector[Contribution]])
    // Search and Travel pay at the pick, in the modifier decision.
    val picked = Probe(card, Cost(favor = 1),
      Set(MajorActionType.Travel, MajorActionType.Search))
    assertEquals(picked.contributions,
      Map.empty[PowerWindow, Vector[Contribution]])
```

- [ ] **Step 9: Run every server test**

Run: `./sbtw "test"`

Expected: PASS.

The goldens should not change. A Travel that asks nothing records no step for its modifier decision, so no event index moves.

Some other test may start a Travel directly while its actor holds a Travel modifier, then answer the destination. That walk now parks on `travel.modifiers` first. Fix such a test as follows:
- If it starts through `TravelPlay`, `ModifierPlay.started` or a `Situation`, give it `ModifierPlay.answers(PowerWindow.TravelModifierSelection, Vector.empty)`.
- Otherwise, answer `ModifierPlay.start` before the destination.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/travel/TravelProcedure.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala \
  src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala \
  src/main/scala/oathdigital/gameplay/powers/travel/Tents.scala \
  src/main/scala/oathdigital/gameplay/powers/travel/ForestPaths.scala \
  src/test/scala/oathdigital/gameplay/TravelPlay.scala \
  src/test/scala/oathdigital/gameplay/TravelProcedureSuite.scala \
  src/test/scala/oathdigital/gameplay/FollowUpSupplyWaiverSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/SelectedModifierSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/TravelFixture.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/TravelModifierChoiceSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/TentsSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/ForestPathsSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/RoyalStablesSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/DragonskinDrumSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/NarrowPassConsentSuite.scala \
  src/test/scala/oathdigital/application/ModifierStartControlsSuite.scala \
  src/test/scala/oathdigital/application/TravelDestinationRowsSuite.scala
git commit -m "feat(travel): choose Travel modifiers inside the walk"
```

Add any other suite that Step 9 fixed to the `git add` list. End the message with your trailer.

**Checkpoint: review Task 1 before going on.**

---

### Task 2: Undo and the Game Log for a Travel pick

**Files:**
- Modify: `src/test/scala/oathdigital/application/UndoServiceSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogStartLineSuite.scala`

**Interfaces:**
- Consumes:
  - `ParkedServiceFixture.startTravel(actor)`.
  - `ModifierPlay.pick`, `ModifierPlay.answers`.
  - `TravelPlay.destination`.
- Produces: `LogScripts.travelWithTents: Script`.

No production code changes in this task. Both tests should pass at once. If either fails, the failure is a slice 1 gap for Travel: stop and report it rather than patching around it.

- [ ] **Step 1: Undo takes back a pick, then the start**

Add to `UndoServiceSuite`:

```scala
  test("Undo takes back a Travel modifier pick, and then the start"):
    val withTents = table.pawn(p1, "Ancient City").adviser(p1, "Tents")
      .favor(p1, 1)
    val (service, _) = withTents.service()
    val started = accepted(service.handle("g", 0L,
      ParkedServiceFixture.startTravel(p1)))
    val picked = accepted(service.handle("g", started.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision("travel.modifiers",
        ModifierPlay.pick(Tents.id)))))
    val unpicked = accepted(service.handle("g", picked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(unpicked.state, started.state)
    val undone = accepted(service.handle("g", unpicked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.state, withTents.state)
```

Imports: `oathdigital.gameplay.ModifierPlay` and `oathdigital.gameplay.powers.travel.Tents`.

- [ ] **Step 2: A script that picks Tents**

Add to `LogScripts`, after `facedownWithCry`:

```scala
  /** Tents as p1's adviser, with 1 favor to pay for it. The Travel picks
    * Tents, its only modifier, so it starts by itself, then goes to the
    * other site of Ancient City's region, which Tents makes free. */
  def travelWithTents(using munit.Location): Script =
    val (service, act) = atTable("tents", tableWithOnePawnPerSite
      .adviser(p1, "Tents").favor(p1, 1).banditsRefilled)
    val map = act.ready.game.current.map
    val home = Look(act.ready).pawn(p1)
    val nearby = map.inPlay.find(site => site != home &&
      map.regionOf(site) == map.regionOf(home)).getOrElse(
      munit.Assertions.fail("Ancient City's region has one site"))
    val destination: Answers = {
      case park if park.decisionId == TravelProcedure.destinationDecisionId =>
        TravelPlay.destination(nearby)
    }
    act.withAnswers(destination.orElse(ModifierPlay.answers(
      PowerWindow.TravelModifierSelection, Vector(Tents.id))))
      .after(ParkedServiceFixture.startTravel(p1))
    Script("tents", service, p1)
```

Change `import oathdigital.gameplay.ModifierPlay` to `import oathdigital.gameplay.{ModifierPlay, TravelPlay}`. Then add:

- `import oathdigital.gameplay.actions.travel.TravelProcedure`.
- `import oathdigital.gameplay.powers.travel.Tents`.

- [ ] **Step 3: The start line names the pick**

Add to `GameLogStartLineSuite`:

```scala
  test("a Travel with a pick names it on its own line, with no Chose line"):
    val entries = format(travelWithTents, None)
    val tag = entryStarting(entries, "Travel")
    assertEquals(tag.kind, LogKind.Section)
    // Tents makes the Travel free, so the tag carries no cost.
    assertEquals(text(tag), "Travel")
    assertEquals(text(entries(entries.indexOf(tag) + 1)), "With Tents")
    assert(text(entries(entries.indexOf(tag) + 2))
      .startsWith("Travelled to "), texts(entries))
    assert(!texts(entries).exists(_.startsWith("Chose")), texts(entries))
```

- [ ] **Step 4: Run them**

Run: `./sbtw "testOnly oathdigital.application.UndoServiceSuite oathdigital.application.gamelog.GameLogStartLineSuite"`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/test/scala/oathdigital/application/UndoServiceSuite.scala \
  src/test/scala/oathdigital/application/gamelog/LogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogStartLineSuite.scala
git commit -m "test(travel): undo a Travel pick, and log the Travel's picks"
```

End the message with your trailer.

---

### Task 3: The client starts Travel directly; record the slice and run the gates

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/ModifierFlowDraft.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/ModifierFlowSuite.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/ModifierFlowDrafts.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/ModifierSelectionStateSuite.scala`
- Modify: `docs/ROADMAP.md`, `CONTEXT.md`, `docs/superpowers/specs/2026-10-07-travel-destination-walker-design.md`

**Interfaces:** none new.

- [ ] **Step 1: Make the flow test fail**

In `ModifierFlowSuite`, replace the two tests "a Travel start previews as travel and orders the modifiers it offers" and "a Travel start with no modifiers goes out at once, naming no destination" with:

```scala
  test("a Travel start goes out directly, naming no destination"):
    val FlowUnderTest(host, ui) = flow()
    ui.submitCommand(travel)
    assertEquals(host.previews, Vector.empty)
    assertEquals(host.sent, Vector(travel -> Vector.empty))
    assertEquals(host.currentDrafts.modifiers, None)
```

Then make "confirming leaves the flow and sends the folded submission without a redraw" use Muster:
- Replace each `"travel"` in it with `"muster"`.
- Replace `travel` with a new `private val muster = GameCommand.StartWalker("muster", Vector.empty)`, declared beside `travel`.

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ModifierFlowSuite"`

Expected: FAIL. "a Travel start goes out directly" sees a preview request.

- [ ] **Step 2: Drop Travel from the flow**

In `ModifierFlowDraft.scala`:

```scala
  private val walkerActions: Set[String] = Set("recover", "forge",
    "muster", "trade", "challenge")
```

In its doc comment, change "Search and facedown-adviser play ask for their modifiers inside the walk (modifier walker design), so they are absent and start directly." to "Search, facedown-adviser play and Travel ask for their modifiers inside the walk (modifier walker design), so they are absent and start directly.".

- [ ] **Step 3: Move the other drafts off Travel**

- **`ModifierFlowDrafts`:** replace each `"travel"` with `"muster"`, and "Red's Travel flow" with "Red's Muster flow".
- **`ModifierSelectionStateSuite`, `renderOrderingPanel` and "a stale context clears the flow draft":** replace `"travel"` with `"muster"` in the `MajorActionPreviewResponse`, the context and the `StartWalker`. Leave the `PreviewModifier` at line 159 alone.
- **`ModifierSelectionStateSuite`, the registered-key test:** expect `Vector("recover", "forge", "muster", "trade")`. Replace its comment with:

```scala
    // A registered key names its action whatever start arguments it carries
    // (Trade carries its own). Search, play-facedown-adviser and Travel
    // choose their modifiers inside the walk, and place-banner-resource is
    // not registered, so none of them names one.
```

Run: `./sbtw "frontend/test"`

Expected: PASS.

- [ ] **Step 4: Record the slice**

- **`docs/ROADMAP.md`:** check `- [x] Slice 2: Travel.`
- **`CONTEXT.md`, the modifier flow entry:** change "Search and facedown-adviser play have no flow" to "Search, facedown-adviser play and Travel have no flow".
- **`docs/superpowers/specs/2026-10-07-travel-destination-walker-design.md`:** append to ruling 1, indented under it, as the search source spec does:

```markdown
   [Modifier selection on the walker](2026-10-09-modifier-walker-design.md)
   moved Travel's modifiers into the walk in its slice 2; this ruling is
   rewritten in its slice 4.
```

- [ ] **Step 5: Run the verification gate**

1. `./sbtw "test" "frontend/test"`. Compare the counts with the baseline: server +7, frontend −1.
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
4. `git diff --check main...HEAD`

All must pass.

- [ ] **Step 6: Browser check on a scratch database**

Follow the memory recipe (`preview-start-hits-live-db`). Never use `preview_start`, and never open `var/oathdigital`.

1. Seed a scratch HSQLDB in the scratchpad from `FirstGameSetupFixture.initialSituation().endingWake(p2)`. Arrange Tents as p2's adviser (`Play` onto `Location.PlayArea(p2)`) and 1 favor (`Gain.Favor`).
2. Run the server on port 8081 through Bash, in the background.
3. Open the game as p2 in the browser pane.
4. Check each of these:
   - The Act panel's Travel button parks on "Choose modifiers, or start", with "Start Travel" first and Tents priced at 1 favor.
   - Picking Tents moves on to the destinations, and same-region sites read "0 Supply".
   - Undo takes back the pick.
5. Stop the server. Delete the throwaway seeding suite.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/ModifierFlowDraft.scala \
  frontend/src/test/scala/oathdigital/frontend/ModifierFlowSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/ModifierFlowDrafts.scala \
  frontend/src/test/scala/oathdigital/frontend/ModifierSelectionStateSuite.scala \
  docs/ROADMAP.md CONTEXT.md \
  docs/superpowers/specs/2026-10-07-travel-destination-walker-design.md
git commit -m "feat(frontend): start Travel without the modifier flow"
```

End the message with your trailer.

**Checkpoint: final review of the branch against the spec's slice 2.**
