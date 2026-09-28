package oathdigital.gameplay

import oathdigital.model._
import oathdigital.model.TestGameFixtures._

class WalkerStateSuite extends munit.FunSuite:
  private val actor = playerId

  test("a Repeat composite flattens to its body's leaves regardless of guard"):
    // Guard is evaluated by the walker only; the accessor always descends.
    val body = PayCost(actor, Location.OnCard(DenizenId("pay:target")),
      Cost(favor = 1, secret = 1))
    val repeat = Repeat((_: ReadyGame, _: PendingTree) => false, body)
    assertEquals(Operation.flatten(repeat), Operation.flatten(body))
    assertEquals(Operation.flatten(repeat).size, 2)
