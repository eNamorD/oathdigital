package oathdigital.gameplay

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.TravelCommand
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.phases.{RestCommand, WakeCommand}
import oathdigital.model._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.OathViolation._

class StateBasedEvaluationSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)

  private def prepared(
      owners: Vector[Option[PlayerId]],
      holder: Option[PlayerId] = None,
      side: TitleSide = TitleSide.Oathkeeper,
      round: Int = 1,
      limited: Boolean = true
  ): ReadyGame = {
    val Ready(base) = execute(setup)._1: @unchecked
    val byPlayer = base.game.current.players.map(p => p.player -> p.lineage).toMap
    val sites = base.game.current.map.inPlay.zipWithIndex.map { case (id, index) =>
      val force = owners.lift(index).flatten.fold[SiteForces](
        SiteForces.Occupied(ForceKind.Bandit, 1))(player =>
        SiteForces.Occupied(ForceKind.Exile(byPlayer(player)), 1))
      id -> base.game.current.map.sites(id).copy(forces = force)
    }.toMap
    base.copy(game = base.game.copy(current = base.game.current.copy(
      map = base.game.current.map.copy(sites = sites),
      title = OathkeeperState(holder, side),
      tracks = base.game.current.tracks.copy(round = round,
        usurperLimited = limited))))
  }

  private def atRoundEnd(ready: ReadyGame): ReadyGame = ready.copy(game =
    ready.game.copy(current = ready.game.current.copy(
      turn = ready.game.current.turn.copy(phase = Phase.RoundEnd))))

  test("first-game Supremacy qualification transfers at a completed action boundary") {
    val base = prepared(Vector(Some(PlayerId("p2"))))
    val actor = base.game.current.turn.activePlayer
    val act = base.copy(game = base.game.copy(current = base.game.current.copy(
      turn = TurnState(actor, Phase.Act, Set.empty))))
    val destination = base.game.current.map.inPlay.find(
      _ != base.game.current.players.find(_.player == actor).get.pawnSite.get).get
    val accepted = rules.handle(Ready(act), TravelCommand.Travel(actor, destination))
      .toOption.get

    assert(accepted.events.head.isInstanceOf[Traveled])
    assertEquals(accepted.events.last, OathkeeperChanged(Some(PlayerId("p2"))))
    val Ready(after) = accepted.state: @unchecked
    assertEquals(after.game.current.title,
      OathkeeperState(Some(PlayerId("p2")), TitleSide.Oathkeeper))
    val replayed = accepted.events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(act)))((state, event) => state.flatMap(rules.evolve(_, event)))
    assertEquals(replayed, Right(accepted.state))
  }

  test("F7 retains a highest tied holder but does not invent an initial tie winner") {
    val players = execute(setup)._1.asInstanceOf[Ready].value.game.current.players
      .map(_.player)
    val tied = Vector(Some(players(0)), Some(players(1)))
    assertEquals(StateBasedEvaluation.afterAction(Ready(prepared(tied))), Right(None))

    val retained = prepared(tied, holder = Some(players(0)))
    assertEquals(StateBasedEvaluation.afterAction(Ready(retained)), Right(None))
  }

  test("F7 records and resolves a displaced-holder tie choice") {
    val players = execute(setup)._1.asInstanceOf[Ready].value.game.current.players
      .map(_.player)
    val state = prepared(Vector(Some(players(0)), Some(players(1))),
      holder = Some(players(2)))
    val started = StateBasedEvaluation.afterAction(Ready(state)).toOption.get.get
      .asInstanceOf[OathkeeperRecipientChoiceStarted]
    assertEquals(started.actor, players(2))
    assertEquals(started.candidates, Vector(players(0), players(1)))
    val pending = rules.evolve(Ready(state), started).toOption.get
    val projector = new GameProjector(catalog)
    assertEquals(projector.project("tie", LoadedGame(pending, 10), players(2))
      .oathkeeperRecipient.map(_.candidatePlayerIds),
      Some(started.candidates.map(_.value)))
    assertEquals(projector.project("tie", LoadedGame(pending, 10), players(0))
      .oathkeeperRecipient, None)
    assertEquals(projector.projectPublic("tie", LoadedGame(pending, 10))
      .oathkeeperRecipient, None)
    assert(rules.chooseOathkeeperRecipient(pending, players(0), started.decision,
      players(0)).left.toOption.get.isInstanceOf[WrongPlayer])
    assert(rules.chooseOathkeeperRecipient(pending, players(2), started.decision,
      players(2)).isLeft)
    val chosen = rules.chooseOathkeeperRecipient(pending, players(2),
      started.decision, players(1)).toOption.get
    assertEquals(chosen.events,
      Vector(OathkeeperRecipientChosen(players(2), started.decision, players(1))))
    val Ready(after) = chosen.state: @unchecked
    assertEquals(after.game.current.title,
      OathkeeperState(Some(players(1)), TitleSide.Oathkeeper))
    assertEquals(after.game.current.pending, None)
    assertEquals(rules.evolve(pending, chosen.events.head), Right(chosen.state))
  }

  test("all four printed goals qualify from their authoritative holdings") {
    val base = prepared(Vector.empty)
    val players = base.game.current.players.map(_.player)
    val relicLeader = players(1)
    val withRelics = base.game.current.players.map { player =>
      if (player.player == relicLeader) player.copy(relics = Vector(
        RelicState(RelicId("qualification-relic"), Orientation.FaceDown,
          Tokens.empty)))
      else player
    }

    val cases = Vector(
      OathkeeperGoal.Supremacy -> prepared(Vector(Some(players.head))),
      OathkeeperGoal.Protection -> base.copy(game = base.game.copy(
        current = base.game.current.copy(players = withRelics))),
      OathkeeperGoal.ThePeople -> base.copy(game = base.game.copy(
        current = base.game.current.copy(banners = base.game.current.banners.copy(
          peoplesFavor = base.game.current.banners.peoplesFavor.copy(
            holder = Some(players(2))))))),
      OathkeeperGoal.Devotion -> base.copy(game = base.game.copy(
        current = base.game.current.copy(banners = base.game.current.banners.copy(
          darkestSecret = base.game.current.banners.darkestSecret.copy(
            holder = Some(players(0)))))))
    )

    cases.foreach { case (goal, state) =>
      val expected = goal match {
        case OathkeeperGoal.Supremacy => players.head
        case OathkeeperGoal.Protection => relicLeader
        case OathkeeperGoal.ThePeople => players(2)
        case OathkeeperGoal.Devotion => players(0)
      }
      val scoped = state.copy(game = state.game.copy(
        campaign = state.game.campaign.copy(oathkeeperGoal = goal)))
      val event = OathkeeperChanged(Some(expected))
      assertEquals(StateBasedEvaluation.afterAction(Ready(scoped)),
        Right(Some(event)), clue(goal))
      assertEquals(rules.evolve(Ready(scoped), event).map(_.asInstanceOf[Ready]
        .value.game.current.title.holder), Right(Some(expected)), clue(goal))
      assertEquals(new GameProjector(catalog).projectPublic("goal",
        LoadedGame(Ready(scoped), 0)).oathkeeper.map(_.goal),
        Some(goal.key), clue(goal))
    }
  }

  test("Protection requires at least one relic and preserves highest-count ties") {
    val base = prepared(Vector.empty)
    val players = base.game.current.players
    val protection = base.copy(game = base.game.copy(
      campaign = base.game.campaign.copy(
        oathkeeperGoal = OathkeeperGoal.Protection)))
    assertEquals(StateBasedEvaluation.afterAction(Ready(protection)), Right(None))

    val tiedPlayers = players.zipWithIndex.map { case (player, index) =>
      if (index < 2) player.copy(relics = Vector(RelicState(
        RelicId(s"tied-relic-$index"), Orientation.FaceDown, Tokens.empty)))
      else player
    }
    val tied = protection.copy(game = protection.game.copy(
      current = protection.game.current.copy(players = tiedPlayers,
        title = OathkeeperState(Some(players.head.player), TitleSide.Oathkeeper))))
    assertEquals(StateBasedEvaluation.afterAction(Ready(tied)), Right(None))
  }

  test("round four releases limiter and retained Usurper wins next Wake") {
    val base = execute(setup)._1.asInstanceOf[Ready].value
    val holder = base.setup.firstPlayer
    val initial = Ready(prepared(Vector(Some(holder)), Some(holder),
      round = 3, limited = true))
    val order = initial.value.game.current.players.map(_.player)
    val start = order.indexOf(holder)
    val turnOrder = order.drop(start) ++ order.take(start)
    var state: OathState = initial
    var events = Vector.empty[OathEvent]

    def accept(transition: Either[OathViolation, OathTransition]): Unit = {
      val accepted = transition.toOption.get
      state = accepted.state
      events ++= accepted.events
    }
    def finishTurn(player: PlayerId): Unit = {
      accept(rules.handle(state, WakeCommand.EndWake(player)))
      accept(rules.handle(state, RestCommand.Begin(player)))
      accept(rules.handle(state, RestCommand.Finish(player)))
    }

    turnOrder.foreach(finishTurn)
    val roundFour = state.asInstanceOf[Ready].value
    assertEquals(roundFour.game.current.tracks.round, 4)
    assertEquals(roundFour.game.current.tracks.usurperLimited, false)
    assertEquals(roundFour.game.current.title,
      OathkeeperState(Some(holder), TitleSide.Usurper))
    assert(events.contains(UsurperFlipped(holder)))

    accept(rules.handle(state, WakeCommand.EndWake(holder)))
    val holderState = state.asInstanceOf[Ready].value
    val destination = holderState.game.current.map.inPlay.find(
      _ != holderState.game.current.players.find(_.player == holder)
        .flatMap(_.pawnSite).get).get
    accept(rules.handle(state, TravelCommand.Travel(holder, destination)))
    assertEquals(state.asInstanceOf[Ready].value.game.current.title,
      OathkeeperState(Some(holder), TitleSide.Usurper))
    accept(rules.handle(state, RestCommand.Begin(holder)))
    accept(rules.handle(state, RestCommand.Finish(holder)))
    turnOrder.tail.foreach(finishTurn)

    val won = state.asInstanceOf[Ready].value
    assertEquals(won.game.current.result, Some(GameResult(holder)))
    assertEquals(events.last, UsurperVictory(holder))
    val replayed = events.foldLeft[Either[OathViolation, OathState]](
      Right(initial))((next, event) => next.flatMap(rules.evolve(_, event)))
    assertEquals(replayed, Right(state))
  }

  test("all four true Visions require three drawn and a unique qualifying leader") {
    val base = prepared(Vector.empty)
    val active = base.game.current.turn.activePlayer
    val relic = RelicState(RelicId("vision-relic"), Orientation.FaceDown, Tokens.empty)
    def bannerState(banner: Banner) = base.copy(game = base.game.copy(current =
      base.game.current.copy(banners = banner match {
        case Banner.PeoplesFavor => base.game.current.banners.copy(
          peoplesFavor = base.game.current.banners.peoplesFavor.copy(holder = Some(active)))
        case Banner.DarkestSecret => base.game.current.banners.copy(
          darkestSecret = base.game.current.banners.darkestSecret.copy(holder = Some(active)))
      })))
    val cases = Vector(
      VisionRules.Conquest -> prepared(Vector(Some(active))),
      VisionRules.Sanctuary -> base.copy(game = base.game.copy(current =
        base.game.current.copy(players = base.game.current.players.map(p =>
          if (p.player == active) p.copy(relics = Vector(relic)) else p)))),
      VisionRules.Rebellion -> bannerState(Banner.PeoplesFavor),
      VisionRules.Faith -> bannerState(Banner.DarkestSecret)
    )
    cases.foreach { case (vision, state0) =>
      val state = state0.copy(game = state0.game.copy(current = state0.game.current.copy(
        players = state0.game.current.players.map(p => if (p.player == active)
          p.copy(revealedVision = Some(VisionState(vision, Orientation.FaceUp))) else p),
        tracks = state0.game.current.tracks.copy(visionsDrawn = 3))))
      assertEquals(StateBasedEvaluation.visionAtWake(Ready(state)),
        Right(Some(VisionVictory(active, vision))), clue(vision))
      val below = state.copy(game = state.game.copy(current = state.game.current.copy(
        tracks = state.game.current.tracks.copy(visionsDrawn = 2))))
      assertEquals(StateBasedEvaluation.visionAtWake(Ready(below)), Right(None), clue(vision))
    }
  }

  test("a newly flipped Oathkeeper can win by Vision but never immediately as Usurper") {
    val base = prepared(Vector.empty)
    val active = base.game.current.turn.activePlayer
    val qualifying = prepared(Vector(Some(active)), holder = Some(active), limited = false)
    val withVision = qualifying.copy(game = qualifying.game.copy(current =
      qualifying.game.current.copy(players = qualifying.game.current.players.map(p =>
        if (p.player == active) p.copy(revealedVision = Some(VisionState(
          VisionRules.Conquest, Orientation.FaceUp))) else p), tracks =
        qualifying.game.current.tracks.copy(visionsDrawn = 3))))
    assertEquals(StateBasedEvaluation.atWake(Ready(withVision)),
      Right(Some(UsurperFlipped(active))))
    val flipped = rules.evolve(Ready(withVision), UsurperFlipped(active)).toOption.get
    assertEquals(StateBasedEvaluation.visionAtWake(flipped),
      Right(Some(VisionVictory(active, VisionRules.Conquest))))
    assertEquals(StateBasedEvaluation.atWake(flipped),
      Right(Some(UsurperVictory(active))))

    val noVision = qualifying.copy(game = qualifying.game.copy(current =
      qualifying.game.current.copy(tracks = qualifying.game.current.tracks.copy(
        visionsDrawn = 3))))
    val afterFlip = rules.evolve(Ready(noVision), UsurperFlipped(active)).toOption.get
    assertEquals(StateBasedEvaluation.visionAtWake(afterFlip), Right(None))
  }

  test("rounds one through seven advance without War Exhaustion") {
    (1 to 7).foreach { round =>
      val state = prepared(Vector.empty, round = round)
      val events = StateBasedEvaluation.endRound(Ready(atRoundEnd(state)), _.head)
        .toOption.get
      assertEquals(events, Vector(RoundEnded(round, Some(round + 1))), clue(round))
    }
  }

  test("round-eight War Exhaustion applies Usurper then Oathkeeper then random fallback") {
    val base = prepared(Vector.empty, round = 8)
    val players = base.game.current.players.map(_.player)
    val usurper = base.copy(game = base.game.copy(current = base.game.current.copy(
      title = OathkeeperState(Some(players(1)), TitleSide.Usurper))))
    assertEquals(StateBasedEvaluation.endRound(Ready(atRoundEnd(usurper)), _.head).toOption.get.last,
      WarExhaustionResolved(players(1), VictoryKind.Usurper, None, Vector.empty))

    val keeper = base.copy(game = base.game.copy(current = base.game.current.copy(
      title = OathkeeperState(Some(players(2)), TitleSide.Oathkeeper))))
    assertEquals(StateBasedEvaluation.endRound(Ready(atRoundEnd(keeper)), _.head).toOption.get.last,
      WarExhaustionResolved(players(2), VictoryKind.Oathkeeper, None, Vector.empty))

    val random = StateBasedEvaluation.endRound(Ready(atRoundEnd(base)), _(1)).toOption.get.last
      .asInstanceOf[WarExhaustionResolved]
    assertEquals(random.kind, VictoryKind.RandomSelection)
    assertEquals(random.winner, random.randomCandidates(1))
    assert(rules.evolve(Ready(atRoundEnd(base)), random).isLeft,
      "War Exhaustion replay must include RoundEnded(8, None) first")
    val afterRound = rules.evolve(Ready(atRoundEnd(base)), RoundEnded(8, None)).toOption.get
    assert(rules.evolve(afterRound, random.copy(
      randomCandidates = random.randomCandidates.reverse)).isLeft)
    assertEquals(rules.evolve(afterRound, random).map(_.asInstanceOf[Ready]
      .value.game.current.result), Right(Some(GameResult(random.winner,
      VictoryKind.RandomSelection))))
  }

  test("all printed Visionary outcomes resolve in printed priority order") {
    val base = prepared(Vector.empty, round = 8)
    val players = base.game.current.players.map(_.player)
    val relic = RelicState(RelicId("war-vision-relic"), Orientation.FaceDown,
      Tokens.empty)
    val cases = Vector(
      VisionRules.Conquest -> prepared(Vector(Some(players(0))), round = 8),
      VisionRules.Rebellion -> base.copy(game = base.game.copy(current =
        base.game.current.copy(banners = base.game.current.banners.copy(
          peoplesFavor = base.game.current.banners.peoplesFavor.copy(
            holder = Some(players(0))))))),
      VisionRules.Sanctuary -> base.copy(game = base.game.copy(current =
        base.game.current.copy(players = base.game.current.players.map(p =>
          if (p.player == players(0)) p.copy(relics = Vector(relic)) else p)))),
      VisionRules.Faith -> base.copy(game = base.game.copy(current =
        base.game.current.copy(banners = base.game.current.banners.copy(
          darkestSecret = base.game.current.banners.darkestSecret.copy(
            holder = Some(players(0)))))))
    )
    cases.foreach { case (vision, state0) =>
      val state = state0.copy(game = state0.game.copy(current =
        state0.game.current.copy(players = state0.game.current.players.map(p =>
          if (p.player == players(0)) p.copy(revealedVision = Some(
            VisionState(vision, Orientation.FaceUp))) else p), tracks =
          state0.game.current.tracks.copy(visionsDrawn = 3))))
      assertEquals(StateBasedEvaluation.endRound(Ready(atRoundEnd(state)), _.head).toOption.get.last,
        WarExhaustionResolved(players(0), VictoryKind.Visionary,
          Some(vision), Vector.empty), clue(vision))
    }

    val conquest = cases.head._2
    val rebellion = cases(1)._2
    val combinedPlayers = conquest.game.current.players.zipWithIndex.map {
      case (p, 0) => p.copy(revealedVision = Some(VisionState(
        VisionRules.Conquest, Orientation.FaceUp)))
      case (p, 1) => p.copy(revealedVision = Some(VisionState(
        VisionRules.Rebellion, Orientation.FaceUp)))
      case (p, _) => p
    }
    val combined = conquest.copy(game = conquest.game.copy(current =
      conquest.game.current.copy(players = combinedPlayers,
        banners = rebellion.game.current.banners,
        tracks = conquest.game.current.tracks.copy(visionsDrawn = 3))))
    assertEquals(StateBasedEvaluation.endRound(Ready(atRoundEnd(combined)), _.head).toOption.get.last,
      WarExhaustionResolved(players(0), VictoryKind.Visionary,
        Some(VisionRules.Conquest), Vector.empty))
  }

  test("round-eight Visionary eligibility still requires three Visions drawn") {
    val base = prepared(Vector.empty, round = 8)
    val player = base.game.current.players.head.player
    val qualified = prepared(Vector(Some(player)), round = 8)
    def withCount(count: Int, holder: Option[PlayerId]) = qualified.copy(game =
      qualified.game.copy(current = qualified.game.current.copy(
        players = qualified.game.current.players.map(p => if (p.player == player)
          p.copy(revealedVision = Some(VisionState(VisionRules.Conquest,
            Orientation.FaceUp))) else p),
        tracks = qualified.game.current.tracks.copy(visionsDrawn = count),
        title = OathkeeperState(holder, TitleSide.Oathkeeper))))

    Vector(0, 2).foreach { count =>
      val keeper = base.game.current.players(1).player
      val event = StateBasedEvaluation.endRound(Ready(atRoundEnd(
        withCount(count, Some(keeper)))), _.head).toOption.get.last
      assertEquals(event, WarExhaustionResolved(keeper,
        VictoryKind.Oathkeeper, None, Vector.empty), clue(count))
      val random = StateBasedEvaluation.endRound(Ready(atRoundEnd(
        withCount(count, None))), _.last).toOption.get.last
        .asInstanceOf[WarExhaustionResolved]
      assertEquals(random.kind, VictoryKind.RandomSelection, clue(count))
    }
    assertEquals(StateBasedEvaluation.endRound(Ready(atRoundEnd(
      withCount(3, None))), _.head).toOption.get.last,
      WarExhaustionResolved(player, VictoryKind.Visionary,
        Some(VisionRules.Conquest), Vector.empty))

    val below = atRoundEnd(withCount(2, None))
    val afterRound = rules.evolve(Ready(below), RoundEnded(8, None)).toOption.get
    assert(rules.evolve(afterRound, WarExhaustionResolved(player,
      VictoryKind.Visionary, Some(VisionRules.Conquest), Vector.empty)).isLeft)
  }
}
