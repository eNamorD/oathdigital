package oathdigital.server

import oathdigital.application.{
  AuthenticatedPrincipal,
  AuthenticatedUser,
  AuthenticationFailure,
  Authenticator,
  UserId
}

final case class DevelopmentIdentityHeader(userId: Option[String])

sealed trait DevelopmentIdentityShimError extends Product with Serializable
object DevelopmentIdentityShimError {
  final case class NonLoopbackBinding(message: String)
      extends DevelopmentIdentityShimError
}

object DevelopmentIdentityShim {
  val HeaderName: String = "X-Oath-Dev-User"

  def configure(
      enabled: Boolean,
      validatedBindHost: String
  ): Either[
    DevelopmentIdentityShimError,
    Option[Authenticator[DevelopmentIdentityHeader]]
  ] =
    if (!enabled) Right(None)
    else DevelopmentTrustBoundary.validateLoopbackHost(validatedBindHost)
      .left.map(DevelopmentIdentityShimError.NonLoopbackBinding)
      .map(_ => Some(new DevelopmentHeaderAuthenticator))

  private final class DevelopmentHeaderAuthenticator
      extends Authenticator[DevelopmentIdentityHeader] {
    override def authenticate(
        credential: DevelopmentIdentityHeader
    ): Either[AuthenticationFailure, AuthenticatedPrincipal] =
      credential.userId match {
        case None => Left(AuthenticationFailure.MissingCredential)
        case Some(value) =>
          DevelopmentTrustBoundary
            .validateIdentifier(value, s"header:$HeaderName")
            .left.map(error =>
              AuthenticationFailure.InvalidCredential(error.message))
            .map(valid => AuthenticatedUser(UserId(valid)))
      }
  }
}
