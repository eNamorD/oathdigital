package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver._

object ActionPowers {
  private val whenPlayed = Set(
    "denizen.dazzle", "denizen.revelation", "denizen.threatening-roar",
    "denizen.animal-host", "denizen.a-small-favor", "denizen.key-to-the-city",
    "denizen.charlatan", "denizen.blackmail", "denizen.dissent",
    "denizen.false-prophet", "denizen.family-heirloom", "denizen.fabled-feast",
    "denizen.salad-days", "denizen.the-gathering", "denizen.faithful-friend",
    "denizen.great-herd", "denizen.pilgrimage", "denizen.twin-brother",
    "denizen.garrison", "denizen.royal-tax", "denizen.bewitch",
    "denizen.wizard-s-conclave", "denizen.long-lost-heir", "denizen.true-oath",
    "denizen.autumn-wind", "denizen.shifting-fog", "denizen.royal-ambitions",
    "denizen.riots", "denizen.bandit-chief", "denizen.reliquary-raid",
    "denizen.bandit-prince", "denizen.a-round-of-ale", "denizen.favored-son",
    "denizen.town-meeting", "denizen.ancient-pact", "denizen.search-party",
    "denizen.call-for-help")
  private val boundary = Set("banner.peoples-favor.grand-council",
    "banner.darkest-secret.festival", "foundation.altered")

  val registrations: Vector[RegisteredPower] =
    whenPlayed.toVector.sorted.map(id => PowerRegistration.automatic(id, None,
      Vector(PowerWindow.ActionCardPlayed))) ++
    boundary.toVector.sorted.map(id => PowerRegistration.automatic(id, None,
      Vector(PowerWindow.ActionAfterMajorAction, PowerWindow.WakeBoundary,
        PowerWindow.RestStart)))
}
