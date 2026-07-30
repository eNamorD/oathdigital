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
 * Explicit v2 vocabulary for the exile-only complete first-game setup.
 *
 * V1 remains owned by `SetupEventWire`; this dual reader/writer boundary keeps
 * its checked-in bytes unchanged instead of reinterpreting old payloads.
 */
object FirstGameEventWire {
  import WireError._

  val FormatVersion: Int = 2
  val FirstGameStartedType = "setup.first-game-started"
  val PawnPlacedType = "setup.first-game-pawn-placed"
  val AdviserChosenType = "setup.starting-adviser-chosen"
  val FirstGameCompletedType = "setup.first-game-completed"

  def encodeStream(
      gameId: String,
      catalog: CatalogRef,
      events: Vector[RecordedEvent[FirstGameSetupEvent]]
  ): Either[WireError, String] =
    if (gameId.trim.isEmpty)
      Left(InvalidValue("$[*].gameId", "gameId must not be blank"))
    else
      traverse(events.zipWithIndex) { case (record, position) =>
        if (record.index != position.toLong)
          Left(
            InvalidSequence(
              s"$$[$position].sequence",
              position.toLong,
              record.index
            )
          )
        else
          validateEventCatalog(record.event, catalog, s"$$[$position]")
            .map { _ =>
              ujson.Obj(
                "formatVersion" -> FormatVersion,
                "gameId" -> gameId,
                "sequence" -> ujson.Num(record.index.toDouble),
                "catalog" -> encodeCatalog(catalog),
                "eventType" -> discriminator(record.event),
                "payload" -> encodePayload(record.event)
              )
            }
      }.map(values => ujson.write(ujson.Arr.from(values), indent = 2))

  def decodeStream(
      json: String
  ): Either[WireError, Vector[FirstGameEventEnvelope]] =
    try {
      ujson.read(json) match {
        case array: ujson.Arr =>
          traverse(array.value.zipWithIndex.toVector) {
            case (value, position) => decodeEnvelope(value, position)
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

  private def decodeEnvelope(
      value: ujson.Value,
      position: Int
  ): Either[WireError, FirstGameEventEnvelope] = {
    val path = s"$$[$position]"
    try {
      val obj = value.obj
      val version = obj("formatVersion").num.toInt
      if (version != FormatVersion)
        Left(
          UnsupportedFormatVersion(
            s"$path.formatVersion",
            version,
            FormatVersion
          )
        )
      else {
        val gameId = obj("gameId").str
        val sequence = obj("sequence").num.toLong
        val catalog = decodeCatalog(obj("catalog"), s"$path.catalog")
        val eventType = obj("eventType").str
        for {
          ref <- catalog
          _ <-
            if (gameId.trim.nonEmpty) Right(())
            else Left(InvalidValue(s"$path.gameId", "gameId must not be blank"))
          _ <-
            if (sequence == position.toLong) Right(())
            else Left(
              InvalidSequence(
                s"$path.sequence",
                position.toLong,
                sequence
              )
            )
          event <- decodePayload(
            eventType,
            obj("payload"),
            s"$path.payload",
            ref
          )
        } yield FirstGameEventEnvelope(
          version,
          gameId,
          sequence,
          ref,
          eventType,
          event
        )
      }
    } catch {
      case NonFatal(error) =>
        Left(
          InvalidValue(
            path,
            Option(error.getMessage).getOrElse("invalid event envelope")
          )
        )
    }
  }

  private def validateStream(
      envelopes: Vector[FirstGameEventEnvelope]
  ): Either[WireError, Vector[FirstGameEventEnvelope]] =
    envelopes.headOption match {
      case None => Right(envelopes)
      case Some(first) =>
        envelopes.zipWithIndex.collectFirst {
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

  private def discriminator(event: FirstGameSetupEvent): String =
    event match {
      case _: FirstGameStarted => FirstGameStartedType
      case _: FirstGamePawnPlaced => PawnPlacedType
      case _: StartingAdviserChosen => AdviserChosenType
      case FirstGameCompleted => FirstGameCompletedType
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
