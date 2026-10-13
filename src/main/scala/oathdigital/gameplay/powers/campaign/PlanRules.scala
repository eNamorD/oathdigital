package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans that reach beyond the plan window, the rules that tax them,
  * and the persistent rules of a Campaign, registered together: Sticky Fire (a
  * question in the losses), Warning Signals (a decision of its own and a discard
  * at the end), Gleaming Armor and Insect Swarm (an added cost on the enemy's
  * plans), Horse Archers, Storm Caller, Rival Khan, Great Crusade and Pledge of
  * Defense (a discard at the end), Bag of Siegeworks (the defense scored again),
  * Rain Boots (the defense scored again and a discard at the end), The Great
  * Levy (the attack scored again), Garrison Armory (the targets' warbands added
  * again), Hospital (killed warbands saved until the end), Book Burning (secrets
  * burnt at the end), Rusting Ray and Fae Battalion (the attack scored again),
  * War Tortoise (the attack or the defense scored again), Lancers (the attack
  * scored again and a discard at the end), Fearsome General (the attack scored
  * again), Weeping Banner (the attack scored again, two warbands lost per
  * skull), Kindred Warriors and Rangers (the attack scored again), Mounted
  * Patrol (the attack pool halved before the roll and a discard at the end),
  * Shield Wall (the defender's whole force killed), Billowing Fog and Traveling
  * Doctor (the defeat's losses cancelled), Cursed Cauldron (warbands for the
  * enemy's dead), Salt the Earth (a targeted site's cards buried), Sealing Ward
  * (defense dice for targeted relics), Unstable Summon (an amount asked when
  * applied), Arcane Armor (defense dice set before the roll), Mountain Giant
  * (an amount asked when applied, and a discard at the end on three),
  * Herald (a favor after another player's Campaign) and Vow of Union
  * (warbands moved at the start of any Campaign). Each names its card, so all
  * are always present.
  */
object PlanRules:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector(StickyFire) ++
      Vector(WarningSignals.forCatalog(catalog)) ++
      Vector(GleamingArmor.forCatalog(catalog)) ++
      Vector(InsectSwarm.forCatalog(catalog)) ++
      Vector(HorseArchers.forCatalog(catalog)) ++
      Vector(StormCaller.forCatalog(catalog)) ++
      Vector(RivalKhan.forCatalog(catalog)) ++
      Vector(GreatCrusade.forCatalog(catalog)) ++
      Vector(PledgeOfDefense.forCatalog(catalog)) ++
      Vector(BagOfSiegeworks) ++
      Vector(RainBoots.forCatalog(catalog)) ++
      Vector(GreatLevy) ++
      Vector(GarrisonArmory) ++
      Vector(Hospital) ++
      Vector(BookBurning) ++
      Vector(RustingRay) ++
      Vector(FaeBattalion) ++
      Vector(WarTortoise) ++
      Vector(Lancers.forCatalog(catalog)) ++
      Vector(WeepingBanner) ++
      Vector(FearsomeGeneral) ++
      Vector(KindredWarriors.forCatalog(catalog)) ++
      Vector(Rangers) ++
      Vector(MasterAtArms.forCatalog(catalog)) ++
      Vector(UnstableSummon) ++
      Vector(ArcaneArmor) ++
      Vector(MountainGiant.forCatalog(catalog)) ++
      Vector(MountedPatrol.forCatalog(catalog)) ++
      Vector(ShieldWall) ++
      Vector(BillowingFog) ++
      Vector(TravelingDoctor.forCatalog(catalog)) ++
      Vector(CursedCauldron) ++
      Vector(SaltTheEarth.forCatalog(catalog)) ++
      Vector(SealingWard.forCatalog(catalog)) ++
      Vector(Herald.forCatalog(catalog)) ++
      Vector(VowOfUnion)
