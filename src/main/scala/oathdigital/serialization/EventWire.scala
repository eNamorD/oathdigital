package oathdigital.serialization

import scala.util.control.NonFatal

import oathdigital.engine.RecordedEvent
import oathdigital.model.CatalogRef
import oathdigital.setup.SetupEvent
import oathdigital.setup.SetupEvent.{PawnPlaced, SetupCompleted, SetupStarted}
import oathdigital.setup.SetupParticipant
import oathdigital.model.{LineageId, PlayerId, SiteId}

sealed trait WireError extends Product with Serializable {
  def path: String
  def message: String
}

object WireError {
  final case class MalformedJson(path: String, message: String)
      extends WireError
  final case class MissingField(path: String, message: String)
      extends WireError
  final case class WrongType(path: String, message: String)
      extends WireError
  final case class UnsupportedFormatVersion(
      path: String,
      actual: Int,
      supported: Int
  ) extends WireError {
    override val message: String =
      s"unsupported format version $actual; supported version is $supported"
  }
  final case class UnknownEventType(path: String, eventType: String)
      extends WireError {
    override val message: String = s"unknown event type '$eventType'"
  }
  final case class InvalidValue(path: String, message: String)
      extends WireError
  final case class CatalogMismatch(
      path: String,
      expected: CatalogRef,
      actual: CatalogRef
  ) extends WireError {
    override val message: String =
      s"catalog ${actual.ruleset}@${actual.version} does not match " +
        s"${expected.ruleset}@${expected.version}"
  }
  final case class InvalidSequence(
      path: String,
      expected: Long,
      actual: Long
  ) extends WireError {
    override val message: String =
      s"expected contiguous sequence position $expected but found $actual"
  }
}

final case class SetupEventEnvelope(
    formatVersion: Int,
    gameId: String,
    sequence: Long,
    catalog: CatalogRef,
    eventType: String,
    event: SetupEvent
)

/**
 * Versioned durable JSON boundary for setup domain events.
 *
 * Discriminators and field names are public wire data. They are deliberately
 * independent of Scala class names and require no reflection.
 */
object SetupEventWire {
  import WireError._

  val FormatVersion: Int = 1
  val MaxSafeSequence: Long = 9007199254740991L
  val SetupStartedType: String = "setup.started"
  val PawnPlacedType: String = "setup.pawn-placed"
  val SetupCompletedType: String = "setup.completed"

  def encode(envelope: SetupEventEnvelope): Either[WireError, ujson.Value] =
    validateEnvelope(envelope).map { _ =>
      ujson.Obj(
        "formatVersion" -> envelope.formatVersion,
        "gameId" -> envelope.gameId,
        "sequence" -> ujson.Num(envelope.sequence.toDouble),
        "catalog" -> encodeCatalog(envelope.catalog),
        "eventType" -> envelope.eventType,
        "payload" -> encodePayload(envelope.event)
      )
    }

  def decode(value: ujson.Value, path: String = "$")
      : Either[WireError, SetupEventEnvelope] =
    for {
      obj <- asObject(value, path)
      version <- intField(obj, "formatVersion", path)
      _ <-
        if (version == FormatVersion) Right(())
        else
          Left(
            UnsupportedFormatVersion(
              s"$path.formatVersion",
              version,
              FormatVersion
            )
          )
      gameId <- nonBlankStringField(obj, "gameId", path)
      sequence <- longField(obj, "sequence", path)
      _ <- validateSequence(sequence, s"$path.sequence")
      catalogValue <- field(obj, "catalog", path)
      catalog <- decodeCatalog(catalogValue, s"$path.catalog")
      eventType <- nonBlankStringField(obj, "eventType", path)
      payload <- field(obj, "payload", path)
      event <- decodePayload(
        eventType,
        payload,
        s"$path.payload",
        s"$path.eventType",
        catalog
      )
    } yield SetupEventEnvelope(
      version,
      gameId,
      sequence,
      catalog,
      eventType,
      event
    )

  def encodeStream(
      gameId: String,
      catalog: CatalogRef,
      events: Vector[RecordedEvent[SetupEvent]]
  ): Either[WireError, String] =
    for {
      _ <- validateNonBlank(gameId, "$[*].gameId", "game ID")
      _ <- validateCatalog(catalog, "$[*].catalog")
      values <- traverse(events.zipWithIndex) {
        case (record, position) =>
          if (record.index != position.toLong)
            Left(
              InvalidSequence(
                s"$$[$position].sequence",
                position.toLong,
                record.index
              )
            )
          else {
            val eventType = discriminator(record.event)
            encode(
              SetupEventEnvelope(
                FormatVersion,
                gameId,
                record.index,
                catalog,
                eventType,
                record.event
              )
            )
          }
      }
    } yield ujson.write(ujson.Arr.from(values), indent = 2)

