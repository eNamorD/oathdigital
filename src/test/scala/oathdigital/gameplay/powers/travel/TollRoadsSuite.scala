package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips

class TollRoadsSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val toll = DenizenId("118")
  private val rival = PhasePowerHarness.others(quietTable).head

  /** `id` standing facedown at `at`, a site card turned down. */
  private def facedownAt(ready: ReadyGame, id: DenizenId, at: SiteId): ReadyGame =
    denizenAt(ready, id, at).updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(at, c.map.sites(at).copy(denizens =
        c.map.sites(at).denizens.map {
          case card: DenizenState if card.id == id =>
            card.copy(orientation = Orientation.FaceDown)
          case other => other
        })))))

  /** Toll Roads stands at `plains(1)`, and `rival` rules it and the coast. The
    * actor, at the first plains, holds 1 favor and travels to the coast.
    */
  private def rivalRules: ReadyGame =
    val ready = Table.from(denizenAt(board(), toll, plains(1))).favor(actor, 1)
      .unchecked
    ruledBy(ruledBy(ready, plains(1), rival), coast, rival)

  test("an enemy pays the ruler 1 favor to travel to a site the ruler rules"):
    val ready = rivalRules
    val done = travel(ready, coast).toOption.get
    val result = after(done)
    assertEquals(Look(result).player(actor).pawnSite, Some(coast))
    assertEquals(Look(result).player(actor).board.favor, 0)
    assertEquals(Look(result).player(rival).board.favor,
      Look(ready).player(rival).board.favor + 1)
    assertEquals(supplyOf(result), 7 - 1)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("the rule covers every site the ruler holds, Toll Roads' own included"):
    val result = after(travel(passRuled(rivalRules), plains(1)).toOption.get)
    assertEquals(Look(result).player(actor).board.favor, 0)
    assertEquals(Look(result).player(rival).board.favor,
      Look(rivalRules).player(rival).board.favor + 1)

  test("a traveller who cannot pay does not get the destination"):
    val broke = Table.from(rivalRules).favor(actor, 0).unchecked
    assertEquals(candidates(broke).get(coast), None)
    assert(travel(broke, coast).isLeft)
    // A site the rival does not rule is still offered.
    assert(candidates(broke).contains(plains(2)))

  test("a destination the ruler does not rule costs nothing extra"):
    val result = after(travel(rivalRules, plains(2)).toOption.get)
    assertEquals(Look(result).player(actor).board.favor, 1)

  test("the ruler itself travels for free"):
    val ready = Table.from(denizenAt(board(), toll, plains(1))).favor(actor, 1)
      .unchecked
    val mine = ruledBy(ruledBy(ready, plains(1), actor), coast, actor)
    val result = after(travel(mine, coast).toOption.get)
    assertEquals(Look(result).player(actor).board.favor, 1)

  test("when bandits rule, the favor is burnt"):
    val ready = Table.from(denizenAt(board(), toll, plains(1))).favor(actor, 1)
      .unchecked
    val result = after(travel(ready, coast).toOption.get)
    assertEquals(Look(result).player(actor).board.favor, 0)
    assertEquals(Look(result).player(rival).board.favor,
      Look(ready).player(rival).board.favor)
    assertEquals(candidates(Table.from(ready).favor(actor, 0).unchecked)
      .get(coast), None)

  test("a facedown Toll Roads is not active"):
    // Toll Roads charges from the site it stands on, and only faceup there.
    val ready = Table.from(facedownAt(board(), toll, plains(1))).favor(actor, 1)
      .unchecked
    val ruled = ruledBy(ruledBy(ready, plains(1), rival), coast, rival)
    val result = after(travel(ruled, coast).toOption.get)
    assertEquals(Look(result).player(actor).board.favor, 1)

  // ---- Lines ----

  private val power = TollRoads.forCatalog(catalog)
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("the toll paid to the ruler is written as the Roads' line"):
    assertEquals(said(travel(rivalRules, coast).toOption.get.events),
      Vector(NoteText.Said("paid",
        s"${actor.value} paid 1 favor to ${rival.value}.", covers = false)))

  test("a toll burnt for bandits is written; a free Travel writes nothing"):
    val ready = Table.from(denizenAt(board(), toll, plains(1))).favor(actor, 1)
      .unchecked
    assertEquals(said(travel(ready, coast).toOption.get.events),
      Vector(NoteText.Said("burned", s"${actor.value} burned 1 favor.",
        covers = false)))
    assertEquals(said(travel(rivalRules, plains(2)).toOption.get.events),
      Vector.empty)
