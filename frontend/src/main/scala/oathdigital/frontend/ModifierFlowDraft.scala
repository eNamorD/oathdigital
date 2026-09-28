package oathdigital.frontend

import oathdigital.protocol.{GameIntent, MajorActionPreviewResponse, ModifierInvocation}

private[frontend] enum ModifierFlowStage { case Ordering, Targets }

/** The modifier flow's slot in the draft set (CONTEXT.md): the preview the
  * major action opened, the viewer's modifier order, and the stage the flow
  * is at. `ModifierFlow` is the behavior; this is the value it steps.
  */
private[frontend] final case class ModifierFlowDraft(
    command: Option[GameIntent.StartWalker],
    actionKind: Option[String],
    baseParameters: Map[String, String],
    preview: MajorActionPreviewResponse,
    selection: ModifierSelectionState,
    stage: ModifierFlowStage):
  def ordering: Boolean = stage == ModifierFlowStage.Ordering
  def hadModifierStage: Boolean = preview.modifiers.nonEmpty
  def showTargets(response: MajorActionPreviewResponse): ModifierFlowDraft =
    copy(preview = response, stage = ModifierFlowStage.Targets)
  def backFromTargets: Option[ModifierFlowDraft] = Option.when(hadModifierStage)(
    copy(stage = ModifierFlowStage.Ordering))
  def cancel: Option[ModifierFlowDraft] = None

private[frontend] object ModifierFlowDraft:
  /** The `ActionRef` wire keys registered on the generic walker, as plain
    * strings for the same reason `ServerUiSupport` spells Recover's
    * decision ids out: `ActionRef` lives in the JVM-only engine sources the
    * frontend cannot depend on, and the key rides every walker command as
    * an uninterpreted string already. A key absent here falls through to
    * `None`, which is the safe answer -- the server rejects a preview for
    * an action it does not recognise.
    */
  private val walkerActions: Set[String] = Set("search", "recover", "forge",
    "travel", "muster", "trade", "challenge")

  private val targetedActions = Map(
    "travel" -> ("travel" -> Map.empty[String, String]),
    "play-facedown-adviser" -> ("search" -> Map("procedure" -> "facedown-adviser")))

  def targeted(actionKind: String): Option[(String, Map[String, String])] =
    targetedActions.get(actionKind)

  /** The flow draft a preview response opens. Ordering when the response
    * offers modifiers, Targets otherwise; the selection is reconciled from
    * `previous` so a re-preview of the same shape keeps the viewer's order.
    */
  def fromPreview(command: Option[GameIntent.StartWalker],
      actionKind: Option[String],
      parameters: Map[String, String], response: MajorActionPreviewResponse,
      previous: Option[ModifierSelectionState],
      context: ModifierSelectionContext): ModifierFlowDraft =
    val fingerprint = s"${response.nextSequence}:${response.action}:" +
      response.modifiers.map(m => s"${m.sourceKey}/${m.handlerId}").mkString("|")
    ModifierFlowDraft(command, actionKind, parameters, response,
      ModifierSelectionState.reconcile(previous, context, response.modifiers,
        fingerprint),
      if response.modifiers.nonEmpty then ModifierFlowStage.Ordering
      else ModifierFlowStage.Targets)

  def reconcile(previous: Option[ModifierFlowDraft],
      context: BoardSelectionContext): Option[ModifierFlowDraft] = previous.filter:
    draft =>
      val selected = draft.selection.context
      selected.gameId == context.gameId && selected.playerId == context.playerId &&
        selected.sequence == context.sequence

  def action(command: GameIntent): Option[(String, Map[String, String])] = command match
    // Every action registered on the walker offers its modifiers through
    // `StartWalker`; an unregistered key must NOT be swept in, since the
    // server would reject the preview for an action it does not know.
    case GameIntent.StartWalker(action, _, _) if walkerActions(action) =>
      Some(action -> Map.empty)
    case GameIntent.StartWalker("play-facedown-adviser", _, _) =>
      Some("search" -> Map("procedure" -> "facedown-adviser"))
    case _ => None

  /** The command actually transmitted once modifier ordering is confirmed.
    * Every action with a modifier stage starts on the generic walker, and
    * `StartWalker`'s own `modifiers` field carries the player-selected power
    * ids: the SAME ordered `invocations`, by `handlerId`, folded into the
    * intent itself, with no outer modifiers sent.
    *
    * The start argument survives the fold untouched: an action that is both
    * walker-registered and board-targeted (Travel) picks its target in the
    * stage AFTER modifier ordering, so by the time this runs the destination
    * is already on the intent and only the modifiers are missing.
    */
  def submission(command: GameIntent.StartWalker,
      invocations: Vector[ModifierInvocation]): GameIntent.StartWalker =
    command.copy(modifiers = invocations.map(_.handlerId))

  def targetAction(actionKind: String, response: MajorActionPreviewResponse,
      actions: Vector[BoardTargetAction]): Option[BoardTargetAction] =
    val authorized = response.targets.map(_.key).toSet
    actions.find(_.actionKind == actionKind).map { action =>
      val candidates = action.candidates.filter(candidate => authorized(
        candidate.target.stableKey))
      action.copy(minimum = math.min(action.minimum, candidates.size),
        maximum = math.min(action.maximum, candidates.size), candidates = candidates,
        explicitConfirm = true)
    }
