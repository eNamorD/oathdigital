package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Mercenaries: a favor placed for three more attack dice, or three fewer for the
  * attacker when it defends, and gone when its user is defeated.
  */
class MercenariesSuite extends munit.FunSuite {
  private val card = cardWith("denizen.mercenaries")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)

  private def funded(b: Board, who: PlayerId, favor: Int): Board =
    replacePlayer(b, who)(p => p.copy(board = p.board.copy(favor = favor)))

  private def discarded(state: OathState): Boolean = ready(state).game.current
    .commonCards.regionalDiscards.values.exists(_.contains(id))

  private def attackerBoard: Board = {
    val base = board()
    funded(withAdviser(base, card, Orientation.FaceUp), base.actor, 2)
  }

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    player(state, who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  test("an attacker pays a favor onto the card and adds three attack dice") {
    val b = attackerBoard
    val run = commit(rules(winning), b, 0)
    assertEquals(run.continue, awaits(b.actor, CampaignIds.attackerPlan))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assertEquals(adviserTokens(picked.state, b.actor), Some(Tokens(1, 0)))
    assertEquals(player(picked.state, b.actor).board.favor, 1)
  }

  test("an attacker that wins keeps Mercenaries and its favor") {
    val b = attackerBoard
    val done = commit(rules(winning), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(true))
    assertEquals(adviserTokens(done.state, b.actor), Some(Tokens(1, 0)))
    assert(!discarded(done.state))
  }

  test("an attacker that is defeated discards Mercenaries, and its favor returns to the bank") {
    val b = attackerBoard
    val before = b.ready.banks.favor.getOrElse(Suit.Discord, 0)
    val done = commit(rules(losing), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assertEquals(adviserTokens(done.state, b.actor), None)
    assert(discarded(done.state))
    assertEquals(ready(done.state).banks.favor.getOrElse(Suit.Discord, 0),
      before + 1)
    assertEquals(player(done.state, b.actor).board.favor, 1)
  }

  private def pile(state: OathState, region: Region): Vector[WorldCardId] =
    ready(state).game.current.commonCards.regionalDiscards.getOrElse(region,
      Vector.empty)

  test("an adviser is discarded to the region after the region of its holder's pawn") {
    val b = attackerBoard
    val own = b.ready.game.current.map.regionOf(b.origin).get
    val done = commit(rules(losing), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assert(pile(done.state, CardPlay.nextRegion(own)).contains(id))
  }

  test("a card at a site the attacker rules is discarded from the site, to the region after the site's own") {
    val base = board()
    val current = base.ready.game.current
    val pawnRegion = current.map.regionOf(base.origin).get
    val ruled = current.map.inPlay.find(site =>
      current.map.regionOf(site).exists(_ != pawnRegion)).get
    val siteRegion = current.map.regionOf(ruled).get
    val b = funded(withSiteCard(actorRules(base, ruled), ruled, card), base.actor, 2)
    val done = commit(rules(losing), b, 0).pick(b.actor, CampaignIds.attackerPlan,
      ref).finish
    assert(discarded(done.state))
    assertEquals(ready(done.state).game.current.map.sites(ruled).denizens,
      Vector.empty)
    assert(pile(done.state, CardPlay.nextRegion(siteRegion)).contains(id))
    assert(!pile(done.state, CardPlay.nextRegion(pawnRegion)).contains(id))
  }

  test("an attacker with no favor to place is not offered Mercenaries") {
    val b = funded(attackerBoard, attackerBoard.actor, 0)
    assertEquals(commit(rules(winning), b, 2).continue,
      awaits(b.actor, CampaignIds.sacrifice))
  }

  private def defending(warbands: Int = 8): Board = {
    val base = againstPlayer(board(warbands = warbands))
    funded(withAdviserFor(base, base.other, card, Orientation.FaceUp),
      base.other, 2)
  }

  test("a defender pays at once and takes away the dice the attacker would roll, down to none") {
    val b = defending(warbands = 2)
    val run = commit(rules(winning), b, 2)
    assertEquals(run.continue, awaits(b.other, CampaignIds.defenderPlan))
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref)
    // The pool held two dice, so two are taken and not three.
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))
    assertEquals(adviserTokens(picked.state, b.other), Some(Tokens.empty))
    assertEquals(player(picked.state, b.other).board.favor, 1)
    assertEquals(ready(picked.state).banks.favor.getOrElse(Suit.Discord, 0),
      ready(run.state).banks.favor.getOrElse(Suit.Discord, 0) + 1)
  }

  test("a defender that wins keeps Mercenaries") {
    val b = defending(warbands = 2)
    val done = commit(rules(winning), b, 2)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assert(adviserTokens(done.state, b.other).nonEmpty)
    assert(!discarded(done.state))
  }

  test("a defender that is defeated discards Mercenaries") {
    val b = defending()
    val done = commit(rules(winning), b, 8)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(true))
    assertEquals(adviserTokens(done.state, b.other), None)
    assert(discarded(done.state))
  }

  test("a bandit defender never uses Mercenaries, which costs a favor") {
    val two = board(extras = 1)
    val b = funded(withSiteCard(two, two.extras.head, card), two.actor, 5)
    val run = commit(rules(winning), b, 2)
    assertEquals(run.continue, awaits(b.actor, CampaignIds.sacrifice))
    assert(!run.ops.exists(_.isInstanceOf[PayCost]))
  }

  test("the card is found in the catalog and registered once") {
    assertEquals(SimplePlans.forCatalog(catalog).count(_.id == Mercenaries.id), 1)
  }
}
