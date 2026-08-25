package oathdigital.protocol

private[protocol] object CommandJsonSupport {
  import ProtocolDecodeFailure._

  def obj(value: ujson.Value, path: String): Either[ProtocolDecodeFailure, ujson.Obj] =
    value match { case value: ujson.Obj => Right(value); case _ => Left(ExpectedObject(path)) }

  def exact(value: ujson.Obj, fields: Set[String], path: String)
      : Either[ProtocolDecodeFailure, Unit] =
    value.value.keys.find(!fields.contains(_)).map { name =>
      if (Set("playerId", "actor", "actorId", "actorPlayerId").contains(name))
        Left(ActorInjection(s"$path.$name"))
      else Left(UnexpectedField(s"$path.$name"))
    }.getOrElse(Right(()))

  def field(value: ujson.Obj, name: String, path: String)
      : Either[ProtocolDecodeFailure, ujson.Value] =
    value.value.get(name).toRight(MissingField(s"$path.$name"))

  def string(value: ujson.Obj, name: String, path: String)
      : Either[ProtocolDecodeFailure, String] = field(value, name, path).flatMap {
    case ujson.Str(text) if text.trim.nonEmpty => Right(text)
    case ujson.Str(_) => Left(InvalidValue(s"$path.$name", "must not be blank"))
    case _ => Left(InvalidValue(s"$path.$name", "expected string"))
  }

  def integer(value: ujson.Value, path: String): Either[ProtocolDecodeFailure, Int] = value match {
    case ujson.Num(number) if number.isFinite && number == Math.rint(number) &&
        number >= 0 && number <= Int.MaxValue => Right(number.toInt)
    case _ => Left(InvalidValue(path, "expected a non-negative 32-bit integer"))
  }

  def boolean(value: ujson.Value, path: String): Either[ProtocolDecodeFailure, Boolean] =
    value match { case ujson.Bool(v) => Right(v); case _ => Left(InvalidValue(path, "expected boolean")) }

  def array(value: ujson.Value, path: String): Either[ProtocolDecodeFailure, Vector[ujson.Value]] =
    value match { case a: ujson.Arr => Right(a.value.toVector); case _ => Left(InvalidValue(path, "expected array")) }

  def strings(value: ujson.Value, path: String): Either[ProtocolDecodeFailure, Vector[String]] =
    array(value, path).flatMap(values => traverse(values.zipWithIndex) {
      case (ujson.Str(text), _) if text.trim.nonEmpty => Right(text)
      case (_, index) => Left(InvalidValue(s"$path[$index]", "expected non-blank string"))
    })

  def traverse[A, B](values: Vector[A])(f: A => Either[ProtocolDecodeFailure, B])
      : Either[ProtocolDecodeFailure, Vector[B]] =
    values.foldLeft[Either[ProtocolDecodeFailure, Vector[B]]](Right(Vector.empty)) {
      case (Right(acc), value) => f(value).map(acc :+ _)
      case (failure @ Left(_), _) => failure
    }

  def noDuplicates(values: Vector[String], path: String): Either[ProtocolDecodeFailure, Unit] =
    values.groupBy(identity).collectFirst { case (value, occurrences) if occurrences.size > 1 => value }
      .map(value => Left(InvalidValue(path, s"duplicate value '$value'"))).getOrElse(Right(()))

  def rejectActorFields(value: ujson.Obj, path: String): Either[ProtocolDecodeFailure, Unit] = {
    val forbidden = Set("actor", "actorId", "actorPlayerId") ++
      (if (path == "$.intent") Set("playerId") else Set.empty[String])
    value.value.keys.find(forbidden.contains)
      .map(name => Left(ActorInjection(s"$path.$name"))).getOrElse(Right(()))
  }
}
