package oathdigital.protocol

import scala.util.control.NonFatal
import ProtocolDecodeFailure._

object FirstGameBootstrapCodec {
  private val MaxSafeInteger = 9007199254740991d

  def encode(request: FirstGameBootstrapRequest): String = ujson.write(ujson.Obj(
    "expectedNextSequence" -> ujson.Num(request.expectedNextSequence.toDouble),
    "participants" -> ujson.Arr.from(request.participants.map(participant => ujson.Obj(
      "playerId" -> participant.playerId, "lineageId" -> participant.lineageId,
      "color" -> participant.color))),
    "firstPlayer" -> request.firstPlayer))

  def decode(json: String): Either[ProtocolDecodeFailure, FirstGameBootstrapRequest] =
    try decodeValue(ujson.read(json))
    catch { case NonFatal(error) => Left(MalformedJson("$",
      Option(error.getMessage).getOrElse("malformed JSON"))) }

  private def decodeValue(raw: ujson.Value)
      : Either[ProtocolDecodeFailure, FirstGameBootstrapRequest] = raw match {
    case root: ujson.Obj => for {
      _ <- exact(root, Set("expectedNextSequence", "participants", "firstPlayer"), "$")
      sequenceRaw <- field(root, "expectedNextSequence", "$")
      sequence <- sequenceRaw match {
        case ujson.Num(number) if number.isWhole && number >= 0 &&
            number <= MaxSafeInteger => Right(number.toLong)
        case _ => Left(InvalidValue("$.expectedNextSequence",
          "expected a non-negative safe integer"))
      }
      participantsRaw <- field(root, "participants", "$")
      participantValues <- participantsRaw match {
        case array: ujson.Arr => Right(array.value.toVector)
        case _ => Left(InvalidValue("$.participants", "expected array"))
      }
      participants <- participantValues.zipWithIndex.foldLeft[
        Either[ProtocolDecodeFailure, Vector[BootstrapParticipantRequest]]](
          Right(Vector.empty)) {
        case (Right(done), (value: ujson.Obj, index)) =>
          val path = s"$$.participants[$index]"
          (for {
            _ <- exact(value, Set("playerId", "lineageId", "color"), path)
            player <- text(value, "playerId", path)
            lineage <- text(value, "lineageId", path)
            color <- text(value, "color", path)
          } yield done :+ BootstrapParticipantRequest(player, lineage, color))
        case (Right(_), (_, index)) => Left(ExpectedObject(s"$$.participants[$index]"))
        case (failure @ Left(_), _) => failure
      }
      first <- text(root, "firstPlayer", "$")
    } yield FirstGameBootstrapRequest(sequence, participants, first)
    case _ => Left(ExpectedObject("$"))
  }

  private def exact(value: ujson.Obj, fields: Set[String], path: String) =
    value.value.keys.find(!fields.contains(_))
      .map(key => Left(UnexpectedField(s"$path.$key"))).getOrElse(Right(()))
  private def field(value: ujson.Obj, name: String, path: String) =
    value.value.get(name).toRight(MissingField(s"$path.$name"))
  private def text(value: ujson.Obj, name: String, path: String) =
    field(value, name, path).flatMap {
      case ujson.Str(text) if text.trim.nonEmpty => Right(text)
      case ujson.Str(_) => Left(InvalidValue(s"$path.$name", "must not be blank"))
      case _ => Left(InvalidValue(s"$path.$name", "expected string"))
    }
}
