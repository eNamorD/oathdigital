package oathdigital.server

import java.nio.file.Path

import oathdigital.application.{
  SetupApplicationError,
  SetupApplicationService,
  SetupCommandAccepted
}
import oathdigital.catalog.{
  CatalogLoadRequest,
  CatalogLoader,
  CatalogSelection
}
import oathdigital.persistence.HsqldbEventStreamRepository
import oathdigital.setup.SetupCommand

/**
 * The only production command gateway.
 *
 * Network decoders call this boundary with transient commands. Validation and
 * durable append remain inside the JVM-owned application service.
 */
final class ServerCommandGateway private[server] (
    service: SetupApplicationService
) {
  def handleSetup(
      gameId: String,
      command: SetupCommand
  ): Either[SetupApplicationError, SetupCommandAccepted] =
    service.handle(gameId, command)
}

final class ServerRuntime private (
    val commands: ServerCommandGateway,
    private val repository: HsqldbEventStreamRepository
) extends AutoCloseable {
  override def close(): Unit = repository.close()
}

object ServerRuntime {
  def open(
      databasePath: Path,
      catalogPath: Path
  ): Either[String, ServerRuntime] =
    HsqldbEventStreamRepository.open(databasePath).left.map {
      case oathdigital.application.RepositoryFailure.StorageFailure(message) =>
        message
    }.flatMap { repository =>
      CatalogLoader
        .load(
          catalogPath,
          CatalogLoadRequest(CatalogSelection(
            setupCards = true,
            supplyBoards = true,
            sites = true
          ))
        )
        .left
        .map(errors =>
          errors.map(error => s"${error.path}: ${error.message}").mkString(
            "; "
          ))
        .map { catalog =>
          val service = new SetupApplicationService(catalog, repository)
          new ServerRuntime(new ServerCommandGateway(service), repository)
        }
        .left
        .map { error =>
          repository.close()
          error
        }
    }
}
