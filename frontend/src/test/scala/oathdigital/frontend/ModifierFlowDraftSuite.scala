package oathdigital.frontend

import oathdigital.protocol.{GameIntent, MajorActionPreviewResponse, PreviewModifier}

/** `fromPreview` is the one constructor both flow entries use: stage by the
  * response, fingerprint by its shape, selection carried through reconcile.
  */
class ModifierFlowDraftSuite extends munit.FunSuite:
  private val context = ModifierSelectionContext("game", "red", 9, "recover")
  private val modifier = PreviewModifier("adviser:p:denizen:a", "h.a", "A")
  private def response(modifiers: Vector[PreviewModifier]) =
    MajorActionPreviewResponse(9L, "recover", modifiers, Vector.empty, Vector.empty)

  test("a preview with modifiers starts in Ordering with the documented fingerprint"):
    val draft = ModifierFlowDraft.fromPreview(
      Some(GameIntent.StartWalker("recover", Vector.empty)), None, Map.empty,
      response(Vector(modifier)), None, context)
    assertEquals(draft.stage, ModifierFlowStage.Ordering)
    assertEquals(draft.command, Some(GameIntent.StartWalker("recover", Vector.empty)))
    assertEquals(draft.actionKind, None)
    assertEquals(draft.selection.previewFingerprint, "9:recover:adviser:p:denizen:a/h.a")
    assertEquals(draft.selection.context, context)
    assertEquals(draft.selection.candidates, Vector(modifier))
    assertEquals(draft.selection.selected, Vector.empty)

  test("a preview without modifiers starts in Targets"):
    val draft = ModifierFlowDraft.fromPreview(None, Some("travel"),
      Map("procedure" -> "x"), response(Vector.empty), None, context)
    assertEquals(draft.stage, ModifierFlowStage.Targets)
    assertEquals(draft.actionKind, Some("travel"))
    assertEquals(draft.baseParameters, Map("procedure" -> "x"))
    assertEquals(draft.selection.previewFingerprint, "9:recover:")

  test("the previous selection survives when context, candidates and fingerprint match"):
    val first = ModifierFlowDraft.fromPreview(None, Some("recover"), Map.empty,
      response(Vector(modifier)), None, context)
    val chosen = first.selection.toggle(modifier)
    val again = ModifierFlowDraft.fromPreview(None, Some("recover"), Map.empty,
      response(Vector(modifier)), Some(chosen), context)
    assertEquals(again.selection.selected, Vector(modifier))
    val moved = ModifierFlowDraft.fromPreview(None, Some("recover"), Map.empty,
      response(Vector(modifier)), Some(chosen), context.copy(sequence = 10))
    assertEquals(moved.selection.selected, Vector.empty)
