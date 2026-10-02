package oathdigital.catalog.holding

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower,
  SiteOnly}
import oathdigital.model.{Cost, DenizenId, PowerId, Suit}

// Generated from the retired runtime JSON (card classes slice 1).
// Hand-edited from here on; a card moves out when it is implemented.

object HomesteadersCard extends Denizen(DenizenId("127"), "Homesteaders", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.homesteaders"),
    persistent = false, cost = Cost.free,
    text = "**ACTION:** Move one of your faceup advisers to your site.")
  val powers: Vector[PrintedPower] = Vector(power)

object ARoundOfAleCard extends Denizen(DenizenId("129"), "A Round of Ale", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.a-round-of-ale"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** return all [favor] and [secret], and flip " +
      "your [secret], as in the Rest Phase.")
  val powers: Vector[PrintedPower] = Vector(power)

object LandWardenCard extends Denizen(DenizenId("130"), "Land Warden", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.land-garden"),
    persistent = false, cost = Cost(favor = 1),
    text = "You may play two cards you draw _(instead of one)_ if you " +
      "play at least one card to a site.")
  val powers: Vector[PrintedPower] = Vector(power)

object NewsFromAfarCard extends Denizen(DenizenId("134"), "News from Afar", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.news-from-alfar"),
    persistent = false, cost = Cost(favor = 2),
    text = "Spend no Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

object HeartsAndMindsCard extends Denizen(DenizenId("138"), "Hearts and Minds", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.hearts-and-minds"),
    persistent = false, cost = Cost(favor = 3),
    text = "As defender, you're victorious now. At end, discard Hearts " +
      "and Minds unless you hold the People's Favor.")
  val powers: Vector[PrintedPower] = Vector(power)

object HeraldCard extends Denizen(DenizenId("143"), "Herald", Suit.Hearth) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.herald"),
    persistent = true, cost = Cost.free,
    text = "After another player campaigns against a player _(not " +
      "bandits)_, you gain [favor] from any favor bank.")
  val powers: Vector[PrintedPower] = Vector(power)

object DeedWriterCard extends Denizen(DenizenId("146"), "Deed Writer", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.deed-writer"),
    persistent = false, cost = Cost.free,
    text = "**WHEN NEGOTIATING:** You may give, receive, and swap " +
      "sites. Old ruler moves warbands from site to their board, " +
      "and new ruler moves warbands to site from their board.")
  val powers: Vector[PrintedPower] = Vector(power)

object MarriageCard extends Denizen(DenizenId("148"), "Marriage", Suit.Hearth) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.marriage"),
    persistent = true, cost = Cost.free,
    text = "Marriage counts as two [suit-hearth] advisers, but only " +
      "counts as one toward your adviser limit.")
  val powers: Vector[PrintedPower] = Vector(power)

object AwaitedReturnCard extends Denizen(DenizenId("150"), "Awaited Return", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.awaited-return"),
    persistent = false, cost = Cost.free,
    text = "Spend no Supply if you sacrifice one warband from your " +
      "board.")
  val powers: Vector[PrintedPower] = Vector(power)

object OldSongsCard extends Denizen(DenizenId("229"), "Old Songs", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.old-songs"),
    persistent = false, cost = Cost.free,
    text = "You may trade with cards that have exactly one [secret] on " +
      "them.")
  val powers: Vector[PrintedPower] = Vector(power)

object DiplomatCard extends Denizen(DenizenId("230"), "Diplomat", Suit.Hearth) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.diplomat"),
    persistent = true, cost = Cost.free,
    text = "Players **cannot** campaign against you unless they rule a " +
      "[suit-hearth] card.")
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

object SkilledMerchantsCard extends Denizen(DenizenId("238"), "Skilled Merchants", Suit.Hearth) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.skilled-merchants"),
    persistent = false, cost = Cost.free,
    text = "You may trade with any denizens in your region.")
  val powers: Vector[PrintedPower] = Vector(power)

object TravelingDoctorCard extends Denizen(DenizenId("51"), "Traveling Doctor", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.traveling-doctor"),
    persistent = false, cost = Cost.free,
    text = "If you're defeated, kill no warbands in your force and " +
      "discard Traveling Doctor. **Ignore** powers that kill all " +
      "of your force.")
  val powers: Vector[PrintedPower] = Vector(power)
