package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.OperationConsent
import oathdigital.model._

/** The reasons of a choose-one's options that the search hid because a
  * power's operation refusal rejects their path (catalog batch 8, slice
  * 10): a Muster's source card that Forest Council forbids placing on, or
  * a card the Hall of Ministers protects. Each round grants what the last
  * refusal covers and tries again; an option has a reason when granting
  * every refusal its path meets opens it. It is askable when every refusal
  * is waivable by a player other than the decision's owner and none was
  * refused this turn (consent design, ruling 8); the ask covers every
  * refusal's key, so a grant lifts each of them on the operation path.
  * Otherwise it shows the first refusal's plain reason, which never keeps
  * a decision alive by itself ([[ProcedureWalker]]).
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
      // The first round asks of `state` itself, so the live walk's memo
      // answers it.
      val granted = if found.isEmpty then state else state.copy(consent =
        state.consent.granting(found.flatMap(_.waiver).map(_.covers), turn))
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
    else OperationConsent.shown(state, decide.owner, found)

  /** The options `decide` itself declares askable whose grants would open
    * them: a builder that words its own consent asks (catalog batch 8,
    * slice 15) is held to the check the look-ahead makes before it offers
    * an ask (consent design, ruling 8). */
  def declared(decide: Decide, state: ReadyGame,
      verdict: ReadyGame => DecisionAnswer => Either[OathViolation, Unit])
      : Map[DecisionOptionRef, OptionReason] = decide.query match
    case one: DecisionQuery.ChooseOne => one.options.collect {
      case DecisionOption.Unavailable(option, ask: OptionReason.Askable)
          if opens(decide, state, option.ref, ask, verdict) =>
        option.ref -> (ask: OptionReason) }.toMap
    case _ => Map.empty

  private def opens(decide: Decide, state: ReadyGame, ref: DecisionOptionRef,
      ask: OptionReason.Askable,
      verdict: ReadyGame => DecisionAnswer => Either[OathViolation, Unit])
      : Boolean =
    val keys = if ask.covers.nonEmpty then ask.covers
      else ask.asks.flatMap(_.powers).map(ConsentKey(_, decide.owner,
        decide.decisionId, ref))
    verdict(state.copy(consent = state.consent.granting(keys,
      ConsentTurn.of(state))))(DecisionAnswer.ChooseOneAnswer(ref)).isRight
