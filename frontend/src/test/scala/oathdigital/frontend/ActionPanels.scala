package oathdigital.frontend

import org.scalajs.dom

/** The action panel as the acting player sees it. */
private[frontend] object ActionPanels:
  /** The presentation for `playerId` when it is that player's turn: the
    * gameplay controls show and nobody is waited for.
    */
  def actingViewer(playerId: String): ServerUiSupport.ViewerPresentation =
    ServerUiSupport.ViewerPresentation(showGameplayControls = true,
      waitingForPlayerId = None, waitingForDisplayName = None,
      playerId = playerId)

  /** The action panel of `projection` as red, the acting viewer, who may
    * control the game, with no parked decision to show and `drafts` as the
    * session's drafts.
    */
  def actionsPanelFor(projection: GameProjection,
      drafts: SessionDrafts = SessionDrafts.empty): dom.Element =
    ActionDecisionRenderer.actionsPanel(projection, actingViewer("red"),
      ParkedDecision.Routed(None, None), canControl = true, drafts,
      new RecordingControls()).element
