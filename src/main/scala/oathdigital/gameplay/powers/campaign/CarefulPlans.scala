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
  * folds `Late`, chosen or not, and finds the battle's nine steps by the
  * windows of the two result steps: the attack roll, the attack result,
  * the Campaign's own attack line, the skull losses, the sacrifice and the
  * sacrifice losses, then the defense roll, the defense result and the
  * Campaign's own defense line. Each total's line sits after its result
  * window, so it travels with the half it belongs to. It
  * puts them in one `Branch`, so the root keeps the same shape on every
  * fold, and the branch reads at walk time
  * whether the plan was chosen. When it was, the defense roll and its
  * result come first, so the sacrifice is decided with both rolls known.
  * The skull losses still die right after the attack result, and the
  * sacrifice right after it is chosen. The defense result adds the
  * defender's force in either order. A root of any other shape is left as
  * it is.
  *
  * It writes no line of its own.
  */
case object CarefulPlans extends BattlePlan:
  val cardId: DenizenId = CarefulPlansCard.id
  val id: PowerId = CarefulPlansCard.power.id
  /** The attack roll, the attack result, the Campaign's attack line, the
    * skull losses, the sacrifice and the sacrifice losses, which come
    * before the defense roll and its result. The note leaf counts: this
    * slices the root's children by position, so a step added between the
    * two result windows is added here too. */
  private val AttackSteps = 6

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Careful Plans: the defender rolls first",
      Vector(CampaignPlanCost.Favor(1)), Vector.empty))

  override def rewrites: Map[PowerWindow, Transform] = Map(
    PowerWindow.CampaignActionEligibility -> Transform((ctx, children) =>
      reordered(ctx.actor, children)))

  /** `children`, with the battle's steps in one branch. */
  private def reordered(actor: PlayerId, children: Vector[Operation])
      : Vector[Operation] =
    val attackResult = children.indexWhere(
      _.window.contains(PowerWindow.CampaignAttackResult))
    val defenseResult = children.indexWhere(
      _.window.contains(PowerWindow.CampaignDefenseResult))
    val start = attackResult - 1
    // The defense line is the child after the defense result, as the
    // attack line is the child after the attack result.
    val last = defenseResult + 1
    if start < 0 || defenseResult != start + AttackSteps + 1 ||
        last >= children.size then children
    else
      val battle = children.slice(start, last + 1)
      val (attack, defense) = battle.splitAt(AttackSteps)
      children.take(start) ++ Vector(Branch((ready, pending) =>
        if used(ready, pending, actor) then defense ++ attack
        else battle)) ++ children.drop(last + 1)

  private def used(ready: ReadyGame, pending: PendingTree, actor: PlayerId)
      : Boolean = PlanUse.chosen(ready, pending, actor, cardRef, sides,
    afterOutcome = false).nonEmpty
