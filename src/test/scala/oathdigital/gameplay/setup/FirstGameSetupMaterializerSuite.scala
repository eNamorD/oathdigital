package oathdigital.gameplay.setup

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** `relicOrder` is implemented-first (`ShufflePolicy.implementedFirst`):
  * site relic slots fill from its top, same as the drawable deck they are
  * carved out of, so a Recover at a freshly-set-up site can turn up an
  * implemented relic exactly as reliably as a draw from the deck would.
  */
class FirstGameSetupMaterializerSuite extends munit.FunSuite {
  private val materializer = new FirstGameSetupMaterializer(catalog)

  test("site relic slots are filled from the top of relicOrder, leaving the " +
      "remainder -- in the same order -- as the drawable deck") {
    val sites = catalog.sites.filter(_.relicSlots > 0)
      .sortBy(_.relicSlots)(Ordering[Int].reverse).take(3).map(_.id)
    assert(sites.nonEmpty, "fixture catalog needs at least one relic-slotted site")
    val totalSlots = sites.map(id => catalog.sites.find(_.id == id).get.relicSlots).sum
    val relicOrder = (1 to totalSlots + 5).map(i => RelicId(s"relic:test-$i")).toVector
    val plan = FirstGameSetupPlan(
      participants = Vector(
        FirstGameParticipant(PlayerId("p1"), LineageId("l1"), PlayerColor.Red)),
      firstPlayer = PlayerId("p1"),
      orderedSites = sites,
      denizenOrder = Vector.empty,
      worldDeckOrder = Vector.empty,
      relicOrder = relicOrder,
      homelandEdifices = Vector.empty)
    val material = materializer.materialize(plan, Vector.empty, Vector.empty)

    val placedRelics = sites.flatMap(id => material.map.sites(id).relics.map(_.id))
    assertEquals(placedRelics.toSet, relicOrder.take(totalSlots).toSet)
    assertEquals(material.commonCards.relicDeck, relicOrder.drop(totalSlots))
  }
}
