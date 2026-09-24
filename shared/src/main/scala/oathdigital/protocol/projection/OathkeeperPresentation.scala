package oathdigital.protocol.projection

/** The Oath in play, as its goal card is printed: the title, what the
  * Oathkeeper holds it by, and the successor clause on the card's band.
  *
  * Beside [[VisionCardPresentation]] and for the same reason: `OathkeeperGoal`
  * carries a key, the catalog has no Oath section, and a title derived from
  * the key reads "Oath of The People".
  */
final case class OathkeeperPresentation(title: String, lines: Vector[String])

object OathkeeperPresentation {
  private def successor(clause: String): String =
    s"Successor to the Chancellor: $clause"

  val byGoal: Map[String, OathkeeperPresentation] = Map(
    "supremacy" -> OathkeeperPresentation("Oathkeeper of Supremacy",
      Vector("Rules the most sites", successor("Holds more relics"))),
    "protection" -> OathkeeperPresentation("Oathkeeper of Protection",
      Vector("Holds the most relics", successor("Holds the People's Favor"))),
    "devotion" -> OathkeeperPresentation("Oathkeeper of Devotion",
      Vector("Holds the Darkest Secret", successor("Holds the Grand Scepter"))),
    "the-people" -> OathkeeperPresentation("Oathkeeper of the People",
      Vector("Holds the People's Favor", successor("Holds the Darkest Secret"))))

  /** The printed title, or the raw key for a goal this map does not know --
    * the projection carries a string, so a key is never assumed to be one of
    * the four.
    */
  def title(goal: String): String = byGoal.get(goal).fold(goal)(_.title)
}
