package oathdigital.server

import oathdigital.model.PlayerColor

import java.nio.file.{Files, Paths}

import oathdigital.application.TrustedSeat
import oathdigital.protocol._

class ServerRuntimeSuite extends munit.FunSuite:
  private val request = TrustedGameCreateRequest(Vector(
    BootstrapParticipantRequest("p1", PlayerColor.Red),
    BootstrapParticipantRequest("p2", PlayerColor.Blue),
    BootstrapParticipantRequest("p3", PlayerColor.Yellow)))

  test("trusted-game provisioning draws a randomized board"):
    val runtime = ServerRuntime.open(
      Files.createTempDirectory("server-runtime-wiring-").resolve("database"),
      Paths.get("docs/catalog/new-foundations-component-catalog.json")
    ).toOption.get
    try
      def siteOrder(): Vector[String] =
        val gameId = runtime.trustedGameProvisioning
          .create(request, "https://games.example.test").toOption.get.gameId
        val seat = TrustedSeat(gameId, "p2")
        val projection = runtime.trustedGame.load(gameId, seat).toOption.get
        projection.world.flatMap(_.sites).map(_.siteId)
      val orders = Vector.fill(4)(siteOrder())
      assert(orders.distinct.size > 1,
        "four separately provisioned trusted games should not share one fixed board")
    finally runtime.close()
