package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{NoteText, PlayerFacts, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Refusals}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class DragonskinDrumSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val drum = CatalogNames.relic("Dragonskin Drum")
  private val modifiers = Vector(DragonskinDrum.id)
  private def held = withRelic(board(supply = 7), drum)

  test("after Travel the player gains one warband, and pays the printed Supply"):
    val ready = held
    val done = accepted(travel(ready, coast, modifiers))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).player(actor).pawnSite, Some(coast))
    assertEquals(Look(result).boardWarbands(actor),
      Look(ready).boardWarbands(actor) + 1)
    // 7 Supply, less the printed 1 for a Travel within the Cradle.
    assertEquals(Look(result).supply(actor), 7 - 1)
    // The Drum itself is free.
    assertEquals(Look(result).favor(actor), Look(ready).favor(actor))
    assertEquals(Look(result).faceUpSecrets(actor),
      Look(ready).faceUpSecrets(actor))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("without the selection, or with an empty bank, it gains no warband"):
    val plain = Outcomes.ready(accepted(travel(held, coast)).state)
    assertEquals(Look(plain).boardWarbands(actor),
      Look(held).boardWarbands(actor))
    val kind = accepted(PlayerFacts.forceKind(held, actor))
    val empty = leaveInBank(held, kind, 0)
    val result = Outcomes.ready(accepted(travel(empty, coast, modifiers)).state)
    assertEquals(Look(result).boardWarbands(actor),
      Look(empty).boardWarbands(actor))

  test("a facedown Drum is not usable"):
    assertEquals(travel(withRelic(board(), drum, Orientation.FaceDown), coast,
      modifiers).left.toOption,
      Some(Refusals.notApplicable(DragonskinDrum.id, "travel")))

  // ---- Lines ----

  private val power = DragonskinDrum.forCatalog(catalog)
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("the warband gained is written as the Drum's line"):
    assertEquals(said(accepted(travel(held, coast, modifiers)).events),
      Vector(NoteText.Said("gained", s"${actor.value} gained 1 warband.",
        covers = false)))

  test("an empty warband bank gains nothing, and writes nothing"):
    val kind = accepted(PlayerFacts.forceKind(held, actor))
    assertEquals(
      said(accepted(travel(leaveInBank(held, kind, 0), coast, modifiers))
      .events), Vector.empty)
