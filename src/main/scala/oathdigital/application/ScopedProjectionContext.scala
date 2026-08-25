package oathdigital.application

import oathdigital.gameplay.ReadyGame
import oathdigital.model.{PlayerId, PlayerState, SiteState}
import oathdigital.protocol.projection._

private[application] final case class ScopedProjectionContext(
    ready: ReadyGame,
    viewer: Option[PlayerId]
) {
  val current = ready.game.current
  val active: PlayerState = current.players.find(
    _.player == current.turn.activePlayer).get
  val activeSite: Option[SiteState] = active.pawnSite.flatMap(current.map.sites.get)
  val viewerIsActive: Boolean = viewer.contains(active.player)
}

private[application] final case class LegalProjection(
    controls: Vector[String],
    travel: Vector[LegalTravelDestinationProjection],
    search: Vector[LegalSearchSourceProjection],
    musters: Vector[LegalMusterProjection],
    trades: Vector[LegalTradeProjection],
    boardTargets: Vector[BoardTargetActionProjection],
    minorActions: Option[MinorActionsProjection]
)

private[application] final case class PendingProjection(
    phase: String,
    cardDecision: Option[PendingCardDecisionProjection],
    recover: Option[RecoverProjection],
    forge: Option[ForgeProjection],
    campaign: Option[CampaignProjection],
    campaignRaidRelocation: Option[CampaignRaidRelocationProjection],
    oathkeeperRecipient: Option[OathkeeperRecipientProjection],
    challenge: Option[ChallengeProjection],
    negotiation: Option[NegotiationProjection],
    negotiationWaiting: Boolean
)
