package oathdigital.application

import oathdigital.gameplay.{EconomyFixture, OathRules}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.GameProjection

/** What the Supply track promises the client.
  *
  * The maximum lets the client print "5/7" without knowing the rules, and
  * the Rest preview lets the Act button say what ending the Act returns.
  * The preview is taken before Rest runs, so it is checked against what
  * Rest actually pays.
  */
class SupplyProjectionSuite extends munit.FunSuite {
  import EconomyFixture._

  private val rules = new OathRules(catalog)
  private val projector = new GameProjector(catalog)

  private def project(ready: ReadyGame, viewer: PlayerId): GameProjection =
    projector.project("supply", LoadedGame(Ready(ready), 4), viewer)

  test("the preview matches the Supply Rest actually returns") {
    val board = act(supply = 1)
    val actor = player(board).player
    val projected = project(board, actor)
    assertEquals(projected.supplyMaximum, 7)
    val gain = projected.restSupplyGain
      .getOrElse(fail("an Act that can Rest must preview the return"))
    val completed = rules
      .startWalker(Ready(board), PhaseTransitionRef.BeginRest, actor)
      .getOrElse(fail("Rest must run"))
    val Ready(after) = completed.state: @unchecked
    assertEquals(after.game.current.players.find(_.player == actor).get
      .board.supply.supply, 1 + gain)
  }

  test("a full track returns nothing") {
    val board = act(supply = 7)
    assertEquals(project(board, player(board).player).restSupplyGain, Some(0))
  }

  test("a player who cannot Rest is shown no preview") {
    val board = act()
    val other = board.game.current.players.map(_.player)
      .find(_ != player(board).player).get
    assertEquals(project(board, other).restSupplyGain, None)
  }
}
