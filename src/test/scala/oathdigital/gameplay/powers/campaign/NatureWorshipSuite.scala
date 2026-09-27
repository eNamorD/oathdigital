package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Nature Worship: a secret placed for one attack die, added or removed, per
  * faceup beast adviser its user has, itself included. */
class NatureWorshipSuite extends munit.FunSuite:
  private val card = cardWith("denizen.nature-worship")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val beasts = inert(Suit.Beast, 2)

  private def advised(b: Board, who: PlayerId,
      cards: Vector[(String, Orientation)]): Board =
    cards.foldLeft(b) { case (done, (held, side)) =>
      withAdviserFor(done, who, held, side) }

  private def attacker(self: Orientation,
      others: Vector[(String, Orientation)]): Board =
    val base = board()
    withSecrets(advised(base, base.actor, (card -> self) +: others), 1)

  private def picked(b: Board): Run =
    commit(rules(winning), b, 2).pick(b.actor, CampaignIds.attackerPlan, ref)

  private def ruledSite(b: Board): SiteId =
    b.ready.game.current.map.inPlay.find(_ != b.origin).get

  test("it counts itself and every other faceup beast adviser"):
    val b = attacker(Orientation.FaceUp, beasts.map(_ -> Orientation.FaceUp))
    assert(picked(b).ops.contains(ModifyDicePool(CampaignIds.attackPool, 3)))

  test("a facedown Nature Worship is revealed and counts itself"):
    val b = attacker(Orientation.FaceDown,
      Vector(beasts.head -> Orientation.FaceUp))
    val run = picked(b)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, 2)))
    assert(player(run.state, b.actor).advisers.exists {
      case DenizenState(`id`, Orientation.FaceUp, _) => true
      case _ => false })

  test("a facedown beast adviser has no suit and does not count"):
    val b = attacker(Orientation.FaceUp,
      Vector(beasts.head -> Orientation.FaceDown))
    assert(picked(b).ops.contains(ModifyDicePool(CampaignIds.attackPool, 1)))

  test("at a site its user rules it counts the beast advisers but not itself"):
    val base = board()
    val site = ruledSite(base)
    val b = withSecrets(advised(withSiteCard(actorRules(base, site), site, card),
      base.actor, Vector(beasts.head -> Orientation.FaceUp)), 1)
    assert(picked(b).ops.contains(ModifyDicePool(CampaignIds.attackPool, 1)))

  test("at a site with no beast adviser it would add nothing, so it is not offered"):
    val base = board()
    val site = ruledSite(base)
    val b = withSecrets(withSiteCard(actorRules(base, site), site, card), 1)
    assert(awaits(commit(rules(winning), b, 2), b.actor, CampaignIds.sacrifice))

  test("a defender removes one attack die per beast adviser"):
    val base = againstPlayer(board())
    val b = replacePlayer(advised(base, base.other, Vector(
      card -> Orientation.FaceUp, beasts.head -> Orientation.FaceUp)),
      base.other)(p => p.copy(board = p.board.copy(faceUpSecrets = 1)))
    val run = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))

  test("the card is found in the catalog and registered once"):
    assertEquals(SimplePlans.forCatalog(catalog).count(_.id == NatureWorship.id),
      1)
