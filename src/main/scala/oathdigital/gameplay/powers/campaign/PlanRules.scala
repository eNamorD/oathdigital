package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans that reach beyond the plan window, registered together:
  * Sticky Fire, which asks a question in the losses, and Warning Signals, which
  * asks a decision of its own and is discarded at the end. A power whose card is
  * absent from `catalog` is omitted.
  */
object PlanRules {
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    StickyFire.forCatalog(catalog).toVector ++
      WarningSignals.forCatalog(catalog).toVector
}
