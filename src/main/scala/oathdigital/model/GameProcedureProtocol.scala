package oathdigital.model

import oathdigital.model._

sealed trait OathContinue extends Product with Serializable
object OathContinue {
  final case class AwaitingSetupPawn(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingSetupAdviser(playerId: PlayerId, decision: DecisionId)
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
  final case class AwaitingEconomyDecision(playerId: PlayerId,
      decision: DecisionId) extends OathContinue
  final case class AwaitingRecoverRoll(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingRecoverRelic(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingForgeAssignment(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingBannerDecision(playerId: PlayerId, decision: DecisionId)
      extends OathContinue

  /** Either decision of a Negotiation: the negotiator choice, or the open
    * deal, which any participant may answer. `playerId` is the primary owner,
    * the actor.
    */
  final case class AwaitingNegotiation(playerId: PlayerId, decision: DecisionId)
      extends OathContinue

  /** Any decision of a Campaign: the attacker's choices, and the defender's
    * battle plans, which are owned by the defender. `playerId` is the
    * decision's owner.
    */
  final case class AwaitingCampaignDecision(playerId: PlayerId,
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
