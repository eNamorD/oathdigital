package oathdigital.frontend

import oathdigital.engine.RecordedEvent
import oathdigital.model.{PlayerId, SiteId}
import oathdigital.setup.{SetupEvent, SetupState, SetupViolation}
import scala.concurrent.Future

final case class DebugStreamId(value: Long) extends AnyVal

sealed trait PlayerColorToken extends Product with Serializable {
  def cssClass: String
}
object PlayerColorToken {
  case object Purple extends PlayerColorToken {
    override val cssClass: String = "player-purple"
  }
  case object Blue extends PlayerColorToken {
    override val cssClass: String = "player-blue"
  }
  case object Red extends PlayerColorToken {
    override val cssClass: String = "player-red"
  }
  case object Neutral extends PlayerColorToken {
    override val cssClass: String = "player-neutral"
  }
}

final case class PlayerDisplay(
    id: PlayerId,
    label: String,
    color: PlayerColorToken
)

final case class SiteDisplay(id: SiteId, label: String)
final case class RegionDisplay(name: String, sites: Vector[SiteDisplay])
final case class WorldDisplay(title: String, regions: Vector[RegionDisplay])

sealed trait SetupClientCommand extends Product with Serializable
object SetupClientCommand {
  final case class PlacePawn(siteId: SiteId) extends SetupClientCommand
}

final case class SetupProjection(
    streamId: DebugStreamId,
    nextSequence: Long,
    state: SetupState,
    players: Vector[PlayerDisplay],
    world: WorldDisplay,
    activePlayer: Option[PlayerId],
    legalPlacements: Vector[SiteId],
    visibleEvents: Vector[RecordedEvent[SetupEvent]]
)

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

  final case class MissingSiteDefinition(siteId: SiteId)
      extends SetupClientFailure {
    override val message: String =
      s"projection has no display definition for site ${siteId.value}"
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
  def load(): Future[Either[SetupClientFailure, SetupProjection]]
  def refresh(): Future[Either[SetupClientFailure, SetupProjection]]
  def submit(
      expectedNextSequence: Long,
      command: SetupClientCommand
  ): Future[Either[SetupClientFailure, AcceptedSetupUpdate]]
}

final case class DebugRestarted(
    retiredStream: DebugStreamId,
    newStream: DebugStreamId,
    projection: SetupProjection
)

/** Explicit debug-only lifecycle; production clients must not implement it. */
trait LocalDebugControl {
  def restartDebug(): Future[Either[SetupClientFailure, DebugRestarted]]
}
