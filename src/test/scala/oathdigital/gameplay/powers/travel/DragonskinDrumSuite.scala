package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{NoteText, PlayerFacts, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, Look}
import oathdigital.testkit.Outcomes.assertWireRoundTrips

class DragonskinDrumSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val drum = RelicId("R20")
  private val modifiers = Vector(DragonskinDrum.id)
  private def held = withRelic(board(), drum)

  test("after Travel the player gains one warband, and pays the printed Supply"):
    val ready = held
    val done = travel(ready, coast, modifiers).toOption.get
    val result = after(done)
    assertEquals(Look(result).player(actor).pawnSite, Some(coast))
    assertEquals(Look(result).player(actor).board.warbands, Look(ready)
      .player(actor).board.warbands + 1)
    assertEquals(supplyOf(result), 7 - 1)
    // The Drum itself is free.
    assertEquals(Look(result).player(actor).board.favor, Look(ready)
      .player(actor).board.favor)
    assertEquals(Look(result).player(actor).board.faceUpSecrets,
      Look(ready).player(actor).board.faceUpSecrets)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("without the selection, or with an empty bank, it gains what it can"):
    val plain = after(travel(held, coast).toOption.get)
    assertEquals(Look(plain).player(actor).board.warbands, Look(held)
      .player(actor).board.warbands)
    val kind = PlayerFacts.forceKind(held, actor).toOption.get
    val empty = leaveInBank(held, kind, 0)
    val result = after(travel(empty, coast, modifiers).toOption.get)
    assertEquals(Look(result).player(actor).board.warbands, Look(empty)
      .player(actor).board.warbands)

  test("a facedown Drum is not usable"):
    assertEquals(travel(withRelic(board(), drum, Orientation.FaceDown), coast,
      modifiers).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "power relic.dragonskin-drum is not applicable to this travel")))

  // ---- Lines ----

  private val power = DragonskinDrum.forCatalog(catalog)
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("the warband gained is written as the Drum's line"):
    assertEquals(said(travel(held, coast, modifiers).toOption.get.events),
      Vector(NoteText.Said("gained", s"${actor.value} gained 1 warband.",
        covers = false)))

  test("an empty warband bank gains nothing, and writes nothing"):
    val kind = PlayerFacts.forceKind(held, actor).toOption.get
    assertEquals(said(travel(leaveInBank(held, kind, 0), coast, modifiers)
      .toOption.get.events), Vector.empty)
