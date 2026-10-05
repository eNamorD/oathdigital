package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2}

/** Each player board says how many adviser slots to draw (card play slots
  * design, "Projection"). */
class AdviserLimitProjectionSuite extends munit.FunSuite:
  private val presentation = new GamePresentationProjector(catalog)

  test("a board's adviser limit is 3, and 2 while its player holds Silver " +
      "Tongue faceup; a facedown Insomnia sets none"):
    val ready = Table.start.adviser(p1, "Silver Tongue")
      .adviser(p2, "Insomnia", facedown = true).ready
    val limits = presentation.playerBoards(ready, Some(p1))
      .map(board => board.playerId -> board.adviserLimit).toMap
    assertEquals(limits(p1.value), 2)
    assertEquals(limits(p2.value), 3)
