package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport.{button, element, text}

/** A phase menu as grouped buttons (walker-driven game loop design, slice
  * 4, plan-time rulings 6 and 12): any button that is neither an action,
  * a power nor the ending, without a heading; then the major actions, the
  * minor actions and the powers, each under its heading; then the phase's
  * ending, alone. The groups are read off the option ids the engine
  * declares; the options and their order are the engine's.
  */
private[frontend] object MenuPanel:
  /** The menus' decision id (`PhaseMenu.decisionId`). */
  val decisionId: String = "phase.menu"

  private val majors = Set("search", "travel", "campaign", "muster",
    "trade.favor", "trade.secret", "forge", "recover", "challenge")
  private val minors = Set("place-banner-resource", "negotiation",
    "play-facedown-adviser", "peek-site-relics", "reveal-owned-relic",
    "move-warbands")
  private val endings = Set("end-wake", "finish-rest", "end-act")

  /** The prefix of a power's own button, listed when its source offers two
    * powers of one phase (`PhaseMenu.powerButton`). */
  private val powerPrefix = "use-power:"

  private def isButton(option: DecisionOptionState, ids: Set[String])
      : Boolean = option.kind == "button" && ids(option.id)

  private def isPower(option: DecisionOptionState): Boolean =
    option.kind != "button" || option.id.startsWith(powerPrefix)

  def render(surface: ParkedDecision.Surface.Menu, canControl: Boolean,
      panel: dom.Element, controls: TableControls): Unit =
    val options = surface.query.options
    def control(option: DecisionOptionState, style: String): dom.Element =
      val row = element("div", "action-option")
      val label = option.badge.fold(option.label)(badge =>
        s"${option.label} ($badge)")
      val choose = button(label, s"walker-choice menu-option $style".trim)
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
    val (ending, listed) = options.partition(isButton(_, endings))
    group("other", None, listed.filter(option => option.kind == "button" &&
      !majors(option.id) && !minors(option.id) && !isPower(option)))
    group("major", Some("Major actions"), listed.filter(isButton(_, majors)))
    group("minor", Some("Minor actions"), listed.filter(isButton(_, minors)))
    group("power", Some("Powers"), listed.filter(isPower))
    ending.foreach(option => panel.appendChild(control(option,
      "menu-ending")))
