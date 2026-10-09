package oathdigital.frontend

import oathdigital.protocol.{GameIntent, MajorActionPreviewResponse, PreviewModifier}

/** Builders for the modifier-flow draft a session suite starts from. */
private[frontend] object ModifierFlowDrafts:
  /** Red's Travel flow in game "game": the preview response at `sequence`
    * carries `modifiers`, and none is selected yet.
    */
  def orderingDraft(sequence: Long,
      modifiers: Vector[PreviewModifier] = Vector.empty): ModifierFlowDraft =
    ModifierFlowDraft(command = GameIntent.StartWalker("travel", Vector.empty),
      baseParameters = Map.empty,
      preview = MajorActionPreviewResponse(sequence, "travel", modifiers,
        Vector.empty),
      selection = ModifierSelectionState.reconcile(None,
        ModifierSelectionContext("game", "red", sequence, "travel"),
        modifiers, "p"))
