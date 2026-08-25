package oathdigital.server

import oathdigital.application.FirstGameBootstrapConfig
import oathdigital.protocol.{ActorlessCommandCodec, ActorlessCommandRequest}

final case class AuthenticatedBootstrapRequest(
    expectedNextSequence: Long,
    config: FirstGameBootstrapConfig
)

/** Authentication affects actor binding only, never payload decoding. */
object AuthenticatedGameHttpWire {
  def decodeCommand(json: String): Either[HttpInputError, ActorlessCommandRequest] =
    ActorlessCommandCodec.decode(json)
      .left.map(error => HttpInputError(error.path, error.message))

  def decodeBootstrap(json: String): Either[HttpInputError, AuthenticatedBootstrapRequest] =
    GameHttpWire.decodeBootstrap(json).map(request => AuthenticatedBootstrapRequest(
      request.expectedNextSequence, request.config))
}
