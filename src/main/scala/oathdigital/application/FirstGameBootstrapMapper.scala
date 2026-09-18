package oathdigital.application

import oathdigital.gameplay.setup.{FirstGameParticipant, PlayerColor}
import oathdigital.model.{LineageId, PlayerId}
import oathdigital.protocol.FirstGameBootstrapRequest

object FirstGameBootstrapMapper {
  def map(request: FirstGameBootstrapRequest): FirstGameBootstrapConfig =
    FirstGameBootstrapConfig(
      request.participants.map(participant => FirstGameParticipant(
        PlayerId(participant.playerId),
        LineageId(participant.lineageId),
        PlayerColor(participant.color)
      )),
      PlayerId(request.firstPlayer)
    )
}
