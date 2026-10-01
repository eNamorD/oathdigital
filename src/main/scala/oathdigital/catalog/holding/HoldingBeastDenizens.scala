package oathdigital.catalog.holding

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower, SiteOnly}
import oathdigital.model.{Cost, DenizenId, PowerId, Suit}

// Generated from the retired runtime JSON (card classes slice 1).
// Hand-edited from here on; a card moves out when it is implemented.

object ErrandBoyCard extends Denizen(DenizenId("11"), "Errand Boy", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.errand-boy"),
    persistent = false, cost = Cost(favor = 1),
    text = "You may draw from a discard pile in a different region " +
      "instead of yours.")
  val powers: Vector[PrintedPower] = Vector(power)

object NatureWorshipCard extends Denizen(DenizenId("175"), "Nature Worship", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.nature-worship"),
    persistent = false, cost = Cost(secret = 1),
    text = "± [attack-die] per [suit-beast] adviser you have.")
  val powers: Vector[PrintedPower] = Vector(power)

object BirdsongCard extends Denizen(DenizenId("176"), "Birdsong", Suit.Beast) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.birdsong"),
    persistent = false, cost = Cost.free,
    text = "Spend no Supply if you're trading with a [suit-beast] or " +
      "[suit-nomad] card.")
  val powers: Vector[PrintedPower] = Vector(power)

object SmallFriendsCard extends Denizen(DenizenId("177"), "Small Friends", Suit.Beast) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.small-friends"),
    persistent = false, cost = Cost.free,
    text = "Act as if your pawn is at any site with a [suit-beast] " +
      "card. _(You may use Trade modifiers there.)_")
  val powers: Vector[PrintedPower] = Vector(power)

object GraspingVinesCard extends Denizen(DenizenId("178"), "Grasping Vines", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.grasping-vines"),
    persistent = true, cost = Cost.free,
    text = "Enemies traveling from any site ruled by Grasping Vines' " +
      "ruler **must** kill one warband on their board if able.")
  val powers: Vector[PrintedPower] = Vector(power)

object ThreateningRoarCard extends Denizen(DenizenId("179"), "Threatening Roar", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.threatening-roar"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** discard all [suit-nomad] and [suit-beast] " +
      "cards at sites in your region.")
  val powers: Vector[PrintedPower] = Vector(power)

object FaeMerchantCard extends Denizen(DenizenId("180"), "Fae Merchant", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.fae-merchant"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Draw a relic and take it. Put any relic you " +
      "hold except the Grand Scepter on the bottom of the relic " +
      "deck.")
  val powers: Vector[PrintedPower] = Vector(power)

object SecondChanceCard extends Denizen(DenizenId("181"), "Second Chance", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.second-chance"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Kill one warband on the board of a player who " +
      "has an [suit-order] or [suit-discord] adviser to gain one " +
      "warband.")
  val powers: Vector[PrintedPower] = Vector(power)

object PiedPiperCard extends Denizen(DenizenId("182"), "Pied Piper", Suit.Beast) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.pied-piper"),
    persistent = false, cost = Cost(secret = 1),
    text = "This card ignores the adviser limit. **ACTION:** Move this " +
      "card to any other player's advisers. Take [favor] [favor] " +
      "from them.")
  val powers: Vector[PrintedPower] = Vector(power)

object MushroomsCard extends Denizen(DenizenId("183"), "Mushrooms", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.mushrooms"),
    persistent = false, cost = Cost(secret = 1),
    text = "Spend no Supply, but draw only one card _(not three)_ from " +
      "the bottom of your region's discard pile.")
  val powers: Vector[PrintedPower] = Vector(power)

object InsectSwarmCard extends Denizen(DenizenId("184"), "Insect Swarm", Suit.Beast) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.insect-swarm"),
    persistent = true, cost = Cost.free,
    text = "Your enemy's battle plans each have an added cost of " +
      "[favor-burnt].")
  val powers: Vector[PrintedPower] = Vector(power)

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

