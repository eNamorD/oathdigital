package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport.{button, element, text}

/** A phase menu as grouped buttons (walker-driven game loop design, slice
  * 4, plan-time rulings 6 and 12): any button that is neither an action,
  * a power nor the ending, without a heading; then the major actions, the
  * minor actions and the powers, each under its heading; then the phase's
  * ending, alone. Each option says which band it is in (`OptionGroup`, on
  * the wire as `group`); the options and their order are the engine's.
  */
private[frontend] object MenuPanel:
  /** The menus' decision id (`PhaseMenu.decisionId`), which routes a parked
    * menu to this panel. The engine owns the spelling; this is the only
    * menu vocabulary left in the client (decision-id vocabulary decision,
    * item 2). */
  val decisionId: String = "phase.menu"

  /** The bands, in the order they are read, with the heading each is given.
    * The engine says which band an option is in (`OptionGroup`); the order
    * and the words are this panel's. An option whose band is absent or
    * unknown is read in the first, unheaded band, where the engine's own
    * ungrouped options go. */
  private val bands: Vector[(String, Option[String])] = Vector(
    "other" -> None, "major" -> Some("Major actions"),
    "minor" -> Some("Minor actions"), "power" -> Some("Powers"))

  private val known = bands.map(_._1).toSet + "ending"

  private def band(option: DecisionOptionState): String =
    option.group.filter(known).getOrElse("other")

  def render(surface: ParkedDecision.Surface.Menu, canControl: Boolean,
      panel: dom.Element, controls: TableControls): Unit =
    val options = surface.query.options
    def control(option: DecisionOptionState, style: String): dom.Element =
      val row = element("div", "action-option")
      val choose = button(option.label,
        s"walker-choice menu-option $style".trim)
      // What the pick costs, after its name, its resources drawn as glyphs
      // and named in words for a reader who cannot see them.
      option.badge.foreach { badge =>
        val cost = element("span", "menu-option-cost")
        cost.appendChild(dom.document.createTextNode(" ("))
        RulesTextRenderer.inline(badge, cost)
        cost.appendChild(dom.document.createTextNode(")"))
        choose.appendChild(cost)
        choose.setAttribute("aria-label",
          s"${option.label} (${RulesTextRenderer.spoken(badge)})")
      }
      choose.disabled = !canControl
      option.card.flatMap(_.rulesText)
        .foreach(HoverTip.set(choose, _))
      choose.onclick = _ => controls.submitCommand(
        WalkerPanelSupport.resolveChooseOneCommand(surface.decision, option))
      row.appendChild(choose)
      WalkerPanelSupport.withholds(choose, option)
      row
    def group(key: String, heading: Option[String],
        members: Vector[DecisionOptionState]): Unit =
      if members.nonEmpty then
        val section = element("section",
          s"available-action-group action-group-$key")
        heading.foreach(words =>
          section.appendChild(text("h3", "action-group-heading", words)))
        members.foreach(option => section.appendChild(control(option, "")))
        panel.appendChild(section)
    panel.appendChild(text("h2", "", WalkerPanelSupport.decisionHeading(
      surface.query)))
    val (ending, listed) = options.partition(band(_) == "ending")
    bands.foreach((key, heading) =>
      group(key, heading, listed.filter(band(_) == key)))
    ending.foreach(option => panel.appendChild(control(option,
      "menu-ending")))
