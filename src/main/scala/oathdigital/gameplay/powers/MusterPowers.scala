package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver._

object MusterPowers {
  val ids: Set[String] = Set(
    "denizen.initiation-rite", "denizen.map-library", "denizen.animal-playmates",
    "denizen.the-old-oak", "denizen.birdsong", "denizen.small-friends",
    "denizen.vow-of-beastkin", "denizen.downtrodden",
    "denizen.rowdy-pub", "denizen.pressgangs", "denizen.curfew",
    "denizen.knights-errant", "denizen.golem-legions", "denizen.defame",
    "denizen.friendly-familiar", "denizen.old-songs", "denizen.village-idiot",
    "denizen.skilled-merchants", "denizen.moving-market", "denizen.mounted-library",
    "relic.cup-of-plenty", "relic.spiteful-mirror", "edifice.e26.intact",
    "legacy.beloved")
  val registrations: Vector[RegisteredPower] = ids.toVector.sorted.map(id =>
    PowerRegistration.selected(id, None,
      Vector(PowerWindow.MusterModifierSelection,
        PowerWindow.TradeModifierSelection), implemented = false))
}
