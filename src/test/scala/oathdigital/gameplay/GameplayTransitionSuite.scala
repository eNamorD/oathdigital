package oathdigital.gameplay

import oathdigital.model._

class GameplayTransitionSuite extends munit.FunSuite:
  test("shared transition helper applies ordered events and stops at failure"):
    val events = Vector(OathEvent.BanditsRefilled(Vector.empty),
      OathEvent.BanditsRefilled(Vector.empty))
    val violation = OathViolation.InvalidEventOrder("second event rejected")
    var applied = 0
    val result = GameplayTransition(OathState.NoGame, events) { (state, _) =>
      applied += 1
      if applied == 1 then Right(state) else Left(violation)
    }
    assertEquals(result, Left(violation))
    assertEquals(applied, 2)
