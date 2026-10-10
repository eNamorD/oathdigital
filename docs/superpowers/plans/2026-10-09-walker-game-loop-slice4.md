# Walker-Driven Game Loop, Slice 4 (The Act Menu) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Act phase is played at a menu, as Wake and Rest are. Every menu option is priced and judged by an exact dry run of its own procedure, which searches through the procedure's modifier decision. The frontend draws all three menus as one grouped panel. The start commands `StartWalker`, `UsePower` and `BeginRest` go, and so do the procedures' own phase checks (spec ruling 9).

**Architecture:**

- **The exact start.** `WalkerSimulation.started` walks a freshly built tree past its modifier decision. It tries "Start" with no pick first, then each available pick in turn, depth first. `QueuedStart.priced` builds on it: it returns what the start would cost, or why it would be refused. `QueuedStart.refusal` becomes `priced(...).left.toOption`, so the Wake and Rest menus and the follow-up offer get the exact check too.
- **The Act menu.** `PhaseMenu.Menu.Act` is a third menu, asked by a new triggered procedure, `TriggeredProcedureRef.ActMenu`. Its options are the Act actions in a new `ActOffers` list, each Act phase power's source, and End Act and Rest. Each available option carries its price as a badge. Each unavailable option carries its reason. Act always asks, even when only End Act and Rest is open.
- **The driver parks it.** After any walk that leaves the queue empty in Act, the driver parks the Act menu. Tests that started actions with commands now pick them from the menu, through new test helpers.
- **The panel.** A new frontend surface, `Surface.Menu`, draws every phase menu as grouped buttons. The hand-mapped Act buttons and their files go.
- **The start commands go.** Nothing can send `StartWalker`, `UsePower` or `BeginRest` once every phase parks a menu. `OathLifecycle.validateAct` and `validateReady` go with them.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md`, including its plan-time rulings for slices 1 to 3, before starting. Task 1 adds this plan's rulings to it.

**Starts from:** `main`, after walker game loop slice 3 has merged. Check that `git log --oneline main` shows slice 3's last commit, "docs: record walker game loop slice 3". If it does not, stop and report.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`.
  - An unused import or private member fails the build. Remove every import a deletion leaves unused.
  - A non-exhaustive match fails the build. A new enum case needs an arm in every match over its enum. Add `TriggeredProcedureRef.ActMenu` beside `TriggeredProcedureRef.RestMenu` wherever a match lists `RestMenu`.
  - A refutable pattern in a `val` also fails it, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - No power name appears in walker or `gameplay/operations` sources.
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
- **No compatibility** (spec ruling 14). Stored journals with the deleted commands stop replaying. Do not bump the journal version.
- **Changed tests.** This slice changes behavior, so existing tests change. Each task names the expected changes. When a test fails for a reason the task does not name, stop and report it.
- **Texts.** Docs and commit messages use plain English. Game text (headings, labels, reasons, badges) is given in the steps; use it exactly.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end. Report every test added and deleted.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

Task 1 writes these into the spec.

1. **An option is available when some way through its modifier decision starts it.** The dry run walks the start as the driver would. When the walk parks on the modifier decision, it answers "Start" with no pick if "Start" is available. Otherwise it tries each available pick in turn, depth first, and never tries the same set of picks twice. Once every modifier is picked, the walk goes past the decision without "Start". Such a walk counts only when its next decision has an answer the walk can go on from. When no way starts, the option shows the reason "Start" shows before any pick. This check replaces the playable-option gate in `QueuedStart`, so the Wake and Rest menus and the follow-up offer use it too.
2. **The price is the cheapest way's.** An available option carries a badge with the Supply and favor its start spends:
   - what the walk spends up to its first decision past the modifiers;
   - plus, for a procedure that previews that decision (the playable-option gate: Search, Travel, Muster and Trade), the cheapest answer to it.

   When another answer costs more Supply, the Supply amount gets a "+"; when another costs more favor, so does the favor amount, if the cheapest way spends favor at all. Favor placed on a card (a modifier's payment, Muster's favor) is not counted, as the priced options already do not count it. A free option has no badge. Examples: "1 Supply", "2 Supply · 1 favor", "1+ Supply", "2+ Supply · 1+ favor".
3. **An unavailable option says why,** using the first of these that applies:
   - "Needs N Supply; you have M", or "Needs N favor; you have M", when the start would open with the actor's Supply raised to the track's maximum (and then also their favor raised);
   - the reason "Start" shows, when no way through the modifier decision opens;
   - the procedure's own words for its refusal (the table in `ActOffers.reason` and `PhaseMenu.reason`);
   - a fallback per procedure.
4. **The Act menu's options,** in this order:
   - the major actions: Search, Travel, Campaign, Muster, Trade for favor, Trade for secrets, Forge, Recover, Challenge;
   - the minor actions: Place favor or secrets on a banner, Negotiate, Play facedown adviser, Peek at relics at your site, Reveal a relic, Move warbands;
   - each Act phase power's source;
   - End Act and Rest, labelled "End Act and Rest (+N Supply)" when Rest would return N Supply, as the old button was.

   Each action's option is a button keyed by its procedure's key; Trade's are `trade.favor` and `trade.secret`, and End Act and Rest's is `end-act`. The menu's heading is "Act: take an action, use a power or rest".
5. **Act always asks.** The driver parks the Act menu even when End Act and Rest is the only available option (spec ruling 3).
6. **One panel for every menu.** The frontend draws a parked menu as grouped buttons:
   - any button that is neither an action nor the ending, without a heading (Take Wealth);
   - "Major actions";
   - "Minor actions";
   - "Powers": every option that is not a button;
   - the phase's ending, last and alone.

   A button reads "Label (badge)" when its option has a badge. An unavailable option is disabled, with its reason on hover and printed below.
7. **The start commands go in slice 4.** Every phase now has a menu parked while the game is live, so nothing can send `StartWalker`, `UsePower` or `BeginRest`. They go with their intents, codecs, mapper arms and authorization helpers. `OathRules.startWalker` stays: the driver and the tests start procedures through it.
8. **Spec ruling 9 lands.** `OathLifecycle.validateAct` and `validateReady` go, with the follow-up exemption, and Finish Rest's own phase check goes. The menus check the phase. Use Power keeps its timing check, because a power's timing is its own rule and the menu reads it to list the power.
9. **The projection's Act leftovers wait for slice 5.** These stay until slice 5, unread by the frontend:
   - `LegalActionProjector`'s Act controls, `MinorActionsProjection` and `PhasePowerProjector`;
   - `GameProjection.actionSelectionOpen` and `restSupplyGain`;
   - the procedures' `startable` and `startOptions` functions, and `ModifierOffers.selections`.

   This replaces slice 3's note that `MinorActionsProjection` would go with the panel.
10. **The budget is a benchmark run by hand,** as the Campaign budget is. `ActMenuBudget` measures the Act menu's park and its read on a full board; each must take under 50 ms. If either is over budget, stop and report: sharing the search memo (spec ruling 8) is a design step of its own.
11. **Tests start actions from the menu.** A situation step `Step.Start(player, procedure, args)` picks the procedure's option from the parked menu. On a table where no menu is parked, it starts the procedure directly through the rules. `MenuPlay.starting` is the service command that picks it.

## What exists before this plan

Check each of these in the code before Task 1. If a name or signature differs, read slices 1 to 3's commits and adapt. Stop and report if the difference is more than a name.

- `gameplay/walker/QueuedStart.scala`: `refusal(catalog, ready, entry, powers, phasePowers): Option[OathViolation]`, `startingFrom`, and the private `unplayable`.
- `gameplay/walker/WalkerSimulation.scala`: `refusal`, `preview`, `previewParked`, and the private `answer`, `after(state, pending: PendingTree, next, events)`, `recordedOperations` and `guarded`.
- `gameplay/walker/PricedOptions.scala`: public `options`; private `answerable`, `paths`, `spent` and `lift`.
- `gameplay/walker/ModifierChoice.scala`: `ModifierChoice.cannotStart`.
- `gameplay/powerresolver/ModifierPicks.scala`: `start`, `isDecision`.
- `gameplay/phases/PhaseMenu.scala`: `decisionId`, `enum Menu(phase, procedure, ending, endingOption, endingLabel, heading)` with `Wake` and `Rest`, `of`, `asking`, `takeWealth`, `Offered`, `offers`, `onlyEnding`, `build`, and the private `reason`.
- `gameplay/OathDriver.scala`: `next(transition, finished: ProcedureRef)` and the private `phaseEnd`.
- `gameplay/walker/WalkerProcedureRegistry.scala`: the private `menuEntry` and `lookup`; `isRegistered` reads `PhaseMenu.asking`.
- `gameplay/OathLifecycle.scala`: `validateReady` and `validateAct`, called by the procedures listed in Task 5.
- `gameplay/actions/minor/MinorActionProcedures.scala`: the private `started`, which calls `validateAct`.
- `gameplay/phases/rest/FinishRestProcedure.scala`: `supplyGainAtRest(ready, resting): Either[OathViolation, Int]` and `gate`.
- `application/GameCommands.scala`: `StartWalker(procedure, start: StartPayload)`, `BeginRest(playerId)` and `UsePower(playerId, power, source)`.
- `frontend/.../ActionDecisionRenderer.scala`: the `if showActActionControls(value, presentation) then` block.
- Tests: `testkit/MenuPlay`, `testkit/Situation` with `enum Step { Command; Arrange }`, `testkit/SituationDriver.Rules` and `Journaled`, `testkit/Table.phaseMenuParked`, `gameplay/PhaseMenuSuite`, `gameplay/OathDriverSuite`, `gameplay/SearchBudget`, `gameplay/walker/ModifierChoiceSuite`, `application/PhaseMenuServiceSuite`.

## File map

