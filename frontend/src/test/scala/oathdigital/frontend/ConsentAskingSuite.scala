package oathdigital.frontend

import oathdigital.frontend.Dom.click
import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => Intent, WalkerStartArgWire}
import ParkedDecision.Surface
import org.scalajs.dom

/** An askable option asks before it sends (consent design, user story steps
  * 1 and 2, "Frontend"). */
class ConsentAskingSuite extends munit.FunSuite:
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val blue = GamePlayer("blue", "Blue", "Exile", PlayerColor.Blue)
  private val table = TestProjections.actingProjection(
    players = Vector(red, blue), activePlayer = Some("blue"))
  private val ask = ConsentAskState(Vector("red"), Vector("Narrow Pass"))
  private val reason = "Narrow Pass: Cannot travel to other sites in the " +
    "region without ruler's consent."
  private val woods = DecisionOptionState("site", "site:deep-woods",
    "Deep Woods", state = "askable", reason = Some(reason),
    consent = Some(ask))
  private val query = DecisionQueryState.ChooseOne(Vector(woods))
  private val decision = WalkerDecisionState("travel", "travel.destination",
    "decide", query = Some(query), presentation = "board")
  private val requested = Intent.RequestConsent("travel.destination",
    WalkerStartArgWire("site", "site:deep-woods"))
  private var opened = Vector.empty[CardInspection.Request]

  override def beforeEach(context: BeforeEach): Unit =
    opened = Vector.empty
    CardInspection.onOpen(request => opened :+= request)

  override def afterEach(context: AfterEach): Unit = CardInspection.clear()

  private def confirmation(using munit.Location): CardInspection.Request.Confirm =
    opened match
      case Vector(confirm: CardInspection.Request.Confirm) => confirm
      case other => fail(s"expected one confirmation, got $other")

  test("the question names the granters and the powers"):
    assertEquals(ConsentAsking.question(table, ask),
      "Ask Red for consent to ignore Narrow Pass?")
    assertEquals(ConsentAsking.listed(Vector("Red", "Green", "White")),
      "Red, Green and White")
    assertEquals(ConsentAsking.listed(Vector("Red", "Green")), "Red and Green")

  test("on the board an askable option is amber, says why, and asks " +
      "before it sends"):
    val ui = new RecordingControls()
    val views = BoardOptionViews.of(decision, query, None)
    assertEquals(views.get(BoardOptionViews.key(woods)).map(_.state),
      Some(OptionState.Askable))
    val options = new BoardOptions(views,
      _ => fail("an askable option is never picked"),
      ConsentAsking.asker("travel.destination", table, canControl = true, ui))
    val box = dom.document.createElement("article")
    options.decorate(box, Vector(BoardOptionViews.key(woods)))
    assert(box.classList.contains("board-option-askable"))
    assertEquals(box.getAttribute("title"), reason)
    click(box)
    assertEquals(confirmation.question,
      "Ask Red for consent to ignore Narrow Pass?")
    assertEquals(ui.submitted, Vector.empty)
    confirmation.confirm()
    assertEquals(ui.submitted, Vector(requested))

  test("without control, an askable option asks nothing"):
    val ui = new RecordingControls()
    ConsentAsking.asker("travel.destination", table, canControl = false, ui)(
      woods, dom.document.createElement("button")
        .asInstanceOf[dom.html.Element])
    assertEquals(opened, Vector.empty)

  test("in the panel an askable option is an enabled amber button that " +
      "asks"):
    val ui = new RecordingControls()
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(Surface.ChooseOne(
      decision.copy(presentation = "panel"), query), table, canControl = true,
      panel, ui)
    val control = panel.querySelector(".walker-choice")
      .asInstanceOf[dom.html.Button]
    assert(control.classList.contains("walker-choice-askable"))
    assert(!control.disabled)
    assertEquals(control.getAttribute("title"), reason)
    control.click()
    confirmation.confirm()
    assertEquals(ui.submitted, Vector(requested))

  test("the overlay's Yes runs the question's action and closes; No only " +
      "closes"):
    val root = dom.document.createElement("div")
    dom.document.body.appendChild(root)
    val overlay = new CardInspectionOverlay(root)
    val opener = dom.document.createElement("button")
      .asInstanceOf[dom.html.Element]
    dom.document.body.appendChild(opener)
    var confirmed = 0
    overlay.showConfirm("Ask Red?", () => confirmed += 1, opener)
    root.querySelector(".overlay-confirm-yes").asInstanceOf[dom.html.Button]
      .click()
    assert(!overlay.isOpen)
    assertEquals(confirmed, 1)
    overlay.showConfirm("Ask Red?", () => confirmed += 1, opener)
    root.querySelector(".overlay-confirm-no").asInstanceOf[dom.html.Button]
      .click()
    assert(!overlay.isOpen)
    assertEquals(confirmed, 1)
    overlay.dispose()
    root.remove()
    opener.remove()
