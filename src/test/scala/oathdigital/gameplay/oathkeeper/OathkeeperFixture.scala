package oathdigital.gameplay.oathkeeper

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

/** Title and site-ruler setups for the Supremacy goal the first game uses:
  * `owners(i)` rules the i-th in-play site with one exile warband (`None` is
  * one bandit), so leaders are whoever rules the most sites.
  */
object OathkeeperFixture {
  def base: ReadyGame = initialReady

  def players: Vector[PlayerId] = base.game.current.players.map(_.player)

  def ruled(ready: ReadyGame, owners: Vector[Option[PlayerId]],
      holder: Option[PlayerId] = None,
      side: TitleSide = TitleSide.Oathkeeper): ReadyGame = {
    val byPlayer = ready.game.current.players.map(p => p.player -> p.lineage).toMap
    val sites = ready.game.current.map.inPlay.zipWithIndex.map { case (id, i) =>
      val force = owners.lift(i).flatten.fold[SiteForces](
        SiteForces.Occupied(ForceKind.Bandit, 1))(player =>
        SiteForces.Occupied(ForceKind.Exile(byPlayer(player)), 1))
      id -> ready.game.current.map.sites(id).copy(forces = force)
    }.toMap
    ready.updateCurrent(_.copy(
      map = ready.game.current.map.copy(sites = sites),
      title = OathkeeperState(holder, side)))
  }

  def inPhase(ready: ReadyGame, phase: Phase): ReadyGame =
    ready.updateCurrent(_.copy(
      turn = ready.game.current.turn.copy(phase = phase)))
}
