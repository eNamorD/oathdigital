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

  test("valid inactive player has zero commitments and ignores ruled-site tokens") {
    val ready = withState(0, 0, 1)
    val inactive = ready.game.current.players.find(_.player != actor.player).get
    val pawn = actor.pawnSite.get
    val ruledByInactive = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(map = ready.game.current.map.copy(sites =
        ready.game.current.map.sites.updated(pawn,
          ready.game.current.map.sites(pawn).copy(forces = SiteForces.Occupied(
            ForceKind.Exile(inactive.lineage), 1)))))))
    assertEquals(PlayerSecretSummary.derive(ruledByInactive,
      inactive.player).toOption.get.committed, 0)
    assertEquals(PlayerSecretSummary.derive(ruledByInactive,
      actor.player).toOption.get.committed, 1)
  }

  test("inactive adviser secret commitment is a derivation failure") {
    val inactive = base.game.current.players.find(_.player != actor.player).get
    val card = DenizenState(DenizenId(catalog.denizens.head.id.value),
      Orientation.FaceUp, Tokens(0, 1))
    val corrupt = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p => if (p.player == inactive.player)
        p.copy(advisers = Vector(card)) else p))))
    val failure = PlayerSecretSummary.derive(corrupt, inactive.player).left.toOption.get
    assert(failure.contains("inactive player"))
    assert(failure.contains("advisers=1"))
  }

  test("inactive relic secret commitment is a derivation failure") {
    val inactive = base.game.current.players.find(_.player != actor.player).get
    val relic = RelicState(RelicId(catalog.relics.head.id.value),
      Orientation.FaceUp, Tokens(0, 2))
    val corrupt = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p => if (p.player == inactive.player)
        p.copy(relics = Vector(relic)) else p))))
    val failure = PlayerSecretSummary.derive(corrupt, inactive.player).left.toOption.get
    assert(failure.contains("inactive player"))
    assert(failure.contains("relics=2"))
    val projected = intercept[IllegalStateException] {
      new oathdigital.application.GameProjector(catalog).projectPublic(
        "corrupt-secrets", oathdigital.application.LoadedGame(Ready(corrupt), 9L))
    }
    assert(projected.getMessage.contains("ambiguous committed secrets"))
  }
}
