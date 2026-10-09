package oathdigital.gameplay.powers

import oathdigital.gameplay.powers.travel.{MinesSite, NarrowPassSite,
  TravelFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
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
