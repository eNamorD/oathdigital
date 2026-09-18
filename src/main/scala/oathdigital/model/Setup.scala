package oathdigital.model

final case class PlayerColor(value: String) {
  require(value.trim.nonEmpty, "player color must not be blank")
}

final case class FirstGameParticipant(
    playerId: PlayerId,
    lineageId: LineageId,
    color: PlayerColor
)

final case class PawnPlacement(playerId: PlayerId, siteId: SiteId)

final case class FirstGameSetupPlan(
    catalog: CatalogRef,
    participants: Vector[FirstGameParticipant],
    firstPlayer: PlayerId,
    orderedSites: Vector[SiteId],
    denizenOrder: Vector[DenizenId],
    worldDeckOrder: Vector[WorldCardId],
    relicOrder: Vector[RelicId],
    homelandEdifices: Vector[(SiteId, EdificeId)],
    oathkeeperGoal: OathkeeperGoal = OathkeeperGoal.Supremacy
)

sealed trait FirstGameFoundationProfile extends Product with Serializable
object FirstGameFoundationProfile {
  case object FixedUnaltered extends FirstGameFoundationProfile
}

final case class FirstGameSupportState(
    foundationProfile: FirstGameFoundationProfile,
    firstPlayer: PlayerId
)

sealed trait FirstGameSetupCommand extends Product with Serializable
object FirstGameSetupCommand {
  final case class Begin(plan: FirstGameSetupPlan)
      extends FirstGameSetupCommand
  final case class ChooseAdviser(playerId: PlayerId, adviserId: DenizenId)
      extends FirstGameSetupCommand
  final case class PlacePawn(playerId: PlayerId, siteId: SiteId)
      extends FirstGameSetupCommand
}

/** Pure CR pp. 6-7 table setup shared by setup projection and completion. */
final case class FirstGameSetupMaterial(
    players: Vector[PlayerState],
    map: MapState,
    commonCards: CardZones,
    banners: BannersState,
    tracks: GameTracks,
    favorBanks: Map[Suit, Int]
)
