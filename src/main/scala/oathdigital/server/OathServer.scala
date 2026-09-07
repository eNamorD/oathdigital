package oathdigital.server

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
    val version = Option(getClass.getPackage.getImplementationVersion)
      .getOrElse("development")
    val config = ServerConfig
      .parse(arguments, sys.env, version)
      .fold(
        errors => throw new IllegalArgumentException(errors.mkString("; ")),
        identity
      )
    implicit val system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, "oathdigital-server")
    implicit val executionContext = system.executionContext
    val blockingExecutionContext = system.dispatchers.lookup(
      DispatcherSelector.fromConfig("oathdigital.blocking-dispatcher")
    )
    val readiness = ServerReadiness.starting(config.version)
    system.log.info(
      "Oath Digital server starting: version={}, mode={}, host={}, port={}, publicBaseUrlPresent={}, databasePath={}, catalogPath={}",
      config.version,
      config.mode,
      config.host,
      Int.box(config.port),
      Boolean.box(config.publicBaseUrl.nonEmpty),
      config.databasePath.toAbsolutePath.normalize.toString,
      config.catalogPath.toAbsolutePath.normalize.toString
    )

    ServerRuntime.open(config.databasePath, config.catalogPath) match {
      case Left(error) =>
        system.log.error("Server startup failed: {}", error)
        system.terminate()
        Await.result(system.whenTerminated, 10.seconds)
        throw new IllegalStateException(error)
      case Right(runtime) =>
        CoordinatedShutdown(system).addTask(
          CoordinatedShutdown.PhaseBeforeServiceUnbind,
          "mark-server-stopping"
        ) { () =>
          readiness.markStopping()
          Future.successful(Done)
        }

        CoordinatedShutdown(system).addTask(
          CoordinatedShutdown.PhaseBeforeActorSystemTerminate,
          "close-event-journal"
        ) { () =>
          Future {
            runtime.close()
            Done
          }(blockingExecutionContext)
        }

        val route = ServerRoutes.route(
          runtime,
          blockingExecutionContext,
          config.authenticatedRouteMount,
          readiness
        )

        val binding =
          try
            Await.result(
              Http().newServerAt(config.host, config.port).bind(route),
              30.seconds
            )
          catch {
            case NonFatal(error) =>
              readiness.markStopping()
              runtime.close()
              system.terminate()
              Await.result(system.whenTerminated, 30.seconds)
              throw error
          }
        readiness.markReady()
        system.log.info(
          "Oath Digital server listening at http://{}:{}/",
          config.host,
          Int.box(config.port)
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
