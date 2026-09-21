package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{PlayerFacts, PowerFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class DragonskinDrumSuite extends munit.FunSuite {
  import PowerFixture._
  import TravelFixture._

  private val drum = RelicId("R20")
  private val modifiers = Vector(DragonskinDrum.id)
  private def held = withRelic(board(), drum)

  test("the Drum is a registered selected Travel modifier") {
    val power = DragonskinDrum.forCatalog(catalog).get
    assertEquals(power.cardId, drum)
    assertEquals(power.cost, Cost.free)
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
  }

  test("after Travel the player gains one warband, and pays the printed Supply") {
    val ready = held
    val done = travel(ready, coast, modifiers).toOption.get
    val result = after(done)
    assertEquals(player(result).pawnSite, Some(coast))
    assertEquals(player(result).board.warbands, player(ready).board.warbands + 1)
    assertEquals(supplyOf(result), 7 - 1)
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events), result)
    assert(PaidActionHarness.wireRoundTrips(done.events))
  }

  test("without the selection, or with an empty bank, it gains what it can") {
    val plain = after(travel(held, coast).toOption.get)
    assertEquals(player(plain).board.warbands, player(held).board.warbands)
    val kind = PlayerFacts.forceKind(held, actor).toOption.get
    val empty = leaveInBank(held, kind, 0)
    val result = after(travel(empty, coast, modifiers).toOption.get)
    assertEquals(player(result).board.warbands, player(empty).board.warbands)
  }

  test("a Travel that is rejected gains nothing") {
    val ready = withBoard(held)(_.copy(supply = SupplyTrack(0)))
    assert(travel(ready, coast, modifiers).isLeft)
  }

  test("a facedown Drum is not usable") {
    assert(travel(withRelic(board(), drum, Orientation.FaceDown), coast,
      modifiers).isLeft)
  }
}
