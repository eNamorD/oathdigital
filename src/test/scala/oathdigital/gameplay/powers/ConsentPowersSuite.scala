package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction, Waiver}
import oathdigital.gameplay.operations.{GrandScepter, HallOfMinisters}
import oathdigital.gameplay.powers.cardplay.SecretPolice
import oathdigital.gameplay.powers.economy.Curfew
import oathdigital.gameplay.powers.search.ForcedLabor
import oathdigital.gameplay.powers.travel.{GraspingVines, MinesSite,
  NarrowPassSite, NarrowPassSitePower, TollRoads, TravelFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** Which powers can be waived, and who may waive them (consent design,
  * rulings 1, 4 and 16). */
class ConsentPowersSuite extends munit.FunSuite:
  import TravelFixture.{board, on, pass}
  private val powers = ConsentPowers.default(catalog)
  private val ruled = on(board())(_.warbandsAt(pass, p2, 1))

  test("the Narrow Pass is waivable, named for its site"):
    assert(powers.waiver(NarrowPassSite.pass).nonEmpty)
    assertEquals(powers.source(NarrowPassSite.pass),
      Some(PowerSourceRef.Site(NarrowPassSite.id)))

  test("a power that restricts nobody's enemies is not waivable"):
    assertEquals(powers.waiver(MinesSite.mountain), None)
    assertEquals(powers.source(MinesSite.mountain), None)

  test("the Pass's granter is the player who rules it, and nobody under " +
      "bandits"):
    assertEquals(powers.granter(ruled, NarrowPassSite.pass), Some(p2))
    assertEquals(powers.granter(board(), NarrowPassSite.pass), None)

  test("a player rules the waivable powers of the sites they rule"):
    assertEquals(powers.ruledBy(ruled, p2), Vector(NarrowPassSite.pass))
    assertEquals(powers.ruledBy(ruled, p1), Vector.empty)

  /** A power waivable only on its options. */
  private object TargetBarrier extends ContributingPower:
    val id: PowerId = PowerId("test.target-barrier")
    val source: RuleSourceRef = RuleSourceRef.Site(NarrowPassSite.id)
    private val at = PowerSourceRef.Site(NarrowPassSite.id)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.CampaignTargetSelection -> Vector(OptionRestriction(
        (_, _) => None, Contribution.silent, Some(Waiver(_ => Some(p2), at,
          (_, _) => NarrowPassSitePower.noTargetConsent(at))))))

  test("a power waivable only on its options is waivable"):
    val only = new ConsentPowers(Vector(TargetBarrier))
    assertEquals(only.granter(ruled, TargetBarrier.id), Some(p2))

  test("Secret Police is waivable, its granter the player who rules its " +
      "site, and nobody under bandits"):
    val police = CatalogNames.denizen("Secret Police")
    val home = CatalogNames.site("Ancient City")
    val policed = Table.start.denizen(police, at = home)
    assertEquals(powers.source(SecretPolice.id),
      Some(PowerSourceRef.Card(police)))
    assertEquals(powers.granter(policed.warbandsAt(home, p2, 2).ready,
      SecretPolice.id), Some(p2))
    assertEquals(powers.granter(policed.bandits(home, 2).ready,
      SecretPolice.id), None)

  test("the Hall of Ministers is waivable, its granter the player who rules " +
      "its site"):
    val hall = CatalogNames.edifice("Hall of Ministers")
    val woods = CatalogNames.site("Deep Woods")
    val ready = Table.start.edifice(hall, EdificeSide.Intact, at = woods)
      .warbandsAt(woods, p2, 1).ready
    assertEquals(powers.source(HallOfMinisters.id),
      Some(PowerSourceRef.Card(hall)))
    assertEquals(powers.granter(ready, HallOfMinisters.id), Some(p2))
    assertEquals(powers.ruledBy(ready, p2), Vector(HallOfMinisters.id))

  test("the Grand Scepter's restriction is not waivable"):
    assertEquals(powers.waiver(GrandScepter.id), None)

  test("the toll and penalty cards are waivable, each named for its card"):
    Vector(TollRoads.step, ForcedLabor.step, Curfew.step, GraspingVines.step)
      .foreach { step =>
        assert(powers.waiver(step.power).nonEmpty, step.power)
        assertEquals(powers.source(step.power),
          Some(PowerSourceRef.Card(step.card)))
      }

  test("Toll Roads' granter is the player who rules it, and nobody under " +
      "bandits"):
    val toll = CatalogNames.denizen("Toll Roads")
    val ruledByP2 = on(board())(_.denizen(toll,
      at = TravelFixture.provincePlains)
      .warbandsAt(TravelFixture.provincePlains, p2, 1))
    assertEquals(powers.granter(ruledByP2, TollRoads.id), Some(p2))
    assertEquals(powers.granter(on(board())(_.denizen(toll,
      at = TravelFixture.provincePlains)), TollRoads.id), None)
