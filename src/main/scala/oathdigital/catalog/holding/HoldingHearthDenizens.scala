package oathdigital.catalog.holding

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower, SiteOnly}
import oathdigital.model.{Cost, DenizenId, PowerId, Suit}

// Generated from the retired runtime JSON (card classes slice 1).
// Hand-edited from here on; a card moves out when it is implemented.

object HomesteadersCard extends Denizen(DenizenId("127"), "Homesteaders", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.homesteaders"),
    persistent = false, cost = Cost.free,
    text = "**ACTION:** Move one of your faceup advisers to your site.")
  val powers: Vector[PrintedPower] = Vector(power)

object CropRotationCard extends Denizen(DenizenId("128"), "Crop Rotation", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.crop-rotation"),
    persistent = false, cost = Cost.free,
    text = "If playing to a site, you may discard a denizen there first.")
  val powers: Vector[PrintedPower] = Vector(power)

object ARoundOfAleCard extends Denizen(DenizenId("129"), "A Round of Ale", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.a-round-of-ale"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** return all [favor] and [secret], and flip " +
      "your [secret], as in the Rest Phase.")
  val powers: Vector[PrintedPower] = Vector(power)

object TinkersFairCard extends Denizen(DenizenId("13"), "Tinker's Fair", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.tinker-s-fair"),
    persistent = false, cost = Cost(favor = 3),
    text = "**ACTION:** Draw a relic from the relic deck.")
  val powers: Vector[PrintedPower] = Vector(power)

object LandWardenCard extends Denizen(DenizenId("130"), "Land Warden", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.land-garden"),
    persistent = false, cost = Cost(favor = 1),
    text = "You may play two cards you draw _(instead of one)_ if you " +
      "play at least one card to a site.")
  val powers: Vector[PrintedPower] = Vector(power)

object CharmingFriendCard extends Denizen(DenizenId("131"), "Charming Friend", Suit.Hearth) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.charming-friend"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Take [favor] from a player whose pawn is at " +
      "your site.")
  val powers: Vector[PrintedPower] = Vector(power)

object VillageConstableCard extends Denizen(DenizenId("132"), "Village Constable", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.village-constable"),
    persistent = false, cost = Cost.free,
    text = "±2 [attack-die] unless your enemy has the People's Favor.")
  val powers: Vector[PrintedPower] = Vector(power)

object FamilyHeirloomCard extends Denizen(DenizenId("133"), "Family Heirloom", Suit.Hearth) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.family-heirloom"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** draw a relic. Take it or put it on the " +
      "bottom of the relic deck.")
  val powers: Vector[PrintedPower] = Vector(power)

object NewsFromAfarCard extends Denizen(DenizenId("134"), "News from Afar", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.news-from-alfar"),
    persistent = false, cost = Cost(favor = 2),
    text = "Spend no Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

object LevelersCard extends Denizen(DenizenId("135"), "Levelers", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.levelers"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Move [favor] [favor] from the favor bank with " +
      "the most [favor] to that with the least [favor]. You decide " +
      "ties.")
  val powers: Vector[PrintedPower] = Vector(power)

object FabledFeastCard extends Denizen(DenizenId("136"), "Fabled Feast", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.fabled-feast"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** take X [favor] equal to the number of " +
      "[suit-hearth] cards you rule _(including Fabled Feast)_ " +
      "from any one favor bank.")
  val powers: Vector[PrintedPower] = Vector(power)

object TheGreatLevyCard extends Denizen(DenizenId("137"), "The Great Levy", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.the-great-levy"),
    persistent = false, cost = Cost(favor = 2),
    text = "±3 [attack-die] and **ignore** all skulls [skull] you roll, " +
      "unless your enemy has the People's Favor.")
  val powers: Vector[PrintedPower] = Vector(power)

object HeartsAndMindsCard extends Denizen(DenizenId("138"), "Hearts and Minds", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.hearts-and-minds"),
    persistent = false, cost = Cost(favor = 3),
    text = "As defender, you're victorious now. At end, discard Hearts " +
      "and Minds unless you hold the People's Favor.")
  val powers: Vector[PrintedPower] = Vector(power)

object RelicBreakerCard extends Denizen(DenizenId("139"), "Relic Breaker", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.relic-breaker"),
    persistent = false, cost = Cost.free,
    text = "**ACTION:** Bury a relic you hold to gain [secret].")
  val powers: Vector[PrintedPower] = Vector(power)

object BookBindersCard extends Denizen(DenizenId("140"), "Book Binders", Suit.Hearth) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.book-binders"),
    persistent = true, cost = Cost.free,
    text = "After another player plays a Vision faceup, you gain " +
      "[favor] [favor] from any one favor bank.")
  val powers: Vector[PrintedPower] = Vector(power)

