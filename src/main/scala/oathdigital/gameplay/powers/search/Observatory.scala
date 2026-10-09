package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower,
  SiteOnly}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object ObservatoryCard extends Denizen(DenizenId("64"), "Observatory", Suit.Arcane) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.observatory"),
    persistent = false, cost = Cost.free,
    text = "While your pawn is here, you may draw from any one discard " +
      "pile.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Observatory (card 64, site-only), a free selected Search modifier,
  * usable while the actor's pawn is at its site (`PowerAccess`). Every
  * region's discard pile is offered at the source decision, each at 2
  * Supply, after the printed two and in region order (catalog batch 6,
  * slice 3). It writes no line: the start line names it, and the Search
  * line names the pile.
  */
final case class Observatory private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = ObservatoryCard.id
  def id: PowerId = Observatory.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchSourceSelection -> Vector(Transform((_, operations) =>
      SearchSources.withPiles(operations, Region.all))))

object Observatory:
  val id: PowerId = ObservatoryCard.power.id

  def forCatalog(catalog: ExecutableCatalog): Observatory =
    new Observatory(catalog)
