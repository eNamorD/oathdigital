package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The powers that act when a card is played, registered together: the
  * triggers that reward a play and the rules that forbid one. A relic power
  * whose card is absent from `catalog` is omitted; a denizen power names its
  * card and is always present.
  */
object CardPlayTriggers:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector(WildCry.forCatalog(catalog)) ++
      Vector(WelcomingParty.forCatalog(catalog)) ++
      Vector(Gossip.forCatalog(catalog)) ++
      Vector(BookBinders.forCatalog(catalog)) ++
      Vector(SaddleMakers.forCatalog(catalog)) ++
      Vector(VowOfObedience) ++
      Vector(SecretPolice) ++
      SacredGround.forCatalog(catalog).toVector
