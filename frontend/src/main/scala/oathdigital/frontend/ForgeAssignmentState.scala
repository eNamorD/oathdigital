package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, ForgeAssignment,
  GameIntent => GameCommand}

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

  /** Forge is a walker action (batch-1 Task 3), so a confirmed assignment
    * answers its parked `"forge.assignment"` decision through
    * `ResolveWalker` -- `forge.decisionId` is that decision's id, projected
    * by the server from the walker's own parked position, not a
    * per-command `DecisionId` the way the deleted `CompleteForge` intent's
    * was.
    */
  def command(playerId: String): Option[GameCommand.ResolveWalker] =
    Option.when(canConfirm)(GameCommand.ResolveWalker(forge.decisionId,
      DecisionAnswerWire.ForgeAssignmentWire(
        forge.targets.zip(assignments).map { case (target, resource) =>
          ForgeAssignment(target.siteId, target.denizenId, resource)
        })))
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
