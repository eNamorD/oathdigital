package oathdigital.server

import oathdigital.application.{
  InMemoryEventStreamRepository,
  SetupApplicationError,
  SetupApplicationService
}
import oathdigital.catalog.{ExecutableCatalog, SiteDefinition}
import oathdigital.model.{
  CatalogRef,
  LineageId,
  PlayerId,
  SiteId,
  Tokens
}
import oathdigital.setup.SetupCommand.{BeginSetup, PlacePawn}
import oathdigital.setup.SetupParticipant

class ServerCommandGatewaySuite extends munit.FunSuite {
  private val catalogRef = CatalogRef("test", "1")
  private val sites = Vector.tabulate(8)(index => SiteId(s"site:$index"))
  private val catalog = ExecutableCatalog(
    schemaVersion = "test",
    ref = catalogRef,
    denizens = Vector.empty,
    relics = Vector.empty,
    edifices = Vector.empty,
    legacies = Vector.empty,
    sites = sites.map(site =>
      SiteDefinition(
        id = site,
        name = site.value,
        defense = 1,
        capacity = 1,
        relicSlots = 0,
        recoverDifficulty = None,
        startingResources = Tokens.empty,
        forgeRequirements = None,
        handlers = Vector.empty
      )),
    setupCards = Vector.empty,
    supplyBoards = Vector.empty,
    visions = Vector.empty
  )
  private val participants = Vector(
    SetupParticipant(PlayerId("p1"), LineageId("l1")),
    SetupParticipant(PlayerId("p2"), LineageId("l2"))
  )

  test("rejects a stale client before applying a still-legal command") {
    val repository = new InMemoryEventStreamRepository
    val gateway = new ServerCommandGateway(
      new SetupApplicationService(catalog, repository)
    )

    assert(gateway.handleSetup(
      "game-stale",
      0L,
      BeginSetup(participants, catalogRef, sites)
    ).isRight)
    assert(gateway.handleSetup(
      "game-stale",
      1L,
      PlacePawn(PlayerId("p1"), sites.head)
    ).isRight)

    assertEquals(
      gateway.handleSetup(
        "game-stale",
        1L,
        PlacePawn(PlayerId("p2"), sites.head)
      ),
      Left(SetupApplicationError.StaleClientPosition(1L, 2L))
    )
    assertEquals(
      repository.load("game-stale").toOption.flatten.get.nextSequence,
      2L
    )
    assert(gateway.handleSetup(
      "game-stale",
      2L,
      PlacePawn(PlayerId("p2"), sites.head)
    ).isRight)
  }
}
