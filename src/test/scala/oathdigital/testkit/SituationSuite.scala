package oathdigital.testkit

import oathdigital.application.{CampaignDicePort, GameApplicationService,
  GameCommand, StartPayload,
  InMemoryEventStreamRepository}
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.setup.{FirstGameSetupFixture, SetupProcedure}
import oathdigital.gameplay.setup.FirstGameSetupFixture.{catalog, chronicle,
  orders, sites}
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._

class SituationSuite extends munit.FunSuite:
  /** Dice that always come up the same way, so two adapters given them
    * reach the same board. */
  private val blankDice: CampaignDicePort = new CampaignDicePort:
    def rollAttack(count: Int): Vector[AttackDieFace] =
      Vector.fill(count)(AttackDieFace.HollowSword)
    def rollDefense(count: Int): Vector[DefenseDieFace] =
      Vector.fill(count)(DefenseDieFace.Blank)

  private def journaled(gameId: String)
      : (SituationDriver, InMemoryEventStreamRepository) =
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository,
      campaignDicePort = blankDice)
    (Situation.journaled(service, catalog, repository, gameId), repository)

  private def recordCount(repository: InMemoryEventStreamRepository,
      gameId: String): Int =
    repository.load(gameId).toOption.flatten.get.records.size

  test("wake under the fixture's rules equals initialReady"):
    val situation = Situation.wake(Situation.rules(catalog,
      answers = Situation.pawnsAt(sites) orElse Situation.defaultAnswer))
    val (state, events) = FirstGameSetupFixture.execute()
    assertEquals(situation.state, state)
    assertEquals(situation.events, events)
    assertEquals(situation.ready, FirstGameSetupFixture.initialReady)

  test("the rules and journaled adapters reach the same game for the same " +
      "steps"):
    val (driver, repository) = journaled("agree")
    val steps = Vector(GameCommand.EndWake(orders.firstPlayer))
    val byRules = Situation.wake(Situation.serviceRules(catalog, blankDice))
      .after(steps*)
    val byJournal = Situation.wake(driver).after(steps*)
    assertEquals(byJournal.state, byRules.state)
    assertEquals(byJournal.events, byRules.events)
    assertEquals(byJournal.nextSequence, byRules.nextSequence)
    assertEquals(recordCount(repository, "agree").toLong,
      byJournal.nextSequence)

  test("an unanswerable park fails naming the decision"):
    val partitionless: Answers =
      case park if !park.decide.query.isInstanceOf[DecisionQuery.Partition] &&
          Situation.defaultAnswer.isDefinedAt(park) =>
        Situation.defaultAnswer(park)
    val failure = intercept[AssertionError](
      Situation.wake(Situation.rules(catalog, answers = partitionless)))
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
    def assertArranges(situation: Situation): Unit =
      val holder = situation.ready.game.current.players.map(_.player)
        .find(player => !situation.ready.game.current.title.holder
          .contains(player)).get
      val arranged = situation.after(Step.Arrange(Vector(
        SetOathkeeper(Some(holder)))))
      assertEquals(arranged.events.size, situation.events.size + 1)
      assertEquals(arranged.ready.game.current.title.holder, Some(holder))
    val wokenByJournal = Situation.wake(driver)
    val before = recordCount(repository, "arrange")
    assertArranges(Situation.wake(Situation.rules(catalog)))
    assertArranges(wokenByJournal)
    assertEquals(recordCount(repository, "arrange"), before + 1)

  test("the rules adapter parks a Recover on its continue-or-stop choice " +
      "the way ParkedServiceFixture does"):
    // ParkedServiceFixture.recoverChoicePark's board, under the rules the
    // service builds and dice that fail every Recover roll.
    val recoverSite = catalog.sites.find(site =>
      site.recoverDifficulty.exists(d => d > 0 && d <= 4) &&
        site.relicSlots > 0 &&
        !site.handlers.exists(_.contains(".homeland-"))).get.id
    val recoverChronicle = chronicle.copy(atlasBox =
      chronicle.atlasBox.find(_.site == recoverSite).get +:
        chronicle.atlasBox.filterNot(_.site == recoverSite))
    val actor = orders.firstPlayer
    val driver = Situation.serviceRules(catalog, blankDice,
      answers = Situation.pawnsAt(recoverChronicle.atlasBox.take(8)
        .map(_.site)) orElse Situation.defaultAnswer)
    val parked = Situation.wake(driver, recoverChronicle, orders)
      .parkedAfter(GameCommand.EndWake(actor),
        GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor)))
    new ParkedDecisionAssertions(catalog, WalkerPowerCatalog.default(catalog),
      PhasePowerCatalog.default(catalog)).assertParked(parked.state,
        ActionRef.Recover, RecoverProcedure.choiceDecisionId, actor)
