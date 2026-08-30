package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver._

object TravelPowers {
  private val inherent = Set("site.broken-peaks.mountain",
    "site.desolate-shore.coast", "site.fair-isle.coast",
    "site.fair-isle.island", "site.green-shore.coast",
    "site.headwaters.mountain", "site.hidden-place.mountain",
    "site.mines.mountain", "site.narrow-pass.pass", "site.rocky-coast.coast",
    "site.sunken-isles.coast", "site.sunken-isles.island",
    "site.tidal-marshes.coast")
  val registrations: Vector[RegisteredPower] = inherent.toVector.sorted.map(id =>
    PowerRegistration.automatic(id, Some(MajorActionType.Travel),
      Vector(PowerWindow.TravelCost), implemented = true))
}
