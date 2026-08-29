package oathdigital.gameplay

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.protocol.projection.{BoardTargetFormationProjection,
  BoardTargetRefProjection}
import oathdigital.gameplay.actions.{Campaign, CampaignCommand, CampaignLosingForceRegistry,
  CampaignLosingForceResolver, CampaignPlanEffects, CampaignRules}
import oathdigital.model._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState.Ready
import oathdigital.catalog.CatalogPower
import oathdigital.gameplay.OathViolation._

class CampaignSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)

  private def startAndChoose(ready: ReadyGame, player: PlayerState, site: SiteId,
      id: DecisionId, force: Int, dice: Vector[AttackDieFace],
      source: Option[PendingProcedure.CampaignPlanSource] = None): OathTransition = {
    val started = rules.handle(Ready(ready), CampaignCommand.Start(
      player.player, id, site, force)).toOption.get
    val selected = source.fold(started)(value => rules.handle(started.state,
      CampaignCommand.ChoosePlan(player.player, id, value)).toOption.get)
    rules.handle(selected.state, CampaignCommand.FinishPlans(
      player.player, id, dice)).toOption.get
  }

  private def campaignReady: (ReadyGame, PlayerState, SiteId) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val active = base.game.current.players.find(_.player == base.game.current.turn.activePlayer).get
    val siteId = base.game.current.map.inPlay.find(id =>
      catalog.sites.find(_.id == id).exists(_.handlers.forall(h =>
        !h.endsWith(".mountain") && !h.endsWith(".plains")))).get
    val site = base.game.current.map.sites(siteId).copy(
      forces = SiteForces.Occupied(ForceKind.Bandit, 2), denizens = Vector.empty)
    val moved = active.copy(pawnSite = Some(siteId))
    val ready = base.copy(game = base.game.copy(current = base.game.current.copy(
      turn = base.game.current.turn.copy(phase = Phase.Act),
      players = base.game.current.players.map(p => if (p.player == active.player) moved else p),
      map = base.game.current.map.copy(sites = base.game.current.map.sites.updated(siteId, site)))))
    (ready, moved, siteId)
  }

  private def raidReady: (ReadyGame, PlayerState, PlayerState, SiteId, RelicId) = {
    val (base, attacker0, site) = campaignReady
    val defender0 = base.game.current.players.find(_.player != attacker0.player).get
    val relic = RelicId(catalog.relics.head.id.value)
    val attacker = attacker0.copy(board = attacker0.board.copy(warbands = 4))
    val defender = defender0.copy(pawnSite = Some(site),
      board = defender0.board.copy(warbands = 5, favor = 5),
      advisers = Vector(
        DenizenState(DenizenId("raid-facedown-denizen"), Orientation.FaceDown, Tokens.empty),
        VisionState(VisionId("raid-facedown-vision"), Orientation.FaceDown),
        VisionState(CampaignRules.Conspiracy, Orientation.FaceDown)),
      relics = Vector(RelicState(relic, Orientation.FaceUp, Tokens.empty),
        RelicState(RelicId("raid-facedown-relic"), Orientation.FaceDown, Tokens.empty)))
    val current = base.game.current.copy(players = base.game.current.players.map {
      case p if p.player == attacker.player => attacker
      case p if p.player == defender.player => defender
      case p => p
    }, banners = base.game.current.banners.copy(
      peoplesFavor = base.game.current.banners.peoplesFavor.copy(
        holder = Some(defender.player), favor = 3),
      darkestSecret = base.game.current.banners.darkestSecret.copy(
        holder = Some(defender.player), secrets = 2)))
    (base.copy(game = base.game.copy(current = current)), attacker, defender, site, relic)
  }

  test("Raid targets require the co-located pawn and preserve canonical order") {
    val (ready, attacker, defender, _, relic) = raidReady
    val pawn = CampaignRaidTarget.Pawn(defender.player)
    val relicTarget = CampaignRaidTarget.Relic(defender.player, relic)
    val banner = CampaignRaidTarget.Banner(defender.player,
      CampaignBanner.PeoplesFavor)
    assertEquals(CampaignRules.legalRaidTargets(catalog, ready, attacker.player),
      Vector(pawn, relicTarget, banner,
        CampaignRaidTarget.Banner(defender.player, CampaignBanner.DarkestSecret)))
    assertEquals(CampaignRules.validateRaidStart(catalog, ready, attacker.player,
      Vector(pawn), 0), Right(CampaignDefender.Player(defender.player)))
    assert(CampaignRules.validateRaidStart(catalog, ready, attacker.player,
      Vector(pawn, banner, relicTarget), 0).isLeft)
    assert(CampaignRules.validateRaidStart(catalog, ready, attacker.player,
      Vector(relicTarget), 0).isLeft)
  }

  test("Raid defense uses pawn and targeted printed defense plus board force") {
    val (ready, attacker, defender, _, relic) = raidReady
    val targets = Vector[CampaignRaidTarget](
      CampaignRaidTarget.Pawn(defender.player),
      CampaignRaidTarget.Relic(defender.player, relic),
      CampaignRaidTarget.Banner(defender.player, CampaignBanner.PeoplesFavor),
      CampaignRaidTarget.Banner(defender.player, CampaignBanner.DarkestSecret))
    val campaign = PendingProcedure.Campaign(DecisionId("raid-pool"), attacker.player,
      Vector.empty, CampaignDefender.Player(defender.player), 4, Vector.empty,
      attackerPlansFinished = true, defenderPlansFinished = true, Vector.empty,
      0, 0, None, Vector.empty, None, None, CampaignKind.Raid, targets)
    assertEquals(CampaignRules.defenderForce(ready, campaign), 5)
    assertEquals(CampaignRules.defenseDiceCount(catalog, ready, campaign),
      2 + catalog.relics.find(_.id.value == relic.value).get.defense + 6)
  }

  test("Raid target projection is canonical private and requires the pawn") {
    val (ready, attacker, defender, _, relic) = raidReady
    val projector = new GameProjector(catalog)
    val own = projector.project("raid-targets", LoadedGame(Ready(ready), 4),
      attacker.player)
    val action = own.boardTargetActions.find(_.actionKind == "campaign-raid").get
    assertEquals(action.requiredTargets,
      Vector(BoardTargetRefProjection.PlayerPawn(defender.player.value)))
    assertEquals(action.candidates.map(_.target), Vector(
      BoardTargetRefProjection.PlayerPawn(defender.player.value),
      BoardTargetRefProjection.PlayerRelic(defender.player.value, relic.value),
      BoardTargetRefProjection.PlayerBanner(defender.player.value, "peoples-favor"),
      BoardTargetRefProjection.PlayerBanner(defender.player.value, "darkest-secret")))
    assertEquals(projector.projectPublic("raid-targets",
      LoadedGame(Ready(ready), 4)).boardTargetActions, Vector.empty)
    assert(!projector.project("raid-targets", LoadedGame(Ready(ready), 4),
      defender.player).legalControls.contains("beginCampaignRaid"))
  }

  test("successful Raid durably resolves losses and relocates without Travel") {
    val (ready0, attacker, defender, origin, relic) = raidReady
    val refillSite = ready0.game.current.map.inPlay.find(_ != origin).get
    val ready = ready0.copy(game = ready0.game.copy(current =
      ready0.game.current.copy(map = ready0.game.current.map.copy(sites =
        ready0.game.current.map.sites.updated(refillSite,
          ready0.game.current.map.sites(refillSite).copy(
            forces = SiteForces.Empty))))))
    val decision = DecisionId("raid-resolution")
    val targets = Vector[CampaignRaidTarget](
      CampaignRaidTarget.Pawn(defender.player),
      CampaignRaidTarget.Relic(defender.player, relic),
      CampaignRaidTarget.Banner(defender.player, CampaignBanner.PeoplesFavor),
      CampaignRaidTarget.Banner(defender.player, CampaignBanner.DarkestSecret))
    val started = rules.handle(Ready(ready), CampaignCommand.StartRaid(
      attacker.player, decision, targets, 4)).toOption.get
    val attackerDone = rules.handle(started.state, CampaignCommand.FinishPlans(
      attacker.player, decision, Vector.empty)).toOption.get
    val defenderDone = rules.handle(attackerDone.state, CampaignCommand.FinishPlans(
      defender.player, decision, Vector.fill(4)(AttackDieFace.OneSword))).toOption.get
    val won = rules.handle(defenderDone.state, CampaignCommand.Sacrifice(
      attacker.player, decision, 2, Vector.fill(
        2 + catalog.relics.find(_.id.value == relic.value).get.defense + 6)(
        DefenseDieFace.Blank))).toOption.get
    val destination = refillSite
    val completed = rules.handle(won.state, CampaignCommand.RelocateRaidPawn(
      attacker.player, decision, destination)).toOption.get
    assertEquals(completed.events.take(2).map(_.getClass.getSimpleName),
      Vector("CampaignRaided", "CampaignRaidPawnRelocated"))
    assert(completed.events.drop(2).exists(_.isInstanceOf[BanditsRefilled]),
      "Raid relocation must enter the completed-action boundary")
    assert(completed.events.count(_.isInstanceOf[BanditsRefilled]) <= 1)
    assert(completed.events.count(e => e.isInstanceOf[OathkeeperChanged] ||
      e.isInstanceOf[OathkeeperRecipientChoiceStarted]) <= 1)
    val Ready(after) = completed.state: @unchecked
    val nextAttacker = after.game.current.players.find(_.player == attacker.player).get
    val nextDefender = after.game.current.players.find(_.player == defender.player).get
    assert(nextAttacker.relics.exists(_.id == relic))
    assertEquals(nextDefender.board.warbands, 3)
    assertEquals(nextDefender.board.favor, 3)
    assertEquals(nextDefender.pawnSite, Some(destination))
    val discardRegion = CampaignRules.nextRegion(
      ready.game.current.map.regionOf(origin).get)
    assertEquals(after.game.current.commonCards.discard(discardRegion).takeRight(2),
      Vector(DenizenId("raid-facedown-denizen"), VisionId("raid-facedown-vision")))
    assert(!after.game.campaign.dispossessed.contains(CampaignRules.Conspiracy))
    assertEquals(after.game.campaign.reliquary.last,
      RelicId("raid-facedown-relic"))
    assertEquals(after.game.current.pending, None)
    val replayed = completed.events.foldLeft[Either[OathViolation, OathState]](
      Right(won.state))((state, event) => state.flatMap(rules.evolve(_, event)))
    assertEquals(replayed, Right(completed.state))
    val raided = completed.events.head.asInstanceOf[CampaignRaided]
    assert(rules.evolve(won.state, raided.copy(favorBurned =
      raided.favorBurned + 1)).isLeft)
    assertEquals(raided.bannerFavorReturned.values.sum, 3)
    assertEquals(raided.darkestSecretBurned, 2)
    assertEquals(raided.adviserDiscardRegion, discardRegion)
    assertEquals(raided.boxedConspiracy, Some(CampaignRules.Conspiracy))
    assert(rules.evolve(won.state, raided.copy(adviserDiscardRegion =
      ready.game.current.map.regionOf(origin).get)).isLeft)
    assert(rules.evolve(won.state, raided.copy(darkestSecretBurned = 1)).isLeft)
  }

  test("lost Raid anchors committed losses at the co-location site") {
    val (ready, attacker, defender, origin, _) = raidReady
    val decision = DecisionId("raid-defeat")
    val targets = Vector[CampaignRaidTarget](CampaignRaidTarget.Pawn(defender.player))
    val started = rules.handle(Ready(ready), CampaignCommand.StartRaid(
      attacker.player, decision, targets, 4)).toOption.get
    val attackerDone = rules.handle(started.state, CampaignCommand.FinishPlans(
      attacker.player, decision, Vector.empty)).toOption.get
    val rolled = rules.handle(attackerDone.state, CampaignCommand.FinishPlans(
      defender.player, decision, Vector.fill(4)(AttackDieFace.HollowSword))).toOption.get
    val defeated = rules.handle(rolled.state, CampaignCommand.Sacrifice(
      attacker.player, decision, 0, Vector.fill(2)(DefenseDieFace.TwoShields)))
      .toOption.get
    val event = defeated.events.head.asInstanceOf[CampaignSacrificed]
    assertEquals(event.victorious, false)
    assertEquals(event.losingForces.map(_.site).distinct, Vector(origin))
    val Ready(after) = defeated.state: @unchecked
    assertEquals(after.game.current.players.find(_.player == attacker.player).get
      .board.warbands, 2)
    assertEquals(rules.evolve(rolled.state, event), Right(defeated.state))
    val other = ready.game.current.map.inPlay.find(_ != origin).get
    val tampered = event.copy(losingForces = event.losingForces.map {
      case effect: CampaignLosingForceEffect.KillCommitted => effect.copy(site = other)
      case effect: CampaignLosingForceEffect.ReturnToBoard => effect.copy(site = other)
      case effect => effect
    })
    assert(rules.evolve(rolled.state, tampered).isLeft)
  }

  test("attack faces implement hollow pairs and skull swords") {
    assertEquals(AttackDieFace.score(Vector(AttackDieFace.HollowSword,
      AttackDieFace.HollowSword, AttackDieFace.HollowSword,
      AttackDieFace.OneSword, AttackDieFace.TwoSwordsSkull)), 4)
    assertEquals(AttackDieFace.skulls(Vector(AttackDieFace.TwoSwordsSkull,
      AttackDieFace.OneSword)), 1)
    assertEquals(CampaignRules.attackResult(
      Vector.fill(6)(AttackDieFace.TwoSwordsSkull), force = 2,
      ignoreSkulls = false), 4 -> 2)
    assertEquals(CampaignRules.attackResult(
      Vector.fill(6)(AttackDieFace.TwoSwordsSkull), force = 2,
      ignoreSkulls = true), 12 -> 0)
  }

  test("unimplemented Campaign plan extension effects fail explicitly") {
    val effects = Vector[PendingProcedure.CampaignPlanEffect](
      PendingProcedure.CampaignPlanEffect.TransformAttackResult("future-transform"),
      PendingProcedure.CampaignPlanEffect.ReplaceLosingForcePolicy("future-policy"),
      PendingProcedure.CampaignPlanEffect.Suspend("future-choice"))
    effects.foreach(effect => assert(
      CampaignPlanEffects.validateExecutable(Vector(effect)).isLeft, effect.toString))
  }

  test("legality and projection agree on mandatory bandit origin") {
    val (ready, player, site) = campaignReady
    val legal = CampaignRules.legalTargets(catalog, ready, player.player)
    assertEquals(legal.head, site)
    assert(legal.size > 1)
    val projection = new GameProjector(catalog).project("campaign",
      LoadedGame(Ready(ready), 1), player.player)
    val action = projection.boardTargetActions.find(_.actionKind == "campaign-conquest").get
    assertEquals(action.minimum -> action.maximum, 1 -> legal.size)
    assertEquals(action.candidates.map(_.target), legal.map(id =>
      BoardTargetRefProjection.Site(id.value)))
    assertEquals(action.requiredTargets,
      Vector(BoardTargetRefProjection.Site(site.value)))
    assertEquals(action.candidates.head.details,
      Vector("2 Supply", s"Choose 0 to ${player.board.warbands} board warbands"))
    assertEquals(action.formation, Some(
      BoardTargetFormationProjection(
        0, player.board.warbands, player.board.warbands, 2)))
    val hidden = new GameProjector(catalog).projectPublic("campaign",
      LoadedGame(Ready(ready), 1))
    assertEquals(hidden.boardTargetActions, Vector.empty)

    val reduced = ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      players = ready.game.current.players.map(p => if (p.player == player.player)
        p.copy(board = p.board.copy(warbands = 2)) else p))))
    val changed = new GameProjector(catalog).project("campaign",
      LoadedGame(Ready(reduced), 2), player.player).boardTargetActions
      .find(_.actionKind == "campaign-conquest").flatMap(_.formation).get
    assertEquals(changed.maximumForce -> changed.availableWarbands, 2 -> 2)
  }

  test("multi-site Conquest preserves the mandatory origin and canonical targets") {
    val (ready, player, pawn) = campaignReady
    val legal = CampaignRules.legalTargets(catalog, ready, player.player)
    val optional = legal(1)
    val targets = Vector(pawn, optional)
    val decision = DecisionId("campaign-multi-target")
    val transition = rules.handle(Ready(ready), CampaignCommand.Start(
      player.player, decision, targets, 0)).toOption.get
    val started = transition.events.head.asInstanceOf[CampaignStarted]
    assertEquals(started.targetSites, targets)
    val Ready(afterStart) = transition.state: @unchecked
    assertEquals(afterStart.game.current.pending.collect {
      case campaign: PendingProcedure.Campaign => campaign.targetSites
    }, Some(targets))
    assert(rules.handle(Ready(ready), CampaignCommand.Start(player.player,
      decision, Vector(optional), 0)).isLeft)
    assert(rules.handle(Ready(ready), CampaignCommand.Start(player.player,
      decision, Vector(pawn, pawn), 0)).isLeft)
    assert(rules.handle(Ready(ready), CampaignCommand.Start(player.player,
      decision, Vector(pawn, legal.last, optional), 0)).isLeft)
    assertEquals(rules.evolve(Ready(ready), started), Right(transition.state))
  }

  test("Pass permits its own target but blocks other sites in its region") {
    val Ready(initial) = execute(setup)._1: @unchecked
    val pass = catalog.sites.find(_.handlers.contains(
      "site.narrow-pass.pass")).get.id
    val others = catalog.sites.map(_.id).filterNot(_ == pass)
    val ordered = Vector(others.head, others(1), pass, others(2), others(3),
      others(4), others(5), others(6))
    val states = ordered.map { id =>
      val definition = catalog.sites.find(_.id == id).get
      id -> SiteState(
        if (definition.capacity == 0) SiteForces.Empty
        else SiteForces.Occupied(ForceKind.Bandit, definition.capacity),
        Vector.empty, Vector.empty, definition.startingResources)
    }.toMap
    val source = ordered.head
    val blocked = ordered(3)
    val activeId = initial.game.current.turn.activePlayer
    val players = initial.game.current.players.map { player =>
      if (player.player == activeId) player.copy(pawnSite = Some(source),
        board = player.board.copy(supply = SupplyTrack(Campaign.SupplyCost)))
      else player
    }
    val base = initial.copy(game = initial.game.copy(current =
      initial.game.current.copy(players = players,
        map = MapState(ordered.take(2), ordered.slice(2, 5), ordered.slice(5, 8),
          states), turn = initial.game.current.turn.copy(phase = Phase.Act))))
    assert(CampaignRules.passAllowsTarget(catalog, base, activeId, source, pass))
    assert(!CampaignRules.passAllowsTarget(catalog, base, activeId, source, blocked))
    val lineage = players.find(_.player == activeId).get.lineage
    val controlledPass = base.copy(game = base.game.copy(current =
      base.game.current.copy(map = base.game.current.map.copy(sites =
        base.game.current.map.sites.updated(pass,
          base.game.current.map.sites(pass).copy(forces =
            SiteForces.Occupied(ForceKind.Exile(lineage), 1)))))))
    assert(CampaignRules.passAllowsTarget(catalog, controlledPass, activeId,
      source, blocked))
  }

  test("Campaign projection permits an empty force and requires full Supply cost") {
    val (ready, player, site) = campaignReady
    def withResources(warbands: Int, supply: Int): ReadyGame =
      ready.copy(game = ready.game.copy(current = ready.game.current.copy(
        players = ready.game.current.players.map(p => if (p.player == player.player)
          p.copy(board = p.board.copy(warbands = warbands,
            supply = SupplyTrack(supply))) else p))))
    def campaignAction(state: ReadyGame) = new GameProjector(catalog)
      .project("campaign-resources", LoadedGame(Ready(state), 2), player.player)
      .boardTargetActions.find(_.actionKind == "campaign-conquest")

    Vector(0 -> 0, 0 -> 1, 1 -> 0, 1 -> 1).foreach {
      case (warbands, supply) =>
        val state = withResources(warbands, supply)
        assertEquals(CampaignRules.legalTargets(catalog, state, player.player),
          Vector.empty)
        assertEquals(campaignAction(state), None)
        assert(rules.handle(Ready(state), CampaignCommand.Start(player.player,
          DecisionId(s"campaign-insufficient-$warbands-$supply"), site, 0))
          .left.toOption.get.isInstanceOf[InsufficientSupply])
    }

    val empty = withResources(0, Campaign.SupplyCost)
    assertEquals(CampaignRules.legalTargets(catalog, empty, player.player).head,
      site)
    assertEquals(campaignAction(empty).flatMap(_.formation), Some(
      BoardTargetFormationProjection(0, 0, 0,
        Campaign.SupplyCost)))

    val exact = withResources(1, Campaign.SupplyCost)
    val formation = campaignAction(exact).flatMap(_.formation).get
    assertEquals(formation, BoardTargetFormationProjection(
      Campaign.MinimumForce, 1, 1, Campaign.SupplyCost))
  }

  test("formation projection rejects malformed authoritative bounds") {
    intercept[IllegalArgumentException](BoardTargetFormationProjection(-1, 1, 1, 2))
    intercept[IllegalArgumentException](BoardTargetFormationProjection(2, 1, 2, 2))
    intercept[IllegalArgumentException](BoardTargetFormationProjection(1, 2, 1, 2))
    intercept[IllegalArgumentException](BoardTargetFormationProjection(0, 0, -1, 2))
    intercept[IllegalArgumentException](BoardTargetFormationProjection(1, 1, 1, -1))
  }

  test("empty force finishes with an empty attack pool and zero bounds") {
    val (ready, player, site) = campaignReady
    val decision = DecisionId("campaign-empty")
    val declared = rules.handle(Ready(ready), CampaignCommand.Start(player.player,
      decision, site, 0)).toOption.get
    val finished = rules.handle(declared.state, CampaignCommand.FinishPlans(
      player.player, decision, Vector.empty)).toOption.get
    val Ready(after) = finished.state: @unchecked
    val pending = after.game.current.pending.get.asInstanceOf[PendingProcedure.Campaign]
    assertEquals(pending.attackDice, Vector.empty)
    assertEquals(pending.attack -> pending.skullLosses, 0 -> 0)
    val projected = new GameProjector(catalog).project("campaign-empty",
      LoadedGame(finished.state, 2), player.player).campaign.get
    assertEquals(projected.maxSacrifice -> projected.maxPlacement, 0 -> 0)
  }

  test("empty force can use Brass Army with and without Outriders") {
    val (ready, player, site) = campaignReady
    val brass = catalog.relics.find(_.handlers.contains("relic.brass-army")).get
    val outriders = catalog.denizens.find(_.handlers.contains("denizen.outriders")).get
    val brassSource = PendingProcedure.CampaignPlanSource.Relic(player.player,
      RelicId(brass.id.value))
    val outridersSource = PendingProcedure.CampaignPlanSource.Adviser(player.player,
      DenizenId(outriders.id.value))
    val actor = player.copy(board = player.board.copy(faceUpSecrets = 1),
      advisers = Vector(DenizenState(DenizenId(outriders.id.value),
        Orientation.FaceUp, Tokens.empty)),
      relics = Vector(RelicState(RelicId(brass.id.value), Orientation.FaceUp,
        Tokens.empty)))
    val state = ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      players = ready.game.current.players.map(p =>
        if (p.player == player.player) actor else p))))
    val skulls = Vector.fill(4)(AttackDieFace.TwoSwordsSkull)

    def finish(id: String, plans: Vector[PendingProcedure.CampaignPlanSource]) = {
      val declared = rules.handle(Ready(state), CampaignCommand.Start(player.player,
        DecisionId(id), site, 0)).toOption.get
      val selected = plans.foldLeft(declared) { (transition, source) =>
        rules.handle(transition.state, CampaignCommand.ChoosePlan(player.player,
          DecisionId(id), source)).toOption.get
      }
      rules.handle(selected.state, CampaignCommand.FinishPlans(player.player,
        DecisionId(id), skulls)).toOption.get
    }

    val Ready(brassOnly) = finish("campaign-empty-brass",
      Vector(brassSource)).state: @unchecked
    val brassResult = brassOnly.game.current.pending.get
      .asInstanceOf[PendingProcedure.Campaign]
    assertEquals(brassResult.attackDice.size, 4)
    assertEquals(brassResult.attack -> brassResult.skullLosses, 0 -> 0)

    val Ready(combined) = finish("campaign-empty-combined",
      Vector(outridersSource, brassSource)).state: @unchecked
    val combinedResult = combined.game.current.pending.get
      .asInstanceOf[PendingProcedure.Campaign]
    assertEquals(combinedResult.attackDice.size, 4)
    assertEquals(combinedResult.attack -> combinedResult.skullLosses, 8 -> 0)
  }

  test("staged conquest records cost dice sacrifice and explicit placement") {
    val (ready, player, site) = campaignReady
    val id = DecisionId("campaign-test")
    val started = startAndChoose(ready, player, site, id, 3,
      Vector(AttackDieFace.OneSword, AttackDieFace.OneSword,
        AttackDieFace.TwoSwordsSkull))
    val Ready(afterStart) = started.state: @unchecked
    val pending = afterStart.game.current.pending.get.asInstanceOf[PendingProcedure.Campaign]
    assertEquals(pending.attack, 4)
    assertEquals(pending.skullLosses, 1)
    assertEquals(afterStart.game.current.players.find(_.player == player.player).get
      .board.supply.supply, player.board.supply.supply - 2)

    val battled = rules.handle(started.state, CampaignCommand.Sacrifice(player.player,
      id, 0, Vector.fill(catalog.sites.find(_.id == site).get.defense)(
        DefenseDieFace.Blank))).toOption.get
    val Ready(afterBattle) = battled.state: @unchecked
    assert(afterBattle.game.current.pending.exists(_.isInstanceOf[PendingProcedure.Campaign]))
    val conquered = rules.handle(battled.state,
      CampaignCommand.Place(player.player, id,
        Vector(CampaignForceAllocation(site, 1)))).toOption.get
    val Ready(after) = conquered.state: @unchecked
    assertEquals(after.game.current.map.sites(site).forces,
      SiteForces.Occupied(ForceKind.Exile(player.lineage), 1))
    assertEquals(after.game.current.pending, None)
  }

  test("explicit projected maximum preserves the prior all-warband path") {
    val (ready, player, site) = campaignReady
    val maximum = player.board.warbands
    val started = rules.handle(Ready(ready), CampaignCommand.Start(player.player,
      DecisionId("campaign-maximum"), site, maximum)).toOption.get
    val Ready(after) = started.state: @unchecked
    assertEquals(after.game.current.pending.collect {
      case campaign: PendingProcedure.Campaign => campaign.force
    }, Some(maximum))
    assertEquals(after.game.current.players.find(_.player == player.player).get
      .board.warbands, 0)
  }

  test("defeat kills half surviving force and replay rejects tampering") {
    val (ready, player, site) = campaignReady
    val id = DecisionId("campaign-loss")
    assert(rules.evolve(Ready(ready), CampaignStarted(player.player, id, site,
      1, 2)).left.toOption.get
      .isInstanceOf[CampaignOutcomeMismatch])
    val started = startAndChoose(ready, player, site, id, 2,
      Vector.fill(2)(AttackDieFace.HollowSword))
    val defenseDice = Vector.fill(catalog.sites.find(_.id == site).get.defense)(
      DefenseDieFace.TwoShields)
    val recorded = CampaignSacrificed(player.player, id, 0, defenseDice,
      attack = 99, defense = 99, skullLosses = 0, victorious = false)
    assert(rules.evolve(started.state, recorded).left.toOption.get
      .isInstanceOf[CampaignOutcomeMismatch])
    val defeated = rules.handle(started.state, CampaignCommand.Sacrifice(
      player.player, id, 0, defenseDice)).toOption.get
    val event = defeated.events.head.asInstanceOf[CampaignSacrificed]
    assertEquals(event.losingForcePolicyId,
      Some(CampaignLosingForceResolver.default.id))
    assertEquals(event.losingForces.collect {
      case CampaignLosingForceEffect.KillCommitted(_, _, _, count) => count
    }, Vector(1))
    val Ready(after) = defeated.state: @unchecked
    assertEquals(after.game.current.pending, None)
    assertEquals(after.game.current.players.find(_.player == player.player).get
      .board.warbands, player.board.warbands - 1)
    assert(rules.evolve(started.state, event.copy(losingForces =
      event.losingForces.collect {
        case value: CampaignLosingForceEffect.ReturnToBoard =>
          value.copy(count = value.count + 1)
      })).isLeft)
  }

  test("unimplemented optional facedown adviser does not block Campaign") {
    val (ready, player, site) = campaignReady
    val relevant = catalog.denizens.find(_.rulesText.toLowerCase.contains("campaign")).get
    val withPower = ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      players = ready.game.current.players.map(p => if (p.player != player.player) p else
        p.copy(advisers = Vector(DenizenState(DenizenId(relevant.id.value),
          Orientation.FaceDown, Tokens.empty)))))))
    assert(CampaignRules.validateStart(catalog, withPower, player.player, site, 1).isRight)
  }

  test("faceup Vow of Peace is an exact-ID Campaign block") {
    val (ready, player, site) = campaignReady
    val vow = catalog.denizens.find(_.handlers.contains("denizen.vow-of-peace")).get
    val blocked = ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      players = ready.game.current.players.map(p => if (p.player != player.player) p else
        p.copy(advisers = Vector(DenizenState(DenizenId(vow.id.value),
          Orientation.FaceUp, Tokens.empty)))))))
    val error = CampaignRules.validateStart(catalog, blocked, player.player, site, 1)
      .left.toOption.get
    assert(error.isInstanceOf[CampaignUnavailable])
    assert(error.toString.contains("Vow of Peace"))
  }

  test("facedown owned Outriders is offered by exact source and ignores recorded skulls") {
    val (ready, player, site) = campaignReady
    val outriders = catalog.denizens.find(_.handlers.contains("denizen.outriders")).get
    val source = PendingProcedure.CampaignPlanSource.Adviser(player.player,
      DenizenId(outriders.id.value))
    val state = ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      players = ready.game.current.players.map(p => if (p.player != player.player) p else
        p.copy(advisers = Vector(DenizenState(DenizenId(outriders.id.value),
          Orientation.FaceDown, Tokens.empty)))))))
    val declared = rules.handle(Ready(state), CampaignCommand.Start(player.player,
      DecisionId("campaign-outriders"), site, 1)).toOption.get
    val Ready(pendingState) = declared.state: @unchecked
    val pending = pendingState.game.current.pending.get.asInstanceOf[PendingProcedure.Campaign]
    assertEquals(CampaignRules.legalPlanChoices(catalog, pendingState, pending),
      Vector(source))
    val projector = new GameProjector(catalog)
    val actorView = projector.project("campaign-outriders",
      LoadedGame(declared.state, 1), player.player)
    assertEquals(actorView.campaign.get.planChoices.map(_.kind),
      Vector("adviser"))
    assertEquals(actorView.legalControls,
      Vector("chooseCampaignPlan", "finishCampaignPlans"))
    val other = pendingState.game.current.players.find(_.player != player.player).get.player
    val otherView = projector.project("campaign-outriders",
      LoadedGame(declared.state, 1), other)
    assertEquals(otherView.campaign, None)
    assertEquals(otherView.phase, "campaign-waiting")
    assertEquals(projector.projectPublic("campaign-outriders",
      LoadedGame(declared.state, 1)).campaign, None)
    val selected = rules.handle(declared.state, CampaignCommand.ChoosePlan(player.player,
      pending.decision, source)).toOption.get
    assert(selected.events.head.asInstanceOf[CampaignPlanChosen].revealed)
    val chosen = rules.handle(selected.state, CampaignCommand.FinishPlans(player.player,
      pending.decision, Vector(AttackDieFace.TwoSwordsSkull))).toOption.get
    val Ready(after) = chosen.state: @unchecked
    val result = after.game.current.pending.get.asInstanceOf[PendingProcedure.Campaign]
    assertEquals(result.attack, 2)
    assertEquals(result.skullLosses, 0)
    assertEquals(after.game.current.players.find(_.player == player.player).get
      .advisers.head.asInstanceOf[DenizenState].orientation, Orientation.FaceUp)
  }

  test("Campaign plan choice rejects stale and tampered sources") {
    val (ready, player, site) = campaignReady
    val declared = rules.handle(Ready(ready), CampaignCommand.Start(player.player,
      DecisionId("campaign-stale-plan"), site, 1)).toOption.get
    val fake = PendingProcedure.CampaignPlanSource.Adviser(player.player,
      DenizenId("not-outriders"))
    val error = rules.handle(declared.state, CampaignCommand.ChoosePlan(player.player,
      DecisionId("campaign-stale-plan"), fake))
      .left.toOption.get
    assert(error.isInstanceOf[CampaignPlanUnavailable])
    val tampered = CampaignPlansFinished(player.player,
      DecisionId("campaign-stale-plan"), PendingProcedure.CampaignPlanSide.Attacker,
      Vector.empty, Vector.empty,
      Vector(PendingProcedure.CampaignPlanEffect.IgnoreAttackSkulls),
      Vector(AttackDieFace.TwoSwordsSkull),
      attack = 2, skullLosses = 0)
    assert(rules.evolve(declared.state, tampered).left.toOption.get
      .isInstanceOf[CampaignOutcomeMismatch])
  }

  test("Brass Army shares the plan choice, pays onto the relic, and adds four dice") {
    val (ready, player, site) = campaignReady
    val brass = catalog.relics.find(_.handlers == Vector("relic.brass-army")).get
    val outriders = catalog.denizens.find(_.handlers.contains("denizen.outriders")).get
    val brassId = RelicId(brass.id.value)
    val brassSource = PendingProcedure.CampaignPlanSource.Relic(player.player, brassId)
    val outridersSource = PendingProcedure.CampaignPlanSource.Adviser(player.player,
      DenizenId(outriders.id.value))
    val actor = player.copy(
      board = player.board.copy(faceUpSecrets = 1),
      advisers = Vector(DenizenState(DenizenId(outriders.id.value),
        Orientation.FaceUp, Tokens.empty)),
      relics = Vector(RelicState(brassId, Orientation.FaceUp, Tokens.empty)))
    val state = ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      players = ready.game.current.players.map(p =>
        if (p.player == player.player) actor else p))))
    val declared = rules.handle(Ready(state), CampaignCommand.Start(player.player,
      DecisionId("campaign-brass"), site, 2)).toOption.get
    val Ready(pendingState) = declared.state: @unchecked
    val pending = pendingState.game.current.pending.get.asInstanceOf[PendingProcedure.Campaign]
    assertEquals(CampaignRules.legalPlanChoices(catalog, pendingState, pending),
      Vector(outridersSource, brassSource).sortBy(_.stableKey))
    val actorView = new GameProjector(catalog).project("campaign-brass",
      LoadedGame(declared.state, 1), player.player)
    assertEquals(actorView.campaign.get.planChoices.map(_.kind).toSet,
      Set("adviser", "relic"))
    assertEquals(actorView.campaign.get.planChoices.find(_.kind == "relic").get.secretCost, 1)

    val dice = Vector.fill(6)(AttackDieFace.OneSword)
    val selected = rules.handle(declared.state, CampaignCommand.ChoosePlan(player.player,
      pending.decision, brassSource)).toOption.get
    val event = selected.events.head.asInstanceOf[CampaignPlanChosen]
    assertEquals(event.handlerId, "relic.brass-army")
    assertEquals(event.addedAttackDice, 4)
    val finished = rules.handle(selected.state, CampaignCommand.FinishPlans(player.player,
      pending.decision, dice)).toOption.get
    val Ready(after) = finished.state: @unchecked
    val afterActor = after.game.current.players.find(_.player == player.player).get
    assertEquals(afterActor.board.faceUpSecrets, 0)
    assertEquals(afterActor.relics.head.tokens, Tokens(0, 1))
    assertEquals(after.game.current.pending.get.asInstanceOf[PendingProcedure.Campaign].attack, 6)

    assert(rules.evolve(declared.state, event.copy(effects = Vector(
      PendingProcedure.CampaignPlanEffect.AddAttackDice(3)))).isLeft)
    val finishEvent = finished.events.last.asInstanceOf[CampaignPlansFinished]
    assert(rules.evolve(selected.state, finishEvent.copy(
      attackDice = dice.dropRight(1), attack = 5)).isLeft)
  }

  test("Brass Army is offered only held faceup, empty, and payable") {
    val (ready, player, site) = campaignReady
    val brass = catalog.relics.find(_.handlers.contains("relic.brass-army")).get
    val id = RelicId(brass.id.value)
    def choices(orientation: Orientation, tokens: Tokens, secrets: Int) = {
      val actor = player.copy(board = player.board.copy(faceUpSecrets = secrets),
        relics = Vector(RelicState(id, orientation, tokens)))
      val state = ready.copy(game = ready.game.copy(current = ready.game.current.copy(
        players = ready.game.current.players.map(p =>
          if (p.player == player.player) actor else p))))
      val declared = rules.handle(Ready(state), CampaignCommand.Start(player.player,
        DecisionId(s"campaign-brass-$secrets-${tokens.favor}-${tokens.secrets}"),
        site, 1)).toOption.get
      val Ready(after) = declared.state: @unchecked
      CampaignRules.legalPlanChoices(catalog, after,
        after.game.current.pending.get.asInstanceOf[PendingProcedure.Campaign])
    }
    assertEquals(choices(Orientation.FaceUp, Tokens.empty, 1).size, 1)
    assertEquals(choices(Orientation.FaceUp, Tokens.empty, 0), Vector.empty)
    assertEquals(choices(Orientation.FaceUp, Tokens(1, 0), 1), Vector.empty)
    assertEquals(choices(Orientation.FaceUp, Tokens(0, 1), 1), Vector.empty)
    assertEquals(choices(Orientation.FaceDown, Tokens.empty, 1), Vector.empty)
  }

  test("Outriders and Brass Army resolve once each in either chosen order") {
    val (ready, player, site) = campaignReady
    val brassId = RelicId(catalog.relics.find(
      _.handlers.contains("relic.brass-army")).get.id.value)
    val outridersId = DenizenId(catalog.denizens.find(
      _.handlers.contains("denizen.outriders")).get.id.value)
    val outriders = PendingProcedure.CampaignPlanSource.Adviser(
      player.player, outridersId)
    val brass = PendingProcedure.CampaignPlanSource.Relic(player.player, brassId)
    val actor = player.copy(board = player.board.copy(faceUpSecrets = 1),
      advisers = Vector(DenizenState(outridersId, Orientation.FaceDown, Tokens.empty)),
      relics = Vector(RelicState(brassId, Orientation.FaceUp, Tokens.empty)))
    val state = ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      players = ready.game.current.players.map(p =>
        if (p.player == player.player) actor else p))))

    Vector(Vector(outriders, brass), Vector(brass, outriders)).zipWithIndex.foreach {
      case (order, index) =>
        val decision = DecisionId(s"campaign-combined-$index")
        val started = rules.handle(Ready(state), CampaignCommand.Start(
          player.player, decision, site, 2)).toOption.get
        val selected = order.foldLeft(started) { (transition, source) =>
          val next = rules.handle(transition.state, CampaignCommand.ChoosePlan(
            player.player, decision, source)).toOption.get
          assert(next.events.forall(_.isInstanceOf[CampaignPlanChosen]))
          next
        }
        val Ready(selectedState) = selected.state: @unchecked
        val pending = selectedState.game.current.pending.get
          .asInstanceOf[PendingProcedure.Campaign]
        assertEquals(pending.plans.map(_.source), order)
        assertEquals(pending.attackDice, Vector.empty)
        assertEquals(CampaignRules.legalPlanChoices(catalog, selectedState, pending),
          Vector.empty)
        assert(rules.handle(selected.state, CampaignCommand.ChoosePlan(
          player.player, decision, order.head)).isLeft)
        val view = new GameProjector(catalog).project("campaign-combined",
          LoadedGame(selected.state, 1), player.player).campaign.get
        assertEquals(view.selectedPlans.map(_.sourceKey),
          order.map(source => Some(source.stableKey)))
        val dice = Vector.fill(6)(AttackDieFace.TwoSwordsSkull)
        val finished = rules.handle(selected.state, CampaignCommand.FinishPlans(
          player.player, decision, dice)).toOption.get
        val attackerEvent = finished.events.head.asInstanceOf[CampaignPlansFinished]
        val rollEvent = finished.events.last.asInstanceOf[CampaignPlansFinished]
        assertEquals(attackerEvent.orderedSources, order)
        assertEquals(attackerEvent.addedAttackDice, 4)
        assertEquals(attackerEvent.ignoreAttackSkulls, true)
        assertEquals(rollEvent.attack, 12)
        assertEquals(rollEvent.skullLosses, 0)
        assert(rules.evolve(selected.state,
          attackerEvent.copy(orderedSources = order.reverse)).isLeft)
    }
  }

  test("facedown Vow of Peace has no active pre-Campaign restriction") {
    val (ready, player, site) = campaignReady
    val vow = catalog.denizens.find(_.handlers.contains("denizen.vow-of-peace")).get
    val state = ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      players = ready.game.current.players.map(p => if (p.player != player.player) p else
        p.copy(advisers = Vector(DenizenState(DenizenId(vow.id.value),
          Orientation.FaceDown, Tokens.empty)))))))
    assert(CampaignRules.validateStart(catalog, state, player.player, site, 1).isRight)
  }

  test("unimplemented Bag of Siegeworks does not block base Campaign") {
    val (ready, player, site) = campaignReady
    val bag = catalog.relics.find(_.handlers.contains("relic.bag-of-siegeworks")).get
    val state = ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      players = ready.game.current.players.map(p => if (p.player != player.player) p else
        p.copy(relics = Vector(RelicState(RelicId(bag.id.value),
          Orientation.FaceUp, Tokens.empty)))))))
    assert(CampaignRules.validateStart(catalog, state, player.player, site, 1).isRight)
  }

  test("audited exact-ID bandit classifications remain conservative") {
    import CampaignRules.HandlerSupport
    val irrelevant = Set(
      "denizen.bear-traps", "denizen.extra-provisions",
      "denizen.gleaming-armor", "denizen.herald", "denizen.insect-swarm",
      "denizen.military-parade", "denizen.pledge-of-defense",
      "denizen.relic-hunter", "denizen.sealing-ward", "denizen.specialist",
      "denizen.true-names", "denizen.wrestlers",
      "relic.bandit-standard", "relic.fearsome-shield", "relic.sticky-fire",
      "relic.obsidian-cage", "relic.the-grand-scepter")
    irrelevant.foreach { id =>
      assertEquals(CampaignRules.classify(id, catalog),
        HandlerSupport.IrrelevantToBanditConquest, id)
    }
    Set("denizen.peace-envoy", "relic.bag-of-siegeworks",
      "relic.keeping-banner").foreach { id =>
      assert(CampaignRules.classify(id, catalog)
        .isInstanceOf[HandlerSupport.Blocked], id)
    }
  }

  test("bandit-irrelevant defender power does not block bounded Conquest") {
    val (ready, player, site) = campaignReady
    val honors = catalog.denizens.find(_.handlers.contains("denizen.extra-provisions")).get
    val state = ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      players = ready.game.current.players.map(p => if (p.player != player.player) p else
        p.copy(advisers = Vector(DenizenState(DenizenId(honors.id.value),
          Orientation.FaceUp, Tokens.empty)))))))
    assert(CampaignRules.validateStart(catalog, state, player.player, site, 1).isRight)
  }

  test("bandits deterministically use applicable cost-free defender plans") {
    val (base, player, _) = campaignReady
    val site = base.game.current.map.inPlay.find(id =>
      base.game.current.map.regionOf(id).contains(Region.Cradle) &&
        catalog.sites.find(_.id == id).exists(_.handlers.forall(h =>
          !h.endsWith(".mountain") && !h.endsWith(".plains")))).get
    val watchdog = catalog.denizens.find(_.handlers.contains(
      "denizen.watchdog")).get
    val target = base.game.current.map.sites(site).copy(
      forces = SiteForces.Occupied(ForceKind.Bandit, 1),
      denizens = Vector(DenizenState(DenizenId(watchdog.id.value),
        Orientation.FaceUp, Tokens.empty)))
    val state = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p => if (p.player != player.player) p
        else p.copy(pawnSite = Some(site))),
      map = base.game.current.map.copy(sites =
        base.game.current.map.sites.updated(site, target)))))
    val decision = DecisionId("campaign-bandit-watchdog")
    val started = rules.handle(Ready(state), CampaignCommand.Start(
      player.player, decision, site, 1)).toOption.get
    val finished = rules.handle(started.state, CampaignCommand.FinishPlans(
      player.player, decision, Vector(AttackDieFace.HollowSword))).toOption.get
    val defenderEvent = finished.events.last.asInstanceOf[CampaignPlansFinished]
    assertEquals(defenderEvent.side, PendingProcedure.CampaignPlanSide.Defender)
    assertEquals(defenderEvent.orderedSources,
      Vector(PendingProcedure.CampaignPlanSource.SiteCard(site,
        DenizenId(watchdog.id.value))))
    assertEquals(defenderEvent.effects,
      Vector(PendingProcedure.CampaignPlanEffect.AddDefenseDice(1)))
    val Ready(after) = finished.state: @unchecked
    val pending = after.game.current.pending.get.asInstanceOf[PendingProcedure.Campaign]
    assertEquals(CampaignRules.defensePlanDice(pending), 1)
    val afterAttackerEvent = rules.evolve(started.state, finished.events.head).toOption.get
    assert(rules.evolve(afterAttackerEvent, defenderEvent.copy(
      effects = Vector.empty)).isLeft)
  }

  test("unknown relevant handler rejects with stable handler and source identity") {
    val (ready, player, site) = campaignReady
    val original = catalog.denizens.head
    val changed = catalog.copy(denizens = catalog.denizens.updated(0,
      original.copy(powers = Vector(CatalogPower("denizen.future-plan",
        persistent = false, "+2 [attack-die]")))))
    val state = ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      players = ready.game.current.players.map(p => if (p.player != player.player) p else
        p.copy(advisers = Vector(DenizenState(DenizenId(original.id.value),
          Orientation.FaceDown, Tokens.empty)))))))
    val error = CampaignRules.validateStart(changed, state, player.player, site, 1)
      .left.toOption.get.toString
    assert(error.contains("UnsupportedRuleCatalog") ||
      error.contains("handler inventory is not reviewed"))
  }

  test("unimplemented remote optional denizen does not block Campaign") {
    val (ready, player, target) = campaignReady
    val remote = ready.game.current.map.inPlay.find(_ != target).get
    val relevant = catalog.denizens.find(_.rulesText.toLowerCase.contains("campaign")).get
    val remoteState = ready.game.current.map.sites(remote).copy(
      forces = SiteForces.Occupied(ForceKind.Exile(player.lineage), 1),
      denizens = Vector(DenizenState(DenizenId(relevant.id.value),
        Orientation.FaceDown, Tokens.empty)))
    val withPower = ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      map = ready.game.current.map.copy(sites =
        ready.game.current.map.sites.updated(remote, remoteState)))))
    assert(CampaignRules.validateStart(catalog, withPower, player.player, target, 1).isRight)
  }

  test("corrupt remote site rule mapping rejects Campaign access scan") {
    val (ready, player, target) = campaignReady
    val remote = ready.game.current.map.inPlay.find(_ != target).get
    val corrupt = ready.game.current.map.sites(remote).copy(
      forces = SiteForces.Occupied(ForceKind.Exile(LineageId("absent")), 1))
    val state = ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      map = ready.game.current.map.copy(sites =
        ready.game.current.map.sites.updated(remote, corrupt)))))
    val error = CampaignRules.validateStart(catalog, state, player.player,
      target, 1).left.toOption.get
    assert(error.isInstanceOf[UnsupportedCampaignState])
    assert(error.toString.contains("UnknownLineage"))
  }

  test("zero placement refills bandits before the action-boundary title check") {
    val (ready, player, site) = campaignReady
    val id = DecisionId("campaign-refill")
    val started = startAndChoose(ready, player, site, id, 2,
      Vector.fill(2)(AttackDieFace.TwoSwordsSkull))
    val defenseDice = Vector.fill(catalog.sites.find(_.id == site).get.defense)(
      DefenseDieFace.Blank)
    val won = rules.handle(started.state, CampaignCommand.Sacrifice(
      player.player, id, 0, defenseDice)).toOption.get
    val completed = rules.handle(won.state,
      CampaignCommand.Place(player.player, id,
        Vector(CampaignForceAllocation(site, 0)))).toOption.get
    assert(completed.events.exists(_.isInstanceOf[BanditsRefilled]))
    val Ready(after) = completed.state: @unchecked
    assertEquals(after.game.current.map.sites(site).forces,
      SiteForces.Occupied(ForceKind.Bandit,
        catalog.sites.find(_.id == site).get.capacity))
    val refill = completed.events.collectFirst { case e: BanditsRefilled => e }.get
    assert(rules.evolve(won.state, refill.copy(sites = Vector(site -> 99))).isLeft)
  }

  test("multi-site conquest removes every losing force and allocates atomically") {
    val (ready, player, pawn) = campaignReady
    val other = CampaignRules.legalTargets(catalog, ready, player.player)(1)
    val targets = Vector(pawn, other)
    val state = ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      map = ready.game.current.map.copy(sites = targets.foldLeft(
        ready.game.current.map.sites)((sites, site) => sites.updated(site,
          sites(site).copy(forces = SiteForces.Occupied(ForceKind.Bandit, 1))))))))
    val id = DecisionId("campaign-multi-resolution")
    val started = rules.handle(Ready(state), CampaignCommand.Start(player.player,
      id, targets, 3)).toOption.get
    val planned = rules.handle(started.state, CampaignCommand.FinishPlans(
      player.player, id, Vector.fill(3)(AttackDieFace.OneSword))).toOption.get
    val defenseDice = targets.flatMap(site => Vector.fill(
      catalog.sites.find(_.id == site).get.defense)(DefenseDieFace.Blank))
    val won = rules.handle(planned.state, CampaignCommand.Sacrifice(player.player,
      id, 0, defenseDice)).toOption.get
    val zero = rules.handle(won.state, CampaignCommand.Place(player.player, id,
      targets.map(CampaignForceAllocation(_, 0)))).toOption.get
    assertEquals(zero.events.head.asInstanceOf[CampaignConquered]
      .allocations.map(_.count), Vector(0, 0))
    val allocations = Vector(CampaignForceAllocation(pawn, 1),
      CampaignForceAllocation(other, 1))
    val completed = rules.handle(won.state, CampaignCommand.Place(player.player,
      id, allocations)).toOption.get
    val conquered = completed.events.head.asInstanceOf[CampaignConquered]
    assertEquals(conquered.allocations, allocations)
    assertEquals(conquered.losingForces.map(_.site), targets)
    assertEquals(conquered.losingForces.collect {
      case CampaignLosingForceEffect.Remove(_, ForceKind.Bandit, count) => count
    }.sum, 2)
    val atomic = rules.evolve(won.state, conquered).toOption.get
      .asInstanceOf[Ready].value
    assertEquals(atomic.game.current.map.sites(pawn).forces,
      SiteForces.Occupied(ForceKind.Exile(player.lineage), 1))
    assertEquals(atomic.game.current.map.sites(other).forces,
      SiteForces.Occupied(ForceKind.Exile(player.lineage), 1))
    val boardAfter = atomic.game.current.players.find(
      _.player == player.player).get.board.warbands
    assertEquals(boardAfter + allocations.map(_.count).sum,
      player.board.warbands)
    assertEquals(boardAfter, 1)
  }

  test("multi-site placement validates canonical complete finite allocations") {
    val (ready, player, pawn) = campaignReady
    val other = CampaignRules.legalTargets(catalog, ready, player.player)(1)
    val targets = Vector(pawn, other)
    val state = ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      map = ready.game.current.map.copy(sites = targets.foldLeft(
        ready.game.current.map.sites)((sites, site) => sites.updated(site,
          sites(site).copy(forces = SiteForces.Occupied(ForceKind.Bandit, 1))))))))
    val id = DecisionId("campaign-allocation-validation")
    val started = rules.handle(Ready(state), CampaignCommand.Start(player.player,
      id, targets, 3)).toOption.get
    val planned = rules.handle(started.state, CampaignCommand.FinishPlans(
      player.player, id, Vector.fill(3)(AttackDieFace.TwoSwordsSkull))).toOption.get
    val defenseDice = targets.flatMap(site => Vector.fill(
      catalog.sites.find(_.id == site).get.defense)(DefenseDieFace.Blank))
    val won = rules.handle(planned.state, CampaignCommand.Sacrifice(player.player,
      id, 0, defenseDice)).toOption.get
    def rejects(values: Vector[CampaignForceAllocation]): Unit =
      assert(rules.handle(won.state, CampaignCommand.Place(player.player, id,
        values)).left.toOption.get.isInstanceOf[CampaignOutcomeMismatch])
    rejects(Vector(CampaignForceAllocation(pawn, 0)))
    rejects(Vector(CampaignForceAllocation(other, 0),
      CampaignForceAllocation(pawn, 0)))
    rejects(Vector(CampaignForceAllocation(pawn, 0),
      CampaignForceAllocation(pawn, 0)))
    rejects(Vector(CampaignForceAllocation(pawn, 0),
      CampaignForceAllocation(SiteId("foreign"), 0)))
    rejects(Vector(CampaignForceAllocation(pawn, 1),
      CampaignForceAllocation(other, 1)))
    val zero = rules.handle(won.state, CampaignCommand.Place(player.player, id,
      targets.map(CampaignForceAllocation(_, 0)))).toOption.get
    assertEquals(zero.events.count(_.isInstanceOf[CampaignConquered]), 1)
    assertEquals(zero.events.count(_.isInstanceOf[BanditsRefilled]), 1)
  }

  test("typed losing-force policy controls evolution and blocks occupied placement") {
    val (ready, player, site) = campaignReady
    val id = DecisionId("campaign-preserved-loser")
    val started = startAndChoose(ready, player, site, id, 2,
      Vector.fill(2)(AttackDieFace.TwoSwordsSkull))
    val defenseDice = Vector.fill(catalog.sites.find(_.id == site).get.defense)(
      DefenseDieFace.Blank)
    val won = rules.handle(started.state, CampaignCommand.Sacrifice(
      player.player, id, 0, defenseDice)).toOption.get
    val preserve = new CampaignLosingForceResolver {
      val id = "campaign.loss.test-preserve"
      def resolve(state: ReadyGame, campaign: PendingProcedure.Campaign) =
        Right(campaign.targetSites.map(target => {
          val SiteForces.Occupied(force, count) =
            state.game.current.map.sites(target).forces: @unchecked
          CampaignLosingForceEffect.Preserve(target, force, count)
        }))
    }
    val registry = CampaignLosingForceRegistry(preserve, Vector(preserve))
    val alternateRules = new OathRules(catalog, registry)
    val zero = alternateRules.handle(won.state, CampaignCommand.Place(
      player.player, id, Vector(CampaignForceAllocation(site, 0))))
      .toOption.get
    val event = zero.events.head.asInstanceOf[CampaignConquered]
    assertEquals(event.losingForcePolicyId, preserve.id)
    val Ready(after) = zero.state: @unchecked
    assertEquals(after.game.current.map.sites(site).forces,
      ready.game.current.map.sites(site).forces)
    assert(alternateRules.handle(won.state, CampaignCommand.Place(
      player.player, id, Vector(CampaignForceAllocation(site, 1))))
      .left.toOption.get.isInstanceOf[CampaignOutcomeMismatch])
  }

  test("alternate attacker-loss policy can preserve the complete committed force") {
    val (ready, player, site) = campaignReady
    val id = DecisionId("campaign-preserve-attacker")
    val started = startAndChoose(ready, player, site, id, 2,
      Vector.fill(2)(AttackDieFace.HollowSword))
    val defenseDice = Vector.fill(catalog.sites.find(_.id == site).get.defense)(
      DefenseDieFace.TwoShields)
    val preserve = new CampaignLosingForceResolver {
      val id = "campaign.loss.test-preserve-attacker"
      def resolve(state: ReadyGame, campaign: PendingProcedure.Campaign) =
        CampaignLosingForceResolver.default.resolve(state, campaign)
      override def resolveAttackerDefeat(state: ReadyGame,
          campaign: PendingProcedure.Campaign, surviving: Int) = {
        val owner = state.game.current.players.find(
          _.player == campaign.actor).get
        CampaignRules.campaignOrigin(state, campaign).map(site => Vector(
          CampaignLosingForceEffect.PreserveCommitted(site, campaign.actor,
            ForceKind.Exile(owner.lineage), surviving)))
      }
    }
    val alternate = new OathRules(catalog,
      CampaignLosingForceRegistry(preserve, Vector(preserve)))
    val defeated = alternate.handle(started.state, CampaignCommand.Sacrifice(
      player.player, id, 0, defenseDice)).toOption.get
    val event = defeated.events.head.asInstanceOf[CampaignSacrificed]
    assertEquals(event.losingForcePolicyId, Some(preserve.id))
    val Ready(after) = defeated.state: @unchecked
    assertEquals(after.game.current.players.find(_.player == player.player).get
      .board.warbands, player.board.warbands)
    assertEquals(alternate.evolve(started.state, event), Right(defeated.state))
    assert(rules.evolve(started.state, event).isLeft)
  }

  test("player-defender Conquest aggregates force and resolves title plans") {
    val (base, attacker, pawn) = campaignReady
    val defender = base.game.current.players.find(_.player != attacker.player).get
    val other = CampaignRules.legalTargets(catalog, base, attacker.player)(1)
    val sites = Vector(pawn, other)
    val targetStates = sites.zip(Vector(1, 2)).foldLeft(
      base.game.current.map.sites) { case (all, (site, count)) =>
      all.updated(site, all(site).copy(forces = SiteForces.Occupied(
        ForceKind.Exile(defender.lineage), count), denizens = Vector.empty))
    }
    val state = base.copy(game = base.game.copy(current = base.game.current.copy(
      title = OathkeeperState(None, TitleSide.Oathkeeper),
      players = base.game.current.players.map {
        case p if p.player == attacker.player =>
          p.copy(board = p.board.copy(warbands = 4))
        case p if p.player == defender.player =>
          p.copy(board = p.board.copy(warbands = 10), advisers = Vector.empty,
            relics = Vector.empty)
        case p => p
      }, map = base.game.current.map.copy(sites = targetStates))))
    assertEquals(CampaignRules.validateStart(catalog, state, attacker.player,
      sites, 4), Right(CampaignDefender.Player(defender.player)))
    assertEquals(CampaignRules.defenderForce(state, sites), 3)
    val started = rules.handle(Ready(state), CampaignCommand.Start(attacker.player,
      DecisionId("campaign-player"), sites, 4)).toOption.get
    val Ready(afterStart) = started.state: @unchecked
    val pending = afterStart.game.current.pending.get
      .asInstanceOf[PendingProcedure.Campaign]
    assertEquals(pending.defender, CampaignDefender.Player(defender.player))
    val projected = new GameProjector(catalog).project("player-defender",
      LoadedGame(started.state, 4), attacker.player).campaign.get
    assertEquals(projected.defenderKind -> projected.defenderPlayerId,
      "player" -> Some(defender.player.value))
    assertEquals(projected.defenderForce -> projected.defenseDiceCount,
      3 -> (sites.flatMap(CampaignRules.siteDefinition(catalog, _))
        .map(_.defense).sum))
    assertEquals(new GameProjector(catalog).project("player-defender",
      LoadedGame(started.state, 4), defender.player).campaign, None)
    Vector(TitleSide.Oathkeeper -> 1, TitleSide.Usurper -> 2).foreach { case (side, bonus) =>
      val titled = state.copy(game = state.game.copy(current =
        state.game.current.copy(title = OathkeeperState(
          Some(defender.player), side))))
      assertEquals(CampaignRules.validateStart(catalog, titled,
        attacker.player, sites, 4), Right(CampaignDefender.Player(defender.player)))
      assert(CampaignRules.legalTargets(catalog, titled, attacker.player).nonEmpty)
      assert(new GameProjector(catalog).project("titled-defender",
        LoadedGame(Ready(titled), 4), attacker.player).boardTargetActions
        .exists(_.actionKind == "campaign-conquest"))
      val titleStarted = rules.handle(Ready(titled), CampaignCommand.Start(
        attacker.player, DecisionId(s"title-$side"), sites, 4)).toOption.get
      val attackerDone = rules.handle(titleStarted.state, CampaignCommand.FinishPlans(
        attacker.player, DecisionId(s"title-$side"), Vector.empty)).toOption.get
      val Ready(awaitingDefender) = attackerDone.state: @unchecked
      assertEquals(awaitingDefender.game.current.pending.get
        .asInstanceOf[PendingProcedure.Campaign].attackDice, Vector.empty)
      assert(rules.handle(attackerDone.state, CampaignCommand.FinishPlans(
        attacker.player, DecisionId(s"title-$side"), Vector.empty)).isLeft)
      val defenderView = new GameProjector(catalog).project("titled-defender",
        LoadedGame(attackerDone.state, 5), defender.player).campaign.get
      assertEquals(defenderView.decisionOwnerPlayerId, Some(defender.player.value))
      assertEquals(defenderView.planChoices.map(_.mechanicalResult),
        Vector(s"Add $bonus defense ${if (bonus == 1) "die" else "dice"}"))
      val titleSource = PendingProcedure.CampaignPlanSource.Title(defender.player)
      assert(rules.handle(attackerDone.state, CampaignCommand.ChoosePlan(
        attacker.player, DecisionId(s"title-$side"), titleSource)).isLeft)
      assert(rules.handle(attackerDone.state, CampaignCommand.ChoosePlan(
        defender.player, DecisionId("stale-title"), titleSource)).isLeft)
      val chosen = rules.handle(attackerDone.state, CampaignCommand.ChoosePlan(
        defender.player, DecisionId(s"title-$side"), titleSource)).toOption.get
      val titleEvent = chosen.events.head.asInstanceOf[CampaignPlanChosen]
      assert(rules.evolve(attackerDone.state, titleEvent.copy(effects = Vector(
        PendingProcedure.CampaignPlanEffect.AddDefenseDice(bonus + 1)))).isLeft)
      val resolved = rules.handle(chosen.state, CampaignCommand.FinishPlans(
        defender.player, DecisionId(s"title-$side"),
        Vector.fill(4)(AttackDieFace.OneSword))).toOption.get
      val Ready(afterTitle) = resolved.state: @unchecked
      assertEquals(CampaignRules.defensePlanDice(afterTitle.game.current.pending.get
        .asInstanceOf[PendingProcedure.Campaign]), bonus)
      assertEquals(new GameProjector(catalog).project("titled-defender",
        LoadedGame(resolved.state, 6), defender.player).campaign, None)
    }

    val outriders = catalog.denizens.find(
      _.handlers.contains("denizen.outriders")).get
    val vow = catalog.denizens.find(
      _.handlers.contains("denizen.vow-of-peace")).get
    val brass = catalog.relics.find(
      _.handlers.contains("relic.brass-army")).get
    val attackerOnly = state.copy(game = state.game.copy(current =
      state.game.current.copy(players = state.game.current.players.map {
        case p if p.player == defender.player => p.copy(
          advisers = Vector(outriders, vow).map(card => DenizenState(
            DenizenId(card.id.value), Orientation.FaceUp, Tokens.empty)),
          relics = Vector(RelicState(RelicId(brass.id.value),
            Orientation.FaceUp, Tokens.empty)))
        case p => p
      })))
    assertEquals(CampaignRules.validateStart(catalog, attackerOnly,
      attacker.player, sites, 4), Right(CampaignDefender.Player(defender.player)))

    val original = catalog.denizens.head
    val changedCatalog = catalog.copy(denizens = catalog.denizens.updated(0,
      original.copy(powers = Vector(CatalogPower("denizen.future-defender-plan",
        persistent = false, "+2 [defense-die]")))))
    val unsupported = state.copy(game = state.game.copy(current =
      state.game.current.copy(players = state.game.current.players.map {
        case p if p.player == defender.player => p.copy(advisers = Vector(
          DenizenState(DenizenId(original.id.value), Orientation.FaceUp,
            Tokens.empty)))
        case p => p
      })))
    assert(CampaignRules.validateStart(changedCatalog, unsupported,
      attacker.player, sites, 4).isLeft)
    assertEquals(CampaignRules.legalTargets(changedCatalog, unsupported,
      attacker.player), Vector.empty)

    val mixed = state.copy(game = state.game.copy(current =
      state.game.current.copy(map = state.game.current.map.copy(sites =
        state.game.current.map.sites.updated(other,
          state.game.current.map.sites(other).copy(forces =
            SiteForces.Occupied(ForceKind.Bandit, 2)))))))
    assert(CampaignRules.validateStart(catalog, mixed, attacker.player,
      sites, 4).isLeft)
  }

  test("player-defender victory kills half returns survivors and places atomically") {
    val (base, attacker, pawn) = campaignReady
    val defender = base.game.current.players.find(_.player != attacker.player).get
    val other = CampaignRules.legalTargets(catalog, base, attacker.player)(1)
    val sites = Vector(pawn, other)
    val targetStates = sites.zip(Vector(1, 2)).foldLeft(
      base.game.current.map.sites) { case (all, (site, count)) =>
      all.updated(site, all(site).copy(forces = SiteForces.Occupied(
        ForceKind.Exile(defender.lineage), count), denizens = Vector.empty))
    }
    val state = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map {
        case p if p.player == attacker.player =>
          p.copy(board = p.board.copy(warbands = 4))
        case p if p.player == defender.player =>
          p.copy(board = p.board.copy(warbands = 10), advisers = Vector.empty,
            relics = Vector.empty)
        case p => p
      }, map = base.game.current.map.copy(sites = targetStates))))
    val id = DecisionId("campaign-player-resolution")
    val lossId = DecisionId("campaign-player-attacker-loss")
    val lossStarted = rules.handle(Ready(state), CampaignCommand.Start(
      attacker.player, lossId, sites, 0)).toOption.get
    val lossPlanned = rules.handle(lossStarted.state, CampaignCommand.FinishPlans(
      attacker.player, lossId, Vector.empty)).toOption.get
    val lossRolled = rules.handle(lossPlanned.state, CampaignCommand.FinishPlans(
      defender.player, lossId, Vector.empty)).toOption.get
    val lossDice = sites.flatMap(site => Vector.fill(
      catalog.sites.find(_.id == site).get.defense)(DefenseDieFace.Blank))
    val lost = rules.handle(lossRolled.state, CampaignCommand.Sacrifice(
      attacker.player, lossId, 0, lossDice)).toOption.get
    val Ready(afterLoss) = lost.state: @unchecked
    assertEquals(sites.map(afterLoss.game.current.map.sites(_).forces),
      sites.map(state.game.current.map.sites(_).forces))
    assertEquals(afterLoss.game.current.players.find(_.player == defender.player)
      .get.board.warbands, 10)

    val declared = rules.handle(Ready(state), CampaignCommand.Start(
      attacker.player, id, sites, 4)).toOption.get
    val planned = rules.handle(declared.state, CampaignCommand.FinishPlans(
      attacker.player, id, Vector.empty)).toOption.get
    val rolled = rules.handle(planned.state, CampaignCommand.FinishPlans(
      defender.player, id, Vector.fill(4)(AttackDieFace.OneSword))).toOption.get
    val defenseDice = sites.flatMap(site => Vector.fill(
      catalog.sites.find(_.id == site).get.defense)(DefenseDieFace.Blank))
    val won = rules.handle(rolled.state, CampaignCommand.Sacrifice(
      attacker.player, id, 0, defenseDice)).toOption.get
    val completed = rules.handle(won.state, CampaignCommand.Place(attacker.player,
      id, Vector(CampaignForceAllocation(pawn, 1),
        CampaignForceAllocation(other, 1)))).toOption.get
    val conquered = completed.events.head.asInstanceOf[CampaignConquered]
    assertEquals(conquered.losingForces.collect {
      case CampaignLosingForceEffect.ReturnToBoard(_, player, _, count) =>
        player -> count
    }, Vector(defender.player -> 2))
    val Ready(after) = completed.state: @unchecked
    assertEquals(after.game.current.players.find(_.player == defender.player).get
      .board.warbands, 12)
    assertEquals(sites.map(after.game.current.map.sites(_).forces), Vector(
      SiteForces.Occupied(ForceKind.Exile(attacker.lineage), 1),
      SiteForces.Occupied(ForceKind.Exile(attacker.lineage), 1)))
    assertEquals(after.game.current.players.find(_.player == attacker.player).get
      .board.warbands, 2)
    assertEquals(rules.evolve(won.state, conquered).isRight, true)
    val tampered = conquered.copy(losingForces = conquered.losingForces.map {
      case value: CampaignLosingForceEffect.ReturnToBoard =>
        value.copy(count = value.count + 1)
      case other => other
    })
    assert(rules.evolve(won.state, tampered).left.toOption.get
      .isInstanceOf[CampaignOutcomeMismatch])
  }
}
