package oathdigital.gameplay

import oathdigital.model._

final case class PlayerResourceSources(
    player: PlayerState,
    adviserDenizens: Vector[DenizenState],
    heldRelics: Vector[RelicState],
    siteIds: Set[SiteId],
    siteCards: Vector[SiteDenizenState])

object PlayerResourceSources {
  def player(ready: ReadyGame, playerId: PlayerId): Either[String, PlayerState] =
    ready.game.current.players.find(_.player == playerId).toRight(
      s"unknown player ${playerId.value}")

  /** Printed access set used for the active player's commitments and Rest. */
  def discover(ready: ReadyGame,
      playerId: PlayerId): Either[String, PlayerResourceSources] = player(ready,
    playerId).map { resolved =>
    val current = ready.game.current
    val ruled = current.map.sites.collect {
      case (id, site) if (site.forces match {
        case SiteForces.Occupied(ForceKind.Exile(owner), _) =>
          owner == resolved.lineage
        case _ => false
      }) => id
    }.toSet
    val sites = ruled ++ resolved.pawnSite
    PlayerResourceSources(resolved, resolved.advisers.collect {
      case value: DenizenState => value
    }, resolved.relics, sites, sites.toVector.sortBy(_.value).flatMap(id =>
      current.map.sites.get(id).toVector.flatMap(_.denizens)))
  }
}
