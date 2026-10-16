package oathdigital.model

enum CampaignKind(val key: String):
  case Conquest extends CampaignKind("conquest")
  case Raid extends CampaignKind("raid")

sealed trait CampaignRaidTarget extends Product with Serializable:
  def playerId: PlayerId
  def stableKey: String
  private[model] def canonicalOrder: (Int, String)
object CampaignRaidTarget:
  final case class Pawn(playerId: PlayerId) extends CampaignRaidTarget:
    def stableKey: String = s"pawn:${playerId.value}"
    private[model] def canonicalOrder = 0 -> playerId.value
  final case class Relic(playerId: PlayerId, relicId: RelicId)
      extends CampaignRaidTarget:
    def stableKey: String = s"relic:${playerId.value}:${relicId.value}"
    private[model] def canonicalOrder = 1 -> s"${playerId.value}:${relicId.value}"
  final case class Banner(playerId: PlayerId, banner: oathdigital.model.Banner)
      extends CampaignRaidTarget:
    def stableKey: String = s"banner:${playerId.value}:${banner.key}"
    private[model] def canonicalOrder =
      (2 + oathdigital.model.Banner.all.indexOf(banner)) -> playerId.value

  def canonical(targets: Iterable[CampaignRaidTarget]): Vector[CampaignRaidTarget] =
    targets.toVector.sortBy(_.canonicalOrder)

sealed trait CampaignDefender extends Product with Serializable
object CampaignDefender:
  case object Bandits extends CampaignDefender
  final case class Player(playerId: PlayerId) extends CampaignDefender

/** Which side of a Campaign a battle plan belongs to. */
enum CampaignPlanSide { case Attacker, Defender }

/** What using a battle plan costs its user. `Favor` and `Secret` are placed onto
  * the plan's source card, which may already hold resources; `FavorBurnt` and
  * `SecretBurnt` leave play to the shared bank. `SacrificeWarband` kills one
  * warband of the user's own force, and only a defender may pay it: the board
  * in a Raid, a warband at a target site the defender rules in a Conquest.
  */
sealed trait CampaignPlanCost extends Product with Serializable
object CampaignPlanCost:
  final case class Favor(count: Int) extends CampaignPlanCost:
    require(count > 0, "Campaign favor cost must be positive")
  final case class Secret(count: Int) extends CampaignPlanCost:
    require(count > 0, "Campaign secret cost must be positive")
  final case class FavorBurnt(count: Int) extends CampaignPlanCost:
    require(count > 0, "Campaign burnt favor cost must be positive")
  final case class SecretBurnt(count: Int) extends CampaignPlanCost:
    require(count > 0, "Campaign burnt secret cost must be positive")
  case object SacrificeWarband extends CampaignPlanCost

/** What a chosen battle plan does once it is paid, in order. `RemoveAttackDice`
  * takes what the attack pool holds, up to the count, so a pool of two loses
  * two and an empty pool loses none. `Run` is the escape hatch for a plan whose
  * effect is not a dice change: it runs the operations as they are.
  */
sealed trait CampaignPlanEffect extends Product with Serializable
object CampaignPlanEffect:
  final case class AddAttackDice(count: Int) extends CampaignPlanEffect:
    require(count > 0, "added Campaign attack dice must be positive")
  final case class RemoveAttackDice(count: Int) extends CampaignPlanEffect:
    require(count > 0, "removed Campaign attack dice must be positive")
  final case class AddDefenseDice(count: Int) extends CampaignPlanEffect:
    require(count > 0, "added Campaign defense dice must be positive")
  final case class Run(operations: Vector[Operation]) extends CampaignPlanEffect

sealed trait CampaignPlanSource extends Product with Serializable:
  def stableKey: String
  /** How a decision option names the source: its card, or the title's
    * button. */
  def ref: DecisionOptionRef
object CampaignPlanSource:
  final case class Adviser(playerId: PlayerId, id: DenizenId)
      extends CampaignPlanSource:
    def stableKey: String = s"adviser:${playerId.value}:denizen:${id.value}"
    def ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  final case class SiteCard(siteId: SiteId, id: DenizenId)
      extends CampaignPlanSource:
    def stableKey: String = s"site-card:${siteId.value}:denizen:${id.value}"
    def ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  final case class SiteEdifice(siteId: SiteId, id: EdificeId)
      extends CampaignPlanSource:
    def stableKey: String = s"site-edifice:${siteId.value}:edifice:${id.value}"
    def ref: DecisionOptionRef = DecisionOptionRef.Edifice(id)
  final case class Relic(playerId: PlayerId, id: RelicId)
      extends CampaignPlanSource:
    def stableKey: String = s"relic:${playerId.value}:${id.value}"
    def ref: DecisionOptionRef = DecisionOptionRef.Relic(id)
  final case class Title(playerId: PlayerId) extends CampaignPlanSource:
    def stableKey: String = s"title:${playerId.value}"
    def ref: DecisionOptionRef = DecisionOptionRef.Button("title")

