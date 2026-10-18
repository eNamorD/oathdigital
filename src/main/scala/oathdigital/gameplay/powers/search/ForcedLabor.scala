package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.{PowerAccess, SiteRulers}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogResolution, PayOrAsk, RulerToll}
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
  * The payment is required and comes after the Search's Supply, so a
  * searcher who cannot pay is refused and the Search is not offered. When a
  * player rules it, the searcher is asked first to pay or to search without
  * paying, which its ruler may consent to (consent slice 4). Its line reads
  * the payment's step.
  */
final case class ForcedLabor private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = ForcedLaborCard.id
  def id: PowerId = ForcedLabor.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  override def noteKeys: Vector[NoteKey] = ForcedLabor.noteKeys
  override def narratedDecisions: Set[String] = Set(ForcedLabor.decisionId)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchCost -> Vector(Transform((ctx, operations) =>
      charged(ctx, operations).getOrElse(operations))),
    PowerWindow.PayOrAsk -> Vector(ForcedLabor.step.restriction))

  override def applicable(ctx: PowerCtx): Boolean =
    if ctx.window == PowerWindow.PayOrAsk then ForcedLabor.step.asking(ctx)
    else charged(ctx, Vector.empty).nonEmpty

  /** `operations` with what this Search owes, if it owes anything. */
  private def charged(ctx: PowerCtx, operations: Vector[Operation])
      : Option[Vector[Operation]] = for
    ruler <- RulerToll.enemyRuler(ctx.state, cardId, ctx.actor)
    site <- PowerAccess.pawnSite(ctx.state, ctx.actor)
    if SiteRulers.rulerOf(ctx.state, site).contains(ruler)
  yield RulerToll.owed(ForcedLabor.step, ctx.state, ctx.actor, ruler,
    operations, site.value, "Forced Labor: give 1 favor to search here?",
    "Search without paying")

object ForcedLabor:
  val id: PowerId = ForcedLaborCard.power.id
  val decisionId: String = "power.forced-labor.toll"
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Cannot search at its ruler's sites without giving favor.": the reason
    * of the free option once its ruler refused. */
  val noFreeSearch: NoteKey = notes.key("no-free-search", Vector(NotePart.Text(
    "Cannot search at its ruler's sites without giving favor.")))
  /** The free option's reason while its ruler may be asked. */
  val noFreeSearchConsent: NoteKey = notes.key("no-free-search-consent",
    Vector(NotePart.Text("Cannot search at its ruler's sites without " +
      "giving favor or ruler's consent.")))
  notes.include(RulerToll.notes)
  val step: PayOrAsk = PayOrAsk(id, ForcedLaborCard.id, decisionId,
    noFreeSearch, noFreeSearchConsent)

  /** The free option of a Search from `site`. */
  def free(site: SiteId): DecisionOptionRef.Button = PayOrAsk.free(site.value)

  def forCatalog(catalog: ExecutableCatalog): ForcedLabor =
    new ForcedLabor(catalog)
