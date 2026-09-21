package oathdigital.application

import oathdigital.gameplay.{EconomyFixture, OathRules}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.DecisionOptionProjection

class EconomyProjectionSuite extends munit.FunSuite {
  import EconomyFixture._

  private val rules = new OathRules(catalog)
  private val projector = new GameProjector(catalog)
  private val favor = Vector[DecisionOptionRef](DecisionOptionRef.Button("favor"))
  private val secret = Vector[DecisionOptionRef](DecisionOptionRef.Button("secret"))

  private def controls(ready: ReadyGame, viewer: PlayerId): Vector[String] =
    projector.project("economy", LoadedGame(Ready(ready), 4), viewer).legalControls

  private def parkedOptions(ready: ReadyGame, action: StartableRef,
      args: Vector[DecisionOptionRef]): Vector[DecisionOptionProjection] = {
    val actor = player(ready).player
    val started = rules.startWalker(Ready(ready), action, actor, startArgs = args)
      .getOrElse(fail("the action must start"))
    projector.project("economy", LoadedGame(started.state, 5), actor)
      .walkerDecision.flatMap(_.query).map(_.options)
      .getOrElse(fail("the parked decision must project a query"))
  }

  test("the active player is offered Muster and both Trades, and nobody else") {
    val board = act()
    val actor = player(board).player
    val other = board.game.current.players.map(_.player).find(_ != actor).get
    assert(Vector("beginMuster", "beginTradeFavor", "beginTradeSecret")
      .forall(controls(board, actor).contains))
    assert(!controls(board, other).exists(_.startsWith("beginTrade")))
    assert(!controls(board, other).contains("beginMuster"))
  }

  test("a control is offered only while a source survives the preview") {
    val oneFavor = act(favor = 1)
    val actor = player(oneFavor).player
    assert(controls(oneFavor, actor).contains("beginMuster"))
    assert(controls(oneFavor, actor).contains("beginTradeFavor"))
    assert(!controls(oneFavor, actor).contains("beginTradeSecret"))
    val noSupply = act(supply = 0)
    assert(!controls(noSupply, player(noSupply).player).exists(control =>
      control == "beginMuster" || control.startsWith("beginTrade")))
    val noSecrets = act(secrets = 0)
    assert(!controls(noSecrets, player(noSecrets).player)
      .contains("beginTradeFavor"))
  }

  test("a parked Muster shows its source with what answering costs and yields") {
    val options = parkedOptions(act(advisers = Vector(matchingAdviser)),
      ActionRef.Muster, Vector.empty)
    assertEquals(options.map(option => (option.kind, option.id)),
      Vector(("denizen", plainId.value)))
    assertEquals(options.map(_.details),
      Vector(Vector("1 Supply", "+2 warbands")))
    assert(options.head.label.nonEmpty)
  }

  test("a parked Trade shows its yield in the resource it gains") {
    val board = act(advisers = Vector(matchingAdviser), bank = 1)
    assertEquals(parkedOptions(board, ActionRef.Trade, favor).map(_.details),
      Vector(Vector("1 Supply", "+1 favor")))
    assertEquals(parkedOptions(board, ActionRef.Trade, secret).map(_.details),
      Vector(Vector("1 Supply", "+1 secrets")))
  }

  test("a gain that shrinks to nothing shows no gain line") {
    assertEquals(parkedOptions(act(boardWarbands = 14), ActionRef.Muster,
      Vector.empty).map(_.details), Vector(Vector("1 Supply")))
  }

  test("an option the preview drops is not shown") {
    val board = act(advisers = Vector(matchingAdviser))
    val actor = player(board).player
    val started = rules.startWalker(Ready(board), ActionRef.Muster, actor)
      .getOrElse(fail("the action must start"))
    val parked = started.state.asInstanceOf[Ready].value
    val context = ScopedProjectionContext(parked, Some(actor))
    val withPower = new WalkerDecisionProjector(catalog,
      new GamePresentationProjector(catalog), WalkerPowers(Vector(
        AddAdviserSource(PowerId("test.add-adviser-source")))))
    val options = withPower.project(context).flatMap(_.query).map(_.options)
      .getOrElse(fail("the decision must project"))
    assertEquals(options.map(_.id), Vector(plainId.value))
  }

  test("operation details word the recorded Supply and gains") {
    val actor = PlayerId("p")
    assertEquals(OperationDetails.of(Vector(
      PayCost(actor, Location.OnCard(plainId), Cost(favor = 1)),
      SpendSupply(actor, 1), Gain.Warbands(actor, ForceKind.Imperial, 2),
      Gain.Favor(actor, Suit.Hearth, 3), Gain.Secrets(actor, 1),
      GainSupply(actor, 2))),
      Vector("1 Supply", "+2 warbands", "+3 favor", "+1 secrets", "+2 Supply"))
  }

  test("operation details read the moves a gain records and ignore a payment's") {
    val actor = PlayerId("p")
    val bank = PositionedLocation(Location.FavorBank(Suit.Hearth))
    val area = PositionedLocation(Location.PlayArea(actor))
    assertEquals(OperationDetails.of(Vector(
      Move(Piece.Favor(2), area, PositionedLocation(Location.OnCard(plainId))),
      Move(Piece.Favor(3), bank, area),
      Move(Piece.Warbands(ForceKind.Imperial, 1),
        PositionedLocation(Location.WarbandBank(ForceKind.Imperial)), area))),
      Vector("+3 favor", "+1 warbands"))
  }
}
