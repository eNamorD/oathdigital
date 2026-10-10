package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, PlanAmounts}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Mountain Giant: a plan for either side costing a secret, then one or
  * three attack dice, added or removed. It is discarded after the
  * Campaign only when three was chosen. */
class MountainGiantSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Mountain Giant")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val one = PlanAmounts.button(1)
  private val three = PlanAmounts.button(MountainGiant.Three)
  private val discarded = NoteText.Said("discarded",
    "Discarded after the Campaign.", covers = false)

  private def lines(run: Run): Vector[NoteText.Said] = NoteText.said(
    MountainGiant.id, Vector(MountainGiant.added, MountainGiant.removed,
      PlanDiscard.discarded), run.events)

  /** p1 attacks the bandit-ruled origin holding Mountain Giant faceup and
    * one faceup secret. */
  private def attackerWithGiant: Board =
    withSecrets(withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp), 1)

  /** p2 rules the origin with two warbands and holds the Oathkeeper title,
    * Mountain Giant faceup and `secrets` faceup secrets. */
  private def defenderWithGiant(secrets: Int): Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.secrets(base.rival, faceUp = secrets))

  test("its text names the attack die the card leaves out after ±3"):
    assertEquals(MountainGiantCard.power.text,
      "±1 [attack-die] or ±3 [attack-die] At end, discard.")

  test("after the secret is paid, it asks one or three"):
    val b = attackerWithGiant
    val asked = commit(rules(winningDice), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(awaits(asked, b.actor, MountainGiant.decisionId))
    assertEquals(Look(asked.state).adviserTokens(b.actor, card),
      Tokens(0, 1))
    assertEquals(asked.query(b.actor), DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(one, "Add 1 attack die"),
      DecisionOption.Button(three,
        "Add 3 attack dice, and discard Mountain Giant after the Campaign")),
      heading = Some("Mountain Giant: add 1 or 3 attack dice?")))

  test("an attacker who adds three discards it after the Campaign"):
    val b = attackerWithGiant
    val run = commit(rules(winningDice), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.actor, MountainGiant.decisionId, three)
    assert(picked.since(run).contains(
      ModifyDicePool(CampaignIds.attackPool, 3)))
    val done = picked.finishPlanWindows
    assert(Look(done.state).inRegionalDiscard(card))
    assertEquals(lines(done), Vector(NoteText.Said("added",
      "Added 3 attack dice.", covers = false), discarded))

  test("an attacker who adds one keeps it"):
    val b = attackerWithGiant
    val done = commit(rules(winningDice), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.actor, MountainGiant.decisionId, one).finishPlanWindows
    assert(done.ops.contains(ModifyDicePool(CampaignIds.attackPool, 1)))
    assert(!Look(done.state).inRegionalDiscard(card))
    assertEquals(lines(done), Vector(NoteText.Said("added",
      "Added 1 attack die.", covers = false)))

  test("a defender removes three attack dice, and it is discarded"):
    val b = defenderWithGiant(secrets = 1)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
      .pick(b.rival, MountainGiant.decisionId, three).finishPlanWindows
    assert(done.ops.contains(ModifyDicePool(CampaignIds.attackPool, -3)))
    assert(Look(done.state).inRegionalDiscard(card))
    assertEquals(lines(done), Vector(NoteText.Said("removed",
      "Removed 3 attack dice.", covers = false), discarded))

  test("it costs a secret, and without one it is shown unavailable"):
    val b = defenderWithGiant(secrets = 1)
    val option = commit(rules(winningDice), b, 4).options(b.actor)
      .find(_.ref == ref)
    assertEquals(option.collect {
      case DecisionOption.Priced(_, price) => price
    }, Some(OptionPrice(secrets = 1)))
    val poor = defenderWithGiant(secrets = 0)
    assertEquals(commit(rules(winningDice), poor, 4).withheld(poor.actor,
      ref), Some(OptionReason.Written("Needs 1 secret; you have 0")))
