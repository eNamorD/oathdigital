package oathdigital.frontend

import oathdigital.protocol.{MajorActionPreviewResponse, PreviewModifier}

/** Builders for the modifier-flow draft a session suite starts from. */
private[frontend] object ModifierFlowDrafts:
  /** Red's travel flow at the Targets stage of game "game": the preview
    * response at `sequence` carries `modifiers`, and none is selected yet.
    */
  def travelTargetsDraft(sequence: Long,
      modifiers: Vector[PreviewModifier] = Vector.empty): ModifierFlowDraft =
    ModifierFlowDraft(command = None, actionKind = Some("travel"),
      baseParameters = Map.empty,
      preview = MajorActionPreviewResponse(sequence, "travel", modifiers,
        Vector.empty, Vector.empty),
      selection = ModifierSelectionState.reconcile(None,
        ModifierSelectionContext("game", "red", sequence, "travel"),
        modifiers, "p"),
      stage = ModifierFlowStage.Targets)
