package oathdigital.catalog.holding

import oathdigital.catalog.{Edifice, EdificeFace, Locked, PrintedPower}
import oathdigital.model.{Cost, EdificeId, PowerId, Suit}

// Generated from the retired runtime JSON (card classes slice 1).
// Hand-edited from here on; a card moves out when it is implemented.

object HallOfDebateCard extends Edifice(EdificeId("E01"), Suit.Discord):
  object intact extends EdificeFace("Hall of Debate") with Locked:
    val power = PrintedPower(PowerId("edifice.e01.intact"),
      persistent = true, cost = Cost.free,
      text = "If this card's ruler holds the People's Favor, their " +
        "enemies **cannot** target the People's Favor. If ruled by " +
        "Empire, all Imperials have this power.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Hall of Mockery"):
    val power = PrintedPower(PowerId("edifice.e01.ruined"),
      persistent = false, cost = Cost.free,
      text = "**SETUP (END) / WHEN EXPLORED:** Place [favor] [favor] " +
        "[favor] [favor] on the People's Favor and move it to the " +
        "shared bank—-players **cannot** start with it. Burn " +
        "[favor-burnt] from each favor bank.")
    val powers: Vector[PrintedPower] = Vector(power)

object GreatMarketCard extends Edifice(EdificeId("E02"), Suit.Discord):
  object intact extends EdificeFace("Great Market") with Locked:
    val power = PrintedPower(PowerId("edifice.e02.intact"),
      persistent = false, cost = Cost.free,
      text = "**SETUP (END) / WHEN EXPLORED:** Place [favor] on this site " +
        "for each denizen _(including this)_ in this region. " +
        "_Players may take it in Wake._")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Bandit Market"):
    val power = PrintedPower(PowerId("edifice.e02.ruined"),
      persistent = false, cost = Cost.free,
      text = "**SETUP (END) / WHEN EXPLORED:** Place [favor] on each site " +
        "ruled by the bandits. _(Players may take it in Wake.)_ Burn " +
        "[favor-burnt] from each favor bank.")
    val powers: Vector[PrintedPower] = Vector(power)

object GrandCanalCard extends Edifice(EdificeId("E03"), Suit.Discord):
  object intact extends EdificeFace("Grand Canal") with Locked:
    val power = PrintedPower(PowerId("edifice.e03.intact"),
      persistent = true, cost = Cost.free,
      text = "Act as if this site is a Coast along with its other powers.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Lost Lagoon"):
    val power = PrintedPower(PowerId("edifice.e03.ruined"),
      persistent = true, cost = Cost.free,
      text = "Act as if this site is an Island along with its other " +
        "powers.")
    val powers: Vector[PrintedPower] = Vector(power)

object StoneBathsCard extends Edifice(EdificeId("E04"), Suit.Discord):
  object intact extends EdificeFace("Stone Baths") with Locked:
    val power = PrintedPower(PowerId("edifice.e04.intact"),
      persistent = true, cost = Cost.free,
      text = "CHRONICLE (SUN): Players with a pawn at this site do not " +
        "discard their dormant legacies.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Ruined Baths"):
    val power = PrintedPower(PowerId("edifice.e04.ruined"),
      persistent = true, cost = Cost.free,
      text = "CHRONICLE (SUN): Exiles at this site don't need to meet the " +
        "goals of their active legacies to keep them active.")
    val powers: Vector[PrintedPower] = Vector(power)

object AmberDoorsCard extends Edifice(EdificeId("E05"), Suit.Discord):
  object intact extends EdificeFace("Amber Doors") with Locked:
    val power = PrintedPower(PowerId("edifice.e05.intact"),
      persistent = false, cost = Cost.free,
      text = "SETUP / **WHEN EXPLORED:** If you place your pawn here, you " +
        "may discard all of your legacies (even dormant) to draw 2 " +
        "dormant legacies and activate one.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Lost Doors"):
    val power = PrintedPower(PowerId("edifice.e05.ruined"),
      persistent = false, cost = Cost.free,
      text = "SETUP / **WHEN EXPLORED:** If you place your pawn here, " +
        "draw a dormant legacy, then discard one you hold.")
    val powers: Vector[PrintedPower] = Vector(power)

object GreatForgeCard extends Edifice(EdificeId("E06"), Suit.Nomad):
  object intact extends EdificeFace("Great Forge") with Locked:
    val power = PrintedPower(PowerId("edifice.e06.intact"),
      persistent = false, cost = Cost.free,
      text = "SETUP / **WHEN EXPLORED:** If you place your pawn here, " +
        "draw a relic from the relic deck and take it facedown.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Broken Forge"):
    val power = PrintedPower(PowerId("edifice.e06.ruined"),
      persistent = false, cost = Cost.free,
      text = "SETUP / **WHEN EXPLORED:** If you place your pawn here, " +
        "discard all relics at sites in this region.")
    val powers: Vector[PrintedPower] = Vector(power)

