package oathdigital.catalog.holding

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
import oathdigital.model.{Cost, DenizenId, PowerId, Suit}

// Generated from the retired runtime JSON (card classes slice 1).
// Hand-edited from here on; a card moves out when it is implemented.

object NaysayersCard extends Denizen(DenizenId("21"), "Naysayers", Suit.Discord) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.naysayers"),
    persistent = false, cost = Cost.free,
    text = "**REST:** If any Exile is the Oathkeeper or Usurper, take " +
      "[favor] from the Chancellor.")
  val powers: Vector[PrintedPower] = Vector(power)

object ReliquaryRaidCard extends Denizen(DenizenId("220"), "Reliquary Raid", Suit.Discord) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.reliquary-raid"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** take a random relic from the Reliquary.")
  val powers: Vector[PrintedPower] = Vector(power)

object TrackerCard extends Denizen(DenizenId("221"), "Tracker", Suit.Discord) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.tracker"),
    persistent = false, cost = Cost.free,
    text = "**ACTION:** Choose an enemy pawn at your site and end your " +
      "Act Phase. **WAKE:** If you chose a pawn last turn, you may " +
      "place your pawn at their site.")
  val powers: Vector[PrintedPower] = Vector(power)

object PledgeToDiscordCard extends Denizen(DenizenId("223"), "Pledge to Discord", Suit.Discord) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.pledge-to-discord"),
    persistent = true, cost = Cost.free,
    text = "Your advisers all have the [suit-discord] suit instead of " +
      "their printed suit. _Marriage counts as two [suit-discord]._")
  val powers: Vector[PrintedPower] = Vector(power)

object FriendlyFamiliarCard extends Denizen(DenizenId("224"), "Friendly Familiar", Suit.Discord) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.friendly-familiar"),
    persistent = false, cost = Cost(secret = 1),
    text = "Act as if your pawn is at any site. _You may use Trade " +
      "modifiers at the site._")
  val powers: Vector[PrintedPower] = Vector(power)

object FalseProphetCard extends Denizen(DenizenId("85"), "False Prophet", Suit.Discord) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.false-prophet"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** if you're an Exile, gain one warband and " +
      "put it on any revealed Vision. You now also have it " +
      "revealed. If it is ever discarded, kill the warband and " +
      "play or discard the Vision as if you had searched.")
  val powers: Vector[PrintedPower] = Vector(power)

object VowOfDivisionCard extends Denizen(DenizenId("86"), "Vow of Division", Suit.Discord) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-division"),
    persistent = true, cost = Cost.free,
    text = "You **cannot** take the Challenge action. Whenever an enemy " +
      "burns [favor-burnt], you take that favor unless it was " +
      "burned by a raid targeting you.")
  val powers: Vector[PrintedPower] = Vector(power)

object ZealotsCard extends Denizen(DenizenId("87"), "Zealots", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.zealots"),
    persistent = false, cost = Cost(favor = 1),
    text = "If the defending force is larger than your force, each " +
      "warband you sacrifice will add three _(not one)_ to your " +
      "attack.")
  val powers: Vector[PrintedPower] = Vector(power)

object RoyalAmbitionsCard extends Denizen(DenizenId("88"), "Royal Ambitions", Suit.Discord) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.royal-ambitions"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** if you're an Exile and rule the most " +
      "sites, you may take a random relic from the Reliquary and " +
      "become a Citizen. _Follow the steps in the rules and end " +
      "your Act Phase._")
  val powers: Vector[PrintedPower] = Vector(power)

object SneakAttackCard extends Denizen(DenizenId("98"), "Sneak Attack", Suit.Discord) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.sneak-attack"),
    persistent = true, cost = Cost.free,
    text = "After another player's campaign, you may campaign, spending " +
      "no Supply, if you declare them as the defender.")
  val powers: Vector[PrintedPower] = Vector(power)
