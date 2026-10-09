package oathdigital.catalog.holding

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower,
  SiteOnly}
import oathdigital.model.{Cost, DenizenId, PowerId, Suit}

// Generated from the retired runtime JSON (card classes slice 1).
// Hand-edited from here on; a card moves out when it is implemented.

object MartialCultureCard extends Denizen(DenizenId("10"), "Martial Culture", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.martial-culture"),
    persistent = false, cost = Cost.free,
    text = "If you're an Exile and defeat another Exile, you may become " +
      "a Citizen. _Follow the steps in the rules and end your Act " +
      "Phase._")
  val powers: Vector[PrintedPower] = Vector(power)

object CodeOfHonorCard extends Denizen(DenizenId("103"), "Code of Honor", Suit.Order):
  val power = PrintedPower(PowerId("denizen.code-of-honor"),
    persistent = false, cost = Cost.free,
    text = "±2 [attack-die] but you **cannot** use other battle plans.")
  val powers: Vector[PrintedPower] = Vector(power)

object PalanquinCard extends Denizen(DenizenId("107"), "Palanquin", Suit.Order) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.palanquin"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Choose a player whose pawn is at your site. Put " +
      "your pawn on a site that they can travel to. Make them " +
      "travel to that site, spending no Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

object SpecialistCard extends Denizen(DenizenId("114"), "Specialist", Suit.Order):
  val power = PrintedPower(PowerId("denizen.specialist"),
    persistent = false, cost = Cost(favor = 2),
    text = "The defender **cannot** use battle plans.")
  val powers: Vector[PrintedPower] = Vector(power)

object CaptainsCard extends Denizen(DenizenId("115"), "Captains", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.captains"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Campaign at any site you rule. Act as if your " +
      "pawn is there. Spend no Supply and add your warbands there " +
      "to your force.")
  val powers: Vector[PrintedPower] = Vector(power)

object CouncilSeatCard extends Denizen(DenizenId("123"), "Council Seat", Suit.Order) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.council-seat"),
    persistent = true, cost = Cost.free,
    text = "If you're a Citizen, you **cannot** be exiled, even by " +
      "yourself.")
  val powers: Vector[PrintedPower] = Vector(power)

object PeaceEnvoyCard extends Denizen(DenizenId("125"), "Peace Envoy", Suit.Order):
  val power = PrintedPower(PowerId("denizen.peace-envoy"),
    persistent = false, cost = Cost(favor = 1),
    text = "You **cannot** use other battle plans. If your enemy's pawn " +
      "is at your site, give them one [favor] per [defense-die] in " +
      "the pool. You're victorious now. **Ignore** killing " +
      "warbands.")
  val powers: Vector[PrintedPower] = Vector(power)

object RelicHunterCard extends Denizen(DenizenId("126"), "Relic Hunter", Suit.Order):
  val power = PrintedPower(PowerId("denizen.relic-hunter"),
    persistent = false, cost = Cost.free,
    text = "You may target facedown relics at targeted sites, adding 1 " +
      "[defense-die] per relic. You may put any relics you take on " +
      "the bottom of the relic deck.")
  val powers: Vector[PrintedPower] = Vector(power)

object MasterAtArmsCard extends Denizen(DenizenId("249"), "Master at Arms", Suit.Order):
  val power = PrintedPower(PowerId("denizen.master-at-arms"),
    persistent = false, cost = Cost.free,
    text = "You may reroll any number of dice that show hollow swords " +
      "[hollow-sword] once.")
  val powers: Vector[PrintedPower] = Vector(power)

object BaronCard extends Denizen(DenizenId("250"), "Baron", Suit.Order):
  val power = PrintedPower(PowerId("denizen.baron"),
    persistent = false, cost = Cost.free,
    text = "You may play to any site you rule, and you may discard a " +
      "card from that site first.")
  val powers: Vector[PrintedPower] = Vector(power)

object CityWallCard extends Denizen(DenizenId("252"), "City Wall", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.city-wall"),
    persistent = true, cost = Cost.free,
    text = "While at this site, enemies of City Wall's ruler **cannot** " +
      "place [favor] / [secret] on cards or use non-persistent " +
      "powers.")
  val powers: Vector[PrintedPower] = Vector(power)

object CarefulPlansCard extends Denizen(DenizenId("254"), "Careful Plans", Suit.Order):
  val power = PrintedPower(PowerId("denizen.careful-plans"),
    persistent = false, cost = Cost(favor = 1),
    text = "The defender **must** roll their [defense-die] before the " +
      "attacker rolls their [attack-die].")
  val powers: Vector[PrintedPower] = Vector(power)

object BearTrapsCard extends Denizen(DenizenId("3"), "Bear Traps", Suit.Order):
  val power = PrintedPower(PowerId("denizen.bear-traps"),
    persistent = false, cost = Cost.free,
    text = "- [attack-die] Kill one warband on the attacker's board.")
  val powers: Vector[PrintedPower] = Vector(power)

object KeepCard extends Denizen(DenizenId("5"), "Keep", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.keep"),
    persistent = false, cost = Cost.free,
    text = "If defeated in a raid, after the attacker takes what they " +
      "targeted, place your pawn at this site and end the action. " +
      "_Do not burn favor, move pawn, etc._")
  val powers: Vector[PrintedPower] = Vector(power)