/** The amount a battle plan asks its user for as it is applied (catalog
  * batch 7, slice 3). The plan's application asks `query` after the plan's
  * fixed costs, its sacrifice and its fixed effects, so a dry run of the
  * plan still pays what is fixed and stops at the question. `amountOf`
  * reads the amount from the answer, `secretsEach` is what each unit costs,
  * placed on the plan's card, and `effects` are what that amount does. A
  * bandit defender is asked nothing, so its plan does nothing more.
  *
  * `query` is built when the question is asked, from the state after what
  * the plan and any surcharge have paid already, so its bounds never
  * promise more than the user can still pay. It is a violation when no
  * amount is left to ask for, and the plan is then refused, as a plan that
  * cannot pay is.
  */
final case class PlanAmount(decisionId: String,
    query: (ReadyGame, PlayerId) => Either[OathViolation, DecisionQuery],
    amountOf: DecisionAnswer => Option[Int],
    effects: Int => Vector[CampaignPlanEffect], secretsEach: Int = 0)

/** One battle plan a power offers now: where it comes from, what it costs and
  * what it does. `label` is the words of the option when the source has no card
  * to name (the title). An offer says only that the plan is usable, never
  * whether its user can pay: the engine dry-runs the plan to learn that.
  *
  * The costs and effects must not depend on anything using the plan changes
  * (the resources it spends, the orientation of its card, the warbands it
  * moves), because the plan is rebuilt from a fresh offer whenever a walk
  * resumes inside it.
  *
  * `sides` is who may use the plan, copied in by `BattlePlan` from its own
  * declaration rather than written by each power: a power already says which
  * windows it offers at, and repeating that in every `plan` body is how the
  * two would drift.
  *
  * `withheld` is why the plan stands but cannot be used now: its own rule,
  * such as Encirclement's force rule, or another power's restriction, which
  * the walker writes in when it gathers the window. The plan window shows it
  * unavailable with that reason, and no one applies it.
  *
  * `alone` marks a plan its user may use with no other (Code of Honor). A
  * player's window withholds the rest through the power's own restrictions.
  * A bandit defender, which chooses nothing, applies such a plan and no
  * other when it would apply it (FAQ).
  *
  * `amount` is the question the plan asks before what depends on its
  * answer (Unstable Summon's X). The question's bounds are read when it is
  * asked, after the plan's fixed costs and any surcharge are paid (see
  * `PlanAmount`).
  *
  * `warning` is why a plan its user can pay for deserves a second look
  * (Zealots, whose condition fails). The option shows it and stays
  * choosable.
  */
final case class CampaignPlanOffer(source: CampaignPlanSource, label: String,
    costs: Vector[CampaignPlanCost], effects: Vector[CampaignPlanEffect],
    sides: Set[CampaignPlanSide] = Set.empty,
    withheld: Option[OptionReason] = None, alone: Boolean = false,
    amount: Option[PlanAmount] = None, warning: Option[OptionReason] = None)

/** An offer with the power that made it. */
final case class OfferedPlan(power: PowerId, offer: CampaignPlanOffer)

/** Where a Campaign's force stood when its result was recorded, for a
  * Campaign with a queued origin whose warbands joined it (catalog batch
  * 8, slice 13): the actor's warbands at the origin and on their board.
  * The losses split the dead between the two. */
final case class OriginForce(site: SiteId, atSite: Int, onBoard: Int)

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
  * `sacrificeWorth` is what each sacrificed warband adds to the attack, three
  * with Zealots.
  * A plan that decides the battle (Peace Envoy) sets it whatever the
  * scores, with nothing rolled.
  * `originForce` records where the force stood when a queued origin's
  * warbands joined it, so the losses split the dead between board and site.
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
    attackerWins: Boolean,
    sacrificeWorth: Int = 1,
    originForce: Option[OriginForce] = None
):
  def attackTotal: Int = attackScore + sacrificed * sacrificeWorth
