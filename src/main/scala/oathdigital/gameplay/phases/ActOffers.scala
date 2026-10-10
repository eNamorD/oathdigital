package oathdigital.gameplay.phases

import oathdigital.gameplay.actions.campaign.CampaignProcedure
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.model._

/** The Act menu's own options (walker-driven game loop design, slice 4,
  * plan-time ruling 4): the major actions, then the minor actions, each a
  * button keyed by its procedure. Phase powers and End Act and Rest are
  * added by `PhaseMenu`. */
object ActOffers:
  /** One option: the procedure its pick queues, with the start selection it
    * carries, its button and its label. */
  final case class ActOffer(procedure: ActionRef,
      args: Vector[DecisionOptionRef], ref: DecisionOptionRef.Button,
      label: String)

  private def plain(procedure: ActionRef, label: String): ActOffer =
    ActOffer(procedure, Vector.empty, DecisionOptionRef.Button(procedure.key),
      label)

  private def trade(resource: TradeResource, key: String, label: String)
      : ActOffer = ActOffer(ActionRef.Trade, TradeProcedure.selection(
    resource), DecisionOptionRef.Button(s"${ActionRef.Trade.key}.$key"),
    label)

  val all: Vector[ActOffer] = Vector(
    plain(ActionRef.Search, "Search"),
    plain(ActionRef.Travel, "Travel"),
    plain(ActionRef.Campaign, "Campaign"),
    plain(ActionRef.Muster, "Muster"),
    trade(TradeResource.Favor, "favor", "Trade for favor"),
    trade(TradeResource.Secret, "secret", "Trade for secrets"),
    plain(ActionRef.Forge, "Forge"),
    plain(ActionRef.Recover, "Recover"),
    plain(ActionRef.Challenge, "Challenge"),
    plain(ActionRef.PlaceBannerResource,
      "Place favor or secrets on a banner"),
    plain(ActionRef.Negotiation, "Negotiate"),
    plain(ActionRef.PlayFacedownAdviser, "Play facedown adviser"),
    plain(ActionRef.PeekSiteRelics, "Peek at relics at your site"),
    plain(ActionRef.RevealOwnedRelic, "Reveal a relic"),
    plain(ActionRef.MoveWarbands, "Move warbands"))

  /** Why an Act action cannot start, in the player's words, when its
    * refusal is not about Supply or favor. */
  def reason(procedure: StartableRef, violation: OathViolation): String =
    (procedure, violation) match
      case (_, OathViolation.StartUnavailable(text)) => text
      case (ActionRef.Campaign, OathViolation.CampaignUnavailable(detail))
          if detail == CampaignProcedure.NothingToAttack =>
        "No site to conquer and no enemy pawn to raid"
      case (ActionRef.Campaign, OathViolation.CampaignUnavailable(detail)) =>
        detail
      case (ActionRef.Negotiation,
          OathViolation.NegotiationUnavailable(detail)) => detail
      case (ActionRef.MoveWarbands,
          OathViolation.MinorActionUnavailable(detail)) => detail
      case (ActionRef.PeekSiteRelics, _) => "No relics at your site"
      case (ActionRef.RevealOwnedRelic, _) => "You have no facedown relic"
      case (ActionRef.PlayFacedownAdviser, _) =>
        "You have no facedown adviser"
      case (ActionRef.Search, _) => "Cannot search now"
      case (ActionRef.Travel, _) => "No site to travel to"
      case (ActionRef.Campaign, _) => "Cannot campaign now"
      case (ActionRef.Muster, _) => "No card to muster from"
      case (ActionRef.Trade, _) => "No card to trade with"
      case (ActionRef.Forge, _) => "Cannot forge here"
      case (ActionRef.Recover, _) => "Cannot recover here"
      case (ActionRef.Challenge, _) => "Cannot challenge now"
      case (ActionRef.PlaceBannerResource, _) => "No banner to place on"
      case (ActionRef.Negotiation, _) => "Cannot negotiate now"
      case _ => "Cannot start now"
