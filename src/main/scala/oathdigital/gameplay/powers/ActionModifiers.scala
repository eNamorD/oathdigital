package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.gameplay.powers.economy.{AnimalPlaymates, Birdsong, CupOfPlenty,
  Curfew, Downtrodden, InitiationRite, MovingMarket, RowdyPub, TheOldOak,
  VillageIdiot}
import oathdigital.gameplay.powers.recover.RelicWorship
import oathdigital.gameplay.powers.search.{Augury, CropRotation, Disciples,
  ForcedLabor, NewsFromAfar, TruthfulHarp}

/** The Search, Trade, Muster and Recover modifiers, registered together with
  * the rules that change those actions: Initiation Rite's Muster cost, the
  * tolls of Curfew and Forced Labor, and the Vows' limits. Each names its
  * card, so all are always present.
  */
object ActionModifiers:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector(Augury.forCatalog(catalog)) ++
      Vector(TruthfulHarp.forCatalog(catalog)) ++
      Vector(Disciples.forCatalog(catalog)) ++
      Vector(CropRotation.forCatalog(catalog)) ++
      Vector(NewsFromAfar.forCatalog(catalog)) ++
      Vector(CupOfPlenty.forCatalog(catalog)) ++
      Vector(AnimalPlaymates.forCatalog(catalog)) ++
      Vector(Birdsong.forCatalog(catalog)) ++
      Vector(TheOldOak.forCatalog(catalog)) ++
      Vector(RowdyPub.forCatalog(catalog)) ++
      Vector(VillageIdiot.forCatalog(catalog)) ++
      Vector(Downtrodden.forCatalog(catalog)) ++
      Vector(MovingMarket.forCatalog(catalog)) ++
      Vector(Curfew.forCatalog(catalog)) ++
      Vector(ForcedLabor.forCatalog(catalog)) ++
      Vector(InitiationRite) ++
      Vector(RelicWorship.forCatalog(catalog))
