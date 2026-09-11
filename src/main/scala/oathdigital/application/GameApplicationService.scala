package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.{IgnoredRuleDiagnostic, MajorActionKind,
  PowerRuntime, OathContinue, OathEvent, OathRules, OathState,
  OathTransition, OathViolation, OrderedRuleInvocation}
import oathdigital.gameplay.actions.{Campaign, CampaignCommand, CampaignRules,
  ChallengeCommand, EconomyCommand, SearchCommand, TravelCommand}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.walker.WalkerActionRegistry
import oathdigital.gameplay.actions.MinorActionCommand
import oathdigital.gameplay.actions.VisionCommand
import oathdigital.gameplay.actions.NegotiationCommand
import oathdigital.gameplay.phases.{RestCommand, WakeCommand}
import oathdigital.gameplay.phases.WarExhaustionRandomPort
import oathdigital.model._
import oathdigital.gameplay.setup.{
  FirstGameSetupCommand,
  FirstGameSetupRules
}


final case class GameAccepted(
    state: OathState,
    events: Vector[OathEvent],
    continue: OathContinue,
    nextSequence: Long
)

final case class LoadedGame(
    state: OathState,
    nextSequence: Long
)
final case class MajorActionPreviewAccepted(loaded: LoadedGame,
    options: Vector[OrderedRuleInvocation],
    ignored: Vector[IgnoredRuleDiagnostic])

sealed trait GameApplicationError extends Product with Serializable
object GameApplicationError {
  final case class StreamNotFound(gameId: String)
      extends GameApplicationError
  final case class DuplicateGame(gameId: String)
      extends GameApplicationError
  final case class StaleClientPosition(expected: Long, actual: Long)
      extends GameApplicationError
  final case class StreamIdentityMismatch(expected: String, actual: String)
      extends GameApplicationError
  final case class CodecFailure(error: EventCodecFailure)
      extends GameApplicationError
  final case class ReplayFailure(
      index: Long,
      violation: OathViolation
  ) extends GameApplicationError
  final case class CommandRejected(violation: OathViolation)
      extends GameApplicationError
  final case class BootstrapFailure(message: String)
      extends GameApplicationError
  final case class SequenceConflict(expected: Long, actual: Long)
      extends GameApplicationError
  final case class AppendAcknowledgementMismatch(message: String)
      extends GameApplicationError
  final case class StorageFailure(message: String)
      extends GameApplicationError
}

/**
 * Event-sourced application service for the single current v1 game stream.
 */
