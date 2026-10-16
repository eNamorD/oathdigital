package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.OperationConsent
import oathdigital.model._

/** The asks of a choose-one's options that the search hid because a
  * waivable operation refusal rejects their path (catalog batch 8, slice
  * 10): a Muster's source card that Forest Council forbids placing on, or
  * a card the Hall of Ministers protects. An option is askable when every
  * refusal its path meets is waivable by a player other than the
  * decision's owner, none was refused this turn (consent design, ruling
  * 8), and granting them all opens the path. Each round grants what the
  * last refusal covers and tries again. The ask covers every refusal's
  * key, so a grant lifts each of them on the operation path.
  */
private[walker] object OperationAsks:
  /** Enough rounds for every waivable restriction one path can meet. */
  private val Rounds = 4

  def of(decide: Decide, state: ReadyGame, refs: Vector[DecisionOptionRef],
      verdict: ReadyGame => DecisionAnswer => Either[OathViolation, Unit])
      : Map[DecisionOptionRef, OptionReason] =
    if !decide.query.isInstanceOf[DecisionQuery.ChooseOne] then Map.empty
    else refs.flatMap(ref =>
      ask(decide, state, ref, verdict).map(ref -> _)).toMap

  private def ask(decide: Decide, state: ReadyGame, ref: DecisionOptionRef,
      verdict: ReadyGame => DecisionAnswer => Either[OathViolation, Unit])
      : Option[OptionReason] =
    val turn = ConsentTurn.of(state)
    val answer = DecisionAnswer.ChooseOneAnswer(ref)
    @annotation.tailrec
    def met(found: Vector[OperationReason], round: Int)
        : Vector[OperationReason] =
      val granted = state.copy(consent = state.consent.granting(
        found.flatMap(_.waiver).map(_.covers), turn))
      verdict(granted)(answer) match
        case Right(()) => found
        case Left(OathViolation.WaivableOperationRejected(code, detail,
            waiver)) if round < Rounds &&
            !found.exists(_.waiver.contains(waiver)) =>
          met(found :+ OperationReason(code, detail,
            OperationReasonKind.Impossible, Some(waiver)), round + 1)
        case Left(_) => Vector.empty
    val found = met(Vector.empty, 0)
    if found.isEmpty then None
    else OperationConsent.ask(state, decide.owner, found)
