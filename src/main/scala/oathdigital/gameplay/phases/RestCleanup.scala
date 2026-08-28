package oathdigital.gameplay.phases

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{PlayerResourceSources, ReadyGame}
import oathdigital.model._

final case class RestCleanupPlan(playerId: PlayerId,
    adviserIds: Set[DenizenId], relicIds: Set[RelicId], siteIds: Set[SiteId],
    returnedFavor: Map[Suit, Int], returnedSecrets: Int,
    facedownSecretsFlipped: Int)

object RestCleanupPlan {
  def derive(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId): Either[String, RestCleanupPlan] = {
    PlayerResourceSources.discover(ready, playerId).flatMap { sources =>
      val player = sources.player
      val advisers = sources.adviserDenizens
      val siteCards = sources.siteCards
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
          sources.heldRelics.map(_.id).toSet, sources.siteIds, returned,
          advisers.map(_.tokens.secrets).sum + siteCards.map(_.tokens.secrets).sum +
            sources.heldRelics.map(_.tokens.secrets).sum,
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
