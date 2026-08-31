package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powers.rest.LeagueTreatyPower

/** Reviewed Rest classifications and their procedure-specific callables. */
object RestPowers {
  private def rest = Vector(ReviewedHandler.automatic(PowerWindow.RestStart))

  object Naysayers extends ReviewedPower("denizen.naysayers", None, rest)
  object SilverTongue extends ReviewedPower("denizen.silver-tongue", None, rest)
  object Insomnia extends ReviewedPower("denizen.insomnia", None, rest)
  object VowOfObedience extends ReviewedPower(
    "denizen.vow-of-obedience", None, rest)
  object VowOfPoverty extends ReviewedPower("denizen.vow-of-poverty", None,
    Vector(
      ReviewedHandler.selected(PowerWindow.MusterModifierSelection),
      ReviewedHandler.selected(PowerWindow.TradeModifierSelection),
      ReviewedHandler.automatic(PowerWindow.RestStart)))

  val powers: Vector[Power] = Vector(Naysayers, SilverTongue, Insomnia,
    VowOfObedience, VowOfPoverty, LeagueTreatyPower)
}
