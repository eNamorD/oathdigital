package oathdigital.server

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse => JavaResponse}

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route

class ProductionFrontendRoutesSuite extends munit.FunSuite {
  test("serves the packaged shell and assets with production cache policy") {
    implicit val system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, "production-frontend-routes-test")
    val binding = bind(ProductionFrontendRoutes.route)
    val client = HttpClient.newHttpClient()

    try {
      val index = get(client, binding, "/")
      assertEquals(index.statusCode(), 200)
      assert(index.body().contains("/assets/main.js"))
      assertEquals(cacheControl(index), Some("no-cache"))

      val javascript = get(client, binding, "/assets/main.js")
      assertEquals(javascript.statusCode(), 200)
      assert(javascript.body().nonEmpty)
      assertEquals(
        cacheControl(javascript),
        Some("public, max-age=0, must-revalidate")
      )

      val stylesheet = get(client, binding, "/assets/styles.css")
      assertEquals(stylesheet.statusCode(), 200)
      assert(stylesheet.body().nonEmpty)
      assertEquals(
        cacheControl(stylesheet),
        Some("public, max-age=0, must-revalidate")
      )

      assertEquals(get(client, binding, "/missing.js").statusCode(), 404)
      assertEquals(get(client, binding, "/assets/missing.js").statusCode(), 404)
    } finally {
      Await.result(binding.terminate(5.seconds), 10.seconds)
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }

  private def bind(route: Route)(implicit system: ActorSystem[Nothing]) =
    Await.result(
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

  private def cacheControl(response: JavaResponse[String]): Option[String] =
    Option(response.headers().firstValue("Cache-Control").orElse(null))
}
