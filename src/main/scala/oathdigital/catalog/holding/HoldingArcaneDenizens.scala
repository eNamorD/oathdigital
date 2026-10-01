package oathdigital.catalog.holding

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower, SiteOnly}
import oathdigital.model.{Cost, DenizenId, PowerId, Suit}

// Generated from the retired runtime JSON (card classes slice 1).
// Hand-edited from here on; a card moves out when it is implemented.

object GolemLegionsCard extends Denizen(DenizenId("199"), "Golem Legions", Suit.Arcane) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.golem-legions"),
    persistent = false, cost = Cost.free,
    text = "You may muster by placing [favor] on Golem Legions. _It " +
      "counts itself as a matching adviser._")
  val powers: Vector[PrintedPower] = Vector(power)

object CouncilArbiterCard extends Denizen(DenizenId("200"), "Council Arbiter", Suit.Arcane) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.council-arbiter"),
    persistent = false, cost = Cost.free,
    text = "**WHEN NEGOTIATING:** You may give, receive, and swap " +
      "advisers. _You cannot exchange locked advisers. You still " +
      "follow your adviser limit._")
  val powers: Vector[PrintedPower] = Vector(power)

object CatacombsCard extends Denizen(DenizenId("201"), "Catacombs", Suit.Arcane) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.catacombs"),
    persistent = false, cost = Cost(secret = 1),
    text = "If this site has an empty relic slot, draw a relic from the " +
      "relic deck and place it here facedown.")
  val powers: Vector[PrintedPower] = Vector(power)

object WardOfSilenceCard extends Denizen(DenizenId("202"), "Ward of Silence", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.ward-of-silence"),
    persistent = false, cost = Cost.free,
    text = "X [secret] **ACTION:** Choose a player whose pawn is at " +
      "your site. Flip X [secret] on their board facedown.")
  val powers: Vector[PrintedPower] = Vector(power)

object VowOfWisdomCard extends Denizen(DenizenId("203"), "Vow of Wisdom", Suit.Arcane) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-wisdom"),
    persistent = true, cost = Cost.free,
    text = "You **must** gain [secret] instead of [favor] when you play " +
      "to a site.")
  val powers: Vector[PrintedPower] = Vector(power)

object ArcaneBrokersCard extends Denizen(DenizenId("204"), "Arcane Brokers", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.arcane-brokers"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Discard a relic you hold to gain [secret] " +
      "[secret].")
  val powers: Vector[PrintedPower] = Vector(power)

object DisciplesCard extends Denizen(DenizenId("205"), "Disciples", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.disciples"),
    persistent = false, cost = Cost.free,
    text = "If you have the Darkest Secret, spend only 2 Supply if " +
      "you're searching the world deck.")
  val powers: Vector[PrintedPower] = Vector(power)

object ArcaneArmorCard extends Denizen(DenizenId("206"), "Arcane Armor", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.arcane-armor"),
    persistent = false, cost = Cost.free,
    text = "X [secret] Set X [defense-die] you have collected to their " +
      "[shield][shield] face instead of rolling them.")
  val powers: Vector[PrintedPower] = Vector(power)

object GlamorCard extends Denizen(DenizenId("207"), "Glamor", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.glamor"),
    persistent = false, cost = Cost.free,
    text = "You may also target [secret] on your enemy's board except " +
      "their last. Add [defense-die] for each targeted. If you're " +
      "victorious, take them.")
  val powers: Vector[PrintedPower] = Vector(power)

object WizardsConclaveCard extends Denizen(DenizenId("208"), "Wizard's Conclave", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.wizard-s-conclave"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** the player who rules the most " +
      "[suit-arcane] cards takes the Darkest Secret. You decide " +
      "ties. _Don't remove secrets from it._")
  val powers: Vector[PrintedPower] = Vector(power)

object FireTalkersCard extends Denizen(DenizenId("31"), "Fire Talkers", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.fire-talkers"),
    persistent = false, cost = Cost(secret = 1),
    text = "±3 [attack-die] if you hold the Darkest Secret.")
  val powers: Vector[PrintedPower] = Vector(power)

object MagiciansCodeCard extends Denizen(DenizenId("32"), "Magician's Code", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.magicians-code"),
    persistent = false, cost = Cost(favor = 2),
    text = "If you're challenging the Darkest Secret, gain [secret] " +
      "[secret] and place them on the banner along with any other " +
      "[secret] you're placing _(even none)_.")
  val powers: Vector[PrintedPower] = Vector(power)

object SpiritSnareCard extends Denizen(DenizenId("33"), "Spirit Snare", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.spirit-snare"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Take [favor] from any one favor bank.")
  val powers: Vector[PrintedPower] = Vector(power)

