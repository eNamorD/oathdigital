package oathdigital.gameplay.powers.targeting

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The persistent rules that keep a player, or what they hold, from being
  * targeted by a Raid, a Challenge or a Conspiracy, or taken, registered
  * together. Each names its card, so all are always present.
  */
object TargetProtections:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector(CircletOfCommand.forCatalog(catalog)) ++
      Vector(ForgottenVault.forCatalog(catalog)) ++
      Vector(LostTongue.forCatalog(catalog)) ++
      Vector(OakenFortress.forCatalog(catalog)) ++
      Vector(RottingFortress.forCatalog(catalog))
