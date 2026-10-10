# Walker-Driven Game Loop, Slice 2 (The Wake and Rest Menus) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When the queue is empty in Wake or Rest, the driver parks the phase's menu: one walker decision over Take Wealth, the phase's powers and the phase's ending. A pick queues its procedure, and the driver starts it in the same command. When the ending is the only option available, the phase ends by itself, as today. The End Wake and Finish Rest commands, the Wake and Rest legal controls and the frontend's Wake and Rest buttons go.

**Architecture:**

- **The menu.** A new object, `PhaseMenu`, builds two triggered procedures, `WakeMenu` and `RestMenu`. Its tree is a choose-one `Decide` and a `Branch` that queues the picked option's `QueuedProcedure`. Each option is priced by `QueuedStart.refusal`, the start check slice 1 built for the follow-up offer.
- **The driver.** `OathDriver.phaseEnd` asks `PhaseMenu` for the phase's options. If only the ending is available, it starts the ending directly, as slice 1 does. Otherwise it starts the menu.
- **Tests.** A test situation settles at a menu instead of answering it. `MenuPlay` picks from a menu as a player does.
- **Deletions.** `GameCommand.EndWake`, `GameCommand.FinishRest`, their intents and routes, `LegalActionProjector`'s Wake and Rest controls, `TakeWealthProcedure.candidates`, and the frontend's Take Wealth, End Wake, Finish Rest and Wake/Rest power buttons.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md`, including its "Plan-time rulings (slice 1)", before starting. Task 1 adds this plan's rulings to it. Also read `docs/superpowers/plans/2026-10-09-walker-game-loop-slice1.md`, Tasks 3 and 4: this plan builds on the driver and the follow-up offer they wrote.

**Starts from:** `main`, after walker game loop slice 1 has merged. Check that `git log --oneline main` shows slice 1's last commit, "docs: record walker game loop slice 1". If slice 1 has not merged, stop and report.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`.
  - An unused import or private member fails the build. Remove every import a deletion leaves unused.
  - A non-exhaustive match fails the build. A new enum case needs an arm in every match over its enum.
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
- **Texts.** Docs and commit messages use plain English. Game text (headings, labels, reasons) is given in the steps; use it exactly.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end. Report every test added and deleted.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

Task 1 writes rulings 1 to 7 into the spec. Ruling 8 concerns only the tests; Task 4 writes it into `CONTEXT.md`.

1. **The engine lists the phase powers.** A menu reads the phase power catalog, as Use Power and Forced Wake do. It shows one option for each accessible source of each power its player may choose in the phase. Slice 2 builds no menu window and no `Offer` contribution for menus, for two reasons:
   - a `PhasePower` is not a `ContributingPower`;
   - its sources come from the rule-source index, not from one power instance per source.

   This replaces the mechanism of spec ruling 7. A window is added when a power needs to restrict a menu option.
2. **One procedure per menu.** `TriggeredProcedureRef.WakeMenu` and `RestMenu` are both built by `PhaseMenu`. Slice 4 adds `ActMenu`.
3. **A menu lists every option of its phase.**
   - Wake: Take Wealth for a favor, Take Wealth for a secret, each WAKE power's source, then End Wake.
   - Rest: each REST power's source, then Finish Rest.

   An option that cannot start is shown unavailable with its reason. A phase power's option is its source, which the projector names. Take Wealth's options and the ending are buttons.
4. **Availability is the start's own check.** Each option runs `QueuedStart.refusal` (slice 1, plan-time ruling 4). No Wake or Rest option costs anything today, so none shows a price. The dry-run price arrives with the Act menu.
5. **The menus draw as a panel.** They use `DecisionPresentation.Panel`, and the generic choose-one panel draws them. Hovering over a card option shows its rules text. The grouped Act layout is slice 4's.
6. **The phase checks stay in their procedures until slice 4.** `StartWalker` can still start Take Wealth, End Wake and Finish Rest, so they keep checking the phase. Ruling 9's removal moves to slice 4, with `StartWalker`.
7. **The End Wake and Finish Rest commands go in slice 2.** In Wake and Rest, either a menu is parked or the phase has ended by itself, so nothing can send them. `UsePower` and `BeginRest` stay until the Act menu.
8. **A test situation settles at a menu.** The situation driver stops at a parked phase menu instead of answering it, since the menu asks what the player does next. `Situation.endingWake` and `finishingRest` pick the ending.

## What exists before this plan

Check each of these in the code before Task 1. If a name or signature differs, read slice 1's commits and adapt. Stop and report if the difference is more than a name.

- `model/GameState.scala`:
  - `final case class QueuedProcedure(actor: PlayerId, procedure: StartableRef, startArgs: Vector[DecisionOptionRef], source: Option[PowerSourceRef], kind: QueueKind)`;
  - `enum QueueKind { case Run, Offer }`;
  - `CurrentGameState.queue`, `walkerEntry` and `boundaryDue`.
- `model/CoreOperations.scala`: `final case class QueueProcedure(entry: QueuedProcedure, atFront: Boolean = false)`.
- `model/ProcedureRef.scala`: `TriggeredProcedureRef` has `Oathkeeper`, `Setup`, `ForcedWake` and `FollowUpOffer`, and `TriggeredProcedureRef.all`.
- `gameplay/OathDriver.scala`:
  - `private[gameplay] enum Ended { case Walk(procedure: ProcedureRef); case MinorAction }`;
  - `next`, and the private `phaseEnd`, `phasePowerUsable`, `wakeOptionOpen`, `joined` and `append`.
- `gameplay/walker/QueuedStart.scala`: `refusal(catalog, ready, entry, powers, phasePowers): Option[OathViolation]` and `startingFrom`.
- `gameplay/walker/FollowUpOffer.scala`: `decisionId`, `take`, `skip`, `build(phasePowers, powers)(catalog, ready, actor, args)` and `skips`.
- `gameplay/walker/WalkerProcedureRegistry.scala`:
  - `build` and `rebuild` take a last parameter `walkerPowers: WalkerPowers = WalkerPowers.empty`;
  - `lookup` routes `FollowUpOffer` to `followUpOfferEntry`;
  - `isRegistered` accepts `FollowUpOffer`.
- `OathRulesWalker.buildWalker` and `WalkerDecisionProjector.tree` list `FollowUpOffer` beside `UsePower` and `ForcedWake`.
- `application/gamelog/ActionLines.scala`: an arm for `TriggeredProcedureRef.FollowUpOffer` that writes nothing.
- Tests:
  - `testkit/ParkedNode.of(state, catalog, walkerPowers, phasePowers)` passes `walkerPowers` to `rebuild`;
  - `testkit/Table.offering(entry)` and `Table.parkedWalker(procedure, path, answered)`;
  - `gameplay/FollowUpPlay`;
  - `gameplay/OathDriverSuite`.

## File map

