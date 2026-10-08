package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.{assertWireRoundTrips, ready}
import oathdigital.testkit.Unwrap.accepted

class ShiftingMapSuite extends munit.FunSuite:
  import ActPowerHarness._
  import PowerFixture._

  private val map = CatalogNames.relic("Shifting Map")
  private val source = DecisionOptionRef.Relic(map)

  /** p1's Act holding Shifting Map, with `supply` Supply and `faceUp`
    * faceup secrets. */
  private def staged(supply: Int = 2, faceUp: Int = 1): ReadyGame =
    Table.start.turn(actor, Phase.Act).relic(actor, map).supply(actor, supply)
      .secrets(actor, faceUp).ready

  test("it places a secret on the relic and gains 1 Supply"):
    // 2 Supply + 1 = 3.
    val ready0 = staged(supply = 2)
    val done = accepted(use(rules(), ready0, ShiftingMap.id, source))
    val end = ready(done.state)
    assertEquals(Look(end).tokensOn(map), Tokens(0, 1))
    assertEquals(Look(end).player(actor).board.supply, SupplyTrack(3))
    assertEquals(replayed(rules(), ready0, done.events), end)
    assertWireRoundTrips(done.events)

  test("at the track maximum the cost is paid and nothing is gained"):
    val done = accepted(use(rules(), staged(supply = SupplyTrack.Maximum),
      ShiftingMap.id, source))
    assertEquals(Look(ready(done.state)).player(actor).board.supply,
      SupplyTrack.full)
    assertEquals(Look(ready(done.state)).tokensOn(map), Tokens(0, 1))
    assertEquals(NoteText.said(ShiftingMap, done.events), Vector.empty)

  test("it is unusable without a faceup secret"):
    val broke = staged(faceUp = 0)
    assert(!usableIds(broke).contains(ShiftingMap.id))
    assertEquals(use(rules(), broke, ShiftingMap.id, source).left.toOption,
      Some(OathViolation.InsufficientSecrets(1, 0)))

  test("its line reads the Supply gained"):
    val done = accepted(use(rules(), staged(), ShiftingMap.id, source))
    assertEquals(NoteText.said(ShiftingMap, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 1 Supply.", covers = false)))
