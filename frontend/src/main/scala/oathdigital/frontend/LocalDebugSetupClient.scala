package oathdigital.frontend

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.catalog.SiteDefinition
import oathdigital.model.{LineageId, PlayerId, SiteId}
import oathdigital.setup._
import oathdigital.setup.SetupCommand.{BeginSetup, PlacePawn}
import oathdigital.setup.SetupState.NotStarted
import scala.concurrent.Future

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
    playerDisplays: Vector[PlayerDisplay],
    worldDisplay: WorldDisplay,
    startupFailure: Option[SetupClientFailure],
    designatedInitialEvents: Vector[SetupEvent],
    designatedInitialState: SetupState
) extends SetupClient
    with LocalDebugControl {
  private var activeStreamId = DebugStreamId(1L)
  private var stream = LocalDebugSetupClient.record(designatedInitialEvents, 0)
  private var current = designatedInitialState
  private var retired =
    Vector.empty[(DebugStreamId, Vector[RecordedEvent[SetupEvent]])]

  private def currentProjection: SetupProjection = {
    val active = activePlayer(current)
    SetupProjection(
      activeStreamId,
      stream.size.toLong,
      current,
      playerDisplays,
      worldDisplay,
      active,
      active.fold(Vector.empty[SiteId])(_ => orderedSites),
      stream
    )
  }

  override def load()
      : Future[Either[SetupClientFailure, SetupProjection]] =
    Future.successful(startupFailure.toLeft(currentProjection))

  override def refresh()
      : Future[Either[SetupClientFailure, SetupProjection]] =
    Future.successful(startupFailure.toLeft(currentProjection))

  def retiredStreams
      : Vector[(DebugStreamId, Vector[RecordedEvent[SetupEvent]])] = retired

  override def submit(
      expectedNextSequence: Long,
      command: SetupClientCommand
  ): Future[Either[SetupClientFailure, AcceptedSetupUpdate]] =
    Future.successful(startupFailure match {
      case Some(failure) => Left(failure)
      case None => submitNow(expectedNextSequence, command)
    })

  private def submitNow(
      expectedNextSequence: Long,
      command: SetupClientCommand
  ): Either[SetupClientFailure, AcceptedSetupUpdate] =
    if (expectedNextSequence != stream.size.toLong)
      Left(
        SetupClientFailure.ExpectedPositionConflict(
          expectedNextSequence,
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
      : Future[Either[SetupClientFailure, DebugRestarted]] =
    Future.successful(startupFailure match {
      case Some(failure) => Left(failure)
      case None => restartNow()
    })

  private def restartNow()
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
        DebugRestarted(previousId, nextId, currentProjection)
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
        AcceptedSetupUpdate(currentProjection, accepted)
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

  val defaultPlayers: Vector[PlayerDisplay] =
    Vector(
      PlayerDisplay(
        PlayerId("Chancellor"),
        "Chancellor",
        PlayerColorToken.Purple
      ),
      PlayerDisplay(
        PlayerId("Blue Exile"),
        "Blue Exile",
        PlayerColorToken.Blue
      ),
      PlayerDisplay(
        PlayerId("Red Citizen"),
        "Red Citizen",
        PlayerColorToken.Red
      )
    )

  def demo(): LocalDebugSetupClient = {
    val rules = new SetupRules(DemoCatalog.catalog)
    val sites = DemoCatalog.sites.map(_.id)
    val worldResult = buildWorld(sites, DemoCatalog.sites)
    val world = worldResult.getOrElse(WorldDisplay("The World", Vector.empty))
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
      defaultPlayers,
      world,
      worldResult.left.toOption,
      transition.events,
      replayed
    )
  }

  private[frontend] def buildWorld(
      orderedSites: Vector[SiteId],
      definitions: Vector[SiteDefinition]
  ): Either[SetupClientFailure, WorldDisplay] = {
    val byId = definitions.map(site => site.id -> site).toMap
    orderedSites
      .foldLeft[
        Either[SetupClientFailure, Vector[SiteDisplay]]
      ](Right(Vector.empty)) { (result, siteId) =>
        result.flatMap { accumulated =>
          byId
            .get(siteId)
            .map(site =>
              Right(accumulated :+ SiteDisplay(site.id, site.name))
            )
            .getOrElse(Left(
              SetupClientFailure.MissingSiteDefinition(siteId)
            ))
        }
      }
      .map { sites =>
        WorldDisplay(
          "The World",
          Vector(
            RegionDisplay("Cradle", sites.take(2)),
            RegionDisplay("Provinces", sites.slice(2, 5)),
            RegionDisplay("Hinterland", sites.slice(5, 8))
          )
        )
      }
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
