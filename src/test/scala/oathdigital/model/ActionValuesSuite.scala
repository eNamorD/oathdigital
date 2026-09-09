package oathdigital.model

/** [[DefenseDieFace.score]] is the only oracle every Recover-scoring caller
  * defers to now that the legacy `RecoverSuite` (Task 9b) is gone -- these
  * two literal assertions are the property's last independent check, so a
  * regression here would otherwise go unnoticed by every surviving caller.
  */
class ActionValuesSuite extends munit.FunSuite {
  test("defense faces accumulate shields then apply every doubler") {
    assertEquals(DefenseDieFace.score(Vector(DefenseDieFace.OneShield,
      DefenseDieFace.TwoShields, DefenseDieFace.Doubler,
      DefenseDieFace.Doubler)), 12)
    assertEquals(DefenseDieFace.score(Vector(DefenseDieFace.Blank,
      DefenseDieFace.Doubler)), 0)
  }
}
