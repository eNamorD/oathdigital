package oathdigital.server

import oathdigital.testkit.HttpTestServer
import oathdigital.testkit.HttpTestServer.cacheControl

class ProductionFrontendRoutesSuite extends munit.FunSuite:
  test("serves the packaged shell and assets with production cache policy"):
    val server = HttpTestServer.start("production-frontend-routes-test")(_ =>
      ProductionFrontendRoutes.route)

    try
      val index = server.get("/")
      assertEquals(index.statusCode(), 200)
      assert(index.body().contains("/assets/main.js"))
      assertEquals(cacheControl(index), Some("no-cache"))

      val javascript = server.get("/assets/main.js")
      assertEquals(javascript.statusCode(), 200)
      assert(javascript.body().nonEmpty)
      assertEquals(
        cacheControl(javascript),
        Some("public, max-age=0, must-revalidate")
      )

      val stylesheet = server.get("/assets/styles.css")
      assertEquals(stylesheet.statusCode(), 200)
      assert(stylesheet.body().nonEmpty)
      assertEquals(
        cacheControl(stylesheet),
        Some("public, max-age=0, must-revalidate")
      )

      val font = server.get(
        "/assets/fonts/atkinson-hyperlegible-next-latin-wght-normal.woff2")
      assertEquals(font.statusCode(), 200)
      assert(font.body().nonEmpty)
      assertEquals(
        cacheControl(font),
        Some("public, max-age=0, must-revalidate")
      )

      assertEquals(server.get("/missing.js").statusCode(), 404)
      assertEquals(server.get("/assets/missing.js").statusCode(), 404)
      assertEquals(
        server.get("/assets/fonts/missing.woff2").statusCode(), 404)
    finally server.close()

