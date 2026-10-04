package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport.{button, element}

/** The parked board decision drawn onto the table (board decisions design,
  * "BoardOptions"). A renderer hands each element it draws the option keys
  * that element stands for; the first key the decision shows marks the
  * element with that option's state, and an available or selected option
  * answers a click, Enter or Space with `pick`.
  *
  * A click belongs to the element only when no control inside it took the
  * click first: a card inside a site box, a pawn inside a site, the inspect
  * button beside a card. Each of those answers for itself. `none` marks
  * nothing.
  */
private[frontend] final class BoardOptions(views: Map[String, OptionView],
    pick: DecisionOptionState => Unit):

  def view(keys: Vector[String]): Option[OptionView] =
    keys.iterator.flatMap(views.get).nextOption()

  /** Marks `target` as the option the first of `keys` names, if any. */
  def decorate(target: dom.Element, keys: Vector[String]): Unit =
    view(keys).foreach(mark(target, _))

  /** A card face, marked when it is an option. An option card's body
    * selects it, so reading it moves to an inspect button beside it; the
    * two share one box, since a button may not hold a button. A card the
    * viewer may not identify has nothing to read and gets no button.
    * Returns what to append.
    */
  def card(face: dom.html.Button, details: CardDetails,
      keys: Vector[String]): dom.Element = view(keys) match
    case None => face
    case Some(found) =>
      face.onclick = null
      mark(face, found)
      if details.hidden then face
      else
        val box = element("span", "board-option-card")
        box.appendChild(face)
        val inspect = button("i", "board-option-inspect")
        inspect.setAttribute("aria-label", s"Inspect ${details.name}")
        inspect.addEventListener("click", (event: dom.Event) => {
          event.stopPropagation()
          CardInspection.open(details, inspect)
        })
        box.appendChild(inspect)
        box

  private def mark(target: dom.Element, found: OptionView): Unit =
    val key = BoardOptionViews.key(found.option)
    found.state.classes.foreach(target.classList.add)
    target.setAttribute("data-option-ref", key)
    found.reason.orElse(Option.when(found.option.details.nonEmpty)(
      found.option.details.mkString(" · ")))
      .foreach(target.setAttribute("title", _))
    BoardOptions.hoverSync(target, key)
    val native = target.tagName == "BUTTON"
    if !native then
      target.setAttribute("role", "button")
      target.setAttribute("tabindex", "0")
    found.state match
      case OptionState.Available | OptionState.Selected =>
        target.setAttribute("aria-pressed",
          (found.state == OptionState.Selected).toString)
        target.addEventListener("click", (event: dom.Event) =>
          if own(event, target) then
            event.stopPropagation()
            pick(found.option))
        // A button turns Enter and Space into a click by itself.
        if !native then
          target.addEventListener("keydown", (event: dom.Event) => {
            val pressed = event.asInstanceOf[dom.KeyboardEvent].key
            if (pressed == "Enter" || pressed == " ") && own(event, target)
            then
              event.preventDefault()
              event.stopPropagation()
              pick(found.option)
          })
      case OptionState.Locked =>
        target.setAttribute("aria-pressed", "true")
        target.setAttribute("aria-disabled", "true")
      case OptionState.Unavailable =>
        target.setAttribute("aria-disabled", "true")

  /** Whether `event` landed on `target` itself rather than on a control
    * inside it.
    */
  private def own(event: dom.Event, target: dom.Element): Boolean =
    event.target match
      case from: dom.Element =>
        val nearest = from.closest("button, [role=button]")
        nearest == null || nearest == target
      case _ => true

private[frontend] object BoardOptions:
  val none: BoardOptions = new BoardOptions(Map.empty, _ => ())

  /** A card's own option key: its kind as an option names it, and its id. */
  def cardKey(card: CardDetails): String =
    BoardOptionViews.key(card.cardKind match
      case "relic" => "relic"
      case "vision" => "vision"
      case "edifice" => "edifice"
      case _ => "denizen", card.cardId)

  /** Hovering `target` highlights every element of the same option, on the
    * table and in the panel, and leaving it clears them.
    */
  def hoverSync(target: dom.Element, key: String): Unit =
    target.addEventListener("mouseenter", (_: dom.Event) =>
      highlight(key, on = true))
    target.addEventListener("mouseleave", (_: dom.Event) =>
      highlight(key, on = false))

  def highlight(key: String, on: Boolean): Unit =
    dom.document.querySelectorAll(s"""[data-option-ref="$key"]""").toVector
      .map(_.asInstanceOf[dom.Element]).foreach(node =>
        if on then node.classList.add("board-option-hover")
        else node.classList.remove("board-option-hover"))
