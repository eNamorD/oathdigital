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
  * The preview is the return BEFORE the track's ceiling takes its cut, so a
  * player pricing this Act's spending can read it as the Supply they may
  * still spend for free; it is checked here against what Rest pays once the
  * ceiling has applied.
  */
class SupplyProjectionSuite extends munit.FunSuite {
  import EconomyFixture._

  private val rules = new OathRules(catalog)
  private val projector = new GameProjector(catalog)

  private def project(ready: ReadyGame, viewer: PlayerId): GameProjection =
    projector.project("supply", LoadedGame(Ready(ready), 4), viewer)

  /** The Supply the resting player holds once Rest has run. */
  private def rested(board: ReadyGame, actor: PlayerId): Int = {
    val completed = rules
      .startWalker(Ready(board), PhaseTransitionRef.BeginRest, actor)
      .getOrElse(fail("Rest must run"))
    val Ready(after) = completed.state: @unchecked
    after.game.current.players.find(_.player == actor).get
      .board.supply.supply
  }

  private def preview(board: ReadyGame, actor: PlayerId): Int =
    project(board, actor).restSupplyGain
      .getOrElse(fail("an Act that can Rest must preview the return"))

  test("the preview is the Supply Rest returns, capped by the track") {
    val board = act(supply = 1)
    val actor = player(board).player
    val projected = project(board, actor)
    assertEquals(projected.supplyMaximum, 7)
    val gain = projected.restSupplyGain
      .getOrElse(fail("an Act that can Rest must preview the return"))
    assertEquals(rested(board, actor), math.min(7, 1 + gain))
  }

  test("a full track is still promised its whole band") {
    val empty = act(supply = 0)
    val full = act(supply = 7)
    val actor = player(full).player
    // The band is read off banked warbands, not off the track, so a player
    // sitting at 7/7 is told the same number as one sitting at 0/7 -- which
    // is what makes it a spending budget rather than the room left.
    val gain = preview(full, actor)
    assert(gain > 0, "an Exile band returns Supply")
    assertEquals(gain, preview(empty, player(empty).player))
    assertEquals(rested(full, actor), 7)
  }

  test("a player who cannot Rest is shown no preview") {
    val board = act()
    val other = board.game.current.players.map(_.player)
      .find(_ != player(board).player).get
    assertEquals(project(board, other).restSupplyGain, None)
  }
}
