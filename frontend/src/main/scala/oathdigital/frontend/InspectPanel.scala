package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport.{button, text}

/** The Inspect decision's panel (catalog batch 2, N5): its heading, the
  * cards to look at as a card list, and the one Done button that answers
  * it. The list is drawn inline, so the decision shows its cards without
  * opening an overlay by itself.
  */
private[frontend] object InspectPanel:
  def render(surface: ParkedDecision.Surface.Inspect, canControl: Boolean,
      panel: dom.Element, controls: TableControls): Unit =
    val query = surface.query
    panel.appendChild(text("h2", "",
      WalkerPanelSupport.decisionHeading(query)))
    panel.appendChild(CardList.render(cardCount(query.cards.size),
      query.cards))
    val done = button(query.done.label, "walker-choice")
    done.disabled = !canControl
    done.onclick = _ => controls.submitCommand(
      WalkerPanelSupport.resolveChooseOneCommand(surface.decision, query.done))
    panel.appendChild(done)

  def cardCount(count: Int): String =
    if count == 1 then "1 card" else s"$count cards"
