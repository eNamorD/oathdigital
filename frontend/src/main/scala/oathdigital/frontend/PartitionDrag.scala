package oathdigital.frontend

import org.scalajs.dom

/** Dragging a card between and within a partition's zones, with the zones
  * showing where it would land while it is held.
  *
  * Every listener sits on the zones' shared parent, so the cards and zones
  * carry none of their own. While a card is held, each `dragover` runs the
  * move a drop would make -- the same `placeAt`, so a single slot's swap and
  * a full zone's refusal show too -- on a copy of the draft, and moves the
  * drawn cards to match it. Nothing is staged until the drop. A drag that
  * ends anywhere else, or leaves the zones, puts the cards back where the
  * staged draft has them.
  */
private[frontend] object PartitionDrag:
  def attach(zones: dom.Element, draft: WalkerPartitionDraft,
      controls: TableControls): Unit =
    val cards = zones.querySelectorAll(".decision-option").toVector
    val cardFor = cards.map(card => card.getAttribute("data-option-id") -> card)
      .toMap
    // A browser hides the transfer's data until the drop, so the held card
    // is remembered from its `dragstart`.
    var held = Option.empty[String]

    def zoneOf(event: dom.Event): Option[dom.Element] = event.target match
      case node: dom.Element => Option(node.closest(".partition-zone"))
      case _ => None

    /** Moves the drawn cards to where `shown` has them. */
    def arrange(shown: WalkerPartitionDraft): Unit =
      zones.querySelectorAll(".partition-zone").toVector.foreach(zone =>
        Option(zone.querySelector(".partition-options")).foreach(row =>
          shown.partition.itemsIn(zone.getAttribute("data-section-key"))
            .flatMap(cardFor.get).foreach(row.appendChild)))

    /** The draft a drop of `item` at the event's pointer would stage. The
      * cards the pointer has passed are read off the zone as drawn, which a
      * preview may have changed, and counted among the zone's cards in the
      * staged draft: a card a preview swapped in is not one of those.
      */
    def landing(event: dom.Event, item: String): Option[WalkerPartitionDraft] =
      zoneOf(event).map { zone =>
        val key = zone.getAttribute("data-section-key")
        val others = zone.querySelectorAll(".decision-option").toVector
          .filterNot(_.getAttribute("data-option-id") == item)
        val pointer = event.asInstanceOf[dom.MouseEvent]
        val passed = others.take(PartitionDropTarget.index(others.map(box),
          pointer.clientX, pointer.clientY))
          .map(_.getAttribute("data-option-id")).toSet
        draft.placeAt(item, key, draft.partition.itemsIn(key)
          .filterNot(_ == item).count(passed.contains))
      }

    def heldCard: Option[dom.Element] = held.flatMap(cardFor.get)

    zones.addEventListener("dragstart", (event: dom.Event) =>
      zoneOf(event).flatMap(_ => event.target match
        case node: dom.Element => Option(node.closest(".decision-option"))
        case _ => None
      ).foreach { card =>
        val item = card.getAttribute("data-option-id")
        held = Some(item)
        event.asInstanceOf[dom.DragEvent].dataTransfer
          .setData("text/plain", item)
        // After the browser has taken its picture of the card, so the copy
        // under the pointer is not dimmed with it.
        dom.window.setTimeout(() =>
          if held.contains(item) then card.classList.add("dragging"), 0)
      })
    zones.addEventListener("dragover", (event: dom.Event) =>
      if zoneOf(event).nonEmpty then
        event.preventDefault()
        held.flatMap(landing(event, _)).foreach(arrange))
    // A `dragleave` fires for every child the pointer crosses, so the
    // pointer itself says whether it has left the zones.
    zones.addEventListener("dragleave", (event: dom.Event) =>
      val pointer = event.asInstanceOf[dom.MouseEvent]
      val area = zones.getBoundingClientRect()
      if !(pointer.clientX > area.left && pointer.clientX < area.right &&
          pointer.clientY > area.top && pointer.clientY < area.bottom) then
        arrange(draft))
    zones.addEventListener("drop", (event: dom.Event) =>
      if zoneOf(event).nonEmpty then
        event.preventDefault()
        val item = Option(event.asInstanceOf[dom.DragEvent].dataTransfer
          .getData("text/plain")).filter(_.nonEmpty).orElse(held)
        item.flatMap(landing(event, _)).filter(_ != draft)
          .foreach(moved => controls.stage(Draft.Partition(moved))))
    zones.addEventListener("dragend", (_: dom.Event) =>
      heldCard.foreach(_.classList.remove("dragging"))
      held = None
      arrange(draft))

  private def box(card: dom.Element): PartitionDropTarget.Box =
    val rect = card.getBoundingClientRect()
    PartitionDropTarget.Box(rect.left, rect.top, rect.right, rect.bottom)
