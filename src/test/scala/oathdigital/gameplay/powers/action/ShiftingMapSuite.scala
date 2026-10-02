package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, Look, Table}
import oathdigital.testkit.Outcomes.{assertWireRoundTrips, ready}

class ShiftingMapSuite extends munit.FunSuite:
  import ActPowerHarness._
  import PowerFixture._

  private val map = RelicId("R17")
  private val source = DecisionOptionRef.Relic(map)

  private def staged(supply: Int = 2, faceUp: Int = 1): ReadyGame =
    act(Table.from(withRelic(quietTable, map)).supply(actor, supply)
      .secrets(actor, faceUp).unchecked)

  test("it places a secret on the relic and gains 1 Supply"):
    val ready0 = staged()
    val done = use(rules(), ready0, ShiftingMap.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(Look(end).tokensOn(map), Tokens(0, 1))
    assertEquals(Look(end).player(actor).board.supply, SupplyTrack(3))
    assertEquals(replayed(rules(), ready0, done.events), end)
    assertWireRoundTrips(done.events)

  test("at the track maximum the cost is paid and nothing is gained"):
    val done = use(rules(), staged(supply = SupplyTrack.Maximum),
      ShiftingMap.id, source).toOption.get
    assertEquals(Look(ready(done.state)).player(actor).board.supply,
      SupplyTrack.full)
    assertEquals(Look(ready(done.state)).tokensOn(map), Tokens(0, 1))
    assertEquals(NoteText.said(ShiftingMap, done.events), Vector.empty)

  test("it is unusable without a faceup secret"):
    val broke = staged(faceUp = 0)
    assert(!usableIds(broke).contains(ShiftingMap.id))
    assert(use(rules(), broke, ShiftingMap.id, source).isLeft)

  test("its line reads the Supply gained"):
    val done = use(rules(), staged(), ShiftingMap.id, source).toOption.get
    assertEquals(NoteText.said(ShiftingMap, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 1 Supply.", covers = false)))
