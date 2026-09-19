package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.EventEvolution
import oathdigital.model._
import oathdigital.gameplay.powerresolver.{ContributingPower, PowerCtx, PhasePowers}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerCompleted,
  WalkerOutcome, WalkerParked, WalkerPowers, WalkerProcedureRegistry,
  WalkerSimulation, WalkerStepRecorded}
import oathdigital.model.OathState._
import oathdigital.model.OathViolation._

/** The walker command surface of [[OathRules]], mixed into it.
  *
  * This is the whole of what a client can do to a walker procedure --
  * `startWalker`, `resolveWalker`, `rollWalkerPrepared` and the power and
  * park plumbing they share -- held in one file so `OathRules` can keep
  * carrying the legacy per-action `handle` methods and the turn/phase
  * plumbing without either half crowding the other out. Every method below
  * moved here unchanged; the abstract members are exactly what that block
  * reaches for on the class it is mixed into.
  */
private[gameplay] trait OathRulesWalker {
  self: EventEvolution[OathState, OathEvent, OathViolation] =>

  protected def catalog: ExecutableCatalog
  protected def walkerPowerCatalog: WalkerPowers
  protected def phasePowerCatalog: PhasePowers
  protected def walkerTree: OathRules.WalkerTreeSource
  protected def withFallback(state: OathState, actor: PlayerId,
      action: MajorActionKind)(
      operation: => Either[OathViolation, OathTransition])
      : Either[OathViolation, OathTransition]
  protected def completeAction(transition: OathTransition)
      : Either[OathViolation, OathTransition]
  protected def turnBoundary(transition: OathTransition)
      : Either[OathViolation, OathTransition]
  /** Whether `player` could use a REST power now. */
  protected def restPowerUsable(ready: ReadyGame, player: PlayerId): Boolean

  /** Starts one action on the generic procedure walker. Recover is the only
    * registered action in this vertical slice.
    *
    * `modifiers` (Task 4) is the ordered list of player-selected power ids
    * the client chose; every id is validated against `walkerPowerCatalog`
    * before any node walks (`validateModifiers`), and only the ids that
    * survive select which `ContributingPower`s `walkerPowers` offers to this
    * command's collector -- automatic powers are always offered regardless.
    * An empty `modifiers` behaves exactly like every Recover before this
    * task: nothing to validate, and `walkerPowers` offers only the
    * (currently empty) automatic set.
    */
  def startWalker(state: OathState, procedure: StartableRef,
      requester: PlayerId, modifiers: Vector[PowerId] = Vector.empty,
      startArgs: Vector[DecisionOptionRef] = Vector.empty)
      : Either[OathViolation, OathTransition] =
    state match {
      case Ready(ready) if ready.game.current.walkerPending.nonEmpty ||
          ready.game.current.walkerProcedure.nonEmpty =>
        Left(InvalidEventOrder("a walker procedure is already pending"))
      case Ready(ready) if ready.game.current.result.nonEmpty => Left(GameEnded)
      case Ready(ready) =>
        val activePlayer = ready.game.current.turn.activePlayer
        def run = for {
            _ <- requireActivePlayer(ready, requester)
            _ <- validateModifiers(ready, activePlayer, procedure, modifiers)
            powers = walkerPowers(ready, activePlayer, modifiers)
            tree <- buildWalker(procedure, ready, activePlayer, startArgs,
              starting = true)
            _ <- checkRestrictions(tree, powers, ready, activePlayer)
            outcome <- walkerCall(ProcedureWalker.advance(ready, tree, None,
              powers))
            _ <- requirePlayableOption(procedure, ready, tree, outcome, powers)
            transition <- walkerTransition(state, procedure, tree,
              outcome, powers, modifiers, startArgs)
          } yield transition
        WalkerProcedureRegistry.fallbackKind(procedure).flatMap {
          case Some(kind) => withFallback(state, activePlayer, kind)(run)
          case None => run
        }
      case _ => Left(GameNotStarted)
    }

  /** Starts a procedure the engine triggers, with no client command
    * (walker-ownership spec, Triggered procedures).
    *
    * Strictly sequential: a walker or legacy procedure already pending is a
    * typed rejection rather than a nested start. The procedure's events are
    * appended to `transition`, so the action that triggered it and the
    * procedure journal as one command.
    *
    * When the triggered procedure finishes without parking, `walkerTransition`
    * recomputes its continuation with `continuationIn(phase)`, which knows
    * Act, Wake and Rest. That matches every current
    * `completeAction` caller today -- all Act-gated with
    * `ActActionSelection`, or Wake's Take Wealth -- so a triggered procedure
    * always finishes in one of those phases. A future trigger fired outside
    * Act, Wake or Rest must extend `continuationIn` first.
    */
  private[gameplay] def startTriggered(transition: OathTransition,
      procedure: TriggeredProcedureRef): Either[OathViolation, OathTransition] =
    transition.state match {
      case Ready(ready) if ready.game.current.walkerPending.nonEmpty ||
          ready.game.current.walkerProcedure.nonEmpty ||
          ready.game.current.pending.nonEmpty =>
        Left(InvalidEventOrder(s"cannot start ${procedure.key}: another " +
          "procedure is already pending"))
      case Ready(ready) =>
        val activePlayer = ready.game.current.turn.activePlayer
        val powers = walkerPowers(ready, activePlayer, Vector.empty)
        for {
          tree <- buildWalker(procedure, ready, activePlayer, Vector.empty,
            starting = true)
          _ <- checkRestrictions(tree, powers, ready, activePlayer)
          outcome <- walkerCall(ProcedureWalker.advance(ready, tree, None, powers))
          started <- walkerTransition(transition.state, procedure, tree,
            outcome, powers, Vector.empty, Vector.empty)
        } yield started.copy(events = transition.events ++ started.events)
      case _ => Right(transition)
    }

  /** The single place (Task 4) that turns `walkerPowerCatalog` plus the
    * `modifiers` chosen for THIS command into the vector the walker actually
    * sees. An automatic power (`PowerResolution.Automatic`, the trait
    * default) is offered unconditionally; a player-selected power is offered
    * only when its id appears in `modifiers` -- already validated against
    * the catalog by `validateModifiers` before this runs on `startWalker`'s
    * path. A resumed command (`resolveWalker`/`rollWalkerPrepared`) carries
    * no `modifiers` of its own: `walkerResumeContext` (fix-round ruling I)
    * reads the durable `CurrentGameState.walkerModifiers` -- persisted from
    * `startWalker`'s choice via the `WalkerParked` fact -- and passes THAT
    * here, so a player-selected power chosen at start is still offered on
    * every later resume, and the fold at a shared window (e.g. the tree
    * root) stays identical across the whole action.
    */
  def walkerPowers(ready: ReadyGame, actor: PlayerId,
      modifiers: Vector[PowerId]): WalkerPowers =
    WalkerPowers.selected(walkerPowerCatalog, modifiers)

  /** The exact `ContributingPower`s a player may choose as a `modifiers` id
    * for `actor` on `action` right now: `PlayerSelected` powers in
    * `walkerPowerCatalog` that are `applicable` at THAT ACTION'S
    * modifier-selection window (not a tree node; see `RecoverProcedure`'s
    * doc). `validateModifiers` (below) rejects any id outside this set on
    * `startWalker`, and the pre-start preview
    * (`GameApplicationService.preview`, Task 9a) offers exactly this set, so
    * the two can never drift -- one predicate, not a copy on each side.
    *
    * Batch-1 Task 1: the window comes from
    * `WalkerProcedureRegistry.modifierWindow(procedure)`, not the
    * `PowerWindow.RecoverModifierSelection` literal this method used to name
    * -- harmless with one registered action, but with two it would filter
    * every action's offers through Recover's window. An entry declaring
    * `modifierWindow = None` offers nothing; a procedure absent from
    * `registrations` is a `Left`, since "registers no window" and "is not
    * registered" are different facts and only the first is a rule.
    *
    * `registrations` defaults to the production `WalkerProcedureRegistry
    * .entries`, so production call sites pass nothing. It exists for the same
    * reason `WalkerProcedureRegistry.build`/`rebuild` carry one (see
    * `WalkerProcedureRegistrySuite`'s doc): behaviour differing BETWEEN
    * procedures -- the whole point of this change -- would otherwise be
    * unprovable while only one of a family is registered.
    */
  def offerableWalkerPowers(ready: ReadyGame, actor: PlayerId,
      procedure: ProcedureRef,
      registrations: Map[ProcedureRef, WalkerProcedureRegistry.Entry] =
        WalkerProcedureRegistry.entries)
      : Either[OathViolation, Vector[ContributingPower]] =
    WalkerProcedureRegistry.modifierWindow(procedure, registrations).map {
      case None => Vector.empty
      case Some(window) => walkerPowerCatalog.powers.filter(power =>
        power.resolution == PowerResolution.PlayerSelected &&
        power.applicable(PowerCtx(ready, actor, power.source, window,
          Vector.empty, Sequence(Vector.empty, Some(window)))))
    }

  /** Rejects an unknown or inapplicable `modifiers` id with
    * `InvalidEventOrder` before any node walks and before any event is
    * appended (Task 4). A valid id names a `PlayerSelected` power in
    * `walkerPowerCatalog` that is `applicable` at `procedure`'s own
    * modifier-selection window -- exactly `offerableWalkerPowers`' set,
    * queried above rather than recomputed here, so a procedure declaring
    * `modifierWindow = None` rejects every id for free. An empty `modifiers`
    * validates trivially, matching every Recover before this task.
    * `private[gameplay]`, not `private`, so a suite can pass `registrations`
    * (see `offerableWalkerPowers`): `startWalker` carries no such parameter
    * of its own to thread one through. Production passes nothing.
    */
  private[gameplay] def validateModifiers(ready: ReadyGame, actor: PlayerId,
      procedure: ProcedureRef, modifiers: Vector[PowerId],
      registrations: Map[ProcedureRef, WalkerProcedureRegistry.Entry] =
        WalkerProcedureRegistry.entries): Either[OathViolation, Unit] =
    offerableWalkerPowers(ready, actor, procedure, registrations).flatMap {
      offerable =>
        val offered: Set[PowerId] = offerable.map(_.id).toSet
        val selectable: Set[PowerId] = walkerPowerCatalog.powers
          .filter(_.resolution == PowerResolution.PlayerSelected)
          .map(_.id).toSet
        modifiers.foldLeft[Either[OathViolation, Unit]](Right(())) {
          case (Right(_), id) if offered(id) => Right(())
          // Ruling R3: name the action, not "Recover" -- this string is
          // user-visible and would be flatly wrong for every action the
          // batch port adds.
          case (Right(_), id) if selectable(id) => Left(InvalidEventOrder(
            s"power ${id.value} is not applicable to this ${procedure.key}"))
          case (Right(_), id) => Left(InvalidEventOrder(
            s"unknown or non-selectable power id ${id.value}"))
          case (left, _) => left
        }
    }

  /** Task 3 wiring rule: restrictions run once per command, at command entry,
    * before the walk -- collected across the whole derived `tree` via
    * [[ProcedureWalker.restrictionViolations]]. The first violation (if any)
    * rejects the command with no events appended, exactly like any other
    * `withFallback`/`for`-comprehension short-circuit here.
    */
  private def checkRestrictions(tree: Operation, powers: WalkerPowers,
      ready: ReadyGame, actor: PlayerId): Either[OathViolation, Unit] =
    ProcedureWalker.restrictionViolations(tree, powers, ready, actor)
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
    resumeWalker(state) {
      case (ready, procedure, tree, pending, powers, modifiers, startArgs) =>
        walkerCall(ProcedureWalker.resolve(ready, tree, pending,
          Answered(decisionId, answer, by = requester),
          powers)).flatMap(walkerTransition(state, procedure, tree, _,
            powers, modifiers, startArgs))
    }

  /** Validates and derives the action tree once, then asks the application for
    * exactly the parked pool's authoritative number of faces.
    *
    * `requester` is bound by the transport and must be the active player. A
    * roll park is always the active player's, so that check needs no tree and
    * runs before the rebuild, whose own failures (a restriction, a moved
    * position) would otherwise answer an intruder first.
    */
  def rollWalkerPrepared(state: OathState, requester: PlayerId, pool: PoolKey)(
      prepareFaces: Int => Either[OathViolation, Vector[DieFace]])
      : Either[OathViolation, OathTransition] = (state match {
      case Ready(ready) => requireActivePlayer(ready, requester)
      case _ => Right(())
    }).flatMap(_ => resumeWalker(state) {
      case (ready, procedure, tree, pending, powers, modifiers, startArgs) =>
      for {
        parked <- walkerCall(ProcedureWalker.parkedRoll(ready, tree, pending,
          powers).toRight(InvalidEventOrder(
            "current walker position is not a Roll park")))
        _ <- Either.cond(parked._1 == pool, (), InvalidEventOrder(
          s"roll pool ${pool.value} does not match parked pool ${parked._1.value}"))
        faces <- prepareFaces(parked._2)
        outcome <- walkerCall(ProcedureWalker.roll(ready, tree, pending, faces,
          powers))
        transition <- walkerTransition(state, procedure, tree, outcome,
          powers, modifiers, startArgs)
      } yield transition
    })

  /** Rejects a requester who is not the active player. Starting a procedure
    * and rolling at its park are the active player's alone, so neither check
    * needs the tree.
    */
  private def requireActivePlayer(ready: ReadyGame, requester: PlayerId)
      : Either[OathViolation, Unit] = {
    val activePlayer = ready.game.current.turn.activePlayer
    Either.cond(requester == activePlayer, (),
      WrongPlayer(activePlayer, requester))
  }

  private def resumeWalker(state: OathState)(run: (ReadyGame,
      ProcedureRef, Operation, PendingTree, WalkerPowers, Vector[PowerId],
      Vector[DecisionOptionRef]) => Either[OathViolation, OathTransition])
      : Either[OathViolation, OathTransition] =
    walkerResumeContext(state).flatMap {
      case (ready, procedure, tree, pending, powers, modifiers, startArgs) =>
        run(ready, procedure, tree, pending, powers, modifiers, startArgs)
    }

  /** `modifiers` (fix-round ruling I) is read from the durable
    * `CurrentGameState.walkerModifiers` -- restored by replay from the
    * `WalkerParked` fact `startWalker` wrote, never re-derived -- so a
    * resumed command offers the SAME player-selected powers `startWalker`
    * validated, keeping every shared window's fold identical across the
    * whole action.
    */
  private def walkerResumeContext(state: OathState)
      : Either[OathViolation,
      (ReadyGame, ProcedureRef, Operation, PendingTree, WalkerPowers,
        Vector[PowerId], Vector[DecisionOptionRef])] =
    state match {
    case Ready(ready) => for {
      procedure <- ready.game.current.walkerProcedure.toRight(
        InvalidEventOrder("no walker procedure is pending"))
      pending <- ready.game.current.walkerPending.toRight(
        InvalidEventOrder("no walker position is pending"))
      // No phase gate: only resume commands are accepted while a walker is
      // pending, so nothing else can change the phase, and the procedure's
      // own build passed its phase gates at start.
      _ <- Either.cond(ready.game.current.pending.isEmpty, (),
        InvalidEventOrder("legacy pending procedure blocks walker resume"))
      activePlayer = ready.game.current.turn.activePlayer
      startArgs = ready.game.current.walkerStartArgs
      tree <- buildWalker(procedure, ready, activePlayer, startArgs,
        starting = false)
      modifiers = ready.game.current.walkerModifiers
      powers = walkerPowers(ready, activePlayer, modifiers)
      _ <- checkRestrictions(tree, powers, ready, activePlayer)
    } yield (ready, procedure, tree, pending, powers, modifiers, startArgs)
    case _ => Left(GameNotStarted)
  }

  private def buildWalker(procedure: ProcedureRef, ready: ReadyGame,
      actor: PlayerId, startArgs: Vector[DecisionOptionRef],
      starting: Boolean)
      : Either[OathViolation, Operation] =
    procedure match {
      case _: ActionRef.UsePower if starting => WalkerProcedureRegistry.build(
        procedure, catalog, ready, actor, startArgs, phasePowerCatalog)
      case _: ActionRef.UsePower => WalkerProcedureRegistry.rebuild(
        procedure, catalog, ready, actor, startArgs, phasePowerCatalog)
      case _ => walkerTree(catalog, procedure, ready, actor, startArgs, starting)
    }

  /** A procedure that opts in (`Entry.requiresPlayableOption`) is rejected at
    * start when its first decision offers no option its own answer would
    * accept, before anything is persisted. The decision must be the first
    * thing the tree does, because the preview answers it against the state
    * before the start.
    */
  private def requirePlayableOption(procedure: ProcedureRef, ready: ReadyGame,
      tree: Operation, outcome: WalkerOutcome, powers: WalkerPowers)
      : Either[OathViolation, Unit] =
    if (!WalkerProcedureRegistry.requiresPlayableOption(procedure)) Right(())
    else outcome match {
      case WalkerOutcome.Parked(pending, events) =>
        if (events.exists {
          case step: WalkerStepRecorded => step.ops.nonEmpty
          case _ => false
        }) Left(InvalidEventOrder(s"${procedure.key} runs operations before " +
          "its first decision, so its start cannot be previewed"))
        else WalkerSimulation.previewParked(ready, tree, pending, powers)
          .flatMap(options => Either.cond(options.exists(_.outcome.isRight), (),
            NoPlayableOption(procedure.key)))
      case _ => Right(())
    }

  private def walkerCall[A](result: => Either[OathViolation, A])
      : Either[OathViolation, A] =
    try result
    catch {
      case error: IllegalArgumentException => Left(InvalidEventOrder(
        Option(error.getMessage).getOrElse("invalid walker resume position")))
    }

  private def walkerTransition(state: OathState,
      procedure: ProcedureRef, tree: Operation, outcome: WalkerOutcome,
      powers: WalkerPowers, modifiers: Vector[PowerId],
      startArgs: Vector[DecisionOptionRef])
      : Either[OathViolation, OathTransition] = outcome match {
    case WalkerOutcome.Parked(pending, steps) =>
      val fact = WalkerParked(procedure, pending.at,
        pending.answered, modifiers, startArgs)
      // The park's continuation prompt can depend on a Branch selecting its
      // children by *live* state (Recover's success-only relic decision
      // checks the just-written roll outcome), so `continue` must be derived
      // from the state after `steps` land, not from this command's pre-walk
      // snapshot — a stale-state Branch.select would silently resolve to the
      // wrong node or none at all.
      for {
        afterSteps <- foldEvents(state, steps)
        liveReady <- afterSteps match {
          case Ready(live) => Right(live)
          case _ => Left(InvalidEventOrder(
            "walker park did not resolve to a Ready state"))
        }
        continue <- parkedContinue(liveReady, tree, pending, powers, procedure)
        finalState <- evolve(afterSteps, fact)
      } yield OathTransition(finalState, steps :+ fact, continue)

    case WalkerOutcome.Finished(treeless, steps) =>
      val turn = treeless.game.current.turn
      val conspiracy = treeless.game.current.pending.collect {
        case value: PendingProcedure.Conspiracy if value.awaitingTarget => value
      }
      val continued = conspiracy match {
        case Some(value) => Right(OathContinue.AwaitingConspiracyDecision(
          value.actor, value.decision))
        case None if runsTurnBoundary(procedure) =>
          Right(OathContinue.AwaitingWakeAction(turn.activePlayer))
        case None => continuationIn(turn.phase, turn.activePlayer)
      }
      continued.flatMap(continue => GameplayTransition(state,
          steps :+ WalkerCompleted(procedure), continue)(evolve)
        .flatMap(recordCardPlayFallback(_, procedure, steps))
        .flatMap(transition =>
          if (conspiracy.nonEmpty) Right(transition)
          else if (runsActionBoundary(procedure)) completeAction(transition)
          else if (runsTurnBoundary(procedure)) turnBoundary(transition)
          else Right(transition))
        .flatMap(transition =>
          if (procedure == PhaseTransitionRef.BeginRest) autoFinishRest(transition)
          else Right(transition)))
  }

  /** Diagnostics for unimplemented WHEN PLAYED handlers follow the recorded
    * faceup placement, not the pre-play Search state. Implemented walker
    * powers remain absent from the reviewed fallback registry.
    */
  private def recordCardPlayFallback(transition: OathTransition,
      procedure: ProcedureRef, steps: Vector[OathEvent])
      : Either[OathViolation, OathTransition] =
    if (procedure != ActionRef.Search &&
        procedure != ActionRef.PlayFacedownAdviser) Right(transition)
    else transition.state match {
      case Ready(ready) =>
        val actor = ready.game.current.turn.activePlayer
        val sources = steps.collect { case step: WalkerStepRecorded =>
          step.ops.collect {
            case Play(card: WorldCardId, _, Location.Site(site),
                Orientation.FaceUp, _) =>
              RuleSourceRef.SiteCard(site, card)
            case Play(card: WorldCardId, _, Location.PlayArea(player),
                Orientation.FaceUp, _) =>
              RuleSourceRef.Adviser(player, card)
          }
        }.flatten.distinct
        sources.foldLeft[Either[OathViolation,
            Vector[IgnoredRuleDiagnostic]]](Right(Vector.empty)) {
          case (Right(found), source) => PowerRuntime.ignoredAtSource(catalog,
            ready, actor, MajorActionKind.WhenPlayed, source)
            .map(found ++ _)
          case (failure @ Left(_), _) => failure
        }.map { diagnostics =>
          if (diagnostics.isEmpty) transition
          else transition.copy(events = transition.events :+
            OathEvent.IgnoredRulesRecorded(actor, MajorActionKind.WhenPlayed,
              diagnostics))
        }
      case _ => Right(transition)
    }

  /** Whether the action boundary follows a completed procedure. Only an
    * action runs it, in whatever phase it ran: Take Wealth does, End Wake (a
    * phase transition) and a triggered procedure do not. Carried by the
    * reference's family, never by a registry flag and never by the phase --
    * Take Wealth and End Wake both start in Wake.
    */
  private def runsActionBoundary(procedure: ProcedureRef): Boolean =
    procedure match {
      case _: ActionRef => true
      case _: PhaseTransitionRef | _: TriggeredProcedureRef => false
    }

  /** Whether the turn boundary (round end, then Wake evaluation) follows a
    * completed procedure. Only Finish Rest hands the turn over; it owns its
    * continuation, so `continuationIn` is never asked about `RoundEnd`.
    */
  private def runsTurnBoundary(procedure: ProcedureRef): Boolean =
    procedure == PhaseTransitionRef.FinishRest

  /** Only after Begin Rest: a player with no usable REST power finishes Rest
    * in the same command. After a REST power is used the player always
    * finishes Rest deliberately, so nothing else calls this.
    */
  private def autoFinishRest(transition: OathTransition)
      : Either[OathViolation, OathTransition] = transition.state match {
    case Ready(ready) if !restPowerUsable(ready,
        ready.game.current.turn.activePlayer) =>
      startWalker(transition.state, PhaseTransitionRef.FinishRest,
        ready.game.current.turn.activePlayer).map(finished =>
        finished.copy(events = transition.events ++ finished.events))
    case _ => Right(transition)
  }

  /** Where a completed walker procedure returns its player: read off the
    * phase the procedure finished in, and deliberately not declared by the
    * procedure or carried on its registry entry -- the walker and its
    * registry state what a procedure DOES, and which phase a player is in is
    * neither's business.
    *
    * Rest has a continuation. `RoundEnd` has none, because only Finish Rest
    * reaches it and the turn boundary owns that continuation.
    * A phase with no walker continuation is a typed rejection rather than a
    * default, because a default here is exactly the kind of behaviour nobody
    * chooses: the first procedure registered in Rest should fail loudly and
    * be given its continuation, not silently return its player to Act.
    */
  private def continuationIn(phase: Phase, actor: PlayerId)
      : Either[OathViolation, OathContinue] = phase match {
    case Phase.Act => Right(OathContinue.ActActionSelection(actor))
    case Phase.Wake => Right(OathContinue.AwaitingWakeAction(actor))
    case Phase.Rest => Right(OathContinue.AwaitingRestAction(actor))
    case other => Left(InvalidEventOrder("a walker procedure completed in " +
      s"the ${other.productPrefix} phase, which has no walker continuation"))
  }

  /** Folds `evolve` over `events` in order, threading state — the same
    * left-fold [[GameplayTransition]] performs internally, exposed here so
    * `walkerTransition` can inspect the intermediate state reached after the
    * step events but before the terminal park/completion fact is applied.
    */
  private def foldEvents(state: OathState, events: Vector[OathEvent])
      : Either[OathViolation, OathState] =
    events.foldLeft[Either[OathViolation, OathState]](Right(state)) {
      case (Right(current), event) => evolve(current, event)
      case (failure @ Left(_), _) => failure
    }

  /** Maps a parked walker position to its client-facing continuation prompt
    * by dispatching on the parked node's stable identity — a Roll's pool via
    * [[ProcedureWalker.parkedRoll]], or a Decide's `decisionId` via
    * [[ProcedureWalker.parkedDecide]] — rather than on the park's structural
    * child-index path. Dispatching on path made the mapping fragile: inserting
    * or reordering a node in the action's tree would silently change which
    * path a given decision parks at, and the client would be handed the wrong
    * prompt (and decision id) with no error.
    *
    * The decision id -> continuation mapping itself is looked up on
    * [[WalkerProcedureRegistry.continuationFor]] for `procedure` (I4), rather
    * than matched here against one procedure's own constants (previously
    * `RecoverProcedure.rollDecisionId`/`relicDecisionId`/`choiceDecisionId`)
    * -- this module has no reason to know which decision ids any given
    * procedure declares, only how to resolve the one the walker just parked
    * on.
    */
  private def parkedContinue(ready: ReadyGame, tree: Operation,
      pending: PendingTree, powers: WalkerPowers, procedure: ProcedureRef)
      : Either[OathViolation, OathContinue] = {
    // The awaited player (a Decide's owner, or the active player for a
    // Roll) names who must answer, so a continuation such as
    // `AwaitingOathkeeperRecipient(owner, decision)` is issued together with
    // the same recomputed owner that authorizes the next command.
    val awaited = ProcedureWalker.awaitedPlayer(ready, tree, pending, powers)
      .getOrElse(ready.game.current.turn.activePlayer)
    def continuationFor(decisionId: String): Either[OathViolation, OathContinue] =
      WalkerProcedureRegistry.continuationFor(procedure, decisionId,
        awaited, DecisionId(decisionId))
        .flatMap(_.toRight(InvalidEventOrder(
          "no client continuation is registered for walker decision " +
            decisionId)))

    ProcedureWalker.parkedRoll(ready, tree, pending, powers) match {
      case Some(_) => WalkerProcedureRegistry.rollDecisionId(procedure)
        .flatMap(continuationFor)
      case None => ProcedureWalker.parkedDecide(ready, tree, pending,
          powers) match {
        case Some(decide) => continuationFor(decide.decisionId)
        case None => Left(InvalidEventOrder(
          "parked walker position is neither a Roll nor a Decide"))
      }
    }
  }
}
