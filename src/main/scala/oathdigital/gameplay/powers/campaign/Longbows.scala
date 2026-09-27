package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Longbows (card 4), a battle plan for either side: "± [attack-die]".
  *
  * It is free. An attacker adds one attack die; a defender removes one. A
  * bandit defender applies it when it stands faceup at a site the bandits
  * rule, as it applies every free plan.
  */
final case class Longbows private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = Longbows.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      PlanDice.label("Longbows", context.side, Longbows.Dice), Vector.empty,
      Vector(PlanDice.effect(context.side, Longbows.Dice))))

object Longbows:
  val id: PowerId = PowerId("denizen.longbows")
  val Dice: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[Longbows] =
    CatalogCards.denizen(catalog, id).map(new Longbows(_))
