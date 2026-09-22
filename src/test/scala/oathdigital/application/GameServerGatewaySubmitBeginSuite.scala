package oathdigital.application

import java.nio.file.Files

import oathdigital.catalog.RelicRole
import oathdigital.gameplay.setup.SetupProcedure
import oathdigital.model.{OathState, PlayerId}
import oathdigital.persistence.OwnedHsqldbEventStreamRepository
import oathdigital.server.{GameHttpWire, GameServerGateway}
import oathdigital.gameplay.setup.FirstGameSetupFixture._

/** `GameServerGateway.submit` with `GameCommand.Begin` is what the
  * development and trusted bootstrap paths both reduce to once a Chronicle
  * has been derived; this exercises persistence and redaction independent
  * of how that Chronicle was derived. This suite asserts the exact
  * requested first player, so seating must not shuffle. */
class GameServerGatewaySubmitBeginSuite extends munit.FunSuite {
  // GeneratedFirstGamePlanFactory always makes the head of the (possibly
  // shuffled) seating the first player, ignoring the requested
  // `firstPlayer` -- so with an identity shuffle, "p2" must already be
  // first in `participants` to end up first here.
  private val unshuffled: ChronicleRandomPort = new ChronicleRandomPort {
    def shuffle[A](values: Vector[A]): Vector[A] = values
  }
  private val config = FirstGameBootstrapConfig(
    participants.sortBy(p => if (p.playerId == PlayerId("p2")) 0 else 1),
    PlayerId("p2"))

  test("beginning a game persists normal v2 history and returns redacted projection") {
    val plan = new GeneratedFirstGamePlanFactory(catalog, unshuffled)
      .build(config).toOption.get
    val dealt = ChronicleFirstGamePlan.dealOrder(plan.chronicle, plan.resolvedConfig)
    val path = Files.createTempDirectory("oathdigital-bootstrap-")
      .resolve("journal")
    val repository = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    val service = new GameApplicationService(catalog, repository)
    val gateway = new GameServerGateway(service, new GameProjector(catalog))
    val projection =
      try gateway.submit("bootstrap-game", PlayerId("p2"), 0L,
        GameCommand.Begin(plan.chronicle, dealt)).toOption.get
      finally repository.close()
    val json = GameHttpWire.encodeProjection(projection)

    // GameStarted plus the WalkerParked fact from Setup's immediate first
    // park (2026-09-21 Chronicle design, slice 2): two records for one
    // Begin command.
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
      plan.chronicle.relicDeck.size)
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
