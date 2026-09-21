package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution}
import oathdigital.model._

/** Gossip (card 99), a persistent rule of a faceup adviser: when any other
  * player places an adviser facedown, a denizen or a Vision, the holder gains 1
  * favor from the Discord bank. The holder's own facedown plays do not count.
  *
  * The rule is automatic, so it needs no selection. A facedown copy is not
  * active, and the card is adviser-only, so the holder is found among the
  * players' faceup advisers.
  */
final case class Gossip private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower {
  def id: PowerId = Gossip.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFacedown -> Vector(Transform((ctx, children) =>
      holderOf(ctx).fold(children)(holder => children :+
        Gain.Favor(holder, Suit.Discord, Gossip.Favor)))))

  override def applicable(ctx: PowerCtx): Boolean = holderOf(ctx).nonEmpty

  /** The holder, when the hooked play is another player's facedown play. */
  private def holderOf(ctx: PowerCtx): Option[PlayerId] = ctx.operation match {
    case CardPlayedFacedown(_, player) => ctx.state.game.current.players
      .find(_.advisers.exists {
        case DenizenState(card, Orientation.FaceUp, _) => card == cardId
        case _ => false
      }).map(_.player).filter(_ != player)
    case _ => None
  }
}

object Gossip {
  val id: PowerId = PowerId("denizen.gossip")
  val Favor: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[Gossip] =
    CatalogCards.denizen(catalog, id).map(new Gossip(_, catalog))
}
