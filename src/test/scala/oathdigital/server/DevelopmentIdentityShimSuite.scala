package oathdigital.server

import oathdigital.application.{
  AuthenticatedUser,
  AuthenticationFailure,
  UserId
}
import oathdigital.server.DevelopmentIdentityShimError.NonLoopbackBinding

class DevelopmentIdentityShimSuite extends munit.FunSuite {
  test("shim is absent unless explicitly enabled") {
    assertEquals(
      DevelopmentIdentityShim.configure(false, "127.0.0.1"),
      Right(None)
    )
  }

  test("enabled shim accepts loopback identity and validates its header") {
    val authenticator = DevelopmentIdentityShim
      .configure(true, "127.0.0.1").toOption.flatten.get
    assertEquals(
      authenticator.authenticate(DevelopmentIdentityHeader(Some("user-1"))),
      Right(AuthenticatedUser(UserId("user-1")))
    )
    assertEquals(
      authenticator.authenticate(DevelopmentIdentityHeader(None)),
      Left(AuthenticationFailure.MissingCredential)
    )
    assert(authenticator.authenticate(
      DevelopmentIdentityHeader(Some("../../impersonate"))
    ).left.toOption.get.isInstanceOf[AuthenticationFailure.InvalidCredential])
  }

  test("enabled shim refuses wildcard and non-loopback bindings") {
    Vector("0.0.0.0", "192.168.1.20", "example.com").foreach { host =>
      assert(DevelopmentIdentityShim.configure(true, host)
        .left.toOption.get.isInstanceOf[NonLoopbackBinding])
    }
  }
}