  def decodeStream(json: String)
      : Either[WireError, Vector[SetupEventEnvelope]] =
    for {
      value <- parse(json)
      array <- asArray(value, "$")
      envelopes <- traverse(array.value.zipWithIndex.toVector) {
        case (item, position) => decode(item, s"$$[$position]")
      }
      _ <- validateStream(envelopes)
    } yield envelopes

  private def parse(json: String): Either[WireError, ujson.Value] =
    try Right(ujson.read(json))
    catch {
      case NonFatal(error) =>
        Left(MalformedJson("$", Option(error.getMessage).getOrElse(
          "invalid JSON"
        )))
    }

  private def validateStream(
      envelopes: Vector[SetupEventEnvelope]
  ): Either[WireError, Unit] =
    envelopes.zipWithIndex.foldLeft[Either[WireError, Option[
      (String, CatalogRef)
    ]]](Right(None)) {
      case (failure @ Left(_), _) => failure
      case (Right(identity), (envelope, position)) =>
        if (envelope.sequence != position.toLong)
          Left(
            InvalidSequence(
              s"$$[$position].sequence",
              position.toLong,
              envelope.sequence
            )
          )
        else
          identity match {
            case None => Right(Some(envelope.gameId -> envelope.catalog))
            case Some((gameId, _)) if envelope.gameId != gameId =>
              Left(
                InvalidValue(
                  s"$$[$position].gameId",
                  s"must match stream game ID '$gameId'"
                )
              )
            case Some((_, catalog)) if envelope.catalog != catalog =>
              Left(
                CatalogMismatch(
                  s"$$[$position].catalog",
                  catalog,
                  envelope.catalog
                )
              )
            case current => Right(current)
          }
    }.map(_ => ())

  private def validateEnvelope(
      envelope: SetupEventEnvelope
  ): Either[WireError, Unit] =
    for {
      _ <-
        if (envelope.formatVersion == FormatVersion) Right(())
        else
          Left(
            UnsupportedFormatVersion(
              "$.formatVersion",
              envelope.formatVersion,
              FormatVersion
            )
          )
      _ <- validateNonBlank(envelope.gameId, "$.gameId", "game ID")
      _ <- validateSequence(envelope.sequence, "$.sequence")
      _ <- validateCatalog(envelope.catalog, "$.catalog")
      expectedType = discriminator(envelope.event)
      _ <-
        if (envelope.eventType == expectedType) Right(())
        else
          Left(
            InvalidValue(
              "$.eventType",
              s"must be '$expectedType' for this event"
            )
          )
      _ <- envelope.event match {
        case SetupStarted(_, payloadCatalog, _)
            if payloadCatalog != envelope.catalog =>
          Left(CatalogMismatch("$.payload.catalog", envelope.catalog,
            payloadCatalog))
        case _ => Right(())
      }
    } yield ()

  private def discriminator(event: SetupEvent): String =
    event match {
      case _: SetupStarted => SetupStartedType
      case _: PawnPlaced => PawnPlacedType
      case SetupCompleted => SetupCompletedType
    }

