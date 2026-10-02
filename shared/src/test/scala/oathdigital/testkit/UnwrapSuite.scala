package oathdigital.testkit

import Unwrap.*

/** The accessors return the value a test expects and, when the result went
  * the other way, fail with the value it got. */
class UnwrapSuite extends munit.FunSuite:
  private def failure(body: => Any): String =
    intercept[munit.FailException](body).getMessage

  test("accepted returns a Right and fails naming the Left"):
    assertEquals(accepted(Right(3)), 3)
    assert(failure(accepted(Left("no wealth here"))).contains("no wealth here"))

  test("rejected returns a Left and fails naming the Right"):
    assertEquals(rejected(Left("no wealth here")), "no wealth here")
    assert(failure(rejected(Right(3))).contains("Right(3)"))

  test("present returns the value and fails saying what was missing"):
    assertEquals(present(Some(3), "a stored game"), 3)
    assert(failure(present(None, "a stored game")).contains("a stored game"))

  test("as returns the case and fails naming the actual value"):
    val value: Either[String, Int] = Right(3)
    assertEquals(as[Right[String, Int]](value), Right(3))
    assert(failure(as[Left[String, Int]](value)).contains("Right(3)"))
