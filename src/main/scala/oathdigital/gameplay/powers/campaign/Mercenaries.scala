package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Mercenaries (card 12), a battle plan for either side: "[favor] +-3 attack
  * dice. If you are defeated while using this power, discard Mercenaries."
  *
  * It costs a favor, placed onto the card. An attacker adds three dice to the
  * attack pool. A defender removes three from it, and the pool never goes below
  * zero. The sign is fixed by the side, so a player cannot choose the other one
  * (a deferred rule). When the user is defeated, the card is discarded by the
  * standard denizen discard once the Campaign has resolved, so its favor returns
  * to the bank.
  */
final case class Mercenaries private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan {
  def id: PowerId = Mercenaries.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map { source =>
      val (label, effect) = context.side match {
        case CampaignPlanSide.Attacker => "Mercenaries: add 3 attack dice" ->
          CampaignPlanEffect.AddAttackDice(Mercenaries.Dice)
        case CampaignPlanSide.Defender => "Mercenaries: remove 3 attack dice" ->
          CampaignPlanEffect.RemoveAttackDice(Mercenaries.Dice)
      }
      CampaignPlanOffer(source, label, Vector(CampaignPlanCost.Favor(1)),
        Vector(effect))
    }

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      if (!use.won.contains(false)) Vector.empty
      else use.user.toVector.map(PlanDiscard.denizen(catalog, _, cardId))))
}

object Mercenaries {
  val id: PowerId = PowerId("denizen.mercenaries")
  val Dice: Int = 3

  def forCatalog(catalog: ExecutableCatalog): Option[Mercenaries] =
    CatalogCards.denizen(catalog, id).map(new Mercenaries(_, catalog))
}
