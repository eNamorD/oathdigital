package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

object VillageConstableCard extends Denizen(DenizenId("132"), "Village Constable", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.village-constable"),
    persistent = false, cost = Cost.free,
    text = "±2 [attack-die] unless your enemy has the People's Favor.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Village Constable (card 132), a battle plan for either side: "±2
  * [attack-die] unless your enemy has the People's Favor."
  *
  * It is site-only and free, used by the ruler of its site. An attacker adds
  * two attack dice; a defender removes two. It is not offered while the enemy
  * holds the People's Favor. Bandits hold no banner, so it is always offered
  * against them, and a bandit defender applies it at a site it rules.
  */
final case class VillageConstable private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = VillageConstable.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => !context.enemyHolds(Banner.PeoplesFavor))
      .map(source => CampaignPlanOffer(source,
        PlanDice.label("Village Constable", context.side, VillageConstable.Dice),
        Vector.empty,
        Vector(PlanDice.effect(context.side, VillageConstable.Dice))))

object VillageConstable:
  val id: PowerId = PowerId("denizen.village-constable")
  val Dice: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[VillageConstable] =
    CatalogCards.denizen(catalog, id).map(new VillageConstable(_))