| File | Change | Task |
|---|---|---|
| `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md` | modify | 1, 4 |
| `src/main/scala/oathdigital/model/ProcedureRef.scala` | modify | 1 |
| `src/main/scala/oathdigital/gameplay/phases/PhaseMenu.scala` | create | 1 |
| `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` | modify | 1 |
| `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala` | modify | 1 |
| `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala` | modify | 1 |
| `src/main/scala/oathdigital/application/gamelog/*.scala` | modify where a match needs the new cases | 1 |
| `src/main/scala/oathdigital/gameplay/OathDriver.scala` | modify | 2 |
| `src/main/scala/oathdigital/gameplay/phases/wake/TakeWealthProcedure.scala` | modify | 3 |
| `src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala` | modify (doc) | 3 |
| `src/main/scala/oathdigital/application/{GameCommands,Authorization,GameIntentMapper,GameApplicationService,LegalActionProjector}.scala` | modify | 3 |
| `shared/src/main/scala/oathdigital/protocol/{CommandIntents,CommandIntentCodec,CommandIntentDecoders}.scala` | modify | 3 |
| `frontend/src/main/scala/oathdigital/frontend/{ActionDecisionRenderer,ServerUiSupport,PhasePowerButtons,WalkerPanelSupport}.scala` | modify | 3 |
| `src/test/scala/oathdigital/testkit/MenuPlay.scala` | create | 1 |
| `src/test/scala/oathdigital/testkit/{Table,Situation,SituationDriver}.scala` | modify | 1, 2, 3 |
| `src/test/scala/oathdigital/gameplay/PhaseMenuSuite.scala` | create | 1 |
| `src/test/scala/oathdigital/application/PhaseMenuServiceSuite.scala` | create | 1 |
| `src/test/scala/oathdigital/application/gamelog/{LogScripts,GameLogPhaseMenuSuite}.scala` | modify, create | 1 |
| `src/test/scala/oathdigital/gameplay/OathDriverSuite.scala` | modify | 2 |
| other test suites (listed per task) | modify | 1-3 |
| `CONTEXT.md` | modify | 4 |
| `docs/ROADMAP.md` | modify | 4 |

---

### Task 1: The menu procedure

The menu exists and works when parked, but nothing parks it yet: the tests park it by hand. The driver still ends Wake and Rest as slice 1 left it.

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md`
- Modify: `src/main/scala/oathdigital/model/ProcedureRef.scala`
- Create: `src/main/scala/oathdigital/gameplay/phases/PhaseMenu.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`, and any other match the compiler reports
- Create: `src/test/scala/oathdigital/testkit/MenuPlay.scala`
- Modify: `src/test/scala/oathdigital/testkit/Table.scala`
- Create: `src/test/scala/oathdigital/gameplay/PhaseMenuSuite.scala`, `src/test/scala/oathdigital/application/PhaseMenuServiceSuite.scala`, `src/test/scala/oathdigital/application/gamelog/GameLogPhaseMenuSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`
- Modify tests: `ProcedureRefSuite`, `WalkerProcedureRegistrySuite`, wherever they list the triggered procedures

**Interfaces:**
- Consumes: slice 1's `QueuedProcedure`, `QueueKind`, `QueueProcedure`, `QueuedStart.refusal`, and the registry's `walkerPowers` parameter.
- Produces, in `oathdigital.model`: `TriggeredProcedureRef.WakeMenu` (key `"wake-menu"`) and `RestMenu` (key `"rest-menu"`).
- Produces, in `oathdigital.gameplay.phases`:

  ```scala
  object PhaseMenu:
    val decisionId: String                       // "phase.menu"
    enum Menu(val phase: Phase, val procedure: TriggeredProcedureRef,
        val ending: PhaseTransitionRef,
        val endingOption: DecisionOptionRef.Button, val endingLabel: String,
        val heading: String):
      case Wake, Rest
    def of(phase: Phase): Option[Menu]
    def asking(procedure: ProcedureRef): Option[Menu]
    def takeWealth(resource: WakeResource): DecisionOptionRef.Button
    final case class Offered(entry: QueuedProcedure, option: DecisionOption)
    def offers(menu: Menu, catalog: ExecutableCatalog, ready: ReadyGame,
        actor: PlayerId, phasePowers: PhasePowers, powers: WalkerPowers)
        : Vector[Offered]
    def onlyEnding(menu: Menu, offers: Vector[Offered]): Boolean
    def build(menu: Menu, phasePowers: PhasePowers, powers: WalkerPowers)(
        catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
        args: Vector[DecisionOptionRef]): Either[OathViolation, Operation]
  ```

- Produces, in tests:
  - `MenuPlay.picking`, `picked`, `parked`, `ending` and `options` (Step 3);
  - `Table.phaseMenuParked`;
  - the Game Log scripts `LogScripts.wealthFromMenu` and `wealthStarted`.

- [ ] **Step 1: Set up and record the baseline**

Follow Global Constraints, "Worktree". Check "What exists before this plan". Run `./sbtw "test" "frontend/test"` and note both counts.

- [ ] **Step 2: Write the plan-time rulings into the spec**

In `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md`, add a section after "Plan-time rulings (slice 1)":

```markdown
## Plan-time rulings (slice 2)

1. **The engine lists the phase powers.** A menu reads the phase power
   catalog, as Use Power and Forced Wake do: one option for each accessible
   source of each power its player may choose in the phase. No menu window
   and no `Offer` contribution are built. A `PhasePower` is not a
   `ContributingPower`, and its sources come from the rule-source index, not
   from one power instance per source. This replaces ruling 7's mechanism. A
   window is added when a power needs to restrict a menu option.
2. **One procedure per menu.** `WakeMenu` and `RestMenu` are triggered
   procedures, both built by `PhaseMenu`. Slice 4 adds `ActMenu`.
3. **A menu lists every option of its phase.** Wake: Take Wealth for a
   favor, Take Wealth for a secret, each WAKE power's source, then End Wake.
   Rest: each REST power's source, then Finish Rest. An option that cannot
   start is shown unavailable with its reason. A phase power's option is its
   source.
4. **Availability is the start's own check**, `QueuedStart.refusal`. No
   Wake or Rest option costs anything today, so none shows a price; the
   dry-run price arrives with the Act menu.
5. **The menus draw as a panel**, in the generic choose-one panel. Hovering
   over a card option shows its rules text. The grouped Act layout is slice
   4's.
6. **The phase checks stay in Take Wealth, End Wake and Finish Rest** while
   `StartWalker` can still start them. Ruling 9's removal moves to slice 4.
7. **The End Wake and Finish Rest commands go in slice 2.** In Wake and Rest
   a menu is parked or the phase has ended by itself, so nothing can send
   them. `UsePower` and `BeginRest` stay until the Act menu.
```

In "The menus", replace the bullet that begins "**phase powers' offers**: each `PhasePower` contributes an `Offer`" with:

```markdown
- **phase powers' offers**: one for each accessible source of each
  `PhasePower` of the phase's timing that is not forced (plan-time ruling 1
  of slice 2). Its once-per-turn limit, its source's accessibility and its
  `usable` stay its own. Hunger and any other forced power are started by
  Forced Wake and offer nothing.
```

At the end of ruling 7, add: "(Its mechanism is replaced by plan-time ruling 1 of slice 2.)"

- [ ] **Step 3: The test helpers**

Create `src/test/scala/oathdigital/testkit/MenuPlay.scala`:

```scala
package oathdigital.testkit

import oathdigital.application.{GameCommand, StartPayload, TreeDecision}
import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._

/** Picks from a phase menu as its player does (walker-driven game loop
  * design, "The menus"). */
