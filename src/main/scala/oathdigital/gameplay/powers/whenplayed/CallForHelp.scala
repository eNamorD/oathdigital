package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog, Locked,
  PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts, PowerAnswers}
import oathdigital.model._

object CallForHelpCard extends Denizen(DenizenId("246"), "Call for Help", Suit.Nomad) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.call-for-help"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** reveal and discard up to 6 cards from your " +
      "region's discard pile. Gain 2 warbands for each revealed " +
      "[suit-nomad] card.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Call for Help (card 246, adviser-only, locked), WHEN PLAYED: reveal and
  * discard up to 6 cards from your region's discard pile. Gain 2 warbands
  * for each revealed Nomad card.
  *
  * Your region is the pawn's. The actor draws in passes of a `Repeat`. Each
  * pass asks for an amount from 0 to the smaller of what is left of 6 and
  * the pile's size, then moves that many cards off the top, one at a time,
  * each to the top of the next region's pile, so the first drawn ends
  * deepest. Each card is revealed there, so every player knows it: a card
  * entering a pile is forgotten, so the reveal follows the move. The actor
  * gains 2 warbands per Nomad card drawn, up to their bank, in the same
  * step. While fewer than 6 are drawn, the pile is not empty and the pass's
  * amount was not 0, the actor chooses "Keep drawing", which runs another
  * pass, or "Stop".
  *
  * One line per draw covers that draw's Revealed lines. With nothing drawn,
  * one line says so.
  */
