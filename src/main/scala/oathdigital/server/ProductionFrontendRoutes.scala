package oathdigital.server

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object ProductionFrontendRoutes {
  private val IndexCacheControl = RawHeader("Cache-Control", "no-cache")
  // Assets are served at fixed, unfingerprinted paths, so a long-lived
  // `immutable` policy would strand upgraded clients on a previous bundle.
  // Revalidate every request and let `getFromResource`'s ETag/Last-Modified
  // keep the response cheap until fingerprinted names land.
  private val AssetCacheControl = RawHeader(
    "Cache-Control",
    "public, max-age=0, must-revalidate"
  )

  val gamePage: Route =
    respondWithHeader(IndexCacheControl) {
      getFromResource("oathdigital/frontend/index.html")
    }

  val route: Route =
    pathEndOrSingleSlash {
      gamePage
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
