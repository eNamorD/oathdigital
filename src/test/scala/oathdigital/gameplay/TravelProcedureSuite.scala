package oathdigital.gameplay

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Transform}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation._
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, present, rejected}

/** Travel on the generic walker.
  *
  * The board pins coast, island, mountain and pass at named sites, so every
  * cost this suite asserts is the printed cost for that route.
  */
class TravelProcedureSuite extends munit.FunSuite:
  private val powers: WalkerPowers = WalkerPowers.selected(
    WalkerPowerCatalog.default(catalog), Vector.empty)

  private val actor = p1
  // The terrain sites, and four Plains sites.
  private val coast = CatalogNames.site("Desolate Shore")
  private val island = CatalogNames.site("Fair Isle")
  private val mountain = CatalogNames.site("Broken Peaks")
  private val pass = CatalogNames.site("Narrow Pass")
  private val cradlePlains = CatalogNames.site("Buried Giant")
  private val provincePlains = CatalogNames.site("Dunes")
  private val hinterlandPlains = CatalogNames.site("Salt Flats")
  private val secondHinterlandPlains = CatalogNames.site("Shrouded Woods")

  /** The site whose handler names the terrain `power`. */
  private def terrainSite(power: String): Option[SiteId] = catalog.sites.find(
    _.handlers.exists(_.value.endsWith(s".$power"))).map(_.id)

  /** p1's Act on a re-laid map. The Cradle holds `source` and the coast
    * (Desolate Shore); the Provinces hold Dunes, the mountain (Broken Peaks)
    * and the pass (Narrow Pass); the Hinterland holds Salt Flats, the island
    * (Fair Isle) and Shrouded Woods. The pass holds one bandit, every other
    * site with a printed capacity a bandit stack of it, and every site its
    * printed starting wealth. p1 stands at `source` with `supply` Supply; p2
    * stands on the layout's third site (Dunes) and p3 on its fourth (Broken
    * Peaks). The printed base is 1 within the Cradle, 2 from the Cradle to
    * the Provinces, 4 from the Cradle to the Hinterland. The layout is for
    * the default source; a source already on the list shifts every later
    * site up one place.
    */
  private def travelBoard(
      supply: Int,
      source: SiteId = cradlePlains
  ): ReadyGame =
    val initial = Table.start.supply(actor, supply).ready
    val chosen = Vector(source, coast, provincePlains, mountain, pass,
      hinterlandPlains, island, secondHinterlandPlains)
    val ids = (chosen.distinct ++ catalog.sites.map(_.id)
      .filterNot(chosen.contains)).take(8)
    val states = ids.map { id =>
      val definition = present(catalog.site(id), id.value)
      id -> SiteState(
        if id == pass then SiteForces.Occupied(ForceKind.Bandit, 1)
        else if definition.capacity == 0 then SiteForces.Empty
        else SiteForces.Occupied(ForceKind.Bandit, definition.capacity),
        Vector.empty, Vector.empty, definition.startingResources)
    }.toMap
    val players = initial.game.current.players.zipWithIndex.map:
      case (player, index) => player.copy(pawnSite = Some(
        if player.player == actor then source else ids(index + 1)))
    // No Table step lays out regions: the board replaces the whole map.
    initial.updateCurrent(_.copy(
      players = players,
      map = MapState(ids.take(2), ids.slice(2, 5), ids.slice(5, 8), states)))

  /** The pass ruled by the actor, so a cross-region route past it is legal. */
  private def passRuled(ready: ReadyGame): ReadyGame =
    Table.from(ready).warbandsAt(pass, actor, 1).unchecked

  private def simulate(ready: ReadyGame, destination: SiteId,
      using: WalkerPowers = powers): Option[Int] =
    TravelProcedure.candidates(catalog, ready, actor, using)
      .collectFirst { case (site, cost) if site == destination => cost }

  // (a) candidate simulation matches StartWalker for every terrain case.

  test("simulated candidate cost equals the printed table on a plain route"):
    Vector(cradlePlains, provincePlains, hinterlandPlains,
      secondHinterlandPlains).foreach { site =>
      assert(catalog.site(site).exists(_.handlers.exists(
        _.value.endsWith(".plains"))), site.value)
    }
    Vector("coast" -> coast, "island" -> island, "mountain" -> mountain,
      "pass" -> pass).foreach { (power, site) =>
      assertEquals(terrainSite(power), Some(site), power)
    }
    val ready = passRuled(travelBoard(supply = 7))
    // The printed base: 1 within the Cradle, 2 to the Provinces, 4 to the
    // Hinterland.
    assertEquals(simulate(ready, coast), Some(1))
    assertEquals(simulate(ready, provincePlains), Some(2))
    assertEquals(simulate(ready, hinterlandPlains), Some(4))

  test("Mountain adds one and Island adds two to the printed base"):
    val ready = passRuled(travelBoard(supply = 7))
    // The Provinces' 2, plus 1; the Hinterland's 4, plus 2.
    assertEquals(simulate(ready, mountain), Some(3))
    assertEquals(simulate(ready, island), Some(6))

  test("a coast route ignores the destination add rather than stacking it"):
    // Fair Isle carries both a coast and an island power: on a coast route
    // the replace lands and the +2 is dropped, so 1 rather than 3.
    val ready = travelBoard(supply = 7, source = coast)
    assertEquals(simulate(ready, island), Some(1))
    assertNotEquals(simulate(ready, island), Some(3))

  test("a non-coast route from a coastal site keeps the printed base"):
    // With the coast as the source the layout shifts by one: the Cradle
    // holds Desolate Shore and Dunes, the Provinces Broken Peaks, Narrow Pass
    // and Salt Flats.
    val ready = passRuled(travelBoard(supply = 7, source = coast))
    assertEquals(ready.game.current.map.provinces,
      Vector(mountain, pass, hinterlandPlains))
    // Salt Flats lies in the Provinces on this shifted layout.
    val shiftedProvincePlains = hinterlandPlains
    assertEquals(simulate(ready, shiftedProvincePlains), Some(2))

  test("StartWalker spends exactly the cost the candidate simulation showed"):
    val ready = passRuled(travelBoard(supply = 7))
    val rules = new OathRules(catalog, walkerPowerCatalog = powers)
    Vector(coast, mountain, island).foreach:
      destination =>
        val expected = present(simulate(ready, destination), "a cost")
        val traveled = accepted(rules.startWalker(Ready(ready),
          ActionRef.Travel, actor, Vector.empty,
          Vector(DecisionOptionRef.Site(destination))))
        val after = Outcomes.ready(traveled.state)
        assertEquals(Look(after).pawn(actor), destination)
        assertEquals(Look(after).supply(actor), 7 - expected,
          s"travel to ${destination.value}")

  // (b) Narrow Pass removes the candidate and rejects a forged command.

  test("Narrow Pass removes the candidate and rejects a forged command"):
    val ready = travelBoard(supply = 7)
    val destination = provincePlains
    val rules = new OathRules(catalog, walkerPowerCatalog = powers)
    assertEquals(simulate(ready, destination), None)
    assertEquals(rejected(rules.startWalker(Ready(ready), ActionRef.Travel,
      actor, Vector.empty,
      Vector(DecisionOptionRef.Site(destination)))),
      TravelPassBlocked(pass, destination))

  test("a pass ruled by the actor blocks nothing, and a coast route passes it"):
    val ruled = passRuled(travelBoard(supply = 7))
    val destination = provincePlains
    assert(simulate(ruled, destination).nonEmpty)
    // A coast route is exempt from the pass even while a bandit holds it.
    assert(simulate(travelBoard(supply = 7, source = coast), island).nonEmpty)

  // (c) zero Supply is not a build gate, but simulation and execution refuse.

  test("zero Supply passes the semantic build and fails at the pay node"):
    val ready = passRuled(travelBoard(supply = 0))
    val destination = coast
    assert(TravelProcedure.build(catalog, ready, actor,
      Vector(DecisionOptionRef.Site(destination))).isRight,
      "supply is owned by the transformed SpendSupply, not a build gate")
    assertEquals(simulate(ready, destination), None)

    val rules = new OathRules(catalog, walkerPowerCatalog = powers)
    val result = rules.startWalker(Ready(ready), ActionRef.Travel, actor,
      Vector.empty, Vector(DecisionOptionRef.Site(destination)))
    assertEquals(result.map(_ => ()), Left(OathViolation.CoreOperationRejected(
      "insufficient-supply", "a supply spend of 1 exceeds the 0 available")))

  test("simulation omits every destination the actor cannot afford"):
    val ready = passRuled(travelBoard(supply = 2))
    val offered = TravelProcedure.candidates(catalog, ready,
      actor, powers)
    assert(offered.nonEmpty)
    assert(offered.forall(_._2 <= 2))
    assert(!offered.exists(_._1 == cradlePlains))

  // (d) role and Foundation state do not gate Travel.

  test("role and Foundation changes do not gate Travel"):
    val ready = passRuled(travelBoard(supply = 7))
    val destination = coast
    // No Table step reaches lineage roles or foundations.
    val citizen = ready.updateCampaign(_.copy(lineages = ready.game.campaign.lineages.map {
        case (id, lineage) => id -> lineage.copy(role = Role.Citizen)
      }))
    val altered = citizen.updateCampaign(campaign => campaign.copy(foundations =
      campaign.foundations.map { case (k, f) => k -> f.copy(face = FoundationFace.Altered) }))
    assert(TravelProcedure.build(catalog, altered, actor,
      Vector(DecisionOptionRef.Site(destination))).isRight)

  // (e) projection uses automatic powers; preview uses the selected vector.

  test("a player-selected power changes the cost only when it is selected"):
    val ready = passRuled(travelBoard(supply = 7))
    val destination = coast
    val surcharge = SurchargePower(PowerId("test.travel.surcharge"))
    val catalogued = WalkerPowers(powers.powers :+ surcharge)

    assertEquals(simulate(ready, destination, using =
      WalkerPowers.selected(catalogued, Vector.empty)), Some(1),
      "an unselected player-selected power is not offered to the walk")
    assertEquals(simulate(ready, destination, using =
      WalkerPowers.selected(catalogued, Vector(surcharge.id))), Some(3))

  // (f) Travel is one atomic StartWalker: no park, and replay reproduces it.

  test("Travel never parks and leaves no walker state behind"):
    val ready = passRuled(travelBoard(supply = 7))
    val rules = new OathRules(catalog, walkerPowerCatalog = powers)
    val parked = new ParkedDecisionAssertions(catalog, powers)
    val destination = coast
    val traveled = accepted(rules.startWalker(Ready(ready), ActionRef.Travel,
      actor, Vector.empty, Vector(DecisionOptionRef.Site(destination))))
    val after = Outcomes.ready(traveled.state)
    assertEquals(after.game.current.walkerPending, None)
    assertEquals(after.game.current.walkerProcedure, None)
    parked.assertResumed(traveled.state, Phase.Act, actor)

  test("the declared tree is a windowed cost sequence inside an eligibility root"):
    val ready = passRuled(travelBoard(supply = 7))
    val destination = coast
    val tree = accepted(TravelProcedure.build(catalog, ready, actor,
      Vector(DecisionOptionRef.Site(destination))))
    assertEquals(tree.window, Some(PowerWindow.TravelActionEligibility))
    val cost = tree.children.head
    assertEquals(cost.window, Some(PowerWindow.TravelCost))
    assertEquals(Operation.flatten(tree).size, 2)
    assert(Operation.flatten(tree).head.isInstanceOf[SpendSupply])
    assert(Operation.flatten(tree).last.isInstanceOf[Move])

  test("build rejects a missing, foreign or degenerate start argument"):
    val ready = travelBoard(supply = 7)
    val source = cradlePlains
    assert(TravelProcedure.build(catalog, ready, actor, Vector.empty).isLeft)
    assertEquals(rejected(TravelProcedure.build(catalog, ready, actor,
      Vector(DecisionOptionRef.Site(source)))),
      SameTravelSite(source))
    assert(rejected(TravelProcedure.build(catalog, ready, actor,
      Vector(DecisionOptionRef.Site(SiteId("missing")))))
      .isInstanceOf[SiteNotInPlay])

  /** A player-selected Transform that raises any Travel pay node by two.
    * Fixture-only: it exists so the suite can tell "offered" from "selected"
    * with a number, which no production Travel power can do today.
    */
  private final case class SurchargePower(id: PowerId)
      extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.Banner("test")
    override def resolution: PowerResolution = PowerResolution.PlayerSelected
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
        operations.map {
          case SpendSupply(player, amount, _) if player == ctx.activePlayer =>
            SpendSupply(player, amount + 2)
          case other => other
        })))
