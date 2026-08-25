package oathdigital.application

import java.nio.file.Files

import oathdigital.model._
import oathdigital.gameplay.actions.{CampaignRules, SearchRules}
import oathdigital.persistence.OwnedHsqldbEventStreamRepository
import oathdigital.serialization.GameEventWire
import oathdigital.server.GameHttpWire
import oathdigital.serialization.WireError.UnsupportedFormatVersion
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup.OathEvent.{
  GamePawnPlaced,
  FirstGameStarted
}
import oathdigital.gameplay.setup.OathViolation.{CatalogMismatch, WrongPlayer}
import oathdigital.gameplay.setup.OathState.Ready
import oathdigital.gameplay.setup.WakeResource
import oathdigital.gameplay.setup.ReadyGame

class GameApplicationServiceSuite extends munit.FunSuite {
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
    val discarded = service.handle("game-minor-replay", act.nextSequence,
      GameCommand.DiscardFacedownAdviser(actor, adviser)).toOption.get
    val reloaded = new GameApplicationService(catalog, repository)
      .load("game-minor-replay").toOption.flatten.get
    assertEquals(reloaded.state, discarded.state)
    assertEquals(reloaded.nextSequence, discarded.nextSequence)
  }

  test("Challenge persists owner-only pending state and reloads deterministic Mob completion") {
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
      GameCommand.TakeWealth(actor, WakeResource.Favor)).toOption.get
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.EndWake(actor)).toOption.get
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.BeginChallenge(actor, Banner.PeoplesFavor)).toOption.get
    val reloaded = new GameApplicationService(catalog, repository)
      .load(gameId).toOption.flatten.get
    assertEquals(reloaded.state, accepted.state)
    val Ready(pendingReady) = reloaded.state: @unchecked
    val pending = pendingReady.game.current.pending.get
      .asInstanceOf[PendingProcedure.Challenge]
    assertEquals(pending.remainingRibbonResources, 0)
    val other = pendingReady.game.current.players.find(_.player != actor).get.player
    val projector = new GameProjector(catalog)
    assertEquals(projector.project(gameId, reloaded, actor).legalControls,
      Vector("completeChallenge"))
    assertEquals(projector.project(gameId, reloaded, other).challenge, None)
    val completed = new GameApplicationService(catalog, repository).handle(gameId,
      reloaded.nextSequence, GameCommand.CompleteChallenge(actor,
        pending.decision, 2)).toOption.get
    val Ready(after) = completed.state: @unchecked
    assertEquals(after.game.current.banners.peoplesFavor.holder, Some(actor))
    assertEquals(after.game.current.banners.peoplesFavor.favor, 2)
  }

  test("Forge persists private pending and completed state and prepares relic once") {
    val repository = new InMemoryEventStreamRepository
    var prepared = 0
    val relicPort = new RelicDrawPort {
      def prepare(ready: ReadyGame) = {
        prepared += 1
        ready.game.current.commonCards.relicDeck.headOption
          .toRight(oathdigital.gameplay.setup.OathViolation.ForgeUnavailable("empty"))
      }
    }
    val dice = new CampaignDicePort {
      def rollAttack(count: Int) = Vector.fill(count)(AttackDieFace.OneSword)
      def rollDefense(count: Int) = Vector.fill(count)(DefenseDieFace.Blank)
    }
    val forgeSite = catalog.sites.find(site => site.forgeRequirements.nonEmpty &&
      !site.handlers.exists(_.contains(".homeland-"))).get.id
    val forgePlan = plan.copy(orderedSites = forgeSite +:
      plan.orderedSites.filterNot(_ == forgeSite))
    val service = new GameApplicationService(catalog, repository,
      relicDrawPort = relicPort, campaignDicePort = dice)
    val gameId = "game-forge-persistence"
    var accepted = service.handle(gameId, 0L, GameCommand.Begin(forgePlan)).toOption.get
    val order = Vector(PlayerId("p2"), PlayerId("p3"), PlayerId("p1"))
    order.zipWithIndex.foreach { case (playerId, index) =>
      val destination = if (index == 0) forgeSite else forgePlan.orderedSites(index)
      accepted = service.handle(gameId, accepted.nextSequence,
        GameCommand.PlacePawn(playerId, destination)).toOption.get
      val participantIndex = forgePlan.participants.indexWhere(_.playerId == playerId)
      accepted = service.handle(gameId, accepted.nextSequence,
        GameCommand.ChooseAdviser(playerId,
          forgePlan.denizenOrder(6 + participantIndex * 3))).toOption.get
    }
    val actor = PlayerId("p2")
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.EndWake(actor)).toOption.get
    val campaignDecision = DecisionId(s"campaign-${accepted.nextSequence}")
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.BeginCampaignConquest(actor, forgeSite, 3)).toOption.get
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.FinishCampaignPlans(actor, campaignDecision)).toOption.get
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.ChooseCampaignSacrifice(actor, campaignDecision, 2)).toOption.get
    val Ready(won) = accepted.state: @unchecked
    won.game.current.pending.collect { case c: PendingProcedure.Campaign => c }
      .foreach { campaign =>
        accepted = service.handle(gameId, accepted.nextSequence,
          GameCommand.PlaceCampaignForce(actor, campaignDecision,
            Vector(CampaignForceAllocation(forgeSite,
              campaign.force - campaign.skullLosses -
                campaign.sacrificed.getOrElse(0))))).toOption.get
      }

    def searchOne(): Unit = {
      accepted = service.handle(gameId, accepted.nextSequence,
        GameCommand.BeginSearch(actor, SearchSource.WorldDeck)).toOption.get
      val Ready(pendingReady) = accepted.state: @unchecked
      val pending = pendingReady.game.current.pending.get
        .asInstanceOf[PendingProcedure.Search]
      val kept = pending.drawn.find(card => SearchRules.legalPlacements(
        catalog, pendingReady, pending, card).contains(SearchPlacement.Site(None))).get
      accepted = service.handle(gameId, accepted.nextSequence,
        GameCommand.CompleteSearch(actor, pending.decision, kept,
          pending.drawn.filterNot(_ == kept), SearchPlacement.Site(None))).toOption.get
    }
    searchOne(); searchOne()
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.BeginRest(actor)).toOption.get
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.FinishRest(actor)).toOption.get
    Vector(PlayerId("p3"), PlayerId("p1")).foreach { player =>
      accepted = service.handle(gameId, accepted.nextSequence,
        GameCommand.EndWake(player)).toOption.get
      accepted = service.handle(gameId, accepted.nextSequence,
        GameCommand.BeginRest(player)).toOption.get
      accepted = service.handle(gameId, accepted.nextSequence,
        GameCommand.FinishRest(player)).toOption.get
    }
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.EndWake(actor)).toOption.get
    searchOne()
    val begun = service.handle(gameId, accepted.nextSequence,
      GameCommand.BeginForge(actor)).fold(error => fail(error.toString), identity)
    val reloadedService = new GameApplicationService(catalog, repository,
      relicDrawPort = relicPort, campaignDicePort = dice)
    val pendingLoaded = reloadedService.load(gameId).toOption.flatten.get
    assertEquals(pendingLoaded.state, begun.state)
    val Ready(forgeReady) = pendingLoaded.state: @unchecked
    val pending = forgeReady.game.current.pending.get.asInstanceOf[PendingProcedure.Forge]
    val other = forgeReady.game.current.players.find(_.player != actor).get.player
    val projector = new GameProjector(catalog)
    assert(projector.project(gameId, pendingLoaded, actor).forge.nonEmpty)
    assertEquals(projector.project(gameId, pendingLoaded, other).forge, None)
    assertEquals(projector.projectPublic(gameId, pendingLoaded).forge, None)
    val resources = Vector.fill(pending.cost.favor)(ForgeResource.Favor) ++
      Vector.fill(pending.cost.secrets)(ForgeResource.Secret)
    val assignments = pending.eligibleTargets.zip(resources).map {
      case (target, resource) => ForgeResourceAssignment(target, resource) }
    val beforeRejected = repository.load(gameId).toOption.flatten.get.records
    Vector[GameCommand](
      GameCommand.CompleteForge(actor, DecisionId("stale"), assignments),
      GameCommand.CompleteForge(other, pending.decision, assignments),
      GameCommand.CompleteForge(actor, pending.decision,
        assignments.updated(1, assignments.head)),
      GameCommand.CompleteForge(actor, pending.decision,
        assignments.map(_.copy(resource = ForgeResource.Secret)))
    ).foreach(command => assert(reloadedService.handle(gameId,
      begun.nextSequence, command).isLeft))
    assertEquals(prepared, 0)
    assertEquals(repository.load(gameId).toOption.flatten.get.records, beforeRejected)
    val relic = forgeReady.game.current.commonCards.relicDeck.head
    val completed = reloadedService.handle(gameId, begun.nextSequence,
      GameCommand.CompleteForge(actor, pending.decision, assignments)).toOption.get
    assertEquals(prepared, 1)
    val completedLoaded = new GameApplicationService(catalog, repository)
      .load(gameId).toOption.flatten.get
    assertEquals(completedLoaded.state, completed.state)
    val otherJson = GameHttpWire.encodeProjection(
      projector.project(gameId, completedLoaded, other))
    val publicJson = GameHttpWire.encodeProjection(
      projector.projectPublic(gameId, completedLoaded))
    assert(!otherJson.contains(relic.value))
    assert(!publicJson.contains(relic.value))
  }
  private def safeCampaignSite: SiteId = catalog.sites.find(site =>
    site.handlers.forall(h => !h.endsWith(".mountain") &&
      !h.endsWith(".plains") && !h.contains(".homeland-"))).get.id

  private val blankCampaignDice = new CampaignDicePort {
    def rollAttack(count: Int) = Vector.fill(count)(AttackDieFace.OneSword)
    def rollDefense(count: Int) = Vector.fill(count)(DefenseDieFace.Blank)
  }

  private def beginServiceRaid(service: GameApplicationService, gameId: String)
      : (GameAccepted, PlayerId, PlayerId, DecisionId, SiteId, Int) = {
    val shared = safeCampaignSite
    val otherSite = sites.find(_ != shared).get
    val setup = execute(service, gameId, Vector(shared, shared, otherSite))
    val Ready(ready) = setup.state: @unchecked
    val attacker = ready.game.current.turn.activePlayer
    val defender = ready.game.current.players.find(p => p.player != attacker &&
      p.pawnSite.contains(shared)).get.player
    val force = ready.game.current.players.find(_.player == attacker).get.board.warbands
    val act = service.handle(gameId, setup.nextSequence,
      GameCommand.EndWake(attacker)).toOption.get
    val targets = Vector[CampaignRaidTarget](CampaignRaidTarget.Pawn(defender))
    val started = service.handle(gameId, act.nextSequence,
      GameCommand.BeginCampaignRaid(attacker, targets, force)).toOption.get
    (started, attacker, defender, DecisionId(s"campaign-${act.nextSequence}"),
      shared, force)
  }

  test("Raid service owns randomness and rejects stale or spoofed decisions") {
    val repository = new InMemoryEventStreamRepository
    var attackRolls = Vector.empty[Int]
    var defenseRolls = Vector.empty[Int]
    val dice = new CampaignDicePort {
      def rollAttack(count: Int) = {
        attackRolls :+= count
        Vector.fill(count)(AttackDieFace.OneSword)
      }
      def rollDefense(count: Int) = {
        defenseRolls :+= count
        Vector.fill(count)(DefenseDieFace.Blank)
      }
    }
    val service = new GameApplicationService(catalog, repository,
      campaignDicePort = dice)
    val (started, attacker, defender, decision, origin, force) =
      beginServiceRaid(service, "raid-service")
    val before = repository.load("raid-service").toOption.flatten.get.records
    assert(service.handle("raid-service", started.nextSequence,
      GameCommand.FinishCampaignPlans(attacker, DecisionId("stale"))).isLeft)
    assert(service.handle("raid-service", started.nextSequence,
      GameCommand.FinishCampaignPlans(defender, decision)).isLeft)
    assertEquals(attackRolls, Vector.empty)
    assertEquals(repository.load("raid-service").toOption.flatten.get.records, before)

    val attackerDone = service.handle("raid-service", started.nextSequence,
      GameCommand.FinishCampaignPlans(attacker, decision)).toOption.get
    assertEquals(attackRolls, Vector.empty)
    val rolled = service.handle("raid-service", attackerDone.nextSequence,
      GameCommand.FinishCampaignPlans(defender, decision)).toOption.get
    assertEquals(attackRolls, Vector(force))
    val Ready(rolledReady) = rolled.state: @unchecked
    val pending = rolledReady.game.current.pending.get
      .asInstanceOf[PendingProcedure.Campaign]
    val minimumSacrifice = math.max(0,
      CampaignRules.defenderForce(rolledReady, pending) + 1 - pending.attack)
    val won = service.handle("raid-service", rolled.nextSequence,
      GameCommand.ChooseCampaignSacrifice(attacker, decision, minimumSacrifice))
      .toOption.get
    assertEquals(defenseRolls, Vector(2))
    val destination = rolledReady.game.current.map.inPlay.find(_ != origin).get
    val completed = service.handle("raid-service", won.nextSequence,
      GameCommand.RelocateCampaignRaidPawn(attacker, decision, destination))
      .toOption.get
    val Ready(after) = completed.state: @unchecked
    assertEquals(after.game.current.players.find(_.player == defender).get.pawnSite,
      Some(destination))
    assertEquals(after.game.current.pending, None)
    assertEquals(new GameApplicationService(catalog, repository,
      campaignDicePort = dice).load("raid-service").toOption.flatten.get.state,
      completed.state)
  }

  test("Raid pending projection gives controls only to the current owner") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository,
      campaignDicePort = blankCampaignDice)
    val (started, attacker, defender, decision, _, _) =
      beginServiceRaid(service, "raid-redaction")
    val Ready(ready) = started.state: @unchecked
    val observer = ready.game.current.players.map(_.player)
      .find(id => id != attacker && id != defender).get
    val projector = new GameProjector(catalog)
    val loaded = LoadedGame(started.state, started.nextSequence)
    val owner = projector.project("raid-redaction", loaded, attacker)
    val other = projector.project("raid-redaction", loaded, observer)
    val public = projector.projectPublic("raid-redaction", loaded)
    assertEquals(owner.campaign.map(_.decisionId), Some(decision.value))
    assert(owner.legalControls.contains("finishCampaignPlans"))
    assertEquals(other.phase, "campaign-waiting")
    assertEquals(other.campaign, None)
    assertEquals(other.campaignRaidRelocation, None)
    assertEquals(other.legalControls, Vector.empty)
    assertEquals(public.campaign, None)
    val defenderAdviserIds = ready.game.current.players.find(_.player == defender).get
      .advisers.map(_.id.value)
    val otherJson = GameHttpWire.encodeProjection(other)
    defenderAdviserIds.foreach(id => assert(!otherJson.contains(id)))
  }
  test("invalid Campaign plan requests consume no attack randomness") {
    val repository = new InMemoryEventStreamRepository
    var attackRolls = Vector.empty[Int]
    val dice = new CampaignDicePort {
      def rollAttack(count: Int) = {
        attackRolls = attackRolls :+ count
        Vector.fill(count)(AttackDieFace.OneSword)
      }
      def rollDefense(count: Int) = Vector.fill(count)(DefenseDieFace.Blank)
    }
    val service = new GameApplicationService(catalog, repository,
      campaignDicePort = dice)
    val safe = catalog.sites.find(site => site.handlers.forall(h =>
      !h.endsWith(".mountain") && !h.endsWith(".plains") &&
        !h.contains(".homeland-"))).get.id
    val setup = execute(service, "campaign-rng-validation",
      Vector(sites(0), sites(1), safe))
    val Ready(ready) = setup.state: @unchecked
    val active = ready.game.current.turn.activePlayer
    val other = ready.game.current.players.find(_.player != active).get.player
    val act = service.handle("campaign-rng-validation", setup.nextSequence,
      GameCommand.EndWake(active)).toOption.get
    val target = new GameProjector(catalog).project("campaign-rng-validation",
      LoadedGame(act.state, act.nextSequence), active).boardTargetActions
      .find(_.actionKind == "campaign-conquest").get.candidates.head.target
      .asInstanceOf[BoardTargetRefProjection.Site].siteId
    val beforeInvalid = repository.load("campaign-rng-validation").toOption.flatten.get
      .records
    assert(service.handle("campaign-rng-validation", act.nextSequence,
      GameCommand.BeginCampaignConquest(active, SiteId(target), 999)).isLeft)
    assert(service.handle("campaign-rng-validation", act.nextSequence - 1,
      GameCommand.BeginCampaignConquest(active, SiteId(target), 1)).isLeft)
    assertEquals(repository.load("campaign-rng-validation").toOption.flatten.get.records,
      beforeInvalid)
    assertEquals(attackRolls, Vector.empty)
    val declared = service.handle("campaign-rng-validation", act.nextSequence,
      GameCommand.BeginCampaignConquest(active, SiteId(target), 2)).toOption.get
    val decision = DecisionId(s"campaign-${act.nextSequence}")
    val invalid = PendingProcedure.CampaignPlanSource.Adviser(active,
      DenizenId("not-outriders"))

    assert(service.handle("campaign-rng-validation", declared.nextSequence,
      GameCommand.FinishCampaignPlans(other, decision)).isLeft)
    assert(service.handle("campaign-rng-validation", declared.nextSequence,
      GameCommand.FinishCampaignPlans(active, DecisionId("stale"))).isLeft)
    assert(service.handle("campaign-rng-validation", declared.nextSequence,
      GameCommand.ChooseCampaignPlan(active, decision, invalid)).isLeft)
    assertEquals(attackRolls, Vector.empty)

    val chosen = service.handle("campaign-rng-validation", declared.nextSequence,
      GameCommand.FinishCampaignPlans(active, decision)).toOption.get
    assertEquals(attackRolls, Vector(2))
    assert(service.handle("campaign-rng-validation", chosen.nextSequence,
      GameCommand.FinishCampaignPlans(active, decision)).isLeft)
    assertEquals(attackRolls, Vector(2))
  }

  test("Campaign dice persist and reload without client randomness") {
    val repository = new InMemoryEventStreamRepository
    val dice = new CampaignDicePort {
      def rollAttack(count: Int) = Vector.fill(count)(AttackDieFace.OneSword)
      def rollDefense(count: Int) = Vector.fill(count)(DefenseDieFace.Blank)
    }
    val service = new GameApplicationService(catalog, repository,
      campaignDicePort = dice)
    val safe = catalog.sites.find(site =>
      site.handlers.forall(h => !h.endsWith(".mountain") &&
        !h.endsWith(".plains") && !h.contains(".homeland-"))).get.id
    val setup = execute(service, "campaign-persist", Vector(sites(0), sites(1), safe))
    val Ready(ready) = setup.state: @unchecked
    val active = ready.game.current.turn.activePlayer
    val act = service.handle("campaign-persist", setup.nextSequence,
      GameCommand.EndWake(active)).toOption.get
    val projected = new GameProjector(catalog).project("campaign-persist",
      LoadedGame(act.state, act.nextSequence), active)
    val target = projected.boardTargetActions.find(_.actionKind == "campaign-conquest")
      .get.candidates.head.target.asInstanceOf[BoardTargetRefProjection.Site].siteId
    val started = service.handle("campaign-persist", act.nextSequence,
      GameCommand.BeginCampaignConquest(active, SiteId(target), 0)).toOption.get
    val startEvent = started.events.head.asInstanceOf[
      oathdigital.gameplay.setup.OathEvent.CampaignStarted]
    assertEquals(startEvent.force, 0)
    val Ready(afterPartial) = started.state: @unchecked
    assertEquals(afterPartial.game.current.players.find(_.player == active).get
      .board.warbands,
      ready.game.current.players.find(_.player == active).get.board.warbands)
    val chosen = service.handle("campaign-persist", started.nextSequence,
      GameCommand.FinishCampaignPlans(active,
        DecisionId(s"campaign-${act.nextSequence}"))).toOption.get
    assertEquals(chosen.events.head.asInstanceOf[
      oathdigital.gameplay.setup.OathEvent.CampaignPlansFinished].attackDice, Vector.empty)
    assert(chosen.events.head.isInstanceOf[oathdigital.gameplay.setup.OathEvent.CampaignPlansFinished])
    val reloaded = new GameApplicationService(catalog, repository,
      campaignDicePort = dice).load("campaign-persist").toOption.flatten.get
    assertEquals(reloaded.state, chosen.state)
    assertEquals(reloaded.nextSequence, chosen.nextSequence)
  }

  private def execute(
      service: GameApplicationService,
      gameId: String,
      placementSites: Vector[oathdigital.model.SiteId] = sites,
      setupPlan: oathdigital.gameplay.setup.FirstGameSetupPlan = plan
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
    val resource =
      if (site.tokens.favor > 0) WakeResource.Favor else WakeResource.Secret

    val wealth = service.handle(
      "game-wake",
      setup.nextSequence,
      GameCommand.TakeWealth(active, resource)
    ).toOption.get
    assertEquals(wealth.nextSequence, 9L)
    assertEquals(
      service.handle("game-wake", 8L, GameCommand.EndWake(active)),
      Left(GameApplicationError.StaleClientPosition(8L, 9L))
    )
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
    val records = repository.load("game-wake").toOption.flatten.get.records
    assertEquals(records.take(8).map(record =>
      ujson.read(record)("formatVersion").num.toInt).distinct, Vector(2))
    assertEquals(records.drop(8).map(record =>
      ujson.read(record)("formatVersion").num.toInt), Vector(3, 3))
    assertEquals(records.drop(8).map(record =>
      ujson.read(record)("eventType").str),
      Vector("gameplay.take-wealth", "gameplay.wake-ended"))
  }

  test("Travel appends one v3 event and reloads pawn Supply and Act") {
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
      GameCommand.Travel(active, destination)).toOption.get
    val loaded = new GameApplicationService(catalog, repository)
      .load("game-travel").toOption.flatten.get
    val Ready(after) = loaded.state: @unchecked
    val moved = after.game.current.players.find(_.player == active).get

    assertEquals(loaded.state, traveled.state)
    assertEquals(loaded.nextSequence, ended.nextSequence + 1)
    assertEquals(moved.pawnSite, Some(destination))
    assert(moved.board.supply.supply < before.board.supply.supply)
    assertEquals(after.game.current.turn.phase, Phase.Act)
    val last = ujson.read(repository.load("game-travel").toOption.flatten.get
      .records.last)
    assertEquals(last("formatVersion").num.toInt, 3)
    assertEquals(last("eventType").str, "gameplay.traveled")
  }

  test("ruined edifice Economy target persists and replays with its kind") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val (siteId, edificeId) = plan.homelandEdifices.head
    val placements = siteId +: sites.filterNot(_ == siteId).take(2)
    val setup = execute(service, "game-economy-edifice", placements)
    val Ready(ready) = setup.state: @unchecked
    val active = ready.game.current.turn.activePlayer
    val ended = service.handle("game-economy-edifice", setup.nextSequence,
      GameCommand.EndWake(active)).toOption.get
    val target = EconomyTargetRef.Edifice(edificeId)
    val mustered = service.handle("game-economy-edifice", ended.nextSequence,
      GameCommand.Muster(active, target)).toOption.get
    val loaded = new GameApplicationService(catalog, repository)
      .load("game-economy-edifice").toOption.flatten.get
    assertEquals(loaded.state, mustered.state)
    val Ready(after) = loaded.state: @unchecked
    assertEquals(after.game.current.map.sites(siteId).denizens.collectFirst {
      case value: EdificeState if value.id == edificeId => value.tokens
    }, Some(Tokens(1, 0)))
    val record = ujson.read(repository.load("game-economy-edifice")
      .toOption.flatten.get.records.last)
    assertEquals(record("formatVersion").num.toInt, 6)
    assertEquals(record("payload")("target")("kind").str, "edifice")
    assertEquals(record("payload")("target")("id").str, edificeId.value)
  }

  test("Search persists and reloads pending private decision then completes in v4") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val setup = execute(service, "game-search")
    val Ready(ready) = setup.state: @unchecked
    val active = ready.game.current.turn.activePlayer
    val ended = service.handle("game-search", setup.nextSequence,
      GameCommand.EndWake(active)).toOption.get
    val started = service.handle("game-search", ended.nextSequence,
      GameCommand.BeginSearch(active, SearchSource.WorldDeck)).toOption.get
    val reloadedPending = new GameApplicationService(catalog, repository)
      .load("game-search").toOption.flatten.get
    assertEquals(reloadedPending.state, started.state)
    val Ready(pendingReady) = reloadedPending.state: @unchecked
    val pending = pendingReady.game.current.pending.get
      .asInstanceOf[PendingProcedure.Search]
    assertEquals(service.handle("game-search", ended.nextSequence,
      GameCommand.BeginSearch(active, SearchSource.WorldDeck)),
      Left(GameApplicationError.StaleClientPosition(
        ended.nextSequence, started.nextSequence)))
    val completed = service.handle("game-search", started.nextSequence,
      GameCommand.CompleteSearch(active, pending.decision,
        pending.drawn.head, pending.drawn.tail, SearchPlacement.Discard))
      .toOption.get
    val loaded = new GameApplicationService(catalog, repository)
      .load("game-search").toOption.flatten.get
    assertEquals(loaded.state, completed.state)
    val versions = repository.load("game-search").toOption.flatten.get.records
      .takeRight(2).map(record => ujson.read(record)("formatVersion").num.toInt)
    assertEquals(versions, Vector(4, 4))
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
      GameCommand.BeginSearch(active, SearchSource.WorldDeck))
      .left.toOption.get.isInstanceOf[GameApplicationError.CommandRejected])
    assertEquals(repository.load("game-search-tamper").toOption.flatten.get
      .nextSequence, ended.nextSequence)
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
    assertEquals(act.legalControls, Vector("beginRest", "facedownAdviserMinorAction"))
    assertEquals(act.actionFamilies.size, 9)
    assert(act.boardTargetActions.exists(_.actionKind == "travel"))
    val travel = act.boardTargetActions.find(_.actionKind == "travel").get
    assertEquals(travel.minimum -> travel.maximum, 1 -> 1)
    assert(!travel.autoActivate)
    assert(travel.candidates.forall(_.target.isInstanceOf[
      BoardTargetRefProjection.Site]))
    assert(travel.candidates.forall(_.details.exists(_.endsWith("Supply"))))
    assertEquals(projector.projectPublic("game-projection-wake",
      LoadedGame(ended.state, ended.nextSequence)).boardTargetActions,
      Vector.empty)
  }

  test("Rest v5 commands persist reload and project the next player's Wake") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val setup = execute(service, "game-rest")
    val Ready(ready) = setup.state: @unchecked
    val active = ready.game.current.turn.activePlayer
    val act = service.handle("game-rest", setup.nextSequence,
      GameCommand.EndWake(active)).toOption.get
    val begun = service.handle("game-rest", act.nextSequence,
      GameCommand.BeginRest(active)).toOption.get
    val restProjection = new GameProjector(catalog).project("game-rest",
      LoadedGame(begun.state, begun.nextSequence), active)
    assertEquals(restProjection.phase, "rest")
    assertEquals(restProjection.legalControls, Vector("finishRest"))
    val finished = service.handle("game-rest", begun.nextSequence,
      GameCommand.FinishRest(active)).toOption.get
    val loaded = new GameApplicationService(catalog, repository)
      .load("game-rest").toOption.flatten.get
    val Ready(after) = loaded.state: @unchecked

    assertEquals(loaded.state, finished.state)
    assertEquals(after.game.current.turn.phase, Phase.Wake)
    assertNotEquals(after.game.current.turn.activePlayer, active)
    assertEquals(repository.load("game-rest").toOption.flatten.get.records
      .takeRight(2).map(record => ujson.read(record)("formatVersion").num.toInt),
      Vector(5, 5))
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

  test("v1 envelopes are not reinterpreted as v2 setup history") {
    val repository = new InMemoryEventStreamRepository
    repository.seed(
      "game-v1",
      Vector(ujson.write(ujson.Obj("formatVersion" -> 1)))
    )
    val result = new GameApplicationService(catalog, repository)
      .load("game-v1")

    assert(result.left.toOption.get match {
      case GameApplicationError.CodecFailure(
            _: UnsupportedFormatVersion
          ) => true
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
        oathdigital.serialization.WireError.InvalidSequence(
          "$[0].sequence",
          0L,
          1L
        )
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

  test("HSQL reopen preserves completed Negotiation disclosure knowledge") {
    val path = Files.createTempDirectory("oathdigital-negotiation-reopen-").resolve("journal")
    val gameId = "game-hsql-negotiation"
    val first = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    val (completed, actor, other, adviser) = try {
      val service = new GameApplicationService(catalog, first)
      val setup = execute(service, gameId)
      val Ready(ready) = setup.state: @unchecked
      val actor = ready.game.current.turn.activePlayer
      val other = ready.game.current.players.find(_.player != actor).get
      val act = service.handle(gameId, setup.nextSequence,
        GameCommand.EndWake(actor)).toOption.get
      val traveled = service.handle(gameId, act.nextSequence,
        GameCommand.Travel(actor, other.pawnSite.get)).toOption.get
      val started = service.handle(gameId, traveled.nextSequence,
        GameCommand.BeginNegotiation(actor, Vector(other.player))).toOption.get
      val decision = started.state.asInstanceOf[Ready].value.game.current.pending.get
        .asInstanceOf[PendingProcedure.Negotiation].decision
      val adviser = ready.game.current.players.find(_.player == actor).get
        .advisers.head.id.asInstanceOf[WorldCardId]
      val terms = NegotiationTerms(disclosures = Vector(NegotiationDisclosure(
        other.player, NegotiationDisclosureRef.Adviser(actor, adviser))))
      val changed = service.handle(gameId, started.nextSequence,
        GameCommand.ReplaceNegotiationTerms(actor, decision, terms)).fold(
          error => fail(s"failed to persist Negotiation terms: $error"), identity)
      assertEquals(service.handle(gameId, started.nextSequence,
        GameCommand.AcceptNegotiation(actor, decision)).left.toOption,
        Some(GameApplicationError.StaleClientPosition(
          started.nextSequence, changed.nextSequence)))
      val actorAccepted = service.handle(gameId, changed.nextSequence,
        GameCommand.AcceptNegotiation(actor, decision)).toOption.get
      val completed = service.handle(gameId, actorAccepted.nextSequence,
        GameCommand.AcceptNegotiation(other.player, decision)).toOption.get
      (completed, actor, other.player, adviser)
    } finally first.close()
    val reopened = OwnedHsqldbEventStreamRepository.open(path).fold(
      error => fail(s"failed to reopen Negotiation repository: $error"), identity)
    try {
      val loaded = new GameApplicationService(catalog, reopened)
        .load(gameId).toOption.flatten.get
      assertEquals(loaded.state, completed.state)
      val projector = new GameProjector(catalog)
      assertEquals(projector.project(gameId, loaded, actor).negotiation, None)
      val recipientBoard = projector.project(gameId, loaded, other).playerBoards
        .find(_.playerId == actor.value).get
      assert(recipientBoard.advisers.exists(card => card.cardId == adviser.value && !card.hidden))
      val publicBoard = projector.projectPublic(gameId, loaded).playerBoards
        .find(_.playerId == actor.value).get
      assert(publicBoard.advisers.forall(card => card.cardId != adviser.value || card.hidden))
      assertEquals(projector.projectPublic(gameId, loaded).negotiation, None)
    } finally reopened.close()
  }


  test("HSQL reopen preserves Raid pending and completed replay") {
    val path = Files.createTempDirectory("oathdigital-raid-reopen-").resolve("journal")
    val gameId = "game-hsql-raid"
    val firstRepository = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    val (started, attacker, defender, decision, origin, force) = try {
      beginServiceRaid(new GameApplicationService(catalog, firstRepository,
        campaignDicePort = blankCampaignDice), gameId)
    } finally firstRepository.close()

    val secondRepository = OwnedHsqldbEventStreamRepository.open(path).fold(
      error => fail(s"failed to reopen pending Raid repository: $error"),
      identity
    )
    val completed = try {
      val service = new GameApplicationService(catalog, secondRepository,
        campaignDicePort = blankCampaignDice)
      assertEquals(service.load(gameId).toOption.flatten.get.state, started.state)
      val attackerDone = service.handle(gameId, started.nextSequence,
        GameCommand.FinishCampaignPlans(attacker, decision)).toOption.get
      val rolled = service.handle(gameId, attackerDone.nextSequence,
        GameCommand.FinishCampaignPlans(defender, decision)).toOption.get
      val Ready(rolledReady) = rolled.state: @unchecked
      val pending = rolledReady.game.current.pending.get
        .asInstanceOf[PendingProcedure.Campaign]
      val sacrifice = math.max(0,
        CampaignRules.defenderForce(rolledReady, pending) + 1 - pending.attack)
      val won = service.handle(gameId, rolled.nextSequence,
        GameCommand.ChooseCampaignSacrifice(attacker, decision, sacrifice))
        .toOption.get
      val destination = rolledReady.game.current.map.inPlay.find(_ != origin).get
      service.handle(gameId, won.nextSequence,
        GameCommand.RelocateCampaignRaidPawn(attacker, decision, destination))
        .toOption.get
    } finally secondRepository.close()

    val thirdRepository = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    try {
      val loaded = new GameApplicationService(catalog, thirdRepository,
        campaignDicePort = blankCampaignDice).load(gameId).toOption.flatten.get
      assertEquals(loaded.state, completed.state)
      assertEquals(loaded.nextSequence, completed.nextSequence)
      val Ready(after) = loaded.state: @unchecked
      assertEquals(after.game.current.pending, None)
      assertEquals(after.game.current.players.find(_.player == defender).get.pawnSite,
        completed.state match {
          case Ready(value) => value.game.current.players
            .find(_.player == defender).get.pawnSite
          case _ => fail("completed Raid must remain ready")
        })
      assert(force >= 0)
    } finally thirdRepository.close()
  }
}
