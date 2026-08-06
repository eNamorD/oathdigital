package oathdigital.serialization

import scala.util.control.NonFatal

import oathdigital.engine.RecordedEvent
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.FirstGameSetupEvent._

final case class FirstGameEventEnvelope(
    formatVersion: Int,
    gameId: String,
    sequence: Long,
    catalog: CatalogRef,
    eventType: String,
    event: FirstGameSetupEvent
)

/**
 * Explicit mixed vocabulary: v2 setup followed by v3 gameplay events.
 *
 * V1 remains owned by `SetupEventWire`; this dual reader/writer boundary keeps
 * its checked-in bytes unchanged instead of reinterpreting old payloads.
 */
object FirstGameEventWire {
  import WireError._

  val FormatVersion: Int = 2
  val GameplayFormatVersion: Int = 3
  val MaxSafeSequence: Long = SetupEventWire.MaxSafeSequence
  val FirstGameStartedType = "setup.first-game-started"
  val PawnPlacedType = "setup.first-game-pawn-placed"
  val AdviserChosenType = "setup.starting-adviser-chosen"
  val FirstGameCompletedType = "setup.first-game-completed"
  val TakeWealthType = "gameplay.take-wealth"
  val WakeEndedType = "gameplay.wake-ended"
  val TraveledType = "gameplay.traveled"

  /** Encodes one event at its absolute position in the game stream. */
  def encodeEvent(
      gameId: String,
      catalog: CatalogRef,
      sequence: Long,
      event: FirstGameSetupEvent
  ): Either[WireError, ujson.Value] =
    encode(
      FirstGameEventEnvelope(
        formatVersion(event),
        gameId,
        sequence,
        catalog,
        discriminator(event),
        event
      )
    )

  def encode(
      envelope: FirstGameEventEnvelope
  ): Either[WireError, ujson.Value] =
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

  def encodeStream(
      gameId: String,
      catalog: CatalogRef,
      events: Vector[RecordedEvent[FirstGameSetupEvent]]
  ): Either[WireError, String] = {
    val startSequence = events.headOption.map(_.index).getOrElse(0L)
    encodeStream(gameId, catalog, startSequence, events)
  }

  def encodeStream(
      gameId: String,
      catalog: CatalogRef,
      startSequence: Long,
      events: Vector[RecordedEvent[FirstGameSetupEvent]]
  ): Either[WireError, String] =
    validateSequence(startSequence, "$[*].sequence").flatMap { _ =>
      traverse(events.zipWithIndex) { case (record, position) =>
        val expected =
          if (startSequence > MaxSafeSequence - position.toLong)
            None
          else Some(startSequence + position.toLong)
        if (!expected.contains(record.index))
          Left(
            InvalidSequence(
              s"$$[$position].sequence",
              expected.getOrElse(MaxSafeSequence),
              record.index
            )
          )
        else
          encodeEvent(gameId, catalog, record.index, record.event)
      }.map(values => ujson.write(ujson.Arr.from(values), indent = 2))
    }

  def decodeStream(
      json: String
  ): Either[WireError, Vector[FirstGameEventEnvelope]] =
    try {
      ujson.read(json) match {
        case array: ujson.Arr =>
          traverse(array.value.zipWithIndex.toVector) {
            case (value, position) => decode(value, s"$$[$position]")
          }.flatMap(validateStream)
        case _ => Left(WrongType("$", "expected an array"))
      }
    } catch {
      case NonFatal(error) =>
        Left(
          MalformedJson(
            "$",
            Option(error.getMessage).getOrElse("invalid JSON")
          )
        )
    }

  def decode(
      value: ujson.Value,
      path: String = "$"
  ): Either[WireError, FirstGameEventEnvelope] = {
    value match {
      case obj: ujson.Obj =>
        for {
          version <- formatVersionField(obj, path)
          _ <-
            if (version == FormatVersion || version == GameplayFormatVersion)
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
            if (gameId.trim.nonEmpty) Right(())
            else Left(InvalidValue(s"$path.gameId", "gameId must not be blank"))
          sequence <- safeIntegerField(obj, "sequence", path)
          refValue <- requiredField(obj, "catalog", path)
          ref <- decodeCatalog(refValue, s"$path.catalog")
          eventType <- stringField(obj, "eventType", path)
          payload <- requiredField(obj, "payload", path)
          event <- decodePayload(
            eventType,
            payload,
            s"$path.payload",
            ref
          )
          _ <- validateEventVersion(version, eventType, path)
        } yield FirstGameEventEnvelope(
          version,
          gameId,
          sequence,
          ref,
          eventType,
          event
        )
      case _ => Left(WrongType(path, "expected an object"))
    }
  }

