package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Horse Archers: a free plan for three attack dice, added or removed, and the
  * card is discarded after the Campaign whoever won. */
class HorseArchersSuite extends munit.FunSuite:
  private val card = cardWith("denizen.horse-archers")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val line = NoteText.Said("discarded", "Discarded after the Campaign.",
    covers = false)

  private def discarded(state: OathState): Boolean = ready(state).game.current
    .commonCards.regionalDiscards.values.exists(_.contains(id))

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(HorseArchers.id, Vector(PlanDiscard.discarded), run.events)

  private def winner(run: Run): Option[Boolean] =
    ready(run.state).game.current.lastCampaignResult.map(_.attackerWins)

  test("an attacker adds three attack dice, for nothing"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val run = commit(rules(winning), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("it is discarded after a Campaign its user won, and says so"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val done = commit(rules(winning), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(winner(done), Some(true))
    assert(discarded(done.state))
    assertEquals(lines(done), Vector(line))

  test("it is discarded after a Campaign its user lost"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val done = commit(rules(losing), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(winner(done), Some(false))
    assert(discarded(done.state))
    assertEquals(lines(done), Vector(line))

  test("a defender removes three attack dice, and it is discarded"):
    val base = againstPlayer(board())
    val b = withAdviserFor(base, base.other, card, Orientation.FaceUp)
    val run = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -3)))
    assert(discarded(run.finish.state))

  test("a bandit defender applies it at its site, and it is discarded"):
    val base = board()
    val b = withSiteCard(base, base.origin, card)
    val done = commit(rules(winning), b, 4).finish
    assert(done.ops.contains(ModifyDicePool(CampaignIds.attackPool, -3)))
    assert(discarded(done.state))
    assertEquals(lines(done), Vector(line))

  test("a Campaign that does not choose it leaves it in play and says nothing"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val done = commit(rules(winning), b, 2).finish
    assert(!discarded(done.state))
    assertEquals(lines(done), Vector.empty)
