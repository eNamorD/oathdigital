# Modifier Selection on the Walker, Slice 3 (Muster and Trade) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Muster or a Trade asks for its modifiers inside the walk, as its first decision, instead of taking them with the start.

**Architecture:**

- **Slice 1 built the machinery; this slice moves Muster and Trade onto it.** No walker file changes. The node (`ModifierChoice`), the picks vocabulary (`ModifierPicks`), the powers that follow the answers (`WalkerPowers.withAnswers`), the start refusal (`choosesModifiers`) and the Game Log's pick reading are all on `main`.
- **Both actions move together.** Muster and Trade build their trees through one shared object, `EconomyTree`. Its tree now starts with `ModifierChoice.loop`. What follows, the source decision and the payment and gain, becomes `afterModifiers`. `Kind` gains the modifier window and the start label: "Start Muster", "Start Trade". Trade's resource stays a start selection; the modifier decision comes after it.
- **"Start" names a shortfall of Supply.** Muster and Trade register no option pricing, so their source decision keeps showing only the sources a start could complete. For "Start"'s reason alone, `EconomyTree` passes `ModifierChoice.loop` a pricing of the source decision through `PricedOptions`. At 0 Supply, "Start Muster" reads "Needs 1 Supply; you have 0". With no source at all, it reads "Cannot start now".
- **The Act panel previews what follows the decision.** `MusterProcedure.startOptions` and `TradeProcedure.startOptions` preview `afterModifiers`, through a new `EconomyTree.startOptions`. Previewing the whole tree would park on the modifier decision whenever the actor holds a Muster or Trade modifier.
- **Paying moves, but nothing pays.** All 17 Muster and Trade modifiers are free, so joining `SelectedModifier.paidAtPick` changes no payment. It stops the eligibility-window payment Transform for any costed modifier added later.
- **Tests that start with modifiers pick them instead.**
  - `ModifierPlay.started` gains `startArgs`, for Trade's resource.
  - `EconomyActions`, which most economy power suites drive through, starts through `ModifierPlay.started`. With no modifier given, it answers "Start" when the decision is asked.
  - Suites that start directly convert the same way.
  - Some tests expected a start refused with `NoPlayableOption` while the actor held a modifier that would make it playable. Those starts are now accepted and park on the modifier decision, with "Start" unavailable. They assert that park instead.
- **Two suites used Muster only as an action that takes start modifiers.** `SelectionPaymentsSuite` moves to Recover. `EnclosingProcedureSuite` starts its Muster with an automatic spy.
- **The client starts Muster and Trade directly,** without the modifier flow. Slice 2's frontend tests used Muster as their stand-in for an action still in the flow; they move to Recover.
- **The Game Log needs no code change.** The Muster and Trade tag already waits for the cost step, after the source decision, by which every pick is made. This slice adds a test that proves it.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-10-09-modifier-walker-design.md`. Read it whole, including its "Plan-time rulings". The slice 2 plan, `docs/superpowers/plans/2026-10-09-modifier-walker-slice2.md`, shows how Travel moved. This plan copies that move for Muster and Trade. The files to copy from are:

- `SearchProcedure` (`gameplay/actions/search/`).
- `TravelModifierChoiceSuite` (`src/test/scala/oathdigital/gameplay/powers/travel/`).
- The Search and Travel tests of `ModifierStartControlsSuite`.

**Starts from:** `main` at `5ae1e668` (the slice 2 merge) or later. Slice 2 changes several files this plan changes again: `SelectedModifier.paidAtPick`, `SelectedModifierSuite`, `ModifierStartControlsSuite`, `LogScripts`, `UndoServiceSuite`, `ModifierFlowDraft` and the frontend flow suites. The steps below describe those files as slice 2 leaves them. Where slice 2's executor deviated from its plan, adapt the edit and say so in the commit message.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build.
  - Several edits below remove the last use of an import (`Refusals`, `WalkerSimulation`, `ChooseOneAnswer`) or of a private helper (`assertStarts`). Delete it when the compiler says it is unused.
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
- **No compatibility for parked saves** (spec ruling 10). A journal parked mid-Muster or mid-Trade before this change fails to resume.
- **Texts, exactly:**
  - The start buttons: "Start Muster", "Start Trade".
  - Start's reason at 0 Supply with a source at the site: "Needs 1 Supply; you have 0".
  - The start refusals: "muster chooses its modifiers inside the walk", "trade chooses its modifiers inside the walk".
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end. This plan adds 11 server tests and 2 frontend tests.
- **The verification gate** (roadmap standing rule), run in Task 3:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
  4. `git diff --check main...HEAD`

---

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `src/main/scala/oathdigital/gameplay/actions/economy/EconomyTree.scala` | Modify | The tree starts with the modifier decision; `afterModifiers`; `startOptions`; Start's reason |
| `src/main/scala/oathdigital/gameplay/actions/economy/MusterProcedure.scala`, `TradeProcedure.scala` | Modify | Their `Kind`s name the modifier window and start label; `startOptions` previews what follows the decision |
| `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` | Modify | Muster and Trade `choosesModifiers` |
| `src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala` | Modify | Muster and Trade join `paidAtPick` |
| `src/test/scala/oathdigital/gameplay/ModifierPlay.scala` | Modify | `started` takes `startArgs` |
| `src/test/scala/oathdigital/testkit/EconomyActions.scala` | Modify | Picks modifiers through the decision; `parkedDecision`, `optionStatesAtStart` |
| `src/test/scala/oathdigital/gameplay/powers/economy/MusterModifierChoiceSuite.scala`, `TradeModifierChoiceSuite.scala` | Create | The Muster and Trade modifier decisions |
| Economy power suites and others | Modify | Listed in Task 1 |
| `src/test/scala/oathdigital/application/UndoServiceSuite.scala` | Modify | Undo of a Muster pick, then of the start |
| `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`, `GameLogStartLineSuite.scala` | Modify | `golemMuster` picks Golem Legions; the start line names it |
| `frontend/src/main/scala/oathdigital/frontend/ModifierFlowDraft.scala` | Modify | Muster and Trade start directly |
| Frontend suites | Modify | Listed in Task 3 |
| `docs/ROADMAP.md`, `CONTEXT.md` | Modify | Slice 3 checked; flow note |

---

### Task 1: Muster and Trade choose their modifiers in the walk

This task turns the switch and fixes every suite the switch breaks, so that its commit is green.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/economy/EconomyTree.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/economy/MusterProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/economy/TradeProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala` (the `ActionRef.Muster` and `ActionRef.Trade` entries)
- Modify: `src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala`
- Modify: `src/test/scala/oathdigital/gameplay/ModifierPlay.scala`
- Modify: `src/test/scala/oathdigital/testkit/EconomyActions.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/economy/MusterModifierChoiceSuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/economy/TradeModifierChoiceSuite.scala`
- Modify, under `src/test/scala/oathdigital/gameplay/powers/economy/`: `AwaitedReturnSuite`, `DefameSuite`, `GolemLegionsSuite`, `KnightsErrantSuite`, `MapLibrarySuite`, `MovingMarketSuite`, `OldSongsSuite`, `PressgangsSuite`, `SkilledMerchantsSuite`, `VowOfPovertySuite`
- Modify: `src/test/scala/oathdigital/gameplay/EnclosingProcedureSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/SelectionPaymentsSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/SelectedModifierSuite.scala`
- Modify: `src/test/scala/oathdigital/application/ModifierStartControlsSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (`golemMuster` only)

**Interfaces:**
- Consumes, from slice 1:
  - `ModifierChoice.loop(actor, window, rest, startLabel, explain)`, `ModifierChoice.cannotStart`.
  - `PricedOptions.priced(state, tree, pending, powers, decide, fallback, explain)`.
  - `ModifierPicks.start`, `ModifierPicks.decisionId(window)`.
  - `ModifierPlay.started`, `ModifierPlay.pick(power)`, `ModifierPlay.answers(window, modifiers)`.
  - `ParkedNode.of(state, catalog, powers)`, `PhasePowerHarness.queryOf(transition, viewer)`.
- Produces:
  - `EconomyTree.startOptions(catalog, state, actor, kind, powers): Vector[PreviewedOption]`.
  - `ModifierPlay.started(rules, state, procedure, actor, window, modifiers, powers, startArgs: Vector[DecisionOptionRef] = Vector.empty)`.
  - `EconomyActions.parkedDecision(state: OathState): Decide`.
  - `EconomyActions.optionStatesAtStart(rules: OathRules, ready: ReadyGame, actor: PlayerId, action: ActionRef, startArgs: Vector[DecisionOptionRef] = Vector.empty): Option[Vector[(String, String)]]`.

The economy suites below are listed from an inventory of every test that starts a Muster or Trade. Most go through `EconomyActions` and need no edit of their own. Each edit below names the test it changes.

- [ ] **Step 1: Let `ModifierPlay.started` carry start arguments**

In `src/test/scala/oathdigital/gameplay/ModifierPlay.scala`, change `started`'s doc comment and signature, and pass `startArgs` to the start:

```scala
  /** `actor` starts `procedure` with `startArgs`, picks `modifiers` in
    * order and starts. With no modifier, "Start" is answered only when the
    * decision was asked. The events are every command's. */
  def started(rules: OathRules, state: OathState, procedure: StartableRef,
      actor: PlayerId, window: PowerWindow, modifiers: Vector[PowerId],
      powers: WalkerPowers = production,
      startArgs: Vector[DecisionOptionRef] = Vector.empty)
      : Either[OathViolation, OathTransition] =
