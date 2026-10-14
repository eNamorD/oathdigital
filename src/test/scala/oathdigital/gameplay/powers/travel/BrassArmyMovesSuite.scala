package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.CampaignFixture.{on, raidBoard, rules}
import oathdigital.gameplay.TravelPlay
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver.{awaits, commit,
  winningDice}
import oathdigital.gameplay.powers.targeting.TargetingFixture
import oathdigital.gameplay.walker.WalkerStepRecorded
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class BrassArmyMovesSuite extends munit.FunSuite:
  private val brass = CatalogNames.relic("Brass Army")
  private val game = ActPowerHarness.rulesWithPrintedRestrictions()
  private val destination: SiteId = Table.quietPawnSite(p2)

  /** The lines Brass Army's restriction journaled in `events`. */
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(BrassArmyMoves.id, BrassArmyMoves.noteKeys, events)

  /** p1, in Act, holding Brass Army (facedown when `facedown`) with
    * `faceUp` faceup and 2 facedown secrets. */
  private def holdingBrassArmy(faceUp: Int, facedown: Boolean = false)
      : ReadyGame = Table.start.turn(p1, Phase.Act)
    .relic(p1, brass, facedown = facedown)
    .secrets(p1, faceUp = faceUp, faceDown = 2).ready

  /** p1's Travel to p2's home site, with every command's events. */
  private def travelled(board: ReadyGame): OathTransition =
    accepted(TravelPlay.travelled(game, Ready(board), p1, destination))

  /** p1's answer to Brass Army's question after `travel`, with every
    * command's events. */
  private def answered(travel: OathTransition, answer: DecisionOptionRef)
      : OathTransition =
    val done = accepted(game.resolveWalker(travel.state, p1,
      BrassArmyMoves.decisionId, DecisionAnswer.ChooseOneAnswer(answer)))
    done.copy(events = travel.events ++ done.events)

  /** Every operation the steps in `events` recorded. */
  private def recordedOps(events: Vector[OathEvent]): Vector[CoreOperation] =
    events.collect { case step: WalkerStepRecorded => step.ops }.flatten

  test("a Travel asks the holder at once, and flipping turns one secret " +
      "facedown, with its line"):
    val board = holdingBrassArmy(faceUp = 2)
    val travel = travelled(board)
    assertEquals(TargetingFixture.parkedOn(travel),
      Some(BrassArmyMoves.decisionId))
    assertEquals(TargetingFixture.optionsAt(travel, ActionRef.Travel),
      Vector[DecisionOptionRef](BrassArmyMoves.bury, BrassArmyMoves.flip))
    assertEquals(Look(TargetingFixture.ready(travel)).pawn(p1), destination)
    val done = answered(travel, BrassArmyMoves.flip)
    val end = Outcomes.ready(done.state)
    assertEquals(Look(end).faceUpSecrets(p1), 1)
    assertEquals(Look(end).faceDownSecrets(p1), 3)
    assert(Look(end).relics(p1).contains(brass))
    assertEquals(said(done.events), Vector(NoteText.Said("flipped",
      "Flipped a secret facedown.", covers = false)))
    assertEquals(ActPowerHarness.replayed(game, board, done.events), end)
    assertWireRoundTrips(done.events)

  test("burying puts the relic back, with a line that covers the bury"):
    val done = answered(travelled(holdingBrassArmy(faceUp = 2)),
      BrassArmyMoves.bury)
    val end = Outcomes.ready(done.state)
    assert(!Look(end).relics(p1).contains(brass))
    assertEquals(Look(end).faceUpSecrets(p1), 2)
    assert(recordedOps(done.events).contains(Bury(BuryableCard.Relic(brass),
      PositionedLocation(Location.PlayArea(p1)))))
    assertEquals(said(done.events), Vector(NoteText.Said("buried",
      "Buried after a pawn move.", covers = true)))

  test("with no faceup secret the relic is buried and nothing is asked"):
    val travel = travelled(holdingBrassArmy(faceUp = 0))
    assertNotEquals(TargetingFixture.parkedOn(travel),
      Some(BrassArmyMoves.decisionId))
    val end = Outcomes.ready(travel.state)
    assert(!Look(end).relics(p1).contains(brass))
    assertEquals(Look(end).faceDownSecrets(p1), 2)
    assertEquals(said(travel.events), Vector(NoteText.Said("buried",
      "Buried after a pawn move.", covers = true)))

  test("a facedown Brass Army asks nothing"):
    val travel = travelled(holdingBrassArmy(faceUp = 2, facedown = true))
    assertNotEquals(TargetingFixture.parkedOn(travel),
      Some(BrassArmyMoves.decisionId))
    assert(Look(Outcomes.ready(travel.state)).relics(p1).contains(brass))
    assertEquals(said(travel.events), Vector.empty)

  test("a defender whose pawn a Raid relocates is asked off turn, and the " +
      "line names them"):
    val raid = raidBoard()
    val b = on(raid.board)(_.relic(raid.board.rival, brass)
      .secrets(raid.board.rival, faceUp = 1, faceDown = 0))
    val run = commit(rules(winningDice), b, 4, targets = Vector(
      DecisionOptionRef.Relic(raid.relic)), raid = true).finishPlanWindows
    assertEquals(run.winner, Some(true))
    val relocated = run.pick(b.actor, CampaignIds.relocation,
      run.offered(b.actor).head).finishPlanWindows
    assert(awaits(relocated, b.rival, BrassArmyMoves.decisionId))
    val done = relocated.pick(b.rival, BrassArmyMoves.decisionId,
      BrassArmyMoves.flip).finishPlanWindows
    assertEquals(Look(done.state).faceUpSecrets(b.rival), 0)
    assertEquals(Look(done.state).faceDownSecrets(b.rival), 1)
    assertEquals(said(done.events), Vector(NoteText.Said("flipped-by",
      s"${b.rival.value} flipped a secret facedown.", covers = false)))
