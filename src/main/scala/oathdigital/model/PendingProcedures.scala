package oathdigital.model

sealed trait PendingProcedure extends Product with Serializable {
  def decision: DecisionId
}

/** Stable, container-qualified target for a denizen printed at a site. */
final case class SiteDenizenTarget(siteId: SiteId, denizenId: DenizenId) {
  def stableKey: String = s"site:${siteId.value}:denizen:${denizenId.value}"
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
  final case class Banner(playerId: PlayerId, banner: oathdigital.model.Banner)
      extends CampaignRaidTarget {
    def stableKey: String = s"banner:${playerId.value}:${banner.key}"
    private[model] def canonicalOrder =
      (2 + oathdigital.model.Banner.all.indexOf(banner)) -> playerId.value
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
final case class CampaignRaidBoardLoss(playerId: PlayerId, killed: Int,
    returned: Int) {
  require(killed >= 0 && returned >= 0, "Raid board losses must be non-negative")
}
object PendingProcedure {
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

  final case class CampaignRaidRelocation(
      decision: DecisionId,
      actor: PlayerId,
      defender: PlayerId,
      origin: SiteId,
      legalSites: Vector[SiteId]
  ) extends PendingProcedure {
    require(legalSites.nonEmpty && !legalSites.contains(origin) &&
      legalSites.distinct.size == legalSites.size,
      "Raid relocation sites must be distinct and exclude the origin")
  }

}
