package oathdigital.server

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.headers.RawHeader

object DevelopmentRoutes {
  private val DevelopmentAssetCacheControl = RawHeader(
    "Cache-Control",
    "no-store, no-cache, must-revalidate, max-age=0"
  )

  def route(
      firstGame: GameServerGateway,
      blockingExecutionContext: ExecutionContext,
      serveFrontend: Boolean = true
  ): Route = {
    val api =
      path("health") {
        get {
          complete("ok")
        }
      } ~ new GameRoutes(
        firstGame,
        blockingExecutionContext
      ).route

    if (!serveFrontend) api
    else
      api ~
        respondWithHeader(DevelopmentAssetCacheControl) {
          pathEndOrSingleSlash {
            getFromFile("frontend/index.html")
          } ~
          getFromDirectory("frontend")
        }
  }
}
