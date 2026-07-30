package oathdigital.server

import java.nio.file.Paths

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._

object OathServer {
  def main(arguments: Array[String]): Unit = {
    val databasePath =
      Paths.get(arguments.headOption.getOrElse("var/oathdigital"))
    val catalogPath = Paths.get(
      arguments.drop(1).headOption.getOrElse(
        "docs/catalog/new-foundations-component-catalog.json"
      )
    )
    val host = sys.props.getOrElse("oathdigital.host", "127.0.0.1")
    val port =
      sys.props.get("oathdigital.port").fold(8080)(_.toInt)

    implicit val system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, "oathdigital-server")
    implicit val executionContext = system.executionContext

    ServerRuntime.open(databasePath, catalogPath) match {
      case Left(error) =>
        system.log.error("Server startup failed: {}", error)
        system.terminate()
        Await.result(system.whenTerminated, 10.seconds)
        throw new IllegalStateException(error)
      case Right(runtime) =>
        CoordinatedShutdown(system).addTask(
          CoordinatedShutdown.PhaseBeforeActorSystemTerminate,
          "close-event-journal"
        ) { () =>
          Future {
            runtime.close()
            Done
          }
        }

        val route =
          path("health") {
            get {
              complete("ok")
            }
          }

        val binding =
          Await.result(Http().newServerAt(host, port).bind(route), 30.seconds)
        system.log.info(
          "Oath Digital server listening at http://{}:{}/",
          host,
          Int.box(port)
        )

        CoordinatedShutdown(system).addTask(
          CoordinatedShutdown.PhaseServiceUnbind,
          "unbind-http"
        ) { () =>
          binding.terminate(10.seconds).map(_ => Done)
        }

        Await.result(system.whenTerminated, Duration.Inf)
    }
  }
}
