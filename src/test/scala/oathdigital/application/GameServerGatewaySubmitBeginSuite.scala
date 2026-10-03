package oathdigital.application

import java.nio.file.{Files, Path}

import oathdigital.gameplay.operations.TheGrandScepterCard
import oathdigital.gameplay.setup.SetupProcedure
import oathdigital.model.OathState
import oathdigital.persistence.OwnedHsqldbEventStreamRepository
import oathdigital.server.{GameHttpWire, GameServerGateway}
import oathdigital.protocol.projection.{DecisionQueryProjection, GameProjection,
  SiteForcesProjection}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.testkit.Table.p2
import oathdigital.testkit.Unwrap.{accepted, present}
import oathdigital.application.ParkedServiceFixture.loaded

/** `GameServerGateway.submit` with `GameCommand.Begin` is what the
  * development and trusted bootstrap paths both reduce to once a Chronicle
  * has been derived; this exercises persistence and redaction independent
  * of how that Chronicle was derived. This suite asserts the exact
  * requested first player, so seating must not shuffle. */
class GameServerGatewaySubmitBeginSuite extends munit.FunSuite:
  // GeneratedFirstGamePlanFactory always makes the head of the (possibly
  // shuffled) seating the first player, ignoring the requested
  // `firstPlayer` -- so with an identity shuffle, "p2" must already be
  // first in `participants` to end up first here.
  private val unshuffled: ChronicleRandomPort = new ChronicleRandomPort:
    def shuffle[A](values: Vector[A]): Vector[A] = values
  private val config = FirstGameBootstrapConfig(
    participants.sortBy(p => if p.playerId == p2 then 0 else 1), p2)

  /** A game begun through the gateway, journaled in HSQL at `path`: the plan
    * it was dealt from and the projection p2 is returned. */
  private final case class BegunGame(path: Path, plan: FirstGamePlan,
      projection: GameProjection)

  private def begun(using munit.Location): BegunGame =
    val plan = accepted(new GeneratedFirstGamePlanFactory(catalog, unshuffled)
      .build(config))
    val dealt = ChronicleFirstGamePlan.dealOrder(plan.chronicle, plan.resolvedConfig)
    val path = Files.createTempDirectory("oathdigital-bootstrap-")
      .resolve("journal")
    val repository = accepted(OwnedHsqldbEventStreamRepository.open(path))
    val service = new GameApplicationService(catalog, repository)
    val gateway = new GameServerGateway(service, new GameProjector(catalog))
    val projection =
      try accepted(gateway.submit("bootstrap-game", p2, 0L,
        GameCommand.Begin(plan.chronicle, dealt)))
      finally repository.close()
    BegunGame(path, plan, projection)

  test("beginning a game persists history and reloads it"):
    val game = begun
    // GameStarted plus the WalkerParked fact from Setup's immediate first
    // park (2026-09-21 Chronicle design, slice 2): two records for one
    // Begin command.
    assertEquals(game.projection.nextSequence, 2L)
    val reopened = accepted(OwnedHsqldbEventStreamRepository.open(game.path))
    try
      val stored = loaded(new GameApplicationService(catalog, reopened)
        .load("bootstrap-game"))
      assertEquals(stored.nextSequence, 2L)
      assert(stored.state.isInstanceOf[OathState.Ready])
      val publicProjection = new GameProjector(catalog).projectPublic(
        "bootstrap-game", stored)
      // Nobody but the awaited player sees the parked decision's owner-private
      // projection (WalkerDecisionProjector.project); the public view instead
      // sees who it is waiting on.
      assert(publicProjection.walkerDecision.isEmpty)
      assertEquals(publicProjection.walkerWaiting.map(_.playerId), Some("p2"))
    finally reopened.close()

  test("a begun game projects its dealt world and tracks"):
    val BegunGame(_, plan, projection) = begun
    assertEquals(projection.phase, "setup-walker-decision")
    assertEquals(projection.playerBoards.size, config.participants.size)
    assert(projection.world.flatMap(_.sites).exists(_.looseFavor > 0))
    assert(projection.world.flatMap(_.sites).exists(_.relics.facedownCount > 0))
    assert(projection.world.flatMap(_.sites).exists(_.forces.exists(_.isInstanceOf[SiteForcesProjection.Bandit])))
    assert(projection.world.flatMap(_.sites).exists(_.denizens.exists(
      _.details.exists(details => details.cardKind == "edifice" &&
        details.side.contains("ruined")))))
    val projectedEdifices = projection.world.flatMap(_.sites).flatMap(_.denizens)
      .flatMap(_.details).filter(_.cardKind == "edifice")
    assert(projectedEdifices.nonEmpty)
    projectedEdifices.foreach { details =>
      val definition = present(
        catalog.edifices.find(_.id.value == details.cardId), details.cardId)
      assertEquals(details.restrictions, Some("unrestricted"))
      assertEquals(details.rulesText, Some(definition.ruined.rulesText))
    }
    // Setup seeds 2 discards in each region.
    assertEquals(projection.world.map(_.discardCount), Vector(2, 2, 2))
    assertEquals(projection.favorBanks.map(_.suit).toSet,
      oathdigital.model.Suit.all.map(_.key).toSet)
    // The first round, no Visions drawn, the Usurper limiter at round 4, and
    // p2 first.
    assertEquals(projection.tracks.map(track =>
      (track.round, track.visionsDrawn, track.usurperLimited, track.limiterRound,
        track.firstPlayerId)), Some((1, 0, true, 4, "p2")))
    assertEquals(projection.relicDeckCount +
      projection.world.flatMap(_.sites).map(_.relics.facedownCount).sum,
      plan.chronicle.relicDeck.size)

  test("the first Setup decision offers p2 every in-play site"):
    val projection = begun.projection
    // The generic walker decision panel renders Setup's first pawn placement
    // with no bespoke code (2026-09-21 Chronicle design, slice 2): no
    // separate `boardTargetActions` entry, just the parked `Decide`.
    assert(projection.boardTargetActions.isEmpty)
    val decision = present(projection.walkerDecision, "p2's Setup decision")
    assertEquals(decision.action, "setup")
    assertEquals(decision.decisionId, SetupProcedure.pawnDecisionId(p2))
    assertEquals(decision.kind, "decide")
    val siteOptions = decision.query match
      case Some(one: DecisionQueryProjection.ChooseOne) => one.options
      case other => fail(s"expected a choose-one, got $other")
    // The 8 in-play sites.
    assertEquals(siteOptions.size, 8)
    assert(siteOptions.forall(_.kind == "site"))

  test("the redacted projection hides deck orders and relic ids"):
    val json = GameHttpWire.encodeProjection(begun.projection)
    assert(!json.contains("relicOrder"))
    assert(!json.contains("worldDeckOrder"))
    assert(!json.contains("denizenOrder"))
    catalog.relics.filter(_.id != TheGrandScepterCard.id)
      .foreach(relic => assert(!json.contains(relic.id.value)))
