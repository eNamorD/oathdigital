package oathdigital.serialization

import oathdigital.model.{DecisionAnswer, DecisionOptionRef, DecisionPlacement}
import oathdigital.serialization.WireError.InvalidValue

/** Journal wire form of a walker [[DecisionAnswer]].
  *
  * Extracted from `WalkerEventCodec.scala` for headroom: that file sits within
  * a handful of lines of the 800-line production cap, and a third decision
  * shape added there would fail the architecture check rather than the review.
  *
  * The encode side is exhaustive and cannot throw. That is new, and it is what
  * sealing [[DecisionAnswer]] bought: while the family was open, no match over
  * it could be complete, so the encoder carried a runtime `throw` for a case
  * its decode counterpart already rejected with a typed error. Both directions
  * now agree -- an unknown tag is an `InvalidValue`, and an unhandled answer
  * shape is a compile error.
  *
  * Both shapes spell an option reference as the kind/id pair
  * [[DecisionOptionRef.kind]] and [[DecisionOptionRef.wireId]] define, so the
  * seven variants are named once in the model rather than tabulated again
  * here, in the command protocol, and in the projection.
  */
private[serialization] object DecisionAnswerCodec {
  private val ChooseOneTag = "choose-one"
  private val PartitionTag = "partition"

  def encode(answer: DecisionAnswer): ujson.Value = answer match {
    case DecisionAnswer.ChooseOneAnswer(selected) => ujson.Obj(
      "kind" -> ChooseOneTag, "option" -> encodeRef(selected))
    case DecisionAnswer.PartitionAnswer(placements) => ujson.Obj(
      "kind" -> PartitionTag,
      "placements" -> ujson.Arr.from(placements.map(placement => ujson.Obj(
        "option" -> encodeRef(placement.option),
        "sectionKey" -> placement.sectionKey))))
  }

  def decode(value: ujson.Value,
      path: String): Either[WireError, DecisionAnswer] =
    value("kind").str match {
      case ChooseOneTag => decodeRef(value("option"), s"$path.option")
        .map(DecisionAnswer.ChooseOneAnswer)
      case PartitionTag =>
        traverse(value("placements").arr.toVector.zipWithIndex) {
          case (entry, index) =>
            val entryPath = s"$path.placements[$index]"
            decodeRef(entry("option"), s"$entryPath.option").map(
              DecisionPlacement(_, entry("sectionKey").str))
        }.map(DecisionAnswer.PartitionAnswer)
      case other => Left(InvalidValue(s"$path.kind",
        s"unknown walker decision answer '$other'"))
    }

  private def encodeRef(ref: DecisionOptionRef): ujson.Value =
    ujson.Obj("kind" -> ref.kind, "id" -> ref.wireId)

  private def decodeRef(value: ujson.Value,
      path: String): Either[WireError, DecisionOptionRef] = {
    val kind = value("kind").str
    val id = value("id").str
    DecisionOptionRef.fromWire(kind, id).toRight(InvalidValue(path,
      s"unknown decision option '$kind/$id'"))
  }

  private def traverse[A, B](values: Vector[A])(
      f: A => Either[WireError, B]): Either[WireError, Vector[B]] =
    values.foldLeft[Either[WireError, Vector[B]]](Right(Vector.empty)) {
      case (Right(acc), value) => f(value).map(acc :+ _)
      case (failure @ Left(_), _) => failure
    }
}
