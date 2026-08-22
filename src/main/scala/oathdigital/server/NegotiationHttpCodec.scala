package oathdigital.server

import scala.util.control.NonFatal
import oathdigital.model._

private[server] object NegotiationHttpCodec {
  def decodeTerms(value: ujson.Value, path: String): Either[HttpInputError, NegotiationTerms] =
    try {
      val transfers = value("transfers").arr.toVector.map { row =>
        val favor = row("favor").num
        if (!favor.isWhole || favor < 0 || favor > Int.MaxValue)
          return Left(HttpInputError(s"$path.transfers.favor", "expected non-negative integer"))
        NegotiationTransfer(PlayerId(row("recipientPlayerId").str), favor.toInt,
          row("relicIds").arr.toVector.map(v => RelicId(v.str)))
      }
      val disclosures = value("disclosures").arr.toVector.map { row =>
        val info = row("information")
        val decoded: NegotiationDisclosureRef = info("kind").str match {
          case "adviser" =>
            val card = info("card")
            val id: WorldCardId = card("kind").str match {
              case "denizen" => DenizenId(card("id").str)
              case "vision" => VisionId(card("id").str)
              case other => return Left(HttpInputError(s"$path.disclosures.card.kind",
                s"unknown world card kind '$other'"))
            }
            NegotiationDisclosureRef.Adviser(PlayerId(info("ownerPlayerId").str), id)
          case "held-relic" => NegotiationDisclosureRef.HeldRelic(
            PlayerId(info("ownerPlayerId").str), RelicId(info("relicId").str))
          case "site-relic" => NegotiationDisclosureRef.SiteRelic(
            SiteId(info("siteId").str), RelicId(info("relicId").str))
          case other => return Left(HttpInputError(s"$path.disclosures.information.kind",
            s"unknown disclosure kind '$other'"))
        }
        NegotiationDisclosure(PlayerId(row("recipientPlayerId").str), decoded)
      }
      Right(NegotiationTerms(transfers, disclosures))
    } catch { case NonFatal(error) => Left(HttpInputError(path,
      Option(error.getMessage).getOrElse("invalid Negotiation terms"))) }
}
