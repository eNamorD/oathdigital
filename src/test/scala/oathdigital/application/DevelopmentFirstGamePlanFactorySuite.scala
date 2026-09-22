package oathdigital.application

import java.nio.file.Files

import oathdigital.catalog.RelicRole
import oathdigital.gameplay.setup.{GameStartRules, SetupProcedure}
import oathdigital.model.{EdificeId, EdificeSide, EdificeState,
  OathState, PlayerId, RelicId, Tokens, VisionId}
import oathdigital.persistence.OwnedHsqldbEventStreamRepository
import oathdigital.protocol.{
  BootstrapParticipantRequest,
  FirstGameBootstrapRequest
}
import oathdigital.server.{GameHttpWire, GameServerGateway}
import oathdigital.gameplay.setup.FirstGameSetupFixture._

class DevelopmentFirstGamePlanFactorySuite extends munit.FunSuite {
  private val config = FirstGameBootstrapConfig(
    participants,
    PlayerId("p2")
  )

  test("derived production-catalog Chronicle passes first-game validation") {
    val factory = new DevelopmentFirstGamePlanFactory(catalog)
    val derived = factory.build(config).toOption.get.chronicle
    val orders = ChronicleFirstGamePlan.dealOrder(derived, config)

    assert(GameStartRules.evolve(catalog, derived, orders).isRight)
    assertEquals(derived.atlasBox.size, 8)
    oathdigital.model.Suit.all.foreach { suit =>
      assertEquals(
        derived.worldDeck.count(id =>
          catalog.denizens.find(_.id.value == id.value).get.suit == suit),
        10
      )
    }
  }

  test("ordinary relic order uses printed IDs and numeric catalog values") {
    val derived =
      new DevelopmentFirstGamePlanFactory(catalog).build(config).toOption.get.chronicle
    val expectedDefinitions = catalog.relics
      .filter(_.role == RelicRole.Ordinary)
      .sortBy(relic => relic.value -> relic.id.value)

    assertEquals(
      derived.relicDeck,
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

  test("bootstrap persists normal v2 history and parks the walker on the first player's pawn placement") {
    val derived = new DevelopmentFirstGamePlanFactory(catalog).build(config).toOption.get.chronicle
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

    // GameStarted plus the WalkerParked fact from Setup's immediate first
    // park (2026-09-21 Chronicle design, slice 2): two records for one
    // bootstrap command.
    assertEquals(projection.nextSequence, 2L)
    assertEquals(projection.phase, "setup-walker-decision")
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
      derived.relicDeck.size)
    // The generic walker decision panel renders Setup's first pawn placement
    // with no bespoke code (2026-09-21 Chronicle design, slice 2): no
    // separate `boardTargetActions` entry, just the parked `Decide`.
    assert(projection.boardTargetActions.isEmpty)
    val decision = projection.walkerDecision.get
    assertEquals(decision.action, "setup")
    assertEquals(decision.decisionId, SetupProcedure.pawnDecisionId(PlayerId("p2")))
    assertEquals(decision.kind, "decide")
    assertEquals(decision.query.get.form, "choose-one")
    val siteOptions = decision.query.get.options
    assertEquals(siteOptions.size, 8)
    assert(siteOptions.forall(_.kind == "site"))
    assert(!json.contains("relicOrder"))
    assert(!json.contains("worldDeckOrder"))
    assert(!json.contains("denizenOrder"))
    catalog.relics.filter(_.role == RelicRole.Ordinary)
      .foreach(relic => assert(!json.contains(relic.id.value)))

    val reopened = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    try {
      val loaded = new GameApplicationService(catalog, reopened)
        .load("bootstrap-game").toOption.flatten.get
      assertEquals(loaded.nextSequence, 2L)
      assert(loaded.state.isInstanceOf[OathState.Ready])
      val publicProjection = new GameProjector(catalog).projectPublic(
        "bootstrap-game", loaded)
      // Nobody but the awaited player sees the parked decision's owner-private
      // projection (WalkerDecisionProjector.project); the public view instead
      // sees who it is waiting on.
      assert(publicProjection.walkerDecision.isEmpty)
      assertEquals(publicProjection.walkerWaiting.map(_.playerId), Some("p2"))
    } finally reopened.close()
  }
}
