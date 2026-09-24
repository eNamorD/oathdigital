package oathdigital.frontend

import org.scalajs.dom

/** The faces of the two dice, drawn as the symbols printed on them.
  *
  * The engine names faces with the same seven strings for both dice
  * (`WalkerDecisionProjector.defenseFaceName` and its attack twin), so one
  * renderer serves every panel that shows a roll. Five faces compose from
  * sprite glyphs; a blank face has nothing printed on it, and the doubler
  * multiplies rather than adding, so it says so in figures.
  */
private[frontend] object DieFace {
  private val words = Map(
    "hollow-sword" -> "hollow sword", "one-sword" -> "one sword",
    "two-swords-skull" -> "two swords and a skull", "blank" -> "blank",
    "one-shield" -> "one shield", "two-shields" -> "two shields",
    "doubler" -> "doubler")

  private val symbols = Map(
    "hollow-sword" -> Vector("hollow-sword"),
    "one-sword" -> Vector("sword"),
    "two-swords-skull" -> Vector("sword", "sword", "skull"),
    "one-shield" -> Vector("shield"),
    "two-shields" -> Vector("shield", "shield"),
    "blank" -> Vector.empty[String],
    "doubler" -> Vector.empty[String])

  def name(face: String): String = words.getOrElse(face, face)

  def face(value: String): dom.Element = {
    val node = ServerUiSupport.element("span", s"die-face die-face-$value")
    node.setAttribute("role", "img")
    node.setAttribute("aria-label", name(value))
    symbols.get(value) match {
      case Some(glyphs) =>
        glyphs.foreach(glyph => node.appendChild(RulesTextRenderer.glyph(glyph)))
        if (value == "doubler")
          node.appendChild(dom.document.createTextNode("×2"))
      // A face this client does not know is printed as itself rather than
      // dropped, the same way an unknown rules token stays literal.
      case None => node.appendChild(dom.document.createTextNode(value))
    }
    node
  }

  def roll(faces: Vector[String]): dom.Element = {
    val node = ServerUiSupport.element("span", "die-faces")
    node.setAttribute("aria-label", faces.map(name).mkString(", "))
    faces.foreach(value => node.appendChild(face(value)))
    node
  }
}
