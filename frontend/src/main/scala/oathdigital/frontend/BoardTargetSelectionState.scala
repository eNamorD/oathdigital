package oathdigital.frontend

private[frontend] final case class BoardSelectionContext(
    gameId: String, playerId: String, sequence: Long)

private[frontend] sealed trait BoardSelectionResult
private[frontend] object BoardSelectionResult {
  final case class Updated(state: BoardTargetSelectionState)
      extends BoardSelectionResult
  final case class Submit(action: BoardTargetAction,
      targets: Vector[BoardTargetRef]) extends BoardSelectionResult
  final case class Form(state: BoardTargetFormationState)
      extends BoardSelectionResult
}

private[frontend] final case class BoardTargetFormationState(
    context: BoardSelectionContext,
    action: BoardTargetAction,
    targets: Vector[BoardTargetRef],
    force: Int
) {
  private def facts = action.formation.get
  def minimumForce: Int = facts.minimumForce
  def maximumForce: Int = facts.maximumForce
  def availableWarbands: Int = facts.availableWarbands
  def supplyCost: Int = facts.supplyCost
  def remainingWarbands: Int = availableWarbands - force
  def attackDiceBeforePlans: Int = force
  def decrement: BoardTargetFormationState = copy(force = math.max(minimumForce, force - 1))
  def increment: BoardTargetFormationState = copy(force = math.min(maximumForce, force + 1))
  def choose(value: Int): BoardTargetFormationState =
    if (value >= minimumForce && value <= maximumForce) copy(force = value) else this
}
private[frontend] object BoardTargetFormationState {
  def apply(context: BoardSelectionContext, action: BoardTargetAction,
      target: BoardTargetRef, force: Int): BoardTargetFormationState =
    new BoardTargetFormationState(context, action, Vector(target), force)

  def reconcile(previous: Option[BoardTargetFormationState],
      context: BoardSelectionContext, actions: Vector[BoardTargetAction]) =
    previous.filter(state => state.context == context &&
      actions.contains(state.action) && state.targets.forall(target =>
        state.action.candidates.exists(_.target == target)))
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
    actions.find(action => action.actionKind == kind && !action.autoActivate)
      .map(action => copy(activeActionKind = Some(kind),
        selectedKeys = action.requiredTargets.map(_.stableKey).toSet))
      .getOrElse(this)

  def cancel: BoardTargetSelectionState = activeAction match {
    case Some(action) if !action.autoActivate =>
      copy(activeActionKind = None, selectedKeys = Set.empty)
    case _ => this
  }

  def choose(target: BoardTargetRef): BoardSelectionResult = activeAction match {
    case Some(action) if action.candidates.exists(_.target == target) &&
        action.maximum == 1 && action.formation.nonEmpty =>
      BoardSelectionResult.Form(BoardTargetFormationState(context, action,
        Vector(target),
        action.formation.get.maximumForce))
    case Some(action) if action.candidates.exists(_.target == target) &&
        action.maximum == 1 => BoardSelectionResult.Submit(action, Vector(target))
    case Some(action) if action.candidates.exists(_.target == target) =>
      val key = target.stableKey
      val required = action.requiredTargets.map(_.stableKey).toSet
      val next = if (selectedKeys.contains(key) && !required(key)) selectedKeys - key
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
    targets = action.candidates.collect {
    case candidate if selected(candidate.target) => candidate.target
    }
  } yield BoardSelectionResult.Submit(action, targets)

  def confirmResult: Option[BoardSelectionResult] = confirm.map { confirmed =>
    confirmed.action.formation match {
      case Some(formation) => BoardSelectionResult.Form(
        BoardTargetFormationState(context, confirmed.action, confirmed.targets,
          formation.maximumForce))
      case None => confirmed
    }
  }
}

private[frontend] object BoardTargetSelectionState {
  def reconcile(previous: Option[BoardTargetSelectionState],
      context: BoardSelectionContext, actions: Vector[BoardTargetAction])
      : BoardTargetSelectionState = previous match {
    case Some(state) if state.context == context && state.actions == actions => state
    case _ =>
      val automatic = actions.find(_.autoActivate).map(_.actionKind)
      val required = automatic.toVector.flatMap(kind => actions.find(
        _.actionKind == kind).toVector.flatMap(_.requiredTargets)).map(_.stableKey).toSet
      BoardTargetSelectionState(context, actions, automatic, required)
  }
}