object MenuPlay:
  /** The command that picks `option` from the parked menu as `player`. */
  def picking(player: PlayerId, option: DecisionOptionRef): GameCommand =
    GameCommand.ResolveWalker(player, TreeDecision(PhaseMenu.decisionId,
      DecisionAnswer.ChooseOneAnswer(option)))

  /** Picks `option` through the rules directly. */
  def picked(rules: OathRules, state: OathState, player: PlayerId,
      option: DecisionOptionRef): Either[OathViolation, OathTransition] =
    rules.resolveWalker(state, player, PhaseMenu.decisionId,
      DecisionAnswer.ChooseOneAnswer(option))

  /** The menu parked in `state`, when one is. */
  def parked(state: OathState): Option[PhaseMenu.Menu] = state match
    case OathState.Ready(ready) =>
      ready.game.current.walkerProcedure.flatMap(PhaseMenu.asking)
    case _ => None

  /** The command that ends `menu`'s phase for `player`: the ending's pick
    * when the menu is parked, or else a start of the ending, as on a table
    * no driver has run on. */
  def ending(state: OathState, menu: PhaseMenu.Menu, player: PlayerId)
      : GameCommand =
    if parked(state).contains(menu) then picking(player, menu.endingOption)
    else GameCommand.StartWalker(menu.ending, StartPayload(player))

  /** The parked menu's options, as the walker shows them. */
  def options(state: OathState, catalog: ExecutableCatalog,
      walkerPowers: WalkerPowers, phasePowers: PhasePowers)
      : Vector[DecisionOption] =
    ParkedNode.of(state, catalog, walkerPowers, phasePowers).toOption.flatten
      .collect { case ParkedNode.Decision(_, decide, _) => decide.query }
      .collect { case one: DecisionQuery.ChooseOne => one.options }
      .getOrElse(Vector.empty)

  /** The parked menu's options with the production power catalogs. */
  def options(state: OathState, catalog: ExecutableCatalog)
      : Vector[DecisionOption] = options(state, catalog,
    WalkerPowerCatalog.default(catalog), PhasePowerCatalog.default(catalog))
```

If `ParkedNode.Decision` has a different arity, match it as `FollowUpRulesSuite.options` does.

In `src/test/scala/oathdigital/testkit/Table.scala`, after `offering`:

```scala
  /** The menu of the turn's phase parked, as the driver leaves it when the
    * queue is empty and more than the phase's ending is available
    * (walker-driven game loop design, "The menus"). */
  def phaseMenuParked(using munit.Location): Table =
    val phase = game.game.current.turn.phase
    PhaseMenu.of(phase).fold(munit.Assertions.fail(
      s"the $phase phase has no menu"))(menu => parkedWalker(menu.procedure))
```

Import `oathdigital.gameplay.phases.PhaseMenu`. If `Table`'s field is not called `game`, use the field `update` passes to its function.

- [ ] **Step 4: Write the failing rules tests**

Create `src/test/scala/oathdigital/gameplay/PhaseMenuSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.phases.{PhaseMenu, PhasePowerProcedure}
import oathdigital.gameplay.phases.wake.TakeWealthProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.powers.rest.Insomnia
import oathdigital.gameplay.powers.wake.TakeWealthLimit
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureDequeued, WalkerCompleted}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, MenuPlay, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** A phase's menu (walker-driven game loop design, "The menus"), parked as
  * the driver leaves it. */
class PhaseMenuSuite extends munit.FunSuite:
  private val walkerPowers = WalkerPowerCatalog.default(catalog)
  private val phasePowers = PhasePowerCatalog.default(catalog)
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = walkerPowers, phasePowerCatalog = phasePowers)
  private val ancientCity = CatalogNames.site("Ancient City")
  private val insomniaCard = CatalogNames.denizen("Insomnia")
  private val insomnia = DecisionOptionRef.Denizen(insomniaCard)
  private val favor = PhaseMenu.takeWealth(WakeResource.Favor)
  private val secret = PhaseMenu.takeWealth(WakeResource.Secret)

  /** p1 at Ancient City, p2 at Broken Peaks and p3 at Buried Giant. */
  private def table: Table = Table.start
    .pawn(p1, at = ancientCity).pawn(p2, at = "Broken Peaks")
    .pawn(p3, at = "Buried Giant")

  /** p1's Wake, with one favor and no secret at Ancient City. */
  private def waking: Table = table.turn(p1, Phase.Wake)
    .siteTokens(ancientCity, favor = 1, secrets = 0)

  /** p1's Rest, with Insomnia as an adviser. */
  private def resting: Table = table.adviser(p1, "Insomnia")
    .turn(p1, Phase.Rest)

  /** `used` spent this turn. */
  private def spent(at: Table, used: PowerUseRef): Table =
    at.update(_.updateCurrent(current => current.copy(
      turn = current.turn.copy(usedPowers = Set(used)))))

  private def options(state: OathState): Vector[DecisionOption] =
    MenuPlay.options(state, catalog, walkerPowers, phasePowers)

  private def replayed(from: OathState, events: Vector[OathEvent])
      : OathState =
    accepted(events.foldLeft[Either[OathViolation, OathState]](Right(from))(
      (state, event) => state.flatMap(rules.evolve(_, event))))

  test("the Wake menu lists Take Wealth, then End Wake, each with why it " +
      "cannot start"):
    assertEquals(options(waking.phaseMenuParked.state), Vector[DecisionOption](
      DecisionOption.Button(favor, "Take Wealth: 1 favor"),
      DecisionOption.Unavailable(
        DecisionOption.Button(secret, "Take Wealth: 1 secret"),
        OptionReason.Written("No secret at your site")),
      DecisionOption.Button(PhaseMenu.Menu.Wake.endingOption, "End Wake")))

  test("a pick queues its procedure, and the driver starts it in the same " +
      "command"):
    val start = waking.phaseMenuParked.state
    val taken = accepted(MenuPlay.picked(rules, start, p1, favor))
    val entry = QueuedProcedure(p1, ActionRef.TakeWealth,
      TakeWealthProcedure.selection(WakeResource.Favor), None, QueueKind.Run)
    val completed = taken.events.indexOf(
      WalkerCompleted(TriggeredProcedureRef.WakeMenu))
    assertEquals(taken.events.lift(completed + 1),
      Some(ProcedureDequeued(entry): OathEvent))
    assert(taken.events.contains(WalkerCompleted(ActionRef.TakeWealth)),
      taken.events)
    assertEquals(replayed(start, taken.events), taken.state)

  test("an option shown unavailable is refused"):
    assert(MenuPlay.picked(rules, waking.phaseMenuParked.state, p1, secret)
      .isLeft)

  test("Take Wealth already used at this site says so"):
    val used = spent(waking, TakeWealthLimit.useRef(ancientCity))
    assertEquals(options(used.phaseMenuParked.state).head,
      DecisionOption.Unavailable(
        DecisionOption.Button(favor, "Take Wealth: 1 favor"),
        OptionReason.Written("Already taken from this site this turn")))

  test("an enemy pawn at the site says so"):
    val shared = waking.pawn(p2, at = ancientCity)
    assertEquals(options(shared.phaseMenuParked.state).head,
      DecisionOption.Unavailable(
        DecisionOption.Button(favor, "Take Wealth: 1 favor"),
        OptionReason.Written("Another player's pawn is at your site")))

  test("the Rest menu lists each REST power by its source, then Finish " +
      "Rest"):
    val listed = options(resting.phaseMenuParked.state)
    assertEquals(listed.map(_.ref), Vector[DecisionOptionRef](insomnia,
      PhaseMenu.Menu.Rest.endingOption))
    assert(listed.forall(_.available), listed)

  test("a REST power used this turn says so"):
    val used = spent(resting,
      PhasePowerProcedure.useRef(Insomnia, PowerSourceRef.Card(insomniaCard)))
    assertEquals(options(used.phaseMenuParked.state).head,
      DecisionOption.Unavailable(DecisionOption.Denizen(insomnia),
        OptionReason.Written("Already used this turn")))

  test("picking a REST power uses it from that source"):
    val start = resting.phaseMenuParked.state
    val used = accepted(MenuPlay.picked(rules, start, p1, insomnia))
    assert(used.events.contains(
      WalkerCompleted(ActionRef.UsePower(Insomnia.id))), used.events)
    assertEquals(replayed(start, used.events), used.state)

  test("only the active player answers the menu"):
    assertEquals(MenuPlay.picked(rules, waking.phaseMenuParked.state, p2,
      PhaseMenu.Menu.Wake.endingOption),
      Left(OathViolation.WrongPlayer(p1, p2)))
