package oathdigital.protocol

private[protocol] object CommandIntentCodec {
  import GameIntent._
  import CommandJsonSupport._

  def encode(intent: GameIntent): ujson.Obj = intent match {
    case PlacePawn(site) => tagged("placePawn", "siteId" -> site)
    case EndWake => tagged("endWake")
    case BeginRest => tagged("beginRest")
    case FinishRest => tagged("finishRest")
    case UsePower(power, source) => tagged("usePower", "powerId" -> power,
      "source" -> CommandNestedCodecs.encodeStartArgWire(source))
    case PeekSiteRelics => tagged("peekSiteRelics")
    case RevealOwnedRelic(id) => tagged("revealOwnedRelic", "relicId" -> id)
    case MoveWarbands(toSite, amount) => tagged("moveWarbands", "toSite" -> toSite, "amount" -> amount)
    case BeginNegotiation(ids) => tagged("beginNegotiation", "participantPlayerIds" -> ujson.Arr.from(ids.map(ujson.Str(_))))
    case ReplaceNegotiationTerms(id, terms) => tagged("replaceNegotiationTerms", "decisionId" -> id, "terms" -> negotiation(terms))
    case AcceptNegotiation(id) => tagged("acceptNegotiation", "decisionId" -> id)
    case DeclineNegotiation(id) => tagged("declineNegotiation", "decisionId" -> id)
    case BeginCampaignConquest(sites, count) => tagged("beginCampaignConquest", "targetSiteIds" -> ujson.Arr.from(sites.map(ujson.Str(_))), "attackDiceCount" -> count)
    case BeginCampaignRaid(targets, count) => tagged("beginCampaignRaid", "targets" -> ujson.Arr.from(targets.map(raid)), "attackDiceCount" -> count)
    case ChooseCampaignPlan(id, source) => tagged("chooseCampaignPlan", "decisionId" -> id, "source" -> plan(source))
    case FinishCampaignPlans(id) => tagged("finishCampaignPlans", "decisionId" -> id)
    case ChooseCampaignSacrifice(id, count) => tagged("chooseCampaignSacrifice", "decisionId" -> id, "count" -> count)
    case PlaceCampaignForce(id, allocations) => tagged("placeCampaignForce", "decisionId" -> id, "allocations" -> ujson.Arr.from(allocations.map(allocation)))
    case RelocateCampaignRaidPawn(id, site) => tagged("relocateCampaignRaidPawn", "decisionId" -> id, "destinationSiteId" -> site)
    case ResolveCardDecision(id, resolution) => tagged("resolveCardDecision", "decisionId" -> id, "resolution" -> decision(resolution))
    case StartWalker(action, modifiers, startArgs) =>
      tagged("startWalker", "action" -> action,
        "modifiers" -> ujson.Arr.from(modifiers.map(ujson.Str(_))),
        "startArgs" -> ujson.Arr.from(startArgs.map(
          CommandNestedCodecs.encodeStartArgWire)))
    case RollWalker(pool) => tagged("rollWalker", "pool" -> pool)
    case ResolveWalker(id, payload) => tagged("resolveWalker", "decisionId" -> id,
      "payload" -> CommandNestedCodecs.encodeDecisionAnswerWire(payload))
  }

  def decode(value: ujson.Value, path: String): Either[ProtocolDecodeFailure, GameIntent] =
    obj(value, path).flatMap { value =>
      rejectActorFields(value, path).flatMap(_ => string(value, "type", path).flatMap(
        CommandIntentDecoders.decode(_, value, path)))
    }

  private def tagged(kind: String, values: (String, ujson.Value)*): ujson.Obj =
    ujson.Obj.from(("type" -> ujson.Str(kind)) +: values)
  private def allocation(v: CampaignForceAllocation) = ujson.Obj("siteId" -> v.siteId, "count" -> v.count)
  private def raid(v: CampaignRaidTarget): ujson.Obj = v match {
    case CampaignRaidTarget.Pawn(player) => ujson.Obj("kind" -> "pawn", "playerId" -> player)
    case CampaignRaidTarget.Relic(player, relic) => ujson.Obj("kind" -> "relic", "playerId" -> player, "relicId" -> relic)
    case CampaignRaidTarget.Banner(player, banner) => ujson.Obj("kind" -> banner, "playerId" -> player)
  }
  private def plan(v: CampaignPlanSource): ujson.Obj = v match {
    case CampaignPlanSource.Adviser(player, id) => ujson.Obj("kind" -> "adviser", "playerId" -> player, "cardId" -> id)
    case CampaignPlanSource.SiteCard(site, id) => ujson.Obj("kind" -> "site-card", "siteId" -> site, "cardId" -> id)
    case CampaignPlanSource.Relic(player, id) => ujson.Obj("kind" -> "relic", "playerId" -> player, "cardId" -> id)
    case CampaignPlanSource.Title(player) => ujson.Obj("kind" -> "title", "playerId" -> player)
  }
  private def negotiation(v: NegotiationTerms): ujson.Obj = CommandNestedCodecs.encodeNegotiation(v)
  private def decision(v: DecisionResolution): ujson.Obj = CommandNestedCodecs.encodeDecision(v)
}