final case class CallForHelp private (catalog: ExecutableCatalog)
    extends WhenPlayedPower:
  val cardId: DenizenId = CallForHelpCard.id
  import CallForHelp._
  def id: PowerId = CallForHelp.id

  override def noteKeys: Vector[NoteKey] = CallForHelp.noteKeys

  private def cardSource: PowerSourceRef = PowerSourceRef.Card(cardId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    Vector(
      Repeat((_, pending) => drawing(pending.answered),
        Sequence(Vector[Operation](
          Branch((live, pending) => askAmount(live, ctx.actor, pending)),
          BuildOps((live, pending) => Right(draw(live, ctx.actor, pending))),
          Note(id, drawnNote(_, ctx.actor), covers = true),
          Branch((live, pending) => askContinue(live, ctx.actor, pending))))),
      Note(id, nothingNote))

  /** Each pass's amount, in order. */
  private def amounts(answered: Vector[Answered]): Vector[Int] =
    PowerAnswers.amounts(answered, amountDecisionId)

  /** Whether another pass runs: none has yet, or the actor chose to keep
    * drawing after the latest one. */
  private def drawing(answered: Vector[Answered]): Boolean =
    val passes = amounts(answered).size
    passes == 0 ||
      (answered.count(_.decisionId == continueDecisionId) == passes &&
        PowerAnswers.last(answered, continueDecisionId).contains(keep))

  private def home(ready: ReadyGame, actor: PlayerId): Option[Region] =
    PowerAccess.pawnSite(ready, actor).flatMap(ready.game.current.map.regionOf)

  private def pile(ready: ReadyGame, actor: PlayerId): Vector[WorldCardId] =
    home(ready, actor).fold(Vector.empty[WorldCardId])(
      ready.game.current.commonCards.discard)

  private def askAmount(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    val most = math.min(Most - amounts(pending.answered).sum,
      pile(ready, actor).size)
    if most <= 0 then Vector.empty
    else Vector(Decide(amountDecisionId, actor, DecisionQuery.ChooseAmount(0,
      most, Some("Call for Help: reveal and discard cards from your " +
        "region's discard pile"), "Reveal and discard")))

  /** The pass's cards off the top of the pile, top first, each moved to the
    * top of the next region's pile and revealed there, and the warbands
    * their Nomad cards give. Nothing for an amount of 0. */
  private def draw(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Vector[CoreOperation] =
    amounts(pending.answered).lastOption.filter(_ > 0)
      .zip(home(ready, actor)).toVector.flatMap { (amount, region) =>
        val next = CardPlay.nextRegion(region)
        val drawn = ready.game.current.commonCards.discard(region)
          .takeRight(amount).reverse
        drawn.flatMap(card => Vector[CoreOperation](
          Move(Piece.Card(card),
            PositionedLocation(Location.RegionalDiscard(region)),
            PositionedLocation(Location.RegionalDiscard(next),
              StackPosition.Top)),
          Reveal(card, Location.RegionalDiscard(next)))) ++
          gain(ready, actor, drawn)
      }

  /** 2 warbands per Nomad card drawn, up to the actor's bank. */
  private def gain(ready: ReadyGame, actor: PlayerId,
      drawn: Vector[WorldCardId]): Vector[CoreOperation] =
    val earned = PerNomad *
      drawn.count(card => catalog.suitOf(card).contains(Suit.Nomad))
    PlayerFacts.forceKind(ready, actor).toOption.toVector.flatMap { kind =>
      val amount = math.min(earned, PlayerFacts.banked(ready, kind))
      Option.when(amount > 0)(Gain.Warbands(actor, kind, amount)) }

  /** "Keep drawing" or "Stop", while fewer than 6 are drawn, the pile holds
    * cards and the pass drew some. */
  private def askContinue(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    val drawn = amounts(pending.answered)
    if drawn.lastOption.forall(_ == 0) || drawn.sum >= Most ||
        pile(ready, actor).isEmpty then Vector.empty
    else Vector(Decide(continueDecisionId, actor, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Button(keep, "Keep drawing"),
        DecisionOption.Button(stop, "Stop")),
      heading = Some(s"Call for Help: ${drawn.sum} of $Most drawn"))))

  /** The cards the step before drew, read from the pile before it, and the
    * warbands it gave. */
  private def drawnNote(states: NoteStates, actor: PlayerId)
      : Option[LogNote] = for
    amount <- amounts(states.answered).lastOption
    if amount > 0
    step <- states.previous
    region <- home(step._1, actor)
  yield
    val cards = NoteArg.Cards(step._1.game.current.commonCards
      .discard(region).takeRight(amount).reverse)
    val gained = NoteSupport.warbands(step, actor)
    if gained > 0 then revealed(cardSource, cards,
      NoteArg.Amount(gained, NoteUnit.Warband))
    else discarded(cardSource, cards)

  /** "Nothing was revealed." when no pass drew a card. */
  private def nothingNote(states: NoteStates): Option[LogNote] =
    Option.when(amounts(states.answered).sum == 0)(none(cardSource))

object CallForHelp:
  val id: PowerId = CallForHelpCard.power.id
  val amountDecisionId: String = "cardplay.call-for-help.amount"
  val continueDecisionId: String = "cardplay.call-for-help.continue"
  /** The most cards drawn in all. */
  val Most: Int = 6
  /** The warbands each Nomad card drawn gives. */
  val PerNomad: Int = 2
  val keep: DecisionOptionRef.Button = DecisionOptionRef.Button("keep-drawing")
  val stop: DecisionOptionRef.Button = DecisionOptionRef.Button("stop")
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Revealed and discarded {cards}, and gained {n warbands}." */
  val revealed: NoteKey = notes.key("revealed", Vector(
    NotePart.Text("Revealed and discarded "), NotePart.Arg(0),
    NotePart.Text(", and gained "), NotePart.Arg(1), NotePart.Text(".")))
  /** "Revealed and discarded {cards}." */
  val discarded: NoteKey = notes.key("discarded", Vector(
    NotePart.Text("Revealed and discarded "), NotePart.Arg(0),
    NotePart.Text(".")))
  /** "Nothing was revealed." */
  val none: NoteKey = notes.key("none", Vector(
    NotePart.Text("Nothing was revealed.")))

  def forCatalog(catalog: ExecutableCatalog): CallForHelp =
    new CallForHelp(catalog)
