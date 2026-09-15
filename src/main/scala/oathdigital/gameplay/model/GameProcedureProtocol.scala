package oathdigital.gameplay

import oathdigital.model._

sealed trait OathContinue extends Product with Serializable
object OathContinue {
  final case class AwaitingPawn(playerId: PlayerId) extends OathContinue
  final case class AwaitingAdviser(playerId: PlayerId)
      extends OathContinue
  final case class ReadyForFirstTurn(playerId: PlayerId)
      extends OathContinue
  final case class AwaitingWakeAction(playerId: PlayerId)
      extends OathContinue
  final case class ActActionSelection(playerId: PlayerId)
      extends OathContinue
  final case class AwaitingRestAction(playerId: PlayerId)
      extends OathContinue
  /** Any decision parked inside Finish Rest; the owner may be off-turn. */
  final case class AwaitingRestDecision(playerId: PlayerId,
      decision: DecisionId) extends OathContinue
  /** Any decision parked inside a used phase power. */
  final case class AwaitingPowerDecision(playerId: PlayerId,
      decision: DecisionId) extends OathContinue
  final case class AwaitingSearchDecision(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingRecoverRoll(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingRecoverRelic(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingForgeAssignment(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingBannerDecision(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingConspiracyDecision(playerId: PlayerId,
      decision: DecisionId) extends OathContinue
  final case class AwaitingCampaignSacrifice(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingCampaignPlan(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingCampaignPlacement(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingCampaignRaidRelocation(playerId: PlayerId,
      decision: DecisionId) extends OathContinue
  final case class AwaitingOathkeeperRecipient(playerId: PlayerId,
      decision: DecisionId) extends OathContinue
  final case class GameFinished(winner: PlayerId) extends OathContinue
}

final case class OathTransition(
    state: OathState,
    events: Vector[OathEvent],
    continue: OathContinue
)