  private def validateStream(
      envelopes: Vector[FirstGameEventEnvelope]
  ): Either[WireError, Vector[FirstGameEventEnvelope]] =
    envelopes.headOption match {
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
          case (envelope, index) if envelope.catalog != first.catalog =>
            CatalogMismatch(
              s"$$[$index].catalog",
              first.catalog,
              envelope.catalog
            )
        }.toLeft(envelopes)
    }

  private def validateEnvelope(
      envelope: FirstGameEventEnvelope
  ): Either[WireError, Unit] =
    for {
      _ <-
        if (envelope.formatVersion == formatVersion(envelope.event)) Right(())
        else
          Left(
            UnsupportedFormatVersion(
              "$.formatVersion",
              envelope.formatVersion,
              formatVersion(envelope.event)
            )
          )
      _ <-
        if (envelope.gameId.trim.nonEmpty) Right(())
        else Left(InvalidValue("$.gameId", "gameId must not be blank"))
      _ <- validateSequence(envelope.sequence, "$.sequence")
      _ <-
        if (envelope.eventType == discriminator(envelope.event)) Right(())
        else
          Left(
            InvalidValue(
              "$.eventType",
              s"must be '${discriminator(envelope.event)}' for this event"
            )
          )
      _ <- validateEventCatalog(envelope.event, envelope.catalog, "$")
    } yield ()

  private def discriminator(event: FirstGameSetupEvent): String =
    event match {
      case _: FirstGameStarted => FirstGameStartedType
      case _: FirstGamePawnPlaced => PawnPlacedType
      case _: StartingAdviserChosen => AdviserChosenType
      case FirstGameCompleted => FirstGameCompletedType
      case _: WealthTaken => TakeWealthType
      case _: WakeEnded => WakeEndedType
      case _: Traveled => TraveledType
    }

  private def formatVersion(event: FirstGameSetupEvent): Int = event match {
    case _: WealthTaken | _: WakeEnded | _: Traveled => GameplayFormatVersion
    case _ => FormatVersion
  }

  private def encodePayload(event: FirstGameSetupEvent): ujson.Value =
    event match {
      case FirstGameStarted(plan) => encodePlan(plan)
      case FirstGamePawnPlaced(playerId, siteId) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "siteId" -> siteId.value
        )
      case StartingAdviserChosen(playerId, adviserId) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "adviserId" -> adviserId.value
        )
      case FirstGameCompleted => ujson.Obj()
      case WealthTaken(playerId, siteId, resource) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "siteId" -> siteId.value,
          "resource" -> (resource match {
            case WakeResource.Favor => "favor"
            case WakeResource.Secret => "secret"
          })
        )
      case WakeEnded(playerId) =>
        ujson.Obj("playerId" -> playerId.value)
      case Traveled(playerId, source, destination, supplySpent) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "sourceSiteId" -> source.value,
          "destinationSiteId" -> destination.value,
          "supplySpent" -> supplySpent
        )
    }

  private def decodePayload(
      eventType: String,
      payload: ujson.Value,
      path: String,
      envelopeCatalog: CatalogRef
  ): Either[WireError, FirstGameSetupEvent] =
    try {
      eventType match {
        case FirstGameStartedType =>
          decodePlan(payload, path).flatMap { plan =>
            if (plan.catalog == envelopeCatalog)
              Right(FirstGameStarted(plan))
            else
              Left(
                CatalogMismatch(
                  s"$path.catalog",
                  envelopeCatalog,
                  plan.catalog
                )
              )
          }
        case PawnPlacedType =>
          Right(
            FirstGamePawnPlaced(
              PlayerId(payload("playerId").str),
              SiteId(payload("siteId").str)
            )
          )
        case AdviserChosenType =>
          Right(
            StartingAdviserChosen(
              PlayerId(payload("playerId").str),
              DenizenId(payload("adviserId").str)
            )
          )
        case FirstGameCompletedType => Right(FirstGameCompleted)
        case TakeWealthType =>
          val resource = payload("resource").str match {
            case "favor" => Right(WakeResource.Favor)
            case "secret" => Right(WakeResource.Secret)
            case other => Left(InvalidValue(
              s"$path.resource",
              s"unknown wealth resource '$other'"
            ))
          }
          resource.map(WealthTaken(
            PlayerId(payload("playerId").str),
            SiteId(payload("siteId").str),
            _
          ))
        case WakeEndedType =>
          Right(WakeEnded(PlayerId(payload("playerId").str)))
        case TraveledType =>
          val spent = payload("supplySpent").num
          if (!spent.isFinite || spent != Math.rint(spent) || spent < 0 ||
              spent > Int.MaxValue)
            Left(InvalidValue(s"$path.supplySpent",
              "must be a non-negative integer"))
          else Right(Traveled(
            PlayerId(payload("playerId").str),
            SiteId(payload("sourceSiteId").str),
            SiteId(payload("destinationSiteId").str),
            spent.toInt
          ))
        case other => Left(UnknownEventType(s"$path.eventType", other))
      }
    } catch {
      case NonFatal(error) =>
        Left(
          InvalidValue(
            path,
            Option(error.getMessage).getOrElse("invalid payload")
          )
        )
    }

  private def validateEventVersion(
      version: Int,
      eventType: String,
      path: String
  ): Either[WireError, Unit] = {
    val expected =
      if (eventType == TakeWealthType || eventType == WakeEndedType ||
          eventType == TraveledType)
        GameplayFormatVersion
      else FormatVersion
    if (version == expected) Right(())
    else Left(InvalidValue(
      s"$path.formatVersion",
      s"event type '$eventType' requires format version $expected"
    ))
  }

  private def encodePlan(plan: FirstGameSetupPlan): ujson.Value =
    ujson.Obj(
      "catalog" -> encodeCatalog(plan.catalog),
      "participants" -> ujson.Arr.from(plan.participants.map { participant =>
        ujson.Obj(
          "playerId" -> participant.playerId.value,
          "lineageId" -> participant.lineageId.value,
          "color" -> participant.color.value
        )
      }),
      "firstPlayer" -> plan.firstPlayer.value,
      "orderedSites" -> stringArray(plan.orderedSites.map(_.value)),
      "denizenOrder" -> stringArray(plan.denizenOrder.map(_.value)),
      "worldDeckOrder" -> ujson.Arr.from(
        plan.worldDeckOrder.map {
          case id: DenizenId =>
            ujson.Obj("kind" -> "denizen", "id" -> id.value)
          case id: VisionId =>
            ujson.Obj("kind" -> "vision", "id" -> id.value)
        }
      ),
      "relicOrder" -> stringArray(plan.relicOrder.map(_.value)),
      "homelandEdifices" -> ujson.Arr.from(
        plan.homelandEdifices.map { case (siteId, edificeId) =>
          ujson.Obj(
            "siteId" -> siteId.value,
            "edificeId" -> edificeId.value
          )
        }
      )
    )

  private def decodePlan(
      value: ujson.Value,
      path: String
  ): Either[WireError, FirstGameSetupPlan] =
    try {
      val obj = value.obj
      for {
        catalog <- decodeCatalog(obj("catalog"), s"$path.catalog")
        participants <- traverse(
          obj("participants").arr.zipWithIndex.toVector
        ) { case (participant, _) =>
          Right(
            FirstGameParticipant(
              PlayerId(participant("playerId").str),
              LineageId(participant("lineageId").str),
              PlayerColor(participant("color").str)
            )
          )
        }
        world <- traverse(obj("worldDeckOrder").arr.toVector) { item =>
          item("kind").str match {
            case "denizen" =>
              Right(DenizenId(item("id").str): WorldCardId)
            case "vision" =>
              Right(VisionId(item("id").str): WorldCardId)
            case kind =>
              Left(
                InvalidValue(
                  s"$path.worldDeckOrder",
                  s"unknown card kind $kind"
                )
              )
          }
        }
      } yield FirstGameSetupPlan(
        catalog,
        participants,
        PlayerId(obj("firstPlayer").str),
        obj("orderedSites").arr.toVector.map(v => SiteId(v.str)),
        obj("denizenOrder").arr.toVector.map(v => DenizenId(v.str)),
        world,
        obj("relicOrder").arr.toVector.map(v => RelicId(v.str)),
        obj("homelandEdifices").arr.toVector.map { entry =>
          SiteId(entry("siteId").str) -> EdificeId(entry("edificeId").str)
        }
      )
    } catch {
      case NonFatal(error) =>
        Left(
          InvalidValue(
            path,
            Option(error.getMessage).getOrElse("invalid setup plan")
          )
        )
    }

  private def validateEventCatalog(
      event: FirstGameSetupEvent,
      catalog: CatalogRef,
      path: String
  ): Either[WireError, Unit] =
    event match {
      case FirstGameStarted(plan) if plan.catalog != catalog =>
        Left(CatalogMismatch(s"$path.payload.catalog", catalog, plan.catalog))
      case _ => Right(())
    }

  private def encodeCatalog(ref: CatalogRef): ujson.Value =
    ujson.Obj("ruleset" -> ref.ruleset, "version" -> ref.version)

  private def decodeCatalog(
      value: ujson.Value,
      path: String
  ): Either[WireError, CatalogRef] =
    try Right(CatalogRef(value("ruleset").str, value("version").str))
    catch {
      case NonFatal(error) =>
        Left(
          InvalidValue(
            path,
            Option(error.getMessage).getOrElse("invalid catalog")
          )
        )
    }

  private def requiredField(
      obj: ujson.Obj,
      name: String,
      path: String
  ): Either[WireError, ujson.Value] =
    obj.value.get(name).toRight(
      MissingField(s"$path.$name", "field is required")
    )

  private def stringField(
      obj: ujson.Obj,
      name: String,
      path: String
  ): Either[WireError, String] =
    requiredField(obj, name, path).flatMap {
      case ujson.Str(value) => Right(value)
      case _ => Left(WrongType(s"$path.$name", "expected a string"))
    }

  private def formatVersionField(
      obj: ujson.Obj,
      path: String
  ): Either[WireError, Int] =
    requiredField(obj, "formatVersion", path)
      .flatMap(value => safeInteger(value, s"$path.formatVersion"))
      .flatMap { value =>
        if (value <= Int.MaxValue.toLong) Right(value.toInt)
        else
          Left(
            InvalidValue(
              s"$path.formatVersion",
              s"must be between 0 and ${Int.MaxValue} inclusive"
            )
          )
      }

  private def safeIntegerField(
      obj: ujson.Obj,
      name: String,
      path: String
  ): Either[WireError, Long] =
    requiredField(obj, name, path)
      .flatMap(value => safeInteger(value, s"$path.$name"))

  private def safeInteger(
      value: ujson.Value,
      path: String
  ): Either[WireError, Long] =
    value match {
      case ujson.Num(number)
          if !number.isNaN &&
            !number.isInfinity &&
            number == math.rint(number) &&
            number >= 0 &&
            number <= MaxSafeSequence.toDouble =>
        Right(number.toLong)
      case ujson.Num(number)
          if !number.isNaN &&
            !number.isInfinity &&
            number == math.rint(number) =>
        Left(
          InvalidValue(
            path,
            s"must be between 0 and $MaxSafeSequence inclusive"
          )
        )
      case _ => Left(WrongType(path, "expected an integer"))
    }

  private def validateSequence(
      sequence: Long,
      path: String
  ): Either[WireError, Unit] =
    if (sequence >= 0 && sequence <= MaxSafeSequence) Right(())
    else
      Left(
        InvalidValue(
          path,
          s"must be between 0 and $MaxSafeSequence inclusive"
        )
      )

  private def stringArray(values: Vector[String]): ujson.Value =
    ujson.Arr.from(values.map(ujson.Str(_)))

  private def traverse[A, B](
      values: Vector[A]
  )(f: A => Either[WireError, B]): Either[WireError, Vector[B]] =
    values.foldLeft[Either[WireError, Vector[B]]](Right(Vector.empty)) {
      case (Right(acc), value) => f(value).map(acc :+ _)
      case (failure @ Left(_), _) => failure
    }
}
