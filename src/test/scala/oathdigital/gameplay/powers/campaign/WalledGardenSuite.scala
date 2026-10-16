package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** Walled Garden: a free, site-only defender's plan for one defense die per
  * beast card at any site, offered only when its site is a Conquest target. */
class WalledGardenSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Walled Garden")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val beasts = planless("Bracken", "Autumn Wind")
  private val beast = beasts.head
  private val arcane = planlessCard("Glamor")
  // Beast, on either face: Hallowed Spring intact, Hiding Place ruined.
  private val beastEdifice = CatalogNames.edifice("Hallowed Spring")

  // Sites other than the origin, Ancient City, in map order.
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  private val buriedGiant = CatalogNames.site("Buried Giant")
  private val deepWoods = CatalogNames.site("Deep Woods")
  private val desolateShore = CatalogNames.site("Desolate Shore")

  /** What the bandits' application of Walled Garden recorded as its effect:
    * the operation just before its marker. */
  private def banditEffect(run: Run): Option[CoreOperation] =
    val marker = ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)
    Option.when(run.ops.contains(marker))(run.ops.takeWhile(_ != marker).last)

  test("a defender adds one defense die per beast card at any site, " +
      "counting itself and cards nobody rules"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    // Walled Garden at the origin, a beast denizen and a ruined beast
    // edifice count; a faceup non-beast card and a facedown beast card
    // count nothing.
    val atOrigin = withSiteCard(base, base.origin, card)
    val beastsOut = withEdifice(withSiteCard(atOrigin, brokenPeaks, beast),
      buriedGiant, beastEdifice, EdificeSide.Ruined)
    val b = on(withSiteCard(beastsOut, deepWoods, arcane))(
      _.denizen(beasts(1), at = desolateShore, facedown = true))
    val run = commit(rules(winningDice), b, 3)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(
      picked.since(run).contains(ModifyDicePool(CampaignIds.defensePool, 3)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("a beast edifice counts on either face"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withEdifice(withSiteCard(base, base.origin, card),
      brokenPeaks, beastEdifice, EdificeSide.Intact)
    val run = commit(rules(winningDice), b, 3)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(
      picked.since(run).contains(ModifyDicePool(CampaignIds.defensePool, 2)))

  test("it is offered only when its site is a target"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val site = brokenPeaks
    val b = withSiteCard(on(base)(_.warbandsAt(site, base.rival, 2)), site,
      card)
    assert(!commit(rules(winningDice), b, 3).offers(b.actor, b.rival,
      CampaignIds.defenderPlan, ref))
    assert(commit(rules(winningDice), b, 5,
      targets = Vector(DecisionOptionRef.Site(site))).offers(b.actor, b.rival,
      CampaignIds.defenderPlan, ref))

  test("a bandit defender applies it at a site it rules"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(withSiteCard(base, base.origin, card),
      brokenPeaks, beast)
    assertEquals(banditEffect(commit(rules(winningDice), b, 2)),
      Some(ModifyDicePool(CampaignIds.defensePool, 2)))