object GiantPythonCard extends Denizen(DenizenId("186"), "Giant Python", Suit.Beast) with SiteOnly:
  val action = PrintedPower(PowerId("denizen.giant-python.action"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** If you hold the Darkest Secret, move Giant " +
      "Python to another site, and you may discard a card there " +
      "first.")
  val restriction = PrintedPower(PowerId("denizen.giant-python.restriction"),
    persistent = true, cost = Cost.free,
    text = "Other cards at this site **cannot** be used for " +
      "non-persistent powers or placing [favor] / [secret].")
  val powers: Vector[PrintedPower] = Vector(action, restriction)

object WarTortoiseCard extends Denizen(DenizenId("187"), "War Tortoise", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.war-tortoise"),
    persistent = false, cost = Cost(favor = 1),
    text = "**Ignore** all attack or defense added by your enemy's " +
      "rolls of [sword] [sword] [skull] or [shield] [shield]. " +
      "_(Any [skull] they roll still kill their warbands.)_")
  val powers: Vector[PrintedPower] = Vector(power)

object NewGrowthCard extends Denizen(DenizenId("188"), "New Growth", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.new-growth"),
    persistent = false, cost = Cost.free,
    text = "You may play [suit-beast] and [suit-hearth] cards to any " +
      "site _(that has space)_.")
  val powers: Vector[PrintedPower] = Vector(power)

object WildCryCard extends Denizen(DenizenId("189"), "Wild Cry", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.wild-cry"),
    persistent = false, cost = Cost.free,
    text = "If you play a [suit-beast] card, gain 1 Supply and 2 " +
      "warbands.")
  val powers: Vector[PrintedPower] = Vector(power)

object AnimalHostCard extends Denizen(DenizenId("190"), "Animal Host", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.animal-host"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** gain warbands equal to the total number of " +
      "[suit-beast] cards _(including Animal Host)_ at any sites " +
      "_(regardless of rule)_.")
  val powers: Vector[PrintedPower] = Vector(power)

object MemoryOfNatureCard extends Denizen(DenizenId("191"), "Memory of Nature", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.memory-of-nature"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Move a total of X [favor] from any favor banks " +
      "to the [suit-beast] bank. X is the number of [suit-beast] " +
      "cards on the map.")
  val powers: Vector[PrintedPower] = Vector(power)

object MarshSpiritCard extends Denizen(DenizenId("192"), "Marsh Spirit", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.marsh-spirit"),
    persistent = true, cost = Cost.free,
    text = "Players who target this site **cannot** use battle plans.")
  val powers: Vector[PrintedPower] = Vector(power)

object VowOfPovertyCard extends Denizen(DenizenId("193"), "Vow of Poverty", Suit.Beast) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-poverty"),
    persistent = false, cost = Cost.free,
    text = "You **cannot** gain [favor] from Trade. **REST:** If you " +
      "have no [favor], take [favor] [favor] from any one favor " +
      "bank.")
  val powers: Vector[PrintedPower] = Vector(power)

object ForestCouncilCard extends Denizen(DenizenId("194"), "Forest Council", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.forest-council"),
    persistent = true, cost = Cost.free,
    text = "Enemies of Forest Council's ruler **cannot** place [favor] " +
      "/ [secret] on [suit-beast] cards or use their " +
      "non-persistent powers.")
  val powers: Vector[PrintedPower] = Vector(power)

object WalledGardenCard extends Denizen(DenizenId("195"), "Walled Garden", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.walled-garden"),
    persistent = false, cost = Cost.free,
    text = "+[defense-die] per [suit-beast] at any sites if this site " +
      "is targeted.")
  val powers: Vector[PrintedPower] = Vector(power)

object VowOfBeastkinCard extends Denizen(DenizenId("196"), "Vow of Beastkin", Suit.Beast) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-beastkin"),
    persistent = false, cost = Cost.free,
    text = "You **must** muster on a card matching any of your " +
      "advisers, but you gain one more warband.")
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

object TrueOathCard extends Denizen(DenizenId("209"), "True Oath", Suit.Beast) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.true-oath"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** if you rule at least three other " +
      "[suit-beast] cards, you may change the Oathkeeper goal.")
  val powers: Vector[PrintedPower] = Vector(power)

object BogCard extends Denizen(DenizenId("210"), "Bog", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.bog"),
    persistent = false, cost = Cost.free,
    text = "**ACTION:** Discard a relic you hold to gain [favor] " +
      "[favor] [favor] from the [suit-beast] bank.")
  val powers: Vector[PrintedPower] = Vector(power)

object WhisperingLeavesCard extends Denizen(DenizenId("211"), "Whispering Leaves", Suit.Beast) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.whispering-leaves"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Choose a player whose pawn is at your site. " +
      "They **must** place [favor] [favor] on this card.")
  val powers: Vector[PrintedPower] = Vector(power)

object BedOfRootsCard extends Denizen(DenizenId("212"), "Bed of Roots", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.bed-of-roots"),
    persistent = false, cost = Cost(favorBurnt = 3),
    text = "**ACTION:** Bury a faceup adviser you have _(even if " +
      "locked)_ to gain [secret] [secret].")
  val powers: Vector[PrintedPower] = Vector(power)

