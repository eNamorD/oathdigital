package oathdigital.server

import oathdigital.protocol.{
  ActorlessCommandRequest,
  FirstGameBootstrapCodec,
  FirstGameBootstrapRequest,
  ProtocolDecodeFailure
}
import oathdigital.protocol.projection.{GameProjection, GameProjectionCodec}

final case class HttpInputError(path: String, message: String)

object GameHttpWire {
  def decodeBootstrap(json: String): Either[HttpInputError,
      FirstGameBootstrapRequest] =
    FirstGameBootstrapCodec.decode(json).left.map(inputError)

  def decodeCommand(json: String): Either[HttpInputError,
    ActorlessCommandRequest] = AuthenticatedGameHttpWire.decodeCommand(json)

  def encodeProjection(projection: GameProjection): String =
    GameProjectionCodec.encode(projection)

  def encodeError(code: String, message: String): String =
    ujson.write(ujson.Obj("error" -> code, "message" -> message))

  private[server] def inputError(error: ProtocolDecodeFailure): HttpInputError =
    HttpInputError(error.path, error.message)
}
