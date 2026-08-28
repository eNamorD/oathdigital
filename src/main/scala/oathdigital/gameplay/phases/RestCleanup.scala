package oathdigital.gameplay.phases

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.ReadyGame
import oathdigital.model._

final case class RestCleanupPlan(playerId: PlayerId,
    adviserIds: Set[DenizenId], relicIds: Set[RelicId], siteIds: Set[SiteId],
    returnedFavor: Map[Suit, Int], returnedSecrets: Int,
    facedownSecretsFlipped: Int)

object RestCleanupPlan {
  def derive(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId): Either[String, RestCleanupPlan] = {
    val current = ready.game.current
    current.players.find(_.player == playerId).toRight(
      s"unknown Rest player ${playerId.value}").flatMap { player =>
      val ruled = current.map.sites.collect {
        case (id, site) if (site.forces match {
          case SiteForces.Occupied(ForceKind.Exile(owner), _) => owner == player.lineage
          case _ => false
        }) => id
      }.toSet
      val sites = ruled ++ player.pawnSite
      val advisers = player.advisers.collect { case value: DenizenState => value }
      val siteCards = sites.toVector.sortBy(_.value).flatMap(id =>
        current.map.sites.get(id).toVector.flatMap(_.denizens))
      val favorCards = advisers.map(v => (v.id: CardId) -> v.tokens) ++
        siteCards.map(v => v.id -> v.tokens)
      favorCards.foldLeft[Either[String, Map[Suit, Int]]](
        Right(Map.empty.withDefaultValue(0))) { case (result, (id, tokens)) =>
        result.flatMap { accumulated =>
          if (tokens.favor == 0) Right(accumulated)
          else suitOf(catalog, id).toRight(
            s"cannot attribute favor on ${id.value} to a suit").map(suit =>
            accumulated.updated(suit, accumulated(suit) + tokens.favor))
        }
      }.map(_.filter(_._2 > 0)).map { returned =>
        RestCleanupPlan(playerId, advisers.map(_.id).toSet,
          player.relics.map(_.id).toSet, sites, returned,
          advisers.map(_.tokens.secrets).sum + siteCards.map(_.tokens.secrets).sum +
            player.relics.map(_.tokens.secrets).sum,
          player.board.faceDownSecrets)
      }
    }
  }

  private def suitOf(catalog: ExecutableCatalog, id: CardId): Option[Suit] = {
    val key = catalog.denizens.find(_.id.value == id.value).map(_.suit.value)
      .orElse(catalog.edifices.find(_.id.value == id.value).map(_.suit.value))
    key.flatMap(value => Suit.all.find(_.key == value))
  }
}
