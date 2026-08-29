package oathdigital.gameplay.phases

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{InPlayCardResources, PlayerResourceSources, ReadyGame}
import oathdigital.model._

final case class RestCleanupPlan(returnedFavor: Map[Suit, Int],
    returnedSecrets: Int)

object RestCleanupPlan {
  def derive(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId): Either[String, RestCleanupPlan] = {
    PlayerResourceSources.player(ready, playerId).flatMap { _ =>
      val resources = InPlayCardResources.discover(ready)
      resources.denizens.foldLeft[Either[String, Map[Suit, Int]]](
        Right(Map.empty.withDefaultValue(0))) { case (result, card) =>
        result.flatMap { accumulated =>
          if (card.tokens.favor == 0) Right(accumulated)
          else suitOf(catalog, card.id).toRight(
            s"cannot attribute favor on ${card.id.value} to a suit").map(suit =>
            accumulated.updated(suit, accumulated(suit) + card.tokens.favor))
        }
      }.map(_.filter(_._2 > 0)).map { returned =>
        RestCleanupPlan(returned, resources.secrets)
      }
    }
  }

  private def suitOf(catalog: ExecutableCatalog, id: CardId): Option[Suit] = {
    val key = catalog.denizens.find(_.id.value == id.value).map(_.suit.value)
      .orElse(catalog.edifices.find(_.id.value == id.value).map(_.suit.value))
    key.flatMap(value => Suit.all.find(_.key == value))
  }
}
