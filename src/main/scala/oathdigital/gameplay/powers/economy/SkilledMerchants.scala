package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object SkilledMerchantsCard extends Denizen(DenizenId("238"), "Skilled Merchants", Suit.Hearth) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.skilled-merchants"),
    persistent = false, cost = Cost.free,
    text = "You may trade with any denizens in your region.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Skilled Merchants (card 238, adviser-only), a selected Trade modifier
  * with no cost: the Trade may draw on any card at any site in the pawn's
  * region, the pawn's own site and edifices included. A player acting as
  * if at another site draws on that site's region.
  *
  * It adds those cards to the source decision, after the pawn site's
  * ([[EconomySources.widened]]). The token-free rule still applies. Map
  * Library adds the same cards, so with both selected each is offered once.
  * It writes no line: the start line names it, and the Trade's own line
  * names the source.
  */
final case class SkilledMerchants private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = SkilledMerchantsCard.id
  def id: PowerId = SkilledMerchants.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeSourceSelection -> Vector(Transform((ctx, operations) =>
      EconomySources.widened(ctx, operations,
        EconomySources.inPawnRegion(catalog, ctx.state, ctx.activePlayer)))))

object SkilledMerchants:
  val id: PowerId = SkilledMerchantsCard.power.id

  def forCatalog(catalog: ExecutableCatalog): SkilledMerchants =
    new SkilledMerchants(catalog)