| File | Change | Task |
|---|---|---|
| `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md` | modify | 1, 6 |
| `src/main/scala/oathdigital/model/GameViolation.scala` | modify | 1 |
| `src/main/scala/oathdigital/gameplay/walker/{WalkerSimulation,PricedOptions,QueuedStart}.scala` | modify | 1 |
| `src/main/scala/oathdigital/model/ProcedureRef.scala` | modify | 2 |
| `src/main/scala/oathdigital/gameplay/actions/economy/TradeProcedure.scala` | modify | 2 |
| `src/main/scala/oathdigital/gameplay/phases/ActOffers.scala` | create | 2 |
| `src/main/scala/oathdigital/gameplay/phases/PhaseMenu.scala` | modify | 2 |
| `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` | modify | 2 |
| `src/main/scala/oathdigital/gameplay/{OathRulesWalker,OathDriver}.scala` | modify | 2, 3 |
| `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala` | modify | 2 |
| `src/main/scala/oathdigital/application/gamelog/ActionLines.scala` (and any match the compiler names) | modify | 2 |
| `src/main/scala/oathdigital/gameplay/OathLifecycle.scala` and the procedures that call it | modify | 5 |
| `src/main/scala/oathdigital/application/{GameCommands,Authorization,GameIntentMapper,GameApplicationService}.scala` | modify | 5 |
| `shared/src/main/scala/oathdigital/protocol/{CommandIntents,CommandIntentCodec,CommandIntentDecoders}.scala` | modify | 5 |
| `frontend/src/main/scala/oathdigital/frontend/MenuPanel.scala` | create | 4 |
| `frontend/src/main/scala/oathdigital/frontend/{ParkedDecision,ActionDecisionRenderer,ServerUiSupport}.scala` | modify | 4 |
| `frontend/src/main/scala/oathdigital/frontend/{EconomyControls,BannerControls,NegotiationControls,CampaignControls,PhasePowerButtons}.scala` | delete | 4 |
| `src/test/scala/oathdigital/gameplay/walker/StartSearchSuite.scala` | create | 1 |
| `src/test/scala/oathdigital/gameplay/QueuedStartSuite.scala` | create | 1 |
| `src/test/scala/oathdigital/testkit/{MenuPlay,Situation,SituationDriver}.scala` | modify | 2, 5 |
| `src/test/scala/oathdigital/gameplay/{PhaseMenuSuite,OathDriverSuite}.scala` | modify | 2, 3 |
| `src/test/scala/oathdigital/gameplay/ActMenuBudget.scala` | create | 2 |
| `src/test/scala/oathdigital/model/ProcedureRefSuite.scala` | modify | 2 |
| `src/test/scala/oathdigital/application/PhaseMenuServiceSuite.scala` | modify | 3 |
| `frontend/src/test/scala/oathdigital/frontend/MenuPanelSuite.scala` | create | 4 |
| other test suites (listed per task) | modify | 2-5 |
| `docs/ROADMAP.md` | modify | 6 |

---

### Task 1: The exact start

A start is searched through its modifier decision and priced. Nothing shows prices yet; Task 2's menu does.

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md`
- Modify: `src/main/scala/oathdigital/model/GameViolation.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala`, `PricedOptions.scala`, `QueuedStart.scala`
- Create tests: `src/test/scala/oathdigital/gameplay/walker/StartSearchSuite.scala`, `src/test/scala/oathdigital/gameplay/QueuedStartSuite.scala`

**Interfaces:**
- Produces, in `oathdigital.model.OathViolation`: `final case class StartUnavailable(reason: String)`.
- Produces, in `oathdigital.gameplay.walker.WalkerSimulation`:

  ```scala
  final case class Started(at: Option[(ReadyGame, PendingTree)],
      operations: Vector[CoreOperation])
  def started(tree: Operation, state: ReadyGame, powers: WalkerPowers)
      : Either[OathViolation, Started]
  ```

- Produces, in `oathdigital.gameplay.walker.QueuedStart`:

  ```scala
  final case class StartCost(supply: Int, favor: Int,
      moreSupply: Boolean = false, moreFavor: Boolean = false):
    def words: Option[String]
  def priced(catalog: ExecutableCatalog, ready: ReadyGame,
      entry: QueuedProcedure, powers: WalkerPowers,
      phasePowers: PhasePowers): Either[OathViolation, StartCost]
  def needs(catalog: ExecutableCatalog, ready: ReadyGame,
      entry: QueuedProcedure, powers: WalkerPowers,
      phasePowers: PhasePowers): Option[String]
  def refusal(...)  // unchanged signature: priced(...).left.toOption
  ```

- [ ] **Step 1: Set up and record the baseline**

Create the worktree as the Global Constraints say. Run `./sbtw "test" "frontend/test"` and record both test counts.

- [ ] **Step 2: Write the plan-time rulings into the spec**

In the spec, add a section `## Plan-time rulings (slice 4)` after "Plan-time rulings (slice 3)". Copy rulings 1 to 11 above into it as a numbered list. Keep their bold leads and their wording; drop the code formatting of file names that are not code identifiers. Wrap lines at 76 characters, as the spec does.

- [ ] **Step 3: Write the failing search tests**

Create `src/test/scala/oathdigital/gameplay/walker/StartSearchSuite.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.Costs
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  ModifierPicks, Transform}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** The dry run that walks a start past its modifier decision (walker-driven
  * game loop design, ruling 8; slice 4, plan-time ruling 1), over a
  * hand-built tree walked with stand-in modifiers. */
class StartSearchSuite extends munit.FunSuite:
  private val window = PowerWindow.SearchModifierSelection
  private val alpha = CatalogNames.denizen("Wrestlers")
  private val beta = CatalogNames.denizen("Mercenaries")
  private val gamma = CatalogNames.denizen("Errand Boy")
  private val go = DecisionOptionRef.Button("go")

  /** A stand-in modifier on `card`, pickable at the Search window, that
    * waives every node at `waives`. */
  private final case class Stand(name: String, card: DenizenId,
      waives: Option[PowerWindow] = None, cost: Cost = Cost.free)
      extends ContributingPower:
    val id: PowerId = PowerId(s"stand.$name")
    val source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      waives.map(at => at -> Vector[Contribution](
        Transform((_, _) => Vector.empty))).toMap
    override def resolution: PowerResolution = PowerResolution.PlayerSelected
    override def selectionCard: Option[CardId] = Some(card)
    override def selectionWindows: Set[PowerWindow] = Set(window)
    override def selectionPayments(ready: ReadyGame, actor: PlayerId)
        : Vector[CoreOperation] =
      if cost == Cost.free then Vector.empty
      else Vector(Costs.onCard(actor, card, cost, catalog))

  /** One Supply spent at a node windowed `at`. */
  private def spending(at: PowerWindow): Operation =
    BuildOps((_, _) => Right(Vector(SpendSupply(p1, 1))), window = Some(at),
      required = true)

  /** Asks "Go", then spends one Supply at the Search cost window and one at
    * the Travel cost window. */
  private val rest: Operation = Sequence(Vector(
    Decide("stand.go", p1, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(go, "Go")))),
    spending(PowerWindow.SearchCost), spending(PowerWindow.TravelCost)))

  private val tree: Operation =
    Sequence(Vector(ModifierChoice.loop(p1, window, rest, "Start"), rest))

  private def powers(stands: Stand*): WalkerPowers =
    WalkerPowers.selected(WalkerPowers(stands.toVector), Vector.empty)

  /** p1 at Ancient City with `supply` Supply and three favor, holding
    * alpha, beta and gamma faceup. */
  private def table(supply: Int): ReadyGame =
    Table.start.pawn(p1, "Ancient City").favor(p1, 3).supply(p1, supply)
      .adviser(p1, alpha).adviser(p1, beta).adviser(p1, gamma).ready

  /** The decision the walk stands at once it is past the modifiers, and the
    * modifiers picked on the way. */
  private def reached(found: WalkerSimulation.Started)(using munit.Location)
      : (String, Vector[CardId]) = found.at match
    case Some((state, pending)) =>
      val decide = ProcedureWalker.parkedDecide(state, tree, pending,
        WalkerPowers.empty, probing = false)
      (decide.fold("none")(_.decisionId), ModifierPicks.cards(pending.answered))
    case None => fail("the walk finished")

  private def started(supply: Int, stands: Stand*) =
    WalkerSimulation.started(tree, table(supply), powers(stands*))

  test("with nothing to pick, the walk is past the modifiers at once"):
    val found = started(2)
    assertEquals(found.map(reached), Right(("stand.go", Vector.empty)))

  test("Start with no pick comes first"):
    val found = started(2, Stand("a", alpha,
      waives = Some(PowerWindow.SearchCost)))
    assertEquals(found.map(reached), Right(("stand.go", Vector.empty)))

  test("a start that needs two of three modifiers is found"):
    val found = started(0,
      Stand("c", gamma),
      Stand("a", alpha, waives = Some(PowerWindow.SearchCost)),
      Stand("b", beta, waives = Some(PowerWindow.TravelCost)))
    // The search may pick gamma on the way too; alpha and beta must be in.
    assertEquals(found.map(reached).map(_._1), Right("stand.go"))
    assertEquals(found.map(reached).map(at => Set[CardId](alpha, beta)
      .subsetOf(at._2.toSet)), Right(true))

  test("with no way through, the refusal is the reason Start shows"):
    val found = started(0, Stand("c", gamma),
      Stand("a", alpha, waives = Some(PowerWindow.SearchCost)))
    assertEquals(found.left.toOption, Some(OathViolation.StartUnavailable(
      ModifierChoice.cannotStart)))

  test("a walk every pick took past the modifiers needs an answer"):
    // Picking gamma, the only modifier, starts the walk by itself, and "Go"
    // then spends Supply p1 does not have.
    val found = started(0, Stand("c", gamma))
    assertEquals(found.left.toOption, Some(OathViolation.StartUnavailable(
      ModifierChoice.cannotStart)))

  test("what the walk spends before its first decision is reported"):
    val paying = Sequence(Vector(spending(PowerWindow.SearchCost),
      Decide("stand.go", p1, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(go, "Go"))))))
    val found = WalkerSimulation.started(paying, table(2), powers())
    assertEquals(found.map(_.operations), Right(Vector[CoreOperation](
      SpendSupply(p1, 1))))
```

Create `src/test/scala/oathdigital/gameplay/QueuedStartSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.QueuedStart
import oathdigital.gameplay.walker.QueuedStart.StartCost
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

/** What a start costs, or why it would be refused, through the production
  * registry (walker-driven game loop design, slice 4, plan-time rulings 2
  * and 3). */
class QueuedStartSuite extends munit.FunSuite:
  private val walkerPowers = WalkerPowerCatalog.default(catalog)
  private val phasePowers = PhasePowerCatalog.default(catalog)
  private val ancientCity = CatalogNames.site("Ancient City")

  /** p1's Act at Ancient City, with `supply` Supply, and Brass Horse there
    * when `relic` says so. */
  private def acting(supply: Int, relic: Boolean = true): ReadyGame =
    val seated = Table.start.pawn(p1, at = ancientCity)
      .pawn(p2, at = "Broken Peaks").pawn(p3, at = "Buried Giant")
      .supply(p1, supply)
    (if relic then seated.relicAt("Brass Horse", at = ancientCity)
      else seated).ready

  private def entry(procedure: StartableRef): QueuedProcedure =
    QueuedProcedure(p1, procedure, Vector.empty, None, QueueKind.Run)

  private def priced(procedure: StartableRef, supply: Int = 7) =
    QueuedStart.priced(catalog, acting(supply), entry(procedure),
      walkerPowers, phasePowers)

  test("Search costs its one source's Supply"):
    // Every discard pile is empty, so the world deck is the only source.
    assertEquals(priced(ActionRef.Search), Right(StartCost(2, 0)))
    assertEquals(priced(ActionRef.Search).map(_.words), Right(Some(
      "2 Supply")))

  test("Recover is priced from what its first walk spends"):
    assertEquals(priced(ActionRef.Recover).map(_.words), Right(Some(
      "1 Supply")))

  test("Travel's cost depends on the destination"):
    val words = priced(ActionRef.Travel).toOption.flatMap(_.words)
    assert(words.exists(_.matches("\\d+\\+ Supply")), words)

  test("a free start has no words"):
    assertEquals(priced(ActionRef.PeekSiteRelics).map(_.words), Right(None))

  test("a Supply shortfall is worded with the amounts"):
    assert(priced(ActionRef.Search, supply = 1).isLeft)
    assertEquals(QueuedStart.needs(catalog, acting(1), entry(
      ActionRef.Search), walkerPowers, phasePowers),
      Some("Needs 2 Supply; you have 1"))

  test("a refusal not about Supply has no shortfall"):
    val bare = acting(7, relic = false)
    // The precondition: nothing lies at Ancient City.
    assertEquals(Look(bare).relicsAt(ancientCity), Vector.empty)
    assertEquals(QueuedStart.needs(catalog, bare, entry(
      ActionRef.PeekSiteRelics), walkerPowers, phasePowers), None)
```

