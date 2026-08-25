package oathdigital.server

import oathdigital.protocol.{ActorlessCommandCodec, ActorlessCommandRequest,
  FirstGameBootstrapRequest}

/** Authentication affects actor binding only, never payload decoding. */
object AuthenticatedGameHttpWire {
  def decodeCommand(json: String): Either[HttpInputError, ActorlessCommandRequest] =
    ActorlessCommandCodec.decode(json)
      .left.map(error => HttpInputError(error.path, error.message))

  def decodeBootstrap(json: String): Either[HttpInputError, FirstGameBootstrapRequest] =
    GameHttpWire.decodeBootstrap(json)
}
