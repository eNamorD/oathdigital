package oathdigital.frontend

import oathdigital.frontend.Dom.all
import oathdigital.testkit.Unwrap.as
import ParkedDecision.Surface
import org.scalajs.dom

/** A phase menu drawn as grouped buttons (walker-driven game loop design,
  * slice 4, plan-time rulings 6 and 12). Runs under jsdom. */
class MenuPanelSuite extends munit.FunSuite:
  private def button(id: String, label: String,
      badge: Option[String] = None) =
    DecisionOptionState("button", id, label, None, Vector.empty, badge)

  private val search = button("search", "Search", Some("2 Supply"))
  private val recover = button("recover", "Recover").copy(
    state = "unavailable", reason = Some("Needs 1 Supply; you have 0"))
  private val peek = button("peek-site-relics", "Peek at relics at your site")
  private val power = DecisionOptionState("denizen", "97", "Insomnia",
    Some(CardDetails("97", "denizen", "Insomnia",
      rulesText = Some("**ACT:** Gain [secret]."))))
  private val flame = button("use-power:flame.move", "Wandering Flame: move")
  private val ending = button("end-act", "End Act and Rest (+5 Supply)")

  private def draw(options: Vector[DecisionOptionState],
      ui: RecordingControls = new RecordingControls()): dom.Element =
    val query = DecisionQueryState.ChooseOne(options,
      heading = Some("Act: take an action, use a power or rest"))
    val panel = dom.document.createElement("div")
    MenuPanel.render(Surface.Menu(WalkerDecisionState("act-menu",
      "phase.menu", "decide", query = Some(query)), query),
      canControl = true, panel, ui)
    panel

  test("the options are grouped, and the ending comes last"):
    val panel = draw(Vector(search, recover, peek, power, ending))
    assertEquals(all(panel, "h2").map(_.textContent),
      Vector("Act: take an action, use a power or rest"))
    assertEquals(all(panel, ".action-group-heading").map(_.textContent),
      Vector("Major actions", "Minor actions", "Powers"))
    assertEquals(all(panel, ".menu-option").map(_.textContent), Vector(
      "Search (2 Supply)", "Recover", "Peek at relics at your site",
      "Insomnia", "End Act and Rest (+5 Supply)"))
    assertEquals(all(panel, ".menu-ending").map(_.textContent),
      Vector("End Act and Rest (+5 Supply)"))

  test("an unavailable option is withheld and says why only in its note"):
    val panel = draw(Vector(recover, ending))
    val shown = as[dom.html.Button](all(panel, ".menu-option").head)
    assertEquals(shown.getAttribute("aria-disabled"), "true")
    assertEquals(shown.getAttribute("data-tip"), "Needs 1 Supply; you have 0")
    assert(!panel.textContent.contains("Needs 1 Supply"))

  test("a power shows its rules text on hover"):
    val panel = draw(Vector(power, ending))
    assertEquals(as[dom.html.Button](all(panel, ".menu-option").head)
      .getAttribute("data-tip"), "**ACT:** Gain [secret].")

  test("a power's own button sits under the Powers heading"):
    val panel = draw(Vector(search, flame, ending))
    assertEquals(all(panel, ".action-group-heading").map(_.textContent),
      Vector("Major actions", "Powers"))
    assertEquals(all(panel, ".action-group-power .menu-option")
      .map(_.textContent), Vector("Wandering Flame: move"))

  test("a Wake menu's Take Wealth buttons have no heading"):
    val panel = draw(Vector(button("take-wealth.favor",
      "Take Wealth: 1 favor"), button("end-wake", "End Wake")))
    assertEquals(all(panel, ".action-group-heading").size, 0)
    assertEquals(all(panel, ".menu-option").map(_.textContent),
      Vector("Take Wealth: 1 favor", "End Wake"))

  test("a pick submits the menu's answer"):
    val ui = new RecordingControls()
    as[dom.html.Button](all(draw(Vector(search, ending), ui),
      ".menu-option").head).click()
    assertEquals(ui.submitted, Vector(
      oathdigital.protocol.GameIntent.ResolveWalker("phase.menu",
        oathdigital.protocol.DecisionAnswerWire.ChooseOneWire("button",
          "search"))))
