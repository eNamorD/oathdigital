package oathdigital.serialization

import scala.util.control.NonFatal
import oathdigital.model._
import oathdigital.serialization.WireError.InvalidValue

/** Journal wire form of negotiation terms, shared by the walker's answer
  * codec and (until it is deleted) the legacy Negotiation event codec, so the
  * two spell terms identically.
  */
private[serialization] object NegotiationTermsCodec {
  def encode(terms: NegotiationTerms): ujson.Value = ujson.Obj(
    "transfers" -> ujson.Arr.from(terms.transfers.map(transfer => ujson.Obj(
      "recipientPlayerId" -> transfer.recipient.value,
      "favor" -> transfer.favor,
      "relicIds" -> ujson.Arr.from(transfer.relics.map(r => ujson.Str(r.value)))))),
    "disclosures" -> ujson.Arr.from(terms.disclosures.map { disclosure =>
      val information = disclosure.information match {
        case NegotiationDisclosureRef.Adviser(owner, card) => ujson.Obj(
          "kind" -> "adviser", "ownerPlayerId" -> owner.value,
          "card" -> encodeWorldCard(card))
        case NegotiationDisclosureRef.HeldRelic(owner, relic) => ujson.Obj(
          "kind" -> "held-relic", "ownerPlayerId" -> owner.value,
          "relicId" -> relic.value)
        case NegotiationDisclosureRef.SiteRelic(site, relic) => ujson.Obj(
          "kind" -> "site-relic", "siteId" -> site.value,
          "relicId" -> relic.value)
      }
      ujson.Obj("recipientPlayerId" -> disclosure.recipient.value,
        "information" -> information)
    }))

  def decode(value: ujson.Value, path: String)
      : Either[WireError, NegotiationTerms] = try {
    for {
      transfers <- traverse(value("transfers").arr.toVector) { row =>
        val raw = row("favor").num
        Either.cond(raw.isValidInt, raw.toInt, InvalidValue(
          s"$path.transfers.favor", s"favor '$raw' is not an integer")).map(
          favor => NegotiationTransfer(PlayerId(row("recipientPlayerId").str),
            favor, row("relicIds").arr.toVector.map(v => RelicId(v.str))))
      }
      disclosures <- traverse(value("disclosures").arr.toVector) { row =>
        val info = row("information")
        val decoded: Either[WireError, NegotiationDisclosureRef] =
          info("kind").str match {
            case "adviser" => decodeWorldCard(info("card"),
              s"$path.disclosures.card").map(card =>
              NegotiationDisclosureRef.Adviser(
                PlayerId(info("ownerPlayerId").str), card))
            case "held-relic" => Right(NegotiationDisclosureRef.HeldRelic(
              PlayerId(info("ownerPlayerId").str), RelicId(info("relicId").str)))
            case "site-relic" => Right(NegotiationDisclosureRef.SiteRelic(
              SiteId(info("siteId").str), RelicId(info("relicId").str)))
            case other => Left(InvalidValue(s"$path.disclosures.kind",
              s"unknown disclosure kind '$other'"))
          }
        decoded.map(NegotiationDisclosure(
          PlayerId(row("recipientPlayerId").str), _))
      }
    } yield NegotiationTerms(transfers, disclosures)
  } catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid Negotiation terms"))) }

  private def encodeWorldCard(id: WorldCardId): ujson.Value = id match {
    case value: DenizenId => ujson.Obj("kind" -> "denizen", "id" -> value.value)
    case value: VisionId => ujson.Obj("kind" -> "vision", "id" -> value.value)
  }

  private def decodeWorldCard(value: ujson.Value, path: String)
      : Either[WireError, WorldCardId] = value("kind").str match {
    case "denizen" => Right(DenizenId(value("id").str))
    case "vision" => Right(VisionId(value("id").str))
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown world card kind '$other'"))
  }

  private def traverse[A, B](values: Vector[A])(
      f: A => Either[WireError, B]): Either[WireError, Vector[B]] =
    values.foldLeft[Either[WireError, Vector[B]]](Right(Vector.empty)) {
      case (Right(acc), value) => f(value).map(acc :+ _)
      case (failure @ Left(_), _) => failure
    }
}
