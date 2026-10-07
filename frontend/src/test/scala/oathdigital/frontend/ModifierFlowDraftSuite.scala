package oathdigital.frontend

import oathdigital.protocol.{GameIntent, MajorActionPreviewResponse, PreviewModifier}

/** `fromPreview` is the one constructor the flow uses: the command by the
  * start, the fingerprint by the response's shape, the selection carried
  * through reconcile.
  */
class ModifierFlowDraftSuite extends munit.FunSuite:
  private val context = ModifierSelectionContext("game", "red", 9, "recover")
  private val start = GameIntent.StartWalker("recover", Vector.empty)
  private val modifier = PreviewModifier("adviser:p:denizen:a", "h.a", "A")
  private def response(modifiers: Vector[PreviewModifier]) =
    MajorActionPreviewResponse(9L, "recover", modifiers, Vector.empty)

  test("a preview with modifiers opens a draft with the documented fingerprint"):
    val draft = ModifierFlowDraft.fromPreview(start, Map.empty,
      response(Vector(modifier)), None, context)
    assertEquals(draft.command, start)
    assertEquals(draft.selection.previewFingerprint, "9:recover:adviser:p:denizen:a/h.a")
    assertEquals(draft.selection.context, context)
    assertEquals(draft.selection.candidates, Vector(modifier))
    assertEquals(draft.selection.selected, Vector.empty)

  test("the draft keeps the base parameters the preview was asked with"):
    val draft = ModifierFlowDraft.fromPreview(start, Map("procedure" -> "x"),
      response(Vector.empty), None, context)
    assertEquals(draft.baseParameters, Map("procedure" -> "x"))
    assertEquals(draft.selection.previewFingerprint, "9:recover:")

  test("the previous selection survives when context, candidates and fingerprint match"):
    val first = ModifierFlowDraft.fromPreview(start, Map.empty,
      response(Vector(modifier)), None, context)
    val chosen = first.selection.toggle(modifier)
    val again = ModifierFlowDraft.fromPreview(start, Map.empty,
      response(Vector(modifier)), Some(chosen), context)
    assertEquals(again.selection.selected, Vector(modifier))
    val moved = ModifierFlowDraft.fromPreview(start, Map.empty,
      response(Vector(modifier)), Some(chosen), context.copy(sequence = 10))
    assertEquals(moved.selection.selected, Vector.empty)
