package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** Code of Honor: two attack dice added or removed, used with no other
  * battle plan. */
class CodeOfHonorSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Code of Honor")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val longbows = CatalogNames.denizen("Longbows")
  private val longbowsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(longbows)
  private val outriders = CatalogNames.denizen("Outriders")
  private val source = PowerSourceRef.Card(card)

  /** The actor holds Code of Honor, Longbows and Outriders faceup: three
    * free attacker plans. Two bandits rule the origin. */
  private def threeFreeAttackerPlans: Board =
    Vector(card, longbows, outriders).foldLeft(
      board(warbands = 5, supply = 7))((b, id) =>
      withAdviser(b, id, Orientation.FaceUp))

  test("an attacker adds two attack dice and may then choose no other plan"):
    val b = threeFreeAttackerPlans
    val run = commit(rules(winningDice), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(
      ModifyDicePool(CampaignIds.attackPool, 2)))
    // Longbows and Outriders are withheld, so nothing is left to ask.
    assert(!awaits(picked, b.actor, CampaignIds.attackerPlan))
    assert(!picked.finishPlanWindows.ops.contains(
      ModifyDicePool(CampaignIds.attackPool, 1)))
    assertEquals(said(CodeOfHonor, picked.events), Vector.empty)

  test("it is withheld once its user has chosen another plan"):
    val b = threeFreeAttackerPlans
    val picked = commit(rules(winningDice), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, longbowsRef)
    assert(picked.offers(b.actor, b.actor, CampaignIds.attackerPlan,
      DecisionOptionRef.Denizen(outriders)))
    assertEquals(picked.withheld(b.actor, ref), Some(OptionReason.Noted(
      CodeOfHonor.id, CodeOfHonor.notWithOthers(source))))

  test("a defender removes two attack dice"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withAdviserFor(base, base.rival, card, Orientation.FaceUp)
    val run = commit(rules(winningDice), b, 3).finishAttackerPlans(b.actor)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))

  test("a bandit defender applies it and no other plan"):
    // Both stand at the origin, which the bandits rule; both are free.
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(withSiteCard(base, base.origin, card), base.origin,
      longbows)
    val ops = commit(rules(winningDice), b, 3).finishPlanWindows.ops
    assert(ops.contains(ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)))
    assert(!ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(longbowsRef), 1)))

  test("its reasons read as ruled"):
    assertEquals(NoteText.sentence(CodeOfHonor.noteKeys,
      CodeOfHonor.notWithOthers(source)),
      "Cannot be used with other battle plans.")
    assertEquals(NoteText.sentence(CodeOfHonor.noteKeys,
      CodeOfHonor.noOthers(source)), "No other battle plans.")
