package oathdigital.frontend

import org.scalajs.dom
import oathdigital.protocol.projection.{LogEntryWire, LogSpanWire,
  LogTakenBackWire}
import ServerUiSupport._

/** One reading of the game log (spec, "Pane" and "Overlay"). The pane and the
  * overlay each hold one, fed the same entries, so both hold the same
  * children. Entries only ever arrive at the end, so a render appends what is
  * new rather than replacing the list. A reader at the end stays there; a
  * reader who has scrolled up stays put and is offered the "New" chip.
  *
  * `headings` makes headlines into headings, for the overlay: a screen reader
  * jumps between them there, while the pane keeps the table's outline short.
  *
  * The lines of one action share a band; an append moves the band's end,
  * never anything above it.
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
  // The taken-back groups the reader opened, kept across redraws.
  private var expanded = Set.empty[Long]
  list.appendChild(headline(GameLogPane.placeholder, "round"))

  private val onScroll: dom.Event => Unit = _ =>
    if atEnd then chip.setAttribute("hidden", "")
    scrolled()
  content.addEventListener("scroll", onScroll)

  /** `since` is the stored marker. It places the divider only when the list
    * is drawn in full, so appends never move it. An undo changes lines
    * already shown, and a taken-back line is drawn inside its group, so
    * only new live lines at the end append; anything else redraws. */
  def show(sessionKey: String, entries: Vector[LogEntryWire],
      colors: Map[String, String], since: Option[Long] = None): Unit =
    val sameSession = key.contains(sessionKey)
    if !sameSession then expanded = Set.empty
    val appending = sameSession && shown.nonEmpty &&
      entries.startsWith(shown) &&
      entries.drop(shown.size).forall(_.takenBack.isEmpty)
    if appending then
      if entries.size > shown.size then
        val following = atEnd
        // A line that continues the last entry's band moves the band's end
        // down to itself; anything else starts a band of its own.
        entries.drop(shown.size).foldLeft(shown.last) { (previous, entry) =>
          val continues = GameLogPane.joined(previous, entry)
          if continues then Option(list.lastElementChild)
            .foreach(_.classList.remove("log-band-end"))
          list.appendChild(item(entry, colors, start = !continues, end = true))
          entry
        }
        if following then toEnd() else chip.removeAttribute("hidden")
    else redraw(entries, colors, since,
      keepPlace = sameSession && shown.nonEmpty)
    key = Some(sessionKey)
    shown = entries

  /** `keepPlace` is a redraw of a list already on show, after an undo: a
    * reader at the end stays there and anyone else keeps their scroll.
    * Only a session's first draw goes to the divider. */
  private def redraw(entries: Vector[LogEntryWire], colors: Map[String, String],
      since: Option[Long], keepPlace: Boolean): Unit =
    val following = atEnd
    val top = content.scrollTop
    while list.firstChild != null do list.removeChild(list.firstChild)
    chip.setAttribute("hidden", "")
    if entries.isEmpty then list.appendChild(headline(GameLogPane.placeholder, "round"))
    // A divider never splits a group: it stands before the group's first
    // line instead.
    val divideAt = since.map(marker => entries.indexWhere(_.sequence > marker))
      .filter(_ >= 0).map(at => entries(at).takenBack.fold(at)(taken =>
        entries.indexWhere(_.takenBack.exists(_.group == taken.group))))
    // The divider ends the band above it and starts the band below it.
    entries.zipWithIndex.foldLeft(Option.empty[(Long, dom.Element)]) {
      case (open, (entry, index)) =>
        val divided = divideAt.contains(index)
        if divided then list.appendChild(GameLogPane.divider)
        val start = divided ||
          !entries.lift(index - 1).exists(GameLogPane.joined(_, entry))
        val end = divideAt.contains(index + 1) ||
          !entries.lift(index + 1).exists(GameLogPane.joined(entry, _))
        val node = item(entry, colors, start, end)
        entry.takenBack match
          case None =>
            list.appendChild(node)
            None
          case Some(taken) =>
            val lines = open.filter(_._1 == taken.group).map(_._2)
              .getOrElse(group(taken, colors))
            lines.appendChild(node)
            Some(taken.group -> lines)
    }
    if keepPlace then
      if following then toEnd() else content.scrollTop = top
    else divideAt.fold(toEnd())(_ => reveal(list.querySelector(".log-divider")))

  /** A taken-back group (undo design, "Game Log"): a toggle naming who took
    * the moves back, over the lines, collapsed unless the reader opened it.
    * Appends the group to the list and returns the list its lines go in. */
  private def group(taken: LogTakenBackWire, colors: Map[String, String])
      : dom.Element =
    val node = element("li", "log-taken-back")
    val toggle = button("", "log-taken-back-toggle")
    taken.header.foreach(span =>
      toggle.appendChild(GameLogPane.spanNode(span, colors)))
    val lines = element("ol", "log-taken-back-lines")
    def open(value: Boolean): Unit =
      toggle.setAttribute("aria-expanded", value.toString)
      if value then lines.removeAttribute("hidden")
      else lines.setAttribute("hidden", "")
    open(expanded.contains(taken.group))
    toggle.onclick = _ =>
      val opening = lines.hasAttribute("hidden")
      expanded = if opening then expanded + taken.group
        else expanded - taken.group
      open(opening)
    node.appendChild(toggle)
    node.appendChild(lines)
    list.appendChild(node)
    lines

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

  private def item(entry: LogEntryWire, colors: Map[String, String],
      start: Boolean, end: Boolean): dom.Element =
    val node = GameLogPane.item(entry, colors, start, end)
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

  def item(entry: LogEntryWire, colors: Map[String, String],
      start: Boolean = true, end: Boolean = true): dom.Element =
    val depth = if entry.depth == 0 then "log-headline" else "log-line"
    val classes = Vector("log-entry", depth, s"log-${entry.kind}") ++
      band(entry, start, end)
    val node = element("li", classes.mkString(" "))
    node.setAttribute("title", s"${entry.sequence}.${entry.ordinal}")
    entry.spans.foreach(span => node.appendChild(spanNode(span, colors)))
    node

  /** An entry's band (DESIGN.md, The Action Band Rule): its section's band
    * class, and an edge where the band starts or ends in this list. */
  def band(entry: LogEntryWire, start: Boolean, end: Boolean): Vector[String] =
    entry.section.toVector.flatMap(section =>
      Vector("log-band", s"log-band-${bandName(section.action)}") ++
        Option.when(start)("log-band-start") ++ Option.when(end)("log-band-end"))

  /** Playing a facedown adviser continues a Search, so it takes Search's
    * band; every power shares one band whatever its id. */
  def bandName(action: String): String =
    if action == "play-facedown-adviser" then "search"
    else if action.startsWith("use-power:") then "use-power"
    else action

  def sameSection(earlier: LogEntryWire, later: LogEntryWire): Boolean =
    earlier.section.nonEmpty &&
      earlier.section.map(_.id) == later.section.map(_.id)

  /** Two entries share a band only inside the same section and on the same
    * side of a taken-back group's edge. */
  def joined(earlier: LogEntryWire, later: LogEntryWire): Boolean =
    sameSection(earlier, later) &&
      earlier.takenBack.map(_.group) == later.takenBack.map(_.group)

  private def spanNode(span: LogSpanWire, colors: Map[String, String])
      : dom.Node = span.kind match
    case "player" => text("span", "log-player player-ref " +
      span.id.flatMap(colors.get).getOrElse(PlayerColorCss.neutral), span.text)
    case "card" | "site" | "amount" | "cost" =>
      text("span", s"log-${span.kind}", span.text)
    // The same die chips the table draws; the faces ride in `id`.
    case "dice" => span.id.fold[dom.Node](dom.document.createTextNode(span.text)) {
      faces =>
        val dice = DieFace.roll(faces.split(" ").toVector.filter(_.nonEmpty),
          span.unit)
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
