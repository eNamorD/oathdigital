package oathdigital.frontend

import oathdigital.frontend.TestBoards.*
import oathdigital.model.PlayerColor

/** What a board's Supply figure says on hover and opens. */
class SupplyAtRestSuite extends munit.FunSuite:
  private val bands = Vector(SupplyBand(9, None, 6), SupplyBand(4, Some(8), 5),
    SupplyBand(0, Some(3), 4))

  private val board = quietBoard("red").withWarbands(6).withSupply(5)
    .copy(bankedWarbands = 5, printedWarbands = 14, siteWarbands = 3,
      restSupplyBase = 5)

  test("an Exile's note names the bank and the Supply the next Rest gains"):
    assertEquals(SupplyAtRest.note("exile", board),
      "Warbands in bank: 5\nSupply gain at Rest: +5")

  test("a citizen's note names the bank and the Chancellor's rule"):
    assertEquals(SupplyAtRest.note("Citizen", board),
      "Warbands in bank: 5\nCitizens copy the Chancellor's Supply.")

  test("an Exile's panel reads the bank, where the rest are, the track, and the Rest"):
    assertEquals(SupplyAtRest.panel(board, bands, 7, PlayerColor.Red,
      own = true), SupplyAtRest.Panel(
        "Warbands in bank: 5",
        "(6 on your board, 3 on sites)",
        SupplyTracker.Track(bands, supply = 5, banked = 5, maximum = 7,
          PlayerColor.Red),
        "Supply gain at Rest: 5 plus unspent Supply (max 7)"))

  test("another player's panel says their board"):
    assertEquals(SupplyAtRest.panel(board, bands, 7, PlayerColor.Red,
      own = false).elsewhere, "(6 on their board, 3 on sites)")

  test("only an Exile reads the track"):
    assert(SupplyAtRest.isExile("Exile"))
    assert(!SupplyAtRest.isExile("citizen"))
    assertEquals(SupplyAtRest.citizenLines,
      Vector("Citizens copy the Chancellor's Supply."))
