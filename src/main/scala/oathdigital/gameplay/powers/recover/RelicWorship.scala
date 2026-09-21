package oathdigital.gameplay.powers.recover

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** Relic Worship (card 173), a selected Recover modifier. Cost: 1 secret placed
  * on the card. After recovering a relic, gain 2 Supply.
  *
  * The secret is paid at the start of the Recover, like every modifier's cost
  * (the kit does it), and the gain follows the move that takes the relic, at
  * `RecoverAfterRelic`. A Recover that ends without a relic has still paid the
  * secret and gains nothing: the player owns the choice to select it. Selecting
  * needs a faceup secret and an empty card, and every selected modifier's payment
  * is checked together at selection, so Catacombs and Relic Worship with one
  * faceup secret are refused at the start instead of stranding the Recover.
  */
final case class RelicWorship private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier {
  def id: PowerId = RelicWorship.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Recover)
  override def cost: Cost = Cost(secret = RelicWorship.Secrets)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.RecoverAfterRelic -> Vector(Transform((ctx, operations) =>
      operations :+ GainSupply(ctx.activePlayer, RelicWorship.Supply))))
}

object RelicWorship {
  val id: PowerId = PowerId("denizen.relic-worship")
  val Secrets: Int = 1
  val Supply: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[RelicWorship] =
    CatalogCards.denizen(catalog, id).map(new RelicWorship(_, catalog))
}
