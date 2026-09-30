package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Rival Khan: a free plan for four attack dice, added or removed, offered
  * only when the enemy has a faceup nomad adviser, and discarded after the
  * Campaign. */
class RivalKhanSuite extends munit.FunSuite:
  private val card = cardWith("denizen.rival-khan")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val nomad = inert(Suit.Nomad, 1).head
  private val line = NoteText.Said("discarded", "Discarded after the Campaign.",
    covers = false)

  private def discarded(state: OathState): Boolean = ready(state).game.current
    .commonCards.regionalDiscards.values.exists(_.contains(id))

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(RivalKhan.id, Vector(PlanDiscard.discarded), run.events)

  /** The actor holds Rival Khan; the defender, a player, holds a nomad
    * adviser on `enemy`'s face. */
  private def attacker(enemy: Orientation): Board =
    val base = againstPlayer(board())
    withAdviserFor(withAdviser(base, card, Orientation.FaceUp), base.other,
      nomad, enemy)

  test("an attacker adds four attack dice, and it is discarded after the " +
      "Campaign"):
    val b = attacker(Orientation.FaceUp)
    val run = commit(rules(losing), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 4)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))
    val done = picked.finish
    assert(discarded(done.state))
    assertEquals(lines(done), Vector(line))

  test("it is not offered when the enemy's nomad adviser is facedown"):
    val b = attacker(Orientation.FaceDown)
    assert(!commit(rules(losing), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("it is not offered when the enemy's faceup adviser is not a nomad"):
    val base = againstPlayer(board())
    val b = withAdviserFor(withAdviser(base, card, Orientation.FaceUp),
      base.other, inert(Suit.Arcane, 1).head, Orientation.FaceUp)
    assert(!commit(rules(losing), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("a bandit defender applies it against a nomad adviser, and it is " +
      "discarded"):
    val base = board()
    val b = withAdviser(withSiteCard(base, base.origin, card), nomad,
      Orientation.FaceUp)
    val done = commit(rules(winning), b, 5).finish
    assert(done.ops.contains(ModifyDicePool(CampaignIds.attackPool, -4)))
    assert(discarded(done.state))

  test("it is never offered against bandits"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    assert(!commit(rules(losing), b, 2).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("a defender removes four attack dice when the attacker has a nomad " +
      "adviser, and it is discarded"):
    val base = againstPlayer(board())
    val b = withAdviserFor(withAdviser(base, nomad, Orientation.FaceUp),
      base.other, card, Orientation.FaceUp)
    val run = commit(rules(losing), b, 5)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -4)))
    assert(discarded(run.finish.state))
