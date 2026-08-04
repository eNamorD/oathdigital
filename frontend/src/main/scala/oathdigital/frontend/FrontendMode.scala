package oathdigital.frontend

import scala.scalajs.js

sealed trait FrontendMode
object FrontendMode {
  case object Server extends FrontendMode
  case object LocalDebug extends FrontendMode

  def fromSearch(search: String): FrontendMode =
    queryParameter(search, "mode").map(_.toLowerCase) match {
      case Some("local") => LocalDebug
      case _ => Server
    }

  def queryParameter(search: String, name: String): Option[String] =
    search.stripPrefix("?").split("&").toVector.flatMap { pair =>
      pair.split("=", 2).toVector match {
        case Vector(key, value) if key == name =>
          Some(js.URIUtils.decodeURIComponent(value))
        case _ => None
      }
    }.headOption.filter(_.nonEmpty)
}
