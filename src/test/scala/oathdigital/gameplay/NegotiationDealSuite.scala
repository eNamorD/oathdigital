package oathdigital.gameplay

import oathdigital.gameplay.actions.negotiation.{DealState, NegotiationDeal}
import oathdigital.gameplay.NegotiationFixture.Board
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseManyAnswer, DeclineDeal, ProposeTerms}
import oathdigital.model.OathViolation.InsufficientFavor

/** The deal as a pure function of the answers recorded so far. */
class NegotiationDealSuite extends munit.FunSuite {
  private val b: Board = NegotiationFixture.board()
  private val (a, p, q) = (b.actor, b.second, b.third)
  private val dealId = NegotiationDeal.dealDecisionId

  private def said(by: PlayerId, answer: DecisionAnswer) =
    Answered(dealId, answer, by)
  private val gift = NegotiationTerms(Vector(NegotiationTransfer(p, 2, Vector.empty)))
  private def fold(answers: Answered*) =
    NegotiationDeal.fold(Vector(a, p), answers.toVector)

  test("a fresh deal is empty, open and not agreed") {
    val deal = fold()
    assertEquals(deal.terms, Map(a -> NegotiationTerms(), p -> NegotiationTerms()))
    assert(!deal.hasSubstance && !deal.closed && !deal.agreed)
  }

  test("a proposal replaces its author's whole terms and clears every acceptance") {
    val deal = fold(said(a, ProposeTerms(gift)), said(p, AcceptDeal),
      said(p, ProposeTerms(NegotiationTerms())))
    assertEquals(deal.terms(a), gift)
    assertEquals(deal.terms(p), NegotiationTerms())
    assertEquals(deal.accepted, Set.empty[PlayerId])
    assert(deal.hasSubstance)
  }

  test("the deal is agreed only when every participant accepted terms with substance") {
    val once = fold(said(a, ProposeTerms(gift)), said(p, AcceptDeal))
    assert(!once.closed && !once.agreed)
    val all = fold(said(a, ProposeTerms(gift)), said(p, AcceptDeal),
      said(a, AcceptDeal))
    assert(all.closed && all.agreed)
  }

  test("a decline closes the deal without agreeing it") {
    val deal = fold(said(a, ProposeTerms(gift)), said(a, AcceptDeal),
      said(p, DeclineDeal))
    assert(deal.closed && deal.declined && !deal.agreed)
  }

  test("answers to other decisions are ignored") {
    val stray = Answered(NegotiationDeal.negotiatorsDecisionId,
      ChooseManyAnswer(Vector(DecisionOptionRef.Player(p))), a)
    assertEquals(fold(stray), fold())
  }

  test("eligible lists the other players at the actor's site, in table order") {
    assertEquals(NegotiationDeal.eligible(b.ready, a), Vector(p, q))
    assertEquals(NegotiationDeal.eligible(
      NegotiationFixture.withThirdElsewhere(b).ready, a), Vector(p))
    assertEquals(NegotiationDeal.eligible(
      NegotiationFixture.isolated(b).ready, a), Vector.empty[PlayerId])
  }

  test("participants come from the negotiator answer, else the one candidate") {
    val chosen = PendingTree(Vector("1"), Vector(Answered(
      NegotiationDeal.negotiatorsDecisionId,
      ChooseManyAnswer(Vector(DecisionOptionRef.Player(q))), a)))
    assertEquals(NegotiationDeal.participants(b.ready, a, chosen), Vector(a, q))
    val forced = NegotiationFixture.withThirdElsewhere(b)
    assertEquals(NegotiationDeal.participants(forced.ready, a,
      PendingTree(Vector.empty, Vector.empty)), Vector(a, p))
  }

  test("the snapshot carries each author's own bounds and who may accept") {
    val deal = fold(said(a, ProposeTerms(gift)), said(a, AcceptDeal))
    val query = NegotiationDeal.snapshot(b.ready, deal)
    assertEquals(query.participants, Vector(a, p))
    assertEquals(query.terms(a), gift)
    assertEquals(query.accepted, Set(a))
    assertEquals(query.acceptors, Set(p))
    val own = query.bounds(a)
    assertEquals(own.recipients, Vector(p))
    assertEquals(own.maxFavor, 5)
    assertEquals(own.relics, Vector(b.actorRelic))
    // The fixture leaves every player their setup advisers, so assert what the
    // relics contribute rather than the whole list.
    assert(own.disclosures.contains(
      NegotiationDisclosureRef.HeldRelic(a, b.actorRelic)))
    assert(own.disclosures.contains(
      NegotiationDisclosureRef.SiteRelic(b.site, b.siteRelic)))
    assert(query.bounds(p).disclosures.contains(
      NegotiationDisclosureRef.HeldRelic(p, b.otherRelic)))
    assert(!query.bounds(p).disclosures.exists {
      case NegotiationDisclosureRef.SiteRelic(_, _) => true
      case _ => false
    })
  }

  test("an empty deal has no acceptors") {
    assertEquals(NegotiationDeal.snapshot(b.ready, fold()).acceptors,
      Set.empty[PlayerId])
  }

  test("settlement records disclosures before transfers, in participant order") {
    val terms = NegotiationTerms(
      Vector(NegotiationTransfer(p, 3, Vector(b.actorRelic))),
      Vector(NegotiationDisclosure(p,
        NegotiationDisclosureRef.HeldRelic(a, b.actorRelic))))
    val ops = NegotiationDeal.settle(b.ready,
      fold(said(a, ProposeTerms(terms)))).toOption.get
    assertEquals(ops, Vector[CoreOperation](
      Peek(p, b.actorRelic, Location.PlayArea(a)),
      Give(Piece.Favor(3), a, Location.PlayArea(a), Location.PlayArea(p)),
      Give(Piece.Card(b.actorRelic), a, Location.PlayArea(a),
        Location.PlayArea(p))))
  }

  test("settlement is refused when an author can no longer afford their terms") {
    val terms = NegotiationTerms(Vector(NegotiationTransfer(p, 3, Vector.empty)))
    val broke = b.ready.updateCurrent(current => current.copy(players =
      current.players.map(pl => if (pl.player == a)
        pl.copy(board = pl.board.copy(favor = 1)) else pl)))
    assertEquals(NegotiationDeal.settle(broke,
      fold(said(a, ProposeTerms(terms)))), Left(InsufficientFavor(3, 1)))
    val gone = terms.copy(transfers = Vector(NegotiationTransfer(p, 0,
      Vector(b.otherRelic))))
    assert(NegotiationDeal.settle(b.ready,
      fold(said(a, ProposeTerms(gone)))).isLeft)
  }
}
