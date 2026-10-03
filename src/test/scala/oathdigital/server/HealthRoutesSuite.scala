package oathdigital.server

import java.net.http.HttpResponse

import oathdigital.testkit.HttpTestServer
import oathdigital.testkit.HttpTestServer.cacheControl

class HealthRoutesSuite extends munit.FunSuite:
  test("liveness stays live while readiness follows lifecycle state"):
    val readiness = ServerReadiness.starting("test-version")
    val server = HttpTestServer.start("health-routes-test")(_ =>
      HealthRoutes.route(readiness))

    try
      assertHealth(
        server.get("/health/live"),
        200,
        "{\"status\":\"live\",\"version\":\"test-version\"}"
      )
      assertHealth(
        server.get("/health/ready"),
        503,
        "{\"status\":\"not-ready\",\"version\":\"test-version\"}"
      )

      readiness.markReady()
      assertHealth(
        server.get("/health/ready"),
        200,
        "{\"status\":\"ready\",\"version\":\"test-version\"}"
      )
      assertHealth(
        server.get("/health"),
        200,
        "{\"status\":\"ready\",\"version\":\"test-version\"}"
      )

      readiness.markStopping()
      assertHealth(
        server.get("/health/live"),
        200,
        "{\"status\":\"live\",\"version\":\"test-version\"}"
      )
      assertHealth(
        server.get("/health/ready"),
        503,
        "{\"status\":\"not-ready\",\"version\":\"test-version\"}"
      )
    finally server.close()

  private def assertHealth(
      response: HttpResponse[String],
      status: Int,
      body: String
  ): Unit =
    assertEquals(response.statusCode(), status)
    assertEquals(response.body(), body)
    assertEquals(cacheControl(response), Some("no-store"))
