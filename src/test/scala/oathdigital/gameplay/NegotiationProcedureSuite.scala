package oathdigital.gameplay

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.NegotiationFixture.{Board, player}
import oathdigital.gameplay.actions.negotiation.{NegotiationDeal, NegotiationProcedure}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseManyAnswer, DeclineDeal, ProposeTerms}
import oathdigital.model.OathEvent.IgnoredRulesRecorded
import oathdigital.model.OathState.Ready

/** Negotiation through the rules, as a client drives it: start, choose the
  * negotiators, then answer the deal in any order until it closes.
  */
class NegotiationProcedureSuite extends munit.FunSuite {
  private val rules = new OathRules(catalog)
  private val negotiators = NegotiationDeal.negotiatorsDecisionId
  private val dealId = NegotiationDeal.dealDecisionId

  private def start(b: Board) =
    rules.startWalker(Ready(b.ready), ActionRef.Negotiation, b.actor)

  private def choose(state: OathState, actor: PlayerId, who: PlayerId*) =
    rules.resolveWalker(state, actor, negotiators, ChooseManyAnswer(
      who.toVector.map(DecisionOptionRef.Player(_))))

  private def say(state: OathState, by: PlayerId, answer: DecisionAnswer) =
    rules.resolveWalker(state, by, dealId, answer)

  private def ready(state: OathState): ReadyGame = state match {
    case Ready(value) => value
    case other => fail(s"expected a ready game, got $other")
  }

  private def gift(to: PlayerId, favor: Int, relics: Vector[RelicId] = Vector.empty) =
    ProposeTerms(NegotiationTerms(Vector(NegotiationTransfer(to, favor, relics))))

  /** Starts and chooses `who`, returning the state parked at the deal. */
  private def atDeal(b: Board, who: PlayerId*): OathTransition = {
    val started = start(b).getOrElse(fail("Negotiation must start"))
    choose(started.state, b.actor, who: _*).getOrElse(
      fail("the negotiators must be accepted"))
  }

  test("starting parks on the negotiator choice, offered to the actor") {
    val b = NegotiationFixture.board()
    val started = start(b).getOrElse(fail("Negotiation must start"))
    assertEquals(started.continue, OathContinue.AwaitingNegotiation(b.actor,
      DecisionId(negotiators)))
  }

  test("a lone candidate skips the negotiator choice") {
    val b = NegotiationFixture.withThirdElsewhere(NegotiationFixture.board())
    val started = start(b).getOrElse(fail("Negotiation must start"))
    assertEquals(started.continue, OathContinue.AwaitingNegotiation(b.actor,
      DecisionId(dealId)))
  }

  test("with no candidate the start is rejected and offered nowhere") {
    val b = NegotiationFixture.isolated(NegotiationFixture.board())
    assert(start(b).left.toOption.exists(
      _.isInstanceOf[OathViolation.NegotiationUnavailable]))
    assert(!NegotiationProcedure.startable(catalog, b.ready, b.actor,
      WalkerPowers.empty))
    val open = NegotiationFixture.board()
    assert(NegotiationProcedure.startable(catalog, open.ready, open.actor,
      WalkerPowers.empty))
  }

  test("no first-game gate: an Imperial player or an altered Foundation still negotiates") {
    val b = NegotiationFixture.board()
    val lineage = b.ready.game.campaign.lineages(player(b.ready, b.second).lineage)
    val campaign = b.ready.game.campaign
    val citizen = b.ready.copy(game = b.ready.game.copy(campaign =
      campaign.copy(lineages = campaign.lineages.updated(lineage.id,
        lineage.copy(role = Role.Citizen)))))
    assert(start(b.copy(ready = citizen)).isRight)
    val altered = b.ready.copy(game = b.ready.game.copy(campaign =
      campaign.copy(foundations = campaign.foundations.map { case (k, f) =>
        k -> f.copy(face = FoundationFace.Altered) })))
    assert(start(b.copy(ready = altered)).isRight)
  }

