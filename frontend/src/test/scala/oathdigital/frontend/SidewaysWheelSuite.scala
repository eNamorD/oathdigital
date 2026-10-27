package oathdigital.frontend

import org.scalajs.dom
import scala.scalajs.js

/** A mouse wheel scrolls the Players strip sideways while it has room, and
  * leaves every other wheel to the browser. jsdom lays nothing out, so the
  * strip's widths and its scroll position are given to it.
  */
class SidewaysWheelSuite extends munit.FunSuite:
  private final class Strip(scrollWidth: Double, clientWidth: Double = 400):
    val node = dom.document.createElement("div").asInstanceOf[dom.html.Div]
    var left = 0.0
    private def define(name: String, descriptor: js.Dynamic): Unit =
      js.Object.defineProperty(node, name,
        descriptor.asInstanceOf[js.PropertyDescriptor])
    define("scrollWidth", js.Dynamic.literal(get = () => scrollWidth))
    define("clientWidth", js.Dynamic.literal(get = () => clientWidth))
    define("scrollLeft", js.Dynamic.literal(get = () => left,
      set = (value: Double) => left = math.max(0,
        math.min(scrollWidth - clientWidth, value))))
    dom.document.body.appendChild(node)
    val detach = SidewaysWheel.attach(node)

    def wheel(deltaY: Double, deltaX: Double = 0, mode: Int = 0)
        : dom.WheelEvent =
      val init = new dom.WheelEventInit {}
      init.bubbles = true
      init.cancelable = true
      init.deltaY = deltaY
      init.deltaX = deltaX
      init.deltaMode = mode
      val event = new dom.WheelEvent("wheel", init)
      node.dispatchEvent(event)
      event

    def close(): Unit =
      detach()
      node.remove()

  test("a wheel turn scrolls the strip sideways, not the page"):
    val strip = Strip(scrollWidth = 1000)
    try
      val turn = strip.wheel(100)
      assert(turn.defaultPrevented)
      assertEqualsDouble(strip.left, 100, 1e-9)
      strip.wheel(-40)
      assertEqualsDouble(strip.left, 60, 1e-9)
    finally strip.close()

  test("a line-mode wheel moves the strip by the map's line"):
    val strip = Strip(scrollWidth = 1000)
    try
      strip.wheel(3, mode = 1)
      assertEqualsDouble(strip.left, 3 * MapViewState.LinePixels, 1e-9)
    finally strip.close()

  test("a trackpad's sideways swipe is left to the browser"):
    val strip = Strip(scrollWidth = 1000)
    try
      val swipe = strip.wheel(deltaY = 5, deltaX = 60)
      assert(!swipe.defaultPrevented)
      assertEqualsDouble(strip.left, 0, 1e-9)
    finally strip.close()

  test("at either end of the strip the wheel passes on"):
    val strip = Strip(scrollWidth = 1000)
    try
      assert(!strip.wheel(-100).defaultPrevented, "already at the start")
      strip.left = 600
      assert(!strip.wheel(100).defaultPrevented, "already at the end")
    finally strip.close()

  test("a strip with nothing to scroll leaves the wheel alone"):
    val strip = Strip(scrollWidth = 400)
    try assert(!strip.wheel(100).defaultPrevented)
    finally strip.close()
