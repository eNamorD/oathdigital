package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.PowerUseRules
import oathdigital.gameplay.powerresolver.{PowerUse, PowerUseRefusal,
  PowerUseStep}
import oathdigital.model._

/** How the walker resolves an [[AutomaticUse]] (catalog batch 8, slice
  * 10; automatic power consent design). Every walker path that resolves
  * a `Branch` resolves this node the same way, so a resume addresses the
  * same vector the walk did.
  *
  * A node the power-use restrictions allow, or refuse only waivably, has
  * two children: a slot, then a body. A node refused by a restriction
  * nobody may waive has none, and its use is deactivated unasked.
  *
  *  - Allowed, or waivable with every grant remembered this turn: the
  *    slot does nothing and the body is the effect.
  *  - Waivable with a refusal remembered this turn: the slot says the use
  *    stays deactivated, and the body does nothing.
  *  - Waivable with nothing remembered: the slot is the power-use step
  *    ([[PowerUseStep]]), and the body runs the effect only when the step
  *    was answered `use`. A preview walks the effect instead, as if its
  *    rulers let it work.
  *
  * The verdict reads the raw refusals and ignores the ledger, so a grant
  * or a refusal never changes the node's shape. A resume addresses the
  * slot only when it is the step, and the body only when it is the
  * effect: a resume inside the effect runs it unjudged, since a step of
  * the effect that turned the verdict would otherwise empty the vector
  * the parked position points into. */
private[walker] object AutomaticUses:
  /** The body's index. A resume there is inside the effect. */
  private val Body = "1"
  private val unasked: Operation = Sequence(Vector.empty)

  def children(use: AutomaticUse, state: ReadyGame, pending: PendingTree,
      powers: WalkerPowers, cursor: Option[Vector[String]])
      : Vector[Operation] =
    val judged = PowerUse.automatic(use.user, use.power, use.card)
    cursor.flatMap(_.headOption) match
      case Some(Body) => Vector(unasked, effect(use))
      case Some(_) => Vector(step(use, judged, state, powers), answered(use))
      case None =>
        val raised = PowerUseRules.raised(state, judged,
          powers.useRestrictions)
        if raised.isEmpty then Vector(unasked, effect(use))
        else if !PowerUseRules.waivable(judged, raised) then Vector.empty
        else remembered(state, raised) match
          case Some(ConsentAnswer.Refused) =>
            Vector(stays(use, state, raised), unasked)
          case Some(ConsentAnswer.Granted) => Vector(unasked, effect(use))
          case None if powers.previewing => Vector(unasked, effect(use))
          case None =>
            Vector(step(use, judged, state, powers), answered(use))

  private def effect(use: AutomaticUse): Operation =
    Branch((now, tree) => use.effect(now, tree))

  /** The effect, when the step was answered `use`. */
  private def answered(use: AutomaticUse): Operation = Branch((now, tree) =>
    if PowerUseStep.answer(tree.answered, use.power)
        .contains(PowerUseStep.use) then use.effect(now, tree)
    else Vector.empty)

  /** The step: `use`, askable of the rulers whose refusals are not yet
    * lifted, or open once each has granted; and `skip`. */
  private def step(use: AutomaticUse, judged: PowerUse, state: ReadyGame,
      powers: WalkerPowers): Decide =
    val left = PowerUseRules.refusals(state, judged, powers.useRestrictions)
    val letting = DecisionOption.Button(PowerUseStep.use, "Let it work")
    Decide(PowerUseStep.decisionId(use.power), use.user,
      DecisionQuery.ChooseOne(Vector(
        if left.isEmpty then letting
        else DecisionOption.Unavailable(letting, PowerUseRules.shown(state,
          use.user, left, consent = judged.consent)),
        DecisionOption.Button(PowerUseStep.skip, "Deactivate"))))

  /** Refused when any refusal's answer this turn is a refusal, granted
    * when every one's is a grant, and `None` while some ruler has not
    * answered. */
  private def remembered(state: ReadyGame,
      refusals: Vector[PowerUseRefusal]): Option[ConsentAnswer] =
    val answers = refusals.map(answerTo(state, _))
    if answers.contains(Some(ConsentAnswer.Refused)) then
      Some(ConsentAnswer.Refused)
    else if answers.forall(_.contains(ConsentAnswer.Granted)) then
      Some(ConsentAnswer.Granted)
    else None

  private def answerTo(state: ReadyGame, refusal: PowerUseRefusal)
      : Option[ConsentAnswer] = refusal.reason.waiver.flatMap(waiver =>
    state.consent.answer(waiver.covers, waiver.granter,
      ConsentTurn.of(state)))

  /** "{card} stays deactivated.", under the first refusing card whose
    * refusal is remembered. */
  private def stays(use: AutomaticUse, state: ReadyGame,
      refusals: Vector[PowerUseRefusal]): Operation =
    refusals.find(answerTo(state, _).contains(ConsentAnswer.Refused))
      .flatMap(refusal => refusal.note.source.map(source =>
        Note(refusal.power, _ => Some(PowerUseStep.staysDeactivated(source,
          NoteArg.Card(use.card))))))
      .getOrElse(unasked)
