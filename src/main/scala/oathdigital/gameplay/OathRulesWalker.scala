package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.EventEvolution
import oathdigital.model._
import oathdigital.gameplay.operations.{Operation, Sequence}
import oathdigital.gameplay.powerresolver.{ContributingPower, PowerCtx,
  PowerResolution}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerActionRegistry,
  WalkerCompleted, WalkerOutcome, WalkerParked, WalkerPowers}
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._

/** The walker command surface of [[OathRules]], mixed into it.
  *
  * This is the whole of what a client can do to a walker action --
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
  protected def walkerTree: OathRules.WalkerTreeSource
  protected def withFallback(state: OathState, actor: PlayerId,
      action: MajorActionKind)(
      operation: => Either[OathViolation, OathTransition])
      : Either[OathViolation, OathTransition]
  protected def completeAction(transition: OathTransition)
      : Either[OathViolation, OathTransition]

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
  def startWalker(state: OathState, action: ActionRef, actor: PlayerId,
      modifiers: Vector[PowerId] = Vector.empty,
      startArgs: Vector[DecisionOptionRef] = Vector.empty)
      : Either[OathViolation, OathTransition] =
    state match {
      case Ready(ready) if ready.game.current.walkerPending.nonEmpty ||
          ready.game.current.walkerAction.nonEmpty =>
        Left(InvalidEventOrder("a walker action is already pending"))
      case Ready(ready) => WalkerActionRegistry.fallbackKind(action)
        .flatMap(kind => withFallback(state, actor, kind) {
          for {
            _ <- validateModifiers(ready, actor, action, modifiers)
            powers = walkerPowers(ready, actor, modifiers)
            tree <- buildWalker(action, ready, actor, startArgs,
              starting = true)
            _ <- checkRestrictions(tree, powers, ready, actor)
            outcome <- walkerCall(ProcedureWalker.advance(ready, tree, None,
              powers))
            transition <- walkerTransition(state, ready, action, tree, outcome,
              powers, modifiers, startArgs)
          } yield transition
        })
      case _ => Left(GameNotStarted)
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
    * `WalkerActionRegistry.modifierWindow(action)`, not the
    * `PowerWindow.RecoverModifierSelection` literal this method used to name
    * -- harmless with one registered action, but with two it would filter
    * every action's offers through Recover's window. An entry declaring
    * `modifierWindow = None` offers nothing; an action absent from
    * `registrations` is a `Left`, since "registers no window" and "is not
    * registered" are different facts and only the first is a rule.
    *
    * `registrations` defaults to the production `WalkerActionRegistry
    * .entries`, so production call sites pass nothing. It exists for the same
    * reason `WalkerActionRegistry.build`/`rebuild` carry one (see
    * `WalkerActionRegistrySuite`'s doc): `ActionRef` is sealed with one
    * inhabitant, so behaviour differing BETWEEN actions -- the whole point of
    * this change -- is otherwise unprovable until a second one registers.
    */
  def offerableWalkerPowers(ready: ReadyGame, actor: PlayerId,
      action: ActionRef,
      registrations: Map[ActionRef, WalkerActionRegistry.Entry] =
        WalkerActionRegistry.entries)
      : Either[OathViolation, Vector[ContributingPower]] =
    WalkerActionRegistry.modifierWindow(action, registrations).map {
      case None => Vector.empty
      case Some(window) => walkerPowerCatalog.powers.filter(power =>
        power.resolution == PowerResolution.PlayerSelected &&
        power.applicable(PowerCtx(ready, actor, power.source, window,
          Vector.empty, Sequence(Vector.empty, Some(window)))))
    }

  /** Rejects an unknown or inapplicable `modifiers` id with
    * `InvalidEventOrder` before any node walks and before any event is
    * appended (Task 4). A valid id names a `PlayerSelected` power in
    * `walkerPowerCatalog` that is `applicable` at `action`'s own
    * modifier-selection window -- exactly `offerableWalkerPowers`' set,
    * queried above rather than recomputed here, so an action declaring
    * `modifierWindow = None` rejects every id for free. An empty `modifiers`
    * validates trivially, matching every Recover before this task.
    * `private[gameplay]`, not `private`, so a suite can pass `registrations`
    * (see `offerableWalkerPowers`): `startWalker` carries no such parameter
    * of its own to thread one through. Production passes nothing.
    */
  private[gameplay] def validateModifiers(ready: ReadyGame, actor: PlayerId,
      action: ActionRef, modifiers: Vector[PowerId],
      registrations: Map[ActionRef, WalkerActionRegistry.Entry] =
        WalkerActionRegistry.entries): Either[OathViolation, Unit] =
    offerableWalkerPowers(ready, actor, action, registrations).flatMap {
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
            s"power ${id.value} is not applicable to this ${action.key}"))
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

  /** Resolves the current parked Decide. Action identity is reconstructed
    * from the durable walkerAction fact, never supplied by the client.
    *
    * `actor` is the requester bound by the transport (C1): it is checked
    * against the durable parked position's own actor in
    * `walkerResumeContext` before anything runs, so a seated player can
    * never resume another player's parked walker action.
    */
  def resolveWalker(state: OathState, actor: PlayerId,
      answer: Answered): Either[OathViolation, OathTransition] =
    resumeWalker(state, actor) {
      case (ready, action, tree, pending, powers, modifiers, startArgs) =>
        walkerCall(ProcedureWalker.resolve(ready, tree, pending, answer,
          powers)).flatMap(walkerTransition(state, ready, action, tree, _,
            powers, modifiers, startArgs))
    }

  /** Validates and derives the action tree once, then asks the application for
    * exactly the parked pool's authoritative number of faces.
    *
    * `actor` is the requester bound by the transport (C1); see `resolveWalker`.
    */
  def rollWalkerPrepared(state: OathState, actor: PlayerId, pool: PoolKey)(
      prepareFaces: Int => Either[OathViolation, Vector[DieFace]])
      : Either[OathViolation, OathTransition] =
    resumeWalker(state, actor) {
      case (ready, action, tree, pending, powers, modifiers, startArgs) =>
      for {
        parked <- walkerCall(ProcedureWalker.parkedRoll(ready, tree, pending,
          powers).toRight(InvalidEventOrder(
            "current walker position is not a Roll park")))
        _ <- Either.cond(parked._1 == pool, (), InvalidEventOrder(
          s"roll pool ${pool.value} does not match parked pool ${parked._1.value}"))
        faces <- prepareFaces(parked._2)
        outcome <- walkerCall(ProcedureWalker.roll(ready, tree, pending, faces,
          powers))
        transition <- walkerTransition(state, ready, action, tree, outcome,
          powers, modifiers, startArgs)
      } yield transition
    }

  private def resumeWalker(state: OathState, actor: PlayerId)(run: (ReadyGame,
      ActionRef, Operation, PendingTree, WalkerPowers, Vector[PowerId],
      Vector[DecisionOptionRef]) => Either[OathViolation, OathTransition])
      : Either[OathViolation, OathTransition] =
    walkerResumeContext(state, actor).flatMap {
      case (ready, action, tree, pending, powers, modifiers, startArgs) =>
        run(ready, action, tree, pending, powers, modifiers, startArgs)
    }

  /** `modifiers` (fix-round ruling I) is read from the durable
    * `CurrentGameState.walkerModifiers` -- restored by replay from the
    * `WalkerParked` fact `startWalker` wrote, never re-derived -- so a
    * resumed command offers the SAME player-selected powers `startWalker`
    * validated, keeping every shared window's fold identical across the
    * whole action.
    *
    * `actor` is the requester bound by the transport (C1): it is checked
    * against `pending.actor` -- the durable owner of the parked position --
    * independently of the `pending.actor == turn.activePlayer` sanity check
    * below (that check compares two pieces of state against each other and
    * proves nothing about who is asking; this one compares the caller
    * against the state).
    */
  private def walkerResumeContext(state: OathState, actor: PlayerId)
      : Either[OathViolation,
      (ReadyGame, ActionRef, Operation, PendingTree, WalkerPowers,
        Vector[PowerId], Vector[DecisionOptionRef])] =
    state match {
    case Ready(ready) => for {
      action <- ready.game.current.walkerAction.toRight(
        InvalidEventOrder("no walker action is pending"))
      pending <- ready.game.current.walkerPending.toRight(
        InvalidEventOrder("no walker position is pending"))
      _ <- Either.cond(pending.actor == ready.game.current.turn.activePlayer, (),
        WrongPlayer(ready.game.current.turn.activePlayer, pending.actor))
      _ <- Either.cond(actor == pending.actor, (),
        WrongPlayer(pending.actor, actor))
      _ <- Either.cond(ready.game.current.turn.phase == Phase.Act, (),
        WrongPhase(Phase.Act, ready.game.current.turn.phase))
      _ <- Either.cond(ready.game.current.pending.isEmpty, (),
        InvalidEventOrder("legacy pending procedure blocks walker resume"))
      startArgs = ready.game.current.walkerStartArgs
      tree <- buildWalker(action, ready, pending.actor, startArgs,
        starting = false)
      modifiers = ready.game.current.walkerModifiers
      powers = walkerPowers(ready, pending.actor, modifiers)
      _ <- checkRestrictions(tree, powers, ready, pending.actor)
    } yield (ready, action, tree, pending, powers, modifiers, startArgs)
    case _ => Left(GameNotStarted)
  }

  private def buildWalker(action: ActionRef, ready: ReadyGame,
      actor: PlayerId, startArgs: Vector[DecisionOptionRef],
      starting: Boolean)
      : Either[OathViolation, Operation] =
    walkerTree(catalog, action, ready, actor, startArgs, starting)

  private def walkerCall[A](result: => Either[OathViolation, A])
      : Either[OathViolation, A] =
    try result
    catch {
      case error: IllegalArgumentException => Left(InvalidEventOrder(
        Option(error.getMessage).getOrElse("invalid walker resume position")))
    }

  private def walkerTransition(state: OathState, ready: ReadyGame,
      action: ActionRef, tree: Operation, outcome: WalkerOutcome,
      powers: WalkerPowers, modifiers: Vector[PowerId],
      startArgs: Vector[DecisionOptionRef])
      : Either[OathViolation, OathTransition] = outcome match {
    case WalkerOutcome.Parked(pending, steps) =>
      val fact = WalkerParked(pending.actor, action, pending.at,
        pending.answered, modifiers, startArgs)
      // The park's continuation prompt can depend on a Branch selecting its
      // children by *live* state (Recover's success-only relic decision
      // checks the just-written roll outcome), so `continue` must be derived
      // from the state after `steps` land, not from `ready` (this command's
      // pre-walk snapshot) — a stale-state Branch.select would silently
      // resolve to the wrong node or none at all.
      for {
        afterSteps <- foldEvents(state, steps)
        liveReady <- afterSteps match {
          case Ready(live) => Right(live)
          case _ => Left(InvalidEventOrder(
            "walker park did not resolve to a Ready state"))
        }
        continue <- parkedContinue(liveReady, tree, pending, powers, action)
        finalState <- evolve(afterSteps, fact)
      } yield OathTransition(finalState, steps :+ fact, continue)

    case WalkerOutcome.Finished(treeless, steps) =>
      val actor = treeless.game.current.turn.activePlayer
      completionIn(treeless.game.current.turn.phase, actor).flatMap {
        completed => GameplayTransition(state,
          steps :+ WalkerCompleted(actor, action), completed.continue)(evolve)
          .flatMap(transition =>
            if (completed.runsActionBoundary) completeAction(transition)
            else Right(transition))
      }
  }

  /** What a completed walker action hands back, and whether the Act action
    * boundary runs after it (batch-1 Task 7).
    *
    * Read off the phase the action completed in, and deliberately not
    * declared by the action or carried on its registry entry: the walker and
    * its registry state what an action DOES, and which phase a player is in
    * is neither the walker's business nor an action's. Every walker action
    * before Take Wealth ran in Act, so this was an `ActActionSelection`
    * literal with an unconditional `completeAction` after it; a Wake action
    * ported under that literal would have ended the player's Wake phase after
    * one take and run the Act boundary's bandit refill and state-based
    * evaluation in the middle of Wake.
    *
    * A phase with no walker continuation is a typed rejection rather than a
    * default, because a default here is exactly the kind of behaviour nobody
    * chooses: the first action registered in Rest should fail loudly and be
    * given its continuation, not silently return its player to Act.
    */
  private final case class WalkerCompletion(continue: OathContinue,
      runsActionBoundary: Boolean)

  private def completionIn(phase: Phase, actor: PlayerId)
      : Either[OathViolation, WalkerCompletion] = phase match {
    case Phase.Act => Right(WalkerCompletion(
      OathContinue.ActActionSelection(actor), runsActionBoundary = true))
    case Phase.Wake => Right(WalkerCompletion(
      OathContinue.AwaitingWakeAction(actor), runsActionBoundary = false))
    case other => Left(InvalidEventOrder("a walker action completed in the " +
      s"${other.productPrefix} phase, which has no walker continuation"))
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
    * [[WalkerActionRegistry.continuationFor]] for `action` (I4), rather than
    * matched here against one action's own constants (previously
    * `RecoverProcedure.rollDecisionId`/`relicDecisionId`/`choiceDecisionId`)
    * -- this module has no reason to know which decision ids any given
    * action declares, only how to resolve the one the walker just parked
    * on.
    */
  private def parkedContinue(ready: ReadyGame, tree: Operation,
      pending: PendingTree, powers: WalkerPowers, action: ActionRef)
      : Either[OathViolation, OathContinue] = {
    def continuationFor(decisionId: String): Either[OathViolation, OathContinue] =
      WalkerActionRegistry.continuationFor(action, decisionId, pending.actor,
        DecisionId(decisionId)).flatMap(_.toRight(InvalidEventOrder(
          "no client continuation is registered for walker decision " +
            decisionId)))

    ProcedureWalker.parkedRoll(ready, tree, pending, powers) match {
      case Some(_) => WalkerActionRegistry.rollDecisionId(action)
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
