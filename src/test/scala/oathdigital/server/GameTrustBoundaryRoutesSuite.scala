package oathdigital.server

import oathdigital.application._
import oathdigital.catalog.ExecutableCatalog
import oathdigital.testkit.HttpTestServer

class GameTrustBoundaryRoutesSuite extends munit.FunSuite:
  private val catalog = ExecutableCatalog(
    Vector.empty,
    Vector.empty,
    Vector.empty,
    Vector.empty,
    Vector.empty
  )

  test("route validates identity actor and internal-error boundaries"):
    val secret = "internal-database-password-and-path"
    // Stub of EventStreamRepository: every call fails with a StorageFailure
    // carrying the secret.
    val repository = new EventStreamRepository:
      override def load(gameId: String): Either[RepositoryFailure, Option[StoredEventStream]] =
        Left(RepositoryFailure.StorageFailure(secret))
      override def append(
          gameId: String,
          expected: ExpectedStream,
          records: Vector[String]
      ): Either[RepositoryFailure, RepositoryAppendResult] = Left(RepositoryFailure.StorageFailure(secret))
    val gateway = new GameServerGateway(
      new GameApplicationService(catalog, repository),
      new GameProjector(catalog)
    )
    val server = HttpTestServer.start("trust-boundary-route-test")(blocking =>
      DevelopmentRoutes.route(gateway, blocking, serveFrontend = false))

    try
      Vector(
        "/api/dev/first-games/%20?playerId=p1",
        "/api/dev/first-games/game?playerId=bad!",
        "/api/dev/first-games/game?playerId=" +
          "x" * (DevelopmentTrustBoundary.MaximumIdentifierLength + 1),
        "/api/dev/first-games/game?playerId="
      ).foreach(path => assertEquals(server.get(path).statusCode(), 400))

      val begin = server.post(
        "/api/dev/first-games/game/commands?playerId=p1",
        """{"expectedNextSequence":0,"intent":{"type":"begin",
          |"plan":{"relicOrder":["hidden"]}}}""".stripMargin
      )
      assertEquals(begin.statusCode(), 400)
      assert(ujson.read(begin.body())("message").str.contains("unknown"))

      val mismatch = server.post(
        "/api/dev/first-games/game/commands?playerId=p1",
        """{"expectedNextSequence":0,"intent":{"type":"placePawn",
          |"playerId":"p2","siteId":"S1"}}""".stripMargin
      )
      assertEquals(mismatch.statusCode(), 400)
      assertEquals(
        ujson.read(mismatch.body())("error").str,
        "malformed-request"
      )
      val travelMismatch = server.post(
        "/api/dev/first-games/game/commands?playerId=p1",
        """{"expectedNextSequence":0,"intent":{"type":"travel",
          |"playerId":"p2","destinationSiteId":"S2"}}""".stripMargin
      )
      assertEquals(travelMismatch.statusCode(), 400)
      assertEquals(ujson.read(travelMismatch.body())("error").str,
        "malformed-request")

      val internal = server.get("/api/dev/first-games/game?playerId=p1")
      assertEquals(internal.statusCode(), 500)
      assertEquals(ujson.read(internal.body())("error").str, "internal-error")
      assert(!internal.body().contains(secret))
    finally server.close()

