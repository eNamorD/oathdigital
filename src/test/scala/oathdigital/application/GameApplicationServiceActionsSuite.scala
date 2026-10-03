package oathdigital.application

import oathdigital.model._
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.walker.{ChoicePayload, WalkerStepRecorded}
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.model.DecisionAnswer.{ChooseAmountAnswer, ChooseOneAnswer}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathViolation.WrongPlayer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1
import ParkedServiceFixture.{parkedAssertions, replayingSecond}

/** The application service persists and reloads the walker actions and
  * parks besides Recover and Forge: League Treaty, previews, minor actions,
  * Challenge, Travel, the Oathkeeper tie, Muster and Search.
  */
class GameApplicationServiceActionsSuite extends munit.FunSuite:
  test("beginRest through the service parks the off-turn League Treaty ruler " +
      "and survives reload"):
    val ParkedServiceFixture.LeagueTreatyPark(game, active, ruler) =
      ParkedServiceFixture.leagueTreatyPark("game-league-treaty")
    val (service, parked) = (game.service, game.accepted)
    val reloaded = new GameApplicationService(catalog, game.repository,
      genesis = service.genesis).load("game-league-treaty").toOption.flatten.get
    assertEquals(reloaded.state, parked.state)
    val decision = parkedAssertions.parkedDecision(parked.state).get.decision
    val decline = GameCommand.ResolveWalker(_: PlayerId, TreeDecision(
      decision, ChooseOneAnswer(DecisionOptionRef.Button("decline"))))
    assert(service.handle("game-league-treaty", parked.nextSequence,
      decline(active)).isLeft)
    val finished = service.handle("game-league-treaty", parked.nextSequence,
      decline(ruler)).toOption.get
    val Ready(after) = finished.state: @unchecked
    assertEquals(after.game.current.turn.phase, Phase.Wake)
    assertNotEquals(after.game.current.turn.activePlayer, active)

  test("major-action preview is stateless stale-safe and rejects unavailable modifiers"):
    // p1 holds a facedown adviser and starts in Wake, so ending Wake gives
    // the stream a record to be stale against.
    val table = Table.start.turn(p1, Phase.Wake)
      .adviser(p1, "Wizard's Conclave", facedown = true)
    val (service, repository) = table.service()
    val Ready(ready) = table.state: @unchecked
    val actor = p1
    val act = service.handle("game-preview", 0L, GameCommand.EndWake(actor))
      .toOption.get
    val before = repository.load("game-preview").toOption.flatten.get.records
    val preview = service.preview("game-preview", act.nextSequence, actor,
      oathdigital.model.ActionKind.Travel, Vector.empty).toOption.get
    assertEquals(preview.loaded.nextSequence, act.nextSequence)
    assertEquals(repository.load("game-preview").toOption.flatten.get.records, before)
    assert(service.preview("game-preview", act.nextSequence - 1, actor,
      oathdigital.model.ActionKind.Travel, Vector.empty).left.toOption
      .exists(_.isInstanceOf[GameApplicationError.StaleClientPosition]))
    val forged = oathdigital.model.OrderedRuleInvocation(
      oathdigital.model.RuleSourceRef.GameRule("forged"), "unknown")
    assert(service.preview("game-preview", act.nextSequence, actor,
      oathdigital.model.ActionKind.Travel, Vector(forged)).isLeft)
    val adviser = ready.game.current.players.find(_.player == actor).get.advisers.head.id
      .asInstanceOf[WorldCardId]
    assert(service.handle("game-preview", act.nextSequence,
      GameCommand.WithModifiers(GameCommand.StartWalker(
        ActionRef.PlayFacedownAdviser, StartPayload(actor, Vector.empty,
          Vector(adviser match {
            case id: DenizenId => DecisionOptionRef.Denizen(id)
            case id: VisionId => DecisionOptionRef.Vision(id)
          }))), Vector(forged))).isLeft)
    val challenge = service.preview("game-preview", act.nextSequence, actor,
      oathdigital.model.ActionKind.Challenge, Vector.empty).toOption.get
    assertEquals(challenge.options, Vector.empty)
    assertEquals(challenge.ignored, Vector.empty)

  test("minor adviser action persists and reloads through authoritative replay"):
    val table = Table.start.adviser(p1, "Wizard's Conclave", facedown = true)
    val (service, repository) = table.service()
    val Ready(ready) = table.state: @unchecked
    val actor = p1
    val adviser = ready.game.current.players.find(_.player == actor).get.advisers.head.id
      .asInstanceOf[WorldCardId]
    val started = service.handle("game-minor-replay", 0L,
      GameCommand.StartWalker(ActionRef.PlayFacedownAdviser,
        StartPayload(actor, Vector.empty, Vector(adviser match {
          case id: DenizenId => DecisionOptionRef.Denizen(id)
          case id: VisionId => DecisionOptionRef.Vision(id)
        })))).toOption.get
    // The parked walk reloads to the same state before it is answered.
    assertEquals(replayingSecond(service, repository)
      .load("game-minor-replay").toOption.flatten.get.state, started.state)
    val discarded = service.handle("game-minor-replay", started.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(
        s"cardplay.place.${adviser.kind}.${adviser.value}",
        ChooseOneAnswer(DecisionOptionRef.Button("discard"))))).toOption.get
    val beforeRetry = repository.load("game-minor-replay").toOption.flatten.get.records
    assert(service.handle("game-minor-replay", discarded.nextSequence,
      GameCommand.StartWalker(ActionRef.PlayFacedownAdviser,
        StartPayload(actor, Vector.empty, Vector(adviser match {
          case id: DenizenId => DecisionOptionRef.Denizen(id)
          case id: VisionId => DecisionOptionRef.Vision(id)
        })))).isLeft)
    assertEquals(repository.load("game-minor-replay").toOption.flatten.get.records,
      beforeRetry)
    val reloaded = replayingSecond(service, repository)
      .load("game-minor-replay").toOption.flatten.get
    assertEquals(reloaded.state, discarded.state)
    assertEquals(reloaded.nextSequence, discarded.nextSequence)

  test("Challenge persists its walker park and reloads deterministic Mob completion"):
    // p1 holds the 2 favor the Challenge's amount decision will spend.
    val (service, repository) = Table.start.favor(p1, 2).service()
    val gameId = "game-challenge-persistence"
    val actor = p1
    var accepted = service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.Challenge, StartPayload(actor)))
      .toOption.get
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision("challenge.banner",
        ChooseOneAnswer(DecisionOptionRef.Banner(Banner.PeoplesFavor)))))
      .toOption.get
    val reloaded = replayingSecond(service, repository)
      .load(gameId).toOption.flatten.get
    assertEquals(reloaded.state, accepted.state)
    val Ready(pendingReady) = reloaded.state: @unchecked
    assertEquals(pendingReady.game.current.walkerProcedure,
      Some(ActionRef.Challenge))
    val other = pendingReady.game.current.players.find(_.player != actor).get.player
    val projector = new GameProjector(catalog)
    assertEquals(projector.project(gameId, reloaded, actor).legalControls,
      Vector("resolveWalkerDecision"))
    assertEquals(projector.project(gameId, reloaded, other).legalControls,
      Vector.empty)
    val completed = replayingSecond(service, repository).handle(gameId,
      reloaded.nextSequence, GameCommand.ResolveWalker(actor, TreeDecision(
        "challenge.amount", ChooseAmountAnswer(2)))).toOption.get
    val Ready(after) = completed.state: @unchecked
    assertEquals(after.game.current.banners.peoplesFavor.holder, Some(actor))
    assertEquals(after.game.current.banners.peoplesFavor.favor, 2)
    val claimed = projector.project(gameId,
      LoadedGame(completed.state, completed.nextSequence), actor)
    assert(!claimed.banners.exists(_.key == "peoples-favor"))
    assertEquals(claimed.playerBoards.find(_.playerId == actor.value).toVector
      .flatMap(_.banners).map(banner => banner.key -> banner.resources),
      Vector("peoples-favor" -> 2))

  test("Travel is one atomic walker command and reloads pawn Supply and Act"):
    // Bandits at every empty site, so the Travel's own events are the only
    // ones journaled: no refill follows it.
    val table = Table.start.banditsRefilled
    val (service, repository) = table.service()
    val active = p1
    val Ready(inAct) = table.state: @unchecked
    val before = inAct.game.current.players.find(_.player == active).get
    val destination = inAct.game.current.map.cradle.find(
      !before.pawnSite.contains(_)).getOrElse(
        inAct.game.current.map.provinces.head)
    val traveled = service.handle("game-travel", 0L,
      GameCommand.StartWalker(ActionRef.Travel, StartPayload(active,
        Vector.empty, Vector(DecisionOptionRef.Site(destination))))
      ).toOption.get
    val loaded = replayingSecond(service, repository)
      .load("game-travel").toOption.flatten.get
    val Ready(after) = loaded.state: @unchecked
    val moved = after.game.current.players.find(_.player == active).get

    assertEquals(loaded.state, traveled.state)
    assertEquals(moved.pawnSite, Some(destination))
    assert(moved.board.supply.supply < before.board.supply.supply)
    assertEquals(after.game.current.turn.phase, Phase.Act)
    // The single legacy travel event became the walker's own three: the pay
    // node, the pawn move, and the completion boundary. That is the whole
    // observable shape change of the port -- the state each side reaches is
    // identical, which is what the assertions above pin.
    assertEquals(loaded.nextSequence, 3L)
    assertEquals(traveled.events.map(_.productPrefix),
      Vector("WalkerStepRecorded", "WalkerStepRecorded", "WalkerCompleted"))
    // Nothing parked: a flat tree finishes inside the command that started it.
    assertEquals(after.game.current.walkerPending, None)
    assertEquals(after.game.current.walkerProcedure, None)
    val types = repository.load("game-travel").toOption.flatten.get.records
      .takeRight(3).map(ujson.read(_)("eventType").str)
    assertEquals(types, Vector("walker.step-recorded", "walker.step-recorded",
      "walker.completed"))

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
    val (service, repository, parked) = (game.service, game.repository,
      game.accepted)
    assertEquals(
      repository.load(gameId).toOption.flatten.get.records.size.toLong,
      parked.nextSequence)

    val reloadedParked = service.load(gameId).toOption.flatten.get
    assertEquals(reloadedParked.state, parked.state)
    assertEquals(reloadedParked.nextSequence, parked.nextSequence)

    // The application gate rejects the active player's answer: only the
    // holder may resolve the recipient decision.
    assertEquals(
      service.handle(gameId, parked.nextSequence, GameCommand.ResolveWalker(
        active, TreeDecision(OathkeeperProcedure.recipientDecisionId,
          ChooseOneAnswer(DecisionOptionRef.Player(leaderB))))),
      Left(GameApplicationError.CommandRejected(
        WrongPlayer(holder, active))))

    val resolved = service.handle(gameId, parked.nextSequence,
      GameCommand.ResolveWalker(holder, TreeDecision(
        OathkeeperProcedure.recipientDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Player(leaderB))))).toOption.get
    assert(resolved.events.exists {
      case WalkerStepRecorded(_, ChoicePayload(_, _, by), _, _) => by == holder
      case _ => false
    }, "the recorded choice must be answered by the holder")
    parkedAssertions.assertResumed(resolved.state, Phase.Act, active)
    val Ready(afterResolved) = resolved.state: @unchecked
    assertEquals(afterResolved.game.current.title,
      OathkeeperState(Some(leaderB), TitleSide.Oathkeeper))

    val reloadedResolved = service.load(gameId).toOption.flatten.get
    assertEquals(reloadedResolved.state, resolved.state)
    assertEquals(reloadedResolved.nextSequence, resolved.nextSequence)

  test("a walker Muster on an edifice persists, reloads and replays with its kind"):
    val siteId = CatalogNames.site("Deep Woods")
    val edificeId = CatalogNames.edifice("Hiding Place")
    val (service, repository) = Table.start.pawn(p1, siteId)
      .edifice("Hiding Place", EdificeSide.Ruined, siteId).service()
    val active = p1
    val started = service.handle("game-walker-economy", 0L,
      GameCommand.StartWalker(ActionRef.Muster, StartPayload(active))).toOption.get
    parkedAssertions.assertParked(started.state, ActionRef.Muster,
      MusterProcedure.decisionId, active)
    val mustered = service.handle("game-walker-economy", started.nextSequence,
      GameCommand.ResolveWalker(active, TreeDecision(MusterProcedure.decisionId,
        ChooseOneAnswer(DecisionOptionRef.Edifice(edificeId))))).toOption.get
    val loaded = replayingSecond(service, repository)
      .load("game-walker-economy").toOption.flatten.get
    assertEquals(loaded.state, mustered.state)
    val Ready(after) = loaded.state: @unchecked
    assertEquals(after.game.current.map.sites(siteId).denizens.collectFirst {
      case value: EdificeState if value.id == edificeId => value.tokens
    }, Some(Tokens(1, 0)))
    val records = repository.load("game-walker-economy").toOption.flatten.get.records
    assert(records.exists(record => record.contains("edifice") &&
      record.contains(edificeId.value)),
      "the journalled answer must spell the edifice kind and id")

  test("Search draw port cannot inject card identities inconsistent with state"):
    val port = new SearchDrawPort:
      def prepare(ready: ReadyGame, source: SearchSource, origin: Region): Either[OathViolation, Vector[WorldCardId]] =
        Right(Vector(DenizenId("denizen:tampered")))
    // Built by hand: `Table#service` takes no draw port.
    val repository = new InMemoryEventStreamRepository
    val table = Table.start
    val service = new GameApplicationService(catalog, repository, port,
      genesis = table.state)
    val active = p1
    assertEquals(service.handle("game-search-tamper", 0L,
      GameCommand.StartWalker(ActionRef.Search, StartPayload(active,
        Vector.empty, Vector(DecisionOptionRef.Button("search:world")))))
      .left.toOption, Some(GameApplicationError.CommandRejected(OathViolation.SearchDrawMismatch(
        "prepared draw does not match authoritative source order"))))
    assertEquals(service.load("game-search-tamper").toOption.flatten.get
      .nextSequence, 0L)
    assertEquals(repository.load("game-search-tamper"), Right(None))

  test("walker Search persists its card choice and completes after reload"):
    // Three denizens on top of the world deck, so the Search offers a choice.
    val (service, repository) = Table.start
      .worldDeckTop("Threatening Roar", "Fae Merchant", "Second Chance")
      .service()
    val gameId = "game-walker-search"
    val actor = p1
    val started = service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.Search, StartPayload(actor,
        Vector.empty, Vector(DecisionOptionRef.Button("search:world")))))
      .toOption.get
    val reloaded = replayingSecond(service, repository)
      .load(gameId).toOption.flatten.get
    assertEquals(reloaded.state, started.state)
    val Ready(afterDraw) = reloaded.state: @unchecked
    val drawn = afterDraw.game.current.temporaryHands(actor)
    val chosen = if drawn.size == 1 then started else
      def ref(card: WorldCardId): DecisionOptionRef = card match
        case id: DenizenId => DecisionOptionRef.Denizen(id)
        case id: VisionId => DecisionOptionRef.Vision(id)
      val assignments = Vector(DecisionPlacement(ref(drawn.head), "keep")) ++
        drawn.tail.map(card => DecisionPlacement(ref(card), "discard"))
      service.handle(gameId, reloaded.nextSequence,
        GameCommand.ResolveWalker(actor, TreeDecision("search.cards",
          DecisionAnswer.PartitionAnswer(assignments)))).toOption.get
    val completed = service.handle(gameId, chosen.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(
        s"cardplay.place.${drawn.head.kind}.${drawn.head.value}",
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("discard")))))
      .toOption.get
    assertEquals(replayingSecond(service, repository)
      .load(gameId).toOption.flatten.get.state, completed.state)
