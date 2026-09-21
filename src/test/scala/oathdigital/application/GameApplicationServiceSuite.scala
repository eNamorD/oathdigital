package oathdigital.application

import oathdigital.protocol.projection.{BoardTargetRefProjection,
  DecisionSectionProjection, SiteForcesProjection}

import java.nio.file.Files

import oathdigital.model._
import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.actions.forge.ForgeProcedure
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.walker.{ChoicePayload, WalkerCompleted,
  WalkerParked, WalkerStepRecorded}
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.walker.WalkerStepPayload.DeltaRecorded
import oathdigital.gameplay.walker.DeltaMeaning.{DicePoolModified,
  RelicAcquired, SupplySpent}
import oathdigital.gameplay.actions.negotiation.NegotiationDeal
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseAmountAnswer, ChooseManyAnswer, ChooseOneAnswer, PartitionAnswer, ProposeTerms}
import oathdigital.persistence.OwnedHsqldbEventStreamRepository
import oathdigital.serialization.GameEventWire
import oathdigital.server.GameHttpWire
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.application.ForgeWalkerFixture.{blankCampaignDice,
  forgeReadyGame, mixedForgeCostCatalog}
import oathdigital.model.OathEvent.{
  GamePawnPlaced,
  FirstGameStarted
}
import oathdigital.model.OathViolation.{CatalogMismatch, WrongPlayer}
import oathdigital.model.OathState.Ready
import oathdigital.gameplay.OathRules

class GameApplicationServiceSuite extends munit.FunSuite {
  private val catacombsId = DenizenId(catalog.denizens.find(_.powers.exists(
    _.id.value == "denizen.catacombs")).get.id.value)

  test("withWorldDeckTop preserves two absent requested denizens of one suit") {
    val (suit, absent) = catalog.denizens.groupBy(_.suit).iterator
      .map { case (suit, definitions) => suit -> definitions
        .map(definition => DenizenId(definition.id.value))
        .filterNot(plan.denizenOrder.contains) }
      .find(_._2.size >= 2).get
    val (otherSuits, sameSuit) = plan.denizenOrder.partition(id =>
      catalog.denizens.find(_.id.value == id.value).forall(_.suit != suit))
    val base = plan.copy(denizenOrder = otherSuits ++ sameSuit)
    val requested = absent.take(2).toVector
    val changed = ParkedServiceFixture.withWorldDeckTop(base, requested)
    val dealt = 6 + changed.participants.size * 3

    assertEquals(changed.denizenOrder.slice(dealt, dealt + 2), requested)
    assertEquals(changed.denizenOrder.distinct.size, changed.denizenOrder.size)
    assert(requested.forall(changed.denizenOrder.contains))
  }

