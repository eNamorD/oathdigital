package oathdigital.model

final case class PlayerBoardState(
    favor: Int,
    faceUpSecrets: Int,
    faceDownSecrets: Int,
    warbands: Int,
    supply: SupplyTrack
) {
  require(favor >= 0, "player favor must be non-negative")
  require(faceUpSecrets >= 0, "faceup secrets must be non-negative")
  require(faceDownSecrets >= 0, "facedown secrets must be non-negative")
  require(warbands >= 0, "player-board warbands must be non-negative")
}

final case class PlayerState(
    player: PlayerId,
    lineage: LineageId,
    pawnSite: Option[SiteId],
    board: PlayerBoardState,
    advisers: Vector[AdviserState],
    relics: Vector[RelicState],
    revealedVision: Option[VisionState]
)

/**
 * Persistent lineage facts. Starting advisers are populated between games and
 * moved into the active player's adviser container during setup.
 */
final case class LineageState(
    id: LineageId,
    previousPlayer: Option[PlayerId],
    role: Role,
    legacies: Vector[LegacyState],
    startingAdvisers: Vector[AdviserState]
)

final case class EraState(
    targetScore: Int,
    lineageScores: Map[LineageId, Int]
) {
  require(targetScore > 0, "era target score must be positive")
  require(
    lineageScores.values.forall(_ >= 0),
    "lineage scores must be non-negative"
  )
}

final case class CampaignState(
    atlas: AtlasState,
    foundations: Map[FoundationNumber, FoundationState],
    lineages: Map[LineageId, LineageState],
    reliquary: Vector[RelicId],
    dispossessed: Vector[WorldCardId],
    suitedReserves: Map[Suit, Vector[DenizenId]],
    oathkeeperGoal: OathkeeperGoal,
    era: EraState
)

sealed trait Phase extends Product with Serializable
object Phase {
  case object Wake extends Phase
  case object Act extends Phase
  case object Rest extends Phase
  private[oathdigital] case object RoundEnd extends Phase
  private[oathdigital] case object WarExhaustion extends Phase
}

final case class TurnState(
    activePlayer: PlayerId,
    phase: Phase,
    usedPowers: Set[PowerUseRef]
)

sealed trait PowerTiming extends Product with Serializable
object PowerTiming {
  case object Wake extends PowerTiming
  case object Act extends PowerTiming
  case object Rest extends PowerTiming
}

sealed trait PowerSourceRef extends Product with Serializable
object PowerSourceRef {
  final case class Site(id: SiteId) extends PowerSourceRef
}

/** A stable identity for one use-limited power instance this turn. */
final case class PowerUseRef(
    timing: PowerTiming,
    source: PowerSourceRef,
    powerId: PowerId
)

final case class GameTracks(
    round: Int,
    visionsDrawn: Int,
    usurperLimited: Boolean
) {
  require(round >= 1, "round must be positive")
  require(round <= 8, "round must be eight or less")
  require(visionsDrawn >= 0, "Visions Drawn must be non-negative")
}

sealed trait VictoryKind extends Product with Serializable { def key: String }
object VictoryKind {
  case object Usurper extends VictoryKind { val key = "usurper" }
  case object Visionary extends VictoryKind { val key = "visionary" }
  case object Oathkeeper extends VictoryKind { val key = "oathkeeper" }
  case object RandomSelection extends VictoryKind { val key = "random-selection" }
}

final case class GameResult(winner: PlayerId,
    kind: VictoryKind = VictoryKind.Usurper)

final case class CurrentGameState(
    players: Vector[PlayerState],
    map: MapState,
    commonCards: CardZones,
    banners: BannersState,
    title: OathkeeperState,
    turn: TurnState,
    tracks: GameTracks,
    pending: Option[PendingProcedure],
    result: Option[GameResult],
    temporaryHands: Map[PlayerId, Vector[WorldCardId]] = Map.empty,
    setAsideRelics: Vector[RelicId] = Vector.empty,
    // Walker (procedure-walker) pending state. Legacy `pending` stays
    // alongside for actions still on the legacy evolve path this slice (dual
    // pending); walker actions read/write only `walkerPending`.
    walkerPending: Option[PendingTree] = None,
    rollPools: Map[PoolKey, DicePoolState] = Map.empty,
    rollOutcomes: Map[PoolKey, RollOutcome] = Map.empty,
    // Stored beside, not inside, pointer-only PendingTree. Rebuilds the
    // command-local operation tree after reload.
    walkerAction: Option[ActionRef] = None
)

final case class OathGame(
    catalog: CatalogRef,
    campaign: CampaignState,
    current: CurrentGameState
)
