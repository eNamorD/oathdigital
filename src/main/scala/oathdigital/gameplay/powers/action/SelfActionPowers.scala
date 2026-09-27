package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of catalog batch 2's slice 3, registered by
  * [[oathdigital.gameplay.powers.PhasePowerCatalog]] through this one object,
  * like [[DiceAndRelicDrawPowers]].
  */
object SelfActionPowers:
  val powers: Vector[PhasePower] = Vector(Tutor, ShiftingMap, DemonTail,
    WizardSchool)
