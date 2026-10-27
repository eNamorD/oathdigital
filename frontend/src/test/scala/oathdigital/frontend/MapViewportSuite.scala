package oathdigital.frontend

import org.scalajs.dom
import scala.scalajs.js

/** The map pane as one draggable surface: the wheel zooms, a pen drags like a
  * mouse, a touch keeps native panning. Runs under jsdom through `TestBrowser`,
  * which supplies the `ResizeObserver` the viewport observes.
  */
class MapViewportSuite extends munit.FunSuite:
  private final class Fixture:
    val browser = new TestBrowser()
    val viewport = dom.document.createElement("div").asInstanceOf[dom.html.Div]
    val content = dom.document.createElement("div").asInstanceOf[dom.html.Div]
    browser.mount.appendChild(viewport)
    var scales = Vector.empty[Double]
    var clicks = 0
    // jsdom has no pointer capture; the viewport only calls it while dragging.
    val dynamic = viewport.asInstanceOf[js.Dynamic]
    dynamic.setPointerCapture = (_: Double) => ()
    dynamic.hasPointerCapture = (_: Double) => false
    dynamic.releasePointerCapture = (_: Double) => ()
    val map = new MapViewport(viewport, content, scale => scales :+= scale)
    viewport.addEventListener("click", (_: dom.Event) => clicks += 1)

    def wheel(delta: Double, mode: Int = 0, ctrl: Boolean = false): dom.WheelEvent =
      val event = new dom.WheelEvent("wheel", new dom.WheelEventInit {
        bubbles = true; cancelable = true; deltaY = delta; deltaMode = mode
        ctrlKey = ctrl })
      viewport.dispatchEvent(event)
      event

    // jsdom has no PointerEvent either; a mouse event carries the pointer
    // fields the viewport reads.
    def pointer(kind: String, pointerType: String, x: Double): Unit =
      val event = new dom.MouseEvent(kind, new dom.MouseEventInit {
        bubbles = true; cancelable = true; button = 0; buttons = 1
        clientX = x; clientY = 10 })
      val fields = js.Dynamic.literal(pointerType = pointerType, pointerId = 1)
      js.Object.keys(fields).foreach(key =>
        js.Object.defineProperty(event, key, js.Dynamic.literal(value = fields.selectDynamic(key))
          .asInstanceOf[js.PropertyDescriptor]))
      viewport.dispatchEvent(event)

    // jsdom has no Touch: an event carries the fingers as a plain list of
    // the fields the viewport reads.
    def touch(kind: String, at: (Double, Double)*): dom.Event =
      val event = new dom.Event(kind, new dom.EventInit {
        bubbles = true; cancelable = true })
      val fingers = js.Array(at.map((x, y) =>
        js.Dynamic.literal(clientX = x, clientY = y))*)
      js.Object.defineProperty(event, "touches", js.Dynamic.literal(
        value = fingers).asInstanceOf[js.PropertyDescriptor])
      viewport.dispatchEvent(event)
      event

    def click(): Unit =
      viewport.dispatchEvent(new dom.MouseEvent("click", new dom.MouseEventInit {
        bubbles = true; cancelable = true; detail = 1 }))

    def close(): Unit =
      map.dispose()
      browser.close()

  test("a wheel notch zooms one step and never scrolls the pane"):
    val f = new Fixture
    try
      val in = f.wheel(-100)
      assert(in.defaultPrevented, "the wheel must not also scroll")
      assertEqualsDouble(f.scales.last, 1.25, 1e-9)
      f.wheel(100)
      assertEqualsDouble(f.scales.last, 1.0, 1e-9)
    finally f.close()

  test("a trackpad pinch arrives as a ctrl wheel and zooms instead of the page"):
    val f = new Fixture
    try
      val pinch = f.wheel(-20, ctrl = true)
      assert(pinch.defaultPrevented, "the browser must not zoom the page")
      assertEqualsDouble(f.scales.last, 1.25, 1e-9)
    finally f.close()

  test("a pen drags the map like a mouse and swallows the click after it"):
    val f = new Fixture
    try
      f.pointer("pointerdown", "pen", 10)
      f.pointer("pointermove", "pen", 40)
      assert(f.viewport.classList.contains("map-dragging"))
      f.click()
      assertEquals(f.clicks, 0)
    finally f.close()

  test("a touch keeps native panning and its click"):
    val f = new Fixture
    try
      f.pointer("pointerdown", "touch", 10)
      f.pointer("pointermove", "touch", 40)
      assert(!f.viewport.classList.contains("map-dragging"))
      f.click()
      assertEquals(f.clicks, 1)
    finally f.close()

  test("two fingers pinch the map about their midpoint, not the page"):
    val f = new Fixture
    try
      f.touch("touchstart", (0, 10), (100, 10))
      val spread = f.touch("touchmove", (0, 10), (125, 10))
      assert(spread.defaultPrevented, "the browser must not also move the page")
      assertEqualsDouble(f.scales.last, 1.25, 1e-9)
      // Each move zooms by how far the fingers moved since the last one.
      f.touch("touchmove", (0, 10), (100, 10))
      assertEqualsDouble(f.scales.last, 1.0, 1e-9)
      f.touch("touchend", (0, 10))
      val after = f.touch("touchmove", (0, 10))
      assert(!after.defaultPrevented)
    finally f.close()

  test("one finger leaves the pan to the browser"):
    val f = new Fixture
    try
      val before = f.scales.size
      f.touch("touchstart", (10, 10))
      val pan = f.touch("touchmove", (40, 10))
      assert(!pan.defaultPrevented)
      assertEquals(f.scales.size, before)
    finally f.close()
