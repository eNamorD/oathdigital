package oathdigital.server

import java.nio.file.Path

import oathdigital.application.{
  IdentityRepository,
  MembershipAuthorizationService
}
import oathdigital.catalog.{
  CatalogLoadRequest,
  CatalogLoader,
  CatalogSelection
}
import oathdigital.persistence.HsqldbDatabaseOwner
final class ServerRuntime private (
    val firstGame: GameServerGateway,
    val authenticatedGame: AuthenticatedGameGateway,
    val authorization: MembershipAuthorizationService,
    val identities: IdentityRepository,
    val trustedGameProvisioning: TrustedGameProvisioning,
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
            new TrustedGameProvisioning(firstGameService, planFactory, database.trustedGames),
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
