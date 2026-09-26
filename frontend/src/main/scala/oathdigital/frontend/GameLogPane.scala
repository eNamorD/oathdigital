package oathdigital.frontend

import org.scalajs.dom
import oathdigital.protocol.projection.{LogEntryWire, LogSpanWire}
import ServerUiSupport._

/** The Log pane's list (spec, "Pane"). Entries only ever arrive at the end,
  * so a render appends what is new rather than replacing the list, and the
  * view stays at the end when the reader was already there.
  */
private[frontend] final class GameLogPane(content: dom.html.Element):
  private val list = element("ol", "game-log")
  list.setAttribute("role", "log")
  list.setAttribute("aria-live", "polite")
  content.appendChild(list)
  private var key = Option.empty[String]
  private var shown = Vector.empty[LogEntryWire]
  list.appendChild(GameLogPane.placeholder)

  def show(sessionKey: String, entries: Vector[LogEntryWire],
      colors: Map[String, String]): Unit =
    val appending = key.contains(sessionKey) && shown.nonEmpty &&
      entries.size >= shown.size && entries(shown.size - 1) == shown.last
    if !(appending && entries.size == shown.size) then
      val following = !appending || GameLogPane.atEnd(content)
      val fresh = if appending then entries.drop(shown.size) else
        while list.firstChild != null do list.removeChild(list.firstChild)
        entries
      if fresh.isEmpty then list.appendChild(GameLogPane.placeholder)
      else fresh.foreach(entry => list.appendChild(GameLogPane.item(entry, colors)))
      if following then content.scrollTop = content.scrollHeight.toDouble
    key = Some(sessionKey)
    shown = entries

private[frontend] object GameLogPane:
  /** Within a few pixels of the end counts as at the end. */
  private val Slack = 4.0

  def atEnd(content: dom.html.Element): Boolean =
    content.scrollHeight - content.scrollTop - content.clientHeight <= Slack

  /** Before the first entry arrives the log is the game's first headline. */
  def placeholder: dom.Element =
    text("li", "log-entry log-headline log-round", "Setup")

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
    case _ => dom.document.createTextNode(span.text)
