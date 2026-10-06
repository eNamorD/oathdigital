package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers,
  SelectedModifier, SiteRoom}
import oathdigital.model._

object MovingMarketCard extends Denizen(DenizenId("241"), "Moving Market", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.moving-market"),
    persistent = false, cost = Cost.free,
    text = "After trading with Moving Market, you may move this card to " +
      "any site with capacity. If you do, gain [favor] from the " +
      "[suit-nomad] bank.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Moving Market (card 241, site-only), a selected Trade modifier with no
  * cost: after trading with Moving Market, the player may move it to any
  * site with capacity, and if they do, gains 1 favor from the Nomad bank.
  *
  * One `Branch` is appended to the Trade's root, after the source, cost and
  * gain, as Knights Errant appends to a Muster's. A decision inside the
  * Economy `Branch` would be re-derived through `MusterSource.resolve`,
  * which refuses a source holding the Trade's tokens.
  *
  * The `Branch` asks only when Moving Market was the Trade's source and
  * another site in play has room. Its one question offers every empty slot
  * at the other sites in play, in map then slot order, then Skip. A chosen
  * slot moves the card to that site with its tokens, the Trade's included;
  * it takes the site's next slot, since a row has no gaps. The move is a
  * required batch. The gain is its own optional batch, so an empty bank
  * never rejects the move, and the line then names only the move. Skip
  * writes nothing.
  */
final case class MovingMarket private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  import MovingMarket._

  val cardId: DenizenId = MovingMarketCard.id
  def id: PowerId = MovingMarket.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)
  override def noteKeys: Vector[NoteKey] = Vector(moved, movedOnly)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeActionEligibility -> Vector(Transform((ctx, children) =>
      children :+ offer(ctx.activePlayer))))

  private def offer(actor: PlayerId): Operation = Branch((ready, pending) =>
    val slots =
      if tradedWith(pending) then openSlots(ready) else Vector.empty
    if slots.isEmpty then Vector.empty
    else Vector(
      Decide(decisionId, actor, DecisionQuery.ChooseOne(
        slots.map(DecisionOption.SiteSlot(_)) :+
          DecisionOption.Button(skip, "Skip"),
        heading = Some("Moving Market: move it to another site?"))),
      BuildOps((live, answered) => move(live, answered), required = true),
      BuildOps((live, answered) => gain(live, actor, answered)),
      Note(id, movedNote(_, actor))))

  private def tradedWith(pending: PendingTree): Boolean =
    PowerAnswers.one(pending, TradeProcedure.decisionId)
      .contains(DecisionOptionRef.Denizen(cardId))

  /** Every empty slot at the other sites in play, in map then slot order. */
  private def openSlots(ready: ReadyGame): Vector[DecisionOptionRef.SiteSlot] =
    SiteRulers.siteOf(ready, cardId).toVector.flatMap(here =>
      ready.game.current.map.inPlay.filter(_ != here).flatMap(site =>
        SiteRoom.emptySlots(catalog, ready, site)
          .map(DecisionOptionRef.SiteSlot(site, _))))

  private def move(ready: ReadyGame, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case Some(`skip`) => Right(Vector.empty)
      case Some(slot: DecisionOptionRef.SiteSlot) => (for
          here <- SiteRulers.siteOf(ready, cardId)
          if openSlots(ready).contains(slot)
        yield Vector[CoreOperation](Move(Piece.Card(cardId),
          PositionedLocation(Location.Site(here)),
          PositionedLocation(Location.Site(slot.site))))).toRight(
        OathViolation.InvalidEventOrder(
          s"${slot.wireId} is not an empty slot Moving Market can move to"))
      case Some(other) => Left(OathViolation.InvalidEventOrder(
        s"${other.wireId} is not a slot or Skip"))
      case None => Left(PowerAnswers.missing(decisionId))

  /** The favor, once the card stands at the chosen slot's site. */
  private def gain(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    Right(PowerAnswers.one(pending, decisionId).collect {
      case slot: DecisionOptionRef.SiteSlot
          if SiteRulers.siteOf(ready, cardId).contains(slot.site) =>
        Gain.Favor(actor, Suit.Nomad, Gained)
    }.toVector)

  /** Written when the card stands at the chosen slot's site. The favor is
    * read from the gain's step; with none, the line names only the move. */
  private def movedNote(states: NoteStates, actor: PlayerId)
      : Option[PowerNote] = for
    case slot: DecisionOptionRef.SiteSlot <-
      NoteSupport.answer(states, decisionId)
    if SiteRulers.siteOf(states.now, cardId).contains(slot.site)
  yield
    val card = PowerSourceRef.Card(cardId)
    val gained = states.previous.fold(0)(NoteSupport.favor(_, actor))
    if gained > 0 then moved(card, NoteArg.Site(slot.site),
      NoteArg.Amount(gained, NoteUnit.Favor), NoteArg.Bank(Suit.Nomad))
    else movedOnly(card, NoteArg.Site(slot.site))

object MovingMarket:
  val id: PowerId = MovingMarketCard.power.id
  /** Under the `trade.` prefix, as Knights Errant's is under `muster.`. */
  val decisionId: String = "trade.moving-market.move"
  val skip: DecisionOptionRef.Button = DecisionOptionRef.Button("skip")
  /** The favor a move gains, best-effort. */
  val Gained: Int = 1
  /** "Moved it to {site} and gained {n favor} from {the Nomad bank}." */
  val moved: NoteKey = NoteKey("moved", Vector(NotePart.Text("Moved it to "),
    NotePart.Arg(0), NotePart.Text(" and gained "), NotePart.Arg(1),
    NotePart.Text(" from "), NotePart.Arg(2), NotePart.Text(".")))
  /** "Moved it to {site}." */
  val movedOnly: NoteKey = NoteKey("moved.only", Vector(
    NotePart.Text("Moved it to "), NotePart.Arg(0), NotePart.Text(".")))

  def forCatalog(catalog: ExecutableCatalog): MovingMarket =
    new MovingMarket(catalog)
