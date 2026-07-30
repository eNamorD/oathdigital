package oathdigital.frontend

import munit.FunSuite
import oathdigital.model.{PlayerId, SiteId}
import oathdigital.setup.SetupState
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class LocalDebugSetupClientSuite extends FunSuite {
  test("load exposes explicit sequence and display-ready projection") {
    val client = LocalDebugSetupClient.demo()
    client.load().map { loaded =>
      val projection = loaded.toOption.get
      assertEquals(projection.nextSequence, 1L)
      assertEquals(projection.visibleEvents.map(_.index), Vector(0L))
      assertEquals(projection.players.map(_.label), Vector(
        "Chancellor",
        "Blue Exile",
        "Red Citizen"
      ))
      assertEquals(projection.world.regions.map(_.sites.size), Vector(2, 3, 3))
    }
  }

  test("visible events do not determine authoritative next sequence") {
    LocalDebugSetupClient.demo().load().map { loaded =>
      val projection = loaded.toOption.get
      val filtered = projection.copy(visibleEvents = Vector.empty)
      assertEquals(filtered.visibleEvents.size, 0)
      assertEquals(filtered.nextSequence, 1L)
    }
  }

  test("accepted update returns newly accepted events and next sequence") {
    val client = LocalDebugSetupClient.demo()
    client.load().flatMap { loaded =>
      val projection = loaded.toOption.get
      client
        .submit(
          projection.nextSequence,
          SetupClientCommand.PlacePawn(
            projection.world.regions.head.sites.head.id
          )
        )
        .map { result =>
          val accepted = result.toOption.get
          assertEquals(accepted.acceptedEvents.size, 1)
          assertEquals(accepted.projection.nextSequence, 2L)
        }
    }
  }

  test("conflict can refresh to the authoritative newer projection") {
    val client = LocalDebugSetupClient.demo()
    client.load().flatMap { loaded =>
      val stale = loaded.toOption.get
      val site = stale.world.regions.head.sites.head.id
      client
        .submit(stale.nextSequence, SetupClientCommand.PlacePawn(site))
        .flatMap { accepted =>
          assert(accepted.isRight)
          client
            .submit(stale.nextSequence, SetupClientCommand.PlacePawn(site))
            .flatMap { conflict =>
              assertEquals(
                conflict,
                Left(SetupClientFailure.ExpectedPositionConflict(1L, 2L))
              )
              client.refresh().map { refreshed =>
                val current = refreshed.toOption.get
                assertEquals(current.nextSequence, 2L)
                assertEquals(
                  current.state,
                  accepted.toOption.get.projection.state
                )
              }
            }
        }
    }
  }

  test("restart creates a stream and reconstructs designated initial state") {
    val client = LocalDebugSetupClient.demo()
    client.load().flatMap { loaded =>
      val designated = loaded.toOption.get
      val site = designated.world.regions.head.sites.head.id
      client
        .submit(
          designated.nextSequence,
          SetupClientCommand.PlacePawn(site)
        )
        .flatMap { advanced =>
          val advancedProjection = advanced.toOption.get.projection
          client.restartDebug().map { result =>
            val restarted = result.toOption.get
            assertEquals(restarted.retiredStream, designated.streamId)
            assertNotEquals(restarted.newStream, restarted.retiredStream)
            assertEquals(restarted.projection.nextSequence, 1L)
            assertEquals(restarted.projection.state, designated.state)
            assertEquals(
              client.retiredStreams.head._2,
              advancedProjection.visibleEvents
            )
          }
        }
    }
  }

  test("final placement exposes replay-derived completed projection") {
    val client = LocalDebugSetupClient.demo()
    client.load().flatMap { loaded =>
      val sites =
        loaded.toOption.get.world.regions.flatMap(_.sites).take(3).map(_.id)
      sites
        .foldLeft(Future.successful(())) { (previous, site) =>
          previous.flatMap { _ =>
            client.refresh().flatMap { refreshed =>
              val projection = refreshed.toOption.get
              client
                .submit(
                  projection.nextSequence,
                  SetupClientCommand.PlacePawn(site)
                )
                .map(result => assert(result.isRight))
            }
          }
        }
        .flatMap(_ => client.refresh())
        .map { refreshed =>
          val projection = refreshed.toOption.get
          assert(projection.state.isInstanceOf[SetupState.Completed])
          assertEquals(projection.activePlayer, None)
          assertEquals(projection.nextSequence, 5L)
        }
    }
  }
}

class SetupDisplayProjectionSuite extends FunSuite {
  test("world projection preserves title, order, site counts, and labels") {
    LocalDebugSetupClient.demo().load().map { loaded =>
      val projection = loaded.toOption.get
      assertEquals(projection.world.title, "The World")
      assertEquals(projection.world.regions.map(_.name), Vector(
        "Cradle",
        "Provinces",
        "Hinterland"
      ))
      assertEquals(projection.world.regions.map(_.sites.size), Vector(2, 3, 3))
      assert(projection.world.regions.flatMap(_.sites).forall(_.label.nonEmpty))
    }
  }

  test("missing site definition is a typed projection failure") {
    val missing = SiteId("missing-site")
    assertEquals(
      LocalDebugSetupClient.buildWorld(Vector(missing), Vector.empty),
      Left(SetupClientFailure.MissingSiteDefinition(missing))
    )
  }

  test("supported player colors are explicit and unknown players are neutral") {
    LocalDebugSetupClient.demo().load().map { loaded =>
      val projection = loaded.toOption.get
      assertEquals(
        projection.players.map(_.color),
        Vector(
          PlayerColorToken.Purple,
          PlayerColorToken.Blue,
          PlayerColorToken.Red
        )
      )
      assertEquals(
        SetupViewModel.player(projection, PlayerId("unknown")).color,
        PlayerColorToken.Neutral
      )
    }
  }
}
