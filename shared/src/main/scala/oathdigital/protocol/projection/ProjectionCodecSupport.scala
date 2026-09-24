package oathdigital.protocol.projection

import oathdigital.protocol.ProtocolDecodeFailure
import oathdigital.protocol.ProtocolDecodeFailure._

private[protocol] object ProjectionCodecSupport {
  type Result[A] = Either[ProtocolDecodeFailure, A]
  val MaxSafeInteger = 9007199254740991d

  def obj(value: ujson.Value, path: String): Result[ujson.Obj] = value match {
    case value: ujson.Obj => Right(value)
    case _ => Left(ExpectedObject(path))
  }
  def exact(value: ujson.Obj, fields: Set[String], path: String): Result[Unit] =
    value.value.keys.find(!fields.contains(_))
      .map(key => Left(UnexpectedField(s"$path.$key"))).getOrElse(Right(()))
  def field(value: ujson.Obj, name: String, path: String): Result[ujson.Value] =
    value.value.get(name).toRight(MissingField(s"$path.$name"))
  def default[A](value: ujson.Obj, name: String, path: String, fallback: A)
      (decode: (ujson.Value, String) => Result[A]): Result[A] =
    value.value.get(name).fold[Result[A]](Right(fallback))(
      decode(_, s"$path.$name"))
  def string(value: ujson.Value, path: String): Result[String] = value match {
    case ujson.Str(text) => Right(text)
    case _ => Left(InvalidValue(path, "expected string"))
  }
  def string(value: ujson.Obj, name: String, path: String): Result[String] =
    field(value, name, path).flatMap(string(_, s"$path.$name"))
  def playerColor(value: ujson.Obj, name: String, path: String)
      : Result[oathdigital.model.PlayerColor] =
    string(value, name, path).flatMap(key =>
      oathdigital.model.PlayerColor.fromKey(key).toRight(
        InvalidValue(s"$path.$name", s"unknown player color '$key'")))
  def int(value: ujson.Value, path: String): Result[Int] = value match {
    case ujson.Num(number) if number.isWhole && number >= Int.MinValue &&
        number <= Int.MaxValue => Right(number.toInt)
    case _ => Left(InvalidValue(path, "expected integer"))
  }
  def int(value: ujson.Obj, name: String, path: String): Result[Int] =
    field(value, name, path).flatMap(int(_, s"$path.$name"))
  def long(value: ujson.Obj, name: String, path: String): Result[Long] =
    field(value, name, path).flatMap {
      case ujson.Num(number) if number.isWhole && number >= 0 &&
          number <= MaxSafeInteger => Right(number.toLong)
      case _ => Left(InvalidValue(s"$path.$name",
        "expected a non-negative safe integer"))
    }
  def bool(value: ujson.Value, path: String): Result[Boolean] = value match {
    case ujson.Bool(flag) => Right(flag)
    case _ => Left(InvalidValue(path, "expected boolean"))
  }
  def bool(value: ujson.Obj, name: String, path: String): Result[Boolean] =
    field(value, name, path).flatMap(bool(_, s"$path.$name"))
  def array(value: ujson.Value, path: String): Result[Vector[ujson.Value]] = value match {
    case value: ujson.Arr => Right(value.value.toVector)
    case _ => Left(InvalidValue(path, "expected array"))
  }
  def array(value: ujson.Obj, name: String, path: String): Result[Vector[ujson.Value]] =
    field(value, name, path).flatMap(array(_, s"$path.$name"))
  def strings(value: ujson.Obj, name: String, path: String): Result[Vector[String]] =
    array(value, name, path).flatMap(traverse(_, s"$path.$name")(string))
  def stringsOrEmpty(value: ujson.Obj, name: String, path: String): Result[Vector[String]] =
    default(value, name, path, Vector.empty[String])((raw, child) =>
      array(raw, child).flatMap(traverse(_, child)(string)))
  def optional[A](value: ujson.Obj, name: String, path: String)
      (decode: (ujson.Value, String) => Result[A]): Result[Option[A]] =
    field(value, name, path).flatMap {
      case ujson.Null => Right(None)
      case raw => decode(raw, s"$path.$name").map(Some(_))
    }
  def optionalAbsent[A](value: ujson.Obj, name: String, path: String)
      (decode: (ujson.Value, String) => Result[A]): Result[Option[A]] =
    value.value.get(name) match {
      case None | Some(ujson.Null) => Right(None)
      case Some(raw) => decode(raw, s"$path.$name").map(Some(_))
    }
  def intOr(value: ujson.Obj, name: String, path: String, fallback: Int) =
    default(value, name, path, fallback)(int)
  def boolOr(value: ujson.Obj, name: String, path: String, fallback: Boolean) =
    default(value, name, path, fallback)(bool)
  def optionalString(value: ujson.Obj, name: String, path: String) =
    optional(value, name, path)(string)
  def optionalInt(value: ujson.Obj, name: String, path: String) =
    optional(value, name, path)(int)
  def traverse[A, B](values: Vector[A], path: String)
      (decode: (A, String) => Result[B]): Result[Vector[B]] =
    values.zipWithIndex.foldLeft[Result[Vector[B]]](Right(Vector.empty)) {
      case (Right(done), (value, index)) => decode(value, s"$path[$index]").map(done :+ _)
      case (failure @ Left(_), _) => failure
    }
  def encoded[A](values: Vector[A])(encode: A => ujson.Value): ujson.Arr =
    ujson.Arr.from(values.map(encode))
  def option[A](value: Option[A])(encode: A => ujson.Value): ujson.Value =
    value.fold[ujson.Value](ujson.Null)(encode)
  def stringOption(value: Option[String]): ujson.Value = option(value)(ujson.Str(_))
  def intOption(value: Option[Int]): ujson.Value = option(value)(v => ujson.Num(v))
}
