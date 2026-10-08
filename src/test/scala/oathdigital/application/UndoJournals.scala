package oathdigital.application

import oathdigital.model._
import oathdigital.serialization.GameEventWire
import oathdigital.testkit.Unwrap

/** Journal surgery for undo tests: reading a stream's events, and writing
  * streams the service itself would never write. */
object UndoJournals:
  def events(repository: EventStreamRepository, gameId: String)
      (using munit.Location): Vector[OathEvent] =
    Unwrap.present(Unwrap.accepted(repository.load(gameId)),
      s"a stored stream for $gameId").records.map(record =>
        GameEventWire.decode(ujson.read(record)).fold(
          error => munit.Assertions.fail(s"undecodable: $error"), _.event))

  /** `gameId`'s journal as written before command batches were recorded:
    * every `CommandAccepted` dropped and the rest renumbered. */
  def withoutBatchOpenings(repository: EventStreamRepository, gameId: String)
      (using munit.Location): InMemoryEventStreamRepository =
    rewritten(events(repository, gameId)
      .filterNot(_.isInstanceOf[OathEvent.CommandAccepted]), gameId)

  /** A fresh repository holding exactly `events` as `gameId`. */
  def rewritten(events: Vector[OathEvent], gameId: String)
      (using munit.Location): InMemoryEventStreamRepository =
    val copy = new InMemoryEventStreamRepository
    val records = events.zipWithIndex.map { case (event, index) =>
      GameEventWire.encodeEvent(gameId, index.toLong, event).fold(
        error => munit.Assertions.fail(s"unencodable: $error"),
        encoded => ujson.write(encoded)) }
    munit.Assertions.assert(
      copy.append(gameId, ExpectedStream.MustNotExist, records).isRight)
    copy

  /** The journal index at which a second service over `repository`, started
    * where `service` started, fails replaying `gameId`; fails the test if
    * the load does anything else. */
  def replayFailureIndex(service: GameApplicationService,
      repository: EventStreamRepository, gameId: String)(using munit.Location)
      : Long =
    ParkedServiceFixture.replayingSecond(service, repository).load(gameId)
      match
      case Left(GameApplicationError.ReplayFailure(index, _)) => index
      case other => munit.Assertions.fail(
        s"expected a replay failure, got $other")
