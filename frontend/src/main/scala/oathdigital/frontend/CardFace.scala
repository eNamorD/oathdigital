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
private[frontend] object CardFace {

  /** Edifices share the denizen box because they occupy denizen slots, and a
    * differently shaped edifice would break the slot grid the moment one is
    * built. Visions share it rather than earn a ratio used by one case.
    */
  def boxClass(cardKind: String): String =
    if (cardKind == "relic") "card-face-relic" else "card-face-denizen"

  /** Safe on a card the viewer cannot identify: a denizen back and a vision
    * back differ physically, so which one this is stays public even when the
    * card's identity does not. `GamePresentationProjector` projects the real
    * kind for an unidentifiable adviser (commit `7ae54c3`).
    */
  def backLetter(cardKind: String): String = cardKind match {
    case "vision" => "V"
    case "relic" => "R"
    case _ => "D"
  }

  def faceDown(card: CardDetails): Boolean =
    card.hidden || card.orientation.contains("face-down")

  def render(card: CardDetails): dom.html.Button = {
    val node = dom.document.createElement("button").asInstanceOf[dom.html.Button]
    node.`type` = "button"
    node.className = Vector("card-face", boxClass(card.cardKind),
      if (faceDown(card)) "card-face-down" else "",
      if (faceDown(card) && !card.hidden) "card-face-knowable" else "",
      if (card.implemented) "" else "card-face-unimplemented")
      .filter(_.nonEmpty).mkString(" ")
    node.setAttribute("data-card-id", card.cardId)
    node.setAttribute("aria-label",
      if (card.hidden) card.name
      else if (card.implemented) card.name
      else s"${card.name} (unimplemented)")

    if (card.hidden) {
      node.appendChild(text("span", "card-back-letter", backLetter(card.cardKind)))
    } else if (faceDown(card)) {
      // Knowable: the back is what shows at rest; the summary is revealed by
      // CSS on hover only, inside the same box, so nothing moves.
      node.appendChild(text("span", "card-back-letter", backLetter(card.cardKind)))
      val pip = element("span", "card-knowable-pip")
      pip.setAttribute("aria-hidden", "true")
      node.appendChild(pip)
      val peek = element("span", "card-hover-summary")
      summary(card).foreach(peek.appendChild)
      node.appendChild(peek)
    } else {
      summary(card).foreach(node.appendChild)
    }

    // Unclaimed everywhere: `BoardTargetRefProjection` has only a `Site` case,
    // and decision panels select by drag, by the move-option buttons, or by
    // the plus/minus steppers -- never by clicking a card. So a click can mean
    // inspect, in every context, with nothing to disambiguate against.
    node.onclick = _ => CardInspection.open(card, node)
    node
  }

  /** Priority order, dropped from the bottom as the box shrinks: name, live
    * tokens, suit, relic value and defense, restriction, unimplemented.
    * Live tokens outrank suit because they are the only fields that change
    * during play, and a board that looks stale is worse than one with an
    * unlabelled suit.
    */
  private def summary(card: CardDetails): Vector[dom.Element] = {
    val name = text("span", "card-name", card.name)
    val tokens = element("span", "card-tokens")
    if (card.favor > 0) counted("favor", card.favor).foreach(tokens.appendChild)
    if (card.secrets > 0) counted("secret", card.secrets).foreach(tokens.appendChild)
    val stats = element("span", "card-stats")
    card.relicValue.foreach(value =>
      stats.appendChild(labelled("card-stat relic-value", value.toString,
        s"relic value $value")))
    card.defense.foreach(value =>
      stats.appendChild(labelled("card-stat relic-defense", value.toString,
        s"defense $value")))
    Vector(Some(name),
      Option.when(tokens.childNodes.length > 0)(tokens),
      card.suit.map(value => text("span", s"card-suit suit-$value", value)),
      Option.when(stats.childNodes.length > 0)(stats),
      card.restrictions.map(value => text("span", "card-restriction", value)),
      Option.when(!card.implemented)(
        text("span", "card-unimplemented-badge", "Unimplemented"))).flatten
  }

  private def counted(token: String, count: Int): Vector[dom.Element] =
    Vector(RulesTextRenderer.glyph(token),
      text("span", "card-token-count", count.toString))

  private def labelled(className: String, value: String,
      accessible: String): dom.Element = {
    val node = text("span", className, value)
    node.setAttribute("aria-label", accessible)
    node
  }
}