final class GameApplicationService(
    catalog: ExecutableCatalog,
    repository: EventStreamRepository,
    searchDrawPort: SearchDrawPort = SearchDrawPort.authoritative,
    defenseDicePort: DefenseDicePort = DefenseDicePort.random,
    campaignDicePort: CampaignDicePort = CampaignDicePort.random,
    warExhaustionRandomPort: WarExhaustionRandomPort =
      WarExhaustionRandomPort.random,
    eventCodec: GameEventCodec = GameEventCodec.default
) {
  import GameApplicationError._
  import RepositoryAppendResult._

  private val setupRules = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog,
    warExhaustionRandomPort = warExhaustionRandomPort,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))
  private val replay = new EventReplayEngine(rules)

  /** Privileged development support. Never include this in a player projection. */
  def rawEventHistory(
      gameId: String,
      limit: Int
  ): Either[GameApplicationError, Vector[String]] =
    if (limit < 1 || limit > 100)
      Left(BootstrapFailure("event history limit must be between 1 and 100"))
    else repository.load(gameId).left.map(storageError).flatMap {
      case None => Left(GameApplicationError.StreamNotFound(gameId))
      case Some(stream) => Right(stream.records.takeRight(limit))
    }

  def load(
      gameId: String
  ): Either[GameApplicationError, Option[LoadedGame]] =
    repository.load(gameId).left.map(storageError).flatMap {
      case None => Right(None)
      case Some(stream) =>
        reconstruct(gameId, stream).map(state =>
          Some(LoadedGame(state, stream.nextSequence)))
    }

  def preview(gameId: String, expectedNextSequence: Long, actor: PlayerId,
      action: MajorActionKind, selected: Vector[OrderedRuleInvocation])
      : Either[GameApplicationError, MajorActionPreviewAccepted] =
    load(gameId).flatMap {
      case None => Left(GameApplicationError.StreamNotFound(gameId))
      case Some(loaded) if loaded.nextSequence != expectedNextSequence =>
        Left(StaleClientPosition(expectedNextSequence, loaded.nextSequence))
      case Some(loaded @ LoadedGame(OathState.Ready(ready), _)) =>
        walkerAction(action) match {
          // The `ActionRef` this match already resolved is bound rather than
          // discarded (batch-1 Task 1): `offerableWalkerPowers` reads the
          // action's own modifier-selection window from the registry, so the
          // preview offers what THIS action offers instead of what Recover
          // does. It is the same ref, not a second derivation.
          case Some(actionRef) => for {
            offerable <- rules.offerableWalkerPowers(ready, actor, actionRef)
              .left.map(CommandRejected)
            options = offerable.map(power =>
              OrderedRuleInvocation(power.source, power.id.value))
            accepted <- acceptPreview(loaded, options, selected, Vector.empty)
          } yield accepted
          case None => for {
            options <- PowerRuntime.options(catalog, ready, actor, action)
              .left.map(CommandRejected)
            ignored <- PowerRuntime.ignored(catalog, ready, actor, action)
              .left.map(CommandRejected)
            accepted <- acceptPreview(loaded, options, selected, ignored)
          } yield accepted
        }
      case Some(_) => Left(CommandRejected(OathViolation.GameNotStarted))
    }

  /** `action` runs on the generic walker (Task 9a) exactly when its wire key
    * names a registered [[oathdigital.model.ActionRef]] --
    * `WalkerActionRegistry` stays the single place that knows which actions
    * are walker-driven, so this needs no per-action `MajorActionKind` match
    * of its own. `MajorActionKind` and `ActionRef` share their key strings by
    * convention (see `GameIntentMapper.actionRef`, which bridges the same
    * way from the wire intent), so a legacy-only kind like `Travel` simply
    * has no matching `ActionRef` and falls through to the `None` branch.
    */
  private def walkerAction(action: MajorActionKind): Option[ActionRef] =
    ActionRef.fromKey(action.key).filter(WalkerActionRegistry.isRegistered)

  /** Shared acceptance gate for both preview branches: `selected` must be
    * duplicate-free and a subset of `options`, whichever machinery produced
    * `options`.
    */
  private def acceptPreview(loaded: LoadedGame,
      options: Vector[OrderedRuleInvocation],
      selected: Vector[OrderedRuleInvocation],
      ignored: Vector[IgnoredRuleDiagnostic])
      : Either[GameApplicationError, MajorActionPreviewAccepted] =
    Either.cond(selected.distinct.size == selected.size &&
      selected.forall(options.contains),
      MajorActionPreviewAccepted(loaded, options, ignored),
      CommandRejected(OathViolation.InvalidModifierInvocation(
        "preview contains a duplicate or unavailable modifier")))

  def handle(
      gameId: String,
      expectedNextSequence: Long,
      command: GameCommand
  ): Either[GameApplicationError, GameAccepted] =
    repository.load(gameId).left.map(storageError).flatMap {
      case None =>
        if (expectedNextSequence != 0L)
          Left(StaleClientPosition(expectedNextSequence, 0L))
        else
          command match {
            case GameCommand.Begin(_) =>
              handleAgainst(
                gameId,
                rules.initialState,
                command,
                ExpectedStream.MustNotExist,
                0L
              )
            case _ =>
              Left(GameApplicationError.StreamNotFound(gameId))
          }
      case Some(stream) =>
        if (expectedNextSequence != stream.nextSequence)
          Left(StaleClientPosition(
            expectedNextSequence,
            stream.nextSequence
          ))
        else
          command match {
            case GameCommand.Begin(_) => Left(DuplicateGame(gameId))
            case _ =>
              reconstruct(gameId, stream).flatMap { state =>
                handleAgainst(
                  gameId,
                  state,
                  command,
                  ExpectedStream.AtNextSequence(stream.nextSequence),
                  stream.nextSequence
                )
              }
          }
    }

  private def reconstruct(
      gameId: String,
      stream: StoredEventStream
  ): Either[GameApplicationError, OathState] =
    for {
      _ <-
        if (stream.gameId == gameId) Right(())
        else Left(StreamIdentityMismatch(gameId, stream.gameId))
      envelopes <- eventCodec
        .decodeStream(stream.records.mkString("[", ",", "]"))
        .left
        .map(CodecFailure)
      _ <- envelopes.zipWithIndex.collectFirst {
        case (envelope, index) if envelope.sequence != index.toLong =>
          CodecFailure(EventCodecFailure("invalid-sequence",
            s"$$[$index].sequence",
            s"expected $index but found ${envelope.sequence}"))
        case (envelope, _) if envelope.gameId != gameId =>
          StreamIdentityMismatch(gameId, envelope.gameId)
      }.toLeft(())
      state <- replay
        .replay(envelopes.map(envelope =>
          RecordedEvent(envelope.sequence, envelope.event)))
        .left
        .map(failure => ReplayFailure(failure.index, failure.violation))
    } yield state

  private def handleAgainst(
      gameId: String,
      state: OathState,
      command: GameCommand,
      expected: ExpectedStream,
      nextSequence: Long
  ): Either[GameApplicationError, GameAccepted] =
    for {
      transition <- applyCommand(state, command, nextSequence).left.map(CommandRejected)
      records <- encode(gameId, nextSequence, transition.events)
      result <- repository.append(gameId, expected, records)
        .left.map(storageError)
      accepted <- result match {
        case Appended(first, count)
            if first == nextSequence && count == records.size =>
          Right(GameAccepted(
            transition.state,
            transition.events,
            transition.continue,
            nextSequence + count
          ))
        case StreamAlreadyExists => Left(DuplicateGame(gameId))
        case RepositoryAppendResult.StreamNotFound =>
          Left(GameApplicationError.StreamNotFound(gameId))
        case RepositoryAppendResult.SequenceConflict(wanted, actual) =>
          Left(GameApplicationError.SequenceConflict(wanted, actual))
        case Appended(first, count) =>
          Left(AppendAcknowledgementMismatch(
            s"expected first=$nextSequence count=${records.size}; " +
              s"repository returned first=$first count=$count"
          ))
      }
    } yield accepted

  private def applyCommand(
      state: OathState,
      command: GameCommand,
      nextSequence: Long
  ): Either[OathViolation, OathTransition] =
    state match {
      case OathState.Ready(ready)
          if ready.game.current.walkerPending.nonEmpty &&
            !isWalkerResume(command) =>
        Left(OathViolation.InvalidEventOrder(
          "a walker action is pending; only walker resume commands are legal"))
      case _ => applyUnblockedCommand(state, command, nextSequence)
    }

  private def applyUnblockedCommand(
      state: OathState,
      command: GameCommand,
      nextSequence: Long
  ): Either[OathViolation, OathTransition] =
    command match {
      case GameCommand.WithModifiers(inner, ordered) => state match {
        case OathState.Ready(ready) => majorAction(inner).toRight(
          OathViolation.InvalidModifierInvocation(
            "ordered modifiers are only valid on a major-action start"))
          .flatMap { case (actor, action) =>
            PowerRuntime.options(catalog, ready, actor, action).flatMap { options =>
              val duplicate = ordered.distinct.size != ordered.size
              val unavailable = ordered.find(value => !options.contains(value))
              if (duplicate) Left(OathViolation.InvalidModifierInvocation(
                "a modifier may be invoked only once"))
              else unavailable.map(value => Left(OathViolation.InvalidModifierInvocation(
                s"modifier ${value.handlerId} is unavailable from ${value.source.stableKey}")))
                .getOrElse(applyCommand(state, inner, nextSequence))
            }
          }
        case _ => Left(OathViolation.GameNotStarted)
      }
      case GameCommand.Begin(plan) =>
        setupRules.handle(state, FirstGameSetupCommand.Begin(plan))
      case GameCommand.StartWalker(action, start) =>
        rules.startWalker(state, action, start.actor, start.modifiers)
      case GameCommand.ResolveWalker(actor, treeDecision) =>
        rules.resolveWalker(state, actor, Answered(treeDecision.decisionId,
          treeDecision.answer))
      case GameCommand.RollWalker(actor, pool) =>
        rules.rollWalkerPrepared(state, actor, pool) { count =>
          Either.cond(count == defenseDicePort.diceCount,
            defenseDicePort.rollTwo(), OathViolation.InvalidEventOrder(
              s"walker roll pool ${pool.value} requested $count dice but " +
                s"the defense dice port only rolls ${defenseDicePort.diceCount}"))
        }
      case GameCommand.PlacePawn(playerId, siteId) =>
        setupRules.handle(state, FirstGameSetupCommand.PlacePawn(playerId, siteId))
      case GameCommand.ChooseAdviser(playerId, adviserId) =>
        setupRules.handle(
          state,
          FirstGameSetupCommand.ChooseAdviser(playerId, adviserId)
        )
      case GameCommand.TakeWealth(playerId, resource) =>
        rules.handle(state, WakeCommand.TakeWealth(playerId, resource))
      case GameCommand.EndWake(playerId) =>
        rules.handle(state, WakeCommand.EndWake(playerId))
      case GameCommand.Travel(playerId, destination) =>
        rules.handle(state, TravelCommand.Travel(playerId, destination))
      case GameCommand.Muster(playerId, target) =>
        rules.handle(state, EconomyCommand.Muster(playerId, target))
      case GameCommand.Trade(playerId, target, resource) =>
        rules.handle(state, EconomyCommand.Trade(playerId, target, resource))
      case GameCommand.BeginSearch(playerId, source) => state match {
        case OathState.Ready(ready) =>
          for {
            region <- ready.game.current.players.find(_.player == playerId)
              .flatMap(_.pawnSite).flatMap(ready.game.current.map.regionOf)
              .toRight(OathViolation.PawnSiteMissing(playerId))
            drawn <- searchDrawPort.prepare(ready, source, region)
            result <- rules.handle(state, SearchCommand.Start(
              playerId, DecisionId(s"search-$nextSequence"), source, drawn))
          } yield result
        case _ => Left(OathViolation.GameNotStarted)
      }
      case GameCommand.BeginChallenge(playerId, banner) =>
        rules.handle(state, ChallengeCommand.Begin(playerId,
          DecisionId(s"challenge-$nextSequence"), banner))
      case GameCommand.ChooseChallengeSecretSite(playerId, decision, site) =>
        rules.handle(state, ChallengeCommand.ChooseSecretSite(playerId, decision, site))
      case GameCommand.CompleteChallenge(playerId, decision, amount) =>
        rules.handle(state, ChallengeCommand.Complete(playerId, decision, amount))
      case GameCommand.PlaceBannerResource(playerId, banner, amount) =>
        rules.handle(state, ChallengeCommand.PlaceResource(playerId, banner, amount))
      case GameCommand.ResolveFacedownAdviser(playerId, adviser, placement) =>
        placement.fold(rules.handle(state,
          MinorActionCommand.DiscardFacedownAdviser(playerId, adviser)))(selected =>
          rules.handle(state, MinorActionCommand.PlayFacedownAdviser(
            playerId, adviser, selected)))
      case GameCommand.PeekSiteRelics(playerId) =>
        rules.handle(state, MinorActionCommand.PeekSiteRelics(playerId))
      case GameCommand.RevealOwnedRelic(playerId, relic) =>
        rules.handle(state, MinorActionCommand.RevealOwnedRelic(playerId, relic))
      case GameCommand.MoveWarbands(playerId, toSite, amount) =>
        rules.handle(state, MinorActionCommand.MoveWarbands(playerId, toSite, amount))
      case GameCommand.RevealVision(playerId, visionId) =>
        rules.handle(state, VisionCommand.Reveal(playerId, visionId))
      case GameCommand.PlayConspiracy(playerId, target) =>
        rules.handle(state, VisionCommand.PlayConspiracy(playerId,
          state match {
            case OathState.Ready(ready) => ready.game.current.pending.collect {
              case p: PendingProcedure.Conspiracy if p.awaitingTarget => p.decision
            }.getOrElse(DecisionId(s"conspiracy-$nextSequence"))
            case _ => DecisionId(s"conspiracy-$nextSequence")
          }, target))
      case GameCommand.BeginNegotiation(playerId, participants) =>
        rules.handle(state, NegotiationCommand.Begin(playerId,
          DecisionId(s"negotiation-$nextSequence"), participants))
      case GameCommand.ReplaceNegotiationTerms(playerId, decision, terms) =>
        rules.handle(state, NegotiationCommand.ReplaceTerms(playerId, decision, terms))
      case GameCommand.AcceptNegotiation(playerId, decision) =>
        rules.handle(state, NegotiationCommand.Accept(playerId, decision))
      case GameCommand.DeclineNegotiation(playerId, decision) =>
        rules.handle(state, NegotiationCommand.Decline(playerId, decision))
      case GameCommand.BeginCampaignConquest(playerId, targets, count) =>
        rules.handle(state, CampaignCommand.Start(playerId,
          DecisionId(s"campaign-$nextSequence"), targets, count))
      case GameCommand.BeginCampaignRaid(playerId, targets, count) =>
        rules.handle(state, CampaignCommand.StartRaid(playerId,
          DecisionId(s"campaign-$nextSequence"), targets, count))
      case GameCommand.ChooseCampaignPlan(playerId, decision, source) =>
        rules.handle(state, CampaignCommand.ChoosePlan(playerId, decision, source))
      case GameCommand.FinishCampaignPlans(playerId, decision) =>
        Campaign.prepareFinishPlans(catalog, state, playerId, decision).flatMap { count =>
          rules.handle(state, CampaignCommand.FinishPlans(playerId, decision,
            count.fold(Vector.empty[AttackDieFace])(campaignDicePort.rollAttack)))
        }
      case GameCommand.ChooseCampaignSacrifice(playerId, decision, count) => state match {
        case OathState.Ready(ready) => ready.game.current.pending match {
          case Some(c: PendingProcedure.Campaign) =>
            val defenseCount = CampaignRules.defenseDiceCount(catalog, ready, c)
            rules.handle(state, CampaignCommand.Sacrifice(playerId, decision, count,
              campaignDicePort.rollDefense(defenseCount)))
          case _ => rules.handle(state, CampaignCommand.Sacrifice(playerId, decision,
            count, Vector.empty))
        }
        case _ => rules.handle(state, CampaignCommand.Sacrifice(playerId, decision,
          count, Vector.empty))
      }
      case GameCommand.PlaceCampaignForce(playerId, decision, allocations) =>
        rules.handle(state, CampaignCommand.Place(playerId, decision, allocations))
      case GameCommand.RelocateCampaignRaidPawn(playerId, decision, destination) =>
        rules.handle(state, CampaignCommand.RelocateRaidPawn(
          playerId, decision, destination))
      case GameCommand.ChooseOathkeeperRecipient(playerId, decision, recipient) =>
        rules.chooseOathkeeperRecipient(state, playerId, decision, recipient)
      case GameCommand.CompleteSearch(playerId, decision, kept, discarded,
          placement) =>
        rules.handle(state, SearchCommand.Complete(
          playerId, decision, kept, discarded, placement))
      case GameCommand.ResolveCardDecision(playerId, decision, resolution) =>
        resolution match {
          case CardDecisionResolution.StartingAdviser(adviserId) => state match {
            case progress: OathState.InProgress =>
              val expected = CardDecisionIds.startingAdviser(
                playerId, progress.adviserChoices.size)
              if (decision != expected)
                Left(OathViolation.InvalidEventOrder(
                  "stale or incorrect starting-adviser decision ID"))
              else setupRules.handle(state,
                FirstGameSetupCommand.ChooseAdviser(playerId, adviserId))
            case _ => Left(OathViolation.InvalidEventOrder(
              "starting-adviser resolution has the wrong decision kind"))
          }
          case CardDecisionResolution.Search(kept, discarded, placement) =>
            rules.handle(state, SearchCommand.Complete(
              playerId, decision, kept, discarded, placement))
        }
      case GameCommand.BeginRest(playerId) =>
        rules.handle(state, RestCommand.Begin(playerId))
      case GameCommand.FinishRest(playerId) =>
        rules.handle(state, RestCommand.Finish(playerId))
      case GameCommand.ResolveRestPower(playerId, decision, allocations, bank) =>
        rules.handle(state, RestCommand.ResolvePower(playerId, decision,
          allocations, bank))
      case GameCommand.DeclineRestPower(playerId, decision) =>
        rules.handle(state, RestCommand.DeclinePower(playerId, decision))
    }

  private def isWalkerResume(command: GameCommand): Boolean = command match {
    case _: GameCommand.ResolveWalker | _: GameCommand.RollWalker => true
    case _ => false
  }

  private def majorAction(command: GameCommand): Option[(PlayerId, MajorActionKind)] =
    command match {
      case GameCommand.Travel(actor, _) => Some(actor -> MajorActionKind.Travel)
      case GameCommand.Muster(actor, _) => Some(actor -> MajorActionKind.Muster)
      case GameCommand.Trade(actor, _, _) => Some(actor -> MajorActionKind.Trade)
      case GameCommand.BeginSearch(actor, _) => Some(actor -> MajorActionKind.Search)
      case GameCommand.ResolveFacedownAdviser(actor, _, _) =>
        Some(actor -> MajorActionKind.Search)
      case GameCommand.BeginChallenge(actor, _) =>
        Some(actor -> MajorActionKind.Challenge)
      case GameCommand.BeginCampaignConquest(actor, _, _) =>
        Some(actor -> MajorActionKind.Campaign)
      case GameCommand.BeginCampaignRaid(actor, _, _) =>
        Some(actor -> MajorActionKind.Campaign)
      case _ => None
    }

  private def encode(
      gameId: String,
      firstSequence: Long,
      events: Vector[OathEvent]
  ): Either[GameApplicationError, Vector[String]] =
    events.zipWithIndex.foldLeft[
      Either[GameApplicationError, Vector[String]]
    ](Right(Vector.empty)) {
      case (Right(accumulated), (event, offset)) =>
        eventCodec
          .encodeEvent(
            gameId,
            catalog.ref,
            firstSequence + offset.toLong,
            event
          )
          .left
          .map(CodecFailure)
          .map(value => accumulated :+ value)
      case (failure @ Left(_), _) => failure
    }

  private def storageError(
      failure: RepositoryFailure
  ): GameApplicationError =
    failure match {
      case RepositoryFailure.StorageFailure(message) =>
        StorageFailure(message)
      case RepositoryFailure.InvalidConfiguration(message) =>
        StorageFailure(message)
    }
}