```

and, inside it:

```scala
      begun <- rules.startWalker(state, procedure, actor,
        startArgs = startArgs)
```

- [ ] **Step 2: Drive `EconomyActions` through the decision**

Replace `src/test/scala/oathdigital/testkit/EconomyActions.scala` with:

```scala
package oathdigital.testkit

import oathdigital.gameplay.{ModifierPlay, OathRules}
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Muster and Trade driven through the rules to the end: start, pick the
  * modifiers in the modifier decision and start the action, then answer the
  * source decision. Each fails with the violation or the state it got. */
object EconomyActions:
  /** A completed economy action and the game it left. `journal` carries every
    * event of the action, but its `state` is the started one, not the end's.
    */
  final case class Completed(journal: OathTransition, result: ReadyGame)

  /** `actor` Musters from `card`, picking `selected`. */
  def muster(rules: OathRules, ready: ReadyGame, actor: PlayerId,
      selected: Vector[PowerId], card: DecisionOptionRef)(
      using munit.Location): Completed =
    complete(rules, Unwrap.accepted(musterStarted(rules, ready, actor,
      selected)), actor, MusterProcedure.decisionId, card)

  /** `actor` Trades with `card` for `resource` ("favor" or "secret"),
    * picking `selected`. */
  def trade(rules: OathRules, ready: ReadyGame, actor: PlayerId,
      selected: Vector[PowerId], resource: String, card: DecisionOptionRef)(
      using munit.Location): Completed =
    complete(rules, Unwrap.accepted(tradeStarted(rules, ready, actor,
      selected, resource)), actor, TradeProcedure.decisionId, card)

  private def complete(rules: OathRules, started: OathTransition,
      actor: PlayerId, decisionId: String, card: DecisionOptionRef)(
      using munit.Location): Completed =
    val done = Unwrap.accepted(rules.resolveWalker(started.state, actor,
      decisionId, DecisionAnswer.ChooseOneAnswer(card)))
    Completed(started.copy(events = started.events ++ done.events),
      Outcomes.ready(done.state))

  /** The Trade [[trade]] drives, except that a refusal at any step comes
    * back as the violation instead of failing the test. */
  def attemptTrade(rules: OathRules, ready: ReadyGame, actor: PlayerId,
      selected: Vector[PowerId], resource: String, card: DecisionOptionRef)
      : Either[OathViolation, OathTransition] =
    for
      started <- tradeStarted(rules, ready, actor, selected, resource)
      done <- rules.resolveWalker(started.state, actor,
        TradeProcedure.decisionId, DecisionAnswer.ChooseOneAnswer(card))
    yield done

  /** The Muster [[muster]] drives, except that a refusal at any step comes
    * back as the violation instead of failing the test. */
  def attemptMuster(rules: OathRules, ready: ReadyGame, actor: PlayerId,
      selected: Vector[PowerId], card: DecisionOptionRef)
      : Either[OathViolation, OathTransition] =
    for
      started <- musterStarted(rules, ready, actor, selected)
      done <- rules.resolveWalker(started.state, actor,
        MusterProcedure.decisionId, DecisionAnswer.ChooseOneAnswer(card))
    yield done

  /** `actor` starts a Muster and picks `selected`, in order, in its
    * modifier decision. */
  private def musterStarted(rules: OathRules, ready: ReadyGame,
      actor: PlayerId, selected: Vector[PowerId])
      : Either[OathViolation, OathTransition] =
    ModifierPlay.started(rules, Ready(ready), ActionRef.Muster, actor,
      PowerWindow.MusterModifierSelection, selected)

  /** `actor` starts a Trade for `resource` and picks `selected`, in order,
    * in its modifier decision. */
  private def tradeStarted(rules: OathRules, ready: ReadyGame,
      actor: PlayerId, selected: Vector[PowerId], resource: String)
      : Either[OathViolation, OathTransition] =
    ModifierPlay.started(rules, Ready(ready), ActionRef.Trade, actor,
      PowerWindow.TradeModifierSelection, selected,
      startArgs = Vector(DecisionOptionRef.Button(resource)))

  /** The decision a walk at `state` is parked on, rebuilt as the projector
    * rebuilds it, with the production powers. */
  def parkedDecision(state: OathState)(using munit.Location): Decide =
    ParkedNode.of(state, catalog, WalkerPowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide
      case other =>
        munit.Assertions.fail(s"expected a parked decision, got $other")

  /** Each option's id and state, as the projector shows them, of the
    * decision `actor`'s start of `action` at `ready` parks on. For a Muster
    * or Trade with a modifier to pick, "start" comes first. */
  def optionStatesAtStart(rules: OathRules, ready: ReadyGame, actor: PlayerId,
      action: ActionRef, startArgs: Vector[DecisionOptionRef] = Vector.empty)(
      using munit.Location): Option[Vector[(String, String)]] =
    PhasePowerHarness.queryOf(Unwrap.accepted(rules.startWalker(Ready(ready),
      action, actor, startArgs = startArgs)), actor)
      .map(_.offeredOptions.map(option => option.id -> option.state))
```

- [ ] **Step 3: Write the failing Muster suite**

Create `src/test/scala/oathdigital/gameplay/powers/economy/MusterModifierChoiceSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.{SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, EconomyActions, Look, Outcomes,
  Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, rejected}

/** Muster asks for its modifiers inside the walk (modifier walker design,
  * slice 3). */
class MusterModifierChoiceSuite extends munit.FunSuite:
  import EconomyActions.parkedDecision
  import SearchFixture.rules

  private val playmates = CatalogNames.denizen("Animal Playmates")
  /** A plain Beast denizen to muster on: it has no Muster or Trade power. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val home = "Ancient City"
  private val modifierDecisionId =
    ModifierPicks.decisionId(PowerWindow.MusterModifierSelection)
  private val startMuster: DecisionOption =
    DecisionOption.Button(ModifierPicks.start, "Start Muster")

  /** p1 at Ancient City beside Errand Boy, with 4 favor and `supply`
    * Supply, holding Animal Playmates faceup when `withPlaymates`. */
  private def besideErrandBoy(supply: Int, withPlaymates: Boolean = true)
      : ReadyGame =
    val table = Table.start.pawn(p1, at = home).denizen(errandBoy, at = home)
      .favor(p1, 4).supply(p1, supply)
    (if withPlaymates then table.adviser(p1, playmates) else table).ready

  private def started(ready: ReadyGame): OathTransition =
    accepted(rules.startWalker(Ready(ready), ActionRef.Muster, p1))

  private def options(state: OathState)(using munit.Location)
      : Vector[DecisionOption] = parkedDecision(state).query match
    case DecisionQuery.ChooseOne(listed, _) => listed
    case other => fail(s"expected a choose-one, got $other")

  test("a Muster starts on the modifier decision when a modifier can be " +
      "picked"):
    val begun = started(besideErrandBoy(supply = 7))
    assertEquals(parkedDecision(begun.state).decisionId, modifierDecisionId)
    assertEquals(options(begun.state), Vector(startMuster,
      DecisionOption.Denizen(DecisionOptionRef.Denizen(playmates))))

  test("with no modifier to pick, a Muster starts on its source"):
    val begun = started(besideErrandBoy(supply = 7, withPlaymates = false))
    assertEquals(parkedDecision(begun.state).decisionId,
      MusterProcedure.decisionId)

  test("Start says why the Muster cannot go on, until a pick waives the " +
      "Supply"):
    val begun = started(besideErrandBoy(supply = 0))
    assertEquals(options(begun.state).head, DecisionOption.Unavailable(
      startMuster, OptionReason.Written("Needs 1 Supply; you have 0")))
    val picked = accepted(rules.resolveWalker(begun.state, p1,
      modifierDecisionId, ModifierPlay.pick(AnimalPlaymates.id)))
    assertEquals(parkedDecision(picked.state).decisionId,
      MusterProcedure.decisionId)
    val done = accepted(rules.resolveWalker(picked.state, p1,
      MusterProcedure.decisionId, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Denizen(errandBoy))))
    assertEquals(Look(Outcomes.ready(done.state)).tokensOn(errandBoy),
      Tokens(1, 0))

  test("a start may no longer carry modifiers for a Muster"):
    assertEquals(rejected(rules.startWalker(Ready(besideErrandBoy(7)),
      ActionRef.Muster, p1, Vector(AnimalPlaymates.id))),
      OathViolation.InvalidEventOrder(
        "muster chooses its modifiers inside the walk"))

  test("the start control previews the sources, not the modifier " +
      "decision"):
    val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
      Vector.empty)
    assertEquals(MusterProcedure.startOptions(catalog,
      besideErrandBoy(supply = 7), p1, powers).map(_.option.ref),
      Vector[DecisionOptionRef](DecisionOptionRef.Denizen(errandBoy)))
