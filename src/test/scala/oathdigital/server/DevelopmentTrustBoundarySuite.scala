package oathdigital.server

class DevelopmentTrustBoundarySuite extends munit.FunSuite {
  test("development server binding accepts only explicit loopback hosts") {
    Vector("127.0.0.1", "localhost", "::1").foreach { host =>
      assert(DevelopmentTrustBoundary.validateLoopbackHost(host).isRight)
    }
    Vector("0.0.0.0", "192.168.1.10", "example.com", "").foreach { host =>
      val error = DevelopmentTrustBoundary
        .validateLoopbackHost(host).left.toOption.get
      assert(error.contains("loopback"))
    }
  }

  test("development identifiers enforce length and safe characters") {
    assert(DevelopmentTrustBoundary
      .validateIdentifier("game-1:branch_a", "$.gameId").isRight)
    assertEquals(
      DevelopmentTrustBoundary
        .validateIdentifier(" ", "$.gameId").left.toOption.get.path,
      "$.gameId"
    )
    assert(DevelopmentTrustBoundary.validateIdentifier(
      "x" * (DevelopmentTrustBoundary.MaximumIdentifierLength + 1),
      "$.playerId"
    ).isLeft)
    assert(DevelopmentTrustBoundary
      .validateIdentifier("player/../../bad", "$.playerId").isLeft)
  }
}
