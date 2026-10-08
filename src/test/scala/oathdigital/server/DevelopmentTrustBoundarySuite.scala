package oathdigital.server

import oathdigital.testkit.Unwrap.rejected

class DevelopmentTrustBoundarySuite extends munit.FunSuite:
  test("development server binding accepts only explicit loopback hosts"):
    Vector("127.0.0.1", "localhost", "::1").foreach { host =>
      assertEquals(DevelopmentTrustBoundary.validateLoopbackHost(host),
        Right(host))
    }
    Vector("0.0.0.0", "192.168.1.10", "example.com", "").foreach { host =>
      assertEquals(
        rejected(DevelopmentTrustBoundary.validateLoopbackHost(host)),
        "unauthenticated development routes require a loopback host " +
          "(127.0.0.1, localhost, or ::1)", host)
    }

  test("development identifiers enforce length and safe characters"):
    assertEquals(DevelopmentTrustBoundary
      .validateIdentifier("game-1:branch_a", "$.gameId"), Right("game-1:branch_a"))
    assertEquals(
      DevelopmentTrustBoundary.validateIdentifier(" ", "$.gameId"),
      Left(HttpInputError("$.gameId", "must not be blank"))
    )
    assertEquals(DevelopmentTrustBoundary.validateIdentifier(
      "x" * (DevelopmentTrustBoundary.MaximumIdentifierLength + 1),
      "$.playerId"
    ), Left(HttpInputError("$.playerId",
      s"must be at most ${DevelopmentTrustBoundary.MaximumIdentifierLength} " +
        "characters")))
    assertEquals(DevelopmentTrustBoundary
      .validateIdentifier("player/../../bad", "$.playerId"),
      Left(HttpInputError("$.playerId", "contains unsupported characters")))
