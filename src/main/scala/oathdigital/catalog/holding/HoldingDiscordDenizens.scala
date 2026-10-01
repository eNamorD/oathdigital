package oathdigital.catalog.holding

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower, SiteOnly}
import oathdigital.model.{Cost, DenizenId, PowerId, Suit}

// Generated from the retired runtime JSON (card classes slice 1).
// Hand-edited from here on; a card moves out when it is implemented.

object BanditChiefCard extends Denizen(DenizenId("100"), "Bandit Chief", Suit.Discord) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.bandit-chief"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** kill one warband at each site not ruled by " +
      "the bandits.")
  val powers: Vector[PrintedPower] = Vector(power)

object ChaosCultCard extends Denizen(DenizenId("101"), "Chaos Cult", Suit.Discord) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.chaos-cult"),
    persistent = true, cost = Cost.free,
    text = "After another player takes the Oathkeeper title, you take " +
      "[favor] from them.")
  val powers: Vector[PrintedPower] = Vector(power)

object DefameCard extends Denizen(DenizenId("102"), "Defame", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.defame"),
    persistent = false, cost = Cost.free,
    text = "After trading with a [suit-discord] card, burn " +
      "[favor-burnt] from any player board.")
  val powers: Vector[PrintedPower] = Vector(power)

object MercenariesCard extends Denizen(DenizenId("12"), "Mercenaries", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.mercenaries"),
    persistent = false, cost = Cost(favor = 1),
    text = "±3 [attack-die] If you're defeated while using this power, " +
      "discard Mercenaries.")
  val powers: Vector[PrintedPower] = Vector(power)

object ASmallFavorCard extends Denizen(DenizenId("15"), "A Small Favor", Suit.Discord) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.a-small-favor"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** Gain four warbands.")
  val powers: Vector[PrintedPower] = Vector(power)

object SecondWindCard extends Denizen(DenizenId("16"), "Second Wind", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.second-wind"),
    persistent = false, cost = Cost(secret = 1, favorBurnt = 1),
    text = "After you're victorious, you may travel and then may " +
      "campaign, spending no Supply for either.")
  val powers: Vector[PrintedPower] = Vector(power)

object SleightOfHandCard extends Denizen(DenizenId("17"), "Sleight of Hand", Suit.Discord) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.sleight-of-hand"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Take [secret] from a player whose pawn is at " +
      "your site. You cannot take their last [secret].")
  val powers: Vector[PrintedPower] = Vector(power)

object KeyToTheCityCard extends Denizen(DenizenId("18"), "Key to the City", Suit.Discord) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.key-to-the-city"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** if the ruler's pawn is not at this site, " +
      "kill any warbands at this site, then gain a warband and " +
      "place it here.")
  val powers: Vector[PrintedPower] = Vector(power)

object ScryerCard extends Denizen(DenizenId("19"), "Scryer", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.scryer"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Peek at any one discard pile.")
  val powers: Vector[PrintedPower] = Vector(power)

object DisgracedCaptainCard extends Denizen(DenizenId("20"), "Disgraced Captain", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.disgraced-captain"),
    persistent = false, cost = Cost(favor = 1, favorBurnt = 1),
    text = "±4 [attack-die] if the defender rules an [suit-order] card " +
      "_(even an adviser)_.")
  val powers: Vector[PrintedPower] = Vector(power)

object NaysayersCard extends Denizen(DenizenId("21"), "Naysayers", Suit.Discord) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.naysayers"),
    persistent = false, cost = Cost.free,
    text = "**REST:** If any Exile is the Oathkeeper or Usurper, take " +
      "[favor] from the Chancellor.")
  val powers: Vector[PrintedPower] = Vector(power)

object BanditPaymasterCard extends Denizen(DenizenId("219"), "Bandit Paymaster", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.bandit-paymaster"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Remove a bandit from your site except the last " +
      "to gain 3 warbands.")
  val powers: Vector[PrintedPower] = Vector(power)

object BookBurningCard extends Denizen(DenizenId("22"), "Book Burning", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.book-burning"),
    persistent = false, cost = Cost.free,
    text = "If you're victorious in a raid, burn all [secret] on the " +
      "defender's board except their last [secret].")
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

object BannerBreakersCard extends Denizen(DenizenId("222"), "Banner Breakers", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.banner-breakers"),
    persistent = false, cost = Cost(favor = 1, favorBurnt = 1),
    text = "+3 [attack-die] if the defender has the Darkest Secret or " +
      "People's Favor.")
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

object UnstableSummonCard extends Denizen(DenizenId("225"), "Unstable Summon", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.unstable-summon"),
    persistent = false, cost = Cost.free,
    text = "If you hold the Darkest Secret, burn X [secret] from it to " +
      "collect X [attack-die].")
  val powers: Vector[PrintedPower] = Vector(power)

object BanditPrinceCard extends Denizen(DenizenId("226"), "Bandit Prince", Suit.Discord) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.bandit-prince"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** you may replace all bandits at any sites " +
      "you choose with your warbands.")
  val powers: Vector[PrintedPower] = Vector(power)

object DarkEnforcerCard extends Denizen(DenizenId("227"), "Dark Enforcer", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.dark-enforcer"),
    persistent = false, cost = Cost(favorBurnt = 1),
    text = "**ACTION:** Discard all [suit-order] and [suit-hearth] " +
      "cards from your site.")
  val powers: Vector[PrintedPower] = Vector(power)

