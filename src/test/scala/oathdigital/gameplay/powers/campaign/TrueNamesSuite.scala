package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** True Names: its holder's enemy cannot use battle plans matching any of
  * the holder's faceup advisers. */
class TrueNamesSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("True Names")
  private val warTortoise = CatalogNames.denizen("War Tortoise")
  private val warTortoiseRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(warTortoise)
  private val longbows = CatalogNames.denizen("Longbows")
  private val longbowsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(longbows)
  private val outriders = CatalogNames.denizen("Outriders")
  private val outridersRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(outriders)
  private val encirclement = CatalogNames.denizen("Encirclement")
  private val encirclementRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(encirclement)
  private val horseArchers = CatalogNames.denizen("Horse Archers")
  private val horseArchersRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(horseArchers)
  private val codeOfHonor = CatalogNames.denizen("Code of Honor")
  private val codeOfHonorRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(codeOfHonor)
  private val title: DecisionOptionRef = DecisionOptionRef.Button("title")

  /** The rival rules the origin with two warbands and the Oathkeeper title,
    * and holds War Tortoise (Beast) and Longbows (Order) faceup, and a
    * favor. The actor holds True Names (Beast) faceup and Outriders (Order)
    * facedown. */
  private def trueNamesAgainstBeastAndOrderPlans: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val rival = on(Vector(warTortoise, longbows).foldLeft(base)((b, id) =>
      withAdviserFor(b, base.rival, id, Orientation.FaceUp)))(
      _.favor(base.rival, 1))
    withAdviser(withAdviser(rival, card, Orientation.FaceUp), outriders,
      Orientation.FaceDown)

  test("the enemy cannot use plans matching the holder's faceup advisers"):
    val b = trueNamesAgainstBeastAndOrderPlans
    val run = commit(rules(winningDice), b, 3).finishAttackerPlans(b.actor)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assertEquals(run.withheld(b.actor, warTortoiseRef),
      Some(OptionReason.Noted(TrueNames.id, TrueNames.named(
        PowerSourceRef.Card(card), NoteArg.Player(b.actor)))))
    assert(run.offered(b.actor).contains(title))

  test("a facedown adviser's suit does not count"):
    // Outriders, an Order card, is facedown, so Longbows is offered.
    val b = trueNamesAgainstBeastAndOrderPlans
    val run = commit(rules(winningDice), b, 3).finishAttackerPlans(b.actor)
    assert(run.offered(b.actor).contains(longbowsRef))

  test("an adviser turned faceup during plan selection counts from the " +
      "next pass"):
    // Choosing the facedown Outriders turns it faceup.
    val b = trueNamesAgainstBeastAndOrderPlans
    val run = commit(rules(winningDice), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, outridersRef)
      .finishAttackerPlans(b.actor)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assert(run.withheld(b.actor, longbowsRef).nonEmpty)

  test("a plan chosen before an adviser turns faceup stays chosen"):
    // The rival defends holding True Names faceup and Encirclement (Order)
    // facedown; the actor chooses Longbows (Order) while the rival shows no
    // Order card, and then the rival's Encirclement is turned faceup.
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withAdviser(withAdviserFor(withAdviserFor(base, base.rival,
      card, Orientation.FaceUp), base.rival, encirclement,
      Orientation.FaceDown), longbows, Orientation.FaceUp))(
      _.favor(base.rival, 1))
    // One warband against the rival's two: Encirclement is usable.
    val chosen = commit(rules(winningDice), b, 1)
      .pick(b.actor, CampaignIds.attackerPlan, longbowsRef)
      .finishAttackerPlans(b.actor)
    val revealed = chosen.pick(b.rival, CampaignIds.defenderPlan,
      encirclementRef)
    val ended = revealed.finishPlanWindows
    assert(ended.ops.contains(ModifyDicePool(CampaignIds.attackPool, 1)))
    assert(ended.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))
    assert(ended.winner.nonEmpty)

  test("bandits whose Code of Honor is withheld apply their other plans"):
    // The actor shows True Names (Beast) and Outriders (Order) faceup, so
    // the bandits' Code of Honor (Order) is withheld and their Horse Archers
    // (Nomad) is not.
    val base = board(warbands = 5, supply = 7)
    val sites = withSiteCard(withSiteCard(base, base.origin, codeOfHonor),
      base.origin, horseArchers)
    val b = withAdviser(withAdviser(sites, card, Orientation.FaceUp),
      outriders, Orientation.FaceUp)
    val ops = commit(rules(winningDice), b, 3)
      .finishAttackerPlans(b.actor).finishPlanWindows.ops
    assert(ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(horseArchersRef), 1)))
    assert(!ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(codeOfHonorRef), 1)))

  test("its reason reads as ruled"):
    val b = trueNamesAgainstBeastAndOrderPlans
    assertEquals(NoteText.sentence(TrueNames.noteKeys, TrueNames.named(
      PowerSourceRef.Card(card), NoteArg.Player(b.actor))),
      s"No battle plans matching ${b.actor.value}'s advisers.")
