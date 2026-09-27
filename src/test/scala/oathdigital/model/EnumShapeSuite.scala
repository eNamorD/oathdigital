package oathdigital.model

class EnumShapeSuite extends munit.FunSuite:
  test("enum cases keep their names, keys and hand-written order"):
    assertEquals(Suit.Order.toString, "Order")
    assertEquals(Suit.Order.productPrefix, "Order")
    assertEquals(Suit.Order.key, "order")
    assertEquals(Suit.all, Vector(Suit.Discord, Suit.Arcane, Suit.Order,
      Suit.Hearth, Suit.Beast, Suit.Nomad))
    assertEquals(Region.all.map(_.key), Vector("cradle", "provinces", "hinterland"))
    assertEquals(FoundationNumber.IV.value, 4)
    assertEquals(Role.Chancellor.isImperial, true)
    assertEquals(MajorActionType.all.map(_.key).head, "search")
    assertEquals(ActionKind.fromKey("when-played"), Some(ActionKind.WhenPlayed))
    assertEquals(PhaseTransitionRef.EndWake.family, "phase-transition")
    assertEquals(PhaseTransitionRef.EndWake.key, "end-wake")
    assertEquals(TriggeredProcedureRef.all.map(_.key),
      Vector("oathkeeper", "setup", "forced-wake"))
