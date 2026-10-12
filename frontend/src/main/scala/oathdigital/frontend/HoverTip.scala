package oathdigital.frontend

import org.scalajs.dom
import scala.scalajs.js

/** The table's hover note, drawn in the table's own face in place of the
  * browser's `title` tooltip, which is slow to appear and wears the system's
  * chrome.
  *
  * An element asks for a note with `HoverTip.set`, which stores the text in
  * `data-tip`. One tip element serves the whole document: delegated
  * listeners show it over the nearest element that carries a note, so no
  * render code wires events of its own.
  *
  * - A mouse or pen shows it after `Delay`, and at once while another note
  *   was showing within `Warm`, so reading along a row of options does not
  *   wait at every one.
  * - Keyboard focus shows it at once.
  * - A touch screen cannot hover, so a tap on a withheld control
  *   (`aria-disabled`) shows its note instead; any other tap acts as usual.
  * - Leaving, blurring, pressing elsewhere, scrolling and Escape hide it, and
  *   so does a render that removes its element.
  *
  * The tip neither fades nor moves (DESIGN.md, "No animation"). While it
  * shows, the element it describes names it in `aria-describedby`.
  *
  * `after` and `now` are the browser's timer and clock; the suite passes its
  * own.
  */
private[frontend] final class HoverTip(document: dom.html.Document,
    after: (Double, () => Unit) => (() => Unit),
    now: () => Double):
  import HoverTip.*

  private var tip = Option.empty[dom.html.Element]
  private var shown = Option.empty[dom.Element]
  private var pending: () => Unit = () => ()
  private var lastShown = Double.NegativeInfinity
  private var describes = false
  private var listeners = Vector.empty[(String, js.Function1[dom.Event, Unit], Boolean)]
  private var observer = Option.empty[dom.MutationObserver]

  def install(): Unit =
    listen("pointerover", false)(event => if !touch(event) then
      noted(event.target).filterNot(shown.contains).foreach { target =>
        pending()
        if now() - lastShown <= Warm then show(target)
        else pending = after(Delay, () => show(target))
      })
    listen("pointerout", false)(event => if !touch(event) then
      val into = noted(event.asInstanceOf[dom.MouseEvent].relatedTarget)
      if into.isEmpty || into != noted(event.target) then hide())
    listen("focusin", false)(event => noted(event.target)
      .filter(focusVisible).foreach(show))
    listen("focusout", false)(event =>
      if noted(event.target) == shown then hide())
    listen("keydown", false)(event =>
      if event.asInstanceOf[dom.KeyboardEvent].key == "Escape" then hide())
    // A press anywhere but on the withheld control the note explains closes
    // it; the click that follows a press on that control opens it.
    listen("pointerdown", true)(event =>
      if shown.isEmpty || noted(event.target).filter(withheld) != shown then
        hide())
    listen("click", true)(event =>
      noted(event.target).filter(withheld).foreach(show))
    listen("scroll", true)(_ => hide())
    val watch = new dom.MutationObserver((_, _) =>
      if shown.exists(target => !target.isConnected) then hide())
    watch.observe(document.documentElement, new dom.MutationObserverInit {
      childList = true; subtree = true })
    observer = Some(watch)

  /** Removes the listeners and the tip; for suites that install their own. */
  def uninstall(): Unit =
    hide()
    listeners.foreach((name, handler, capture) =>
      document.removeEventListener(name, handler, capture))
    listeners = Vector.empty
    observer.foreach(_.disconnect())
    observer = None
    tip.foreach(_.remove())
    tip = None

  private def listen(name: String, capture: Boolean)(
      handler: dom.Event => Unit): Unit =
    val bound: js.Function1[dom.Event, Unit] = handler
    document.addEventListener(name, bound, capture)
    listeners :+= ((name, bound, capture))

  private def show(target: dom.Element): Unit =
    pending()
    pending = () => ()
    val note = Option(target.getAttribute(Attribute)).filter(_.nonEmpty)
    if target.isConnected then note.foreach { text =>
      if !shown.contains(target) then hide()
      val face = tip.getOrElse(create())
      face.textContent = text
      face.removeAttribute("hidden")
      place(face, target)
      if !target.hasAttribute("aria-describedby") then
        target.setAttribute("aria-describedby", TipId)
        describes = true
      shown = Some(target)
      lastShown = now()
    }

  private def hide(): Unit =
    pending()
    pending = () => ()
    shown.foreach { target =>
      if describes then target.removeAttribute("aria-describedby")
      lastShown = now()
    }
    describes = false
    shown = None
    tip.foreach(_.setAttribute("hidden", ""))

  private def create(): dom.html.Element =
    val face = document.createElement("div").asInstanceOf[dom.html.Element]
    face.id = TipId
    face.className = "hover-tip"
    face.setAttribute("role", "tooltip")
    face.setAttribute("hidden", "")
    document.body.appendChild(face)
    tip = Some(face)
    face

  /** Above the element, centered on it, or below where the top of the
    * window leaves no room; always inside the window, with the arrow still
    * pointing at the element's middle.
    */
  private def place(face: dom.html.Element, target: dom.Element): Unit =
    // Measured at the window's corner, so the last place it showed does not
    // squeeze its width.
    face.style.left = "0px"
    face.style.top = "0px"
    val box = target.getBoundingClientRect()
    val width = face.offsetWidth.toDouble
    val height = face.offsetHeight.toDouble
    val view = document.defaultView.innerWidth.toDouble
    val middle = box.left + box.width / 2
    val left = math.max(Margin,
      math.min(middle - width / 2, view - width - Margin))
    val above = box.top - height - Gap
    val below = above < Margin
    face.style.left = s"${left}px"
    face.style.top = s"${if below then box.bottom + Gap else above}px"
    face.style.setProperty("--tip-arrow", s"${middle - left}px")
    face.setAttribute("data-side", if below then "below" else "above")

