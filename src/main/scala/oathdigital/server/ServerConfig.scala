package oathdigital.server

import java.net.URI
import java.nio.file.{Path, Paths}

import scala.util.Try
import scala.util.control.NonFatal

sealed trait ServerMode extends Product with Serializable

object ServerMode {
  case object Development extends ServerMode
  case object TrustedAlpha extends ServerMode
}

final case class ServerConfig(
    host: String,
    port: Int,
    publicBaseUrl: Option[URI],
    databasePath: Path,
    catalogPath: Path,
    mode: ServerMode,
    authenticatedRouteMount: Option[AuthenticatedRouteMountConfiguration],
    version: String
)

object ServerConfig {
  private val DefaultHost = "127.0.0.1"
  private val DefaultPort = "8080"
  private val DefaultDatabasePath = "var/oathdigital"
  private val DefaultCatalogPath =
    "docs/catalog/new-foundations-component-catalog.json"
  private val DefaultMode = "development"

  private val SupportedOptionOrder = Vector(
    "--host",
    "--port",
    "--public-base-url",
    "--session-cookie-name",
    "--authenticated-public-origin",
    "--database-path",
    "--catalog-path",
    "--mode"
  )
  private val SupportedOptions = SupportedOptionOrder.toSet
  private val OptionOrder = SupportedOptionOrder.zipWithIndex.toMap

  val usage: String =
    "Usage: oathdigital [--host HOST] [--port PORT] " +
      "[--public-base-url URL] [--session-cookie-name NAME] " +
      "[--authenticated-public-origin ORIGIN] [--database-path PATH] " +
      "[--catalog-path PATH] [--mode development|trusted-alpha]. " +
      "Internet exposure requires HTTPS at a trusted reverse proxy."

  def parse(
      arguments: Array[String],
      environment: Map[String, String],
      version: String
  ): Either[Vector[String], ServerConfig] = {
    val (cli, argumentErrors) = parseArguments(arguments.toVector)

    val host = parseHost(value(
      "--host", "OATH_HOST", DefaultHost, cli, environment
    ))
    val port = parsePort(value(
      "--port", "OATH_PORT", DefaultPort, cli, environment
    ))
    val publicBaseUrl = parseOptionalUri(optionalValue(
      "--public-base-url", "OATH_PUBLIC_BASE_URL", cli, environment
    ))
    val authenticatedRouteMount = parseAuthenticatedRouteMount(
      optionalValue(
        "--session-cookie-name",
        "OATH_SESSION_COOKIE_NAME",
        cli,
        environment
      ),
      optionalValue(
        "--authenticated-public-origin",
        "OATH_AUTHENTICATED_PUBLIC_ORIGIN",
        cli,
        environment
      )
    )
    val databasePath = parsePath(
      "--database-path",
      value(
        "--database-path",
        "OATH_DATABASE_PATH",
        DefaultDatabasePath,
        cli,
        environment
      )
    )
    val catalogPath = parsePath(
      "--catalog-path",
      value(
        "--catalog-path",
        "OATH_CATALOG_PATH",
        DefaultCatalogPath,
        cli,
        environment
      )
    )
    val mode = parseMode(value(
      "--mode", "OATH_MODE", DefaultMode, cli, environment
    ))

    val validationErrors = Vector(
      "--host" -> host.left.toOption,
      "--port" -> port.left.toOption,
      "--public-base-url" -> publicBaseUrl.left.toOption,
      "--database-path" -> databasePath.left.toOption,
      "--catalog-path" -> catalogPath.left.toOption,
      "--mode" -> mode.left.toOption
    ).collect { case (option, Some(error)) => option -> error } ++
      authenticatedRouteMount.left.toOption.toVector

    val trustBoundaryErrors = (host, publicBaseUrl, mode) match {
      case (Right(validHost), Right(baseUrl), Right(validMode)) =>
        validateTrustBoundary(validHost, baseUrl, validMode).toVector
      case _ => Vector.empty
    }
    val errors = (argumentErrors ++ validationErrors ++ trustBoundaryErrors)
      .zipWithIndex
      .sortBy { case ((option, _), sequence) =>
        OptionOrder.getOrElse(option, SupportedOptionOrder.size) -> sequence
      }
      .map(_._1._2)

    if (errors.nonEmpty) Left(errors)
    else
      Right(ServerConfig(
        host.toOption.get,
        port.toOption.get,
        publicBaseUrl.toOption.get,
        databasePath.toOption.get,
        catalogPath.toOption.get,
        mode.toOption.get,
        authenticatedRouteMount.toOption.get,
        version
      ))
  }

