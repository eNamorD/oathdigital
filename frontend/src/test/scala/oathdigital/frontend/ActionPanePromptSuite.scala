package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.MajorActionPreviewResponse
import TestProjections.actingProjection
import org.scalajs.dom

/** The prompt the action pane reports is the text the old DOM scrape
  * found, so the decision key `ServerModeUi` composes is the same string it
  * was. That scrape had four selectors; two of them (`#card-decision-title`,
  * `.resolution-choice`) matched nothing, so these cases compare against
  * the two live ones, `.selection-instruction,.modifier-confirm`.
  */
class ActionPanePromptSuite extends munit.FunSuite:
  private def projection(open: Boolean): GameProjection =
    actingProjection(
      players = Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)))
      .copy(actionSelectionOpen = open)
  private val presentation = ServerUiSupport.ViewerPresentation(
    showGameplayControls = true, None, None, playerId = "red")
  private def pane(value: GameProjection, drafts: SessionDrafts)
      : ActionDecisionRenderer.ActionPane =
    ActionDecisionRenderer.actionsPanel(value, presentation,
      ParkedDecision.Routed(None, None), canControl = true, drafts,
      new RecordingControls())
  private def scraped(element: dom.Element): String =
    Option(element.querySelector(".selection-instruction,.modifier-confirm"))
      .map(_.textContent).getOrElse("")

  test("the prompt is the modifier confirm while ordering"):
    val draft = ModifierFlowDrafts.orderingDraft(1L).copy(
      preview = MajorActionPreviewResponse(nextSequence = 1L,
        action = "travel", modifiers = Vector.empty,
        ignoredRules = Vector.empty))
    val result = pane(projection(open = true),
      SessionDrafts.empty.copy(modifiers = Some(draft)))
    assertEquals(result.prompt, "Confirm modifier order")
    assertEquals(result.prompt, scraped(result.element))

  test("no question in flight is an empty prompt"):
    val result = pane(projection(open = true), SessionDrafts.empty)
    assertEquals(result.prompt, "")
    assertEquals(result.prompt, scraped(result.element))