object WildPasturesCard extends Edifice(EdificeId("E07"), Suit.Nomad):
  object intact extends EdificeFace("Wild Pastures") with Locked:
    val power = PrintedPower(PowerId("edifice.e07.intact"),
      persistent = true, cost = Cost.free,
      text = "Whenever this card's ruler would discard a [suit-nomad] " +
        "card from a power, they may instead pay [favor] to the " +
        "[suit-nomad] bank.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Blasted Pastures"):
    val power = PrintedPower(PowerId("edifice.e07.ruined"),
      persistent = true, cost = Cost.free,
      text = "**CHRONICLE (START OF THRONE):** Bury all [suit-nomad] " +
        "cards on the map unless the winner rules this card.")
    val powers: Vector[PrintedPower] = Vector(power)

object SacredGroundCard extends Edifice(EdificeId("E08"), Suit.Nomad):
  object intact extends EdificeFace("Sacred Ground") with Locked:
    val power = PrintedPower(PowerId("edifice.e08.intact"),
      persistent = true, cost = Cost.free,
      text = "Players **cannot** play Visions, except the Conspiracy, " +
        "faceup unless their pawn is at this site.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Desecrated Ground"):
    val power = PrintedPower(PowerId("edifice.e08.ruined"),
      persistent = true, cost = Cost.free,
      text = "If an Exile reveals a Vision while their pawn is at this " +
        "site, they do not discard their current revealed Vision. " +
        "_They can have any number!_")
    val powers: Vector[PrintedPower] = Vector(power)

object GiantsPathCard extends Edifice(EdificeId("E09"), Suit.Nomad):
  object intact extends EdificeFace("Giant's Path") with Locked:
    val power = PrintedPower(PowerId("edifice.e09.intact"),
      persistent = false, cost = Cost(secret = 1),
      text = "**ACTION:** Move this card to another site, then place your " +
        "pawn there.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Giant's Rampage"):
    val power = PrintedPower(PowerId("edifice.e09.ruined"),
      persistent = false, cost = Cost(favor = 1, secret = 2),
      text = "**ACTION:** Bury a denizen at any site _(even here)_. If " +
        "you do, move Giant's Rampage to that site and place your " +
        "pawn there.")
    val powers: Vector[PrintedPower] = Vector(power)

object TempleOfAncestorsCard extends Edifice(EdificeId("E10"), Suit.Nomad):
  object intact extends EdificeFace("Temple of Ancestors") with Locked:
    val power = PrintedPower(PowerId("edifice.e10.intact"),
      persistent = false, cost = Cost(favor = 1),
      text = "**ACTION:** Draw a card from the bottom of the world deck " +
        "and play it to your advisers or discard it, as if you " +
        "searched.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Ruined Temple"):
    val power = PrintedPower(PowerId("edifice.e10.ruined"),
      persistent = false, cost = Cost.free,
      text = "**SETUP / WHEN EXPLORED:** If you place your pawn here, you " +
        "may burn [secret-burnt]. If you do and then win the game, " +
        "score double the legacy points and take rule of all bandit " +
        "sites.")
    val powers: Vector[PrintedPower] = Vector(power)

object GreatSpireCard extends Edifice(EdificeId("E11"), Suit.Arcane):
  object intact extends EdificeFace("Great Spire") with Locked:
    val power = PrintedPower(PowerId("edifice.e11.intact"),
      persistent = false, cost = Cost(secret = 1),
      text = "**ACTION:** Add 3 random cards from the Dispossessed to " +
        "this region's discard pile, then add the bottom 3 denizens " +
        "from that pile to the Dispossessed.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Fallen Spire"):
    val power = PrintedPower(PowerId("edifice.e11.ruined"),
      persistent = false, cost = Cost.free,
      text = "SETUP (END) / **WHEN EXPLORED:** Swap denizens in this " +
        "region's discard pile with the same number of random cards " +
        "from the Dispossessed. Then, shuffle these cards with any " +
        "Visions in this pile.")
    val powers: Vector[PrintedPower] = Vector(power)