```

If `CatalogNames.denizen` returns a type that `DecisionOptionRef.Denizen` does not take, convert it as `FollowUpRulesSuite` converts Knights Errant's id. `Table.adviser(p1, "Insomnia")` places Insomnia faceup; check its signature in `Table`.

- [ ] **Step 5: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.PhaseMenuSuite"`
Expected: FAIL to compile: `PhaseMenu` does not exist.

- [ ] **Step 6: The procedure references**

In `model/ProcedureRef.scala`, add to `TriggeredProcedureRef`, after `FollowUpOffer`:

```scala
  /** The Wake phase's menu (walker-driven game loop design, "The menus").
    * The driver starts it when the queue is empty in Wake and more than End
    * Wake is available. */
  case WakeMenu extends TriggeredProcedureRef("wake-menu")
  /** The Rest phase's menu, as the Wake menu is Wake's. */
  case RestMenu extends TriggeredProcedureRef("rest-menu")
```

Add both to `TriggeredProcedureRef.all`, after `FollowUpOffer`.

- [ ] **Step 7: The menu**

Create `src/main/scala/oathdigital/gameplay/phases/PhaseMenu.scala`:

```scala
package oathdigital.gameplay.phases

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.phases.wake.TakeWealthProcedure
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.walker.{QueuedStart, WalkerPowers}
import oathdigital.model._

/** A phase's menu (walker-driven game loop design, "The menus"): one
  * decision over everything the active player may do now in Wake or Rest.
  * Each option is the procedure its pick queues.
  *
  * {{{
  * Sequence(
  *   Decide(phase.menu, actor, ChooseOne(options), Panel),
  *   Branch(QueueProcedure(the picked option's entry)))
  * }}}
  *
  * The driver starts it when the queue is empty and more than the phase's
  * ending is available (ruling 3), and starts the picked procedure when this
  * walk ends. An option is available when its start would be accepted
  * (`QueuedStart`), and the walker refuses a pick of any other. The pick's
  * entry has no source, so its walk spends Supply as any start does.
  *
  * The tree runs nothing before its decision, so a resume rebuilds the same
  * question from the same state. The menu is not an action: its walk leaves
  * no boundary due, and the Game Log gives it no section and no line.
  *
  * Phase powers come from the phase power catalog, as Use Power and Forced
  * Wake read them (plan-time ruling 1 of slice 2): one option for each
  * accessible source of each power the player may choose in this phase.
  */
object PhaseMenu:
  val decisionId: String = "phase.menu"

  /** One phase's menu: the procedure that asks it, the procedure that ends
    * the phase, which is always its last option, and its words. */
  enum Menu(val phase: Phase, val procedure: TriggeredProcedureRef,
      val ending: PhaseTransitionRef,
      val endingOption: DecisionOptionRef.Button, val endingLabel: String,
      val heading: String):
    case Wake extends Menu(Phase.Wake, TriggeredProcedureRef.WakeMenu,
      PhaseTransitionRef.EndWake, DecisionOptionRef.Button("end-wake"),
      "End Wake", "Wake: take wealth, use a power or end Wake")
    case Rest extends Menu(Phase.Rest, TriggeredProcedureRef.RestMenu,
      PhaseTransitionRef.FinishRest, DecisionOptionRef.Button("finish-rest"),
      "Finish Rest", "Rest: use a power or finish Rest")

  /** The menu `phase` asks, when it has one. Act's comes in slice 4. */
  def of(phase: Phase): Option[Menu] = Menu.values.find(_.phase == phase)

  /** The menu `procedure` asks, when it is a menu. */
  def asking(procedure: ProcedureRef): Option[Menu] =
    Menu.values.find(_.procedure == procedure)

  /** The option that takes `resource` with Take Wealth. */
  def takeWealth(resource: WakeResource): DecisionOptionRef.Button =
    DecisionOptionRef.Button(s"take-wealth.${resource.key}")

  /** One option of a menu: the entry its pick queues, as the player sees
    * it. */
  final case class Offered(entry: QueuedProcedure, option: DecisionOption)

  /** The menu's options, in order: Take Wealth's two resources in Wake, each
    * phase power's source, then the ending. A source that offers two powers
    * of one phase is listed once, for the first. */
  def offers(menu: Menu, catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, phasePowers: PhasePowers, powers: WalkerPowers)
      : Vector[Offered] =
    def offered(procedure: StartableRef, args: Vector[DecisionOptionRef],
        shown: DecisionOption): Offered =
      val entry = QueuedProcedure(actor, procedure, args, None, QueueKind.Run)
      Offered(entry, QueuedStart.refusal(catalog, ready, entry, powers,
          phasePowers)
        .fold(shown)(violation => DecisionOption.Unavailable(shown,
          OptionReason.Written(reason(procedure, violation)))))
    val wealth =
      if menu != Menu.Wake then Vector.empty
      else WakeResource.all.map(resource => offered(ActionRef.TakeWealth,
        TakeWealthProcedure.selection(resource),
        DecisionOption.Button(takeWealth(resource),
          s"Take Wealth: 1 ${resource.key}")))
    val used = for
      power <- phasePowers.powers
      if !power.forced &&
        PhasePowerProcedure.timingOf(menu.phase).contains(power.timing)
      ref <- PhasePowerProcedure.sources(catalog, ready, actor, power)
        .map(_._2)
      shown <- DecisionOption.forRef(ref).toVector
    yield offered(ActionRef.UsePower(power.id), Vector(ref), shown)
    (wealth ++ used).distinctBy(_.option.ref) :+ offered(menu.ending,
      Vector.empty, DecisionOption.Button(menu.endingOption,
        menu.endingLabel))

  /** Whether the menu has nothing to ask: its ending is the only option
    * available (ruling 3). */
  def onlyEnding(menu: Menu, offers: Vector[Offered]): Boolean =
    offers.forall(offered => !offered.option.available ||
      offered.entry.procedure == menu.ending)

  def build(menu: Menu, phasePowers: PhasePowers, powers: WalkerPowers)(
      catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    val phase = ready.game.current.turn.phase
    for
      _ <- Either.cond(args.isEmpty, (), OathViolation.InvalidEventOrder(
        s"the ${menu.phase.key} menu takes no start selection"))
      _ <- Either.cond(phase == menu.phase, (),
        OathViolation.WrongPhase(menu.phase, phase))
    yield
      val listed = offers(menu, catalog, ready, actor, phasePowers, powers)
      Sequence(Vector(
        Decide(decisionId, actor, DecisionQuery.ChooseOne(
          listed.map(_.option), heading = Some(menu.heading)),
          presentation = DecisionPresentation.Panel),
        Branch((_, pending) => listed.find(offered =>
          picked(pending, offered.option.ref)).map(offered =>
          QueueProcedure(offered.entry)).toVector)))

  private def picked(pending: PendingTree, ref: DecisionOptionRef): Boolean =
    pending.answered.exists:
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(answer), _) =>
        answer == ref
      case _ => false

  /** Why an option cannot start, in the player's words. */
  private def reason(procedure: StartableRef, violation: OathViolation)
      : String = (procedure, violation) match
    case (_, OathViolation.ResourceUnavailable(_, resource)) =>
      s"No ${resource.key} at your site"
    case (_, _: OathViolation.EnemyPawnBlocksTakeWealth) =>
      "Another player's pawn is at your site"
    case (ActionRef.TakeWealth, _: OathViolation.PowerAlreadyUsed) =>
      "Already taken from this site this turn"
    case (ActionRef.TakeWealth, _) => "Cannot take wealth now"
    case (_: ActionRef.UsePower, _: OathViolation.PowerAlreadyUsed) =>
      "Already used this turn"
    case (_: ActionRef.UsePower, _) => "Nothing to do now"
    case _ => "Cannot end the phase now"
```

