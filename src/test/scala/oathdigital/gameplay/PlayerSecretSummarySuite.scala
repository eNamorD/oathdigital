package oathdigital.gameplay

import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.setup.{FirstGameSetupRules,
  FirstGameSetupFixture}
import oathdigital.model._

class PlayerSecretSummarySuite extends munit.FunSuite {
  import FirstGameSetupFixture._
  private val Ready(base) = execute(new FirstGameSetupRules(catalog))._1: @unchecked
  private val actor = base.game.current.players.find(
    _.player == base.game.current.turn.activePlayer).get

  private def withState(available: Int, facedown: Int,
      siteCommitted: Int): ReadyGame = {
    val site = actor.pawnSite.get
    val definition = catalog.denizens.head
    base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p => if (p.player == actor.player)
        p.copy(board = p.board.copy(faceUpSecrets = available,
          faceDownSecrets = facedown), advisers = Vector.empty, relics = Vector.empty)
        else p),
      map = base.game.current.map.copy(sites = base.game.current.map.sites.updated(
        site, base.game.current.map.sites(site).copy(denizens =
          Option.when(siteCommitted > 0)(DenizenState(
            DenizenId(definition.id.value), Orientation.FaceUp,
            Tokens(0, siteCommitted))).toVector))))))
  }

  test("derived secret accounting reports available facedown committed and total") {
    val examples = Vector(
      withState(1, 0, 0) -> PlayerSecretSummary(1, 0, 0),
      withState(0, 0, 1) -> PlayerSecretSummary(0, 0, 1),
      withState(0, 1, 0) -> PlayerSecretSummary(0, 1, 0),
      withState(1, 0, 1) -> PlayerSecretSummary(1, 0, 1),
      withState(1, 0, 0) -> PlayerSecretSummary(1, 0, 0))
    examples.foreach { case (ready, expected) =>
      val actual = PlayerSecretSummary.derive(ready, actor.player).toOption.get
      assertEquals(actual, expected)
      assertEquals(actual.totalSecrets,
        actual.available + actual.facedown + actual.committed)
    }
  }

  test("inactive players do not inherit active accessible-site commitments") {
    val ready = withState(0, 0, 1)
    val inactive = ready.game.current.players.find(_.player != actor.player).get
    assertEquals(PlayerSecretSummary.derive(ready, inactive.player).toOption.get.committed,
      inactive.advisers.collect { case d: DenizenState => d.tokens.secrets }.sum +
        inactive.relics.map(_.tokens.secrets).sum)
  }
}
