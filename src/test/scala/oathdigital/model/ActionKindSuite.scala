package oathdigital.model

class ActionKindSuite extends munit.FunSuite:
  test("keys are the stable wire spellings"):
    assertEquals(ActionKind.all.map(_.key), Vector("travel", "search",
      "campaign", "muster", "trade", "forge", "recover", "challenge", "wake",
      "rest", "when-played", "action-boundary", "negotiation"))

  test("fromKey inverts key for every member"):
    ActionKind.all.foreach(kind =>
      assertEquals(ActionKind.fromKey(kind.key), Some(kind)))
    assertEquals(ActionKind.fromKey("major"), None)
