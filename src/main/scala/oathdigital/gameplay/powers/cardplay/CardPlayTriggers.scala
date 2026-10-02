package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The powers that act when a card is played, registered together: the
  * triggers that reward a play and the rules that forbid one. Each names its
  * card, so all are always present.
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
      Vector(SacredGround)
