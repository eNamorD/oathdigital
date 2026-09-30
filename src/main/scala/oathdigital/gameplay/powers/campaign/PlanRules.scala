package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans that reach beyond the plan window, and the rule that taxes
  * them, registered together: Sticky Fire (a question in the losses), Warning
  * Signals (a decision of its own and a discard at the end), Gleaming Armor (an
  * added cost on the enemy's plans), Horse Archers, Storm Caller and Rival Khan
  * (a discard at the end), Bag of Siegeworks (the defense scored again) and
  * Hospital (killed warbands saved until the end). A power whose card is absent
  * from `catalog` is omitted.
  */
object PlanRules:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    StickyFire.forCatalog(catalog).toVector ++
      WarningSignals.forCatalog(catalog).toVector ++
      GleamingArmor.forCatalog(catalog).toVector ++
      HorseArchers.forCatalog(catalog).toVector ++
      StormCaller.forCatalog(catalog).toVector ++
      RivalKhan.forCatalog(catalog).toVector ++
      BagOfSiegeworks.forCatalog(catalog).toVector ++
      Hospital.forCatalog(catalog).toVector
