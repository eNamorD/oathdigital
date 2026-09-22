package oathdigital.protocol

final case class TrustedGameCreateRequest(
    gameId: String,
    participants: Vector[BootstrapParticipantRequest]
)

final case class TrustedSeatLink(playerId: String, url: String)
final case class TrustedGameCreateResponse(gameId: String, seats: Vector[TrustedSeatLink])
