package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, Look, Table}
import oathdigital.testkit.Outcomes.{assertWireRoundTrips, ready}

class DemonTailSuite extends munit.FunSuite:
  import ActPowerHarness._
  import PowerFixture._

  private val tail = RelicId("R46")
  private val source = DecisionOptionRef.Relic(tail)

  private def staged(supply: Int = 2, faceUp: Int = 3): ReadyGame =
    act(Table.from(withRelic(quietTable, tail)).supply(actor, supply)
      .secrets(actor, faceUp, 0).unchecked)

  test("it burns three secrets and gains 2 Supply"):
    val ready0 = staged()
    val done = use(rules(), ready0, DemonTail.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(allSecrets(end), 0)
    // Burnt secrets go to the shared bank, not onto the relic.
    assertEquals(Look(end).tokensOn(tail), Tokens.empty)
    assertEquals(Look(end).player(actor).board.supply, SupplyTrack(4))
    assertEquals(replayed(rules(), ready0, done.events), end)
    assertWireRoundTrips(done.events)

  test("the gain is clamped at the track maximum, and the line says so"):
    val done = use(rules(), staged(supply = SupplyTrack.Maximum - 1),
      DemonTail.id, source).toOption.get
    assertEquals(Look(ready(done.state)).player(actor).board.supply,
      SupplyTrack.full)
    assertEquals(NoteText.said(DemonTail, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 1 Supply.", covers = false)))

  test("two secrets are not enough"):
    val broke = staged(faceUp = 2)
    assert(!usableIds(broke).contains(DemonTail.id))
    assert(use(rules(), broke, DemonTail.id, source).isLeft)

  test("its line reads the Supply gained"):
    val done = use(rules(), staged(), DemonTail.id, source).toOption.get
    assertEquals(NoteText.said(DemonTail, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 2 Supply.", covers = false)))
