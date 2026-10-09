package oathdigital.gameplay.powers.travel

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogResolution, NoteSupport, PayOrAsk, RulerToll}
import oathdigital.model._

object TollRoadsCard extends Denizen(DenizenId("118"), "Toll Roads", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.toll-roads"),
    persistent = true, cost = Cost.free,
    text = "Enemies **cannot** travel to sites ruled by Toll Roads' " +
      "ruler unless they give [favor] to its ruler. _(Give it to " +
      "Chancellor if Empire, burn it if bandits.)_")
  val powers: Vector[PrintedPower] = Vector(power)

/** Toll Roads (card 118), a persistent rule of a faceup site card: enemies
  * cannot travel to a site ruled by Toll Roads' ruler unless they give 1 favor
  * to that ruler, or burn it when bandits rule. Toll Roads' ruler is the ruler
  * of the site it stands at, and the rule covers every site that ruler holds,
  * Toll Roads' own included. Empire rulers are not supported.
  *
  * When a player rules it, the traveller is asked first to pay or to travel
  * without paying, which its ruler may consent to ([[PayOrAsk]], consent
  * slice 4). Under bandits the favor is burnt, unasked. The payment is a
  * required operation placed after the rest of the Travel's cost and before
  * the pawn's move, so a traveller who cannot pay and is not let off is
  * rejected. The ruler is exempt.
  *
  * The payment writes "{Red} paid 1 favor to {Blue}.", or "{Red} burned 1
  * favor." when bandits rule, read from the payment's step. Travelling free
  * writes "{Red} paid nothing, with {Blue}'s consent."
  */
final case class TollRoads private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = TollRoadsCard.id
  def id: PowerId = TollRoads.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  override def noteKeys: Vector[NoteKey] = Vector(TollRoads.paid,
    TollRoads.burned, TollRoads.waived, TollRoads.noFreeTravel,
    TollRoads.noFreeTravelConsent)
  override def narratedDecisions: Set[String] = Set(TollRoads.decisionId)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      charged(ctx, operations).getOrElse(operations))),
    PowerWindow.PayOrAsk -> Vector(TollRoads.step.restriction))

  override def applicable(ctx: PowerCtx): Boolean =
    if ctx.window == PowerWindow.PayOrAsk then TollRoads.step.asking(ctx)
    else charged(ctx, Vector.empty).nonEmpty

  /** `operations` with what this Travel owes, if it owes anything: the step
    * first when a player rules Toll Roads, the payment alone when bandits
    * do. Either way the payment runs after the rest of the cost and before
    * the pawn's move ([[TravelRoute.beforeMove]]). */
  private def charged(ctx: PowerCtx, operations: Vector[Operation])
      : Option[Vector[Operation]] = for
    route <- TravelRoute.pawnMove(ctx.operation)
    ruler <- SiteRulers.rulerOfCard(ctx.state, cardId)
    if SiteRule.enemies(ruler, SiteRuler.Player(route.player))
    if SiteRulers.rulerOf(ctx.state, route.destination).contains(ruler)
  yield
    val paid = Vector(payment(route.player, ruler),
      Note(id, paidNote(route.player, ruler)))
    ruler match
      case SiteRuler.Player(owner) =>
        TollRoads.step.question(route.player, route.destination.value,
          "Toll Roads: give 1 favor to travel there?",
          RulerToll.payOption(ctx.state, route.player, owner),
          "Travel without paying") +:
          TravelRoute.beforeMove(operations, Vector(TollRoads.step.outcome(
            paid, Vector(Note(id, _ => Some(TollRoads.waived(
              PowerSourceRef.Card(cardId), NoteArg.Player(route.player),
              NoteArg.Player(owner))))))))
      case _ => TravelRoute.beforeMove(operations, paid)

  private def payment(traveller: PlayerId, ruler: SiteRuler): CoreOperation =
    ruler match
      case SiteRuler.Player(owner) => Give(Piece.Favor(TollRoads.Favor),
        traveller, Location.PlayArea(traveller), Location.PlayArea(owner),
        required = true)
      case _ => PayCost(traveller, Location.SharedBank,
        Cost(favorBurnt = TollRoads.Favor))

  /** What the payment's step took from the traveller. */
  private def paidNote(traveller: PlayerId, ruler: SiteRuler)(
      states: NoteStates): Option[PowerNote] = for
    step <- states.previous
    paid = -NoteSupport.favor(step, traveller)
    if paid > 0
  yield
    val card = PowerSourceRef.Card(cardId)
    val amount = NoteArg.Amount(paid, NoteUnit.Favor)
    ruler match
      case SiteRuler.Player(owner) => TollRoads.paid(card,
        NoteArg.Player(traveller), amount, NoteArg.Player(owner))
      case _ => TollRoads.burned(card, NoteArg.Player(traveller), amount)

object TollRoads:
  val id: PowerId = TollRoadsCard.power.id
  val Favor: Int = 1
  /** "{Red} paid {1 favor} to {Blue}." */
  val paid: NoteKey = NoteKey("paid", Vector(NotePart.Arg(0),
    NotePart.Text(" paid "), NotePart.Arg(1), NotePart.Text(" to "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} burned {1 favor}.", when bandits rule. */
  val burned: NoteKey = NoteKey("burned", Vector(NotePart.Arg(0),
    NotePart.Text(" burned "), NotePart.Arg(1), NotePart.Text(".")))
  val decisionId: String = "power.toll-roads.toll"
  /** "{Red} paid nothing, with {Blue}'s consent." */
  val waived: NoteKey = NoteKey("waived", Vector(NotePart.Arg(0),
    NotePart.Text(" paid nothing, with "), NotePart.Arg(1),
    NotePart.Text("'s consent.")))
  /** "Cannot travel to its ruler's sites without giving favor.": the reason
    * of the free option once its ruler refused. */
  val noFreeTravel: NoteKey = NoteKey("no-free-travel", Vector(NotePart.Text(
    "Cannot travel to its ruler's sites without giving favor.")))
  /** The free option's reason while its ruler may be asked. */
  val noFreeTravelConsent: NoteKey = NoteKey("no-free-travel-consent",
    Vector(NotePart.Text("Cannot travel to its ruler's sites without " +
      "giving favor or ruler's consent.")))
  val step: PayOrAsk = PayOrAsk(id, TollRoadsCard.id, decisionId,
    noFreeTravel, noFreeTravelConsent)

  /** The free option of a Travel to `destination`. */
  def free(destination: SiteId): DecisionOptionRef.Button =
    PayOrAsk.free(destination.value)

  def forCatalog(catalog: ExecutableCatalog): TollRoads =
    new TollRoads(catalog)
