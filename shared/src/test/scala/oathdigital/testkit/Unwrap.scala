package oathdigital.testkit

import scala.reflect.ClassTag

/** Reads of a result a test expects to have gone one way. Each fails the
  * test with the value it got instead, never with a bare
  * NoSuchElementException, MatchError or ClassCastException. The root,
  * frontend and shared suites all use them: this file compiles into both
  * test trees.
  *
  * One name per shape: a Right is `accepted`, a Left is `rejected`, an
  * Option's value is `present`, and a case of a sealed type is `as`. */
object Unwrap:
  /** The Right of `result`; fails with the Left. */
  def accepted[L, R](result: Either[L, R])(using munit.Location): R =
    result match
      case Right(value) => value
      case Left(refusal) =>
        munit.Assertions.fail(s"expected a Right, got Left($refusal)")

  /** The Left of `result`; fails with the Right. */
  def rejected[L, R](result: Either[L, R])(using munit.Location): L =
    result match
      case Left(refusal) => refusal
      case Right(value) =>
        munit.Assertions.fail(s"expected a Left, got Right($value)")

  /** The value in `option`; fails saying `what` was expected. */
  def present[A](option: Option[A], what: String)(using munit.Location): A =
    option.getOrElse(munit.Assertions.fail(s"expected $what, got None"))

  /** `value` as the case `T`; fails with the value's actual case. */
  def as[T](value: Any)(using tag: ClassTag[T], location: munit.Location): T =
    value match
      case tag(matched) => matched
      case other => munit.Assertions.fail(
        s"expected a ${tag.runtimeClass.getSimpleName}, got $other")
