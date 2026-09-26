package oathdigital.frontend

import org.scalajs.dom
import oathdigital.protocol.projection.LogEntryWire
import ServerUiSupport._

/** The whole log, full height over the table (spec, "Overlay"). The card
  * overlay's idiom: a scrim, a pane-fill panel, and Escape, Close or a click
  * on the scrim returning focus to the control that opened it. It is fed
  * every entry the pane is, so it is current whenever it opens. Focus lands
  * on the list, not on Close: the overlay exists to be scrolled.
  */
private[frontend] final class GameLogOverlay(root: dom.Element,
    scrolled: () => Unit, closed: () => Unit):
  private val node = element("aside", "log-overlay").asInstanceOf[dom.html.Element]
  node.id = "log-overlay"
  node.setAttribute("hidden", "")
  node.setAttribute("role", "dialog")
  node.setAttribute("aria-modal", "true")
  node.setAttribute("aria-labelledby", "log-overlay-heading")
  private val panel = element("div", "log-overlay-panel")
  private val header = element("div", "pane-header")
  private val heading = text("h2", "pane-heading", "Game Log")
  heading.id = "log-overlay-heading"
  private val close = button("Close", "log-overlay-close")
  private val content = element("div", "pane-content log-overlay-content")
    .asInstanceOf[dom.html.Element]
  content.tabIndex = 0
  content.setAttribute("aria-label", "Game Log contents")
  header.appendChild(heading)
  header.appendChild(close)
  panel.appendChild(header)
  panel.appendChild(content)
  node.appendChild(panel)
  root.appendChild(node)
  private val log = new GameLogPane(content, headings = true, scrolled)
  private var opener = Option.empty[dom.html.Element]

  def isOpen: Boolean = !node.hasAttribute("hidden")

  /** A closed overlay is never at the end: only what the reader sees counts. */
  def atEnd: Boolean = isOpen && log.atEnd

  def show(sessionKey: String, entries: Vector[LogEntryWire],
      colors: Map[String, String], since: Option[Long]): Unit =
    log.show(sessionKey, entries, colors, since)

  /** Opens where the pane is (see `GameLogPane.position`). */
  def open(position: Option[Int], origin: dom.html.Element): Unit =
    opener = Some(origin)
    node.removeAttribute("hidden")
    log.restore(position)
    content.focus()

  def hide(): Unit =
    if isOpen then
      node.setAttribute("hidden", "")
      // The opener is the pane heading, which the shell never rebuilds; the
      // guard matches the card overlay's in case that ever changes.
      opener.filter(dom.document.contains).foreach(_.focus())
      opener = None
      closed()

  private val dismiss: dom.MouseEvent => Unit = event =>
    if event.target == node || event.target == close then hide()

  private val escape: dom.KeyboardEvent => Unit = event =>
    if event.key == "Escape" && isOpen then { event.preventDefault(); hide() }

  node.addEventListener("click", dismiss)
  node.addEventListener("keydown", escape)

  def dispose(): Unit =
    node.removeEventListener("click", dismiss)
    node.removeEventListener("keydown", escape)
    log.dispose()
    node.remove()
