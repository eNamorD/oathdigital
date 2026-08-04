package oathdigital.server

object DevelopmentTrustBoundary {
  val MaximumIdentifierLength: Int = 128
  private val SafeIdentifier = "^[A-Za-z0-9][A-Za-z0-9._:-]*$".r

  def validateIdentifier(
      value: String,
      path: String
  ): Either[HttpInputError, String] =
    if (value.trim.isEmpty)
      Left(HttpInputError(path, "must not be blank"))
    else if (value.length > MaximumIdentifierLength)
      Left(HttpInputError(
        path,
        s"must be at most $MaximumIdentifierLength characters"
      ))
    else if (SafeIdentifier.findFirstIn(value).isEmpty)
      Left(HttpInputError(
        path,
        "contains unsupported characters"
      ))
    else Right(value)

  def validateLoopbackHost(host: String): Either[String, String] =
    host.trim.toLowerCase match {
      case "127.0.0.1" => Right("127.0.0.1")
      case "localhost" => Right("localhost")
      case "::1" => Right("::1")
      case _ =>
        Left(
          "unauthenticated development routes require a loopback host " +
            "(127.0.0.1, localhost, or ::1)"
        )
    }
}
