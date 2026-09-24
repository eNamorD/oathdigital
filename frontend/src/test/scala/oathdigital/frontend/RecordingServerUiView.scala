package oathdigital.frontend

import oathdigital.protocol.{GameIntent => Intent, PreviewModifier}

/** A `ServerUiView` that records submissions and rerenders instead of
  * touching a server, shared by the walker panel render suites.
  */
private[frontend] final class RecordingView(gameId: String, playerId: String)
    extends ServerUiView {
  var partition: Option[WalkerPartitionDraft] = None
  var distribution: Option[WalkerDistributeDraft] = None
  var selection: Option[WalkerSelectionDraft] = None
  var submitted: Vector[Intent] = Vector.empty
  var rerenders: Int = 0

  def currentWalkerPartition: Option[WalkerPartitionDraft] = partition
  def currentWalkerPartition_=(value: Option[WalkerPartitionDraft]): Unit =
    partition = value
  def currentWalkerDistribution: Option[WalkerDistributeDraft] = distribution
  def currentWalkerDistribution_=(value: Option[WalkerDistributeDraft]): Unit =
    distribution = value
  def currentWalkerSelection: Option[WalkerSelectionDraft] = selection
  def currentWalkerSelection_=(value: Option[WalkerSelectionDraft]): Unit =
    selection = value
  def rerender(): Unit = rerenders += 1
  def submitCommand(command: Intent): Unit = submitted :+= command

  def currentGameId: String = gameId
  def currentPlayerId: String = playerId
  def displayedProjection: Option[GameProjection] = None
  val sessionCoordinator: ServerSessionCoordinator =
    new ServerSessionCoordinator(gameId, playerId)
  var boardSelection: Option[BoardTargetSelectionState] = None
  def currentBoardSelection: Option[BoardTargetSelectionState] = boardSelection
  def currentBoardSelection_=(value: Option[BoardTargetSelectionState]): Unit =
    boardSelection = value
  var modifierWorkflow: Option[ModifierWorkflow] = None
  def currentModifierWorkflow: Option[ModifierWorkflow] = modifierWorkflow
  def currentFacedownAdviserDraft: Option[FacedownAdviserDraft] = None
  def chooseFacedownAdviser(cardId: String): Unit = ()
  def toggleModifier(value: PreviewModifier): Unit = ()
  def moveModifier(value: PreviewModifier, delta: Int): Unit = ()
  def confirmModifiers(): Unit = ()
  def backFromModifiers(): Unit = ()
  def cancelModifiers(): Unit = ()
  def beginTargetedMajorAction(actionKind: String): Unit = ()
  def backFromTargets(): Unit = ()
  def cancelTargetAction(): Unit = ()
  def submitTargetCommand(command: Intent): Unit = ()
  def canControl: Boolean = true
  def handleSelection(result: BoardSelectionResult): Unit = ()
  def loadSession(gameId: String, playerId: String): Unit = ()
  def reconnectSession(): Unit = ()
  def createGame(): Unit = ()
}
