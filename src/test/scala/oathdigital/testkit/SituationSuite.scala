package oathdigital.testkit

import oathdigital.application.{GameApplicationService, GameCommand,
  InMemoryEventStreamRepository, ParkedServiceFixture, StartPayload}
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.setup.{FirstGameSetupFixture, SetupProcedure}
import oathdigital.gameplay.setup.FirstGameSetupFixture.{catalog, chronicle,
  orders, sites}
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, present}

class SituationSuite extends munit.FunSuite:
  private def journaledService()
      : (GameApplicationService, InMemoryEventStreamRepository) =
    val repository = new InMemoryEventStreamRepository
    (new GameApplicationService(catalog, repository,
      campaignDicePort = Dice.blank), repository)

  private def journaled(gameId: String)
      : (SituationDriver, InMemoryEventStreamRepository) =
    val (service, repository) = journaledService()
    (Situation.journaled(service, catalog, repository, gameId), repository)

  /** `ready` once its Wake has ended. */
  private def inAct(ready: ReadyGame): ReadyGame =
    Table.from(ready).backInPhase(Phase.Act).unchecked

  private def recordCount(repository: InMemoryEventStreamRepository,
      gameId: String): Int =
    present(accepted(repository.load(gameId)), s"stream $gameId").records.size

  test("wake is the fixture's first game: the first player's Wake, with " +
      "nothing to decide, ended; pawns on the first sites and one adviser each"):
    // Before FirstGameSetupFixture delegated to it, this compared `wake`
    // with the fixture's own hand-written walk, state and events alike.
    val ready = Situation.wake(Situation.rules(catalog)
      .withAnswers(Situation.pawnsAt(sites))).ready
    val current = ready.game.current
    assertEquals(ready, inAct(FirstGameSetupFixture.initialReady))
    assertEquals((current.turn.phase, current.turn.activePlayer),
      (Phase.Act, orders.firstPlayer))
    assertEquals(current.players.find(_.player == orders.firstPlayer)
      .flatMap(_.pawnSite), Some(sites.head))
    assertEquals(current.players.flatMap(_.pawnSite).toSet,
      sites.take(orders.participants.size).toSet)
    assert(current.players.forall(_.advisers.size == 1), current.players)

  test("the rules and journaled adapters reach the same game for the same " +
      "steps"):
    // An arranged Oathkeeper, then the first player's Wake and a Search of
    // the world deck with every park answered by default: the Arrange, the
    // draw and the card play all cross the adapters' seams.
    val (driver, repository) = journaled("agree")
    val actor = orders.firstPlayer
    // The first player is p2 and nobody holds the Oathkeeper title, so p1
    // is the one the arrangement makes Oathkeeper.
    val other = p1
    assertEquals(FirstGameSetupFixture.initialReady.game.current.title.holder,
      None)
    val steps = Vector[GameCommand | Step](
      Step.Arrange(Vector(SetOathkeeper(Some(other)))),
      GameCommand.StartWalker(ActionRef.Search, StartPayload(actor,
        Vector.empty, Vector(DecisionOptionRef.Button("search:world")))))
    val byRules = Situation.wake(
      Situation.productionRules(catalog, Dice.blank))
      .after(steps*)
    val byJournal = Situation.wake(driver).after(steps*)
    assertEquals(byJournal.state, byRules.state)
    assertEquals(byJournal.events, byRules.events)
    assertEquals(byJournal.nextSequence, byRules.nextSequence)
    assertEquals(recordCount(repository, "agree").toLong,
      byJournal.nextSequence)

  test("the rules and journaled adapters agree from a table"):
    val table = Table.start
    val (service, repository) = table.service(campaignDice = Dice.blank)
    val steps = Vector[GameCommand | Step](GameCommand.StartWalker(
      ActionRef.Search, StartPayload(p1, Vector.empty,
        Vector(DecisionOptionRef.Button("search:world")))))
    val byRules = table.situation(
      Situation.productionRules(catalog, Dice.blank))
      .after(steps*)
    val byJournal = table.situation(Situation.journaled(service, catalog,
      repository, "table")).after(steps*)
    assertEquals(byJournal.state, byRules.state)
    assertEquals(byJournal.events, byRules.events)
    assertEquals(byJournal.nextSequence, byRules.nextSequence)
    assertEquals(recordCount(repository, "table").toLong,
      byJournal.nextSequence)

  test("a journaled situation at a table needs a service begun there"):
    val (service, repository) = journaledService()
    val failure = intercept[AssertionError](Table.start.situation(
      Situation.journaled(service, catalog, repository, "elsewhere")))
    assert(failure.getMessage.contains("table.service"), failure.getMessage)

  test("an unanswerable park fails naming the decision"):
    val partitionless: Answers =
      case park if !park.decide.query.isInstanceOf[DecisionQuery.Partition] &&
          Situation.defaultAnswer.isDefinedAt(park) =>
        Situation.defaultAnswer(park)
    val failure = intercept[AssertionError](
      Situation.wake(Situation.rules(catalog).withPolicy(partitionless)))
    assert(failure.getMessage.contains(
      SetupProcedure.adviserDecisionId(orders.firstPlayer)),
      failure.getMessage)
    assert(failure.getMessage.contains("Partition"), failure.getMessage)

  test("parkedAfter leaves the last step's park for ParkedDecisionAssertions"):
    val parked = Situation.start(Situation.rules(catalog))
      .parkedAfter(GameCommand.Begin(chronicle, orders))
    new ParkedDecisionAssertions(catalog).assertParked(parked.state,
      TriggeredProcedureRef.Setup,
      SetupProcedure.pawnDecisionId(orders.firstPlayer), orders.firstPlayer)

  test("an Arrange step crosses the replay path on both adapters"):
    val (driver, repository) = journaled("arrange")
    // Nobody holds the Oathkeeper title after the wake, so p1 can be made
    // Oathkeeper.
    def assertArranges(situation: Situation): Unit =
      val holder = p1
      assertEquals(situation.ready.game.current.title.holder, None)
      val arranged = situation.after(Step.Arrange(Vector(
        SetOathkeeper(Some(holder)))))
      assertEquals(arranged.events.size, situation.events.size + 1)
      assertEquals(arranged.nextSequence, situation.nextSequence + 1)
      assertEquals(arranged.ready.game.current.title.holder, Some(holder))
    val wokenByJournal = Situation.wake(driver)
    val before = recordCount(repository, "arrange")
    assertArranges(Situation.wake(Situation.rules(catalog)))
    assertArranges(wokenByJournal)
    assertEquals(recordCount(repository, "arrange"), before + 1)

  test("an Arrange as the first step of a table journals against a stream " +
      "that does not exist yet"):
    val table = Table.start
    val (service, repository) = table.service()
    val driver = Situation.journaled(service, catalog, repository, "first")
    val arranged = table.situation(driver).after(Step.Arrange(Vector(
      SetOathkeeper(Some(p1)))))
    assertEquals(arranged.nextSequence, 1L)
    assertEquals(recordCount(repository, "first"), 1)
    assertEquals(arranged.ready.game.current.title.holder, Some(p1))
    assertEquals(present(accepted(service.load("first")), "stream first").state,
      arranged.state)

  test("the rules adapter parks a Recover on its continue-or-stop choice " +
      "the way ParkedServiceFixture does"):
    // ParkedServiceFixture.recoverChoicePark's table, under the rules the
    // service builds and dice that fail every Recover roll.
    val parked = ParkedServiceFixture.recoverTable
      .situation(Situation.productionRules(catalog, Dice.blank))
      .parkedAfter(GameCommand.StartWalker(ActionRef.Recover, StartPayload(p1)))
    new ParkedDecisionAssertions(catalog, WalkerPowerCatalog.default(catalog),
      PhasePowerCatalog.default(catalog)).assertParked(parked.state,
        ActionRef.Recover, RecoverProcedure.choiceDecisionId, p1)

  test("after with no steps answers the park a situation holds"):
    val parked = Situation.start(Situation.rules(catalog))
      .parkedAfter(GameCommand.Begin(chronicle, orders))
    val woken = parked.withAnswers(Situation.pawnsAt(sites)).after()
    assertEquals(woken.ready, inAct(FirstGameSetupFixture.initialReady))

  test("the rules adapter refuses ordered modifiers rather than skip their " +
      "check"):
    val failure = intercept[AssertionError](
      FirstGameSetupFixture.initialSituation().after(GameCommand.WithModifiers(
        GameCommand.EndWake(orders.firstPlayer), Vector.empty)))
    assert(failure.getMessage.contains("journaled adapter"),
      failure.getMessage)
