package oathdigital.application

import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.serialization.GameEventWire
import oathdigital.gameplay.setup.SetupProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.Table
import oathdigital.testkit.Table.p2
import ParkedServiceFixture.{replayingSecond, setupViaService}

/** The application service's stream: what it appends, at which position,
  * and what it refuses to append or replay.
  */
class GameApplicationServiceStreamSuite extends munit.FunSuite:
  test("all-Exile powered game persists and replays through round-eight victory"):
    // Stays on the replayed setup: the full game from Setup to round eight is
    // the end-to-end smoke test.
    val repository = new InMemoryEventStreamRepository
    val whenPlayedPower = DenizenId(catalog.denizenWithPower(PowerId("denizen.revelation")).get.id.value)
    def place(order: Vector[DenizenId], index: Int, id: DenizenId) =
      val current = order.indexWhere(_.value == id.value)
      if current < 0 then order.updated(index, id)
      else order.updated(index, id).updated(current, order(index))
    val p2Index = 6 + orders.participants.indexWhere(_.playerId == PlayerId("p2")) * 3
    val poweredWorldDeck = place(chronicle.worldDeck, p2Index, whenPlayedPower)
    val poweredChronicle = chronicle.copy(worldDeck = poweredWorldDeck)
    val poweredOrders = ChronicleFirstGamePlan.dealOrder(poweredChronicle,
      FirstGameBootstrapConfig(orders.participants, orders.firstPlayer))
    var service = new GameApplicationService(catalog, repository,
      warExhaustionRandomPort = new oathdigital.gameplay.phases.rest.WarExhaustionRandomPort {
        def choose(candidates: Vector[PlayerId]) = candidates.head
      })
    var accepted = setupViaService(service, "powered-playability", sites,
      poweredChronicle, poweredOrders)
    var played = Set.empty[PlayerId]
    var safety = 0
    while {
      val Ready(ready) = accepted.state: @unchecked
      ready.game.current.result.isEmpty
    } do
      safety += 1
      assert(safety <= 24, "all-Exile game should finish after eight rounds")
      val Ready(wake) = accepted.state: @unchecked
      val actor = wake.game.current.turn.activePlayer
      accepted = ParkedServiceFixture.endingWake(service, "powered-playability", accepted, actor)
      if !played(actor) then
        val Ready(act) = accepted.state: @unchecked
        val adviser = act.game.current.players.find(_.player == actor).get.advisers.head.id
          .asInstanceOf[WorldCardId]
        accepted = service.handle("powered-playability", accepted.nextSequence,
          GameCommand.StartWalker(ActionRef.PlayFacedownAdviser,
            StartPayload(actor, Vector.empty, Vector(adviser match {
              case id: DenizenId => DecisionOptionRef.Denizen(id)
              case id: VisionId => DecisionOptionRef.Vision(id)
            })))).toOption.get
        accepted = service.handle("powered-playability", accepted.nextSequence,
          GameCommand.ResolveWalker(actor, TreeDecision(
            s"cardplay.place.${adviser.kind}.${adviser.value}",
            ChooseOneAnswer(DecisionOptionRef.Button("adviser-faceup")))))
          .toOption.get
        played += actor
      accepted = service.handle("powered-playability", accepted.nextSequence,
        GameCommand.BeginRest(actor)).toOption.get
      service = new GameApplicationService(catalog, repository,
        warExhaustionRandomPort = new oathdigital.gameplay.phases.rest.WarExhaustionRandomPort {
          def choose(candidates: Vector[PlayerId]) = candidates.head
        })
      val reopened = service.load("powered-playability").toOption.flatten.get
      assertEquals(reopened.state, accepted.state)
      assertEquals(reopened.nextSequence, accepted.nextSequence)
    val Ready(finished) = accepted.state: @unchecked
    assert(finished.game.current.result.nonEmpty)
    val projector = new GameProjector(catalog)
    finished.game.current.players.foreach { player =>
      val view = projector.project("powered-playability",
        LoadedGame(accepted.state, accepted.nextSequence),
        player.player)
      assertEquals(view.phase, "game-over")
      assertEquals(view.legalControls, Vector.empty)
      assert(!view.actionSelectionOpen)
      assertEquals(view.oathkeeper.flatMap(_.winnerPlayerId),
        finished.game.current.result.map(_.winner.value))
      assert(view.world.flatMap(_.sites).nonEmpty)
    }
    val raw = repository.load("powered-playability").toOption.flatten.get.records
    assert(raw.exists(_.contains("diagnostic.ignored-rules-recorded")))
    assert(raw.exists(_.contains("reviewed-unimplemented-pre-alpha-fallback")))

  test("create advance and reload replay the complete persisted stream"):
    // Stays on the replayed setup: it checks the length of Setup's journal.
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val accepted = setupViaService(service, "game-v2")
    val reloaded = new GameApplicationService(catalog, repository)
      .load("game-v2").toOption.flatten.get

    assertEquals(reloaded.state, accepted.state)
    // Setup now runs as an ordinary triggered walker procedure (2026-09-21
    // Chronicle design, slice 2): `GameStarted` plus a `WalkerParked`/
    // `WalkerStepRecorded`/`WalkerCompleted` fact per player decision and
    // delta, not one event per legacy setup command. The first Wake has
    // nothing to decide, so its End Wake step and completion follow.
    assertEquals(reloaded.nextSequence, 29L)
    assert(reloaded.state.isInstanceOf[Ready])
    val records = repository.load("game-v2").toOption.flatten.get.records
    assertEquals(records.size, 29)
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
    val siteId = Table.quietPawnSite(p2)

    val wealth = service.handle("game-wake", 0L,
      GameCommand.StartWalker(ActionRef.TakeWealth, StartPayload(active,
        Vector.empty, Vector(DecisionOptionRef.Button("secret"))))).toOption.get
    // Take Wealth is one atomic walker command (batch-1 Task 7): the resource
    // move, the use record and the completion. It was Wake's only option, so
    // Wake ends in the same command: its phase-change step and completion
    // follow, and the stream holds five events.
    assertEquals(wealth.nextSequence, 5L)
    assertEquals(
      service.handle("game-wake", 0L, GameCommand.EndWake(active)),
      Left(GameApplicationError.StaleClientPosition(0L, 5L))
    )
    val reloaded = replayingSecond(service, repository)
      .load("game-wake").toOption.flatten.get
    val Ready(after) = reloaded.state: @unchecked

    assertEquals(reloaded.state, wealth.state)
    assertEquals(after.game.current.turn.phase, Phase.Act)
    assert(after.game.current.turn.usedPowers.contains(
      oathdigital.gameplay.powers.wake.TakeWealthLimit.useRef(siteId)),
      "the replayed journal must restore the use limit it recorded")
    val records = repository.load("game-wake").toOption.flatten.get.records
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
      GameCommand.ResolveWalker(PlayerId("p2"), TreeDecision(
        SetupProcedure.pawnDecisionId(PlayerId("p2")),
        ChooseOneAnswer(DecisionOptionRef.Site(sites.head))))
    )

    assertEquals(
      service.handle(
        "game-stale-v2",
        1L,
        GameCommand.ResolveWalker(PlayerId("p2"), TreeDecision(
          SetupProcedure.adviserDecisionId(PlayerId("p2")),
          ChooseOneAnswer(DecisionOptionRef.Denizen(DenizenId("92")))))
      ),
      Left(GameApplicationError.StaleClientPosition(1L, 2L))
    )
    assertEquals(
      repository.load("game-stale-v2").toOption.flatten.get.nextSequence,
      2L
    )

  test("malformed v1 envelopes are rejected without reinterpretation"):
    val repository = new InMemoryEventStreamRepository
    repository.seed(
      "game-v1",
      Vector(ujson.write(ujson.Obj("formatVersion" -> 1)))
    )
    val result = new GameApplicationService(catalog, repository)
      .load("game-v1")

    assert(result.left.toOption.get match {
      case GameApplicationError.CodecFailure(_) => true
      case _ => false
    })

  test("replay violations report the exact index and append nothing"):
    val repository = new InMemoryEventStreamRepository
    val badDiagnostics = Vector(IgnoredRuleDiagnostic(
      RuleSourceRef.Adviser(PlayerId("p2"), DenizenId("insomnia")),
      "denizen.insomnia", ActionKind.Rest, RuleTiming.Trigger,
      "corrupted for the test"))
    val records = Vector(
      GameEventWire.encodeEvent(
        "game-replay-corrupt",
        0L,
        OathEvent.GameStarted(chronicle, orders)
      ).toOption.get,
      GameEventWire.encodeEvent(
        "game-replay-corrupt",
        1L,
        OathEvent.IgnoredRulesRecorded(PlayerId("p2"), ActionKind.Rest,
          badDiagnostics)
      ).toOption.get
    ).map(ujson.write(_))
    repository.seed("game-replay-corrupt", records)

    assertEquals(
      new GameApplicationService(catalog, repository).handle(
        "game-replay-corrupt",
        2L,
        GameCommand.EndWake(PlayerId("p2"))
      ),
      Left(GameApplicationError.ReplayFailure(
        1L,
        oathdigital.model.OathViolation.InvalidEventOrder(
          "ignored-rule diagnostics do not match authoritative discovery")
      ))
    )
    assertEquals(
      repository.load("game-replay-corrupt").toOption.flatten.get.records,
      records
    )

  test("repository and envelope game identity mismatches append nothing"):
    def repositoryFor(
        storedGameId: String,
        envelopeGameId: String
    ): (EventStreamRepository, () => Int) =
      var appendCalls = 0
      val record = GameEventWire.encodeEvent(
        envelopeGameId,
        0L,
        OathEvent.GameStarted(chronicle, orders)
      ).toOption.get
      val repository = new EventStreamRepository:
        override def load(gameId: String): Either[RepositoryFailure, Option[StoredEventStream]] = Right(Some(StoredEventStream(
          storedGameId,
          Vector(ujson.write(record))
        )))
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
          GameCommand.EndWake(PlayerId("p2"))
        ),
        Left(GameApplicationError.StreamIdentityMismatch(
          "game-a",
          "game-b"
        ))
      )
      assertEquals(appendCalls(), 0)
    }

  test("authoritative history cannot omit sequence zero"):
    val repository = new InMemoryEventStreamRepository
    val record = GameEventWire.encodeEvent(
      "game-missing-zero",
      1L,
      OathEvent.GameStarted(chronicle, orders)
    ).toOption.get
    repository.seed("game-missing-zero", Vector(ujson.write(record)))

    assertEquals(
      new GameApplicationService(catalog, repository)
        .load("game-missing-zero"),
      Left(GameApplicationError.CodecFailure(
        EventCodecFailure("invalid-sequence", "$[0].sequence",
          "expected 0 but found 1")
      ))
    )
