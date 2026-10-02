package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The Campaign battle plans the first batch's engine ported, registered
  * together: the title's defense, Outriders, Brass Army and Watchdog. All are
  * always present: each card plan names its card, and no card prints the
  * title's defense.
  */
object BattlePlans:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector[ContributingPower](TitleDefensePlan.plan) ++
      Vector(Outriders) ++
      Vector(BrassArmy) ++
      Vector(Watchdog)
