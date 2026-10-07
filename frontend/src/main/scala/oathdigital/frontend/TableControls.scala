package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand, PreviewModifier}

/** What a panel or the board may do to the session: stage a draft or send a
  * command. Two members; a renderer reads its drafts as values and never
  * asks the session for them.
  */
private[frontend] trait TableControls:
  /** Replaces one slot of the draft set and redraws the table. */
  def stage(draft: Draft): Unit
  def submitCommand(command: GameCommand): Unit

/** The modifier flow's commands, on top of the table's. Only the action
  * pane sees these. Wide on purpose: the flow behind them is the follow-up's
  * module, and it moves as one piece.
  */
private[frontend] trait ActionControls extends TableControls:
  def toggleModifier(value: PreviewModifier): Unit
  def moveModifier(value: PreviewModifier, delta: Int): Unit
  def confirmModifiers(): Unit
  def backFromModifiers(): Unit
  def cancelModifiers(): Unit

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
  /** Fetches the client the server is now serving. The screen offers it when
    * this build cannot parse the position; it is the same path Reconnect
    * takes to the session.
    */
  def reloadClient(): Unit