Check these against the code:

- `DecisionQuery.ChooseOne`'s second field is `heading: Option[String]`.
- `Decide` takes `presentation` by name.
- `Branch`'s function returns `Vector[Operation]`. If `.toVector` gives a `Vector[QueueProcedure]` the compiler refuses, write `.toVector[Operation]`.
- In Scala 3, the enum cases may refer to `Phase.Wake` inside an enum that also has a case named `Wake`, because the reference is qualified. If the compiler reports an ambiguity, write `oathdigital.model.Phase.Wake`.

- [ ] **Step 8: Register it**

In `WalkerProcedureRegistry.scala`:

- `lookup` gains two arms beside `FollowUpOffer`:

  ```scala
      case TriggeredProcedureRef.WakeMenu =>
        Right(menuEntry(PhaseMenu.Menu.Wake, powers, walkerPowers))
      case TriggeredProcedureRef.RestMenu =>
        Right(menuEntry(PhaseMenu.Menu.Rest, powers, walkerPowers))
  ```

- add, after `followUpOfferEntry`:

  ```scala
  /** A phase menu lists the phase powers and checks each option's start,
    * which needs the walk's powers. The driver starts it, so it has no
    * fallback kind, no modifier window and no start selection. */
  private def menuEntry(menu: PhaseMenu.Menu, phasePowers: PhasePowers,
      powers: WalkerPowers): Entry = Entry(
    fallbackKind = None,
    rollDecisionId = None,
    modifierWindow = None,
    build = PhaseMenu.build(menu, phasePowers, powers),
    rebuild = PhaseMenu.build(menu, phasePowers, powers))
  ```

- `isRegistered` also accepts `procedure` when `PhaseMenu.asking(procedure).nonEmpty`;
- import `oathdigital.gameplay.phases.PhaseMenu`.

Route the builds:

- `OathRulesWalker.buildWalker`: add `TriggeredProcedureRef.WakeMenu | TriggeredProcedureRef.RestMenu` to both patterns that hold `FollowUpOffer`.
- `WalkerDecisionProjector.tree`: add them to its pattern that holds `FollowUpOffer`.

In `WalkerProcedureRegistrySuite` and `ProcedureRefSuite`, wherever `FollowUpOffer` is listed as a triggered procedure or as built outside `entries`, list `WakeMenu` and `RestMenu` beside it.

- [ ] **Step 9: The Game Log**

In `ActionLines.lines`, the arm for `TriggeredProcedureRef.FollowUpOffer` becomes:

```scala
      // A question whose pick starts its own walk, which writes the lines.
      case TriggeredProcedureRef.FollowUpOffer |
          TriggeredProcedureRef.WakeMenu | TriggeredProcedureRef.RestMenu =>
        Vector.empty
```

Keep the comment slice 1 wrote above the arm if it says the same thing. Any other match over `TriggeredProcedureRef` that the compiler reports as non-exhaustive gets the two cases in the arm that holds `FollowUpOffer`.

- [ ] **Step 10: Run the rules tests**

Run: `./sbtw "testOnly oathdigital.gameplay.PhaseMenuSuite"`
Expected: PASS.

If "Take Wealth already used at this site says so" shows "Cannot take wealth now", check what `WalkerSimulation.refusal` returns for `TakeWealthLimit`'s restriction, and match it in `reason`.

- [ ] **Step 11: The service and the Game Log tests**

Create `src/test/scala/oathdigital/application/PhaseMenuServiceSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.phases.wake.TakeWealthProcedure
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, MenuPlay, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** A phase menu through the service (walker-driven game loop design,
  * rulings 11 and 12). */
class PhaseMenuServiceSuite extends munit.FunSuite:
  private val ancientCity = CatalogNames.site("Ancient City")
  private val favor = PhaseMenu.takeWealth(WakeResource.Favor)

  /** A fresh service at p1's Wake, the Wake menu parked, one favor to
    * take at Ancient City. */
  private def waking(using munit.Location)
      : (GameApplicationService, InMemoryEventStreamRepository) =
    Table.start.pawn(p1, at = ancientCity).pawn(p2, at = "Broken Peaks")
      .pawn(p3, at = "Buried Giant").turn(p1, Phase.Wake)
      .siteTokens(ancientCity, favor = 1, secrets = 0)
      .phaseMenuParked.service()

  test("other commands wait for the menu"):
    val started = waking._1.handle("game", 0L, GameCommand.StartWalker(
      ActionRef.TakeWealth, StartPayload(p1,
        TakeWealthProcedure.selection(WakeResource.Favor))))
    assertEquals(started.left.toOption.collect {
      case CommandRejected(violation) => violation
    }, Some(OathViolation.InvalidEventOrder("a walker procedure is " +
      "pending; only walker resume commands are legal")))

  test("Undo after a pick returns to the menu"):
    val (service, _) = waking
    val taken = accepted(service.handle("game", 0L,
      MenuPlay.picking(p1, favor)))
    val undone = accepted(service.handle("game", taken.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(MenuPlay.parked(undone.state), Some(PhaseMenu.Menu.Wake))
```

Use the game id `Table.service` begins, as `FollowUpServiceSuite` does. If the Undo's result is not the state Undo returns to, read what `UndoServiceSuite` asserts and match it.

In `LogScripts.scala`, after `recordedEventsBoard`, add:

```scala
  /** p1's Wake with one favor at Ancient City and nothing else to take. */
  private def wealthTable: Table = tableWithOnePawnPerSite
    .turn(p1, Phase.Wake).siteTokens("Ancient City", favor = 1, secrets = 0)

  /** p1 takes the favor by picking it from the Wake menu. */
  def wealthFromMenu(using munit.Location): Script =
    val (service, waking) = atTable("menu-wealth",
      wealthTable.phaseMenuParked)
    waking.after(MenuPlay.picking(p1,
      PhaseMenu.takeWealth(WakeResource.Favor)))
    Script("menu-wealth", service, p1)

  /** The same take, started directly, as before the menus. */
  def wealthStarted(using munit.Location): Script =
    val (service, waking) = atTable("started-wealth", wealthTable)
    waking.after(GameCommand.StartWalker(ActionRef.TakeWealth,
      StartPayload(p1, TakeWealthProcedure.selection(WakeResource.Favor))))
    Script("started-wealth", service, p1)
```

