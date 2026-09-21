package oathdigital.protocol

import scala.util.control.NonFatal
import ProtocolDecodeFailure._

private[protocol] object TrustedGameCodecFields {
  def parse[A](json: String)(decode: ujson.Value => Either[ProtocolDecodeFailure, A])
      : Either[ProtocolDecodeFailure, A] =
    try decode(ujson.read(json))
    catch { case NonFatal(_) => Left(MalformedJson("$", "malformed JSON")) }

  def exact(raw: ujson.Value, keys: Vector[String], path: String)
      : Either[ProtocolDecodeFailure, ujson.Obj] = raw match {
    case obj: ujson.Obj =>
      obj.value.keys.find(!keys.contains(_)).map(key =>
        Left(UnexpectedField(s"$path.$key"))).getOrElse(
        keys.find(!obj.value.contains(_)).map(key =>
          Left(MissingField(s"$path.$key"))).getOrElse(Right(obj)))
    case _ => Left(ExpectedObject(path))
  }

  def text(obj: ujson.Obj, key: String, path: String)
      : Either[ProtocolDecodeFailure, String] = obj(key) match {
    case ujson.Str(value) if value.trim.nonEmpty => Right(value)
    case _ => Left(InvalidValue(s"$path.$key", "expected a non-blank string"))
  }

  def identifier(obj: ujson.Obj, key: String, path: String)
      : Either[ProtocolDecodeFailure, String] = text(obj, key, path).flatMap { value =>
    Either.cond(value.length <= 128 && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*"),
      value, InvalidValue(s"$path.$key", "invalid identifier"))
  }

  def array[A](raw: ujson.Value, path: String)(
      decode: (ujson.Value, String) => Either[ProtocolDecodeFailure, A])
      : Either[ProtocolDecodeFailure, Vector[A]] = raw match {
    case values: ujson.Arr => values.value.zipWithIndex.foldLeft[
        Either[ProtocolDecodeFailure, Vector[A]]](Right(Vector.empty)) {
      case (done, (value, index)) => for {
        acc <- done
        next <- decode(value, s"$path[$index]")
      } yield acc :+ next
    }
    case _ => Left(InvalidValue(path, "expected array"))
  }
}

object TrustedGameCreateRequestCodec {
  import TrustedGameCodecFields._

  def encode(request: TrustedGameCreateRequest): String = ujson.write(ujson.Obj(
    "gameId" -> request.gameId,
    "participants" -> ujson.Arr.from(request.participants.map(p => ujson.Obj(
      "playerId" -> p.playerId, "lineageId" -> p.lineageId, "color" -> p.color))),
    "firstPlayerId" -> request.firstPlayerId))

  def decode(json: String): Either[ProtocolDecodeFailure, TrustedGameCreateRequest] =
    parse(json) { raw => for {
      root <- exact(raw, Vector("gameId", "participants", "firstPlayerId"), "$")
      game <- identifier(root, "gameId", "$")
      participants <- array(root("participants"), "$.participants") { (raw, path) => for {
        obj <- exact(raw, Vector("playerId", "lineageId", "color"), path)
        player <- identifier(obj, "playerId", path)
        lineage <- identifier(obj, "lineageId", path)
        color <- text(obj, "color", path)
      } yield BootstrapParticipantRequest(player, lineage, color) }
      first <- identifier(root, "firstPlayerId", "$")
      _ <- Either.cond(participants.nonEmpty &&
        participants.map(_.playerId).distinct.size == participants.size, (),
        InvalidValue("$.participants", "requires unique player IDs and at least one participant"))
      _ <- Either.cond(participants.exists(_.playerId == first), (),
        InvalidValue("$.firstPlayerId", "must identify a participant"))
    } yield TrustedGameCreateRequest(game, participants, first) }
}

object TrustedGameCreateResponseCodec {
  import TrustedGameCodecFields._

  def encode(response: TrustedGameCreateResponse): String = ujson.write(ujson.Obj(
    "gameId" -> response.gameId,
    "seats" -> ujson.Arr.from(response.seats.map(seat => ujson.Obj(
      "playerId" -> seat.playerId, "url" -> seat.url)))))

  def decode(json: String): Either[ProtocolDecodeFailure, TrustedGameCreateResponse] =
    parse(json) { raw => for {
      root <- exact(raw, Vector("gameId", "seats"), "$")
      game <- identifier(root, "gameId", "$")
      seats <- array(root("seats"), "$.seats") { (raw, path) => for {
        obj <- exact(raw, Vector("playerId", "url"), path)
        player <- identifier(obj, "playerId", path)
        url <- text(obj, "url", path)
      } yield TrustedSeatLink(player, url) }
      _ <- Either.cond(seats.nonEmpty && seats.map(_.playerId).distinct.size == seats.size,
        (), InvalidValue("$.seats", "requires unique player IDs and at least one seat"))
    } yield TrustedGameCreateResponse(game, seats) }
}
