package oathdigital.application

import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseOneAnswer, PartitionAnswer}
import oathdigital.serialization.GameEventWire
import oathdigital.gameplay.setup.SetupProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.p2
import oathdigital.testkit.Unwrap.{accepted, as, rejected}
import ParkedServiceFixture.{firstPlayer, loaded, playFacedownAdviser,
  replayingSecond, setupViaService}

/** The application service's stream: what it appends, at which position,
  * and what it refuses to append or replay.
  */
class GameApplicationServiceStreamSuite extends munit.FunSuite:
  test("all-Exile powered game persists and replays through round-eight victory"):
    // Stays on the replayed setup: the full game from Setup to round eight is
    // the end-to-end smoke test.
    val repository = new InMemoryEventStreamRepository
    val whenPlayedPower = DenizenId(
      catalog.denizenWithPower(PowerId("denizen.revelation")).get.id.value)
    def place(order: Vector[DenizenId], index: Int, id: DenizenId) =
      val current = order.indexWhere(_.value == id.value)
      if current < 0 then order.updated(index, id)
      else order.updated(index, id).updated(current, order(index))
    // Setup deals 6 site cards, then 3 advisers to each seat in seat order:
    // p2's hand starts here.
    val p2Index = 6 + orders.participants.indexWhere(_.playerId == p2) * 3
    val poweredWorldDeck = place(chronicle.worldDeck, p2Index, whenPlayedPower)
    val poweredChronicle = chronicle.copy(worldDeck = poweredWorldDeck)
    val poweredOrders = ChronicleFirstGamePlan.dealOrder(poweredChronicle,
      FirstGameBootstrapConfig(orders.participants, orders.firstPlayer))
    var service = new GameApplicationService(catalog, repository,
      warExhaustionRandomPort = new oathdigital.gameplay.phases.rest.WarExhaustionRandomPort {
        def choose(candidates: Vector[PlayerId]) = candidates.head
      })
    var position = setupViaService(service, "powered-playability", sites,
      poweredChronicle, poweredOrders)
    var played = Set.empty[PlayerId]
    var safety = 0
    while {
      val ready = Outcomes.ready(position.state)
      ready.game.current.result.isEmpty
    } do
      safety += 1
      // 8 rounds of 3 players: at most 24 turns.
      assert(safety <= 24, "all-Exile game should finish after eight rounds")
      val wake = Outcomes.ready(position.state)
      val actor = wake.game.current.turn.activePlayer
      position = ParkedServiceFixture.endingWake(service, "powered-playability",
        position, actor)
      if !played(actor) then
        val act = Outcomes.ready(position.state)
        // Each seat plays whichever adviser it kept in Setup.
        val adviser =
          as[WorldCardId](Look(act).player(actor).advisers.head.id)
        position = accepted(service.handle("powered-playability",
          position.nextSequence, playFacedownAdviser(actor, adviser)))
        position = accepted(service.handle("powered-playability",
          position.nextSequence, GameCommand.ResolveWalker(actor, TreeDecision(
            s"cardplay.place.${adviser.kind}.${adviser.value}",
            ChooseOneAnswer(DecisionOptionRef.Button("adviser-faceup"))))))
        played += actor
      position = accepted(service.handle("powered-playability",
        position.nextSequence, GameCommand.BeginRest(actor)))
      service = new GameApplicationService(catalog, repository,
        warExhaustionRandomPort = new oathdigital.gameplay.phases.rest.WarExhaustionRandomPort {
          def choose(candidates: Vector[PlayerId]) = candidates.head
        })
      val reopened = loaded(service.load("powered-playability"))
      assertEquals(reopened.state, position.state)
      assertEquals(reopened.nextSequence, position.nextSequence)
    val finished = Outcomes.ready(position.state)
    assert(finished.game.current.result.nonEmpty)
    val projector = new GameProjector(catalog)
    finished.game.current.players.foreach { player =>
      val view = projector.project("powered-playability",
        LoadedGame(position.state, position.nextSequence),
        player.player)
      assertEquals(view.phase, "game-over")
      assertEquals(view.legalControls, Vector.empty)
      assert(!view.actionSelectionOpen)
      assertEquals(view.oathkeeper.flatMap(_.winnerPlayerId),
        finished.game.current.result.map(_.winner.value))
      assert(view.world.flatMap(_.sites).nonEmpty)
    }
    val raw = loaded(repository.load("powered-playability")).records
    assert(raw.exists(_.contains("diagnostic.ignored-rules-recorded")))
    assert(raw.exists(_.contains("reviewed-unimplemented-pre-alpha-fallback")))

  test("create advance and reload replay the complete persisted stream"):
    // Stays on the replayed setup: it checks the length of Setup's journal.
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val setUp = setupViaService(service, "game-v2")
    val reloaded = loaded(new GameApplicationService(catalog, repository)
      .load("game-v2"))

    assertEquals(reloaded.state, setUp.state)
    // Setup now runs as an ordinary triggered walker procedure (2026-09-21
    // Chronicle design, slice 2): `GameStarted` plus a `WalkerParked`/
    // `WalkerStepRecorded`/`WalkerCompleted` fact per player decision and
    // delta, not one event per legacy setup command. The first Wake has
    // nothing to decide, so its End Wake step and completion follow: 1
    // GameStarted, 9 parks (3 decisions per seat), 17 steps and 2
    // completions (Setup's and the first Wake's) make 29.
    assertEquals(reloaded.nextSequence, 29L)
    assert(reloaded.state.isInstanceOf[Ready])
    val records = loaded(repository.load("game-v2")).records
    assertEquals(records.size, 29)
    val eventType = (record: String) => ujson.read(record)("eventType").str
    assertEquals(records.groupMapReduce(eventType)(_ => 1)(_ + _),
      Map("setup.game-started" -> 1, "walker.parked" -> 9,
        "walker.step-recorded" -> 17, "walker.completed" -> 2))
    assert(records.forall(record =>
      ujson.read(record)("formatVersion").num.toInt ==
        GameEventWire.FormatVersion))

  test("gameplay appends at the absolute position and reloads equally"):
    // p2 wakes at Broken Peaks, whose 2 secrets are its only Wake option.
    val (service, repository) = Table.start.turn(p2, Phase.Wake)
      .siteTokens("Broken Peaks", secrets = 2)
      // Bandits at every empty site keep the refill out of the five events.
      .banditsRefilled.service()
    val active = p2
    val siteId = CatalogNames.site("Broken Peaks")

    val wealth = accepted(service.handle("game-wake", 0L,
      GameCommand.StartWalker(ActionRef.TakeWealth, StartPayload(active,
        Vector.empty, Vector(DecisionOptionRef.Button("secret"))))))
    // Take Wealth is one atomic walker command (batch-1 Task 7): the resource
    // move, the use record and the completion. It was Wake's only option, so
    // Wake ends in the same command: its phase-change step and completion
    // follow, and the stream holds five events.
    assertEquals(wealth.nextSequence, 5L)
    assertEquals(
      service.handle("game-wake", 0L, GameCommand.EndWake(active)),
      Left(GameApplicationError.StaleClientPosition(0L, 5L)))
    val reloaded = loaded(replayingSecond(service, repository)
      .load("game-wake"))
    val after = Outcomes.ready(reloaded.state)

    assertEquals(reloaded.state, wealth.state)
    assertEquals(after.game.current.turn.phase, Phase.Act)
    assert(after.game.current.turn.usedPowers.contains(
      oathdigital.gameplay.powers.wake.TakeWealthLimit.useRef(siteId)),
      "the replayed journal must restore the use limit it recorded")
    val records = loaded(repository.load("game-wake")).records
    assertEquals(records.map(record =>
      ujson.read(record)("formatVersion").num.toInt), Vector(1, 1, 1, 1, 1))
    // Ending Wake is a walker procedure too (batch-1 Task 7), so the Wake
    // phase journals nothing of its own: the last two records are its
    // phase-change step and its completion, not a `gameplay.wake-ended`.
    assertEquals(records.map(record =>
      ujson.read(record)("eventType").str),
      Vector("walker.step-recorded", "walker.step-recorded",
        "walker.completed", "walker.step-recorded", "walker.completed"))

  test("stale expected position rejects a command legal on current state"):
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    service.handle("game-stale-v2", 0L, GameCommand.Begin(chronicle, orders))
    service.handle(
      "game-stale-v2",
      1L,
      GameCommand.ResolveWalker(firstPlayer, TreeDecision(
        SetupProcedure.pawnDecisionId(firstPlayer),
        ChooseOneAnswer(DecisionOptionRef.Site(
          CatalogNames.site("Ancient City"))))))

    assertEquals(
      service.handle(
        "game-stale-v2",
        1L,
        GameCommand.ResolveWalker(firstPlayer, TreeDecision(
          SetupProcedure.adviserDecisionId(firstPlayer),
          // p2 keeps Wizard's Conclave from its dealt hand.
          PartitionAnswer(Vector(
            DecisionPlacement(DecisionOptionRef.Denizen(
              CatalogNames.denizen("Wizard's Conclave")),
              SetupProcedure.adviserKeepKey),
            DecisionPlacement(DecisionOptionRef.Denizen(
              CatalogNames.denizen("Errand Boy")),
              SetupProcedure.adviserDiscardKey),
            DecisionPlacement(DecisionOptionRef.Denizen(
              CatalogNames.denizen("Nature Worship")),
              SetupProcedure.adviserDiscardKey)))))
      ),
      Left(GameApplicationError.StaleClientPosition(1L, 2L)))
    assertEquals(
      loaded(repository.load("game-stale-v2")).nextSequence,
      2L)

  test("malformed v1 envelopes are rejected without reinterpretation"):
    val repository = new InMemoryEventStreamRepository
    repository.seed(
      "game-v1",
      Vector(ujson.write(ujson.Obj("formatVersion" -> 1))))
    val result = new GameApplicationService(catalog, repository)
      .load("game-v1")

    assert(rejected(result) match {
      case GameApplicationError.CodecFailure(_) => true
      case _ => false
    })

  test("replay violations report the exact index and append nothing"):
    val repository = new InMemoryEventStreamRepository
    val badDiagnostics = Vector(IgnoredRuleDiagnostic(
      RuleSourceRef.Adviser(firstPlayer, DenizenId("insomnia")),
      "denizen.insomnia", ActionKind.Rest, RuleTiming.Trigger,
      "corrupted for the test"))
    val records = Vector(
      accepted(GameEventWire.encodeEvent(
        "game-replay-corrupt",
        0L,
        OathEvent.GameStarted(chronicle, orders)
      )),
      accepted(GameEventWire.encodeEvent(
        "game-replay-corrupt",
        1L,
        OathEvent.IgnoredRulesRecorded(firstPlayer, ActionKind.Rest,
          badDiagnostics)))
    ).map(ujson.write(_))
    repository.seed("game-replay-corrupt", records)

    assertEquals(
      new GameApplicationService(catalog, repository).handle(
        "game-replay-corrupt",
        2L,
        GameCommand.EndWake(firstPlayer)
      ),
      Left(GameApplicationError.ReplayFailure(
        1L,
        oathdigital.model.OathViolation.InvalidEventOrder(
          "ignored-rule diagnostics do not match authoritative discovery"))))
    assertEquals(
      loaded(repository.load("game-replay-corrupt")).records,
      records)

  test("repository and envelope game identity mismatches append nothing"):
    def repositoryFor(
        storedGameId: String,
        envelopeGameId: String
    ): (EventStreamRepository, () => Int) =
      var appendCalls = 0
      val record = accepted(GameEventWire.encodeEvent(
        envelopeGameId,
        0L,
        OathEvent.GameStarted(chronicle, orders)))
      val repository = new EventStreamRepository:
        override def load(gameId: String): Either[RepositoryFailure, Option[StoredEventStream]] = Right(Some(StoredEventStream(
          storedGameId,
          Vector(ujson.write(record)))))
        override def append(
            gameId: String,
            expected: ExpectedStream,
            records: Vector[String]
        ): Either[RepositoryFailure, RepositoryAppendResult] =
          appendCalls += 1
          Right(RepositoryAppendResult.Appended(1L, records.size))
      repository -> (() => appendCalls)

    Vector(
      repositoryFor("game-b", "game-b"),
      repositoryFor("game-a", "game-b")
    ).foreach { case (repository, appendCalls) =>
      assertEquals(
        new GameApplicationService(catalog, repository).handle(
          "game-a",
          1L,
          GameCommand.EndWake(firstPlayer)
        ),
        Left(GameApplicationError.StreamIdentityMismatch(
          "game-a",
          "game-b")))
      assertEquals(appendCalls(), 0)
    }

  test("authoritative history cannot omit sequence zero"):
    val repository = new InMemoryEventStreamRepository
    val record = accepted(GameEventWire.encodeEvent(
      "game-missing-zero",
      1L,
      OathEvent.GameStarted(chronicle, orders)))
    repository.seed("game-missing-zero", Vector(ujson.write(record)))

    assertEquals(
      new GameApplicationService(catalog, repository)
        .load("game-missing-zero"),
      Left(GameApplicationError.CodecFailure(
        EventCodecFailure("invalid-sequence", "$[0].sequence",
          "expected 0 but found 1"))))
