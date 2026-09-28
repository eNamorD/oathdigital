package oathdigital.gameplay

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.actions.negotiation.{NegotiationDeal, NegotiationProcedure}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerPowers}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseManyAnswer, DeclineDeal, ProposeTerms}
import oathdigital.model.OathEvent.IgnoredRulesRecorded
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

/** Negotiation through the rules, as a client drives it: start, choose the
  * negotiators, then answer the deal in any order until it closes.
  */
class NegotiationProcedureSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)
  private val parked = new ParkedDecisionAssertions(catalog)
  private val negotiators = NegotiationDeal.negotiatorsDecisionId
  private val dealId = NegotiationDeal.dealDecisionId
  private val site = CatalogNames.site("Broken Peaks")
  private val p1Relic = CatalogNames.relic("Brass Horse")
  private val siteRelic = CatalogNames.relic("Sticky Fire")

  /** Every player stands at Broken Peaks with 5 favor and one facedown
    * relic: p1 holds Brass Horse with a secret on it, p2 Truthful Harp and
    * p3 Grand Mask. Broken Peaks also holds Sticky Fire, which p1 knows. */
  private def gathered: Table = Table.start
    .pawn(p1, at = site).pawn(p3, at = site)       // p2 already stands there
    .favor(p1, 5).favor(p2, 5).favor(p3, 5)
    .relic(p1, p1Relic, facedown = true).tokens(p1Relic, secrets = 1)
    .relic(p2, "Truthful Harp", facedown = true)
    .relic(p3, "Grand Mask", facedown = true)
    .relicAt(siteRelic, at = site)
    .knowsRelicAt(p1, siteRelic, at = site)

  private def start(ready: ReadyGame) =
    rules.startWalker(Ready(ready), ActionRef.Negotiation, p1)

  private def choose(state: OathState, actor: PlayerId, who: PlayerId*) =
    rules.resolveWalker(state, actor, negotiators, ChooseManyAnswer(
      who.toVector.map(DecisionOptionRef.Player(_))))

  private def say(state: OathState, by: PlayerId, answer: DecisionAnswer) =
    rules.resolveWalker(state, by, dealId, answer)

  private def ready(state: OathState): ReadyGame = state match
    case Ready(value) => value
    case other => fail(s"expected a ready game, got $other")

  private def gift(to: PlayerId, favor: Int, relics: Vector[RelicId] = Vector.empty) =
    ProposeTerms(NegotiationTerms(Vector(NegotiationTransfer(to, favor, relics))))

  /** Starts and chooses `who`, returning the state parked at the deal. */
  private def atDeal(ready: ReadyGame, who: PlayerId*): OathTransition =
    val started = start(ready).getOrElse(fail("Negotiation must start"))
    choose(started.state, p1, who*).getOrElse(
      fail("the negotiators must be accepted"))

  test("starting parks on the negotiator choice, offered to the actor"):
    val started = start(gathered.ready).getOrElse(fail("Negotiation must start"))
    parked.assertParked(started.state, ActionRef.Negotiation, negotiators, p1)

  test("a lone candidate skips the negotiator choice"):
    val started = start(gathered.pawn(p3, at = "Dunes").ready)
      .getOrElse(fail("Negotiation must start"))
    parked.assertParked(started.state, ActionRef.Negotiation, dealId, p1)

  test("with no candidate the start is rejected and offered nowhere"):
    val alone = gathered.pawn(p2, at = "Dunes").pawn(p3, at = "Dunes").ready
    assert(start(alone).left.toOption.exists(
      _.isInstanceOf[OathViolation.NegotiationUnavailable]))
    assert(!NegotiationProcedure.startable(catalog, alone, p1,
      WalkerPowers.empty))
    assert(NegotiationProcedure.startable(catalog, gathered.ready, p1,
      WalkerPowers.empty))

  test("no first-game gate: an Imperial player or an altered Foundation still negotiates"):
    val ready = gathered.ready
    val lineage = ready.game.campaign.lineages(Look(ready).player(p2).lineage)
    val campaign = ready.game.campaign
    val citizen = ready.copy(game = ready.game.copy(campaign =
      campaign.copy(lineages = campaign.lineages.updated(lineage.id,
        lineage.copy(role = Role.Citizen)))))
    assert(start(citizen).isRight)
    val altered = ready.copy(game = ready.game.copy(campaign =
      campaign.copy(foundations = campaign.foundations.map { case (k, f) =>
        k -> f.copy(face = FoundationFace.Altered) })))
    assert(start(altered).isRight)

  test("a bilateral favor and relic transfer settles atomically on the last accept"):
    val before = gathered.ready
    val deal = atDeal(before, p2)
    parked.assertParked(deal.state, ActionRef.Negotiation, dealId, p1)
    val proposed = say(deal.state, p1, gift(p2, 3, Vector(p1Relic)))
      .getOrElse(fail("terms must be accepted"))
    val theirs = say(proposed.state, p2, AcceptDeal)
      .getOrElse(fail("the counterparty may accept first"))
    val done = say(theirs.state, p1, AcceptDeal)
      .getOrElse(fail("the last accept must settle"))
    val after = ready(done.state)
    assertEquals(Look(after).favor(p1), 2)
    assertEquals(Look(after).favor(p2), 8)
    assertEquals(after.game.current.players.map(_.board.favor).sum,
      before.game.current.players.map(_.board.favor).sum)
    assert(Look(after).relics(p2).contains(p1Relic))
    assertEquals(Look(after).tokensOn(p1Relic), Tokens(0, 1))
    assertEquals(after.game.current.walkerPending, None)
    parked.assertResumed(done.state, Phase.Act, p1)

  test("three players answer in any order and a changed term resets consent"):
    val deal = atDeal(gathered.ready, p2, p3)
    val one = say(deal.state, p1, gift(p2, 1)).toOption.get
    val two = say(one.state, p2, AcceptDeal).toOption.get
    val three = say(two.state, p3, gift(p1, 2)).toOption.get
    val open = ready(three.state).game.current.walkerPending.get
    val folded = NegotiationDeal.fold(Vector(p1, p2, p3), open.answered)
    assertEquals(folded.accepted, Set.empty[PlayerId])
    val accepted = Vector(p3, p2, p1).foldLeft(three.state):
      (state, by) => say(state, by, AcceptDeal).getOrElse(fail(s"$by accepts"))
        .state
    assertEquals(Look(accepted).favor(p1), 5 - 1 + 2)
    assertEquals(ready(accepted).game.current.walkerPending, None)

  test("a decline ends the action with nothing moved"):
    val before = gathered.ready
    val deal = atDeal(before, p2)
    val proposed = say(deal.state, p1, gift(p2, 3)).toOption.get
    val declined = say(proposed.state, p2, DeclineDeal)
      .getOrElse(fail("any participant may decline"))
    val after = ready(declined.state)
    assertEquals(after.game.current.players, before.game.current.players)
    assertEquals(after.game.current.walkerPending, None)
    parked.assertResumed(declined.state, Phase.Act, p1)

  test("a player outside the chosen negotiators cannot answer"):
    val deal = atDeal(gathered.ready, p2)
    assertEquals(say(deal.state, p3, gift(p1, 1)).left.toOption,
      Some(OathViolation.WrongPlayer(p1, p3)))

  test("terms beyond the author's means, and an empty deal's accept, are rejected"):
    val deal = atDeal(gathered.ready, p2)
    assertEquals(say(deal.state, p1, gift(p2, 6)).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "decision negotiation.deal offers more than 5 favor")))
    assertEquals(say(deal.state, p2, AcceptDeal).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "decision negotiation.deal does not let this player accept now")))

  test("a settlement that cannot be met rejects the last accept and leaves the deal open"):
    val deal = atDeal(gathered.ready, p2)
    val proposed = say(deal.state, p1, gift(p2, 3)).toOption.get
    val theirs = say(proposed.state, p2, AcceptDeal).toOption.get
    // p1 loses favor while the deal is open, which no Table step can do to a
    // parked state.
    val broke = Ready(ready(theirs.state).updateCurrent(current =>
      current.copy(players = current.players.map(p => if p.player == p1 then
        p.copy(board = p.board.copy(favor = 1)) else p))))
    assertEquals(say(broke, p1, AcceptDeal).left.toOption,
      Some(OathViolation.InsufficientFavor(3, 1)))
    assert(say(theirs.state, p1, AcceptDeal).isRight)

  test("an agreed disclosure grants durable knowledge to its recipient only"):
    val adviser = CatalogNames.denizen("Errand Boy")
    val deal = atDeal(gathered.adviser(p1, adviser, facedown = true).ready, p2, p3)
    val terms = ProposeTerms(NegotiationTerms(disclosures = Vector(
      NegotiationDisclosure(p2, NegotiationDisclosureRef.Adviser(p1, adviser)),
      NegotiationDisclosure(p2, NegotiationDisclosureRef.SiteRelic(site, siteRelic)))))
    val proposed = say(deal.state, p1, terms).getOrElse(fail("terms"))
    val closed = Vector(p1, p2, p3).foldLeft(proposed.state):
      (state, by) => say(state, by, AcceptDeal).getOrElse(fail(s"$by accepts"))
        .state
    val after = ready(closed)
    assert(after.knowledge.advisers(p2).contains(adviser))
    assert(after.knowledge.siteRelics(p2)(site).contains(siteRelic))
    assert(!after.knowledge.advisers.getOrElse(p3, Vector.empty)
      .contains(adviser))

  test("closing a deal runs the action boundary, whether declined or agreed"):
    // No site on the table holds forces, so the boundary has bandits to refill.
    val deal = atDeal(gathered.ready, p2)
    val declined = say(deal.state, p2, DeclineDeal)
      .getOrElse(fail("any participant may decline"))
    assert(declined.events.exists(_.isInstanceOf[OathEvent.BanditsRefilled]),
      "a decline must run the action boundary and its bandit refill")
    val proposed = say(deal.state, p1, gift(p2, 1)).getOrElse(fail("terms"))
    val agreed = Vector(p2, p1).foldLeft(proposed.state):
      (state, by) => say(state, by, AcceptDeal).getOrElse(fail(s"$by accepts"))
        .state
    assertEquals(ready(agreed).game.current.walkerPending, None)

  test("unsupported Negotiation rules are recorded as ignored, not blocking"):
    val powered = gathered.adviser(p1, "Council Arbiter").ready
    val started = start(powered).getOrElse(
      fail("an unsupported rule must not block"))
    val recorded = started.events.head.asInstanceOf[IgnoredRulesRecorded]
    assertEquals(recorded.action, ActionKind.Negotiation)
    assertEquals(recorded.diagnostics.head.handlerId, "denizen.council-arbiter")
    assert(choose(started.state, p1, p2).isRight)

  test("a completed Negotiation replays exactly from its journal"):
    val (setupState, setupEvents) = execute()
    val Ready(original) = setupState: @unchecked
    val actor = original.game.current.turn.activePlayer
    val other = original.game.current.players.find(_.player != actor).get.player
    val destination = Look(original).pawn(other)
    val act = rules.startWalker(setupState, PhaseTransitionRef.EndWake, actor)
      .toOption.get
    val traveled = rules.startWalker(act.state, ActionRef.Travel, actor,
      Vector.empty, Vector(DecisionOptionRef.Site(destination))).toOption.get
    val adviser = Look(traveled.state).player(actor).advisers.head.id
      .asInstanceOf[WorldCardId]
    val started = rules.startWalker(traveled.state, ActionRef.Negotiation, actor)
      .getOrElse(fail("Negotiation must start"))
    val chosen =
      if parked.parkedDecision(started.state).exists(facts =>
          facts.decision == negotiators && facts.awaiting == actor) then
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
