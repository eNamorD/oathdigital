package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport._

/** A full-window card inspector, rendered at document level above the panes.
  *
  * Follows the development panel's dialog pattern in `GameTableShell`: hidden
  * attribute, Escape handler, Close button, focus return to the opening
  * control. It ignores projection changes while open -- what a card does does
  * not change when the card moves, and closing on every poll tick would yank
  * the card out from under a reader mid-sentence, which is the interruption
  * this whole design exists to remove.
  */
private[frontend] final class CardInspectionOverlay(root: dom.Element):
  private val node = element("aside", "card-overlay").asInstanceOf[dom.html.Element]
  node.setAttribute("hidden", "")
  node.setAttribute("role", "dialog")
  node.setAttribute("aria-modal", "true")
  node.setAttribute("aria-label", "Card details")
  private val body = element("div", "card-overlay-body")
  private val close = button("Close", "card-overlay-close")
  node.appendChild(body)
  node.appendChild(close)
  root.appendChild(node)
  TokenSprite.mount(root)

  /** What opened the overlay, and where focus goes back to when it closes.
    * `pane` is the opener's `.game-pane`: a poll rebuilds a pane's content
    * but keeps the pane and its heading, so the pane is where a rebuilt
    * opener is looked for, and its heading is the fallback when none is
    * found -- the same fallback `PanelContent.replace` uses.
    */
  private final case class Opener(node: dom.html.Element,
      pane: Option[dom.Element], place: Option[PanelContent.Place])

  private var opener = Option.empty[Opener]

  def isOpen: Boolean = !node.hasAttribute("hidden")

  def show(card: CardDetails, origin: dom.html.Element): Unit =
    clear()
    node.setAttribute("aria-label", "Card details")
    // Inspection reads a card, so a card the viewer is allowed to read is
    // turned over here. One the viewer cannot identify has nothing to turn.
    body.appendChild(CardFace.render(
      if card.hidden then card else card.copy(orientation = Some("face-up"))))
    if !card.hidden then body.appendChild(details(card))
    open(origin)

  /** The overlay over something that is not a card: a title and its printed
    * lines. The Oath is one of these -- it is a state of the game with a
    * printed card behind it, and faking a `CardDetails` for it would put a
    * card face on the table that nothing can be played from.
    */
  def showText(title: String, lines: Vector[String],
      origin: dom.html.Element): Unit =
    clear()
    // Named for what it shows: it is not a card, so "Card details" would
    // misname it.
    node.setAttribute("aria-label", title)
    val panel = element("div", "card-overlay-details")
    panel.appendChild(text("h3", "card-overlay-name", title))
    lines.foreach(line => RulesTextRenderer.powers(line)
      .foreach(panel.appendChild))
    body.appendChild(panel)
    open(origin)

  /** Cards in the order given, as a card list (catalog batch 2, N5): a log
    * line's "6 cards". Each is its face or its back as projected; a face
    * still opens that one card here.
    */
  def showCards(title: String, cards: Vector[CardDetails],
      origin: dom.html.Element): Unit =
    clear()
    node.setAttribute("aria-label", title)
    body.appendChild(CardList.render(title, cards))
    open(origin)

  private def clear(): Unit =
    while body.firstChild != null do body.removeChild(body.firstChild)

  private def open(origin: dom.html.Element): Unit =
    val pane = Option(origin.closest(".game-pane"))
    opener = Some(Opener(origin, pane,
      pane.map(PanelContent.place(_, origin)).filter(_.index >= 0)))
    node.removeAttribute("hidden")
    close.focus()

  def hide(): Unit =
    node.setAttribute("hidden", "")
    opener.flatMap(returnTarget) match
      case Some(target) => target.focus()
      // Nowhere to return to: at least do not leave focus on the hidden
      // close button.
      case None => close.blur()
    opener = None

  /** The opener if it is still in the document; else, when a poll rebuilt
    * it away while the overlay was open, its rebuilt twin in the same pane;
    * else that pane's heading.
    */
  private def returnTarget(from: Opener): Option[dom.html.Element] =
    val pane = from.pane.filter(dom.document.contains)
    Option(from.node).filter(dom.document.contains)
      .orElse(for p <- pane; at <- from.place; n <- PanelContent.find(p, at) yield n)
      .orElse(pane.flatMap(p => Option(p.querySelector(".pane-heading")))
        .map(_.asInstanceOf[dom.html.Element]))

  private def details(card: CardDetails): dom.Element =
    val panel = element("div", "card-overlay-details")
    panel.appendChild(text("h3", "card-overlay-name", card.name))
    val properties = element("dl", "card-overlay-properties")
    Vector(card.suit.map("Suit" -> _),
      card.restrictions.filterNot(_ == "unrestricted").map("Restrictions" -> _),
      card.orientation.map("Orientation" -> _),
      card.side.map("Side" -> _),
      Option.when(card.favor > 0)("Favor" -> card.favor.toString),
      Option.when(card.secrets > 0)("Secrets" -> card.secrets.toString),
      card.relicValue.map(v => "Relic value" -> v.toString),
      card.defense.map(v => "Defense" -> v.toString)).flatten.foreach:
      case (label, value) =>
        val item = element("div", "card-overlay-property")
        item.appendChild(text("dt", "", label))
        item.appendChild(text("dd", "", value))
        properties.appendChild(item)
    if properties.childNodes.length > 0 then panel.appendChild(properties)
    card.rulesText.foreach { value =>
      val rules = element("div", "card-overlay-rules")
      RulesTextRenderer.powers(value).foreach(rules.appendChild)
      panel.appendChild(rules)
    }
    if !card.implemented then
      panel.appendChild(text("p", "card-unimplemented-badge", "Unimplemented"))
    panel

  private val dismiss: dom.MouseEvent => Unit = event =>
    // A click anywhere on the overlay dismisses it, except inside the card
    // itself -- selecting rules text should not close what you are reading.
    if event.target == node || event.target == close then hide()

  private val escape: dom.KeyboardEvent => Unit = event =>
    if event.key == "Escape" && isOpen then { event.preventDefault(); hide() }

  node.addEventListener("click", dismiss)
  node.addEventListener("keydown", escape)

  def dispose(): Unit =
    node.removeEventListener("click", dismiss)
    node.removeEventListener("keydown", escape)
    node.remove()
