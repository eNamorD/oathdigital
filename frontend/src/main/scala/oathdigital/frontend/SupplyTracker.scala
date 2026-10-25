package oathdigital.frontend

import oathdigital.model.PlayerColor
import org.scalajs.dom
import ServerUiSupport._

/** The Supply track as the physical board prints it: a row of spaces, one
  * more than the most Supply a player may hold, with the marker on the
  * space whose count of spaces to its right is the player's Supply. The
  * leftmost space is the most Supply, starred; each Rest band is printed on
  * the space Rest sets the marker to, so one row says both what the player
  * holds and what each band returns.
  *
  * Every number is the server's; the track only places them.
  */
private[frontend] object SupplyTracker:
  /** One board's track. `banked` is the warbands in that player's bank,
    * which picks the band Rest reads. */
  final case class Track(bands: Vector[SupplyBand], supply: Int, banked: Int,
      maximum: Int, color: PlayerColor)

  def render(track: Track): dom.Element =
    val row = element("ol", "supply-track")
    row.setAttribute("aria-label", "Supply track")
    (track.maximum to 0 by -1).foreach { supply =>
      val band = track.bands.find(_.supply == supply)
      val current = band.exists(holds(_, track.banked))
      val marked = supply == track.supply
      val space = element("li", "supply-space")
      if current then space.classList.add("supply-space-current")
      space.setAttribute("aria-label", (Vector(s"Supply $supply") ++
        band.map(value => s"${spokenRange(value)} warbands in bank") ++
        Option.when(current)("current band") ++
        Option.when(marked)("marker")).mkString(", "))
      // The space's name says all of it, so what it draws is not read twice.
      (Option.when(supply == track.maximum)(text("span", "supply-star", "★")) ++
        band.map(printedRange) ++
        Option.when(marked)(element("span",
          s"supply-marker supply-marker-${track.color.key}")))
        .foreach { part =>
          part.setAttribute("aria-hidden", "true")
          space.appendChild(part)
        }
      row.appendChild(space)
    }
    row

  private def holds(band: SupplyBand, banked: Int): Boolean =
    band.from <= banked && band.to.forall(banked <= _)

  private def spokenRange(band: SupplyBand): String =
    band.to.fold(s"${band.from} or more")(to => s"${band.from} to $to")

  /** High to low, as printed: "9+", "8 to 4", over the warband glyph. */
  private def printedRange(band: SupplyBand): dom.Element =
    val label = element("span", "supply-range")
    val figures = element("span", "supply-range-figures")
    band.to match
      case None => figures.appendChild(dom.document.createTextNode(s"${band.from}+"))
      case Some(to) =>
        figures.appendChild(dom.document.createTextNode(to.toString))
        figures.appendChild(text("span", "supply-range-to", "to"))
        figures.appendChild(dom.document.createTextNode(band.from.toString))
    label.appendChild(figures)
    label.appendChild(RulesTextRenderer.glyph("warband"))
    label
