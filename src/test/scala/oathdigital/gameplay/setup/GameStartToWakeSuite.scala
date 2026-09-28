package oathdigital.gameplay.setup

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.Situation

class GameStartToWakeSuite extends munit.FunSuite:
  private val catalog = FirstGameSetupFixture.catalog
  private val rules = new OathRules(catalog)
  private val parked = new ParkedDecisionAssertions(catalog)
  private val chronicle = FirstGameSetupFixture.chronicle
  private val orders = FirstGameSetupFixture.orders

  test("beginGame parks on the first player's pawn-placement decision"):
    val transition = rules.beginGame(OathState.NoGame, chronicle, orders)
      .toOption.get
    parked.assertParked(transition.state, TriggeredProcedureRef.Setup,
      SetupProcedure.pawnDecisionId(orders.firstPlayer), orders.firstPlayer)

  test("driving every player's two decisions starts the first turn with the " +
      "recorded seating"):
    val ready = Situation.wake(Situation.rules(catalog), chronicle, orders)
      .ready

    // The first Wake has nothing to decide, so it has already ended.
    parked.assertResumed(Ready(ready), Phase.Act, orders.firstPlayer)
    assertEquals(ready.game.current.players.count(_.pawnSite.nonEmpty),
      orders.participants.size)
    assertEquals(ready.game.current.players.count(_.advisers.size == 1),
      orders.participants.size)

  test("replaying the recorded events reproduces the same Ready state with no RNG"):
    val transition = rules.beginGame(OathState.NoGame, chronicle, orders)
      .toOption.get
    val replayed = transition.events.foldLeft[Either[OathViolation, OathState]](
      Right(OathState.NoGame)):
      case (Right(state), event) => rules.evolve(state, event)
      case (left, _) => left
    assertEquals(replayed, Right(transition.state))
