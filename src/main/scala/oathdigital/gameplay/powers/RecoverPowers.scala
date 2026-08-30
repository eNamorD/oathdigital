package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver._

object RecoverPowers {
  private val ids = Set("denizen.relic-worship", "edifice.e13.ruined",
    "edifice.e17.intact", "edifice.e17.ruined")
  val registrations: Vector[RegisteredPower] = ids.toVector.sorted.map(id =>
    PowerRegistration.automatic(id, Some(MajorActionType.Recover),
      Vector(PowerWindow.RecoverBeforeFirstRoll)))
}
