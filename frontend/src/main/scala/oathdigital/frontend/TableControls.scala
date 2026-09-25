package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand, PreviewModifier}

/** What a panel or the board may do to the session: stage a draft, send a
  * command, or hand over a board selection. Three members; a renderer reads
  * its drafts as values and never asks the session for them.
  */
private[frontend] trait TableControls:
  /** Replaces one slot of the draft set and redraws the table. */
  def stage(draft: Draft): Unit
  def submitCommand(command: GameCommand): Unit
  def handleSelection(result: BoardSelectionResult): Unit

/** The modifier flow's commands, on top of the table's. Only the action
  * pane and the facedown-adviser panel see these. Wide on purpose: the flow
  * behind them is the follow-up's module, and it moves as one piece.
  */
private[frontend] trait ActionControls extends TableControls:
  def chooseFacedownAdviser(cardId: String): Unit
  def toggleModifier(value: PreviewModifier): Unit
  def moveModifier(value: PreviewModifier, delta: Int): Unit
  def confirmModifiers(): Unit
  def backFromModifiers(): Unit
  def cancelModifiers(): Unit
  def beginTargetedMajorAction(actionKind: String): Unit
  def backFromTargets(): Unit
  def cancelTargetAction(): Unit
  def submitTargetCommand(command: GameCommand): Unit

/** The development toolbar's view of the session: which game and seat,
  * whether it is connected, and the three ways to change it.
  */
private[frontend] trait SessionControls:
  def currentGameId: String
  def currentPlayerId: String
  def displayedProjection: Option[GameProjection]
  def connectionState: ServerConnectionState
  def loadSession(gameId: String, playerId: String): Unit
  def reconnectSession(): Unit
  def createGame(): Unit
