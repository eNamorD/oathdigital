package oathdigital.model

class ActionKindSuite extends munit.FunSuite:
  test("keys are the stable wire spellings"):
    // Hand list on purpose: each key is persisted in the journal and sent on
    // the wire, so this pins the spellings; a renamed key breaks saved games.
    assertEquals(ActionKind.all.map(_.key), Vector("travel", "search",
      "campaign", "muster", "trade", "forge", "recover", "challenge", "wake",
      "rest", "when-played", "action-boundary", "negotiation"))

  test("fromKey inverts key for every member"):
    ActionKind.all.foreach(kind =>
      assertEquals(ActionKind.fromKey(kind.key), Some(kind)))
    assertEquals(ActionKind.fromKey("major"), None)
