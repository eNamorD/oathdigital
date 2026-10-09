package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogResolution, PayOrAsk,
  PowerAnswers, RuledCards, RulerToll}
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
  * The toll runs after the rest of the Trade's cost and reads the Trade's
  * source when it runs. Under bandits it is a required batch placed for
  * every Trade by an enemy. When a player rules Curfew, the trader is asked
  * first to pay or to trade without paying, which its ruler may consent to
  * (consent slice 4), only for a source that ruler rules. A trader who
  * cannot pay and is not let off is refused, and the restriction look-ahead
  * hides that source. Its line reads the payment's step.
  */
final case class Curfew private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = CurfewCard.id
  def id: PowerId = Curfew.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  override def noteKeys: Vector[NoteKey] = RulerToll.keys ++
    Vector(Curfew.noFreeTrade, Curfew.noFreeTradeConsent)
  override def narratedDecisions: Set[String] = Set(Curfew.decisionId)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeCost -> Vector(Transform((ctx, operations) =>
      RulerToll.enemyRuler(ctx.state, cardId, ctx.activePlayer)
        .fold(operations)(charged(ctx.activePlayer, _, operations)))),
    PowerWindow.PayOrAsk -> Vector(Curfew.step.restriction))

  override def applicable(ctx: PowerCtx): Boolean =
    if ctx.window == PowerWindow.PayOrAsk then Curfew.step.asking(ctx)
    else RulerToll.enemyRuler(ctx.state, cardId, ctx.activePlayer).nonEmpty

  /** `operations` with what a Trade with a card `ruler` rules owes, read
    * against the Trade's source when it runs: the step first and its outcome
    * last when a player rules Curfew (the outcome runs nothing when the step
    * was not asked), the payment and its line last under
    * bandits. */
  private def charged(trader: PlayerId, ruler: SiteRuler,
      operations: Vector[Operation]): Vector[Operation] = ruler match
    case SiteRuler.Player(owner) =>
      val paid = Vector(RulerToll.payment(trader, ruler),
        Note(id, RulerToll.note(cardId, trader, ruler)))
      val waived = Vector(Note(id, _ => Some(RulerToll.waived(
        Curfew.step.source, NoteArg.Player(owner)))))
      Branch((ready, pending) => tradedWith(ready, ruler, pending).toVector
        .map(source => Curfew.step.question(trader, source.wireId,
          "Curfew: give 1 favor to trade with this card?",
          RulerToll.payOption(ready, trader, owner,
            RulerToll.favorTaken(operations, trader)),
          "Trade without paying"))) +: operations :+
        Curfew.step.outcome(paid, waived)
    case _ => operations ++ Vector(
      BuildOps((ready, pending) => Right(tradedWith(ready, ruler, pending)
        .map(_ => RulerToll.payment(trader, ruler)).toVector),
        required = true),
      Note(id, RulerToll.note(cardId, trader, ruler)))

  /** The Trade's source, when it is a card `ruler` rules. */
  private def tradedWith(ready: ReadyGame, ruler: SiteRuler,
      pending: PendingTree): Option[DecisionOptionRef] =
    PowerAnswers.one(pending, TradeProcedure.decisionId).filter(ref =>
      cardOf(ref).exists(ruled(ready, ruler, _)))

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
  val decisionId: String = "power.curfew.toll"
  /** "Cannot trade with its ruler's cards without giving favor.": the
    * reason of the free option once its ruler refused. */
  val noFreeTrade: NoteKey = NoteKey("no-free-trade", Vector(NotePart.Text(
    "Cannot trade with its ruler's cards without giving favor.")))
  /** The free option's reason while its ruler may be asked. */
  val noFreeTradeConsent: NoteKey = NoteKey("no-free-trade-consent",
    Vector(NotePart.Text("Cannot trade with its ruler's cards without " +
      "giving favor or ruler's consent.")))
  val step: PayOrAsk = PayOrAsk(id, CurfewCard.id, decisionId, noFreeTrade,
    noFreeTradeConsent)

  /** The free option of a Trade with `source`. */
  def free(source: DecisionOptionRef): DecisionOptionRef.Button =
    PayOrAsk.free(source.wireId)

  def forCatalog(catalog: ExecutableCatalog): Curfew = new Curfew(catalog)
