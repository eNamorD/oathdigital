package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

object AuguryCard extends Denizen(DenizenId("56"), "Augury", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.augury"),
    persistent = false, cost = Cost.free,
    text = "Draw one more card. _(Stop after a Vision as normal.)_")
  val powers: Vector[PrintedPower] = Vector(power)

/** Augury (card 56), a selected Search modifier: a Search from the world deck
  * or a regional discard draws one more card. The draw still stops after a
  * Vision.
  */
final case class Augury private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier:
  def id: PowerId = Augury.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchBeforeDraw -> Vector(Transform((_, operations) =>
      DrawExtension.extend(operations, Augury.More))))

object Augury:
  val id: PowerId = PowerId("denizen.augury")
  val More: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[Augury] =
    CatalogCards.denizen(catalog, id).map(new Augury(_, catalog))
