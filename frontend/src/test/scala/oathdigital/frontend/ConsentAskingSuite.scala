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
    query = Some(query), presentation = "board")
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
    assertEquals(box.getAttribute("data-tip"), reason)
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
    assertEquals(control.getAttribute("data-tip"), reason)
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

  test("in a choose-many on the board an askable target asks, is never " +
      "picked, and the other targets still pick"):
    val peaks = DecisionOptionState("site", "site:broken-peaks",
      "Broken Peaks")
    val targets = DecisionQueryState.ChooseMany(Vector(peaks, woods), 0, 2)
    val conquest = WalkerDecisionState("campaign", "campaign.targets",
      query = Some(targets), presentation = "board")
    val ui = new RecordingControls()
    var picked = Vector.empty[String]
    val views = BoardOptionViews.of(conquest, targets, None)
    assertEquals(views.get(BoardOptionViews.key(woods)).map(_.state),
      Some(OptionState.Askable))
    val options = new BoardOptions(views, option => picked :+= option.id,
      ConsentAsking.asker("campaign.targets", table, canControl = true, ui))
    val woodsBox = dom.document.createElement("article")
    val peaksBox = dom.document.createElement("article")
    options.decorate(woodsBox, Vector(BoardOptionViews.key(woods)))
    options.decorate(peaksBox, Vector(BoardOptionViews.key(peaks)))
    click(woodsBox)
    confirmation.confirm()
    assertEquals(ui.submitted, Vector(Intent.RequestConsent(
      "campaign.targets", WalkerStartArgWire("site", "site:deep-woods"))))
    click(peaksBox)
    assertEquals(picked, Vector("site:broken-peaks"))
    // A draft never holds it either.
    val draft = BoardSelectionDraft(BoardSelectionContext("g", "blue", 1L),
      "campaign.targets", targets, Vector.empty)
    assertEquals(draft.click(woods).keys, Vector.empty)

  /** An ask any one of whose two granters will do: Red agreeing to pay
    * Toll Roads, or Blue waiving it. */
  private val eitherRedOrBlue = ConsentAskState(Vector.empty, Vector.empty,
    Vector(ConsentAlternativeState("red", Vector("Toll Roads"),
      paying = true),
      ConsentAlternativeState("blue", Vector("Toll Roads"), paying = false)))

  test("an ask with alternatives offers one button each, then both"):
    val choices = ConsentAsking.choices(table, eitherRedOrBlue)
    assertEquals(choices.map(_.label), Vector("Ask Red", "Ask Blue",
      "Ask both"))
    assertEquals(choices.map(_.granters),
      Vector(Vector("red"), Vector("blue"), Vector.empty))
    assertEquals(ConsentAsking.question(table, eitherRedOrBlue),
      "Ask Red to pay for Toll Roads or Blue to waive it?")

  test("an askable option with alternatives asks whom, and sends that " +
      "choice"):
    val ui = new RecordingControls()
    ConsentAsking.asker("travel.destination", table, canControl = true, ui)(
      woods.copy(consent = Some(eitherRedOrBlue)),
      dom.document.createElement("button").asInstanceOf[dom.html.Element])
    val choices = opened match
      case Vector(found: CardInspection.Request.Choices) => found.choices
      case other => fail(s"expected one choice dialog, got $other")
    assertEquals(choices.map(_._1), Vector("Ask Red", "Ask Blue", "Ask both"))
    choices(1)._2()
    assertEquals(ui.submitted, Vector(requested.copy(granters =
      Vector("blue"))))

  test("the overlay's choices each run and close; Cancel only closes"):
    val root = dom.document.createElement("div")
    dom.document.body.appendChild(root)
    val overlay = new CardInspectionOverlay(root)
    val opener = dom.document.createElement("button")
      .asInstanceOf[dom.html.Element]
    dom.document.body.appendChild(opener)
    var ran = Vector.empty[String]
    overlay.showChoices("Ask whom?", Vector("Ask Red" -> (() => ran :+= "red"),
      "Ask Blue" -> (() => ran :+= "blue")), opener)
    val buttons = root.querySelectorAll(".overlay-confirm-choice")
    assertEquals(buttons.length, 2)
    buttons(1).asInstanceOf[dom.html.Button].click()
    assert(!overlay.isOpen)
    assertEquals(ran, Vector("blue"))
    overlay.showChoices("Ask whom?", Vector("Ask Red" -> (() => ran :+= "red")),
      opener)
    root.querySelector(".overlay-confirm-no").asInstanceOf[dom.html.Button]
      .click()
    assert(!overlay.isOpen)
    assertEquals(ran, Vector("blue"))
    overlay.dispose()
    root.remove()
    opener.remove()

  test("a granter asked to pay is asked to pay, after any ask to ignore"):
    val payer = ConsentAlternativeState("red", Vector("Toll Roads"),
      paying = true)
    assertEquals(ConsentAsking.question(table, ConsentAskState(Vector.empty,
      Vector.empty, payers = Vector(payer))),
      "Ask Red to pay for Toll Roads?")
    assertEquals(ConsentAsking.question(table, ConsentAskState(
      Vector("blue"), Vector("Narrow Pass"), payers = Vector(payer))),
      "Ask Blue for consent to ignore Narrow Pass and Red to pay for Toll " +
        "Roads?")