object SpoiledSuppliesCard extends Denizen(DenizenId("228"), "Spoiled Supplies", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.spoiled-supplies"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Enemies with a pawn at your site each lose 1 " +
      "Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

object CharlatanCard extends Denizen(DenizenId("79"), "Charlatan", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.charlatan"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** burn all [secret] but one from the Darkest " +
      "Secret.")
  val powers: Vector[PrintedPower] = Vector(power)

object AssassinCard extends Denizen(DenizenId("80"), "Assassin", Suit.Discord) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.assassin"),
    persistent = false, cost = Cost(favor = 1),
    text = "You can only have two advisers. **ACTION:** Discard a " +
      "faceup adviser of a player whose pawn is at your site.")
  val powers: Vector[PrintedPower] = Vector(power)

object DowntroddenCard extends Denizen(DenizenId("81"), "Downtrodden", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.downtrodden"),
    persistent = false, cost = Cost.free,
    text = "Gain two more warbands if mustering on a card whose favor " +
      "bank has the least [favor] _(not tied)_.")
  val powers: Vector[PrintedPower] = Vector(power)

object BlackmailCard extends Denizen(DenizenId("82"), "Blackmail", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.blackmail"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** choose a relic held by a player whose pawn " +
      "is at your site. You take the relic unless they give you " +
      "[favor] [favor] [favor].")
  val powers: Vector[PrintedPower] = Vector(power)

object CrackedSageCard extends Denizen(DenizenId("83"), "Cracked Sage", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.cracked-sage"),
    persistent = false, cost = Cost(secret = 1, favorBurnt = 1),
    text = "±4 [attack-die] if your enemy has an [suit-arcane] adviser.")
  val powers: Vector[PrintedPower] = Vector(power)

object DissentCard extends Denizen(DenizenId("84"), "Dissent", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.dissent"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** each player, except the holder of the " +
      "People's Favor, places one [favor] on this card for each " +
      "suit of card they rule.")
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

object SaltTheEarthCard extends Denizen(DenizenId("89"), "Salt the Earth", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.salt-the-earth"),
    persistent = false, cost = Cost.free,
    text = "If you're victorious in a conquest, bury all cards at one " +
      "site you targeted.")
  val powers: Vector[PrintedPower] = Vector(power)

object BeastTamerCard extends Denizen(DenizenId("90"), "Beast Tamer", Suit.Discord) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.beast-tamer"),
    persistent = true, cost = Cost.free,
    text = "Enemies cannot use [suit-beast] or [suit-nomad] battle " +
      "plans against you.")
  val powers: Vector[PrintedPower] = Vector(power)

object RiotsCard extends Denizen(DenizenId("91"), "Riots", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.riots"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** discard all denizens at sites in this " +
      "region. Burn the same number of [favor] from the People's " +
      "Favor.")
  val powers: Vector[PrintedPower] = Vector(power)

object SilverTongueCard extends Denizen(DenizenId("92"), "Silver Tongue", Suit.Discord) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.silver-tongue"),
    persistent = false, cost = Cost.free,
    text = "You can only have two advisers. **REST:** Take [favor] from " +
      "a favor bank matching a card at your site.")
  val powers: Vector[PrintedPower] = Vector(power)

object GamblingHallCard extends Denizen(DenizenId("93"), "Gambling Hall", Suit.Discord) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.gambling-hall"),
    persistent = false, cost = Cost(favor = 2),
    text = "**ACTION:** Roll 4 [defense-die] and take X [favor] equal " +
      "to the total [shield] result from any one favor bank.")
  val powers: Vector[PrintedPower] = Vector(power)

object RelicThiefCard extends Denizen(DenizenId("95"), "Relic Thief", Suit.Discord) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.relic-thief"),
    persistent = false, cost = Cost(favor = 1, secret = 1),
    text = "**ACTION:** Choose a faceup relic held by a player whose " +
      "pawn is at your site. Roll [defense-die] equal to its " +
      "defense. If you roll no [shield], take it.")
  val powers: Vector[PrintedPower] = Vector(power)

object EnchantressCard extends Denizen(DenizenId("96"), "Enchantress", Suit.Discord) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.enchantress"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Swap this card with any faceup adviser.")
  val powers: Vector[PrintedPower] = Vector(power)

object InsomniaCard extends Denizen(DenizenId("97"), "Insomnia", Suit.Discord) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.insomnia"),
    persistent = false, cost = Cost.free,
    text = "You can only have two advisers. **REST:** Gain [secret].")
  val powers: Vector[PrintedPower] = Vector(power)

object SneakAttackCard extends Denizen(DenizenId("98"), "Sneak Attack", Suit.Discord) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.sneak-attack"),
    persistent = true, cost = Cost.free,
    text = "After another player's campaign, you may campaign, spending " +
      "no Supply, if you declare them as the defender.")
  val powers: Vector[PrintedPower] = Vector(power)

object GossipCard extends Denizen(DenizenId("99"), "Gossip", Suit.Discord) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.gossip"),
    persistent = true, cost = Cost.free,
    text = "After an enemy plays an adviser facedown _(including a " +
      "Vision)_, gain [favor] from the [suit-discord] bank.")
  val powers: Vector[PrintedPower] = Vector(power)
