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
            "intent" -> ujson.Obj("type" -> "beginRecover"),
            "orderedModifiers" -> value)).map(_.orderedModifiers)
        }
      } yield MajorActionPreviewRequest(sequence, action, parameters, modifiers)
      case _ => Left(ExpectedObject("$"))
    } catch { case NonFatal(error) => Left(MalformedJson("$",
      Option(error.getMessage).getOrElse("malformed JSON"))) }

  def encode(value: MajorActionPreviewResponse): String = ujson.write(ujson.Obj(
    "nextSequence" -> value.nextSequence,
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

  private def nonEmpty(obj: ujson.Obj, key: String, path: String) =
    obj.value.get(key) match {
      case Some(ujson.Str(value)) if value.nonEmpty => Right(value)
      case _ => Left(InvalidValue(path, "expected non-empty string"))
    }
}
