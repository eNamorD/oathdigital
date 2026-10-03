package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.CardPlay
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
  private val card = cardWith("denizen.mercenaries")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  /** Mercenaries' suit: the bank its placed favor comes from and returns
    * to. */
  private val mercenariesSuit = present(catalog.suitOf(card),
    "the suit of Mercenaries")

  private def funded(b: Board, who: PlayerId, favor: Int): Board =
    on(b)(_.favor(who, favor))

  private def discarded(state: OathState): Boolean = ready(state).game.current
    .commonCards.regionalDiscards.values.exists(_.contains(card))

  private def attackerBoard: Board =
    val base = board(warbands = 5, supply = 7)
    funded(withAdviser(base, card, Orientation.FaceUp), base.actor, 2)

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    Look(state).player(who).advisers.collectFirst {
      case held: DenizenState if held.id == card => held.tokens }

  test("an attacker pays a favor onto the card and adds three attack dice"):
    val b = attackerBoard
    val run = commit(rules(winning), b, 0)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assertEquals(adviserTokens(picked.state, b.actor), Some(Tokens(1, 0)))
    assertEquals(Look(picked.state).player(b.actor).board.favor, 1)

  test("an attacker that wins keeps Mercenaries and its favor"):
    val b = attackerBoard
    val done = commit(rules(winning), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(true))
    assertEquals(adviserTokens(done.state, b.actor), Some(Tokens(1, 0)))
    assert(!discarded(done.state))

  test("an attacker that is defeated discards Mercenaries, and its favor " +
      "returns to the bank"):
    val b = attackerBoard
    val before = b.ready.banks.favor.getOrElse(mercenariesSuit, 0)
    val done = commit(rules(losing), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assertEquals(adviserTokens(done.state, b.actor), None)
    assert(discarded(done.state))
    assertEquals(ready(done.state).banks.favor.getOrElse(mercenariesSuit, 0),
      before + 1)
    assertEquals(Look(done.state).player(b.actor).board.favor, 1)

  private def pile(state: OathState, region: Region): Vector[WorldCardId] =
    ready(state).game.current.commonCards.regionalDiscards.getOrElse(region,
      Vector.empty)

  test("an adviser is discarded to the region after the region of its " +
      "holder's pawn"):
    val b = attackerBoard
    val own = b.ready.game.current.map.regionOf(b.origin).get
    val done = commit(rules(losing), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assert(pile(done.state, CardPlay.nextRegion(own)).contains(card))

  test("a card at a site the attacker rules is discarded from the site, to " +
      "the region after the site's own"):
    val base = board(warbands = 5, supply = 7)
    val current = base.ready.game.current
    val pawnRegion = current.map.regionOf(base.origin).get
    // Buried Giant, in the Provinces, away from p1's Cradle site.
    val ruled = CatalogNames.site("Buried Giant")
    assert(!current.map.regionOf(ruled).contains(pawnRegion))
    val siteRegion = current.map.regionOf(ruled).get
    val b = funded(withSiteCard(actorRules(base, ruled), ruled, card),
      base.actor, 2)
    val done = commit(rules(losing), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assert(discarded(done.state))
    assertEquals(ready(done.state).game.current.map.sites(ruled).denizens,
      Vector.empty)
    assert(pile(done.state, CardPlay.nextRegion(siteRegion)).contains(card))
    assert(!pile(done.state, CardPlay.nextRegion(pawnRegion)).contains(card))

  test("an attacker with no favor to place is not offered Mercenaries"):
    val b = funded(attackerBoard, attackerBoard.actor, 0)
    assert(awaits(commit(rules(winning), b, 2), b.actor, CampaignIds.sacrifice))

  private def defending(warbands: Int): Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = warbands, supply = 7))
    funded(withAdviserFor(base, base.rival, card, Orientation.FaceUp),
      base.rival, 2)

  test("a defender pays at once and takes away the dice the attacker would " +
      "roll, down to none"):
    val b = defending(warbands = 2)
    val run = commit(rules(winning), b, 2)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    // The pool held two dice, so two are taken and not three.
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))
    assertEquals(adviserTokens(picked.state, b.rival), Some(Tokens.empty))
    assertEquals(Look(picked.state).player(b.rival).board.favor, 1)
    assertEquals(ready(picked.state).banks.favor.getOrElse(mercenariesSuit, 0),
      ready(run.state).banks.favor.getOrElse(mercenariesSuit, 0) + 1)

  test("a defender that wins keeps Mercenaries"):
    val b = defending(warbands = 2)
    val done = commit(rules(winning), b, 2)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assert(adviserTokens(done.state, b.rival).nonEmpty)
    assert(!discarded(done.state))

  test("a defender that is defeated discards Mercenaries"):
    val b = defending(warbands = 8)
    val done = commit(rules(winning), b, 8)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(true))
    assertEquals(adviserTokens(done.state, b.rival), None)
    assert(discarded(done.state))

  test("a bandit defender never uses Mercenaries, which costs a favor"):
    val two = board(extras = 1, warbands = 5, supply = 7)
    val b = funded(withSiteCard(two, two.extras.head, card), two.actor, 5)
    val run = commit(rules(winning), b, 2)
    assert(awaits(run, b.actor, CampaignIds.sacrifice))
    assert(!run.ops.exists(_.isInstanceOf[PayCost]))

  // ---- Lines ----

  private val power = Mercenaries.forCatalog(catalog)
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("a defeated user's discard is written as the Mercenaries' line"):
    val b = attackerBoard
    val done = commit(rules(losing), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(said(done.events), Vector(NoteText.Said("discarded",
      s"Discarded after ${b.actor.value} lost.", covers = false)))

  test("a winner keeps Mercenaries and reads no line"):
    val b = attackerBoard
    val done = commit(rules(winning), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(said(done.events), Vector.empty)
