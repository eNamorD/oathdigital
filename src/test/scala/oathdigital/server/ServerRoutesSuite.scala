package oathdigital.server

import java.net.URI
import java.nio.file.{Files, Paths}

import oathdigital.testkit.HttpTestServer
import oathdigital.testkit.Unwrap.accepted

class ServerRoutesSuite extends munit.FunSuite:
  /** The port every configuration here says the server listens on. The test
    * servers bind a free port instead, so it shows only in origins and in
    * seat links. */
  private val listenPort = 8080

  /** A loopback port that is not the listen port. */
  private val otherPort = 9090

  /** A two-player create-game request, Red and Blue. */
  private val createGameBody = """{"participants":[""" +
    """{"playerId":"Red","color":"red"},""" +
    """{"playerId":"Blue","color":"blue"}]}"""

  private def openRuntime(label: String): ServerRuntime =
    accepted(ServerRuntime.open(
      Files.createTempDirectory(label).resolve("database")))

  test("development routes serve health and refuse bad dev and seat " +
      "requests, and mount authenticated routes only with complete session " +
      "configuration"):
    val runtime = openRuntime("server-routes-")
    try
      val readiness = ServerReadiness.starting("test-version")
      val absent = HttpTestServer.start("server-routes-test")(blocking =>
        ServerRoutes.route(runtime, blocking, config(ServerMode.Development),
          readiness))
      try
        assertEquals(absent.get("/health/live").statusCode(), 200)
        assertEquals(absent.get("/health").statusCode(), 503)
        readiness.markReady()
        assertEquals(absent.get("/health/ready").statusCode(), 200)
        assertEquals(absent.get("/health").statusCode(), 200)
        assertEquals(
          absent.get("/api/authenticated/first-games/game").statusCode(), 404)
        // 400: the events history refuses a limit above 100.
        assertEquals(absent.get(
          "/api/dev/first-games/test-game/events?limit=101").statusCode(), 400)
        // 404: no seat has this code.
        assertEquals(absent.get("/s/AAAAAAAAAAAAAAAAAAAAAA").statusCode(), 404)
        // 403: the request carries no seat cookie.
        assertEquals(absent.get("/games/game/api").statusCode(), 403)
      finally absent.close()

      val configuration = AuthenticatedRouteMountConfiguration(
        "oath_session", "http://127.0.0.1")
      val mounted = HttpTestServer.start("server-routes-mounted-test")(
        blocking => ServerRoutes.route(
          runtime,
          blocking,
          config(
            ServerMode.Development,
            authenticatedRouteMount = Some(configuration)
          ),
          ServerReadiness.starting("test-version"),
          () => 0L
        ))
      try assertEquals(
        mounted.get("/api/authenticated/first-games/game").statusCode(), 401)
      finally mounted.close()
    finally runtime.close()

  test("trusted-alpha serves production frontend without development routes"):
    val runtime = openRuntime("trusted-alpha-routes-")
    try
      val server = HttpTestServer.start("trusted-alpha-routes-test")(blocking =>
        ServerRoutes.route(
          runtime,
          blocking,
          config(
            ServerMode.TrustedAlpha,
            authenticatedRouteMount = Some(AuthenticatedRouteMountConfiguration(
              "oath_session",
              "http://127.0.0.1"
            ))
          ),
          ServerReadiness.starting("test-version")
        ))
      try
        assertEquals(server.get("/").statusCode(), 200)
        assertEquals(server.get("/games/test-game").statusCode(), 403)
        assertEquals(server.get("/games/test-game/api").statusCode(), 403)
        assertEquals(server.get(
          "/api/dev/first-games/test-game?playerId=p1").statusCode(), 404)
        assertEquals(server.get(
          "/api/authenticated/first-games/test-game").statusCode(), 404)
      finally server.close()
    finally runtime.close()

  test("development mode creates games like trusted-alpha from its own " +
      "loopback origins only and opens them in the dev API"):
    val runtime = openRuntime("development-create-routes-")
    try
      val server = HttpTestServer.start("development-create-routes-test")(
        blocking => ServerRoutes.route(runtime, blocking,
          config(ServerMode.Development),
          ServerReadiness.starting("test-version")))
      def create(origin: String) = server.post("/games", createGameBody,
        origin = Some(origin), csrf = None)
      try
        assertEquals(
          create(s"http://example.com:$listenPort").statusCode(), 403)
        assertEquals(create(s"http://localhost:$otherPort").statusCode(), 403)
        assertEquals(create(s"http://127.0.0.1:$listenPort").statusCode(), 201)
        val created = create(s"http://localhost:$listenPort")
        assertEquals(created.statusCode(), 201)
        assert(created.body().contains(s"http://127.0.0.1:$listenPort/s/"))
        val gameId = ujson.read(created.body())("gameId").str
        val loaded = server.get(s"/api/dev/first-games/$gameId?playerId=Red")
        assertEquals(loaded.statusCode(), 200)
        assert(loaded.body().contains(s"\"gameId\":\"$gameId\""))
      finally server.close()
    finally runtime.close()

  test("trusted-alpha also accepts the host's own loopback origins on the " +
      "listen port, for game creation and for commands"):
    val runtime = openRuntime("trusted-alpha-loopback-origin-")
    try
      val server = HttpTestServer.start("trusted-alpha-loopback-origin-test")(
        blocking => ServerRoutes.route(runtime, blocking,
          config(ServerMode.TrustedAlpha,
            publicBaseUrl =
              Some(URI.create(s"http://203.0.113.7:$listenPort"))),
          ServerReadiness.starting("test-version")))
      def post(path: String, origin: String, body: String) =
        server.post(path, body, origin = Some(origin), csrf = None)
      def create(origin: String) = post("/games", origin, createGameBody)
      try
        assertEquals(
          create(s"http://203.0.113.7:$listenPort").statusCode(), 201)
        Seq(s"http://localhost:$listenPort", s"http://127.0.0.1:$listenPort",
            s"http://[::1]:$listenPort")
          .foreach { origin =>
            val created = create(origin)
            assertEquals(created.statusCode(), 201, origin)
            assert(created.body().contains(
              s"\"http://203.0.113.7:$listenPort/s/"), origin)
          }
        Seq(s"http://localhost:$otherPort", s"https://localhost:$listenPort",
            s"http://192.168.1.20:$listenPort",
            s"http://example.com:$listenPort")
          .foreach(origin =>
            assertEquals(create(origin).statusCode(), 403, origin))
        // Commands pass the same origin gate before the seat cookie is checked.
        val fromLoopback = post("/games/g/api/commands",
          s"http://localhost:$listenPort", "{}")
        assert(!fromLoopback.body().contains("csrf-validation-failed"),
          fromLoopback.body())
        val fromLan = post("/games/g/api/commands",
          s"http://192.168.1.20:$listenPort", "{}")
        assert(fromLan.body().contains("csrf-validation-failed"),
          fromLan.body())
      finally server.close()
    finally runtime.close()

  /** A ServerConfig that says the server listens on 127.0.0.1 at
    * `listenPort`. */
  private def config(
      mode: ServerMode,
      authenticatedRouteMount: Option[AuthenticatedRouteMountConfiguration] = None,
      publicBaseUrl: Option[URI] = None
  ): ServerConfig = ServerConfig(
    host = "127.0.0.1",
    port = listenPort,
    publicBaseUrl = publicBaseUrl,
    databasePath = Paths.get("var/test-server-routes"),
    mode = mode,
    authenticatedRouteMount = authenticatedRouteMount,
    version = "test-version"
  )