private[frontend] object HoverTip:
  val Attribute = "data-tip"
  private val TipId = "hover-tip"
  /** How long a pointer rests before the note shows, in milliseconds. */
  private val Delay = 150.0
  /** How soon after one note another shows at once, in milliseconds. */
  private val Warm = 300.0
  /** The space between the tip and its element, and the window's edge. */
  private val Gap = 8.0
  private val Margin = 8.0

  /** Gives `target` the hover note `text`. */
  def set(target: dom.Element, text: String): Unit =
    target.setAttribute(Attribute, text)

  /** Shows `control` as not to be used, with `reason` as its note. Unlike
    * a disabled button it still takes focus and a tap, and both name the
    * reason; it no longer answers its click.
    */
  def withhold(control: dom.html.Button, reason: String): Unit =
    set(control, reason)
    control.setAttribute("aria-disabled", "true")
    control.onclick = null

  /** Draws hover notes for the page's document. */
  def install(): Unit =
    new HoverTip(dom.document,
      (delay, act) =>
        val handle = dom.window.setTimeout(() => act(), delay)
        () => dom.window.clearTimeout(handle),
      () => js.Date.now()).install()

  private def noted(target: dom.EventTarget | Null): Option[dom.Element] =
    target match
      case element: dom.Element =>
        Option(element.closest(s"[$Attribute]"))
      case _ => None

  /** A control shown but not to be used; its note says why. */
  private def withheld(target: dom.Element): Boolean =
    target.getAttribute("aria-disabled") == "true"

  private def touch(event: dom.Event): Boolean =
    event.asInstanceOf[js.Dynamic].pointerType.asInstanceOf[js.UndefOr[String]]
      .contains("touch")

  /** Focus from the keyboard, not the focus a click leaves behind; where
    * the selector is unknown, any focus.
    */
  private def focusVisible(target: dom.Element): Boolean =
    try target.matches(":focus-visible")
    catch case _: Throwable => true
