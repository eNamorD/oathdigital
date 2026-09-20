package oathdigital.serialization

import oathdigital.model.{DecisionAnswer, DecisionOptionRef, DecisionPlacement,
  DistributeAmount}
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
  * eight variants are named once in the model rather than tabulated again
  * here, in the command protocol, and in the projection.
  */
private[serialization] object DecisionAnswerCodec {
  private val ChooseOneTag = "choose-one"
  private val ChooseManyTag = "choose-many"
  private val ChooseAmountTag = "choose-amount"
  private val PartitionTag = "partition"
  private val DistributeTag = "distribute"
  private val ProposeTermsTag = "propose-terms"
  private val AcceptDealTag = "accept-deal"
  private val DeclineDealTag = "decline-deal"

  def encode(answer: DecisionAnswer): ujson.Value = answer match {
    case DecisionAnswer.ChooseOneAnswer(selected) => ujson.Obj(
      "kind" -> ChooseOneTag, "option" -> encodeRef(selected))
    case DecisionAnswer.ChooseManyAnswer(selected) => ujson.Obj(
      "kind" -> ChooseManyTag,
      "options" -> ujson.Arr.from(selected.map(encodeRef)))
    case DecisionAnswer.ChooseAmountAnswer(amount) => ujson.Obj(
      "kind" -> ChooseAmountTag, "amount" -> amount)
    case DecisionAnswer.PartitionAnswer(placements) => ujson.Obj(
      "kind" -> PartitionTag,
      "placements" -> ujson.Arr.from(placements.map(placement => ujson.Obj(
        "option" -> encodeRef(placement.option),
        "sectionKey" -> placement.sectionKey))))
    case DecisionAnswer.DistributeAnswer(amounts) => ujson.Obj(
      "kind" -> DistributeTag,
      "amounts" -> ujson.Arr.from(amounts.map(entry => ujson.Obj(
        "option" -> encodeRef(entry.ref), "amount" -> entry.amount))))
    case DecisionAnswer.ProposeTerms(terms) => ujson.Obj(
      "kind" -> ProposeTermsTag, "terms" -> NegotiationTermsCodec.encode(terms))
    case DecisionAnswer.AcceptDeal => ujson.Obj("kind" -> AcceptDealTag)
    case DecisionAnswer.DeclineDeal => ujson.Obj("kind" -> DeclineDealTag)
  }

  def decode(value: ujson.Value,
      path: String): Either[WireError, DecisionAnswer] =
    value("kind").str match {
      case ChooseOneTag => decodeRef(value("option"), s"$path.option")
        .map(DecisionAnswer.ChooseOneAnswer)
      case ChooseManyTag =>
        traverse(value("options").arr.toVector.zipWithIndex) {
          case (entry, index) => decodeRef(entry, s"$path.options[$index]")
        }.map(DecisionAnswer.ChooseManyAnswer)
      case ChooseAmountTag =>
        val raw = value("amount").num
        Either.cond(raw.isValidInt, DecisionAnswer.ChooseAmountAnswer(raw.toInt),
          InvalidValue(s"$path.amount", s"amount '$raw' is not an integer"))
      case PartitionTag =>
        traverse(value("placements").arr.toVector.zipWithIndex) {
          case (entry, index) =>
            val entryPath = s"$path.placements[$index]"
            decodeRef(entry("option"), s"$entryPath.option").map(
              DecisionPlacement(_, entry("sectionKey").str))
        }.map(DecisionAnswer.PartitionAnswer)
      case DistributeTag =>
        traverse(value("amounts").arr.toVector.zipWithIndex) {
          case (entry, index) =>
            val entryPath = s"$path.amounts[$index]"
            val raw = entry("amount").num
            for {
              ref <- decodeRef(entry("option"), s"$entryPath.option")
              amount <- Either.cond(raw.isValidInt, raw.toInt,
                InvalidValue(s"$entryPath.amount", s"amount '$raw' is not an integer"))
            } yield DistributeAmount(ref, amount)
        }.map(DecisionAnswer.DistributeAnswer)
      case ProposeTermsTag => NegotiationTermsCodec.decode(value("terms"),
        s"$path.terms").map(DecisionAnswer.ProposeTerms)
      case AcceptDealTag => Right(DecisionAnswer.AcceptDeal)
      case DeclineDealTag => Right(DecisionAnswer.DeclineDeal)
      case other => Left(InvalidValue(s"$path.kind",
        s"unknown walker decision answer '$other'"))
    }

  /** `private[serialization]`, not `private`: [[WalkerEventCodec]] writes the
    * start selections on a `WalkerParked` with this same pair, so the journal
    * has ONE spelling of an option reference rather than two that agree by
    * convention -- the drift `DecisionOptionRef.wireId`'s own doc exists to
    * prevent.
    */
  private[serialization] def encodeRef(ref: DecisionOptionRef): ujson.Value =
    ujson.Obj("kind" -> ref.kind, "id" -> ref.wireId)

  private[serialization] def decodeRef(value: ujson.Value,
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
