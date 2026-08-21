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
    dispossessed: Vector[DenizenId],
    suitedReserves: Map[Suit, Vector[DenizenId]],
    oathkeeperGoal: OathkeeperGoal,
    era: EraState
)

sealed trait Phase extends Product with Serializable
object Phase {
  case object Wake extends Phase
  case object Act extends Phase
  case object Rest extends Phase
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

sealed trait ChronicleTask extends Product with Serializable
object ChronicleTask {
  case object Sun extends ChronicleTask
  case object Throne extends ChronicleTask
  case object World extends ChronicleTask
  case object Beacon extends ChronicleTask
  case object Stars extends ChronicleTask
}

sealed trait PendingProcedure extends Product with Serializable {
  def decision: DecisionId
}

final case class CampaignForceAllocation(site: SiteId, count: Int) {
  require(count >= 0, "Campaign allocation must be non-negative")
}

sealed trait CampaignKind extends Product with Serializable {
  def key: String
}
object CampaignKind {
  case object Conquest extends CampaignKind { val key = "conquest" }
  case object Raid extends CampaignKind { val key = "raid" }
}

sealed trait CampaignBanner extends Product with Serializable {
  def key: String
  private[model] def order: Int
}
object CampaignBanner {
  case object PeoplesFavor extends CampaignBanner {
    val key = "peoples-favor"
    private[model] val order = 0
  }
  case object DarkestSecret extends CampaignBanner {
    val key = "darkest-secret"
    private[model] val order = 1
  }
}

sealed trait CampaignRaidTarget extends Product with Serializable {
  def playerId: PlayerId
  def stableKey: String
  private[model] def canonicalOrder: (Int, String)
}
object CampaignRaidTarget {
  final case class Pawn(playerId: PlayerId) extends CampaignRaidTarget {
    def stableKey: String = s"pawn:${playerId.value}"
    private[model] def canonicalOrder = 0 -> playerId.value
  }
  final case class Relic(playerId: PlayerId, relicId: RelicId)
      extends CampaignRaidTarget {
    def stableKey: String = s"relic:${playerId.value}:${relicId.value}"
    private[model] def canonicalOrder = 1 -> s"${playerId.value}:${relicId.value}"
  }
  final case class Banner(playerId: PlayerId, banner: CampaignBanner)
      extends CampaignRaidTarget {
    def stableKey: String = s"banner:${playerId.value}:${banner.key}"
    private[model] def canonicalOrder =
      (2 + banner.order) -> playerId.value
  }

  def canonical(targets: Iterable[CampaignRaidTarget]): Vector[CampaignRaidTarget] =
    targets.toVector.sortBy(_.canonicalOrder)

