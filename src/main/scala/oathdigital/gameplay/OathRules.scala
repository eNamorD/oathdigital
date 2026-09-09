package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.EventEvolution
import oathdigital.gameplay.actions.{Campaign, CampaignCommand,
  CampaignLosingForceRegistry, Challenge, ChallengeCommand, Economy, EconomyCommand, Forge, ForgeCommand,
  Search, SearchCommand, Travel,
  TravelCommand}
import oathdigital.gameplay.actions.{MinorActions, MinorActionCommand}
import oathdigital.gameplay.actions.{Visions, VisionCommand}
import oathdigital.gameplay.actions.{Negotiation, NegotiationCommand}
import oathdigital.gameplay.phases.{Rest, RestCommand, Wake, WakeCommand,
  WarExhaustionRandomPort}
import oathdigital.model._
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.powers.SearchPowers
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.operations.Operation
import oathdigital.gameplay.powerresolver.{ContributingPower,
  ContributionCollector, PowerCtx, PowerResolution, PowerWindow}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerActionRegistry,
  WalkerCompleted, WalkerOutcome, WalkerParked, WalkerPowers,
  WalkerStepRecorded}
import oathdigital.gameplay._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._

/** Deterministic aggregate boundary for setup and gameplay routing.
  *
  * `walkerPowerCatalog` is the full set of `ContributingPower`s this instance
  * may offer to a walker command (empty in production until Task 5 registers
  * a real power) -- a constructor parameter so a suite can drive a real
  * command against a real power. `OathRules.walkerPowers` (Task 4) is the
  * per-command projection of that catalog: restrictions are gathered from it
  * at command entry and its transforms fold the tree during the walk.
  * `walkerTree` is the same injection seam for the action tree a walker
  * command walks; production derives it from the action's own module.
  */