Import `PhaseMenu`, `TakeWealthProcedure` and `MenuPlay`.

Create `src/test/scala/oathdigital/application/gamelog/GameLogPhaseMenuSuite.scala`:

```scala
package oathdigital.application.gamelog

import LogScripts._

/** A phase menu's walk in the Game Log (walker-driven game loop design,
  * ruling 12). */
class GameLogPhaseMenuSuite extends munit.FunSuite:
  test("a pick writes nothing: a take from the menu reads as a direct take"):
    assertEquals(texts(format(wealthFromMenu, None)),
      texts(format(wealthStarted, None)))
```

- [ ] **Step 12: Run everything**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS. The driver does not park a menu yet, so no existing test changes beyond the reference lists of Step 8.

- [ ] **Step 13: Commit**

```bash
git add docs/superpowers/specs/2026-10-09-walker-game-loop-design.md \
  src/main/scala/oathdigital/model/ProcedureRef.scala \
  src/main/scala/oathdigital/gameplay/phases/PhaseMenu.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala \
  src/main/scala/oathdigital/gameplay/OathRulesWalker.scala \
  src/main/scala/oathdigital/application src/test/scala
git status --short
git commit -m "feat(walker): the Wake and Rest menus as walker procedures"
```

---

### Task 2: The driver parks the menu

`OathDriver.phaseEnd` starts the phase's menu, or the ending when it is the only option available. A test situation settles at a menu.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/OathDriver.scala`
- Modify: `src/test/scala/oathdigital/testkit/Situation.scala`, `SituationDriver.scala`
- Modify: `src/test/scala/oathdigital/gameplay/OathDriverSuite.scala`
- Modify tests: those listed in Step 6

**Interfaces:**
- Consumes: Task 1's `PhaseMenu`, `MenuPlay` and `Table.phaseMenuParked`.
- Produces, in tests: `Situation.finishingRest(player)`. `Situation.endingWake(player)` keeps its name and picks End Wake from a parked menu.

- [ ] **Step 1: Write the failing driver tests**

Add to `OathDriverSuite`:

```scala
  test("a Wake with an option left parks the Wake menu"):
    // A favor at p2's and p3's sites, so whoever wakes next is asked.
    val start = table.siteTokens(brokenPeaks, favor = 1)
      .siteTokens("Buried Giant", favor = 1).state
    val rested = accepted(rules.startWalker(start,
      PhaseTransitionRef.BeginRest, p1))
    assertEquals(current(rested.state).turn.phase, Phase.Wake)
    assertEquals(MenuPlay.parked(rested.state), Some(PhaseMenu.Menu.Wake))
    assertEquals(replayed(start, rested.events), rested.state)

  test("Wake ends by itself once End Wake is the only option left"):
    // p1 takes Ancient City's only favor. Take Wealth is then spent at this
    // site, and p1 has no WAKE power.
    val start = table.turn(p1, Phase.Wake)
      .siteTokens(ancientCity, favor = 1, secrets = 0).phaseMenuParked.state
    val taken = accepted(MenuPlay.picked(rules, start, p1,
      PhaseMenu.takeWealth(WakeResource.Favor)))
    assert(taken.events.contains(
      WalkerCompleted(PhaseTransitionRef.EndWake)), taken.events)
    assertEquals(current(taken.state).turn.phase, Phase.Act)

  test("Rest asks while a REST power is usable, and finishes by itself " +
      "once it is used"):
    val withPowers = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
      phasePowerCatalog = PhasePowerCatalog.default(catalog),
      walkerDice = WalkerDiceFixture.blanks)
    val start = table.adviser(p1, "Insomnia").state
    val rested = accepted(withPowers.startWalker(start,
      PhaseTransitionRef.BeginRest, p1))
    assertEquals(MenuPlay.parked(rested.state), Some(PhaseMenu.Menu.Rest))
    val insomnia = DecisionOptionRef.Denizen(
      CatalogNames.denizen("Insomnia"))
    val used = accepted(MenuPlay.picked(withPowers, rested.state, p1,
      insomnia))
    assert(used.events.contains(
      WalkerCompleted(PhaseTransitionRef.FinishRest)), used.events)

  test("a menu's walk leaves no boundary, and Act parks nothing yet"):
    val start = table.turn(p1, Phase.Wake).phaseMenuParked.state
    val ended = accepted(MenuPlay.picked(rules, start, p1,
      PhaseMenu.Menu.Wake.endingOption))
    assertEquals(boundaries(ended.events), 0)
    assertEquals(current(ended.state).turn.phase, Phase.Act)
    assertEquals(current(ended.state).walkerPending, None)
```

Imports: `oathdigital.gameplay.phases.PhaseMenu`, `oathdigital.gameplay.powers.PhasePowerCatalog` and `oathdigital.testkit.MenuPlay`.

If the take in "Wake ends by itself" starts the Oathkeeper walk on this table, state the title holder so the take changes nothing: read `OathkeeperRules.outcome` for the table and report what you set.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.OathDriverSuite"`
Expected: FAIL: "a Wake with an option left parks the Wake menu" finds no menu parked, and "Rest asks while a REST power is usable" finds no menu parked. The other two may pass already.

- [ ] **Step 3: The driver starts the menu**

In `OathDriver.scala`, replace `phaseEnd`, `phasePowerUsable` and `wakeOptionOpen` with:

```scala
  /** Step 3: the phase's menu (walker-driven game loop design, "The
    * menus"). When its ending is the only option available, the ending
    * starts without asking (ruling 3; slice 1, plan-time ruling 2). Act
    * parks nothing until its menu exists (slice 4). */
  private def phaseEnd(transition: OathTransition)
      : Either[OathViolation, OathTransition] = transition.state match
    case Ready(ready) if ready.game.current.result.isEmpty =>
      val player = ready.game.current.turn.activePlayer
      PhaseMenu.of(ready.game.current.turn.phase) match
        case Some(menu) if PhaseMenu.onlyEnding(menu, PhaseMenu.offers(menu,
            catalog, ready, player, phasePowerCatalog, walkerPowers)) =>
          joined(transition, startWalker(transition.state, menu.ending,
            player))
        case Some(menu) => startTriggered(transition, menu.procedure)
        case None => Right(transition)
    case _ => Right(transition)
```

Update the class doc's step 3: "the phase's menu: in Wake and Rest, the menu, or the phase's ending when it is the only option available (ruling 3). Act parks nothing until its menu exists (slice 4)."

Imports: add `oathdigital.gameplay.phases.PhaseMenu`. Remove `PhasePowerProcedure` and `TakeWealthProcedure` if nothing else in the file uses them; `ForcedWakeProcedure` stays.

- [ ] **Step 4: A test situation settles at a menu**

In `SituationDriver.scala`, in `answerParks`, both reads of `parkedNode(situation.state)` (before the loop and at its end) become:

```scala
parkedNode(situation.state).fold(failed, identity).filterNot(settled)
```

and add, in the trait:

