package oathdigital.gameplay

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{FollowUpSkipped, FollowUpStarted,
  WalkerCompleted}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{CatalogNames, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** Starting and skipping a queued action (follow-up actions design, "Start
  * and skip"). Entries are placed on the table directly: which power queues
  * them is each card's own suite. */
class FollowUpRulesSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = WalkerDiceFixture.blanks)
  private val ancientCity = CatalogNames.site("Ancient City")
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  private val knights =
    PowerSourceRef.Card(CatalogNames.denizen("Knights Errant"))

  private def owed(action: ActionRef): FollowUp = FollowUp(p1, action, knights)

  /** p1 at Ancient City, which two bandits rule, so a Conquest is legal; p2 at
    * Broken Peaks and p3 at Buried Giant. `queue` is owed to p1. */
  private def table(queue: FollowUp*): Table = Table.start
    .pawn(p1, at = ancientCity).pawn(p2, at = brokenPeaks)
    .pawn(p3, at = "Buried Giant")
    .bandits(ancientCity, 2)
    .update(_.updateCurrent(_.copy(followUps = queue.toVector)))

  private def current(state: OathState): CurrentGameState =
    Outcomes.ready(state).game.current

  /** Folds `events` from `from` through the replay path. */
  private def replayed(from: OathState, events: Vector[OathEvent])
      : OathState =
    accepted(events.foldLeft[Either[OathViolation, OathState]](Right(from))(
      (state, event) => state.flatMap(rules.evolve(_, event))))

  test("the queued start takes the head and opens with FollowUpStarted"):
    val start = table(owed(ActionRef.Campaign)).state
    val started = accepted(rules.startWalker(start, ActionRef.Campaign, p1))
    assertEquals(started.events.head,
      FollowUpStarted(owed(ActionRef.Campaign)): OathEvent)
    assertEquals(current(started.state).followUps, Vector.empty)
    assertEquals(current(started.state).walkerFollowUp,
      Some(owed(ActionRef.Campaign)))
    assertEquals(replayed(start, started.events), started.state)

  test("another action than the head's is refused"):
    assertEquals(rules.startWalker(table(owed(ActionRef.Campaign)).state,
      ActionRef.Travel, p1),
      Left(OathViolation.FollowUpPending(owed(ActionRef.Campaign))))

  test("another player than the head's is refused"):
    assertEquals(rules.startWalker(table(owed(ActionRef.Campaign)).state,
      ActionRef.Campaign, p2), Left(OathViolation.WrongPlayer(p1, p2)))

  test("a start that fails leaves the whole command refused"):
    // No bandits and no enemy pawn at Ancient City: nothing to campaign
    // against. A rules call is pure, so a refusal records nothing.
    val board = table(owed(ActionRef.Campaign)).bandits(ancientCity, 0).state
    assert(rules.startWalker(board, ActionRef.Campaign, p1).isLeft)

  test("Skip takes the head, and the next entry is owed"):
    val start = table(owed(ActionRef.Travel), owed(ActionRef.Campaign)).state
    val skipped = accepted(rules.skipFollowUp(start, p1))
    assertEquals(skipped.events,
      Vector[OathEvent](FollowUpSkipped(owed(ActionRef.Travel))))
    assertEquals(current(skipped.state).followUps,
      Vector(owed(ActionRef.Campaign)))
    assertEquals(replayed(start, skipped.events), skipped.state)

  test("Skip is refused with nothing queued, and from another player"):
    assertEquals(rules.skipFollowUp(table().state, p1),
      Left(OathViolation.InvalidEventOrder("no follow-up action is queued")))
    assertEquals(rules.skipFollowUp(
      table(owed(ActionRef.Campaign)).state, p2),
      Left(OathViolation.WrongPlayer(p1, p2)))

  test("a queued run's completion leaves the next entry owed"):
    val start = table(owed(ActionRef.Travel), owed(ActionRef.Campaign)).state
    val started = accepted(rules.startWalker(start, ActionRef.Travel, p1))
    val done = accepted(rules.resolveWalker(started.state, p1,
      TravelProcedure.destinationDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Site(brokenPeaks))))
    assert(done.events.contains(WalkerCompleted(ActionRef.Travel)),
      done.events)
    assertEquals(current(done.state).walkerFollowUp, None)
    assertEquals(current(done.state).followUps,
      Vector(owed(ActionRef.Campaign)))
    assertEquals(replayed(start, started.events ++ done.events), done.state)

  test("a queued action starts outside the Act phase"):
    val resting = table(owed(ActionRef.Campaign)).turn(p1, Phase.Rest).state
    assert(rules.startWalker(resting, ActionRef.Campaign, p1).isRight)

  test("without a queue, a Campaign still needs the Act phase"):
    assertEquals(rules.startWalker(table().turn(p1, Phase.Rest).state,
      ActionRef.Campaign, p1),
      Left(OathViolation.WrongPhase(Phase.Act, Phase.Rest)))
