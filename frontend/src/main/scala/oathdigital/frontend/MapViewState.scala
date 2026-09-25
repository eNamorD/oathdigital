package oathdigital.frontend

private[frontend] final case class MapBounds(
    viewWidth: Double, viewHeight: Double, contentWidth: Double, contentHeight: Double):
  def fitScale: Double = math.min(1.0, math.max(0.001,
    math.min(math.max(1, viewWidth) / math.max(1, contentWidth),
      math.max(1, viewHeight) / math.max(1, contentHeight))))

/** View-only geometry. Like Arcs, fit is independent of game state and panning
  * is constrained to keep the board recoverable within its viewport. */
private[frontend] final case class MapViewState(
    scale: Double = 1, fit: Boolean = true, left: Double = 0, top: Double = 0):
  def resize(bounds: MapBounds): MapViewState =
    val next = if fit then copy(scale = bounds.fitScale, left = 0, top = 0)
      else copy(scale = math.max(bounds.fitScale, scale))
    next.panTo(next.left, next.top, bounds)

  def panTo(x: Double, y: Double, bounds: MapBounds): MapViewState = copy(
    left = math.max(0, math.min(x, bounds.contentWidth * scale - bounds.viewWidth)),
    top = math.max(0, math.min(y, bounds.contentHeight * scale - bounds.viewHeight)))

  /** Zooms about the viewport centre: the buttons and the keys. */
  def zoomBy(factor: Double, bounds: MapBounds): MapViewState =
    zoomAt(factor, bounds.viewWidth / 2, bounds.viewHeight / 2, bounds)

  /** Zooms about a point in viewport pixels, so the world under the pointer
    * stays under the pointer. When fitted content sits inset in the viewport
    * the inset changes with the scale, which is why both insets are part of
    * the sum. */
  def zoomAt(factor: Double, anchorX: Double, anchorY: Double, bounds: MapBounds)
      : MapViewState =
    val next = math.max(bounds.fitScale, math.min(3, scale * factor))
    def anchoredScroll(scroll: Double, anchor: Double, view: Double, content: Double)
        : Double =
      val oldInset = math.max(0, (view - content * scale) / 2)
      val newInset = math.max(0, (view - content * next) / 2)
      (scroll + anchor - oldInset) * next / scale + newInset - anchor
    copy(scale = next, fit = false).panTo(
      anchoredScroll(left, anchorX, bounds.viewWidth, bounds.contentWidth),
      anchoredScroll(top, anchorY, bounds.viewHeight, bounds.contentHeight), bounds)

private[frontend] object MapViewState:
  /** One zoom step, shared by the buttons, the keys and one wheel notch. */
  val Step = 1.25
  /** A mouse notch reports 100 pixels in Chrome and 3 lines in Firefox; both
    * are one step. A page is the viewport. */
  private val NotchPixels = 100.0
  private val LinePixels = NotchPixels / 3
  /** A trackpad pinch reports a few pixels per event, so it counts more. */
  private val PinchGain = 5.0

  /** The zoom factor for one wheel event. A full notch in either direction is
    * one step and a flick never exceeds it; a smaller delta is a fraction of
    * a step, which keeps a trackpad smooth. `deltaMode` follows the DOM: 0 is
    * pixels, 1 lines, 2 pages. */
  def wheelFactor(deltaY: Double, deltaMode: Int, pinch: Boolean, viewHeight: Double)
      : Double =
    val pixels = deltaMode match
      case 1 => deltaY * LinePixels
      case 2 => deltaY * viewHeight
      case _ => deltaY
    val gained = if pinch then pixels * PinchGain else pixels
    val clamped = math.max(-NotchPixels, math.min(NotchPixels, gained))
    math.exp(-clamped / NotchPixels * math.log(Step))
