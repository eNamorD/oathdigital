package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans and rules that restrict which plans a side of a
  * Campaign may choose (catalog batch 6, slice 1), registered together:
  * Specialist and Code of Honor. Each names its card, so all are always
  * present.
  */
object PlanRestrictions:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector(Specialist, CodeOfHonor)
