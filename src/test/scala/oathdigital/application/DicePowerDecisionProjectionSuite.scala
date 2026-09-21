package oathdigital.application

import oathdigital.gameplay.powers.PowerFixture._
import oathdigital.gameplay.powers.action.{GamblingHall, PaidActionHarness}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** The parked decisions of the slice 1b powers reach their owner with every
  * option presented, and reach nobody else. A projection is dropped whole when
  * one option cannot be presented, so this is what proves a facedown relic in
  * the owner's own play area is nameable to the owner.
  */
class DicePowerDecisionProjectionSuite extends munit.FunSuite {
  import PaidActionHarness._

  private val projector = new WalkerDecisionProjector(catalog,
    new GamePresentationProjector(catalog))

  private def owner(state: ReadyGame) =
    projector.project(ScopedProjectionContext(state, Some(actor)))
  private def other(state: ReadyGame) = projector.project(
    ScopedProjectionContext(state, Some(state.game.current.players.map(_.player)
      .find(_ != actor).get)))

  test("Gambling Hall offers the owner all six favor banks") {
    val hall = DenizenId("93")
    val ready0 = act(withBoard(atHome(base, hall))(_.copy(favor = 3)))
    val rules0 = rules(defenseDice(DefenseDieFace.OneShield,
      DefenseDieFace.OneShield, DefenseDieFace.TwoShields,
      DefenseDieFace.Blank))
    val parked = ready(use(rules0, ready0, GamblingHall.id,
      DecisionOptionRef.Denizen(hall)).toOption.get.state)
    val projection = owner(parked).get
    assertEquals(projection.decisionId, GamblingHall.decisionId)
    assertEquals(projection.query.get.options.map(_.kind).distinct,
      Vector("favor-bank"))
    assertEquals(projection.query.get.options.size, Suit.all.size)
    assertEquals(other(parked), None)
  }
}
