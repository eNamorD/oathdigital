package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DecisionPlacementWire,
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

  /** Forge's decision is a partition: every offered denizen is placed in
    * either the favor section or the secret section. This state's per-target
    * `"favor"`/`"secret"` choice is exactly that placement, so the command is
    * a direct translation rather than a second vocabulary. The site id the
    * old wire row carried is dropped: a denizen names itself.
    */
  def command(playerId: String): Option[GameCommand.ResolveWalker] =
    Option.when(canConfirm)(GameCommand.ResolveWalker(forge.decisionId,
      DecisionAnswerWire.PartitionWire(
        forge.targets.zip(assignments).map { case (target, resource) =>
          DecisionPlacementWire("denizen", target.denizenId,
            if (resource == "favor") "pay-favor" else "pay-secret")
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
