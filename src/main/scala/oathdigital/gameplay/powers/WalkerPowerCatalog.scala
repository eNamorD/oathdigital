package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.recover.CatacombsContribution
import oathdigital.gameplay.powers.rest.{LeagueTreatyContribution, SilverTongue}
import oathdigital.gameplay.powers.travel.TravelSitePowers
import oathdigital.gameplay.powers.wake.TakeWealthLimit
import oathdigital.gameplay.powers.whenplayed.Dazzle
import oathdigital.gameplay.walker.WalkerPowers

/** The real catalog of `ContributingPower`s wired onto the generic walker
  * seam (Task 5's first entry: Catacombs). Catalog-parameterized like
  * `ReviewedPowerCatalog.resolver`/`registry`: a contribution that carries a
  * catalog-specific card id must be resolved against the same catalog the
  * caller is running. A power whose card is absent from `catalog` (e.g. a
  * synthetic test catalog) is simply omitted, not a construction failure.
  *
  * A power carrying no catalog id is simply always present: Take Wealth's
  * once-per-turn limit states a rulebook clause about whichever site the pawn
  * stands on, so there is nothing to look up and nothing to omit. It is inert
  * until an action declares `PowerWindow.WakeTakeWealth` (batch-1 Task 7),
  * since discovery keeps only powers that hook the window being gathered.
  * League Treaty is inert until Finish Rest walks its `RestReturnFavor` window.
  * Silver Tongue's restriction is inert until Search walks
  * `SearchPlayAdviser`.
  */
object WalkerPowerCatalog {
  def default(catalog: ExecutableCatalog): WalkerPowers =
    WalkerPowers(CatacombsContribution.forCatalog(catalog).toVector ++
      TravelSitePowers.forCatalog(catalog) ++
      LeagueTreatyContribution.forCatalog(catalog) ++
      SilverTongue.forCatalog(catalog) ++
      Dazzle.forCatalog(catalog) :+ TakeWealthLimit)
}
