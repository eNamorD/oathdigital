package oathdigital.frontend

import org.scalajs.dom

/** Keeps press-and-drag and press-and-release apart on one element.
  *
  * `partitionOption` builds a `draggable` article with a clickable card
  * inside it, so without a threshold the drop would also open the inspection
  * overlay. `MapViewport` solves the same problem the same way at capture
  * phase, with the same six-pixel threshold; the decision panel is outside the
  * map, so it needs its own.
  */
private[frontend] object DragClickGuard {
  private val Threshold = 6.0

  def attach(node: dom.html.Element): Unit = {
    var start = Option.empty[(Double, Double)]
    var suppress = false
    node.addEventListener("mousedown", (event: dom.MouseEvent) => {
      start = Some((event.clientX, event.clientY))
      suppress = false
    })
    node.addEventListener("mousemove", (event: dom.MouseEvent) =>
      start.foreach { case (x, y) =>
        if (math.hypot(event.clientX - x, event.clientY - y) > Threshold)
          suppress = true
      })
    node.addEventListener("dragstart", (_: dom.Event) => suppress = true)
    node.addEventListener("click", (event: dom.Event) => {
      if (suppress) { event.preventDefault(); event.stopImmediatePropagation() }
      suppress = false
      start = None
    }, true)
  }
}
