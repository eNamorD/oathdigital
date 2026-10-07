package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogResolution, PowerAnswers,
  RuledCards, RulerToll}
import oathdigital.model._

object CurfewCard extends Denizen(DenizenId("119"), "Curfew", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.curfew"),
    persistent = true, cost = Cost.free,
    text = "Enemies **cannot** trade with cards ruled by Curfew's ruler " +
      "unless they give [favor] to its ruler. _(Give it to " +
      "Chancellor if Empire, burn it if bandits.)_")
  val powers: Vector[PrintedPower] = Vector(power)

/** Curfew (card 119, site-only), a persistent rule of a faceup site card: an
  * enemy of Curfew's ruler who trades with a card that ruler rules, an
  * adviser included, gives the ruler 1 favor, or burns it when bandits rule,
  * on top of the Trade's cost ([[RulerToll]]). The ruler is exempt.
  *
  * The toll is a required batch placed before the Trade's cost, so a trader
  * who cannot pay is refused, and the restriction look-ahead hides that
  * source. The batch reads the Trade's source when it runs and is placed for
  * every Trade by an enemy, so the cost node's shape never depends on the
  * answer. Its line reads the payment's step.
  */
final case class Curfew private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = CurfewCard.id
  def id: PowerId = Curfew.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  override def noteKeys: Vector[NoteKey] = RulerToll.keys

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeCost -> Vector(Transform((ctx, operations) =>
      RulerToll.enemyRuler(ctx.state, cardId, ctx.activePlayer)
        .fold(operations)(ruler => Vector(toll(ctx.activePlayer, ruler),
          Note(id, RulerToll.note(cardId, ctx.activePlayer, ruler))) ++
          operations))))

  override def applicable(ctx: PowerCtx): Boolean =
    RulerToll.enemyRuler(ctx.state, cardId, ctx.activePlayer).nonEmpty

  /** The payment, when the Trade's source is a card `ruler` rules. */
  private def toll(trader: PlayerId, ruler: SiteRuler): Operation =
    BuildOps((ready, pending) => Right(
      PowerAnswers.one(pending, TradeProcedure.decisionId).flatMap(cardOf)
        .filter(ruled(ready, ruler, _))
        .map(_ => RulerToll.payment(trader, ruler)).toVector),
      required = true)

  private def cardOf(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(card) => Some(card)
    case DecisionOptionRef.Edifice(card) => Some(card)
    case _ => None

  /** Whether `ruler` rules `card`: one of its faceup advisers, or a card at
    * a site it rules. */
  private def ruled(ready: ReadyGame, ruler: SiteRuler, card: CardId)
      : Boolean = catalog.suitOf(card).exists(suit =>
    RuledCards.of(catalog, ready, ruler, suit).contains(card))

object Curfew:
  val id: PowerId = CurfewCard.power.id

  def forCatalog(catalog: ExecutableCatalog): Curfew = new Curfew(catalog)
