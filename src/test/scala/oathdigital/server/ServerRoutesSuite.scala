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
      val absent = bind(ServerRoutes.route(runtime, blocking, None, readiness))
      try {
        assertEquals(get(client, absent, "/health/live").statusCode(), 200)
        assertEquals(get(client, absent, "/health").statusCode(), 503)
        readiness.markReady()
        assertEquals(get(client, absent, "/health/ready").statusCode(), 200)
        assertEquals(get(client, absent, "/health").statusCode(), 200)
        assertEquals(get(client, absent,
          "/api/authenticated/first-games/game").statusCode(), 404)
      } finally Await.result(absent.terminate(5.seconds), 10.seconds)

      val configuration = AuthenticatedRouteMountConfiguration(
        "oath_session", "http://127.0.0.1")
      val mounted = bind(ServerRoutes.route(
        runtime,
        blocking,
        Some(configuration),
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
