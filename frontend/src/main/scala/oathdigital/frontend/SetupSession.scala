package oathdigital.frontend

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.model.{LineageId, PlayerId, SiteId}
import oathdigital.setup._
import oathdigital.setup.SetupCommand.{BeginSetup, PlacePawn}
import oathdigital.setup.SetupState.NotStarted

final class SetupSession(
    val rules: SetupRules,
    val participants: Vector[SetupParticipant],
    val orderedSites: Vector[SiteId]
) {
  private var current: SetupState = NotStarted
  private var stream = Vector.empty[RecordedEvent[SetupEvent]]

  begin()

  def state: SetupState = current
  def events: Vector[RecordedEvent[SetupEvent]] = stream

  def activePlayer: Option[PlayerId] =
    state match {
      case progress: SetupState.InProgress =>
        Some(progress.participants(progress.pawnPlacements.size).playerId)
      case _ => None
    }

  def legalPlacements: Vector[SiteId] =
    activePlayer.fold(Vector.empty[SiteId])(_ => orderedSites)

  def place(siteId: SiteId): Either[SetupViolation, SetupState] =
    activePlayer match {
      case None => Right(state)
      case Some(playerId) =>
        rules.handle(state, PlacePawn(playerId, siteId)).map { transition =>
          append(transition.events)
          current = transition.state
          current
        }
    }

  def replayedState: Either[String, SetupState] =
    new EventReplayEngine(rules)
      .replay(stream)
      .left
      .map(failure => s"event ${failure.index}: ${failure.violation}")

  private def begin(): Unit = {
    val command =
      BeginSetup(participants, DemoCatalog.ref, orderedSites)
    val transition = rules.handle(NotStarted, command).fold(
      violation => throw new IllegalStateException(violation.toString),
      identity
    )
    current = transition.state
    append(transition.events)
  }

  private def append(events: Vector[SetupEvent]): Unit = {
    val offset = stream.size
    stream ++= events.zipWithIndex.map { case (event, index) =>
      RecordedEvent((offset + index).toLong, event)
    }
  }
}

object SetupSession {
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
