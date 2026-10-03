package oathdigital.server

import oathdigital.application.{
  AuthenticatedUser,
  AuthenticationFailure,
  UserId
}
import oathdigital.server.DevelopmentIdentityShimError.NonLoopbackBinding
import oathdigital.testkit.Unwrap.{accepted, as, present, rejected}

class DevelopmentIdentityShimSuite extends munit.FunSuite:
  test("shim is absent unless explicitly enabled"):
    assertEquals(
      DevelopmentIdentityShim.configure(false, "127.0.0.1"),
      Right(None)
    )

  test("enabled shim accepts loopback identity and validates its header"):
    val authenticator = present(
      accepted(DevelopmentIdentityShim.configure(true, "127.0.0.1")),
      "an enabled shim")
    assertEquals(
      authenticator.authenticate(DevelopmentIdentityHeader(Some("user-1"))),
      Right(AuthenticatedUser(UserId("user-1")))
    )
    assertEquals(
      authenticator.authenticate(DevelopmentIdentityHeader(None)),
      Left(AuthenticationFailure.MissingCredential)
    )
    as[AuthenticationFailure.InvalidCredential](rejected(
      authenticator.authenticate(
        DevelopmentIdentityHeader(Some("../../impersonate")))))

  test("enabled shim refuses a wildcard binding"):
    // The host list is DevelopmentTrustBoundarySuite's; one shows the check
    // applies.
    as[NonLoopbackBinding](
      rejected(DevelopmentIdentityShim.configure(true, "0.0.0.0")))
