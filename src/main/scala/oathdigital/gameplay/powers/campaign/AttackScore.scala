package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignIds,
  CampaignSetup}
import oathdigital.model._

/** What a hollow sword is worth: half a sword, counted in pairs and rounded
  * down; a whole sword with Fae Battalion; nothing with Rusting Ray. */
enum HollowWorth:
  case Half, Whole, Zero

/** How the plans chosen in a Campaign score its attack (catalog batch 4
  * rulings, "The attack score"). The defaults are the printed rules.
  *
  *  - `hollow`: what a hollow sword is worth.
  *  - `skullSwords`: the swords a two-swords-and-skull face is worth, 4 with
  *    Fearsome General.
  *  - `doubled`: Lancers doubles the count of every face rolled, skulls
  *    included, before the skull cap.
  *  - `skullsIgnored`: no skull kills a warband, and every skull face scores
  *    its swords (Outriders, The Great Levy, Kindred Warriors, Rangers).
  *  - `skullFacesScoreNothing`: War Tortoise used by the defender. Payable
  *    skulls still kill.
  *  - `skullKills`: the warbands each skull kills, 2 with the defender's
  *    Weeping Banner. The kills are capped by the force, and a skull face
  *    scores only when all of its kills are paid.
  */
final case class AttackRules(hollow: HollowWorth = HollowWorth.Half,
    skullSwords: Int = 2, doubled: Boolean = false,
    skullsIgnored: Boolean = false, skullFacesScoreNothing: Boolean = false,
    skullKills: Int = 1)

/** One plan's change to how the attack is scored: the plan's source as its
  * decision names it, the side that must have chosen it, and the change. */
final case class AttackChange(ref: DecisionOptionRef, side: CampaignPlanSide,
    change: AttackRules => AttackRules)

/** The attack scorer (catalog batch 4 design, R1). Every plan that changes
  * the attack writes the score computed from the faces, the committed force
  * and every chosen plan in `changes`, so the plans write the same total
  * whichever order their transforms fold in. With no plan chosen it scores
  * as `CampaignBattle.attackResult` does, which the window's own step
  * writes first.
  */
object AttackScore:
  /** The attack's score and the skulls it loses, in that order. The
    * sacrifice is added later and is never doubled. */
  def of(faces: Vector[AttackDieFace], force: Int, rules: AttackRules)
      : (Int, Int) =
    val times = if rules.doubled then 2 else 1
    def count(face: AttackDieFace): Int = faces.count(_ == face) * times
    val hollow = count(AttackDieFace.HollowSword)
    val skulls = count(AttackDieFace.TwoSwordsSkull)
    val lost =
      if rules.skullsIgnored then 0
      else math.min(skulls * rules.skullKills, force)
    val scoring =
      if rules.skullsIgnored then skulls
      else math.min(skulls, force / rules.skullKills)
    val hollowScore = rules.hollow match
      case HollowWorth.Half => hollow / 2
      case HollowWorth.Whole => hollow
      case HollowWorth.Zero => 0
    val skullScore =
      if rules.skullFacesScoreNothing then 0 else scoring * rules.skullSwords
    (count(AttackDieFace.OneSword) + hollowScore + skullScore, lost)

  private def ignored(rules: AttackRules): AttackRules =
    rules.copy(skullsIgnored = true)

  /** Every plan that changes how the attack is scored. */
  val changes: Vector[AttackChange] = Vector(
    AttackChange(DecisionOptionRef.Denizen(OutridersCard.id),
      CampaignPlanSide.Attacker, ignored),
    AttackChange(DecisionOptionRef.Denizen(TheGreatLevyCard.id),
      CampaignPlanSide.Attacker, ignored),
    AttackChange(DecisionOptionRef.Denizen(FaeBattalionCard.id),
      CampaignPlanSide.Attacker, _.copy(hollow = HollowWorth.Whole)),
    AttackChange(DecisionOptionRef.Denizen(RustingRayCard.id),
      CampaignPlanSide.Defender, _.copy(hollow = HollowWorth.Zero)),
    AttackChange(DecisionOptionRef.Denizen(LancersCard.id),
      CampaignPlanSide.Attacker, _.copy(doubled = true)),
    AttackChange(DecisionOptionRef.Denizen(FearsomeGeneralCard.id),
      CampaignPlanSide.Attacker, _.copy(skullSwords = 4)),
    AttackChange(DecisionOptionRef.Denizen(WarTortoiseCard.id),
      CampaignPlanSide.Defender, _.copy(skullFacesScoreNothing = true)),
    AttackChange(DecisionOptionRef.Denizen(KindredWarriorsCard.id),
      CampaignPlanSide.Attacker, ignored),
    AttackChange(DecisionOptionRef.Denizen(RangersCard.id),
      CampaignPlanSide.Attacker, ignored),
    AttackChange(DecisionOptionRef.Relic(WeepingBannerCard.id),
      CampaignPlanSide.Defender, _.copy(skullKills = 2)))

  /** The rules of the plans chosen in `actor`'s Campaign. */
  def rules(ready: ReadyGame, pending: PendingTree, actor: PlayerId)
      : AttackRules =
    changes.filter(change => PlanUse.chosen(ready, pending, actor, change.ref,
      Set(change.side), afterOutcome = false).nonEmpty)
      .foldLeft(AttackRules())((rules, change) => change.change(rules))

  /** What one plan added to the attack: the score with every chosen plan,
    * less the score once `without` undoes that plan's change. Zero without
    * a Campaign setup. */
  def addedBy(ready: ReadyGame, pending: PendingTree, actor: PlayerId,
      without: AttackRules => AttackRules): Int =
    CampaignSetup.setup(ready, actor, pending).fold(0) { setup =>
      val faces = CampaignBattle.attackFacesOf(ready)
      val chosen = rules(ready, pending, actor)
      of(faces, setup.force, chosen)._1 -
        of(faces, setup.force, without(chosen))._1
    }

  /** Writes the attack as every chosen plan scores it over the recorded one.
    * Nothing is written when the attack never rolled, or when the recorded
    * score and skull loss already stand. */
  def rescore(actor: PlayerId): Operation = BuildOps((ready, pending) =>
    CampaignSetup.setup(ready, actor, pending).toRight(
      OathViolation.InvalidEventOrder(
        "the attack was scored again without a Campaign setup")).map { setup =>
      ready.game.current.rollOutcomes.get(CampaignIds.attackPool).toVector
        .flatMap { recorded =>
          val (score, lost) = of(CampaignBattle.attackFacesOf(ready),
            setup.force, rules(ready, pending, actor))
          Option.when(score != recorded.score || lost != recorded.skulls)(
            ModifyRollOutcome(CampaignIds.attackPool, Some(lost), Some(score)))
        }
    })

  /** Whether the attack rolled a hollow sword. */
  def hollowRolled(ready: ReadyGame): Boolean =
    CampaignBattle.attackFacesOf(ready).contains(AttackDieFace.HollowSword)

  /** Whether the attack rolled a skull face. */
  def skullRolled(ready: ReadyGame): Boolean =
    AttackDieFace.skulls(CampaignBattle.attackFacesOf(ready)) > 0
