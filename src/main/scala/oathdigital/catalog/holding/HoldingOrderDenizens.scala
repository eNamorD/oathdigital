package oathdigital.catalog.holding

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower, SiteOnly}
import oathdigital.model.{Cost, DenizenId, PowerId, Suit}

// Generated from the retired runtime JSON (card classes slice 1).
// Hand-edited from here on; a card moves out when it is implemented.

object WrestlersCard extends Denizen(DenizenId("1"), "Wrestlers", Suit.Order):
  val power = PrintedPower(PowerId("denizen.wrestlers"),
    persistent = false, cost = Cost.free,
    text = "+ [defense-die] if you sacrifice one warband in your force.")
  val powers: Vector[PrintedPower] = Vector(power)

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

object OutridersCard extends Denizen(DenizenId("104"), "Outriders", Suit.Order):
  val power = PrintedPower(PowerId("denizen.outriders"),
    persistent = false, cost = Cost.free,
    text = "Ignore all skulls [skull] you roll.")
  val powers: Vector[PrintedPower] = Vector(power)

object MessengerCard extends Denizen(DenizenId("105"), "Messenger", Suit.Order):
  val power = PrintedPower(PowerId("denizen.messenger"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Move any warbands to and from your board and " +
      "any sites you rule _(except the last warband from a site)_.")
  val powers: Vector[PrintedPower] = Vector(power)

object FieldPromotionCard extends Denizen(DenizenId("106"), "Field Promotion", Suit.Order):
  val power = PrintedPower(PowerId("denizen.field-promotion"),
    persistent = false, cost = Cost(favor = 1),
    text = "If you're victorious, gain three warbands.")
  val powers: Vector[PrintedPower] = Vector(power)

object PalanquinCard extends Denizen(DenizenId("107"), "Palanquin", Suit.Order) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.palanquin"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Choose a player whose pawn is at your site. Put " +
      "your pawn on a site that they can travel to. Make them " +
      "travel to that site, spending no Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

object ShieldWallCard extends Denizen(DenizenId("108"), "Shield Wall", Suit.Order):
  val power = PrintedPower(PowerId("denizen.shield-wall"),
    persistent = false, cost = Cost(favor = 1),
    text = "+2 [defense-die] If you're defeated, kill all of your force.")
  val powers: Vector[PrintedPower] = Vector(power)

object MilitaryParadeCard extends Denizen(DenizenId("109"), "Military Parade", Suit.Order):
  val power = PrintedPower(PowerId("denizen.military-parade"),
    persistent = false, cost = Cost.free,
    text = "If you're victorious, gain [favor] from the favor banks " +
      "matching each adviser of your enemy _(including Imperial " +
      "Allies)_.")
  val powers: Vector[PrintedPower] = Vector(power)

object TyrantCard extends Denizen(DenizenId("111"), "Tyrant", Suit.Order) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.tyrant"),
    persistent = false, cost = Cost.free,
    text = "You **must** kill a warband _(even your own)_ at the site " +
      "you travel to, if able.")
  val powers: Vector[PrintedPower] = Vector(power)

object ForcedLaborCard extends Denizen(DenizenId("112"), "Forced Labor", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.forced-labor"),
    persistent = true, cost = Cost.free,
    text = "Enemies **cannot** search if their pawn is at any site " +
      "ruled by Forced Labor's ruler unless they give [favor] to " +
      "its ruler. _(Give it to Chancellor if Empire, burn it if " +
      "bandits.)_")
  val powers: Vector[PrintedPower] = Vector(power)

object SecretPoliceCard extends Denizen(DenizenId("113"), "Secret Police", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.secret-police"),
    persistent = true, cost = Cost.free,
    text = "Enemies **cannot** play Visions faceup while their pawn is " +
      "at any site ruled by Secret Police's ruler.")
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

