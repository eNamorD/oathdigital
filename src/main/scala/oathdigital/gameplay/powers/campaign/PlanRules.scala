package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans that reach beyond the plan window, and the rules that tax
  * them, registered together: Sticky Fire (a question in the losses), Warning
  * Signals (a decision of its own and a discard at the end), Gleaming Armor and
  * Insect Swarm (an added cost on the enemy's plans), Horse Archers, Storm
  * Caller, Rival Khan, Great Crusade and Pledge of Defense (a discard at the end), Bag of
  * Siegeworks (the defense scored again), Rain Boots (the defense scored again
  * and a discard at the end), The Great Levy (the attack scored again),
  * Garrison Armory (the targets' warbands added again), Hospital (killed
  * warbands saved until the end) and Book Burning (secrets burnt at the
  * end). A relic power whose card is absent from `catalog` is
  * omitted; a denizen power names its card and is always present.
  */
object PlanRules:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    StickyFire.forCatalog(catalog).toVector ++
      Vector(WarningSignals.forCatalog(catalog)) ++
      Vector(GleamingArmor.forCatalog(catalog)) ++
      Vector(InsectSwarm.forCatalog(catalog)) ++
      Vector(HorseArchers.forCatalog(catalog)) ++
      Vector(StormCaller.forCatalog(catalog)) ++
      Vector(RivalKhan.forCatalog(catalog)) ++
      Vector(GreatCrusade.forCatalog(catalog)) ++
      Vector(PledgeOfDefense.forCatalog(catalog)) ++
      BagOfSiegeworks.forCatalog(catalog).toVector ++
      Vector(RainBoots.forCatalog(catalog)) ++
      Vector(GreatLevy) ++
      Vector(GarrisonArmory) ++
      Vector(Hospital) ++
      Vector(BookBurning)