  def isCanonical(targets: Vector[CampaignRaidTarget]): Boolean =
    targets.nonEmpty && targets.head.isInstanceOf[Pawn] &&
      targets.map(_.playerId).distinct.size == 1 &&
      targets.map(_.stableKey).distinct.size == targets.size &&
      canonical(targets) == targets
}

sealed trait CampaignLosingForceEffect extends Product with Serializable {
  def site: SiteId
}
sealed trait CampaignDefender extends Product with Serializable
object CampaignDefender {
  case object Bandits extends CampaignDefender
  final case class Player(playerId: PlayerId) extends CampaignDefender
}
object CampaignLosingForceEffect {
  final case class Remove(site: SiteId, force: ForceKind, count: Int)
      extends CampaignLosingForceEffect {
    require(count > 0, "removed Campaign force must be positive")
  }
  final case class Preserve(site: SiteId, force: ForceKind, count: Int)
      extends CampaignLosingForceEffect {
    require(count > 0, "preserved Campaign force must be positive")
  }
  final case class Relocate(site: SiteId, destination: SiteId,
      force: ForceKind, count: Int) extends CampaignLosingForceEffect {
    require(site != destination, "Campaign relocation needs a different site")
    require(count > 0, "relocated Campaign force must be positive")
  }
  final case class Replace(site: SiteId, force: ForceKind, count: Int,
      replacementForce: Option[ForceKind], replacementCount: Int)
      extends CampaignLosingForceEffect {
    require(count > 0, "replaced Campaign force must be positive")
    require(replacementCount >= 0, "replacement force must be non-negative")
    require(replacementForce.nonEmpty == (replacementCount > 0),
      "replacement force and count must agree")
  }
  final case class ReturnToBoard(site: SiteId, player: PlayerId,
      force: ForceKind, count: Int)
      extends CampaignLosingForceEffect {
    require(count > 0, "returned Campaign force must be positive")
  }
  final case class KillCommitted(site: SiteId, player: PlayerId,
      force: ForceKind, count: Int) extends CampaignLosingForceEffect {
    require(count > 0, "killed committed Campaign force must be positive")
  }
  final case class RelocateCommitted(site: SiteId, player: PlayerId,
      force: ForceKind, count: Int) extends CampaignLosingForceEffect {
    require(count > 0, "relocated committed Campaign force must be positive")
  }
  final case class PreserveCommitted(site: SiteId, player: PlayerId,
      force: ForceKind, count: Int) extends CampaignLosingForceEffect {
    require(count > 0, "preserved committed Campaign force must be positive")
  }
}
object PendingProcedure {
  sealed trait CampaignPlanSide extends Product with Serializable
  object CampaignPlanSide {
    case object Attacker extends CampaignPlanSide
    case object Defender extends CampaignPlanSide
  }

  sealed trait CampaignPlanCost extends Product with Serializable
  object CampaignPlanCost {
    final case class Favor(count: Int) extends CampaignPlanCost {
      require(count > 0, "Campaign favor cost must be positive")
    }
    final case class Secret(count: Int) extends CampaignPlanCost {
      require(count > 0, "Campaign secret cost must be positive")
    }
  }

  sealed trait CampaignPlanEffect extends Product with Serializable
  object CampaignPlanEffect {
    final case class AddAttackDice(count: Int) extends CampaignPlanEffect {
      require(count > 0, "added Campaign attack dice must be positive")
    }
    final case class AddDefenseDice(count: Int) extends CampaignPlanEffect {
      require(count > 0, "added Campaign defense dice must be positive")
    }
    case object IgnoreAttackSkulls extends CampaignPlanEffect
    case object RevealSource extends CampaignPlanEffect
    /** Typed extension points: their payload remains owned by a registered
      * handler rather than interpreted as a general card scripting language.
      */
    final case class TransformAttackResult(handlerId: String)
        extends CampaignPlanEffect
    final case class ReplaceLosingForcePolicy(policyId: String)
        extends CampaignPlanEffect
    final case class Suspend(decisionKind: String) extends CampaignPlanEffect
  }

  sealed trait CampaignPlanSource extends Product with Serializable {
    def stableKey: String
  }
  object CampaignPlanSource {
    final case class Adviser(playerId: PlayerId, id: DenizenId)
        extends CampaignPlanSource {
      def stableKey: String = s"adviser:${playerId.value}:denizen:${id.value}"
    }
    final case class SiteCard(siteId: SiteId, id: DenizenId)
        extends CampaignPlanSource {
      def stableKey: String = s"site-card:${siteId.value}:denizen:${id.value}"
    }
    final case class Relic(playerId: PlayerId, id: RelicId)
        extends CampaignPlanSource {
      def stableKey: String = s"relic:${playerId.value}:${id.value}"
    }
    final case class Title(playerId: PlayerId) extends CampaignPlanSource {
      def stableKey: String = s"title:${playerId.value}"
    }
  }

  final case class CampaignPlanResolution(
      source: CampaignPlanSource,
      handlerId: String,
      side: CampaignPlanSide,
      costs: Vector[CampaignPlanCost],
      effects: Vector[CampaignPlanEffect]
  )

  final case class Search(
      decision: DecisionId,
      actor: PlayerId,
      source: SearchSource = SearchSource.WorldDeck,
      origin: Region = Region.Cradle,
      supplySpent: Int = 0,
      drawn: Vector[WorldCardId] = Vector.empty
  ) extends PendingProcedure

