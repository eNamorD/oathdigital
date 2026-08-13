package oathdigital.frontend

private[frontend] final case class BoardSelectionContext(
    gameId: String, playerId: String, sequence: Long)

private[frontend] sealed trait BoardSelectionResult
private[frontend] object BoardSelectionResult {
  final case class Updated(state: BoardTargetSelectionState)
      extends BoardSelectionResult
  final case class Submit(action: BoardTargetAction,
      targets: Vector[BoardTargetRef]) extends BoardSelectionResult
}

private[frontend] final case class BoardTargetSelectionState(
    context: BoardSelectionContext,
    actions: Vector[BoardTargetAction],
    activeActionKind: Option[String],
    selectedKeys: Set[String]
) {
  def activeAction: Option[BoardTargetAction] = activeActionKind.flatMap(kind =>
    actions.find(_.actionKind == kind))

  def activate(kind: String): BoardTargetSelectionState =
    if (actions.exists(action => action.actionKind == kind && !action.autoActivate))
      copy(activeActionKind = Some(kind), selectedKeys = Set.empty)
    else this

  def cancel: BoardTargetSelectionState = activeAction match {
    case Some(action) if !action.autoActivate =>
      copy(activeActionKind = None, selectedKeys = Set.empty)
    case _ => this
  }

  def choose(target: BoardTargetRef): BoardSelectionResult = activeAction match {
    case Some(action) if action.candidates.exists(_.target == target) &&
        action.maximum == 1 => BoardSelectionResult.Submit(action, Vector(target))
    case Some(action) if action.candidates.exists(_.target == target) =>
      val key = target.stableKey
      val next = if (selectedKeys.contains(key)) selectedKeys - key
      else if (selectedKeys.size < action.maximum) selectedKeys + key
      else selectedKeys
      BoardSelectionResult.Updated(copy(selectedKeys = next))
    case _ => BoardSelectionResult.Updated(this)
  }

  def keyboardChoose(key: String, target: BoardTargetRef): BoardSelectionResult =
    if (key == "Enter" || key == " " || key == "Spacebar") choose(target)
    else BoardSelectionResult.Updated(this)

  def selected(target: BoardTargetRef): Boolean =
    selectedKeys.contains(target.stableKey)

  def canConfirm: Boolean = activeAction.exists(action =>
    action.maximum > 1 && selectedKeys.size >= action.minimum &&
      selectedKeys.size <= action.maximum)

  def confirm: Option[BoardSelectionResult.Submit] = for {
    action <- activeAction if canConfirm
  } yield BoardSelectionResult.Submit(action, action.candidates.collect {
    case candidate if selected(candidate.target) => candidate.target
  })
}

private[frontend] object BoardTargetSelectionState {
  def reconcile(previous: Option[BoardTargetSelectionState],
      context: BoardSelectionContext, actions: Vector[BoardTargetAction])
      : BoardTargetSelectionState = previous match {
    case Some(state) if state.context == context && state.actions == actions => state
    case _ =>
      val automatic = actions.find(_.autoActivate).map(_.actionKind)
      BoardTargetSelectionState(context, actions, automatic, Set.empty)
  }
}
