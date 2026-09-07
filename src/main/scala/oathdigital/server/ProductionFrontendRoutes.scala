package oathdigital.server

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object ProductionFrontendRoutes {
  private val IndexCacheControl = RawHeader("Cache-Control", "no-cache")
  private val AssetCacheControl = RawHeader(
    "Cache-Control",
    "public, max-age=31536000, immutable"
  )

  val route: Route =
    pathEndOrSingleSlash {
      respondWithHeader(IndexCacheControl) {
        getFromResource("oathdigital/frontend/index.html")
      }
    } ~
      pathPrefix("assets") {
        respondWithHeader(AssetCacheControl) {
          path("main.js") {
            getFromResource("oathdigital/frontend/main.js")
          } ~
            path("styles.css") {
              getFromResource("oathdigital/frontend/styles.css")
            }
        }
      }
}
