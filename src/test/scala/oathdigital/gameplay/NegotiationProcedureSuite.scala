package oathdigital.gameplay

import oathdigital.testkit.MenuPlay
import oathdigital.gameplay.actions.negotiation.NegotiationDeal
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerPowers}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseManyAnswer, DeclineDeal, ProposeTerms}
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Journal, Look, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}
import oathdigital.testkit.StartPreview

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
    .pawn(p1, at = site).pawn(p2, at = site).pawn(p3, at = site)
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
    assert(!StartPreview.opens(catalog, alone, p1, ActionRef.Negotiation,
        WalkerPowers.empty))
    assert(StartPreview.opens(catalog, gathered.ready, p1,
        ActionRef.Negotiation, WalkerPowers.empty))

  test("no first-game gate: an Imperial player or an altered Foundation still negotiates"):
    // No Table step reaches lineage roles or foundations.
    val ready = gathered.ready
    val lineage = ready.game.campaign.lineages(Look(ready).player(p2).lineage)
    val campaign = ready.game.campaign
    val citizen = ready.copy(game = ready.game.copy(campaign =
      campaign.copy(lineages = campaign.lineages.updated(lineage.id,
        lineage.copy(role = Role.Citizen)))))
    parked.assertParked(accepted(start(citizen)).state, ActionRef.Negotiation,
      negotiators, p1)
    val altered = ready.copy(game = ready.game.copy(campaign =
      campaign.copy(foundations = campaign.foundations.map { case (k, f) =>
        k -> f.copy(face = FoundationFace.Altered) })))
    parked.assertParked(accepted(start(altered)).state, ActionRef.Negotiation,
      negotiators, p1)

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
    val after = Outcomes.ready(done.state)
    assertEquals(Look(after).favor(p1), 2)
    assertEquals(Look(after).favor(p2), 8)
    assertEquals(after.game.current.players.map(_.board.favor).sum,
      before.game.current.players.map(_.board.favor).sum)
    assert(Look(after).relics(p2).contains(p1Relic))
    assertEquals(Look(after).tokensOn(p1Relic), Tokens(0, 1))
    assertEquals(MenuPlay.pendingBeyondMenu(after), None)
    parked.assertResumed(done.state, Phase.Act, p1)

  test("three players answer in any order and a changed term resets consent"):
    val deal = atDeal(gathered.ready, p2, p3)
    val one = accepted(say(deal.state, p1, gift(p2, 1)))
    val two = accepted(say(one.state, p2, AcceptDeal))
    val three = accepted(say(two.state, p3, gift(p1, 2)))
    val open = present(Outcomes.ready(three.state).game.current.walkerPending,
      "the open deal")
    val folded = NegotiationDeal.fold(Vector(p1, p2, p3), open.answered)
    assertEquals(folded.accepted, Set.empty[PlayerId])
    val closed = Vector(p3, p2, p1).foldLeft(three.state):
      (state, by) => say(state, by, AcceptDeal).getOrElse(fail(s"$by accepts"))
        .state
    // p1 started with 5, gave 1 to p2 and received 2 from p3.
    assertEquals(Look(closed).favor(p1), 5 - 1 + 2)
    assertEquals(MenuPlay.pendingBeyondMenu(Outcomes.ready(closed)), None)

  test("a decline ends the action with nothing moved"):
    val before = gathered.ready
    val deal = atDeal(before, p2)
    val proposed = accepted(say(deal.state, p1, gift(p2, 3)))
    val declined = say(proposed.state, p2, DeclineDeal)
      .getOrElse(fail("any participant may decline"))
    val after = Outcomes.ready(declined.state)
    assertEquals(after.game.current.players, before.game.current.players)
    assertEquals(MenuPlay.pendingBeyondMenu(after), None)
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
    val proposed = accepted(say(deal.state, p1, gift(p2, 3)))
    val theirs = accepted(say(proposed.state, p2, AcceptDeal))
    // p1 drops to 1 favor while the deal is open.
    val broke = Ready(Table.from(Outcomes.ready(theirs.state)).favor(p1, 1)
      .ready)
    assertEquals(say(broke, p1, AcceptDeal).left.toOption,
      Some(OathViolation.InsufficientFavor(3, 1)))
    // The deal stayed open: p1, with the favor back, can still accept.
    assertEquals(MenuPlay.pendingBeyondMenu(Outcomes.ready(accepted(
      say(theirs.state, p1, AcceptDeal)).state)), None)

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
    val after = Outcomes.ready(closed)
    assert(after.knowledge.advisers(p2).contains(adviser))
    assert(after.knowledge.siteRelics(p2)(site).contains(siteRelic))
    assert(!after.knowledge.advisers.getOrElse(p3, Vector.empty)
      .contains(adviser))

  test("closing a deal runs the action boundary, whether declined or agreed"):
    // No site on the table holds forces, so the boundary has bandits to refill.
    val deal = atDeal(gathered.ready, p2)
    val declined = say(deal.state, p2, DeclineDeal)
      .getOrElse(fail("any participant may decline"))
    assertEquals(declined.events.collect {
      case refill: OathEvent.BanditsRefilled => refill }.size, 1,
      "a decline must run the action boundary and its bandit refill")
    val proposed = say(deal.state, p1, gift(p2, 1)).getOrElse(fail("terms"))
    val theirAccept = say(proposed.state, p2, AcceptDeal)
      .getOrElse(fail("p2 accepts"))
    val agreed = say(theirAccept.state, p1, AcceptDeal)
      .getOrElse(fail("p1 accepts"))
    assertEquals(agreed.events.collect {
      case refill: OathEvent.BanditsRefilled => refill }.size, 1,
      "an agreed deal must run the action boundary and its bandit refill")
    assertEquals(MenuPlay.pendingBeyondMenu(Outcomes.ready(agreed.state)), None)

  test("an unsupported Negotiation rule does not block the Negotiation"):
    val powered = gathered.adviser(p1, "Council Arbiter").ready
    val started = start(powered).getOrElse(
      fail("an unsupported rule must not block"))
    parked.assertParked(accepted(choose(started.state, p1, p2)).state,
      ActionRef.Negotiation, dealId, p1)

  test("a completed Negotiation replays exactly from its journal"):
    val SetupResult(setupState, setupEvents) = setupThroughWake()
    // p2, the first player, travels to p1's site and negotiates with p1.
    val actor = firstPlayer
    val other = p1
    assertEquals(Look(setupState).active, actor)
    val destination = Look(setupState).pawn(other)
    val act = accepted(
      rules.startWalker(setupState, PhaseTransitionRef.EndWake, actor))
    val traveled = accepted(
      TravelPlay.travelled(rules, act.state, actor, destination))
    val adviser = present(Look(traveled.state).player(actor).advisers
      .headOption.map(_.id).collect { case id: WorldCardId => id },
      "a kept adviser")
    val started = MenuPlay.started(rules, traveled.state, actor, ActionRef.Negotiation)
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
    val replayed = Journal.replayed(rules, events)
    assertEquals(replayed, done.state)
