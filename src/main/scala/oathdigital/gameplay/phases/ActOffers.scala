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
    * carries, its button, its label and whether the rules print it as a
    * major or a minor action. */
  final case class ActOffer(procedure: ActionRef,
      args: Vector[DecisionOptionRef], ref: DecisionOptionRef.Button,
      label: String, actionClass: ActionClass)

  private def major(procedure: ActionRef, label: String): ActOffer =
    plain(procedure, label, ActionClass.Major)

  private def minor(procedure: ActionRef, label: String): ActOffer =
    plain(procedure, label, ActionClass.Minor)

  private def plain(procedure: ActionRef, label: String,
      actionClass: ActionClass): ActOffer =
    ActOffer(procedure, Vector.empty, DecisionOptionRef.Button(procedure.key),
      label, actionClass)

  private def trade(resource: TradeResource, key: String, label: String)
      : ActOffer = ActOffer(ActionRef.Trade, TradeProcedure.selection(
    resource), DecisionOptionRef.Button(s"${ActionRef.Trade.key}.$key"),
    label, ActionClass.Major)

  val all: Vector[ActOffer] = Vector(
    major(ActionRef.Search, "Search"),
    major(ActionRef.Travel, "Travel"),
    major(ActionRef.Campaign, "Campaign"),
    major(ActionRef.Muster, "Muster"),
    trade(TradeResource.Favor, "favor", "Trade for favor"),
    trade(TradeResource.Secret, "secret", "Trade for secrets"),
    major(ActionRef.Forge, "Forge"),
    major(ActionRef.Recover, "Recover"),
    major(ActionRef.Challenge, "Challenge"),
    minor(ActionRef.PlaceBannerResource,
      "Place favor or secrets on a banner"),
    minor(ActionRef.Negotiation, "Negotiate"),
    minor(ActionRef.PlayFacedownAdviser, "Play facedown adviser"),
    minor(ActionRef.PeekSiteRelics, "Peek at relics at your site"),
    minor(ActionRef.RevealOwnedRelic, "Reveal a relic"),
    minor(ActionRef.MoveWarbands, "Move warbands"))

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
