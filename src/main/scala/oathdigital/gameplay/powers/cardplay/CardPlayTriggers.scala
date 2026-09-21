package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The powers that trigger when a card is played, registered together. A power
  * whose card is absent from `catalog` is omitted.
  */
object CardPlayTriggers {
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    WildCry.forCatalog(catalog).toVector ++
      WelcomingParty.forCatalog(catalog).toVector ++
      Gossip.forCatalog(catalog).toVector
}
