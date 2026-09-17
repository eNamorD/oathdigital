package oathdigital.frontend

import oathdigital.protocol.{GameIntent, MajorActionPreviewResponse, ModifierInvocation}

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
  /** The `ActionRef` wire keys registered on the generic walker, as plain
    * strings for the same reason `ServerUiSupport` spells Recover's
    * decision ids out: `ActionRef` lives in the JVM-only engine sources the
    * frontend cannot depend on, and the key rides every walker command as
    * an uninterpreted string already. A key absent here falls through to
    * `None`, which is the safe answer -- the server rejects a preview for
    * an action it does not recognise.
    */
  private val walkerActions: Set[String] = Set("search", "recover", "forge",
    "travel")

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
    // Every action registered on the walker offers its modifiers through
    // `StartWalker`; an unregistered key must NOT be swept in, since the
    // server would reject the preview for an action it does not know.
    case GameIntent.StartWalker(action, _, _) if walkerActions(action) =>
      Some(action -> Map.empty)
    case GameIntent.StartWalker("play-facedown-adviser", _, _) =>
      Some("search" -> Map("procedure" -> "facedown-adviser"))
    case _ => None
  }

  /** The command actually transmitted once modifier ordering is confirmed.
    * `StartWalker`'s own `modifiers` field is the walker command surface's
    * carrier for player-selected power ids (Task 6/7b) -- distinct from the
    * legacy `orderedModifiers`/`WithModifiers` wrapping every other major
    * action still uses. `GameApplicationService.majorAction` does not
    * recognize `StartWalker`, so wrapping it in `WithModifiers` would reject
    * with "ordered modifiers are only valid on a major-action start" the
    * moment a modifier (e.g. Catacombs) is actually selected. Folding the
    * SAME ordered `invocations` (by `handlerId`, the stable power id string
    * both the legacy and walker power catalogs share) into the intent
    * itself, and sending no outer modifiers, keeps this command through the
    * path the engine actually accepts.
    */
  def submission(command: GameIntent, invocations: Vector[ModifierInvocation])
      : (GameIntent, Vector[ModifierInvocation]) = command match {
    // The start argument survives the fold untouched: an action that is both
    // walker-registered and board-targeted (Travel, batch-1 Task 5) picks its
    // target in the stage AFTER modifier ordering, so by the time this runs
    // the destination is already on the intent and only the modifiers are
    // missing. Rebuilding the intent without it would submit a Travel with no
    // route.
    case GameIntent.StartWalker(action, _, startArgs) =>
      GameIntent.StartWalker(action, invocations.map(_.handlerId),
        startArgs) -> Vector.empty
    case other => other -> invocations
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
