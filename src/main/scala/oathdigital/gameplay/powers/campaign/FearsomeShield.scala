package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{ExecutableCatalog, PrintedPower, Relic}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

object FearsomeShieldCard extends Relic(RelicId("R27"), "Fearsome Shield", value = 52, defense = 1):
  val power = PrintedPower(PowerId("relic.fearsome-shield"),
    persistent = false, cost = Cost(secretBurnt = 2),
    text = "+2 [defense-die]")
  val powers: Vector[PrintedPower] = Vector(power)

/** Fearsome Shield (relic R27), a defender's battle plan: "[secret-burnt]
  * [secret-burnt] +2 defense dice."
  *
  * The relic must be faceup in the defender's play area. Two faceup secrets are
  * burnt to the shared bank, and nothing is placed on the relic. A defender with
  * fewer than two faceup secrets cannot pay, and the plan is not offered.
  */
final case class FearsomeShield private (relicId: RelicId) extends BattlePlan:
  def id: PowerId = FearsomeShield.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Relic(relicId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.relic(relicId).map(source => CampaignPlanOffer(source,
      "Fearsome Shield: burn 2 secrets for 2 defense dice",
      Vector(CampaignPlanCost.SecretBurnt(2)),
      Vector(CampaignPlanEffect.AddDefenseDice(2))))

object FearsomeShield:
  val id: PowerId = PowerId("relic.fearsome-shield")

  def forCatalog(catalog: ExecutableCatalog): Option[FearsomeShield] =
    CatalogCards.relic(catalog, id).map(new FearsomeShield(_))
