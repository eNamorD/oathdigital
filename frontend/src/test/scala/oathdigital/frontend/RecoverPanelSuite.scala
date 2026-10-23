package oathdigital.frontend

import oathdigital.protocol.projection.DieFace

import oathdigital.frontend.Dom.all
import oathdigital.testkit.Unwrap.as
import ParkedDecision.{RecoverStep, Surface}
import org.scalajs.dom

/** Recover's controls.
  *
  * The walker rolls as it walks and parks only on what it has to ask, so
  * one answer buys the next two dice and rolls them. The label says both,
  * because both happen on the click.
  */
class RecoverPanelSuite extends munit.FunSuite:
  /** The panel takes the routed surface; the projection rides along only
    * for the acting player's supply, which gates buying more dice.
    */
  private def render(surface: Surface.Recover, supply: Int = 5): dom.Element =
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderRecoverPanel(surface,
      TestProjections.actingProjection().copy(activePlayerResources = Some(
        ActivePlayerResources(favor = 0, faceUpSecrets = 0, faceDownSecrets = 0,
          committedSecrets = 0, totalSecrets = 0, supply = supply))),
      canControl = true, panel, new RecordingControls())
    panel

  private val choiceQuery = DecisionQueryState.ChooseOne(
    Vector(DecisionOptionState("button", "continue", "Continue"),
      DecisionOptionState("button", "stop", "Stop")),
    heading = Some("Recover"))

  private def choice(rollOutcome: Option[WalkerRollOutcomeState] = None)
      : Surface.Recover =
    Surface.Recover(WalkerDecisionState("recover",
      ParkedDecision.recoverChoiceDecisionId,
      query = Some(choiceQuery), rollOutcome = rollOutcome),
      RecoverStep.Choice(choiceQuery))

  /** One click buys the dice and throws them, so the label names both the
    * cost and the roll rather than promising a second button.
    */
  test("continuing says it buys dice and rolls them"):
    val panel = render(choice())
    assertEquals(all(panel, ".recover-add").map(_.textContent),
      Vector("Roll two more dice (1 Supply)"))
    assertEquals(all(panel, ".recover-stop").map(_.textContent),
      Vector("Stop Recover"))

  test("without the supply to buy them, the control is refused"):
    assert(as[dom.html.Button](
      all(render(choice(), supply = 0), ".recover-add").head).disabled)
    // The label costs 1 Supply, so 1 is enough.
    assert(!as[dom.html.Button](
      all(render(choice(), supply = 1), ".recover-add").head).disabled)

  test("Recover shows its dice and how close the score is to the target"):
    val outcome = WalkerRollOutcomeState("recover",
      Vector(DieFace.OneShield, DieFace.TwoShields), 3, Some(4),
      Vector.empty)
    val panel = render(choice(rollOutcome = Some(outcome)))
    assertEquals(all(panel, ".walker-roll-totals").map(_.textContent),
      Vector("Shields 3 · need 4"))
    assertEquals(all(panel, ".walker-roll-faces").map(_.getAttribute("aria-label")),
      Vector("Rolled shield, two shields -- 3 shields so far (need 4)."))
    assertEquals(all(panel, ".walker-roll-faces .die-faces").size, 1)

  test("with no dice rolled yet, Recover shows the target and no dice"):
    val outcome = WalkerRollOutcomeState("recover", Vector.empty, 0, Some(4))
    val panel = render(choice(rollOutcome = Some(outcome)))
    assertEquals(all(panel, ".walker-roll-faces").size, 0)
    assertEquals(all(panel, ".walker-roll-totals").map(_.textContent),
      Vector("Shields 0 · need 4"))
