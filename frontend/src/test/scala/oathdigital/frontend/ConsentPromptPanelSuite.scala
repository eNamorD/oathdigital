package oathdigital.frontend

import oathdigital.frontend.Dom.all
import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => Intent}
import oathdigital.protocol.projection.WalkerWaitingProjection
import org.scalajs.dom

/** The asked ruler's prompt and the requester's wait (consent design, user
  * story steps 3 to 6). Red rules the Pass; Blue asks. */
class ConsentPromptPanelSuite extends munit.FunSuite:
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val blue = GamePlayer("blue", "Blue", "Exile", PlayerColor.Blue)
  private val table = TestProjections.actingProjection(
    players = Vector(red, blue), activePlayer = Some("blue"))
  private val prompted = table.copy(
    walkerWaiting = Some(WalkerWaitingProjection("blue",
      Some("Choose a Travel destination"))),
    consentPrompt = Some(ConsentPromptState("blue", Vector("Narrow Pass"),
      "Deep Woods")))
  private val waiting = table.copy(
    consentWaiting = Some(ConsentWaitingState(Vector("red"))))

  private def pane(value: GameProjection, viewer: String,
      ui: RecordingControls, canControl: Boolean = true): dom.Element =
    val panel = dom.document.createElement("div")
    val presentation = ServerUiSupport.viewerPresentation(value, viewer)
    ParkedDecision.render(value, presentation,
      ParkedDecision.route(value, presentation), canControl, panel,
      SessionDrafts.empty, ui)
    panel

  test("the asked ruler reads the question, four answers and where to " +
      "change preferences, and no waiting line"):
    val panel = pane(prompted, "red", new RecordingControls())
    assertEquals(panel.querySelector(".consent-question").textContent,
      "Give Blue consent to ignore Narrow Pass and select Deep Woods?")
    assertEquals(all(panel, ".consent-answer").map(_.textContent),
      Vector("Never Allow", "No", "Yes", "Always Allow"))
    assertEquals(panel.querySelector(".consent-notice").textContent,
      "You can adjust your consent preferences by clicking on the Narrow " +
        "Pass power.")
    assertEquals(all(panel, ".walker-waiting"), Vector.empty)

  test("each answer sends its choice"):
    val ui = new RecordingControls()
    val panel = pane(prompted, "red", ui)
    all(panel, ".consent-answer").foreach(_.asInstanceOf[dom.html.Button]
      .click())
    assertEquals(ui.submitted, Vector("never", "no", "yes", "always")
      .map(Intent.AnswerConsent(_)))

  test("without control the answers are disabled"):
    val panel = pane(prompted, "red", new RecordingControls(),
      canControl = false)
    assert(all(panel, ".consent-answer")
      .forall(_.asInstanceOf[dom.html.Button].disabled))

  test("the requester waits on either granter's consent while any one " +
      "will do"):
    val either = table.copy(consentWaiting = Some(ConsentWaitingState(
      Vector("red", "blue"), either = true)))
    val panel = pane(either, "blue", new RecordingControls())
    assertEquals(all(panel, ".walker-waiting").map(_.textContent),
      Vector("Waiting for Red's or Blue's consent…"))

  test("the requester waits on the ruler's consent"):
    val panel = pane(waiting, "blue", new RecordingControls())
    assertEquals(all(panel, ".walker-waiting").map(_.textContent),
      Vector("Waiting for Red's consent…"))
    assertEquals(all(panel, ".consent-prompt"), Vector.empty)