  test("a bilateral favor and relic transfer settles atomically on the last accept") {
    val b = NegotiationFixture.board()
    val deal = atDeal(b, b.second)
    assertEquals(deal.continue, OathContinue.AwaitingNegotiation(b.actor,
      DecisionId(dealId)))
    val proposed = say(deal.state, b.actor, gift(b.second, 3, Vector(b.actorRelic)))
      .getOrElse(fail("terms must be accepted"))
    val theirs = say(proposed.state, b.second, AcceptDeal)
      .getOrElse(fail("the counterparty may accept first"))
    val done = say(theirs.state, b.actor, AcceptDeal)
      .getOrElse(fail("the last accept must settle"))
    val after = ready(done.state)
    assertEquals(player(after, b.actor).board.favor, 2)
    assertEquals(player(after, b.second).board.favor, 8)
    assertEquals(after.game.current.players.map(_.board.favor).sum,
      b.ready.game.current.players.map(_.board.favor).sum)
    assertEquals(player(after, b.second).relics.find(_.id == b.actorRelic)
      .get.tokens, Tokens(0, 1))
    assertEquals(after.game.current.walkerPending, None)
    assertEquals(done.continue, OathContinue.ActActionSelection(b.actor))
  }

  test("three players answer in any order and a changed term resets consent") {
    val b = NegotiationFixture.board()
    val deal = atDeal(b, b.second, b.third)
    val one = say(deal.state, b.actor, gift(b.second, 1)).toOption.get
    val two = say(one.state, b.second, AcceptDeal).toOption.get
    val three = say(two.state, b.third, gift(b.actor, 2)).toOption.get
    val open = ready(three.state).game.current.walkerPending.get
    val folded = NegotiationDeal.fold(Vector(b.actor, b.second, b.third),
      open.answered)
    assertEquals(folded.accepted, Set.empty[PlayerId])
    val accepted = Vector(b.third, b.second, b.actor).foldLeft(three.state) {
      (state, by) => say(state, by, AcceptDeal).getOrElse(fail(s"$by accepts"))
        .state
    }
    assertEquals(player(ready(accepted), b.actor).board.favor, 5 - 1 + 2)
    assertEquals(ready(accepted).game.current.walkerPending, None)
  }

  test("a decline ends the action with nothing moved") {
    val b = NegotiationFixture.board()
    val deal = atDeal(b, b.second)
    val proposed = say(deal.state, b.actor, gift(b.second, 3)).toOption.get
    val declined = say(proposed.state, b.second, DeclineDeal)
      .getOrElse(fail("any participant may decline"))
    val after = ready(declined.state)
    assertEquals(after.game.current.players, b.ready.game.current.players)
    assertEquals(after.game.current.walkerPending, None)
    assertEquals(declined.continue, OathContinue.ActActionSelection(b.actor))
  }

  test("a player outside the chosen negotiators cannot answer") {
    val b = NegotiationFixture.board()
    val deal = atDeal(b, b.second)
    assertEquals(say(deal.state, b.third, gift(b.actor, 1)).left.toOption,
      Some(OathViolation.WrongPlayer(b.actor, b.third)))
  }

