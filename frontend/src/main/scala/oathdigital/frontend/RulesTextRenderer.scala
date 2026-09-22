package oathdigital.frontend

import org.scalajs.dom

/** Renders catalog `rulesText` markup instead of printing it.
  *
  * `CatalogModel.rulesText` joins a card's powers with a blank line before the
  * projection is built, so splitting on that boundary is what recovers the
  * per-power blocks. Six cards in the catalog have more than one power.
  */
private[frontend] object RulesTextRenderer {
  private val Svg = "http://www.w3.org/2000/svg"
  private val Token = """\[([a-z0-9-]+)\]""".r
  // Lookbehind needs ES2018, which this build's JS target does not enable, so
  // the left word-boundary check for `_italic_` is done by hand below instead
  // of `(?<![A-Za-z0-9_])`.
  private val Emphasis = """\*\*(.+?)\*\*|_(.+?)_(?![A-Za-z0-9_])""".r

  private def isWordChar(value: Char): Boolean = value.isLetterOrDigit || value == '_'

  def glyph(token: String): dom.Element = {
    val node = dom.document.createElementNS(Svg, "svg")
    node.setAttribute("class", s"token-glyph token-$token")
    node.setAttribute("role", "img")
    node.setAttribute("aria-label", TokenSprite.label(token))
    val use = dom.document.createElementNS(Svg, "use")
    use.setAttribute("href", s"#token-$token")
    node.appendChild(use)
    node
  }

  def powers(rulesText: String): Vector[dom.Element] =
    rulesText.split("\n\\s*\n").toVector.map(_.trim).filter(_.nonEmpty)
      .map { block =>
        val node = ServerUiSupport.element("p", "rules-power")
        emphasise(block, node)
        node
      }

  /** `**bold**` and `_italic_` wrap runs that may themselves hold tokens. */
  private def emphasise(value: String, into: dom.Element): Unit = {
    var cursor = 0
    Emphasis.findAllMatchIn(value).foreach { found =>
      val isBold = found.group(1) != null
      val validBoundary = isBold || found.start == 0 ||
        !isWordChar(value.charAt(found.start - 1))
      if (validBoundary) {
        tokenise(value.substring(cursor, found.start), into)
        val inner = if (isBold) found.group(1) else found.group(2)
        val wrapper = ServerUiSupport.element(if (isBold) "strong" else "em", "")
        tokenise(inner, wrapper)
        into.appendChild(wrapper)
        cursor = found.end
      }
    }
    tokenise(value.substring(cursor), into)
  }

  /** A bracketed word that is not one of the seventeen stays literal text --
    * silently dropping it would hide a catalog typo instead of showing it.
    */
  private def tokenise(value: String, into: dom.Element): Unit = {
    var cursor = 0
    Token.findAllMatchIn(value).foreach { found =>
      if (TokenSprite.isToken(found.group(1))) {
        append(value.substring(cursor, found.start), into)
        into.appendChild(glyph(found.group(1)))
        cursor = found.end
      }
    }
    append(value.substring(cursor), into)
  }

  private def append(value: String, into: dom.Element): Unit =
    if (value.nonEmpty) into.appendChild(dom.document.createTextNode(value))
}
