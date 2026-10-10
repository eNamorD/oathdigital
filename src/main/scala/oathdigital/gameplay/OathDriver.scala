package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.EventEvolution
import oathdigital.gameplay.oathkeeper.{OathkeeperOutcome, OathkeeperRules}
import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.phases.rest.{TurnBoundary,
  WarExhaustionRandomPort}
import oathdigital.gameplay.phases.wake.ForcedWakeProcedure
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.walker.{ActionBoundaryRan, ProcedureDequeued}
import oathdigital.model._
import oathdigital.model.OathEvent._
import oathdigital.model.OathState._
import oathdigital.model.OathViolation._

/** What runs between walks (walker-driven game loop design, "The driver").
  * Every finished walk is handed to [[next]], which starts nothing while a
  * result is set, and otherwise starts the queue's head. With an empty queue
  * it runs, in order:
  *
  *  1. the action boundary, when an action has finished since the last one
  *     (`boundaryDue`, ruling 5): the ignored-rule diagnostics, the bandit
  *     refill and the Oathkeeper check. A title walk the check starts hands
  *     back here when it ends.
  *  2. the turn's start, after a Finish Rest or Setup walk: the round's end
  *     when the last player has rested, then Wake's entry and the Forced
  *     Wake walk when one is due, which hands back here when it ends.
  *  3. the phase's menu: in Wake and Rest, the menu, or the phase's ending
  *     when it is the only option available (ruling 3). Act always asks.
  *
  * Each start runs on the state the steps before it left, so a turn that
  * changes inside one command gives the next walk the new active player.
  */
