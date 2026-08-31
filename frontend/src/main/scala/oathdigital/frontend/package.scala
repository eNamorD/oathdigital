package oathdigital

package object frontend {
  type GameProjection = protocol.projection.GameProjection
  val GameProjection = protocol.projection.GameProjection
  type GamePlayer = protocol.projection.SetupPlayerProjection
  object GamePlayer {
    def apply(playerId: String, displayName: String, role: String,
        color: PlayerColorToken): GamePlayer =
      protocol.projection.SetupPlayerProjection(playerId, displayName, role,
        color match {
          case PlayerColorToken.Purple => "purple"
          case PlayerColorToken.Blue => "blue"
          case PlayerColorToken.Red => "red"
          case PlayerColorToken.Yellow => "yellow"
          case PlayerColorToken.Neutral => "neutral"
        })
  }
  type CardDetails = protocol.projection.CardDetailsProjection
  val CardDetails = protocol.projection.CardDetailsProjection
  type GameSiteCard = protocol.projection.SiteCardProjection
  val GameSiteCard = protocol.projection.SiteCardProjection
  type GameSiteRelics = protocol.projection.SiteRelicsProjection
  val GameSiteRelics = protocol.projection.SiteRelicsProjection
  type ForgeCost = protocol.projection.ForgeCostProjection
  val ForgeCost = protocol.projection.ForgeCostProjection
  type SiteForces = protocol.projection.SiteForcesProjection
  val SiteForces = protocol.projection.SiteForcesProjection
  type GameSite = protocol.projection.SetupSiteProjection
  val GameSite = protocol.projection.SetupSiteProjection
  type SitePower = protocol.projection.SitePowerProjection
  val SitePower = protocol.projection.SitePowerProjection
  type GameRegion = protocol.projection.SetupRegionProjection
  val GameRegion = protocol.projection.SetupRegionProjection
  type GamePawn = protocol.projection.PawnLocationProjection
  val GamePawn = protocol.projection.PawnLocationProjection
  type ActivePlayerResources = protocol.projection.ActivePlayerResourcesProjection
  val ActivePlayerResources = protocol.projection.ActivePlayerResourcesProjection
  type CurrentSiteResources = protocol.projection.CurrentSiteResourcesProjection
  val CurrentSiteResources = protocol.projection.CurrentSiteResourcesProjection
  type LegalTravelDestination = protocol.projection.LegalTravelDestinationProjection
  val LegalTravelDestination = protocol.projection.LegalTravelDestinationProjection
  type LegalSearchSource = protocol.projection.LegalSearchSourceProjection
  val LegalSearchSource = protocol.projection.LegalSearchSourceProjection
  type LegalMuster = protocol.projection.LegalMusterProjection
  object LegalMuster {
    def apply(target: EconomyTarget, label: String, suit: String,
        supplyCost: Int, warbandsGained: Int): LegalMuster =
      protocol.projection.LegalMusterProjection(target.kind, target.id, label,
        suit, supplyCost, warbandsGained)
  }
  type LegalTrade = protocol.projection.LegalTradeProjection
  object LegalTrade {
    def apply(target: EconomyTarget, label: String, suit: String,
        resource: String, supplyCost: Int, gained: Int): LegalTrade =
      protocol.projection.LegalTradeProjection(target.kind, target.id, label,
        suit, resource, supplyCost, gained)
  }
  type BoardTargetRef = protocol.projection.BoardTargetRefProjection
  val BoardTargetRef = protocol.projection.BoardTargetRefProjection
  type BoardTargetCandidate = protocol.projection.BoardTargetCandidateProjection
  val BoardTargetCandidate = protocol.projection.BoardTargetCandidateProjection
  type BoardTargetFormation = protocol.projection.BoardTargetFormationProjection
  val BoardTargetFormation = protocol.projection.BoardTargetFormationProjection
  type BoardTargetAction = protocol.projection.BoardTargetActionProjection
  val BoardTargetAction = protocol.projection.BoardTargetActionProjection
  type CardResolution = protocol.projection.CardResolutionProjection
  val CardResolution = protocol.projection.CardResolutionProjection
  type MinorAdviserPlacement = protocol.projection.CardResolutionProjection
  object MinorAdviserPlacement {
    def apply(kind: String,
        replacement: Option[CardDetails] = None): MinorAdviserPlacement =
      protocol.projection.CardResolutionProjection(kind, None,
        replacement.nonEmpty, replacement.toVector)
  }
  type PendingCardDecision = protocol.projection.PendingCardDecisionProjection
  val PendingCardDecision = protocol.projection.PendingCardDecisionProjection
  type RecoverState = protocol.projection.RecoverProjection
  val RecoverState = protocol.projection.RecoverProjection
  type ForgeTarget = protocol.projection.ForgeAssignmentTargetProjection
  val ForgeTarget = protocol.projection.ForgeAssignmentTargetProjection
  type ForgeState = protocol.projection.ForgeProjection
  val ForgeState = protocol.projection.ForgeProjection
  type CampaignState = protocol.projection.CampaignProjection
  object CampaignState {
    def apply(decisionId: String, targetSiteIds: Vector[String], force: Int,
        plansFinished: Boolean, planChoices: Vector[CampaignPlanChoice],
        selectedPlans: Vector[CampaignPlanChoice], attackDice: Vector[String],
        attack: Int, skullLosses: Int, maxSacrifice: Int,
        sacrificed: Option[Int], defenseDice: Vector[String],
        defense: Option[Int], victorious: Option[Boolean], maxPlacement: Int,
        placementTargets: Vector[CampaignPlacementTarget]): CampaignState =
      protocol.projection.CampaignProjection(decisionId, targetSiteIds, force,
        plansFinished, planChoices, selectedPlans, attackDice, attack,
        skullLosses, maxSacrifice, sacrificed, defenseDice, defense, victorious,
        maxPlacement, placementTargets)

    def apply(decisionId: String, siteId: String, force: Int,
        plansFinished: Boolean, planChoices: Vector[CampaignPlanChoice],
        selectedPlans: Vector[CampaignPlanChoice], attackDice: Vector[String],
        attack: Int, skullLosses: Int, maxSacrifice: Int,
        sacrificed: Option[Int], defenseDice: Vector[String],
        defense: Option[Int], victorious: Option[Boolean],
        maxPlacement: Int): CampaignState =
      protocol.projection.CampaignProjection(decisionId, Vector(siteId), force,
        plansFinished, planChoices, selectedPlans, attackDice, attack,
        skullLosses, maxSacrifice, sacrificed, defenseDice, defense, victorious,
        maxPlacement, Vector(CampaignPlacementTarget(siteId, siteId)))
  }
  type CampaignRaidRelocation = protocol.projection.CampaignRaidRelocationProjection
  val CampaignRaidRelocation = protocol.projection.CampaignRaidRelocationProjection
  type CampaignPlacementTarget = protocol.projection.CampaignPlacementTargetProjection
  val CampaignPlacementTarget = protocol.projection.CampaignPlacementTargetProjection
  type CampaignPlanChoice = protocol.projection.CampaignPlanChoiceProjection
  val CampaignPlanChoice = protocol.projection.CampaignPlanChoiceProjection
  type BannerState = protocol.projection.BannerProjection
  val BannerState = protocol.projection.BannerProjection
  type ChallengeState = protocol.projection.ChallengeProjection
  val ChallengeState = protocol.projection.ChallengeProjection
  type MinorAdviser = protocol.projection.MinorAdviserProjection
  val MinorAdviser = protocol.projection.MinorAdviserProjection
  type MinorActionsState = protocol.projection.MinorActionsProjection
  val MinorActionsState = protocol.projection.MinorActionsProjection
  type NegotiationTransferState = protocol.projection.NegotiationTransferProjection
  val NegotiationTransferState = protocol.projection.NegotiationTransferProjection
  type NegotiationDisclosureState = protocol.projection.NegotiationDisclosureProjection
  val NegotiationDisclosureState = protocol.projection.NegotiationDisclosureProjection
  type NegotiationState = protocol.projection.NegotiationProjection
  val NegotiationState = protocol.projection.NegotiationProjection
  type RestFavorSourceState = protocol.projection.RestFavorSourceProjection
  val RestFavorSourceState = protocol.projection.RestFavorSourceProjection
  type LeagueTreatyState = protocol.projection.LeagueTreatyProjection
  val LeagueTreatyState = protocol.projection.LeagueTreatyProjection
  type RestPowerState = protocol.projection.RestPowerProjection
  val RestPowerState = protocol.projection.RestPowerProjection
  type PlayerBoard = protocol.projection.PlayerBoardProjection
  val PlayerBoard = protocol.projection.PlayerBoardProjection
  type OathkeeperStatus = protocol.projection.OathkeeperProjection
  val OathkeeperStatus = protocol.projection.OathkeeperProjection
  type OathkeeperRecipientDecision = protocol.projection.OathkeeperRecipientProjection
  val OathkeeperRecipientDecision = protocol.projection.OathkeeperRecipientProjection
}
