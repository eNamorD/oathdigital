package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Great Crusade: a free plan for one attack die per nomad card its user
  * rules, counting itself, added or removed, and the card is discarded after
  * the Campaign. */
class GreatCrusadeSuite extends munit.FunSuite:
  private val card = cardWith("denizen.great-crusade")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val nomads = inert(Suit.Nomad, 3)
  private val order = inert(Suit.Order, 1).head
  private val line = NoteText.Said("discarded", "Discarded after the Campaign.",
    covers = false)

  private def discarded(state: OathState): Boolean = ready(state).game.current
    .commonCards.regionalDiscards.values.exists(_.contains(id))

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(GreatCrusade.id, Vector(PlanDiscard.discarded), run.events)

  test("an attacker adds one attack die per nomad card they rule, counting " +
      "itself, for nothing"):
    val base = board()
    val site = base.ready.game.current.map.inPlay.find(_ != base.origin).get
    // A facedown nomad adviser and an order card at a ruled site count nothing.
    val advisers = withAdviserFor(withAdviser(withAdviser(base, card,
      Orientation.FaceUp), nomads(0), Orientation.FaceUp), base.actor,
      nomads(1), Orientation.FaceDown)
    val b = withSiteCard(withSiteCard(actorRules(advisers, site), site,
      nomads(2)), site, order)
    val run = commit(rules(winning), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("a facedown Great Crusade counts itself, since choosing it reveals it"):
    val b = withAdviser(board(), card, Orientation.FaceDown)
    val run = commit(rules(winning), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 1)))

  test("it is discarded after the Campaign, and says so"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val done = commit(rules(winning), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assert(discarded(done.state))
    assertEquals(lines(done), Vector(line))

  test("a defender removes one attack die per nomad card they rule, and it " +
      "is discarded"):
    val base = againstPlayer(board())
    val b = withAdviserFor(withAdviserFor(base, base.other, card,
      Orientation.FaceUp), base.other, nomads(0), Orientation.FaceUp)
    val run = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))
    assert(discarded(run.finish.state))

  test("a bandit defender counts the nomad cards at every site bandits " +
      "rule, and it is discarded"):
    val base = board(extras = 1)
    val b = withSiteCard(withSiteCard(base, base.origin, card),
      base.extras.head, nomads(0))
    val done = commit(rules(winning), b, 4).finish
    assert(done.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))
    assert(discarded(done.state))
    assertEquals(lines(done), Vector(line))

  test("a facedown nomad plan the defender chose first is revealed and " +
      "counts, and one chosen after does not"):
    val pledge = cardWith("denizen.pledge-of-defense")
    val pledgeRef: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(pledge))
    val base = againstPlayer(board())
    val b = withAdviserFor(withAdviserFor(base, base.other, card,
      Orientation.FaceUp), base.other, pledge, Orientation.FaceDown)
    val run = commit(rules(winning), b, 4)
    val crusadeFirst = run.pick(b.other, CampaignIds.defenderPlan, ref)
    assert(crusadeFirst.since(run)
      .contains(ModifyDicePool(CampaignIds.attackPool, -1)))
    val pledgeFirst = run.pick(b.other, CampaignIds.defenderPlan, pledgeRef)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(pledgeFirst.since(run)
      .contains(ModifyDicePool(CampaignIds.attackPool, -2)))
