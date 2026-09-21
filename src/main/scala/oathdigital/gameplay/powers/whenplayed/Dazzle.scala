package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.operations._
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Dazzle discards as many Hearth/Order site denizens and ruined edifices in
  * the actor's region as the generic discard rules permit. Intact edifices are
  * locked, so they stay.
  */
final case class Dazzle private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower {
  def id: PowerId = Dazzle.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  override def applicable(ctx: PowerCtx): Boolean = ctx.operation match {
    case CardPlayedFaceup(card, _) => card == cardId
    case _ => false
  }

  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.ActionCardPlayedFaceup -> Vector(Transform((ctx, children) =>
      children :+ BuildOps((ready, _) => effects(ready, ctx.activePlayer),
        restrictions = (_, _) => Vector(
        new DiscardRestrictions(catalog, ctx.activePlayer))))))

  private def effects(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val current = ready.game.current
    val region = current.players.find(_.player == actor).flatMap(_.pawnSite)
      .flatMap(current.map.regionOf)
    region.toRight(OathViolation.PawnSiteMissing(actor)).flatMap { origin =>
      val destination = origin match {
        case Region.Cradle => Region.Provinces
        case Region.Provinces => Region.Hinterland
        case Region.Hinterland => Region.Cradle
      }
      val candidates = current.map.inPlay.filter(site =>
        current.map.regionOf(site).contains(origin)).flatMap { siteId =>
        current.map.sites.get(siteId).toVector.flatMap(_.denizens.map(siteId -> _))
      }
      candidates.foldLeft[Either[OathViolation, Vector[CoreOperation]]](
        Right(Vector.empty)) { case (acc, (siteId, card)) => for {
        operations <- acc
        suit <- catalog.suitOf(card.id).toRight(card match {
          case denizen: DenizenState => OathViolation.UnknownWorldCard(denizen.id)
          case edifice: EdificeState => OathViolation.UnknownEdifice(edifice.id)
        })
      } yield if (suit != Suit.Hearth && suit != Suit.Order) operations
      else card match {
        case denizen: DenizenState => operations :+ Discard.Denizen(denizen.id,
          PositionedLocation(Location.Site(siteId)), destination, suit,
          denizen.tokens.favor, denizen.tokens.secrets, actor)
        case edifice: EdificeState if edifice.side == EdificeSide.Ruined =>
          operations :+ Discard.RuinedEdifice(edifice.id,
            PositionedLocation(Location.Site(siteId)), suit,
            edifice.tokens.favor, edifice.tokens.secrets, actor)
        case _ => operations
      } }
    }
  }
}

object Dazzle {
  val id: PowerId = PowerId("denizen.dazzle")
  def forCatalog(catalog: ExecutableCatalog): Option[Dazzle] =
    catalog.denizens.find(_.powers.exists(_.id == id))
      .map(definition => new Dazzle(DenizenId(definition.id.value), catalog))
}
