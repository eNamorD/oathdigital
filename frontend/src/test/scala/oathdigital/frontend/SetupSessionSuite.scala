package oathdigital.frontend

import munit.FunSuite
import oathdigital.setup.{SetupEvent, SetupState}

class SetupSessionSuite extends FunSuite {
  test("demo exposes all eight legal sites to the ordered active participant") {
    val session = SetupSession.demo()

    assertEquals(session.activePlayer, Some(session.participants.head.playerId))
    assertEquals(session.legalPlacements, session.orderedSites)
    assertEquals(session.events.map(_.index), Vector(0L))
  }

  test("placements emit authoritative events and replay to the live state") {
    val session = SetupSession.demo()

    session.orderedSites.take(3).foreach { site =>
      assert(session.place(site).isRight)
    }

    assert(session.state.isInstanceOf[SetupState.Completed])
    assertEquals(session.activePlayer, None)
    assertEquals(session.legalPlacements, Vector.empty)
    assertEquals(session.events.size, 5)
    assertEquals(session.events.last.event, SetupEvent.SetupCompleted)
    assertEquals(session.replayedState, Right(session.state))
  }

  test("event positions remain contiguous when final command emits two events") {
    val session = SetupSession.demo()
    session.orderedSites.take(3).foreach(session.place)

    assertEquals(
      session.events.map(_.index),
      Vector(0L, 1L, 2L, 3L, 4L)
    )
  }
}
