package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

object FireTalkersCard extends Denizen(DenizenId("31"), "Fire Talkers", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.fire-talkers"),
    persistent = false, cost = Cost(secret = 1),
    text = "±3 [attack-die] if you hold the Darkest Secret.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Fire Talkers (card 31), a battle plan for either side: "[secret] ±3
  * [attack-die] if you hold the Darkest Secret."
  *
  * A secret is placed onto the card. An attacker adds three attack dice; a
  * defender removes three. It is offered only while its user holds the
  * Darkest Secret, so a bandit defender never uses it.
  */
case object FireTalkers extends BattlePlan:
  val cardId: DenizenId = FireTalkersCard.id
  val id: PowerId = FireTalkersCard.power.id
  val Dice: Int = 3

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).filter(_ => holdsDarkestSecret(context))
      .map(source => CampaignPlanOffer(source,
        PlanDice.label("Fire Talkers", context.side, FireTalkers.Dice),
        Vector(CampaignPlanCost.Secret(1)),
        Vector(PlanDice.effect(context.side, FireTalkers.Dice))))

  private def holdsDarkestSecret(context: PlanContext): Boolean =
    context.user.exists(user => BannerRules.holder(context.ready.game.current,
      Banner.DarkestSecret).contains(user))
