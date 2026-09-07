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
    negotiationWaiting: Boolean,
    restPower: Option[RestPowerProjection],
    restPowerWaiting: Boolean,
    walkerDecision: Option[WalkerDecisionProjection]
)

/** Owner-private projection of a parked generic-walker decision (Task 6:
  * `CurrentGameState.walkerPending` / `walkerAction`) — the walker path's
  * counterpart to [[RecoverProjection]]/[[PendingCardDecisionProjection]],
  * which the walker deliberately never populates (those stay wired to the
  * legacy `PendingProcedure.Recover`, untouched by this slice).
  *
  * `kind` is the client-facing verb, not the tree's structural node type:
  * `"roll"` means answer with `RollWalker` (no faces ride the command —
  * `pool`/`count` are informational only), `"decide"` means answer with
  * `ResolveWalker`. `decisionId` is always the parked node's stable identity
  * (a synthetic id for a Roll park, since only `Decide` nodes carry one
  * natively — see `RecoverProcedure.rollDecisionId`).
  *
  * Deliberately carries no relic identity: `RecoverProcedure`'s
  * `"recover.relic"` Decide closes over a placeholder marker relic id (the
  * first facedown relic at the site, used only to type-tag the payload) —
  * the actual chosen relic rides the `ResolveWalker` answer, never the
  * park. Surfacing that marker here would present it as a preselected or
  * committed choice, which it is not.
  */
private[application] final case class WalkerDecisionProjection(
    action: String,
    decisionId: String,
    kind: String,
    pool: Option[String] = None,
    count: Option[Int] = None
)
