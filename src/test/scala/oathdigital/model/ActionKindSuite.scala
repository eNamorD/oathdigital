package oathdigital.model

class ActionKindSuite extends munit.FunSuite {
  test("keys are the stable wire spellings") {
    assertEquals(ActionKind.values.map(_.key), Vector("travel", "search",
      "campaign", "muster", "trade", "forge", "recover", "challenge", "wake",
      "rest", "when-played", "action-boundary", "negotiation"))
  }

  test("fromKey inverts key for every member") {
    ActionKind.values.foreach(kind =>
      assertEquals(ActionKind.fromKey(kind.key), Some(kind)))
    assertEquals(ActionKind.fromKey("major"), None)
  }
}
