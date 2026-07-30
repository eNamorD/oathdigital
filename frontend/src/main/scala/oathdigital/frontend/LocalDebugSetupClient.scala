package oathdigital.frontend

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.model.{LineageId, PlayerId, SiteId}
import oathdigital.setup._
import oathdigital.setup.SetupCommand.{BeginSetup, PlacePawn}
import oathdigital.setup.SetupState.NotStarted

/**
 * Browser-memory authority for debug/manual testing only.
 *
 * Restart rotates to a fresh stream identity. Retired streams remain only as
 * in-memory diagnostics, modeling append-only history rather than rewriting
 * an existing stream.
 */
final class LocalDebugSetupClient private (
    val rules: SetupRules,
    val participants: Vector[SetupParticipant],
    val orderedSites: Vector[SiteId],
    designatedInitialEvents: Vector[SetupEvent],
    designatedInitialState: SetupState
) extends SetupClient
    with LocalDebugControl {
  private var activeStreamId = DebugStreamId(1L)
  private var stream = LocalDebugSetupClient.record(designatedInitialEvents, 0)
  private var current = designatedInitialState
  private var retired =
    Vector.empty[(DebugStreamId, Vector[RecordedEvent[SetupEvent]])]

  override def projection: SetupProjection = {
    val active = activePlayer(current)
    SetupProjection(
      activeStreamId,
      current,
      participants,
      orderedSites,
      active,
      active.fold(Vector.empty[SiteId])(_ => orderedSites),
      stream
    )
  }

  def retiredStreams
      : Vector[(DebugStreamId, Vector[RecordedEvent[SetupEvent]])] = retired

  override def submit(
      expectedPosition: Long,
      command: SetupClientCommand
  ): Either[SetupClientFailure, AcceptedSetupUpdate] =
    if (expectedPosition != stream.size.toLong)
      Left(
        SetupClientFailure.ExpectedPositionConflict(
          expectedPosition,
          stream.size.toLong
        )
      )
    else
      command match {
        case SetupClientCommand.PlacePawn(siteId) =>
          activePlayer(current) match {
            case None =>
              Left(SetupClientFailure.CommandRejected(
                SetupViolation.AlreadyCompleted
              ))
            case Some(playerId) =>
              rules
                .handle(current, PlacePawn(playerId, siteId))
                .left
                .map(SetupClientFailure.CommandRejected)
                .flatMap(transition =>
                  accept(transition.state, transition.events)
                )
          }
      }

  override def restartDebug()
      : Either[SetupClientFailure, DebugRestarted] = {
    val nextId = DebugStreamId(activeStreamId.value + 1)
    val fresh = LocalDebugSetupClient.record(designatedInitialEvents, 0)
    LocalDebugSetupClient
      .verifyReplay(rules, fresh, designatedInitialState)
      .map { replayDerived =>
        val previousId = activeStreamId
        retired :+= previousId -> stream
        activeStreamId = nextId
        stream = fresh
        current = replayDerived
        DebugRestarted(previousId, nextId, projection)
      }
  }

  private def accept(
      transitionState: SetupState,
      emitted: Vector[SetupEvent]
  ): Either[SetupClientFailure, AcceptedSetupUpdate] = {
    val accepted = LocalDebugSetupClient.record(emitted, stream.size)
    val candidate = stream ++ accepted
    LocalDebugSetupClient
      .verifyReplay(rules, candidate, transitionState)
      .map { replayDerived =>
        stream = candidate
        current = replayDerived
        AcceptedSetupUpdate(projection, accepted)
      }
  }

  private def activePlayer(state: SetupState): Option[PlayerId] =
    state match {
      case progress: SetupState.InProgress =>
        Some(progress.participants(progress.pawnPlacements.size).playerId)
      case _ => None
    }
}

object LocalDebugSetupClient {
  val defaultParticipants: Vector[SetupParticipant] =
    Vector(
      SetupParticipant(PlayerId("Chancellor"), LineageId("Purple")),
      SetupParticipant(PlayerId("Blue Exile"), LineageId("Blue")),
      SetupParticipant(PlayerId("Red Citizen"), LineageId("Red"))
    )

  def demo(): LocalDebugSetupClient = {
    val rules = new SetupRules(DemoCatalog.catalog)
    val sites = DemoCatalog.sites.map(_.id)
    val transition = rules
      .handle(
        NotStarted,
        BeginSetup(defaultParticipants, DemoCatalog.ref, sites)
      )
      .fold(
        violation => throw new IllegalStateException(violation.toString),
        identity
      )
    val initial = record(transition.events, 0)
    val replayed = verifyReplay(rules, initial, transition.state).fold(
      failure => throw new IllegalStateException(failure.message),
      identity
    )
    new LocalDebugSetupClient(
      rules,
      defaultParticipants,
      sites,
      transition.events,
      replayed
    )
  }

  private[frontend] def record(
      events: Vector[SetupEvent],
      offset: Int
  ): Vector[RecordedEvent[SetupEvent]] =
    events.zipWithIndex.map { case (event, index) =>
      RecordedEvent((offset + index).toLong, event)
    }

  private[frontend] def replay(
      rules: SetupRules,
      events: Vector[RecordedEvent[SetupEvent]]
  ): Either[SetupClientFailure, SetupState] =
    new EventReplayEngine(rules)
      .replay(events)
      .left
      .map(failure =>
        SetupClientFailure.ReplayFailed(failure.index, failure.violation)
      )

  private[frontend] def verifyReplay(
      rules: SetupRules,
      events: Vector[RecordedEvent[SetupEvent]],
      transitionState: SetupState
  ): Either[SetupClientFailure, SetupState] =
    replay(rules, events).flatMap { replayed =>
      if (replayed == transitionState) Right(replayed)
      else Left(SetupClientFailure.ReplayDiverged(transitionState, replayed))
    }
}
