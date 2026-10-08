package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}
import oathdigital.testkit.Unwrap.present

/** Mercenaries: a favor placed for three more attack dice, or three fewer for
  * the attacker when it defends, and gone when its user is defeated. */
class MercenariesSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Mercenaries")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  /** Mercenaries' suit: the bank its placed favor comes from and returns
    * to. */
  private val mercenariesSuit = present(catalog.suitOf(card),
    "the suit of Mercenaries")

  private def funded(b: Board, who: PlayerId, favor: Int): Board =
    on(b)(_.favor(who, favor))

  private def attackerBoard: Board =
    val base = board(warbands = 5, supply = 7)
    funded(withAdviser(base, card, Orientation.FaceUp), base.actor, 2)

  test("an attacker pays a favor onto the card and adds three attack dice"):
    val b = attackerBoard
    val run = commit(rules(winningDice), b, 0)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assertEquals(Look(picked.state).adviserTokens(b.actor, card), Tokens(1, 0))
    assertEquals(Look(picked.state).player(b.actor).board.favor, 1)

  test("an attacker that wins keeps Mercenaries and its favor"):
    val b = attackerBoard
    val done = commit(rules(winningDice), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).adviserTokens(b.actor, card), Tokens(1, 0))
    assert(!Look(done.state).inRegionalDiscard(card))

  test("an attacker that is defeated discards Mercenaries, and its favor " +
      "returns to the bank"):
    val b = attackerBoard
    val before = Look(b.ready).bankFavor(mercenariesSuit)
    val done = commit(rules(losingDice), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assert(!Look(done.state).player(b.actor).advisers.exists(_.id == card))
    assert(Look(done.state).inRegionalDiscard(card))
    assertEquals(Look(done.state).bankFavor(mercenariesSuit), before + 1)
    assertEquals(Look(done.state).favor(b.actor), 1)

  test("an adviser is discarded to the region after the region of its " +
      "holder's pawn"):
    val b = attackerBoard
    val done = commit(rules(losingDice), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assert(Look(done.state).discardAfter(b.origin).contains(card))

  test("a card at a site the attacker rules is discarded from the site, to " +
      "the region after the site's own"):
    val base = board(warbands = 5, supply = 7)
    val current = base.ready.game.current
    // Buried Giant, in the Provinces, away from p1's Cradle site.
    val ruled = CatalogNames.site("Buried Giant")
    assert(!current.map.regionOf(ruled).contains(
      current.map.regionOf(base.origin).get))
    val b = funded(withSiteCard(actorRules(base, ruled), ruled, card),
      base.actor, 2)
    val done = commit(rules(losingDice), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assert(Look(done.state).inRegionalDiscard(card))
    assertEquals(ready(done.state).game.current.map.sites(ruled).denizens,
      Vector.empty)
    assert(Look(done.state).discardAfter(ruled).contains(card))
    assert(!Look(done.state).discardAfter(base.origin).contains(card))

  test("an attacker with no favor to place is not offered Mercenaries"):
    val b = funded(attackerBoard, attackerBoard.actor, 0)
    assert(awaits(commit(rules(winningDice), b, 2), b.actor,
      CampaignIds.sacrifice))

  private def defending(warbands: Int): Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = warbands, supply = 7))
    funded(withAdviserFor(base, base.rival, card, Orientation.FaceUp),
      base.rival, 2)

  test("a defender pays at once and takes away the dice the attacker would " +
      "roll, down to none"):
    val b = defending(warbands = 2)
    val run = commit(rules(winningDice), b, 2)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    // The pool held two dice, so two are taken and not three.
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))
    assertEquals(Look(picked.state).adviserTokens(b.rival, card), Tokens.empty)
    assertEquals(Look(picked.state).player(b.rival).board.favor, 1)
    assertEquals(ready(picked.state).banks.favor.getOrElse(mercenariesSuit, 0),
      ready(run.state).banks.favor.getOrElse(mercenariesSuit, 0) + 1)

  test("a defender that wins keeps Mercenaries"):
    val b = defending(warbands = 2)
    val done = commit(rules(winningDice), b, 2)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assert(Look(done.state).player(b.rival).advisers.exists(_.id == card))
    assert(!Look(done.state).inRegionalDiscard(card))

  test("a defender that is defeated discards Mercenaries"):
    val b = defending(warbands = 8)
    val done = commit(rules(winningDice), b, 8)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    assert(!Look(done.state).player(b.rival).advisers.exists(_.id == card))
    assert(Look(done.state).inRegionalDiscard(card))

  test("a bandit defender never uses Mercenaries, which costs a favor"):
    val two = board(extras = 1, warbands = 5, supply = 7)
    val b = funded(withSiteCard(two, two.extras.head, card), two.actor, 5)
    val run = commit(rules(winningDice), b, 2)
    assert(awaits(run, b.actor, CampaignIds.sacrifice))
    assert(!run.ops.exists(_.isInstanceOf[PayCost]))

  // ---- Lines ----

  private val power = Mercenaries.forCatalog(catalog)
  test("a defeated user's discard is written as the Mercenaries' line"):
    val b = attackerBoard
    val done = commit(rules(losingDice), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(said(power, done.events), Vector(NoteText.Said("discarded",
      s"Discarded after ${b.actor.value} lost.", covers = false)))

  test("a winner keeps Mercenaries and reads no line"):
    val b = attackerBoard
    val done = commit(rules(winningDice), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(said(power, done.events), Vector.empty)
