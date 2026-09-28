package oathdigital.frontend


import munit.FunSuite
import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import oathdigital.protocol.{ActorlessCommandCodec, ActorlessCommandRequest,
  DecisionAnswerWire, GameIntent, MajorActionPreviewRequest}

class HttpGameClientSuite extends FunSuite:
  test("trusted client uses canonical cookie APIs and actorless bodies"):
    val transport = new StubTransport(Vector(
      Right(TransportResponse(200, projectionJson(7))),
      Right(TransportResponse(200, """{"nextSequence":7,"action":"trade","modifiers":[],"ignoredRules":[],"targets":[]}""")),
      Right(TransportResponse(200, projectionJson(8)))))
    val client = new TrustedHttpGameClient(transport)
    client.load("game /?", "seat-must-not-travel").flatMap { loaded =>
      assertEquals(loaded.toOption.get.nextSequence, 7L)
      client.preview("game /?", "seat-must-not-travel",
        MajorActionPreviewRequest(7, "trade", Map("resource" -> "favor")))
    }.flatMap { preview =>
      assertEquals(preview.toOption.get.nextSequence, 7L)
      client.submit("game /?", "seat-must-not-travel", 7, GameIntent.RevealOwnedRelic("relic:a"))
    }.map { submitted =>
      assertEquals(submitted.toOption.get.nextSequence, 8L)
      assertEquals(transport.requests.map(r => r._1 -> r._2).toVector, Vector(
        "GET" -> "/games/game%20%2F%3F/api",
        "POST" -> "/games/game%20%2F%3F/api/preview",
        "POST" -> "/games/game%20%2F%3F/api/commands"))
      assertEquals(transport.requests.head._3, None)
      assertEquals(ActorlessCommandCodec.decode(transport.requests.last._3.get),
        Right(ActorlessCommandRequest(7, GameIntent.RevealOwnedRelic("relic:a"))))
      assert(!transport.requests.toString.contains("seat-must-not-travel"))
    }

  test("a 409 is a stale position, and the client does not retry the command"):
    val transport = new StubTransport(Vector(
      Right(TransportResponse(409, """{"error":"stale-client-position","message":"position changed"}"""))))
    val client = new TrustedHttpGameClient(transport)
    client.submit("game-1", "red-exile", 7, GameIntent.RevealOwnedRelic("relic:a"))
      .map { result =>
        assert(result.left.toOption.exists(
          _.isInstanceOf[GameClientFailure.StalePosition]), result.toString)
        assertEquals(transport.requests.map(_._1).toVector, Vector("POST"))
      }
  test("pawn and adviser commands preserve explicit sequence and opaque IDs"):
    // Setup's pawn placement and adviser choice are generic walker decisions
    // now (2026-09-21 Chronicle design, slice 2): the client answers them
    // through `ResolveWalker`, the same as Recover's or Forge's, rather
    // than through a bespoke `place-pawn` board-target action.
    val opaqueSite = "site:ancient-city"
    val opaqueAdviser = "denizen:0612"
    val pawnCommand = oathdigital.protocol.GameIntent.ResolveWalker("setup.pawn-placement.red-exile",
      DecisionAnswerWire.ChooseOneWire("site", opaqueSite))
    val adviserCommand = oathdigital.protocol.GameIntent.ResolveWalker("setup.adviser-choice.red-exile",
      DecisionAnswerWire.ChooseOneWire("denizen", opaqueAdviser))
    val transport = new StubTransport(Vector(
      Right(TransportResponse(200, projectionJson(
        sequence = 2,
        phase = "setup-walker-decision",
        siteId = opaqueSite,
        adviserId = opaqueAdviser
      ))),
      Right(TransportResponse(200, projectionJson(
        sequence = 3,
        phase = "setup-walker-decision",
        siteId = opaqueSite,
        choices = false
      )))
    ))
    val client = new HttpGameClient(transport)

    client
      .submit(
        "game-1",
        "red-exile",
        1L,
        pawnCommand
      )
      .flatMap { pawn =>
        client.submit(
          "game-1",
          "red-exile",
          pawn.toOption.get.nextSequence,
          adviserCommand
        )
      }
      .map { _ =>
        val setupBody = transport.requests.head._3.get
        assertEquals(ujson.read(setupBody).obj.keySet,
          Set("expectedNextSequence", "intent"))
        assertEquals(ActorlessCommandCodec.decode(setupBody),
          Right(ActorlessCommandRequest(1L, GameIntent.ResolveWalker(
            "setup.pawn-placement.red-exile",
            DecisionAnswerWire.ChooseOneWire("site", opaqueSite)))))
        assert(transport.requests(1)._3.exists(_.contains(opaqueAdviser)))
        assert(transport.requests(1)._3.exists(
          _.contains("\"expectedNextSequence\":2")
        ))
      }

  test("typed board-target actions decode sites and reject malformed refs"):
    val actions = """[{"actionKind":"travel","prompt":"Choose a destination","minimum":1,"maximum":1,"autoActivate":false,"explicitConfirm":false,"candidates":[{"target":{"kind":"site","siteId":"site:b"},"label":"Site B","details":["2 Supply"]},{"target":{"kind":"site","siteId":"site:c"},"label":"Site C","details":[]}]}]"""
    val json = projectionJson(sequence = 10, choices = false)
      .replace("\"boardTargetActions\":[]",
        s"\"boardTargetActions\":$actions")
    val decoded = GameJson.decodeProjection(json).toOption.get
      .boardTargetActions.head
    assertEquals(decoded.minimum -> decoded.maximum, 1 -> 1)
    assertEquals(decoded.candidates.map(_.target), Vector(
      BoardTargetRef.Site("site:b"), BoardTargetRef.Site("site:c")))
    assert(GameJson.decodeProjection(json.replace("\"kind\":\"site\"",
      "\"kind\":\"site-card\"")).isLeft)
    assert(GameJson.decodeProjection(json.replace("\"maximum\":1", "\"maximum\":2")).isLeft)
    assert(GameJson.decodeProjection(json.replace("\"autoActivate\":false,",
      "\"autoActivate\":false,\"requiredTargets\":[],")).isLeft)

  test("reconnect generation rejects late load and command callbacks"):
    val coordinator = new ServerSessionCoordinator("game-1", "red-exile")
    val oldLoad = coordinator.switchSession("game-1", "red-exile")
    val offline = GameClientFailure.RequestTimedOut("GET", "/api", 10000)
    assert(coordinator.recordFailure(oldLoad, offline))

    val reconnect = coordinator.reconnect()
    assert(!coordinator.accepts(oldLoad))
    assert(!coordinator.recordFailure(
      oldLoad,
      GameClientFailure.NetworkFailure("late command failure")
    ))
    assertEquals(coordinator.route(
      oldLoad,
      projection(activePlayer = "blue-exile"),
      None
    ), None)
    assert(coordinator.accepts(reconnect))

  test("only transport failures produce disconnected state"):
    val transient = Vector[GameClientFailure](
      GameClientFailure.NetworkFailure("offline"),
      GameClientFailure.RequestTimedOut("GET", "/api", 10000),
      GameClientFailure.RequestAborted("GET", "/api")
    )
    transient.foreach(error => assert(GameClientFailure.isTransient(error)))
    assert(!GameClientFailure.isTransient(
      GameClientFailure.StalePosition("conflict")
    ))
    assert(!GameClientFailure.isTransient(
      GameClientFailure.DecodeFailure("$", "bad projection")
    ))

  test("null, scalar, and malformed nested projections are decode failures"):
    val malformed = Vector(
      """{"gameId":"game-1"}""",
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
      val result = GameJson.decodeProjection(json)
      assert(result.left.toOption.exists(
        _.isInstanceOf[GameClientFailure.DecodeFailure]
      ), json)
    }

  test("nextSequence is bounded to the largest JSON-safe integer"):
    val maximum = GameJson.decodeProjection(
      projectionJson(sequence = 1).replace(
        "\"nextSequence\":1",
        "\"nextSequence\":9007199254740991"
      )
    )
    assertEquals(maximum.toOption.get.nextSequence, 9007199254740991L)

    val unsafe = GameJson.decodeProjection(
      projectionJson(sequence = 1).replace(
        "\"nextSequence\":1",
        "\"nextSequence\":9007199254740992"
      )
    )
    assert(unsafe.left.toOption.exists(
      _.isInstanceOf[GameClientFailure.DecodeFailure]
    ))

  test("stale refresh routes through active-player selection with notice"):
    val coordinator = new ServerSessionCoordinator("game-1", "red-exile")
    val request = coordinator.switchSession("game-1", "red-exile")
    val stale = GameClientFailure.StalePosition("position changed")
    val refreshed = projection(activePlayer = "blue-exile")

    coordinator.route(request, refreshed, Some(stale)) match
      case Some(ProjectionRoute.ReloadForActivePlayer(
            value,
            nextRequest,
            notice
          )) =>
        assertEquals(value.activeParticipantId, Some("blue-exile"))
        assertEquals(nextRequest.playerId, "blue-exile")
        assertEquals(notice, Some(stale))
      case other => fail(s"unexpected route: $other")

  test("late callbacks from an old game or player selection are discarded"):
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

  private def projectionJson(
      sequence: Long,
      phase: String = "act-action-selection",
      siteId: String = "site:001",
      adviserId: String = "denizen:0612",
      ready: Boolean = false,
      completed: Boolean = false,
      choices: Boolean = true
  ): String =
    val pendingDecision =
      if choices then
        s"""{"decisionId":"search-draw-0-red-exile","kind":"search","actorPlayerId":"red-exile","prompt":"Choose adviser","instructions":[],"cards":[${cardJson(adviserId, "denizen", "Printed Adviser")}],"keepMinimum":1,"keepMaximum":1,"orderingRequired":false,"resolutionsByCard":{"$adviserId":[{"kind":"search","orientation":null,"replacementRequired":false,"replacementTargets":[]}]}}"""
      else "null"
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
       |"legalControls":[],
       |"ready":$ready,
       |"completed":$completed,
       |"boardTargetActions":[],
       |"pendingCardDecision":$pendingDecision
       |}""".stripMargin

  private def cardJson(id: String, kind: String, name: String): String =
    s"""{"cardId":"$id","cardKind":"$kind","name":"$name","suit":null,"restrictions":null,"rulesText":null,"orientation":"face-up","hidden":false}"""

  private def siteJson(
      siteId: String,
      label: String,
      populated: Boolean = false
  ): String =
    if populated then
      s"""{"siteId":"$siteId","label":"$label","looseFavor":2,"looseSecrets":1,"denizenCapacity":3,"relicCapacity":2,"denizens":[{"denizenId":"denizen:z","label":"Zed"},{"denizenId":"denizen:a","label":"Able"}],"relics":{"facedownCount":2},"forces":{"forceKind":"exile","count":2,"rulerKind":"player","rulerPlayerId":"red-exile","label":"Red Warbands","colorToken":"red"}}"""
    else
      s"""{"siteId":"$siteId","label":"$label","looseFavor":0,"looseSecrets":0,"denizenCapacity":0,"relicCapacity":0,"denizens":[],"relics":{"facedownCount":0},"forces":null}"""

  private def projection(
      gameId: String = "game-1",
      activePlayer: String = "red-exile"
  ): GameProjection =
    GameJson.decodeProjection(
      projectionJson(sequence = 2).replace(
        "\"gameId\":\"game-1\"",
        s"\"gameId\":\"$gameId\""
      ).replace(
        "\"activeParticipantId\":\"red-exile\"",
        s"\"activeParticipantId\":\"$activePlayer\""
      )
    ).toOption.get

  private val logPage = """{"gameId":"g","after":3,"nextSequence":5,"entries":[{"sequence":3,"ordinal":0,"kind":"turn","depth":0,"spans":[{"kind":"player","text":"Red","id":"red"},{"kind":"text","text":"'s turn"}]}]}"""

  test("the trusted client reads the log after a cursor on the seat's path"):
    val transport = new StubTransport(Vector(Right(TransportResponse(200, logPage))))
    new TrustedHttpGameClient(transport).loadLog("my game", "ignored", 3L).map { result =>
      assertEquals(transport.requests.map(r => r._1 -> r._2).toVector,
        Vector("GET" -> "/games/my%20game/api/log/3"))
      assertEquals(result.map(_.entries.map(_.sequence)), Right(Vector(3L)))
    }

  test("the development client reads the log for the selected seat"):
    val transport = new StubTransport(Vector(Right(TransportResponse(200, logPage))))
    new HttpGameClient(transport).loadLog("g", "red exile", 0L).map { result =>
      assertEquals(transport.requests.map(_._2).toVector,
        Vector("/api/dev/first-games/g/log/0?playerId=red%20exile"))
      assert(result.isRight)
    }

  test("a log failure is a client failure, never a thrown decode"):
    val transport = new StubTransport(Vector(
      Right(TransportResponse(400, """{"error":"malformed-request","message":"bad"}""")),
      Right(TransportResponse(200, "{"))))
    val client = new TrustedHttpGameClient(transport)
    client.loadLog("g", "", 9L).flatMap { refused =>
      assertEquals(refused, Left(GameClientFailure.HttpFailure(400,
        "malformed-request", "bad")))
      client.loadLog("g", "", 0L)
    }.map(garbled => assert(garbled.isLeft))

  private final class StubTransport(
      responses: Vector[Either[GameClientFailure, TransportResponse]]
  ) extends JsonTransport:
    private val remaining = mutable.Queue(responses*)
    val requests =
      mutable.ArrayBuffer.empty[(String, String, Option[String])]

    override def request(
        method: String,
        url: String,
        body: Option[String]
    ) =
      requests += ((method, url, body))
      Future.successful(remaining.dequeue())
