package oathdigital.frontend

import munit.FunSuite
import oathdigital.engine.RecordedEvent
import oathdigital.model.PlayerId
import oathdigital.setup.{SetupEvent, SetupState}
import oathdigital.setup.SetupEvent.PawnPlaced

class SetupSessionSuite extends FunSuite {
  test("demo exposes all eight legal sites to the ordered active participant") {
    val session = SetupSession.demo()

    assertEquals(session.activePlayer, Some(session.participants.head.playerId))
    assertEquals(session.legalPlacements, session.orderedSites)
    assertEquals(session.events.map(_.index), Vector(0L))
  }

  test("placements emit events and expose the replay-derived state") {
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

  test("corrupt authoritative stream produces an explicit replay failure") {
    val session = SetupSession.demo()
    val corrupt = session.events :+ RecordedEvent[SetupEvent](
      1L,
      PawnPlaced(PlayerId("wrong-player"), session.orderedSites.head)
    )

    val failure =
      SetupSession.replay(session.rules, corrupt).left.toOption.get

    assert(failure.isInstanceOf[SetupSessionError.ReplayFailed])
    assertEquals(
      failure.asInstanceOf[SetupSessionError.ReplayFailed].index,
      1L
    )
  }

  test("transition and replay disagreement is rejected as divergence") {
    val session = SetupSession.demo()
    val correctPlacement = PawnPlaced(
      session.participants.head.playerId,
      session.orderedSites.head
    )
    val candidate =
      session.events :+ RecordedEvent[SetupEvent](1L, correctPlacement)

    val result =
      SetupSession.verifyReplay(session.rules, candidate, session.state)

    assert(result.left.toOption.exists(
      _.isInstanceOf[SetupSessionError.ReplayDiverged]
    ))
  }
}
