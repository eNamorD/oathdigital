package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.EventEvolution
import oathdigital.model._
import oathdigital.gameplay.powerresolver.{ContributingPower, PhasePowers}
import oathdigital.gameplay.walker.{DeltaPayload, FollowUpOffer,
  FollowUpSkipped, ModifierOffers, ParkedUses, ProcedureWalker,
  WalkerCompleted, WalkerDice, WalkerOutcome, WalkerParked, WalkerPowers,
  WalkerProcedureRegistry, WalkerSimulation, WalkerStepRecorded}
import oathdigital.model.OathState._
import oathdigital.model.OathViolation._

/** The walker command surface of [[OathRules]], mixed into it.
  *
  * A client acts on a walker procedure only through `resolveWalker` and
  * `rollWalkerPrepared`: it picks from a phase menu, which queues the next
  * procedure. `startWalker` is for the driver and the tests. Those and the
  * power and park plumbing they share are held in one file so `OathRules` can keep
  * carrying the turn/phase plumbing without either half crowding the other
  * out. Every method below moved here unchanged; the abstract members are exactly what that block
  * reaches for on the class it is mixed into.
  */
private[gameplay] trait OathRulesWalker:
  self: EventEvolution[OathState, OathEvent, OathViolation] =>

  protected def catalog: ExecutableCatalog
  protected def walkerPowerCatalog: WalkerPowers
  protected def phasePowerCatalog: PhasePowers
  protected def walkerDice: WalkerDice
  protected def walkerTree: OathRules.WalkerTreeSource
  /** Runs what follows a finished walk (`OathDriver`). */
  private[gameplay] def next(transition: OathTransition,
      finished: ProcedureRef): Either[OathViolation, OathTransition]

  /** Starts one action on the generic procedure walker. An action asks for
    * its modifiers inside the walk, as its first decision (modifier walker
    * design), so a start carries only what the player selected before it
    * (`startArgs`).
    */
  def startWalker(state: OathState, procedure: StartableRef,
      requester: PlayerId,
      startArgs: Vector[DecisionOptionRef] = Vector.empty)
      : Either[OathViolation, OathTransition] =
    state match
      case Ready(ready) if ready.game.current.walkerPending.nonEmpty ||
          ready.game.current.walkerProcedure.nonEmpty =>
        Left(InvalidEventOrder("a walker procedure is already pending"))
      case Ready(ready) if ready.game.current.result.nonEmpty => Left(GameEnded)
      case Ready(ready) =>
        startNow(state, ready, procedure, requester, startArgs)
      case _ => Left(GameNotStarted)

  /** The start itself, for the driver's dequeued `Run` entries and for a
    * test's direct start. */
  private def startNow(state: OathState, ready: ReadyGame,
      procedure: StartableRef, requester: PlayerId,
      startArgs: Vector[DecisionOptionRef])
      : Either[OathViolation, OathTransition] =
    val actor = ready.game.current.walkActor
    for
      _ <- requireWalkActor(ready, requester)
      powers = walkerPowers
      tree <- buildWalker(procedure, ready, actor, startArgs,
        starting = true)
      _ <- checkRestrictions(tree, powers, ready, actor)
      outcome <- walkerCall(ProcedureWalker.advance(ready, tree, None,
        powers, walkerDice))
      _ <- requirePlayableOption(procedure, ready, tree, outcome, powers)
      transition <- walkerTransition(state, procedure, tree,
        outcome, powers, startArgs,
        WalkerProcedureRegistry.startFacts(procedure, catalog, ready,
          actor))
    yield transition

  /** Starts a dequeued `Run` entry for its actor, on the state its
    * `ProcedureDequeued` left, so the cost waiver and the phase exemption
    * see it. A start that fails refuses the whole command. */
  private[gameplay] def startQueued(transition: OathTransition,
      entry: QueuedProcedure): Either[OathViolation, OathTransition] =
    transition.state match
      case Ready(ready) => startNow(transition.state, ready, entry.procedure,
          entry.actor, entry.startArgs)
        .map(started => started.copy(
          events = transition.events ++ started.events))
      case _ => Left(GameNotStarted)

  /** Starts a procedure the engine triggers, with no client command
    * (walker-ownership spec, Triggered procedures).
    *
    * Strictly sequential: a walker procedure already pending is a
    * typed rejection rather than a nested start. The procedure's events are
    * appended to `transition`, so whatever handed it on and the procedure
    * journal as one command.
    */
  private[gameplay] def startTriggered(transition: OathTransition,
      procedure: TriggeredProcedureRef): Either[OathViolation, OathTransition] =
    transition.state match
      case Ready(ready) if ready.game.current.walkerPending.nonEmpty ||
          ready.game.current.walkerProcedure.nonEmpty =>
        Left(InvalidEventOrder(s"cannot start ${procedure.key}: another " +
          "procedure is already pending"))
      case Ready(ready) =>
        val actor = ready.game.current.walkActor
        val powers = walkerPowers
        for
          tree <- buildWalker(procedure, ready, actor, Vector.empty,
            starting = true)
          _ <- checkRestrictions(tree, powers, ready, actor)
          outcome <- walkerCall(ProcedureWalker.advance(ready, tree, None, powers,
            walkerDice))
          started <- walkerTransition(transition.state, procedure, tree,
            outcome, powers, Vector.empty, Vector.empty)
        yield started.copy(events = transition.events ++ started.events)
      case _ => Right(transition)

  /** The powers a command walks with: every automatic power of
    * `walkerPowerCatalog`, and none of its player-selected ones. The walker
    * adds the modifiers the walk's answers pick (`WalkerPowers.withAnswers`),
    * on every command alike, so a resume folds every window as the start
    * did.
    */
  def walkerPowers: WalkerPowers =
    WalkerPowers.selected(walkerPowerCatalog, Vector.empty)

  /** The `ContributingPower`s a player could pick for `actor` on `procedure`
    * right now: `PlayerSelected` powers in `walkerPowerCatalog` that are
    * `applicable` at that procedure's modifier-selection window and whose
    * selection payment `actor` can make (`ModifierOffers.offerable`). The
    * modifier decision lists these and judges each option exactly.
    *
    * An entry declaring `modifierWindow = None` offers nothing; a procedure
    * absent from `registrations` is a `Left`, since "registers no window" and
    * "is not registered" are different facts and only the first is a rule.
    * `registrations` defaults to the production `WalkerProcedureRegistry
    * .entries`; a suite passes its own to prove behaviour that differs
    * between procedures.
    */
  def offerableWalkerPowers(ready: ReadyGame, actor: PlayerId,
      procedure: ProcedureRef,
      registrations: Map[ProcedureRef, WalkerProcedureRegistry.Entry] =
        WalkerProcedureRegistry.entries)
      : Either[OathViolation, Vector[ContributingPower]] =
    ModifierOffers.offerable(walkerPowerCatalog, ready, actor, procedure,
      registrations)

  /** Restrictions run once per command, at command entry, before the walk
    * -- collected across the whole derived `tree` via
    * [[ProcedureWalker.restrictionViolations]]. The first violation (if any)
    * rejects the command with no events appended, exactly like any other
    * `for`-comprehension short-circuit here.
    */
  private def checkRestrictions(tree: Operation, powers: WalkerPowers,
      ready: ReadyGame, actor: PlayerId,
      answered: Vector[Answered] = Vector.empty): Either[OathViolation, Unit] =
    ProcedureWalker.restrictionViolations(tree, powers, ready, actor, answered)
      .headOption.toLeft(())

  /** Resolves the current parked Decide. Procedure identity is reconstructed
    * from the durable walkerProcedure fact, never supplied by the client.
    *
    * `requester` is bound by the transport and checked against the rebuilt
    * Decide's owner by the walker before its answer is recorded.
    */
  def resolveWalker(state: OathState, requester: PlayerId,
      decisionId: String, answer: DecisionAnswer)
      : Either[OathViolation, OathTransition] =
    resumeWalker(state):
      case (ready, procedure, tree, pending, powers, startArgs) =>
        val recorded = Answered(decisionId, answer, by = requester)
        walkerCall(ProcedureWalker.resolve(ready, tree, pending, recorded,
          powers, walkerDice)).flatMap(outcome => checkRestrictions(tree,
          powers, ready, ready.game.current.walkActor,
          pending.answered :+ recorded).map(_ => outcome))
          .flatMap(walkerTransition(state, procedure, tree, _, powers,
            startArgs, ready.game.current.walkerStartFacts))

  /** The ask a consent request for `target` sends (consent design,
    * "Commands"). `target` must be an askable option of the parked
    * `decisionId`, which `requester` owns; the look-ahead decided that, so
    * nothing here re-derives who may consent. */
  def consentAsk(state: OathState, requester: PlayerId, decisionId: String,
      target: DecisionOptionRef)
      : Either[OathViolation, OptionReason.Askable] =
    walkerResumeContext(state).flatMap:
      case (ready, _, tree, pending, powers, _) =>
        walkerCall(Right(ProcedureWalker.parkedDecide(ready, tree, pending,
          powers))).flatMap(decide => decide
          .filter(found => found.decisionId == decisionId &&
            found.owner == requester)
          .flatMap(found => offered(found).collectFirst {
            case DecisionOption.Unavailable(option,
                ask: OptionReason.Askable) if option.ref == target => ask
          })
          .toRight(ConsentUnavailable(
            s"${target.wireId} is not an option to ask consent for")))

  /** The granters [[consentAsk]] would ask, with their powers. */
  def consentAsks(state: OathState, requester: PlayerId, decisionId: String,
      target: DecisionOptionRef): Either[OathViolation, Vector[ConsentAsk]] =
    consentAsk(state, requester, decisionId, target).map(_.asks)

  /** Whether a grant answers `requester`'s parked `decisionId` with its
    * target. Only a choose-one: one option is its whole answer. A grant at a
    * choose-many opens the option, and the requester goes on choosing
    * (consent slice 2). */
  def grantAnswers(state: OathState, requester: PlayerId, decisionId: String)
      : Boolean =
    val parked = walkerResumeContext(state).flatMap {
      case (ready, _, tree, pending, powers, _) =>
        walkerCall(Right(ProcedureWalker.parkedDecide(ready, tree, pending,
          powers)))
    }
    parked.toOption.flatten.exists(decide =>
      decide.decisionId == decisionId && decide.owner == requester &&
        decide.query.isInstanceOf[DecisionQuery.ChooseOne])

  /** The automatic use whose power-use step the walk is parked at, if it
    * is (automatic power consent design, "Reaching the step"). */
  def powerUseStep(state: OathState): Option[AutomaticUse] =
    val atSlot = state match
      case Ready(ready) => ready.game.current.walkerPending
        .exists(_.at.lastOption.contains(ParkedUses.Slot))
      case _ => false
    // Most parks are elsewhere, so their tree is not rebuilt.
    if !atSlot then None
    else walkerResumeContext(state).toOption.flatMap {
      case (ready, _, tree, pending, powers, _) =>
        walkerCall(Right(ParkedUses.of(ready, tree, pending, powers)))
          .toOption.flatten
    }

  /** The options of a choose-one or a choose-many, as offered. */
  private def offered(decide: Decide): Vector[DecisionOption] =
    decide.query match
      case one: DecisionQuery.ChooseOne => one.options
      case many: DecisionQuery.ChooseMany => many.options
      case _ => Vector.empty

  /** Validates and derives the action tree once, then asks the application for
    * exactly the parked pool's authoritative number of faces.
    *
    * `requester` is bound by the transport and must be the walk's actor. A
    * roll park is always the walk actor's, so that check needs no tree and
    * runs before the rebuild, whose own failures (a restriction, a moved
    * position) would otherwise answer an intruder first.
    */
  def rollWalkerPrepared(state: OathState, requester: PlayerId, pool: PoolKey)(
      prepareFaces: Int => Either[OathViolation, Vector[DieFace]])
      : Either[OathViolation, OathTransition] = (state match {
      case Ready(ready) => requireWalkActor(ready, requester)
      case _ => Right(())
    }).flatMap(_ => resumeWalker(state) {
      case (ready, procedure, tree, pending, powers, startArgs) =>
      for
        parked <- walkerCall(ProcedureWalker.parkedRoll(ready, tree, pending,
          powers).toRight(InvalidEventOrder(
            "current walker position is not a Roll park")))
        _ <- Either.cond(parked._1 == pool, (), InvalidEventOrder(
          s"roll pool ${pool.value} does not match parked pool ${parked._1.value}"))
        faces <- prepareFaces(parked._2)
        outcome <- walkerCall(ProcedureWalker.roll(ready, tree, pending, faces,
          powers, walkerDice))
        transition <- walkerTransition(state, procedure, tree, outcome,
          powers, startArgs, ready.game.current.walkerStartFacts)
      yield transition
    })

  /** Rejects a requester who is not the walk's actor: the turn's player,
    * or a dequeued entry's actor (catalog batch 8, slice 14, ruling 2).
    * Starting a procedure and rolling at its park are the actor's alone,
    * so neither check needs the tree. A menu start has no entry until the
    * menu queues it, so it still requires the turn's player.
    */
  private def requireWalkActor(ready: ReadyGame, requester: PlayerId)
      : Either[OathViolation, Unit] =
    val actor = ready.game.current.walkActor
    Either.cond(requester == actor, (), WrongPlayer(actor, requester))

  private def resumeWalker(state: OathState)(run: (ReadyGame,
      ProcedureRef, Operation, PendingTree, WalkerPowers,
      Vector[DecisionOptionRef]) => Either[OathViolation, OathTransition])
      : Either[OathViolation, OathTransition] =
    walkerResumeContext(state).flatMap:
      case (ready, procedure, tree, pending, powers, startArgs) =>
        run(ready, procedure, tree, pending, powers, startArgs)

  private def walkerResumeContext(state: OathState)
      : Either[OathViolation,
      (ReadyGame, ProcedureRef, Operation, PendingTree, WalkerPowers,
        Vector[DecisionOptionRef])] =
    state match
    case Ready(ready) =>
      for
        procedure <- ready.game.current.walkerProcedure.toRight(
          InvalidEventOrder("no walker procedure is pending"))
        pending <- ready.game.current.walkerPending.toRight(
          InvalidEventOrder("no walker position is pending"))
        // No phase gate: only resume commands are accepted while a walker is
        // pending, so nothing else can change the phase, and the procedure's
        // own build passed its phase gates at start.
        actor = ready.game.current.walkActor
        startArgs = ready.game.current.walkerStartArgs
        tree <- buildWalker(procedure, ready, actor, startArgs,
          starting = false)
        powers = walkerPowers
        _ <- checkRestrictions(tree, powers, ready, actor,
          pending.answered)
      yield (ready, procedure, tree, pending, powers, startArgs)
    case _ => Left(GameNotStarted)

  private def buildWalker(procedure: ProcedureRef, ready: ReadyGame,
      actor: PlayerId, startArgs: Vector[DecisionOptionRef],
      starting: Boolean)
      : Either[OathViolation, Operation] =
    procedure match
      case _: ActionRef.UsePower | TriggeredProcedureRef.ForcedWake |
          TriggeredProcedureRef.FollowUpOffer |
          TriggeredProcedureRef.WakeMenu | TriggeredProcedureRef.RestMenu |
          TriggeredProcedureRef.ActMenu
          if starting =>
        WalkerProcedureRegistry.build(procedure, catalog, ready, actor,
          startArgs, phasePowerCatalog, walkerPowers = walkerPowers)
      case _: ActionRef.UsePower | TriggeredProcedureRef.ForcedWake |
          TriggeredProcedureRef.FollowUpOffer |
          TriggeredProcedureRef.WakeMenu | TriggeredProcedureRef.RestMenu |
          TriggeredProcedureRef.ActMenu =>
        WalkerProcedureRegistry.rebuild(procedure, catalog, ready, actor,
          startArgs, phasePowerCatalog, walkerPowers = walkerPowers)
      case _ => walkerTree(catalog, procedure, ready, actor, startArgs, starting)

  /** A procedure that opts in (`Entry.requiresPlayableOption`) is rejected at
    * start when its first decision offers no option its own answer would
    * accept, before anything is persisted. The decision must be the first
    * thing the tree does, because the preview answers it against the state
    * before the start. A queued entry whose start arguments answer that
    * decision skips the gate (`WalkerProcedureRegistry.gatesStart`).
    */
  private def requirePlayableOption(procedure: ProcedureRef, ready: ReadyGame,
      tree: Operation, outcome: WalkerOutcome, powers: WalkerPowers)
      : Either[OathViolation, Unit] =
    if !WalkerProcedureRegistry.gatesStart(procedure, ready) then Right(())
    else outcome match
      case WalkerOutcome.Parked(pending, events) =>
        if events.exists {
          case WalkerStepRecorded(_, DeltaPayload(ops), _) => ops.nonEmpty
          case _ => false
        } then Left(InvalidEventOrder(s"${procedure.key} runs operations before " +
          "its first decision, so its start cannot be previewed"))
        else WalkerSimulation.previewParked(ready, tree, pending, powers)
          .flatMap(options => Either.cond(options.exists(_.outcome.isRight), (),
            NoPlayableOption(procedure.key)))
      case _ => Right(())

  private def walkerCall[A](result: => Either[OathViolation, A])
      : Either[OathViolation, A] =
    try result
    catch
      case error: IllegalArgumentException => Left(InvalidEventOrder(
        Option(error.getMessage).getOrElse("invalid walker resume position")))

  private def walkerTransition(state: OathState,
      procedure: ProcedureRef, tree: Operation, outcome: WalkerOutcome,
      powers: WalkerPowers, startArgs: Vector[DecisionOptionRef],
      startFacts: Vector[DecisionOptionRef])
      : Either[OathViolation, OathTransition] = outcome match
    case WalkerOutcome.Parked(pending, steps) =>
      val fact = WalkerParked(procedure, pending.at,
        pending.answered, startArgs, startFacts)
      // Which node the walker is parked on can depend on a Branch selecting
      // its children by *live* state (Recover's success-only relic decision
      // checks the just-written roll outcome), so the answerability check
      // must run against the state after `steps` land, not against this
      // command's pre-walk snapshot — a stale-state Branch.select would
      // silently resolve to the wrong node or none at all.
      for
        afterSteps <- foldEvents(state, steps)
        liveReady <- afterSteps match
          case Ready(live) => Right(live)
          case _ => Left(InvalidEventOrder(
            "walker park did not resolve to a Ready state"))
        _ <- checkAnswerable(liveReady, tree, pending, powers, procedure)
        finalState <- evolve(afterSteps, fact)
      yield OathTransition(finalState, steps :+ fact)

    case WalkerOutcome.Finished(_, steps) =>
      GameplayTransition(state, steps ++ offerSkipped(state, procedure,
          steps) :+ WalkerCompleted(procedure))(evolve)
        .flatMap(next(_, procedure))

  /** The Skip line's anchor when a follow-up offer ends on Skip
    * (walker-driven game loop design, plan-time ruling 5). */
  private def offerSkipped(state: OathState, procedure: ProcedureRef,
      steps: Vector[OathEvent]): Vector[OathEvent] = state match
    case Ready(ready) if procedure == TriggeredProcedureRef.FollowUpOffer &&
        steps.exists(FollowUpOffer.skips) =>
      ready.game.current.walkerEntry.map(FollowUpSkipped(_)).toVector
    case _ => Vector.empty

  /** Folds `evolve` over `events` in order, threading state — the same
    * left-fold [[GameplayTransition]] performs internally, exposed here so
    * `walkerTransition` can inspect the intermediate state reached after the
    * step events but before the terminal park/completion fact is applied.
    */
  private def foldEvents(state: OathState, events: Vector[OathEvent])
      : Either[OathViolation, OathState] =
    events.foldLeft[Either[OathViolation, OathState]](Right(state)):
      case (Right(current), event) => evolve(current, event)
      case (failure @ Left(_), _) => failure

  /** A park a client cannot answer is refused at command time, before the
    * `WalkerParked` fact is appended.
    *
    * A Roll park carries no `Decide` and therefore no decision id of its own,
    * so the procedure must declare one; `WalkerDecisionProjector` reads the
    * same accessor when it projects the park, and a park whose id no tree
    * declares would be handed to the client as an unanswerable question.
    */
  private def checkAnswerable(ready: ReadyGame, tree: Operation,
      pending: PendingTree, powers: WalkerPowers, procedure: ProcedureRef)
      : Either[OathViolation, Unit] =
    ProcedureWalker.parkedRoll(ready, tree, pending, powers) match
      case Some(_) =>
        WalkerProcedureRegistry.rollDecisionId(procedure).map(_ => ())
      case None => ProcedureWalker.parkedDecide(ready, tree, pending,
          powers) match
        case Some(_) => Right(())
        case None => Left(InvalidEventOrder(
          "parked walker position is neither a Roll nor a Decide"))
