package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.economy.{MusterSource, TradeProcedure}
import oathdigital.gameplay.powerresolver.Contribution
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** The Cup of Plenty (relic R07), a selected Trade modifier: trading with a
  * card whose suit differs from every faceup adviser the player holds costs no
  * Supply. A facedown adviser does not count, and a player with no faceup
  * adviser trades free. The waiver is [[SupplyWaiver]].
  */
final case class CupOfPlenty private (cardId: RelicId,
    catalog: ExecutableCatalog) extends SelectedModifier:
  def id: PowerId = CupOfPlenty.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)

  def effects: Map[PowerWindow, Vector[Contribution]] = SupplyWaiver.effects(
    PowerWindow.TradeCost, TradeProcedure.decisionId, catalog)(
    (ready, actor, suit) =>
      MusterSource.matching(catalog, ready, actor, suit) == 0)

object CupOfPlenty:
  val id: PowerId = PowerId("relic.cup-of-plenty")

  def forCatalog(catalog: ExecutableCatalog): Option[CupOfPlenty] =
    CatalogCards.relic(catalog, id).map(new CupOfPlenty(_, catalog))
