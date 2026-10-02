package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class TollRoadsSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val toll = CatalogNames.denizen("Toll Roads")
  private val rival = Table.p2

  /** Toll Roads stands at `provincePlains`, and `rival` rules it and the
    * coast. The actor, at Buried Giant, the Cradle's plains, holds 1 favor and
    * travels to the coast.
    */
  private def rivalRules: ReadyGame =
    Table.from(board(supply = 7)).denizen(toll, at = provincePlains)
      .favor(actor, 1).warbandsAt(provincePlains, rival, 1)
      .warbandsAt(coast, rival, 1).unchecked

  test("an enemy pays the ruler 1 favor to travel to a site the ruler rules"):
    val ready = rivalRules
    val done = accepted(travel(ready, coast))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).player(actor).pawnSite, Some(coast))
    assertEquals(Look(result).favor(actor), 0)
    assertEquals(Look(result).favor(rival), Look(ready).favor(rival) + 1)
    // 7 Supply, less the printed 1 for a Travel within the Cradle.
    assertEquals(Look(result).supply(actor), 7 - 1)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("the rule covers every site the ruler holds, Toll Roads' own included"):
    val passRuled = Table.from(rivalRules).warbandsAt(pass, actor, 1).unchecked
    val done = accepted(travel(passRuled, provincePlains))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).favor(actor), 0)
    assertEquals(Look(result).favor(rival), Look(rivalRules).favor(rival) + 1)

  test("a traveller who cannot pay does not get the destination"):
    val broke = Table.from(rivalRules).favor(actor, 0).unchecked
    assertEquals(candidates(broke).get(coast), None)
    assert(travel(broke, coast).isLeft)
    // A site the rival does not rule is still offered.
    assert(candidates(broke).contains(hinterlandPlains))

  test("a destination the ruler does not rule costs nothing extra"):
    val done = accepted(travel(rivalRules, hinterlandPlains))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).favor(actor), 1)

  test("the ruler itself travels for free"):
    val mine = Table.from(board()).denizen(toll, at = provincePlains)
      .favor(actor, 1).warbandsAt(provincePlains, actor, 1)
      .warbandsAt(coast, actor, 1).unchecked
    val result = Outcomes.ready(accepted(travel(mine, coast)).state)
    assertEquals(Look(result).favor(actor), 1)

  test("when bandits rule, the favor is burnt"):
    val ready = Table.from(board()).denizen(toll, at = provincePlains)
      .bandits(provincePlains, 1).bandits(coast, 1).favor(actor, 1).unchecked
    val result = Outcomes.ready(accepted(travel(ready, coast)).state)
    assertEquals(Look(result).favor(actor), 0)
    assertEquals(Look(result).favor(rival), Look(ready).favor(rival))
    assertEquals(candidates(Table.from(ready).favor(actor, 0).unchecked)
      .get(coast), None)

  test("a facedown Toll Roads is not active"):
    // Toll Roads charges from the site it stands on, and only faceup there.
    val ruled = Table.from(board())
      .denizen(toll, at = provincePlains, facedown = true).favor(actor, 1)
      .warbandsAt(provincePlains, rival, 1).warbandsAt(coast, rival, 1)
      .unchecked
    val result = Outcomes.ready(accepted(travel(ruled, coast)).state)
    assertEquals(Look(result).favor(actor), 1)

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
      .bandits(provincePlains, 1).bandits(coast, 1).favor(actor, 1).unchecked
    assertEquals(said(accepted(travel(ready, coast)).events),
      Vector(NoteText.Said("burned", s"${actor.value} burned 1 favor.",
        covers = false)))
    assertEquals(said(accepted(travel(rivalRules, hinterlandPlains)).events),
      Vector.empty)
