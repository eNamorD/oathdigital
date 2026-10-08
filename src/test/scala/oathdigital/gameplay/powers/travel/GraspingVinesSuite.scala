package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class GraspingVinesSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val vines = CatalogNames.denizen("Grasping Vines")
  private val rival = Table.p2
  private def warbands(ready: ReadyGame): Int = Look(ready).boardWarbands(actor)

  /** The Vines stand at the actor's own site, which `ruler` rules with one
    * warband, or one bandit when `ruler` is None. */
  private def vinesAtHome(ruler: Option[PlayerId]): ReadyGame =
    on(board())(table =>
      val vinesTable = table.denizen(vines, at = cradlePlains)
      ruler.fold(vinesTable.bandits(cradlePlains, 1))(
        vinesTable.warbandsAt(cradlePlains, _, 1)))

  private val power = GraspingVines.forCatalog(catalog)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("an enemy traveling from a site the ruler rules kills a warband of " +
      "their own"):
    val ready = vinesAtHome(Some(rival))
    val done = accepted(travel(ready, coast))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).player(actor).pawnSite, Some(coast))
    assertEquals(warbands(result), warbands(ready) - 1)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("bandits rule the site: the traveller is still an enemy"):
    val ready = vinesAtHome(None)
    val result = Outcomes.ready(accepted(travel(ready, coast)).state)
    assertEquals(warbands(result), warbands(ready) - 1)

  test("the ruler is exempt"):
    val ready = vinesAtHome(Some(actor))
    val result = Outcomes.ready(accepted(travel(ready, coast)).state)
    assertEquals(warbands(result), warbands(ready))

  test("only the site the traveller leaves counts, not the destination"):
    val ready = on(board())(_.denizen(vines, at = provincePlains)
      .warbandsAt(provincePlains, rival, 1))
    val result = Outcomes.ready(accepted(travel(ready, coast)).state)
    assertEquals(warbands(result), warbands(ready))
    // Travelling out of the Vines' site does kill.
    val leaving = on(board(source = provincePlains))(_
      .denizen(vines, at = provincePlains).warbandsAt(provincePlains, rival, 1)
      )
    val out = Outcomes.ready(accepted(travel(leaving, coast)).state)
    assertEquals(warbands(out), warbands(leaving) - 1)

  test("the kill is not required: a traveller with no warband is not stopped"):
    val ready = on(vinesAtHome(Some(rival)))(_.boardWarbands(actor, 0)
      )
    val result = Outcomes.ready(accepted(travel(ready, coast)).state)
    assertEquals(Look(result).player(actor).pawnSite, Some(coast))
    assertEquals(warbands(result), 0)

  test("a facedown Grasping Vines is not active"):
    // The Vines rule from the site they stand on, and only faceup there.
    val ready = on(board())(_
      .denizen(vines, at = cradlePlains, facedown = true)
      .warbandsAt(cradlePlains, rival, 1))
    val result = Outcomes.ready(accepted(travel(ready, coast)).state)
    assertEquals(warbands(result), warbands(ready))

  // ---- Lines ----

  test("the kill is written as the Vines' line, naming the traveller"):
    assertEquals(said(accepted(travel(vinesAtHome(Some(rival)), coast)).events),
      Vector(NoteText.Said("killed",
        s"Killed 1 ${actor.value} warband.", covers = false)))

  test("a traveller with no warband to lose reads no line"):
    val ready = on(vinesAtHome(Some(rival)))(_.boardWarbands(actor, 0)
      )
    assertEquals(said(accepted(travel(ready, coast)).events), Vector.empty)
