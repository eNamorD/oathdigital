package oathdigital.frontend

import org.scalajs.dom
import oathdigital.protocol.projection.{LogEntryWire, LogSpanWire}
import ServerUiSupport._

/** One reading of the game log (spec, "Pane" and "Overlay"). The pane and the
  * overlay each hold one, fed the same entries, so both hold the same
  * children. Entries only ever arrive at the end, so a render appends what is
  * new rather than replacing the list. A reader at the end stays there; a
  * reader who has scrolled up stays put and is offered the "New" chip.
  *
  * `headings` makes headlines into headings, for the overlay: a screen reader
  * jumps between them there, while the pane keeps the table's outline short.
  */
private[frontend] final class GameLogPane(content: dom.html.Element,
    headings: Boolean = false, scrolled: () => Unit = () => ()):
  private val list = element("ol", "game-log")
  list.setAttribute("role", "log")
  list.setAttribute("aria-live", "polite")
  content.appendChild(list)
  private val chip = button("New", "log-new")
  chip.setAttribute("hidden", "")
  chip.onclick = _ => toEnd()
  content.appendChild(chip)
  /** The headline that sticks in this list: the turn in the pane, the round
    * in the overlay (spec, "Pane" and "Overlay"). */
  private val sticky = if headings then "log-round" else "log-turn"
  private var key = Option.empty[String]
  private var shown = Vector.empty[LogEntryWire]
  list.appendChild(headline(GameLogPane.placeholder, "round"))

  private val onScroll: dom.Event => Unit = _ =>
    if atEnd then chip.setAttribute("hidden", "")
    scrolled()
  content.addEventListener("scroll", onScroll)

  /** `since` is the stored marker. It places the divider only when the list
    * is drawn in full, so appends never move it. */
  def show(sessionKey: String, entries: Vector[LogEntryWire],
      colors: Map[String, String], since: Option[Long] = None): Unit =
    val appending = key.contains(sessionKey) && shown.nonEmpty &&
      entries.size >= shown.size && entries(shown.size - 1) == shown.last
    if appending then
      if entries.size > shown.size then
        val following = atEnd
        entries.drop(shown.size).foreach(entry => list.appendChild(item(entry, colors)))
        if following then toEnd() else chip.removeAttribute("hidden")
    else redraw(entries, colors, since)
    key = Some(sessionKey)
    shown = entries

  private def redraw(entries: Vector[LogEntryWire], colors: Map[String, String],
      since: Option[Long]): Unit =
    while list.firstChild != null do list.removeChild(list.firstChild)
    chip.setAttribute("hidden", "")
    if entries.isEmpty then list.appendChild(headline(GameLogPane.placeholder, "round"))
    val divideAt = since.map(marker => entries.indexWhere(_.sequence > marker))
      .filter(_ >= 0)
    entries.zipWithIndex.foreach { (entry, index) =>
      if divideAt.contains(index) then list.appendChild(GameLogPane.divider)
      list.appendChild(item(entry, colors))
    }
    divideAt.fold(toEnd())(_ => reveal(list.querySelector(".log-divider")))

  def atEnd: Boolean = GameLogPane.atEnd(content)

  def last: Option[Long] = shown.lastOption.map(_.sequence)

  /** Where the reader is: nothing at the end, otherwise the index among the
    * list's children of the first one the reader can see. Stuck headlines
    * sit at the top edge whatever the scroll, so they are skipped, and a line
    * hidden under them does not count as seen. */
  def position: Option[Int] =
    if atEnd then None else
      val edge = content.getBoundingClientRect().top
      val stuck = children.filter(_.classList.contains(sticky))
        .map(_.getBoundingClientRect()).filter(_.top <= edge + 1)
      val top = stuck.map(_.bottom).maxOption.fold(edge)(_.max(edge))
      children.indexWhere(child => !child.classList.contains(sticky) &&
        child.getBoundingClientRect().bottom > top) match
        case -1 => None
        case index => Some(index)

  /** Scrolls to a position another list reported (see `position`). */
  def restore(at: Option[Int]): Unit =
    at.flatMap(children.lift).fold(toEnd())(reveal)

  def dispose(): Unit = content.removeEventListener("scroll", onScroll)

  private def children: Vector[dom.Element] =
    (0 until list.children.length).map(list.children(_)).toVector

  private def toEnd(): Unit =
    chip.setAttribute("hidden", "")
    content.scrollTop = content.scrollHeight.toDouble

  /** Scrolls `node` to the top, just under the headline stuck above it. */
  private def reveal(node: dom.Element): Unit =
    val before = children.takeWhile(_ != node)
    val cover = before.findLast(_.classList.contains(sticky))
      .fold(0.0)(_.getBoundingClientRect().height)
    content.scrollTop = content.scrollTop + node.getBoundingClientRect().top -
      content.getBoundingClientRect().top - cover

  private def item(entry: LogEntryWire, colors: Map[String, String]): dom.Element =
    val node = GameLogPane.item(entry, colors)
    if entry.depth == 0 then headline(node, entry.kind) else node

  /** In headings mode the headline's spans move into an `h3`, an `h4` for
    * a turn, or an `h5` for a phase. */
  private def headline(node: dom.Element, kind: String): dom.Element =
    if headings then
      val level = kind match
        case "turn" => "h4"
        case "phase" => "h5"
        case _ => "h3"
      val heading = element(level, "log-heading")
      while node.firstChild != null do heading.appendChild(node.firstChild)
      node.appendChild(heading)
    node

private[frontend] object GameLogPane:
  /** Within a few pixels of the end counts as at the end. */
  private val Slack = 4.0

  def atEnd(content: dom.html.Element): Boolean =
    content.scrollHeight - content.scrollTop - content.clientHeight <= Slack

  /** Before the first entry arrives the log is the game's first headline. */
  def placeholder: dom.Element =
    text("li", "log-entry log-headline log-round", "Setup")

  /** Client-only: where the reader left off (spec, "Divider"). */
  def divider: dom.Element = text("li", "log-divider", "Since you last looked")

  def item(entry: LogEntryWire, colors: Map[String, String]): dom.Element =
    val depth = if entry.depth == 0 then "log-headline" else "log-line"
    val node = element("li", s"log-entry $depth log-${entry.kind}")
    node.setAttribute("title", s"${entry.sequence}.${entry.ordinal}")
    entry.spans.foreach(span => node.appendChild(spanNode(span, colors)))
    node

  private def spanNode(span: LogSpanWire, colors: Map[String, String])
      : dom.Node = span.kind match
    case "player" => text("span", "log-player player-ref " +
      span.id.flatMap(colors.get).getOrElse(PlayerColorCss.neutral), span.text)
    case "card" | "site" | "amount" | "cost" =>
      text("span", s"log-${span.kind}", span.text)
    // The same die chips the table draws; the faces ride in `id`.
    case "dice" => span.id.fold[dom.Node](dom.document.createTextNode(span.text)) {
      faces =>
        val dice = DieFace.roll(faces.split(" ").toVector.filter(_.nonEmpty))
        dice.setAttribute("class",
          s"die-faces log-dice log-dice-${span.unit.getOrElse("unknown")}")
        dice
    }
    // More cards than a line names: a control that opens them as a card
    // list (catalog batch 2, N5). A span with no cards stays words.
    case "cards" if span.cards.nonEmpty =>
      val open = button(span.text, "log-cards")
      open.onclick = _ => CardInspection.openCards(span.text, span.cards, open)
      open
    case _ => dom.document.createTextNode(span.text)
