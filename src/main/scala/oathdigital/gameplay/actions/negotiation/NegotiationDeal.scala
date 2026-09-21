package oathdigital.gameplay.actions.negotiation

import oathdigital.model._
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseManyAnswer, DeclineDeal, ProposeTerms}

/** One deal, derived from the answers recorded so far. */
final case class DealState(participants: Vector[PlayerId],
    terms: Map[PlayerId, NegotiationTerms], accepted: Set[PlayerId],
    declined: Boolean) {
  def hasSubstance: Boolean = terms.values.exists(t =>
    t.transfers.exists(x => x.favor > 0 || x.relics.nonEmpty) ||
      t.disclosures.nonEmpty)
  def unanimous: Boolean =
    participants.nonEmpty && accepted == participants.toSet
  def closed: Boolean = declined || unanimous
  def agreed: Boolean = !declined && unanimous && hasSubstance
}

/** The deal rules, pure: who may negotiate, what the answers so far amount
  * to, what each author may still offer, and what settling does. Nothing here
  * is stored: the walker's `PendingTree.answered` is the only record, and
  * everything else is recomputed from it and from live state on every
  * command, which is safe because nothing that changes a bound can run while
  * a deal is open.
  */
object NegotiationDeal {
  val negotiatorsDecisionId: String = "negotiation.negotiators"
  val dealDecisionId: String = "negotiation.deal"
  val decisionIds: Set[String] = Set(negotiatorsDecisionId, dealDecisionId)

  /** The players the actor may deal with: the others whose pawn is at the
    * actor's pawn site. The one place that rule lives, asked when the tree is
    * built and (through the tree) when a later power widens it.
    */
  def eligible(state: ReadyGame, actor: PlayerId): Vector[PlayerId] = {
    val players = state.game.current.players
    players.find(_.player == actor).flatMap(_.pawnSite).toVector.flatMap(site =>
      players.collect {
        case other if other.player != actor && other.pawnSite.contains(site) =>
          other.player
      })
  }