private[gameplay] trait OathDriver:
  self: OathRulesWalker & EventEvolution[OathState, OathEvent,
    OathViolation] =>

  protected def catalog: ExecutableCatalog
  protected def phasePowerCatalog: PhasePowers
  protected def warExhaustionRandomPort: WarExhaustionRandomPort
  protected def outsideActions: Vector[OperationRestriction]

  private[gameplay] def next(transition: OathTransition,
      finished: ProcedureRef): Either[OathViolation, OathTransition] = transition.state match
    case Ready(ready) if ready.game.current.result.nonEmpty =>
      Right(transition)
    case Ready(ready) if ready.game.current.queue.nonEmpty =>
      dequeue(transition, ready.game.current.queue.head)
    case Ready(ready) if ready.game.current.boundaryDue =>
      boundary(transition, finished)
    case Ready(_) => turnStart(transition, finished)
    case _ => Right(transition)

  /** A queued head starts at once: a `Run` as its procedure, an `Offer` as
    * the question whose answer may queue it as a `Run`. */
  private def dequeue(transition: OathTransition, entry: QueuedProcedure) =
    append(transition, ProcedureDequeued(entry)).flatMap(taken =>
      entry.kind match
        case QueueKind.Run => startQueued(taken, entry)
        case QueueKind.Offer => startTriggered(taken,
          TriggeredProcedureRef.FollowUpOffer))

  /** Step 1. Its order is the old `completeAction`'s, after the marker. */
  private def boundary(transition: OathTransition,
      finished: ProcedureRef) =
    for
      marked <- append(transition, ActionBoundaryRan)
      noted <- boundaryDiagnostics(marked)
      refilled <- appendEvaluation(noted,
        StateBasedEvaluation.banditRefill(catalog, _))
      checked <- oathkeeper(refilled, finished)
    yield checked

  /** The boundary decides only WHETHER the title changes; the triggered
    * procedure performs the change, so every title change is one walker
    * step. */
  private def oathkeeper(transition: OathTransition,
      finished: ProcedureRef) =
    transition.state match
      case Ready(ready) => StateBasedEvaluation.supported(transition.state)
        .flatMap(_ => OathkeeperRules.outcome(ready) match
          case OathkeeperOutcome.NoChange => turnStart(transition, finished)
          case _ => startTriggered(transition,
            TriggeredProcedureRef.Oathkeeper))
      case _ => Right(transition)

  private def boundaryDiagnostics(transition: OathTransition) =
    transition.state match
      case Ready(ready) =>
        val actor = ready.game.current.turn.activePlayer
        PowerRuntime.ignored(catalog, ready, actor,
          ActionKind.ActionBoundary).map { diagnostics =>
          if diagnostics.isEmpty then transition else transition.copy(
            events = transition.events :+ IgnoredRulesRecorded(actor,
              ActionKind.ActionBoundary, diagnostics))
        }
      case _ => Right(transition)

  /** Step 2 (plan-time ruling 8). Setup ends as Finish Rest does, so the
    * first Wake runs these hooks too. */
  private def turnStart(transition: OathTransition, finished: ProcedureRef)
      : Either[OathViolation, OathTransition] = finished match
    case PhaseTransitionRef.FinishRest | TriggeredProcedureRef.Setup =>
      val rounded = transition.state match
        case Ready(ready) if ready.game.current.turn.phase ==
            Phase.RoundEnd =>
          TurnBoundary.finishRound(catalog, transition,
            warExhaustionRandomPort, outsideActions)
        case _ => Right(transition)
      rounded.flatMap(after => after.state match
        case Ready(ready) if ready.game.current.result.nonEmpty =>
          Right(after)
        case _ => enterWake(after))
    case _ => phaseEnd(transition)

  private def enterWake(transition: OathTransition) =
    val prepared = transition.state match
      case Ready(ready) => PowerRuntime.ignored(catalog, ready,
          ready.game.current.turn.activePlayer, ActionKind.Wake)
        .map { diagnostics =>
          if diagnostics.isEmpty then transition else transition.copy(
            events = transition.events :+ IgnoredRulesRecorded(
              ready.game.current.turn.activePlayer, ActionKind.Wake,
              diagnostics))
        }
      case _ => Right(transition)
    prepared.flatMap(current =>
      StateBasedEvaluation.atWake(current.state).flatMap {
        case Some(win: UsurperVictory) => append(current, win)
        case Some(flip: UsurperFlipped) => append(current, flip)
          .flatMap(vision)
        case None => vision(current)
        case Some(other) => Left(InvalidEventOrder(
          s"unexpected Wake evaluation event: $other"))
      }).flatMap(forcedWake)

  private def vision(transition: OathTransition) =
    StateBasedEvaluation.visionAtWake(transition.state).flatMap {
      case Some(won) => append(transition, won)
      case None => Right(transition)
    }

  /** The forced Wake powers of the player whose Wake begins (catalog batch
    * 2, N8), while the game goes on. */
  private def forcedWake(transition: OathTransition) =
    transition.state match
      case Ready(ready) if ready.game.current.result.isEmpty &&
          ready.game.current.turn.phase == Phase.Wake &&
          ForcedWakeProcedure.due(catalog, ready,
            ready.game.current.turn.activePlayer,
            phasePowerCatalog).nonEmpty =>
        startTriggered(transition, TriggeredProcedureRef.ForcedWake)
      case _ => phaseEnd(transition)

  /** Step 3: the phase's menu (walker-driven game loop design, "The
    * menus"). When its ending is the only option available, the ending
    * starts without asking (ruling 3; slice 1, plan-time ruling 2). Act
    * always asks. */
  private def phaseEnd(transition: OathTransition)
      : Either[OathViolation, OathTransition] = transition.state match
    case Ready(ready) if ready.game.current.result.isEmpty =>
      val player = ready.game.current.turn.activePlayer
      PhaseMenu.of(ready.game.current.turn.phase) match
        case Some(menu) if PhaseMenu.onlyEnding(menu, PhaseMenu.offers(menu,
            catalog, ready, player, phasePowerCatalog, walkerPowers)) =>
          joined(transition, startWalker(transition.state, menu.ending,
            player))
        case Some(menu) => startTriggered(transition, menu.procedure)
        case None => Right(transition)
    case _ => Right(transition)

  private def joined(before: OathTransition,
      started: Either[OathViolation, OathTransition]) =
    started.map(after => after.copy(events = before.events ++ after.events))

  private def append(transition: OathTransition, event: OathEvent) =
    evolve(transition.state, event).map(state =>
      transition.copy(state = state, events = transition.events :+ event))

  private def appendEvaluation(transition: OathTransition,
      evaluate: OathState => Either[OathViolation, Option[OathEvent]]) =
    evaluate(transition.state).flatMap:
      case None => Right(transition)
      case Some(event) => append(transition, event)
