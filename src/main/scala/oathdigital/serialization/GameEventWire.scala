package oathdigital.serialization

import scala.util.control.NonFatal

import oathdigital.engine.RecordedEvent
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.OathEvent._

final case class GameEventEnvelope(
    formatVersion: Int,
    gameId: String,
    sequence: Long,
    catalog: CatalogRef,
    eventType: String,
    event: OathEvent
)

/**
 * Explicit mixed vocabulary: v2 setup followed by v3 gameplay events.
 *
 * V1 remains owned by `SetupEventWire`; this dual reader/writer boundary keeps
 * its checked-in bytes unchanged instead of reinterpreting old payloads.
 */
object GameEventWire {
  import WireError._

  val FormatVersion: Int = 2
  val GameplayFormatVersion: Int = 3
  val SearchFormatVersion: Int = 4
  val RestFormatVersion: Int = 5
  val EconomyFormatVersion: Int = 6
  val MaxSafeSequence: Long = SetupEventWire.MaxSafeSequence
  val FirstGameStartedType = "setup.first-game-started"
  val PawnPlacedType = "setup.first-game-pawn-placed"
  val AdviserChosenType = "setup.starting-adviser-chosen"
  val FirstGameCompletedType = "setup.first-game-completed"
  val TakeWealthType = "gameplay.take-wealth"
  val WakeEndedType = "gameplay.wake-ended"
  val TraveledType = "gameplay.traveled"
  val MusteredType = "gameplay.mustered"
  val TradedType = "gameplay.traded"
  val SearchStartedType = "gameplay.search-started"
  val SearchCompletedType = "gameplay.search-completed"
  val RestStartedType = "gameplay.rest-started"
  val RestCompletedType = "gameplay.rest-completed"

  /** Encodes one event at its absolute position in the game stream. */
  def encodeEvent(
      gameId: String,
      catalog: CatalogRef,
      sequence: Long,
      event: OathEvent
  ): Either[WireError, ujson.Value] =
    encode(
      GameEventEnvelope(
        formatVersion(event),
        gameId,
        sequence,
        catalog,
        discriminator(event),
        event
      )
    )

  def encode(
      envelope: GameEventEnvelope
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
      events: Vector[RecordedEvent[OathEvent]]
  ): Either[WireError, String] = {
    val startSequence = events.headOption.map(_.index).getOrElse(0L)
    encodeStream(gameId, catalog, startSequence, events)
  }

