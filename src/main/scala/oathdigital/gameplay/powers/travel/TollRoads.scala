package oathdigital.gameplay.powers.travel

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform, Waiver}
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
  *
  * A Travel another player's power sent to its site (catalog batch 8,
  * slice 15) asks nothing: the sender settled the toll before sending it
  * (`TollRoads.sending`). The traveller pays if they agreed to
  * (`TollRoads.payerKey`); otherwise the ruler waived it. Under bandits the
  * favor is burnt, as for any Travel.
  */
final case class TollRoads private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = TollRoadsCard.id
  def id: PowerId = TollRoads.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  override def noteKeys: Vector[NoteKey] = TollRoads.noteKeys
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
    ruler <- TollRoads.owed(ctx.state, route.player, route.destination)
  yield
    val paid = Vector(payment(route.player, ruler),
      Note(id, paidNote(route.player, ruler)))
    ruler match
      case SiteRuler.Player(owner) if sent(ctx.state, route) =>
        // A sent Travel asks nothing (catalog batch 8, slice 15, ruling
        // 11): its sender settled the toll before sending it. A sender who
        // rules Toll Roads and waived it says so in the entry.
        if agreed(ctx.state, route) && !waivedBySender(ctx.state, route)
        then TravelRoute.beforeMove(operations, paid)
        else TravelRoute.beforeMove(operations, Vector(Note(id, _ =>
          Some(TollRoads.waived(PowerSourceRef.Card(cardId),
            NoteArg.Player(route.player), NoteArg.Player(owner))))))
      case SiteRuler.Player(owner) =>
        TollRoads.step.question(route.player, route.destination.value,
          "Toll Roads: give 1 favor to travel there?",
          RulerToll.payOption(ctx.state, route.player, owner,
            RulerToll.favorTaken(operations, route.player)),
          "Travel without paying") +:
          TravelRoute.beforeMove(operations, Vector(TollRoads.step.outcome(
            paid, Vector(Note(id, _ => Some(TollRoads.waived(
              PowerSourceRef.Card(cardId), NoteArg.Player(route.player),
              NoteArg.Player(owner))))))))
      case _ => TravelRoute.beforeMove(operations, paid)

  /** Whether this Travel was sent to its destination by another player's
    * power. */
  private def sent(ready: ReadyGame, route: TravelRoute.PawnMove): Boolean =
    ready.game.current.walkerEntry.exists(entry =>
      entry.procedure == ActionRef.Travel && entry.actor == route.player &&
        entry.sender.nonEmpty && entry.startArgs.headOption.contains(
          DecisionOptionRef.Site(route.destination)))

  /** Whether the entry carries the free option after its destination: the
    * sender ruling Toll Roads waived the toll. The entry runs at the front
    * of the queue, right after the sender's walk, and a start's arguments
    * must equal its entry's, so the sender still rules it. */
  private def waivedBySender(ready: ReadyGame, route: TravelRoute.PawnMove)
      : Boolean = ready.game.current.walkerEntry.exists(
    _.startArgs.contains(TollRoads.free(route.destination)))

  private def agreed(ready: ReadyGame, route: TravelRoute.PawnMove)
      : Boolean =
    ready.consent.answer(TollRoads.payerKey(route.player, route.destination),
      Some(route.player), ConsentTurn.of(ready))
      .contains(ConsentAnswer.Granted)

  private def payment(traveller: PlayerId, ruler: SiteRuler): CoreOperation =
    ruler match
      case SiteRuler.Player(owner) => Give(Piece.Favor(TollRoads.Favor),
        traveller, Location.PlayArea(traveller), Location.PlayArea(owner),
        required = true)
      case _ => PayCost(traveller, Location.SharedBank,
        Cost(favorBurnt = TollRoads.Favor))

  /** What the payment's step took from the traveller. */
  private def paidNote(traveller: PlayerId, ruler: SiteRuler)(
      states: NoteStates): Option[LogNote] = for
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
  /** Exposed: other powers write this power's lines and include this
    * set to declare them. */
  val notes: NoteSet = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "{Red} paid {1 favor} to {Blue}." */
  val paid: NoteKey = notes.key("paid", Vector(NotePart.Arg(0),
    NotePart.Text(" paid "), NotePart.Arg(1), NotePart.Text(" to "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} burned {1 favor}.", when bandits rule. */
  val burned: NoteKey = notes.key("burned", Vector(NotePart.Arg(0),
    NotePart.Text(" burned "), NotePart.Arg(1), NotePart.Text(".")))
  val decisionId: String = "power.toll-roads.toll"
  /** "{Red} paid nothing, with {Blue}'s consent." */
  val waived: NoteKey = notes.key("waived", Vector(NotePart.Arg(0),
    NotePart.Text(" paid nothing, with "), NotePart.Arg(1),
    NotePart.Text("'s consent.")))
  /** "Cannot travel to its ruler's sites without giving favor.": the reason
    * of the free option once its ruler refused. */
  val noFreeTravel: NoteKey = notes.key("no-free-travel", Vector(NotePart.Text(
    "Cannot travel to its ruler's sites without giving favor.")))
  /** The free option's reason while its ruler may be asked. */
  val noFreeTravelConsent: NoteKey = notes.key("no-free-travel-consent",
    Vector(NotePart.Text("Cannot travel to its ruler's sites without " +
      "giving favor or ruler's consent.")))
  val step: PayOrAsk = PayOrAsk(id, TollRoadsCard.id, decisionId,
    noFreeTravel, noFreeTravelConsent)

  /** The free option of a Travel to `destination`. */
  def free(destination: SiteId): DecisionOptionRef.Button =
    PayOrAsk.free(destination.value)

  /** "{Blue} must agree to give {1 favor} to {Red}, or {Red} must waive
    * it." */
  val sentEither: NoteKey = notes.key("sent-either", Vector(NotePart.Arg(0),
    NotePart.Text(" must agree to give "), NotePart.Arg(1),
    NotePart.Text(" to "), NotePart.Arg(2), NotePart.Text(", or "),
    NotePart.Arg(2), NotePart.Text(" must waive it.")))
  /** "{Blue} must agree to give {1 favor} to {Red}." */
  val sentPayer: NoteKey = notes.key("sent-payer", Vector(NotePart.Arg(0),
    NotePart.Text(" must agree to give "), NotePart.Arg(1),
    NotePart.Text(" to "), NotePart.Arg(2), NotePart.Text(".")))
  /** "{Blue} must agree to burn {1 favor}." */
  val sentBurn: NoteKey = notes.key("sent-burn", Vector(NotePart.Arg(0),
    NotePart.Text(" must agree to burn "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "{Red} must waive it." */
  val sentWaiver: NoteKey = notes.key("sent-waiver", Vector(NotePart.Arg(0),
    NotePart.Text(" must waive it.")))
  /** "{Blue} refused." */
  val sentRefused: NoteKey = notes.key("sent-refused", Vector(
    NotePart.Arg(0), NotePart.Text(" refused.")))
  /** "{Red} refused to waive it." */
  val sentWaiveRefused: NoteKey = notes.key("sent-waive-refused", Vector(
    NotePart.Arg(0), NotePart.Text(" refused to waive it.")))
  /** "{Blue} refused, and {Red} refused to waive it." */
  val sentBothRefused: NoteKey = notes.key("sent-both-refused", Vector(
    NotePart.Arg(0), NotePart.Text(" refused, and "), NotePart.Arg(1),
    NotePart.Text(" refused to waive it.")))
  /** "{Blue} has no favor for it." */
  val sentNoFavor: NoteKey = notes.key("sent-no-favor", Vector(
    NotePart.Arg(0), NotePart.Text(" has no favor for it.")))
  /** "The traveller may agree to pay." */
  val payerAsked: NoteKey = notes.key("payer-asked", Vector(NotePart.Text(
    "The traveller may agree to pay.")))

  /** Who a Travel by `traveller` to `destination` owes Toll Roads to, if
    * anyone: its ruler, when the traveller is their enemy and the ruler
    * rules the destination. */
  def owed(ready: ReadyGame, traveller: PlayerId, destination: SiteId)
      : Option[SiteRuler] = for
    ruler <- SiteRulers.rulerOfCard(ready, TollRoadsCard.id)
    if SiteRule.enemies(ruler, SiteRuler.Player(traveller))
    if SiteRulers.rulerOf(ready, destination).contains(ruler)
  yield ruler

  /** The traveller's own agreement to pay, which a sent Travel asks of them
    * (catalog batch 8, slice 15). It belongs to no card's ruler, so no
    * preference is ever set on it. */
  val payerId: PowerId = PowerId("denizen.toll-roads.payer")

  /** `traveller` agreed to pay to reach `destination`. */
  def payerKey(traveller: PlayerId, destination: SiteId): ConsentKey =
    ConsentKey(payerId, traveller, decisionId,
      DecisionOptionRef.Site(destination))

  /** The ruler let `traveller` reach `destination` free: the key of the
    * free option of `traveller`'s own toll step there. */
  def freeKey(traveller: PlayerId, destination: SiteId): ConsentKey =
    ConsentKey(id, traveller, decisionId, free(destination))

  /** The payer's entry for the consent registry: named for Toll Roads,
    * with nobody who rules it. */
  val payerWaiver: (PowerId, Waiver) = payerId -> Waiver(_ => None,
    PowerSourceRef.Card(TollRoadsCard.id), (_, _) => payerAsked(
      PowerSourceRef.Card(TollRoadsCard.id)))

  /** What a Travel `sender` sends `traveller` on to `destination` waits on
    * for Toll Roads. */
  enum Sending:
    /** Nothing is owed. */
    case NotOwed
    /** The sender rules Toll Roads: they choose to take or waive. */
    case SenderRules
    /** The traveller agreed to pay, or the ruler waived it. */
    case Settled
    /** Any one of `alternatives` would settle it. */
    case Asks(alternatives: Vector[Alternative], hover: LogNote)
    /** Nobody may settle it. */
    case Barred(reason: LogNote)

  /** One way to settle it: `granter` consenting to `power` under `key`. */
  final case class Alternative(granter: PlayerId, power: PowerId,
      key: ConsentKey)

  /** Whether `traveller` holds the favor the toll takes. */
  def canPay(ready: ReadyGame, traveller: PlayerId): Boolean =
    ready.game.current.players.find(_.player == traveller)
      .fold(0)(_.board.favor) >= Favor

  def sending(ready: ReadyGame, traveller: PlayerId, destination: SiteId,
      sender: PlayerId): Sending =
    owed(ready, traveller, destination) match
      case None => Sending.NotOwed
      case Some(SiteRuler.Player(owner)) if owner == sender =>
        Sending.SenderRules
      case Some(ruler) =>
        val turn = ConsentTurn.of(ready)
        val owner = ruler match
          case SiteRuler.Player(player) => Some(player)
          case _ => None
        val pays = ready.consent.answer(payerKey(traveller, destination),
          Some(traveller), turn)
        val waives = owner.flatMap(by => ready.consent.answer(
          freeKey(traveller, destination), Some(by), turn))
        if pays.contains(ConsentAnswer.Granted) ||
            waives.contains(ConsentAnswer.Granted) then Sending.Settled
        else unsettled(traveller, destination, owner,
          canPay(ready, traveller), pays, waives)

  /** Who may still settle an unsettled toll, and how it is worded. */
  private def unsettled(traveller: PlayerId, destination: SiteId,
      owner: Option[PlayerId], favor: Boolean,
      pays: Option[ConsentAnswer], waives: Option[ConsentAnswer]): Sending =
    val card = PowerSourceRef.Card(TollRoadsCard.id)
    val payer = Option.when(favor && !pays.contains(ConsentAnswer.Refused))(
      Alternative(traveller, payerId, payerKey(traveller, destination)))
    val waiver = owner.filterNot(_ => waives.contains(ConsentAnswer.Refused))
      .map(by => Alternative(by, id, freeKey(traveller, destination)))
    val them = NoteArg.Player(traveller)
    val amount = NoteArg.Amount(Favor, NoteUnit.Favor)
    (payer, waiver, owner) match
      case (Some(_), Some(_), Some(by)) => Sending.Asks(
        Vector(payer, waiver).flatten,
        sentEither(card, them, amount, NoteArg.Player(by)))
      case (Some(_), None, Some(by)) => Sending.Asks(payer.toVector,
        sentPayer(card, them, amount, NoteArg.Player(by)))
      case (Some(_), _, None) => Sending.Asks(payer.toVector,
        sentBurn(card, them, amount))
      case (None, Some(_), Some(by)) => Sending.Asks(waiver.toVector,
        sentWaiver(card, NoteArg.Player(by)))
      case (_, _, Some(by)) if pays.contains(ConsentAnswer.Refused) &&
          waives.contains(ConsentAnswer.Refused) =>
        Sending.Barred(sentBothRefused(card, them, NoteArg.Player(by)))
      case (_, _, Some(by)) =>
        Sending.Barred(sentWaiveRefused(card, NoteArg.Player(by)))
      case _ if pays.contains(ConsentAnswer.Refused) =>
        Sending.Barred(sentRefused(card, them))
      case _ => Sending.Barred(sentNoFavor(card, them))

  def forCatalog(catalog: ExecutableCatalog): TollRoads =
    new TollRoads(catalog)
