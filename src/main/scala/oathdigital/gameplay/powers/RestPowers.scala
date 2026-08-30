package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver._

object RestPowers {
  private val ids = Set("denizen.naysayers",
    "denizen.silver-tongue", "denizen.insomnia", "denizen.vow-of-obedience")
  val registrations: Vector[RegisteredPower] = ids.toVector.sorted.map(id =>
    PowerRegistration.automatic(id, None, Vector(PowerWindow.RestStart))) :+
    PowerRegistration.automaticAt("denizen.vow-of-poverty", None,
      Vector(PowerWindow.MusterModifierSelection,
        PowerWindow.TradeModifierSelection, PowerWindow.RestStart),
      Set(PowerWindow.RestStart))
}
