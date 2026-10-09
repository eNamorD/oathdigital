package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.WalkerDiceFixture.skullThenSwords
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Kindred Warriors: a secret placed. ±X attack dice, X the suits other than
  * Arcane among the cards its user rules; an attacker also ignores every
  * skull, and so is offered it at X = 0. */
class KindredWarriorsSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Kindred Warriors")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val ignored = NoteText.Said("ignored", "Skulls ignored.",
    covers = false)

  private def attackDice(ops: Vector[CoreOperation]): Vector[Int] =
    ops.collect { case ModifyDicePool(CampaignIds.attackPool, delta, _) =>
      delta }

  /** p1 holds Kindred Warriors faceup with one faceup secret, and these
    * denizens faceup. */
  private def attacker(others: Vector[DenizenId]): Board =
    val held = others.foldLeft(withAdviser(board(warbands = 5, supply = 7),
      card, Orientation.FaceUp))((b, other) =>
      withAdviser(b, other, Orientation.FaceUp))
    withSecrets(held, 1)

  test("an attacker ruling a Nomad and an Order card adds two dice, places " +
      "a secret and ignores every skull"):
    val b = attacker(planless("Mountain Giant", "Baron"))
    val run = commit(rules(skullThenSwords), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(attackDice(picked.since(run)), Vector(2))
    assertEquals(Look(picked.state).adviserTokens(b.actor, card), Tokens(0, 1))
    val done = picked.finishPlanWindows
    // A skull face and three swords, the skull ignored.
    assertEquals(done.result.skullLosses, 0)
    assertEquals(done.result.attackScore, 2 + 3)
    assertEquals(NoteText.said(KindredWarriors.id, Vector(Outriders.ignored),
      done.events), Vector(ignored))

  test("an attacker ruling only Arcane cards is offered it for the skulls " +
      "alone"):
    val b = attacker(Vector.empty)
    val run = commit(rules(skullThenSwords), b, 2)
    assert(run.offers(b.actor, b.actor, CampaignIds.attackerPlan, ref))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(attackDice(picked.since(run)), Vector.empty)
    assertEquals(picked.finishPlanWindows.result.skullLosses, 0)

  test("a defender is offered it only when X is above zero, and removes X " +
      "dice"):
    val ruled = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val base = on(withAdviserFor(ruled, ruled.rival, card,
      Orientation.FaceUp))(_.secrets(ruled.rival, faceUp = 1))
    val bare = commit(rules(winningDice), base, 4)
    assert(awaits(bare, base.rival, CampaignIds.defenderPlan))
    assert(!bare.offered(base.actor).contains(ref))
    val b = withAdviserFor(base, base.rival, planlessCard("Mountain Giant"),
      Orientation.FaceUp)
    val run = commit(rules(winningDice), b, 4)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assertEquals(attackDice(picked.since(run)), Vector(-1))
