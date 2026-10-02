package oathdigital.catalog.holding

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower,
  SiteOnly}
import oathdigital.model.{Cost, DenizenId, PowerId, Suit}

// Generated from the retired runtime JSON (card classes slice 1).
// Hand-edited from here on; a card moves out when it is implemented.

object ConvoysCard extends Denizen(DenizenId("151"), "Convoys", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.convoys"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Take another region's discard pile and put it " +
      "on top of your region's discard pile _(even if empty)_.")
  val powers: Vector[PrintedPower] = Vector(power)

object VowOfKinshipCard extends Denizen(DenizenId("152"), "Vow of Kinship", Suit.Nomad) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-kinship"),
    persistent = false, cost = Cost.free,
    text = "Move all of your [favor] to the [suit-nomad] bank. Any " +
      "[favor] you gain or take is put in the [suit-nomad] bank. " +
      "You can use [favor] in the [suit-nomad] bank as if it is on " +
      "your board.")
  val powers: Vector[PrintedPower] = Vector(power)

object WildMountsCard extends Denizen(DenizenId("153"), "Wild Mounts", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.wild-mounts"),
    persistent = false, cost = Cost.free,
    text = "If you would discard any number of [suit-nomad] battle " +
      "plans, you may instead discard any one [suit-beast] card " +
      "you rule.")
  val powers: Vector[PrintedPower] = Vector(power)

object LancersCard extends Denizen(DenizenId("154"), "Lancers", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.lancers"),
    persistent = false, cost = Cost.free,
    text = "Double your total attack roll. At end, discard Lancers.")
  val powers: Vector[PrintedPower] = Vector(power)

object MountainGiantCard extends Denizen(DenizenId("155"), "Mountain Giant", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.mountain-giant"),
    persistent = false, cost = Cost(secret = 1),
    text = "±1 [attack-die] or ±3 At end, discard.")
  val powers: Vector[PrintedPower] = Vector(power)

object SpecialEnvoyCard extends Denizen(DenizenId("158"), "Special Envoy", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.special-envoy"),
    persistent = false, cost = Cost.free,
    text = "Spend no Supply. After traveling, end your Act Phase.")
  val powers: Vector[PrintedPower] = Vector(power)

object ResettleCard extends Denizen(DenizenId("159"), "Resettle", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.resettle"),
    persistent = false, cost = Cost.free,
    text = "**ACTION:** Move a faceup [suit-nomad] card from any " +
      "player's advisers to any site.")
  val powers: Vector[PrintedPower] = Vector(power)

object PilgrimageCard extends Denizen(DenizenId("161"), "Pilgrimage", Suit.Nomad) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.pilgrimage"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** move all denizens at your site to the " +
      "Dispossessed. Shuffle and draw denizens from the " +
      "Dispossessed equal to the number you moved. Peek at them " +
      "and put them on your region's discard pile.")
  val powers: Vector[PrintedPower] = Vector(power)

object SpellBreakerCard extends Denizen(DenizenId("162"), "Spell Breaker", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.spell-breaker"),
    persistent = true, cost = Cost.free,
    text = "Enemies of Spell Breaker's ruler **cannot** use powers that " +
      "cost any [secret] or [secret-burnt].")
  val powers: Vector[PrintedPower] = Vector(power)

object MountedPatrolCard extends Denizen(DenizenId("163"), "Mounted Patrol", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.mounted-patrol"),
    persistent = false, cost = Cost.free,
    text = "The attacker rolls only half the [attack-die] in their " +
      "attack pool, rounded down. At end, discard Mounted Patrol.")
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

object FamilyWagonCard extends Denizen(DenizenId("168"), "Family Wagon", Suit.Nomad) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.family-wagon"),
    persistent = true, cost = Cost.free,
    text = "You have no adviser limit but may only have [suit-nomad] " +
      "advisers-—bury other advisers you have or would gain.")
  val powers: Vector[PrintedPower] = Vector(power)

object WayStationCard extends Denizen(DenizenId("169"), "Way Station", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.way-station"),
    persistent = true, cost = Cost.free,
    text = "Spend no Supply if you're traveling to this site and either " +
      "rule Way Station or choose to give [favor] to its ruler. " +
      "_(Give it to Chancellor if Empire, burn it if bandits.)_")
  val powers: Vector[PrintedPower] = Vector(power)