`Look` is the testkit reader `MinorActionsSuite` uses for the same precondition.

- [ ] **Step 4: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.StartSearchSuite oathdigital.gameplay.QueuedStartSuite"`
Expected: compile failure: `started`, `StartUnavailable`, `priced`, `needs` and `StartCost` do not exist.

- [ ] **Step 5: The violation**

In `model/GameViolation.scala`, after `NoPlayableOption`, add:

```scala
  /** No way through an action's modifier decision starts it. `reason` is
    * the one its "Start" option shows before any pick (walker-driven game
    * loop design, slice 4, plan-time ruling 1). */
  final case class StartUnavailable(reason: String) extends OathViolation
```

If a match over `OathViolation` without a catch-all fails to compile, add an arm beside `NoPlayableOption`'s that treats it the same way.

- [ ] **Step 6: The search**

In `gameplay/walker/PricedOptions.scala`, change `answerable`, `paths`, `spent` and `lift` from `private` to `private[walker]`. Change nothing else there.

In `gameplay/walker/WalkerSimulation.scala`:

- Add `ModifierPicks` to the imports from `oathdigital.gameplay.powerresolver`, add `scala.collection.mutable`, and add `OptionReason` and `DecisionOptionRef` to the model import if they are missing.
- Change `after`'s second parameter to `from: Option[PendingTree]` and its first line to:

  ```scala
        .foldLeft[Either[OathViolation, OathState]](Right(OathState.Ready(
          state.updateCurrent(_.copy(walkerPending = from))))) {
  ```

  Its doc's last sentence becomes: "The steps start from the position the answer was given at, `None` for a start's first walk, which a start's preview has not recorded yet."
- In `answer`, the call becomes `after(state, Some(pending), next, events)`.
- After `refusal`, add:

```scala
  /** Where a start's walk is once it is past its modifier decision: the
    * state and the position it parked at, `None` once it has finished, and
    * the operations it recorded on the way. */
  final case class Started(at: Option[(ReadyGame, PendingTree)],
      operations: Vector[CoreOperation])

  /** The first way a freshly built `tree` gets past its modifier decision
    * (walker-driven game loop design, ruling 8): the restriction check and
    * the first walk a start runs, then, at the modifier decision, "Start"
    * when it is available, or else each available pick in turn, depth
    * first, never the same picks twice. A walk the picks took past the
    * decision without "Start" counts only when its next decision has an
    * answer the walk can go on from. `Left` is the start's own refusal, or
    * `StartUnavailable` with the reason "Start" shows before any pick.
    * Nothing is persisted. */
  def started(tree: Operation, state: ReadyGame, powers: WalkerPowers)
      : Either[OathViolation, Started] =
    val tried = mutable.Set.empty[Set[DecisionOptionRef]]
    guarded:
      ProcedureWalker.restrictionViolations(tree, powers, state,
        state.game.current.turn.activePlayer)
        .headOption.toLeft(())
        .flatMap(_ => ProcedureWalker.advance(state, tree, None, powers,
          WalkerDice.placeholder))
        .flatMap(reached(state, tree, None, Vector.empty, powers,
          picked = false, tried))

  /** Where `outcome` leaves the walk, given the position `from` it was
    * walked from and the operations recorded `before` it. */
  private def reached(state: ReadyGame, tree: Operation,
      from: Option[PendingTree], before: Vector[CoreOperation],
      powers: WalkerPowers, picked: Boolean,
      tried: mutable.Set[Set[DecisionOptionRef]])(outcome: WalkerOutcome)
      : Either[OathViolation, Started] = outcome match
    case WalkerOutcome.Finished(_, events) =>
      Right(Started(None, before ++ recordedOperations(events)))
    case WalkerOutcome.Parked(next, events) =>
      val operations = before ++ recordedOperations(events)
      after(state, from, next, events).flatMap { here =>
        ProcedureWalker.parkedDecide(here, tree, next, powers) match
          case Some(decide) if ModifierPicks.isDecision(decide.decisionId) =>
            through(here, tree, next, decide, operations, powers, tried)
          case _ if picked && !opens(here, tree, next, powers) =>
            Left(OathViolation.InvalidEventOrder(
              "no answer goes on past the modifiers"))
          case _ => Right(Started(Some(here -> next), operations))
      }

  /** "Start" when it is available, else each available pick not yet tried
    * with the same picks. */
  private def through(state: ReadyGame, tree: Operation,
      pending: PendingTree, decide: Decide, before: Vector[CoreOperation],
      powers: WalkerPowers, tried: mutable.Set[Set[DecisionOptionRef]])
      : Either[OathViolation, Started] =
    val options = PricedOptions.options(decide)
    val start = options.find(_.ref == ModifierPicks.start)
    val chosen = picks(pending)
    val ways = start.filter(_.available).toVector ++ options.filter(option =>
      option.available && option.ref != ModifierPicks.start &&
        tried.add(chosen + option.ref))
    def answering(option: DecisionOption) =
      ProcedureWalker.resolve(state, tree, pending, Answered(
          decide.decisionId, DecisionAnswer.ChooseOneAnswer(option.ref),
          decide.owner), powers, WalkerDice.placeholder)
        .flatMap(reached(state, tree, Some(pending), before, powers,
          picked = option.ref != ModifierPicks.start, tried))
    ways.iterator.map(answering).collectFirst { case Right(found) => found }
      .toRight(OathViolation.StartUnavailable(start.collect {
        case DecisionOption.Unavailable(_, OptionReason.Written(text)) =>
          text
      }.getOrElse(ModifierChoice.cannotStart)))

  /** The modifiers picked so far, as their options. */
  private def picks(pending: PendingTree): Set[DecisionOptionRef] =
    pending.answered.collect {
      case Answered(id, DecisionAnswer.ChooseOneAnswer(ref), _)
          if ModifierPicks.isDecision(id) && ref != ModifierPicks.start => ref
    }.toSet

  /** Whether the walk can go on from where it parked: a choose-one with an
    * answer that can be completed. Any other park is taken to go on. */
  private def opens(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers): Boolean =
    previewParked(state, tree, pending, powers) match
      case Right(options) => options.exists(_.outcome.isRight)
      case Left(_) => true
```

`guarded` takes a by-name block; if the compiler rejects the `val` above it inside the method, move `tried` into the block.

- [ ] **Step 7: The price**

Replace the body of `QueuedStart` (keep the package line and imports; add what is missing) with:

```scala
/** Whether a queued entry would start now, and what it would cost
  * (walker-driven game loop design, ruling 8; slice 4, plan-time rulings 1
  * to 3): the checks the driver's start of it runs, on the state that start
  * would see, then the search through its modifier decision, with nothing
  * persisted. An option shown available is a start the driver accepts.
  *
  * It builds through the production registry, so a suite that injects its
  * own tree source into `OathRules` does not reach it. It does not check that
  * the entry's actor is the active player, which the start does: until an
  * off-turn actor exists (Sneak Attack), every entry's actor is the active
  * player.
  */
object QueuedStart:
  /** What a start costs its actor by its cheapest way, and whether another
    * way costs more Supply or more favor. */
  final case class StartCost(supply: Int, favor: Int,
      moreSupply: Boolean = false, moreFavor: Boolean = false):
    /** "1 Supply", "2 Supply · 1 favor", "1+ Supply", "2+ Supply · 1+
      * favor": a "+" marks an amount another way spends more of. `None`
      * when the start is free. */
    def words: Option[String] =
      def part(amount: Int, more: Boolean, unit: String) =
        Option.when(amount > 0)(s"$amount${if more then "+" else ""} $unit")
      Option.when(supply > 0 || favor > 0)(Vector(
        part(supply, moreSupply, "Supply"), part(favor, moreFavor, "favor"))
        .flatten.mkString(" · "))

  def refusal(catalog: ExecutableCatalog, ready: ReadyGame,
      entry: QueuedProcedure, powers: WalkerPowers,
      phasePowers: PhasePowers): Option[OathViolation] =
    priced(catalog, ready, entry, powers, phasePowers).left.toOption

  /** What the entry's start would cost now, or why it would be refused. */
  def priced(catalog: ExecutableCatalog, ready: ReadyGame,
      entry: QueuedProcedure, powers: WalkerPowers,
      phasePowers: PhasePowers): Either[OathViolation, StartCost] =
    val state = startingFrom(ready, entry)
    for
      tree <- WalkerProcedureRegistry.build(entry.procedure, catalog, state,
        entry.actor, entry.startArgs, phasePowers)
      past <- WalkerSimulation.started(tree, state, powers)
      cost <- costOf(entry, tree, past, powers)
    yield cost

  /** "Needs 2 Supply; you have 1", or the same for favor, when the start
    * would open with the actor's Supply raised to the track's maximum, then
    * also their favor raised: a refusal carries no amount. */
  def needs(catalog: ExecutableCatalog, ready: ReadyGame,
      entry: QueuedProcedure, powers: WalkerPowers,
      phasePowers: PhasePowers): Option[String] =
    def lifted(favor: Boolean) = priced(catalog,
      PricedOptions.lift(ready, entry.actor, favor), entry, powers,
      phasePowers).toOption
    for
      board <- ready.game.current.players.find(_.player == entry.actor)
        .map(_.board)
      cost <- lifted(favor = false).orElse(lifted(favor = true))
      text <-
        if cost.supply > board.supply.supply then Some(
          s"Needs ${cost.supply} Supply; you have ${board.supply.supply}")
        else if cost.favor > board.favor then Some(
          s"Needs ${cost.favor} favor; you have ${board.favor}")
        else None
    yield text

  /** The state the entry's start runs on: no walk open, and the entry
    * dequeued, so the cost waiver and the phase exemption see it. */
  def startingFrom(ready: ReadyGame, entry: QueuedProcedure): ReadyGame =
    ready.updateCurrent(_.copy(walkerPending = None, walkerProcedure = None,
      walkerStartArgs = Vector.empty, walkerStartFacts = Vector.empty,
      walkerEntry = Some(entry)))

  /** What the walk spent before its first decision past the modifiers,
    * plus, for a procedure that previews that decision, its cheapest
    * answer. Such a procedure with no answer that can be completed is
    * refused, as the playable-option gate refuses it. */
  private def costOf(entry: QueuedProcedure, tree: Operation,
      past: WalkerSimulation.Started, powers: WalkerPowers)
      : Either[OathViolation, StartCost] =
    val actor = entry.actor
    val (supply, favor) = PricedOptions.spent(past.operations, actor)
    past.at match
      case Some((state, pending))
          if WalkerProcedureRegistry.requiresPlayableOption(
            entry.procedure) =>
        WalkerSimulation.previewParked(state, tree, pending, powers,
            followUp = true).flatMap { options =>
          val ways = options.collect {
            case WalkerSimulation.PreviewedOption(_, Right(outcome))
                if PricedOptions.answerable(outcome) => outcome
          }.flatMap(PricedOptions.paths).map(PricedOptions.spent(_, actor))
            .map((more, extra) => (supply + more, favor + extra)).distinct
          if ways.isEmpty then
            Left(OathViolation.NoPlayableOption(entry.procedure.key))
          else
            val (least, leastFavor) = ways.min
            Right(StartCost(least, leastFavor,
              moreSupply = ways.exists(_._1 > least),
              moreFavor = ways.exists(_._2 > leastFavor)))
        }
      case _ => Right(StartCost(supply, favor))
```

