package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class TollRoadsSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val toll = CatalogNames.denizen("Toll Roads")
  private val rival = PhasePowerHarness.others(quietTable).head

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

  /** Toll Roads stands at `provincePlains`, and `rival` rules it and the
    * coast. The actor, at Buried Giant, the Cradle's plains, holds 1 favor and
    * travels to the coast.
    */
  private def rivalRules: ReadyGame =
    val ready = Table.from(board()).denizen(toll, at = provincePlains)
      .favor(actor, 1).unchecked
    Table.from(ready).warbandsAt(provincePlains, rival, 1)
      .warbandsAt(coast, rival, 1).unchecked

  test("an enemy pays the ruler 1 favor to travel to a site the ruler rules"):
    val ready = rivalRules
    val done = accepted(travel(ready, coast))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).player(actor).pawnSite, Some(coast))
    assertEquals(Look(result).player(actor).board.favor, 0)
    assertEquals(Look(result).player(rival).board.favor,
      Look(ready).player(rival).board.favor + 1)
    assertEquals(Look(result).supply(actor), 7 - 1)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("the rule covers every site the ruler holds, Toll Roads' own included"):
    val result = Outcomes.ready(
      accepted(
      travel(Table.from(rivalRules).warbandsAt(pass, actor, 1).unchecked,
      provincePlains)).state)
    assertEquals(Look(result).player(actor).board.favor, 0)
    assertEquals(Look(result).player(rival).board.favor,
      Look(rivalRules).player(rival).board.favor + 1)

  test("a traveller who cannot pay does not get the destination"):
    val broke = Table.from(rivalRules).favor(actor, 0).unchecked
    assertEquals(candidates(broke).get(coast), None)
    assert(travel(broke, coast).isLeft)
    // A site the rival does not rule is still offered.
    assert(candidates(broke).contains(hinterlandPlains))

  test("a destination the ruler does not rule costs nothing extra"):
    val result = Outcomes.ready(accepted(travel(rivalRules, hinterlandPlains))
      .state)
    assertEquals(Look(result).player(actor).board.favor, 1)

  test("the ruler itself travels for free"):
    val ready = Table.from(board()).denizen(toll, at = provincePlains)
      .favor(actor, 1).unchecked
    val mine = Table.from(ready).warbandsAt(provincePlains, actor, 1)
      .warbandsAt(coast, actor, 1).unchecked
    val result = Outcomes.ready(accepted(travel(mine, coast)).state)
    assertEquals(Look(result).player(actor).board.favor, 1)

  test("when bandits rule, the favor is burnt"):
    val ready = Table.from(board()).denizen(toll, at = provincePlains)
      .favor(actor, 1).unchecked
    val result = Outcomes.ready(accepted(travel(ready, coast)).state)
    assertEquals(Look(result).player(actor).board.favor, 0)
    assertEquals(Look(result).player(rival).board.favor,
      Look(ready).player(rival).board.favor)
    assertEquals(candidates(Table.from(ready).favor(actor, 0).unchecked)
      .get(coast), None)

  test("a facedown Toll Roads is not active"):
    // Toll Roads charges from the site it stands on, and only faceup there.
    val ready = Table.from(facedownAt(board(), toll, provincePlains))
      .favor(actor, 1).unchecked
    val ruled = Table.from(ready).warbandsAt(provincePlains, rival, 1)
      .warbandsAt(coast, rival, 1).unchecked
    val result = Outcomes.ready(accepted(travel(ruled, coast)).state)
    assertEquals(Look(result).player(actor).board.favor, 1)

  // ---- Lines ----

  private val power = TollRoads.forCatalog(catalog)
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("the toll paid to the ruler is written as the Roads' line"):
    assertEquals(said(accepted(travel(rivalRules, coast)).events),
      Vector(NoteText.Said("paid",
        s"${actor.value} paid 1 favor to ${rival.value}.", covers = false)))

  test("a toll burnt for bandits is written; a free Travel writes nothing"):
    val ready = Table.from(board()).denizen(toll, at = provincePlains)
      .favor(actor, 1).unchecked
    assertEquals(said(accepted(travel(ready, coast)).events),
      Vector(NoteText.Said("burned", s"${actor.value} burned 1 favor.",
        covers = false)))
    assertEquals(said(accepted(travel(rivalRules, hinterlandPlains)).events),
      Vector.empty)
