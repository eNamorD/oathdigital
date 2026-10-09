package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.{PowerAccess, SiteRulers}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object MapLibraryCard extends Denizen(DenizenId("76"), "Map Library", Suit.Arcane) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.map-library"),
    persistent = false, cost = Cost.free,
    text = "While your pawn is at this site, you may trade with a card " +
      "at any site in your region.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Map Library (card 76, site-only), a selected Trade modifier with no cost,
  * selectable only while its user's pawn stands at Map Library's site: the
  * Trade may draw on any card at any site in the pawn's region, the pawn's
  * own site and edifices included.
  *
  * It adds those cards to the source decision, after the pawn site's
  * ([[EconomySources.widened]]). The token-free rule still applies. Skilled
  * Merchants adds the same cards, so with both selected each is offered
  * once. It writes no line: the start line names it, and the Trade's own
  * line names the source.
  */
final case class MapLibrary private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = MapLibraryCard.id
  def id: PowerId = MapLibrary.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeSourceSelection -> Vector(Transform((ctx, operations) =>
      EconomySources.widened(operations,
        EconomySources.inPawnRegion(ctx.state, ctx.activePlayer)))))

  /** The rulebook's access, and the pawn at Map Library's site: ruling the
    * site is not enough. */
  override def selectable(ready: ReadyGame, actor: PlayerId): Boolean =
    super.selectable(ready, actor) &&
      PowerAccess.pawnSite(ready, actor).exists(site =>
        SiteRulers.siteOf(ready, cardId).contains(site))

object MapLibrary:
  val id: PowerId = MapLibraryCard.power.id

  def forCatalog(catalog: ExecutableCatalog): MapLibrary =
    new MapLibrary(catalog)
