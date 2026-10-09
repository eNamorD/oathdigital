package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand}
import org.scalajs.dom
import ServerUiSupport.{button, element, text}

/** The viewer's consent preferences for one power they rule (ruling 16):
  * a row per other player, and what choosing a setting does. Without
  * control (`choose` is `None`) the grid only shows the settings. */
private[frontend] final case class ConsentGrid(rows: Vector[ConsentGridRow],
    choose: Option[(String, String) => Unit])
private[frontend] final case class ConsentGridRow(playerId: String,
    name: String, setting: String)

/** What a site power's label opens (consent design, "Frontend"): the
  * power's printed text, and the viewer's grid when they rule the power.
  * `submit` sends a preference, and is `None` without control. */
private[frontend] final class SitePowerLinks(value: Option[GameProjection],
    submit: Option[GameCommand => Unit]):
  def grid(power: SitePower): Option[ConsentGrid] = for
    projection <- value
    preferences <- projection.consentPreferences.find(_.powerId == power.id)
  yield ConsentGrid(preferences.rows.map(row => ConsentGridRow(row.playerId,
      ConsentAsking.name(projection, row.playerId), row.setting)),
    submit.map(send => (requester, setting) =>
      send(GameCommand.SetConsentPreference(power.id, requester, setting))))

private[frontend] object SitePowerLinks:
  val none: SitePowerLinks = new SitePowerLinks(None, None)
  def of(value: GameProjection, submit: Option[GameCommand => Unit])
      : SitePowerLinks =
    new SitePowerLinks(Some(value), submit)

/** The grid as the overlay draws it. A choice is shown at once and sent; the
  * overlay ignores polls while open, so it keeps what was chosen, and a
  * choice already shown sends nothing. */
private[frontend] object ConsentGridView:
  private val settings = Vector("never" -> "Never Allow",
    "case-by-case" -> "Case-by-case", "always" -> "Always Allow")

  def render(grid: ConsentGrid): dom.Element =
    val section = element("section", "consent-grid")
    section.appendChild(text("h4", "consent-grid-heading",
      "Consent preferences"))
    grid.rows.foreach { row =>
      val line = element("div", "consent-grid-row")
      line.setAttribute("data-player-id", row.playerId)
      line.appendChild(text("span", "consent-grid-player", row.name))
      val choices = settings.map { (key, label) =>
        val choice = button(label, "consent-grid-choice")
        choice.setAttribute("data-setting", key)
        choice.setAttribute("aria-pressed", (key == row.setting).toString)
        choice.disabled = grid.choose.isEmpty
        key -> choice
      }
      choices.foreach { (key, choice) =>
        choice.onclick = _ =>
          if choice.getAttribute("aria-pressed") != "true" then
            choices.foreach((_, other) => other.setAttribute("aria-pressed",
              (other eq choice).toString))
            grid.choose.foreach(_(row.playerId, key))
        line.appendChild(choice)
      }
      section.appendChild(line)
    }
    section
