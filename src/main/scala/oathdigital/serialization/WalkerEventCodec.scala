package oathdigital.serialization

import scala.util.control.NonFatal

import oathdigital.gameplay.OathEvent
import oathdigital.gameplay.walker.{ChoicePayload, RollPayload, WalkerCompleted,
  DeltaMeaning, WalkerParked, WalkerStepPayload, WalkerStepRecorded}
import oathdigital.gameplay.walker.DeltaMeaning.{DicePoolModified,
  OperationApplied, RelicAcquired, SupplySpent}
import oathdigital.model._

/** Wire vocabulary for generic walker journal facts.
  *
  * `encodeOperation`/`encodePiece` (I8) are total over `CoreOperation`/
  * `Piece`, exactly like [[encodeLocation]] below (added for the same
  * reason: a bounded operation set is what let Catacombs' recorded `Move`
  * out of `Location.Deck` reach an append-time throw with no test to catch
  * it -- that lesson generalises to every recorded shape, not just
  * `Location`). The one genuine exception is `encodeOperation`'s five
  * walker tree-control arms (`Decide`/`BuildOps`/`Repeat`/`Branch`/
  * `Sequence`): three close over a Scala function value with no data
  * representation at all, and none of the five can ever legally reach this
  * method, because `ProcedureWalker` only ever records an
  * ALREADY-APPLIED delta batch (a leaf's own effect, or a `BuildOps`
  * closure's *returned* `Vector[CoreOperation]`) -- never one of these
  * control nodes themselves. Those five arms throw
  * [[UnencodableOperation]] (a typed [[WireError]] carrier caught by
  * `GameEventWire.encodePayloadSafe`) instead of falling into a silent
  * wildcard `case other => throw`, so a NEW `CoreOperation` case fails to
  * compile here ("match may not be exhaustive") until it is given a real
  * arm.
  */
private[serialization] trait WalkerEventCodec extends WalkerOperationCodec {
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
    case WalkerParked(actor, action, at, answered, modifiers, startArgs) =>
      ujson.Obj(
        "actorPlayerId" -> actor.value,
        "action" -> action.key,
        "at" -> stringArray(at),
        "answered" -> ujson.Arr.from(answered.map(encodeAnswered)),
        "modifiers" -> stringArray(modifiers.map(_.value)),
        // Always written, empty for an action that selects nothing, exactly
        // as `modifiers` is. A park written before batch-1 Task 5 has no such
        // key at all, and `decodeParked` reads a missing key as empty.
        "startArgs" -> ujson.Arr.from(startArgs.map(
          DecisionAnswerCodec.encodeRef)))
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
    "payload" -> DecisionAnswerCodec.encode(answered.answer))

  private def decodeAnswered(value: ujson.Value,
      path: String): Either[WireError, Answered] = for {
    answer <- DecisionAnswerCodec.decode(value("payload"), s"$path.payload")
  } yield Answered(value("decisionId").str, answer)

  private def encodeStepPayload(payload: WalkerStepPayload): ujson.Value =
    payload match {
      case WalkerStepPayload.DeltaRecorded(meaning) =>
        ujson.Obj("kind" -> "delta", "meaning" -> encodeDeltaMeaning(meaning))
      case ChoicePayload(decisionId, answer) => ujson.Obj(
        "kind" -> "choice", "decisionId" -> decisionId,
        "payload" -> DecisionAnswerCodec.encode(answer))
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
      case "choice" => DecisionAnswerCodec.decode(value("payload"), s"$path.payload")
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


  private def decodeAction(value: String,
      path: String): Either[WireError, ActionRef] =
    ActionRef.fromKey(value).toRight(InvalidValue(path,
      s"unknown walker action '$value'"))

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
    modifiers = value("modifiers").arr.toVector.map(id => PowerId(id.str))
    startArgs <- decodeStartArgs(value, s"$path.startArgs")
  } yield WalkerParked(PlayerId(value("actorPlayerId").str), action,
    value("at").arr.toVector.map(_.str), answered, modifiers, startArgs)
  catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid walker park"))) }

  /** Start selections are `DecisionOptionRef`s and nothing else, so this
    * names no action and needs no arm per action -- a future action that
    * selects a site, a card or a player already round-trips here unchanged.
    */
  private def decodeStartArgs(value: ujson.Value, path: String)
      : Either[WireError, Vector[DecisionOptionRef]] =
    value.obj.get("startArgs") match {
      case None => Right(Vector.empty)
      case Some(args) => traverse(args.arr.zipWithIndex.toVector) {
        case (ref, index) =>
          DecisionAnswerCodec.decodeRef(ref, s"$path[$index]")
      }
    }
}
