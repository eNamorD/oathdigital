package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

object CrackingGroundCard extends Denizen(DenizenId("71"), "Cracking Ground", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.cracking-ground"),
    persistent = false, cost = Cost.free,
    text = "± [attack-die] per site targeted.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Cracking Ground (card 71), a battle plan for either side: "± [attack-die]
  * per site targeted."
  *
  * It is free. An attacker adds one attack die per site the Conquest targets;
  * a defender removes as many. A Raid targets no site, so it is not offered in
  * one. A bandit defender applies it at a site it rules.
  */
final case class CrackingGround private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = CrackingGround.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    val dice = context.setup.targetSites.size
    if dice == 0 then None
    else context.denizen(cardId).map(source => CampaignPlanOffer(source,
      PlanDice.label("Cracking Ground", context.side, dice), Vector.empty,
      Vector(PlanDice.effect(context.side, dice))))

object CrackingGround:
  val id: PowerId = PowerId("denizen.cracking-ground")

  def forCatalog(catalog: ExecutableCatalog): Option[CrackingGround] =
    CatalogCards.denizen(catalog, id).map(new CrackingGround(_))
