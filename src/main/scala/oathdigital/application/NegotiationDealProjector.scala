package oathdigital.application

import oathdigital.model._
import oathdigital.protocol.projection._

/** Projects a [[DecisionQuery.Negotiate]] for one viewer.
  *
  * Everyone sees what a non-author participant sees: who is in the deal, who
  * has accepted, favor amounts, relic counts and the details of faceup relics,
  * and the kind and recipient of each disclosure. Only an author sees the
  * identity of a facedown relic they offer or of information they promise.
  * Only a participant gets the editing inputs, and never another player's.
  */
private[application] final class NegotiationDealProjector(
    presentation: GamePresentationProjector) {

  def project(ready: ReadyGame, viewer: Option[PlayerId],
      query: DecisionQuery.Negotiate): NegotiationDealProjection = {
    val current = ready.game.current
    val transfers = query.participants.flatMap { author =>
      val owner = current.players.find(_.player == author).get
      query.terms(author).transfers.map { transfer =>
        NegotiationTransferProjection(author.value, transfer.recipient.value,
          transfer.favor, transfer.relics.size,
          transfer.relics.flatMap(id => owner.relics.find(_.id == id)).collect {
            case relic if viewer.contains(author) ||
                relic.orientation == Orientation.FaceUp =>
              presentation.cardDetails(relic.id, Some(relic.orientation),
                hidden = false)
          })
      }
    }
    val disclosures = query.participants.flatMap { author =>
      query.terms(author).disclosures.map { disclosure =>
        val visible = viewer.contains(author)
        def detail(id: CardId) = Option.when(visible)(presentation.cardDetails(
          id, Some(Orientation.FaceDown), hidden = false))
        val (kind, card) = disclosure.information match {
          case NegotiationDisclosureRef.Adviser(_, id) => "adviser" -> detail(id)
          case NegotiationDisclosureRef.HeldRelic(_, id) => "held-relic" -> detail(id)
          case NegotiationDisclosureRef.SiteRelic(_, id) => "site-relic" -> detail(id)
        }
        NegotiationDisclosureProjection(author.value,
          disclosure.recipient.value, kind, card)
      }
    }
    NegotiationDealProjection(query.participants.map(_.value),
      query.participants.filter(query.accepted).map(_.value), transfers,
      disclosures, viewer.filter(query.participants.contains)
        .map(editing(ready, query, _)))
  }

  private def editing(ready: ReadyGame, query: DecisionQuery.Negotiate,
      viewer: PlayerId): NegotiationEditingProjection = {
    val current = ready.game.current
    val player = current.players.find(_.player == viewer).get
    val own = query.bounds(viewer)
    NegotiationEditingProjection(own.maxFavor,
      own.relics.flatMap(id => player.relics.find(_.id == id)).map(relic =>
        presentation.cardDetails(relic.id, Some(relic.orientation),
          hidden = false)),
      own.disclosures.collect {
        case NegotiationDisclosureRef.Adviser(_, card) =>
          presentation.cardDetails(card, Some(Orientation.FaceDown),
            hidden = false)
      },
      own.disclosures.collect {
        case NegotiationDisclosureRef.SiteRelic(site, relic) => site -> relic
      }.flatMap { case (site, relic) =>
        current.map.sites.get(site).flatMap(_.relics.find(_.id == relic)).map(
          state => NegotiationSiteRelicProjection(site.value,
            presentation.cardDetails(relic, Some(state.orientation),
              hidden = false)))
      },
      query.acceptors.contains(viewer))
  }
}
