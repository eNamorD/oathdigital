package oathdigital.protocol

import scala.util.control.NonFatal

final case class MajorActionPreviewRequest(
    expectedNextSequence: Long,
    action: String,
    baseParameters: Map[String, String] = Map.empty,
    orderedModifiers: Vector[ModifierInvocation] = Vector.empty)
final case class PreviewModifier(sourceKey: String, handlerId: String,
    description: String)
final case class PreviewIgnoredRule(sourceKey: String, handlerId: String,
    timing: String, reason: String)
final case class PreviewTarget(key: String, supplyCost: Int, description: String)
final case class MajorActionPreviewResponse(nextSequence: Long, action: String,
    modifiers: Vector[PreviewModifier], ignoredRules: Vector[PreviewIgnoredRule],
    targets: Vector[PreviewTarget])

object MajorActionPreviewCodec {
  import ProtocolDecodeFailure._

  def encodeRequest(value: MajorActionPreviewRequest): String = {
    // `BeginForge` is used only as an arbitrary carrier intent to reuse the
    // shared envelope's ordered-modifiers encode/decode machinery, which
    // attaches to any `GameIntent` -- this preview covers every major
    // action, not just Forge (see `action` below, encoded separately).
    val envelope = ActorlessCommandRequest(value.expectedNextSequence,
      GameIntent.BeginForge, value.orderedModifiers)
    val encodedModifiers = ujson.read(ActorlessCommandCodec.encode(envelope))
      .obj.value.get("orderedModifiers").getOrElse(ujson.Arr())
    ujson.write(ujson.Obj("expectedNextSequence" -> ujson.Num(
        value.expectedNextSequence.toDouble),
      "action" -> value.action,
      "baseParameters" -> ujson.Obj.from(value.baseParameters.map {
        case (key, parameter) => key -> ujson.Str(parameter) }),
      "orderedModifiers" -> encodedModifiers))
  }

  def decode(json: String): Either[ProtocolDecodeFailure, MajorActionPreviewRequest] =
    try ujson.read(json) match {
      case root: ujson.Obj => for {
        sequence <- root.value.get("expectedNextSequence") match {
          case Some(ujson.Num(value)) if value.isWhole && value >= 0 => Right(value.toLong)
          case _ => Left(InvalidValue("$.expectedNextSequence",
            "expected non-negative integer"))
        }
        action <- nonEmpty(root, "action", "$.action")
        parameters <- root.value.get("baseParameters") match {
          case None => Right(Map.empty[String, String])
          case Some(value: ujson.Obj) => value.value.toVector.foldLeft[
            Either[ProtocolDecodeFailure, Map[String, String]]](Right(Map.empty)) {
            case (Right(acc), (key, ujson.Str(v))) => Right(acc.updated(key, v))
            case (Right(_), (key, _)) => Left(InvalidValue(
              s"$$.baseParameters.$key", "expected string"))
            case (left @ Left(_), _) => left
          }
          case _ => Left(InvalidValue("$.baseParameters", "expected object"))
        }
        modifiers <- root.value.get("orderedModifiers") match {
          case None => Right(Vector.empty)
          case Some(value) => ActorlessCommandCodec.decodeValue(ujson.Obj(
            "expectedNextSequence" -> ujson.Num(sequence.toDouble),
            "intent" -> ujson.Obj("type" -> "beginForge"),
            "orderedModifiers" -> value)).map(_.orderedModifiers)
        }
      } yield MajorActionPreviewRequest(sequence, action, parameters, modifiers)
      case _ => Left(ExpectedObject("$"))
    } catch { case NonFatal(error) => Left(MalformedJson("$",
      Option(error.getMessage).getOrElse("malformed JSON"))) }

  def encode(value: MajorActionPreviewResponse): String = ujson.write(ujson.Obj(
    "nextSequence" -> ujson.Num(value.nextSequence.toDouble),
    "action" -> value.action,
    "modifiers" -> ujson.Arr.from(value.modifiers.map(v => ujson.Obj(
      "sourceKey" -> v.sourceKey, "handlerId" -> v.handlerId,
      "description" -> v.description))),
    "ignoredRules" -> ujson.Arr.from(value.ignoredRules.map(v => ujson.Obj(
      "sourceKey" -> v.sourceKey, "handlerId" -> v.handlerId,
      "timing" -> v.timing, "reason" -> v.reason))),
    "targets" -> ujson.Arr.from(value.targets.map(v => ujson.Obj(
      "key" -> v.key, "supplyCost" -> v.supplyCost,
      "description" -> v.description)))))

  def decodeResponse(json: String)
      : Either[ProtocolDecodeFailure, MajorActionPreviewResponse] = try {
    ujson.read(json) match {
      case root: ujson.Obj => for {
        sequence <- root.value.get("nextSequence") match {
          case Some(ujson.Num(value)) if value.isWhole && value >= 0 => Right(value.toLong)
          case _ => Left(InvalidValue("$.nextSequence", "expected non-negative integer"))
        }
        action <- nonEmpty(root, "action", "$.action")
        modifiers <- array(root, "modifiers", "$.modifiers") { (value, path) => for {
          source <- nonEmpty(value, "sourceKey", s"$path.sourceKey")
          handler <- nonEmpty(value, "handlerId", s"$path.handlerId")
          description <- nonEmpty(value, "description", s"$path.description")
        } yield PreviewModifier(source, handler, description) }
        ignored <- array(root, "ignoredRules", "$.ignoredRules") { (value, path) => for {
          source <- nonEmpty(value, "sourceKey", s"$path.sourceKey")
          handler <- nonEmpty(value, "handlerId", s"$path.handlerId")
          timing <- nonEmpty(value, "timing", s"$path.timing")
          reason <- nonEmpty(value, "reason", s"$path.reason")
        } yield PreviewIgnoredRule(source, handler, timing, reason) }
        targets <- array(root, "targets", "$.targets") { (value, path) => for {
          key <- nonEmpty(value, "key", s"$path.key")
          cost <- value.value.get("supplyCost") match {
            case Some(ujson.Num(v)) if v.isWhole && v >= 0 => Right(v.toInt)
            case _ => Left(InvalidValue(s"$path.supplyCost", "expected non-negative integer"))
          }
          description <- nonEmpty(value, "description", s"$path.description")
        } yield PreviewTarget(key, cost, description) }
      } yield MajorActionPreviewResponse(sequence, action, modifiers, ignored, targets)
      case _ => Left(ExpectedObject("$"))
    }
  } catch { case NonFatal(error) => Left(MalformedJson("$",
    Option(error.getMessage).getOrElse("malformed JSON"))) }

  private def array[A](root: ujson.Obj, key: String, path: String)(
      decode: (ujson.Obj, String) => Either[ProtocolDecodeFailure, A]) =
    root.value.get(key) match {
      case Some(values: ujson.Arr) => values.value.toVector.zipWithIndex.foldLeft[
        Either[ProtocolDecodeFailure, Vector[A]]](Right(Vector.empty)) {
        case (Right(acc), (value: ujson.Obj, index)) =>
          decode(value, s"$path[$index]").map(acc :+ _)
        case (Right(_), (_, index)) => Left(ExpectedObject(s"$path[$index]"))
        case (failure @ Left(_), _) => failure
      }
      case _ => Left(InvalidValue(path, "expected array"))
    }

  private def nonEmpty(obj: ujson.Obj, key: String, path: String) =
    obj.value.get(key) match {
      case Some(ujson.Str(value)) if value.nonEmpty => Right(value)
      case _ => Left(InvalidValue(path, "expected non-empty string"))
    }
}
