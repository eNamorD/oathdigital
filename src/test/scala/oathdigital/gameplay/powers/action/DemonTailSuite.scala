package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.{assertWireRoundTrips, ready}
import oathdigital.testkit.Unwrap.accepted

class DemonTailSuite extends munit.FunSuite:
  import ActPowerHarness._
  import PowerFixture._

  private val tail = CatalogNames.relic("Demon Tail")
  private val source = DecisionOptionRef.Relic(tail)

  /** p1's Act holding Demon Tail, with `supply` Supply and `faceUp` faceup
    * secrets. */
  private def staged(supply: Int = 2, faceUp: Int = 3): ReadyGame =
    Table.start.turn(actor, Phase.Act).relic(actor, tail)
      .supply(actor, supply).secrets(actor, faceUp).ready

  test("it burns three secrets and gains 2 Supply"):
    // 2 Supply + 2 = 4; 3 faceup secrets - 3 burnt = 0.
    val ready0 = staged(supply = 2, faceUp = 3)
    val done = accepted(use(rules(), ready0, DemonTail.id, source))
    val end = ready(done.state)
    assertEquals(allSecrets(end), 0)
    // Burnt secrets go to the shared bank, not onto the relic.
    assertEquals(Look(end).tokensOn(tail), Tokens.empty)
    assertEquals(Look(end).player(actor).board.supply, SupplyTrack(4))
    assertEquals(replayed(rules(), ready0, done.events), end)
    assertWireRoundTrips(done.events)

  test("the gain is clamped at the track maximum, and the line says so"):
    val done = accepted(use(rules(), staged(supply = SupplyTrack.Maximum - 1),
      DemonTail.id, source))
    assertEquals(Look(ready(done.state)).player(actor).board.supply,
      SupplyTrack.full)
    assertEquals(NoteText.said(DemonTail, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 1 Supply.", covers = false)))

  test("two secrets are not enough"):
    val broke = staged(faceUp = 2)
    assert(!usableIds(broke).contains(DemonTail.id))
    assertEquals(use(rules(), broke, DemonTail.id, source).left.toOption,
      Some(OathViolation.InsufficientSecrets(3, 2)))

  test("its line reads the Supply gained"):
    val done = accepted(use(rules(), staged(), DemonTail.id, source))
    assertEquals(NoteText.said(DemonTail, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 2 Supply.", covers = false)))
