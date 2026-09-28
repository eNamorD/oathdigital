package oathdigital.gameplay

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.RecoverRules
import oathdigital.model.OathState.Ready
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.Table

/** Covers `LegalActionProjector.recoverEligible`: projection delegates to the
  * same Recover procedure builder as command execution, and relic availability
  * does not gate the action.
  */
class RecoverEligibilitySuite extends munit.FunSuite:
  private val projector = new GameProjector(catalog)

  private def baseReady: (ReadyGame, PlayerState, SiteId) =
    val base = Table.start.ready
    val active = base.game.current.players.find(
      _.player == base.game.current.turn.activePlayer).get
    val siteId = base.game.current.map.inPlay.find { id =>
      RecoverRules.difficulty(catalog, id).nonEmpty
    }.get
    val moved = active.copy(pawnSite = Some(siteId))
    val ready = base.updateCurrent(_.copy(
      players = base.game.current.players.map(p =>
        if p.player == active.player then moved else p)))
    (ready, moved, siteId)

  private def legalControls(ready: ReadyGame, actor: PlayerId): Vector[String] =
    projector.project("recover-eligibility", LoadedGame(Ready(ready), 1), actor)
      .legalControls

  test("beginRecover is offered without a relic or Catacombs"):
    val (base, active, siteId) = baseReady
    val catacombsId = DenizenId(catalog.denizens.find(_.powers.exists(
      _.id.value == "denizen.catacombs")).get.id.value)
    // Cleared exactly like the Catacombs fixture above, minus the Catacombs
    // card itself -- isolating the one variable the gate cares about, rather
    // than relying on the setup's incidental relic/denizen placement.
    val site = base.game.current.map.sites(siteId).copy(relics = Vector.empty,
      denizens = base.game.current.map.sites(siteId).denizens.filterNot(_.id == catacombsId))
    val ready = base.updateCurrent(_.copy(
      map = base.game.current.map.copy(sites =
        base.game.current.map.sites.updated(siteId, site))))

    assert(legalControls(ready, active.player).contains("beginRecover"))

  test("beginRecover is not offered without the Supply to pay for it"):
    val (base, active, _) = baseReady
    def withSupply(supply: Int) = base.updateCurrent(_.copy(
      players = base.game.current.players.map(p =>
        if p.player == active.player then
          p.copy(board = p.board.copy(supply = SupplyTrack(supply)))
        else p)))
    assert(legalControls(withSupply(1), active.player).contains("beginRecover"))
    assert(!legalControls(withSupply(0), active.player).contains("beginRecover"))
