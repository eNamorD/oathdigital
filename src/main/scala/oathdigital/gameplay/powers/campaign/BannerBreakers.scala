package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Banner Breakers (card 222), an attacker's battle plan: "[favor]
  * [favor-burnt] +3 [attack-die] if the defender has the Darkest Secret or
  * People's Favor."
  *
  * A favor is placed onto the card and another is burnt. It adds three attack
  * dice, and is offered only while the defender holds either banner, so never
  * against bandits.
  */
final case class BannerBreakers private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = BannerBreakers.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).filter(_ => Banner.all.exists(context.enemyHolds))
      .map(source => CampaignPlanOffer(source,
        PlanDice.label("Banner Breakers", context.side, BannerBreakers.Dice),
        Vector(CampaignPlanCost.Favor(1), CampaignPlanCost.FavorBurnt(1)),
        Vector(CampaignPlanEffect.AddAttackDice(BannerBreakers.Dice))))

object BannerBreakers:
  val id: PowerId = PowerId("denizen.banner-breakers")
  val Dice: Int = 3

  def forCatalog(catalog: ExecutableCatalog): Option[BannerBreakers] =
    CatalogCards.denizen(catalog, id).map(new BannerBreakers(_))