final class OathRules(catalog: ExecutableCatalog,
    campaignLosingForceRegistry: CampaignLosingForceRegistry =
      CampaignLosingForceRegistry.default,
    warExhaustionRandomPort: WarExhaustionRandomPort =
      WarExhaustionRandomPort.random,
    walkerPowerCatalog: WalkerPowers = WalkerPowers.empty,
    walkerTree: OathRules.WalkerTreeSource = OathRules.declaredWalkerTree)
    extends EventEvolution[OathState, OathEvent, OathViolation] {
  private val setup = new FirstGameSetupRules(catalog)

  override val initialState: OathState = setup.initialState

  def handle(
      state: OathState,
      command: WakeCommand
  ): Either[OathViolation, OathTransition] =
    command match {
      case WakeCommand.EndWake(actor) => withFallback(state, actor,
        MajorActionKind.Wake)(Wake.handle(state, command))
      case _ => Wake.handle(state, command)
    }

  def handle(
      state: OathState,
      command: TravelCommand
  ): Either[OathViolation, OathTransition] =
    command match { case TravelCommand.Travel(actor, _) =>
      withFallback(state, actor, MajorActionKind.Travel)(
        Travel.handle(catalog, state, command)).flatMap(completeAction _)
    }

  def handle(state: OathState, command: EconomyCommand)
      : Either[OathViolation, OathTransition] =
    command match {
      case value: EconomyCommand.Muster => withFallback(state, value.playerId,
        MajorActionKind.Muster)(Economy.handle(catalog, state, command))
        .flatMap(completeAction _)
      case value: EconomyCommand.Trade => withFallback(state, value.playerId,
        MajorActionKind.Trade)(Economy.handle(catalog, state, command))
        .flatMap(completeAction _)
    }

  def handle(
      state: OathState,
      command: SearchCommand
  ): Either[OathViolation, OathTransition] =
    (command match {
      case start: SearchCommand.Start => withFallback(state, start.playerId,
        MajorActionKind.Search)(Search.handle(catalog, state, command))
      case _ => Search.handle(catalog, state, command)
    }).flatMap(transition => recordSearchWhenPlayed(command, transition))
      .flatMap { transition =>
      command match {
        case _: SearchCommand.Complete if (transition.state match {
          case Ready(ready) => ready.game.current.pending.exists {
            case p: PendingProcedure.Conspiracy => p.awaitingTarget
            case _ => false
          }
          case _ => false
        }) => Right(transition.copy(continue = transition.state match {
          case Ready(ready) => ready.game.current.pending.collect {
            case p: PendingProcedure.Conspiracy =>
              OathContinue.AwaitingConspiracyDecision(p.actor, p.decision)
          }.get
          case _ => transition.continue
        }))
        case _: SearchCommand.Complete => completeAction(transition)
        case _ => Right(transition)
      }
    }

  private def recordSearchWhenPlayed(command: SearchCommand,
      transition: OathTransition): Either[OathViolation, OathTransition] =
    (command, transition.state) match {
      case (complete: SearchCommand.Complete, Ready(ready)) =>
        SearchPowers.recordPlayHooks(catalog, transition, ready, complete.playerId,
          complete.kept, complete.placement)
      case _ => Right(transition)
    }

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
      modifiers: Vector[PowerId] = Vector.empty)
      : Either[OathViolation, OathTransition] =
    state match {
      case Ready(ready) if ready.game.current.walkerPending.nonEmpty ||
          ready.game.current.walkerAction.nonEmpty =>
        Left(InvalidEventOrder("a walker action is already pending"))
      case Ready(ready) => withFallback(state, actor, MajorActionKind.Recover) {
        for {
          _ <- validateModifiers(ready, actor, modifiers)
          powers = walkerPowers(ready, actor, modifiers)
          tree <- buildWalker(action, ready, actor, starting = true,
            eligibilityRelaxed = eligibilityGathered(ready, actor, powers))
          _ <- checkRestrictions(tree, powers, ready, actor)
          outcome <- walkerCall(ProcedureWalker.advance(ready, tree, None,
            powers))
          transition <- walkerTransition(state, ready, action, tree, outcome,
            powers, modifiers)
        } yield transition
      }
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
    * for `actor` right now: `PlayerSelected` powers in `walkerPowerCatalog`
    * that are `applicable` at `RecoverModifierSelection` -- the window a
    * player-selected power is offered at (not a tree node; see
    * `RecoverProcedure`'s doc). `validateModifiers` (below) rejects any id
    * outside this set on `startWalker`; the pre-start preview
    * (`GameApplicationService.preview`, Task 9a) offers exactly this set so
    * the two can never drift apart -- one predicate, not a copy on each
    * side.
    */
  def offerableWalkerPowers(ready: ReadyGame, actor: PlayerId)
      : Vector[ContributingPower] =
    walkerPowerCatalog.powers.filter(power =>
      power.resolution == PowerResolution.PlayerSelected &&
      power.applicable(PowerCtx(ready, actor, power.source,
        PowerWindow.RecoverModifierSelection, Vector.empty)))

  /** Rejects an unknown or inapplicable `modifiers` id with
    * `InvalidEventOrder` before any node walks and before any event is
    * appended (Task 4). A valid id names a `PlayerSelected` power in
    * `walkerPowerCatalog` that is `applicable` at `RecoverModifierSelection`
    * -- exactly `offerableWalkerPowers`' set, queried above rather than
    * recomputed here. An empty `modifiers` validates trivially, matching
    * every Recover before this task.
    */
  private def validateModifiers(ready: ReadyGame, actor: PlayerId,
      modifiers: Vector[PowerId]): Either[OathViolation, Unit] = {
    val offered: Set[PowerId] = offerableWalkerPowers(ready, actor).map(_.id).toSet
    val selectable: Set[PowerId] = walkerPowerCatalog.powers
      .filter(_.resolution == PowerResolution.PlayerSelected).map(_.id).toSet
    modifiers.foldLeft[Either[OathViolation, Unit]](Right(())) {
      case (Right(_), id) if offered(id) => Right(())
      case (Right(_), id) if selectable(id) => Left(InvalidEventOrder(
        s"power ${id.value} is not applicable to this Recover"))
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

  /** Ruling C (narrowed by fix-round ruling L): the base start gate (a
    * facedown relic already at the site) relaxes only when some applicable
    * power declares a `Transform` at `RecoverActionEligibility`. Gathered
    * directly against the window, with no tree needed yet -- this runs
    * BEFORE `buildWalker` so its answer can steer the build (ruling B: the
    * relaxed check lives here, not inside `RecoverProcedure.build`).
    *
    * Delegates to `OathRules.eligibilityRelaxed` (I6): the SAME predicate
    * `LegalActionProjector.recoverEligible` uses to decide whether the
    * Recover button should appear before a modifier is even chosen -- one
    * shared rule instead of a hand-copied second implementation. This call
    * site passes the command's SELECTED powers (`powers.powers`); the
    * projector passes the full catalog (see that method's doc for why the
    * two sets legitimately differ).
    */
  private def eligibilityGathered(ready: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): Boolean =
    OathRules.eligibilityRelaxed(ready, actor, powers.powers)

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
    resumeWalker(state, actor) { case (ready, action, tree, pending, powers, modifiers) =>
      walkerCall(ProcedureWalker.resolve(ready, tree, pending, answer,
        powers)).flatMap(walkerTransition(state, ready, action, tree, _,
          powers, modifiers))
    }

  /** Validates and derives the action tree once, then asks the application for
    * exactly the parked pool's authoritative number of faces.
    *
    * `actor` is the requester bound by the transport (C1); see `resolveWalker`.
    */
  def rollWalkerPrepared(state: OathState, actor: PlayerId, pool: PoolKey)(
      prepareFaces: Int => Either[OathViolation, Vector[DieFace]])
      : Either[OathViolation, OathTransition] =
    resumeWalker(state, actor) { case (ready, action, tree, pending, powers, modifiers) =>
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
          powers, modifiers)
      } yield transition
    }

  private def resumeWalker(state: OathState, actor: PlayerId)(run: (ReadyGame,
      ActionRef, Operation, PendingTree, WalkerPowers, Vector[PowerId]) =>
      Either[OathViolation, OathTransition])
      : Either[OathViolation, OathTransition] =
    walkerResumeContext(state, actor).flatMap {
      case (ready, action, tree, pending, powers, modifiers) =>
        run(ready, action, tree, pending, powers, modifiers)
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
        Vector[PowerId])] =
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
      tree <- buildWalker(action, ready, pending.actor, starting = false)
      modifiers = ready.game.current.walkerModifiers
      powers = walkerPowers(ready, pending.actor, modifiers)
      _ <- checkRestrictions(tree, powers, ready, pending.actor)
    } yield (ready, action, tree, pending, powers, modifiers)
    case _ => Left(GameNotStarted)
  }

  private def buildWalker(action: ActionRef, ready: ReadyGame,
      actor: PlayerId, starting: Boolean, eligibilityRelaxed: Boolean = false)
      : Either[OathViolation, Operation] =
    walkerTree(catalog, action, ready, actor, starting, eligibilityRelaxed)

  private def walkerCall[A](result: => Either[OathViolation, A])
      : Either[OathViolation, A] =
    try result
    catch {
      case error: IllegalArgumentException => Left(InvalidEventOrder(
        Option(error.getMessage).getOrElse("invalid walker resume position")))
    }

  private def walkerTransition(state: OathState, ready: ReadyGame,
      action: ActionRef, tree: Operation, outcome: WalkerOutcome,
      powers: WalkerPowers, modifiers: Vector[PowerId])
      : Either[OathViolation, OathTransition] = outcome match {
    case WalkerOutcome.Parked(pending, steps) =>
      val fact = WalkerParked(pending.actor, action, pending.at,
        pending.answered, modifiers)
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
        continue <- parkedContinue(liveReady, tree, pending, powers)
        finalState <- evolve(afterSteps, fact)
      } yield OathTransition(finalState, steps :+ fact, continue)

    case WalkerOutcome.Finished(treeless, steps) =>
      val actor = treeless.game.current.turn.activePlayer
      GameplayTransition(state, steps :+ WalkerCompleted(actor, action),
        OathContinue.ActActionSelection(actor))(evolve).flatMap(completeAction)
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
    */
  private def parkedContinue(ready: ReadyGame, tree: Operation,
      pending: PendingTree, powers: WalkerPowers)
      : Either[OathViolation, OathContinue] =
    ProcedureWalker.parkedRoll(ready, tree, pending, powers) match {
      case Some(_) => Right(OathContinue.AwaitingRecoverRoll(pending.actor,
        DecisionId(RecoverProcedure.rollDecisionId)))
      case None => ProcedureWalker.parkedDecide(ready, tree, pending,
          powers) match {
        case Some(decide)
            if decide.decisionId == RecoverProcedure.relicDecisionId =>
          Right(OathContinue.AwaitingRecoverRelic(pending.actor,
            DecisionId(RecoverProcedure.relicDecisionId)))
        case Some(decide)
            if decide.decisionId == RecoverProcedure.choiceDecisionId =>
          Right(OathContinue.AwaitingRecoverRoll(pending.actor,
            DecisionId(RecoverProcedure.choiceDecisionId)))
        case Some(decide) => Left(InvalidEventOrder(
          "no client continuation is registered for walker decision " +
            decide.decisionId))
        case None => Left(InvalidEventOrder(
          "parked walker position is neither a Roll nor a Decide"))
      }
    }

  def handle(state: OathState, command: ForgeCommand)
      : Either[OathViolation, OathTransition] =
    (command match {
      case begin: ForgeCommand.Begin => withFallback(state, begin.playerId,
        MajorActionKind.Forge)(Forge.handle(catalog, state, command))
      case _ => Forge.handle(catalog, state, command)
    }).flatMap { transition =>
      command match {
        case _: ForgeCommand.Complete => completeAction(transition)
        case _ => Right(transition)
      }
    }

  def handle(state: OathState, command: ChallengeCommand)
      : Either[OathViolation, OathTransition] =
    (command match {
      case begin: ChallengeCommand.Begin => withFallback(state, begin.player,
        MajorActionKind.Challenge)(Challenge.handle(catalog, state, command))
      case _ => Challenge.handle(catalog, state, command)
    }).flatMap { transition => command match {
      case _: ChallengeCommand.Complete | _: ChallengeCommand.PlaceResource =>
        completeAction(transition)
      case _ => Right(transition)
    }}

  def handle(state: OathState, command: MinorActionCommand)
      : Either[OathViolation, OathTransition] =
    MinorActions.handle(catalog, state, command).flatMap { transition =>
      (command, transition.state) match {
        case (play: MinorActionCommand.PlayFacedownAdviser, Ready(ready)) =>
          SearchPowers.recordPlayHooks(catalog, transition, ready, play.player,
            play.adviser, play.placement)
        case _ => Right(transition)
      }
    }.flatMap(completeAction _)

  def handle(state: OathState, command: VisionCommand)
      : Either[OathViolation, OathTransition] =
    Visions.handle(catalog, state, command).flatMap { transition => command match {
      case _: VisionCommand.PlayConspiracy
          if (transition.state match {
            case Ready(ready) => ready.game.current.pending.isEmpty
            case _ => false
          }) => completeAction(transition)
      case _: VisionCommand.Reveal => completeAction(transition)
      case _ => Right(transition)
    }}

  def handle(state: OathState, command: NegotiationCommand)
      : Either[OathViolation, OathTransition] =
    Negotiation.handle(catalog, state, command).flatMap { transition => command match {
      case _: NegotiationCommand.Accept if (transition.state match {
        case Ready(ready) => ready.game.current.pending.isEmpty
        case _ => false
      }) => completeAction(transition)
      case _: NegotiationCommand.Decline => completeAction(transition)
      case _ => Right(transition)
    }}

  def handle(state: OathState, command: CampaignCommand)
      : Either[OathViolation, OathTransition] =
    (command match {
      case begin: CampaignCommand.Start => withFallback(state, begin.playerId,
        MajorActionKind.Campaign)(Campaign.handle(catalog, state, command,
          campaignLosingForceRegistry))
      case begin: CampaignCommand.StartRaid => withFallback(state, begin.playerId,
        MajorActionKind.Campaign)(Campaign.handle(catalog, state, command,
          campaignLosingForceRegistry))
      case _ => Campaign.handle(catalog, state, command, campaignLosingForceRegistry)
    })
      .flatMap { transition =>
      command match {
        case _: CampaignCommand.Place | _: CampaignCommand.RelocateRaidPawn =>
          completeAction(transition)
        case _: CampaignCommand.Sacrifice if (transition.state match {
          case Ready(ready) => ready.game.current.pending.isEmpty
          case _ => false
        }) => completeAction(transition)
        case _ => Right(transition)
      }
    }

  def handle(state: OathState, command: RestCommand)
      : Either[OathViolation, OathTransition] =
    (command match {
      case begin: RestCommand.Begin => withFallback(state, begin.playerId,
        MajorActionKind.Rest)(Rest.handle(catalog, state, command,
          warExhaustionRandomPort))
      case _ => Rest.handle(catalog, state, command, warExhaustionRandomPort)
    }).flatMap { transition =>
      command match {
        case _: RestCommand.Finish if (transition.state match {
          case Ready(ready) => ready.game.current.result.nonEmpty
          case _ => false
        }) => Right(transition)
        case _: RestCommand.Finish => enterWake(transition)
        case _ => Right(transition)
      }
    }

  def chooseOathkeeperRecipient(state: OathState, actor: PlayerId,
      decision: DecisionId, recipient: PlayerId) =
    StateBasedEvaluation.chooseRecipient(
      catalog, state, actor, decision, recipient)

  override def evolve(
      state: OathState,
      event: OathEvent
  ): Either[OathViolation, OathState] =
    event match {
      case recorded: IgnoredRulesRecorded => state match {
        case Ready(ready) => (if (recorded.action == MajorActionKind.WhenPlayed)
          recorded.diagnostics.map(_.source).distinct.foldLeft[
            Either[OathViolation, Vector[IgnoredRuleDiagnostic]]](Right(Vector.empty)) {
              case (Right(found), source) => PowerRuntime.ignoredAtSource(
                catalog, ready, recorded.playerId, recorded.action, source)
                .map(found ++ _)
              case (failure @ Left(_), _) => failure
            }
          else PowerRuntime.ignored(catalog, ready,
            recorded.playerId, recorded.action)).flatMap(expected =>
          Either.cond(expected == recorded.diagnostics, state,
            InvalidEventOrder("ignored-rule diagnostics do not match authoritative discovery")))
        case _ => Left(GameNotStarted)
      }
      case event: WealthTaken => Wake.evolve(state, event)
      case event: WakeEnded => Wake.evolve(state, event)
      case event: Traveled => Travel.evolve(catalog, state, event)
      case event: Mustered => Economy.evolve(catalog, state, event)
      case event: Traded => Economy.evolve(catalog, state, event)
      case event: SearchStarted => Search.evolve(catalog, state, event)
      case event: SearchCompleted => Search.evolve(catalog, state, event)
      case event: WalkerStepRecorded => ProcedureWalker.applyRecorded(state, event)
      case event: WalkerParked => ProcedureWalker.applyRecorded(state, event)
      case event: WalkerCompleted => ProcedureWalker.applyRecorded(state, event)
      case event: ForgeStarted => Forge.evolve(catalog, state, event)
      case event: ForgeCompleted => Forge.evolve(catalog, state, event)
      case event: BannerChallengeStarted => Challenge.evolve(catalog, state, event)
      case event: BannerRibbonChoiceMade => Challenge.evolve(catalog, state, event)
      case event: BannerChallengeCompleted => Challenge.evolve(catalog, state, event)
      case event: BannerResourcePlaced => Challenge.evolve(catalog, state, event)
      case event: FacedownAdviserDiscarded => MinorActions.evolve(catalog, state, event)
      case event: FacedownAdviserPlayed => MinorActions.evolve(catalog, state, event)
      case event: SiteRelicsPeeked => MinorActions.evolve(catalog, state, event)
      case event: OwnedRelicRevealed => MinorActions.evolve(catalog, state, event)
      case event: WarbandsMoved => MinorActions.evolve(catalog, state, event)
      case event: VisionRevealed => Visions.evolve(catalog, state, event)
      case event: ConspiracyStarted => Visions.evolve(catalog, state, event)
      case event: ConspiracyCompleted => Visions.evolve(catalog, state, event)
      case event: NegotiationStarted => Negotiation.evolve(catalog, state, event)
      case event: NegotiationTermsReplaced => Negotiation.evolve(catalog, state, event)
      case event: NegotiationAccepted => Negotiation.evolve(catalog, state, event)
      case event: NegotiationDeclined => Negotiation.evolve(catalog, state, event)
      case event: NegotiationCompleted => Negotiation.evolve(catalog, state, event)
      case event: CampaignStarted => Campaign.evolve(catalog, state, event,
        campaignLosingForceRegistry)
      case event: CampaignPlanChosen => Campaign.evolve(catalog, state, event,
        campaignLosingForceRegistry)
      case event: CampaignPlansFinished => Campaign.evolve(catalog, state, event,
        campaignLosingForceRegistry)
      case event: CampaignSacrificed => Campaign.evolve(catalog, state, event,
        campaignLosingForceRegistry)
      case event: CampaignConquered => Campaign.evolve(catalog, state, event,
        campaignLosingForceRegistry)
      case event: CampaignRaided => Campaign.evolve(catalog, state, event,
        campaignLosingForceRegistry)
      case event: CampaignRaidPawnRelocated => Campaign.evolve(catalog, state, event,
        campaignLosingForceRegistry)
      case event: RestStarted => Rest.evolve(catalog, state, event)
      case event: RestPowerEvent => Rest.evolve(catalog, state, event)
      case event: RestCompleted => Rest.evolve(catalog, state, event)
      case event: BanditsRefilled => StateBasedEvaluation.evolve(catalog, state, event)
      case event: OathkeeperChanged => StateBasedEvaluation.evolve(catalog, state, event)
      case event: OathkeeperRecipientChoiceStarted =>
        StateBasedEvaluation.evolve(catalog, state, event)
      case event: OathkeeperRecipientChosen =>
        StateBasedEvaluation.evolve(catalog, state, event)
      case event: UsurperFlipped => StateBasedEvaluation.evolve(catalog, state, event)
      case event: UsurperVictory => StateBasedEvaluation.evolve(catalog, state, event)
      case event: VisionVictory => StateBasedEvaluation.evolve(catalog, state, event)
      case event: RoundEnded => StateBasedEvaluation.evolve(catalog, state, event)
      case event: WarExhaustionResolved =>
        StateBasedEvaluation.evolve(catalog, state, event)
      case setupEvent => setup.evolve(state, setupEvent)
    }

  private def completeAction(transition: OathTransition) =
    if (hasResult(transition.state)) Right(transition)
    else recordBoundaryFallback(transition).flatMap(afterDiagnostics =>
      appendEvaluation(afterDiagnostics,
      StateBasedEvaluation.banditRefill(catalog, _)))
      .flatMap { afterRefill =>
        if (hasResult(afterRefill.state)) Right(afterRefill)
        else appendEvaluation(afterRefill, StateBasedEvaluation.afterAction)
      }

  private def recordBoundaryFallback(transition: OathTransition) =
    transition.state match {
      case Ready(ready) =>
        val actor = ready.game.current.turn.activePlayer
        PowerRuntime.ignored(catalog, ready, actor,
          MajorActionKind.ActionBoundary).map { diagnostics =>
          if (diagnostics.isEmpty) transition else transition.copy(events =
            transition.events :+ IgnoredRulesRecorded(actor,
              MajorActionKind.ActionBoundary, diagnostics))
        }
      case _ => Right(transition)
    }

  private def withFallback(state: OathState, actor: PlayerId,
      action: MajorActionKind)(operation: => Either[OathViolation, OathTransition]) =
    state match {
      case Ready(ready) => PowerRuntime.ignored(catalog, ready, actor, action)
        .flatMap { diagnostics => operation.map { transition =>
          if (diagnostics.isEmpty) transition
          else transition.copy(events = IgnoredRulesRecorded(actor, action,
            diagnostics) +: transition.events)
        }}
      case _ => operation
    }

  private def hasResult(state: OathState): Boolean = state match {
    case Ready(ready) => ready.game.current.result.nonEmpty
    case _ => false
  }

  private def enterWake(transition: OathTransition): Either[OathViolation, OathTransition] = {
    def append(current: OathTransition, event: OathEvent) =
      evolve(current.state, event).map(next => current.copy(state = next,
        events = current.events :+ event, continue = event match {
          case UsurperVictory(winner) => OathContinue.GameFinished(winner)
          case VisionVictory(winner, _) => OathContinue.GameFinished(winner)
          case _ => current.continue
        }))
    val prepared = transition.state match {
      case Ready(ready) => PowerRuntime.ignored(catalog, ready,
        ready.game.current.turn.activePlayer, MajorActionKind.Wake).map { diagnostics =>
        if (diagnostics.isEmpty) transition else transition.copy(events =
          transition.events :+ IgnoredRulesRecorded(
            ready.game.current.turn.activePlayer, MajorActionKind.Wake, diagnostics))
      }
      case _ => Right(transition)
    }
    prepared.flatMap(current => StateBasedEvaluation.atWake(current.state).flatMap {
      case Some(win: UsurperVictory) => append(current, win)
      case Some(flip: UsurperFlipped) => append(current, flip).flatMap { after =>
        StateBasedEvaluation.visionAtWake(after.state).flatMap {
          case Some(vision) => append(after, vision)
          case None => Right(after)
        }
      }
      case None => StateBasedEvaluation.visionAtWake(current.state).flatMap {
        case Some(vision) => append(current, vision)
        case None => Right(current)
      }
      case Some(other) => Left(InvalidEventOrder(
        s"unexpected Wake evaluation event: $other"))
    })
  }

  private def appendEvaluation(transition: OathTransition,
      evaluate: OathState => Either[OathViolation, Option[OathEvent]]) =
    evaluate(transition.state).flatMap {
      case None => Right(transition)
      case Some(event) => evolve(transition.state, event).map { next =>
        transition.copy(state = next, events = transition.events :+ event,
          continue = event match {
            case UsurperVictory(winner) => OathContinue.GameFinished(winner)
            case OathkeeperRecipientChoiceStarted(actor, decision, _) =>
              OathContinue.AwaitingOathkeeperRecipient(actor, decision)
            case _ => transition.continue
          })
      }
    }
}

