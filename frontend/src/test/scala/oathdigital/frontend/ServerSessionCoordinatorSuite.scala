package oathdigital.frontend

import munit.FunSuite

/** The server session's bookkeeping of which snapshots to accept and
  * whether the connection holds.
  */
class ServerSessionCoordinatorSuite extends FunSuite:
  test("snapshotAdvances distinguishes unchanged, older, and changed " +
      "snapshots"):
    val session = new ServerSessionCoordinator("game-1", "red-exile")
    val request = session.switchSession("game-1", "red-exile")

    assert(!session.snapshotAdvances(request, 7, 7))
    assert(!session.snapshotAdvances(request, 7, 6))
    assert(session.snapshotAdvances(request, 7, 8))

  test("recording a snapshot success marks the connection Connected"):
    val session = new ServerSessionCoordinator("game-1", "red-exile")
    val request = session.switchSession("game-1", "red-exile")

    assert(session.recordSnapshotSuccess(request))
    assertEquals(session.connectionState, ServerConnectionState.Connected)
