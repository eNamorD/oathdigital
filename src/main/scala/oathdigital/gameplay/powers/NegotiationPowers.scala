package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver._
import oathdigital.model.PowerWindow

object NegotiationPowers {
  private def handler = Vector(ReviewedHandler.automatic(PowerWindow.NegotiationOffer))
  object CouncilArbiter extends ReviewedPower("denizen.council-arbiter", None, handler)
  object DeedWriter extends ReviewedPower("denizen.deed-writer", None, handler)
  object TravelingNegotiator extends ReviewedPower("denizen.traveling-negotiator", None, handler)
  object E19Intact extends ReviewedPower("edifice.e19.intact", None, handler)
  object E19Ruined extends ReviewedPower("edifice.e19.ruined", None, handler)
  object E21Intact extends ReviewedPower("edifice.e21.intact", None, handler)
  object GrandScepter extends ReviewedPower("relic.the-grand-scepter.negotiation", None, handler)
  object HighPriest extends ReviewedPower("legacy.high-priest", None, handler)
  val powers: Vector[Power] = Vector(CouncilArbiter, DeedWriter,
    TravelingNegotiator, E19Intact, E19Ruined, E21Intact, GrandScepter, HighPriest)
}
