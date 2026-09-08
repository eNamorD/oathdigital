package oathdigital.serialization

import scala.util.control.NonFatal

import oathdigital.gameplay.OathEvent
import oathdigital.gameplay.operations.{AdjustSupply, CoreOperation, Location,
  ModifyDicePool, Move, Piece, PositionedLocation, StackPosition}
import oathdigital.gameplay.walker.{ChoicePayload, RollPayload, WalkerCompleted,
  DeltaMeaning, WalkerParked, WalkerStepPayload, WalkerStepRecorded}
import oathdigital.gameplay.walker.DeltaMeaning.{DicePoolModified,
  OperationApplied, RelicAcquired, SupplySpent}
import oathdigital.model.DecisionPayload.{RecoverChoice,
  RecoverChoicePayload, RecoverRelicPayload}
import oathdigital.model._

/** Wire vocabulary for generic walker journal facts. Operation encoding is
  * intentionally bounded to Recover's recorded leaf set in this slice.
  */
private[serialization] trait WalkerEventCodec {
    this: GameEventJsonSupport =>
  import GameEventWire._
  import WireError._

  protected final val walkerDiscriminator: PartialFunction[OathEvent, String] = {
    case _: WalkerStepRecorded => WalkerStepRecordedType
    case _: WalkerParked => WalkerParkedType
    case _: WalkerCompleted => WalkerCompletedType
  }

  protected final val walkerEncoder: PartialFunction[OathEvent, ujson.Value] = {
    case WalkerStepRecorded(actor, nodeId, payload, ops, contributions) =>
      ujson.Obj(
        "actorPlayerId" -> actor.value,
        "nodeId" -> nodeId,
        "step" -> encodeStepPayload(payload),
        "ops" -> ujson.Arr.from(ops.map(encodeOperation)),
        "contributions" -> stringArray(contributions.map(_.value)))
    case WalkerParked(actor, action, at, answered) => ujson.Obj(
      "actorPlayerId" -> actor.value,
      "action" -> action.key,
      "at" -> stringArray(at),
      "answered" -> ujson.Arr.from(answered.map(encodeAnswered)))
    case WalkerCompleted(actor, action) => ujson.Obj(
      "actorPlayerId" -> actor.value,
      "action" -> action.key)
  }

  protected final def walkerDecode(eventType: String, payload: ujson.Value,
      path: String, envelopeCatalog: CatalogRef)
      : Option[Either[WireError, OathEvent]] = {
    val decoder: PartialFunction[String, Either[WireError, OathEvent]] = {
      case WalkerStepRecordedType => decodeStepRecorded(payload, path)
      case WalkerParkedType => decodeParked(payload, path)
      case WalkerCompletedType => for {
        action <- decodeAction(payload("action").str, s"$path.action")
      } yield WalkerCompleted(PlayerId(payload("actorPlayerId").str), action)
    }
    decoder.lift(eventType)
  }

  private def encodeAnswered(answered: Answered): ujson.Value = ujson.Obj(
    "decisionId" -> answered.decisionId,
    "payload" -> encodeDecisionPayload(answered.payload))

  private def decodeAnswered(value: ujson.Value,
      path: String): Either[WireError, Answered] = for {
    payload <- decodeDecisionPayload(value("payload"), s"$path.payload")
  } yield Answered(value("decisionId").str, payload)

  private def encodeStepPayload(payload: WalkerStepPayload): ujson.Value =
    payload match {
      case WalkerStepPayload.DeltaRecorded(meaning) =>
        ujson.Obj("kind" -> "delta", "meaning" -> encodeDeltaMeaning(meaning))
      case ChoicePayload(decisionId, answer) => ujson.Obj(
        "kind" -> "choice", "decisionId" -> decisionId,
        "payload" -> encodeDecisionPayload(answer))
      case RollPayload(pool, faces) => ujson.Obj(
        "kind" -> "roll", "pool" -> pool.value,
        "faces" -> ujson.Arr.from(faces.map {
          case face: DefenseDieFace => ujson.Str(encodeDefenseFace(face))
          case other => throw new IllegalArgumentException(
            s"unsupported walker die face $other")
        }))
      case other => throw new IllegalArgumentException(
        s"unsupported walker step payload $other")
    }

  private def decodeStepPayload(value: ujson.Value,
      path: String): Either[WireError, WalkerStepPayload] =
    value("kind").str match {
      case "delta" => decodeDeltaMeaning(value("meaning"), s"$path.meaning")
        .map(WalkerStepPayload.DeltaRecorded)
      case "choice" => decodeDecisionPayload(value("payload"), s"$path.payload")
        .map(ChoicePayload(value("decisionId").str, _))
      case "roll" => traverse(value("faces").arr.toVector)(face =>
        decodeDefenseFace(face.str, s"$path.faces"))
        .map(faces => RollPayload(PoolKey(value("pool").str), faces))
      case other => Left(InvalidValue(s"$path.kind",
        s"unknown walker step payload '$other'"))
    }

  private def encodeDeltaMeaning(meaning: DeltaMeaning): ujson.Value =
    meaning match {
      case DicePoolModified(pool, delta) => ujson.Obj(
        "kind" -> "dice-pool-modified", "pool" -> pool.value,
        "delta" -> delta)
      case SupplySpent(player, amount) => ujson.Obj(
        "kind" -> "supply-spent", "playerId" -> player.value,
        "amount" -> amount)
      case RelicAcquired(player, relic, site) => ujson.Obj(
        "kind" -> "relic-acquired", "playerId" -> player.value,
        "relicId" -> relic.value, "siteId" -> site.value)
      case OperationApplied(label) => ujson.Obj(
        "kind" -> "operation-applied", "label" -> label)
    }

  private def decodeDeltaMeaning(value: ujson.Value,
      path: String): Either[WireError, DeltaMeaning] = value("kind").str match {
    case "dice-pool-modified" =>
      decodeSignedInt(value("delta"), s"$path.delta").map(delta =>
        DicePoolModified(PoolKey(value("pool").str), delta))
    case "supply-spent" =>
      decodeSignedInt(value("amount"), s"$path.amount").flatMap { amount =>
        if (amount > 0) Right(SupplySpent(
          PlayerId(value("playerId").str), amount))
        else Left(InvalidValue(s"$path.amount", "must be positive"))
      }
    case "relic-acquired" => Right(RelicAcquired(
      PlayerId(value("playerId").str), RelicId(value("relicId").str),
      SiteId(value("siteId").str)))
    case "operation-applied" =>
      Right(OperationApplied(value("label").str))
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown walker delta meaning '$other'"))
  }

  private def encodeDecisionPayload(payload: DecisionPayload): ujson.Value =
    payload match {
      case RecoverChoicePayload(choice) => ujson.Obj(
        "kind" -> "recover-choice",
        "choice" -> (choice match {
          case RecoverChoice.Continue => "continue"
          case RecoverChoice.Stop => "stop"
        }))
      case RecoverRelicPayload(relic) => ujson.Obj(
        "kind" -> "recover-relic", "relicId" -> relic.value)
      case other => throw new IllegalArgumentException(
        s"unsupported walker decision payload $other")
    }

  private def decodeDecisionPayload(value: ujson.Value,
      path: String): Either[WireError, DecisionPayload] =
    value("kind").str match {
      case "recover-choice" => value("choice").str match {
        case "continue" => Right(RecoverChoicePayload(RecoverChoice.Continue))
        case "stop" => Right(RecoverChoicePayload(RecoverChoice.Stop))
        case other => Left(InvalidValue(s"$path.choice",
          s"unknown Recover choice '$other'"))
      }
      case "recover-relic" =>
        Right(RecoverRelicPayload(RelicId(value("relicId").str)))
      case other => Left(InvalidValue(s"$path.kind",
        s"unknown walker decision payload '$other'"))
    }

  private def encodeOperation(operation: CoreOperation): ujson.Value =
    operation match {
      case AdjustSupply(player, amount) => ujson.Obj(
        "kind" -> "adjust-supply", "playerId" -> player.value,
        "amount" -> amount)
      case ModifyDicePool(pool, delta, _) => ujson.Obj(
        "kind" -> "modify-dice-pool", "pool" -> pool.value,
        "delta" -> delta)
      case Move(piece, from, to, orientation) => ujson.Obj(
        "kind" -> "move",
        "piece" -> encodePiece(piece),
        "from" -> encodePositionedLocation(from),
        "to" -> encodePositionedLocation(to),
        "resultingOrientation" -> orientation.fold[ujson.Value](ujson.Null)(
          value => ujson.Str(encodeOrientation(value))))
      case other => throw new IllegalArgumentException(
        s"unsupported recorded walker operation $other")
    }

  private def decodeOperation(value: ujson.Value,
      path: String): Either[WireError, CoreOperation] =
    value("kind").str match {
      case "adjust-supply" => decodeSignedInt(value("amount"), s"$path.amount")
        .map(amount => AdjustSupply(PlayerId(value("playerId").str), amount))
      case "modify-dice-pool" =>
        decodeSignedInt(value("delta"), s"$path.delta")
          .map(delta => ModifyDicePool(PoolKey(value("pool").str), delta))
      case "move" => for {
        piece <- decodePiece(value("piece"), s"$path.piece")
        from <- decodePositionedLocation(value("from"), s"$path.from")
        to <- decodePositionedLocation(value("to"), s"$path.to")
        orientation <- value("resultingOrientation") match {
          case ujson.Null => Right(None)
          case other => decodeOrientation(other.str,
            s"$path.resultingOrientation").map(Some(_))
        }
      } yield Move(piece, from, to, orientation)
      case other => Left(InvalidValue(s"$path.kind",
        s"unknown recorded walker operation '$other'"))
    }

  private def encodePiece(piece: Piece): ujson.Value = piece match {
    case Piece.Card(id) => ujson.Obj("kind" -> "card",
      "card" -> encodeCardRef(id))
    case other => throw new IllegalArgumentException(
      s"unsupported recorded walker piece $other")
  }

  private def decodePiece(value: ujson.Value,
      path: String): Either[WireError, Piece] = value("kind").str match {
    case "card" => decodeCardRef(value("card"), s"$path.card").map(Piece.Card)
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown recorded walker piece '$other'"))
  }

  private def encodePositionedLocation(value: PositionedLocation): ujson.Value =
    ujson.Obj("location" -> encodeLocation(value.location),
      "position" -> (value.position match {
        case StackPosition.Unspecified => "unspecified"
        case StackPosition.Top => "top"
        case StackPosition.Bottom => "bottom"
      }))

  private def decodePositionedLocation(value: ujson.Value,
      path: String): Either[WireError, PositionedLocation] = for {
    location <- decodeLocation(value("location"), s"$path.location")
    position <- value("position").str match {
      case "unspecified" => Right(StackPosition.Unspecified)
      case "top" => Right(StackPosition.Top)
      case "bottom" => Right(StackPosition.Bottom)
      case other => Left(InvalidValue(s"$path.position",
        s"unknown stack position '$other'"))
    }
  } yield PositionedLocation(location, position)

  private def encodeLocation(value: Location): ujson.Value = value match {
    case Location.Site(site) => ujson.Obj("kind" -> "site",
      "siteId" -> site.value)
    case Location.PlayArea(player) => ujson.Obj("kind" -> "play-area",
      "playerId" -> player.value)
    case other => throw new IllegalArgumentException(
      s"unsupported recorded walker location $other")
  }

  private def decodeLocation(value: ujson.Value,
      path: String): Either[WireError, Location] = value("kind").str match {
    case "site" => Right(Location.Site(SiteId(value("siteId").str)))
    case "play-area" =>
      Right(Location.PlayArea(PlayerId(value("playerId").str)))
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown recorded walker location '$other'"))
  }

  private def encodeOrientation(value: Orientation): String = value match {
    case Orientation.FaceUp => "face-up"
    case Orientation.FaceDown => "face-down"
  }

  private def decodeOrientation(value: String,
      path: String): Either[WireError, Orientation] = value match {
    case "face-up" => Right(Orientation.FaceUp)
    case "face-down" => Right(Orientation.FaceDown)
    case other => Left(InvalidValue(path, s"unknown orientation '$other'"))
  }

  private def decodeAction(value: String,
      path: String): Either[WireError, ActionRef] =
    ActionRef.fromKey(value).toRight(InvalidValue(path,
      s"unknown walker action '$value'"))

  private def decodeSignedInt(value: ujson.Value,
      path: String): Either[WireError, Int] = value match {
    case ujson.Num(number) if number.isFinite && number == math.rint(number) &&
        number >= Int.MinValue && number <= Int.MaxValue => Right(number.toInt)
    case _ => Left(InvalidValue(path, "expected a signed 32-bit integer"))
  }

  private def decodeStepRecorded(value: ujson.Value,
      path: String): Either[WireError, OathEvent] = try for {
    step <- decodeStepPayload(value("step"), s"$path.step")
    ops <- traverse(value("ops").arr.zipWithIndex.toVector) {
      case (operation, index) => decodeOperation(operation, s"$path.ops[$index]")
    }
    contributions = value("contributions").arr.toVector.map(id =>
      PowerId(id.str))
  } yield WalkerStepRecorded(PlayerId(value("actorPlayerId").str),
    value("nodeId").str, step, ops, contributions)
  catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid walker step"))) }

  private def decodeParked(value: ujson.Value,
      path: String): Either[WireError, OathEvent] = try for {
    action <- decodeAction(value("action").str, s"$path.action")
    answered <- traverse(value("answered").arr.zipWithIndex.toVector) {
      case (answer, index) => decodeAnswered(answer,
        s"$path.answered[$index]")
    }
  } yield WalkerParked(PlayerId(value("actorPlayerId").str), action,
    value("at").arr.toVector.map(_.str), answered)
  catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid walker park"))) }
}
