package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.gameplay.powers.economy.{AnimalPlaymates, AwaitedReturn,
  Birdsong, CupOfPlenty, Curfew, Defame, Downtrodden, FriendlyFamiliar,
  GolemLegions, InitiationRite, MapLibrary, MountedLibrary, MovingMarket,
  OldSongs, Pressgangs, RowdyPub, SecretSignal, SkilledMerchants,
  SmallFriends, SpitefulMirror, TheOldOak, VillageIdiot, VowOfBeastkin,
  VowOfPoverty, VowOfWandering, VowOfWanderingMuster}
import oathdigital.gameplay.powers.recover.{RelicWorship, SilverCharm}
import oathdigital.gameplay.powers.search.{Augury, CropRotation, Disciples,
  ErrandBoy, ForcedLabor, HuntingParty, LandWarden, Mushrooms, NewsFromAfar,
  Observatory, PaintedTrumpet, SpinningBee, TruthfulHarp, WineOfWelcome}

/** The Search, Trade, Muster and Recover modifiers, registered together with
  * the rules that change those actions: Initiation Rite's Muster cost, the
  * tolls of Curfew and Forced Labor, the Vows' limits and extra warbands,
  * and Vow of Wandering's ban on placing warbands. Each names its card, so
  * all are always present.
  */
object ActionModifiers:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector(Augury.forCatalog(catalog)) ++
      Vector(TruthfulHarp.forCatalog(catalog)) ++
      Vector(Disciples.forCatalog(catalog)) ++
      Vector(CropRotation.forCatalog(catalog)) ++
      Vector(NewsFromAfar.forCatalog(catalog)) ++
      Vector(HuntingParty.forCatalog(catalog)) ++
      Vector(Observatory.forCatalog(catalog)) ++
      Vector(ErrandBoy.forCatalog(catalog)) ++
      Vector(Mushrooms.forCatalog(catalog)) ++
      Vector(WineOfWelcome.forCatalog(catalog)) ++
      Vector(PaintedTrumpet.forCatalog(catalog)) ++
      Vector(SpinningBee.forCatalog(catalog)) ++
      Vector(LandWarden.forCatalog(catalog)) ++
      Vector(CupOfPlenty.forCatalog(catalog)) ++
      Vector(AnimalPlaymates.forCatalog(catalog)) ++
      Vector(Birdsong.forCatalog(catalog)) ++
      Vector(TheOldOak.forCatalog(catalog)) ++
      Vector(RowdyPub.forCatalog(catalog)) ++
      Vector(VillageIdiot.forCatalog(catalog)) ++
      Vector(Downtrodden.forCatalog(catalog)) ++
      Vector(SpitefulMirror.forCatalog(catalog)) ++
      Vector(MovingMarket.forCatalog(catalog)) ++
      Vector(AwaitedReturn.forCatalog(catalog)) ++
      Vector(MountedLibrary.forCatalog(catalog)) ++
      Vector(Defame.forCatalog(catalog)) ++
      Vector(GolemLegions.forCatalog(catalog)) ++
      Vector(MapLibrary.forCatalog(catalog)) ++
      Vector(SkilledMerchants.forCatalog(catalog)) ++
      Vector(SmallFriends.forCatalog(catalog),
        FriendlyFamiliar.forCatalog(catalog),
        SecretSignal.forCatalog(catalog)) ++
      Vector(Pressgangs.forCatalog(catalog)) ++
      Vector(OldSongs.forCatalog(catalog)) ++
      Vector(Curfew.forCatalog(catalog)) ++
      Vector(ForcedLabor.forCatalog(catalog)) ++
      Vector(InitiationRite) ++
      Vector(VowOfBeastkin.forCatalog(catalog)) ++
      Vector(VowOfWanderingMuster, VowOfWandering) ++
      Vector(VowOfPoverty) ++
      Vector(RelicWorship.forCatalog(catalog)) ++
      Vector(SilverCharm.forCatalog(catalog))
