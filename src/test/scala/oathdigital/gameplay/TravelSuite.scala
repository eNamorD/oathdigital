package oathdigital.gameplay

import oathdigital.gameplay.actions.{TravelCommand, TravelRules}
import oathdigital.gameplay.phases.WakeCommand

import oathdigital.model._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.OathContinue.ActActionSelection
import oathdigital.gameplay.setup.OathEvent.Traveled
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup.OathState.Ready
import oathdigital.gameplay.setup.OathViolation._

class TravelSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)

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
    val selected = (Vector(source, coast, plains(1), mountain, pass,
      plains(2), island, plains(3))).distinct ++
      catalog.sites.map(_.id).filterNot(Vector(source, coast, plains(1),
        mountain, pass, plains(2), island, plains(3)).contains)
    val ids = selected.take(8)
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

  test("region matrix and mandatory destination modifiers are exact") {
    val base = act()
    val basePlayer = active(base)
    val actorPass = base.game.current.map.sites(pass).copy(forces =
      SiteForces.Occupied(ForceKind.Exile(basePlayer.lineage), 1))
    val ready = base.copy(game = base.game.copy(current =
      base.game.current.copy(map = base.game.current.map.copy(sites =
        base.game.current.map.sites.updated(pass, actorPass)))))
    val player = active(ready)
    val map = ready.game.current.map
    def cost(destination: SiteId) = TravelRules.cost(
      catalog, ready, player, player.pawnSite.get, destination).toOption.get

    assertEquals(cost(map.cradle.find(_ != player.pawnSite.get).get), 1)
    assertEquals(cost(map.provinces.find(_ != mountain).get), 2)
    assertEquals(cost(map.hinterland.find(_ != island).get), 4)
    assertEquals(cost(mountain), 3)
    assertEquals(cost(island), 6)

    val provinceSource = ready.game.current.map.provinces.head
    val provinces = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(players = ready.game.current.players.map { value =>
        if (value.player == player.player) value.copy(pawnSite = Some(provinceSource))
        else value
      })))
    val provincePlayer = active(provinces)
    assertEquals(TravelRules.cost(catalog, provinces, provincePlayer,
      provincePlayer.pawnSite.get, provinces.game.current.map.cradle.head),
      Right(2))
    assertEquals(TravelRules.cost(catalog, provinces, provincePlayer,
      provincePlayer.pawnSite.get, provinces.game.current.map.hinterland.head),
      Right(2))

    val hinterSource = ready.game.current.map.hinterland.head
    val hinter = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(players = ready.game.current.players.map { value =>
        if (value.player == player.player) value.copy(pawnSite = Some(hinterSource))
        else value
      })))
    val hinterPlayer = active(hinter)
    assertEquals(TravelRules.cost(catalog, hinter, hinterPlayer, hinterSource,
      hinter.game.current.map.cradle.head), Right(4))
    assertEquals(TravelRules.cost(catalog, hinter, hinterPlayer, hinterSource,
      hinter.game.current.map.provinces.head), Right(2))
    assertEquals(TravelRules.cost(catalog, hinter, hinterPlayer, hinterSource,
      hinter.game.current.map.hinterland.last), Right(3))
  }

  test("Coast override costs one and ignores Island Mountain and Pass") {
    val ready = act(source = coast)
    val player = active(ready)
    assertEquals(TravelRules.cost(
      catalog, ready, player, coast, island), Right(1))
  }

  test("Pass permits itself and actor rule but blocks bandits and consent") {
    val blocked = act()
    val player = active(blocked)
    assert(TravelRules.cost(catalog, blocked, player,
      player.pawnSite.get, blocked.game.current.map.provinces.head)
      .left.toOption.get.isInstanceOf[TravelPassBlocked])
    assert(TravelRules.cost(catalog, blocked, player,
      player.pawnSite.get, pass).isRight)

    val ruled = act(passForces = SiteForces.Occupied(
      ForceKind.Exile(player.lineage), 1))
    val ruledPlayer = active(ruled)
    assert(TravelRules.cost(catalog, ruled, ruledPlayer,
      ruledPlayer.pawnSite.get, ruled.game.current.map.provinces.head).isRight)

    val other = blocked.game.current.players.find(_.player != player.player).get
    val consent = act(passForces = SiteForces.Occupied(
      ForceKind.Exile(other.lineage), 1))
    val consentPlayer = active(consent)
    assertEquals(TravelRules.cost(catalog, consent, consentPlayer,
      consentPlayer.pawnSite.get, consent.game.current.map.provinces.head)
      .left.toOption.get, TravelConsentUnsupported(pass, other.player))
  }

  test("Travel atomically spends Supply moves pawn and remains in Act") {
    val ready = act()
    val player = active(ready)
    val destination = ready.game.current.map.cradle.find(
      _ != player.pawnSite.get).get
    val before = ready.game.current
    val accepted = rules.handle(Ready(ready),
      TravelCommand.Travel(player.player, destination)).toOption.get
    val Ready(after) = accepted.state: @unchecked

    assertEquals(accepted.events, Vector(Traveled(
      player.player, player.pawnSite.get, destination, 1)))
    assertEquals(accepted.continue, ActActionSelection(player.player))
    assertEquals(after.game.current.turn.phase, Phase.Act)
    assertEquals(after.game.current.turn.usedPowers, before.turn.usedPowers)
    assertEquals(active(after).pawnSite, Some(destination))
    assertEquals(active(after).board.supply.supply, 6)
    assertEquals(after.game.current.map, before.map)
    assertEquals(after.game.current.players.filterNot(_.player == player.player),
      before.players.filterNot(_.player == player.player))

    val repeatedDestination = after.game.current.map.cradle.find(
      _ != destination).get
    val repeated = rules.handle(accepted.state,
      TravelCommand.Travel(player.player, repeatedDestination)).toOption.get
    val Ready(afterRepeated) = repeated.state: @unchecked
    assertEquals(afterRepeated.game.current.turn.phase, Phase.Act)
    assertEquals(active(afterRepeated).board.supply.supply, 5)
    assertEquals(rules.handle(repeated.state,
      WakeCommand.TakeWealth(player.player, WakeResource.Favor))
      .left.toOption.get, WrongPhase(Phase.Wake, Phase.Act))
  }

  test("Travel validates actor phase pending destination and Supply") {
    val ready = act(supply = 0)
    val player = active(ready)
    val destination = ready.game.current.map.cradle.find(
      _ != player.pawnSite.get).get
    assertEquals(rules.handle(Ready(ready), TravelCommand.Travel(
      player.player, destination)).left.toOption.get,
      InsufficientSupply(1, 0))
    assert(rules.handle(Ready(ready), TravelCommand.Travel(
      ready.game.current.players.find(_.player != player.player).get.player,
      destination)).left.toOption.get.isInstanceOf[WrongPlayer])
    assertEquals(TravelRules.cost(catalog, ready, player,
      player.pawnSite.get, player.pawnSite.get).left.toOption.get,
      SameTravelSite(player.pawnSite.get))
    assert(TravelRules.cost(catalog, ready, player,
      player.pawnSite.get, SiteId("missing")).left.toOption.get
      .isInstanceOf[SiteNotInPlay])

    val pending = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(pending = Some(PendingProcedure.Search(
        DecisionId("search"), player.player)))))
    assert(rules.handle(Ready(pending), TravelCommand.Travel(
      player.player, destination)).left.toOption.get
      .isInstanceOf[PendingProcedureBlocksAction])
  }

  test("replay rejects tampered source and cost") {
    val ready = act()
    val player = active(ready)
    val destination = ready.game.current.map.cradle.find(
      _ != player.pawnSite.get).get
    assert(rules.evolve(Ready(ready), Traveled(player.player,
      destination, player.pawnSite.get, 1)).left.toOption.get
      .isInstanceOf[TravelSourceMismatch])
    assert(rules.evolve(Ready(ready), Traveled(player.player,
      player.pawnSite.get, destination, 2)).left.toOption.get
      .isInstanceOf[TravelCostMismatch])
  }

  test("legal projection omits unaffordable Pass-consent and private routes") {
    val ready = act(supply = 2)
    val player = active(ready)
    val own = new oathdigital.application.GameProjector(catalog).project(
      "travel", oathdigital.application.LoadedGame(Ready(ready), 9),
      player.player)
    val other = ready.game.current.players.find(_.player != player.player).get
    val hidden = new oathdigital.application.GameProjector(catalog).project(
      "travel", oathdigital.application.LoadedGame(Ready(ready), 9),
      other.player)
    assert(own.legalTravelDestinations.nonEmpty)
    assert(own.legalTravelDestinations.forall(_.supplyCost <= 2))
    assert(!own.legalTravelDestinations.exists(_.siteId == player.pawnSite.get.value))
    assertEquals(hidden.legalTravelDestinations, Vector.empty)
  }

  test("unsupported modifier-bearing states fail conservatively") {
    val ready = act()
    val player = active(ready)
    val faceUp = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(players = ready.game.current.players.map { value =>
        if (value.player != player.player) value
        else value.copy(advisers = value.advisers.map {
          case DenizenState(id, _, tokens) =>
            DenizenState(id, Orientation.FaceUp, tokens)
          case other => other
        })
      })))
    assert(TravelRules.validateSupportedState(faceUp).left.toOption.get
      .isInstanceOf[UnsupportedTravelState])
  }
}
