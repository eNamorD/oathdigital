package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.model.{Answered, ConsentKey, ConsentTurn, CoreOperation,
  Decide, DecisionAnswer, DecisionOption, DecisionOptionRef, DecisionQuery,
  OathEvent, OathState, OathViolation, Operation, OptionReason, PendingTree,
  PowerWindow, ReadyGame}

import scala.collection.mutable

/** Runs a declared action tree to completion against immutable state and
  * throws the result away, reporting only the operations it would have
  * applied (batch-1 Task 5).
  *
  * This is how a projector or a preview learns what an action would cost
  * without the client having committed to it. It is the command path minus
  * the journal: the same restrictions run, the same windows gather, the same
  * transforms fold, and the same `OperationPipeline` validates -- so a
  * candidate the simulation accepts is a candidate the command accepts, and
  * one it rejects is one the command would have rejected for the same reason.
  * The alternative, a second arithmetic path beside the tree, is what let
  * Travel's projected cost and its charged cost be two expressions that
  * agreed only by convention.
  *
  * Deliberately generic: nothing here knows which action it is running or
  * what the operations mean. The caller supplies a tree and reads whatever
  * it needs out of the returned batch.
  *
  * A tree that parks is rejected rather than reported: a park means the
  * action asks the player something, and an unanswered question has no
  * outcome to simulate. An action whose tree parks asks `preview` instead.
  *
  * `preview` and `previewParked` extend the same idea to a tree that parks
  * on a `ChooseOne`: they answer each option against the tree and report
  * what that answer would record.
  */
