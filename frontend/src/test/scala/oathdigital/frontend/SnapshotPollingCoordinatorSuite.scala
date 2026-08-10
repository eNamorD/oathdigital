package oathdigital.frontend

import munit.FunSuite
import scala.collection.mutable

class SnapshotPollingCoordinatorSuite extends FunSuite {
  test("schedules conservatively and never overlaps a current-generation poll") {
    val clock = new FakePollClock
    val requests = mutable.ArrayBuffer.empty[ServerRequestIdentity]
    val poller = new SnapshotPollingCoordinator(clock, requests += _, 5000, 30000)
    val request = ServerRequestIdentity(1, "game-1", "red-exile")

    poller.resume(request)
    assertEquals(clock.activeDelays, Vector(5000))
    clock.fireNext()
    assertEquals(requests.toVector, Vector(request))
    assert(poller.hasInFlightPoll)
    assertEquals(clock.activeDelays, Vector.empty)

    poller.resume(request)
    assert(poller.hasInFlightPoll)
    assertEquals(clock.activeDelays, Vector.empty)

    assert(poller.complete(request, continuePolling = true))
    assertEquals(clock.activeDelays, Vector(5000))
    clock.fireNext()
    assertEquals(requests.toVector, Vector(request, request))
  }

  test("a changed snapshot must be resumed before another poll is scheduled") {
    val clock = new FakePollClock
    val requests = mutable.ArrayBuffer.empty[ServerRequestIdentity]
    val poller = new SnapshotPollingCoordinator(clock, requests += _)
    val request = ServerRequestIdentity(1, "game-1", "red-exile")

    poller.resume(request)
    clock.fireNext()
    assert(poller.complete(request, continuePolling = false))
    assert(!poller.isRunning)
    assertEquals(clock.activeDelays, Vector.empty)

    poller.resume(request)
    assert(poller.isRunning)
    assertEquals(clock.activeDelays, Vector(5000))
  }

  test("nextSequence distinguishes unchanged, older, and changed snapshots") {
    val session = new ServerSessionCoordinator("game-1", "red-exile")
    val request = session.switchSession("game-1", "red-exile")

    assert(!session.snapshotAdvances(request, 7, 7))
    assert(!session.snapshotAdvances(request, 7, 6))
    assert(session.snapshotAdvances(request, 7, 8))
    assert(session.recordSnapshotSuccess(request))
    assertEquals(session.connectionState, ServerConnectionState.Connected)
  }

  test("hidden polling slows and becoming visible catches up immediately") {
    val clock = new FakePollClock
    val requests = mutable.ArrayBuffer.empty[ServerRequestIdentity]
    val poller = new SnapshotPollingCoordinator(clock, requests += _, 5000, 30000)
    val request = ServerRequestIdentity(1, "game-1", "red-exile")

    poller.resume(request)
    poller.visibilityChanged(isHidden = true)
    assertEquals(clock.activeDelays, Vector(30000))
    poller.visibilityChanged(isHidden = false)
    assertEquals(requests.toVector, Vector(request))
    assert(poller.hasInFlightPoll)
    assertEquals(clock.activeDelays, Vector.empty)
  }

  test("disconnect stops polling and explicit reconnect resumes it") {
    val clock = new FakePollClock
    val requests = mutable.ArrayBuffer.empty[ServerRequestIdentity]
    val poller = new SnapshotPollingCoordinator(clock, requests += _)
    val session = new ServerSessionCoordinator("game-1", "red-exile")
    val initial = session.switchSession("game-1", "red-exile")

    poller.resume(initial)
    clock.fireNext()
    val offline = GameClientFailure.NetworkFailure("offline")
    assert(session.recordFailure(initial, offline))
    assert(poller.complete(initial, continuePolling = false))
    poller.stop()
    assert(!poller.isRunning)
    assertEquals(clock.activeDelays, Vector.empty)

    val reconnect = session.reconnect()
    poller.resume(reconnect)
    assert(poller.isRunning)
    assertEquals(clock.activeDelays, Vector(5000))
  }

  test("session switches reject stale timers and callbacks") {
    val clock = new FakePollClock
    val requests = mutable.ArrayBuffer.empty[ServerRequestIdentity]
    val poller = new SnapshotPollingCoordinator(clock, requests += _)
    val old = ServerRequestIdentity(1, "game-a", "red-exile")
    val current = ServerRequestIdentity(2, "game-b", "blue-exile")

    poller.resume(old)
    val oldTimer = clock.latestIndex
    poller.resume(current)
    clock.fireEvenIfCancelled(oldTimer)
    assertEquals(requests.toVector, Vector.empty)

    clock.fireNext()
    assertEquals(requests.toVector, Vector(current))
    assert(!poller.complete(old, continuePolling = true))
    assert(poller.complete(current, continuePolling = true))
  }

  private final class FakePollClock extends PollClock {
    private final class Entry(
        val delay: Int,
        val task: () => Unit,
        var cancelled: Boolean
    )
    private val entries = mutable.ArrayBuffer.empty[Entry]

    override def schedule(delayMillis: Int)(task: () => Unit) = {
      val entry = new Entry(delayMillis, task, cancelled = false)
      entries += entry
      new PollCancellation {
        override def cancel(): Unit = entry.cancelled = true
      }
    }

    def activeDelays: Vector[Int] =
      entries.filterNot(_.cancelled).map(_.delay).toVector

    def latestIndex: Int = entries.size - 1

    def fireNext(): Unit = {
      val entry = entries.find(!_.cancelled).getOrElse(
        fail("no active timer")
      )
      entry.cancelled = true
      entry.task()
    }

    def fireEvenIfCancelled(index: Int): Unit = entries(index).task()
  }
}
