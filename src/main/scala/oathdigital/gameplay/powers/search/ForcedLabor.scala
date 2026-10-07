package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.{PowerAccess, SiteRulers}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogResolution, RulerToll}
import oathdigital.model._

object ForcedLaborCard extends Denizen(DenizenId("112"), "Forced Labor", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.forced-labor"),
    persistent = true, cost = Cost.free,
    text = "Enemies **cannot** search if their pawn is at any site " +
      "ruled by Forced Labor's ruler unless they give [favor] to " +
      "its ruler. _(Give it to Chancellor if Empire, burn it if " +
      "bandits.)_")
  val powers: Vector[PrintedPower] = Vector(power)

/** Forced Labor (card 112, site-only), a persistent rule of a faceup site
  * card: an enemy of Forced Labor's ruler whose pawn stands at a site that
  * ruler rules gives the ruler 1 favor, or burns it when bandits rule, on top
  * of a Search's cost ([[RulerToll]]). The ruler is exempt.
  *
  * The payment is required and comes before the Search's Supply, so a
  * searcher who cannot pay is refused and the Search is not offered. Its line
  * reads the payment's step.
  */
final case class ForcedLabor private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = ForcedLaborCard.id
  def id: PowerId = ForcedLabor.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  override def noteKeys: Vector[NoteKey] = RulerToll.keys

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchCost -> Vector(Transform((ctx, operations) =>
      toll(ctx).fold(operations)(_ ++ operations))))

  override def applicable(ctx: PowerCtx): Boolean = toll(ctx).nonEmpty

  /** The payment this Search owes and its line, if it owes one. */
  private def toll(ctx: PowerCtx): Option[Vector[Operation]] = for
    ruler <- RulerToll.enemyRuler(ctx.state, cardId, ctx.activePlayer)
    site <- PowerAccess.pawnSite(ctx.state, ctx.activePlayer)
    if SiteRulers.rulerOf(ctx.state, site).contains(ruler)
  yield Vector(RulerToll.payment(ctx.activePlayer, ruler),
    Note(id, RulerToll.note(cardId, ctx.activePlayer, ruler)))

object ForcedLabor:
  val id: PowerId = ForcedLaborCard.power.id

  def forCatalog(catalog: ExecutableCatalog): ForcedLabor =
    new ForcedLabor(catalog)
