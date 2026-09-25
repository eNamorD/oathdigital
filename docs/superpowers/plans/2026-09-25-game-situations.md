# Game Situations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give "a game at a known point of play" one module, `oathdigital.testkit.Situation`, with two adapters (rules and journaled), and re-express the four hand-built Setup walks and six park recipes over it, so a Setup decision's shape stops being every suite's interface.

**Architecture:** A `Situation` is reached by issuing steps (`GameCommand` or `Arrange(ops)`) and answering every parked decision they cause with a default policy or a per-decision override. The park-answer loop is shared; how a step becomes a transition is the adapter's: the rules adapter calls `OathRules` directly, the journaled adapter calls `GameApplicationService.handle`. Both need "the parked node of this state", which is extracted from `ParkedDecisionAssertions` into `ParkedNode` so the rebuild has one owner. `FirstGameSetupFixture` stays the first-game input; its `initialReady` and `execute()` become delegates.

**Tech Stack:** Scala 3.9.0, sbt via `./sbtw`, munit. Gates: `./sbtw test`, `./sbtw frontend/test`, `python3 scripts/check-architecture.py`, `python3 scripts/check-markdown-links.py`.

**Spec:** `docs/superpowers/specs/2026-09-25-game-situations-design.md`. Vocabulary: `CONTEXT.md` ("First-game input", "Situation").

## Global Constraints

- **No production behaviour changes.** Every file this plan creates or edits is under `src/test`. If a step appears to need a production edit, stop and report it.
- **Preservation.** `FirstGameSetupFixture.initialReady` is byte-identical before and after (Task 3 pins it). Migrated tests keep their exact assertions, including event counts such as `nextSequence == 21L`. A migrated test that fails is a discovered defect or a driver bug, never a reason to weaken the assertion.
- **Dependencies are accepted, not created.** `Situation` takes catalog, rules or service, dice, repository and game id as parameters; `default` companions cover the common case. No situation builds a port it was not given.
- **No new decision-id strings.** Overrides key on ids the procedures export (`SetupProcedure.pawnDecisionId`, `RecoverProcedure.choiceDecisionId`, `CampaignIds.*`).
- **Keep power names out of `src/test/scala/oathdigital/testkit/`.** The module is engine-adjacent; suites may name a power, the module may not.
- Commit messages: Conventional Commits, `test(testkit): ...` unless noted. End every commit message with the trailer line `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` — this plan's trailer overrides a worker's own model trailer.
- Run the full JVM suite (`./sbtw test`) at the end of every task, not only the touched suites: the fixture's blast radius is the whole gameplay tree.
- Line numbers are as of commit `c286cfd2` and drift as tasks run. Locate members by signature and assertions by text.
- Work in one worktree off `main`. `main` moves under this plan (other sessions merge into it); re-check `git log -1 main` before any rebase.

---

## Baseline

- [ ] **Step 0: Record the baseline**

```bash
./sbtw test
```

```bash
grep -rln 'PlayerId("p2"), PlayerId("p3"), PlayerId("p1")' src/test | wc -l
```

```bash
grep -rln 'adviserKeepKey' src/test | wc -l
```

Expected: the suite passes; the greps print `4` and `9` (the fixtures `FirstGameSetupFixture`, `ParkedServiceFixture`, `ForgeWalkerFixture`, `GameStartToWakeSuite`, `SetupProcedureSuite`, `NegotiationDealSuite`, and the three route suites). Different numbers mean the repository moved; re-measure the affected task before editing.

---

## File structure

Created:

- `src/test/scala/oathdigital/testkit/ParkedNode.scala` — the parked node rebuild.
- `src/test/scala/oathdigital/testkit/Situation.scala` — `Situation`, `Step`, `Park`, `Answers`, named situations, `seedInto`.
- `src/test/scala/oathdigital/testkit/SituationDriver.scala` — the shared loop and the two adapters.
- `src/test/scala/oathdigital/testkit/SituationSuite.scala` — the module's own tests.

Modified (all test tree):