object AutumnWindCard extends Denizen(DenizenId("213"), "Autumn Wind", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.autumn-wind"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** shuffle together all discard piles, then " +
      "deal these cards to the discard piles as evenly as " +
      "possible. You decide ties.")
  val powers: Vector[PrintedPower] = Vector(power)

object ShiftingFogCard extends Denizen(DenizenId("214"), "Shifting Fog", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.shifting-fog"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** move all favor in each favor bank to the " +
      "next bank to the right. [suit-nomad] moves to " +
      "[suit-discord].")
  val powers: Vector[PrintedPower] = Vector(power)

object FaeBattalionCard extends Denizen(DenizenId("215"), "Fae Battalion", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.fae-battalion"),
    persistent = false, cost = Cost.free,
    text = "If you have the Darkest Secret, you may count each " +
      "[hollow-sword] as a [sword] instead.")
  val powers: Vector[PrintedPower] = Vector(power)

object HungerCard extends Denizen(DenizenId("216"), "Hunger", Suit.Beast) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.hunger"),
    persistent = false, cost = Cost.free,
    text = "**WAKE:** You **must** bury an adviser held by a player " +
      "whose pawn is at your site _(even yourself)_, but cannot " +
      "bury this card.")
  val powers: Vector[PrintedPower] = Vector(power)

object SignalTreesCard extends Denizen(DenizenId("217"), "Signal Trees", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.signal-trees"),
    persistent = false, cost = Cost(secret = 1),
    text = "Reveal the top three cards of your region's discard pile. " +
      "±2 [attack-die] per revealed [suit-beast]. Discard all of " +
      "the revealed cards.")
  val powers: Vector[PrintedPower] = Vector(power)

object ForestWardenCard extends Denizen(DenizenId("218"), "Forest Warden", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.forest-warden"),
    persistent = false, cost = Cost.free,
    text = "If you play a [suit-beast] card to a site, you may bury a " +
      "card at the site first.")
  val powers: Vector[PrintedPower] = Vector(power)

object WolvesCard extends Denizen(DenizenId("39"), "Wolves", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.wolves"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Kill one warband _(even yours)_ on any one " +
      "board.")
  val powers: Vector[PrintedPower] = Vector(power)

object AnimalPlaymatesCard extends Denizen(DenizenId("40"), "Animal Playmates", Suit.Beast) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.animal-playmates"),
    persistent = false, cost = Cost.free,
    text = "Spend no Supply if you're mustering on a [suit-beast] card.")
  val powers: Vector[PrintedPower] = Vector(power)

object TrueNamesCard extends Denizen(DenizenId("41"), "True Names", Suit.Beast) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.true-names"),
    persistent = true, cost = Cost.free,
    text = "Your enemy **cannot** use battle plans that match any of " +
      "your advisers against you.")
  val powers: Vector[PrintedPower] = Vector(power)

object TheOldOakCard extends Denizen(DenizenId("42"), "The Old Oak", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.the-old-oak"),
    persistent = false, cost = Cost.free,
    text = "If trading with The Old Oak for [secret], gain one more " +
      "[secret] if you have any [suit-beast] advisers.")
  val powers: Vector[PrintedPower] = Vector(power)

object ForestPathsCard extends Denizen(DenizenId("43"), "Forest Paths", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.forest-paths"),
    persistent = false, cost = Cost(favor = 1),
    text = "Spend no Supply and ignore the powers of sites if you're " +
      "traveling to a site with a [suit-beast] card.")
  val powers: Vector[PrintedPower] = Vector(power)

object LongLostHeirCard extends Denizen(DenizenId("44"), "Long-Lost Heir", Suit.Beast) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.long-lost-heir"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** if you're an Exile, you may become a " +
      "Citizen. _Follow the steps in the rules and end your Act " +
      "Phase._")
  val powers: Vector[PrintedPower] = Vector(power)

object RangersCard extends Denizen(DenizenId("45"), "Rangers", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.rangers"),
    persistent = false, cost = Cost(favor = 1),
    text = "Ignore all skulls [skull] you roll. +2 [attack-die] if the " +
      "defense pool has 4+ [defense-die].")
  val powers: Vector[PrintedPower] = Vector(power)

object RovingTerrorCard extends Denizen(DenizenId("46"), "Roving Terror", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.roving-terror"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Discard a denizen card at any other site and " +
      "move this card there.")
  val powers: Vector[PrintedPower] = Vector(power)
