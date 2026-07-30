package oathdigital.frontend

import oathdigital.engine.RecordedEvent
import oathdigital.model.{PlayerId, SiteId}
import oathdigital.setup.{SetupEvent, SetupParticipant, SetupState, SetupViolation}

final case class DebugStreamId(value: Long) extends AnyVal

sealed trait SetupClientCommand extends Product with Serializable
object SetupClientCommand {
  final case class PlacePawn(siteId: SiteId) extends SetupClientCommand
}

final case class SetupProjection(
    streamId: DebugStreamId,
    state: SetupState,
    participants: Vector[SetupParticipant],
    orderedSites: Vector[SiteId],
    activePlayer: Option[PlayerId],
    legalPlacements: Vector[SiteId],
    acceptedEvents: Vector[RecordedEvent[SetupEvent]]
) {
  def expectedPosition: Long = acceptedEvents.size.toLong
}

sealed trait SetupClientFailure extends Product with Serializable {
  def message: String
}
object SetupClientFailure {
  final case class ExpectedPositionConflict(expected: Long, actual: Long)
      extends SetupClientFailure {
    override val message: String =
      s"expected event position $expected, but current position is $actual"
  }

  final case class CommandRejected(violation: SetupViolation)
      extends SetupClientFailure {
    override val message: String = s"command rejected: $violation"
  }

  final case class ReplayFailed(index: Long, violation: SetupViolation)
      extends SetupClientFailure {
    override val message: String =
      s"accepted-event replay failed at event $index: $violation"
  }

  final case class ReplayDiverged(
      transitionState: SetupState,
      replayedState: SetupState
  ) extends SetupClientFailure {
    override val message: String =
      "command transition diverged from accepted-event replay"
  }
}

final case class AcceptedSetupUpdate(
    projection: SetupProjection,
    acceptedEvents: Vector[RecordedEvent[SetupEvent]]
)

/**
 * Transport-neutral browser boundary.
 *
 * A production adapter submits transient commands plus `expectedPosition` to
 * the authoritative JVM service and consumes only accepted events and a
 * player-scoped projection. No HTTP wire format is defined here.
 */
trait SetupClient {
  def projection: SetupProjection
  def submit(
      expectedPosition: Long,
      command: SetupClientCommand
  ): Either[SetupClientFailure, AcceptedSetupUpdate]
}

final case class DebugRestarted(
    retiredStream: DebugStreamId,
    newStream: DebugStreamId,
    projection: SetupProjection
)

/** Explicit debug-only lifecycle; production clients must not implement it. */
trait LocalDebugControl {
  def restartDebug(): Either[SetupClientFailure, DebugRestarted]
}