```

- [ ] **Step 4: Write the failing Trade suite**

Create `src/test/scala/oathdigital/gameplay/powers/economy/TradeModifierChoiceSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, EconomyActions, Look, Outcomes,
  Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, rejected}

/** Trade asks for its modifiers inside the walk, after its resource
  * (modifier walker design, slice 3). */
class TradeModifierChoiceSuite extends munit.FunSuite:
  import EconomyActions.parkedDecision
  import SearchFixture.rules

  private val birdsong = CatalogNames.denizen("Birdsong")
  /** A plain Beast denizen to trade with: it has no Muster or Trade power. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val home = "Ancient City"
  private val modifierDecisionId =
    ModifierPicks.decisionId(PowerWindow.TradeModifierSelection)
  private val startTrade: DecisionOption =
    DecisionOption.Button(ModifierPicks.start, "Start Trade")

  /** p1 at Ancient City beside Errand Boy, with 4 favor, 2 faceup secrets
    * and `supply` Supply, holding Birdsong faceup when `withBirdsong`. */
  private def besideErrandBoy(supply: Int, withBirdsong: Boolean = true)
      : ReadyGame =
    val table = Table.start.pawn(p1, at = home).denizen(errandBoy, at = home)
      .favor(p1, 4).secrets(p1, faceUp = 2).supply(p1, supply)
    (if withBirdsong then table.adviser(p1, birdsong) else table).ready

  private def started(ready: ReadyGame, resource: String): OathTransition =
    accepted(rules.startWalker(Ready(ready), ActionRef.Trade, p1,
      startArgs = Vector(DecisionOptionRef.Button(resource))))

  private def options(state: OathState)(using munit.Location)
      : Vector[DecisionOption] = parkedDecision(state).query match
    case DecisionQuery.ChooseOne(listed, _) => listed
    case other => fail(s"expected a choose-one, got $other")

  test("a Trade starts on the modifier decision, and Start keeps its " +
      "resource"):
    val begun = started(besideErrandBoy(supply = 7), "secret")
    assertEquals(parkedDecision(begun.state).decisionId, modifierDecisionId)
    assertEquals(options(begun.state), Vector(startTrade,
      DecisionOption.Denizen(DecisionOptionRef.Denizen(birdsong))))
    val going = accepted(rules.resolveWalker(begun.state, p1,
      modifierDecisionId, ModifierPlay.start))
    val source = parkedDecision(going.state)
    assertEquals(source.decisionId, TradeProcedure.decisionId)
    assertEquals(source.query match
      case DecisionQuery.ChooseOne(_, heading) => heading
      case _ => None,
      Some("Choose a card to Trade for secrets"))

  test("with no modifier to pick, a Trade starts on its source"):
    val begun = started(besideErrandBoy(supply = 7, withBirdsong = false),
      "favor")
    assertEquals(parkedDecision(begun.state).decisionId,
      TradeProcedure.decisionId)

  test("Start says why the Trade cannot go on, until a pick waives the " +
      "Supply"):
    val begun = started(besideErrandBoy(supply = 0), "favor")
    assertEquals(options(begun.state).head, DecisionOption.Unavailable(
      startTrade, OptionReason.Written("Needs 1 Supply; you have 0")))
    val picked = accepted(rules.resolveWalker(begun.state, p1,
      modifierDecisionId, ModifierPlay.pick(Birdsong.id)))
    val done = accepted(rules.resolveWalker(picked.state, p1,
      TradeProcedure.decisionId, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Denizen(errandBoy))))
    // Trade for favor pays one secret onto the card.
    assertEquals(Look(Outcomes.ready(done.state)).tokensOn(errandBoy),
      Tokens(0, 1))

  test("a start may no longer carry modifiers for a Trade"):
    assertEquals(rejected(rules.startWalker(Ready(besideErrandBoy(7)),
      ActionRef.Trade, p1, Vector(Birdsong.id),
      Vector(DecisionOptionRef.Button("favor")))),
      OathViolation.InvalidEventOrder(
        "trade chooses its modifiers inside the walk"))
