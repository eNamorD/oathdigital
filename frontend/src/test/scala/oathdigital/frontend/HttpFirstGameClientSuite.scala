package oathdigital.frontend

import munit.FunSuite
import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class HttpFirstGameClientSuite extends FunSuite {
  private val bootstrap = FirstGameBootstrap(
    Vector(
      BootstrapPlayer("red-exile", "red-lineage", "red"),
      BootstrapPlayer("blue-exile", "blue-lineage", "blue"),
      BootstrapPlayer("yellow-exile", "yellow-lineage", "yellow")
    ),
    "red-exile"
  )

  test("bootstrap uses same-origin route and server nextSequence") {
    val transport = new StubTransport(Vector(
      Right(TransportResponse(200, projectionJson(sequence = 1)))
    ))
    val client = new HttpFirstGameClient(transport)

    client.bootstrap("new game", "red-exile", bootstrap).map { result =>
      assertEquals(result.toOption.get.nextSequence, 1L)
      assertEquals(
        transport.requests.head._2,
        "/api/dev/first-games/new%20game/bootstrap?playerId=red-exile"
      )
      assert(transport.requests.head._3.exists(
        _.contains("\"expectedNextSequence\":0")
      ))
    }
  }

  test("pawn and adviser commands preserve explicit sequence and opaque IDs") {
    val opaqueSite = "site:ABC-17/printed"
    val opaqueAdviser = "denizen:0612"
    val transport = new StubTransport(Vector(
      Right(TransportResponse(200, projectionJson(
        sequence = 2,
        phase = "awaiting-adviser",
        siteId = opaqueSite,
        adviserId = opaqueAdviser
      ))),
      Right(TransportResponse(200, projectionJson(
        sequence = 3,
        phase = "awaiting-pawn",
        siteId = opaqueSite,
        choices = false
      )))
    ))
    val client = new HttpFirstGameClient(transport)

    client
      .submit(
        "game-1",
        "red-exile",
        1L,
        FirstGameCommand.PlacePawn("red-exile", opaqueSite)
      )
      .flatMap { pawn =>
        val adviser = pawn.toOption.get.privateAdviserChoices.head
        assertEquals(adviser.adviserId, opaqueAdviser)
        client.submit(
          "game-1",
          "red-exile",
          pawn.toOption.get.nextSequence,
          FirstGameCommand.ChooseAdviser("red-exile", adviser.adviserId)
        )
      }
      .map { _ =>
        assert(transport.requests.head._3.exists(_.contains(opaqueSite)))
        assert(transport.requests(1)._3.exists(_.contains(opaqueAdviser)))
        assert(transport.requests(1)._3.exists(
          _.contains("\"expectedNextSequence\":2")
        ))
      }
  }

  test("Wake commands and action-selection projection are deterministic") {
    val wealth = FirstGameJson.encodeCommand(
      8L,
      FirstGameCommand.TakeWealth("red-exile", "favor")
    )
    val end = FirstGameJson.encodeCommand(
      9L,
      FirstGameCommand.EndWake("red-exile")
    )
    assert(wealth.contains("\"type\":\"takeWealth\""))
    assert(wealth.contains("\"resource\":\"favor\""))
    assert(end.contains("\"type\":\"endWake\""))

    val json = projectionJson(
      sequence = 10,
      phase = "act-action-selection",
      ready = true,
      completed = true,
      choices = false
    ).replace(
      "\"privateAdviserChoices\":[]",
      "\"privateAdviserChoices\":[]," +
        "\"activePlayerResources\":{" +
        "\"favor\":2,\"faceUpSecrets\":1," +
        "\"faceDownSecrets\":0,\"supply\":7}," +
        "\"currentSiteResources\":{" +
        "\"siteId\":\"site:001\",\"favor\":0,\"secrets\":1}," +
        "\"actionSelectionOpen\":true," +
        "\"actionFamilies\":[\"Search\",\"Travel\"]"
    )
    val projection = FirstGameJson.decodeProjection(json).toOption.get
    assert(projection.actionSelectionOpen)
    assertEquals(projection.actionFamilies, Vector("Search", "Travel"))
    assertEquals(projection.activePlayerResources.map(_.supply), Some(7))
  }

  test("site detail decoder preserves populated and empty site projections") {
    val projection = FirstGameJson.decodeProjection(
      projectionJson(sequence = 2)
    ).toOption.get
    val populated = projection.world.head.sites.head
    val empty = projection.world.head.sites(1)

    assertEquals(populated.looseFavor, 2)
    assertEquals(populated.looseSecrets, 1)
    assertEquals(populated.denizenCapacity, 3)
    assertEquals(populated.relicCapacity, 2)
    assertEquals(
      populated.denizens,
      Vector(
        FirstGameSiteCard("denizen:z", "Zed"),
        FirstGameSiteCard("denizen:a", "Able")
      )
    )
    assertEquals(populated.relics, FirstGameSiteRelics(2))
    assertEquals(empty.denizens, Vector.empty)
    assertEquals(empty.relics.facedownCount, 0)
  }

  test("409 is surfaced and caller refreshes without command retry") {
    val transport = new StubTransport(Vector(
      Right(TransportResponse(
        409,
        """{"error":"stale-client-position","message":"expected 1 actual 2"}"""
      )),
      Right(TransportResponse(200, projectionJson(sequence = 2)))
    ))
    val client = new HttpFirstGameClient(transport)

    client
      .submit(
        "game-1",
        "red-exile",
        1L,
        FirstGameCommand.PlacePawn("red-exile", "site:001")
      )
      .flatMap { conflict =>
        assert(conflict.left.toOption.exists(
          _.isInstanceOf[FirstGameClientFailure.StalePosition]
        ))
        client.load("game-1", "red-exile")
      }
      .map { refreshed =>
        assertEquals(refreshed.toOption.get.nextSequence, 2L)
        assertEquals(
          transport.requests.map(_._1).toVector,
          Vector("POST", "GET")
        )
      }
  }

  test("existing game reload uses selected identity without creating history") {
    val transport = new StubTransport(Vector(
      Right(TransportResponse(200, projectionJson(sequence = 6)))
    ))
    val client = new HttpFirstGameClient(transport)

    client.load("persisted-game", "blue-exile").map { loaded =>
      assertEquals(loaded.toOption.get.nextSequence, 6L)
      assertEquals(transport.requests.toVector, Vector((
        "GET",
        "/api/dev/first-games/persisted-game?playerId=blue-exile",
        None
      )))
    }
  }

  test("transient disconnect reconnects by GET at authoritative sequence") {
    val transport = new StubTransport(Vector(
      Left(FirstGameClientFailure.NetworkFailure("offline")),
      Right(TransportResponse(200, projectionJson(sequence = 9)))
    ))
    val client = new HttpFirstGameClient(transport)
    val coordinator = new ServerSessionCoordinator("game-1", "red-exile")
    val initial = coordinator.switchSession("game-1", "red-exile")

    client.load("game-1", "red-exile").flatMap {
      case Left(error) =>
        assert(coordinator.recordFailure(initial, error))
        assert(coordinator.connectionState.isInstanceOf[
          ServerConnectionState.Disconnected
        ])
        val reconnect = coordinator.reconnect()
        client.load(reconnect.gameId, reconnect.playerId).map {
          case Right(authoritative) =>
            assertEquals(authoritative.nextSequence, 9L)
            assert(coordinator.route(reconnect, authoritative, None).nonEmpty)
            assertEquals(
              coordinator.connectionState,
              ServerConnectionState.Connected
            )
          case Left(failure) => fail(failure.message)
        }
      case Right(_) => fail("expected initial disconnect")
    }.map { _ =>
      assertEquals(transport.requests.map(_._1).toVector, Vector("GET", "GET"))
      assert(!transport.requests.exists(_._1 == "POST"))
    }
  }

  test("reconnect generation rejects late load and command callbacks") {
    val coordinator = new ServerSessionCoordinator("game-1", "red-exile")
    val oldLoad = coordinator.switchSession("game-1", "red-exile")
    val offline = FirstGameClientFailure.RequestTimedOut("GET", "/api", 10000)
    assert(coordinator.recordFailure(oldLoad, offline))

    val reconnect = coordinator.reconnect()
    assert(!coordinator.accepts(oldLoad))
    assert(!coordinator.recordFailure(
      oldLoad,
      FirstGameClientFailure.NetworkFailure("late command failure")
    ))
    assertEquals(coordinator.route(
      oldLoad,
      projection(activePlayer = "blue-exile"),
      None
    ), None)
    assert(coordinator.accepts(reconnect))
  }

  test("only transport failures produce disconnected state") {
    val transient = Vector[FirstGameClientFailure](
      FirstGameClientFailure.NetworkFailure("offline"),
      FirstGameClientFailure.RequestTimedOut("GET", "/api", 10000),
      FirstGameClientFailure.RequestAborted("GET", "/api")
    )
    transient.foreach(error => assert(FirstGameClientFailure.isTransient(error)))
    assert(!FirstGameClientFailure.isTransient(
      FirstGameClientFailure.StalePosition("conflict")
    ))
    assert(!FirstGameClientFailure.isTransient(
      FirstGameClientFailure.DecodeFailure("$", "bad projection")
    ))
  }

  test("malformed projection is a typed decode failure") {
    val transport = new StubTransport(Vector(
      Right(TransportResponse(200, """{"gameId":"game-1"}"""))
    ))

    new HttpFirstGameClient(transport)
      .load("game-1", "red-exile")
      .map(result =>
        assert(result.left.toOption.exists(
          _.isInstanceOf[FirstGameClientFailure.DecodeFailure]
        )))
  }

  test("null, scalar, and malformed nested projections are decode failures") {
    val malformed = Vector(
      "null",
      "7",
      "[]",
      projectionJson(sequence = 1).replace(
        "\"players\":[",
        "\"players\":[null,"
      ),
      projectionJson(sequence = 1).replace(
        "\"sites\":[{",
        "\"sites\":[7,{"
      )
    )

    malformed.foreach { json =>
      val result = FirstGameJson.decodeProjection(json)
      assert(result.left.toOption.exists(
        _.isInstanceOf[FirstGameClientFailure.DecodeFailure]
      ), json)
    }
  }

  test("nextSequence is bounded to the largest JSON-safe integer") {
    val maximum = FirstGameJson.decodeProjection(
      projectionJson(sequence = 1).replace(
        "\"nextSequence\":1",
        "\"nextSequence\":9007199254740991"
      )
    )
    assertEquals(maximum.toOption.get.nextSequence, 9007199254740991L)

    val unsafe = FirstGameJson.decodeProjection(
      projectionJson(sequence = 1).replace(
        "\"nextSequence\":1",
        "\"nextSequence\":9007199254740992"
      )
    )
    assert(unsafe.left.toOption.exists(
      _.isInstanceOf[FirstGameClientFailure.DecodeFailure]
    ))
  }

  test("mode selection is explicit and independent of serving port") {
    assertEquals(FrontendMode.fromSearch(""), FrontendMode.Server)
    assertEquals(
      FrontendMode.fromSearch("?gameId=manual-1"),
      FrontendMode.Server
    )
    assertEquals(
      FrontendMode.fromSearch("?mode=server&gameId=manual-1"),
      FrontendMode.Server
    )
    assertEquals(
      FrontendMode.fromSearch("?mode=local"),
      FrontendMode.LocalDebug
    )
  }

  test("stale refresh routes through active-player selection with notice") {
    val coordinator = new ServerSessionCoordinator("game-1", "red-exile")
    val request = coordinator.switchSession("game-1", "red-exile")
    val stale = FirstGameClientFailure.StalePosition("position changed")
    val refreshed = projection(activePlayer = "blue-exile")

    coordinator.route(request, refreshed, Some(stale)) match {
      case Some(ProjectionRoute.ReloadForActivePlayer(
            value,
            nextRequest,
            notice
          )) =>
        assertEquals(value.activeParticipantId, Some("blue-exile"))
        assertEquals(nextRequest.playerId, "blue-exile")
        assertEquals(notice, Some(stale))
      case other => fail(s"unexpected route: $other")
    }
  }

  test("late callbacks from an old game or player selection are discarded") {
    val coordinator = new ServerSessionCoordinator("game-a", "red-exile")
    val oldGame = coordinator.switchSession("game-a", "red-exile")
    val newGame = coordinator.switchSession("game-b", "red-exile")

    assert(!coordinator.accepts(oldGame))
    assertEquals(coordinator.route(
      oldGame,
      projection(gameId = "game-a"),
      None
    ), None)
    assert(coordinator.accepts(newGame))

    val blueView = coordinator.switchSession("game-b", "blue-exile")
    assert(!coordinator.accepts(newGame))
    assert(coordinator.accepts(blueView))
  }

  test("transport timeout and abort are typed failures") {
    val timeout = FirstGameClientFailure.RequestTimedOut("GET", "/api", 10000)
    val aborted = FirstGameClientFailure.RequestAborted("POST", "/api")
    assert(timeout.message.contains("10000 ms"))
    assert(aborted.message.contains("aborted"))
  }

  test("new persisted test uses a distinct bootstrap route, never mutation") {
    val transport = new StubTransport(Vector(
      Right(TransportResponse(200, projectionJson(sequence = 1))),
      Right(TransportResponse(200, projectionJson(sequence = 1)))
    ))
    val client = new HttpFirstGameClient(transport)

    client.bootstrap("game-a", "red-exile", bootstrap)
      .flatMap(_ => client.bootstrap("game-b", "red-exile", bootstrap))
      .map { _ =>
        assertEquals(
          transport.requests.map(_._2).toVector,
          Vector(
            "/api/dev/first-games/game-a/bootstrap?playerId=red-exile",
            "/api/dev/first-games/game-b/bootstrap?playerId=red-exile"
          )
        )
        assert(!transport.requests.exists(_._1 == "DELETE"))
      }
  }

  test("projection contains scoped choices, no hidden plan, and Ready state") {
    val json = projectionJson(
      sequence = 7,
      phase = "ready",
      ready = true,
      completed = true,
      choices = false
    )
    assert(!json.contains("denizenOrder"))
    assert(!json.contains("worldDeckOrder"))
    assert(!json.contains("relicOrder"))

    FirstGameJson.decodeProjection(json) match {
      case Right(projection) =>
        assert(projection.ready)
        assert(projection.completed)
        assertEquals(projection.phase, "ready")
        assertEquals(projection.privateAdviserChoices, Vector.empty)
      case Left(failure) => fail(failure.message)
    }
  }

  private def projectionJson(
      sequence: Long,
      phase: String = "awaiting-pawn",
      siteId: String = "site:001",
      adviserId: String = "denizen:0612",
      ready: Boolean = false,
      completed: Boolean = false,
      choices: Boolean = true
  ): String = {
    val privateChoices =
      if (choices)
        s"""[{"adviserId":"$adviserId","label":"Printed Adviser"}]"""
      else "[]"
    s"""{
       |"gameId":"game-1",
       |"nextSequence":$sequence,
       |"phase":"$phase",
       |"activeParticipantId":"red-exile",
       |"players":[
       |{"playerId":"red-exile","displayName":"Red Exile","role":"exile","colorToken":"red"},
       |{"playerId":"blue-exile","displayName":"Blue Exile","role":"exile","colorToken":"blue"},
       |{"playerId":"yellow-exile","displayName":"Yellow Exile","role":"exile","colorToken":"yellow"}
       |],
       |"world":[
       |{"regionId":"cradle","sites":[${siteJson(siteId, "Printed Site", populated = true)},${siteJson("site:002", "Second")}]},
       |{"regionId":"provinces","sites":[${siteJson("site:003", "Third")},${siteJson("site:004", "Fourth")},${siteJson("site:005", "Fifth")}]},
       |{"regionId":"hinterland","sites":[${siteJson("site:006", "Sixth")},${siteJson("site:007", "Seventh")},${siteJson("site:008", "Eighth")}]}
       |],
       |"pawnLocations":[],
       |"legalControls":["placePawn","chooseAdviser"],
       |"ready":$ready,
       |"completed":$completed,
       |"privateAdviserChoices":$privateChoices
       |}""".stripMargin
  }

  private def siteJson(
      siteId: String,
      label: String,
      populated: Boolean = false
  ): String =
    if (populated)
      s"""{"siteId":"$siteId","label":"$label","looseFavor":2,"looseSecrets":1,"denizenCapacity":3,"relicCapacity":2,"denizens":[{"denizenId":"denizen:z","label":"Zed"},{"denizenId":"denizen:a","label":"Able"}],"relics":{"facedownCount":2}}"""
    else
      s"""{"siteId":"$siteId","label":"$label","looseFavor":0,"looseSecrets":0,"denizenCapacity":0,"relicCapacity":0,"denizens":[],"relics":{"facedownCount":0}}"""

  private def projection(
      gameId: String = "game-1",
      activePlayer: String = "red-exile"
  ): FirstGameProjection =
    FirstGameJson.decodeProjection(
      projectionJson(sequence = 2).replace(
        "\"gameId\":\"game-1\"",
        s"\"gameId\":\"$gameId\""
      ).replace(
        "\"activeParticipantId\":\"red-exile\"",
        s"\"activeParticipantId\":\"$activePlayer\""
      )
    ).toOption.get

  private final class StubTransport(
      responses: Vector[Either[FirstGameClientFailure, TransportResponse]]
  ) extends JsonTransport {
    private val remaining = mutable.Queue(responses: _*)
    val requests =
      mutable.ArrayBuffer.empty[(String, String, Option[String])]

    override def request(
        method: String,
        url: String,
        body: Option[String]
    ) = {
      requests += ((method, url, body))
      Future.successful(remaining.dequeue())
    }
  }
}