object OathRules {
  /** How a walker command derives the action tree it walks. `starting`
    * distinguishes a fresh `startWalker` (the action's full start gates) from
    * a resume (rebuild only). `eligibilityRelaxed` (Task 5, ruling B) carries
    * `startWalker`'s relaxed-eligibility decision through to a `starting`
    * build; a resume ignores it (its rebuild never re-runs the start gates).
    */
  type WalkerTreeSource = (ExecutableCatalog, ActionRef, ReadyGame, PlayerId,
    Boolean, Boolean) => Either[OathViolation, Operation]

  /** Production tree source: every registered action declares its own tree
    * via [[oathdigital.gameplay.walker.WalkerActionRegistry]] (Task 8) --
    * this is no longer an exhaustive match on [[ActionRef]], so an
    * unregistered action rejects with a typed `Left` instead of a
    * `MatchError`.
    */
  val declaredWalkerTree: WalkerTreeSource =
    (catalog, action, ready, actor, starting, eligibilityRelaxed) =>
      if (starting) WalkerActionRegistry.build(action, catalog, ready, actor,
        eligibilityRelaxed)
      else WalkerActionRegistry.rebuild(action, catalog, ready, actor)

  /** I6: the single relaxed-eligibility rule, shared between the command
    * (`OathRules.startWalker`, via `eligibilityGathered`) and the projection
    * layer (`LegalActionProjector.recoverEligible`), so the two never drift
    * apart the way a hand-copied second implementation eventually will.
    *
    * True when SOME applicable power in `powers` declares a `Transform` at
    * `RecoverActionEligibility`. A `Restriction` at this window never grants
    * eligibility on its own -- it can only reject the action for an
    * unrelated reason, and its mere presence must not be read as "eligible"
    * (that reading previously let a power that *forbids* Recover also
    * *enable* it).
    *
    * This method's contract is only the window and the Transform-presence
    * check; it takes no position on WHICH powers `powers` should contain --
    * that choice is deliberately the caller's, and the two production
    * callers deliberately choose different sets. `startWalker` passes the
    * command's SELECTED powers (`WalkerPowers.selected`, already narrowed to
    * the modifiers this command chose) because it is deciding whether THIS
    * command may proceed. `recoverEligible` passes the FULL catalog because
    * it answers a different question -- "could some power relax this if the
    * player chose it as a modifier" -- asked before any modifier has been
    * picked, so the Recover button can appear on a relic-less Catacombs
    * site even though the eventual `StartWalker` command still must select
    * the power to actually use it.
    *
    * NOTE: this checks the *presence* of a Transform, not its *effect*. A
    * future power whose Transform at this window does not actually supply a
    * relic would still relax the gate. Known limitation, worth revisiting
    * once a second power hooks this window.
    */
  def eligibilityRelaxed(ready: ReadyGame, actor: PlayerId,
      powers: Vector[ContributingPower]): Boolean = {
    val window = PowerWindow.RecoverActionEligibility
    ContributionCollector.gather(window, powers,
      power => PowerCtx(ready, actor, power.source, window, Vector.empty))
      .transforms.nonEmpty
  }
}

private[gameplay] object GameStateUpdates {
  def updateCurrent(
      ready: ReadyGame
  )(f: CurrentGameState => CurrentGameState): ReadyGame =
    ready.copy(game = ready.game.copy(current = f(ready.game.current)))
}