object WalkerSimulation:

  /** What answering one option would record. `complete` is true when the tree
    * then finished and false when it parked at a further decision. `next`
    * previews that further decision's options, when it was asked for and the
    * decision is a choose-one.
    */
  final case class PreviewOutcome(operations: Vector[CoreOperation],
      complete: Boolean, next: Vector[PreviewedOption] = Vector.empty)

  /** One option of a previewed decision and what answering it does, or the
    * violation that makes it unanswerable.
    */
  final case class PreviewedOption(option: DecisionOption,
      outcome: Either[OathViolation, PreviewOutcome])

  def run(tree: Operation, state: ReadyGame,
      powers: WalkerPowers): Either[OathViolation, Vector[CoreOperation]] =
    guarded:
      ProcedureWalker.restrictionViolations(tree, powers, state,
        state.game.current.turn.activePlayer)
        .headOption.toLeft(())
        .flatMap(_ => ProcedureWalker.advance(state, tree, None, powers,
          WalkerDice.placeholder))
        .flatMap:
          case WalkerOutcome.Finished(_, events) =>
            Right(recordedOperations(events))
          case WalkerOutcome.Parked(_, _) => Left(
            OathViolation.InvalidEventOrder("a simulated walker tree parked; " +
              "only a tree that runs to the end has an outcome to simulate"))

  /** Whether `tree` could run now, as an [[oathdigital.gameplay.powerresolver.OfferHost]]
    * asks it of a plan, and the operations it would record: the same windows and
    * powers fold it and the same pipeline validates it. A tree that parks on a
    * decision is accepted when everything before the decision ran, because a
    * decision is not a cost. The state is not changed.
    */
  def applies(tree: Operation, state: ReadyGame,
      powers: WalkerPowers): Either[OathViolation, Vector[CoreOperation]] =
    guarded(ProcedureWalker.advance(state, tree, None, powers,
      WalkerDice.placeholder).map {
        case WalkerOutcome.Finished(_, events) => recordedOperations(events)
        case WalkerOutcome.Parked(_, events) => recordedOperations(events)
      })

  /** Why `tree` would not start now, or `None` when it would: a restriction,
    * then the first walk up to its first park, with nothing persisted. A tree
    * that parks and one that finishes both start. `answered` are the answers
    * the walk starts with (`ProcedureWalker.advance`'s `seed`). */
  def refusal(tree: Operation, state: ReadyGame, powers: WalkerPowers,
      answered: Vector[Answered] = Vector.empty): Option[OathViolation] =
    guarded {
      ProcedureWalker.restrictionViolations(tree, powers, state,
        state.game.current.turn.activePlayer, answered)
        .headOption.toLeft(())
        .flatMap(_ => ProcedureWalker.advance(state, tree, None, powers,
          WalkerDice.placeholder, answered))
    }.left.toOption

  /** Where a start's walk is once it is past its modifier decision: the
    * state and the position it parked at, `None` once it has finished, and
    * the operations it recorded on the way. */
  final case class Started(at: Option[(ReadyGame, PendingTree)],
      operations: Vector[CoreOperation])

  /** The first way a freshly built `tree` gets past its modifier decision
    * (walker-driven game loop design, ruling 8): the restriction check and
    * the first walk a start runs, then, at the modifier decision, "Start"
    * when it is available, or else each available pick in turn, depth
    * first, never the same picks twice. A walk the picks took past the
    * decision without "Start" counts only when its next decision has an
    * answer the walk can go on from. `Left` is the start's own refusal, or
    * `StartUnavailable` with the reason "Start" shows before any pick.
    * Nothing is persisted. */
  def started(tree: Operation, state: ReadyGame, powers: WalkerPowers)
      : Either[OathViolation, Started] = guarded:
    val tried = mutable.Set.empty[Set[DecisionOptionRef]]
    ProcedureWalker.restrictionViolations(tree, powers, state,
      state.game.current.turn.activePlayer)
      .headOption.toLeft(())
      .flatMap(_ => ProcedureWalker.advance(state, tree, None, powers,
        WalkerDice.placeholder))
      .flatMap(reached(state, tree, None, Vector.empty, powers,
        picked = false, tried))

  /** Where `outcome` leaves the walk, given the position `from` it was
    * walked from and the operations recorded `before` it. */
  private def reached(state: ReadyGame, tree: Operation,
      from: Option[PendingTree], before: Vector[CoreOperation],
      powers: WalkerPowers, picked: Boolean,
      tried: mutable.Set[Set[DecisionOptionRef]])(outcome: WalkerOutcome)
      : Either[OathViolation, Started] = outcome match
    case WalkerOutcome.Finished(_, events) =>
      Right(Started(None, before ++ recordedOperations(events)))
    case WalkerOutcome.Parked(next, events) =>
      val operations = before ++ recordedOperations(events)
      after(state, from, next, events).flatMap { here =>
        ProcedureWalker.parkedDecide(here, tree, next, powers) match
          case Some(decide) if ModifierPicks.isDecision(decide.decisionId) =>
            through(here, tree, next, decide, operations, powers, tried)
          case _ if picked && !opens(here, tree, next, powers) =>
            Left(OathViolation.InvalidEventOrder(
              "no answer goes on past the modifiers"))
          case _ => Right(Started(Some(here -> next), operations))
      }

  /** "Start" when it is available, else each available pick not yet tried
    * with the same picks. */
  private def through(state: ReadyGame, tree: Operation,
      pending: PendingTree, decide: Decide, before: Vector[CoreOperation],
      powers: WalkerPowers, tried: mutable.Set[Set[DecisionOptionRef]])
      : Either[OathViolation, Started] =
    val options = PricedOptions.options(decide)
    val start = options.find(_.ref == ModifierPicks.start)
    val chosen = picks(pending)
    val ways = start.filter(_.available).toVector ++ options.filter(option =>
      option.available && option.ref != ModifierPicks.start &&
        tried.add(chosen + option.ref))
    def answering(option: DecisionOption) =
      ProcedureWalker.resolve(state, tree, pending, Answered(
          decide.decisionId, DecisionAnswer.ChooseOneAnswer(option.ref),
          decide.owner), powers, WalkerDice.placeholder)
        .flatMap(reached(state, tree, Some(pending), before, powers,
          picked = option.ref != ModifierPicks.start, tried))
    // "Start" available yet refused: its own violation says why, where the
    // reason of a "Start" that is unavailable is all there is.
    var startRefusal: Option[OathViolation] = None
    ways.iterator.map(option => answering(option) match
      case refused @ Left(violation) =>
        if option.ref == ModifierPicks.start && startRefusal.isEmpty then
          startRefusal = Some(violation)
        refused
      case found => found).collectFirst { case Right(found) => found }
      .toRight(startRefusal.getOrElse(OathViolation.StartUnavailable(
        start.collect {
          case DecisionOption.Unavailable(_, OptionReason.Written(text)) =>
            text
        }.getOrElse(ModifierChoice.cannotStart))))

  /** The modifiers picked so far, as their options. */
  private def picks(pending: PendingTree): Set[DecisionOptionRef] =
    pending.answered.collect {
      case Answered(id, DecisionAnswer.ChooseOneAnswer(ref), _)
          if ModifierPicks.isDecision(id) && ref != ModifierPicks.start => ref
    }.toSet

  /** Whether the walk can go on from where it parked: a choose-one with an
    * answer that can be completed. Any other park is taken to go on. */
  private def opens(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers): Boolean =
    previewParked(state, tree, pending, powers) match
      case Right(options) => options.exists(_.outcome.isRight)
      case Left(_) => true

  /** Previews a freshly built tree: walks it to its first park and previews
    * that decision. The tree must park before it runs any operation, because
    * the state after an executed prefix is not reported by `advance`.
    */
  def preview(tree: Operation, state: ReadyGame, powers: WalkerPowers,
      answered: Vector[Answered] = Vector.empty)
      : Either[OathViolation, Vector[PreviewedOption]] =
    guarded:
      ProcedureWalker.restrictionViolations(tree, powers, state,
        state.game.current.turn.activePlayer, answered)
        .headOption.toLeft(())
        .flatMap(_ => ProcedureWalker.advance(state, tree, None, powers,
          WalkerDice.placeholder, answered))
        .flatMap:
          case WalkerOutcome.Parked(pending, events)
              if recordedOperations(events).isEmpty =>
            previewParked(state, tree, pending, powers)
          case WalkerOutcome.Parked(_, _) => Left(
            OathViolation.InvalidEventOrder("a previewed tree may not run " +
              "operations before its first decision"))
          case WalkerOutcome.Finished(_, _) => Left(
            OathViolation.InvalidEventOrder(
              "a previewed tree finished without parking on a decision"))

  /** Previews the decision `pending` is parked on: each available `ChooseOne`
    * option is answered as the decision's owner, against a copy of the parked position,
    * and the walk continues until it finishes or parks again. Nothing is
    * persisted. Any other query shape is rejected. With `followUp`, an answer
    * that parks at a further choose-one also previews that decision's options
    * (one level deep). A pay-or-ask step is previewed whether or not
    * `followUp` asks for it, and does not use up that level, so a priced
    * option still shows the toll its step asks (consent slice 4).
    * A pay-or-ask step with no option its payer may take previews each
    * askable option as if its ruler had consented, so a priced option shows
    * what going on free costs.
    */
  def previewParked(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, followUp: Boolean = false)
      : Either[OathViolation, Vector[PreviewedOption]] =
    guarded:
      ProcedureWalker.parkedDecide(state, tree, pending, powers)
        .toRight(OathViolation.InvalidEventOrder(
          "the walker is not parked on a decision"))
        .flatMap { decide =>
          decide.query match
            case one: DecisionQuery.ChooseOne =>
              Right(one.options.filter(_.available).map(option =>
                PreviewedOption(option,
                  answer(state, tree, pending, powers, decide, option,
                    followUp))) ++
                consented(state, decide, one.options).map((option, granted) =>
                  PreviewedOption(option, answer(granted, tree, pending,
                    powers, decide, option, followUp))))
            case _ => Left(OathViolation.InvalidEventOrder(
              s"decision ${decide.decisionId} is not a choose-one, so it " +
                "cannot be previewed"))
        }

  private def answer(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, decide: Decide, option: DecisionOption,
      followUp: Boolean): Either[OathViolation, PreviewOutcome] = guarded:
    ProcedureWalker.resolve(state, tree, pending,
      Answered(decide.decisionId, DecisionAnswer.ChooseOneAnswer(option.ref),
        decide.owner), powers, WalkerDice.placeholder).map:
      case WalkerOutcome.Finished(_, events) =>
        PreviewOutcome(recordedOperations(events), complete = true)
      case WalkerOutcome.Parked(next, events) =>
        val at = after(state, Some(pending), next, events)
        val step = at.toOption.exists(waitsOnStep(_, tree, next, powers))
        PreviewOutcome(recordedOperations(events), complete = false,
          next = if !followUp && !step then Vector.empty
            else at.flatMap(previewParked(_, tree, next, powers,
              followUp = followUp && step)).getOrElse(Vector.empty))

  /** Whether the walk parked at `next` waits on a pay-or-ask step. */
  private def waitsOnStep(ready: ReadyGame, tree: Operation,
      next: PendingTree, powers: WalkerPowers): Boolean =
    ProcedureWalker.parkedDecide(ready, tree, next, powers, probing = false)
      .exists(_.window.contains(PowerWindow.PayOrAsk))

  /** For a pay-or-ask step with no option its payer may take, each askable
    * option with the state as it would stand once its ruler consents. Empty
    * for any other decision. */
  private def consented(state: ReadyGame, decide: Decide,
      options: Vector[DecisionOption]): Vector[(DecisionOption, ReadyGame)] =
    if !decide.window.contains(PowerWindow.PayOrAsk) ||
        options.exists(_.available) then Vector.empty
    else options.collect {
      case option @ DecisionOption.Unavailable(_, ask: OptionReason.Askable) =>
        option -> state.copy(consent = state.consent.granting(
          ask.asks.flatMap(_.powers).distinct.map(ConsentKey(_, decide.owner,
            decide.decisionId, option.ref)), ConsentTurn.of(state)))
    }

  /** The parked position an answer leads to: its recorded steps applied to
    * `state` as replay applies them, parked at `next`. The steps start from
    * the position the answer was given at, `None` for a start's first walk,
    * which a start's preview has not recorded yet. */
  private def after(state: ReadyGame, from: Option[PendingTree],
      next: PendingTree, events: Vector[OathEvent])
      : Either[OathViolation, ReadyGame] =
    events.collect { case step: WalkerStepRecorded => step }
      .foldLeft[Either[OathViolation, OathState]](Right(OathState.Ready(
        state.updateCurrent(_.copy(walkerPending = from))))) {
        (folded, step) => folded.flatMap(ProcedureWalker.applyRecorded(_, step))
      }.flatMap:
        case OathState.Ready(ready) =>
          Right(ready.updateCurrent(_.copy(walkerPending = Some(next))))
        case _ => Left(OathViolation.GameNotStarted)

  private def recordedOperations(events: Vector[OathEvent])
      : Vector[CoreOperation] = events.collect {
    case step: WalkerStepRecorded => step.ops
  }.flatten

  // `ProcedureWalker` reports a broken resume position by throwing, and a
  // simulation must never propagate that to a projector building a view.
  private def guarded[A](body: => Either[OathViolation, A])
      : Either[OathViolation, A] =
    try body
    catch
      case error: IllegalArgumentException => Left(
        OathViolation.InvalidEventOrder(Option(error.getMessage)
          .getOrElse("invalid simulated walker position")))
