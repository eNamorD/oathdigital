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
    intent: GameIntent
)

final case class BootstrapParticipantRequest(
    playerId: String,
    lineageId: String,
    color: String
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

  def encode(request: ActorlessCommandRequest): String = ujson.write(ujson.Obj(
    "expectedNextSequence" -> ujson.Num(request.expectedNextSequence.toDouble),
    "intent" -> CommandIntentCodec.encode(request.intent)))

  def decode(json: String): Either[ProtocolDecodeFailure, ActorlessCommandRequest] =
    try decodeValue(ujson.read(json))
    catch { case NonFatal(error) => Left(MalformedJson("$",
      Option(error.getMessage).getOrElse("malformed JSON"))) }

  def decodeValue(value: ujson.Value)
      : Either[ProtocolDecodeFailure, ActorlessCommandRequest] = value match {
    case root: ujson.Obj => for {
      _ <- exact(root, Set("expectedNextSequence", "intent"), "$")
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
    } yield ActorlessCommandRequest(sequence, intent)
    case _ => Left(ExpectedObject("$"))
  }

  private def exact(obj: ujson.Obj, expected: Set[String], path: String)
      : Either[ProtocolDecodeFailure, Unit] =
    obj.value.keys.find(key => !expected.contains(key))
      .map(key => Left(UnexpectedField(s"$path.$key"))).getOrElse(Right(()))
}
