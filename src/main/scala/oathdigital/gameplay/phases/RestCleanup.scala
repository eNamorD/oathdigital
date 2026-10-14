package oathdigital.gameplay.phases

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{EffectiveSuit, InPlayCardResources,
  PlayerResourceSources}
import oathdigital.model._

/** One in-play card's planned Rest return. Denizens and edifices return both
  * favor (to their suit bank, Discord for a Pledge to Discord holder's
  * advisers: `EffectiveSuit`) and secrets; relics return only secrets and
  * keep favor in place.
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

object RestCleanupPlan:
  def derive(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId): Either[String, RestCleanupPlan] =
    PlayerResourceSources.player(ready, playerId).flatMap { _ =>
      val resources = InPlayCardResources.discover(ready)
      val denizenCards = resources.denizens.foldLeft[
        Either[String, Vector[RestCleanupCard]]](Right(Vector.empty)):
        case (result, card) => result.flatMap { accumulated =>
          if card.tokens.favor == 0 then Right(accumulated :+ RestCleanupCard(
            card.id, None, 0, card.tokens.secrets))
          else EffectiveSuit.of(catalog, ready, card.id).toRight(
            s"cannot attribute favor on ${card.id.value} to a suit").map { suit =>
            accumulated :+ RestCleanupCard(card.id, Some(suit),
              card.tokens.favor, card.tokens.secrets)
          }
        }
      denizenCards.map { denizens =>
        val relicCards = resources.relics.map(relic => RestCleanupCard(
          relic.id, None, 0, relic.tokens.secrets))
        val cards = denizens ++ relicCards
        val returnedFavor = cards.foldLeft(Map.empty[Suit, Int]):
          case (banks, card) => card.suit match
            case Some(suit) if card.favor > 0 =>
              banks.updated(suit, banks.getOrElse(suit, 0) + card.favor)
            case _ => banks
        RestCleanupPlan(cards, returnedFavor,
          cards.iterator.map(_.secrets).sum)
      }
    }

/** The Rest cleanup's operations: favor on every card in play to its
  * suit bank (`EffectiveSuit`: a Pledge to Discord holder's advisers return
  * to Discord), secrets on cards to the resting player, and the
  * resting player's facedown secrets flipped faceup -- the operations legacy
  * `Rest.applyCompletion` ran. Finish Rest runs them at the Rest favor
  * window; A Round of Ale runs them in an action, outside that window.
  */
object RestCleanup:
  def operations(catalog: ExecutableCatalog, ready: ReadyGame,
      resting: PlayerId): Either[OathViolation, Vector[CoreOperation]] = for
    plan <- RestCleanupPlan.derive(catalog, ready, resting)
      .left.map(OathViolation.UnsupportedRestState.apply)
    player <- ready.game.current.players.find(_.player == resting)
      .toRight(OathViolation.UnsupportedRestState(
        s"unknown resting player $resting"))
  yield
    val from = (id: CardId) => PositionedLocation(Location.OnCard(id))
    val favor = plan.cards.collect:
      case card if card.suit.nonEmpty && card.favor > 0 =>
        Move(Piece.Favor(card.favor), from(card.id),
          PositionedLocation(Location.FavorBank(card.suit.get)))
    val secrets = plan.cards.collect:
      case card if card.secrets > 0 => Move(Piece.Secrets(card.secrets),
        from(card.id), PositionedLocation(Location.PlayArea(resting)))
    val reveal = Vector(player.board.faceDownSecrets).filter(_ > 0).map(count =>
      FlipSecrets(resting, count, SecretSide.FaceDown, SecretSide.FaceUp))
    favor ++ secrets ++ reveal
