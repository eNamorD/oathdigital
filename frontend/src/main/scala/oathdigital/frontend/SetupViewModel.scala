package oathdigital.frontend

import oathdigital.model.{PlayerId, SiteId}
import oathdigital.setup.{PawnPlacement, SetupState}

final case class PlayerReference(
    id: PlayerId,
    text: String,
    colorClass: String,
    placed: Boolean,
    active: Boolean
)

final case class RegionView(name: String, sites: Vector[SiteId])

final case class SetupViewModel(
    worldTitle: String,
    players: Vector[PlayerReference],
    regions: Vector[RegionView],
    placements: Vector[PawnPlacement]
)

object SetupViewModel {
  def from(projection: SetupProjection): SetupViewModel = {
    val placed = placements(projection.state).map(_.playerId).toSet
    SetupViewModel(
      worldTitle = "The World",
      players = projection.participants.map { participant =>
        PlayerReference(
          participant.playerId,
          participant.playerId.value,
          colorClass(participant.playerId),
          placed.contains(participant.playerId),
          projection.activePlayer.contains(participant.playerId)
        )
      },
      regions = Vector(
        RegionView("Cradle", projection.orderedSites.take(2)),
        RegionView("Provinces", projection.orderedSites.slice(2, 5)),
        RegionView("Hinterland", projection.orderedSites.slice(5, 8))
      ),
      placements = placements(projection.state)
    )
  }

  def colorClass(playerId: PlayerId): String =
    if (playerId.value == "Chancellor") "player-purple"
    else if (playerId.value.startsWith("Blue ")) "player-blue"
    else if (playerId.value.startsWith("Red ")) "player-red"
    else "player-neutral"

  private def placements(state: SetupState): Vector[PawnPlacement] =
    state match {
      case value: SetupState.InProgress => value.pawnPlacements
      case value: SetupState.Completed => value.pawnPlacements
      case SetupState.NotStarted => Vector.empty
    }
}
