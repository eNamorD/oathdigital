package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class TyrantSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val tyrant = CatalogNames.denizen("Tyrant")
  private val rival = Table.p2

  /** The actor holds Tyrant faceup, at Buried Giant, the Cradle's plains,
    * and the coast is set by `atCoast`. */
  private def holdingTyrant(atCoast: Table => Table): ReadyGame =
    on(board(supply = 7))(table => atCoast(table.adviser(actor, tyrant)))

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(Tyrant.id, Tyrant.noteKeys, events)

  test("after its holder travels, one warband at the destination dies, and " +
      "the line names whose"):
    val ready = holdingTyrant(_.warbandsAt(coast, rival, 2))
    val done = accepted(travel(ready, coast))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).forces(coast),
      SiteForces.Occupied(Look(result).forceKind(rival), 1))
    assertEquals(said(done.events), Vector(NoteText.Said("killed",
      s"Killed 1 ${rival.value} warband at ${coast.value}.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("its holder's own warband dies too"):
    val ready = holdingTyrant(_.warbandsAt(coast, actor, 2))
    val done = accepted(travel(ready, coast))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).forces(coast),
      SiteForces.Occupied(Look(result).forceKind(actor), 1))
    assertEquals(said(done.events), Vector(NoteText.Said("killed",
      s"Killed 1 ${actor.value} warband at ${coast.value}.", covers = false)))

  test("a bandit warband dies, with its own line"):
    val done = accepted(travel(holdingTyrant(_.bandits(coast, 2)), coast))
    assertEquals(Look(Outcomes.ready(done.state)).forces(coast),
      SiteForces.Occupied(ForceKind.Bandit, 1))
    assertEquals(said(done.events), Vector(NoteText.Said("killed.bandits",
      s"Killed 1 bandit warband at ${coast.value}.", covers = false)))

  test("an empty destination, or another player's Tyrant, kills nothing"):
    val emptyCoast = holdingTyrant(_.bandits(coast, 0))
    val rivalHolds = on(board(supply = 7))(_.adviser(rival, tyrant)
      .warbandsAt(coast, rival, 2))
    Vector(emptyCoast, rivalHolds).foreach { ready =>
      val done = accepted(travel(ready, coast))
      assert(!PhasePowerHarness.ops(done.events).exists(_.isInstanceOf[Kill]))
      assertEquals(said(done.events), Vector.empty)
    }
