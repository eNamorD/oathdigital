package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Nature Worship: a secret placed for one attack die, added or removed, per
  * faceup beast adviser its user has, itself included. */
class NatureWorshipSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Nature Worship")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val beasts = planless("Errand Boy", "Small Friends")

  private def advised(b: Board, who: PlayerId,
      cards: Vector[(DenizenId, Orientation)]): Board =
    cards.foldLeft(b) { case (done, (held, side)) =>
      withAdviserFor(done, who, held, side) }

  private def attacker(self: Orientation,
      others: Vector[(DenizenId, Orientation)]): Board =
    val base = board(warbands = 5, supply = 7)
    withSecrets(advised(base, base.actor, (card -> self) +: others), 1)

  private def picked(b: Board): Run =
    commit(rules(winning), b, 2).pick(b.actor, CampaignIds.attackerPlan, ref)

  /** The site p1 comes to rule: Broken Peaks, the board's first site after
    * the origin. */
  private val ruledSite = CatalogNames.site("Broken Peaks")

  test("it counts itself and every other faceup beast adviser"):
    val b = attacker(Orientation.FaceUp, beasts.map(_ -> Orientation.FaceUp))
    assert(picked(b).ops.contains(ModifyDicePool(CampaignIds.attackPool, 3)))

  test("a facedown Nature Worship is revealed and counts itself"):
    val b = attacker(Orientation.FaceDown,
      Vector(beasts.head -> Orientation.FaceUp))
    val run = picked(b)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, 2)))
    assert(Look(run.state).player(b.actor).advisers.exists {
      case DenizenState(`card`, Orientation.FaceUp, _) => true
      case _ => false })

  test("a facedown beast adviser has no suit and does not count"):
    val b = attacker(Orientation.FaceUp,
      Vector(beasts.head -> Orientation.FaceDown))
    assert(picked(b).ops.contains(ModifyDicePool(CampaignIds.attackPool, 1)))

  test("at a site its user rules it counts the beast advisers but not itself"):
    val base = board(warbands = 5, supply = 7)
    val atSite = withSiteCard(actorRules(base, ruledSite), ruledSite, card)
    val advisedToo = advised(atSite, base.actor,
      Vector(beasts.head -> Orientation.FaceUp))
    val b = withSecrets(advisedToo, 1)
    assert(picked(b).ops.contains(ModifyDicePool(CampaignIds.attackPool, 1)))

  test("at a site with no beast adviser it would add nothing, so it " +
      "is not offered"):
    val base = board(warbands = 5, supply = 7)
    val atSite = withSiteCard(actorRules(base, ruledSite), ruledSite, card)
    val b = withSecrets(atSite, 1)
    assert(awaits(commit(rules(winning), b, 2), b.actor, CampaignIds.sacrifice))

  test("a defender removes one attack die per beast adviser"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(advised(base, base.rival, Vector(
      card -> Orientation.FaceUp, beasts.head -> Orientation.FaceUp)))(
      _.secrets(base.rival, faceUp = 1))
    val run = commit(rules(winning), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))
