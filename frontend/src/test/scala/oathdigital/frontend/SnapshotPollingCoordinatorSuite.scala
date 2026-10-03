package oathdigital.frontend

import munit.FunSuite
import scala.collection.mutable

class SnapshotPollingCoordinatorSuite extends FunSuite:
  test("schedules conservatively and never overlaps a current-generation poll"):
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

  test("a changed snapshot must be resumed before another poll is scheduled"):
    val clock = new FakePollClock
    val requests = mutable.ArrayBuffer.empty[ServerRequestIdentity]
    val poller = new SnapshotPollingCoordinator(clock, requests += _,
      5000, 30000)
    val request = ServerRequestIdentity(1, "game-1", "red-exile")

    poller.resume(request)
    clock.fireNext()
    assert(poller.complete(request, continuePolling = false))
    assert(!poller.isRunning)
    assertEquals(clock.activeDelays, Vector.empty)

    poller.resume(request)
    assert(poller.isRunning)
    assertEquals(clock.activeDelays, Vector(5000))

  test("hidden polling slows and becoming visible catches up immediately"):
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

  test("session switches reject stale timers and callbacks"):
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
