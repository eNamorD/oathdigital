package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The When Played powers of catalog batches 3 and 4, which
  * `WalkerPowerCatalog` wires in. Each names its denizen card, so all are
  * always present. */
object WhenPlayedPowers:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector[ContributingPower]() ++
      Vector(ThreateningRoar.forCatalog(catalog)) ++
      Vector(Riots.forCatalog(catalog)) ++
      Vector(AnimalHost.forCatalog(catalog)) ++
      Vector(Charlatan) ++
      Vector(KeyToTheCity) ++
      Vector(BanditPrince) ++
      Vector(SaladDays) ++
      Vector(FabledFeast.forCatalog(catalog)) ++
      Vector(TownMeeting.forCatalog(catalog)) ++
      Vector(GreatHerd.forCatalog(catalog)) ++
      Vector(RoyalTax) ++
      Vector(Revelation) ++
      Vector(Blackmail.forCatalog(catalog)) ++
      Vector(Dissent.forCatalog(catalog))
