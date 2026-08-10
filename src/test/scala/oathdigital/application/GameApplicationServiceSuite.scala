package oathdigital.application

import java.nio.file.Files

import oathdigital.model._
import oathdigital.persistence.OwnedHsqldbEventStreamRepository
import oathdigital.serialization.GameEventWire
import oathdigital.serialization.WireError.UnsupportedFormatVersion
import oathdigital.setup.FirstGameSetupFixture._
import oathdigital.setup.FirstGameSetupEvent.{
  GamePawnPlaced,
  FirstGameStarted
}
import oathdigital.setup.FirstGameSetupViolation.{CatalogMismatch, WrongPlayer}
import oathdigital.setup.FirstGameSetupState.Ready
import oathdigital.setup.WakeResource
import oathdigital.setup.ReadyFirstGame

class GameApplicationServiceSuite extends munit.FunSuite {
  private def execute(
      service: GameApplicationService,
      gameId: String,
      placementSites: Vector[oathdigital.model.SiteId] = sites
  ): GameAccepted = {
    var accepted =
      service.handle(gameId, 0L, GameCommand.Begin(plan))
        .toOption.get
    val order = Vector(PlayerId("p2"), PlayerId("p3"), PlayerId("p1"))
    order.zipWithIndex.foreach { case (playerId, index) =>
      accepted = service.handle(
        gameId,
        accepted.nextSequence,
        GameCommand.PlacePawn(playerId, placementSites(index))
      ).toOption.get
      val participantIndex =
        plan.participants.indexWhere(_.playerId == playerId)
      accepted = service.handle(
        gameId,
        accepted.nextSequence,
        GameCommand.ChooseAdviser(
          playerId,
          plan.denizenOrder(6 + participantIndex * 3)
        )
      ).toOption.get
    }
    accepted
  }

  test("create advance and reload replay the complete persisted v2 stream") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val accepted = execute(service, "game-v2")
    val reloaded = new GameApplicationService(catalog, repository)
      .load("game-v2").toOption.flatten.get

