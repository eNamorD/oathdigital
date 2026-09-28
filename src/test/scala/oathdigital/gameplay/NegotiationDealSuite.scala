package oathdigital.gameplay

import oathdigital.gameplay.actions.negotiation.NegotiationDeal
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseManyAnswer, DeclineDeal, ProposeTerms}
import oathdigital.model.OathViolation.InsufficientFavor
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

/** The deal as a pure function of the answers recorded so far. */
class NegotiationDealSuite extends munit.FunSuite:
  private val site = CatalogNames.site("Broken Peaks")
  private val p1Relic = CatalogNames.relic("Brass Horse")
  private val p2Relic = CatalogNames.relic("Truthful Harp")
  private val siteRelic = CatalogNames.relic("Sticky Fire")

  /** Every player stands at Broken Peaks with 5 favor and one facedown
    * relic: p1 holds Brass Horse with a secret on it, p2 Truthful Harp and
    * p3 Grand Mask. Broken Peaks also holds Sticky Fire, which p1 knows. */
  private def gathered: Table = Table.start
    .pawn(p1, at = site).pawn(p3, at = site)       // p2 already stands there
    .favor(p1, 5).favor(p2, 5).favor(p3, 5)
    .relic(p1, p1Relic, facedown = true).tokens(p1Relic, secrets = 1)
    .relic(p2, p2Relic, facedown = true)
    .relic(p3, "Grand Mask", facedown = true)
    .relicAt(siteRelic, at = site)
    .knowsRelicAt(p1, siteRelic, at = site)

  private val ready: ReadyGame = gathered.ready
  private val (a, p, q) = (p1, p2, p3)
  private val dealId = NegotiationDeal.dealDecisionId

  private def said(by: PlayerId, answer: DecisionAnswer) =
    Answered(dealId, answer, by)
  private val gift = NegotiationTerms(Vector(NegotiationTransfer(p, 2, Vector.empty)))
  private def fold(answers: Answered*) =
    NegotiationDeal.fold(Vector(a, p), answers.toVector)

  test("a fresh deal is empty, open and not agreed"):
    val deal = fold()
    assertEquals(deal.terms, Map(a -> NegotiationTerms(), p -> NegotiationTerms()))
    assert(!deal.hasSubstance && !deal.closed && !deal.agreed)

  test("a proposal replaces its author's whole terms and clears every acceptance"):
    val deal = fold(said(a, ProposeTerms(gift)), said(p, AcceptDeal),
      said(p, ProposeTerms(NegotiationTerms())))
    assertEquals(deal.terms(a), gift)
    assertEquals(deal.terms(p), NegotiationTerms())
    assertEquals(deal.accepted, Set.empty[PlayerId])
    assert(deal.hasSubstance)

  test("the deal is agreed only when every participant accepted terms with substance"):
    val once = fold(said(a, ProposeTerms(gift)), said(p, AcceptDeal))
    assert(!once.closed && !once.agreed)
    val all = fold(said(a, ProposeTerms(gift)), said(p, AcceptDeal),
      said(a, AcceptDeal))
    assert(all.closed && all.agreed)

  test("a decline closes the deal without agreeing it"):
    val deal = fold(said(a, ProposeTerms(gift)), said(a, AcceptDeal),
      said(p, DeclineDeal))
    assert(deal.closed && deal.declined && !deal.agreed)

  test("answers to other decisions are ignored"):
    val stray = Answered(NegotiationDeal.negotiatorsDecisionId,
      ChooseManyAnswer(Vector(DecisionOptionRef.Player(p))), a)
    assertEquals(fold(stray), fold())

  test("eligible lists the other players at the actor's site, in table order"):
    assertEquals(NegotiationDeal.eligible(ready, a), Vector(p, q))
    assertEquals(NegotiationDeal.eligible(
      gathered.pawn(p3, at = "Dunes").ready, a), Vector(p))
    assertEquals(NegotiationDeal.eligible(
      gathered.pawn(p2, at = "Dunes").pawn(p3, at = "Dunes").ready, a),
      Vector.empty[PlayerId])

  test("participants come from the negotiator answer, else the one candidate"):
    val chosen = PendingTree(Vector("1"), Vector(Answered(
      NegotiationDeal.negotiatorsDecisionId,
      ChooseManyAnswer(Vector(DecisionOptionRef.Player(q))), a)))
    assertEquals(NegotiationDeal.participants(ready, a, chosen), Vector(a, q))
    val forced = gathered.pawn(p3, at = "Dunes")   // p2 is the one candidate
    assertEquals(NegotiationDeal.participants(forced.ready, a,
      PendingTree(Vector.empty, Vector.empty)), Vector(a, p))

  test("the snapshot carries each author's own bounds and who may accept"):
    val deal = fold(said(a, ProposeTerms(gift)), said(a, AcceptDeal))
    val query = NegotiationDeal.snapshot(ready, deal)
    assertEquals(query.participants, Vector(a, p))
    assertEquals(query.terms(a), gift)
    assertEquals(query.accepted, Set(a))
    assertEquals(query.acceptors, Set(p))
    val own = query.bounds(a)
    assertEquals(own.recipients, Vector(p))
    assertEquals(own.maxFavor, 5)
    assertEquals(own.relics, Vector(p1Relic))
    assert(own.disclosures.contains(
      NegotiationDisclosureRef.HeldRelic(a, p1Relic)))
    assert(own.disclosures.contains(
      NegotiationDisclosureRef.SiteRelic(site, siteRelic)))
    assert(query.bounds(p).disclosures.contains(
      NegotiationDisclosureRef.HeldRelic(p, p2Relic)))
    assert(!query.bounds(p).disclosures.exists {
      case NegotiationDisclosureRef.SiteRelic(_, _) => true
      case _ => false
    })

  test("an empty deal has no acceptors"):
    assertEquals(NegotiationDeal.snapshot(ready, fold()).acceptors,
      Set.empty[PlayerId])

  test("settlement records disclosures before transfers, in participant order"):
    val terms = NegotiationTerms(
      Vector(NegotiationTransfer(p, 3, Vector(p1Relic))),
      Vector(NegotiationDisclosure(p,
        NegotiationDisclosureRef.HeldRelic(a, p1Relic))))
    val ops = NegotiationDeal.settle(ready,
      fold(said(a, ProposeTerms(terms)))).toOption.get
    assertEquals(ops, Vector[CoreOperation](
      Peek(p, p1Relic, Location.PlayArea(a)),
      Give(Piece.Favor(3), a, Location.PlayArea(a), Location.PlayArea(p)),
      Give(Piece.Card(p1Relic), a, Location.PlayArea(a),
        Location.PlayArea(p))))

  test("settlement is refused when an author can no longer afford their terms"):
    val terms = NegotiationTerms(Vector(NegotiationTransfer(p, 3, Vector.empty)))
    val broke = gathered.favor(p1, 1).ready
    assertEquals(NegotiationDeal.settle(broke,
      fold(said(a, ProposeTerms(terms)))), Left(InsufficientFavor(3, 1)))
    val gone = terms.copy(transfers = Vector(NegotiationTransfer(p, 0,
      Vector(p2Relic))))
    assert(NegotiationDeal.settle(ready,
      fold(said(a, ProposeTerms(gone)))).isLeft)
