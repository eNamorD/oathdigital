package oathdigital.server

import java.nio.file.Path

import oathdigital.application.{
  SetupApplicationError,
  SetupApplicationService,
  SetupCommandAccepted,
  IdentityRepository,
  MembershipAuthorizationService
}
import oathdigital.catalog.{
  CatalogLoadRequest,
  CatalogLoader,
  CatalogSelection
}
import oathdigital.persistence.HsqldbDatabaseOwner
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
      expectedNextSequence: Long,
      command: SetupCommand
  ): Either[SetupApplicationError, SetupCommandAccepted] =
    service.handleAtExpectedPosition(
      gameId,
      expectedNextSequence,
      command
    )
}

final class ServerRuntime private (
    val commands: ServerCommandGateway,
    val firstGame: GameServerGateway,
    val authenticatedGame: AuthenticatedGameGateway,
    val authorization: MembershipAuthorizationService,
    val identities: IdentityRepository,
    private val database: HsqldbDatabaseOwner
) extends AutoCloseable {
  override def close(): Unit = database.close()
}

object ServerRuntime {
  def open(
      databasePath: Path,
      catalogPath: Path
  ): Either[String, ServerRuntime] =
    HsqldbDatabaseOwner.open(databasePath).left.map {
      case oathdigital.application.RepositoryFailure.StorageFailure(message) =>
        message
      case oathdigital.application.RepositoryFailure.InvalidConfiguration(
            message
          ) =>
        message
    }.flatMap { database =>
      val repository = database.eventStreams
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
          val firstGameService =
            new oathdigital.application.GameApplicationService(
              catalog,
              repository
            )
          val projector =
            new oathdigital.application.GameProjector(catalog)
          val planFactory =
            new oathdigital.application.DevelopmentFirstGamePlanFactory(
              catalog
            )
          val authorization =
            new MembershipAuthorizationService(database.identities)
          new ServerRuntime(
            new ServerCommandGateway(service),
            new GameServerGateway(
              firstGameService,
              projector,
              planFactory
            ),
            new AuthenticatedGameGateway(
              firstGameService,
              projector,
              authorization,
              database.identities,
              planFactory
            ),
            authorization,
            database.identities,
            database
          )
        }
        .left
        .map { error =>
          database.close()
          error
        }
    }
}
