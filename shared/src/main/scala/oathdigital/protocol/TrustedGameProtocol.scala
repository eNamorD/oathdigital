package oathdigital.protocol

/** The server generates the game ID and derives each lineage, so a request
  * names only the players and their colors. */
final case class TrustedGameCreateRequest(
    participants: Vector[BootstrapParticipantRequest]
)

final case class TrustedSeatLink(playerId: String, url: String)
final case class TrustedGameCreateResponse(gameId: String, seats: Vector[TrustedSeatLink])
