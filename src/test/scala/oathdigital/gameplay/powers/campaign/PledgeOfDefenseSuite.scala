package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Pledge of Defense: a free defender's plan for one defense die per nomad
  * card its user rules, counting itself, and the card is discarded after the
  * Campaign. */
class PledgeOfDefenseSuite extends munit.FunSuite:
  private val card = cardWith("denizen.pledge-of-defense")
  private val id = card
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val nomads = planless("Convoys", "Vow of Kinship")
  private val line = NoteText.Said("discarded", "Discarded after the Campaign.",
    covers = false)

  private def discarded(state: OathState): Boolean = ready(state).game.current
    .commonCards.regionalDiscards.values.exists(_.contains(id))

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(PledgeOfDefense.id, Vector(PlanDiscard.discarded), run.events)

  /** What the bandits' application of Pledge of Defense recorded as its
    * effect: the operation just before its marker. */
  private def banditEffect(run: Run): Option[CoreOperation] =
    val marker = ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)
    Option.when(run.ops.contains(marker))(run.ops.takeWhile(_ != marker).last)

  test("a defender adds one defense die per nomad card they rule, counting " +
      "itself, and it is discarded"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    // The defender rules the origin, where a nomad card stands.
    val b = withSiteCard(withAdviserFor(withAdviserFor(base, base.rival, card,
      Orientation.FaceUp), base.rival, nomads(0), Orientation.FaceUp),
      base.origin, nomads(1))
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.defensePool, 3)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))
    val done = picked.finishPlanWindows
    assert(discarded(done.state))
    assertEquals(lines(done), Vector(line))

  test("a bandit defender counts the nomad cards at every site bandits " +
      "rule, and it is discarded"):
    val base = board(extras = 1, warbands = 5, supply = 7)
    val b = withSiteCard(withSiteCard(base, base.origin, card),
      base.extras.head, nomads(0))
    val done = commit(rules(winning), b, 2).finishPlanWindows
    assertEquals(banditEffect(done),
      Some(ModifyDicePool(CampaignIds.defensePool, 2)))
    assert(discarded(done.state))
    assertEquals(lines(done), Vector(line))

  test("a facedown nomad plan the defender chose first is revealed and " +
      "counts, and one chosen after does not"):
    val crusade = cardWith("denizen.great-crusade")
    val crusadeRef: DecisionOptionRef = DecisionOptionRef.Denizen(crusade)
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withAdviserFor(withAdviserFor(base, base.rival, card,
      Orientation.FaceUp), base.rival, crusade, Orientation.FaceDown)
    val run = commit(rules(winning), b, 3)
    val crusadeFirst = run.pick(b.rival, CampaignIds.defenderPlan, crusadeRef)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(crusadeFirst.since(run)
      .contains(ModifyDicePool(CampaignIds.defensePool, 2)))
    val pledgeFirst = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(pledgeFirst.since(run)
      .contains(ModifyDicePool(CampaignIds.defensePool, 1)))
