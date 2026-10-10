package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.powerresolver.Transform
import oathdigital.model._

object CarefulPlansCard extends Denizen(DenizenId("254"), "Careful Plans",
    Suit.Order):
  val power = PrintedPower(PowerId("denizen.careful-plans"),
    persistent = false, cost = Cost(favor = 1),
    text = "The defender **must** roll their [defense-die] before the " +
      "attacker rolls their [attack-die].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Careful Plans (card 254), an attacker's battle plan: "The defender must
  * roll their defense dice before the attacker rolls their attack dice."
  *
  * One favor is placed onto the card. Its rewrite at the Campaign's root
  * folds `Late`, chosen or not, and finds the battle's five steps by the
  * windows of the two result steps: the attack roll, the attack result and
  * the sacrifice, then the defense roll and the defense result. It puts
  * them in one `Branch`, so the root keeps the same shape on every fold,
  * and the branch reads at walk time whether the plan was chosen. When it
  * was, the defense roll and its result come first, so the sacrifice is
  * decided with both rolls known. The defense result adds the defender's
  * force in either order. A root of any other shape is left as it is.
  *
  * It writes no line of its own.
  */
case object CarefulPlans extends BattlePlan:
  val cardId: DenizenId = CarefulPlansCard.id
  val id: PowerId = CarefulPlansCard.power.id
  /** The attack roll, the attack result and the sacrifice, which come
    * before the defense roll and its result. */
  private val AttackSteps = 3

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Careful Plans: the defender rolls first",
      Vector(CampaignPlanCost.Favor(1)), Vector.empty))

  override def rewrites: Map[PowerWindow, Transform] = Map(
    PowerWindow.CampaignActionEligibility -> Transform((ctx, children) =>
      reordered(ctx.activePlayer, children)))

  /** `children`, with the battle's steps in one branch. */
  private def reordered(actor: PlayerId, children: Vector[Operation])
      : Vector[Operation] =
    val attackResult = children.indexWhere(
      _.window.contains(PowerWindow.CampaignAttackResult))
    val defenseResult = children.indexWhere(
      _.window.contains(PowerWindow.CampaignDefenseResult))
    val start = attackResult - 1
    if start < 0 || defenseResult != start + AttackSteps + 1 then children
    else
      val battle = children.slice(start, defenseResult + 1)
      val (attack, defense) = battle.splitAt(AttackSteps)
      children.take(start) ++ Vector(Branch((ready, pending) =>
        if used(ready, pending, actor) then defense ++ attack
        else battle)) ++ children.drop(defenseResult + 1)

  private def used(ready: ReadyGame, pending: PendingTree, actor: PlayerId)
      : Boolean = PlanUse.chosen(ready, pending, actor, cardRef, sides,
    afterOutcome = false).nonEmpty