  def encodeStream(
      gameId: String,
      catalog: CatalogRef,
      startSequence: Long,
      events: Vector[RecordedEvent[OathEvent]]
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
  ): Either[WireError, Vector[GameEventEnvelope]] =
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
  ): Either[WireError, GameEventEnvelope] = {
    value match {
      case obj: ujson.Obj =>
        for {
          version <- formatVersionField(obj, path)
          _ <-
            if (version == FormatVersion || version == GameplayFormatVersion ||
                version == SearchFormatVersion || version == RestFormatVersion ||
                version == EconomyFormatVersion)
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
        } yield GameEventEnvelope(
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
      envelopes: Vector[GameEventEnvelope]
  ): Either[WireError, Vector[GameEventEnvelope]] =
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
      envelope: GameEventEnvelope
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

  private def discriminator(event: OathEvent): String =
    event match {
      case _: FirstGameStarted => FirstGameStartedType
      case _: GamePawnPlaced => PawnPlacedType
      case _: StartingAdviserChosen => AdviserChosenType
      case FirstGameCompleted => FirstGameCompletedType
      case _: WealthTaken => TakeWealthType
      case _: WakeEnded => WakeEndedType
      case _: Traveled => TraveledType
      case _: Mustered => MusteredType
      case _: Traded => TradedType
      case _: SearchStarted => SearchStartedType
      case _: SearchCompleted => SearchCompletedType
      case _: RestStarted => RestStartedType
      case _: RestCompleted => RestCompletedType
    }

  private def formatVersion(event: OathEvent): Int = event match {
    case _: WealthTaken | _: WakeEnded | _: Traveled => GameplayFormatVersion
    case _: Mustered | _: Traded => EconomyFormatVersion
    case _: SearchStarted | _: SearchCompleted => SearchFormatVersion
    case _: RestStarted | _: RestCompleted => RestFormatVersion
    case _ => FormatVersion
  }

  private def encodePayload(event: OathEvent): ujson.Value =
    event match {
      case FirstGameStarted(plan) => encodePlan(plan)
      case GamePawnPlaced(playerId, siteId) =>
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
      case Mustered(playerId, site, target, suit, spent, gained) =>
        ujson.Obj("playerId" -> playerId.value, "siteId" -> site.value,
          "target" -> encodeCardRef(target.id), "suit" -> suit.key,
          "supplySpent" -> spent, "warbandsGained" -> gained)
      case Traded(playerId, site, target, suit, resource, spent, gained) =>
        ujson.Obj("playerId" -> playerId.value, "siteId" -> site.value,
          "target" -> encodeCardRef(target.id), "suit" -> suit.key,
          "resource" -> (resource match {
            case TradeResource.Favor => "favor"
            case TradeResource.Secret => "secret"
          }), "supplySpent" -> spent, "gained" -> gained)
      case SearchStarted(playerId, decision, source, origin, spent, drawn) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "decisionId" -> decision.value,
          "source" -> encodeSearchSource(source),
          "origin" -> origin.key,
          "supplySpent" -> spent,
          "drawn" -> ujson.Arr.from(drawn.map(encodeWorldCard))
        )
      case SearchCompleted(playerId, decision, kept, discarded, placement) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "decisionId" -> decision.value,
          "kept" -> encodeWorldCard(kept),
          "discardedInOrder" -> ujson.Arr.from(discarded.map(encodeWorldCard)),
          "placement" -> encodeSearchPlacement(placement)
        )
      case RestStarted(playerId) => ujson.Obj("playerId" -> playerId.value)
      case RestCompleted(playerId, favor, secrets, supply, next, round) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "returnedFavor" -> ujson.Obj.from(favor.toVector.sortBy(_._1.key)
            .map { case (suit, amount) => suit.key -> ujson.Num(amount) }),
          "returnedSecrets" -> secrets,
          "refreshedSupply" -> supply,
          "nextPlayerId" -> next.value,
          "nextRound" -> round
        )
    }

  private def decodePayload(
      eventType: String,
      payload: ujson.Value,
      path: String,
      envelopeCatalog: CatalogRef
  ): Either[WireError, OathEvent] =
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
            GamePawnPlaced(
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
        case MusteredType => for {
          target <- decodeEconomyTarget(payload("target"), s"$path.target")
          suit <- decodeSuit(payload("suit").str, s"$path.suit")
          spent <- safeIntField(payload.obj, "supplySpent", path)
          gained <- safeIntField(payload.obj, "warbandsGained", path)
        } yield Mustered(PlayerId(payload("playerId").str),
          SiteId(payload("siteId").str), target, suit, spent, gained)
        case TradedType => for {
          target <- decodeEconomyTarget(payload("target"), s"$path.target")
          suit <- decodeSuit(payload("suit").str, s"$path.suit")
          resource <- payload("resource").str match {
            case "favor" => Right(TradeResource.Favor)
            case "secret" => Right(TradeResource.Secret)
            case other => Left(InvalidValue(s"$path.resource",
              s"unknown Trade resource '$other'"))
          }
          spent <- safeIntField(payload.obj, "supplySpent", path)
          gained <- safeIntField(payload.obj, "gained", path)
        } yield Traded(PlayerId(payload("playerId").str),
          SiteId(payload("siteId").str), target, suit, resource, spent, gained)
        case SearchStartedType =>
          for {
            source <- decodeSearchSource(payload("source"), s"$path.source")
            origin <- decodeRegion(payload("origin").str, s"$path.origin")
            drawn <- traverse(payload("drawn").arr.toVector)(decodeWorldCard(_, s"$path.drawn"))
            spent = payload("supplySpent").num
            _ <- if (spent.isFinite && spent == Math.rint(spent) && spent >= 0 &&
              spent <= Int.MaxValue) Right(()) else Left(InvalidValue(
              s"$path.supplySpent", "must be a non-negative integer"))
          } yield SearchStarted(
            PlayerId(payload("playerId").str),
            DecisionId(payload("decisionId").str), source, origin,
            spent.toInt, drawn)
        case SearchCompletedType =>
          for {
            kept <- decodeWorldCard(payload("kept"), s"$path.kept")
            discarded <- traverse(payload("discardedInOrder").arr.toVector)(
              decodeWorldCard(_, s"$path.discardedInOrder"))
            placement <- decodeSearchPlacement(payload("placement"), s"$path.placement")
          } yield SearchCompleted(
            PlayerId(payload("playerId").str),
            DecisionId(payload("decisionId").str), kept, discarded, placement)
        case RestStartedType =>
          Right(RestStarted(PlayerId(payload("playerId").str)))
        case RestCompletedType =>
          val favorObject = payload("returnedFavor").obj
          val favor = favorObject.toVector.map { case (key, value) =>
            Suit.all.find(_.key == key).toRight(InvalidValue(
              s"$path.returnedFavor.$key", "unknown suit")).flatMap { suit =>
              safeInt(value, s"$path.returnedFavor.$key").map(suit -> _)
            }
          }
          for {
            entries <- favor.foldLeft[Either[WireError, Vector[(Suit, Int)]]](
              Right(Vector.empty)) {
              case (Right(acc), Right(entry)) => Right(acc :+ entry)
              case (Left(error), _) => Left(error)
              case (_, Left(error)) => Left(error)
            }
            returnedSecrets <- safeIntField(payload.obj, "returnedSecrets", path)
            refreshedSupply <- safeIntField(payload.obj, "refreshedSupply", path)
            nextRound <- safeIntField(payload.obj, "nextRound", path)
          } yield RestCompleted(
            PlayerId(payload("playerId").str), entries.toMap,
            returnedSecrets, refreshedSupply,
            PlayerId(payload("nextPlayerId").str), nextRound)
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
      if (eventType == MusteredType || eventType == TradedType)
        EconomyFormatVersion
      else if (eventType == RestStartedType || eventType == RestCompletedType)
        RestFormatVersion
      else if (eventType == SearchStartedType || eventType == SearchCompletedType)
        SearchFormatVersion
      else if (eventType == TakeWealthType || eventType == WakeEndedType ||
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
      event: OathEvent,
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

  private def safeIntField(obj: ujson.Obj, name: String, path: String)
      : Either[WireError, Int] =
    requiredField(obj, name, path).flatMap(value =>
      safeInt(value, s"$path.$name"))

  private def safeInt(value: ujson.Value, path: String)
      : Either[WireError, Int] =
    safeInteger(value, path).flatMap { number =>
      if (number <= Int.MaxValue.toLong) Right(number.toInt)
      else Left(InvalidValue(path,
        s"must be between 0 and ${Int.MaxValue} inclusive"))
    }

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

  private def encodeWorldCard(id: WorldCardId): ujson.Value = id match {
    case value: DenizenId => ujson.Obj("kind" -> "denizen", "id" -> value.value)
    case value: VisionId => ujson.Obj("kind" -> "vision", "id" -> value.value)
  }

  private def decodeWorldCard(value: ujson.Value, path: String)
      : Either[WireError, WorldCardId] = try value("kind").str match {
    case "denizen" => Right(DenizenId(value("id").str))
    case "vision" => Right(VisionId(value("id").str))
    case other => Left(InvalidValue(s"$path.kind", s"unknown world card kind '$other'"))
  } catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid world card"))) }

  private def encodeSearchSource(source: SearchSource): ujson.Value = source match {
    case SearchSource.WorldDeck => ujson.Obj("kind" -> "world")
    case SearchSource.RegionalDiscard(region) =>
      ujson.Obj("kind" -> "regional-discard", "region" -> region.key)
  }

  private def decodeSearchSource(value: ujson.Value, path: String)
      : Either[WireError, SearchSource] = try value("kind").str match {
    case "world" => Right(SearchSource.WorldDeck)
    case "regional-discard" => decodeRegion(value("region").str, s"$path.region")
      .map(SearchSource.RegionalDiscard)
    case other => Left(InvalidValue(s"$path.kind", s"unknown Search source '$other'"))
  } catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid Search source"))) }

  private def decodeRegion(value: String, path: String): Either[WireError, Region] =
    Region.all.find(_.key == value).toRight(InvalidValue(path, s"unknown region '$value'"))

  private def decodeSuit(value: String, path: String): Either[WireError, Suit] =
    Suit.all.find(_.key == value).toRight(InvalidValue(path, s"unknown suit '$value'"))

  private def encodeCardRef(id: CardId): ujson.Value = id match {
    case value: DenizenId => encodeWorldCard(value)
    case value: VisionId => encodeWorldCard(value)
    case value: EdificeId => ujson.Obj("kind" -> "edifice", "id" -> value.value)
    case value: RelicId => ujson.Obj("kind" -> "relic", "id" -> value.value)
    case value: LegacyId => ujson.Obj("kind" -> "legacy", "id" -> value.value)
  }

  private def decodeCardRef(value: ujson.Value, path: String): Either[WireError, CardId] =
    try value("kind").str match {
      case "denizen" => Right(DenizenId(value("id").str))
      case "vision" => Right(VisionId(value("id").str))
      case "edifice" => Right(EdificeId(value("id").str))
      case "relic" => Right(RelicId(value("id").str))
      case "legacy" => Right(LegacyId(value("id").str))
      case other => Left(InvalidValue(s"$path.kind", s"unknown card kind '$other'"))
    } catch { case NonFatal(error) => Left(InvalidValue(path,
      Option(error.getMessage).getOrElse("invalid card reference"))) }

  private def decodeEconomyTarget(value: ujson.Value, path: String)
      : Either[WireError, EconomyTargetRef] =
    decodeCardRef(value, path).flatMap { id =>
      EconomyTargetRef.fromCard(id).toRight(InvalidValue(path,
        "Economy target must be a denizen or edifice"))
    }

  private def encodeSearchPlacement(value: SearchPlacement): ujson.Value = value match {
    case SearchPlacement.Discard => ujson.Obj("kind" -> "discard")
    case SearchPlacement.Site(replace) => ujson.Obj(
      "kind" -> "site", "replace" -> replace.fold[ujson.Value](ujson.Null)(encodeCardRef))
    case SearchPlacement.Adviser(orientation, replace) => ujson.Obj(
      "kind" -> "adviser",
      "orientation" -> (if (orientation == Orientation.FaceUp) "face-up" else "face-down"),
      "replace" -> replace.fold[ujson.Value](ujson.Null)(encodeCardRef))
  }

  private def decodeSearchPlacement(value: ujson.Value, path: String)
      : Either[WireError, SearchPlacement] = {
    def replacement: Either[WireError, Option[CardId]] = value("replace") match {
      case ujson.Null => Right(None)
      case card => decodeCardRef(card, s"$path.replace").map(Some(_))
    }
    try value("kind").str match {
      case "discard" => Right(SearchPlacement.Discard)
      case "site" => replacement.map(SearchPlacement.Site)
      case "adviser" => for {
        orientation <- value("orientation").str match {
          case "face-up" => Right(Orientation.FaceUp)
          case "face-down" => Right(Orientation.FaceDown)
          case other => Left(InvalidValue(s"$path.orientation", s"unknown orientation '$other'"))
        }
        replace <- replacement
      } yield SearchPlacement.Adviser(orientation, replace)
      case other => Left(InvalidValue(s"$path.kind", s"unknown placement '$other'"))
    } catch { case NonFatal(error) => Left(InvalidValue(path,
      Option(error.getMessage).getOrElse("invalid Search placement"))) }
  }

  private def traverse[A, B](
      values: Vector[A]
  )(f: A => Either[WireError, B]): Either[WireError, Vector[B]] =
    values.foldLeft[Either[WireError, Vector[B]]](Right(Vector.empty)) {
      case (Right(acc), value) => f(value).map(acc :+ _)
      case (failure @ Left(_), _) => failure
    }
}
