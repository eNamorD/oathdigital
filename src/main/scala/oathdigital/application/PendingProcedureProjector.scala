package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.{BannerRules, CampaignPlanOption, CampaignRules,
  RecoverRules, SearchRules}
import oathdigital.model._
import oathdigital.protocol.projection._

private[application] final class PendingProcedureProjector(
    catalog: ExecutableCatalog,
    presentation: GamePresentationProjector
) {
  def project(context: ScopedProjectionContext): PendingProjection = {
    val cardDecision = pendingCardDecision(context)
    val recover = recoverProjection(context)
    val forge = forgeProjection(context)
    val challenge = challengeProjection(context)
    val campaign = campaignProjection(context)
    val relocation = campaignRaidRelocation(context)
    val recipient = oathkeeperRecipient(context)
    val restPower = restPowerProjection(context)
    PendingProjection(
      phase(context, cardDecision, recover, forge, challenge, campaign,
        relocation, recipient),
      cardDecision, recover, forge, campaign, relocation, recipient, challenge,
      negotiationProjection(context),
      context.current.pending.exists {
        case n: PendingProcedure.Negotiation =>
          !context.viewer.exists(n.participants.contains)
        case _ => false
      }, restPower, context.current.pending.exists {
        case p: PendingProcedure.RestPowerDecision =>
          !context.viewer.contains(p.current.decisionOwner)
        case _ => false
      })
  }

  private def restPowerProjection(context: ScopedProjectionContext) =
    context.current.pending.collect {
      case pending: PendingProcedure.RestPowerDecision
          if context.viewer.contains(pending.current.decisionOwner) =>
        pending.payload match {
          case payload: RestPowerDecisionPayload.LeagueTreaty =>
            RestPowerProjection(pending.decision.value, pending.restActor.value,
              pending.current.decisionOwner.value, pending.current.powerId.value,
              LeagueTreatyProjection(payload.eligibleSources.map(
                restSourceProjection(context, _)), payload.legalBanks.map(_.key)))
        }
    }

  private def restSourceProjection(context: ScopedProjectionContext,
      source: SiteFavorSource): RestFavorSourceProjection = {
    val site = context.current.map.sites(source.siteId)
    source match {
      case SiteFavorSource.Denizen(_, id) =>
        val favor = site.denizens.collectFirst {
          case DenizenState(`id`, _, tokens) => tokens.favor }.getOrElse(0)
        RestFavorSourceProjection("denizen", source.siteId.value, id.value,
          presentation.denizenLabel(id), favor)
      case SiteFavorSource.Edifice(_, id) =>
        val (side, favor) = site.denizens.collectFirst {
          case EdificeState(`id`, side, tokens) => side -> tokens.favor
        }.get
        RestFavorSourceProjection("edifice", source.siteId.value, id.value,
          presentation.edificeLabel(id, side), favor)
      case SiteFavorSource.Relic(_, slot) =>
        val relic = site.relics(slot)
        val label = if (relic.orientation == Orientation.FaceDown)
          s"Facedown relic ${slot + 1}"
        else presentation.relicLabel(relic.id)
        RestFavorSourceProjection("relic-slot", source.siteId.value,
          slot.toString, label, relic.tokens.favor)
    }
  }

  private def pendingCardDecision(context: ScopedProjectionContext) =
    context.current.pending.collect {
      case search: PendingProcedure.Search if context.viewer.contains(search.actor) =>
        val drawn = context.current.temporaryHands
          .getOrElse(search.actor, Vector.empty)
        PendingCardDecisionProjection(search.decision.value, "search",
          search.actor.value, "Resolve Search",
          Vector("Move exactly one card to Keep.",
            "Remaining cards are discarded from left to right."),
          drawn.map(presentation.cardDetails(_,
            Some(Orientation.FaceUp), hidden = false)),
          1, 1, orderingRequired = true,
          drawn.map(card => card.value -> groupedResolutions(
            SearchRules.legalPlacements(catalog, context.ready, search, card))).toMap)
      case recover: PendingProcedure.Recover
          if recover.successful && context.viewer.contains(recover.actor) =>
        val relics = context.current.map.sites(recover.site).relics
        PendingCardDecisionProjection(recover.decision.value, "recover-relic",
          recover.actor.value, "Choose a relic to recover",
          Vector("You privately peek at the site's relics.",
            "Take exactly one; it remains facedown."),
          relics.map(r => presentation.cardDetails(r.id,
            Some(Orientation.FaceDown), hidden = false)),
          1, 1, orderingRequired = false,
          relics.map(r => r.id.value -> Vector(
            CardResolutionProjection("take-facedown-relic"))).toMap)
    }

  private def recoverProjection(context: ScopedProjectionContext) =
    context.current.pending.collect {
      case r: PendingProcedure.Recover if context.viewer.contains(r.actor) =>
        val remaining = context.current.players.find(_.player == r.actor).get
          .board.supply.supply
        RecoverProjection(r.decision.value, r.rolls.flatten.map(defenseFaceName),
          RecoverRules.score(r.rolls.flatten), r.difficulty, r.supplySpent,
          remaining, !r.successful && remaining > 0, !r.successful)
    }

  private def forgeProjection(context: ScopedProjectionContext) =
    context.current.pending.collect {
      case f: PendingProcedure.Forge if context.viewer.contains(f.actor) =>
        ForgeProjection(f.decision.value, f.actor.value, f.cost.favor,
          f.cost.secrets, f.eligibleTargets.map(target =>
            ForgeAssignmentTargetProjection(target.siteId.value,
              target.denizenId.value, presentation.denizenLabel(target.denizenId))))
    }

  private def challengeProjection(context: ScopedProjectionContext) =
    context.current.pending.collect {
      case c: PendingProcedure.Challenge if context.viewer.contains(c.actor) =>
        val actor = context.current.players.find(_.player == c.actor).get
        val legalSites = if (c.banner == Banner.DarkestSecret &&
            c.remainingRibbonResources > 0)
          BannerRules.leastSites(context.current, c.secretsPlaced).map(_.value)
        else Vector.empty
        ChallengeProjection(c.decision.value, c.actor.value, c.banner.key,
          c.priorHolder.map(_.value), c.priorResources, legalSites,
          c.priorResources + 1, BannerRules.playerResources(actor, c.banner))
    }

  private def campaignProjection(context: ScopedProjectionContext) =
    context.current.pending.collect {
      case campaign: PendingProcedure.Campaign if context.viewer.exists(player =>
          player == campaign.actor || (!campaign.defenderPlansFinished &&
            player == CampaignRules.planDecisionOwner(campaign))) =>
        val remaining = campaign.force - campaign.skullLosses
        val isDecisionOwner = context.viewer.contains(
          CampaignRules.planDecisionOwner(campaign))
        val planChoices = if (isDecisionOwner)
          CampaignRules.planOptions(catalog, context.ready, campaign).map(optionProjection)
        else Vector.empty
        val selectedPlans = campaign.plans.filter(_.side ==
          CampaignRules.currentPlanSide(campaign)).map(
          resolutionProjection(context, _))
        CampaignProjection(campaign.decision.value,
          campaign.targetSites.map(_.value), campaign.force,
          campaign.defenderPlansFinished,
          if (campaign.defenderPlansFinished) Vector.empty else planChoices,
          selectedPlans, campaign.attackDice.map(attackFaceName), campaign.attack,
          campaign.skullLosses, remaining, campaign.sacrificed,
          campaign.defenseDice.map(defenseFaceName), campaign.defense,
          campaign.victorious, remaining - campaign.sacrificed.getOrElse(0),
          campaign.targetSites.map(site => CampaignPlacementTargetProjection(
            site.value, presentation.siteLabel(site))),
          campaign.defender match {
            case CampaignDefender.Bandits => "bandits"
            case _: CampaignDefender.Player => "player"
          }, campaign.defender match {
            case CampaignDefender.Player(player) => Some(player.value)
            case _ => None
          }, CampaignRules.defenderForce(context.ready, campaign),
          CampaignRules.defenseDiceCount(catalog, context.ready, campaign),
          CampaignRules.currentPlanSide(campaign).toString.toLowerCase,
          Option.when(!campaign.defenderPlansFinished)(
            CampaignRules.planDecisionOwner(campaign).value),
          campaign.kind.key, campaign.raidTargets.map(_.stableKey))
    }

  private def sourceIdentity(source: PendingProcedure.CampaignPlanSource) = source match {
    case PendingProcedure.CampaignPlanSource.Adviser(player, id) =>
      ("adviser", Some(player.value), None, Some(id.value))
    case PendingProcedure.CampaignPlanSource.SiteCard(site, id) =>
      ("site-card", None, Some(site.value), Some(id.value))
    case PendingProcedure.CampaignPlanSource.Relic(player, id) =>
      ("relic", Some(player.value), None, Some(id.value))
    case PendingProcedure.CampaignPlanSource.Title(player) =>
      ("title", Some(player.value), None, None)
  }
  private def sourceLabel(context: ScopedProjectionContext,
      source: PendingProcedure.CampaignPlanSource) = source match {
    case PendingProcedure.CampaignPlanSource.Adviser(_, id) =>
      presentation.denizenLabel(id)
    case PendingProcedure.CampaignPlanSource.SiteCard(_, id) =>
      presentation.denizenLabel(id)
    case PendingProcedure.CampaignPlanSource.Relic(_, id) =>
      presentation.relicLabel(id)
    case PendingProcedure.CampaignPlanSource.Title(_) =>
      context.current.title.side.toString
  }
  private def costs(values: Vector[PendingProcedure.CampaignPlanCost]) = (
    values.collect { case PendingProcedure.CampaignPlanCost.Favor(n) => n }.sum,
    values.collect { case PendingProcedure.CampaignPlanCost.Secret(n) => n }.sum)
  private def effects(values: Vector[PendingProcedure.CampaignPlanEffect]) =
    values.map {
      case PendingProcedure.CampaignPlanEffect.AddAttackDice(n) => s"Add $n attack dice"
      case PendingProcedure.CampaignPlanEffect.AddDefenseDice(n) => s"Add $n defense dice"
      case PendingProcedure.CampaignPlanEffect.IgnoreAttackSkulls =>
        "Ignore attack-roll skull losses"
      case PendingProcedure.CampaignPlanEffect.RevealSource => "Reveal this card"
      case PendingProcedure.CampaignPlanEffect.TransformAttackResult(id) =>
        s"Transform attack result ($id)"
      case PendingProcedure.CampaignPlanEffect.ReplaceLosingForcePolicy(id) =>
        s"Replace losing-force policy ($id)"
      case PendingProcedure.CampaignPlanEffect.Suspend(kind) =>
        s"Requires $kind decision"
    }.mkString("; ")
  private def optionProjection(option: CampaignPlanOption) = {
    val (kind, player, site, card) = sourceIdentity(option.source)
    val (favor, secret) = costs(option.costs)
    CampaignPlanChoiceProjection(kind, Some(option.source.stableKey), player, site,
      card, option.label, Some(option.handlerId), favor, secret, option.description)
  }
  private def resolutionProjection(context: ScopedProjectionContext,
      plan: PendingProcedure.CampaignPlanResolution) = {
    val (kind, player, site, card) = sourceIdentity(plan.source)
    val label = sourceLabel(context, plan.source)
    val (favor, secret) = costs(plan.costs)
    CampaignPlanChoiceProjection(kind, Some(plan.source.stableKey), player, site,
      card, label, Some(plan.handlerId), favor, secret, effects(plan.effects))
  }

  private def campaignRaidRelocation(context: ScopedProjectionContext) =
    context.current.pending.collect {
      case r: PendingProcedure.CampaignRaidRelocation
          if context.viewer.contains(r.actor) =>
        CampaignRaidRelocationProjection(r.decision.value, r.actor.value,
          r.defender.value, r.origin.value, r.legalSites.map(_.value))
    }
  private def oathkeeperRecipient(context: ScopedProjectionContext) =
    context.current.pending.collect {
      case p: PendingProcedure.OathkeeperRecipient
          if context.viewer.contains(p.actor) =>
        OathkeeperRecipientProjection(p.decision.value, p.actor.value,
          p.candidates.map(_.value))
    }

  private def negotiationProjection(context: ScopedProjectionContext) =
    context.current.pending.collect {
      case negotiation: PendingProcedure.Negotiation
          if context.viewer.exists(negotiation.participants.contains) =>
        val viewing = context.viewer.get
        val transfers = negotiation.participants.flatMap { author =>
          negotiation.terms(author).transfers.map { transfer =>
            val owner = context.current.players.find(_.player == author).get
            NegotiationTransferProjection(author.value, transfer.recipient.value,
              transfer.favor, transfer.relics.size,
              transfer.relics.flatMap(id => owner.relics.find(_.id == id)).collect {
                case relic if viewing == author || relic.orientation == Orientation.FaceUp =>
                  presentation.cardDetails(relic.id, Some(relic.orientation), hidden = false)
              })
          }
        }
        val disclosures = negotiation.participants.flatMap { author =>
          negotiation.terms(author).disclosures.map { disclosure =>
            val visible = viewing == author
            val (kind, detail) = disclosure.information match {
              case NegotiationDisclosureRef.Adviser(_, card) => "adviser" ->
                Option.when(visible)(presentation.cardDetails(card,
                  Some(Orientation.FaceDown), hidden = false))
              case NegotiationDisclosureRef.HeldRelic(_, relic) => "held-relic" ->
                Option.when(visible)(presentation.cardDetails(relic,
                  Some(Orientation.FaceDown), hidden = false))
              case NegotiationDisclosureRef.SiteRelic(_, relic) => "site-relic" ->
                Option.when(visible)(presentation.cardDetails(relic,
                  Some(Orientation.FaceDown), hidden = false))
            }
            NegotiationDisclosureProjection(author.value,
              disclosure.recipient.value, kind, detail)
          }
        }
        val player = context.current.players.find(_.player == viewing).get
        val siteRelicOffers = context.ready.knowledge.siteRelics
          .getOrElse(viewing, Map.empty).toVector.flatMap { case (site, known) =>
          context.current.map.sites.get(site).toVector.flatMap(
            _.relics.filter(relic => known.contains(relic.id)).map(relic =>
              NegotiationSiteRelicProjection(site.value, presentation.cardDetails(
                relic.id, Some(relic.orientation), hidden = false))))
        }
        NegotiationProjection(negotiation.decision.value,
          negotiation.actor.value, negotiation.site.value,
          negotiation.participants.map(_.value),
          negotiation.participants.filter(negotiation.accepted).map(_.value),
          transfers, disclosures, player.board.favor,
          player.relics.map(r => presentation.cardDetails(r.id,
            Some(r.orientation), hidden = false)),
          player.advisers.collect {
            case d: DenizenState if d.orientation == Orientation.FaceDown =>
              presentation.cardDetails(d.id, Some(d.orientation), hidden = false)
            case v: VisionState if v.orientation == Orientation.FaceDown =>
              presentation.cardDetails(v.id, Some(v.orientation), hidden = false)
          }, siteRelicOffers)
    }

  private def groupedResolutions(placements: Vector[SearchPlacement]) = {
    val keys = placements.map {
      case SearchPlacement.Discard => "discard" -> None
      case SearchPlacement.Site(_) => "site" -> Some("face-up")
      case SearchPlacement.Adviser(orientation, _) =>
        "adviser" -> Some(presentation.orientationName(orientation))
    }.distinct
    keys.map { case (kind, orientation) =>
      val matching = placements.filter {
        case SearchPlacement.Discard => kind == "discard"
        case SearchPlacement.Site(_) => kind == "site"
        case SearchPlacement.Adviser(value, _) => kind == "adviser" &&
          orientation.contains(presentation.orientationName(value))
      }
      val replacements = matching.flatMap {
        case SearchPlacement.Site(replace) => replace
        case SearchPlacement.Adviser(_, replace) => replace
        case SearchPlacement.Discard => None
      }.distinct
      val required = matching.nonEmpty && matching.forall {
        case SearchPlacement.Site(replace) => replace.nonEmpty
        case SearchPlacement.Adviser(_, replace) => replace.nonEmpty
        case SearchPlacement.Discard => false
      }
      CardResolutionProjection(kind, orientation, required,
        if (required) replacements.map(presentation.cardDetails(_, None,
          hidden = false)) else Vector.empty)
    }
  }

  private def phase(context: ScopedProjectionContext,
      card: Option[PendingCardDecisionProjection], recover: Option[RecoverProjection],
      forge: Option[ForgeProjection], challenge: Option[ChallengeProjection],
      campaign: Option[CampaignProjection],
      relocation: Option[CampaignRaidRelocationProjection],
      recipient: Option[OathkeeperRecipientProjection]): String =
    if (context.current.result.nonEmpty) "game-over"
    else context.current.pending match {
      case Some(_: PendingProcedure.Search) if card.nonEmpty => "search-decision"
      case Some(_: PendingProcedure.Search) => "search-waiting"
      case Some(r: PendingProcedure.Recover)
          if context.viewer.contains(r.actor) && r.successful => "recover-relic-decision"
      case Some(_: PendingProcedure.Recover) if recover.nonEmpty => "recover-rolling"
      case Some(_: PendingProcedure.Recover) => "recover-waiting"
      case Some(_: PendingProcedure.RecoverPowerApplied) => "recover-waiting"
      case Some(_: PendingProcedure.Forge) if forge.nonEmpty => "forge-assignment"
      case Some(_: PendingProcedure.Forge) => "forge-waiting"
      case Some(_: PendingProcedure.Challenge) if challenge.nonEmpty => "challenge-decision"
      case Some(_: PendingProcedure.Challenge) => "challenge-waiting"
      case Some(_: PendingProcedure.Campaign)
          if campaign.exists(_.victorious.contains(true)) => "campaign-placement"
      case Some(c: PendingProcedure.Campaign) if campaign.nonEmpty &&
          !c.defenderPlansFinished => "campaign-plan"
      case Some(_: PendingProcedure.Campaign) if campaign.nonEmpty => "campaign-sacrifice"
      case Some(_: PendingProcedure.Campaign) => "campaign-waiting"
      case Some(_: PendingProcedure.CampaignRaidRelocation) if relocation.nonEmpty =>
        "campaign-raid-relocation"
      case Some(_: PendingProcedure.CampaignRaidRelocation) =>
        "campaign-raid-relocation-waiting"
      case Some(_: PendingProcedure.OathkeeperRecipient) if recipient.nonEmpty =>
        "oathkeeper-recipient"
      case Some(_: PendingProcedure.OathkeeperRecipient) =>
        "oathkeeper-recipient-waiting"
      case Some(p: PendingProcedure.Conspiracy) if p.awaitingTarget &&
          context.viewer.contains(p.actor) => "conspiracy-target"
      case Some(_: PendingProcedure.Conspiracy) => "conspiracy-waiting"
      case Some(p: PendingProcedure.RestPowerDecision)
          if context.viewer.contains(p.current.decisionOwner) => "rest-power-decision"
      case Some(_: PendingProcedure.RestPowerDecision) => "rest-power-waiting"
      case _ => context.current.turn.phase match {
        case Phase.Wake => "wake"
        case Phase.Act => "act-action-selection"
        case Phase.Rest => "rest"
        case Phase.RoundEnd => "round-end"
        case Phase.WarExhaustion => "war-exhaustion"
      }
    }

  private def attackFaceName(value: AttackDieFace) = value match {
    case AttackDieFace.HollowSword => "hollow-sword"
    case AttackDieFace.OneSword => "one-sword"
    case AttackDieFace.TwoSwordsSkull => "two-swords-skull"
  }
  private def defenseFaceName(value: DefenseDieFace) = value match {
    case DefenseDieFace.Blank => "blank"
    case DefenseDieFace.OneShield => "one-shield"
    case DefenseDieFace.TwoShields => "two-shields"
    case DefenseDieFace.Doubler => "doubler"
  }
}
