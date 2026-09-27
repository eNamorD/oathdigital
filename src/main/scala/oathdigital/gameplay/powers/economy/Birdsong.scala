package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.Contribution
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** Birdsong (card 176, adviser-only), a selected Trade modifier with no cost:
  * trading with a beast or nomad card spends no Supply. It may be selected
  * whatever the card, as the Cup of Plenty may. The waiver is
  * [[SupplyWaiver]].
  */
final case class Birdsong private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier:
  def id: PowerId = Birdsong.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)

  def effects: Map[PowerWindow, Vector[Contribution]] = SupplyWaiver.effects(
    PowerWindow.TradeCost, TradeProcedure.decisionId, catalog)(
    (_, _, suit) => Birdsong.Suits(suit))

object Birdsong:
  val id: PowerId = PowerId("denizen.birdsong")
  val Suits: Set[Suit] = Set(Suit.Beast, Suit.Nomad)

  def forCatalog(catalog: ExecutableCatalog): Option[Birdsong] =
    CatalogCards.denizen(catalog, id).map(new Birdsong(_, catalog))