  test("terms beyond the author's means, and an empty deal's accept, are rejected") {
    val b = NegotiationFixture.board()
    val deal = atDeal(b, b.second)
    assertEquals(say(deal.state, b.actor, gift(b.second, 6)).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "decision negotiation.deal offers more than 5 favor")))
    assertEquals(say(deal.state, b.second, AcceptDeal).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "decision negotiation.deal does not let this player accept now")))
  }

  test("a settlement that cannot be met rejects the last accept and leaves the deal open") {
    val b = NegotiationFixture.board()
    val deal = atDeal(b, b.second)
    val proposed = say(deal.state, b.actor, gift(b.second, 3)).toOption.get
    val theirs = say(proposed.state, b.second, AcceptDeal).toOption.get
    val broke = Ready(ready(theirs.state).updateCurrent(current =>
      current.copy(players = current.players.map(p => if (p.player == b.actor)
        p.copy(board = p.board.copy(favor = 1)) else p))))
    assertEquals(say(broke, b.actor, AcceptDeal).left.toOption,
      Some(OathViolation.InsufficientFavor(3, 1)))
    assert(say(theirs.state, b.actor, AcceptDeal).isRight)
  }

  test("an agreed disclosure grants durable knowledge to its recipient only") {
    val b = NegotiationFixture.board()
    val adviser = player(b.ready, b.actor).advisers.head.id
      .asInstanceOf[WorldCardId]
    val deal = atDeal(b, b.second, b.third)
    val terms = ProposeTerms(NegotiationTerms(disclosures = Vector(
      NegotiationDisclosure(b.second,
        NegotiationDisclosureRef.Adviser(b.actor, adviser)),
      NegotiationDisclosure(b.second,
        NegotiationDisclosureRef.SiteRelic(b.site, b.siteRelic)))))
    val proposed = say(deal.state, b.actor, terms).getOrElse(fail("terms"))
    val closed = Vector(b.actor, b.second, b.third).foldLeft(proposed.state) {
      (state, by) => say(state, by, AcceptDeal).getOrElse(fail(s"$by accepts"))
        .state
    }
    val after = ready(closed)
    assert(after.knowledge.advisers(b.second).contains(adviser))
    assert(after.knowledge.siteRelics(b.second)(b.site).contains(b.siteRelic))
    assert(!after.knowledge.advisers.getOrElse(b.third, Vector.empty)
      .contains(adviser))
  }

  test("closing a deal runs the action boundary, whether declined or agreed") {
    val b = NegotiationFixture.board()
    val emptySite = b.ready.game.current.map.inPlay.find(_ != b.site).get
    val boundary = b.copy(ready = b.ready.updateCurrent(current => current.copy(
      map = current.map.copy(sites = current.map.sites.updated(emptySite,
        current.map.sites(emptySite).copy(forces = SiteForces.Empty))))))
    val deal = atDeal(boundary, b.second)
    val declined = say(deal.state, b.second, DeclineDeal)
      .getOrElse(fail("any participant may decline"))
    assert(declined.events.exists(_.isInstanceOf[OathEvent.BanditsRefilled]),
      "a decline must run the action boundary and its bandit refill")
    val proposed = say(deal.state, b.actor, gift(b.second, 1))
      .getOrElse(fail("terms"))
    val agreed = Vector(b.second, b.actor).foldLeft(proposed.state) {
      (state, by) => say(state, by, AcceptDeal).getOrElse(fail(s"$by accepts"))
        .state
    }
    assertEquals(ready(agreed).game.current.walkerPending, None)
  }

  test("unsupported Negotiation rules are recorded as ignored, not blocking") {
    val b = NegotiationFixture.board()
    val definition = catalog.denizens.find(
      _.handlers.contains("denizen.council-arbiter")).get
    val powered = b.ready.updateCurrent(current => current.copy(players =
      current.players.map(p => if (p.player == b.actor) p.copy(advisers =
        Vector(DenizenState(DenizenId(definition.id.value), Orientation.FaceUp,
          Tokens.empty))) else p)))
    val started = start(b.copy(ready = powered)).getOrElse(
      fail("an unsupported rule must not block"))
    val recorded = started.events.head.asInstanceOf[IgnoredRulesRecorded]
    assertEquals(recorded.action, ActionKind.Negotiation)
    assertEquals(recorded.diagnostics.head.handlerId, "denizen.council-arbiter")
    assert(choose(started.state, b.actor, b.second).isRight)
  }

  test("a completed Negotiation replays exactly from its journal") {
    val (setupState, setupEvents) = execute(new FirstGameSetupRules(catalog))
    val Ready(original) = setupState: @unchecked
    val actor = original.game.current.turn.activePlayer
    val other = original.game.current.players.find(_.player != actor).get.player
    val destination = player(original, other).pawnSite.get
    val act = rules.startWalker(setupState, PhaseTransitionRef.EndWake, actor)
      .toOption.get
    val traveled = rules.startWalker(act.state, ActionRef.Travel, actor,
      Vector.empty, Vector(DecisionOptionRef.Site(destination))).toOption.get
    val adviser = player(ready(traveled.state), actor).advisers.head.id
      .asInstanceOf[WorldCardId]
    val started = rules.startWalker(traveled.state, ActionRef.Negotiation, actor)
      .getOrElse(fail("Negotiation must start"))
    val chosen =
      if (started.continue == OathContinue.AwaitingNegotiation(actor,
          DecisionId(negotiators)))
        choose(started.state, actor, other).getOrElse(fail("negotiators"))
      else started
    val terms = ProposeTerms(NegotiationTerms(disclosures = Vector(
      NegotiationDisclosure(other, NegotiationDisclosureRef.Adviser(actor,
        adviser)))))
    val proposed = say(chosen.state, actor, terms).getOrElse(fail("terms"))
    val a = say(proposed.state, actor, AcceptDeal).getOrElse(fail("actor"))
    val done = say(a.state, other, AcceptDeal).getOrElse(fail("other"))
    val events = setupEvents ++ act.events ++ traveled.events ++ started.events ++
      chosen.events.filterNot(started.events.contains) ++ proposed.events ++
      a.events ++ done.events
    val replayed = new EventReplayEngine(rules).replay(events.zipWithIndex.map {
      case (event, index) => RecordedEvent(index.toLong, event)
    }).toOption.get
    assertEquals(replayed, done.state)
  }
}
