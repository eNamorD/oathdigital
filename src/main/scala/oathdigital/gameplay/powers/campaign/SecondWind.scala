package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.model._

object SecondWindCard extends Denizen(DenizenId("16"), "Second Wind", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.second-wind"),
    persistent = false, cost = Cost(secret = 1, favorBurnt = 1),
    text = "After you're victorious, you may travel and then may " +
      "campaign, spending no Supply for either.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Second Wind (card 16), an attacker's battle plan: "[secret] [favor-burnt]
  * After you're victorious, you may travel and then may campaign, spending no
  * Supply for either."
  *
  * Choosing it places a secret onto the card and burns a favor. A plan's cost
  * may be paid onto a card that already holds tokens, so a queued Campaign
  * may choose it again, and a chain ends only when its cost cannot be paid.
  * Once the Campaign has resolved, a win queues a Travel and then a Campaign,
  * each spending no Supply (follow-up actions design, rulings 9 and 11). The
  * two entries are independent: skipping the Travel still offers the
  * Campaign.
  *
  * Offered to the attacker only. A winning defender would be owed actions
  * off turn, which waits for the off-turn actor (see Sneak Attack).
  */
case object SecondWind extends BattlePlan:
  val cardId: DenizenId = SecondWindCard.id
  val id: PowerId = SecondWindCard.power.id

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Second Wind: travel, then campaign, for no Supply if victorious",
      Vector(CampaignPlanCost.Secret(1), CampaignPlanCost.FavorBurnt(1)),
      Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      if !use.won.contains(true) then Vector.empty
      else use.user.toVector.flatMap(user =>
        Vector(ActionRef.Travel, ActionRef.Campaign).map(action =>
          QueueProcedure(QueuedProcedure(user, action, Vector.empty,
            Some(PowerSourceRef.Card(cardId)), QueueKind.Offer))))))
