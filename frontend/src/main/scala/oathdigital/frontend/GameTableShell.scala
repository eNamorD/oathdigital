package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport._

/** Stable player-facing layout; renderer refreshes replace only panel contents. */
private[frontend] final class GameTableShell(mount: dom.Element, developmentTools: Boolean = true) {
  private val table = element("div", "game-table")
  while (mount.firstChild != null) mount.removeChild(mount.firstChild)
  mount.appendChild(table)

  private class Pane(id: String, title: String) {
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
  }

  private val players = new Pane("players", "Players")
  private val world = new Pane("world", "World Map")
  private val actions = new Pane("actions", "Action Selection")
  private val log = new Pane("log", "Game Log")
  log.content.appendChild(text("p", "log-placeholder", "Game history will appear here."))
  private val mapContent = element("div", "map-content").asInstanceOf[dom.html.Div]
  private val zoomLabel = text("span", "zoom-label", "100%")
  private val mapView = new MapViewport(world.content, mapContent,
    scale => zoomLabel.textContent = s"${math.round(scale * 100)}%")
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
  world.content.setAttribute("aria-label", "World Map contents. Scroll or drag to pan; plus and minus to zoom; zero to fit.")

  private val devToggle = button("Dev tools", "dev-toggle")
  devToggle.setAttribute("aria-controls", "development-panel")
  devToggle.setAttribute("aria-expanded", "false")
  if (developmentTools) players.header.appendChild(devToggle)
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
  private def setDev(open: Boolean): Unit = {
    if (open) dev.removeAttribute("hidden") else dev.setAttribute("hidden", "")
    devToggle.setAttribute("aria-expanded", open.toString)
    if (open) close.focus() else devToggle.focus()
  }
  devToggle.onclick = _ => setDev(dev.hasAttribute("hidden"))
  close.onclick = _ => setDev(false)
  private val escape: dom.KeyboardEvent => Unit = e => {
    if (e.key == "Escape" && !dev.hasAttribute("hidden")) { e.preventDefault(); setDev(false) }
  }
  dev.addEventListener("keydown", escape)
  private var previousGame = ""
  private var previousDecision = ""

  def update(gameId: String, decisionKey: String, playerContent: dom.Element,
      worldContent: dom.Element, actionContent: dom.Element, development: dom.Element): Unit = {
    val changedGame = previousGame != gameId
    PanelContent.replace(players.content, playerContent, players.heading, changedGame)
    PanelContent.replace(mapContent, worldContent, world.heading)
    PanelContent.replace(actions.content, actionContent, actions.heading,
      changedGame || previousDecision != decisionKey)
    PanelContent.replace(devContent, development, devHeading)
    mapView.refresh(changedGame)
    previousGame = gameId
    previousDecision = decisionKey
  }

  def dispose(): Unit = {
    mapView.dispose()
    dev.removeEventListener("keydown", escape)
    table.remove()
    dev.remove()
  }
}
