package oathdigital.gameplay

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.operations.{AdjustSupply, Location, Move, Operation,
  Piece, PositionedLocation, Sequence}
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  PowerCtx, PowerResolution, PowerWindow, Transform}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.OathViolation._
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._

/** Travel on the generic walker (batch 1, Task 5).
  *
  * The board below is `TravelSuite`'s: it pins coast, island, mountain and
  * pass at known sites so every parity number this suite asserts is the same
  * number the retired terrain fold produced for the same route.
  * Parity is the acceptance criterion, not plausibility.
  */
class TravelProcedureSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)
  private val powers: WalkerPowers = WalkerPowerCatalog.default(catalog)

  private def site(power: String): SiteId = catalog.sites.find(
    _.handlers.exists(_.endsWith(s".$power"))).get.id
  private val plains = catalog.sites.filter(
    _.handlers.exists(_.endsWith(".plains"))).map(_.id)
  private val coast = site("coast")
  private val island = site("island")
  private val mountain = site("mountain")
  private val pass = site("pass")

  private def act(
      source: SiteId = plains.head,
      supply: Int = 7,
      passForces: SiteForces = SiteForces.Occupied(ForceKind.Bandit, 1)
  ): ReadyGame = {
    val Ready(initial) = execute(setup)._1: @unchecked
    val chosen = Vector(source, coast, plains(1), mountain, pass, plains(2),
      island, plains(3))
    val ids = (chosen.distinct ++ catalog.sites.map(_.id)
      .filterNot(chosen.contains)).take(8)
    val states = ids.map { id =>
      val definition = catalog.sites.find(_.id == id).get
      id -> SiteState(
        if (id == pass) passForces
        else if (definition.capacity == 0) SiteForces.Empty
        else SiteForces.Occupied(ForceKind.Bandit, definition.capacity),
        Vector.empty, Vector.empty, definition.startingResources)
    }.toMap
    val active = initial.game.current.turn.activePlayer
    val players = initial.game.current.players.zipWithIndex.map {
      case (player, index) => player.copy(
        pawnSite = Some(if (player.player == active) source else ids(index + 1)),
        board = if (player.player == active)
          player.board.copy(supply = SupplyTrack(supply)) else player.board)
    }
    initial.copy(game = initial.game.copy(current = initial.game.current.copy(
      players = players,
      map = MapState(ids.take(2), ids.slice(2, 5), ids.slice(5, 8), states),
      turn = initial.game.current.turn.copy(phase = Phase.Act))))
  }

  private def active(ready: ReadyGame): PlayerState =
    ready.game.current.players.find(
      _.player == ready.game.current.turn.activePlayer).get

  /** The pass ruled by the actor, so a cross-region route past it is legal. */
  private def passRuled(ready: ReadyGame): ReadyGame = {
    val lineage = active(ready).lineage
    val ruled = ready.game.current.map.sites(pass).copy(
      forces = SiteForces.Occupied(ForceKind.Exile(lineage), 1))
    ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      map = ready.game.current.map.copy(sites =
        ready.game.current.map.sites.updated(pass, ruled)))))
  }

  private def simulate(ready: ReadyGame, destination: SiteId,
      using: WalkerPowers = powers): Option[Int] =
    TravelProcedure.candidates(catalog, ready, active(ready).player, using)
      .collectFirst { case (site, cost) if site == destination => cost }

  // (a) candidate simulation matches StartWalker for every terrain case.

  test("simulated candidate cost equals the printed table on a plain route") {
    val ready = passRuled(act())
    val map = ready.game.current.map
    val source = active(ready).pawnSite.get
    assertEquals(simulate(ready, map.cradle.find(_ != source).get), Some(1))
    assertEquals(simulate(ready, map.provinces.find(_ != mountain).get), Some(2))
    assertEquals(simulate(ready, map.hinterland.find(_ != island).get), Some(4))
  }

  test("Mountain adds one and Island adds two to the printed base") {
    val ready = passRuled(act())
    assertEquals(simulate(ready, mountain), Some(3))
    assertEquals(simulate(ready, island), Some(6))
  }

  test("a coast route replaces the cost with one") {
    assertEquals(simulate(act(source = coast), island), Some(1))
  }

  test("a coast route ignores the destination add rather than stacking it") {
    // Fair Isle carries both a coast and an island power: on a coast route
    // the replace lands and the +2 is dropped, so 1 rather than 3.
    val ready = act(source = coast)
    assertEquals(simulate(ready, island), Some(1))
    assertNotEquals(simulate(ready, island), Some(3))
  }

  test("a non-coast route from a coastal site keeps the printed base") {
    val ready = passRuled(act(source = coast))
    val plainProvince = ready.game.current.map.provinces
      .find(id => id != mountain && id != pass).get
    assertEquals(simulate(ready, plainProvince), Some(2))
  }

  test("StartWalker spends exactly the cost the candidate simulation showed") {
    val ready = passRuled(act())
    val rules = new OathRules(catalog, walkerPowerCatalog = powers)
    Vector(ready.game.current.map.cradle.find(
      _ != active(ready).pawnSite.get).get, mountain, island).foreach {
      destination =>
        val expected = simulate(ready, destination).get
        val accepted = rules.startWalker(Ready(ready), ActionRef.Travel,
          active(ready).player, Vector.empty,
          Vector(DecisionOptionRef.Site(destination))).toOption.get
        val Ready(after) = accepted.state: @unchecked
        assertEquals(active(after).pawnSite, Some(destination))
        assertEquals(active(after).board.supply.supply, 7 - expected,
          s"travel to ${destination.value}")
    }
  }

  // (b) Narrow Pass removes the candidate and rejects a forged command.

  test("Narrow Pass removes the candidate and rejects a forged command") {
    val ready = act()
    val destination = ready.game.current.map.provinces
      .find(id => id != pass && id != mountain).get
    val rules = new OathRules(catalog, walkerPowerCatalog = powers)
    assertEquals(simulate(ready, destination), None)
    assertEquals(rules.startWalker(Ready(ready), ActionRef.Travel,
      active(ready).player, Vector.empty,
      Vector(DecisionOptionRef.Site(destination))).left.toOption.get,
      TravelPassBlocked(pass, destination))
  }

  test("a pass ruled by the actor blocks nothing, and a coast route passes it") {
    val ruled = passRuled(act())
    val destination = ruled.game.current.map.provinces
      .find(id => id != pass && id != mountain).get
    assert(simulate(ruled, destination).nonEmpty)
    // A coast route is exempt from the pass even while a bandit holds it.
    assert(simulate(act(source = coast), island).nonEmpty)
  }

  // (c) zero Supply is not a build gate, but simulation and execution refuse.

  test("zero Supply passes the semantic build and fails at the pay node") {
    val ready = passRuled(act(supply = 0))
    val actor = active(ready).player
    val destination = ready.game.current.map.cradle.find(
      _ != active(ready).pawnSite.get).get
    assert(TravelProcedure.build(catalog, ready, actor,
      Vector(DecisionOptionRef.Site(destination))).isRight,
      "supply is owned by the transformed AdjustSupply, not a build gate")
    assertEquals(simulate(ready, destination), None)

    val rules = new OathRules(catalog, walkerPowerCatalog = powers)
    val rejected = rules.startWalker(Ready(ready), ActionRef.Travel, actor,
      Vector.empty, Vector(DecisionOptionRef.Site(destination)))
    assert(rejected.isLeft)
    assertEquals(active(ready).pawnSite, Some(plains.head))
  }

  test("simulation omits every destination the actor cannot afford") {
    val ready = passRuled(act(supply = 2))
    val offered = TravelProcedure.candidates(catalog, ready,
      active(ready).player, powers)
    assert(offered.nonEmpty)
    assert(offered.forall(_._2 <= 2))
    assert(!offered.exists(_._1 == active(ready).pawnSite.get))
  }

  // (d) role and Foundation state do not gate Travel.

  test("role and Foundation changes do not gate Travel") {
    val ready = passRuled(act())
    val actor = active(ready).player
    val destination = ready.game.current.map.cradle.find(
      _ != active(ready).pawnSite.get).get
    val citizen = ready.copy(game = ready.game.copy(campaign =
      ready.game.campaign.copy(lineages = ready.game.campaign.lineages.map {
        case (id, lineage) => id -> lineage.copy(role = Role.Citizen)
      })))
    assert(TravelProcedure.build(catalog, citizen, actor,
      Vector(DecisionOptionRef.Site(destination))).isRight)
  }

  // (e) projection uses automatic powers; preview uses the selected vector.

  test("a player-selected power changes the cost only when it is selected") {
    val ready = passRuled(act())
    val actor = active(ready).player
    val destination = ready.game.current.map.cradle.find(
      _ != active(ready).pawnSite.get).get
    val surcharge = SurchargePower(PowerId("test.travel.surcharge"))
    val catalogued = WalkerPowers(powers.powers :+ surcharge)

    assertEquals(simulate(ready, destination, using =
      WalkerPowers.selected(catalogued, Vector.empty)), Some(1),
      "an unselected player-selected power is not offered to the walk")
    assertEquals(simulate(ready, destination, using =
      WalkerPowers.selected(catalogued, Vector(surcharge.id))), Some(3))
  }

  // (f) Travel is one atomic StartWalker: no park, and replay reproduces it.

  test("Travel never parks and leaves no walker state behind") {
    val ready = passRuled(act())
    val rules = new OathRules(catalog, walkerPowerCatalog = powers)
    val destination = ready.game.current.map.cradle.find(
      _ != active(ready).pawnSite.get).get
    val accepted = rules.startWalker(Ready(ready), ActionRef.Travel,
      active(ready).player, Vector.empty,
      Vector(DecisionOptionRef.Site(destination))).toOption.get
    val Ready(after) = accepted.state: @unchecked
    assertEquals(after.game.current.walkerPending, None)
    assertEquals(after.game.current.walkerAction, None)
    assertEquals(accepted.continue, OathContinue.ActActionSelection(
      active(ready).player))
    assertEquals(after.game.current.turn.phase, Phase.Act)
  }

  test("the declared tree is a windowed cost sequence inside an eligibility root") {
    val ready = passRuled(act())
    val destination = ready.game.current.map.cradle.find(
      _ != active(ready).pawnSite.get).get
    val tree = TravelProcedure.build(catalog, ready, active(ready).player,
      Vector(DecisionOptionRef.Site(destination))).toOption.get
    assertEquals(tree.window, Some(PowerWindow.TravelActionEligibility))
    val cost = tree.children.head
    assertEquals(cost.window, Some(PowerWindow.TravelCost))
    assertEquals(Operation.flatten(tree).size, 2)
    assert(Operation.flatten(tree).head.isInstanceOf[AdjustSupply])
    assert(Operation.flatten(tree).last.isInstanceOf[Move])
  }

  test("build rejects a missing, foreign or degenerate start argument") {
    val ready = act()
    val actor = active(ready).player
    val source = active(ready).pawnSite.get
    assert(TravelProcedure.build(catalog, ready, actor, Vector.empty).isLeft)
    assertEquals(TravelProcedure.build(catalog, ready, actor,
      Vector(DecisionOptionRef.Site(source))).left.toOption.get,
      SameTravelSite(source))
    assert(TravelProcedure.build(catalog, ready, actor,
      Vector(DecisionOptionRef.Site(SiteId("missing")))).left.toOption.get
      .isInstanceOf[SiteNotInPlay])
  }

  /** A player-selected Transform that raises any Travel pay node by two.
    * Fixture-only: it exists so the suite can tell "offered" from "selected"
    * with a number, which no production Travel power can do today.
    */
  private final case class SurchargePower(id: PowerId)
      extends ContributingPower {
    def source: RuleSourceRef = RuleSourceRef.Banner("test")
    override def resolution: PowerResolution = PowerResolution.PlayerSelected
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
        operations.map {
          case AdjustSupply(player, amount) if player == ctx.actor =>
            AdjustSupply(player, amount - 2)
          case other => other
        })))
  }
}
