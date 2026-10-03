package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.Look

/** Sticky Fire: when its user wins, they may kill the whole of the enemy's
  * force, and if they do they give the loser a favor. The question is asked in
  * the losses, by the winner, before anything dies. */
class StickyFireSuite extends munit.FunSuite:
  private val relic = relicWith("relic.sticky-fire")
  private val ref: DecisionOptionRef = DecisionOptionRef.Relic(relic)
  private val decision = StickyFire.decisionId

  private def favored(b: Board, who: PlayerId, favor: Int): Board =
    on(b)(_.favor(who, favor))

  private def warbands(state: OathState, who: PlayerId): Int =
    Look(state).player(who).board.warbands

  private def favorOf(state: OathState, who: PlayerId): Int =
    Look(state).player(who).board.favor

  // ---- the attacker wins a Conquest against a player ----------------------

  private def conquest: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    favored(withRelic(base, relic), base.actor, 2)

  private def bank(state: OathState): Int =
    ready(state).banks.favor.values.sum

  test("the winner is asked before anything dies, and the defender keeps no " +
      "returned warband if the attacker says yes"):
    val b = conquest
    val g = rules(winning)
    val before = warbands(OathState.Ready(b.ready), b.rival)
    val asked = commit(g, b, 4).pick(b.actor, CampaignIds.attackerPlan, ref)
      .finishDefenderPlans(b.rival)
      .declineSacrifice(b.actor)
    assert(awaits(asked, b.actor, decision))
    // The question is asked first: nobody has died yet.
    assertEquals(ready(asked.state).game.current.map.sites(b.origin).forces,
      SiteForces.Occupied(ForceKind.Exile(b.player(b.rival).lineage), 2))
    val done = asked.pick(b.actor, decision, StickyFire.yes).finishPlanWindows
    assertEquals(warbands(done.state, b.rival), before)
    // The winner gave the loser a favor.
    assertEquals(favorOf(done.state, b.actor), 1)
    assertEquals(favorOf(done.state, b.rival),
      favorOf(OathState.Ready(b.ready), b.rival) + 1)

  test("if the attacker says no, the defender keeps the returned half and " +
      "nothing is given"):
    val b = conquest
    val before = warbands(OathState.Ready(b.ready), b.rival)
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .finishDefenderPlans(b.rival)
      .declineSacrifice(b.actor)
      .pick(b.actor, decision, StickyFire.no).finishPlanWindows
    assertEquals(warbands(done.state, b.rival), before + 1)
    assertEquals(favorOf(done.state, b.actor), 2)

  test("nothing is asked when its user loses, or when it was not chosen"):
    val b = conquest
    val lost = commit(rules(losing), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .finishDefenderPlans(b.rival)
      .declineSacrifice(b.actor)
    assertEquals(ready(lost.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assert(!awaits(lost, b.actor, decision))
    assert(!awaits(lost, b.rival, decision))
    val unchosen = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, CampaignIds.finish)
      .finishDefenderPlans(b.rival)
      .declineSacrifice(b.actor)
    assert(!awaits(unchosen, b.actor, decision))

  test("the favor is given only if the winner has one to give"):
    val b = favored(conquest, conquest.actor, 0)
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .finishDefenderPlans(b.rival)
      .declineSacrifice(b.actor)
      .pick(b.actor, decision, StickyFire.yes).finishPlanWindows
    assertEquals(favorOf(done.state, b.rival),
      favorOf(OathState.Ready(b.ready), b.rival))

  // ---- the attacker wins a Raid -------------------------------------------

  test("in a Raid every warband on the defender's board dies, not half"):
    val base = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    val b = favored(withRelic(on(base)(_.boardWarbands(base.rival, 3)), relic),
      base.actor, 1)
    val g = rules(winning)
    val done = commit(g, b, 4, raid = true)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .declineSacrifice(b.actor)
      .pick(b.actor, decision, StickyFire.yes)
    assertEquals(warbands(done.state, b.rival), 0)
    assertEquals(favorOf(done.state, b.actor), 0)
    // The Raid then burns half of the defender's favor, the given one included.
    val defenderFavor = favorOf(OathState.Ready(b.ready), b.rival) + 1
    assertEquals(favorOf(done.state, b.rival),
      defenderFavor - defenderFavor / 2)

  // ---- the defender wins --------------------------------------------------

  private def defending: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    favored(withRelicFor(base, base.rival, relic), base.rival, 2)

  test("a defender that wins is asked, and every warband on the attacker's " +
      "board dies, committed or not"):
    val b = defending
    val g = rules(losing)
    val asked = commit(g, b, 4).pick(b.rival, CampaignIds.defenderPlan, ref)
      .finishDefenderPlans(b.rival)
      .declineSacrifice(b.actor)
    assertEquals(ready(asked.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assert(awaits(asked, b.rival, decision))
    val done = asked.pick(b.rival, decision, StickyFire.yes).finishPlanWindows
    assertEquals(warbands(done.state, b.actor), 0)
    assertEquals(favorOf(done.state, b.rival), 1)
    assertEquals(favorOf(done.state, b.actor),
      favorOf(OathState.Ready(b.ready), b.actor) + 1)

  // ---- against bandits ----------------------------------------------------

  test("against bandits the favor is burnt instead of given"):
    val base = board(warbands = 5, supply = 7)
    val b = favored(withRelic(base, relic), base.actor, 2)
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .declineSacrifice(b.actor)
      .pick(b.actor, decision, StickyFire.yes).finishPlanWindows
    assertEquals(favorOf(done.state, b.actor), 1)
    // The favor left every player's board without reaching a suit bank.
    assertEquals(bank(done.state), bank(OathState.Ready(b.ready)))

  test("a player who does not hold the relic is never asked"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val done = commit(rules(winning), favored(base, base.actor, 2), 4)
      .finishDefenderPlans(base.rival)
      .finishPlanWindows
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(true))
    assertEquals(done.ops.count(_.isInstanceOf[Give]), 0)

  // ---- Lines ----

  private val power = StickyFire
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("a burnt force is written: what died, and the favor given"):
    val base = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    val b = favored(withRelic(on(base)(_.boardWarbands(base.rival, 3)), relic),
      base.actor, 1)
    val done = commit(rules(winning), b, 4, raid = true)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .declineSacrifice(b.actor)
      .pick(b.actor, decision, StickyFire.yes)
    // The Raid's own losses kill one of the three first; the burn kills the
    // two left, and its line says what the burn did.
    assertEquals(said(done.events), Vector(NoteText.Said("burned",
      s"Killed 2 ${b.rival.value} warbands, and ${b.rival.value} gained 1 " +
        "favor.",
      covers = false)))

  test("a winner with no favor to give writes only the kill"):
    val b = favored(conquest, conquest.actor, 0)
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .finishDefenderPlans(b.rival)
      .declineSacrifice(b.actor)
      .pick(b.actor, decision, StickyFire.yes).finishPlanWindows
    assertEquals(said(done.events), Vector(NoteText.Said("killed",
      s"Killed 1 ${b.rival.value} warband.", covers = false)))

  test("sparing the force, or burning bandits, writes nothing"):
    val b = conquest
    val spared = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .finishDefenderPlans(b.rival)
      .declineSacrifice(b.actor)
      .pick(b.actor, decision, StickyFire.no).finishPlanWindows
    assertEquals(said(spared.events), Vector.empty)
    val base = board(warbands = 5, supply = 7)
    val bandits = favored(withRelic(base, relic), base.actor, 2)
    val burnt = commit(rules(winning), bandits, 4)
      .pick(bandits.actor, CampaignIds.attackerPlan, ref)
      .declineSacrifice(bandits.actor)
      .pick(bandits.actor, decision, StickyFire.yes).finishPlanWindows
    assertEquals(said(burnt.events), Vector.empty)
