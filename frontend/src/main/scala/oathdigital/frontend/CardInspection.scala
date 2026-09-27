package oathdigital.frontend

import org.scalajs.dom

/** One slot connecting every rendered card to whatever is showing overlays.
  *
  * Cards reach `CardFace.render` through six renderer objects, only some of
  * which carry the session's controls. Threading a callback through the rest
  * for a single handler is more change than the feature is worth, and
  * `GameTableShell` already owns the other document-level chrome.
  */
private[frontend] object CardInspection:
  /** What the slot is asked to show. Sealed rather than two slots: the shell
    * wires exactly one handler, and a second slot could be left unwired.
    */
  sealed trait Request extends Product with Serializable:
    def origin: dom.html.Element
  object Request:
    final case class Card(card: CardDetails, origin: dom.html.Element)
        extends Request
    final case class Text(title: String, lines: Vector[String],
        origin: dom.html.Element) extends Request
    /** Cards in order, as a log line's card list opens them (N5). */
    final case class Cards(title: String, cards: Vector[CardDetails],
        origin: dom.html.Element) extends Request

  private var handler = Option.empty[Request => Unit]

  def onOpen(value: Request => Unit): Unit = handler = Some(value)

  def clear(): Unit = handler = None

  def open(card: CardDetails, origin: dom.html.Element): Unit =
    handler.foreach(_(Request.Card(card, origin)))

  def openText(title: String, lines: Vector[String],
      origin: dom.html.Element): Unit =
    handler.foreach(_(Request.Text(title, lines, origin)))

  def openCards(title: String, cards: Vector[CardDetails],
      origin: dom.html.Element): Unit =
    handler.foreach(_(Request.Cards(title, cards, origin)))
