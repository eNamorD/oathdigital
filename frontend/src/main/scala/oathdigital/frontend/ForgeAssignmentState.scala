package oathdigital.frontend

private[frontend] final case class ForgeAssignmentState(
    context: BoardSelectionContext,
    forge: ForgeState,
    assignments: Vector[String]
) {
  def choose(index: Int, resource: String): ForgeAssignmentState =
    if (!assignments.indices.contains(index) ||
        !Set("favor", "secret").contains(resource)) this
    else copy(assignments = assignments.updated(index, resource))

  def canConfirm: Boolean = assignments.size == forge.targets.size &&
    assignments.count(_ == "favor") == forge.favor &&
    assignments.count(_ == "secret") == forge.secrets

  def command(playerId: String): Option[GameCommand.CompleteForge] =
    Option.when(canConfirm)(GameCommand.CompleteForge(playerId,
      forge.decisionId, forge.targets.zip(assignments)))
}

private[frontend] object ForgeAssignmentState {
  def reconcile(previous: Option[ForgeAssignmentState],
      context: BoardSelectionContext, forge: Option[ForgeState])
      : Option[ForgeAssignmentState] = forge.map { current =>
    previous.filter(state => state.context == context && state.forge == current)
      .getOrElse(ForgeAssignmentState(context, current,
        Vector.fill(current.favor)("favor") ++
          Vector.fill(current.secrets)("secret")))
  }
}
