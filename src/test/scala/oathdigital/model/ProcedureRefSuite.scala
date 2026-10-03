package oathdigital.model

class ProcedureRefSuite extends munit.FunSuite:
  private val use = ActionRef.UsePower(PowerId("denizen.silver-tongue"))

  test("a use-power reference parses from its key under the action family only"):
    assertEquals(use.key, "use-power:denizen.silver-tongue")
    assertEquals(ActionRef.fromKey(use.key), Some(use))
    assertEquals(StartableRef.fromKey(use.key), Some(use))
    assertEquals(ProcedureRef.fromFamilyKey("action", use.key), Some(use))
    assertEquals(ProcedureRef.fromFamilyKey("phase-transition", use.key), None)
    assertEquals(ActionRef.fromKey("use-power:"), None)

  test("procedure reference keys are listed per family and unique across " +
      "all of them"):
    assertEquals(ActionRef.all.map(_.key),
      Vector("search", "play-facedown-adviser", "recover", "forge",
        "travel", "take-wealth", "muster", "trade", "challenge",
        "place-banner-resource", "negotiation", "campaign"))
    assertEquals(PhaseTransitionRef.all.map(_.key),
      Vector("end-wake", "begin-rest", "finish-rest"))
    assertEquals(ProcedureRef.all.map(_.key).distinct.size,
      ProcedureRef.all.size)
    assertEquals(TriggeredProcedureRef.all,
      Vector(TriggeredProcedureRef.Oathkeeper, TriggeredProcedureRef.Setup,
        TriggeredProcedureRef.ForcedWake))

  test("a key resolves only under its own family"):
    // end-wake is a phase transition and recover an action: end-wake
    // resolves under its own family only, and finish-rest resolves there.
    assertEquals(StartableRef.fromKey("end-wake"),
      Some(PhaseTransitionRef.EndWake))
    assertEquals(ActionRef.fromKey("end-wake"), None)
    assertEquals(ProcedureRef.fromFamilyKey("action", "end-wake"), None)
    assertEquals(ProcedureRef.fromFamilyKey("phase-transition", "end-wake"),
      Some(PhaseTransitionRef.EndWake))
    assertEquals(ProcedureRef.fromFamilyKey("triggered", "recover"), None)
    assertEquals(ProcedureRef.fromFamilyKey("phase-transition", "finish-rest"),
      Some(PhaseTransitionRef.FinishRest))

  test("a triggered procedure cannot be started by key alone"):
    // A client names a procedure by key alone; a triggered key must not
    // resolve.
    assertEquals(StartableRef.fromKey("oathkeeper"), None)
    assertEquals(StartableRef.fromKey("forced-wake"), None)
