package oathdigital.frontend

import org.scalajs.dom

/** A number field with a list of the amounts it allows: the player types a
  * number, or opens the list and picks one. The field holds the value and
  * the list only fills it in. Nothing is staged as the value changes, so
  * typing never redraws the panel and the field keeps its focus.
  *
  * `changed` hears the parsed value after every edit or pick, and `submit`
  * hears Enter while the list is closed.
  */
private[frontend] final class AmountCombobox(id: String, label: String,
    minimum: Int, maximum: Int, initial: Int, enabled: Boolean,
    changed: Option[Int] => Unit, submit: () => Unit):
  import ServerUiSupport.{button, element, text}

  private val listId = s"$id-options"
  private var active = initial

  val input: dom.html.Input =
    dom.document.createElement("input").asInstanceOf[dom.html.Input]
  input.className = "walker-amount"
  input.`type` = "text"
  input.value = initial.toString
  input.disabled = !enabled
  input.setAttribute("inputmode", "numeric")
  input.setAttribute("autocomplete", "off")
  input.setAttribute("role", "combobox")
  input.setAttribute("aria-label", label)
  input.setAttribute("aria-autocomplete", "none")
  input.setAttribute("aria-controls", listId)

  // Out of the tab order: the field already opens the list from the keyboard.
  private val toggle = button("▾", "walker-amount-toggle")
  toggle.`type` = "button"
  toggle.tabIndex = -1
  toggle.disabled = !enabled
  toggle.setAttribute("aria-label", "Show amounts")

  private val list = element("ul", "walker-amount-options")
  list.id = listId
  list.setAttribute("role", "listbox")
  list.setAttribute("aria-label", label)

  private val options = (minimum to maximum).toVector.map { amount =>
    val option = text("li", "walker-amount-option", amount.toString)
    option.id = s"$listId-$amount"
    option.setAttribute("role", "option")
    option.setAttribute("data-amount", amount.toString)
    // On mousedown, not click: a click would blur the field first, and the
    // blur closes the list before the click lands.
    option.addEventListener("mousedown", (event: dom.MouseEvent) =>
      event.preventDefault()
      pick(amount))
    list.appendChild(option)
    amount -> option
  }

  val root: dom.Element = element("div", "walker-amount-combobox")
  private val field = element("div", "walker-amount-field")
  field.appendChild(input)
  field.appendChild(toggle)
  root.appendChild(field)
  root.appendChild(list)

  /** The typed amount, when it is a whole number in range. */
  def value: Option[Int] = AmountCombobox.parse(input.value, minimum, maximum)

  private def open: Boolean = !list.hasAttribute("hidden")

  private def paint(): Unit =
    val chosen = value
    options.foreach { (amount, option) =>
      option.setAttribute("class",
        if amount == active then "walker-amount-option active"
        else "walker-amount-option")
      option.setAttribute("aria-selected", chosen.contains(amount).toString)
    }
    input.setAttribute("aria-invalid", chosen.isEmpty.toString)
    if open then input.setAttribute("aria-activedescendant", s"$listId-$active")
    else input.removeAttribute("aria-activedescendant")

  private def show(visible: Boolean): Unit =
    if visible then list.removeAttribute("hidden")
    else list.setAttribute("hidden", "")
    input.setAttribute("aria-expanded", visible.toString)
    paint()

  private def edited(): Unit =
    value.foreach(active = _)
    paint()
    changed(value)

  private def pick(amount: Int): Unit =
    input.value = amount.toString
    show(false)
    edited()
    input.focus()

  input.oninput = _ => edited()
  input.onblur = _ => show(false)
  input.onkeydown = event => event.key match
    case "ArrowDown" | "ArrowUp" =>
      event.preventDefault()
      if !open then show(true)
      else
        val step = if event.key == "ArrowDown" then 1 else -1
        active = math.max(minimum, math.min(maximum, active + step))
        paint()
    case "Enter" =>
      event.preventDefault()
      if open then pick(active) else submit()
    // Only an open list claims Escape; a closed one leaves it to the page.
    case "Escape" if open =>
      event.preventDefault()
      event.stopPropagation()
      show(false)
    case _ => ()
  toggle.addEventListener("mousedown", (event: dom.MouseEvent) =>
    event.preventDefault()
    if enabled then
      show(!open)
      input.focus())

  show(false)

private[frontend] object AmountCombobox:
  def parse(value: String, minimum: Int, maximum: Int): Option[Int] =
    value.trim.toIntOption.filter(amount => amount >= minimum && amount <= maximum)
