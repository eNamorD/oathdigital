package oathdigital.catalog.holding

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.model.{Cost, PowerId, RelicId}

// Generated from the retired runtime JSON (card classes slice 1).
// Hand-edited from here on; a card moves out when it is implemented.

object CursedCauldronCard extends Relic(RelicId("R02"), "Cursed Cauldron", value = 84, defense = 2):
  val power = PrintedPower(PowerId("relic.cursed-cauldron"),
    persistent = false, cost = Cost.free,
    text = "If you're victorious, gain 1 warband per enemy warband " +
      "killed in this campaign.")
  val powers: Vector[PrintedPower] = Vector(power)

object GrandMaskCard extends Relic(RelicId("R05"), "Grand Mask", value = 36, defense = 1):
  val power = PrintedPower(PowerId("relic.grand-mask"),
    persistent = true, cost = Cost.free,
    text = "If you're an Exile, during your turn you rule cards except " +
      "battle plans at Imperial sites, and Imperial players do not.")
  val powers: Vector[PrintedPower] = Vector(power)

object CrackedHornCard extends Relic(RelicId("R10"), "Cracked Horn", value = 21, defense = 1):
  val power = PrintedPower(PowerId("relic.cracked-horn"),
    persistent = false, cost = Cost.free,
    text = "You may put all of the cards you discard on the bottom of " +
      "the world deck.")
  val powers: Vector[PrintedPower] = Vector(power)

object BanditCrownCard extends Relic(RelicId("R11"), "Bandit Crown", value = 56, defense = 3):
  val power = PrintedPower(PowerId("relic.bandit-crown"),
    persistent = true, cost = Cost.free,
    text = "You may use cards at sites ruled by bandits as if you ruled " +
      "them. _You may use these battle plans against bandits._")
  val powers: Vector[PrintedPower] = Vector(power)

object RingOfDevotionCard extends Relic(RelicId("R12"), "Ring of Devotion", value = 90, defense = 1):
  val power = PrintedPower(PowerId("relic.king-of-devotion"),
    persistent = false, cost = Cost.free,
    text = "You may discard cards to your region's discard pile instead " +
      "of the next region out.")
  val powers: Vector[PrintedPower] = Vector(power)

object ObsidianCageCard extends Relic(RelicId("R18"), "Obsidian Cage", value = 72, defense = 3):
  val campaign = PrintedPower(PowerId("relic.obsidian-cage.campaign"),
    persistent = false, cost = Cost.free,
    text = "If you're victorious, move all unkilled warbands in your " +
      "enemy's force to Obsidian Cage.")
  val action = PrintedPower(PowerId("relic.obsidian-cage.action"),
    persistent = false, cost = Cost.free,
    text = "**ACTION:** Move any number of warbands from Obsidian Cage " +
      "to any board of the same color.")
  val powers: Vector[PrintedPower] = Vector(campaign, action)

object AncientWritCard extends Relic(RelicId("R22"), "Ancient Writ", value = 81, defense = 1):
  val power = PrintedPower(PowerId("relic.ancient-grit"),
    persistent = false, cost = Cost.free,
    text = "**ACTION:** Bury this to draw 3 legacy cards, keep 1 " +
      "facedown, and discard the other 2.")
  val powers: Vector[PrintedPower] = Vector(power)

object PaintedTrumpetCard extends Relic(RelicId("R23"), "Painted Trumpet", value = 46, defense = 1):
  val power = PrintedPower(PowerId("relic.painted-trumpet"),
    persistent = false, cost = Cost.free,
    text = "Instead of drawing from one discard pile, you may draw the " +
      "top card from each discard pile. This draw amount " +
      "**cannot** be modified.")
  val powers: Vector[PrintedPower] = Vector(power)

