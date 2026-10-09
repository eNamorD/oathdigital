package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Specialist: two favor placed so that the defender cannot use battle
  * plans. */
class SpecialistSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Specialist")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val longbows = CatalogNames.denizen("Longbows")
  private val longbowsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(longbows)

  /** `b`, with the actor holding Specialist faceup and two favor. */
  private def withSpecialist(b: Board): Board =
    on(withAdviser(b, card, Orientation.FaceUp))(_.favor(b.actor, 2))

  /** The rival rules the origin with two warbands and the Oathkeeper title,
    * whose defense keeps their window asked, and holds Longbows faceup. The
    * actor holds Specialist faceup and two favor. */
  private def specialistAgainstLongbows: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    withSpecialist(withAdviserFor(base, base.rival, longbows,
      Orientation.FaceUp))

  test("the attacker places two favor, and the defender is not asked"):
    val b = specialistAgainstLongbows
    val picked = commit(rules(winningDice), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(Look(picked.state).adviserTokens(b.actor, card), Tokens(2, 0))
    assert(!awaits(picked, b.rival, CampaignIds.defenderPlan))
    assert(!picked.finishPlanWindows.ops.contains(
      ModifyDicePool(CampaignIds.attackPool, -1)))
    assertEquals(said(Specialist, picked.events), Vector.empty)

  test("the defender is asked when the attacker does not choose it"):
    val b = specialistAgainstLongbows
    val run = commit(rules(winningDice), b, 3).finishAttackerPlans(b.actor)
    assert(run.offers(b.actor, b.rival, CampaignIds.defenderPlan,
      longbowsRef))

  test("a bandit defender applies no plan"):
    // Longbows stands at the origin, which the bandits rule.
    val base = board(warbands = 5, supply = 7)
    val b = withSpecialist(withSiteCard(base, base.origin, longbows))
    val run = commit(rules(winningDice), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assert(!run.ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(longbowsRef), 1)))

  test("it is the attacker's plan only"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.favor(base.rival, 2))
    val run = commit(rules(winningDice), b, 3).finishAttackerPlans(b.actor)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assert(!run.options(b.actor).map(_.ref).contains(ref))

  test("its reason reads as ruled"):
    assertEquals(NoteText.sentence(Specialist.noteKeys,
      Specialist.silenced(PowerSourceRef.Card(card))),
      "The defender cannot use battle plans.")
