package oathdigital.gameplay

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{FollowUpOffer, FollowUpSkipped,
  ProcedureDequeued, WalkerCompleted}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{CatalogNames, Outcomes, ParkedNode, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** The follow-up offer (walker-driven game loop design, ruling 6): the
  * driver dequeues an entry and asks its actor, who takes the action for no
  * Supply or skips it. Entries are placed on the table directly: which power
  * queues them is each card's own suite. */
class FollowUpRulesSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = WalkerDiceFixture.blanks)
  private val ancientCity = CatalogNames.site("Ancient City")
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  private val knights =
    PowerSourceRef.Card(CatalogNames.denizen("Knights Errant"))

  private def offer(action: StartableRef): QueuedProcedure =
    QueuedProcedure(p1, action, Vector.empty, Some(knights), QueueKind.Offer)

  private def run(action: StartableRef): QueuedProcedure =
    offer(action).copy(kind = QueueKind.Run)

  /** p1 at Ancient City, which two bandits rule, so a Conquest is legal; p2 at
    * Broken Peaks and p3 at Buried Giant. `queue` is owed to p1. */
  private def table(queue: QueuedProcedure*): Table = Table.start
    .pawn(p1, at = ancientCity).pawn(p2, at = brokenPeaks)
    .pawn(p3, at = "Buried Giant")
    .bandits(ancientCity, 2)
    .update(_.updateCurrent(_.copy(queue = queue.toVector)))

  private def current(state: OathState): CurrentGameState =
    Outcomes.ready(state).game.current

  /** Folds `events` from `from` through the replay path. */
  private def replayed(from: OathState, events: Vector[OathEvent])
      : OathState =
    accepted(events.foldLeft[Either[OathViolation, OathState]](Right(from))(
      (state, event) => state.flatMap(rules.evolve(_, event))))

  /** The parked offer's options, as the walker shows them. */
  private def options(state: OathState): Vector[DecisionOption] =
    ParkedNode.of(state, catalog, WalkerPowerCatalog.default(catalog))
      .toOption.flatten.collect {
        case ParkedNode.Decision(_, decide, _) => decide.query
      }.collect { case one: DecisionQuery.ChooseOne => one.options }
      .getOrElse(Vector.empty)

  test("the driver dequeues an offer at once, and the offer parks"):
    val start = table(offer(ActionRef.Campaign)).state
    val parked = accepted(FollowUpPlay.dequeued(rules, start))
    assertEquals(parked.events.head,
      ProcedureDequeued(offer(ActionRef.Campaign)): OathEvent)
    assertEquals(FollowUpPlay.offered(parked.state),
      Some(offer(ActionRef.Campaign)))
    assertEquals(current(parked.state).queue, Vector.empty)
    assertEquals(parked.state,
      table().offering(offer(ActionRef.Campaign)).state)
    assertEquals(replayed(start, parked.events), parked.state)

  test("the offer shows the action for no Supply, then Skip"):
    val parked = table().offering(offer(ActionRef.Campaign)).state
    assertEquals(options(parked).map(_.ref),
      Vector[DecisionOptionRef](FollowUpOffer.take, FollowUpOffer.skip))
    assert(options(parked).forall(_.available), options(parked))

  test("taking it starts the action in the same command"):
    val start = table().offering(offer(ActionRef.Campaign)).state
    val started = accepted(FollowUpPlay.taken(rules, start, p1))
    assert(started.events.contains(
      WalkerCompleted(TriggeredProcedureRef.FollowUpOffer)), started.events)
    assert(started.events.contains(
      ProcedureDequeued(run(ActionRef.Campaign))), started.events)
    assertEquals(current(started.state).walkerProcedure,
      Some(ActionRef.Campaign))
    assertEquals(current(started.state).walkerEntry,
      Some(run(ActionRef.Campaign)))
    assertEquals(replayed(start, started.events), started.state)

  test("an action that cannot start is shown with its reason, and Skip " +
      "still works"):
    // No bandits and no enemy pawn at Ancient City: nothing to campaign
    // against.
    val start = table().bandits(ancientCity, 0)
      .offering(offer(ActionRef.Campaign)).state
    assertEquals(options(start).headOption, Some(DecisionOption.Unavailable(
      DecisionOption.Button(FollowUpOffer.take, "Campaign for no Supply"),
      OptionReason.Written("No site to conquer and no enemy pawn to raid"))))
    assert(FollowUpPlay.taken(rules, start, p1).isLeft)
    val skipped = accepted(FollowUpPlay.skipped(rules, start, p1))
    assert(skipped.events.contains(
      FollowUpSkipped(offer(ActionRef.Campaign))), skipped.events)
    assertEquals(current(skipped.state).walkerProcedure, None)
    assertEquals(replayed(start, skipped.events), skipped.state)

  test("skipping one entry offers the next"):
    val start = table(offer(ActionRef.Travel), offer(ActionRef.Campaign))
      .state
    val travel = accepted(FollowUpPlay.dequeued(rules, start))
    val next = accepted(FollowUpPlay.skipped(rules, travel.state, p1))
    assertEquals(FollowUpPlay.offered(next.state),
      Some(offer(ActionRef.Campaign)))

  test("a taken action goes before the entries already waiting"):
    val start = table(offer(ActionRef.Travel), offer(ActionRef.Campaign))
      .state
    val travel = accepted(FollowUpPlay.dequeued(rules, start))
    val started = accepted(FollowUpPlay.taken(rules, travel.state, p1))
    assertEquals(current(started.state).walkerProcedure,
      Some(ActionRef.Travel))
    assertEquals(current(started.state).queue,
      Vector(offer(ActionRef.Campaign)))

  test("a taken action's completion offers the next entry in the same " +
      "command, and the chain replays"):
    val start = table(offer(ActionRef.Travel), offer(ActionRef.Campaign))
      .state
    val offering = accepted(FollowUpPlay.dequeued(rules, start))
    val taken = accepted(FollowUpPlay.taken(rules, offering.state, p1))
    val done = accepted(rules.resolveWalker(taken.state, p1,
      TravelProcedure.destinationDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Site(brokenPeaks))))
    assert(done.events.contains(WalkerCompleted(ActionRef.Travel)),
      done.events)
    assertEquals(FollowUpPlay.offered(done.state),
      Some(offer(ActionRef.Campaign)))
    assertEquals(replayed(start,
      offering.events ++ taken.events ++ done.events), done.state)

  test("a queued action starts outside the Act phase"):
    val resting = table().turn(p1, Phase.Rest)
      .offering(offer(ActionRef.Campaign)).state
    assertEquals(current(accepted(FollowUpPlay.taken(rules, resting, p1))
      .state).walkerProcedure, Some(ActionRef.Campaign))

  test("without an entry, a Campaign still needs the Act phase"):
    assertEquals(rules.startWalker(table().turn(p1, Phase.Rest).state,
      ActionRef.Campaign, p1),
      Left(OathViolation.WrongPhase(Phase.Act, Phase.Rest)))

  test("only the entry's actor answers the offer"):
    val start = table().offering(offer(ActionRef.Campaign)).state
    assertEquals(FollowUpPlay.skipped(rules, start, p2),
      Left(OathViolation.WrongPlayer(p1, p2)))
