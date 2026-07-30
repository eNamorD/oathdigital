package oathdigital.frontend

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.model.{LineageId, PlayerId, SiteId}
import oathdigital.setup._
import oathdigital.setup.SetupCommand.{BeginSetup, PlacePawn}
import oathdigital.setup.SetupState.NotStarted

sealed trait SetupSessionError extends Product with Serializable {
  def message: String
}
object SetupSessionError {
  final case class RuleRejected(violation: SetupViolation)
      extends SetupSessionError {
    override val message: String = s"rule rejected command: $violation"
  }

  final case class ReplayFailed(index: Long, violation: SetupViolation)
      extends SetupSessionError {
    override val message: String =
      s"authoritative replay failed at event $index: $violation"
  }

  final case class ReplayDiverged(
      transitionState: SetupState,
      replayedState: SetupState
  ) extends SetupSessionError {
    override val message: String =
      "rule transition diverged from authoritative event replay"
  }
}

final class SetupSession(
    val rules: SetupRules,
    val participants: Vector[SetupParticipant],
    val orderedSites: Vector[SiteId]
) {
  private var current: SetupState = NotStarted
  private var stream = Vector.empty[RecordedEvent[SetupEvent]]
  private var latestError: Option[SetupSessionError] = None

  begin()

  def state: SetupState = current
  def events: Vector[RecordedEvent[SetupEvent]] = stream
  def error: Option[SetupSessionError] = latestError

  def activePlayer: Option[PlayerId] =
    state match {
      case progress: SetupState.InProgress =>
        Some(progress.participants(progress.pawnPlacements.size).playerId)
      case _ => None
    }

  def legalPlacements: Vector[SiteId] =
    activePlayer.fold(Vector.empty[SiteId])(_ => orderedSites)

  def place(siteId: SiteId): Either[SetupSessionError, SetupState] =
    activePlayer match {
      case None => Right(state)
      case Some(playerId) =>
        rules
          .handle(state, PlacePawn(playerId, siteId))
          .left
          .map(SetupSessionError.RuleRejected)
          .flatMap { transition =>
            accept(transition.state, transition.events)
          }
          .left
          .map { failure =>
            latestError = Some(failure)
            failure
          }
          .map { accepted =>
            latestError = None
            accepted
          }
    }

  def replayedState: Either[SetupSessionError, SetupState] =
    SetupSession.replay(rules, stream)

  private def begin(): Unit = {
    val command = BeginSetup(participants, DemoCatalog.ref, orderedSites)
    val accepted = rules
      .handle(NotStarted, command)
      .left
      .map(SetupSessionError.RuleRejected)
      .flatMap(transition => accept(transition.state, transition.events))

    accepted.fold(
      failure => throw new IllegalStateException(failure.message),
      _ => ()
    )
  }

  private def accept(
      transitionState: SetupState,
      emitted: Vector[SetupEvent]
  ): Either[SetupSessionError, SetupState] = {
    val candidate = stream ++ SetupSession.record(emitted, stream.size)
    SetupSession
      .verifyReplay(rules, candidate, transitionState)
      .map { replayDerived =>
        stream = candidate
        current = replayDerived
        replayDerived
      }
  }
}

object SetupSession {
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
  ): Either[SetupSessionError, SetupState] =
    new EventReplayEngine(rules)
      .replay(events)
      .left
      .map(failure =>
        SetupSessionError.ReplayFailed(failure.index, failure.violation)
      )

  private[frontend] def verifyReplay(
      rules: SetupRules,
      events: Vector[RecordedEvent[SetupEvent]],
      transitionState: SetupState
  ): Either[SetupSessionError, SetupState] =
    replay(rules, events).flatMap { replayed =>
      if (replayed == transitionState) Right(replayed)
      else
        Left(
          SetupSessionError.ReplayDiverged(transitionState, replayed)
        )
    }

  val defaultParticipants: Vector[SetupParticipant] =
    Vector(
      SetupParticipant(PlayerId("Chancellor"), LineageId("Purple")),
      SetupParticipant(PlayerId("Exile One"), LineageId("Blue")),
      SetupParticipant(PlayerId("Exile Two"), LineageId("Red"))
    )

  def demo(): SetupSession =
    new SetupSession(
      new SetupRules(DemoCatalog.catalog),
      defaultParticipants,
      DemoCatalog.sites.map(_.id)
    )
}
