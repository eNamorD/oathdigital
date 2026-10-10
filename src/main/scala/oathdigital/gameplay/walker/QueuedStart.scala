package oathdigital.gameplay.walker

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.model._

/** Whether a queued entry would start now (walker-driven game loop design,
  * plan-time ruling 4): the checks the driver's start of it runs, on the
  * state that start would see, with nothing persisted. An option the
  * follow-up offer shows available is a start the driver accepts.
  *
  * It builds through the production registry, so a suite that injects its
  * own tree source into `OathRules` does not reach it.
  */
object QueuedStart:
  def refusal(catalog: ExecutableCatalog, ready: ReadyGame,
      entry: QueuedProcedure, powers: WalkerPowers,
      phasePowers: PhasePowers): Option[OathViolation] =
    val state = startingFrom(ready, entry)
    WalkerProcedureRegistry.build(entry.procedure, catalog, state,
        entry.actor, entry.startArgs, phasePowers)
      .fold(Some(_), tree => WalkerSimulation.refusal(tree, state, powers)
        .orElse(unplayable(entry.procedure, tree, state, powers)))

  /** The state the entry's start runs on: no walk open, and the entry
    * dequeued, so the cost waiver and the phase exemption see it. */
  def startingFrom(ready: ReadyGame, entry: QueuedProcedure): ReadyGame =
    ready.updateCurrent(_.copy(walkerPending = None, walkerProcedure = None,
      walkerStartArgs = Vector.empty, walkerStartFacts = Vector.empty,
      walkerEntry = Some(entry)))

  /** The playable-option gate a start runs for a procedure that opts in. */
  private def unplayable(procedure: StartableRef, tree: Operation,
      state: ReadyGame, powers: WalkerPowers): Option[OathViolation] =
    if !WalkerProcedureRegistry.requiresPlayableOption(procedure) then None
    else ProcedureWalker.advance(state, tree, None, powers,
        WalkerDice.placeholder).toOption.collect {
        case WalkerOutcome.Parked(pending, _) => pending
      }.flatMap(pending =>
        WalkerSimulation.previewParked(state, tree, pending, powers) match
          case Right(options) if options.exists(_.outcome.isRight) => None
          case Right(_) => Some(OathViolation.NoPlayableOption(
            procedure.key))
          case Left(violation) => Some(violation))
