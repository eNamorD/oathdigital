package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseAmountAnswer
import oathdigital.testkit.{CatalogNames, Look}

/** Billowing Fog: when its user is defeated, the defeat kills none of their
  * force, and the powers that kill all of it are ignored. */
class BillowingFogSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Billowing Fog")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  private def spared(who: PlayerId): NoteText.Said = NoteText.Said("spared",
    s"${who.value}'s force was spared.", covers = false)

  /** `who` holds Billowing Fog faceup and a faceup secret to place on it. */
  private def withFogFor(b: Board, who: PlayerId): Board =
    on(withAdviserFor(b, who, card, Orientation.FaceUp))(
      _.secrets(who, faceUp = BillowingFog.Secret))

  test("a defeated attacker loses none of its surviving warbands"):
    val base = board(warbands = 5, supply = 7)
    val b = withFogFor(base, base.actor)
    val done = commit(rules(losingDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    // Four hollow swords make 2, against the origin's two bandits. The
    // defeat would have killed half of the four survivors.
    assertEquals(done.winner, Some(false))
    assertEquals(Look(done.state).boardWarbands(b.actor), 5)
    assertEquals(said(BillowingFog, done.events), Vector(spared(b.actor)))

  test("a defeated attacker still loses its sacrifice"):
    val base = board(extras = 1, warbands = 5, supply = 7)
    val b = withFogFor(base, base.actor)
    val extra = b.extras.head
    val done = commit(rules(losingDice), b, 4,
        targets = Vector(DecisionOptionRef.Site(extra)))
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .answer(b.actor, CampaignIds.sacrifice, ChooseAmountAnswer(1))
      .finishPlanWindows
    // 2 from the dice and 1 sacrificed do not beat four bandits and four
    // blank dice. The defeat would kill the sacrifice and half of the three
    // survivors; only the sacrifice dies.
    assertEquals(done.winner, Some(false))
    assert(done.ops.contains(Kill(
      Piece.Warbands(ForceKind.Exile(b.player(b.actor).lineage), 1),
      PositionedLocation(Location.PlayArea(b.actor)))))
    assertEquals(Look(done.state).boardWarbands(b.actor), 4)
    assertEquals(said(BillowingFog, done.events), Vector(spared(b.actor)))

  test("a defeated Conquest defender gets back every warband at the targets"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withFogFor(base, base.rival)
    val before = b.player(b.rival).board.warbands
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    // Both warbands at the origin die, and both return, not one.
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).boardWarbands(b.rival), before + 2)
    assertEquals(said(BillowingFog, done.events), Vector(spared(b.rival)))

  test("a defeated Raid defender loses no warband from its board"):
    val base = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    val b = on(withFogFor(base, base.rival))(_.boardWarbands(base.rival, 3))
    val won = commit(rules(winningDice), b, 4, raid = true)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assert(awaits(won, b.actor, CampaignIds.relocation))
    val done = won.pick(b.actor, CampaignIds.relocation,
      won.offered(b.actor).head)
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).boardWarbands(b.rival), 3)
    assertEquals(said(BillowingFog, done.events), Vector(spared(b.rival)))

  test("a defeated user ignores Sticky Fire: nothing is asked, nothing more " +
      "dies and no favor is given"):
    val fire = CatalogNames.relic("Sticky Fire")
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withRelicFor(withFogFor(base, base.actor), base.rival, fire))(
      _.favor(base.rival, 2))
    val done = commit(rules(losingDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.rival, CampaignIds.defenderPlan, DecisionOptionRef.Relic(fire))
      .finishDefenderPlans(b.rival)
      .declineSacrifice(b.actor).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assert(!awaits(done, b.rival, StickyFire.decisionId))
    assertEquals(Look(done.state).boardWarbands(b.actor), 5)
    assertEquals(Look(done.state).player(b.rival).board.favor, 2)
    assertEquals(said(BillowingFog, done.events), Vector(spared(b.actor)))

  test("a defeated defender ignores its own Shield Wall"):
    val wall = CatalogNames.denizen("Shield Wall")
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withAdviserFor(withFogFor(base, base.rival), base.rival, wall,
      Orientation.FaceUp))(_.favor(base.rival, ShieldWall.Favor))
    val before = b.player(b.rival).board.warbands
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
      .pick(b.rival, CampaignIds.defenderPlan, DecisionOptionRef.Denizen(wall))
      .finishPlanWindows
    // Four swords beat the two warbands and four blank dice.
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).boardWarbands(b.rival), before + 2)
    assertEquals(NoteText.said(ShieldWall.id, ShieldWall.noteKeys,
      done.events), Vector.empty)
    assertEquals(said(BillowingFog, done.events), Vector(spared(b.rival)))

  test("a victorious user changes nothing and writes nothing"):
    val base = board(warbands = 5, supply = 7)
    val b = withFogFor(base, base.actor)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).boardWarbands(b.actor), 5)
    assertEquals(said(BillowingFog, done.events), Vector.empty)
