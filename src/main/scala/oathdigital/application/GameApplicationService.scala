package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.{RecordedEvent, ReplayStep}
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powerresolver.PowerUseStep
import oathdigital.gameplay.powers.{ConsentPowers, PhasePowerCatalog,
  WalkerPowerCatalog}
import oathdigital.gameplay.phases.rest.WarExhaustionRandomPort
import oathdigital.model._
import oathdigital.protocol.projection.LogKind


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
  private val journalFold = new JournalFold(rules.evolve)
  private val consent = new ConsentDesk(rules, ConsentPowers.default(catalog))
  private val coupling = new UndoCoupling(state =>
    rules.powerUseStep(state).nonEmpty)
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
          routed(gameId, FoldedJournal(genesis, Vector.empty, Nil), command,
            ExpectedStream.MustNotExist, 0L)
      case Some(stream) =>
        if expectedNextSequence != stream.nextSequence then
          Left(StaleClientPosition(
            expectedNextSequence,
            stream.nextSequence
          ))
        else
          command match
            case GameCommand.Begin(_, _) => Left(DuplicateGame(gameId))
            case _ =>
              reconstruct(gameId, stream).flatMap(journal =>
                routed(gameId, journal, command,
                  ExpectedStream.AtNextSequence(stream.nextSequence),
                  stream.nextSequence))

  /** Undo and Redo go through the fold, not the rules: both are legal over a
    * pending walker (undo design, "Rulings"). Consent commands go to the
    * consent desk, legal at any time (consent design, "Commands").
    * Everything else runs in the rules. */
  private def routed(gameId: String, journal: FoldedJournal,
      command: GameCommand, expected: ExpectedStream, nextSequence: Long)
      : Either[GameApplicationError, GameAccepted] =
    command match
      // The requester's Undo withdraws an unanswered request (ruling 14).
      // The issuer's Undo withdraws an automatic power's request and takes
      // back the batch that walked to its step (automatic power consent
      // design, ruling 9).
      case GameCommand.Undo(actor) => consent.pending(journal.state) match
        case Some(request) if request.use.nonEmpty =>
          withdrawUse(gameId, journal, request, actor, expected, nextSequence)
        case Some(_) => consentBatch(gameId, journal,
          consent.withdraw(journal.state, actor), expected, nextSequence)
        case None => takeBack(gameId, journal, actor, expected, nextSequence)
      case GameCommand.Redo(actor) =>
        if consent.pending(journal.state).nonEmpty then
          Left(CommandRejected(OathViolation.ConsentPending))
        else restore(gameId, journal, actor, expected, nextSequence)
      case GameCommand.RequestConsent(requester, decisionId, target,
          granters) =>
        consentBatch(gameId, journal, consent.request(journal.state,
          requester, decisionId, target, granters), expected, nextSequence)
      case GameCommand.AnswerConsent(granter, choice) =>
        consentBatch(gameId, journal, consent.answer(journal.state, granter,
          choice), expected, nextSequence)
      case GameCommand.SetConsentPreference(granter, power, requester,
          setting) =>
        consentBatch(gameId, journal, consent.setPreference(journal.state,
          granter, power, requester, setting), expected, nextSequence)
      case _ => handleAgainst(gameId, journal, command, expected, nextSequence)

  /** Withdraws `request`, about an automatic power, and takes back what
    * walked to its step, in one append. Only that batch's issuer may, and
    * not when a barrier keeps it off the stack. */
  private def withdrawUse(gameId: String, journal: FoldedJournal,
      request: ConsentRequest, actor: PlayerId, expected: ExpectedStream,
      nextSequence: Long): Either[GameApplicationError, GameAccepted] =
    coupling.undoing(journal).filter(_.forall(_.issuer == actor)) match
      case None => Left(CommandRejected(OathViolation.ConsentPending))
      case Some(chain) =>
        val events = OathEvent.ConsentRequestWithdrawn(request.requester) +:
          chain.map(batch =>
            OathEvent.StepTakenBack(batch.from, batch.until, actor))
        appendBatch(gameId, journal, events, events, expected, nextSequence)

  /** A consent command's events, appended as one batch. Any batch opening
    * among them is left out of the reported events, as it is for every
    * command. */
  private def consentBatch(gameId: String, journal: FoldedJournal,
      events: Either[OathViolation, Vector[OathEvent]],
      expected: ExpectedStream, nextSequence: Long)
      : Either[GameApplicationError, GameAccepted] =
    events.left.map(CommandRejected.apply).flatMap(appended =>
      appendBatch(gameId, journal, appended, appended.filterNot(
        _.isInstanceOf[OathEvent.CommandAccepted]), expected, nextSequence))

  private def reconstruct(
      gameId: String,
      stream: StoredEventStream
  ): Either[GameApplicationError, FoldedJournal] =
    decodeRecords(gameId, stream).flatMap(records =>
      journalFold.fold(genesis, records).left
        .map(failure => ReplayFailure(failure.index, failure.violation)))

  /** `batch` as an Undo or Redo would name it to its issuer. The note is the
    * batch's first log line, skipping an action's tag, which names the
    * action and its cost rather than what changed (undo design, "Controls").
    * Formatting stops at the batch's end, so a taken-back batch reads as it
    * did before its undo. */
  private def offered(journal: FoldedJournal, batch: CommandBatch): UndoStep =
    val note = log.format(journal.steps.take(batch.until.toInt),
        Some(batch.issuer))
      .find(entry => entry.sequence >= batch.from &&
        entry.kind != LogKind.Section)
      .map(_.spans.map(_.text).mkString)
    UndoStep(batch.issuer, batch.from, batch.until, note)

  /** The Undo a journal offers: its chain's issuer and whole range, named
    * by the batch at its bottom, which walked to any step. */
  private def offeredUndo(journal: FoldedJournal): Option[UndoStep] =
    coupling.undoing(journal).map(chain =>
      offered(journal, chain.last).copy(until = chain.head.until))

  /** The Redo a journal offers, named by the chain's first batch. */
  private def offeredRedo(journal: FoldedJournal): Option[UndoStep] =
    coupling.redoing(journal).map(chain =>
      offered(journal, chain.head).copy(until = chain.last.until))

  private def loadedFrom(journal: FoldedJournal, nextSequence: Long)
      : LoadedGame =
    LoadedGame(journal.state, nextSequence,
      undo = offeredUndo(journal),
      redo = offeredRedo(journal),
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

  /** Runs `command` and appends its batch. A walk that parks at an
    * automatic power's step opens its request in the same append
    * (automatic power consent design, "Reaching the step"). */
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
      issuer = GameCommand.issuer(command)
      opened <- issuer.fold[Either[OathViolation, Vector[OathEvent]]](
          Right(Vector.empty))(consent.opened(transition.state, _))
        .left.map(CommandRejected.apply)
      appended = issuer.map(OathEvent.CommandAccepted(_)).toVector ++
        transition.events ++ opened
      accepted <- appendBatch(gameId, journal, appended,
        transition.events ++ opened.filterNot(
          _.isInstanceOf[OathEvent.CommandAccepted]),
        expected, nextSequence)
    yield accepted

  /** Appends the markers that take back the latest live batches, and
    * returns the state before them (undo design, "Commands and
    * authorization"): one batch, or a coupled chain that ends at an
    * automatic power's step ([[UndoCoupling]]). Nothing runs in the rules
    * engine. */
  private def takeBack(gameId: String, journal: FoldedJournal,
      actor: PlayerId, expected: ExpectedStream, nextSequence: Long)
      : Either[GameApplicationError, GameAccepted] =
    for
      chain <- coupling.undoing(journal).toRight(
        CommandRejected(OathViolation.NothingToUndo))
      _ <- chain.find(_.issuer != actor).fold[Either[GameApplicationError,
        Unit]](Right(()))(batch => Left(CommandRejected(
          OathViolation.UndoNotIssuer(actor, batch.issuer))))
      markers = chain.map(batch =>
        OathEvent.StepTakenBack(batch.from, batch.until, actor))
      accepted <- appendBatch(gameId, journal, markers, markers, expected,
        nextSequence)
    yield accepted

  /** Appends the markers that restore the batches most recently taken
    * back, and returns the state after them (undo design, "Redo"): one
    * batch, or a coupled chain ([[UndoCoupling]]). A Redo that ends at an
    * automatic power's step asks its rulers again. Nothing else runs in
    * the rules engine. */
  private def restore(gameId: String, journal: FoldedJournal,
      actor: PlayerId, expected: ExpectedStream, nextSequence: Long)
      : Either[GameApplicationError, GameAccepted] =
    for
      chain <- coupling.redoing(journal).toRight(
        CommandRejected(OathViolation.NothingToRedo))
      _ <- chain.find(_.issuer != actor).fold[Either[GameApplicationError,
        Unit]](Right(()))(batch => Left(CommandRejected(
          OathViolation.RedoNotIssuer(actor, batch.issuer))))
      markers = chain.map(batch =>
        OathEvent.StepRestored(batch.from, batch.until, actor))
      restored <- extended(journal, markers, nextSequence)
      reopened <- consent.opened(restored.state, actor)
        .left.map(CommandRejected.apply)
      events = markers ++ reopened
      accepted <- appendBatch(gameId, journal, events, events.filterNot(
        _.isInstanceOf[OathEvent.CommandAccepted]), expected, nextSequence)
    yield accepted

  /** Appends `appended`, then any stale-preference clearing it leaves
    * (consent design, "Commands"), as one append, and reports `reported`
    * plus the clearing. The state reported is the fold's, which an undo
    * or a redo has given the current consent ledger. The fold runs before
    * the append, so a batch it would reject is never written. */
  private def appendBatch(
      gameId: String,
      journal: FoldedJournal,
      appended: Vector[OathEvent],
      reported: Vector[OathEvent],
      expected: ExpectedStream,
      nextSequence: Long
  ): Either[GameApplicationError, GameAccepted] =
    for
      opened <- extended(journal, appended, nextSequence)
      clearing = consent.stale(opened.state)
      folded <- extended(opened, clearing, nextSequence + appended.size)
      records <- encode(gameId, nextSequence, appended ++ clearing)
      result <- repository.append(gameId, expected, records)
        .left.map(storageError)
      accepted <- result match
        case Appended(first, count)
            if first == nextSequence && count == records.size =>
          Right(GameAccepted(folded.state, reported ++ clearing,
            nextSequence + count,
            undo = offeredUndo(folded),
            redo = offeredRedo(folded),
            logRevision = folded.logRevision))
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

  private def extended(journal: FoldedJournal, events: Vector[OathEvent],
      from: Long): Either[GameApplicationError, FoldedJournal] =
    journalFold.extend(journal, events.zipWithIndex.map {
        case (event, offset) => RecordedEvent(from + offset, event) })
      .left.map(failure => ReplayFailure(failure.index, failure.violation))

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
      // A consent request waits on its granters; nothing the rules run may
      // move past it (consent design, "Commands"). `routed` sends what may
      // run to the consent desk instead.
      case OathState.Ready(ready) if ready.consent.pending.nonEmpty =>
        Left(OathViolation.ConsentPending)
      // Only the refusing cards' rulers answer an automatic power's step,
      // through consent (automatic power consent design). Its owner never
      // does, even should a park at the step lack its request.
      case _ if answersPowerUseStep(command) =>
        Left(OathViolation.ConsentUnavailable(
          "the refusing cards' rulers decide whether this power works"))
      case OathState.Ready(ready)
          if (ready.game.current.walkerPending.nonEmpty ||
            ready.game.current.walkerProcedure.nonEmpty) &&
            !isWalkerResume(command) =>
        Left(OathViolation.InvalidEventOrder(
          "a walker procedure is pending; only walker resume commands are legal"))
      case _ => applyUnblockedCommand(state, command, nextSequence)

  private def answersPowerUseStep(command: GameCommand): Boolean =
    command match
      case GameCommand.ResolveWalker(_, decision) =>
        PowerUseStep.isDecision(decision.decisionId)
      case _ => false

  private def applyUnblockedCommand(
      state: OathState,
      command: GameCommand,
      nextSequence: Long
  ): Either[OathViolation, OathTransition] =
    command match
      case GameCommand.Begin(chronicle, orders) =>
        rules.beginGame(state, chronicle, orders)
      // The source answer is where a Search draws, so the prepared draw is
      // checked there, against the pile the answer names.
      case GameCommand.ResolveWalker(actor,
          TreeDecision(SearchProcedure.sourceDecisionId, answer)) =>
        state match
          case OathState.Ready(ready)
              if ready.game.current.walkerProcedure.contains(ActionRef.Search) =>
            for
              source <- SearchProcedure.sourceOf(answer)
              prepared <- searchDrawPort.prepare(ready, source)
              authoritative = oathdigital.gameplay.actions.SearchRules.draw(
                ready, source)
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
      // `routed` sends Undo and Redo to the fold on every stream; these arms
      // only keep the match exhaustive.
      case GameCommand.Undo(_) => Left(OathViolation.NothingToUndo)
      case GameCommand.Redo(_) => Left(OathViolation.NothingToRedo)
      // `routed` sends these to the consent desk; none reaches the rules.
      case _: GameCommand.ConsentCommand => Left(OathViolation.InvalidEventOrder(
        "a consent command never reaches the rules"))

  private def isWalkerResume(command: GameCommand): Boolean = command match
    case _: GameCommand.ResolveWalker => true
    case _ => false

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
