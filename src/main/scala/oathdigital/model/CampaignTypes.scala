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

/** What using a battle plan costs its user. `Favor` and `Secret` are placed onto
  * the plan's source card, which may already hold resources; `FavorBurnt` and
  * `SecretBurnt` leave play to the shared bank. `SacrificeWarband` kills one
  * warband of the user's own force, and only a defender may pay it: the board
  * in a Raid, a warband at a target site the defender rules in a Conquest.
  */
sealed trait CampaignPlanCost extends Product with Serializable
object CampaignPlanCost {
  final case class Favor(count: Int) extends CampaignPlanCost {
    require(count > 0, "Campaign favor cost must be positive")
  }
  final case class Secret(count: Int) extends CampaignPlanCost {
    require(count > 0, "Campaign secret cost must be positive")
  }
  final case class FavorBurnt(count: Int) extends CampaignPlanCost {
    require(count > 0, "Campaign burnt favor cost must be positive")
  }
  final case class SecretBurnt(count: Int) extends CampaignPlanCost {
    require(count > 0, "Campaign burnt secret cost must be positive")
  }
  case object SacrificeWarband extends CampaignPlanCost
}

/** What a chosen battle plan does once it is paid, in order. `RemoveAttackDice`
  * takes what the attack pool holds, up to the count, so a pool of two loses
  * two and an empty pool loses none. `Run` is the escape hatch for a plan whose
  * effect is not a dice change: it runs the operations as they are.
  */
sealed trait CampaignPlanEffect extends Product with Serializable
object CampaignPlanEffect {
  final case class AddAttackDice(count: Int) extends CampaignPlanEffect {
    require(count > 0, "added Campaign attack dice must be positive")
  }
  final case class RemoveAttackDice(count: Int) extends CampaignPlanEffect {
    require(count > 0, "removed Campaign attack dice must be positive")
  }
  final case class AddDefenseDice(count: Int) extends CampaignPlanEffect {
    require(count > 0, "added Campaign defense dice must be positive")
  }
  final case class Run(operations: Vector[Operation]) extends CampaignPlanEffect
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
  final case class SiteEdifice(siteId: SiteId, id: EdificeId)
      extends CampaignPlanSource {
    def stableKey: String = s"site-edifice:${siteId.value}:edifice:${id.value}"
  }
  final case class Relic(playerId: PlayerId, id: RelicId)
      extends CampaignPlanSource {
    def stableKey: String = s"relic:${playerId.value}:${id.value}"
  }
  final case class Title(playerId: PlayerId) extends CampaignPlanSource {
    def stableKey: String = s"title:${playerId.value}"
  }
}

/** One battle plan a power offers now: where it comes from, what it costs and
  * what it does. `label` is the words of the option when the source has no card
  * to name (the title). An offer says only that the plan is usable, never
  * whether its user can pay: the engine dry-runs the plan to learn that.
  *
  * The costs and effects must not depend on anything using the plan changes
  * (the resources it spends, the orientation of its card, the warbands it
  * moves), because the plan is rebuilt from a fresh offer whenever a walk
  * resumes inside it.
  */
final case class CampaignPlanOffer(source: CampaignPlanSource, label: String,
    costs: Vector[CampaignPlanCost], effects: Vector[CampaignPlanEffect])

/** An offer with the power that made it. */
final case class OfferedPlan(power: PowerId, offer: CampaignPlanOffer)

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