object BallotBoxCard extends Denizen(DenizenId("141"), "Ballot Box", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.ballot-box"),
    persistent = false, cost = Cost(favor = 2),
    text = "**ACTION:** If you have an adviser matching a card at this " +
      "site, replace all warbands on this site with your warbands. " +
      "Remove any that can't be replaced.")
  val powers: Vector[PrintedPower] = Vector(power)

object SaddleMakersCard extends Denizen(DenizenId("142"), "Saddle Makers", Suit.Hearth) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.saddle-makers"),
    persistent = true, cost = Cost.free,
    text = "After another player plays a [suit-nomad] or [suit-order] " +
      "card, you gain [favor] [favor] from the matching favor bank.")
  val powers: Vector[PrintedPower] = Vector(power)

object HeraldCard extends Denizen(DenizenId("143"), "Herald", Suit.Hearth) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.herald"),
    persistent = true, cost = Cost.free,
    text = "After another player campaigns against a player _(not " +
      "bandits)_, you gain [favor] from any favor bank.")
  val powers: Vector[PrintedPower] = Vector(power)

object RowdyPubCard extends Denizen(DenizenId("144"), "Rowdy Pub", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.rowdy-pub"),
    persistent = false, cost = Cost.free,
    text = "Gain one more warband if mustering from Rowdy Pub.")
  val powers: Vector[PrintedPower] = Vector(power)

object VowOfPeaceCard extends Denizen(DenizenId("145"), "Vow of Peace", Suit.Hearth) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-peace"),
    persistent = true, cost = Cost.free,
    text = "You **cannot** campaign. Attackers **cannot** sacrifice " +
      "warbands to increase their attack against you.")
  val powers: Vector[PrintedPower] = Vector(power)

object DeedWriterCard extends Denizen(DenizenId("146"), "Deed Writer", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.deed-writer"),
    persistent = false, cost = Cost.free,
    text = "**WHEN NEGOTIATING:** You may give, receive, and swap " +
      "sites. Old ruler moves warbands from site to their board, " +
      "and new ruler moves warbands to site from their board.")
  val powers: Vector[PrintedPower] = Vector(power)

object SaladDaysCard extends Denizen(DenizenId("147"), "Salad Days", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.salad-days"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** gain [favor] [favor] [favor]- one each " +
      "from three different favor banks.")
  val powers: Vector[PrintedPower] = Vector(power)

object MarriageCard extends Denizen(DenizenId("148"), "Marriage", Suit.Hearth) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.marriage"),
    persistent = true, cost = Cost.free,
    text = "Marriage counts as two [suit-hearth] advisers, but only " +
      "counts as one toward your adviser limit.")
  val powers: Vector[PrintedPower] = Vector(power)

object HospitalCard extends Denizen(DenizenId("149"), "Hospital", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.hospital"),
    persistent = false, cost = Cost.free,
    text = "If any of your warbands would be killed, place them on " +
      "Hospital's site instead if you still rule it.")
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

object VillageIdiotCard extends Denizen(DenizenId("231"), "Village Idiot", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.village-idiot"),
    persistent = false, cost = Cost.free,
    text = "After mustering with this card, gain [favor] from the " +
      "[suit-hearth] bank.")
  val powers: Vector[PrintedPower] = Vector(power)

object SpinningBeeCard extends Denizen(DenizenId("232"), "Spinning Bee", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.spinning-bee"),
    persistent = false, cost = Cost.free,
    text = "Declare a suit. Draw from your discard pile until you draw " +
      "a matching card. If you draw none, discard them all. You " +
      "**cannot** use other Search modifiers.")
  val powers: Vector[PrintedPower] = Vector(power)

