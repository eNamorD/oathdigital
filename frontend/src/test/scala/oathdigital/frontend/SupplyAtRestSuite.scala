package oathdigital.frontend

/** The lines behind a board's Supply figure. */
class SupplyAtRestSuite extends munit.FunSuite:
  private val bands = Vector(SupplyBand(9, None, 6), SupplyBand(4, Some(8), 5),
    SupplyBand(0, Some(3), 4))

  private val board = PlayerBoard("red", warbands = 6, favor = 0,
    faceUpSecrets = 0, faceDownSecrets = 0, committedSecrets = 0,
    totalSecrets = 0, supply = 5, pawnSiteId = None, advisers = Vector.empty,
    relics = Vector.empty, revealedVision = None, bankedWarbands = 5,
    printedWarbands = 14, siteWarbands = 3, restSupplyBase = 5)

  test("an Exile reads the bag, the table with its band marked, and the Rest"):
    assertEquals(SupplyAtRest.lines("exile", board, bands, 7), Vector(
      "Warbands in bag: 5 (14 printed, 6 on board, 3 at sites).",
      "0–3 warbands: Supply 4",
      "4–8 warbands: Supply 5 (now)",
      "9 or more warbands: Supply 6",
      "Next Rest: 5 from the band, plus unspent Supply, capped at 7."))

  test("an empty bag marks the lowest band"):
    val lines = SupplyAtRest.lines("Exile", board.copy(bankedWarbands = 0,
      restSupplyBase = 4), bands, 7)
    assertEquals(lines(1), "0–3 warbands: Supply 4 (now)")
    assertEquals(lines(2), "4–8 warbands: Supply 5")

  test("a citizen is told the Chancellor's rule and no table"):
    assertEquals(SupplyAtRest.lines("citizen", board, bands, 7),
      Vector("Citizens copy the Chancellor's Supply."))
