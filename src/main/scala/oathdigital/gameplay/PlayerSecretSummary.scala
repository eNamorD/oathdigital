package oathdigital.gameplay

import oathdigital.model._

final case class PlayerSecretSummary(available: Int, facedown: Int, committed: Int) {
  require(available >= 0 && facedown >= 0 && committed >= 0,
    "secret summary counts must be non-negative")
  def totalSecrets: Int = available + facedown + committed
}

object PlayerSecretSummary {
  def derive(ready: ReadyGame, playerId: PlayerId): Either[String, PlayerSecretSummary] = {
    val current = ready.game.current
    current.players.find(_.player == playerId).toRight(
      s"unknown player ${playerId.value}").map { player =>
      val owned = player.advisers.collect {
        case value: DenizenState => value.tokens.secrets
      }.sum + player.relics.map(_.tokens.secrets).sum
      val accessibleSites = if (current.turn.activePlayer != playerId) Set.empty[SiteId]
      else current.map.sites.collect {
        case (id, site) if (site.forces match {
          case SiteForces.Occupied(ForceKind.Exile(owner), _) => owner == player.lineage
          case _ => false
        }) => id
      }.toSet ++ player.pawnSite
      val siteCommitted = accessibleSites.toVector.distinct.flatMap(id =>
        current.map.sites.get(id).toVector.flatMap(_.denizens))
        .map(_.tokens.secrets).sum
      PlayerSecretSummary(player.board.faceUpSecrets,
        player.board.faceDownSecrets, owned + siteCommitted)
    }
  }
}
