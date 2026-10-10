package oathdigital.model

final case class PlayerBoardState(
    favor: Int,
    faceUpSecrets: Int,
    faceDownSecrets: Int,
    warbands: Int,
    supply: SupplyTrack
):
  require(favor >= 0, "player favor must be non-negative")
  require(faceUpSecrets >= 0, "faceup secrets must be non-negative")
  require(faceDownSecrets >= 0, "facedown secrets must be non-negative")
  require(warbands >= 0, "player-board warbands must be non-negative")

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
):
  require(targetScore > 0, "era target score must be positive")
  require(
    lineageScores.values.forall(_ >= 0),
    "lineage scores must be non-negative"
  )

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

/** `key` is the phase's wire spelling, carried here rather than in a codec
  * because the phase is now a journalled value: `EnterPhase` records a phase
  * change as a walker operation, so replay has to read one back. Following
  * [[OathkeeperGoal]]'s shape keeps the spelling next to the case that owns
  * it instead of in a match a new phase could be added without touching.
  */
sealed trait Phase extends Product with Serializable { def key: String }
object Phase:
  case object Setup extends Phase { val key = "setup" }
  case object Wake extends Phase { val key = "wake" }
  case object Act extends Phase { val key = "act" }
  case object Rest extends Phase { val key = "rest" }
  private[oathdigital] case object RoundEnd extends Phase:
    val key = "round-end"
  private[oathdigital] case object WarExhaustion extends Phase:
    val key = "war-exhaustion"

  val all: Vector[Phase] = Vector(Setup, Wake, Act, Rest, RoundEnd, WarExhaustion)

  def fromKey(key: String): Option[Phase] = all.find(_.key == key)

final case class TurnState(
    activePlayer: PlayerId,
    phase: Phase,
    usedPowers: Set[PowerUseRef]
)

enum PowerTiming { case Wake, Act, Rest }

sealed trait PowerSourceRef extends Product with Serializable
object PowerSourceRef:
  final case class Site(id: SiteId) extends PowerSourceRef

  /** A globally named card whose printed power was used. This includes
    * denizens, relics, edifices, visions, and legacies. Where the card sat
    * when it was used is not part of the use's identity, just as a decision
    * option names a card without its location.
    */
  final case class Card(id: CardId) extends PowerSourceRef

  /** A banner whose printed face power was used. */
  final case class Banner(banner: oathdigital.model.Banner) extends PowerSourceRef

  /** The source a phase power's start selection names, for its notes. */
  def of(ref: DecisionOptionRef): Option[PowerSourceRef] = ref match
    case DecisionOptionRef.Denizen(id) => Some(Card(id))
    case DecisionOptionRef.Relic(id) => Some(Card(id))
    case DecisionOptionRef.Vision(id) => Some(Card(id))
    case DecisionOptionRef.Edifice(id) => Some(Card(id))
    case DecisionOptionRef.Site(id) => Some(Site(id))
    case DecisionOptionRef.Banner(banner) => Some(Banner(banner))
    case _ => None

/** An action owed to `player` once the current one ends (follow-up actions
  * design): a full action of its own, started or skipped by its player
  * before anything else happens, spending no Supply. `source` is the power
  * that queued it, which the Game Log credits. `player` is the one place an
  * off-turn actor (Sneak Attack) will differ from the active player. */
final case class FollowUp(player: PlayerId, action: ActionRef,
    source: PowerSourceRef)

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
):
  require(round >= 1, "round must be positive")
  require(round <= 8, "round must be eight or less")
  require(visionsDrawn >= 0, "Visions Drawn must be non-negative")

enum VictoryKind(val key: String):
  case Usurper extends VictoryKind("usurper")
  case Visionary extends VictoryKind("visionary")
  case Oathkeeper extends VictoryKind("oathkeeper")
  case RandomSelection extends VictoryKind("random-selection")

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
    result: Option[GameResult],
    temporaryHands: Map[PlayerId, Vector[WorldCardId]] = Map.empty,
    setAsideRelics: Vector[RelicId] = Vector.empty,
    // Walker (procedure-walker) pending state: the only pending procedure
    // position. Walker procedures read and write `walkerPending`.
    walkerPending: Option[PendingTree] = None,
    rollPools: Map[PoolKey, DicePoolState] = Map.empty,
    rollOutcomes: Map[PoolKey, RollOutcome] = Map.empty,
    // Stored beside, not inside, pointer-only PendingTree. Rebuilds the
    // command-local operation tree after reload.
    walkerProcedure: Option[ProcedureRef] = None,
    // What the player selected when the walker procedure started, for an action
    // whose tree cannot be built without it (batch-1 Task 5) -- Search's
    // source is one. They are `DecisionOptionRef`s, the
    // same game-object vocabulary a decision option names, so nothing outside
    // the action that declared them learns what they mean: empty is "this
    // action declares none", and the action itself rejects a shape it did not
    // ask for.
    //
    // Durable because a resumed command rebuilds the tree the start built,
    // and a selection -- unlike a pawn site -- cannot be re-derived from
    // state. Restored by replay from the `WalkerParked` fact and cleared by
    // `WalkerCompleted` alongside the other walker-owned scratch fields.
    walkerStartArgs: Vector[DecisionOptionRef] = Vector.empty,
    // What the engine derived when the walker procedure started and cannot
    // read back once the walk changes it (travel destination design, ruling
    // 6): Travel's source site, after the pawn has moved. Engine-owned, where
    // `walkerStartArgs` is what the player selected. Restored by replay from
    // the `WalkerParked` fact and cleared by `WalkerCompleted`.
    walkerStartFacts: Vector[DecisionOptionRef] = Vector.empty,
    // The public result of the last Campaign fought. Not walker scratch:
    // `WalkerCompleted` leaves it, and the next Campaign replaces it.
    lastCampaignResult: Option[CampaignResult] = None,
    // Actions owed once the current one ends, oldest first (follow-up
    // actions design). Not walker scratch: it outlives runs and phases, and
    // only `FollowUpStarted` and `FollowUpSkipped` take from it.
    followUps: Vector[FollowUp] = Vector.empty,
    // The entry the current run was started from. Walker scratch: replay
    // sets it from `FollowUpStarted` and `WalkerCompleted` clears it, so the
    // cost waiver and the Act-phase exemption hold on every resume.
    walkerFollowUp: Option[FollowUp] = None,
    // Ops a step of the current action holds for a later step of it
    // (operation rewrite design, "Deferral"). Walker scratch: replay
    // rebuilds it from the recorded `Defer`s, and the walker and
    // `WalkerCompleted` clear it with the dice pools. Never projected.
    walkerDeferred: Map[DeferralKey, Vector[CoreOperation]] = Map.empty
)

final case class OathGame(
    campaign: CampaignState,
    current: CurrentGameState
):
  /** Where every card of this state is, with no expected cards (see
    * `CardIndex.from`). Built at most once per instance.
    *
    * The cache cannot go stale: `OathGame` is immutable and every change makes
    * a new instance through `copy`. Case-class equality, hashing and `copy`
    * read only the constructor fields, so the cache changes none of them, and
    * nothing serializes it.
    */
  lazy val cardIndex: Either[Vector[CardIndexProblem], CardIndex] =
    CardIndex.scan(this, Set.empty)