  private def encodePayload(event: SetupEvent): ujson.Value =
    event match {
      case SetupStarted(participants, catalog, orderedSites) =>
        ujson.Obj(
          "participants" -> ujson.Arr.from(participants.map { participant =>
            ujson.Obj(
              "playerId" -> participant.playerId.value,
              "lineageId" -> participant.lineageId.value
            )
          }),
          "catalog" -> encodeCatalog(catalog),
          "orderedSites" ->
            ujson.Arr.from(orderedSites.map(site => ujson.Str(site.value)))
        )
      case PawnPlaced(playerId, siteId) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "siteId" -> siteId.value
        )
      case SetupCompleted => ujson.Obj()
    }

  private def decodePayload(
      eventType: String,
      value: ujson.Value,
      path: String,
      eventTypePath: String,
      envelopeCatalog: CatalogRef
  ): Either[WireError, SetupEvent] =
    eventType match {
      case SetupStartedType =>
        for {
          obj <- asObject(value, path)
          participantValue <- field(obj, "participants", path)
          participantArray <- asArray(
            participantValue,
            s"$path.participants"
          )
          participants <- traverse(
            participantArray.value.zipWithIndex.toVector
          ) { case (participant, index) =>
            val participantPath = s"$path.participants[$index]"
            for {
              participantObj <- asObject(participant, participantPath)
              playerId <- nonBlankStringField(
                participantObj,
                "playerId",
                participantPath
              )
              lineageId <- nonBlankStringField(
                participantObj,
                "lineageId",
                participantPath
              )
            } yield SetupParticipant(PlayerId(playerId), LineageId(lineageId))
          }
          catalogValue <- field(obj, "catalog", path)
          catalog <- decodeCatalog(catalogValue, s"$path.catalog")
          _ <-
            if (catalog == envelopeCatalog) Right(())
            else Left(CatalogMismatch(s"$path.catalog", envelopeCatalog,
              catalog))
          sitesValue <- field(obj, "orderedSites", path)
          sitesArray <- asArray(sitesValue, s"$path.orderedSites")
          sites <- traverse(sitesArray.value.zipWithIndex.toVector) {
            case (site, index) =>
              asNonBlankString(site, s"$path.orderedSites[$index]",
                "site ID").map(SiteId)
          }
        } yield SetupStarted(participants, catalog, sites)
      case PawnPlacedType =>
        for {
          obj <- asObject(value, path)
          playerId <- nonBlankStringField(obj, "playerId", path)
          siteId <- nonBlankStringField(obj, "siteId", path)
        } yield PawnPlaced(PlayerId(playerId), SiteId(siteId))
      case SetupCompletedType =>
        asObject(value, path).map(_ => SetupCompleted)
      case other => Left(UnknownEventType(eventTypePath, other))
    }

  private def encodeCatalog(catalog: CatalogRef): ujson.Obj =
    ujson.Obj(
      "ruleset" -> catalog.ruleset,
      "version" -> catalog.version
    )

  private def decodeCatalog(
      value: ujson.Value,
      path: String
  ): Either[WireError, CatalogRef] =
    for {
      obj <- asObject(value, path)
      ruleset <- nonBlankStringField(obj, "ruleset", path)
      version <- nonBlankStringField(obj, "version", path)
    } yield CatalogRef(ruleset, version)

  private def validateCatalog(
      catalog: CatalogRef,
      path: String
  ): Either[WireError, Unit] =
    for {
      _ <- validateNonBlank(catalog.ruleset, s"$path.ruleset", "ruleset")
      _ <- validateNonBlank(catalog.version, s"$path.version",
        "catalog version")
    } yield ()

  private def field(
      obj: ujson.Obj,
      name: String,
      path: String
  ): Either[WireError, ujson.Value] =
    obj.value.get(name).toRight(
      MissingField(s"$path.$name", "field is required")
    )

  private def nonBlankStringField(
      obj: ujson.Obj,
      name: String,
      path: String
  ): Either[WireError, String] =
    field(obj, name, path).flatMap(value =>
      asNonBlankString(value, s"$path.$name", name)
    )

  private def intField(
      obj: ujson.Obj,
      name: String,
      path: String
  ): Either[WireError, Int] =
    field(obj, name, path).flatMap {
      case ujson.Num(value)
          if value.isValidInt && value == value.toInt.toDouble =>
        Right(value.toInt)
      case _ => Left(WrongType(s"$path.$name", "expected an integer"))
    }

  private def longField(
      obj: ujson.Obj,
      name: String,
      path: String
  ): Either[WireError, Long] =
    field(obj, name, path).flatMap {
      case ujson.Num(value)
          if !value.isNaN &&
            !value.isInfinity &&
            value >= Long.MinValue.toDouble &&
            value <= Long.MaxValue.toDouble &&
            value == value.toLong.toDouble =>
        Right(value.toLong)
      case _ => Left(WrongType(s"$path.$name", "expected an integer"))
    }

  private def asNonBlankString(
      value: ujson.Value,
      path: String,
      label: String
  ): Either[WireError, String] =
    value match {
      case ujson.Str(text) => validateNonBlank(text, path, label).map(_ => text)
      case _ => Left(WrongType(path, "expected a string"))
    }

  private def validateNonBlank(
      value: String,
      path: String,
      label: String
  ): Either[WireError, Unit] =
    if (value.trim.nonEmpty) Right(())
    else Left(InvalidValue(path, s"$label must not be blank"))

  private def validateSequence(
      value: Long,
      path: String
  ): Either[WireError, Unit] =
    if (value >= 0 && value <= MaxSafeSequence) Right(())
    else
      Left(
        InvalidValue(
          path,
          s"must be between 0 and $MaxSafeSequence inclusive"
        )
      )

  private def asObject(
      value: ujson.Value,
      path: String
  ): Either[WireError, ujson.Obj] =
    value match {
      case obj: ujson.Obj => Right(obj)
      case _ => Left(WrongType(path, "expected an object"))
    }

  private def asArray(
      value: ujson.Value,
      path: String
  ): Either[WireError, ujson.Arr] =
    value match {
      case array: ujson.Arr => Right(array)
      case _ => Left(WrongType(path, "expected an array"))
    }

  private def traverse[A, B](
      values: Vector[A]
  )(f: A => Either[WireError, B]): Either[WireError, Vector[B]] =
    values.foldLeft[Either[WireError, Vector[B]]](Right(Vector.empty)) {
      case (Right(accumulated), value) =>
        f(value).map(accumulated :+ _)
      case (failure @ Left(_), _) => failure
    }
}
