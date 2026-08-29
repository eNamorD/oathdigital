package oathdigital.gameplay

import oathdigital.model._

/** Every resource-bearing card currently in play, independent of access,
  * ownership, rule, location, or orientation.
  */
final case class InPlayCardResources(
    denizens: Vector[SiteDenizenState],
    relics: Vector[RelicState]) {
  def secrets: Int = denizens.map(_.tokens.secrets).sum +
    relics.map(_.tokens.secrets).sum
}

object InPlayCardResources {
  def discover(ready: ReadyGame): InPlayCardResources = {
    val current = ready.game.current
    val playerDenizens = current.players.flatMap(_.advisers.collect {
      case value: DenizenState => value
    })
    val playerRelics = current.players.flatMap(_.relics)
    val siteDenizens = current.map.inPlay.flatMap(id =>
      current.map.sites.get(id).toVector.flatMap(_.denizens))
    val siteRelics = current.map.inPlay.flatMap(id =>
      current.map.sites.get(id).toVector.flatMap(_.relics))
    InPlayCardResources(playerDenizens ++ siteDenizens,
      playerRelics ++ siteRelics)
  }
}
