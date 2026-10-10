package oathdigital.frontend

/** The draft set (CONTEXT.md): everything a viewer has staged at the table
  * and not yet sent. One immutable value, bound to one game, seat and
  * position through `context`. `ServerModeUi` holds the current one; the
  * renderers read it; every write is one of the two methods below, so the
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
    board: Option[BoardSelectionDraft]):

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
          .filterNot(_ => this.context.contains(context))))

  /** One slot replaced; the panel that holds a draft can replace only it. */
  def staged(draft: Draft): SessionDrafts = draft match
    case Draft.Partition(value) => copy(partition = Some(value))
    case Draft.Distribute(value) => copy(distribute = Some(value))
    case Draft.Selection(value) => copy(selection = Some(value))
    case Draft.Board(value) => copy(board = value)

private[frontend] object SessionDrafts:
  val empty: SessionDrafts =
    SessionDrafts(None, None, None, None, None)

/** A draft a panel or the board can stage. `Board` carries an `Option`
  * because a second click on the picked site clears the pick, and that
  * clear is a stage like any other.
  */
private[frontend] enum Draft:
  case Partition(value: WalkerPartitionDraft)
  case Distribute(value: WalkerDistributeDraft)
  case Selection(value: WalkerSelectionDraft)
  case Board(value: Option[BoardSelectionDraft])
