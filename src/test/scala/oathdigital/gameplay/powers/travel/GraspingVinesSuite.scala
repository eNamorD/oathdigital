package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class GraspingVinesSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val vines = CatalogNames.denizen("Grasping Vines")
  private val rival = PhasePowerHarness.others(quietTable).head
  private def warbands(ready: ReadyGame): Int = Look(ready).player(actor)
    .board.warbands

  /** `id` standing facedown at `at`, a site card turned down. */
  private def facedownAt(ready: ReadyGame, id: DenizenId, at: SiteId): ReadyGame =
    Table.from(ready).denizen(id, at = at)
      .unchecked.updateCurrent(
      c => c.copy(
      map = c.map.copy(
      sites = c.map.sites.updated(at, c.map.sites(at).copy(denizens =
        c.map.sites(at).denizens.map {
          case card: DenizenState if card.id == id =>
            card.copy(orientation = Orientation.FaceDown)
          case other => other
        })))))

  /** The Vines stand at the actor's own site, which `ruler` rules. */
  private def vinesAtHome(ruler: Option[PlayerId]): ReadyGame =
    val ready = Table.from(board()).denizen(vines, at = cradlePlains).unchecked
    ruler.fold(ready)(Table.from(ready).warbandsAt(cradlePlains, _, 1)
      .unchecked)

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
    assertEquals(
      warbands(Outcomes.ready(accepted(travel(ready, coast)).state)), warbands(
      ready) - 1)

  test("the ruler is exempt"):
    val ready = vinesAtHome(Some(actor))
    assertEquals(
      warbands(Outcomes.ready(accepted(travel(ready, coast)).state)), warbands(
      ready))

  test("only the site the traveller leaves counts, not the destination"):
    val ready = Table.from(board()).denizen(vines, at = provincePlains)
      .warbandsAt(provincePlains, rival, 1).unchecked
    assertEquals(
      warbands(Outcomes.ready(accepted(travel(ready, coast)).state)), warbands(
      ready))
    // Travelling out of the Vines' site does kill.
    val leaving = Table.from(board(source = provincePlains))
      .denizen(vines, at = provincePlains).warbandsAt(provincePlains, rival, 1)
      .unchecked
    val out = Outcomes.ready(accepted(travel(leaving, coast)).state)
    assertEquals(warbands(out), warbands(leaving) - 1)

  test("the kill is not required: a traveller with no warband is not stopped"):
    val ready = Table.from(vinesAtHome(Some(rival))).boardWarbands(actor, 0)
      .unchecked
    val result = Outcomes.ready(accepted(travel(ready, coast)).state)
    assertEquals(Look(result).player(actor).pawnSite, Some(coast))
    assertEquals(warbands(result), 0)

  test("a facedown Grasping Vines is not active"):
    // The Vines rule from the site they stand on, and only faceup there.
    val ready = Table.from(facedownAt(board(), vines, cradlePlains))
      .warbandsAt(cradlePlains, rival, 1).unchecked
    assertEquals(
      warbands(Outcomes.ready(accepted(travel(ready, coast)).state)), warbands(
      ready))

  // ---- Lines ----

  private val power = GraspingVines.forCatalog(catalog)
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("the kill is written as the Vines' line, naming the traveller"):
    assertEquals(
      said(accepted(travel(vinesAtHome(Some(rival)), coast))
      .events), Vector(NoteText.Said("killed",
        s"Killed 1 ${actor.value} warband.", covers = false)))

  test("a traveller with no warband to lose reads no line"):
    val ready = Table.from(vinesAtHome(Some(rival))).boardWarbands(actor, 0)
      .unchecked
    assertEquals(said(accepted(travel(ready, coast)).events), Vector.empty)
