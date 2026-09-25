package oathdigital.gameplay

import oathdigital.gameplay.setup.FirstGameSetupFixture.{catalog, freshReady}
import oathdigital.model.OathState.Ready
import oathdigital.model.{OathViolation, WalkerEvent}

/** `WalkerEvent` is an open family, so `OathRules.evolve` cannot match it
  * exhaustively. A walker event type it does not know must be rejected with
  * a typed violation, not a `MatchError`.
  */
class OathRulesEvolveSuite extends munit.FunSuite:
  private case object UnregisteredWalkerEvent extends WalkerEvent

  test("an unregistered walker event evolves to a typed violation"):
    val result = new OathRules(catalog).evolve(Ready(freshReady), UnregisteredWalkerEvent)
    assertEquals(result, Left(OathViolation.InvalidEventOrder(
      "unsupported walker event: UnregisteredWalkerEvent")))
