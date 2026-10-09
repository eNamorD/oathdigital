package oathdigital.application

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2, p3}

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

  test("a board draws one more slot for each adviser that does not count"):
    val ready = Table.start.adviser(p1, "Pied Piper")
      .relic(p2, "Lost Tapestry")
      .adviser(p2, VisionRules.Faith, facedown = true).ready
    val limits = presentation.playerBoards(ready, Some(p1))
      .map(board => board.playerId -> board.adviserLimit).toMap
    assertEquals(limits(p1.value), 4)
    assertEquals(limits(p2.value), 4)
    assertEquals(limits(p3.value), 3)

  test("a board with no limit draws its advisers and one empty slot"):
    val ready = Table.start.adviser(p1, "Family Wagon").adviser(p1, "Elders")
      .adviser(p1, "Rain Boots").adviser(p1, "Lancers").ready
    val limits = presentation.playerBoards(ready, Some(p1))
      .map(board => board.playerId -> board.adviserLimit).toMap
    assertEquals(limits(p1.value), 5)
    assertEquals(limits(p2.value), 3)
