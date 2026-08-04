package oathdigital.application

import java.nio.file.Files

import oathdigital.catalog.RelicRole
import oathdigital.model.{PlayerId, RelicId}
import oathdigital.persistence.OwnedHsqldbEventStreamRepository
import oathdigital.server.{
  FirstGameBootstrapRequest,
  FirstGameHttpWire,
  FirstGameServerGateway
}
import oathdigital.setup.{
  FirstGameSetupCommand,
  FirstGameSetupRules,
  FirstGameSetupState
}
import oathdigital.setup.FirstGameSetupFixture._

class DevelopmentFirstGamePlanFactorySuite extends munit.FunSuite {
  private val config = FirstGameBootstrapConfig(
    participants.map(participant =>
      BootstrapParticipant(
        participant.playerId,
        participant.lineageId,
        participant.color
      )),
    PlayerId("p2")
  )

  test("derived production-catalog plan passes first-game validation") {
    val factory = new DevelopmentFirstGamePlanFactory(catalog)
    val derived = factory.build(config).toOption.get
    val rules = new FirstGameSetupRules(catalog)

    assert(rules.handle(
      FirstGameSetupState.NoGame,
      FirstGameSetupCommand.Begin(derived)
    ).isRight)
    assertEquals(derived.orderedSites.size, 8)
    oathdigital.catalog.Suit.values.foreach { suit =>
      assertEquals(
        derived.denizenOrder.count(id =>
          catalog.denizens.find(_.id.value == id.value).get.suit.value == suit),
        10
      )
    }
  }

  test("ordinary relic order uses printed IDs and numeric catalog values") {
    val derived =
      new DevelopmentFirstGamePlanFactory(catalog).build(config).toOption.get
    val expectedDefinitions = catalog.relics
      .filter(_.role == RelicRole.Ordinary)
      .sortBy(relic => relic.value -> relic.id.value)

    assertEquals(
      derived.relicOrder,
      expectedDefinitions.map(relic => RelicId(relic.id.value))
    )
    assert(expectedDefinitions.forall(_.id.value.matches("R[0-9]+")))
    assertEquals(
      expectedDefinitions.map(_.value),
      expectedDefinitions.map(_.value).sorted
    )
  }

  test("bootstrap persists normal v2 history and returns redacted projection") {
    val path = Files.createTempDirectory("oathdigital-bootstrap-")
      .resolve("journal")
    val repository = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    val service = new FirstGameApplicationService(catalog, repository)
    val gateway = new FirstGameServerGateway(
      service,
      new FirstGameProjector(catalog),
      new DevelopmentFirstGamePlanFactory(catalog)
    )
    val projection =
      try gateway.bootstrap(
        "bootstrap-game",
        PlayerId("p2"),
        FirstGameBootstrapRequest(0L, config)
      ).toOption.get
      finally repository.close()
    val json = FirstGameHttpWire.encodeProjection(projection)

    assertEquals(projection.nextSequence, 1L)
    assertEquals(projection.phase, "awaiting-pawn")
    assert(!json.contains("relicOrder"))
    assert(!json.contains("worldDeckOrder"))
    assert(!json.contains("denizenOrder"))
    catalog.relics.filter(_.role == RelicRole.Ordinary)
      .foreach(relic => assert(!json.contains(relic.id.value)))

    val reopened = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    try {
      val loaded = new FirstGameApplicationService(catalog, reopened)
        .load("bootstrap-game").toOption.flatten.get
      assertEquals(loaded.nextSequence, 1L)
      assert(loaded.state.isInstanceOf[FirstGameSetupState.InProgress])
    } finally reopened.close()
  }
}
