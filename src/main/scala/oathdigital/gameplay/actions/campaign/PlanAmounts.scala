package oathdigital.gameplay.actions.campaign

import oathdigital.model._

/** The amounts battle plans ask for (`PlanAmount`), and the read of the
  * answer (catalog batch 7, slice 3). */
object PlanAmounts:
  /** An amount from `min` to `max`, asked as a `ChooseAmount`, each unit
    * costing `secretsEach` secrets. */
  def range(decisionId: String, min: Int, max: Int, heading: String,
      confirm: String, secretsEach: Int = 0)(
      effects: Int => Vector[CampaignPlanEffect]): PlanAmount =
    PlanAmount(decisionId,
      DecisionQuery.ChooseAmount(min, max, Some(heading), confirm), {
        case DecisionAnswer.ChooseAmountAnswer(amount) => Some(amount)
        case _ => None
      }, effects, secretsEach)

  /** The amount the plan's question was answered with in this walk. */
  def chosen(pending: PendingTree, amount: PlanAmount): Option[Int] =
    pending.answered.reverseIterator.collectFirst {
      case Answered(id, answer, _) if id == amount.decisionId => answer
    }.flatMap(amount.amountOf)
