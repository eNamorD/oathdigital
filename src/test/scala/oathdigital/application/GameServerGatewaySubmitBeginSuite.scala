package oathdigital.application

import java.nio.file.Files

import oathdigital.catalog.RelicRole
import oathdigital.model.{OathState, PlayerId}
import oathdigital.persistence.OwnedHsqldbEventStreamRepository
import oathdigital.protocol.projection.BoardTargetRefProjection
import oathdigital.server.{GameHttpWire, GameServerGateway}
import oathdigital.gameplay.setup.FirstGameSetupFixture._

/** `GameServerGateway.submit` with `GameCommand.Begin` is what the
  * development and trusted bootstrap paths both reduce to once a
  * `FirstGameSetupPlan` exists; this exercises persistence and redaction
  * independent of how that plan was derived. */
class GameServerGatewaySubmitBeginSuite extends munit.FunSuite {
  test("beginning a game persists normal v2 history and returns redacted projection") {
    val path = Files.createTempDirectory("oathdigital-bootstrap-")
      .resolve("journal")
    val repository = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    val service = new GameApplicationService(catalog, repository)
    val gateway = new GameServerGateway(service, new GameProjector(catalog))
    val projection =
      try gateway.submit("bootstrap-game", PlayerId("p2"), 0L,
        GameCommand.Begin(plan)).toOption.get
      finally repository.close()
    val json = GameHttpWire.encodeProjection(projection)

    assertEquals(projection.nextSequence, 1L)
    assertEquals(projection.phase, "awaiting-pawn")
    assertEquals(projection.privateAdviserPreview.size, 3)
    assertEquals(projection.playerBoards.size, participants.size)
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
      plan.relicOrder.size)
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
