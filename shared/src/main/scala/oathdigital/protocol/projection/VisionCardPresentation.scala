package oathdigital.protocol.projection

final case class VisionCardPresentation(name: String, rulesText: String)

object VisionCardPresentation {
  /** The gate every true Vision carries. The engine has always enforced it
    * (`VisionVictoryEligibility`); the text now says so.
    */
  private val gate =
    "and at least three visions have been drawn from the world deck."

  val byId: Map[String, VisionCardPresentation] = Vector(
    "vision:vision-of-conquest" -> VisionCardPresentation("Vision of Conquest",
      s"Wake: You win if you hold the **most sites** $gate"),
    "vision:vision-of-sanctuary" -> VisionCardPresentation("Vision of Sanctuary",
      s"Wake: You win if you hold the **most relics** $gate"),
    "vision:vision-of-rebellion" -> VisionCardPresentation("Vision of Rebellion",
      s"Wake: You win if you hold the **People's Favor** $gate"),
    "vision:vision-of-faith" -> VisionCardPresentation("Vision of Faith",
      s"Wake: You win if you hold the **Darkest Secret** $gate"),
    "vision:conspiracy" -> VisionCardPresentation("Conspiracy",
      "If this card is discarded in a raid campaign, return it to the box.\n\n" +
        "WHEN PLAYED: Take a relic or banner from a player whose pawn is at " +
        "your site. If you take a banner, adjust its [favor]/[secret] as " +
        "shown by its right ribbon. Return the Conspiracy to the box.")
  ).toMap
}
