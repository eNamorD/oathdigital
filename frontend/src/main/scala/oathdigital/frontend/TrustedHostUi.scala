package oathdigital.frontend

import oathdigital.protocol._
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import ServerUiSupport._

/** Seat links live only in this creation result; the host distributes them manually. */
private[frontend] object TrustedHostUi {
  def start(mount: dom.Element, transport: JsonTransport): Unit = {
    mount.textContent = ""
    mount.appendChild(text("h1", "", "Create an Oath Digital game"))
    mount.appendChild(text("p", "", "Create one seat per player, then send each player " +
      "their assigned link. Anyone with a seat link can control that seat. " +
      "Save these links before leaving this page."))
    val form = element("form", "trusted-host-form")
    def field(label: String, initial: String): dom.html.Input = {
      val wrapper = element("label", "host-field")
      wrapper.appendChild(text("span", "", label))
      val input = dom.document.createElement("input").asInstanceOf[dom.html.Input]
      input.setAttribute("aria-label", label)
      input.value = initial
      input.required = true
      wrapper.appendChild(input)
      form.appendChild(wrapper)
      input
    }
    val game = field("Game ID", freshGameId())
    val seatsLabel = element("label", "host-field")
    seatsLabel.appendChild(text("span", "", "Seat definitions"))
    val seats = dom.document.createElement("textarea").asInstanceOf[dom.html.TextArea]
    seats.setAttribute("aria-label", "Seat definitions")
    seats.rows = 4
    seats.value = "red-exile,red-lineage,red\nblue-exile,blue-lineage,blue\nyellow-exile,yellow-lineage,yellow"
    seats.required = true
    seatsLabel.appendChild(seats)
    form.appendChild(seatsLabel)
    form.appendChild(text("p", "", "One seat per line: player ID, lineage ID, color. " +
      "Colors: red, blue, yellow, white, black."))
    val first = field("First player ID", "red-exile")
    val create = button("Create game", "create-trusted-game")
    create.setAttribute("type", "submit")
    val status = text("p", "status", "")
    status.setAttribute("role", "status")
    def submit(): Unit = {
      if (create.disabled) return
      val rows = seats.value.split("\n").toVector.filter(_.trim.nonEmpty)
        .map(_.split(",", -1).toVector.map(_.trim))
      if (game.value.trim.isEmpty || first.value.trim.isEmpty || rows.isEmpty ||
          rows.exists(row => row.size != 3 || row.exists(_.isEmpty))) {
        status.textContent = "Enter a game ID, first player ID, and three values for each seat."
        return
      }
      val participants = rows.map(row => BootstrapParticipantRequest(row(0), row(1), row(2)))
      val request = TrustedGameCreateRequest(game.value.trim, participants, first.value.trim)
      create.disabled = true
      status.textContent = "Creating game…"
      transport.request("POST", "/games", Some(TrustedGameCreateRequestCodec.encode(request)))
        .foreach { result =>
          val decoded = result.flatMap { response =>
            if (response.status >= 200 && response.status < 300)
              TrustedGameCreateResponseCodec.decode(response.body).left.map(error =>
                GameClientFailure.DecodeFailure(error.path, error.message))
            else if (response.status == 409) Left(GameClientFailure.HttpFailure(409,
              "game-already-exists", "Choose another game ID; this one already exists."))
            else Left(GameJson.responseFailure(response))
          }
          decoded match {
            case Left(error) => create.disabled = false; status.textContent = error.message
            case Right(created) =>
              mount.removeChild(form)
              status.textContent = "Game created. Copy and save each assigned seat link."
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
}
