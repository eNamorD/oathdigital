package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._

class ShiftingMapSuite extends munit.FunSuite:
  import PaidActionHarness._
  import PowerFixture._

  private val map = RelicId("R17")
  private val source = DecisionOptionRef.Relic(map)

  private def staged(supply: Int = 2, faceUp: Int = 1): ReadyGame =
    act(withBoard(withRelic(base, map))(
      _.copy(supply = SupplyTrack(supply), faceUpSecrets = faceUp)))

  test("it places a secret on the relic and gains 1 Supply"):
    val ready0 = staged()
    val done = use(rules(), ready0, ShiftingMap.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(tokensOn(end, map), Tokens(0, 1))
    assertEquals(player(end).board.supply, SupplyTrack(3))
    assertEquals(replayed(rules(), ready0, done.events), end)
    assert(wireRoundTrips(done.events))

  test("at the track maximum the cost is paid and nothing is gained"):
    val done = use(rules(), staged(supply = SupplyTrack.Maximum),
      ShiftingMap.id, source).toOption.get
    assertEquals(player(ready(done.state)).board.supply, SupplyTrack.full)
    assertEquals(tokensOn(ready(done.state), map), Tokens(0, 1))
    assertEquals(NoteText.said(ShiftingMap, done.events), Vector.empty)

  test("it is unusable without a faceup secret"):
    val broke = staged(faceUp = 0)
    assert(!usableIds(broke).contains(ShiftingMap.id))
    assert(use(rules(), broke, ShiftingMap.id, source).isLeft)

  test("its line reads the Supply gained"):
    val done = use(rules(), staged(), ShiftingMap.id, source).toOption.get
    assertEquals(NoteText.said(ShiftingMap, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 1 Supply.", covers = false)))