```

- [ ] **Step 5: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.MusterModifierChoiceSuite oathdigital.gameplay.powers.economy.TradeModifierChoiceSuite"`

Expected:
- "with no modifier to pick" (both suites) and "the start control previews the sources" pass already.
- The six others fail: the start parks on the source, and a start with modifiers is accepted.

- [ ] **Step 6: Start the economy tree with the modifier decision**

Replace `src/main/scala/oathdigital/gameplay/actions/economy/EconomyTree.scala` with:

```scala
package oathdigital.gameplay.actions.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.OathLifecycle
import oathdigital.gameplay.walker.{ModifierChoice, PricedOptions,
  WalkerPowers, WalkerSimulation}
import oathdigital.gameplay.walker.WalkerSimulation.PreviewedOption
import oathdigital.model._

/** The tree Muster and Trade share (modifier walker design for its first
  * node):
  *
  * {{{
  * Sequence(
  *   Repeat(ModifierChoice),                 // the modifiers, one per pass,
  *                                           // then "Start Muster" or
  *                                           // "Start Trade"
  *   Sequence(                               // window = <Action>ActionEligibility
  *     Decide(source),                       // window = <Action>SourceSelection
  *     Branch { after the answer:
  *       Sequence(PayCost, SpendSupply(1)),  // window = <Action>Cost
  *       Sequence(the gain) }))              // window = <Action>Gain
  * }}}
  *
  * The modifiers come first. Each pick pays at once, and a picked modifier
  * then folds at the windows after the decision. The start carries no
  * selection of modifiers; Trade's resource is still its start selection.
  *
  * The source is a decision, not a start argument, so a power can add to or
  * remove from the offered cards through the decision's window. The gain is a
  * concrete operation inside its own windowed node, not an opaque `BuildOps`,
  * so a power can see and rewrite the requested amount; it is requested
  * unclamped and the best-effort `Gain` takes what the supply or bank holds.
  * Legacy computed the gain from the state before the cost, and no cost feeds
  * a gain input, so it is built from the state at the `Branch`.
  */
private[economy] object EconomyTree:

  /** What differs between Muster and the two Trades. */
  final case class Kind(
      decisionId: String,
      heading: String,
      modifiers: PowerWindow,
      startLabel: String,
      eligibility: PowerWindow,
      sourceSelection: PowerWindow,
      cost: PowerWindow,
      gain: PowerWindow,
      payment: Cost,
      yields: (PlayerId, MusterSource, Int, ForceKind) => Option[CoreOperation])

  /** Fresh start: the lifecycle gate, a pawn site, a resolvable ruler for
    * every site.
    */
  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      kind: Kind): Either[OathViolation, Operation] =
    gated(state, actor).map(_ => tree(catalog, state, actor, kind))

  /** Every source the actor could draw on now under `powers`, each previewed
    * through what follows the modifier decision. Empty when the action cannot
    * start at all. The start control and the parked decision read it.
    */
  def startOptions(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, kind: Kind, powers: WalkerPowers)
      : Vector[PreviewedOption] =
    gated(state, actor).map(_ => afterModifiers(catalog, state, actor, kind))
      .flatMap(WalkerSimulation.preview(_, state, powers))
      .getOrElse(Vector.empty)

  private def gated(state: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Unit] = for
    _ <- OathLifecycle.validateAct(OathState.Ready(state), actor)
    _ <- state.game.current.players.find(_.player == actor).flatMap(_.pawnSite)
      .toRight(OathViolation.PawnSiteMissing(actor))
    _ <- siteRulers(state)
  yield ()

  /** Every site's forces must name a ruler the game can identify. Legacy
    * Economy refused a board where one did not, and nothing else rejects an
    * unknown or duplicated lineage on a site, so the check survives as a start
    * gate.
    */
  private def siteRulers(state: ReadyGame): Either[OathViolation, Unit] =
    state.game.current.map.sites.valuesIterator
      .map(site => SiteRule.ruler(site.forces, state.game.current.players))
      .collectFirst { case Left(error) => OathViolation.UnsupportedEconomyState(
        s"invalid site ruler mapping: $error") }
      .toLeft(())

  /** The same tree without the start-only gates, for a resume. */
  def tree(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      kind: Kind): Operation =
    val rest = afterModifiers(catalog, state, actor, kind)
    Sequence(Vector(ModifierChoice.loop(actor, kind.modifiers, rest,
      kind.startLabel, Some(startReason)), rest))

  /** The source pick, then the payment and the gain. */
  private def afterModifiers(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, kind: Kind): Operation = Sequence(Vector[Operation](
    Decide(kind.decisionId, actor, DecisionQuery.ChooseOne(
      MusterSource.atSite(catalog, state, actor),
      heading = Some(kind.heading)), window = Some(kind.sourceSelection)),
    Branch((ready, pending) => answered(pending, kind.decisionId).toVector
      .flatMap(ref => afterSource(catalog, ready, actor, kind, ref)
        .fold(error => Vector[Operation](fail(error)), identity)))),
    Some(kind.eligibility))

  /** Why "Start" cannot go on: the source decision priced, so that a
    * shortfall of Supply is named ("Needs 1 Supply; you have 0"). The parked
    * source decision itself still shows only the sources a start could
    * complete. */
  private def startReason(state: ReadyGame, tree: Operation,
      pending: PendingTree, powers: WalkerPowers, decide: Decide)
      : Vector[DecisionOption] =
    PricedOptions.priced(state, tree, pending, powers, decide,
      fallback = ModifierChoice.cannotStart)
```

Keep `afterSource`, `answered` and `fail` below it as they are.

- [ ] **Step 7: Name the window and label in each `Kind`**

In `MusterProcedure.scala`:

