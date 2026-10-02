package oathdigital.gameplay.powers.travel

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogResolution, NoteSupport}
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
  * The payment is a required operation placed before the pawn's move, so a
  * traveller who cannot pay is rejected, and Travel's destination list does not
  * offer that destination. The ruler is exempt.
  *
  * The payment writes "{Red} paid 1 favor to {Blue}.", or "{Red} burned 1
  * favor." when bandits rule, read from the payment's step.
  */
final case class TollRoads private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = TollRoadsCard.id
  def id: PowerId = TollRoads.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  override def noteKeys: Vector[NoteKey] = Vector(TollRoads.paid, TollRoads.burned)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      toll(ctx).fold(operations)(_ ++ operations))))

  override def applicable(ctx: PowerCtx): Boolean = toll(ctx).nonEmpty

  /** The payment this Travel owes and its line, if it owes one. */
  private def toll(ctx: PowerCtx): Option[Vector[Operation]] = for
    route <- TravelRoute.pawnMove(ctx.operation)
    ruler <- SiteRulers.rulerOfCard(ctx.state, cardId)
    if SiteRule.enemies(ruler, SiteRuler.Player(route.player))
    if SiteRulers.rulerOf(ctx.state, route.destination).contains(ruler)
  yield Vector(payment(route.player, ruler),
    Note(id, paidNote(route.player, ruler)))

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

  def forCatalog(catalog: ExecutableCatalog): TollRoads =
    new TollRoads(catalog)
