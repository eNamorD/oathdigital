package oathdigital.frontend

import oathdigital.protocol.PreviewModifier

/** The draft set (CONTEXT.md): everything a viewer has staged at the table
  * and not yet sent. One immutable value, bound to one game, seat and
  * position through `context`. `ServerModeUi` holds the current one; the
  * renderers read it; every write is one of the three methods below, so the
  * question "which drafts die together" has one answer, here.
  *
  * `empty` has no context. It is what a session holds before its first
  * snapshot, and what a seat change or a reload resets to; the next
  * `reconcile` binds it.
  */
private[frontend] final case class SessionDrafts(
    context: Option[BoardSelectionContext],
    boardTargets: Option[BoardTargetSelectionState],
    partition: Option[WalkerPartitionDraft],
    distribute: Option[WalkerDistributeDraft],
    selection: Option[WalkerSelectionDraft],
    board: Option[WalkerBoardDraft],
    modifiers: Option[ModifierWorkflow],
    facedownAdviser: Option[FacedownAdviserDraft]):

  /** The set after a snapshot: each slot asked to carry over what still
    * answers the same question in the same context, and to drop the rest.
    */
  def reconcile(context: BoardSelectionContext,
      projection: GameProjection): SessionDrafts =
    SessionDrafts(
      context = Some(context),
      boardTargets = Some(BoardTargetSelectionState.reconcile(boardTargets,
        context, projection.boardTargetActions)),
      partition = WalkerPartitionDraft.reconcile(partition, context,
        projection.walkerDecision),
      distribute = WalkerDistributeDraft.reconcile(distribute, context,
        projection.walkerDecision),
      selection = WalkerSelectionDraft.reconcile(selection, context,
        projection.walkerDecision),
      board = WalkerBoardDraft.reconcile(board, context,
        projection.walkerDecision),
      modifiers = ModifierWorkflow.reconcile(modifiers, context),
      facedownAdviser = FacedownAdviserDraft.reconcile(facedownAdviser,
        context, projection.minorActions))

  /** One slot replaced; the panel that holds a draft can replace only it. */
  def staged(draft: Draft): SessionDrafts = draft match
    case Draft.Partition(value) => copy(partition = Some(value))
    case Draft.Distribute(value) => copy(distribute = Some(value))
    case Draft.Selection(value) => copy(selection = Some(value))
    case Draft.Board(value) => copy(board = value)
    case Draft.BoardTargets(value) => copy(boardTargets = Some(value))

  /** The set after leaving the modifier flow one of six ways. The table
    * is the spec's; `Failed` and `Completed` differ only on the board
    * targets, and that is observable on a submit error.
    */
  def leave(exit: FlowExit): SessionDrafts = exit match
    case FlowExit.Restarted =>
      copy(modifiers = None, facedownAdviser = None,
        boardTargets = boardTargets.map(_.cancel))
    case FlowExit.Failed =>
      copy(modifiers = None, facedownAdviser = None)
    case FlowExit.Completed =>
      copy(modifiers = None, facedownAdviser = None, boardTargets = None)
    case FlowExit.Cancelled(restored) =>
      copy(modifiers = None, facedownAdviser = None, boardTargets = restored)
    case FlowExit.TargetsLeft =>
      copy(modifiers = modifiers.flatMap(_.backFromTargets),
        facedownAdviser = None, boardTargets = None)
    case FlowExit.OrderingLeft =>
      copy(modifiers = None)

  /** The set after one step inside the modifier flow. The table is the
    * spec's; the module computes a `Targets` entry from the projection and
    * the context, and this only applies it.
    */
  def step(value: FlowStep): SessionDrafts = value match
    case FlowStep.Ordering(workflow) => copy(modifiers = Some(workflow))
    case FlowStep.Targets(workflow, TargetsEntry.Facedown(pick)) =>
      copy(modifiers = Some(workflow), facedownAdviser = pick, boardTargets = None)
    case FlowStep.Targets(workflow, TargetsEntry.Board(targets)) =>
      copy(modifiers = Some(workflow), boardTargets = Some(targets))
    case FlowStep.Targets(workflow, TargetsEntry.NoTargets) =>
      copy(modifiers = Some(workflow))
    case FlowStep.Toggle(modifier) =>
      copy(modifiers = modifiers.map(workflow =>
        workflow.copy(selection = workflow.selection.toggle(modifier))))
    case FlowStep.Move(modifier, delta) =>
      copy(modifiers = modifiers.map(workflow => workflow.copy(selection =
        if delta < 0 then workflow.selection.moveEarlier(modifier)
        else workflow.selection.moveLater(modifier))))
    case FlowStep.ChooseFacedown(cardId) =>
      copy(facedownAdviser = facedownAdviser.map(_.choose(cardId)))

private[frontend] object SessionDrafts:
  val empty: SessionDrafts =
    SessionDrafts(None, None, None, None, None, None, None, None)

/** A draft a panel or the board can stage. There is no case for the
  * modifier workflow or the facedown pick: no panel writes them; the modifier
  * flow does, through its commands. `Board` carries an `Option` because a
  * second click on the picked site clears the pick, and that clear is a
  * stage like any other.
  */
private[frontend] enum Draft:
  case Partition(value: WalkerPartitionDraft)
  case Distribute(value: WalkerDistributeDraft)
  case Selection(value: WalkerSelectionDraft)
  case Board(value: Option[WalkerBoardDraft])
  case BoardTargets(value: BoardTargetSelectionState)

/** The six ways a viewer leaves the modifier flow. `Cancelled` carries the
  * restored board targets because computing them needs the projection's
  * `boardTargetActions`; the caller (`restoredTargets` in `ServerModeUi`)
  * builds the restore from the set's own context.
  */
private[frontend] enum FlowExit:
  case Restarted
  case Failed
  case Completed
  case Cancelled(restored: Option[BoardTargetSelectionState])
  case TargetsLeft
  /** Leaving the ordering stage with no targets stage after it: Confirm
    * submitted the command directly, or Back left the ordering panel. Only
    * the workflow goes; the facedown pick and the board targets stay.
    */
  case OrderingLeft

/** The five ways a viewer moves inside the modifier flow without leaving
  * it. `Ordering` and `Targets` are the two entries; the other three are the
  * steps a panel's controls take. Only `ModifierFlow` applies these.
  */
private[frontend] enum FlowStep:
  case Ordering(workflow: ModifierWorkflow)
  case Targets(workflow: ModifierWorkflow, entry: TargetsEntry)
  case Toggle(value: PreviewModifier)
  case Move(value: PreviewModifier, delta: Int)
  case ChooseFacedown(cardId: String)

/** What entering the Targets stage opens beside the workflow: the facedown
  * pick (and no board targets), one activated board action, or nothing when
  * the preview names no target action the board knows.
  */
private[frontend] enum TargetsEntry:
  case Facedown(pick: Option[FacedownAdviserDraft])
  case Board(targets: BoardTargetSelectionState)
  case NoTargets
