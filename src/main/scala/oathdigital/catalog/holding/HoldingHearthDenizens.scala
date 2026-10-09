package oathdigital.catalog.holding

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower,
  SiteOnly}
import oathdigital.model.{Cost, DenizenId, PowerId, Suit}

// Generated from the retired runtime JSON (card classes slice 1).
// Hand-edited from here on; a card moves out when it is implemented.

object LandWardenCard extends Denizen(DenizenId("130"), "Land Warden", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.land-garden"),
    persistent = false, cost = Cost(favor = 1),
    text = "You may play two cards you draw _(instead of one)_ if you " +
      "play at least one card to a site.")
  val powers: Vector[PrintedPower] = Vector(power)

object HeartsAndMindsCard extends Denizen(DenizenId("138"), "Hearts and Minds", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.hearts-and-minds"),
    persistent = false, cost = Cost(favor = 3),
    text = "As defender, you're victorious now. At end, discard Hearts " +
      "and Minds unless you hold the People's Favor.")
  val powers: Vector[PrintedPower] = Vector(power)

object DeedWriterCard extends Denizen(DenizenId("146"), "Deed Writer", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.deed-writer"),
    persistent = false, cost = Cost.free,
    text = "**WHEN NEGOTIATING:** You may give, receive, and swap " +
      "sites. Old ruler moves warbands from site to their board, " +
      "and new ruler moves warbands to site from their board.")
  val powers: Vector[PrintedPower] = Vector(power)

object SpinningBeeCard extends Denizen(DenizenId("232"), "Spinning Bee", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.spinning-bee"),
    persistent = false, cost = Cost.free,
    text = "Declare a suit. Draw from your discard pile until you draw " +
      "a matching card. If you draw none, discard them all. You " +
      "**cannot** use other Search modifiers.")
  val powers: Vector[PrintedPower] = Vector(power)

object FavoredSonCard extends Denizen(DenizenId("235"), "Favored Son", Suit.Hearth) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.favored-son"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** take the People's Favor. If you do, adjust " +
      "its [favor] using its left ribbon.")
  val powers: Vector[PrintedPower] = Vector(power)
