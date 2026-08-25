package oathdigital.gameplay

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.{RecoverCommand, RecoverRules}
import oathdigital.model._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup.OathEvent._
import oathdigital.gameplay.setup.OathState.Ready
import oathdigital.gameplay.setup.OathViolation._

class RecoverSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)

  private def recoverable: (ReadyGame, PlayerState, SiteId, RelicState) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val active = base.game.current.players.find(
      _.player == base.game.current.turn.activePlayer).get
    val siteId = base.game.current.map.inPlay.find { id =>
      RecoverRules.difficulty(catalog, id).nonEmpty
    }.get
    val relic = RelicState(base.game.current.commonCards.relicDeck.head,
      Orientation.FaceDown, Tokens.empty)
    val site = base.game.current.map.sites(siteId).copy(relics = Vector(relic))
    val moved = active.copy(pawnSite = Some(siteId))
    val ready = base.copy(game = base.game.copy(current = base.game.current.copy(
      turn = base.game.current.turn.copy(phase = Phase.Act),
      players = base.game.current.players.map(p => if (p.player == active.player) moved else p),
      map = base.game.current.map.copy(sites = base.game.current.map.sites.updated(siteId, site)))))
    (ready, moved, siteId, relic)
  }

  test("defense faces accumulate shields then apply every doubler") {
    assertEquals(RecoverRules.score(Vector(DefenseDieFace.OneShield,
      DefenseDieFace.TwoShields, DefenseDieFace.Doubler,
      DefenseDieFace.Doubler)), 12)
    assertEquals(RecoverRules.score(Vector(DefenseDieFace.Blank,
      DefenseDieFace.Doubler)), 0)
  }

  test("ordinary Act projects Recover and Rest as additive legal controls") {
    val (ready, player, _, _) = recoverable
    val projection = new GameProjector(catalog).project(
      "recover-controls", LoadedGame(Ready(ready), 1), player.player)

    assertEquals(projection.legalControls.toSet,
      Set("beginRecover", "beginRest", "facedownAdviserMinorAction",
        "peekSiteRelics", "beginNegotiation"))
  }

  test("each payment records two dice and stop returns to Act without revealing") {
    val (ready, player, _, _) = recoverable
    val id = DecisionId("recover-test")
    val first = rules.handle(Ready(ready), RecoverCommand.Roll(player.player, id,
      Vector(DefenseDieFace.Blank, DefenseDieFace.OneShield))).toOption.get
    val Ready(pending) = first.state: @unchecked
    assertEquals(pending.game.current.pending.collect {
      case r: PendingProcedure.Recover => r.supplySpent }, Some(1))
    assertEquals(pending.game.current.players.find(_.player == player.player).get
      .board.supply.supply, player.board.supply.supply - 1)
    val stopped = rules.handle(first.state, RecoverCommand.Stop(player.player, id)).toOption.get
    val Ready(after) = stopped.state: @unchecked
    assertEquals(after.game.current.pending, None)
    assertEquals(after.game.current.turn.phase, Phase.Act)
  }

  test("success privately peeks and transfers exactly one facedown relic") {
    val (base, player, siteId, relic) = recoverable
    val ready = base.copy(game = base.game.copy(campaign =
      base.game.campaign.copy(oathkeeperGoal = OathkeeperGoal.Protection)))
    val other = ready.game.current.players.find(_.player != player.player).get
    val id = DecisionId("recover-private")
    val success = rules.handle(Ready(ready), RecoverCommand.Roll(player.player, id,
      Vector(DefenseDieFace.TwoShields, DefenseDieFace.Doubler))).toOption.get
    val projector = new GameProjector(catalog)
    val owner = projector.project("recover", LoadedGame(success.state, 1), player.player)
    val hidden = projector.project("recover", LoadedGame(success.state, 1), other.player)
    assertEquals(owner.pendingCardDecision.map(_.cards.map(_.cardId)),
      Some(Vector(relic.id.value)))
    assertEquals(hidden.pendingCardDecision, None)
    assertEquals(hidden.recover, None)
    assertEquals(hidden.phase, "recover-waiting")
    val taken = rules.handle(success.state,
      RecoverCommand.TakeRelic(player.player, id, relic.id)).toOption.get
    val Ready(after) = taken.state: @unchecked
    assertEquals(after.game.current.map.sites(siteId).relics, Vector.empty)
    assertEquals(after.game.current.players.find(_.player == player.player).get
      .relics.map(r => r.id -> r.orientation), Vector(relic.id -> Orientation.FaceDown))
    assertEquals(after.game.current.pending, None)
    assertEquals(taken.events.last, OathkeeperChanged(Some(player.player)))
    assertEquals(after.game.current.title.holder, Some(player.player))
  }

  test("replay rejects wrong cost roll size stale decision and unavailable relic") {
    val (ready, player, site, relic) = recoverable
    val id = DecisionId("recover-tamper")
    assert(rules.evolve(Ready(ready), RecoverRolled(player.player, id, site, 2,
      Vector(DefenseDieFace.OneShield, DefenseDieFace.OneShield))).left.toOption.get
      .isInstanceOf[RecoverOutcomeMismatch])
    assert(rules.evolve(Ready(ready), RecoverRolled(player.player, id, site, 1,
      Vector(DefenseDieFace.OneShield))).left.toOption.get
      .isInstanceOf[RecoverOutcomeMismatch])
    val started = rules.handle(Ready(ready), RecoverCommand.Roll(player.player, id,
      Vector(DefenseDieFace.TwoShields, DefenseDieFace.Doubler))).toOption.get
    assert(rules.handle(started.state, RecoverCommand.TakeRelic(player.player,
      DecisionId("stale"), relic.id)).left.toOption.get
      .isInstanceOf[RecoverDecisionMismatch])
    assert(rules.handle(started.state, RecoverCommand.TakeRelic(player.player,
      id, RelicId("not-there"))).left.toOption.get
      .isInstanceOf[RecoverOutcomeMismatch])
  }
}
