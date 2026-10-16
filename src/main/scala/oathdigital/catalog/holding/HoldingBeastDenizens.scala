package oathdigital.catalog.holding

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
import oathdigital.model.{Cost, DenizenId, PowerId, Suit}

// Generated from the retired runtime JSON (card classes slice 1).
// Hand-edited from here on; a card moves out when it is implemented.

object NewGrowthCard extends Denizen(DenizenId("188"), "New Growth", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.new-growth"),
    persistent = false, cost = Cost.free,
    text = "You may play [suit-beast] and [suit-hearth] cards to any " +
      "site _(that has space)_.")
  val powers: Vector[PrintedPower] = Vector(power)

object BrackenCard extends Denizen(DenizenId("197"), "Bracken", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.bracken"),
    persistent = false, cost = Cost(secret = 1),
    text = "You may put all of the cards you discard on the top or " +
      "bottom of any one discard pile _(even your region's)_.")
  val powers: Vector[PrintedPower] = Vector(power)

object WildAlliesCard extends Denizen(DenizenId("198"), "Wild Allies", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.wild-allies"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Campaign at any site with a [suit-beast] card. " +
      "Act as if your pawn is there. Spend no Supply and add your " +
      "warbands there to your force.")
  val powers: Vector[PrintedPower] = Vector(power)

object AutumnWindCard extends Denizen(DenizenId("213"), "Autumn Wind", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.autumn-wind"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** shuffle together all discard piles, then " +
      "deal these cards to the discard piles as evenly as " +
      "possible. You decide ties.")
  val powers: Vector[PrintedPower] = Vector(power)

object ForestWardenCard extends Denizen(DenizenId("218"), "Forest Warden", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.forest-warden"),
    persistent = false, cost = Cost.free,
    text = "If you play a [suit-beast] card to a site, you may bury a " +
      "card at the site first.")
  val powers: Vector[PrintedPower] = Vector(power)

object LongLostHeirCard extends Denizen(DenizenId("44"), "Long-Lost Heir", Suit.Beast) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.long-lost-heir"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** if you're an Exile, you may become a " +
      "Citizen. _Follow the steps in the rules and end your Act " +
      "Phase._")
  val powers: Vector[PrintedPower] = Vector(power)
