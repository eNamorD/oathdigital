package oathdigital.protocol

private[protocol] object CommandIntentDecoders {
  import CommandJsonSupport._
  import GameIntent._
  import ProtocolDecodeFailure._

  def decode(kind: String, value: ujson.Obj, path: String)
      : Either[ProtocolDecodeFailure, GameIntent] = kind match {
    case "placePawn" => one(value, path, "siteId")(PlacePawn)
    case "endWake" => empty(value, path, EndWake)
    case "beginRest" => empty(value, path, BeginRest)
    case "finishRest" => empty(value, path, FinishRest)
    case "usePower" => for {
      _ <- exact(value, Set("type", "powerId", "source"), path)
      power <- string(value, "powerId", path)
      source <- field(value, "source", path).flatMap(raw =>
        CommandNestedCodecs.decodeStartArgsWire(ujson.Arr(raw), s"$path.source"))
        .flatMap {
          case Vector(one) => Right(one)
          case _ => Left(InvalidValue(s"$path.source",
            "expected one power source"))
        }
    } yield UsePower(power, source)
    case "beginChallenge" => one(value, path, "banner")(BeginChallenge)
    case "chooseChallengeSecretSite" => two(value, path, "decisionId", "siteId")(ChooseChallengeSecretSite)
    case "completeChallenge" => idInt(value, path, "amount")(CompleteChallenge)
    case "placeBannerResource" => for {
      _ <- exact(value, Set("type", "banner", "amount"), path)
      banner <- string(value, "banner", path)
      amount <- field(value, "amount", path).flatMap(integer(_, s"$path.amount"))
    } yield PlaceBannerResource(banner, amount)
    case "peekSiteRelics" => empty(value, path, PeekSiteRelics)
    case "revealOwnedRelic" => one(value, path, "relicId")(RevealOwnedRelic)
    case "moveWarbands" => for {
      _ <- exact(value, Set("type", "toSite", "amount"), path)
      toSite <- field(value, "toSite", path).flatMap(boolean(_, s"$path.toSite"))
      amount <- field(value, "amount", path).flatMap(integer(_, s"$path.amount"))
    } yield MoveWarbands(toSite, amount)
    case "beginNegotiation" => for {
      _ <- exact(value, Set("type", "participantPlayerIds"), path)
      ids <- field(value, "participantPlayerIds", path).flatMap(strings(_, s"$path.participantPlayerIds"))
      _ <- noDuplicates(ids, s"$path.participantPlayerIds")
    } yield BeginNegotiation(ids)
    case "replaceNegotiationTerms" => for {
      _ <- exact(value, Set("type", "decisionId", "terms"), path)
      id <- string(value, "decisionId", path)
      terms <- field(value, "terms", path).flatMap(CommandNestedCodecs.decodeNegotiation(_, s"$path.terms"))
    } yield ReplaceNegotiationTerms(id, terms)
    case "acceptNegotiation" => decision(value, path)(AcceptNegotiation)
    case "declineNegotiation" => decision(value, path)(DeclineNegotiation)
    case "beginCampaignConquest" => for {
      _ <- exact(value, Set("type", "targetSiteIds", "attackDiceCount"), path)
      sites <- field(value, "targetSiteIds", path).flatMap(strings(_, s"$path.targetSiteIds"))
      _ <- noDuplicates(sites, s"$path.targetSiteIds")
      count <- field(value, "attackDiceCount", path).flatMap(integer(_, s"$path.attackDiceCount"))
    } yield BeginCampaignConquest(sites, count)
    case "beginCampaignRaid" => for {
      _ <- exact(value, Set("type", "targets", "attackDiceCount"), path)
      raw <- field(value, "targets", path).flatMap(array(_, s"$path.targets"))
      targets <- traverse(raw.zipWithIndex) { case (v, i) => campaignRaid(v, s"$path.targets[$i]") }
      _ <- noDuplicates(targets.map(_.toString), s"$path.targets")
      count <- field(value, "attackDiceCount", path).flatMap(integer(_, s"$path.attackDiceCount"))
    } yield BeginCampaignRaid(targets, count)
    case "chooseCampaignPlan" => for {
      _ <- exact(value, Set("type", "decisionId", "source"), path)
      id <- string(value, "decisionId", path)
      source <- field(value, "source", path).flatMap(campaignPlan(_, s"$path.source"))
    } yield ChooseCampaignPlan(id, source)
    case "finishCampaignPlans" => decision(value, path)(FinishCampaignPlans)
    case "chooseCampaignSacrifice" => idInt(value, path, "count")(ChooseCampaignSacrifice)
    case "placeCampaignForce" => for {
      _ <- exact(value, Set("type", "decisionId", "allocations"), path)
      id <- string(value, "decisionId", path)
      raw <- field(value, "allocations", path).flatMap(array(_, s"$path.allocations"))
      allocations <- traverse(raw.zipWithIndex) { case (v, i) => allocation(v, s"$path.allocations[$i]") }
      _ <- noDuplicates(allocations.map(_.siteId), s"$path.allocations")
    } yield PlaceCampaignForce(id, allocations)
    case "relocateCampaignRaidPawn" => two(value, path, "decisionId", "destinationSiteId")(RelocateCampaignRaidPawn)
    case "resolveCardDecision" => for {
      _ <- exact(value, Set("type", "decisionId", "resolution"), path)
      id <- string(value, "decisionId", path)
      resolution <- field(value, "resolution", path).flatMap(CommandNestedCodecs.decodeDecision(_, s"$path.resolution"))
    } yield ResolveCardDecision(id, resolution)
    case "startWalker" => for {
      _ <- exact(value, Set("type", "action", "modifiers", "startArgs"), path)
      action <- string(value, "action", path)
      modifiers <- field(value, "modifiers", path).flatMap(strings(_, s"$path.modifiers"))
      // Optional and empty by default: every walker procedure but Travel
      // selects nothing before it starts.
      startArgs <- value.value.get("startArgs") match {
        case None => Right(Vector.empty[WalkerStartArgWire])
        case Some(args) => CommandNestedCodecs.decodeStartArgsWire(args,
          s"$path.startArgs")
      }
    } yield StartWalker(action, modifiers, startArgs)
    case "rollWalker" => one(value, path, "pool")(RollWalker)
    case "resolveWalker" => for {
      _ <- exact(value, Set("type", "decisionId", "payload"), path)
      id <- string(value, "decisionId", path)
      payload <- field(value, "payload", path).flatMap(
        CommandNestedCodecs.decodeDecisionAnswerWire(_, s"$path.payload"))
    } yield ResolveWalker(id, payload)
    case other => Left(InvalidValue(s"$path.type", s"unknown intent type '$other'"))
  }

  private def empty(value: ujson.Obj, path: String, result: GameIntent) = exact(value, Set("type"), path).map(_ => result)
  private def one(value: ujson.Obj, path: String, name: String)(f: String => GameIntent) =
    exact(value, Set("type", name), path).flatMap(_ => string(value, name, path)).map(f)
  private def two(value: ujson.Obj, path: String, a: String, b: String)(f: (String, String) => GameIntent) = for {
    _ <- exact(value, Set("type", a, b), path); av <- string(value, a, path); bv <- string(value, b, path)
  } yield f(av, bv)
  private def decision(value: ujson.Obj, path: String)(f: String => GameIntent) = one(value, path, "decisionId")(f)
  private def idInt(value: ujson.Obj, path: String, name: String)(f: (String, Int) => GameIntent) = for {
    _ <- exact(value, Set("type", "decisionId", name), path); id <- string(value, "decisionId", path)
    number <- field(value, name, path).flatMap(integer(_, s"$path.$name"))
  } yield f(id, number)
  private def allocation(v: ujson.Value, p: String) = obj(v, p).flatMap { o => for {
    _ <- exact(o, Set("siteId", "count"), p); s <- string(o, "siteId", p); c <- field(o, "count", p).flatMap(integer(_, s"$p.count"))
  } yield CampaignForceAllocation(s, c) }
  private def campaignRaid(v: ujson.Value, p: String): Either[ProtocolDecodeFailure, CampaignRaidTarget] = obj(v, p).flatMap { o => string(o, "kind", p).flatMap {
    case "pawn" => exact(o, Set("kind", "playerId"), p).flatMap(_ => string(o, "playerId", p)).map(CampaignRaidTarget.Pawn)
    case "relic" => for { _ <- exact(o, Set("kind", "playerId", "relicId"), p); player <- string(o, "playerId", p); relic <- string(o, "relicId", p) } yield CampaignRaidTarget.Relic(player, relic)
    case banner @ ("peoples-favor" | "darkest-secret") => exact(o, Set("kind", "playerId"), p).flatMap(_ => string(o, "playerId", p)).map(CampaignRaidTarget.Banner(_, banner))
    case k => Left(InvalidValue(s"$p.kind", s"unknown campaign raid target '$k'"))
  }}
  private def campaignPlan(v: ujson.Value, p: String): Either[ProtocolDecodeFailure, CampaignPlanSource] = obj(v, p).flatMap { o => string(o, "kind", p).flatMap {
    case "adviser" => triple(o, p, "playerId", "cardId").map { case (a,b) => CampaignPlanSource.Adviser(a,b) }
    case "site-card" => triple(o, p, "siteId", "cardId").map { case (a,b) => CampaignPlanSource.SiteCard(a,b) }
    case "relic" => triple(o, p, "playerId", "cardId").map { case (a,b) => CampaignPlanSource.Relic(a,b) }
    case "title" => exact(o, Set("kind", "playerId"), p).flatMap(_ => string(o, "playerId", p)).map(CampaignPlanSource.Title)
    case k => Left(InvalidValue(s"$p.kind", s"unknown campaign plan source '$k'"))
  }}
  private def triple(o: ujson.Obj, p: String, a: String, b: String) = for {
    _ <- exact(o, Set("kind", a, b), p); av <- string(o, a, p); bv <- string(o, b, p)
  } yield (av, bv)
}
