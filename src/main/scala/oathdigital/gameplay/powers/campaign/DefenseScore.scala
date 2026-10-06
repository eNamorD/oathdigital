package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignIds,
  CampaignSetup}
import oathdigital.model._

/** How the plans chosen in a Campaign score its defense (catalog batch 4
  * rulings, "The defense score"). The defaults are the printed rules.
  *
  *  - `singleShieldsIgnored`: a single shield scores nothing (Rain Boots, Bag
  *    of Siegeworks).
  *  - `doubleShieldsIgnored`: two shields score nothing (War Tortoise used by
  *    the attacker).
  *  - `forceTwice`: the defender's force is added twice (Garrison Armory).
  */
final case class DefenseRules(singleShieldsIgnored: Boolean = false,
    doubleShieldsIgnored: Boolean = false, forceTwice: Boolean = false)

/** One plan's change to how the defense is scored: the plan's source as its
  * decision names it, the side that must have chosen it, and the change. */
final case class DefenseChange(ref: DecisionOptionRef, side: CampaignPlanSide,
    change: DefenseRules => DefenseRules)

/** The defense scorer (catalog batch 4 design, R2): the faces the plans
  * filter out score nothing, the doublers multiply what is left, and the
  * defender's force is added, undoubled. Every plan that changes the defense
  * writes the total from every chosen plan in `changes`, after the window's
  * own step has written the printed score, so their fold order does not
  * matter.
  */
object DefenseScore:
  /** The dice's score once the chosen plans have filtered the faces. */
  def dice(faces: Vector[DefenseDieFace], rules: DefenseRules): Int =
    DefenseDieFace.score(faces.filterNot {
      case DefenseDieFace.OneShield => rules.singleShieldsIgnored
      case DefenseDieFace.TwoShields => rules.doubleShieldsIgnored
      case _ => false
    })

  /** The defense: the dice's score plus the defender's force. */
  def of(faces: Vector[DefenseDieFace], force: Int, rules: DefenseRules): Int =
    dice(faces, rules) + force * (if rules.forceTwice then 2 else 1)

  private def singleShields(rules: DefenseRules): DefenseRules =
    rules.copy(singleShieldsIgnored = true)

  /** Every plan that changes how the defense is scored. */
  val changes: Vector[DefenseChange] = Vector(
    DefenseChange(DecisionOptionRef.Denizen(RainBootsCard.id),
      CampaignPlanSide.Attacker, singleShields),
    DefenseChange(DecisionOptionRef.Relic(BagOfSiegeworksCard.id),
      CampaignPlanSide.Attacker, singleShields),
    DefenseChange(DecisionOptionRef.Denizen(GarrisonArmoryCard.id),
      CampaignPlanSide.Defender, _.copy(forceTwice = true)),
    DefenseChange(DecisionOptionRef.Denizen(WarTortoiseCard.id),
      CampaignPlanSide.Attacker, _.copy(doubleShieldsIgnored = true)))

  /** The rules of the plans chosen in `actor`'s Campaign. */
  def rules(ready: ReadyGame, pending: PendingTree, actor: PlayerId)
      : DefenseRules =
    changes.filter(change => PlanUse.chosen(ready, pending, actor, change.ref,
      Set(change.side), afterOutcome = false).nonEmpty)
      .foldLeft(DefenseRules())((rules, change) => change.change(rules))

  /** The faces the defense pool rolled. */
  def faces(ready: ReadyGame): Vector[DefenseDieFace] =
    ready.game.current.rollOutcomes.get(CampaignIds.defensePool).toVector
      .flatMap(_.faces.collect { case face: DefenseDieFace => face })

  /** Writes the defense as every chosen plan scores it over the recorded
    * one. Nothing is written when the recorded score already stands. */
  def rescore(actor: PlayerId): Operation = BuildOps((ready, pending) =>
    CampaignSetup.setup(ready, actor, pending).toRight(
      OathViolation.InvalidEventOrder(
        "the defense was scored again without a Campaign setup")).map { setup =>
      val total = of(faces(ready), CampaignBattle.defenderForce(ready, setup),
        rules(ready, pending, actor))
      val recorded = ready.game.current.rollOutcomes
        .get(CampaignIds.defensePool).fold(0)(_.score)
      Option.when(total != recorded)(ModifyRollOutcome(CampaignIds.defensePool,
        None, Some(total))).toVector
    })
