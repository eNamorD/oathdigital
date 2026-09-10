package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.recover.CatacombsContribution
import oathdigital.gameplay.powers.travel.TravelSitePowers
import oathdigital.gameplay.walker.WalkerPowers

/** The real catalog of `ContributingPower`s wired onto the generic walker
  * seam (Task 5's first entry: Catacombs). Catalog-parameterized like
  * `ReviewedPowerCatalog.resolver`/`registry`: a contribution that carries a
  * catalog-specific card id must be resolved against the same catalog the
  * caller is running. A power whose card is absent from `catalog` (e.g. a
  * synthetic test catalog) is simply omitted, not a construction failure.
  */
object WalkerPowerCatalog {
  def default(catalog: ExecutableCatalog): WalkerPowers =
    WalkerPowers(CatacombsContribution.forCatalog(catalog).toVector ++
      TravelSitePowers.forCatalog(catalog))
}
