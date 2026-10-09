package oathdigital.gameplay.powers

import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.{OptionRestriction, PowerCtx,
  Waiver}
import oathdigital.model._

/** A toll or penalty a card's ruling player may waive (consent design, "Toll
  * and penalty steps"). When a player other than the payer rules the card,
  * the payer is asked to pay or to go on without paying. The free option is
  * barred by the card's waivable restriction: it is askable until the ruler
  * answers, and choosable once they consent. The step is hooked by
  * [[PowerWindow.PayOrAsk]], where only that restriction speaks.
  *
  * The free option's key names the occasion (the destination, the pawn's
  * site, the traded card, the site left), so a grant covers that occasion
  * for the rest of the turn (ruling 12), not every toll the card charges.
  */
final case class PayOrAsk(power: PowerId, card: DenizenId,
    decisionId: String, plain: NoteKey, consent: NoteKey):
  val source: PowerSourceRef = PowerSourceRef.Card(card)

  /** `operations` with the step `payer` answers on `occasion` first and what
    * its answer runs last: `paid` when they pay, `waived` when they go on
    * free. Every question of a cost node is asked before any of it is paid,
    * because a resume folds the node again against the state it parked in,
    * and a payment made before a later question would change what that fold
    * reads: Way Station decides whether to ask from the favor held. */
  def around(operations: Vector[Operation], payer: PlayerId,
      occasion: String, heading: String, pay: DecisionOption,
      freeLabel: String, paid: Vector[Operation],
      waived: Vector[Operation]): Vector[Operation] =
    question(payer, occasion, heading, pay, freeLabel) +: operations :+
      outcome(paid, waived)

  /** The step: `pay`, which is unavailable with its reason when the payer
    * cannot pay, or the free option. */
  def question(payer: PlayerId, occasion: String, heading: String,
      pay: DecisionOption, freeLabel: String): Decide =
    Decide(decisionId, payer, DecisionQuery.ChooseOne(Vector(pay,
      DecisionOption.Button(PayOrAsk.free(occasion), freeLabel)),
      heading = Some(heading)), window = Some(PowerWindow.PayOrAsk),
      presentation = DecisionPresentation.Panel)

  /** What the step's answer runs. */
  def outcome(paid: Vector[Operation], waived: Vector[Operation]): Branch =
    Branch((_, pending) => PowerAnswers.one(pending, decisionId) match
      case Some(PayOrAsk.pay) => paid
      case Some(_) => waived
      case None => Vector.empty)

  /** Bars the free option of this card's step until its ruler consents. */
  lazy val restriction: OptionRestriction = OptionRestriction(
    (ctx, ref) => Option.when(barred(ctx, ref))(
      OathViolation.ConsentUnavailable(
        s"${power.value} is owed unless its ruler consents")),
    (ctx, ref) => Option.when(barred(ctx, ref))(plain(source)),
    Some(Waiver(granter, source, (_, _) => consent(source))))

  /** Whether `ctx` is gathered at this card's step. */
  def asking(ctx: PowerCtx): Boolean = ctx.operation match
    case decide: Decide => decide.decisionId == decisionId
    case _ => false

  /** The player ruling the card; none under bandits. */
  def granter(ready: ReadyGame): Option[PlayerId] =
    SiteRulers.rulerOfCard(ready, card).collect {
      case SiteRuler.Player(player) => player }

  private def barred(ctx: PowerCtx, ref: DecisionOptionRef): Boolean =
    asking(ctx) && PayOrAsk.isFree(ref)

object PayOrAsk:
  val pay: DecisionOptionRef.Button = DecisionOptionRef.Button("pay")

  /** The free option of the step asked on `occasion`. */
  def free(occasion: String): DecisionOptionRef.Button =
    DecisionOptionRef.Button(s"free.$occasion")

  def isFree(ref: DecisionOptionRef): Boolean = ref match
    case DecisionOptionRef.Button(key) => key.startsWith("free.")
    case _ => false
