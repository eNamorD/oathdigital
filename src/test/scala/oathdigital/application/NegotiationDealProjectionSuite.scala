package oathdigital.application

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.negotiation.NegotiationDeal
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseManyAnswer, ProposeTerms}
import oathdigital.protocol.projection.{DecisionQueryProjection,
  GameProjection, NegotiationDealProjection}
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

/** What each viewer of a parked deal is shown. */
class NegotiationDealProjectionSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)
  private val projector = new GameProjector(catalog)
  private val dealId = NegotiationDeal.dealDecisionId
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

  private def parkedDeal(table: Table, terms: Option[NegotiationTerms] = None,
      who: Vector[PlayerId] = Vector.empty): OathState =
    val started = rules.startWalker(table.state, ActionRef.Negotiation, p1)
      .getOrElse(fail("Negotiation must start"))
    val chosen = rules.resolveWalker(started.state, p1,
      NegotiationDeal.negotiatorsDecisionId, ChooseManyAnswer(
        (if who.isEmpty then Vector(p2, p3) else who)
          .map(DecisionOptionRef.Player(_)))).getOrElse(fail("negotiators"))
    terms.fold(chosen.state)(value => rules.resolveWalker(chosen.state, p1,
      dealId, ProposeTerms(value)).getOrElse(fail("terms")).state)

  private def view(state: OathState, viewer: PlayerId): GameProjection =
    projector.project("negotiation", LoadedGame(state, 30), viewer)

  private def deal(projection: GameProjection): NegotiationDealProjection =
    projection.walkerDecision.flatMap(_.query).collect {
      case negotiate: DecisionQueryProjection.Negotiate => negotiate.deal }
      .orElse(projection.walkerWaiting.flatMap(_.deal))
      .getOrElse(fail("the deal must be projected"))

  test("the actor and every co-owner get the full decision with editing inputs"):
    val state = parkedDeal(gathered)
    Vector(p1, p2, p3).foreach { viewer =>
      val projection = view(state, viewer)
      assertEquals(projection.walkerWaiting, None, viewer.value)
      val decision = projection.walkerDecision.getOrElse(fail("owner decision"))
      assertEquals(decision.decisionId, dealId)
      assert(decision.query.exists(
        _.isInstanceOf[DecisionQueryProjection.Negotiate]))
      val editing = deal(projection).editing.getOrElse(fail("editing"))
      assertEquals(editing.editableFavor, 5)
      assert(!editing.canAccept)
      assert(projection.legalControls.contains("resolveWalkerDecision"))
    }

  test("a player outside the deal and the public view see it read-only"):
    val state = parkedDeal(gathered, who = Vector(p2))
    val outsider = view(state, p3)
    assertEquals(outsider.walkerDecision, None)
    val waiting = outsider.walkerWaiting.getOrElse(fail("waiting"))
    assertEquals(waiting.playerId, p1.value)
    assertEquals(waiting.coOwnerPlayerIds, Vector(p2.value))
    assertEquals(deal(outsider).participantPlayerIds,
      Vector(p1.value, p2.value))
    assertEquals(deal(outsider).editing, None)
    assert(!outsider.legalControls.contains("resolveWalkerDecision"))
    val public = projector.projectPublic("negotiation", LoadedGame(state, 30))
    assertEquals(deal(public).editing, None)
    assertEquals(public.walkerDecision, None)

  test("terms show amounts to everyone but hide identities from everyone but their author"):
    val terms = NegotiationTerms(
      Vector(NegotiationTransfer(p2, 3, Vector(p1Relic))),
      Vector(NegotiationDisclosure(p2,
        NegotiationDisclosureRef.HeldRelic(p1, p1Relic))))
    val state = parkedDeal(gathered, Some(terms), Vector(p2))
    val author = deal(view(state, p1))
    assertEquals(author.transfers.head.favor, 3)
    assertEquals(author.transfers.head.relicCount, 1)
    assertEquals(author.transfers.head.relics.map(_.cardId), Vector(p1Relic.value))
    assertEquals(author.disclosures.head.card.map(_.cardId), Some(p1Relic.value))
    val others = Vector(view(state, p2), view(state, p3),
      projector.projectPublic("negotiation", LoadedGame(state, 30)))
    others.foreach { projection =>
      val seen = deal(projection)
      assertEquals(seen.transfers.head.favor, 3)
      assertEquals(seen.transfers.head.relicCount, 1)
      assertEquals(seen.transfers.head.relics, Vector.empty)
      assertEquals(seen.disclosures.head.kind, "held-relic")
      assertEquals(seen.disclosures.head.recipientPlayerId, p2.value)
      assertEquals(seen.disclosures.head.card, None)
    }

  test("acceptances and the right to accept are projected"):
    val proposed = parkedDeal(gathered, Some(NegotiationTerms(
      Vector(NegotiationTransfer(p2, 1, Vector.empty)))), Vector(p2))
    assert(deal(view(proposed, p2)).editing.exists(_.canAccept))
    val accepted = rules.resolveWalker(proposed, p2, dealId, AcceptDeal)
      .getOrElse(fail("accept")).state
    val seen = deal(view(accepted, p3))
    assertEquals(seen.acceptedPlayerIds, Vector(p2.value))
    assert(!deal(view(accepted, p2)).editing.exists(_.canAccept))
    assert(deal(view(accepted, p1)).editing.exists(_.canAccept))

  test("the start control is offered while a candidate exists and not once parked"):
    assert(view(gathered.state, p1).legalControls.contains("beginNegotiation"))
    val alone = gathered.pawn(p2, at = "Dunes").pawn(p3, at = "Dunes")
    assert(!view(alone.state, p1).legalControls.contains("beginNegotiation"))
    assert(!view(parkedDeal(gathered), p1).legalControls.contains("beginNegotiation"))

  test("Negotiation is offered as a start control, not as a board-target selection"):
    val projection = view(gathered.state, p1)
    assert(projection.legalControls.contains("beginNegotiation"))
    assert(!projection.boardTargetActions.exists(_.actionKind == "negotiation"))

  test("a faceup relic in a transfer is shown to every viewer, public included"):
    val faceUp = gathered.relic(p1, p1Relic).tokens(p1Relic, secrets = 1)
    val terms = NegotiationTerms(Vector(NegotiationTransfer(p2, 1,
      Vector(p1Relic))))
    val state = parkedDeal(faceUp, Some(terms), Vector(p2))
    val views = Vector(view(state, p1), view(state, p2),
      view(state, p3),
      projector.projectPublic("negotiation", LoadedGame(state, 30)))
    views.foreach(projection => assertEquals(
      deal(projection).transfers.head.relics.map(_.cardId),
      Vector(p1Relic.value)))