Delete the old private `unplayable`. Remove the imports it leaves unused (`ProcedureWalker`, `WalkerOutcome` and `WalkerDice` if nothing else uses them).

If `PricedOptions.lift` has another signature than `(ReadyGame, PlayerId, Boolean): ReadyGame`, call it as it is declared.

- [ ] **Step 8: Run the new tests**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.StartSearchSuite oathdigital.gameplay.QueuedStartSuite"`
Expected: PASS.

If "Search costs its one source's Supply" fails because a discard pile on the table is not empty, read the table: when another source is answerable at 2 Supply, the cost does not vary. Correct only the test's setup, not the code, and report it.

- [ ] **Step 9: Run everything**

Run: `./sbtw "test"`
Expected: PASS. The follow-up offer and the Wake and Rest menus now use the exact check. If a test of theirs changes because an option a modifier could not open is now unavailable, update it to the new reason and list it in your report. Any other failure: stop and report.

- [ ] **Step 10: Commit**

```bash
git add docs/superpowers/specs/2026-10-09-walker-game-loop-design.md \
  src/main/scala/oathdigital/model/GameViolation.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala \
  src/main/scala/oathdigital/gameplay/walker/PricedOptions.scala \
  src/main/scala/oathdigital/gameplay/walker/QueuedStart.scala \
  src/test/scala/oathdigital/gameplay/walker/StartSearchSuite.scala \
  src/test/scala/oathdigital/gameplay/QueuedStartSuite.scala
git status --short
git commit -m "feat(walker): a start is searched through its modifier decision and priced"
```

Add any test you updated in Step 9 to the `git add` list.

---

### Task 2: The Act menu

The Act menu exists, lists every option with its price or reason, and starts what is picked. The driver does not park it yet: a one-line guard holds it back until Task 3.

**Files:**
- Modify: `src/main/scala/oathdigital/model/ProcedureRef.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/economy/TradeProcedure.scala`
- Create: `src/main/scala/oathdigital/gameplay/phases/ActOffers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/phases/PhaseMenu.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala`, `OathDriver.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`, `application/gamelog/ActionLines.scala`
- Modify tests: `src/test/scala/oathdigital/testkit/{MenuPlay,Situation,SituationDriver}.scala`, `src/test/scala/oathdigital/gameplay/PhaseMenuSuite.scala`, `src/test/scala/oathdigital/model/ProcedureRefSuite.scala`
- Create test: `src/test/scala/oathdigital/gameplay/ActMenuBudget.scala`

**Interfaces:**
- Consumes: Task 1's `QueuedStart.priced`, `needs`, `StartCost.words` and `OathViolation.StartUnavailable`.
- Produces:
  - `TriggeredProcedureRef.ActMenu` (key `"act-menu"`), appended to `TriggeredProcedureRef.all`;
  - `TradeProcedure.selection(resource: TradeResource): Vector[DecisionOptionRef]`;
  - `ActOffers.ActOffer(procedure: ActionRef, args: Vector[DecisionOptionRef], ref: DecisionOptionRef.Button, label: String)`, `ActOffers.all: Vector[ActOffer]` and `ActOffers.reason(procedure: StartableRef, violation: OathViolation): String`;
  - `PhaseMenu.Menu.Act`, with `endingOption` `Button("end-act")`;
  - `PhaseMenu.optionFor(procedure: StartableRef, args: Vector[DecisionOptionRef]): Option[DecisionOptionRef]`;
  - in tests: `Step.Start(player: PlayerId, procedure: StartableRef, args: Vector[DecisionOptionRef] = Vector.empty)`, `MenuPlay.start(player, procedure, args): Step`, `MenuPlay.starting(player, procedure, args): GameCommand` and `MenuPlay.started(rules, state, player, procedure, args): Either[OathViolation, OathTransition]`.

- [ ] **Step 1: The test helpers**

In `testkit/Situation.scala`, add a case to `enum Step`, after `Arrange`:

```scala
  /** Starts `procedure` as `player`: picks its option from the parked phase
    * menu, or, on a table where no menu is parked, starts it through the
    * rules (walker-driven game loop design, slice 4, plan-time ruling 11).
    */
  case Start(player: PlayerId, procedure: StartableRef,
      args: Vector[DecisionOptionRef] = Vector.empty)
```

In `testkit/SituationDriver.scala`, add an arm to `Rules.apply`, after `Step.Arrange`'s:

```scala
      case Step.Start(player, procedure, args) =>
        if MenuPlay.parked(from.state).nonEmpty then
          picking(player, procedure, args).flatMap(command =>
            apply(from, Step.Command(command)))
        else rules.startWalker(from.state, procedure, player, args)
          .left.map(_.toString).map(result => advanced(from, result.state,
            result.events, Vector(OathEvent.CommandAccepted(player))))
```

and to `Journaled.apply`, after `Step.Arrange`'s:

```scala
      case Step.Start(player, procedure, args) =>
        if MenuPlay.parked(from.state).isEmpty then Left("no phase menu " +
          "is parked: park one with Table.phaseMenuParked to start " +
          procedure.key)
        else picking(player, procedure, args).flatMap(command =>
          apply(from, Step.Command(command)))
```

and to `object SituationDriver`, after `opening`:

```scala
  /** The command that picks `procedure`'s option from the parked menu. */
  private def picking(player: PlayerId, procedure: StartableRef,
      args: Vector[DecisionOptionRef]): Either[String, GameCommand] =
    PhaseMenu.optionFor(procedure, args)
      .toRight(s"no menu lists ${procedure.key}")
      .map(MenuPlay.picking(player, _))
```

If `Journaled.apply` cannot call itself because it is not recursive-friendly, inline the `Step.Command` arm's body for the picked command.

In `testkit/MenuPlay.scala`, add:

```scala
  /** The step that starts `procedure` as `player`: see `Step.Start`. */
  def start(player: PlayerId, procedure: StartableRef,
      args: Vector[DecisionOptionRef] = Vector.empty): Step =
    Step.Start(player, procedure, args)

  /** The command that picks `procedure`'s option from the parked menu. */
  def starting(player: PlayerId, procedure: StartableRef,
      args: Vector[DecisionOptionRef] = Vector.empty)(
      using munit.Location): GameCommand =
    picking(player, optionOf(procedure, args))

  /** Starts `procedure` through the rules: its pick when a menu is parked,
    * a start on a table where none is. */
  def started(rules: OathRules, state: OathState, player: PlayerId,
      procedure: StartableRef, args: Vector[DecisionOptionRef] = Vector.empty)(
      using munit.Location): Either[OathViolation, OathTransition] =
    if parked(state).nonEmpty then
      picked(rules, state, player, optionOf(procedure, args))
    else rules.startWalker(state, procedure, player, args)

  private def optionOf(procedure: StartableRef,
      args: Vector[DecisionOptionRef])(using munit.Location)
      : DecisionOptionRef =
    PhaseMenu.optionFor(procedure, args).getOrElse(munit.Assertions.fail(
      s"no menu lists ${procedure.key}"))
```

- [ ] **Step 2: Write the failing menu tests**

In `gameplay/PhaseMenuSuite.scala`, add `oathdigital.gameplay.phases.ActOffers` to the `phases` import, `oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Transform}` and `oathdigital.gameplay.walker.WalkerPowers`, and then add:

