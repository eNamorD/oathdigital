package oathdigital.frontend

import org.scalajs.dom
import scala.scalajs.js

/** Dragging a card between and within a partition's zones, with the zones
  * showing where it would land while it is held.
  *
  * Built on pointer events, so one path serves a mouse, a pen and a finger:
  * a phone's browser never starts an HTML drag from a touch. A mouse or pen
  * lifts the card once it moves past the map's six-pixel threshold. A finger
  * must rest on the card first (`HoldMs`): a finger that moves sooner is
  * scrolling the pane, and the browser keeps it. While a card is held, the
  * pane's scrolling is held off, a copy of the card follows the pointer, and
  * the pane scrolls itself when the pointer nears its top or bottom edge.
  *
  * Every listener sits on the zones' shared parent, so the cards and zones
  * carry none of their own. Each move runs the move a drop would make --
  * the same `placeAt`, so a single slot's swap and a full zone's refusal
  * show too -- on a copy of the draft, and moves the drawn cards to match
  * it. Nothing is staged until the release. A release outside the zones, or
  * a gesture the browser takes over, puts the cards back where the staged
  * draft has them. The click that ends a drag is swallowed, so letting go
  * never also opens the card's inspection overlay.
  */
private[frontend] object PartitionDrag:
  /** How far a press moves before it is a drag, as on the map. */
  private val Threshold = 6.0
  /** How long a finger rests on a card before moving it drags the card
    * rather than scrolling the pane, in milliseconds. */
  private[frontend] val HoldMs = 250.0
  /** How near the pane's edge the pointer comes before the pane scrolls,
    * and how far it scrolls each frame there. */
  private val EdgePx = 48.0
  private val ScrollStepPx = 12.0

  /** A press on a card that may yet become a drag. */
  private final case class Press(card: dom.Element, item: String,
      pointerId: Double, touch: Boolean, x: Double, y: Double, at: Double)

  /** A card being dragged: the copy that follows the pointer, where on the
    * card it was taken, and the pane that scrolls under it. */
  private final class Lift(val press: Press, val ghost: dom.html.Element,
      val offsetX: Double, val offsetY: Double,
      val scroller: Option[dom.html.Element]):
    var x: Double = press.x
    var y: Double = press.y
    var frame: Option[Int] = None

  def attach(zones: dom.Element, draft: WalkerPartitionDraft,
      controls: TableControls): Unit =
    val cards = zones.querySelectorAll(".decision-option").toVector
    val cardFor = cards.map(card => card.getAttribute("data-option-id") -> card)
      .toMap
    var press = Option.empty[Press]
    var lift = Option.empty[Lift]
    var swallowClick = false

    /** The zone under (`x`, `y`), read off the page: the copy under the
      * pointer lets every hit through to what lies beneath it. */
    def zoneAt(x: Double, y: Double): Option[dom.Element] =
      Option(dom.document.elementFromPoint(x, y))
        .flatMap(node => Option(node.closest(".partition-zone")))
        .filter(zones.contains)

    /** Moves the drawn cards to where `shown` has them. */
    def arrange(shown: WalkerPartitionDraft): Unit =
      zones.querySelectorAll(".partition-zone").toVector.foreach(zone =>
        Option(zone.querySelector(".partition-options")).foreach(row =>
          shown.partition.itemsIn(zone.getAttribute("data-section-key"))
            .flatMap(cardFor.get).foreach(row.appendChild)))

    /** The draft a release of `item` at (`x`, `y`) would stage. The cards
      * the pointer has passed are read off the zone as drawn, which a
      * preview may have changed, and counted among the zone's cards in the
      * staged draft: a card a preview swapped in is not one of those.
      */
    def landing(item: String, x: Double, y: Double)
        : Option[WalkerPartitionDraft] =
      zoneAt(x, y).map { zone =>
        val key = zone.getAttribute("data-section-key")
        val others = zone.querySelectorAll(".decision-option").toVector
          .filterNot(_.getAttribute("data-option-id") == item)
        val passed = others.take(PartitionDropTarget.index(others.map(box),
          x, y)).map(_.getAttribute("data-option-id")).toSet
        draft.placeAt(item, key, draft.partition.itemsIn(key)
          .filterNot(_ == item).count(passed.contains))
      }

    /** Shows where the held card would land, or puts the cards back when
      * the pointer is outside the zones. */
    def preview(held: Lift): Unit =
      arrange(landing(held.press.item, held.x, held.y).getOrElse(draft))

    def follow(held: Lift, x: Double, y: Double): Unit =
      held.x = x
      held.y = y
      held.ghost.style.left = s"${x - held.offsetX}px"
      held.ghost.style.top = s"${y - held.offsetY}px"
      preview(held)

    /** Scrolls the pane while the pointer rests near its top or bottom
      * edge, a frame at a time, and shows where the card would land as the
      * cards pass under it. The pinned confirm strip is the bottom edge. */
    def scrollNearEdges(held: Lift): Unit =
      held.scroller.foreach { pane =>
        val area = pane.getBoundingClientRect()
        val bottom = Option(pane.querySelector(".partition-footer"))
          .map(_.getBoundingClientRect().top).filter(_ > area.top)
          .fold(area.bottom)(math.min(area.bottom, _))
        val step =
          if held.y < area.top + EdgePx then -ScrollStepPx
          else if held.y > bottom - EdgePx then ScrollStepPx
          else 0.0
        if step != 0 then
          pane.scrollTop += step
          preview(held)
        held.frame = Some(dom.window.requestAnimationFrame(_ =>
          if lift.contains(held) then scrollNearEdges(held)))
      }

    def start(taken: Press, x: Double, y: Double): Unit =
      val area = taken.card.getBoundingClientRect()
      val ghost = taken.card.cloneNode(true).asInstanceOf[dom.html.Element]
      // A picture of the card, not an option: nothing finds it as one.
      ghost.className = "partition-ghost"
      Vector("data-option-id", "aria-label").foreach(ghost.removeAttribute)
      ghost.setAttribute("aria-hidden", "true")
      ghost.style.width = s"${area.right - area.left}px"
      ghost.style.height = s"${area.bottom - area.top}px"
      // Fixed, so the pane's edges do not clip it, and among the zones, so
      // it is drawn at the pane's size of card.
      zones.appendChild(ghost)
      val held = Lift(taken, ghost, taken.x - area.left, taken.y - area.top,
        scrollerOf(zones))
      lift = Some(held)
      swallowClick = true
      taken.card.classList.remove("partition-pressed")
      taken.card.classList.add("dragging")
      capture(zones, taken.pointerId)
      follow(held, x, y)
      scrollNearEdges(held)

    /** Ends the gesture; `restore` puts the cards back where the staged
      * draft has them. Only a drag has moved them: a press that never lifted
      * its card leaves them alone, since a browser drops the click of a card
      * moved between press and release, even back to its own place. */
    def finish(restore: Boolean): Unit =
      val previewed = lift.nonEmpty
      lift.foreach { held =>
        held.frame.foreach(dom.window.cancelAnimationFrame)
        held.ghost.remove()
        held.press.card.classList.remove("dragging")
        release(zones, held.press.pointerId)
      }
      press.foreach(_.card.classList.remove("partition-pressed"))
      if restore && previewed then arrange(draft)
      press = None
      lift = None

    def ours(event: dom.Event): Option[Press] =
      press.filter(_.pointerId == pointerId(event))

    zones.addEventListener("pointerdown", (event: dom.MouseEvent) =>
      if press.isEmpty && event.button == 0 then
        cardOf(event.target).filter(zones.contains).foreach { card =>
          val touch = pointerType(event) == "touch"
          press = Some(Press(card, card.getAttribute("data-option-id"),
            pointerId(event), touch, event.clientX, event.clientY,
            event.timeStamp))
          swallowClick = false
          // The ring a finger sees once its card would come with it.
          if touch then dom.window.setTimeout(() =>
            press.filter(p => p.card == card && lift.isEmpty)
              .foreach(_.card.classList.add("partition-pressed")), HoldMs)
        })
    zones.addEventListener("pointermove", (event: dom.MouseEvent) =>
      ours(event).foreach { taken =>
        lift match
          case Some(held) => follow(held, event.clientX, event.clientY)
          case None =>
            if math.hypot(event.clientX - taken.x, event.clientY - taken.y) >
                Threshold then
              if taken.touch && event.timeStamp - taken.at < HoldMs then
                finish(restore = false)
              else start(taken, event.clientX, event.clientY)
      })
    zones.addEventListener("pointerup", (event: dom.MouseEvent) =>
      ours(event).foreach { _ =>
        val moved = lift.flatMap(held =>
          landing(held.press.item, event.clientX, event.clientY))
          .filter(_ != draft)
        finish(restore = moved.isEmpty)
        moved.foreach(next => controls.stage(Draft.Partition(next)))
      })
    zones.addEventListener("pointercancel", (event: dom.Event) =>
      ours(event).foreach(_ => finish(restore = true)))
    // A held card keeps the pane from scrolling under the finger. The
    // browser asks before it scrolls only of a listener that may refuse.
    zones.addEventListener("touchmove", (event: dom.Event) =>
      if lift.nonEmpty then event.preventDefault(),
      js.Dynamic.literal(passive = false)
        .asInstanceOf[dom.EventListenerOptions])
    // A finger resting on a card asks for no menu.
    zones.addEventListener("contextmenu", (event: dom.Event) =>
      if press.exists(_.touch) then event.preventDefault())
    zones.addEventListener("click", (event: dom.Event) =>
      if swallowClick then
        swallowClick = false
        event.preventDefault()
        event.stopImmediatePropagation(),
      true)

  private def cardOf(target: dom.EventTarget): Option[dom.Element] =
    target match
      case node: dom.Element => Option(node.closest(".decision-option"))
      case _ => None

  /** The nearest ancestor that scrolls vertically: the Actions pane. */
  private def scrollerOf(node: dom.Element): Option[dom.html.Element] =
    Iterator.iterate(Option(node.parentNode))(_.flatMap(parent =>
      Option(parent.parentNode))).takeWhile(_.nonEmpty).flatten
      .collectFirst { case element: dom.html.Element
          if Set("auto", "scroll").contains(
            dom.window.getComputedStyle(element).overflowY) => element }

  // jsdom has no PointerEvent: a test's mouse event carries the fields.
  private def pointerType(event: dom.Event): String =
    event.asInstanceOf[js.Dynamic].pointerType
      .asInstanceOf[js.UndefOr[String]].getOrElse("mouse")

  private def pointerId(event: dom.Event): Double =
    event.asInstanceOf[js.Dynamic].pointerId
      .asInstanceOf[js.UndefOr[Double]].getOrElse(1.0)

  // Capture keeps the moves coming to the zones once the pointer leaves
  // them. jsdom has none.
  private def capture(node: dom.Element, id: Double): Unit =
    val dynamic = node.asInstanceOf[js.Dynamic]
    if js.typeOf(dynamic.setPointerCapture) == "function" then
      try dynamic.setPointerCapture(id)
      catch case _: Throwable => ()

  private def release(node: dom.Element, id: Double): Unit =
    val dynamic = node.asInstanceOf[js.Dynamic]
    if js.typeOf(dynamic.releasePointerCapture) == "function" then
      try dynamic.releasePointerCapture(id)
      catch case _: Throwable => ()

  private def box(card: dom.Element): PartitionDropTarget.Box =
    val rect = card.getBoundingClientRect()
    PartitionDropTarget.Box(rect.left, rect.top, rect.right, rect.bottom)
