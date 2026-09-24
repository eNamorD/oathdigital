package oathdigital.protocol

import scala.util.control.NonFatal

sealed trait ProtocolDecodeFailure extends Product with Serializable {
  def path: String
  def message: String
}
object ProtocolDecodeFailure {
  final case class MalformedJson(path: String, message: String)
      extends ProtocolDecodeFailure
  final case class ExpectedObject(path: String, message: String = "expected object")
      extends ProtocolDecodeFailure
  final case class MissingField(path: String, message: String = "missing required field")
      extends ProtocolDecodeFailure
  final case class UnexpectedField(path: String, message: String = "unexpected field")
      extends ProtocolDecodeFailure
  final case class InvalidValue(path: String, message: String)
      extends ProtocolDecodeFailure
  final case class ActorInjection(path: String,
      message: String = "actor identity is bound by the server")
      extends ProtocolDecodeFailure
}

final case class ActorlessCommandRequest(
    expectedNextSequence: Long,
    intent: GameIntent,
    orderedModifiers: Vector[ModifierInvocation] = Vector.empty
)

final case class ModifierInvocation(
    sourceKind: String,
    sourceId: String,
    contextId: Option[String],
    handlerId: String
)

final case class BootstrapParticipantRequest(
    playerId: String,
    lineageId: String,
    color: oathdigital.model.PlayerColor
)
final case class FirstGameBootstrapRequest(
    expectedNextSequence: Long,
    participants: Vector[BootstrapParticipantRequest],
    firstPlayer: String
)

/** Exact shared envelope codec. Intent-family codecs own their exact payload fields. */
object ActorlessCommandCodec {
  import ProtocolDecodeFailure._

  private val MaxSafeInteger = 9007199254740991d

  def encode(request: ActorlessCommandRequest): String = {
    val value = ujson.Obj(
      "expectedNextSequence" -> ujson.Num(request.expectedNextSequence.toDouble),
      "intent" -> CommandIntentCodec.encode(request.intent))
    if (request.orderedModifiers.nonEmpty) value("orderedModifiers") = ujson.Arr.from(
      request.orderedModifiers.map(v => ujson.Obj(
        "sourceKind" -> v.sourceKind, "sourceId" -> v.sourceId,
        "contextId" -> v.contextId.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
        "handlerId" -> v.handlerId)))
    ujson.write(value)
  }

  def decode(json: String): Either[ProtocolDecodeFailure, ActorlessCommandRequest] =
    try decodeValue(ujson.read(json))
    catch { case NonFatal(error) => Left(MalformedJson("$",
      Option(error.getMessage).getOrElse("malformed JSON"))) }

  def decodeValue(value: ujson.Value)
      : Either[ProtocolDecodeFailure, ActorlessCommandRequest] = value match {
    case root: ujson.Obj => for {
      _ <- exact(root, Set("expectedNextSequence", "intent", "orderedModifiers"), "$")
      sequenceValue <- root.value.get("expectedNextSequence")
        .toRight(MissingField("$.expectedNextSequence"))
      sequence <- sequenceValue match {
        case ujson.Num(number) if number.isWhole && number >= 0 &&
            number <= MaxSafeInteger => Right(number.toLong)
        case _ => Left(InvalidValue("$.expectedNextSequence",
          "expected a non-negative safe integer"))
      }
      intentValue <- root.value.get("intent").toRight(MissingField("$.intent"))
      intent <- CommandIntentCodec.decode(intentValue, "$.intent")
      modifiers <- root.value.get("orderedModifiers") match {
        case None => Right(Vector.empty)
        case Some(value: ujson.Arr) => value.value.toVector.zipWithIndex.foldLeft[
          Either[ProtocolDecodeFailure, Vector[ModifierInvocation]]](Right(Vector.empty)) {
          case (Right(acc), (obj: ujson.Obj, index)) => for {
            _ <- exact(obj, Set("sourceKind", "sourceId", "contextId", "handlerId"),
              s"$$.orderedModifiers[$index]")
            kind <- requiredString(obj, "sourceKind", index)
            id <- requiredString(obj, "sourceId", index)
            context <- obj.value.get("contextId") match {
              case Some(ujson.Str(v)) if v.nonEmpty => Right(Some(v))
              case Some(ujson.Null) => Right(None)
              case _ => Left(InvalidValue(s"$$.orderedModifiers[$index].contextId",
                "expected non-empty string or null"))
            }
            handler <- requiredString(obj, "handlerId", index)
          } yield acc :+ ModifierInvocation(kind, id, context, handler)
          case (Right(_), (_, index)) => Left(ExpectedObject(s"$$.orderedModifiers[$index]"))
          case (left @ Left(_), _) => left
        }
        case Some(_) => Left(InvalidValue("$.orderedModifiers", "expected array"))
      }
      _ <- if (modifiers.distinct.size == modifiers.size) Right(()) else
        Left(InvalidValue("$.orderedModifiers", "duplicate modifier invocation"))
    } yield ActorlessCommandRequest(sequence, intent, modifiers)
    case _ => Left(ExpectedObject("$"))
  }

  private def exact(obj: ujson.Obj, expected: Set[String], path: String)
      : Either[ProtocolDecodeFailure, Unit] =
    obj.value.keys.find(key => !expected.contains(key))
      .map(key => Left(UnexpectedField(s"$path.$key"))).getOrElse(Right(()))

  private def requiredString(obj: ujson.Obj, key: String, index: Int) =
    obj.value.get(key) match {
      case Some(ujson.Str(value)) if value.nonEmpty => Right(value)
      case _ => Left(InvalidValue(s"$$.orderedModifiers[$index].$key",
        "expected non-empty string"))
    }
}
