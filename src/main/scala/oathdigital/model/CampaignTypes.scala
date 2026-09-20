package oathdigital.model

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
  * board force. The attacker prevails when `attackTotal > defenseScore`.
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
    victorious: Boolean
) {
  def attackTotal: Int = attackScore + sacrificed
}
