package oathdigital.server

import java.nio.charset.StandardCharsets
import java.security.{MessageDigest, SecureRandom}
import java.util.Base64

import oathdigital.application.SeatCodeDigest

final class SeatCode private (val raw: String) {
  def digest: SeatCodeDigest =
    SeatCodeDigest.fromBytes(MessageDigest.getInstance("SHA-256").digest(
      raw.getBytes(StandardCharsets.UTF_8)
    ).toVector).toOption.get

  override def equals(other: Any): Boolean = other match {
    case that: SeatCode => raw == that.raw
    case _ => false
  }

  override def hashCode(): Int = raw.hashCode
}

object SeatCode {
  private val ByteLength = 16
  private val Pattern = "[A-Za-z0-9_-]{22}".r
  private val InvalidCode = "invalid seat code"

  def generate(random: SecureRandom): SeatCode = {
    val bytes = new Array[Byte](ByteLength)
    random.nextBytes(bytes)
    new SeatCode(Base64.getUrlEncoder.withoutPadding.encodeToString(bytes))
  }

  def parse(raw: String): Either[String, SeatCode] =
    if (raw == null || !Pattern.pattern.matcher(raw).matches()) Left(InvalidCode)
    else {
      try {
        val bytes = Base64.getUrlDecoder.decode(raw)
        if (bytes.length != ByteLength ||
            Base64.getUrlEncoder.withoutPadding.encodeToString(bytes) != raw)
          Left(InvalidCode)
        else Right(new SeatCode(raw))
      } catch {
        case _: IllegalArgumentException => Left(InvalidCode)
      }
    }
}
