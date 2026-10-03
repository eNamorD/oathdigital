package oathdigital.testkit

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.duration._

import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route

import oathdigital.application.CsrfTokenDigest
import oathdigital.server.SensitiveTokenDigest

/** A route served on a free loopback port for one test, with a client that
  * talks to it. `get` and `post` take a path under `base`; `post` sends the
  * same-origin `Origin` and the `csrfToken` header unless told otherwise
  * (the development routes ignore both), a `user` as the X-Test-User
  * header the suites' authenticators read, and a `cookie` as the Cookie
  * header. `close` unbinds the route and stops its actor system, even when
  * unbinding fails. */
final class HttpTestServer private (system: ActorSystem[Nothing],
    binding: Http.ServerBinding):
  val base: String = s"http://127.0.0.1:${binding.localAddress.getPort}"
  private val client = HttpClient.newHttpClient()

  def get(path: String, user: Option[String] = None,
      cookie: Option[String] = None): HttpResponse[String] =
    val builder = HttpRequest.newBuilder(URI.create(base + path)).GET()
    user.foreach(value => builder.header("X-Test-User", value))
    cookie.foreach(value => builder.header("Cookie", value))
    client.send(builder.build(), HttpResponse.BodyHandlers.ofString())

  def post(path: String, body: String, user: Option[String] = None,
      origin: Option[String] = Some(HttpTestServer.origin),
      csrf: Option[String] = Some(HttpTestServer.csrfToken),
      cookie: Option[String] = None): HttpResponse[String] =
    val builder = HttpRequest.newBuilder(URI.create(base + path))
      .header("Content-Type", "application/json")
    user.foreach(value => builder.header("X-Test-User", value))
    cookie.foreach(value => builder.header("Cookie", value))
    origin.foreach(value => builder.header("Origin", value))
    csrf.foreach(value => builder.header("X-CSRF-Token", value))
    client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
      HttpResponse.BodyHandlers.ofString())

  def close(): Unit =
    try Await.result(binding.terminate(5.seconds), 10.seconds)
    finally
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)

object HttpTestServer:
  /** The origin the suites configure SameOriginCsrfProtection with, and
    * that `post` sends as Origin. */
  val origin = "http://127.0.0.1"

  /** The CSRF token `post` sends by default: 43 characters, the shortest
    * token SessionSecurity accepts (43 to 128). */
  val csrfToken: String = "c" * 43

  /** The digest a test session holds for `csrfToken`. The token is a
    * well-formed constant, so building its digest cannot fail. */
  val csrfDigest: CsrfTokenDigest = CsrfTokenDigest.fromBytes(
    SensitiveTokenDigest.sha256(csrfToken))
    .fold(error => throw new IllegalStateException(error), identity)

  /** Boots an actor system named `name` and binds the route `routeOn`
    * builds from the system's blocking dispatcher. Stops the system if the
    * route cannot be built or bound. */
  def start(name: String)(
      routeOn: ExecutionContextExecutor => Route): HttpTestServer =
    given system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, name)
    try
      val blocking = system.dispatchers.lookup(
        DispatcherSelector.fromConfig("oathdigital.blocking-dispatcher"))
      val binding = Await.result(
        Http().newServerAt("127.0.0.1", 0).bind(routeOn(blocking)),
        10.seconds)
      new HttpTestServer(system, binding)
    catch
      case error: Throwable =>
        system.terminate()
        throw error

  /** The response's Cache-Control header, if it has one. */
  def cacheControl(response: HttpResponse[String]): Option[String] =
    val value = response.headers().firstValue("Cache-Control")
    if value.isPresent then Some(value.get()) else None