```scala
  /** p1's Act at Ancient City, with `supply` Supply. */
  private def acting(supply: Int = 7): Table = table.supply(p1, supply)

  private def act(procedure: ActionRef): DecisionOptionRef =
    PhaseMenu.optionFor(procedure, Vector.empty)
      .getOrElse(fail(s"no menu lists ${procedure.key}"))

  private def shown(state: OathState, ref: DecisionOptionRef)
      : DecisionOption = options(state).find(_.ref == ref)
    .getOrElse(fail(s"the menu does not list $ref"))

  test("the Act menu lists the actions, the minor actions, then End Act " +
      "and Rest"):
    // Any Act power's source is listed between them; this table's buttons
    // are the menu's own.
    assertEquals(options(acting().phaseMenuParked.state).map(_.ref)
      .filter(_.isInstanceOf[DecisionOptionRef.Button]),
      ActOffers.all.map(_.ref) :+ PhaseMenu.Menu.Act.endingOption)
    assertEquals(ActOffers.all.map(_.ref.value), Vector("search", "travel",
      "campaign", "muster", "trade.favor", "trade.secret", "forge",
      "recover", "challenge", "place-banner-resource", "negotiation",
      "play-facedown-adviser", "peek-site-relics", "reveal-owned-relic",
      "move-warbands"))

  test("an Act option carries its start's price"):
    val state = acting().phaseMenuParked.state
    assertEquals(shown(state, act(ActionRef.Search)), DecisionOption.Badged(
      DecisionOption.Button(DecisionOptionRef.Button("search"), "Search"),
      "2 Supply"))
    shown(state, act(ActionRef.Travel)) match
      case DecisionOption.Badged(_, badge) =>
        assert(badge.matches("\\d+\\+ Supply"), badge)
      case other => fail(s"Travel is not priced: $other")

  test("an Act option that cannot start says why"):
    val parked = acting(supply = 1).phaseMenuParked
    // The preconditions: no relic at Ancient City, and none held.
    assertEquals(Look(parked.ready).relicsAt(ancientCity), Vector.empty)
    assertEquals(Look(parked.ready).player(p1).relics, Vector.empty)
    val state = parked.state
    assertEquals(shown(state, act(ActionRef.Search)),
      DecisionOption.Unavailable(DecisionOption.Button(
        DecisionOptionRef.Button("search"), "Search"),
        OptionReason.Written("Needs 2 Supply; you have 1")))
    assertEquals(shown(state, act(ActionRef.PeekSiteRelics)),
      DecisionOption.Unavailable(DecisionOption.Button(
        DecisionOptionRef.Button("peek-site-relics"),
        "Peek at relics at your site"),
        OptionReason.Written("No relics at your site")))
    assertEquals(shown(state, act(ActionRef.RevealOwnedRelic)),
      DecisionOption.Unavailable(DecisionOption.Button(
        DecisionOptionRef.Button("reveal-owned-relic"), "Reveal a relic"),
        OptionReason.Written("You have no facedown relic")))

  test("an Act option only a modifier can open is available"):
    // p1 has no Supply; a stand-in modifier on Wrestlers waives Search's
    // cost.
    val wrestlers = CatalogNames.denizen("Wrestlers")
    val waiver = new ContributingPower:
      val id: PowerId = PowerId("stand.waiver")
      val source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
      def contributions: Map[PowerWindow, Vector[Contribution]] =
        Map(PowerWindow.SearchCost -> Vector(
          Transform((_, _) => Vector.empty)))
      override def resolution: PowerResolution =
        PowerResolution.PlayerSelected
      override def selectionCard: Option[CardId] = Some(wrestlers)
      override def selectionWindows: Set[PowerWindow] =
        Set(PowerWindow.SearchModifierSelection)
    val ready = acting(supply = 0).adviser(p1, wrestlers).ready
    val search = PhaseMenu.offers(PhaseMenu.Menu.Act, catalog, ready, p1,
      phasePowers, WalkerPowers.selected(WalkerPowers(Vector(waiver)),
        Vector.empty)).find(_.option.ref == act(ActionRef.Search))
    assert(search.exists(_.option.available), search)

  test("End Act and Rest names the Supply Rest returns"):
    val ending = shown(acting().phaseMenuParked.state,
      PhaseMenu.Menu.Act.endingOption)
    ending match
      case DecisionOption.Button(_, label) =>
        assert(label == "End Act and Rest" ||
          label.matches("End Act and Rest \\(\\+\\d+ Supply\\)"), label)
      case other => fail(s"End Act and Rest is not a plain button: $other")

  test("the Act menu always asks"):
    val ending = PhaseMenu.Offered(QueuedProcedure(p1,
      PhaseTransitionRef.BeginRest, Vector.empty, None, QueueKind.Run),
      DecisionOption.Button(PhaseMenu.Menu.Act.endingOption,
        "End Act and Rest"))
    assert(!PhaseMenu.onlyEnding(PhaseMenu.Menu.Act, Vector(ending)))

  test("an Act pick queues its action, which starts in the same command"):
    val start = acting().phaseMenuParked.state
    val picked = accepted(MenuPlay.picked(rules, start, p1,
      act(ActionRef.Search)))
    val entry = QueuedProcedure(p1, ActionRef.Search, Vector.empty, None,
      QueueKind.Run)
    val completed = picked.events.indexOf(
      WalkerCompleted(TriggeredProcedureRef.ActMenu))
    assert(completed >= 0, picked.events)
    assertEquals(picked.events.lift(completed + 1),
      Some(ProcedureDequeued(entry): OathEvent))
    assertEquals(Outcomes.ready(picked.state).game.current.walkerProcedure,
      Some(ActionRef.Search))
    assertEquals(replayed(start, picked.events), picked.state)

  test("picking End Act and Rest begins the Rest"):
    val rested = accepted(MenuPlay.picked(rules,
      acting().phaseMenuParked.state, p1, PhaseMenu.Menu.Act.endingOption))
    assert(rested.events.contains(WalkerCompleted(
      PhaseTransitionRef.BeginRest)), rested.events)
```

Add `Look` and `Outcomes` to the testkit import. `table` is the suite's existing p1-at-Ancient-City table; `Table.start` is p1's Act. If a precondition fails, the table differs from what this plan read: stop and report.

In `model/ProcedureRefSuite.scala`, in "procedure reference keys are listed per family and unique across all of them", append `TriggeredProcedureRef.ActMenu` to the expected `TriggeredProcedureRef.all`, after `RestMenu`, and add a line:

```scala
    assertEquals(TriggeredProcedureRef.ActMenu.key, "act-menu")
```

- [ ] **Step 3: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.PhaseMenuSuite oathdigital.model.ProcedureRefSuite"`
Expected: compile failure: `ActOffers`, `Menu.Act`, `optionFor` and `ActMenu` do not exist.

- [ ] **Step 4: The procedure reference**

In `model/ProcedureRef.scala`, after `RestMenu`, add:

```scala
  /** The Act phase's menu, as the Wake menu is Wake's. The driver starts it
    * whenever the queue is empty in Act; it always asks. */
  case ActMenu extends TriggeredProcedureRef("act-menu")
```

and append `ActMenu` to `TriggeredProcedureRef.all`, after `RestMenu`.

- [ ] **Step 5: Trade's selection**

In `TradeProcedure.scala`, after `resourceOf`, add:

```scala
  /** The start selection that trades for `resource`, as `resourceOf` reads
    * it. */
  def selection(resource: TradeResource): Vector[DecisionOptionRef] =
    resource match
      case TradeResource.Favor => Vector(DecisionOptionRef.Button("favor"))
      case TradeResource.Secret => Vector(DecisionOptionRef.Button("secret"))
```

- [ ] **Step 6: The Act offers**

Create `src/main/scala/oathdigital/gameplay/phases/ActOffers.scala`:

```scala
package oathdigital.gameplay.phases

import oathdigital.gameplay.actions.campaign.CampaignProcedure
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.model._

/** The Act menu's own options (walker-driven game loop design, slice 4,
  * plan-time ruling 4): the major actions, then the minor actions, each a
  * button keyed by its procedure. Phase powers and End Act and Rest are
  * added by `PhaseMenu`. */
object ActOffers:
  /** One option: the procedure its pick queues, with the start selection it
    * carries, its button and its label. */
  final case class ActOffer(procedure: ActionRef,
      args: Vector[DecisionOptionRef], ref: DecisionOptionRef.Button,
      label: String)

  private def plain(procedure: ActionRef, label: String): ActOffer =
    ActOffer(procedure, Vector.empty, DecisionOptionRef.Button(procedure.key),
      label)

  private def trade(resource: TradeResource, key: String, label: String)
      : ActOffer = ActOffer(ActionRef.Trade, TradeProcedure.selection(
    resource), DecisionOptionRef.Button(s"${ActionRef.Trade.key}.$key"),
    label)

  val all: Vector[ActOffer] = Vector(
    plain(ActionRef.Search, "Search"),
    plain(ActionRef.Travel, "Travel"),
    plain(ActionRef.Campaign, "Campaign"),
    plain(ActionRef.Muster, "Muster"),
    trade(TradeResource.Favor, "favor", "Trade for favor"),
    trade(TradeResource.Secret, "secret", "Trade for secrets"),
    plain(ActionRef.Forge, "Forge"),
    plain(ActionRef.Recover, "Recover"),
    plain(ActionRef.Challenge, "Challenge"),
    plain(ActionRef.PlaceBannerResource,
      "Place favor or secrets on a banner"),
    plain(ActionRef.Negotiation, "Negotiate"),
    plain(ActionRef.PlayFacedownAdviser, "Play facedown adviser"),
    plain(ActionRef.PeekSiteRelics, "Peek at relics at your site"),
    plain(ActionRef.RevealOwnedRelic, "Reveal a relic"),
    plain(ActionRef.MoveWarbands, "Move warbands"))

  /** Why an Act action cannot start, in the player's words, when its
    * refusal is not about Supply or favor. */
  def reason(procedure: StartableRef, violation: OathViolation): String =
    (procedure, violation) match
      case (_, OathViolation.StartUnavailable(text)) => text
      case (ActionRef.Campaign, OathViolation.CampaignUnavailable(detail))
          if detail == CampaignProcedure.NothingToAttack =>
        "No site to conquer and no enemy pawn to raid"
      case (ActionRef.Campaign, OathViolation.CampaignUnavailable(detail)) =>
        detail
      case (ActionRef.Negotiation,
          OathViolation.NegotiationUnavailable(detail)) => detail
      case (ActionRef.MoveWarbands,
          OathViolation.MinorActionUnavailable(detail)) => detail
      case (ActionRef.PeekSiteRelics, _) => "No relics at your site"
      case (ActionRef.RevealOwnedRelic, _) => "You have no facedown relic"
      case (ActionRef.PlayFacedownAdviser, _) =>
        "You have no facedown adviser"
      case (ActionRef.Search, _) => "Cannot search now"
      case (ActionRef.Travel, _) => "No site to travel to"
      case (ActionRef.Campaign, _) => "Cannot campaign now"
      case (ActionRef.Muster, _) => "No card to muster from"
      case (ActionRef.Trade, _) => "No card to trade with"
      case (ActionRef.Forge, _) => "Cannot forge here"
      case (ActionRef.Recover, _) => "Cannot recover here"
      case (ActionRef.Challenge, _) => "Cannot challenge now"
      case (ActionRef.PlaceBannerResource, _) => "No banner to place on"
      case (ActionRef.Negotiation, _) => "Cannot negotiate now"
      case _ => "Cannot start now"
```

`CampaignProcedure.NothingToAttack` is the constant `FollowUpOffer.reason` reads; if it lives elsewhere, import it from there.

- [ ] **Step 7: The menu**

In `PhaseMenu.scala`:

- Add the Act case to `enum Menu`, after `Rest`:

  ```scala
    case Act extends Menu(Phase.Act, TriggeredProcedureRef.ActMenu,
      PhaseTransitionRef.BeginRest, DecisionOptionRef.Button("end-act"),
      "End Act and Rest", "Act: take an action, use a power or rest")
  ```

- In the object's doc, "in Wake or Rest" becomes "in its phase". The paragraph on phase powers stays. Add, after it: "Each available option carries what its start costs, and each unavailable one why it cannot start (slice 4, plan-time rulings 2 and 3)."
- `of`'s doc becomes "The menu `phase` asks, when it has one."
- Add, after `takeWealth`:

  ```scala
  /** The option that starts `procedure` with `args` in its phase's menu,
    * when a menu lists one: a phase power's option is its source. */
  def optionFor(procedure: StartableRef, args: Vector[DecisionOptionRef])
      : Option[DecisionOptionRef] = procedure match
    case _: ActionRef.UsePower => args.headOption
    case ActionRef.TakeWealth => WakeResource.all.find(resource =>
      TakeWealthProcedure.selection(resource) == args).map(takeWealth)
    case _ => Menu.values.find(_.ending == procedure).map(_.endingOption)
      .orElse(ActOffers.all.find(offer => offer.procedure == procedure &&
        offer.args == args).map(_.ref))
  ```

- In `offers`, replace the inner `offered` with:

  ```scala
    def offered(procedure: StartableRef, args: Vector[DecisionOptionRef],
        shown: DecisionOption): Offered =
      val entry = QueuedProcedure(actor, procedure, args, None, QueueKind.Run)
      Offered(entry, QueuedStart.priced(catalog, ready, entry, powers,
          phasePowers) match
        case Right(cost) =>
          cost.words.fold(shown)(DecisionOption.Badged(shown, _))
        case Left(violation) => DecisionOption.Unavailable(shown,
          OptionReason.Written(QueuedStart.needs(catalog, ready, entry,
            powers, phasePowers).getOrElse(reason(procedure, violation)))))
  ```

  After `wealth`, add:

  ```scala
    val acting: Vector[Offered] =
      if menu != Menu.Act then Vector.empty
      else ActOffers.all.map(offer => offered(offer.procedure, offer.args,
        DecisionOption.Button(offer.ref, offer.label)))
  ```

  The last expression becomes:

  ```scala
    (wealth ++ acting ++ used).distinctBy(_.option.ref) :+ offered(
      menu.ending, Vector.empty, DecisionOption.Button(menu.endingOption,
        endingLabel(menu, ready, actor)))
  ```

  and its doc: "The menu's options, in order: Take Wealth's two resources in Wake, the Act actions in Act, each phase power's source, then the ending. A source that offers two powers of one phase is listed once, for the first."
