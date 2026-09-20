package oathdigital.application

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer._
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.{CampaignResultProjection, GameProjection}

class CampaignResultProjectionSuite extends munit.FunSuite {
  private val projector = new GameProjector(catalog)
  private def view(state: OathState, viewer: PlayerId): GameProjection =
    projector.project("campaign", LoadedGame(state, 40), viewer)

  private def printed(b: Board) = catalog.sites.find(_.id == b.origin).get.defense

  /** A finished Conquest victory: force 4 of swords against 2 bandits. */
  private def fought(b: Board): OathState = {
    val game = rules(dice(Vector.fill(4)(AttackDieFace.OneSword),
      Vector.fill(printed(b))(DefenseDieFace.Blank)))
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor).toOption.get
    val forced = game.resolveWalker(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(4)).toOption.get
    val sacrificed = game.resolveWalker(forced.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(1)).toOption.get
    game.resolveWalker(sacrificed.state, b.actor, CampaignIds.placement,
      ChooseAmountAnswer(0)).toOption.get.state
  }

  test("no Campaign has been fought, so there is no result to show") {
    val b = board()
    assertEquals(view(Ready(b.ready), b.actor).lastCampaign, None)
  }

  test("every viewer, including the public, sees the same result") {
    val b = board()
    val state = fought(b)
    val expected = CampaignResultProjection(b.actor.value, "conquest", None,
      Vector(b.origin.value), Vector.empty, force = 4,
      attackDice = Vector.fill(4)("one-sword"), attackScore = 4, skullLosses = 0,
      sacrificed = 1, defenseDice = Vector.fill(printed(b))("blank"),
      defenseScore = 2, victorious = true)
    Vector(b.actor, b.other).foreach(viewer =>
      assertEquals(view(state, viewer).lastCampaign, Some(expected), viewer.value))
    assertEquals(projector.projectPublic("campaign", LoadedGame(state, 40))
      .lastCampaign, Some(expected))
  }

  test("a Raid result names its targets and its defender") {
    val (b, relic) = raidBoard()
    val printedRaid = 2 + catalog.relics.find(_.id.value == relic.value).get.defense
    val game = rules(dice(Vector.fill(4)(AttackDieFace.OneSword),
      Vector.fill(printedRaid)(DefenseDieFace.Blank)))
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor).toOption.get
    val kind = game.resolveWalker(started.state, b.actor, CampaignIds.kind,
      ChooseOneAnswer(DecisionOptionRef.Button("raid"))).toOption.get
    val targeted = game.resolveWalker(kind.state, b.actor, CampaignIds.targets,
      ChooseManyAnswer(Vector(DecisionOptionRef.Relic(relic)))).toOption.get
    val forced = game.resolveWalker(targeted.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(4)).toOption.get
    val fight = game.resolveWalker(forced.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(0)).toOption.get
    val shown = view(fight.state, b.other).lastCampaign.get
    assertEquals(shown.kind, "raid")
    assertEquals(shown.defenderPlayerId, Some(b.other.value))
    assertEquals(shown.raidTargets, Vector(s"pawn:${b.other.value}",
      s"relic:${b.other.value}:${relic.value}"))
    assertEquals(shown.targetSiteIds, Vector.empty[String])
  }

  test("the start control is offered to the active player exactly when a Campaign could start") {
    val b = board()
    assert(view(Ready(b.ready), b.actor).legalControls.contains("beginCampaign"))
    assert(!view(Ready(b.ready), b.other).legalControls.contains("beginCampaign"))
    assert(!view(Ready(board(supply = 1).ready), b.actor).legalControls
      .contains("beginCampaign"))
    val started = rules().startWalker(Ready(b.ready), ActionRef.Campaign, b.actor)
      .toOption.get
    assert(!view(started.state, b.actor).legalControls.contains("beginCampaign"))
  }

  test("a player defender's plan decision is the defender's; everyone else waits on them") {
    val b = againstPlayer(board())
    val game = rules(dice(Vector.fill(2)(AttackDieFace.OneSword),
      Vector.fill(printed(b))(DefenseDieFace.Blank)))
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor).toOption.get
    val plans = game.resolveWalker(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(2)).toOption.get
    val defender = view(plans.state, b.other)
    assertEquals(defender.walkerDecision.map(_.decisionId), Some(CampaignIds.defenderPlan))
    assertEquals(defender.walkerWaiting, None)
    val attacker = view(plans.state, b.actor)
    assertEquals(attacker.walkerDecision, None)
    assertEquals(attacker.walkerWaiting.map(_.playerId), Some(b.other.value))
    assertEquals(projector.projectPublic("campaign", LoadedGame(plans.state, 40))
      .walkerDecision, None)
  }

  test("Campaign is offered as a start control, not as a board-target selection") {
    val b = board(extras = 1)
    val projection = view(Ready(b.ready), b.actor)
    assert(projection.legalControls.contains("beginCampaign"))
    assert(!projection.boardTargetActions.exists(_.actionKind.startsWith("campaign")))
    assert(!projection.legalControls.exists(_.endsWith("CampaignPlans")))
  }
}
