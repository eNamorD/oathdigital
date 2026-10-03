package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{Look, Outcomes, PhasePowerHarness, Table}

class FaithfulFriendSuite extends munit.FunSuite:
  import PowerFixture._
  import WhenPlayedHarness._

  private val power = FaithfulFriend
  private val card = power.cardId
  private def withSupply(amount: Int) = Table.from(asAdviser(quietTable, card))
    .supply(actor, amount).unchecked

  test("Faithful Friend is in the default walker catalog"):
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))

  test("playing it gains 4 Supply"):
    val ready = withSupply(2)
    val done = Outcomes.finished(play(ready, power, card))
    assertEquals(Look(done.treeless).player(actor).board.supply, SupplyTrack(6))
    assertEquals(replayed(ready, done.events), done.treeless)

  test("the gain is clamped at the track maximum"):
    val done = Outcomes.finished(play(withSupply(5), power, card))
    assertEquals(Look(done.treeless).player(actor).board.supply, SupplyTrack(7))

  test("another card being played does nothing"):
    val other = DenizenId("1")
    val ready = asAdviser(withSupply(2), other)
    val done = Outcomes.finished(play(ready, power, other))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
