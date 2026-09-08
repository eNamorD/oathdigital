package oathdigital.frontend

import org.scalajs.dom
import scala.scalajs.js

// Pointer capture is implemented by supported browsers but absent from the
// project's scalajs-dom Element facade.
@js.native
private[frontend] trait PointerCapture extends js.Object {
  def setPointerCapture(id: Double): Unit = js.native
  def hasPointerCapture(id: Double): Boolean = js.native
  def releasePointerCapture(id: Double): Unit = js.native
}

/** Scales the existing DOM board while retaining native scroll and target access. */
private[frontend] final class MapViewport(
    viewport: dom.html.Div, content: dom.html.Div, onScale: Double => Unit) {
  private val surface = dom.document.createElement("div").asInstanceOf[dom.html.Div]
  surface.className = "map-surface"
  content.className = "map-content"
  surface.appendChild(content)
  viewport.appendChild(surface)
  private var state = MapViewState()
  private var dragging = false
  private var suppressClick = false
  private var start = Option.empty[(Double, Double, Double, Double)]
  private val capture = viewport.asInstanceOf[PointerCapture]

  private def bounds = MapBounds(viewport.clientWidth, viewport.clientHeight,
    content.offsetWidth, content.offsetHeight)

  private def paint(): Unit = {
    val b = bounds
    surface.style.width = s"${math.max(b.viewWidth, b.contentWidth * state.scale)}px"
    surface.style.height = s"${math.max(b.viewHeight, b.contentHeight * state.scale)}px"
    content.style.transform = s"scale(${state.scale})"
    content.style.left = s"${math.max(0, (b.viewWidth - b.contentWidth * state.scale) / 2)}px"
    content.style.top = s"${math.max(0, (b.viewHeight - b.contentHeight * state.scale) / 2)}px"
    viewport.scrollLeft = state.left
    viewport.scrollTop = state.top
    onScale(state.scale)
  }

  def refresh(reset: Boolean): Unit = {
    if (reset) state = MapViewState()
    state = state.resize(bounds)
    paint()
  }
  def reset(): Unit = refresh(reset = true)
  def zoomBy(factor: Double): Unit = {
    state = state.panTo(viewport.scrollLeft, viewport.scrollTop, bounds).zoomBy(factor, bounds)
    paint()
  }

  private val scroll: dom.Event => Unit = _ => {
    state = state.panTo(viewport.scrollLeft, viewport.scrollTop, bounds)
  }
  private val down: dom.PointerEvent => Unit = e => {
    // Touch uses native overflow panning; controls retain native editing behavior.
    if (e.pointerType == "mouse" && e.button == 0 &&
        e.target.isInstanceOf[dom.Element] &&
        e.target.asInstanceOf[dom.Element].closest("input,select,textarea") == null) {
      suppressClick = false
      start = Some((e.clientX, e.clientY, viewport.scrollLeft, viewport.scrollTop))
    }
  }
  private val move: dom.PointerEvent => Unit = e => start.foreach { case (x, y, left, top) =>
    if (e.buttons == 0) start = None
    else if (dragging || math.hypot(e.clientX - x, e.clientY - y) > 6) {
      if (!dragging) {
        capture.setPointerCapture(e.pointerId)
        dragging = true
        viewport.classList.add("map-dragging")
      }
      e.preventDefault()
      viewport.scrollLeft = left - (e.clientX - x)
      viewport.scrollTop = top - (e.clientY - y)
      suppressClick = true
    }
  }
  private val up: dom.PointerEvent => Unit = e => {
    if (capture.hasPointerCapture(e.pointerId)) capture.releasePointerCapture(e.pointerId)
    start = None
    dragging = false
    viewport.classList.remove("map-dragging")
  }
  private val click: dom.MouseEvent => Unit = e => {
    if (suppressClick && e.detail != 0) {
      e.preventDefault()
      e.stopImmediatePropagation()
    }
    suppressClick = false
  }
  private val key: dom.KeyboardEvent => Unit = e => {
    if (e.target == viewport) e.key match {
      case "+" | "=" => e.preventDefault(); zoomBy(1.25)
      case "-" => e.preventDefault(); zoomBy(0.8)
      case "0" => e.preventDefault(); reset()
      case _ => ()
    }
  }
  private val observer = new dom.ResizeObserver((_, _) => refresh(reset = false))
  observer.observe(viewport)
  observer.observe(content)
  viewport.addEventListener("scroll", scroll)
  viewport.addEventListener("pointerdown", down)
  viewport.addEventListener("pointermove", move)
  viewport.addEventListener("pointerup", up)
  viewport.addEventListener("pointercancel", up)
  viewport.addEventListener("click", click, true)
  viewport.addEventListener("keydown", key)

  def dispose(): Unit = {
    observer.disconnect()
    viewport.removeEventListener("scroll", scroll)
    viewport.removeEventListener("pointerdown", down)
    viewport.removeEventListener("pointermove", move)
    viewport.removeEventListener("pointerup", up)
    viewport.removeEventListener("pointercancel", up)
    viewport.removeEventListener("click", click, true)
    viewport.removeEventListener("keydown", key)
  }
}
