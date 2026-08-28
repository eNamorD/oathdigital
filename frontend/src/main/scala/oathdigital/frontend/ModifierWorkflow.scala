package oathdigital.frontend

import oathdigital.protocol.{GameIntent, MajorActionPreviewResponse}

private[frontend] sealed trait ModifierWorkflowStage
private[frontend] object ModifierWorkflowStage {
  case object Ordering extends ModifierWorkflowStage
  case object Targets extends ModifierWorkflowStage
}

private[frontend] final case class ModifierWorkflow(
    command: Option[GameIntent],
    actionKind: Option[String],
    baseParameters: Map[String, String],
    preview: MajorActionPreviewResponse,
    selection: ModifierSelectionState,
    stage: ModifierWorkflowStage) {
  def ordering: Boolean = stage == ModifierWorkflowStage.Ordering
  def hadModifierStage: Boolean = preview.modifiers.nonEmpty
  def showTargets(response: MajorActionPreviewResponse): ModifierWorkflow =
    copy(preview = response, stage = ModifierWorkflowStage.Targets)
  def backFromTargets: Option[ModifierWorkflow] = Option.when(hadModifierStage)(
    copy(stage = ModifierWorkflowStage.Ordering))
  def cancel: Option[ModifierWorkflow] = None
}

private[frontend] object ModifierWorkflow {
  private val targetedActions = Map(
    "travel" -> ("travel" -> Map.empty[String, String]),
    "campaign-conquest" -> ("campaign" -> Map("kind" -> "conquest")),
    "campaign-raid" -> ("campaign" -> Map("kind" -> "raid")),
    "muster" -> ("muster" -> Map.empty[String, String]),
    "trade-favor" -> ("trade" -> Map("resource" -> "favor")),
    "trade-secret" -> ("trade" -> Map("resource" -> "secret")),
    "play-facedown-adviser" -> ("search" -> Map("procedure" -> "facedown-adviser")))

  def targeted(actionKind: String): Option[(String, Map[String, String])] =
    targetedActions.get(actionKind)

  def reconcile(previous: Option[ModifierWorkflow], gameId: String,
      playerId: String, sequence: Long): Option[ModifierWorkflow] = previous.filter {
    workflow =>
      val context = workflow.selection.context
      context.gameId == gameId && context.playerId == playerId &&
        context.sequence == sequence
  }

  def action(command: GameIntent): Option[(String, Map[String, String])] = command match {
    case GameIntent.BeginSearch(source) => Some("search" ->
      (Map("source" -> source.source) ++ source.region.map("region" -> _)))
    case GameIntent.BeginForge => Some("forge" -> Map.empty)
    case GameIntent.BeginRecover => Some("recover" -> Map.empty)
    case GameIntent.ResolveFacedownAdviser(_, _) =>
      Some("search" -> Map("procedure" -> "facedown-adviser"))
    case _ => None
  }

  def targetAction(actionKind: String, response: MajorActionPreviewResponse,
      actions: Vector[BoardTargetAction]): Option[BoardTargetAction] = {
    val authorized = response.targets.map(_.key).toSet
    actions.find(_.actionKind == actionKind).map { action =>
      val candidates = action.candidates.filter(candidate => authorized(
        previewKey(candidate.target)))
      val required = action.requiredTargets.filter(target => authorized(previewKey(target)))
      action.copy(minimum = math.min(action.minimum, candidates.size),
        maximum = math.min(action.maximum, candidates.size), candidates = candidates,
        requiredTargets = required, explicitConfirm = true)
    }
  }

  private def previewKey(target: BoardTargetRef): String = target match {
    case BoardTargetRef.SiteCard(_, kind, id) => s"$kind:$id"
    case other => other.stableKey
  }
}
