package oathdigital.gameplay

import oathdigital.gameplay.walker.FollowUpOffer
import oathdigital.model._

/** Answers a follow-up offer as its player does (walker-driven game loop
  * design, ruling 6). */
object FollowUpPlay:
  val take: DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(FollowUpOffer.take)
  val skip: DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(FollowUpOffer.skip)

  def taken(rules: OathRules, state: OathState, actor: PlayerId)
      : Either[OathViolation, OathTransition] =
    rules.resolveWalker(state, actor, FollowUpOffer.decisionId, take)

  def skipped(rules: OathRules, state: OathState, actor: PlayerId)
      : Either[OathViolation, OathTransition] =
    rules.resolveWalker(state, actor, FollowUpOffer.decisionId, skip)

  /** The entry the parked offer asks about, when an offer is parked. */
  def offered(state: OathState): Option[QueuedProcedure] = state match
    case OathState.Ready(ready) if ready.game.current.walkerProcedure
        .contains(TriggeredProcedureRef.FollowUpOffer) =>
      ready.game.current.walkerEntry
    case _ => None

  /** The driver on a table that holds a queue, as when the walk that
    * queued it ends: it dequeues the head. */
  def dequeued(rules: OathRules, state: OathState)
      : Either[OathViolation, OathTransition] =
    rules.next(OathTransition(state, Vector.empty),
      Ended.Walk(ActionRef.Travel))
