package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.{RecordedEvent, ReplayStep}
import oathdigital.gameplay.{PowerRuntime, OathRules}
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.walker.WalkerProcedureRegistry
import oathdigital.gameplay.actions.MinorActionCommand
import oathdigital.gameplay.phases.rest.WarExhaustionRandomPort
import oathdigital.model._
import oathdigital.protocol.PreviewModifier


/** `events` are the events the command produced: the batch's opening
  * `CommandAccepted` is left out, though `nextSequence` counts it. */
final case class GameAccepted(
    state: OathState,
    events: Vector[OathEvent],
    nextSequence: Long,
    undo: Option[UndoStep] = None,
    redo: Option[UndoStep] = None,
    logRevision: Option[Long] = None
):
  /** The position this acceptance left, as a load would read it. */
  def loaded: LoadedGame =
    LoadedGame(state, nextSequence, undo, redo, logRevision)

/** `undo` is the batch the next Undo would take back, `redo` the batch the
  * next Redo would restore, and `logRevision` the latest void marker (undo
  * design, "Projection"). */
final case class LoadedGame(
    state: OathState,
    nextSequence: Long,
    undo: Option[UndoStep] = None,
    redo: Option[UndoStep] = None,
    logRevision: Option[Long] = None
)

/** The command batch an Undo would take back or a Redo restore: its issuer
  * and range, and the first log line its issuer sees for it (undo design,
  * "Controls"). */
final case class UndoStep(issuer: PlayerId, from: Long, until: Long,
    note: Option[String])
/** The whole journal as the log reads it: every event with the state
  * before and after it, and the sequence the next event takes.
  */
final case class GameHistory(
    steps: Vector[ReplayStep[OathState, OathEvent]],
    nextSequence: Long
)
final case class PreparedGameBootstrap(
    records: Vector[String],
    state: OathState,
    events: Vector[OathEvent]
)
final case class MajorActionPreviewAccepted(loaded: LoadedGame,
    options: Vector[OrderedRuleInvocation],
    ignored: Vector[IgnoredRuleDiagnostic],
    modifiers: Vector[PreviewModifier] = Vector.empty)

