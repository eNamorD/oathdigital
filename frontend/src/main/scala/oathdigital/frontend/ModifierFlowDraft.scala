package oathdigital.frontend

import oathdigital.protocol.{GameIntent, MajorActionPreviewResponse, ModifierInvocation}

/** The modifier flow's slot in the draft set (CONTEXT.md): the command the
  * viewer started, the preview its action opened, and the viewer's modifier
  * order. The flow exists only while the viewer orders modifiers; a start
  * with none goes straight out. `ModifierFlow` is the behavior; this is the
  * value it steps.
  */
private[frontend] final case class ModifierFlowDraft(
    command: GameIntent.StartWalker,
    baseParameters: Map[String, String],
    preview: MajorActionPreviewResponse,
    selection: ModifierSelectionState)

private[frontend] object ModifierFlowDraft:
  /** The `ActionRef` wire keys registered on the generic walker, as plain
    * strings for the same reason `ServerUiSupport` spells Recover's
    * decision ids out: `ActionRef` lives in the JVM-only engine sources the
    * frontend cannot depend on, and the key rides every walker command as
    * an uninterpreted string already. A key absent here falls through to
    * `None`, which is the safe answer -- the server rejects a preview for
    * an action it does not recognise. Search and facedown-adviser play ask
    * for their modifiers inside the walk (modifier walker design), so they
    * are absent and start directly.
    */
  private val walkerActions: Set[String] = Set("recover", "forge",
    "travel", "muster", "trade", "challenge")

  /** The flow draft a preview response that offers modifiers opens; the
    * selection is reconciled from `previous` so a re-preview of the same
    * shape keeps the viewer's order.
    */
  def fromPreview(command: GameIntent.StartWalker,
      parameters: Map[String, String], response: MajorActionPreviewResponse,
      previous: Option[ModifierSelectionState],
      context: ModifierSelectionContext): ModifierFlowDraft =
    val fingerprint = s"${response.nextSequence}:${response.action}:" +
      response.modifiers.map(m => s"${m.sourceKey}/${m.handlerId}").mkString("|")
    ModifierFlowDraft(command, parameters, response,
      ModifierSelectionState.reconcile(previous, context, response.modifiers,
        fingerprint))

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
    case _ => None

  /** The command actually transmitted once modifier ordering is confirmed.
    * Every action with a modifier stage starts on the generic walker, and
    * `StartWalker`'s own `modifiers` field carries the player-selected power
    * ids: the SAME ordered `invocations`, by `handlerId`, folded into the
    * intent itself, with no outer modifiers sent.
    */
  def submission(command: GameIntent.StartWalker,
      invocations: Vector[ModifierInvocation]): GameIntent.StartWalker =
    command.copy(modifiers = invocations.map(_.handlerId))
