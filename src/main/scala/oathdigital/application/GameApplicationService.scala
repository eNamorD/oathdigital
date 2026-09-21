package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.{PowerRuntime, OathRules}
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.walker.WalkerProcedureRegistry
import oathdigital.gameplay.actions.MinorActionCommand
import oathdigital.gameplay.phases.rest.WarExhaustionRandomPort
import oathdigital.model._
import oathdigital.protocol.PreviewTarget
import oathdigital.gameplay.setup.FirstGameSetupRules


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
final case class PreparedGameBootstrap(
    records: Vector[String],
    state: OathState,
    events: Vector[OathEvent],
    continue: OathContinue
)
/** `targets` (batch-1 Task 5) is the walker-action targets costed against the
  * modifiers THIS preview selected, rather than against the automatic set the
  * projection is built from. Empty for an action whose targets the projection
  * already carries, and for a walker action that has none.
  */
final case class MajorActionPreviewAccepted(loaded: LoadedGame,
    options: Vector[OrderedRuleInvocation],
    ignored: Vector[IgnoredRuleDiagnostic],
    targets: Vector[PreviewTarget] = Vector.empty)

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
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    phasePowerCatalog = PhasePowerCatalog.default(catalog),
    walkerDice = CampaignDicePort.walkerDice(campaignDicePort))
  private val replay = new EventReplayEngine(rules)

  /** Derives a validated initial journal and state without accessing storage. */
  def prepareBootstrap(
      gameId: String,
      request: FirstGameSetupPlan
  ): Either[GameApplicationError, PreparedGameBootstrap] =
    prepareTransition(gameId, rules.initialState, GameCommand.Begin(request), 0L)
      .map { case (transition, records) => PreparedGameBootstrap(
        records, transition.state, transition.events, transition.continue) }

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
      action: ActionKind, selected: Vector[OrderedRuleInvocation])
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
            accepted <- acceptPreview(loaded, options, selected, Vector.empty,
              walkerTargets(actionRef, ready, actor, selected))
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
    * `WalkerProcedureRegistry` stays the single place that knows which
    * procedures are walker-driven, so this needs no per-action
    * `ActionKind` match of its own. `ActionKind` and `ActionRef`
    * share their key strings by convention (see `GameIntentMapper.actionRef`,
    * which bridges the same way from the wire intent), so a legacy-only kind
    * like `Campaign` simply has no matching `ActionRef` and falls through to
    * the `None` branch.
    */
  private def walkerAction(action: ActionKind): Option[ActionRef] =
    ActionRef.fromKey(action.key).filter(WalkerProcedureRegistry.isRegistered)

  /** Shared acceptance gate for both preview branches: `selected` must be
    * duplicate-free and a subset of `options`, whichever machinery produced
    * `options`.
    */
  private def acceptPreview(loaded: LoadedGame,
      options: Vector[OrderedRuleInvocation],
      selected: Vector[OrderedRuleInvocation],
      ignored: Vector[IgnoredRuleDiagnostic],
      // By name: `targets` costs the action against `selected`, which is only
      // known to name real powers once this gate has accepted it. A rejected
      // preview must not evaluate it at all -- `PowerId` refuses to be built
      // from an unknown id, so costing an unvalidated selection throws rather
      // than rejecting.
      targets: => Vector[PreviewTarget] = Vector.empty)
      : Either[GameApplicationError, MajorActionPreviewAccepted] =
    Either.cond(selected.distinct.size == selected.size &&
      selected.forall(options.contains),
      MajorActionPreviewAccepted(loaded, options, ignored, targets),
      CommandRejected(OathViolation.InvalidModifierInvocation(
        "preview contains a duplicate or unavailable modifier")))

  /** A walker action's targets, costed with the powers this preview actually
    * selected (batch-1 Task 5).
    *
    * The projection a route builds beside this is costed with automatic
    * powers alone, because it is built before the player has chosen anything.
    * Once they have, the number shown beside a destination has to reflect
    * that choice, so the preview asks the same evaluator again with the
    * selected vector. The match is one line per action that has targets at
    * all, in the application layer beside the command dispatch that already
    * names each action -- it is not a rule, and no engine code learns it.
    */
  private def walkerTargets(action: ActionRef,
      ready: oathdigital.model.ReadyGame,
      actor: PlayerId, selected: Vector[OrderedRuleInvocation])
      : Vector[PreviewTarget] = action match {
    case ActionRef.Travel =>
      val powers = rules.walkerPowers(ready, actor,
        selected.map(invocation => PowerId(invocation.handlerId)))
      TravelProcedure.candidates(catalog, ready, actor, powers).map {
        case (site, cost) =>
          PreviewTarget(s"site:${site.value}", cost, "Travel destination")
      }
    case _ => Vector.empty
  }

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
      prepared <- prepareTransition(gameId, state, command, nextSequence)
      (transition, records) = prepared
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

  private def prepareTransition(
      gameId: String,
      state: OathState,
      command: GameCommand,
      nextSequence: Long
  ): Either[GameApplicationError, (OathTransition, Vector[String])] = for {
    transition <- applyCommand(state, command, nextSequence).left.map(CommandRejected)
    records <- encode(gameId, nextSequence, transition.events)
  } yield transition -> records

  private def applyCommand(
      state: OathState,
      command: GameCommand,
      nextSequence: Long
  ): Either[OathViolation, OathTransition] =
    state match {
      case OathState.Ready(ready)
          if (ready.game.current.walkerPending.nonEmpty ||
            ready.game.current.walkerProcedure.nonEmpty) &&
            !isWalkerResume(command) =>
        Left(OathViolation.InvalidEventOrder(
          "a walker procedure is pending; only walker resume commands are legal"))
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
      case GameCommand.StartWalker(ActionRef.Search, start) => state match {
        case OathState.Ready(ready) => for {
          source <- SearchProcedure.sourceOf(start.startArgs)
          region <- ready.game.current.players.find(_.player == start.actor)
            .flatMap(_.pawnSite).flatMap(ready.game.current.map.regionOf)
            .toRight(OathViolation.PawnSiteMissing(start.actor))
          prepared <- searchDrawPort.prepare(ready, source, region)
          authoritative <- oathdigital.gameplay.actions.SearchRules.draw(
            ready, source, region)
          _ <- Either.cond(prepared == authoritative, (),
            OathViolation.SearchDrawMismatch(
              "prepared draw does not match authoritative source order"))
          result <- rules.startWalker(state, ActionRef.Search, start.actor,
            start.modifiers, start.startArgs)
        } yield result
        case _ => Left(OathViolation.GameNotStarted)
      }
      case GameCommand.StartWalker(procedure, start) =>
        rules.startWalker(state, procedure, start.actor, start.modifiers,
          start.startArgs)
      case GameCommand.ResolveWalker(actor, treeDecision) =>
        rules.resolveWalker(state, actor, treeDecision.decisionId,
          treeDecision.answer)
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
      // Ending Wake is a walker procedure (batch-1 Task 7); the command
      // survives as the client's spelling for it, so no transport and no
      // caller had to learn that the engine changed underneath.
      case GameCommand.EndWake(playerId) =>
        rules.startWalker(state, PhaseTransitionRef.EndWake, playerId)
      case GameCommand.UsePower(playerId, power, source) =>
        rules.startWalker(state, ActionRef.UsePower(power), playerId,
          Vector.empty, Vector(source))
      case GameCommand.PeekSiteRelics(playerId) =>
        rules.handle(state, MinorActionCommand.PeekSiteRelics(playerId))
      case GameCommand.RevealOwnedRelic(playerId, relic) =>
        rules.handle(state, MinorActionCommand.RevealOwnedRelic(playerId, relic))
      case GameCommand.MoveWarbands(playerId, toSite, amount) =>
        rules.handle(state, MinorActionCommand.MoveWarbands(playerId, toSite, amount))
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
        }
      case GameCommand.BeginRest(playerId) =>
        rules.startWalker(state, PhaseTransitionRef.BeginRest, playerId)
      case GameCommand.FinishRest(playerId) =>
        rules.startWalker(state, PhaseTransitionRef.FinishRest, playerId)
    }

  private def isWalkerResume(command: GameCommand): Boolean = command match {
    case _: GameCommand.ResolveWalker | _: GameCommand.RollWalker => true
    case _ => false
  }

  private def majorAction(command: GameCommand): Option[(PlayerId, ActionKind)] =
    command match {
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
