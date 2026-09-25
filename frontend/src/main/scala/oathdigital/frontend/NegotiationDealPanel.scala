package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, GameIntent => GameCommand}
import org.scalajs.dom

/** The deal panel for a parked negotiation. Every viewer sees who is in the
  * deal, who has accepted, and the terms as their redaction level allows; the
  * viewer the route gave an editor answers the deal decision through it
  * (`ResolveWalker`: propose terms, accept, decline).
  */
private[frontend] object NegotiationDealPanel:
  import ServerUiSupport.{button, protocolNegotiationTerms, text}

  private final case class DisclosureOffer(kind: String, card: CardDetails,
      siteId: Option[String])

  def render(surface: ParkedDecision.Surface.Negotiate, canControl: Boolean,
      panel: dom.Element, ui: ServerUiView): Unit =
    summary(surface.deal, panel)
    surface.editor.foreach { case (decisionId, editing) =>
      editor(decisionId, surface.deal, editing, canControl, panel, ui)
    }

  private def summary(deal: NegotiationDealState, panel: dom.Element): Unit =
    panel.appendChild(text("h2", "", "Negotiation"))
    panel.appendChild(text("p", "negotiation-status",
      deal.participantPlayerIds.map(id => s"$id: ${if deal.acceptedPlayerIds
        .contains(id) then "accepted" else "reviewing"}").mkString(" · ")))
    deal.transfers.foreach(t => panel.appendChild(text("p", "negotiation-transfer",
      s"${t.authorPlayerId} gives ${t.recipientPlayerId}: ${t.favor} favor, " +
        s"${t.relicCount} relic(s)")))
    deal.disclosures.foreach(d => panel.appendChild(text("p",
      "negotiation-disclosure",
      s"${d.authorPlayerId} promises ${d.recipientPlayerId} a ${d.kind} disclosure")))

  private def offers(editing: NegotiationEditingState): Vector[DisclosureOffer] =
    editing.editableAdvisers.map(DisclosureOffer("adviser", _, None)) ++
      editing.editableRelics.filter(_.orientation.contains("face-down"))
        .map(DisclosureOffer("held-relic", _, None)) ++
      editing.editableSiteRelics.map(entry =>
        DisclosureOffer("site-relic", entry.card, Some(entry.siteId)))

  private def input(kind: String): dom.html.Input =
    val node = dom.document.createElement("input").asInstanceOf[dom.html.Input]
    node.`type` = kind
    node

  /** One term per row, each with the control and the words that name it in
    * the same `label`: a run of bare checkboxes and their trailing text wraps
    * into a paragraph of offers no one can read.
    */
  private def item(control: dom.html.Input, caption: String,
      before: Boolean): dom.Element =
    val row = dom.document.createElement("label").asInstanceOf[dom.html.Label]
    row.className = "negotiation-item"
    if before then
      row.appendChild(text("span", "negotiation-item-label", caption))
      row.appendChild(control)
    else
      row.appendChild(control)
      row.appendChild(text("span", "negotiation-item-label", caption))
    row

  private def editor(decisionId: String, deal: NegotiationDealState,
      editing: NegotiationEditingState, canControl: Boolean,
      panel: dom.Element, ui: ServerUiView): Unit =
    val me = ui.currentPlayerId
    val favors = scala.collection.mutable.ArrayBuffer.empty[(String, dom.html.Input)]
    val relics = scala.collection.mutable.ArrayBuffer.empty[
      (String, String, dom.html.Input)]
    val disclosures = scala.collection.mutable.ArrayBuffer.empty[
      (String, DisclosureOffer, dom.html.Input)]
    deal.participantPlayerIds.filterNot(_ == me).foreach { recipient =>
      panel.appendChild(text("h3", "", s"Your terms for $recipient"))
      val favor = input("number")
      favor.min = "0"; favor.max = editing.editableFavor.toString
      favor.value = deal.transfers.find(t => t.authorPlayerId == me &&
        t.recipientPlayerId == recipient).map(_.favor).getOrElse(0).toString
      favor.setAttribute("aria-label", s"Favor offered to $recipient")
      panel.appendChild(item(favor, "Favor", before = true))
      favors += recipient -> favor
      editing.editableRelics.foreach { relic =>
        val check = input("checkbox")
        check.setAttribute("aria-label", s"Offer ${relic.name} to $recipient")
        check.checked = deal.transfers.exists(t => t.authorPlayerId == me &&
          t.recipientPlayerId == recipient && t.relics.exists(_.cardId == relic.cardId))
        // A relic goes to one recipient at most.
        check.onchange = _ => if check.checked then relics.foreach:
          case (other, otherRelic, otherCheck)
              if other != recipient && otherRelic == relic.cardId =>
            otherCheck.checked = false
          case _ => ()
        panel.appendChild(item(check, relic.name, before = false))
        relics += ((recipient, relic.cardId, check))
      }
      offers(editing).foreach { offer =>
        val check = input("checkbox")
        check.setAttribute("aria-label",
          s"Promise ${offer.kind} disclosure of ${offer.card.name} to $recipient")
        check.checked = deal.disclosures.exists(d => d.authorPlayerId == me &&
          d.recipientPlayerId == recipient && d.kind == offer.kind &&
          d.card.exists(_.cardId == offer.card.cardId))
        panel.appendChild(item(check, s"Show ${offer.card.name}",
          before = false))
        disclosures += ((recipient, offer, check))
      }
    }
    val save = button("Save Deal Changes", "negotiation-save")
    save.disabled = !canControl
    save.onclick = _ => {
      val terms = NegotiationTermsInput(favors.map { case (recipient, favor) =>
        NegotiationTransferInput(recipient, favor.value.toIntOption.getOrElse(0),
          relics.collect { case (`recipient`, relic, check) if check.checked =>
            relic }.toVector)
      }.toVector, disclosures.collect { case (recipient, offer, check)
          if check.checked => NegotiationDisclosureInput(recipient, offer.kind,
        Option.when(offer.kind != "site-relic")(me), offer.siteId,
        Option.when(offer.kind == "adviser")(offer.card.cardKind),
        offer.card.cardId) }.toVector)
      ui.submitCommand(GameCommand.ResolveWalker(decisionId,
        DecisionAnswerWire.ProposeTermsWire(protocolNegotiationTerms(terms))))
    }
    panel.appendChild(save)
    val accept = button("Accept Current Deal", "negotiation-accept")
    accept.disabled = !canControl || !editing.canAccept
    accept.onclick = _ => ui.submitCommand(GameCommand.ResolveWalker(
      decisionId, DecisionAnswerWire.AcceptDealWire))
    panel.appendChild(accept)
    val decline = button("End/Decline", "negotiation-decline")
    decline.disabled = !canControl
    decline.onclick = _ => ui.submitCommand(GameCommand.ResolveWalker(
      decisionId, DecisionAnswerWire.DeclineDealWire))
    panel.appendChild(decline)