```scala
  /** A phase menu is where a situation rests: it asks what the player does
    * next, which the test states itself (walker-driven game loop, slice 2
    * plan-time ruling 8). */
  private def settled(node: ParkedNode): Boolean = node match
    case ParkedNode.Decision(procedure, _, _) =>
      PhaseMenu.asking(procedure).nonEmpty
    case _ => false
```

The `run` doc's "until nothing is parked" becomes "until nothing is parked but a phase menu".

In `Situation.scala`, replace `endingWake` with:

```scala
  /** Ends `player`'s Wake: picks End Wake from the Wake menu, or starts it
    * on a table where no menu is parked. Does nothing once Wake has ended
    * by itself for having nothing to decide. */
  def endingWake(player: PlayerId)(using munit.Location): Situation =
    ending(PhaseMenu.Menu.Wake, player)

  /** Finishes `player`'s Rest, as `endingWake` ends Wake. */
  def finishingRest(player: PlayerId)(using munit.Location): Situation =
    ending(PhaseMenu.Menu.Rest, player)

  private def ending(menu: PhaseMenu.Menu, player: PlayerId)(
      using munit.Location): Situation =
    if ready.game.current.turn.phase != menu.phase then this
    else after(MenuPlay.ending(state, menu, player))
```

Import `PhaseMenu` in both files.

- [ ] **Step 5: Run the driver suite**

Run: `./sbtw "testOnly oathdigital.gameplay.OathDriverSuite oathdigital.gameplay.PhaseMenuSuite"`
Expected: PASS.

- [ ] **Step 6: Run everything and update the expected changes**

First, convert every use of the two commands Task 3 deletes, whether or not its test fails:

- `situation.after(GameCommand.EndWake(p))` becomes `situation.endingWake(p)`;
- `situation.after(GameCommand.FinishRest(p))` becomes `situation.finishingRest(p)`;
- a `service.handle(id, n, GameCommand.EndWake(p))` becomes `service.handle(id, n, MenuPlay.ending(state, PhaseMenu.Menu.Wake, p))`, where `state` is the state that command runs on; `FinishRest` likewise with `Menu.Rest`;
- `SituationDriver`'s arms for `GameCommand.EndWake` and `FinishRest` stay until Task 3.

`grep -rn "GameCommand.EndWake\|GameCommand.FinishRest" src/test/scala` then shows only `SituationDriver`, `PendingWalkerInvariantSuite` and `MembershipAuthorizationServiceSuite`. Task 3 deletes those.

Run: `./sbtw "test" "frontend/test"`

These failures are expected. Update each test and list it in your report:

- **The menu is parked.** A test that reads `walkerPending` or `walkerProcedure` as empty in Wake or Rest, after the driver ran, now sees the phase's menu. `WakeAutoEndSuite`'s "a Wake with wealth to take waits for the player" is one: assert `MenuPlay.parked(...) == Some(PhaseMenu.Menu.Wake)`.
- **Starting refused.** A test that starts Take Wealth, End Wake, Finish Rest, or a WAKE or REST power's Use Power, on a state the driver left in Wake or Rest with options open, is now refused with "a walker procedure is pending" or "already pending". Pick the same thing from the menu: `MenuPlay.picked` through the rules, `MenuPlay.picking` through the service. On a hand-built table with no menu parked, leave `startWalker` as it is.
- **The menu's park.** A test that asserts a command's exact `events` now sees the menu's `WalkerParked` after a walk that leaves Wake or Rest open.
- **Situations stop at the menu.** A situation step in Wake or Rest that used to run on now stops at the menu. Add the step that picks what the test meant: `endingWake`, `finishingRest`, or `after(MenuPlay.picking(...))`.
- **Legal controls.** In a projection with the menu parked, the active player's legal controls are `resolveWalkerDecision`, and `phase` is `"wake-menu-walker-decision"` or `"rest-menu-walker-decision"`. `PhasePowerProjectorSuite`'s Rest tests are among these. A REST power now shows as a menu option, so rewrite them over `MenuPlay.options`.
- **The first Wake.** `Situation.wake` now holds the Wake menu parked when that Wake has an option. `FirstGameSetupFixture.setupThroughWake`'s callers may see it.

Any other failure: stop and report.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/OathDriver.scala \
  src/test/scala
git status --short
git commit -m "feat(walker): the driver parks the Wake and Rest menus"
```

---

### Task 3: The Wake and Rest commands and buttons go

Nothing can send End Wake or Finish Rest now, and no projection in Wake or Rest is left without a parked decision. The commands, the controls and the frontend's Wake and Rest buttons go. The menu is drawn by the generic choose-one panel.

**Files:**
- Modify: `src/main/scala/oathdigital/application/GameCommands.scala`, `Authorization.scala`, `GameIntentMapper.scala`, `GameApplicationService.scala`, `LegalActionProjector.scala`
- Modify: `src/main/scala/oathdigital/gameplay/phases/wake/TakeWealthProcedure.scala`, `phases/PhasePowerProcedure.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandIntents.scala`, `CommandIntentCodec.scala`, `CommandIntentDecoders.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala`, `ServerUiSupport.scala`, `PhasePowerButtons.scala`, `WalkerPanelSupport.scala`
- Modify tests: `SituationDriver`, `PendingWalkerInvariantSuite`, `MembershipAuthorizationServiceSuite`, `TakeWealthProcedureSuite`, `EndWakeProcedureSuite`, `GameApplicationServiceProjectionSuite`, `TrustedSeatRoutesSuite`, `CommandProtocolSuite`, `ViewerPresentationSuite`, `PhasePowerButtonsSuite`, `WalkerChoicePanelRenderSuite`

**Interfaces:**
- Consumes: Tasks 1 and 2.
- Produces: no new names. Deletes `GameCommand.EndWake`, `GameCommand.FinishRest`, `GameIntent.EndWake`, `GameIntent.FinishRest`, `Authorization.endWake`, `Authorization.finishRest`, `TakeWealthProcedure.candidates`, `ServerUiSupport.takeWealthActions`, `ServerUiSupport.TakeWealthAction`, `PhasePowerButtons.render` and `PhasePowerButtons.showsFinishRest`.

- [ ] **Step 1: Write the failing frontend test**

Add to `WalkerChoicePanelRenderSuite`:

```scala
  test("a card option shows its rules text on hover, and a reason wins"):
    val card = CardDetails("97", "denizen", "Insomnia",
      rulesText = Some("**REST:** Gain [secret]."))
    val power = DecisionOptionState("denizen", "97", "Insomnia", Some(card))
    val used = power.copy(state = "unavailable",
      reason = Some("Already used this turn"))
    def titles(option: DecisionOptionState) =
      all(draw(WalkerDecisionState("rest-menu", "phase.menu", "decide",
        query = Some(DecisionQueryState.ChooseOne(Vector(option))))),
        ".walker-choice").map(_.getAttribute("title"))
    assertEquals(titles(power), Vector("**REST:** Gain [secret]."))
    assertEquals(titles(used), Vector("Already used this turn"))
```

Check `CardDetails`'s and `DecisionOptionState`'s parameter names and order in `frontend/.../GameProjection` or wherever the frontend aliases them. If `DecisionQueryState.ChooseOne` needs a heading, pass `heading = None`.

Run: `./sbtw "frontend/testOnly oathdigital.frontend.WalkerChoicePanelRenderSuite"`
Expected: FAIL: the first assertion finds a null title.

- [ ] **Step 2: The hover**

In `WalkerPanelSupport.renderChooseOnePanel`, after `choose.disabled = !canControl`:

```scala
      // A card option reads its rules text on hover, as the Act panel's
      // power buttons do; `withholds` below puts a reason in its place.
      option.card.filter(_ => option.badge.isEmpty).flatMap(_.rulesText)
        .foreach(choose.setAttribute("title", _))
