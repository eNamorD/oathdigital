package oathdigital.application

import java.nio.file.Files

import oathdigital.model.PlayerId
import oathdigital.persistence.HsqldbEventStreamRepository
import oathdigital.serialization.FirstGameEventWire
import oathdigital.serialization.WireError.UnsupportedFormatVersion
import oathdigital.setup.FirstGameSetupFixture._
import oathdigital.setup.FirstGameSetupState.Ready

class FirstGameApplicationServiceSuite extends munit.FunSuite {
  private def execute(
      service: FirstGameApplicationService,
      gameId: String
  ): FirstGameAccepted = {
    var accepted =
      service.handle(gameId, 0L, FirstGameCommand.Begin(plan))
        .toOption.get
    val order = Vector(PlayerId("p2"), PlayerId("p3"), PlayerId("p1"))
    order.zipWithIndex.foreach { case (playerId, index) =>
      accepted = service.handle(
        gameId,
        accepted.nextSequence,
        FirstGameCommand.PlacePawn(playerId, sites(index))
      ).toOption.get
      val participantIndex =
        plan.participants.indexWhere(_.playerId == playerId)
      accepted = service.handle(
        gameId,
        accepted.nextSequence,
        FirstGameCommand.ChooseAdviser(
          playerId,
          plan.denizenOrder(6 + participantIndex * 3)
        )
      ).toOption.get
    }
    accepted
  }

  test("create advance and reload replay the complete persisted v2 stream") {
    val repository = new InMemoryEventStreamRepository
    val service = new FirstGameApplicationService(catalog, repository)
    val accepted = execute(service, "game-v2")
    val reloaded = new FirstGameApplicationService(catalog, repository)
      .load("game-v2").toOption.flatten.get

    assertEquals(reloaded.state, accepted.state)
    assertEquals(reloaded.nextSequence, 8L)
    assert(reloaded.state.isInstanceOf[Ready])
    val records = repository.load("game-v2").toOption.flatten.get.records
    assertEquals(records.size, 8)
    assert(records.forall(record =>
      ujson.read(record)("formatVersion").num.toInt ==
        FirstGameEventWire.FormatVersion))
  }

  test("stale expected position rejects a command legal on current state") {
    val repository = new InMemoryEventStreamRepository
    val service = new FirstGameApplicationService(catalog, repository)
    service.handle("game-stale-v2", 0L, FirstGameCommand.Begin(plan))
    service.handle(
      "game-stale-v2",
      1L,
      FirstGameCommand.PlacePawn(PlayerId("p2"), sites.head)
    )
    val adviser = plan.denizenOrder(6 + 1 * 3)

    assertEquals(
      service.handle(
        "game-stale-v2",
        1L,
        FirstGameCommand.ChooseAdviser(PlayerId("p2"), adviser)
      ),
      Left(FirstGameApplicationError.StaleClientPosition(1L, 2L))
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
    val result = new FirstGameApplicationService(catalog, repository)
      .load("game-v1")

    assert(result.left.toOption.get match {
      case FirstGameApplicationError.CodecFailure(
            _: UnsupportedFormatVersion
          ) => true
      case _ => false
    })
  }

  test("player projection redacts other adviser hands and hidden orders") {
    val repository = new InMemoryEventStreamRepository
    val service = new FirstGameApplicationService(catalog, repository)
    service.handle("game-private", 0L, FirstGameCommand.Begin(plan))
    val placed = service.handle(
      "game-private",
      1L,
      FirstGameCommand.PlacePawn(PlayerId("p2"), sites.head)
    ).toOption.get
    val projector = new FirstGameProjector(catalog)
    val loaded = LoadedFirstGame(placed.state, placed.nextSequence)
    val own = projector.project("game-private", loaded, PlayerId("p2"))
    val other = projector.project("game-private", loaded, PlayerId("p1"))
    val privateIds = own.privateAdviserChoices.map(_.adviserId)
    val otherJson = oathdigital.server.FirstGameHttpWire
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
      HsqldbEventStreamRepository.open(path).toOption.get
    val accepted =
      try execute(
        new FirstGameApplicationService(catalog, firstRepository),
        "game-hsql-v2"
      )
      finally firstRepository.close()

    val reopened = HsqldbEventStreamRepository.open(path).toOption.get
    try {
      val loaded = new FirstGameApplicationService(catalog, reopened)
        .load("game-hsql-v2").toOption.flatten.get
      assertEquals(loaded.state, accepted.state)
      assertEquals(loaded.nextSequence, 8L)
    } finally reopened.close()
  }
}