- Change the walker import to `import oathdigital.gameplay.walker.WalkerPowers`. Keep the `PreviewedOption` import.
- Replace the doc comment's last sentence with "Takes no start selection; the modifiers are the `muster.modifiers` decision and the card is the `muster.source` decision."
- Replace `kind` and `startOptions`:

```scala
  private val kind = EconomyTree.Kind(decisionId,
    "Choose a card to Muster from", PowerWindow.MusterModifierSelection,
    "Start Muster", PowerWindow.MusterActionEligibility,
    PowerWindow.MusterSourceSelection, PowerWindow.MusterCost,
    PowerWindow.MusterGain, Cost(favor = 1),
    (actor, _, matching, force) => Some(Gain.Warbands(actor, force, 1 + matching)))
```

```scala
  /** Every source the actor could Muster from now, each previewed through
    * what follows the modifier decision. Empty when the action cannot start
    * at all. This is the single definition the start control and the parked
    * decision read.
    */
  def startOptions(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, powers: WalkerPowers): Vector[PreviewedOption] =
    EconomyTree.startOptions(catalog, state, actor, kind, powers)
```

In `TradeProcedure.scala`:

- Change the walker import to `import oathdigital.gameplay.walker.WalkerPowers`. Keep the `PreviewedOption` import.
- Change the doc comment's last sentence to "Both spend one Supply, and both ask for their modifiers in the `trade.modifiers` decision, then draw on the `trade.source` decision."
- Replace `kind`'s two cases and `startOptions`:

```scala
  private def kind(resource: TradeResource): EconomyTree.Kind = resource match
    case TradeResource.Favor => EconomyTree.Kind(decisionId,
      "Choose a card to Trade for favor", PowerWindow.TradeModifierSelection,
      "Start Trade", PowerWindow.TradeActionEligibility,
      PowerWindow.TradeSourceSelection, PowerWindow.TradeCost,
      PowerWindow.TradeGain, Cost(secret = 1),
      (actor, source, matching, _) =>
        Some(Gain.Favor(actor, source.suit, 1 + matching)))
    case TradeResource.Secret => EconomyTree.Kind(decisionId,
      "Choose a card to Trade for secrets", PowerWindow.TradeModifierSelection,
      "Start Trade", PowerWindow.TradeActionEligibility,
      PowerWindow.TradeSourceSelection, PowerWindow.TradeCost,
      PowerWindow.TradeGain, Cost(favor = 1, favorBurnt = 1),
      (actor, _, matching, _) =>
        Option.when(matching > 0)(Gain.Secrets(actor, matching)))
```

```scala
  /** As [[MusterProcedure.startOptions]], for one resource. */
  def startOptions(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, resource: TradeResource,
      powers: WalkerPowers): Vector[PreviewedOption] =
    EconomyTree.startOptions(catalog, state, actor, kind(resource), powers)
```

- [ ] **Step 8: Register the switch and the payment**

In `WalkerProcedureRegistry.scala`, replace the doc comment above `ActionRef.Muster` with:

```scala
    /** Economy. Muster and Trade ask for their modifiers, then park on their
      * source (modifier walker design). Nothing runs before those decisions,
      * so the start is previewed through the playable-option gate. Their
      * source decision shows only the sources a start could complete; it is
      * not priced.
      */
```

Add `choosesModifiers = true` to both the `ActionRef.Muster` and the `ActionRef.Trade` entries, after `requiresPlayableOption = true`.

In `SelectedModifier.scala`:

```scala
  val paidAtPick: Set[MajorActionType] = Set(MajorActionType.Search,
    MajorActionType.Travel, MajorActionType.Muster, MajorActionType.Trade)
```

In its trait doc comment, the first bullet under "The payment is `selectionPayments`" names the actions that pay at the pick. Make it read "A Search, Travel, Muster or Trade modifier is paid when it is picked in the walk's modifier decision (`paidAtPick`)."

- [ ] **Step 9: Run the new suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.MusterModifierChoiceSuite oathdigital.gameplay.powers.economy.TradeModifierChoiceSuite"`

Expected: PASS, 9 tests.

- [ ] **Step 10: Convert the economy power suites**

Each edit below replaces a direct start that carried modifiers, or a test that the new decision changes. The suites not listed drive through `EconomyActions` and need no edit.

**Suites whose helper started with modifiers.** In each, replace the `rules.startWalker` call with `ModifierPlay.started`, keeping the rest of the helper. Add `import oathdigital.gameplay.ModifierPlay`.

- `DefameSuite.tradedForFavorWithMercenaries`, `MovingMarketSuite.tradedWithMarket`, `MapLibrarySuite.tradeStarted`, `OldSongsSuite.tradeStarted`, `SkilledMerchantsSuite.tradeStarted`, and the start in `VowOfPovertySuite`'s "Moving Market's favor is not from Trade, so its holder still gains it". Each becomes, with its own modifiers value and resource:

```scala
    accepted(ModifierPlay.started(rules, Ready(ready), ActionRef.Trade, p1,
      PowerWindow.TradeModifierSelection, modifiers,
      startArgs = Vector(DecisionOptionRef.Button("favor"))))
```

  `MapLibrarySuite` names the vector `selected`. `VowOfPovertySuite` passes `Vector(MovingMarket.id)` and the button `"secret"`.

- `GolemLegionsSuite.musterStarted`, `PressgangsSuite.musterStarted` and `KnightsErrantSuite.musterFrom`. Each start becomes:

```scala
    accepted(ModifierPlay.started(rules, Ready(state), ActionRef.Muster, p1,
      PowerWindow.MusterModifierSelection, selected))
```

  Use each helper's own names for the state and the vector. `KnightsErrantSuite` already imports from `oathdigital.gameplay`; make it `import oathdigital.gameplay.{ModifierPlay, OathRules, WalkerDiceFixture}`.

These helpers also serve tests that pass no modifiers: `OldSongsSuite`'s "without it, the card with one secret is shown unavailable and refused", `PressgangsSuite`'s "without it, a card holding favor or secrets is shown unavailable and refused" and `KnightsErrantSuite`'s `afterMuster`. A source is playable in each, so "Start" is available, `ModifierPlay.started` answers it, and the walk parks on the source as before.

**`GolemLegionsSuite`, the first test.** No card stands at the site, so the start no longer fails: it parks on the modifier decision until Golem Legions is picked. Replace the test with:

```scala
  test("picked, it offers Golem Legions as a Muster source, so a Muster " +
      "with no card at the pawn site can start"):
    val ready = holdingGolem.ready
    // Nothing can be mustered on until Golem Legions is picked.
    assertEquals(EconomyActions.optionStatesAtStart(rules, ready, p1,
      ActionRef.Muster), Some(Vector("start" -> "unavailable",
      golem.value -> "available")))
    assert(controls(ready).contains("beginMuster"), controls(ready))
    assertEquals(PhasePowerHarness.offered(musterStarted(ready), p1),
      Some(Vector("denizen" -> golem.value)))
```

