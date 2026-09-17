package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathViolation, ReadyGame, RuleSourceRef}
import oathdigital.gameplay.operations._
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Dazzle discards as many Hearth/Order site denizens in the actor's region
  * as the generic discard rules permit. Immunity is a pipeline restriction,
  * never filtered by this power.
  */
final case class Dazzle private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower {
  def id: PowerId = Dazzle.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  override def applicable(ctx: PowerCtx): Boolean = ctx.operation match {
    case CardPlayed(card, _) => card == cardId
    case _ => false
  }

  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.ActionCardPlayed -> Vector(Transform((ctx, children) =>
      children :+ BuildOps((ready, _) => effects(ready, ctx.activePlayer),
        restrictions = (_, _) => Vector(
        new DiscardRestrictions(catalog, ctx.activePlayer))))))

  private def effects(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val current = ready.game.current
    val region = current.players.find(_.player == actor).flatMap(_.pawnSite)
      .flatMap(current.map.regionOf)
    region.toRight(OathViolation.PawnSiteMissing(actor)).map { origin =>
      val destination = origin match {
        case Region.Cradle => Region.Provinces
        case Region.Provinces => Region.Hinterland
        case Region.Hinterland => Region.Cradle
      }
      current.map.inPlay.filter(site =>
        current.map.regionOf(site).contains(origin)).flatMap { siteId =>
        current.map.sites.get(siteId).toVector.flatMap(_.denizens.flatMap {
          case card: DenizenState => suitOf(card.id)
            .filter(suit => suit == Suit.Hearth || suit == Suit.Order)
            .map(suit => Discard.Denizen(card.id,
              PositionedLocation(Location.Site(siteId)), destination,
              suit, card.tokens.favor, card.tokens.secrets, actor))
          case _ => None
        })
      }
    }
  }

  private def suitOf(card: DenizenId): Option[Suit] =
    catalog.denizens.find(_.id.value == card.value)
      .flatMap(definition => Suit.all.find(_.key == definition.suit.value))
}

object Dazzle {
  val id: PowerId = PowerId("denizen.dazzle")
  def forCatalog(catalog: ExecutableCatalog): Option[Dazzle] =
    catalog.denizens.find(_.powers.exists(_.id == id))
      .map(definition => new Dazzle(DenizenId(definition.id.value), catalog))
}