sealed trait GameApplicationError extends Product with Serializable
object GameApplicationError:
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
    eventCodec: GameEventCodec = GameEventCodec.default,
    /** Where every stream of this service begins: `NoGame`, so a stream's
      * first event is `GameStarted`, unless a test seeds a start state. Only
      * tests pass it (`BackendArchitectureSuite`), until a journaled
      * arranged-start event replaces it (`docs/ROADMAP.md`). */
    val genesis: OathState = OathState.NoGame
):
  import GameApplicationError._
  import RepositoryAppendResult._

  private val rules = new OathRules(catalog,
    warExhaustionRandomPort = warExhaustionRandomPort,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    phasePowerCatalog = PhasePowerCatalog.default(catalog),
    walkerDice = CampaignDicePort.walkerDice(campaignDicePort))
  private val presentation = new GamePresentationProjector(catalog)
  private val descriptions = new PreviewModifierDescriptions(catalog, presentation)
  private val journalFold = new JournalFold(rules.evolve)
  private val log = new gamelog.GameLogFormatter(catalog, presentation)

  /** Derives a validated initial journal and state without accessing storage. */
  def prepareBootstrap(
      gameId: String,
      chronicle: Chronicle,
      config: FirstGameBootstrapConfig
  ): Either[GameApplicationError, PreparedGameBootstrap] =
    prepareTransition(gameId, rules.initialState,
      GameCommand.Begin(chronicle, ChronicleFirstGamePlan.dealOrder(chronicle, config)),
      0L)
      .map { case (transition, records) => PreparedGameBootstrap(
        records, transition.state, transition.events) }

  /** Privileged development support. Never include this in a player projection. */
  def rawEventHistory(
      gameId: String,
      limit: Int
  ): Either[GameApplicationError, Vector[String]] =
    if limit < 1 || limit > 100 then
      Left(BootstrapFailure("event history limit must be between 1 and 100"))
    else repository.load(gameId).left.map(storageError).flatMap:
      case None => Left(GameApplicationError.StreamNotFound(gameId))
      case Some(stream) => Right(stream.records.takeRight(limit))

  def load(
      gameId: String
  ): Either[GameApplicationError, Option[LoadedGame]] =
    repository.load(gameId).left.map(storageError).flatMap:
      case None => Right(genesis match
        case OathState.NoGame => None
        case started => Some(LoadedGame(started, 0L)))
      case Some(stream) =>
        reconstruct(gameId, stream).map(journal =>
          Some(loadedFrom(journal, stream.nextSequence)))

  /** The scanned journal: the same decode and fold `load` runs, keeping
    * every step. The game log formats from this; nothing else needs it.
    */
  def history(
      gameId: String
  ): Either[GameApplicationError, Option[GameHistory]] =
    repository.load(gameId).left.map(storageError).flatMap:
      case None => Right(None)
      case Some(stream) =>
        reconstruct(gameId, stream).map(journal =>
          Some(GameHistory(journal.steps, stream.nextSequence)))

  def preview(gameId: String, expectedNextSequence: Long, actor: PlayerId,
      action: ActionKind, selected: Vector[OrderedRuleInvocation])
      : Either[GameApplicationError, MajorActionPreviewAccepted] =
    load(gameId).flatMap:
      case None => Left(GameApplicationError.StreamNotFound(gameId))
      case Some(loaded) if loaded.nextSequence != expectedNextSequence =>
        Left(StaleClientPosition(expectedNextSequence, loaded.nextSequence))
      case Some(loaded @ LoadedGame(OathState.Ready(ready), _, _, _, _)) =>
        walkerAction(action) match
          // The `ActionRef` this match already resolved is bound rather than
          // discarded (batch-1 Task 1): `offerableWalkerPowers` reads the
          // action's own modifier-selection window from the registry, so the
          // preview offers what THIS action offers instead of what Recover
          // does. It is the same ref, not a second derivation.
          case Some(actionRef) =>
            for
              offerable <- rules.offerableWalkerPowers(ready, actor, actionRef)
                .left.map(CommandRejected.apply)
              options = offerable.map(power =>
                OrderedRuleInvocation(power.source, power.id.value))
              accepted <- acceptPreview(loaded, options, selected, Vector.empty)
            yield accepted.copy(modifiers = descriptions.describe(ready,
              actor, action, offerable, accepted.options))
          case None =>
            for
              options <- PowerRuntime.options(catalog, ready, actor, action)
                .left.map(CommandRejected.apply)
              ignored <- PowerRuntime.ignored(catalog, ready, actor, action)
                .left.map(CommandRejected.apply)
              accepted <- acceptPreview(loaded, options, selected, ignored)
            yield accepted.copy(modifiers = descriptions.describe(ready,
              actor, action, Vector.empty, accepted.options))
      case Some(_) => Left(CommandRejected(OathViolation.GameNotStarted))

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
    repository.load(gameId).left.map(storageError).flatMap:
      case None =>
        if expectedNextSequence != 0L then
          Left(StaleClientPosition(expectedNextSequence, 0L))
        else if genesis == rules.initialState &&
            !command.isInstanceOf[GameCommand.Begin] then
          Left(GameApplicationError.StreamNotFound(gameId))
        else
          handleAgainst(
            gameId,
            FoldedJournal(genesis, Vector.empty, Nil),
            command,
            ExpectedStream.MustNotExist,
            0L
          )
      case Some(stream) =>
        if expectedNextSequence != stream.nextSequence then
          Left(StaleClientPosition(
            expectedNextSequence,
            stream.nextSequence
          ))
        else
          command match
            case GameCommand.Begin(_, _) => Left(DuplicateGame(gameId))
            // Undo and Redo go through the fold, not the rules: both are
            // legal over a pending walker (undo design, "Rulings").
            case GameCommand.Undo(actor) =>
              reconstruct(gameId, stream).flatMap(journal =>
                takeBack(gameId, journal, actor, stream.nextSequence))
            case GameCommand.Redo(actor) =>
              reconstruct(gameId, stream).flatMap(journal =>
                restore(gameId, journal, actor, stream.nextSequence))
            case _ =>
              reconstruct(gameId, stream).flatMap { journal =>
                handleAgainst(
                  gameId,
                  journal,
                  command,
                  ExpectedStream.AtNextSequence(stream.nextSequence),
                  stream.nextSequence
                )
              }

  private def reconstruct(
      gameId: String,
      stream: StoredEventStream
  ): Either[GameApplicationError, FoldedJournal] =
    decodeRecords(gameId, stream).flatMap(records =>
      journalFold.fold(genesis, records).left
        .map(failure => ReplayFailure(failure.index, failure.violation)))

  /** `batch` as an Undo or Redo would name it to its issuer. The note is the
    * batch's first log line; formatting stops at the batch's end, so a
    * taken-back batch reads as it did before its undo. */
  private def offered(journal: FoldedJournal, batch: CommandBatch): UndoStep =
    val note = log.format(journal.steps.take(batch.until.toInt),
        Some(batch.issuer))
      .find(entry => entry.sequence >= batch.from)
      .map(_.spans.map(_.text).mkString)
    UndoStep(batch.issuer, batch.from, batch.until, note)

  private def loadedFrom(journal: FoldedJournal, nextSequence: Long)
      : LoadedGame =
    LoadedGame(journal.state, nextSequence,
      undo = journal.undoable.map(offered(journal, _)),
      redo = journal.redoable.map(offered(journal, _)),
      logRevision = journal.logRevision)

  private def decodeRecords(
      gameId: String,
      stream: StoredEventStream
  ): Either[GameApplicationError, Vector[RecordedEvent[OathEvent]]] =
    for
      _ <-
        if stream.gameId == gameId then Right(())
        else Left(StreamIdentityMismatch(gameId, stream.gameId))
      envelopes <- eventCodec
        .decodeStream(stream.records.mkString("[", ",", "]"))
        .left
        .map(CodecFailure.apply)
      _ <- envelopes.zipWithIndex.collectFirst {
        case (envelope, index) if envelope.sequence != index.toLong =>
          CodecFailure(EventCodecFailure("invalid-sequence",
            s"$$[$index].sequence",
            s"expected $index but found ${envelope.sequence}"))
        case (envelope, _) if envelope.gameId != gameId =>
          StreamIdentityMismatch(gameId, envelope.gameId)
      }.toLeft(())
    yield envelopes.map(envelope =>
      RecordedEvent(envelope.sequence, envelope.event)).toVector

  private def handleAgainst(
      gameId: String,
      journal: FoldedJournal,
      command: GameCommand,
      expected: ExpectedStream,
      nextSequence: Long
  ): Either[GameApplicationError, GameAccepted] =
    for
      transition <- applyCommand(journal.state, command, nextSequence)
        .left.map(CommandRejected.apply)
      appended = GameCommand.issuer(command)
        .map(OathEvent.CommandAccepted(_)).toVector ++
        transition.events
      accepted <- appendBatch(gameId, journal, appended, transition.events,
        transition.state, expected, nextSequence)
    yield accepted

  /** Appends the marker that takes back the latest live batch, and returns
    * the state before it (undo design, "Commands and authorization").
    * Nothing runs in the rules engine. */
  private def takeBack(gameId: String, journal: FoldedJournal,
      actor: PlayerId, nextSequence: Long)
      : Either[GameApplicationError, GameAccepted] =
    for
      batch <- journal.undoable.toRight(
        CommandRejected(OathViolation.NothingToUndo))
      _ <- Either.cond(batch.issuer == actor, (),
        CommandRejected(OathViolation.UndoNotIssuer(actor, batch.issuer)))
      marker = OathEvent.StepTakenBack(batch.from, batch.until, actor)
      accepted <- appendBatch(gameId, journal, Vector(marker), Vector(marker),
        batch.before, ExpectedStream.AtNextSequence(nextSequence),
        nextSequence)
    yield accepted

  /** Appends the marker that restores the batch most recently taken back,
    * and returns the state after it (undo design, "Redo"). Nothing runs in
    * the rules engine. */
  private def restore(gameId: String, journal: FoldedJournal,
      actor: PlayerId, nextSequence: Long)
      : Either[GameApplicationError, GameAccepted] =
    for
      batch <- journal.redoable.toRight(
        CommandRejected(OathViolation.NothingToRedo))
      _ <- Either.cond(batch.issuer == actor, (),
        CommandRejected(OathViolation.RedoNotIssuer(actor, batch.issuer)))
      marker = OathEvent.StepRestored(batch.from, batch.until, actor)
      accepted <- appendBatch(gameId, journal, Vector(marker), Vector(marker),
        batch.after, ExpectedStream.AtNextSequence(nextSequence),
        nextSequence)
    yield accepted

  /** Appends `appended` as one batch and reports `reported` as the events
    * the command produced. `state` is what the command left, which the fold
    * reproduces from the batch. The fold runs before the append, so a batch
    * it would reject is never written. */
  private def appendBatch(
      gameId: String,
      journal: FoldedJournal,
      appended: Vector[OathEvent],
      reported: Vector[OathEvent],
      state: OathState,
      expected: ExpectedStream,
      nextSequence: Long
  ): Either[GameApplicationError, GameAccepted] =
    for
      records <- encode(gameId, nextSequence, appended)
      extended <- journalFold.extend(journal, appended.zipWithIndex.map {
          case (event, offset) => RecordedEvent(nextSequence + offset, event) })
        .left.map(failure => ReplayFailure(failure.index, failure.violation))
      result <- repository.append(gameId, expected, records)
        .left.map(storageError)
      accepted <- result match
        case Appended(first, count)
            if first == nextSequence && count == records.size =>
          Right(GameAccepted(state, reported, nextSequence + count,
            undo = extended.undoable.map(offered(extended, _)),
            redo = extended.redoable.map(offered(extended, _)),
            logRevision = extended.logRevision))
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
    yield accepted

  /** Begin's journal, built against the rules' initial state: it has no
    * `CommandAccepted`. */
  private def prepareTransition(
      gameId: String,
      state: OathState,
      command: GameCommand,
      nextSequence: Long
  ): Either[GameApplicationError, (OathTransition, Vector[String])] = for
    transition <- applyCommand(state, command, nextSequence).left.map(CommandRejected.apply)
    records <- encode(gameId, nextSequence, transition.events)
  yield transition -> records

  private def applyCommand(
      state: OathState,
      command: GameCommand,
      nextSequence: Long
  ): Either[OathViolation, OathTransition] =
    state match
      case OathState.Ready(ready)
          if (ready.game.current.walkerPending.nonEmpty ||
            ready.game.current.walkerProcedure.nonEmpty) &&
            !isWalkerResume(command) =>
        Left(OathViolation.InvalidEventOrder(
          "a walker procedure is pending; only walker resume commands are legal"))
      case _ => applyUnblockedCommand(state, command, nextSequence)

  private def applyUnblockedCommand(
      state: OathState,
      command: GameCommand,
      nextSequence: Long
  ): Either[OathViolation, OathTransition] =
    command match
      case GameCommand.WithModifiers(inner, ordered) => state match
        case OathState.Ready(ready) => majorAction(inner).toRight(
          OathViolation.InvalidModifierInvocation(
            "ordered modifiers are only valid on a major-action start"))
          .flatMap { case (actor, action) =>
            PowerRuntime.options(catalog, ready, actor, action).flatMap { options =>
              val duplicate = ordered.distinct.size != ordered.size
              val unavailable = ordered.find(value => !options.contains(value))
              if duplicate then Left(OathViolation.InvalidModifierInvocation(
                "a modifier may be invoked only once"))
              else unavailable.map(value => Left(OathViolation.InvalidModifierInvocation(
                s"modifier ${value.handlerId} is unavailable from ${value.source.stableKey}")))
                .getOrElse(applyCommand(state, inner, nextSequence))
            }
          }
        case _ => Left(OathViolation.GameNotStarted)
      case GameCommand.Begin(chronicle, orders) =>
        rules.beginGame(state, chronicle, orders)
      case GameCommand.StartWalker(procedure, start) =>
        rules.startWalker(state, procedure, start.actor, start.modifiers,
          start.startArgs)
      // The source answer is where a Search draws, so the prepared draw is
      // checked there, against the pile the answer names.
      case GameCommand.ResolveWalker(actor,
          TreeDecision(SearchProcedure.sourceDecisionId, answer)) =>
        state match
          case OathState.Ready(ready)
              if ready.game.current.walkerProcedure.contains(ActionRef.Search) =>
            for
              source <- SearchProcedure.sourceOf(answer)
              region <- SearchProcedure.actorRegion(ready, actor)
              prepared <- searchDrawPort.prepare(ready, source, region)
              authoritative <- oathdigital.gameplay.actions.SearchRules.draw(
                ready, source, region)
              _ <- Either.cond(prepared == authoritative, (),
                OathViolation.SearchDrawMismatch(
                  "prepared draw does not match authoritative source order"))
              result <- rules.resolveWalker(state, actor,
                SearchProcedure.sourceDecisionId, answer)
            yield result
          case _ => rules.resolveWalker(state, actor,
            SearchProcedure.sourceDecisionId, answer)
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
      case GameCommand.BeginRest(playerId) =>
        rules.startWalker(state, PhaseTransitionRef.BeginRest, playerId)
      case GameCommand.FinishRest(playerId) =>
        rules.startWalker(state, PhaseTransitionRef.FinishRest, playerId)
      // An Undo or Redo on a stream with history never gets here (`handle`
      // folds it first); on a missing stream nothing has been taken.
      case GameCommand.Undo(_) => Left(OathViolation.NothingToUndo)
      case GameCommand.Redo(_) => Left(OathViolation.NothingToRedo)

  private def isWalkerResume(command: GameCommand): Boolean = command match
    case _: GameCommand.ResolveWalker | _: GameCommand.RollWalker => true
    case _ => false

  private def majorAction(command: GameCommand): Option[(PlayerId, ActionKind)] =
    command match
      case _ => None

  private def encode(
      gameId: String,
      firstSequence: Long,
      events: Vector[OathEvent]
  ): Either[GameApplicationError, Vector[String]] =
    events.zipWithIndex.foldLeft[
      Either[GameApplicationError, Vector[String]]
    ](Right(Vector.empty)):
      case (Right(accumulated), (event, offset)) =>
        eventCodec
          .encodeEvent(
            gameId,
            firstSequence + offset.toLong,
            event
          )
          .left
          .map(CodecFailure.apply)
          .map(value => accumulated :+ value)
      case (failure @ Left(_), _) => failure

  private def storageError(
      failure: RepositoryFailure
  ): GameApplicationError =
    failure match
      case RepositoryFailure.StorageFailure(message) =>
        StorageFailure(message)
      case RepositoryFailure.InvalidConfiguration(message) =>
        StorageFailure(message)
