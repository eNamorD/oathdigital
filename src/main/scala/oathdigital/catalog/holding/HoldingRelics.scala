package oathdigital.catalog.holding

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.model.{Cost, PowerId, RelicId}

// Generated from the retired runtime JSON (card classes slice 1).
// Hand-edited from here on; a card moves out when it is implemented.

object StickyFireCard extends Relic(RelicId("R01"), "Sticky Fire", value = 3, defense = 3):
  val power = PrintedPower(PowerId("relic.sticky-fire"),
    persistent = false, cost = Cost.free,
    text = "If you're victorious, you may kill all the warbands in your " +
      "enemy's force. If you do, you **must** give them [favor] if " +
      "able.")
  val powers: Vector[PrintedPower] = Vector(power)

object CursedCauldronCard extends Relic(RelicId("R02"), "Cursed Cauldron", value = 84, defense = 2):
  val power = PrintedPower(PowerId("relic.cursed-cauldron"),
    persistent = false, cost = Cost.free,
    text = "If you're victorious, gain 1 warband per enemy warband " +
      "killed in this campaign.")
  val powers: Vector[PrintedPower] = Vector(power)

object BrassHorseCard extends Relic(RelicId("R03"), "Brass Horse", value = 76, defense = 2):
  val power = PrintedPower(PowerId("relic.brass-horse"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Reveal the top card of your region's discard " +
      "pile. Place your pawn at a different site with a matching " +
      "card. If you **cannot**, place it at any site.")
  val powers: Vector[PrintedPower] = Vector(power)

object TruthfulHarpCard extends Relic(RelicId("R04"), "Truthful Harp", value = 39, defense = 0):
  val power = PrintedPower(PowerId("relic.truthful-harp"),
    persistent = false, cost = Cost.free,
    text = "You may draw 2 more cards. If you do, you **must** reveal " +
      "every card you draw and the card you keep.")
  val powers: Vector[PrintedPower] = Vector(power)

object GrandMaskCard extends Relic(RelicId("R05"), "Grand Mask", value = 36, defense = 1):
  val power = PrintedPower(PowerId("relic.grand-mask"),
    persistent = true, cost = Cost.free,
    text = "If you're an Exile, during your turn you rule cards except " +
      "battle plans at Imperial sites, and Imperial players do not.")
  val powers: Vector[PrintedPower] = Vector(power)

object HornedMaskCard extends Relic(RelicId("R06"), "Horned Mask", value = 48, defense = 2):
  val power = PrintedPower(PowerId("relic.horned-mask"),
    persistent = false, cost = Cost.free,
    text = "**WAKE:** You may take a non-edifice denizen from your site " +
      "as a facedown adviser _(once per turn, like all Wake " +
      "powers)_.")
  val powers: Vector[PrintedPower] = Vector(power)

object CupOfPlentyCard extends Relic(RelicId("R07"), "Cup of Plenty", value = 96, defense = 1):
  val power = PrintedPower(PowerId("relic.cup-of-plenty"),
    persistent = false, cost = Cost.free,
    text = "Spend no Supply if you're trading with a card that does not " +
      "match any of your advisers.")
  val powers: Vector[PrintedPower] = Vector(power)

object WhistleCard extends Relic(RelicId("R08"), "Whistle", value = 6, defense = 1):
  val power = PrintedPower(PowerId("relic.whistle"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Take a pawn from another site, place it at your " +
      "site, and give them the [secret] here.")
  val powers: Vector[PrintedPower] = Vector(power)

object DowsingSticksCard extends Relic(RelicId("R09"), "Dowsing Sticks", value = 9, defense = 1):
  val power = PrintedPower(PowerId("relic.dowsing-sticks"),
    persistent = false, cost = Cost(secret = 1, secretBurnt = 2),
    text = "**ACTION:** Draw a relic from the relic deck and take it. " +
      "_(You may keep it facedown.)_")
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

object SkeletonKeyCard extends Relic(RelicId("R13"), "Skeleton Key", value = 18, defense = 2):
  val power = PrintedPower(PowerId("relic.skeleton-key"),
    persistent = false, cost = Cost(secret = 1, secretBurnt = 1),
    text = "**ACTION:** If your pawn is at a Hinterland site, take a " +
      "relic from your site.")
  val powers: Vector[PrintedPower] = Vector(power)

object OracularPigCard extends Relic(RelicId("R14"), "Oracular Pig", value = 93, defense = 2):
  val power = PrintedPower(PowerId("relic.oracular-pig"),
    persistent = false, cost = Cost.free,
    text = "**ACTION:** Peek at the top 3 cards of the world deck.")
  val powers: Vector[PrintedPower] = Vector(power)

object CircletOfCommandCard extends Relic(RelicId("R15"), "Circlet of Command", value = 12, defense = 0):
  val power = PrintedPower(PowerId("relic.circlet-of-command"),
    persistent = true, cost = Cost.free,
    text = "Players **cannot** target your banners or your other relics.")
  val powers: Vector[PrintedPower] = Vector(power)

object IvoryEyeCard extends Relic(RelicId("R16"), "Ivory Eye", value = 79, defense = 2):
  val power = PrintedPower(PowerId("relic.ivory-eye"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Peek at any facedown adviser. _(This includes " +
      "Visions.)_")
  val powers: Vector[PrintedPower] = Vector(power)

object ShiftingMapCard extends Relic(RelicId("R17"), "Shifting Map", value = 15, defense = 1):
  val power = PrintedPower(PowerId("relic.shifting-map"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Gain 1 Supply.")
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

object BookOfRecordsCard extends Relic(RelicId("R19"), "Book of Records", value = 50, defense = 1):
  val power = PrintedPower(PowerId("relic.book-of-records"),
    persistent = false, cost = Cost(secret = 1, secretBurnt = 2),
    text = "**ACTION:** Take [favor] [favor] or [secret] [secret] from " +
      "a banner held by a player whose pawn is at your site.")
  val powers: Vector[PrintedPower] = Vector(power)

object DragonskinDrumCard extends Relic(RelicId("R20"), "Dragonskin Drum", value = 59, defense = 2):
  val power = PrintedPower(PowerId("relic.dragonskin-drum"),
    persistent = false, cost = Cost.free,
    text = "After traveling, gain one warband.")
  val powers: Vector[PrintedPower] = Vector(power)

object CrystalVialCard extends Relic(RelicId("R21"), "Crystal Vial", value = 71, defense = 1):
  val power = PrintedPower(PowerId("relic.crystal-vial"),
    persistent = false, cost = Cost(secret = 1, secretBurnt = 1),
    text = "**ACTION:** Bury an adviser of yours or a denizen at your " +
      "site. _(Bury ignores the locked restriction.)_")
  val powers: Vector[PrintedPower] = Vector(power)

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

object BoneDiceCard extends Relic(RelicId("R24"), "Bone Dice", value = 1, defense = 1):
  val power = PrintedPower(PowerId("relic.bone-dice"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Roll [attack-die] [attack-die]. Gain 1 Supply " +
      "for each [sword] rolled, then bury this relic if you rolled " +
      "any [skull].")
  val powers: Vector[PrintedPower] = Vector(power)

object BrassArmyCard extends Relic(RelicId("R25"), "Brass Army", value = 77, defense = 3):
  val campaign = PrintedPower(PowerId("relic.brass-army.campaign"),
    persistent = false, cost = Cost(secret = 1),
    text = "+4 [attack-die]")
  val restriction = PrintedPower(PowerId("relic.brass-army.restriction"),
    persistent = true, cost = Cost.free,
    text = "Whenever your pawn moves _(travel or place)_, bury this or " +
      "flip a secret you have facedown.")
  val powers: Vector[PrintedPower] = Vector(campaign, restriction)

object ImperialSealCard extends Relic(RelicId("R26"), "Imperial Seal", value = 35, defense = 2):
  val power = PrintedPower(PowerId("relic.imperial-seal"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** If no Imperial pawns are at your site, move any " +
      "number of Imperial warbands from your site to any Imperial " +
      "site or Imperial player board.")
  val powers: Vector[PrintedPower] = Vector(power)

object FearsomeShieldCard extends Relic(RelicId("R27"), "Fearsome Shield", value = 52, defense = 1):
  val power = PrintedPower(PowerId("relic.fearsome-shield"),
    persistent = false, cost = Cost(secretBurnt = 2),
    text = "+2 [defense-die]")
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

object BanditStandardCard extends Relic(RelicId("R30"), "Bandit Standard", value = 55, defense = 3):
  val power = PrintedPower(PowerId("relic.bandit-standard"),
    persistent = false, cost = Cost.free,
    text = "+ [attack-die] for each bandit in your region. This " +
      "**cannot** be used while targeting sites ruled by bandits.")
  val powers: Vector[PrintedPower] = Vector(power)

object SilverCharmCard extends Relic(RelicId("R31"), "Silver Charm", value = 37, defense = 1):
  val power = PrintedPower(PowerId("relic.silver-charm"),
    persistent = false, cost = Cost.free,
    text = "Each time you spend Supply to roll [attack-die], you may " +
      "also reroll any number of previous [attack-die] at the same " +
      "time.")
  val powers: Vector[PrintedPower] = Vector(power)

object AmberFlameCard extends Relic(RelicId("R32"), "Amber Flame", value = 26, defense = 2):
  val power = PrintedPower(PowerId("relic.amber-flame"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Burn [favor-burnt] or [secret-burnt] from a " +
      "banner held by a player whose pawn is at your site.")
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

object BlackSwordCard extends Relic(RelicId("R35"), "Black Sword", value = 73, defense = 2):
  val power = PrintedPower(PowerId("relic.black-sword"),
    persistent = false, cost = Cost(secretBurnt = 2),
    text = "+5 [attack-die]")
  val powers: Vector[PrintedPower] = Vector(power)

object BarbedNetCard extends Relic(RelicId("R36"), "Barbed Net", value = 33, defense = 1):
  val power = PrintedPower(PowerId("relic.barbed-net"),
    persistent = false, cost = Cost(secretBurnt = 3),
    text = "**ACTION:** Take a relic from your site. _(You may keep it " +
      "facedown.)_")
  val powers: Vector[PrintedPower] = Vector(power)

object BagOfSiegeworksCard extends Relic(RelicId("R37"), "Bag of Siegeworks", value = 42, defense = 2):
  val power = PrintedPower(PowerId("relic.bag-of-siegeworks"),
    persistent = false, cost = Cost(secret = 1),
    text = "If you're targeting sites, ignore [defense-die] rolls with " +
      "a single [shield].")
  val powers: Vector[PrintedPower] = Vector(power)

object SecretTestamentCard extends Relic(RelicId("R38"), "Secret Testament", value = 82, defense = 2):
  val power = PrintedPower(PowerId("relic.secret-testament"),
    persistent = true, cost = Cost.free,
    text = "When an enemy takes a banner from you, take all [favor] or " +
      "[secret] on the banner.")
  val powers: Vector[PrintedPower] = Vector(power)

object MagicCarpetCard extends Relic(RelicId("R39"), "Magic Carpet", value = 51, defense = 1):
  val power = PrintedPower(PowerId("relic.magic-carpet"),
    persistent = false, cost = Cost.free,
    text = "**ACTION:** Place your pawn at any site. Then, discard " +
      "Magic Carpet or give it to a player at a different site.")
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

object MagicWaterskinCard extends Relic(RelicId("R45"), "Magic Waterskin", value = 24, defense = 1):
  val power = PrintedPower(PowerId("relic.magic-waterskin"),
    persistent = false, cost = Cost.free,
    text = "**ACTION:** Bury this relic to gain 4 Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

object DemonTailCard extends Relic(RelicId("R46"), "Demon Tail", value = 69, defense = 1):
  val power = PrintedPower(PowerId("relic.demon-tail"),
    persistent = false, cost = Cost(secretBurnt = 3),
    text = "**ACTION:** Gain 2 Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

object ClayRattleCard extends Relic(RelicId("R47"), "Clay Rattle", value = 45, defense = 2):
  val power = PrintedPower(PowerId("relic.clay-rattle"),
    persistent = false, cost = Cost(secret = 2),
    text = "**ACTION:** Shuffle the world deck or any region's discard " +
      "pile.")
  val powers: Vector[PrintedPower] = Vector(power)

object TheGrandScepterCard extends Relic(RelicId("grand-scepter"), "The Grand Scepter", value = 0, defense = 3):
  val restriction = PrintedPower(PowerId("relic.the-grand-scepter.restriction"),
    persistent = false, cost = Cost.free,
    text = "This relic **cannot** be removed from play or added to a " +
      "lineage.")
  val campaign = PrintedPower(PowerId("relic.the-grand-scepter.campaign"),
    persistent = false, cost = Cost.free,
    text = "[favor-burnt][favor-burnt][favor-burnt] If you are Imperial " +
      "and victorious against a Citizen, you exile them.")
  val action = PrintedPower(PowerId("relic.the-grand-scepter.action"),
    persistent = false, cost = Cost.free,
    text = "**ACTION:** You may become a Citizen if an Exile. **IN " +
      "NEGOTIATION:** May offer Citizenship.")
  val negotiation = PrintedPower(PowerId("relic.the-grand-scepter.negotiation"),
    persistent = false, cost = Cost.free,
    text = "**IN NEGOTIATION:** May offer Citizenship.")
  val powers: Vector[PrintedPower] = Vector(restriction, campaign, action, negotiation)
