package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans that reach beyond the plan window, and the rules that tax
  * them, registered together: Sticky Fire (a question in the losses), Warning
  * Signals (a decision of its own and a discard at the end), Gleaming Armor and Insect Swarm
  * (an added cost on the enemy's plans), Horse Archers, Storm Caller, Rival Khan,
  * Great Crusade and Pledge of Defense (a discard at the end), Bag of
  * Siegeworks (the defense scored again), Rain Boots (the defense scored again
  * and a discard at the end), The Great Levy (the attack scored again),
  * Garrison Armory (the targets' warbands added again) and Hospital (killed
  * warbands saved until the end). A power whose card is absent from `catalog`
  * is omitted.
  */
object PlanRules:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    StickyFire.forCatalog(catalog).toVector ++
      WarningSignals.forCatalog(catalog).toVector ++
      GleamingArmor.forCatalog(catalog).toVector ++
      InsectSwarm.forCatalog(catalog).toVector ++
      HorseArchers.forCatalog(catalog).toVector ++
      StormCaller.forCatalog(catalog).toVector ++
      RivalKhan.forCatalog(catalog).toVector ++
      GreatCrusade.forCatalog(catalog).toVector ++
      PledgeOfDefense.forCatalog(catalog).toVector ++
      BagOfSiegeworks.forCatalog(catalog).toVector ++
      RainBoots.forCatalog(catalog).toVector ++
      GreatLevy.forCatalog(catalog).toVector ++
      GarrisonArmory.forCatalog(catalog).toVector ++
      Hospital.forCatalog(catalog).toVector
