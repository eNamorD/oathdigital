package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** Welcoming Party (card 50), a selected Search modifier: if you play a
  * denizen faceup when it is first drawn, gain 1 favor from the Hearth bank.
  *
  * "When first drawn" is the card's origin: it comes straight from the draw,
  * the temporary hand of a Search. It is played faceup when it goes to a site or
  * becomes a faceup adviser. A card placed facedown does not trigger it, and
  * neither does a card that was already a facedown adviser and is played faceup
  * later by the Play-Facedown-Adviser action. A Vision is not a denizen, and a
  * card does not trigger on its own play.
  *
  * The played-card hook does not carry the origin, so the power reads it from
  * the procedure the window is walked for (`PowerCtx.procedure`): a card played
  * by a Search came straight from its draw. The favor is best-effort, so an
  * empty Hearth bank gives nothing.
  */
final case class WelcomingParty private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier {
  def id: PowerId = WelcomingParty.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup -> Vector(Transform((ctx, children) =>
      children :+ Gain.Favor(ctx.activePlayer, Suit.Hearth,
        WelcomingParty.Favor))))

  override def appliesAt(ctx: PowerCtx): Boolean = ctx.operation match {
    case CardPlayedFaceup(card: DenizenId, _) => card != cardId &&
      ctx.procedure.contains(ActionRef.Search)
    case _ => false
  }
}

object WelcomingParty {
  val id: PowerId = PowerId("denizen.welcoming-party")
  val Favor: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[WelcomingParty] =
    CatalogCards.denizen(catalog, id).map(new WelcomingParty(_, catalog))
}
