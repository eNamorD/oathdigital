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

/** The concrete per-game deal derived from a Chronicle: who is seated and in
  * what order, and the dealt orders of the two decks a Chronicle's own
  * order is not meaningful for (2026-09-21 Chronicle design, slice 2,
  * "Setup from a Chronicle"). Recorded on `GameStarted` alongside the
  * Chronicle itself, so replay never re-derives either from live state.
  */
final case class SetupOrders(
    participants: Vector[FirstGameParticipant],
    firstPlayer: PlayerId,
    worldDeckOrder: Vector[WorldCardId],
    relicOrder: Vector[RelicId]
)

sealed trait FirstGameFoundationProfile extends Product with Serializable
object FirstGameFoundationProfile {
  case object FixedUnaltered extends FirstGameFoundationProfile
}

final case class FirstGameSupportState(
    foundationProfile: FirstGameFoundationProfile,
    firstPlayer: PlayerId
)

/** The validated shape `FirstGameSetupMaterializer` builds a table from.
  * Built fresh from a Chronicle by `GameStartRules.evolve` (slice 2); no
  * longer a command payload, so it carries no `catalog` field -- Chronicle
  * validation now checks ids directly against the live catalog instead of
  * comparing a stored `CatalogRef`.
  *
  * `denizenOrder` deals hands and the six seeded regional discards (CR pp.
  * 6-7); it never contains a Vision. `worldDeckOrder` is `denizenOrder`
  * with the five fixed Vision identities spliced into the 10+2/15+3
  * packets, and becomes the draw deck once dealing is done.
  */
final case class FirstGameSetupPlan(
    participants: Vector[FirstGameParticipant],
    firstPlayer: PlayerId,
    orderedSites: Vector[SiteId],
    denizenOrder: Vector[DenizenId],
    worldDeckOrder: Vector[WorldCardId],
    relicOrder: Vector[RelicId],
    homelandEdifices: Vector[(SiteId, EdificeId)],
    oathkeeperGoal: OathkeeperGoal = OathkeeperGoal.Supremacy
)

/** Pure CR pp. 6-7 table setup, shared by `GameStartRules`. */
final case class FirstGameSetupMaterial(
    players: Vector[PlayerState],
    map: MapState,
    commonCards: CardZones,
    banners: BannersState,
    tracks: GameTracks,
    favorBanks: Map[Suit, Int],
    temporaryHands: Map[PlayerId, Vector[WorldCardId]]
)
