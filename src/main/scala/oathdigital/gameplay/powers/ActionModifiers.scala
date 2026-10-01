package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.gameplay.powers.economy.{AnimalPlaymates, Birdsong, CupOfPlenty,
  Downtrodden, InitiationRite, RowdyPub, VillageIdiot}
import oathdigital.gameplay.powers.recover.RelicWorship
import oathdigital.gameplay.powers.search.{Augury, TruthfulHarp}

/** The Search, Trade, Muster and Recover modifiers, registered together with
  * Initiation Rite, the rule that changes a Muster's cost. A power whose card
  * is absent from `catalog` is omitted.
  */
object ActionModifiers:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Augury.forCatalog(catalog).toVector ++
      TruthfulHarp.forCatalog(catalog).toVector ++
      CupOfPlenty.forCatalog(catalog).toVector ++
      AnimalPlaymates.forCatalog(catalog).toVector ++
      Birdsong.forCatalog(catalog).toVector ++
      RowdyPub.forCatalog(catalog).toVector ++
      VillageIdiot.forCatalog(catalog).toVector ++
      Downtrodden.forCatalog(catalog).toVector ++
      InitiationRite.forCatalog(catalog).toVector ++
      RelicWorship.forCatalog(catalog).toVector
