package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.{DenizenId, PlayerId, SiteId}
import oathdigital.setup.FirstGameSetupState.{InProgress, NoGame, Ready}
import oathdigital.setup.FirstGameParticipant

final case class SetupPlayerProjection(
    playerId: String,
    displayName: String,
    role: String,
    colorToken: String
)
final case class SetupSiteProjection(siteId: String, label: String)
final case class SetupRegionProjection(
    regionId: String,
    sites: Vector[SetupSiteProjection]
)
final case class PawnLocationProjection(playerId: String, siteId: String)
final case class PrivateAdviserChoice(adviserId: String, label: String)

final case class FirstGameProjection(
    gameId: String,
    nextSequence: Long,
    phase: String,
    activeParticipantId: Option[String],
    players: Vector[SetupPlayerProjection],
    world: Vector[SetupRegionProjection],
    pawnLocations: Vector[PawnLocationProjection],
    legalControls: Vector[String],
    ready: Boolean,
    completed: Boolean,
    privateAdviserChoices: Vector[PrivateAdviserChoice]
)

final class FirstGameProjector(catalog: ExecutableCatalog) {
  private val siteNames =
    catalog.sites.map(site => site.id -> site.name).toMap
  private val denizenNames =
    catalog.denizens.map(d => DenizenId(d.id.value) -> d.name).toMap

  def project(
      gameId: String,
      loaded: LoadedFirstGame,
      requestingPlayer: PlayerId
  ): FirstGameProjection =
    loaded.state match {
      case NoGame =>
        FirstGameProjection(
          gameId,
          loaded.nextSequence,
          "not-started",
          None,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          ready = false,
          completed = false,
          Vector.empty
        )
      case progress: InProgress =>
        val order = turnOrder(progress.plan.participants,
          progress.plan.firstPlayer)
        val awaitingAdviser =
          progress.placements.size == progress.adviserChoices.size + 1
        val active =
          if (awaitingAdviser)
            order(progress.adviserChoices.size).playerId
          else order(progress.placements.size).playerId
        val phase =
          if (awaitingAdviser) "awaiting-adviser" else "awaiting-pawn"
        val controls =
          if (active != requestingPlayer) Vector.empty
          else if (awaitingAdviser) Vector("chooseAdviser")
          else Vector("placePawn")
        val privateChoices =
          if (active == requestingPlayer && awaitingAdviser) {
            val participantIndex = progress.plan.participants
              .indexWhere(_.playerId == requestingPlayer)
            progress.plan.denizenOrder
              .slice(6 + participantIndex * 3, 9 + participantIndex * 3)
              .map(id => PrivateAdviserChoice(
                id.value,
                denizenNames.getOrElse(id, safeLabel(id.value))
              ))
          } else Vector.empty
        FirstGameProjection(
          gameId,
          loaded.nextSequence,
          phase,
          Some(active.value),
          players(progress.plan.participants),
          world(progress.plan.orderedSites),
          progress.placements.map(placement =>
            PawnLocationProjection(
              placement.playerId.value,
              placement.siteId.value
            )),
          controls,
          ready = false,
          completed = false,
          privateChoices
        )
      case Ready(value) =>
        val setupPlayers = value.game.current.players.map { player =>
          SetupPlayerProjection(
            player.player.value,
            safeLabel(player.player.value),
            "exile",
            value.playerColors(player.player).value
          )
        }
        FirstGameProjection(
          gameId,
          loaded.nextSequence,
          "ready",
          Some(value.game.current.turn.activePlayer.value),
          setupPlayers,
          Vector(
            region("cradle", value.game.current.map.cradle),
            region("provinces", value.game.current.map.provinces),
            region("hinterland", value.game.current.map.hinterland)
          ),
          value.game.current.players.flatMap(player =>
            player.pawnSite.map(site =>
              PawnLocationProjection(player.player.value, site.value))),
          Vector.empty,
          ready = true,
          completed = true,
          Vector.empty
        )
    }

  private def players(
      participants: Vector[FirstGameParticipant]
  ): Vector[SetupPlayerProjection] =
    participants.map { participant =>
      SetupPlayerProjection(
        participant.playerId.value,
        safeLabel(participant.playerId.value),
        "exile",
        participant.color.value
      )
    }

  private def world(sites: Vector[SiteId]): Vector[SetupRegionProjection] =
    Vector(
      region("cradle", sites.take(2)),
      region("provinces", sites.slice(2, 5)),
      region("hinterland", sites.slice(5, 8))
    )

  private def region(
      id: String,
      sites: Vector[SiteId]
  ): SetupRegionProjection =
    SetupRegionProjection(
      id,
      sites.map(site =>
        SetupSiteProjection(
          site.value,
          siteNames.getOrElse(site, safeLabel(site.value))
        ))
    )

  private def turnOrder(
      participants: Vector[FirstGameParticipant],
      firstPlayer: PlayerId
  ): Vector[FirstGameParticipant] = {
    val start = participants.indexWhere(_.playerId == firstPlayer)
    participants.drop(start) ++ participants.take(start)
  }

  private def safeLabel(id: String): String =
    id.split(":").lastOption.getOrElse(id)
      .split("-").map(_.capitalize).mkString(" ")
}