  test("beginRest through the service parks the off-turn League Treaty ruler " +
      "and survives reload") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val (parked, active, ruler) = ParkedServiceFixture.leagueTreatyPark(
      service, repository, "game-league-treaty")
    val reloaded = new GameApplicationService(catalog, repository)
      .load("game-league-treaty").toOption.flatten.get
    assertEquals(reloaded.state, parked.state)
    val OathContinue.AwaitingRestDecision(_, decision) = parked.continue: @unchecked
    val decline = GameCommand.ResolveWalker(_: PlayerId, TreeDecision(
      decision.value, ChooseOneAnswer(DecisionOptionRef.Button("decline"))))
    assert(service.handle("game-league-treaty", parked.nextSequence,
      decline(active)).isLeft)
    val finished = service.handle("game-league-treaty", parked.nextSequence,
      decline(ruler)).toOption.get
    val Ready(after) = finished.state: @unchecked
    assertEquals(after.game.current.turn.phase, Phase.Wake)
    assertNotEquals(after.game.current.turn.activePlayer, active)
  }

  private def catacombsPlan = {
    val recoverSite = catalog.sites.find(site => site.recoverDifficulty.nonEmpty &&
      site.relicSlots == 1 && !site.handlers.exists(_.contains(".homeland-"))).get.id
    val actorIndex = plan.participants.indexWhere(_.playerId == PlayerId("p2"))
    val adviserIndex = 6 + actorIndex * 3
    val oldIndex = plan.denizenOrder.indexWhere(_.value == catacombsId.value)
    val order = if (oldIndex < 0) plan.denizenOrder.updated(adviserIndex, catacombsId)
      else plan.denizenOrder.updated(adviserIndex, catacombsId)
        .updated(oldIndex, plan.denizenOrder(adviserIndex))
    plan.copy(orderedSites = recoverSite +: plan.orderedSites.filterNot(_ == recoverSite),
      denizenOrder = order)
  }

  private final class CountingRecoverDice extends DefenseDicePort {
    var calls = 0
    def rollTwo() = {
      calls += 1
      Vector(DefenseDieFace.TwoShields, DefenseDieFace.Doubler)
    }
  }

  private final class FixedRecoverDice(faces: Vector[DefenseDieFace])
      extends DefenseDicePort {
    var calls = 0
    def rollTwo() = {
      calls += 1
      faces
    }
  }

  private final class ScriptedRecoverDice(
      rolls: Vector[Vector[DefenseDieFace]]) extends DefenseDicePort {
    var calls = 0
    def rollTwo() = {
      val faces = rolls(calls)
      calls += 1
      faces
    }
  }

  test("walker Recover persists every park and replays to the same final state") {
    val recoverSite = catalog.sites.find(site =>
      site.recoverDifficulty.exists(difficulty => difficulty > 0 && difficulty <= 4) &&
        site.relicSlots > 0 &&
        !site.handlers.exists(_.contains(".homeland-"))).get.id
    val recoverPlan = plan.copy(orderedSites = recoverSite +:
      plan.orderedSites.filterNot(_ == recoverSite))
    val actor = recoverPlan.firstPlayer

    val walkerRepository = new InMemoryEventStreamRepository
    val walkerDice = new CountingRecoverDice
    def walkerService = new GameApplicationService(catalog, walkerRepository,
      defenseDicePort = walkerDice)
    val walkerSetup = execute(walkerService, "walker-recover",
      recoverPlan.orderedSites, recoverPlan)
    val walkerAct = walkerService.handle("walker-recover",
      walkerSetup.nextSequence, GameCommand.EndWake(actor)).toOption.get

    val started = walkerService.handle("walker-recover", walkerAct.nextSequence,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor))).toOption.get
    val Ready(atRoll) = started.state: @unchecked
    assert(started.events.last.isInstanceOf[WalkerParked])
    assertEquals(atRoll.game.current.walkerProcedure, Some(ActionRef.Recover))
    assert(atRoll.game.current.walkerPending.nonEmpty)
    assertEquals(started.continue, OathContinue.AwaitingRecoverRoll(actor,
      DecisionId(RecoverProcedure.rollDecisionId)))

    walkerService.handle("walker-recover", started.nextSequence,
      GameCommand.PeekSiteRelics(actor)) match {
      case Left(GameApplicationError.CommandRejected(
          _: oathdigital.model.OathViolation.InvalidEventOrder)) => ()
      case other => fail(s"legacy command should be blocked by walker park: $other")
    }
    assertEquals(walkerService.load("walker-recover").toOption.flatten.get
      .nextSequence, started.nextSequence)

    val reloadedAtRoll = new GameApplicationService(catalog, walkerRepository)
      .load("walker-recover").toOption.flatten.get
    assertEquals(reloadedAtRoll.state, started.state)
    val rolled = walkerService.handle("walker-recover",
      reloadedAtRoll.nextSequence,
      GameCommand.RollWalker(actor, RecoverProcedure.recoverPool)).toOption.get
    assertEquals(walkerDice.calls, 1)
    val Ready(atRelic) = rolled.state: @unchecked
    assert(rolled.events.last.isInstanceOf[WalkerParked])
    assertEquals(rolled.continue, OathContinue.AwaitingRecoverRelic(actor,
      DecisionId(RecoverProcedure.relicDecisionId)))
    val relic = atRelic.game.current.map.sites(recoverSite).relics.head.id

    val finished = walkerService.handle("walker-recover", rolled.nextSequence,
        GameCommand.ResolveWalker(actor, TreeDecision(RecoverProcedure.relicDecisionId,
          ChooseOneAnswer(DecisionOptionRef.Relic(relic))))).toOption.get
    val Ready(afterWalker) = finished.state: @unchecked
    assert(finished.events.exists(_.isInstanceOf[WalkerCompleted]))
    assertEquals(finished.continue, OathContinue.ActActionSelection(actor))
    assert(afterWalker.game.current.walkerPending.isEmpty)
    assert(afterWalker.game.current.walkerProcedure.isEmpty)
    assertEquals(afterWalker.game.current.rollPools,
      Map.empty[PoolKey, DicePoolState])
    assertEquals(afterWalker.game.current.rollOutcomes,
      Map.empty[PoolKey, RollOutcome])

    // The walker is now the only Recover path, so the final state is
    // asserted directly rather than against a legacy run: the relic moves
    // facedown into the actor's play area and off the site.
    val actorState = afterWalker.game.current.players.find(
      _.player == actor).get
    assertEquals(actorState.relics.map(r => r.id -> r.orientation),
      Vector(relic -> Orientation.FaceDown))
    assertEquals(afterWalker.game.current.map.sites(recoverSite).relics.map(_.id),
      atRelic.game.current.map.sites(recoverSite).relics.map(_.id)
        .filterNot(_ == relic))

    val replayed = walkerService.load("walker-recover").toOption.flatten.get
    assertEquals(replayed.state, finished.state)
    assertEquals(replayed.nextSequence, finished.nextSequence)

    val recordedOps = (started.events ++ rolled.events ++ finished.events)
      .collect { case step: WalkerStepRecorded => step.ops }.flatten
    assertEquals(recordedOps, Vector[CoreOperation](
      ModifyDicePool(RecoverProcedure.recoverPool, 2,
        window = Some(PowerWindow.RecoverBeforeFirstRoll)),
      SpendSupply(actor, 1),
      Move(Piece.Card(relic),
        PositionedLocation(Location.Site(recoverSite)),
        PositionedLocation(Location.PlayArea(actor)),
        resultingOrientation = Some(Orientation.FaceDown))))
    assertEquals((started.events ++ rolled.events ++ finished.events).collect {
      case WalkerStepRecorded(_, DeltaRecorded(semantic), _, _) => semantic
    }, Vector(
      DicePoolModified(RecoverProcedure.recoverPool, 2),
      SupplySpent(actor, 1),
      RelicAcquired(actor, relic, recoverSite)))
  }

  test("StartWalker rejects an unknown power id in modifiers, appending no " +
      "events, while empty modifiers still starts Recover exactly as today") {
    val recoverSite = catalog.sites.find(site =>
      site.recoverDifficulty.exists(difficulty => difficulty > 0 &&
        difficulty <= 4) && site.relicSlots > 0 &&
        !site.handlers.exists(_.contains(".homeland-"))).get.id
    val recoverPlan = plan.copy(orderedSites = recoverSite +:
      plan.orderedSites.filterNot(_ == recoverSite))
    val actor = recoverPlan.firstPlayer
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository,
      defenseDicePort = new FixedRecoverDice(Vector(DefenseDieFace.TwoShields,
        DefenseDieFace.Doubler)))
    val setup = execute(service, "walker-unknown-modifier",
      recoverPlan.orderedSites, recoverPlan)
    val act = service.handle("walker-unknown-modifier", setup.nextSequence,
      GameCommand.EndWake(actor)).toOption.get

    // No `ContributingPower` is registered yet (Task 5 ports the first one),
    // so any non-empty `modifiers` names an id the catalog cannot recognize:
    // rejected before the walk ever starts, with nothing appended.
    service.handle("walker-unknown-modifier", act.nextSequence,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor,
        Vector(PowerId("power.does-not-exist"))))) match {
      case Left(GameApplicationError.CommandRejected(
          _: oathdigital.model.OathViolation.InvalidEventOrder)) => ()
      case other => fail(s"expected an InvalidEventOrder rejection, got $other")
    }
    assertEquals(service.load("walker-unknown-modifier").toOption.flatten.get
      .nextSequence, act.nextSequence,
      "the rejected StartWalker must append no events")

    // The exact same command with an empty `modifiers` (every walker Recover
    // before this task) starts and parks exactly as the suite's other walker
    // tests already pin.
    val started = service.handle("walker-unknown-modifier", act.nextSequence,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor)))
      .toOption.get
    assert(started.events.nonEmpty)
    assert(started.events.last.isInstanceOf[WalkerParked])
    assertEquals(started.continue, OathContinue.AwaitingRecoverRoll(actor,
      DecisionId(RecoverProcedure.rollDecisionId)))
  }

  test("walker Continue answer and repeated Roll park survive reload") {
    val recoverSite = catalog.sites.find(site =>
      site.recoverDifficulty.nonEmpty && site.relicSlots > 0 &&
        !site.handlers.exists(_.contains(".homeland-"))).get.id
    val recoverPlan = plan.copy(orderedSites = recoverSite +:
      plan.orderedSites.filterNot(_ == recoverSite))
    val repository = new InMemoryEventStreamRepository
    val dice = new FixedRecoverDice(Vector(DefenseDieFace.Blank,
      DefenseDieFace.Blank))
    def service = new GameApplicationService(catalog, repository,
      defenseDicePort = dice)
    val setup = execute(service, "walker-continue", recoverPlan.orderedSites,
      recoverPlan)
    val actor = recoverPlan.firstPlayer
    val act = service.handle("walker-continue", setup.nextSequence,
      GameCommand.EndWake(actor)).toOption.get
    val started = service.handle("walker-continue", act.nextSequence,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor))).toOption.get
    assertEquals(started.continue, OathContinue.AwaitingRecoverRoll(actor,
      DecisionId(RecoverProcedure.rollDecisionId)))
    val failed = service.handle("walker-continue", started.nextSequence,
      GameCommand.RollWalker(actor, RecoverProcedure.recoverPool)).toOption.get
    // A failed roll parks the Continue/Stop Decide, not the Roll itself: same
    // AwaitingRecoverRoll continuation shape as the Roll park above, but a
    // different decision id — proving the mapping dispatches on the parked
    // node's identity rather than reusing whatever it last saw at this path.
    assertEquals(failed.continue, OathContinue.AwaitingRecoverRoll(actor,
      DecisionId(RecoverProcedure.choiceDecisionId)))
    val continued = service.handle("walker-continue", failed.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(RecoverProcedure.choiceDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Button("continue"))))).toOption.get
    assertEquals(continued.continue, OathContinue.AwaitingRecoverRoll(actor,
      DecisionId(RecoverProcedure.rollDecisionId)))
    val Ready(ready) = continued.state: @unchecked
    assertEquals(ready.game.current.walkerPending.toVector.flatMap(_.answered),
      Vector(Answered(RecoverProcedure.choiceDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Button("continue")), actor)))
    assertEquals(ready.game.current.walkerProcedure, Some(ActionRef.Recover))
    assertEquals(new GameApplicationService(catalog, repository)
      .load("walker-continue").toOption.flatten.get.state, continued.state)
    assert(repository.load("walker-continue").toOption.flatten.get.records
      .exists(record => record.contains(
        "\"procedure\":{\"family\":\"action\",\"key\":\"recover\"}") &&
        record.contains("\"answered\"")))

    val failedAgain = service.handle("walker-continue", continued.nextSequence,
      GameCommand.RollWalker(actor, RecoverProcedure.recoverPool)).toOption.get
    assertEquals(dice.calls, 2)
    assertEquals(failedAgain.continue, OathContinue.AwaitingRecoverRoll(actor,
      DecisionId(RecoverProcedure.choiceDecisionId)))
    val Ready(afterSecondRoll) = failedAgain.state: @unchecked
    val accumulated = afterSecondRoll.game.current.rollOutcomes(
      RecoverProcedure.recoverPool)
    assertEquals(accumulated.count, 4)
    assertEquals(accumulated.faces.size, 4)
    assertEquals(new GameApplicationService(catalog, repository)
      .load("walker-continue").toOption.flatten.get.state, failedAgain.state)

    val rules = new OathRules(catalog)
    Vector(Vector("not-a-node"), Vector("999999999999999999999")).foreach {
      path =>
        val malformed = ready.updateCurrent(_.copy(walkerPending = ready.game.current.walkerPending
            .map(_.copy(at = path))))
        val rejected = rules.rollWalkerPrepared(Ready(malformed), actor,
          RecoverProcedure.recoverPool)(_ => Right(
            Vector(DefenseDieFace.Blank, DefenseDieFace.Blank)))
        assert(rejected.left.toOption.exists(
          _.isInstanceOf[oathdigital.model.OathViolation.InvalidEventOrder]))
    }
  }

  test("walker Recover accumulates and reloads when a later Doubler " +
      "multiplies shields from an earlier roll") {
    val saltFlats = SiteId("site:salt-flats")
    assertEquals(RecoverRules.difficulty(catalog, saltFlats), Some(2))
    val orderedSites = saltFlats +:
      plan.orderedSites.filterNot(_ == saltFlats).take(7)
    val recoverPlan = plan.copy(
      orderedSites = orderedSites,
      homelandEdifices = plan.homelandEdifices.filter(entry =>
        orderedSites.contains(entry._1)))
    val actor = recoverPlan.firstPlayer
    val rolls = Vector[Vector[DefenseDieFace]](
      Vector(DefenseDieFace.OneShield, DefenseDieFace.Blank),
      Vector(DefenseDieFace.Doubler, DefenseDieFace.Blank))

    val walkerRepository = new InMemoryEventStreamRepository
    val walkerDice = new ScriptedRecoverDice(rolls)
    val walkerService = new GameApplicationService(catalog, walkerRepository,
      defenseDicePort = walkerDice)
    val walkerSetup = execute(walkerService, "walker-cross-roll-doubler",
      recoverPlan.orderedSites, recoverPlan)
    val walkerAct = walkerService.handle("walker-cross-roll-doubler",
      walkerSetup.nextSequence, GameCommand.EndWake(actor)).toOption.get
    val walkerStarted = walkerService.handle("walker-cross-roll-doubler",
      walkerAct.nextSequence,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor)))
      .toOption.get
    val walkerFirst = walkerService.handle("walker-cross-roll-doubler",
      walkerStarted.nextSequence,
      GameCommand.RollWalker(actor, RecoverProcedure.recoverPool)).toOption.get
    val Ready(walkerAfterFirst) = walkerFirst.state: @unchecked
    assertEquals(walkerAfterFirst.game.current.rollOutcomes(
      RecoverProcedure.recoverPool).score, 1)
    assertEquals(walkerFirst.continue, OathContinue.AwaitingRecoverRoll(actor,
      DecisionId(RecoverProcedure.choiceDecisionId)))
    val walkerFirstReloaded = new GameApplicationService(catalog,
      walkerRepository).load("walker-cross-roll-doubler").toOption.flatten.get
    assertEquals(walkerFirstReloaded.state, walkerFirst.state)
    val Ready(walkerAfterFirstReload) = walkerFirstReloaded.state: @unchecked
    assertEquals(walkerAfterFirstReload.game.current.rollOutcomes(
      RecoverProcedure.recoverPool).score, 1)

    val walkerContinued = walkerService.handle("walker-cross-roll-doubler",
      walkerFirstReloaded.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(RecoverProcedure.choiceDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Button("continue"))))).toOption.get
    val walkerSecond = walkerService.handle("walker-cross-roll-doubler",
      walkerContinued.nextSequence,
      GameCommand.RollWalker(actor, RecoverProcedure.recoverPool)).toOption.get
    val Ready(walkerAfterSecond) = walkerSecond.state: @unchecked
    assertEquals(walkerAfterSecond.game.current.rollOutcomes(
      RecoverProcedure.recoverPool).score, 2)
    assertEquals(walkerSecond.continue, OathContinue.AwaitingRecoverRelic(actor,
      DecisionId(RecoverProcedure.relicDecisionId)))
    val walkerSecondReloaded = new GameApplicationService(catalog,
      walkerRepository).load("walker-cross-roll-doubler").toOption.flatten.get
    assertEquals(walkerSecondReloaded.state, walkerSecond.state)
    val Ready(walkerAfterSecondReload) = walkerSecondReloaded.state: @unchecked
    assertEquals(walkerAfterSecondReload.game.current.rollOutcomes(
      RecoverProcedure.recoverPool).score, 2)

    // The walker is now the only Recover path: finish the walk and assert
    // its own final state directly rather than against a legacy run.
    val walkerRelic = walkerAfterSecond.game.current.map.sites(saltFlats)
      .relics.head.id
    val walkerFinished = walkerService.handle("walker-cross-roll-doubler",
      walkerSecondReloaded.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(RecoverProcedure.relicDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Relic(walkerRelic))))).toOption.get
    val Ready(walkerFinal) = walkerFinished.state: @unchecked
    val actorState = walkerFinal.game.current.players.find(
      _.player == actor).get
    assertEquals(actorState.relics.map(r => r.id -> r.orientation),
      Vector(walkerRelic -> Orientation.FaceDown))
    assertEquals(walkerFinal.game.current.map.sites(saltFlats).relics.map(_.id),
      walkerAfterSecond.game.current.map.sites(saltFlats).relics.map(_.id)
        .filterNot(_ == walkerRelic))
    assertEquals(walkerDice.calls, 2)
  }

  test("RollWalker with a pool key that does not match the parked pool " +
      "never calls defenseDicePort") {
    // rollWalkerPrepared validates the parked pool against the command's
    // pool key BEFORE invoking prepareFaces (OathRules.scala): this is the
    // entire mechanism the authoritative-dice property relies on to keep a
    // caller from steering which dice get rolled. Pin the ordering directly
    // by proving the port is untouched on a mismatch, not just that the
    // command is rejected.
    val recoverSite = catalog.sites.find(site =>
      site.recoverDifficulty.nonEmpty && site.relicSlots > 0 &&
        !site.handlers.exists(_.contains(".homeland-"))).get.id
    val recoverPlan = plan.copy(orderedSites = recoverSite +:
      plan.orderedSites.filterNot(_ == recoverSite))
    val repository = new InMemoryEventStreamRepository
    val dice = new CountingRecoverDice
    val service = new GameApplicationService(catalog, repository,
      defenseDicePort = dice)
    val actor = recoverPlan.firstPlayer
    val setup = execute(service, "walker-pool-mismatch",
      recoverPlan.orderedSites, recoverPlan)
    val act = service.handle("walker-pool-mismatch", setup.nextSequence,
      GameCommand.EndWake(actor)).toOption.get
    val started = service.handle("walker-pool-mismatch", act.nextSequence,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor)))
      .toOption.get

    val rejected = service.handle("walker-pool-mismatch", started.nextSequence,
      GameCommand.RollWalker(actor, PoolKey("not-the-parked-pool")))
    assert(rejected match {
      case Left(GameApplicationError.CommandRejected(
          _: oathdigital.model.OathViolation.InvalidEventOrder)) => true
      case _ => false
    }, s"expected InvalidEventOrder, got $rejected")
    assertEquals(dice.calls, 0)
  }

  private def prepareCatacombs(service: GameApplicationService, gameId: String,
      setupPlan: oathdigital.model.FirstGameSetupPlan)
      : (GameAccepted, PlayerId, OrderedRuleInvocation) = {
    val setup = execute(service, gameId, setupPlan.orderedSites.take(3), setupPlan)
    val Ready(ready) = setup.state: @unchecked
    val actor = ready.game.current.turn.activePlayer
    val act = service.handle(gameId, setup.nextSequence,
      GameCommand.EndWake(actor)).toOption.get
    // Runs one full Recover through the walker -- the only path left -- to
    // put a relic in `actor`'s hand, purely as setup for playing Catacombs
    // as a facedown adviser below; the specific relic recovered is
    // irrelevant to what this helper hands back.
    val started = service.handle(gameId, act.nextSequence,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor)))
      .toOption.get
    val rolled = service.handle(gameId, started.nextSequence,
      GameCommand.RollWalker(actor, RecoverProcedure.recoverPool)).toOption.get
    val Ready(atRelic) = rolled.state: @unchecked
    val siteId = atRelic.game.current.players.find(_.player == actor).get
      .pawnSite.get
    val relic = atRelic.game.current.map.sites(siteId).relics.head.id
    val emptied = service.handle(gameId, rolled.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(RecoverProcedure.relicDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Relic(relic))))).toOption.get
    val selecting = service.handle(gameId, emptied.nextSequence,
      GameCommand.StartWalker(ActionRef.PlayFacedownAdviser,
        StartPayload(actor, Vector.empty,
          Vector(DecisionOptionRef.Denizen(catacombsId))))).toOption.get
    val played = service.handle(gameId, selecting.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(
        s"cardplay.place.denizen.${catacombsId.value}",
        ChooseOneAnswer(DecisionOptionRef.Button("site"))))).toOption.get
    val Ready(atCatacombs) = played.state: @unchecked
    val site = atCatacombs.game.current.players.find(_.player == actor).get.pawnSite.get
    (played, actor, OrderedRuleInvocation(
      RuleSourceRef.SiteCard(site, catacombsId), "denizen.catacombs"))
  }

  test("StartWalker drives Catacombs through the full persisted path: its " +
      "recorded ops encode, append and replay (Task 9b prerequisite)") {
    // The only end-to-end way to use Catacombs once Task 9b deletes the
    // legacy object. `service.handle` is the whole path -- rules, event
    // encoding, append, replay -- so it is the layer that pins the gap the
    // rules-only assertion above cannot see: Catacombs records a `Move` out
    // of `Location.Deck(CardDeck.Relic)` and a `PayCost` at
    // `Location.OnCard`, none of which the walker codec used to encode.
    val repository = new InMemoryEventStreamRepository
    val dice = new CountingRecoverDice
    val service = new GameApplicationService(catalog, repository,
      defenseDicePort = dice)
    val gameId = "catacombs-walker-persisted"
    val (prepared, actor, _) = prepareCatacombs(service, gameId, catacombsPlan)
    val Ready(before) = prepared.state: @unchecked
    val beforePlayer = before.game.current.players.find(_.player == actor).get
    val siteId = beforePlayer.pawnSite.get
    val topRelic = before.game.current.commonCards.relicDeck.head

    val started = service.handle(gameId, prepared.nextSequence,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor,
        Vector(PowerId("denizen.catacombs"))))) match {
      case Right(accepted) => accepted
      case Left(error) =>
        fail(s"StartWalker with Catacombs must persist, got $error")
    }

    // Catacombs' own batch, verbatim, survived encoding: the relic move off
    // the top of the relic deck and the 1-secret payment onto the card.
    assertEquals(started.events.collect {
      case step: WalkerStepRecorded => step
    }.flatMap(_.ops).take(2), Vector[CoreOperation](
      Move(Piece.Card(topRelic),
        PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
        PositionedLocation(Location.Site(siteId)),
        resultingOrientation = Some(Orientation.FaceDown)),
      PayCost(actor, Location.OnCard(catacombsId), Cost(secret = 1),
        matchingBank = catalog.suitOf(catacombsId))))
    assert(started.events.collect { case step: WalkerStepRecorded =>
      step.contributions }.contains(Vector(PowerId("denizen.catacombs"))),
      "the recorded step must name the contribution that produced it")

    val Ready(after) = started.state: @unchecked
    val afterPlayer = after.game.current.players.find(_.player == actor).get
    assertEquals(after.game.current.commonCards.relicDeck,
      before.game.current.commonCards.relicDeck.tail)
    assertEquals(after.game.current.map.sites(siteId).relics.map(relic =>
      relic.id -> relic.orientation), Vector(topRelic -> Orientation.FaceDown))
    assertEquals(afterPlayer.board.faceUpSecrets,
      beforePlayer.board.faceUpSecrets - 1)
    assertEquals(started.continue, OathContinue.AwaitingRecoverRoll(actor,
      DecisionId(RecoverProcedure.rollDecisionId)))

    // Replay from the journal alone reproduces the same state: the ops did
    // not merely encode, they decode back to the operations that built it.
    val reloaded = new GameApplicationService(catalog, repository)
      .load(gameId).toOption.flatten.get
    assertEquals(reloaded.state, started.state)
    assertEquals(reloaded.nextSequence, started.nextSequence)

    // And the walk still finishes on the reloaded stream.
    val rolled = service.handle(gameId, reloaded.nextSequence,
      GameCommand.RollWalker(actor, RecoverProcedure.recoverPool)).toOption.get
    val Ready(atRelic) = rolled.state: @unchecked
    val recovered = atRelic.game.current.map.sites(siteId).relics.head.id
    val finished = service.handle(gameId, rolled.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(RecoverProcedure.relicDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Relic(recovered))))).toOption.get
    assert(finished.events.exists(_.isInstanceOf[WalkerCompleted]))
    assertEquals(new GameApplicationService(catalog, repository).load(gameId)
      .toOption.flatten.get.state, finished.state)
  }

  test("preview offers the walker Catacombs contribution, and " +
      "OathRules.startWalker accepts exactly the offered id (Task 9a)") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository,
      defenseDicePort = new CountingRecoverDice)
    val gameId = "catacombs-walker-preview"
    val (prepared, actor, _) = prepareCatacombs(service, gameId, catacombsPlan)
    val preview = service.preview(gameId, prepared.nextSequence, actor,
      ActionKind.Recover, Vector.empty).toOption.get
    val offeredIds = preview.options.map(v => PowerId(v.handlerId))
    assertEquals(offeredIds, Vector(PowerId("denizen.catacombs")))

    // The property that matters: the id the preview just offered is exactly
    // the id `OathRules.startWalker` will accept -- not merely "some id
    // that happens to work". A defect here (offered-but-rejected, or
    // accepted-but-never-offered) is precisely what Task 9a exists to
    // prevent. Asserted directly against `OathRules` because that is the
    // layer owning `validateModifiers`, the accepting predicate; the
    // persisted path the preview actually feeds is covered separately by
    // "StartWalker drives Catacombs through the full persisted path" above.
    // (That test exists because this rules-only assertion once hid a real
    // defect: `WalkerEventCodec` could not encode the `Location.Deck` in
    // Catacombs' relic move, so `StartWalker` passed here and failed at
    // append. The codec is now total over `Location`.)
    val rules = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowerCatalog.default(catalog))
    val started = rules.startWalker(prepared.state, ActionRef.Recover, actor,
      offeredIds)
    assert(started.isRight,
      s"expected StartWalker to accept the previewed id, got $started")

    // A legacy action's preview is untouched: still resolved through
    // `PowerRuntime`, not the walker catalog.
    val travelPreview = service.preview(gameId, prepared.nextSequence, actor,
      ActionKind.Travel, Vector.empty)
    assert(travelPreview.isRight)
  }

  test("all-Exile powered game persists and replays through round-eight victory") {
    val repository = new InMemoryEventStreamRepository
    val whenPlayedPower = DenizenId(catalog.denizens.find(
      _.handlers.contains("denizen.revelation")).get.id.value)
    def place(order: Vector[DenizenId], index: Int, id: DenizenId) = {
      val current = order.indexWhere(_.value == id.value)
      if (current < 0) order.updated(index, id)
      else order.updated(index, id).updated(current, order(index))
    }
    val p2Index = 6 + plan.participants.indexWhere(_.playerId == PlayerId("p2")) * 3
    val poweredOrder = place(plan.denizenOrder, p2Index, whenPlayedPower)
    val remaining = poweredOrder.drop(6 + plan.participants.size * 3)
    val poweredWorldDeck = remaining.take(10) ++
      oathdigital.gameplay.setup.FirstGameRulesData.visions.take(2) ++
      remaining.slice(10, 25) ++
      oathdigital.gameplay.setup.FirstGameRulesData.visions.drop(2) ++ remaining.drop(25)
    val poweredPlan = plan.copy(denizenOrder = poweredOrder,
      worldDeckOrder = poweredWorldDeck)
    var service = new GameApplicationService(catalog, repository,
      warExhaustionRandomPort = new oathdigital.gameplay.phases.rest.WarExhaustionRandomPort {
        def choose(candidates: Vector[PlayerId]) = candidates.head
      })
    var accepted = execute(service, "powered-playability", setupPlan = poweredPlan)
    var played = Set.empty[PlayerId]
    var safety = 0
    while ({
      val Ready(ready) = accepted.state: @unchecked
      ready.game.current.result.isEmpty
    }) {
      safety += 1
      assert(safety <= 24, "all-Exile game should finish after eight rounds")
      val Ready(wake) = accepted.state: @unchecked
      val actor = wake.game.current.turn.activePlayer
      accepted = service.handle("powered-playability", accepted.nextSequence,
        GameCommand.EndWake(actor)).toOption.get
      if (!played(actor)) {
        val Ready(act) = accepted.state: @unchecked
        val adviser = act.game.current.players.find(_.player == actor).get.advisers.head.id
          .asInstanceOf[WorldCardId]
        accepted = service.handle("powered-playability", accepted.nextSequence,
          GameCommand.StartWalker(ActionRef.PlayFacedownAdviser,
            StartPayload(actor, Vector.empty, Vector(adviser match {
              case id: DenizenId => DecisionOptionRef.Denizen(id)
              case id: VisionId => DecisionOptionRef.Vision(id)
            })))).toOption.get
        accepted = service.handle("powered-playability", accepted.nextSequence,
          GameCommand.ResolveWalker(actor, TreeDecision(
            s"cardplay.place.${adviser.kind}.${adviser.value}",
            ChooseOneAnswer(DecisionOptionRef.Button("adviser-faceup")))))
          .toOption.get
        played += actor
      }
      accepted = service.handle("powered-playability", accepted.nextSequence,
        GameCommand.BeginRest(actor)).toOption.get
      service = new GameApplicationService(catalog, repository,
        warExhaustionRandomPort = new oathdigital.gameplay.phases.rest.WarExhaustionRandomPort {
          def choose(candidates: Vector[PlayerId]) = candidates.head
        })
      val reopened = service.load("powered-playability").toOption.flatten.get
      assertEquals(reopened.state, accepted.state)
      assertEquals(reopened.nextSequence, accepted.nextSequence)
    }
    val Ready(finished) = accepted.state: @unchecked
    assert(finished.game.current.result.nonEmpty)
    val projector = new GameProjector(catalog)
    finished.game.current.players.foreach { player =>
      val view = projector.project("powered-playability",
        LoadedGame(accepted.state, accepted.nextSequence),
        player.player)
      assertEquals(view.phase, "game-over")
      assertEquals(view.legalControls, Vector.empty)
      assert(!view.actionSelectionOpen)
      assertEquals(view.oathkeeper.flatMap(_.winnerPlayerId),
        finished.game.current.result.map(_.winner.value))
      assert(view.world.flatMap(_.sites).nonEmpty)
    }
    val raw = repository.load("powered-playability").toOption.flatten.get.records
    assert(raw.exists(_.contains("diagnostic.ignored-rules-recorded")))
    assert(raw.exists(_.contains("reviewed-unimplemented-pre-alpha-fallback")))
  }
  test("major-action preview is stateless stale-safe and rejects unavailable modifiers") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val setup = execute(service, "game-preview")
    val Ready(ready) = setup.state: @unchecked
    val actor = ready.game.current.turn.activePlayer
    val act = service.handle("game-preview", setup.nextSequence,
      GameCommand.EndWake(actor)).toOption.get
    val before = repository.load("game-preview").toOption.flatten.get.records
    val preview = service.preview("game-preview", act.nextSequence, actor,
      oathdigital.model.ActionKind.Travel, Vector.empty).toOption.get
    assertEquals(preview.loaded.nextSequence, act.nextSequence)
    assertEquals(repository.load("game-preview").toOption.flatten.get.records, before)
    assert(service.preview("game-preview", act.nextSequence - 1, actor,
      oathdigital.model.ActionKind.Travel, Vector.empty).left.toOption
      .exists(_.isInstanceOf[GameApplicationError.StaleClientPosition]))
    val forged = oathdigital.model.OrderedRuleInvocation(
      oathdigital.model.RuleSourceRef.GameRule("forged"), "unknown")
    assert(service.preview("game-preview", act.nextSequence, actor,
      oathdigital.model.ActionKind.Travel, Vector(forged)).isLeft)
    val adviser = ready.game.current.players.find(_.player == actor).get.advisers.head.id
      .asInstanceOf[WorldCardId]
    assert(service.handle("game-preview", act.nextSequence,
      GameCommand.WithModifiers(GameCommand.StartWalker(
        ActionRef.PlayFacedownAdviser, StartPayload(actor, Vector.empty,
          Vector(adviser match {
            case id: DenizenId => DecisionOptionRef.Denizen(id)
            case id: VisionId => DecisionOptionRef.Vision(id)
          }))), Vector(forged))).isLeft)
    val challenge = service.preview("game-preview", act.nextSequence, actor,
      oathdigital.model.ActionKind.Challenge, Vector.empty).toOption.get
    assertEquals(challenge.options, Vector.empty)
    assertEquals(challenge.ignored, Vector.empty)
  }
  test("minor adviser action persists and reloads through authoritative replay") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val setup = execute(service, "game-minor-replay")
    val Ready(ready) = setup.state: @unchecked
    val actor = ready.game.current.turn.activePlayer
    val adviser = ready.game.current.players.find(_.player == actor).get.advisers.head.id
      .asInstanceOf[WorldCardId]
    val act = service.handle("game-minor-replay", setup.nextSequence,
      GameCommand.EndWake(actor)).toOption.get
    val started = service.handle("game-minor-replay", act.nextSequence,
      GameCommand.StartWalker(ActionRef.PlayFacedownAdviser,
        StartPayload(actor, Vector.empty, Vector(adviser match {
          case id: DenizenId => DecisionOptionRef.Denizen(id)
          case id: VisionId => DecisionOptionRef.Vision(id)
        })))).toOption.get
    val discarded = service.handle("game-minor-replay", started.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(
        s"cardplay.place.${adviser.kind}.${adviser.value}",
        ChooseOneAnswer(DecisionOptionRef.Button("discard"))))).toOption.get
    val beforeRetry = repository.load("game-minor-replay").toOption.flatten.get.records
    assert(service.handle("game-minor-replay", discarded.nextSequence,
      GameCommand.StartWalker(ActionRef.PlayFacedownAdviser,
        StartPayload(actor, Vector.empty, Vector(adviser match {
          case id: DenizenId => DecisionOptionRef.Denizen(id)
          case id: VisionId => DecisionOptionRef.Vision(id)
        })))).isLeft)
    assertEquals(repository.load("game-minor-replay").toOption.flatten.get.records,
      beforeRetry)
    val reloaded = new GameApplicationService(catalog, repository)
      .load("game-minor-replay").toOption.flatten.get
    assertEquals(reloaded.state, discarded.state)
    assertEquals(reloaded.nextSequence, discarded.nextSequence)
  }

  test("Challenge persists its walker park and reloads deterministic Mob completion") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val gameId = "game-challenge-persistence"
    val wealthSite = catalog.sites.find(_.startingResources.favor > 0).get.id
    val orderedSites = wealthSite +: sites.filterNot(_ == wealthSite).take(7)
    val challengePlan = plan.copy(orderedSites = orderedSites)
    var accepted = service.handle(gameId, 0L, GameCommand.Begin(challengePlan)).toOption.get
    val order = Vector(PlayerId("p2"), PlayerId("p3"), PlayerId("p1"))
    order.zipWithIndex.foreach { case (playerId, index) =>
      accepted = service.handle(gameId, accepted.nextSequence,
        GameCommand.PlacePawn(playerId, challengePlan.orderedSites(index))).toOption.get
      val participantIndex = challengePlan.participants.indexWhere(_.playerId == playerId)
      accepted = service.handle(gameId, accepted.nextSequence,
        GameCommand.ChooseAdviser(playerId,
          challengePlan.denizenOrder(6 + participantIndex * 3))).toOption.get
    }
    val Ready(setupReady) = accepted.state: @unchecked
    val actor = setupReady.game.current.turn.activePlayer
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.StartWalker(ActionRef.TakeWealth, StartPayload(actor,
        Vector.empty, Vector(DecisionOptionRef.Button("favor"))))).toOption.get
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.EndWake(actor)).toOption.get
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.StartWalker(ActionRef.Challenge, StartPayload(actor)))
      .toOption.get
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision("challenge.banner",
        ChooseOneAnswer(DecisionOptionRef.Banner(Banner.PeoplesFavor)))))
      .toOption.get
    val reloaded = new GameApplicationService(catalog, repository)
      .load(gameId).toOption.flatten.get
    assertEquals(reloaded.state, accepted.state)
    val Ready(pendingReady) = reloaded.state: @unchecked
    assertEquals(pendingReady.game.current.walkerProcedure,
      Some(ActionRef.Challenge))
    val other = pendingReady.game.current.players.find(_.player != actor).get.player
    val projector = new GameProjector(catalog)
    assertEquals(projector.project(gameId, reloaded, actor).legalControls,
      Vector("resolveWalkerDecision"))
    assertEquals(projector.project(gameId, reloaded, other).legalControls,
      Vector.empty)
    val completed = new GameApplicationService(catalog, repository).handle(gameId,
      reloaded.nextSequence, GameCommand.ResolveWalker(actor, TreeDecision(
        "challenge.amount", ChooseAmountAnswer(2)))).toOption.get
    val Ready(after) = completed.state: @unchecked
    assertEquals(after.game.current.banners.peoplesFavor.holder, Some(actor))
    assertEquals(after.game.current.banners.peoplesFavor.favor, 2)
    val claimed = projector.project(gameId,
      LoadedGame(completed.state, completed.nextSequence), actor)
    assert(!claimed.banners.exists(_.key == "peoples-favor"))
    assertEquals(claimed.playerBoards.find(_.playerId == actor.value).toVector
      .flatMap(_.banners).map(banner => banner.key -> banner.resources),
      Vector("peoples-favor" -> 2))
  }

  // ---------------------------------------------------------------------------
  // Forge runs end to end on the generic walker, through
  // `StartWalker`/`ResolveWalker` alone, and its journal replays to the same
  // state. The replay is what proves the answer codec: a `PartitionAnswer`
  // rides both a `WalkerStepRecorded` ChoicePayload and the `WalkerParked`
  // fact, so the encoder is reached on append and the decoder on every
  // reload -- the in-memory run alone would not necessarily touch either.
  // ---------------------------------------------------------------------------

  test("walker Forge completes through StartWalker/ResolveWalker alone and " +
      "replays to the same final state") {
    val repository = new InMemoryEventStreamRepository
    // The only non-homeland forgeable site prints three favor, which is a
    // forced split the engine resolves without prompting (the next test
    // covers that). Overriding just that site's printed cost is what gives
    // the PARKED flow a real end-to-end run: the site, its denizens and
    // every other rule stay exactly as shipped.
    val forgeCatalog = mixedForgeCostCatalog
    val service = new GameApplicationService(forgeCatalog, repository,
      campaignDicePort = blankCampaignDice)
    val gameId = "game-walker-forge"
    val (ready, actor, forgeSite) = forgeReadyGame(service, gameId,
      forgeCatalog)
    val Ready(beforeStart) = ready.state: @unchecked
    val supplyBefore = beforeStart.game.current.players
      .find(_.player == actor).get.board.supply.supply
    val relic = beforeStart.game.current.commonCards.relicDeck.head
    val banksBefore = beforeStart.banks.favor
    val actorBefore = beforeStart.game.current.players
      .find(_.player == actor).get
    val favorBefore = actorBefore.board.favor
    val secretsBefore = actorBefore.board.faceUpSecrets

    val started = service.handle(gameId, ready.nextSequence,
      GameCommand.StartWalker(ActionRef.Forge, StartPayload(actor)))
      .fold(error => fail(s"walker Forge start rejected: $error"), identity)
    assert(started.events.last.isInstanceOf[WalkerParked])
    assertEquals(started.continue, OathContinue.AwaitingForgeAssignment(actor,
      DecisionId(ForgeProcedure.assignmentDecisionId)))
    val Ready(parked) = started.state: @unchecked
    assertEquals(parked.game.current.walkerProcedure, Some(ActionRef.Forge))
    assertEquals(parked.game.current.players.find(_.player == actor).get
      .board.supply.supply, supplyBefore - 1)

    // The prompt the client actually answers from, reloaded through the
    // codec rather than read off the in-memory transition.
    val parkedLoaded = service.load(gameId).toOption.flatten.get
    assertEquals(parkedLoaded.state, started.state)
    val projector = new GameProjector(forgeCatalog)
    val owner = projector.project(gameId, parkedLoaded, actor)
    val other = parked.game.current.players.find(_.player != actor).get.player
    // Task 4: the prompt IS the projected query. There is no Forge-shaped
    // projection any more -- two declared sections carrying the printed
    // minima, and one denizen option per live eligible target, described
    // from the same `Decide` the walker is parked on.
    val decision = owner.walkerDecision.getOrElse(
      fail("the parked actor must be offered the Forge assignment prompt"))
    assertEquals(decision.decisionId, ForgeProcedure.assignmentDecisionId)
    val prompt = decision.query.getOrElse(
      fail("a parked Forge decision must project its query"))
    assertEquals(prompt.form, "partition")
    val printed = forgeCatalog.sites.find(_.id == forgeSite).get
      .forgeRequirements.get
    assertEquals(prompt.sections, Vector(
      DecisionSectionProjection(ForgeProcedure.favorSectionKey, "Pay Favor",
        printed.favor),
      DecisionSectionProjection(ForgeProcedure.secretSectionKey, "Pay Secret",
        printed.secrets)))
    assertEquals(prompt.options.size, 3)
    assertEquals(prompt.options.map(_.kind).distinct, Vector("denizen"))
    // The options are the live eligible targets at the Forge site, with
    // their presentation card details -- never a set the projector derived
    // by consulting `ForgeProcedure` a second time.
    assertEquals(prompt.options.map(_.id).toSet,
      ForgeProcedure.eligibleTargets(parked, actor)
        .map(_.denizenId.value).toSet)
    assert(prompt.options.forall(_.card.nonEmpty))
    val favorMinimum = printed.favor
    val secretMinimum = printed.secrets
    assertEquals(favorMinimum + secretMinimum, 3)
    assertEquals(owner.phase, "forge-walker-decision")
    assertEquals(owner.legalControls, Vector("resolveWalkerDecision"))
    // Forge has no dice, and its parked decision says so. (This is a
    // client-facing fact, not R18's proof: no site in the catalog carries
    // both a printed Forge cost and a Recover difficulty, so `rollOutcome`
    // is `None` here for want of a difficulty either way. R18's projector
    // call site is proven in `WalkerDecisionProjectorSuite`.)
    assertEquals(owner.walkerDecision.flatMap(_.rollOutcome), None)
    assertEquals(projector.project(gameId, parkedLoaded, other)
      .walkerDecision, None)
    assertEquals(projector.projectPublic(gameId, parkedLoaded)
      .walkerDecision, None)

    // The answer is built from the projected prompt, exactly as the UI
    // builds it: the offered targets, in order, taking the offered counts.
    val sections = Vector.fill(favorMinimum)(ForgeProcedure.favorSectionKey) ++
      Vector.fill(secretMinimum)(ForgeProcedure.secretSectionKey)
    val placements = prompt.options.zip(sections).map { case (option, section) =>
      DecisionPlacement(DecisionOptionRef.Denizen(DenizenId(option.id)),
        section) }

    val beforeRejected = repository.load(gameId).toOption.flatten.get.records
    Vector[GameCommand](
      GameCommand.ResolveWalker(actor, TreeDecision("forge.stale",
        PartitionAnswer(placements))),
      GameCommand.ResolveWalker(other, TreeDecision(
        ForgeProcedure.assignmentDecisionId,
        PartitionAnswer(placements))),
      GameCommand.ResolveWalker(actor, TreeDecision(
        ForgeProcedure.assignmentDecisionId,
        PartitionAnswer(placements.updated(1, placements.head)))),
      GameCommand.ResolveWalker(actor, TreeDecision(
        ForgeProcedure.assignmentDecisionId, PartitionAnswer(
          placements.map(_.copy(sectionKey = ForgeProcedure.secretSectionKey))))),
      GameCommand.BeginRest(actor)
    ).foreach(command => assert(service.handle(gameId, started.nextSequence,
      command).isLeft, s"$command must be rejected while Forge is parked"))
    assertEquals(repository.load(gameId).toOption.flatten.get.records,
      beforeRejected, "a rejected command must append nothing")

    val finished = service.handle(gameId, started.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(
        ForgeProcedure.assignmentDecisionId,
        PartitionAnswer(placements))))
      .fold(error => fail(s"walker Forge answer rejected: $error"), identity)
    assert(finished.events.exists(_.isInstanceOf[WalkerCompleted]))
    val Ready(after) = finished.state: @unchecked

    // The same final state the deleted legacy path produced: three
    // denizens each carrying exactly one resource, the relic facedown in the
    // actor's play area, the deck advanced by one, one Supply spent in total,
    // and nothing pending on either mechanism.
    assertEquals(after.game.current.map.sites(forgeSite).denizens.collect {
      case d: DenizenState => d.tokens.favor + d.tokens.secrets }, Vector(1, 1, 1))
    assertEquals(after.game.current.players.find(_.player == actor).get.relics.last,
      RelicState(relic, Orientation.FaceDown, Tokens.empty))
    assertEquals(after.game.current.commonCards.relicDeck,
      beforeStart.game.current.commonCards.relicDeck.drop(1))
    assertEquals(after.game.current.players.find(_.player == actor).get
      .board.supply.supply, supplyBefore - 1)
    assert(after.game.current.walkerPending.isEmpty)
    assert(after.game.current.walkerProcedure.isEmpty)

    // The actor funded the whole printed cost out of their own play area,
    // and no suit bank moved at all. This reverses the pre-walker behaviour
    // of drawing each favor from the target denizen's own suit bank.
    assertEquals(after.banks.favor, banksBefore)
    val actorAfter = after.game.current.players.find(_.player == actor).get
    assertEquals(actorAfter.board.favor, favorBefore - favorMinimum)
    assertEquals(actorAfter.board.faceUpSecrets,
      secretsBefore - secretMinimum)

    // P2: reconstructing purely from the journal reproduces that state.
    val replayed = new GameApplicationService(forgeCatalog, repository)
      .load(gameId).toOption.flatten.get
    assertEquals(replayed.state, finished.state)

    // The forged relic stays private to its owner.
    val otherJson = GameHttpWire.encodeProjection(
      projector.project(gameId, replayed, other))
    assert(!otherJson.contains(relic.value))
    assert(!GameHttpWire.encodeProjection(
      projector.projectPublic(gameId, replayed)).contains(relic.value))
  }

  test("a Forge whose printed cost is three of one resource completes in " +
      "the command that starts it, with no decision to answer") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository,
      campaignDicePort = blankCampaignDice)
    val gameId = "game-walker-forge-forced"
    val (ready, actor, forgeSite) = forgeReadyGame(service, gameId)
    val Ready(beforeStart) = ready.state: @unchecked
    val cost = catalog.sites.find(_.id == forgeSite).get.forgeRequirements.get
    assert(cost.favor == 0 || cost.secrets == 0,
      s"this test needs a single-resource printed cost, got $cost")
    val relic = beforeStart.game.current.commonCards.relicDeck.head
    val actorBefore = beforeStart.game.current.players
      .find(_.player == actor).get
    val banksBefore = beforeStart.banks.favor

    val finished = service.handle(gameId, ready.nextSequence,
      GameCommand.StartWalker(ActionRef.Forge, StartPayload(actor)))
      .fold(error => fail(s"walker Forge start rejected: $error"), identity)

    // One command: the action completes, nothing parks, and the client is
    // never asked to confirm a split it could not have got wrong.
    assert(finished.events.exists(_.isInstanceOf[WalkerCompleted]))
    assert(!finished.events.exists(_.isInstanceOf[WalkerParked]))
    assertEquals(finished.continue, OathContinue.ActActionSelection(actor))
    val Ready(after) = finished.state: @unchecked
    assert(after.game.current.walkerPending.isEmpty)
    assert(after.game.current.walkerProcedure.isEmpty)
    assertEquals(new GameProjector(catalog).project(gameId,
      LoadedGame(finished.state, finished.nextSequence), actor)
      .walkerDecision, None)

    // And the determined split really was applied, out of the actor's own
    // play area.
    assertEquals(after.game.current.map.sites(forgeSite).denizens.collect {
      case d: DenizenState => d.tokens.favor + d.tokens.secrets }, Vector(1, 1, 1))
    assertEquals(after.banks.favor, banksBefore)
    val actorAfter = after.game.current.players.find(_.player == actor).get
    assertEquals(actorAfter.board.favor, actorBefore.board.favor - cost.favor)
    assertEquals(actorAfter.board.faceUpSecrets,
      actorBefore.board.faceUpSecrets - cost.secrets)
    assertEquals(actorAfter.relics.last,
      RelicState(relic, Orientation.FaceDown, Tokens.empty))

    // The journal replays to the same state even though it carries a
    // completion that was never preceded by a park.
    assertEquals(new GameApplicationService(catalog, repository)
      .load(gameId).toOption.flatten.get.state, finished.state)
  }

  private def execute(
      service: GameApplicationService,
      gameId: String,
      placementSites: Vector[oathdigital.model.SiteId] = sites,
      setupPlan: oathdigital.model.FirstGameSetupPlan = plan
  ): GameAccepted = {
    var accepted =
      service.handle(gameId, 0L, GameCommand.Begin(setupPlan))
        .toOption.get
    val order = Vector(PlayerId("p2"), PlayerId("p3"), PlayerId("p1"))
    order.zipWithIndex.foreach { case (playerId, index) =>
      accepted = service.handle(
        gameId,
        accepted.nextSequence,
        GameCommand.PlacePawn(playerId, placementSites(index))
      ).toOption.get
      val participantIndex =
        setupPlan.participants.indexWhere(_.playerId == playerId)
      accepted = service.handle(
        gameId,
        accepted.nextSequence,
        GameCommand.ChooseAdviser(
          playerId,
          setupPlan.denizenOrder(6 + participantIndex * 3)
        )
      ).toOption.get
    }
    accepted
  }

  test("a walker Campaign persists every command and replays to the same state") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository,
      campaignDicePort = blankCampaignDice)
    val (accepted, _, _) = forgeReadyGame(service, "walker-campaign")
    val reloaded = new GameApplicationService(catalog, repository,
      campaignDicePort = blankCampaignDice).load("walker-campaign")
      .toOption.flatten.get
    assertEquals(reloaded.state, accepted.state)
    assertEquals(reloaded.nextSequence, accepted.nextSequence)
    val Ready(after) = reloaded.state: @unchecked
    assert(after.game.current.lastCampaignResult.exists(_.attackerWins))
  }

  test("create advance and reload replay the complete persisted v2 stream") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val accepted = execute(service, "game-v2")
    val reloaded = new GameApplicationService(catalog, repository)
      .load("game-v2").toOption.flatten.get

    assertEquals(reloaded.state, accepted.state)
    assertEquals(reloaded.nextSequence, 8L)
    assert(reloaded.state.isInstanceOf[Ready])
    val records = repository.load("game-v2").toOption.flatten.get.records
    assertEquals(records.size, 8)
    assert(records.forall(record =>
      ujson.read(record)("formatVersion").num.toInt ==
        GameEventWire.FormatVersion))
  }

  test("reload preserves a non-Supremacy setup goal") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val accepted = execute(service, "game-protection", setupPlan =
      plan.copy(oathkeeperGoal = OathkeeperGoal.Protection))
    val reloaded = new GameApplicationService(catalog, repository)
      .load("game-protection").toOption.flatten.get

    assertEquals(reloaded.state, accepted.state)
    assertEquals(reloaded.state.asInstanceOf[Ready].value.game.campaign
      .oathkeeperGoal, OathkeeperGoal.Protection)
  }

  test("gameplay appends v3 at the absolute position and reloads equally") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val wealthSite = catalog.sites.find(site =>
      sites.contains(site.id) && !site.startingResources.isEmpty).get.id
    val otherSites = sites.filterNot(_ == wealthSite).take(2)
    val setup = execute(
      service,
      "game-wake",
      wealthSite +: otherSites
    )
    val Ready(ready) = setup.state: @unchecked
    val active = ready.game.current.turn.activePlayer
    val siteId = ready.game.current.players.find(_.player == active)
      .flatMap(_.pawnSite).get
    val site = ready.game.current.map.sites(siteId)
    val resource = if (site.tokens.favor > 0) "favor" else "secret"

    val wealth = service.handle("game-wake", setup.nextSequence,
      GameCommand.StartWalker(ActionRef.TakeWealth, StartPayload(active,
        Vector.empty, Vector(DecisionOptionRef.Button(resource))))).toOption.get
    // Take Wealth is one atomic walker command (batch-1 Task 7): the single
    // legacy event became the walker's three -- the resource move, the use
    // record, and the completion -- so the next free position moves by three.
    assertEquals(wealth.nextSequence, 11L)
    assertEquals(
      service.handle("game-wake", 8L, GameCommand.EndWake(active)),
      Left(GameApplicationError.StaleClientPosition(8L, 11L))
    )
    val Ready(afterTake) = wealth.state: @unchecked
    // The phase did not end with the action: a completed Wake action returns
    // its player to Wake, and the limit it recorded survives the reload below.
    assertEquals(afterTake.game.current.turn.phase, Phase.Wake)
    assertEquals(wealth.continue, OathContinue.AwaitingWakeAction(active))
    val ended = service.handle(
      "game-wake",
      wealth.nextSequence,
      GameCommand.EndWake(active)
    ).toOption.get
    val reloaded = new GameApplicationService(catalog, repository)
      .load("game-wake").toOption.flatten.get
    val Ready(after) = reloaded.state: @unchecked

    assertEquals(reloaded.state, ended.state)
    assertEquals(after.game.current.turn.phase, Phase.Act)
    assert(after.game.current.turn.usedPowers.contains(
      oathdigital.gameplay.powers.wake.TakeWealthLimit.useRef(siteId)),
      "the replayed journal must restore the use limit it recorded")
    val records = repository.load("game-wake").toOption.flatten.get.records
    assertEquals(records.take(8).map(record =>
      ujson.read(record)("formatVersion").num.toInt).distinct, Vector(1))
    assertEquals(records.drop(8).map(record =>
      ujson.read(record)("formatVersion").num.toInt), Vector(1, 1, 1, 1, 1))
    // Ending Wake is a walker procedure too now (batch-1 Task 7), so the
    // Wake phase journals nothing of its own: the last two records are its
    // phase-change step and its completion, not a `gameplay.wake-ended`.
    assertEquals(records.drop(8).map(record =>
      ujson.read(record)("eventType").str),
      Vector("walker.step-recorded", "walker.step-recorded",
        "walker.completed", "walker.step-recorded", "walker.completed"))
  }

  test("Travel is one atomic walker command and reloads pawn Supply and Act") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val setup = execute(service, "game-travel")
    val Ready(ready) = setup.state: @unchecked
    val active = ready.game.current.turn.activePlayer
    val ended = service.handle("game-travel", setup.nextSequence,
      GameCommand.EndWake(active)).toOption.get
    val Ready(inAct) = ended.state: @unchecked
    val before = inAct.game.current.players.find(_.player == active).get
    val destination = inAct.game.current.map.cradle.find(
      !before.pawnSite.contains(_)).getOrElse(
        inAct.game.current.map.provinces.head)
    val traveled = service.handle("game-travel", ended.nextSequence,
      GameCommand.StartWalker(ActionRef.Travel, StartPayload(active,
        Vector.empty, Vector(DecisionOptionRef.Site(destination))))
      ).toOption.get
    val loaded = new GameApplicationService(catalog, repository)
      .load("game-travel").toOption.flatten.get
    val Ready(after) = loaded.state: @unchecked
    val moved = after.game.current.players.find(_.player == active).get

    assertEquals(loaded.state, traveled.state)
    assertEquals(moved.pawnSite, Some(destination))
    assert(moved.board.supply.supply < before.board.supply.supply)
    assertEquals(after.game.current.turn.phase, Phase.Act)
    // The single legacy travel event became the walker's own three: the pay
    // node, the pawn move, and the completion boundary. That is the whole
    // observable shape change of the port -- the state each side reaches is
    // identical, which is what the assertions above pin.
    assertEquals(loaded.nextSequence, ended.nextSequence + 3)
    assertEquals(traveled.events.map(_.productPrefix),
      Vector("WalkerStepRecorded", "WalkerStepRecorded", "WalkerCompleted"))
    // Nothing parked: a flat tree finishes inside the command that started it.
    assertEquals(after.game.current.walkerPending, None)
    assertEquals(after.game.current.walkerProcedure, None)
    val types = repository.load("game-travel").toOption.flatten.get.records
      .takeRight(3).map(ujson.read(_)("eventType").str)
    assertEquals(types, Vector("walker.step-recorded", "walker.step-recorded",
      "walker.completed"))
  }

  /** Item 10 of the final fix brief: an off-turn Oathkeeper tie, arranged and
    * resolved entirely through `GameApplicationService`, survives reload on
    * both sides of the park.
    *
    * `FirstGameSetup` places no initial site forces, so every player starts
    * ruling zero sites; a single synthetic `WalkerStepRecorded` delta step --
    * seeded as one more record on the same stream the setup commands already
    * wrote, via the same `GameEventWire.encodeEvent`/`repository.append`
    * seam the malformed/historical-envelope tests above use to inject
    * hand-picked records -- gives a title holder and two OTHER players one
    * ruled site apiece. `WalkerReplay`'s delta branch applies the carried
    * `SetOathkeeper`/`Move` operations through the same `OperationExecutor`
    * real gameplay uses, with no pending-walker precondition, so this is a
    * legitimate use of production machinery rather than a bypass of it. With
    * exactly three players, the active player is automatically one of the
    * two tied leaders and the holder is automatically off-turn: no further
    * arrangement is needed to reach `OathkeeperRules.outcome`'s `Choose`.
    */
  test("an off-turn Oathkeeper tie parks through the application service " +
      "and survives reload") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val gameId = "game-oathkeeper-tie"
    val (parked, active, holder, leaderB) =
      ParkedServiceFixture.oathkeeperTiePark(service, repository, gameId)
    assertEquals(
      repository.load(gameId).toOption.flatten.get.records.size.toLong,
      parked.nextSequence)

    val reloadedParked = service.load(gameId).toOption.flatten.get
    assertEquals(reloadedParked.state, parked.state)
    assertEquals(reloadedParked.nextSequence, parked.nextSequence)

    // The application gate rejects the active player's answer: only the
    // holder may resolve the recipient decision.
    assertEquals(
      service.handle(gameId, parked.nextSequence, GameCommand.ResolveWalker(
        active, TreeDecision(OathkeeperProcedure.recipientDecisionId,
          ChooseOneAnswer(DecisionOptionRef.Player(leaderB))))),
      Left(GameApplicationError.CommandRejected(
        WrongPlayer(holder, active))))

    val resolved = service.handle(gameId, parked.nextSequence,
      GameCommand.ResolveWalker(holder, TreeDecision(
        OathkeeperProcedure.recipientDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Player(leaderB))))).toOption.get
    assert(resolved.events.exists {
      case WalkerStepRecorded(_, ChoicePayload(_, _, by), _, _) => by == holder
      case _ => false
    }, "the recorded choice must be answered by the holder")
    assertEquals(resolved.continue, OathContinue.ActActionSelection(active))
    val Ready(afterResolved) = resolved.state: @unchecked
    assertEquals(afterResolved.game.current.title,
      OathkeeperState(Some(leaderB), TitleSide.Oathkeeper))

    val reloadedResolved = service.load(gameId).toOption.flatten.get
    assertEquals(reloadedResolved.state, resolved.state)
    assertEquals(reloadedResolved.nextSequence, resolved.nextSequence)
  }

  test("a walker Muster on an edifice persists, reloads and replays with its kind") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val (siteId, edificeId) = plan.homelandEdifices.head
    val placements = siteId +: sites.filterNot(_ == siteId).take(2)
    val setup = execute(service, "game-walker-economy", placements)
    val Ready(ready) = setup.state: @unchecked
    val active = ready.game.current.turn.activePlayer
    val ended = service.handle("game-walker-economy", setup.nextSequence,
      GameCommand.EndWake(active)).toOption.get
    val started = service.handle("game-walker-economy", ended.nextSequence,
      GameCommand.StartWalker(ActionRef.Muster, StartPayload(active))).toOption.get
    assertEquals(started.continue, OathContinue.AwaitingEconomyDecision(active,
      DecisionId(MusterProcedure.decisionId)))
    val mustered = service.handle("game-walker-economy", started.nextSequence,
      GameCommand.ResolveWalker(active, TreeDecision(MusterProcedure.decisionId,
        ChooseOneAnswer(DecisionOptionRef.Edifice(edificeId))))).toOption.get
    val loaded = new GameApplicationService(catalog, repository)
      .load("game-walker-economy").toOption.flatten.get
    assertEquals(loaded.state, mustered.state)
    val Ready(after) = loaded.state: @unchecked
    assertEquals(after.game.current.map.sites(siteId).denizens.collectFirst {
      case value: EdificeState if value.id == edificeId => value.tokens
    }, Some(Tokens(1, 0)))
    val records = repository.load("game-walker-economy").toOption.flatten.get.records
    assert(records.exists(record => record.contains("edifice") &&
      record.contains(edificeId.value)),
      "the journalled answer must spell the edifice kind and id")
  }

  test("Search draw port cannot inject card identities inconsistent with state") {
    val repository = new InMemoryEventStreamRepository
    val port = new SearchDrawPort {
      def prepare(ready: ReadyGame, source: SearchSource, origin: Region) =
        Right(Vector(DenizenId("denizen:tampered")))
    }
    val service = new GameApplicationService(catalog, repository, port)
    val setup = execute(service, "game-search-tamper")
    val Ready(ready) = setup.state: @unchecked
    val active = ready.game.current.turn.activePlayer
    val ended = service.handle("game-search-tamper", setup.nextSequence,
      GameCommand.EndWake(active)).toOption.get
    assert(service.handle("game-search-tamper", ended.nextSequence,
      GameCommand.StartWalker(ActionRef.Search, StartPayload(active,
        Vector.empty, Vector(DecisionOptionRef.Button("search:world")))))
      .left.toOption.get.isInstanceOf[GameApplicationError.CommandRejected])
    assertEquals(repository.load("game-search-tamper").toOption.flatten.get
      .nextSequence, ended.nextSequence)
  }

  test("walker Search persists its card choice and completes after reload") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val gameId = "game-walker-search"
    val setup = execute(service, gameId)
    val Ready(ready) = setup.state: @unchecked
    val actor = ready.game.current.turn.activePlayer
    val act = service.handle(gameId, setup.nextSequence,
      GameCommand.EndWake(actor)).toOption.get
    val started = service.handle(gameId, act.nextSequence,
      GameCommand.StartWalker(ActionRef.Search, StartPayload(actor,
        Vector.empty, Vector(DecisionOptionRef.Button("search:world")))))
      .toOption.get
    val reloaded = new GameApplicationService(catalog, repository)
      .load(gameId).toOption.flatten.get
    assertEquals(reloaded.state, started.state)
    val Ready(afterDraw) = reloaded.state: @unchecked
    val drawn = afterDraw.game.current.temporaryHands(actor)
    val chosen = if (drawn.size == 1) started else {
      def ref(card: WorldCardId): DecisionOptionRef = card match {
        case id: DenizenId => DecisionOptionRef.Denizen(id)
        case id: VisionId => DecisionOptionRef.Vision(id)
      }
      val assignments = Vector(DecisionPlacement(ref(drawn.head), "keep")) ++
        drawn.tail.map(card => DecisionPlacement(ref(card), "discard"))
      service.handle(gameId, reloaded.nextSequence,
        GameCommand.ResolveWalker(actor, TreeDecision("search.cards",
          DecisionAnswer.PartitionAnswer(assignments)))).toOption.get
    }
    val completed = service.handle(gameId, chosen.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(
        s"cardplay.place.${drawn.head.kind}.${drawn.head.value}",
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("discard")))))
      .toOption.get
    assertEquals(new GameApplicationService(catalog, repository)
      .load(gameId).toOption.flatten.get.state, completed.state)
  }

  test("facedown adviser plays through the shared walker after reload") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val gameId = "game-walker-facedown-adviser"
    val setup = execute(service, gameId)
    val Ready(ready) = setup.state: @unchecked
    val actor = ready.game.current.turn.activePlayer
    val adviser = ready.game.current.players.find(_.player == actor).get
      .advisers.collectFirst {
        case DenizenState(id, Orientation.FaceDown, _) => id
      }.get
    val act = service.handle(gameId, setup.nextSequence,
      GameCommand.EndWake(actor)).toOption.get
    val started = service.handle(gameId, act.nextSequence,
      GameCommand.StartWalker(ActionRef.PlayFacedownAdviser,
        StartPayload(actor, Vector.empty,
          Vector(DecisionOptionRef.Denizen(adviser))))).toOption.get
    val loaded = new GameApplicationService(catalog, repository)
      .load(gameId).toOption.flatten.get
    assertEquals(loaded.state, started.state)
    val result = service.handle(gameId, loaded.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(
        s"cardplay.place.${adviser.kind}.${adviser.value}",
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("discard")))))
      .toOption.get
    assertEquals(new GameApplicationService(catalog, repository)
      .load(gameId).toOption.flatten.get.state, result.state)
  }

  test("Wake projection is actor-private and Act boundary is informational") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val setup = execute(service, "game-projection-wake")
    val Ready(ready) = setup.state: @unchecked
    val active = ready.game.current.turn.activePlayer
    val projector = new GameProjector(catalog)
    val loaded = LoadedGame(setup.state, setup.nextSequence)

    val own = projector.project("game-projection-wake", loaded, active)
    val public = projector.projectPublic("game-projection-wake", loaded)
    assertEquals(own.phase, "wake")
    assert(own.legalControls.contains("endWake"))
    assertEquals(public.legalControls, Vector.empty)
    assert(own.activePlayerResources.nonEmpty)
    assert(own.currentSiteResources.nonEmpty)

    val ended = service.handle(
      "game-projection-wake",
      setup.nextSequence,
      GameCommand.EndWake(active)
    ).toOption.get
    val act = projector.project(
      "game-projection-wake",
      LoadedGame(ended.state, ended.nextSequence),
      active
    )
    assertEquals(act.phase, "act-action-selection")
    assert(act.actionSelectionOpen)
    assertEquals(act.legalControls, Vector("beginRest", "beginCampaign", "facedownAdviserMinorAction"))
    assertEquals(act.actionFamilies.size, 9)
    assert(act.boardTargetActions.exists(_.actionKind == "travel"))
    val travel = act.boardTargetActions.find(_.actionKind == "travel").get
    assertEquals(travel.minimum -> travel.maximum, 1 -> 1)
    assert(!travel.autoActivate)
    assert(travel.candidates.forall(_.target.isInstanceOf[
      BoardTargetRefProjection.Site]))
    assert(travel.candidates.forall(_.details.exists(_.endsWith("Supply"))))
    assertEquals(
      travel.candidates.map(candidate => candidate.target -> candidate.details),
      act.legalTravelDestinations.map(destination =>
        BoardTargetRefProjection.Site(destination.siteId) ->
          Vector(s"${destination.supplyCost} Supply")))
    assertEquals(projector.projectPublic("game-projection-wake",
      LoadedGame(ended.state, ended.nextSequence)).boardTargetActions,
      Vector.empty)
  }

  test("Begin Rest finishes Rest, persists and reloads to the next player's Wake") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val setup = execute(service, "game-rest")
    val Ready(ready) = setup.state: @unchecked
    val active = ready.game.current.turn.activePlayer
    val act = service.handle("game-rest", setup.nextSequence,
      GameCommand.EndWake(active)).toOption.get
    val begun = service.handle("game-rest", act.nextSequence,
      GameCommand.BeginRest(active)).toOption.get
    val finished = begun
    val loaded = new GameApplicationService(catalog, repository)
      .load("game-rest").toOption.flatten.get
    val Ready(after) = loaded.state: @unchecked

    assertEquals(loaded.state, finished.state)
    assertEquals(after.game.current.turn.phase, Phase.Wake)
    assertNotEquals(after.game.current.turn.activePlayer, active)
    assertEquals(repository.load("game-rest").toOption.flatten.get.records
      .takeRight(2).map(record => ujson.read(record)("formatVersion").num.toInt),
      Vector(1, 1))
  }

  test("site projection exposes ordered public properties without relic identity") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val setup = execute(service, "game-site-details")
    val Ready(ready) = setup.state: @unchecked
    val siteId = ready.game.current.map.cradle.head
    val emptySiteId = ready.game.current.map.cradle(1)
    val imperialSiteId = ready.game.current.map.provinces.head
    val banditSiteId = ready.game.current.map.provinces(1)
    val otherPlayerSiteId = ready.game.current.map.provinces(2)
    val definition = catalog.sites.find(_.id == siteId).get
    val denizenDefinitions = catalog.denizens.take(2)
    val relicDefinitions = catalog.relics.take(2)
    val activePlayer = ready.game.current.players.find(
      _.player == ready.game.current.turn.activePlayer).get
    val otherPlayer = ready.game.current.players.find(_ != activePlayer).get
    val populated = ready.game.current.map.sites(siteId).copy(
      forces = SiteForces.Occupied(ForceKind.Exile(activePlayer.lineage), 2),
      denizens = denizenDefinitions.reverse.map(definition =>
        DenizenState(
          DenizenId(definition.id.value),
          Orientation.FaceUp,
          Tokens.empty
        )),
      relics = relicDefinitions.map(definition =>
        RelicState(
          RelicId(definition.id.value),
          Orientation.FaceDown,
          Tokens.empty
        )),
      tokens = Tokens(2, 1)
    )
    val current = ready.game.current.copy(
      commonCards = ready.game.current.commonCards.copy(
        regionalDiscards = Map(
          Region.Cradle -> Vector(DenizenId(denizenDefinitions.head.id.value)),
          Region.Provinces -> Vector(VisionId("vision:conquest")),
          Region.Hinterland -> Vector.empty)),
      map = ready.game.current.map.copy(
        sites = ready.game.current.map.sites
          .updated(siteId, populated)
          .updated(
            emptySiteId,
            ready.game.current.map.sites(emptySiteId).copy(
              forces = SiteForces.Empty,
              denizens = Vector.empty,
              relics = Vector.empty,
              tokens = Tokens.empty
            )
          )
          .updated(imperialSiteId, ready.game.current.map.sites(imperialSiteId)
            .copy(forces = SiteForces.Occupied(ForceKind.Imperial, 1)))
          .updated(banditSiteId, ready.game.current.map.sites(banditSiteId)
            .copy(forces = SiteForces.Occupied(ForceKind.Bandit, 3)))
          .updated(otherPlayerSiteId, ready.game.current.map.sites(otherPlayerSiteId)
            .copy(forces = SiteForces.Occupied(
              ForceKind.Exile(otherPlayer.lineage), 4)))
      )
    )
    val loaded = LoadedGame(
      Ready(ready.copy(game = ready.game.copy(current = current))),
      setup.nextSequence
    )
    val projector = new GameProjector(catalog)
    val own = projector.project("game-site-details", loaded,
      current.turn.activePlayer)
    val public = projector.projectPublic("game-site-details", loaded)
    val site = own.world.flatMap(_.sites).find(_.siteId == siteId.value).get
    val empty = own.world.flatMap(_.sites)
      .find(_.siteId == emptySiteId.value).get

    assertEquals(site.looseFavor, 2)
    assertEquals(site.looseSecrets, 1)
    assertEquals(site.denizenCapacity, definition.capacity)
    assertEquals(site.relicCapacity, definition.relicSlots)
    assertEquals(
      site.denizens.map(card => card.cardId -> card.label),
      denizenDefinitions.reverse.map(definition =>
        definition.id.value -> definition.name)
    )
    assertEquals(site.relics.facedownCount, 2)
    assertEquals(empty.denizens, Vector.empty)
    assertEquals(empty.relics.facedownCount, 0)
    assertEquals(site.forces, Some(SiteForcesProjection("exile", 2, "player",
      Some(activePlayer.player.value),
      s"${ready.playerColors(activePlayer.player).value.capitalize} Warbands",
      ready.playerColors(activePlayer.player).value)))
    assertEquals(empty.forces, None)
    assertEquals(own.world.flatMap(_.sites).find(_.siteId == imperialSiteId.value)
      .flatMap(_.forces), Some(SiteForcesProjection("imperial", 1, "empire",
      None, "Imperial Warbands", "empire")))
    assertEquals(own.world.flatMap(_.sites).find(_.siteId == banditSiteId.value)
      .flatMap(_.forces), Some(SiteForcesProjection("bandit", 3, "bandit",
      None, "Bandit Warbands", "bandit")))
    val otherColor = ready.playerColors(otherPlayer.player).value
    assertEquals(own.world.flatMap(_.sites).find(_.siteId == otherPlayerSiteId.value)
      .flatMap(_.forces), Some(SiteForcesProjection("exile", 4, "player",
      Some(otherPlayer.player.value), s"${otherColor.capitalize} Warbands",
      otherColor)))
    val wire = ujson.read(GameHttpWire.encodeProjection(own))
    val wireSites = wire("world").arr.flatMap(_("sites").arr)
    val wirePlayerForces = wireSites.find(_("siteId").str == siteId.value)
      .get("forces")
    assertEquals(wirePlayerForces.obj.keySet,
      Set("forceKind", "count", "rulerKind", "rulerPlayerId", "label",
        "colorToken"))
    assertEquals(wirePlayerForces("rulerPlayerId").str, activePlayer.player.value)
    assertEquals(wireSites.find(_("siteId").str == emptySiteId.value)
      .get("forces"), ujson.Null)
    assertEquals(public.world, own.world)
    assertEquals(own.world.map(region => region.regionId ->
      region.discardTopCardKind).toMap,
      Map("cradle" -> Some("denizen"), "provinces" -> Some("vision"),
        "hinterland" -> None))
    assertEquals(own.worldDeckTopCardKind,
      current.commonCards.worldDeck.headOption.map(_.kind))
    def worldTop(deck: Vector[WorldCardId]) = projector.project(
      "game-world-top", LoadedGame(Ready(ready.copy(game = ready.game.copy(
        current = current.copy(commonCards = current.commonCards.copy(
          worldDeck = deck))))), setup.nextSequence), current.turn.activePlayer)
      .worldDeckTopCardKind
    assertEquals(worldTop(Vector(VisionId("vision:conquest"))), Some("vision"))
    assertEquals(worldTop(Vector.empty), None)
    own.world.flatMap(_.sites).foreach { projected =>
      val source = catalog.sites.find(_.id.value == projected.siteId).get
      if (source.capacity == 3) {
        assertEquals(projected.recoverDifficulty, None)
        assertEquals(projected.forgeCost.map(cost =>
          Tokens(cost.favor, cost.secrets)), source.forgeRequirements)
      } else {
        assertEquals(projected.forgeCost, None)
        assertEquals(projected.recoverDifficulty, source.recoverDifficulty)
      }
    }

    val json = oathdigital.server.GameHttpWire.encodeProjection(public)
    assert(json.contains("\"looseFavor\":2"))
    assert(json.contains("\"facedownCount\":2"))
    assert(json.contains("\"discardTopCardKind\":\"denizen\""))
    current.commonCards.worldDeck.headOption.foreach(card =>
      assert(json.contains(s"\"worldDeckTopCardKind\":\"${card.kind}\"")))
    relicDefinitions.foreach(relic => assert(!json.contains(relic.id.value)))
  }

  test("stale expected position rejects a command legal on current state") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    service.handle("game-stale-v2", 0L, GameCommand.Begin(plan))
    service.handle(
      "game-stale-v2",
      1L,
      GameCommand.PlacePawn(PlayerId("p2"), sites.head)
    )
    val adviser = plan.denizenOrder(6 + 1 * 3)

    assertEquals(
      service.handle(
        "game-stale-v2",
        1L,
        GameCommand.ChooseAdviser(PlayerId("p2"), adviser)
      ),
      Left(GameApplicationError.StaleClientPosition(1L, 2L))
    )
    assertEquals(
      repository.load("game-stale-v2").toOption.flatten.get.nextSequence,
      2L
    )
  }

  test("malformed v1 envelopes are rejected without reinterpretation") {
    val repository = new InMemoryEventStreamRepository
    repository.seed(
      "game-v1",
      Vector(ujson.write(ujson.Obj("formatVersion" -> 1)))
    )
    val result = new GameApplicationService(catalog, repository)
      .load("game-v1")

    assert(result.left.toOption.get match {
      case GameApplicationError.CodecFailure(_) => true
      case _ => false
    })
  }

  test("pre2 history is rejected by pre3 rules at its exact replay index") {
    val repository = new InMemoryEventStreamRepository
    val historical = CatalogRef(catalogRef.ruleset, "2026.07.27-pre2")
    val record = GameEventWire.encodeEvent(
      "game-pre2",
      historical,
      0L,
      FirstGameStarted(plan.copy(catalog = historical))
    ).toOption.get
    repository.seed("game-pre2", Vector(ujson.write(record)))

    assertEquals(
      new GameApplicationService(catalog, repository).load("game-pre2"),
      Left(GameApplicationError.ReplayFailure(
        0L,
        CatalogMismatch(catalogRef, historical)
      ))
    )
  }

  test("v2 replay violations report the exact index and append nothing") {
    val repository = new InMemoryEventStreamRepository
    val records = Vector(
      GameEventWire.encodeEvent(
        "game-replay-corrupt",
        catalogRef,
        0L,
        FirstGameStarted(plan)
      ).toOption.get,
      GameEventWire.encodeEvent(
        "game-replay-corrupt",
        catalogRef,
        1L,
        GamePawnPlaced(PlayerId("p1"), sites.head)
      ).toOption.get
    ).map(ujson.write(_))
    repository.seed("game-replay-corrupt", records)

    assertEquals(
      new GameApplicationService(catalog, repository).handle(
        "game-replay-corrupt",
        2L,
        GameCommand.PlacePawn(PlayerId("p2"), sites.head)
      ),
      Left(GameApplicationError.ReplayFailure(
        1L,
        WrongPlayer(PlayerId("p2"), PlayerId("p1"))
      ))
    )
    assertEquals(
      repository.load("game-replay-corrupt").toOption.flatten.get.records,
      records
    )
  }

  test("repository and envelope game identity mismatches append nothing") {
    def repositoryFor(
        storedGameId: String,
        envelopeGameId: String
    ): (EventStreamRepository, () => Int) = {
      var appendCalls = 0
      val record = GameEventWire.encodeEvent(
        envelopeGameId,
        catalogRef,
        0L,
        FirstGameStarted(plan)
      ).toOption.get
      val repository = new EventStreamRepository {
        override def load(gameId: String) = Right(Some(StoredEventStream(
          storedGameId,
          Vector(ujson.write(record))
        )))
        override def append(
            gameId: String,
            expected: ExpectedStream,
            records: Vector[String]
        ) = {
          appendCalls += 1
          Right(RepositoryAppendResult.Appended(1L, records.size))
        }
      }
      repository -> (() => appendCalls)
    }

    Vector(
      repositoryFor("game-b", "game-b"),
      repositoryFor("game-a", "game-b")
    ).foreach { case (repository, appendCalls) =>
      assertEquals(
        new GameApplicationService(catalog, repository).handle(
          "game-a",
          1L,
          GameCommand.PlacePawn(PlayerId("p2"), sites.head)
        ),
        Left(GameApplicationError.StreamIdentityMismatch(
          "game-a",
          "game-b"
        ))
      )
      assertEquals(appendCalls(), 0)
    }
  }

  test("authoritative v2 history cannot omit sequence zero") {
    val repository = new InMemoryEventStreamRepository
    val record = GameEventWire.encodeEvent(
      "game-missing-zero",
      catalogRef,
      1L,
      FirstGameStarted(plan)
    ).toOption.get
    repository.seed("game-missing-zero", Vector(ujson.write(record)))

    assertEquals(
      new GameApplicationService(catalog, repository)
        .load("game-missing-zero"),
      Left(GameApplicationError.CodecFailure(
        EventCodecFailure("invalid-sequence", "$[0].sequence",
          "expected 0 but found 1")
      ))
    )
  }

  test("player projection redacts other adviser hands and hidden orders") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    service.handle("game-private", 0L, GameCommand.Begin(plan))
    val placed = service.handle(
      "game-private",
      1L,
      GameCommand.PlacePawn(PlayerId("p2"), sites.head)
    ).toOption.get
    val projector = new GameProjector(catalog)
    val loaded = LoadedGame(placed.state, placed.nextSequence)
    val own = projector.project("game-private", loaded, PlayerId("p2"))
    val other = projector.project("game-private", loaded, PlayerId("p1"))
    val privateIds = own.pendingCardDecision.toVector.flatMap(_.cards)
      .map(_.cardId)
    val otherJson = oathdigital.server.GameHttpWire
      .encodeProjection(other)

    assertEquals(privateIds.size, 3)
    assertEquals(own.privateAdviserPreview, Vector.empty)
    assertEquals(other.pendingCardDecision, None)
    val publicShape = ujson.read(otherJson).obj
    assert(!publicShape.contains("privateAdviserChoices"))
    assert(!publicShape.contains("pendingSearch"))
    assert(publicShape.contains("pendingCardDecision"))
    privateIds.foreach(id => assert(!otherJson.contains(id)))
    val exposedValues = jsonStrings(ujson.read(otherJson))
    assertEquals(
      exposedValues.intersect(plan.relicOrder.map(_.value).toSet),
      Set.empty[String]
    )
    assertEquals(
      exposedValues.intersect(plan.worldDeckOrder.map(_.value).toSet),
      Set.empty[String]
    )
    val chosen = service.handle("game-private", placed.nextSequence,
      GameCommand.ChooseAdviser(PlayerId("p2"), DenizenId(privateIds.head)))
      .toOption.get
    val continuedPublic = projector.projectPublic("game-private",
      LoadedGame(chosen.state, chosen.nextSequence))
    val continued = projector.project("game-private",
      LoadedGame(chosen.state, chosen.nextSequence),
      PlayerId(continuedPublic.activeParticipantId.get))
    assertEquals(continued.phase, "awaiting-pawn")
    assertEquals(continued.world.map(_.discardCount).sum, 8)
    assertEquals(continued.privateAdviserPreview.size, 3)
  }

  private def jsonStrings(value: ujson.Value): Set[String] =
    value match {
      case ujson.Str(text) => Set(text)
      case obj: ujson.Obj =>
        obj.value.valuesIterator.flatMap(jsonStrings).toSet
      case array: ujson.Arr =>
        array.value.iterator.flatMap(jsonStrings).toSet
      case _ => Set.empty
    }

  test("HSQL close and reopen preserves v2 replay equality") {
    val path =
      Files.createTempDirectory("oathdigital-v2-reopen-").resolve("journal")
    val firstRepository =
      OwnedHsqldbEventStreamRepository.open(path).toOption.get
    val accepted =
      try execute(
        new GameApplicationService(catalog, firstRepository),
        "game-hsql-v2"
      )
      finally firstRepository.close()

    val reopened = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    try {
      val loaded = new GameApplicationService(catalog, reopened)
        .load("game-hsql-v2").toOption.flatten.get
      assertEquals(loaded.state, accepted.state)
      assertEquals(loaded.nextSequence, 8L)
    } finally reopened.close()
  }

  test("HSQL reopen preserves completed Rest cleanup and secret summary") {
    val path = Files.createTempDirectory("oathdigital-rest-reopen-").resolve("journal")
    val gameId = "game-hsql-rest-cleanup"
    val first = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    val finished = try {
      val service = new GameApplicationService(catalog, first)
      val setup = execute(service, gameId)
      val Ready(ready) = setup.state: @unchecked
      val actor = ready.game.current.turn.activePlayer
      val act = service.handle(gameId, setup.nextSequence,
        GameCommand.EndWake(actor)).toOption.get
      val begun = service.handle(gameId, act.nextSequence,
        GameCommand.BeginRest(actor)).toOption.get
      begun
    } finally first.close()
    val reopened = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    try {
      val loaded = new GameApplicationService(catalog, reopened)
        .load(gameId).toOption.flatten.get
      assertEquals(loaded.state, finished.state)
      val Ready(ready) = loaded.state: @unchecked
      val rested = ready.game.current.players.find(_.player !=
        ready.game.current.turn.activePlayer).get
      val summary = oathdigital.gameplay.PlayerSecretSummary
        .derive(ready, rested.player).toOption.get
      assertEquals(summary.facedown, 0)
      assertEquals(summary.totalSecrets, summary.available + summary.committed)
    } finally reopened.close()
  }

  test("HSQL reopen preserves private minor-action relic knowledge") {
    val path = Files.createTempDirectory("oathdigital-minor-reopen-").resolve("journal")
    val gameId = "game-hsql-minor-relics"
    val relicSite = catalog.sites.find(_.relicSlots > 0).get.id
    val placementSites = relicSite +: sites.filterNot(_ == relicSite).take(7)
    val first = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    val peeked = try {
      val service = new GameApplicationService(catalog, first)
      val setup = execute(service, gameId, placementSites)
      val Ready(ready) = setup.state: @unchecked
      val actor = ready.game.current.turn.activePlayer
      val act = service.handle(gameId, setup.nextSequence,
        GameCommand.EndWake(actor)).toOption.get
      service.handle(gameId, act.nextSequence,
        GameCommand.PeekSiteRelics(actor)).toOption.get
    } finally first.close()

    val reopened = OwnedHsqldbEventStreamRepository.open(path).fold(
      error => fail(s"failed to reopen minor-action repository: $error"), identity)
    try {
      val loaded = new GameApplicationService(catalog, reopened)
        .load(gameId).toOption.flatten.get
      assertEquals(loaded.state, peeked.state)
      val Ready(ready) = loaded.state: @unchecked
      val actor = ready.game.current.turn.activePlayer
      val other = ready.game.current.players.find(_.player != actor).get.player
      val siteId = ready.game.current.players.find(_.player == actor).get.pawnSite.get
      val projector = new GameProjector(catalog)
      assert(projector.project(gameId, loaded, actor).world.flatMap(_.sites)
        .find(_.siteId == siteId.value).get.relics.knownRelics.nonEmpty)
      assertEquals(projector.project(gameId, loaded, other).world.flatMap(_.sites)
        .find(_.siteId == siteId.value).get.relics.knownRelics, Vector.empty)
      assertEquals(projector.projectPublic(gameId, loaded).world.flatMap(_.sites)
        .find(_.siteId == siteId.value).get.relics.knownRelics, Vector.empty)
    } finally reopened.close()
  }

  private def negotiationOpened(service: GameApplicationService, gameId: String)
      : (GameAccepted, PlayerId, PlayerId, WorldCardId) = {
    val setup = execute(service, gameId)
    val Ready(ready) = setup.state: @unchecked
    val actor = ready.game.current.turn.activePlayer
    val other = ready.game.current.players.find(_.player != actor).get
    val act = service.handle(gameId, setup.nextSequence,
      GameCommand.EndWake(actor)).toOption.get
    val traveled = service.handle(gameId, act.nextSequence,
      GameCommand.StartWalker(ActionRef.Travel, StartPayload(actor,
        Vector.empty, Vector(DecisionOptionRef.Site(other.pawnSite.get)))))
      .toOption.get
    val adviser = ready.game.current.players.find(_.player == actor).get
      .advisers.head.id.asInstanceOf[WorldCardId]
    val started = service.handle(gameId, traveled.nextSequence,
      GameCommand.StartWalker(ActionRef.Negotiation, StartPayload(actor)))
      .fold(error => fail(s"Negotiation must start: $error"), identity)
    val Ready(afterTravel) = traveled.state: @unchecked
    val opened =
      if (NegotiationDeal.eligible(afterTravel, actor).size < 2) started
      else service.handle(gameId, started.nextSequence,
        GameCommand.ResolveWalker(actor, TreeDecision(
          NegotiationDeal.negotiatorsDecisionId, ChooseManyAnswer(
            Vector(DecisionOptionRef.Player(other.player))))))
        .fold(error => fail(s"negotiators must be accepted: $error"), identity)
    (opened, actor, other.player, adviser)
  }

  private def say(service: GameApplicationService, gameId: String,
      from: GameAccepted, by: PlayerId, answer: DecisionAnswer) =
    service.handle(gameId, from.nextSequence, GameCommand.ResolveWalker(by,
      TreeDecision(NegotiationDeal.dealDecisionId, answer)))

  test("HSQL reopen preserves a parked Negotiation deal") {
    val path = Files.createTempDirectory("oathdigital-negotiation-parked-")
      .resolve("journal")
    val gameId = "game-hsql-negotiation-parked"
    val first = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    val (parked, actor, other, adviser) = try {
      val service = new GameApplicationService(catalog, first)
      val (opened, actor, other, adviser) = negotiationOpened(service, gameId)
      val terms = NegotiationTerms(disclosures = Vector(NegotiationDisclosure(
        other, NegotiationDisclosureRef.Adviser(actor, adviser))))
      (say(service, gameId, opened, actor, ProposeTerms(terms))
        .fold(error => fail(s"terms must persist: $error"), identity),
        actor, other, adviser)
    } finally first.close()
    val reopened = OwnedHsqldbEventStreamRepository.open(path).fold(
      error => fail(s"failed to reopen Negotiation repository: $error"), identity)
    try {
      val loaded = new GameApplicationService(catalog, reopened)
        .load(gameId).toOption.flatten.get
      assertEquals(loaded.state, parked.state)
      val seen = new GameProjector(catalog).project(gameId, loaded, other)
        .walkerDecision.flatMap(_.query).flatMap(_.deal).get
      assertEquals(seen.disclosures.map(d => (d.authorPlayerId, d.card)),
        Vector((actor.value, None)))
    } finally reopened.close()
  }

  test("HSQL reopen preserves completed Negotiation disclosure knowledge") {
    val path = Files.createTempDirectory("oathdigital-negotiation-reopen-")
      .resolve("journal")
    val gameId = "game-hsql-negotiation"
    val first = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    val (completed, actor, other, adviser) = try {
      val service = new GameApplicationService(catalog, first)
      val (opened, actor, other, adviser) = negotiationOpened(service, gameId)
      val terms = NegotiationTerms(disclosures = Vector(NegotiationDisclosure(
        other, NegotiationDisclosureRef.Adviser(actor, adviser))))
      val changed = say(service, gameId, opened, actor, ProposeTerms(terms))
        .fold(error => fail(s"failed to persist Negotiation terms: $error"), identity)
      assertEquals(say(service, gameId, opened, actor, AcceptDeal).left.toOption,
        Some(GameApplicationError.StaleClientPosition(
          opened.nextSequence, changed.nextSequence)))
      val actorAccepted = say(service, gameId, changed, actor, AcceptDeal)
        .toOption.get
      val completed = say(service, gameId, actorAccepted, other, AcceptDeal)
        .toOption.get
      (completed, actor, other, adviser)
    } finally first.close()
    val reopened = OwnedHsqldbEventStreamRepository.open(path).fold(
      error => fail(s"failed to reopen Negotiation repository: $error"), identity)
    try {
      val loaded = new GameApplicationService(catalog, reopened)
        .load(gameId).toOption.flatten.get
      assertEquals(loaded.state, completed.state)
      val projector = new GameProjector(catalog)
      assertEquals(projector.project(gameId, loaded, actor).walkerDecision, None)
      val recipientBoard = projector.project(gameId, loaded, other).playerBoards
        .find(_.playerId == actor.value).get
      assert(recipientBoard.advisers.exists(card =>
        card.cardId == adviser.value && !card.hidden))
      val publicBoard = projector.projectPublic(gameId, loaded).playerBoards
        .find(_.playerId == actor.value).get
      assert(publicBoard.advisers.forall(card =>
        card.cardId != adviser.value || card.hidden))
      assertEquals(projector.projectPublic(gameId, loaded).walkerWaiting, None)
    } finally reopened.close()
  }

}