object GreatClockCard extends Edifice(EdificeId("E12"), Suit.Arcane):
  object intact extends EdificeFace("Great Clock") with Locked:
    val power = PrintedPower(PowerId("edifice.e12.intact"),
      persistent = false, cost = Cost(favor = 1, secret = 2, secretBurnt = 1),
      text = "**ACTION:** Advance the round marker once, if possible. _Do " +
        "not roll [round-die]. This cannot end the game._")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Broken Clock"):
    val power = PrintedPower(PowerId("edifice.e12.ruined"),
      persistent = false, cost = Cost.free,
      text = "**SETUP (END) / WHEN EXPLORED:** Advance the round marker " +
        "once, if possible. _Do not roll [round-die]. This cannot " +
        "end the game._")
    val powers: Vector[PrintedPower] = Vector(power)

object UndergroundLibraryCard extends Edifice(EdificeId("E13"), Suit.Arcane):
  object intact extends EdificeFace("Underground Library") with Locked:
    val power = PrintedPower(PowerId("edifice.e13.intact"),
      persistent = false, cost = Cost(favor = 1),
      text = "**ACTION:** Peek at all relics in this region, then swap " +
        "one with a relic you have, placing it facedown.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Drowned Library"):
    val power = PrintedPower(PowerId("edifice.e13.ruined"),
      persistent = false, cost = Cost(secret = 1),
      text = "If your pawn is at this site, you may recover the top card " +
        "of the relic deck instead. Its recover difficulty is 5.")
    val powers: Vector[PrintedPower] = Vector(power)

object StonePortalCard extends Edifice(EdificeId("E14"), Suit.Arcane):
  object intact extends EdificeFace("Stone Portal") with Locked:
    val power = PrintedPower(PowerId("edifice.e14.intact"),
      persistent = false, cost = Cost(secret = 1),
      text = "**ACTION:** If your pawn is at this site, place your pawn " +
        "on another site. If your pawn is not at this site, place " +
        "your pawn at this site.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Shattered Portal"):
    val power = PrintedPower(PowerId("edifice.e14.ruined"),
      persistent = false, cost = Cost(secret = 1),
      text = "**ACTION:** If your pawn is at this site, roll [round-die] " +
        "and place your pawn that many sites Hinterward. The bottom " +
        "Hinterland site wraps to the top Cradle site.")
    val powers: Vector[PrintedPower] = Vector(power)

object MarbleFountainsCard extends Edifice(EdificeId("E15"), Suit.Arcane):
  object intact extends EdificeFace("Marble Fountains") with Locked:
    val power = PrintedPower(PowerId("edifice.e15.intact"),
      persistent = false, cost = Cost.free,
      text = "**WAKE:** If your pawn is at this site, refresh your Supply " +
        "marker to the leftmost space.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Murky Fountain"):
    val power = PrintedPower(PowerId("edifice.e15.ruined"),
      persistent = false, cost = Cost(secret = 1),
      text = "**ACTION:** If your pawn is at this site, roll 2 " +
        "[defense-die] and gain Supply equal to the total [shield] " +
        "rolled. If you roll none, end your Act Phase.")
    val powers: Vector[PrintedPower] = Vector(power)

object HallOfMinistersCard extends Edifice(EdificeId("E16"), Suit.Order):
  object intact extends EdificeFace("Hall of Ministers") with Locked:
    val power = PrintedPower(PowerId("edifice.e16.intact"),
      persistent = true, cost = Cost.free,
      text = "Enemies of this card's ruler **cannot** discard cards from " +
        "sites ruled by this card's ruler. _They can still be " +
        "buried._")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Hall of Bandits"):
    val power = PrintedPower(PowerId("edifice.e16.ruined"),
      persistent = true, cost = Cost.free,
      text = "**CHRONICLE:** Ruined edifices ruled by bandits are not " +
        "discarded.")
    val powers: Vector[PrintedPower] = Vector(power)

object RelicPatrolsCard extends Edifice(EdificeId("E17"), Suit.Order):
  object intact extends EdificeFace("Relic Patrols") with Locked:
    val power = PrintedPower(PowerId("edifice.e17.intact"),
      persistent = true, cost = Cost.free,
      text = "Enemies of this card's ruler **cannot** recover relics at " +
        "sites ruled by this card's ruler.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Bandit Patrols"):
    val power = PrintedPower(PowerId("edifice.e17.ruined"),
      persistent = true, cost = Cost.free,
      text = "Enemies of this card's ruler **cannot** recover relics at " +
        "sites ruled by bandits.")
    val powers: Vector[PrintedPower] = Vector(power)