object FirebrandCard extends Denizen(DenizenId("233"), "Firebrand", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.firebrand"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Add [favor] to the People's Favor from any " +
      "favor bank, or burn [favor-burnt] from the People's Favor.")
  val powers: Vector[PrintedPower] = Vector(power)

object WatchdogCard extends Denizen(DenizenId("234"), "Watchdog", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.watchdog"),
    persistent = false, cost = Cost.free,
    text = "+[defense-die] if any target is in the Cradle.")
  val powers: Vector[PrintedPower] = Vector(power)

object FavoredSonCard extends Denizen(DenizenId("235"), "Favored Son", Suit.Hearth) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.favored-son"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** take the People's Favor. If you do, adjust " +
      "its [favor] using its left ribbon.")
  val powers: Vector[PrintedPower] = Vector(power)

object TownMeetingCard extends Denizen(DenizenId("236"), "Town Meeting", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.town-meeting"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** gain [favor] from any favor bank for each " +
      "[suit-hearth] card you rule. _This includes your advisers, " +
      "even Town Meeting._")
  val powers: Vector[PrintedPower] = Vector(power)

object LeagueTreatyCard extends Denizen(DenizenId("237"), "League Treaty", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.league-treaty"),
    persistent = true, cost = Cost.free,
    text = "During the Rest Phase of any player, League Treaty's ruler " +
      "may move any [favor] from cards in its region to any one " +
      "favor bank.")
  val powers: Vector[PrintedPower] = Vector(power)

object SkilledMerchantsCard extends Denizen(DenizenId("238"), "Skilled Merchants", Suit.Hearth) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.skilled-merchants"),
    persistent = false, cost = Cost.free,
    text = "You may trade with any denizens in your region.")
  val powers: Vector[PrintedPower] = Vector(power)

object WaysideInnCard extends Denizen(DenizenId("47"), "Wayside Inn", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.wayside-inn"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Gain 2 Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

object ExtraProvisionsCard extends Denizen(DenizenId("48"), "Extra Provisions", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.extra-provisions"),
    persistent = false, cost = Cost(favor = 1),
    text = "+ [defense-die]")
  val powers: Vector[PrintedPower] = Vector(power)

object MemoryOfHomeCard extends Denizen(DenizenId("49"), "Memory of Home", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.memory-of-home"),
    persistent = false, cost = Cost(secret = 1, secretBurnt = 1),
    text = "**ACTION:** Move all [favor] from any one favor bank to the " +
      "[suit-hearth] bank.")
  val powers: Vector[PrintedPower] = Vector(power)

object WelcomingPartyCard extends Denizen(DenizenId("50"), "Welcoming Party", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.welcoming-party"),
    persistent = false, cost = Cost.free,
    text = "If you play a denizen card that was not a facedown adviser, " +
      "gain [favor] from the [suit-hearth] bank.")
  val powers: Vector[PrintedPower] = Vector(power)

object TravelingDoctorCard extends Denizen(DenizenId("51"), "Traveling Doctor", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.traveling-doctor"),
    persistent = false, cost = Cost.free,
    text = "If you're defeated, kill no warbands in your force and " +
      "discard Traveling Doctor. **Ignore** powers that kill all " +
      "of your force.")
  val powers: Vector[PrintedPower] = Vector(power)

object StorytellerCard extends Denizen(DenizenId("52"), "Storyteller", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.storyteller"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Place [secret] from the shared bank on the " +
      "Darkest Secret.")
  val powers: Vector[PrintedPower] = Vector(power)

object ArmedMobCard extends Denizen(DenizenId("53"), "Armed Mob", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.armed-mob"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Discard a faceup adviser from a player who " +
      "holds the Darkest Secret but not the People's Favor.")
  val powers: Vector[PrintedPower] = Vector(power)

object TavernSongsCard extends Denizen(DenizenId("54"), "Tavern Songs", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.tavern-songs"),
    persistent = false, cost = Cost.free,
    text = "**ACTION:** Peek at the top three cards of your region's " +
      "discard pile.")
  val powers: Vector[PrintedPower] = Vector(power)
