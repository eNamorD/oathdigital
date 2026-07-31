package oathdigital.server

import java.nio.file.Paths

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http

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
    val blockingExecutionContext = system.dispatchers.lookup(
      DispatcherSelector.fromConfig("oathdigital.blocking-dispatcher")
    )

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
          }(blockingExecutionContext)
        }

        val route = DevelopmentRoutes.route(
          runtime.firstGame,
          blockingExecutionContext
        )

        val binding =
          try
            Await.result(
              Http().newServerAt(host, port).bind(route),
              30.seconds
            )
          catch {
            case NonFatal(error) =>
              runtime.close()
              system.terminate()
              Await.result(system.whenTerminated, 30.seconds)
              throw error
          }
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
