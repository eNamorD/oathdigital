package oathdigital.catalog.holding

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower,
  SiteOnly}
import oathdigital.model.{Cost, DenizenId, PowerId, Suit}

// Generated from the retired runtime JSON (card classes slice 1).
// Hand-edited from here on; a card moves out when it is implemented.

object VowOfKinshipCard extends Denizen(DenizenId("152"), "Vow of Kinship", Suit.Nomad) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-kinship"),
    persistent = false, cost = Cost.free,
    text = "Move all of your [favor] to the [suit-nomad] bank. Any " +
      "[favor] you gain or take is put in the [suit-nomad] bank. " +
      "You can use [favor] in the [suit-nomad] bank as if it is on " +
      "your board.")
  val powers: Vector[PrintedPower] = Vector(power)

object PilgrimageCard extends Denizen(DenizenId("161"), "Pilgrimage", Suit.Nomad) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.pilgrimage"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** move all denizens at your site to the " +
      "Dispossessed. Shuffle and draw denizens from the " +
      "Dispossessed equal to the number you moved. Peek at them " +
      "and put them on your region's discard pile.")
  val powers: Vector[PrintedPower] = Vector(power)

object AncientBloodlineCard extends Denizen(DenizenId("165"), "Ancient Bloodline", Suit.Nomad) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.ancient-bloodline"),
    persistent = true, cost = Cost.free,
    text = "Your enemies act as if denizens and relics at sites you " +
      "rule are **locked**.")
  val powers: Vector[PrintedPower] = Vector(power)

object AncientPactCard extends Denizen(DenizenId("166"), "Ancient Pact", Suit.Nomad) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.ancient-pact"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** if you're an Exile and hold the Darkest " +
      "Secret, you may take a random relic from the Reliquary and " +
      "become a Citizen. _Follow the steps in the rules and end " +
      "your Act Phase._")
  val powers: Vector[PrintedPower] = Vector(power)

object SearchPartyCard extends Denizen(DenizenId("240"), "Search Party", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.search-party"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** peek at the bottom 3 cards of a discard " +
      "pile and play one as if you searched. _Return the rest._")
  val powers: Vector[PrintedPower] = Vector(power)

object TravelingNegotiatorCard extends Denizen(DenizenId("242"), "Traveling Negotiator", Suit.Nomad) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.traveling-negotiator"),
    persistent = false, cost = Cost.free,
    text = "**WHEN NEGOTIATING:** You may negotiate with any player.")
  val powers: Vector[PrintedPower] = Vector(power)

object TheGatheringCard extends Denizen(DenizenId("27"), "The Gathering", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.the-gathering"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** any players in turn order may put their " +
      "pawn on this site. Then, players with pawns here may " +
      "negotiate binding exchanges of [favor], [secret], relics, " +
      "and advisers.")
  val powers: Vector[PrintedPower] = Vector(power)
