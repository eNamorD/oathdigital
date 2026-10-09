package oathdigital.frontend

import oathdigital.protocol.{GameIntent, MajorActionPreviewResponse, PreviewModifier}

/** Builders for the modifier-flow draft a session suite starts from. */
private[frontend] object ModifierFlowDrafts:
  /** Red's Recover flow in game "game": the preview response at `sequence`
    * carries `modifiers`, and none is selected yet.
    */
  def orderingDraft(sequence: Long,
      modifiers: Vector[PreviewModifier] = Vector.empty): ModifierFlowDraft =
    ModifierFlowDraft(command = GameIntent.StartWalker("recover", Vector.empty),
      baseParameters = Map.empty,
      preview = MajorActionPreviewResponse(sequence, "recover", modifiers,
        Vector.empty),
      selection = ModifierSelectionState.reconcile(None,
        ModifierSelectionContext("game", "red", sequence, "recover"),
        modifiers, "p"))
