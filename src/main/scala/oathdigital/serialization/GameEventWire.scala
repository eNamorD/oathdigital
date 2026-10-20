package oathdigital.serialization

import scala.util.control.NonFatal

import oathdigital.engine.RecordedEvent
import oathdigital.model._

final case class GameEventEnvelope(
    formatVersion: Int,
    gameId: String,
    sequence: Long,
    eventType: String,
    event: OathEvent
)

/**
 * Explicit current game-event vocabulary, including first-game setup.
 */
object GameEventWire extends GameEventJsonSupport with LifecycleEventCodec
    with EndingEventCodec
    with WalkerEventCodec with UndoEventCodec with ConsentEventCodec:
  import WireError._

  /** The pre-release stream has one current format and no compatibility reader. */
  val FormatVersion: Int = 1
  val MaxSafeSequence: Long = 9007199254740991L
  val GameStartedType = "setup.game-started"
  val IgnoredRulesRecordedType = "diagnostic.ignored-rules-recorded"
  val BanditsRefilledType = "gameplay.bandits-refilled"
  val UsurperFlippedType = "gameplay.usurper-flipped"
  val UsurperVictoryType = "gameplay.usurper-victory"
  val VisionVictoryType = "gameplay.vision-victory"
  val RoundEndedType = "gameplay.round-ended"
  val WarExhaustionResolvedType = "gameplay.war-exhaustion-resolved"
  val WalkerStepRecordedType = "walker.step-recorded"
  val WalkerParkedType = "walker.parked"
  val WalkerCompletedType = "walker.completed"
  /** The Scala type is `LogNoted`; the wire tag keeps the older `power`
    * spelling, because changing it would make an openable game fail to
    * load. */
  val LogNotedType = "walker.power-noted"
  val ProcedureDequeuedType = "walker.procedure-dequeued"
  val FollowUpSkippedType = "walker.follow-up-skipped"
  val ActionBoundaryRanType = "walker.action-boundary-ran"
  val CommandAcceptedType = "undo.command-accepted"
  val StepTakenBackType = "undo.step-taken-back"
  val StepRestoredType = "undo.step-restored"
  val ConsentRequestedType = "consent.requested"
  val ConsentAnsweredType = "consent.answered"
  val ConsentRequestWithdrawnType = "consent.request-withdrawn"
  val ConsentPreferenceSetType = "consent.preference-set"
  val ConsentPreferencesClearedType = "consent.preferences-cleared"

  /** Encodes one event at its absolute position in the game stream. */
  def encodeEvent(
      gameId: String,
      sequence: Long,
      event: OathEvent
  ): Either[WireError, ujson.Value] =
    discriminator(event).flatMap { eventType =>
      encode(
        GameEventEnvelope(
          FormatVersion,
          gameId,
          sequence,
          eventType,
          event
        )
      )
    }

  def encode(
      envelope: GameEventEnvelope
  ): Either[WireError, ujson.Value] =
    validateEnvelope(envelope).flatMap { _ =>
      encodePayloadSafe(envelope.event).map { payload =>
        ujson.Obj(
          "formatVersion" -> envelope.formatVersion,
          "gameId" -> envelope.gameId,
          "sequence" -> ujson.Num(envelope.sequence.toDouble),
          "eventType" -> envelope.eventType,
          "payload" -> payload
        )
      }
    }

  /** Encoders in this vocabulary are total for every event this build knows
    * how to construct (I8: `WalkerEventCodec.encodeOperation`/`encodePiece`
    * are now total over `CoreOperation`/`Piece` too, matching
    * `encodeLocation`), so an unencodable value reaching this append path
    * must surface as a typed [[WireError]] rather than escape as a raw
    * exception (matches the decode side's `NonFatal` boundary in
    * `decodePayload`). There is no longer a special case ahead of the
    * generic `NonFatal` fallback: the walker's control vocabulary is not a
    * `CoreOperation`, so the shapes that once needed one cannot reach the
    * encoder at all.
    *
    * This boundary covers a value that fails *inside* an encoder, which the
    * two dispatches being composed separately still allows: a vocabulary
    * registering a discriminator arm without an encoder arm lands here. An
    * event no vocabulary registers at all never reaches it, because
    * [[discriminator]] reports that as a typed error first.
    */
  private def encodePayloadSafe(
      event: OathEvent
  ): Either[WireError, ujson.Value] =
    try Right(encodePayload(event))
    catch
      case NonFatal(error) =>
        Left(
          InvalidValue(
            "$.payload",
            Option(error.getMessage).getOrElse("invalid payload")
          )
        )

  def encodeStream(
      gameId: String,
      events: Vector[RecordedEvent[OathEvent]]
  ): Either[WireError, String] =
    val startSequence = events.headOption.map(_.index).getOrElse(0L)
    encodeStream(gameId, startSequence, events)

  def encodeStream(
      gameId: String,
      startSequence: Long,
      events: Vector[RecordedEvent[OathEvent]]
  ): Either[WireError, String] =
    validateSequence(startSequence, "$[*].sequence").flatMap { _ =>
      traverse(events.zipWithIndex) { case (record, position) =>
        val expected =
          if startSequence > MaxSafeSequence - position.toLong then
            None
          else Some(startSequence + position.toLong)
        if !expected.contains(record.index) then
          Left(
            InvalidSequence(
              s"$$[$position].sequence",
              expected.getOrElse(MaxSafeSequence),
              record.index
            )
          )
        else
          encodeEvent(gameId, record.index, record.event)
      }.map(values => ujson.write(ujson.Arr.from(values), indent = 2))
    }

  def decodeStream(
      json: String
  ): Either[WireError, Vector[GameEventEnvelope]] =
    try
      ujson.read(json) match
        case array: ujson.Arr =>
          traverse(array.value.zipWithIndex.toVector) {
            case (value, position) => decode(value, s"$$[$position]")
          }.flatMap(validateStream)
        case _ => Left(WrongType("$", "expected an array"))
    catch
      case NonFatal(error) =>
        Left(
          MalformedJson(
            "$",
            Option(error.getMessage).getOrElse("invalid JSON")
          )
        )

  def decode(
      value: ujson.Value,
      path: String = "$"
  ): Either[WireError, GameEventEnvelope] =
    value match
      case obj: ujson.Obj =>
        for
          version <- formatVersionField(obj, path)
          _ <-
            if version == FormatVersion then
              Right(())
            else
              Left(
                UnsupportedFormatVersion(
                  s"$path.formatVersion",
                  version,
                  FormatVersion
                )
              )
          gameId <- stringField(obj, "gameId", path)
          _ <-
            if gameId.trim.nonEmpty then Right(())
            else Left(InvalidValue(s"$path.gameId", "gameId must not be blank"))
          sequence <- safeIntegerField(obj, "sequence", path)
          eventType <- stringField(obj, "eventType", path)
          payload <- requiredField(obj, "payload", path)
          event <- decodePayload(
            eventType,
            payload,
            s"$path.payload"
          )
        yield GameEventEnvelope(
          version,
          gameId,
          sequence,
          eventType,
          event
        )
      case _ => Left(WrongType(path, "expected an object"))

  private def validateStream(
      envelopes: Vector[GameEventEnvelope]
  ): Either[WireError, Vector[GameEventEnvelope]] =
    envelopes.headOption match
      case None => Right(envelopes)
      case Some(first) =>
        envelopes.zipWithIndex.collectFirst {
          case (envelope, index)
              if first.sequence > MaxSafeSequence - index.toLong ||
                envelope.sequence != first.sequence + index.toLong =>
            InvalidSequence(
              s"$$[$index].sequence",
              first.sequence + index.toLong,
              envelope.sequence
            )
          case (envelope, index) if envelope.gameId != first.gameId =>
            InvalidValue(
              s"$$[$index].gameId",
              s"must match stream game ID '${first.gameId}'"
            )
        }.toLeft(envelopes)

  private def validateEnvelope(
      envelope: GameEventEnvelope
  ): Either[WireError, Unit] =
    for
      _ <-
        if envelope.formatVersion == FormatVersion then Right(())
        else
          Left(
            UnsupportedFormatVersion(
              "$.formatVersion",
              envelope.formatVersion,
              FormatVersion
            )
          )
      _ <-
        if envelope.gameId.trim.nonEmpty then Right(())
        else Left(InvalidValue("$.gameId", "gameId must not be blank"))
      _ <- validateSequence(envelope.sequence, "$.sequence")
      expected <- discriminator(envelope.event)
      _ <-
        if envelope.eventType == expected then Right(())
        else
          Left(
            InvalidValue(
              "$.eventType",
              s"must be '$expected' for this event"
            )
          )
    yield ()

  private val discriminatorDispatch = lifecycleDiscriminator
    .orElse(endingDiscriminator).orElse(walkerDiscriminator)
    .orElse(undoDiscriminator).orElse(consentDiscriminator)

  private val encoderDispatch = lifecycleEncoder
    .orElse(endingEncoder).orElse(walkerEncoder).orElse(undoEncoder)
    .orElse(consentEncoder)

  /** The wire type for `event`, or a typed error when no vocabulary in this
    * build registers one. `discriminatorDispatch` is a composed
    * `PartialFunction` and `WalkerEvent` is open, so an event with no arm is
    * constructible -- `OathRulesEvolveSuite` declares one on purpose for
    * `evolve`. Returning that partiality instead of applying the dispatch is
    * what keeps it off the append path's exception channel: both callers
    * resolve the type before anything is written, which is outside
    * [[encodePayloadSafe]]'s boundary, so a `MatchError` here would escape
    * the very guarantee that method documents.
    */
  private def discriminator(event: OathEvent): Either[WireError, String] =
    discriminatorDispatch.lift(event).toRight(
      InvalidValue(
        "$.eventType",
        s"no wire type is registered for ${event.productPrefix}"
      )
    )

  private def encodePayload(event: OathEvent): ujson.Value =
    encoderDispatch(event)

  private def decodePayload(
      eventType: String,
      payload: ujson.Value,
      path: String
  ): Either[WireError, OathEvent] =
    try
      lifecycleDecode(eventType, payload, path)
        .orElse(endingDecode(eventType, payload, path))
        .orElse(walkerDecode(eventType, payload, path))
        .orElse(undoDecode(eventType, payload, path))
        .orElse(consentDecode(eventType, payload, path))
        .getOrElse(Left(UnknownEventType(s"$path.eventType", eventType)))
    catch
      case NonFatal(error) => Left(InvalidValue(path,
        Option(error.getMessage).getOrElse("invalid payload")))
