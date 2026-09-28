package oathdigital.model

/** Keys that journaled events and the client protocol carry. Renaming one
  * breaks saved games or the frontend, so each is spelled out here.
  */
class EnumWireKeySuite extends munit.FunSuite:
  test("suit, region and major action keys stay as the wire spells them"):
    assertEquals(Suit.all.map(_.key),
      Vector("discord", "arcane", "order", "hearth", "beast", "nomad"))
    assertEquals(Region.all.map(_.key),
      Vector("cradle", "provinces", "hinterland"))
    assertEquals(MajorActionType.all.map(_.key), Vector("search", "travel",
      "campaign", "muster", "trade", "forge", "recover", "challenge"))

  test("Phase.Setup round-trips through its wire key"):
    assertEquals(Phase.fromKey("setup"), Some(Phase.Setup))
    assertEquals(Phase.Setup.key, "setup")
