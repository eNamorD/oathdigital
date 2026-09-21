package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.gameplay.powers.economy.{CupOfPlenty, RowdyPub}
import oathdigital.gameplay.powers.recover.RelicWorship
import oathdigital.gameplay.powers.search.{Augury, TruthfulHarp}

/** The Search, Trade, Muster and Recover modifiers, registered together. A
  * power whose card is absent from `catalog` is omitted.
  */
object ActionModifiers {
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Augury.forCatalog(catalog).toVector ++
      TruthfulHarp.forCatalog(catalog).toVector ++
      CupOfPlenty.forCatalog(catalog).toVector ++
      RowdyPub.forCatalog(catalog).toVector ++
      RelicWorship.forCatalog(catalog).toVector
}
