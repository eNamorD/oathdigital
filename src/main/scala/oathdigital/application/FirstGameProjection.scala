package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._
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
final case class ActivePlayerResourcesProjection(
    favor: Int,
    faceUpSecrets: Int,
    faceDownSecrets: Int,
    supply: Int
)
final case class CurrentSiteResourcesProjection(
    siteId: String,
    favor: Int,
    secrets: Int
)

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
    privateAdviserChoices: Vector[PrivateAdviserChoice],
    activePlayerResources: Option[ActivePlayerResourcesProjection] = None,
    currentSiteResources: Option[CurrentSiteResourcesProjection] = None,
    actionSelectionOpen: Boolean = false,
    actionFamilies: Vector[String] = Vector.empty
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
  ): FirstGameProjection = projectFor(gameId, loaded, Some(requestingPlayer))

  def projectPublic(
      gameId: String,
      loaded: LoadedFirstGame
  ): FirstGameProjection = projectFor(gameId, loaded, None)

  private def projectFor(
      gameId: String,
      loaded: LoadedFirstGame,
      requestingPlayer: Option[PlayerId]
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
          if (!requestingPlayer.contains(active)) Vector.empty
          else if (awaitingAdviser) Vector("chooseAdviser")
          else Vector("placePawn")
        val privateChoices =
          if (requestingPlayer.contains(active) && awaitingAdviser) {
            val participantIndex = progress.plan.participants
              .indexWhere(participant =>
                requestingPlayer.contains(participant.playerId))
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
        val current = value.game.current
        val active = current.players.find(
          _.player == current.turn.activePlayer).get
        val site = active.pawnSite.flatMap(current.map.sites.get)
        val enemiesAtSite = active.pawnSite.exists(siteId =>
          current.players.exists(other =>
            other.player != active.player && other.pawnSite.contains(siteId)))
        val takeUsed = active.pawnSite.exists(siteId =>
          current.turn.usedPowers.contains(PowerUseRef(
            PowerTiming.Wake,
            PowerSourceRef.Site(siteId),
            PowerId("take-wealth")
          )))
        val controls =
          if (!requestingPlayer.contains(active.player) ||
              current.turn.phase != Phase.Wake) Vector.empty
          else {
            val takeControls = site.toVector.flatMap { state =>
              if (enemiesAtSite || takeUsed) Vector.empty
              else Vector(
                Option.when(state.tokens.favor > 0)("takeFavor"),
                Option.when(state.tokens.secrets > 0)("takeSecret")
              ).flatten
            }
            takeControls :+ "endWake"
          }
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
          current.turn.phase match {
            case Phase.Wake => "wake"
            case Phase.Act => "act-action-selection"
            case Phase.Rest => "rest"
          },
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
          controls,
          ready = true,
          completed = true,
          Vector.empty,
          Some(ActivePlayerResourcesProjection(
            active.board.favor,
            active.board.faceUpSecrets,
            active.board.faceDownSecrets,
            active.board.supply.supply
          )),
          active.pawnSite.flatMap(siteId => site.map(state =>
            CurrentSiteResourcesProjection(
              siteId.value,
              state.tokens.favor,
              state.tokens.secrets
            ))),
          actionSelectionOpen = current.turn.phase == Phase.Act,
          actionFamilies =
            if (current.turn.phase == Phase.Act)
              Vector("Search", "Travel", "Campaign", "Muster", "Trade",
                "Forge", "Recover", "Challenge")
            else Vector.empty
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