object TomeGuardiansCard extends Edifice(EdificeId("E18"), Suit.Order):
  object intact extends EdificeFace("Tome Guardians") with Locked:
    val power = PrintedPower(PowerId("edifice.e18.intact"),
      persistent = true, cost = Cost.free,
      text = "If this card's ruler holds the Darkest Secret, their " +
        "enemies **cannot** target the Darkest Secret. If ruled by " +
        "Empire, all Imperials have this power.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Lost Guardians"):
    val power = PrintedPower(PowerId("edifice.e18.ruined"),
      persistent = false, cost = Cost.free,
      text = "**SETUP (END) / WHEN EXPLORED:** Place [secret] [secret] " +
        "[secret] on the Darkest Secret and move it to the shared " +
        "bank. Players **cannot** start with it.")
    val powers: Vector[PrintedPower] = Vector(power)

object TheTribunalCard extends Edifice(EdificeId("E19"), Suit.Order):
  object intact extends EdificeFace("The Tribunal") with Locked:
    val power = PrintedPower(PowerId("edifice.e19.intact"),
      persistent = false, cost = Cost.free,
      text = "**WHEN NEGOTIATING:** You may negotiate with players at any " +
        "sites. Players may make binding promises, even into later " +
        "games.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Bandit Tribunal"):
    val power = PrintedPower(PowerId("edifice.e19.ruined"),
      persistent = true, cost = Cost.free,
      text = "**WHEN NEGOTIATING:** Players at sites ruled by bandits may " +
        "negotiate with each other.")
    val powers: Vector[PrintedPower] = Vector(power)

object ToweringRampartCard extends Edifice(EdificeId("E20"), Suit.Order):
  object intact extends EdificeFace("Towering Rampart") with Locked:
    val power = PrintedPower(PowerId("edifice.e20.intact"),
      persistent = false, cost = Cost.free,
      text = "+2 [defense-die] if your pawn is at this site or this site " +
        "is targeted.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Cracked Rampart"):
    val power = PrintedPower(PowerId("edifice.e20.ruined"),
      persistent = false, cost = Cost.free,
      text = "+1 [defense-die] if this site is targeted.")
    val powers: Vector[PrintedPower] = Vector(power)

object FestivalDistrictCard extends Edifice(EdificeId("E21"), Suit.Hearth):
  object intact extends EdificeFace("Festival District") with Locked:
    val power = PrintedPower(PowerId("edifice.e21.intact"),
      persistent = false, cost = Cost.free,
      text = "**WHEN NEGOTIATING:** Players may give, receive, and swap " +
        "advisers. _Players must follow their adviser limits._")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Squalid District"):
    val power = PrintedPower(PowerId("edifice.e21.ruined"),
      persistent = false, cost = Cost(favor = 1, favorBurnt = 1),
      text = "**ACTION:** Discard any number of facedown advisers you " +
        "have. Draw that many denizens from the bottom of the world " +
        "deck as facedown advisers.")
    val powers: Vector[PrintedPower] = Vector(power)

object ProvingGroundsCard extends Edifice(EdificeId("E22"), Suit.Hearth):
  object intact extends EdificeFace("Proving Grounds") with Locked:
    val power = PrintedPower(PowerId("edifice.e22.intact"),
      persistent = false, cost = Cost.free,
      text = "SETUP / **WHEN EXPLORED:** If you place your pawn here, " +
        "gain three warbands.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Empty Grounds"):
    val power = PrintedPower(PowerId("edifice.e22.ruined"),
      persistent = false, cost = Cost.free,
      text = "SETUP (END) / **WHEN EXPLORED:** Discard all other denizens " +
        "in this region.")
    val powers: Vector[PrintedPower] = Vector(power)

object SpiralCastleCard extends Edifice(EdificeId("E23"), Suit.Hearth):
  object intact extends EdificeFace("Spiral Castle") with Locked:
    val power = PrintedPower(PowerId("edifice.e23.intact"),
      persistent = false, cost = Cost.free,
      text = "If you're victorious in a raid, place the defender's pawn " +
        "on this site, and they lose 2 Supply.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Spiral Dungeons"):
    val power = PrintedPower(PowerId("edifice.e23.ruined"),
      persistent = false, cost = Cost.free,
      text = "If you're victorious, place the attacker's pawn on this " +
        "site, and they lose 2 Supply. _Bandits will use this._")
    val powers: Vector[PrintedPower] = Vector(power)

object BoilingLakeCard extends Edifice(EdificeId("E24"), Suit.Hearth):
  object intact extends EdificeFace("Boiling Lake") with Locked:
    val power = PrintedPower(PowerId("edifice.e24.intact"),
      persistent = false, cost = Cost.free,
      text = "If you travel to this site and do not rule this card, you " +
        "**must** kill 2 warbands on your board if able.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Still Boiling Lake"):
    val power = PrintedPower(PowerId("edifice.e24.ruined"),
      persistent = true, cost = Cost.free,
      text = "Cards **cannot** be played to this site. _This card can " +
        "still be discarded by People's Favor, etc._")
    val powers: Vector[PrintedPower] = Vector(power)

