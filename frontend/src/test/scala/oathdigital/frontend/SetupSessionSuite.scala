package oathdigital.frontend

import munit.FunSuite
import oathdigital.setup.SetupState

class LocalDebugSetupClientSuite extends FunSuite {
  test("demo exposes all eight legal sites to the ordered active participant") {
    val client = LocalDebugSetupClient.demo()
    val projection = client.projection

    assertEquals(
      projection.activePlayer,
      Some(projection.participants.head.playerId)
    )
    assertEquals(projection.legalPlacements, projection.orderedSites)
    assertEquals(projection.acceptedEvents.map(_.index), Vector(0L))
  }

  test("accepted update returns only newly accepted events and projection") {
    val client = LocalDebugSetupClient.demo()

    val result = client
      .submit(
        client.projection.expectedPosition,
        SetupClientCommand.PlacePawn(client.projection.orderedSites.head)
      )
      .toOption
      .get

    assertEquals(result.acceptedEvents.size, 1)
    assertEquals(result.projection.expectedPosition, 2L)
    assertEquals(client.projection, result.projection)
  }

  test("stale expected position is a typed conflict and changes no history") {
    val client = LocalDebugSetupClient.demo()
    val before = client.projection

    val result = client.submit(
      0L,
      SetupClientCommand.PlacePawn(before.orderedSites.head)
    )

    assertEquals(
      result,
      Left(SetupClientFailure.ExpectedPositionConflict(0L, 1L))
    )
    assertEquals(client.projection, before)
  }

  test("restart retires history and reconstructs designated initial state") {
    val client = LocalDebugSetupClient.demo()
    val designated = client.projection
    client.submit(
      designated.expectedPosition,
      SetupClientCommand.PlacePawn(designated.orderedSites.head)
    )
    val completedStream = client.projection

    val restarted = client.restartDebug().toOption.get

    assertEquals(restarted.retiredStream, designated.streamId)
    assertNotEquals(restarted.newStream, restarted.retiredStream)
    assertEquals(restarted.projection.expectedPosition, 1L)
    assertEquals(restarted.projection.state, designated.state)
    assertEquals(restarted.projection.activePlayer, designated.activePlayer)
    assertEquals(client.retiredStreams.size, 1)
    assertEquals(
      client.retiredStreams.head._2,
      completedStream.acceptedEvents
    )
  }

  test("final placement emits completion and replay-derived completed state") {
    val client = LocalDebugSetupClient.demo()
    client.projection.orderedSites.take(3).foreach { site =>
      val position = client.projection.expectedPosition
      assert(client.submit(
        position,
        SetupClientCommand.PlacePawn(site)
      ).isRight)
    }

    assert(client.projection.state.isInstanceOf[SetupState.Completed])
    assertEquals(client.projection.activePlayer, None)
    assertEquals(client.projection.legalPlacements, Vector.empty)
    assertEquals(
      client.projection.acceptedEvents.map(_.index),
      Vector(0L, 1L, 2L, 3L, 4L)
    )
  }
}

class SetupViewModelSuite extends FunSuite {
  test("world regions preserve title, left-to-right order, and site counts") {
    val view = SetupViewModel.from(LocalDebugSetupClient.demo().projection)

    assertEquals(view.worldTitle, "The World")
    assertEquals(view.regions.map(_.name), Vector(
      "Cradle",
      "Provinces",
      "Hinterland"
    ))
    assertEquals(view.regions.map(_.sites.size), Vector(2, 3, 3))
    assertEquals(
      view.regions.flatMap(_.sites),
      LocalDebugSetupClient.demo().projection.orderedSites
    )
  }

  test("complete player names carry stable non-color semantics and classes") {
    val players =
      SetupViewModel.from(LocalDebugSetupClient.demo().projection).players

    assertEquals(
      players.map(player => player.text -> player.colorClass),
      Vector(
        "Chancellor" -> "player-purple",
        "Blue Exile" -> "player-blue",
        "Red Citizen" -> "player-red"
      )
    )
  }
}
