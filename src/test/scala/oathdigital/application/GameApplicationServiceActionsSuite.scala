package oathdigital.application

import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.model._
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.walker.{ChoicePayload, WalkerStepRecorded}
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.model.DecisionAnswer.{ChooseAmountAnswer, ChooseOneAnswer}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathViolation.WrongPlayer
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PlacementPicks, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted
import ParkedServiceFixture.{loaded, optionRef, parkedAssertions,
  pickFacedownAdviser, playFacedownAdviser, replayingSecond}

/** The application service persists and reloads the walker actions and
  * parks besides Recover and Forge: League Treaty, previews, minor actions,
  * Challenge, Travel, the Oathkeeper tie, Muster and Search.
  */
class GameApplicationServiceActionsSuite extends munit.FunSuite:
  test("beginRest through the service parks the off-turn League Treaty ruler " +
      "and survives reload"):
    val ParkedServiceFixture.LeagueTreatyPark(game, _, _) =
      ParkedServiceFixture.leagueTreatyPark("game-league-treaty")
    val reloaded = loaded(new GameApplicationService(catalog, game.repository,
      genesis = game.service.genesis).load("game-league-treaty"))
    assertEquals(reloaded.state, game.accepted.state)

  test("only the League Treaty ruler's answer resumes the Rest: Wake, next " +
      "player"):
    val ParkedServiceFixture.LeagueTreatyPark(game, active, ruler) =
      ParkedServiceFixture.leagueTreatyPark("game-league-treaty-answer")
    val parked = game.accepted
    val decision = parkedAssertions.parkedDecision(parked.state).get.decision
    val decline = GameCommand.ResolveWalker(_: PlayerId, TreeDecision(
      decision, ChooseOneAnswer(DecisionOptionRef.Button("decline"))))
    assert(game.service.handle("game-league-treaty-answer",
      parked.nextSequence, decline(active)).isLeft)
    val finished = accepted(game.service.handle("game-league-treaty-answer",
      parked.nextSequence, decline(ruler)))
    val after = Outcomes.ready(finished.state)
    assertEquals(after.game.current.turn.phase, Phase.Wake)
    assertNotEquals(after.game.current.turn.activePlayer, active)

  test("major-action preview is stateless stale-safe and rejects unavailable modifiers"):
    // p1 holds a facedown adviser and starts in Wake, so ending Wake gives
    // the stream a record to be stale against.
    val table = Table.start.turn(p1, Phase.Wake)
      .adviser(p1, "Wizard's Conclave", facedown = true)
    val (service, repository) = table.service()
    val actor = p1
    val act = accepted(
      service.handle("game-preview", 0L, GameCommand.EndWake(actor)))
    val before = loaded(repository.load("game-preview")).records
    val preview = accepted(service.preview("game-preview", act.nextSequence,
      actor, oathdigital.model.ActionKind.Travel, Vector.empty))
    assertEquals(preview.loaded.nextSequence, act.nextSequence)
    assertEquals(loaded(repository.load("game-preview")).records, before)
    assert(service.preview("game-preview", act.nextSequence - 1, actor,
      oathdigital.model.ActionKind.Travel, Vector.empty).left.toOption
      .exists(_.isInstanceOf[GameApplicationError.StaleClientPosition]))
    val forged = oathdigital.model.OrderedRuleInvocation(
      oathdigital.model.RuleSourceRef.GameRule("forged"), "unknown")
    assert(service.preview("game-preview", act.nextSequence, actor,
      oathdigital.model.ActionKind.Travel, Vector(forged)).isLeft)
    assert(service.handle("game-preview", act.nextSequence,
      GameCommand.WithModifiers(playFacedownAdviser(actor),
        Vector(forged))).isLeft)
    val challenge = accepted(service.preview("game-preview", act.nextSequence,
      actor, oathdigital.model.ActionKind.Challenge, Vector.empty))
    assertEquals(challenge.options, Vector.empty)
    assertEquals(challenge.ignored, Vector.empty)

  test("minor adviser action persists and reloads through authoritative replay"):
    val table = Table.start.adviser(p1, "Wizard's Conclave", facedown = true)
    val (service, repository) = table.service()
    val actor = p1
    val adviser = CatalogNames.denizen("Wizard's Conclave")
    val started = accepted(service.handle("game-minor-replay", 0L,
      playFacedownAdviser(actor)))
    // The parked walk reloads to the same state before it is answered.
    assertEquals(loaded(replayingSecond(service, repository)
      .load("game-minor-replay")).state, started.state)
    val picked = accepted(service.handle("game-minor-replay",
      started.nextSequence, pickFacedownAdviser(actor, adviser)))
    val discarded = accepted(service.handle("game-minor-replay",
      picked.nextSequence, GameCommand.ResolveWalker(actor, TreeDecision(
        s"cardplay.place.${adviser.kind}.${adviser.value}",
        PlacementPicks.parkedAnswer(picked.state, PlayedAs.Discarded,
          WalkerPowerCatalog.default(catalog))))))
    val beforeRetry = loaded(repository.load("game-minor-replay")).records
    assert(service.handle("game-minor-replay", discarded.nextSequence,
      playFacedownAdviser(actor)).isLeft)
    assertEquals(loaded(repository.load("game-minor-replay")).records,
      beforeRetry)
    val reloaded = loaded(replayingSecond(service, repository)
      .load("game-minor-replay"))
    assertEquals(reloaded.state, discarded.state)
    assertEquals(reloaded.nextSequence, discarded.nextSequence)

  test("Challenge persists its walker park and reloads deterministic Mob completion"):
    // p1 holds the 2 favor the Challenge's amount decision will spend.
    val (service, repository) = Table.start.favor(p1, 2).service()
    val gameId = "game-challenge-persistence"
    val actor = p1
    var position = accepted(service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.Challenge, StartPayload(actor))))
    position = accepted(service.handle(gameId, position.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision("challenge.banner",
        ChooseOneAnswer(DecisionOptionRef.Banner(Banner.PeoplesFavor))))))
    val reloaded = loaded(replayingSecond(service, repository).load(gameId))
    assertEquals(reloaded.state, position.state)
    val pendingReady = Outcomes.ready(reloaded.state)
    assertEquals(pendingReady.game.current.walkerProcedure,
      Some(ActionRef.Challenge))
    val other = pendingReady.game.current.players.find(
      _.player != actor).get.player
    val projector = new GameProjector(catalog)
    assertEquals(projector.project(gameId, reloaded, actor).legalControls,
      Vector("resolveWalkerDecision"))
    assertEquals(projector.project(gameId, reloaded, other).legalControls,
      Vector.empty)
    val completed = accepted(replayingSecond(service, repository).handle(gameId,
      reloaded.nextSequence, GameCommand.ResolveWalker(actor, TreeDecision(
        "challenge.amount", ChooseAmountAnswer(2)))))
    val after = Outcomes.ready(completed.state)
    assertEquals(after.game.current.banners.peoplesFavor.holder, Some(actor))
    assertEquals(after.game.current.banners.peoplesFavor.favor, 2)
    val claimed = projector.project(gameId,
      LoadedGame(completed.state, completed.nextSequence), actor)
    assert(!claimed.banners.exists(_.key == "peoples-favor"))
    assertEquals(claimed.playerBoards.find(_.playerId == actor.value).toVector
      .flatMap(_.banners).map(banner => banner.key -> banner.resources),
      Vector("peoples-favor" -> 2))

  test("Travel parks on its destination, completes on the pick and reloads pawn Supply and Act"):
    // Bandits at every empty site, so the Travel's own events are the only
    // ones journaled: no refill follows it.
    val table = Table.start.banditsRefilled
    val (service, repository) = table.service()
    val active = p1
    val inAct = Outcomes.ready(table.state)
    val before = Look(inAct).player(active)
    // p1 stands at Ancient City and travels to the other Cradle site.
    val destination = CatalogNames.site("Broken Peaks")
    val parked = accepted(service.handle("game-travel", 0L,
      ParkedServiceFixture.startTravel(active)))
    val traveled = accepted(service.handle("game-travel", parked.nextSequence,
      ParkedServiceFixture.pickTravelDestination(active, destination)))
    val stored = loaded(replayingSecond(service, repository)
      .load("game-travel"))
    val after = Outcomes.ready(stored.state)
    val moved = Look(after).player(active)

    assertEquals(stored.state, traveled.state)
    assertEquals(moved.pawnSite, Some(destination))
    assert(moved.board.supply.supply < before.board.supply.supply)
    assertEquals(after.game.current.turn.phase, Phase.Act)
    // The start parks on the destination: its batch holds `CommandAccepted`
    // and the park. The pick's batch holds the recorded answer, the pay node,
    // the pawn move and the completion boundary, behind its own
    // `CommandAccepted`.
    assertEquals(parked.events.map(_.productPrefix), Vector("WalkerParked"))
    assertEquals(stored.nextSequence, 7L)
    assertEquals(traveled.events.map(_.productPrefix), Vector(
      "WalkerStepRecorded", "WalkerStepRecorded", "WalkerStepRecorded",
      "WalkerCompleted"))
    // Nothing is left parked once the destination is picked.
    assertEquals(after.game.current.walkerPending, None)
    assertEquals(after.game.current.walkerProcedure, None)
    val types = loaded(repository.load("game-travel")).records
      .takeRight(4).map(ujson.read(_)("eventType").str)
    assertEquals(types, Vector("walker.step-recorded", "walker.step-recorded",
      "walker.step-recorded", "walker.completed"))

  /** An off-turn Oathkeeper tie, reached and resolved entirely through
    * `GameApplicationService`, survives reload on both sides of the park.
    *
    * `ParkedServiceFixture.oathkeeperTiePark` states the board as a table:
    * p3 holds the title and p1 and p2 each rule one site. With exactly three
    * players, the active player is one of the two tied leaders and the
    * holder is off-turn, so p1's Travel reaches `OathkeeperRules.outcome`'s
    * `Choose`.
    */
  test("an off-turn Oathkeeper tie parks through the application service " +
      "and survives reload"):
    val gameId = "game-oathkeeper-tie"
    val ParkedServiceFixture.OathkeeperTiePark(game, active, holder, leaderB) =
      ParkedServiceFixture.oathkeeperTiePark(gameId)
    val parked = game.accepted
    assertEquals(
      loaded(game.repository.load(gameId)).records.size.toLong,
      parked.nextSequence)

    val reloadedParked = loaded(game.service.load(gameId))
    assertEquals(reloadedParked.state, parked.state)
    assertEquals(reloadedParked.nextSequence, parked.nextSequence)

    // The application gate rejects the active player's answer: only the
    // holder may resolve the recipient decision.
    assertEquals(
      game.service.handle(gameId, parked.nextSequence,
        GameCommand.ResolveWalker(active, TreeDecision(
          OathkeeperProcedure.recipientDecisionId,
          ChooseOneAnswer(DecisionOptionRef.Player(leaderB))))),
      Left(GameApplicationError.CommandRejected(
        WrongPlayer(holder, active))))

    val resolved = accepted(game.service.handle(gameId, parked.nextSequence,
      GameCommand.ResolveWalker(holder, TreeDecision(
        OathkeeperProcedure.recipientDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Player(leaderB))))))
    assert(resolved.events.exists {
      case WalkerStepRecorded(_, ChoicePayload(_, _, by), _, _) => by == holder
      case _ => false
    }, "the recorded choice must be answered by the holder")
    parkedAssertions.assertResumed(resolved.state, Phase.Act, active)
    val afterResolved = Outcomes.ready(resolved.state)
    assertEquals(afterResolved.game.current.title,
      OathkeeperState(Some(leaderB), TitleSide.Oathkeeper))

    val reloadedResolved = loaded(game.service.load(gameId))
    assertEquals(reloadedResolved.state, resolved.state)
    assertEquals(reloadedResolved.nextSequence, resolved.nextSequence)

  test("a walker Muster on an edifice persists, reloads and replays with its kind"):
    val siteId = CatalogNames.site("Deep Woods")
    val edificeId = CatalogNames.edifice("Hiding Place")
    val (service, repository) = Table.start.pawn(p1, siteId)
      .edifice("Hiding Place", EdificeSide.Ruined, siteId).service()
    val active = p1
    val started = accepted(service.handle("game-walker-economy", 0L,
      GameCommand.StartWalker(ActionRef.Muster, StartPayload(active))))
    parkedAssertions.assertParked(started.state, ActionRef.Muster,
      MusterProcedure.decisionId, active)
    val mustered = accepted(service.handle("game-walker-economy",
      started.nextSequence, GameCommand.ResolveWalker(active,
        TreeDecision(MusterProcedure.decisionId,
        ChooseOneAnswer(DecisionOptionRef.Edifice(edificeId))))))
    val stored = loaded(replayingSecond(service, repository)
      .load("game-walker-economy"))
    assertEquals(stored.state, mustered.state)
    val after = Outcomes.ready(stored.state)
    // Muster pays 1 favor onto the card it uses.
    assertEquals(after.game.current.map.sites(siteId).denizens.collectFirst {
      case value: EdificeState if value.id == edificeId => value.tokens
    }, Some(Tokens(1, 0)))
    val records = loaded(repository.load("game-walker-economy")).records
    assert(records.exists(record => record.contains("edifice") &&
      record.contains(edificeId.value)),
      "the journalled answer must spell the edifice kind and id")

  test("Search draw port cannot inject card identities inconsistent with state"):
    /** Stub of SearchDrawPort: always draws DenizenId("denizen:tampered"). */
    val port = new SearchDrawPort:
      def prepare(ready: ReadyGame, source: SearchSource, origin: Region): Either[OathViolation, Vector[WorldCardId]] =
        Right(Vector(DenizenId("denizen:tampered")))
    // Built by hand: `Table#service` takes no draw port.
    val repository = new InMemoryEventStreamRepository
    val table = Table.start
    val service = new GameApplicationService(catalog, repository, port,
      genesis = table.state)
    val active = p1
    val started = accepted(service.handle("game-search-tamper", 0L,
      ParkedServiceFixture.startSearch(active)))
    assertEquals(service.handle("game-search-tamper", started.nextSequence,
      ParkedServiceFixture.pickSearchSource(active, SearchSource.WorldDeck))
      .left.toOption, Some(GameApplicationError.CommandRejected(
        OathViolation.SearchDrawMismatch(
          "prepared draw does not match authoritative source order"))))
    assertEquals(loaded(service.load("game-search-tamper")).nextSequence,
      started.nextSequence)

  test("walker Search persists its card choice and completes after reload"):
    // Three denizens on top of the world deck, so the Search offers a choice.
    val (service, repository) = Table.start
      .worldDeckTop("Threatening Roar", "Fae Merchant", "Second Chance")
      .service()
    val gameId = "game-walker-search"
    val actor = p1
    val begun = accepted(service.handle(gameId, 0L,
      ParkedServiceFixture.startSearch(actor)))
    val started = accepted(service.handle(gameId, begun.nextSequence,
      ParkedServiceFixture.pickSearchSource(actor, SearchSource.WorldDeck)))
    val reloaded = loaded(replayingSecond(service, repository).load(gameId))
    assertEquals(reloaded.state, started.state)
    val afterDraw = Outcomes.ready(reloaded.state)
    val drawn = afterDraw.game.current.temporaryHands(actor)
    val roar = CatalogNames.denizen("Threatening Roar")
    val merchant = CatalogNames.denizen("Fae Merchant")
    val secondChance = CatalogNames.denizen("Second Chance")
    assertEquals(drawn, Vector[WorldCardId](roar, merchant, secondChance))
    // Keep Threatening Roar and discard the other two.
    val assignments = Vector(DecisionPlacement(optionRef(roar), "keep"),
      DecisionPlacement(optionRef(merchant), "discard"),
      DecisionPlacement(optionRef(secondChance), "discard"))
    val chosen = accepted(service.handle(gameId, reloaded.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision("search.cards",
        DecisionAnswer.PartitionAnswer(assignments)))))
    val completed = accepted(service.handle(gameId, chosen.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(
        s"cardplay.place.${roar.kind}.${roar.value}",
        PlacementPicks.parkedAnswer(chosen.state, PlayedAs.Discarded,
          WalkerPowerCatalog.default(catalog))))))
    assertEquals(loaded(replayingSecond(service, repository)
      .load(gameId)).state, completed.state)
