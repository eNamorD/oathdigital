package oathdigital.application

import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseOneAnswer, DistributeAnswer, PartitionAnswer}
import oathdigital.protocol.{GameIntent => Intent, _}

final case class GameIntentMappingFailure(path: String, message: String)

/** Sole conversion and actor-binding boundary for transport-safe commands. */
object GameIntentMapper {
  private type Result[A] = Either[GameIntentMappingFailure, A]

  def bind(actorId: PlayerId, intent: Intent): Result[GameCommand] = {
    val actor = AuthorizedPlayer.forPlayer(actorId)
    intent match {
      case Intent.PlacePawn(site) => Right(actor.placePawn(SiteId(site)))
      case Intent.EndWake => Right(actor.endWake)
      case Intent.BeginRest => Right(actor.beginRest)
      case Intent.FinishRest => Right(actor.finishRest)
      case Intent.UsePower(value, source) => for {
        power <- PowerId.fromValue(value).toRight(GameIntentMappingFailure(
          "$.intent.powerId", s"invalid power id '$value'"))
        ref <- optionRef(source.optionKind, source.optionId, "$.intent.source")
      } yield actor.usePower(power, ref)
      case Intent.BeginChallenge(value) => banner(value).map(actor.beginChallenge)
      case Intent.ChooseChallengeSecretSite(id, site) => Right(actor.chooseChallengeSecretSite(DecisionId(id), SiteId(site)))
      case Intent.CompleteChallenge(id, amount) => Right(actor.completeChallenge(DecisionId(id), amount))
      case Intent.PlaceBannerResource(value, amount) => banner(value).map(actor.placeBannerResource(_, amount))
      case Intent.RevealVision(id) => Right(actor.revealVision(VisionId(id)))
      case Intent.PlayConspiracy(value) => option(value)(conspiracy).map(actor.playConspiracy)
      case Intent.PeekSiteRelics => Right(actor.peekSiteRelics)
      case Intent.RevealOwnedRelic(id) => Right(actor.revealOwnedRelic(RelicId(id)))
      case Intent.MoveWarbands(toSite, amount) => Right(actor.moveWarbands(toSite, amount))
      case Intent.BeginNegotiation(ids) => Right(actor.beginNegotiation(ids.map(PlayerId)))
      case Intent.ReplaceNegotiationTerms(id, value) => negotiation(value).map(actor.replaceNegotiationTerms(DecisionId(id), _))
      case Intent.AcceptNegotiation(id) => Right(actor.acceptNegotiation(DecisionId(id)))
      case Intent.DeclineNegotiation(id) => Right(actor.declineNegotiation(DecisionId(id)))
      case Intent.BeginCampaignConquest(sites, count) => Right(actor.beginCampaignConquest(sites.map(SiteId), count))
      case Intent.BeginCampaignRaid(values, count) => traverse(values)(raid).map(actor.beginCampaignRaid(_, count))
      case Intent.ChooseCampaignPlan(id, value) => plan(value).map(actor.chooseCampaignPlan(DecisionId(id), _))
      case Intent.FinishCampaignPlans(id) => Right(actor.finishCampaignPlans(DecisionId(id)))
      case Intent.ChooseCampaignSacrifice(id, count) => Right(actor.chooseCampaignSacrifice(DecisionId(id), count))
      case Intent.PlaceCampaignForce(id, values) => Right(actor.placeCampaignForce(DecisionId(id), values.map(v => oathdigital.model.CampaignForceAllocation(SiteId(v.siteId), v.count))))
      case Intent.RelocateCampaignRaidPawn(id, site) => Right(actor.relocateCampaignRaidPawn(DecisionId(id), SiteId(site)))
      case Intent.ResolveCardDecision(id, value) => resolution(value).map(actor.resolveCardDecision(DecisionId(id), _))
      case Intent.StartWalker(value, modifiers, startArgs) => for {
        ref <- actionRef(value)
        ids <- traverse(modifiers.zipWithIndex)((powerId _).tupled)
        args <- traverse(startArgs.zipWithIndex)((walkerStartArg _).tupled)
      } yield GameCommand.StartWalker(ref, StartPayload(actorId, ids, args))
      case Intent.RollWalker(pool) => Right(actor.rollWalker(PoolKey(pool)))
      case Intent.ResolveWalker(id, value) => decisionAnswer(value).map(p =>
        actor.resolveWalker(TreeDecision(id, p)))
    }
  }

  def bind(actorId: PlayerId, intent: Intent,
      modifiers: Vector[ModifierInvocation]): Result[GameCommand] =
    for {
      command <- bind(actorId, intent)
      ordered <- traverse(modifiers)(modifier(actorId, _))
    } yield if (ordered.isEmpty) command else GameCommand.WithModifiers(command, ordered)

