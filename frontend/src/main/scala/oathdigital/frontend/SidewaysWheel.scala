package oathdigital.frontend

import org.scalajs.dom
import scala.scalajs.js

/** Lets a mouse wheel scroll a sideways strip, the Players pane's row of
  * boards, which never scrolls vertically.
  *
  * A wheel turn that is mostly vertical scrolls the strip sideways by the
  * same distance, while the strip has further to go that way. A trackpad's
  * sideways swipe already scrolls it and is left to the browser, as is a
  * turn at either end of the strip, so the page around it behaves as before.
  */
private[frontend] object SidewaysWheel:
  def attach(strip: dom.html.Element): () => Unit =
    val wheel: dom.WheelEvent => Unit = event =>
      pixels(event, strip.clientWidth)
        .filter(_ => math.abs(event.deltaY) > math.abs(event.deltaX))
        .filter(step => room(strip, step))
        .foreach { step =>
          event.preventDefault()
          strip.scrollLeft += step
        }
    strip.addEventListener("wheel", wheel, js.Dynamic.literal(passive = false)
      .asInstanceOf[dom.EventListenerOptions])
    () => strip.removeEventListener("wheel", wheel)

  private def pixels(event: dom.WheelEvent, page: Double): Option[Double] =
    val step = event.deltaMode match
      case 1 => event.deltaY * MapViewState.LinePixels
      case 2 => event.deltaY * page
      case _ => event.deltaY
    Option.when(step != 0)(step)

  /** Whether the strip can move `step` pixels sideways at all. */
  private def room(strip: dom.html.Element, step: Double): Boolean =
    if step < 0 then strip.scrollLeft > 0
    else strip.scrollLeft + strip.clientWidth < strip.scrollWidth - 1
