package oathdigital.serialization

import oathdigital.model._
import oathdigital.model.OathEvent._

private[serialization] trait ActionEventCodec { this: GameEventJsonSupport =>
  import GameEventWire._

  protected final val actionDiscriminator: PartialFunction[OathEvent, String] = {
      case _: SiteRelicsPeeked => SiteRelicsPeekedType
      case _: OwnedRelicRevealed => OwnedRelicRevealedType
      case _: WarbandsMoved => WarbandsMovedType
      case _: NegotiationStarted => NegotiationStartedType
      case _: NegotiationTermsReplaced => NegotiationTermsReplacedType
      case _: NegotiationAccepted => NegotiationAcceptedType
      case _: NegotiationDeclined => NegotiationDeclinedType
      case _: NegotiationCompleted => NegotiationCompletedType
  }

  protected final val actionEncoder: PartialFunction[OathEvent, ujson.Value] = {
      case SiteRelicsPeeked(player, site, relics) =>
        ujson.Obj("playerId" -> player.value, "siteId" -> site.value,
          "relics" -> ujson.Arr.from(relics.map(r => ujson.Str(r.value))))
      case OwnedRelicRevealed(player, relic) =>
        ujson.Obj("playerId" -> player.value, "relicId" -> relic.value)
      case WarbandsMoved(player, site, toSite, amount, board, atSite) =>
        ujson.Obj("playerId" -> player.value, "siteId" -> site.value,
          "toSite" -> toSite, "amount" -> amount,
          "priorBoardWarbands" -> board, "priorSiteWarbands" -> atSite)
      case NegotiationStarted(player, decision, site, participants) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "siteId" -> site.value, "participants" -> stringArray(participants.map(_.value)))
      case NegotiationTermsReplaced(player, decision, terms) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "terms" -> encodeNegotiationTerms(terms))
      case NegotiationAccepted(player, decision) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value)
      case NegotiationDeclined(player, decision) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value)
      case NegotiationCompleted(player, decision, participants, terms) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "participants" -> stringArray(participants.map(_.value)),
        "terms" -> ujson.Arr.from(participants.map(author => ujson.Obj(
          "authorPlayerId" -> author.value,
          "terms" -> encodeNegotiationTerms(terms(author))))))
  }

  protected final def actionDecode(eventType: String, payload: ujson.Value,
      path: String, envelopeCatalog: CatalogRef): Option[Either[WireError, OathEvent]] = {
    val decoder: PartialFunction[String, Either[WireError, OathEvent]] = {
        case SiteRelicsPeekedType => Right(SiteRelicsPeeked(
          PlayerId(payload("playerId").str), SiteId(payload("siteId").str),
          payload("relics").arr.toVector.map(value => RelicId(value.str))))
        case OwnedRelicRevealedType => Right(OwnedRelicRevealed(
          PlayerId(payload("playerId").str), RelicId(payload("relicId").str)))
        case WarbandsMovedType => for {
          amount <- safeIntField(payload.obj, "amount", path)
          board <- safeIntField(payload.obj, "priorBoardWarbands", path)
          atSite <- safeIntField(payload.obj, "priorSiteWarbands", path)
        } yield WarbandsMoved(PlayerId(payload("playerId").str),
          SiteId(payload("siteId").str), payload("toSite").bool, amount, board, atSite)
        case NegotiationStartedType => Right(NegotiationStarted(
          PlayerId(payload("playerId").str), DecisionId(payload("decisionId").str),
          SiteId(payload("siteId").str), payload("participants").arr.toVector
            .map(v => PlayerId(v.str))))
        case NegotiationTermsReplacedType => decodeNegotiationTerms(
          payload("terms"), s"$path.terms").map(terms => NegotiationTermsReplaced(
          PlayerId(payload("playerId").str), DecisionId(payload("decisionId").str), terms))
        case NegotiationAcceptedType => Right(NegotiationAccepted(
          PlayerId(payload("playerId").str), DecisionId(payload("decisionId").str)))
        case NegotiationDeclinedType => Right(NegotiationDeclined(
          PlayerId(payload("playerId").str), DecisionId(payload("decisionId").str)))
        case NegotiationCompletedType => for {
          participants <- Right(payload("participants").arr.toVector.map(v => PlayerId(v.str)))
          rows <- traverse(payload("terms").arr.toVector)(row =>
            decodeNegotiationTerms(row("terms"), s"$path.terms").map(
              PlayerId(row("authorPlayerId").str) -> _))
        } yield NegotiationCompleted(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), participants, rows.toMap)
    }
    decoder.lift(eventType)
  }
}