object HospitalityCard extends Denizen(DenizenId("171"), "Hospitality", Suit.Nomad) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.hospitality"),
    persistent = false, cost = Cost.free,
    text = "After traveling, gain [favor] from one favor bank that " +
      "matches both a card at your site and one of your advisers.")
  val powers: Vector[PrintedPower] = Vector(power)

object AFastSteedCard extends Denizen(DenizenId("172"), "A Fast Steed", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.old-fast-steed"),
    persistent = false, cost = Cost(favor = 1),
    text = "Spend no Supply if you have three or fewer warbands on your " +
      "board.")
  val powers: Vector[PrintedPower] = Vector(power)

object AncientBindingCard extends Denizen(DenizenId("23"), "Ancient Binding", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.ancient-binding"),
    persistent = false, cost = Cost(secret = 1, secretBurnt = 1),
    text = "**ACTION:** Each player burns all [secret] from their board " +
      "and cards except their last [secret]. _(Keep only your " +
      "[secret] here.)_")
  val powers: Vector[PrintedPower] = Vector(power)

object SearchPartyCard extends Denizen(DenizenId("240"), "Search Party", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.search-party"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** peek at the bottom 3 cards of a discard " +
      "pile and play one as if you searched. _Return the rest._")
  val powers: Vector[PrintedPower] = Vector(power)

object MovingMarketCard extends Denizen(DenizenId("241"), "Moving Market", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.moving-market"),
    persistent = false, cost = Cost.free,
    text = "After trading with Moving Market, you may move this card to " +
      "any site with capacity. If you do, gain [favor] from the " +
      "[suit-nomad] bank.")
  val powers: Vector[PrintedPower] = Vector(power)

object TravelingNegotiatorCard extends Denizen(DenizenId("242"), "Traveling Negotiator", Suit.Nomad) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.traveling-negotiator"),
    persistent = false, cost = Cost.free,
    text = "**WHEN NEGOTIATING:** You may negotiate with any player.")
  val powers: Vector[PrintedPower] = Vector(power)

object TheRedSeerCard extends Denizen(DenizenId("244"), "The Red Seer", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.the-red-seer"),
    persistent = false, cost = Cost(secret = 1, secretBurnt = 2),
    text = "**ACTION:** If you hold the Darkest Secret, change the " +
      "Oathkeeper goal to anything but Devotion.")
  val powers: Vector[PrintedPower] = Vector(power)

object CallForHelpCard extends Denizen(DenizenId("246"), "Call for Help", Suit.Nomad) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.call-for-help"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** reveal and discard up to 6 cards from your " +
      "region's discard pile. Gain 2 warbands for each revealed " +
      "[suit-nomad] card.")
  val powers: Vector[PrintedPower] = Vector(power)

object VowOfWanderingCard extends Denizen(DenizenId("247"), "Vow of Wandering", Suit.Nomad) with Locked with AdviserOnly:
  val muster = PrintedPower(PowerId("denizen.vow-of-wandering.muster"),
    persistent = false, cost = Cost.free,
    text = "Gain two more warbands.")
  val power = PrintedPower(PowerId("denizen.vow-of-wandering"),
    persistent = true, cost = Cost.free,
    text = "You **cannot** place warbands on sites.")
  val powers: Vector[PrintedPower] = Vector(muster, power)

object MountedLibraryCard extends Denizen(DenizenId("248"), "Mounted Library", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.mounted-library"),
    persistent = false, cost = Cost.free,
    text = "If trading on Mounted Library for [secret], gain double the " +
      "[secret], then discard Mounted Library.")
  val powers: Vector[PrintedPower] = Vector(power)

object TheGatheringCard extends Denizen(DenizenId("27"), "The Gathering", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.the-gathering"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** any players in turn order may put their " +
      "pawn on this site. Then, players with pawns here may " +
      "negotiate binding exchanges of [favor], [secret], relics, " +
      "and advisers.")
  val powers: Vector[PrintedPower] = Vector(power)
