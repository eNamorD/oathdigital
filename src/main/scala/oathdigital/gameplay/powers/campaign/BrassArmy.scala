package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Brass Army (relic R25), an attacker's battle plan: "[secret] +4 attack dice."
  *
  * The relic must be faceup in the attacker's play area. The secret is placed
  * onto the relic, which may already hold resources: a battle plan pays into an
  * occupied card. The dice join the attack pool but not the force, so no loss,
  * sacrifice or placement limit changes. The relic's other power, the pawn-move
  * restriction, is not part of this plan.
  */
final case class BrassArmy private (relicId: RelicId) extends BattlePlan {
  def id: PowerId = BrassArmy.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Relic(relicId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.relic(relicId).map(source => CampaignPlanOffer(source,
      "Brass Army: add 4 attack dice", Vector(CampaignPlanCost.Secret(1)),
      Vector(CampaignPlanEffect.AddAttackDice(BrassArmy.Dice))))
}

object BrassArmy {
  val id: PowerId = PowerId("relic.brass-army.campaign")
  val Dice: Int = 4

  def forCatalog(catalog: ExecutableCatalog): Option[BrassArmy] =
    CatalogCards.relic(catalog, id).map(new BrassArmy(_))
}
