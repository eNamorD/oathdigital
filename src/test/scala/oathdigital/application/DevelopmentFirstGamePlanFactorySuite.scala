package oathdigital.application

import java.nio.file.Files

import oathdigital.catalog.RelicRole
import oathdigital.model.{EdificeId, EdificeSide, EdificeState, FirstGameSetupCommand, OathState, PlayerId, RelicId, Tokens, VisionId}
import oathdigital.persistence.OwnedHsqldbEventStreamRepository
import oathdigital.protocol.{
  BootstrapParticipantRequest,
  FirstGameBootstrapRequest
}
import oathdigital.protocol.projection.BoardTargetRefProjection
import oathdigital.server.{GameHttpWire, GameServerGateway}
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.setup.FirstGameSetupFixture._

class DevelopmentFirstGamePlanFactorySuite extends munit.FunSuite {
  private val config = FirstGameBootstrapConfig(
    participants,
    PlayerId("p2")
  )

  test("derived production-catalog plan passes first-game validation") {
    val factory = new DevelopmentFirstGamePlanFactory(catalog)
    val derived = factory.build(config).toOption.get
    val rules = new FirstGameSetupRules(catalog)

    assert(rules.handle(
      OathState.NoGame,
      FirstGameSetupCommand.Begin(derived)
    ).isRight)
    assertEquals(derived.orderedSites.size, 8)
    oathdigital.model.Suit.all.foreach { suit =>
      assertEquals(
        derived.denizenOrder.count(id =>
          catalog.denizens.find(_.id.value == id.value).get.suit == suit),
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

  test("every Vision card detail uses the authoritative printed presentation") {
    val projector = new GamePresentationProjector(catalog)
    val ids = Vector("vision:vision-of-conquest", "vision:vision-of-sanctuary",
      "vision:vision-of-rebellion", "vision:vision-of-faith", "vision:conspiracy")
    ids.foreach { id =>
      val expected = oathdigital.protocol.projection.VisionCardPresentation.byId(id)
      val details = projector.cardDetails(VisionId(id), None, hidden = false)
      assertEquals(details.name, expected.name)
      assertEquals(details.rulesText, Some(expected.rulesText))
    }
  }

  test("edifice card details use intact and ruined catalog face text") {
    val projector = new GamePresentationProjector(catalog)
    val definition = catalog.edifices.head
    val id = EdificeId(definition.id.value)
    Vector(EdificeSide.Intact -> definition.intact,
      EdificeSide.Ruined -> definition.ruined).foreach { case (side, face) =>
      val details = projector.edificeCardDetails(EdificeState(id, side, Tokens.empty))
      assertEquals(details.name, face.name)
      assertEquals(details.rulesText, Some(face.rulesText))
      assertEquals(details.restrictions, Some(side match {
        case EdificeSide.Intact => "locked"
        case EdificeSide.Ruined => "unrestricted"
      }))
    }
  }

  test("bootstrap persists normal v2 history and returns redacted projection") {
    val derived = new DevelopmentFirstGamePlanFactory(catalog).build(config).toOption.get
    val path = Files.createTempDirectory("oathdigital-bootstrap-")
      .resolve("journal")
    val repository = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    val service = new GameApplicationService(catalog, repository)
    val gateway = new GameServerGateway(
      service,
      new GameProjector(catalog),
      new DevelopmentFirstGamePlanFactory(catalog)
    )
    val projection =
      try gateway.bootstrap(
        "bootstrap-game",
        PlayerId("p2"),
        FirstGameBootstrapRequest(0L, config.participants.map(participant =>
          BootstrapParticipantRequest(participant.playerId.value,
            participant.lineageId.value, participant.color.value)),
          config.firstPlayer.value)
      ).toOption.get
      finally repository.close()
    val json = GameHttpWire.encodeProjection(projection)

    assertEquals(projection.nextSequence, 1L)
    assertEquals(projection.phase, "awaiting-pawn")
    assertEquals(projection.privateAdviserPreview.size, 3)
    assertEquals(projection.playerBoards.size, config.participants.size)
    assert(projection.world.flatMap(_.sites).exists(_.looseFavor > 0))
    assert(projection.world.flatMap(_.sites).exists(_.relics.facedownCount > 0))
    assert(projection.world.flatMap(_.sites).exists(_.forces.exists(_.forceKind == "bandit")))
    assert(projection.world.flatMap(_.sites).exists(_.denizens.exists(
      _.details.exists(details => details.cardKind == "edifice" &&
        details.side.contains("ruined")))))
    val projectedEdifices = projection.world.flatMap(_.sites).flatMap(_.denizens)
      .flatMap(_.details).filter(_.cardKind == "edifice")
    assert(projectedEdifices.nonEmpty)
    projectedEdifices.foreach { details =>
      val definition = catalog.edifices.find(_.id.value == details.cardId).get
      assertEquals(details.restrictions, Some("unrestricted"))
      assertEquals(details.rulesText, Some(definition.ruined.rulesText))
    }
    assertEquals(projection.world.map(_.discardCount), Vector(2, 2, 2))
    assertEquals(projection.favorBanks.map(_.suit).toSet,
      oathdigital.model.Suit.all.map(_.key).toSet)
    assertEquals(projection.tracks.map(track =>
      (track.round, track.visionsDrawn, track.usurperLimited, track.limiterRound,
        track.firstPlayerId)), Some((1, 0, true, 4, "p2")))
    assertEquals(projection.relicDeckCount +
      projection.world.flatMap(_.sites).map(_.relics.facedownCount).sum,
      derived.relicOrder.size)
    val placement = projection.boardTargetActions.head
    assertEquals(placement.actionKind, "place-pawn")
    assert(placement.autoActivate)
    assertEquals(placement.minimum -> placement.maximum, 1 -> 1)
    assertEquals(placement.candidates.map(_.target).toSet,
      projection.world.flatMap(_.sites).map(site =>
        oathdigital.model.SiteId(site.siteId)).map(site =>
        (BoardTargetRefProjection.Site(site.value): BoardTargetRefProjection)).toSet)
    val targetWire = ujson.read(json)("boardTargetActions")(0)
    assertEquals(targetWire("actionKind").str, "place-pawn")
    assertEquals(targetWire("minimum").num.toInt ->
      targetWire("maximum").num.toInt, 1 -> 1)
    assertEquals(targetWire("candidates")(0)("target")("kind").str, "site")
    assert(!json.contains("relicOrder"))
    assert(!json.contains("worldDeckOrder"))
    assert(!json.contains("denizenOrder"))
    catalog.relics.filter(_.role == RelicRole.Ordinary)
      .foreach(relic => assert(!json.contains(relic.id.value)))

    val reopened = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    try {
      val loaded = new GameApplicationService(catalog, reopened)
        .load("bootstrap-game").toOption.flatten.get
      assertEquals(loaded.nextSequence, 1L)
      assert(loaded.state.isInstanceOf[OathState.InProgress])
      val publicProjection = new GameProjector(catalog).projectPublic(
        "bootstrap-game", loaded)
      assertEquals(publicProjection.privateAdviserPreview, Vector.empty)
      projection.privateAdviserPreview.foreach(card =>
        assert(!GameHttpWire.encodeProjection(publicProjection).contains(card.cardId)))
    } finally reopened.close()
  }
}
