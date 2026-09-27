package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver._
import oathdigital.model.PowerWindow

/** Reviewed Rest classifications and their procedure-specific callables.
  * League Treaty and Vow of Obedience are audited through their walker
  * contributions instead.
  */
object RestPowers:
  private def rest = Vector(ReviewedHandler.automatic(PowerWindow.RestStart))

  object Naysayers extends ReviewedPower("denizen.naysayers", None, rest)
  object SilverTongue extends ReviewedPower("denizen.silver-tongue", None,
    Vector(ReviewedHandler.automatic(PowerWindow.SearchModifierSelection)))
  object Insomnia extends ReviewedPower("denizen.insomnia", None, rest)
  object VowOfPoverty extends ReviewedPower("denizen.vow-of-poverty", None,
    Vector(
      ReviewedHandler.selected(PowerWindow.MusterModifierSelection),
      ReviewedHandler.selected(PowerWindow.TradeModifierSelection),
      ReviewedHandler.automatic(PowerWindow.RestStart)))

  val powers: Vector[Power] = Vector(Naysayers, SilverTongue, Insomnia,
    VowOfPoverty)
