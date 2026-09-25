package oathdigital.frontend

import oathdigital.protocol.{GameIntent => Intent, PreviewModifier}

/** A `ServerUiView` that records submissions and rerenders instead of
  * touching a server, shared by the walker panel render suites.
  */
private[frontend] class RecordingView(gameId: String, playerId: String)
    extends ServerUiView:
  var partition: Option[WalkerPartitionDraft] = None
  var distribution: Option[WalkerDistributeDraft] = None
  var selection: Option[WalkerSelectionDraft] = None
  var board: Option[WalkerBoardDraft] = None
  var submitted: Vector[Intent] = Vector.empty
  var rerenders: Int = 0
  var staged: Vector[Draft] = Vector.empty
  /** The draft set as the stages so far leave it, from `empty`. */
  def drafts: SessionDrafts = staged.foldLeft(SessionDrafts.empty)(_.staged(_))
  def stage(draft: Draft): Unit =
    staged :+= draft
    // Mirror into the transitional slots so a suite that re-renders from
    // the fake's getters keeps seeing the staged draft until it migrates.
    draft match
      case Draft.Partition(value) => partition = Some(value)
      case Draft.Distribute(value) => distribution = Some(value)
      case Draft.Selection(value) => selection = Some(value)
      case Draft.Board(value) => board = value
      case Draft.BoardTargets(value) => boardSelection = Some(value)

  def currentWalkerPartition: Option[WalkerPartitionDraft] = partition
  def currentWalkerPartition_=(value: Option[WalkerPartitionDraft]): Unit =
    partition = value
  def currentWalkerDistribution: Option[WalkerDistributeDraft] = distribution
  def currentWalkerDistribution_=(value: Option[WalkerDistributeDraft]): Unit =
    distribution = value
  def currentWalkerSelection: Option[WalkerSelectionDraft] = selection
  def currentWalkerSelection_=(value: Option[WalkerSelectionDraft]): Unit =
    selection = value
  def currentWalkerBoard: Option[WalkerBoardDraft] = board
  def currentWalkerBoard_=(value: Option[WalkerBoardDraft]): Unit =
    board = value
  def rerender(): Unit = rerenders += 1
  def submitCommand(command: Intent): Unit = submitted :+= command

  def currentPlayerId: String = playerId
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
