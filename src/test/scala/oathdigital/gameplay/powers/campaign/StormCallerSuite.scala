package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Storm Caller: a defender's free plan for two defense dice, and the card is
  * discarded after the Campaign whoever won. */
class StormCallerSuite extends munit.FunSuite:
  private val card = cardWith("denizen.storm-caller")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  private def discarded(state: OathState): Boolean = ready(state).game.current
    .commonCards.regionalDiscards.values.exists(_.contains(card))

  private def defending: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    withAdviserFor(base, base.rival, card, Orientation.FaceUp)

  test("a defender adds two defense dice, for nothing"):
    val b = defending
    val run = commit(rules(winning), b, 4)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.defensePool, 2)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("it is discarded after the Campaign, and says so"):
    val b = defending
    val done = commit(rules(winning), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assert(discarded(done.state))
    assertEquals(NoteText.said(StormCaller.id, Vector(PlanDiscard.discarded),
      done.events), Vector(NoteText.Said("discarded",
      "Discarded after the Campaign.", covers = false)))

  test("an attacker is not offered it"):
    val b = withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp)
    assert(awaits(commit(rules(winning), b, 2), b.actor, CampaignIds.sacrifice))

  test("a bandit defender applies it at its site, and it is discarded"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, card)
    val done = commit(rules(winning), b, 4).finishPlanWindows
    assert(done.ops.contains(ModifyDicePool(CampaignIds.defensePool, 2)))
    assert(discarded(done.state))
