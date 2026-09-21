package oathdigital.application

import oathdigital.model._

sealed trait AuthenticatedPrincipal extends Product with Serializable {
  def userId: UserId
}
final case class AuthenticatedUser(userId: UserId)
    extends AuthenticatedPrincipal

sealed trait AuthenticationFailure extends Product with Serializable
object AuthenticationFailure {
  case object MissingCredential extends AuthenticationFailure
  final case class InvalidCredential(message: String)
      extends AuthenticationFailure
  final case class StorageFailure(message: String)
      extends AuthenticationFailure
}

trait Authenticator[-Credential] {
  def authenticate(
      credential: Credential
  ): Either[AuthenticationFailure, AuthenticatedPrincipal]
}

sealed trait GameAccessContext extends Product with Serializable {
  def gameId: String
  def userId: UserId
}
object GameAccessContext {
  final case class Owner(gameId: String, userId: UserId)
      extends GameAccessContext
  final case class Player(
      gameId: String,
      userId: UserId,
      playerId: PlayerId
  ) extends GameAccessContext
  final case class Spectator(gameId: String, userId: UserId)
      extends GameAccessContext
}

sealed trait ProjectionScope extends Product with Serializable
object ProjectionScope {
  case object PublicOnly extends ProjectionScope
  final case class PlayerPrivate(playerId: PlayerId) extends ProjectionScope
}

object AuthorizedPlayer {
  private[application] def forPlayer(playerId: PlayerId): AuthorizedPlayer =
    AuthorizedPlayer(GameAccessContext.Player("transport", UserId("transport"), playerId))
}

final case class ProjectionAuthorization(
    access: GameAccessContext,
    scope: ProjectionScope
)

final case class AuthorizedPlayer private (
    access: GameAccessContext.Player
) {
  def placePawn(siteId: SiteId): GameCommand =
    GameCommand.PlacePawn(access.playerId, siteId)

  def chooseAdviser(adviserId: DenizenId): GameCommand =
    GameCommand.ChooseAdviser(access.playerId, adviserId)

  def endWake: GameCommand =
    GameCommand.EndWake(access.playerId)

  def beginRest: GameCommand = GameCommand.BeginRest(access.playerId)
  def finishRest: GameCommand = GameCommand.FinishRest(access.playerId)
  def usePower(power: PowerId, source: DecisionOptionRef): GameCommand =
    GameCommand.UsePower(access.playerId, power, source)
  def peekSiteRelics: GameCommand = GameCommand.PeekSiteRelics(access.playerId)
  def revealOwnedRelic(relic: RelicId): GameCommand =
    GameCommand.RevealOwnedRelic(access.playerId, relic)
  def moveWarbands(toSite: Boolean, amount: Int): GameCommand =
    GameCommand.MoveWarbands(access.playerId, toSite, amount)
  def resolveCardDecision(
      decision: DecisionId,
      resolution: CardDecisionResolution
  ): GameCommand = GameCommand.ResolveCardDecision(
    access.playerId, decision, resolution)

  def rollWalker(pool: PoolKey): GameCommand =
    GameCommand.RollWalker(access.playerId, pool)

  def resolveWalker(treeDecision: TreeDecision): GameCommand =
    GameCommand.ResolveWalker(access.playerId, treeDecision)
}

sealed trait AuthorizationFailure extends Product with Serializable
object AuthorizationFailure {
  final case class NotMember(gameId: String, userId: UserId)
      extends AuthorizationFailure
  final case class Forbidden(action: String) extends AuthorizationFailure
  final case class CorruptMembership(message: String)
      extends AuthorizationFailure
  final case class StorageFailure(message: String)
      extends AuthorizationFailure
}

final class MembershipAuthorizationService(repository: IdentityRepository) {
  import AuthorizationFailure._
  import ProjectionScope._

  def resolve(
      gameId: String,
      principal: AuthenticatedPrincipal
  ): Either[AuthorizationFailure, GameAccessContext] =
    repository.findMembership(gameId, principal.userId)
      .left.map {
        case IdentityFailure.StorageFailure(message) => StorageFailure(message)
        case other => StorageFailure(other.toString)
      }
      .flatMap {
        case None => Left(NotMember(gameId, principal.userId))
        case Some(membership) => membership.role match {
          case MembershipRole.Owner if membership.playerId.isEmpty =>
            Right(GameAccessContext.Owner(gameId, principal.userId))
          case MembershipRole.Player =>
            membership.playerId.filter(_.trim.nonEmpty) match {
            case Some(playerId) => Right(GameAccessContext.Player(
              gameId,
              principal.userId,
              PlayerId(playerId)
            ))
            case None => Left(CorruptMembership(
              "player membership has no player ID"
            ))
          }
          case MembershipRole.Spectator if membership.playerId.isEmpty =>
            Right(GameAccessContext.Spectator(gameId, principal.userId))
          case _ => Left(CorruptMembership(
            "non-player membership occupies a player seat"
          ))
        }
      }

  def authorizeBootstrap(
      gameId: String,
      principal: AuthenticatedPrincipal
  ): Either[AuthorizationFailure, GameAccessContext.Owner] =
    resolve(gameId, principal).flatMap {
      case owner: GameAccessContext.Owner => Right(owner)
      case _ => Left(Forbidden("bootstrap"))
    }

  def authorizeProjection(
      gameId: String,
      principal: AuthenticatedPrincipal
  ): Either[AuthorizationFailure, ProjectionAuthorization] =
    resolve(gameId, principal).map {
      case player: GameAccessContext.Player =>
        ProjectionAuthorization(player, PlayerPrivate(player.playerId))
      case other => ProjectionAuthorization(other, PublicOnly)
    }

  def authorizeCommand(
      gameId: String,
      principal: AuthenticatedPrincipal
  ): Either[AuthorizationFailure, AuthorizedPlayer] =
    resolve(gameId, principal).flatMap {
      case player: GameAccessContext.Player =>
        Right(AuthorizedPlayer(player))
      case _ => Left(Forbidden("command"))
    }
}