  private def parseArguments(
      remaining: Vector[String],
      values: Map[String, String] = Map.empty,
      errors: Vector[(String, String)] = Vector.empty
  ): (Map[String, String], Vector[(String, String)]) =
    if (remaining.isEmpty) (values, errors)
    else {
      val argument = remaining.head
      val hasValue = remaining.lengthCompare(1) > 0 &&
        !remaining(1).startsWith("--")
      if (SupportedOptions.contains(argument) && hasValue)
        parseArguments(
          remaining.drop(2),
          values.updated(argument, remaining(1)),
          errors
        )
      else if (SupportedOptions.contains(argument))
        parseArguments(
          remaining.tail,
          values,
          errors :+ argument -> s"missing value for $argument. $usage"
        )
      else if (argument.startsWith("--"))
        parseArguments(
          remaining.drop(if (hasValue) 2 else 1),
          values,
          errors :+ argument -> s"unknown option $argument. $usage"
        )
      else
        parseArguments(
          remaining.tail,
          values,
          errors :+ argument -> s"unknown argument $argument. $usage"
        )
    }

  private def value(
      option: String,
      environmentName: String,
      default: String,
      cli: Map[String, String],
      environment: Map[String, String]
  ): String = cli.get(option).orElse(environment.get(environmentName))
    .getOrElse(default)

  private def optionalValue(
      option: String,
      environmentName: String,
      cli: Map[String, String],
      environment: Map[String, String]
  ): Option[String] = cli.get(option).orElse(environment.get(environmentName))

  private def parseHost(value: String): Either[String, String] = {
    val host = value.trim
    val valid = host.nonEmpty && !host.exists(_.isWhitespace) &&
      !host.exists("/?#@".contains(_)) &&
      Try(new URI("http", null, host, -1, null, null, null))
        .toOption.exists(uri => Option(uri.getHost).exists(_.nonEmpty))
    Either.cond(
      valid,
      host,
      "--host: must be a valid non-blank host"
    )
  }

  private def parsePort(value: String): Either[String, Int] =
    Try(value.trim.toInt).toOption
      .filter(port => port >= 1 && port <= 65535)
      .toRight("--port: must be an integer from 1 through 65535")

  private def parseOptionalUri(
      value: Option[String]
  ): Either[String, Option[URI]] = value match {
    case None => Right(None)
    case Some(raw) => parsePublicBaseUrl(raw).map(Some(_))
  }

  private def parseAuthenticatedRouteMount(
      sessionCookieName: Option[String],
      publicOrigin: Option[String]
  ): Either[(String, String), Option[AuthenticatedRouteMountConfiguration]] =
    AuthenticatedRouteMountConfiguration
      .fromOptions(sessionCookieName, publicOrigin)
      .left.map { message =>
        val option =
          if (sessionCookieName.forall(_.trim.isEmpty))
            "--session-cookie-name"
          else "--authenticated-public-origin"
        option -> s"$option: $message"
      }
      .flatMap {
        case Some(configuration) =>
          SameOriginCsrfProtection
            .validateOrigin(configuration.publicOrigin)
            .left.map(message =>
              "--authenticated-public-origin" ->
                s"--authenticated-public-origin: $message"
            )
            .map(_ => Some(configuration))
        case None => Right(None)
      }

  private def parsePublicBaseUrl(value: String): Either[String, URI] = {
    val parsed = Try(new URI(value.trim)).toOption
    parsed.filter { uri =>
      val scheme = Option(uri.getScheme).map(_.toLowerCase)
      uri.isAbsolute && !uri.isOpaque &&
        scheme.exists(Set("http", "https").contains) &&
        Option(uri.getHost).exists(_.nonEmpty) &&
        uri.getPort >= -1 && uri.getPort <= 65535 &&
        uri.getRawUserInfo == null && uri.getRawQuery == null &&
        uri.getRawFragment == null &&
        Option(uri.getRawPath).forall(_.isEmpty)
    }.toRight(
      "--public-base-url: must be an absolute HTTP or HTTPS origin " +
        "without credentials, path, query, or fragment"
    )
  }

  private def parsePath(
      option: String,
      value: String
  ): Either[String, Path] =
    if (value.trim.isEmpty) Left(s"$option: must not be blank")
    else
      try Right(Paths.get(value).toAbsolutePath.normalize)
      catch {
        case NonFatal(_) => Left(s"$option: must be a valid path")
      }

  private def parseMode(value: String): Either[String, ServerMode] =
    value.trim match {
      case "development" => Right(ServerMode.Development)
      case "trusted-alpha" => Right(ServerMode.TrustedAlpha)
      case _ => Left(
        "--mode: must be development or trusted-alpha"
      )
    }

  private def validateTrustBoundary(
      host: String,
      publicBaseUrl: Option[URI],
      mode: ServerMode
  ): Option[(String, String)] = mode match {
    case ServerMode.Development =>
      DevelopmentTrustBoundary.validateLoopbackHost(host).left.toOption
        .map(message => "--host" -> s"--host: $message")
    case ServerMode.TrustedAlpha
        if !isLoopback(host) && publicBaseUrl.isEmpty =>
      Some("--public-base-url" ->
        "--public-base-url: required for non-loopback trusted-alpha binding"
      )
    case ServerMode.TrustedAlpha => None
  }

  private def isLoopback(host: String): Boolean =
    Set("127.0.0.1", "localhost", "::1").contains(host.toLowerCase)
}