  /** Actor first, then the chosen players in table order. With no recorded
    * negotiator answer the one eligible candidate is the negotiator: the
    * tree omits the choice when there is only one.
    */
  def participants(state: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[PlayerId] = {
    val picked = pending.answered.reverse.collectFirst {
      case Answered(`negotiatorsDecisionId`, ChooseManyAnswer(selected), _) =>
        selected.collect { case DecisionOptionRef.Player(id) => id }
    }.getOrElse(eligible(state, actor)).toSet
    actor +: state.game.current.players.map(_.player).filter(picked)
  }

  def fold(participants: Vector[PlayerId],
      answered: Vector[Answered]): DealState =
    answered.filter(_.decisionId == dealDecisionId).foldLeft(DealState(
      participants, participants.map(_ -> NegotiationTerms()).toMap,
      Set.empty, declined = false)) {
      case (deal, Answered(_, ProposeTerms(terms), by)) =>
        deal.copy(terms = deal.terms.updated(by, terms), accepted = Set.empty)
      case (deal, Answered(_, AcceptDeal, by)) =>
        deal.copy(accepted = deal.accepted + by)
      case (deal, Answered(_, DeclineDeal, _)) => deal.copy(declined = true)
      case (deal, _) => deal
    }

  def deal(state: ReadyGame, actor: PlayerId, pending: PendingTree): DealState =
    fold(participants(state, actor, pending), pending.answered)

  /** The question the deal asks now: everyone's terms, who accepted, what each
    * author may still offer, and who may accept (anyone who has not, once the
    * deal has substance).
    */
  def snapshot(state: ReadyGame, deal: DealState): DecisionQuery.Negotiate =
    DecisionQuery.Negotiate(deal.participants, deal.terms, deal.accepted,
      deal.participants.map(author => author ->
        bounds(state, author, deal.participants)).toMap,
      if (deal.hasSubstance && !deal.declined)
        deal.participants.filterNot(deal.accepted).toSet
      else Set.empty[PlayerId],
      heading = Some("Negotiation"))

  private def bounds(state: ReadyGame, author: PlayerId,
      participants: Vector[PlayerId]): NegotiationBounds = {
    val player = state.game.current.players.find(_.player == author).get
    NegotiationBounds(participants.filter(_ != author), player.board.favor,
      player.relics.map(_.id), disclosable(state, player))
  }

  /** What `author` may promise to reveal: their facedown advisers and held
    * relics, and site relics they know that are still at that site.
    */
  private def disclosable(state: ReadyGame,
      player: PlayerState): Vector[NegotiationDisclosureRef] = {
    val advisers = player.advisers.collect {
      case DenizenState(id, Orientation.FaceDown, _) =>
        NegotiationDisclosureRef.Adviser(player.player, id): NegotiationDisclosureRef
      case VisionState(id, Orientation.FaceDown) =>
        NegotiationDisclosureRef.Adviser(player.player, id): NegotiationDisclosureRef
    }
    val held = player.relics.collect {
      case relic if relic.orientation == Orientation.FaceDown =>
        NegotiationDisclosureRef.HeldRelic(player.player, relic.id): NegotiationDisclosureRef
    }
    val known = state.knowledge.siteRelics.getOrElse(player.player, Map.empty)
      .toVector.sortBy(_._1.value).flatMap { case (site, ids) =>
        state.game.current.map.sites.get(site).toVector.flatMap(
          _.relics.filter(relic => ids.contains(relic.id)).map(relic =>
            NegotiationDisclosureRef.SiteRelic(site, relic.id): NegotiationDisclosureRef))
      }
    advisers ++ held ++ known
  }

  /** What an agreed deal does: every disclosure records knowledge first, while
    * the cards are still where they were disclosed, then every transfer moves.
    * Refused (nothing recorded, the deal stays open) when an author can no
    * longer afford their terms.
    */
  def settle(state: ReadyGame,
      deal: DealState): Either[OathViolation, Vector[CoreOperation]] =
    deal.participants.foldLeft[Either[OathViolation, Unit]](Right(())) {
      (result, author) => result.flatMap(_ => affordable(state, author,
        deal.terms(author)))
    }.map(_ => knowledge(deal) ++ transfers(deal))

  private def affordable(state: ReadyGame, author: PlayerId,
      terms: NegotiationTerms): Either[OathViolation, Unit] = {
    val player = state.game.current.players.find(_.player == author).get
    val favor = terms.transfers.map(_.favor).sum
    for {
      _ <- Either.cond(favor <= player.board.favor, (),
        OathViolation.InsufficientFavor(favor, player.board.favor))
      _ <- Either.cond(terms.transfers.flatMap(_.relics).forall(id =>
        player.relics.exists(_.id == id)), (), OathViolation.NegotiationUnavailable(
        "an offered relic is no longer held by its author"))
    } yield ()
  }

  private def knowledge(deal: DealState): Vector[CoreOperation] =
    deal.participants.flatMap(deal.terms(_).disclosures).map {
      case NegotiationDisclosure(recipient,
          NegotiationDisclosureRef.Adviser(owner, card)) =>
        Peek(recipient, card, Location.PlayArea(owner)): CoreOperation
      case NegotiationDisclosure(recipient,
          NegotiationDisclosureRef.HeldRelic(owner, relic)) =>
        Peek(recipient, relic, Location.PlayArea(owner)): CoreOperation
      case NegotiationDisclosure(recipient,
          NegotiationDisclosureRef.SiteRelic(site, relic)) =>
        Peek(recipient, relic, Location.Site(site)): CoreOperation
    }

  private def transfers(deal: DealState): Vector[CoreOperation] =
    deal.participants.flatMap { author =>
      deal.terms(author).transfers.flatMap { transfer =>
        val favor = Option.when(transfer.favor > 0)(Give(Piece.Favor(
          transfer.favor), author, Location.PlayArea(author),
          Location.PlayArea(transfer.recipient)): CoreOperation).toVector
        val relics = transfer.relics.map(relic => Give(Piece.Card(relic),
          author, Location.PlayArea(author),
          Location.PlayArea(transfer.recipient)): CoreOperation)
        favor ++ relics
      }
    }
}
