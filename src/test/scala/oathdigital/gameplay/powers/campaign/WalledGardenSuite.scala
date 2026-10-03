package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** Walled Garden: a free, site-only defender's plan for one defense die per
  * beast card at any site, offered only when its site is a Conquest target. */
class WalledGardenSuite extends munit.FunSuite:
  private val card = cardWith("denizen.walled-garden")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val beasts = planless("Errand Boy", "Small Friends")
  private val beast = beasts.head
  private val arcane = planlessCard("Golem Legions")
  // Beast, on either face: Hallowed Spring intact, Hiding Place ruined.
  private val beastEdifice = CatalogNames.edifice("Hallowed Spring")

  /** Sites other than the origin, in map order. */
  private def others(b: Board): Vector[SiteId] =
    b.ready.game.current.map.inPlay.filter(_ != b.origin)

  /** What the bandits' application of Walled Garden recorded as its effect:
    * the operation just before its marker. */
  private def banditEffect(run: Run): Option[CoreOperation] =
    val marker = ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)
    Option.when(run.ops.contains(marker))(run.ops.takeWhile(_ != marker).last)

  test("a defender adds one defense die per beast card at any site, " +
      "counting itself and cards nobody rules"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val sites = others(base)
    // A faceup non-beast card and a facedown beast card count nothing.
    val b = on(withSiteCard(withEdifice(withSiteCard(withSiteCard(base,
      base.origin, card), sites(0), beast), sites(1), beastEdifice,
      EdificeSide.Ruined), sites(2), arcane))(
      _.denizen(beasts(1), at = sites(3), facedown = true))
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.defensePool, 3)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("a beast edifice counts on either face"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withEdifice(withSiteCard(base, base.origin, card),
      others(base).head, beastEdifice, EdificeSide.Intact)
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.defensePool, 2)))

  test("it is offered only when its site is a target"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val site = others(base).head
    val b = withSiteCard(on(base)(_.warbandsAt(site, base.rival, 2)), site,
      card)
    assert(!commit(rules(winning), b, 3).offers(b.actor, b.rival,
      CampaignIds.defenderPlan, ref))
    assert(commit(rules(winning), b, 5,
      targets = Vector(DecisionOptionRef.Site(site))).offers(b.actor, b.rival,
      CampaignIds.defenderPlan, ref))

  test("a bandit defender applies it at a site it rules"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(withSiteCard(base, base.origin, card),
      others(base).head, beast)
    assertEquals(banditEffect(commit(rules(winning), b, 2)),
      Some(ModifyDicePool(CampaignIds.defensePool, 2)))
