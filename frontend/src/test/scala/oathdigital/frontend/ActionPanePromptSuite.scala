package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.MajorActionPreviewResponse
import org.scalajs.dom

/** The prompt the action pane reports is the text the old DOM scrape
  * found, so the decision key `ServerModeUi` composes is the same string it
  * was. That scrape had four selectors; two of them (`#card-decision-title`,
  * `.resolution-choice`) matched nothing, so these cases compare against
  * the two live ones, `.selection-instruction,.modifier-confirm`.
  */
class ActionPanePromptSuite extends munit.FunSuite:
  private val context = BoardSelectionContext("game", "red", 1L)
  private val travel = BoardTargetAction("travel", "Choose a destination", 1, 1,
    autoActivate = true, Vector(BoardTargetCandidate(
      BoardTargetRef.Site("site:woods"), "Deep Woods", Vector.empty)))
  private def projection(open: Boolean): GameProjection =
    GameProjection("game", 1L, "act", Some("red"),
      Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
      Vector.empty, Vector.empty, Vector.empty, ready = true, completed = false,
      actionSelectionOpen = open, boardTargetActions = Vector(travel))
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
    val result = pane(projection(open = false), drafts)
    assertEquals(result.prompt, "Choose a destination")
    assertEquals(result.prompt, scraped(result.element))

  test("the prompt is the modifier confirm while ordering"):
    val draft = ModifierFlowDraft(None, Some("travel"), Map.empty,
      MajorActionPreviewResponse(1L, "travel", Vector.empty, Vector.empty,
        Vector.empty),
      ModifierSelectionState.reconcile(None,
        ModifierSelectionContext("game", "red", 1L, "travel"), Vector.empty, "p"),
      ModifierFlowStage.Ordering)
    val result = pane(projection(open = true),
      SessionDrafts.empty.copy(modifiers = Some(draft)))
    assertEquals(result.prompt, "Confirm modifier order")
    assertEquals(result.prompt, scraped(result.element))

  test("no question in flight is an empty prompt"):
    val result = pane(projection(open = true), SessionDrafts.empty)
    assertEquals(result.prompt, "")
    assertEquals(result.prompt, scraped(result.element))