object ImperialSealCard extends Relic(RelicId("R26"), "Imperial Seal", value = 35, defense = 2):
  val power = PrintedPower(PowerId("relic.imperial-seal"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** If no Imperial pawns are at your site, move any " +
      "number of Imperial warbands from your site to any Imperial " +
      "site or Imperial player board.")
  val powers: Vector[PrintedPower] = Vector(power)

object SingingMaskCard extends Relic(RelicId("R28"), "Singing Mask", value = 54, defense = 1):
  val power = PrintedPower(PowerId("relic.singing-mask"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Choose a denizen at your site. Move up to " +
      "[favor] [favor] between the matching favor bank and the " +
      "People's Favor.")
  val powers: Vector[PrintedPower] = Vector(power)

object LostTapestryCard extends Relic(RelicId("R29"), "Lost Tapestry", value = 85, defense = 1):
  val power = PrintedPower(PowerId("relic.lost-tapestry"),
    persistent = true, cost = Cost.free,
    text = "Facedown Visions do not count against your adviser limit.")
  val powers: Vector[PrintedPower] = Vector(power)

object SilverCharmCard extends Relic(RelicId("R31"), "Silver Charm", value = 37, defense = 1):
  val power = PrintedPower(PowerId("relic.silver-charm"),
    persistent = false, cost = Cost.free,
    text = "Each time you spend Supply to roll [attack-die], you may " +
      "also reroll any number of previous [attack-die] at the same " +
      "time.")
  val powers: Vector[PrintedPower] = Vector(power)

object WineOfWelcomeCard extends Relic(RelicId("R33"), "Wine of Welcome", value = 7, defense = 1):
  val power = PrintedPower(PowerId("relic.wine-of-welcome"),
    persistent = false, cost = Cost.free,
    text = "Draw from the bottom of the world deck instead of the top.")
  val powers: Vector[PrintedPower] = Vector(power)

object WhisperingStoneCard extends Relic(RelicId("R34"), "Whispering Stone", value = 13, defense = 2):
  val power = PrintedPower(PowerId("relic.whispering-stone"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Negotiate with any one player in private, even " +
      "if their pawn is not at your site.")
  val powers: Vector[PrintedPower] = Vector(power)

object SecretTestamentCard extends Relic(RelicId("R38"), "Secret Testament", value = 82, defense = 2):
  val power = PrintedPower(PowerId("relic.secret-testament"),
    persistent = true, cost = Cost.free,
    text = "When an enemy takes a banner from you, take all [favor] or " +
      "[secret] on the banner.")
  val powers: Vector[PrintedPower] = Vector(power)

object YewStaffCard extends Relic(RelicId("R40"), "Yew Staff", value = 60, defense = 2):
  val power = PrintedPower(PowerId("relic.yew-staff"),
    persistent = true, cost = Cost.free,
    text = "When using card powers, you may act as if you hold the " +
      "Darkest Secret.")
  val powers: Vector[PrintedPower] = Vector(power)

object SpitefulMirrorCard extends Relic(RelicId("R41"), "Spiteful Mirror", value = 27, defense = 1):
  val power = PrintedPower(PowerId("relic.spiteful-mirror"),
    persistent = false, cost = Cost(secret = 1),
    text = "This turn, you may act as if any cards you use to trade or " +
      "muster are [suit-discord] cards.")
  val powers: Vector[PrintedPower] = Vector(power)

object WeepingBannerCard extends Relic(RelicId("R42"), "Weeping Banner", value = 63, defense = 2):
  val power = PrintedPower(PowerId("relic.keeping-banner"),
    persistent = false, cost = Cost(secret = 1),
    text = "The attacker **must** resolve another [skull] for each " +
      "[skull] rolled.")
  val powers: Vector[PrintedPower] = Vector(power)

object SigilOfTheEyeCard extends Relic(RelicId("R43"), "Sigil of the Eye", value = 66, defense = 1):
  val power = PrintedPower(PowerId("relic.sigil-of-the-eye"),
    persistent = false, cost = Cost.free,
    text = "You may challenge the Darkest Secret from any site.")
  val powers: Vector[PrintedPower] = Vector(power)

object SigilOfTheHeartCard extends Relic(RelicId("R44"), "Sigil of the Heart", value = 53, defense = 1):
  val power = PrintedPower(PowerId("relic.sigil-of-the-heart"),
    persistent = false, cost = Cost.free,
    text = "You may challenge the People's Favor from any site.")
  val powers: Vector[PrintedPower] = Vector(power)
