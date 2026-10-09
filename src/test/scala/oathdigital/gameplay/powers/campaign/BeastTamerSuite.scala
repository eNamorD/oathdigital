package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** Beast Tamer: its holder's enemy cannot use Beast or Nomad battle plans. */
class BeastTamerSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Beast Tamer")
  private val warTortoise = CatalogNames.denizen("War Tortoise")
  private val warTortoiseRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(warTortoise)
  private val horseArchers = CatalogNames.denizen("Horse Archers")
  private val horseArchersRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(horseArchers)
  private val longbows = CatalogNames.denizen("Longbows")
  private val longbowsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(longbows)
  private val title: DecisionOptionRef = DecisionOptionRef.Button("title")
  private val tamed = Some(OptionReason.Noted(BeastTamer.id,
    BeastTamer.tamed(PowerSourceRef.Card(card))))

  /** The rival rules the origin with two warbands and the Oathkeeper title,
    * and holds War Tortoise (Beast), Horse Archers (Nomad) and Longbows
    * (Order) faceup, and a favor. */
  private def rivalWithBeastNomadAndOrderPlans: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(Vector(warTortoise, horseArchers, longbows).foldLeft(base)((b, id) =>
      withAdviserFor(b, base.rival, id, Orientation.FaceUp)))(
      _.favor(base.rival, 1))

  /** A Conquest of `b`, at the defender's plan window. */
  private def defenderWindow(b: Board): Run =
    commit(rules(winningDice), b, 3).finishAttackerPlans(b.actor)

  test("the defender's Beast and Nomad plans are withheld"):
    val b = withAdviser(rivalWithBeastNomadAndOrderPlans, card,
      Orientation.FaceUp)
    val run = defenderWindow(b)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assertEquals(run.withheld(b.actor, warTortoiseRef), tamed)
    assertEquals(run.withheld(b.actor, horseArchersRef), tamed)
    assert(run.offered(b.actor).contains(longbowsRef))
    assert(run.offered(b.actor).contains(title))

  test("a facedown Beast Tamer withholds nothing"):
    val b = withAdviser(rivalWithBeastNomadAndOrderPlans, card,
      Orientation.FaceDown)
    assert(defenderWindow(b).offered(b.actor).contains(warTortoiseRef))

  test("a bystander's Beast Tamer withholds nothing"):
    val base = rivalWithBeastNomadAndOrderPlans
    val b = withAdviserFor(base, base.bystander, card, Orientation.FaceUp)
    assert(defenderWindow(b).offered(b.actor).contains(horseArchersRef))

  test("against a defending holder, the attacker's plans are withheld"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withAdviser(withAdviser(withAdviserFor(base, base.rival, card,
      Orientation.FaceUp), horseArchers, Orientation.FaceUp), longbows,
      Orientation.FaceUp)
    val run = commit(rules(winningDice), b, 3)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    assertEquals(run.withheld(b.actor, horseArchersRef), tamed)
    assert(run.offered(b.actor).contains(longbowsRef))

  test("the holder's own plans are not withheld"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withAdviser(withAdviser(base, card, Orientation.FaceUp),
      horseArchers, Orientation.FaceUp)
    assert(commit(rules(winningDice), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, horseArchersRef))

  test("a bandit defender does not apply a Nomad plan"):
    // Horse Archers is free and stands at the origin, which bandits rule.
    val base = board(warbands = 5, supply = 7)
    val b = withAdviser(withSiteCard(base, base.origin, horseArchers), card,
      Orientation.FaceUp)
    val ops = commit(rules(winningDice), b, 3).finishPlanWindows.ops
    assert(!ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(horseArchersRef), 1)))

  test("its reason reads as ruled"):
    assertEquals(NoteText.sentence(BeastTamer.noteKeys,
      BeastTamer.tamed(PowerSourceRef.Card(card))),
      "No Beast or Nomad battle plans.")
