package oathdigital.frontend

import org.scalajs.dom

/** Preserves local reading position and semantic keyboard focus during rebuilds. */
private[frontend] object PanelContent {
  private val controls = "button,input,select,textarea,a,[tabindex]"
  private def candidates(root: dom.Element): Vector[dom.html.Element] = {
    val nodes = root.querySelectorAll(controls)
    (0 until nodes.length).map(nodes(_).asInstanceOf[dom.html.Element]).toVector
  }
  private def identity(node: dom.Element): String = {
    val target = Option(node.closest("[data-target-ref],[data-player-id],[data-denizen-id]"))
      .map(n => Vector("data-target-ref", "data-player-id", "data-denizen-id")
        .map(n.getAttribute).mkString("|")).getOrElse("")
    Vector(node.tagName, node.getAttribute("type"), node.getAttribute("aria-label"),
      node.getAttribute("name"), target,
      if (node.tagName == "INPUT" || node.tagName == "TEXTAREA") "" else node.textContent)
      .mkString("|")
  }

  def replace(root: dom.Element, next: dom.Element, heading: dom.html.Element,
      resetScroll: Boolean = false): Unit = {
    val x = root.scrollLeft
    val y = root.scrollTop
    val active = Option(dom.document.activeElement).filter(n => n != root && root.contains(n))
    val focused = active.map { node =>
      val id = identity(node)
      val index = candidates(root).filter(n => identity(n) == id).indexOf(node)
      val value = node match {
        case input: dom.html.Input => Some(input.value)
        case area: dom.html.TextArea => Some(area.value)
        case _ => None
      }
      (id, index, value)
    }
    while (root.firstChild != null) root.removeChild(root.firstChild)
    root.appendChild(next)
    focused.foreach { case (id, index, value) =>
      candidates(root).filter(n => identity(n) == id).lift(index) match {
        case Some(node) =>
          value.foreach { v => node match {
            case input: dom.html.Input if input.`type` != "checkbox" && input.`type` != "radio" => input.value = v
            case area: dom.html.TextArea => area.value = v
            case _ => ()
          }}
          node.focus()
        case None => heading.focus()
      }
    }
    root.scrollLeft = if (resetScroll) 0 else x
    root.scrollTop = if (resetScroll) 0 else y
  }
}