  final case class Campaign(
      decision: DecisionId,
      actor: PlayerId,
      targetSites: Vector[SiteId],
      defender: CampaignDefender,
      force: Int,
      plans: Vector[CampaignPlanResolution],
      attackerPlansFinished: Boolean,
      defenderPlansFinished: Boolean,
      attackDice: Vector[AttackDieFace],
      attack: Int,
      skullLosses: Int,
      sacrificed: Option[Int],
      defenseDice: Vector[DefenseDieFace],
      defense: Option[Int],
      victorious: Option[Boolean],
      kind: CampaignKind = CampaignKind.Conquest,
      raidTargets: Vector[CampaignRaidTarget] = Vector.empty
  ) extends PendingProcedure {
    require(kind match {
      case CampaignKind.Conquest => targetSites.nonEmpty && raidTargets.isEmpty
      case CampaignKind.Raid => targetSites.isEmpty &&
        CampaignRaidTarget.isCanonical(raidTargets)
    }, "Campaign targets must match their kind and canonical order")
  }

  final case class Recover(
      decision: DecisionId,
      actor: PlayerId,
      site: SiteId,
      difficulty: Int,
      rolls: Vector[Vector[DefenseDieFace]],
      supplySpent: Int,
      successful: Boolean
  ) extends PendingProcedure

  final case class OathkeeperRecipient(
      decision: DecisionId,
      actor: PlayerId,
      candidates: Vector[PlayerId]
  ) extends PendingProcedure

  final case class Negotiation(
      decision: DecisionId,
      actor: PlayerId,
      other: PlayerId
  ) extends PendingProcedure

  final case class Chronicle(
      decision: DecisionId,
      task: ChronicleTask,
      taskHolders: Map[ChronicleTask, PlayerId]
  ) extends PendingProcedure
}

sealed trait AttackDieFace extends Product with Serializable
object AttackDieFace {
  case object HollowSword extends AttackDieFace
  case object OneSword extends AttackDieFace
  case object TwoSwordsSkull extends AttackDieFace

  def score(faces: Vector[AttackDieFace]): Int =
    faces.count(_ == OneSword) + faces.count(_ == HollowSword) / 2 +
      faces.count(_ == TwoSwordsSkull) * 2

  def skulls(faces: Vector[AttackDieFace]): Int =
    faces.count(_ == TwoSwordsSkull)
}

sealed trait DefenseDieFace extends Product with Serializable
object DefenseDieFace {
  case object Blank extends DefenseDieFace
  case object OneShield extends DefenseDieFace
  case object TwoShields extends DefenseDieFace
  case object Doubler extends DefenseDieFace

  def score(faces: Vector[DefenseDieFace]): Int = {
    val shields = faces.map {
      case OneShield => 1
      case TwoShields => 2
      case _ => 0
    }.sum
    shields * (1 << faces.count(_ == Doubler))
  }
}

sealed trait SearchSource extends Product with Serializable
object SearchSource {
  case object WorldDeck extends SearchSource
  final case class RegionalDiscard(region: Region) extends SearchSource
}

sealed trait SearchPlacement extends Product with Serializable
object SearchPlacement {
  case object Discard extends SearchPlacement
  final case class Site(replace: Option[CardId]) extends SearchPlacement
  final case class Adviser(
      orientation: Orientation,
      replace: Option[CardId]
  ) extends SearchPlacement
}

final case class GameResult(winner: PlayerId)

final case class CurrentGameState(
    players: Vector[PlayerState],
    map: MapState,
    commonCards: CardZones,
    banners: BannersState,
    title: OathkeeperState,
    turn: TurnState,
    tracks: GameTracks,
    pending: Option[PendingProcedure],
    result: Option[GameResult]
)

final case class OathGame(
    catalog: CatalogRef,
    campaign: CampaignState,
    current: CurrentGameState
)
