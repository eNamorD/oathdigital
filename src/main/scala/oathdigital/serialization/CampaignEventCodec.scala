package oathdigital.serialization

import oathdigital.model._
import oathdigital.gameplay._
import oathdigital.gameplay.OathEvent._

private[serialization] trait CampaignEventCodec { this: GameEventJsonSupport =>
  import GameEventWire._
  import WireError._

  protected final val campaignDiscriminator: PartialFunction[OathEvent, String] = {
      case _: CampaignStarted => CampaignStartedType
      case _: CampaignPlanChosen => CampaignPlanChosenType
      case _: CampaignPlansFinished => CampaignPlansFinishedType
      case _: CampaignSacrificed => CampaignSacrificedType
      case _: CampaignConquered => CampaignConqueredType
      case _: CampaignRaided => CampaignRaidedType
      case _: CampaignRaidPawnRelocated => CampaignRaidPawnRelocatedType
  }

  protected final val campaignEncoder: PartialFunction[OathEvent, ujson.Value] = {
      case CampaignStarted(player, decision, sites, defender, spent, force,
          kind, raidTargets) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "campaignKind" -> kind.key,
        "targetSiteIds" -> ujson.Arr.from(sites.map(site => ujson.Str(site.value))),
        "raidTargets" -> ujson.Arr.from(raidTargets.map(encodeCampaignRaidTarget)),
        "defender" -> (defender match {
          case CampaignDefender.Bandits => ujson.Obj("kind" -> "bandits")
          case CampaignDefender.Player(id) => ujson.Obj(
            "kind" -> "player", "playerId" -> id.value)
        }),
        "supplySpent" -> spent, "force" -> force)
      case CampaignPlanChosen(player, decision, source, handler, side, costs,
          effects) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "source" -> encodeCampaignPlanSource(source),
        "handlerId" -> handler, "side" -> encodeCampaignPlanSide(side),
        "costs" -> ujson.Arr.from(costs.map(encodeCampaignPlanCost)),
        "effects" -> ujson.Arr.from(effects.map(encodeCampaignPlanEffect)))
      case CampaignPlansFinished(player, decision, side, sources, handlerIds, effects,
          dice, attack, skulls) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "side" -> encodeCampaignPlanSide(side),
        "orderedSources" -> ujson.Arr.from(sources.map(encodeCampaignPlanSource)),
        "orderedHandlerIds" -> ujson.Arr.from(handlerIds.map(ujson.Str(_))),
        "effects" -> ujson.Arr.from(effects.map(encodeCampaignPlanEffect)),
        "attackDice" -> ujson.Arr.from(dice.map(d => ujson.Str(encodeAttackFace(d)))),
        "attack" -> attack, "skullLosses" -> skulls)
      case CampaignSacrificed(player, decision, sacrificed, dice, attack,
          defense, skulls, victorious, policyId, losses) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "sacrificed" -> sacrificed,
        "defenseDice" -> ujson.Arr.from(dice.map(d => ujson.Str(encodeDefenseFace(d)))),
        "attack" -> attack, "defense" -> defense, "skullLosses" -> skulls,
        "victorious" -> victorious,
        "losingForcePolicyId" -> policyId.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
        "losingForces" -> ujson.Arr.from(losses.map(encodeLosingForceEffect)))
      case CampaignConquered(player, decision, policyId, losses, allocations) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "losingForcePolicyId" -> policyId,
        "losingForces" -> ujson.Arr.from(losses.map(encodeLosingForceEffect)),
        "allocations" -> ujson.Arr.from(allocations.map { allocation =>
          ujson.Obj("siteId" -> allocation.site.value,
            "count" -> allocation.count)
        }))
      case CampaignRaided(player, decision, policyId, loss, relics, banners,
          advisers, adviserRegion, boxedConspiracy, discardedRelics, burned,
          returned, darkestSecretBurned) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "losingForcePolicyId" -> policyId,
        "defenderLoss" -> ujson.Obj("playerId" -> loss.playerId.value,
          "killed" -> loss.killed, "returned" -> loss.returned),
        "takenRelics" -> ujson.Arr.from(relics.map(r => ujson.Str(r.value))),
        "takenBanners" -> ujson.Arr.from(banners.map(b => ujson.Str(b.key))),
        "discardedAdvisers" -> ujson.Arr.from(advisers.map(encodeWorldCard)),
        "adviserDiscardRegion" -> adviserRegion.key,
        "boxedConspiracy" -> boxedConspiracy.fold[ujson.Value](ujson.Null)(id =>
          ujson.Str(id.value)),
        "discardedRelics" -> ujson.Arr.from(discardedRelics.map(r => ujson.Str(r.value))),
        "favorBurned" -> burned,
        "darkestSecretBurned" -> darkestSecretBurned,
        "bannerFavorReturned" -> ujson.Obj.from(returned.toVector.sortBy(_._1.key)
          .map { case (suit, amount) => suit.key -> ujson.Num(amount) }))
      case CampaignRaidPawnRelocated(player, decision, defender, origin, destination) =>
        ujson.Obj("playerId" -> player.value, "decisionId" -> decision.value,
          "defenderPlayerId" -> defender.value, "originSiteId" -> origin.value,
          "destinationSiteId" -> destination.value)
  }

  protected final def campaignDecode(eventType: String, payload: ujson.Value,
      path: String, envelopeCatalog: CatalogRef): Option[Either[WireError, OathEvent]] = {
    val decoder: PartialFunction[String, Either[WireError, OathEvent]] = {
        case CampaignStartedType => for {
          spent <- safeIntField(payload.obj, "supplySpent", path)
          force <- safeIntField(payload.obj, "force", path)
          kind <- decodeCampaignKind(payload("campaignKind"), s"$path.campaignKind")
          sites <- traverse(payload("targetSiteIds").arr.toVector)(value =>
            Right(SiteId(value.str)))
          raidTargets <- traverse(payload("raidTargets").arr.toVector)(value =>
            decodeCampaignRaidTarget(value, s"$path.raidTargets"))
          defender <- payload("defender")("kind").str match {
            case "bandits" => Right(CampaignDefender.Bandits)
            case "player" => Right(CampaignDefender.Player(
              PlayerId(payload("defender")("playerId").str)))
            case other => Left(InvalidValue(s"$path.defender.kind",
              s"unknown Campaign defender '$other'"))
          }
        } yield CampaignStarted(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), sites,
          defender, spent, force, kind, raidTargets)
        case CampaignPlanChosenType => for {
          source <- decodeCampaignPlanSource(payload("source"), s"$path.source")
          side <- decodeCampaignPlanSide(payload("side"), s"$path.side")
          costs <- traverse(payload("costs").arr.toVector)(v =>
            decodeCampaignPlanCost(v, s"$path.costs"))
          effects <- traverse(payload("effects").arr.toVector)(v =>
            decodeCampaignPlanEffect(v, s"$path.effects"))
        } yield CampaignPlanChosen(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), source,
          payload("handlerId").str, side, costs, effects)
        case CampaignPlansFinishedType => for {
          side <- decodeCampaignPlanSide(payload("side"), s"$path.side")
          sources <- traverse(payload("orderedSources").arr.toVector)(v =>
            decodeCampaignPlanSource(v, s"$path.orderedSources"))
          _ <- Either.cond(sources.distinct.size == sources.size, (),
            InvalidValue(s"$path.orderedSources", "duplicate Campaign plan source"))
          handlerIds = payload("orderedHandlerIds").arr.toVector.map(_.str)
          _ <- Either.cond(handlerIds.size == sources.size, (), InvalidValue(
            s"$path.orderedHandlerIds", "handler IDs must match Campaign plan sources"))
          effects <- traverse(payload("effects").arr.toVector)(v =>
            decodeCampaignPlanEffect(v, s"$path.effects"))
          attack <- safeIntField(payload.obj, "attack", path)
          skulls <- safeIntField(payload.obj, "skullLosses", path)
          dice <- traverse(payload("attackDice").arr.toVector)(v =>
            decodeAttackFace(v.str, s"$path.attackDice"))
        } yield CampaignPlansFinished(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), side, sources, handlerIds,
          effects, dice, attack, skulls)
        case CampaignSacrificedType => for {
          sacrificed <- safeIntField(payload.obj, "sacrificed", path)
          attack <- safeIntField(payload.obj, "attack", path)
          defense <- safeIntField(payload.obj, "defense", path)
          skulls <- safeIntField(payload.obj, "skullLosses", path)
          dice <- traverse(payload("defenseDice").arr.toVector)(v =>
            decodeDefenseFace(v.str, s"$path.defenseDice"))
          policyId = payload("losingForcePolicyId") match {
            case ujson.Null => None
            case value => Some(value.str)
          }
          losses <- traverse(payload("losingForces").arr.toVector)(value =>
            decodeLosingForceEffect(value, s"$path.losingForces"))
        } yield CampaignSacrificed(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), sacrificed, dice, attack,
          defense, skulls, payload("victorious").bool, policyId, losses)
        case CampaignConqueredType => for {
          losses <- traverse(payload("losingForces").arr.toVector)(value =>
            decodeLosingForceEffect(value, s"$path.losingForces"))
          allocations <- traverse(payload("allocations").arr.toVector) { value =>
            val allocationPath = s"$path.allocations"
            safeIntField(value.obj, "count", allocationPath).map(count =>
              CampaignForceAllocation(SiteId(value("siteId").str), count))
          }
        } yield CampaignConquered(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str),
          payload("losingForcePolicyId").str, losses, allocations)
        case CampaignRaidedType => for {
          killed <- safeIntField(payload("defenderLoss").obj, "killed",
            s"$path.defenderLoss")
          returnedCount <- safeIntField(payload("defenderLoss").obj, "returned",
            s"$path.defenderLoss")
          relics = payload("takenRelics").arr.toVector.map(v => RelicId(v.str))
          banners <- traverse(payload("takenBanners").arr.toVector)(value =>
            decodeBanner(value.str, s"$path.takenBanners"))
          advisers <- traverse(payload("discardedAdvisers").arr.toVector)(value =>
            decodeWorldCard(value, s"$path.discardedAdvisers"))
          adviserRegion <- decodeRegion(payload("adviserDiscardRegion").str,
            s"$path.adviserDiscardRegion")
          boxedConspiracy = payload("boxedConspiracy") match {
            case ujson.Null => None
            case value => Some(VisionId(value.str))
          }
          discardedRelics = payload("discardedRelics").arr.toVector.map(v => RelicId(v.str))
          burned <- safeIntField(payload.obj, "favorBurned", path)
          darkestSecretBurned <- safeIntField(payload.obj, "darkestSecretBurned", path)
          favorEntries <- traverse(payload("bannerFavorReturned").obj.toVector) {
            case (key, value) => Suit.fromKey(key).toRight(InvalidValue(
              s"$path.bannerFavorReturned.$key", "unknown suit")).flatMap(suit =>
              safeInt(value, s"$path.bannerFavorReturned.$key").map(suit -> _))
          }
        } yield CampaignRaided(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), payload("losingForcePolicyId").str,
          CampaignRaidBoardLoss(PlayerId(payload("defenderLoss")("playerId").str),
            killed, returnedCount), relics, banners, advisers, adviserRegion,
          boxedConspiracy, discardedRelics, burned, favorEntries.toMap,
          darkestSecretBurned)
        case CampaignRaidPawnRelocatedType => Right(CampaignRaidPawnRelocated(
          PlayerId(payload("playerId").str), DecisionId(payload("decisionId").str),
          PlayerId(payload("defenderPlayerId").str), SiteId(payload("originSiteId").str),
          SiteId(payload("destinationSiteId").str)))
    }
    decoder.lift(eventType)
  }
}
