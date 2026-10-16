package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, MenuPlay, Table}
import oathdigital.testkit.Table.{p1, p2}

/** A Forge whose every split places on a Beast card, under an enemy's
  * Forest Council (catalog batch 8, slice 10, part 2): the walker tries the
  * split, finds no answer survives, and refuses the Forge before anything
  * is spent. */
class ForestCouncilForgeSuite extends munit.FunSuite:
  private val forgeSite = CatalogNames.site("Deep Woods")
  private val councilSite = CatalogNames.site("Ancient City")
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))

  /** p1's Act at Deep Woods, which p1 rules, with 5 favor and 5 faceup
    * secrets, and three faceup empty denizens there, Errand Boy (Beast)
    * among them; Forest Council at Ancient City, ruled by `councilRuler`.
    * Deep Woods prints a Forge cost of 1 favor and 2 secrets, so each split
    * places one resource on each card. */
  private def forgeBesideBeast(councilRuler: PlayerId): ReadyGame =
    Vector("Wrestlers", "Martial Culture", "Errand Boy")
      .foldLeft(Table.start.turn(p1, Phase.Act).pawn(p1, forgeSite)
        .warbandsAt(forgeSite, p1, 1).favor(p1, 5).secrets(p1, faceUp = 5)
        .denizen(CatalogNames.denizen("Forest Council"), at = councilSite)
        .warbandsAt(councilSite, councilRuler, 1))((table, card) =>
        table.denizen(CatalogNames.denizen(card), at = forgeSite)).ready

  private def forgeOffered(ready: ReadyGame)(using munit.Location): Boolean =
    MenuPlay.availableInMenu(catalog, ready, ActionRef.Forge)

  test("an enemy's Forest Council refuses every split, so the Forge does " +
      "not start"):
    val ready = forgeBesideBeast(councilRuler = p2)
    assert(!forgeOffered(ready))
    assert(rules.startWalker(Ready(ready), ActionRef.Forge, p1).isLeft)

  test("under its ruler's own Forest Council the Forge starts"):
    val ready = forgeBesideBeast(councilRuler = p1)
    assert(forgeOffered(ready))
    assert(rules.startWalker(Ready(ready), ActionRef.Forge, p1).isRight)
