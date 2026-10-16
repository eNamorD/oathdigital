package oathdigital.frontend

import org.scalajs.dom
import oathdigital.protocol.projection.{Die, DieFace}

/** The faces of the two dice, drawn as the symbols printed on them.
  *
  * One renderer serves every panel that shows a roll. Five faces compose
  * from sprite glyphs; a blank face has nothing printed on it, and the
  * doubler multiplies rather than adding, so it says so in figures.
  *
  * Named `DieFaceChip`, not `DieFace`: the face itself is
  * `oathdigital.protocol.projection.DieFace`, and this is what the page
  * draws for one. The words a face is read by are that enum's; this object
  * keeps only the glyph composition, which is the client's own drawing
  * concern and has no server twin.
  */
private[frontend] object DieFaceChip:
  private val symbols = Map(
    "hollow-sword" -> Vector("hollow-sword"),
    "one-sword" -> Vector("sword"),
    "two-swords-skull" -> Vector("sword", "sword", "skull"),
    "one-shield" -> Vector("shield"),
    "two-shields" -> Vector("shield", "shield"),
    "blank" -> Vector.empty[String],
    "doubler" -> Vector.empty[String])

  def name(key: String): String =
    DieFace.values.find(_.key == key).fold(key)(_.word)

  def face(key: String, die: Option[Die] = None): dom.Element =
    val classes = Vector("die-face", s"die-face-$key") ++
      die.map(kind => s"die-face-${kind.key}")
    val node = ServerUiSupport.element("span", classes.mkString(" "))
    node.setAttribute("role", "img")
    node.setAttribute("aria-label", name(key))
    symbols.get(key) match
      case Some(glyphs) =>
        glyphs.foreach(glyph => node.appendChild(RulesTextRenderer.glyph(glyph)))
        if key == "doubler" then
          node.appendChild(dom.document.createTextNode("×2"))
      // A face this client does not know is printed as itself rather than
      // dropped, the same way an unknown rules token stays literal. Only the
      // decision wire can still send one; the log's faces are typed.
      case None => node.appendChild(dom.document.createTextNode(key))
    node

  /** The log's roll: every face typed, so the die comes off the faces. */
  def roll(faces: Vector[DieFace]): dom.Element =
    row(faces.map(_.key), faces.headOption.map(_.die))

  /** The decision wire's roll. Its faces are still keys
    * (`WalkerDecisionProjector.faceName`), held to `DieFace` by a test in
    * `WalkerDecisionProjectorSuite`. */
  def rollKeys(keys: Vector[String], die: Option[Die]): dom.Element =
    row(keys, die)

  private def row(keys: Vector[String], die: Option[Die]): dom.Element =
    val node = ServerUiSupport.element("span", "die-faces")
    node.setAttribute("aria-label", keys.map(name).mkString(", "))
    keys.foreach(key => node.appendChild(face(key, die)))
    node
