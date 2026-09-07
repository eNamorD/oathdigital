package oathdigital.server

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse => JavaResponse}

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http

class HealthRoutesSuite extends munit.FunSuite {
  test("liveness stays live while readiness follows lifecycle state") {
    implicit val system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, "health-routes-test")
    val readiness = ServerReadiness.starting("test-version")
    val binding = bind(HealthRoutes.route(readiness))
    val client = HttpClient.newHttpClient()

    try {
      assertHealth(
        get(client, binding, "/health/live"),
        200,
        "{\"status\":\"live\",\"version\":\"test-version\"}"
      )
      assertHealth(
        get(client, binding, "/health/ready"),
        503,
        "{\"status\":\"not-ready\",\"version\":\"test-version\"}"
      )

      readiness.markReady()
      assertHealth(
        get(client, binding, "/health/ready"),
        200,
        "{\"status\":\"ready\",\"version\":\"test-version\"}"
      )
      assertHealth(
        get(client, binding, "/health"),
        200,
        "{\"status\":\"ready\",\"version\":\"test-version\"}"
      )

      readiness.markStopping()
      assertHealth(
        get(client, binding, "/health/live"),
        200,
        "{\"status\":\"live\",\"version\":\"test-version\"}"
      )
      assertHealth(
        get(client, binding, "/health/ready"),
        503,
        "{\"status\":\"not-ready\",\"version\":\"test-version\"}"
      )
    } finally {
      Await.result(binding.terminate(5.seconds), 10.seconds)
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }

  private def assertHealth(
      response: JavaResponse[String],
      status: Int,
      body: String
  ): Unit = {
    assertEquals(response.statusCode(), status)
    assertEquals(response.body(), body)
    assertEquals(response.headers().firstValue("Cache-Control").get(), "no-store")
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
  ): JavaResponse[String] =
    client.send(
      HttpRequest.newBuilder(URI.create(
        s"http://127.0.0.1:${binding.localAddress.getPort}$path"
      )).GET().build(),
      JavaResponse.BodyHandlers.ofString()
    )
}
