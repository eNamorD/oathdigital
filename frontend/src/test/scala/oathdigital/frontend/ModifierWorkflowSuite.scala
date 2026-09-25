package oathdigital.frontend

import oathdigital.protocol.{GameIntent, MajorActionPreviewResponse, PreviewModifier}

/** `fromPreview` is the one constructor both flow entries use: stage by the
  * response, fingerprint by its shape, selection carried through reconcile.
  */
class ModifierWorkflowSuite extends munit.FunSuite:
  private val context = ModifierSelectionContext("game", "red", 9, "recover")
  private val modifier = PreviewModifier("adviser:p:denizen:a", "h.a", "A")
  private def response(modifiers: Vector[PreviewModifier]) =
    MajorActionPreviewResponse(9L, "recover", modifiers, Vector.empty, Vector.empty)

  test("a preview with modifiers starts in Ordering with the documented fingerprint"):
    val workflow = ModifierWorkflow.fromPreview(
      Some(GameIntent.StartWalker("recover", Vector.empty)), None, Map.empty,
      response(Vector(modifier)), None, context)
    assertEquals(workflow.stage, ModifierWorkflowStage.Ordering)
    assertEquals(workflow.command, Some(GameIntent.StartWalker("recover", Vector.empty)))
    assertEquals(workflow.actionKind, None)
    assertEquals(workflow.selection.previewFingerprint, "9:recover:adviser:p:denizen:a/h.a")
    assertEquals(workflow.selection.context, context)
    assertEquals(workflow.selection.candidates, Vector(modifier))
    assertEquals(workflow.selection.selected, Vector.empty)

  test("a preview without modifiers starts in Targets"):
    val workflow = ModifierWorkflow.fromPreview(None, Some("travel"),
      Map("procedure" -> "x"), response(Vector.empty), None, context)
    assertEquals(workflow.stage, ModifierWorkflowStage.Targets)
    assertEquals(workflow.actionKind, Some("travel"))
    assertEquals(workflow.baseParameters, Map("procedure" -> "x"))
    assertEquals(workflow.selection.previewFingerprint, "9:recover:")

  test("the previous selection survives when context, candidates and fingerprint match"):
    val first = ModifierWorkflow.fromPreview(None, Some("recover"), Map.empty,
      response(Vector(modifier)), None, context)
    val chosen = first.selection.toggle(modifier)
    val again = ModifierWorkflow.fromPreview(None, Some("recover"), Map.empty,
      response(Vector(modifier)), Some(chosen), context)
    assertEquals(again.selection.selected, Vector(modifier))
    val moved = ModifierWorkflow.fromPreview(None, Some("recover"), Map.empty,
      response(Vector(modifier)), Some(chosen), context.copy(sequence = 10))
    assertEquals(moved.selection.selected, Vector.empty)
