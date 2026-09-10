package oathdigital.serialization

import oathdigital.model._
import oathdigital.gameplay._
import oathdigital.gameplay.OathEvent._

private[serialization] trait ActionEventCodec { this: GameEventJsonSupport =>
  import GameEventWire._
  import WireError._

  protected final val actionDiscriminator: PartialFunction[OathEvent, String] = {
      case _: Traveled => TraveledType
      case _: Mustered => MusteredType
      case _: Traded => TradedType
      case _: SearchStarted => SearchStartedType
      case _: SearchCompleted => SearchCompletedType
      case _: BannerChallengeStarted => BannerChallengeStartedType
      case _: BannerRibbonChoiceMade => BannerRibbonChoiceMadeType
      case _: BannerChallengeCompleted => BannerChallengeCompletedType
      case _: BannerResourcePlaced => BannerResourcePlacedType
      case _: FacedownAdviserDiscarded => FacedownAdviserDiscardedType
      case _: FacedownAdviserPlayed => FacedownAdviserPlayedType
      case _: SiteRelicsPeeked => SiteRelicsPeekedType
      case _: OwnedRelicRevealed => OwnedRelicRevealedType
      case _: WarbandsMoved => WarbandsMovedType
      case _: NegotiationStarted => NegotiationStartedType
      case _: NegotiationTermsReplaced => NegotiationTermsReplacedType
      case _: NegotiationAccepted => NegotiationAcceptedType
      case _: NegotiationDeclined => NegotiationDeclinedType
      case _: NegotiationCompleted => NegotiationCompletedType
      case _: VisionRevealed => VisionRevealedType
      case _: ConspiracyStarted => ConspiracyStartedType
      case _: ConspiracyCompleted => ConspiracyCompletedType
  }

  protected final val actionEncoder: PartialFunction[OathEvent, ujson.Value] = {
      case Traveled(playerId, source, destination, supplySpent) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "sourceSiteId" -> source.value,
          "destinationSiteId" -> destination.value,
          "supplySpent" -> supplySpent
        )
      case Mustered(playerId, site, target, suit, spent, gained) =>
        ujson.Obj("playerId" -> playerId.value, "siteId" -> site.value,
          "target" -> encodeCardRef(target.id), "suit" -> suit.key,
          "supplySpent" -> spent, "warbandsGained" -> gained)
      case Traded(playerId, site, target, suit, resource, spent, gained) =>
        ujson.Obj("playerId" -> playerId.value, "siteId" -> site.value,
          "target" -> encodeCardRef(target.id), "suit" -> suit.key,
          "resource" -> (resource match {
            case TradeResource.Favor => "favor"
            case TradeResource.Secret => "secret"
          }), "supplySpent" -> spent, "gained" -> gained)
      case SearchStarted(playerId, decision, source, origin, spent, drawn) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "decisionId" -> decision.value,
          "source" -> encodeSearchSource(source),
          "origin" -> origin.key,
          "supplySpent" -> spent,
          "drawn" -> ujson.Arr.from(drawn.map(encodeWorldCard))
        )
      case SearchCompleted(playerId, decision, kept, discarded, placement,
          favorGained, discardedWorld, discardedEdifices) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "decisionId" -> decision.value,
          "kept" -> encodeWorldCard(kept),
          "discardedInOrder" -> ujson.Arr.from(discarded.map(encodeWorldCard)),
          "placement" -> encodeSearchPlacement(placement),
          "favorGained" -> favorGained,
          "discardedWorld" -> ujson.Arr.from(discardedWorld.map(encodeWorldCard)),
          "discardedEdifices" -> ujson.Arr.from(discardedEdifices.map(id =>
            ujson.Str(id.value)))
        )
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
      case FacedownAdviserDiscarded(player, adviser, destination) =>
        ujson.Obj("playerId" -> player.value, "adviser" -> encodeWorldCard(adviser),
          "destination" -> destination.key)
      case FacedownAdviserPlayed(player, adviser, placement, favor, world, edifices) =>
        ujson.Obj("playerId" -> player.value, "adviser" -> encodeWorldCard(adviser),
          "placement" -> encodeSearchPlacement(placement), "favorGained" -> favor,
          "discardedWorld" -> ujson.Arr.from(world.map(encodeWorldCard)),
          "discardedEdifices" -> ujson.Arr.from(edifices.map(e => ujson.Str(e.value))))
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
      case VisionRevealed(player, vision, replaced, destination) => ujson.Obj(
        "playerId" -> player.value, "visionId" -> vision.value,
        "replacedVisionId" -> replaced.fold[ujson.Value](ujson.Null)(v => ujson.Str(v.value)),
        "discardRegion" -> destination.key)
      case ConspiracyStarted(player, decision, source, target, favor) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "sourceVisionId" -> source.value,
        "target" -> target.fold[ujson.Value](ujson.Null)(encodeConspiracyTarget),
        "automaticFavorReturns" -> stringArray(favor.map(_.key)))
      case ConspiracyCompleted(player, decision, source, target, favor) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "sourceVisionId" -> source.value,
        "target" -> target.fold[ujson.Value](ujson.Null)(encodeConspiracyTarget),
        "favorReturnOrder" -> stringArray(favor.map(_.key)))
  }

  protected final def actionDecode(eventType: String, payload: ujson.Value,
      path: String, envelopeCatalog: CatalogRef): Option[Either[WireError, OathEvent]] = {
    val decoder: PartialFunction[String, Either[WireError, OathEvent]] = {
        case TraveledType =>
          val spent = payload("supplySpent").num
          if (!spent.isFinite || spent != Math.rint(spent) || spent < 0 ||
              spent > Int.MaxValue)
            Left(InvalidValue(s"$path.supplySpent",
              "must be a non-negative integer"))
          else Right(Traveled(
            PlayerId(payload("playerId").str),
            SiteId(payload("sourceSiteId").str),
            SiteId(payload("destinationSiteId").str),
            spent.toInt
          ))
        case MusteredType => for {
          target <- decodeEconomyTarget(payload("target"), s"$path.target")
          suit <- decodeSuit(payload("suit").str, s"$path.suit")
          spent <- safeIntField(payload.obj, "supplySpent", path)
          gained <- safeIntField(payload.obj, "warbandsGained", path)
        } yield Mustered(PlayerId(payload("playerId").str),
          SiteId(payload("siteId").str), target, suit, spent, gained)
        case TradedType => for {
          target <- decodeEconomyTarget(payload("target"), s"$path.target")
          suit <- decodeSuit(payload("suit").str, s"$path.suit")
          resource <- payload("resource").str match {
            case "favor" => Right(TradeResource.Favor)
            case "secret" => Right(TradeResource.Secret)
            case other => Left(InvalidValue(s"$path.resource",
              s"unknown Trade resource '$other'"))
          }
          spent <- safeIntField(payload.obj, "supplySpent", path)
          gained <- safeIntField(payload.obj, "gained", path)
        } yield Traded(PlayerId(payload("playerId").str),
          SiteId(payload("siteId").str), target, suit, resource, spent, gained)
        case SearchStartedType =>
          for {
            source <- decodeSearchSource(payload("source"), s"$path.source")
            origin <- decodeRegion(payload("origin").str, s"$path.origin")
            drawn <- traverse(payload("drawn").arr.toVector)(decodeWorldCard(_, s"$path.drawn"))
            spent = payload("supplySpent").num
            _ <- if (spent.isFinite && spent == Math.rint(spent) && spent >= 0 &&
              spent <= Int.MaxValue) Right(()) else Left(InvalidValue(
              s"$path.supplySpent", "must be a non-negative integer"))
          } yield SearchStarted(
            PlayerId(payload("playerId").str),
            DecisionId(payload("decisionId").str), source, origin,
            spent.toInt, drawn)
        case SearchCompletedType =>
          for {
            kept <- decodeWorldCard(payload("kept"), s"$path.kept")
            discarded <- traverse(payload("discardedInOrder").arr.toVector)(
              decodeWorldCard(_, s"$path.discardedInOrder"))
            placement <- decodeSearchPlacement(payload("placement"), s"$path.placement")
            favor <- safeIntField(payload.obj, "favorGained", path)
            discardedWorld <- traverse(payload("discardedWorld").arr.toVector)(
              decodeWorldCard(_, s"$path.discardedWorld"))
            discardedEdifices = payload("discardedEdifices").arr.toVector.map(value =>
              EdificeId(value.str))
          } yield SearchCompleted(
            PlayerId(payload("playerId").str),
            DecisionId(payload("decisionId").str), kept, discarded, placement,
            favor, discardedWorld, discardedEdifices)
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
        case FacedownAdviserDiscardedType => for {
          adviser <- decodeWorldCard(payload("adviser"), s"$path.adviser")
          destination <- decodeRegion(payload("destination").str, s"$path.destination")
        } yield FacedownAdviserDiscarded(PlayerId(payload("playerId").str),
          adviser, destination)
        case FacedownAdviserPlayedType => for {
          adviser <- decodeWorldCard(payload("adviser"), s"$path.adviser")
          placement <- decodeSearchPlacement(payload("placement"), s"$path.placement")
          favor <- safeIntField(payload.obj, "favorGained", path)
          world <- traverse(payload("discardedWorld").arr.toVector)(value =>
            decodeWorldCard(value, s"$path.discardedWorld"))
          edifices = payload("discardedEdifices").arr.toVector.map(value =>
            EdificeId(value.str))
        } yield FacedownAdviserPlayed(PlayerId(payload("playerId").str), adviser,
          placement, favor, world, edifices)
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
        case VisionRevealedType => for {
          destination <- decodeRegion(payload("discardRegion").str,
            s"$path.discardRegion")
        } yield VisionRevealed(PlayerId(payload("playerId").str),
          VisionId(payload("visionId").str), payload("replacedVisionId") match {
            case ujson.Null => None
            case value => Some(VisionId(value.str))
          }, destination)
        case ConspiracyStartedType => for {
          target <- decodeOptionalConspiracyTarget(payload("target"), s"$path.target")
          favor <- traverse(payload("automaticFavorReturns").arr.toVector)(v =>
            Suit.all.find(_.key == v.str).toRight(InvalidValue(
              s"$path.automaticFavorReturns", "unknown suit")))
        } yield ConspiracyStarted(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str),
          VisionId(payload("sourceVisionId").str), target, favor)
        case ConspiracyCompletedType => for {
          target <- decodeOptionalConspiracyTarget(payload("target"), s"$path.target")
          favor <- traverse(payload("favorReturnOrder").arr.toVector)(v =>
            Suit.all.find(_.key == v.str).toRight(InvalidValue(
              s"$path.favorReturnOrder", "unknown suit")))
        } yield ConspiracyCompleted(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str),
          VisionId(payload("sourceVisionId").str), target, favor)
    }
    decoder.lift(eventType)
  }
}
