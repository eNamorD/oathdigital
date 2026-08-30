package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver._

object NegotiationPowers {
  private val ids = Set("denizen.council-arbiter", "denizen.deed-writer",
    "denizen.traveling-negotiator", "edifice.e19.intact", "edifice.e19.ruined",
    "edifice.e21.intact", "relic.the-grand-scepter.negotiation",
    "legacy.high-priest")
  val registrations: Vector[RegisteredPower] = ids.toVector.sorted.map(id =>
    PowerRegistration.automatic(id, None, Vector(PowerWindow.NegotiationOffer)))
}
