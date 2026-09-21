package oathdigital.application

import oathdigital.gameplay.NegotiationFixture
import oathdigital.gameplay.NegotiationFixture.Board
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.negotiation.NegotiationDeal
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseManyAnswer, ProposeTerms}
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.{GameProjection, NegotiationDealProjection}

/** What each viewer of a parked deal is shown. */
class NegotiationDealProjectionSuite extends munit.FunSuite {
  private val rules = new OathRules(catalog)
  private val projector = new GameProjector(catalog)
  private val dealId = NegotiationDeal.dealDecisionId

  private def parkedDeal(b: Board, terms: Option[NegotiationTerms] = None,
      who: Vector[PlayerId] = Vector.empty): OathState = {
    val started = rules.startWalker(Ready(b.ready), ActionRef.Negotiation,
      b.actor).getOrElse(fail("Negotiation must start"))
    val chosen = rules.resolveWalker(started.state, b.actor,
      NegotiationDeal.negotiatorsDecisionId, ChooseManyAnswer(
        (if (who.isEmpty) Vector(b.second, b.third) else who)
          .map(DecisionOptionRef.Player(_)))).getOrElse(fail("negotiators"))
    terms.fold(chosen.state)(value => rules.resolveWalker(chosen.state, b.actor,
      dealId, ProposeTerms(value)).getOrElse(fail("terms")).state)
  }

  private def view(state: OathState, viewer: PlayerId): GameProjection =
    projector.project("negotiation", LoadedGame(state, 30), viewer)

  private def deal(projection: GameProjection): NegotiationDealProjection =
    projection.walkerDecision.flatMap(_.query).flatMap(_.deal)
      .orElse(projection.walkerWaiting.flatMap(_.deal))
      .getOrElse(fail("the deal must be projected"))

  test("the actor and every co-owner get the full decision with editing inputs") {
    val b = NegotiationFixture.board()
    val state = parkedDeal(b)
    Vector(b.actor, b.second, b.third).foreach { viewer =>
      val projection = view(state, viewer)
      assertEquals(projection.walkerWaiting, None, viewer.value)
      val decision = projection.walkerDecision.getOrElse(fail("owner decision"))
      assertEquals(decision.decisionId, dealId)
      assertEquals(decision.query.map(_.form), Some("negotiate"))
      val editing = deal(projection).editing.getOrElse(fail("editing"))
      assertEquals(editing.editableFavor, 5)
      assert(!editing.canAccept)
      assert(projection.legalControls.contains("resolveWalkerDecision"))
    }
  }

  test("a player outside the deal and the public view see it read-only") {
    val b = NegotiationFixture.board()
    val state = parkedDeal(b, who = Vector(b.second))
    val outsider = view(state, b.third)
    assertEquals(outsider.walkerDecision, None)
    val waiting = outsider.walkerWaiting.getOrElse(fail("waiting"))
    assertEquals(waiting.playerId, b.actor.value)
    assertEquals(waiting.coOwnerPlayerIds, Vector(b.second.value))
    assertEquals(deal(outsider).participantPlayerIds,
      Vector(b.actor.value, b.second.value))
    assertEquals(deal(outsider).editing, None)
    assert(!outsider.legalControls.contains("resolveWalkerDecision"))
    val public = projector.projectPublic("negotiation", LoadedGame(state, 30))
    assertEquals(deal(public).editing, None)
    assertEquals(public.walkerDecision, None)
  }

  test("terms show amounts to everyone but hide identities from everyone but their author") {
    val b = NegotiationFixture.board()
    val terms = NegotiationTerms(
      Vector(NegotiationTransfer(b.second, 3, Vector(b.actorRelic))),
      Vector(NegotiationDisclosure(b.second,
        NegotiationDisclosureRef.HeldRelic(b.actor, b.actorRelic))))
    val state = parkedDeal(b, Some(terms), Vector(b.second))
    val author = deal(view(state, b.actor))
    assertEquals(author.transfers.head.favor, 3)
    assertEquals(author.transfers.head.relicCount, 1)
    assertEquals(author.transfers.head.relics.map(_.cardId), Vector(b.actorRelic.value))
    assertEquals(author.disclosures.head.card.map(_.cardId), Some(b.actorRelic.value))
    val others = Vector(view(state, b.second), view(state, b.third),
      projector.projectPublic("negotiation", LoadedGame(state, 30)))
    others.foreach { projection =>
      val seen = deal(projection)
      assertEquals(seen.transfers.head.favor, 3)
      assertEquals(seen.transfers.head.relicCount, 1)
      assertEquals(seen.transfers.head.relics, Vector.empty)
      assertEquals(seen.disclosures.head.kind, "held-relic")
      assertEquals(seen.disclosures.head.recipientPlayerId, b.second.value)
      assertEquals(seen.disclosures.head.card, None)
    }
  }

  test("acceptances and the right to accept are projected") {
    val b = NegotiationFixture.board()
    val proposed = parkedDeal(b, Some(NegotiationTerms(
      Vector(NegotiationTransfer(b.second, 1, Vector.empty)))), Vector(b.second))
    assert(deal(view(proposed, b.second)).editing.exists(_.canAccept))
    val accepted = rules.resolveWalker(proposed, b.second, dealId, AcceptDeal)
      .getOrElse(fail("accept")).state
    val seen = deal(view(accepted, b.third))
    assertEquals(seen.acceptedPlayerIds, Vector(b.second.value))
    assert(!deal(view(accepted, b.second)).editing.exists(_.canAccept))
    assert(deal(view(accepted, b.actor)).editing.exists(_.canAccept))
  }

  test("the start control is offered while a candidate exists and not once parked") {
    val b = NegotiationFixture.board()
    assert(view(Ready(b.ready), b.actor).legalControls.contains("beginNegotiation"))
    assert(!view(Ready(NegotiationFixture.isolated(b).ready), b.actor)
      .legalControls.contains("beginNegotiation"))
    assert(!view(parkedDeal(b), b.actor).legalControls.contains("beginNegotiation"))
  }

  test("Negotiation is offered as a start control, not as a board-target selection") {
    val b = NegotiationFixture.board()
    val projection = view(Ready(b.ready), b.actor)
    assert(projection.legalControls.contains("beginNegotiation"))
    assert(!projection.boardTargetActions.exists(_.actionKind == "negotiation"))
  }

  test("a faceup relic in a transfer is shown to every viewer, public included") {
    val b = NegotiationFixture.board()
    val faceUp = b.copy(ready = b.ready.updateCurrent(current => current.copy(
      players = current.players.map(p => if (p.player == b.actor)
        p.copy(relics = p.relics.map(_.copy(orientation = Orientation.FaceUp)))
        else p))))
    val terms = NegotiationTerms(Vector(NegotiationTransfer(b.second, 1,
      Vector(b.actorRelic))))
    val state = parkedDeal(faceUp, Some(terms), Vector(b.second))
    val views = Vector(view(state, b.actor), view(state, b.second),
      view(state, b.third),
      projector.projectPublic("negotiation", LoadedGame(state, 30)))
    views.foreach(projection => assertEquals(
      deal(projection).transfers.head.relics.map(_.cardId),
      Vector(b.actorRelic.value)))
  }
}