- `gameplay/walker/ParkedDecisionAssertions.scala` — consumes `ParkedNode`.
- `gameplay/setup/SetupWalkDriver.scala` — delegates its answer policy to `Situation.defaultAnswer`.
- `gameplay/setup/FirstGameSetupFixture.scala` — `execute()` and `initialReady` delegate to `Situation.wake`.
- `gameplay/setup/GameStartToWakeSuite.scala` — second test over the driver.
- `application/ParkedServiceFixture.scala` — `setUp`, `seed` and the four parks over the journaled adapter.
- `application/GameApplicationServiceSuite.scala`, `application/WalkerDecisionProjectionSuite.scala` — call-site adjustments only.
- `application/ForgeWalkerFixture.scala` — `forgeReadyGame` and `parkedForge` over the journaled adapter.
- `server/AuthenticatedGameRoutesSuite.scala`, `server/TrustedSeatRoutesSuite.scala` — `seedInto` replaces the hand-rolled seeding.

---

### Task 1: Extract `ParkedNode` from `ParkedDecisionAssertions`

**Files:** Create `src/test/scala/oathdigital/testkit/ParkedNode.scala`; modify `src/test/scala/oathdigital/gameplay/walker/ParkedDecisionAssertions.scala:45-88`.

**Interfaces:**

```scala
package oathdigital.testkit

/** The node a Ready game's walker is parked on, rebuilt from state the way
  * the projector rebuilds it. `None` exactly when nothing is parked. Every
  * other failure to explain a set `walkerPending` is a `Left` with the reason.
  */
enum ParkedNode:
  case Decision(procedure: ProcedureRef, decide: Decide, awaiting: PlayerId)
  case Roll(procedure: ProcedureRef, pool: PoolKey, count: Int,
      decisionId: String, awaiting: PlayerId)
  def procedure: ProcedureRef
  def decisionId: String
  def awaiting: PlayerId

object ParkedNode:
  def of(state: OathState, catalog: ExecutableCatalog,
      walkerPowers: WalkerPowers = WalkerPowers.empty,
      phasePowers: PhasePowers = PhasePowers.empty)
      : Either[String, Option[ParkedNode]]
```

- [ ] **Step 1: Baseline.** `./sbtw "testOnly oathdigital.gameplay.walker.ParkedDecisionAssertionsSuite"`; require pass. Its six tests are the bracket for this task; do not edit them.

- [ ] **Step 2: Move the rebuild.** Lift the body of `ParkedDecisionAssertions.parkedDecision` (`WalkerProcedureRegistry.rebuild`, `WalkerPowers.selected(walkerPowerCatalog, current.walkerModifiers)`, `ProcedureWalker.parkedDecide` / `parkedRoll` / `awaitedPlayer`, `WalkerProcedureRegistry.rollDecisionId`) into `ParkedNode.of`. Each `fail(...)` in the original becomes a `Left(message)` carrying the same text. `ParkedNode.Decision` carries the whole `Decide` (the driver needs its `query`), not only the id.

- [ ] **Step 3: Consume it.** `ParkedDecisionAssertions.parkedDecision` becomes: `ParkedNode.of(state, catalog, walkerPowerCatalog, phasePowerCatalog)` matched as `Left(reason) => fail(reason)`, `Right(node) => node.map(n => ParkedDecisionFacts(n.procedure, n.decisionId, n.awaiting))`. The public signatures of `ParkedDecisionAssertions` do not change.

- [ ] **Step 4: Verify.** `./sbtw "testOnly oathdigital.gameplay.walker.ParkedDecisionAssertionsSuite"`, then `./sbtw test`. Commit: `test(testkit): extract ParkedNode from ParkedDecisionAssertions`.

---

### Task 2: The `Situation` module and its suite

**Files:** Create `Situation.scala`, `SituationDriver.scala`, `SituationSuite.scala` under `src/test/scala/oathdigital/testkit/`; modify `src/test/scala/oathdigital/gameplay/setup/SetupWalkDriver.scala:34-47`.

**Interfaces:**

