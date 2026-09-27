package oathdigital.application

import oathdigital.model.{FirstGameParticipant, LineageId, PlayerColor, PlayerId}
import oathdigital.protocol.FirstGameBootstrapRequest

object FirstGameBootstrapMapper:
  /** The lineage a color plays: every color has exactly one. */
  def lineageOf(color: PlayerColor): LineageId = LineageId(s"${color.key}-lineage")

  def map(request: FirstGameBootstrapRequest): FirstGameBootstrapConfig =
    FirstGameBootstrapConfig(
      request.participants.map(participant => FirstGameParticipant(
        PlayerId(participant.playerId),
        lineageOf(participant.color),
        participant.color
      )),
      PlayerId(request.firstPlayer)
    )
