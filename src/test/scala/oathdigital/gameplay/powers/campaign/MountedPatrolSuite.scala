package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Mounted Patrol: a free defender's plan that halves the attack pool,
  * rounded down, just before the attack roll, and is discarded after the
  * Campaign. Its halving is the first `wrapping` on the attack roll's leaf
  * (catalog batch 4 design, "Seams used for the first time"). */
class MountedPatrolSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Mounted Patrol")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val longbows = CatalogNames.denizen("Longbows")
  private val discarded = NoteText.Said("discarded",
    "Discarded after the Campaign.", covers = false)

  /** The faces of `n` attack dice rolled with `winningDice`. */
  private def swords(n: Int): Vector[AttackDieFace] =
    Vector.fill(n)(AttackDieFace.OneSword)

  private def halved(text: String): NoteText.Said =
    NoteText.Said("halved", s"The attack pool was halved to $text.",
      covers = false)

  private def lines(run: Run): Vector[NoteText.Said] = NoteText.said(
    MountedPatrol.id, Vector(MountedPatrol.halved, PlanDiscard.discarded),
    run.events)

  /** p2 rules the origin, holds the Oathkeeper title and Mounted Patrol
    * faceup. */
  private def defender: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    withAdviserFor(base, base.rival, card, Orientation.FaceUp)

  test("the defender halves the attack pool before the roll, and the card " +
      "is discarded"):
    val b = defender
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assert(done.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))
    assertEquals(done.result.attackFaces, swords(2))
    assert(Look(done.state).inRegionalDiscard(card))
    assertEquals(lines(done), Vector(halved("2 dice"), discarded))

  test("a defender plan chosen after it still counts"):
    val base = defender
    val b = withAdviserFor(base, base.rival, longbows, Orientation.FaceUp)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
      .pick(b.rival, CampaignIds.defenderPlan,
        DecisionOptionRef.Denizen(longbows)).finishPlanWindows
    // Four dice, one removed by Longbows, then halved.
    assertEquals(done.result.attackFaces, swords(1))
    assertEquals(lines(done), Vector(halved("1 die"), discarded))

  test("a bandit defender applies it and discards it"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, card)
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(done.result.attackFaces, swords(2))
    assert(Look(done.state).inRegionalDiscard(card))
    assertEquals(lines(done), Vector(halved("2 dice"), discarded))

  test("a pool of one is halved to none"):
    val b = defender
    val done = commit(rules(winningDice), b, 1)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.result.attackFaces, Vector.empty)
    assertEquals(done.result.attackScore, 0)
    assertEquals(lines(done), Vector(halved("0 dice"), discarded))
