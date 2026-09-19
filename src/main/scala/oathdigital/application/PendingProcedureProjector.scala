package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.{CampaignPlanOption, CampaignRules}
import oathdigital.model._
import oathdigital.protocol.projection._

private[application] final class PendingProcedureProjector(
    catalog: ExecutableCatalog,
    presentation: GamePresentationProjector,
    walkerDecisions: WalkerDecisionProjector
) {
  def project(context: ScopedProjectionContext): PendingProjection = {
    val walkerDecision = walkerDecisions.project(context)
    val walkerWaiting = walkerDecisions.waiting(context)
    val campaign = campaignProjection(context)
    val relocation = campaignRaidRelocation(context)
    PendingProjection(
      phase(context, campaign,
        relocation, walkerDecision),
      None, campaign, relocation,
      negotiationProjection(context),
      context.current.pending.exists {
        case n: PendingProcedure.Negotiation =>
          !context.viewer.exists(n.participants.contains)
        case _ => false
      }, walkerDecision, walkerWaiting)
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

  private def phase(context: ScopedProjectionContext,
      campaign: Option[CampaignProjection],
      relocation: Option[CampaignRaidRelocationProjection],
      walkerDecision: Option[WalkerDecisionProjection]): String =
    if (context.current.result.nonEmpty) "game-over"
    else if (context.current.walkerPending.nonEmpty)
      // The walker path never populates legacy `pending` (verified above:
      // `context.current.pending` is always `None` here in this slice), so
      // this is checked ahead of the legacy match rather than folded into
      // its trailing `None` arm — keeping the two pending mechanisms
      // visibly separate instead of interleaving one case among many.
      //
      // The label is keyed off the parked procedure's own wire key (Task 8)
      // instead of a hardcoded "recover-*" literal, so a second procedure
      // parked on the walker reports its own phase rather than borrowing
      // Recover's. `walkerProcedure` is always populated alongside
      // `walkerPending` (both are written by `WalkerParked` and cleared
      // together by `WalkerCompleted`), so the `None` arm below is
      // unreachable in practice; it exists only so this stays total.
      context.current.walkerProcedure.fold("walker-waiting") { procedure =>
        walkerDecision match {
          case Some(w) if w.kind == "roll" => s"${procedure.key}-walker-roll"
          case Some(_) => s"${procedure.key}-walker-decision"
          case None => s"${procedure.key}-walker-waiting"
        }
      }
    else context.current.pending match {
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
