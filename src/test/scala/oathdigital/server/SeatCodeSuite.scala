package oathdigital.server

import java.security.SecureRandom
import java.util.Base64

import oathdigital.application.SeatCodeDigest

class SeatCodeSuite extends munit.FunSuite {
  test("generated seat codes are unpadded URL-safe encodings of 128 random bits") {
    val code = SeatCode.generate(randomBytes(0 to 15))

    assertEquals(Base64.getUrlDecoder.decode(code.raw).length, 16)
    assert(code.raw.matches("[A-Za-z0-9_-]{22}"))
    assert(!code.raw.contains("="))
    assert(!code.toString.contains(code.raw))
  }

  test("distinct random bytes produce distinct seat codes") {
    val first = SeatCode.generate(randomBytes(0 to 15))
    val second = SeatCode.generate(randomBytes(1 to 16))

    assertNotEquals(first.raw, second.raw)
  }

  test("malformed and non-22-character seat codes fail with one generic error") {
    Vector("", "short", "a" * 21, "a" * 23, "a" * 21 + "=", "a" * 21 + "+", null)
      .foreach(raw => assertEquals(SeatCode.parse(raw), Left("invalid seat code")))
  }

  test("identical raw seat codes produce identical 32-byte digests") {
    val raw = SeatCode.generate(randomBytes(0 to 15)).raw
    val first = SeatCode.parse(raw).toOption.get
    val second = SeatCode.parse(raw).toOption.get

    assertEquals(first.digest, second.digest)
    assertEquals(first.digest.bytes.length, 32)
    assertEquals(
      SeatCodeDigest.fromBytes(Vector.fill(31)(0.toByte)),
      Left("seat code digest must contain exactly 32 bytes")
    )
  }

  private def randomBytes(values: Seq[Int]): SecureRandom =
    new SecureRandom {
      override def nextBytes(bytes: Array[Byte]): Unit =
        values.map(_.toByte).copyToArray(bytes)
    }
}
