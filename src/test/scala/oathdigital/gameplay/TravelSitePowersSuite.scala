package oathdigital.gameplay

import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.travel.TravelSitePowers
import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.model._

/** Travel terrain rules are proved against contribution collection, before
  * Travel owns a walker tree. Every cost node has R25's real shape: a
  * windowed Sequence holding a bare payment leaf and its sibling pawn Move.
  */
class TravelSitePowersSuite extends munit.FunSuite {
  private val catalog = FirstGameSetupFixture.catalog
  private val baseReady = FirstGameSetupFixture.execute()._1 match {
    case OathState.Ready(ready) => ready
    case other => fail(s"expected Ready state, got $other")
  }
  private val actor = baseReady.game.current.turn.activePlayer
  private val powers = TravelSitePowers.forCatalog(catalog)

  private def site(suffix: String): SiteId = catalog.sites.find(
    _.handlers.exists(_.endsWith(suffix))).fold(fail(s"missing $suffix"))(_.id)
  private def powerId(siteId: SiteId, suffix: String): PowerId =
    PowerId(catalog.sites.find(_.id == siteId).fold(
      fail(s"missing $siteId"))(_.handlers.find(_.endsWith(suffix)).get))

  private val plain = site(".plains")
  private val otherPlain = catalog.sites.filter(
    _.handlers.exists(_.endsWith(".plains"))).map(_.id).find(_ != plain)
    .getOrElse(fail("missing a second plains site"))
  private val mountain = site(".mountain")
  private val coast = site(".coast")
  private val island = site(".island")
  private val pass = site(".pass")

  private def readyAt(source: SiteId, map: MapState = baseReady.game.current.map)
      : ReadyGame =
    baseReady.updateCurrent(_.copy(
      map = map,
      players = baseReady.game.current.players.map { player =>
        if (player.player == actor) player.copy(pawnSite = Some(source))
        else player
      }))

  private def costNode(source: SiteId, destination: SiteId, base: Int): Sequence =
    Sequence(Vector(
      SpendSupply(actor, base),
      Move(Piece.Pawn(actor), PositionedLocation(Location.Site(source)),
        PositionedLocation(Location.Site(destination)))
    ), Some(PowerWindow.TravelCost))

  private def ctx(ready: ReadyGame, window: PowerWindow, operation: Operation,
      power: ContributingPower): PowerCtx =
    PowerCtx(ready, actor, power.source, window, Vector.empty, operation)

  private def gathered(ready: ReadyGame, node: Operation,
      window: PowerWindow = PowerWindow.TravelCost,
      candidates: Vector[ContributingPower] = powers): GatheredContributions =
    ContributionCollector.gather(window, candidates, ctx(ready, window, node, _))

  private def transformedCost(ready: ReadyGame, source: SiteId,
      destination: SiteId, base: Int,
      candidates: Vector[ContributingPower] = powers): Int = {
    val node = costNode(source, destination, base)
    val byId = candidates.map(power => power.id -> power).toMap
    val transformed = gathered(ready, node, candidates = candidates)
      .transforms.foldLeft(node.children) { case (ops, (id, transform)) =>
        transform.fn(ctx(ready, PowerWindow.TravelCost, node, byId(id)), ops)
      }
    transformed.collectFirst { case SpendSupply(_, amount, _) => amount }
      .getOrElse(fail("terrain transform removed payment"))
  }

  /** The parity table, as numbers rather than as a call.
    *
    * Task 4 asserted each of these against the legacy terrain fold while that
    * fold was still alive, which is what proved the transforms reproduce it.
    * Task 5 deleted the fold, so what survives that proof is its result: these
    * are the exact values the legacy path returned for these routes, and a
    * transform that stops reproducing one fails here. Keeping a call to
    * something that no longer exists was never an option; keeping the numbers
    * it produced is the whole point of having run the comparison.
    */
  test("terrain transforms reproduce the retired terrain fold's parity table") {
    val routes = Vector(
      (plain, mountain, 2, 3),                        // Mountain adds 1
      (plain, island, 2, 4),                          // Island adds 2
      (plain, otherPlain, 2, 2),                      // no terrain
      (coast, site(".rocky-coast.coast"), 2, 1),      // coast route replaces
      (coast, island, 2, 1),                          // coast route beats +2
      (coast, plain, 2, 2)                            // coastal source, plain
    )                                                 // destination: no replace

    routes.foreach { case (source, destination, base, expected) =>
      assertEquals(transformedCost(readyAt(source), source, destination, base),
        expected, s"route $source -> $destination")
    }
  }

