package oathdigital.server

import oathdigital.model.PlayerColor

import java.nio.file.Files

import oathdigital.application.TrustedSeat
import oathdigital.protocol._
import oathdigital.testkit.Unwrap.accepted

class ServerRuntimeSuite extends munit.FunSuite:
  private val request = TrustedGameCreateRequest(Vector(
    BootstrapParticipantRequest("p1", PlayerColor.Red),
    BootstrapParticipantRequest("p2", PlayerColor.Blue),
    BootstrapParticipantRequest("p3", PlayerColor.Yellow)))

  test("trusted-game provisioning draws a randomized board"):
    val runtime = accepted(ServerRuntime.open(
      Files.createTempDirectory("server-runtime-wiring-").resolve("database")))
    try
      def siteOrder(): Vector[String] =
        val gameId = accepted(runtime.trustedGameProvisioning
          .create(request, "https://games.example.test")).gameId
        val seat = TrustedSeat(gameId, "p2")
        val projection = accepted(runtime.trustedGame.load(gameId, seat))
        projection.world.flatMap(_.sites).map(_.siteId)
      val orders = Vector.fill(4)(siteOrder())
      assert(orders.distinct.size > 1,
        "four separately provisioned trusted games should not share one fixed board")
    finally runtime.close()