    assertEquals(reloaded.state, accepted.state)
    assertEquals(reloaded.nextSequence, 8L)
    assert(reloaded.state.isInstanceOf[Ready])
    val records = repository.load("game-v2").toOption.flatten.get.records
    assertEquals(records.size, 8)
    assert(records.forall(record =>
      ujson.read(record)("formatVersion").num.toInt ==
        GameEventWire.FormatVersion))
  }

  test("gameplay appends v3 at the absolute position and reloads equally") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val wealthSite = catalog.sites.find(site =>
      sites.contains(site.id) && !site.startingResources.isEmpty).get.id
    val otherSites = sites.filterNot(_ == wealthSite).take(2)
    val setup = execute(
      service,
      "game-wake",
      wealthSite +: otherSites
    )
    val Ready(ready) = setup.state: @unchecked
    val active = ready.game.current.turn.activePlayer
    val siteId = ready.game.current.players.find(_.player == active)
      .flatMap(_.pawnSite).get
    val site = ready.game.current.map.sites(siteId)
    val resource =
      if (site.tokens.favor > 0) WakeResource.Favor else WakeResource.Secret

    val wealth = service.handle(
      "game-wake",
      setup.nextSequence,
      GameCommand.TakeWealth(active, resource)
    ).toOption.get
    assertEquals(wealth.nextSequence, 9L)
    assertEquals(
      service.handle("game-wake", 8L, GameCommand.EndWake(active)),
      Left(GameApplicationError.StaleClientPosition(8L, 9L))
    )
    val ended = service.handle(
      "game-wake",
      wealth.nextSequence,
      GameCommand.EndWake(active)
    ).toOption.get
    val reloaded = new GameApplicationService(catalog, repository)
      .load("game-wake").toOption.flatten.get
    val Ready(after) = reloaded.state: @unchecked

    assertEquals(reloaded.state, ended.state)
    assertEquals(after.game.current.turn.phase, Phase.Act)
    val records = repository.load("game-wake").toOption.flatten.get.records
    assertEquals(records.take(8).map(record =>
      ujson.read(record)("formatVersion").num.toInt).distinct, Vector(2))
    assertEquals(records.drop(8).map(record =>
      ujson.read(record)("formatVersion").num.toInt), Vector(3, 3))
    assertEquals(records.drop(8).map(record =>
      ujson.read(record)("eventType").str),
      Vector("gameplay.take-wealth", "gameplay.wake-ended"))
  }

  test("Travel appends one v3 event and reloads pawn Supply and Act") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val setup = execute(service, "game-travel")
    val Ready(ready) = setup.state: @unchecked
    val active = ready.game.current.turn.activePlayer
    val ended = service.handle("game-travel", setup.nextSequence,
      GameCommand.EndWake(active)).toOption.get
    val Ready(inAct) = ended.state: @unchecked
    val before = inAct.game.current.players.find(_.player == active).get
    val destination = inAct.game.current.map.cradle.find(
      !before.pawnSite.contains(_)).getOrElse(
        inAct.game.current.map.provinces.head)
    val traveled = service.handle("game-travel", ended.nextSequence,
      GameCommand.Travel(active, destination)).toOption.get
    val loaded = new GameApplicationService(catalog, repository)
      .load("game-travel").toOption.flatten.get
    val Ready(after) = loaded.state: @unchecked
    val moved = after.game.current.players.find(_.player == active).get

    assertEquals(loaded.state, traveled.state)
    assertEquals(loaded.nextSequence, ended.nextSequence + 1)
    assertEquals(moved.pawnSite, Some(destination))
    assert(moved.board.supply.supply < before.board.supply.supply)
    assertEquals(after.game.current.turn.phase, Phase.Act)
    val last = ujson.read(repository.load("game-travel").toOption.flatten.get
      .records.last)
    assertEquals(last("formatVersion").num.toInt, 3)
    assertEquals(last("eventType").str, "gameplay.traveled")
  }

  test("Search persists and reloads pending private decision then completes in v4") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val setup = execute(service, "game-search")
    val Ready(ready) = setup.state: @unchecked
    val active = ready.game.current.turn.activePlayer
    val ended = service.handle("game-search", setup.nextSequence,
      GameCommand.EndWake(active)).toOption.get
    val started = service.handle("game-search", ended.nextSequence,
      GameCommand.BeginSearch(active, SearchSource.WorldDeck)).toOption.get
    val reloadedPending = new GameApplicationService(catalog, repository)
      .load("game-search").toOption.flatten.get
    assertEquals(reloadedPending.state, started.state)
    val Ready(pendingReady) = reloadedPending.state: @unchecked
    val pending = pendingReady.game.current.pending.get
      .asInstanceOf[PendingProcedure.Search]
    assertEquals(service.handle("game-search", ended.nextSequence,
      GameCommand.BeginSearch(active, SearchSource.WorldDeck)),
      Left(GameApplicationError.StaleClientPosition(
        ended.nextSequence, started.nextSequence)))
    val completed = service.handle("game-search", started.nextSequence,
      GameCommand.CompleteSearch(active, pending.decision,
        pending.drawn.head, pending.drawn.tail, SearchPlacement.Discard))
      .toOption.get
    val loaded = new GameApplicationService(catalog, repository)
      .load("game-search").toOption.flatten.get
    assertEquals(loaded.state, completed.state)
    val versions = repository.load("game-search").toOption.flatten.get.records
      .takeRight(2).map(record => ujson.read(record)("formatVersion").num.toInt)
    assertEquals(versions, Vector(4, 4))
  }

  test("Search draw port cannot inject card identities inconsistent with state") {
    val repository = new InMemoryEventStreamRepository
    val port = new SearchDrawPort {
      def prepare(ready: ReadyFirstGame, source: SearchSource, origin: Region) =
        Right(Vector(DenizenId("denizen:tampered")))
    }
    val service = new GameApplicationService(catalog, repository, port)
    val setup = execute(service, "game-search-tamper")
    val Ready(ready) = setup.state: @unchecked
    val active = ready.game.current.turn.activePlayer
    val ended = service.handle("game-search-tamper", setup.nextSequence,
      GameCommand.EndWake(active)).toOption.get
    assert(service.handle("game-search-tamper", ended.nextSequence,
      GameCommand.BeginSearch(active, SearchSource.WorldDeck))
      .left.toOption.get.isInstanceOf[GameApplicationError.CommandRejected])
    assertEquals(repository.load("game-search-tamper").toOption.flatten.get
      .nextSequence, ended.nextSequence)
  }

  test("Wake projection is actor-private and Act boundary is informational") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val setup = execute(service, "game-projection-wake")
    val Ready(ready) = setup.state: @unchecked
    val active = ready.game.current.turn.activePlayer
    val projector = new GameProjector(catalog)
    val loaded = LoadedGame(setup.state, setup.nextSequence)

    val own = projector.project("game-projection-wake", loaded, active)
    val public = projector.projectPublic("game-projection-wake", loaded)
    assertEquals(own.phase, "wake")
    assert(own.legalControls.contains("endWake"))
    assertEquals(public.legalControls, Vector.empty)
    assert(own.activePlayerResources.nonEmpty)
    assert(own.currentSiteResources.nonEmpty)

    val ended = service.handle(
      "game-projection-wake",
      setup.nextSequence,
      GameCommand.EndWake(active)
    ).toOption.get
    val act = projector.project(
      "game-projection-wake",
      LoadedGame(ended.state, ended.nextSequence),
      active
    )
    assertEquals(act.phase, "act-action-selection")
    assert(act.actionSelectionOpen)
    assertEquals(act.legalControls, Vector.empty)
    assertEquals(act.actionFamilies.size, 8)
  }

  test("site projection exposes ordered public properties without relic identity") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val setup = execute(service, "game-site-details")
    val Ready(ready) = setup.state: @unchecked
    val siteId = ready.game.current.map.cradle.head
    val emptySiteId = ready.game.current.map.cradle(1)
    val definition = catalog.sites.find(_.id == siteId).get
    val denizenDefinitions = catalog.denizens.take(2)
    val relicDefinitions = catalog.relics.take(2)
    val populated = ready.game.current.map.sites(siteId).copy(
      denizens = denizenDefinitions.reverse.map(definition =>
        DenizenState(
          DenizenId(definition.id.value),
          Orientation.FaceUp,
          Tokens.empty
        )),
      relics = relicDefinitions.map(definition =>
        RelicState(
          RelicId(definition.id.value),
          Orientation.FaceDown,
          Tokens.empty
        )),
      tokens = Tokens(2, 1)
    )
    val current = ready.game.current.copy(
      map = ready.game.current.map.copy(
        sites = ready.game.current.map.sites
          .updated(siteId, populated)
          .updated(
            emptySiteId,
            ready.game.current.map.sites(emptySiteId).copy(
              denizens = Vector.empty,
              relics = Vector.empty,
              tokens = Tokens.empty
            )
          )
      )
    )
    val loaded = LoadedGame(
      Ready(ready.copy(game = ready.game.copy(current = current))),
      setup.nextSequence
    )
    val projector = new GameProjector(catalog)
    val own = projector.project("game-site-details", loaded,
      current.turn.activePlayer)
    val public = projector.projectPublic("game-site-details", loaded)
    val site = own.world.flatMap(_.sites).find(_.siteId == siteId.value).get
    val empty = own.world.flatMap(_.sites)
      .find(_.siteId == emptySiteId.value).get

    assertEquals(site.looseFavor, 2)
    assertEquals(site.looseSecrets, 1)
    assertEquals(site.denizenCapacity, definition.capacity)
    assertEquals(site.relicCapacity, definition.relicSlots)
    assertEquals(
      site.denizens.map(card => card.cardId -> card.label),
      denizenDefinitions.reverse.map(definition =>
        definition.id.value -> definition.name)
    )
    assertEquals(site.relics.facedownCount, 2)
    assertEquals(empty.denizens, Vector.empty)
    assertEquals(empty.relics.facedownCount, 0)
    assertEquals(public.world, own.world)

    val json = oathdigital.server.GameHttpWire.encodeProjection(public)
    assert(json.contains("\"looseFavor\":2"))
    assert(json.contains("\"facedownCount\":2"))
    relicDefinitions.foreach(relic => assert(!json.contains(relic.id.value)))
  }

  test("stale expected position rejects a command legal on current state") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    service.handle("game-stale-v2", 0L, GameCommand.Begin(plan))
    service.handle(
      "game-stale-v2",
      1L,
      GameCommand.PlacePawn(PlayerId("p2"), sites.head)
    )
    val adviser = plan.denizenOrder(6 + 1 * 3)

    assertEquals(
      service.handle(
        "game-stale-v2",
        1L,
        GameCommand.ChooseAdviser(PlayerId("p2"), adviser)
      ),
      Left(GameApplicationError.StaleClientPosition(1L, 2L))
    )
    assertEquals(
      repository.load("game-stale-v2").toOption.flatten.get.nextSequence,
      2L
    )
  }

  test("v1 envelopes are not reinterpreted as v2 setup history") {
    val repository = new InMemoryEventStreamRepository
    repository.seed(
      "game-v1",
      Vector(ujson.write(ujson.Obj("formatVersion" -> 1)))
    )
    val result = new GameApplicationService(catalog, repository)
      .load("game-v1")

    assert(result.left.toOption.get match {
      case GameApplicationError.CodecFailure(
            _: UnsupportedFormatVersion
          ) => true
      case _ => false
    })
  }

  test("pre2 history is rejected by pre3 rules at its exact replay index") {
    val repository = new InMemoryEventStreamRepository
    val historical = CatalogRef(catalogRef.ruleset, "2026.07.27-pre2")
    val record = GameEventWire.encodeEvent(
      "game-pre2",
      historical,
      0L,
      FirstGameStarted(plan.copy(catalog = historical))
    ).toOption.get
    repository.seed("game-pre2", Vector(ujson.write(record)))

    assertEquals(
      new GameApplicationService(catalog, repository).load("game-pre2"),
      Left(GameApplicationError.ReplayFailure(
        0L,
        CatalogMismatch(catalogRef, historical)
      ))
    )
  }

  test("v2 replay violations report the exact index and append nothing") {
    val repository = new InMemoryEventStreamRepository
    val records = Vector(
      GameEventWire.encodeEvent(
        "game-replay-corrupt",
        catalogRef,
        0L,
        FirstGameStarted(plan)
      ).toOption.get,
      GameEventWire.encodeEvent(
        "game-replay-corrupt",
        catalogRef,
        1L,
        GamePawnPlaced(PlayerId("p1"), sites.head)
      ).toOption.get
    ).map(ujson.write(_))
    repository.seed("game-replay-corrupt", records)

    assertEquals(
      new GameApplicationService(catalog, repository).handle(
        "game-replay-corrupt",
        2L,
        GameCommand.PlacePawn(PlayerId("p2"), sites.head)
      ),
      Left(GameApplicationError.ReplayFailure(
        1L,
        WrongPlayer(PlayerId("p2"), PlayerId("p1"))
      ))
    )
    assertEquals(
      repository.load("game-replay-corrupt").toOption.flatten.get.records,
      records
    )
  }

  test("repository and envelope game identity mismatches append nothing") {
    def repositoryFor(
        storedGameId: String,
        envelopeGameId: String
    ): (EventStreamRepository, () => Int) = {
      var appendCalls = 0
      val record = GameEventWire.encodeEvent(
        envelopeGameId,
        catalogRef,
        0L,
        FirstGameStarted(plan)
      ).toOption.get
      val repository = new EventStreamRepository {
        override def load(gameId: String) = Right(Some(StoredEventStream(
          storedGameId,
          Vector(ujson.write(record))
        )))
        override def append(
            gameId: String,
            expected: ExpectedStream,
            records: Vector[String]
        ) = {
          appendCalls += 1
          Right(RepositoryAppendResult.Appended(1L, records.size))
        }
      }
      repository -> (() => appendCalls)
    }

    Vector(
      repositoryFor("game-b", "game-b"),
      repositoryFor("game-a", "game-b")
    ).foreach { case (repository, appendCalls) =>
      assertEquals(
        new GameApplicationService(catalog, repository).handle(
          "game-a",
          1L,
          GameCommand.PlacePawn(PlayerId("p2"), sites.head)
        ),
        Left(GameApplicationError.StreamIdentityMismatch(
          "game-a",
          "game-b"
        ))
      )
      assertEquals(appendCalls(), 0)
    }
  }

  test("authoritative v2 history cannot omit sequence zero") {
    val repository = new InMemoryEventStreamRepository
    val record = GameEventWire.encodeEvent(
      "game-missing-zero",
      catalogRef,
      1L,
      FirstGameStarted(plan)
    ).toOption.get
    repository.seed("game-missing-zero", Vector(ujson.write(record)))

    assertEquals(
      new GameApplicationService(catalog, repository)
        .load("game-missing-zero"),
      Left(GameApplicationError.CodecFailure(
        oathdigital.serialization.WireError.InvalidSequence(
          "$[0].sequence",
          0L,
          1L
        )
      ))
    )
  }

  test("player projection redacts other adviser hands and hidden orders") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    service.handle("game-private", 0L, GameCommand.Begin(plan))
    val placed = service.handle(
      "game-private",
      1L,
      GameCommand.PlacePawn(PlayerId("p2"), sites.head)
    ).toOption.get
    val projector = new GameProjector(catalog)
    val loaded = LoadedGame(placed.state, placed.nextSequence)
    val own = projector.project("game-private", loaded, PlayerId("p2"))
    val other = projector.project("game-private", loaded, PlayerId("p1"))
    val privateIds = own.privateAdviserChoices.map(_.adviserId)
    val otherJson = oathdigital.server.GameHttpWire
      .encodeProjection(other)

    assertEquals(privateIds.size, 3)
    assertEquals(other.privateAdviserChoices, Vector.empty)
    privateIds.foreach(id => assert(!otherJson.contains(id)))
    val exposedValues = jsonStrings(ujson.read(otherJson))
    assertEquals(
      exposedValues.intersect(plan.relicOrder.map(_.value).toSet),
      Set.empty[String]
    )
    assertEquals(
      exposedValues.intersect(plan.worldDeckOrder.map(_.value).toSet),
      Set.empty[String]
    )
  }

  private def jsonStrings(value: ujson.Value): Set[String] =
    value match {
      case ujson.Str(text) => Set(text)
      case obj: ujson.Obj =>
        obj.value.valuesIterator.flatMap(jsonStrings).toSet
      case array: ujson.Arr =>
        array.value.iterator.flatMap(jsonStrings).toSet
      case _ => Set.empty
    }

  test("HSQL close and reopen preserves v2 replay equality") {
    val path =
      Files.createTempDirectory("oathdigital-v2-reopen-").resolve("journal")
    val firstRepository =
      OwnedHsqldbEventStreamRepository.open(path).toOption.get
    val accepted =
      try execute(
        new GameApplicationService(catalog, firstRepository),
        "game-hsql-v2"
      )
      finally firstRepository.close()

    val reopened = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    try {
      val loaded = new GameApplicationService(catalog, reopened)
        .load("game-hsql-v2").toOption.flatten.get
      assertEquals(loaded.state, accepted.state)
      assertEquals(loaded.nextSequence, 8L)
    } finally reopened.close()
  }
}
