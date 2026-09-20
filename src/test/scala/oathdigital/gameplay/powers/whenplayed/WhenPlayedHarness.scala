package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.gameplay.powers.PowerFixture.actor
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers, WalkerStepRecorded}
import oathdigital.model._

/** Drives a When Played power through its `CardPlayed` hook, the way the
  * Dazzle and Conspiracy suites do.
  */
object WhenPlayedHarness {
  def hook(card: DenizenId): CardPlayed =
    CardPlayed(card, RuleSourceRef.Adviser(actor, card))

  def powers(power: ContributingPower): WalkerPowers =
    WalkerPowers(Vector(power))

  def play(ready: ReadyGame, power: ContributingPower, card: DenizenId)
      : Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.advance(ready, hook(card), None, powers(power))

  def finished(outcome: Either[OathViolation, WalkerOutcome])
      : WalkerOutcome.Finished =
    outcome.toOption.get.asInstanceOf[WalkerOutcome.Finished]

  def parked(outcome: Either[OathViolation, WalkerOutcome])
      : WalkerOutcome.Parked =
    outcome.toOption.get.asInstanceOf[WalkerOutcome.Parked]

  def recorded(events: Vector[OathEvent]): Vector[CoreOperation] =
    events.collect { case step: WalkerStepRecorded => step.ops }.flatten

  /** The state a journal replay of `events` reaches from `from`. */
  def replayed(from: ReadyGame, events: Vector[OathEvent]): ReadyGame =
    OperationPipeline.run(from, recorded(events),
      OperationPolicy.Permissive)(Right(_)).toOption.get.state
}