**`PressgangsSuite`, "with only such a card at the site, Muster starts only with it selected".** Replace it with:

```scala
  test("with only such a card at the site, Muster cannot start until it is " +
      "picked"):
    val ready = occupiedAlchemist.ready
    assertEquals(states(accepted(rules.startWalker(Ready(ready),
      ActionRef.Muster, p1))), Some(Vector("start" -> "unavailable",
      pressgangs.value -> "available")))
    assert(controls(ready).contains("beginMuster"), controls(ready))
    assertEquals(states(musterStarted(ready, selected)),
      Some(Vector(alchemist.value -> "available")))
```

**`PressgangsSuite`, "with Golem Legions, Golem Legions is mustered on twice in one turn".** Replace the middle assertion, the refused start with Golem Legions alone, with:

```scala
    // Golem Legions now holds favor, so with it picked alone the Muster
    // cannot start: Start waits for Pressgangs.
    val again = accepted(rules.startWalker(Ready(first.result),
      ActionRef.Muster, p1))
    val golemPicked = accepted(rules.resolveWalker(again.state, p1,
      musterModifiers, ModifierPlay.pick(GolemLegions.id)))
    assertEquals(states(golemPicked), Some(Vector(
      "start" -> "unavailable", pressgangs.value -> "available")))
```

and add to the suite:

```scala
  private val musterModifiers =
    ModifierPicks.decisionId(PowerWindow.MusterModifierSelection)
```

with `import oathdigital.gameplay.powerresolver.ModifierPicks`.

**`AwaitedReturnSuite`, "it lets a player with no Supply trade".** Replace the refused `attemptTrade` assertion with:

```scala
    // Without it the Trade cannot start: Start waits for the pick.
    assertEquals(EconomyActions.optionStatesAtStart(rules, noSupply, p1,
      ActionRef.Trade, Vector(DecisionOptionRef.Button("favor"))),
      Some(Vector("start" -> "unavailable", awaited.value -> "available")))
```

`wrestlersCard` stays, for the `trade` call after it.

- [ ] **Step 11: Move the two suites that only borrowed Muster**

**`EnclosingProcedureSuite`.** Its second test started a Muster with a selected spy that has no card, which a Muster can no longer pick. Make the spy a factory, keep the selected one for the first test, and walk the second with an automatic one:

```scala
  /** Spy of ContributingPower with `chosen` resolution: records (window,
    * procedure) in `seen` each time it is asked if it applies, at the Muster
    * windows; always applicable. */
  private def spy(name: String, chosen: PowerResolution): ContributingPower =
    new ContributingPower:
      def id: PowerId = PowerId(name)
      def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
      override def resolution: PowerResolution = chosen
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
        PowerWindow.MusterSourceSelection -> Vector.empty,
        PowerWindow.MusterCost -> Vector.empty)
      override def applicable(ctx: PowerCtx): Boolean =
        seen += ctx.window -> ctx.procedure
        true

  /** Selected, so it is asked at the Muster modifier window. */
  private val probe = spy("test.enclosing-procedure",
    PowerResolution.PlayerSelected)
  /** Automatic, so a Muster walks it without a pick: it has no card to be
    * picked by. */
  private val walked = spy("test.enclosing-procedure.walked",
    PowerResolution.Automatic)

  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowers(Vector(probe)))
  private val walkingRules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowers(Vector(walked)))
```

In the second test, start with `walkingRules.startWalker(Ready(staged), ActionRef.Muster, p1)` and resolve with `walkingRules.resolveWalker(...)`. Its assertions stay.

**`SelectionPaymentsSuite`.** Muster pays at the pick now, so a joint refusal at selection is Recover's alone. Move the suite's helper onto a Recover from a relic site:

```scala
  /** p1 starts a Recover at Fair Isle, which holds a facedown relic, with
    * `secrets` faceup secrets, selecting `selected`. Only the selected
    * powers' secrets are in question. */
  private def recoverSelecting(secrets: Int, selected: ContributingPower*)
      : Either[OathViolation, OathTransition] =
    val rules = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowers(Vector(a, b, free)),
      walkerDice = WalkerDiceFixture.blanks)
    val fixture = CatacombsGame.relicSite()
    val ready = Table.from(fixture.ready)
      .secrets(fixture.actor, faceUp = secrets).ready
    rules.startWalker(Ready(ready), ActionRef.Recover, fixture.actor,
      selected.map(_.id).toVector)
```

- Rename every `muster(` call in the first three tests to `recoverSelecting(`.
- Drop the now unused `p1` import.
- Change the suite's doc comment to "Selecting modifiers for an action that still takes them at its start refuses a combination the player cannot pay, at selection, because every selected modifier pays at the start of its action. Search, Travel, Muster and Trade pay at each pick instead (modifier walker design)."

If `CatacombsGame.relicSite()` turns out to give a Recover that the start refuses for reasons of its own, use the fixture the Catacombs tests at the end of the suite start their Recover from, and say so in the commit message.

- [ ] **Step 12: Move the payment-window test off Muster**

In `SelectedModifierSuite`, slice 2 made "a cost not paid at the pick is paid at the root of the action's tree, and only for a costed modifier" use Muster. Replace it with:

```scala
  test("a cost not paid at the pick is paid at the root of the action's " +
      "tree, and only for a costed modifier"):
    val recover: Set[MajorActionType] = Set(MajorActionType.Recover)
    val priced = Probe(card, Cost(favor = 1), recover)
    assertEquals(priced.contributions.keySet,
      Set[PowerWindow](PowerWindow.RecoverActionEligibility))
    assertEquals(Probe(card, Cost.free, recover).contributions,
      Map.empty[PowerWindow, Vector[Contribution]])
    // Search, Travel, Muster and Trade pay at the pick, in the modifier
    // decision.
    val picked = Probe(card, Cost(favor = 1), Set(MajorActionType.Search,
      MajorActionType.Travel, MajorActionType.Muster, MajorActionType.Trade))
    assertEquals(picked.contributions,
      Map.empty[PowerWindow, Vector[Contribution]])
```

Slice 2 also moved "a payment and an effect at the same window keep the payment first" onto Muster. Move it to Recover the same way: its stub's `actions` become `Set(MajorActionType.Recover)`, each `PowerWindow.MusterActionEligibility` becomes `PowerWindow.RecoverActionEligibility`, and its comment says a Recover modifier.

- [ ] **Step 13: Start the Act panel's Muster and Trade through the decision**

In `ModifierStartControlsSuite`, replace the first Muster test with:

```scala
  test("Muster is offered at 0 Supply when Animal Playmates waives it; the " +
      "start asks for the modifier, and picking it reaches the source"):
    val ready = beastMuster(playmates = true)
    assert(controls(ready, p1).contains("beginMuster"))
    val started = accepted(rules.startWalker(OathState.Ready(ready),
      ActionRef.Muster, p1))
    val modifiers = parked(started.state)
    assertEquals(modifiers.decisionId, musterModifiers)
    assertEquals(modifiers.query match
      case DecisionQuery.ChooseOne(options, _) => options.headOption
      case _ => None,
      Some(DecisionOption.Unavailable(
        DecisionOption.Button(ModifierPicks.start, "Start Muster"),
        OptionReason.Written("Needs 1 Supply; you have 0"))))
    val picked = accepted(rules.resolveWalker(started.state, p1,
      musterModifiers, ModifierPlay.pick(AnimalPlaymates.id)))
    assertEquals(parked(picked.state).decisionId, MusterProcedure.decisionId)

  private val musterModifiers =
    ModifierPicks.decisionId(PowerWindow.MusterModifierSelection)
```

