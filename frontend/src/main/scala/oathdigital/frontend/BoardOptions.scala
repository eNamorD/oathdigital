package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport.{button, element, text}

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
    pick: DecisionOptionState => Unit,
    ask: (DecisionOptionState, dom.html.Element) => Unit = (_, _) => ()):

  def view(keys: Vector[String]): Option[OptionView] =
    keys.iterator.flatMap(views.get).nextOption()

  /** Marks `target` as the option the first of `keys` names, if any. */
  def decorate(target: dom.Element, keys: Vector[String]): Unit =
    view(keys).foreach(mark(target, _))

  /** A control that does something of its own when clicked (a card face
    * opens the inspector, a pile lists its cards), marked when it is an
    * option. An option's body picks it, so the control's own action moves
    * to an inspect button beside it, named "Inspect {name}"; the two share
    * one box, since a button may not hold a button. `inspect` is `None`
    * when there is nothing to read. A marked control is enabled, since an
    * empty pile is still a place to play to. Returns what to append.
    */
  def control(face: dom.html.Button, keys: Vector[String],
      inspect: Option[(String, dom.html.Element => Unit)]): dom.Element =
    view(keys) match
      case None => face
      case Some(found) =>
        face.onclick = null
        face.disabled = false
        mark(face, found)
        inspect.fold[dom.Element](face) { (name, open) =>
          val box = element("span", "board-option-card")
          box.appendChild(face)
          val reader = button("i", "board-option-inspect")
          reader.setAttribute("aria-label", s"Inspect $name")
          reader.addEventListener("click", (event: dom.Event) => {
            event.stopPropagation()
            open(reader)
          })
          box.appendChild(reader)
          box
        }

  /** A card face, marked when it is an option. A card the viewer may not
    * identify has nothing to read and gets no inspect button. `open`
    * replaces the plain inspector, for a card that opens with a grid. */
  def card(face: dom.html.Button, details: CardDetails,
      keys: Vector[String],
      open: Option[dom.html.Element => Unit] = None): dom.Element =
    control(face, keys, Option.unless(details.hidden)(
      details.name -> open.getOrElse(CardInspection.open(details, _))))

  private def mark(target: dom.Element, found: OptionView): Unit =
    val key = BoardOptionViews.home(found.option)
    found.state.classes.foreach(target.classList.add)
    if found.option.replaces.nonEmpty then
      val word = if found.option.buries then "Bury" else "Replace"
      target.classList.add("board-option-replace")
      target.appendChild(text("span", "board-option-badge", word))
      // A card face names itself with an aria-label, which hides its
      // content from a screen reader, so the word joins the label.
      Option(target.getAttribute("aria-label")).foreach(name =>
        target.setAttribute("aria-label", s"$name, $word"))
    target.setAttribute("data-option-ref", key)
    found.reason.orElse(Option.when(found.option.details.nonEmpty)(
      found.option.details.mkString(" · ")))
      .foreach(HoverTip.set(target, _))
    BoardOptions.hoverSync(target, key)
    val native = target.tagName == "BUTTON"
    if !native then
      target.setAttribute("role", "button")
      target.setAttribute("tabindex", "0")
    found.state match
      case OptionState.Available | OptionState.Selected |
          OptionState.Askable =>
        // An askable option asks its granters first (consent design,
        // "Frontend"); any other is picked.
        val act: DecisionOptionState => Unit =
          if found.state == OptionState.Askable then
            option => ask(option, target.asInstanceOf[dom.html.Element])
          else pick
        target.setAttribute("aria-pressed",
          (found.state == OptionState.Selected).toString)
        target.addEventListener("click", (event: dom.Event) =>
          if own(event, target) then
            event.stopPropagation()
            act(found.option))
        // A button turns Enter and Space into a click by itself.
        if !native then
          target.addEventListener("keydown", (event: dom.Event) => {
            val pressed = event.asInstanceOf[dom.KeyboardEvent].key
            if (pressed == "Enter" || pressed == " ") && own(event, target)
            then
              event.preventDefault()
              event.stopPropagation()
              act(found.option)
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

  /** What a pick on the table or in the panel does: stage the board draft
    * after the click, in the session's current context. Without a context
    * (a seat change's interim frame) or without control it does nothing.
    */
  def stager(surface: ParkedDecision.Surface.BoardDecision,
      drafts: SessionDrafts, canControl: Boolean,
      controls: TableControls): DecisionOptionState => Unit =
    option => if canControl then drafts.context.foreach(context =>
      controls.stage(Draft.Board(Some(BoardSelectionDraft.click(drafts.board,
        context, surface.decision.decisionId, surface.query, option)))))

  def of(surface: ParkedDecision.Surface.BoardDecision, drafts: SessionDrafts,
      canControl: Boolean, controls: TableControls,
      value: GameProjection): BoardOptions =
    new BoardOptions(BoardOptionViews.of(surface.decision, surface.query,
      drafts.board), stager(surface, drafts, canControl, controls),
      ConsentAsking.asker(surface.decision.decisionId, value, canControl,
        controls))

  /** A card's kind as an option names it. */
  def cardKind(card: CardDetails): String = card.cardKind match
    case "relic" | "vision" | "edifice" => card.cardKind
    case _ => "denizen"

  /** A card's own option key: its kind as an option names it, and its id. */
  def cardKey(card: CardDetails): String =
    BoardOptionViews.key(cardKind(card), card.cardId)

  /** Hovering `target` highlights every element of the same option, on the
    * table and in the panel, and leaving it clears them.
    */
  def hoverSync(target: dom.Element, key: String): Unit =
    target.addEventListener("mouseenter", (_: dom.Event) =>
      highlight(key, on = true))
    target.addEventListener("mouseleave", (_: dom.Event) =>
      highlight(key, on = false))

  def highlight(key: String, on: Boolean): Unit =
    dom.document.querySelectorAll("[data-option-ref]").toVector
      .map(_.asInstanceOf[dom.Element])
      .filter(_.getAttribute("data-option-ref") == key).foreach(node =>
        if on then node.classList.add("board-option-hover")
        else node.classList.remove("board-option-hover"))