```scala
package oathdigital.testkit

enum Step:
  case Command(command: GameCommand)
  /** Places pieces by recording one arranging WalkerStepRecorded and
    * applying it through the replay path -- ParkedServiceFixture.seed with
    * a name and a second adapter. */
  case Arrange(ops: Vector[CoreOperation], label: String = "arrange")
object Step:
  given Conversion[GameCommand, Step] = Step.Command(_)

/** What an answer policy sees: the parked Decide, the state it parks on and
  * the player it awaits. */
final case class Park(decide: Decide, ready: ReadyGame, awaiting: PlayerId,
    procedure: ProcedureRef)

type Answers = PartialFunction[Park, DecisionAnswer]

final case class Situation(state: OathState, events: Vector[OathEvent],
    nextSequence: Long, driver: SituationDriver):
  def ready: ReadyGame                       // fails if not Ready
  def after(steps: Step*): Situation         // continue, answering every park
  def parkedAfter(steps: Step*): Situation   // continue, leave the last park
  def withAnswers(answers: Answers): Situation
  def seedInto(repository: InMemoryEventStreamRepository, gameId: String): Unit

object Situation:
  /** ChooseOne: first option. Partition: first option kept in the first
    * section demanding one, the rest discarded (SetupWalkDriver's policy).
    * ChooseMany: the first `min` options. ChooseAmount: `min`. */
  val defaultAnswer: Answers
  def defaultRules(catalog: ExecutableCatalog,
      campaignDice: CampaignDicePort = CampaignDicePort.random): OathRules
  def rules(rules: OathRules, defenseDice: DefenseDicePort = DefenseDicePort.random,
      answers: Answers = defaultAnswer): SituationDriver
  def journaled(service: GameApplicationService,
      repository: InMemoryEventStreamRepository, gameId: String,
      answers: Answers = defaultAnswer): SituationDriver
  def wake(driver: SituationDriver, chronicle: Chronicle = FirstGameSetupFixture.chronicle,
      orders: SetupOrders = FirstGameSetupFixture.orders): Situation
  def act(driver: SituationDriver, actor: PlayerId, ...): Situation   // wake then EndWake(actor)
  def rest(driver: SituationDriver, actor: PlayerId, ...): Situation  // act then BeginRest(actor)

trait SituationDriver:
  def catalog: ExecutableCatalog
  def answers: Answers
  def withAnswers(answers: Answers): SituationDriver
  /** One step, no park handling. */
  protected def apply(from: Situation, step: Step): Either[OathViolation, Situation]
  /** The pool the adapter's dice roll for a Roll park. */
  protected def roll(from: Situation, node: ParkedNode.Roll): Either[OathViolation, Situation]
  protected def parkedNode(state: OathState): Either[String, Option[ParkedNode]]
  /** Shared loop: apply, then answer parks until none remain (settle = true)
    * or until the step's own parks are answered except the last. Fails via
    * munit with step index, step, decision id, form and awaited player. */
  final def run(from: Situation, steps: Seq[Step], settle: Boolean): Situation
```

- [ ] **Step 1: Write the failing suite first.** `SituationSuite` with these tests, all against `FirstGameSetupFixture.{catalog, chronicle, orders}`:

  1. `"wake under the fixture's rules equals initialReady"` — `Situation.wake(Situation.rules(new OathRules(catalog))).ready == FirstGameSetupFixture.initialReady`. Until Task 3 this compares against the old `execute()`; that is the point.
  2. `"the rules and journaled adapters reach the same ready for the same steps"` — both drivers built from `Situation.defaultRules(catalog, dice)` and `new GameApplicationService(catalog, repo, campaignDicePort = dice)` with a deterministic `CampaignDicePort`; run `wake` then `EndWake(firstPlayer)`; assert equal `ready` and equal `events`.
  3. `"an unanswerable park fails naming the decision"` — `withAnswers(PartialFunction.empty)` plus a default that throws for `Partition`; `intercept[AssertionError]` on `wake`, message contains `SetupProcedure.adviserDecisionId(orders.firstPlayer)`.
  4. `"parkedAfter leaves the last park for ParkedDecisionAssertions"` — `Situation.parkedAfter(Begin)` under the rules driver; `new ParkedDecisionAssertions(catalog).assertParked(state, TriggeredProcedureRef.Setup, SetupProcedure.pawnDecisionId(orders.firstPlayer), orders.firstPlayer)`.
  5. `"an Arrange step crosses the replay path on both adapters"` — after `wake`, `Arrange(Vector(SetOathkeeper(Some(p))))`; both adapters show `p` as Oathkeeper; the journaled one's repository holds one more record.
  6. `"a Roll park is rolled by the driver"` — mirror `ParkedServiceFixture.recoverChoicePark`: rules driver with a dice port that always rolls blank; `parkedAfter(EndWake(actor), StartWalker(ActionRef.Recover, StartPayload(actor)))` under the same Chronicle rotation that fixture uses; `assertParked(..., ActionRef.Recover, RecoverProcedure.choiceDecisionId, actor)`.

  Run `./sbtw "testOnly oathdigital.testkit.SituationSuite"`; require compilation failure.

