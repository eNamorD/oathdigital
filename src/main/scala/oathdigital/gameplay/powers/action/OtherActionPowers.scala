package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of catalog batch 2's slice 4, actions on others,
  * registered by [[oathdigital.gameplay.powers.PhasePowerCatalog]] through
  * this one object, like [[SelfActionPowers]].
  */
object OtherActionPowers:
  val powers: Vector[PhasePower] =
    Vector[PhasePower](SpoiledSupplies, CharmingFriend)
