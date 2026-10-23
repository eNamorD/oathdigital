package oathdigital.gameplay.powers.whenplayed

import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.p1

class FaithfulFriendSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[FaithfulFriend.type]
  private val card = power.cardId

  /** p1 holds Faithful Friend faceup, with `amount` Supply. */
  private def withSupply(amount: Int): Table =
    Table.start.adviser(p1, card).supply(p1, amount)

  test("playing it gains 4 Supply"):
    val ready = withSupply(2).ready
    val done = Outcomes.finished(play(ready, power, card))
    assertEquals(Look(done.treeless).player(p1).board.supply,
      SupplyTrack(2 + 4))
    assertJournalRebuilds(ready, done.events, done.treeless)

  test("the gain is clamped at the track maximum"):
    val done = Outcomes.finished(play(withSupply(5).ready, power, card))
    // 5 + 4 would be 9; the track stops at 7.
    assertEquals(Look(done.treeless).player(p1).board.supply, SupplyTrack(7))

  test("another card being played does nothing"):
    val other = CatalogNames.denizen("Wrestlers")
    val ready = withSupply(2).adviser(p1, other).ready
    val done = Outcomes.finished(play(ready, power, other))
    assertEquals(ops(done.events), Vector.empty)