object WizardSchoolCard extends Denizen(DenizenId("34"), "Wizard School", Suit.Arcane) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.wizard-school"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Gain [secret], then end your Act Phase.")
  val powers: Vector[PrintedPower] = Vector(power)

object DazzleCard extends Denizen(DenizenId("35"), "Dazzle", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.dazzle"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** discard all [suit-hearth] and [suit-order] " +
      "cards at sites in your region.")
  val powers: Vector[PrintedPower] = Vector(power)

object ActingTroupeCard extends Denizen(DenizenId("36"), "Acting Troupe", Suit.Arcane) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.acting-troupe"),
    persistent = false, cost = Cost.free,
    text = "Act as if Acting Troupe is a [suit-beast] or [suit-order] " +
      "card instead.")
  val powers: Vector[PrintedPower] = Vector(power)

object TamingCharmCard extends Denizen(DenizenId("37"), "Taming Charm", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.taming-charm"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Discard a [suit-beast] or [suit-nomad] card at " +
      "your site to gain [favor] [favor] from the matching favor " +
      "bank.")
  val powers: Vector[PrintedPower] = Vector(power)

object InquisitorCard extends Denizen(DenizenId("38"), "Inquisitor", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.inquisitor"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Guess the name of a facedown Vision _(even the " +
      "Conspiracy)_ held by a player whose pawn is at your site. " +
      "If you are correct, discard it.")
  val powers: Vector[PrintedPower] = Vector(power)

object SecretSignalCard extends Denizen(DenizenId("55"), "Secret Signal", Suit.Arcane) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.secret-signal"),
    persistent = false, cost = Cost.free,
    text = "If you gain only one [favor], gain one more [favor].")
  val powers: Vector[PrintedPower] = Vector(power)

object AuguryCard extends Denizen(DenizenId("56"), "Augury", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.augury"),
    persistent = false, cost = Cost.free,
    text = "Draw one more card. _(Stop after a Vision as normal.)_")
  val powers: Vector[PrintedPower] = Vector(power)

object RustingRayCard extends Denizen(DenizenId("57"), "Rusting Ray", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.rusting-ray"),
    persistent = false, cost = Cost(secret = 1),
    text = "If you hold the Darkest Secret, **ignore** all your enemy's " +
      "rolls of hollow swords [hollow-sword].")
  val powers: Vector[PrintedPower] = Vector(power)

object QuickExitCard extends Denizen(DenizenId("58"), "Quick Exit", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.quick-exit"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Place an enemy pawn at your site onto any other " +
      "site.")
  val powers: Vector[PrintedPower] = Vector(power)

object BillowingFogCard extends Denizen(DenizenId("59"), "Billowing Fog", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.billowing-fog"),
    persistent = false, cost = Cost(secret = 1),
    text = "If you're defeated, kill no warbands in your force. " +
      "**Ignore** powers that kill all of your force.")
  val powers: Vector[PrintedPower] = Vector(power)

object KindredWarriorsCard extends Denizen(DenizenId("60"), "Kindred Warriors", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.kindred-warriors"),
    persistent = false, cost = Cost(secret = 1),
    text = "Ignore all skulls [skull] you roll. ±X [attack-die] up to " +
      "the number of other suits you rule.")
  val powers: Vector[PrintedPower] = Vector(power)

object TerrorSpellsCard extends Denizen(DenizenId("61"), "Terror Spells", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.terror-spells"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Kill any two warbands in your region _(at sites " +
      "or on boards, even yours)_ if you hold the Darkest Secret.")
  val powers: Vector[PrintedPower] = Vector(power)

object BloodPactCard extends Denizen(DenizenId("62"), "Blood Pact", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.blood-pact"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Sacrifice an even number of warbands on your " +
      "board. For every two you sacrifice, gain [secret].")
  val powers: Vector[PrintedPower] = Vector(power)

object RevelationCard extends Denizen(DenizenId("63"), "Revelation", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.revelation"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** each player, following turn order, may " +
      "burn any number of [favor] to gain an equal number of " +
      "[secret].")
  val powers: Vector[PrintedPower] = Vector(power)

object ObservatoryCard extends Denizen(DenizenId("64"), "Observatory", Suit.Arcane) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.observatory"),
    persistent = false, cost = Cost.free,
    text = "While your pawn is here, you may draw from any one discard " +
      "pile.")
  val powers: Vector[PrintedPower] = Vector(power)

object PlagueEnginesCard extends Denizen(DenizenId("65"), "Plague Engines", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.plague-engines"),
    persistent = false, cost = Cost(secret = 1, secretBurnt = 1),
    text = "**ACTION:** Each player _(even you)_ places one [favor] per " +
      "site they rule into the [suit-arcane] bank.")
  val powers: Vector[PrintedPower] = Vector(power)