- Add, after `offers`:

  ```scala
  /** "End Act and Rest (+5 Supply)": what Rest would return, as the old
    * button said. Any other ending, or a Rest that cannot price it, is
    * its plain label. */
  private def endingLabel(menu: Menu, ready: ReadyGame, actor: PlayerId)
      : String =
    if menu != Menu.Act then menu.endingLabel
    else FinishRestProcedure.supplyGainAtRest(ready, actor).toOption
      .filter(_ > 0)
      .fold(menu.endingLabel)(gain => s"${menu.endingLabel} (+$gain Supply)")
  ```

- `onlyEnding` becomes:

  ```scala
  /** Whether the menu has nothing to ask: its ending is the only option
    * available. Act always asks (ruling 3). */
  def onlyEnding(menu: Menu, offers: Vector[Offered]): Boolean =
    menu != Menu.Act && offers.forall(offered => !offered.option.available ||
      offered.entry.procedure == menu.ending)
  ```

- In `reason`, before `case _ => "Cannot end the phase now"`, add:

  ```scala
    case (action: ActionRef, _) => ActOffers.reason(action, violation)
    case (PhaseTransitionRef.BeginRest, _) => "Cannot rest now"
  ```

  The existing `UsePower` and `TakeWealth` arms come first, so they keep their words.
- Imports: add `oathdigital.gameplay.phases.rest.FinishRestProcedure`.

- [ ] **Step 8: Register it**

- `WalkerProcedureRegistry.lookup`: after the `RestMenu` case, add `case TriggeredProcedureRef.ActMenu => Right(menuEntry(PhaseMenu.Menu.Act, powers, walkerPowers))`. `isRegistered` already reads `PhaseMenu.asking`.
- `OathRulesWalker.buildWalker`: in both arms that list `TriggeredProcedureRef.WakeMenu | TriggeredProcedureRef.RestMenu`, add `| TriggeredProcedureRef.ActMenu`.
- `WalkerDecisionProjector`: likewise in the arm at its `rebuild` dispatch.
- `gamelog/ActionLines.scala`: add `TriggeredProcedureRef.ActMenu` to the arm with `WakeMenu` and `RestMenu`, which writes nothing (spec ruling 12).
- Compile with `./sbtw compile`. For every other non-exhaustive match the compiler names, add `ActMenu` beside `RestMenu`.

- [ ] **Step 9: The driver holds it back**

In `OathDriver.phaseEnd`, add as the first case of `PhaseMenu.of(...) match`:

```scala
        // Task 3 of slice 4 parks the Act menu; until then Act parks
        // nothing.
        case Some(PhaseMenu.Menu.Act) => Right(transition)
```

- [ ] **Step 10: Run the menu tests**

Run: `./sbtw "testOnly oathdigital.gameplay.PhaseMenuSuite oathdigital.model.ProcedureRefSuite"`
Expected: PASS.

If "an Act option carries its start's price" fails on Search's badge because the table's world deck costs more than 2 Supply (Visions Drawn above 0), correct only the expected badge and report it. If "an Act option that cannot start says why" fails on a reason, check the refusal against `ActOffers.reason` and the procedure's own violation. Fix the table in `ActOffers` when the procedure refuses with a different violation than the table expects; do not change the expected text.

- [ ] **Step 11: The budget benchmark**

Create `src/test/scala/oathdigital/gameplay/ActMenuBudget.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture.board
import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerProcedureRegistry}
import oathdigital.model._

/** The Act menu's budget (walker-driven game loop design, ruling 8): on a
  * full board, building and walking the menu to its park, and reading the
  * park back, each take under 50 ms. A benchmark run by hand, never by
  * `sbt test`:
  *
  * {{{./sbtw "Test/runMain oathdigital.gameplay.ActMenuBudget"}}}
  *
  * Each measure is the best of five runs after three warm-up runs. It exits
  * with status 1 when a measure is over budget.
  */
object ActMenuBudget:
  private val budgetMillis = 50L

  private def best(body: => Any): Long =
    (1 to 3).foreach(_ => body)
    (1 to 5).map { _ =>
      val start = System.nanoTime()
      body
      (System.nanoTime() - start) / 1000000L
    }.min

  def main(args: Array[String]): Unit =
    val walkerPowers = WalkerPowerCatalog.default(catalog)
    val phasePowers = PhasePowerCatalog.default(catalog)
    val b = board(extras = 3, warbands = 5, supply = 7)
    val menu = TriggeredProcedureRef.ActMenu
    def tree: Operation = WalkerProcedureRegistry.build(menu, catalog,
        b.ready, b.actor, Vector.empty, phasePowers,
        walkerPowers = walkerPowers)
      .fold(violation => sys.error(s"the Act menu must build: $violation"),
        identity)
    def parked: PendingTree = ProcedureWalker.advance(b.ready, tree, None,
        walkerPowers) match
      case Right(WalkerOutcome.Parked(pending, _)) => pending
      case other => sys.error(s"the Act menu must park: $other")
    val park = best(parked)
    val pending = parked
    val ready = b.ready.updateCurrent(_.copy(walkerPending = Some(pending),
      walkerProcedure = Some(menu)))
    val read = best(ProcedureWalker.openDecisions(ready,
      WalkerProcedureRegistry.rebuild(menu, catalog, ready, b.actor,
        Vector.empty, phasePowers, walkerPowers = walkerPowers)
        .fold(violation => sys.error(s"rebuild: $violation"), identity),
      pending, walkerPowers))
    println(s"Act menu park: $park ms; reading it: $read ms " +
      s"(budget $budgetMillis ms each, ${PhaseMenu.decisionId})")
    if park >= budgetMillis || read >= budgetMillis then sys.exit(1)
```

Run: `./sbtw "Test/runMain oathdigital.gameplay.ActMenuBudget"`
Expected: both measures under 50 ms, exit status 0. Put the two numbers in your report. If either is over budget, stop and report the numbers (plan-time ruling 10).

- [ ] **Step 12: Run everything**

Run: `./sbtw "test"`
Expected: PASS. If a projection or Game Log test lists every triggered procedure and now misses `ActMenu`, add it there and report it.

- [ ] **Step 13: Commit**

```bash
git add src/main/scala/oathdigital/model/ProcedureRef.scala \
  src/main/scala/oathdigital/gameplay/actions/economy/TradeProcedure.scala \
  src/main/scala/oathdigital/gameplay/phases/ActOffers.scala \
  src/main/scala/oathdigital/gameplay/phases/PhaseMenu.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala \
  src/main/scala/oathdigital/gameplay/OathRulesWalker.scala \
  src/main/scala/oathdigital/gameplay/OathDriver.scala \
  src/main/scala/oathdigital/application/WalkerDecisionProjector.scala \
  src/main/scala/oathdigital/application/gamelog/ActionLines.scala \
  src/test/scala/oathdigital/testkit/MenuPlay.scala \
  src/test/scala/oathdigital/testkit/Situation.scala \
  src/test/scala/oathdigital/testkit/SituationDriver.scala \
  src/test/scala/oathdigital/gameplay/PhaseMenuSuite.scala \
  src/test/scala/oathdigital/gameplay/ActMenuBudget.scala \
  src/test/scala/oathdigital/model/ProcedureRefSuite.scala
git status --short
git commit -m "feat(walker): the Act menu"
```

Add every other file you changed for a compiler-named match.

---

### Task 3: The driver parks the Act menu

Act is played at its menu. Tests that started actions with commands pick them from the menu.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/OathDriver.scala`
- Modify tests: `src/test/scala/oathdigital/gameplay/OathDriverSuite.scala`, `src/test/scala/oathdigital/application/PhaseMenuServiceSuite.scala`, the Game Log menu suite, and the suites listed in Step 5

**Interfaces:**
- Consumes: Task 2's `PhaseMenu.Menu.Act`, `MenuPlay.start`, `starting` and `started`.

- [ ] **Step 1: Write the failing tests**

In `OathDriverSuite.scala`, rename "a menu's walk leaves no boundary, and Act parks nothing yet" to "a menu's walk leaves no boundary, and Act parks its menu", and replace its last assertion with:

```scala
    assertEquals(MenuPlay.parked(ended.state), Some(PhaseMenu.Menu.Act))
```

Add:

```scala
  test("an action in Act ends at the Act menu"):
    val traveled = accepted(TravelPlay.travelled(rules, table.state, p1,
      brokenPeaks))
    assertEquals(MenuPlay.parked(traveled.state), Some(PhaseMenu.Menu.Act))
    assertEquals(replayed(table.state, traveled.events), traveled.state)
