package oathdigital.frontend

import oathdigital.protocol.PreviewModifier

class ModifierSelectionStateSuite extends munit.FunSuite {
  private val context = ModifierSelectionContext("g", "p", 4, "trade")
  private val first = PreviewModifier("adviser:p:denizen:a", "h.a", "A")
  private val second = PreviewModifier("site-card:s:denizen:b", "h.b", "B")

  test("selection preserves click order supports badges reorder toggle and keyboard") {
    val empty = ModifierSelectionState.reconcile(None, context,
      Vector(first, second), "preview-1")
    val chosen = empty.toggle(second).keyboard(first, "Enter")
    assertEquals(chosen.selected, Vector(second, first))
    assertEquals(chosen.ordinal(first), Some(2))
    assertEquals(chosen.moveEarlier(first).selected, Vector(first, second))
    assertEquals(chosen.keyboard(second, "ArrowUp").selected, Vector(second, first))
    assertEquals(chosen.toggle(second).selected, Vector(first))
  }

  test("game viewer sequence candidates and preview changes clear the draft") {
    val selected = ModifierSelectionState.reconcile(None, context,
      Vector(first), "preview-1").toggle(first)
    assertEquals(ModifierSelectionState.reconcile(Some(selected), context,
      Vector(first), "preview-1").selected, Vector(first))
    assert(ModifierSelectionState.reconcile(Some(selected), context.copy(sequence = 5),
      Vector(first), "preview-1").selected.isEmpty)
    assert(ModifierSelectionState.reconcile(Some(selected), context,
      Vector(first, second), "preview-1").selected.isEmpty)
    assert(ModifierSelectionState.reconcile(Some(selected), context,
      Vector(first), "preview-2").selected.isEmpty)
  }
}
