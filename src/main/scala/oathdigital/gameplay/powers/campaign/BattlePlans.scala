package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The Campaign battle plans the first batch's engine ported, registered
  * together: the title's defense, Outriders, Brass Army and Watchdog. A plan
  * whose card is absent from `catalog` is omitted, and the title's defense,
  * which no card prints, is always present.
  */
object BattlePlans {
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector[ContributingPower](TitleDefensePlan.plan) ++
      Outriders.forCatalog(catalog).toVector ++
      BrassArmy.forCatalog(catalog).toVector ++
      Watchdog.forCatalog(catalog).toVector
}
