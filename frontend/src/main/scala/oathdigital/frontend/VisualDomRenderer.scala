package oathdigital.frontend

import org.scalajs.dom
import oathdigital.presentation.VisualInstruction

private[frontend] object VisualDomRenderer {
  def render(plan: VisualRenderPlan, className: String): dom.Element =
    plan.instruction match {
      case placeholder: VisualInstruction.Placeholder =>
        renderPlaceholder(placeholder, className)
      case VisualInstruction.Image(reference, accessibleLabel) =>
        val image = dom.document.createElement("img")
          .asInstanceOf[dom.html.Image]
        var failed = false
        image.className = className
        image.alt = accessibleLabel.value
        image.addEventListener("error", (_: dom.Event) =>
          if (!failed) {
            failed = true
            Option(image.parentNode).foreach(_.replaceChild(
              renderPlaceholder(plan.afterFailure, className),
              image
            ))
          }
        )
        image.src = reference.value
        image
    }

  private def renderPlaceholder(
      placeholder: VisualInstruction.Placeholder,
      className: String
  ): dom.Element = {
    val node = dom.document.createElement("span")
    node.setAttribute("class", s"$className visual-fallback")
    node.setAttribute("role", "img")
    node.setAttribute("aria-label", placeholder.accessibleLabel.value)
    node.setAttribute("data-fallback-text", placeholder.text)
    node.textContent = placeholder.symbol
    node
  }
}