and the first Trade test with:

```scala
  test("Trade is offered at 0 Supply when Birdsong waives it; the start " +
      "asks for the modifier, and picking it reaches the source"):
    val ready = beastTrade(birdsong = true)
    assert(controls(ready, p1).contains("beginTradeFavor"))
    val started = accepted(rules.startWalker(OathState.Ready(ready),
      ActionRef.Trade, p1, startArgs = Vector(DecisionOptionRef.Button(
        "favor"))))
    val modifiers = parked(started.state)
    assertEquals(modifiers.decisionId, tradeModifiers)
    assertEquals(modifiers.query match
      case DecisionQuery.ChooseOne(options, _) => options.headOption
      case _ => None,
      Some(DecisionOption.Unavailable(
        DecisionOption.Button(ModifierPicks.start, "Start Trade"),
        OptionReason.Written("Needs 1 Supply; you have 0"))))
    val picked = accepted(rules.resolveWalker(started.state, p1,
      tradeModifiers, ModifierPlay.pick(Birdsong.id)))
    assertEquals(parked(picked.state).decisionId, TradeProcedure.decisionId)

  private val tradeModifiers =
    ModifierPicks.decisionId(PowerWindow.TradeModifierSelection)
```

Add `import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}`. The suite's `parked` helper, from slice 1, serves every action. `assertStarts` is now unused: delete it.

- [ ] **Step 14: Pick Golem Legions in the Game Log script**

In `LogScripts.golemMuster`, replace the start with a pick through the decision:

```scala
  /** p1 musters on Golem Legions, their adviser, with no card at their site.
    * The Muster picks Golem Legions, its only modifier, so it starts by
    * itself. */
  def golemMuster(using munit.Location): Script =
    val (service, act) = atTable("golem-muster", tableWithOnePawnPerSite
      .adviser(p1, "Golem Legions").banditsRefilled)
    act.withAnswers(ModifierPlay.answers(PowerWindow.MusterModifierSelection,
      Vector(GolemLegions.id)))
      .after(GameCommand.StartWalker(ActionRef.Muster,
        StartPayload(p1, Vector.empty)))
    Script("golem-muster", service, p1)
```

`LogScripts` already imports `ModifierPlay`. If `StartPayload` needs its third argument here, pass `Vector.empty` for it too.

- [ ] **Step 15: Run every server test**

Run: `./sbtw "test"`

Expected: PASS.

The goldens should not change. The `muster` and `trade` scripts hold no Muster or Trade modifier, so their walks ask nothing and record no step for the decision. `golemMuster` has no golden.

Some other test may start a Muster or Trade directly while its actor holds a Muster or Trade modifier, then answer the source. That walk now parks on the modifier decision first. Fix such a test as follows:
- If it starts through `EconomyActions`, `ModifierPlay.started` or a `Situation`, it is already handled, or give the `Situation` `ModifierPlay.answers(window, Vector.empty)`.
- Otherwise, answer `ModifierPlay.start` before the source.

- [ ] **Step 16: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/economy/EconomyTree.scala \
  src/main/scala/oathdigital/gameplay/actions/economy/MusterProcedure.scala \
  src/main/scala/oathdigital/gameplay/actions/economy/TradeProcedure.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala \
  src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala \
  src/test/scala/oathdigital/gameplay/ModifierPlay.scala \
  src/test/scala/oathdigital/testkit/EconomyActions.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/MusterModifierChoiceSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/TradeModifierChoiceSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/AwaitedReturnSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/DefameSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/GolemLegionsSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/KnightsErrantSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/MapLibrarySuite.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/MovingMarketSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/OldSongsSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/PressgangsSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/SkilledMerchantsSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/VowOfPovertySuite.scala \
  src/test/scala/oathdigital/gameplay/EnclosingProcedureSuite.scala \
  src/test/scala/oathdigital/gameplay/SelectionPaymentsSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/SelectedModifierSuite.scala \
  src/test/scala/oathdigital/application/ModifierStartControlsSuite.scala \
  src/test/scala/oathdigital/application/gamelog/LogScripts.scala
