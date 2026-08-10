package oathdigital.server

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object DevelopmentRoutes {
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
        pathEndOrSingleSlash {
          getFromFile("frontend/index.html")
        } ~
        getFromDirectory("frontend")
  }
}
