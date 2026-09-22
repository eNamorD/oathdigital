package oathdigital.frontend

import oathdigital.protocol._
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import ServerUiSupport._

/** Seat links live only in this creation result; the host distributes them manually. */
private[frontend] object TrustedHostUi {
  /** Lineage colors in menu order. Purple is the Chancellor's and is not offered yet. */
  val LineageColors: Vector[String] = Vector("red", "blue", "yellow", "white", "black", "pink", "brown")
  val MinPlayers = 2
  val MaxPlayers = 6

  /** The color decides the lineage; the backend should derive it (see the roadmap). */
  def lineageId(color: String): String = s"$color-lineage"
  def defaultPlayerId(color: String): String = color.capitalize

  /** Mirrors the backend identifier rule in `TrustedGameCodecFields.identifier`. */
  def validPlayerId(value: String): Boolean =
    value.length <= 128 && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*")

  private final case class PlayerRow(color: String, node: dom.Element, input: dom.html.Input)

  def start(mount: dom.Element, transport: JsonTransport): Unit = {
    mount.textContent = ""
    mount.appendChild(text("h1", "", "Create an Oath Digital game"))
    mount.appendChild(text("p", "", "Add one row per player, then send each player " +
      "their assigned link. Anyone with a seat link can control that seat. " +
      "Seating order and the first player are chosen at random. " +
      "Save these links before leaving this page."))
    var gameId = freshGameId()
    val form = element("form", "trusted-host-form")
    val list = element("ol", "host-players")
    list.setAttribute("aria-label", "Players")
    form.appendChild(list)
    val status = text("p", "status", "")
    status.setAttribute("role", "status")
    var rows = Vector.empty[PlayerRow]

    val adder = element("div", "add-player")
    val toggle = button("Add a Player", "add-player-toggle")
    toggle.setAttribute("type", "button")
    toggle.setAttribute("aria-haspopup", "listbox")
    toggle.setAttribute("aria-expanded", "false")
    val menu = element("ul", "add-player-menu")
    menu.setAttribute("role", "listbox")
    menu.setAttribute("aria-label", "Player color")
    menu.setAttribute("hidden", "")
    val limit = text("span", "add-player-limit", "")
    adder.appendChild(toggle)
    adder.appendChild(menu)
    adder.appendChild(limit)
    form.appendChild(adder)

    def taken: Set[String] = rows.map(_.color).toSet
    def options: Vector[dom.html.Button] = {
      val found = menu.querySelectorAll("button")
      (0 until found.length).toVector.map(found(_).asInstanceOf[dom.html.Button])
    }
    def closeMenu(): Unit = {
      menu.setAttribute("hidden", "")
      toggle.setAttribute("aria-expanded", "false")
    }
    def openMenu(): Unit = {
      menu.textContent = ""
      LineageColors.filterNot(taken).foreach { color =>
        val item = element("li", "")
        item.setAttribute("role", "none")
        val option = button("", s"add-player-option add-player-$color")
        option.setAttribute("type", "button")
        option.setAttribute("role", "option")
        option.appendChild(swatch(color))
        option.appendChild(text("span", "", color.capitalize))
        option.onclick = event => {
          event.preventDefault(); closeMenu(); addRow(color); toggle.focus()
        }
        item.appendChild(option)
        menu.appendChild(item)
      }
      menu.removeAttribute("hidden")
      toggle.setAttribute("aria-expanded", "true")
      options.headOption.foreach(_.focus())
    }
    def refresh(): Unit = {
      val full = rows.size >= MaxPlayers
      toggle.disabled = full || taken.size == LineageColors.size
      limit.textContent = if (full) s"Maximum $MaxPlayers players" else ""
      if (toggle.disabled) closeMenu()
    }
    def addRow(color: String): Unit = {
      val node = element("li", s"host-player host-player-$color")
      node.appendChild(swatch(color))
      node.appendChild(text("span", "host-player-color", color.capitalize))
      val input = dom.document.createElement("input").asInstanceOf[dom.html.Input]
      input.className = "host-player-id"
      input.setAttribute("aria-label", s"${color.capitalize} player ID")
      input.value = defaultPlayerId(color)
      input.required = true
      node.appendChild(input)
      val remove = button("Remove", s"remove-player remove-player-$color")
      remove.setAttribute("type", "button")
      remove.setAttribute("aria-label", s"Remove ${color.capitalize} player")
      remove.onclick = event => {
        event.preventDefault()
        rows = rows.filterNot(_.color == color)
        list.removeChild(node)
        refresh()
      }
      node.appendChild(remove)
      list.appendChild(node)
      rows = rows :+ PlayerRow(color, node, input)
      refresh()
    }

    toggle.onclick = event => {
      event.preventDefault()
      if (menu.hasAttribute("hidden")) openMenu() else closeMenu()
    }
    menu.asInstanceOf[dom.html.Element].onkeydown = event => {
      val current = options.indexWhere(_ == dom.document.activeElement)
      event.key match {
        case "Escape" => event.preventDefault(); closeMenu(); toggle.focus()
        case "ArrowDown" if options.nonEmpty =>
          event.preventDefault(); options((current + 1) % options.size).focus()
        case "ArrowUp" if options.nonEmpty =>
          event.preventDefault(); options((current - 1 + options.size) % options.size).focus()
        case _ => ()
      }
    }

    LineageColors.take(MinPlayers).foreach(addRow)

    val create = button("Create game", "create-trusted-game")
    create.setAttribute("type", "submit")
    def submit(): Unit = {
      if (create.disabled) return
      val ids = rows.map(_.input.value.trim)
      val problem =
        if (rows.size < MinPlayers) Some(s"Need at least $MinPlayers players.")
        else rows.zip(ids).collectFirst {
          case (row, id) if !validPlayerId(id) =>
            s"${row.color.capitalize} player ID must start with a letter or digit and use only " +
              "letters, digits, '.', '_', ':' or '-' (up to 128 characters)."
        }.orElse(ids.diff(ids.distinct).headOption.map(id =>
          s"Player ID \"$id\" is used twice. Give each player a different ID."))
      problem match {
        case Some(message) => status.textContent = message; return
        case None => ()
      }
      val participants = rows.zip(ids).map { case (row, id) =>
        BootstrapParticipantRequest(id, lineageId(row.color), row.color)
      }
      val request = TrustedGameCreateRequest(gameId, participants)
      create.disabled = true
      status.textContent = "Creating game…"
      transport.request("POST", "/games", Some(TrustedGameCreateRequestCodec.encode(request)))
        .foreach { result =>
          val decoded = result.flatMap { response =>
            if (response.status >= 200 && response.status < 300)
              TrustedGameCreateResponseCodec.decode(response.body).left.map(error =>
                GameClientFailure.DecodeFailure(error.path, error.message))
            else if (response.status == 409) {
              gameId = freshGameId()
              Left(GameClientFailure.HttpFailure(409, "game-already-exists",
                "That game ID was already taken. A new one was generated; select Create game again."))
            }
            else Left(GameJson.responseFailure(response))
          }
          decoded match {
            case Left(error) => create.disabled = false; status.textContent = error.message
            case Right(created) =>
              mount.removeChild(form)
              status.textContent = "Game created. Copy and save each assigned seat link."
              mount.appendChild(text("p", "created-game-id", s"Game ID: ${created.gameId}"))
              val links = element("ol", "seat-links")
              created.seats.foreach { seat =>
                val row = element("li", "seat-link-row")
                row.appendChild(text("span", "", seat.playerId))
                val link = dom.document.createElement("input").asInstanceOf[dom.html.Input]
                link.className = "seat-link"
                link.value = seat.url
                link.readOnly = true
                link.setAttribute("aria-label", s"Seat link for ${seat.playerId}")
                row.appendChild(link)
                val copy = button("Copy link", "copy-seat-link")
                copy.onclick = _ => {
                  link.focus(); link.select()
                  status.textContent = "Link selected. Copy it to share with its player."
                  val clipboard = dom.window.navigator.asInstanceOf[js.Dynamic].selectDynamic("clipboard")
                  if (!js.isUndefined(clipboard) && clipboard != null)
                    clipboard.writeText(seat.url).asInstanceOf[js.Promise[Unit]].toFuture.foreach { _ =>
                      status.textContent = s"Copied seat link for ${seat.playerId}."
                    }
                }
                row.appendChild(copy)
                links.appendChild(row)
              }
              mount.appendChild(links)
          }
        }
    }
    create.onclick = event => { event.preventDefault(); submit() }
    form.asInstanceOf[dom.html.Form].onsubmit = event => { event.preventDefault(); submit() }
    form.appendChild(create)
    mount.appendChild(form)
    mount.appendChild(status)
  }

  private def swatch(color: String): dom.Element = {
    val node = element("span", s"color-swatch ${PlayerColorToken.fromKey(color).cssClass}")
    node.setAttribute("aria-hidden", "true")
    node
  }
}
