package oathdigital.server

import oathdigital.application.{
  GameApplicationService,
  GameCommand,
  GameProjector,
  InMemoryEventStreamRepository
}
import oathdigital.gameplay.setup.SetupProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.protocol.{ActorlessCommandCodec, ActorlessCommandRequest,
  DecisionAnswerWire, GameIntent}
import oathdigital.testkit.{CatalogNames, HttpTestServer}
import oathdigital.testkit.HttpTestServer.cacheControl
import oathdigital.testkit.Table.p2
import oathdigital.testkit.Unwrap.accepted

class GameRoutesSuite extends munit.FunSuite:
  /** The development routes serving "route-game", begun with p2 first and
    * parked on p2's Setup pawn placement; `afterBegin` is its next
    * sequence. */
  private final case class BegunRoutes(server: HttpTestServer,
      afterBegin: Long)

  private val begunRoutes = FunFixture[BegunRoutes](
    setup = _ =>
      val service = new GameApplicationService(catalog,
        new InMemoryEventStreamRepository)
      val begun = accepted(service.handle("route-game", 0L,
        GameCommand.Begin(chronicle, orders.copy(firstPlayer = p2))))
      // GameStarted plus the WalkerParked fact from Setup's immediate first
      // park (2026-09-21 Chronicle design, slice 2): two records for one
      // Begin command.
      assertEquals(begun.nextSequence, 2L)
      val gateway = new GameServerGateway(service, new GameProjector(catalog))
      BegunRoutes(
        HttpTestServer.start("first-game-route-test")(blocking =>
          DevelopmentRoutes.route(gateway, blocking)),
        begun.nextSequence),
    teardown = _.server.close())

  private val p2Commands =
    "/api/dev/first-games/route-game/commands?playerId=p2"
  private val ancientCity = CatalogNames.site("Ancient City").value

  begunRoutes.test("the development shell is served uncached and /health " +
      "is not a development route"):
    case BegunRoutes(server, _) =>
      val index = server.get("/")
      assertEquals(index.statusCode(), 200)
      assertEquals(cacheControl(index),
        Some("no-store, no-cache, must-revalidate, max-age=0"))
      // The pinned, version-free fastLinkJS output directory (build.sbt).
      assert(index.body().contains(
        "./target/fastopt/main.js?dev-cache=no-store-v1"))
      val stylesheet = server.get("/styles.css")
      assertEquals(stylesheet.statusCode(), 200)
      assertEquals(cacheControl(stylesheet),
        Some("no-store, no-cache, must-revalidate, max-age=0"))

      val health = server.get("/health")
      assertEquals(health.statusCode(), 404)
      assertEquals(cacheControl(health), None)

  begunRoutes.test("the events history pages from the newest event and " +
      "refuses a limit above 100"):
    case BegunRoutes(server, afterBegin) =>
      val history = server.get("/api/dev/first-games/route-game/events?limit=1")
      assertEquals(history.statusCode(), 200)
      assertEquals(ujson.read(history.body())("events").arr.size, 1)
      assertEquals(ujson.read(history.body())("events")(0)("sequence").num.toLong,
        afterBegin - 1)
      assert(ujson.read(history.body())("warning").str.contains("hidden"))
      // GameRoutes refuses a limit above 100.
      assertEquals(server.get(
        "/api/dev/first-games/route-game/events?limit=101").statusCode(), 400)

  begunRoutes.test("a malformed command is refused and a stale one conflicts"):
    case BegunRoutes(server, _) =>
      val malformed = server.post(p2Commands,
        """{"expectedNextSequence":1,"intent":{"type":"resolveWalker"}}""")
      assertEquals(malformed.statusCode(), 400)

      val pawnDecision = SetupProcedure.pawnDecisionId(p2)
      val stale = server.post(p2Commands,
        s"""{"expectedNextSequence":0,"intent":{"type":"resolveWalker",
           |"decisionId":"$pawnDecision",
           |"payload":{"kind":"choose-one","optionKind":"site",
           |"optionId":"$ancientCity"}}}""".stripMargin)
      assertEquals(stale.statusCode(), 409)
      assertEquals(
        ujson.read(stale.body())("error").str,
        "stale-client-position"
      )

  begunRoutes.test("p2 walks Setup through the development API while p3 " +
      "waits"):
    case BegunRoutes(server, afterBegin) =>
      val pawnDecision = SetupProcedure.pawnDecisionId(p2)
      val placed = server.post(p2Commands,
        ActorlessCommandCodec.encode(ActorlessCommandRequest(
          afterBegin,
          GameIntent.ResolveWalker(pawnDecision,
            DecisionAnswerWire.ChooseOneWire("site", ancientCity))
        ))
      )
      assertEquals(placed.statusCode(), 200)
      assertEquals(ujson.read(placed.body())("phase").str,
        "setup-walker-decision")
      val afterPlaced = ujson.read(placed.body())("nextSequence").num.toLong
      val placedHistory =
        server.get("/api/dev/first-games/route-game/events?limit=2")
      assertEquals(placedHistory.statusCode(), 200)
      assertEquals(ujson.read(placedHistory.body())("events").arr.size, 2)
      assertEquals(
        ujson.read(placedHistory.body())("events")(1)("sequence").num.toLong,
        afterPlaced - 1
      )

      val adviserDecision = SetupProcedure.adviserDecisionId(p2)
      val handOptions = ujson.read(placed.body())("walkerDecision")("query")(
        "options").arr.map(_("id").str).toVector
      // p2 is dealt Wizard's Conclave, Errand Boy and Nature Worship and keeps
      // Wizard's Conclave, which is not site-only, so Reveal Cards follows.
      val adviser = CatalogNames.denizen("Wizard's Conclave").value
      val rejectedAdvisers = Vector("Errand Boy", "Nature Worship")
        .map(CatalogNames.denizen(_).value)
      assertEquals(handOptions, adviser +: rejectedAdvisers)
      val chosen = server.post(p2Commands,
        ujson.write(ujson.Obj(
          "expectedNextSequence" -> ujson.Num(afterPlaced.toDouble),
          "intent" -> ujson.Obj("type" -> "resolveWalker",
            "decisionId" -> adviserDecision,
            "payload" -> ujson.Obj("kind" -> "partition",
              "placements" -> ujson.Arr.from(
                ujson.Obj("optionKind" -> "denizen", "optionId" -> adviser,
                  "sectionKey" -> "keep") +: rejectedAdvisers.map(id =>
                  ujson.Obj("optionKind" -> "denizen", "optionId" -> id,
                    "sectionKey" -> "discard")))))))
      )
      assertEquals(chosen.statusCode(), 200)

      val reloaded = server.get("/api/dev/first-games/route-game?playerId=p3")
      assertEquals(reloaded.statusCode(), 200)
      assertEquals(
        ujson.read(reloaded.body())("nextSequence").num.toLong,
        ujson.read(chosen.body())("nextSequence").num.toLong
      )
      // p2 is asked Reveal Cards next, so p3 waits on it.
      assertEquals(ujson.read(reloaded.body())("phase").str,
        "setup-walker-waiting")
