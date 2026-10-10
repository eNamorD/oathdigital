package oathdigital.model

class ProcedureRefSuite extends munit.FunSuite:
  private val use = ActionRef.UsePower(PowerId("denizen.silver-tongue"))

  test("a use-power reference parses from its key under the action family only"):
    assertEquals(use.key, "use-power:denizen.silver-tongue")
    assertEquals(ActionRef.fromKey(use.key), Some(use))
    assertEquals(ProcedureRef.fromFamilyKey("action", use.key), Some(use))
    assertEquals(ProcedureRef.fromFamilyKey("phase-transition", use.key), None)
    assertEquals(ActionRef.fromKey("use-power:"), None)

  test("procedure reference keys are listed per family and unique across " +
      "all of them"):
    // The hand lists below pin wire values: each key is persisted in the
    // journal and sent by clients, so a renamed key breaks saved games.
    assertEquals(ActionRef.all.map(_.key),
      Vector("search", "play-facedown-adviser", "recover", "forge",
        "travel", "take-wealth", "muster", "trade", "challenge",
        "place-banner-resource", "negotiation", "campaign",
        "peek-site-relics", "reveal-owned-relic", "move-warbands"))
    assertEquals(PhaseTransitionRef.all.map(_.key),
      Vector("end-wake", "begin-rest", "finish-rest"))
    assertEquals(ProcedureRef.all.map(_.key).distinct.size,
      ProcedureRef.all.size)
    assertEquals(TriggeredProcedureRef.all,
      Vector(TriggeredProcedureRef.Oathkeeper, TriggeredProcedureRef.Setup,
        TriggeredProcedureRef.ForcedWake,
        TriggeredProcedureRef.FollowUpOffer, TriggeredProcedureRef.WakeMenu,
        TriggeredProcedureRef.RestMenu, TriggeredProcedureRef.ActMenu))
    assertEquals(TriggeredProcedureRef.ActMenu.key, "act-menu")

  test("a key resolves only under its own family"):
    // end-wake is a phase transition and recover an action: end-wake
    // resolves under its own family only, and finish-rest resolves there.
    assertEquals(ActionRef.fromKey("end-wake"), None)
    assertEquals(ProcedureRef.fromFamilyKey("action", "end-wake"), None)
    assertEquals(ProcedureRef.fromFamilyKey("phase-transition", "end-wake"),
      Some(PhaseTransitionRef.EndWake))
    assertEquals(ProcedureRef.fromFamilyKey("triggered", "recover"), None)
    assertEquals(ProcedureRef.fromFamilyKey("phase-transition", "finish-rest"),
      Some(PhaseTransitionRef.FinishRest))
