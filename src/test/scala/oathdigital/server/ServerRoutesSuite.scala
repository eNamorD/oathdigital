package oathdigital.server

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse => JavaResponse}
import java.nio.file.{Files, Paths}

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http

class ServerRoutesSuite extends munit.FunSuite {
  test("authenticated routes mount only with complete session configuration") {
    assertEquals(
      AuthenticatedRouteMountConfiguration.fromOptions(None, None),
      Right(None)
    )
    assert(AuthenticatedRouteMountConfiguration.fromOptions(
      Some("oath_session"), None).isLeft)
    assert(AuthenticatedRouteMountConfiguration.fromOptions(
      None, Some("https://oath.example")).isLeft)

    implicit val system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, "server-routes-test")
    val blocking = system.dispatchers.lookup(
      DispatcherSelector.fromConfig("oathdigital.blocking-dispatcher"))
    val runtime = ServerRuntime.open(
      Files.createTempDirectory("server-routes-").resolve("database"),
      Paths.get("docs/catalog/new-foundations-component-catalog.json")
    ).toOption.get
    val client = HttpClient.newHttpClient()

    try {
      val readiness = ServerReadiness.starting("test-version")
      val absent = bind(ServerRoutes.route(
        runtime,
        blocking,
        config(ServerMode.Development),
        readiness
      ))
      try {
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
        assertEquals(get(client, absent, "/games/game/api").statusCode(), 404)
        assertEquals(get(client, absent, "/games/game").statusCode(), 404)
      } finally Await.result(absent.terminate(5.seconds), 10.seconds)

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
    } finally {
      runtime.close()
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }

  test("trusted-alpha serves production frontend without development routes") {
    implicit val system: ActorSystem[Nothing] =
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

    try {
      assertEquals(get(client, binding, "/").statusCode(), 200)
      assertEquals(get(client, binding, "/games/test-game").statusCode(), 403)
      assertEquals(get(client, binding, "/games/test-game/api").statusCode(), 403)
      assertEquals(get(client, binding,
        "/api/dev/first-games/test-game?playerId=p1").statusCode(), 404)
      assertEquals(get(client, binding,
        "/api/authenticated/first-games/test-game").statusCode(), 404)
    } finally {
      Await.result(binding.terminate(5.seconds), 10.seconds)
      runtime.close()
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }

  private def config(
      mode: ServerMode,
      authenticatedRouteMount: Option[AuthenticatedRouteMountConfiguration] = None
  ): ServerConfig = ServerConfig(
    host = "127.0.0.1",
    port = 8080,
    publicBaseUrl = None,
    databasePath = Paths.get("var/test-server-routes"),
    catalogPath = Paths.get(
      "docs/catalog/new-foundations-component-catalog.json"),
    mode = mode,
    authenticatedRouteMount = authenticatedRouteMount,
    version = "test-version"
  )

  private def bind(route: akka.http.scaladsl.server.Route)(implicit
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
}