- [ ] **Step 2: `Situation.scala`.** `Step`, `Park`, `Answers`, `Situation`, `defaultAnswer`, `defaultRules` (mirror `GameApplicationService.scala:87-91`: `WalkerPowerCatalog.default`, `PhasePowerCatalog.default`, `CampaignDicePort.walkerDice`), the three named situations, `seedInto` (the `zipWithIndex` → `GameEventWire.encodeEvent(gameId, catalog.ref, index, event)` → `repository.seed` block from `AuthenticatedGameRoutesSuite.scala:48-51`).

- [ ] **Step 3: `SituationDriver.scala`.** The shared `run` loop:

  ```
  for each step (index i):
    situation = apply(situation, step) or fail(s"step $i ${step} rejected: $violation")
    if settle || i < steps.size - 1:
      loop:
        parkedNode(situation.state) match
          Left(reason)            => fail(reason)
          Right(None)             => break
          Right(Some(roll: Roll)) => situation = roll(situation, roll)
          Right(Some(d: Decision))=>
            val park = Park(d.decide, situation.ready, d.awaiting, d.procedure)
            val answer = answers.orElse(defaultAnswer).applyOrElse(park,
              _ => fail(s"step $i: no answer for ${d.decisionId} (${d.decide.query.getClass.getSimpleName}) awaiting ${d.awaiting}"))
            situation = apply(situation, ResolveWalker(d.awaiting, TreeDecision(d.decisionId, answer)))
  ```

  `RulesDriver(rules, defenseDice, answers)`: `apply` dispatches `GameCommand` exactly as `GameApplicationService.applyCommand` (`GameApplicationService.scala:346-412`) — `Begin → beginGame`, `StartWalker(p, s) → startWalker(state, p, s.actor, s.modifiers, s.startArgs)` (Search included; no draw port at this level), `ResolveWalker → resolveWalker`, `RollWalker → rollWalkerPrepared(...)(count => Either.cond(count == defenseDice.diceCount, defenseDice.rollTwo(), ...))`, `EndWake/BeginRest/FinishRest → startWalker(PhaseTransitionRef.*)`, `UsePower → startWalker(ActionRef.UsePower(power), actor, Vector.empty, Vector(source))`, the three minor actions → `rules.handle(MinorActionCommand.*)`, `WithModifiers → apply(inner)`. `Arrange` builds the `WalkerStepRecorded("0", DeltaRecorded(OperationApplied(label)), ops, Vector.empty)` and applies it with `WalkerReplay.applyRecorded`, appending the event. `parkedNode` uses the rules' own power catalogs — read them by constructing the driver with the same `WalkerPowers`/`PhasePowers` you passed to `OathRules` (add them as parameters of `Situation.rules` with defaults `WalkerPowers.empty`/`PhasePowers.empty`, matching `new OathRules(catalog)`).

  `JournaledDriver(service, repository, gameId, answers)`: `apply` for a command is `service.handle(gameId, from.nextSequence, command)` mapped to `Situation(accepted.state, from.events ++ accepted.events, accepted.nextSequence, this)`; for `Arrange` it appends the encoded record at `from.nextSequence` (today's `ParkedServiceFixture.seed`) then `service.load(gameId)`. `roll` issues `RollWalker(awaiting, pool)`. `parkedNode` uses `WalkerPowerCatalog.default(catalog)` and `PhasePowerCatalog.default(catalog)`, which is what the service's rules run.

- [ ] **Step 4: One answer policy.** Replace `SetupWalkDriver.defaultAnswer` (`SetupWalkDriver.scala:34-47`) with a call to `Situation.defaultAnswer` applied to a `Park` built from the tree-level `Decide` (`Park(decide, state, decide.owner, TriggeredProcedureRef.Setup)`). `driveToCompletion` keeps its signature and its four callers do not move.

- [ ] **Step 5: Verify.** `./sbtw "testOnly oathdigital.testkit.SituationSuite oathdigital.gameplay.setup.SetupProcedureSuite oathdigital.gameplay.powers.setup.*"`, then `./sbtw test`. Commit: `test(testkit): add Situation with a rules and a journaled adapter`.

---

### Task 3: `FirstGameSetupFixture` delegates; `GameStartToWakeSuite` over the driver

**Files:** Modify `src/test/scala/oathdigital/gameplay/setup/FirstGameSetupFixture.scala:93-138`, `src/test/scala/oathdigital/gameplay/setup/GameStartToWakeSuite.scala:22-56`.

- [ ] **Step 1: Pin.** Before editing, in `SituationSuite` test 1 already compares `Situation.wake` against the old `execute()`. Run it; it must pass, or the driver does not reproduce today's walk (check `placementSites(index)` — the default policy picks the first *offered* site, the old fixture picks `sites(index)`; if they differ, `wake` takes an `answers` override that maps `pawnDecisionId(p)` to `sites(turnIndex)` and the test pins that override, not the default).

- [ ] **Step 2: Delegate.** `execute(placementSites)` becomes: build `Situation.rules(new OathRules(catalog))` with the pawn override from Step 1 over `placementSites`, `Situation.wake(driver, chronicle, orders)`, return `(situation.state, situation.events)`. `initialReady` stays `lazy val` over `execute()._1`. Delete the local `order` vector and the hand-assembled `PartitionAnswer`.

- [ ] **Step 3: `GameStartToWakeSuite`.** Its second test (`"driving every player's two decisions ends in Wake with the recorded seating"`) becomes `Situation.wake(Situation.rules(rules))` followed by the same three assertions. Tests one and three are unchanged.

- [ ] **Step 4: Verify.** `./sbtw "testOnly oathdigital.testkit.SituationSuite oathdigital.gameplay.setup.*"`, then `./sbtw test` — this is the task with the widest blast radius (39 `initialReady` readers, 29 `execute()` callers). Commit: `test(setup): drive FirstGameSetupFixture through Situation.wake`.

---

### Task 4: `ParkedServiceFixture` over the journaled adapter

**Files:** Modify `src/test/scala/oathdigital/application/ParkedServiceFixture.scala` (whole file), `src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala:998-1007` (`private def execute`), `src/test/scala/oathdigital/application/WalkerDecisionProjectionSuite.scala` (one `setUp` call).

- [ ] **Step 1: Baseline.** `./sbtw "testOnly oathdigital.application.GameApplicationServiceSuite oathdigital.application.PendingWalkerInvariantSuite oathdigital.application.PhasePowerProjectorSuite oathdigital.application.WalkerDecisionProjectionSuite"`; require pass. Record `GameApplicationServiceSuite`'s `nextSequence == 21L` and `records.size == 21` assertions; they must still hold at the end.

- [ ] **Step 2: `setUp`.** Signature unchanged (`service, gameId, placementSites, setupChronicle, setupOrders): GameAccepted`). Body: `Situation.journaled(service, repository?, gameId)` — the current signature has no repository; add an overload that takes one, keep the old one building a driver whose `Arrange` fails with "setUp without a repository cannot arrange" so existing callers compile. Pawn override as in Task 3 Step 1. Return `GameAccepted(situation.state, situation.events, situation.nextSequence)`.

- [ ] **Step 3: `seed` → `Arrange`.** Keep `seed(repository, gameId, at, ops)` as a one-line delegate that runs `Step.Arrange(ops, s"arrange the $gameId fixture")` through a journaled driver at `at`; the four parks stop calling it directly and use `situation.after(Arrange(...))` instead.

- [ ] **Step 4: The four parks.** Re-express each as steps over the driver, keeping every assertion:
  - `leagueTreatyPark`: `wake` (seeded Chronicle) → `Arrange(cleared ++ moves)` → `parkedAfter(EndWake(active), BeginRest(active))`; the `assertParked(... FinishRest, destinationDecisionId ...)` stays.
  - `recoverChoicePark`: `wake` (rotated Chronicle) → `parkedAfter(EndWake(actor), StartWalker(Recover, ...))`. The driver auto-rolls with the service's `failingDice`, so the Roll park is answered the way the fixture's doc already describes.
  - `oathkeeperTiePark`: `wake` → `Arrange(...)` → `parkedAfter(EndWake(active), StartWalker(Travel, ...))`.
  - `silverTonguePark`: `wake` (seeded Chronicle) → `Arrange(...)` → `after(EndWake(active), BeginRest(active))` → `parkedAfter(UsePower(active, SilverTongue.id, source))`.
  `withWorldDeckTop`, `topOfWorldDeck`, `cleared`, `failingDice` stay as they are.

- [ ] **Step 5: Call sites.** `GameApplicationServiceSuite.execute` (`:998`) and `WalkerDecisionProjectionSuite`'s `setUp` call keep their shape; adjust only if the repository overload is needed where an `Arrange` follows.

- [ ] **Step 6: Verify.** The Step 1 suites, then `./sbtw test`. Commit: `test(application): drive ParkedServiceFixture through Situation`.

---

### Task 5: `ForgeWalkerFixture` over the journaled adapter

**Files:** Modify `src/test/scala/oathdigital/application/ForgeWalkerFixture.scala:56-178`.

- [ ] **Step 1: Baseline.** `./sbtw "testOnly oathdigital.application.GameApplicationServiceSuite oathdigital.application.WalkerDecisionProjectionSuite"`; require pass.

- [ ] **Step 2: `forgeReadyGame`.** Replace the Setup loop (`:72-91`) with `Situation.wake(driver, forgeChronicle, forgeOrders)` using a pawn override that places `order(i)` at `orderedSites(i)`. Replace the Campaign block (`:93-117`) with `after(EndWake(actor), StartWalker(Campaign, StartPayload(actor)))` under an `answers` override: `CampaignIds.targets → ChooseManyAnswer(Vector.empty)`, `CampaignIds.force → ChooseAmountAnswer(3)`, `CampaignIds.sacrifice → ChooseAmountAnswer(2)`, `CampaignIds.placement → ChooseAmountAnswer(1)`. The conditional parks (`targets`, `placement`) need no `if`: the override fires only when the park occurs.

- [ ] **Step 3: `searchOne`.** Becomes `after(StartWalker(Search, StartPayload(actor, Vector.empty, Vector(Button("search:world")))))` under an override with two cases: `"search.cards"` computes `kept` from `park.ready.game.current.temporaryHands(actor)` exactly as today (`CardPlay.plannedOperations(cat, park.ready, actor, card, SearchPlacement.Site(None), Origin.TemporaryHand).isRight`) and answers the keep/discard `PartitionAnswer`; `id if id.startsWith("cardplay.place.")` answers `ChooseOneAnswer(Button("site"))`. The Rest round (`:158-166`) becomes `after(BeginRest(actor), EndWake(p3), BeginRest(p3), EndWake(p1), BeginRest(p1), EndWake(actor))` then one more `searchOne()`.

- [ ] **Step 4: `parkedForge`.** `parkedAfter(StartWalker(Forge, ...))` from the `forgeReadyGame` situation under `mixedForgeCostCatalog`; return values unchanged.

- [ ] **Step 5: Verify.** Step 1 suites, then `./sbtw test`. Commit: `test(application): drive ForgeWalkerFixture through Situation`.

---

### Task 6: `seedInto` for the route suites

**Files:** Modify `src/test/scala/oathdigital/server/AuthenticatedGameRoutesSuite.scala:36-51`, `src/test/scala/oathdigital/server/TrustedSeatRoutesSuite.scala:57-67`.

- [ ] **Step 1: Baseline.** `./sbtw "testOnly oathdigital.server.AuthenticatedGameRoutesSuite oathdigital.server.TrustedSeatRoutesSuite"`; require pass.

- [ ] **Step 2: Replace the seeding.** In each, the `execute()` + `startWalker` chain + `repository.seed(...encodeEvent...)` block becomes `Situation.act(Situation.rules(new OathRules(catalog)), actor)` (plus `after(StartWalker(Travel, ...))` in the Negotiation test) followed by `situation.seedInto(repository, gameId)`. `actor` is `situation.ready.game.current.turn.activePlayer` read from `wake` first. The sequence the Trusted test computes (`(events ++ act.events).size`) becomes `situation.nextSequence`.

- [ ] **Step 3: Verify.** Step 1 suites, then `./sbtw test`. Commit: `test(server): seed route journals from a Situation`.

---

### Task 7: Close out

- [ ] **Step 1: Gates.** `./sbtw test`, `./sbtw frontend/test`, `python3 scripts/check-architecture.py`, `python3 scripts/check-markdown-links.py`.

- [ ] **Step 2: Sweep.**

```bash
grep -rln 'PlayerId("p2"), PlayerId("p3"), PlayerId("p1")' src/test
```

Expected: no output. The turn order is now the walker's to know.

```bash
grep -rln 'adviserKeepKey' src/test
```

Expected: only `SetupProcedureSuite`, `NegotiationDealSuite` and the three route suites, where the Setup answer itself is under test. If `ParkedServiceFixture`, `ForgeWalkerFixture`, `FirstGameSetupFixture` or `GameStartToWakeSuite` still appear, a task is incomplete.

- [ ] **Step 3: Docs.** Tick this plan's boxes. Add a "Situations" paragraph to `docs/testing/` where fixtures are described, if such a page exists; otherwise nothing.

- [ ] **Step 4: Merge.** Merge local `main` into the worktree branch first (it lags and leads `origin` independently), run `./sbtw test` once more, then merge into `main`.