```

Run the suite again. Expected: PASS.

- [ ] **Step 3: Delete the commands and intents**

- `GameCommands.scala`: delete `EndWake` and `FinishRest`, and their arms in `issuer` and any other match.
- `Authorization.scala`: delete `endWake` and `finishRest`.
- `GameIntentMapper.scala`: delete the `GameIntent.EndWake` and `GameIntent.FinishRest` arms.
- `GameApplicationService.scala`: delete the `GameCommand.EndWake` and `GameCommand.FinishRest` routes.
- `shared/.../CommandIntents.scala`: delete `case object EndWake` and `case object FinishRest`. In `CommandIntentCodec.scala` and `CommandIntentDecoders.scala`, delete their arms.
- `src/test/.../testkit/SituationDriver.scala`: delete the two arms.
- `PendingWalkerInvariantSuite` and `MembershipAuthorizationServiceSuite`: delete the two commands from their lists. If `MembershipAuthorizationServiceSuite` needs a command a seat may send, use `GameCommand.BeginRest(PlayerId("p1"))`.
- `CommandProtocolSuite`'s actor-injection test and `TrustedSeatRoutesSuite.originGame`: the intent `{"type":"endWake"}` becomes `{"type":"beginRest"}`, another intent with no fields. Update the `originGame` doc, which names "an endWake command".

- [ ] **Step 4: Delete the legal controls**

In `LegalActionProjector.scala`:

- the `Phase.Wake` and `Phase.Rest` arms of `controls` go. The remaining arm reads:

  ```scala
        // Wake and Rest are answered at their menus (walker-driven game
        // loop design, "The menus"), so a live game there always has a
        // decision parked.
        case Phase.Wake | Phase.Rest | Phase.Setup | Phase.RoundEnd |
            Phase.WarExhaustion => Vector.empty
  ```

- delete `takeableResources` and `takeControl`, and the import of `TakeWealthProcedure`.

In `TakeWealthProcedure.scala`, delete `candidates` and its doc, and the imports only it used (`WalkerPowers`, `WalkerSimulation`). In `selection`'s doc, delete any sentence that names `candidates` or the legal-action projection.

In `PhasePowerProcedure.scala`'s object doc, the sentence "`usable` is the single usability function: the start gate, legal controls, the `phasePowers` projection and the Rest auto-skip all ask it." becomes "`usable` lists the powers the Act panel offers. Wake and Rest powers are offered by their phase's menu, which runs each start's own check."

Tests:

- `TakeWealthProcedureSuite`: the `candidates` test and the legal-controls test become one test over the Wake menu. For each board they used, the menu's favor and secret options are available exactly when `take(...)` with that resource is accepted. Read the options with `MenuPlay.options(Table.from(ready).phaseMenuParked.state, catalog)`, and the take from the board without the menu.
- `EndWakeProcedureSuite`'s legal-controls test: End Wake's menu option is available exactly when `endWake(state, p1)` is accepted, read the same way.
- `GameApplicationServiceProjectionSuite`'s Wake test: build the table with `.phaseMenuParked`. Assert `own.phase == "wake-menu-walker-decision"` and that `own.legalControls` contains `"resolveWalkerDecision"`. End Wake with `MenuPlay.ending(table.state, PhaseMenu.Menu.Wake, active)`.

- [ ] **Step 5: Delete the frontend's Wake and Rest buttons**

In `ActionDecisionRenderer.scala`:

- in `status`, `case None if value.phase == "act-action-selection" || value.phase == "wake" => ()` becomes `case None if value.phase == "act-action-selection" => ()`, and the comment above it says "The Game Log names the phase and the controls below say what can be done, so the Act phase prints no instruction of its own.";
- delete the arm `case None if value.phase == "rest" =>` and its line;
- delete the block `if value.phase == "wake" && presentation.showGameplayControls then`, from the comment above it ("The oath and the usurper limit describe the table") to the End Wake button;
- delete the block `if value.phase == "rest" && presentation.showGameplayControls then` at the end.

In `ServerUiSupport.scala`, delete `TakeWealthAction`, `takeWealth` and `takeWealthActions`, and any import only they used.

In `PhasePowerButtons.scala`:

- delete `showsFinishRest` and `render`;
- the object doc becomes "One button per legal phase power in the Act panel. Wake and Rest powers are options of their phase's menu.";
- `appendTo`'s doc drops "instead of standing loose next to the one that ends the Act".

Tests:

- `ViewerPresentationSuite`: delete each test that calls `takeWealthActions`, and any helper only they used.
- `PhasePowerButtonsSuite`: delete the `showsFinishRest` test. The `actions` test stays.

- [ ] **Step 6: Run everything**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS. Expected count changes:
- `WalkerChoicePanelRenderSuite`: one test added.
- `ViewerPresentationSuite` and `PhasePowerButtonsSuite`: tests deleted.
- `TakeWealthProcedureSuite`: two tests become one.

Report the counts.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital src/test/scala \
  shared/src/main/scala/oathdigital/protocol \
  shared/src/test/scala/oathdigital/protocol \
  frontend/src/main/scala/oathdigital/frontend \
  frontend/src/test/scala/oathdigital/frontend
git status --short
git commit -m "feat(walker): Wake and Rest are played at their menus"
```

---

### Task 4: Docs, and the gates

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-walker-game-loop-design.md`
- Modify: `CONTEXT.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: The glossary**

In `CONTEXT.md`, under "### Walker", after **Parked decision**, add:

```markdown
**Phase menu**:
The parked decision over everything the active player may do now in Wake or
Rest: each option starts one procedure, and the phase's ending is always the
last. It is asked only when more than the ending is available; otherwise the
phase ends by itself.
_Avoid_: action list, phase controls, wake actions
```

Under "### Game", in **Situation**:

- "Wake (Setup complete: the first player's turn, already in Act when that Wake had nothing to decide)" becomes "Wake (Setup complete: the first player's Wake menu, or their Act when that Wake had nothing to decide)";
- add, after the sentence that ends "in order.": "A situation settles at a phase menu: it stops there, unanswered, because the menu asks what the player does next."

- [ ] **Step 2: The roadmap and the spec's status**

In `docs/ROADMAP.md`, under "### Phase - Walker-driven game loop", delete the line "- [ ] Slice 2: the Wake and Rest menus."

In the spec, replace the status paragraph with:

```markdown
**Status:** designed 2026-10-09. Slices 1 and 2 have landed; slice 3 is
ready to plan.
```

- [ ] **Step 3: The gates**

Run, in order:

1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass. Compare the test counts with the baseline and report every test added and deleted.

- [ ] **Step 4: Commit, and merge**

```bash
git add CONTEXT.md docs/ROADMAP.md \
  docs/superpowers/specs/2026-10-09-walker-game-loop-design.md
git status --short
git commit -m "docs: record walker game loop slice 2"
```

Then run `ExitWorktree` with `keep`. In the main checkout:
1. Check `git log --oneline -1`.
2. Merge the branch into local `main` (`git merge --ff-only <branch>`, or a merge commit when `main` moved).
3. Run gate 1 again on the merge.

Do not push.