  def bindModifiers(actorId: PlayerId, modifiers: Vector[ModifierInvocation])
      : Result[Vector[OrderedRuleInvocation]] = traverse(modifiers)(modifier(actorId, _))

  private def modifier(actor: PlayerId, value: ModifierInvocation)
      : Result[OrderedRuleInvocation] = {
    val source = value.sourceKind match {
      case "site" => Right(RuleSourceRef.Site(SiteId(value.sourceId)))
      case "site-card" => value.contextId.toRight(GameIntentMappingFailure(
        "$.orderedModifiers.contextId", "site-card requires site context"))
        .map(site => RuleSourceRef.SiteCard(SiteId(site), DenizenId(value.sourceId)))
      case "adviser" => Right(RuleSourceRef.Adviser(actor, DenizenId(value.sourceId)))
      case "relic" => Right(RuleSourceRef.Relic(actor, RelicId(value.sourceId)))
      case "edifice" => value.contextId.toRight(GameIntentMappingFailure(
        "$.orderedModifiers.contextId", "edifice requires site context"))
        .map(site => RuleSourceRef.Edifice(SiteId(site), EdificeId(value.sourceId)))
      case "banner" => Right(RuleSourceRef.Banner(value.sourceId))
      case "foundation" => scala.util.Try(value.sourceId.toInt).toOption
        .flatMap(n => FoundationNumber.all.find(_.value == n))
        .map(n => RuleSourceRef.Foundation(n): RuleSourceRef).toRight(
          GameIntentMappingFailure("$.orderedModifiers.sourceId", "unknown foundation"))
      case "legacy" => value.contextId.toRight(GameIntentMappingFailure(
        "$.orderedModifiers.contextId", "legacy requires lineage context"))
        .map(lineage => RuleSourceRef.Legacy(LineageId(lineage), LegacyId(value.sourceId)))
      case other => Left(GameIntentMappingFailure("$.orderedModifiers.sourceKind",
        s"unknown modifier source '$other'"))
    }
    source.map(OrderedRuleInvocation(_, value.handlerId))
  }

  private def invalid(path: String, value: String, kind: String) = Left(GameIntentMappingFailure(path, s"unknown $kind '$value'"))
  private def banner(value: String): Result[Banner] = Banner.fromKey(value).toRight(GameIntentMappingFailure("$.intent.banner", s"unknown banner '$value'"))
  private def world(value: WorldCard, path: String): Result[WorldCardId] = value.kind match { case "denizen" => Right(DenizenId(value.id)); case "vision" => Right(VisionId(value.id)); case v => invalid(s"$path.kind", v, "world card kind") }
  private def conspiracy(value: oathdigital.protocol.ConspiracyTarget): Result[ConspiracyTargetRef] = value match {
    case oathdigital.protocol.ConspiracyTarget.RelicSlot(owner, slot) => Right(ConspiracyTargetRef.RelicSlot(PlayerId(owner), slot))
    case oathdigital.protocol.ConspiracyTarget.Banner(owner, key) => banner(key).map(ConspiracyTargetRef.Banner(PlayerId(owner), _))
  }
  private def raid(value: oathdigital.protocol.CampaignRaidTarget): Result[oathdigital.model.CampaignRaidTarget] = value match {
    case oathdigital.protocol.CampaignRaidTarget.Pawn(p) => Right(oathdigital.model.CampaignRaidTarget.Pawn(PlayerId(p)))
    case oathdigital.protocol.CampaignRaidTarget.Relic(p, r) => Right(oathdigital.model.CampaignRaidTarget.Relic(PlayerId(p), RelicId(r)))
    case oathdigital.protocol.CampaignRaidTarget.Banner(p, key) => Banner.fromKey(key)
      .map(oathdigital.model.CampaignRaidTarget.Banner(PlayerId(p), _))
      .toRight(GameIntentMappingFailure("$.intent.targets.banner", s"unknown campaign banner '$key'"))
  }
  private def plan(value: oathdigital.protocol.CampaignPlanSource): Result[PendingProcedure.CampaignPlanSource] = value match {
    case oathdigital.protocol.CampaignPlanSource.Adviser(p,c) => Right(PendingProcedure.CampaignPlanSource.Adviser(PlayerId(p), DenizenId(c)))
    case oathdigital.protocol.CampaignPlanSource.SiteCard(s,c) => Right(PendingProcedure.CampaignPlanSource.SiteCard(SiteId(s), DenizenId(c)))
    case oathdigital.protocol.CampaignPlanSource.Relic(p,c) => Right(PendingProcedure.CampaignPlanSource.Relic(PlayerId(p), RelicId(c)))
    case oathdigital.protocol.CampaignPlanSource.Title(p) => Right(PendingProcedure.CampaignPlanSource.Title(PlayerId(p)))
  }
  private def negotiation(value: oathdigital.protocol.NegotiationTerms): Result[oathdigital.model.NegotiationTerms] = traverse(value.disclosures)(disclosure).map(ds => oathdigital.model.NegotiationTerms(value.transfers.map(v => oathdigital.model.NegotiationTransfer(PlayerId(v.recipientPlayerId), v.favor, v.relicIds.map(RelicId))), ds))
  private def disclosure(value: oathdigital.protocol.NegotiationDisclosure): Result[oathdigital.model.NegotiationDisclosure] = value.information match {
    case NegotiationInformation.Adviser(owner, c) => world(c, "$.intent.terms.disclosures.information.card").map(v => oathdigital.model.NegotiationDisclosure(PlayerId(value.recipientPlayerId), NegotiationDisclosureRef.Adviser(PlayerId(owner), v)))
    case NegotiationInformation.HeldRelic(owner, relic) => Right(oathdigital.model.NegotiationDisclosure(PlayerId(value.recipientPlayerId), NegotiationDisclosureRef.HeldRelic(PlayerId(owner), RelicId(relic))))
    case NegotiationInformation.SiteRelic(site, relic) => Right(oathdigital.model.NegotiationDisclosure(PlayerId(value.recipientPlayerId), NegotiationDisclosureRef.SiteRelic(SiteId(site), RelicId(relic))))
  }
  /** One wire start selection to the engine's own reference, through the SAME
    * `DecisionOptionRef.fromWire` a walker answer is decoded with (see
    * `optionRef`). Whether the action accepts this reference at all is not
    * asked here: that is the registered action's own question, answered when
    * it builds its tree.
    */
  private def walkerStartArg(value: WalkerStartArgWire, index: Int)
      : Result[DecisionOptionRef] = optionRef(value.optionKind, value.optionId,
    s"$$.intent.startArgs[$index]")

