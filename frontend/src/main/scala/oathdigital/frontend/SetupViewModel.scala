package oathdigital.frontend

import oathdigital.model.PlayerId
import oathdigital.setup.{PawnPlacement, SetupState}

final case class PlayerReference(
    id: PlayerId,
    text: String,
    color: PlayerColorToken,
    placed: Boolean,
    active: Boolean
)

final case class RegionView(name: String, sites: Vector[SiteDisplay])

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
      players = projection.players.map { player =>
        PlayerReference(
          player.id,
          player.label,
          player.color,
          placed.contains(player.id),
          projection.activePlayer.contains(player.id)
        )
      },
      regions = projection.world.regions.map(region =>
        RegionView(region.name, region.sites)
      ),
      placements = placements(projection.state)
    )
  }

  def player(
      projection: SetupProjection,
      playerId: PlayerId
  ): PlayerDisplay =
    projection.players
      .find(_.id == playerId)
      .getOrElse(PlayerDisplay(
        playerId,
        playerId.value,
        PlayerColorToken.Neutral
      ))

  private def placements(state: SetupState): Vector[PawnPlacement] =
    state match {
      case value: SetupState.InProgress => value.pawnPlacements
      case value: SetupState.Completed => value.pawnPlacements
      case SetupState.NotStarted => Vector.empty
    }
}
