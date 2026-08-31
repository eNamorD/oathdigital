package oathdigital.frontend

import oathdigital.protocol.{GameIntent, RestFavorAllocation, RestFavorSource}
import org.scalajs.dom

private[frontend] object RestPowerDecisionRenderer {
  def render(value: GameProjection, decision: RestPowerState,
      ui: ServerUiView): dom.Element = {
    import ServerUiSupport._
    val section = element("section", "rest-power-decision")
    section.setAttribute("aria-labelledby", "rest-power-title")
    val title = text("h2", "", "League Treaty")
    title.id = "rest-power-title"
    section.appendChild(title)
    section.appendChild(text("p", "decision-instruction",
      "Move any amount of favor from eligible regional cards to one favor bank, or decline."))
    val inputs = decision.sources.map { source =>
      val row = element("div", "rest-power-source")
      val inputId = s"rest-favor-${source.kind}-${source.siteId}-${source.sourceId}"
      val label = dom.document.createElement("label").asInstanceOf[dom.html.Label]
      label.htmlFor = inputId
      label.textContent = s"${source.label} at ${siteLabel(value, source.siteId)} " +
        s"(0–${source.availableFavor})"
      val input = dom.document.createElement("input").asInstanceOf[dom.html.Input]
      input.id = inputId
      input.`type` = "number"
      input.min = "0"
      input.max = source.availableFavor.toString
      input.value = "0"
      input.setAttribute("inputmode", "numeric")
      row.appendChild(label)
      row.appendChild(input)
      section.appendChild(row)
      source -> input
    }
    val bankLabel = dom.document.createElement("label").asInstanceOf[dom.html.Label]
    bankLabel.htmlFor = "rest-power-bank"
    bankLabel.textContent = "Destination favor bank"
    val bank = dom.document.createElement("select").asInstanceOf[dom.html.Select]
    bank.id = "rest-power-bank"
    decision.legalBanks.foreach { suit =>
      val option = dom.document.createElement("option").asInstanceOf[dom.html.Option]
      option.value = suit
      option.textContent = suit.capitalize
      bank.appendChild(option)
    }
    section.appendChild(bankLabel)
    section.appendChild(bank)
    val resolve = button("Move Favor", "rest-power-resolve")
    resolve.disabled = !ui.canControl
    resolve.onclick = _ => {
      val allocations = inputs.flatMap { case (source, input) =>
        scala.util.Try(input.value.toInt).toOption.filter(_ > 0).map(amount =>
          RestFavorAllocation(RestFavorSource(source.kind, source.siteId,
            source.sourceId), amount))
      }
      ui.submitCommand(GameIntent.ResolveRestPower(decision.decisionId,
        allocations, bank.value))
    }
    val decline = button("Decline", "rest-power-decline")
    decline.disabled = !ui.canControl
    decline.onclick = _ => ui.submitCommand(
      GameIntent.DeclineRestPower(decision.decisionId))
    section.appendChild(resolve)
    section.appendChild(decline)
    section
  }
}
