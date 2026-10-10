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
    rangeBy(decisionId, (_, _) => Right(min -> max), heading, confirm,
      secretsEach)(effects)

  /** An amount whose bounds `bounds` reads from the state when the question
    * is asked: after the plan's fixed costs and any surcharge are paid, for
    * the plan's user. It is a violation when no amount is left. */
  def rangeBy(decisionId: String,
      bounds: (ReadyGame, PlayerId) => Either[OathViolation, (Int, Int)],
      heading: String, confirm: String, secretsEach: Int = 0)(
      effects: Int => Vector[CampaignPlanEffect]): PlanAmount =
    PlanAmount(decisionId, (ready, user) => bounds(ready, user).map(
      (min, max) => DecisionQuery.ChooseAmount(min, max, Some(heading),
        confirm)), {
        case DecisionAnswer.ChooseAmountAnswer(amount) => Some(amount)
        case _ => None
      }, effects, secretsEach)

  /** One of `choices`, each a button with its words, asked as a
    * `ChooseOne`. */
  def oneOf(decisionId: String, heading: String,
      choices: Vector[(Int, String)])(
      effects: Int => Vector[CampaignPlanEffect]): PlanAmount =
    PlanAmount(decisionId, (_, _) => Right(DecisionQuery.ChooseOne(
      choices.map { case (amount, words) =>
        DecisionOption.Button(button(amount), words) },
      heading = Some(heading))), {
      case DecisionAnswer.ChooseOneAnswer(ref) => choices.collectFirst {
        case (amount, _) if button(amount) == ref => amount }
      case _ => None
    }, effects)

  /** The button that answers `amount` in a `oneOf` question. */
  def button(amount: Int): DecisionOptionRef.Button =
    DecisionOptionRef.Button(s"amount-$amount")

  /** The amount the plan's question was answered with in this walk. */
  def chosen(pending: PendingTree, amount: PlanAmount): Option[Int] =
    pending.answered.reverseIterator.collectFirst {
      case Answered(id, answer, _) if id == amount.decisionId => answer
    }.flatMap(amount.amountOf)
