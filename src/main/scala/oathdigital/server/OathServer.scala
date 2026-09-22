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
    val osName = sys.props.getOrElse("os.name", "")
    val launch = DesktopLaunchProfile.prepare(
      arguments,
      sys.env,
      osName,
      sys.props.getOrElse("user.home", ""),
      () => LanAddress.detect()
    ) match {
      case Left(error) =>
        System.err.println(s"oathdigital: $error")
        sys.exit(2)
      case Right(prepared) => prepared
    }
    launch.toVector.flatMap(_.warnings)
      .foreach(warning => System.err.println(s"oathdigital: warning: $warning"))
    val environment = launch.fold(sys.env)(_.environment)
    val config = ServerConfig.parse(arguments, environment, version) match {
      case Left(errors) =>
        errors.foreach(error => System.err.println(s"oathdigital: $error"))
        sys.exit(2)
      case Right(parsed) => parsed
    }
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
    if (config.mode == ServerMode.TrustedAlpha &&
        config.authenticatedRouteMount.isDefined)
      system.log.warn(
        "Authenticated route options are ignored in trusted-alpha mode; " +
          "session cookie name and authenticated public origin apply to " +
          "development mode only"
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
            system.log.info("Oath Digital database closed")
            Done
          }(blockingExecutionContext)
        }

        val route = ServerRoutes.route(
          runtime,
          blockingExecutionContext,
          config,
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
              launch.flatMap(desktop =>
                DesktopConsole.bindFailure(error, config.port, desktop.settingsFile)
              ) match {
                case Some(message) =>
                  System.err.println(s"oathdigital: $message")
                  sys.exit(2)
                case None => throw error
              }
          }
        readiness.markReady()
        system.log.info(
          "Oath Digital server listening at http://{}:{}/",
          config.host,
          Int.box(config.port)
        )
        launch.foreach { desktop =>
          System.out.print(DesktopConsole.banner(config, desktop))
          System.out.flush()
          if (desktop.openBrowser)
            DesktopConsole.openBrowser(
              osName,
              DesktopConsole.browserUrl(config),
              command => {
                new ProcessBuilder(command: _*)
                  .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                  .redirectError(ProcessBuilder.Redirect.DISCARD)
                  .start()
                ()
              }
            ).foreach(warning => system.log.warn(warning))
        }

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
