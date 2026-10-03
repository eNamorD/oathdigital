package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, GameIntent => GameCommand}
import org.scalajs.dom

/** The deal panel for a parked negotiation. Every viewer sees who is in the
  * deal, who has accepted, and the terms as their redaction level allows; the
  * viewer the route gave an editor answers the deal decision through it
  * (`ResolveWalker`: propose terms, accept, decline).
  */
private[frontend] object NegotiationDealPanel:
  import ServerUiSupport.{button, cardLink, protocolNegotiationTerms, text}

  private final case class DisclosureOffer(kind: String, card: CardDetails,
      siteId: Option[String])

  def render(surface: ParkedDecision.Surface.Negotiate, playerId: String,
      canControl: Boolean, panel: dom.Element, controls: TableControls): Unit =
    summary(surface.deal, panel)
    surface.editor.foreach { case (decisionId, editing) =>
      editor(decisionId, surface.deal, editing, playerId, canControl, panel,
        controls)
    }

  private def summary(deal: NegotiationDealState, panel: dom.Element): Unit =
    panel.appendChild(text("h2", "", "Negotiation"))
    panel.appendChild(text("p", "negotiation-status",
      deal.participantPlayerIds.map(id => s"$id: ${if deal.acceptedPlayerIds
        .contains(id) then "accepted" else "reviewing"}").mkString(" · ")))
    deal.transfers.foreach(t => panel.appendChild(transferLine(t)))
    deal.disclosures.foreach(d => panel.appendChild(disclosureLine(d)))

  /** What the author gives, named where the viewer can see it: favor, each
    * relic the projection carries, and a count of the facedown rest.
    */
  private def transferLine(t: NegotiationTransferState): dom.Element =
    val hidden = t.relicCount - t.relics.size
    val parts: Vector[dom.Node] =
      Option.when(t.favor > 0)(textNode(s"${t.favor} favor")).toVector ++
        t.relics.map(cardLink) ++
        Option.when(hidden > 0)(textNode(
          s"$hidden facedown ${if hidden == 1 then "relic" else "relics"}"))
    val line = text("p", "negotiation-transfer",
      s"${t.authorPlayerId} gives ${t.recipientPlayerId}: ")
    if parts.isEmpty then line.appendChild(textNode("nothing"))
    parts.zipWithIndex.foreach { (part, index) =>
      if index > 0 then line.appendChild(textNode(", "))
      line.appendChild(part)
    }
    line

  /** What the author promises to show: the card when the viewer may see it,
    * otherwise only what kind of card it is.
    */
  private def disclosureLine(d: NegotiationDisclosureState): dom.Element =
    val line = text("p", "negotiation-disclosure",
      s"${d.authorPlayerId} promises to show ${d.recipientPlayerId} ")
    d.card.fold(line.appendChild(textNode(d.kind match
      case "adviser" => "an adviser"
      case "held-relic" => "a held relic"
      case "site-relic" => "a site relic"
      case other => other)))(card => line.appendChild(cardLink(card)))
    line

  private def textNode(value: String): dom.Node =
    dom.document.createTextNode(value)

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

  /** The favor row: the words that name the number box and the box, in one
    * `label`. One term per row: a run of bare controls and their trailing
    * text wraps into a paragraph of offers no one can read.
    */
  private def favorItem(control: dom.html.Input): dom.Element =
    val row = dom.document.createElement("label").asInstanceOf[dom.html.Label]
    row.className = "negotiation-item"
    row.appendChild(text("span", "negotiation-item-label", "Favor"))
    row.appendChild(control)
    row

  /** A one-term row, like the favor row, whose caption names a card that
    * opens in the overlay. Not a `label`: a button inside one would be a
    * second labelable control, and the checkbox is named by its `aria-label`
    * already. Clicking the name inspects the card; the offer toggles from
    * the box alone.
    */
  private def cardItem(control: dom.html.Input, prefix: String,
      card: CardDetails): dom.Element =
    val row = text("div", "negotiation-item", "")
    row.appendChild(control)
    row.appendChild(textNode(prefix))
    row.appendChild(cardLink(card))
    row

  private def editor(decisionId: String, deal: NegotiationDealState,
      editing: NegotiationEditingState, me: String, canControl: Boolean,
      panel: dom.Element, controls: TableControls): Unit =
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
      panel.appendChild(favorItem(favor))
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
        panel.appendChild(cardItem(check, "", relic))
        relics += ((recipient, relic.cardId, check))
      }
      offers(editing).foreach { offer =>
        val check = input("checkbox")
        check.setAttribute("aria-label",
          s"Promise ${offer.kind} disclosure of ${offer.card.name} to $recipient")
        check.checked = deal.disclosures.exists(d => d.authorPlayerId == me &&
          d.recipientPlayerId == recipient && d.kind == offer.kind &&
          d.card.exists(_.cardId == offer.card.cardId))
        panel.appendChild(cardItem(check, "Show ", offer.card))
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
      controls.submitCommand(GameCommand.ResolveWalker(decisionId,
        DecisionAnswerWire.ProposeTermsWire(protocolNegotiationTerms(terms))))
    }
    panel.appendChild(save)
    val accept = button("Accept Current Deal", "negotiation-accept")
    accept.disabled = !canControl || !editing.canAccept
    accept.onclick = _ => controls.submitCommand(GameCommand.ResolveWalker(
      decisionId, DecisionAnswerWire.AcceptDealWire))
    panel.appendChild(accept)
    val decline = button("End/Decline", "negotiation-decline")
    decline.disabled = !canControl
    decline.onclick = _ => controls.submitCommand(GameCommand.ResolveWalker(
      decisionId, DecisionAnswerWire.DeclineDealWire))
    panel.appendChild(decline)
