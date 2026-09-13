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

  test("procedure references form three families with keys unique across all") {
    assertEquals(ActionRef.all.map(_.key),
      Vector("recover", "forge", "travel", "take-wealth"))
    assertEquals(PhaseTransitionRef.all.map(_.key), Vector("end-wake"))
    assertEquals(ProcedureRef.all.map(_.key).distinct.size,
      ProcedureRef.all.size)
    assertEquals(StartableRef.fromKey("end-wake"),
      Some(PhaseTransitionRef.EndWake))
    assertEquals(ActionRef.fromKey("end-wake"), None)
    assertEquals(ProcedureRef.fromFamilyKey("action", "end-wake"), None)
    assertEquals(ProcedureRef.fromFamilyKey("phase-transition", "end-wake"),
      Some(PhaseTransitionRef.EndWake))
    assertEquals(ProcedureRef.fromFamilyKey("triggered", "recover"), None)
    assertEquals(TriggeredProcedureRef.all, Vector(TriggeredProcedureRef.Oathkeeper))
    // A client names a procedure by key alone; the triggered key must not resolve.
    assertEquals(StartableRef.fromKey("oathkeeper"), None)
  }
}
