package oathdigital.frontend

private[frontend] final case class MapBounds(
    viewWidth: Double, viewHeight: Double, contentWidth: Double, contentHeight: Double) {
  def fitScale: Double = math.min(1.0, math.max(0.001,
    math.min(math.max(1, viewWidth) / math.max(1, contentWidth),
      math.max(1, viewHeight) / math.max(1, contentHeight))))
}

/** View-only geometry. Like Arcs, fit is independent of game state and panning
  * is constrained to keep the board recoverable within its viewport. */
private[frontend] final case class MapViewState(
    scale: Double = 1, fit: Boolean = true, left: Double = 0, top: Double = 0) {
  def resize(bounds: MapBounds): MapViewState = {
    val next = if (fit) copy(scale = bounds.fitScale, left = 0, top = 0)
      else copy(scale = math.max(bounds.fitScale, scale))
    next.panTo(next.left, next.top, bounds)
  }

  def panTo(x: Double, y: Double, bounds: MapBounds): MapViewState = copy(
    left = math.max(0, math.min(x, bounds.contentWidth * scale - bounds.viewWidth)),
    top = math.max(0, math.min(y, bounds.contentHeight * scale - bounds.viewHeight)))

  def zoomBy(factor: Double, bounds: MapBounds): MapViewState = {
    val next = math.max(bounds.fitScale, math.min(3, scale * factor))
    def centeredScroll(scroll: Double, view: Double, content: Double): Double = {
      val oldInset = math.max(0, (view - content * scale) / 2)
      val newInset = math.max(0, (view - content * next) / 2)
      (scroll + view / 2 - oldInset) * next / scale + newInset - view / 2
    }
    copy(scale = next, fit = false).panTo(
      centeredScroll(left, bounds.viewWidth, bounds.contentWidth),
      centeredScroll(top, bounds.viewHeight, bounds.contentHeight), bounds)
  }
}
