package oathdigital.server

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse => JavaResponse}
import java.nio.file.{Files, Paths}

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http

class ServerRoutesSuite extends munit.FunSuite:
  test("authenticated routes mount only with complete session configuration"):
    given system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, "server-routes-test")
    val blocking = system.dispatchers.lookup(
      DispatcherSelector.fromConfig("oathdigital.blocking-dispatcher"))
    val runtime = ServerRuntime.open(
      Files.createTempDirectory("server-routes-").resolve("database"),
      Paths.get("docs/catalog/new-foundations-component-catalog.json")
    ).toOption.get
    val client = HttpClient.newHttpClient()

    try
      val readiness = ServerReadiness.starting("test-version")
      val absent = bind(ServerRoutes.route(
        runtime,
        blocking,
        config(ServerMode.Development),
        readiness
      ))
      try
        assertEquals(get(client, absent, "/health/live").statusCode(), 200)
        assertEquals(get(client, absent, "/health").statusCode(), 503)
        readiness.markReady()
        assertEquals(get(client, absent, "/health/ready").statusCode(), 200)
        assertEquals(get(client, absent, "/health").statusCode(), 200)
        assertEquals(get(client, absent,
          "/api/authenticated/first-games/game").statusCode(), 404)
        assertEquals(get(client, absent,
          "/api/dev/first-games/test-game/events?limit=101").statusCode(), 400)
        assertEquals(get(client, absent, "/s/AAAAAAAAAAAAAAAAAAAAAA").statusCode(), 404)
        assertEquals(get(client, absent, "/games/game/api").statusCode(), 403)
      finally Await.result(absent.terminate(5.seconds), 10.seconds)

      val configuration = AuthenticatedRouteMountConfiguration(
        "oath_session", "http://127.0.0.1")
      val mounted = bind(ServerRoutes.route(
        runtime,
        blocking,
        config(
          ServerMode.Development,
          authenticatedRouteMount = Some(configuration)
        ),
        ServerReadiness.starting("test-version"),
        () => 0L
      ))
      try assertEquals(get(client, mounted,
        "/api/authenticated/first-games/game").statusCode(), 401)
      finally Await.result(mounted.terminate(5.seconds), 10.seconds)
    finally
      runtime.close()
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)

  test("trusted-alpha serves production frontend without development routes"):
    given system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, "trusted-alpha-routes-test")
    val blocking = system.dispatchers.lookup(
      DispatcherSelector.fromConfig("oathdigital.blocking-dispatcher"))
    val runtime = ServerRuntime.open(
      Files.createTempDirectory("trusted-alpha-routes-").resolve("database"),
      Paths.get("docs/catalog/new-foundations-component-catalog.json")
    ).toOption.get
    val client = HttpClient.newHttpClient()
    val binding = bind(ServerRoutes.route(
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
      assertEquals(get(client, binding, "/").statusCode(), 200)
      assertEquals(get(client, binding, "/games/test-game").statusCode(), 403)
      assertEquals(get(client, binding, "/games/test-game/api").statusCode(), 403)
      assertEquals(get(client, binding,
        "/api/dev/first-games/test-game?playerId=p1").statusCode(), 404)
      assertEquals(get(client, binding,
        "/api/authenticated/first-games/test-game").statusCode(), 404)
    finally
      Await.result(binding.terminate(5.seconds), 10.seconds)
      runtime.close()
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)

  test("development mode creates games like trusted-alpha and opens them in the dev API"):
    given system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, "development-create-routes-test")
    val blocking = system.dispatchers.lookup(
      DispatcherSelector.fromConfig("oathdigital.blocking-dispatcher"))
    val runtime = ServerRuntime.open(
      Files.createTempDirectory("development-create-routes-").resolve("database"),
      Paths.get("docs/catalog/new-foundations-component-catalog.json")
    ).toOption.get
    val client = HttpClient.newHttpClient()
    val binding = bind(ServerRoutes.route(runtime, blocking,
      config(ServerMode.Development), ServerReadiness.starting("test-version")))

    def create(origin: String) = client.send(
      HttpRequest.newBuilder(URI.create(
        s"http://127.0.0.1:${binding.localAddress.getPort}/games"))
        .header("Origin", origin)
        .POST(HttpRequest.BodyPublishers.ofString(
          """{"participants":[""" +
            """{"playerId":"Red","color":"red"},""" +
            """{"playerId":"Blue","color":"blue"}]}"""))
        .build(),
      JavaResponse.BodyHandlers.ofString())

    try
      assertEquals(create("http://example.com:8080").statusCode(), 403)
      assertEquals(create("http://localhost:9090").statusCode(), 403)
      assertEquals(create("http://127.0.0.1:8080").statusCode(), 201)
      val created = create("http://localhost:8080")
      assertEquals(created.statusCode(), 201)
      assert(created.body().contains("http://127.0.0.1:8080/s/"))
      val gameId = ujson.read(created.body())("gameId").str
      val loaded = get(client, binding, s"/api/dev/first-games/$gameId?playerId=Red")
      assertEquals(loaded.statusCode(), 200)
      assert(loaded.body().contains(s"\"gameId\":\"$gameId\""))
    finally
      Await.result(binding.terminate(5.seconds), 10.seconds)
      runtime.close()
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)

  test("trusted-alpha also accepts the host's own loopback origins on the listen port"):
    given system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, "trusted-alpha-loopback-origin-test")
    val blocking = system.dispatchers.lookup(
      DispatcherSelector.fromConfig("oathdigital.blocking-dispatcher"))
    val runtime = ServerRuntime.open(
      Files.createTempDirectory("trusted-alpha-loopback-origin-").resolve("database"),
      Paths.get("docs/catalog/new-foundations-component-catalog.json")
    ).toOption.get
    val client = HttpClient.newHttpClient()
    val binding = bind(ServerRoutes.route(runtime, blocking,
      config(ServerMode.TrustedAlpha,
        publicBaseUrl = Some(URI.create("http://203.0.113.7:8080"))),
      ServerReadiness.starting("test-version")))

    def post(path: String, origin: String, body: String) = client.send(
      HttpRequest.newBuilder(URI.create(
        s"http://127.0.0.1:${binding.localAddress.getPort}$path"))
        .header("Origin", origin)
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build(),
      JavaResponse.BodyHandlers.ofString())
    def create(origin: String) = post("/games", origin,
      """{"participants":[""" +
        """{"playerId":"Red","color":"red"},""" +
        """{"playerId":"Blue","color":"blue"}]}""")

    try
      assertEquals(create("http://203.0.113.7:8080").statusCode(), 201)
      Seq("http://localhost:8080", "http://127.0.0.1:8080", "http://[::1]:8080")
        .foreach { origin =>
          val created = create(origin)
          assertEquals(created.statusCode(), 201, origin)
          assert(created.body().contains("\"http://203.0.113.7:8080/s/"), origin)
        }
      Seq("http://localhost:9090", "https://localhost:8080",
          "http://192.168.1.20:8080", "http://example.com:8080")
        .foreach(origin => assertEquals(create(origin).statusCode(), 403, origin))
      // Commands pass the same origin gate before the seat cookie is checked.
      val fromLoopback = post("/games/g/api/commands", "http://localhost:8080", "{}")
      assert(!fromLoopback.body().contains("csrf-validation-failed"), fromLoopback.body())
      val fromLan = post("/games/g/api/commands", "http://192.168.1.20:8080", "{}")
      assert(fromLan.body().contains("csrf-validation-failed"), fromLan.body())
    finally
      Await.result(binding.terminate(5.seconds), 10.seconds)
      runtime.close()
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)

  private def config(
      mode: ServerMode,
      authenticatedRouteMount: Option[AuthenticatedRouteMountConfiguration] = None,
      publicBaseUrl: Option[URI] = None
  ): ServerConfig = ServerConfig(
    host = "127.0.0.1",
    port = 8080,
    publicBaseUrl = publicBaseUrl,
    databasePath = Paths.get("var/test-server-routes"),
    catalogPath = Paths.get(
      "docs/catalog/new-foundations-component-catalog.json"),
    mode = mode,
    authenticatedRouteMount = authenticatedRouteMount,
    version = "test-version"
  )

  private def bind(route: akka.http.scaladsl.server.Route)(using
      system: ActorSystem[Nothing]
  ) = Await.result(
    Http().newServerAt("127.0.0.1", 0).bind(route),
    10.seconds
  )

  private def get(
      client: HttpClient,
      binding: akka.http.scaladsl.Http.ServerBinding,
      path: String
  ) = client.send(
    HttpRequest.newBuilder(URI.create(
      s"http://127.0.0.1:${binding.localAddress.getPort}$path"
    )).GET().build(),
    JavaResponse.BodyHandlers.ofString()
  )
