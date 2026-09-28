package oathdigital.application

import oathdigital.gameplay.{EconomyFixture, OathRules}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.DecisionOptionProjection
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

class EconomyProjectionSuite extends munit.FunSuite:
  import EconomyFixture.AddAdviserSource

  private val rules = new OathRules(catalog)
  private val projector = new GameProjector(catalog)
  private val favor = Vector[DecisionOptionRef](DecisionOptionRef.Button("favor"))
  private val secret = Vector[DecisionOptionRef](DecisionOptionRef.Button("secret"))
  private val alchemist = CatalogNames.denizen("Alchemist")
  private val magiciansCode = CatalogNames.denizen("Magician's Code")

  /** p1 stands at Ancient City, which holds the token-free Alchemist
    * (Arcane) and nothing else. p1 has 4 favor and 2 faceup secrets, and
    * the Arcane bank holds 5. */
  private def atAlchemist: Table = Table.start
    .denizen(alchemist, at = Table.homeOf(p1))
    .favor(p1, 4).secrets(p1, faceUp = 2)
    .bankFavor(Suit.Arcane, 5)

  /** Magician's Code is Arcane, like Alchemist. */
  private def withMatchingAdviser: Table = atAlchemist.adviser(p1, magiciansCode)

  private def controls(ready: ReadyGame, viewer: PlayerId): Vector[String] =
    projector.project("economy", LoadedGame(Ready(ready), 4), viewer).legalControls

  private def parkedOptions(ready: ReadyGame, action: StartableRef,
      args: Vector[DecisionOptionRef]): Vector[DecisionOptionProjection] =
    val started = rules.startWalker(Ready(ready), action, p1, startArgs = args)
      .getOrElse(fail("the action must start"))
    projector.project("economy", LoadedGame(started.state, 5), p1)
      .walkerDecision.flatMap(_.query).map(_.offeredOptions)
      .getOrElse(fail("the parked decision must project a query"))

  test("the active player is offered Muster and both Trades, and nobody else"):
    val board = atAlchemist.ready
    assert(Vector("beginMuster", "beginTradeFavor", "beginTradeSecret")
      .forall(controls(board, p1).contains))
    assert(!controls(board, p2).exists(_.startsWith("beginTrade")))
    assert(!controls(board, p2).contains("beginMuster"))

  test("a control is offered only while a source survives the preview"):
    val oneFavor = atAlchemist.favor(p1, 1).ready
    assert(controls(oneFavor, p1).contains("beginMuster"))
    assert(controls(oneFavor, p1).contains("beginTradeFavor"))
    assert(!controls(oneFavor, p1).contains("beginTradeSecret"))
    assert(!controls(atAlchemist.supply(p1, 0).ready, p1).exists(control =>
      control == "beginMuster" || control.startsWith("beginTrade")))
    assert(!controls(atAlchemist.secrets(p1, faceUp = 0).ready, p1)
      .contains("beginTradeFavor"))

  test("a parked Muster shows its source with what answering costs and yields"):
    val options = parkedOptions(withMatchingAdviser.ready,
      ActionRef.Muster, Vector.empty)
    assertEquals(options.map(option => (option.kind, option.id)),
      Vector(("denizen", alchemist.value)))
    assertEquals(options.map(_.details),
      Vector(Vector("1 Supply", "+2 warbands")))
    assert(options.head.label.nonEmpty)

  test("a parked Trade shows its yield in the resource it gains"):
    val board = withMatchingAdviser.bankFavor(Suit.Arcane, 1).ready
    assertEquals(parkedOptions(board, ActionRef.Trade, favor).map(_.details),
      Vector(Vector("1 Supply", "+1 favor")))
    assertEquals(parkedOptions(board, ActionRef.Trade, secret).map(_.details),
      Vector(Vector("1 Supply", "+1 secrets")))

  test("a gain that shrinks to nothing shows no gain line"):
    // All 14 of p1's warbands are on the board, so Muster can gain none.
    assertEquals(parkedOptions(atAlchemist.warbands(p1, 14).ready, ActionRef.Muster,
      Vector.empty).map(_.details), Vector(Vector("1 Supply")))

  test("an option the preview drops is not shown"):
    val board = withMatchingAdviser.ready
    val actor = p1
    val started = rules.startWalker(Ready(board), ActionRef.Muster, actor)
      .getOrElse(fail("the action must start"))
    val parked = started.state.asInstanceOf[Ready].value
    val context = ScopedProjectionContext(parked, Some(actor))
    val withPower = new WalkerDecisionProjector(catalog,
      new GamePresentationProjector(catalog), WalkerPowers(Vector(
        AddAdviserSource(PowerId("test.add-adviser-source"), magiciansCode))))
    val options = withPower.project(context).flatMap(_.query)
      .map(_.offeredOptions)
      .getOrElse(fail("the decision must project"))
    assertEquals(options.map(_.id), Vector(alchemist.value))

  test("operation details word the recorded Supply and gains"):
    val actor = PlayerId("p")
    assertEquals(OperationDetails.of(Vector(
      PayCost(actor, Location.OnCard(alchemist), Cost(favor = 1)),
      SpendSupply(actor, 1), Gain.Warbands(actor, ForceKind.Imperial, 2),
      Gain.Favor(actor, Suit.Hearth, 3), Gain.Secrets(actor, 1),
      GainSupply(actor, 2))),
      Vector("1 Supply", "+2 warbands", "+3 favor", "+1 secrets", "+2 Supply"))

  test("operation details read the moves a gain records and ignore a payment's"):
    val actor = PlayerId("p")
    val bank = PositionedLocation(Location.FavorBank(Suit.Hearth))
    val area = PositionedLocation(Location.PlayArea(actor))
    assertEquals(OperationDetails.of(Vector(
      Move(Piece.Favor(2), area, PositionedLocation(Location.OnCard(alchemist))),
      Move(Piece.Favor(3), bank, area),
      Move(Piece.Warbands(ForceKind.Imperial, 1),
        PositionedLocation(Location.WarbandBank(ForceKind.Imperial)), area))),
      Vector("+3 favor", "+1 warbands"))
