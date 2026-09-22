package oathdigital.gameplay.powers.setup

import oathdigital.gameplay.powers.CardStaging
import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.model._

class EdificeSetupSupportSuite extends munit.FunSuite {
  private val edifice = EdificeId("E02")
  private val site = FirstGameSetupFixture.sites.head

  test("siteOf finds an edifice staged on a side, and misses the other side") {
    val staged = CardStaging.without(FirstGameSetupFixture.freshReady, edifice)
      .updateCurrent(c => c.copy(map = c.map.copy(sites = c.map.sites.updated(
        site, c.map.sites(site).copy(denizens = c.map.sites(site).denizens :+
          EdificeState(edifice, EdificeSide.Ruined, Tokens.empty))))))
    assertEquals(EdificeSetupSupport.siteOf(staged, edifice, EdificeSide.Ruined),
      Some(site))
    assertEquals(EdificeSetupSupport.siteOf(staged, edifice, EdificeSide.Intact),
      None)
  }

  test("siteOf finds nothing when the edifice is not on the board") {
    val cleared = CardStaging.without(FirstGameSetupFixture.freshReady, edifice)
    assertEquals(EdificeSetupSupport.siteOf(cleared, edifice, EdificeSide.Ruined),
      None)
  }
}
