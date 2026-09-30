package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Walled Garden: a free, site-only defender's plan for one defense die per
  * beast card at any site, offered only when its site is a Conquest target. */
class WalledGardenSuite extends munit.FunSuite:
  private val card = cardWith("denizen.walled-garden")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))
  private val beast = inert(Suit.Beast, 1).head
  private val beastEdifice =
    catalog.edifices.find(_.suit == Suit.Beast).get.id.value

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
    val base = againstPlayer(board())
    val sites = others(base)
    val b = withEdifice(withSiteCard(withSiteCard(base, base.origin, card),
      sites(0), beast), sites(1), beastEdifice, EdificeSide.Ruined)
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.defensePool, 3)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("it is offered only when its site is a target"):
    val base = againstPlayer(board())
    val site = others(base).head
    val b = withSiteCard(on(base)(_.warbandsAt(site, base.other, 2)), site, card)
    assert(!commit(rules(winning), b, 3).offers(b.actor, b.other,
      CampaignIds.defenderPlan, ref))
    assert(commit(rules(winning), b, 5,
      targets = Vector(DecisionOptionRef.Site(site))).offers(b.actor, b.other,
      CampaignIds.defenderPlan, ref))

  test("a bandit defender applies it at a site it rules"):
    val base = board()
    val b = withSiteCard(withSiteCard(base, base.origin, card),
      others(base).head, beast)
    assertEquals(banditEffect(commit(rules(winning), b, 2)),
      Some(ModifyDicePool(CampaignIds.defensePool, 2)))
