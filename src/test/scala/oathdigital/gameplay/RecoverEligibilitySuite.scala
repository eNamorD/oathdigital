package oathdigital.gameplay

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

/** Covers `LegalActionProjector.recoverEligible`: projection delegates to the
  * same Recover procedure builder as command execution, and relic availability
  * does not gate the action.
  */
class RecoverEligibilitySuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)
  private val projector = new GameProjector(catalog)

  private def baseReady: (ReadyGame, PlayerState, SiteId) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val active = base.game.current.players.find(
      _.player == base.game.current.turn.activePlayer).get
    val siteId = base.game.current.map.inPlay.find { id =>
      RecoverRules.difficulty(catalog, id).nonEmpty
    }.get
    val moved = active.copy(pawnSite = Some(siteId))
    val ready = base.copy(game = base.game.copy(current = base.game.current.copy(
      turn = base.game.current.turn.copy(phase = Phase.Act),
      players = base.game.current.players.map(p =>
        if (p.player == active.player) moved else p))))
    (ready, moved, siteId)
  }

  private def legalControls(ready: ReadyGame, actor: PlayerId): Vector[String] =
    projector.project("recover-eligibility", LoadedGame(Ready(ready), 1), actor)
      .legalControls

  test("beginRecover is offered when a facedown relic already sits at the site") {
    val (base, active, siteId) = baseReady
    val relic = RelicState(base.game.current.commonCards.relicDeck.head,
      Orientation.FaceDown, Tokens.empty)
    val site = base.game.current.map.sites(siteId).copy(relics = Vector(relic))
    val ready = base.copy(game = base.game.copy(current = base.game.current.copy(
      commonCards = base.game.current.commonCards.copy(
        relicDeck = base.game.current.commonCards.relicDeck.tail),
      map = base.game.current.map.copy(sites =
        base.game.current.map.sites.updated(siteId, site)))))

    assert(RecoverProcedure.build(catalog, ready, active.player).isRight)
    assert(legalControls(ready, active.player).contains("beginRecover"))
  }

  test("beginRecover is offered at an empty site once Catacombs is face-up there") {
    val (base, active0, siteId) = baseReady
    val definition = catalog.denizens.find(_.powers.exists(
      _.id.value == "denizen.catacombs")).get
    val cardId = DenizenId(definition.id.value)
    val active = active0.copy(board = active0.board.copy(faceUpSecrets = 2))
    val site = base.game.current.map.sites(siteId).copy(relics = Vector.empty,
      denizens = Vector(DenizenState(cardId, Orientation.FaceUp, Tokens.empty)))
    val ready = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p =>
        if (p.player == active.player) active else p),
      commonCards = base.game.current.commonCards.copy(
        worldDeck = base.game.current.commonCards.worldDeck.filterNot(_ == cardId),
        regionalDiscards = base.game.current.commonCards.regionalDiscards.map {
          case (region, cards) => region -> cards.filterNot(_ == cardId)
        }),
      map = base.game.current.map.copy(sites =
        base.game.current.map.sites.updated(siteId, site)))))

    assert(RecoverProcedure.build(catalog, ready, active.player).isRight)
    assert(legalControls(ready, active.player).contains("beginRecover"))
  }

  test("beginRecover is offered without a relic or Catacombs") {
    val (base, active, siteId) = baseReady
    val catacombsId = DenizenId(catalog.denizens.find(_.powers.exists(
      _.id.value == "denizen.catacombs")).get.id.value)
    // Cleared exactly like the Catacombs fixture above, minus the Catacombs
    // card itself -- isolating the one variable the gate cares about, rather
    // than relying on the setup's incidental relic/denizen placement.
    val site = base.game.current.map.sites(siteId).copy(relics = Vector.empty,
      denizens = base.game.current.map.sites(siteId).denizens.filterNot(_.id == catacombsId))
    val ready = base.copy(game = base.game.copy(current = base.game.current.copy(
      map = base.game.current.map.copy(sites =
        base.game.current.map.sites.updated(siteId, site)))))

    assert(ready.game.current.map.sites(siteId).relics.isEmpty)
    assert(legalControls(ready, active.player).contains("beginRecover"))
  }
}
