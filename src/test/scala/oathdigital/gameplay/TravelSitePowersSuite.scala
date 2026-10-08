package oathdigital.gameplay

import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.travel.TravelSitePowers
import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table, TestCards}
import oathdigital.testkit.Unwrap.present

/** Travel terrain rules are proved against contribution collection, before
  * Travel owns a walker tree. Every cost node has R25's real shape: a
  * windowed Sequence holding a bare payment leaf and its sibling pawn Move.
  */
class TravelSitePowersSuite extends munit.FunSuite:
  private val catalog = FirstGameSetupFixture.catalog
  private val actor = Table.p1
  private val powers = TravelSitePowers.all

  /** The first catalog site whose handler ends in `suffix`. */
  private def site(suffix: String): SiteId =
    def handles(handler: PowerId) = handler.value.endsWith(suffix)
    catalog.sites.find(_.handlers.exists(handles))
      .fold(fail(s"missing $suffix"))(_.id)
  private def powerId(siteId: SiteId, suffix: String): PowerId =
    present(catalog.site(siteId).flatMap(_.handlers.find(_.value
      .endsWith(suffix))), s"the $suffix handler of $siteId")

  // The first two Plains sites, and the first site of each terrain, in
  // catalog order; Rocky Coast is a second coast site, with its own
  // `.rocky-coast.coast` handler.
  private val plain = CatalogNames.site("Buried Giant")
  private val otherPlain = CatalogNames.site("Dunes")
  private val mountain = CatalogNames.site("Broken Peaks")
  private val coast = CatalogNames.site("Desolate Shore")
  private val island = CatalogNames.site("Fair Isle")
  private val pass = CatalogNames.site("Narrow Pass")
  private val rockyCoast = CatalogNames.site("Rocky Coast")

  /** p1's Act on the quiet table, p1 at `source`, the map as given. */
  private def readyAt(source: SiteId, map: Option[MapState] = None)
      : ReadyGame =
    val placed = Table.start.pawn(actor, source).ready
    // No Table step lays out regions.
    map.fold(placed)(laid => placed.updateCurrent(_.copy(map = laid)))

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
    ContributionCollector.gather(window, PowerWindowIndex.of(candidates),
      ctx(ready, window, node, _))

  private def transformedCost(ready: ReadyGame, source: SiteId,
      destination: SiteId, base: Int,
      candidates: Vector[ContributingPower] = powers): Int =
    val node = costNode(source, destination, base)
    val byId = candidates.map(power => power.id -> power).toMap
    val transformed = gathered(ready, node, candidates = candidates)
      .transforms.foldLeft(node.children) { case (ops, (id, transform)) =>
        transform.fn(ctx(ready, PowerWindow.TravelCost, node, byId(id)), ops)
      }
    transformed.collectFirst { case SpendSupply(_, amount, _) => amount }
      .getOrElse(fail("terrain transform removed payment"))

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
  test("terrain transforms give the printed Travel cost on each route"):
    assertEquals(site(".plains"), plain)
    assert(catalog.site(otherPlain).exists(_.handlers.exists(
      _.value.endsWith(".plains"))))
    assertEquals(site(".mountain"), mountain)
    assertEquals(site(".coast"), coast)
    assertEquals(site(".island"), island)
    assertEquals(site(".pass"), pass)
    assertEquals(site(".rocky-coast.coast"), rockyCoast)
    val routes = Vector(
      (plain, mountain, 2, 3),                        // Mountain adds 1
      (plain, island, 2, 4),                          // Island adds 2
      (plain, otherPlain, 2, 2),                      // no terrain
      (coast, rockyCoast, 2, 1),                      // coast route replaces
      (coast, island, 2, 1),                          // coast route beats +2
      (coast, plain, 2, 2)                            // coastal source, plain
    )                                                 // destination: no replace

    routes.foreach { case (source, destination, base, expected) =>
      assertEquals(transformedCost(readyAt(source), source, destination, base),
        expected, s"route $source -> $destination")
    }

  test("coast shouldIgnore removes destination add only on coast route"):
    val islandId = powerId(island, ".island")
    val coastRoute = costNode(coast, island, 2)
    val coastOrder = gathered(readyAt(coast), coastRoute).order
    assert(!coastOrder.contains(islandId),
      "coast-route destination Island must be ignored during gather")

    val ordinaryRoute = costNode(plain, island, 2)
    val ordinaryOrder = gathered(readyAt(plain), ordinaryRoute).order
    assert(ordinaryOrder.contains(islandId),
      "ordinary-route destination Island must remain gathered")

  private def passMap(source: SiteId, destination: SiteId,
      passForces: SiteForces = SiteForces.Occupied(ForceKind.Bandit, 1))
      : MapState =
    def state(id: SiteId, forces: SiteForces): SiteState =
      val definition = present(catalog.site(id), id.value)
      SiteState(forces, Vector.empty, Vector.empty, definition.startingResources)
    MapState(Vector(source), Vector(pass, destination), Vector.empty, Map(
      source -> state(source, SiteForces.Empty),
      pass -> state(pass, passForces),
      destination -> state(destination, SiteForces.Empty)
    ))

  private def eligibilityTree(source: SiteId, destination: SiteId): Sequence =
    Sequence(Vector(
      costNode(source, destination, 2),
      Move(Piece.Pawn(actor), PositionedLocation(Location.Site(source)),
        PositionedLocation(Location.Site(destination)))
    ), Some(PowerWindow.TravelActionEligibility))

  private def passViolation(ready: ReadyGame, tree: Operation)
      : Option[OathViolation] =
    val byId = powers.map(power => power.id -> power).toMap
    gathered(ready, tree, PowerWindow.TravelActionEligibility).restrictions
      .flatMap { case (id, restriction) =>
        restriction.fn(ctx(ready, PowerWindow.TravelActionEligibility, tree,
          byId(id)), tree)
      }.headOption

  test("Narrow Pass restricts cross-region travel but permits coast route"):
    val blockedDestination = otherPlain
    val blockedReady = readyAt(plain, Some(passMap(plain, blockedDestination)))
    assertEquals(passViolation(blockedReady,
      eligibilityTree(plain, blockedDestination)),
      Some(OathViolation.TravelPassBlocked(pass, blockedDestination)))

    val coastReady = readyAt(coast, Some(passMap(coast, island)))
    assertEquals(passViolation(coastReady, eligibilityTree(coast, island)), None)

  test("Narrow Pass fails closed when its ruler lineage is corrupt"):
    val destination = otherPlain
    val unknownReady = readyAt(plain, Some(passMap(plain, destination,
      SiteForces.Occupied(ForceKind.Exile(LineageId("missing")), 1))))
    val actorState = Look(unknownReady).player(actor)
    // No Table step adds a seat: a second player sharing p1's lineage makes
    // the pass ruler ambiguous.
    val duplicateReady = unknownReady.updateCurrent(_.copy(
        map = passMap(plain, destination,
          SiteForces.Occupied(ForceKind.Exile(actorState.lineage), 1)),
        players = unknownReady.game.current.players :+ actorState.copy(
          player = PlayerId("duplicate-lineage"))))

    Vector(unknownReady, duplicateReady).foreach { ready =>
      assertEquals(passViolation(ready, eligibilityTree(plain, destination)),
        Some(OathViolation.TravelPassBlocked(pass, destination)))
    }

  test("unknown terrain-suffixed handlers never fabricate Travel powers"):
    val fixturePower = PowerId("site.fixture-site.coast")
    val fixtureSite = TestCards.siteLike(present(catalog.site(plain), "plain"))(
      id = SiteId("site:fixture-site"), handlers = Vector(fixturePower))
    val augmented = catalog.copy(sites = catalog.sites :+ fixtureSite)

    assert(!WalkerPowerCatalog.default(augmented).powers.map(_.id)
      .contains(fixturePower))
