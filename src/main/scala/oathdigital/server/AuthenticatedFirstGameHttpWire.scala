package oathdigital.server

import scala.util.control.NonFatal

import oathdigital.application.{
  BootstrapParticipant,
  FirstGameBootstrapConfig
}
import oathdigital.model.{DenizenId, LineageId, PlayerId, SiteId}
import oathdigital.serialization.FirstGameEventWire
import oathdigital.setup.{PlayerColor, WakeResource}

sealed trait FirstGameIntent extends Product with Serializable
object FirstGameIntent {
  final case class PlacePawn(siteId: SiteId) extends FirstGameIntent
  final case class ChooseAdviser(adviserId: DenizenId) extends FirstGameIntent
  final case class TakeWealth(resource: WakeResource) extends FirstGameIntent
  case object EndWake extends FirstGameIntent
}

final case class AuthenticatedCommandRequest(
    expectedNextSequence: Long,
    intent: FirstGameIntent
)

final case class AuthenticatedBootstrapRequest(
    expectedNextSequence: Long,
    config: FirstGameBootstrapConfig
)

object AuthenticatedFirstGameHttpWire {
  def decodeBootstrap(
      json: String
  ): Either[HttpInputError, AuthenticatedBootstrapRequest] =
    try {
      for {
        root <- objectValue(ujson.read(json), "$")
        _ <- exactFields(
          root,
          Set("expectedNextSequence", "participants", "firstPlayer"),
          "$"
        )
        expectedValue <- field(root, "expectedNextSequence", "$")
        expected <- safeSequence(expectedValue, "$.expectedNextSequence")
        participantsValue <- field(root, "participants", "$")
        participantValues <- arrayValue(participantsValue, "$.participants")
        participants <- traverse(participantValues.zipWithIndex) {
          case (value, index) =>
            val path = s"$$.participants[$index]"
            for {
              obj <- objectValue(value, path)
              _ <- exactFields(
                obj,
                Set("playerId", "lineageId", "color"),
                path
              )
              playerId <- stringField(obj, "playerId", path)
              lineageId <- stringField(obj, "lineageId", path)
              color <- stringField(obj, "color", path)
            } yield BootstrapParticipant(
              PlayerId(playerId),
              LineageId(lineageId),
              PlayerColor(color)
            )
        }
        firstPlayer <- stringField(root, "firstPlayer", "$")
      } yield AuthenticatedBootstrapRequest(
        expected,
        FirstGameBootstrapConfig(participants, PlayerId(firstPlayer))
      )
    } catch {
      case NonFatal(error) => Left(HttpInputError(
        "$",
        Option(error.getMessage).getOrElse("malformed JSON")
      ))
    }

  def decodeCommand(
      json: String
  ): Either[HttpInputError, AuthenticatedCommandRequest] =
    try {
      for {
        root <- objectValue(ujson.read(json), "$")
        _ <- exactFields(root, Set("expectedNextSequence", "intent"), "$")
        expectedValue <- field(root, "expectedNextSequence", "$")
        expected <- safeSequence(expectedValue, "$.expectedNextSequence")
        intentValue <- field(root, "intent", "$")
        intentObject <- objectValue(intentValue, "$.intent")
        intent <- decodeIntent(intentObject)
      } yield AuthenticatedCommandRequest(expected, intent)
    } catch {
      case NonFatal(error) => Left(HttpInputError(
        "$",
        Option(error.getMessage).getOrElse("malformed JSON")
      ))
    }

  private def decodeIntent(
      obj: ujson.Obj
  ): Either[HttpInputError, FirstGameIntent] =
    stringField(obj, "type", "$.intent").flatMap {
      case "placePawn" =>
        exactFields(obj, Set("type", "siteId"), "$.intent")
          .flatMap(_ => stringField(obj, "siteId", "$.intent"))
          .map(value => FirstGameIntent.PlacePawn(SiteId(value)))
      case "chooseAdviser" =>
        exactFields(obj, Set("type", "adviserId"), "$.intent")
          .flatMap(_ => stringField(obj, "adviserId", "$.intent"))
          .map(value => FirstGameIntent.ChooseAdviser(DenizenId(value)))
      case "takeWealth" =>
        exactFields(obj, Set("type", "resource"), "$.intent")
          .flatMap(_ => stringField(obj, "resource", "$.intent"))
          .flatMap {
            case "favor" => Right(FirstGameIntent.TakeWealth(WakeResource.Favor))
            case "secret" => Right(FirstGameIntent.TakeWealth(WakeResource.Secret))
            case other => Left(HttpInputError(
              "$.intent.resource",
              s"unknown wealth resource '$other'"
            ))
          }
      case "endWake" =>
        exactFields(obj, Set("type"), "$.intent").map(_ =>
          FirstGameIntent.EndWake)
      case other => Left(HttpInputError(
        "$.intent.type",
        s"unknown intent type '$other'"
      ))
    }

  private def exactFields(
      obj: ujson.Obj,
      allowed: Set[String],
      path: String
  ): Either[HttpInputError, Unit] =
    obj.value.keys.find(key => !allowed.contains(key)) match {
      case Some(key) => Left(HttpInputError(
        s"$path.$key",
        "field is not accepted"
      ))
      case None => Right(())
    }

  private def safeSequence(value: ujson.Value, path: String) = value match {
    case ujson.Num(number)
        if number.isFinite && number == Math.rint(number) && number >= 0 &&
          number <= FirstGameEventWire.MaxSafeSequence => Right(number.toLong)
    case _: ujson.Num => Left(HttpInputError(path, "expected a safe non-negative integer"))
    case _ => Left(HttpInputError(path, "expected a number"))
  }

  private def stringField(obj: ujson.Obj, name: String, path: String) =
    field(obj, name, path).flatMap {
      case ujson.Str(value) if value.trim.nonEmpty => Right(value)
      case ujson.Str(_) => Left(HttpInputError(s"$path.$name", "must not be blank"))
      case _ => Left(HttpInputError(s"$path.$name", "expected a string"))
    }

  private def field(obj: ujson.Obj, name: String, path: String) =
    obj.value.get(name).toRight(HttpInputError(s"$path.$name", "field is required"))

  private def arrayValue(value: ujson.Value, path: String) = value match {
    case array: ujson.Arr => Right(array.value.toVector)
    case _ => Left(HttpInputError(path, "expected an array"))
  }

  private def traverse[A, B](values: Vector[A])(
      decode: A => Either[HttpInputError, B]
  ): Either[HttpInputError, Vector[B]] =
    values.foldLeft[Either[HttpInputError, Vector[B]]](Right(Vector.empty)) {
      case (Right(accumulated), value) =>
        decode(value).map(accumulated :+ _)
      case (failure @ Left(_), _) => failure
    }

  private def objectValue(value: ujson.Value, path: String) = value match {
    case obj: ujson.Obj => Right(obj)
    case _ => Left(HttpInputError(path, "expected an object"))
  }
}