  private def actionRef(value: String): Result[StartableRef] =
    StartableRef.fromKey(value).toRight(GameIntentMappingFailure(
      "$.intent.action", s"unknown action '$value'"))
  private def powerId(value: String, index: Int): Result[PowerId] =
    PowerId.fromValue(value).toRight(GameIntentMappingFailure(
      s"$$.intent.modifiers[$index]", s"invalid power id '$value'"))
  /** The kind/id pair back to the engine's option reference. Total over the
    * seven declared variants, and deliberately not a table written here:
    * `DecisionOptionRef.fromWire` is the same function the journal codec
    * decodes with, so a client's answer and a replayed one resolve a given
    * pair identically or not at all.
    */
  private def optionRef(kind: String, id: String,
      path: String): Result[DecisionOptionRef] =
    DecisionOptionRef.fromWire(kind, id).toRight(GameIntentMappingFailure(path,
      s"unknown decision option '$kind/$id'"))

  /** Names no action and no decision id: a walker answer is generic over the
    * query shapes the engine declares, and the engine checks it against the
    * query the parked node actually carries.
    */
  private def decisionAnswer(value: DecisionAnswerWire): Result[DecisionAnswer] = value match {
    case DecisionAnswerWire.ChooseOneWire(kind, id) =>
      optionRef(kind, id, "$.intent.payload.option").map(ChooseOneAnswer)
    case DecisionAnswerWire.PartitionWire(placements) =>
      traverse(placements)(row => optionRef(row.optionKind, row.optionId,
        "$.intent.payload.placements.option").map(
          DecisionPlacement(_, row.sectionKey))).map(PartitionAnswer)
    case DecisionAnswerWire.DistributeWire(amounts) =>
      traverse(amounts)(row => optionRef(row.optionKind, row.optionId,
        "$.intent.payload.amounts.option").map(
          DistributeAmount(_, row.amount))).map(DistributeAnswer)
  }
  private def resolution(value: DecisionResolution): Result[CardDecisionResolution] = value match {
    case DecisionResolution.StartingAdviser(id) => Right(CardDecisionResolution.StartingAdviser(DenizenId(id)))
  }
  private def traverse[A,B](values: Vector[A])(f: A => Result[B]): Result[Vector[B]] = values.foldLeft[Result[Vector[B]]](Right(Vector.empty)) { case (Right(acc), v) => f(v).map(acc :+ _); case (l @ Left(_), _) => l }
  private def option[A,B](value: Option[A])(f: A => Result[B]): Result[Option[B]] = value match { case Some(v) => f(v).map(Some(_)); case None => Right(None) }
}
