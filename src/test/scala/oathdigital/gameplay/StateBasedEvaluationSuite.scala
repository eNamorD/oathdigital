package oathdigital.gameplay

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.oathkeeper.{OathkeeperOutcome, OathkeeperRules}
import oathdigital.model._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathEvent._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, as}

class StateBasedEvaluationSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)

  // War Exhaustion's random choice, fixed for each test.
  private val pickFirstCandidate: Vector[PlayerId] => PlayerId = _.head
  private val pickSecondCandidate: Vector[PlayerId] => PlayerId = _(1)
  private val pickLastCandidate: Vector[PlayerId] => PlayerId = _.last

  /** p1's Wake in `round`, with the Usurper limit as `limited` says. Each
    * of `rulers` rules one site with a warband: the first Dunes, the second
    * Fair Isle. `holder` holds the title on `side`. */
  private def preparedTable(
      rulers: Vector[PlayerId],
      holder: Option[PlayerId] = None,
      side: TitleSide = TitleSide.Oathkeeper,
      round: Int = 1,
      limited: Boolean = true
  ): Table =
    rulers.zip(Vector("Dunes", "Fair Isle")).foldLeft(
      Table.start.turn(p1, Phase.Wake).oathkeeper(holder, side)) {
        case (table, (ruler, site)) => table.warbandsAt(site, ruler, 1) }
      // The round track is the subject here, and no other suite sets it.
      .update(_.updateCurrent(current => current.copy(tracks =
        current.tracks.copy(round = round, usurperLimited = limited))))

  private def prepared(rulers: Vector[PlayerId],
      holder: Option[PlayerId] = None, side: TitleSide = TitleSide.Oathkeeper,
      round: Int = 1, limited: Boolean = true): ReadyGame =
    preparedTable(rulers, holder, side, round, limited).ready

  private def atRoundEnd(table: Table): ReadyGame =
    table.turn(p1, Phase.RoundEnd).ready

  private def withGoal(ready: ReadyGame, goal: OathkeeperGoal): ReadyGame =
    // No Table step sets the campaign's Oathkeeper goal.
    ready.updateCampaign(_.copy(oathkeeperGoal = goal))

  test("all four printed goals qualify from their authoritative holdings"):
    val cases = Vector(
      // p1 rules Dunes.
      OathkeeperGoal.Supremacy -> (preparedTable(Vector(p1)) -> p1),
      // p2 holds a relic.
      OathkeeperGoal.Protection -> (preparedTable(Vector.empty)
        .relic(p2, "Brass Horse", facedown = true) -> p2),
      // p3 holds the People's Favor.
      OathkeeperGoal.ThePeople -> (preparedTable(Vector.empty)
        .peoplesFavor(Some(p3), favor = 1) -> p3),
      // p1 holds the Darkest Secret.
      OathkeeperGoal.Devotion -> (preparedTable(Vector.empty)
        .darkestSecret(Some(p1), secrets = 1) -> p1)
    )

    cases.foreach { case (goal, (table, expected)) =>
      val scoped = withGoal(table.ready, goal)
      assertEquals(OathkeeperRules.outcome(scoped),
        OathkeeperOutcome.Transfer(Some(expected)), clue(goal))
      assertEquals(new GameProjector(catalog).projectPublic("goal",
        LoadedGame(Ready(scoped), 0)).oathkeeper.map(_.goal),
        Some(goal.key), clue(goal))
    }

  test("Protection requires at least one relic and preserves highest-count ties"):
    val protection = withGoal(prepared(Vector.empty), OathkeeperGoal.Protection)
    assertEquals(OathkeeperRules.outcome(protection), OathkeeperOutcome.NoChange)

    // p1 holds the title, and p1 and p2 hold one relic each.
    val tied = withGoal(preparedTable(Vector.empty, holder = Some(p1))
      .relic(p1, "Brass Horse", facedown = true)
      .relic(p2, "Cracked Horn", facedown = true).ready,
      OathkeeperGoal.Protection)
    assertEquals(OathkeeperRules.outcome(tied), OathkeeperOutcome.NoChange)

  test("round four releases limiter and retained Usurper wins next Wake"):
    val holder = p1                                  // the first player
    val initial = Ready(prepared(Vector(holder), Some(holder),
      round = 3, limited = true))
    // The quiet table's turn order starts with p1.
    val turnOrder = Vector(p1, p2, p3)
    var state: OathState = initial
    var events = Vector.empty[OathEvent]

    def accept(transition: Either[OathViolation, OathTransition]): Unit =
      val done = accepted(transition)
      state = done.state
      events ++= done.events
    // A Wake with nothing to decide has already ended by itself.
    def endWake(player: PlayerId): Unit =
      if Look(state).phase == Phase.Wake
      then accept(rules.startWalker(state, PhaseTransitionRef.EndWake, player))
    def finishTurn(player: PlayerId): Unit =
      endWake(player)
      accept(rules.startWalker(state, PhaseTransitionRef.BeginRest, player))

    turnOrder.foreach(finishTurn)
    val roundFour = Outcomes.ready(state)
    assertEquals(roundFour.game.current.tracks.round, 4)
    assertEquals(roundFour.game.current.tracks.usurperLimited, false)
    assertEquals(roundFour.game.current.title,
      OathkeeperState(Some(holder), TitleSide.Usurper))
    assert(events.contains(UsurperFlipped(holder)))

    endWake(holder)
    // p1 travels from Ancient City to Broken Peaks.
    val destination = CatalogNames.site("Broken Peaks")
    accept(rules.startWalker(state, ActionRef.Travel, holder, Vector.empty,
      Vector(DecisionOptionRef.Site(destination))))
    assertEquals(Outcomes.ready(state).game.current.title,
      OathkeeperState(Some(holder), TitleSide.Usurper))
    accept(rules.startWalker(state, PhaseTransitionRef.BeginRest, holder))
    turnOrder.tail.foreach(finishTurn)

    val won = Outcomes.ready(state)
    assertEquals(won.game.current.result, Some(GameResult(holder)))
    assertEquals(events.last, UsurperVictory(holder))
    val replayed = events.foldLeft[Either[OathViolation, OathState]](
      Right(initial))((next, event) => next.flatMap(rules.evolve(_, event)))
    assertEquals(replayed, Right(state))

  test("all four true Visions win only with three Visions drawn"):
    // p1, the active player, qualifies for each Vision in turn.
    val cases = Vector(
      VisionRules.Conquest -> preparedTable(Vector(p1)),
      VisionRules.Sanctuary -> preparedTable(Vector.empty)
        .relic(p1, "Dowsing Sticks", facedown = true),
      VisionRules.Rebellion -> preparedTable(Vector.empty)
        .peoplesFavor(Some(p1), favor = 1),
      VisionRules.Faith -> preparedTable(Vector.empty)
        .darkestSecret(Some(p1), secrets = 1)
    )
    cases.foreach { case (vision, table) =>
      val revealed = table.revealedVision(p1, vision)
      val state = revealed.visionsDrawn(3).ready
      assertEquals(StateBasedEvaluation.visionAtWake(Ready(state)),
        Right(Some(VisionVictory(p1, vision))), clue(vision))
      val below = revealed.visionsDrawn(2).ready
      assertEquals(StateBasedEvaluation.visionAtWake(Ready(below)), Right(None),
        clue(vision))
    }

  test("a newly flipped Oathkeeper can win by Vision but never immediately as Usurper"):
    val qualifying = preparedTable(Vector(p1), holder = Some(p1),
      limited = false)
    val withVision = qualifying.revealedVision(p1, VisionRules.Conquest)
      .visionsDrawn(3).ready
    assertEquals(StateBasedEvaluation.atWake(Ready(withVision)),
      Right(Some(UsurperFlipped(p1))))
    val flipped = accepted(rules.evolve(Ready(withVision), UsurperFlipped(p1)))
    assertEquals(StateBasedEvaluation.visionAtWake(flipped),
      Right(Some(VisionVictory(p1, VisionRules.Conquest))))
    assertEquals(StateBasedEvaluation.atWake(flipped),
      Right(Some(UsurperVictory(p1))))

    val noVision = qualifying.visionsDrawn(3).ready
    val afterFlip = accepted(rules.evolve(Ready(noVision), UsurperFlipped(p1)))
    assertEquals(StateBasedEvaluation.visionAtWake(afterFlip), Right(None))

  test("rounds one through seven advance without War Exhaustion"):
    (1 to 7).foreach { round =>
      val state = preparedTable(Vector.empty, round = round)
      val events = accepted(StateBasedEvaluation.endRound(
        Ready(atRoundEnd(state)), pickFirstCandidate))
      assertEquals(events, Vector(RoundEnded(round, Some(round + 1))), clue(round))
    }

  test("round-eight War Exhaustion applies Usurper then Oathkeeper then random fallback"):
    val usurper = preparedTable(Vector.empty, holder = Some(p2),
      side = TitleSide.Usurper, round = 8)
    assertEquals(accepted(StateBasedEvaluation.endRound(
      Ready(atRoundEnd(usurper)), pickFirstCandidate)).last,
      WarExhaustionResolved(p2, VictoryKind.Usurper, None, Vector.empty))

    val keeper = preparedTable(Vector.empty, holder = Some(p3), round = 8)
    assertEquals(accepted(StateBasedEvaluation.endRound(
      Ready(atRoundEnd(keeper)), pickFirstCandidate)).last,
      WarExhaustionResolved(p3, VictoryKind.Oathkeeper, None, Vector.empty))

    val base = atRoundEnd(preparedTable(Vector.empty, round = 8))
    val random = as[WarExhaustionResolved](accepted(
      StateBasedEvaluation.endRound(Ready(base), pickSecondCandidate)).last)
    assertEquals(random.kind, VictoryKind.RandomSelection)
    assertEquals(random.winner, random.randomCandidates(1))
    assert(rules.evolve(Ready(base), random).isLeft,
      "War Exhaustion replay must include RoundEnded(8, None) first")
    val afterRound = accepted(rules.evolve(Ready(base), RoundEnded(8, None)))
    assert(rules.evolve(afterRound, random.copy(
      randomCandidates = random.randomCandidates.reverse)).isLeft)
    assertEquals(rules.evolve(afterRound, random).map(
      Outcomes.ready(_).game.current.result), Right(Some(GameResult(
        random.winner, VictoryKind.RandomSelection))))

  test("all printed Visionary outcomes resolve in printed priority order"):
    val conquestState = preparedTable(Vector(p1), round = 8)
    val rebellionState = preparedTable(Vector.empty, round = 8)
      .peoplesFavor(Some(p1), favor = 1)
    val cases = Vector(
      VisionRules.Conquest -> conquestState,
      VisionRules.Rebellion -> rebellionState,
      VisionRules.Sanctuary -> preparedTable(Vector.empty, round = 8)
        .relic(p1, "Dowsing Sticks", facedown = true),
      VisionRules.Faith -> preparedTable(Vector.empty, round = 8)
        .darkestSecret(Some(p1), secrets = 1)
    )
    cases.foreach { case (vision, table) =>
      val state = table.revealedVision(p1, vision).visionsDrawn(3)
      assertEquals(accepted(StateBasedEvaluation.endRound(
        Ready(atRoundEnd(state)), pickFirstCandidate)).last,
        WarExhaustionResolved(p1, VictoryKind.Visionary,
          Some(vision), Vector.empty), clue(vision))
    }

    // p1 qualifies for Conquest and Rebellion; p1 reveals Conquest and p2
    // Rebellion. Conquest comes first in the printed order.
    val combined = conquestState.peoplesFavor(Some(p1), favor = 1)
      .revealedVision(p1, VisionRules.Conquest)
      .revealedVision(p2, VisionRules.Rebellion).visionsDrawn(3)
    assertEquals(accepted(StateBasedEvaluation.endRound(
      Ready(atRoundEnd(combined)), pickFirstCandidate)).last,
      WarExhaustionResolved(p1, VictoryKind.Visionary,
        Some(VisionRules.Conquest), Vector.empty))

  test("round-eight Visionary eligibility still requires three Visions drawn"):
    // p1 rules Dunes and reveals Conquest; `holder` holds the title.
    def withCount(count: Int, holder: Option[PlayerId]) =
      preparedTable(Vector(p1), holder = holder, round = 8)
        .revealedVision(p1, VisionRules.Conquest).visionsDrawn(count)

    Vector(0, 2).foreach { count =>
      val event = accepted(StateBasedEvaluation.endRound(Ready(atRoundEnd(
        withCount(count, Some(p2)))), pickFirstCandidate)).last
      assertEquals(event, WarExhaustionResolved(p2,
        VictoryKind.Oathkeeper, None, Vector.empty), clue(count))
      val random = as[WarExhaustionResolved](accepted(
        StateBasedEvaluation.endRound(Ready(atRoundEnd(withCount(count, None))),
          pickLastCandidate)).last)
      assertEquals(random.kind, VictoryKind.RandomSelection, clue(count))
    }
    assertEquals(accepted(StateBasedEvaluation.endRound(Ready(atRoundEnd(
      withCount(3, None))), pickFirstCandidate)).last,
      WarExhaustionResolved(p1, VictoryKind.Visionary,
        Some(VisionRules.Conquest), Vector.empty))

    val below = atRoundEnd(withCount(2, None))
    val afterRound = accepted(rules.evolve(Ready(below), RoundEnded(8, None)))
    assert(rules.evolve(afterRound, WarExhaustionResolved(p1,
      VictoryKind.Visionary, Some(VisionRules.Conquest), Vector.empty)).isLeft)

  test("once the game has a result, every command is refused at the lifecycle gate"):
    // No Table step ends the game.
    def finished(phase: Phase) = preparedTable(Vector.empty).turn(p1, phase)
      .ready.updateCurrent(_.copy(
        result = Some(GameResult(p1, VictoryKind.Usurper))))
    val act = finished(Phase.Act)
    // p1 stands at Ancient City and would travel to Broken Peaks.
    val destination = CatalogNames.site("Broken Peaks")
    assertEquals(rules.startWalker(Ready(act), ActionRef.Travel, p1,
      Vector.empty, Vector(DecisionOptionRef.Site(destination))),
      Left(OathViolation.GameEnded))
    assertEquals(rules.startWalker(Ready(act), ActionRef.Forge, p1),
      Left(OathViolation.GameEnded))
    assertEquals(rules.startWalker(Ready(finished(Phase.Wake)),
      PhaseTransitionRef.EndWake, p1), Left(OathViolation.GameEnded))

  test("state-based operation policy permits only bandit-bank refills"):
    // Ancient City, capacity 3, holds no forces on the quiet table.
    val site = CatalogNames.site("Ancient City")
    val empty = Table.start.bandits(site, 0).ready
    val refill = Move(
      Piece.Warbands(ForceKind.Bandit, 1),
      PositionedLocation(Location.WarbandBank(ForceKind.Bandit)),
      PositionedLocation(Location.Site(site)))
    val wrongKind = refill.copy(piece = Piece.Warbands(
      ForceKind.Exile(Look(empty).player(p1).lineage), 1))

    assert(StateBasedOperationPolicy.validate(empty, refill).isRight)
    assert(StateBasedOperationPolicy.validate(empty, wrongKind).isLeft)
