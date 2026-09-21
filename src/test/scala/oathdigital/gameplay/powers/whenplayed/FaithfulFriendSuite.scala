package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class FaithfulFriendSuite extends munit.FunSuite {
  import PowerFixture._
  import WhenPlayedHarness._

  private val power = FaithfulFriend.forCatalog(catalog).get
  private val card = power.cardId
  private def withSupply(amount: Int) = withBoard(asAdviser(base, card))(
    _.copy(supply = SupplyTrack(amount)))

  test("Faithful Friend is in the default walker catalog") {
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))
  }

  test("playing it gains 4 Supply") {
    val ready = withSupply(2)
    val done = finished(play(ready, power, card))
    assertEquals(player(done.treeless).board.supply, SupplyTrack(6))
    assertEquals(replayed(ready, done.events), done.treeless)
  }

  test("the gain is clamped at the track maximum") {
    val done = finished(play(withSupply(5), power, card))
    assertEquals(player(done.treeless).board.supply, SupplyTrack(7))
  }

  test("another card being played does nothing") {
    val other = DenizenId("1")
    val ready = asAdviser(withSupply(2), other)
    assertEquals(recorded(finished(play(ready, power, other)).events),
      Vector.empty)
  }
}
