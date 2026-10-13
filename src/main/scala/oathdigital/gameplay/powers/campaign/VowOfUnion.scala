package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
import oathdigital.model._

object VowOfUnionCard extends Denizen(DenizenId("185"), "Vow of Union", Suit.Beast) with Locked with AdviserOnly:
  val campaign = PrintedPower(PowerId("denizen.vow-of-union.campaign"),
    persistent = true, cost = Cost.free,
    text = "At the start of **any** campaign, you may move any number " +
      "of warbands from sites you rule in your region to your " +
      "board or vice versa.")
  val restriction = PrintedPower(PowerId("denizen.vow-of-union.restriction"),
    persistent = true, cost = Cost.free,
    text = "You may only have [suit-beast] advisers—-bury other " +
      "advisers you have or would gain.")
  val powers: Vector[PrintedPower] = Vector(campaign, restriction)
