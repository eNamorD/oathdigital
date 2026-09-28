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
  private val dealId = NegotiationDeal.dealDecisionId

  private def said(by: PlayerId, answer: DecisionAnswer) =
    Answered(dealId, answer, by)
  private val gift = NegotiationTerms(Vector(NegotiationTransfer(p2, 2, Vector.empty)))
  private def fold(answers: Answered*) =
    NegotiationDeal.fold(Vector(p1, p2), answers.toVector)

  test("a fresh deal is empty, open and not agreed"):
    val deal = fold()
    assertEquals(deal.terms, Map(p1 -> NegotiationTerms(), p2 -> NegotiationTerms()))
    assert(!deal.hasSubstance && !deal.closed && !deal.agreed)

  test("a proposal replaces its author's whole terms and clears every acceptance"):
    val deal = fold(said(p1, ProposeTerms(gift)), said(p2, AcceptDeal),
      said(p2, ProposeTerms(NegotiationTerms())))
    assertEquals(deal.terms(p1), gift)
    assertEquals(deal.terms(p2), NegotiationTerms())
    assertEquals(deal.accepted, Set.empty[PlayerId])
    assert(deal.hasSubstance)

  test("the deal is agreed only when every participant accepted terms with substance"):
    val once = fold(said(p1, ProposeTerms(gift)), said(p2, AcceptDeal))
    assert(!once.closed && !once.agreed)
    val all = fold(said(p1, ProposeTerms(gift)), said(p2, AcceptDeal),
      said(p1, AcceptDeal))
    assert(all.closed && all.agreed)

  test("a decline closes the deal without agreeing it"):
    val deal = fold(said(p1, ProposeTerms(gift)), said(p1, AcceptDeal),
      said(p2, DeclineDeal))
    assert(deal.closed && deal.declined && !deal.agreed)

  test("answers to other decisions are ignored"):
    val stray = Answered(NegotiationDeal.negotiatorsDecisionId,
      ChooseManyAnswer(Vector(DecisionOptionRef.Player(p2))), p1)
    assertEquals(fold(stray), fold())

  test("eligible lists the other players at the actor's site, in table order"):
    assertEquals(NegotiationDeal.eligible(ready, p1), Vector(p2, p3))
    assertEquals(NegotiationDeal.eligible(
      gathered.pawn(p3, at = "Dunes").ready, p1), Vector(p2))
    assertEquals(NegotiationDeal.eligible(
      gathered.pawn(p2, at = "Dunes").pawn(p3, at = "Dunes").ready, p1),
      Vector.empty[PlayerId])

  test("participants come from the negotiator answer, else the one candidate"):
    val chosen = PendingTree(Vector("1"), Vector(Answered(
      NegotiationDeal.negotiatorsDecisionId,
      ChooseManyAnswer(Vector(DecisionOptionRef.Player(p3))), p1)))
    assertEquals(NegotiationDeal.participants(ready, p1, chosen), Vector(p1, p3))
    val forced = gathered.pawn(p3, at = "Dunes")   // p2 is the one candidate
    assertEquals(NegotiationDeal.participants(forced.ready, p1,
      PendingTree(Vector.empty, Vector.empty)), Vector(p1, p2))

  test("the snapshot carries each author's own bounds and who may accept"):
    val deal = fold(said(p1, ProposeTerms(gift)), said(p1, AcceptDeal))
    val query = NegotiationDeal.snapshot(ready, deal)
    assertEquals(query.participants, Vector(p1, p2))
    assertEquals(query.terms(p1), gift)
    assertEquals(query.accepted, Set(p1))
    assertEquals(query.acceptors, Set(p2))
    val own = query.bounds(p1)
    assertEquals(own.recipients, Vector(p2))
    assertEquals(own.maxFavor, 5)
    assertEquals(own.relics, Vector(p1Relic))
    assert(own.disclosures.contains(
      NegotiationDisclosureRef.HeldRelic(p1, p1Relic)))
    assert(own.disclosures.contains(
      NegotiationDisclosureRef.SiteRelic(site, siteRelic)))
    assert(query.bounds(p2).disclosures.contains(
      NegotiationDisclosureRef.HeldRelic(p2, p2Relic)))
    assert(!query.bounds(p2).disclosures.exists {
      case NegotiationDisclosureRef.SiteRelic(_, _) => true
      case _ => false
    })

  test("an empty deal has no acceptors"):
    assertEquals(NegotiationDeal.snapshot(ready, fold()).acceptors,
      Set.empty[PlayerId])

  test("settlement records disclosures before transfers, in participant order"):
    val terms = NegotiationTerms(
      Vector(NegotiationTransfer(p2, 3, Vector(p1Relic))),
      Vector(NegotiationDisclosure(p2,
        NegotiationDisclosureRef.HeldRelic(p1, p1Relic))))
    val ops = NegotiationDeal.settle(ready,
      fold(said(p1, ProposeTerms(terms)))).toOption.get
    assertEquals(ops, Vector[CoreOperation](
      Peek(p2, p1Relic, Location.PlayArea(p1)),
      Give(Piece.Favor(3), p1, Location.PlayArea(p1), Location.PlayArea(p2)),
      Give(Piece.Card(p1Relic), p1, Location.PlayArea(p1),
        Location.PlayArea(p2))))

  test("settlement is refused when an author can no longer afford their terms"):
    val terms = NegotiationTerms(Vector(NegotiationTransfer(p2, 3, Vector.empty)))
    val broke = gathered.favor(p1, 1).ready
    assertEquals(NegotiationDeal.settle(broke,
      fold(said(p1, ProposeTerms(terms)))), Left(InsufficientFavor(3, 1)))
    val gone = terms.copy(transfers = Vector(NegotiationTransfer(p2, 0,
      Vector(p2Relic))))
    assert(NegotiationDeal.settle(ready,
      fold(said(p1, ProposeTerms(gone)))).isLeft)