object GleamingArmorCard extends Denizen(DenizenId("66"), "Gleaming Armor", Suit.Arcane) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.gleaming-armor"),
    persistent = true, cost = Cost.free,
    text = "Your enemy's battle plans have an added cost of [secret].")
  val powers: Vector[PrintedPower] = Vector(power)

object BewitchCard extends Denizen(DenizenId("67"), "Bewitch", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.bewitch"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** if you're an Exile and have the most " +
      "[secret] _(even on cards)_, you may take a random relic " +
      "from the Reliquary and become a Citizen. _Follow the steps " +
      "in the rules and end your Act Phase._")
  val powers: Vector[PrintedPower] = Vector(power)

object JinxCard extends Denizen(DenizenId("68"), "Jinx", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.jinx"),
    persistent = true, cost = Cost(secret = 1),
    text = "If you rule Jinx, after you roll [attack-die] or " +
      "[defense-die] for any reason, you may use this power to " +
      "reroll all those dice once. _(This is not a battle plan!)_")
  val powers: Vector[PrintedPower] = Vector(power)

object TutorCard extends Denizen(DenizenId("69"), "Tutor", Suit.Arcane) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.tutor"),
    persistent = false, cost = Cost(favor = 1, secret = 1),
    text = "**ACTION:** Gain [secret]")
  val powers: Vector[PrintedPower] = Vector(power)

object DreamThiefCard extends Denizen(DenizenId("70"), "Dream Thief", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.dream-thief"),
    persistent = false, cost = Cost(favor = 2),
    text = "**ACTION:** Swap any two facedown advisers.")
  val powers: Vector[PrintedPower] = Vector(power)

object CrackingGroundCard extends Denizen(DenizenId("71"), "Cracking Ground", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.cracking-ground"),
    persistent = false, cost = Cost.free,
    text = "± [attack-die] per site targeted.")
  val powers: Vector[PrintedPower] = Vector(power)

object SealingWardCard extends Denizen(DenizenId("72"), "Sealing Ward", Suit.Arcane) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.sealing-ward"),
    persistent = true, cost = Cost.free,
    text = "Your relics add one more [defense-die] when targeted.")
  val powers: Vector[PrintedPower] = Vector(power)

object InitiationRiteCard extends Denizen(DenizenId("73"), "Initiation Rite", Suit.Arcane) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.initiation-rite"),
    persistent = false, cost = Cost.free,
    text = "To muster, you **must** place [secret] instead of [favor].")
  val powers: Vector[PrintedPower] = Vector(power)

object VowOfSilenceCard extends Denizen(DenizenId("74"), "Vow of Silence", Suit.Arcane) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-silence"),
    persistent = true, cost = Cost.free,
    text = "You **cannot** challenge the Darkest Secret. Whenever an " +
      "enemy burns [secret-burnt], you take those secrets.")
  val powers: Vector[PrintedPower] = Vector(power)

object ForgottenVaultCard extends Denizen(DenizenId("75"), "Forgotten Vault", Suit.Arcane) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.forgotten-vault"),
    persistent = true, cost = Cost.free,
    text = "Enemies of Forgotten Vault's ruler **cannot** target relics " +
      "held by its ruler. If ruled by Empire, all Imperials have " +
      "this power.")
  val powers: Vector[PrintedPower] = Vector(power)

object MapLibraryCard extends Denizen(DenizenId("76"), "Map Library", Suit.Arcane) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.map-library"),
    persistent = false, cost = Cost.free,
    text = "While your pawn is at this site, you may trade with a card " +
      "at any site in your region.")
  val powers: Vector[PrintedPower] = Vector(power)

object WitchsBargainCard extends Denizen(DenizenId("77"), "Witch's Bargain", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.witchs-bargain"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Give [secret] to a player whose pawn is at your " +
      "site to take [favor] [favor] from them, or give [favor] " +
      "[favor] for [secret], any number of times.")
  val powers: Vector[PrintedPower] = Vector(power)

object MasterOfDisguiseCard extends Denizen(DenizenId("78"), "Master of Disguise", Suit.Arcane) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.master-of-disguise"),
    persistent = false, cost = Cost(secret = 1),
    text = "Act as if you had another player's advisers instead. _(You " +
      "can't use your other advisers.)_")
  val powers: Vector[PrintedPower] = Vector(power)

object AlchemistCard extends Denizen(DenizenId("9"), "Alchemist", Suit.Arcane) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.alchemist"),
    persistent = false, cost = Cost(secret = 1, secretBurnt = 1),
    text = "**ACTION:** Gain [favor] [favor] [favor] [favor] from any " +
      "favor bank or banks.")
  val powers: Vector[PrintedPower] = Vector(power)
