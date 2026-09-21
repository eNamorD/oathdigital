package oathdigital.gameplay.actions.campaign

import oathdigital.model._

/** What a battle plan costs its user, for the option that offers it. The payments
  * are read from the operations a dry run of the plan's application recorded,
  * not from the offer, so a power that adds to the cost (a surcharge on the
  * enemy's plans) shows in it: a payment is a `PayCost` whether it is placed or
  * burnt, and a secret paid by turning it facedown is a `FlipSecrets`.
  *
  * A warband sacrifice is read from the offer. The dry run stops at the decision
  * that asks which force pays, before the warband is killed, so it may not have
  * recorded the kill yet.
  */
private[campaign] object PlanPrice {
  def of(offer: CampaignPlanOffer, operations: Vector[CoreOperation])
      : OptionPrice = {
    val paid = operations.foldLeft(OptionPrice()) {
      case (price, PayCost(_, _, cost, _, _, _)) => price.copy(
        favor = price.favor + cost.favor,
        secrets = price.secrets + cost.secret,
        favorBurnt = price.favorBurnt + cost.favorBurnt,
        secretsBurnt = price.secretsBurnt + cost.secretBurnt)
      case (price, FlipSecrets(_, amount, SecretSide.FaceUp, SecretSide.FaceDown)) =>
        price.copy(secrets = price.secrets + amount)
      case (price, _) => price
    }
    if (offer.costs.contains(CampaignPlanCost.SacrificeWarband))
      paid.copy(warbands = 1)
    else paid
  }

  /** The option, carrying its price when it has one. */
  def priced(option: DecisionOption, offer: CampaignPlanOffer,
      operations: Vector[CoreOperation]): DecisionOption = {
    val price = of(offer, operations)
    if (price.isFree) option else DecisionOption.Priced(option, price)
  }
}
