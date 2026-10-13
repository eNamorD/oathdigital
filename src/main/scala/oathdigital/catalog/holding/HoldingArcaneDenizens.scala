package oathdigital.catalog.holding

import oathdigital.catalog.{AdviserOnly, Denizen, PrintedPower}
import oathdigital.model.{Cost, DenizenId, PowerId, Suit}

// Generated from the retired runtime JSON (card classes slice 1).
// Hand-edited from here on; a card moves out when it is implemented.

object CouncilArbiterCard extends Denizen(DenizenId("200"), "Council Arbiter", Suit.Arcane) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.council-arbiter"),
    persistent = false, cost = Cost.free,
    text = "**WHEN NEGOTIATING:** You may give, receive, and swap " +
      "advisers. _You cannot exchange locked advisers. You still " +
      "follow your adviser limit._")
  val powers: Vector[PrintedPower] = Vector(power)

object GlamorCard extends Denizen(DenizenId("207"), "Glamor", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.glamor"),
    persistent = false, cost = Cost.free,
    text = "You may also target [secret] on your enemy's board except " +
      "their last. Add [defense-die] for each targeted. If you're " +
      "victorious, take them.")
  val powers: Vector[PrintedPower] = Vector(power)

object MagiciansCodeCard extends Denizen(DenizenId("32"), "Magician's Code", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.magicians-code"),
    persistent = false, cost = Cost(favor = 2),
    text = "If you're challenging the Darkest Secret, gain [secret] " +
      "[secret] and place them on the banner along with any other " +
      "[secret] you're placing _(even none)_.")
  val powers: Vector[PrintedPower] = Vector(power)

object ActingTroupeCard extends Denizen(DenizenId("36"), "Acting Troupe", Suit.Arcane) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.acting-troupe"),
    persistent = false, cost = Cost.free,
    text = "Act as if Acting Troupe is a [suit-beast] or [suit-order] " +
      "card instead.")
  val powers: Vector[PrintedPower] = Vector(power)

object SecretSignalCard extends Denizen(DenizenId("55"), "Secret Signal", Suit.Arcane) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.secret-signal"),
    persistent = false, cost = Cost.free,
    text = "If you gain only one [favor], gain one more [favor].")
  val powers: Vector[PrintedPower] = Vector(power)

object BewitchCard extends Denizen(DenizenId("67"), "Bewitch", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.bewitch"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** if you're an Exile and have the most " +
      "[secret] _(even on cards)_, you may take a random relic " +
      "from the Reliquary and become a Citizen. _Follow the steps " +
      "in the rules and end your Act Phase._")
  val powers: Vector[PrintedPower] = Vector(power)

object MasterOfDisguiseCard extends Denizen(DenizenId("78"), "Master of Disguise", Suit.Arcane) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.master-of-disguise"),
    persistent = false, cost = Cost(secret = 1),
    text = "Act as if you had another player's advisers instead. _(You " +
      "can't use your other advisers.)_")
  val powers: Vector[PrintedPower] = Vector(power)