```

`OathDriverSuite`'s `rules` has no phase power catalog. If the menu's park needs one to build, construct it as `PhaseMenuSuite` does (`PowerRules.withWalkerAndPhasePowers`).

In `PhaseMenuServiceSuite.scala`, add:

```scala
  test("Undo after an Act pick returns to the Act menu"):
    val (service, _) = Table.start.pawn(p1, at = ancientCity)
      .pawn(p2, at = "Broken Peaks").pawn(p3, at = "Buried Giant")
      .phaseMenuParked.service()
    val taken = accepted(service.handle("game", 0L,
      MenuPlay.starting(p1, ActionRef.Search)))
    val undone = accepted(service.handle("game", taken.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(MenuPlay.parked(undone.state), Some(PhaseMenu.Menu.Act))
```

In the Game Log suite that tests the Wake menu's silent pick (slice 2 created `GameLogPhaseMenuSuite`; find it with `grep -rln "a pick writes nothing\|PhaseMenu" src/test/scala/oathdigital/application/gamelog`), add the same test for an Act pick: the lines after picking Search from the Act menu are exactly the lines Search's start writes, with no menu line and no menu section. Follow the Wake test's shape.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.OathDriverSuite"`
Expected: FAIL: Act parks nothing.

- [ ] **Step 3: The driver parks the menu**

In `OathDriver.phaseEnd`, delete the `case Some(PhaseMenu.Menu.Act) => Right(transition)` arm and its comment. In the trait's doc, step 3 becomes: "the phase's menu: the menu, or, in Wake and Rest, the phase's ending when it is the only option available (ruling 3). Act always asks."

Run: `./sbtw "testOnly oathdigital.gameplay.OathDriverSuite oathdigital.application.PhaseMenuServiceSuite"`
Expected: PASS.

- [ ] **Step 4: Convert every start command in the tests**

Convert every use of the three commands Task 5 deletes, whether or not its test fails:

- In a situation step (`.after(...)`, `.parkedAfter(...)`, a step `Vector`):
  - `GameCommand.StartWalker(ref, StartPayload(p, args))` becomes `MenuPlay.start(p, ref, args)`;
  - `GameCommand.BeginRest(p)` becomes `MenuPlay.start(p, PhaseTransitionRef.BeginRest)`;
  - `GameCommand.UsePower(p, power, source)` becomes `MenuPlay.start(p, ActionRef.UsePower(power), Vector(source))`.
- In `Situation.rest`, `.after(GameCommand.BeginRest(actor))` becomes `.after(MenuPlay.start(actor, PhaseTransitionRef.BeginRest))`.
- In a direct `service.handle(id, n, ...)`, the command becomes `MenuPlay.starting(p, ref, args)`, with the same three translations. When that service's table was built by hand and parks no menu, add `.phaseMenuParked` to the table before `.service()`.
- In a test's fixed command value (`private val startCampaign = GameCommand.StartWalker(...)`), make it a `MenuPlay.starting(...)` value the same way.
- Leave these for Task 5: the arms in `SituationDriver.transition`, `PendingWalkerInvariantSuite.everyCommand`, `MembershipAuthorizationServiceSuite`, `GameHttpWireSuite`, `CommandProtocolSuite` and `MenuPlay.ending`.

`grep -rn "GameCommand.StartWalker\|GameCommand.BeginRest\|GameCommand.UsePower" src/test/scala` then shows only the files Task 5 handles.

A rules-level `rules.startWalker(state, ref, p, args)` on a state the driver produced (a situation's state, or the result of another command) becomes `MenuPlay.started(rules, state, p, ref, args)`. On a hand-built table, leave it as it is.

- [ ] **Step 5: Run everything and update the expected changes**

Run: `./sbtw "test"`

These failures are expected. Update each test and list it in your report:

- **The menu is parked.** A test that reads `walkerPending` or `walkerProcedure` as empty in Act, after the driver ran, now sees the Act menu. Assert `MenuPlay.parked(...) == Some(PhaseMenu.Menu.Act)` instead.
- **Starting refused.** A rules-level start on a state the driver left in Act is refused with "a walker procedure is already pending". Convert it as Step 4 says.
- **The menu's park.** A test that asserts a command's exact `events` now sees the Act menu's `WalkerParked` after a walk that leaves Act open.
- **The projection.** In a projection with the Act menu parked, the active player's legal controls are `resolveWalkerDecision`, `phase` is `"act-menu-walker-decision"`, `actionSelectionOpen` is false and `minorActions` is empty. A test that read Act controls off a state the driver produced reads the menu's options instead, through `MenuPlay.options`; one on a hand-built table with no menu parked keeps its controls (plan-time ruling 9).
- **The first Act.** `Situation.act` now holds the Act menu parked.

Any other failure: stop and report.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/OathDriver.scala \
  src/test/scala
git status --short
git commit -m "feat(walker): the driver parks the Act menu"
```

Check that `git status --short` lists no file outside `src/` you did not mean to change.

---

### Task 4: The menu panel

The frontend draws every phase menu as one grouped panel. The hand-mapped Act buttons go.

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/MenuPanel.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ParkedDecision.scala`, `ActionDecisionRenderer.scala`, `ServerUiSupport.scala`
- Delete: `frontend/src/main/scala/oathdigital/frontend/{EconomyControls,BannerControls,NegotiationControls,CampaignControls,PhasePowerButtons}.scala`
- Create test: `frontend/src/test/scala/oathdigital/frontend/MenuPanelSuite.scala`
- Modify or delete tests: listed in Step 5

**Interfaces:**
- Produces: `ParkedDecision.Surface.Menu(decision: WalkerDecisionState, query: DecisionQueryState.ChooseOne)` and `MenuPanel.render(surface: ParkedDecision.Surface.Menu, canControl: Boolean, panel: dom.Element, controls: TableControls): Unit`.

- [ ] **Step 1: Write the failing frontend test**

Create `frontend/src/test/scala/oathdigital/frontend/MenuPanelSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.frontend.Dom.all
import oathdigital.testkit.Unwrap.as
import ParkedDecision.Surface
import org.scalajs.dom

/** A phase menu drawn as grouped buttons (walker-driven game loop design,
  * slice 4, plan-time ruling 6). Runs under jsdom. */
class MenuPanelSuite extends munit.FunSuite:
  private def button(id: String, label: String,
      badge: Option[String] = None) =
    DecisionOptionState("button", id, label, None, Vector.empty, badge)

  private val search = button("search", "Search", Some("2 Supply"))
  private val recover = button("recover", "Recover").copy(
    state = "unavailable", reason = Some("Needs 1 Supply; you have 0"))
  private val peek = button("peek-site-relics", "Peek at relics at your site")
  private val power = DecisionOptionState("denizen", "97", "Insomnia",
    Some(CardDetails("97", "denizen", "Insomnia",
      rulesText = Some("**ACT:** Gain [secret]."))))
  private val ending = button("end-act", "End Act and Rest (+5 Supply)")

  private def draw(options: Vector[DecisionOptionState],
      ui: RecordingControls = new RecordingControls()): dom.Element =
    val query = DecisionQueryState.ChooseOne(options,
      heading = Some("Act: take an action, use a power or rest"))
    val panel = dom.document.createElement("div")
    MenuPanel.render(Surface.Menu(WalkerDecisionState("act-menu",
      "phase.menu", "decide", query = Some(query)), query),
      canControl = true, panel, ui)
    panel

  test("the options are grouped, and the ending comes last"):
    val panel = draw(Vector(search, recover, peek, power, ending))
    assertEquals(all(panel, ".action-group-heading").map(_.textContent),
      Vector("Major actions", "Minor actions", "Powers"))
    assertEquals(all(panel, ".menu-option").map(_.textContent), Vector(
      "Search (2 Supply)", "Recover", "Peek at relics at your site",
      "Insomnia", "End Act and Rest (+5 Supply)"))
    assertEquals(all(panel, ".menu-ending").map(_.textContent),
      Vector("End Act and Rest (+5 Supply)"))

  test("an unavailable option is disabled and says why"):
    val panel = draw(Vector(recover, ending))
    val shown = as[dom.html.Button](all(panel, ".menu-option").head)
    assert(shown.disabled)
    assertEquals(shown.getAttribute("title"), "Needs 1 Supply; you have 0")
    assertEquals(all(panel, ".walker-choice-reason").map(_.textContent),
      Vector("Needs 1 Supply; you have 0"))

  test("a power shows its rules text on hover"):
    val panel = draw(Vector(power, ending))
    assertEquals(as[dom.html.Button](all(panel, ".menu-option").head)
      .getAttribute("title"), "**ACT:** Gain [secret].")

  test("a Wake menu's Take Wealth buttons have no heading"):
    val panel = draw(Vector(button("take-wealth.favor",
      "Take Wealth: 1 favor"), button("end-wake", "End Wake")))
    assertEquals(all(panel, ".action-group-heading").size, 0)
    assertEquals(all(panel, ".menu-option").map(_.textContent),
      Vector("Take Wealth: 1 favor", "End Wake"))

  test("a pick submits the menu's answer"):
    val ui = new RecordingControls()
    as[dom.html.Button](all(draw(Vector(search, ending), ui),
      ".menu-option").head).click()
    assertEquals(ui.submitted, Vector(
      oathdigital.protocol.GameIntent.ResolveWalker("phase.menu",
        oathdigital.protocol.DecisionAnswerWire.ChooseOneWire("button",
          "search"))))
```

If `CardDetails` takes other parameters, build it as `WalkerChoicePanelRenderSuite` does.

Run: `./sbtw "frontend/testOnly oathdigital.frontend.MenuPanelSuite"`
Expected: compile failure: `MenuPanel` and `Surface.Menu` do not exist.

- [ ] **Step 2: The panel**

Create `frontend/src/main/scala/oathdigital/frontend/MenuPanel.scala`:

```scala
package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport.{button, element, text}

/** A phase menu as grouped buttons (walker-driven game loop design, slice
  * 4, plan-time ruling 6): any button that is neither an action nor the
  * ending, without a heading; then the major actions, the minor actions and
  * the powers, each under its heading; then the phase's ending, alone. The
  * groups are read off the option ids the engine declares; the options and
  * their order are the engine's.
  */
private[frontend] object MenuPanel:
  /** The menus' decision id (`PhaseMenu.decisionId`). */
  val decisionId: String = "phase.menu"

  private val majors = Set("search", "travel", "campaign", "muster",
    "trade.favor", "trade.secret", "forge", "recover", "challenge")
  private val minors = Set("place-banner-resource", "negotiation",
    "play-facedown-adviser", "peek-site-relics", "reveal-owned-relic",
    "move-warbands")
  private val endings = Set("end-wake", "finish-rest", "end-act")

  private def isButton(option: DecisionOptionState, ids: Set[String])
      : Boolean = option.kind == "button" && ids(option.id)

  def render(surface: ParkedDecision.Surface.Menu, canControl: Boolean,
      panel: dom.Element, controls: TableControls): Unit =
    val options = surface.query.options
    def control(option: DecisionOptionState, style: String): dom.Element =
      val row = element("div", "action-option")
      val label = option.badge.fold(option.label)(badge =>
        s"${option.label} ($badge)")
      val choose = button(label, s"walker-choice menu-option $style".trim)
      choose.disabled = !canControl
      option.card.flatMap(_.rulesText)
        .foreach(choose.setAttribute("title", _))
      choose.onclick = _ => controls.submitCommand(
        WalkerPanelSupport.resolveChooseOneCommand(surface.decision, option))
      row.appendChild(choose)
      WalkerPanelSupport.withholds(choose, option, row)
      row
    def group(key: String, heading: Option[String],
        members: Vector[DecisionOptionState]): Unit =
      if members.nonEmpty then
        val section = element("section",
          s"available-action-group action-group-$key")
        heading.foreach(words =>
          section.appendChild(text("h3", "action-group-heading", words)))
        members.foreach(option => section.appendChild(control(option, "")))
        panel.appendChild(section)
    val (ending, listed) = options.partition(isButton(_, endings))
    group("other", None, listed.filter(option => option.kind == "button" &&
      !majors(option.id) && !minors(option.id)))
    group("major", Some("Major actions"), listed.filter(isButton(_, majors)))
    group("minor", Some("Minor actions"), listed.filter(isButton(_, minors)))
    group("power", Some("Powers"), listed.filter(_.kind != "button"))
    ending.foreach(option => panel.appendChild(control(option,
      "menu-ending")))
```

If `withholds` prints its reason under `after`'s last child in a way that puts it outside `row`, keep it as it is; the test reads `.walker-choice-reason` anywhere in the panel.

In `ParkedDecision.scala`:

- Add to `enum Surface`, after `ChooseOne`:

  ```scala
    /** A phase menu: the grouped panel (walker-driven game loop design,
      * slice 4). */
    case Menu(decision: WalkerDecisionState,
        query: DecisionQueryState.ChooseOne)
  ```

- In `render`, add `case surface: Surface.Menu => MenuPanel.render(surface, canControl, panel, controls)`.
- In `formSurface`, add as the first case:

  ```scala
      case one: DecisionQueryState.ChooseOne
          if decision.decisionId == MenuPanel.decisionId &&
            decision.kind == "decide" =>
        Some(Surface.Menu(decision, one))
  ```

Run: `./sbtw "frontend/testOnly oathdigital.frontend.MenuPanelSuite"`
Expected: PASS.

- [ ] **Step 3: Delete the hand-mapped Act buttons**

- In `ActionDecisionRenderer.actionsPanel`, delete the whole `if showActActionControls(value, presentation) then` block, through the End Act and Rest button. Delete the `import controls._` line if nothing else in the method uses it.
- In `status`, delete the `case None if value.phase == "act-action-selection" => ()` arm and its comment: a live Act now always has its menu's heading to show.
- Delete `EconomyControls.scala`, `BannerControls.scala`, `NegotiationControls.scala`, `CampaignControls.scala` and `PhasePowerButtons.scala`.
- In `ServerUiSupport.scala`, delete `showActActionControls`, `actionCategory`, `majorFamilyOrder`, `actionFamily`, `actionCategoryOrder`, `ActionSections` and `facedownAdviserLaunchCount`. Delete `actionLabel` too if `grep -rn "actionLabel" frontend/src` shows no other caller.

- [ ] **Step 4: Run the frontend tests**

Run: `./sbtw "frontend/test"`

- [ ] **Step 5: Update the frontend tests**

These failures are expected:

- **Suites of deleted files.** Delete `EconomyControlsSuite`, `BannerControlsSuite`, `NegotiationControlsSuite`, `CampaignControlsSuite` and `PhasePowerButtonsSuite`.
- **The Act panel's suites.** `ActionOptionRowSuite`, `RestButtonSuite`, `SearchLaunchSuite`, `TravelLaunchSuite` and `FacedownAdviserPickSuite` test the deleted buttons. Delete each test that clicks or reads a deleted button. Delete a suite whose every test does. When a test checks something the menu panel now does (a row per option, a reason on hover, the rest gain in the ending's label), say which `MenuPanelSuite` test covers it in your report.
- **The menu route.** A `ParkedDecisionSuite` or `ServerModeSessionSuite` test that expects a `phase.menu` decision to route to `Surface.ChooseOne` now gets `Surface.Menu`. Its buttons keep the `walker-choice` class, so queries by that class still work.
- **Helpers.** Remove from `RenderedControls`, `ServerModeFixtures` and `TestProjections` any helper only the deleted tests used.

Any other failure: stop and report.

Run: `./sbtw "frontend/test"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/MenuPanel.scala \
  frontend/src/main/scala/oathdigital/frontend/ParkedDecision.scala \
  frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala \
  frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala \
  frontend/src/test/scala
git rm frontend/src/main/scala/oathdigital/frontend/EconomyControls.scala \
  frontend/src/main/scala/oathdigital/frontend/BannerControls.scala \
  frontend/src/main/scala/oathdigital/frontend/NegotiationControls.scala \
  frontend/src/main/scala/oathdigital/frontend/CampaignControls.scala \
  frontend/src/main/scala/oathdigital/frontend/PhasePowerButtons.scala
git status --short
git commit -m "feat(frontend): every phase menu is one grouped panel"
```

Use `git rm` for each deleted test suite too.

---

### Task 5: The start commands and the phase checks go

Nothing sends `StartWalker`, `UsePower` or `BeginRest` any more, and the procedures stop checking the phase they start in.

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandIntents.scala`, `CommandIntentCodec.scala`, `CommandIntentDecoders.scala`
- Modify: `src/main/scala/oathdigital/application/GameCommands.scala`, `Authorization.scala`, `GameIntentMapper.scala`, `GameApplicationService.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathLifecycle.scala` and its callers (Step 3)
- Modify tests: listed in Step 4

**Interfaces:**
- Removes: `GameIntent.StartWalker`, `UsePower` and `BeginRest`; `GameCommand.StartWalker`, `UsePower`, `BeginRest` and `StartPayload`; `OathLifecycle.validateAct` and `validateReady`; `BeginRestProcedure.validateBegin`.
- Changes: `MenuPlay.ending(menu: PhaseMenu.Menu, player: PlayerId): Step`.

- [ ] **Step 1: Delete the intents and commands**

- `shared/.../CommandIntents.scala`: delete `BeginRest`, `UsePower` and `StartWalker`. Keep `WalkerStartArgWire` if another intent still uses it; otherwise delete it too.
- `CommandIntentCodec.scala` and `CommandIntentDecoders.scala`: delete their arms.
- `application/GameCommands.scala`: delete `StartWalker`, `BeginRest`, `UsePower`, their `issuer` arms, and `StartPayload` with its doc.
- `application/GameIntentMapper.scala`: delete the three arms, and any helper only they used.
- `application/Authorization.scala`: delete `beginRest` and `usePower`.
- `application/GameApplicationService.scala`: delete the `StartWalker`, `UsePower` and `BeginRest` arms of `applyUnblockedCommand`.

Run `./sbtw compile` and remove what the compiler names as unused.

- [ ] **Step 2: The test helpers**

- `SituationDriver.Rules.transition`: delete the `StartWalker`, `BeginRest` and `UsePower` arms.
- `MenuPlay.ending` becomes:

  ```scala
  /** The step that ends `menu`'s phase for `player`: see `Step.Start`. */
  def ending(menu: PhaseMenu.Menu, player: PlayerId): Step =
    Step.Start(player, menu.ending)
  ```

  In `Situation.ending`, the call becomes `after(MenuPlay.ending(menu, player))`. Any other caller that passed `ending`'s result to `service.handle` passes `MenuPlay.picking(player, menu.endingOption)` instead.

- [ ] **Step 3: The phase checks go (spec ruling 9)**

Delete `validateReady` and `validateAct` from `OathLifecycle`, and the `OathLifecycle` object itself if nothing is left in it. `GameplayTransition` in the same file stays.

Remove each call. Where the call bound a value (`ready <- OathLifecycle.validateReady(OathState.Ready(state), activePlayer)`), use the builder's own state parameter in its place:

- `gameplay/actions/economy/EconomyTree.scala`
- `gameplay/actions/cardplay/CardPlayProcedure.scala`
- `gameplay/actions/forge/ForgeProcedure.scala`
- `gameplay/actions/negotiation/NegotiationProcedure.scala`
- `gameplay/actions/challenge/ChallengeProcedure.scala`
- `gameplay/actions/challenge/PlaceBannerResourceProcedure.scala`
- `gameplay/actions/recover/RecoverProcedure.scala` (two calls)
- `gameplay/actions/search/SearchProcedure.scala`
- `gameplay/actions/campaign/CampaignProcedure.scala`
- `gameplay/actions/travel/TravelProcedure.scala` (two calls)
- `gameplay/actions/minor/MinorActionProcedures.scala` (in `started`; its doc becomes "no start selection, and the first-game rules")
- `gameplay/phases/wake/EndWakeProcedure.scala` and `TakeWealthProcedure.scala`

In `BeginRestProcedure`, delete `validateBegin`; `build` calls `validateSupportedState(catalog, ready)` in its place. In `LegalActionProjector`, the `beginRest` control reads `BeginRestProcedure.validateSupportedState(catalog, context.ready).isRight`.

In `FinishRestProcedure.gate`, delete the `else if current.turn.phase != Phase.Rest then Left(WrongPhase(...))` branch.

Update the docs that mention these checks:

- `EndWakeProcedure`'s doc names `OathLifecycle.validateReady` and `WrongPhase(Wake, Act)`: the sentences become "The Wake menu offers End Wake only in Wake (walker-driven game loop design, ruling 9)."
- `TakeWealthProcedure`'s comment above the deleted call goes.
- `RecoverProcedure`'s doc line "`build` validates Act phase and the action-specific necessity" becomes "`build` validates the action-specific necessity".
- Any other doc `grep -rn "validateAct\|validateReady\|Act-phase gate" src/main/scala` shows.

Leave `PhasePowerProcedure`'s timing check as it is (plan-time ruling 8).

Run: `./sbtw compile`
Expected: success.

- [ ] **Step 4: Run everything and update the expected changes**

Run: `./sbtw "test" "frontend/test"`

These failures are expected. Update each test and list it in your report:

- **The command lists.** `PendingWalkerInvariantSuite.everyCommand` loses its `StartWalker`, `UsePower` and `BeginRest` instances, and its doc says so. `MembershipAuthorizationServiceSuite`, `GameHttpWireSuite`, `CommandProtocolSuite` and `HttpGameClientSuite` lose their cases for the deleted commands and intents. A wire test, or a frontend test such as `TableSessionSuite`, that used one of the deleted intents as its sample submits `ResolveWalker` instead.
- **Wrong phase.** A test that expects `WrongPhase` from a start now sees the start accepted, or refused for another reason. The menus check the phase now. Delete the `WrongPhase` assertion, and the test when it asserts nothing else; say in your report which menu test covers that option's absence. `grep -rn "WrongPhase" src/test/scala` finds them: `RestSuite`, `FollowUpRulesSuite`, `MusterProcedureSuite`, `EndWakeProcedureSuite`, `TakeWealthProcedureSuite` and `MinorActionsSuite`'s "a minor action refuses an off-turn actor, a wrong phase and a start selection", whose other two assertions stay and whose name loses "a wrong phase".
- **Begin Rest's check.** A test of `BeginRestProcedure.validateBegin` tests `validateSupportedState`.

Any other failure: stop and report.

- [ ] **Step 5: Commit**

```bash
git add shared/src src/main/scala src/test/scala frontend/src/test/scala
git status --short
git commit -m "refactor: the start commands and the procedures' phase checks go"
```

Check that `git status --short` lists only files this task changed.

---

### Task 6: Docs, and the gates

**Files:**
- Modify: `docs/ROADMAP.md`, `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md`

- [ ] **Step 1: The roadmap and the spec's status**

- In `docs/ROADMAP.md`, under "Phase - Walker-driven game loop", change "- [ ] Slice 4: the Act menu and its frontend panel." to "- [x]".
- Delete the open item "**Start controls and modifier combinations.**" with its whole paragraph: the Act menu's search replaces the heuristic it describes.
- In the spec, the status line becomes: "**Status:** designed 2026-10-09. Slices 1 to 4 have landed; slice 5 is ready to plan."

- [ ] **Step 2: The gates**

Run, in order:

1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass. Compare the test counts with the baseline and list every test added and deleted.

- [ ] **Step 3: Commit, and merge**

```bash
git add docs/ROADMAP.md \
  docs/superpowers/specs/2026-10-09-walker-game-loop-design.md
git commit -m "docs: record walker game loop slice 4"
```

Run `ExitWorktree` with `keep`. Then, in the main checkout, check `git log --oneline -1` and merge the branch into `main` with `git merge --no-ff`, using the message "Merge walker game loop slice 4". Do not push.