object HiddenPassagesCard extends Edifice(EdificeId("E25"), Suit.Hearth):
  object intact extends EdificeFace("Hidden Passages") with Locked:
    val power = PrintedPower(PowerId("edifice.e25.intact"),
      persistent = false, cost = Cost.free,
      text = "**WAKE:** If your pawn is at this site and its ruler " +
        "consents, place your pawn on any site.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Sinking Passages"):
    val power = PrintedPower(PowerId("edifice.e25.ruined"),
      persistent = false, cost = Cost.free,
      text = "**WAKE:** If your pawn is at this site, you may burn " +
        "[favor-burnt] to place your pawn on any site.")
    val powers: Vector[PrintedPower] = Vector(power)

object HallowedSpringCard extends Edifice(EdificeId("E26"), Suit.Beast):
  object intact extends EdificeFace("Hallowed Spring") with Locked:
    val power = PrintedPower(PowerId("edifice.e26.intact"),
      persistent = true, cost = Cost.free,
      text = "When trading at this site for [secret], you **must** place " +
        "both [favor] on the card you are trading with _(instead of " +
        "burning one)_.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Hiding Place"):
    val power = PrintedPower(PowerId("edifice.e26.ruined"),
      persistent = true, cost = Cost.free,
      text = "CHRONICLE (THRONE): This site's ruler may secretly keep a " +
        "relic they have facedown. They start the next game with it.")
    val powers: Vector[PrintedPower] = Vector(power)

object SchoolOfVinesCard extends Edifice(EdificeId("E27"), Suit.Beast):
  object intact extends EdificeFace("School of Vines") with Locked:
    val power = PrintedPower(PowerId("edifice.e27.intact"),
      persistent = false, cost = Cost.free,
      text = "You may challenge a banner held by an enemy even if your " +
        "pawn is not at their pawn's site.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Ruined School"):
    val power = PrintedPower(PowerId("edifice.e27.ruined"),
      persistent = false, cost = Cost.free,
      text = "**SETUP (END) / WHEN EXPLORED:** Place [secret] on each " +
        "site ruled by the bandits. _Players may take it in Wake._")
    val powers: Vector[PrintedPower] = Vector(power)

object OakenFortressCard extends Edifice(EdificeId("E28"), Suit.Beast):
  object intact extends EdificeFace("Oaken Fortress") with Locked:
    val power = PrintedPower(PowerId("edifice.e28.intact"),
      persistent = true, cost = Cost.free,
      text = "While this card's ruler is at this site, they **cannot** be " +
        "targeted by a challenge or a raid campaign. If ruled by " +
        "Empire, all Imperials have this power.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Rotting Fortress"):
    val power = PrintedPower(PowerId("edifice.e28.ruined"),
      persistent = true, cost = Cost.free,
      text = "Players at this site **cannot** be targeted by a challenge " +
        "or a raid campaign, unless the targeting player has a " +
        "[suit-beast] adviser.")
    val powers: Vector[PrintedPower] = Vector(power)

object ForbiddenForestCard extends Edifice(EdificeId("E29"), Suit.Beast):
  object intact extends EdificeFace("Forbidden Forest") with Locked:
    val power = PrintedPower(PowerId("edifice.e29.intact"),
      persistent = true, cost = Cost.free,
      text = "Enemies of this card's ruler **cannot** travel to this site " +
        "unless they have a [suit-beast] adviser.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Bandit Forest"):
    val power = PrintedPower(PowerId("edifice.e29.ruined"),
      persistent = true, cost = Cost.free,
      text = "Imperials **cannot** place [favor]/[secret] on cards ruled " +
        "by bandits or use their non-persistent powers.")
    val powers: Vector[PrintedPower] = Vector(power)

object ForestHornCard extends Edifice(EdificeId("E30"), Suit.Beast):
  object intact extends EdificeFace("Forest Horn") with Locked:
    val power = PrintedPower(PowerId("edifice.e30.intact"),
      persistent = false, cost = Cost(secret = 2),
      text = "**ACTION:** Choose a region. Discard all non-[suit-beast] " +
        "denizens at sites in that region.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Broken Horn"):
    val power = PrintedPower(PowerId("edifice.e30.ruined"),
      persistent = false, cost = Cost.free,
      text = "SETUP (END) / **WHEN EXPLORED:** Bury this region's discard " +
        "pile.")
    val powers: Vector[PrintedPower] = Vector(power)
