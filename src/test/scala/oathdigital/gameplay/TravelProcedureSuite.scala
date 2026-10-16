package oathdigital.gameplay

import oathdigital.testkit.MenuPlay
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.travel.TravelFixture.destinationNotOffered
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Transform}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation._
import oathdigital.gameplay.walker.{WalkerPowers, WalkerSimulation}
import oathdigital.gameplay.walker.WalkerSimulation.PreviewedOption
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

  /** What a Travel to `destination` would spend, from the same preview the
    * parked decision reads; `None` when the preview refuses it. */
  private def simulate(ready: ReadyGame, destination: SiteId,
      using: WalkerPowers = powers): Option[Int] =
    previewedCosts(ready, using).get(destination)

  /** Every destination the preview accepts, with the Supply it spends. */
  private def previewedCosts(ready: ReadyGame, using: WalkerPowers = powers)
      : Map[SiteId, Int] =
    TravelProcedure.afterModifiers(catalog, ready, actor)
      .flatMap(WalkerSimulation.preview(_, ready, using))
      .getOrElse(Vector.empty).collect {
        case PreviewedOption(DecisionOption.Site(DecisionOptionRef.Site(site)),
            Right(outcome)) => site -> outcome.operations.collect {
          case SpendSupply(player, amount, _) if player == actor => amount }.sum
      }.toMap

  private def travelled(ready: ReadyGame, destination: SiteId)
      : Either[OathViolation, OathTransition] =
    TravelPlay.travelled(new OathRules(catalog, walkerPowerCatalog = powers),
      Ready(ready), actor, destination)

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
    Vector(coast, mountain, island).foreach:
      destination =>
        val expected = present(simulate(ready, destination), "a cost")
        val traveled = accepted(travelled(ready, destination))
        val after = Outcomes.ready(traveled.state)
        assertEquals(Look(after).pawn(actor), destination)
        assertEquals(Look(after).supply(actor), 7 - expected,
          s"travel to ${destination.value}")

  // (b) Narrow Pass removes the candidate and rejects a forged command.

  test("Narrow Pass removes the candidate and rejects a forged command"):
    val ready = travelBoard(supply = 7)
    val destination = provincePlains
    assertEquals(simulate(ready, destination), None)
    assertEquals(rejected(travelled(ready, destination)),
      destinationNotOffered)

  test("a pass ruled by the actor blocks nothing, and a coast route passes it"):
    val ruled = passRuled(travelBoard(supply = 7))
    val destination = provincePlains
    // The Cradle to the Provinces costs 2; the pass adds nothing.
    assertEquals(simulate(ruled, destination), Some(2))
    // A coast route is exempt from the pass even while a bandit holds it.
    assertEquals(simulate(travelBoard(supply = 7, source = coast), island),
      Some(1))

  // (c) zero Supply is not a build gate, but simulation and execution refuse.

  test("zero Supply passes the semantic build and fails at the pay node"):
    val ready = passRuled(travelBoard(supply = 0))
    val destination = coast
    // Supply is owned by the transformed SpendSupply, not a build gate.
    accepted(TravelProcedure.build(catalog, ready, actor, Vector.empty))
    assertEquals(simulate(ready, destination), None)
    assertEquals(rejected(travelled(ready, destination)),
      CoreOperationRejected("insufficient-supply",
        "a supply spend of 1 exceeds the 0 available"))

  test("simulation omits every destination the actor cannot afford"):
    val ready = passRuled(travelBoard(supply = 2))
    val offered = previewedCosts(ready)
    // Only the destinations costing 2 or less remain: the coast in the
    // Cradle, and the pass and Dunes in the Provinces.
    assertEquals(offered, Map(coast -> 1, pass -> 2, provincePlains -> 2))

  // (d) role and Foundation state do not gate Travel.

  test("role and Foundation changes do not gate Travel"):
    val ready = passRuled(travelBoard(supply = 7))
    // No Table step reaches lineage roles or foundations.
    val citizen = ready.updateCampaign(_.copy(lineages = ready.game.campaign.lineages.map {
        case (id, lineage) => id -> lineage.copy(role = Role.Citizen)
      }))
    val altered = citizen.updateCampaign(campaign => campaign.copy(foundations =
      campaign.foundations.map { case (k, f) => k -> f.copy(face = FoundationFace.Altered) }))
    accepted(TravelProcedure.build(catalog, altered, actor, Vector.empty))

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

  // (f) Travel parks on its destination, and replay reproduces the walk.

  test("a Travel parks on its destination and completes on the answer"):
    val ready = passRuled(travelBoard(supply = 7))
    val rules = new OathRules(catalog, walkerPowerCatalog = powers)
    val started = accepted(rules.startWalker(Ready(ready), ActionRef.Travel,
      actor, Vector.empty))
    val parked = Outcomes.ready(started.state)
    assertEquals(parked.game.current.walkerProcedure, Some(ActionRef.Travel))
    assertEquals(parked.game.current.walkerStartFacts,
      Vector(DecisionOptionRef.Site(cradlePlains)))
    assertEquals(Look(parked).supply(actor), 7)
    val done = accepted(rules.resolveWalker(started.state, actor,
      TravelProcedure.destinationDecisionId, TravelPlay.destination(coast)))
    val after = Outcomes.ready(done.state)
    assertEquals(Look(after).pawn(actor), coast)
    assertEquals(MenuPlay.procedureBeyondMenu(after), None)
    assertEquals(after.game.current.walkerStartFacts, Vector.empty)

  test("the destination offers every in-play site but the pawn's"):
    val ready = travelBoard(supply = 7)
    val tree = accepted(TravelProcedure.build(catalog, ready, actor,
      Vector.empty))
    // The modifier decision comes first; the destination opens what follows.
    val offered = tree.children(1).children.head match
      case Decide(TravelProcedure.destinationDecisionId, `actor`,
          DecisionQuery.ChooseOne(options, Some("Choose a Travel destination"),
            Some("Travel to")),
          _, _, _, _) => options.map(_.ref)
      case other => fail(s"not the destination decision: $other")
    assertEquals(offered, ready.game.current.map.inPlay
      .filter(_ != cradlePlains).map(DecisionOptionRef.Site(_)))

  test("a resume reads the source from the start fact, not the pawn"):
    val ready = travelBoard(supply = 7)
    val moved = ready.updateCurrent(current => current.copy(
      walkerStartFacts = Vector(DecisionOptionRef.Site(cradlePlains)),
      players = current.players.map(player =>
        if player.player == actor then player.copy(pawnSite = Some(coast))
        else player)))
    val rebuilt = accepted(TravelProcedure.rebuild(catalog, moved, actor,
      Vector.empty))
    val pending = PendingTree(Vector("1", "1"), Vector(Answered(
      TravelProcedure.destinationDecisionId, TravelPlay.destination(coast),
      actor)))
    val selected = rebuilt.children(1).children(1) match
      case branch: Branch => branch.select(moved, pending)
      case other => fail(s"not the route branch: $other")
    assertEquals(selected.flatMap(Operation.flatten).collect {
      case Move(Piece.Pawn(_), PositionedLocation(Location.Site(from), _, _),
          _, _, _) => from }, Vector(cradlePlains))

  test("a start that names a selection is refused, and so is a resume " +
      "without its start fact"):
    val ready = travelBoard(supply = 7)
    assertEquals(rejected(TravelProcedure.build(catalog, ready, actor,
      Vector(DecisionOptionRef.Site(coast)))),
      InvalidEventOrder("travel takes no start selection"))
    assertEquals(rejected(TravelProcedure.rebuild(catalog, ready, actor,
      Vector.empty)), InvalidEventOrder(
      "a resumed Travel needs its source site as its one start fact"))

  test("a start with no destination the actor can afford is refused"):
    val broke = passRuled(travelBoard(supply = 0))
    val rules = new OathRules(catalog, walkerPowerCatalog = powers)
    assertEquals(rejected(rules.startWalker(Ready(broke), ActionRef.Travel,
      actor, Vector.empty)), CoreOperationRejected("insufficient-supply",
      "a supply spend of 1 exceeds the 0 available"))

  /** Stub of ContributingPower: raises any Travel pay node by two,
    * player-selected. It exists so the suite can tell "offered" from
    * "selected" with a number, which no production Travel power can do
    * today.
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
