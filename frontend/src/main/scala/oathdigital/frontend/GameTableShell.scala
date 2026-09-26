package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport._

/** Stable player-facing layout; renderer refreshes replace only panel contents. */
private[frontend] final class GameTableShell(mount: dom.Element,
    developmentTools: Boolean = true,
    logStore: Option[LogStore] = LogStore.browser):
  private val table = element("div", "game-table")
  while mount.firstChild != null do mount.removeChild(mount.firstChild)
  mount.appendChild(table)

  private class Pane(id: String, title: String):
    val node = element("section", s"game-pane pane-$id")
    node.setAttribute("aria-labelledby", s"$id-heading")
    val header = element("div", "pane-header")
    val heading = text("h2", "pane-heading", title).asInstanceOf[dom.html.Element]
    heading.id = s"$id-heading"
    heading.tabIndex = -1
    val content = element("div", "pane-content").asInstanceOf[dom.html.Div]
    content.tabIndex = 0
    content.setAttribute("aria-label", s"$title contents")
    header.appendChild(heading)
    node.appendChild(header)
    node.appendChild(content)
    table.appendChild(node)

  private val players = new Pane("players", "Players")
  private val world = new Pane("world", "World Map")
  private val actions = new Pane("actions", "Action Selection")
  private val log = new Pane("log", "Game Log")
  private val marker = new LogMarker(logStore)
  private val logPane = new GameLogPane(log.content, scrolled = () => watchLog())
  private val logOverlay = new GameLogOverlay(mount, () => watchLog(), () => {
    behindLog(false)
    log.heading.setAttribute("aria-expanded", "false")
    watchLog()
  })
  /** The log overlay is modal: while it is open the table and the developer
    * panel behind it are inert, so Tab cannot leave it and no second dialog
    * opens under it. */
  private def behindLog(open: Boolean): Unit =
    Vector(table, dev).foreach(node =>
      if open then node.setAttribute("inert", "") else node.removeAttribute("inert"))
  // The heading opens the whole log (spec, "Overlay").
  log.heading.tabIndex = 0
  log.heading.setAttribute("role", "button")
  log.heading.setAttribute("aria-expanded", "false")
  log.heading.setAttribute("aria-controls", "log-overlay")
  private def openLog(): Unit =
    if inspector.isOpen then inspector.hide()
    behindLog(true)
    log.heading.setAttribute("aria-expanded", "true")
    logOverlay.open(logPane.position, log.heading)
    watchLog()
  log.heading.onclick = _ => openLog()
  log.heading.onkeydown = event =>
    if event.key == "Enter" || event.key == " " then
      event.preventDefault()
      openLog()
  private var logSeat = Option.empty[String]
  private var logSince = Option.empty[Long]
  /** Either list at its end for a second marks the log seen (spec,
    * "Divider"). */
  private def watchLog(): Unit =
    marker.observe(logPane.atEnd || logOverlay.atEnd, logPane.last)
  private val leaving: dom.Event => Unit = _ => marker.flush()
  dom.window.addEventListener("pagehide", leaving)
  private val mapContent = element("div", "map-content").asInstanceOf[dom.html.Div]
  private val zoomLabel = text("span", "zoom-label", "100%")
  private val mapView = new MapViewport(world.content, mapContent, scale => {
    zoomLabel.textContent = s"${math.round(scale * 100)}%"
    // The stylesheet reads the scale to hold the glance layer (names, pawns,
    // tokens) near one screen size while the board shrinks under it.
    mapContent.style.setProperty("--map-scale", scale.toString)
    // Pure class toggle, no re-render: the face keeps every element and CSS
    // decides what is visible, so a degraded face-up card can never adopt the
    // face-down letter treatment.
    GameTableShell.fitClasses.foreach(mapContent.classList.remove)
    GameTableShell.nameFit(scale) match
      case Some(bucket) =>
        mapContent.classList.add("map-compact")
        mapContent.classList.add(s"map-fit-$bucket")
      case None => mapContent.classList.remove("map-compact")
  })
  private val zoomControls = element("div", "map-controls")
  private val out = button("−", "map-control")
  out.setAttribute("aria-label", "Zoom out on world map")
  out.onclick = _ => mapView.zoomBy(0.8)
  private val in = button("+", "map-control")
  in.setAttribute("aria-label", "Zoom in on world map")
  in.onclick = _ => mapView.zoomBy(1.25)
  private val fit = button("Fit", "map-control")
  fit.setAttribute("aria-label", "Fit world map")
  fit.onclick = _ => mapView.reset()
  Vector(out, zoomLabel, in, fit).foreach(zoomControls.appendChild)
  world.header.appendChild(zoomControls)
  world.content.setAttribute("aria-label", "World Map contents. Drag to pan; wheel, plus and minus to zoom; zero to fit.")

  private val devToggle = button("Dev tools", "dev-toggle")
  devToggle.setAttribute("aria-controls", "development-panel")
  devToggle.setAttribute("aria-expanded", "false")
  if developmentTools then players.header.appendChild(devToggle)
  private val dev = element("aside", "development-panel").asInstanceOf[dom.html.Element]
  dev.id = "development-panel"
  dev.setAttribute("hidden", "")
  dev.setAttribute("role", "dialog")
  dev.setAttribute("aria-labelledby", "development-heading")
  private val devHeader = element("div", "pane-header")
  private val devHeading = text("h2", "pane-heading", "Development tools").asInstanceOf[dom.html.Element]
  devHeading.id = "development-heading"
  devHeading.tabIndex = -1
  private val close = button("Close", "dev-close")
  private val devContent = element("div", "pane-content")
  devHeader.appendChild(devHeading)
  devHeader.appendChild(close)
  dev.appendChild(devHeader)
  dev.appendChild(devContent)
  mount.appendChild(dev)
  private def setDev(open: Boolean): Unit =
    if open then dev.removeAttribute("hidden") else dev.setAttribute("hidden", "")
    devToggle.setAttribute("aria-expanded", open.toString)
    if open then close.focus() else devToggle.focus()
  devToggle.onclick = _ => setDev(dev.hasAttribute("hidden"))
  close.onclick = _ => setDev(false)
  private val escape: dom.KeyboardEvent => Unit = e => {
    if e.key == "Escape" && !dev.hasAttribute("hidden") then { e.preventDefault(); setDev(false) }
  }
  dev.addEventListener("keydown", escape)
  private val inspector = new CardInspectionOverlay(mount)
  CardInspection.onOpen:
    case CardInspection.Request.Card(card, origin) => inspector.show(card, origin)
    case CardInspection.Request.Text(title, lines, origin) =>
      inspector.showText(title, lines, origin)
  private var previousGame = ""
  private var previousDecision = ""

  def update(gameId: String, decisionKey: String, playerContent: dom.Element,
      worldContent: dom.Element, actionContent: dom.Element, development: dom.Element): Unit =
    val changedGame = previousGame != gameId
    PanelContent.replace(players.content, playerContent, players.heading, changedGame)
    PanelContent.replace(mapContent, worldContent, world.heading)
    PanelContent.replace(actions.content, actionContent, actions.heading,
      changedGame || previousDecision != decisionKey)
    PanelContent.replace(devContent, development, devHeading)
    mapView.refresh(changedGame)
    previousGame = gameId
    previousDecision = decisionKey

  /** The log is not rebuilt with the other panes: it only grows. The marker
    * is read once per seat, so the pane and the overlay divide at the same
    * entry. */
  def showLog(gameId: String, seatId: String,
      entries: Vector[oathdigital.protocol.projection.LogEntryWire],
      colors: Map[String, String]): Unit =
    val sessionKey = s"$gameId|$seatId"
    if !logSeat.contains(sessionKey) then
      logSince = marker.open(gameId, seatId)
      logSeat = Some(sessionKey)
    logPane.show(sessionKey, entries, colors, logSince)
    logOverlay.show(sessionKey, entries, colors, logSince)
    watchLog()

  def dispose(): Unit =
    mapView.dispose()
    dev.removeEventListener("keydown", escape)
    CardInspection.clear()
    inspector.dispose()
    dom.window.removeEventListener("pagehide", leaving)
    logPane.dispose()
    logOverlay.dispose()
    table.remove()
    dev.remove()

