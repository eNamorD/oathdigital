package oathdigital.model

class ProcedureRefSuite extends munit.FunSuite {
  private val use = ActionRef.UsePower(PowerId("denizen.silver-tongue"))

  test("a use-power reference parses from its key under the action family only") {
    assertEquals(use.key, "use-power:denizen.silver-tongue")
    assertEquals(ActionRef.fromKey(use.key), Some(use))
    assertEquals(StartableRef.fromKey(use.key), Some(use))
    assertEquals(ProcedureRef.fromFamilyKey("action", use.key), Some(use))
    assertEquals(ProcedureRef.fromFamilyKey("phase-transition", use.key), None)
    assertEquals(ActionRef.fromKey("use-power:"), None)
    assertEquals(ProcedureRef.fromFamilyKey("phase-transition", "finish-rest"),
      Some(PhaseTransitionRef.FinishRest))
  }
}
