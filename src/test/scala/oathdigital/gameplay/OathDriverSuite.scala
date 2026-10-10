package oathdigital.gameplay

import oathdigital.gameplay.actions.MinorActionCommand
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ActionBoundaryRan, WalkerCompleted}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{CatalogNames, Outcomes, TakeWealthFixture, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** What runs between walks (walker-driven game loop design, "The
  * driver"). */
class OathDriverSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = WalkerDiceFixture.blanks)
  private val ancientCity = CatalogNames.site("Ancient City")
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  private val knights =
    PowerSourceRef.Card(CatalogNames.denizen("Knights Errant"))

  private def offer(action: StartableRef): QueuedProcedure =
    QueuedProcedure(p1, action, Vector.empty, Some(knights), QueueKind.Offer)

  private def current(state: OathState): CurrentGameState =
    Outcomes.ready(state).game.current

  private def boundaries(events: Vector[OathEvent]): Int =
    events.count(_ == ActionBoundaryRan)

  /** p1 at Ancient City, which two bandits rule; p2 at Broken Peaks and p3
    * at Buried Giant. It is p1's Act. */
  private def table: Table = Table.start
    .pawn(p1, at = ancientCity).pawn(p2, at = brokenPeaks)
    .pawn(p3, at = "Buried Giant").bandits(ancientCity, 2)

  private def replayed(from: OathState, events: Vector[OathEvent])
      : OathState =
    accepted(events.foldLeft[Either[OathViolation, OathState]](Right(from))(
      (state, event) => state.flatMap(rules.evolve(_, event))))

  test("an action's walk ends with the boundary, once"):
    val start = table.state
    val traveled = accepted(TravelPlay.travelled(rules, start, p1,
      brokenPeaks))
    val completed = traveled.events.indexOf(WalkerCompleted(ActionRef.Travel))
    assertEquals(traveled.events.lift(completed + 1),
      Some(ActionBoundaryRan: OathEvent))
    assertEquals(boundaries(traveled.events), 1)
    assertEquals(current(traveled.state).boundaryDue, false)
    assertEquals(replayed(start, traveled.events), traveled.state)

  test("Begin Rest, Finish Rest and End Wake run no boundary"):
    // p1 has no REST power, so Rest finishes in the same command.
    val rested = accepted(rules.startWalker(table.state,
      PhaseTransitionRef.BeginRest, p1))
    assert(rested.events.contains(
      WalkerCompleted(PhaseTransitionRef.FinishRest)), rested.events)
    assertEquals(boundaries(rested.events), 0)

  test("a queued chain gets one boundary, after its last action"):
    val start = table.update(_.updateCurrent(_.copy(boundaryDue = true,
      queue = Vector(offer(ActionRef.Campaign))))).state
    val parked = accepted(FollowUpPlay.dequeued(rules, start))
    assertEquals(boundaries(parked.events), 0)
    val skipped = accepted(FollowUpPlay.skipped(rules, parked.state, p1))
    assertEquals(boundaries(skipped.events), 1)

  test("a Skip with no action before it runs no boundary"):
    val start = table.offering(offer(ActionRef.Campaign)).state
    val skipped = accepted(FollowUpPlay.skipped(rules, start, p1))
    assertEquals(boundaries(skipped.events), 0)

  test("a result stops the driver"):
    val over = table.update(_.updateCurrent(_.copy(
      result = Some(GameResult(p2)),
      queue = Vector(offer(ActionRef.Campaign))))).state
    assertEquals(accepted(FollowUpPlay.dequeued(rules, over)).events,
      Vector.empty)

  test("a minor action runs the boundary"):
    val start = table.relicAt("Brass Horse", at = ancientCity).state
    val peeked = accepted(rules.handle(start,
      MinorActionCommand.PeekSiteRelics(p1)))
    assertEquals(boundaries(peeked.events), 1)

  test("the title walk hands back to the driver: Wake ends once the holder " +
      "answers"):
    // p2 holds the title while p1 and p3 each rule one site, a tie p2
    // breaks. p1 takes Ancient City's only favor, which leaves no Wake
    // option.
    val ready = Table.start
      .pawn(p1, at = "Ancient City").pawn(p2, at = "Broken Peaks")
      .pawn(p3, at = "Buried Giant").oathkeeper(Some(p2))
      .warbandsAt("Dunes", p1, 1).warbandsAt("Fair Isle", p3, 1)
      .turn(p1, Phase.Wake).siteTokens("Ancient City", favor = 1, secrets = 0)
      .ready
    val parked = accepted(TakeWealthFixture.take(rules, ready))
    assertEquals(current(parked.state).walkerProcedure,
      Some(TriggeredProcedureRef.Oathkeeper))
    val chosen = accepted(rules.resolveWalker(parked.state, p2,
      OathkeeperProcedure.recipientDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Player(p1))))
    assert(chosen.events.contains(
      WalkerCompleted(PhaseTransitionRef.EndWake)), chosen.events)
    assertEquals(current(chosen.state).turn.phase, Phase.Act)

  /** p1's Wake, with p1 the Usurper: the Wake check ends the game. */
  private def usurperWakes: OathState = Table.start.turn(p1, Phase.Wake)
    .oathkeeper(Some(p1), TitleSide.Usurper).state

  private def driven(ended: Ended): Vector[OathEvent] =
    accepted(rules.next(OathTransition(usurperWakes, Vector.empty), ended))
      .events

  test("the turn-start hooks run after Setup and after Finish Rest"):
    assertEquals(driven(Ended.Walk(TriggeredProcedureRef.Setup)),
      Vector[OathEvent](OathEvent.UsurperVictory(p1)))
    assertEquals(driven(Ended.Walk(PhaseTransitionRef.FinishRest)),
      Vector[OathEvent](OathEvent.UsurperVictory(p1)))

  test("no other walk starts a turn"):
    // The Wake check belongs to a turn's start, not to every finished walk.
    Vector(TriggeredProcedureRef.Oathkeeper, PhaseTransitionRef.EndWake,
      ActionRef.Travel).foreach: walk =>
      assert(!driven(Ended.Walk(walk)).contains(OathEvent.UsurperVictory(p1)),
        walk)

  test("Setup's end replays: the first Wake begins in the same command"):
    val setup = FirstGameSetupFixture.initialSituation()
    assertEquals(replayed(OathState.NoGame, setup.events), setup.state)
