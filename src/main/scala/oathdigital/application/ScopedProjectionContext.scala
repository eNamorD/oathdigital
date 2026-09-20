package oathdigital.application

import oathdigital.model.{PlayerId, PlayerState, ReadyGame, SiteState}
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
    boardTargets: Vector[BoardTargetActionProjection],
    minorActions: Option[MinorActionsProjection]
)

private[application] final case class PendingProjection(
    phase: String,
    cardDecision: Option[PendingCardDecisionProjection],
    campaign: Option[CampaignProjection],
    campaignRaidRelocation: Option[CampaignRaidRelocationProjection],
    walkerDecision: Option[WalkerDecisionProjection],
    walkerWaiting: Option[WalkerWaitingProjection]
)

// [[WalkerDecisionProjection]] itself now lives on the wire: it is the
// same type `GameProjection.walkerDecision` carries (`shared/.../
// ActionProjectionDtos.scala`), resolved here via the `protocol.projection`
// wildcard import above rather than a separate application-local type --
// see that file's doc comment for the owner-privacy and marker-relic
// rulings that used to live on this now-deleted local copy.
