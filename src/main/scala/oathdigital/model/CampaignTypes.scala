package oathdigital.model

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

sealed trait CampaignDefender extends Product with Serializable
object CampaignDefender {
  case object Bandits extends CampaignDefender
  final case class Player(playerId: PlayerId) extends CampaignDefender
}

/** Which side of a Campaign a battle plan belongs to. */
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

/** The public, durable record of one Campaign's battle: written by
  * `RecordCampaignResult` when the outcome is known, projected to every viewer,
  * and replaced by the next Campaign. Everything in it is public: dice are
  * public, and a Raid's targets are a pawn, faceup relics and banners.
  *
  * `attackScore` is the attack after the skull cap and any Outriders, before
  * the sacrifice; `defenseScore` is the defense dice score plus the defender's
  * board force. The attacker prevails when `attackTotal > defenseScore`, and
  * `attackerWins` records that fact: it is true for an attacker victory and
  * false for a defender victory, whichever side a battle plan's user is on.
  */
final case class CampaignResult(
    attacker: PlayerId,
    kind: CampaignKind,
    defender: CampaignDefender,
    targetSites: Vector[SiteId],
    raidTargets: Vector[CampaignRaidTarget],
    force: Int,
    attackFaces: Vector[AttackDieFace],
    attackScore: Int,
    skullLosses: Int,
    sacrificed: Int,
    defenseFaces: Vector[DefenseDieFace],
    defenseScore: Int,
    attackerWins: Boolean
) {
  def attackTotal: Int = attackScore + sacrificed
}
