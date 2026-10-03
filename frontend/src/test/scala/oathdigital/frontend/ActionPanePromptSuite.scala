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
  private val context = BoardSelectionContext("game", "red", 1L)
  private val travel = BoardTargetAction("travel", "Choose a destination",
    minimum = 1, maximum = 1, autoActivate = true, Vector(BoardTargetCandidate(
      BoardTargetRef.Site("site:woods"), "Deep Woods", Vector.empty)))
  private def projection(open: Boolean,
      offered: Vector[BoardTargetAction]): GameProjection =
    actingProjection(
      players = Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)))
      .copy(actionSelectionOpen = open, boardTargetActions = offered)
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

  test("the prompt is the active board target's instruction"):
    val drafts = SessionDrafts.empty.copy(boardTargets = Some(
      BoardTargetSelectionState(context, Vector(travel), Some("travel"), Set.empty)))
    val result = pane(projection(open = false, offered = Vector(travel)),
      drafts)
    assertEquals(result.prompt, "Choose a destination")
    assertEquals(result.prompt, scraped(result.element))

  test("the prompt is the modifier confirm while ordering"):
    val draft = ModifierFlowDraft(command = None, actionKind = Some("travel"),
      baseParameters = Map.empty,
      preview = MajorActionPreviewResponse(nextSequence = 1L, action = "travel",
        modifiers = Vector.empty, ignoredRules = Vector.empty,
        targets = Vector.empty),
      selection = ModifierSelectionState.reconcile(None,
        ModifierSelectionContext("game", "red", 1L, "travel"), Vector.empty, "p"),
      stage = ModifierFlowStage.Ordering)
    val result = pane(projection(open = true, offered = Vector(travel)),
      SessionDrafts.empty.copy(modifiers = Some(draft)))
    assertEquals(result.prompt, "Confirm modifier order")
    assertEquals(result.prompt, scraped(result.element))

  test("no question in flight is an empty prompt"):
    // A Travel target is offered but not activated, so no question is asked.
    val result = pane(projection(open = true, offered = Vector(travel)),
      SessionDrafts.empty)
    assertEquals(result.prompt, "")
    assertEquals(result.prompt, scraped(result.element))
