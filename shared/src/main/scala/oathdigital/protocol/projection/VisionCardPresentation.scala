package oathdigital.protocol.projection

final case class VisionCardPresentation(name: String, rulesText: String)

object VisionCardPresentation {
  val byId: Map[String, VisionCardPresentation] = Vector(
    "vision:vision-of-conquest" -> VisionCardPresentation("Vision of Conquest",
      "Win if you uniquely rule the most sites, and rule at least one site."),
    "vision:vision-of-sanctuary" -> VisionCardPresentation("Vision of Sanctuary",
      "Win if you uniquely hold the most relics, and hold at least one relic."),
    "vision:vision-of-rebellion" -> VisionCardPresentation("Vision of Rebellion",
      "Win if you hold the People's Favor."),
    "vision:vision-of-faith" -> VisionCardPresentation("Vision of Faith",
      "Win if you hold the Darkest Secret."),
    "vision:conspiracy" -> VisionCardPresentation("Conspiracy",
      "This is not a true Vision. Play it to take a relic or banner from a player " +
        "whose pawn is at your site, then return Conspiracy to the box.")
  ).toMap
}
