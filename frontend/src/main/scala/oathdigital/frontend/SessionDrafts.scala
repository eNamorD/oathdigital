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
    partition: Option[WalkerPartitionDraft],
    distribute: Option[WalkerDistributeDraft],
    selection: Option[WalkerSelectionDraft],
    board: Option[BoardSelectionDraft],
    modifiers: Option[ModifierFlowDraft]):

  /** The set after a snapshot: each slot asked to carry over what still
    * answers the same question in the same context, and to drop the rest.
    */
  def reconcile(context: BoardSelectionContext,
      projection: GameProjection): SessionDrafts =
    SessionDrafts(
      context = Some(context),
      partition = WalkerPartitionDraft.reconcile(partition, context,
        projection.walkerDecision),
      distribute = WalkerDistributeDraft.reconcile(distribute, context,
        projection.walkerDecision),
      selection = WalkerSelectionDraft.reconcile(selection, context,
        projection.walkerDecision),
      // A fresh position may open with a pick; at the same position, a pick
      // the viewer cleared stays cleared.
      board = BoardSelectionDraft.reconcile(board, context,
        projection.walkerDecision).orElse(BoardSelectionDraft.preselected(
          context, projection.walkerDecision)
          .filterNot(_ => this.context.contains(context))),
      modifiers = ModifierFlowDraft.reconcile(modifiers, context))

  /** One slot replaced; the panel that holds a draft can replace only it. */
  def staged(draft: Draft): SessionDrafts = draft match
    case Draft.Partition(value) => copy(partition = Some(value))
    case Draft.Distribute(value) => copy(distribute = Some(value))
    case Draft.Selection(value) => copy(selection = Some(value))
    case Draft.Board(value) => copy(board = value)

  /** The set after leaving the modifier flow. Every exit drops the flow
    * draft and nothing else; the three are named for why the viewer left.
    */
  def leave(exit: FlowExit): SessionDrafts = exit match
    case FlowExit.Failed | FlowExit.Cancelled | FlowExit.OrderingLeft =>
      copy(modifiers = None)

  /** The set after one step inside the modifier flow. */
  def step(value: FlowStep): SessionDrafts = value match
    case FlowStep.Ordering(draft) => copy(modifiers = Some(draft))
    case FlowStep.Toggle(modifier) =>
      copy(modifiers = modifiers.map(draft =>
        draft.copy(selection = draft.selection.toggle(modifier))))
    case FlowStep.Move(modifier, delta) =>
      copy(modifiers = modifiers.map(draft => draft.copy(selection =
        if delta < 0 then draft.selection.moveEarlier(modifier)
        else draft.selection.moveLater(modifier))))

private[frontend] object SessionDrafts:
  val empty: SessionDrafts =
    SessionDrafts(None, None, None, None, None, None)

/** A draft a panel or the board can stage. There is no case for the
  * modifier flow draft: no panel writes it; the modifier flow does, through
  * its commands. `Board` carries an `Option` because a
  * second click on the picked site clears the pick, and that clear is a
  * stage like any other.
  */
private[frontend] enum Draft:
  case Partition(value: WalkerPartitionDraft)
  case Distribute(value: WalkerDistributeDraft)
  case Selection(value: WalkerSelectionDraft)
  case Board(value: Option[BoardSelectionDraft])

/** The ways a viewer leaves the modifier flow. `Failed` is a preview or
  * submit error, `Cancelled` the Cancel button, and `OrderingLeft` Confirm
  * (which submits the command) or Back out of the ordering panel.
  */
private[frontend] enum FlowExit:
  case Failed
  case Cancelled
  case OrderingLeft

/** The three ways a viewer moves inside the modifier flow without leaving
  * it. `Ordering` is the entry; the other two are the steps a panel's
  * controls take. Only `ModifierFlow` applies these.
  */
private[frontend] enum FlowStep:
  case Ordering(draft: ModifierFlowDraft)
  case Toggle(value: PreviewModifier)
  case Move(value: PreviewModifier, delta: Int)
