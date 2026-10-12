package oathdigital.frontend

import munit.FunSuite
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

final class HoverTipSuite extends FunSuite:
  /** A mounted tip whose timer and clock the test turns by hand. */
  private final class ManualTimerTip:
    var clock = 0.0
    var waiting = Vector.empty[() => Unit]
    val mount: dom.html.Element =
      dom.document.createElement("main").asInstanceOf[dom.html.Element]
    dom.document.body.appendChild(mount)
    val tips = new HoverTip(dom.document,
      (_, act) =>
        waiting :+= act
        val queued = act
        () => waiting = waiting.filterNot(_ eq queued),
      () => clock)
    tips.install()

    /** Runs whatever the tip had waiting, as its delay elapsing would. */
    def elapse(): Unit =
      val due = waiting
      waiting = Vector.empty
      due.foreach(_())

    def noted(label: String, note: String): dom.html.Button =
      val control = ServerUiSupport.button(label, "")
      HoverTip.set(control, note)
      mount.appendChild(control)
      control

    def tip: Option[dom.Element] =
      Option(dom.document.getElementById("hover-tip"))
        .filterNot(_.hasAttribute("hidden"))

    def close(): Unit =
      tips.uninstall()
      mount.remove()

  private val fixture = FunFixture[ManualTimerTip](_ => new ManualTimerTip,
    _.close())

  private def pointer(kind: String, target: dom.EventTarget,
      pointerType: String = "mouse",
      related: Option[dom.EventTarget] = None): Unit =
    // jsdom has no PointerEvent: a mouse event carries the pointer's kind.
    val event = new dom.MouseEvent(kind, js.Dynamic.literal(bubbles = true,
      relatedTarget = related.orNull).asInstanceOf[dom.MouseEventInit])
    js.Object.defineProperty(event.asInstanceOf[js.Object], "pointerType",
      js.Dynamic.literal(value = pointerType)
        .asInstanceOf[js.PropertyDescriptor])
    target.dispatchEvent(event)

  fixture.test("a resting pointer shows the note after the delay, in the tip") {
    page =>
      val control = page.noted("Recover", "Needs 1 Supply")
      pointer("pointerover", control)
      assertEquals(page.tip, None)
      page.elapse()
      val shown = page.tip.getOrElse(fail("no tip"))
      assertEquals(shown.textContent, "Needs 1 Supply")
      assertEquals(shown.getAttribute("role"), "tooltip")
      assertEquals(control.getAttribute("aria-describedby"), "hover-tip")
  }

  fixture.test("leaving the element hides the note and its description") {
    page =>
      val control = page.noted("Recover", "Needs 1 Supply")
      pointer("pointerover", control)
      page.elapse()
      pointer("pointerout", control, related = Some(page.mount))
      assertEquals(page.tip, None)
      assert(!control.hasAttribute("aria-describedby"))
  }

  fixture.test("leaving before the delay never shows the note") { page =>
    val control = page.noted("Recover", "Needs 1 Supply")
    pointer("pointerover", control)
    pointer("pointerout", control, related = Some(page.mount))
    page.elapse()
    assertEquals(page.tip, None)
  }

  fixture.test("moving onto the label inside the element keeps the note") {
    page =>
      val control = page.noted("", "Needs 1 Supply")
      val label = dom.document.createElement("span")
      control.appendChild(label)
      pointer("pointerover", control)
      page.elapse()
      pointer("pointerout", control, related = Some(label))
      assertEquals(page.tip.map(_.textContent), Some("Needs 1 Supply"))
  }

  fixture.test("a second note right after the first shows at once") { page =>
    val first = page.noted("Recover", "Needs 1 Supply")
    val second = page.noted("Search", "Needs 2 Supply")
    pointer("pointerover", first)
    page.elapse()
    pointer("pointerout", first, related = Some(second))
    page.clock += 100
    pointer("pointerover", second)
    assertEquals(page.tip.map(_.textContent), Some("Needs 2 Supply"))
  }

  fixture.test("a note long after the last one waits for the delay again") {
    page =>
      val first = page.noted("Recover", "Needs 1 Supply")
      val second = page.noted("Search", "Needs 2 Supply")
      pointer("pointerover", first)
      page.elapse()
      pointer("pointerout", first, related = Some(page.mount))
      page.clock += 1000
      pointer("pointerover", second)
      assertEquals(page.tip, None)
  }

  fixture.test("a touch passing over shows nothing") { page =>
    val control = page.noted("Recover", "Needs 1 Supply")
    pointer("pointerover", control, pointerType = "touch")
    page.elapse()
    assertEquals(page.tip, None)
  }

  fixture.test("a tap on a withheld control shows its note at once") { page =>
    val control = page.noted("Recover", "")
    HoverTip.withhold(control, "Needs 1 Supply")
    pointer("pointerdown", control, pointerType = "touch")
    control.click()
    assertEquals(page.tip.map(_.textContent), Some("Needs 1 Supply"))
    // The touch lifting off is not the pointer leaving.
    pointer("pointerout", control, pointerType = "touch")
    assert(page.tip.nonEmpty)
    pointer("pointerdown", page.mount, pointerType = "touch")
    assertEquals(page.tip, None)
  }

  fixture.test("a tap on a control that is not withheld shows nothing") {
    page =>
      val control = page.noted("Recover", "Gain a secret")
      control.click()
      assertEquals(page.tip, None)
  }

  fixture.test("a withheld control no longer answers its click") { page =>
    var clicked = false
    val control = page.noted("Recover", "")
    control.onclick = _ => clicked = true
    HoverTip.withhold(control, "Needs 1 Supply")
    control.click()
    assert(!clicked)
    assertEquals(control.getAttribute("aria-disabled"), "true")
    assert(!control.disabled)
  }

  fixture.test("focus shows the note at once and Escape hides it") { page =>
    val control = page.noted("Recover", "Needs 1 Supply")
    control.focus()
    assertEquals(page.tip.map(_.textContent), Some("Needs 1 Supply"))
    Dom.press(control, "Escape")
    assertEquals(page.tip, None)
  }

  fixture.test("a render that removes the element hides its note") { page =>
    val control = page.noted("Recover", "Needs 1 Supply")
    pointer("pointerover", control)
    page.elapse()
    control.remove()
    // The removal is reported after the current task, as a microtask.
    js.Promise.resolve[Unit](()).toFuture.map(_ =>
      assertEquals(page.tip, None))
  }

  /** The tip, 120 by 30, shown for an element drawn at `left`, `top`,
    * 60 by 40, in jsdom's 1024-wide window.
    */
  private def tipForElementAt(page: ManualTimerTip, left: Double,
      top: Double): dom.html.Element =
    val first = page.noted("Recover", "Needs 1 Supply")
    pointer("pointerover", first)
    page.elapse()
    val face = page.tip.getOrElse(fail("no tip"))
    for (name, size) <- Vector("offsetWidth" -> 120, "offsetHeight" -> 30) do
      js.Object.defineProperty(face.asInstanceOf[js.Object], name,
        js.Dynamic.literal(configurable = true, get = () => size)
          .asInstanceOf[js.PropertyDescriptor])
    val target = page.noted("Search", "Needs 2 Supply")
    target.asInstanceOf[js.Dynamic].getBoundingClientRect = () =>
      js.Dynamic.literal(left = left, top = top, width = 60, height = 40,
        right = left + 60, bottom = top + 40)
    pointer("pointerout", first, related = Some(target))
    pointer("pointerover", target)
    page.elapse()
    page.tip.getOrElse(fail("no tip")).asInstanceOf[dom.html.Element]

  fixture.test("the tip sits centered above its element, arrow on its middle") {
    page =>
      val shown = tipForElementAt(page, 100, 200)
      assertEquals(shown.getAttribute("data-side"), "above")
      assertEquals((shown.style.left, shown.style.top), ("70px", "162px"))
      assertEquals(shown.style.getPropertyValue("--tip-arrow"), "60px")
  }

  fixture.test("a tip near the window's edge stays inside it, arrow on the element") {
    page =>
      val shown = tipForElementAt(page, 970, 200)
      assertEquals(shown.style.left, "896px")
      assertEquals(shown.style.getPropertyValue("--tip-arrow"), "104px")
  }

  fixture.test("a tip with no room above sits below its element") { page =>
    val shown = tipForElementAt(page, 100, 2)
    assertEquals(shown.getAttribute("data-side"), "below")
    assertEquals(shown.style.top, "50px")
  }
