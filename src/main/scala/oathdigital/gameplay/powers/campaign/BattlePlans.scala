package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The Campaign battle plans the first batch's engine ported, registered
  * together: the title's defense, Outriders, Brass Army and Watchdog. A relic
  * plan whose card is absent from `catalog` is omitted; a denizen plan names
  * its card, and the title's defense, which no card prints, is always present.
  */
object BattlePlans:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector[ContributingPower](TitleDefensePlan.plan) ++
      Vector(Outriders) ++
      BrassArmy.forCatalog(catalog).toVector ++
      Vector(Watchdog)
