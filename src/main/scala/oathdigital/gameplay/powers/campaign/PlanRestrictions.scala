package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans and rules that restrict which plans a side of a
  * Campaign may choose (catalog batch 6, slice 1), registered together:
  * Specialist, Code of Honor, Marsh Spirit and Beast Tamer. Each names its
  * card, so all are always present.
  */
object PlanRestrictions:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector(Specialist, CodeOfHonor, MarshSpirit,
      BeastTamer.forCatalog(catalog))
