package oathdigital.protocol

private[protocol] object CommandIntentCodec {
  import GameIntent._
  import CommandJsonSupport._

  def encode(intent: GameIntent): ujson.Obj = intent match {
    case PlacePawn(site) => tagged("placePawn", "siteId" -> site)
    case TakeWealth(resource) => tagged("takeWealth", "resource" -> resource)
    case EndWake => tagged("endWake")
    case BeginRest => tagged("beginRest")
    case FinishRest => tagged("finishRest")
    case ResolveRestPower(id, allocations, bank) => tagged("resolveRestPower",
      "decisionId" -> id,
      "allocations" -> ujson.Arr.from(allocations.map(restAllocation)),
      "destinationBank" -> bank)
    case DeclineRestPower(id) => tagged("declineRestPower", "decisionId" -> id)
    case Travel(site) => tagged("travel", "destinationSiteId" -> site)
    case Muster(target) => tagged("muster", "target" -> economy(target))
    case Trade(target, resource) => tagged("trade", "target" -> economy(target), "resource" -> resource)
    case BeginSearch(source) => tagged("beginSearch", "source" -> source.source,
      "region" -> source.region.map(ujson.Str(_)).getOrElse(ujson.Null))
    case BeginRecover => tagged("beginRecover")
    case BeginForge => tagged("beginForge")
    case CompleteForge(id, assignments) => tagged("completeForge", "decisionId" -> id,
      "assignments" -> ujson.Arr.from(assignments.map(forge)))
    case BeginChallenge(banner) => tagged("beginChallenge", "banner" -> banner)
    case ChooseChallengeSecretSite(id, site) => tagged("chooseChallengeSecretSite", "decisionId" -> id, "siteId" -> site)
    case CompleteChallenge(id, amount) => tagged("completeChallenge", "decisionId" -> id, "amount" -> amount)
    case PlaceBannerResource(banner, amount) => tagged("placeBannerResource", "banner" -> banner, "amount" -> amount)
    case ResolveFacedownAdviser(adviser, placement) => tagged(
      "resolveFacedownAdviser", "adviser" -> world(adviser),
      "placement" -> placement.map(place).getOrElse(ujson.Null))
    case RevealVision(id) => tagged("revealVision", "visionId" -> id)
    case PlayConspiracy(target) => tagged("playConspiracy", "target" -> target.map(conspiracy).getOrElse(ujson.Null))
    case PeekSiteRelics => tagged("peekSiteRelics")
    case RevealOwnedRelic(id) => tagged("revealOwnedRelic", "relicId" -> id)
    case MoveWarbands(toSite, amount) => tagged("moveWarbands", "toSite" -> toSite, "amount" -> amount)
    case BeginNegotiation(ids) => tagged("beginNegotiation", "participantPlayerIds" -> ujson.Arr.from(ids.map(ujson.Str(_))))
    case ReplaceNegotiationTerms(id, terms) => tagged("replaceNegotiationTerms", "decisionId" -> id, "terms" -> negotiation(terms))
    case AcceptNegotiation(id) => tagged("acceptNegotiation", "decisionId" -> id)
    case DeclineNegotiation(id) => tagged("declineNegotiation", "decisionId" -> id)
    case AddRecoverDice(id) => tagged("addRecoverDice", "decisionId" -> id)
    case StopRecover(id) => tagged("stopRecover", "decisionId" -> id)
    case BeginCampaignConquest(sites, count) => tagged("beginCampaignConquest", "targetSiteIds" -> ujson.Arr.from(sites.map(ujson.Str(_))), "attackDiceCount" -> count)
    case BeginCampaignRaid(targets, count) => tagged("beginCampaignRaid", "targets" -> ujson.Arr.from(targets.map(raid)), "attackDiceCount" -> count)
    case ChooseCampaignPlan(id, source) => tagged("chooseCampaignPlan", "decisionId" -> id, "source" -> plan(source))
    case FinishCampaignPlans(id) => tagged("finishCampaignPlans", "decisionId" -> id)
    case ChooseCampaignSacrifice(id, count) => tagged("chooseCampaignSacrifice", "decisionId" -> id, "count" -> count)
    case PlaceCampaignForce(id, allocations) => tagged("placeCampaignForce", "decisionId" -> id, "allocations" -> ujson.Arr.from(allocations.map(allocation)))
    case RelocateCampaignRaidPawn(id, site) => tagged("relocateCampaignRaidPawn", "decisionId" -> id, "destinationSiteId" -> site)
    case ChooseOathkeeperRecipient(id, recipient) => tagged("chooseOathkeeperRecipient", "decisionId" -> id, "recipientPlayerId" -> recipient)
    case ResolveCardDecision(id, resolution) => tagged("resolveCardDecision", "decisionId" -> id, "resolution" -> decision(resolution))
  }

  def decode(value: ujson.Value, path: String): Either[ProtocolDecodeFailure, GameIntent] =
    obj(value, path).flatMap { value =>
      rejectActorFields(value, path).flatMap(_ => string(value, "type", path).flatMap(
        CommandIntentDecoders.decode(_, value, path)))
    }

  private def tagged(kind: String, values: (String, ujson.Value)*): ujson.Obj =
    ujson.Obj.from(("type" -> ujson.Str(kind)) +: values)
  private def economy(v: EconomyTarget) = ujson.Obj("kind" -> v.kind, "id" -> v.id)
  private def world(v: WorldCard) = ujson.Obj("kind" -> v.kind, "id" -> v.id)
  private def card(v: CardRef) = ujson.Obj("kind" -> v.kind, "id" -> v.id)
  private def place(v: Placement) = ujson.Obj("kind" -> v.kind,
    "replace" -> v.replace.map(card).getOrElse(ujson.Null))
  private def forge(v: ForgeAssignment) = ujson.Obj("siteId" -> v.siteId, "denizenId" -> v.denizenId, "resource" -> v.resource)
  private def allocation(v: CampaignForceAllocation) = ujson.Obj("siteId" -> v.siteId, "count" -> v.count)
  private def restAllocation(v: RestFavorAllocation) = ujson.Obj(
    "source" -> ujson.Obj("kind" -> v.source.kind,
      "siteId" -> v.source.siteId, "sourceId" -> v.source.sourceId),
    "amount" -> v.amount)
  private def conspiracy(v: ConspiracyTarget): ujson.Obj = v match {
    case ConspiracyTarget.RelicSlot(owner, slot) => ujson.Obj("kind" -> "relic-slot", "ownerPlayerId" -> owner, "slot" -> slot)
    case ConspiracyTarget.Banner(owner, banner) => ujson.Obj("kind" -> "banner", "ownerPlayerId" -> owner, "banner" -> banner)
  }
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
