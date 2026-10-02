package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.gameplay.powers.economy.{AnimalPlaymates, Birdsong, CupOfPlenty,
  Downtrodden, InitiationRite, RowdyPub, TheOldOak, VillageIdiot}
import oathdigital.gameplay.powers.recover.RelicWorship
import oathdigital.gameplay.powers.search.{Augury, CropRotation, Disciples,
  TruthfulHarp}

/** The Search, Trade, Muster and Recover modifiers, registered together with
  * Initiation Rite, the rule that changes a Muster's cost. A relic power whose
  * card is absent from `catalog` is omitted; a denizen power names its card
  * and is always present.
  */
object ActionModifiers:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector(Augury.forCatalog(catalog)) ++
      TruthfulHarp.forCatalog(catalog).toVector ++
      Vector(Disciples.forCatalog(catalog)) ++
      Vector(CropRotation.forCatalog(catalog)) ++
      CupOfPlenty.forCatalog(catalog).toVector ++
      Vector(AnimalPlaymates.forCatalog(catalog)) ++
      Vector(Birdsong.forCatalog(catalog)) ++
      Vector(TheOldOak.forCatalog(catalog)) ++
      Vector(RowdyPub.forCatalog(catalog)) ++
      Vector(VillageIdiot.forCatalog(catalog)) ++
      Vector(Downtrodden.forCatalog(catalog)) ++
      Vector(InitiationRite) ++
      Vector(RelicWorship.forCatalog(catalog))
