package oathdigital.frontend

import PartitionDropTarget.Box

/** Where a dragged card lands among a zone's other cards, from the pointer
  * alone. The cards are the ones a zone draws, in reading order, without
  * the card being dragged.
  */
class PartitionDropTargetSuite extends munit.FunSuite:
  /** A card 100 wide and 140 tall whose left edge is at `left` and top edge
    * at `top`.
    */
  private def cardAt(left: Double, top: Double = 0): Box =
    Box(left, top, left + 100, top + 140)

  /** Three cards side by side, 10 apart: midpoints at 50, 160 and 270. */
  private val row = Vector(cardAt(0), cardAt(110), cardAt(220))

  test("a zone with no other cards takes the card first"):
    assertEquals(PartitionDropTarget.index(Vector.empty, 500, 500), 0)

  test("short of a card's midpoint the card lands before it"):
    assertEquals(PartitionDropTarget.index(row, 40, 70), 0)
    assertEquals(PartitionDropTarget.index(row, 150, 70), 1)

  test("past a card's midpoint the card lands after it"):
    assertEquals(PartitionDropTarget.index(row, 60, 70), 1)
    assertEquals(PartitionDropTarget.index(row, 280, 70), 3)

  test("above or below the row the pointer still reads across it"):
    assertEquals(PartitionDropTarget.index(row, 170, -60), 2)
    assertEquals(PartitionDropTarget.index(row, 170, 400), 2)

  /** Two cards on the first row, one wrapped onto a second. */
  private val wrapped = Vector(cardAt(0), cardAt(110), cardAt(0, 150))

  test("on a wrapped row every card of the rows above is passed"):
    assertEquals(PartitionDropTarget.index(wrapped, 10, 220), 2)
    assertEquals(PartitionDropTarget.index(wrapped, 60, 220), 3)

  test("on the first of two rows no card of the row below is passed"):
    assertEquals(PartitionDropTarget.index(wrapped, 200, 70), 2)
    assertEquals(PartitionDropTarget.index(wrapped, 10, 70), 0)