  test("coast shouldIgnore removes destination add only on coast route") {
    val islandId = powerId(island, ".island")
    val coastRoute = costNode(coast, island, 2)
    val coastOrder = gathered(readyAt(coast), coastRoute).order
    assert(!coastOrder.contains(islandId),
      "coast-route destination Island must be ignored during gather")

    val ordinaryRoute = costNode(plain, island, 2)
    val ordinaryOrder = gathered(readyAt(plain), ordinaryRoute).order
    assert(ordinaryOrder.contains(islandId),
      "ordinary-route destination Island must remain gathered")
  }

  test("each terrain transform changes cost from its no-power baseline") {
    val cases = Vector(
      ("Mountain", plain, mountain, ".mountain"),
      ("Island", plain, island, ".island"),
      ("Coast", coast, site(".rocky-coast.coast"), ".coast")
    )
    cases.foreach { case (name, source, destination, suffix) =>
      val ready = readyAt(source)
      val terrainOnly = powers.filter(_.id == powerId(
        if (name == "Coast") source else destination, suffix))
      assertEquals(transformedCost(ready, source, destination, 2, Vector.empty), 2,
        s"$name baseline must remain the unmodified payment")
      assertNotEquals(transformedCost(ready, source, destination, 2, terrainOnly), 2,
        s"$name transform must rewrite the payment")
    }
  }

  private def passMap(source: SiteId, destination: SiteId,
      passForces: SiteForces = SiteForces.Occupied(ForceKind.Bandit, 1))
      : MapState = {
    def state(id: SiteId, forces: SiteForces): SiteState = {
      val definition = catalog.sites.find(_.id == id).get
      SiteState(forces, Vector.empty, Vector.empty, definition.startingResources)
    }
    MapState(Vector(source), Vector(pass, destination), Vector.empty, Map(
      source -> state(source, SiteForces.Empty),
      pass -> state(pass, passForces),
      destination -> state(destination, SiteForces.Empty)
    ))
  }

  private def eligibilityTree(source: SiteId, destination: SiteId): Sequence =
    Sequence(Vector(
      costNode(source, destination, 2),
      Move(Piece.Pawn(actor), PositionedLocation(Location.Site(source)),
        PositionedLocation(Location.Site(destination)))
    ), Some(PowerWindow.TravelActionEligibility))

  private def passViolation(ready: ReadyGame, tree: Operation)
      : Option[OathViolation] = {
    val byId = powers.map(power => power.id -> power).toMap
    gathered(ready, tree, PowerWindow.TravelActionEligibility).restrictions
      .flatMap { case (id, restriction) =>
        restriction.fn(ctx(ready, PowerWindow.TravelActionEligibility, tree,
          byId(id)), tree)
      }.headOption
  }

  test("Narrow Pass restricts cross-region travel but permits coast route") {
    val blockedDestination = otherPlain
    val blockedReady = readyAt(plain, passMap(plain, blockedDestination))
    assertEquals(passViolation(blockedReady,
      eligibilityTree(plain, blockedDestination)),
      Some(OathViolation.TravelPassBlocked(pass, blockedDestination)))

    val coastReady = readyAt(coast, passMap(coast, island))
    assertEquals(passViolation(coastReady, eligibilityTree(coast, island)), None)
  }

  test("Narrow Pass fails closed when its ruler lineage is corrupt") {
    val destination = otherPlain
    val unknownReady = readyAt(plain, passMap(plain, destination,
      SiteForces.Occupied(ForceKind.Exile(LineageId("missing")), 1)))
    val actorState = unknownReady.game.current.players.find(_.player == actor).get
    val duplicateReady = unknownReady.updateCurrent(_.copy(
        map = passMap(plain, destination,
          SiteForces.Occupied(ForceKind.Exile(actorState.lineage), 1)),
        players = unknownReady.game.current.players :+ actorState.copy(
          player = PlayerId("duplicate-lineage"))))

    Vector(unknownReady, duplicateReady).foreach { ready =>
      assertEquals(passViolation(ready, eligibilityTree(plain, destination)),
        Some(OathViolation.TravelPassBlocked(pass, destination)))
    }
  }

  test("Walker catalog registers every Travel site contribution") {
    val registered = WalkerPowerCatalog.default(catalog).powers.map(_.id).toSet
    assert(powers.map(_.id).toSet.subsetOf(registered))
  }

  test("unknown terrain-suffixed handlers never fabricate Travel powers") {
    val fixturePower = PowerId("site.fixture-site.coast")
    val fixtureSite = catalog.sites.find(_.id == plain).get.copy(
      id = SiteId("site:fixture-site"), handlers = Vector(fixturePower.value))
    val augmented = catalog.copy(sites = catalog.sites :+ fixtureSite)

    assert(!TravelSitePowers.forCatalog(augmented).map(_.id).contains(fixturePower))
  }
}
