package oathdigital.server

import java.nio.file.Path

import oathdigital.application.{
  ChronicleRandomPort,
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
    val trustedGame: TrustedGameGateway,
    private val database: HsqldbDatabaseOwner
) extends AutoCloseable {
  override def close(): Unit = database.close()
}

object ServerRuntime {
  def open(
      databasePath: Path,
      catalogPath: Path,
      random: ChronicleRandomPort = ChronicleRandomPort.random
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
          val generatedPlanFactory =
            new oathdigital.application.GeneratedFirstGamePlanFactory(
              catalog,
              random
            )
          val authorization =
            new MembershipAuthorizationService(database.identities)
          new ServerRuntime(
            new GameServerGateway(
              firstGameService,
              projector
            ),
            new AuthenticatedGameGateway(
              firstGameService,
              projector,
              authorization,
              database.identities,
              generatedPlanFactory
            ),
            authorization,
            database.identities,
            new TrustedGameProvisioning(firstGameService, generatedPlanFactory,
              database.trustedGames),
            new TrustedGameGateway(firstGameService, projector),
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
