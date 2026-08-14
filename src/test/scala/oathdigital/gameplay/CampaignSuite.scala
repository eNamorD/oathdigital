package oathdigital.gameplay

import oathdigital.application.{BoardTargetRefProjection, GameProjector, LoadedGame}
import oathdigital.gameplay.actions.{CampaignCommand, CampaignRules}
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.FirstGameSetupFixture._
import oathdigital.setup.OathEvent._
import oathdigital.setup.OathState.Ready
import oathdigital.setup.OathViolation._

class CampaignSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)

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

  test("attack faces implement hollow pairs and skull swords") {
    assertEquals(AttackDieFace.score(Vector(AttackDieFace.HollowSword,
      AttackDieFace.HollowSword, AttackDieFace.HollowSword,
      AttackDieFace.OneSword, AttackDieFace.TwoSwordsSkull)), 4)
    assertEquals(AttackDieFace.skulls(Vector(AttackDieFace.TwoSwordsSkull,
      AttackDieFace.OneSword)), 1)
  }

  test("legality and projection agree on mandatory bandit origin") {
    val (ready, player, site) = campaignReady
    assertEquals(CampaignRules.legalTargets(catalog, ready, player.player), Vector(site))
    val projection = new GameProjector(catalog).project("campaign",
      LoadedGame(Ready(ready), 1), player.player)
    val action = projection.boardTargetActions.find(_.actionKind == "campaign-conquest").get
    assertEquals(action.minimum -> action.maximum, 1 -> 1)
    assertEquals(action.candidates.map(_.target),
      Vector(BoardTargetRefProjection.Site(site.value)))
    assertEquals(action.candidates.head.details,
      Vector("2 Supply", s"Commit all ${player.board.warbands} board warbands"))
  }

  test("staged conquest records cost dice sacrifice and explicit placement") {
    val (ready, player, site) = campaignReady
    val id = DecisionId("campaign-test")
    val started = rules.handle(Ready(ready), CampaignCommand.Start(player.player,
      id, site, 3, Vector(AttackDieFace.OneSword, AttackDieFace.OneSword,
        AttackDieFace.TwoSwordsSkull))).toOption.get
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
      CampaignCommand.Place(player.player, id, 1)).toOption.get
    val Ready(after) = conquered.state: @unchecked
    assertEquals(after.game.current.map.sites(site).forces,
      SiteForces.Occupied(ForceKind.Exile(player.lineage), 1))
    assertEquals(after.game.current.pending, None)
  }

  test("defeat kills half surviving force and replay rejects tampering") {
    val (ready, player, site) = campaignReady
    val id = DecisionId("campaign-loss")
    assert(rules.evolve(Ready(ready), CampaignStarted(player.player, id, site,
      1, 2, Vector(AttackDieFace.OneSword))).left.toOption.get
      .isInstanceOf[CampaignOutcomeMismatch])
    val started = rules.handle(Ready(ready), CampaignCommand.Start(player.player,
      id, site, 2, Vector.fill(2)(AttackDieFace.HollowSword))).toOption.get
    val defenseDice = Vector.fill(catalog.sites.find(_.id == site).get.defense)(
      DefenseDieFace.TwoShields)
    val recorded = CampaignSacrificed(player.player, id, 0, defenseDice,
      attack = 99, defense = 99, skullLosses = 0, victorious = false)
    assert(rules.evolve(started.state, recorded).left.toOption.get
      .isInstanceOf[CampaignOutcomeMismatch])
    val defeated = rules.handle(started.state, CampaignCommand.Sacrifice(
      player.player, id, 0, defenseDice)).toOption.get
    val Ready(after) = defeated.state: @unchecked
    assertEquals(after.game.current.pending, None)
    assertEquals(after.game.current.players.find(_.player == player.player).get
      .board.warbands, player.board.warbands - 1)
  }

  test("relevant facedown adviser fails explicitly") {
    val (ready, player, site) = campaignReady
    val relevant = catalog.denizens.find(_.rulesText.toLowerCase.contains("campaign")).get
    val withPower = ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      players = ready.game.current.players.map(p => if (p.player != player.player) p else
        p.copy(advisers = Vector(DenizenState(DenizenId(relevant.id.value),
          Orientation.FaceDown, Tokens.empty)))))))
    assert(CampaignRules.validateStart(catalog, withPower, player.player, site, 1)
      .left.toOption.get.isInstanceOf[UnsupportedCampaignState])
  }

  test("relevant denizen at another actor-ruled site fails explicitly") {
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
    assert(CampaignRules.validateStart(catalog, withPower, player.player, target, 1)
      .left.toOption.get.isInstanceOf[UnsupportedCampaignState])
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
    val started = rules.handle(Ready(ready), CampaignCommand.Start(player.player,
      id, site, 2, Vector.fill(2)(AttackDieFace.TwoSwordsSkull))).toOption.get
    val defenseDice = Vector.fill(catalog.sites.find(_.id == site).get.defense)(
      DefenseDieFace.Blank)
    val won = rules.handle(started.state, CampaignCommand.Sacrifice(
      player.player, id, 0, defenseDice)).toOption.get
    val completed = rules.handle(won.state,
      CampaignCommand.Place(player.player, id, 0)).toOption.get
    assert(completed.events.exists(_.isInstanceOf[BanditsRefilled]))
    val Ready(after) = completed.state: @unchecked
    assertEquals(after.game.current.map.sites(site).forces,
      SiteForces.Occupied(ForceKind.Bandit,
        catalog.sites.find(_.id == site).get.capacity))
    val refill = completed.events.collectFirst { case e: BanditsRefilled => e }.get
    assert(rules.evolve(won.state, refill.copy(sites = Vector(site -> 99))).isLeft)
  }
}
