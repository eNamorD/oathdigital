package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport._

/** The one place a projected `CardDetails` becomes DOM.
  *
  * Every card occupies its type's box -- denizen 1:1.4, relic 1:1 -- in both
  * states, so revealing or covering a card can never reflow the pane around
  * it. Full rules live in the inspection overlay, not here: a summary fits a
  * small card and rules do not (measured p90 is 134 glyphs for a denizen).
  */
private[frontend] object CardFace:

  /** Edifices share the denizen box because they occupy denizen slots, and a
    * differently shaped edifice would break the slot grid the moment one is
    * built. Visions share it rather than earn a ratio used by one case.
    */
  def boxClass(cardKind: String): String =
    if cardKind == "relic" then "card-face-relic" else "card-face-denizen"

  /** Safe on a card the viewer cannot identify: a denizen back and a vision
    * back differ physically, so which one this is stays public even when the
    * card's identity does not. `GamePresentationProjector` projects the real
    * kind for an unidentifiable adviser (commit `7ae54c3`).
    */
  def backLetter(cardKind: String): String = cardKind match
    case "vision" => "V"
    case "relic" => "R"
    case _ => "D"

  def faceDown(card: CardDetails): Boolean =
    card.hidden || card.orientation.contains("face-down")

  /** Word lengths a zoomed-out map card can show whole on one line, in
    * characters. A card carries one `name-fits-N` class per bucket its
    * longest word clears; the map carries the one `map-fit-N` class its
    * scale allows (`GameTableShell.nameFit`), and the stylesheet shows the
    * whole name where the two meet and the initials everywhere else.
    */
  val NameFitBuckets: Vector[Int] = Vector(6, 8, 10, 12)

  private def words(name: String): Vector[String] =
    name.split("[\\s-]+").toVector.filter(_.nonEmpty)

  /** The name as a card shows it when the map is too small for the whole
    * word: the first character of each word, or a lone word's first two.
    * Case is the catalog's own, so "Master of Disguise" reads MoD.
    */
  def initials(name: String): String = words(name) match
    case Vector(only) => only.take(2)
    case many => many.map(_.take(1)).mkString

  def nameFitClasses(name: String): Vector[String] =
    val longest = words(name).map(_.length).maxOption.getOrElse(0)
    NameFitBuckets.filter(longest <= _).map(bucket => s"name-fits-$bucket")

  def render(card: CardDetails): dom.html.Button =
    val node = dom.document.createElement("button").asInstanceOf[dom.html.Button]
    node.`type` = "button"
    node.className = (Vector("card-face", boxClass(card.cardKind),
      if faceDown(card) then "card-face-down" else "",
      if faceDown(card) && !card.hidden then "card-face-knowable" else "",
      if card.implemented then "" else "card-face-unimplemented") ++
      (if card.hidden then Vector.empty else nameFitClasses(card.name)))
      .filter(_.nonEmpty).mkString(" ")
    node.setAttribute("data-card-id", card.cardId)
    node.setAttribute("aria-label",
      if card.hidden then card.name
      else if card.implemented then card.name
      else s"${card.name} (unimplemented)")
    // The whole name on hover, for the zoomed-out face that shows initials.
    if !card.hidden then node.setAttribute("title", card.name)

    if card.hidden then
      node.appendChild(text("span", "card-back-letter", backLetter(card.cardKind)))
    else if faceDown(card) then
      // Knowable: the back is what shows at rest; the summary is revealed by
      // CSS on hover only, inside the same box, so nothing moves.
      node.appendChild(text("span", "card-back-letter", backLetter(card.cardKind)))
      val pip = element("span", "card-knowable-pip")
      pip.setAttribute("aria-hidden", "true")
      node.appendChild(pip)
      val peek = element("span", "card-hover-summary")
      summary(card).foreach(peek.appendChild)
      node.appendChild(peek)
    else
      summary(card).foreach(node.appendChild)

    // A click means inspect, in every context. Decision panels select by
    // drag, by the move-option buttons, or by the plus/minus steppers, never
    // by clicking a card. The one clickable ancestor a card sits in, a site
    // box on the board, ignores events that came from a card
    // (`WorldBoardRenderer.onCard`); the card itself does not stop them, so
    // an ancestor that wants to know a card was clicked still can.
    node.onclick = _ => CardInspection.open(card, node)
    node

  /** Priority order, dropped from the bottom as the box shrinks: the header
    * (suit glyph and name), live tokens, relic value and defense,
    * restriction, unimplemented. The suit rides the header as a glyph rather
    * than a word below the name: it costs no line and colour plus shape says
    * it faster than reading does.
    */
  private def summary(card: CardDetails): Vector[dom.Element] =
    val header = element("span", "card-header")
    card.suit.foreach(value =>
      header.appendChild(RulesTextRenderer.glyph(s"suit-$value")))
    header.appendChild(text("span", "card-name", card.name))
    // Both forms are in the DOM and the map's scale picks one, so a zoom
    // never re-renders a card. The short form is decoration for the eye:
    // the button's label already carries the name.
    val short = text("span", "card-name-short", initials(card.name))
    short.setAttribute("aria-hidden", "true")
    header.appendChild(short)
    val tokens = element("span", "card-tokens")
    if card.favor > 0 then counted("favor", card.favor).foreach(tokens.appendChild)
    if card.secrets > 0 then counted("secret", card.secrets).foreach(tokens.appendChild)
    // Defense is rolled against, so it is drawn as the dice that roll it. It
    // rides the header's right end rather than floating over the corner: a
    // name long enough to wrap would otherwise run under the dice. The
    // printed relic value it used to share a line with is a catalog number,
    // and lives in the overlay.
    card.defense.filter(_ > 0).foreach { value =>
      val node = element("span", "card-defense")
      node.setAttribute("aria-label", s"defense $value")
      (0 until value).foreach(_ =>
        node.appendChild(RulesTextRenderer.glyph("defense-die")))
      header.appendChild(node)
    }
    Vector(Some(header),
      Option.when(tokens.childNodes.length > 0)(tokens),
      // Unrestricted is the default every card carries, so printing it spends
      // a line of a small face on nothing.
      card.restrictions.filterNot(_ == "unrestricted")
        .map(value => text("span", "card-restriction", value)),
      Option.when(!card.implemented)(
        text("span", "card-unimplemented-badge", "Unimplemented"))).flatten

  private def counted(token: String, count: Int): Vector[dom.Element] =
    Vector(RulesTextRenderer.glyph(token),
      text("span", "card-token-count", count.toString))
