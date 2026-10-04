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
private[campaign] object PlanPrice:
  def of(offer: CampaignPlanOffer, operations: Vector[CoreOperation])
      : OptionPrice =
    val paid = operations.foldLeft(OptionPrice()):
      case (price, PayCost(_, _, cost, _, _, _)) => price.copy(
        favor = price.favor + cost.favor,
        secrets = price.secrets + cost.secret,
        favorBurnt = price.favorBurnt + cost.favorBurnt,
        secretsBurnt = price.secretsBurnt + cost.secretBurnt)
      case (price, FlipSecrets(_, amount, SecretSide.FaceUp, SecretSide.FaceDown)) =>
        price.copy(secrets = price.secrets + amount)
      case (price, _) => price
    if offer.costs.contains(CampaignPlanCost.SacrificeWarband) then
      paid.copy(warbands = 1)
    else paid

  /** The option, carrying its price when it has one. */
  def priced(option: DecisionOption, offer: CampaignPlanOffer,
      operations: Vector[CoreOperation]): DecisionOption =
    val price = of(offer, operations)
    if price.isFree then option else DecisionOption.Priced(option, price)

  /** Why a plan's dry run could not pay for it, as player text. The payment
    * fails inside the operation pipeline, which reports only a code, so the
    * shortfall is read off the user's board against what the plan prints. A
    * surcharge a power adds is not printed on the plan; a payment that fails
    * only for it reads as the general line.
    */
  def shortfall(ready: ReadyGame, user: PlayerId, offer: CampaignPlanOffer,
      violation: OathViolation): String = violation match
    case OathViolation.InsufficientFavor(required, available) =>
      needs(required, "favor", available)
    case OathViolation.InsufficientSecrets(required, available) =>
      needs(required, secretWord(required), available)
    case _ =>
      val favor = offer.costs.collect {
        case CampaignPlanCost.Favor(count) => count
        case CampaignPlanCost.FavorBurnt(count) => count }.sum
      val secrets = offer.costs.collect {
        case CampaignPlanCost.Secret(count) => count
        case CampaignPlanCost.SecretBurnt(count) => count }.sum
      ready.game.current.players.find(_.player == user).map(_.board)
        .flatMap(board =>
          if board.favor < favor then
            Some(needs(favor, "favor", board.favor))
          else if board.faceUpSecrets < secrets then
            Some(needs(secrets, secretWord(secrets), board.faceUpSecrets))
          else None)
        .getOrElse("Cannot be paid for now")

  private def secretWord(count: Int): String =
    if count == 1 then "secret" else "secrets"

  private def needs(required: Int, what: String, available: Int): String =
    s"Needs $required $what; you have $available"
