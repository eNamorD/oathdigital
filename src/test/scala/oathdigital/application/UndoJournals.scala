package oathdigital.application

import oathdigital.model._
import oathdigital.serialization.GameEventWire

/** Journal surgery for undo tests: reading a stream's events, and writing
  * streams the service itself would never write. */
object UndoJournals:
  def events(repository: EventStreamRepository, gameId: String)
      (using munit.Location): Vector[OathEvent] =
    repository.load(gameId).toOption.flatten.toVector
      .flatMap(_.records).map(record =>
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