object SiegeEnginesCard extends Denizen(DenizenId("116"), "Siege Engines", Suit.Order):
  val power = PrintedPower(PowerId("denizen.siege-engines"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Kill two warbands _(even yours)_ at any one " +
      "site in your region.")
  val powers: Vector[PrintedPower] = Vector(power)

object RoyalTaxCard extends Denizen(DenizenId("117"), "Royal Tax", Suit.Order):
  val power = PrintedPower(PowerId("denizen.royal-tax"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:**: take [favor] [favor] from each player " +
      "whose pawn is at a site you rule in your pawn's region.")
  val powers: Vector[PrintedPower] = Vector(power)

object TollRoadsCard extends Denizen(DenizenId("118"), "Toll Roads", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.toll-roads"),
    persistent = true, cost = Cost.free,
    text = "Enemies **cannot** travel to sites ruled by Toll Roads' " +
      "ruler unless they give [favor] to its ruler. _(Give it to " +
      "Chancellor if Empire, burn it if bandits.)_")
  val powers: Vector[PrintedPower] = Vector(power)

object CurfewCard extends Denizen(DenizenId("119"), "Curfew", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.curfew"),
    persistent = true, cost = Cost.free,
    text = "Enemies **cannot** trade with cards ruled by Curfew's ruler " +
      "unless they give [favor] to its ruler. _(Give it to " +
      "Chancellor if Empire, burn it if bandits.)_")
  val powers: Vector[PrintedPower] = Vector(power)

object KnightsErrantCard extends Denizen(DenizenId("120"), "Knights Errant", Suit.Order):
  val power = PrintedPower(PowerId("denizen.knights-errant"),
    persistent = false, cost = Cost.free,
    text = "After mustering, you may campaign, spending no Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

object VowOfObedienceCard extends Denizen(DenizenId("121"), "Vow of Obedience", Suit.Order) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-obedience"),
    persistent = false, cost = Cost.free,
    text = "You **cannot** play Visions faceup. **REST:** Take [favor] " +
      "from any one favor bank.")
  val powers: Vector[PrintedPower] = Vector(power)

object HuntingPartyCard extends Denizen(DenizenId("122"), "Hunting Party", Suit.Order):
  val power = PrintedPower(PowerId("denizen.hunting-party"),
    persistent = false, cost = Cost.free,
    text = "After searching the world deck, you may campaign, spending " +
      "no Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

object CouncilSeatCard extends Denizen(DenizenId("123"), "Council Seat", Suit.Order) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.council-seat"),
    persistent = true, cost = Cost.free,
    text = "If you're a Citizen, you **cannot** be exiled, even by " +
      "yourself.")
  val powers: Vector[PrintedPower] = Vector(power)

object EncirclementCard extends Denizen(DenizenId("124"), "Encirclement", Suit.Order):
  val power = PrintedPower(PowerId("denizen.encirclement"),
    persistent = false, cost = Cost(favor = 1),
    text = "±2 [attack-die] if your force is larger than your enemy's.")
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

object BattleHonorsCard extends Denizen(DenizenId("2"), "Battle Honors", Suit.Order):
  val power = PrintedPower(PowerId("denizen.battle-honors"),
    persistent = false, cost = Cost.free,
    text = "If you're victorious, gain [favor] [favor] from the " +
      "[suit-order] bank.")
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

object HonorGuardCard extends Denizen(DenizenId("251"), "Honor Guard", Suit.Order) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.honor-guard"),
    persistent = false, cost = Cost(favor = 2, favorBurnt = 1),
    text = "**ACTION:** Choose a player with no [suit-order] advisers " +
      "whose pawn is at your site. Bury a faceup adviser they have.")
  val powers: Vector[PrintedPower] = Vector(power)

object CityWallCard extends Denizen(DenizenId("252"), "City Wall", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.city-wall"),
    persistent = true, cost = Cost.free,
    text = "While at this site, enemies of City Wall's ruler **cannot** " +
      "place [favor] / [secret] on cards or use non-persistent " +
      "powers.")
  val powers: Vector[PrintedPower] = Vector(power)

object FearsomeGeneralCard extends Denizen(DenizenId("253"), "Fearsome General", Suit.Order):
  val power = PrintedPower(PowerId("denizen.fearsome-general"),
    persistent = false, cost = Cost.free,
    text = "Each [skull] you roll adds two more [sword] _(4 total)_, " +
      "even if the [skull] is ignored _(Outriders, etc.)_.")
  val powers: Vector[PrintedPower] = Vector(power)

object CarefulPlansCard extends Denizen(DenizenId("254"), "Careful Plans", Suit.Order):
  val power = PrintedPower(PowerId("denizen.careful-plans"),
    persistent = false, cost = Cost(favor = 1),
    text = "The defender **must** roll their [defense-die] before the " +
      "attacker rolls their [attack-die].")
  val powers: Vector[PrintedPower] = Vector(power)

object GarrisonArmoryCard extends Denizen(DenizenId("255"), "Garrison Armory", Suit.Order):
  val power = PrintedPower(PowerId("denizen.garrison-armory"),
    persistent = false, cost = Cost(favor = 1),
    text = "In a conquest, warbands on targeted sites each add +2 " +
      "defense _(instead of +1)_.")
  val powers: Vector[PrintedPower] = Vector(power)

object BattleAxesCard extends Denizen(DenizenId("256"), "Battle Axes", Suit.Order):
  val power = PrintedPower(PowerId("denizen.battle-axes"),
    persistent = false, cost = Cost.free,
    text = "±2 [attack-die] if your enemy rules a [suit-beast] card.")
  val powers: Vector[PrintedPower] = Vector(power)

object GreatFeastCard extends Denizen(DenizenId("257"), "Great Feast", Suit.Order):
  val power = PrintedPower(PowerId("denizen.great-feast"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Discard a [suit-beast] card at your site to " +
      "gain 3 Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

object QuartermasterCard extends Denizen(DenizenId("258"), "Quartermaster", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.quartermaster"),
    persistent = false, cost = Cost.free,
    text = "**WAKE:** If you rule this card, gain 1 Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

object BearTrapsCard extends Denizen(DenizenId("3"), "Bear Traps", Suit.Order):
  val power = PrintedPower(PowerId("denizen.bear-traps"),
    persistent = false, cost = Cost.free,
    text = "- [attack-die] Kill one warband on the attacker's board.")
  val powers: Vector[PrintedPower] = Vector(power)

object LongbowsCard extends Denizen(DenizenId("4"), "Longbows", Suit.Order):
  val power = PrintedPower(PowerId("denizen.longbows"),
    persistent = false, cost = Cost.free,
    text = "± [attack-die]")
  val powers: Vector[PrintedPower] = Vector(power)

object KeepCard extends Denizen(DenizenId("5"), "Keep", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.keep"),
    persistent = false, cost = Cost.free,
    text = "If defeated in a raid, after the attacker takes what they " +
      "targeted, place your pawn at this site and end the action. " +
      "_Do not burn favor, move pawn, etc._")
  val powers: Vector[PrintedPower] = Vector(power)

object PressgangsCard extends Denizen(DenizenId("6"), "Pressgangs", Suit.Order):
  val power = PrintedPower(PowerId("denizen.pressgangs"),
    persistent = false, cost = Cost.free,
    text = "You can muster on cards that already have [favor] or " +
      "[secret] on them.")
  val powers: Vector[PrintedPower] = Vector(power)

object GarrisonCard extends Denizen(DenizenId("7"), "Garrison", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.garrison"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** gain one warband per site you rule, and " +
      "put one warband from your board on each site you rule.")
  val powers: Vector[PrintedPower] = Vector(power)

object ScoutsCard extends Denizen(DenizenId("8"), "Scouts", Suit.Order):
  val power = PrintedPower(PowerId("denizen.scouts"),
    persistent = false, cost = Cost.free,
    text = "Gain 1 Supply.")
  val powers: Vector[PrintedPower] = Vector(power)
