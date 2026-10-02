package oathdigital.gameplay.powers.setup

import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}

class EdificeSetupSupportSuite extends munit.FunSuite:
  private val edifice = CatalogNames.edifice("Great Market")
  private val site = CatalogNames.site("Ancient City")

  test("siteOf finds an edifice staged on a side, and misses the other side"):
    val staged = Table.from(FirstGameSetupFixture.freshReady)
      .edifice(edifice, EdificeSide.Ruined, at = site).unchecked
    assertEquals(EdificeSetupSupport.siteOf(staged, edifice, EdificeSide.Ruined),
      Some(site))
    assertEquals(EdificeSetupSupport.siteOf(staged, edifice, EdificeSide.Intact),
      None)

  test("siteOf finds nothing when the edifice is not on the board"):
    // The fresh setup deals the edifice to no site.
    val cleared = FirstGameSetupFixture.freshReady
    assertEquals(EdificeSetupSupport.siteOf(cleared, edifice, EdificeSide.Ruined),
      None)
