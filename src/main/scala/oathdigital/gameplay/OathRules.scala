package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.EventEvolution
import oathdigital.gameplay.actions.{Campaign, CampaignCommand,
  CampaignLosingForceRegistry, Challenge, ChallengeCommand, Economy, EconomyCommand, Forge, ForgeCommand, Recover,
  PreparedRecoverModifier, RecoverCommand, Search, SearchCommand, Travel,
  TravelCommand}
import oathdigital.gameplay.actions.{MinorActions, MinorActionCommand}
import oathdigital.gameplay.actions.{Visions, VisionCommand}
import oathdigital.gameplay.actions.{Negotiation, NegotiationCommand}
import oathdigital.gameplay.phases.{Rest, RestCommand, Wake, WakeCommand,
  WarExhaustionRandomPort}
import oathdigital.model._
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.powers.recover.RecoverPowerIntegration
import oathdigital.gameplay.powers.SearchPowers
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.operations.Operation
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerCompleted,
  WalkerOutcome, WalkerParked, WalkerStepRecorded}
import oathdigital.gameplay._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._

/** Deterministic aggregate boundary for setup and gameplay routing. */
final class OathRules(catalog: ExecutableCatalog,
    campaignLosingForceRegistry: CampaignLosingForceRegistry =
      CampaignLosingForceRegistry.default,
    warExhaustionRandomPort: WarExhaustionRandomPort =
      WarExhaustionRandomPort.random)
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

  def handle(state: OathState, command: RecoverCommand)
      : Either[OathViolation, OathTransition] =
    (command match {
      case start: RecoverCommand.Start => withFallback(state, start.playerId,
        MajorActionKind.Recover)(start.modifier match {
          case None => Recover.handle(catalog, state, command)
          case Some(PreparedRecoverModifier(events)) =>
            GameplayTransition(state, events,
              OathContinue.ActActionSelection(start.playerId))(evolve).flatMap {
              prepared => Recover.handle(catalog, prepared.state, RecoverCommand.Roll(
                start.playerId, start.decision, start.dice)).map(rolled =>
                rolled.copy(events = prepared.events ++ rolled.events))
            }
          case Some(_) => Left(InvalidModifierInvocation(
            "unknown Recover modifier contribution"))
        })
      case roll: RecoverCommand.Roll => withFallback(state, roll.playerId,
        MajorActionKind.Recover)(Recover.handle(catalog, state, command))
      case _ => Recover.handle(catalog, state, command)
    }).flatMap { transition =>
      command match {
        case _: RecoverCommand.Stop | _: RecoverCommand.TakeRelic =>
          completeAction(transition)
        case _ => Right(transition)
      }
    }

  /** Starts one action on the generic procedure walker. Recover is the only
    * registered action in this vertical slice.
    */
  def startWalker(state: OathState, action: ActionRef,
      actor: PlayerId): Either[OathViolation, OathTransition] =
    state match {
      case Ready(ready) if ready.game.current.walkerPending.nonEmpty ||
          ready.game.current.walkerAction.nonEmpty =>
        Left(InvalidEventOrder("a walker action is already pending"))
      case Ready(ready) => withFallback(state, actor, MajorActionKind.Recover) {
        for {
          tree <- buildWalker(action, ready, actor, starting = true)
          outcome <- walkerCall(ProcedureWalker.advance(ready, tree, None))
          transition <- walkerTransition(state, ready, action, tree, outcome)
        } yield transition
      }
      case _ => Left(GameNotStarted)
    }

  /** Resolves the current parked Decide. Action identity is reconstructed
    * from the durable walkerAction fact, never supplied by the client.
    */
  def resolveWalker(state: OathState,
      answer: Answered): Either[OathViolation, OathTransition] =
    resumeWalker(state) { case (ready, action, tree, pending) =>
      walkerCall(ProcedureWalker.resolve(ready, tree, pending, answer))
        .flatMap(walkerTransition(state, ready, action, tree, _))
    }

  /** Validates and derives the action tree once, then asks the application for
    * exactly the parked pool's authoritative number of faces.
    */
  def rollWalkerPrepared(state: OathState, pool: PoolKey)(
      prepareFaces: Int => Either[OathViolation, Vector[DieFace]])
      : Either[OathViolation, OathTransition] =
    resumeWalker(state) { case (ready, action, tree, pending) =>
      for {
        parked <- walkerCall(ProcedureWalker.parkedRoll(ready, tree, pending)
          .toRight(InvalidEventOrder(
            "current walker position is not a Roll park")))
        _ <- Either.cond(parked._1 == pool, (), InvalidEventOrder(
          s"roll pool ${pool.value} does not match parked pool ${parked._1.value}"))
        faces <- prepareFaces(parked._2)
        outcome <- walkerCall(ProcedureWalker.roll(ready, tree, pending, faces))
        transition <- walkerTransition(state, ready, action, tree, outcome)
      } yield transition
    }

  private def resumeWalker(state: OathState)(run: (ReadyGame, ActionRef,
      Operation, PendingTree) => Either[OathViolation, OathTransition])
      : Either[OathViolation, OathTransition] =
    walkerResumeContext(state).flatMap { case (ready, action, tree, pending) =>
      run(ready, action, tree, pending)
    }

  private def walkerResumeContext(state: OathState)
      : Either[OathViolation, (ReadyGame, ActionRef, Operation, PendingTree)] =
    state match {
    case Ready(ready) => for {
      action <- ready.game.current.walkerAction.toRight(
        InvalidEventOrder("no walker action is pending"))
      pending <- ready.game.current.walkerPending.toRight(
        InvalidEventOrder("no walker position is pending"))
      _ <- Either.cond(pending.actor == ready.game.current.turn.activePlayer, (),
        WrongPlayer(ready.game.current.turn.activePlayer, pending.actor))
      _ <- Either.cond(ready.game.current.turn.phase == Phase.Act, (),
        WrongPhase(Phase.Act, ready.game.current.turn.phase))
      _ <- Either.cond(ready.game.current.pending.isEmpty, (),
        InvalidEventOrder("legacy pending procedure blocks walker resume"))
      tree <- buildWalker(action, ready, pending.actor, starting = false)
    } yield (ready, action, tree, pending)
    case _ => Left(GameNotStarted)
  }

  private def buildWalker(action: ActionRef, ready: ReadyGame,
      actor: PlayerId, starting: Boolean): Either[OathViolation, Operation] =
    action match {
      case ActionRef.Recover =>
        if (starting) RecoverProcedure.build(catalog, ready, actor)
        else RecoverProcedure.rebuild(catalog, ready, actor)
    }

  private def walkerCall[A](result: => Either[OathViolation, A])
      : Either[OathViolation, A] =
    try result
    catch {
      case error: IllegalArgumentException => Left(InvalidEventOrder(
        Option(error.getMessage).getOrElse("invalid walker resume position")))
    }

  private def walkerTransition(state: OathState, ready: ReadyGame,
      action: ActionRef, tree: Operation, outcome: WalkerOutcome)
      : Either[OathViolation, OathTransition] = outcome match {
    case WalkerOutcome.Parked(pending, steps) =>
      val fact = WalkerParked(pending.actor, action, pending.at,
        pending.answered)
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
        continue <- parkedContinue(liveReady, tree, pending)
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
      pending: PendingTree): Either[OathViolation, OathContinue] =
    ProcedureWalker.parkedRoll(ready, tree, pending) match {
      case Some(_) => Right(OathContinue.AwaitingRecoverRoll(pending.actor,
        DecisionId(RecoverProcedure.rollDecisionId)))
      case None => ProcedureWalker.parkedDecide(ready, tree, pending) match {
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
      case event: RecoverRolled => Recover.evolve(catalog, state, event)
      case event: RecoverPowerEvent =>
        RecoverPowerIntegration.evolve(catalog, state, event)
      case event: RecoverStopped => Recover.evolve(catalog, state, event)
      case event: RelicRecovered => Recover.evolve(catalog, state, event)
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

private[gameplay] object GameStateUpdates {
  def updateCurrent(
      ready: ReadyGame
  )(f: CurrentGameState => CurrentGameState): ReadyGame =
    ready.copy(game = ready.game.copy(current = f(ready.game.current)))
}