git commit -m "feat(economy): choose Muster and Trade modifiers inside the walk"
```

Add any other suite that Step 15 fixed to the `git add` list. End the message with your trailer.

**Checkpoint: review Task 1 before going on.**

---

### Task 2: Undo and the Game Log for a Muster pick

**Files:**
- Modify: `src/test/scala/oathdigital/application/UndoServiceSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogStartLineSuite.scala`

**Interfaces:**
- Consumes: `ModifierPlay.pick`; `LogScripts.golemMuster`, as Task 1 left it.
- Produces: nothing new.

No production code changes in this task. Both tests should pass at once. If either fails, the failure is a slice 1 gap for Muster: stop and report it rather than patching around it.

- [ ] **Step 1: Undo takes back a pick, then the start**

Add to `UndoServiceSuite`:

```scala
  test("Undo takes back a Muster modifier pick, and then the start"):
    // No card stands at p1's site, so Start waits for Golem Legions.
    val withGolem = table.adviser(p1, "Golem Legions")
    val (service, _) = withGolem.service()
    val started = accepted(service.handle("g", 0L,
      GameCommand.StartWalker(ActionRef.Muster,
        StartPayload(p1, Vector.empty, Vector.empty))))
    val picked = accepted(service.handle("g", started.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision("muster.modifiers",
        ModifierPlay.pick(GolemLegions.id)))))
    val unpicked = accepted(service.handle("g", picked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(unpicked.state, started.state)
    val undone = accepted(service.handle("g", unpicked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.state, withGolem.state)
```

Imports: `oathdigital.gameplay.powers.economy.GolemLegions`. Slice 2 added `oathdigital.gameplay.ModifierPlay`.

- [ ] **Step 2: The start line names the pick**

Add to `GameLogStartLineSuite`:

```scala
  test("a Muster with a pick names it on its own line, with no Chose line"):
    val entries = format(golemMuster, None)
    val tag = entryStarting(entries, "Muster")
    assertEquals(tag.kind, LogKind.Section)
    // Muster's printed cost; Golem Legions, a free modifier, adds nothing.
    assertEquals(text(tag), "Muster −1 Supply")
    assertEquals(text(entries(entries.indexOf(tag) + 1)),
      "With Golem Legions")
    assert(!texts(entries).exists(_.startsWith("Chose")), texts(entries))
```

- [ ] **Step 3: Run them**

Run: `./sbtw "testOnly oathdigital.application.UndoServiceSuite oathdigital.application.gamelog.GameLogStartLineSuite oathdigital.application.gamelog.GameLogActionLineSuite"`

Expected: PASS. `GameLogActionLineSuite`'s "Muster on Golem Legions: the line names the adviser as the source" also runs the converted script.

- [ ] **Step 4: Commit**

```bash
git add src/test/scala/oathdigital/application/UndoServiceSuite.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogStartLineSuite.scala
git commit -m "test(economy): undo a Muster pick, and log the Muster's picks"
```

End the message with your trailer.

---

### Task 3: The client starts Muster and Trade directly; record the slice and run the gates

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/ModifierFlowDraft.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/ModifierFlowSuite.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/ModifierFlowDrafts.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/ModifierSelectionStateSuite.scala`
- Modify: `docs/ROADMAP.md`, `CONTEXT.md`

**Interfaces:** none new.

- [ ] **Step 1: Make the flow tests fail**

In `ModifierFlowSuite`, add after "a Search start goes out directly":

```scala
  test("a Muster start goes out directly"):
    val FlowUnderTest(host, ui) = flow()
    val start = GameCommand.StartWalker("muster", Vector.empty)
    ui.submitCommand(start)
    assertEquals(host.previews, Vector.empty)
    assertEquals(host.sent, Vector(start -> Vector.empty))
    assertEquals(host.currentDrafts.modifiers, None)

  test("a Trade start goes out directly, with its resource"):
    val FlowUnderTest(host, ui) = flow()
    val start = GameCommand.StartWalker("trade", Vector.empty,
      Vector(WalkerStartArgWire("button", "favor")))
    ui.submitCommand(start)
    assertEquals(host.previews, Vector.empty)
    assertEquals(host.sent, Vector(start -> Vector.empty))
    assertEquals(host.currentDrafts.modifiers, None)
```

Add `WalkerStartArgWire` to the `oathdigital.protocol` import.

Slice 2 made "confirming leaves the flow and sends the folded submission without a redraw" use a `muster` value. Make it use `recover` instead:
- Replace each `"muster"` in it with `"recover"`.
- Replace `muster` with the suite's existing `recover`, and delete the `muster` value.

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ModifierFlowSuite"`

Expected: FAIL. The two new tests see a preview request.

- [ ] **Step 2: Drop Muster and Trade from the flow**

In `ModifierFlowDraft.scala`:

```scala
  private val walkerActions: Set[String] = Set("recover", "forge",
    "challenge")
```

In its doc comment, change "Search, facedown-adviser play and Travel ask for their modifiers inside the walk" to "Search, facedown-adviser play, Travel, Muster and Trade ask for their modifiers inside the walk".

- [ ] **Step 3: Move the other drafts off Muster and Trade**

- **`ModifierFlowDrafts`:** replace each `"muster"` with `"recover"`, and "Red's Muster flow" with "Red's Recover flow".
- **`ModifierSelectionStateSuite`, `renderOrderingPanel` and "a stale context clears the flow draft":** replace `"muster"` with `"recover"` in the `MajorActionPreviewResponse`, the context and the `StartWalker`. If an expectation in the rendering tests names the action ("Muster"), change it to "Recover". Leave the `PreviewModifier`'s own action label alone.
- **`ModifierSelectionStateSuite`, the registered-key test:** expect `Vector("recover", "forge")`. Replace its comment with:

```scala
    // A registered key names its action whatever start arguments it
    // carries. Search, play-facedown-adviser, Travel, Muster and Trade
    // choose their modifiers inside the walk, and place-banner-resource is
    // not registered, so none of them names one.
```

- **`ModifierSelectionStateSuite`, "a selected game-rule modifier such as Rowdy Pub invokes without throwing":** Rowdy Pub is a Muster modifier, so the flow never carries it now. Replace the test with:

```scala
  test("a selected game-rule modifier such as Relic Worship invokes " +
      "without throwing"):
    val relicWorship = PreviewModifier("game:denizen.relic-worship",
      "denizen.relic-worship", "Relic Worship")
    val selection = ModifierSelectionState.reconcile(None,
      context.copy(action = "recover"), Vector(relicWorship),
      "recover-preview").toggle(relicWorship)
    assertEquals(selection.invocations, Vector(ModifierInvocation("game",
      "denizen.relic-worship", None, "denizen.relic-worship")))
    assertEquals(ModifierFlowDraft.submission(
      GameIntent.StartWalker("recover", Vector.empty), selection.invocations),
      GameIntent.StartWalker("recover", Vector("denizen.relic-worship")))
```

- **`ModifierSelectionStateSuite`, "a Trade walker start retains its start argument":** no action in the flow takes a start argument now, but the fold still keeps any it is given. Rename the test to "a walker start keeps its start arguments through the fold", and replace its `GameIntent.StartWalker("trade", ...)` with `GameIntent.StartWalker("recover", Vector.empty, Vector(oathdigital.protocol.WalkerStartArgWire("button", "example")))`.

Run: `./sbtw "frontend/test"`

Expected: PASS.

- [ ] **Step 4: Record the slice**

- **`docs/ROADMAP.md`:** check `- [x] Slice 3: Muster and Trade.`
- **`CONTEXT.md`, the modifier flow entry:** change "Search, facedown-adviser play and Travel have no flow" to "Search, facedown-adviser play, Travel, Muster and Trade have no flow".

- [ ] **Step 5: Run the verification gate**

1. `./sbtw "test" "frontend/test"`. Compare the counts with the baseline: server +11, frontend +2.
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
4. `git diff --check main...HEAD`

All must pass.

- [ ] **Step 6: Browser check on a scratch database**

Follow the memory recipe (`preview-start-hits-live-db`). Never use `preview_start`, and never open `var/oathdigital`.

1. Seed a scratch HSQLDB in the scratchpad from `FirstGameSetupFixture.initialSituation().endingWake(p2)`. p2 stands at Ancient City, where a first game deals no denizen. Arrange Golem Legions as p2's adviser (`Play` onto `Location.PlayArea(p2)`).
2. Run the server on port 8081 through Bash, in the background.
3. Open the game as p2 in the browser pane.
4. Check each of these:
   - The Act panel's Muster button parks on "Choose modifiers, or start", with "Start Muster" first, unavailable, and Golem Legions available.
   - Hovering "Start Muster" shows its reason.
   - Picking Golem Legions moves on to the source decision, which offers Golem Legions.
   - Undo takes back the pick.
   - A Trade button, where offered, goes to the source decision without asking for modifiers.
5. Stop the server. Delete the throwaway seeding suite.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/ModifierFlowDraft.scala \
  frontend/src/test/scala/oathdigital/frontend/ModifierFlowSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/ModifierFlowDrafts.scala \
  frontend/src/test/scala/oathdigital/frontend/ModifierSelectionStateSuite.scala \
  docs/ROADMAP.md CONTEXT.md
git commit -m "feat(frontend): start Muster and Trade without the modifier flow"
```

End the message with your trailer.

**Checkpoint: final review of the branch against the spec's slice 3.**