private[frontend] object GameTableShell:
  /** Functional text holds 11px on screen (the design detector's floor). */
  private val NameFloorPx = 11.0
  /** A map card's name at rest is 0.95em of the map's 16px, 15.2px, which
    * crosses the floor at 0.72. Below it the compact face takes over and the
    * glance layer is set against the scale instead.
    */
  private val CompactBelow = NameFloorPx / 15.2
  def compactAtScale(scale: Double): Boolean = scale < CompactBelow

  /** A map card is 8.6rem wide; the compact face pads it 0.25em of its
    * floor-sized text on each side. Inter at 750 advances about 0.58em per
    * character of a mixed-case name.
    */
  private val CardWidthPx = 8.6 * 16
  private val CardPaddingScreenPx = 0.25 * NameFloorPx
  private val AverageAdvanceEm = 0.58

  /** The bucket of `CardFace.NameFitBuckets` in force at this scale: the
    * longest word a card can show whole on one line of floor-sized text.
    * `Some(0)` is a scale where no bucket fits and every card shows its
    * initials; `None` is a scale that is not compact at all.
    */
  def nameFit(scale: Double): Option[Int] = Option.when(compactAtScale(scale)) {
    val characters = (CardWidthPx * scale - 2 * CardPaddingScreenPx) /
      (NameFloorPx * AverageAdvanceEm)
    CardFace.NameFitBuckets.filter(_ <= characters).lastOption.getOrElse(0)
  }

  val fitClasses: Vector[String] =
    (0 +: CardFace.NameFitBuckets).map(bucket => s"map-fit-$bucket")
