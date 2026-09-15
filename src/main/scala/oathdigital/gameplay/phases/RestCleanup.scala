package oathdigital.gameplay.phases

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{InPlayCardResources, PlayerResourceSources, ReadyGame}
import oathdigital.model._

/** One in-play card's planned Rest return. Denizens and edifices return both
  * favor (to their printed suit bank) and secrets; relics return only secrets
  * and keep favor in place.
  */
final case class RestCleanupCard(
    id: CardId,
    suit: Option[Suit],
    favor: Int,
    secrets: Int)

/** Immutable Rest cleanup plan. `cards` retains per-card suit and token
  * amounts so completion can execute the same plan as core operations, while
  * `returnedFavor` and `returnedSecrets` keep the authoritative event facts.
  */
final case class RestCleanupPlan(
    cards: Vector[RestCleanupCard],
    returnedFavor: Map[Suit, Int],
    returnedSecrets: Int)

object RestCleanupPlan {
  def derive(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId): Either[String, RestCleanupPlan] = {
    PlayerResourceSources.player(ready, playerId).flatMap { _ =>
      val resources = InPlayCardResources.discover(ready)
      val denizenCards = resources.denizens.foldLeft[
        Either[String, Vector[RestCleanupCard]]](Right(Vector.empty)) {
        case (result, card) => result.flatMap { accumulated =>
          if (card.tokens.favor == 0) Right(accumulated :+ RestCleanupCard(
            card.id, None, 0, card.tokens.secrets))
          else suitOf(catalog, card.id).toRight(
            s"cannot attribute favor on ${card.id.value} to a suit").map { suit =>
            accumulated :+ RestCleanupCard(card.id, Some(suit),
              card.tokens.favor, card.tokens.secrets)
          }
        }
      }
      denizenCards.map { denizens =>
        val relicCards = resources.relics.map(relic => RestCleanupCard(
          relic.id, None, 0, relic.tokens.secrets))
        val cards = denizens ++ relicCards
        val returnedFavor = cards.foldLeft(Map.empty[Suit, Int]) {
          case (banks, card) => card.suit match {
            case Some(suit) if card.favor > 0 =>
              banks.updated(suit, banks.getOrElse(suit, 0) + card.favor)
            case _ => banks
          }
        }
        RestCleanupPlan(cards, returnedFavor,
          cards.iterator.map(_.secrets).sum)
      }
    }
  }

  private[gameplay] def suitOf(catalog: ExecutableCatalog, id: CardId): Option[Suit] = {
    val key = catalog.denizens.find(_.id.value == id.value).map(_.suit.value)
      .orElse(catalog.edifices.find(_.id.value == id.value).map(_.suit.value))
    key.flatMap(value => Suit.all.find(_.key == value))
  }
}
