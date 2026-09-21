package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, PlayerFacts, SelectedModifier}
import oathdigital.model._

/** Wild Cry (card 189), a selected Search modifier: when you play a beast
  * denizen faceup, to a site or as a faceup adviser, gain 1 Supply and 2
  * warbands. A facedown play does not trigger it, and a card does not trigger
  * on its own play.
  *
  * The trigger reads only the played card, so the fold is the same on every
  * resume. Gaining warbands is best-effort: an empty warband bank gives what
  * it holds.
  */
final case class WildCry private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier {
  def id: PowerId = WildCry.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup -> Vector(Transform((ctx, children) =>
      children ++ effects(ctx.activePlayer))))

  override def appliesAt(ctx: PowerCtx): Boolean = ctx.operation match {
    case CardPlayedFaceup(card: DenizenId, _) =>
      card != cardId && catalog.suitOf(card).contains(Suit.Beast)
    case _ => false
  }

  private def effects(actor: PlayerId): Vector[Operation] = Vector(
    GainSupply(actor, WildCry.Supply),
    BuildOps((ready, _) => PlayerFacts.forceKind(ready, actor).map(kind =>
      Vector(Gain.Warbands(actor, kind, WildCry.Warbands)))))
}

object WildCry {
  val id: PowerId = PowerId("denizen.wild-cry")
  val Supply: Int = 1
  val Warbands: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[WildCry] =
    CatalogCards.denizen(catalog, id).map(new WildCry(_, catalog))
}
