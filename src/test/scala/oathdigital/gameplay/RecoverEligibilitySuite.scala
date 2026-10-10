package oathdigital.gameplay

import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, MenuPlay, Table}
import oathdigital.testkit.Table.p1

/** Covers Recover's availability in the Act menu: the menu delegates to the
  * same Recover procedure builder as command execution, and relic
  * availability does not gate the action.
  */
class RecoverEligibilitySuite extends munit.FunSuite:
  /** p1, in Act, stands at Broken Peaks, whose Recover difficulty is 4. On
    * the quiet table the site holds no relic and no card, Catacombs
    * included. */
  private val recoverSite = CatalogNames.site("Broken Peaks")
  private def atRecoverSite: Table = Table.start.pawn(p1, at = recoverSite)

  private def recoverAvailable(ready: ReadyGame): Boolean =
    MenuPlay.availableInMenu(catalog, ready, ActionRef.Recover)

  test("Recover is available without a relic or Catacombs"):
    assertEquals(RecoverRules.difficulty(catalog, recoverSite), Some(4))
    assert(recoverAvailable(atRecoverSite.ready))

  test("Recover is not available without the Supply to pay for it"):
    def withSupply(supply: Int) = atRecoverSite.supply(p1, supply).ready
    assert(recoverAvailable(withSupply(1)))
    assert(!recoverAvailable(withSupply(0)))
