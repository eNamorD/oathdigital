package oathdigital.serialization

import oathdigital.model._
import oathdigital.model.OathEvent.{CommandAccepted, StepTakenBack}

/** Undo's journal facts (undo design, "Storage"). */
private[serialization] trait UndoEventCodec { this: GameEventJsonSupport =>
  import GameEventWire._

  protected final val undoDiscriminator: PartialFunction[OathEvent, String] =
      case _: CommandAccepted => CommandAcceptedType
      case _: StepTakenBack => StepTakenBackType

  protected final val undoEncoder: PartialFunction[OathEvent, ujson.Value] =
      case CommandAccepted(by) => ujson.Obj("by" -> by.value)
      case StepTakenBack(from, until, by) => ujson.Obj(
        "from" -> ujson.Num(from.toDouble),
        "until" -> ujson.Num(until.toDouble),
        "by" -> by.value)

  protected final def undoDecode(eventType: String, payload: ujson.Value,
      path: String): Option[Either[WireError, OathEvent]] =
    val decoder: PartialFunction[String, Either[WireError, OathEvent]] =
        case CommandAcceptedType =>
          Right(CommandAccepted(PlayerId(payload("by").str)))
        case StepTakenBackType => for
          from <- safeIntegerField(payload.obj, "from", path)
          until <- safeIntegerField(payload.obj, "until", path)
        yield StepTakenBack(from, until, PlayerId(payload("by").str))
    decoder.lift(eventType)
}
