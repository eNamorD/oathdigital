package oathdigital.serialization

import oathdigital.model._
import oathdigital.model.OathEvent._

private[serialization] trait ActionEventCodec { this: GameEventJsonSupport =>
  import GameEventWire._

  protected final val actionDiscriminator: PartialFunction[OathEvent, String] = {
      case _: BannerChallengeStarted => BannerChallengeStartedType
      case _: BannerRibbonChoiceMade => BannerRibbonChoiceMadeType
      case _: BannerChallengeCompleted => BannerChallengeCompletedType
      case _: BannerResourcePlaced => BannerResourcePlacedType
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
      case BannerChallengeStarted(player, decision, banner, holder, prior, spent,
          favor, sites) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "banner" -> banner.key,
        "priorHolderPlayerId" -> holder.fold[ujson.Value](ujson.Null)(p => ujson.Str(p.value)),
        "priorResources" -> prior, "supplySpent" -> spent,
        "automaticFavorReturns" -> ujson.Arr.from(favor.map(s => ujson.Str(s.key))),
        "automaticSecretSites" -> ujson.Arr.from(sites.map(s => ujson.Str(s.value))))
      case BannerRibbonChoiceMade(player, decision, banner, site, sites) =>
        ujson.Obj("playerId" -> player.value, "decisionId" -> decision.value,
          "banner" -> banner.key,
          "secretSiteId" -> site.value,
          "automaticSecretSites" -> ujson.Arr.from(sites.map(s => ujson.Str(s.value))))
      case BannerChallengeCompleted(player, decision, banner, holder, prior,
          placed, favor, sites, returned) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "banner" -> banner.key,
        "priorHolderPlayerId" -> holder.fold[ujson.Value](ujson.Null)(p => ujson.Str(p.value)),
        "priorResources" -> prior, "placedResources" -> placed,
        "favorReturnOrder" -> ujson.Arr.from(favor.map(s => ujson.Str(s.key))),
        "secretSiteOrder" -> ujson.Arr.from(sites.map(s => ujson.Str(s.value))),
        "secretsReturnedToHolder" -> returned)
      case BannerResourcePlaced(player, banner, amount) => ujson.Obj(
        "playerId" -> player.value, "banner" -> banner.key, "amount" -> amount)
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
        case BannerChallengeStartedType => for {
          banner <- decodeBanner(payload("banner").str, s"$path.banner")
          prior <- safeIntField(payload.obj, "priorResources", path)
          spent <- safeIntField(payload.obj, "supplySpent", path)
          favor <- traverse(payload("automaticFavorReturns").arr.toVector)(v =>
            decodeSuit(v.str, s"$path.automaticFavorReturns"))
          sites = payload("automaticSecretSites").arr.toVector.map(v => SiteId(v.str))
          holder = payload("priorHolderPlayerId") match {
            case ujson.Null => None
            case value => Some(PlayerId(value.str))
          }
        } yield BannerChallengeStarted(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), banner, holder, prior, spent,
          favor, sites)
        case BannerRibbonChoiceMadeType => for {
          banner <- decodeBanner(payload("banner").str, s"$path.banner")
          site = SiteId(payload("secretSiteId").str)
          sites = payload("automaticSecretSites").arr.toVector.map(v => SiteId(v.str))
        } yield BannerRibbonChoiceMade(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), banner, site, sites)
        case BannerChallengeCompletedType => for {
          banner <- decodeBanner(payload("banner").str, s"$path.banner")
          prior <- safeIntField(payload.obj, "priorResources", path)
          placed <- safeIntField(payload.obj, "placedResources", path)
          returned <- safeIntField(payload.obj, "secretsReturnedToHolder", path)
          favor <- traverse(payload("favorReturnOrder").arr.toVector)(v =>
            decodeSuit(v.str, s"$path.favorReturnOrder"))
          sites = payload("secretSiteOrder").arr.toVector.map(v => SiteId(v.str))
          holder = payload("priorHolderPlayerId") match {
            case ujson.Null => None
            case value => Some(PlayerId(value.str))
          }
        } yield BannerChallengeCompleted(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), banner, holder, prior, placed,
          favor, sites, returned)
        case BannerResourcePlacedType => for {
          banner <- decodeBanner(payload("banner").str, s"$path.banner")
          amount <- safeIntField(payload.obj, "amount", path)
        } yield BannerResourcePlaced(PlayerId(payload("playerId").str), banner, amount)
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
