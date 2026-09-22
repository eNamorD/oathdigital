package oathdigital.frontend

import org.scalajs.dom

/** `cardDetailsPopover` is the one place every rendered card -- in hand, on
  * the board, or revealed as an adviser -- turns a projected `CardDetails`
  * into DOM, so marking `implemented = false` here is what the whole UI
  * needs (`ServerUiSupport.scala`, `WorldBoardRenderer.scala`,
  * `FacedownAdviserRenderer.scala`, `ActionDecisionRenderer.scala` all call
  * it). Runs under jsdom (`Test / jsEnv` in `build.sbt`).
  */
class ServerUiSupportCardMarkerSuite extends munit.FunSuite {
  private def card(implemented: Boolean): CardDetails =
    CardDetails("d1", "denizen", "Old Oak", implemented = implemented)

  private def badges(node: dom.Element): Vector[dom.Element] =
    node.querySelectorAll(".card-unimplemented-badge").toVector
      .map(_.asInstanceOf[dom.Element])

  test("an implemented card's popover carries no unimplemented marker") {
    val node = ServerUiSupport.cardDetailsPopover(card(implemented = true))
    assertEquals(node.getAttribute("class"), "card-detail")
    assertEquals(node.getAttribute("aria-label"), "Old Oak")
    assertEquals(badges(node), Vector.empty)
  }

  test("an unimplemented card's popover is marked in its class, label, and a visible badge") {
    val node = ServerUiSupport.cardDetailsPopover(card(implemented = false))
    assertEquals(node.getAttribute("class"), "card-detail card-detail-unimplemented")
    assertEquals(node.getAttribute("aria-label"), "Old Oak (unimplemented)")
    val marked = badges(node)
    assertEquals(marked.size, 1)
    assertEquals(marked.head.textContent, "Unimplemented")
  }
}
