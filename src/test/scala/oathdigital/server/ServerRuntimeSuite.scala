package oathdigital.server

import java.nio.file.{Files, Paths}

import oathdigital.application.TrustedSeat
import oathdigital.protocol._

class ServerRuntimeSuite extends munit.FunSuite {
  private def request(gameId: String) = TrustedGameCreateRequest(gameId, Vector(
    BootstrapParticipantRequest("p1", "l1", "red"),
    BootstrapParticipantRequest("p2", "l2", "blue"),
    BootstrapParticipantRequest("p3", "l3", "yellow")))

  test("trusted-game provisioning draws a randomized board, not the fixed dev one") {
    val runtime = ServerRuntime.open(
      Files.createTempDirectory("server-runtime-wiring-").resolve("database"),
      Paths.get("docs/catalog/new-foundations-component-catalog.json")
    ).toOption.get
    try {
      def siteOrder(gameId: String): Vector[String] = {
        assert(runtime.trustedGameProvisioning
          .create(request(gameId), "https://games.example.test").isRight)
        val seat = TrustedSeat(gameId, "p2")
        val projection = runtime.trustedGame.load(gameId, seat).toOption.get
        projection.world.flatMap(_.sites).map(_.siteId)
      }
      val orders = Vector("wiring-a", "wiring-b", "wiring-c", "wiring-d").map(siteOrder)
      assert(orders.distinct.size > 1,
        "four separately provisioned trusted games should not share one fixed board")
    } finally runtime.close()
  }
}
