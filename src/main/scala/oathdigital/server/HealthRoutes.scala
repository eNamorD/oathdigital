package oathdigital.server

import java.util.concurrent.atomic.AtomicReference

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

sealed trait ReadinessState

object ReadinessState {
  case object Starting extends ReadinessState
  case object Ready extends ReadinessState
  case object Stopping extends ReadinessState
}

final class ServerReadiness private (
    val version: String,
    private val state: AtomicReference[ReadinessState]
) {
  def current: ReadinessState = state.get()

  def markReady(): Unit = state.set(ReadinessState.Ready)

  def markStopping(): Unit = state.set(ReadinessState.Stopping)
}

object ServerReadiness {
  def starting: ServerReadiness = starting("development")

  def starting(version: String): ServerReadiness =
    new ServerReadiness(version, new AtomicReference(ReadinessState.Starting))
}

object HealthRoutes {
  private val NoStore = RawHeader("Cache-Control", "no-store")

  def route(readiness: ServerReadiness): Route =
    respondWithHeader(NoStore) {
      path("health" / "live") {
        get {
          complete(response(StatusCodes.OK, "live", readiness.version))
        }
      } ~
      path("health" / "ready") {
        get {
          complete(readyResponse(readiness))
        }
      } ~
      // Remove after alpha clients migrate to /health/ready.
      path("health") {
        get {
          complete(readyResponse(readiness))
        }
      }
    }

  private def readyResponse(readiness: ServerReadiness): HttpResponse =
    readiness.current match {
      case ReadinessState.Ready => response(StatusCodes.OK, "ready", readiness.version)
      case ReadinessState.Starting | ReadinessState.Stopping =>
        response(StatusCodes.ServiceUnavailable, "not-ready", readiness.version)
    }

  private def response(
      status: akka.http.scaladsl.model.StatusCode,
      health: String,
      version: String
  ): HttpResponse =
    HttpResponse(
      status = status,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        ujson.write(ujson.Obj("status" -> health, "version" -> version))
      )
    )
}
