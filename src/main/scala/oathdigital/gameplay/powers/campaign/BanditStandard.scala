package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{ExecutableCatalog, PrintedPower, Relic}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

object BanditStandardCard extends Relic(RelicId("R30"), "Bandit Standard", value = 55, defense = 3):
  val power = PrintedPower(PowerId("relic.bandit-standard"),
    persistent = false, cost = Cost.free,
    text = "+ [attack-die] for each bandit in your region. This " +
      "**cannot** be used while targeting sites ruled by bandits.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Bandit Standard (relic R30), an attacker's battle plan: "+ [attack-die] for
  * each bandit in your region. This cannot be used while targeting sites ruled
  * by bandits."
  *
  * The relic must be faceup in the attacker's play area. It is free and adds
  * one attack die per bandit warband at the sites of the region the attacker's
  * pawn stands in. It is not offered in a Conquest against bandits, nor when
  * that region holds no bandit. A Raid targets no site, so it may be used in
  * one.
  */
final case class BanditStandard private (relicId: RelicId) extends BattlePlan:
  def id: PowerId = BanditStandard.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Relic(relicId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    val againstBandits = context.setup.kind == CampaignKind.Conquest &&
      context.setup.defender == CampaignDefender.Bandits
    val dice = bandits(context)
    if againstBandits || dice == 0 then None
    else context.relic(relicId).map(source => CampaignPlanOffer(source,
      PlanDice.label("Bandit Standard", context.side, dice), Vector.empty,
      Vector(CampaignPlanEffect.AddAttackDice(dice))))

  /** The bandit warbands at the sites of the region of the attacker's pawn. */
  private def bandits(context: PlanContext): Int =
    val map = context.ready.game.current.map
    map.regionOf(context.setup.origin).toVector.flatMap(region =>
      map.inPlay.filter(site => map.regionOf(site).contains(region)))
      .flatMap(map.sites.get).map(_.forces match {
        case SiteForces.Occupied(ForceKind.Bandit, count) => count
        case _ => 0
      }).sum

object BanditStandard:
  val id: PowerId = PowerId("relic.bandit-standard")

  def forCatalog(catalog: ExecutableCatalog): Option[BanditStandard] =
    CatalogCards.relic(catalog, id).map(new BanditStandard(_))
